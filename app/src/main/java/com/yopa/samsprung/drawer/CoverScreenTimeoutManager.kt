/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * See https://github.com/SamSprung/.github/blob/main/LICENSE#L5
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.yopa.samsprung.drawer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import com.yopa.samsprung.AccessibilityObserver
import com.yopa.samsprung.settings.Preferences
import java.util.Collections

object CoverScreenTimeoutManager {
    private const val TAG = "CoverTimeoutManager"
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var isInitialized = false
    private var lastActivityTime = 0L

    private val unavailableCameras = Collections.synchronizedSet(mutableSetOf<String>())
    private val torchCameras = Collections.synchronizedSet(mutableSetOf<String>())
    private var cameraManager: CameraManager? = null
    private var audioManager: AudioManager? = null
    private var currentForegroundPackage: String? = null

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            unavailableCameras.remove(cameraId)
            Log.d(TAG, "Camera $cameraId available. In-use cameras: $unavailableCameras")
        }

        override fun onCameraUnavailable(cameraId: String) {
            unavailableCameras.add(cameraId)
            Log.d(TAG, "Camera $cameraId in use. In-use cameras: $unavailableCameras")
            userActivity("camera_busy")
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (enabled) {
                torchCameras.add(cameraId)
            } else {
                torchCameras.remove(cameraId)
            }
        }
    }

    private val timeoutRunnable = Runnable {
        onTimeoutExpired()
    }

    private var displayListener: DisplayManager.DisplayListener? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "ACTION_SCREEN_OFF received: cancelling timeout")
                    cancel()
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    if (isCoverScreenActive()) {
                        Log.d(TAG, "${intent.action} with cover screen active: resetting timeout")
                        resetTimer()
                    }
                }
            }
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
        isInitialized = true

        val ctx = appContext ?: return

        // 1. Register display listener to detect fold/unfold and cover screen state
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        if (dm != null) {
            displayListener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) {}
                override fun onDisplayRemoved(displayId: Int) {}
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == 0) {
                        val mainDisplay = try { dm.getDisplay(0) } catch (_: Exception) { null }
                        if (mainDisplay != null && mainDisplay.state == Display.STATE_ON) {
                            Log.d(TAG, "Main display turned ON (unfolded). Cancelling cover timeout.")
                            cancel()
                            return
                        }
                    }
                    if (displayId == 1) {
                        val coverDisplay = try { dm.getDisplay(1) } catch (_: Exception) { null }
                        if (coverDisplay != null && coverDisplay.state == Display.STATE_ON) {
                            if (isCoverScreenActive()) {
                                Log.d(TAG, "Cover display turned ON. Starting timeout countdown.")
                                resetTimer()
                            }
                        } else {
                            Log.d(TAG, "Cover display turned OFF. Cancelling timeout.")
                            cancel()
                        }
                    }
                }
            }
            dm.registerDisplayListener(displayListener, handler)
        }

        // 2. Register broadcast receiver for screen on/off
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        @Suppress("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(screenReceiver, filter)
        }

        // 3. Register camera availability callback and audio manager for Smart Keep-Alive
        audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        cameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        try {
            cameraManager?.registerAvailabilityCallback(cameraCallback, handler)
            cameraManager?.registerTorchCallback(torchCallback, handler)
            Log.d(TAG, "Registered camera availability & torch callbacks")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register camera callbacks", e)
        }

        if (isCoverScreenActive()) {
            resetTimer()
        }
    }

    fun isCoverScreenActive(): Boolean {
        val ctx = appContext ?: return false
        val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
        val mainDisplay = try { dm.getDisplay(0) } catch (_: Exception) { null }
        val coverDisplay = try { dm.getDisplay(1) } catch (_: Exception) { null }

        // If main display is ON, the phone is unfolded/opened: cover timeout should NOT run
        if (mainDisplay != null && mainDisplay.state == Display.STATE_ON) {
            return false
        }
        // Cover display must be present and currently ON
        return coverDisplay != null && coverDisplay.state == Display.STATE_ON
    }

    fun setCurrentForegroundPackage(packageName: String?) {
        currentForegroundPackage = packageName
        Log.d(TAG, "setCurrentForegroundPackage: $packageName")
        if (packageName != null) {
            userActivity("app_switch")
        }
    }

    fun isCameraActive(): Boolean {
        // 1. Any camera sensor is active/unavailable (excluding pure torch)
        val hasActiveSensor = unavailableCameras.any { !torchCameras.contains(it) }
        if (hasActiveSensor) return true

        // 2. The active app on cover display is a camera application
        val pkg = currentForegroundPackage
        if (pkg != null && (pkg.contains("camera", ignoreCase = true) || pkg == "com.sec.android.app.camera")) {
            return true
        }

        return false
    }

    fun isMediaActive(): Boolean {
        val isAudioPlaying = try {
            audioManager?.isMusicActive == true
        } catch (_: Exception) {
            false
        }
        if (!isAudioPlaying) return false

        // If audio is playing in background while user is just on the cover launcher,
        // let the screen timeout normally to avoid draining battery.
        val pkg = currentForegroundPackage ?: return false
        val isLauncher = pkg == appContext?.packageName || pkg.contains("launcher")
        if (isLauncher) return false

        // Audio is actively playing and a foreground app is open on the cover display
        return true
    }

    fun userActivity(source: String = "touch") {
        val now = SystemClock.uptimeMillis()
        if (now - lastActivityTime < 600) {
            return
        }
        lastActivityTime = now

        if (!isCoverScreenActive()) {
            cancel()
            return
        }
        Log.d(TAG, "userActivity ($source): Resetting cover timeout")
        resetTimer()
    }

    fun resetTimer() {
        val ctx = appContext ?: return
        handler.removeCallbacks(timeoutRunnable)

        val timeoutSeconds = Preferences.getCoverTimeoutSeconds(ctx)
        if (timeoutSeconds <= 0) return

        Log.d(TAG, "resetTimer: scheduling lock in $timeoutSeconds seconds")
        handler.postDelayed(timeoutRunnable, timeoutSeconds * 1000L)
    }

    fun cancel() {
        handler.removeCallbacks(timeoutRunnable)
    }

    private fun onTimeoutExpired() {
        if (!isCoverScreenActive()) {
            Log.d(TAG, "onTimeoutExpired: Cover screen is not active, skipping lock.")
            return
        }
        val ctx = appContext ?: return

        // Smart Keep-Alive: Keep screen on while camera is active or media is playing
        if (isCameraActive()) {
            Log.d(TAG, "onTimeoutExpired: Camera is actively in use. Extending cover screen awake time.")
            resetTimer()
            return
        }

        if (isMediaActive()) {
            Log.d(TAG, "onTimeoutExpired: Media/Audio is actively playing. Extending cover screen awake time.")
            resetTimer()
            return
        }

        val timeoutSeconds = Preferences.getCoverTimeoutSeconds(ctx)
        Log.d(TAG, "onTimeoutExpired: Cover screen inactivity timeout ($timeoutSeconds s) reached. Locking device.")
        val success = AccessibilityObserver.performLockAction()
        Log.d(TAG, "performLockAction result: $success")
    }
}
