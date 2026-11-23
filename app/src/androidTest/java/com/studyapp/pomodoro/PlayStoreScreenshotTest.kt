package com.studyapp.pomodoro

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import com.studyapp.pomodoro.focus.GlobalPermissionManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Captures every feature for the four Play Store device classes.
 *
 * Run with:
 *   adb shell am instrument -w \
 *     -e class com.studyapp.pomodoro.PlayStoreScreenshotTest \
 *     -e screenshotProfile compact \
 *     com.studyapp.pomodoro.test/androidx.test.runner.AndroidJUnitRunner
 *
 * Screenshots are written to the app's external files dir:
 *   /sdcard/Android/data/com.studyapp.pomodoro/files/screenshots/<profile>/
 *
 * The test attempts every capture even if one fails, then reports the failures at the end.
 */
@RunWith(AndroidJUnit4::class)
class PlayStoreScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val engine get() = PomodoroTimerEngine.getInstance(context)
    private val state get() = engine.state.value

    private val profile: String by lazy {
        InstrumentationRegistry.getArguments().getString("screenshotProfile")
            ?: when {
                context.resources.configuration.smallestScreenWidthDp >= 600 -> "tablet"
                context.resources.configuration.screenWidthDp < 390 -> "compact"
                else -> "phone"
            }
    }

    private val outputDir: File by lazy {
        File(context.getExternalFilesDir(null), "screenshots/$profile").apply { mkdirs() }
    }

    private val manifest: File by lazy { File(outputDir, "manifest.txt") }
    private val failures = mutableListOf<String>()

    @Before
    fun prepare() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(
                    context.packageName,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            }
        }
        enableAccessibilityService()
        resetAll()
        device.wakeUp()
        val orientation = InstrumentationRegistry.getArguments().getString("screenshotOrientation")
            ?: if (profile.startsWith("tablet")) "landscape" else "portrait"
        if (orientation == "landscape") {
            device.setOrientationLandscape()
        } else {
            device.setOrientationPortrait()
        }
        device.waitForIdle()
        dismissSystemDialogs()
        composeRule.waitForIdle()
        manifest.writeText(
            buildString {
                appendLine("profile=$profile")
                appendLine("manufacturer=${Build.MANUFACTURER}")
                appendLine("model=${Build.MODEL}")
                appendLine("api=${Build.VERSION.SDK_INT}")
                appendLine("resolution=${device.displayWidth}x${device.displayHeight}")
                appendLine("density=${context.resources.displayMetrics.density}")
                appendLine("fontScale=${context.resources.configuration.fontScale}")
                appendLine("orientation=${context.resources.configuration.orientation}")
                appendLine("capturedAt=${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                appendLine("name\tdescription\twidth\theight")
            },
        )
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
        device.executeShellCommand("cmd statusbar collapse")
        device.waitForIdle()
        runCatching {
            composeRule.activityRule.scenario.moveToState(
                androidx.lifecycle.Lifecycle.State.RESUMED,
            )
        }
        assertTrue(
            "Screenshot failures:\n${failures.joinToString("\n")}",
            failures.isEmpty(),
        )
    }

    @Test
    fun captureEveryFeature() {
        // 1. Sanctuary home
        resetAll()
        scrollToTop()
        capture("01_home_idle", "Sanctuary home, idle 25:00 focus timer")

        // 2. Running timer
        clickText("Start focus")
        Thread.sleep(1_500L)
        scrollToTop()
        capture("02_timer_running", "Focus session running with live progress ring")

        // 3. Paused timer
        clickText("Pause")
        Thread.sleep(600L)
        scrollToTop()
        capture("03_timer_paused", "Paused session with Resume and Reset")

        // 4. Focus theme
        resetAll()
        applyFocusTheme(advanced = false)
        scrollToTop()
        capture("04_focus_mode", "Focus Mode theme")

        // 5. Advanced Focus theme
        applyFocusTheme(advanced = true)
        scrollToTop()
        capture("05_advanced_focus", "Advanced Focus theme")

        // 6. Advanced Focus locked
        instrumentation.runOnMainSync { engine.toggle() }
        Thread.sleep(1_200L)
        scrollToTop()
        capture("06_advanced_focus_locked", "Advanced Focus locked while the timer runs")

        // 7. Focus menu
        resetAll()
        clickDesc("Open Focus menu")
        Thread.sleep(400L)
        capture("07_focus_menu", "Focus protection menu: Off, Block apps, Strict, Advanced Focus")

        // 8. Blocked apps picker
        runCatching {
            clickText("MANAGE")
            Thread.sleep(1_500L)
            capture("08_blocked_apps", "Per-app blocklist picker with search and suggestions")
            clickDesc("Close blocked apps")
        }.onFailure { failures += "08_blocked_apps: ${it.message}" }
        closeMenuIfOpen("FOCUS PROTECTION", "Open Focus menu")

        // 9. Music menu
        clickDesc("Open Music menu")
        Thread.sleep(400L)
        capture("09_music_menu", "Ambient soundscapes, custom music and volume")
        closeMenuIfOpen("AMBIENT FOCUS SOUNDSCAPE", "Open Music menu")

        // 10-12. Ledger
        instrumentation.runOnMainSync { engine.seedDemoHistory() }
        composeRule.waitForIdle()
        scrollTo("30-DAY LEDGER")
        Thread.sleep(500L)
        capture("10_rhythm_ledger", "30-day rhythm heatmap, goal and streak")

        runCatching {
            clickText("365D")
            Thread.sleep(500L)
            capture("11_ledger_year", "365-day archive with month cards")
        }.onFailure { failures += "11_ledger_year: ${it.message}" }

        runCatching {
            val cells = composeRule.onAllNodes(
                hasContentDescription("sessions", substring = true),
            ).fetchSemanticsNodes()
            if (cells.isNotEmpty()) {
                composeRule.onAllNodes(
                    hasContentDescription("sessions", substring = true),
                )[0].performScrollTo().performClick()
                Thread.sleep(500L)
                capture("12_day_detail", "Day detail: sessions, minutes, flow overtime and tasks")
            }
        }.onFailure { failures += "12_day_detail: ${it.message}" }

        // 27. CSV export share sheet
        runCatching {
            clickText("CSV")
            Thread.sleep(1_500L)
            captureRaw("27_csv_export", "CSV export share sheet")
            if (device.currentPackageName != context.packageName) {
                device.pressBack()
                device.waitForIdle()
                Thread.sleep(500L)
            }
        }.onFailure { failures += "27_csv_export: ${it.message}" }

        // 13. Subjects
        runCatching {
            scrollTo("STUDY SUBJECTS")
            clickText("+ SUBJECT")
            Thread.sleep(400L)
            capture("13_subjects", "Study subjects, custom subject form and proportional breakdown")
            clickText("CLOSE")
        }.onFailure { failures += "13_subjects: ${it.message}" }

        // 14-15. Tasks
        runCatching {
            instrumentation.runOnMainSync {
                engine.addTask("Complete Algorithms Problem Set", 3)
                engine.addTask("Review Organic Chemistry notes", 2)
                engine.addTask("Read Chapter 6 — Thermodynamics", 4)
                engine.toggleTaskCompleted(state.tasks.last().id)
            }
            composeRule.waitForIdle()
            scrollTo("TODAY'S TASKS")
            Thread.sleep(500L)
            capture("14_tasks", "Today's task checklist with active task and progress")
            clickText("+ ADD TASK")
            Thread.sleep(400L)
            capture("15_task_input", "Add-task form with session estimate")
            clickText("CLOSE")
        }.onFailure { failures += "14_tasks: ${it.message}" }

        // 16. Completion
        resetAll()
        clickText("Start focus")
        Thread.sleep(500L)
        instrumentation.runOnMainSync { engine.completeCurrentPhaseForTesting() }
        composeRule.waitForIdle()
        Thread.sleep(700L)
        instrumentation.runOnMainSync { engine.stopAlarm() }
        capture("16_completion", "Focus complete dialog with Start break, Keep flowing and Later")

        // 17. Flow overtime
        runCatching {
            clickText("Keep flowing")
            Thread.sleep(2_200L)
            scrollToTop()
            capture("17_flow_overtime", "Flow Overtime counting up with adaptive break credit")
        }.onFailure { failures += "17_flow_overtime: ${it.message}" }

        // 18. Alarm settings
        resetAll()
        clickDesc("Alarm settings")
        Thread.sleep(500L)
        capture("18_alarm_settings", "Alarm settings: sound, vibration, ring duration and preview")

        // 19. Privacy policy
        runCatching {
            clickText("Privacy policy")
            Thread.sleep(600L)
            capture("19_privacy_policy", "In-app privacy policy and data safety disclosure")
            clickDesc("Close privacy policy")
        }.onFailure { failures += "19_privacy_policy: ${it.message}" }

        // 20. Notification
        resetAll()
        clickText("Start focus")
        Thread.sleep(800L)
        runCatching {
            device.executeShellCommand("cmd statusbar expand-notifications")
            device.wait(Until.hasObject(By.textContains("Your focus is protected.")), 5_000L)
            Thread.sleep(1_200L)
            captureRaw("20_notification", "Ongoing timer notification with live progress and controls")
            device.executeShellCommand("cmd statusbar collapse")
            Thread.sleep(600L)
        }.onFailure { failures += "20_notification: ${it.message}" }

        // 21-22. Landscape
        runCatching {
            device.setOrientationLandscape()
            device.waitForIdle()
            composeRule.waitForIdle()
            Thread.sleep(1_000L)
            resetAll()
            scrollToTop()
            capture("21_landscape", "Landscape two-pane layout")
            scrollTo("TODAY'S TASKS")
            Thread.sleep(500L)
            capture("22_landscape_ledger", "Landscape ledger and task pane")
        }.onFailure { failures += "21_landscape: ${it.message}" }

        // Restore the profile orientation.
        val orientation = InstrumentationRegistry.getArguments().getString("screenshotOrientation")
            ?: if (profile.startsWith("tablet")) "landscape" else "portrait"
        if (orientation == "landscape") {
            device.setOrientationLandscape()
        } else {
            device.setOrientationPortrait()
        }
        device.waitForIdle()
        composeRule.waitForIdle()
        Thread.sleep(600L)

        // 23. Permission disclosure
        runCatching {
            disableAccessibilityService()
            instrumentation.runOnMainSync {
                engine.setFocusModeActive(false, force = true)
                engine.refreshPermissionStates()
                GlobalPermissionManager.requestPermissionsWithPrompt(
                    context = composeRule.activity,
                    permissions = listOf(FocusPermissionType.ACCESSIBILITY_LOCK),
                    onGranted = {},
                    onDeclined = {},
                )
            }
            composeRule.waitForIdle()
            waitForText("AccessibilityService API Disclosure")
            capture("23_permission_disclosure", "AccessibilityService API prominent disclosure")
            clickText("Decline")
            enableAccessibilityService()
        }.onFailure { failures += "23_permission_disclosure: ${it.message}" }

        // 24. Blocked screen
        runCatching {
            instrumentation.runOnMainSync {
                engine.setBlockingMode(FocusBlockingMode.BLOCKLIST)
                engine.setFocusModeActive(true, force = true)
                engine.toggle()
            }
            context.startActivity(
                Intent(context, BlockedActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(BlockedActivity.EXTRA_BLOCKED_PACKAGE, "com.instagram.android")
                },
            )
            assertTrue(
                "Block screen did not open",
                device.wait(Until.hasObject(By.textContains("is paused")), 6_000L),
            )
            Thread.sleep(500L)
            captureRaw("24_blocked_screen", "Calm blocked-app screen with emergency pass and hold-to-end")
            device.findObject(By.text("Back to focus"))!!.click()
            device.waitForIdle()
            Thread.sleep(600L)
        }.onFailure { failures += "24_blocked_screen: ${it.message}" }

        instrumentation.runOnMainSync { engine.forceReset() }
    }

    // ------------------------------------------------------------ capture helpers

    private fun capture(name: String, description: String) {
        try {
            dismissSystemDialogs()
            composeRule.waitForIdle()
            Thread.sleep(350L)
            writeScreenshot(name, description)
        } catch (error: Throwable) {
            failures += "$name: ${error.message}"
        }
    }

    private fun captureRaw(name: String, description: String) {
        try {
            dismissSystemDialogs()
            Thread.sleep(350L)
            writeScreenshot(name, description)
        } catch (error: Throwable) {
            failures += "$name: ${error.message}"
        }
    }

    private fun writeScreenshot(name: String, description: String) {
        val file = File(outputDir, "$name.png")
        val ok = device.takeScreenshot(file)
        if (!ok) {
            failures += "$name: takeScreenshot returned false"
            return
        }
        manifest.appendText(
            "$name\t$description\t${device.displayWidth}\t${device.displayHeight}\n",
        )
    }

    private fun resetAll() {
        instrumentation.runOnMainSync {
            // Clear an Advanced Focus lock first; disabling Advanced Focus while locked is a no-op.
            engine.reset(force = true)
            engine.setAdvancedFocusEnabled(false)
            engine.setAdvancedFocusConfirmVisible(false)
            engine.setFocusModeActive(false, force = true)
            engine.stopAlarm()
            engine.selectAmbientSoundscape(AmbientSoundscape.OFF)
            engine.dismissCompletion()
            engine.selectPhase(Phase.Focus)
            engine.updateSetting(Phase.Focus, 25)
            engine.updateSetting(Phase.ShortBreak, 5)
            engine.updateSetting(Phase.LongBreak, 15)
            engine.setBlockingMode(FocusBlockingMode.BLOCKLIST)
            engine.updateBlockedPackages(DefaultBlockedPackages)
            engine.setBlockDuringBreaks(false)
            engine.clearAllHistory()
            engine.state.value.subjectTags
                .filter { it.isCustom }
                .forEach { engine.removeCustomSubject(it.id) }
            engine.updateAlarmEnabled(true)
            engine.updateAlarmSound(DEFAULT_ALARM_SOUND)
            engine.updateAlarmVibration(true)
            engine.updateAlarmDuration(10)
        }
        composeRule.waitForIdle()
    }

    private fun applyFocusTheme(advanced: Boolean) {
        repeat(3) {
            instrumentation.runOnMainSync {
                engine.setFocusModeActive(true, force = true)
                if (advanced) engine.setAdvancedFocusEnabled(true)
            }
            Thread.sleep(700L)
            if (!dismissImmersivePrompt()) {
                composeRule.waitForIdle()
                if (engine.state.value.isFocusModeActive) return
            } else {
                Thread.sleep(400L)
            }
        }
        instrumentation.runOnMainSync {
            engine.setFocusModeActive(true, force = true)
            if (advanced) engine.setAdvancedFocusEnabled(true)
        }
        Thread.sleep(700L)
    }

    private fun dismissImmersivePrompt(): Boolean {
        val prompt = device.findObject(By.text(java.util.regex.Pattern.compile("(?i)got it")))
            ?: return false
        prompt.click()
        device.waitForIdle()
        Thread.sleep(400L)
        return true
    }

    private fun scrollToTop() {
        runCatching { composeRule.onNodeWithText("Pomodoro").performScrollTo() }
        composeRule.waitForIdle()
    }

    private fun scrollTo(text: String) {
        runCatching {
            composeRule.onNodeWithText(text, substring = true).performScrollTo()
        }
        composeRule.waitForIdle()
    }

    private fun clickText(text: String) {
        runCatching {
            composeRule.onNodeWithText(text, substring = true).performScrollTo()
        }
        composeRule.onNodeWithText(text, substring = true).performClick()
        composeRule.waitForIdle()
    }

    private fun clickDesc(description: String) {
        runCatching {
            composeRule.onNodeWithContentDescription(description).performScrollTo()
        }
        composeRule.onNodeWithContentDescription(description).performClick()
        composeRule.waitForIdle()
    }

    private fun closeMenuIfOpen(title: String, chevronDescription: String) {
        val open = composeRule.onAllNodesWithText(title, substring = true)
            .fetchSemanticsNodes().isNotEmpty()
        if (open) {
            runCatching { clickDesc(chevronDescription) }
        }
    }

    private fun waitForText(text: String, timeoutMillis: Long = 8_000L) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            if (composeRule.onAllNodesWithText(text, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            ) {
                return
            }
            Thread.sleep(150L)
        }
        composeRule.onNodeWithText(text, substring = true).assertExists()
    }

    private fun dismissSystemDialogs() {
        listOf("Wait", "Got it", "Allow", "While using the app", "OK", "Continue", "Agree", "Consent")
            .forEach { label ->
                val node = device.findObject(By.text(label))
                if (node != null) {
                    node.click()
                    device.waitForIdle()
                    Thread.sleep(200L)
                }
            }
    }

    private fun enableAccessibilityService() {
        val component = "${context.packageName}/${com.studyapp.pomodoro.focus.FocusLockAccessibilityService::class.java.name}"
        device.executeShellCommand("settings put secure enabled_accessibility_services $component")
        device.executeShellCommand("settings put secure accessibility_enabled 1")
        Thread.sleep(700L)
    }

    private fun disableAccessibilityService() {
        device.executeShellCommand("settings put secure enabled_accessibility_services ''")
        device.executeShellCommand("settings put secure accessibility_enabled 0")
        Thread.sleep(400L)
    }
}
