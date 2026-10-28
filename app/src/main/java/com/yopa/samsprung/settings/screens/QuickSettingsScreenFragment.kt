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

class QuickSettingsScreenFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.screen_quick_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)

        fun setupToggle(cardId: Int, switchId: Int, prefKey: String) {
            val card = view.findViewById<View>(cardId)
            val switchView = view.findViewById<SwitchCompat>(switchId)

            switchView.isChecked = prefs.getBoolean(prefKey, true)

            card.setOnClickListener {
                val newState = !switchView.isChecked
                switchView.isChecked = newState
                prefs.edit().putBoolean(prefKey, newState).apply()
            }
        }

        setupToggle(R.id.card_quick_wifi, R.id.switch_quick_wifi, Preferences.prefQuickWifi)
        setupToggle(R.id.card_quick_bluetooth, R.id.switch_quick_bluetooth, Preferences.prefQuickBluetooth)
        setupToggle(R.id.card_quick_sound, R.id.switch_quick_sound, Preferences.prefQuickSound)
        setupToggle(R.id.card_quick_rotation, R.id.switch_quick_rotation, Preferences.prefQuickRotation)
        setupToggle(R.id.card_quick_torch, R.id.switch_quick_torch, Preferences.prefQuickTorch)
        setupToggle(R.id.card_quick_dnd, R.id.switch_quick_dnd, Preferences.prefQuickDnd)
        setupToggle(R.id.card_quick_nfc, R.id.switch_quick_nfc, Preferences.prefQuickNfc)
        setupToggle(R.id.card_quick_auth, R.id.switch_quick_auth, Preferences.prefQuickAuth)
    }
}
