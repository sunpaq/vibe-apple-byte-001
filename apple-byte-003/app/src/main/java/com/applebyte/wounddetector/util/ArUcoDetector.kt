package com.applebyte.wounddetector.util

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ArUcoResult(
    val markerCorners: List<Point>,
    val markerId: Int,
    val markerSizeMm: Float,
    val rotationVector: Mat?,
    val translationVector: Mat?,
    val pixelsPerMm: Float
)

class ArUcoDetector {
    
    companion object {
        const val TAG = "ArUcoDetector"
        const val MARKER_ID = 0
        const val STANDARD_MARKER_SIZE_MM = 50f
    }

    fun detectMarkers(bitmap: Bitmap): ArUcoResult? {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        
        val gray: Mat = if (mat.channels() > 1) {
            val grayTemp = Mat()
            Imgproc.cvtColor(mat, grayTemp, Imgproc.COLOR_BGR2GRAY)
            grayTemp
        } else {
            mat
        }
        
        val result = detectArUcoMarker(gray, mat.size())
        if (result != null) {
            Log.d(TAG, "Marker detected successfully")
            return result
        }
        
        Log.d(TAG, "No marker detected")
        return null
    }
    
    private fun detectArUcoMarker(gray: Mat, imgSize: Size): ArUcoResult? {
        val markerCorners = findMarkerContours(gray, imgSize)
        if (markerCorners != null && markerCorners.size == 4) {
            val pixelsPerMm = calculatePixelsPerMm(markerCorners)
            return ArUcoResult(
                markerCorners = markerCorners,
                markerId = MARKER_ID,
                markerSizeMm = STANDARD_MARKER_SIZE_MM,
                rotationVector = null,
                translationVector = null,
                pixelsPerMm = pixelsPerMm
            )
        }
        
        return null
    }
    
    private fun findMarkerContours(gray: Mat, imgSize: Size): List<Point>? {
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)
        
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val dilated = Mat()
        Imgproc.dilate(edges, dilated, kernel, Point(-1.0, -1.0), 2)
        
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(dilated, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        
        val minArea = max(imgSize.width, imgSize.height) * max(imgSize.width, imgSize.height) * 0.001
        val maxArea = max(imgSize.width, imgSize.height) * max(imgSize.width, imgSize.height) * 0.25
        
        val squares = contours
            .filter { contour ->
                val area = Imgproc.contourArea(contour)
                area >= minArea && area <= maxArea
            }
            .mapNotNull { contour ->
                approximateSquare(contour)
            }
            .filter { corners ->
                corners.size == 4 && isValidSquare(corners, imgSize)
            }
            .sortedByDescending { corners ->
                calculateSquarenessScore(corners)
            }
        
        if (squares.isEmpty()) {
            return tryThresholdDetection(gray, imgSize)
        }
        
        return squares.firstOrNull()
    }
    
    private fun tryThresholdDetection(gray: Mat, imgSize: Size): List<Point>? {
        val thresholds = listOf(70.0, 90.0, 110.0, 130.0)
        
        for (threshold in thresholds) {
            val binary = Mat()
            Imgproc.threshold(gray, binary, threshold, 255.0, Imgproc.THRESH_BINARY_INV)
            
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            
            val minArea = max(imgSize.width, imgSize.height) * max(imgSize.width, imgSize.height) * 0.001
            val maxArea = max(imgSize.width, imgSize.height) * max(imgSize.width, imgSize.height) * 0.25
            
            val squares = contours
                .filter { contour ->
                    val area = Imgproc.contourArea(contour)
                    area >= minArea && area <= maxArea
                }
                .mapNotNull { contour ->
                    approximateSquare(contour)
                }
                .filter { corners ->
                    corners.size == 4 && isValidSquare(corners, imgSize)
                }
                .sortedByDescending { corners ->
                    calculateSquarenessScore(corners)
                }
            
            if (squares.isNotEmpty()) {
                return squares.first()
            }
        }
        
        return tryAdaptiveThreshold(gray, imgSize)
    }
    
    private fun tryAdaptiveThreshold(gray: Mat, imgSize: Size): List<Point>? {
        val binary = Mat()
        Imgproc.adaptiveThreshold(
            gray, binary, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY_INV, 11, 2.0
        )
        
        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        
        val minArea = max(imgSize.width, imgSize.height) * max(imgSize.width, imgSize.height) * 0.001
        val maxArea = max(imgSize.width, imgSize.height) * max(imgSize.width, imgSize.height) * 0.25
        
        return contours
            .filter { contour ->
                val area = Imgproc.contourArea(contour)
                area >= minArea && area <= maxArea
            }
            .mapNotNull { contour ->
                approximateSquare(contour)
            }
            .filter { corners ->
                corners.size == 4 && isValidSquare(corners, imgSize)
            }
            .maxByOrNull { corners ->
                calculateSquarenessScore(corners)
            }
    }
    
