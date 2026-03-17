package com.example.applewounddetector.data.datasource

import android.content.Context
import android.media.Image
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.CvType
import org.opencv.imgproc.Imgproc

class ArDataSource(private val context: Context) {
    
    private var session: Session? = null
    private var isDepthSupported: Boolean = false
    private var isInitialized: Boolean = false
    
    companion object {
        const val MARKER_SIZE_MM = 50f
    }
    
    fun initializeArSession(): Session? {
        if (isInitialized) return session
        
        try {
            when (ArCoreApk.getInstance().requestInstall(context as android.app.Activity, true)) {
                ArCoreApk.InstallStatus.INSTALLED -> {}
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> return null
            }
            
            session = Session(context)
            val config = Config(session!!)
            
            isDepthSupported = session?.isDepthModeSupported(Config.DepthMode.AUTOMATIC) == true
            if (isDepthSupported) {
                config.depthMode = Config.DepthMode.AUTOMATIC
            }
            
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            
            session?.configure(config)
            isInitialized = true
            
            return session
        } catch (e: UnavailableException) {
            e.printStackTrace()
            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    fun getSession(): Session? = session
    
    fun isDepthSupported(): Boolean = isDepthSupported
    
    fun getDepthImage(frame: Frame): Image? {
        if (!isDepthSupported) return null
        return try {
            frame.acquireDepthImage16Bits()
        } catch (e: Exception) {
            null
        }
    }
    
    fun getMarkerSize(): Float = MARKER_SIZE_MM
    
    fun detectArUcoMarker(grayMat: Mat): List<MarkerDetection> {
        val detections = mutableListOf<MarkerDetection>()
        
        try {
            val binaryMat = Mat()
            Imgproc.threshold(grayMat, binaryMat, 127.0, 255.0, Imgproc.THRESH_BINARY_INV)
            
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(binaryMat, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            for (contour in contours) {
                val approx = MatOfPoint2f()
                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)
                
                val corners = approx.toList()
                if (corners.size == 4) {
                    val area = Imgproc.contourArea(approx)
                    if (area > 1000 && area < 50000) {
                        val isConvex = isShapeConvex(corners)
                        if (isConvex) {
                            detections.add(
                                MarkerDetection(
                                    id = detections.size,
                                    corners = corners,
                                    size = MARKER_SIZE_MM
                                )
                            )
                        }
                    }
                }
            }
            
            binaryMat.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return detections
    }
    
    private fun isShapeConvex(corners: List<Point>): Boolean {
        if (corners.size < 3) return false
        
        var sign = 0
        for (i in corners.indices) {
            val p1 = corners[i]
            val p2 = corners[(i + 1) % corners.size]
            val p3 = corners[(i + 2) % corners.size]
            
            val cross = (p2.x - p1.x) * (p3.y - p2.y) - (p2.y - p1.y) * (p3.x - p2.x)
            
            if (i == 0) {
                sign = if (cross > 0) 1 else -1
            } else {
                if ((cross > 0 && sign < 0) || (cross < 0 && sign > 0)) {
                    return false
                }
            }
        }
        return true
    }
    
    fun release() {
        session?.close()
        session = null
        isInitialized = false
    }
    
    data class MarkerDetection(
        val id: Int,
        val corners: List<Point>,
        val size: Float
    )
}

class OpenCVDataSource {
    
    companion object {
        private val WOUND_COLOR_LOWER = Scalar(0.0, 0.0, 0.0)
        private val WOUND_COLOR_UPPER = Scalar(180.0, 255.0, 100.0)
    }
    
    fun detectWoundArea(rgbMat: Mat): WoundAreaResult? {
        try {
            val hsvMat = Mat()
            Imgproc.cvtColor(rgbMat, hsvMat, Imgproc.COLOR_RGB2HSV)
            
            val woundMask = Mat()
            org.opencv.core.Core.inRange(hsvMat, WOUND_COLOR_LOWER, WOUND_COLOR_UPPER, woundMask)
            
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, org.opencv.core.Size(5.0, 5.0))
            Imgproc.morphologyEx(woundMask, woundMask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(woundMask, woundMask, Imgproc.MORPH_OPEN, kernel)
            
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(woundMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            
            if (contours.isEmpty()) {
                woundMask.release()
                hsvMat.release()
                return null
            }
            
            val largestContour = contours.maxByOrNull { Imgproc.contourArea(it) }
                ?: return null
            
            val area = Imgproc.contourArea(largestContour)
            if (area < 100) {
                woundMask.release()
                hsvMat.release()
                return null
            }
            
            val moments = Imgproc.moments(largestContour)
            val cx = (moments.m10 / moments.m00).toFloat()
            val cy = (moments.m01 / moments.m00).toFloat()
            
            woundMask.release()
            hsvMat.release()
            
            return WoundAreaResult(
                area = area.toFloat(),
                centerX = cx,
                centerY = cy,
                contour = largestContour.toList()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    fun convertImageToRgb(image: Image): Mat {
        val yBuffer = image.planes[0].buffer
        val vBuffer = image.planes[2].buffer
        
        val ySize = yBuffer.remaining()
        val vSize = vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + vSize)
        
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        
        val yuvMat = Mat(image.height + image.height / 2, image.width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)
        
        val rgbMat = Mat()
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)
        
        yuvMat.release()
        
        return rgbMat
    }
    
    fun convertYuvToRgb(image: Image): Mat {
        val yData = image.planes[0].buffer
        val vData = image.planes[2].buffer
        
        val ySize = yData.remaining()
        val vSize = vData.remaining()
        
        val nv21 = ByteArray(ySize + vSize)
        
        yData.get(nv21, 0, ySize)
        vData.get(nv21, ySize, vSize)
        
        val yuvMat = Mat(image.height * 3 / 2, image.width, CvType.CV_8UC1)
        yuvMat.put(0, 0, nv21)
        
        val rgbMat = Mat()
        Imgproc.cvtColor(yuvMat, rgbMat, Imgproc.COLOR_YUV2RGB_NV21)
        
        yuvMat.release()
        
        return rgbMat
    }
    
    data class WoundAreaResult(
        val area: Float,
        val centerX: Float,
        val centerY: Float,
        val contour: List<Point>
    )
}
