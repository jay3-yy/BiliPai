package com.android.purebilibili.feature.video.playback.policy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull

class PluginPollingPolicyTest {

    @Test
    fun `active playback should use tighter plugin poll interval`() {
        assertEquals(
            750L,
            resolvePluginPollingIntervalMs(
                hasPlugins = true,
                isPlaying = true
            )
        )
    }

    @Test
    fun `paused playback waits for player events`() {
        assertNull(
            resolvePluginPollingIntervalMs(
                hasPlugins = true,
                isPlaying = false
            )
        )
    }

    @Test
    fun `no plugin state has no position polling timer`() {
        assertNull(
            resolvePluginPollingIntervalMs(
                hasPlugins = false,
                isPlaying = true
            )
        )
    }

    @Test
    fun `sampling requires an enabled consumer and active playback or loading`() {
        assertNull(resolvePlaybackSamplingIntervalMs(
            isPlaying = false, isLoading = false, analyticsEnabled = true, cdnEnabled = true,
        ))
        assertNull(resolvePlaybackSamplingIntervalMs(
            isPlaying = true, isLoading = true, analyticsEnabled = false, cdnEnabled = false,
        ))
        assertEquals(5_000L, resolvePlaybackSamplingIntervalMs(
            isPlaying = true, isLoading = false, analyticsEnabled = true, cdnEnabled = false,
        ))
        // 暂停时仍在下载缓冲，CDN 的连接策略仍需要真实缓冲数据。
        assertEquals(5_000L, resolvePlaybackSamplingIntervalMs(
            isPlaying = false, isLoading = true, analyticsEnabled = false, cdnEnabled = true,
        ))
    }

    @Test
    fun `position updates should dispatch immediately the first time`() {
        assertTrue(
            shouldDispatchPluginPositionUpdate(
                lastDispatchedPositionMs = null,
                currentPositionMs = 0L,
                minPositionDeltaMs = 400L
            )
        )
    }

    @Test
    fun `position updates should skip tiny movement`() {
        assertFalse(
            shouldDispatchPluginPositionUpdate(
                lastDispatchedPositionMs = 1_000L,
                currentPositionMs = 1_250L,
                minPositionDeltaMs = 400L
            )
        )
    }

    @Test
    fun `position updates should dispatch after meaningful progress`() {
        assertTrue(
            shouldDispatchPluginPositionUpdate(
                lastDispatchedPositionMs = 1_000L,
                currentPositionMs = 1_500L,
                minPositionDeltaMs = 400L
            )
        )
    }

    @Test
    fun `position updates should dispatch after user seeks backwards`() {
        assertTrue(
            shouldDispatchPluginPositionUpdate(
                lastDispatchedPositionMs = 12_000L,
                currentPositionMs = 7_000L,
                minPositionDeltaMs = 400L
            )
        )
    }
}
