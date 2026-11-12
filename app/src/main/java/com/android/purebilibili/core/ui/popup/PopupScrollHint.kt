package com.android.purebilibili.core.ui.popup

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors

internal data class PopupScrollThumb(val top: Float, val length: Float)

internal fun resolvePopupScrollThumb(
    trackLength: Float,
    viewport: Int,
    maxScroll: Int,
    scroll: Int,
    minLength: Float,
): PopupScrollThumb? {
    if (trackLength <= 0f || viewport <= 0 || maxScroll <= 0 || maxScroll == Int.MAX_VALUE) return null
    val length = (trackLength * viewport.toFloat() / (viewport.toFloat() + maxScroll))
        .coerceIn(minOf(minLength, trackLength), trackLength)
    return PopupScrollThumb((trackLength - length) * scroll.toFloat() / maxScroll, length)
}

/** 只提示可滚动范围，不增加点击区域、不拦截正文手势，也不启动循环动画。 */
@Composable
internal fun Modifier.popupScrollHint(scrollState: ScrollState): Modifier {
    val color = AppContentColors.primaryText().copy(alpha = 0.28f)
    return drawWithContent {
        drawContent()
        val inset = 12.dp.toPx()
        val thumb = resolvePopupScrollThumb(
            size.height - inset * 2f, scrollState.viewportSize, scrollState.maxValue,
            scrollState.value, 28.dp.toPx(),
        ) ?: return@drawWithContent
        val width = 3.dp.toPx()
        drawRoundRect(color, Offset(size.width - 5.dp.toPx() - width, inset + thumb.top),
            Size(width, thumb.length), CornerRadius(width / 2f))
    }
}
