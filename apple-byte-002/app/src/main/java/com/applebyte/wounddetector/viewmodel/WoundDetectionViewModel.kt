package com.applebyte.wounddetector.viewmodel

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.applebyte.wounddetector.util.ArUcoDetector
import com.applebyte.wounddetector.util.WoundDetector
import com.applebyte.wounddetector.util.DepthEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class WoundDetectionViewModel : ViewModel() {

    private val _woundArea = MutableLiveData(0.0)
    val woundArea: LiveData<Double> = _woundArea

    private val _woundDepth = MutableLiveData(0.0)
    val woundDepth: LiveData<Double> = _woundDepth

    private val _status = MutableLiveData("Initializing...")
    val status: LiveData<String> = _status

    private val _overlayBitmap = MutableLiveData<Bitmap?>()
    val overlayBitmap: LiveData<Bitmap?> = _overlayBitmap

    private var isProcessing = false
    private var lastFrame: ImageProxy? = null

    private val woundDetector = WoundDetector()
    private val arUcoDetector = ArUcoDetector()
    private val depthEstimator = DepthEstimator()

    private var markerSizeMm = 50.0
    private var isArUcoDetected = false

    fun onCameraReady() {
        _status.postValue("Ready - Place ArUco marker in view")
    }

    fun processFrame(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        isProcessing = true

        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    processImageProxy(imageProxy)
                }

                result?.let { (area, depth, overlay) ->
                    if (isArUcoDetected) {
                        _woundArea.postValue(area)
                        _woundDepth.postValue(depth)
                        _status.postValue("Wound detected")
                    } else {
                        _status.postValue("Place ArUco marker in view")
                    }
                    // _overlayBitmap.postValue(overlay)
                } ?: run {
                    _status.postValue("Analyzing...")
                }
            } catch (e: Exception) {
                _status.postValue("Error: ${e.message}")
            } finally {
                isProcessing = false
                imageProxy.close()
            }
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy): Triple<Double, Double, Bitmap>? {
        val bitmap = imageProxyToBitmap(imageProxy) ?: return null

        val arUcoResult = arUcoDetector.detect(bitmap)
        isArUcoDetected = arUcoResult.detected

        if (isArUcoDetected) {
            markerSizeMm = arUcoResult.sizeMm
        }

        val woundResult = woundDetector.detectWound(bitmap, arUcoResult.corners)

        val overlay = createOverlayBitmap(bitmap, woundResult.contour, arUcoResult.corners)

        val pixelToMmRatio = if (isArUcoDetected) {
            markerSizeMm / arUcoResult.pixelSize
        } else {
            0.5
        }

        val woundAreaMm = woundResult.area * pixelToMmRatio * pixelToMmRatio

        val woundDepthMm = if (isArUcoDetected && woundResult.area > 100 && woundResult.contour.isNotEmpty()) {
            val intrinsics = DepthEstimator.CameraIntrinsics(
                fx = 640f,
                fy = 480f,
                cx = imageProxy.width / 2f,
                cy = imageProxy.height / 2f
            )
            val simulatedDepthMap = generateSimulatedDepthMap(imageProxy.width, imageProxy.height)
            depthEstimator.calculateRealDepthFromDepthMap(
                simulatedDepthMap,
                imageProxy.width,
                imageProxy.height,
                woundResult.contour,
                intrinsics
            )
        } else {
            0.0
        }

        return Triple(woundAreaMm, woundDepthMm, overlay)
    }

    private fun generateSimulatedDepthMap(width: Int, height: Int): FloatArray {
        val depthMap = FloatArray(width * height)
        val centerX = width / 2.0
        val centerY = height / 2.0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                val maxDist = kotlin.math.sqrt(centerX * centerX + centerY * centerY)

                val normalizedDist = (distance / maxDist).coerceIn(0.0, 1.0)
                val baseDepth = 0.5 + normalizedDist * 0.3

                depthMap[y * width + x] = baseDepth.toFloat()
            }
        }

        return depthMap
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
            val imageBytes = out.toByteArray()

            android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun createOverlayBitmap(
        original: Bitmap,
        woundContour: List<Pair<Float, Float>>,
        markerCorners: List<Pair<Float, Float>>?
    ): Bitmap {
        val result = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val woundPaint = Paint().apply {
            color = Color.argb(128, 255, 109, 0)
            style = Paint.Style.FILL
        }

        val woundStrokePaint = Paint().apply {
            color = Color.rgb(255, 109, 0)
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val markerPaint = Paint().apply {
            color = Color.argb(128, 21, 101, 192)
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        if (woundContour.size > 2) {
            val path = android.graphics.Path()
            path.moveTo(woundContour[0].first, woundContour[0].second)
            for (i in 1 until woundContour.size) {
                path.lineTo(woundContour[i].first, woundContour[i].second)
            }
            path.close()
            canvas.drawPath(path, woundPaint)
            canvas.drawPath(path, woundStrokePaint)
        }

        markerCorners?.let { corners ->
            if (corners.size >= 4) {
                for (i in 0..3) {
                    val start = corners[i]
                    val end = corners[(i + 1) % 4]
                    canvas.drawLine(start.first, start.second, end.first, end.second, markerPaint)
                }
            }
        }

        return result
    }

    fun capture() {
        _status.postValue("Capturing...")
    }

    fun reset() {
        _woundArea.postValue(0.0)
        _woundDepth.postValue(0.0)
        _overlayBitmap.postValue(null)
        _status.postValue("Ready - Place ArUco marker in view")
    }
}
