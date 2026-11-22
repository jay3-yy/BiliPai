package com.android.purebilibili.feature.audio.player

import androidx.compose.runtime.Immutable
import com.android.purebilibili.feature.audio.lyrics.LyricDocument
import com.android.purebilibili.feature.video.player.PlayMode

internal sealed interface MusicPlaybackSource {
    val stableId: String

    data class AudioSong(val sid: Long) : MusicPlaybackSource {
        override val stableId: String = "au:$sid"
    }

    data class VideoAudio(
        val bvid: String,
        val cid: Long,
        val title: String
    ) : MusicPlaybackSource {
        override val stableId: String = "video:$bvid:$cid"
    }
}

internal enum class MusicPlaybackOwner {
    MINI_PLAYER_MANAGER,
    PLAYER_VIEW_MODEL
}

internal fun resolveMusicPlaybackOwner(source: MusicPlaybackSource): MusicPlaybackOwner = when (source) {
    is MusicPlaybackSource.AudioSong -> MusicPlaybackOwner.MINI_PLAYER_MANAGER
    is MusicPlaybackSource.VideoAudio -> MusicPlaybackOwner.PLAYER_VIEW_MODEL
}

internal data class MusicQueueControlState(
    val hasPrevious: Boolean,
    val hasNext: Boolean,
    val showQueue: Boolean
)

internal fun resolveMusicQueueControlState(
    queueSize: Int,
    currentIndex: Int,
    playMode: PlayMode = PlayMode.SEQUENTIAL,
    shuffleEnabled: Boolean = false
): MusicQueueControlState {
    if (queueSize <= 0 || currentIndex !in 0 until queueSize) {
        return MusicQueueControlState(false, false, false)
    }
    val wrapsQueue = shuffleEnabled || playMode != PlayMode.SEQUENTIAL
    return MusicQueueControlState(
        hasPrevious = queueSize > 1 && (wrapsQueue || currentIndex > 0),
        hasNext = queueSize > 1 && (wrapsQueue || currentIndex < queueSize - 1),
        showQueue = true
    )
}

internal fun shouldReleaseMusicPlayerOnScreenExit(
    isManagedByMiniPlayer: Boolean
): Boolean = !isManagedByMiniPlayer

@Immutable
internal data class MusicQueueItemUi(
    val stableId: String,
    val title: String,
    val artist: String,
    val coverUrl: String
)

@Immutable
internal data class MusicLyricCandidateUi(
    val title: String,
    val artist: String,
    val sourceLabel: String
)

@androidx.compose.runtime.Stable
internal data class MusicPlayerUiState(
    val title: String = "未知歌曲",
    val artist: String = "",
    val coverUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lyrics: LyricDocument? = null,
    val lyricsError: String? = null,
    val lyricCandidates: List<MusicLyricCandidateUi> = emptyList(),
    val isLyricsSearching: Boolean = false,
    val queue: List<MusicQueueItemUi> = emptyList(),
    val currentQueueIndex: Int = -1,
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1f
) {
    val queueControls: MusicQueueControlState
        get() = resolveMusicQueueControlState(
            queueSize = queue.size,
            currentIndex = currentQueueIndex,
            playMode = playMode,
            shuffleEnabled = shuffleEnabled
        )
}
