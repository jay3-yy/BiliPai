package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.core.plugin.BuiltinPluginIds
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.toVideoItem
import com.android.purebilibili.feature.plugin.filterVideoTagMatches
import com.android.purebilibili.plugin.sdk.FeedKind

/** 保留推荐接口的尺寸信息与条目身份，同时复用视频过滤规则及过滤后的顺序。 */
internal fun filterRelatedVideoCandidates(
    items: List<RelatedVideo>,
    filter: (List<VideoItem>) -> List<VideoItem>,
): List<RelatedVideo> {
    val originals = items.associateBy { it.aid to it.bvid }
    return filter(items.map { it.toVideoItem() }).mapNotNull { originals[it.aid to it.bvid] }
}

internal suspend fun filterRelatedVideoRecommendations(items: List<RelatedVideo>): List<RelatedVideo> {
    PluginManager.awaitPluginReady(BuiltinPluginIds.AD_FILTER)
    PluginManager.awaitPluginReady(BuiltinPluginIds.FEED_FILTER)
    com.android.purebilibili.core.network.NetworkModule.appContext?.let {
        com.android.purebilibili.core.store.ContentFilterRules.snapshot(it)
    }
    val filtered = filterRelatedVideoCandidates(items) {
        PluginManager.filterFeedItemsBeforeContentProjection(it, feedKind = FeedKind.GENERIC)
    }
    return filterVideoTagMatches(filtered, bvid = { it.bvid }, asVideoItem = { it.toVideoItem() })
}
