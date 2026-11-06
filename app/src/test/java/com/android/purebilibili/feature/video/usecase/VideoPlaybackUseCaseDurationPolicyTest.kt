package com.android.purebilibili.feature.video.usecase

import com.android.purebilibili.core.player.resolvePlaybackCompletionPositionMs
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.data.model.response.ViewInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class VideoPlaybackUseCaseDurationPolicyTest {

    @Test
    fun `video load duration prefers playurl timelength`() {
        val info = ViewInfo(
            cid = 2L,
            pages = listOf(Page(cid = 2L, duration = 120L))
        )

        assertEquals(
            118_000L,
            resolveVideoLoadDurationMs(
                playUrlDurationMs = 118_000L,
                info = info
            )
        )
    }

    @Test
    fun `video load duration falls back to current page duration`() {
        val info = ViewInfo(
            cid = 2L,
            pages = listOf(
                Page(cid = 1L, duration = 60L),
                Page(cid = 2L, duration = 180L)
            )
        )

        assertEquals(
            180_000L,
            resolveVideoLoadDurationMs(
                playUrlDurationMs = 0L,
                info = info
            )
        )
    }

    @Test
    fun `completed cached position restarts from zero`() {
        assertEquals(
            0L,
            resolveRestoredPlaybackPositionMs(
                cachedPositionMs = 164_000L,
                videoDurationMs = 164_133L
            )
        )
    }

    @Test
    fun `mid video cached position is preserved`() {
        assertEquals(
            60_000L,
            resolveRestoredPlaybackPositionMs(
                cachedPositionMs = 60_000L,
                videoDurationMs = 164_133L
            )
        )
    }

    @Test
    fun `cached position is preserved when duration is unknown`() {
        assertEquals(
            60_000L,
            resolveRestoredPlaybackPositionMs(
                cachedPositionMs = 60_000L,
                videoDurationMs = 0L
            )
        )
    }

    @Test
    fun `short video completion keeps the 95 percent floor`() {
        assertEquals(
            114_000L,
            resolvePlaybackCompletionPositionMs(durationMs = 120_000L)
        )
    }

    @Test
    fun `long video needs to be within the last 60 seconds`() {
        val twoHoursMs = 7_200_000L
        // 95% 还剩 6 分钟，不能在这里就判看完。
        assertEquals(7_140_000L, resolvePlaybackCompletionPositionMs(twoHoursMs))
        assertEquals(
            7_000_000L,
            resolveRestoredPlaybackPositionMs(
                cachedPositionMs = 7_000_000L,
                videoDurationMs = twoHoursMs
            )
        )
        assertEquals(
            0L,
            resolveRestoredPlaybackPositionMs(
                cachedPositionMs = 7_140_000L,
                videoDurationMs = twoHoursMs
            )
        )
    }

}
