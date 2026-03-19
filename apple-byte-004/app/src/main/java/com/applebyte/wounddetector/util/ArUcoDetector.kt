package com.applebyte.wounddetector.util

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.*

data class MarkerDetection(
    val id: Int,
    val corners: MatOfPoint2f,
    val distance: Double,
    val sizePx: Float,
    val confidence: Double
)

data class SfMResult(
    val points3d: List<Point3>,
    val cameraPoses: List<Mat>,
    val depthMap: Mat?,
    val success: Boolean,
    val errorMessage: String?
)

class ArUcoDetector(private val markerSizeMm: Float = 50f) {

    private val minContourArea = 800
    private val maxContourArea = 50000
    private val minAspectRatio = 0.7f
    private val maxAspectRatio = 1.4f
    private val minConfidence = 0.5

    private val cameraMatrix = Mat(3, 3, CvType.CV_64FC1).apply {
        put(0, 0, 1.0, 0.0, 320.0, 0.0, 1.0, 240.0, 0.0, 0.0, 1.0)
    }

    private val distCoeffs = Mat.zeros(5, 1, CvType.CV_64FC1)

    private val arucoDictionary = generateArUcoDictionary()
    
    private fun generateArUcoDictionary(): Map<Int, List<Int>> {
        return mapOf(
            0 to listOf(0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 1, 0, 1),
            1 to listOf(1, 0, 0, 1, 0, 1, 1, 0, 1, 1, 0, 0, 1, 0, 1, 0),
            2 to listOf(1, 0, 1, 0, 1, 1, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1),
            3 to listOf(1, 1, 0, 0, 0, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 0),
            4 to listOf(0, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 0, 1, 0, 0, 1),
            5 to listOf(0, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 1, 0, 0, 1),
            6 to listOf(0, 0, 1, 1, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 1, 0),
            7 to listOf(1, 0, 0, 1, 1, 1, 1, 0, 0, 0, 1, 0, 1, 1, 0, 0),
            8 to listOf(1, 1, 1, 0, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 0, 1),
            9 to listOf(0, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 1, 0)
        )
    }

    fun detectMarkers(rgbaImage: Mat): List<MarkerDetection> {
        val detections = mutableListOf<MarkerDetection>()

        try {
            val grayImage = Mat()
            if (rgbaImage.channels() > 1) {
                Imgproc.cvtColor(rgbaImage, grayImage, Imgproc.COLOR_RGBA2GRAY)
            } else {
                rgbaImage.copyTo(grayImage)
            }

            val blurred = Mat()
            Imgproc.GaussianBlur(grayImage, blurred, Size(3.0, 3.0), 0.0)

            val binary = Mat()
            Imgproc.adaptiveThreshold(
                blurred, binary, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                11, 2.0
            )

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel)

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(binary.clone(), contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < minContourArea || area > maxContourArea) continue

                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.03 * peri, true)

                if (approx.rows() != 4) continue

                val boundingRect = Imgproc.boundingRect(contour)
                val aspectRatio = boundingRect.width.toFloat() / boundingRect.height.toFloat()
                if (aspectRatio < minAspectRatio || aspectRatio > maxAspectRatio) continue

