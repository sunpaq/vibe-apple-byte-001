package com.example.applewounddetector.ui.ar

import android.app.Application
import android.graphics.ImageFormat
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.applewounddetector.data.repository.WoundDetectionRepositoryImpl
import com.example.applewounddetector.domain.model.MeasurementResult
import com.example.applewounddetector.domain.model.MeasurementStatus
import com.example.applewounddetector.domain.repository.WoundDetectionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ArViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: WoundDetectionRepository = WoundDetectionRepositoryImpl(application)
    
    private val _measurementState = MutableStateFlow(MeasurementUiState())
    val measurementState: StateFlow<MeasurementUiState> = _measurementState.asStateFlow()
    
    private val _statusMessage = MutableStateFlow("Initializing AR...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()
    
    init {
        initializeAr()
    }
    
    private fun initializeAr() {
        viewModelScope.launch {
            _measurementState.value = _measurementState.value.copy(
                status = MeasurementStatus.INITIALIZING
            )
            _statusMessage.value = "Initializing AR..."
            
            repository.initialize()
            
            val depthAvailable = repository.isDepthAvailable()
            _measurementState.value = _measurementState.value.copy(
                status = MeasurementStatus.AR_READY,
                depthAvailable = depthAvailable
            )
            _statusMessage.value = if (depthAvailable) {
                "AR Ready - Depth Available"
            } else {
                "AR Ready - Depth Not Available"
            }
        }
    }
    
    fun processImage(imageProxy: ImageProxy) {
        viewModelScope.launch {
            try {
                val image = imageProxy.image ?: return@launch
                
                val yBuffer = image.planes[0].buffer
                val ySize = yBuffer.remaining()
                val imageData = ByteArray(ySize)
                yBuffer.get(imageData)
                
                val result = repository.detectWound(
                    imageData = imageData,
                    width = image.width,
                    height = image.height
                )
                
                result?.let { measurement ->
                    updateMeasurement(measurement)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                imageProxy.close()
            }
        }
    }
    
    fun processYuvData(yData: ByteArray, uData: ByteArray, vData: ByteArray, width: Int, height: Int) {
        viewModelScope.launch {
            try {
                val ySize = yData.size
                val vSize = vData.size
                val imageData = ByteArray(ySize + vSize)
                
                System.arraycopy(yData, 0, imageData, 0, ySize)
                System.arraycopy(vData, 0, imageData, ySize, vSize)
                
                val result = repository.detectWound(
                    imageData = imageData,
                    width = width,
                    height = height
                )
                
                result?.let { measurement ->
                    updateMeasurement(measurement)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun updateMeasurement(measurement: MeasurementResult) {
        _measurementState.value = _measurementState.value.copy(
            areaMm2 = measurement.areaMm2,
            depthMm = measurement.depthMm,
            markerDetected = measurement.markerDetected,
            depthAvailable = measurement.depthAvailable,
            status = measurement.status
        )
        
        _statusMessage.value = when {
            measurement.status == MeasurementStatus.COMPLETED && measurement.markerDetected -> {
                "Wound: ${String.format("%.1f", measurement.areaMm2)}mm², Depth: ${String.format("%.1f", measurement.depthMm)}mm"
            }
            measurement.markerDetected -> "Marker Detected - Scanning for wound..."
            else -> "Point camera at ArUco marker"
        }
    }
    
    fun setMarkerDetected(detected: Boolean) {
        _measurementState.value = _measurementState.value.copy(
            markerDetected = detected
        )
    }
    
    fun setDepthAvailable(available: Boolean) {
        _measurementState.value = _measurementState.value.copy(
            depthAvailable = available
        )
    }
    
    override fun onCleared() {
        super.onCleared()
        repository.release()
    }
}

data class MeasurementUiState(
    val areaMm2: Float = 0f,
    val depthMm: Float = 0f,
    val markerDetected: Boolean = false,
    val depthAvailable: Boolean = false,
    val status: MeasurementStatus = MeasurementStatus.IDLE
)
