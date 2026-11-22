package com.studyapp.pomodoro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.studyapp.pomodoro.MainActivity
import com.studyapp.pomodoro.PomodoroTimerEngine
import com.studyapp.pomodoro.PomodoroUiState
import com.studyapp.pomodoro.R
import com.studyapp.pomodoro.SessionsPerCycle
import com.studyapp.pomodoro.heading
import com.studyapp.pomodoro.service.PomodoroTimerService
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Home screen widget displaying the live/ready Pomodoro timer, active study subject,
 * daily session goal progress, current streak, and 1-tap Start/Pause and Reset controls.
 * Disables Reset when Advanced Focus is locked during an active timer while keeping Play/Pause active.
 */
class PomodoroAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val state = PomodoroTimerEngine.getInstance(context).state.value
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId, state)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context, state: PomodoroUiState) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext) ?: return
            val component = ComponentName(appContext, PomodoroAppWidgetProvider::class.java)
            val ids = runCatching { manager.getAppWidgetIds(component) }.getOrNull() ?: return
            if (ids.isEmpty()) return
            ids.forEach { widgetId ->
                updateWidget(appContext, manager, widgetId, state)
            }
        }

        private fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            state: PomodoroUiState,
        ) {
            val phaseHeading = if (state.isFlowOvertime) {
                "FLOW OVERTIME"
            } else {
                state.phase.heading.uppercase(Locale.US)
            }
            val headerText = "$phaseHeading · ${state.activeSubject.name.uppercase(Locale.US)}"
            val summaryText =
                "Today: ${state.completedFocus}/$SessionsPerCycle sessions (${state.focusedMinutes}m) · ${state.currentStreakDays}d streak"

            val openIntent = PendingIntent.getActivity(
                context,
                501,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val toggleIntent = PendingIntent.getForegroundService(
                context,
                502,
                Intent(context, PomodoroTimerService::class.java)
                    .setAction(PomodoroTimerService.ACTION_TOGGLE)
                    .putExtra(PomodoroTimerService.EXTRA_STARTED_AS_FOREGROUND, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val resetIntent = PendingIntent.getService(
                context,
                503,
                Intent(context, PomodoroTimerService::class.java)
                    .setAction(PomodoroTimerService.ACTION_RESET)
                    .putExtra(PomodoroTimerService.EXTRA_STARTED_AS_FOREGROUND, false),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val views = RemoteViews(context.packageName, R.layout.widget_pomodoro).apply {
                setTextViewText(R.id.widget_phase_subject, headerText)
                setTextViewText(R.id.widget_clock, state.displayClock)
                setTextViewText(R.id.widget_summary, summaryText)
                setProgressBar(
                    R.id.widget_progress,
                    1000,
                    (state.progress * 1000f).roundToInt().coerceIn(0, 1000),
                    false,
                )
                setTextViewText(
                    R.id.widget_button_toggle,
                    state.actionLabel,
                )
                setTextViewText(R.id.widget_button_reset, "Reset")
                setOnClickPendingIntent(R.id.widget_root, openIntent)
                setViewVisibility(R.id.widget_button_toggle, View.VISIBLE)
                setOnClickPendingIntent(
                    R.id.widget_button_toggle,
                    if (state.requiresAdvancedFocusStartWarning) openIntent else toggleIntent,
                )
                if (state.isAdvancedFocusLocked) {
                    setViewVisibility(R.id.widget_button_reset, View.GONE)
                    setOnClickPendingIntent(R.id.widget_button_reset, openIntent)
                } else {
                    setViewVisibility(R.id.widget_button_reset, View.VISIBLE)
                    setOnClickPendingIntent(R.id.widget_button_reset, resetIntent)
                }
            }
            runCatching { manager.updateAppWidget(widgetId, views) }
        }
    }
}
