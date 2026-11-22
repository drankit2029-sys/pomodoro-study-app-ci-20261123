package com.studyapp.pomodoro.focus

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.studyapp.pomodoro.FocusPermissionType
import com.studyapp.pomodoro.PermissionPromptState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single global permission manager for the application.
 *
 * Focus Mode, Auto Focus, and Advanced Focus only require the Android Accessibility Service
 * permission ([FocusPermissionType.ACCESSIBILITY_LOCK]) to pause the user's blocked apps
 * during focus sessions (and, in Strict lock, to keep navigation inside Pomodoro).
 *
 * Before navigating the user to the Accessibility Settings menu or triggering Android's
 * runtime permission modal, this manager always displays an explanatory prompt telling the
 * user **what to do** and **why the permission is needed**.
 */
object GlobalPermissionManager {

    private val _promptState = MutableStateFlow<PermissionPromptState?>(null)
    val promptState: StateFlow<PermissionPromptState?> = _promptState.asStateFlow()

    private data class PermissionRequest(
        val types: List<FocusPermissionType>,
        val onGranted: () -> Unit,
        val onDeclined: (FocusPermissionType) -> Unit,
    )

    private val pendingQueue = ArrayDeque<FocusPermissionType>()
    private val requestQueue = ArrayDeque<PermissionRequest>()
    private var activePermissionType: FocusPermissionType? = null
    private var activeRequest: PermissionRequest? = null

    /**
     * Process-wide (not per-Activity) so a configuration change cannot re-trigger the
     * notification prompt after the user has already answered it.
     */
    @Volatile
    private var notificationPermissionRequestedThisProcess: Boolean = false

    /**
     * True while the user has been intentionally sent to Android's system settings or runtime
     * permission modal from our prompt, so [FocusLockAccessibilityService] and [MainActivity]
     * do not pull the user away from the Settings screen while they are granting permission.
     */
    @Volatile
    var isAwaitingSystemPermissionGrant: Boolean = false
        private set

    fun hasAccessibilityPermission(context: Context): Boolean {
        val expected = "${context.packageName}/${FocusLockAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Returns true exactly once per process while the notification permission is missing, so
     * rotation or a second completion cannot re-prompt after a decline.
     */
    fun shouldRequestNotificationPermission(context: Context): Boolean {
        if (notificationPermissionRequestedThisProcess) return false
        if (hasNotificationPermission(context)) return false
        notificationPermissionRequestedThisProcess = true
        return true
    }

    fun isPermissionGranted(context: Context, type: FocusPermissionType): Boolean = when (type) {
        FocusPermissionType.ACCESSIBILITY_LOCK -> hasAccessibilityPermission(context)
        FocusPermissionType.NOTIFICATIONS -> hasNotificationPermission(context)
    }

    fun getMissingFocusPermissions(context: Context): List<FocusPermissionType> {
        return if (hasAccessibilityPermission(context)) {
            emptyList()
        } else {
            listOf(FocusPermissionType.ACCESSIBILITY_LOCK)
        }
    }

    fun hasAllFocusPermissions(context: Context): Boolean =
        getMissingFocusPermissions(context).isEmpty()

    fun ensureFocusPermissions(
        context: Context,
        onGranted: () -> Unit,
        onDeclined: (FocusPermissionType) -> Unit = {},
    ) {
        val missing = getMissingFocusPermissions(context)
        if (missing.isEmpty()) {
            onGranted()
            return
        }
        requestPermissionsWithPrompt(
            context = context,
            permissions = missing,
            onGranted = onGranted,
            onDeclined = onDeclined,
        )
    }

    fun requestPermissionsWithPrompt(
        context: Context,
        permissions: List<FocusPermissionType>,
        onGranted: () -> Unit = {},
        onDeclined: (FocusPermissionType) -> Unit = {},
    ) {
        val missing = permissions.filterNot { isPermissionGranted(context, it) }.distinct()
        if (missing.isEmpty()) {
            onGranted()
            return
        }
        val request = PermissionRequest(missing, onGranted, onDeclined)
        // A second request (e.g. the completion screen asking for notifications while the
        // Focus disclosure is open) must queue behind the active one instead of replacing
        // its callbacks, which would strand the Focus request.
        if (activeRequest != null || activePermissionType != null) {
            requestQueue.addLast(request)
            return
        }
        activeRequest = request
        pendingQueue.clear()
        pendingQueue.addAll(missing)
        showNextPromptOrFinish(context)
    }

    private fun showNextPromptOrFinish(context: Context) {
        while (pendingQueue.isNotEmpty()) {
            val next = pendingQueue.removeFirst()
            if (!isPermissionGranted(context, next)) {
                activePermissionType = next
                _promptState.value = buildPromptState(next)
                return
            }
        }
        activePermissionType = null
        _promptState.value = null
        isAwaitingSystemPermissionGrant = false
        val grantedCallback = activeRequest?.onGranted
        activeRequest = null
        pendingQueue.clear()
        startNextQueuedRequest(context)
        grantedCallback?.invoke()
    }

    private fun startNextQueuedRequest(context: Context) {
        val next = requestQueue.removeFirstOrNull() ?: return
        activeRequest = next
        pendingQueue.addAll(next.types)
        showNextPromptOrFinish(context)
    }

    fun onUserConfirmedPrompt(
        launchAccessibilitySettings: (Intent) -> Unit,
        launchRuntimePermission: (String) -> Unit,
    ) {
        val current = activePermissionType ?: return
        _promptState.value = null
        isAwaitingSystemPermissionGrant = true
        when (current) {
            FocusPermissionType.ACCESSIBILITY_LOCK -> {
                launchAccessibilitySettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            FocusPermissionType.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launchRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    isAwaitingSystemPermissionGrant = false
                }
            }
            else -> {
                isAwaitingSystemPermissionGrant = false
            }
        }
    }

