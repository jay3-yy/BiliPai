package com.android.purebilibili.feature.audio.screen

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun MusicWavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    wavy: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color,
    hapticStep: Float? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.PLAYER_SEEK)
    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val resolvedHapticStep = hapticStep?.takeIf { it > 0f } ?: rangeSpan / 20f
    fun hapticBucket(progress: Float): Int =
        ((progress - valueRange.start) / resolvedHapticStep).toInt().coerceAtLeast(0)
    var lastHapticBucket by remember(valueRange, resolvedHapticStep) {
        mutableIntStateOf(hapticBucket(value))
    }
    var lastHapticTimeMs by remember { mutableLongStateOf(0L) }
    fun changeValue(nextValue: Float, isDrag: Boolean) {
        if (isDrag) {
            val nextBucket = hapticBucket(nextValue)
            val nowMs = SystemClock.elapsedRealtime()
            if (nextBucket != lastHapticBucket && nowMs - lastHapticTimeMs >= 55L) {
                haptic(HapticType.SELECTION)
                lastHapticTimeMs = nowMs
                lastHapticBucket = nextBucket
            } else if (nextBucket != lastHapticBucket) {
                lastHapticBucket = nextBucket
            }
        } else if (nextValue != value) {
            haptic(HapticType.LIGHT)
            lastHapticTimeMs = SystemClock.elapsedRealtime()
            lastHapticBucket = hapticBucket(nextValue)
        }
        onValueChange(nextValue)
    }
    val animationGate = if (wavy) {
        com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
    } else null
    val amplitudePx = animateFloatAsState(
        targetValue = if (wavy) with(density) { MUSIC_WAVY_AMPLITUDE_DP.dp.toPx() } else 0f,
        label = "music-wavy-amplitude"
    )
    val animatedPhase = if (animationGate?.active == true) {
        rememberInfiniteTransition(label = "music-wavy-phase").animateFloat(
            initialValue = 0f,
            targetValue = (2f * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "music-wavy-phase-value"
        )
    } else null
    val wavelengthPx = with(density) { MUSIC_WAVY_WAVELENGTH_DP.dp.toPx() }
    val strokePx = with(density) { MUSIC_WAVY_STROKE_DP.dp.toPx() }
    val thumbRadiusPx = with(density) { MUSIC_WAVY_THUMB_DP.dp.toPx() / 2f }
    // 波形 Path 跨帧复用(每帧 reset 重填),避免播放/拖动期间逐帧分配。
    val activePathScratch = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .then(animationGate?.modifier ?: Modifier)
            .semantics {
                setProgress {
                    changeValue(
                        resolveMusicProgressValue(it, valueRange.start, valueRange.endInclusive),
                        isDrag = false,
                    )
                    onValueChangeFinished()
                    true
                }
                if (valueRange.endInclusive <= valueRange.start) disabled()
            }
            .pointerInput(valueRange) {
                detectTapGestures { offset ->
                    val fraction = if (size.width <= 0) 0f else (offset.x / size.width).coerceIn(0f, 1f)
                    changeValue(
                        resolveMusicProgressValue(fraction, valueRange.start, valueRange.endInclusive),
                        isDrag = false,
                    )
                    onValueChangeFinished()
                }
            }
            .pointerInput(valueRange) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        haptic(HapticType.LIGHT)
                        lastHapticTimeMs = SystemClock.elapsedRealtime()
                    },
                    onDragEnd = onValueChangeFinished,
                    onDragCancel = onValueChangeFinished
                ) { change, _ ->
                    val fraction = if (size.width <= 0) 0f else (change.position.x / size.width).coerceIn(0f, 1f)
                    changeValue(
                        resolveMusicProgressValue(fraction, valueRange.start, valueRange.endInclusive),
                        isDrag = true,
                    )
                }
            }
    ) {
        val phase = animatedPhase?.value ?: 0f
        val amplitude = amplitudePx.value
        val fraction = resolveMusicProgressFraction(value, valueRange.start, valueRange.endInclusive)
        val progressX = size.width * fraction
        val centerY = size.height / 2f
        if (amplitude <= 0.5f) {
            // 无波形(非 wavy 或收起中):直接画直线,跳过逐点采样。
            if (progressX > 0f) {
                drawLine(
                    color = activeColor,
                    start = Offset(0f, centerY),
                    end = Offset(progressX, centerY),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
        } else {
            val activePath = activePathScratch
            activePath.reset()
            // 采样步长按波长自适应(约 12 个采样点/波长),固定 2px 步长在宽屏下每帧
            // 会上百次 sin + lineTo,视觉上并无差异。
            val step = (wavelengthPx / 12f).coerceAtLeast(2f)
            var x = 0f
            var started = false
            while (x <= progressX) {
                val y = centerY + sin((x / wavelengthPx) * 2f * PI.toFloat() + phase) * amplitude
                if (!started) {
                    activePath.moveTo(x, y)
                    started = true
                } else {
                    activePath.lineTo(x, y)
                }
                x += step
            }
            if (started) {
                drawPath(
                    path = activePath,
                    color = activeColor,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        drawLine(
            color = inactiveColor,
            start = Offset(progressX, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = thumbColor,
            radius = thumbRadiusPx,
            center = Offset(progressX, centerY)
        )
    }
}
