@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.playback.session

import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackFirstFrameTrackerTest {
    @Test
    fun readyPlaybackAndDecodedDimensionsDoNotRevealAnUnrenderedVideo() {
        val listener = PlaybackFirstFrameListener()
        listener.onVideoSizeChanged(VideoSize(1920, 1080))
        listener.onPlaybackStateChanged(Player.STATE_READY)
        listener.onIsPlayingChanged(true)
        assertFalse(listener.hasRenderedFirstFrame.value)
        listener.onRenderedFirstFrame()
        assertTrue(listener.hasRenderedFirstFrame.value)
    }

    @Test
    fun renderedFrameSurvivesPauseBufferingAndRepeatOfTheSameMedia() {
        val listener = PlaybackFirstFrameListener()
        listener.onRenderedFirstFrame()
        listener.onIsPlayingChanged(false)
        listener.onPlaybackStateChanged(Player.STATE_BUFFERING)
        listener.onPlaybackStateChanged(Player.STATE_READY)
        listener.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        assertTrue(listener.hasRenderedFirstFrame.value)
    }

    @Test
    fun reusingThePlayerForAnotherMediaRequiresItsOwnRenderedFrame() {
        for (reason in listOf(
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK,
            Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED,
        )) {
            val listener = PlaybackFirstFrameListener()
            listener.onRenderedFirstFrame()
            listener.onMediaItemTransition(null, reason)
            listener.onPlaybackStateChanged(Player.STATE_READY)
            assertFalse(listener.hasRenderedFirstFrame.value)
            listener.onRenderedFirstFrame()
            assertTrue(listener.hasRenderedFirstFrame.value)
        }
    }

    @Test
    fun stoppingThePlayerInvalidatesItsRetainedFrame() {
        val listener = PlaybackFirstFrameListener()
        listener.onRenderedFirstFrame()
        listener.onPlaybackStateChanged(Player.STATE_IDLE)
        assertFalse(listener.hasRenderedFirstFrame.value)
    }
}
