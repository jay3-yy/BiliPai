package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VideoDetailTopOverscrollZoomTest {

    @Test
    fun `top overscroll scale stays at 1 for non-positive or non-finite input`() {
        assertEquals(1f, resolveVideoDetailTopOverscrollScale(0f, 660f))
        assertEquals(1f, resolveVideoDetailTopOverscrollScale(-24f, 660f))
        assertEquals(1f, resolveVideoDetailTopOverscrollScale(Float.NaN, 660f))
    }

    @Test
    fun `top overscroll scale increases monotonically with diminishing returns below cap`() {
        val small = resolveVideoDetailTopOverscrollScale(100f, 660f)
        val medium = resolveVideoDetailTopOverscrollScale(400f, 660f)
        val large = resolveVideoDetailTopOverscrollScale(4000f, 660f)
        assertTrue(small > 1f)
        assertTrue(medium > small)
        assertTrue(large > medium)
        assertTrue(large < 1f + VIDEO_DETAIL_TOP_OVERSCROLL_MAX_SCALE_DELTA)
    }

    @Test
    fun `top fling impulse ignores slow velocities and caps at max delta`() {
        assertEquals(1f, resolveVideoDetailTopFlingImpulseScale(300f, density = 3f))
        val medium = resolveVideoDetailTopFlingImpulseScale(3600f, density = 3f)
        val extreme = resolveVideoDetailTopFlingImpulseScale(30000f, density = 3f)
        assertTrue(medium > 1f)
        assertTrue(medium < extreme)
        assertEquals(1f + VIDEO_DETAIL_TOP_FLING_MAX_SCALE_DELTA, extreme, 0.0001f)
    }
}
