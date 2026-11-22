package com.studyapp.pomodoro.focus

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.studyapp.pomodoro.BlockedActivity
import com.studyapp.pomodoro.FocusBlockingMode
import com.studyapp.pomodoro.MainActivity
import com.studyapp.pomodoro.PomodoroTimerEngine
import com.studyapp.pomodoro.ads.AdsConsentManager
import com.studyapp.pomodoro.ads.FullscreenVideoAdManager

/**
 * Focus Mode enforcement.
 *
 * Two policies share this service:
 *
 * - **Per-app blocklist** (default): the user picks distracting apps. When one of them comes
 *   to the foreground during a focus session, the service sends the user home and shows
 *   [BlockedActivity] with the remaining time and a way back to the timer. Everything else —
 *   notes, PDFs, calculators, messaging — keeps working.
 * - **Strict lockdown** (opt-in, legacy): leaving Pomodoro is blocked entirely by consuming
 *   navigation keys and pulling [MainActivity] back to the front.
 *
 * Both policies skip system-critical surfaces (dialer, in-call, emergency, permission
 * controller), input methods, the launcher, ads and ad/consent journeys.
 */
class FocusLockAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastRelaunchElapsed: Long = 0L
    private var lastForegroundPackage: String = ""
    private var imeCacheElapsed: Long = 0L
    private var imePackages: Set<String> = emptySet()
    private var homeCacheElapsed: Long = 0L
    private var homePackages: Set<String> = emptySet()
    private var lastBlockedPackage: String = ""
    private var lastBlockElapsed: Long = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val engine = PomodoroTimerEngine.getInstance(this)
        // Only the strict lockdown consumes navigation keys. The blocklist must never make
        // Back/Home/Recents feel broken.
        if (!engine.state.value.isFocusExitBlocked) return false
        if (GlobalPermissionManager.isAwaitingSystemPermissionGrant) return false
        // Never swallow navigation keys while an ad (or the advertiser's landing page) owns
        // the screen: that turns a legitimate click into an invalid-traffic signal.
        if (FullscreenVideoAdManager.isShowingAd) return false
        if (AdsConsentManager.isInExternalAdOrPolicyJourney) return false
        // onKeyEvent has no package argument; fall back to the last window we observed so
        // Back/Home still work on the in-call screen, emergency dialer and permission UI.
        if (lastForegroundPackage.isNotBlank() &&
            isSystemCriticalOrCallPackage(lastForegroundPackage)
        ) {
            return false
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SEARCH,
            KeyEvent.KEYCODE_ASSIST,
            KeyEvent.KEYCODE_VOICE_ASSIST,
            KeyEvent.KEYCODE_ALL_APPS,
            KeyEvent.KEYCODE_NOTIFICATION,
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_EXPLORER,
            KeyEvent.KEYCODE_ENVELOPE,
            KeyEvent.KEYCODE_CONTACTS,
            KeyEvent.KEYCODE_CALENDAR,
            KeyEvent.KEYCODE_CALCULATOR,
            KeyEvent.KEYCODE_CAMERA,
            -> true
            else -> false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val engine = PomodoroTimerEngine.getInstance(this)
        val state = engine.state.value
        if (!state.isFocusEnabled) return
        if (GlobalPermissionManager.isAwaitingSystemPermissionGrant) return
        // An interstitial, the advertiser's landing page, or the AdChoices page must be
        // allowed to stay in front; pulling MainActivity over them is click interference.
        if (FullscreenVideoAdManager.isShowingAd) return
        if (AdsConsentManager.isInExternalAdOrPolicyJourney) return

        val safeEvent = event ?: return
        val pkg = safeEvent.packageName?.toString() ?: return
        val eventType = safeEvent.eventType
        if (pkg != packageName) lastForegroundPackage = pkg
        if (pkg == packageName) return
        if (isInputMethodPackage(pkg)) return
        if (isSystemCriticalOrCallPackage(pkg)) return

        if (state.strictFocus.blockingMode == FocusBlockingMode.STRICT) {
            enforceStrictLock(pkg, eventType)
            return
        }

        // The launcher is never interrupted in blocklist mode; otherwise the user would be
        // trapped on the block screen with no way back to their home screen.
        if (isLauncherPackage(pkg)) return
        if (!state.isAppBlockingActive) return

        val shouldBlock = BlockPolicy.shouldBlock(
            packageName = pkg,
            blockedPackages = state.strictFocus.blockedPackages,
            mode = state.strictFocus.blockingMode,
            blockingActive = state.isAppBlockingActive,
            emergencyPassActive = state.isEmergencyPassActive,
            isLauncher = false,
            isInputMethod = false,
        )
        if (shouldBlock) {
            blockForegroundApp(pkg)
        }
    }

    private fun enforceStrictLock(pkg: String, eventType: Int) {
        if (pkg == "com.android.systemui") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            } else {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            bringPomodoroToFrontThrottled()
            return
        }

        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            -> bringPomodoroToFront()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> bringPomodoroToFrontThrottled()
            else -> Unit
        }
    }

    /**
     * Sends the blocked app to the background first, then shows the calm block screen over
     * the launcher. Doing it in that order means Back/Recents reveal the home screen instead
     * of dropping the user straight back into the app they are trying to avoid.
     */
    private fun blockForegroundApp(pkg: String) {
        val now = SystemClock.elapsedRealtime()
        if (pkg == lastBlockedPackage && now - lastBlockElapsed < BLOCK_THROTTLE_MILLIS) return
        lastBlockedPackage = pkg
        lastBlockElapsed = now

        performGlobalAction(GLOBAL_ACTION_HOME)
        mainHandler.postDelayed(
            {
                val intent = Intent(this, BlockedActivity::class.java).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                    putExtra(BlockedActivity.EXTRA_BLOCKED_PACKAGE, pkg)
                }
                runCatching { startActivity(intent) }
            },
            BLOCK_SCREEN_DELAY_MILLIS,
        )
    }

    /**
     * Never block emergency dialers, incoming/active phone call screens, or Android's
     * runtime permission controller so Focus Mode never interferes with user safety or
     * platform security controls required by Google Play policy.
     */
    private fun isSystemCriticalOrCallPackage(pkg: String): Boolean {
        val normalized = pkg.lowercase()
        return normalized in SYSTEM_CRITICAL_PACKAGES ||
            normalized.contains("incallui") ||
            normalized.contains("emergency") ||
            normalized.contains("permissioncontroller")
    }

    private fun isInputMethodPackage(pkg: String): Boolean {
        // Accessibility events can arrive every 50 ms; querying Settings.Secure and the IME
        // list per event costs two binder round-trips. Cache the package set for 5 s.
        val now = SystemClock.elapsedRealtime()
        if (now - imeCacheElapsed > 5_000L) {
            imeCacheElapsed = now
            imePackages = runCatching {
                val defaultIme = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.DEFAULT_INPUT_METHOD,
                )
                val imm = getSystemService(InputMethodManager::class.java)
                buildSet {
                    defaultIme?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { add(it) }
                    imm?.enabledInputMethodList?.forEach { add(it.packageName) }
                }
            }.getOrDefault(emptySet())
        }
        return pkg in imePackages
    }

    /** The home app is never interrupted; otherwise the user would be trapped. */
    private fun isLauncherPackage(pkg: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - homeCacheElapsed > 60_000L) {
            homeCacheElapsed = now
            homePackages = runCatching {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.resolveActivity(
                        intent,
                        PackageManager.ResolveInfoFlags.of(
                            PackageManager.MATCH_DEFAULT_ONLY.toLong(),
                        ),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                }
                resolved?.activityInfo?.packageName?.let(::setOf) ?: emptySet()
            }.getOrDefault(emptySet())
        }
        return pkg in homePackages
    }

    private fun bringPomodoroToFrontThrottled() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRelaunchElapsed < 180L) return
        lastRelaunchElapsed = now
        bringPomodoroToFront()
    }

    private fun bringPomodoroToFront() {
        lastRelaunchElapsed = SystemClock.elapsedRealtime()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
        }
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    companion object {
        private const val BLOCK_THROTTLE_MILLIS = 1_200L
        private const val BLOCK_SCREEN_DELAY_MILLIS = 140L

        private val SYSTEM_CRITICAL_PACKAGES = setOf(
            "com.android.incallui",
            "com.google.android.dialer",
            "com.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
        )
    }
}
