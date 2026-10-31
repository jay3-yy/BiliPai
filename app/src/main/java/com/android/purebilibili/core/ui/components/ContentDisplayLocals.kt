package com.android.purebilibili.core.ui.components

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.android.purebilibili.core.store.ContentImageSettings
import com.android.purebilibili.core.store.ScrollFeedbackPreset

internal val LocalContentImageDarkTheme = compositionLocalOf { false }
internal val LocalContentImageSettings = compositionLocalOf { ContentImageSettings() }
internal val LocalScrollFeedbackPreset = compositionLocalOf { ScrollFeedbackPreset.STANDARD }

internal fun contentImageDimFilter(dark: Boolean, percent: Int): ColorFilter? {
    if (!dark || percent <= 0) return null
    val multiplier = 1f - percent.coerceIn(0, 30) / 100f
    return ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        multiplier, 0f, 0f, 0f, 0f,
        0f, multiplier, 0f, 0f, 0f,
        0f, 0f, multiplier, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
}

/** 只用于首页下拉释放与底栏收合；标准档保留调用方的原始参数。 */
internal fun scrollFeedbackSpring(
    preset: ScrollFeedbackPreset,
    damping: Float,
    stiffness: Float,
): SpringSpec<Float> = when (preset) {
    ScrollFeedbackPreset.STANDARD -> spring(dampingRatio = damping, stiffness = stiffness)
    ScrollFeedbackPreset.GENTLE -> spring(dampingRatio = 1f, stiffness = stiffness)
    ScrollFeedbackPreset.LIVELY -> spring(dampingRatio = 0.68f, stiffness = stiffness)
}
