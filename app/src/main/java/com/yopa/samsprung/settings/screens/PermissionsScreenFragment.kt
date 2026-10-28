package com.yopa.samsprung.settings.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.yopa.samsprung.AccessibilityObserver
import com.yopa.samsprung.R
import com.yopa.samsprung.settings.CoverPreferences

class PermissionsScreenFragment : Fragment() {

    private lateinit var switchOverlay: SwitchCompat
    private lateinit var switchAccessibility: SwitchCompat
    private lateinit var switchNotifications: SwitchCompat
    private lateinit var switchOptimization: SwitchCompat
    private lateinit var switchUsage: SwitchCompat
    private lateinit var switchVoice: SwitchCompat

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

        val prefActivity = activity as? CoverPreferences

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

        view.findViewById<View>(R.id.card_voice).setOnClickListener {
            prefActivity?.requestVoicePermission()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    fun updatePermissionStates() {
        val context = context ?: return
        val prefActivity = activity as? CoverPreferences ?: return

        switchOverlay.isChecked = Settings.canDrawOverlays(context)
        switchAccessibility.isChecked = AccessibilityObserver.hasEnabledService(context)
        switchNotifications.isChecked = prefActivity.hasNotificationListener()
        switchOptimization.isChecked = prefActivity.ignoreBatteryOptimization()
        switchUsage.isChecked = prefActivity.hasUsageStatistics()
        switchVoice.isChecked = prefActivity.hasPermission(Manifest.permission.RECORD_AUDIO)
    }
}
