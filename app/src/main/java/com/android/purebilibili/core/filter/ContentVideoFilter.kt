package com.android.purebilibili.core.filter

import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.core.store.ContentVideoSurface
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.plugin.sdk.FeedKind

internal fun ContentFilterSnapshot.keepsVideo(item: VideoItem, kind: FeedKind): Boolean = !blocksVideo(
    item.title, item.owner.mid, item.isFollowed,
    when (kind) {
        FeedKind.HOME_RECOMMEND -> ContentVideoSurface.RECOMMEND
        FeedKind.HOME_POPULAR -> ContentVideoSurface.POPULAR
        FeedKind.HOME_RANK -> ContentVideoSurface.RANK
        FeedKind.SEARCH -> ContentVideoSurface.SEARCH
        else -> ContentVideoSurface.OTHER
    },
)
