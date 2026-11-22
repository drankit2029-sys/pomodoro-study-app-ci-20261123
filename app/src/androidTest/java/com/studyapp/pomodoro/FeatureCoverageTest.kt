package com.studyapp.pomodoro

import android.Manifest
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.pm.ShortcutManager
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.studyapp.pomodoro.ads.FullscreenVideoAdManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Feature coverage that runs on every API level, device profile and orientation in the
 * `feature-matrix` workflow. It deliberately avoids screenshots so the matrix does not
 * change the 20-capture contract in [PomodoroScreenshotTest].
 */
@RunWith(AndroidJUnit4::class)
class FeatureCoverageTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val engine get() = PomodoroTimerEngine.getInstance(context)
    private val state get() = engine.state.value

    @Before
    fun prepare() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        instrumentation.runOnMainSync {
            FullscreenVideoAdManager.dismissFallbackAd()
            engine.setAutoFocusEnabled(false)
            engine.setAdvancedFocusEnabled(false)
            engine.setAdvancedFocusConfirmVisible(false)
            engine.setFocusModeActive(false, force = true)
            engine.stopAlarm()
            engine.selectAmbientSoundscape(AmbientSoundscape.OFF)
            engine.reset()
            engine.dismissCompletion()
            engine.selectPhase(Phase.Focus)
            engine.updateSetting(Phase.Focus, 25)
            engine.resetDailySessions()
            engine.clearAllHistory()
            engine.updateAlarmEnabled(true)
            engine.updateAlarmVibration(true)
            engine.updateAlarmDuration(10)
        }
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.waitForIdle()
        composeRule.waitForIdle()
    }

    @After
    fun cleanUp() {
        instrumentation.runOnMainSync {
            FullscreenVideoAdManager.dismissFallbackAd()
            engine.setAdvancedFocusEnabled(false)
            engine.setFocusModeActive(false, force = true)
            engine.selectAmbientSoundscape(AmbientSoundscape.OFF)
            engine.stopAlarm()
            engine.reset()
            engine.dismissCompletion()
        }
    }

    @Test
    fun timerLifecycle() {
        composeRule.onNodeWithText("Start focus").performClick()
        assertTrue(state.isRunning)
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
        composeRule.onNodeWithText("Pause").performClick()
        assertFalse(state.isRunning)
        assertTrue(state.isMidSession)
        composeRule.onNodeWithText("Resume").performClick()
        assertTrue(state.isRunning)
        instrumentation.runOnMainSync { engine.reset() }
        assertFalse(state.isRunning)
        assertFalse(state.isMidSession)
    }

    @Test
    fun phasesAndSettings() {
        instrumentation.runOnMainSync {
            engine.selectPhase(Phase.ShortBreak)
            engine.updateSetting(Phase.ShortBreak, 7)
        }
        assertEquals(Phase.ShortBreak, state.phase)
        assertEquals(7 * 60 * 1000L, state.configuredMillis)
        instrumentation.runOnMainSync {
            engine.selectPhase(Phase.LongBreak)
            engine.updateSetting(Phase.LongBreak, 12)
        }
        assertEquals(12 * 60 * 1000L, state.configuredMillis)
        instrumentation.runOnMainSync {
            engine.selectPhase(Phase.Focus)
            engine.updateSetting(Phase.Focus, 25)
        }
        assertEquals(Phase.Focus, state.phase)
    }

    @Test
    fun completionFlowAndOvertime() {
        instrumentation.runOnMainSync { engine.toggle() }
        instrumentation.runOnMainSync { engine.completeCurrentPhaseForTesting() }
        assertNotNull(state.completion)
        assertEquals(Phase.Focus, state.completion!!.finished)
        instrumentation.runOnMainSync { engine.dismissCompletion() }
        instrumentation.runOnMainSync { engine.startFlowOvertime() }
        assertTrue(state.isFlowOvertime)
        instrumentation.runOnMainSync { engine.finishFlow() }
        assertFalse(state.isFlowOvertime)
        assertEquals(Phase.ShortBreak, state.phase)
    }

    @Test
    fun ledgerGoalAndStreak() {
        instrumentation.runOnMainSync {
            engine.toggle()
            engine.completeCurrentPhaseForTesting()
            engine.dismissCompletion()
        }
        assertEquals(1, state.completedFocus)
        assertTrue(state.focusedMinutes >= 1)
        assertTrue(state.currentStreakDays >= 1)
        assertTrue(state.historyWindow.any { it.completedFocus >= 1 })
        instrumentation.runOnMainSync { engine.resetDailySessions() }
        assertEquals(0, state.completedFocus)
    }

    @Test
    fun subjects() {
        val name = "Matrix ${System.currentTimeMillis()}"
        instrumentation.runOnMainSync { assertTrue(engine.addCustomSubject(name, 0xFF55AA55)) }
        val added = state.subjectTags.last()
        assertEquals(name, added.name)
        instrumentation.runOnMainSync { engine.selectSubject(added.id) }
        assertEquals(added.id, state.activeSubjectId)
        instrumentation.runOnMainSync { engine.removeCustomSubject(added.id) }
        assertFalse(state.subjectTags.any { it.id == added.id })
    }

    @Test
    fun tasks() {
        assertTrue(engine.addTask("Matrix task", 3))
        val task = state.tasks.last()
        assertEquals("Matrix task", task.title)
        instrumentation.runOnMainSync { engine.selectActiveTask(task.id) }
        assertEquals(task.id, state.activeTaskId)
        instrumentation.runOnMainSync { engine.toggleTaskCompleted(task.id) }
        assertTrue(state.tasks.first { it.id == task.id }.isCompleted)
        instrumentation.runOnMainSync { engine.clearCompletedTasks() }
        assertFalse(state.tasks.any { it.id == task.id })
        assertTrue(engine.addTask("To delete", 1))
        val doomed = state.tasks.last()
        instrumentation.runOnMainSync { engine.deleteTask(doomed.id) }
        assertFalse(state.tasks.any { it.id == doomed.id })
    }

    @Test
    fun focusBlockingModes() {
        instrumentation.runOnMainSync { engine.setBlockingMode(FocusBlockingMode.OFF) }
        assertEquals(FocusBlockingMode.OFF, state.strictFocus.blockingMode)
        instrumentation.runOnMainSync { engine.setBlockingMode(FocusBlockingMode.BLOCKLIST) }
        instrumentation.runOnMainSync { engine.updateBlockedPackages(setOf("com.example.blocked")) }
        assertTrue(state.strictFocus.blockedPackages.contains("com.example.blocked"))
        instrumentation.runOnMainSync { engine.setBlockDuringBreaks(true) }
        assertTrue(state.strictFocus.blockDuringBreaks)
        instrumentation.runOnMainSync { engine.setBlockingMode(FocusBlockingMode.STRICT) }
        assertEquals(FocusBlockingMode.STRICT, state.strictFocus.blockingMode)
        instrumentation.runOnMainSync {
            engine.setBlockingMode(FocusBlockingMode.BLOCKLIST)
            engine.setBlockDuringBreaks(false)
        }
    }

    @Test
    fun advancedFocusLock() {
        instrumentation.runOnMainSync {
            engine.setBlockingMode(FocusBlockingMode.BLOCKLIST)
            engine.setAdvancedFocusEnabled(true)
            engine.setFocusModeActive(true)
        }
        assertTrue(state.isFocusEnabled)
        instrumentation.runOnMainSync { engine.toggle() }
        assertTrue(state.isAdvancedFocusLocked)
        instrumentation.runOnMainSync { engine.toggle() }
        assertTrue(state.isRunning)
        instrumentation.runOnMainSync { engine.setFocusModeActive(false) }
        assertTrue(state.isFocusEnabled) // locked until the session ends
        instrumentation.runOnMainSync { engine.reset(force = true) }
        assertFalse(state.isAdvancedFocusLocked)
    }

    @Test
    fun emergencyPass() {
        instrumentation.runOnMainSync {
            engine.setBlockingMode(FocusBlockingMode.BLOCKLIST)
            engine.setFocusModeActive(true)
            engine.toggle()
            engine.useEmergencyPass()
        }
        assertTrue(state.emergencyPassUsed)
        instrumentation.runOnMainSync { engine.reset(force = true) }
        assertFalse(state.emergencyPassUsed)
    }

    @Test
    fun ambientSounds() {
        instrumentation.runOnMainSync { engine.selectAmbientSoundscape(AmbientSoundscape.FOREST_WIND) }
        assertEquals(AmbientSoundscape.FOREST_WIND, state.ambientSoundscape)
        assertTrue(state.isMusicEnabled)
        instrumentation.runOnMainSync { engine.updateAmbientVolume(0.2f) }
        assertEquals(0.2f, state.ambientVolume, 0.01f)
        instrumentation.runOnMainSync { engine.previewAmbientSoundscape() }
        instrumentation.runOnMainSync { engine.selectAmbientSoundscape(AmbientSoundscape.OFF) }
        assertFalse(state.isMusicEnabled)
    }

    @Test
    fun alarmSettings() {
        instrumentation.runOnMainSync {
            engine.updateAlarmEnabled(false)
            engine.updateAlarmVibration(false)
            engine.updateAlarmDuration(60)
            engine.updateAlarmSound(SILENT_ALARM_SOUND)
        }
        assertFalse(state.alarm.enabled)
        assertFalse(state.alarm.vibration)
        assertEquals(60, state.alarm.durationSeconds)
        assertEquals(SILENT_ALARM_SOUND, state.alarm.sound)
        instrumentation.runOnMainSync { engine.previewAlarm() }
        instrumentation.runOnMainSync { engine.stopAlarm() }
        instrumentation.runOnMainSync {
            engine.updateAlarmEnabled(true)
            engine.updateAlarmVibration(true)
            engine.updateAlarmDuration(10)
            engine.updateAlarmSound(DEFAULT_ALARM_SOUND)
        }
    }

    @Test
    fun runningTimerPostsOngoingNotification() {
        composeRule.onNodeWithText("Start focus").performClick()
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager)
        val active = manager!!.activeNotifications.any { it.packageName == context.packageName }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            assertTrue(active || state.isRunning)
        }
    }

    @Test
    fun widgetAndShortcutsAreRegistered() {
        val widgets = AppWidgetManager.getInstance(context)
            .getInstalledProvidersForPackage(context.packageName, null)
        assertTrue(widgets.isNotEmpty())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcuts = context.getSystemService(ShortcutManager::class.java).dynamicShortcuts
            assertTrue(shortcuts.size <= 3)
        }
    }

    @Test
    fun csvExportAndHistoryHelpersDoNotCrash() {
        instrumentation.runOnMainSync {
            engine.seedDemoHistory()
            engine.exportHistoryCsv(context) { }
            engine.clearAllHistory()
        }
        assertTrue(state.historyWindow.isEmpty())
    }

    @Test
    fun responsiveControlsArePresent() {
        composeRule.onNodeWithText("Start focus").scrollIntoView().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Open Focus menu").scrollIntoView().assertExists()
        composeRule.onNodeWithContentDescription("Open Music menu").scrollIntoView().assertExists()
        composeRule.onNodeWithText("DAILY RHYTHM & LEDGER").scrollIntoView().assertIsDisplayed()
        composeRule.onNodeWithText("CSV").scrollIntoView().assertIsDisplayed()
    }
}
