package com.yopa.samsprung.settings.screens

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.yopa.samsprung.R
import com.yopa.samsprung.drawer.QuickTileData
import com.yopa.samsprung.settings.Preferences
import java.util.Collections

class QuickSettingsReorderAdapter(
    private val tiles: MutableList<QuickTileData>,
    private val prefs: SharedPreferences,
    private val dragStartListener: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<QuickSettingsReorderAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: View = view.findViewById(R.id.card_tile_item)
        val btnDrag: AppCompatImageView = view.findViewById(R.id.btn_drag_handle)
        val iconTile: AppCompatImageView = view.findViewById(R.id.icon_tile)
        val textTitle: TextView = view.findViewById(R.id.text_tile_title)
        val textDesc: TextView = view.findViewById(R.id.text_tile_desc)
        val switchEnabled: SwitchCompat = view.findViewById(R.id.switch_tile_enabled)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_quick_settings_reorder, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tile = tiles[position]

        holder.textTitle.text = tile.title
        holder.textDesc.text = tile.description
        holder.iconTile.setImageResource(tile.iconRes)

        holder.switchEnabled.setOnCheckedChangeListener(null)
        val isEnabled = prefs.getBoolean(tile.prefKey, true)
        holder.switchEnabled.isChecked = isEnabled

        holder.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(tile.prefKey, isChecked).apply()
        }

        holder.card.setOnClickListener {
            holder.switchEnabled.toggle()
        }

        holder.btnDrag.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                dragStartListener(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = tiles.size

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(tiles, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(tiles, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        saveCurrentOrder()
    }

    private fun saveCurrentOrder() {
        val orderString = tiles.joinToString(",") { it.id }
        prefs.edit().putString(Preferences.prefQuickTilesOrder, orderString).apply()
    }
}
