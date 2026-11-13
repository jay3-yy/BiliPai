package com.android.purebilibili.feature.home.components.cards

import kotlin.test.Test
import kotlin.test.assertEquals

class HorizontalVideoCardLayoutPolicyTest {

    @Test
    fun horizontalCoverStopsGrowingOnWideRows() {
        assertEquals(176f, resolveHorizontalVideoCoverWidthDp(320f), absoluteTolerance = 0.001f)
        assertEquals(220f, resolveHorizontalVideoCoverWidthDp(700f))
        assertEquals(220f, resolveHorizontalVideoCoverWidthDp(1200f))
        assertEquals(220f, resolveHorizontalVideoCoverWidthDp(1920f))
    }

    @Test
    fun coverHeightNeverUsesSubUnityAspect() {
        assertEquals(140f, resolveHorizontalVideoCoverHeightDp(140f, 0.5f))
    }

    @Test
    fun minimumHeightAccommodatesSelectedCoverAndLargeText() {
        assertEquals(105f, resolveHorizontalVideoCardMinimumHeightDp(1f, 4f / 3f))
        assertEquals(90f, resolveHorizontalVideoCardMinimumHeightDp(1f, 16f / 10f))
        assertEquals(90f, resolveHorizontalVideoCardMinimumHeightDp(1f, 16f / 9f))
        assertEquals(180f, resolveHorizontalVideoCardMinimumHeightDp(2f, 4f / 3f))
    }
}
