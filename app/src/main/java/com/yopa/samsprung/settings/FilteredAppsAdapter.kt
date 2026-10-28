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

package com.yopa.samsprung.settings

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.yopa.samsprung.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.fastscroll.PopupTextProvider
import java.util.Collections

class FilteredAppsAdapter(
    private val pacMan: PackageManager,
    private var packages: MutableList<ResolveInfo>,
    private var pinnedPackages: MutableList<String>,
    private var hide: HashSet<String>,
    private val prefs: SharedPreferences,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<FilteredAppsAdapter.HideViewHolder>(), PopupTextProvider {

    fun setPackages(
        packages: MutableList<ResolveInfo>,
        pinnedPackages: MutableList<String>,
        hide: HashSet<String>
    ) {
        this.packages = packages
        this.pinnedPackages = pinnedPackages
        this.hide = hide
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return packages.size
    }

    override fun getItemId(i: Int): Long {
        if (i < 0 || i >= packages.size) return RecyclerView.NO_ID
        val info = packages[i]
        return "${info.activityInfo.packageName}/${info.activityInfo.name}".hashCode().toLong()
    }

    private fun getItem(i: Int): ResolveInfo {
        return packages[i]
    }

    override fun getItemViewType(position: Int): Int {
        return 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HideViewHolder {
        return SimpleViewHolder(parent, pacMan, prefs, hide, pinnedPackages, onStartDrag, ::togglePin)
    }

    override fun getPopupText(view: View, position: Int): CharSequence {
        if (position >= packages.size) return "?"
        val item = packages[position]
        if (pinnedPackages.contains(item.activityInfo.packageName)) {
            return "📌"
        }
        val label: CharSequence? = try {
            item.loadLabel(pacMan)
        } catch (_: Exception) {
            try {
                item.nonLocalizedLabel
            } catch (_: Exception) {
                null
            }
        }
        return if (label.isNullOrEmpty()) "?" else label[0].uppercase()
    }

    override fun onBindViewHolder(holder: HideViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun onItemMove(from: Int, to: Int): Boolean {
        if (from < 0 || to < 0 || from >= pinnedPackages.size || to >= pinnedPackages.size) {
            return false
        }
        Collections.swap(packages, from, to)
        Collections.swap(pinnedPackages, from, to)
        notifyItemMoved(from, to)
        prefs.edit().putString(Preferences.prefPinnedApps, pinnedPackages.joinToString(",")).apply()
        return true
    }

    fun togglePin(position: Int) {
        if (position < 0 || position >= packages.size) return
        val item = packages[position]
        val pkg = item.activityInfo.packageName
        if (pinnedPackages.contains(pkg)) {
            pinnedPackages.remove(pkg)
        } else {
            pinnedPackages.add(pkg)
        }
        prefs.edit().putString(Preferences.prefPinnedApps, pinnedPackages.joinToString(",")).apply()
        val sortOrder = prefs.getString(Preferences.prefAppSortOrder, Preferences.SORT_ASC) ?: Preferences.SORT_ASC
        sortAndRebuildList(sortOrder)
    }

    fun sortAndRebuildList(sortOrder: String) {
        val pinnedMap = packages.associateBy { it.activityInfo.packageName }
        val pinnedResult = mutableListOf<ResolveInfo>()
        for (pkg in pinnedPackages) {
            pinnedMap[pkg]?.let { pinnedResult.add(it) }
        }

        val unpinned = packages.filter { !pinnedPackages.contains(it.activityInfo.packageName) }.toMutableList()
        val comparator = ResolveInfo.DisplayNameComparator(pacMan)
        if (sortOrder == Preferences.SORT_DESC) {
            Collections.sort(unpinned, Collections.reverseOrder(comparator))
        } else {
            Collections.sort(unpinned, comparator)
        }

        packages.clear()
        packages.addAll(pinnedResult)
        packages.addAll(unpinned)
        notifyDataSetChanged()
    }

    abstract class HideViewHolder(
        itemView: View,
        private val packageManager: PackageManager,
        private val prefs: SharedPreferences,
        private val hide: HashSet<String>,
        private val pinnedPackages: MutableList<String>,
        private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
        private val onTogglePin: (Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private lateinit var application: ResolveInfo

        @SuppressLint("ClickableViewAccessibility")
        fun bind(appInfo: ResolveInfo) {
            this.application = appInfo
            val detailView = itemView.findViewById<LinearLayout>(R.id.hiddenItemContainer)
            val btnPin = itemView.findViewById<AppCompatImageView>(R.id.btnPinItem)
            val btnDrag = itemView.findViewById<AppCompatImageView>(R.id.btnDragHandle)
            val hideSwitch = itemView.findViewById<SwitchCompat>(R.id.hiddenItemSwitch)
            val pkg = application.activityInfo.packageName

            CoroutineScope(Dispatchers.IO).launch {
                val label: CharSequence? = try {
                    application.loadLabel(packageManager)
                } catch (_: Exception) {
                    try {
                        application.nonLocalizedLabel
                    } catch (_: Exception) {
                        null
                    }
                }
                val appName = if (label.isNullOrEmpty()) "?" else label
                val icon = application.loadIcon(packageManager)

                withContext(Dispatchers.Main) {
                    if (this@HideViewHolder.application == appInfo) {
                        detailView.findViewById<AppCompatImageView>(R.id.hiddenItemImage).setImageDrawable(icon)
                        detailView.findViewById<TextView>(R.id.hiddenItemText).text = appName
                    }
                }
            }

            val isPinned = pinnedPackages.contains(pkg)
            if (isPinned) {
                btnPin.setImageResource(R.drawable.ic_pin_24dp)
                btnPin.setColorFilter(ContextCompat.getColor(itemView.context, R.color.primary_dark))
                btnDrag.visibility = View.VISIBLE
                btnDrag.setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                        onStartDrag(this)
                    }
                    false
                }
            } else {
                btnPin.setImageResource(R.drawable.ic_pin_outline_24dp)
                btnPin.setColorFilter(Color.parseColor("#8AFFFFFF"))
                btnDrag.visibility = View.GONE
                btnDrag.setOnTouchListener(null)
            }

            btnPin.setOnClickListener {
                onTogglePin(bindingAdapterPosition)
            }

            hideSwitch.isChecked = !hide.contains(pkg)
            hideSwitch.setOnClickListener {
                if (hide.contains(pkg)) {
                    hide.remove(pkg)
                } else {
                    hide.add(pkg)
                }
                prefs.edit().putStringSet(Preferences.prefHidden, hide).apply()
            }
        }
    }

    internal class SimpleViewHolder(
        parent: ViewGroup,
        packageManager: PackageManager,
        prefs: SharedPreferences,
        hide: HashSet<String>,
        pinnedPackages: MutableList<String>,
        onStartDrag: (RecyclerView.ViewHolder) -> Unit,
        onTogglePin: (Int) -> Unit
    ) : HideViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.filtered_apps_item, parent, false
        ), packageManager, prefs, hide, pinnedPackages, onStartDrag, onTogglePin
    )
}
