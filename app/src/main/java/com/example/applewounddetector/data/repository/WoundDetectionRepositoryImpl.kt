package com.example.applewounddetector.data.repository

import android.content.Context
import android.graphics.ImageFormat
import com.example.applewounddetector.data.datasource.ArDataSource
import com.example.applewounddetector.data.datasource.OpenCVDataSource
import com.example.applewounddetector.domain.model.MeasurementResult
import com.example.applewounddetector.domain.model.MeasurementStatus
import com.example.applewounddetector.domain.repository.WoundDetectionRepository
import org.opencv.android.OpenCVLoader
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class WoundDetectionRepositoryImpl(
    private val context: Context
) : WoundDetectionRepository {
    
    private val arDataSource: ArDataSource = ArDataSource(context)
    private val openCVDataSource: OpenCVDataSource = OpenCVDataSource()
    
    private var markerSizeMm: Float = MARKER_SIZE_MM
    private var pixelsPerMm: Float = 0f
    private var isArInitialized: Boolean = false
    private var defaultPixelsPerMm: Float = 16.0f // Default estimate for ~30cm distance
    
    companion object {
        private const val MARKER_SIZE_MM = 12f
        
        fun initOpenCV(): Boolean {
            return OpenCVLoader.initDebug()
        }
    }
    
    init {
        initOpenCV()
    }
    
    override fun initialize() {
        val session = arDataSource.initializeArSession()
        isArInitialized = session != null
        if (!isArInitialized) {
            pixelsPerMm = defaultPixelsPerMm
        }
    }
    
    override fun release() {
        arDataSource.release()
    }
    
    override suspend fun detectWound(
        imageData: ByteArray,
        width: Int,
        height: Int
    ): MeasurementResult? {
        try {
            val yuvMat = Mat(height + height / 2, width, CvType.CV_8UC1)
            yuvMat.put(0, 0, imageData)
            
            val rgbMat = Mat()
            Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)
            yuvMat.release()
            
            val grayMat = Mat()
            Imgproc.cvtColor(rgbMat, grayMat, Imgproc.COLOR_RGB2GRAY)
            
            val markers = arDataSource.detectArUcoMarker(grayMat)
            
            val markerDetected = markers.isNotEmpty()
            var areaMm2 = 0f
            var depthMm = 0f
            
            if (markerDetected) {
                val marker = markers.first()
                val markerWidthPx = calculateMarkerWidth(marker.corners)
                pixelsPerMm = markerWidthPx / MARKER_SIZE_MM
                markerSizeMm = MARKER_SIZE_MM
                
                val woundResult = openCVDataSource.detectWoundArea(rgbMat)
                
                if (woundResult != null && pixelsPerMm > 0) {
                    areaMm2 = woundResult.area / (pixelsPerMm * pixelsPerMm)
                    depthMm = estimateDepth(woundResult.area, pixelsPerMm)
                }
            } else if (isArInitialized) {
                val woundResult = openCVDataSource.detectWoundArea(rgbMat)
                if (woundResult != null && pixelsPerMm > 0) {
                    areaMm2 = woundResult.area / (pixelsPerMm * pixelsPerMm)
                    depthMm = estimateDepth(woundResult.area, pixelsPerMm)
                }
            } else {
                val woundResult = openCVDataSource.detectWoundArea(rgbMat)
                if (woundResult != null) {
                    areaMm2 = woundResult.area / (defaultPixelsPerMm * defaultPixelsPerMm)
                    depthMm = estimateDepth(woundResult.area, defaultPixelsPerMm)
                }
            }
            
            grayMat.release()
            rgbMat.release()
            
            return MeasurementResult(
                areaMm2 = areaMm2,
                depthMm = depthMm,
                markerDetected = markerDetected,
                depthAvailable = arDataSource.isDepthSupported(),
                status = if (markerDetected) MeasurementStatus.COMPLETED else MeasurementStatus.MARKER_DETECTED
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    private fun calculateMarkerWidth(corners: List<org.opencv.core.Point>): Float {
        val topLeft = corners[0]
        val topRight = corners[1]
        val dx = topRight.x - topLeft.x
        val dy = topRight.y - topLeft.y
        return kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
    
    private fun estimateDepth(woundAreaPx: Float, pixelsPerMmValue: Float): Float {
        val areaMm2 = woundAreaPx / (pixelsPerMmValue * pixelsPerMmValue)
        
        return when {
            areaMm2 < 10 -> 1.0f
            areaMm2 < 50 -> 2.0f
            areaMm2 < 100 -> 3.5f
            areaMm2 < 200 -> 5.0f
            else -> 7.0f
        }
    }
    
    override fun isDepthAvailable(): Boolean {
        return arDataSource.isDepthSupported()
    }
    
    fun getDepthAtPoint(frame: com.google.ar.core.Frame, x: Int, y: Int): Float? {
        val depthImage = arDataSource.getDepthImage(frame) ?: return null
        
        try {
            if (x >= 0 && x < depthImage.width && y >= 0 && y < depthImage.height) {
                val pixel = depthImage.planes[0].buffer.getShort(y * depthImage.width * 2 + x * 2)
                val depthMeters = pixel.toFloat() / 1000f
                return depthMeters
            }
        } finally {
            depthImage.close()
        }
        
        return null
    }
}
