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

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class EdgeBackGestureManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onBackAction: (() -> Unit)? = null
) {
    private var leftEdgeView: EdgeGestureView? = null
    private var rightEdgeView: EdgeGestureView? = null
    private var isAttached = false
    private var isEdgesTouchable = true

    fun attach() {
        if (isAttached) return

        val left = EdgeGestureView(context, isLeftEdge = true, windowManager, onBackAction)
        val right = EdgeGestureView(context, isLeftEdge = false, windowManager, onBackAction)

        val leftParams = createLayoutParams(Gravity.START or Gravity.FILL_VERTICAL, left.getIdleWidth())
        val rightParams = createLayoutParams(Gravity.END or Gravity.FILL_VERTICAL, right.getIdleWidth())

        if (!isEdgesTouchable) {
            leftParams.flags = leftParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            rightParams.flags = rightParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            left.isGestureEnabled = false
            right.isGestureEnabled = false
            left.visibility = View.GONE
            right.visibility = View.GONE
        }

        try {
            windowManager.addView(left, leftParams)
            leftEdgeView = left
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            windowManager.addView(right, rightParams)
            rightEdgeView = right
        } catch (e: Exception) {
            e.printStackTrace()
        }

        isAttached = (leftEdgeView != null || rightEdgeView != null)
    }

    fun detach() {
        if (!isAttached) return

        leftEdgeView?.let {
            try {
                windowManager.removeViewImmediate(it)
            } catch (_: Exception) {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
        }
        rightEdgeView?.let {
            try {
                windowManager.removeViewImmediate(it)
            } catch (_: Exception) {
                try { windowManager.removeView(it) } catch (_: Exception) {}
            }
        }

        leftEdgeView = null
        rightEdgeView = null
        isAttached = false
    }

    fun setEdgesTouchable(touchable: Boolean) {
        isEdgesTouchable = touchable
        setEdgeTouchable(leftEdgeView, touchable)
        setEdgeTouchable(rightEdgeView, touchable)
    }

    private fun setEdgeTouchable(view: EdgeGestureView?, touchable: Boolean) {
        if (view == null) return
        view.isGestureEnabled = touchable
        view.visibility = if (touchable) View.VISIBLE else View.GONE
        try {
            val params = view.layoutParams as? WindowManager.LayoutParams ?: return
            val flags = if (touchable) {
                params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            if (params.flags != flags) {
                params.flags = flags
                windowManager.updateViewLayout(view, params)
            }
        } catch (_: Exception) {}
    }

    fun setLeftEdgeEnabled(enabled: Boolean) {
        setEdgeTouchable(leftEdgeView, enabled)
    }

    private fun createLayoutParams(gravity: Int, width: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
        }
    }
}
