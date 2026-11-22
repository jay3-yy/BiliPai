package com.android.purebilibili.core.ui.blur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 在共同的连续坐标系中采样，再缩入调用方的降采样目标。
 *
 * 源不再分别把坐标对齐到低分辨率网格，因此各层没有待补偿的小数余量。
 * 保留调用方的降采样缓冲、模糊和放大流程，不创建额外图层或全尺寸位图。
 */
class ContinuousBackdrop(private val source: Backdrop) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = source.isCoordinatesDependent

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        val factor = downscaleFactor
        if (factor == 1f) {
            with(source) { drawBackdrop(density, coordinates, layerBlock, 1f) }
        } else {
            // 围绕左上角等比缩放，与源内部的逆向缩放/旋转补偿相容。
            scale(1f / factor, 1f / factor, Offset.Zero) {
                with(source) { drawBackdrop(density, coordinates, layerBlock, 1f) }
            }
        }
    }
}

@Composable
fun rememberContinuousBackdrop(source: Backdrop): Backdrop =
    if (source is ContinuousBackdrop) source else remember(source) { ContinuousBackdrop(source) }
