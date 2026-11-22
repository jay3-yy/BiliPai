package com.android.purebilibili.feature.home.components.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import com.android.purebilibili.core.ui.blur.rememberContinuousBackdrop

/**
 * Keep the optical consumer's identity while a warmed shared source replaces its fallback.
 * Forward only the current recording: never retain a disposed layer, blend ancestor captures,
 * or freeze old pixels. Reading the state during drawing invalidates the sampled content.
 */
@Stable
internal class StableBackdrop(private val source: State<Backdrop>) : Backdrop {
    override val isCoordinatesDependent: Boolean get() = source.value.isCoordinatesDependent
    override val offsetResidualX: Float get() = source.value.offsetResidualX
    override val offsetResidualY: Float get() = source.value.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        with(source.value) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Composable
internal fun rememberStableBackdrop(source: Backdrop): Backdrop {
    val currentSource = rememberUpdatedState(rememberContinuousBackdrop(source))
    return remember { StableBackdrop(currentSource) }
}
