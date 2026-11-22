package com.studyapp.pomodoro

import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

const val HISTORY_WINDOW_DAYS = 30
const val HISTORY_WINDOW_DAYS_YEAR = 365

data class DailyRhythmRecord(
    val dayEpoch: Long,
    val completedFocus: Int,
    val focusedMinutes: Int,
) {
    fun resetIfNeeded(todayEpoch: Long): DailyRhythmRecord =
        if (dayEpoch == todayEpoch) {
            this
        } else {
            copy(
                dayEpoch = todayEpoch,
                completedFocus = 0,
                focusedMinutes = 0,
            )
        }
}

data class DayHistoryEntry(
    val dayEpoch: Long,
    val completedFocus: Int,
    val focusedMinutes: Int,
)

/**
 * Represents a color-coded study subject or project tag.
 */
data class StudySubjectTag(
    val id: String,
    val name: String,
    val colorHex: Long,
    val isCustom: Boolean = false,
)

val DefaultSubjectTags: List<StudySubjectTag> = listOf(
    StudySubjectTag(id = "general", name = "General", colorHex = 0xFF0F5132L),
    StudySubjectTag(id = "math_cs", name = "Math & CS", colorHex = 0xFF2B5B84L),
    StudySubjectTag(id = "science", name = "Science", colorHex = 0xFF2E6F5EL),
    StudySubjectTag(id = "reading", name = "Reading", colorHex = 0xFF8C5A32L),
    StudySubjectTag(id = "writing", name = "Writing", colorHex = 0xFF6E4B7AL),
    StudySubjectTag(id = "languages", name = "Languages", colorHex = 0xFF996515L),
)

data class SubjectAllocationSummary(
    val tag: StudySubjectTag,
    val focusedMinutes: Int,
    val shareFraction: Float,
)

/**
 * Normalizes a custom subject color hex into a valid, opaque 32-bit ARGB color (`0xFFRRGGBB`)
 * so `androidx.compose.ui.graphics.Color(Long)` never crashes or renders transparent tags.
 */
fun sanitizeSubjectColorHex(colorHex: Long): Long {
    val masked = colorHex and 0xFFFFFFFFL
    val opaque = if ((masked ushr 24) == 0L) masked or 0xFF000000L else masked
    return if ((opaque and 0x00FFFFFFL) == 0L) 0xFF2B5B84L else opaque
}

enum class AppThemeMode {
    SANCTUARY,
    FOCUS,
    ADVANCED_FOCUS,
}

enum class AmbientSoundscape(
    val label: String,
    val subtitle: String,
) {
    OFF("Off", "Quiet sanctuary"),
    SOFT_RAIN("Soft rain", "Gentle steady rainfall"),
    OCEAN_WAVES("Ocean waves", "Slow rhythmic coastal surf"),
    MOUNTAIN_STREAM("Mountain stream", "Babbling brook over smooth stones"),
    FOREST_WIND("Forest wind", "Breeze through botanical canopy"),
    FIREPLACE("Warm hearth", "Gentle crackle & ember warmth"),
    LIBRARY("Library murmur", "Soft room tone & study hush"),
    NIGHT_MEADOW("Night meadow", "Warm dusk air & distant crickets"),
    DISTANT_THUNDER("Rain & thunder", "Steady rain with distant rumbles"),
    SINGING_BOWL("Zen drone", "Warm harmonic meditation resonance"),
    PINK_NOISE("Pink noise", "Balanced octave-filtered study mask"),
    BROWN_NOISE("Brown noise", "Deep warm low-frequency hush"),
    CUSTOM("Custom track", "Personal audio track"),
}

val SelectableBuiltInSoundscapes: List<AmbientSoundscape> = listOf(
    AmbientSoundscape.SOFT_RAIN,
    AmbientSoundscape.OCEAN_WAVES,
    AmbientSoundscape.MOUNTAIN_STREAM,
    AmbientSoundscape.FOREST_WIND,
    AmbientSoundscape.FIREPLACE,
    AmbientSoundscape.LIBRARY,
    AmbientSoundscape.NIGHT_MEADOW,
    AmbientSoundscape.DISTANT_THUNDER,
    AmbientSoundscape.SINGING_BOWL,
    AmbientSoundscape.PINK_NOISE,
    AmbientSoundscape.BROWN_NOISE,
)

