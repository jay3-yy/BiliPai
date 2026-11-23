package com.android.purebilibili.feature.audio.library

import com.android.purebilibili.data.model.response.FavFolder
import com.android.purebilibili.data.model.response.FavFolderSource
import com.android.purebilibili.data.model.response.FavoriteData
import com.android.purebilibili.feature.video.player.PlaylistItem

internal fun mapListenVideoPlaylists(folders: List<FavFolder>): List<ListenVideoPlaylist> {
    return folders.mapNotNull { folder ->
        if (
            folder.source != FavFolderSource.OWNED ||
            folder.id <= 0L ||
            folder.title.isBlank()
        ) {
            return@mapNotNull null
        }
        ListenVideoPlaylist(
            mediaId = folder.id,
            title = folder.title.trim(),
            coverUrl = folder.cover.trim(),
            trackCount = folder.media_count.coerceAtLeast(0),
            source = folder.source
        )
    }.distinctBy(ListenVideoPlaylist::mediaId)
}

internal fun mapListenVideoAlbums(
    collectedFolders: List<FavFolder>,
    resources: List<FavoriteData>
): List<ListenVideoAlbum> {
    val folderAlbums = collectedFolders.mapNotNull { folder ->
        if (folder.type != 21 || folder.id <= 0L || folder.title.isBlank()) {
            return@mapNotNull null
        }
        ListenVideoAlbum(
            seasonId = folder.id,
            ownerMid = folder.upper?.mid?.takeIf { it > 0L } ?: folder.mid.coerceAtLeast(0L),
            title = folder.title.trim(),
            coverUrl = folder.cover.trim(),
            trackCount = folder.media_count.coerceAtLeast(0),
            artistName = folder.upper?.name.orEmpty().trim()
        )
    }
    val resourceAlbums = resources.mapNotNull { resource ->
        if (resource.type != 21 || resource.title.isBlank()) {
            return@mapNotNull null
        }
        val seasonId = resource.season_id.takeIf { it > 0L }
            ?: resource.id.takeIf { it > 0L }
            ?: return@mapNotNull null
        ListenVideoAlbum(
            seasonId = seasonId,
            ownerMid = resource.upper?.mid?.coerceAtLeast(0L) ?: 0L,
            title = resource.title.trim(),
            coverUrl = resource.cover.trim(),
            trackCount = resource.media_count.coerceAtLeast(0),
            artistName = resource.upper?.name.orEmpty().trim()
        )
    }
    return (folderAlbums + resourceAlbums).distinctBy(ListenVideoAlbum::seasonId)
}

internal fun FavoriteData.toListenVideoTrackOrNull(): ListenVideoTrack? {
    if (type == 21) return null
    val resolvedBvid = bvid.ifBlank { bv_id }.trim()
    val artist = upper ?: return null
    if (resolvedBvid.isBlank() || artist.mid <= 0L) return null
    return ListenVideoTrack(
        bvid = resolvedBvid,
        cid = ugc?.first_cid?.coerceAtLeast(0L) ?: 0L,
        title = title.trim().ifBlank { resolvedBvid },
        coverUrl = cover.trim(),
        durationMs = duration.coerceAtLeast(0) * 1_000L,
        artistId = artist.mid,
        artistName = artist.name.trim().ifBlank { "UP主${artist.mid}" },
        artistAvatarUrl = artist.face.trim()
    )
}

internal fun mapListenVideoArtists(resources: List<FavoriteData>): List<ListenVideoArtist> {
    return resources
        .mapNotNull(FavoriteData::toListenVideoTrackOrNull)
        .distinctBy(ListenVideoTrack::bvid)
        .groupBy(ListenVideoTrack::artistId)
        .map { (mid, tracks) ->
            val first = tracks.first()
            ListenVideoArtist(
                mid = mid,
                name = first.artistName,
                avatarUrl = first.artistAvatarUrl,
                tracks = tracks
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

internal fun resolveListenVideoPlaybackSelection(
    tracks: List<ListenVideoTrack>,
    clickedBvid: String
): ListenVideoPlaybackSelection {
    val playableTracks = tracks
        .filter { it.bvid.isNotBlank() }
        .distinctBy(ListenVideoTrack::bvid)
    val items = playableTracks.map { track ->
        PlaylistItem(
            bvid = track.bvid,
            cid = track.cid,
            title = track.title,
            cover = track.coverUrl,
            owner = track.artistName,
            ownerFace = track.artistAvatarUrl,
            duration = track.durationMs / 1_000L
        )
    }
    val clickedIndex = playableTracks.indexOfFirst { it.bvid == clickedBvid }
    return ListenVideoPlaybackSelection(
        items = items,
        startIndex = when {
            items.isEmpty() -> -1
            clickedIndex >= 0 -> clickedIndex
            else -> 0
        }
    )
}

internal fun filterListenVideoPlaylists(
    playlists: List<ListenVideoPlaylist>,
    query: String,
): List<ListenVideoPlaylist> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return playlists
    return playlists.filter { it.title.contains(normalized, ignoreCase = true) }
}

internal fun filterListenVideoAlbums(
    albums: List<ListenVideoAlbum>,
    query: String,
): List<ListenVideoAlbum> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return albums
    return albums.filter { album ->
        album.title.contains(normalized, ignoreCase = true) ||
            album.artistName.contains(normalized, ignoreCase = true)
    }
}

internal fun filterListenVideoArtists(
    artists: List<ListenVideoArtist>,
    query: String,
): List<ListenVideoArtist> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return artists
    return artists.filter { artist ->
        artist.name.contains(normalized, ignoreCase = true) ||
            artist.tracks.any { track -> track.title.contains(normalized, ignoreCase = true) }
    }
}
