package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.material.EdgePeak
import top.yukonga.miuix.kmp.material.EdgeStrokes
import top.yukonga.miuix.kmp.material.drawEdgeStroke
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported

/** 仅玻璃播放器使用轨道高光；不增加背景采样，也不改变原生轨道尺寸和触摸区域。 */
@Composable
internal fun rememberPlayerProgressGlassHighlight(): PlayerProgressGlassHighlight? {
    if (!LocalPlayerControlGlassEnabled.current) return null
    val density = LocalDensity.current
    return remember(density) {
        PlayerProgressGlassHighlight(with(density) { 0.65.dp.toPx() })
    }
}

internal class PlayerProgressGlassHighlight(
    private val widthPx: Float,
) {
    private val shape = RoundedCornerShape(50)
    private val rim = EdgeStrokes.SmallDark.copy(width = 0.65.dp, bevel = 0.6.dp, peak = EdgePeak.Vertical)
    private val stroke = Stroke(widthPx)
    private var fallbackHeight = -1f
    private var fallbackBrush: Brush? = null

    fun draw(scope: DrawScope, trackHeightPx: Float) = with(scope) {
        val height = trackHeightPx.coerceAtMost(size.height)
        if (height <= 0f || size.width <= 0f) return@with
        val top = (size.height - height) / 2f
        inset(left = 0f, top = top, right = 0f, bottom = top) {
            if (isRuntimeShaderSupported()) {
                drawEdgeStroke(shape, rim, alpha = 0.7f)
            } else {
                if (fallbackHeight != height) {
                    fallbackHeight = height
                    fallbackBrush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.5f),
                        0.5f to Color.White.copy(alpha = 0.08f),
                        1f to Color.White.copy(alpha = 0.22f),
                        startY = 0f,
                        endY = height,
                    )
                }
                val halfStroke = widthPx / 2f
                if (size.width > widthPx && height > widthPx) {
                    drawRoundRect(
                        brush = requireNotNull(fallbackBrush),
                        topLeft = Offset(halfStroke, halfStroke),
                        size = Size(size.width - widthPx, height - widthPx),
                        cornerRadius = CornerRadius((height - widthPx) / 2f),
                        style = stroke,
                    )
                }
            }
        }
    }
}
