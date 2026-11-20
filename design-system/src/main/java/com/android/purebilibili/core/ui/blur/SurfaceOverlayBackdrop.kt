package com.android.purebilibili.core.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

/** 背景采样与可见遮色按绘制顺序合成；不捕获按钮、文字或玻璃自身。 */
class SurfaceOverlayBackdrop(
    private val content: Backdrop?,
    private val surfaceColor: () -> Color,
) : Backdrop {
    constructor(content: Backdrop?, surfaceColor: Color) : this(content, { surfaceColor })

    override val isCoordinatesDependent: Boolean get() = content?.isCoordinatesDependent == true
    override val offsetResidualX: Float get() = if (surfaceColor().alpha >= 1f) 0f else content?.offsetResidualX ?: 0f
    override val offsetResidualY: Float get() = if (surfaceColor().alpha >= 1f) 0f else content?.offsetResidualY ?: 0f

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        val color = surfaceColor()
        // 完全遮挡时不再绘制底层内容，保持采样与实色顶栏一致。
        if (color.alpha < 1f && content != null) {
            with(content) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        }
        drawRect(color)
    }
}

@Composable
fun rememberSurfaceOverlayBackdrop(content: Backdrop?, surfaceColor: Color): Backdrop? =
    if (surfaceColor.alpha <= 0f) content else remember(content, surfaceColor) {
        SurfaceOverlayBackdrop(content, surfaceColor)
    }
