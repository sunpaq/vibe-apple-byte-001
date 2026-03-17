package com.example.applewounddetector.domain.model

data class ArUcoMarker(
    val id: Int,
    val corners: List<Point2D>,
    val size: Float
)

data class Point2D(
    val x: Float,
    val y: Float
)

data class Point3D(
    val x: Float,
    val y: Float,
    val z: Float
)

data class WoundDetectionResult(
    val woundArea: Float,
    val woundDepth: Float,
    val woundCenter: Point2D?,
    val woundContour: List<Point2D>,
    val timestamp: Long = System.currentTimeMillis()
)

data class MeasurementResult(
    val areaMm2: Float,
    val depthMm: Float,
    val markerDetected: Boolean,
    val depthAvailable: Boolean,
    val status: MeasurementStatus
)

enum class MeasurementStatus {
    IDLE,
    INITIALIZING,
    AR_READY,
    MARKER_DETECTED,
    MEASURING,
    COMPLETED,
    ERROR
}
