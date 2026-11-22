package com.studyapp.pomodoro.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.studyapp.pomodoro.AmbientSoundscape
import com.studyapp.pomodoro.FocusBlockingMode
import com.studyapp.pomodoro.FocusPermissionType
import com.studyapp.pomodoro.PermissionPromptState
import com.studyapp.pomodoro.PomodoroUiState
import com.studyapp.pomodoro.SelectableBuiltInSoundscapes
import com.studyapp.pomodoro.StrictFocusSettings
import com.studyapp.pomodoro.StudySubjectTag
import com.studyapp.pomodoro.StudyTask
import com.studyapp.pomodoro.SubjectAllocationSummary
import com.studyapp.pomodoro.focus.AppCategory
import com.studyapp.pomodoro.focus.BlockableApp
import com.studyapp.pomodoro.focus.InstalledApps
import com.studyapp.pomodoro.focus.displayLabel
import com.studyapp.pomodoro.focus.matchesQuery
import com.studyapp.pomodoro.ui.theme.Border
import com.studyapp.pomodoro.ui.theme.Forest
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val CustomSubjectPalette = listOf(
    0xFF0F5132L,
    0xFF2B5B84L,
    0xFF8C5A32L,
    0xFF6E4B7AL,
    0xFF996515L,
    0xFF2E6F5EL,
)

/**
 * CALM / SYSTEM Tactile capsule switch (`Forest` #0F5132 track with raised `Ivory` thumb when On,
 * soft `StoneLight` track with raised `Ivory` thumb when Off).
 */
@Composable
fun CalmSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) Forest else StoneLight,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "calmSwitchTrack",
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Forest else Stone,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "calmSwitchBorder",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 0.dp,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "calmSwitchThumb",
    )
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.48f)
            .width(52.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor, RoundedCornerShape(999.dp))
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(
                enabled = enabled,
                role = Role.Switch,
            ) {
                onCheckedChange(!checked)
            }
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .softRaised(Ivory, 999.dp, 3.dp, highlight = true),
        )
    }
}

/**
 * Compact tactile capsule switch placed directly in front of "Focus" and "Music" in the
 * pill triggers so the user can toggle Focus Mode and Music on/off immediately.
 */
@Composable
fun CalmMiniSwitch(
    checked: Boolean,
    invertedSurface: Boolean = checked,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = when {
            invertedSurface && checked -> Mint
            checked -> Forest
            else -> StoneLight
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "miniSwitchTrack",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            invertedSurface && checked -> Ivory.copy(alpha = 0.55f)
            checked -> Forest
            else -> Stone
        },
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "miniSwitchBorder",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (invertedSurface && checked) Forest else Ivory,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "miniSwitchThumbColor",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 14.dp else 0.dp,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "miniSwitchThumbOffset",
    )
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.52f)
            .width(34.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(trackColor, RoundedCornerShape(999.dp))
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(16.dp)
                .background(thumbColor, CircleShape),
        )
    }
}

/**
 * CALM / SYSTEM Rounded-square checkbox (`Forest` fill with crisp `Ivory` check when
 * selected; soft `Ivory` surface with `Stone` border when unselected).
 */
