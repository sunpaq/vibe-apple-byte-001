package com.applebyte.wounddetector.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.applebyte.wounddetector.R
import com.applebyte.wounddetector.databinding.ActivityMainBinding
import com.applebyte.wounddetector.util.*
import com.applebyte.wounddetector.viewmodel.CaptureState
import com.applebyte.wounddetector.viewmodel.ProcessingState
import com.applebyte.wounddetector.viewmodel.WoundDetectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: WoundDetectionViewModel by viewModels()
    
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var photoAdapter: PhotoThumbnailAdapter
    
    private val arUcoDetector = ArUcoDetector()
    private val sfmProcessor = SfMProcessor()
    private val woundDetector = WoundDetector()
    
    private var lastArUcoResult: ArUcoResult? = null
    private var currentPixelsPerMm = 10f
    private var isAnalyzingFrame = false
    private var hasMarkerDetectedOnce = false
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setupUI()
        observeViewModel()
        checkCameraPermission()
    }
    
    private fun setupUI() {
        photoAdapter = PhotoThumbnailAdapter()
        binding.capturedPhotosStrip.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = photoAdapter
        }
        
        binding.captureButton.setOnClickListener {
            capturePhoto()
        }
        
        binding.btnProcess.setOnClickListener {
            processImages()
        }
        
        binding.btnReset.setOnClickListener {
            viewModel.reset()
            lastArUcoResult = null
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.captureState.collect { state ->
                updateCaptureUI(state)
            }
        }
        
        lifecycleScope.launch {
            viewModel.markerDetected.collect { detected ->
                updateMarkerStatus(detected)
            }
        }

        lifecycleScope.launch {
            viewModel.capturedPhotos.collect { photos ->
                photoAdapter.submitList(photos)
                binding.capturedPhotosStrip.visibility = if (photos.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }
        
        lifecycleScope.launch {
            viewModel.photoCount.collect { count ->
                binding.tvCaptureProgress.visibility = if (count > 0) View.VISIBLE else View.GONE
                binding.tvCaptureProgress.text = getString(R.string.capture_progress, count, WoundDetectionViewModel.MAX_PHOTOS)
            }
        }
        
        lifecycleScope.launch {
            viewModel.instructionText.collect { text ->
                binding.tvInstruction.text = text
            }
        }
        
        lifecycleScope.launch {
            viewModel.processingState.collect { state ->
                updateProcessingUI(state)
            }
        }
        
        lifecycleScope.launch {
            viewModel.detectionResult.collect { result ->
                if (result != null) {
                    showResults(result)
                }
            }
        }
    }

    private fun updateMarkerStatus(detected: Boolean) {
        if (detected) {
            binding.captureButton.isEnabled = true
            binding.captureButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
        }
    }
    
    private fun updateCaptureUI(state: CaptureState) {
        when (state) {
            is CaptureState.Ready -> {
                binding.captureButton.isEnabled = true
                binding.captureButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
                binding.btnProcess.visibility = View.GONE
                binding.btnReset.visibility = View.GONE
                binding.resultsCard.visibility = View.GONE
            }
            is CaptureState.Capturing, is CaptureState.MorePhotos -> {
                binding.captureButton.isEnabled = true
                binding.captureButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
                binding.btnProcess.visibility = View.GONE
                binding.btnReset.visibility = View.VISIBLE
            }
            is CaptureState.ReadyToProcess -> {
                binding.captureButton.isEnabled = false
                binding.captureButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.background_dark)
                binding.btnProcess.visibility = View.VISIBLE
                binding.btnProcess.text = getString(R.string.processing)
                binding.btnProcess.isEnabled = true
                binding.btnReset.visibility = View.VISIBLE
            }
        }
    }
    
    private fun updateProcessingUI(state: ProcessingState) {
        when (state) {
            is ProcessingState.Idle -> {
                hideProcessingProgress()
            }
            is ProcessingState.Processing -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.captureButton.isEnabled = false
                binding.btnProcess.isEnabled = false
            }
            is ProcessingState.Progress -> {
                binding.progressBar.visibility = View.VISIBLE
            }
            is ProcessingState.Complete -> {
                hideProcessingProgress()
            }
            is ProcessingState.Error -> {
                hideProcessingProgress()
                Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showResults(result: WoundDetectionResult) {
        binding.resultsCard.visibility = View.VISIBLE
        binding.tvWoundArea.text = String.format("%.1f mm²", result.woundAreaMm2)
        binding.tvWoundDepth.text = String.format("%.1f mm", result.woundDepthMm)
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }
            
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            
            // Temporarily disabled for debugging
            // val imageAnalysis = ImageAnalysis.Builder()
            //     .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            //     .build()
            //     .also { analysis ->
            //         analysis.setAnalyzer(cameraExecutor) { imageProxy ->
            //             processFrame(imageProxy)
            //         }
            //     }
            
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }
    
    private fun processFrame(imageProxy: ImageProxy) {
        if (isAnalyzingFrame || hasMarkerDetectedOnce) {
            imageProxy.close()
            return
        }
        
        isAnalyzingFrame = true
        
        val bitmap = imageProxyToBitmap(imageProxy)
        imageProxy.close()
        
        if (bitmap != null) {
            cameraExecutor.execute {
                try {
                    val result = arUcoDetector.detectMarkers(bitmap)
                    mainHandler.post {
                        if (result != null) {
                            hasMarkerDetectedOnce = true
                            lastArUcoResult = result
                            currentPixelsPerMm = result.pixelsPerMm
                            viewModel.setMarkerDetected(true)
                        }
                        isAnalyzingFrame = false
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        isAnalyzingFrame = false
                    }
                }
            }
        } else {
            isAnalyzingFrame = false
        }
    }
    
    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return
        
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    
                    if (bitmap != null) {
                        if (lastArUcoResult != null) {
                            viewModel.addPhoto(bitmap)
                        } else {
                            lifecycleScope.launch(Dispatchers.Default) {
                                val result = arUcoDetector.detectMarkers(bitmap)
                                withContext(Dispatchers.Main) {
                                    if (result != null) {
                                        lastArUcoResult = result
                                        currentPixelsPerMm = result.pixelsPerMm
                                        viewModel.setMarkerDetected(true)
                                        viewModel.addPhoto(bitmap)
                                    } else {
                                        Toast.makeText(this@MainActivity, R.string.aruco_not_found, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    }
                }
                
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(this@MainActivity, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        
        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
        
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    private fun processImages() {
        val photos = viewModel.capturedPhotos.value
        
        if (photos.size < WoundDetectionViewModel.MIN_PHOTOS_REQUIRED) {
            Toast.makeText(this, R.string.insufficient_photos, Toast.LENGTH_SHORT).show()
            return
        }
        
        viewModel.setProcessing(true)
        showProcessingProgress(0, 100, "Loading images...")
        
        lifecycleScope.launch {
            try {
                // Step 1: Detect features in all images
                showProcessingProgress(10, 100, "Detecting features in ${photos.size} images...")
                
                val depthResult = withContext(Dispatchers.Default) {
                    sfmProcessor.processImages(photos, currentPixelsPerMm)
                }
                
                if (depthResult == null) {
                    viewModel.setError(getString(R.string.sfm_failed))
                    hideProcessingProgress()
                    return@launch
                }
                
                showProcessingProgress(50, 100, "Estimating camera poses...")
                
                showProcessingProgress(70, 100, "Generating depth map...")
                
                val referencePhoto = photos[photos.size / 2]
                
                showProcessingProgress(85, 100, "Detecting wound area...")
                
                val woundResult = withContext(Dispatchers.Default) {
                    woundDetector.detectWound(referencePhoto, depthResult.depthMap, currentPixelsPerMm)
                }
                
                showProcessingProgress(100, 100, "Complete!")
                
                if (woundResult == null) {
                    viewModel.setError(getString(R.string.no_wound_detected))
                    hideProcessingProgress()
                    return@launch
                }
                
                viewModel.setResult(woundResult)
                hideProcessingProgress()
                
            } catch (e: Exception) {
                viewModel.setError("Error: ${e.message}")
                hideProcessingProgress()
            }
        }
    }
    
    private fun showProcessingProgress(progress: Int, max: Int, status: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.max = max
        binding.progressBar.progress = progress
        binding.tvProcessingStatus.visibility = View.VISIBLE
        binding.tvProcessingStatus.text = status
        binding.captureButton.isEnabled = false
        binding.btnProcess.isEnabled = false
    }
    
    private fun hideProcessingProgress() {
        binding.progressBar.visibility = View.GONE
        binding.tvProcessingStatus.visibility = View.GONE
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
