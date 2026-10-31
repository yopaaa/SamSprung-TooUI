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
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.File

class AccessibilityObserver : AccessibilityService() {

    interface InputFocusListener {
        fun onInputFocusChanged(isFocused: Boolean)
    }

    companion object {
        private const val TAG = "AccessibilityObserver"
        private var observerInstance: AccessibilityObserver? = null
        private var isConnected: Boolean = false
        private var inputFocusListener: InputFocusListener? = null
        private var cachedInputNode: AccessibilityNodeInfo? = null

        fun setInputFocusListener(listener: InputFocusListener?) {
            inputFocusListener = listener
        }

        fun getInstance(): AccessibilityObserver? {
            return observerInstance
        }

        fun ensureServiceEnabled(context: Context) {
            try {
                if (!hasEnabledService(context)) {
                    val component = "${context.packageName}/${AccessibilityObserver::class.java.name}"
                    val current = Settings.Secure.getString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                    ) ?: ""
                    val newServices = if (current.isEmpty()) component else "$current:$component"
                    Settings.Secure.putString(
                        context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        newServices
                    )
                    Settings.Secure.putInt(
                        context.contentResolver,
                        Settings.Secure.ACCESSIBILITY_ENABLED,
                        1
                    )
                    Log.d(TAG, "Enabled AccessibilityObserver via secure settings")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not enable accessibility service automatically", e)
            }
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

        private var isExecutingRecentsAction = false
        private var isCloseAllRequested = false
        private var targetLabelToClose: String? = null

        fun closeAllRecentTasks(): Boolean {
            val inst = getInstance() ?: return false
            Log.d(TAG, "closeAllRecentTasks() requested")
            isCloseAllRequested = true
            targetLabelToClose = null
            isExecutingRecentsAction = true

            inst.performGlobalAction(GLOBAL_ACTION_RECENTS)

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({ inst.checkAndExecuteRecentsAction() }, 150)
            handler.postDelayed({ inst.checkAndExecuteRecentsAction() }, 300)
            handler.postDelayed({ inst.checkAndExecuteRecentsAction() }, 500)

            handler.postDelayed({
                if (isExecutingRecentsAction) {
                    Log.w(TAG, "closeAllRecentTasks timed out, dismissing recents")
                    isExecutingRecentsAction = false
                    isCloseAllRequested = false
                    inst.performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }, 1200)

            return true
        }

        fun closeSingleRecentTask(label: String): Boolean {
            val inst = getInstance() ?: return false
            Log.d(TAG, "closeSingleRecentTask() requested for: $label")
            isCloseAllRequested = false
            targetLabelToClose = label
            isExecutingRecentsAction = true

            inst.performGlobalAction(GLOBAL_ACTION_RECENTS)

            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({ inst.checkAndExecuteRecentsAction() }, 150)
            handler.postDelayed({ inst.checkAndExecuteRecentsAction() }, 300)
            handler.postDelayed({ inst.checkAndExecuteRecentsAction() }, 500)

            handler.postDelayed({
                if (isExecutingRecentsAction) {
                    Log.w(TAG, "closeSingleRecentTask timed out for $label, dismissing recents")
                    isExecutingRecentsAction = false
                    targetLabelToClose = null
                    inst.performGlobalAction(GLOBAL_ACTION_BACK)
                }
            }, 1200)

            return true
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

        fun getActiveInputNode(): AccessibilityNodeInfo? {
            val inst = getInstance() ?: return null

            // 1. Try cached node if still valid and editable
            cachedInputNode?.let { cached ->
                try {
                    if (cached.refresh() && cached.isEditable) {
                        return cached
                    }
                } catch (_: Exception) {}
                cachedInputNode = null
            }

            // 2. Multi-display search (API 30+) - Prioritize Display 1 (Cover screen)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val allDisplays = inst.windowsOnAllDisplays
                    val display1Windows = allDisplays.get(1)
                    if (display1Windows != null) {
                        for (win in display1Windows) {
                            win.root?.let { root ->
                                val node = findInputInRoot(root)
                                if (node != null) {
                                    cachedInputNode = node
                                    return node
                                }
                            }
                        }
                    }
                    for (i in 0 until allDisplays.size()) {
                        val dId = allDisplays.keyAt(i)
                        if (dId == 1) continue
                        val winList = allDisplays.valueAt(i) ?: continue
                        for (win in winList) {
                            win.root?.let { root ->
                                val node = findInputInRoot(root)
                                if (node != null) {
                                    cachedInputNode = node
                                    return node
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking windowsOnAllDisplays", e)
                }
            }

            // 3. Check rootInActiveWindow
            try {
                inst.rootInActiveWindow?.let { root ->
                    val node = findInputInRoot(root)
                    if (node != null) {
                        cachedInputNode = node
                        return node
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking rootInActiveWindow", e)
            }

            // 4. Fallback: Check inst.windows
            try {
                for (win in inst.windows) {
                    win.root?.let { root ->
                        val node = findInputInRoot(root)
                        if (node != null) {
                            cachedInputNode = node
                            return node
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking inst.windows", e)
            }

            return null
        }

        private fun findInputInRoot(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            // 1. WhatsApp entry view ID
            try {
                val byId = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
                if (!byId.isNullOrEmpty() && byId[0].isEditable) {
                    return byId[0]
                }
            } catch (_: Exception) {}

            // 2. Focused editable node
            try {
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused != null && focused.isEditable) {
                    return focused
                }
            } catch (_: Exception) {}

            // 3. Other common messenger input IDs
            val commonIds = listOf(
                "org.telegram.messenger:id/chat_text_edit",
                "com.google.android.apps.messaging:id/compose_message_text",
                "com.samsung.android.messaging:id/message_edit_text",
                "android:id/input"
            )
            for (id in commonIds) {
                try {
                    val nodes = root.findAccessibilityNodeInfosByViewId(id)
                    if (!nodes.isNullOrEmpty() && nodes[0].isEditable) {
                        return nodes[0]
                    }
                } catch (_: Exception) {}
            }

            // 4. Any editable node in hierarchy
            return findEditableNode(root)
        }

        fun getCurrentInputText(): String {
            val node = getActiveInputNode() ?: return ""
            node.refresh()
            return node.text?.toString() ?: ""
        }

        fun insertDirect(text: CharSequence): Boolean {
            if (text.isBlank()) return false
            val node = getActiveInputNode()
            if (node == null) {
                Log.w(TAG, "insertDirect: No active input node found")
                return false
            }

            try {
                node.refresh()
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                var success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                Log.d(TAG, "insertDirect ACTION_SET_TEXT result: $success, text='$text'")

                if (!success) {
                    // Fallback: Clipboard paste
                    val inst = getInstance()
                    if (inst != null) {
                        val clipboard = inst.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("voice_input", text)
                        clipboard.setPrimaryClip(clip)
                        success = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        Log.d(TAG, "insertDirect fallback ACTION_PASTE result: $success")
                    }
                }
                return success
            } catch (e: Exception) {
                Log.e(TAG, "insertDirect exception", e)
                return false
            }
        }

        fun performBackspace(): Boolean {
            val node = getActiveInputNode() ?: return false
            node.refresh()
            val current = node.text?.toString() ?: ""
            if (current.isEmpty()) return false

            val trimmed = current.trimEnd()
            val lastSpace = trimmed.lastIndexOf(' ')
            val newText = if (lastSpace >= 0) {
                trimmed.substring(0, lastSpace)
            } else if (trimmed.length > 1) {
                trimmed.substring(0, trimmed.length - 1)
            } else {
                ""
            }

            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            var success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            if (!success && newText.isEmpty()) {
                val inst = getInstance()
                if (inst != null) {
                    val clipboard = inst.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("empty", ""))
                    node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                }
            }
            Log.d(TAG, "performBackspace result: $success, newText='$newText'")
            return success
        }

        fun performClear(): Boolean {
            val node = getActiveInputNode() ?: return false
            node.refresh()
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
            }
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }

        fun performSend(): Boolean {
            val inst = getInstance() ?: return false
            inst.rootInActiveWindow?.let { root ->
                val sendBtn = findSendButton(root)
                if (sendBtn != null && sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d(TAG, "performSend succeeded on rootInActiveWindow")
                    return true
                }
            }
            try {
                for (win in inst.windows) {
                    if (win.displayId == 1 || win.isActive) {
                        win.root?.let { root ->
                            val sendBtn = findSendButton(root)
                            if (sendBtn != null && sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                Log.d(TAG, "performSend succeeded on window displayId=${win.displayId}")
                                return true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error performing send across windows", e)
            }
            return false
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
                val handler = Handler(Looper.getMainLooper())
                var attempts = 0
                val checkAndSend = object : Runnable {
                    override fun run() {
                        attempts++
                        try {
                            val freshRoot = inst.rootInActiveWindow ?: root
                            val sendBtn = findSendButton(freshRoot)
                            if (sendBtn != null) {
                                val clicked = sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                Log.d(TAG, "autoSend click on send button result: $clicked (attempt $attempts)")
                                if (clicked) return
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error clicking send button", e)
                        }
                        if (attempts < 5) {
                            handler.postDelayed(this, 200)
                        }
                    }
                }
                handler.postDelayed(checkAndSend, 150)
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

            val isNotAudio = !desc.contains("audio") && !desc.contains("suara") && !desc.contains("voice")
            val isSendMatch = viewId.endsWith(":id/send") ||
                    viewId.endsWith(":id/button_send") ||
                    desc.contains("kirim") || desc.contains("send") ||
                    text == "send" || text == "kirim"

            if (isNotAudio && isSendMatch) {
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
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED or
                    AccessibilityEvent.TYPE_VIEW_SELECTED
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

    private fun findRecentsRoot(): AccessibilityNodeInfo? {
        try {
            for (window in windows) {
                val root = window.root
                if (root?.packageName == "com.sec.android.app.launcher") {
                    return root
                }
            }
        } catch (_: Exception) {}
        val active = rootInActiveWindow
        if (active?.packageName == "com.sec.android.app.launcher") {
            return active
        }
        return active
    }

    private fun checkAndExecuteRecentsAction() {
        if (!isExecutingRecentsAction) return

        val root = findRecentsRoot() ?: return

        if (isCloseAllRequested) {
            val clearButtons = root.findAccessibilityNodeInfosByViewId("com.sec.android.app.launcher:id/clear_all")
            if (!clearButtons.isNullOrEmpty()) {
                clearButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Successfully clicked clear_all button in system recents")
                isExecutingRecentsAction = false
                isCloseAllRequested = false
                return
            }

            // Fallback by text
            val byText = root.findAccessibilityNodeInfosByText("Tutup semua")
                ?: root.findAccessibilityNodeInfosByText("Close all")
            if (!byText.isNullOrEmpty()) {
                byText[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.d(TAG, "Successfully clicked clear_all by text in system recents")
                isExecutingRecentsAction = false
                isCloseAllRequested = false
                return
            }
        } else if (targetLabelToClose != null) {
            val target = targetLabelToClose ?: return
            val tasks = root.findAccessibilityNodeInfosByViewId("com.sec.android.app.launcher:id/taskView")
            if (!tasks.isNullOrEmpty()) {
                for (task in tasks) {
                    val desc = task.contentDescription?.toString() ?: ""
                    if (desc.contains(target, ignoreCase = true)) {
                        val hasDismiss = task.actionList.any {
                            it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id
                        }
                        if (hasDismiss) {
                            task.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id)
                            Log.d(TAG, "Dismissed task using ACTION_DISMISS: $desc")
                        } else {
                            val rect = Rect()
                            task.getBoundsInScreen(rect)
                            val startX = rect.centerX().toFloat()
                            val startY = rect.centerY().toFloat()
                            val path = Path().apply {
                                moveTo(startX, startY)
                                lineTo(startX, startY - 700f)
                            }
                            val gesture = GestureDescription.Builder()
                                .addStroke(GestureDescription.StrokeDescription(path, 0, 150))
                                .build()
                            dispatchGesture(gesture, null, null)
                            Log.d(TAG, "Dismissed task using swipe gesture: $desc")
                        }
                        isExecutingRecentsAction = false
                        targetLabelToClose = null
                        Handler(Looper.getMainLooper()).postDelayed({
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }, 300)
                        return
                    }
                }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SELECTED -> {
                com.yopa.samsprung.drawer.CoverScreenTimeoutManager.userActivity("a11y_interaction")
            }
        }

        val src = try { event.source } catch (_: Exception) { null }
        if (src != null) {
            val isEditable = src.isEditable ||
                    src.className?.contains("EditText") == true ||
                    src.viewIdResourceName?.contains("entry") == true
            if (isEditable) {
                cachedInputNode = src
            }
        }

        if (isExecutingRecentsAction) {
            checkAndExecuteRecentsAction()
        }

        val listener = inputFocusListener ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val editable = src != null && (src.isEditable || src.className?.contains("EditText") == true)
                listener.onInputFocusChanged(editable)
            }
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (src != null && (src.isEditable || src.className?.contains("EditText") == true)) {
                    listener.onInputFocusChanged(true)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Handler(Looper.getMainLooper()).postDelayed({
                    val hasInput = getActiveInputNode() != null
                    listener.onInputFocusChanged(hasInput)
                }, 300)
            }
        }
    }

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