@Composable
fun CalmCheckbox(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(7.dp)
    // toggleable() exposes the real checked state to TalkBack (the old clickable + child
    // icon description announced every task as "Completed"), and the 48 dp box gives the
    // 22 dp visual an accessible hit target.
    Box(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onCheckedChange() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(shape)
                .background(if (checked) Forest else Ivory, shape)
                .border(1.dp, if (checked) Forest else Stone, shape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Ivory,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * Two sculptural CALM / SYSTEM pills placed directly below the circular timer:
 * 1. **Focus** pill:
 *    - Left body has a toggle switch in front of "Focus" to turn Focus Mode on/off directly
 *      (checking Accessibility permission; disabled when `state.isAdvancedFocusLocked`), and
 *      displays the active Focus/Advanced status below "Focus".
 *    - Right chevron opens the Focus menu: blocking policy (Off / Block apps / Strict),
 *      the blocked-app picker, block-during-breaks, and Advanced Focus.
 * 2. **Music** pill:
 *    - Left body has a toggle switch in front of "Music" to turn Music on/off directly with
 *      a default soundscape (`Soft rain`) selected out of the box.
 *    - Right chevron opens the Music menu with built-in soundscapes, device storage audio
 *      picker, inbuilt ringtone/music tone picker, and volume slider.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FocusAndMusicPillsRow(
    state: PomodoroUiState,
    onManualFocusToggle: () -> Unit,
    onToggleAutoFocus: (Boolean) -> Unit = {},
    onToggleAdvancedFocus: () -> Unit,
    onToggleMusic: () -> Unit = {},
    onSelectAmbientSoundscape: (AmbientSoundscape) -> Unit,
    onOpenStorageMusicPicker: () -> Unit = {},
    onOpenInbuiltMusicPicker: () -> Unit = {},
    onUpdateAmbientVolume: (Float) -> Unit,
    onPreviewAmbientSoundscape: () -> Unit,
    onSetBlockingMode: (FocusBlockingMode) -> Unit = {},
    onUpdateBlockedPackages: (Set<String>) -> Unit = {},
    onSetBlockDuringBreaks: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    expanded: Boolean = false,
) {
    var openMenu by rememberSaveable { mutableStateOf<String?>(null) }
    var showBlocklistDialog by rememberSaveable { mutableStateOf(false) }
    val focusOpen = openMenu == "focus"
    val musicOpen = openMenu == "music"
    val focusActive = state.isFocusEnabled
    val soundActive = state.isMusicEnabled
    val selectedTrack = state.selectedMusicTrack
    val pillHeight = when {
        expanded -> 62.dp
        compact -> 48.dp
        else -> 54.dp
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (expanded) 16.dp else 12.dp),
        ) {
            // --- Left Pill: FOCUS (text on left, toggle switch on right of text, chevron on far right) ---
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(pillHeight)
                    .softRaised(
                        color = if (focusActive) Forest else Paper,
                        cornerRadius = 999.dp,
                        elevation = if (focusActive) 6.dp else 5.dp,
                        highlight = !focusActive,
                    )
                    .clip(RoundedCornerShape(999.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .alpha(if (state.canToggleFocusMode) 1f else 0.58f)
                        .clickable(
                            enabled = state.canToggleFocusMode,
                            role = Role.Switch,
                            onClick = onManualFocusToggle,
                        )
                        .padding(start = if (compact) 14.dp else 16.dp, end = if (compact) 8.dp else 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Focus",
                            color = if (focusActive) Ivory else Ink,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = if (compact) 16.sp else 18.sp,
                                lineHeight = if (compact) 18.sp else 20.sp,
                            ),
                        )
                        val focusSub = when {
                            state.isAdvancedFocusLocked -> "Locked"
                            focusActive && state.strictFocus.advancedFocusEnabled -> "On · Adv"
                            focusActive -> "On"
                            state.strictFocus.advancedFocusEnabled -> "Advanced"
                            else -> "Off"
                        }
                        Text(
                            text = focusSub.uppercase(),
                            color = if (focusActive) Mint else Muted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                letterSpacing = 1.1.sp,
                            ),
                        )
                    }
                    Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                    CalmMiniSwitch(
                        checked = focusActive,
                        invertedSurface = focusActive,
                        enabled = state.canToggleFocusMode,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(
                            if (focusActive) Ivory.copy(alpha = 0.22f) else Border,
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(role = Role.Button) {
                            openMenu = if (focusOpen) null else "focus"
                        }
                        .padding(horizontal = if (compact) 10.dp else 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (focusOpen) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = "Open Focus menu",
                        tint = if (focusActive) Ivory else Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // --- Right Pill: MUSIC (text on left, toggle switch on right of text, chevron on far right) ---
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(pillHeight)
                    .softRaised(
                        color = if (soundActive) Forest else Paper,
                        cornerRadius = 999.dp,
                        elevation = if (soundActive) 6.dp else 5.dp,
                        highlight = !soundActive,
                    )
                    .clip(RoundedCornerShape(999.dp)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            role = Role.Switch,
                            onClick = onToggleMusic,
                        )
                        .padding(start = if (compact) 14.dp else 16.dp, end = if (compact) 8.dp else 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Music",
                            color = if (soundActive) Ivory else Ink,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = if (compact) 16.sp else 18.sp,
                                lineHeight = if (compact) 18.sp else 20.sp,
                            ),
                        )
                        val musicSub = if (soundActive) {
                            state.activeMusicTitle.uppercase()
                        } else {
                            "OFF · ${state.activeMusicTitle.uppercase()}"
                        }
                        Text(
                            text = musicSub,
                            color = if (soundActive) Mint else Muted,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                letterSpacing = 1.0.sp,
                            ),
                        )
                    }
                    Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                    CalmMiniSwitch(
                        checked = soundActive,
                        invertedSurface = soundActive,
                        enabled = true,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(
                            if (soundActive) Ivory.copy(alpha = 0.22f) else Border,
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable(role = Role.Button) {
                            openMenu = if (musicOpen) null else "music"
                        }
                        .padding(horizontal = if (compact) 10.dp else 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (musicOpen) {
                            Icons.Rounded.KeyboardArrowUp
                        } else {
                            Icons.Rounded.KeyboardArrowDown
                        },
                        contentDescription = "Open Music menu",
                        tint = if (soundActive) Ivory else Muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        // --- Expandable Focus Pill Menu: blocking policy + Advanced Focus ---
        if (focusOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .softRaised(Paper, 24.dp, 7.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "FOCUS PROTECTION",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp,
                    ),
                )

                val modeLocked = state.isAdvancedFocusLocked
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FocusBlockingMode.entries.forEach { mode ->
                        val selected = state.strictFocus.blockingMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) Forest else Parchment,
                                    RoundedCornerShape(999.dp),
                                )
                                .border(
                                    1.dp,
                                    if (selected) Forest else Border,
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable(
                                    enabled = !modeLocked,
                                    role = Role.RadioButton,
                                ) {
                                    onSetBlockingMode(mode)
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = when (mode) {
                                    FocusBlockingMode.OFF -> "OFF"
                                    FocusBlockingMode.BLOCKLIST -> "BLOCK APPS"
                                    FocusBlockingMode.STRICT -> "STRICT"
                                },
                                color = when {
                                    selected -> Ivory
                                    modeLocked -> MutedLight
                                    else -> Ink
                                },
                                maxLines = 1,
                                softWrap = false,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    letterSpacing = 0.8.sp,
                                ),
                            )
                        }
                    }
                }

                Text(
                    text = when (state.strictFocus.blockingMode) {
                        FocusBlockingMode.OFF ->
                            "Focus Mode is visual only. Nothing is blocked."
                        FocusBlockingMode.BLOCKLIST ->
                            "Only the apps you choose are paused while a focus session runs. Everything else keeps working."
                        FocusBlockingMode.STRICT ->
                            "Full lockdown: leaving Pomodoro is blocked until the session ends."
                    },
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    ),
                )

                if (state.strictFocus.blockingMode == FocusBlockingMode.BLOCKLIST) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Ivory, RoundedCornerShape(16.dp))
                            .border(1.dp, Border, RoundedCornerShape(16.dp))
                            .clickable(
                                enabled = !modeLocked,
                                role = Role.Button,
                            ) { showBlocklistDialog = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BLOCKED APPS",
                                color = Ink,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    letterSpacing = 1.2.sp,
                                ),
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = when (val count = state.blockedAppCount) {
                                    0 -> "Nothing selected yet · choose distractions"
                                    1 -> "1 app paused during focus"
                                    else -> "$count apps paused during focus"
                                },
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "MANAGE",
                            color = if (modeLocked) MutedLight else Forest,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.1.sp,
                            ),
                        )
                    }

                    FocusMenuSwitchRow(
                        title = "Block during breaks",
                        subtitle = "Keep the same apps paused through short and long breaks.",
                        checked = state.strictFocus.blockDuringBreaks,
                        enabled = !modeLocked,
                        onCheckedChange = { onSetBlockDuringBreaks(it) },
                    )
                }

                HorizontalDivider(color = Border, thickness = 1.dp)

                FocusMenuSwitchRow(
                    title = "Advanced Focus",
                    subtitle = if (state.isAdvancedFocusLocked) {
                        "Locked while timer is active. Focus Mode and timer reset stay disabled until the session ends."
                    } else {
                        "Also prevents turning Focus Mode off or resetting the timer once a session starts. Play/Pause stays available."
                    },
                    checked = state.strictFocus.advancedFocusEnabled,
                    enabled = !state.isAdvancedFocusLocked,
                    onCheckedChange = { onToggleAdvancedFocus() },
                )
            }
        }

        if (showBlocklistDialog) {
            AppBlocklistDialog(
                blockedPackages = state.strictFocus.blockedPackages,
                onDismiss = { showBlocklistDialog = false },
                onSave = { packages ->
                    onUpdateBlockedPackages(packages)
                    showBlocklistDialog = false
                },
            )
        }

        // --- Expandable Music / Sounds Pill Menu (no redundant Music switch) ---
        if (musicOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .softRaised(Paper, 24.dp, 7.dp)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AMBIENT FOCUS SOUNDSCAPE",
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 1.5.sp,
                            ),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${state.activeMusicTitle} — ${state.activeMusicSubtitle}",
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MintLight, RoundedCornerShape(999.dp))
                            .clickable(role = Role.Button, onClick = onPreviewAmbientSoundscape)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "PREVIEW",
                            color = Forest,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                letterSpacing = 1.1.sp,
                            ),
                        )
                    }
                }

                Text(
                    text = "DEFAULT & BUILT-IN SOUNDSCAPES",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 1.3.sp,
                    ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SelectableBuiltInSoundscapes.forEach { sound ->
                        val selected = selectedTrack == sound
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) Forest else Parchment,
                                    RoundedCornerShape(999.dp),
                                )
                                .border(
                                    1.dp,
                                    if (selected) Forest else Border,
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable(role = Role.RadioButton) {
                                    onSelectAmbientSoundscape(sound)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = sound.label,
                                color = if (selected) Ivory else Ink,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                            )
                        }
                    }
                    if (state.customMusicUri.isNotBlank()) {
                        val customSelected = selectedTrack == AmbientSoundscape.CUSTOM
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (customSelected) Forest else Parchment,
                                    RoundedCornerShape(999.dp),
                                )
                                .border(
                                    1.dp,
                                    if (customSelected) Forest else Border,
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable(role = Role.RadioButton) {
                                    onSelectAmbientSoundscape(AmbientSoundscape.CUSTOM)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "♪ ${state.customMusicTitle.ifBlank { "Custom track" }}",
                                color = if (customSelected) Ivory else Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (customSelected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                            )
                        }
                    }
                }

                Text(
                    text = "CUSTOM MUSIC TRACK",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        letterSpacing = 1.3.sp,
                    ),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val canPickExternal = !state.isFocusExitBlocked && !state.isAdvancedFocusLocked
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (canPickExternal) 1f else 0.5f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Parchment, RoundedCornerShape(999.dp))
                            .border(1.dp, Border, RoundedCornerShape(999.dp))
                            .clickable(
                                enabled = canPickExternal,
                                role = Role.Button,
                                onClick = onOpenStorageMusicPicker,
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+ Device storage",
                            color = Forest,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .alpha(if (canPickExternal) 1f else 0.5f)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Parchment, RoundedCornerShape(999.dp))
                            .border(1.dp, Border, RoundedCornerShape(999.dp))
                            .clickable(
                                enabled = canPickExternal,
                                role = Role.Button,
                                onClick = onOpenInbuiltMusicPicker,
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "+ Inbuilt tones",
                            color = Forest,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "0",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    )
                    Slider(
                        value = state.ambientVolume,
                        onValueChange = onUpdateAmbientVolume,
                        valueRange = 0.05f..1.0f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Ivory,
                            activeTrackColor = Forest,
                            inactiveTrackColor = StoneLight,
                        ),
                    )
                    Text(
                        text = "${(state.ambientVolume * 100).roundToInt()}",
                        color = Ink,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBlocklistDialog(
    blockedPackages: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember(blockedPackages) { mutableStateOf(blockedPackages) }
    var apps by remember { mutableStateOf<List<BlockableApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { InstalledApps.loadLaunchable(context) }
        loading = false
    }

    val filtered = remember(apps, query) { apps.filter { it.matchesQuery(query) } }
    val suggested = remember(apps) {
        apps.filter {
            it.category == AppCategory.SOCIAL ||
                it.category == AppCategory.VIDEO ||
                it.category == AppCategory.GAME
        }.map { it.packageName }.toSet()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .softRaised(Paper, 28.dp, 16.dp)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "BLOCKED APPS",
                            color = Forest,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.4.sp,
                            ),
                        )
                        Text(
                            text = "Choose your distractions",
                            color = Ink,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 23.sp,
                                lineHeight = 27.sp,
                            ),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close blocked apps",
                        tint = Muted,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(role = Role.Button, onClick = onDismiss),
                    )
                }

                Text(
                    text = "These apps are paused only while a focus session is running. Nothing is blocked while the timer is idle, and your list never leaves this device.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    ),
                )

                BasicTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = Ink,
                        fontSize = 13.sp,
                    ),
                    cursorBrush = SolidColor(Forest),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Ivory, RoundedCornerShape(14.dp))
                        .border(1.dp, Border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search installed apps",
                                    color = Muted,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MintLight, RoundedCornerShape(999.dp))
                                .clickable(role = Role.Button) {
                                    selected = selected + suggested
                                }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "SUGGEST",
                                color = Forest,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    letterSpacing = 1.0.sp,
                                ),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Parchment, RoundedCornerShape(999.dp))
                                .clickable(role = Role.Button) { selected = emptySet() }
                                .padding(horizontal = 11.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "CLEAR",
                                color = Muted,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    letterSpacing = 1.0.sp,
                                ),
                            )
                        }
                    }
                    Text(
                        text = "${selected.size} SELECTED",
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            letterSpacing = 1.0.sp,
                        ),
                    )
                }

                HorizontalDivider(color = Border, thickness = 1.dp)

                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Loading installed apps…",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            )
                        }
                    }

                    filtered.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (apps.isEmpty()) {
                                    "No launchable apps were found on this device."
                                } else {
                                    "No app matches \"$query\"."
                                },
                                color = Muted,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(filtered, key = { it.packageName }) { app ->
                                val checked = app.packageName in selected
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable(role = Role.Checkbox) {
                                            selected = if (checked) {
                                                selected - app.packageName
                                            } else {
                                                selected + app.packageName
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(
                                                if (checked) Forest else Ivory,
                                                RoundedCornerShape(7.dp),
                                            )
                                            .border(
                                                1.dp,
                                                if (checked) Forest else Border,
                                                RoundedCornerShape(7.dp),
                                            ),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (checked) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = Ivory,
                                                modifier = Modifier.size(15.dp),
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.label,
                                            color = Ink,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 13.sp,
                                            ),
                                        )
                                        Text(
                                            text = app.packageName,
                                            color = Muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 9.sp,
                                            ),
                                        )
                                    }
                                    val categoryLabel = app.category.displayLabel()
                                    if (categoryLabel.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = categoryLabel.uppercase(),
                                            color = Muted,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 7.sp,
                                                letterSpacing = 0.8.sp,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SoftButton(
                        label = "Cancel",
                        onClick = onDismiss,
                        variant = SoftButtonVariant.Secondary,
                        modifier = Modifier.weight(0.4f),
                    )
                    SoftButton(
                        label = "Save ${selected.size}",
                        onClick = { onSave(selected) },
                        modifier = Modifier.weight(0.6f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusMenuSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                role = Role.Switch,
            ) {
                onCheckedChange(!checked)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Ink,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                ),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Muted,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                ),
            )
        }
        Spacer(Modifier.width(14.dp))
        CalmSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * Displays the active Subject Tag selector row, custom tag creator/remover, and proportional
 * multi-subject breakdown bar inside the Rhythm & Ledger card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Suppress("OPT_IN_IS_NOT_ENABLED", "UnnecessaryOptInAnnotation")
