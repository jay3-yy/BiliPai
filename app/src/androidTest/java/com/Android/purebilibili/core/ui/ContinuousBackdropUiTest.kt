package com.android.purebilibili.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.purebilibili.core.ui.blur.ContinuousBackdrop
import com.android.purebilibili.feature.home.components.liquid.CombinedBackdrop
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.blur.Backdrop
import kotlin.math.round

class ContinuousBackdropUiTest {
    /** 两个源故意错开 1px 原点，触发它们在不同的慢拖位置切换取整网格。 */
    private class GridSource(val origin: Float, val y: Float, val color: Color) : Backdrop {
        override val isCoordinatesDependent = false
        override var offsetResidualX = 0f
        var phase = 0f
        val factors = mutableListOf<Float>()

        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
            downscaleFactor: Float,
        ) {
            factors += downscaleFactor
            val offset = phase - origin
            offsetResidualX = if (downscaleFactor > 1) {
                offset - round(offset / downscaleFactor * 0.5f) * 2f * downscaleFactor
            } else 0f
            drawRect(
                color,
                topLeft = Offset((16f - phase + offsetResidualX) / downscaleFactor, y / downscaleFactor),
                size = Size(20f / downscaleFactor, 16f / downscaleFactor),
            )
        }
    }

    @Test
    fun combinedSourcesRemainAlignedAcrossIndependentGridBoundaries() {
        val first = GridSource(0f, 0f, Color.Red)
        val second = GridSource(1f, 16f, Color.Green)
        val combined = CombinedBackdrop(first, second)
        for (phase in listOf(1.9f, 2.1f, 2.9f, 3.1f, 1.9f)) {
            first.phase = phase
            second.phase = phase
            val pixels = render(combined, 2f).toPixelMap()
            for (x in 0 until pixels.width) {
                assertEquals("phase=$phase, x=$x", pixels[x, 3].red, pixels[x, 11].green, 0.02f)
            }
            assertEquals(0f, combined.offsetResidualX)
        }
        assertEquals(setOf(1f), first.factors.toSet())
        assertEquals(setOf(1f), second.factors.toSet())
    }

    @Test
    fun singleSourceKeepsContinuousCoordinatesAtEveryRequestedScale() {
        val source = GridSource(1f, 0f, Color.Red)
        val continuous = ContinuousBackdrop(source)
        for (scale in listOf(1f, 2.5f, 4f)) {
            source.phase = 2.1f
            render(continuous, scale)
            assertEquals(0f, continuous.offsetResidualX)
            assertEquals(0f, source.offsetResidualX)
        }
        assertEquals(listOf(1f, 1f, 1f), source.factors)
    }

    private fun render(backdrop: Backdrop, factor: Float): ImageBitmap {
        val bitmap = ImageBitmap(32, 16)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(32f, 16f)) {
            with(backdrop) { drawBackdrop(this@draw, null, null, factor) }
        }
        return bitmap
    }
}
