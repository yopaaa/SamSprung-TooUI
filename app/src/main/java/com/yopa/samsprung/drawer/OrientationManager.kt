package com.yopa.samsprung.drawer

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import com.yopa.content.ScaledContext

object OrientationManager {

    private const val TAG = "OrientationManager"
    private var orientationView: View? = null
    private var windowManager: WindowManager? = null

    @Synchronized
    fun addOrientationLayout(context: Context, orientation: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
        try {
            val coverContext = ScaledContext(context.applicationContext ?: context).cover()
            val wm = coverContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            if (orientationView == null) {
                val view = LinearLayout(coverContext)
                val params = WindowManager.LayoutParams(
                    0,
                    0,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSPARENT
                ).apply {
                    screenOrientation = orientation
                }
                wm.addView(view, params)
                orientationView = view
                Log.d(TAG, "addOrientationLayout: added view with orientation $orientation")
            } else {
                val params = orientationView?.layoutParams as? WindowManager.LayoutParams
                if (params != null) {
                    params.screenOrientation = orientation
                    wm.updateViewLayout(orientationView, params)
                    Log.d(TAG, "addOrientationLayout: updated view with orientation $orientation")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "addOrientationLayout failed", e)
        }
    }

    @Synchronized
    fun removeOrientationLayout(context: Context? = null) {
        val view = orientationView ?: return
        val wm = windowManager ?: (context?.let {
            ScaledContext(it.applicationContext ?: it).cover().getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        })
        try {
            wm?.removeViewImmediate(view)
            Log.d(TAG, "removeOrientationLayout: removed view successfully")
        } catch (_: Exception) {
            try {
                wm?.removeView(view)
                Log.d(TAG, "removeOrientationLayout: removed view (non-immediate)")
            } catch (e: Exception) {
                Log.w(TAG, "removeOrientationLayout failed", e)
            }
        }
        orientationView = null
        windowManager = null
    }
}