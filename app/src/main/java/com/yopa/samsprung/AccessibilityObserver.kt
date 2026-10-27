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

package com.yopa.samsprung

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.io.File

class AccessibilityObserver : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityObserver"
        private var observerInstance: AccessibilityObserver? = null
        private var isConnected: Boolean = false

        fun getInstance(): AccessibilityObserver? {
            return observerInstance
        }

        fun hasEnabledService(context: Context): Boolean {
            if (BuildConfig.GOOGLE_PLAY) return false
            val serviceString = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expected = "${context.packageName}${File.separator}${AccessibilityObserver::class.java.name}"
            val expectedShort = "${context.packageName}/.AccessibilityObserver"
            return serviceString.contains(expected) || serviceString.contains(expectedShort)
        }

        fun performBackAction(): Boolean {
            val inst = getInstance()
            Log.d(TAG, "performBackAction() inst=$inst")
            return inst?.performGlobalAction(GLOBAL_ACTION_BACK) == true
        }

        fun performLockAction(): Boolean {
            val inst = getInstance()
            Log.d(TAG, "performLockAction() inst=$inst, isConnected=$isConnected")
            if (inst == null) {
                Log.w(TAG, "Cannot lock: AccessibilityObserver instance is null")
                return false
            }
            val result = inst.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            Log.d(TAG, "performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) returned $result")
            return result
        }

        fun getWindowManager(): WindowManager? {
            return getInstance()?.getSystemService(WINDOW_SERVICE) as? WindowManager
        }
    }

    init {
        observerInstance = this
        Log.d(TAG, "AccessibilityObserver initialized: $this")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        observerInstance = this
        isConnected = true
        Log.d(TAG, "AccessibilityObserver onServiceConnected")
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 100
            info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring serviceInfo", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) { }

    override fun onInterrupt() {
        if (observerInstance == null) observerInstance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "AccessibilityObserver onUnbind")
        isConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.d(TAG, "AccessibilityObserver onDestroy")
        isConnected = false
        if (observerInstance == this) {
            observerInstance = null
        }
        super.onDestroy()
    }
}
