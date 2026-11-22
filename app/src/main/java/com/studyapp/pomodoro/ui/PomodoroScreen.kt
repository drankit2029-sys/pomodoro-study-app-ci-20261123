package com.studyapp.pomodoro.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.util.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.IntentCompat
import com.studyapp.pomodoro.AmbientSoundscape
import com.studyapp.pomodoro.AppThemeMode
import com.studyapp.pomodoro.BuildConfig
import com.studyapp.pomodoro.focus.GlobalPermissionManager
import com.studyapp.pomodoro.DEFAULT_ALARM_SOUND
import com.studyapp.pomodoro.DayHistoryEntry
import com.studyapp.pomodoro.DefaultSettings
import com.studyapp.pomodoro.FocusBlockingMode
import com.studyapp.pomodoro.FocusPermissionType
import com.studyapp.pomodoro.HISTORY_WINDOW_DAYS
import com.studyapp.pomodoro.HISTORY_WINDOW_DAYS_YEAR
import com.studyapp.pomodoro.MonthHistoryGroup
import com.studyapp.pomodoro.Phase
import com.studyapp.pomodoro.PomodoroUiState
import com.studyapp.pomodoro.SILENT_ALARM_SOUND
import com.studyapp.pomodoro.SessionsPerCycle
import com.studyapp.pomodoro.SettingBounds
import com.studyapp.pomodoro.StrictFocusSettings
import com.studyapp.pomodoro.StudyTask
import com.studyapp.pomodoro.heading
import com.studyapp.pomodoro.sessionGreenIntensity
import com.studyapp.pomodoro.ui.components.AdvancedFocusLockWarningDialog
import com.studyapp.pomodoro.ui.components.AlarmSettingsDialog
import com.studyapp.pomodoro.ui.components.BotanicalBranch
import com.studyapp.pomodoro.ui.components.CompletionDialog
import com.studyapp.pomodoro.ui.components.FocusAndMusicPillsRow
import com.studyapp.pomodoro.ui.components.FullscreenVideoAdDialog
import com.studyapp.pomodoro.ui.components.PermissionExplanationDialog
import com.studyapp.pomodoro.ui.components.PrivacyPolicyDialog
import com.studyapp.pomodoro.ui.components.ProgressRing
import com.studyapp.pomodoro.ui.components.SoftButton
import com.studyapp.pomodoro.ui.components.SoftButtonVariant
import com.studyapp.pomodoro.ui.components.SoftIconButton
import com.studyapp.pomodoro.ui.components.StudyTasksAndFocusStudioCard
import com.studyapp.pomodoro.ui.components.SubjectBreakdownSection
import com.studyapp.pomodoro.ui.components.softRaised
import com.studyapp.pomodoro.ui.theme.Border
import com.studyapp.pomodoro.ui.theme.Forest
import com.studyapp.pomodoro.ui.theme.ForestDeep
import com.studyapp.pomodoro.ui.theme.Ink
import com.studyapp.pomodoro.ui.theme.Ivory
import com.studyapp.pomodoro.ui.theme.Mint
import com.studyapp.pomodoro.ui.theme.MintLight
import com.studyapp.pomodoro.ui.theme.Muted
import com.studyapp.pomodoro.ui.theme.MutedLight
import com.studyapp.pomodoro.ui.theme.Paper
import com.studyapp.pomodoro.ui.theme.Parchment
import com.studyapp.pomodoro.ui.theme.Stone
import com.studyapp.pomodoro.ui.theme.StoneLight
import com.studyapp.pomodoro.ui.theme.SyncPomodoroPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val ShortDateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val DetailedDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

