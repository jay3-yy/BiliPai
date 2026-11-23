package com.android.purebilibili.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuScrollHintGeometryTest {
    @Test
    fun `scroll metrics expose viewport and position fractions`() {
        val top = requireNotNull(resolveMenuScrollMetrics(400, 800, 0))
        val bottom = requireNotNull(resolveMenuScrollMetrics(400, 800, 800))

        assertEquals(1f / 3f, top.viewportFraction, 0.001f)
        assertEquals(0f, top.scrollFraction, 0f)
        assertEquals(1f, bottom.scrollFraction, 0f)
        assertNull(resolveMenuScrollMetrics(400, 0, 0))
        assertNull(resolveMenuScrollMetrics(400, Int.MAX_VALUE, 0))
    }

    @Test
    fun `edge overscroll shrinks the thumb and keeps the edge anchored`() {
        val metrics = MenuScrollMetrics(viewportFraction = 0.5f, scrollFraction = 0.5f)
        val base = requireNotNull(
            resolveMenuScrollHintGeometry(
                trackLength = 300f,
                metrics = metrics,
                overscrollPx = 0f,
                maxOverscrollPx = 100f,
                minLengthPx = 28f,
                baseWidthPx = 3f,
            )
        )
        val top = requireNotNull(
            resolveMenuScrollHintGeometry(
                trackLength = 300f,
                metrics = metrics,
                overscrollPx = 100f,
                maxOverscrollPx = 100f,
                minLengthPx = 28f,
                baseWidthPx = 3f,
            )
        )
        val bottom = requireNotNull(
            resolveMenuScrollHintGeometry(
                trackLength = 300f,
                metrics = metrics,
                overscrollPx = -100f,
                maxOverscrollPx = 100f,
                minLengthPx = 28f,
                baseWidthPx = 3f,
            )
        )

        assertEquals(150f, base.length, 0.001f)
        assertEquals(75f, base.top, 0.001f)
        assertEquals(0f, top.top, 0f)
        assertEquals(90f, top.length, 0.001f)
        assertTrue(top.width < base.width)
        assertEquals(210f, bottom.top, 0.001f)
        assertEquals(top.length, bottom.length, 0.001f)
    }
}
