package com.applebyte.wounddetector.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.applebyte.wounddetector.R
import com.applebyte.wounddetector.databinding.ActivityMainBinding
import com.applebyte.wounddetector.ui.capture.CaptureFragment
import com.applebyte.wounddetector.ui.processing.ProcessingFragment
import com.applebyte.wounddetector.ui.results.ResultsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            navigateToCapture()
        } else {
            Toast.makeText(this, R.string.camera_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            showWelcomeFragment()
        }
    }

    private fun showWelcomeFragment() {
        replaceFragment(WelcomeFragment.newInstance())
    }

    fun navigateToCapture() {
        replaceFragment(CaptureFragment.newInstance())
    }

    fun navigateToProcessing(images: List<String>) {
        val fragment = ProcessingFragment.newInstance(images)
        replaceFragment(fragment)
    }

    fun navigateToResults(areaMm2: Double, depthMm: Double, imagePath: String) {
        val fragment = ResultsFragment.newInstance(areaMm2, depthMm, imagePath)
        replaceFragment(fragment)
    }

    fun navigateToResultsWithPoints(areaMm2: Double, depthMm: Double, imagePath: String, points3d: List<Triple<Float, Float, Float>>) {
        val fragment = ResultsFragment.newInstanceWithPoints(areaMm2, depthMm, imagePath, points3d)
        replaceFragment(fragment)
    }

    fun navigateToWelcome() {
        replaceFragment(WelcomeFragment.newInstance())
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    fun checkCameraPermission(onGranted: () -> Unit) {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                onGranted()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}