@Composable
@Suppress("UNUSED_PARAMETER")
fun PomodoroScreen(
    state: PomodoroUiState,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onSelectPhase: (Phase) -> Unit,
    onUpdateSetting: (Phase, Int) -> Unit,
    onResetDaily: () -> Unit,
    onUpdateAlarmEnabled: (Boolean) -> Unit,
    onUpdateAlarmSound: (String) -> Unit,
    onUpdateAlarmVibration: (Boolean) -> Unit,
    onUpdateAlarmDuration: (Int) -> Unit,
    onPreviewAlarm: () -> Unit,
    onStopAlarm: () -> Unit,
    onDismissCompletion: () -> Unit,
    onStartNext: () -> Unit,
    onKeepFlowing: () -> Unit = {},
    onFinishFlow: () -> Unit = {},
    showPrivacyOptions: Boolean = false,
    onOpenPrivacyOptions: () -> Unit = {},
    showFallbackVideoAd: Boolean = false,
    onDismissFallbackVideoAd: () -> Unit = {},
    onSelectSubject: (String) -> Unit = {},
    onAddCustomSubject: (String, Long) -> Boolean = { _, _ -> false },
    onRemoveCustomSubject: (String) -> Unit = {},
    onSetShowYearHistory: (Boolean) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onToggleMusic: () -> Unit = {},
    onSelectAmbientSoundscape: (AmbientSoundscape) -> Unit = {},
    onSelectCustomMusic: (String, String) -> Unit = { _, _ -> },
    onUpdateAmbientVolume: (Float) -> Unit = {},
    onPreviewAmbientSoundscape: () -> Unit = {},
    onSetBlockingMode: (FocusBlockingMode) -> Unit = {},
    onUpdateBlockedPackages: (Set<String>) -> Unit = {},
    onSetBlockDuringBreaks: (Boolean) -> Unit = {},
    onUpdateStrictFocus: (StrictFocusSettings) -> Unit = {},
    onToggleAutoFocus: (Boolean) -> Unit = {},
    onToggleAdvancedFocus: () -> Unit = {},
    onConfirmAdvancedFocus: () -> Unit = {},
    onDismissAdvancedFocus: () -> Unit = {},
    onManualFocusToggle: () -> Unit = {},
    onAddTask: (String, Int) -> Boolean = { _, _ -> false },
    onSelectActiveTask: (String?) -> Unit = {},
    onToggleTaskCompleted: (String) -> Unit = {},
    onDeleteTask: (String) -> Unit = {},
    onClearCompletedTasks: () -> Unit = {},
    onSeedDemoHistory: () -> Unit = {},
    onRequestPermission: (FocusPermissionType) -> Unit = {},
    onDismissDeclinedPermission: () -> Unit = {},
    onConfirmPermissionPrompt: () -> Unit = {},
    onDismissPermissionPrompt: () -> Unit = {},
) {
    SyncPomodoroPalette(state.themeMode)

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var showAlarmSettings by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }
    var showCustomTimeDialog by rememberSaveable { mutableStateOf(false) }
    var selectedDayEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    val clearDaySelection = { selectedDayEpoch = null }

    val ringtonePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        GlobalPermissionManager.onExternalPickerResult()
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val pickedUri = IntentCompat.getParcelableExtra(
                    data,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java,
                )
                onUpdateAlarmSound(pickedUri?.toString() ?: SILENT_ALARM_SOUND)
            }
        }
    }

    val storageMusicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        GlobalPermissionManager.onExternalPickerResult()
        if (result.resultCode == Activity.RESULT_OK) {
            val pickedUri = result.data?.data
            if (pickedUri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        pickedUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                val title = resolveAudioTrackTitle(context, pickedUri, fallback = "Storage audio")
                onSelectCustomMusic(pickedUri.toString(), title)
            }
        }
    }

    val inbuiltMusicPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        GlobalPermissionManager.onExternalPickerResult()
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                val pickedUri = IntentCompat.getParcelableExtra(
                    data,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java,
                )
                if (pickedUri != null) {
                    val title = resolveAudioTrackTitle(context, pickedUri, fallback = "Inbuilt tone")
                    onSelectCustomMusic(pickedUri.toString(), title)
                }
            }
        }
    }
    SideEffect { view.keepScreenOn = state.isRunning }
    DisposableEffect(Unit) {
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(state.completion) {
        if (state.completion != null) {
            showAlarmSettings = false
            showCustomTimeDialog = false
            selectedDayEpoch = null
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val openAlarmSettings = {
        if (!state.isFocusExitBlocked) {
            selectedDayEpoch = null
            showAlarmSettings = true
        }
    }
    val openCustomTimeModal = {
        if (state.canAdjustDuration) {
            selectedDayEpoch = null
            showCustomTimeDialog = true
        }
    }
    val openRingtonePicker: () -> Unit = {
        if (!state.isFocusExitBlocked) {
            val existingUri = when (state.alarm.sound) {
                DEFAULT_ALARM_SOUND -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                SILENT_ALARM_SOUND -> null
                else -> runCatching { Uri.parse(state.alarm.sound) }.getOrNull()
            }
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALARM,
                )
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                )
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Pomodoro alarm")
            }
            GlobalPermissionManager.onExternalPickerLaunched()
            runCatching { ringtonePicker.launch(intent) }
                .onFailure { GlobalPermissionManager.onExternalPickerResult() }
        }
    }
    LaunchedEffect(state.isFocusExitBlocked) {
        if (state.isFocusExitBlocked) {
            showAlarmSettings = false
            showPrivacyPolicy = false
        }
    }
    val openStorageMusicPicker: () -> Unit = {
        if (!state.isFocusExitBlocked && !state.isAdvancedFocusLocked) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
            }
            GlobalPermissionManager.onExternalPickerLaunched()
            runCatching { storageMusicPicker.launch(intent) }
                .onFailure { GlobalPermissionManager.onExternalPickerResult() }
        }
    }
    val openInbuiltMusicPicker: () -> Unit = {
        if (!state.isFocusExitBlocked && !state.isAdvancedFocusLocked) {
            val existingUri = state.customMusicUri.takeIf { it.isNotBlank() }
                ?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALL,
                )
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                )
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select inbuilt music or tone")
            }
            GlobalPermissionManager.onExternalPickerLaunched()
            runCatching { inbuiltMusicPicker.launch(intent) }
                .onFailure { GlobalPermissionManager.onExternalPickerResult() }
        }
    }

    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp
    val isLandscape = screenWidth > screenHeight
    val minDimension = minOf(screenWidth, screenHeight)
    val maxDimension = maxOf(screenWidth, screenHeight)
    val aspectRatio = maxDimension.value / minDimension.value.coerceAtLeast(1f)
    val isTablet = minDimension >= 600.dp
    val isCompactPortrait = !isLandscape && screenWidth < 390.dp
    val isCompactLandscape = isLandscape && screenHeight < 440.dp
    val useFoldableSquare = minDimension >= 540.dp && aspectRatio < 1.22f
    val useTabletLandscape = isTablet && isLandscape && !useFoldableSquare
    val useTabletPortrait = isTablet && !isLandscape && !useFoldableSquare
    val useTwoPane = isLandscape && !useTabletLandscape && !useFoldableSquare &&
        screenWidth >= 520.dp && screenHeight >= 280.dp

    val dismissSelectionModifier = if (selectedDayEpoch != null) {
        Modifier.pointerInput(selectedDayEpoch) {
            detectTapGestures { selectedDayEpoch = null }
        }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Ivory)
            .then(dismissSelectionModifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        FocusThemeAmbientOverlay(
            themeMode = state.themeMode,
            isRunning = state.isRunning,
            isLocked = state.isAdvancedFocusLocked,
            modifier = Modifier.fillMaxSize(),
        )

        BotanicalBranch(
            filledSessions = state.filledDots,
            isFlowOvertime = state.isFlowOvertime,
            themeMode = state.themeMode,
            isAnimating = state.isRunning || state.themeMode != AppThemeMode.SANCTUARY,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = if (isLandscape) 44.dp else 52.dp, y = (-18).dp)
                .size(if (isLandscape) 112.dp else 132.dp),
        )

        BackHandler(enabled = state.isFocusExitBlocked) { }

        when {
            useTabletLandscape -> TabletLandscapeContent(
                state = state,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                selectedDayEpoch = selectedDayEpoch,
                onSelectDay = { selectedDayEpoch = it },
                onDismissDaySelection = clearDaySelection,
                onToggle = {
                    clearDaySelection()
                    onToggle()
                },
                onReset = {
                    clearDaySelection()
                    onReset()
                },
                onFinishFlow = {
                    clearDaySelection()
                    onFinishFlow()
                },
                onSelectPhase = {
                    clearDaySelection()
                    onSelectPhase(it)
                },
                onUpdateSetting = { phase, minutes ->
                    clearDaySelection()
                    onUpdateSetting(phase, minutes)
                },
                onOpenCustomTime = openCustomTimeModal,
                onResetDaily = {
                    clearDaySelection()
                    onResetDaily()
                },
                onOpenAlarmSettings = openAlarmSettings,
                onSelectSubject = onSelectSubject,
                onAddCustomSubject = onAddCustomSubject,
                onRemoveCustomSubject = onRemoveCustomSubject,
                onSetShowYearHistory = onSetShowYearHistory,
                onExportCsv = onExportCsv,
                onToggleMusic = onToggleMusic,
                onSelectAmbientSoundscape = onSelectAmbientSoundscape,
                onOpenStorageMusicPicker = openStorageMusicPicker,
                onOpenInbuiltMusicPicker = openInbuiltMusicPicker,
                onUpdateAmbientVolume = onUpdateAmbientVolume,
                onPreviewAmbientSoundscape = onPreviewAmbientSoundscape,
                onSetBlockingMode = onSetBlockingMode,
                onUpdateBlockedPackages = onUpdateBlockedPackages,
                onSetBlockDuringBreaks = onSetBlockDuringBreaks,
                onToggleAutoFocus = onToggleAutoFocus,
                onToggleAdvancedFocus = onToggleAdvancedFocus,
                onManualFocusToggle = onManualFocusToggle,
                onSeedDemoHistory = onSeedDemoHistory,
                onAddTask = onAddTask,
                onSelectActiveTask = onSelectActiveTask,
                onToggleTaskCompleted = onToggleTaskCompleted,
                onDeleteTask = onDeleteTask,
                onClearCompletedTasks = onClearCompletedTasks,
            )
            useTabletPortrait -> TabletPortraitContent(
                state = state,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                selectedDayEpoch = selectedDayEpoch,
                onSelectDay = { selectedDayEpoch = it },
                onDismissDaySelection = clearDaySelection,
                onToggle = {
                    clearDaySelection()
                    onToggle()
                },
                onReset = {
                    clearDaySelection()
                    onReset()
                },
                onFinishFlow = {
                    clearDaySelection()
                    onFinishFlow()
                },
                onSelectPhase = {
                    clearDaySelection()
                    onSelectPhase(it)
                },
                onUpdateSetting = { phase, minutes ->
                    clearDaySelection()
                    onUpdateSetting(phase, minutes)
                },
                onOpenCustomTime = openCustomTimeModal,
                onResetDaily = {
                    clearDaySelection()
                    onResetDaily()
                },
                onOpenAlarmSettings = openAlarmSettings,
                onSelectSubject = onSelectSubject,
                onAddCustomSubject = onAddCustomSubject,
                onRemoveCustomSubject = onRemoveCustomSubject,
                onSetShowYearHistory = onSetShowYearHistory,
                onExportCsv = onExportCsv,
                onToggleMusic = onToggleMusic,
                onSelectAmbientSoundscape = onSelectAmbientSoundscape,
                onOpenStorageMusicPicker = openStorageMusicPicker,
                onOpenInbuiltMusicPicker = openInbuiltMusicPicker,
                onUpdateAmbientVolume = onUpdateAmbientVolume,
                onPreviewAmbientSoundscape = onPreviewAmbientSoundscape,
                onSetBlockingMode = onSetBlockingMode,
                onUpdateBlockedPackages = onUpdateBlockedPackages,
                onSetBlockDuringBreaks = onSetBlockDuringBreaks,
                onToggleAutoFocus = onToggleAutoFocus,
                onToggleAdvancedFocus = onToggleAdvancedFocus,
                onManualFocusToggle = onManualFocusToggle,
                onSeedDemoHistory = onSeedDemoHistory,
                onAddTask = onAddTask,
                onSelectActiveTask = onSelectActiveTask,
                onToggleTaskCompleted = onToggleTaskCompleted,
                onDeleteTask = onDeleteTask,
                onClearCompletedTasks = onClearCompletedTasks,
            )
            useFoldableSquare -> FoldableSquareContent(
                state = state,
                screenWidth = screenWidth,
                screenHeight = screenHeight,
                selectedDayEpoch = selectedDayEpoch,
                onSelectDay = { selectedDayEpoch = it },
                onDismissDaySelection = clearDaySelection,
                onToggle = {
                    clearDaySelection()
                    onToggle()
                },
                onReset = {
                    clearDaySelection()
                    onReset()
                },
                onFinishFlow = {
                    clearDaySelection()
                    onFinishFlow()
                },
                onSelectPhase = {
                    clearDaySelection()
                    onSelectPhase(it)
                },
                onUpdateSetting = { phase, minutes ->
                    clearDaySelection()
                    onUpdateSetting(phase, minutes)
                },
                onOpenCustomTime = openCustomTimeModal,
                onResetDaily = {
                    clearDaySelection()
                    onResetDaily()
                },
                onOpenAlarmSettings = openAlarmSettings,
                onSelectSubject = onSelectSubject,
                onAddCustomSubject = onAddCustomSubject,
                onRemoveCustomSubject = onRemoveCustomSubject,
                onSetShowYearHistory = onSetShowYearHistory,
                onExportCsv = onExportCsv,
                onToggleMusic = onToggleMusic,
                onSelectAmbientSoundscape = onSelectAmbientSoundscape,
                onOpenStorageMusicPicker = openStorageMusicPicker,
                onOpenInbuiltMusicPicker = openInbuiltMusicPicker,
                onUpdateAmbientVolume = onUpdateAmbientVolume,
                onPreviewAmbientSoundscape = onPreviewAmbientSoundscape,
                onSetBlockingMode = onSetBlockingMode,
                onUpdateBlockedPackages = onUpdateBlockedPackages,
                onSetBlockDuringBreaks = onSetBlockDuringBreaks,
                onToggleAutoFocus = onToggleAutoFocus,
                onToggleAdvancedFocus = onToggleAdvancedFocus,
                onManualFocusToggle = onManualFocusToggle,
                onSeedDemoHistory = onSeedDemoHistory,
                onAddTask = onAddTask,
                onSelectActiveTask = onSelectActiveTask,
                onToggleTaskCompleted = onToggleTaskCompleted,
                onDeleteTask = onDeleteTask,
                onClearCompletedTasks = onClearCompletedTasks,
            )
            useTwoPane -> LandscapeContent(
                state = state,
                compact = isCompactLandscape,
                selectedDayEpoch = selectedDayEpoch,
                onSelectDay = { selectedDayEpoch = it },
                onDismissDaySelection = clearDaySelection,
                onToggle = {
                    clearDaySelection()
                    onToggle()
                },
                onReset = {
                    clearDaySelection()
                    onReset()
                },
                onFinishFlow = {
                    clearDaySelection()
                    onFinishFlow()
                },
                onSelectPhase = {
                    clearDaySelection()
                    onSelectPhase(it)
                },
                onUpdateSetting = { phase, minutes ->
                    clearDaySelection()
                    onUpdateSetting(phase, minutes)
                },
                onOpenCustomTime = openCustomTimeModal,
                onResetDaily = {
                    clearDaySelection()
                    onResetDaily()
                },
                onOpenAlarmSettings = openAlarmSettings,
                onSelectSubject = onSelectSubject,
                onAddCustomSubject = onAddCustomSubject,
                onRemoveCustomSubject = onRemoveCustomSubject,
                onSetShowYearHistory = onSetShowYearHistory,
                onExportCsv = onExportCsv,
                onToggleMusic = onToggleMusic,
                onSelectAmbientSoundscape = onSelectAmbientSoundscape,
                onOpenStorageMusicPicker = openStorageMusicPicker,
                onOpenInbuiltMusicPicker = openInbuiltMusicPicker,
                onUpdateAmbientVolume = onUpdateAmbientVolume,
                onPreviewAmbientSoundscape = onPreviewAmbientSoundscape,
                onSetBlockingMode = onSetBlockingMode,
                onUpdateBlockedPackages = onUpdateBlockedPackages,
                onSetBlockDuringBreaks = onSetBlockDuringBreaks,
                onToggleAutoFocus = onToggleAutoFocus,
                onToggleAdvancedFocus = onToggleAdvancedFocus,
                onManualFocusToggle = onManualFocusToggle,
                onSeedDemoHistory = onSeedDemoHistory,
                onAddTask = onAddTask,
                onSelectActiveTask = onSelectActiveTask,
                onToggleTaskCompleted = onToggleTaskCompleted,
                onDeleteTask = onDeleteTask,
                onClearCompletedTasks = onClearCompletedTasks,
            )
            else -> {
                val maxRingDiameter = (screenWidth - if (isCompactPortrait) 48.dp else 58.dp)
                    .coerceIn(272.dp, 340.dp)
                PortraitContent(
                    state = state,
                    compact = isCompactPortrait,
                    selectedDayEpoch = selectedDayEpoch,
                    onSelectDay = { selectedDayEpoch = it },
                    onDismissDaySelection = clearDaySelection,
                    onToggle = {
                        clearDaySelection()
                        onToggle()
                    },
                    onReset = {
                        clearDaySelection()
                        onReset()
                    },
                    onFinishFlow = {
                        clearDaySelection()
                        onFinishFlow()
                    },
                    onSelectPhase = {
                        clearDaySelection()
                        onSelectPhase(it)
                    },
                    onUpdateSetting = { phase, minutes ->
                        clearDaySelection()
                        onUpdateSetting(phase, minutes)
                    },
                    onOpenCustomTime = openCustomTimeModal,
                    onResetDaily = {
                        clearDaySelection()
                        onResetDaily()
                    },
                    onOpenAlarmSettings = openAlarmSettings,
                    onSelectSubject = onSelectSubject,
                    onAddCustomSubject = onAddCustomSubject,
                    onRemoveCustomSubject = onRemoveCustomSubject,
                    onSetShowYearHistory = onSetShowYearHistory,
                    onExportCsv = onExportCsv,
                    onToggleMusic = onToggleMusic,
                    onSelectAmbientSoundscape = onSelectAmbientSoundscape,
                    onOpenStorageMusicPicker = openStorageMusicPicker,
                    onOpenInbuiltMusicPicker = openInbuiltMusicPicker,
                    onUpdateAmbientVolume = onUpdateAmbientVolume,
                    onPreviewAmbientSoundscape = onPreviewAmbientSoundscape,
                    onSetBlockingMode = onSetBlockingMode,
                    onUpdateBlockedPackages = onUpdateBlockedPackages,
                    onSetBlockDuringBreaks = onSetBlockDuringBreaks,
                    onToggleAutoFocus = onToggleAutoFocus,
                    onToggleAdvancedFocus = onToggleAdvancedFocus,
                    onManualFocusToggle = onManualFocusToggle,
                    onSeedDemoHistory = onSeedDemoHistory,
                    onAddTask = onAddTask,
                    onSelectActiveTask = onSelectActiveTask,
                    onToggleTaskCompleted = onToggleTaskCompleted,
                    onDeleteTask = onDeleteTask,
                    onClearCompletedTasks = onClearCompletedTasks,
                    maxContentWidth = 560.dp,
                    timerFaceSize = maxRingDiameter,
                )
            }
        }

        if (state.isAdvancedFocusConfirmVisible && state.completion == null) {
            AdvancedFocusLockWarningDialog(
                phaseLabel = state.phase.heading,
                durationMinutes = state.settings.minutesFor(state.phase),
                onConfirmStart = onConfirmAdvancedFocus,
                onDismiss = onDismissAdvancedFocus,
            )
        }

        if (showCustomTimeDialog && state.completion == null) {
            CustomDurationDialog(
                phase = state.phase,
                currentMinutes = state.settings.minutesFor(state.phase),
                onConfirm = { newMinutes ->
                    onUpdateSetting(state.phase, newMinutes)
                    showCustomTimeDialog = false
                },
                onDismiss = { showCustomTimeDialog = false },
            )
        }

        if (showAlarmSettings && state.completion == null) {
            AlarmSettingsDialog(
                settings = state.alarm,
                onDismiss = {
                    onStopAlarm()
                    showAlarmSettings = false
                },
                onEnabledChange = onUpdateAlarmEnabled,
                onSoundClick = openRingtonePicker,
                onVibrationChange = onUpdateAlarmVibration,
                onDurationChange = onUpdateAlarmDuration,
                onPreview = onPreviewAlarm,
                showPrivacyOptions = showPrivacyOptions,
                onOpenPrivacyOptions = onOpenPrivacyOptions,
                onOpenPrivacyPolicy = {
                    onStopAlarm()
                    showAlarmSettings = false
                    showPrivacyPolicy = true
                },
            )
        }

        if (showPrivacyPolicy && state.completion == null) {
            PrivacyPolicyDialog(
                onDismiss = { showPrivacyPolicy = false },
                showPrivacyOptions = showPrivacyOptions,
                onOpenPrivacyOptions = {
                    showPrivacyPolicy = false
                    onOpenPrivacyOptions()
                },
            )
        }

        state.completion?.let { completion ->
            CompletionDialog(
                completion = completion,
                settings = state.settings,
                onDismiss = onDismissCompletion,
                onStartNext = onStartNext,
                onKeepFlowing = onKeepFlowing,
            )
        }

        state.permissionPrompt?.let { prompt ->
            PermissionExplanationDialog(
                prompt = prompt,
                onConfirm = onConfirmPermissionPrompt,
                onDismiss = onDismissPermissionPrompt,
            )
        }

        if (showFallbackVideoAd) {
            FullscreenVideoAdDialog(
                state = state,
                onDismissAd = onDismissFallbackVideoAd,
            )
        }
    }
}

