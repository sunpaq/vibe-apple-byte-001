package com.example.applewounddetector.ui.ar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.applewounddetector.R
import com.example.applewounddetector.databinding.FragmentArBinding
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ArFragment : Fragment() {
    
    private var _binding: FragmentArBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ArViewModel
    private var arSession: Session? = null
    private var isArMode: Boolean = false
    
    private lateinit var cameraExecutor: ExecutorService
    
    companion object {
        private const val TAG = "ArFragment"
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCameraWithFallback()
        } else {
            Toast.makeText(
                requireContext(),
                getString(R.string.permission_camera_required),
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[ArViewModel::class.java]
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        setupObservers()
        checkArCoreAndStartCamera()
    }
    
    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.statusMessage.collectLatest { message ->
                binding.tvStatus.text = message
            }
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.measurementState.collectLatest { state ->
                updateMeasurementDisplay(state)
            }
        }
    }
    
    private fun updateMeasurementDisplay(state: MeasurementUiState) {
        if (state.markerDetected && state.areaMm2 > 0) {
            binding.tvWoundArea.text = getString(R.string.result_wound_area, state.areaMm2)
            binding.tvWoundDepth.text = getString(R.string.result_wound_depth, state.depthMm)
            binding.tvWoundArea.visibility = View.VISIBLE
            binding.tvWoundDepth.visibility = View.VISIBLE
        } else {
            binding.tvWoundArea.visibility = View.INVISIBLE
            binding.tvWoundDepth.visibility = View.INVISIBLE
        }
        
        val markerColor = if (state.markerDetected) {
            ContextCompat.getColor(requireContext(), R.color.status_success)
        } else {
            ContextCompat.getColor(requireContext(), R.color.status_warning)
        }
        binding.viewMarkerIndicator.setBackgroundColor(markerColor)
        
        val depthColor = if (state.depthAvailable) {
            ContextCompat.getColor(requireContext(), R.color.status_success)
        } else {
            ContextCompat.getColor(requireContext(), R.color.status_warning)
        }
        binding.viewDepthIndicator.setBackgroundColor(depthColor)
    }
    
    private fun checkArCoreAndStartCamera() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(requireContext())
            
            when (availability) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    createArSession()
                }
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    tryInstallArCore()
                }
                ArCoreApk.Availability.UNKNOWN_CHECKING,
                ArCoreApk.Availability.UNKNOWN_ERROR,
                ArCoreApk.Availability.UNKNOWN_TIMED_OUT -> {
                    Log.w(TAG, "ARCore availability unknown, using fallback mode")
                    startCameraOnlyMode()
                }
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    Log.w(TAG, "ARCore not supported, using fallback mode")
                    startCameraOnlyMode()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking ARCore availability", e)
            startCameraOnlyMode()
        }
    }
    
    private fun tryInstallArCore() {
        try {
            when (ArCoreApk.getInstance().requestInstall(requireActivity(), true)) {
                ArCoreApk.InstallStatus.INSTALLED -> {
                    createArSession()
                }
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    Log.i(TAG, "ARCore installation requested")
                }
            }
        } catch (e: UnavailableException) {
            Log.w(TAG, "ARCore installation failed, using fallback mode", e)
            startCameraOnlyMode()
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting ARCore install", e)
            startCameraOnlyMode()
        }
    }
    
    private fun startCameraOnlyMode() {
        isArMode = false
        viewModel.setDepthAvailable(false)
        viewModel.setMarkerDetected(false)
        binding.tvStatus.text = "Camera Mode - Point at marker and wound"
        startCamera()
    }
    
    private fun createArSession() {
        try {
            arSession = Session(requireContext())
            
            val config = Config(arSession)
            
            val isDepthSupported = arSession?.isDepthModeSupported(Config.DepthMode.AUTOMATIC) == true
            if (isDepthSupported) {
                config.depthMode = Config.DepthMode.AUTOMATIC
            }
            
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            
            arSession?.configure(config)
            
            isArMode = true
            viewModel.setDepthAvailable(isDepthSupported)
            
            val statusMsg = if (isDepthSupported) {
                "AR Mode - Depth Enabled"
            } else {
                "AR Mode - Depth Disabled"
            }
            binding.tvStatus.text = statusMsg
            
            startCamera()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AR session, using fallback", e)
            startCameraOnlyMode()
        }
    }
    
    private fun startCameraWithFallback() {
        if (isArMode && arSession != null) {
            createArSession()
        } else {
            startCameraOnlyMode()
        }
    }
    
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            viewModel.processImage(imageProxy)
                        }
                    }
                
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                Toast.makeText(
                    requireContext(),
                    "Camera error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    override fun onResume() {
        super.onResume()
        arSession?.resume()
    }
    
    override fun onPause() {
        super.onPause()
        arSession?.pause()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        arSession?.close()
        _binding = null
    }
}