/**
 * How Focus Mode protects a session.
 *
 * - [OFF]: Focus Mode only changes the app's appearance; nothing is blocked.
 * - [BLOCKLIST]: only the user's chosen apps are paused while a focus session runs.
 *   Everything else (notes, PDFs, calculators, messaging with a study group) keeps working.
 * - [STRICT]: the legacy full-device lockdown. Leaving Pomodoro is blocked for the whole
 *   session. Kept as an explicit opt-in for users who want it.
 */
enum class FocusBlockingMode {
    OFF,
    BLOCKLIST,
    STRICT,
}

/**
 * Apps most people want out of the way while studying. These are package names, so the
 * defaults survive uninstalls/reinstalls and do not depend on device locale. Missing
 * packages simply never match; the picker shows what is actually installed.
 */
val DefaultBlockedPackages: Set<String> = setOf(
    "com.instagram.android",
    "com.zhiliaoapp.musically",
    "com.ss.android.ugc.trill",
    "com.google.android.youtube",
    "com.facebook.katana",
    "com.twitter.android",
    "com.reddit.frontpage",
    "com.snapchat.android",
    "com.netflix.mediaclient",
    "com.discord",
    "com.pinterest",
    "com.linkedin.android",
)

/**
 * Focus Mode settings:
 * - [isSectionExpanded]: UI menu state.
 * - [autoFocusEnabled]: Retired (always false; normal Focus Mode handles focus protection).
 * - [advancedFocusEnabled]: Modifies Focus Mode so that when Focus Mode is enabled and any
 *   timer starts, exiting the app, turning off Focus Mode, and resetting the timer are locked
 *   until that timer ends.
 * - [blockingMode]: off, per-app blocklist, or the legacy strict lockdown.
 * - [blockedPackages]: package names paused during a focus session in [FocusBlockingMode.BLOCKLIST].
 * - [blockDuringBreaks]: keep the blocklist active during short/long breaks too.
 */
data class StrictFocusSettings(
    val isSectionExpanded: Boolean = false,
    val autoFocusEnabled: Boolean = false,
    val advancedFocusEnabled: Boolean = false,
    val blockingMode: FocusBlockingMode = FocusBlockingMode.BLOCKLIST,
    val blockedPackages: Set<String> = DefaultBlockedPackages,
    val blockDuringBreaks: Boolean = false,
)

/** Permissions managed by [com.studyapp.pomodoro.focus.GlobalPermissionManager]. */
enum class FocusPermissionType {
    ACCESSIBILITY_LOCK,
    NOTIFICATIONS,
}

/**
 * Pre-permission user prompt shown by [com.studyapp.pomodoro.focus.GlobalPermissionManager]
 * explaining what to do and why a permission is needed before navigating to the Accessibility
 * menu or triggering Android's runtime permission modal.
 */
data class PermissionPromptState(
    val permissionType: FocusPermissionType,
    val title: String,
    val whyNeeded: String,
    val whatToDo: String,
    val confirmLabel: String,
)

/** A calendar month of the 365-day ledger, with weekday alignment and monthly totals. */
data class MonthHistoryGroup(
    val year: Int,
    val month: Int,
    val title: String,
    val days: List<DayHistoryEntry>,
    val leadingEmptySlots: Int,
    val totalSessions: Int,
    val totalMinutes: Int,
)

private val MonthHeaderFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

/**
 * Distributes the 365-day history window (ending on [todayEpoch]) into calendar months
 * ordered from most recent month to oldest month, with each month containing its exact
 * calendar days within the window and the weekday offset needed for a 7-column grid.
 */
