package com.yopa.samsprung.settings.screens

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.yopa.samsprung.R
import com.yopa.samsprung.settings.CoverPreferences
import com.yopa.samsprung.settings.Preferences
import java.io.File

class CoverScreenFragment : Fragment() {

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

        // 2. Wallpaper Buttons
        view.findViewById<MaterialButton>(R.id.btn_choose_wallpaper).setOnClickListener {
            prefActivity?.pickWallpaper()
        }
        view.findViewById<MaterialButton>(R.id.btn_remove_wallpaper).setOnClickListener {
            val png = File(requireContext().filesDir, "wallpaper.png")
            val gif = File(requireContext().filesDir, "wallpaper.gif")
            if (png.exists()) png.delete()
            if (gif.exists()) gif.delete()
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
            switchFloatingVoice.toggle()
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
}
