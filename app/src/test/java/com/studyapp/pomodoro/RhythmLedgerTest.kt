package com.studyapp.pomodoro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exhaustive unit tests for Focus Mode, independent Advanced Focus (with Play/Pause allowed
 * and Reset/Focus toggle locked), per-day task archiving and midnight refresh, 30-day & 365-day
 * Rhythm Ledger, session-count heatmap intensity, subject breakdown analytics, CSV export,
 * streak calculation, and Flow Overtime adaptive breaks.
 */
class RhythmLedgerTest {

    @Test
    fun seededSubjectMinutesCoverFiveSubjects() {
        val minutes = buildDefaultSeedSubjectMinutes()

        assertEquals(5, minutes.size)
        assertEquals(320, minutes.getValue("math_cs"))
        assertEquals(800, minutes.values.sum())
    }

    @Test
    fun seededTasksHaveTargetsAndProgress() {
        val tasks = buildDefaultSeedTasks()

        assertEquals(4, tasks.size)
        assertTrue(tasks.all { it.estimatedPomodoros > 0 })
        assertTrue(tasks.all { it.completedPomodoros <= it.estimatedPomodoros })
        assertTrue(tasks.any { it.completedPomodoros == it.estimatedPomodoros })
        assertTrue(tasks.any { it.completedPomodoros < it.estimatedPomodoros })
    }

    @Test
    fun normalFocusModeBlocksChosenAppsAndAllowsManualToggleAndReset() {
        val running = PomodoroUiState(
            isRunning = true,
            phase = Phase.Focus,
            isFocusModeActive = true,
        )

        assertTrue(running.isFocusEnabled)
        // The default policy pauses the user's chosen apps; it never traps navigation.
        assertTrue(running.isAppBlockingActive)
        assertFalse(running.isFocusExitBlocked)
        assertFalse(running.isAdvancedFocusLocked)
        assertTrue(running.canToggleFocusMode)
        assertTrue(running.canReset)
        assertTrue(running.canPressPrimaryTimerButton)
        assertFalse(running.canSelectPhaseTabs)
    }

    @Test
    fun advancedFocusAllowsPlayPauseAndLocksFocusAndResetUntilTimerEnds() {
        val idleAdvanced = PomodoroUiState(
            isRunning = false,
            phase = Phase.Focus,
            isFocusModeActive = true,
            strictFocus = StrictFocusSettings(
                autoFocusEnabled = false,
                advancedFocusEnabled = true,
            ),
        )
        assertFalse(idleAdvanced.isAdvancedFocusLocked)
        assertTrue(idleAdvanced.canToggleFocusMode)
        assertTrue(idleAdvanced.canPressPrimaryTimerButton)

        // Starting Focus timer locks Focus button and Reset button, while Play/Pause stays allowed
        val runningFocus = idleAdvanced.copy(isRunning = true)
        assertTrue(runningFocus.isAdvancedFocusLocked)
        assertTrue(runningFocus.isAppBlockingActive)
        assertFalse(runningFocus.isFocusExitBlocked)
        assertFalse(runningFocus.canToggleFocusMode)
        assertFalse(runningFocus.canReset)
        assertTrue(runningFocus.canPressPrimaryTimerButton)
        assertFalse(runningFocus.canSelectPhaseTabs)

        // Starting Short Break or Long Break timer also locks Focus button and Reset button
        val runningBreak = runningFocus.copy(phase = Phase.ShortBreak)
        assertTrue(runningBreak.isAdvancedFocusLocked)
        assertFalse(runningBreak.canToggleFocusMode)
        assertFalse(runningBreak.canReset)
        assertTrue(runningBreak.canPressPrimaryTimerButton)

        // Paused mid-session remains locked until the timer actually ends
        val pausedMidSession = runningFocus.copy(
            isRunning = false,
            sessionTotalMillisByPhase = mapOf(Phase.Focus to 25 * 60_000L),
            remainingMillisByPhase = mapOf(Phase.Focus to 10 * 60_000L),
        )
        assertTrue(pausedMidSession.isMidSession)
        assertTrue(pausedMidSession.isAdvancedFocusLocked)
        assertFalse(pausedMidSession.canToggleFocusMode)
        assertFalse(pausedMidSession.canReset)
        assertTrue(pausedMidSession.canPressPrimaryTimerButton)

        // Only the explicit Strict policy blocks leaving Pomodoro.
        val strictRunning = runningFocus.copy(
            strictFocus = runningFocus.strictFocus.copy(
                blockingMode = FocusBlockingMode.STRICT,
            ),
        )
        assertTrue(strictRunning.isFocusExitBlocked)
        assertTrue(strictRunning.isAppBlockingActive)
    }

