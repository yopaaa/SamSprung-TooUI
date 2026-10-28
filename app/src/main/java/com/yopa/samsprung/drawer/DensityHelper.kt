/*
 * ====================================================================
 * Copyright (c) 2012-2023 AbandonedCart.  All rights reserved.
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

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

object DensityHelper {

    fun hasPermission(context: Context): Boolean {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    }

    fun getCurrentDensity(context: Context, displayId: Int = 1): Int {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = dm.getDisplay(displayId) ?: dm.getDisplay(0)
            val metrics = DisplayMetrics()
            display.getRealMetrics(metrics)
            metrics.densityDpi
        } catch (e: Exception) {
            160
        }
    }

    fun setDensity(density: Int, displayId: Int = 1): Boolean {
        return try {
            val wmGlobalClass = Class.forName("android.view.WindowManagerGlobal")
            val wmService = HiddenApiBypass.invoke(wmGlobalClass, null, "getWindowManagerService")
            val userId = 0
            if (density <= 0 || density == 160) {
                HiddenApiBypass.invoke(wmService.javaClass, wmService, "clearForcedDisplayDensityForUser", displayId, userId)
            } else {
                HiddenApiBypass.invoke(wmService.javaClass, wmService, "setForcedDisplayDensityForUser", displayId, density, userId)
            }
            true
        } catch (e: Exception) {
            Log.e("DensityHelper", "Failed to set density: ${e.message}", e)
            false
        }
    }

    fun setDisplayImePolicy(policy: Int = 0, displayId: Int = 1): Boolean {
        return try {
            val wmGlobalClass = Class.forName("android.view.WindowManagerGlobal")
            val wmService = HiddenApiBypass.invoke(wmGlobalClass, null, "getWindowManagerService")
            HiddenApiBypass.invoke(wmService.javaClass, wmService, "setDisplayImePolicy", displayId, policy)
            Log.d("DensityHelper", "setDisplayImePolicy($displayId, $policy) successful")
            true
        } catch (e: Throwable) {
            Log.w("DensityHelper", "setDisplayImePolicy failed: ${e.message}")
            false
        }
    }
}