fun buildYearHistoryMonths(
    todayRecord: DailyRhythmRecord,
    history: Map<Long, DayHistoryEntry>,
    todayEpoch: Long = todayRecord.dayEpoch,
): List<MonthHistoryGroup> {
    val startEpoch = todayEpoch - (HISTORY_WINDOW_DAYS_YEAR - 1)
    val startDate = LocalDate.ofEpochDay(startEpoch)
    val endDate = LocalDate.ofEpochDay(todayEpoch)
    val startYm = YearMonth.from(startDate)
    val endYm = YearMonth.from(endDate)

    val months = mutableListOf<MonthHistoryGroup>()
    var currentYm = endYm
    while (!currentYm.isBefore(startYm)) {
        val firstOfMonth = currentYm.atDay(1)
        val daysInMonth = (1..currentYm.lengthOfMonth()).map { dayOfMonth ->
            val epoch = currentYm.atDay(dayOfMonth).toEpochDay()
            when {
                epoch == todayRecord.dayEpoch -> DayHistoryEntry(
                    dayEpoch = epoch,
                    completedFocus = todayRecord.completedFocus.coerceAtLeast(0),
                    focusedMinutes = todayRecord.focusedMinutes.coerceAtLeast(0),
                )
                epoch in startEpoch..todayEpoch -> history[epoch] ?: DayHistoryEntry(
                    dayEpoch = epoch,
                    completedFocus = 0,
                    focusedMinutes = 0,
                )
                else -> DayHistoryEntry(
                    dayEpoch = epoch,
                    completedFocus = 0,
                    focusedMinutes = 0,
                )
            }
        }
        val activeDaysInWindow = daysInMonth.filter { it.dayEpoch in startEpoch..todayEpoch }
        val leadingOffset = firstOfMonth.dayOfWeek.value - 1
        months.add(
            MonthHistoryGroup(
                year = currentYm.year,
                month = currentYm.monthValue,
                title = firstOfMonth.format(MonthHeaderFormatter).uppercase(Locale.US),
                days = daysInMonth,
                leadingEmptySlots = leadingOffset,
                totalSessions = activeDaysInWindow.sumOf { it.completedFocus },
                totalMinutes = activeDaysInWindow.sumOf { it.focusedMinutes },
            ),
        )
        currentYm = currentYm.minusMonths(1)
    }
    return months
}

internal fun buildDefaultSeedSubjectMinutes(): Map<String, Int> = linkedMapOf(
    "math_cs" to 320,
    "science" to 185,
    "general" to 140,
    "reading" to 95,
    "writing" to 60,
)

internal fun buildDefaultSeedTasks(): List<StudyTask> = listOf(
    StudyTask(
        id = "seed_coding",
        title = "Build the revision planner",
        subjectId = "math_cs",
        estimatedPomodoros = 4,
        completedPomodoros = 3,
    ),
    StudyTask(
        id = "seed_design",
        title = "Sketch the dashboard layout",
        subjectId = "science",
        estimatedPomodoros = 2,
        completedPomodoros = 2,
    ),
    StudyTask(
        id = "seed_reading",
        title = "Read chapter 6 notes",
        subjectId = "reading",
        estimatedPomodoros = 3,
        completedPomodoros = 1,
    ),
    StudyTask(
        id = "seed_math",
        title = "Practice integration drills",
        subjectId = "general",
        estimatedPomodoros = 5,
        completedPomodoros = 2,
    ),
)

/**
 * A study checklist task with estimated and completed Pomodoro session counts.
 */
data class StudyTask(
    val id: String,
    val title: String,
    val subjectId: String = "general",
    val estimatedPomodoros: Int = 1,
    val completedPomodoros: Int = 0,
    val isCompleted: Boolean = false,
)

fun currentEpochDay(): Long = LocalDate.now().toEpochDay()

fun nextMidnightDelayMillis(now: ZonedDateTime): Long {
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
    return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L)
}

fun sessionGreenIntensity(
    completedFocus: Int,
    focusedMinutes: Int = 0,
    maxSessionsInWindow: Int = SessionsPerCycle,
): Float {
    if (completedFocus <= 0) {
        return if (focusedMinutes > 0) 0.12f else 0f
    }
    val effectiveCap = maxOf(SessionsPerCycle + 2, maxSessionsInWindow, completedFocus)
    if (effectiveCap <= 1) return 1f
    val normalized = (completedFocus - 1).toFloat() / (effectiveCap - 1).toFloat()
    return (0.22f + normalized * 0.78f).coerceIn(0.22f, 1f)
}

