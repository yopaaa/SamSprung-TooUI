package com.yopa.samsprung.alarm

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.Log
import com.yopa.app.CoverOptions
import com.yopa.content.ScaledContext
import com.yopa.samsprung.NotificationReceiver
import com.yopa.samsprung.SamSprungOverlay
import com.yopa.samsprung.drawer.CoverScreenTimeoutManager

object AlarmAlertManager {
    private const val TAG = "AlarmAlertManager"

    private var activeAlarmKey: String? = null
    @Volatile
    private var isAlarmFiring: Boolean = false

    fun isAlarmRinging(): Boolean {
        if (isAlarmFiring || activeAlarmKey != null) return true
        try {
            val receiver = NotificationReceiver.getReceiver() ?: return false
            val notifs = receiver.activeNotifications ?: return false
            for (sbn in notifs) {
                if (isAlarmNotification(sbn)) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    fun isAlarmNotification(sbn: StatusBarNotification): Boolean {
        val pkg = sbn.packageName ?: return false
        val notif = sbn.notification ?: return false

        val isClockApp = pkg == "com.sec.android.app.clockpackage" ||
                pkg == "com.google.android.deskclock" ||
                pkg.contains("clock", ignoreCase = true)

        if (!isClockApp) return false

        val isAlarmCategory = notif.category == Notification.CATEGORY_ALARM
        val isInsistent = (notif.flags and Notification.FLAG_INSISTENT) != 0
        val hasFullScreen = notif.fullScreenIntent != null

        val title = notif.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.lowercase() ?: ""
        val text = notif.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.lowercase() ?: ""
        val combined = "$title $text"
        val hasAlarmText = combined.contains("alarm berbunyi") ||
                combined.contains("alaram berbunyi") ||
                combined.contains("pilih untuk membatalkan") ||
                combined.contains("alarm ringing") ||
                combined.contains("berbunyi") ||
                (combined.contains("alarm") && combined.contains("diatur"))

        return isAlarmCategory || isInsistent || hasFullScreen || hasAlarmText
    }

    fun onNotificationPosted(sbn: StatusBarNotification) {
        if (isAlarmNotification(sbn)) {
            Log.d(TAG, "Ringing alarm notification detected from ${sbn.packageName}: ${sbn.key}. Hiding TwoUI!")
            activeAlarmKey = sbn.key
            isAlarmFiring = true
            SamSprungOverlay.hideOverlayForAlarm()
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == activeAlarmKey || isAlarmNotification(sbn)) {
            Log.d(TAG, "Alarm notification removed: ${sbn.key}. Restoring TwoUI if cover screen is on.")
            activeAlarmKey = null
            isAlarmFiring = false
            NotificationReceiver.getReceiver()?.let {
                restoreOverlay(it.applicationContext)
            }
        }
    }

    fun setAlarmFiring(firing: Boolean, context: Context?) {
        Log.d(TAG, "setAlarmFiring: firing=$firing")
        isAlarmFiring = firing
        if (firing) {
            SamSprungOverlay.hideOverlayForAlarm()
        } else {
            activeAlarmKey = null
            context?.let { restoreOverlay(it) }
        }
    }

    fun restoreOverlay(context: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (isAlarmRinging()) {
                    Log.d(TAG, "Skip restore: alarm is still ringing")
                    return@postDelayed
                }
                if (CoverScreenTimeoutManager.isCoverScreenActive()) {
                    Log.d(TAG, "Restoring TwoUI overlay after alarm ended")
                    ScaledContext(context).cover().startActivity(
                        Intent(context.applicationContext, SamSprungOverlay::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        CoverOptions(null).getActivityOptions(1).toBundle()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore TwoUI after alarm", e)
            }
        }, 500)
    }
}
