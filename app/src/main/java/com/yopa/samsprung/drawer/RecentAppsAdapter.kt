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

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.yopa.samsprung.R

class RecentAppsAdapter(
    private val listener: OnRecentAppInteractionListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_APP = 1
        const val VIEW_TYPE_CLOSE_ALL = 2
    }

    interface OnRecentAppInteractionListener {
        fun onAppClick(item: RecentAppItem)
        fun onCloseAppClick(item: RecentAppItem, position: Int)
        fun onCloseAllClick()
    }

    private val items = mutableListOf<RecentAppItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<RecentAppItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun removeItem(position: Int) {
        if (position in 0 until items.size) {
            items.removeAt(position)
            notifyItemRemoved(position)
            if (items.isEmpty()) {
                notifyDataSetChanged()
            }
        }
    }

    fun getItems(): List<RecentAppItem> = items.toList()

    override fun getItemCount(): Int {
        return if (items.isNotEmpty()) items.size + 1 else 0
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < items.size) VIEW_TYPE_APP else VIEW_TYPE_CLOSE_ALL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_APP) {
            val view = inflater.inflate(R.layout.item_recent_app, parent, false)
            AppViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_recent_close_all, parent, false)
            CloseAllViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AppViewHolder && position < items.size) {
            val item = items[position]
            holder.ivIcon.setImageDrawable(item.icon)
            holder.tvLabel.text = item.label

            holder.itemView.setOnClickListener {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos < items.size) {
                    listener.onAppClick(items[currentPos])
                }
            }

            holder.btnClose.setOnClickListener {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION && currentPos < items.size) {
                    listener.onCloseAppClick(items[currentPos], currentPos)
                }
            }
        } else if (holder is CloseAllViewHolder) {
            holder.btnCloseAll.setOnClickListener {
                listener.onCloseAllClick()
            }
        }
    }

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivIcon: ImageView = itemView.findViewById(R.id.iv_recent_icon)
        val tvLabel: TextView = itemView.findViewById(R.id.tv_recent_label)
        val btnClose: View = itemView.findViewById(R.id.btn_close_app)
    }

    class CloseAllViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val btnCloseAll: View = itemView.findViewById(R.id.btn_close_all)
    }
}