fun archiveAndPruneHistory(
    existingHistory: Map<Long, DayHistoryEntry>,
    record: DailyRhythmRecord,
    todayEpoch: Long,
    maxWindowDays: Int = HISTORY_WINDOW_DAYS,
): Map<Long, DayHistoryEntry> {
    val safeWindowDays = maxWindowDays.coerceAtLeast(HISTORY_WINDOW_DAYS)
    val oldestAllowedEpoch = todayEpoch - (safeWindowDays - 1)
    val mutable = existingHistory
        .filterKeys { epoch -> epoch in oldestAllowedEpoch..todayEpoch }
        .toMutableMap()

    if (record.dayEpoch in oldestAllowedEpoch..todayEpoch) {
        if (record.completedFocus > 0 || record.focusedMinutes > 0) {
            mutable[record.dayEpoch] = DayHistoryEntry(
                dayEpoch = record.dayEpoch,
                completedFocus = record.completedFocus.coerceAtLeast(0),
                focusedMinutes = record.focusedMinutes.coerceAtLeast(0),
            )
        } else {
            mutable.remove(record.dayEpoch)
        }
    }
    return mutable
}

fun buildHistoryWindow(
    todayRecord: DailyRhythmRecord,
    history: Map<Long, DayHistoryEntry>,
    todayEpoch: Long = todayRecord.dayEpoch,
    windowDays: Int = HISTORY_WINDOW_DAYS,
): List<DayHistoryEntry> {
    val safeDays = windowDays.coerceAtLeast(1)
    val oldestAllowed = todayEpoch - (safeDays - 1)
    val earliestActiveEpoch = history.entries
        .filter {
            it.key >= oldestAllowed &&
                (it.value.completedFocus > 0 || it.value.focusedMinutes > 0)
        }
        .minOfOrNull { it.key }
        ?.coerceAtMost(todayEpoch)
        ?: todayEpoch

    val activeSpanDays = (todayEpoch - earliestActiveEpoch + 1L).toInt()
    val startEpoch = if (safeDays >= HISTORY_WINDOW_DAYS_YEAR) {
        todayEpoch - (safeDays - 1)
    } else if (activeSpanDays <= safeDays) {
        earliestActiveEpoch
    } else {
        todayEpoch - (safeDays - 1)
    }

    return (0 until safeDays).map { offset ->
        val epoch = startEpoch + offset
        when {
            epoch == todayRecord.dayEpoch -> DayHistoryEntry(
                dayEpoch = epoch,
                completedFocus = todayRecord.completedFocus.coerceAtLeast(0),
                focusedMinutes = todayRecord.focusedMinutes.coerceAtLeast(0),
            )
            epoch <= todayEpoch -> history[epoch] ?: DayHistoryEntry(
                dayEpoch = epoch,
                completedFocus = 0,
                focusedMinutes = 0,
            )
            else -> DayHistoryEntry(
                dayEpoch = epoch,
                completedFocus = 0,
                focusedMinutes = 0,
            )
        }
    }
}

fun buildThirtyDayWindow(
    todayRecord: DailyRhythmRecord,
    history: Map<Long, DayHistoryEntry>,
    todayEpoch: Long = todayRecord.dayEpoch,
): List<DayHistoryEntry> = buildHistoryWindow(
    todayRecord = todayRecord,
    history = history,
    todayEpoch = todayEpoch,
    windowDays = HISTORY_WINDOW_DAYS,
)

fun computeCurrentStreak(
    window: List<DayHistoryEntry>,
    todayEpoch: Long = currentEpochDay(),
): Int {
    return computeActiveStreakEpochs(window, todayEpoch).size
}

