package com.applebyte.wounddetector.util

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.sqrt

class WoundDetector {

    data class WoundResult(
        val area: Double,
        val contour: List<Pair<Float, Float>>,
        val centerDepth: Pair<Float, Float>?
    )

    fun detectWound(
        bitmap: Bitmap,
        markerCorners: List<Pair<Float, Float>>?
    ): WoundResult {
        val width = bitmap.width
        val height = bitmap.height

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val woundMask = ByteArray(width * height)
        val woundPixels = mutableListOf<Pair<Int, Int>>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = pixels[y * width + x]
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                if (isWoundColor(r, g, b)) {
                    woundMask[y * width + x] = 1
                    woundPixels.add(Pair(x, y))
                }
            }
        }

        if (woundPixels.isEmpty()) {
            return WoundResult(0.0, emptyList(), null)
        }

        val smoothedMask = smoothMask(woundMask, width, height)

        val contour = findContour(smoothedMask, width, height)

        val area = smoothedMask.count { it == 1.toByte() }.toDouble()

        val center = if (woundPixels.isNotEmpty()) {
            val centerX = woundPixels.map { it.first }.average().toFloat()
            val centerY = woundPixels.map { it.second }.average().toFloat()
            Pair(centerX, centerY)
        } else null

        return WoundResult(area, contour, center)
    }

    private fun isWoundColor(r: Int, g: Int, b: Int): Boolean {
        val brownLower = 60
        val brownUpper = 150
        val maxRGB = maxOf(r, g, b)
        val minRGB = minOf(r, g, b)

        if (maxRGB > 50) {
            val saturation = if (maxRGB > 0) (maxRGB - minRGB).toFloat() / maxRGB else 0f
            val hue = calculateHue(r, g, b)

            val isBrownish = r in brownLower..brownUpper &&
                    g in brownLower..brownUpper &&
                    b in brownLower..brownUpper &&
                    abs(r - g) < 40 &&
                    abs(r - b) < 40

            val isDarkRotten = r < 80 && g < 80 && b < 80 && (r + g + b) > 50

            val isDiscolored = (hue > 20 && hue < 50) && saturation > 0.2f

            return isBrownish || isDarkRotten || isDiscolored
        }

        return false
    }

    private fun calculateHue(r: Int, g: Int, b: Int): Float {
        val rNorm = r / 255f
        val gNorm = g / 255f
        val bNorm = b / 255f

        val max = maxOf(rNorm, gNorm, bNorm)
        val min = minOf(rNorm, gNorm, bNorm)
        val delta = max - min

        var hue = when {
            delta == 0f -> 0f
            max == rNorm -> 60f * (((gNorm - bNorm) / delta) % 6)
            max == gNorm -> 60f * (((bNorm - rNorm) / delta) + 2)
            else -> 60f * (((rNorm - gNorm) / delta) + 4)
        }

        if (hue < 0) hue += 360f

        return hue
    }

    private fun smoothMask(mask: ByteArray, width: Int, height: Int): ByteArray {
        val result = mask.copyOf()

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x
                var neighbors = 0

                for (dy in -1..1) {
                    for (dx in -1..1) {
                        if (mask[(y + dy) * width + (x + dx)].toInt() == 1) {
                            neighbors++
                        }
                    }
                }

                result[idx] = if (neighbors >= 5) 1 else 0
            }
        }

        return result
    }

    private fun findContour(mask: ByteArray, width: Int, height: Int): List<Pair<Float, Float>> {
        val contour = mutableListOf<Pair<Float, Float>>()

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val idx = y * width + x

                if (mask[idx] == 1.toByte()) {
                    val left = mask[y * width + (x - 1)]
                    val right = mask[y * width + (x + 1)]
                    val top = mask[(y - 1) * width + x]
                    val bottom = mask[(y + 1) * width + x]

                    if (left == 0.toByte() || right == 0.toByte() ||
                        top == 0.toByte() || bottom == 0.toByte()) {
                        contour.add(Pair(x.toFloat(), y.toFloat()))
                    }
                }
            }
        }

        return simplifyContour(contour, 10)
    }

    private fun simplifyContour(contour: List<Pair<Float, Float>>, tolerance: Int): List<Pair<Float, Float>> {
        if (contour.size < 3) return contour

        val simplified = mutableListOf<Pair<Float, Float>>()

        var lastAdded = contour.first()
        simplified.add(lastAdded)

        for (i in 1 until contour.size) {
            val point = contour[i]
            val dx = point.first - lastAdded.first
            val dy = point.second - lastAdded.second
            val dist = sqrt((dx * dx + dy * dy).toDouble())

            if (dist > tolerance) {
                simplified.add(point)
                lastAdded = point
            }
        }

        return simplified
    }
}
