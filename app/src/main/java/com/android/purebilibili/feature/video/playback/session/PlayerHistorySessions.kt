package com.android.purebilibili.feature.video.playback.session

import androidx.media3.common.Player
import java.util.WeakHashMap

/** Follows the player through detail, mini-player and background ownership changes. Main thread only. */
internal object PlayerHistorySessions {
    private val sessions = WeakHashMap<Player, PlaybackHistorySession>()

    fun forPlayer(player: Player): PlaybackHistorySession = sessions.getOrPut(player) {
        PlaybackHistorySession().also { session ->
            player.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    session.onIsPlayingChanged(isPlaying)
                }
            })
        }
    }
}
