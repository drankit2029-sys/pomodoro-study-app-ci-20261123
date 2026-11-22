package com.studyapp.pomodoro

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.studyapp.pomodoro.ads.FullscreenVideoAdManager
import java.io.File
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PomodoroScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val engine
        get() = PomodoroTimerEngine.getInstance(instrumentation.targetContext)

    @Before
    fun prepareDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
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
            engine.reset()
            engine.dismissCompletion()
            engine.selectPhase(Phase.Focus)
            engine.resetDailySessions()
            engine.updateSetting(Phase.Focus, 25)
            engine.updateAlarmEnabled(true)
            engine.updateAlarmSound(DEFAULT_ALARM_SOUND)
            engine.updateAlarmVibration(true)
            engine.updateAlarmDuration(10)
            engine.selectAmbientSoundscape(AmbientSoundscape.OFF)
            engine.ensureDailyReset()
        }
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        device.setOrientationPortrait()
        dismissSystemDialog(device)
        device.waitForIdle()
        Thread.sleep(300L)
        composeRule.waitForIdle()
    }

    @After
    fun resetDevice() {
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
        }
        val device = UiDevice.getInstance(instrumentation)
        device.setOrientationNatural()
        device.waitForIdle()
    }

    @Test
    fun capturesPortraitTimerDailyAndNotification() {
        val device = UiDevice.getInstance(instrumentation)

        composeRule.onNodeWithText("Start focus").scrollIntoView().performClick()
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
        Thread.sleep(500L)
        scrollToTop()
        capture(device, "01_timer_running.png")

        composeRule.onNodeWithText("AUTO RESET · 12:00 AM")
            .scrollIntoView()
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reset today's sessions").assertExists()

        instrumentation.runOnMainSync {
            engine.addTask("Complete Algorithms Problem Set", 3)
        }
        composeRule.waitForIdle()
        val taskId = engine.state.value.tasks.last().id
        composeRule.onNodeWithText("TODAY'S TASKS", substring = true)
            .scrollIntoView()
            .assertIsDisplayed()
        device.swipe(
            device.displayWidth / 2,
            (device.displayHeight * 0.78f).toInt(),
            device.displayWidth / 2,
            (device.displayHeight * 0.46f).toInt(),
            20,
        )
        device.waitForIdle()
        Thread.sleep(500L)
        capture(device, "02_daily_rhythm.png")
        instrumentation.runOnMainSync { engine.deleteTask(taskId) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Pause").scrollIntoView().performClick()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
        scrollToTop()
        capture(device, "03_timer_paused.png")

        val pausedText = "Paused. Return when you are ready."
        val pausedNotification = waitForOngoingNotification(pausedText)
        assertEquals(
            pausedText,
            pausedNotification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                ?.toString(),
        )
        assertTrue(
            "Paused notification should remain ongoing",
            (pausedNotification.flags and Notification.FLAG_ONGOING_EVENT) != 0,
        )

        device.executeShellCommand("cmd statusbar expand-notifications")
        assertTrue(
            "Notification shade did not open",
            device.wait(Until.hasObject(By.text(pausedText)), 5_000L),
        )
        device.waitForIdle()
        Thread.sleep(3000L)
        capture(device, "04_notification_collapsed.png")
        device.pressBack()
        device.waitForIdle()

        composeRule.onNodeWithText("Resume").scrollIntoView().assertIsDisplayed()
    }

    @Test
    fun capturesAlarmSettingsCompletionAndDailyReset() {
        val device = UiDevice.getInstance(instrumentation)

        scrollToTop()
        composeRule.onNodeWithContentDescription("Alarm settings").performClick()
        composeRule.onNodeWithText("Alarm settings").assertIsDisplayed()
        composeRule.onNodeWithText("Ring when a session ends").assertIsDisplayed()
        composeRule.onNodeWithText("Alarm sound").assertIsDisplayed()
        capture(device, "05_alarm_settings.png")

        composeRule.onNodeWithText("RING FOR").scrollIntoView().assertIsDisplayed()
        composeRule.onNodeWithText("30s").performClick()
        composeRule.waitForIdle()
        assertEquals(30, engine.state.value.alarm.durationSeconds)
        composeRule.onNodeWithText("Preview alarm").assertExists()
        capture(device, "06_alarm_settings_durations.png")

        composeRule.onNodeWithContentDescription("Close alarm settings")
            .scrollIntoView()
            .performClick()
        composeRule.onNodeWithText("Pomodoro").assertIsDisplayed()

        composeRule.onNodeWithText("Start focus").scrollIntoView().performClick()
        waitForOngoingNotification("Your focus is protected.")
        instrumentation.runOnMainSync { engine.completeCurrentPhaseForTesting() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Focus complete").assertIsDisplayed()
        capture(device, "07_session_complete.png")

        instrumentation.runOnMainSync { engine.stopAlarm() }
        composeRule.onNodeWithText("Later").scrollIntoView().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("30-DAY LEDGER").scrollIntoView().assertIsDisplayed()
        composeRule.onNodeWithText("1 OF 4").scrollIntoView().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Reset today's sessions")
            .scrollIntoView()
            .performClick()
        composeRule.onNodeWithText("0 OF 4").scrollIntoView().assertIsDisplayed()
        capture(device, "08_daily_reset.png")
    }

    @Test
    fun capturesLandscapeTimerAndAlarmSettings() {
        val device = UiDevice.getInstance(instrumentation)
        device.setOrientationLandscape()
        device.waitForIdle()
        composeRule.waitForIdle()
        Thread.sleep(1200L)

        composeRule.onNodeWithText("30-DAY LEDGER").assertExists()
        capture(device, "09_landscape.png")

        // The left timer pane never scrolls in landscape. Frame 18 shows the right
        // (ledger/tasks) column scrolled to the tasks card instead.
        composeRule.onNodeWithText("TODAY'S TASKS", substring = true).scrollIntoView()
        Thread.sleep(500L)
        capture(device, "18_landscape_scrolled.png")

        composeRule.onNodeWithText("Start focus").scrollIntoView().performClick()
        Thread.sleep(500L)

        val settingsButton = composeRule.onNodeWithContentDescription("Alarm settings")
        runCatching { settingsButton.scrollIntoView() }
        settingsButton.performClick()
        composeRule.onNodeWithText("Alarm settings").assertIsDisplayed()
        capture(device, "10_landscape_settings.png")

        composeRule.onNodeWithContentDescription("Close alarm settings")
            .scrollIntoView()
            .performClick()
        composeRule.waitForIdle()

        instrumentation.runOnMainSync { engine.reset() }
        device.setOrientationNatural()
        device.waitForIdle()
    }

    @Test
    fun capturesThirtyDayLedgerAndDayDetail() {
        val device = UiDevice.getInstance(instrumentation)

        instrumentation.runOnMainSync { engine.seedDemoHistory() }
        composeRule.waitForIdle()
        scrollToTop()

        composeRule.onNodeWithText("30-DAY LEDGER").scrollIntoView().assertExists()

        val streakBadges = composeRule
            .onAllNodesWithText("DAY STREAK", substring = true)
            .fetchSemanticsNodes()
        assertTrue("Seeded demo history should expose a streak badge", streakBadges.isNotEmpty())
        composeRule.onNodeWithText("TAP ANY DAY FOR DETAILS").scrollIntoView()
        Thread.sleep(400L)
        capture(device, "13_ledger_30day.png")

        composeRule.onAllNodesWithText("DAY STREAK", substring = true)[0].performScrollTo()
        Thread.sleep(300L)
        composeRule.onAllNodesWithText("DAY STREAK", substring = true)[0].performClick()
        composeRule.waitForIdle()
        Thread.sleep(300L)
        composeRule.onNodeWithText("TAP OUTSIDE TO CLOSE").scrollIntoView()
        Thread.sleep(400L)
        capture(device, "14_ledger_day_detail.png")

        runCatching {
            composeRule.onNodeWithContentDescription("Close day details", useUnmergedTree = true)
                .performClick()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("TAP ANY DAY FOR DETAILS").assertExists()

        instrumentation.runOnMainSync { engine.clearAllHistory() }
        composeRule.waitForIdle()
    }

    @Test
    fun capturesFlowOvertimeAndAdaptiveBreakCredit() {
        val device = UiDevice.getInstance(instrumentation)

        scrollToTop()
        composeRule.onNodeWithText("Start focus").scrollIntoView().performClick()
        waitForOngoingNotification("Your focus is protected.")

        instrumentation.runOnMainSync { engine.completeCurrentPhaseForTesting() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Focus complete").assertIsDisplayed()

        val enteredFlow = runCatching {
            composeRule.onNodeWithText("Keep flowing").performClick()
        }.isSuccess
        if (!enteredFlow) {
            instrumentation.runOnMainSync { engine.startFlowOvertime() }
        }
        composeRule.waitForIdle()
        assertTrue("Flow overtime should be running", engine.state.value.isFlowOvertime)

        Thread.sleep(2_500L)
        scrollToTop()
        composeRule.onNodeWithText("FLOW OVERTIME").assertExists()
        capture(device, "15_flow_overtime.png")

        instrumentation.runOnMainSync { engine.finishFlow() }
        composeRule.waitForIdle()
        Thread.sleep(800L)
        scrollToTop()

        val afterFlow = engine.state.value
        assertFalse("Flow overtime should stop after finishing", afterFlow.isFlowOvertime)
        assertFalse("Finishing flow should start a recovery phase", afterFlow.phase == Phase.Focus)
        capture(device, "16_flow_break_credit.png")

        instrumentation.runOnMainSync { engine.reset() }
        composeRule.waitForIdle()
    }

    @Test
    fun capturesExpandedNotification() {
        val device = UiDevice.getInstance(instrumentation)

        composeRule.onNodeWithText("Start focus").scrollIntoView().performClick()
        waitForOngoingNotification("Your focus is protected.")
        composeRule.onNodeWithText("Pause").scrollIntoView().performClick()

        val pausedText = "Paused. Return when you are ready."
        waitForOngoingNotification(pausedText)

        device.executeShellCommand("cmd statusbar expand-notifications")
        device.wait(Until.hasObject(By.text(pausedText)), 5_000L)
        device.waitForIdle()
        Thread.sleep(1_500L)

        runCatching {
            val toggles = device.findObjects(By.desc("Expand")) +
                device.findObjects(By.desc("Collapse"))
            toggles.maxByOrNull { it.visibleBounds.centerY() }?.click()
            device.waitForIdle()
            Thread.sleep(1_500L)
        }
        capture(device, "17_notification_expanded.png")

        device.executeShellCommand("cmd statusbar collapse")
        device.waitForIdle()
        Thread.sleep(1_000L)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Resume").assertExists()
    }

    @Test
    fun capturesFocusAndAdvancedFocusThemes() {
        val device = UiDevice.getInstance(instrumentation)

        enableFocusTheme(device)
        Thread.sleep(1_200L)
        capture(device, "11_focus_mode.png")

        enableFocusTheme(device, advanced = true)
        Thread.sleep(1_500L)
        capture(device, "12_advanced_focus.png")

        // Starting the timer while Advanced Focus is on engages the lock morph.
        instrumentation.runOnMainSync {
            engine.toggle()
        }
        // Make sure Compose has published the running-timer action label before
        // restoring the theme; otherwise the hide/show redraw can lag the screenshot.
        waitForComposeText("Pause")
        composeRule.waitForIdle()
        Thread.sleep(1_200L)
        enableFocusTheme(device, advanced = true)
        Thread.sleep(500L)
        capture(device, "19_advanced_focus_locked.png")

        assertTrue(
            "Focus Mode should still be on for the theme captures",
            engine.state.value.isFocusModeActive,
        )
    }

    @Test
    fun capturesBlockedAppsPicker() {
        val device = UiDevice.getInstance(instrumentation)

        scrollToTop()
        composeRule.onNodeWithContentDescription("Open Focus menu").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("BLOCK APPS").assertExists()
        composeRule.onNodeWithText("MANAGE").scrollIntoView().performClick()
        composeRule.waitForIdle()
        // The picker loads launchable apps off the main thread.
        Thread.sleep(1_500L)
        composeRule.onNodeWithText("Choose your distractions").assertExists()
        capture(device, "20_blocked_apps.png")

        composeRule.onNodeWithContentDescription("Close blocked apps").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("FOCUS PROTECTION").assertExists()
    }

    /**
     * Turns Focus Mode (and optionally Advanced Focus) on and keeps it on through
     * Android's one-time immersive-mode confirmation. When the app first hides the
     * system bars, that system dialog pauses MainActivity; its onResume clears Focus
     * Mode because Accessibility is not granted on CI. Dismiss the dialog once, then
     * re-assert the theme so the next capture/video is not a focus-off frame.
     */
    private fun enableFocusTheme(device: UiDevice, advanced: Boolean = false) {
        repeat(3) {
            applyFocusTheme(advanced)
            Thread.sleep(700L)
            if (!dismissImmersivePrompt(device)) {
                composeRule.waitForIdle()
                if (engine.state.value.isFocusModeActive) return
            } else {
                Thread.sleep(500L)
            }
        }
        // Never leave the capture on the focus-off frame if the last attempt
        // dismissed the prompt and MainActivity.onResume reset the theme.
        applyFocusTheme(advanced)
        Thread.sleep(700L)
        composeRule.waitForIdle()
        assertTrue(
            "Focus Mode should stay active for the theme captures",
            engine.state.value.isFocusModeActive,
        )
    }

    private fun applyFocusTheme(advanced: Boolean) {
        instrumentation.runOnMainSync {
            engine.setFocusModeActive(true, force = true)
            if (advanced) engine.setAdvancedFocusEnabled(true)
        }
    }

    private fun dismissImmersivePrompt(device: UiDevice): Boolean {
        val prompt = device.findObject(By.text(Pattern.compile("(?i)got it"))) ?: return false
        prompt.click()
        device.waitForIdle()
        Thread.sleep(500L)
        return true
    }

    private fun waitForComposeText(text: String, timeoutMillis: Long = 5_000L) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            runCatching { composeRule.onNodeWithText(text).assertExists() }
                .onSuccess { return }
            Thread.sleep(200L)
        }
        composeRule.onNodeWithText(text).assertExists()
    }

    private fun scrollToTop() {
        runCatching {
            composeRule.onNodeWithText("Pomodoro").performScrollTo()
        }
        composeRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.scrollIntoView(): SemanticsNodeInteraction {
        runCatching { performScrollTo() }
        return this
    }

    private fun capture(device: UiDevice, name: String) {
        dismissSystemDialog(device)
        val directory = File(instrumentation.targetContext.filesDir, "screenshots")
        directory.mkdirs()
        assertTrue("Could not capture $name", device.takeScreenshot(File(directory, name)))
    }

    private fun dismissSystemDialog(device: UiDevice) {
        val wait = device.findObject(By.text("Wait"))
        if (wait != null) {
            wait.click()
            device.waitForIdle()
            Thread.sleep(400L)
        }
        val gotIt = device.findObject(By.text("Got it"))
        if (gotIt != null) {
            gotIt.click()
            device.waitForIdle()
            Thread.sleep(400L)
        }
    }

    private fun waitForOngoingNotification(expectedText: String? = null): Notification {
        val manager = instrumentation.targetContext.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 25_000L
        while (System.currentTimeMillis() < deadline) {
            manager.activeNotifications
                .firstOrNull { it.id == PomodoroNotifications.ONGOING_NOTIFICATION_ID }
                ?.notification
                ?.let { notification ->
                    val text = notification.extras
                        .getCharSequence(Notification.EXTRA_TEXT)
                        ?.toString()
                    if (expectedText == null || text == expectedText) {
                        return notification
                    }
                }
            Thread.sleep(200L)
        }
        val actual = manager.activeNotifications.joinToString { posted ->
            val text = posted.notification.extras
                .getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()
            "${posted.id}=${text ?: "<no EXTRA_TEXT>"}"
        }
        throw AssertionError(
            "Notification did not reach text: $expectedText. Active: ${actual.ifEmpty { "<none>" }}",
        )
    }
}
