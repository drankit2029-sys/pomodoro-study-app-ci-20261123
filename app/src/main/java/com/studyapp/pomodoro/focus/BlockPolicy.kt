package com.studyapp.pomodoro.focus

import com.studyapp.pomodoro.FocusBlockingMode

/** Coarse Play Store style category used to suggest likely distractions on first run. */
enum class AppCategory {
    SOCIAL,
    VIDEO,
    GAME,
    OTHER,
}

/**
 * A launchable app the user can put on the blocklist. Pure data so the policy and codec can
 * be unit-tested without Android.
 */
data class BlockableApp(
    val packageName: String,
    val label: String,
    val category: AppCategory = AppCategory.OTHER,
)

/**
 * Stable, dependency-free encoding for the blocked package set.
 *
 * Package names cannot contain newlines, so a newline-separated list round-trips safely
 * through SharedPreferences and stays readable in a backup/export.
 */
object AppBlocklistCodec {

    private const val SEPARATOR = "\n"

    fun encode(packages: Set<String>): String =
        packages.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
            .joinToString(SEPARATOR)

    fun decode(raw: String?): Set<String> =
        raw.orEmpty()
            .split(SEPARATOR)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}

/**
 * Decides whether an app should be interrupted right now.
 *
 * Kept deliberately free of Android imports: the AccessibilityService supplies the
 * foreground package and the flags, and the JVM tests exercise the same function.
 */
object BlockPolicy {

    /**
     * Packages that must never be blocked, whatever the user selected. Blocking the
     * permission controller, the dialer or an in-call screen would break safety and
     * platform security flows (and is a Play policy problem).
     */
    private val ALWAYS_ALLOWED_PACKAGES = setOf(
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

    fun isAlwaysAllowed(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        if (normalized in ALWAYS_ALLOWED_PACKAGES) return true
        return normalized.contains("incallui") ||
            normalized.contains("emergency") ||
            normalized.contains("permissioncontroller")
    }

    /**
     * @param blockingActive true while the current session should enforce the blocklist.
     * @param emergencyPassActive true while the user's one-off 60 second pass is running.
     * @param isLauncher the device's home app is never interrupted.
     * @param isInputMethod the keyboard is never interrupted.
     */
    fun shouldBlock(
        packageName: String,
        blockedPackages: Set<String>,
        mode: FocusBlockingMode,
        blockingActive: Boolean,
        emergencyPassActive: Boolean,
        isLauncher: Boolean,
        isInputMethod: Boolean,
    ): Boolean {
        if (mode != FocusBlockingMode.BLOCKLIST) return false
        if (!blockingActive || emergencyPassActive) return false
        if (packageName.isBlank()) return false
        if (isLauncher || isInputMethod) return false
        if (isAlwaysAllowed(packageName)) return false
        return packageName in blockedPackages
    }
}
