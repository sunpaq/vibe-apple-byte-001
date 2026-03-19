package com.applebyte.wounddetector.ui.capture

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.applebyte.wounddetector.R
import com.applebyte.wounddetector.databinding.FragmentCaptureBinding
import com.applebyte.wounddetector.ui.main.MainActivity
import com.applebyte.wounddetector.util.ArUcoDetector
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CaptureFragment : Fragment() {

    private var _binding: FragmentCaptureBinding? = null
    private val binding get() = _binding!!

    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService

    private val capturedImages = mutableListOf<String>()
    private val maxImages = 10

    private var isProcessing = false
    private var lastMarkerDistance = 0.0

    private lateinit var arucoDetector: ArUcoDetector
    private var analysisJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var analysisEnabled = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCaptureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            System.loadLibrary("opencv_java4")
        } catch (e: UnsatisfiedLinkError) {
            Toast.makeText(requireContext(), "OpenCV library not found", Toast.LENGTH_SHORT).show()
        }

        arucoDetector = ArUcoDetector(50f)
        cameraExecutor = Executors.newSingleThreadExecutor()

        setupUI()
        startCamera()
    }

    private fun setupUI() {
        updateProgress()

        binding.captureButton.setOnClickListener {
            if (!isProcessing && capturedImages.size < maxImages) {
                capturePhoto()
            }
        }

        binding.doneButton.setOnClickListener {
            if (capturedImages.size >= 3) {
                processImages()
            } else {
                Toast.makeText(requireContext(), "Need at least 3 photos", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cancelButton.setOnClickListener {
            capturedImages.clear()
            updateProgress()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder()
                .setTargetResolution(Size(640, 480))
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(640, 480))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )

                setupImageAnalysis()

            } catch (e: Exception) {
                e.printStackTrace()
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun setupImageAnalysis() {
        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        var stableDetectionCount = 0
        var lastStableDistance = 0.0
        val stabilityThreshold = 3

        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            if (!analysisEnabled || isProcessing) {
                imageProxy.close()
                return@setAnalyzer
            }

            try {
                val mat = imageProxyToMat(imageProxy)
                if (!mat.empty()) {
                    val detections = arucoDetector.detectMarkers(mat)

                    mainHandler.post {
                        if (detections.isNotEmpty()) {
                            val bestDetection = detections.first()
                            lastMarkerDistance = bestDetection.distance
                            
                            val markerId = bestDetection.id
                            val confidence = bestDetection.confidence
                            val confidencePercent = (confidence * 100).toInt()
                            
                            binding.markerStatusText.text = "Marker ID: $markerId | ${confidencePercent}% confidence"
                            binding.markerStatusText.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.success)
                            )
                            
                            if (confidence > 0.6) {
                                stableDetectionCount++
                                if (stableDetectionCount >= stabilityThreshold) {
                                    binding.captureButton.isEnabled = true
                                    binding.captureHintText.text = "Marker $markerId stable! Tap to capture"
                                    binding.captureHintText.setTextColor(
                                        ContextCompat.getColor(requireContext(), R.color.success)
                                    )
                                }
                            } else {
                                stableDetectionCount = 0
                                binding.captureButton.isEnabled = capturedImages.isNotEmpty()
                                binding.captureHintText.text = getString(R.string.capture_hint_move)
                                binding.captureHintText.setTextColor(
                                    ContextCompat.getColor(requireContext(), android.R.color.white)
                                )
                            }
                        } else {
                            stableDetectionCount = 0
                            binding.markerStatusText.text = getString(R.string.capture_hint_marker)
                            binding.markerStatusText.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.error)
                            )
                            binding.captureButton.isEnabled = capturedImages.isNotEmpty()
                            binding.captureHintText.text = getString(R.string.capture_hint_marker)
                            binding.captureHintText.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.error)
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                imageProxy.close()
            }
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture,
                    imageAnalysis
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun imageProxyToMat(imageProxy: ImageProxy): Mat {
        val yBuffer = imageProxy.planes[0].buffer
        val uBuffer = imageProxy.planes[1].buffer
        val vBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
        val imageBytes = out.toByteArray()

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        // Rotate 90 degrees
        val matrix = Matrix()
        matrix.postRotate(90f)
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        val result = Mat()
        Utils.bitmapToMat(rotatedBitmap, result)
        
        bitmap.recycle()
        rotatedBitmap.recycle()

        return result
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return

        isProcessing = true
        binding.captureButton.isEnabled = false
        binding.progressIndicator.visibility = View.VISIBLE

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val mat = imageProxyToMat(image)
                    val filename = saveImage(mat)
                    image.close()

                    if (filename != null) {
                        capturedImages.add(filename)
                        mainHandler.post {
                            onCaptureSuccess()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    mainHandler.post {
                        onCaptureError(exception.message ?: "Capture failed")
                    }
                }
            }
        )
    }

    private fun saveImage(mat: Mat): String? {
        return try {
            val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, bitmap)

            val filename = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            val file = File(requireContext().cacheDir, filename)

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun onCaptureSuccess() {
        updateProgress()

        binding.captureSuccessText.visibility = View.VISIBLE
        binding.captureSuccessText.text = getString(R.string.capture_success)

        mainHandler.postDelayed({
            binding.captureSuccessText.visibility = View.GONE
        }, 1000)

        isProcessing = false

        if (capturedImages.size >= maxImages) {
            binding.captureButton.visibility = View.GONE
            binding.doneButton.visibility = View.VISIBLE
        } else if (capturedImages.size >= 3) {
            binding.doneButton.visibility = View.VISIBLE
        }

        binding.progressIndicator.visibility = View.GONE
        binding.captureButton.isEnabled = true
    }

    private fun onCaptureError(message: String) {
        isProcessing = false
        binding.progressIndicator.visibility = View.GONE
        binding.captureButton.isEnabled = true
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun updateProgress() {
        val current = capturedImages.size
        binding.progressText.text = getString(R.string.capture_progress, current, maxImages)
        binding.progressCircular.progress = (current * 100 / maxImages)

        if (current < 3) {
            binding.captureHintText.text = getString(R.string.capture_hint_move)
        } else if (current < maxImages) {
            binding.captureHintText.text = "Keep capturing or press Done"
        } else {
            binding.captureHintText.text = "All photos captured!"
        }
    }

    private fun processImages() {
        analysisEnabled = false
        (activity as? MainActivity)?.navigateToProcessing(capturedImages.toList())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        analysisJob?.cancel()
        _binding = null
    }

    companion object {
        fun newInstance() = CaptureFragment()
    }
}
