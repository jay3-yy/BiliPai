package com.android.purebilibili.feature.watchlater

import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.video.player.PlaylistItem

internal enum class WatchLaterManagementAction {
    CLEAR_INVALID,
    CLEAR_VIEWED,
    CLEAR_ALL
}

enum class WatchLaterFilter(val viewed: Int, val label: String) {
    ALL(0, "全部"),
    UNFINISHED(2, "未看完"),
}

/** The server-provided order is the default order for the Watch Later list. */
enum class WatchLaterSortOrder(val label: String) {
    FORWARD("最近添加"),
    REVERSE("最早添加"),
    DURATION_ASC("时长最短"),
    DURATION_DESC("时长最长"),
    PUBDATE_DESC("投稿最新"),
    VIEW_DESC("播放最多");

    val requiresServerAscending: Boolean
        get() = this == REVERSE

    fun toggled(): WatchLaterSortOrder = when (this) {
        FORWARD -> REVERSE
        else -> FORWARD
    }

    companion object {
        fun fromSavedValue(value: String): WatchLaterSortOrder {
            return entries.firstOrNull { it.name == value } ?: FORWARD
        }
    }
}

data class WatchLaterExternalPlaylist(
    val playlistItems: List<PlaylistItem>,
    val startIndex: Int
)

internal data class WatchLaterPlaybackTarget(
    val bvid: String,
    val cid: Long,
    val resumePositionMs: Long
)

internal fun sortWatchLaterItems(
    items: List<VideoItem>,
    order: WatchLaterSortOrder
): List<VideoItem> {
    return when (order) {
        WatchLaterSortOrder.FORWARD -> items
        WatchLaterSortOrder.REVERSE -> items.asReversed()
        WatchLaterSortOrder.DURATION_ASC -> items.sortedBy { it.duration }
        WatchLaterSortOrder.DURATION_DESC -> items.sortedByDescending { it.duration }
        WatchLaterSortOrder.PUBDATE_DESC -> items.sortedByDescending { it.pubdate }
        WatchLaterSortOrder.VIEW_DESC -> items.sortedByDescending { it.stat.view }
    }
}

/** 服务端已按 FORWARD/REVERSE 返回顺序，界面仅对附加维度做本地排序。 */
internal fun applyWatchLaterDisplaySort(
    items: List<VideoItem>,
    order: WatchLaterSortOrder
): List<VideoItem> {
    return when (order) {
        WatchLaterSortOrder.FORWARD,
        WatchLaterSortOrder.REVERSE -> items
        else -> sortWatchLaterItems(items, order)
    }
}

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
            ownerFace = video.owner.face,
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

internal fun resolveWatchLaterPlaybackTarget(
    items: List<VideoItem>,
    clickedBvid: String
): WatchLaterPlaybackTarget? {
    if (clickedBvid.isBlank()) return null
    val item = items.firstOrNull { it.bvid == clickedBvid } ?: return null
    return WatchLaterPlaybackTarget(
        bvid = item.bvid,
        cid = item.cid.coerceAtLeast(0L),
        resumePositionMs = item.progress
            .takeIf { it > 0 }
            ?.toLong()
            ?.times(1000L)
            ?: 0L
    )
}

internal fun isWatchLaterViewed(item: VideoItem): Boolean {
    return com.android.purebilibili.data.model.isWatchLaterViewed(item)
}

internal fun resolveWatchLaterItemsAfterManagementAction(
    items: List<VideoItem>,
    action: WatchLaterManagementAction
): List<VideoItem> {
    return when (action) {
        WatchLaterManagementAction.CLEAR_INVALID -> items
        WatchLaterManagementAction.CLEAR_VIEWED -> items.filterNot(::isWatchLaterViewed)
        WatchLaterManagementAction.CLEAR_ALL -> emptyList()
    }
}

internal fun resolveWatchLaterManagementConfirmText(
    action: WatchLaterManagementAction,
    affectedCount: Int
): String {
    return when (action) {
        WatchLaterManagementAction.CLEAR_INVALID ->
            "确认清理已失效的视频吗？列表会按服务端结果刷新。"
        WatchLaterManagementAction.CLEAR_VIEWED ->
            "确认清空已看完的视频吗？本地预计影响 $affectedCount 个，实际以服务端记录为准。"
        WatchLaterManagementAction.CLEAR_ALL ->
            "确认清空全部稍后再看吗？本操作会移除当前账号的全部稍后再看视频。"
    }
}

internal fun resolveWatchLaterManagementSuccessMessage(
    action: WatchLaterManagementAction,
    affectedCount: Int
): String {
    return when (action) {
        WatchLaterManagementAction.CLEAR_INVALID -> "已清理失效视频"
        WatchLaterManagementAction.CLEAR_VIEWED ->
            if (affectedCount > 0) "已清理 $affectedCount 个已看视频" else "已请求清理已看视频"
        WatchLaterManagementAction.CLEAR_ALL -> "已清空稍后再看"
    }
}