@Composable
private fun PortraitContent(
    state: PomodoroUiState,
    compact: Boolean,
    selectedDayEpoch: Long?,
    onSelectDay: (Long?) -> Unit,
    onDismissDaySelection: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onFinishFlow: () -> Unit,
    onSelectPhase: (Phase) -> Unit,
    onUpdateSetting: (Phase, Int) -> Unit,
    onOpenCustomTime: () -> Unit,
    onResetDaily: () -> Unit,
    onOpenAlarmSettings: () -> Unit,
    onSelectSubject: (String) -> Unit,
    onAddCustomSubject: (String, Long) -> Boolean,
    onRemoveCustomSubject: (String) -> Unit,
    onSetShowYearHistory: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onToggleMusic: () -> Unit = {},
    onSelectAmbientSoundscape: (AmbientSoundscape) -> Unit,
    onOpenStorageMusicPicker: () -> Unit = {},
    onOpenInbuiltMusicPicker: () -> Unit = {},
    onUpdateAmbientVolume: (Float) -> Unit,
    onPreviewAmbientSoundscape: () -> Unit,
    onSetBlockingMode: (FocusBlockingMode) -> Unit,
    onUpdateBlockedPackages: (Set<String>) -> Unit,
    onSetBlockDuringBreaks: (Boolean) -> Unit,
    onToggleAutoFocus: (Boolean) -> Unit = {},
    onToggleAdvancedFocus: () -> Unit = {},
    onManualFocusToggle: () -> Unit = {},
    onSeedDemoHistory: () -> Unit = {},
    onAddTask: (String, Int) -> Boolean,
    onSelectActiveTask: (String?) -> Unit,
    onToggleTaskCompleted: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
    maxContentWidth: Dp,
    timerFaceSize: Dp,
) {
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val bottomContentPadding = when {
        isImeVisible -> 8.dp
        compact -> 16.dp
        else -> 20.dp
    }

    Column(
        modifier = Modifier
            .widthIn(max = maxContentWidth)
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 14.dp else 20.dp)
            .padding(top = if (compact) 12.dp else 16.dp, bottom = bottomContentPadding),
    ) {
        Header(
            onOpenAlarmSettings = onOpenAlarmSettings,
            compact = compact,
            enabled = !state.isFocusExitBlocked,
        )
        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))

        // 1. Circular Timer Ring (with refined, non-crowded inner icons on phone portrait)
        TimerCard(
            state = state,
            onToggle = onToggle,
            onReset = onReset,
            onFinishFlow = onFinishFlow,
            onSelectPhase = onSelectPhase,
            onUpdateSetting = onUpdateSetting,
            onOpenCustomTime = onOpenCustomTime,
            faceSize = timerFaceSize,
            compact = compact,
            isTablet = false,
        )
        Spacer(Modifier.height(if (compact) 12.dp else 14.dp))

        // 2. Two Sculptural Pills for Focus and Music
        FocusAndMusicPillsRow(
            state = state,
            onManualFocusToggle = onManualFocusToggle,
            onToggleAutoFocus = onToggleAutoFocus,
            onToggleAdvancedFocus = onToggleAdvancedFocus,
            onToggleMusic = onToggleMusic,
            onSelectAmbientSoundscape = onSelectAmbientSoundscape,
            onOpenStorageMusicPicker = onOpenStorageMusicPicker,
            onOpenInbuiltMusicPicker = onOpenInbuiltMusicPicker,
            onUpdateAmbientVolume = onUpdateAmbientVolume,
            onPreviewAmbientSoundscape = onPreviewAmbientSoundscape,
            onSetBlockingMode = onSetBlockingMode,
            onUpdateBlockedPackages = onUpdateBlockedPackages,
            onSetBlockDuringBreaks = onSetBlockDuringBreaks,
            compact = compact,
        )
        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))

        // 3. Heatmap of Sessions, Session Counts & Study Subjects
        RhythmCard(
            state = state,
            selectedDayEpoch = selectedDayEpoch,
            onSelectDay = onSelectDay,
            onDismissDaySelection = onDismissDaySelection,
            onResetDaily = onResetDaily,
            onSelectSubject = onSelectSubject,
            onAddCustomSubject = onAddCustomSubject,
            onRemoveCustomSubject = onRemoveCustomSubject,
            onSetShowYearHistory = onSetShowYearHistory,
            onExportCsv = onExportCsv,
            onSeedDemoHistory = onSeedDemoHistory,
            compact = compact,
        )
        Spacer(Modifier.height(if (compact) 12.dp else 16.dp))

        // 4. Today's Task List (refreshes at 12 AM, separates Incomplete & Completed)
        StudyTasksAndFocusStudioCard(
            state = state,
            compact = compact,
            onAddTask = onAddTask,
            onSelectActiveTask = onSelectActiveTask,
            onToggleTaskCompleted = onToggleTaskCompleted,
            onDeleteTask = onDeleteTask,
            onClearCompletedTasks = onClearCompletedTasks,
        )
    }
}

/**
 * Compact & Phone Landscape (16:9 / 20:9):
 * Uses `BoxWithConstraints` on the left half of the screen so the `TimerCard` is sized from
 * the exact post-inset height (`maxHeight - 36.dp`) and width (`maxWidth - 16.dp`).
 * This guarantees the entire TimerCard (ring + controls + status line) is 100% contained
 * vertically inside the left half-screen and never scrolls or clips.
 */
