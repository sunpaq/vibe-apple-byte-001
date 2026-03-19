package com.applebyte.wounddetector.ui.results

import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.applebyte.wounddetector.databinding.FragmentResultsBinding
import com.applebyte.wounddetector.ui.main.MainActivity
import com.google.android.material.tabs.TabLayoutMediator
import java.io.File
import java.io.Serializable
import java.text.DecimalFormat

data class PointData(val x: Float, val y: Float, val z: Float) : Serializable

class ResultsFragment : Fragment() {

    private var _binding: FragmentResultsBinding? = null
    private val binding get() = _binding!!

    private var woundAreaMm2: Double = 0.0
    private var woundDepthMm: Double = 0.0
    private var imagePath: String = ""
    
    private var points3d: List<Triple<Float, Float, Float>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            woundAreaMm2 = it.getDouble(ARG_AREA)
            woundDepthMm = it.getDouble(ARG_DEPTH)
            imagePath = it.getString(ARG_IMAGE, "")
            
            val pointDataList = it.getSerializable(ARG_POINTS) as? ArrayList<PointData>
            points3d = pointDataList?.map { pd -> Triple(pd.x, pd.y, pd.z) } ?: emptyList()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViewPager()
    }

    private fun setupViewPager() {
        val adapter = ResultsPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "3D View"
                1 -> "Measurements"
                else -> ""
            }
        }.attach()
    }

    fun getMeasurements(): Triple<Double, Double, String> {
        return Triple(woundAreaMm2, woundDepthMm, imagePath)
    }
    
    fun setPoints3D(points: List<Triple<Float, Float, Float>>) {
        points3d = points
    }
    
    fun getPoints3D(): List<Triple<Float, Float, Float>> = points3d

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_AREA = "area"
        private const val ARG_DEPTH = "depth"
        private const val ARG_IMAGE = "image"
        private const val ARG_POINTS = "points"

        fun newInstance(areaMm2: Double, depthMm: Double, imagePath: String): ResultsFragment {
            return ResultsFragment().apply {
                arguments = Bundle().apply {
                    putDouble(ARG_AREA, areaMm2)
                    putDouble(ARG_DEPTH, depthMm)
                    putString(ARG_IMAGE, imagePath)
                }
            }
        }

        fun newInstanceWithPoints(areaMm2: Double, depthMm: Double, imagePath: String, points3d: List<Triple<Float, Float, Float>>): ResultsFragment {
            return ResultsFragment().apply {
                arguments = Bundle().apply {
                    putDouble(ARG_AREA, areaMm2)
                    putDouble(ARG_DEPTH, depthMm)
                    putString(ARG_IMAGE, imagePath)
                    putSerializable(ARG_POINTS, ArrayList(points3d.map { PointData(it.first, it.second, it.third) }))
                }
            }
        }
    }
}

class ResultsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PointCloudFragment()
            1 -> MeasurementsFragment()
            else -> MeasurementsFragment()
        }
    }
}
