package com.applebyte.wounddetector.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.applebyte.wounddetector.R
import com.applebyte.wounddetector.databinding.ActivityMainBinding
import com.applebyte.wounddetector.viewmodel.WoundDetectionViewModel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: WoundDetectionViewModel
    private lateinit var cameraExecutor: ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[WoundDetectionViewModel::class.java]
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUI()
        observeViewModel()
        checkCameraPermission()
    }

    private fun setupUI() {
        binding.captureButton.setOnClickListener {
            viewModel.capture()
        }

        binding.resetButton.setOnClickListener {
            viewModel.reset()
        }
    }

    private fun observeViewModel() {
        viewModel.woundArea.observe(this) { area ->
            binding.woundAreaText.text = if (area > 0) {
                String.format("%.1f %s", area, getString(R.string.mm2))
            } else {
                "--"
            }
        }

        viewModel.woundDepth.observe(this) { depth ->
            binding.woundDepthText.text = if (depth > 0) {
                String.format("%.2f %s", depth, getString(R.string.mm))
            } else {
                "--"
            }
        }

        viewModel.status.observe(this) { status ->
            binding.statusText.text = status
        }

        viewModel.overlayBitmap.observe(this) { bitmap ->
            bitmap?.let {
                binding.overlayView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                binding.overlayView.background = null
                drawOverlay(it)
            }
        }
    }

    private fun drawOverlay(bitmap: Bitmap) {
        val viewWidth = binding.overlayView.width
        val viewHeight = binding.overlayView.height

        if (viewWidth == 0 || viewHeight == 0) return

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, viewWidth, viewHeight, true)
        binding.overlayView.background = android.graphics.drawable.BitmapDrawable(resources, scaledBitmap)
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
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
            cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImage(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                viewModel.onCameraReady()
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to start camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImage(imageProxy: ImageProxy) {
        viewModel.processFrame(imageProxy)
        imageProxy.close()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
