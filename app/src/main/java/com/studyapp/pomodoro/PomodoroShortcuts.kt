package com.studyapp.pomodoro

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/**
 * Launcher long-press shortcuts for the timer.
 *
 * The set is published dynamically (instead of a static `shortcuts.xml`) so the pause entry can
 * follow the real state: "Pause focus" while the timer runs, "Resume focus" while it is paused.
 * Intents are built with an explicit component, so they keep working in debug builds that carry
 * an `applicationIdSuffix`.
 */
object PomodoroShortcuts {

    const val ACTION_START_FOCUS = "com.studyapp.pomodoro.action.START_FOCUS"
    const val ACTION_TOGGLE_TIMER = "com.studyapp.pomodoro.action.TOGGLE_TIMER"
    const val ACTION_RESET_TIMER = "com.studyapp.pomodoro.action.RESET_TIMER"

    private const val ID_START = "shortcut_start_focus"
    private const val ID_TOGGLE = "shortcut_toggle_timer"
    private const val ID_RESET = "shortcut_reset_timer"

    private val shortcutActions = setOf(ACTION_START_FOCUS, ACTION_TOGGLE_TIMER, ACTION_RESET_TIMER)

    /** True when [action] was produced by one of our launcher shortcuts. */
    fun isShortcutAction(action: String?): Boolean = action != null && action in shortcutActions

    /**
     * Re-publishes the dynamic shortcut set for [state]. Safe to call on every state change:
     * the system ignores an update that would not change anything, and a rate-limited manager
     * keeps the previous set instead of throwing.
     */
    fun publish(context: Context, state: PomodoroUiState) {
        runCatching {
            if (!ShortcutManagerCompat.isRateLimitingActive(context)) {
                ShortcutManagerCompat.setDynamicShortcuts(context, buildShortcuts(context, state))
            }
        }.onFailure {
            Log.w("PomodoroShortcuts", "Unable to publish launcher shortcuts", it)
        }
    }

    private fun buildShortcuts(context: Context, state: PomodoroUiState): List<ShortcutInfoCompat> {
        val icon = IconCompat.createWithResource(context, R.drawable.ic_notification_pomodoro)
        val toggleLabel = if (state.isRunning) "Pause focus" else "Resume focus"
        val toggleShortLabel = if (state.isRunning) "Pause" else "Resume"

        return listOf(
            shortcut(
                context = context,
                id = ID_START,
                action = ACTION_START_FOCUS,
                shortLabel = "Start",
                longLabel = "Start focus",
                icon = icon,
                rank = 0,
            ),
            shortcut(
                context = context,
                id = ID_TOGGLE,
                action = ACTION_TOGGLE_TIMER,
                shortLabel = toggleShortLabel,
                longLabel = toggleLabel,
                icon = icon,
                rank = 1,
            ),
            shortcut(
                context = context,
                id = ID_RESET,
                action = ACTION_RESET_TIMER,
                shortLabel = "Reset",
                longLabel = "Reset timer",
                icon = icon,
                rank = 2,
            ),
        )
    }

    private fun shortcut(
        context: Context,
        id: String,
        action: String,
        shortLabel: String,
        longLabel: String,
        icon: IconCompat,
        rank: Int,
    ): ShortcutInfoCompat {
        val intent = Intent(context, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(icon)
            .setIntent(intent)
            .setActivity(ComponentName(context, MainActivity::class.java))
            .setRank(rank)
            .build()
    }
}
