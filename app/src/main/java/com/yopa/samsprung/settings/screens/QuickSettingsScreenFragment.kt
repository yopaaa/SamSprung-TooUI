package com.yopa.samsprung.settings.screens

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yopa.samsprung.R
import com.yopa.samsprung.drawer.QuickTileRegistry
import com.yopa.samsprung.settings.Preferences

class QuickSettingsScreenFragment : Fragment() {

    private lateinit var adapter: QuickSettingsReorderAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.screen_quick_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireActivity().getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)
        val savedOrder = prefs.getString(Preferences.prefQuickTilesOrder, null)
        val orderedTiles = QuickTileRegistry.getOrderedTiles(savedOrder).toMutableList()

        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_quick_settings_reorder)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = QuickSettingsReorderAdapter(orderedTiles, prefs) { viewHolder ->
            itemTouchHelper.startDrag(viewHolder)
        }
        recyclerView.adapter = adapter

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
                if (from != RecyclerView.NO_POSITION && to != RecyclerView.NO_POSITION) {
                    adapter.onItemMove(from, to)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Do not support swipe to dismiss
            }

            override fun isLongPressDragEnabled(): Boolean = false
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
}
