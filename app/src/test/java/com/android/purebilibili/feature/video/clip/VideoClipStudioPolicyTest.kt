package com.android.purebilibili.feature.video.clip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoClipStudioPolicyTest {

    @Test
    fun initialClipRangeDefaultsToFifteenSecondsAndBorrowsBackwardNearEnd() {
        val mid = resolveInitialClipRange(currentPositionMs = 20_000L, totalDurationMs = 120_000L)
        assertEquals(20_000L, mid.startMs)
        assertEquals(35_000L, mid.endMs)

        val nearEnd = resolveInitialClipRange(currentPositionMs = 115_000L, totalDurationMs = 120_000L)
        assertEquals(105_000L, nearEnd.startMs)
        assertEquals(120_000L, nearEnd.endMs)
    }

    @Test
    fun normalizeClipRangeEnforcesMinimumOneSecondAndMaximumSixtySeconds() {
        val tooShort = normalizeClipRange(startMs = 10_000L, endMs = 10_200L, totalDurationMs = 50_000L)
        assertEquals(1_000L, tooShort.durationMs)

        val tooLong = normalizeClipRange(startMs = 5_000L, endMs = 95_000L, totalDurationMs = 120_000L)
        assertEquals(60_000L, tooLong.durationMs)
        assertEquals(5_000L, tooLong.startMs)
        assertEquals(65_000L, tooLong.endMs)
    }

    @Test
    fun outputDisplayNameSanitizesIllegalCharactersAndIncludesTimeRange() {
        val name = buildClipOutputDisplayName(
            videoTitle = "教程/A:B*C?\"测试\"",
            startMs = 80_000L,
            endMs = 95_000L,
            mode = VideoClipExportMode.MP4_VIDEO,
        )
        assertTrue(name.endsWith("_01m20s-01m35s.mp4"))
        assertTrue(!name.contains("/") && !name.contains(":") && !name.contains("*"))

        val audioName = buildClipOutputDisplayName(
            videoTitle = "   ",
            startMs = 0L,
            endMs = 12_000L,
            mode = VideoClipExportMode.M4A_AUDIO,
        )
        assertEquals("BiliPai_Clip_00m00s-00m12s.m4a", audioName)
    }
}
