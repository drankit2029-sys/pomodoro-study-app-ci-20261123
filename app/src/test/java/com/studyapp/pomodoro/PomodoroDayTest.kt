package com.studyapp.pomodoro

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroDayTest {

    @Test
    fun countsResetWhenTheDayChanges() {
        val record = DailyRhythmRecord(
            dayEpoch = 10L,
            completedFocus = 3,
            focusedMinutes = 75,
        )

        val reset = record.resetIfNeeded(11L)

        assertEquals(11L, reset.dayEpoch)
        assertEquals(0, reset.completedFocus)
        assertEquals(0, reset.focusedMinutes)
    }

    @Test
    fun countsStayWhenTheDayIsTheSame() {
        val record = DailyRhythmRecord(
            dayEpoch = 10L,
            completedFocus = 2,
            focusedMinutes = 50,
        )

        assertEquals(record, record.resetIfNeeded(10L))
    }

    @Test
    fun aSessionFinishingAfterMidnightStartsFromZero() {
        val beforeMidnight = DailyRhythmRecord(
            dayEpoch = 10L,
            completedFocus = 3,
            focusedMinutes = 75,
        )

        val reset = beforeMidnight.resetIfNeeded(11L)
        val afterFinish = reset.copy(
            completedFocus = reset.completedFocus + 1,
            focusedMinutes = reset.focusedMinutes + 25,
        )

        assertEquals(1, afterFinish.completedFocus)
        assertEquals(25, afterFinish.focusedMinutes)
    }

    @Test
    fun midnightDelayTargetsTheNextLocalMidnight() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 10, 24, 23, 59, 30, 0, zone)

        assertEquals(30_000L, nextMidnightDelayMillis(now))
    }

    @Test
    fun midnightDelayHandlesTheStartOfTheDay() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, zone)

        assertEquals(86_400_000L, nextMidnightDelayMillis(now))
    }

    @Test
    fun adaptiveBreakBonusScalesWithFlowOvertimeAndCapsAtFifteenMinutes() {
        assertEquals(0, adaptiveBreakBonusMinutes(0L))
        assertEquals(0, adaptiveBreakBonusMinutes(4 * 60_000L))
        assertEquals(1, adaptiveBreakBonusMinutes(5 * 60_000L))
        assertEquals(3, adaptiveBreakBonusMinutes(15 * 60_000L))
        assertEquals(15, adaptiveBreakBonusMinutes(120 * 60_000L))
    }

    @Test
    fun freshWindowStartsAtTopLeftIndexZero() {
        val todayRecord = DailyRhythmRecord(
            dayEpoch = 100L,
            completedFocus = 2,
            focusedMinutes = 50,
        )
        val window = buildThirtyDayWindow(todayRecord, emptyMap(), todayEpoch = 100L)

        assertEquals(30, window.size)
        // Top-Left (index 0) is Today when no earlier history exists!
        assertEquals(100L, window.first().dayEpoch)
        assertEquals(2, window.first().completedFocus)
        assertEquals(1, computeCurrentStreak(window, todayEpoch = 100L))
    }

    @Test
    fun archiveAndPruneHistoryKeepsRollingThirtyDayWindow() {
        val initial = mapOf(
            70L to DayHistoryEntry(dayEpoch = 70L, completedFocus = 4, focusedMinutes = 100),
            71L to DayHistoryEntry(dayEpoch = 71L, completedFocus = 2, focusedMinutes = 50),
            99L to DayHistoryEntry(dayEpoch = 99L, completedFocus = 3, focusedMinutes = 75),
        )
        val todayRecord = DailyRhythmRecord(
            dayEpoch = 100L,
            completedFocus = 1,
            focusedMinutes = 25,
        )

        val updated = archiveAndPruneHistory(initial, todayRecord, todayEpoch = 100L)
        val window = buildThirtyDayWindow(todayRecord, updated, todayEpoch = 100L)

        // Day 70 is 31 days ago relative to Day 100 (window is 71..100), so it is pruned.
        assertTrue(70L !in updated)
        assertEquals(2, updated[71L]?.completedFocus)
        assertEquals(1, updated[100L]?.completedFocus)
        assertEquals(30, window.size)
        assertEquals(71L, window.first().dayEpoch)
        assertEquals(100L, window.last().dayEpoch)
    }

    @Test
    fun currentStreakCountsConsecutiveActiveDays() {
        val history = mapOf(
            97L to DayHistoryEntry(97L, completedFocus = 2, focusedMinutes = 50),
            98L to DayHistoryEntry(98L, completedFocus = 4, focusedMinutes = 100),
            99L to DayHistoryEntry(99L, completedFocus = 1, focusedMinutes = 25),
        )
        val todayEmpty = DailyRhythmRecord(dayEpoch = 100L, completedFocus = 0, focusedMinutes = 0)
        val windowBeforeTodaySession = buildThirtyDayWindow(todayEmpty, history, todayEpoch = 100L)
        assertEquals(3, computeCurrentStreak(windowBeforeTodaySession, todayEpoch = 100L))

        val todayActive = DailyRhythmRecord(dayEpoch = 100L, completedFocus = 1, focusedMinutes = 25)
        val windowAfterTodaySession = buildThirtyDayWindow(todayActive, history, todayEpoch = 100L)
        assertEquals(4, computeCurrentStreak(windowAfterTodaySession, todayEpoch = 100L))
    }

    @Test
    fun historyLedgerEncodesAndDecodesCleanly() {
        val original = mapOf(
            95L to DayHistoryEntry(95L, completedFocus = 2, focusedMinutes = 50),
            98L to DayHistoryEntry(98L, completedFocus = 5, focusedMinutes = 135),
        )
        val encoded = encodeHistoryLedger(original)
        val decoded = decodeHistoryLedger(encoded)

        assertEquals(original, decoded)
    }
}
