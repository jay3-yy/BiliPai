package com.android.purebilibili.navigation3.predictiveback

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.sin

internal fun resolveVideoCardPathOffset(
    bounds: Rect,
    width: Float,
    height: Float,
    morph: Float,
    arcAmplitudePx: Float,
): Offset {
    val remaining = 1f - morph
    val arcSign = if (bounds.center.x < width / 2f) 1f else -1f
    return Offset(
        bounds.left.coerceIn(-width, width) * remaining +
            arcSign * arcAmplitudePx * sin(PI.toFloat() * morph) * remaining,
        bounds.top.coerceIn(-height, height) * remaining,
    )
}
