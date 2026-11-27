package com.bytedance.danmaku.render.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class RenderFrameCadenceTest {
    @Test
    fun `native refresh cadence has no skipped vsyncs on 60 90 120 and 144Hz`() {
        listOf(60, 90, 120, 144).forEach { hz ->
            val cadence = RenderFrameCadence()
            var rendered = 0
            repeat(hz * 10) { tick ->
                val time = tick * 1_000_000_000L / hz
                if (cadence.shouldRender(time)) {
                    cadence.onFrame(time)
                    rendered++
                }
            }
            assertEquals(hz * 10, rendered, "display=$hz Hz")
        }
    }

    @Test
    fun `duplicate callbacks are suppressed and resume has no frame debt`() {
        val cadence = RenderFrameCadence()
        cadence.onFrame(1_000_000_000L)
        assertFalse(cadence.shouldRender(1_000_000_000L))
        assertTrue(cadence.shouldRender(1_008_333_333L))
        cadence.onFrame(9_000_000_000L)
        assertFalse(cadence.shouldRender(9_000_000_000L))
        cadence.reset()
        assertTrue(cadence.shouldRender(9_000_000_000L))
    }

    @Test
    fun `harmonic divisor targets cleanly divide high refresh panels without uneven drops`() {
        // 120Hz -> 60Hz (2:1)
        val cadence60 = RenderFrameCadence().apply { updateCadence(targetFps = 60f, displayHz = 120f) }
        var rendered60 = 0
        repeat(120 * 5) { tick ->
            val time = tick * 1_000_000_000L / 120
            if (cadence60.shouldRender(time)) {
                cadence60.onFrame(time)
                rendered60++
            }
        }
        assertEquals(60 * 5, rendered60)

        // 90Hz -> 30Hz (3:1)
        val cadence30 = RenderFrameCadence().apply { updateCadence(targetFps = 30f, displayHz = 90f) }
        var rendered30 = 0
        repeat(90 * 5) { tick ->
            val time = tick * 1_000_000_000L / 90
            if (cadence30.shouldRender(time)) {
                cadence30.onFrame(time)
                rendered30++
            }
        }
        assertEquals(30 * 5, rendered30)
    }

    @Test
    fun `non-divisor target on 90Hz panel keeps native cadence instead of degrading to 45fps`() {
        assertFalse(isHarmonicFrameRateDivisor(displayRefreshRateHz = 90f, targetFrameRateHz = 60f))
        assertFalse(isHarmonicFrameRateDivisor(displayRefreshRateHz = 144f, targetFrameRateHz = 60f))
        assertTrue(isHarmonicFrameRateDivisor(displayRefreshRateHz = 120f, targetFrameRateHz = 60f))
        assertTrue(isHarmonicFrameRateDivisor(displayRefreshRateHz = 90f, targetFrameRateHz = 30f))

        val cadence = RenderFrameCadence().apply { updateCadence(targetFps = 60f, displayHz = 90f) }
        var rendered = 0
        repeat(90 * 3) { tick ->
            val time = tick * 1_000_000_000L / 90
            if (cadence.shouldRender(time)) {
                cadence.onFrame(time)
                rendered++
            }
        }
        assertEquals(90 * 3, rendered)
    }
}
