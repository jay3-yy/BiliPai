package com.android.purebilibili.navigation3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSharedContainerPolicyTest {
    @Test
    fun ordinaryControlsCanUseSharedFallback() {
        assertTrue(supportsNavigationSharedContainer(BiliPaiNavKey.Settings))
        assertTrue(supportsNavigationSharedContainer(BiliPaiNavKey.Search()))
    }

    @Test
    fun videoDetailsAndUnmatchedDestinationsKeepTheirPageTransition() {
        assertFalse(supportsNavigationSharedContainer(BiliPaiNavKey.VideoDetail(bvid = "BV1")))
        assertFalse(supportsNavigationSharedContainer(BiliPaiNavKey.VideoDetail(bvid = "BV1", fullscreen = true)))
        assertFalse(supportsNavigationSharedContainer(BiliPaiNavKey.VideoDetail(bvid = "BV1", directPortraitEntry = true)))
        assertFalse(supportsNavigationSharedContainer(BiliPaiNavKey.MainHost))
    }

    @Test
    fun neighborStartsAndLandsAtRestWithOneCompressionPeak() {
        assertEquals(0f, navigationSharedNeighborPressure(0f), 0f)
        assertEquals(0f, navigationSharedNeighborPressure(1f), 0f)
        // 端点斜率趋近零，不能在落位时骤停。
        assertTrue(navigationSharedNeighborPressure(0.001f) / 0.001f < 0.02f)
        assertTrue(navigationSharedNeighborPressure(0.999f) / 0.001f < 0.02f)
        val entering = listOf(0f, 0.1f, 0.3f, 0.5f).map(::navigationSharedNeighborPressure)
        val landing = listOf(0.5f, 0.7f, 0.9f, 1f).map(::navigationSharedNeighborPressure)
        assertTrue(entering.zipWithNext().all { (before, after) -> before < after })
        assertTrue(landing.zipWithNext().all { (before, after) -> before > after })
    }
}