    @Test
    fun flowOvertimeCountsAsAdvancedFocusSession() {
        val flow = PomodoroUiState(
            isRunning = true,
            isFlowOvertime = true,
            isFocusModeActive = true,
            strictFocus = StrictFocusSettings(
                autoFocusEnabled = false,
                advancedFocusEnabled = true,
            ),
        )

        assertTrue(flow.isFocusRunning)
        assertTrue(flow.isFocusSessionActive)
        assertTrue(flow.isAdvancedFocusLocked)
        assertFalse(flow.canToggleFocusMode)
        assertFalse(flow.canReset)
        assertTrue(flow.canPressPrimaryTimerButton)
    }

    @Test
    fun advancedFocusOnlyLocksWhenFocusModeIsEnabled() {
        val advancedWithFocusOff = PomodoroUiState(
            isRunning = true,
            phase = Phase.Focus,
            isFocusModeActive = false,
            strictFocus = StrictFocusSettings(
                advancedFocusEnabled = true,
            ),
        )
        assertFalse(advancedWithFocusOff.isFocusEnabled)
        assertFalse(advancedWithFocusOff.isAdvancedFocusLocked)
        assertTrue(advancedWithFocusOff.canToggleFocusMode)
        assertTrue(advancedWithFocusOff.canReset)
        assertEquals(AppThemeMode.SANCTUARY, advancedWithFocusOff.themeMode)

        val idleWithBothOn = PomodoroUiState(
            isRunning = false,
            phase = Phase.Focus,
            isFocusModeActive = true,
            strictFocus = StrictFocusSettings(
                advancedFocusEnabled = true,
            ),
        )
        assertTrue(idleWithBothOn.requiresAdvancedFocusStartWarning)
        assertEquals(AppThemeMode.ADVANCED_FOCUS, idleWithBothOn.themeMode)

        val idleNormalFocus = PomodoroUiState(
            isRunning = false,
            phase = Phase.Focus,
            isFocusModeActive = true,
            strictFocus = StrictFocusSettings(
                advancedFocusEnabled = false,
            ),
        )
        assertFalse(idleNormalFocus.requiresAdvancedFocusStartWarning)
        assertEquals(AppThemeMode.FOCUS, idleNormalFocus.themeMode)
    }

    @Test
    fun defaultMusicTrackIsSelectedOutOfTheBox() {
        val defaultState = PomodoroUiState()
        assertFalse(defaultState.isMusicEnabled)
        assertEquals(AmbientSoundscape.SOFT_RAIN, defaultState.selectedMusicTrack)
        assertEquals("Soft rain", defaultState.activeMusicTitle)
        assertEquals(11, SelectableBuiltInSoundscapes.size)
        assertTrue(AmbientSoundscape.OCEAN_WAVES in SelectableBuiltInSoundscapes)
        assertTrue(AmbientSoundscape.MOUNTAIN_STREAM in SelectableBuiltInSoundscapes)
        assertTrue(AmbientSoundscape.SINGING_BOWL in SelectableBuiltInSoundscapes)

        val customState = defaultState.copy(
            isMusicEnabled = true,
            ambientSoundscape = AmbientSoundscape.CUSTOM,
            customMusicUri = "content://media/external/audio/media/42",
            customMusicTitle = "Study Lo-Fi",
        )
        assertTrue(customState.isMusicEnabled)
        assertEquals(AmbientSoundscape.CUSTOM, customState.selectedMusicTrack)
        assertEquals("Study Lo-Fi", customState.activeMusicTitle)
    }

