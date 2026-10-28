package com.yopa.samsprung.settings.screens

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.yopa.pm.PackageRetriever
import com.yopa.samsprung.R
import com.yopa.samsprung.settings.FilteredAppsAdapter
import com.yopa.samsprung.settings.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class AppsManagerScreenFragment : Fragment() {

    private lateinit var itemTouchHelper: ItemTouchHelper
    private var adapter: FilteredAppsAdapter? = null

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

        // 3. App Launch Orientation (Lock Portrait vs Auto)
        val textOrientationMode = view.findViewById<TextView>(R.id.text_orientation_mode)
        val btnToggleOrientation = view.findViewById<MaterialButton>(R.id.btn_toggle_orientation_mode)

        fun updateOrientationUI(mode: String) {
            if (mode == Preferences.ORIENTATION_MODE_PORTRAIT) {
                textOrientationMode.text = "Kunci Portrait (Tegak)"
                btnToggleOrientation.text = "Ubah ke Otomatis"
            } else {
                textOrientationMode.text = "Otomatis (Mengikuti Sensor)"
                btnToggleOrientation.text = "Kunci Portrait"
            }
        }

        var orientationMode = prefs.getString(Preferences.prefAppOrientationMode, Preferences.ORIENTATION_MODE_PORTRAIT) ?: Preferences.ORIENTATION_MODE_PORTRAIT
        updateOrientationUI(orientationMode)

        fun toggleOrientation() {
            orientationMode = if (orientationMode == Preferences.ORIENTATION_MODE_PORTRAIT) {
                Preferences.ORIENTATION_MODE_AUTO
            } else {
                Preferences.ORIENTATION_MODE_PORTRAIT
            }
            prefs.edit().putString(Preferences.prefAppOrientationMode, orientationMode).apply()
            updateOrientationUI(orientationMode)
            val msg = if (orientationMode == Preferences.ORIENTATION_MODE_PORTRAIT) {
                "Aplikasi akan selalu dibuka dalam posisi Portrait (Tegak)"
            } else {
                "Orientasi aplikasi mengikuti sensor gravitasi"
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }

        btnToggleOrientation.setOnClickListener { toggleOrientation() }

        // 4. Sort Order Button (A-Z vs Z-A)
        val btnSortOrder = view.findViewById<MaterialButton>(R.id.btn_sort_order)
        var sortOrder = prefs.getString(Preferences.prefAppSortOrder, Preferences.SORT_ASC) ?: Preferences.SORT_ASC

        fun updateSortOrderUI(order: String) {
            btnSortOrder.text = if (order == Preferences.SORT_DESC) "Urutan: Z-A" else "Urutan: A-Z"
        }
        updateSortOrderUI(sortOrder)

        btnSortOrder.setOnClickListener {
            sortOrder = if (sortOrder == Preferences.SORT_ASC) Preferences.SORT_DESC else Preferences.SORT_ASC
            prefs.edit().putString(Preferences.prefAppSortOrder, sortOrder).apply()
            updateSortOrderUI(sortOrder)
            adapter?.sortAndRebuildList(sortOrder)
            val label = if (sortOrder == Preferences.SORT_DESC) "Z-A (Descending)" else "A-Z (Ascending)"
            Toast.makeText(requireContext(), "Urutan aplikasi diubah: $label", Toast.LENGTH_SHORT).show()
        }

        // 5. App Visibility & Pinned Apps RecyclerView
        val rv = view.findViewById<RecyclerView>(R.id.app_visibility_list)
        rv.layoutManager = LinearLayoutManager(context)
        rv.addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))

        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                return adapter?.onItemMove(from, to) ?: false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(rv)

        val retriever = PackageRetriever(requireContext())
        CoroutineScope(Dispatchers.IO).launch {
            val allPackages = retriever.getPackageList()
            val hidden = retriever.getHiddenPackages()
            val pinnedList = retriever.getPinnedPackages()

            val pinnedMap = allPackages.associateBy { it.activityInfo.packageName }
            val pinnedResult = mutableListOf<android.content.pm.ResolveInfo>()
            for (pkg in pinnedList) {
                pinnedMap[pkg]?.let { pinnedResult.add(it) }
            }

            val unpinned = allPackages.filter { !pinnedList.contains(it.activityInfo.packageName) }.toMutableList()
            val comparator = android.content.pm.ResolveInfo.DisplayNameComparator(requireContext().packageManager)
            if (sortOrder == Preferences.SORT_DESC) {
                Collections.sort(unpinned, Collections.reverseOrder(comparator))
            } else {
                Collections.sort(unpinned, comparator)
            }

            val fullList = (pinnedResult + unpinned).toMutableList()

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                adapter = FilteredAppsAdapter(
                    requireContext().packageManager,
                    fullList,
                    pinnedList,
                    hidden,
                    prefs
                ) { holder ->
                    itemTouchHelper.startDrag(holder)
                }
                rv.adapter = adapter
            }
        }
    }
}
