package com.applebyte.wounddetector.ui.results

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.applebyte.wounddetector.databinding.Page3dViewBinding

class PointCloudFragment : Fragment() {

    private var _binding: Page3dViewBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Page3dViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get points from parent fragment
        val parentFragment = parentFragment as? ResultsFragment
        val points3d = parentFragment?.getPoints3D() ?: emptyList()
        
        if (points3d.isNotEmpty()) {
            binding.pointCloudView.setPoints3D(points3d)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
