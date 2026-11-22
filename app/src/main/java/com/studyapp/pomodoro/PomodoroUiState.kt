package com.studyapp.pomodoro

import androidx.compose.runtime.Immutable

@Immutable
data class PomodoroUiState(
    val settings: SessionSettings = DefaultSettings,
    val phase: Phase = Phase.Focus,
    val sessionTotalMillisByPhase: Map<Phase, Long> = defaultDurationMillisMap(DefaultSettings),
    val remainingMillisByPhase: Map<Phase, Long> = defaultDurationMillisMap(DefaultSettings),
    val isRunning: Boolean = false,
    val isFlowOvertime: Boolean = false,
    val flowOvertimeMillis: Long = 0L,
    val completedFocus: Int = 0,
    val focusedMinutes: Int = 0,
    val todayEpoch: Long = currentEpochDay(),
    val historyWindow: List<DayHistoryEntry> = emptyList(),
    val fullHistoryWindow: List<DayHistoryEntry> = emptyList(),
    val showYearHistory: Boolean = false,
    val currentStreakDays: Int = 0,
    val completion: CompletionInfo? = null,
    val alarm: AlarmSettings = AlarmSettings(),
    val subjectTags: List<StudySubjectTag> = DefaultSubjectTags,
    val activeSubjectId: String = DefaultSubjectTags.first().id,
    val subjectMinutesByDay: Map<Long, Map<String, Int>> = emptyMap(),
    val isMusicEnabled: Boolean = false,
    val ambientSoundscape: AmbientSoundscape = AmbientSoundscape.SOFT_RAIN,
    val customMusicUri: String = "",
    val customMusicTitle: String = "",
    val ambientVolume: Float = 0.65f,
    val strictFocus: StrictFocusSettings = StrictFocusSettings(),
    val isPhoneFaceDown: Boolean = false,
    val strictFocusWarning: String? = null,
    val tasks: List<StudyTask> = emptyList(),
    val tasksByDay: Map<Long, List<StudyTask>> = emptyMap(),
    val activeTaskId: String? = null,
    val dailyTasksCreatedCount: Int = 0,
    val notificationPermissionGranted: Boolean = false,
    val dndPermissionGranted: Boolean = false,
    val phoneStatePermissionGranted: Boolean = false,
    val accessibilityPermissionGranted: Boolean = false,
    val isFocusModeActive: Boolean = false,
    val isAppPinned: Boolean = isFocusModeActive,
    val isAdvancedFocusConfirmVisible: Boolean = false,
    val emergencyPassUsed: Boolean = false,
    val emergencyPassUntilMillis: Long = 0L,
    val permissionPrompt: PermissionPromptState? = null,
) {
    /** True when Focus Mode is currently ON (pauses the user's blocked apps via Accessibility). */
    val isFocusEnabled: Boolean
        get() = isFocusModeActive || isAppPinned

    val configuredMillis: Long
        get() = millisFor(phase, settings)

    val totalMillis: Long
        get() = sessionTotalMillisByPhase[phase] ?: configuredMillis

    val totalSeconds: Int
        get() = (totalMillis / 1000L).toInt()

    val remainingMillis: Long
        get() = (remainingMillisByPhase[phase] ?: totalMillis).coerceIn(0L, totalMillis)

    val remaining: Int
        get() = ((remainingMillis + 999L) / 1000L).toInt()

    val flowOvertimeSeconds: Int
        get() = (flowOvertimeMillis / 1000L).toInt()

    val bonusBreakMinutes: Int
        get() = adaptiveBreakBonusMinutes(flowOvertimeMillis)

    val nextBreakPhaseAfterFlow: Phase
        get() = if (completedFocus > 0 && completedFocus % SessionsPerCycle == 0) {
            Phase.LongBreak
        } else {
            Phase.ShortBreak
        }

    val adaptiveNextBreakMinutes: Int
        get() = settings.minutesFor(nextBreakPhaseAfterFlow) + bonusBreakMinutes

    val displayClock: String
        get() = if (isFlowOvertime) {
            "+${formatClock(flowOvertimeSeconds)}"
        } else {
            formatClock(remaining)
        }

    val isMidSession: Boolean
        get() = isFlowOvertime || (remainingMillis in 1L until totalMillis)

    val progress: Float
        get() = when {
            isFlowOvertime -> 1f
            totalSeconds <= 0 -> 0f
            else -> (1f - remaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
        }

    /**
     * True while any timer (Focus, Short Break, Long Break, or Flow Overtime) has started
     * and has not yet finished (whether currently ticking or paused mid-session).
     */
    val isTimerSessionActive: Boolean
        get() = isRunning || isMidSession

    /**
     * True when Focus Mode is ON, Advanced Focus is ON, and ANY timer has started and not yet ended.
     * Advanced Focus modifies the effects of Focus Mode so that once any timer starts while Focus
     * Mode is enabled, turning off Focus Mode and resetting the timer are disabled until that timer
     * ends. Play/Pause remains allowed.
     */
    val isAdvancedFocusLocked: Boolean
        get() = isFocusEnabled && strictFocus.advancedFocusEnabled && isTimerSessionActive

    /**
     * True when pressing Start on an idle timer should display the Advanced Focus lockdown warning.
     */
    val requiresAdvancedFocusStartWarning: Boolean
        get() = !isTimerSessionActive && isFocusEnabled && strictFocus.advancedFocusEnabled

    /**
     * Whole-app visual theme mode driven by Focus Mode and Advanced Focus state.
     */
    val themeMode: AppThemeMode
        get() = when {
            isFocusEnabled && strictFocus.advancedFocusEnabled -> AppThemeMode.ADVANCED_FOCUS
            isFocusEnabled -> AppThemeMode.FOCUS
            else -> AppThemeMode.SANCTUARY
        }

    /**
     * Always resolves to a non-OFF track (defaulting to [AmbientSoundscape.SOFT_RAIN]).
     */
    val selectedMusicTrack: AmbientSoundscape
        get() = when {
            ambientSoundscape == AmbientSoundscape.CUSTOM && customMusicUri.isNotBlank() ->
                AmbientSoundscape.CUSTOM
            ambientSoundscape == AmbientSoundscape.OFF ||
                ambientSoundscape == AmbientSoundscape.CUSTOM ->
                AmbientSoundscape.SOFT_RAIN
            else -> ambientSoundscape
        }

    val activeMusicTitle: String
        get() = if (selectedMusicTrack == AmbientSoundscape.CUSTOM && customMusicTitle.isNotBlank()) {
            customMusicTitle
        } else {
            selectedMusicTrack.label
        }

    val activeMusicSubtitle: String
        get() = if (selectedMusicTrack == AmbientSoundscape.CUSTOM) {
            if (customMusicTitle.isNotBlank()) {
                "Custom audio track"
            } else {
                selectedMusicTrack.subtitle
            }
        } else {
            selectedMusicTrack.subtitle
        }

    /**
     * True when the legacy strict lockdown is active: leaving Pomodoro is blocked for the
     * whole session. Per-app blocklist mode never blocks navigation.
     */
    val isFocusExitBlocked: Boolean
        get() = isFocusEnabled && strictFocus.blockingMode == FocusBlockingMode.STRICT

    /**
     * True while the current session should enforce the per-app blocklist.
     *
     * Focus sessions block from the first second until the session ends (including while
     * paused, so pausing does not quietly unlock distractions). Breaks unblock by default;
     * [StrictFocusSettings.blockDuringBreaks] keeps the blocklist on through breaks too.
     */
    val isAppBlockingActive: Boolean
        get() = when {
            !isFocusEnabled -> false
            strictFocus.blockingMode == FocusBlockingMode.OFF -> false
            strictFocus.blockingMode == FocusBlockingMode.STRICT -> true
            isFocusSessionActive -> true
            strictFocus.blockDuringBreaks && isTimerSessionActive -> true
            else -> false
        }

    /** One 60-second escape hatch per session, so a real need never traps the user. */
    val isEmergencyPassActive: Boolean
        get() = emergencyPassUsed && System.currentTimeMillis() < emergencyPassUntilMillis

    val blockedAppCount: Int
        get() = strictFocus.blockedPackages.size

    /**
     * The Focus button checks Advanced Focus state and is disabled once a timer starts
     * while Advanced Focus is on, re-enabling only when the timer ends.
     */
    val canToggleFocusMode: Boolean
        get() = !isAdvancedFocusLocked

    /**
     * The Timer Reset button checks Advanced Focus state and is disabled once a timer starts
     * while Advanced Focus is on, re-enabling only when the timer ends.
     */
    val canReset: Boolean
        get() = !isAdvancedFocusLocked &&
            (isRunning || isMidSession || totalMillis != configuredMillis)

    val canResetDaily: Boolean
        get() = !isAdvancedFocusLocked && (completedFocus > 0 || focusedMinutes > 0)

    val sessionNumber: Int
        get() = (completedFocus % SessionsPerCycle) + 1

    val filledDots: Int
        get() = if (
            (phase != Phase.Focus || isFlowOvertime) &&
            completedFocus > 0 &&
            completedFocus % SessionsPerCycle == 0
        ) {
            SessionsPerCycle
        } else {
            completedFocus % SessionsPerCycle
        }

    val activeSubject: StudySubjectTag
        get() = subjectTags.firstOrNull { it.id == activeSubjectId } ?: DefaultSubjectTags.first()

    val activeTask: StudyTask?
        get() = tasks.firstOrNull { it.id == activeTaskId && !it.isCompleted }
            ?: tasks.firstOrNull { !it.isCompleted }

    val incompleteTasks: List<StudyTask>
        get() = tasks.filterNot { it.isCompleted }

    val completedTasks: List<StudyTask>
        get() = tasks.filter { it.isCompleted }

    fun tasksForDay(epoch: Long): List<StudyTask> =
        if (epoch == todayEpoch) {
            tasks
        } else {
            tasksByDay[epoch].orEmpty()
        }

    val sessionLabel: String
        get() = when {
            isFlowOvertime -> if (bonusBreakMinutes > 0) {
                "Flow overtime · +${bonusBreakMinutes}m break earned"
            } else {
                "Flow overtime · Counting up"
            }
            phase == Phase.Focus -> "Session $sessionNumber of $SessionsPerCycle"
            phase == Phase.LongBreak -> "Long recovery"
            else -> "Short recovery"
        }

    val actionLabel: String
        get() = when {
            isFlowOvertime && isRunning -> "Pause flow"
            isFlowOvertime && !isRunning -> "Resume flow"
            isRunning -> "Pause"
            isMidSession -> "Resume"
            phase == Phase.Focus -> "Start focus"
            else -> "Start break"
        }

    val finishFlowLabel: String
        get() = "Take ${adaptiveNextBreakMinutes}m break"

    val statusText: String
        get() = when {
            strictFocusWarning != null && isRunning && (phase == Phase.Focus || isFlowOvertime) ->
                strictFocusWarning
            isFlowOvertime && isRunning ->
                "Deep in flow. Extra time scales your upcoming recovery break."
            isFlowOvertime ->
                "Flow paused. Resume or step into your ${adaptiveNextBreakMinutes}-minute break."
            isRunning -> "The room is quiet. Your focus is protected."
            isMidSession -> "Paused. Resume or reset before switching modes."
            else -> "Begin gently. The screen will stay awake while you focus."
        }

    val thirtyDayFocusedMinutes: Int
        get() = if (historyWindow.isEmpty()) {
            focusedMinutes
        } else {
            historyWindow.filter { it.dayEpoch <= todayEpoch }.sumOf { it.focusedMinutes }
        }

    val thirtyDayCompletedSessions: Int
        get() = if (historyWindow.isEmpty()) {
            completedFocus
        } else {
            historyWindow.filter { it.dayEpoch <= todayEpoch }.sumOf { it.completedFocus }
        }

    val yearFocusedMinutes: Int
        get() = if (fullHistoryWindow.isEmpty()) {
            thirtyDayFocusedMinutes
        } else {
            fullHistoryWindow.filter { it.dayEpoch <= todayEpoch }.sumOf { it.focusedMinutes }
        }

    val yearCompletedSessions: Int
        get() = if (fullHistoryWindow.isEmpty()) {
            thirtyDayCompletedSessions
        } else {
            fullHistoryWindow.filter { it.dayEpoch <= todayEpoch }.sumOf { it.completedFocus }
        }

    val activeStreakEpochs: Set<Long>
        get() = computeActiveStreakEpochs(
            if (fullHistoryWindow.isNotEmpty()) fullHistoryWindow else historyWindow,
            todayEpoch,
        )

    val isFocusRunning: Boolean
        get() = isRunning && (phase == Phase.Focus || isFlowOvertime)

    val isFocusSessionActive: Boolean
        get() = (phase == Phase.Focus || isFlowOvertime) && (isRunning || isMidSession)

    /**
     * Play/Pause is allowed in all modes, including Advanced Focus.
     */
    val canPressPrimaryTimerButton: Boolean
        get() = true

    val canSelectPhaseTabs: Boolean
        get() = !isAdvancedFocusLocked && !isRunning && !isMidSession

    val canAdjustDuration: Boolean
        get() = !isAdvancedFocusLocked && !isRunning

    val yearHistoryMonths: List<MonthHistoryGroup>
        get() = buildYearHistoryMonths(
            todayRecord = DailyRhythmRecord(
                dayEpoch = todayEpoch,
                completedFocus = completedFocus,
                focusedMinutes = focusedMinutes,
            ),
            history = fullHistoryWindow.associateBy { it.dayEpoch },
            todayEpoch = todayEpoch,
        )

    val subjectBreakdown: List<SubjectAllocationSummary>
        get() {
            val spanDays = if (showYearHistory) HISTORY_WINDOW_DAYS_YEAR else HISTORY_WINDOW_DAYS
            val startEpoch = todayEpoch - (spanDays - 1)
            return computeSubjectBreakdown(
                subjectMinutesByDay = subjectMinutesByDay,
                tags = subjectTags,
                windowStartEpoch = startEpoch,
                todayEpoch = todayEpoch,
            )
        }
}
