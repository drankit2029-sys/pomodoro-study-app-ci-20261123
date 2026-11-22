package com.studyapp.pomodoro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.studyapp.pomodoro.PomodoroTimerEngine

/**
 * Delivers the best-effort backup wake-up scheduled at the session deadline.
 *
 * Instantiating the engine restores the persisted session; if the deadline has passed the
 * restore path posts the completion notification and rings the alarm. This avoids starting
 * a foreground service from the alarm, which Android 12+ blocks for inexact alarms.
 */
class PomodoroWakeupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val engine = PomodoroTimerEngine.getInstance(context.applicationContext)
        engine.onBackupWakeup()
    }
}
