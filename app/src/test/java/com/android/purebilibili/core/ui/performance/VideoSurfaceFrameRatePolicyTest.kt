package com.android.purebilibili.core.ui.performance

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoSurfaceFrameRatePolicyTest {
    @Test
    fun contentCadenceIsPreservedIncludingFractionalRatesAndPlaybackSpeed() {
        assertEquals(23.976f, resolveVideoSurfaceFrameRate(23.976f, 1f, true))
        assertEquals(59.94f, resolveVideoSurfaceFrameRate(29.97f, 2f, true))
        assertEquals(120f, resolveVideoSurfaceFrameRate(60f, 2f, true))
    }

    @Test
    fun pausedPlaybackClearsTheVote() {
        assertEquals(0f, resolveVideoSurfaceFrameRate(60f, 1f, false))
    }

    @Test
    fun unknownOrUnusableTimingDoesNotInventAFrameRate() {
        for (value in listOf(-1f, 0f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertEquals(0f, resolveVideoSurfaceFrameRate(value, 1f, true))
            assertEquals(0f, resolveVideoSurfaceFrameRate(60f, value, true))
        }
        assertEquals(0f, resolveVideoSurfaceFrameRate(Float.MAX_VALUE, 2f, true))
    }
}