    fun onUserDismissedPrompt() {
        val declinedType = activePermissionType ?: FocusPermissionType.ACCESSIBILITY_LOCK
        val declinedCb = activeRequest?.onDeclined
        pendingQueue.clear()
        requestQueue.clear()
        activePermissionType = null
        activeRequest = null
        _promptState.value = null
        isAwaitingSystemPermissionGrant = false
        declinedCb?.invoke(declinedType)
    }

    fun onSystemPermissionResult(context: Context) {
        val current = activePermissionType ?: run {
            isAwaitingSystemPermissionGrant = false
            return
        }
        isAwaitingSystemPermissionGrant = false
        if (isPermissionGranted(context, current)) {
            activePermissionType = null
            showNextPromptOrFinish(context)
        } else {
            val declinedCb = activeRequest?.onDeclined
            pendingQueue.clear()
            requestQueue.clear()
            activePermissionType = null
            activeRequest = null
            _promptState.value = null
            declinedCb?.invoke(current)
        }
    }

    /**
     * Handles the case where the user returns from Accessibility Settings via Home/Recents
     * switcher instead of pressing Back.
     */
    fun onExternalPickerLaunched() {
        isAwaitingSystemPermissionGrant = true
    }

    fun onExternalPickerResult() {
        if (activePermissionType == null) {
            isAwaitingSystemPermissionGrant = false
        }
    }

    fun onActivityResumed(context: Context) {
        if (isAwaitingSystemPermissionGrant && activePermissionType != null) {
            onSystemPermissionResult(context)
        } else if (activePermissionType == null) {
            isAwaitingSystemPermissionGrant = false
        }
    }

    fun buildPromptState(type: FocusPermissionType): PermissionPromptState = when (type) {
        FocusPermissionType.ACCESSIBILITY_LOCK -> PermissionPromptState(
            permissionType = FocusPermissionType.ACCESSIBILITY_LOCK,
            title = "AccessibilityService API Disclosure",
            whyNeeded = "Pomodoro uses the Android AccessibilityService API only while Focus Mode is on. " +
                "It detects which app comes to the foreground so it can pause the apps you chose to " +
                "block during a study session. If you turn on Strict lock, it also detects navigation " +
                "key presses (Back, Home, Recents, and the notification shade) to keep you inside " +
                "Pomodoro for the session. Pomodoro is configured with " +
                "canRetrieveWindowContent=\"false\" and never reads screen content, keystrokes, or " +
                "passwords, and never collects, stores, or shares any personal or sensitive user data " +
                "through the AccessibilityService API.",
            whatToDo = "1. Tap \"Accept & Continue\" below to consent and open Android Accessibility Settings.\n" +
                "2. Select \"Pomodoro\" (under Downloaded apps or Installed services).\n" +
                "3. Turn ON \"Use Pomodoro\", confirm the system prompt, and return here (or tap \"Decline\" to cancel).",
            confirmLabel = "Accept & Continue",
        )
        FocusPermissionType.NOTIFICATIONS -> PermissionPromptState(
            permissionType = FocusPermissionType.NOTIFICATIONS,
            title = "Timer Notifications",
            whyNeeded = "Pomodoro uses notifications to display your live countdown timer in " +
                "the status bar and alert you when a focus or break session finishes.",
            whatToDo = "1. Tap \"Continue\" below.\n" +
                "2. Select \"Allow\" on Android's permission prompt.",
            confirmLabel = "Continue",
        )
        else -> PermissionPromptState(
            permissionType = type,
            title = "Permission Required",
            whyNeeded = "This permission is required to enable Focus Mode protection.",
            whatToDo = "Tap \"Continue\" below and grant the permission in the next screen.",
            confirmLabel = "Continue",
        )
    }
}
