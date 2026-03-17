package com.example.applewounddetector.domain.usecase

import com.example.applewounddetector.domain.model.MeasurementResult
import com.example.applewounddetector.domain.repository.WoundDetectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DetectWoundUseCase(
    private val repository: WoundDetectionRepository
) {
    suspend operator fun invoke(
        imageData: ByteArray,
        width: Int,
        height: Int
    ): MeasurementResult? = withContext(Dispatchers.Default) {
        repository.detectWound(imageData, width, height)
    }
}

class InitializeArUseCase(
    private val repository: WoundDetectionRepository
) {
    operator fun invoke() {
        repository.initialize()
    }
}

class ReleaseArUseCase(
    private val repository: WoundDetectionRepository
) {
    operator fun invoke() {
        repository.release()
    }
}

class CheckDepthAvailableUseCase(
    private val repository: WoundDetectionRepository
) {
    operator fun invoke(): Boolean {
        return repository.isDepthAvailable()
    }
}
