package com.applebyte.wounddetector.util

import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import java.util.Collections

data class MarkerDetection(
    val id: Int,
    val corners: MatOfPoint2f,
    val distance: Double,
    val sizePx: Float
)

data class SfMResult(
    val points3d: List<Point3>,
    val cameraPoses: List<Mat>,
    val depthMap: Mat?,
    val success: Boolean,
    val errorMessage: String?
)

class ArUcoDetector(private val markerSizeMm: Float = 50f) {

    private val markerCorners = listOf(
        floatArrayOf(0f, 0f, 1f),
        floatArrayOf(markerSizeMm, 0f, 1f),
        floatArrayOf(markerSizeMm, markerSizeMm, 1f),
        floatArrayOf(0f, markerSizeMm, 1f)
    )

    private val cameraMatrix = Mat(3, 3, CvType.CV_64FC1).apply {
        put(0, 0, 
            1.0, 0.0, 320.0,
            0.0, 1.0, 240.0,
            0.0, 0.0, 1.0
        )
    }

    private val distCoeffs = Mat.zeros(5, 1, CvType.CV_64FC1)

    fun detectMarkers(rgbaImage: Mat): List<MarkerDetection> {
        val detections = mutableListOf<MarkerDetection>()

        try {
            // Convert to grayscale if needed
            val grayImage = Mat()
            if (rgbaImage.channels() > 1) {
                Imgproc.cvtColor(rgbaImage, grayImage, Imgproc.COLOR_RGBA2GRAY)
            } else {
                rgbaImage.copyTo(grayImage)
            }

            // Ensure binary format for findContours
            val binary = Mat()
            Imgproc.threshold(grayImage, binary, 127.0, 255.0, Imgproc.THRESH_BINARY)

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area > 500 && area < 10000) {
                    val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                    val approx = MatOfPoint2f()
                    Imgproc.approxPolyDP(MatOfPoint2f(*contour.toArray()), approx, 0.02 * peri, true)

                    if (approx.rows() == 4) {
                        val sizePx = Math.sqrt(area.toDouble()).toFloat()
                        val distance = estimateDistance(sizePx)

                        detections.add(MarkerDetection(
                            id = 0,
                            corners = approx,
                            distance = distance,
                            sizePx = sizePx
                        ))
                    }
                }
            }

            hierarchy.release()
            binary.release()
            grayImage.release()

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return detections
    }

    private fun estimateDistance(markerSizePx: Float): Double {
        val knownSizePx = 100f
        val focalLength = 500.0
        return (markerSizeMm * focalLength) / markerSizePx.toDouble()
    }

    fun drawDetections(image: Mat, detections: List<MarkerDetection>): Mat {
        val outputImage = image.clone()
        
        for (detection in detections) {
            val corners = detection.corners.toArray()
            for (i in corners.indices) {
                val start = corners[i]
                val end = corners[(i + 1) % corners.size]
                Imgproc.line(outputImage, start, end, Scalar(0.0, 255.0, 0.0, 255.0), 2)
            }

            val center = Point()
            for (corner in corners) {
                center.x += corner.x
                center.y += corner.y
            }
            center.x /= corners.size
            center.y /= corners.size
            
            Imgproc.putText(outputImage, "Marker", 
                Point(center.x - 20, center.y), 
                Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, 
                Scalar(0.0, 255.0, 0.0), 2)
        }
        
        return outputImage
    }

    companion object {
        fun estimateScaleFactor(markerSizePx: Float, markerSizeMm: Float): Float {
            return markerSizeMm / markerSizePx
        }
    }
}