    @Test
    fun tasksByDayLedgerEncodesDecodesAndSeparatesCompletedAndIncomplete() {
        val day1 = 20_000L
        val day2 = 20_001L
        val day1Tasks = listOf(
            StudyTask(id = "t1", title = "Linear Algebra", estimatedPomodoros = 3, completedPomodoros = 3, isCompleted = true),
            StudyTask(id = "t2", title = "Read Chapter 5", estimatedPomodoros = 2, completedPomodoros = 1, isCompleted = false),
        )
        val day2Tasks = listOf(
            StudyTask(id = "t3", title = "Lab Report", estimatedPomodoros = 4, completedPomodoros = 0, isCompleted = false),
        )
        val ledger = mapOf(day1 to day1Tasks, day2 to day2Tasks)

        val encoded = encodeTasksByDayLedger(ledger, oldestAllowedEpoch = day1)
        val decoded = decodeTasksByDayLedger(encoded)

        assertEquals(2, decoded.size)
        assertEquals(day1Tasks, decoded[day1])
        assertEquals(day2Tasks, decoded[day2])

        val state = PomodoroUiState(
            todayEpoch = day2,
            tasks = day2Tasks,
            tasksByDay = decoded,
        )
        assertEquals(day1Tasks, state.tasksForDay(day1))
        assertEquals(day2Tasks, state.tasksForDay(day2))
        assertEquals(1, state.tasksForDay(day1).count { it.isCompleted })
        assertEquals(1, state.tasksForDay(day1).count { !it.isCompleted })
    }

    @Test
    fun yearHistoryDistributesAcrossTwelveMonths() {
        val today = 20_000L
        val seeded = buildDefaultSeedHistory(today)
        val todayRecord = DailyRhythmRecord(today, completedFocus = 3, focusedMinutes = 85)

        val months = buildYearHistoryMonths(todayRecord, seeded, todayEpoch = today)

        assertTrue(months.size in 12..13)
        assertTrue(months.all { it.days.isNotEmpty() })
        assertTrue(months.flatMap { it.days }.none {
            it.dayEpoch > today && (it.completedFocus > 0 || it.focusedMinutes > 0)
        })

        val fullWindow = buildHistoryWindow(
            todayRecord = todayRecord,
            history = seeded,
            todayEpoch = today,
            windowDays = HISTORY_WINDOW_DAYS_YEAR,
        )
        assertEquals(365, fullWindow.size)
        assertEquals(today - 364, fullWindow.first().dayEpoch)
        assertEquals(today, fullWindow.last().dayEpoch)
        assertEquals(
            fullWindow.filter { it.dayEpoch <= today }.sumOf { it.completedFocus },
            months.sumOf { it.totalSessions },
        )
        assertEquals(
            fullWindow.filter { it.dayEpoch <= today }.sumOf { it.focusedMinutes },
            months.sumOf { it.totalMinutes },
        )
    }

    @Test
    fun defaultSeedHistoryProducesEighteenDaysWithTwoRestDays() {
        val today = 20_000L
        val seeded = buildDefaultSeedHistory(today)

        assertEquals(16, seeded.size)
        assertEquals(3, seeded[today]?.completedFocus)
        assertEquals(85, seeded[today]?.focusedMinutes)

        assertEquals(3, seeded[today - 17]?.completedFocus)
        assertEquals(80, seeded[today - 17]?.focusedMinutes)

        assertFalse((today - 12) in seeded)
        assertFalse((today - 7) in seeded)
    }

