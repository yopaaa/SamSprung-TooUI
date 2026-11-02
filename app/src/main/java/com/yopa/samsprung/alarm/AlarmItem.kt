package com.yopa.samsprung.alarm

import android.app.PendingIntent

data class AlarmItem(
    val id: String,
    val timeText: String,
    val remainingText: String,
    val label: String,
    val triggerTimeMillis: Long,
    val showIntent: PendingIntent?,
    val isNextAlarm: Boolean = false
)
