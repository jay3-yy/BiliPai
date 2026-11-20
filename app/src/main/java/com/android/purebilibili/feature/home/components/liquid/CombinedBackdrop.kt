package com.android.purebilibili.feature.home.components.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import com.android.purebilibili.core.ui.blur.ContinuousBackdrop

/**
 * BiliPai combined sampling: page backdrop first, then tinted tabs
 * capture, as a single sampling source for the liquid indicator.
 */
@Stable
class CombinedBackdrop(
    val first: Backdrop,
    val second: Backdrop,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = first.isCoordinatesDependent || second.isCoordinatesDependent

    // 每个源使用相同的连续采样，不借用第一层余量补偿其余图层。
    private val firstSampling = ContinuousBackdrop(first)
    private val secondSampling = ContinuousBackdrop(second)

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        with(firstSampling) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(secondSampling) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Composable
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }
