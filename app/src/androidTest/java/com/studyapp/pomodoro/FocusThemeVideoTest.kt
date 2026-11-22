package com.studyapp.pomodoro

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Holds the screen on the Focus and Advanced Focus themes long enough for the
 * CI recorder (`screenrecord`) to capture the ambient morphs. These tests take
 * no screenshots; [PomodoroScreenshotTest] owns the still captures.
 *
 * Unlike the screenshot suite, this deliberately uses [ActivityScenarioRule]
 * rather than the Compose test rule: the Compose test clock stops animating
 * while the test thread sleeps, so screenrecord would only see one frame. The
 * real frame clock keeps the botanical morphs moving for the recorder.
 */
@RunWith(AndroidJUnit4::class)
class FocusThemeVideoTest {

    companion object {
        const val RECORDER_TAG = "FocusVideoReady"
    }

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val engine
        get() = PomodoroTimerEngine.getInstance(instrumentation.targetContext)

    @Before
    fun prepareDevice() {
        recorderMarker().delete()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        instrumentation.runOnMainSync {
            engine.setAutoFocusEnabled(false)
            engine.setAdvancedFocusConfirmVisible(false)
            engine.setFocusModeActive(false, force = true)
            engine.setAdvancedFocusEnabled(false)
            engine.stopAlarm()
            engine.reset()
            engine.dismissCompletion()
            engine.selectPhase(Phase.Focus)
            engine.updateSetting(Phase.Focus, 25)
            engine.selectAmbientSoundscape(AmbientSoundscape.OFF)
        }
        val device = UiDevice.getInstance(instrumentation)
        device.wakeUp()
        // Keep each profile in its natural shape: portrait on the phone and
        // compact emulators, landscape on the 10-inch tablet.
        device.setOrientationNatural()
        device.waitForIdle()
        Thread.sleep(300L)
    }

    @After
    fun resetDevice() {
        instrumentation.runOnMainSync {
            engine.setAdvancedFocusConfirmVisible(false)
            engine.setFocusModeActive(false, force = true)
            engine.setAdvancedFocusEnabled(false)
            engine.stopAlarm()
            engine.reset()
            engine.dismissCompletion()
            engine.selectAmbientSoundscape(AmbientSoundscape.OFF)
        }
        val device = UiDevice.getInstance(instrumentation)
        device.setOrientationNatural()
        device.waitForIdle()
    }

    @Test
    fun focusModeAnimation() {
        applyFocusTheme()
        signalRecorderReady()
        // The recorder only starts after the marker, so hold the theme long
        // enough for the 24 s clip plus the adb hand-off. The emulators emit
        // roughly half as many frames per wall-clock second, hence the margin.
        Thread.sleep(34_000L)
    }

    @Test
    fun advancedFocusModeAnimation() {
        applyFocusTheme(advanced = true)
        signalRecorderReady()
        Thread.sleep(5_000L)
        instrumentation.runOnMainSync {
            engine.toggle()
        }
        Thread.sleep(29_000L)
    }

    /**
     * The CI recorder cannot start before the activity is actually on screen:
     * instrumentation warm-up can show the launcher or splash for 10-20 s, so
     * an early screenrecord can miss the whole theme. Write this marker only
     * after the focus theme is composed, and let the script start recording then.
     */
    private fun signalRecorderReady() {
        assertTrue(
            "Focus Mode should be active before recording",
            engine.state.value.isFocusModeActive,
        )
        recorderMarker().writeText("ready")
        // The CI recorder watches logcat: polling the marker file through
        // `adb shell run-as` costs seconds per check on a loaded emulator and
        // starts screenrecord too late in the theme's hold window.
        Log.i(RECORDER_TAG, "ready")
    }

    private fun recorderMarker() = File(instrumentation.targetContext.filesDir, "video-ready")

    /**
     * The CI script pre-confirms Android's one-time immersive-mode prompt, so
     * there is no dialog to dismiss here. Re-assert the theme once after it has
     * settled: resuming MainActivity clears Focus Mode when Accessibility is
     * not granted, and the second pass covers that resume.
     */
    private fun applyFocusTheme(advanced: Boolean = false) {
        instrumentation.runOnMainSync {
            engine.setFocusModeActive(true, force = true)
            if (advanced) engine.setAdvancedFocusEnabled(true)
        }
        Thread.sleep(1_200L)
        instrumentation.runOnMainSync {
            engine.setFocusModeActive(true, force = true)
            if (advanced) engine.setAdvancedFocusEnabled(true)
        }
        Thread.sleep(800L)
    }
}
