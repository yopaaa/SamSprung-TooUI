package com.yopa.samsprung.settings.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.yopa.samsprung.R
import com.yopa.samsprung.settings.CoverPreferences
import com.yopa.samsprung.settings.Preferences
import java.io.File

class CoverScreenFragment : Fragment() {

    private lateinit var ivWallpaperPreview: AppCompatImageView
    private lateinit var layoutWallpaperEmpty: View
    private lateinit var tvWallpaperBadge: TextView
    private lateinit var btnRemoveWallpaper: MaterialButton

    private lateinit var cardClockS1: MaterialCardView
    private lateinit var cardClockS2: MaterialCardView
    private lateinit var cardClockS3: MaterialCardView
    private lateinit var cardClockS4: MaterialCardView
    private lateinit var tvClockLabelS1: TextView
    private lateinit var tvClockLabelS2: TextView
    private lateinit var tvClockLabelS3: TextView
    private lateinit var tvClockLabelS4: TextView
    private lateinit var previewClockS1: View
    private lateinit var previewClockS2: View
    private lateinit var previewClockS3: View
    private lateinit var previewClockS4: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.screen_cover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)
        val prefActivity = activity as? CoverPreferences

        // 1. Rotation Switch
        val switchRotation = view.findViewById<SwitchCompat>(R.id.switch_rotation)
        switchRotation.isChecked = prefs.getBoolean(Preferences.prefRotate, false)
        switchRotation.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Preferences.prefRotate, isChecked).apply()
        }

        // 1.5 Clock Style Selection
        cardClockS1 = view.findViewById(R.id.card_clock_s1)
        cardClockS2 = view.findViewById(R.id.card_clock_s2)
        cardClockS3 = view.findViewById(R.id.card_clock_s3)
        cardClockS4 = view.findViewById(R.id.card_clock_s4)

        tvClockLabelS1 = view.findViewById(R.id.tv_clock_label_s1)
        tvClockLabelS2 = view.findViewById(R.id.tv_clock_label_s2)
        tvClockLabelS3 = view.findViewById(R.id.tv_clock_label_s3)
        tvClockLabelS4 = view.findViewById(R.id.tv_clock_label_s4)

        previewClockS1 = view.findViewById(R.id.preview_clock_s1)
        previewClockS2 = view.findViewById(R.id.preview_clock_s2)
        previewClockS3 = view.findViewById(R.id.preview_clock_s3)
        previewClockS4 = view.findViewById(R.id.preview_clock_s4)

        val currentClockStyle = prefs.getInt(Preferences.prefClockStyle, Preferences.CLOCK_STYLE_1)
        updateClockStyleUI(currentClockStyle)

        fun selectClockStyle(style: Int) {
            prefs.edit().putInt(Preferences.prefClockStyle, style).apply()
            updateClockStyleUI(style)
        }

        cardClockS1.setOnClickListener { selectClockStyle(Preferences.CLOCK_STYLE_1) }
        cardClockS2.setOnClickListener { selectClockStyle(Preferences.CLOCK_STYLE_2) }
        cardClockS3.setOnClickListener { selectClockStyle(Preferences.CLOCK_STYLE_3) }
        cardClockS4.setOnClickListener { selectClockStyle(Preferences.CLOCK_STYLE_4) }

        // 2. Wallpaper Preview & Buttons
        ivWallpaperPreview = view.findViewById(R.id.iv_wallpaper_preview)
        layoutWallpaperEmpty = view.findViewById(R.id.layout_wallpaper_empty)
        tvWallpaperBadge = view.findViewById(R.id.tv_wallpaper_badge)
        btnRemoveWallpaper = view.findViewById(R.id.btn_remove_wallpaper)

        updateWallpaperPreview()

        view.findViewById<MaterialButton>(R.id.btn_choose_wallpaper).setOnClickListener {
            prefActivity?.pickWallpaper()
        }
        btnRemoveWallpaper.setOnClickListener {
            val png = File(requireContext().filesDir, "wallpaper.png")
            val gif = File(requireContext().filesDir, "wallpaper.gif")
            if (png.exists()) png.delete()
            if (gif.exists()) gif.delete()
            updateWallpaperPreview()
            Toast.makeText(requireContext(), "Wallpaper cover screen telah dihapus", Toast.LENGTH_SHORT).show()
        }

        // 3. Transition Animation Spinner
        val spinnerAnimation = view.findViewById<Spinner>(R.id.spinner_page_animation)
        val currentAnim = prefs.getInt(Preferences.prefPaging, 0)
        if (currentAnim in 0 until spinnerAnimation.count) {
            spinnerAnimation.setSelection(currentAnim)
        }
        spinnerAnimation.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                prefs.edit().putInt(Preferences.prefPaging, position).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 4. Cover Density Spinner
        val spinnerDensity = view.findViewById<Spinner>(R.id.spinner_cover_density)
        val textDensityHint = view.findViewById<TextView>(R.id.text_density_permission_hint)
        val btnCopyCmd = view.findViewById<MaterialButton>(R.id.btn_copy_density_cmd)

        val densityValues = resources.getIntArray(R.array.cover_density_values)
        val hasSecurePerm = com.yopa.samsprung.drawer.DensityHelper.hasPermission(requireContext())
        if (!hasSecurePerm) {
            textDensityHint.visibility = View.VISIBLE
            btnCopyCmd.visibility = View.VISIBLE
            btnCopyCmd.setOnClickListener {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("ADB Command", "adb shell pm grant com.yopa.samsprung android.permission.WRITE_SECURE_SETTINGS")
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), "Perintah ADB disalin ke clipboard", Toast.LENGTH_SHORT).show()
            }
        } else {
            textDensityHint.visibility = View.GONE
            btnCopyCmd.visibility = View.GONE
        }

        val activeDensity = com.yopa.samsprung.drawer.DensityHelper.getCurrentDensity(requireContext(), 1)
        val currentDensityPref = prefs.getInt(Preferences.prefCoverDensity, activeDensity)
        val densityIndex = densityValues.indexOf(currentDensityPref).let { if (it >= 0) it else 0 }
        spinnerDensity.setSelection(densityIndex)

        var isDensityInit = true
        spinnerDensity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (isDensityInit) {
                    isDensityInit = false
                    return
                }
                val selectedDensity = densityValues.getOrElse(position) { 160 }
                if (!com.yopa.samsprung.drawer.DensityHelper.hasPermission(requireContext())) {
                    Toast.makeText(
                        requireContext(),
                        "Izin WRITE_SECURE_SETTINGS belum aktif! Berikan izin via ADB.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                val success = com.yopa.samsprung.drawer.DensityHelper.setDensity(selectedDensity, 1)
                if (success) {
                    prefs.edit().putInt(Preferences.prefCoverDensity, selectedDensity).apply()
                    Toast.makeText(
                        requireContext(),
                        "Kerapatan layar cover diubah ke $selectedDensity DPI",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Gagal mengubah kerapatan layar cover",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 4. Floating Voice Switch
        val switchFloatingVoice = view.findViewById<SwitchCompat>(R.id.switch_floating_voice)
        switchFloatingVoice.isChecked = prefs.getBoolean(Preferences.prefFloatingVoice, true)
        switchFloatingVoice.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Preferences.prefFloatingVoice, isChecked).apply()
        }
        view.findViewById<View>(R.id.card_floating_voice).setOnClickListener {
            switchFloatingVoice.isChecked = !switchFloatingVoice.isChecked
        }

        // 5. Timeout SeekBar
        val textTimeoutVal = view.findViewById<TextView>(R.id.text_timeout_val)
        val seekbarTimeout = view.findViewById<SeekBar>(R.id.seekbar_timeout)
        val currentTimeout = prefs.getInt(Preferences.prefDelays, 5)
        seekbarTimeout.progress = currentTimeout
        textTimeoutVal.text = "$currentTimeout detik"

        seekbarTimeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                textTimeoutVal.text = "$progress detik"
                if (fromUser) {
                    prefs.edit().putInt(Preferences.prefDelays, progress).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        updateWallpaperPreview()
        val prefs = requireActivity().getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)
        updateClockStyleUI(prefs.getInt(Preferences.prefClockStyle, Preferences.CLOCK_STYLE_1))
    }

    private fun updateClockStyleUI(selectedStyle: Int) {
        if (!::cardClockS1.isInitialized) return
        val greenColor = Color.parseColor("#00E676")
        val dimStroke = Color.parseColor("#26FFFFFF")
        val selectedBg = Color.parseColor("#222222")
        val unselectedBg = Color.parseColor("#1A1A1A")
        val textDim = Color.parseColor("#B3FFFFFF")

        val strokeSelected = (2 * resources.displayMetrics.density).toInt()
        val strokeUnselected = (1 * resources.displayMetrics.density).toInt()

        fun updateCard(card: MaterialCardView, label: TextView, isSelected: Boolean) {
            if (isSelected) {
                card.strokeWidth = strokeSelected
                card.strokeColor = greenColor
                card.setCardBackgroundColor(selectedBg)
                label.setTextColor(greenColor)
            } else {
                card.strokeWidth = strokeUnselected
                card.strokeColor = dimStroke
                card.setCardBackgroundColor(unselectedBg)
                label.setTextColor(textDim)
            }
        }

        updateCard(cardClockS1, tvClockLabelS1, selectedStyle == Preferences.CLOCK_STYLE_1)
        updateCard(cardClockS2, tvClockLabelS2, selectedStyle == Preferences.CLOCK_STYLE_2)
        updateCard(cardClockS3, tvClockLabelS3, selectedStyle == Preferences.CLOCK_STYLE_3)
        updateCard(cardClockS4, tvClockLabelS4, selectedStyle == Preferences.CLOCK_STYLE_4)

        previewClockS1.visibility = if (selectedStyle == Preferences.CLOCK_STYLE_1) View.VISIBLE else View.GONE
        previewClockS2.visibility = if (selectedStyle == Preferences.CLOCK_STYLE_2) View.VISIBLE else View.GONE
        previewClockS3.visibility = if (selectedStyle == Preferences.CLOCK_STYLE_3) View.VISIBLE else View.GONE
        previewClockS4.visibility = if (selectedStyle == Preferences.CLOCK_STYLE_4) View.VISIBLE else View.GONE
    }

    fun updateWallpaperPreview() {
        if (!::ivWallpaperPreview.isInitialized) return
        val context = context ?: return
        val gif = File(context.filesDir, "wallpaper.gif")
        val png = File(context.filesDir, "wallpaper.png")

        if (gif.exists()) {
            try {
                val source = ImageDecoder.createSource(context.contentResolver, Uri.fromFile(gif))
                val drawable = ImageDecoder.decodeDrawable(source)
                ivWallpaperPreview.setImageDrawable(drawable)
                ivWallpaperPreview.visibility = View.VISIBLE
                layoutWallpaperEmpty.visibility = View.GONE
                tvWallpaperBadge.text = "Animasi GIF"
                btnRemoveWallpaper.isEnabled = true
                if (drawable is AnimatedImageDrawable) {
                    drawable.start()
                }
                return
            } catch (e: Exception) {
                Log.w("CoverScreenFragment", "Failed to decode wallpaper.gif preview", e)
            }
        }

        if (png.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(png.absolutePath)
                if (bitmap != null) {
                    ivWallpaperPreview.setImageBitmap(bitmap)
                    ivWallpaperPreview.visibility = View.VISIBLE
                    layoutWallpaperEmpty.visibility = View.GONE
                    tvWallpaperBadge.text = "${bitmap.width} × ${bitmap.height}"
                    btnRemoveWallpaper.isEnabled = true
                    return
                }
            } catch (e: Exception) {
                Log.w("CoverScreenFragment", "Failed to decode wallpaper.png preview", e)
            }
        }

        // Empty state
        ivWallpaperPreview.setImageDrawable(null)
        ivWallpaperPreview.visibility = View.GONE
        layoutWallpaperEmpty.visibility = View.GONE
        tvWallpaperBadge.text = "Wallpaper Default (512×260)"
        btnRemoveWallpaper.isEnabled = false
    }
}
