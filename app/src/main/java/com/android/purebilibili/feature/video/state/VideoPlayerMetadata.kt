package com.android.purebilibili.feature.video.state

import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** 播放器只订阅媒体身份和展示信息，附属详情变化不使整个播放器状态持有器重组。 */
internal data class VideoPlayerMetadata(
    val bvid: String, val cid: Long, val title: String, val artist: String, val coverUrl: String,
)

internal fun VideoPlaybackUiState.playerMetadata(): VideoPlayerMetadata? =
    (this as? VideoPlaybackUiState.Success)?.info?.let {
        VideoPlayerMetadata(it.bvid, it.cid, it.title, it.owner.name, it.pic)
    }

internal fun Flow<VideoPlaybackUiState>.playerMetadataUpdates(): Flow<VideoPlayerMetadata?> =
    map { it.playerMetadata() }.distinctUntilChanged()
