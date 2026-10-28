package com.yopa.samsprung.drawer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.yopa.samsprung.R

data class TileUIState(
    val isActive: Boolean,
    val iconRes: Int,
    val label: String
)

class CoverQuickTilesAdapter(
    private var tiles: List<QuickTileData>,
    private val stateProvider: (String) -> TileUIState,
    private val onTileClick: (QuickTileData) -> Unit
) : RecyclerView.Adapter<CoverQuickTilesAdapter.ViewHolder>() {

    private val activeBg = R.drawable.quick_tile_bg_active
    private val inactiveBg = R.drawable.quick_tile_bg
    private var recyclerView: RecyclerView? = null
    private var lastMeasuredWidth: Int = 0

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.tile_container)
        val icon: AppCompatImageView = view.findViewById(R.id.icon_tile)
        val text: TextView = view.findViewById(R.id.text_tile)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
        recyclerView.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val w = right - left
            if (w > 0 && w != lastMeasuredWidth) {
                lastMeasuredWidth = w
                recyclerView.post { notifyDataSetChanged() }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cover_quick_tile, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tile = tiles[position]
        val uiState = stateProvider(tile.id)

        holder.icon.setImageResource(uiState.iconRes)
        holder.text.text = uiState.label
        holder.container.setBackgroundResource(if (uiState.isActive) activeBg else inactiveBg)

        // Set dynamic width so tiles fill screen proportionally
        recyclerView?.let { rv ->
            val parentWidth = if (rv.measuredWidth > 0) rv.measuredWidth else lastMeasuredWidth
            if (parentWidth > 0) {
                val totalTiles = tiles.size
                val columnCount = when {
                    totalTiles <= 2 -> 2
                    totalTiles <= 4 -> 2
                    totalTiles <= 6 -> 3
                    else -> 4
                }
                val availableWidth = parentWidth - rv.paddingLeft - rv.paddingRight
                val marginLp = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
                val hMargin = (marginLp?.leftMargin ?: 0) + (marginLp?.rightMargin ?: 0)
                val computedWidth = (availableWidth / columnCount) - hMargin

                if (computedWidth > 0 && holder.itemView.layoutParams.width != computedWidth) {
                    val lp = holder.itemView.layoutParams
                    lp.width = computedWidth
                    holder.itemView.layoutParams = lp
                }
            }
        }

        holder.container.setOnClickListener {
            onTileClick(tile)
        }
    }

    override fun getItemCount(): Int = tiles.size

    fun updateTiles(newTiles: List<QuickTileData>) {
        tiles = newTiles
        notifyDataSetChanged()
    }
}
