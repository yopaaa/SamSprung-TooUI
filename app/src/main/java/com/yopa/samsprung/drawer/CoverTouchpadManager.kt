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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import com.yopa.os.Version
import com.yopa.samsprung.AccessibilityObserver
import com.yopa.samsprung.R
import kotlin.math.abs
import kotlin.math.hypot

class CoverTouchpadManager(
    private val context: Context,
    private val windowManager: WindowManager,
    var onOpenListener: (() -> Unit)? = null,
    var onCloseListener: (() -> Unit)? = null,
    var onSwipeUpFromBottom: (() -> Unit)? = null,
    var onUserInteraction: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "CoverTouchpadManager"
        private const val SENSITIVITY = 1.25f
        private const val DOUBLE_TAP_TIMEOUT_MS = 250L
    }

    private val themedContext = ContextThemeWrapper(context, R.style.Theme_SecondScreen)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var touchpadView: View? = null
    private var ivCursor: ImageView? = null
    private var viewRipple: View? = null

    var isShowing: Boolean = false
        private set

    private var cursorX = 130f
    private var cursorY = 256f

    private var screenWidth = 260
    private var screenHeight = 512

    private val density = themedContext.resources.displayMetrics.density
    private val tipOffsetX = 0f
    private val tipOffsetY = 0f

    private var currentBottomOffsetPx: Int = (54f * density).toInt()

    // State guards to prevent touch leaks and conflicting gestures
    private var isExecutingClick = false
    private var isExecutingScroll = false
    private var isPhysicalFingerDown = false
    private var restoreTimeoutRunnable: Runnable? = null

    // Double-tap & drag to scroll
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var isDoubleTapCandidate = false
    private var isDoubleTapDragging = false
    private var scrollAccumulatorY = 0f

    // Bottom gesture state (swipe up reveals bottom nav)
    private var isBottomGesture = false

    private val vibrator: Vibrator by lazy {
        if (Version.isSnowCone) {
            with(context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager) {
                defaultVibrator
            }
        } else {
            @Suppress("deprecation")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val touchSlop = ViewConfiguration.get(themedContext).scaledTouchSlop

    fun updateBottomNavOffset(offsetPx: Int) {
        currentBottomOffsetPx = offsetPx
        val view = touchpadView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val newHeight = (screenHeight - offsetPx).coerceAtLeast(100)
        if (params.height != newHeight) {
            params.height = newHeight
            try {
                windowManager.updateViewLayout(view, params)
                Log.d(TAG, "Updated touchpadView height: ${newHeight}px (offset: ${offsetPx}px)")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating touchpadView height", e)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun showTouchpad() {
        if (isShowing) return

        try {
            val bounds = windowManager.maximumWindowMetrics.bounds
            screenWidth = if (bounds.width() > 0) bounds.width() else 260
            screenHeight = if (bounds.height() > 0) bounds.height() else 512
        } catch (_: Exception) {}

        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f

        try {
            val view = LayoutInflater.from(themedContext).inflate(R.layout.layout_cover_touchpad, null)
            touchpadView = view

            ivCursor = view.findViewById(R.id.iv_cursor_pointer)
            viewRipple = view.findViewById(R.id.view_click_ripple)
            val surface = view.findViewById<View>(R.id.touchpad_surface)

            ivCursor?.x = cursorX - tipOffsetX
            ivCursor?.y = cursorY - tipOffsetY

            view.post {
                if (view.width > 0) screenWidth = view.width
                if (view.height > 0) screenHeight = view.height
            }

            var downX = 0f
            var downY = 0f
            var lastX = 0f
            var lastY = 0f
            var downTime = 0L
            var hasMoved = false
            var isTwoFinger = false
            var twoFingerStartY = 0f

            surface.setOnTouchListener { _, event ->
                // Filter out virtual injected touches
                if (event.deviceId < 0) {
                    return@setOnTouchListener false
                }

                CoverScreenTimeoutManager.userActivity("touchpad_touch")
                val now = SystemClock.uptimeMillis()

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isPhysicalFingerDown = true
                        onUserInteraction?.invoke()

                        if (isExecutingClick) {
                            restoreTouchable()
                        }

                        val timeSinceLastTap = now - lastTapTime
                        val distFromLastTap = hypot((event.rawX - lastTapX).toDouble(), (event.rawY - lastTapY).toDouble())

                        downX = event.rawX
                        downY = event.rawY
                        lastX = event.rawX
                        lastY = event.rawY
                        downTime = now
                        hasMoved = false
                        isTwoFinger = false

                        if (timeSinceLastTap < DOUBLE_TAP_TIMEOUT_MS && distFromLastTap < (touchSlop * 3.0f)) {
                            // Candidate for double-tap drag scroll or double click
                            isDoubleTapCandidate = true
                            scrollAccumulatorY = 0f
                        } else {
                            isDoubleTapCandidate = false
                            isDoubleTapDragging = false
                            // Check bottom zone (swipe up reveals bottom nav)
                            isBottomGesture = downY >= (screenHeight - currentBottomOffsetPx) - (20f * density)
                        }
                        true
                    }

                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (event.pointerCount >= 2) {
                            isTwoFinger = true
                            isDoubleTapCandidate = false
                            isDoubleTapDragging = false
                            twoFingerStartY = (event.getY(0) + event.getY(1)) / 2f
                        }
                        true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        // A. Two-finger scroll
                        if (isTwoFinger && event.pointerCount >= 2) {
                            val currentTwoFingerY = (event.getY(0) + event.getY(1)) / 2f
                            val dy = currentTwoFingerY - twoFingerStartY
                            if (abs(dy) > 8f) {
                                twoFingerStartY = currentTwoFingerY
                                performScroll(dy)
                            }
                            return@setOnTouchListener true
                        }

                        // B. Double-tap and drag scroll
                        if (isDoubleTapCandidate || isDoubleTapDragging) {
                            val dist = hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble())
                            if (dist > touchSlop) {
                                if (!isDoubleTapDragging) {
                                    isDoubleTapDragging = true
                                    vibrateTick()
                                    Log.d(TAG, "Double-tap drag mode engaged")
                                }
                                val dy = event.rawY - lastY
                                scrollAccumulatorY += dy
                                lastX = event.rawX
                                lastY = event.rawY

                                if (abs(scrollAccumulatorY) > 8f) {
                                    val scrollDy = scrollAccumulatorY
                                    scrollAccumulatorY = 0f
                                    performScroll(scrollDy)
                                }
                                return@setOnTouchListener true
                            }
                        }

                        // C. Bottom Edge Swipe Up: Bring up navigation bar
                        if (isBottomGesture) {
                            val dy = downY - event.rawY
                            val dx = abs(event.rawX - downX)
                            if (dy > (16f * density) && dy > dx * 1.2f) {
                                isBottomGesture = false
                                vibrateClick()
                                onSwipeUpFromBottom?.invoke()
                                return@setOnTouchListener true
                            }
                        }

                        // D. Normal cursor movement across entire screen
                        val dx = (event.rawX - lastX) * SENSITIVITY
                        val dy = (event.rawY - lastY) * SENSITIVITY

                        val dist = hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble())
                        if (dist > touchSlop) {
                            hasMoved = true
                        }

                        cursorX = (cursorX + dx).coerceIn(1f, (screenWidth - 1).toFloat())
                        cursorY = (cursorY + dy).coerceIn(1f, (screenHeight - 1).toFloat())

                        ivCursor?.x = cursorX - tipOffsetX
                        ivCursor?.y = cursorY - tipOffsetY

                        lastX = event.rawX
                        lastY = event.rawY
                        true
                    }

                    MotionEvent.ACTION_UP -> {
                        isPhysicalFingerDown = false
                        val duration = now - downTime
                        val totalDist = hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble())

                        if (isDoubleTapDragging) {
                            isDoubleTapDragging = false
                            isDoubleTapCandidate = false
                            lastTapTime = 0L
                            return@setOnTouchListener true
                        }

                        // Double tap without drag -> perform second click at cursor
                        if (isDoubleTapCandidate && !hasMoved && totalDist <= (touchSlop * 2.0f)) {
                            isDoubleTapCandidate = false
                            lastTapTime = 0L
                            performClickAt(cursorX, cursorY)
                            return@setOnTouchListener true
                        }

                        // Normal single tap or long click
                        if (!isTwoFinger && !hasMoved && totalDist <= (touchSlop * 2.0f)) {
                            if (duration < 350) {
                                performClickAt(cursorX, cursorY)
                                lastTapTime = now
                                lastTapX = event.rawX
                                lastTapY = event.rawY
                            } else if (duration >= 450) {
                                performLongClickAt(cursorX, cursorY)
                                lastTapTime = 0L
                            }
                        }

                        hasMoved = false
                        isTwoFinger = false
                        isDoubleTapCandidate = false
                        isDoubleTapDragging = false
                        isBottomGesture = false
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        isPhysicalFingerDown = false
                        isDoubleTapCandidate = false
                        isDoubleTapDragging = false
                        hasMoved = false
                        isTwoFinger = false
                        isBottomGesture = false
                        true
                    }
                    else -> false
                }
            }

            // Window height covers area above bottom navigation bar
            val initialHeight = (screenHeight - currentBottomOffsetPx).coerceAtLeast(100)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                initialHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }

            windowManager.addView(view, params)
            isShowing = true
            vibrateClick()
            onOpenListener?.invoke()
            Log.d(TAG, "Touchpad overlay attached successfully (${screenWidth}x$initialHeight)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach touchpad overlay", e)
            hideTouchpad()
        }
    }

    private fun performClickAt(x: Float, y: Float) {
        if (isPhysicalFingerDown) {
            Log.d(TAG, "performClickAt: physical finger still down, skipping")
            return
        }

        if (isExecutingClick) {
            Log.d(TAG, "performClickAt: already executing click, skipping")
            return
        }

        isExecutingClick = true
        triggerClickRipple(x, y)
        vibrateClick()

        // 1. Try semantic node click first (instant, 0ms lag, zero leak, native)
        val nodeClicked = AccessibilityObserver.clickNodeAt(x, y, displayId = 1)
        if (nodeClicked) {
            Log.d(TAG, "performClickAt: node click succeeded at ($x, $y)")
            isExecutingClick = false
            return
        }

        // 2. Fallback: MotionEvent injection via dispatchGesture
        val view = touchpadView ?: run {
            isExecutingClick = false
            return
        }
        val params = view.layoutParams as? WindowManager.LayoutParams ?: run {
            isExecutingClick = false
            return
        }

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting FLAG_NOT_TOUCHABLE", e)
            isExecutingClick = false
            return
        }

        var finished = false
        val finishAction = {
            if (!finished) {
                finished = true
                mainHandler.post { restoreTouchable() }
            }
        }

        restoreTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        val timeoutAction = Runnable { finishAction() }
        restoreTimeoutRunnable = timeoutAction
        mainHandler.postDelayed(timeoutAction, 160)

        mainHandler.postDelayed({
            if (isPhysicalFingerDown) {
                Log.w(TAG, "Physical touch down before click injection, aborting")
                finishAction()
                return@postDelayed
            }

            val dispatched = AccessibilityObserver.clickAt(x, y, displayId = 1) {
                finishAction()
            }
            if (!dispatched) {
                Log.w(TAG, "AccessibilityObserver.clickAt returned false")
                finishAction()
            }
        }, 50)
    }

    private fun performLongClickAt(x: Float, y: Float) {
        if (isPhysicalFingerDown) return
        if (isExecutingClick) return

        isExecutingClick = true
        triggerClickRipple(x, y)
        vibrateHeavy()

        val view = touchpadView ?: run {
            isExecutingClick = false
            return
        }
        val params = view.layoutParams as? WindowManager.LayoutParams ?: run {
            isExecutingClick = false
            return
        }

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting FLAG_NOT_TOUCHABLE", e)
            isExecutingClick = false
            return
        }

        var finished = false
        val finishAction = {
            if (!finished) {
                finished = true
                mainHandler.post { restoreTouchable() }
            }
        }

        restoreTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        val timeoutAction = Runnable { finishAction() }
        restoreTimeoutRunnable = timeoutAction
        mainHandler.postDelayed(timeoutAction, 600)

        mainHandler.postDelayed({
            if (isPhysicalFingerDown) {
                finishAction()
                return@postDelayed
            }

            val dispatched = AccessibilityObserver.longClickAt(x, y, displayId = 1) {
                finishAction()
            }
            if (!dispatched) {
                finishAction()
            }
        }, 50)
    }

    private fun performScroll(dy: Float) {
        if (isExecutingClick || isExecutingScroll) return
        AccessibilityObserver.scrollAt(cursorX, cursorY, dy, displayId = 1)
    }

    private fun restoreTouchable() {
        restoreTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        restoreTimeoutRunnable = null
        isExecutingClick = false

        val view = touchpadView ?: return
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return

        if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "Error restoring touchable flags", e)
            }
        }
    }

    private fun triggerClickRipple(x: Float, y: Float) {
        val ripple = viewRipple ?: return
        ripple.x = x - (ripple.width / 2f)
        ripple.y = y - (ripple.height / 2f)
        ripple.alpha = 1.0f
        ripple.scaleX = 0.3f
        ripple.scaleY = 0.3f
        ripple.visibility = View.VISIBLE

        ripple.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                ripple.visibility = View.GONE
            }
            .start()
    }

    fun hideTouchpad() {
        if (!isShowing) return
        restoreTouchable()
        try {
            touchpadView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing touchpad view", e)
        }
        touchpadView = null
        ivCursor = null
        viewRipple = null
        isShowing = false
        isExecutingClick = false
        isExecutingScroll = false
        isPhysicalFingerDown = false
        isDoubleTapCandidate = false
        isDoubleTapDragging = false
        lastTapTime = 0L
        onCloseListener?.invoke()
        Log.d(TAG, "Touchpad overlay closed")
    }

    fun toggleTouchpad() {
        if (isShowing) {
            hideTouchpad()
        } else {
            showTouchpad()
        }
    }

    fun destroy() {
        hideTouchpad()
        onOpenListener = null
        onCloseListener = null
        onSwipeUpFromBottom = null
        onUserInteraction = null
    }

    private fun vibrateClick() {
        try {
            if (Version.isSnowCone) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("deprecation")
                vibrator.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateTick() {
        try {
            if (Version.isSnowCone) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("deprecation")
                vibrator.vibrate(10)
            }
        } catch (_: Exception) {}
    }

    private fun vibrateHeavy() {
        try {
            if (Version.isSnowCone) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("deprecation")
                vibrator.vibrate(50)
            }
        } catch (_: Exception) {}
    }
}
