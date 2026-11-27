package com.android.purebilibili.feature.plugin.googlecast

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Media3CastPlaybackPolicyTest {

    @Test
    fun sessionWaitingPreservesTenSecondTimeoutSemantics() {
        assertTrue(
            shouldContinueWaitingForMedia3CastSession(
                remotePlaybackActive = false,
                elapsedMs = 0L,
            )
        )
        assertTrue(
            shouldContinueWaitingForMedia3CastSession(
                remotePlaybackActive = false,
                elapsedMs = MEDIA3_CAST_SESSION_TIMEOUT_MS - 1L,
            )
        )
        assertFalse(
            shouldContinueWaitingForMedia3CastSession(
                remotePlaybackActive = true,
                elapsedMs = 500L,
            )
        )
        assertFalse(
            shouldContinueWaitingForMedia3CastSession(
                remotePlaybackActive = false,
                elapsedMs = MEDIA3_CAST_SESSION_TIMEOUT_MS,
            )
        )
    }

    @Test
    fun playbackActiveRequiresLiveCastSessionEvenWhenStaleMediaItemRemains() {
        assertFalse(
            resolveMedia3CastPlaybackActive(
                isCastSessionAvailable = false,
                hasCurrentMediaItem = true,
                playbackState = Player.STATE_READY,
            )
        )
        assertFalse(
            resolveMedia3CastPlaybackActive(
                isCastSessionAvailable = true,
                hasCurrentMediaItem = false,
                playbackState = Player.STATE_IDLE,
            )
        )
        assertTrue(
            resolveMedia3CastPlaybackActive(
                isCastSessionAvailable = true,
                hasCurrentMediaItem = true,
                playbackState = Player.STATE_IDLE,
            )
        )
        assertTrue(
            resolveMedia3CastPlaybackActive(
                isCastSessionAvailable = true,
                hasCurrentMediaItem = false,
                playbackState = Player.STATE_BUFFERING,
            )
        )
    }
}
