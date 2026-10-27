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
import android.app.SearchManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ResolveInfo
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yopa.content.ScaledContext
import com.yopa.io.Debug
import com.yopa.pm.PackageRetriever
import com.yopa.samsprung.R
import com.yopa.samsprung.SamSprungOverlay
import com.yopa.samsprung.settings.Preferences
import com.yopa.view.OnSwipeTouchListener
import com.yopa.widget.RecyclerViewTouch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppDrawerFragment : Fragment(), DrawerAppAdapter.OnAppClickListener {

    companion object {
        private var cachedPackages: MutableList<ResolveInfo>? = null
    }

    private val Number.toPx get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(),
        ScaledContext(requireActivity()).cover().resources.displayMetrics
    )

    private val prefs: SharedPreferences by lazy {
        requireActivity().getSharedPreferences(Preferences.prefsValue, Service.MODE_PRIVATE)
    }
    private val launcherManager: LauncherManager by lazy {
        LauncherManager(requireActivity() as SamSprungOverlay)
    }

    private lateinit var launcherView: RecyclerView
    private var packReceiver: BroadcastReceiver? = null
    private var currentLayoutIsGrid: Boolean? = null
    private var currentColumnCount: Int? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(
            R.layout.fragment_drawer, container, false
        ) as ViewGroup
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isGrid = prefs.getBoolean(Preferences.prefLayout, true)
        val cols = getColumnCount()
        currentLayoutIsGrid = isGrid
        currentColumnCount = cols

        launcherView = view.findViewById<RecyclerView>(R.id.appsList).apply {
            setHasFixedSize(true)
            itemAnimator = null // Mencegah kedipan / blink animasi default RecyclerView
            layoutManager = if (isGrid) {
                GridLayoutManager(activity, cols)
            } else {
                LinearLayoutManager(activity).also {
                    addItemDecoration(DividerItemDecoration(activity, DividerItemDecoration.VERTICAL))
                }
            }
            updatePadding(bottom = 6) // Padding bersih tanpa search bar
        }

        val existing = cachedPackages
        if (existing != null && existing.isNotEmpty()) {
            launcherView.adapter = DrawerAppAdapter(
                existing, this@AppDrawerFragment, requireActivity().packageManager, prefs
            )
        } else {
            val adapter = DrawerAppAdapter(
                mutableListOf(), this@AppDrawerFragment, requireActivity().packageManager, prefs
            )
            launcherView.adapter = adapter
            getFilteredPackageList()
        }

        RecyclerViewTouch(launcherView).setSwipeCallback(ItemTouchHelper.DOWN,
            object: RecyclerViewTouch.SwipeCallback {
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (direction == ItemTouchHelper.DOWN) {
                    onSwipeClosed(launcherView)
                }
            }
        })

        launcherView.setOnTouchListener(object : OnSwipeTouchListener(requireActivity()) {
            override fun onSwipeBottom() : Boolean {
                onSwipeClosed(launcherView)
                return false
            }
        })

        packReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                if (intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED) {
                    DrawerAppAdapter.clearCache()
                    getFilteredPackageList()
                } else if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                    if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                        DrawerAppAdapter.clearCache()
                        getFilteredPackageList()
                    }
                }
            }
        }

        IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }.also {
            ContextCompat.registerReceiver(
                requireActivity(), packReceiver, it, ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun getFilteredPackageList() {
        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val packages = PackageRetriever(requireActivity()).getFilteredPackageList()
            cachedPackages = packages
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                val adapter = launcherView.adapter as? DrawerAppAdapter
                if (adapter != null) {
                    adapter.setPackages(packages)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun onSwipeClosed(recyclerView: RecyclerView) {
        if (recyclerView.layoutManager is LinearLayoutManager) {
            val manager = recyclerView.layoutManager as LinearLayoutManager
            if (manager.itemCount == 0 || manager.findFirstCompletelyVisibleItemPosition() == 0) {
                val activity = requireActivity() as SamSprungOverlay
                activity.closeMainDrawer()
            }
        }
        if (recyclerView.layoutManager is GridLayoutManager) {
            val manager = recyclerView.layoutManager as GridLayoutManager
            if (manager.itemCount == 0 || manager.findFirstCompletelyVisibleItemPosition() == 0) {
                val activity = requireActivity() as SamSprungOverlay
                activity.closeMainDrawer()
            }
        }
    }

    private fun getColumnCount(): Int {
        val columnDefault = if (Debug.isOppoDevice) 3 else 6
        val columns = prefs.getInt(Preferences.prefLength, columnDefault)
        return if (columns < columnDefault) {
            columns
        } else {
            (requireActivity().windowManager.currentWindowMetrics
                .bounds.width() / 92.toPx + 0.5).toInt()
            // drawer_apps_icon: width - 48, margin - 4, scale - 1.5
        }
    }

    override fun onAppClicked(resolveInfo: ResolveInfo, position: Int) {
        launcherManager.launchResolveInfo(resolveInfo)
    }

    override fun onResume() {
        super.onResume()
        val isGrid = prefs.getBoolean(Preferences.prefLayout, true)
        val cols = getColumnCount()
        if (currentLayoutIsGrid != isGrid || currentColumnCount != cols) {
            currentLayoutIsGrid = isGrid
            currentColumnCount = cols
            if (isGrid) {
                launcherView.layoutManager = GridLayoutManager(activity, cols)
            } else {
                launcherView.layoutManager = LinearLayoutManager(activity)
                launcherView.addItemDecoration(DividerItemDecoration(activity,
                    DividerItemDecoration.VERTICAL))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { requireActivity().unregisterReceiver(packReceiver) } catch (_: Exception) { }
    }
}
