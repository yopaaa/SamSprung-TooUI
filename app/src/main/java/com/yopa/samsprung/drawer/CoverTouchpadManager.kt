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
import android.widget.FrameLayout
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
    var onSwipeUpFromBottom: (() -> Unit)? = null
) {
    companion object {
        private const val TAG = "CoverTouchpadManager"
        private const val SENSITIVITY = 1.25f
        private const val DOUBLE_TAP_TIMEOUT_MS = 250L
    }

    private val themedContext = ContextThemeWrapper(context, R.style.Theme_SecondScreen)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Two-window architecture:
    // Window 1: cursorOverlayView (visual only, ALWAYS FLAG_NOT_TOUCHABLE, never flickers)
    // Window 2: touchSurfaceView (transparent touch interceptor, height excludes bottom nav)
    private var cursorOverlayView: View? = null
    private var touchSurfaceView: View? = null
    private var ivCursor: ImageView? = null
    private var viewRipple: View? = null

    var isShowing: Boolean = false
        private set

    private var cursorX = 130f
    private var cursorY = 256f

    private var screenWidth = 260
    private var screenHeight = 512

    private val density = themedContext.resources.displayMetrics.density
    private var currentBottomOffsetPx: Int = (54f * density).toInt()

    // State guards to prevent touch leaks and collision
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

    // Edge & bottom gesture state
    private var isEdgeGestureLeft = false
    private var isEdgeGestureRight = false
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
        val surface = touchSurfaceView ?: return
        val params = surface.layoutParams as? WindowManager.LayoutParams ?: return
        val newHeight = (screenHeight - offsetPx).coerceAtLeast(100)
        if (params.height != newHeight) {
            params.height = newHeight
            try {
                windowManager.updateViewLayout(surface, params)
                Log.d(TAG, "Updated touchSurface height: ${newHeight}px (offset: ${offsetPx}px)")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating surface height", e)
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
            // 1. Create and attach Touch Surface Window (Window 2)
            val surface = View(themedContext).apply {
                setBackgroundColor(0) // completely transparent
            }
            touchSurfaceView = surface

            var downX = 0f
            var downY = 0f
            var lastX = 0f
            var lastY = 0f
            var downTime = 0L
            var hasMoved = false
            var isTwoFinger = false
            var twoFingerStartY = 0f

            surface.setOnTouchListener { _, event ->
                // CRITICAL: Filter out any synthetic/accessibility touches
                if (event.deviceId < 0 || (event.flags and 0x800) != 0) {
                    return@setOnTouchListener false
                }

                CoverScreenTimeoutManager.userActivity("touchpad_touch")
                val now = SystemClock.uptimeMillis()

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        isPhysicalFingerDown = true

                        // If touchable restore was pending, restore immediately
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
                            // Check edge zones
                            isEdgeGestureLeft = downX <= (28f * density)
                            isEdgeGestureRight = downX >= screenWidth - (28f * density)
                            isBottomGesture = downY >= (screenHeight - currentBottomOffsetPx) - (24f * density)
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
                            if (abs(dy) > 12f) {
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

                                if (abs(scrollAccumulatorY) > 12f) {
                                    val scrollDy = scrollAccumulatorY
                                    scrollAccumulatorY = 0f
                                    performScroll(scrollDy)
                                }
                                return@setOnTouchListener true
                            }
                        }

                        // C. Edge Back Gestures (swipe inward from left/right edge)
                        if (isEdgeGestureLeft) {
                            val dx = event.rawX - downX
                            val dy = abs(event.rawY - downY)
                            if (dx > (30f * density) && dx > dy * 1.2f) {
                                isEdgeGestureLeft = false
                                vibrateClick()
                                hideTouchpad()
                                AccessibilityObserver.performBackAction()
                                return@setOnTouchListener true
                            }
                        }
                        if (isEdgeGestureRight) {
                            val dx = downX - event.rawX
                            val dy = abs(event.rawY - downY)
                            if (dx > (30f * density) && dx > dy * 1.2f) {
                                isEdgeGestureRight = false
                                vibrateClick()
                                hideTouchpad()
                                AccessibilityObserver.performBackAction()
                                return@setOnTouchListener true
                            }
                        }

                        // D. Bottom Edge Swipe Up: Bring up navigation bar
                        if (isBottomGesture) {
                            val dy = downY - event.rawY
                            val dx = abs(event.rawX - downX)
                            if (dy > (18f * density) && dy > dx * 1.2f) {
                                isBottomGesture = false
                                vibrateClick()
                                onSwipeUpFromBottom?.invoke()
                                return@setOnTouchListener true
                            }
                        }

                        // E. Normal cursor movement
                        val dx = (event.rawX - lastX) * SENSITIVITY
                        val dy = (event.rawY - lastY) * SENSITIVITY

                        val dist = hypot((event.rawX - downX).toDouble(), (event.rawY - downY).toDouble())
                        if (dist > touchSlop) {
                            hasMoved = true
                        }

                        cursorX = (cursorX + dx).coerceIn(0f, screenWidth.toFloat())
                        cursorY = (cursorY + dy).coerceIn(0f, screenHeight.toFloat())

                        ivCursor?.x = cursorX
                        ivCursor?.y = cursorY

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

                        // Double tap without drag -> perform immediate second click at cursor
                        if (isDoubleTapCandidate && !hasMoved && totalDist <= touchSlop) {
                            isDoubleTapCandidate = false
                            lastTapTime = 0L
                            performClickAt(cursorX, cursorY)
                            return@setOnTouchListener true
                        }

                        // Normal single tap or long click
                        if (!isTwoFinger && !hasMoved && totalDist <= touchSlop) {
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
                        isEdgeGestureLeft = false
                        isEdgeGestureRight = false
                        isBottomGesture = false
                        true
                    }

                    MotionEvent.ACTION_CANCEL -> {
                        isPhysicalFingerDown = false
                        isDoubleTapCandidate = false
                        isDoubleTapDragging = false
                        hasMoved = false
                        isTwoFinger = false
                        isEdgeGestureLeft = false
                        isEdgeGestureRight = false
                        isBottomGesture = false
                        true
                    }
                    else -> false
                }
            }

            val surfaceHeight = (screenHeight - currentBottomOffsetPx).coerceAtLeast(100)
            val surfaceParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                surfaceHeight,
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
            windowManager.addView(surface, surfaceParams)

            // 2. Create and attach Cursor Visual Window (Window 1)
            // ALWAYS FLAG_NOT_TOUCHABLE: never intercepts touches, never flickers
            val cursorView = LayoutInflater.from(themedContext).inflate(R.layout.layout_cover_touchpad, null)
            cursorOverlayView = cursorView
            ivCursor = cursorView.findViewById(R.id.iv_cursor_pointer)
            viewRipple = cursorView.findViewById(R.id.view_click_ripple)

            ivCursor?.x = cursorX
            ivCursor?.y = cursorY

            cursorView.post {
                if (cursorView.width > 0) screenWidth = cursorView.width
                if (cursorView.height > 0) screenHeight = cursorView.height
            }

            val cursorParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 0
                y = 0
            }
            windowManager.addView(cursorView, cursorParams)

            isShowing = true
            vibrateClick()
            onOpenListener?.invoke()
            Log.d(TAG, "Touchpad windows attached successfully (${screenWidth}x$screenHeight)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach touchpad windows", e)
            hideTouchpad()
        }
    }

    private fun performClickAt(x: Float, y: Float) {
        if (isPhysicalFingerDown) {
            Log.d(TAG, "performClickAt: physical finger still down, skipping")
            return
        }

        // Instant visual and haptic response
        triggerClickRipple(x, y)
        vibrateClick()

        val surface = touchSurfaceView ?: return
        val params = surface.layoutParams as? WindowManager.LayoutParams ?: return

        isExecutingClick = true

        // 1. Make touch surface untouchable
        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(surface, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting FLAG_NOT_TOUCHABLE", e)
            isExecutingClick = false
            return
        }

        // 2. Dispatch click after 16ms (1 frame for WMS to sync flags)
        mainHandler.postDelayed({
            // If user touched down again during these 16ms, abort immediately to prevent leak!
            if (isPhysicalFingerDown) {
                Log.w(TAG, "Physical touch detected before click injection, aborting")
                restoreTouchable()
                return@postDelayed
            }

            var finished = false
            val finishAction = {
                if (!finished) {
                    finished = true
                    mainHandler.post { restoreTouchable() }
                }
            }

            // Safety watchdog: unconditionally restore touchable in 40ms max!
            restoreTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            val timeoutAction = Runnable { finishAction() }
            restoreTimeoutRunnable = timeoutAction
            mainHandler.postDelayed(timeoutAction, 40)

            // Inject 15ms tap at exact cursor coordinates
            val dispatched = AccessibilityObserver.clickAt(x, y, displayId = 1) {
                finishAction()
            }

            if (!dispatched) {
                Log.w(TAG, "AccessibilityObserver.clickAt returned false")
                finishAction()
            }
        }, 16)
    }

    private fun performLongClickAt(x: Float, y: Float) {
        if (isPhysicalFingerDown) return

        triggerClickRipple(x, y)
        vibrateHeavy()

        val surface = touchSurfaceView ?: return
        val params = surface.layoutParams as? WindowManager.LayoutParams ?: return

        isExecutingClick = true

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(surface, params)
        } catch (e: Exception) {
            Log.e(TAG, "Error setting FLAG_NOT_TOUCHABLE", e)
            isExecutingClick = false
            return
        }

        mainHandler.postDelayed({
            if (isPhysicalFingerDown) {
                restoreTouchable()
                return@postDelayed
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
            mainHandler.postDelayed(timeoutAction, 450)

            val dispatched = AccessibilityObserver.longClickAt(x, y, displayId = 1) {
                finishAction()
            }

            if (!dispatched) {
                finishAction()
            }
        }, 16)
    }

    private fun performScroll(dy: Float) {
        if (isExecutingClick || isExecutingScroll) return

        // 1. Semantic scroll via AccessibilityNodeInfo
        // Zero FLAG_NOT_TOUCHABLE, zero touch leak, works directly on RecyclerView / ScrollView!
        val scrolledViaNode = AccessibilityObserver.scrollAt(cursorX, cursorY, dy, displayId = 1)
        if (scrolledViaNode) {
            return
        }

        // 2. Fallback: swipe gesture
        isExecutingScroll = true
        val startY = cursorY.coerceIn(40f, screenHeight - 40f)
        val endY = (startY + dy * 2.0f).coerceIn(10f, screenHeight - 10f)

        val surface = touchSurfaceView ?: run {
            isExecutingScroll = false
            return
        }
        val params = surface.layoutParams as? WindowManager.LayoutParams ?: run {
            isExecutingScroll = false
            return
        }

        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try {
            windowManager.updateViewLayout(surface, params)
        } catch (_: Exception) {}

        mainHandler.postDelayed({
            AccessibilityObserver.swipeAt(cursorX, startY, cursorX, endY, durationMs = 60, displayId = 1) {
                restoreTouchable()
                isExecutingScroll = false
            }
            mainHandler.postDelayed({
                restoreTouchable()
                isExecutingScroll = false
            }, 80)
        }, 16)
    }

    private fun restoreTouchable() {
        restoreTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        restoreTimeoutRunnable = null
        isExecutingClick = false

        val surface = touchSurfaceView ?: return
        val params = surface.layoutParams as? WindowManager.LayoutParams ?: return

        if ((params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            try {
                windowManager.updateViewLayout(surface, params)
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
            touchSurfaceView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing touch surface view", e)
        }
        try {
            cursorOverlayView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing cursor overlay view", e)
        }
        touchSurfaceView = null
        cursorOverlayView = null
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
    }

    private fun vibrateClick() {
        try {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } catch (_: Exception) {}
    }

    private fun vibrateTick() {
        try {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } catch (_: Exception) {}
    }

    private fun vibrateHeavy() {
        try {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } catch (_: Exception) {}
    }
}
