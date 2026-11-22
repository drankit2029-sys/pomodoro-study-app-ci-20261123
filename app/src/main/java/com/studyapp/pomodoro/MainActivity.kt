package com.studyapp.pomodoro

import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.studyapp.pomodoro.ads.AdsConsentManager
import com.studyapp.pomodoro.ads.FullscreenVideoAdManager
import com.studyapp.pomodoro.focus.GlobalPermissionManager
import com.studyapp.pomodoro.ui.PomodoroScreen
import com.studyapp.pomodoro.ui.components.NativeAdCache
import com.studyapp.pomodoro.ui.theme.PomodoroTheme

class MainActivity : ComponentActivity() {

    private val engine: PomodoroTimerEngine
        get() = PomodoroTimerEngine.getInstance(this)

    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        engine.refreshPermissionStates()
        GlobalPermissionManager.onSystemPermissionResult(this)
    }

    private val permissionSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        engine.refreshPermissionStates()
        GlobalPermissionManager.onSystemPermissionResult(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        AdsConsentManager.gatherConsent(this)
        handleShortcutIntent(intent)

        setContent {
            val viewModel: PomodoroViewModel = viewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            PomodoroTheme(themeMode = state.themeMode) {
                val canRequestAds by AdsConsentManager.canRequestAds
                    .collectAsStateWithLifecycle()
                val showPrivacyOptions by AdsConsentManager.isPrivacyOptionsRequired
                    .collectAsStateWithLifecycle()
                val showFallbackVideoAd by FullscreenVideoAdManager.isFallbackVideoAdShowing
                    .collectAsStateWithLifecycle()

                LaunchedEffect(canRequestAds, state.isRunning, state.completion) {
                    if (canRequestAds) {
                        FullscreenVideoAdManager.preload(applicationContext)
                    }
                }
                LaunchedEffect(state.isFocusExitBlocked) {
                    applyFocusSystemBars(state.isFocusExitBlocked)
                }
                LaunchedEffect(state.isRunning, state.isMidSession) {
                    PomodoroShortcuts.publish(this@MainActivity, state)
                }

                PomodoroScreen(
                    state = state,
                    onToggle = {
                        if (state.requiresAdvancedFocusStartWarning) {
                            viewModel.setAdvancedFocusConfirmVisible(true)
                        } else {
                            requestNotificationPermissionIfNeeded()
                            viewModel.toggle()
                        }
                    },
                    onReset = viewModel::reset,
                    onSelectPhase = viewModel::selectPhase,
                    onUpdateSetting = viewModel::updateSetting,
                    onResetDaily = viewModel::resetDailySessions,
                    onUpdateAlarmEnabled = viewModel::updateAlarmEnabled,
                    onUpdateAlarmSound = viewModel::updateAlarmSound,
                    onUpdateAlarmVibration = viewModel::updateAlarmVibration,
                    onUpdateAlarmDuration = viewModel::updateAlarmDuration,
                    onPreviewAlarm = viewModel::previewAlarm,
                    onStopAlarm = viewModel::stopAlarm,
                    onDismissCompletion = viewModel::dismissCompletion,
                    onStartNext = {
                        val finishedFocusSession = state.completion?.finished == Phase.Focus
                        val isStartingBreak = state.completion?.let {
                            it.next == Phase.ShortBreak || it.next == Phase.LongBreak
                        } ?: false
                        val shouldShowBreakVideoAd = finishedFocusSession && isStartingBreak

                        requestNotificationPermissionIfNeeded()
                        NativeAdCache.onUserAction()
                        viewModel.dismissCompletion()

                        if (shouldShowBreakVideoAd) {
                            // Start the break only after the ad closes so the break timer is
                            // not silently running behind a full-screen video.
                            FullscreenVideoAdManager.showOnBreakStart(
                                activity = this@MainActivity,
                                isFocusLocked = state.isFocusExitBlocked,
                            ) {
                                viewModel.startNext()
                            }
                        } else {
                            viewModel.startNext()
                        }
                    },
                    onKeepFlowing = {
                        requestNotificationPermissionIfNeeded()
                        viewModel.startFlowOvertime()
                    },
                    onFinishFlow = {
                        requestNotificationPermissionIfNeeded()
                        viewModel.finishFlow()
                    },
                    showPrivacyOptions = showPrivacyOptions,
                    // Consent must be as easy to withdraw as to grant, including during a
                    // locked session; the UMP form is in-app and never leaves Pomodoro.
                    onOpenPrivacyOptions = {
                        AdsConsentManager.showPrivacyOptionsForm(this@MainActivity)
                    },
                    showFallbackVideoAd = showFallbackVideoAd,
                    onDismissFallbackVideoAd = FullscreenVideoAdManager::dismissFallbackAd,
                    onSelectSubject = viewModel::selectSubject,
                    onAddCustomSubject = viewModel::addCustomSubject,
                    onRemoveCustomSubject = viewModel::removeCustomSubject,
                    onSetShowYearHistory = viewModel::setShowYearHistory,
                    onExportCsv = {
                        viewModel.exportHistoryCsv(this@MainActivity)
                    },
                    onToggleMusic = viewModel::toggleMusic,
                    onSelectAmbientSoundscape = viewModel::selectAmbientSoundscape,
                    onSelectCustomMusic = viewModel::selectCustomMusicTrack,
                    onUpdateAmbientVolume = viewModel::updateAmbientVolume,
                    onPreviewAmbientSoundscape = viewModel::previewAmbientSoundscape,
                    onSetBlockingMode = viewModel::setBlockingMode,
                    onUpdateBlockedPackages = viewModel::updateBlockedPackages,
                    onSetBlockDuringBreaks = viewModel::setBlockDuringBreaks,
                    onUpdateStrictFocus = viewModel::updateStrictFocus,
                    onToggleAutoFocus = ::onToggleAutoFocusRequested,
                    onToggleAdvancedFocus = ::onToggleAdvancedFocusRequested,
                    onConfirmAdvancedFocus = ::onConfirmAdvancedFocusRequested,
                    onDismissAdvancedFocus = ::onDismissAdvancedFocusRequested,
                    onManualFocusToggle = ::onManualFocusToggle,
                    onRequestPermission = ::requestPermission,
                    onDismissDeclinedPermission = viewModel::dismissDeclinedPermission,
                    onConfirmPermissionPrompt = ::onConfirmPermissionPrompt,
                    onDismissPermissionPrompt = GlobalPermissionManager::onUserDismissedPrompt,
                    onAddTask = viewModel::addTask,
                    onSelectActiveTask = viewModel::selectActiveTask,
                    onToggleTaskCompleted = viewModel::toggleTaskCompleted,
                    onDeleteTask = viewModel::deleteTask,
                    onClearCompletedTasks = viewModel::clearCompletedTasks,
                    onSeedDemoHistory = viewModel::seedDemoHistory,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    /**
     * Runs a launcher-shortcut action exactly once. The action is cleared from the intent first so
     * a configuration change, a redelivery, or a later [onNewIntent] cannot repeat it.
     */
    private fun handleShortcutIntent(intent: Intent?) {
        val action = intent?.action ?: return
        if (!PomodoroShortcuts.isShortcutAction(action)) return
        intent.action = null
        engine.ensureDailyReset()
        when (action) {
            PomodoroShortcuts.ACTION_RESET_TIMER -> engine.reset()
            PomodoroShortcuts.ACTION_TOGGLE_TIMER -> startOrPauseFromShortcut()
            PomodoroShortcuts.ACTION_START_FOCUS -> {
                if (!engine.state.value.isRunning) startOrPauseFromShortcut()
            }
        }
    }

    /**
     * Mirrors the Start button: Advanced Focus shows its warning instead of silently locking the
     * device, and the notification permission is requested before the foreground service starts.
     */
    private fun startOrPauseFromShortcut() {
        if (engine.state.value.requiresAdvancedFocusStartWarning) {
            engine.setAdvancedFocusConfirmVisible(true)
            return
        }
        requestNotificationPermissionIfNeeded()
        engine.toggle()
    }

    override fun onStart() {
        super.onStart()
        engine.apply {
            ensureDailyReset()
            ensureServiceIfNeeded()
        }
    }

    override fun onResume() {
        super.onResume()
        AdsConsentManager.onHostActivityResumed()
        GlobalPermissionManager.onActivityResumed(this)
        engine.verifyAndEnforcePermissions()
        engine.refreshPermissionStates()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        reassertFocusLockIfNeeded()
    }

    override fun onStop() {
        super.onStop()
        reassertFocusLockIfNeeded()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyFocusSystemBars(engine.state.value.isFocusExitBlocked)
        } else {
            reassertFocusLockIfNeeded()
        }
    }

    private fun reassertFocusLockIfNeeded() {
        if (!engine.state.value.isFocusExitBlocked) return
        if (GlobalPermissionManager.isAwaitingSystemPermissionGrant) return
        if (FullscreenVideoAdManager.isShowingAd) return
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

    private fun onManualFocusToggle() {
        val state = engine.state.value
        if (state.isAdvancedFocusLocked) return
        if (state.isFocusEnabled) {
            engine.setFocusModeActive(false)
            return
        }
        GlobalPermissionManager.ensureFocusPermissions(
            context = this,
            onGranted = {
                engine.setFocusModeActive(true)
            },
            onDeclined = { type ->
                engine.setFocusModeActive(false)
                engine.onPermissionDeclined(type)
            },
        )
    }

    private fun onToggleAutoFocusRequested(targetEnabled: Boolean) {
        val state = engine.state.value
        if (!targetEnabled) {
            if (state.isAdvancedFocusLocked) return
            engine.setAutoFocusEnabled(false)
            return
        }
        GlobalPermissionManager.ensureFocusPermissions(
            context = this,
            onGranted = {
                engine.setAutoFocusEnabled(true)
                if (engine.state.value.isRunning) {
                    engine.setFocusModeActive(true)
                }
            },
            onDeclined = { type ->
                engine.setAutoFocusEnabled(false)
                engine.onPermissionDeclined(type)
            },
        )
    }

    private fun onToggleAdvancedFocusRequested() {
        val state = engine.state.value
        if (state.isAdvancedFocusLocked) return
        if (state.strictFocus.advancedFocusEnabled) {
            engine.setAdvancedFocusEnabled(false)
            return
        }
        GlobalPermissionManager.ensureFocusPermissions(
            context = this,
            onGranted = {
                engine.setAdvancedFocusEnabled(true)
            },
            onDeclined = { type ->
                engine.setAdvancedFocusEnabled(false)
                engine.onPermissionDeclined(type)
            },
        )
    }

    private fun onConfirmAdvancedFocusRequested() {
        engine.setAdvancedFocusConfirmVisible(false)
        requestNotificationPermissionIfNeeded()
        engine.toggle()
    }

    private fun onDismissAdvancedFocusRequested() {
        engine.setAdvancedFocusConfirmVisible(false)
    }

    private fun requestPermission(type: FocusPermissionType) {
        GlobalPermissionManager.requestPermissionsWithPrompt(
            context = this,
            permissions = listOf(type),
            onGranted = {
                engine.refreshPermissionStates()
            },
            onDeclined = engine::onPermissionDeclined,
        )
    }

    private fun onConfirmPermissionPrompt() {
        GlobalPermissionManager.onUserConfirmedPrompt(
            launchAccessibilitySettings = { intent ->
                runCatching { permissionSettingsLauncher.launch(intent) }
            },
            launchRuntimePermission = { permission ->
                runCatching { runtimePermissionLauncher.launch(permission) }
            },
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (!GlobalPermissionManager.shouldRequestNotificationPermission(this)) return
        GlobalPermissionManager.requestPermissionsWithPrompt(
            context = this,
            permissions = listOf(FocusPermissionType.NOTIFICATIONS),
            onGranted = { engine.refreshPermissionStates() },
            onDeclined = {},
        )
    }

    private fun applyFocusSystemBars(locked: Boolean) {
        runCatching {
            val decorView = window?.decorView ?: return
            val controller = WindowCompat.getInsetsController(window, decorView)
            if (locked) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val metrics = resources.displayMetrics
                    val bottom = (80 * metrics.density).toInt()
                    val side = (40 * metrics.density).toInt()
                    if (metrics.widthPixels > side * 2 && metrics.heightPixels > bottom) {
                        decorView.systemGestureExclusionRects = listOf(
                            Rect(
                                0,
                                metrics.heightPixels - bottom,
                                metrics.widthPixels,
                                metrics.heightPixels,
                            ),
                            Rect(0, 0, side, metrics.heightPixels),
                            Rect(
                                metrics.widthPixels - side,
                                0,
                                metrics.widthPixels,
                                metrics.heightPixels,
                            ),
                        )
                    }
                }
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    decorView.systemGestureExclusionRects = emptyList()
                }
            }
        }
    }
}
