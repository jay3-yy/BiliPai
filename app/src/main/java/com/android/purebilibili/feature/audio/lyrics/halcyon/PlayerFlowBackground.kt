package com.android.purebilibili.feature.audio.lyrics.halcyon

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Ported Halcyon flow background accents (soft drifting palette blobs).
 * Used as an optional layer under immersive lyrics chrome.
 */
@Composable
internal fun PlayerFlowBackground(
    palette: PlayerPalette,
    animate: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    val driftState = if (animate && LocalPlayerSurfaceActive.current && animationGate.active) {
        rememberInfiniteTransition(label = "player_flow_background").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 14_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "player_flow_background_drift",
        )
    } else {
        null
    }
    Canvas(modifier = modifier.fillMaxSize().background(palette.middle).then(animationGate.modifier)) {
        val drift = driftState?.value ?: 0.3f
        val w = size.width
        val h = size.height
        val t = drift * Math.PI.toFloat() * 2f
        drawRect(
            brush = Brush.verticalGradient(
                listOf(palette.top, palette.middle, palette.bottom),
            ),
        )
        val blobs = listOf(
            palette.accent.copy(alpha = 0.18f) to Offset(
                (0.25f + 0.12f * kotlin.math.sin(t)) * w,
                (0.30f + 0.10f * kotlin.math.cos(t)) * h,
            ),
            palette.top.copy(alpha = 0.16f) to Offset(
                (0.78f + 0.10f * kotlin.math.cos(t * 1.3f)) * w,
                (0.55f + 0.12f * kotlin.math.sin(t * 0.8f)) * h,
            ),
        )
        blobs.forEach { (color, center) ->
            val radius = minOf(w, h) * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color, Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}
