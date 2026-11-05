package com.android.purebilibili.feature.home.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import kotlin.math.roundToInt

/** 图标从圆钮中央连续移动到输入框前缘；玻璃和原生分别传入原有的展开留白。 */
internal fun Modifier.bottomBarSearchContentInsets(
    progress: () -> Float,
    expandedInset: Dp,
): Modifier = layout { measurable, constraints ->
    val iconWidth = AppChromeSizeTokens.MinimumTouchTarget.roundToPx()
    val centeredInset = (constraints.maxWidth - iconWidth).coerceAtLeast(0) / 2f
    val amount = progress().coerceIn(0f, 1f)
    val inset = (centeredInset + (expandedInset.toPx() - centeredInset) * amount)
        .roundToInt().coerceIn(0, constraints.maxWidth / 2)
    val placeable = measurable.measure(constraints.offset(horizontal = -2 * inset))
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative(inset, 0)
    }
}
