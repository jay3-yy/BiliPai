package com.android.purebilibili.core.filter

import com.android.purebilibili.core.store.ContentFilterHistory
import com.android.purebilibili.core.store.ContentFilterHit
import com.android.purebilibili.core.store.ContentFilterReason
import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.core.store.ContentVideoSurface
import com.android.purebilibili.core.store.FilteredContentKind
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.plugin.sdk.FeedKind

internal fun ContentFilterSnapshot.keepsVideo(item: VideoItem, kind: FeedKind): Boolean = keepsVideo(
    bvid = item.bvid,
    aid = item.aid.takeIf { it > 0L } ?: item.id,
    title = item.title,
    mid = item.owner.mid,
    author = item.owner.name,
    followed = item.isFollowed,
    surface = when (kind) {
        FeedKind.HOME_RECOMMEND -> ContentVideoSurface.RECOMMEND
        FeedKind.HOME_POPULAR -> ContentVideoSurface.POPULAR
        FeedKind.HOME_RANK -> ContentVideoSurface.RANK
        FeedKind.SEARCH -> ContentVideoSurface.SEARCH
        else -> ContentVideoSurface.OTHER
    },
)

internal fun ContentFilterSnapshot.keepsVideo(
    bvid: String, aid: Long, title: String, mid: Long, author: String,
    surface: ContentVideoSurface, followed: Boolean = false,
): Boolean {
    val reason = videoFilterReason(title, mid, followed, surface) ?: return true
    recordFilteredVideo(bvid, aid, title, author, reason)
    return false
}

internal fun recordFilteredVideo(item: VideoItem, reason: ContentFilterReason, pluginName: String = "") {
    recordFilteredVideo(item.bvid, item.aid.takeIf { it > 0L } ?: item.id, item.title, item.owner.name, reason, pluginName)
}

private fun recordFilteredVideo(
    bvid: String, aid: Long, title: String, author: String, reason: ContentFilterReason, pluginName: String = "",
) {
    val id = when {
        bvid.isNotBlank() -> bvid
        aid > 0L -> "av$aid"
        title.isBlank() -> return
        else -> "$author\u0000$title"
    }
    ContentFilterHistory.record(FilteredContentKind.VIDEO, ContentFilterHit(id, title, author, reason, pluginName))
}