    @Test
    fun seededWindowAnchorsFirstActiveDayAtTopLeftIndexZero() {
        val today = 20_000L
        val seeded = buildDefaultSeedHistory(today)
        val todayRecord = DailyRhythmRecord(today, completedFocus = 3, focusedMinutes = 85)

        val window = buildThirtyDayWindow(todayRecord, seeded, todayEpoch = today)

        assertEquals(HISTORY_WINDOW_DAYS, window.size)
        assertEquals(today - 17, window.first().dayEpoch)
        assertEquals(3, window.first().completedFocus)

        assertEquals(today, window[17].dayEpoch)
        assertEquals(3, window[17].completedFocus)
        assertEquals(85, window[17].focusedMinutes)

        for (i in 18 until HISTORY_WINDOW_DAYS) {
            assertTrue(window[i].dayEpoch > today)
            assertEquals(0, window[i].completedFocus)
            assertEquals(0, window[i].focusedMinutes)
        }
    }

    @Test
    fun seededWindowComputesSevenDayActiveStreak() {
        val today = 20_000L
        val seeded = buildDefaultSeedHistory(today)
        val todayRecord = DailyRhythmRecord(today, completedFocus = 3, focusedMinutes = 85)
        val window = buildThirtyDayWindow(todayRecord, seeded, todayEpoch = today)

        val streak = computeCurrentStreak(window, todayEpoch = today)
        val streakEpochs = computeActiveStreakEpochs(window, todayEpoch = today)

        assertEquals(7, streak)
        assertEquals(7, streakEpochs.size)
        assertTrue(today in streakEpochs)
        assertTrue((today - 6) in streakEpochs)
        assertFalse((today - 7) in streakEpochs)
    }

    @Test
    fun sessionGreenIntensityScalesMonotonicallyWithNumberOfSessions() {
        val restIntensity = sessionGreenIntensity(completedFocus = 0, focusedMinutes = 0, maxSessionsInWindow = 6)
        val s1 = sessionGreenIntensity(completedFocus = 1, focusedMinutes = 25, maxSessionsInWindow = 6)
        val s2 = sessionGreenIntensity(completedFocus = 2, focusedMinutes = 50, maxSessionsInWindow = 6)
        val s3 = sessionGreenIntensity(completedFocus = 3, focusedMinutes = 75, maxSessionsInWindow = 6)
        val s4 = sessionGreenIntensity(completedFocus = 4, focusedMinutes = 100, maxSessionsInWindow = 6)
        val s5 = sessionGreenIntensity(completedFocus = 5, focusedMinutes = 125, maxSessionsInWindow = 6)
        val s6 = sessionGreenIntensity(completedFocus = 6, focusedMinutes = 150, maxSessionsInWindow = 6)

        assertEquals(0f, restIntensity, 0.0001f)
        assertTrue("1 session should be > 0", s1 > 0f)
        assertTrue("2 sessions should be deeper green than 1", s2 > s1)
        assertTrue("3 sessions should be deeper green than 2", s3 > s2)
        assertTrue("4 sessions should be deeper green than 3", s4 > s3)
        assertTrue("5 sessions should be deeper green than 4", s5 > s4)
        assertTrue("6 sessions should reach full intensity", s6 > s5)
        assertEquals(1.0f, s6, 0.0001f)
    }

    @Test
    fun unlockedHistoryRetainsUpTo365Days() {
        val today = 1_000L
        val yearAgoEntry = DayHistoryEntry(dayEpoch = today - 200, completedFocus = 4, focusedMinutes = 100)
        val expiredEntry = DayHistoryEntry(dayEpoch = today - 400, completedFocus = 3, focusedMinutes = 75)
        val initial = mapOf(
            yearAgoEntry.dayEpoch to yearAgoEntry,
            expiredEntry.dayEpoch to expiredEntry,
        )
        val todayRecord = DailyRhythmRecord(today, completedFocus = 2, focusedMinutes = 50)

        val prunedYear = archiveAndPruneHistory(
            existingHistory = initial,
            record = todayRecord,
            todayEpoch = today,
            maxWindowDays = HISTORY_WINDOW_DAYS_YEAR,
        )
        assertTrue("365-day retention keeps 200-day-old entry", (today - 200) in prunedYear)
        assertFalse("365-day retention prunes 400-day-old entry", (today - 400) in prunedYear)
    }