fun computeActiveStreakEpochs(
    window: List<DayHistoryEntry>,
    todayEpoch: Long = currentEpochDay(),
): Set<Long> {
    if (window.isEmpty()) return emptySet()
    val maxPastEpoch = window.maxOf { it.dayEpoch }.coerceAtMost(todayEpoch)
    val eligible = window
        .filter { it.dayEpoch <= maxPastEpoch }
        .sortedBy { it.dayEpoch }
    if (eligible.isEmpty()) return emptySet()

    val lastIndex = eligible.lastIndex
    val startIndex = when {
        eligible[lastIndex].completedFocus > 0 || eligible[lastIndex].focusedMinutes > 0 ->
            lastIndex
        lastIndex >= 1 &&
            (eligible[lastIndex - 1].completedFocus > 0 ||
                eligible[lastIndex - 1].focusedMinutes > 0) ->
            lastIndex - 1
        else -> return emptySet()
    }

    val streakEpochs = LinkedHashSet<Long>()
    var expectedEpoch = eligible[startIndex].dayEpoch
    for (i in startIndex downTo 0) {
        val entry = eligible[i]
        if (entry.dayEpoch != expectedEpoch) break
        if (entry.completedFocus > 0 || entry.focusedMinutes > 0) {
            streakEpochs.add(entry.dayEpoch)
            expectedEpoch--
        } else {
            break
        }
    }
    return streakEpochs
}

fun computeSubjectBreakdown(
    subjectMinutesByDay: Map<Long, Map<String, Int>>,
    tags: List<StudySubjectTag>,
    windowStartEpoch: Long,
    todayEpoch: Long,
): List<SubjectAllocationSummary> {
    val totalsByTag = mutableMapOf<String, Int>()
    subjectMinutesByDay.forEach { (epoch, dayMap) ->
        if (epoch in windowStartEpoch..todayEpoch) {
            dayMap.forEach { (tagId, mins) ->
                if (mins > 0) {
                    totalsByTag[tagId] = (totalsByTag[tagId] ?: 0) + mins
                }
            }
        }
    }
    val grandTotal = totalsByTag.values.sum()
    if (grandTotal <= 0) return emptyList()

    val tagById = tags.associateBy { it.id }
    val fallbackTag = tags.firstOrNull() ?: DefaultSubjectTags.first()
    return totalsByTag.entries
        .sortedByDescending { it.value }
        .map { (tagId, mins) ->
            val tag = tagById[tagId]
                ?: fallbackTag.copy(id = tagId, name = tagId.replaceFirstChar { it.uppercase() })
            SubjectAllocationSummary(
                tag = tag,
                focusedMinutes = mins,
                shareFraction = (mins.toFloat() / grandTotal.toFloat()).coerceIn(0f, 1f),
            )
        }
}

fun incrementActiveTaskPomodoro(
    tasks: List<StudyTask>,
    activeTaskId: String?,
): List<StudyTask> {
    if (tasks.isEmpty()) return tasks
    val targetId = activeTaskId?.takeIf { id -> tasks.any { it.id == id && !it.isCompleted } }
        ?: tasks.firstOrNull { !it.isCompleted }?.id
        ?: return tasks

    return tasks.map { task ->
        if (task.id == targetId) {
            val nextCompleted = task.completedPomodoros + 1
            task.copy(
                completedPomodoros = nextCompleted,
                isCompleted = nextCompleted >= task.estimatedPomodoros,
            )
        } else {
            task
        }
    }
}

