package com.studyapp.pomodoro

import com.studyapp.pomodoro.focus.AppBlocklistCodec
import com.studyapp.pomodoro.focus.BlockPolicy
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ruthless JVM edge-case suite: bounds, malformed input, overflow, unicode, DST,
 * empty states, and every derived property of [PomodoroUiState].
 *
 * These tests are intentionally hostile. They must never crash, never produce a
 * negative duration, and never let a malformed persisted token corrupt the ledger.
 */
class RuthlessEdgeCaseTest {

    // ---------------------------------------------------------------- clock

    @Test
    fun formatClockHandlesZeroNegativeAndLargeValues() {
        assertEquals("00:00", formatClock(0))
        assertEquals("00:00", formatClock(-5))
        assertEquals("00:59", formatClock(59))
        assertEquals("01:00", formatClock(60))
        assertEquals("59:59", formatClock(3599))
        assertEquals("60:00", formatClock(3600))
        assertEquals("180:00", formatClock(180 * 60))
        assertEquals("35791394:07", formatClock(Int.MAX_VALUE))
    }

    @Test
    fun formatClockIsLocaleIndependent() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("01:05", formatClock(65))
            Locale.setDefault(Locale.forLanguageTag("fa-IR"))
            assertEquals("25:00", formatClock(25 * 60))
        } finally {
            Locale.setDefault(original)
        }
    }

    // ---------------------------------------------------------------- durations

    @Test
    fun settingBoundsAreSaneAndCoverEveryPhase() {
        assertEquals(3, SettingBounds.size)
        Phase.entries.forEach { phase ->
            val bound = SettingBounds.getValue(phase)
            assertTrue("$phase min must be >= 1", bound.min >= 1)
            assertTrue("$phase max must exceed min", bound.max > bound.min)
            assertTrue("$phase step must be positive", bound.step > 0)
        }
        assertEquals(1, SettingBounds.getValue(Phase.Focus).min)
        assertEquals(180, SettingBounds.getValue(Phase.Focus).max)
        assertEquals(1, SettingBounds.getValue(Phase.ShortBreak).min)
        assertEquals(90, SettingBounds.getValue(Phase.ShortBreak).max)
        assertEquals(1, SettingBounds.getValue(Phase.LongBreak).min)
        assertEquals(120, SettingBounds.getValue(Phase.LongBreak).max)
    }

    @Test
    fun millisAndSecondsMatchEveryPhase() {
        Phase.entries.forEach { phase ->
            val minutes = DefaultSettings.minutesFor(phase)
            assertEquals(minutes * 60, secondsFor(phase, DefaultSettings))
            assertEquals(minutes * 60_000L, millisFor(phase, DefaultSettings))
        }
        assertEquals(1_500_000L, millisFor(Phase.Focus, SessionSettings(focus = 25)))
        assertEquals(60_000L, millisFor(Phase.Focus, SessionSettings(focus = 1)))
        assertEquals(10_800_000L, millisFor(Phase.Focus, SessionSettings(focus = 180)))
    }

    @Test
    fun defaultDurationMapHasEveryPhase() {
        val map = defaultDurationMillisMap()
        assertEquals(Phase.entries.toSet(), map.keys)
        Phase.entries.forEach { phase ->
            assertEquals(millisFor(phase, DefaultSettings), map.getValue(phase))
        }
    }

    @Test
    fun adaptiveBreakBonusBoundariesAndCap() {
        assertEquals(0, adaptiveBreakBonusMinutes(0L))
        assertEquals(0, adaptiveBreakBonusMinutes(-1L))
        assertEquals(0, adaptiveBreakBonusMinutes(299_999L))
        assertEquals(1, adaptiveBreakBonusMinutes(300_000L))
        assertEquals(1, adaptiveBreakBonusMinutes(599_999L))
        assertEquals(2, adaptiveBreakBonusMinutes(600_000L))
        assertEquals(3, adaptiveBreakBonusMinutes(15 * 60_000L))
        assertEquals(14, adaptiveBreakBonusMinutes(74 * 60_000L))
        assertEquals(15, adaptiveBreakBonusMinutes(75 * 60_000L))
        assertEquals(15, adaptiveBreakBonusMinutes(120 * 60_000L))
        assertEquals(15, adaptiveBreakBonusMinutes(10_000 * 60_000L))
    }

    // ---------------------------------------------------------------- intensity

    @Test
    fun sessionGreenIntensityHandlesZeroNegativeAndHuge() {
        assertEquals(0f, sessionGreenIntensity(0, 0, 4), 0.0001f)
        assertEquals(0f, sessionGreenIntensity(-5, 0, 4), 0.0001f)
        assertEquals(0.12f, sessionGreenIntensity(0, 10, 4), 0.0001f)
        assertEquals(0.22f, sessionGreenIntensity(1, 25, 4), 0.0001f)
        assertEquals(1f, sessionGreenIntensity(4, 100, 4), 0.0001f)
        assertEquals(1f, sessionGreenIntensity(100, 2500, 4), 0.0001f)
        assertEquals(1f, sessionGreenIntensity(6, 150, 0), 0.0001f)
        assertTrue(sessionGreenIntensity(2, 50, 6) > sessionGreenIntensity(1, 25, 6))
    }

    // ---------------------------------------------------------------- colors

    @Test
    fun sanitizeSubjectColorHexGuaranteesOpaqueVisibleArgb() {
        assertEquals(0xFF2B5B84L, sanitizeSubjectColorHex(0L))
        assertEquals(0xFF0F5132L, sanitizeSubjectColorHex(0x000F5132L))
        assertEquals(0xFF0F5132L, sanitizeSubjectColorHex(0xFF0F5132L))
        assertEquals(0xFFFFFFFFL, sanitizeSubjectColorHex(0xFFFFFFFFL))
        assertEquals(0xFF2B5B84L, sanitizeSubjectColorHex(0xFF000000L))
        assertEquals(0xFF123456L, sanitizeSubjectColorHex(0x123456L))
        assertEquals(0xFFFFFFFFL, sanitizeSubjectColorHex(-1L))
    }

    // ---------------------------------------------------------------- history windows

    @Test
    fun emptyHistoryWindowStartsAtTodayAndIncludesFutureDays() {
        val window = buildThirtyDayWindow(
            todayRecord = DailyRhythmRecord(100L, 0, 0),
            history = emptyMap(),
            todayEpoch = 100L,
        )
        assertEquals(30, window.size)
        assertEquals(100L, window.first().dayEpoch)
        assertEquals(129L, window.last().dayEpoch)
        assertTrue(window.all { it.completedFocus == 0 && it.focusedMinutes == 0 })
    }

    @Test
    fun historyWindowIgnoresFutureEntriesAndSlidesToEarliestActiveDay() {
        val history = mapOf(
            95L to DayHistoryEntry(95L, 2, 50),
            101L to DayHistoryEntry(101L, 9, 225),
        )
        val window = buildThirtyDayWindow(
            todayRecord = DailyRhythmRecord(100L, 1, 25),
            history = history,
            todayEpoch = 100L,
        )
        assertEquals(30, window.size)
        assertEquals(95L, window.first().dayEpoch)
        assertEquals(124L, window.last().dayEpoch)
        assertEquals(2, window.first { it.dayEpoch == 95L }.completedFocus)
        assertEquals(1, window.first { it.dayEpoch == 100L }.completedFocus)
        assertEquals(0, window.first { it.dayEpoch == 101L }.completedFocus)
    }

    @Test
    fun historyWindowDropsEntriesOlderThanTheWindow() {
        val history = mapOf(
            50L to DayHistoryEntry(50L, 4, 100),
            71L to DayHistoryEntry(71L, 1, 25),
        )
        val window = buildThirtyDayWindow(
            todayRecord = DailyRhythmRecord(100L, 0, 0),
            history = history,
            todayEpoch = 100L,
        )
        assertEquals(71L, window.first().dayEpoch)
        assertEquals(1, window.first().completedFocus)
    }

    @Test
    fun yearWindowIsAlways365AndEndsToday() {
        val window = buildHistoryWindow(
            todayRecord = DailyRhythmRecord(1_000L, 0, 0),
            history = emptyMap(),
            todayEpoch = 1_000L,
            windowDays = HISTORY_WINDOW_DAYS_YEAR,
        )
        assertEquals(365, window.size)
        assertEquals(636L, window.first().dayEpoch)
        assertEquals(1_000L, window.last().dayEpoch)
    }

    @Test
    fun archivePrunesOutsideWindowAndRemovesZeroRecords() {
        val history = mapOf(
            70L to DayHistoryEntry(70L, 4, 100),
            71L to DayHistoryEntry(71L, 2, 50),
            100L to DayHistoryEntry(100L, 3, 75),
        )
        val pruned = archiveAndPruneHistory(
            existingHistory = history,
            record = DailyRhythmRecord(100L, 0, 0),
            todayEpoch = 100L,
            maxWindowDays = 30,
        )
        assertFalse(70L in pruned)
        assertTrue(71L in pruned)
        assertFalse(100L in pruned)
    }

    @Test
    fun archiveCoercesTinyWindowToThirtyDays() {
        val history = mapOf(71L to DayHistoryEntry(71L, 1, 25))
        val pruned = archiveAndPruneHistory(
            existingHistory = history,
            record = DailyRhythmRecord(100L, 0, 0),
            todayEpoch = 100L,
            maxWindowDays = 1,
        )
        assertTrue(71L in pruned)
    }

    // ---------------------------------------------------------------- streaks

    @Test
    fun streakHandlesEmptyGapsAndFutureDays() {
        assertEquals(0, computeCurrentStreak(emptyList(), 100L))

        val gap = listOf(
            DayHistoryEntry(98L, 1, 25),
            DayHistoryEntry(99L, 0, 0),
            DayHistoryEntry(100L, 1, 25),
        )
        assertEquals(1, computeCurrentStreak(gap, 100L))

        val yesterdayAndBefore = listOf(
            DayHistoryEntry(98L, 1, 25),
            DayHistoryEntry(99L, 1, 25),
            DayHistoryEntry(100L, 0, 0),
        )
        assertEquals(2, computeCurrentStreak(yesterdayAndBefore, 100L))

        val stale = listOf(
            DayHistoryEntry(97L, 1, 25),
            DayHistoryEntry(98L, 0, 0),
            DayHistoryEntry(99L, 0, 0),
            DayHistoryEntry(100L, 0, 0),
        )
        assertEquals(0, computeCurrentStreak(stale, 100L))

        val future = listOf(
            DayHistoryEntry(100L, 0, 0),
            DayHistoryEntry(101L, 4, 100),
        )
        assertEquals(0, computeCurrentStreak(future, 100L))
    }

    @Test
    fun streakCountsMinutesOnlyDays() {
        val window = listOf(
            DayHistoryEntry(99L, 0, 12),
            DayHistoryEntry(100L, 0, 8),
        )
        assertEquals(2, computeCurrentStreak(window, 100L))
    }

    // ---------------------------------------------------------------- subjects

    @Test
    fun subjectBreakdownIgnoresUnknownZeroAndNegativeMinutes() {
        val map = mapOf(
            100L to mapOf(
                "math_cs" to 30,
                "ghost" to 10,
                "science" to 0,
                "reading" to -5,
            ),
        )
        val breakdown = computeSubjectBreakdown(
            subjectMinutesByDay = map,
            tags = DefaultSubjectTags,
            windowStartEpoch = 100L,
            todayEpoch = 100L,
        )
        assertEquals(2, breakdown.size)
        assertEquals("math_cs", breakdown[0].tag.id)
        assertEquals(30, breakdown[0].focusedMinutes)
        assertEquals(0.75f, breakdown[0].shareFraction, 0.0001f)
        assertEquals("ghost", breakdown[1].tag.id)
        assertEquals("Ghost", breakdown[1].tag.name)
        assertEquals(0.25f, breakdown[1].shareFraction, 0.0001f)
    }

    @Test
    fun subjectBreakdownShareAlwaysSumsToOne() {
        val map = mapOf(
            100L to mapOf("general" to 10, "math_cs" to 20, "science" to 30),
            101L to mapOf("reading" to 40),
        )
        val breakdown = computeSubjectBreakdown(
            subjectMinutesByDay = map,
            tags = DefaultSubjectTags,
            windowStartEpoch = 100L,
            todayEpoch = 101L,
        )
        assertEquals(4, breakdown.size)
        assertEquals(1f, breakdown.sumOf { it.shareFraction.toDouble() }.toFloat(), 0.0001f)
    }

    @Test
    fun subjectBreakdownFiltersOutsideWindow() {
        val map = mapOf(
            99L to mapOf("math_cs" to 100),
            100L to mapOf("science" to 10),
        )
        val breakdown = computeSubjectBreakdown(
            subjectMinutesByDay = map,
            tags = DefaultSubjectTags,
            windowStartEpoch = 100L,
            todayEpoch = 100L,
        )
        assertEquals(1, breakdown.size)
        assertEquals("science", breakdown[0].tag.id)
    }

    // ---------------------------------------------------------------- CSV

    @Test
    fun csvHasHeaderAndSortedRowsAndExcludesFutureDays() {
        val history = mapOf(
            102L to DayHistoryEntry(102L, 9, 225),
            99L to DayHistoryEntry(99L, 1, 25),
            100L to DayHistoryEntry(100L, 2, 50),
        )
        val csv = buildStudyHistoryCsv(
            history = history,
            todayRecord = DailyRhythmRecord(100L, 2, 50),
            subjectMinutesByDay = emptyMap(),
            tags = DefaultSubjectTags,
            todayEpoch = 100L,
        )
        val lines = csv.trim().lines()
        assertEquals("Date,EpochDay,CompletedSessions,FocusedMinutes,Subjects", lines[0])
        assertEquals(3, lines.size)
        assertTrue(lines[1].startsWith("1970-04-10,99,1,25,"))
        assertTrue(lines[2].startsWith("1970-04-11,100,2,50,"))
    }

    @Test
    fun csvEscapesQuotesInSubjectNames() {
        val tags = DefaultSubjectTags + StudySubjectTag(
            id = "custom_quote",
            name = "He said \"hi\"",
            colorHex = 0xFF123456,
            isCustom = true,
        )
        val csv = buildStudyHistoryCsv(
            history = emptyMap(),
            todayRecord = DailyRhythmRecord(100L, 1, 25),
            subjectMinutesByDay = mapOf(100L to mapOf("custom_quote" to 25)),
            tags = tags,
            todayEpoch = 100L,
        )
        assertTrue(csv.contains("\"\"hi\"\""))
    }

    @Test
    fun csvEmptyLedgerStillWritesTodayRow() {
        val csv = buildStudyHistoryCsv(
            history = emptyMap(),
            todayRecord = DailyRhythmRecord(100L, 0, 0),
            subjectMinutesByDay = emptyMap(),
            tags = DefaultSubjectTags,
            todayEpoch = 100L,
        )
        assertTrue(csv.contains(",100,0,0,"))
        assertTrue(csv.contains("General: 0m"))
    }

    // ---------------------------------------------------------------- codecs

    @Test
    fun historyLedgerRejectsMalformedTokensAndClampsNegatives() {
        val decoded = decodeHistoryLedger(
            "100:3:75;bad_token;101:-2:-10;102:4:100:extra;;103:1:25",
        )
        assertEquals(3, decoded.size)
        assertEquals(DayHistoryEntry(100L, 3, 75), decoded[100L])
        assertEquals(DayHistoryEntry(101L, 0, 0), decoded[101L])
        assertEquals(DayHistoryEntry(103L, 1, 25), decoded[103L])
        assertTrue(decodeHistoryLedger(null).isEmpty())
        assertTrue(decodeHistoryLedger("").isEmpty())
        assertTrue(decodeHistoryLedger("   ").isEmpty())
    }

    @Test
    fun historyLedgerRoundTrips365Entries() {
        val history = (0L until 365L).associateWith { epoch ->
            DayHistoryEntry(epoch, (epoch % 6).toInt(), (epoch % 200).toInt())
        }
        assertEquals(history, decodeHistoryLedger(encodeHistoryLedger(history)))
    }

    @Test
    fun subjectLedgerRejectsMalformedAndNegativeMinutes() {
        val decoded = decodeSubjectLedger(
            "100=math_cs:30,science:0,reading:-5;bad;101=;102=general:10",
        )
        assertEquals(2, decoded.size)
        assertEquals(mapOf("math_cs" to 30), decoded[100L])
        assertEquals(mapOf("general" to 10), decoded[102L])
        assertTrue(decodeSubjectLedger(null).isEmpty())
    }

    @Test
    fun subjectLedgerRoundTrips() {
        val map = mapOf(
            100L to mapOf("math_cs" to 30, "science" to 20),
            101L to mapOf("general" to 5),
        )
        assertEquals(map, decodeSubjectLedger(encodeSubjectLedger(map)))
    }

    @Test
    fun taskCodecSanitizesSeparatorsAndClampsEstimates() {
        val task = StudyTask(
            id = "t1",
            title = "Read | chapter ; 5 ## now => later",
            subjectId = "reading",
            estimatedPomodoros = 100,
            completedPomodoros = -3,
            isCompleted = true,
        )
        val decoded = decodeStudyTasks(encodeStudyTasks(listOf(task))).single()
        assertEquals("t1", decoded.id)
        assertEquals("Read   chapter   5    now    later", decoded.title)
        assertEquals("reading", decoded.subjectId)
        assertEquals(20, decoded.estimatedPomodoros)
        assertEquals(0, decoded.completedPomodoros)
        assertTrue(decoded.isCompleted)
    }

    @Test
    fun taskCodecRejectsMalformedTokens() {
        assertTrue(decodeStudyTasks(null).isEmpty())
        assertTrue(decodeStudyTasks("").isEmpty())
        assertTrue(decodeStudyTasks("bad;t1|title;t2|title|subject|1|0|0|extra").isEmpty())
        assertEquals(1, decodeStudyTasks("t1|title|subject|1|0|0").size)
    }

    @Test
    fun tasksByDayLedgerRoundTripsAndFiltersOldDays() {
        val day1 = listOf(StudyTask("a", "Alpha", estimatedPomodoros = 2))
        val day2 = listOf(StudyTask("b", "Beta", estimatedPomodoros = 3))
        val map = mapOf(100L to day1, 101L to day2)
        assertEquals(map, decodeTasksByDayLedger(encodeTasksByDayLedger(map)))
        assertEquals(
            mapOf(101L to day2),
            decodeTasksByDayLedger(encodeTasksByDayLedger(map, oldestAllowedEpoch = 101L)),
        )
        assertTrue(decodeTasksByDayLedger("bad##102=>").isEmpty())
    }

    @Test
    fun customSubjectCodecSanitizesAndRoundTrips() {
        val tag = StudySubjectTag(
            id = "custom_x",
            name = "Bio | Chem ; Lab",
            colorHex = 0x00123456,
            isCustom = true,
        )
        val decoded = decodeCustomSubjectTags(encodeCustomSubjectTags(listOf(tag))).single()
        assertEquals("custom_x", decoded.id)
        assertEquals("Bio   Chem   Lab", decoded.name)
        assertEquals(0xFF123456L, decoded.colorHex)
        assertTrue(decoded.isCustom)
        assertTrue(decodeCustomSubjectTags(null).isEmpty())
        assertTrue(decodeCustomSubjectTags("bad|token").isEmpty())
    }

    @Test
    fun appBlocklistCodecDedupesSortsAndTrims() {
        val encoded = AppBlocklistCodec.encode(
            setOf(" com.zhihu.android ", "com.instagram.android", "com.instagram.android", ""),
        )
        assertEquals("com.instagram.android\ncom.zhihu.android", encoded)
        assertEquals(
            setOf("com.instagram.android", "com.zhihu.android"),
            AppBlocklistCodec.decode(encoded),
        )
        assertTrue(AppBlocklistCodec.decode(null).isEmpty())
        assertTrue(AppBlocklistCodec.decode(" \n \n").isEmpty())
    }

    // ---------------------------------------------------------------- block policy

    @Test
    fun blockPolicyRejectsBlankAndInactiveStates() {
        val blocked = setOf("com.instagram.android")
        assertFalse(
            BlockPolicy.shouldBlock(
                packageName = "",
                blockedPackages = blocked,
                mode = FocusBlockingMode.BLOCKLIST,
                blockingActive = true,
                emergencyPassActive = false,
                isLauncher = false,
                isInputMethod = false,
            ),
        )
        assertFalse(
            BlockPolicy.shouldBlock(
                packageName = "com.instagram.android",
                blockedPackages = emptySet(),
                mode = FocusBlockingMode.BLOCKLIST,
                blockingActive = true,
                emergencyPassActive = false,
                isLauncher = false,
                isInputMethod = false,
            ),
        )
        assertFalse(
            BlockPolicy.shouldBlock(
                packageName = "com.instagram.android",
                blockedPackages = blocked,
                mode = FocusBlockingMode.BLOCKLIST,
                blockingActive = false,
                emergencyPassActive = false,
                isLauncher = false,
                isInputMethod = false,
            ),
        )
        assertFalse(
            BlockPolicy.shouldBlock(
                packageName = "com.instagram.android",
                blockedPackages = blocked,
                mode = FocusBlockingMode.BLOCKLIST,
                blockingActive = true,
                emergencyPassActive = true,
                isLauncher = false,
                isInputMethod = false,
            ),
        )
    }

    @Test
    fun blockPolicyAlwaysAllowsSafetySurfaces() {
        val safety = listOf(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.settings",
            "com.android.incallui",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.android.keychain",
        )
        safety.forEach { pkg ->
            assertTrue("$pkg must be always allowed", BlockPolicy.isAlwaysAllowed(pkg))
            assertFalse(
                BlockPolicy.shouldBlock(
                    packageName = pkg,
                    blockedPackages = setOf(pkg),
                    mode = FocusBlockingMode.BLOCKLIST,
                    blockingActive = true,
                    emergencyPassActive = false,
                    isLauncher = false,
                    isInputMethod = false,
                ),
            )
        }
    }

    @Test
    fun blockPolicyIsCaseSensitiveForTheBlocklistButNotForSafety() {
        assertTrue(BlockPolicy.isAlwaysAllowed("COM.ANDROID.DIALER"))
        assertFalse(
            BlockPolicy.shouldBlock(
                packageName = "COM.INSTAGRAM.ANDROID",
                blockedPackages = setOf("com.instagram.android"),
                mode = FocusBlockingMode.BLOCKLIST,
                blockingActive = true,
                emergencyPassActive = false,
                isLauncher = false,
                isInputMethod = false,
            ),
        )
    }

    // ---------------------------------------------------------------- dates

    @Test
    fun nextMidnightDelayHandlesNormalAndLeapDays() {
        val utc = ZoneId.of("UTC")
        assertEquals(
            30_000L,
            nextMidnightDelayMillis(ZonedDateTime.of(2026, 10, 24, 23, 59, 30, 0, utc)),
        )
        assertEquals(
            86_400_000L,
            nextMidnightDelayMillis(ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, utc)),
        )
        assertEquals(
            1_000L,
            nextMidnightDelayMillis(ZonedDateTime.of(2028, 2, 28, 23, 59, 59, 0, utc)),
        )
    }

    @Test
    fun nextMidnightDelaySurvivesDstTransitions() {
        val ny = ZoneId.of("America/New_York")
        val spring = nextMidnightDelayMillis(
            ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, ny),
        )
        val fall = nextMidnightDelayMillis(
            ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, ny),
        )
        assertTrue(spring in 1L..(26L * 60L * 60L * 1000L))
        assertTrue(fall in 1L..(26L * 60L * 60L * 1000L))
    }

    // ---------------------------------------------------------------- year months

    @Test
    fun yearMonthsHandleLeapFebruary() {
        val today = LocalDate.of(2028, 3, 1).toEpochDay()
        val months = buildYearHistoryMonths(
            todayRecord = DailyRhythmRecord(today, 2, 50),
            history = emptyMap(),
            todayEpoch = today,
        )
        val feb = months.first { it.year == 2028 && it.month == 2 }
        assertEquals(29, feb.days.size)
        assertTrue(months.size in 12..13)
    }

    @Test
    fun yearMonthsCrossTheYearBoundary() {
        val today = LocalDate.of(2026, 1, 15).toEpochDay()
        val months = buildYearHistoryMonths(
            todayRecord = DailyRhythmRecord(today, 1, 25),
            history = emptyMap(),
            todayEpoch = today,
        )
        assertTrue(months.any { it.year == 2026 && it.month == 1 })
        assertTrue(months.any { it.year == 2025 && it.month == 1 })
        assertTrue(months.size in 12..13)
    }

    // ---------------------------------------------------------------- tasks

    @Test
    fun incrementActiveTaskPomodoroEdgeCases() {
        assertTrue(incrementActiveTaskPomodoro(emptyList(), null).isEmpty())

        val tasks = listOf(
            StudyTask("a", "Alpha", estimatedPomodoros = 2, completedPomodoros = 0),
            StudyTask("b", "Beta", estimatedPomodoros = 3, completedPomodoros = 0),
        )
        assertEquals("a", incrementActiveTaskPomodoro(tasks, null).first().id)
        assertEquals(1, incrementActiveTaskPomodoro(tasks, null).first().completedPomodoros)
        assertEquals("b", incrementActiveTaskPomodoro(tasks, "b").first { it.id == "b" }.id)
        assertEquals(1, incrementActiveTaskPomodoro(tasks, "b").first { it.id == "b" }.completedPomodoros)

        val completedActive = listOf(
            StudyTask("a", "Alpha", estimatedPomodoros = 1, completedPomodoros = 1, isCompleted = true),
            StudyTask("b", "Beta", estimatedPomodoros = 3, completedPomodoros = 0),
        )
        assertEquals(1, incrementActiveTaskPomodoro(completedActive, "a").first { it.id == "b" }.completedPomodoros)

        val allDone = listOf(
            StudyTask("a", "Alpha", estimatedPomodoros = 1, completedPomodoros = 1, isCompleted = true),
        )
        assertEquals(allDone, incrementActiveTaskPomodoro(allDone, "a"))

        val over = listOf(StudyTask("a", "Alpha", estimatedPomodoros = 1, completedPomodoros = 5))
        val after = incrementActiveTaskPomodoro(over, "a").single()
        assertEquals(6, after.completedPomodoros)
        assertTrue(after.isCompleted)
    }

    // ---------------------------------------------------------------- UI state

    @Test
    fun defaultUiStateIsIdleAndGentle() {
        val state = PomodoroUiState()
        assertEquals(Phase.Focus, state.phase)
        assertEquals("25:00", state.displayClock)
        assertEquals(0f, state.progress, 0.0001f)
        assertFalse(state.isMidSession)
        assertFalse(state.isRunning)
        assertFalse(state.canReset)
        assertTrue(state.canSelectPhaseTabs)
        assertTrue(state.canAdjustDuration)
        assertEquals("Start focus", state.actionLabel)
        assertEquals("Session 1 of 4", state.sessionLabel)
        assertEquals("Begin gently. The screen will stay awake while you focus.", state.statusText)
        assertEquals(AppThemeMode.SANCTUARY, state.themeMode)
    }

    @Test
    fun runningAndPausedUiStateDerivations() {
        val total = 25 * 60_000L
        val running = PomodoroUiState(
            isRunning = true,
            sessionTotalMillisByPhase = mapOf(Phase.Focus to total),
            remainingMillisByPhase = mapOf(Phase.Focus to total),
        )
        assertEquals("Pause", running.actionLabel)
        assertEquals("The room is quiet. Your focus is protected.", running.statusText)
        assertFalse(running.canSelectPhaseTabs)

        val paused = running.copy(
            isRunning = false,
            remainingMillisByPhase = mapOf(Phase.Focus to 10 * 60_000L),
        )
        assertTrue(paused.isMidSession)
        assertEquals("Resume", paused.actionLabel)
        assertEquals("Paused. Resume or reset before switching modes.", paused.statusText)
        assertTrue(paused.canReset)
        assertFalse(paused.canSelectPhaseTabs)
        assertFalse(paused.canAdjustDuration)
    }

    @Test
    fun remainingAndProgressClampAtBoundaries() {
        val total = 25 * 60_000L
        val over = PomodoroUiState(
            sessionTotalMillisByPhase = mapOf(Phase.Focus to total),
            remainingMillisByPhase = mapOf(Phase.Focus to total + 99_000L),
        )
        assertEquals(total, over.remainingMillis)
        assertEquals(0f, over.progress, 0.0001f)

        val under = over.copy(remainingMillisByPhase = mapOf(Phase.Focus to -5_000L))
        assertEquals(0L, under.remainingMillis)
        assertEquals(1f, under.progress, 0.0001f)

        val zeroTotal = PomodoroUiState(
            sessionTotalMillisByPhase = mapOf(Phase.Focus to 0L),
            remainingMillisByPhase = mapOf(Phase.Focus to 0L),
        )
        assertEquals(0f, zeroTotal.progress, 0.0001f)
    }

    @Test
    fun flowOvertimeUiStateDerivations() {
        val flow = PomodoroUiState(
            phase = Phase.Focus,
            isRunning = true,
            isFlowOvertime = true,
            flowOvertimeMillis = 12 * 60_000L + 34_000L,
            completedFocus = 1,
        )
        assertEquals("+12:34", flow.displayClock)
        assertEquals(2, flow.bonusBreakMinutes)
        assertEquals(Phase.ShortBreak, flow.nextBreakPhaseAfterFlow)
        assertEquals(DefaultSettings.short + 2, flow.adaptiveNextBreakMinutes)
        assertEquals("Take 7m break", flow.finishFlowLabel)
        assertEquals(1f, flow.progress, 0.0001f)
        assertEquals("Pause flow", flow.actionLabel)
        assertEquals("Flow overtime · +2m break earned", flow.sessionLabel)

        val paused = flow.copy(isRunning = false)
        assertEquals("Resume flow", paused.actionLabel)
        assertEquals("Flow paused. Resume or step into your 7-minute break.", paused.statusText)
    }

    @Test
    fun flowBonusCapsAtFifteenMinutes() {
        val flow = PomodoroUiState(
            isFlowOvertime = true,
            flowOvertimeMillis = 120 * 60_000L,
        )
        assertEquals(15, flow.bonusBreakMinutes)
        assertEquals(DefaultSettings.short + 15, flow.adaptiveNextBreakMinutes)
    }

    @Test
    fun cycleBoundaryAndSessionNumbering() {
        val afterFourth = PomodoroUiState(phase = Phase.LongBreak, completedFocus = 4)
        assertEquals(4, afterFourth.filledDots)
        assertEquals(1, afterFourth.sessionNumber)
        assertEquals(Phase.LongBreak, afterFourth.nextBreakPhaseAfterFlow)

        val fifth = PomodoroUiState(phase = Phase.Focus, completedFocus = 5)
        assertEquals(1, fifth.filledDots)
        assertEquals(2, fifth.sessionNumber)

        val focusAtBoundary = PomodoroUiState(phase = Phase.Focus, completedFocus = 4)
        assertEquals(0, focusAtBoundary.filledDots)

        val flowAtBoundary = PomodoroUiState(
            phase = Phase.Focus,
            completedFocus = 4,
            isFlowOvertime = true,
        )
        assertEquals(4, flowAtBoundary.filledDots)
    }

    @Test
    fun themeModeFollowsFocusAndAdvancedFocus() {
        val off = PomodoroUiState()
        assertEquals(AppThemeMode.SANCTUARY, off.themeMode)

        val focus = off.copy(isFocusModeActive = true)
        assertEquals(AppThemeMode.FOCUS, focus.themeMode)

        val advanced = focus.copy(
            strictFocus = StrictFocusSettings(advancedFocusEnabled = true),
        )
        assertEquals(AppThemeMode.ADVANCED_FOCUS, advanced.themeMode)
    }

    @Test
    fun advancedFocusLockOnlyAppliesWhileFocusIsEnabledAndASessionIsActive() {
        val idle = PomodoroUiState(
            isFocusModeActive = true,
            strictFocus = StrictFocusSettings(advancedFocusEnabled = true),
        )
        assertFalse(idle.isAdvancedFocusLocked)
        assertTrue(idle.requiresAdvancedFocusStartWarning)
        assertTrue(idle.canToggleFocusMode)
        assertTrue(idle.canReset)

        val running = idle.copy(isRunning = true)
        assertTrue(running.isAdvancedFocusLocked)
        assertFalse(running.requiresAdvancedFocusStartWarning)
        assertFalse(running.canToggleFocusMode)
        assertFalse(running.canReset)
        assertTrue(running.canPressPrimaryTimerButton)

        val paused = running.copy(
            isRunning = false,
            sessionTotalMillisByPhase = mapOf(Phase.Focus to 25 * 60_000L),
            remainingMillisByPhase = mapOf(Phase.Focus to 10 * 60_000L),
        )
        assertTrue(paused.isAdvancedFocusLocked)

        val focusOff = running.copy(isFocusModeActive = false)
        assertFalse(focusOff.isAdvancedFocusLocked)
        assertTrue(focusOff.canToggleFocusMode)
    }

    @Test
    fun strictModeBlocksNavigationEvenWhenIdle() {
        // Documents the current behaviour: Strict mode is a whole-Focus-Mode lockdown,
        // not a session-only lockdown. See tests.md finding #1.
        val strictIdle = PomodoroUiState(
            isFocusModeActive = true,
            strictFocus = StrictFocusSettings(blockingMode = FocusBlockingMode.STRICT),
        )
        assertTrue(strictIdle.isFocusExitBlocked)
        assertTrue(strictIdle.isAppBlockingActive)
    }

    @Test
    fun blocklistOnlyBlocksDuringFocusAndOptionalBreaks() {
        val focus = PomodoroUiState(
            phase = Phase.Focus,
            isRunning = true,
            isFocusModeActive = true,
            strictFocus = StrictFocusSettings(blockingMode = FocusBlockingMode.BLOCKLIST),
        )
        assertTrue(focus.isAppBlockingActive)
        assertFalse(focus.isFocusExitBlocked)

        val idle = focus.copy(isRunning = false, remainingMillisByPhase = emptyMap())
        assertFalse(idle.isAppBlockingActive)

        val breakRunning = focus.copy(phase = Phase.ShortBreak)
        assertFalse(breakRunning.isAppBlockingActive)

        val breakLocked = breakRunning.copy(
            strictFocus = breakRunning.strictFocus.copy(blockDuringBreaks = true),
        )
        assertTrue(breakLocked.isAppBlockingActive)

        val off = focus.copy(
            strictFocus = focus.strictFocus.copy(blockingMode = FocusBlockingMode.OFF),
        )
        assertFalse(off.isAppBlockingActive)
    }

    @Test
    fun emergencyPassExpires() {
        val active = PomodoroUiState(
            emergencyPassUsed = true,
            emergencyPassUntilMillis = System.currentTimeMillis() + 60_000L,
        )
        assertTrue(active.isEmergencyPassActive)

        val expired = active.copy(emergencyPassUntilMillis = System.currentTimeMillis() - 1L)
        assertFalse(expired.isEmergencyPassActive)
    }

    @Test
    fun musicFallbacksAndTitles() {
        val off = PomodoroUiState(ambientSoundscape = AmbientSoundscape.OFF)
        assertEquals(AmbientSoundscape.SOFT_RAIN, off.selectedMusicTrack)
        assertEquals("Soft rain", off.activeMusicTitle)

        val customWithoutUri = PomodoroUiState(ambientSoundscape = AmbientSoundscape.CUSTOM)
        assertEquals(AmbientSoundscape.SOFT_RAIN, customWithoutUri.selectedMusicTrack)

        val custom = PomodoroUiState(
            isMusicEnabled = true,
            ambientSoundscape = AmbientSoundscape.CUSTOM,
            customMusicUri = "content://media/external/audio/media/42",
            customMusicTitle = "Study Lo-Fi",
        )
        assertEquals(AmbientSoundscape.CUSTOM, custom.selectedMusicTrack)
        assertEquals("Study Lo-Fi", custom.activeMusicTitle)
        assertEquals("Custom audio track", custom.activeMusicSubtitle)

        val builtIn = PomodoroUiState(ambientSoundscape = AmbientSoundscape.OCEAN_WAVES)
        assertEquals("Ocean waves", builtIn.activeMusicTitle)
        assertEquals("Slow rhythmic coastal surf", builtIn.activeMusicSubtitle)
    }

    @Test
    fun activeTaskFallsBackToFirstIncomplete() {
        val tasks = listOf(
            StudyTask("a", "Alpha", isCompleted = true),
            StudyTask("b", "Beta"),
            StudyTask("c", "Gamma"),
        )
        val state = PomodoroUiState(tasks = tasks, activeTaskId = "a")
        assertEquals("b", state.activeTask?.id)
        assertEquals(2, state.incompleteTasks.size)
        assertEquals(1, state.completedTasks.size)

        val none = PomodoroUiState(tasks = tasks.map { it.copy(isCompleted = true) })
        assertNull(none.activeTask)
    }

    @Test
    fun tasksForDayUsesTodayListAndArchiveForOtherDays() {
        val today = 100L
        val todayTasks = listOf(StudyTask("a", "Alpha"))
        val archived = listOf(StudyTask("b", "Beta"))
        val state = PomodoroUiState(
            todayEpoch = today,
            tasks = todayTasks,
            tasksByDay = mapOf(99L to archived),
        )
        assertEquals(todayTasks, state.tasksForDay(today))
        assertEquals(archived, state.tasksForDay(99L))
        assertTrue(state.tasksForDay(98L).isEmpty())
    }

    @Test
    fun thirtyDayAndYearTotalsUseHistoryWhenPresent() {
        val today = 100L
        val history = listOf(
            DayHistoryEntry(99L, 2, 50),
            DayHistoryEntry(100L, 1, 25),
            DayHistoryEntry(101L, 9, 225),
        )
        val state = PomodoroUiState(
            todayEpoch = today,
            completedFocus = 1,
            focusedMinutes = 25,
            historyWindow = history,
            fullHistoryWindow = history,
        )
        assertEquals(75, state.thirtyDayFocusedMinutes)
        assertEquals(3, state.thirtyDayCompletedSessions)
        assertEquals(75, state.yearFocusedMinutes)
        assertEquals(3, state.yearCompletedSessions)
    }

    @Test
    fun subjectBreakdownUsesYearWindowWhenRequested() {
        val today = 100L
        val map = mapOf(
            50L to mapOf("math_cs" to 100),
            100L to mapOf("science" to 10),
        )
        val month = PomodoroUiState(
            todayEpoch = today,
            subjectMinutesByDay = map,
            showYearHistory = false,
        )
        assertEquals(1, month.subjectBreakdown.size)
        assertEquals("science", month.subjectBreakdown.first().tag.id)

        val year = month.copy(showYearHistory = true)
        assertEquals(2, year.subjectBreakdown.size)
    }

    @Test
    fun defaultTagsAndBlockedPackagesAreUniqueAndNonBlank() {
        assertEquals(6, DefaultSubjectTags.size)
        assertEquals(DefaultSubjectTags.size, DefaultSubjectTags.map { it.id }.distinct().size)
        assertTrue(DefaultSubjectTags.all { it.name.isNotBlank() })
        assertTrue(DefaultSubjectTags.none { it.isCustom })

        assertTrue(DefaultBlockedPackages.isNotEmpty())
        assertTrue(DefaultBlockedPackages.all { it.isNotBlank() })
        assertTrue(DefaultBlockedPackages.all { it.contains(".") })
    }

    @Test
    fun completionInfoDefaultsFlowOnlyForFocus() {
        assertTrue(CompletionInfo(Phase.Focus, Phase.ShortBreak, 1).canEnterFlow)
        assertFalse(CompletionInfo(Phase.ShortBreak, Phase.Focus, 1).canEnterFlow)
        assertFalse(CompletionInfo(Phase.LongBreak, Phase.Focus, 4).canEnterFlow)
    }

    @Test
    fun phaseLabelsAreStable() {
        assertEquals("Focus session", Phase.Focus.heading)
        assertEquals("Short break", Phase.ShortBreak.heading)
        assertEquals("Long break", Phase.LongBreak.heading)
        assertEquals("Focus", Phase.Focus.shortLabel)
        assertEquals("Break", Phase.ShortBreak.shortLabel)
        assertEquals("Long Break", Phase.LongBreak.shortLabel)
    }

    @Test
    fun studyTaskDefaultsAreSafe() {
        val task = StudyTask(id = "x", title = "Title")
        assertEquals("general", task.subjectId)
        assertEquals(1, task.estimatedPomodoros)
        assertEquals(0, task.completedPomodoros)
        assertFalse(task.isCompleted)
    }

    @Test
    fun seededHistoryIsDeterministicAndHasRestDays() {
        val today = 20_000L
        val seeded = buildDefaultSeedHistory(today)
        assertEquals(16, seeded.size)
        assertEquals(3, seeded[today]?.completedFocus)
        assertEquals(85, seeded[today]?.focusedMinutes)
        assertFalse((today - 12) in seeded)
        assertFalse((today - 7) in seeded)
        assertNotNull(seeded[today - 17])
    }
}
