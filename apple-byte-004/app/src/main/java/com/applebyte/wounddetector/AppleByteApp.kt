package com.applebyte.wounddetector

import android.app.Application
import org.opencv.android.BaseLoaderCallback
import org.opencv.android.LoaderCallbackInterface
import org.opencv.android.OpenCVLoader

class AppleByteApp : Application() {

    private val loaderCallback = object : BaseLoaderCallback(this) {
        override fun onManagerConnected(status: Int) {
            when (status) {
                LoaderCallbackInterface.SUCCESS -> {
                    // OpenCV loaded successfully
                }
                else -> {
                    super.onManagerConnected(status)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        initOpenCV()
    }

    private fun initOpenCV(): Boolean {
        return if (OpenCVLoader.initDebug()) {
            loaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS)
            true
        } else {
            false
        }
    }
}