@Composable
fun SubjectBreakdownSection(
    tags: List<StudySubjectTag>,
    activeSubjectId: String,
    breakdown: List<SubjectAllocationSummary>,
    compact: Boolean,
    onSelectSubject: (String) -> Unit,
    onAddCustomSubject: (String, Long) -> Boolean,
    onRemoveCustomSubject: (String) -> Unit = {},
) {
    var showAddSubjectRow by rememberSaveable { mutableStateOf(false) }
    var customSubjectName by rememberSaveable { mutableStateOf("") }
    var selectedPaletteColor by rememberSaveable { mutableLongStateOf(CustomSubjectPalette[1]) }
    val subjectInputBringIntoView = remember { BringIntoViewRequester() }
    var isSubjectInputFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(isSubjectInputFocused, isImeVisible) {
        if (isSubjectInputFocused && isImeVisible) {
            subjectInputBringIntoView.bringIntoView()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STUDY SUBJECTS",
                color = Muted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (compact) 9.sp else 10.sp,
                    letterSpacing = 1.5.sp,
                ),
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (showAddSubjectRow) Forest else Parchment,
                        RoundedCornerShape(999.dp),
                    )
                    .clickable(role = Role.Button) {
                        showAddSubjectRow = !showAddSubjectRow
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = if (showAddSubjectRow) "CLOSE" else "+ SUBJECT",
                    color = if (showAddSubjectRow) Ivory else Forest,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (compact) 7.sp else 8.sp,
                        letterSpacing = 1.1.sp,
                    ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.forEach { tag ->
                val selected = tag.id == activeSubjectId
                val tagColor = Color(tag.colorHex)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) tagColor else Parchment,
                            RoundedCornerShape(999.dp),
                        )
                        .border(
                            1.dp,
                            if (selected) tagColor else Border,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(role = Role.RadioButton) { onSelectSubject(tag.id) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (selected) Ivory else tagColor, CircleShape),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = tag.name,
                        color = if (selected) Ivory else Ink,
                        maxLines = 1,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = if (compact) 10.sp else 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    )
                    if (tag.isCustom) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Remove custom subject ${tag.name}",
                            tint = if (selected) Ivory.copy(alpha = 0.85f) else Muted,
                            modifier = Modifier
                                .size(12.dp)
                                .clickable { onRemoveCustomSubject(tag.id) },
                        )
                    }
                }
            }
        }

        if (showAddSubjectRow) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(subjectInputBringIntoView)
                    .background(Parchment, RoundedCornerShape(16.dp))
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(Ivory, RoundedCornerShape(999.dp))
                            .border(1.dp, Border, RoundedCornerShape(999.dp))
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        if (customSubjectName.isEmpty()) {
                            Text(
                                text = "New subject name...",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            )
                        }
                        BasicTextField(
                            value = customSubjectName,
                            onValueChange = { customSubjectName = it.take(22) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = Ink,
                                fontSize = 12.sp,
                            ),
                            cursorBrush = SolidColor(Forest),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isSubjectInputFocused = it.isFocused },
                        )
                    }
                    val canAdd = customSubjectName.trim().isNotEmpty()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (canAdd) Forest else StoneLight,
                                RoundedCornerShape(999.dp),
                            )
                            .clickable(enabled = canAdd, role = Role.Button) {
                                if (onAddCustomSubject(customSubjectName, selectedPaletteColor)) {
                                    customSubjectName = ""
                                    showAddSubjectRow = false
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = "ADD",
                            color = if (canAdd) Ivory else MutedLight,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.1.sp,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CustomSubjectPalette.forEach { hex ->
                        val isPicked = hex == selectedPaletteColor
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color(hex), CircleShape)
                                .then(
                                    if (isPicked) {
                                        Modifier.border(2.dp, Ink, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { selectedPaletteColor = hex },
                        )
                    }
                }
            }
        }

        if (breakdown.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
            ) {
                var startX = 0f
                breakdown.forEach { item ->
                    val segWidth = (size.width * item.shareFraction).coerceAtLeast(2f)
                    drawRect(
                        color = Color(item.tag.colorHex),
                        topLeft = Offset(startX, 0f),
                        size = Size(segWidth, size.height),
                    )
                    startX += segWidth
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                breakdown.take(6).forEach { item ->
                    val pct = (item.shareFraction * 100f).roundToInt().coerceIn(1, 100)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Color(item.tag.colorHex), CircleShape),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "${item.tag.name} ${item.focusedMinutes}m ($pct%)",
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                letterSpacing = 0.6.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Today's Task List card placed below the Heatmap/Subjects card.
 * Refreshes automatically at 12:00 AM midnight, saves every day's tasks into the ledger,
 * and displays Incomplete and Completed tasks separately in the CALM / SYSTEM style.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
@Suppress("UNUSED_PARAMETER", "OPT_IN_IS_NOT_ENABLED", "UnnecessaryOptInAnnotation")
fun StudyTasksAndFocusStudioCard(
    state: PomodoroUiState,
    compact: Boolean = false,
    onAddTask: (String, Int) -> Boolean,
    onSelectActiveTask: (String?) -> Unit,
    onToggleTaskCompleted: (String) -> Unit,
    onDeleteTask: (String) -> Unit,
    onClearCompletedTasks: () -> Unit,
    onSelectAmbientSoundscape: (AmbientSoundscape) -> Unit = {},
    onUpdateAmbientVolume: (Float) -> Unit = {},
    onPreviewAmbientSoundscape: () -> Unit = {},
    onUpdateStrictFocus: (StrictFocusSettings) -> Unit = {},
    onToggleAutoFocus: (Boolean) -> Unit = {},
    onToggleAdvancedFocus: () -> Unit = {},
    onConfirmAdvancedFocus: () -> Unit = {},
    onDismissAdvancedFocus: () -> Unit = {},
    onRequestPermission: (FocusPermissionType) -> Unit = {},
    onDismissDeclinedPermission: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var newTaskTitle by rememberSaveable { mutableStateOf("") }
    var estimatedPomodoros by rememberSaveable { mutableIntStateOf(2) }
    var showTaskInput by rememberSaveable { mutableStateOf(false) }
    val taskInputBringIntoView = remember { BringIntoViewRequester() }
    var isTaskInputFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    LaunchedEffect(isTaskInputFocused, isImeVisible) {
        if (isTaskInputFocused && isImeVisible) {
            taskInputBringIntoView.bringIntoView()
        }
    }

    val incompleteTasks = state.incompleteTasks
    val completedTasks = state.completedTasks

    Column(
        modifier = modifier
            .fillMaxWidth()
            .softRaised(Paper, if (compact) 24.dp else 26.dp, if (compact) 6.dp else 8.dp)
            .padding(if (compact) 16.dp else 22.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TODAY'S TASKS · RESETS 12:00 AM",
                    color = Muted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (compact) 9.sp else 10.sp,
                        letterSpacing = 1.5.sp,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                val activeTask = state.activeTask
                Text(
                    text = if (activeTask != null) {
                        "Focusing on: ${activeTask.title} (${activeTask.completedPomodoros}/${activeTask.estimatedPomodoros})"
                    } else {
                        "Plan today's sessions. Saved automatically to today's ledger."
                    },
                    color = if (activeTask != null) Forest else Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    ),
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (completedTasks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Parchment, RoundedCornerShape(999.dp))
                            .clickable(role = Role.Button, onClick = onClearCompletedTasks)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "CLEAR DONE",
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                letterSpacing = 1.0.sp,
                            ),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (showTaskInput) Forest else MintLight,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable(role = Role.Button) {
                            showTaskInput = !showTaskInput
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = if (showTaskInput) "CLOSE" else "+ ADD TASK",
                        color = if (showTaskInput) Ivory else Forest,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 8.sp,
                            letterSpacing = 1.1.sp,
                        ),
                    )
                }
            }
        }

        if (showTaskInput) {
            Spacer(Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(taskInputBringIntoView)
                    .background(Parchment, RoundedCornerShape(18.dp))
                    .border(1.dp, Border, RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .softRaised(Ivory, 999.dp, 2.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (newTaskTitle.isEmpty()) {
                            Text(
                                text = "What are you studying today?",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            )
                        }
                        BasicTextField(
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it.take(60) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                color = Ink,
                                fontSize = 13.sp,
                            ),
                            cursorBrush = SolidColor(Forest),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isTaskInputFocused = it.isFocused },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            text = "SESSIONS:",
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                letterSpacing = 1.1.sp,
                            ),
                        )
                        listOf(1, 2, 3, 4, 6).forEach { count ->
                            val selected = estimatedPomodoros == count
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (selected) Forest else Ivory,
                                        RoundedCornerShape(999.dp),
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) Forest else Border,
                                        RoundedCornerShape(999.dp),
                                    )
                                    .clickable { estimatedPomodoros = count }
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "$count",
                                    color = if (selected) Ivory else Ink,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                )
                            }
                        }
                    }
                    val canSaveTask = newTaskTitle.trim().isNotEmpty()
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (canSaveTask) Forest else StoneLight,
                                RoundedCornerShape(999.dp),
                            )
                            .clickable(enabled = canSaveTask, role = Role.Button) {
                                if (onAddTask(newTaskTitle, estimatedPomodoros)) {
                                    newTaskTitle = ""
                                    showTaskInput = false
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    ) {
                        Text(
                            text = "SAVE TASK",
                            color = if (canSaveTask) Ivory else MutedLight,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.1.sp,
                            ),
                        )
                    }
                }
            }
        }

        if (incompleteTasks.isEmpty() && completedTasks.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Parchment.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No tasks yet for today. Tap + ADD TASK to plan your study blocks.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                )
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "INCOMPLETE (${incompleteTasks.size})",
                color = Forest,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 1.3.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            if (incompleteTasks.isEmpty()) {
                Text(
                    text = "All tasks completed for today.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    incompleteTasks.forEach { task ->
                        val isActive = state.activeTask?.id == task.id
                        TaskItemRow(
                            task = task,
                            isActive = isActive,
                            onSelect = { onSelectActiveTask(task.id) },
                            onToggleDone = { onToggleTaskCompleted(task.id) },
                            onDelete = { onDeleteTask(task.id) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Border, thickness = 1.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "COMPLETED (${completedTasks.size})",
                color = Muted,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    letterSpacing = 1.3.sp,
                ),
            )
            Spacer(Modifier.height(6.dp))
            if (completedTasks.isEmpty()) {
                Text(
                    text = "Completed tasks will appear here.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    completedTasks.forEach { task ->
                        TaskItemRow(
                            task = task,
                            isActive = false,
                            onSelect = {},
                            onToggleDone = { onToggleTaskCompleted(task.id) },
                            onDelete = { onDeleteTask(task.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Global permission pre-prompt modal styled in the CALM / SYSTEM Notification aesthetic.
 */
@Composable
fun PermissionExplanationDialog(
    prompt: PermissionPromptState,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isAccessibilityDisclosure = prompt.permissionType == FocusPermissionType.ACCESSIBILITY_LOCK
    // The accessibility disclosure is long. Cap the card to the window and let it scroll,
    // otherwise "Accept & Continue" / "Decline" fall outside a compact-landscape or
    // large-font window and the user can neither grant nor dismiss the permission.
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.9f)
        .coerceAtLeast(240.dp)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !isAccessibilityDisclosure,
            dismissOnClickOutside = !isAccessibilityDisclosure,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxDialogHeight)
                    .softRaised(Paper, 28.dp, 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Mint, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Forest,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAccessibilityDisclosure) {
                                "PROMINENT DISCLOSURE · ACCESSIBILITY API"
                            } else {
                                "PERMISSION REQUIRED"
                            },
                            color = if (isAccessibilityDisclosure) Forest else Muted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.4.sp,
                            ),
                        )
                        Text(
                            text = prompt.title,
                            color = Ink,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 24.sp,
                                lineHeight = 28.sp,
                            ),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close permission prompt",
                        tint = Muted,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(role = Role.Button, onClick = onDismiss),
                    )
                }

                HorizontalDivider(color = Border, thickness = 1.dp)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isAccessibilityDisclosure) {
                            "DATA ACCESSED, USAGE & ZERO SHARING GUARANTEE"
                        } else {
                            "WHY THIS IS NEEDED"
                        },
                        color = Forest,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.3.sp,
                        ),
                    )
                    Text(
                        text = prompt.whyNeeded,
                        color = Ink,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        ),
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Parchment, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "WHAT TO DO",
                        color = Forest,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.3.sp,
                        ),
                    )
                    Text(
                        text = prompt.whatToDo,
                        color = Ink,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SoftButton(
                        label = if (isAccessibilityDisclosure) "Decline" else "Back",
                        onClick = onDismiss,
                        variant = SoftButtonVariant.Secondary,
                        modifier = Modifier.weight(0.42f),
                    )
                    SoftButton(
                        label = prompt.confirmLabel,
                        onClick = onConfirm,
                        trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                        variant = SoftButtonVariant.Primary,
                        modifier = Modifier.weight(0.58f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskItemRow(
    task: StudyTask,
    isActive: Boolean,
    onSelect: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) MintLight else Parchment, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (isActive) Forest.copy(alpha = 0.45f) else Border,
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = !task.isCompleted, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalmCheckbox(
            checked = task.isCompleted,
            onCheckedChange = onToggleDone,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = task.title,
            color = if (task.isCompleted) Muted else Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${task.completedPomodoros}/${task.estimatedPomodoros}",
            color = if (isActive) Forest else Muted,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                letterSpacing = 0.9.sp,
            ),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.Close,
            contentDescription = "Delete task",
            tint = Muted,
            modifier = Modifier
                .size(15.dp)
                .clickable(role = Role.Button, onClick = onDelete),
        )
    }
}

