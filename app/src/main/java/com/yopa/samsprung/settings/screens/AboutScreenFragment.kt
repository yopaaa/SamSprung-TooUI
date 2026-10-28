package com.yopa.samsprung.settings.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.yopa.samsprung.BuildConfig
import com.yopa.samsprung.R
import com.yopa.samsprung.settings.CoverPreferences

class AboutScreenFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.screen_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefActivity = activity as? CoverPreferences

        val textVersion = view.findViewById<TextView>(R.id.text_app_version)
        val textCommit = view.findViewById<TextView>(R.id.text_build_commit)

        textVersion.text = "Versi ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.BUILD_TYPE})"
        textCommit.text = "Git Commit: ${BuildConfig.COMMIT}"

        view.findViewById<MaterialButton>(R.id.btn_save_logcat).setOnClickListener {
            prefActivity?.captureLogcat()
        }

        view.findViewById<MaterialButton>(R.id.btn_donate).setOnClickListener {
            prefActivity?.openDonation()
        }
    }
}
