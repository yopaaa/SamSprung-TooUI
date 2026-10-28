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

package com.yopa.samsprung.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import com.google.android.material.button.MaterialButton
import com.yopa.samsprung.R
import com.yopa.samsprung.view.CoverCropView

class WallpaperCropDialog(
    context: Context,
    private val sourceBitmap: Bitmap,
    private val initialPortrait: Boolean = false,
    private val onCropped: (Bitmap) -> Unit
) : Dialog(context, R.style.Theme_Overlay) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_wallpaper_crop)

        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.95).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }

        val cropView = findViewById<CoverCropView>(R.id.cover_crop_view)
        cropView.isPortraitMode = initialPortrait
        cropView.setSourceBitmap(sourceBitmap)

        val btnToggleOrientation = findViewById<MaterialButton>(R.id.btn_toggle_orientation)
        val btnRotate = findViewById<MaterialButton>(R.id.btn_crop_rotate)
        val btnReset = findViewById<MaterialButton>(R.id.btn_crop_reset)
        val btnCancel = findViewById<Button>(R.id.btn_crop_cancel)
        val btnApply = findViewById<Button>(R.id.btn_crop_apply)

        if (initialPortrait) {
            btnToggleOrientation.text = "Potret (260×512)"
        } else {
            btnToggleOrientation.text = "Lanskap (512×260)"
        }

        btnToggleOrientation.setOnClickListener {
            cropView.isPortraitMode = !cropView.isPortraitMode
            if (cropView.isPortraitMode) {
                btnToggleOrientation.text = "Potret (260×512)"
            } else {
                btnToggleOrientation.text = "Lanskap (512×260)"
            }
        }

        btnRotate.setOnClickListener {
            cropView.rotate90()
        }

        btnReset.setOnClickListener {
            cropView.resetFit()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnApply.setOnClickListener {
            val result = cropView.cropResultBitmap(512, 260)
            if (result != null) {
                onCropped(result)
            }
            dismiss()
        }
    }
}
