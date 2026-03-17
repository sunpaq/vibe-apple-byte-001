package com.example.applewounddetector.domain.repository

import com.example.applewounddetector.domain.model.MeasurementResult

interface WoundDetectionRepository {
    fun initialize()
    fun release()
    suspend fun detectWound(imageData: ByteArray, width: Int, height: Int): MeasurementResult?
    fun isDepthAvailable(): Boolean
}
