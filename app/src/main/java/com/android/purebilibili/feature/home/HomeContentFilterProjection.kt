package com.android.purebilibili.feature.home

import com.android.purebilibili.core.filter.ContentListProjection
import com.android.purebilibili.core.filter.contentFiltered
import com.android.purebilibili.core.filter.keepsVideo
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal fun StateFlow<HomeUiState>.filteredHomeContent(scope: CoroutineScope): StateFlow<HomeUiState> {
    val videos = mutableMapOf<Pair<HomeCategory, PopularSubCategory?>, ContentListProjection<VideoItem>>()
    val todayQueue = ContentListProjection<VideoItem>()
    return contentFiltered(scope) { state, rules ->
        val projected = state.mapHomeCategoryContent { category, popular, content ->
            val cache = videos.getOrPut(category to popular) { ContentListProjection() }
            content.copy(
                videos = cache.filter(content.videos, rules) {
                    rules.keepsVideo(it, category.toFeedKind(popular))
                }.toImmutableList(),
                liveRooms = content.liveRooms.filterNot { rules.blocksUser(it.uid) }.toImmutableList(),
                followedLiveRooms = content.followedLiveRooms.filterNot { rules.blocksUser(it.uid) }.toImmutableList(),
            )
        }
        projected.copy(todayWatchPlan = state.todayWatchPlan?.let { plan ->
            plan.copy(
                videoQueue = todayQueue.filter(plan.videoQueue, rules) {
                    rules.keepsVideo(it, com.android.purebilibili.plugin.sdk.FeedKind.HOME_RECOMMEND)
                }.toImmutableList(),
                upRanks = plan.upRanks.filterNot { rules.blocksUser(it.mid) }.toImmutableList(),
            )
        })
    }
}
