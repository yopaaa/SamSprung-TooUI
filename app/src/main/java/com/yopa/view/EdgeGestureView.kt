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

package com.yopa.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.TypedValue
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import com.yopa.os.Version
import com.yopa.samsprung.AccessibilityObserver
import kotlin.math.abs

@SuppressLint("ViewConstructor")
class EdgeGestureView(
    context: Context,
    val isLeftEdge: Boolean,
    private val windowManager: WindowManager,
    private val onBackAction: (() -> Unit)? = null
) : View(context) {

    private val vibrator: Vibrator by lazy {
        if (Version.isSnowCone) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    private val effectArmed = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
    private val effectTriggered = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)

    private val idleWidthPx: Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics
    ).toInt()

    var isGestureEnabled: Boolean = true
        set(value) {
            field = value
            if (!value) {
                resetState()
            }
        }

    private val expandedWidthPx: Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 110f, resources.displayMetrics
    ).toInt()

    private val thresholdDistancePx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 32f, resources.displayMetrics
    )

    private val bubbleRadiusPx: Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics
    )

    private var startRawX = 0f
    private var startRawY = 0f
    private var currentRawX = 0f
    private var currentRawY = 0f

    private var isTracking = false
    private var isArmed = false

    private var velocityTracker: VelocityTracker? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2.5f, resources.displayMetrics)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
    }

    private val arrowPath = Path()
    private val viewLoc = IntArray(2)

    fun getIdleWidth(): Int = idleWidthPx

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isGestureEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                currentRawX = event.rawX
                currentRawY = event.rawY
                isTracking = true
                isArmed = false

                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)

                expandWindow()
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isTracking) return false
                currentRawX = event.rawX
                currentRawY = event.rawY
                velocityTracker?.addMovement(event)

                val deltaX = if (isLeftEdge) (currentRawX - startRawX) else (startRawX - currentRawX)
                val deltaY = abs(currentRawY - startRawY)

                val horizontalMove = deltaX > 0 && deltaX > deltaY * 0.6f

                if (horizontalMove && deltaX >= thresholdDistancePx) {
                    if (!isArmed) {
                        isArmed = true
                        try { vibrator.vibrate(effectArmed) } catch (_: Exception) {}
                    }
                } else if (isArmed && deltaX < thresholdDistancePx * 0.75f) {
                    isArmed = false
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isTracking) {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val xVel = velocityTracker?.xVelocity ?: 0f
                    val inwardVelocity = if (isLeftEdge) xVel else -xVel

                    val deltaX = if (isLeftEdge) (currentRawX - startRawX) else (startRawX - currentRawX)
                    val deltaY = abs(currentRawY - startRawY)

                    val quickFlick = deltaX > 15f && inwardVelocity > 500f && deltaX > deltaY

                    if (isArmed || quickFlick) {
                        try { vibrator.vibrate(effectTriggered) } catch (_: Exception) {}
                        triggerBack()
                    }
                }
                resetState()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetState()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun triggerBack() {
        post {
            onBackAction?.invoke() ?: AccessibilityObserver.performBackAction()
        }
    }

    private fun expandWindow() {
        try {
            val params = layoutParams as? WindowManager.LayoutParams ?: return
            params.width = expandedWidthPx
            windowManager.updateViewLayout(this, params)
        } catch (_: Exception) {}
    }

    private fun shrinkWindow() {
        try {
            val params = layoutParams as? WindowManager.LayoutParams ?: return
            params.width = idleWidthPx
            windowManager.updateViewLayout(this, params)
        } catch (_: Exception) {}
    }

    private fun resetState() {
        isTracking = false
        isArmed = false
        velocityTracker?.recycle()
        velocityTracker = null
        shrinkWindow()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isTracking) return

        val deltaX = if (isLeftEdge) (currentRawX - startRawX) else (startRawX - currentRawX)
        if (deltaX <= 0) return

        getLocationOnScreen(viewLoc)

        val progress = (deltaX / thresholdDistancePx).coerceIn(0f, 1f)
        val currentRadius = bubbleRadiusPx * (0.85f + 0.25f * progress)

        val localY = currentRawY - viewLoc[1]
        val centerY = localY.coerceIn(currentRadius + 8f, height.toFloat() - currentRadius - 8f)

        val centerDistance = (deltaX * 0.45f).coerceIn(currentRadius * 0.7f, expandedWidthPx * 0.45f)
        val centerX = if (isLeftEdge) centerDistance else (width - centerDistance)

        // Color transition: translucent dark -> vibrant Android back gesture blue
        if (isArmed) {
            bgPaint.color = Color.argb(235, 0, 122, 255) // Bright active gesture blue
        } else {
            val alpha = (100 + 90 * progress).toInt().coerceIn(0, 255)
            bgPaint.color = Color.argb(alpha, 25, 25, 25) // Frosted glass dark
        }

        canvas.drawCircle(centerX, centerY, currentRadius, bgPaint)

        // Draw chevron back arrow pointing left (<)
        val arrowSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 5.5f, resources.displayMetrics
        )
        arrowPath.reset()
        arrowPath.moveTo(centerX + arrowSize * 0.4f, centerY - arrowSize)
        arrowPath.lineTo(centerX - arrowSize * 0.6f, centerY)
        arrowPath.lineTo(centerX + arrowSize * 0.4f, centerY + arrowSize)

        canvas.drawPath(arrowPath, arrowPaint)
    }
}
