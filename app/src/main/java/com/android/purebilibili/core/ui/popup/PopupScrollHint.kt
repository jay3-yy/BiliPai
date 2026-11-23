package com.android.purebilibili.core.ui.popup

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.drawMenuScrollHint
import com.android.purebilibili.core.ui.components.resolveMenuScrollMetrics

internal data class PopupScrollThumb(val top: Float, val length: Float)

internal fun resolvePopupScrollThumb(
    trackLength: Float,
    viewport: Int,
    maxScroll: Int,
    scroll: Int,
    minLength: Float,
): PopupScrollThumb? {
    if (trackLength <= 0f || viewport <= 0 || maxScroll <= 0 || maxScroll == Int.MAX_VALUE) return null
    val metrics = resolveMenuScrollMetrics(viewport, maxScroll, scroll) ?: return null
    val length = (trackLength * metrics.viewportFraction)
        .coerceIn(minOf(minLength, trackLength), trackLength)
    return PopupScrollThumb((trackLength - length) * metrics.scrollFraction, length)
}

/** 只提示可滚动范围，不增加点击区域、不拦截正文手势，也不启动循环动画。 */
@Composable
internal fun Modifier.popupScrollHint(
    scrollState: ScrollState,
    overscrollPx: () -> Float = { 0f },
    maxOverscrollPx: () -> Float = { 0f },
): Modifier {
    val color = AppContentColors.primaryText().copy(alpha = 0.28f)
    return drawWithContent {
        drawContent()
        drawMenuScrollHint(
            metrics = resolveMenuScrollMetrics(scrollState),
            overscrollPx = overscrollPx(),
            maxOverscrollPx = maxOverscrollPx(),
            color = color,
            insetPx = 12.dp.toPx(),
            endInsetPx = 5.dp.toPx(),
            minLengthPx = 28.dp.toPx(),
            baseWidthPx = 3.dp.toPx(),
        )
    }
}
