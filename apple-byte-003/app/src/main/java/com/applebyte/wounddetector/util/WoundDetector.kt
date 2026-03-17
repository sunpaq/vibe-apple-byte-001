package com.applebyte.wounddetector.util

import android.graphics.Bitmap
import android.graphics.PointF
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

data class WoundDetectionResult(
    val woundAreaMm2: Float,
    val woundDepthMm: Float,
    val woundContour: List<PointF>,
    val woundBoundingRect: Rect,
    val woundMask: Mat
)

class WoundDetector {
    
    fun detectWound(bitmap: Bitmap, depthMap: Mat?, pixelsPerMm: Float): WoundDetectionResult? {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        val hsv = Mat()
        Imgproc.cvtColor(mat, hsv, Imgproc.COLOR_BGR2HSV)
        
        val lowerBrown = Scalar(0.0, 30.0, 20.0)
        val upperBrown = Scalar(40.0, 255.0, 150.0)
        
        val mask = Mat()
        Core.inRange(hsv, lowerBrown, upperBrown, mask)
        
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_OPEN, kernel)
        Imgproc.morphologyEx(mask, mask, Imgproc.MORPH_CLOSE, kernel)
        
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(mask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        
        if (contours.isEmpty()) {
            return null
        }
        
        val largestContour = contours.maxByOrNull { Imgproc.contourArea(it) } ?: return null
        val contourArea = Imgproc.contourArea(largestContour)
        
        if (contourArea < 100) {
            return null
        }
        
        val boundingRect = Imgproc.boundingRect(largestContour)
        
        val areaInMm2 = contourArea / (pixelsPerMm * pixelsPerMm)
        
        var depthMm = 0f
        if (depthMap != null) {
            val woundDepth = estimateWoundDepth(depthMap, boundingRect)
            depthMm = woundDepth
        }
        
        val contourPoints = largestContour.toList().map { PointF(it.x.toFloat(), it.y.toFloat()) }
        
        return WoundDetectionResult(
            woundAreaMm2 = areaInMm2.toFloat(),
            woundDepthMm = depthMm,
            woundContour = contourPoints,
            woundBoundingRect = boundingRect,
            woundMask = mask
        )
    }
    
    private fun estimateWoundDepth(depthMap: Mat, woundRect: Rect): Float {
        val woundDepth = getAverageDepthAtRegion(depthMap, woundRect)
        
        val margin = 20
        val surroundingRect = Rect(
            (woundRect.x - margin).coerceAtLeast(0),
            (woundRect.y - margin).coerceAtLeast(0),
            woundRect.width + margin * 2,
            woundRect.height + margin * 2
        )
        
        val surroundingDepth = getAverageDepthAtRegion(depthMap, surroundingRect)
        
        return kotlin.math.abs(surroundingDepth - woundDepth) * 10f
    }
    
    private fun getAverageDepthAtRegion(depthMap: Mat, rect: Rect): Float {
        var sum = 0f
        var count = 0
        
        val startX = rect.x.coerceIn(0, depthMap.cols() - 1)
        val endX = (rect.x + rect.width).coerceIn(0, depthMap.cols() - 1)
        val startY = rect.y.coerceIn(0, depthMap.rows() - 1)
        val endY = (rect.y + rect.height).coerceIn(0, depthMap.rows() - 1)
        
        for (y in startY until endY) {
            for (x in startX until endX) {
                val depthArray = depthMap.get(y, x)
                if (depthArray.isNotEmpty()) {
                    val depth = depthArray[0].toFloat()
                    if (depth > 0 && depth < 255) {
                        sum += depth
                        count++
                    }
                }
            }
        }
        
        return if (count > 0) sum / count else 0f
    }
    
    fun getWoundContourOverlay(bitmap: Bitmap, result: WoundDetectionResult): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        val contour = MatOfPoint(*result.woundContour.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        
        Imgproc.drawContours(mat, listOf(contour), -1, Scalar(255.0, 109.0, 0.0), 3)
        
        val outputBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, outputBitmap)
        
        return outputBitmap
    }
}
