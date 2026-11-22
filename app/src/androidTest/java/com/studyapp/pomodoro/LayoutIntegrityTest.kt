package com.studyapp.pomodoro

import android.os.Build
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.studyapp.pomodoro.ads.FullscreenVideoAdManager
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layout integrity across every branch: compact portrait, phone portrait, phone landscape,
 * foldable square, tablet portrait, tablet landscape. Every key control must be inside the
 * window, non-zero sized, and the two large cards must never overlap.
 */
@RunWith(AndroidJUnit4::class)
class LayoutIntegrityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private val engine get() = PomodoroTimerEngine.getInstance(context)

    @Before
    fun prepare() {
        instrumentation.runOnMainSync {
            FullscreenVideoAdManager.dismissFallbackAd()
            engine.setAdvancedFocusEnabled(false)
            engine.setAdvancedFocusConfirmVisible(false)
            engine.setFocusModeActive(false, force = true)
            engine.stopAlarm()
            engine.reset(force = true)
            engine.dismissCompletion()
            engine.selectPhase(Phase.Focus)
            engine.updateSetting(Phase.Focus, 25)
            engine.selectAmbientSoundscape(AmbientSoundscape.OFF)
        }
        device.wakeUp()
        device.setOrientationNatural()
        device.waitForIdle()
        composeRule.waitForIdle()
    }

    @After
    fun cleanUp() {
        instrumentation.runOnMainSync {
            engine.stopAlarm()
            engine.reset(force = true)
            engine.dismissCompletion()
        }
        device.setOrientationNatural()
        device.waitForIdle()
    }

    @Test
    fun keyControlsAreInsideTheWindowAndCardsDoNotOverlap() {
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val header = composeRule.onNodeWithText("Pomodoro").fetchSemanticsNode().boundsInRoot
        val start = composeRule.onNodeWithText("Start focus")
            .scrollIntoView()
            .fetchSemanticsNode().boundsInRoot
        val ledger = composeRule.onNodeWithText("DAILY RHYTHM & LEDGER")
            .scrollIntoView()
            .fetchSemanticsNode().boundsInRoot
        val tasks = composeRule.onNodeWithText("TODAY'S TASKS · RESETS 12:00 AM")
            .scrollIntoView()
            .fetchSemanticsNode().boundsInRoot

        assertInside(root, header, "header")
        assertInside(root, start, "start button")
        assertInside(root, ledger, "ledger", requireFullHeight = false)
        assertInside(root, tasks, "tasks", requireFullHeight = false)
        assertFalse("Ledger and tasks must not overlap", ledger.overlaps(tasks))
        if (root.width <= root.height) {
            assertTrue("Header must be above the timer", header.bottom <= start.top + 1f)
        }
    }

    @Test
    fun dialogsFitInsideTheWindow() {
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithContentDescription("Alarm settings")
            .scrollIntoView()
            .performClick()
        composeRule.waitForIdle()
        val alarmTitle = composeRule.onNodeWithText("Alarm settings")
            .fetchSemanticsNode().boundsInRoot
        assertInside(root, alarmTitle, "alarm dialog title")
        composeRule.onNodeWithContentDescription("Close alarm settings").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("25:00 remaining")
            .performTouchInput { doubleClick() }
        composeRule.waitForIdle()
        val customTitle = composeRule.onNodeWithText("CUSTOM DURATION")
            .fetchSemanticsNode().boundsInRoot
        assertInside(root, customTitle, "custom duration title")
        composeRule.onNodeWithContentDescription("Close custom duration").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun taskAndSubjectInputsAreReachable() {
        composeRule.onNodeWithText("+ ADD TASK").scrollIntoView().performClick()
        composeRule.waitForIdle()
        val taskField = composeRule.onNode(hasSetTextAction())
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Task input must have a usable height", taskField.height > 20f)
        composeRule.onNodeWithText("CLOSE").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("+ SUBJECT").scrollIntoView().performClick()
        composeRule.waitForIdle()
        val subjectField = composeRule.onNode(hasSetTextAction())
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Subject input must have a usable height", subjectField.height > 20f)
        composeRule.onNodeWithText("CLOSE").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun landscapeKeyControlsAreInsideTheWindow() {
        device.setOrientationLandscape()
        device.waitForIdle()
        composeRule.waitForIdle()
        Thread.sleep(800L)

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val start = composeRule.onNodeWithText("Start focus")
            .scrollIntoView()
            .fetchSemanticsNode().boundsInRoot
        val ledger = composeRule.onNodeWithText("DAILY RHYTHM & LEDGER")
            .scrollIntoView()
            .fetchSemanticsNode().boundsInRoot
        assertInside(root, start, "landscape start button")
        assertInside(root, ledger, "landscape ledger")
        assertTrue("Landscape must use a wide window", root.width > root.height)
    }

    @Test
    fun fontScaleTwoKeepsCoreControlsVisible() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        device.executeShellCommand("settings put system font_scale 2.0")
        try {
            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
            Thread.sleep(600L)
            composeRule.onNodeWithText("Start focus").scrollIntoView().assertIsDisplayed()
            composeRule.onNodeWithContentDescription("25:00 remaining").assertExists()
            composeRule.onNodeWithText("Pomodoro").assertIsDisplayed()
        } finally {
            device.executeShellCommand("settings put system font_scale 1.0")
            composeRule.activityRule.scenario.recreate()
            composeRule.waitForIdle()
        }
    }

    private fun assertInside(
        root: Rect,
        child: Rect,
        label: String,
        requireFullHeight: Boolean = true,
    ) {
        assertTrue("$label must have width", child.width > 0f)
        assertTrue("$label must have height", child.height > 0f)
        assertTrue(
            "$label must start inside the window: $child vs $root",
            child.left >= root.left - 1f && child.top >= root.top - 1f,
        )
        assertTrue(
            "$label must end inside the window horizontally: $child vs $root",
            child.right <= root.right + 1f,
        )
        if (requireFullHeight) {
            assertTrue(
                "$label must end inside the window: $child vs $root",
                child.bottom <= root.bottom + 1f,
            )
        } else {
            assertTrue("$label must start above the window bottom", child.top < root.bottom)
        }
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.scrollIntoView():
        androidx.compose.ui.test.SemanticsNodeInteraction {
        runCatching { performScrollTo() }
        return this
    }
}
