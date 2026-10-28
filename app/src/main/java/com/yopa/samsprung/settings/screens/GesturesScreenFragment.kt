package com.yopa.samsprung.settings.screens

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.yopa.samsprung.R
import com.yopa.samsprung.settings.Preferences

class GesturesScreenFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.screen_gestures, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)

        // 1. Edge Gestures Switch
        val switchEdge = view.findViewById<SwitchCompat>(R.id.switch_edge_gestures)
        switchEdge.isChecked = prefs.getBoolean(Preferences.prefSlider, true)
        switchEdge.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Preferences.prefSlider, isChecked).apply()
        }
        view.findViewById<View>(R.id.card_edge_back).setOnClickListener {
            switchEdge.isChecked = !switchEdge.isChecked
        }

        // 2. Haptic Feedback Switch
        val switchHaptic = view.findViewById<SwitchCompat>(R.id.switch_haptic)
        switchHaptic.isChecked = prefs.getBoolean(Preferences.prefReacts, true)
        switchHaptic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(Preferences.prefReacts, isChecked).apply()
        }
        view.findViewById<View>(R.id.card_haptic).setOnClickListener {
            switchHaptic.isChecked = !switchHaptic.isChecked
        }
    }
}
