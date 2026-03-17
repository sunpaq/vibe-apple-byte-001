package com.applebyte.wounddetector.util

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

class WoundDetector {

    data class WoundAnalysis(
        val woundAreaPx: Double,
        val woundAreaMm2: Double,
        val estimatedDepthMm: Double,
        val woundContour: MatOfPoint?,
        val woundCenter: Point?,
        val woundMask: Mat,
        val success: Boolean,
        val errorMessage: String?
    )

    fun detectWound(image: Mat, markerSizePx: Float): WoundAnalysis {
        try {
            val hsvImage = Mat()
            Imgproc.cvtColor(image, hsvImage, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsvImage, hsvImage, Imgproc.COLOR_RGB2HSV)

            val lowerBrown = Scalar(0.0, 30.0, 20.0)
            val upperBrown = Scalar(30.0, 180.0, 120.0)

            val brownMask = Mat()
            Core.inRange(hsvImage, lowerBrown, upperBrown, brownMask)

            val lowerDark = Scalar(0.0, 0.0, 0.0)
            val upperDark = Scalar(180.0, 255.0, 50.0)

            val darkMask = Mat()
            Core.inRange(hsvImage, lowerDark, upperDark, darkMask)

            val woundMask = Mat()
            Core.bitwise_or(brownMask, darkMask, woundMask)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(5.0, 5.0))
            Imgproc.morphologyEx(woundMask, woundMask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(woundMask, woundMask, Imgproc.MORPH_OPEN, kernel)

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(woundMask, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            if (contours.isEmpty()) {
                return WoundAnalysis(
                    woundAreaPx = 0.0,
                    woundAreaMm2 = 0.0,
                    estimatedDepthMm = 0.0,
                    woundContour = null,
                    woundCenter = null,
                    woundMask = woundMask,
                    success = false,
                    errorMessage = "No wound detected"
                )
            }

            val validContours = contours.filter { contour ->
                val area = Imgproc.contourArea(contour)
                area > 500 && area < image.width() * image.height() * 0.5
            }

            if (validContours.isEmpty()) {
                return WoundAnalysis(
                    woundAreaPx = 0.0,
                    woundAreaMm2 = 0.0,
                    estimatedDepthMm = 0.0,
                    woundContour = null,
                    woundCenter = null,
                    woundMask = woundMask,
                    success = false,
                    errorMessage = "No valid wound contours found"
                )
            }

            val largestContour = validContours.maxByOrNull { Imgproc.contourArea(it) }!!
            val woundAreaPx = Imgproc.contourArea(largestContour)

            val moment = Imgproc.moments(largestContour)
            val woundCenter = if (moment.m00 != 0.0) {
                Point(moment.m10 / moment.m00, moment.m01 / moment.m00)
            } else {
                null
            }

            val scaleFactor = calculateScaleFactor(markerSizePx)
            val woundAreaMm2 = woundAreaPx * scaleFactor * scaleFactor

            val estimatedDepthMm = estimateWoundDepth(woundAreaMm2)

            return WoundAnalysis(
                woundAreaPx = woundAreaPx,
                woundAreaMm2 = woundAreaMm2,
                estimatedDepthMm = estimatedDepthMm,
                woundContour = largestContour,
                woundCenter = woundCenter,
                woundMask = woundMask,
                success = true,
                errorMessage = null
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return WoundAnalysis(
                woundAreaPx = 0.0,
                woundAreaMm2 = 0.0,
                estimatedDepthMm = 0.0,
                woundContour = null,
                woundCenter = null,
                woundMask = Mat(),
                success = false,
                errorMessage = e.message
            )
        }
    }

    fun detectWoundOnApple(image: Mat, arucoCorners: MatOfPoint2f?, markerSizeMm: Float = 50f): WoundAnalysis {
        val markerSizePx = if (arucoCorners != null) {
            val width = Math.sqrt(
                Math.pow(arucoCorners.toArray()[1].x - arucoCorners.toArray()[0].x, 2.0) +
                Math.pow(arucoCorners.toArray()[1].y - arucoCorners.toArray()[0].y, 2.0)
            )
            width.toFloat()
        } else {
            100f
        }

        return detectWound(image, markerSizePx)
    }

    private fun calculateScaleFactor(markerSizePx: Float, markerSizeMm: Float = 50f): Double {
        return markerSizeMm.toDouble() / markerSizePx.toDouble()
    }

    private fun estimateWoundDepth(woundAreaMm2: Double): Double {
        val avgAppleRadius = 40.0
        val areaAsRadius = Math.sqrt(woundAreaMm2 / Math.PI)
        val depthRatio = areaAsRadius / avgAppleRadius
        val maxDepth = 15.0
        return depthRatio * maxDepth
    }

    fun drawWoundContour(image: Mat, contour: MatOfPoint?, color: Scalar = Scalar(255.0, 0.0, 0.0, 255.0)): Mat {
        val output = image.clone()
        if (contour != null) {
            Imgproc.drawContours(output, listOf(contour), -1, color, 2)
        }
        return output
    }

    fun segmentApple(image: Mat): Mat? {
        try {
            val hsvImage = Mat()
            Imgproc.cvtColor(image, hsvImage, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsvImage, hsvImage, Imgproc.COLOR_RGB2HSV)

            val lowerRed1 = Scalar(0.0, 50.0, 30.0)
            val upperRed1 = Scalar(10.0, 255.0, 255.0)
            val lowerRed2 = Scalar(160.0, 50.0, 30.0)
            val upperRed2 = Scalar(180.0, 255.0, 255.0)

            val mask1 = Mat()
            val mask2 = Mat()
            Core.inRange(hsvImage, lowerRed1, upperRed1, mask1)
            Core.inRange(hsvImage, lowerRed2, upperRed2, mask2)

            val appleMask = Mat()
            Core.bitwise_or(mask1, mask2, appleMask)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))
            Imgproc.morphologyEx(appleMask, appleMask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.morphologyEx(appleMask, appleMask, Imgproc.MORPH_OPEN, kernel)

            return appleMask
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