@Composable
private fun LandscapeContent(
    state: PomodoroUiState,
    compact: Boolean,
    selectedDayEpoch: Long?,
    onSelectDay: (Long?) -> Unit,
    onDismissDaySelection: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onFinishFlow: () -> Unit,
    onSelectPhase: (Phase) -> Unit,
    onUpdateSetting: (Phase, Int) -> Unit,
    onOpenCustomTime: () -> Unit,
    onResetDaily: () -> Unit,
    onOpenAlarmSettings: () -> Unit,
    onSelectSubject: (String) -> Unit,
    onAddCustomSubject: (String, Long) -> Boolean,
    onRemoveCustomSubject: (String) -> Unit,
    onSetShowYearHistory: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onToggleMusic: () -> Unit = {},
    onSelectAmbientSoundscape: (AmbientSoundscape) -> Unit,
    onOpenStorageMusicPicker: () -> Unit = {},
    onOpenInbuiltMusicPicker: () -> Unit = {},
    onUpdateAmbientVolume: (Float) -> Unit,
    onPreviewAmbientSoundscape: () -> Unit,
    onSetBlockingMode: (FocusBlockingMode) -> Unit,
    onUpdateBlockedPackages: (Set<String>) -> Unit,
    onSetBlockDuringBreaks: (Boolean) -> Unit,
    onToggleAutoFocus: (Boolean) -> Unit = {},
    onToggleAdvancedFocus: () -> Unit = {},
    onManualFocusToggle: () -> Unit = {},
    onSeedDemoHistory: () -> Unit = {},
    onAddTask: (String, Int) -> Boolean,
    onSelectActiveTask: (String?) -> Unit,
    onToggleTaskCompleted: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
) {
    Row(
        modifier = Modifier
            .widthIn(max = 1280.dp)
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 6.dp else 8.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Pane: Strictly contained vertically in the available half-screen (no scrolling)
        BoxWithConstraints(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            val fittedFaceSize = minOf(
                maxWidth - 16.dp,
                maxHeight - 36.dp,
            ).coerceIn(188.dp, 284.dp)

            TimerCard(
                state = state,
                onToggle = onToggle,
                onReset = onReset,
                onFinishFlow = onFinishFlow,
                onSelectPhase = onSelectPhase,
                onUpdateSetting = onUpdateSetting,
                onOpenCustomTime = onOpenCustomTime,
                compact = true,
                isTablet = false,
                faceSize = fittedFaceSize,
            )
        }

        // Right Pane: Scrollable column with Header, Focus/Music pills, Ledger, Tasks, and Ad
        Column(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
        ) {
            Header(
                onOpenAlarmSettings = onOpenAlarmSettings,
                compact = true,
                enabled = !state.isFocusExitBlocked,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
            FocusAndMusicPillsRow(
                state = state,
                onManualFocusToggle = onManualFocusToggle,
                onToggleAutoFocus = onToggleAutoFocus,
                onToggleAdvancedFocus = onToggleAdvancedFocus,
                onToggleMusic = onToggleMusic,
                onSelectAmbientSoundscape = onSelectAmbientSoundscape,
                onOpenStorageMusicPicker = onOpenStorageMusicPicker,
                onOpenInbuiltMusicPicker = onOpenInbuiltMusicPicker,
                onUpdateAmbientVolume = onUpdateAmbientVolume,
                onPreviewAmbientSoundscape = onPreviewAmbientSoundscape,
                onSetBlockingMode = onSetBlockingMode,
                onUpdateBlockedPackages = onUpdateBlockedPackages,
                onSetBlockDuringBreaks = onSetBlockDuringBreaks,
                compact = true,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
            RhythmCard(
                state = state,
                selectedDayEpoch = selectedDayEpoch,
                onSelectDay = onSelectDay,
                onDismissDaySelection = onDismissDaySelection,
                compact = true,
                onResetDaily = onResetDaily,
                onSelectSubject = onSelectSubject,
                onAddCustomSubject = onAddCustomSubject,
                onRemoveCustomSubject = onRemoveCustomSubject,
                onSetShowYearHistory = onSetShowYearHistory,
                onExportCsv = onExportCsv,
                onSeedDemoHistory = onSeedDemoHistory,
            )
            Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
            StudyTasksAndFocusStudioCard(
                state = state,
                compact = true,
                onAddTask = onAddTask,
                onSelectActiveTask = onSelectActiveTask,
                onToggleTaskCompleted = onToggleTaskCompleted,
                onDeleteTask = onDeleteTask,
                onClearCompletedTasks = onClearCompletedTasks,
            )
        }
    }
}

/**
 * Tablet Portrait (3:4 / 10:16 Expanded Portrait):
 * Upper ~60% hero circular Timer Card + expanded Focus/Music pills;
 * Lower ~40% two-column grid with Daily Rhythm & Ledger on the left and Today's Tasks + Ad on the right.
 */
@Composable
private fun TabletPortraitContent(
    state: PomodoroUiState,
    screenWidth: Dp,
    screenHeight: Dp,
    selectedDayEpoch: Long?,
    onSelectDay: (Long?) -> Unit,
    onDismissDaySelection: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onFinishFlow: () -> Unit,
    onSelectPhase: (Phase) -> Unit,
    onUpdateSetting: (Phase, Int) -> Unit,
    onOpenCustomTime: () -> Unit,
    onResetDaily: () -> Unit,
    onOpenAlarmSettings: () -> Unit,
    onSelectSubject: (String) -> Unit,
    onAddCustomSubject: (String, Long) -> Boolean,
    onRemoveCustomSubject: (String) -> Unit,
    onSetShowYearHistory: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onToggleMusic: () -> Unit = {},
    onSelectAmbientSoundscape: (AmbientSoundscape) -> Unit,
    onOpenStorageMusicPicker: () -> Unit = {},
    onOpenInbuiltMusicPicker: () -> Unit = {},
    onUpdateAmbientVolume: (Float) -> Unit,
    onPreviewAmbientSoundscape: () -> Unit,
    onSetBlockingMode: (FocusBlockingMode) -> Unit,
    onUpdateBlockedPackages: (Set<String>) -> Unit,
    onSetBlockDuringBreaks: (Boolean) -> Unit,
    onToggleAutoFocus: (Boolean) -> Unit = {},
    onToggleAdvancedFocus: () -> Unit = {},
    onManualFocusToggle: () -> Unit = {},
    onSeedDemoHistory: () -> Unit = {},
    onAddTask: (String, Int) -> Boolean,
    onSelectActiveTask: (String?) -> Unit,
    onToggleTaskCompleted: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
) {
    val timerFaceSize = minOf(
        screenWidth - 68.dp,
        (screenHeight * 0.45f).coerceAtLeast(388.dp),
    ).coerceIn(380.dp, 500.dp)

    Column(
        modifier = Modifier
            .widthIn(max = 980.dp)
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp, vertical = 20.dp),
    ) {
        Header(
            onOpenAlarmSettings = onOpenAlarmSettings,
            compact = false,
            enabled = !state.isFocusExitBlocked,
        )
        Spacer(Modifier.height(16.dp))

        TimerCard(
            state = state,
            onToggle = onToggle,
            onReset = onReset,
            onFinishFlow = onFinishFlow,
            onSelectPhase = onSelectPhase,
            onUpdateSetting = onUpdateSetting,
            onOpenCustomTime = onOpenCustomTime,
            faceSize = timerFaceSize,
            compact = false,
            isTablet = true,
        )
        Spacer(Modifier.height(14.dp))

        FocusAndMusicPillsRow(
            state = state,
            onManualFocusToggle = onManualFocusToggle,
            onToggleAutoFocus = onToggleAutoFocus,
            onToggleAdvancedFocus = onToggleAdvancedFocus,
            onToggleMusic = onToggleMusic,
            onSelectAmbientSoundscape = onSelectAmbientSoundscape,
            onOpenStorageMusicPicker = onOpenStorageMusicPicker,
            onOpenInbuiltMusicPicker = onOpenInbuiltMusicPicker,
            onUpdateAmbientVolume = onUpdateAmbientVolume,
            onPreviewAmbientSoundscape = onPreviewAmbientSoundscape,
            onSetBlockingMode = onSetBlockingMode,
            onUpdateBlockedPackages = onUpdateBlockedPackages,
            onSetBlockDuringBreaks = onSetBlockDuringBreaks,
            expanded = true,
        )
        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            RhythmCard(
                state = state,
                selectedDayEpoch = selectedDayEpoch,
                onSelectDay = onSelectDay,
                onDismissDaySelection = onDismissDaySelection,
                compact = true,
                onResetDaily = onResetDaily,
                onSelectSubject = onSelectSubject,
                onAddCustomSubject = onAddCustomSubject,
                onRemoveCustomSubject = onRemoveCustomSubject,
                onSetShowYearHistory = onSetShowYearHistory,
                onExportCsv = onExportCsv,
                onSeedDemoHistory = onSeedDemoHistory,
                modifier = Modifier.weight(1.04f),
            )
            Column(
                modifier = Modifier.weight(0.96f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StudyTasksAndFocusStudioCard(
                    state = state,
                    compact = true,
                    onAddTask = onAddTask,
                    onSelectActiveTask = onSelectActiveTask,
                    onToggleTaskCompleted = onToggleTaskCompleted,
                    onDeleteTask = onDeleteTask,
                    onClearCompletedTasks = onClearCompletedTasks,
                )
            }
        }
    }
}

/**
 * Unfolded Foldable / Square Display (1:1 Adaptive Two-Pane):
 * Asymmetric two-pane split giving the left pane to the Header, TimerCard, and Focus/Music pills,
 * and the right pane to Daily Rhythm & Ledger, Today's Tasks, and Native Ad.
 */
@Composable
private fun FoldableSquareContent(
    state: PomodoroUiState,
    screenWidth: Dp,
    screenHeight: Dp,
    selectedDayEpoch: Long?,
    onSelectDay: (Long?) -> Unit,
    onDismissDaySelection: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onFinishFlow: () -> Unit,
    onSelectPhase: (Phase) -> Unit,
    onUpdateSetting: (Phase, Int) -> Unit,
    onOpenCustomTime: () -> Unit,
    onResetDaily: () -> Unit,
    onOpenAlarmSettings: () -> Unit,
    onSelectSubject: (String) -> Unit,
    onAddCustomSubject: (String, Long) -> Boolean,
    onRemoveCustomSubject: (String) -> Unit,
    onSetShowYearHistory: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onToggleMusic: () -> Unit = {},
    onSelectAmbientSoundscape: (AmbientSoundscape) -> Unit,
    onOpenStorageMusicPicker: () -> Unit = {},
    onOpenInbuiltMusicPicker: () -> Unit = {},
    onUpdateAmbientVolume: (Float) -> Unit,
    onPreviewAmbientSoundscape: () -> Unit,
    onSetBlockingMode: (FocusBlockingMode) -> Unit,
    onUpdateBlockedPackages: (Set<String>) -> Unit,
    onSetBlockDuringBreaks: (Boolean) -> Unit,
    onToggleAutoFocus: (Boolean) -> Unit = {},
    onToggleAdvancedFocus: () -> Unit = {},
    onManualFocusToggle: () -> Unit = {},
    onSeedDemoHistory: () -> Unit = {},
    onAddTask: (String, Int) -> Boolean,
    onSelectActiveTask: (String?) -> Unit,
    onToggleTaskCompleted: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
) {
    val leftPaneWidth = (screenWidth - 54.dp) * 0.55f
    // On 540-560 dp near-square foldables the left pane can be narrower than the 308 dp
    // floor, which pushed the ring past the pane edge. Never demand more than the pane has.
    val availablePane = (leftPaneWidth - 20.dp).coerceAtLeast(1.dp)
    val timerFaceSize = minOf(
        availablePane,
        (screenHeight - 196.dp).coerceAtLeast(312.dp),
    ).coerceIn(minOf(308.dp, availablePane), 424.dp)

    Row(
        modifier = Modifier
            .widthIn(max = 1120.dp)
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1.18f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Header(
                onOpenAlarmSettings = onOpenAlarmSettings,
                compact = false,
                enabled = !state.isFocusExitBlocked,
            )
            TimerCard(
                state = state,
                onToggle = onToggle,
                onReset = onReset,
                onFinishFlow = onFinishFlow,
                onSelectPhase = onSelectPhase,
                onUpdateSetting = onUpdateSetting,
                onOpenCustomTime = onOpenCustomTime,
                faceSize = timerFaceSize,
                isTablet = true,
            )
            FocusAndMusicPillsRow(
                state = state,
                onManualFocusToggle = onManualFocusToggle,
                onToggleAutoFocus = onToggleAutoFocus,
                onToggleAdvancedFocus = onToggleAdvancedFocus,
                onToggleMusic = onToggleMusic,
                onSelectAmbientSoundscape = onSelectAmbientSoundscape,
                onOpenStorageMusicPicker = onOpenStorageMusicPicker,
                onOpenInbuiltMusicPicker = onOpenInbuiltMusicPicker,
                onUpdateAmbientVolume = onUpdateAmbientVolume,
                onPreviewAmbientSoundscape = onPreviewAmbientSoundscape,
                onSetBlockingMode = onSetBlockingMode,
                onUpdateBlockedPackages = onUpdateBlockedPackages,
                onSetBlockDuringBreaks = onSetBlockDuringBreaks,
            )
        }

        Column(
            modifier = Modifier
                .weight(0.88f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RhythmCard(
                state = state,
                selectedDayEpoch = selectedDayEpoch,
                onSelectDay = onSelectDay,
                onDismissDaySelection = onDismissDaySelection,
                compact = true,
                onResetDaily = onResetDaily,
                onSelectSubject = onSelectSubject,
                onAddCustomSubject = onAddCustomSubject,
                onRemoveCustomSubject = onRemoveCustomSubject,
                onSetShowYearHistory = onSetShowYearHistory,
                onExportCsv = onExportCsv,
                onSeedDemoHistory = onSeedDemoHistory,
            )
            StudyTasksAndFocusStudioCard(
                state = state,
                compact = true,
                onAddTask = onAddTask,
                onSelectActiveTask = onSelectActiveTask,
                onToggleTaskCompleted = onToggleTaskCompleted,
                onDeleteTask = onDeleteTask,
                onClearCompletedTasks = onClearCompletedTasks,
            )
        }
    }
}

/**
 * Tablet Landscape (4:3 / 16:10 Expanded Landscape):
 * Two-column asymmetric split (57% left column for Header, Hero TimerCard, and Focus/Music pills;
 * 43% right column for Daily Rhythm & Ledger, Today's Tasks, and Sponsored Ad).
 */
@Composable
private fun TabletLandscapeContent(
    state: PomodoroUiState,
    screenWidth: Dp,
    screenHeight: Dp,
    selectedDayEpoch: Long?,
    onSelectDay: (Long?) -> Unit,
    onDismissDaySelection: () -> Unit,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onFinishFlow: () -> Unit,
    onSelectPhase: (Phase) -> Unit,
    onUpdateSetting: (Phase, Int) -> Unit,
    onOpenCustomTime: () -> Unit,
    onResetDaily: () -> Unit,
    onOpenAlarmSettings: () -> Unit,
    onSelectSubject: (String) -> Unit,
    onAddCustomSubject: (String, Long) -> Boolean,
    onRemoveCustomSubject: (String) -> Unit,
    onSetShowYearHistory: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    onToggleMusic: () -> Unit = {},
    onSelectAmbientSoundscape: (AmbientSoundscape) -> Unit,
    onOpenStorageMusicPicker: () -> Unit = {},
    onOpenInbuiltMusicPicker: () -> Unit = {},
    onUpdateAmbientVolume: (Float) -> Unit,
    onPreviewAmbientSoundscape: () -> Unit,
    onSetBlockingMode: (FocusBlockingMode) -> Unit,
    onUpdateBlockedPackages: (Set<String>) -> Unit,
    onSetBlockDuringBreaks: (Boolean) -> Unit,
    onToggleAutoFocus: (Boolean) -> Unit = {},
    onToggleAdvancedFocus: () -> Unit = {},
    onManualFocusToggle: () -> Unit = {},
    onSeedDemoHistory: () -> Unit = {},
    onAddTask: (String, Int) -> Boolean,
    onSelectActiveTask: (String?) -> Unit,
    onToggleTaskCompleted: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
) {
    val leftPaneWidth = (screenWidth - 76.dp) * 0.57f
    val timerFaceSize = minOf(
        leftPaneWidth - 24.dp,
        (screenHeight - 268.dp).coerceAtLeast(332.dp),
    ).coerceIn(332.dp, 428.dp)

    Row(
        modifier = Modifier
            .widthIn(max = 1480.dp)
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 26.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1.32f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(
                onOpenAlarmSettings = onOpenAlarmSettings,
                compact = false,
                enabled = !state.isFocusExitBlocked,
            )
            TimerCard(
                state = state,
                onToggle = onToggle,
                onReset = onReset,
                onFinishFlow = onFinishFlow,
                onSelectPhase = onSelectPhase,
                onUpdateSetting = onUpdateSetting,
                onOpenCustomTime = onOpenCustomTime,
                faceSize = timerFaceSize,
                isTablet = true,
            )
            FocusAndMusicPillsRow(
                state = state,
                onManualFocusToggle = onManualFocusToggle,
                onToggleAutoFocus = onToggleAutoFocus,
                onToggleAdvancedFocus = onToggleAdvancedFocus,
                onToggleMusic = onToggleMusic,
                onSelectAmbientSoundscape = onSelectAmbientSoundscape,
                onOpenStorageMusicPicker = onOpenStorageMusicPicker,
                onOpenInbuiltMusicPicker = onOpenInbuiltMusicPicker,
                onUpdateAmbientVolume = onUpdateAmbientVolume,
                onPreviewAmbientSoundscape = onPreviewAmbientSoundscape,
                onSetBlockingMode = onSetBlockingMode,
                onUpdateBlockedPackages = onUpdateBlockedPackages,
                onSetBlockDuringBreaks = onSetBlockDuringBreaks,
                expanded = true,
            )
        }

        Column(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RhythmCard(
                state = state,
                selectedDayEpoch = selectedDayEpoch,
                onSelectDay = onSelectDay,
                onDismissDaySelection = onDismissDaySelection,
                compact = true,
                onResetDaily = onResetDaily,
                onSelectSubject = onSelectSubject,
                onAddCustomSubject = onAddCustomSubject,
                onRemoveCustomSubject = onRemoveCustomSubject,
                onSetShowYearHistory = onSetShowYearHistory,
                onExportCsv = onExportCsv,
                onSeedDemoHistory = onSeedDemoHistory,
            )
            StudyTasksAndFocusStudioCard(
                state = state,
                compact = true,
                onAddTask = onAddTask,
                onSelectActiveTask = onSelectActiveTask,
                onToggleTaskCompleted = onToggleTaskCompleted,
                onDeleteTask = onDeleteTask,
                onClearCompletedTasks = onClearCompletedTasks,
            )
        }
    }
}

