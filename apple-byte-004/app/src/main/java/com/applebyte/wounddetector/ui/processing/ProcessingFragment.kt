package com.applebyte.wounddetector.ui.processing

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.applebyte.wounddetector.databinding.FragmentProcessingBinding
import com.applebyte.wounddetector.ui.main.MainActivity
import com.applebyte.wounddetector.util.ArUcoDetector
import com.applebyte.wounddetector.util.SfmProcessor
import com.applebyte.wounddetector.util.WoundDetector
import kotlinx.coroutines.*
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point3
import java.io.File

class ProcessingFragment : Fragment() {

    private var _binding: FragmentProcessingBinding? = null
    private val binding get() = _binding!!

    private var imagePaths: List<String> = emptyList()

    private lateinit var sfmProcessor: SfmProcessor
    private lateinit var woundDetector: WoundDetector
    private lateinit var arucoDetector: ArUcoDetector

    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imagePaths = arguments?.getStringArrayList(ARG_IMAGES) ?: emptyList()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProcessingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        startProcessing()
    }

    private fun setupUI() {
        sfmProcessor = SfmProcessor(50f)
        woundDetector = WoundDetector()
        arucoDetector = ArUcoDetector(50f)
    }

    private fun startProcessing() {
        processingScope.launch {
            try {
                withContext(Dispatchers.Main) {
                    binding.processingStatusText.text = "Loading images..."
                }

                val images = loadImages()
                if (images.isEmpty()) {
                    showError("Failed to load images")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    binding.processingStatusText.text = "Detecting ArUco markers..."
                    binding.processingProgress.progress = 20
                }

                val markerCorners = detectMarkers(images)

                withContext(Dispatchers.Main) {
                    binding.processingStatusText.text = "Running SfM analysis..."
                    binding.processingProgress.progress = 40
                }

                val sfmResult = sfmProcessor.processImages(images, arucoDetector)

                withContext(Dispatchers.Main) {
                    binding.processingStatusText.text = "Detecting wound area..."
                    binding.processingProgress.progress = 70
                }

                val markerSizePx = markerCorners?.let { calculateMarkerSize(it) } ?: 100f
                val woundAnalysis = woundDetector.detectWound(images.first(), markerSizePx)

                withContext(Dispatchers.Main) {
                    binding.processingStatusText.text = "Calculating depth..."
                    binding.processingProgress.progress = 90
                }

                val depthMm = if (woundAnalysis.success && sfmResult.success) {
                    woundAnalysis.estimatedDepthMm * getDepthScaleFactor(sfmResult)
                } else {
                    woundAnalysis.estimatedDepthMm
                }

                val points3d = sfmResult.points3d.map { 
                    Triple(it.x.toFloat(), it.y.toFloat(), it.z.toFloat()) 
                }

                withContext(Dispatchers.Main) {
                    binding.processingProgress.progress = 100

                    val resultImagePath = saveResultImage(images.first(), woundAnalysis)

                    (activity as? MainActivity)?.navigateToResultsWithPoints(
                        woundAnalysis.woundAreaMm2,
                        depthMm,
                        resultImagePath ?: imagePaths.firstOrNull() ?: "",
                        points3d
                    )
                }

            } catch (e: Exception) {
                e.printStackTrace()
                showError(e.message ?: "Processing failed")
            }
        }
    }

    private fun loadImages(): List<Mat> {
        return imagePaths.mapNotNull { path ->
            try {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    val mat = Mat()
                    Utils.bitmapToMat(bitmap, mat)
                    bitmap.recycle()
                    mat
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun detectMarkers(images: List<Mat>): MatOfPoint2f? {
        for (image in images) {
            val gray = Mat()
            org.opencv.imgproc.Imgproc.cvtColor(image, gray, org.opencv.imgproc.Imgproc.COLOR_RGBA2GRAY)

            val detections = arucoDetector.detectMarkers(gray)
            if (detections.isNotEmpty()) {
                return detections.first().corners
            }
        }
        return null
    }

    private fun calculateMarkerSize(corners: MatOfPoint2f): Float {
        val points = corners.toArray()
        val width = Math.sqrt(
            Math.pow(points[1].x - points[0].x, 2.0) +
            Math.pow(points[1].y - points[0].y, 2.0)
        )
        return width.toFloat()
    }

    private fun getDepthScaleFactor(sfmResult: com.applebyte.wounddetector.util.SfMResult): Double {
        if (sfmResult.points3d.isEmpty()) return 1.0

        val avgDepth = sfmResult.points3d.map { it.z }.average()
        val markerSizeMm = 50.0
        val estimatedMarkerSizePx = 100.0
        val pixelToMm = markerSizeMm / estimatedMarkerSizePx

        return avgDepth * pixelToMm
    }

    private fun saveResultImage(image: Mat, woundAnalysis: com.applebyte.wounddetector.util.WoundDetector.WoundAnalysis): String? {
        return try {
            val outputImage = if (woundAnalysis.woundContour != null) {
                woundDetector.drawWoundContour(image, woundAnalysis.woundContour)
            } else {
                image
            }

            val bitmap = android.graphics.Bitmap.createBitmap(
                outputImage.cols(),
                outputImage.rows(),
                android.graphics.Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(outputImage, bitmap)

            val filename = "RESULT_${System.currentTimeMillis()}.jpg"
            val file = File(requireContext().cacheDir, filename)

            java.io.FileOutputStream(file).use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }

            bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun showError(message: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            (activity as? MainActivity)?.navigateToWelcome()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        processingScope.cancel()
        _binding = null
    }

    companion object {
        private const val ARG_IMAGES = "images"

        fun newInstance(images: List<String>): ProcessingFragment {
            return ProcessingFragment().apply {
                arguments = Bundle().apply {
                    putStringArrayList(ARG_IMAGES, ArrayList(images))
                }
            }
        }
    }
}
