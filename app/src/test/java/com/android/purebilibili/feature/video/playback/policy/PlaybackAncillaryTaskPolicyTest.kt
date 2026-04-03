package com.android.purebilibili.feature.video.playback.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackAncillaryTaskPolicyTest {

    @Test
    fun `online count refresh should require foreground and enabled setting`() {
        assertTrue(
            shouldRefreshOnlineCount(
                showOnlineCountEnabled = true,
                isInBackground = false,
                currentBvid = "BV1test",
                currentCid = 123L
            )
        )
        assertFalse(
            shouldRefreshOnlineCount(
                showOnlineCountEnabled = true,
                isInBackground = true,
                currentBvid = "BV1test",
                currentCid = 123L
            )
        )
        assertFalse(
            shouldRefreshOnlineCount(
                showOnlineCountEnabled = false,
                isInBackground = false,
                currentBvid = "BV1test",
                currentCid = 123L
            )
        )
    }

    @Test
    fun `online count polling should back off harder in background`() {
        assertEquals(30_000L, resolveOnlineCountPollingDelayMs(isInBackground = false))
        assertEquals(90_000L, resolveOnlineCountPollingDelayMs(isInBackground = true))
    }

    @Test
    fun `heartbeat should only send for active foreground playback`() {
        assertTrue(
            shouldSendPlaybackHeartbeat(
                isPlaying = true,
                isInBackground = false,
                currentBvid = "BV1test",
                currentCid = 456L
            )
        )
        assertFalse(
            shouldSendPlaybackHeartbeat(
                isPlaying = false,
                isInBackground = false,
                currentBvid = "BV1test",
                currentCid = 456L
            )
        )
        assertFalse(
            shouldSendPlaybackHeartbeat(
                isPlaying = true,
                isInBackground = true,
                currentBvid = "BV1test",
                currentCid = 456L
            )
        )
    }

    @Test
    fun `heartbeat session start timestamp should stay stable once assigned`() {
        assertEquals(
            1_700_000_000L,
            resolvePlaybackHeartbeatSessionStartTsSec(
                existingStartTsSec = 1_700_000_000L,
                nowEpochSec = 1_700_000_090L
            )
        )
        assertEquals(
            1_700_000_090L,
            resolvePlaybackHeartbeatSessionStartTsSec(
                existingStartTsSec = 0L,
                nowEpochSec = 1_700_000_090L
            )
        )
    }

    @Test
    fun `heartbeat snapshot should use watched duration instead of playback position for real played time`() {
        val snapshot = resolvePlaybackHeartbeatSnapshot(
            currentPositionMs = 7_230_000L,
            accumulatedPlayMs = 18_000L,
            activePlayStartElapsedMs = 2_000L,
            nowElapsedMs = 14_000L
        )

        assertEquals(7_230L, snapshot.playedTimeSec)
        assertEquals(30L, snapshot.realPlayedTimeSec)
    }

    @Test
    fun `final heartbeat flush should only send meaningful progress deltas`() {
        val advancedSnapshot = PlaybackHeartbeatSnapshot(
            playedTimeSec = 54L,
            realPlayedTimeSec = 31L
        )
        assertTrue(
            shouldFlushPlaybackHeartbeatSnapshot(
                currentBvid = "BV1test",
                currentCid = 456L,
                snapshot = advancedSnapshot,
                lastReportedSnapshot = PlaybackHeartbeatSnapshot(
                    playedTimeSec = 30L,
                    realPlayedTimeSec = 30L
                )
            )
        )

        assertFalse(
            shouldFlushPlaybackHeartbeatSnapshot(
                currentBvid = "BV1test",
                currentCid = 456L,
                snapshot = PlaybackHeartbeatSnapshot(
                    playedTimeSec = 0L,
                    realPlayedTimeSec = 0L
                ),
                lastReportedSnapshot = null
            )
        )

        assertFalse(
            shouldFlushPlaybackHeartbeatSnapshot(
                currentBvid = "BV1test",
                currentCid = 456L,
                snapshot = advancedSnapshot,
                lastReportedSnapshot = advancedSnapshot
            )
        )
    }
}
