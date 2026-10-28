package com.yopa.samsprung.settings.screens

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yopa.pm.PackageRetriever
import com.yopa.samsprung.R
import com.yopa.samsprung.settings.CoverPreferences
import com.yopa.samsprung.settings.FilteredAppsAdapter
import com.yopa.samsprung.settings.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppsManagerScreenFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.screen_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)

        // 1. Layout Mode (Grid vs List)
        val textLayoutMode = view.findViewById<TextView>(R.id.text_layout_mode)
        val btnToggle = view.findViewById<MaterialButton>(R.id.btn_toggle_layout_mode)
        val containerColumns = view.findViewById<LinearLayout>(R.id.container_grid_columns)

        fun updateLayoutModeUI(isGrid: Boolean) {
            if (isGrid) {
                textLayoutMode.text = "Tampilan Kisi (Grid Icons)"
                btnToggle.text = "Ubah ke List"
                containerColumns.visibility = View.VISIBLE
            } else {
                textLayoutMode.text = "Tampilan Daftar (List Cards)"
                btnToggle.text = "Ubah ke Grid"
                containerColumns.visibility = View.GONE
            }
        }

        var isGrid = prefs.getBoolean(Preferences.prefLayout, true)
        updateLayoutModeUI(isGrid)

        btnToggle.setOnClickListener {
            isGrid = !isGrid
            prefs.edit().putBoolean(Preferences.prefLayout, isGrid).apply()
            updateLayoutModeUI(isGrid)
        }

        // 2. Columns SeekBar
        val textColumnsVal = view.findViewById<TextView>(R.id.text_columns_val)
        val seekbarColumns = view.findViewById<SeekBar>(R.id.seekbar_columns)
        val currentCols = prefs.getInt(Preferences.prefLength, 6)
        seekbarColumns.progress = currentCols
        textColumnsVal.text = "$currentCols Kolom"

        seekbarColumns.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val cols = if (progress < 1) 1 else progress
                textColumnsVal.text = "$cols Kolom"
                if (fromUser) {
                    prefs.edit().putInt(Preferences.prefLength, cols).apply()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 3. App Visibility RecyclerView
        val rv = view.findViewById<RecyclerView>(R.id.app_visibility_list)
        rv.layoutManager = LinearLayoutManager(context)
        rv.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        val retriever = PackageRetriever(requireContext())
        CoroutineScope(Dispatchers.IO).launch {
            val packages = retriever.getPackageList()
            val hidden = retriever.getHiddenPackages()
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                rv.adapter = FilteredAppsAdapter(
                    requireContext().packageManager,
                    packages,
                    hidden,
                    prefs
                )
            }
        }
    }
}
