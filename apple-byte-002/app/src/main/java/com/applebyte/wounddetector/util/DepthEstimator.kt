package com.applebyte.wounddetector.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DepthEstimator {

    fun estimateDepth(
        woundCenter: Pair<Float, Float>?,
        pixelToMmRatio: Double
    ): Double {
        if (woundCenter == null) return 0.0

        val baseDepthMm = 0.0

        val estimatedDepthByWoundSize = calculateDepthFromWoundAppearance(woundCenter, pixelToMmRatio)

        return estimatedDepthByWoundSize
    }

    private fun calculateDepthFromWoundAppearance(
        woundCenter: Pair<Float, Float>,
        pixelToMmRatio: Double
    ): Double {
        val centerX = woundCenter.first
        val centerY = woundCenter.second

        val shadowFactor = estimateShadowIntensity(centerX, centerY)

        val depthMm = when {
            shadowFactor > 0.7 -> 8.0 + (shadowFactor - 0.7) * 20
            shadowFactor > 0.5 -> 4.0 + (shadowFactor - 0.5) * 20
            shadowFactor > 0.3 -> 1.5 + (shadowFactor - 0.3) * 12.5
            shadowFactor > 0.1 -> 0.5 + (shadowFactor - 0.1) * 5
            else -> 0.2
        }

        return depthMm
    }

    private fun estimateShadowIntensity(x: Float, y: Float): Double {
        val normalizedX = x / 640.0
        val normalizedY = y / 480.0

        val centerDist = kotlin.math.sqrt(
            (normalizedX - 0.5) * (normalizedX - 0.5) +
            (normalizedY - 0.5) * (normalizedY - 0.5)
        )

        val cornerShadow = 1.0 - (centerDist * 1.5).coerceAtMost(1.0)

        return cornerShadow * 0.5 + 0.3
    }

    fun calculateRealDepthFromDepthMap(
        depthMap: FloatArray,
        width: Int,
        height: Int,
        woundRegion: List<Pair<Float, Float>>,
        cameraIntrinsics: CameraIntrinsics
    ): Double {
        if (woundRegion.isEmpty()) return 0.0

        val woundDepths = mutableListOf<Float>()

        for (point in woundRegion) {
            val x = point.first.toInt().coerceIn(0, width - 1)
            val y = point.second.toInt().coerceIn(0, height - 1)
            val idx = y * width + x

            if (idx < depthMap.size) {
                woundDepths.add(depthMap[idx])
            }
        }

        if (woundDepths.isEmpty()) return 0.0

        val woundAvgDepth = woundDepths.average().toFloat()

        val surroundingDepths = mutableListOf<Float>()
        val minX = woundRegion.minOf { it.first }.toInt().coerceIn(0, width - 1)
        val maxX = woundRegion.maxOf { it.first }.toInt().coerceIn(0, width - 1)
        val minY = woundRegion.minOf { it.second }.toInt().coerceIn(0, height - 1)
        val maxY = woundRegion.maxOf { it.second }.toInt().coerceIn(0, height - 1)

        val margin = 20
        for (y in maxOf(0, minY - margin)..minOf(height - 1, maxY + margin)) {
            for (x in maxOf(0, minX - margin)..minOf(width - 1, maxX + margin)) {
                val isInWound = woundRegion.any { px ->
                    abs(px.first - x) < 10 && abs(px.second - y) < 10
                }
                if (!isInWound) {
                    val idx = y * width + x
                    if (idx < depthMap.size && depthMap[idx] > 0) {
                        surroundingDepths.add(depthMap[idx])
                    }
                }
            }
        }

        if (surroundingDepths.isEmpty()) return 0.0

        val surfaceAvgDepth = surroundingDepths.average().toFloat()

        val depthDiff = surfaceAvgDepth - woundAvgDepth

        return if (depthDiff > 0) {
            depthDiff * 1000.0
        } else {
            0.0
        }
    }

    data class CameraIntrinsics(
        val fx: Float,
        val fy: Float,
        val cx: Float,
        val cy: Float
    )
}
