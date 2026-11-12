package com.android.purebilibili.feature.video.screen

import androidx.media3.common.Player
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FullscreenPlaybackPositionPolicyTest {
    @Test
    fun `ended playback must not retain the final position even with play intent`() {
        assertFalse(
            shouldPreserveFullscreenPlaybackPosition(
                playbackState = Player.STATE_ENDED,
                playWhenReady = true,
                positionMs = 216_000L
            )
        )
    }

    @Test
    fun `active playback retains progress across fullscreen changes`() {
        for (state in listOf(Player.STATE_READY, Player.STATE_BUFFERING)) {
            assertTrue(shouldPreserveFullscreenPlaybackPosition(state, true, 42_000L))
            assertFalse(shouldPreserveFullscreenPlaybackPosition(state, false, 42_000L))
            assertFalse(shouldPreserveFullscreenPlaybackPosition(state, true, 0L))
        }
        assertFalse(shouldPreserveFullscreenPlaybackPosition(Player.STATE_IDLE, true, 42_000L))
    }
}
