package com.studyapp.pomodoro

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.studyapp.pomodoro.ads.FullscreenVideoAdManager
import com.studyapp.pomodoro.audio.AmbientSoundPlayer
import com.studyapp.pomodoro.export.StudyHistoryExporter
import com.studyapp.pomodoro.focus.AppBlocklistCodec
import com.studyapp.pomodoro.focus.GlobalPermissionManager
import com.studyapp.pomodoro.focus.StrictFocusManager
import com.studyapp.pomodoro.service.PomodoroTimerService
import com.studyapp.pomodoro.service.PomodoroWakeupReceiver
import com.studyapp.pomodoro.ui.components.EndScreenBannerAdManager
import com.studyapp.pomodoro.ui.components.NativeAdCache
import com.studyapp.pomodoro.widget.PomodoroAppWidgetProvider
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PomodoroTimerEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var deadlineElapsed: Long? = null
    private var restoredOverdueMillis: Long = 0L
    private var flowAnchorElapsed: Long? = null
    private var bankedFlowMinutes: Int = 0
    private var ticker: Job? = null
    private var dayEpoch: Long = currentEpochDay()
    private var lastDayCheckElapsed: Long = 0L
    private val stateLock = Any()
    private var historyByEpoch: Map<Long, DayHistoryEntry> = emptyMap()
    private var subjectMinutesByDay: MutableMap<Long, MutableMap<String, Int>> = mutableMapOf()
    private var tasksByDay: MutableMap<Long, List<StudyTask>> = mutableMapOf()

    private val _state: MutableStateFlow<PomodoroUiState>
    val state: StateFlow<PomodoroUiState>

    init {
        val restored = restoreState()
        _state = MutableStateFlow(restored)
        state = _state.asStateFlow()

        scope.launch {
            GlobalPermissionManager.promptState.collect { prompt ->
                _state.update { it.copy(permissionPrompt = prompt) }
            }
        }

        if (restored.isRunning && !restored.isFlowOvertime && restored.remainingMillis <= 0L) {
            // A session that expired long before the app was reopened must not ring the
            // completion alarm on cold start; only a fresh expiry (within a minute) should.
            finishPhase(ringAlarm = restoredOverdueMillis <= RESTORED_ALARM_GRACE_MILLIS)
        } else {
            persistState()
            if (restored.isRunning) {
                deadlineElapsed?.let { scheduleBackupWakeup(it) }
                startTicker()
            }
        }
        startDayWatcher()
        syncAuxiliarySystems()
    }

    /**
     * Called by [PomodoroWakeupReceiver] when the best-effort backup alarm fires. If the
     * process is alive but the ticker was delayed (Doze), finish the session now; otherwise
     * make sure the ticker is running.
     */
    fun onBackupWakeup() {
        ensureDailyReset()
        val current = _state.value
        if (current.completion != null && !PomodoroAlarmPlayer.isRinging) {
            // The receiver just constructed the engine: restore finished the overdue
            // session silently (it may be more than a minute late). This alarm is the
            // reason the process woke, so ring now.
            PomodoroAlarmPlayer.play(appContext, current.alarm)
            return
        }
        if (!current.isRunning || current.isFlowOvertime) return
        val deadline = deadlineElapsed ?: return
        if (SystemClock.elapsedRealtime() >= deadline) {
            finishPhase()
        } else {
            startTicker()
        }
    }

    /**
     * Toggles Play/Pause on the current timer. Play/Pause is allowed in all modes,
     * including when Advanced Focus is active.
     */
    fun toggle(shouldStartService: Boolean = true) {
        ensureDailyReset()
        if (_state.value.isRunning) {
            pause(shouldStartService)
        } else {
            start(shouldStartService)
        }
    }

    fun startFlowOvertime(shouldStartService: Boolean = true) {
        ensureDailyReset()
        EndScreenBannerAdManager.onNewTimerCycleStarted()
        PomodoroAlarmPlayer.stop()
        PomodoroNotifications.cancelCompletion(appContext)
        cancelBackupWakeup()
        clearEmergencyPass()

        val nowElapsed = SystemClock.elapsedRealtime()
        deadlineElapsed = null
        flowAnchorElapsed = nowElapsed
        bankedFlowMinutes = 0

        _state.update { current ->
            current.copy(
                phase = Phase.Focus,
                isRunning = true,
                isFlowOvertime = true,
                flowOvertimeMillis = 0L,
                completion = null,
            )
        }
        engageFocusOnTimerStartIfNeeded()
        persistState()
        startTicker()
        if (shouldStartService) {
            startService()
        }
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    fun finishFlow(shouldStartService: Boolean = true) {
        ensureDailyReset()
        val current = _state.value
        if (!current.isFlowOvertime) return

        EndScreenBannerAdManager.onNewTimerCycleStarted()
        PomodoroAlarmPlayer.stop()
        PomodoroNotifications.cancelCompletion(appContext)
        stopTicker()

        val nowElapsed = SystemClock.elapsedRealtime()
        val finalOvertimeMillis = if (current.isRunning && flowAnchorElapsed != null) {
            (nowElapsed - flowAnchorElapsed!!).coerceAtLeast(current.flowOvertimeMillis)
        } else {
            current.flowOvertimeMillis
        }
        bankUnrecordedFlowMinutes(finalOvertimeMillis)

        val bonusMinutes = adaptiveBreakBonusMinutes(finalOvertimeMillis)
        val nextPhase = current.nextBreakPhaseAfterFlow
        val baseBreakMillis = millisFor(nextPhase, current.settings)
        val adaptiveBreakMillis = baseBreakMillis + (bonusMinutes * 60_000L)
        val focusDurationMillis = millisFor(Phase.Focus, current.settings)

        val updatedTotals = current.sessionTotalMillisByPhase.toMutableMap().apply {
            this[Phase.Focus] = focusDurationMillis
            this[nextPhase] = adaptiveBreakMillis
        }
        val updatedRemaining = current.remainingMillisByPhase.toMutableMap().apply {
            this[Phase.Focus] = focusDurationMillis
            this[nextPhase] = adaptiveBreakMillis
        }

        val deadline = SystemClock.elapsedRealtime() + adaptiveBreakMillis
        flowAnchorElapsed = null
        bankedFlowMinutes = 0
        deadlineElapsed = deadline

        _state.update { stateAfterBank ->
            stateAfterBank.copy(
                phase = nextPhase,
                isFlowOvertime = false,
                flowOvertimeMillis = 0L,
                isRunning = true,
                sessionTotalMillisByPhase = updatedTotals,
                remainingMillisByPhase = updatedRemaining,
                completion = null,
            )
        }
        engageFocusOnTimerStartIfNeeded()
        persistState()
        scheduleBackupWakeup(deadline)
        startTicker()
        if (shouldStartService) {
            startService()
        }
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    fun reset(shouldStopService: Boolean = true, force: Boolean = false) {
        if (!force && _state.value.isAdvancedFocusLocked) return
        EndScreenBannerAdManager.onNewTimerCycleStarted()
        PomodoroAlarmPlayer.stop()
        val current = _state.value
        clearEmergencyPass()
        deadlineElapsed = null
        flowAnchorElapsed = null
        bankedFlowMinutes = 0
        cancelBackupWakeup()
        stopTicker()

        val newDurationMillis = millisFor(current.phase, current.settings)
        val updatedTotals = current.sessionTotalMillisByPhase.toMutableMap().apply {
            this[current.phase] = newDurationMillis
        }
        val updatedRemaining = current.remainingMillisByPhase.toMutableMap().apply {
            this[current.phase] = newDurationMillis
        }

        _state.update {
            it.copy(
                isRunning = false,
                isFlowOvertime = false,
                flowOvertimeMillis = 0L,
                sessionTotalMillisByPhase = updatedTotals,
                remainingMillisByPhase = updatedRemaining,
                completion = null,
            )
        }
        PomodoroNotifications.cancelCompletion(appContext)
        persistState()
        if (shouldStopService) {
            PomodoroTimerService.stop(appContext)
        }
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    /**
     * Ends the current session even when Advanced Focus would normally forbid resetting.
     * Only reachable from the blocked-app screen's hold-to-confirm control.
     */
    fun forceReset() = reset(force = true)

    fun selectPhase(phase: Phase) {
        val current = _state.value
        if (current.isAdvancedFocusLocked) return
        if (phase == current.phase || current.isRunning || current.isMidSession) return

        PomodoroAlarmPlayer.stop()
        deadlineElapsed = null
        flowAnchorElapsed = null
        bankedFlowMinutes = 0
        cancelBackupWakeup()
        stopTicker()
        _state.update {
            it.copy(
                phase = phase,
                isRunning = false,
                isFlowOvertime = false,
                flowOvertimeMillis = 0L,
                completion = null,
            )
        }
        PomodoroNotifications.cancelCompletion(appContext)
        persistState()
        PomodoroTimerService.stop(appContext)
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    fun updateSetting(phase: Phase, value: Int) {
        val current = _state.value
        // Changing the length of a paused, partially elapsed session would reset both the
        // total and the remaining time and later credit the new total as focused minutes.
        if (current.isRunning || current.isMidSession || current.isAdvancedFocusLocked) return

        val bounds = SettingBounds.getValue(phase)
        val nextValue = value.coerceIn(bounds.min, bounds.max)

        val nextSettings = when (phase) {
            Phase.Focus -> current.settings.copy(focus = nextValue)
            Phase.ShortBreak -> current.settings.copy(short = nextValue)
            Phase.LongBreak -> current.settings.copy(long = nextValue)
        }

        val newTotalMillis = millisFor(phase, nextSettings)
        val updatedTotals = current.sessionTotalMillisByPhase.toMutableMap().apply {
            this[phase] = newTotalMillis
        }
        val updatedRemaining = current.remainingMillisByPhase.toMutableMap().apply {
            this[phase] = newTotalMillis
        }
        _state.update {
            it.copy(
                settings = nextSettings,
                isFlowOvertime = if (phase == current.phase) false else it.isFlowOvertime,
                flowOvertimeMillis = if (phase == current.phase) 0L else it.flowOvertimeMillis,
                sessionTotalMillisByPhase = updatedTotals,
                remainingMillisByPhase = updatedRemaining,
            )
        }
        persistState()
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    fun resetDailySessions() {
        if (_state.value.isAdvancedFocusLocked) return
        dayEpoch = currentEpochDay()
        val zeroRecord = DailyRhythmRecord(
            dayEpoch = dayEpoch,
            completedFocus = 0,
            focusedMinutes = 0,
        )
        synchronized(stateLock) {
            subjectMinutesByDay.remove(dayEpoch)
            historyByEpoch = archiveAndPruneHistory(
                historyByEpoch,
                zeroRecord,
                dayEpoch,
                HISTORY_WINDOW_DAYS_YEAR,
            )
        }
        val historySnapshot = synchronized(stateLock) { historyByEpoch }
        val window30 = buildThirtyDayWindow(zeroRecord, historySnapshot, dayEpoch)
        val windowFull = buildHistoryWindow(
            zeroRecord,
            historySnapshot,
            dayEpoch,
            HISTORY_WINDOW_DAYS_YEAR,
        )
        val streak = computeCurrentStreak(windowFull, dayEpoch)
        _state.update {
            it.copy(
                completedFocus = 0,
                focusedMinutes = 0,
                todayEpoch = dayEpoch,
                historyWindow = window30,
                fullHistoryWindow = windowFull,
                currentStreakDays = streak,
                subjectMinutesByDay = copySubjectMap(),
            )
        }
        persistState()
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    /**
     * Kept only for explicit calls in instrumentation tests; never called automatically.
     */
    internal fun seedDemoHistory() {
        val today = currentEpochDay()
        dayEpoch = today
        val seeded = buildDefaultSeedHistory(today)
        val todayEntry = seeded[today] ?: DayHistoryEntry(today, 3, 85)
        val todayRecord = DailyRhythmRecord(
            dayEpoch = today,
            completedFocus = todayEntry.completedFocus,
            focusedMinutes = todayEntry.focusedMinutes,
        )
        val historySnapshot = synchronized(stateLock) {
            historyByEpoch = archiveAndPruneHistory(
                seeded,
                todayRecord,
                today,
                HISTORY_WINDOW_DAYS_YEAR,
            )
            subjectMinutesByDay.clear()
            val subjectIds = listOf("general", "math_cs", "science", "reading")
            historyByEpoch.forEach { (epoch, entry) ->
                if (entry.focusedMinutes > 0) {
                    val primaryId = subjectIds[(epoch.toInt().coerceAtLeast(0)) % subjectIds.size]
                    val secondaryId = subjectIds[((epoch.toInt().coerceAtLeast(0)) + 1) % subjectIds.size]
                    val primaryMins = (entry.focusedMinutes * 2) / 3
                    val secondaryMins = (entry.focusedMinutes - primaryMins).coerceAtLeast(0)
                    val map = mutableMapOf(primaryId to primaryMins)
                    if (secondaryMins > 0) {
                        map[secondaryId] = secondaryMins
                    }
                    subjectMinutesByDay[epoch] = map
                }
            }
            historyByEpoch
        }
        val window30 = buildThirtyDayWindow(todayRecord, historySnapshot, today)
        val windowFull = buildHistoryWindow(
            todayRecord,
            historySnapshot,
            today,
            HISTORY_WINDOW_DAYS_YEAR,
        )
        val streak = computeCurrentStreak(windowFull, today)
        _state.update {
            it.copy(
                completedFocus = todayRecord.completedFocus,
                focusedMinutes = todayRecord.focusedMinutes,
                todayEpoch = today,
                historyWindow = window30,
                fullHistoryWindow = windowFull,
                currentStreakDays = streak,
                subjectMinutesByDay = copySubjectMap(),
            )
        }
        persistState()
    }

    internal fun clearAllHistory() {
        val today = currentEpochDay()
        dayEpoch = today
        synchronized(stateLock) {
            historyByEpoch = emptyMap()
            subjectMinutesByDay.clear()
            tasksByDay.clear()
        }
        val zeroRecord = DailyRhythmRecord(today, 0, 0)
        val window30 = buildThirtyDayWindow(zeroRecord, emptyMap(), today)
        val windowFull = buildHistoryWindow(
            zeroRecord,
            emptyMap(),
            today,
            HISTORY_WINDOW_DAYS_YEAR,
        )
        _state.update {
            it.copy(
                completedFocus = 0,
                focusedMinutes = 0,
                todayEpoch = today,
                historyWindow = window30,
                fullHistoryWindow = windowFull,
                currentStreakDays = 0,
                subjectMinutesByDay = emptyMap(),
                tasks = emptyList(),
                tasksByDay = emptyMap(),
                activeTaskId = null,
                dailyTasksCreatedCount = 0,
            )
        }
        persistState()
    }

    /**
     * Archives the prior day's sessions AND tasks at 12:00 AM midnight, and refreshes
     * today's sessions and today's task list to empty for the new day.
     */
    fun ensureDailyReset() {
        val today = currentEpochDay()
        if (today == dayEpoch) return

        val currentBeforeReset = _state.value
        if (currentBeforeReset.isFlowOvertime) {
            val nowElapsed = SystemClock.elapsedRealtime()
            val overtimeBeforeMidnight = if (currentBeforeReset.isRunning && flowAnchorElapsed != null) {
                (nowElapsed - flowAnchorElapsed!!).coerceAtLeast(currentBeforeReset.flowOvertimeMillis)
            } else {
                currentBeforeReset.flowOvertimeMillis
            }
            bankUnrecordedFlowMinutes(overtimeBeforeMidnight)
            flowAnchorElapsed = if (currentBeforeReset.isRunning) nowElapsed else null
            bankedFlowMinutes = 0
        }

        val priorRecord = DailyRhythmRecord(
            dayEpoch = dayEpoch,
            completedFocus = _state.value.completedFocus,
            focusedMinutes = _state.value.focusedMinutes,
        )
        val priorTasks = _state.value.tasks
        val oldestAllowed = today - (HISTORY_WINDOW_DAYS_YEAR - 1)
        synchronized(stateLock) {
            historyByEpoch = archiveAndPruneHistory(
                historyByEpoch,
                priorRecord,
                today,
                HISTORY_WINDOW_DAYS_YEAR,
            )
            if (priorTasks.isNotEmpty()) {
                tasksByDay[dayEpoch] = priorTasks.toList()
            } else {
                tasksByDay.remove(dayEpoch)
            }
            tasksByDay.keys.removeAll { it < oldestAllowed }
        }

        dayEpoch = today
        val todayTasks = synchronized(stateLock) { tasksByDay[today].orEmpty() }
        val todayRecord = DailyRhythmRecord(
            dayEpoch = today,
            completedFocus = 0,
            focusedMinutes = 0,
        )
        val historySnapshot = synchronized(stateLock) { historyByEpoch }
        val window30 = buildThirtyDayWindow(todayRecord, historySnapshot, today)
        val windowFull = buildHistoryWindow(
            todayRecord,
            historySnapshot,
            today,
            HISTORY_WINDOW_DAYS_YEAR,
        )
        val streak = computeCurrentStreak(windowFull, today)
        _state.update {
            it.copy(
                completedFocus = 0,
                focusedMinutes = 0,
                flowOvertimeMillis = if (it.isFlowOvertime) 0L else it.flowOvertimeMillis,
                todayEpoch = today,
                historyWindow = window30,
                fullHistoryWindow = windowFull,
                currentStreakDays = streak,
                tasks = todayTasks,
                tasksByDay = copyTasksByDay(),
                activeTaskId = todayTasks.firstOrNull { t -> !t.isCompleted }?.id,
                dailyTasksCreatedCount = todayTasks.size,
            )
        }
        persistState()
    }

    fun updateAlarmEnabled(enabled: Boolean) {
        _state.update { it.copy(alarm = it.alarm.copy(enabled = enabled)) }
        if (!enabled) {
            PomodoroAlarmPlayer.stop()
        }
        persistState()
    }

    fun updateAlarmSound(sound: String) {
        _state.update { it.copy(alarm = it.alarm.copy(sound = sound)) }
        if (sound == SILENT_ALARM_SOUND) {
            PomodoroAlarmPlayer.stopSound()
        }
        persistState()
    }

    fun updateAlarmVibration(enabled: Boolean) {
        _state.update { it.copy(alarm = it.alarm.copy(vibration = enabled)) }
        if (!enabled) {
            PomodoroAlarmPlayer.stopVibration()
        }
        persistState()
    }

    fun updateAlarmDuration(seconds: Int) {
        val safeSeconds = seconds.coerceIn(5, 120)
        _state.update { it.copy(alarm = it.alarm.copy(durationSeconds = safeSeconds)) }
        persistState()
    }

    fun previewAlarm() {
        PomodoroAlarmPlayer.play(appContext, _state.value.alarm, preview = true)
    }

    fun stopAlarm() {
        PomodoroAlarmPlayer.stop()
        syncAuxiliarySystems()
    }

    // --- Feature 1: Subject / Project Tags ---
    fun selectSubject(subjectId: String) {
        val current = _state.value
        current.subjectTags.firstOrNull { it.id == subjectId } ?: return
        _state.update { it.copy(activeSubjectId = subjectId) }
        persistState()
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    fun addCustomSubject(name: String, colorHex: Long): Boolean {
        val trimmed = name.trim().take(22)
        if (trimmed.isEmpty()) return false
        val existing = _state.value.subjectTags.firstOrNull {
            it.name.equals(trimmed, ignoreCase = true)
        }
        if (existing != null) {
            _state.update { it.copy(activeSubjectId = existing.id) }
            persistState()
            syncAuxiliarySystems()
            return true
        }
        val slug = trimmed.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "tag" }
        val id = "custom_${slug}_${System.currentTimeMillis() % 100_000}"
        val newTag = StudySubjectTag(
            id = id,
            name = trimmed,
            colorHex = sanitizeSubjectColorHex(colorHex),
            isCustom = true,
        )
        _state.update { current ->
            current.copy(
                subjectTags = current.subjectTags + newTag,
                activeSubjectId = newTag.id,
            )
        }
        persistState()
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
        return true
    }

    fun removeCustomSubject(subjectId: String) {
        val current = _state.value
        val target = current.subjectTags.firstOrNull { it.id == subjectId && it.isCustom } ?: return
        val remaining = current.subjectTags.filterNot { it.id == target.id }
        val nextActive = if (current.activeSubjectId == target.id) {
            remaining.firstOrNull()?.id ?: DefaultSubjectTags.first().id
        } else {
            current.activeSubjectId
        }
        _state.update {
            it.copy(
                subjectTags = remaining,
                activeSubjectId = nextActive,
            )
        }
        persistState()
        syncAuxiliarySystems()
    }

    // --- Feature 2: 365-Day History View & CSV Export ---
    fun setShowYearHistory(showYear: Boolean) {
        _state.update { it.copy(showYearHistory = showYear) }
        persistState()
        NativeAdCache.onUserAction()
    }

    fun exportHistoryCsv(context: Context, onLaunchingExternalChooser: () -> Unit = {}) {
        val historySnapshot = synchronized(stateLock) { historyByEpoch.toMap() }
        StudyHistoryExporter.exportAndShareCsv(
            context = context,
            state = _state.value,
            fullHistoryMap = historySnapshot,
            onLaunchingExternalChooser = onLaunchingExternalChooser,
        )
    }

    // --- Feature 3: Built-In Ambient Focus Soundscapes & Custom Music Tracks ---
    fun setMusicEnabled(enabled: Boolean) {
        _state.update { state ->
            val nextTrack = when {
                state.ambientSoundscape == AmbientSoundscape.CUSTOM && state.customMusicUri.isNotBlank() ->
                    AmbientSoundscape.CUSTOM
                state.ambientSoundscape == AmbientSoundscape.OFF ||
                    state.ambientSoundscape == AmbientSoundscape.CUSTOM ->
                    AmbientSoundscape.SOFT_RAIN
                else -> state.ambientSoundscape
            }
            state.copy(
                isMusicEnabled = enabled,
                ambientSoundscape = nextTrack,
            )
        }
        persistState()
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    fun toggleMusic() {
        setMusicEnabled(!_state.value.isMusicEnabled)
    }

    fun selectAmbientSoundscape(soundscape: AmbientSoundscape) {
        if (soundscape == AmbientSoundscape.OFF) {
            _state.update { state ->
                val keptTrack = if (
                    state.ambientSoundscape == AmbientSoundscape.OFF ||
                    (state.ambientSoundscape == AmbientSoundscape.CUSTOM && state.customMusicUri.isBlank())
                ) {
                    AmbientSoundscape.SOFT_RAIN
                } else {
                    state.ambientSoundscape
                }
                state.copy(
                    isMusicEnabled = false,
                    ambientSoundscape = keptTrack,
                )
            }
            persistState()
            syncAuxiliarySystems()
            return
        }
        _state.update {
            it.copy(
                isMusicEnabled = true,
                ambientSoundscape = soundscape,
            )
        }
        persistState()
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    fun selectCustomMusicTrack(uri: String, title: String) {
        val cleanUri = uri.trim()
        if (cleanUri.isEmpty()) return
        val cleanTitle = title.trim().ifEmpty { "Custom track" }.take(42)
        _state.update {
            it.copy(
                isMusicEnabled = true,
                ambientSoundscape = AmbientSoundscape.CUSTOM,
                customMusicUri = cleanUri,
                customMusicTitle = cleanTitle,
            )
        }
        persistState()
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    fun updateAmbientVolume(volume: Float) {
        val clamped = volume.coerceIn(0.05f, 1.0f)
        _state.update { it.copy(ambientVolume = clamped) }
        persistState()
        val current = _state.value
        if (current.isMusicEnabled) {
            syncAuxiliarySystems()
        } else if (!PomodoroAlarmPlayer.isRinging) {
            AmbientSoundPlayer.playPreview(
                soundscape = current.selectedMusicTrack,
                volume = clamped,
                context = appContext,
                customUri = current.customMusicUri,
            )
        }
    }

    fun previewAmbientSoundscape() {
        val current = _state.value
        if (current.isMusicEnabled) {
            syncAuxiliarySystems()
        } else if (!PomodoroAlarmPlayer.isRinging) {
            AmbientSoundPlayer.playPreview(
                soundscape = current.selectedMusicTrack,
                volume = current.ambientVolume,
                context = appContext,
                customUri = current.customMusicUri,
            )
        }
    }

    // --- Feature 4: Focus Mode, Auto Focus & Independent Advanced Focus ---
    fun updateStrictFocus(settings: StrictFocusSettings) {
        val safeAdvanced = if (_state.value.isAdvancedFocusLocked) {
            true
        } else {
            settings.advancedFocusEnabled
        }
        _state.update {
            it.copy(strictFocus = settings.copy(advancedFocusEnabled = safeAdvanced))
        }
        persistState()
    }

    @Suppress("UNUSED_PARAMETER")
    fun setAutoFocusEnabled(enabled: Boolean) {
        _state.update { state ->
            state.copy(
                strictFocus = state.strictFocus.copy(
                    autoFocusEnabled = false,
                ),
            )
        }
        persistState()
    }

    fun setAdvancedFocusEnabled(enabled: Boolean) {
        if (!enabled && _state.value.isAdvancedFocusLocked) return
        _state.update { state ->
            state.copy(
                strictFocus = state.strictFocus.copy(
                    advancedFocusEnabled = enabled,
                ),
                isAdvancedFocusConfirmVisible = false,
            )
        }
        persistState()
    }

    fun setAdvancedFocusConfirmVisible(visible: Boolean) {
        _state.update {
            it.copy(isAdvancedFocusConfirmVisible = visible)
        }
    }

    /**
     * Switches Focus Mode between the per-app blocklist and the legacy strict lockdown.
     * Changing the mode never interrupts a running session; the new policy applies on the
     * next accessibility event, so a user can still stop blocking immediately by choosing
     * [FocusBlockingMode.OFF].
     */
    fun setBlockingMode(mode: FocusBlockingMode) {
        _state.update { state ->
            state.copy(strictFocus = state.strictFocus.copy(blockingMode = mode))
        }
        persistState()
    }

    fun updateBlockedPackages(packages: Set<String>) {
        val sanitized = packages.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_BLOCKED_PACKAGES)
            .toSet()
        _state.update { state ->
            state.copy(strictFocus = state.strictFocus.copy(blockedPackages = sanitized))
        }
        persistState()
    }

    fun setBlockDuringBreaks(enabled: Boolean) {
        _state.update { state ->
            state.copy(strictFocus = state.strictFocus.copy(blockDuringBreaks = enabled))
        }
        persistState()
    }

    /**
     * Grants a single 60-second pass for the current session. The AccessibilityService
     * stops blocking immediately; the pass expires on its own and is cleared when the next
     * session starts.
     */
    fun useEmergencyPass() {
        val current = _state.value
        if (!current.isAppBlockingActive || current.isEmergencyPassActive) return
        _state.update {
            it.copy(
                emergencyPassUsed = true,
                emergencyPassUntilMillis = System.currentTimeMillis() + EMERGENCY_PASS_MILLIS,
            )
        }
        persistState()
        NativeAdCache.onUserAction()
    }

    private fun clearEmergencyPass() {
        _state.update { state ->
            if (state.emergencyPassUsed || state.emergencyPassUntilMillis != 0L) {
                state.copy(emergencyPassUsed = false, emergencyPassUntilMillis = 0L)
            } else {
                state
            }
        }
    }

    fun setFocusModeActive(active: Boolean, force: Boolean = false) {
        if (!active && !force && _state.value.isAdvancedFocusLocked) return
        _state.update {
            it.copy(
                isFocusModeActive = active,
                isAppPinned = active,
            )
        }
        persistState()
        syncAuxiliarySystems()
    }

    fun setAppPinned(pinned: Boolean) = setFocusModeActive(pinned, force = !pinned)

    fun syncPinState() = Unit

    fun refreshPermissionStates() {
        val accessibilityGranted = StrictFocusManager.hasAccessibilityLockPermission(appContext)
        val notificationGranted = StrictFocusManager.hasNotificationPermission(appContext)
        _state.update {
            it.copy(
                notificationPermissionGranted = notificationGranted,
                dndPermissionGranted = true,
                phoneStatePermissionGranted = true,
                accessibilityPermissionGranted = accessibilityGranted,
            )
        }
    }

    fun verifyAndEnforcePermissions() {
        val hasFocusPerms = GlobalPermissionManager.hasAllFocusPermissions(appContext)
        _state.update { state ->
            if (!hasFocusPerms) {
                state.copy(
                    isFocusModeActive = false,
                    isAppPinned = false,
                    strictFocus = state.strictFocus.copy(
                        autoFocusEnabled = false,
                        advancedFocusEnabled = false,
                    ),
                )
            } else {
                state
            }
        }
        persistState()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onPermissionDeclined(type: FocusPermissionType) {
        _state.update { state ->
            state.copy(isAdvancedFocusConfirmVisible = false)
        }
        persistState()
        refreshPermissionStates()
    }

    fun dismissDeclinedPermission() = Unit

    /**
     * When Auto Focus is enabled and any timer starts, automatically turns Focus Mode ON
     * if the required Accessibility permission is granted.
     */
    private fun engageFocusOnTimerStartIfNeeded() = Unit

    // --- Feature 6: Today's Study Task Checklist & Per-Day Archive ---
    fun addTask(title: String, estimatedPomodoros: Int = 1): Boolean {
        ensureDailyReset()
        val current = _state.value
        val cleanTitle = title.trim().take(60)
        if (cleanTitle.isEmpty()) return false
        val nextCreatedCount = maxOf(current.dailyTasksCreatedCount + 1, current.tasks.size + 1)
        val task = StudyTask(
            id = "task_${System.currentTimeMillis()}_$nextCreatedCount",
            title = cleanTitle,
            subjectId = current.activeSubjectId,
            estimatedPomodoros = estimatedPomodoros.coerceIn(1, 12),
            completedPomodoros = 0,
            isCompleted = false,
        )
        val nextTasks = current.tasks + task
        syncTodayTasksIntoArchive(nextTasks)
        _state.update { state ->
            state.copy(
                tasks = nextTasks,
                tasksByDay = copyTasksByDay(),
                activeTaskId = state.activeTaskId ?: task.id,
                dailyTasksCreatedCount = nextCreatedCount,
            )
        }
        persistState()
        NativeAdCache.onUserAction()
        return true
    }

    fun selectActiveTask(taskId: String?) {
        _state.update { it.copy(activeTaskId = taskId) }
        persistState()
        NativeAdCache.onUserAction()
    }

    fun toggleTaskCompleted(taskId: String) {
        ensureDailyReset()
        _state.update { state ->
            val updated = state.tasks.map { task ->
                if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
            }
            syncTodayTasksIntoArchive(updated)
            val nextActive = if (state.activeTaskId == taskId) {
                updated.firstOrNull { !it.isCompleted }?.id
            } else {
                state.activeTaskId
            }
            state.copy(
                tasks = updated,
                tasksByDay = copyTasksByDay(),
                activeTaskId = nextActive,
            )
        }
        persistState()
        NativeAdCache.onUserAction()
    }

    fun deleteTask(taskId: String) {
        ensureDailyReset()
        _state.update { state ->
            val updated = state.tasks.filterNot { it.id == taskId }
            syncTodayTasksIntoArchive(updated)
            val nextActive = if (state.activeTaskId == taskId) {
                updated.firstOrNull { !it.isCompleted }?.id
            } else {
                state.activeTaskId
            }
            state.copy(
                tasks = updated,
                tasksByDay = copyTasksByDay(),
                activeTaskId = nextActive,
            )
        }
        persistState()
        NativeAdCache.onUserAction()
    }

    fun clearCompletedTasks() {
        ensureDailyReset()
        _state.update { state ->
            val remaining = state.tasks.filterNot { it.isCompleted }
            syncTodayTasksIntoArchive(remaining)
            state.copy(
                tasks = remaining,
                tasksByDay = copyTasksByDay(),
                activeTaskId = remaining.firstOrNull { it.id == state.activeTaskId }?.id
                    ?: remaining.firstOrNull()?.id,
            )
        }
        persistState()
    }

    private fun syncTodayTasksIntoArchive(todayTasks: List<StudyTask>) {
        synchronized(stateLock) {
            if (todayTasks.isEmpty()) {
                tasksByDay.remove(dayEpoch)
            } else {
                tasksByDay[dayEpoch] = todayTasks.toList()
            }
        }
    }

    internal fun completeCurrentPhaseForTesting() {
        val current = _state.value
        if (!current.isRunning && !current.isMidSession) return
        if (current.isFlowOvertime) {
            finishFlow()
        } else {
            finishPhase()
        }
    }

    fun dismissCompletion() {
        EndScreenBannerAdManager.onNewTimerCycleStarted()
        PomodoroAlarmPlayer.stop()
        PomodoroNotifications.cancelCompletion(appContext)
        _state.update { it.copy(completion = null) }
        persistState()
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    fun startNext(shouldStartService: Boolean = true) {
        EndScreenBannerAdManager.onNewTimerCycleStarted()
        PomodoroAlarmPlayer.stop()
        PomodoroNotifications.cancelCompletion(appContext)
        start(shouldStartService)
    }

    fun ensureServiceIfNeeded() {
        val current = _state.value
        when {
            current.isRunning -> startService()
            current.isMidSession -> stopServiceAndShowPaused(current)
            else -> {
                PomodoroTimerService.stop(appContext)
                PomodoroNotifications.cancelOngoing(appContext)
            }
        }
    }

    private fun stopServiceAndShowPaused(state: PomodoroUiState) {
        PomodoroTimerService.stop(appContext)
        PomodoroNotifications.showPausedOngoing(appContext, state)
        scope.launch {
            delay(PAUSED_NOTIFICATION_REASSERT_DELAY_MS)
            val latest = _state.value
            if (!latest.isRunning && latest.isMidSession) {
                PomodoroNotifications.showPausedOngoing(appContext, latest)
            }
        }
    }

    fun ensureTicker() {
        ensureDailyReset()
        val current = _state.value
        if (!current.isRunning) return
        if (current.isFlowOvertime) {
            if (flowAnchorElapsed == null) {
                flowAnchorElapsed = SystemClock.elapsedRealtime() - current.flowOvertimeMillis
                persistState()
            }
        } else if (deadlineElapsed == null) {
            val deadline = SystemClock.elapsedRealtime() + current.remainingMillis
            deadlineElapsed = deadline
            persistState()
            scheduleBackupWakeup(deadline)
        }
        if (ticker?.isActive != true) {
            startTicker()
        }
    }

    private fun start(shouldStartService: Boolean = true) {
        ensureDailyReset()
        PomodoroAlarmPlayer.stop()
        val current = _state.value
        if (!current.isMidSession) {
            clearEmergencyPass()
        }
        if (current.isRunning) {
            if (current.completion != null) {
                _state.update { it.copy(completion = null) }
                persistState()
            }
            return
        }

        if (current.isFlowOvertime) {
            flowAnchorElapsed = SystemClock.elapsedRealtime() - current.flowOvertimeMillis
            _state.update { it.copy(isRunning = true, completion = null) }
            engageFocusOnTimerStartIfNeeded()
            PomodoroNotifications.cancelCompletion(appContext)
            persistState()
            startTicker()
            if (shouldStartService) {
                startService()
            }
            NativeAdCache.onUserAction()
            syncAuxiliarySystems()
            return
        }

        val remainingMillis = current.remainingMillis
        if (remainingMillis <= 0L) {
            if (current.completion != null) {
                _state.update { it.copy(completion = null) }
                persistState()
            }
            return
        }

        val deadline = SystemClock.elapsedRealtime() + remainingMillis
        deadlineElapsed = deadline
        if (!current.isMidSession) {
            EndScreenBannerAdManager.onNewTimerCycleStarted()
        }
        _state.update { it.copy(isRunning = true, completion = null) }
        if (current.phase == Phase.Focus) {
            FullscreenVideoAdManager.preloadIfNeeded(appContext)
        }
        EndScreenBannerAdManager.getLoadedAdViewOrSkip()
        if (remainingMillis in 1L..30_000L) {
            EndScreenBannerAdManager.preloadForUpcomingEndScreen(appContext)
        }
        engageFocusOnTimerStartIfNeeded()
        PomodoroNotifications.cancelCompletion(appContext)
        persistState()
        scheduleBackupWakeup(deadline)
        startTicker()
        if (shouldStartService) {
            startService()
        }
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    private fun pause(shouldStartService: Boolean = true) {
        val current = _state.value
        if (current.isFlowOvertime) {
            val anchor = flowAnchorElapsed
            val overtimeMillis = if (anchor != null) {
                (SystemClock.elapsedRealtime() - anchor).coerceAtLeast(current.flowOvertimeMillis)
            } else {
                current.flowOvertimeMillis
            }
            flowAnchorElapsed = null
            stopTicker()
            _state.update {
                it.copy(
                    isRunning = false,
                    flowOvertimeMillis = overtimeMillis,
                )
            }
            bankUnrecordedFlowMinutes(overtimeMillis)
            persistState()
            if (shouldStartService) {
                stopServiceAndShowPaused(_state.value)
            }
            NativeAdCache.onUserAction()
            syncAuxiliarySystems()
            return
        }

        val deadline = deadlineElapsed
        if (deadline != null) {
            val rawRemaining = (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            if (rawRemaining <= 0L) {
                finishPhase()
                return
            }

            // Ensure a started-then-paused timer is always recognized as mid-session (remaining < total)
            val totalForPhase = current.totalMillis
            val remainingMillis = if (rawRemaining >= totalForPhase && totalForPhase > 1L) {
                totalForPhase - 1L
            } else {
                rawRemaining
            }

            deadlineElapsed = null
            cancelBackupWakeup()
            stopTicker()
            updateRemaining(remainingMillis)
            _state.update { it.copy(isRunning = false) }
            persistState()
            if (shouldStartService) {
                stopServiceAndShowPaused(_state.value)
            }
            NativeAdCache.onUserAction()
            syncAuxiliarySystems()
            return
        }

        cancelBackupWakeup()
        stopTicker()
        _state.update { it.copy(isRunning = false) }
        persistState()
        if (shouldStartService) {
            stopServiceAndShowPaused(_state.value)
        }
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    private fun finishPhase(ringAlarm: Boolean = true) {
        ensureDailyReset()
        val current = _state.value

        if (current.phase == Phase.Focus) {
            FullscreenVideoAdManager.preloadIfNeeded(appContext)
        }
        val nextCompletedFocus = if (current.phase == Phase.Focus) {
            current.completedFocus + 1
        } else {
            current.completedFocus
        }

        val addedFocusMinutes = if (current.phase == Phase.Focus) {
            (current.totalMillis / 60_000L).toInt().coerceAtLeast(1)
        } else {
            0
        }

        val nextFocusedMinutes = current.focusedMinutes + addedFocusMinutes

        val nextPhase = if (current.phase == Phase.Focus) {
            if (nextCompletedFocus % SessionsPerCycle == 0) Phase.LongBreak else Phase.ShortBreak
        } else {
            Phase.Focus
        }

        val finishedPhaseDuration = millisFor(current.phase, current.settings)
        val nextPhaseDuration = millisFor(nextPhase, current.settings)

        val updatedTotals = current.sessionTotalMillisByPhase.toMutableMap().apply {
            this[current.phase] = finishedPhaseDuration
            this[nextPhase] = nextPhaseDuration
        }
        val updatedRemaining = current.remainingMillisByPhase.toMutableMap().apply {
            this[current.phase] = finishedPhaseDuration
            this[nextPhase] = nextPhaseDuration
        }

        deadlineElapsed = null
        flowAnchorElapsed = null
        bankedFlowMinutes = 0
        cancelBackupWakeup()
        stopTicker()

        if (addedFocusMinutes > 0) {
            creditSubjectMinutes(dayEpoch, current.activeSubjectId, addedFocusMinutes)
        }
        val updatedTasks = if (current.phase == Phase.Focus) {
            incrementActiveTaskPomodoro(current.tasks, current.activeTaskId)
        } else {
            current.tasks
        }
        syncTodayTasksIntoArchive(updatedTasks)

        val updatedTodayRecord = DailyRhythmRecord(
            dayEpoch = dayEpoch,
            completedFocus = nextCompletedFocus,
            focusedMinutes = nextFocusedMinutes,
        )
        val historySnapshot = synchronized(stateLock) {
            historyByEpoch = archiveAndPruneHistory(
                historyByEpoch,
                updatedTodayRecord,
                dayEpoch,
                HISTORY_WINDOW_DAYS_YEAR,
            )
            historyByEpoch
        }
        val window30 = buildThirtyDayWindow(updatedTodayRecord, historySnapshot, dayEpoch)
        val windowFull = buildHistoryWindow(
            updatedTodayRecord,
            historySnapshot,
            dayEpoch,
            HISTORY_WINDOW_DAYS_YEAR,
        )
        val streak = computeCurrentStreak(windowFull, dayEpoch)

        val finishedState = current.copy(
            phase = nextPhase,
            sessionTotalMillisByPhase = updatedTotals,
            remainingMillisByPhase = updatedRemaining,
            isRunning = false,
            isFlowOvertime = false,
            flowOvertimeMillis = 0L,
            completedFocus = nextCompletedFocus,
            focusedMinutes = nextFocusedMinutes,
            todayEpoch = dayEpoch,
            historyWindow = window30,
            fullHistoryWindow = windowFull,
            currentStreakDays = streak,
            subjectMinutesByDay = copySubjectMap(),
            tasks = updatedTasks,
            tasksByDay = copyTasksByDay(),
            emergencyPassUsed = false,
            emergencyPassUntilMillis = 0L,
            completion = CompletionInfo(
                finished = current.phase,
                next = nextPhase,
                completedFocus = nextCompletedFocus,
                overtimeMinutes = 0,
                bonusBreakMinutes = 0,
                canEnterFlow = current.phase == Phase.Focus,
            ),
        )
        _state.value = finishedState
        persistState()
        PomodoroNotifications.showCompletion(appContext, finishedState)
        if (ringAlarm) {
            PomodoroAlarmPlayer.play(appContext, finishedState.alarm)
        }
        PomodoroTimerService.stop(appContext)
        NativeAdCache.onUserAction()
        syncAuxiliarySystems()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(250L)
                val nowElapsed = SystemClock.elapsedRealtime()
                if (nowElapsed - lastDayCheckElapsed >= 30_000L) {
                    lastDayCheckElapsed = nowElapsed
                    ensureDailyReset()
                }
                val current = _state.value
                if (current.isFlowOvertime) {
                    val anchor = flowAnchorElapsed ?: break
                    val overtimeMillis = (nowElapsed - anchor).coerceAtLeast(0L)
                    val nextOvertimeSeconds = (overtimeMillis / 1000L).toInt()
                    if (
                        current.flowOvertimeSeconds != nextOvertimeSeconds ||
                        current.flowOvertimeMillis == 0L
                    ) {
                        _state.update { it.copy(flowOvertimeMillis = overtimeMillis) }
                    }
                    bankUnrecordedFlowMinutes(overtimeMillis)
                } else {
                    val deadline = deadlineElapsed ?: break
                    val remainingMillis = (deadline - nowElapsed).coerceAtLeast(0L)
                    if (remainingMillis <= 0L) {
                        finishPhase()
                        break
                    }
                    if (current.phase == Phase.Focus && remainingMillis <= 60_000L) {
                        FullscreenVideoAdManager.preloadIfNeeded(appContext)
                    }
                    if (remainingMillis in 1L..30_000L) {
                        EndScreenBannerAdManager.preloadForUpcomingEndScreen(appContext)
                    }
                    val nextRemainingSeconds = ((remainingMillis + 999L) / 1000L).toInt()
                    if (
                        nextRemainingSeconds != current.remaining ||
                        current.remainingMillis == current.totalMillis
                    ) {
                        updateRemaining(remainingMillis)
                    }
                }
            }
        }
    }

    private fun bankUnrecordedFlowMinutes(overtimeMillis: Long) {
        val totalElapsedMinutes = (overtimeMillis / 60_000L).toInt().coerceAtLeast(0)
        val deltaMinutes = totalElapsedMinutes - bankedFlowMinutes
        if (deltaMinutes <= 0) return

        bankedFlowMinutes = totalElapsedMinutes
        val current = _state.value
        creditSubjectMinutes(dayEpoch, current.activeSubjectId, deltaMinutes)
        val nextFocusedMinutes = current.focusedMinutes + deltaMinutes
        val completedFocus = current.completedFocus
        val todayRecord = DailyRhythmRecord(
            dayEpoch = dayEpoch,
            completedFocus = completedFocus,
            focusedMinutes = nextFocusedMinutes,
        )
        val historySnapshot = synchronized(stateLock) {
            historyByEpoch = archiveAndPruneHistory(
                historyByEpoch,
                todayRecord,
                dayEpoch,
                HISTORY_WINDOW_DAYS_YEAR,
            )
            historyByEpoch
        }
        val window30 = buildThirtyDayWindow(todayRecord, historySnapshot, dayEpoch)
        val windowFull = buildHistoryWindow(
            todayRecord,
            historySnapshot,
            dayEpoch,
            HISTORY_WINDOW_DAYS_YEAR,
        )
        val streak = computeCurrentStreak(windowFull, dayEpoch)
        _state.update {
            it.copy(
                flowOvertimeMillis = overtimeMillis,
                focusedMinutes = nextFocusedMinutes,
                todayEpoch = dayEpoch,
                historyWindow = window30,
                fullHistoryWindow = windowFull,
                currentStreakDays = streak,
                subjectMinutesByDay = copySubjectMap(),
            )
        }
        persistState()
    }

    private fun startDayWatcher() {
        scope.launch {
            while (isActive) {
                val delayMillis = nextMidnightDelayMillis(ZonedDateTime.now())
                delay(delayMillis + 500L)
                ensureDailyReset()
            }
        }
    }

    private fun updateRemaining(remainingMillis: Long) {
        val current = _state.value
        if (current.remainingMillisByPhase[current.phase] == remainingMillis) return
        val updated = current.copy(
            remainingMillisByPhase = current.remainingMillisByPhase.toMutableMap().apply {
                this[current.phase] = remainingMillis
            },
        )
        val clockChanged = updated.displayClock != current.displayClock
        _state.value = updated
        // The widget previously refreshed only on discrete actions, so it showed the
        // session start time for the whole countdown. One RemoteViews update per second
        // (when the displayed second changes) keeps it live without IPC churn.
        if (clockChanged) {
            PomodoroAppWidgetProvider.updateAllWidgets(appContext, updated)
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun restoreState(): PomodoroUiState {
        if (!preferences.getBoolean(KEY_FALSE_SEED_CLEANED_V4, false)) {
            preferences.edit()
                .remove(KEY_HISTORY_LEDGER)
                .remove(KEY_SUBJECT_LEDGER)
                .remove(KEY_STUDY_TASKS)
                .remove(KEY_TASKS_BY_DAY_LEDGER)
                .remove(KEY_COMPLETED_FOCUS)
                .remove(KEY_FOCUSED_MINUTES)
                .putBoolean(KEY_FALSE_SEED_CLEANED_V4, true)
                .apply()
        }

        val settings = SessionSettings(
            focus = preferences.getInt(KEY_SETTING_FOCUS, DefaultSettings.focus)
                .coerceIn(SettingBounds.getValue(Phase.Focus).min, SettingBounds.getValue(Phase.Focus).max),
            short = preferences.getInt(KEY_SETTING_SHORT, DefaultSettings.short)
                .coerceIn(SettingBounds.getValue(Phase.ShortBreak).min, SettingBounds.getValue(Phase.ShortBreak).max),
            long = preferences.getInt(KEY_SETTING_LONG, DefaultSettings.long)
                .coerceIn(SettingBounds.getValue(Phase.LongBreak).min, SettingBounds.getValue(Phase.LongBreak).max),
        )

        var phase = preferences.getString(KEY_PHASE, null)
            ?.let { runCatching { Phase.valueOf(it) }.getOrNull() }
            ?: Phase.Focus

        val totals = Phase.entries.associateWith { savedPhase ->
            preferences.getLong(totalKeyFor(savedPhase), millisFor(savedPhase, settings))
                .coerceAtLeast(1L)
        }.toMutableMap()
        val remaining = Phase.entries.associateWith { savedPhase ->
            val total = totals.getValue(savedPhase)
            preferences.getLong(remainingKeyFor(savedPhase), total).coerceIn(0L, total)
        }.toMutableMap()

        val savedRunning = preferences.getBoolean(KEY_IS_RUNNING, false)
        val savedFlowOvertime = preferences.getBoolean(KEY_IS_FLOW_OVERTIME, false)
        var restoredFlowMillis = preferences.getLong(KEY_FLOW_OVERTIME_MILLIS, 0L).coerceAtLeast(0L)
        bankedFlowMinutes = preferences.getInt(KEY_BANKED_FLOW_MINUTES, 0).coerceAtLeast(0)

        val today = currentEpochDay()
        val initialHistory = decodeHistoryLedger(preferences.getString(KEY_HISTORY_LEDGER, null))
        val savedDay = if (preferences.contains(KEY_DAY_EPOCH)) {
            preferences.getLong(KEY_DAY_EPOCH, today)
        } else {
            today
        }
        var rawSavedRecord = DailyRhythmRecord(
            dayEpoch = savedDay,
            completedFocus = preferences.getInt(KEY_COMPLETED_FOCUS, 0),
            focusedMinutes = preferences.getInt(KEY_FOCUSED_MINUTES, 0),
        )

        val savedBootCount = preferences.getInt(KEY_BOOT_COUNT, -1)
        val bootCountNow = currentBootCount()
        val rebooted = savedBootCount >= 0 &&
            bootCountNow >= 0 &&
            savedBootCount != bootCountNow

        var running = savedRunning
        var isFlowOvertime = savedFlowOvertime && savedDay == today
        var finishImmediately = false

        if (!savedFlowOvertime && savedRunning && savedDay < today && !rebooted) {
            val savedDeadline = preferences.getLong(KEY_DEADLINE, -1L).takeIf { it > 0L }
            if (savedDeadline != null && savedDeadline - SystemClock.elapsedRealtime() <= 0L) {
                if (phase == Phase.Focus) {
                    val addedMinutes = (totals.getValue(Phase.Focus) / 60_000L)
                        .toInt()
                        .coerceAtLeast(1)
                    rawSavedRecord = rawSavedRecord.copy(
                        completedFocus = rawSavedRecord.completedFocus + 1,
                        focusedMinutes = rawSavedRecord.focusedMinutes + addedMinutes,
                    )
                }
                running = false
                phase = Phase.Focus
            }
        }

        historyByEpoch = archiveAndPruneHistory(
            initialHistory,
            rawSavedRecord,
            today,
            HISTORY_WINDOW_DAYS_YEAR,
        )

        val dailyRecord = rawSavedRecord.resetIfNeeded(today)
        dayEpoch = dailyRecord.dayEpoch
        val completedFocus = dailyRecord.completedFocus
        var focusedMinutes = dailyRecord.focusedMinutes

        val savedSound = preferences.getString(KEY_ALARM_SOUND, DEFAULT_ALARM_SOUND)
            ?: DEFAULT_ALARM_SOUND
        val alarm = AlarmSettings(
            enabled = preferences.getBoolean(KEY_ALARM_ENABLED, true),
            sound = resolveRestoredAlarmSound(savedSound),
            vibration = preferences.getBoolean(KEY_ALARM_VIBRATION, true),
            durationSeconds = preferences.getInt(KEY_ALARM_DURATION, 20)
                .coerceIn(5, 120),
        )

        if (isFlowOvertime) {
            if (savedRunning && !rebooted) {
                val savedFlowAnchor = preferences.getLong(KEY_FLOW_ANCHOR, -1L).takeIf { it > 0L }
                val nowElapsed = SystemClock.elapsedRealtime()
                if (savedFlowAnchor != null && nowElapsed >= savedFlowAnchor) {
                    restoredFlowMillis = nowElapsed - savedFlowAnchor
                    flowAnchorElapsed = savedFlowAnchor
                    val unbanked = ((restoredFlowMillis / 60_000L).toInt() - bankedFlowMinutes)
                        .coerceAtLeast(0)
                    if (unbanked > 0) {
                        focusedMinutes += unbanked
                        bankedFlowMinutes += unbanked
                    }
                } else {
                    running = false
                }
            } else {
                running = false
            }
        } else if (running) {
            val savedDeadline = preferences.getLong(KEY_DEADLINE, -1L).takeIf { it > 0L }
            if (savedDeadline == null || rebooted) {
                running = false
            } else {
                val remainingMillis = savedDeadline - SystemClock.elapsedRealtime()
                val totalMillis = totals.getValue(phase)
                when {
                    remainingMillis <= 0L -> {
                        remaining[phase] = 0L
                        restoredOverdueMillis = -remainingMillis
                        finishImmediately = true
                    }

                    remainingMillis <= totalMillis -> {
                        remaining[phase] = remainingMillis
                        deadlineElapsed = savedDeadline
                    }

                    else -> {
                        running = false
                    }
                }
            }
        }

        if (savedDay != today && !running && !finishImmediately) {
            isFlowOvertime = false
            restoredFlowMillis = 0L
            bankedFlowMinutes = 0
            Phase.entries.forEach { savedPhase ->
                val defaultMillis = millisFor(savedPhase, settings)
                totals[savedPhase] = defaultMillis
                remaining[savedPhase] = defaultMillis
            }
        }

        if (!running && !finishImmediately) {
            deadlineElapsed = null
            flowAnchorElapsed = null
        }

        val currentTodayRecord = DailyRhythmRecord(
            dayEpoch = dayEpoch,
            completedFocus = completedFocus,
            focusedMinutes = focusedMinutes,
        )
        historyByEpoch = archiveAndPruneHistory(
            historyByEpoch,
            currentTodayRecord,
            dayEpoch,
            HISTORY_WINDOW_DAYS_YEAR,
        )
        val historyWindow = buildThirtyDayWindow(currentTodayRecord, historyByEpoch, dayEpoch)
        val fullHistoryWindow = buildHistoryWindow(
            currentTodayRecord,
            historyByEpoch,
            dayEpoch,
            HISTORY_WINDOW_DAYS_YEAR,
        )
        val streakDays = computeCurrentStreak(fullHistoryWindow, dayEpoch)

        val customTags = decodeCustomSubjectTags(preferences.getString(KEY_CUSTOM_SUBJECTS, null))
        val allSubjectTags = DefaultSubjectTags + customTags
        val savedSubjectId = preferences.getString(KEY_ACTIVE_SUBJECT_ID, null)
            ?.takeIf { id -> allSubjectTags.any { tag -> tag.id == id } }
            ?: DefaultSubjectTags.first().id

        val decodedSubjects = decodeSubjectLedger(preferences.getString(KEY_SUBJECT_LEDGER, null))
        val oldestAllowed = dayEpoch - (HISTORY_WINDOW_DAYS_YEAR - 1)
        synchronized(stateLock) {
            subjectMinutesByDay.clear()
            decodedSubjects.forEach { (epoch, map) ->
                if (epoch in oldestAllowed..dayEpoch) {
                    subjectMinutesByDay[epoch] = map.toMutableMap()
                }
            }
        }

        val rawSavedSoundscape = preferences.getString(KEY_AMBIENT_SOUNDSCAPE, null)
            ?.let { runCatching { AmbientSoundscape.valueOf(it) }.getOrNull() }
        val savedCustomMusicUri = preferences.getString(KEY_CUSTOM_MUSIC_URI, "")?.trim().orEmpty()
            .let { resolveRestoredCustomMusicUri(it) }
        val savedCustomMusicTitle = if (savedCustomMusicUri.isNotBlank()) {
            preferences.getString(KEY_CUSTOM_MUSIC_TITLE, "Custom track")?.trim().orEmpty()
                .ifEmpty { "Custom track" }
        } else {
            ""
        }
        val soundscape = when {
            rawSavedSoundscape == AmbientSoundscape.CUSTOM && savedCustomMusicUri.isNotBlank() ->
                AmbientSoundscape.CUSTOM
            rawSavedSoundscape == null ||
                rawSavedSoundscape == AmbientSoundscape.OFF ||
                rawSavedSoundscape == AmbientSoundscape.CUSTOM ->
                AmbientSoundscape.SOFT_RAIN
            else -> rawSavedSoundscape
        }
        val musicEnabled = if (preferences.contains(KEY_MUSIC_ENABLED)) {
            preferences.getBoolean(KEY_MUSIC_ENABLED, false)
        } else {
            rawSavedSoundscape != null && rawSavedSoundscape != AmbientSoundscape.OFF
        }
        val ambientVolume = preferences.getFloat(KEY_AMBIENT_VOLUME, 0.65f).coerceIn(0.05f, 1.0f)

        val hasFocusPerms = GlobalPermissionManager.hasAllFocusPermissions(appContext)
        val restoredBlockingMode = preferences.getString(KEY_BLOCKING_MODE, null)
            ?.let { runCatching { FocusBlockingMode.valueOf(it) }.getOrNull() }
            ?: FocusBlockingMode.BLOCKLIST
        val restoredBlockedPackages = preferences.getString(KEY_BLOCKED_PACKAGES, null)
            ?.let(AppBlocklistCodec::decode)
            ?: DefaultBlockedPackages
        val strictFocus = StrictFocusSettings(
            isSectionExpanded = preferences.getBoolean(KEY_STRICT_SECTION_EXPANDED, false),
            autoFocusEnabled = false,
            advancedFocusEnabled = hasFocusPerms &&
                preferences.getBoolean(KEY_STRICT_ADVANCED_FOCUS, false),
            blockingMode = restoredBlockingMode,
            blockedPackages = restoredBlockedPackages,
            blockDuringBreaks = preferences.getBoolean(KEY_BLOCK_DURING_BREAKS, false),
        )
        val focusModeActive = hasFocusPerms && preferences.getBoolean(KEY_FOCUS_MODE_ACTIVE, false)

        val decodedTasksByDay = decodeTasksByDayLedger(
            preferences.getString(KEY_TASKS_BY_DAY_LEDGER, null),
        )
        val savedTasksForSavedDay = decodeStudyTasks(preferences.getString(KEY_STUDY_TASKS, null))
            .filterNot { it.id.startsWith("seed_") }
        synchronized(stateLock) {
            tasksByDay.clear()
            decodedTasksByDay.forEach { (epoch, list) ->
                val cleanList = list.filterNot { it.id.startsWith("seed_") }
                if (epoch in oldestAllowed..dayEpoch && cleanList.isNotEmpty()) {
                    tasksByDay[epoch] = cleanList
                }
            }
            if (savedTasksForSavedDay.isNotEmpty() && savedDay in oldestAllowed..dayEpoch) {
                tasksByDay[savedDay] = savedTasksForSavedDay
            }
        }

        val restoredTodayTasks = if (savedDay == today) {
            savedTasksForSavedDay
        } else {
            synchronized(stateLock) { tasksByDay[today].orEmpty() }
        }
        syncTodayTasksIntoArchive(restoredTodayTasks)

        val savedDailyCreatedCount = if (savedDay == today) {
            maxOf(
                preferences.getInt(KEY_DAILY_TASKS_CREATED_COUNT, restoredTodayTasks.size),
                restoredTodayTasks.size,
            )
        } else {
            restoredTodayTasks.size
        }

        val savedActiveTaskId = preferences.getString(KEY_ACTIVE_TASK_ID, null)
            ?.takeIf { id -> restoredTodayTasks.any { it.id == id && !it.isCompleted } }
            ?: restoredTodayTasks.firstOrNull { !it.isCompleted }?.id

        // A pass is only meaningful while it has not expired. Drop stale timestamps so the
        // next session starts with a fresh escape hatch.
        val restoredEmergencyUntil = preferences.getLong(KEY_EMERGENCY_PASS_UNTIL, 0L)
        val restoredEmergencyActive = restoredEmergencyUntil > System.currentTimeMillis()

        return PomodoroUiState(
            settings = settings,
            phase = if (isFlowOvertime) Phase.Focus else phase,
            sessionTotalMillisByPhase = totals,
            remainingMillisByPhase = remaining,
            isRunning = running || finishImmediately,
            isFlowOvertime = isFlowOvertime,
            flowOvertimeMillis = if (isFlowOvertime) restoredFlowMillis else 0L,
            completedFocus = completedFocus,
            focusedMinutes = focusedMinutes,
            todayEpoch = dayEpoch,
            historyWindow = historyWindow,
            fullHistoryWindow = fullHistoryWindow,
            showYearHistory = preferences.getBoolean(KEY_SHOW_YEAR_HISTORY, false),
            currentStreakDays = streakDays,
            alarm = alarm,
            subjectTags = allSubjectTags,
            activeSubjectId = savedSubjectId,
            subjectMinutesByDay = copySubjectMap(),
            isMusicEnabled = musicEnabled,
            ambientSoundscape = soundscape,
            customMusicUri = savedCustomMusicUri,
            customMusicTitle = savedCustomMusicTitle,
            ambientVolume = ambientVolume,
            strictFocus = strictFocus,
            tasks = restoredTodayTasks,
            tasksByDay = copyTasksByDay(),
            activeTaskId = savedActiveTaskId,
            dailyTasksCreatedCount = savedDailyCreatedCount,
            accessibilityPermissionGranted = hasFocusPerms,
            notificationPermissionGranted = StrictFocusManager.hasNotificationPermission(appContext),
            isFocusModeActive = focusModeActive,
            isAppPinned = focusModeActive,
            emergencyPassUsed = restoredEmergencyActive,
            emergencyPassUntilMillis = if (restoredEmergencyActive) restoredEmergencyUntil else 0L,
        )
    }

    private fun resolveRestoredCustomMusicUri(uriString: String): String {
        if (uriString.isBlank()) return ""
        // Do not open the URI here: restore runs in Application.onCreate and a cloud
        // DocumentsProvider can block the main thread for seconds. The player falls back
        // to a built-in soundscape when the URI cannot be opened.
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return ""
        return if (uri.scheme.isNullOrBlank()) "" else uriString
    }

    private fun resolveRestoredAlarmSound(sound: String): String = when (sound) {
        DEFAULT_ALARM_SOUND, SILENT_ALARM_SOUND -> sound
        // The alarm player falls back to the default tone if the persisted URI is gone.
        else -> sound.ifBlank { DEFAULT_ALARM_SOUND }
    }

    private fun persistState() {
        val current = _state.value
        val editor = preferences.edit()

        editor.putInt(KEY_SETTING_FOCUS, current.settings.focus)
        editor.putInt(KEY_SETTING_SHORT, current.settings.short)
        editor.putInt(KEY_SETTING_LONG, current.settings.long)
        editor.putString(KEY_PHASE, current.phase.name)

        Phase.entries.forEach { savedPhase ->
            val totalMillis = current.sessionTotalMillisByPhase[savedPhase]
                ?: millisFor(savedPhase, current.settings)
            editor.putLong(totalKeyFor(savedPhase), totalMillis)
            editor.putLong(
                remainingKeyFor(savedPhase),
                (current.remainingMillisByPhase[savedPhase] ?: totalMillis)
                    .coerceIn(0L, totalMillis),
            )
        }

        editor.putBoolean(KEY_IS_RUNNING, current.isRunning)
        editor.putBoolean(KEY_IS_FLOW_OVERTIME, current.isFlowOvertime)
        editor.putLong(KEY_FLOW_OVERTIME_MILLIS, current.flowOvertimeMillis)
        editor.putLong(KEY_FLOW_ANCHOR, flowAnchorElapsed ?: -1L)
        editor.putInt(KEY_BANKED_FLOW_MINUTES, bankedFlowMinutes)
        editor.putInt(KEY_COMPLETED_FOCUS, current.completedFocus)
        editor.putInt(KEY_FOCUSED_MINUTES, current.focusedMinutes)
        editor.putLong(KEY_DAY_EPOCH, dayEpoch)
        editor.putString(KEY_HISTORY_LEDGER, encodeHistoryLedger(historyByEpoch))
        editor.putBoolean(KEY_ALARM_ENABLED, current.alarm.enabled)
        editor.putString(KEY_ALARM_SOUND, current.alarm.sound)
        editor.putBoolean(KEY_ALARM_VIBRATION, current.alarm.vibration)
        editor.putInt(KEY_ALARM_DURATION, current.alarm.durationSeconds)
        editor.putBoolean(KEY_SHOW_YEAR_HISTORY, current.showYearHistory)
        editor.putString(KEY_ACTIVE_SUBJECT_ID, current.activeSubjectId)
        editor.putString(KEY_CUSTOM_SUBJECTS, encodeCustomSubjectTags(current.subjectTags))
        val oldestEpoch = dayEpoch - (HISTORY_WINDOW_DAYS_YEAR - 1)
        editor.putString(KEY_SUBJECT_LEDGER, encodeSubjectLedger(subjectMinutesByDay, oldestEpoch))
        editor.putBoolean(KEY_MUSIC_ENABLED, current.isMusicEnabled)
        editor.putString(KEY_AMBIENT_SOUNDSCAPE, current.selectedMusicTrack.name)
        editor.putString(KEY_CUSTOM_MUSIC_URI, current.customMusicUri)
        editor.putString(KEY_CUSTOM_MUSIC_TITLE, current.customMusicTitle)
        editor.putFloat(KEY_AMBIENT_VOLUME, current.ambientVolume)
        editor.putBoolean(KEY_STRICT_SECTION_EXPANDED, current.strictFocus.isSectionExpanded)
        editor.putBoolean(KEY_STRICT_AUTO_FOCUS, current.strictFocus.autoFocusEnabled)
        editor.putBoolean(KEY_STRICT_ADVANCED_FOCUS, current.strictFocus.advancedFocusEnabled)
        editor.putString(KEY_BLOCKING_MODE, current.strictFocus.blockingMode.name)
        editor.putString(
            KEY_BLOCKED_PACKAGES,
            AppBlocklistCodec.encode(current.strictFocus.blockedPackages),
        )
        editor.putBoolean(KEY_BLOCK_DURING_BREAKS, current.strictFocus.blockDuringBreaks)
        editor.putBoolean(KEY_EMERGENCY_PASS_USED, current.emergencyPassUsed)
        editor.putLong(KEY_EMERGENCY_PASS_UNTIL, current.emergencyPassUntilMillis)
        editor.putBoolean(KEY_FOCUS_MODE_ACTIVE, current.isFocusEnabled)
        editor.putString(KEY_STUDY_TASKS, encodeStudyTasks(current.tasks))
        editor.putString(KEY_TASKS_BY_DAY_LEDGER, encodeTasksByDayLedger(copyTasksByDay(), oldestEpoch))
        editor.putString(KEY_ACTIVE_TASK_ID, current.activeTaskId ?: "")
        editor.putInt(KEY_DAILY_TASKS_CREATED_COUNT, current.dailyTasksCreatedCount)
        editor.putLong(KEY_DEADLINE, deadlineElapsed ?: -1L)
        editor.putInt(KEY_BOOT_COUNT, currentBootCount())
        editor.apply()
    }

    private fun creditSubjectMinutes(epoch: Long, subjectId: String, minutes: Int) {
        if (minutes <= 0) return
        synchronized(stateLock) {
            val dayMap = subjectMinutesByDay.getOrPut(epoch) { mutableMapOf() }
            dayMap[subjectId] = (dayMap[subjectId] ?: 0) + minutes
        }
    }

    private fun copySubjectMap(): Map<Long, Map<String, Int>> =
        synchronized(stateLock) {
            subjectMinutesByDay.entries.associate { (k, v) -> k to v.toMap() }
        }

    private fun copyTasksByDay(): Map<Long, List<StudyTask>> =
        synchronized(stateLock) {
            tasksByDay.entries.associate { (k, v) -> k to v.toList() }
        }

    private fun syncAuxiliarySystems() {
        val current = _state.value
        AmbientSoundPlayer.syncPlayback(
            soundscape = if (current.isMusicEnabled) current.selectedMusicTrack else AmbientSoundscape.OFF,
            volume = current.ambientVolume,
            isFocusRunning = current.isMusicEnabled,
            isAlarmRinging = PomodoroAlarmPlayer.isRinging,
            context = appContext,
            customUri = current.customMusicUri,
        )
        PomodoroAppWidgetProvider.updateAllWidgets(appContext, current)
    }

    private fun currentBootCount(): Int = runCatching {
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT)
    }.getOrDefault(-1)

    private fun startService() {
        runCatching {
            PomodoroTimerService.start(appContext)
        }.onFailure { error ->
            Log.w(LOG_TAG, "Unable to start the foreground timer service", error)
        }
    }

    private fun scheduleBackupWakeup(deadlineElapsedRealtime: Long) {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = backupWakeupIntent(
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                deadlineElapsedRealtime,
                pendingIntent,
            )
        }.onFailure { error ->
            Log.w(LOG_TAG, "Unable to schedule the backup wake-up", error)
        }
    }

    private fun cancelBackupWakeup() {
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = backupWakeupIntent(
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun backupWakeupIntent(flags: Int): PendingIntent? {
        // Android 12+ blocks background foreground-service starts from inexact alarms, so
        // the wake-up is delivered as a broadcast; the receiver instantiates the engine,
        // whose restore path finishes the overdue session (notification + alarm).
        val intent = Intent(appContext, PomodoroWakeupReceiver::class.java)
        return PendingIntent.getBroadcast(
            appContext,
            BACKUP_WAKEUP_REQUEST_CODE,
            intent,
            flags,
        )
    }

    private fun totalKeyFor(phase: Phase): String = "total_ms_${phase.name}"

    private fun remainingKeyFor(phase: Phase): String = "remaining_ms_${phase.name}"

    companion object {
        private const val LOG_TAG = "PomodoroTimerEngine"
        private const val RESTORED_ALARM_GRACE_MILLIS = 60_000L
        private const val PREFERENCES_NAME = "pomodoro_timer_state"
        private const val KEY_SETTING_FOCUS = "setting_focus"
        private const val KEY_SETTING_SHORT = "setting_short"
        private const val KEY_SETTING_LONG = "setting_long"
        private const val KEY_PHASE = "current_phase"
        private const val KEY_IS_RUNNING = "is_running"
        private const val KEY_IS_FLOW_OVERTIME = "is_flow_overtime"
        private const val KEY_FLOW_OVERTIME_MILLIS = "flow_overtime_millis"
        private const val KEY_FLOW_ANCHOR = "flow_anchor_elapsed"
        private const val KEY_BANKED_FLOW_MINUTES = "banked_flow_minutes"
        private const val KEY_COMPLETED_FOCUS = "completed_focus"
        private const val KEY_FOCUSED_MINUTES = "focused_minutes"
        private const val KEY_DAY_EPOCH = "daily_rhythm_day_epoch"
        private const val KEY_HISTORY_LEDGER = "rhythm_history_ledger_v1"
        private const val KEY_SHOW_YEAR_HISTORY = "show_year_history_v1"
        private const val KEY_ACTIVE_SUBJECT_ID = "active_subject_id_v1"
        private const val KEY_CUSTOM_SUBJECTS = "custom_subject_tags_v1"
        private const val KEY_SUBJECT_LEDGER = "subject_minutes_ledger_v1"
        private const val KEY_MUSIC_ENABLED = "music_enabled_v1"
        private const val KEY_AMBIENT_SOUNDSCAPE = "ambient_soundscape_v1"
        private const val KEY_CUSTOM_MUSIC_URI = "custom_music_uri_v1"
        private const val KEY_CUSTOM_MUSIC_TITLE = "custom_music_title_v1"
        private const val KEY_AMBIENT_VOLUME = "ambient_volume_v1"
        private const val KEY_STRICT_SECTION_EXPANDED = "strict_focus_section_expanded_v2"
        private const val KEY_STRICT_AUTO_FOCUS = "strict_focus_auto_v2"
        private const val KEY_STRICT_ADVANCED_FOCUS = "strict_focus_advanced_v2"
        private const val KEY_BLOCKING_MODE = "focus_blocking_mode_v1"
        private const val KEY_BLOCKED_PACKAGES = "focus_blocked_packages_v1"
        private const val KEY_BLOCK_DURING_BREAKS = "focus_block_during_breaks_v1"
        private const val KEY_EMERGENCY_PASS_USED = "focus_emergency_pass_used_v1"
        private const val KEY_EMERGENCY_PASS_UNTIL = "focus_emergency_pass_until_v1"
        private const val KEY_FOCUS_MODE_ACTIVE = "focus_mode_active_v3"
        private const val KEY_FALSE_SEED_CLEANED_V4 = "false_seed_cleaned_v4"
        private const val KEY_STUDY_TASKS = "study_tasks_v1"
        private const val KEY_TASKS_BY_DAY_LEDGER = "study_tasks_by_day_ledger_v1"
        private const val KEY_ACTIVE_TASK_ID = "active_study_task_id_v1"
        private const val KEY_ALARM_ENABLED = "alarm_enabled"
        private const val KEY_ALARM_SOUND = "alarm_sound"
        private const val KEY_ALARM_VIBRATION = "alarm_vibration"
        private const val KEY_ALARM_DURATION = "alarm_duration_seconds"
        private const val KEY_DEADLINE = "deadline_elapsed"
        private const val KEY_BOOT_COUNT = "boot_count"
        private const val KEY_DAILY_TASKS_CREATED_COUNT = "daily_tasks_created_count_v1"
        private const val BACKUP_WAKEUP_REQUEST_CODE = 99
        private const val PAUSED_NOTIFICATION_REASSERT_DELAY_MS = 400L
        private const val MAX_BLOCKED_PACKAGES = 300
        private const val EMERGENCY_PASS_MILLIS = 60_000L

        @Volatile
        private var instance: PomodoroTimerEngine? = null

        fun initialize(context: Context): PomodoroTimerEngine =
            instance ?: synchronized(this) {
                instance ?: PomodoroTimerEngine(context).also { instance = it }
            }

        fun getInstance(context: Context): PomodoroTimerEngine = initialize(context)
    }
}
