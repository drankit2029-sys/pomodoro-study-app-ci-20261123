package com.studyapp.pomodoro

enum class Phase {
    Focus,
    ShortBreak,
    LongBreak,
}

data class SessionSettings(
    val focus: Int = 25,
    val short: Int = 5,
    val long: Int = 15,
) {
    fun minutesFor(phase: Phase): Int = when (phase) {
        Phase.Focus -> focus
        Phase.ShortBreak -> short
        Phase.LongBreak -> long
    }
}

data class SettingBound(
    val min: Int,
    val max: Int,
    val step: Int,
)

data class CompletionInfo(
    val finished: Phase,
    val next: Phase,
    val completedFocus: Int,
    val overtimeMinutes: Int = 0,
    val bonusBreakMinutes: Int = 0,
    val canEnterFlow: Boolean = finished == Phase.Focus,
)

const val SessionsPerCycle = 4
const val FlowOvertimePerExtraBreakMinute = 5
const val MaxAdaptiveBreakBonusMinutes = 15

val DefaultSettings = SessionSettings()

val SettingBounds = mapOf(
    Phase.Focus to SettingBound(1, 180, 5),
    Phase.ShortBreak to SettingBound(1, 90, 5),
    Phase.LongBreak to SettingBound(1, 120, 5),
)

fun secondsFor(phase: Phase, settings: SessionSettings): Int = settings.minutesFor(phase) * 60

fun millisFor(phase: Phase, settings: SessionSettings): Long = secondsFor(phase, settings) * 1000L

fun defaultDurationMillisMap(settings: SessionSettings = DefaultSettings): Map<Phase, Long> =
    Phase.entries.associateWith { phase -> millisFor(phase, settings) }

/**
 * Every [FlowOvertimePerExtraBreakMinute] minutes of post-session flow overtime earns
 * +1 minute of recovery break, capped at [MaxAdaptiveBreakBonusMinutes] minutes.
 */
fun adaptiveBreakBonusMinutes(overtimeMillis: Long): Int {
    if (overtimeMillis <= 0L) return 0
    val overtimeMinutes = (overtimeMillis / 60_000L).toInt()
    return (overtimeMinutes / FlowOvertimePerExtraBreakMinute)
        .coerceIn(0, MaxAdaptiveBreakBonusMinutes)
}

fun formatClock(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val minutes = safeSeconds / 60
    val seconds = safeSeconds % 60
    // Pin the locale: ar-EG / fa-IR / bn-IN would otherwise render Eastern Arabic,
    // Persian or Bengali digits inside the ring while every other label stays ASCII.
    return String.format(java.util.Locale.US, "%02d:%02d", minutes, seconds)
}

val Phase.heading: String
    get() = when (this) {
        Phase.Focus -> "Focus session"
        Phase.ShortBreak -> "Short break"
        Phase.LongBreak -> "Long break"
    }

val Phase.shortLabel: String
    get() = when (this) {
        Phase.Focus -> "Focus"
        Phase.ShortBreak -> "Break"
        Phase.LongBreak -> "Long Break"
    }
