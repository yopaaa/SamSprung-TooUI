package com.yopa.samsprung.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.service.notification.StatusBarNotification
import android.util.Log

object AlarmAlertManager {
    private const val TAG = "AlarmAlertManager"

    data class FiringAlarm(
        val key: String,
        val packageName: String,
        val timeText: String,
        val label: String,
        val dismissPendingIntent: PendingIntent?,
        val snoozePendingIntent: PendingIntent?,
        val fullScreenIntent: PendingIntent?
    )

    interface AlarmAlertListener {
        fun onAlarmFiring(alarm: FiringAlarm)
        fun onAlarmDismissed(alarmKey: String)
    }

    private var activeAlarm: FiringAlarm? = null
    private var listener: AlarmAlertListener? = null

    fun setListener(listener: AlarmAlertListener?) {
        this.listener = listener
        activeAlarm?.let {
            listener?.onAlarmFiring(it)
        }
    }

    fun isAlarmActive(): Boolean = activeAlarm != null

    fun getActiveAlarm(): FiringAlarm? = activeAlarm

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val notif = sbn.notification ?: return
        val pkg = sbn.packageName ?: ""

        val isAlarmCategory = notif.category == Notification.CATEGORY_ALARM
        val isInsistent = (notif.flags and Notification.FLAG_INSISTENT) != 0
        val isClockApp = pkg == "com.sec.android.app.clockpackage" ||
                pkg == "com.google.android.deskclock" ||
                pkg.contains("clock", ignoreCase = true)

        // Only consider it an active ringing alarm if it's ongoing and categorized as alarm / insistent / fullScreen
        val isRingingAlarm = (isAlarmCategory || (isClockApp && isInsistent) || (isClockApp && notif.fullScreenIntent != null)) && sbn.isOngoing

        if (!isRingingAlarm) return

        Log.d(TAG, "Active ringing alarm detected from $pkg: ${sbn.key}")

        val title = notif.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "Alarm"
        val text = notif.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        var dismissPi: PendingIntent? = null
        var snoozePi: PendingIntent? = null

        notif.actions?.forEach { action ->
            val actionTitle = action.title?.toString()?.lowercase() ?: ""
            if (actionTitle.contains("dismiss") ||
                actionTitle.contains("matikan") ||
                actionTitle.contains("stop") ||
                actionTitle.contains("batal") ||
                actionTitle.contains("off") ||
                actionTitle.contains("tutup")
            ) {
                dismissPi = action.actionIntent
            } else if (actionTitle.contains("snooze") || actionTitle.contains("tunda")) {
                snoozePi = action.actionIntent
            }
        }

        // If not matched by keywords, fall back by order
        if (dismissPi == null && !notif.actions.isNullOrEmpty()) {
            dismissPi = notif.actions[0].actionIntent
            if (notif.actions.size > 1 && snoozePi == null) {
                snoozePi = notif.actions[1].actionIntent
            }
        }

        val firing = FiringAlarm(
            key = sbn.key,
            packageName = pkg,
            timeText = text.ifBlank { title },
            label = if (text.isNotBlank() && title != text) title else "Alarm",
            dismissPendingIntent = dismissPi,
            snoozePendingIntent = snoozePi,
            fullScreenIntent = notif.fullScreenIntent
        )

        activeAlarm = firing
        listener?.onAlarmFiring(firing)
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        val current = activeAlarm ?: return
        if (current.key == sbn.key) {
            Log.d(TAG, "Active alarm notification removed: ${sbn.key}")
            activeAlarm = null
            listener?.onAlarmDismissed(sbn.key)
        }
    }

    fun dismissAlarm(context: Context): Boolean {
        val current = activeAlarm ?: return false
        Log.d(TAG, "Dismissing active alarm: ${current.key}")

        var success = false
        current.dismissPendingIntent?.let {
            try {
                it.send()
                success = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send dismiss PendingIntent", e)
            }
        }

        // Additional broadcasts as safety fallback for Samsung and standard Android
        try {
            context.sendBroadcast(Intent("com.samsung.sec.android.clockpackage.alarm.ALARM_STOP"))
            context.sendBroadcast(Intent(AlarmClock.ACTION_DISMISS_ALARM))
        } catch (_: Exception) {}

        activeAlarm = null
        listener?.onAlarmDismissed(current.key)
        return success
    }

    fun snoozeAlarm(context: Context): Boolean {
        val current = activeAlarm ?: return false
        Log.d(TAG, "Snoozing active alarm: ${current.key}")

        var success = false
        current.snoozePendingIntent?.let {
            try {
                it.send()
                success = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send snooze PendingIntent", e)
            }
        }

        // Fallback broadcast
        try {
            context.sendBroadcast(Intent(AlarmClock.ACTION_SNOOZE_ALARM))
        } catch (_: Exception) {}

        activeAlarm = null
        listener?.onAlarmDismissed(current.key)
        return success
    }
}
