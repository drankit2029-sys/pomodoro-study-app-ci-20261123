package com.studyapp.pomodoro.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.studyapp.pomodoro.AppThemeMode
import com.studyapp.pomodoro.ui.theme.Forest
import com.studyapp.pomodoro.ui.theme.Mint
import com.studyapp.pomodoro.ui.theme.StoneLight
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ProgressRing(
    progress: Float,
    size: Dp,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 8.dp,
    trackColor: Color = StoneLight,
    progressColor: Color = Forest,
    endDotColor: Color = Mint,
    themeMode: AppThemeMode = AppThemeMode.SANCTUARY,
    isLocked: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val focusMorph by animateFloatAsState(
        targetValue = if (themeMode != AppThemeMode.SANCTUARY) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "ringFocusMorph",
    )
    val advancedMorph by animateFloatAsState(
        targetValue = if (themeMode == AppThemeMode.ADVANCED_FOCUS) 1f else 0f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "ringAdvancedMorph",
    )
    val lockMorph by animateFloatAsState(
        targetValue = if (isLocked) 1f else 0f,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "ringLockMorph",
    )

    // The pulse and orbit are only visible inside the Focus / Advanced Focus themes. In
    // Sanctuary they painted nothing but still requested a frame every 16 ms, keeping the
    // whole tree busy (and Compose tests from ever going idle).
    val animateDecorations = themeMode != AppThemeMode.SANCTUARY
    val pulse: Float
    val orbitAngle: Float
    if (animateDecorations) {
        val infiniteTransition = rememberInfiniteTransition(label = "ringThemeAnim")
        pulse = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "ringPulse",
        ).value
        orbitAngle = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 20_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "ringOrbitAngle",
        ).value
    } else {
        pulse = 0f
        orbitAngle = 0f
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val dotRadius = stroke * 0.62f
            val maxInset = maxOf(stroke / 2f, dotRadius) + 5.dp.toPx()
            val diameter = (this.size.minDimension - maxInset * 2f).coerceAtLeast(0f)
            val topLeft = Offset(
                x = (this.size.width - diameter) / 2f,
                y = (this.size.height - diameter) / 2f,
            )
            val arcSize = Size(diameter, diameter)

            // 1. Focus Mode breathing halo morphing smoothly out of the existing ring track
            if (focusMorph > 0.01f) {
                val auraSpread = focusMorph * (2.0f + pulse * 2.4f).dp.toPx()
                drawArc(
                    color = endDotColor.copy(alpha = focusMorph * (0.20f + pulse * 0.16f)),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(topLeft.x - auraSpread, topLeft.y - auraSpread),
                    size = Size(diameter + auraSpread * 2f, diameter + auraSpread * 2f),
                    style = Stroke(
                        width = stroke * (0.35f + focusMorph * 0.30f + pulse * 0.15f),
                        cap = StrokeCap.Round,
                    ),
                )
            }

            // 2. Advanced Focus orbital sanctuary arcs morphing smoothly out of the ring
            if (advancedMorph > 0.01f) {
                val outerSpread = advancedMorph * (3.6f + pulse * 1.6f).dp.toPx()
                drawArc(
                    color = progressColor.copy(
                        alpha = advancedMorph * (0.14f + lockMorph * 0.10f + pulse * 0.08f),
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(topLeft.x - outerSpread, topLeft.y - outerSpread),
                    size = Size(diameter + outerSpread * 2f, diameter + outerSpread * 2f),
                    style = Stroke(width = (1.2f * advancedMorph).dp.toPx(), cap = StrokeCap.Round),
                )
                val segmentCount = 6
                val stepAngle = 360f / segmentCount
                val segSweep = advancedMorph * (16f + lockMorph * 10f)
                for (i in 0 until segmentCount) {
                    drawArc(
                        color = progressColor.copy(alpha = advancedMorph * (0.32f + lockMorph * 0.16f)),
                        startAngle = orbitAngle + i * stepAngle,
                        sweepAngle = segSweep,
                        useCenter = false,
                        topLeft = Offset(topLeft.x - outerSpread, topLeft.y - outerSpread),
                        size = Size(diameter + outerSpread * 2f, diameter + outerSpread * 2f),
                        style = Stroke(width = (2.0f * advancedMorph).dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            val clamped = progress.coerceIn(0f, 1f)
            val sweep = 360f * clamped
            if (sweep > 0f) {
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (clamped < 0.995f) {
                    val radius = diameter / 2f
                    val angle = Math.toRadians((-90f + sweep).toDouble())
                    val dotCenter = Offset(
                        x = center.x + (cos(angle) * radius).toFloat(),
                        y = center.y + (sin(angle) * radius).toFloat(),
                    )
                    if (focusMorph > 0.01f) {
                        drawCircle(
                            color = endDotColor.copy(alpha = focusMorph * (0.28f + pulse * 0.24f)),
                            radius = dotRadius * (1f + focusMorph * (0.45f + pulse * 0.40f)),
                            center = dotCenter,
                        )
                    }
                    drawCircle(
                        color = endDotColor,
                        radius = dotRadius,
                        center = dotCenter,
                    )
                }
            }
        }
        content()
    }
}
