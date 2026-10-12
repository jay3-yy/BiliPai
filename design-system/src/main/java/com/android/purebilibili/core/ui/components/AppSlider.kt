package com.android.purebilibili.core.ui.components

import android.os.SystemClock
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3Slider
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixSlider

@Immutable
data class AppSliderColors(
    val thumbColor: Color,
    val activeTrackColor: Color,
    val inactiveTrackColor: Color,
)

object AppSliderDefaults {
    fun colors(
        thumbColor: Color,
        activeTrackColor: Color,
        inactiveTrackColor: Color,
    ): AppSliderColors = AppSliderColors(
        thumbColor = thumbColor,
        activeTrackColor = activeTrackColor,
        inactiveTrackColor = inactiveTrackColor,
    )
}

@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: AppSliderColors? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val haptic = LocalHapticFeedback.current
    val hapticTickCount = (steps + 1).takeIf { steps > 0 } ?: 20
    fun hapticBucket(progress: Float): Int = (
        ((progress - valueRange.start) / (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)) *
            hapticTickCount
        ).toInt().coerceIn(0, hapticTickCount)
    var lastHapticBucket by remember(valueRange, hapticTickCount) {
        mutableIntStateOf(hapticBucket(value))
    }
    var lastHapticTimeMs by remember { mutableLongStateOf(0L) }
    val onSliderValueChange: (Float) -> Unit = { nextValue ->
        val nextBucket = hapticBucket(nextValue)
        val nowMs = SystemClock.elapsedRealtime()
        if (enabled && nextBucket != lastHapticBucket && nowMs - lastHapticTimeMs >= 55L) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastHapticTimeMs = nowMs
            lastHapticBucket = nextBucket
        } else if (nextBucket != lastHapticBucket) {
            lastHapticBucket = nextBucket
        }
        onValueChange(nextValue)
    }
    if (useAppLiquidValueControls() && value.isFinite() && valueRange.endInclusive > valueRange.start) {
        AppLiquidSlider(value, onSliderValueChange, modifier, valueRange, steps, onValueChangeFinished, colors, enabled, interactionSource)
        return
    }
    when (LocalAppUiStyle.current) {
        AppUiStyle.MATERIAL3 -> AppMaterial3Slider(
            value = value,
            onValueChange = onSliderValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
            interactionSource = interactionSource,
        )
        AppUiStyle.MIUIX -> AppMiuixSlider(
            value = value,
            onValueChange = onSliderValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = colors,
        )
    }
}
