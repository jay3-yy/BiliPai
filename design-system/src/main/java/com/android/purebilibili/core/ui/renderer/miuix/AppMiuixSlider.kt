package com.android.purebilibili.core.ui.renderer.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.purebilibili.core.ui.components.AppSliderColors
import com.android.purebilibili.core.ui.components.appDesktopFocusableItemVisuals
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults

@Composable
internal fun AppMiuixSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)?,
    colors: AppSliderColors?,
) {
    val nativeColors = colors?.let {
        SliderDefaults.sliderColors(
            foregroundColor = it.activeTrackColor,
            backgroundColor = it.inactiveTrackColor,
            thumbColor = it.thumbColor,
        )
    } ?: if (com.android.purebilibili.core.ui.isMiuixNonGlassEnabled()) {
        SliderDefaults.sliderColors()
    } else SliderDefaults.sliderColors(thumbColor = com.android.purebilibili.core.ui.components.appControlThumbColor())
    ProvideAppMiuixHapticFeedback(scene = com.android.purebilibili.core.ui.AppHapticScene.ADJUSTMENT) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier.appDesktopFocusableItemVisuals(enabled),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            colors = nativeColors,
        )
    }
}
