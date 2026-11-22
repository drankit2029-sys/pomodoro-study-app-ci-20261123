package com.studyapp.pomodoro.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.studyapp.pomodoro.AppThemeMode
import com.studyapp.pomodoro.SessionsPerCycle
import com.studyapp.pomodoro.ui.theme.Forest
import com.studyapp.pomodoro.ui.theme.Mint

@Composable
fun BotanicalBranch(
    modifier: Modifier = Modifier,
    color: Color = Forest,
    filledSessions: Int = 0,
    isFlowOvertime: Boolean = false,
    themeMode: AppThemeMode = AppThemeMode.SANCTUARY,
    isAnimating: Boolean = false,
) {
    val focusMorph by animateFloatAsState(
        targetValue = if (themeMode != AppThemeMode.SANCTUARY) 1f else 0f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "branchFocusMorph",
    )
    val advancedMorph by animateFloatAsState(
        targetValue = if (themeMode == AppThemeMode.ADVANCED_FOCUS) 1f else 0f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "branchAdvancedMorph",
    )

    val baseVitality = when {
        isFlowOvertime -> 0.22f
        filledSessions > 0 -> (filledSessions.coerceAtMost(SessionsPerCycle) * 0.045f)
        else -> 0f
    }
    val vitalityBoost = (baseVitality + focusMorph * 0.14f + advancedMorph * 0.08f).coerceAtMost(0.32f)

    // Swaying a decorative branch at 60 fps while nothing is happening burns battery for
    // no visible benefit; animate only during a session or a focus theme.
    val swayPhase: Float
    val glowPulse: Float
    if (isAnimating) {
        val infiniteTransition = rememberInfiniteTransition(label = "botanicalSway")
        swayPhase = infiniteTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "swayAngle",
        ).value
        glowPulse = infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "glowPulse",
        ).value
    } else {
        swayPhase = 0f
        glowPulse = 0.35f
    }

    val swayAmplitude = 1.2f + focusMorph * 2.4f + advancedMorph * 1.4f
    val swayDegrees = swayPhase * swayAmplitude
    val leafScale = 1f + focusMorph * 0.10f + advancedMorph * 0.06f

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stem = Path().apply {
            moveTo(w * 0.98f, h * 0.02f)
            cubicTo(w * 0.84f, h * 0.06f, w * 0.72f, h * 0.18f, w * 0.64f, h * 0.32f)
            cubicTo(w * 0.56f, h * 0.46f, w * 0.42f, h * 0.55f, w * 0.27f, h * 0.60f)
            cubicTo(w * 0.18f, h * 0.63f, w * 0.10f, h * 0.64f, w * 0.03f, h * 0.62f)
        }
        drawPath(
            path = stem,
            color = color.copy(alpha = (0.42f + vitalityBoost).coerceAtMost(0.78f)),
            style = Stroke(width = (1.2f + focusMorph * 0.25f).dp.toPx(), cap = StrokeCap.Round),
        )

        // Secondary tendril unfurling smoothly out of the main stem when Focus Mode is active
        if (focusMorph > 0.01f) {
            val tendrilEndX = lerp(w * 0.64f, w * 0.26f, focusMorph)
            val tendrilEndY = lerp(h * 0.32f, h * 0.84f, focusMorph)
            val tendril = Path().apply {
                moveTo(w * 0.64f, h * 0.32f)
                cubicTo(
                    lerp(w * 0.64f, w * 0.58f, focusMorph),
                    lerp(h * 0.32f, h * 0.54f, focusMorph),
                    lerp(w * 0.64f, w * 0.44f, focusMorph),
                    lerp(h * 0.32f, h * 0.72f, focusMorph),
                    tendrilEndX,
                    tendrilEndY,
                )
            }
            drawPath(
                path = tendril,
                color = color.copy(alpha = 0.36f * focusMorph),
                style = Stroke(width = 1.0.dp.toPx(), cap = StrokeCap.Round),
            )
            drawLeaf(
                Offset(
                    lerp(w * 0.64f, w * 0.50f, focusMorph),
                    lerp(h * 0.32f, h * 0.56f, focusMorph),
                ),
                18f + swayDegrees,
                w * 0.15f * focusMorph,
                w * 0.052f * focusMorph,
                Mint.copy(alpha = 0.65f * focusMorph),
            )
            drawLeaf(
                Offset(
                    lerp(w * 0.64f, w * 0.36f, focusMorph),
                    lerp(h * 0.32f, h * 0.74f, focusMorph),
                ),
                -24f - swayDegrees,
                w * 0.14f * focusMorph,
                w * 0.048f * focusMorph,
                color.copy(alpha = 0.34f * focusMorph),
            )
        }

        drawLeaf(
            Offset(w * 0.87f, h * 0.10f),
            -38f + swayDegrees,
            w * 0.21f * leafScale,
            w * 0.075f * leafScale,
            color.copy(alpha = (0.24f + vitalityBoost).coerceAtMost(0.62f)),
        )
        drawLeaf(
            Offset(w * 0.78f, h * 0.16f),
            -72f - swayDegrees * 0.8f,
            w * 0.18f * leafScale,
            w * 0.065f * leafScale,
            Mint.copy(alpha = (0.58f + vitalityBoost).coerceAtMost(0.92f)),
        )
        drawLeaf(
            Offset(w * 0.72f, h * 0.24f),
            -12f + swayDegrees * 0.9f,
            w * 0.19f * leafScale,
            w * 0.070f * leafScale,
            color.copy(alpha = (0.22f + vitalityBoost).coerceAtMost(0.60f)),
        )
        drawLeaf(
            Offset(w * 0.66f, h * 0.31f),
            -60f - swayDegrees,
            w * 0.17f * leafScale,
            w * 0.060f * leafScale,
            Mint.copy(alpha = (0.55f + vitalityBoost).coerceAtMost(0.90f)),
        )
        drawLeaf(
            Offset(w * 0.59f, h * 0.40f),
            24f + swayDegrees * 1.1f,
            w * 0.18f * leafScale,
            w * 0.065f * leafScale,
            color.copy(alpha = (0.20f + vitalityBoost).coerceAtMost(0.58f)),
        )
        drawLeaf(
            Offset(w * 0.51f, h * 0.48f),
            -44f - swayDegrees * 0.9f,
            w * 0.16f * leafScale,
            w * 0.058f * leafScale,
            Mint.copy(alpha = (0.52f + vitalityBoost).coerceAtMost(0.88f)),
        )
        drawLeaf(
            Offset(w * 0.41f, h * 0.54f),
            32f + swayDegrees,
            w * 0.17f * leafScale,
            w * 0.060f * leafScale,
            color.copy(alpha = (0.18f + vitalityBoost).coerceAtMost(0.55f)),
        )
        drawLeaf(
            Offset(w * 0.31f, h * 0.58f),
            -28f - swayDegrees * 1.1f,
            w * 0.15f * leafScale,
            w * 0.055f * leafScale,
            Mint.copy(alpha = (0.48f + vitalityBoost).coerceAtMost(0.85f)),
        )
        drawLeaf(
            Offset(w * 0.21f, h * 0.61f),
            38f + swayDegrees * 0.8f,
            w * 0.14f * leafScale,
            w * 0.052f * leafScale,
            color.copy(alpha = (0.16f + vitalityBoost).coerceAtMost(0.52f)),
        )

        // Dew buds that smoothly bloom out of the branch along focusMorph & advancedMorph
        if (focusMorph > 0.01f) {
            val budStrength = (focusMorph * 0.65f + advancedMorph * 0.35f).coerceIn(0f, 1f)
            val budRadius = (2.8f * budStrength).dp.toPx()
            val budLocations = listOf(
                Offset(w * 0.74f, h * 0.12f),
                Offset(w * 0.55f, h * 0.34f),
                Offset(w * 0.35f, h * 0.50f),
                Offset(w * 0.14f, h * 0.60f),
            )
            budLocations.forEachIndexed { idx, pos ->
                val phaseAlpha = (((glowPulse + (idx * 0.15f)) % 1f).coerceIn(0.28f, 0.85f)) * budStrength
                drawCircle(
                    color = Mint.copy(alpha = phaseAlpha * 0.55f),
                    radius = budRadius * (1.5f + advancedMorph * 0.45f),
                    center = pos,
                )
                drawCircle(
                    color = color.copy(alpha = phaseAlpha),
                    radius = budRadius,
                    center = pos,
                )
            }
        }
    }
}

private fun DrawScope.drawLeaf(
    center: Offset,
    angle: Float,
    length: Float,
    width: Float,
    color: Color,
) {
    if (length <= 0.5f || width <= 0.5f) return
    rotate(degrees = angle, pivot = center) {
        drawOval(
            color = color,
            topLeft = Offset(center.x - length / 2f, center.y - width / 2f),
            size = Size(length, width),
        )
        drawLine(
            color = color.copy(alpha = (color.alpha * 0.75f).coerceAtMost(1f)),
            start = Offset(center.x - length * 0.42f, center.y),
            end = Offset(center.x + length * 0.42f, center.y),
            strokeWidth = 0.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