                val isConvex = Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))
                if (!isConvex) continue

                val orderedCorners = orderCorners(approx)
                if (orderedCorners == null) continue

                val markerId = detectMarkerId(orderedCorners, binary)
                if (markerId == -1) continue

                val confidence = calculateConfidence(orderedCorners, binary, grayImage)
                if (confidence < minConfidence) continue

                val refinedCorners = refineCorner(orderedCorners, grayImage)
                val sizePx = calculateMarkerSize(refinedCorners)
                val distance = estimateDistance(sizePx)

                detections.add(MarkerDetection(
                    id = markerId,
                    corners = refinedCorners,
                    distance = distance,
                    sizePx = sizePx,
                    confidence = confidence
                ))

                approx.release()
            }

            hierarchy.release()
            binary.release()
            blurred.release()
            grayImage.release()

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return detections.sortedByDescending { it.confidence }
    }

    private fun detectMarkerId(corners: MatOfPoint2f, binary: Mat): Int {
        val points = corners.toArray()
        if (points.size != 4) return -1

        val width = sqrt((points[1].x - points[0].x).pow(2) + (points[1].y - points[0].y).pow(2)).toInt()
        val height = sqrt((points[3].x - points[0].x).pow(2) + (points[3].y - points[0].y).pow(2)).toInt()
        
        if (width < 20 || height < 20) return -1

        val srcPoints = arrayOf(
            Point(0.0, 0.0),
            Point(width.toDouble(), 0.0),
            Point(width.toDouble(), height.toDouble()),
            Point(0.0, height.toDouble())
        )
        
        val dstPoints = points.toList()
        
        val srcMat = MatOfPoint2f(*srcPoints)
        val dstMat = MatOfPoint2f(dstPoints[0], dstPoints[1], dstPoints[2], dstPoints[3])
        
        val transformMatrix = Imgproc.getPerspectiveTransform(srcMat, dstMat)
        
        val warpedSize = Size(width.toDouble(), height.toDouble())
        val warped = Mat(warpedSize, CvType.CV_8UC1)
        Imgproc.warpPerspective(binary, warped, transformMatrix, warpedSize)

        val gridSize = 4
        val cellWidth = width / gridSize
        val cellHeight = height / gridSize
        
        val border = 1
        val innerSize = gridSize - 2 * border
        
        val innerCellWidth = cellWidth * border
        val innerCellHeight = cellHeight * border
        
        val innerStartX = border * cellWidth
        val innerStartY = border * cellHeight

        val pattern = mutableListOf<Int>()
        
        for (row in 0 until innerSize) {
            for (col in 0 until innerSize) {
                val cellX = (innerStartX + col * innerCellWidth).toInt().coerceIn(0, warped.cols() - 1)
                val cellY = (innerStartY + row * innerCellHeight).toInt().coerceIn(0, warped.rows() - 1)
                val cellW = innerCellWidth.toInt().coerceIn(1, warped.cols() - cellX)
                val cellH = innerCellHeight.toInt().coerceIn(1, warped.rows() - cellY)
                
                if (cellW <= 0 || cellH <= 0) continue
                
                val cellRect = Rect(cellX, cellY, cellW, cellH)
                val cellValue = Core.mean(warped.submat(cellRect)).`val`[0]
                
                pattern.add(if (cellValue > 127) 0 else 1)
            }
        }

        warped.release()
        transformMatrix.release()
        srcMat.release()
        dstMat.release()

        if (pattern.size < 16) return -1

        var bestMatch = -1
        var bestHamming = Int.MAX_VALUE
        
        for ((id, expected) in arucoDictionary) {
            if (expected.size != pattern.size) continue
            
            var hamming = 0
            for (i in pattern.indices) {
                if (pattern[i] != expected[i]) hamming++
            }
            
            if (hamming < bestHamming) {
                bestHamming = hamming
                bestMatch = id
            }
        }

        return if (bestHamming <= 4) bestMatch else -1
    }

    private fun orderCorners(corners: MatOfPoint2f): MatOfPoint2f? {
        val points = corners.toArray()
        if (points.size != 4) return null

        val sortedByY = points.sortedBy { it.y }
        val top = sortedByY.take(2).sortedBy { it.x }
        val bottom = sortedByY.takeLast(2).sortedBy { it.x }

        if (top.size != 2 || bottom.size != 2) return null

        val ordered = MatOfPoint2f()
        ordered.fromArray(top[0], top[1], bottom[1], bottom[0])
        return ordered
    }

    private fun refineCorner(corners: MatOfPoint2f, grayImage: Mat): MatOfPoint2f {
        val termCriteria = TermCriteria(TermCriteria.EPS + TermCriteria.COUNT, 30, 0.01)
        val refined = MatOfPoint2f()
        
        try {
            Imgproc.cornerSubPix(
                grayImage, corners, Size(3.0, 3.0),
                Size(-1.0, -1.0), termCriteria
            )
            corners.copyTo(refined)
        } catch (e: Exception) {
            corners.copyTo(refined)
        }
        
        return refined
    }

    private fun calculateConfidence(corners: MatOfPoint2f, binary: Mat, grayImage: Mat): Double {
        val points = corners.toArray()
        if (points.size != 4) return 0.0

        var totalContrast = 0.0
        var edgeScore = 0.0
        var cornerScore = 0.0

        for (point in points) {
            val x = point.x.toInt().coerceIn(5, grayImage.cols() - 5)
            val y = point.y.toInt().coerceIn(5, grayImage.rows() - 5)
            
            val roi = grayImage.submat(Rect(x - 5, y - 5, 10, 10))
            val meanMat = MatOfDouble()
            val stddevMat = MatOfDouble()
            Core.meanStdDev(roi, meanMat, stddevMat)
            totalContrast += if (stddevMat.rows() > 0) stddevMat.get(0, 0)[0] else 0.0
            roi.release()
            meanMat.release()
            stddevMat.release()
        }

        val edges = listOf(
            Pair(points[0], points[1]),
            Pair(points[1], points[2]),
            Pair(points[2], points[3]),
            Pair(points[3], points[0])
        )

        for ((start, end) in edges) {
            val length = sqrt((end.x - start.x).pow(2) + (end.y - start.y).pow(2))
            edgeScore += length / 100.0
        }

        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % 4]
            val p3 = points[(i + 2) % 4]

            val v1x = p1.x - p2.x
            val v1y = p1.y - p2.y
            val v2x = p3.x - p2.x
            val v2y = p3.y - p2.y

            val dot = v1x * v2x + v1y * v2y
            val mag1 = sqrt(v1x.pow(2) + v1y.pow(2))
            val mag2 = sqrt(v2x.pow(2) + v2y.pow(2))

            if (mag1 > 0 && mag2 > 0) {
                val angle = acos((dot / (mag1 * mag2)).coerceIn(-1.0, 1.0))
                cornerScore += (90.0 - abs(Math.toDegrees(angle) - 90)) / 90.0
            }
        }

        cornerScore /= 4.0
        totalContrast /= 4.0

        return (totalContrast / 50.0 * 0.3 + edgeScore.coerceAtMost(1.0) * 0.3 + cornerScore * 0.4).coerceIn(0.0, 1.0)
    }

    private fun calculateMarkerSize(corners: MatOfPoint2f): Float {
        val points = corners.toArray()
        val width = sqrt(
            (points[1].x - points[0].x).pow(2) + 
            (points[1].y - points[0].y).pow(2)
        )
        val height = sqrt(
            (points[3].x - points[0].x).pow(2) + 
            (points[3].y - points[0].y).pow(2)
        )
        return ((width + height) / 2).toFloat()
    }

    private fun estimateDistance(markerSizePx: Float): Double {
        val focalLength = 500.0
        return (markerSizeMm * focalLength) / markerSizePx.toDouble()
    }

    fun drawDetections(image: Mat, detections: List<MarkerDetection>): Mat {
        val outputImage = image.clone()
        
        for (detection in detections) {
            val corners = detection.corners.toArray()
            val color = if (detection.confidence > 0.7) LIGHT_GREEN else YELLOW
            
            for (i in corners.indices) {
                val start = corners[i]
                val end = corners[(i + 1) % corners.size]
                Imgproc.line(outputImage, start, end, color, 3)
            }

            val center = Point(
                corners.map { it.x }.average(),
                corners.map { it.y }.average()
            )
            
            val idText = "ID: ${detection.id}"
            val confText = "${(detection.confidence * 100).toInt()}%"
            
            Imgproc.putText(outputImage, idText, 
                Point(center.x - 30, center.y - 15), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, color, 2)
            Imgproc.putText(outputImage, confText, 
                Point(center.x - 25, center.y + 10), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)
        }
        
        return outputImage
    }

    companion object {
        val LIGHT_GREEN = Scalar(144.0, 238.0, 144.0, 255.0)
        val YELLOW = Scalar(0.0, 255.0, 255.0, 255.0)
        
        fun estimateScaleFactor(markerSizePx: Float, markerSizeMm: Float): Float {
            return markerSizeMm / markerSizePx
        }
    }
}
