package com.android.purebilibili.feature.home.components.liquid

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.blur.Backdrop

class StableBackdropTest {
    @Test
    fun sourceReplacementUsesCurrentCoordinatesWithoutRetainingFallback() {
        val fallback = RecordingBackdrop(false, 1f, 2f)
        val shared = RecordingBackdrop(true, 30f, 40f)
        val source = mutableStateOf<Backdrop>(fallback)
        val stable = StableBackdrop(source)
        assertFalse(stable.isCoordinatesDependent)
        assertEquals(1f, stable.offsetResidualX, 0f)

        source.value = shared

        assertTrue(stable.isCoordinatesDependent)
        assertEquals(30f, stable.offsetResidualX, 0f)
        assertEquals(40f, stable.offsetResidualY, 0f)
        val scope = mockk<DrawScope>()
        with(stable) { with(scope) { drawBackdrop(Density(1f), null, null, 4f) } }
        assertEquals(0, fallback.drawCount)
        assertEquals(1, shared.drawCount)
        assertEquals(4f, shared.lastDownscale, 0f)
    }

    @Test
    fun sourcePixelsStayLiveAcrossRepeatedDraws() {
        val recording = RecordingBackdrop(true, 0f, 0f)
        val stable = StableBackdrop(mutableStateOf<Backdrop>(recording))
        val scope = mockk<DrawScope>()
        repeat(3) {
            with(stable) { with(scope) { drawBackdrop(Density(1f), null, null, 1f) } }
        }
        assertEquals(3, recording.drawCount)
    }

    private class RecordingBackdrop(
        override val isCoordinatesDependent: Boolean,
        override val offsetResidualX: Float,
        override val offsetResidualY: Float,
    ) : Backdrop {
        var drawCount = 0
        var lastDownscale = 0f

        override fun DrawScope.drawBackdrop(
            density: Density,
            coordinates: LayoutCoordinates?,
            layerBlock: (GraphicsLayerScope.() -> Unit)?,
            downscaleFactor: Float,
        ) {
            drawCount++
            lastDownscale = downscaleFactor
        }
    }
}
