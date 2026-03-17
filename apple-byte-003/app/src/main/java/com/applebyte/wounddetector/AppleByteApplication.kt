package com.applebyte.wounddetector

import android.app.Application

class AppleByteApplication : Application() {
    companion object {
        init {
            try {
                System.loadLibrary("opencv_java4")
            } catch (e: UnsatisfiedLinkError) {
                System.loadLibrary("opencv_java3")
            }
        }
    }
}