@Composable
private fun Header(
    onOpenAlarmSettings: () -> Unit,
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pomodoro",
                    color = Ink,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = if (compact) 34.sp else 46.sp,
                        lineHeight = if (compact) 36.sp else 48.sp,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "A softer way to protect your focus.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = if (compact) 12.sp else 13.sp,
                    ),
                )
            }
            Spacer(Modifier.width(8.dp))
            SoftIconButton(
                icon = Icons.Outlined.Alarm,
                contentDescription = "Alarm settings",
                onClick = onOpenAlarmSettings,
                enabled = enabled,
                size = if (compact) 40.dp else 44.dp,
                iconSize = if (compact) 17.dp else 19.dp,
            )
        }
        Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(1.dp)
                .background(Stone),
        )
    }
}

/**
 * CALM / SYSTEM Circular Timer Card:
 * - On tablets (`isTablet = true`), uses the expanded `largeRing` / `heroRing` proportions.
 * - On phones and compact screens (`isTablet = false`), uses refined, smaller icons and buttons
 *   inside the ring so they never crowd the circle or cover excessive space, and uses compact
 *   vertical padding in landscape so the card fits 100% inside the half-screen height.
 */
@Composable
private fun TimerCard(
    state: PomodoroUiState,
    onToggle: () -> Unit,
    onReset: () -> Unit,
    onFinishFlow: () -> Unit,
    onSelectPhase: (Phase) -> Unit,
    onUpdateSetting: (Phase, Int) -> Unit,
    onOpenCustomTime: () -> Unit,
    compact: Boolean = false,
    isTablet: Boolean = false,
    faceSize: Dp = 328.dp,
) {
    val currentMinutes = state.settings.minutesFor(state.phase)
    val bounds = SettingBounds.getValue(state.phase)
    val stepDownTarget = when {
        currentMinutes <= 5 -> (currentMinutes - 1).coerceAtLeast(bounds.min)
        currentMinutes % 5 != 0 -> (currentMinutes / 5) * 5
        else -> (currentMinutes - bounds.step).coerceAtLeast(bounds.min)
    }
    val stepUpTarget = when {
        currentMinutes < 5 -> 5
        currentMinutes % 5 != 0 -> (((currentMinutes / 5) + 1) * 5).coerceAtMost(bounds.max)
        else -> (currentMinutes + bounds.step).coerceAtMost(bounds.max)
    }
    val compactLandscapeRing = !isTablet && faceSize < 268.dp
    val tightRing = !isTablet && faceSize < 295.dp
    val largeRing = isTablet && faceSize >= 340.dp
    val heroRing = isTablet && faceSize >= 400.dp

    val ringStrokeWidth = when {
        heroRing -> 11.dp
        largeRing -> 10.dp
        compactLandscapeRing -> 7.dp
        tightRing -> 8.dp
        else -> 8.5.dp
    }
    val stepButtonSize = when {
        heroRing -> 44.dp
        largeRing -> 38.dp
        compactLandscapeRing -> 24.dp
        tightRing -> 26.dp
        else -> 28.dp
    }
    val stepIconSize = when {
        heroRing -> 18.dp
        largeRing -> 16.dp
        compactLandscapeRing -> 10.dp
        tightRing -> 11.dp
        else -> 12.dp
    }
    // The clock lives inside a fixed-size ring. Counteract the system font scale entirely
    // (and keep it on one line) so "25:00" cannot wrap or push the ± buttons out of the
    // ring; it is already far larger than body text at every scale.
    val clockFontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val sectionGap = when {
        heroRing -> 16.dp
        largeRing -> 14.dp
        compactLandscapeRing -> 6.dp
        tightRing -> 9.dp
        else -> 12.dp
    }
    val innerChordFactor = if (isTablet) 0.82f else 0.76f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .softRaised(Paper, if (compact) 26.dp else 30.dp, if (compact) 7.dp else 9.dp)
            .padding(
                horizontal = if (compact) 8.dp else 14.dp,
                vertical = when {
                    heroRing -> 22.dp
                    compact -> 8.dp
                    else -> 16.dp
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            ProgressRing(
                progress = state.progress,
                size = faceSize,
                strokeWidth = ringStrokeWidth,
                trackColor = StoneLight,
                progressColor = Forest,
                endDotColor = Mint,
                themeMode = state.themeMode,
                isLocked = state.isAdvancedFocusLocked,
            ) {
                Column(
                    modifier = Modifier
                        .width(faceSize * innerChordFactor)
                        .padding(vertical = if (compactLandscapeRing) 2.dp else 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // 1. Segmented Phase Tabs comfortably inside the upper hemisphere
                    RingPhaseTabs(
                        activePhase = state.phase,
                        enabled = state.canSelectPhaseTabs,
                        compactLandscape = compactLandscapeRing,
                        compact = tightRing,
                        large = largeRing,
                        hero = heroRing,
                        onSelect = onSelectPhase,
                    )

                    Spacer(Modifier.height(sectionGap))

                    // 2. Center Clock with Left/Right +/- Duration Controls & Double-Tap Custom Time
                    Text(
                        text = if (state.isFlowOvertime) {
                            "FLOW OVERTIME"
                        } else {
                            state.phase.heading.uppercase()
                        },
                        color = if (state.isFlowOvertime) Forest else Muted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = when {
                                heroRing -> 11.sp
                                largeRing -> 10.sp
                                compactLandscapeRing -> 7.5.sp
                                tightRing -> 8.sp
                                else -> 9.sp
                            },
                            letterSpacing = 1.8.sp,
                        ),
                    )
                    Spacer(Modifier.height(if (compactLandscapeRing) 1.dp else 2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        SoftIconButton(
                            icon = Icons.Outlined.Remove,
                            contentDescription = "Decrease ${state.phase.heading} minutes",
                            onClick = { onUpdateSetting(state.phase, stepDownTarget) },
                            enabled = state.canAdjustDuration && currentMinutes > bounds.min,
                            size = stepButtonSize,
                            iconSize = stepIconSize,
                        )
                        Spacer(
                            Modifier.width(
                                when {
                                    heroRing -> 14.dp
                                    largeRing -> 12.dp
                                    compactLandscapeRing -> 5.dp
                                    tightRing -> 6.dp
                                    else -> 8.dp
                                },
                            ),
                        )

                        val clockFontSize = when {
                            heroRing -> 74.sp
                            largeRing -> 64.sp
                            compactLandscapeRing -> 36.sp
                            faceSize < 280.dp -> 42.sp
                            faceSize < 310.dp -> 48.sp
                            else -> 54.sp
                        }
                        val clockLineHeight = when {
                            heroRing -> 76.sp
                            largeRing -> 66.sp
                            compactLandscapeRing -> 38.sp
                            faceSize < 280.dp -> 44.sp
                            faceSize < 310.dp -> 50.sp
                            else -> 56.sp
                        }
                        Text(
                            text = state.displayClock,
                            color = Ink,
                            modifier = Modifier
                                .pointerInput(state.phase, state.canAdjustDuration) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (state.canAdjustDuration) {
                                                onOpenCustomTime()
                                            }
                                        },
                                    )
                                }
                                .clearAndSetSemantics {
                                    contentDescription = if (state.isFlowOvertime) {
                                        "${state.displayClock} flow overtime"
                                    } else {
                                        "${state.displayClock} remaining"
                                    }
                                },
                            maxLines = 1,
                            softWrap = false,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = (clockFontSize.value / clockFontScale).sp,
                                lineHeight = (clockLineHeight.value / clockFontScale).sp,
                                fontWeight = FontWeight.Light,
                                fontFeatureSettings = "lnum, tnum",
                            ),
                        )

                        Spacer(
                            Modifier.width(
                                when {
                                    heroRing -> 14.dp
                                    largeRing -> 12.dp
                                    compactLandscapeRing -> 5.dp
                                    tightRing -> 6.dp
                                    else -> 8.dp
                                },
                            ),
                        )
                        SoftIconButton(
                            icon = Icons.Outlined.Add,
                            contentDescription = "Increase ${state.phase.heading} minutes",
                            onClick = { onUpdateSetting(state.phase, stepUpTarget) },
                            enabled = state.canAdjustDuration && currentMinutes < bounds.max,
                            size = stepButtonSize,
                            iconSize = stepIconSize,
                        )
                    }

                    Text(
                        text = state.sessionLabel,
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = when {
                                heroRing -> 12.sp
                                largeRing -> 11.sp
                                compactLandscapeRing -> 8.5.sp
                                tightRing -> 9.sp
                                else -> 10.sp
                            },
                        ),
                    )
                    Spacer(Modifier.height(if (compactLandscapeRing) 2.dp else 4.dp))
                    SessionDots(state)

                    Spacer(Modifier.height(sectionGap))

                    // 3. Start/Pause + Reset inside the lower hemisphere
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (heroRing) 10.dp else 7.dp),
                    ) {
                        RingPrimaryButton(
                            label = state.actionLabel,
                            isRunning = state.isRunning,
                            enabled = state.canPressPrimaryTimerButton,
                            compactLandscape = compactLandscapeRing,
                            compact = tightRing,
                            large = largeRing,
                            hero = heroRing,
                            onClick = onToggle,
                        )
                        RingResetButton(
                            enabled = state.canReset,
                            compactLandscape = compactLandscapeRing,
                            compact = tightRing,
                            large = largeRing,
                            hero = heroRing,
                            onClick = onReset,
                        )
                    }
                    if (state.isFlowOvertime) {
                        Spacer(Modifier.height(if (compactLandscapeRing) 3.dp else 5.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MintLight, RoundedCornerShape(999.dp))
                                .border(1.dp, Forest.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                                .clickable(role = Role.Button, onClick = onFinishFlow)
                                .padding(horizontal = 10.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = state.finishFlowLabel,
                                color = Forest,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (largeRing) 9.sp else 8.sp,
                                    letterSpacing = 0.7.sp,
                                ),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        StatusRow(state = state, compact = compact)
    }
}

@Composable
private fun RingPhaseTabs(
    activePhase: Phase,
    enabled: Boolean,
    compactLandscape: Boolean = false,
    compact: Boolean = false,
    large: Boolean = false,
    hero: Boolean = false,
    onSelect: (Phase) -> Unit,
) {
    val phases = listOf(
        Phase.Focus to "Focus",
        Phase.ShortBreak to "Short",
        Phase.LongBreak to "Long",
    )
    val horizontalPad = when {
        hero -> 18.dp
        large -> 15.dp
        compactLandscape -> 8.dp
        compact -> 10.dp
        else -> 11.dp
    }
    val verticalPad = when {
        hero -> 7.dp
        large -> 6.dp
        compactLandscape -> 3.dp
        compact -> 4.dp
        else -> 4.5.dp
    }
    val labelSize = when {
        hero -> 14.sp
        large -> 13.sp
        compactLandscape -> 9.sp
        compact -> 10.sp
        else -> 10.5.sp
    }
    Row(
        modifier = Modifier
            .softRaised(Parchment, 999.dp, 2.dp)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        phases.forEach { (phase, label) ->
            val active = phase == activePhase
            val shape = RoundedCornerShape(999.dp)
            val haptic = LocalHapticFeedback.current
            Box(
                modifier = Modifier
                    .alpha(if (enabled) 1f else 0.55f)
                    .clip(shape)
                    .background(if (active) Forest else Color.Transparent, shape)
                    .clickable(
                        enabled = enabled,
                        role = Role.Tab,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(phase)
                    }
                    .semantics { selected = active }
                    .padding(
                        horizontal = horizontalPad,
                        vertical = verticalPad,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (active) Ivory else Ink,
                    maxLines = 1,
                    softWrap = false,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = labelSize,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RingPrimaryButton(
    label: String,
    isRunning: Boolean,
    enabled: Boolean,
    compactLandscape: Boolean = false,
    compact: Boolean = false,
    large: Boolean = false,
    hero: Boolean = false,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(999.dp)
    val buttonHeight = when {
        hero -> 50.dp
        large -> 46.dp
        compactLandscape -> 30.dp
        compact -> 34.dp
        else -> 36.dp
    }
    val horizontalPad = when {
        hero -> 22.dp
        large -> 18.dp
        compactLandscape -> 10.dp
        compact -> 12.dp
        else -> 14.dp
    }
    val fontSize = when {
        hero -> 18.sp
        large -> 16.sp
        compactLandscape -> 12.sp
        compact -> 13.sp
        else -> 14.sp
    }
    val iconSize = when {
        hero -> 16.dp
        large -> 15.dp
        compactLandscape -> 11.dp
        else -> 12.dp
    }
    Row(
        modifier = Modifier
            .height(buttonHeight)
            .then(
                if (enabled) {
                    Modifier.softRaised(Forest, 999.dp, 4.dp, highlight = false)
                } else {
                    Modifier.background(StoneLight, shape)
                },
            )
            .clip(shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = horizontalPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (isRunning) {
            Icon(
                imageVector = Icons.Rounded.Pause,
                contentDescription = null,
                tint = if (enabled) Ivory else MutedLight,
                modifier = Modifier.size(iconSize),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            color = if (enabled) Ivory else MutedLight,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = fontSize,
            ),
        )
        if (!isRunning) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = if (enabled) Ivory else MutedLight,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun RingResetButton(
    enabled: Boolean,
    compactLandscape: Boolean = false,
    compact: Boolean = false,
    large: Boolean = false,
    hero: Boolean = false,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(999.dp)
    val contentColor = if (enabled) Ink else MutedLight
    val buttonHeight = when {
        hero -> 50.dp
        large -> 46.dp
        compactLandscape -> 30.dp
        compact -> 34.dp
        else -> 36.dp
    }
    val horizontalPad = when {
        hero -> 16.dp
        large -> 14.dp
        compactLandscape -> 8.dp
        compact -> 9.dp
        else -> 10.dp
    }
    val fontSize = when {
        hero -> 13.sp
        large -> 12.sp
        compactLandscape -> 9.sp
        compact -> 10.sp
        else -> 10.5.sp
    }
    val iconSize = when {
        hero -> 14.dp
        large -> 13.dp
        compactLandscape -> 10.dp
        else -> 11.dp
    }
    Row(
        modifier = Modifier
            .height(buttonHeight)
            .then(
                if (enabled) {
                    Modifier.softRaised(Paper, 999.dp, 3.dp)
                } else {
                    Modifier.background(StoneLight.copy(alpha = 0.7f), shape)
                },
            )
            .clip(shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = "Reset timer" }
            .padding(horizontal = horizontalPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Reset",
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = fontSize,
            ),
        )
    }
}

/**
 * Custom Time Input Modal opened by double-tapping the timer clock display.
 */
@Composable
private fun CustomDurationDialog(
    phase: Phase,
    currentMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val bounds = SettingBounds.getValue(phase)
    var inputText by rememberSaveable(phase, currentMinutes) {
        mutableStateOf(currentMinutes.toString())
    }
    val parsed = inputText.trim().toIntOrNull()
    val valid = parsed != null && parsed in bounds.min..bounds.max

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .softRaised(Paper, 28.dp, 16.dp)
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "CUSTOM DURATION",
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.5.sp,
                            ),
                        )
                        Text(
                            text = "${phase.heading} (${bounds.min}–${bounds.max} min)",
                            color = Ink,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 24.sp,
                                lineHeight = 28.sp,
                            ),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close custom duration",
                        tint = Muted,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(role = Role.Button, onClick = onDismiss),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SoftIconButton(
                        icon = Icons.Outlined.Remove,
                        contentDescription = "Minus 1 minute",
                        onClick = {
                            val cur = parsed ?: currentMinutes
                            inputText = (cur - 1).coerceIn(bounds.min, bounds.max).toString()
                        },
                        enabled = (parsed ?: currentMinutes) > bounds.min,
                        size = 42.dp,
                        iconSize = 16.dp,
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .softRaised(Ivory, 999.dp, 2.dp)
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = { next ->
                                inputText = next.filter { it.isDigit() }.take(3)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.headlineLarge.copy(
                                color = Ink,
                                fontSize = 28.sp,
                                textAlign = TextAlign.Center,
                            ),
                            cursorBrush = SolidColor(Forest),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SoftIconButton(
                        icon = Icons.Outlined.Add,
                        contentDescription = "Plus 1 minute",
                        onClick = {
                            val cur = parsed ?: currentMinutes
                            inputText = (cur + 1).coerceIn(bounds.min, bounds.max).toString()
                        },
                        enabled = (parsed ?: currentMinutes) < bounds.max,
                        size = 42.dp,
                        iconSize = 16.dp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val presets = when (phase) {
                        Phase.Focus -> listOf(15, 25, 45, 60, 90)
                        Phase.ShortBreak -> listOf(5, 10, 15, 20)
                        Phase.LongBreak -> listOf(15, 20, 30, 45)
                    }
                    presets.forEach { preset ->
                        val selected = parsed == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) Forest else Parchment,
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable { inputText = preset.toString() }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${preset}m",
                                color = if (selected) Ivory else Ink,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SoftButton(
                        label = "Back",
                        onClick = onDismiss,
                        variant = SoftButtonVariant.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    SoftButton(
                        label = "Continue",
                        onClick = { if (valid && parsed != null) onConfirm(parsed) },
                        trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                        enabled = valid,
                        variant = SoftButtonVariant.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionDots(state: PomodoroUiState) {
    Row(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "${state.filledDots} of $SessionsPerCycle sessions complete"
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(SessionsPerCycle) { index ->
            val filled = index < state.filledDots
            val current = state.phase == Phase.Focus && index == state.filledDots
            val dotModifier = when {
                filled -> Modifier.background(Forest, CircleShape)
                current -> Modifier
                    .background(Mint, CircleShape)
                    .border(1.dp, Forest, CircleShape)
                else -> Modifier.background(StoneLight, CircleShape)
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .then(dotModifier),
            )
        }
    }
}

@Composable
private fun StatusRow(
    state: PomodoroUiState,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val statusColor: Color = when {
            state.isRunning -> Forest
            state.isMidSession -> Mint
            else -> Stone
        }
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(statusColor, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = state.statusText,
            color = Muted,
            textAlign = TextAlign.Center,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = if (compact) 10.sp else 11.sp,
                lineHeight = if (compact) 14.sp else 16.sp,
            ),
        )
    }
}

private fun sessionHeatmapColor(intensity: Float): Color {
    val clamped = intensity.coerceIn(0f, 1f)
    if (clamped <= 0f) return StoneLight
    return when {
        clamped < 0.35f -> androidx.compose.ui.graphics.lerp(
            MintLight,
            Mint,
            (clamped / 0.35f).coerceIn(0f, 1f),
        )
        clamped < 0.75f -> androidx.compose.ui.graphics.lerp(
            Mint,
            Forest,
            ((clamped - 0.35f) / 0.40f).coerceIn(0f, 1f),
        )
        else -> androidx.compose.ui.graphics.lerp(
            Forest,
            ForestDeep,
            ((clamped - 0.75f) / 0.25f).coerceIn(0f, 1f),
        )
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
private fun RhythmCard(
    state: PomodoroUiState,
    selectedDayEpoch: Long?,
    onSelectDay: (Long?) -> Unit,
    onDismissDaySelection: () -> Unit,
    onResetDaily: () -> Unit,
    onSelectSubject: (String) -> Unit = {},
    onAddCustomSubject: (String, Long) -> Boolean = { _, _ -> false },
    onRemoveCustomSubject: (String) -> Unit = {},
    onSetShowYearHistory: (Boolean) -> Unit = {},
    onExportCsv: () -> Unit = {},
    onSeedDemoHistory: () -> Unit = {},
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    contentArrangement: Arrangement.Vertical = Arrangement.Top,
) {
    val goalCount = minOf(state.completedFocus, SessionsPerCycle)
    val goalProgress = (goalCount.toFloat() / SessionsPerCycle).coerceIn(0f, 1f)
    val goalPercent = (goalProgress * 100f).roundToInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .softRaised(Paper, if (compact) 24.dp else 26.dp, if (compact) 6.dp else 8.dp)
            .padding(if (compact) 14.dp else 22.dp),
        verticalArrangement = contentArrangement,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DAILY RHYTHM & LEDGER",
                color = Muted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 9.sp else 10.sp,
                    letterSpacing = 1.5.sp,
                ),
            )
            ResetTodayButton(
                enabled = state.canResetDaily && !state.isAdvancedFocusLocked,
                compact = compact,
                onClick = onResetDaily,
            )
        }
        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatBlock(
                value = state.completedFocus.toString(),
                label = "SESSIONS",
                compact = compact,
                modifier = Modifier.weight(1f),
            )
            StatDivider(compact = compact)
            StatBlock(
                value = state.focusedMinutes.toString(),
                label = "MINUTES",
                compact = compact,
                modifier = Modifier.weight(1f),
            )
            StatDivider(compact = compact)
            StatBlock(
                value = "$goalCount/$SessionsPerCycle",
                label = "GOAL",
                compact = compact,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(if (compact) 10.dp else 16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(if (compact) 9.dp else 12.dp),
            ) {
                val radius = size.height / 2f
                drawRoundRect(
                    color = StoneLight,
                    cornerRadius = CornerRadius(radius, radius),
                )
                if (goalProgress > 0f) {
                    drawRoundRect(
                        color = Forest,
                        size = Size(size.width * goalProgress, size.height),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }
            Text(
                text = "$goalPercent%",
                color = Ink,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = if (compact) 18.sp else 21.sp,
                    fontFeatureSettings = "lnum",
                ),
            )
        }
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "AUTO RESET · 12:00 AM",
                color = Muted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 9.sp else 10.sp,
                    letterSpacing = 1.2.sp,
                ),
            )
            Text(
                text = "$goalCount OF $SessionsPerCycle",
                color = Forest,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 10.sp else 11.sp,
                    letterSpacing = 1.4.sp,
                ),
            )
        }

        if (state.historyWindow.isNotEmpty()) {
            Spacer(Modifier.height(if (compact) 10.dp else 16.dp))
            HorizontalDivider(color = Border, thickness = 1.dp)
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            ThirtyDayLedgerSection(
                window = state.historyWindow,
                fullWindow = state.fullHistoryWindow,
                showYearHistory = state.showYearHistory,
                todayEpoch = state.todayEpoch,
                focusLengthMinutes = state.settings.focus,
                streakDays = state.currentStreakDays,
                activeStreakEpochs = state.activeStreakEpochs,
                totalMinutes = state.thirtyDayFocusedMinutes,
                totalSessions = state.thirtyDayCompletedSessions,
                yearMinutes = state.yearFocusedMinutes,
                yearSessions = state.yearCompletedSessions,
                yearHistoryMonths = if (state.showYearHistory) {
                    state.yearHistoryMonths
                } else {
                    emptyList()
                },
                tasksForDay = state::tasksForDay,
                selectedDayEpoch = selectedDayEpoch,
                onSelectDay = { epoch ->
                    onSelectDay(if (selectedDayEpoch == epoch) null else epoch)
                },
                onClearSelection = onDismissDaySelection,
                onSetShowYearHistory = onSetShowYearHistory,
                onExportCsv = onExportCsv,
                csvEnabled = !state.isFocusExitBlocked,
                compact = compact,
            )
        }

        Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
        HorizontalDivider(color = Border, thickness = 1.dp)
        Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
        SubjectBreakdownSection(
            tags = state.subjectTags,
            activeSubjectId = state.activeSubjectId,
            breakdown = state.subjectBreakdown,
            compact = compact,
            onSelectSubject = onSelectSubject,
            onAddCustomSubject = onAddCustomSubject,
            onRemoveCustomSubject = onRemoveCustomSubject,
        )
    }
}

@Composable
private fun ThirtyDayLedgerSection(
    window: List<DayHistoryEntry>,
    fullWindow: List<DayHistoryEntry>,
    showYearHistory: Boolean,
    todayEpoch: Long,
    focusLengthMinutes: Int,
    streakDays: Int,
    activeStreakEpochs: Set<Long>,
    totalMinutes: Int,
    totalSessions: Int,
    yearMinutes: Int,
    yearSessions: Int,
    yearHistoryMonths: List<MonthHistoryGroup> = emptyList(),
    tasksForDay: (Long) -> List<StudyTask> = { emptyList() },
    selectedDayEpoch: Long?,
    onSelectDay: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onSetShowYearHistory: (Boolean) -> Unit,
    onExportCsv: () -> Unit,
    csvEnabled: Boolean = true,
    compact: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val displayedGridWindow = remember(window, fullWindow, showYearHistory, todayEpoch) {
        if (showYearHistory && fullWindow.isNotEmpty()) {
            val pastAndToday = fullWindow.filter { it.dayEpoch <= todayEpoch }
            if (pastAndToday.size >= 90) {
                pastAndToday.takeLast(90)
            } else {
                fullWindow.take(90)
            }
        } else {
            window
        }
    }
    val lookupPool = remember(window, fullWindow) {
        if (fullWindow.isNotEmpty()) fullWindow else window
    }
    val selectedEntry = remember(lookupPool, selectedDayEpoch) {
        lookupPool.firstOrNull { it.dayEpoch == selectedDayEpoch }
    }
    val maxSessionsInWindow = remember(displayedGridWindow, fullWindow, showYearHistory, todayEpoch) {
        // In year mode the visible grid is still only 90 days, but every month card is
        // rendered; normalising against the 90-day slice flattened older high-session days.
        val pool = if (showYearHistory && fullWindow.isNotEmpty()) fullWindow else displayedGridWindow
        pool
            .filter { it.dayEpoch <= todayEpoch }
            .maxOfOrNull { it.completedFocus }
            ?.coerceAtLeast(SessionsPerCycle)
            ?: SessionsPerCycle
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (showYearHistory) {
                "${HISTORY_WINDOW_DAYS_YEAR}-DAY LEDGER"
            } else {
                "$HISTORY_WINDOW_DAYS-DAY LEDGER"
            },
            color = Muted,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (compact) 9.sp else 10.sp,
                letterSpacing = if (compact) 1.1.sp else 1.4.sp,
            ),
        )
        Spacer(Modifier.width(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(role = Role.Button) {
                        onSetShowYearHistory(!showYearHistory)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (showYearHistory) Forest else Parchment,
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = if (showYearHistory) {
                            "${HISTORY_WINDOW_DAYS_YEAR}D ON"
                        } else {
                            "${HISTORY_WINDOW_DAYS_YEAR}D"
                        },
                        color = if (showYearHistory) Ivory else Forest,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (compact) 7.sp else 8.sp,
                            letterSpacing = 0.7.sp,
                        ),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(
                        enabled = csvEnabled,
                        role = Role.Button,
                        onClick = onExportCsv,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Parchment, RoundedCornerShape(999.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "CSV",
                        color = Forest,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (compact) 7.sp else 8.sp,
                            letterSpacing = 0.7.sp,
                        ),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(role = Role.Button) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val targetEpoch = activeStreakEpochs.maxOrNull() ?: todayEpoch
                        onSelectDay(targetEpoch)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            color = if (streakDays > 0) MintLight else Parchment,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = if (streakDays == 1) "1 DAY STREAK" else "$streakDays DAY STREAK",
                        color = if (streakDays > 0) Forest else Muted,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (compact) 7.5.sp else 8.5.sp,
                            letterSpacing = 0.9.sp,
                        ),
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

    if (showYearHistory) {
        val activeYearDays = remember(fullWindow, todayEpoch) {
            fullWindow.count {
                it.dayEpoch <= todayEpoch &&
                    (it.completedFocus > 0 || it.focusedMinutes > 0)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MintLight, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "365D ARCHIVE · $activeYearDays ACTIVE DAYS",
                color = Forest,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    letterSpacing = 1.0.sp,
                ),
            )
            Text(
                text = "$yearSessions SESSIONS · $yearMinutes MIN",
                color = Forest,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    letterSpacing = 1.0.sp,
                ),
            )
        }
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
        ) {
            yearHistoryMonths.forEach { month ->
                val selectedDayInThisMonth = selectedEntry?.takeIf { entry ->
                    month.days.any { it.dayEpoch == entry.dayEpoch }
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    MonthHistoryCard(
                        month = month,
                        windowStartEpoch = todayEpoch - (HISTORY_WINDOW_DAYS_YEAR - 1),
                        todayEpoch = todayEpoch,
                        maxSessionsInWindow = maxSessionsInWindow,
                        selectedDayEpoch = selectedDayEpoch,
                        compact = compact,
                        onSelectDay = onSelectDay,
                    )
                    if (selectedDayInThisMonth != null) {
                        val dayOfMonthNumber = remember(selectedDayInThisMonth.dayEpoch) {
                            runCatching {
                                LocalDate.ofEpochDay(selectedDayInThisMonth.dayEpoch).dayOfMonth
                            }.getOrDefault(1)
                        }
                        DayDetailPanel(
                            entry = selectedDayInThisMonth,
                            dayNumber = dayOfMonthNumber,
                            todayEpoch = todayEpoch,
                            isStreakDay = selectedDayInThisMonth.dayEpoch in activeStreakEpochs,
                            focusLengthMinutes = focusLengthMinutes,
                            dayTasks = tasksForDay(selectedDayInThisMonth.dayEpoch),
                            compact = compact,
                            onDismiss = onClearSelection,
                        )
                    }
                }
            }
        }
    } else {
        val columnsPerRow = 10
        val rows = remember(displayedGridWindow, columnsPerRow) {
            displayedGridWindow.chunked(columnsPerRow)
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        ) {
            rows.forEach { rowEntries ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
                ) {
                    rowEntries.forEach { entry ->
                        val isFuture = entry.dayEpoch > todayEpoch
                        val isSelected = entry.dayEpoch == selectedDayEpoch
                        val isToday = entry.dayEpoch == todayEpoch
                        val cellColor = if (isFuture) {
                            StoneLight.copy(alpha = 0.72f)
                        } else {
                            val intensity = sessionGreenIntensity(
                                completedFocus = entry.completedFocus,
                                focusedMinutes = entry.focusedMinutes,
                                maxSessionsInWindow = maxSessionsInWindow,
                            )
                            sessionHeatmapColor(intensity)
                        }
                        val dateText = remember(entry.dayEpoch) {
                            runCatching {
                                LocalDate.ofEpochDay(entry.dayEpoch).format(ShortDateFormatter)
                            }.getOrDefault("Day")
                        }
                        val cellShape = RoundedCornerShape(5.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(if (compact) 11.dp else 17.dp)
                                .clip(cellShape)
                                .background(cellColor, cellShape)
                                .then(
                                    when {
                                        isSelected -> Modifier.border(2.dp, Forest, cellShape)
                                        isToday && entry.completedFocus == 0 ->
                                            Modifier.border(
                                                1.dp,
                                                Forest.copy(alpha = 0.45f),
                                                cellShape,
                                            )
                                        else -> Modifier
                                    },
                                )
                                .clickable(role = Role.Button) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectDay(entry.dayEpoch)
                                }
                                .semantics {
                                    contentDescription =
                                        "$dateText: ${entry.completedFocus} sessions, ${entry.focusedMinutes} minutes"
                                },
                        )
                    }
                }
            }
        }

        if (selectedEntry != null) {
            // "DAY n" must mean the same thing in both ledger modes: the day of the month.
            val selectedDayOfMonth = remember(selectedEntry.dayEpoch) {
                runCatching {
                    LocalDate.ofEpochDay(selectedEntry.dayEpoch).dayOfMonth
                }.getOrDefault(1)
            }
            DayDetailPanel(
                entry = selectedEntry,
                dayNumber = selectedDayOfMonth,
                todayEpoch = todayEpoch,
                isStreakDay = selectedEntry.dayEpoch in activeStreakEpochs,
                focusLengthMinutes = focusLengthMinutes,
                dayTasks = tasksForDay(selectedEntry.dayEpoch),
                compact = compact,
                onDismiss = onClearSelection,
            )
        }
    }

    Spacer(Modifier.height(if (compact) 5.dp else 8.dp))

    val totalsText = if (showYearHistory) {
        "365D TOTAL · $yearSessions SESSIONS · $yearMinutes MIN"
    } else {
        "30D TOTAL · $totalSessions SESSIONS · $totalMinutes MIN"
    }
    val hintText = if (selectedEntry != null) "TAP OUTSIDE TO CLOSE" else "TAP ANY DAY FOR DETAILS"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = totalsText,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 8.sp else 9.sp,
                    letterSpacing = 1.1.sp,
                ),
            )
            Text(
                text = hintText,
                color = if (selectedEntry != null) Forest else Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 8.sp else 9.sp,
                    letterSpacing = 1.0.sp,
                ),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf(0f, 0.25f, 0.50f, 0.75f, 1.0f).forEach { level ->
                Box(
                    modifier = Modifier
                        .size(if (compact) 5.dp else 6.dp)
                        .background(
                            sessionHeatmapColor(level),
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}

@Composable
private fun MonthHistoryCard(
    month: MonthHistoryGroup,
    windowStartEpoch: Long,
    todayEpoch: Long,
    maxSessionsInWindow: Int,
    selectedDayEpoch: Long?,
    compact: Boolean,
    onSelectDay: (Long) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val cellHeight = if (compact) 13.dp else 18.dp
    val cellShape = RoundedCornerShape(4.dp)
    val cells: List<DayHistoryEntry?> =
        List(month.leadingEmptySlots) { null } + month.days.map { it as DayHistoryEntry? }
    val weeks = cells.chunked(7)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Parchment.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(horizontal = if (compact) 7.dp else 10.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = month.title,
                color = Ink,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 8.sp else 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                ),
            )
            Text(
                text = "${month.totalSessions} SESSIONS · ${month.totalMinutes}M",
                color = Forest,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 7.sp else 8.sp,
                    letterSpacing = 0.6.sp,
                ),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Text(
                    text = label,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                week.forEach { entry ->
                    if (entry == null) {
                        Spacer(
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeight),
                        )
                    } else {
                        val inWindow = entry.dayEpoch in windowStartEpoch..todayEpoch
                        val isSelected = entry.dayEpoch == selectedDayEpoch
                        val isToday = entry.dayEpoch == todayEpoch
                        val intensity = sessionGreenIntensity(
                            completedFocus = entry.completedFocus,
                            focusedMinutes = entry.focusedMinutes,
                            maxSessionsInWindow = maxSessionsInWindow,
                        )
                        val cellColor = sessionHeatmapColor(intensity).let {
                            if (inWindow) it else it.copy(alpha = 0.28f)
                        }
                        val dateText = remember(entry.dayEpoch) {
                            runCatching {
                                LocalDate.ofEpochDay(entry.dayEpoch).format(ShortDateFormatter)
                            }.getOrDefault("Day")
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(cellHeight)
                                .clip(cellShape)
                                .background(cellColor, cellShape)
                                .then(
                                    when {
                                        isSelected -> Modifier.border(2.dp, Forest, cellShape)
                                        isToday && entry.completedFocus == 0 ->
                                            Modifier.border(
                                                1.dp,
                                                Forest.copy(alpha = 0.45f),
                                                cellShape,
                                            )
                                        else -> Modifier
                                    },
                                )
                                .clickable(
                                    enabled = inWindow,
                                    role = Role.Button,
                                ) {
                                    haptic.performHapticFeedback(
                                        HapticFeedbackType.TextHandleMove,
                                    )
                                    onSelectDay(entry.dayEpoch)
                                }
                                .semantics {
                                    contentDescription =
                                        "$dateText: ${entry.completedFocus} sessions, ${entry.focusedMinutes} minutes"
                                },
                        )
                    }
                }
                repeat(7 - week.size) {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .height(cellHeight),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
        }
    }
}

