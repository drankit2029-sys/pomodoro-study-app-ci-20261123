package com.studyapp.pomodoro.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.studyapp.pomodoro.AppThemeMode

@Composable
fun PomodoroTheme(
    themeMode: AppThemeMode = AppThemeMode.SANCTUARY,
    content: @Composable () -> Unit,
) {
    SyncPomodoroPalette(themeMode)
    val dynamicScheme = lightColorScheme(
        primary = Forest,
        onPrimary = Ivory,
        primaryContainer = MintLight,
        onPrimaryContainer = Forest,
        secondary = Mint,
        onSecondary = Ink,
        secondaryContainer = MintLight,
        onSecondaryContainer = Forest,
        background = Ivory,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = Parchment,
        onSurfaceVariant = Muted,
        outline = Border,
        error = Error,
    )
    MaterialTheme(
        colorScheme = dynamicScheme,
        typography = PomodoroTypography,
        content = content,
    )
}
