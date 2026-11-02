package com.yopa.samsprung.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object AlarmRepository {
    private const val TAG = "AlarmRepository"

    fun getUpcomingAlarms(context: Context): List<AlarmItem> {
        val alarms = mutableListOf<AlarmItem>()
        val now = System.currentTimeMillis()
        val limit24h = now + (24 * 3600 * 1000L)

        // 1. Fetch from standard Android AlarmManager (Works across all OEMs including Samsung)
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val nextAlarm = am?.nextAlarmClock
        if (nextAlarm != null) {
            val creatorPkg = nextAlarm.showIntent?.creatorPackage
            val isFromClockApp = isClockPackage(context, creatorPkg)

            Log.d(TAG, "Next alarm detected from creatorPackage: $creatorPkg, isClock: $isFromClockApp, trigger: ${nextAlarm.triggerTime}")

            if (isFromClockApp) {
                val trigger = nextAlarm.triggerTime
                val diff = trigger - now
                if (diff in 1..24 * 3600 * 1000L) {
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(trigger))
                    val remainingStr = formatRemainingTime(diff)
                    alarms.add(
                        AlarmItem(
                            id = "next_alarm_$trigger",
                            timeText = timeStr,
                            remainingText = remainingStr,
                            label = "Alarm Berikutnya",
                            triggerTimeMillis = trigger,
                            showIntent = nextAlarm.showIntent,
                            isNextAlarm = true
                        )
                    )
                }
            }
        }

        // 2. Try Samsung Clock ContentProvider to discover additional upcoming alarms
        val samsungUris = listOf(
            Uri.parse("content://com.sec.android.app.clockpackage/alarm"),
            Uri.parse("content://com.sec.android.app.clockpackage/alarms"),
            Uri.parse("content://com.android.alarmclock/alarm")
        )

        for (uri in samsungUris) {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val colHour = cursor.getColumnIndex("hour")
                    val colMin = cursor.getColumnIndex("minutes")
                    val colActive = cursor.getColumnIndex("enabled")
                    val colName = cursor.getColumnIndex("name")
                    val colLabel = if (colName != -1) colName else cursor.getColumnIndex("label")
                    val colDays = cursor.getColumnIndex("daysofweek")

                    while (cursor.moveToNext()) {
                        val active = if (colActive != -1) cursor.getInt(colActive) == 1 else true
                        if (!active) continue

                        val h = if (colHour != -1) cursor.getInt(colHour) else -1
                        val m = if (colMin != -1) cursor.getInt(colMin) else -1
                        if (h in 0..23 && m in 0..59) {
                            val trigger = calculateNextTrigger(h, m)
                            if (trigger in (now + 1)..limit24h) {
                                val diff = trigger - now
                                val timeStr = String.format(Locale.getDefault(), "%02d:%02d", h, m)
                                val label = if (colLabel != -1) cursor.getString(colLabel) ?: "Alarm" else "Alarm"

                                // Check if already added by AlarmManager to avoid duplicates
                                val isDuplicate = alarms.any {
                                    Math.abs(it.triggerTimeMillis - trigger) < 60_000L
                                }

                                if (!isDuplicate) {
                                    alarms.add(
                                        AlarmItem(
                                            id = "sec_alarm_${h}_${m}_$trigger",
                                            timeText = timeStr,
                                            remainingText = formatRemainingTime(diff) + if (label.isNotBlank() && label != "Alarm") " • $label" else "",
                                            label = label,
                                            triggerTimeMillis = trigger,
                                            showIntent = null
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                if (alarms.isNotEmpty()) break
            } catch (e: Exception) {
                Log.d(TAG, "ContentProvider query skipped for $uri: ${e.message}")
            }
        }

        alarms.sortBy { it.triggerTimeMillis }
        return alarms
    }

    private fun calculateNextTrigger(hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
    }

    fun formatRemainingTime(diffMillis: Long): String {
        if (diffMillis <= 0) return "Sekarang"
        val totalMinutes = diffMillis / (60 * 1000)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 && minutes > 0 -> "Dalam ${hours} jam ${minutes} mnt"
            hours > 0 -> "Dalam ${hours} jam"
            else -> "Dalam ${minutes} mnt"
        }
    }

    fun isClockPackage(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        val knownClockPackages = setOf(
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock",
            "com.android.deskclock",
            "droom.sleepIfUCan",
            "com.urbandroid.sleep",
            "com.alarmclock.xtreme.free",
            "com.apalon.alarmclock.smart",
            "com.amdroidalarmclock.amdroid"
        )
        if (knownClockPackages.contains(pkg)) return true
        if (pkg.contains("clock", ignoreCase = true) && !pkg.contains("widget", ignoreCase = true)) {
            return true
        }

        return try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).setPackage(pkg)
            val resolved = context.packageManager.queryIntentActivities(intent, 0)
            resolved.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun openClockApp(context: Context, showIntent: PendingIntent? = null) {
        showIntent?.let {
            try {
                it.send()
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to launch showIntent", e)
            }
        }

        val clockPackages = listOf(
            "com.sec.android.app.clockpackage",
            "com.google.android.deskclock"
        )
        for (pkg in clockPackages) {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(intent)
                    return
                } catch (_: Exception) {}
            }
        }

        try {
            val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