fun buildStudyHistoryCsv(
    history: Map<Long, DayHistoryEntry>,
    todayRecord: DailyRhythmRecord,
    subjectMinutesByDay: Map<Long, Map<String, Int>>,
    tags: List<StudySubjectTag>,
    todayEpoch: Long,
): String {
    val merged = history.toMutableMap()
    if (
        todayRecord.completedFocus > 0 ||
        todayRecord.focusedMinutes > 0 ||
        merged.isEmpty()
    ) {
        merged[todayRecord.dayEpoch] = DayHistoryEntry(
            dayEpoch = todayRecord.dayEpoch,
            completedFocus = todayRecord.completedFocus.coerceAtLeast(0),
            focusedMinutes = todayRecord.focusedMinutes.coerceAtLeast(0),
        )
    }
    val tagNames = tags.associate { it.id to it.name }
    val sb = StringBuilder()
    sb.appendLine("Date,EpochDay,CompletedSessions,FocusedMinutes,Subjects")
    val sortedEpochs = merged.keys.filter { it <= todayEpoch }.sorted()
    for (epoch in sortedEpochs) {
        val entry = merged[epoch] ?: continue
        val dateStr = runCatching {
            LocalDate.ofEpochDay(epoch).format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrDefault(epoch.toString())
        val daySubjects = subjectMinutesByDay[epoch].orEmpty()
            .entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .joinToString(" | ") { (tagId, mins) ->
                "${tagNames[tagId] ?: tagId}: ${mins}m"
            }
            .ifEmpty { "General: ${entry.focusedMinutes}m" }
        val escapedSubjects = "\"" + daySubjects.replace("\"", "\"\"") + "\""
        sb.appendLine("$dateStr,$epoch,${entry.completedFocus},${entry.focusedMinutes},$escapedSubjects")
    }
    return sb.toString()
}

internal fun buildDefaultSeedHistory(todayEpoch: Long): Map<Long, DayHistoryEntry> {
    val pattern = listOf(
        3 to 80,
        4 to 110,
        2 to 50,
        4 to 105,
        5 to 135,
        0 to 0,
        2 to 55,
        3 to 75,
        4 to 100,
        1 to 25,
        0 to 0,
        2 to 50,
        3 to 85,
        4 to 110,
        2 to 55,
        5 to 140,
        4 to 105,
        3 to 85,
    )
    val startEpoch = todayEpoch - (pattern.size - 1)
    val map = mutableMapOf<Long, DayHistoryEntry>()
    pattern.forEachIndexed { index, (sessions, minutes) ->
        val epoch = startEpoch + index
        if (sessions > 0 || minutes > 0) {
            map[epoch] = DayHistoryEntry(
                dayEpoch = epoch,
                completedFocus = sessions,
                focusedMinutes = minutes,
            )
        }
    }
    return map
}

fun encodeHistoryLedger(history: Map<Long, DayHistoryEntry>): String =
    history.values
        .filter { it.completedFocus > 0 || it.focusedMinutes > 0 }
        .sortedBy { it.dayEpoch }
        .joinToString(separator = ";") { entry ->
            "${entry.dayEpoch}:${entry.completedFocus}:${entry.focusedMinutes}"
        }

fun decodeHistoryLedger(raw: String?): Map<Long, DayHistoryEntry> {
    if (raw.isNullOrBlank()) return emptyMap()
    val result = mutableMapOf<Long, DayHistoryEntry>()
    raw.split(';').forEach { token ->
        val parts = token.split(':')
        if (parts.size == 3) {
            val epoch = parts[0].toLongOrNull()
            val sessions = parts[1].toIntOrNull()
            val minutes = parts[2].toIntOrNull()
            if (epoch != null && sessions != null && minutes != null) {
                result[epoch] = DayHistoryEntry(
                    dayEpoch = epoch,
                    completedFocus = sessions.coerceAtLeast(0),
                    focusedMinutes = minutes.coerceAtLeast(0),
                )
            }
        }
    }
    return result
}

fun encodeSubjectLedger(
    subjectMinutesByDay: Map<Long, Map<String, Int>>,
    oldestAllowedEpoch: Long = Long.MIN_VALUE,
): String =
    subjectMinutesByDay.entries
        .filter { it.key >= oldestAllowedEpoch && it.value.values.any { m -> m > 0 } }
        .sortedBy { it.key }
        .joinToString(";") { (epoch, tagMap) ->
            val pairs = tagMap.entries
                .filter { it.value > 0 }
                .joinToString(",") { "${it.key}:${it.value}" }
            "$epoch=$pairs"
        }

fun decodeSubjectLedger(raw: String?): Map<Long, Map<String, Int>> {
    if (raw.isNullOrBlank()) return emptyMap()
    val result = mutableMapOf<Long, Map<String, Int>>()
    raw.split(';').forEach { dayToken ->
        val eqIdx = dayToken.indexOf('=')
        if (eqIdx > 0) {
            val epoch = dayToken.substring(0, eqIdx).toLongOrNull()
            val pairsRaw = dayToken.substring(eqIdx + 1)
            if (epoch != null && pairsRaw.isNotBlank()) {
                val dayMap = mutableMapOf<String, Int>()
                pairsRaw.split(',').forEach { pair ->
                    val colonIdx = pair.indexOf(':')
                    if (colonIdx > 0) {
                        val tagId = pair.substring(0, colonIdx)
                        val mins = pair.substring(colonIdx + 1).toIntOrNull()
                        if (tagId.isNotBlank() && mins != null && mins > 0) {
                            dayMap[tagId] = mins
                        }
                    }
                }
                if (dayMap.isNotEmpty()) {
                    result[epoch] = dayMap
                }
            }
        }
    }
    return result
}

fun encodeCustomSubjectTags(tags: List<StudySubjectTag>): String =
    tags.filter { it.isCustom }
        .joinToString(";") { tag ->
            val safeName = tag.name.replace("|", " ").replace(";", " ").trim()
            "${tag.id}|$safeName|${tag.colorHex}"
        }

fun decodeCustomSubjectTags(raw: String?): List<StudySubjectTag> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(';').mapNotNull { token ->
        val parts = token.split('|')
        if (parts.size == 3) {
            val id = parts[0].trim()
            val name = parts[1].trim()
            val color = parts[2].toLongOrNull()
            if (id.isNotEmpty() && name.isNotEmpty() && color != null) {
                StudySubjectTag(
                    id = id,
                    name = name,
                    colorHex = sanitizeSubjectColorHex(color),
                    isCustom = true,
                )
            } else {
                null
            }
        } else {
            null
        }
    }
}

