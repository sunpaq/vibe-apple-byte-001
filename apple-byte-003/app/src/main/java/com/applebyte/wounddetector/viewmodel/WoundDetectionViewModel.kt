package com.applebyte.wounddetector.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.applebyte.wounddetector.util.WoundDetectionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WoundDetectionViewModel : ViewModel() {

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Ready)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    private val _capturedPhotos = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedPhotos: StateFlow<List<Bitmap>> = _capturedPhotos.asStateFlow()

    private val _photoCount = MutableStateFlow(0)
    val photoCount: StateFlow<Int> = _photoCount.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingState>(ProcessingState.Idle)
    val processingState: StateFlow<ProcessingState> = _processingState.asStateFlow()

    private val _detectionResult = MutableStateFlow<WoundDetectionResult?>(null)
    val detectionResult: StateFlow<WoundDetectionResult?> = _detectionResult.asStateFlow()

    private val _instructionText = MutableStateFlow("")
    val instructionText: StateFlow<String> = _instructionText.asStateFlow()

    private val _markerDetected = MutableStateFlow(false)
    val markerDetected: StateFlow<Boolean> = _markerDetected.asStateFlow()

    companion object {
        const val MIN_PHOTOS_REQUIRED = 6
        const val MAX_PHOTOS = 10
    }

    fun updateInstruction(text: String) {
        _instructionText.value = text
    }

    fun setMarkerDetected(detected: Boolean) {
        _markerDetected.value = detected
    }

    fun addPhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            val currentPhotos = _capturedPhotos.value.toMutableList()
            currentPhotos.add(bitmap)
            _capturedPhotos.value = currentPhotos
            _photoCount.value = currentPhotos.size

            when {
                currentPhotos.size < MIN_PHOTOS_REQUIRED -> {
                    _captureState.value = CaptureState.Capturing
                    _instructionText.value = "Move around apple (${currentPhotos.size}/$MIN_PHOTOS_REQUIRED minimum)"
                }
                currentPhotos.size < MAX_PHOTOS -> {
                    _captureState.value = CaptureState.MorePhotos
                    _instructionText.value = "More photos help accuracy (${currentPhotos.size}/$MAX_PHOTOS)"
                }
                else -> {
                    _captureState.value = CaptureState.ReadyToProcess
                    _instructionText.value = "Ready to process"
                }
            }
        }
    }

    fun setProcessing(processing: Boolean) {
        _processingState.value = if (processing) ProcessingState.Processing else ProcessingState.Idle
    }

    fun setProcessingStep(step: String) {
        _processingState.value = ProcessingState.Processing
    }

    fun updateProgress(progress: Int, total: Int) {
        _processingState.value = ProcessingState.Progress(progress, total)
    }

    fun setResult(result: WoundDetectionResult) {
        _detectionResult.value = result
        _processingState.value = ProcessingState.Complete
    }

    fun setError(error: String) {
        _processingState.value = ProcessingState.Error(error)
    }

    fun reset() {
        _capturedPhotos.value = emptyList()
        _photoCount.value = 0
        _captureState.value = CaptureState.Ready
        _markerDetected.value = false
        _processingState.value = ProcessingState.Idle
        _detectionResult.value = null
        _instructionText.value = "Place ArUco marker near apple"
    }
}

sealed class CaptureState {
    object Ready : CaptureState()
    object Capturing : CaptureState()
    object MorePhotos : CaptureState()
    object ReadyToProcess : CaptureState()
}

sealed class ProcessingState {
    object Idle : ProcessingState()
    object Processing : ProcessingState()
    data class Progress(val current: Int, val total: Int) : ProcessingState()
    object Complete : ProcessingState()
    data class Error(val message: String) : ProcessingState()
}
