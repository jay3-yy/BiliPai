package com.android.purebilibili.feature.video.ui.overlay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextWatchPollDelayPolicyTest {

    @Test
    fun `resolveNextWatchPollDelayMs suspends when paused or duration unknown`() {
        assertNull(resolveNextWatchPollDelayMs(durationMs = 60_000L, positionMs = 58_000L, isPlaying = false))
        assertNull(resolveNextWatchPollDelayMs(durationMs = 0L, positionMs = 0L, isPlaying = true))
        assertNull(resolveNextWatchPollDelayMs(durationMs = -1L, positionMs = 0L, isPlaying = true))
    }

    @Test
    fun `resolveNextWatchPollDelayMs sleeps coarsely far from end and switches to 250ms near end`() {
        // 30 min remaining at 1x -> capped at 60s
        assertEquals(60_000L, resolveNextWatchPollDelayMs(durationMs = 1_800_000L, positionMs = 0L, isPlaying = true, playbackSpeed = 1f))
        // 26s remaining at 2x -> (26s - 6s) / 2 = 10s
        assertEquals(10_000L, resolveNextWatchPollDelayMs(durationMs = 60_000L, positionMs = 34_000L, isPlaying = true, playbackSpeed = 2f))
        // Inside final 6.5s window -> 250ms countdown cadence
        assertEquals(250L, resolveNextWatchPollDelayMs(durationMs = 60_000L, positionMs = 54_000L, isPlaying = true, playbackSpeed = 1f))
    }
}
