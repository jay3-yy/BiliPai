package com.android.purebilibili.feature.video.ui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.android.purebilibili.feature.video.danmaku.DanmakuTapAnchor
import kotlin.math.roundToInt

/** 气泡与原有定位使用同一内容尺寸，靠近播放器边缘时箭头仍指向所选弹幕。 */
internal data class DanmakuToolbarBubbleShape(
    val anchor: DanmakuTapAnchor,
    val window: IntSize,
    val gap: Int,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val position = resolveDanmakuToolbarPosition(
            anchor, window, IntSize(size.width.roundToInt(), size.height.roundToInt()), gap,
        )
        val pointsUp = position.y >= anchor.bottom.roundToInt()
        val arrow = with(density) { 6.dp.toPx() }.coerceAtMost(size.height / 4f)
        val top = if (pointsUp) arrow else 0f
        val bottom = if (pointsUp) size.height else size.height - arrow
        val radius = minOf((bottom - top) / 2f, size.width / 4f)
        val halfBase = minOf(arrow * 2f / 3f, size.width / 8f)
        val x = (anchor.tapX - position.x).coerceIn(radius + halfBase, size.width - radius - halfBase)
        val edge = if (pointsUp) top else bottom
        val tip = if (pointsUp) 0f else size.height
        return Outline.Generic(Path().apply {
            addRoundRect(RoundRect(0f, top, size.width, bottom, CornerRadius(radius)))
            moveTo(x - halfBase, edge)
            lineTo(x, tip)
            lineTo(x + halfBase, edge)
            close()
        })
    }
}
