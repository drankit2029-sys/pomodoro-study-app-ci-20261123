package com.studyapp.pomodoro

import com.studyapp.pomodoro.focus.AppBlocklistCodec
import com.studyapp.pomodoro.focus.BlockPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockPolicyTest {

    private val blocked = setOf("com.instagram.android", "com.google.android.youtube")

    @Test
    fun codecRoundTripsAndSortsPackages() {
        val packages = setOf("com.zhihu.android", "com.instagram.android")
        val encoded = AppBlocklistCodec.encode(packages)

        assertEquals("com.instagram.android\ncom.zhihu.android", encoded)
        assertEquals(packages, AppBlocklistCodec.decode(encoded))
    }

    @Test
    fun codecIgnoresBlankLinesAndWhitespace() {
        val decoded = AppBlocklistCodec.decode(
            "\n  com.instagram.android  \n\ncom.google.android.youtube\n   \n",
        )

        assertEquals(
            setOf("com.instagram.android", "com.google.android.youtube"),
            decoded,
        )
    }

    @Test
    fun codecDecodesMissingValueToEmptySet() {
        assertTrue(AppBlocklistCodec.decode(null).isEmpty())
        assertTrue(AppBlocklistCodec.decode("").isEmpty())
    }

    @Test
    fun defaultBlocklistIsNotEmptyAndContainsNoBlankEntries() {
        assertTrue(DefaultBlockedPackages.isNotEmpty())
        assertTrue(DefaultBlockedPackages.none { it.isBlank() })
    }

    @Test
    fun blocksSelectedAppOnlyWhileASessionIsActive() {
        assertTrue(
            BlockPolicy.shouldBlock(
                packageName = "com.instagram.android",
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
                blockedPackages = blocked,
                mode = FocusBlockingMode.BLOCKLIST,
                blockingActive = false,
                emergencyPassActive = false,
                isLauncher = false,
                isInputMethod = false,
            ),
        )
    }

    @Test
    fun allowsAppsThatAreNotOnTheBlocklist() {
        assertFalse(
            BlockPolicy.shouldBlock(
                packageName = "com.google.android.apps.docs",
                blockedPackages = blocked,
                mode = FocusBlockingMode.BLOCKLIST,
                blockingActive = true,
                emergencyPassActive = false,
                isLauncher = false,
                isInputMethod = false,
            ),
        )
    }

    @Test
    fun emergencyPassUnblocksEverythingForItsDuration() {
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
    fun offAndStrictModesNeverUseTheBlocklist() {
        for (mode in listOf(FocusBlockingMode.OFF, FocusBlockingMode.STRICT)) {
            assertFalse(
                "mode $mode must not enforce the blocklist",
                BlockPolicy.shouldBlock(
                    packageName = "com.instagram.android",
                    blockedPackages = blocked,
                    mode = mode,
                    blockingActive = true,
                    emergencyPassActive = false,
                    isLauncher = false,
                    isInputMethod = false,
                ),
            )
        }
    }

    @Test
    fun neverBlocksSafetyOrSystemSurfaces() {
        val alwaysAllowed = listOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.dialer",
            "com.android.incallui",
            "com.android.server.telecom",
        )
        for (packageName in alwaysAllowed) {
            assertFalse(
                "$packageName must never be blocked",
                BlockPolicy.shouldBlock(
                    packageName = packageName,
                    blockedPackages = blocked + packageName,
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
    fun neverBlocksLauncherOrKeyboard() {
        assertFalse(
            BlockPolicy.shouldBlock(
                packageName = "com.instagram.android",
                blockedPackages = blocked,
                mode = FocusBlockingMode.BLOCKLIST,
                blockingActive = true,
                emergencyPassActive = false,
                isLauncher = true,
                isInputMethod = false,
            ),
        )
        assertFalse(
            BlockPolicy.shouldBlock(
                packageName = "com.instagram.android",
                blockedPackages = blocked,
                mode = FocusBlockingMode.BLOCKLIST,
                blockingActive = true,
                emergencyPassActive = false,
                isLauncher = false,
                isInputMethod = true,
            ),
        )
    }

    @Test
    fun blockingStateMatchesTheSessionModel() {
        val focusRunning = PomodoroUiState(
            phase = Phase.Focus,
            isRunning = true,
            isFocusModeActive = true,
            strictFocus = StrictFocusSettings(blockingMode = FocusBlockingMode.BLOCKLIST),
        )
        assertTrue(focusRunning.isAppBlockingActive)
        assertFalse(focusRunning.isFocusExitBlocked)

        val idle = focusRunning.copy(isRunning = false, remainingMillisByPhase = emptyMap())
        assertFalse(idle.isAppBlockingActive)

        val strict = focusRunning.copy(
            strictFocus = StrictFocusSettings(blockingMode = FocusBlockingMode.STRICT),
        )
        assertTrue(strict.isAppBlockingActive)
        assertTrue(strict.isFocusExitBlocked)

        val off = focusRunning.copy(
            strictFocus = StrictFocusSettings(blockingMode = FocusBlockingMode.OFF),
        )
        assertFalse(off.isAppBlockingActive)
    }

    @Test
    fun breaksUnblockUnlessTheUserOptedIn() {
        val breakRunning = PomodoroUiState(
            phase = Phase.ShortBreak,
            isRunning = true,
            isFocusModeActive = true,
            strictFocus = StrictFocusSettings(blockingMode = FocusBlockingMode.BLOCKLIST),
        )
        assertFalse(breakRunning.isAppBlockingActive)

        val breakLocked = breakRunning.copy(
            strictFocus = breakRunning.strictFocus.copy(blockDuringBreaks = true),
        )
        assertTrue(breakLocked.isAppBlockingActive)
    }
}
