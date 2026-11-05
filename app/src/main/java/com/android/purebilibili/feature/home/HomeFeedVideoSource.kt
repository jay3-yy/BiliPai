package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.plugin.sdk.FeedKind

internal data class HomeFeedVideoResponse(
    val videos: List<VideoItem>,
    val notice: String? = null,
)

/** 仅选择视频信息流接口，不持有页面状态、请求协程或账号缓存。 */
internal suspend fun loadHomeFeedVideos(
    category: HomeCategory,
    popularSubCategory: PopularSubCategory,
    pageToFetch: Int,
    recommendRequestIndex: Int,
    columns: Int,
    isLoadMore: Boolean,
    isManualRefresh: Boolean,
): Result<HomeFeedVideoResponse> {
    if (category == HomeCategory.RECOMMEND) {
        return VideoRepository.getHomeFeed(
            idx = recommendRequestIndex,
            refreshCount = resolveHomeFeedPageSize(columns),
            columns = columns,
            flush = resolveHomeFeedFlush(isLoadMore, isManualRefresh),
        ).map { HomeFeedVideoResponse(it.videos, it.notice) }
    }
    val videos = if (category == HomeCategory.POPULAR) {
        when (popularSubCategory) {
            PopularSubCategory.COMPREHENSIVE -> VideoRepository.getPopularVideos(pageToFetch)
            PopularSubCategory.RANKING -> VideoRepository.getRankingVideos(rid = 0, type = "all")
            PopularSubCategory.WEEKLY -> VideoRepository.getWeeklyMustWatchVideos()
            PopularSubCategory.PRECIOUS -> VideoRepository.getPreciousVideos()
        }
    } else if (category.tid > 0) {
        VideoRepository.getRegionVideos(tid = category.tid, page = pageToFetch)
    } else {
        Result.failure(Exception("Unknown category"))
    }
    return videos.map { HomeFeedVideoResponse(it) }
}

internal fun resolveRecommendFeedRequestIndex(
    isLoadMore: Boolean,
    isManualRefresh: Boolean,
    currentRefreshIndex: Int
): Int {
    return if (isLoadMore || isManualRefresh) {
        currentRefreshIndex + 1
    } else {
        0
    }
}

/**
 * 首页分类 → 插件信息流来源(供 FeedPlugin 按来源区分过滤行为)。
 */
internal fun HomeCategory.toFeedKind(popularSubCategory: PopularSubCategory? = null): FeedKind {
    return when (this) {
        HomeCategory.RECOMMEND -> FeedKind.HOME_RECOMMEND
        HomeCategory.POPULAR -> when (popularSubCategory) {
            PopularSubCategory.RANKING -> FeedKind.HOME_RANK
            else -> FeedKind.HOME_POPULAR
        }
        else -> FeedKind.HOME_REGION
    }
}

internal fun shouldAdvanceRecommendFeedRequestIndex(
    category: HomeCategory,
    isLoadMore: Boolean,
    isManualRefresh: Boolean,
    validVideoCount: Int
): Boolean {
    return category == HomeCategory.RECOMMEND &&
        (isLoadMore || isManualRefresh) &&
        validVideoCount > 0
}

/** 首页请求场景区分首次进入、下拉刷新与追加分页。 */
internal fun resolveHomeFeedFlush(isLoadMore: Boolean, isManualRefresh: Boolean): Int = when {
    isLoadMore -> com.android.purebilibili.data.repository.HOME_FEED_FLUSH_PULL_UP
    isManualRefresh -> com.android.purebilibili.data.repository.HOME_FEED_FLUSH_PULL_DOWN
    else -> com.android.purebilibili.data.repository.HOME_FEED_FLUSH_DEFAULT
}

internal fun resolveHomeFeedPageSize(columns: Int): Int {
    // 和请求里的 column 保持一致：统一按 7 列要量。
    // 7 × 3 + 2 = 23，服务端 ps 上限在 30 左右（实测 ps=50 返回 -400）。
    val requestColumns = com.android.purebilibili.data.repository.HOME_FEED_REQUEST_COLUMN
    return (requestColumns * 3 + 2).coerceIn(10, 30)
}
