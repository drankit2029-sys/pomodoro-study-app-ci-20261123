package com.studyapp.pomodoro

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studyapp.pomodoro.ui.components.SoftButton
import com.studyapp.pomodoro.ui.components.SoftButtonVariant
import com.studyapp.pomodoro.ui.theme.Border
import com.studyapp.pomodoro.ui.theme.Forest
import com.studyapp.pomodoro.ui.theme.Ink
import com.studyapp.pomodoro.ui.theme.Ivory
import com.studyapp.pomodoro.ui.theme.Mint
import com.studyapp.pomodoro.ui.theme.Muted
import com.studyapp.pomodoro.ui.theme.Paper
import com.studyapp.pomodoro.ui.theme.PomodoroTheme
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shown when the user opens one of their blocked apps during a focus session.
 *
 * Deliberately calm: it names the app, shows how much of the session is left, and offers a
 * clear way back. It is not a punishment screen, and it is never a dead end — there is one
 * 60-second pass per session and a hold-to-confirm exit.
 */
class BlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val blockedPackage = intent?.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
        val appLabel = resolveAppLabel(blockedPackage)

        setContent {
            val engine = PomodoroTimerEngine.getInstance(this)
            val state by engine.state.collectAsStateWithLifecycle()
            // If the session ends (or Focus Mode is turned off) while this screen is open,
            // get out of the way instead of showing a stale lock screen.
            LaunchedEffect(state.isAppBlockingActive) {
                if (!state.isAppBlockingActive) finish()
            }
            PomodoroTheme(themeMode = state.themeMode) {
                BlockedScreen(
                    appLabel = appLabel,
                    state = state,
                    onBackToFocus = {
                        openTimer()
                        finish()
                    },
                    onEndSession = {
                        engine.forceReset()
                        openTimer()
                        finish()
                    },
                    onEmergencyPass = {
                        engine.useEmergencyPass()
                        finish()
                    },
                )
            }
        }
    }

    private fun openTimer() {
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

    private fun resolveAppLabel(packageName: String): String {
        if (packageName.isBlank()) return "This app"
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
    }
}

@Composable
private fun BlockedScreen(
    appLabel: String,
    state: PomodoroUiState,
    onBackToFocus: () -> Unit,
    onEndSession: () -> Unit,
    onEmergencyPass: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ivory)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Mint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = Forest,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "FOCUS PROTECTION",
                color = Forest,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 1.6.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$appLabel is paused",
                color = Ink,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 26.sp,
                    lineHeight = 31.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "You chose to keep this out of your study session. Everything else on your phone still works.",
                color = Muted,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                ),
            )

            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Paper, RoundedCornerShape(24.dp))
                    .border(1.dp, Border, RoundedCornerShape(24.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = Forest,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.isFlowOvertime) "FLOW OVERTIME" else "TIME LEFT",
                        color = Muted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 1.4.sp,
                        ),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.displayClock,
                    color = Ink,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = 46.sp,
                        lineHeight = 50.sp,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.sessionLabel,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                )
            }

            Spacer(Modifier.height(20.dp))
            SoftButton(
                label = "Back to focus",
                onClick = onBackToFocus,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            if (state.emergencyPassUsed) {
                Text(
                    text = "60-second pass already used this session.",
                    color = Muted,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                )
            } else {
                SoftButton(
                    label = "Use a 60-second pass",
                    onClick = onEmergencyPass,
                    variant = SoftButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(14.dp))
            HoldToEndSession(onEnd = onEndSession)
        }
    }
}

/**
 * A plain button is too easy to hit in frustration, so ending the session requires a
 * deliberate three-second hold. This is the only way out of the session from this screen.
 */
@Composable
private fun HoldToEndSession(onEnd: () -> Unit) {
    var holding by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(27.dp))
            .border(1.dp, Border, RoundedCornerShape(27.dp))
            .pointerInput(onEnd) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    holding = true
                    var cancelled = false
                    val released = withTimeoutOrNull(HOLD_TO_END_MILLIS) {
                        val change = waitForUpOrCancellation()
                        if (change == null) cancelled = true
                        change
                    }
                    holding = false
                    // Timeout while still holding confirms the hold; a cancelled gesture
                    // (system back gesture, window losing focus) must never end the session.
                    if (released == null && !cancelled) {
                        onEnd()
                    }
                }
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (holding) "Keep holding to end…" else "Hold to end session",
            color = Muted,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                letterSpacing = 1.2.sp,
            ),
        )
    }
}

private const val HOLD_TO_END_MILLIS = 2_800L
