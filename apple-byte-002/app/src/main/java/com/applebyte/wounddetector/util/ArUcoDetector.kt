package com.applebyte.wounddetector.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.sqrt

class ArUcoDetector {

    data class MarkerResult(
        val detected: Boolean,
        val sizeMm: Double,
        val corners: List<Pair<Float, Float>>?,
        val pixelSize: Float
    )

    fun detect(bitmap: Bitmap): MarkerResult {
        val width = bitmap.width
        val height = bitmap.height

        val scale = 0.5
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)

        val markerCorners = findArUcoMarker(scaledBitmap)

        return if (markerCorners != null && markerCorners.size >= 4) {
            val pixelSize = calculateMarkerPixelSize(markerCorners)

            MarkerResult(
                detected = true,
                sizeMm = 50.0,
                corners = markerCorners.map { Pair((it.first / scale).toFloat(), (it.second / scale).toFloat()) },
                pixelSize = pixelSize / scale.toFloat()
            )
        } else {
            MarkerResult(
                detected = false,
                sizeMm = 50.0,
                corners = null,
                pixelSize = 0f
            )
        }
    }

    private fun findArUcoMarker(bitmap: Bitmap): List<Pair<Float, Float>>? {
        val width = bitmap.width
        val height = bitmap.height

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0
        var hasWhite = false

        for (y in height / 4 until height * 3 / 4) {
            for (x in width / 4 until width * 3 / 4) {
                val pixel = pixels[y * width + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val isWhite = r > 200 && g > 200 && b > 200
                val isBlack = r < 50 && g < 50 && b < 50

                if (isWhite || isBlack) {
                    hasWhite = true
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (!hasWhite || maxX - minX < 20 || maxY - minY < 20) {
            return null
        }

        val padding = 10
        minX = (minX - padding).coerceAtLeast(0)
        minY = (minY - padding).coerceAtLeast(0)
        maxX = (maxX + padding).coerceAtMost(width - 1)
        maxY = (maxY + padding).coerceAtMost(height - 1)

        val markerPixels = mutableListOf<Pair<Int, Int>>()
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                val pixel = pixels[y * width + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                val brightness = (r + g + b) / 3
                if (brightness > 128) {
                    markerPixels.add(Pair(x, y))
                }
            }
        }

        if (markerPixels.size < 16) {
            return null
        }

        val centerX = markerPixels.map { it.first }.average().toFloat()
        val centerY = markerPixels.map { it.second }.average().toFloat()

        val halfWidth = (maxX - minX) / 2f
        val halfHeight = (maxY - minY) / 2f

        return listOf(
            Pair(centerX - halfWidth, centerY - halfHeight),
            Pair(centerX + halfWidth, centerY - halfHeight),
            Pair(centerX + halfWidth, centerY + halfHeight),
            Pair(centerX - halfWidth, centerY + halfHeight)
        )
    }

    private fun calculateMarkerPixelSize(corners: List<Pair<Float, Float>>): Float {
        if (corners.size < 4) return 0f

        val dx = corners[1].first - corners[0].first
        val dy = corners[1].second - corners[0].second
        val topWidth = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

        val dx2 = corners[2].first - corners[1].first
        val dy2 = corners[2].second - corners[1].second
        val rightHeight = sqrt((dx2 * dx2 + dy2 * dy2).toDouble()).toFloat()

        return (topWidth + rightHeight) / 2f
    }
}