fun encodeStudyTasks(tasks: List<StudyTask>): String =
    tasks.joinToString(";") { task ->
        val safeTitle = task.title
            .replace("|", " ")
            .replace(";", " ")
            .replace("##", " ")
            .replace("=>", " ")
            .trim()
        "${task.id}|$safeTitle|${task.subjectId}|${task.estimatedPomodoros}|${task.completedPomodoros}|${if (task.isCompleted) 1 else 0}"
    }

fun decodeStudyTasks(raw: String?): List<StudyTask> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(';').mapNotNull { token ->
        val parts = token.split('|')
        if (parts.size == 6) {
            val id = parts[0].trim()
            val title = parts[1].trim()
            val subjectId = parts[2].trim().ifEmpty { "general" }
            val est = parts[3].toIntOrNull()?.coerceIn(1, 20) ?: 1
            val comp = parts[4].toIntOrNull()?.coerceAtLeast(0) ?: 0
            val done = parts[5] == "1"
            if (id.isNotEmpty() && title.isNotEmpty()) {
                StudyTask(
                    id = id,
                    title = title,
                    subjectId = subjectId,
                    estimatedPomodoros = est,
                    completedPomodoros = comp,
                    isCompleted = done,
                )
            } else {
                null
            }
        } else {
            null
        }
    }
}

/**
 * Encodes per-day task archives so each day in the 30-day/365-day heatmap retains both
 * completed and incomplete tasks: "epoch=>task1;task2##epoch2=>task3"
 */
fun encodeTasksByDayLedger(
    tasksByDay: Map<Long, List<StudyTask>>,
    oldestAllowedEpoch: Long = Long.MIN_VALUE,
): String =
    tasksByDay.entries
        .filter { it.key >= oldestAllowedEpoch && it.value.isNotEmpty() }
        .sortedBy { it.key }
        .joinToString("##") { (epoch, dayTasks) ->
            "$epoch=>${encodeStudyTasks(dayTasks)}"
        }

fun decodeTasksByDayLedger(raw: String?): Map<Long, List<StudyTask>> {
    if (raw.isNullOrBlank()) return emptyMap()
    val result = mutableMapOf<Long, List<StudyTask>>()
    raw.split("##").forEach { dayBlock ->
        val sepIdx = dayBlock.indexOf("=>")
        if (sepIdx > 0) {
            val epoch = dayBlock.substring(0, sepIdx).trim().toLongOrNull()
            val tasksRaw = dayBlock.substring(sepIdx + 2)
            if (epoch != null && tasksRaw.isNotBlank()) {
                val decoded = decodeStudyTasks(tasksRaw)
                if (decoded.isNotEmpty()) {
                    result[epoch] = decoded
                }
            }
        }
    }
    return result
}
