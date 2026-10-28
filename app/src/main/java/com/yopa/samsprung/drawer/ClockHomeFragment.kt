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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.yopa.samsprung.R
import com.yopa.samsprung.settings.Preferences

class ClockHomeFragment : Fragment() {

    private var style1Container: View? = null
    private var style2Container: View? = null
    private var style3Container: View? = null
    private var style4Container: View? = null

    private var batteryText1: TextView? = null
    private var batteryText2: TextView? = null
    private var batteryText3: TextView? = null
    private var batteryText4: TextView? = null

    private val prefs: SharedPreferences by lazy {
        requireContext().getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Preferences.prefClockStyle) {
            applyClockStyle()
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                        || status == BatteryManager.BATTERY_STATUS_FULL

                if (level >= 0 && scale > 0) {
                    val batteryPct = (level * 100 / scale.toFloat()).toInt()
                    val prefix = if (isCharging) "⚡ " else ""
                    val text = "$prefix$batteryPct%"
                    batteryText1?.text = text
                    batteryText2?.text = text
                    batteryText3?.text = text
                    batteryText4?.text = "Baterai $text"
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_clock_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        style1Container = view.findViewById(R.id.clock_container_style1)
        style2Container = view.findViewById(R.id.clock_container_style2)
        style3Container = view.findViewById(R.id.clock_container_style3)
        style4Container = view.findViewById(R.id.clock_container_style4)

        batteryText1 = view.findViewById(R.id.home_battery_text)
        batteryText2 = view.findViewById(R.id.home_battery_s2)
        batteryText3 = view.findViewById(R.id.home_battery_s3)
        batteryText4 = view.findViewById(R.id.home_battery_s4)

        applyClockStyle()
    }

    private fun applyClockStyle() {
        val style = prefs.getInt(Preferences.prefClockStyle, Preferences.CLOCK_STYLE_1)
        style1Container?.visibility = if (style == Preferences.CLOCK_STYLE_1) View.VISIBLE else View.GONE
        style2Container?.visibility = if (style == Preferences.CLOCK_STYLE_2) View.VISIBLE else View.GONE
        style3Container?.visibility = if (style == Preferences.CLOCK_STYLE_3) View.VISIBLE else View.GONE
        style4Container?.visibility = if (style == Preferences.CLOCK_STYLE_4) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        try {
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            requireContext().registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
        } catch (_: Exception) {}
        applyClockStyle()
    }

    override fun onPause() {
        super.onPause()
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            requireContext().unregisterReceiver(batteryReceiver)
        } catch (_: Exception) {}
    }
}
