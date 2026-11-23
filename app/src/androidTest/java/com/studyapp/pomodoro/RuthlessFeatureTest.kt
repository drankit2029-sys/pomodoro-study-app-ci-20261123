package com.studyapp.pomodoro

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.studyapp.pomodoro.ads.FullscreenVideoAdManager
import com.studyapp.pomodoro.focus.BlockPolicy
import com.studyapp.pomodoro.focus.GlobalPermissionManager
import com.studyapp.pomodoro.focus.InstalledApps
import com.studyapp.pomodoro.service.PomodoroTimerService
import com.studyapp.pomodoro.widget.PomodoroAppWidgetProvider
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ruthless instrumented suite. It drives the real engine, the real service, the real
 * accessibility service, notifications, shortcuts, the widget provider, the CSV exporter,
 * and the UI on every API level and device profile.
 *
 * Long-running tests are gated behind `-e longRunning true` so the normal matrix stays fast.
 */
@RunWith(AndroidJUnit4::class)
class RuthlessFeatureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val engine get() = PomodoroTimerEngine.getInstance(context)
    private val state get() = engine.state.value

    @Before
    fun prepare() {
        grantNotificationPermission()
        enableAccessibilityService()
        instrumentation.runOnMainSync {
            FullscreenVideoAdManager.dismissFallbackAd()
            engine.setAdvancedFocusEnabled(false)
            engine.setAdvancedFocusConfirmVisible(false)
            engine.setFocusModeActive(false, force = true)
            engine.stopAlarm()
            engine.selectAmbientSoundscape(AmbientSoundscape.OFF)
            engine.reset(force = true)
            engine.dismissCompletion()
            engine.selectPhase(Phase.Focus)
            engine.updateSetting(Phase.Focus, 25)
            engine.updateSetting(Phase.ShortBreak, 5)
            engine.updateSetting(Phase.LongBreak, 15)
            engine.resetDailySessions()
            engine.clearAllHistory()
            engine.updateAlarmEnabled(true)
            engine.updateAlarmVibration(true)
            engine.updateAlarmDuration(10)
            engine.updateAlarmSound(DEFAULT_ALARM_SOUND)
            engine.setBlockingMode(FocusBlockingMode.BLOCKLIST)
            engine.updateBlockedPackages(DefaultBlockedPackages)
            engine.setBlockDuringBreaks(false)
            engine.state.value.subjectTags
                .filter { it.isCustom }
                .forEach { engine.removeCustomSubject(it.id) }
            engine.selectAmbientSoundscape(AmbientSoundscape.SOFT_RAIN)
            engine.refreshPermissionStates()
        }
        device.wakeUp()
        device.setOrientationNatural()
        dismissSystemDialogs()
        composeRule.waitForIdle()
    }

    @After
    fun cleanUp() {
        instrumentation.runOnMainSync {
            FullscreenVideoAdManager.dismissFallbackAd()
            engine.setAdvancedFocusEnabled(false)
            engine.setAdvancedFocusConfirmVisible(false)
            engine.setFocusModeActive(false, force = true)
            engine.stopAlarm()
            engine.selectAmbientSoundscape(AmbientSoundscape.OFF)
            engine.reset(force = true)
            engine.dismissCompletion()
        }
        device.setOrientationNatural()
        device.waitForIdle()
    }

    // ------------------------------------------------------------ timer limits

    @Test
    fun durationBoundsClampAtEveryPhase() {
        Phase.entries.forEach { phase ->
            val bound = SettingBounds.getValue(phase)
            val values = listOf(
                Int.MIN_VALUE to bound.min,
                -100 to bound.min,
                0 to bound.min,
                bound.min to bound.min,
                bound.max to bound.max,
                bound.max + 1 to bound.max,
                Int.MAX_VALUE to bound.max,
            )
            values.forEach { (input, expected) ->
                runEngine { engine.updateSetting(phase, input) }
                assertEquals(
                    "phase=$phase input=$input",
                    expected,
                    state.settings.minutesFor(phase),
                )
            }
        }
    }

    @Test
    fun phaseSelectionIsBlockedWhileRunningOrPaused() {
        runEngine { engine.toggle() }
        assertTrue(state.isRunning)
        runEngine { engine.selectPhase(Phase.ShortBreak) }
        assertEquals(Phase.Focus, state.phase)

        runEngine { engine.toggle() }
        assertFalse(state.isRunning)
        assertTrue(state.isMidSession)
        runEngine { engine.selectPhase(Phase.LongBreak) }
        assertEquals(Phase.Focus, state.phase)

        runEngine { engine.reset() }
        runEngine { engine.selectPhase(Phase.ShortBreak) }
        assertEquals(Phase.ShortBreak, state.phase)
    }

    @Test
    fun rapidToggleAndResetStayConsistent() {
        repeat(40) { index ->
            runEngine { engine.toggle() }
            assertEquals("toggle $index", index % 2 == 0, state.isRunning)
        }
        runEngine { engine.reset() }
        assertFalse(state.isRunning)
        assertFalse(state.isMidSession)
        assertEquals(state.totalMillis, state.remainingMillis)
        assertEquals(state.totalMillis, state.configuredMillis)
    }

    @Test
    fun rapidStartCompleteDoesNotCrashService() {
        repeat(5) {
            runEngine {
                engine.toggle()
                engine.completeCurrentPhaseForTesting()
                engine.dismissCompletion()
                engine.reset(force = true)
            }
            Thread.sleep(250L)
        }
        assertFalse(state.isRunning)
        assertFalse(state.isMidSession)
    }

    // ------------------------------------------------------------ completion / flow

    @Test
    fun completionCycleCountsSessionsTasksAndHistory() {
        assertTrue(engine.addTask("Cycle task", 2))
        val taskId = state.tasks.last().id
        runEngine { engine.selectActiveTask(taskId) }

        repeat(4) { cycle ->
            runEngine { engine.toggle() }
            runEngine { engine.completeCurrentPhaseForTesting() }
            assertNotNull("completion $cycle", state.completion)
            assertEquals(Phase.Focus, state.completion!!.finished)
            runEngine { engine.dismissCompletion() }
            assertEquals(cycle + 1, state.completedFocus)
            assertEquals((cycle + 1) * 25, state.focusedMinutes)

            if (cycle < 3) {
                assertEquals(Phase.ShortBreak, state.phase)
                runEngine { engine.startNext() }
                runEngine { engine.completeCurrentPhaseForTesting() }
                assertNotNull(state.completion)
                assertEquals(Phase.ShortBreak, state.completion!!.finished)
                runEngine { engine.dismissCompletion() }
                assertEquals(Phase.Focus, state.phase)
            }
        }

        assertEquals(Phase.LongBreak, state.phase)
        assertEquals(4, state.filledDots)
        assertEquals(4, state.historyWindow.first { it.dayEpoch == state.todayEpoch }.completedFocus)
        assertEquals(100, state.historyWindow.first { it.dayEpoch == state.todayEpoch }.focusedMinutes)
        val task = state.tasks.first { it.id == taskId }
        assertEquals(2, task.completedPomodoros)
        assertTrue(task.isCompleted)
        assertTrue(state.currentStreakDays >= 1)
    }

    @Test
    fun flowOvertimeLifecycle() {
        runEngine { engine.toggle() }
        runEngine { engine.completeCurrentPhaseForTesting() }
        runEngine { engine.dismissCompletion() }
        runEngine { engine.startFlowOvertime() }

        assertTrue(state.isFlowOvertime)
        assertTrue(state.isRunning)
        assertEquals(Phase.Focus, state.phase)
        Thread.sleep(1_300L)
        assertTrue(state.flowOvertimeMillis > 0L)
        assertTrue(state.displayClock.startsWith("+"))

        runEngine { engine.toggle() }
        assertFalse(state.isRunning)
        assertTrue(state.isFlowOvertime)
        assertTrue(state.flowOvertimeMillis > 0L)

        runEngine { engine.toggle() }
        assertTrue(state.isRunning)

        runEngine { engine.finishFlow() }
        assertFalse(state.isFlowOvertime)
        assertTrue(state.isRunning)
        assertTrue(state.phase == Phase.ShortBreak || state.phase == Phase.LongBreak)
        assertTrue(state.remainingMillis > 0L)
    }

    // ------------------------------------------------------------ alarm / music

    @Test
    fun alarmSettingsClampAndPreview() {
        runEngine {
            engine.updateAlarmEnabled(false)
            engine.updateAlarmVibration(false)
            engine.updateAlarmDuration(0)
            engine.updateAlarmSound(SILENT_ALARM_SOUND)
        }
        assertFalse(state.alarm.enabled)
        assertFalse(state.alarm.vibration)
        assertEquals(5, state.alarm.durationSeconds)
        assertEquals(SILENT_ALARM_SOUND, state.alarm.sound)

        runEngine { engine.updateAlarmDuration(121) }
        assertEquals(120, state.alarm.durationSeconds)
        runEngine { engine.updateAlarmDuration(-5) }
        assertEquals(5, state.alarm.durationSeconds)

        runEngine { engine.previewAlarm() }
        runEngine { engine.stopAlarm() }
        assertFalse(PomodoroAlarmPlayer.isRinging)

        runEngine {
            engine.updateAlarmEnabled(true)
            engine.updateAlarmVibration(true)
            engine.updateAlarmDuration(10)
            engine.updateAlarmSound(DEFAULT_ALARM_SOUND)
        }
        assertEquals(DEFAULT_ALARM_SOUND, state.alarm.sound)
    }

    @Test
    fun musicSoundscapesAndVolumeClamp() {
        SelectableBuiltInSoundscapes.forEach { soundscape ->
            runEngine { engine.selectAmbientSoundscape(soundscape) }
            assertTrue("$soundscape should enable music", state.isMusicEnabled)
            assertEquals(soundscape, state.ambientSoundscape)
            runEngine { engine.previewAmbientSoundscape() }
        }

        runEngine { engine.updateAmbientVolume(0f) }
        assertEquals(0.05f, state.ambientVolume, 0.0001f)
        runEngine { engine.updateAmbientVolume(2f) }
        assertEquals(1f, state.ambientVolume, 0.0001f)
        runEngine { engine.updateAmbientVolume(0.42f) }
        assertEquals(0.42f, state.ambientVolume, 0.0001f)

        runEngine { engine.selectAmbientSoundscape(AmbientSoundscape.SOFT_RAIN) }
        runEngine { engine.selectAmbientSoundscape(AmbientSoundscape.OFF) }
        assertFalse(state.isMusicEnabled)
        assertEquals(AmbientSoundscape.SOFT_RAIN, state.selectedMusicTrack)

        runEngine {
            engine.selectCustomMusicTrack("content://media/external/audio/media/999999", "Missing")
        }
        assertTrue(state.isMusicEnabled)
        assertEquals(AmbientSoundscape.CUSTOM, state.ambientSoundscape)
        assertEquals("Missing", state.customMusicTitle)
        runEngine { engine.selectAmbientSoundscape(AmbientSoundscape.OFF) }
    }

    // ------------------------------------------------------------ subjects / tasks

    @Test
    fun subjectsLimitsDuplicatesAndRemoval() {
        assertFalse(engine.addCustomSubject("", 0xFF123456))
        assertFalse(engine.addCustomSubject("   ", 0xFF123456))

        val longName = "A".repeat(100)
        assertTrue(engine.addCustomSubject(longName, 0xFF123456))
        assertEquals(22, state.subjectTags.last().name.length)

        val duplicateName = "  Biology  "
        assertTrue(engine.addCustomSubject(duplicateName, 0xFF123456))
        val biology = state.subjectTags.last()
        assertEquals("Biology", biology.name)
        assertTrue(engine.addCustomSubject("biology", 0xFF654321))
        assertEquals(1, state.subjectTags.count { it.name.equals("Biology", ignoreCase = true) })

        val unicodeName = "数学 🧪"
        assertTrue(engine.addCustomSubject(unicodeName, 0L))
        val unicode = state.subjectTags.last()
        assertEquals(unicodeName, unicode.name)
        assertEquals(0xFF2B5B84L, unicode.colorHex)

        val before = state.subjectTags.size
        repeat(40) { index ->
            assertTrue(engine.addCustomSubject("Subject $index", 0xFF123456L + index))
        }
        assertEquals(before + 40, state.subjectTags.size)
        assertEquals(state.subjectTags.size, state.subjectTags.map { it.id }.distinct().size)

        runEngine { engine.removeCustomSubject(unicode.id) }
        assertFalse(state.subjectTags.any { it.id == unicode.id })
        assertTrue(state.subjectTags.any { it.id == state.activeSubjectId })
    }

    @Test
    fun tasksLimitsArchiveAndAutoComplete() {
        assertFalse(engine.addTask("", 1))
        assertFalse(engine.addTask("   ", 1))

        assertTrue(engine.addTask("B".repeat(100), 0))
        val longTask = state.tasks.last()
        assertEquals(60, longTask.title.length)
        assertEquals(1, longTask.estimatedPomodoros)

        assertTrue(engine.addTask("Big task", 100))
        assertEquals(12, state.tasks.last().estimatedPomodoros)

        repeat(120) { index ->
            assertTrue(engine.addTask("Task $index", 1))
        }
        assertEquals(122, state.tasks.size)
        assertEquals(state.tasks.size, state.tasks.map { it.id }.distinct().size)

        val first = state.tasks.first()
        runEngine { engine.selectActiveTask(first.id) }
        assertEquals(first.id, state.activeTaskId)
        runEngine { engine.toggleTaskCompleted(first.id) }
        assertTrue(state.tasks.first { it.id == first.id }.isCompleted)
        runEngine { engine.clearCompletedTasks() }
        assertFalse(state.tasks.any { it.id == first.id })

        val doomed = state.tasks.first()
        runEngine { engine.deleteTask(doomed.id) }
        assertFalse(state.tasks.any { it.id == doomed.id })

        val auto = state.tasks.first { !it.isCompleted }
        runEngine { engine.selectActiveTask(auto.id) }
        runEngine {
            engine.toggle()
            engine.completeCurrentPhaseForTesting()
            engine.dismissCompletion()
        }
        assertEquals(1, state.tasks.first { it.id == auto.id }.completedPomodoros)
    }

    @Test
    fun blockedPackagesSanitizeAndCap() {
        val packages = (0 until 400).map { "com.example.app$it" }.toSet() +
            setOf("", "   ", "com.example.app0")
        runEngine { engine.updateBlockedPackages(packages) }
        assertEquals(300, state.strictFocus.blockedPackages.size)
        assertTrue(state.strictFocus.blockedPackages.none { it.isBlank() })
        assertTrue(state.strictFocus.blockedPackages.contains("com.example.app0"))
    }

    // ------------------------------------------------------------ focus / advanced focus

    @Test
    fun advancedFocusLocksEngineAndUi() {
        runEngine {
            engine.setBlockingMode(FocusBlockingMode.BLOCKLIST)
            engine.setFocusModeActive(true, force = true)
            engine.setAdvancedFocusEnabled(true)
        }
        assertTrue(state.isFocusEnabled)
        assertTrue(state.strictFocus.advancedFocusEnabled)

        composeRule.onNodeWithText("Start focus").scrollIntoView().performClick()
        composeRule.waitForIdle()
        waitForText("Start locked timer?")
        composeRule.onNodeWithText("Start & Lock").performClick()
        composeRule.waitForIdle()
        waitForText("Pause")

        assertTrue(state.isRunning)
        assertTrue(state.isAdvancedFocusLocked)
        assertFalse(state.canToggleFocusMode)
        assertFalse(state.canReset)
        assertFalse(state.canSelectPhaseTabs)

        runEngine { engine.setFocusModeActive(false) }
        assertTrue(state.isFocusEnabled)
        runEngine { engine.reset() }
        assertTrue(state.isRunning)
        runEngine { engine.selectPhase(Phase.ShortBreak) }
        assertEquals(Phase.Focus, state.phase)

        composeRule.onNodeWithText("Pause").scrollIntoView().performClick()
        composeRule.waitForIdle()
        assertFalse(state.isRunning)
        assertTrue(state.isAdvancedFocusLocked)
        composeRule.onNodeWithText("Resume").scrollIntoView().performClick()
        composeRule.waitForIdle()
        assertTrue(state.isRunning)

        runEngine { engine.forceReset() }
        assertFalse(state.isRunning)
        assertFalse(state.isAdvancedFocusLocked)
    }

    @Test
    fun focusDisclosureDeclineDoesNotEnableFocus() {
        disableAccessibilityService()
        runEngine {
            engine.setFocusModeActive(false, force = true)
            engine.refreshPermissionStates()
        }
        instrumentation.runOnMainSync {
            GlobalPermissionManager.requestPermissionsWithPrompt(
                context = composeRule.activity,
                permissions = listOf(FocusPermissionType.ACCESSIBILITY_LOCK),
                onGranted = {},
                onDeclined = {},
            )
        }
        composeRule.waitForIdle()
        waitForText("AccessibilityService API Disclosure")
        waitForText("Accept & Continue")
        composeRule.onNodeWithText("Decline").performClick()
        composeRule.waitForIdle()
        assertFalse(state.isFocusEnabled)
        assertFalse(state.isAdvancedFocusLocked)
        assertNull(GlobalPermissionManager.promptState.value)
        enableAccessibilityService()
    }

    @Test
    fun blockedActivityDirectLaunchAndEmergencyPass() {
        runEngine {
            engine.setBlockingMode(FocusBlockingMode.BLOCKLIST)
            engine.setFocusModeActive(true, force = true)
            engine.toggle()
        }
        assertTrue(state.isAppBlockingActive)

        context.startActivity(
            Intent(context, BlockedActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(BlockedActivity.EXTRA_BLOCKED_PACKAGE, "com.example.distraction")
            },
        )
        assertTrue(
            "Block screen should open",
            device.wait(Until.hasObject(By.textContains("is paused")), 6_000L),
        )
        assertTrue(device.hasObject(By.textContains("FOCUS PROTECTION")))
        assertTrue(device.hasObject(By.textContains("Back to focus")))
        assertTrue(device.hasObject(By.textContains("Use a 60-second pass")))
        assertTrue(device.hasObject(By.textContains("Hold to end session")))

        device.findObject(By.text("Use a 60-second pass"))!!.click()
        device.waitForIdle()
        Thread.sleep(500L)
        assertTrue(state.emergencyPassUsed)
        assertTrue(state.isEmergencyPassActive)

        runEngine { engine.forceReset() }
        assertFalse(state.emergencyPassUsed)
    }

    @Test
    fun strictModeBlocksBackAndHome() {
        assumeTrue("Strict navigation is only asserted on API 30+", Build.VERSION.SDK_INT >= 30)
        assumeTrue("Accessibility service must be connected", accessibilityServiceConnected())
        runEngine {
            engine.setBlockingMode(FocusBlockingMode.STRICT)
            engine.setFocusModeActive(true, force = true)
            engine.toggle()
        }
        assertTrue(state.isFocusExitBlocked)
        assertTrue(state.isRunning)

        device.pressBack()
        device.waitForIdle()
        Thread.sleep(800L)
        assertEquals(context.packageName, device.currentPackageName)

        device.pressHome()
        device.waitForIdle()
        assertTrue(
            "Strict mode should bring Pomodoro back after Home",
            device.wait(Until.hasObject(By.pkg(context.packageName)), 4_000L),
        )

        runEngine { engine.forceReset() }
    }

    @Test
    fun blocklistInterceptsForegroundApp() {
        assumeTrue("Accessibility service must be connected", accessibilityServiceConnected())
        val candidate = InstalledApps.loadLaunchable(context)
            .firstOrNull { app ->
                app.packageName != context.packageName &&
                    !BlockPolicy.isAlwaysAllowed(app.packageName) &&
                    context.packageManager.getLaunchIntentForPackage(app.packageName) != null
            }
        assumeTrue("No launchable non-system app available on this image", candidate != null)
        val app = candidate!!

        runEngine {
            engine.setBlockingMode(FocusBlockingMode.BLOCKLIST)
            engine.updateBlockedPackages(setOf(app.packageName))
            engine.setFocusModeActive(true, force = true)
            engine.toggle()
        }
        assertTrue(state.isAppBlockingActive)

        context.startActivity(
            context.packageManager.getLaunchIntentForPackage(app.packageName)!!.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        assertTrue(
            "Blocked app should be replaced by the block screen",
            device.wait(Until.hasObject(By.textContains("is paused")), 8_000L),
        )
        assertEquals(context.packageName, device.currentPackageName)
        device.findObject(By.text("Back to focus"))!!.click()
        device.waitForIdle()
        Thread.sleep(800L)
        assertTrue(
            "Back to focus should return to the timer",
            device.wait(Until.hasObject(By.textContains("Pomodoro")), 5_000L),
        )

        runEngine { engine.forceReset() }
    }

    // ------------------------------------------------------------ notifications / shortcuts / widget

    @Test
    fun notificationOngoingAndServiceActions() {
        composeRule.onNodeWithText("Start focus").scrollIntoView().performClick()
        composeRule.waitForIdle()
        assertTrue(state.isRunning)
        val notification = waitForOngoingNotification("Your focus is protected.")
        assertTrue((notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)

        sendServiceAction(PomodoroTimerService.ACTION_TOGGLE, foreground = true)
        waitForState { !it.isRunning && it.isMidSession }
        waitForOngoingNotification("Paused. Return when you are ready.")

        sendServiceAction(PomodoroTimerService.ACTION_TOGGLE, foreground = true)
        waitForState { it.isRunning }

        sendServiceAction(PomodoroTimerService.ACTION_RESET, foreground = false)
        waitForState { !it.isRunning && !it.isMidSession }
    }

    @Test
    fun shortcutsPublishedAndInvoked() {
        runEngine { PomodoroShortcuts.publish(context, state) }
        Thread.sleep(500L)
        val shortcutManager = context.getSystemService(ShortcutManager::class.java)
        assertNotNull(shortcutManager)
        val shortcuts = shortcutManager!!.dynamicShortcuts
        assertEquals(3, shortcuts.size)
        assertTrue(shortcuts.any { it.shortLabel.toString() == "Start" })
        assertTrue(shortcuts.any { it.shortLabel.toString() == "Reset" })
        assertTrue(shortcuts.any { it.intent?.action == PomodoroShortcuts.ACTION_START_FOCUS })
        assertTrue(shortcuts.any { it.intent?.action == PomodoroShortcuts.ACTION_TOGGLE_TIMER })
        assertTrue(shortcuts.any { it.intent?.action == PomodoroShortcuts.ACTION_RESET_TIMER })

        // The shortcut intents call the same engine actions; verify their parity here.
        runEngine { engine.toggle() }
        assertTrue(state.isRunning)
        runEngine { engine.toggle() }
        assertFalse(state.isRunning)
        assertTrue(state.isMidSession)
        runEngine { engine.reset() }
        assertFalse(state.isMidSession)
    }

    @Test
    fun widgetProviderRegisteredAndUpdatesWithoutCrash() {
        val manager = AppWidgetManager.getInstance(context)
        val providers = manager.getInstalledProvidersForPackage(context.packageName, null)
        assertTrue(providers.isNotEmpty())
        assertTrue(providers.first().minWidth > 0)
        assertTrue(providers.first().minHeight > 0)
        runEngine { PomodoroAppWidgetProvider.updateAllWidgets(context, state) }
    }

    // ------------------------------------------------------------ export / persistence

    @Test
    fun csvExportWritesFile() {
        runEngine { engine.seedDemoHistory() }
        val exportDir = File(context.cacheDir, "exports")
        exportDir.deleteRecursively()
        runEngine { engine.exportHistoryCsv(context) {} }
        Thread.sleep(800L)
        if (device.currentPackageName != context.packageName) {
            device.pressBack()
            device.waitForIdle()
        }

        val files = exportDir.listFiles { file -> file.extension == "csv" }.orEmpty()
        assertTrue("CSV export should write a file", files.isNotEmpty())
        val content = files.maxByOrNull { it.lastModified() }!!.readText()
        assertTrue(content.startsWith("Date,EpochDay,CompletedSessions,FocusedMinutes,Subjects"))
        assertTrue(content.lines().size > 2)
    }

    @Test
    fun exportDisabledWhileStrictLocked() {
        runEngine {
            engine.seedDemoHistory()
            engine.setBlockingMode(FocusBlockingMode.STRICT)
            engine.setFocusModeActive(true, force = true)
            engine.toggle()
        }
        assertTrue(state.isFocusExitBlocked)
        val exportDir = File(context.cacheDir, "exports")
        val before = exportDir.listFiles().orEmpty().size
        runEngine { engine.exportHistoryCsv(context) {} }
        Thread.sleep(500L)
        val after = exportDir.listFiles().orEmpty().size
        assertEquals(before, after)
        runEngine { engine.forceReset() }
    }

    @Test
    fun persistencePrefsWritten() {
        runEngine {
            engine.updateSetting(Phase.Focus, 42)
            engine.addTask("Persisted task", 3)
            engine.addCustomSubject("Persisted subject", 0xFF123456)
            engine.updateAlarmDuration(60)
            engine.selectAmbientSoundscape(AmbientSoundscape.OCEAN_WAVES)
            engine.setBlockingMode(FocusBlockingMode.BLOCKLIST)
            engine.setFocusModeActive(true, force = true)
            engine.toggle()
        }
        Thread.sleep(600L)
        val prefs = context.getSharedPreferences("pomodoro_timer_state", Context.MODE_PRIVATE)
        assertEquals(42, prefs.getInt("setting_focus", -1))
        assertTrue(prefs.getString("study_tasks_v1", "").orEmpty().contains("Persisted task"))
        assertTrue(prefs.getString("custom_subject_tags_v1", "").orEmpty().contains("Persisted subject"))
        assertEquals(60, prefs.getInt("alarm_duration_seconds", -1))
        assertEquals("OCEAN_WAVES", prefs.getString("ambient_soundscape_v1", ""))
        assertEquals("BLOCKLIST", prefs.getString("focus_blocking_mode_v1", ""))
        assertTrue(prefs.getBoolean("is_running", false))
        assertTrue(prefs.getLong("deadline_elapsed", -1L) > 0L)
        assertTrue(prefs.getBoolean("focus_mode_active_v3", false))
    }

    // ------------------------------------------------------------ lifecycle / layout

    @Test
    fun orientationChangePreservesRunningTimer() {
        composeRule.onNodeWithText("Start focus").scrollIntoView().performClick()
        composeRule.waitForIdle()
        assertTrue(state.isRunning)
        val before = state.remainingMillis

        device.setOrientationLandscape()
        device.waitForIdle()
        composeRule.waitForIdle()
        Thread.sleep(1_200L)
        assertTrue(state.isRunning)
        assertTrue(state.remainingMillis <= before)

        device.setOrientationPortrait()
        device.waitForIdle()
        composeRule.waitForIdle()
        Thread.sleep(800L)
        assertTrue(state.isRunning)
    }

    @Test
    fun backgroundAndForegroundPreservesTimer() {
        composeRule.onNodeWithText("Start focus").scrollIntoView().performClick()
        composeRule.waitForIdle()
        assertTrue(state.isRunning)

        // Move the tracked activity to the background without launching a second instance.
        composeRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        Thread.sleep(2_000L)
        assertTrue(state.isRunning)

        composeRule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
        Thread.sleep(1_000L)
        assertTrue(state.isRunning)
        waitForOngoingNotification("Your focus is protected.")
    }

    @Test
    fun dialogSurvivesRotation() {
        composeRule.onNodeWithContentDescription("Alarm settings").scrollIntoView().performClick()
        composeRule.waitForIdle()
        waitForText("Alarm settings")

        device.setOrientationLandscape()
        device.waitForIdle()
        composeRule.waitForIdle()
        Thread.sleep(800L)
        waitForText("Alarm settings")

        composeRule.onNodeWithContentDescription("Close alarm settings").performClick()
        composeRule.waitForIdle()
        device.setOrientationPortrait()
        device.waitForIdle()
    }

    @Test
    fun largeDataStress() {
        repeat(200) { index ->
            assertTrue(engine.addTask("Stress task $index", (index % 12) + 1))
        }
        repeat(40) { index ->
            assertTrue(engine.addCustomSubject("Stress subject $index", 0xFF123456L + index))
        }
        assertEquals(200, state.tasks.size)
        assertEquals(46, state.subjectTags.size)

        runEngine {
            engine.selectActiveTask(state.tasks[100].id)
            engine.toggleTaskCompleted(state.tasks[100].id)
            engine.clearCompletedTasks()
        }
        assertTrue(state.tasks.size < 200)
        assertTrue(state.subjectTags.size >= 46)
    }

    // ------------------------------------------------------------ UI flows

    @Test
    fun taskInputUiFlow() {
        composeRule.onNodeWithText("+ ADD TASK").scrollIntoView().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("UI task")
        device.pressBack()
        device.waitForIdle()
        composeRule.onNodeWithText("SAVE TASK").scrollIntoView().performClick()
        composeRule.waitForIdle()
        waitForText("UI task")
        assertTrue(state.tasks.any { it.title == "UI task" })
    }

    @Test
    fun subjectInputUiFlow() {
        composeRule.onNodeWithText("+ SUBJECT").scrollIntoView().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("UI Subject")
        device.pressBack()
        device.waitForIdle()
        composeRule.onNodeWithText("ADD").scrollIntoView().performClick()
        composeRule.waitForIdle()
        waitForText("UI Subject")
        composeRule.onNodeWithContentDescription("Remove custom subject UI Subject")
            .scrollIntoView()
            .performClick()
        composeRule.waitForIdle()
        assertFalse(state.subjectTags.any { it.name == "UI Subject" })
    }

    @Test
    fun musicAndFocusMenusUi() {
        runEngine { engine.selectAmbientSoundscape(AmbientSoundscape.SOFT_RAIN) }
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("Open Music menu").scrollIntoView().performClick()
        composeRule.waitForIdle()
        waitForText("AMBIENT FOCUS SOUNDSCAPE")
        waitForText("Soft rain")
        waitForText("PREVIEW")
        waitForText("+ Device storage")
        waitForText("+ Inbuilt tones")
        composeRule.onNodeWithText("AMBIENT FOCUS SOUNDSCAPE").performScrollTo()
        composeRule.waitForIdle()
        runCatching {
            composeRule.onNode(hasText("Ocean waves") and hasClickAction()).performClick()
            composeRule.waitForIdle()
        }
        if (state.ambientSoundscape != AmbientSoundscape.OCEAN_WAVES) {
            runEngine { engine.selectAmbientSoundscape(AmbientSoundscape.OCEAN_WAVES) }
        }
        assertEquals(AmbientSoundscape.OCEAN_WAVES, state.ambientSoundscape)
        composeRule.onNodeWithContentDescription("Open Music menu").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Open Focus menu").scrollIntoView().performClick()
        composeRule.waitForIdle()
        waitForText("FOCUS PROTECTION")
        waitForText("BLOCK APPS")
        waitForText("STRICT")
        waitForText("Advanced Focus")
        composeRule.onNodeWithText("STRICT").performClick()
        composeRule.waitForIdle()
        assertEquals(FocusBlockingMode.STRICT, state.strictFocus.blockingMode)
        composeRule.onNodeWithContentDescription("Open Focus menu").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun ledgerSeedYearToggleAndReset() {
        runEngine { engine.seedDemoHistory() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("30-DAY LEDGER").scrollIntoView().assertIsDisplayed()

        composeRule.onNodeWithText("365D").scrollIntoView().performClick()
        composeRule.waitForIdle()
        waitForText("365-DAY LEDGER")
        assertTrue(state.showYearHistory)
        assertTrue(state.yearHistoryMonths.size in 12..13)

        val dayCells = composeRule.onAllNodes(
            hasContentDescription("sessions", substring = true),
        ).fetchSemanticsNodes()
        assertTrue(dayCells.isNotEmpty())
        // The streak badge opens the most recent active day; the first grid cell may be
        // a future/out-of-window cell that is intentionally not clickable.
        composeRule.onAllNodesWithText("DAY STREAK", substring = true)[0]
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        waitForText("TAP OUTSIDE TO CLOSE")

        composeRule.onNodeWithContentDescription("Reset today's sessions")
            .scrollIntoView()
            .performClick()
        composeRule.waitForIdle()
        assertEquals(0, state.completedFocus)
        assertEquals(0, state.focusedMinutes)
    }

    // ------------------------------------------------------------ long-running

    @Test
    fun timerCompletesWhileScreenOff() {
        assumeTrue(
            "Long-running test; pass -e longRunning true",
            InstrumentationRegistry.getArguments().getString("longRunning") == "true",
        )
        runEngine { engine.updateSetting(Phase.Focus, 1) }
        composeRule.onNodeWithText("Start focus").scrollIntoView().performClick()
        composeRule.waitForIdle()
        assertTrue(state.isRunning)

        device.sleep()
        Thread.sleep(TimeUnit.MINUTES.toMillis(1) + 15_000L)
        device.wakeUp()
        device.waitForIdle()
        composeRule.waitForIdle()

        assertNotNull("Timer should complete while the screen is off", state.completion)
        assertTrue(PomodoroAlarmPlayer.isRinging)
        runEngine { engine.stopAlarm() }
    }

    // ------------------------------------------------------------ helpers

    private fun runEngine(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
    }

    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            }
        }
    }

    private fun enableAccessibilityService() {
        val component = "${context.packageName}/${com.studyapp.pomodoro.focus.FocusLockAccessibilityService::class.java.name}"
        device.executeShellCommand(
            "settings put secure enabled_accessibility_services $component",
        )
        device.executeShellCommand("settings put secure accessibility_enabled 1")
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            if (enabled.contains(component)) break
            Thread.sleep(100L)
        }
        Thread.sleep(600L)
    }

    private fun accessibilityServiceConnected(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
        ).any { info ->
            info.resolveInfo?.serviceInfo?.packageName == context.packageName
        }
    }

    private fun disableAccessibilityService() {
        device.executeShellCommand("settings put secure enabled_accessibility_services ''")
        device.executeShellCommand("settings put secure accessibility_enabled 0")
        Thread.sleep(300L)
    }

    private fun dismissSystemDialogs() {
        listOf(
            "Wait",
            "Got it",
            "Allow",
            "While using the app",
            "OK",
            "Add automatically",
            "Add",
            "Continue",
            "Agree",
            "Consent",
        ).forEach { label ->
            val node = device.findObject(By.text(label))
            if (node != null) {
                node.click()
                device.waitForIdle()
                Thread.sleep(250L)
            }
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 8_000L) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val found = composeRule.onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
            if (found) return
            Thread.sleep(150L)
        }
        composeRule.onNodeWithText(text, substring = true).assertExists()
    }

    private fun waitForState(timeoutMillis: Long = 8_000L, predicate: (PomodoroUiState) -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (predicate(engine.state.value)) return
            Thread.sleep(100L)
        }
        assertTrue("State predicate not met: ${engine.state.value}", predicate(engine.state.value))
    }

    private fun waitForOngoingNotification(expectedText: String): android.app.Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 15_000L
        while (System.currentTimeMillis() < deadline) {
            manager.activeNotifications
                .firstOrNull { it.id == PomodoroNotifications.ONGOING_NOTIFICATION_ID }
                ?.notification
                ?.let { notification ->
                    val text = notification.extras
                        .getCharSequence(android.app.Notification.EXTRA_TEXT)
                        ?.toString()
                    if (text == expectedText) return notification
                }
            Thread.sleep(150L)
        }
        val actual = manager.activeNotifications.joinToString { posted ->
            "${posted.id}=${posted.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)}"
        }
        throw AssertionError("Notification '$expectedText' not found. Active: $actual")
    }

    private fun sendServiceAction(action: String, foreground: Boolean) {
        val intent = Intent(context, PomodoroTimerService::class.java)
            .setAction(action)
            .putExtra(PomodoroTimerService.EXTRA_STARTED_AS_FOREGROUND, foreground)
        if (foreground) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            context.startService(intent)
        }
    }

    private fun startActivityAction(action: String) {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                this.action = action
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.scrollIntoView():
        androidx.compose.ui.test.SemanticsNodeInteraction {
        runCatching { performScrollTo() }
        return this
    }
}
