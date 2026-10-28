package com.yopa.samsprung.settings.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.yopa.samsprung.AccessibilityObserver
import com.yopa.samsprung.R
import com.yopa.samsprung.settings.CoverPreferences
import com.yopa.samsprung.settings.Preferences

class PermissionsScreenFragment : Fragment() {

    private lateinit var switchOverlay: SwitchCompat
    private lateinit var switchAccessibility: SwitchCompat
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var switchOptimization: SwitchCompat
    private lateinit var switchUsage: SwitchCompat
    private lateinit var switchVoice: SwitchCompat
    private lateinit var switchSecureSettings: SwitchCompat

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.screen_permissions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        switchOverlay = view.findViewById(R.id.switch_overlay)
        switchAccessibility = view.findViewById(R.id.switch_accessibility)
        switchNotifications = view.findViewById(R.id.switch_notifications)
        switchOptimization = view.findViewById(R.id.switch_optimization)
        switchUsage = view.findViewById(R.id.switch_usage)
        switchVoice = view.findViewById(R.id.switch_voice)
        switchSecureSettings = view.findViewById(R.id.switch_secure_settings)

        val prefActivity = activity as? CoverPreferences

        view.findViewById<View>(R.id.card_secure_settings).setOnClickListener {
            val context = requireContext()
            if (com.yopa.samsprung.drawer.DensityHelper.hasPermission(context)) {
                android.widget.Toast.makeText(context, "Izin Pengaturan Sistem Aman sudah aktif.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("ADB Command", "adb shell pm grant com.yopa.samsprung android.permission.WRITE_SECURE_SETTINGS")
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(context, "Perintah ADB disalin ke clipboard! Jalankan di PC/Terminal.", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        view.findViewById<View>(R.id.card_overlay).setOnClickListener {
            try {
                startActivity(Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${requireContext().packageName}")
                ))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
        }

        view.findViewById<View>(R.id.card_accessibility).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {}
        }

        view.findViewById<View>(R.id.card_notifications).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {}
        }

        view.findViewById<View>(R.id.card_optimization).setOnClickListener {
            try {
                startActivity(Intent(
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                    Uri.parse("package:${requireContext().packageName}")
                ))
            } catch (_: ActivityNotFoundException) {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {}
            }
        }

        view.findViewById<View>(R.id.card_usage).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {}
        }

        val prefs = requireActivity().getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)

        view.findViewById<View>(R.id.card_voice).setOnClickListener {
            val currentlyEnabled = prefs.getBoolean(Preferences.prefFloatingVoice, true)
            if (currentlyEnabled) {
                // User wants to turn it OFF
                prefs.edit().putBoolean(Preferences.prefFloatingVoice, false).apply()
                switchVoice.isChecked = false
                Toast.makeText(requireContext(), "Fitur Suara / Voice Typing dimatikan", Toast.LENGTH_SHORT).show()
            } else {
                // User wants to turn it ON
                if (prefActivity?.hasPermission(Manifest.permission.RECORD_AUDIO) == true) {
                    prefs.edit().putBoolean(Preferences.prefFloatingVoice, true).apply()
                    switchVoice.isChecked = true
                    Toast.makeText(requireContext(), "Fitur Suara / Voice Typing diaktifkan", Toast.LENGTH_SHORT).show()
                } else {
                    prefActivity?.requestVoicePermission()
                    prefs.edit().putBoolean(Preferences.prefFloatingVoice, true).apply()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    fun updatePermissionStates() {
        val context = context ?: return
        val prefActivity = activity as? CoverPreferences ?: return
        val prefs = context.getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)

        switchOverlay.isChecked = Settings.canDrawOverlays(context)
        switchAccessibility.isChecked = AccessibilityObserver.hasEnabledService(context)
        switchNotifications.isChecked = prefActivity.hasNotificationListener()
        switchOptimization.isChecked = prefActivity.ignoreBatteryOptimization()
        switchUsage.isChecked = prefActivity.hasUsageStatistics()
        val voiceEnabled = prefs.getBoolean(Preferences.prefFloatingVoice, true)
        val hasAudioPerm = prefActivity.hasPermission(Manifest.permission.RECORD_AUDIO)
        switchVoice.isChecked = voiceEnabled && hasAudioPerm
        switchSecureSettings.isChecked = com.yopa.samsprung.drawer.DensityHelper.hasPermission(context)
    }
}
