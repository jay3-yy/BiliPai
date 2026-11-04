@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.playback.session

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.WeakHashMap

/** A rendered frame belongs to the player/media, not to a detail page's diagnostic collector. */
internal object PlaybackFirstFrameTracker {
    private val states = WeakHashMap<Player, StateFlow<Boolean>>()

    @Synchronized
    fun observe(player: Player): StateFlow<Boolean> = states.getOrPut(player) {
        val listener = PlaybackFirstFrameListener()
        // The listener captures only its state, never the weak player key. Keep observing while
        // the mini player owns playback, even when every detail page has been disposed.
        player.addListener(listener)
        listener.hasRenderedFirstFrame
    }
}

internal class PlaybackFirstFrameListener : Player.Listener {
    private val rendered = MutableStateFlow(false)
    val hasRenderedFirstFrame: StateFlow<Boolean> = rendered.asStateFlow()

    override fun onRenderedFirstFrame() {
        rendered.value = true
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            rendered.value = false
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_IDLE) {
            rendered.value = false
        }
    }
}