/**
 * Day details panel shown below the 30-day grid (or below the individual month card in 365D view).
 * Displays the day's sessions, focused minutes, flow overtime, AND the saved tasks for that day
 * separated into Completed and Incomplete tasks.
 */
@Composable
private fun DayDetailPanel(
    entry: DayHistoryEntry,
    dayNumber: Int,
    todayEpoch: Long,
    isStreakDay: Boolean,
    focusLengthMinutes: Int,
    dayTasks: List<StudyTask> = emptyList(),
    compact: Boolean,
    onDismiss: () -> Unit,
) {
    val formattedDate = remember(entry.dayEpoch) {
        runCatching {
            LocalDate.ofEpochDay(entry.dayEpoch)
                .format(DetailedDateFormatter)
                .uppercase(Locale.US)
        }.getOrDefault("DAY $dayNumber")
    }
    val statusBadge = when {
        entry.dayEpoch == todayEpoch -> "TODAY"
        entry.dayEpoch > todayEpoch -> "UPCOMING"
        isStreakDay -> "ACTIVE STREAK"
        entry.completedFocus >= SessionsPerCycle -> "GOAL MET"
        entry.completedFocus > 0 || entry.focusedMinutes > 0 -> "COMPLETED"
        else -> "REST DAY"
    }
    val effectiveSessionMinutes = when {
        entry.dayEpoch < todayEpoch &&
            focusLengthMinutes != DefaultSettings.focus &&
            entry.focusedMinutes >= entry.completedFocus * DefaultSettings.focus ->
            DefaultSettings.focus
        entry.focusedMinutes < entry.completedFocus * focusLengthMinutes ->
            DefaultSettings.focus
        else -> focusLengthMinutes
    }
    val baseMinutes = entry.completedFocus * effectiveSessionMinutes
    val flowOvertimeMinutes = (entry.focusedMinutes - baseMinutes).coerceAtLeast(0)
    val hours = entry.focusedMinutes / 60
    val mins = entry.focusedMinutes % 60
    val formattedDuration = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

    val completedTasks = remember(dayTasks) { dayTasks.filter { it.isCompleted } }
    val incompleteTasks = remember(dayTasks) { dayTasks.filterNot { it.isCompleted } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (compact) 6.dp else 10.dp)
            .background(Parchment, RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = if (compact) 9.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DAY $dayNumber · $formattedDate",
                color = Ink,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 9.sp else 10.sp,
                    letterSpacing = 1.2.sp,
                ),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (entry.completedFocus > 0) MintLight else StoneLight,
                            RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = statusBadge,
                        color = if (entry.completedFocus > 0) Forest else Muted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            letterSpacing = 1.0.sp,
                        ),
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close day details",
                    tint = Muted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailMetricColumn(
                value = "${entry.completedFocus} / $SessionsPerCycle",
                label = "SESSIONS",
                modifier = Modifier.weight(1f),
            )
            DetailMetricColumn(
                value = formattedDuration,
                label = "FOCUSED (${entry.focusedMinutes}M)",
                modifier = Modifier.weight(1f),
            )
            DetailMetricColumn(
                value = "+${flowOvertimeMinutes}m",
                label = "FLOW OVERTIME",
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Border, thickness = 1.dp)
        Spacer(Modifier.height(8.dp))

        if (completedTasks.isEmpty() && incompleteTasks.isEmpty()) {
            Text(
                text = "No tasks recorded for this day.",
                color = Muted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "COMPLETED TASKS (${completedTasks.size})",
                    color = Forest,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        letterSpacing = 1.1.sp,
                    ),
                )
                if (completedTasks.isEmpty()) {
                    Text(
                        text = "None completed",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    )
                } else {
                    completedTasks.forEach { task ->
                        DayTaskLedgerRow(task = task, completed = true)
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = "INCOMPLETE TASKS (${incompleteTasks.size})",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.sp,
                        letterSpacing = 1.1.sp,
                    ),
                )
                if (incompleteTasks.isEmpty()) {
                    Text(
                        text = "None incomplete",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    )
                } else {
                    incompleteTasks.forEach { task ->
                        DayTaskLedgerRow(task = task, completed = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTaskLedgerRow(
    task: StudyTask,
    completed: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .background(
                    if (completed) Forest else Ivory,
                    RoundedCornerShape(4.dp),
                )
                .border(1.dp, if (completed) Forest else Stone, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Ivory,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        Spacer(Modifier.width(7.dp))
        Text(
            text = task.title,
            color = if (completed) Muted else Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${task.completedPomodoros}/${task.estimatedPomodoros}",
            color = Muted,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
        )
    }
}

@Composable
private fun DetailMetricColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = Forest,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Text(
            text = label,
            color = Muted,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                letterSpacing = 1.0.sp,
            ),
        )
    }
}

