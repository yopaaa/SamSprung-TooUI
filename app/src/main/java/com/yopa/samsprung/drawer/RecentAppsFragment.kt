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

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yopa.samsprung.R
import com.yopa.samsprung.SamSprungOverlay
import com.yopa.samsprung.settings.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RecentAppsFragment : Fragment(),
    RecentAppsAdapter.OnRecentAppInteractionListener,
    RecentAppsManager.OnRecentAppsChangeListener {

    private var layoutEmpty: View? = null
    private var recyclerView: RecyclerView? = null
    private lateinit var adapter: RecentAppsAdapter

    private val vibrator: Vibrator? by lazy {
        context?.let { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }
    }
    private val effectClick = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recent_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutEmpty = view.findViewById(R.id.layout_empty_state)
        recyclerView = view.findViewById(R.id.rv_recent_apps)

        adapter = RecentAppsAdapter(this)

        val gridLayoutManager = GridLayoutManager(requireContext(), 3).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (adapter.getItemViewType(position) == RecentAppsAdapter.VIEW_TYPE_CLOSE_ALL) {
                        3
                    } else {
                        1
                    }
                }
            }
        }

        recyclerView?.apply {
            setHasFixedSize(true)
            itemAnimator = null
            layoutManager = gridLayoutManager
            adapter = this@RecentAppsFragment.adapter
        }

        RecentAppsManager.addListener(this)
        loadRecentApps()
    }

    override fun onResume() {
        super.onResume()
        loadRecentApps()
    }

    private fun loadRecentApps() {
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val items = RecentAppsManager.getRecentAppItems(ctx)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                updateUI(items)
            }
        }
    }

    private fun updateUI(items: List<RecentAppItem>) {
        if (items.isEmpty()) {
            layoutEmpty?.visibility = View.VISIBLE
            recyclerView?.visibility = View.GONE
        } else {
            layoutEmpty?.visibility = View.GONE
            recyclerView?.visibility = View.VISIBLE
            adapter.submitList(items)
        }
    }

    private fun vibrateClick() {
        try {
            val ctx = context ?: return
            val prefs = ctx.getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)
            if (prefs.getBoolean(Preferences.prefReacts, true)) {
                vibrator?.vibrate(effectClick)
            }
        } catch (_: Exception) {}
    }

    override fun onAppClick(item: RecentAppItem) {
        vibrateClick()
        val overlay = activity as? SamSprungOverlay ?: return
        LauncherManager(overlay).launchPackage(item.packageName)
    }

    override fun onCloseAppClick(item: RecentAppItem, position: Int) {
        vibrateClick()
        val ctx = context ?: return
        RecentAppsManager.closeApp(ctx, item.packageName)
    }

    override fun onCloseAllClick() {
        vibrateClick()
        val ctx = context ?: return
        RecentAppsManager.closeAll(ctx)
    }

    override fun onRecentAppsChanged() {
        if (isAdded) {
            loadRecentApps()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        RecentAppsManager.removeListener(this)
        layoutEmpty = null
        recyclerView = null
    }
}
