package com.studyapp.pomodoro

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.studyapp.pomodoro.service.PomodoroTimerService
import kotlin.math.roundToInt

object PomodoroNotifications {

    const val CHANNEL_TIMER = "pomodoro_timer"
    const val CHANNEL_COMPLETION = "pomodoro_completion_alarm"
    private const val LEGACY_CHANNEL_COMPLETION = "pomodoro_completion"
    const val ONGOING_NOTIFICATION_ID = 4101
    const val COMPLETION_NOTIFICATION_ID = 4102

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_COMPLETION)
        val timerChannel = NotificationChannel(
            CHANNEL_TIMER,
            context.getString(R.string.notification_channel_timer),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_timer_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val completionChannel = NotificationChannel(
            CHANNEL_COMPLETION,
            context.getString(R.string.notification_channel_completion),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_completion_description)
            setShowBadge(true)
            enableVibration(false)
            setSound(null, null)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannels(listOf(timerChannel, completionChannel))
    }

    fun buildOngoing(context: Context, state: PomodoroUiState): Notification {
        val phaseTitle = if (state.isFlowOvertime) {
            "Flow Overtime · ${state.sessionLabel}"
        } else {
            "${state.phase.heading} · ${state.sessionLabel}"
        }
        val clock = state.displayClock
        val status = when {
            state.isFlowOvertime && state.isRunning ->
                context.getString(
                    R.string.notification_status_flow_running,
                    state.adaptiveNextBreakMinutes,
                )
            state.isFlowOvertime ->
                context.getString(
                    R.string.notification_status_flow_paused,
                    state.adaptiveNextBreakMinutes,
                )
            state.isRunning ->
                context.getString(R.string.notification_status_running)
            else ->
                context.getString(R.string.notification_status_paused)
        }
        val openIntent = openAppIntent(context)
        val toggleIntent = servicePendingIntent(
            context = context,
            action = PomodoroTimerService.ACTION_TOGGLE,
            requestCode = 11,
            foreground = true,
        )
        val secondaryIntent = if (state.isFlowOvertime) {
            servicePendingIntent(
                context = context,
                action = PomodoroTimerService.ACTION_FINISH_FLOW,
                requestCode = 14,
                foreground = true,
            )
        } else {
            servicePendingIntent(
                context = context,
                action = PomodoroTimerService.ACTION_RESET,
                requestCode = 12,
                foreground = false,
            )
        }

        // Ensure a visible rounded progress cap (min 45/1000) when a session has started
        // so the progress bar never looks like an empty unstyled line at 24:59.
        val rawPermille = (state.progress * 1000f).roundToInt().coerceIn(0, 1000)
        val visualPermille = if (state.isRunning || state.isMidSession) {
            rawPermille.coerceAtLeast(45)
        } else {
            rawPermille
        }

        val compact = RemoteViews(context.packageName, R.layout.notification_pomodoro_compact).apply {
            setTextViewText(R.id.notification_phase, phaseTitle)
            setTextViewText(R.id.notification_title, clock)
            setTextViewText(R.id.notification_body, status)
        }

        val expanded =
            RemoteViews(context.packageName, R.layout.notification_pomodoro_expanded).apply {
                setTextViewText(R.id.notification_phase, phaseTitle)
                setTextViewText(R.id.notification_title, clock)
                setTextViewText(R.id.notification_body, status)
                setProgressBar(
                    R.id.notification_progress,
                    1000,
                    visualPermille,
                    false,
                )
                setViewVisibility(R.id.notification_progress, View.VISIBLE)
                setTextViewText(
                    R.id.notification_action_primary,
                    context.getString(
                        if (state.isRunning) {
                            R.string.notification_action_pause
                        } else {
                            R.string.notification_action_resume
                        },
                    ),
                )
                setTextViewText(
                    R.id.notification_action_secondary,
                    context.getString(
                        if (state.isFlowOvertime) {
                            R.string.notification_action_finish_flow
                        } else {
                            R.string.notification_action_reset
                        },
                    ),
                )
                setViewVisibility(R.id.notification_action_tertiary, View.GONE)
                setViewVisibility(R.id.notification_action_primary, View.VISIBLE)
                setOnClickPendingIntent(R.id.notification_action_primary, toggleIntent)
                if (state.isAdvancedFocusLocked && !state.isFlowOvertime) {
                    setViewVisibility(R.id.notification_action_secondary, View.GONE)
                } else {
                    setViewVisibility(R.id.notification_action_secondary, View.VISIBLE)
                    setOnClickPendingIntent(R.id.notification_action_secondary, secondaryIntent)
                }
            }

        return NotificationCompat.Builder(context, CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_stat_pomodoro)
            .setContentTitle("$phaseTitle · $clock")
            .setContentText(status)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(ContextCompat.getColor(context, R.color.forest))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showCompletion(context: Context, state: PomodoroUiState) {
        val completion = state.completion ?: return
        if (!canPostNotifications(context)) return

        val title = context.getString(
            if (completion.finished == Phase.Focus) {
                R.string.notification_complete_focus
            } else {
                R.string.notification_complete_break
            },
        )
        val nextLabel = when (completion.next) {
            Phase.Focus -> context.getString(R.string.notification_next_focus)
            Phase.ShortBreak -> context.getString(R.string.notification_next_short_break)
            Phase.LongBreak -> context.getString(R.string.notification_next_long_break)
        }
        val body = when {
            completion.finished == Phase.Focus && completion.next == Phase.LongBreak ->
                context.getString(R.string.notification_completion_long_break, nextLabel)

            completion.finished == Phase.Focus ->
                context.getString(
                    R.string.notification_completion_focus,
                    completion.completedFocus,
                    SessionsPerCycle,
                    nextLabel,
                )

            else ->
                context.getString(R.string.notification_completion_break, nextLabel)
        }
        val compactBody =
            context.getString(R.string.notification_completion_compact, nextLabel)
        val openIntent = openAppIntent(context)
        val startNextIntent = servicePendingIntent(
            context = context,
            action = PomodoroTimerService.ACTION_START_NEXT,
            requestCode = 21,
            foreground = true,
        )
        val keepFlowingIntent = servicePendingIntent(
            context = context,
            action = PomodoroTimerService.ACTION_KEEP_FLOWING,
            requestCode = 23,
            foreground = true,
        )
        val stopAlarmIntent = servicePendingIntent(
            context = context,
            action = PomodoroTimerService.ACTION_STOP_ALARM,
            requestCode = 22,
            foreground = false,
        )

        val compact = RemoteViews(context.packageName, R.layout.notification_pomodoro_compact).apply {
            setTextViewText(R.id.notification_phase, title)
            setTextViewText(R.id.notification_title, "Done")
            setTextViewText(R.id.notification_body, compactBody)
        }

        val expanded =
            RemoteViews(context.packageName, R.layout.notification_pomodoro_expanded).apply {
                setTextViewText(R.id.notification_phase, title)
                setTextViewText(R.id.notification_title, "100%")
                setTextViewText(R.id.notification_body, body)
                setProgressBar(R.id.notification_progress, 1000, 1000, false)
                setViewVisibility(R.id.notification_progress, View.VISIBLE)
                setTextViewText(
                    R.id.notification_action_primary,
                    context.getString(R.string.notification_action_start_next),
                )
                if (completion.canEnterFlow) {
                    setTextViewText(
                        R.id.notification_action_secondary,
                        context.getString(R.string.notification_action_keep_flowing),
                    )
                    setOnClickPendingIntent(R.id.notification_action_secondary, keepFlowingIntent)
                    setTextViewText(
                        R.id.notification_action_tertiary,
                        context.getString(R.string.notification_action_silence),
                    )
                    setViewVisibility(R.id.notification_action_tertiary, View.VISIBLE)
                    setOnClickPendingIntent(R.id.notification_action_tertiary, stopAlarmIntent)
                } else {
                    setTextViewText(
                        R.id.notification_action_secondary,
                        context.getString(R.string.notification_action_silence),
                    )
                    setOnClickPendingIntent(R.id.notification_action_secondary, stopAlarmIntent)
                    setViewVisibility(R.id.notification_action_tertiary, View.GONE)
                }
                setOnClickPendingIntent(R.id.notification_action_primary, startNextIntent)
            }

        val notification = NotificationCompat.Builder(context, CHANNEL_COMPLETION)
            .setSmallIcon(R.drawable.ic_stat_pomodoro)
            .setContentTitle(title)
            .setContentText(compactBody)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setColor(ContextCompat.getColor(context, R.color.forest))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compact)
            .setCustomBigContentView(expanded)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(COMPLETION_NOTIFICATION_ID, notification)
        }
    }

    fun cancelCompletion(context: Context) {
        NotificationManagerCompat.from(context).cancel(COMPLETION_NOTIFICATION_ID)
    }

    @SuppressLint("MissingPermission")
    fun showPausedOngoing(context: Context, state: PomodoroUiState) {
        if (!canPostNotifications(context)) return
        val notification = buildOngoing(context, state)
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    fun cancelOngoing(context: Context) {
        NotificationManagerCompat.from(context).cancel(ONGOING_NOTIFICATION_ID)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            31,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(
        context: Context,
        action: String,
        requestCode: Int,
        foreground: Boolean,
    ): PendingIntent {
        val intent = Intent(context, PomodoroTimerService::class.java)
            .setAction(action)
            .putExtra(PomodoroTimerService.EXTRA_STARTED_AS_FOREGROUND, foreground)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (foreground) {
            PendingIntent.getForegroundService(context, requestCode, intent, flags)
        } else {
            PendingIntent.getService(context, requestCode, intent, flags)
        }
    }
}
