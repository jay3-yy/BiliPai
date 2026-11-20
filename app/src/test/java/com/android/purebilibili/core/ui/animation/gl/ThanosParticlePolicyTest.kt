package com.android.purebilibili.core.ui.animation.gl

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThanosParticlePolicyTest {
    @Test
    fun `GLES2 falls back while GLES3 supports transform feedback`() {
        assertFalse(supportsThanosGlVersion(0x20000))
        assertTrue(supportsThanosGlVersion(0x30000))
        assertTrue(supportsThanosGlVersion(0x30002))
    }

    @Test
    fun `particle grid preserves video aspect ratio at each upstream budget`() {
        for (budget in listOf(30_000, 60_000, 120_000)) {
            val grid = resolveThanosParticleGrid(1080, 720, 3f, budget)
            assertTrue(grid.count >= budget)
            // Upstream rounds to a complete rectangular grid, up to one row/column over.
            assertTrue(grid.count <= budget + grid.columns + grid.rows)
            assertTrue(abs(grid.columns.toFloat() / grid.rows - 1.5f) < 0.02f)
            assertTrue(grid.pointSize > 0f)
        }
    }

    @Test
    fun `small snapshots still have a valid grid and feedback stride`() {
        val grid = resolveThanosParticleGrid(1, 1, 1f, 30_000)
        assertTrue(grid.columns >= 1 && grid.rows >= 1)
        assertTrue(grid.count >= 10)
        assertEquals((2 + 2 + 2 + 1) * 4, THANOS_PARTICLE_STRIDE_BYTES)
    }

    @Test
    fun `completion waits for the upstream tail and is dispatched once`() {
        val end = THANOS_LONGEVITY + THANOS_TAIL_SECONDS
        assertFalse(shouldNotifyParticleAnimationComplete(false, THANOS_LONGEVITY, end))
        assertFalse(shouldNotifyParticleAnimationComplete(false, end, end))
        assertTrue(shouldNotifyParticleAnimationComplete(false, end + 0.01f, end))
        assertFalse(shouldNotifyParticleAnimationComplete(true, end + 0.01f, end))
        assertTrue(abs(end / THANOS_TIME_SCALE - 2.087f) < 0.001f)
    }

    @Test
    fun `reflow overlaps only the final particle tail without shortening simulation`() {
        val end = THANOS_LONGEVITY + THANOS_TAIL_SECONDS
        val threshold = end - THANOS_REFLOW_OVERLAP_SECONDS * THANOS_TIME_SCALE
        assertFalse(shouldBeginThanosReflow(THANOS_LONGEVITY))
        assertFalse(shouldBeginThanosReflow(threshold - 0.001f))
        assertTrue(shouldBeginThanosReflow(threshold))
        assertFalse(shouldNotifyParticleAnimationComplete(false, threshold, end))
        assertTrue(shouldBeginThanosReflow(end + 0.01f))
        assertTrue(abs((end - threshold) / THANOS_TIME_SCALE - 0.18f) < 0.001f)
    }

    @Test
    fun `quick reflow starts 180 ms before particles finish`() {
        assertFalse(shouldBeginThanosReflow(0.51f * THANOS_QUICK_TIME_SCALE, THANOS_QUICK_TIME_SCALE))
        assertTrue(shouldBeginThanosReflow(0.53f * THANOS_QUICK_TIME_SCALE, THANOS_QUICK_TIME_SCALE))
        assertFalse(shouldNotifyParticleAnimationComplete(false, 0.69f * THANOS_QUICK_TIME_SCALE, THANOS_DURATION_SECONDS))
        assertTrue(shouldNotifyParticleAnimationComplete(false, 0.71f * THANOS_QUICK_TIME_SCALE, THANOS_DURATION_SECONDS))
    }

}
