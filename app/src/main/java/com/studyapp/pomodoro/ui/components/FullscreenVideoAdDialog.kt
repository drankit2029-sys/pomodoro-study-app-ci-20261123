package com.studyapp.pomodoro.ui.components

import androidx.compose.runtime.Composable
import com.studyapp.pomodoro.PomodoroUiState

/**
 * Fallback ad dialogs are disabled by policy ("No fallbacks for ads").
 * Kept as an empty no-op composable solely for binary/source signature compatibility.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun FullscreenVideoAdDialog(
    state: PomodoroUiState,
    onDismissAd: () -> Unit,
) = Unit