@Composable
private fun ResetTodayButton(
    enabled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = if (enabled) Forest else Muted.copy(alpha = 0.45f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = "Reset today's sessions" }
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(if (compact) 12.dp else 13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "RESET TODAY",
            color = contentColor,
            maxLines = 1,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (compact) 8.sp else 9.sp,
                letterSpacing = 1.0.sp,
            ),
        )
    }
}

@Composable
private fun StatBlock(
    value: String,
    label: String,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = Ink,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = if (compact) 26.sp else 34.sp,
                lineHeight = if (compact) 28.sp else 36.sp,
                fontWeight = FontWeight.Normal,
                fontFeatureSettings = "lnum",
            ),
        )
        Spacer(Modifier.height(if (compact) 1.dp else 3.dp))
        Text(
            text = label,
            color = Muted,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (compact) 9.sp else 10.sp,
                letterSpacing = 1.5.sp,
            ),
        )
    }
}

@Composable
private fun StatDivider(compact: Boolean = false) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(if (compact) 28.dp else 42.dp)
            .background(Border),
    )
}

@Composable
private fun FocusThemeAmbientOverlay(
    themeMode: AppThemeMode,
    isRunning: Boolean,
    isLocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusMorph by animateFloatAsState(
        targetValue = if (themeMode != AppThemeMode.SANCTUARY) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "overlayFocusMorph",
    )
    val advancedMorph by animateFloatAsState(
        targetValue = if (themeMode == AppThemeMode.ADVANCED_FOCUS) 1f else 0f,
        animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing),
        label = "overlayAdvancedMorph",
    )
    val lockMorph by animateFloatAsState(
        targetValue = if (isLocked) 1f else 0f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "overlayLockMorph",
    )

    if (focusMorph <= 0.005f && advancedMorph <= 0.005f) return

    val infiniteTransition = rememberInfiniteTransition(label = "themeAmbientOverlay")
    val breath by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ambientBreath",
    )
    val orbitDeg by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isLocked || isRunning) 20_000 else 30_000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambientOrbit",
    )

    val primaryTint = Forest
    val secondaryTint = Mint

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)

        // Origin of the already-present top-right BotanicalBranch
        val branchOrigin = Offset(w * 0.90f, h * 0.06f)

        // 1. Focus Mode: Soft botanical canopy halos & companion vine morphing out of branchOrigin
        if (focusMorph > 0.005f) {
            val haloCenter = Offset(
                x = lerp(branchOrigin.x, w * 0.24f, focusMorph),
                y = lerp(branchOrigin.y, h * 0.16f, focusMorph),
            )
            val haloRadius = minDim * (0.08f + focusMorph * (0.34f + breath * 0.06f))
            drawCircle(
                color = secondaryTint.copy(alpha = focusMorph * (0.14f + breath * 0.06f)),
                radius = haloRadius,
                center = haloCenter,
            )

            val lowerHaloCenter = Offset(
                x = lerp(branchOrigin.x, w * 0.82f, focusMorph),
                y = lerp(branchOrigin.y, h * 0.52f, focusMorph),
            )
            drawCircle(
                color = primaryTint.copy(alpha = focusMorph * (0.055f + (1f - breath) * 0.035f)),
                radius = minDim * (0.06f + focusMorph * (0.28f + (1f - breath) * 0.05f)),
                center = lowerHaloCenter,
            )

            val rad = Math.toRadians(orbitDeg.toDouble())
            val driftX = (cos(rad) * minDim * 0.035f * focusMorph).toFloat()
            val driftY = (sin(rad) * minDim * 0.035f * focusMorph).toFloat()
            val dewCenter = Offset(
                x = lerp(branchOrigin.x, w * 0.16f + driftX, focusMorph),
                y = lerp(branchOrigin.y, h * 0.70f + driftY, focusMorph),
            )
            drawCircle(
                color = secondaryTint.copy(alpha = focusMorph * 0.16f),
                radius = minDim * 0.085f * focusMorph,
                center = dewCenter,
            )

            // Companion vine smoothly sprouting from the bottom-left corner
            val vineTipX = lerp(0f, w * 0.28f, focusMorph)
            val vineTipY = lerp(h, h * 0.86f, focusMorph)
            val bottomVine = Path().apply {
                moveTo(0f, h)
                cubicTo(
                    lerp(0f, w * 0.08f, focusMorph),
                    lerp(h, h * 0.94f, focusMorph),
                    lerp(0f, w * 0.18f, focusMorph),
                    lerp(h, h * 0.89f, focusMorph),
                    vineTipX,
                    vineTipY,
                )
            }
            drawPath(
                path = bottomVine,
                color = primaryTint.copy(alpha = 0.24f * focusMorph),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // 2. Advanced Focus Mode: Concentric botanical sanctuary arcs & perimeter vine frame
        //    morphing smoothly out of the Focus Mode canopy
        if (advancedMorph > 0.005f) {
            val centerTop = Offset(
                x = lerp(branchOrigin.x, w * 0.5f, advancedMorph),
                y = lerp(branchOrigin.y, h * 0.28f, advancedMorph),
            )
            val shieldRadius = minDim * (0.12f + advancedMorph * (0.34f + breath * 0.04f))
            drawCircle(
                color = secondaryTint.copy(alpha = advancedMorph * (0.11f + breath * 0.05f)),
                radius = shieldRadius,
                center = centerTop,
            )
            drawCircle(
                color = primaryTint.copy(alpha = advancedMorph * (0.10f + lockMorph * 0.08f)),
                radius = shieldRadius * (1.0f + advancedMorph * 0.10f),
                center = centerTop,
                style = Stroke(width = (1.1f * advancedMorph).dp.toPx(), cap = StrokeCap.Round),
            )
            val arcCount = 4
            val sweep = advancedMorph * (28f + lockMorph * 10f)
            for (i in 0 until arcCount) {
                drawArc(
                    color = primaryTint.copy(alpha = advancedMorph * (0.15f + lockMorph * 0.09f)),
                    startAngle = orbitDeg + i * (360f / arcCount),
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(centerTop.x - shieldRadius, centerTop.y - shieldRadius),
                    size = Size(shieldRadius * 2f, shieldRadius * 2f),
                    style = Stroke(width = (1.8f * advancedMorph).dp.toPx(), cap = StrokeCap.Round),
                )
            }

            // Delicate botanical sanctuary frame unfurling along the screen edges from top-right
            val inset = 8.dp.toPx()
            val frameWidth = (w - inset * 2f) * advancedMorph
            val frameHeight = (h - inset * 2f) * advancedMorph
            if (frameWidth > 4f && frameHeight > 4f) {
                drawRoundRect(
                    color = primaryTint.copy(alpha = advancedMorph * (0.14f + lockMorph * 0.10f)),
                    topLeft = Offset(w - inset - frameWidth, inset),
                    size = Size(frameWidth, frameHeight),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                    style = Stroke(width = (1.2f * advancedMorph).dp.toPx()),
                )
            }
        }
    }
}

private fun resolveAudioTrackTitle(
    context: Context,
    uri: Uri,
    fallback: String,
): String {
    val fromCursor = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
    }.getOrNull()
        ?.substringBeforeLast('.')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    if (fromCursor != null) return fromCursor.take(42)

    val fromRingtone = runCatching {
        RingtoneManager.getRingtone(context, uri)?.getTitle(context)
    }.getOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    if (fromRingtone != null) return fromRingtone.take(42)

    val fromSegment = uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.substringAfterLast(':')
        ?.substringBeforeLast('.')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    return (fromSegment ?: fallback).take(42)
}
