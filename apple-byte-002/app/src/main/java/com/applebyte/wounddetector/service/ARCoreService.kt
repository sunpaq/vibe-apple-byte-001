package com.applebyte.wounddetector.service

import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException

class ARCoreService(private val context: Context) {

    private var session: Session? = null
    private var isDepthSupported = false

    fun initializeAR(): ARCoreResult {
        try {
            when (ArCoreApk.getInstance().requestInstall(context as android.app.Activity, true)) {
                ArCoreApk.InstallStatus.INSTALLED -> {}
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    return ARCoreResult.Error("ARCore installation required")
                }
            }

            session = Session(context)

            val config = Config(session)
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.focusMode = Config.FocusMode.AUTO

            isDepthSupported = session?.isDepthModeSupported(Config.DepthMode.AUTOMATIC) ?: false

            if (isDepthSupported) {
                config.depthMode = Config.DepthMode.AUTOMATIC
            }

            session?.configure(config)

            return ARCoreResult.Success(isDepthSupported)
        } catch (e: UnavailableArcoreNotInstalledException) {
            return ARCoreResult.Error("ARCore is not installed")
        } catch (e: UnavailableApkTooOldException) {
            return ARCoreResult.Error("Please update ARCore")
        } catch (e: UnavailableSdkTooOldException) {
            return ARCoreResult.Error("Please update this app")
        } catch (e: UnavailableDeviceNotCompatibleException) {
            return ARCoreResult.Error("This device does not support AR")
        } catch (e: Exception) {
            return ARCoreResult.Error("Failed to create AR session: ${e.message}")
        }
    }

    fun getSession(): Session? = session

    fun isDepthModeSupported(): Boolean = isDepthSupported

    fun pauseSession() {
        session?.pause()
    }

    fun resumeSession() {
        session?.resume()
    }

    fun closeSession() {
        session?.close()
        session = null
    }

    sealed class ARCoreResult {
        data class Success(val isDepthSupported: Boolean) : ARCoreResult()
        data class Error(val message: String) : ARCoreResult()
    }
}
