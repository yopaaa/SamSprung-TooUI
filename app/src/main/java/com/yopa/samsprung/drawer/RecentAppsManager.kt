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

import android.app.ActivityManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import com.yopa.samsprung.settings.Preferences
import org.json.JSONArray
import org.json.JSONObject

data class RecentAppItem(
    val packageName: String,
    val activityName: String?,
    val label: String,
    val icon: Drawable?
)

object RecentAppsManager {
    private const val TAG = "RecentAppsManager"
    private const val PREF_KEY_RECENT_APPS = "pref_recent_apps_json"
    private const val MAX_RECENT_APPS = 15

    interface OnRecentAppsChangeListener {
        fun onRecentAppsChanged()
    }

    private val listeners = mutableListOf<OnRecentAppsChangeListener>()
    private val recentList = mutableListOf<Pair<String, String?>>()
    private var isLoaded = false

    fun addListener(listener: OnRecentAppsChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: OnRecentAppsChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        for (listener in listeners.toList()) {
            try {
                listener.onRecentAppsChanged()
            } catch (e: Exception) {
                Log.e(TAG, "Error in listener", e)
            }
        }
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (isLoaded) return
        val prefs = context.getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(PREF_KEY_RECENT_APPS, null)
        recentList.clear()

        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val pkg = obj.getString("pkg")
                    val act = if (obj.has("act") && !obj.isNull("act")) obj.getString("act") else null
                    if (isPackageValid(context, pkg)) {
                        recentList.add(Pair(pkg, act))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse saved recent apps", e)
            }
        }

        // Jika riwayat kosong, ambil dari UsageStatsManager jika tersedia
        if (recentList.isEmpty()) {
            try {
                val endTime = System.currentTimeMillis()
                val startTime = endTime - (24 * 60 * 60 * 1000)
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                val usageEvents = usm?.queryEvents(startTime, endTime)
                val foundPkgs = linkedSetOf<String>()
                if (usageEvents != null) {
                    val event = UsageEvents.Event()
                    while (usageEvents.hasNextEvent()) {
                        usageEvents.getNextEvent(event)
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                            val pkg = event.packageName
                            if (isPackageValid(context, pkg)) {
                                foundPkgs.add(pkg)
                            }
                        }
                    }
                }
                val reversed = foundPkgs.toList().reversed().take(8)
                for (pkg in reversed) {
                    recentList.add(Pair(pkg, null))
                }
            } catch (_: Exception) {}
        }

        isLoaded = true
    }

    private fun isPackageValid(context: Context, packageName: String): Boolean {
        if (packageName.isBlank() || packageName == context.packageName) return false
        val isLauncher = packageName.contains("launcher") || packageName.contains("home")
        if (isLauncher && packageName.contains("samsung")) return false
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    @Synchronized
    private fun save(context: Context) {
        val prefs = context.getSharedPreferences(Preferences.prefsValue, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (item in recentList) {
            val obj = JSONObject().apply {
                put("pkg", item.first)
                put("act", item.second ?: JSONObject.NULL)
            }
            array.put(obj)
        }
        prefs.edit().putString(PREF_KEY_RECENT_APPS, array.toString()).apply()
    }

    @Synchronized
    fun addRecentApp(context: Context, packageName: String, activityName: String? = null) {
        if (!isPackageValid(context, packageName)) return
        ensureLoaded(context)

        recentList.removeAll { it.first == packageName }
        recentList.add(0, Pair(packageName, activityName))

        if (recentList.size > MAX_RECENT_APPS) {
            recentList.subList(MAX_RECENT_APPS, recentList.size).clear()
        }

        save(context)
        notifyChanged()
    }

    @Synchronized
    fun getRecentAppItems(context: Context): List<RecentAppItem> {
        ensureLoaded(context)
        val pm = context.packageManager
        val result = mutableListOf<RecentAppItem>()

        for (item in recentList) {
            try {
                val appInfo = pm.getApplicationInfo(item.first, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                result.add(RecentAppItem(item.first, item.second, label, icon))
            } catch (_: Exception) {}
        }
        return result
    }

    @Synchronized
    fun closeApp(context: Context, packageName: String) {
        ensureLoaded(context)
        recentList.removeAll { it.first == packageName }
        save(context)

        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.killBackgroundProcesses(packageName)
            Log.d(TAG, "killBackgroundProcesses called for $packageName")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to kill background process for $packageName", e)
        }

        notifyChanged()
    }

    @Synchronized
    fun closeAll(context: Context) {
        ensureLoaded(context)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        for (item in recentList) {
            try {
                am?.killBackgroundProcesses(item.first)
            } catch (_: Exception) {}
        }
        recentList.clear()
        save(context)
        notifyChanged()
    }
}
