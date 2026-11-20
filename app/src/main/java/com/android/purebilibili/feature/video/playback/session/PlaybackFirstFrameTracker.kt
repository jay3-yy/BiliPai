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
    private val listeners = WeakHashMap<Player, PlaybackFirstFrameListener>()

    @Synchronized
    fun observe(player: Player): StateFlow<Boolean> = listenerFor(player).hasRenderedFirstFrame

    @Synchronized
    fun observeHasPlayed(player: Player): StateFlow<Boolean> = listenerFor(player).hasPlayed

    private fun listenerFor(player: Player): PlaybackFirstFrameListener = listeners.getOrPut(player) {
        val listener = PlaybackFirstFrameListener()
        // The listener captures only its state, never the weak player key. Keep observing while
        // the mini player owns playback, even when every detail page has been disposed.
        player.addListener(listener)
        listener.onIsPlayingChanged(player.isPlaying)
        listener
    }

    /** 暂停预备帧尚非最终续播位置，保留封面直到目标位置的新帧到达。 */
    @Synchronized
    fun resetForStartupSeek(player: Player) {
        listeners[player]?.resetForStartupSeek()
    }
}

internal class PlaybackFirstFrameListener : Player.Listener {
    private val rendered = MutableStateFlow(false)
    val hasRenderedFirstFrame: StateFlow<Boolean> = rendered.asStateFlow()
    private val played = MutableStateFlow(false)
    val hasPlayed: StateFlow<Boolean> = played.asStateFlow()

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) played.value = true
    }

    override fun onEvents(player: Player, events: Player.Events) {
        // 自动换集时可能一直处于播放中，没有单独的 isPlaying 变更。
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) onIsPlayingChanged(player.isPlaying)
    }

    fun resetForStartupSeek() {
        rendered.value = false
    }

    override fun onRenderedFirstFrame() {
        rendered.value = true
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
            rendered.value = false
            played.value = false
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_IDLE) {
            rendered.value = false
            played.value = false
        }
    }
}
