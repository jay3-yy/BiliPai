package com.android.purebilibili.feature.audio.library

import androidx.compose.runtime.Immutable
import com.android.purebilibili.data.model.response.FavFolderSource
import com.android.purebilibili.feature.video.player.PlaylistItem

@Immutable
internal data class ListenVideoTrack(
    val bvid: String,
    val cid: Long,
    val title: String,
    val coverUrl: String,
    val durationMs: Long,
    val artistId: Long,
    val artistName: String,
    val artistAvatarUrl: String
)

@Immutable
internal data class ListenVideoPlaylist(
    val mediaId: Long,
    val title: String,
    val coverUrl: String,
    val trackCount: Int,
    val source: FavFolderSource
)

@Immutable
internal data class ListenVideoAlbum(
    val seasonId: Long,
    val ownerMid: Long,
    val title: String,
    val coverUrl: String,
    val trackCount: Int,
    val artistName: String
)

@Immutable
internal data class ListenVideoArtist(
    val mid: Long,
    val name: String,
    val avatarUrl: String,
    val tracks: List<ListenVideoTrack>
)

@Immutable
internal data class ListenVideoPlaybackSelection(
    val items: List<PlaylistItem>,
    val startIndex: Int
)

internal enum class ListenVideoSection {
    PLAYLISTS,
    ALBUMS,
    ARTISTS
}