    @Test
    fun subjectBreakdownAndTaskPomodoroIncrementWorkCorrectly() {
        val subjectMap = mapOf(
            100L to mapOf("math_cs" to 60, "science" to 30),
            101L to mapOf("math_cs" to 30, "reading" to 30),
        )
        val breakdown = computeSubjectBreakdown(
            subjectMinutesByDay = subjectMap,
            tags = DefaultSubjectTags,
            windowStartEpoch = 90L,
            todayEpoch = 101L,
        )
        assertEquals(3, breakdown.size)
        assertEquals("math_cs", breakdown[0].tag.id)
        assertEquals(90, breakdown[0].focusedMinutes)
        assertEquals(0.60f, breakdown[0].shareFraction, 0.01f)

        val tasks = listOf(
            StudyTask(id = "t1", title = "Read Chapter 4", estimatedPomodoros = 2, completedPomodoros = 1),
        )
        val afterOneMore = incrementActiveTaskPomodoro(tasks, activeTaskId = "t1")
        assertEquals(2, afterOneMore.first().completedPomodoros)
        assertTrue("Task auto-completes when completedPomodoros reaches estimatedPomodoros", afterOneMore.first().isCompleted)
    }

    @Test
    fun csvExportContainsHeaderAndFormattedRows() {
        val history = mapOf(
            100L to DayHistoryEntry(100L, completedFocus = 3, focusedMinutes = 75),
        )
        val todayRecord = DailyRhythmRecord(100L, completedFocus = 3, focusedMinutes = 75)
        val subjects = mapOf(100L to mapOf("math_cs" to 50, "science" to 25))
        val csv = buildStudyHistoryCsv(
            history = history,
            todayRecord = todayRecord,
            subjectMinutesByDay = subjects,
            tags = DefaultSubjectTags,
            todayEpoch = 100L,
        )
        assertTrue(csv.startsWith("Date,EpochDay,CompletedSessions,FocusedMinutes,Subjects"))
        assertTrue(csv.contains(",100,3,75,\"Math & CS: 50m | Science: 25m\""))
    }

    @Test
    fun streakBreaksWhenYesterdayAndTodayAreRestDays() {
        val today = 500L
        val history = mapOf(
            496L to DayHistoryEntry(496L, completedFocus = 4, focusedMinutes = 100),
            497L to DayHistoryEntry(497L, completedFocus = 3, focusedMinutes = 75),
            498L to DayHistoryEntry(498L, completedFocus = 2, focusedMinutes = 50),
        )
        val todayEmpty = DailyRhythmRecord(today, completedFocus = 0, focusedMinutes = 0)
        val window = buildThirtyDayWindow(todayEmpty, history, todayEpoch = today)

        assertEquals(0, computeCurrentStreak(window, todayEpoch = today))
        assertTrue(computeActiveStreakEpochs(window, todayEpoch = today).isEmpty())
    }

    @Test
    fun fullThirtyDayHistorySlidesWindowToEndOnToday() {
        val today = 1_000L
        val history = (960L..999L).associateWith { epoch ->
            DayHistoryEntry(epoch, completedFocus = 2, focusedMinutes = 50)
        }
        val todayRecord = DailyRhythmRecord(today, completedFocus = 4, focusedMinutes = 100)

        val pruned = archiveAndPruneHistory(history, todayRecord, todayEpoch = today)
        val window = buildThirtyDayWindow(todayRecord, pruned, todayEpoch = today)

        assertEquals(30, window.size)
        assertEquals(today - 29, window.first().dayEpoch)
        assertEquals(today, window.last().dayEpoch)
        assertEquals(30, computeCurrentStreak(window, todayEpoch = today))
    }

