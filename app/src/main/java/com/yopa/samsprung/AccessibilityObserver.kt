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
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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

        fun insertText(text: CharSequence, autoSend: Boolean = false): Boolean {
            val inst = getInstance()
            if (inst == null) {
                Log.w(TAG, "Cannot insert text: AccessibilityObserver instance is null")
                return false
            }
            copyToClipboard(inst, text)
            val root = inst.rootInActiveWindow
            if (root == null) {
                Log.w(TAG, "rootInActiveWindow is null")
                return false
            }
            var targetNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (targetNode == null) {
                targetNode = findEditableNode(root)
            }
            if (targetNode == null) {
                Log.w(TAG, "No editable node found to insert text")
                return false
            }
            targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            val setSuccess = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Log.d(TAG, "insertText ACTION_SET_TEXT result: $setSuccess")

            if (autoSend) {
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val freshRoot = inst.rootInActiveWindow ?: root
                        val sendBtn = findSendButton(freshRoot)
                        if (sendBtn != null) {
                            val clicked = sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            Log.d(TAG, "autoSend click on send button result: $clicked")
                        } else {
                            Log.w(TAG, "Send button not found for autoSend")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error clicking send button", e)
                    }
                }, 250)
            }
            return setSuccess
        }

        fun copyToClipboard(context: Context, text: CharSequence) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("TooUI Input", text)
                clipboard.setPrimaryClip(clip)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy to clipboard", e)
            }
        }

        private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.isEditable) {
                return node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val editable = findEditableNode(child)
                if (editable != null) return editable
            }
            return null
        }

        private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            if (viewId.endsWith(":id/send") ||
                viewId.endsWith(":id/button_send") ||
                desc == "send" || desc == "kirim" ||
                text == "send" || text == "kirim"
            ) {
                if (node.isClickable) return node
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val btn = findSendButton(child)
                if (btn != null) return btn
            }
            return null
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
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_CLICKED or
                    AccessibilityEvent.TYPE_VIEW_FOCUSED or
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 100
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
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
