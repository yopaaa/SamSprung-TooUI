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

package com.yopa.samsprung.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class CoverCropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var sourceBitmap: Bitmap? = null
    private val imageMatrix = Matrix()

    // Crop box rectangle in view coordinates
    val cropRect = RectF()

    // Aspect ratio: width / height (default: 512 / 260 = ~1.9692)
    var targetAspectRatio: Float = 512f / 260f
        set(value) {
            field = value
            computeCropRect()
            fitImageToCropRect()
            invalidate()
        }

    var isPortraitMode: Boolean = false
        set(value) {
            field = value
            targetAspectRatio = if (value) 260f / 512f else 512f / 260f
        }

    // Drawing paints
    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#B3000000") // 70% black dim
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#00E676") // Accent green border
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        isAntiAlias = true
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#4DFFFFFF") // 30% white grid
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        isAntiAlias = true
    }

    // Touch & Gesture state
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            imageMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
    })

    fun setSourceBitmap(bitmap: Bitmap) {
        this.sourceBitmap = bitmap
        if (width > 0 && height > 0) {
            computeCropRect()
            fitImageToCropRect()
            invalidate()
        }
    }

    fun rotate90() {
        val bmp = sourceBitmap ?: return
        val matrix = Matrix().apply { postRotate(90f) }
        sourceBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        computeCropRect()
        fitImageToCropRect()
        invalidate()
    }

    fun resetFit() {
        computeCropRect()
        fitImageToCropRect()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeCropRect()
        fitImageToCropRect()
    }

    private fun computeCropRect() {
        if (width == 0 || height == 0) return
        val padding = 16f * resources.displayMetrics.density
        val availableW = width - padding * 2
        val availableH = height - padding * 2

        val viewRatio = availableW / availableH
        val cropW: Float
        val cropH: Float

        if (viewRatio > targetAspectRatio) {
            cropH = availableH
            cropW = cropH * targetAspectRatio
        } else {
            cropW = availableW
            cropH = cropW / targetAspectRatio
        }

        val left = (width - cropW) / 2f
        val top = (height - cropH) / 2f
        cropRect.set(left, top, left + cropW, top + cropH)
    }

    private fun fitImageToCropRect() {
        val bmp = sourceBitmap ?: return
        if (cropRect.isEmpty) return

        imageMatrix.reset()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()

        // Scale to fill the cropRect completely (centerCrop)
        val scale = max(cropRect.width() / bmpW, cropRect.height() / bmpH)
        val scaledW = bmpW * scale
        val scaledH = bmpH * scale

        val dx = cropRect.left + (cropRect.width() - scaledW) / 2f
        val dy = cropRect.top + (cropRect.height() - scaledH) / 2f

        imageMatrix.postScale(scale, scale)
        imageMatrix.postTranslate(dx, dy)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && !scaleDetector.isInProgress) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    imageMatrix.postTranslate(dx, dy)
                    invalidate()
                }
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw source bitmap with matrix
        sourceBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, imageMatrix, null)
        }

        // 2. Draw 4 dim rectangles outside the crop box
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, cropRect.top, overlayPaint) // top
        canvas.drawRect(0f, cropRect.bottom, w, h, overlayPaint) // bottom
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint) // left
        canvas.drawRect(cropRect.right, cropRect.top, w, cropRect.bottom, overlayPaint) // right

        // 3. Draw Rule of Thirds grid lines
        val oneThirdW = cropRect.width() / 3f
        val oneThirdH = cropRect.height() / 3f
        // Vertical lines
        canvas.drawLine(cropRect.left + oneThirdW, cropRect.top, cropRect.left + oneThirdW, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + oneThirdW * 2, cropRect.top, cropRect.left + oneThirdW * 2, cropRect.bottom, gridPaint)
        // Horizontal lines
        canvas.drawLine(cropRect.left, cropRect.top + oneThirdH, cropRect.right, cropRect.top + oneThirdH, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + oneThirdH * 2, cropRect.right, cropRect.top + oneThirdH * 2, gridPaint)

        // 4. Draw crop frame border
        canvas.drawRect(cropRect, borderPaint)
    }

    fun cropResultBitmap(targetWidth: Int = 512, targetHeight: Int = 260): Bitmap? {
        val bmp = sourceBitmap ?: return null
        if (cropRect.isEmpty) return null

        val inverse = Matrix()
        if (!imageMatrix.invert(inverse)) return null

        val mappedCrop = RectF(cropRect)
        inverse.mapRect(mappedCrop)

        val srcLeft = max(0, mappedCrop.left.toInt())
        val srcTop = max(0, mappedCrop.top.toInt())
        val srcRight = min(bmp.width, mappedCrop.right.toInt())
        val srcBottom = min(bmp.height, mappedCrop.bottom.toInt())

        val cropW = max(1, srcRight - srcLeft)
        val cropH = max(1, srcBottom - srcTop)

        val cropped = Bitmap.createBitmap(bmp, srcLeft, srcTop, cropW, cropH)
        val tw = if (isPortraitMode) min(targetWidth, targetHeight) else max(targetWidth, targetHeight)
        val th = if (isPortraitMode) max(targetWidth, targetHeight) else min(targetWidth, targetHeight)
        return Bitmap.createScaledBitmap(cropped, tw, th, true)
    }
}
