package com.android.purebilibili.core.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.android.purebilibili.core.ui.blur.SurfaceOverlayBackdrop
import org.junit.Assert.assertEquals
import org.junit.Test
import top.yukonga.miuix.kmp.blur.Backdrop

class SurfaceOverlayBackdropUiTest {
    private class Content : Backdrop {
        var draws = 0
        override val isCoordinatesDependent = false
        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
            downscaleFactor: Float,
        ) {
            draws++
            drawRect(Color.Red)
        }
    }

    @Test
    fun opaqueHeaderHidesContentAtEverySamplingScale() {
        val content = Content()
        for (scale in listOf(1f, 2.5f, 4f)) {
            assertColor(Color.Black, render(SurfaceOverlayBackdrop(content, Color.Black), scale))
        }
        assertEquals(0, content.draws)
    }

    @Test
    fun translucentAndTransparentHeadersKeepActualCompositedBackground() {
        val content = Content()
        val surface = Color.Black.copy(alpha = 0.5f)
        assertColor(surface.compositeOver(Color.Red), render(SurfaceOverlayBackdrop(content, surface)))
        assertColor(Color.Red, render(SurfaceOverlayBackdrop(content, Color.Transparent)))
    }

    @Test
    fun opaqueHeaderWorksBeforeContentSourceIsReady() {
        assertColor(Color.Black, render(SurfaceOverlayBackdrop(null, Color.Black)))
    }

    @Test
    fun sameBackdropFollowsChangingHeaderColor() {
        val content = Content()
        var surface = Color.Transparent
        val backdrop = SurfaceOverlayBackdrop(content) { surface }

        assertColor(Color.Red, render(backdrop))
        surface = Color.Black.copy(alpha = 0.5f)
        assertColor(surface.compositeOver(Color.Red), render(backdrop))
        surface = Color.Black
        assertColor(Color.Black, render(backdrop))
        assertEquals(2, content.draws)
    }

    private fun render(backdrop: Backdrop, factor: Float = 1f): Color {
        val bitmap = ImageBitmap(16, 16)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(16f, 16f)) {
            with(backdrop) { drawBackdrop(this@draw, null, null, factor) }
        }
        return bitmap.toPixelMap()[8, 8]
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.01f)
        assertEquals(expected.green, actual.green, 0.01f)
        assertEquals(expected.blue, actual.blue, 0.01f)
        assertEquals(expected.alpha, actual.alpha, 0.01f)
    }
}