    private fun approximateSquare(contour: MatOfPoint): List<Point>? {
        val perimeter = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
        if (perimeter < 30) return null
        
        val approx = MatOfPoint2f()
        Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * perimeter, true)
        
        val points = approx.toList()
        
        if (points.size < 4) return null
        
        return orderMarkerCorners(points.take(4))
    }
    
    private fun orderMarkerCorners(corners: List<Point>): List<Point> {
        if (corners.size != 4) return corners
        
        val centerX = corners.sumOf { it.x } / 4
        val centerY = corners.sumOf { it.y } / 4
        
        val sorted = corners.sortedBy { point ->
            val dx = point.x - centerX
            val dy = point.y - centerY
            kotlin.math.atan2(dy, dx)
        }
        
        val topLeft = sorted[0]
        val topRight = sorted[1]
        val bottomRight = sorted[2]
        val bottomLeft = sorted[3]
        
        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }
    
    private fun isValidSquare(corners: List<Point>, imgSize: Size): Boolean {
        if (corners.size != 4) return false
        
        val widths = listOf(
            distance(corners[0], corners[1]),
            distance(corners[2], corners[3])
        )
        val heights = listOf(
            distance(corners[1], corners[2]),
            distance(corners[3], corners[0])
        )
        
        val avgWidth = widths.average()
        val avgHeight = heights.average()
        
        if (avgWidth < 20 || avgHeight < 20) return false
        
        val aspectRatio = avgWidth / avgHeight
        if (aspectRatio < 0.5 || aspectRatio > 2.0) return false
        
        val widthRatio = abs(widths[0] - widths[1]) / avgWidth
        val heightRatio = abs(heights[0] - heights[1]) / avgHeight
        
        if (widthRatio > 0.3 || heightRatio > 0.3) return false
        
        return true
    }
    
    private fun calculateSquarenessScore(corners: List<Point>): Double {
        if (corners.size != 4) return 0.0
        
        val widths = listOf(
            distance(corners[0], corners[1]),
            distance(corners[2], corners[3])
        )
        val heights = listOf(
            distance(corners[1], corners[2]),
            distance(corners[3], corners[0])
        )
        
        val avgWidth = widths.average()
        val avgHeight = heights.average()
        
        val widthScore = 1.0 - (abs(widths[0] - widths[1]) / avgWidth)
        val heightScore = 1.0 - (abs(heights[0] - heights[1]) / avgHeight)
        
        val aspectRatio = avgWidth / avgHeight
        val aspectScore = 1.0 - abs(1.0 - aspectRatio)
        
        val angleScore = calculateAngleScore(corners)
        
        return widthScore * heightScore * aspectScore * angleScore
    }
    
    private fun calculateAngleScore(corners: List<Point>): Double {
        fun angle(p1: Point, vertex: Point, p2: Point): Double {
            val v1x = p1.x - vertex.x
            val v1y = p1.y - vertex.y
            val v2x = p2.x - vertex.x
            val v2y = p2.y - vertex.y
            
            val dot = v1x * v2x + v1y * v2y
            val mag1 = sqrt(v1x * v1x + v1y * v1y)
            val mag2 = sqrt(v2x * v2x + v2y * v2y)
            
            if (mag1 == 0.0 || mag2 == 0.0) return 0.0
            
            val cosAngle = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
            return kotlin.math.abs(kotlin.math.PI / 2 - kotlin.math.acos(cosAngle)) / (kotlin.math.PI / 2)
        }
        
        val angle1 = angle(corners[0], corners[1], corners[2])
        val angle2 = angle(corners[1], corners[2], corners[3])
        val angle3 = angle(corners[2], corners[3], corners[0])
        val angle4 = angle(corners[3], corners[0], corners[1])
        
        return (angle1 + angle2 + angle3 + angle4) / 4
    }
    
    private fun distance(p1: Point, p2: Point): Double {
        return sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y))
    }
    
    private fun calculatePixelsPerMm(corners: List<Point>): Float {
        val width = distance(corners[0], corners[1])
        val height = distance(corners[1], corners[2])
        val avgEdge = (width + height) / 2.0
        return (avgEdge / STANDARD_MARKER_SIZE_MM).toFloat()
    }

    fun getMarkerCornersForDrawing(corners: List<Point>): List<Point> {
        return corners
    }
}