/**
 * Clear warning modal displayed when the user presses Start on a timer while Advanced Focus
 * is enabled, warning them that Focus Mode and timer reset stay locked until the session
 * ends. Leaving the app is only blocked in Strict lock; Block apps mode pauses just the
 * chosen apps.
 */
@Composable
fun AdvancedFocusLockWarningDialog(
    phaseLabel: String,
    durationMinutes: Int,
    onConfirmStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .softRaised(Paper, 28.dp, 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Mint, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Forest,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ADVANCED FOCUS LOCKDOWN",
                            color = Forest,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                letterSpacing = 1.4.sp,
                            ),
                        )
                        Text(
                            text = "Start locked timer?",
                            color = Ink,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 24.sp,
                                lineHeight = 28.sp,
                            ),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Cancel locked timer start",
                        tint = Muted,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(role = Role.Button, onClick = onDismiss),
                    )
                }

                HorizontalDivider(color = Border, thickness = 1.dp)

                Text(
                    text = "Advanced Focus is on for this $durationMinutes-minute $phaseLabel session. Once the timer starts:",
                    color = Ink,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Parchment, RoundedCornerShape(16.dp))
                        .border(1.dp, Border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "WHAT LOCKS UNTIL 00:00",
                        color = Forest,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.3.sp,
                        ),
                    )
                    Text(
                        text = "• You cannot turn off Focus Mode or Advanced Focus.\n" +
                            "• You cannot reset the timer or switch phases until the countdown finishes (Play/Pause stays available).\n" +
                            "• In Strict lock, leaving Pomodoro is blocked too. In Block apps mode only the apps you chose are paused.",
                        color = Ink,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        ),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SoftButton(
                        label = "Cancel",
                        onClick = onDismiss,
                        variant = SoftButtonVariant.Secondary,
                        modifier = Modifier.weight(0.42f),
                    )
                    SoftButton(
                        label = "Start & Lock",
                        onClick = onConfirmStart,
                        trailingIcon = Icons.AutoMirrored.Rounded.ArrowForward,
                        variant = SoftButtonVariant.Primary,
                        modifier = Modifier.weight(0.58f),
                    )
                }
            }
        }
    }
}
