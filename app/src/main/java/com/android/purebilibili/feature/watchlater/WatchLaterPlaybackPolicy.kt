package com.android.purebilibili.feature.watchlater

import com.android.purebilibili.core.util.PinyinUtils
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.video.player.PlaylistItem

data class WatchLaterExternalPlaylist(
    val playlistItems: List<PlaylistItem>,
    val startIndex: Int
)

data class WatchLaterPlaybackVisualState(
    val isCompleted: Boolean,
    val durationBadgeText: String? = null
)

fun buildExternalPlaylistFromWatchLater(
    items: List<VideoItem>,
    clickedBvid: String? = null
): WatchLaterExternalPlaylist? {
    if (items.isEmpty()) return null

    val playlistItems = items.map { video ->
        PlaylistItem(
            bvid = video.bvid,
            title = video.title,
            cover = video.pic,
            owner = video.owner.name,
            duration = video.duration.toLong()
        )
    }

    val startIndex = clickedBvid
        ?.takeIf { it.isNotBlank() }
        ?.let { bvid -> items.indexOfFirst { it.bvid == bvid }.takeIf { it >= 0 } }
        ?: 0

    return WatchLaterExternalPlaylist(
        playlistItems = playlistItems,
        startIndex = startIndex
    )
}

fun filterWatchLaterItemsByQuery(
    items: List<VideoItem>,
    query: String
): List<VideoItem> {
    if (query.isBlank()) return items
    return items.filter { item ->
        PinyinUtils.matches(item.title, query) ||
            PinyinUtils.matches(item.owner.name, query)
    }
}

fun resolveWatchLaterPlaybackVisualState(
    video: VideoItem,
    localPositionMs: Long = 0L
): WatchLaterPlaybackVisualState {
    val progressState = if (video.progress == -1) {
        WatchLaterPlaybackVisualState(
            isCompleted = true,
            durationBadgeText = "已看完"
        )
    } else {
        val localProgressSec = if (video.duration > 0) {
            ((localPositionMs / 1000L).toInt()).coerceIn(0, video.duration)
        } else {
            0
        }
        val resolvedProgressSec = maxOf(video.progress.coerceAtLeast(0), localProgressSec)
        val durationBadgeText = when {
            resolvedProgressSec > 0 && video.duration > 0 -> {
                val percent = ((resolvedProgressSec.toFloat() / video.duration.toFloat()) * 100f)
                    .toInt()
                    .coerceIn(1, 99)
                "已播放 ${percent}%"
            }
            else -> null
        }
        WatchLaterPlaybackVisualState(
            isCompleted = false,
            durationBadgeText = durationBadgeText
        )
    }
    return progressState
}