    @Test
    fun resettingTodayRemovesTodayFromActiveHistoryAndTotals() {
        val today = 300L
        val history = mapOf(
            299L to DayHistoryEntry(299L, completedFocus = 2, focusedMinutes = 50),
            300L to DayHistoryEntry(300L, completedFocus = 3, focusedMinutes = 75),
        )
        val zeroToday = DailyRhythmRecord(today, completedFocus = 0, focusedMinutes = 0)

        val afterReset = archiveAndPruneHistory(history, zeroToday, todayEpoch = today)
        val window = buildThirtyDayWindow(zeroToday, afterReset, todayEpoch = today)

        assertFalse(today in afterReset)
        assertEquals(2, window.sumOf { it.completedFocus })
        assertEquals(50, window.sumOf { it.focusedMinutes })
        assertEquals(1, computeCurrentStreak(window, todayEpoch = today))
    }

    @Test
    fun decodeHistoryLedgerIgnoresMalformedTokensAndNegativeValues() {
        val raw = "100:3:75;bad_token;101:-2:-10;102:4:100:extra;;103:1:25"
        val decoded = decodeHistoryLedger(raw)

        assertEquals(3, decoded.size)
        assertEquals(DayHistoryEntry(100L, 3, 75), decoded[100L])
        assertEquals(DayHistoryEntry(101L, 0, 0), decoded[101L])
        assertEquals(DayHistoryEntry(103L, 1, 25), decoded[103L])
    }

    @Test
    fun uiStateProgressUsesWholeSecondsAndStaysDeterministic() {
        val totalMs = 25 * 60_000L
        val stateA = PomodoroUiState(
            sessionTotalMillisByPhase = mapOf(Phase.Focus to totalMs),
            remainingMillisByPhase = mapOf(Phase.Focus to totalMs - 100L),
            isRunning = true,
        )
        val stateB = PomodoroUiState(
            sessionTotalMillisByPhase = mapOf(Phase.Focus to totalMs),
            remainingMillisByPhase = mapOf(Phase.Focus to totalMs - 850L),
            isRunning = true,
        )

        assertEquals(1500, stateA.remaining)
        assertEquals(1500, stateB.remaining)
        assertEquals(stateA.progress, stateB.progress, 0.00001f)
    }

    @Test
    fun flowOvertimeStateFormatsCountUpClockAndScalesAdaptiveBreak() {
        val state = PomodoroUiState(
            phase = Phase.Focus,
            isRunning = true,
            isFlowOvertime = true,
            flowOvertimeMillis = 12 * 60_000L + 34_000L,
            completedFocus = 1,
        )

        assertEquals("+12:34", state.displayClock)
        assertEquals(2, state.bonusBreakMinutes)
        assertEquals(Phase.ShortBreak, state.nextBreakPhaseAfterFlow)
        assertEquals(DefaultSettings.short + 2, state.adaptiveNextBreakMinutes)
        assertEquals("Take 7m break", state.finishFlowLabel)
        assertEquals(1f, state.progress, 0.0001f)
    }

    @Test
    fun cycleBoundarySelectsLongBreakAndFillsAllFourDots() {
        val stateAfterFourthFocus = PomodoroUiState(
            phase = Phase.LongBreak,
            isFlowOvertime = false,
            completedFocus = 4,
        )

        assertEquals(SessionsPerCycle, stateAfterFourthFocus.filledDots)
        assertEquals(Phase.LongBreak, stateAfterFourthFocus.nextBreakPhaseAfterFlow)
    }

    @Test
    fun sanitizeSubjectColorHexGuaranteesOpaqueArgb32BitColor() {
        assertEquals(0xFF2B5B84L, sanitizeSubjectColorHex(0L))
        assertEquals(0xFF0F5132L, sanitizeSubjectColorHex(0x000F5132L))
        assertEquals(0xFF8C5A32L, sanitizeSubjectColorHex(0xFF8C5A32L))
    }
}
