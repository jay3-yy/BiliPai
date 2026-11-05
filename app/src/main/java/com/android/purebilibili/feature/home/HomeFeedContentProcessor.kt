package com.android.purebilibili.feature.home

import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.util.appendDistinctByKey
import com.android.purebilibili.core.util.prependDistinctByKey
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.plugin.sdk.FeedKind
import kotlinx.collections.immutable.toImmutableList

internal fun trimIncrementalRefreshVideosToEvenCount(videos: List<VideoItem>): List<VideoItem> {
    val size = videos.size
    if (size <= 1 || size % 2 == 0) return videos
    return videos.dropLast(1)
}

internal fun selectHomeFeedIncomingVideos(
    responseVideos: List<VideoItem>,
    currentVideos: List<VideoItem>,
    isLoadMore: Boolean,
    isIncrementalRefresh: Boolean,
): List<VideoItem> {
    val responseDistinct = responseVideos.distinctBy { it.bvid }
    val currentBvids = currentVideos.asSequence().map { it.bvid }.filter { it.isNotBlank() }.toHashSet()
    val outsideCurrentList = if (isLoadMore || isIncrementalRefresh) {
        responseDistinct.filter { it.bvid !in currentBvids }
    } else {
        responseDistinct
    }
    return if (isIncrementalRefresh) {
        trimIncrementalRefreshVideosToEvenCount(outsideCurrentList)
    } else {
        outsideCurrentList
    }
}

internal data class PreparedHomeFeedMerge(
    val incomingVideos: List<VideoItem>,
    val videos: kotlinx.collections.immutable.ImmutableList<VideoItem>,
    val addedCount: Int,
)

/** 在后台过滤增量刷新保留的旧卡片，再合并新内容；调用方负责发布前的列表身份检查。 */
internal suspend fun prepareHomeFeedMerge(
    currentVideos: List<VideoItem>,
    responseVideos: List<VideoItem>,
    isLoadMore: Boolean,
    isIncrementalRefresh: Boolean,
    keySelector: (VideoItem) -> String,
    requestVideos: List<VideoItem> = currentVideos,
    filterRetainedVideos: suspend (List<VideoItem>) -> List<VideoItem> = { it },
): PreparedHomeFeedMerge {
    val removedKeys = if (requestVideos === currentVideos) emptySet() else {
        val currentKeys = currentVideos.mapTo(HashSet(), keySelector)
        requestVideos.mapNotNullTo(HashSet()) { video ->
            keySelector(video).takeUnless { it in currentKeys }
        }
    }
    val eligibleResponse = if (removedKeys.isEmpty()) responseVideos else {
        responseVideos.filterNot { keySelector(it) in removedKeys }
    }
    val retainedVideos = if (isIncrementalRefresh) filterRetainedVideos(currentVideos) else currentVideos
    val incoming = selectHomeFeedIncomingVideos(
        eligibleResponse, retainedVideos, isLoadMore, isIncrementalRefresh,
    )
    var addedCount = 0
    val merged = when {
        isLoadMore -> appendDistinctByKey(currentVideos, incoming, keySelector)
        isIncrementalRefresh -> {
            val prepended = prependDistinctByKey(retainedVideos, incoming, keySelector)
            addedCount = (prepended.size - retainedVideos.size).coerceAtLeast(0)
            val keptOldCount = resolveHomeRefreshKeptOldItemCount(retainedVideos.size)
            if (keptOldCount < retainedVideos.size) prepended.take(addedCount + keptOldCount)
            else prepended
        }
        else -> incoming
    }
    return PreparedHomeFeedMerge(incoming, merged.toImmutableList(), addedCount)
}

internal data class PreparedHomeFeedVideos(
    val validVideoCount: Int,
    val filteredVideos: List<VideoItem>,
)

internal suspend fun prepareHomeFeedVideos(
    videos: List<VideoItem>,
    blockedMids: Set<Long>,
    feedKind: FeedKind,
    feedbackFilter: (List<VideoItem>) -> List<VideoItem>,
): PreparedHomeFeedVideos {
    val validVideos = videos.filter { it.bvid.isNotEmpty() && it.title.isNotEmpty() }
    val blockedFiltered = validVideos.filter { video -> video.owner.mid !in blockedMids }
    val feedbackFiltered = feedbackFilter(blockedFiltered)
    val builtinFiltered = PluginManager.filterFeedItems(feedbackFiltered, feedKind = feedKind)
    val filteredVideos = com.android.purebilibili.feature.plugin.filterVideoTagMatches(
        com.android.purebilibili.core.plugin.json.JsonPluginManager.filterVideos(builtinFiltered),
        bvid = { it.bvid },
        asVideoItem = { it },
    )
    return PreparedHomeFeedVideos(
        validVideoCount = validVideos.size,
        filteredVideos = filteredVideos,
    )
}

internal data class HomeRefreshUndoSnapshot(
    val videos: List<VideoItem>,
    val pageIndex: Int,
    val hasMore: Boolean
)

internal fun buildHomeRefreshUndoSnapshot(
    refreshingCategory: HomeCategory,
    recommendCategoryState: CategoryContent?,
    fallbackVideos: List<VideoItem>,
    maxItems: Int = 20
): HomeRefreshUndoSnapshot? {
    if (refreshingCategory != HomeCategory.RECOMMEND) return null
    val sourceVideos = recommendCategoryState?.videos ?: fallbackVideos
    if (sourceVideos.isEmpty()) return null
    val sourcePageIndex = recommendCategoryState?.pageIndex ?: 1
    val sourceHasMore = recommendCategoryState?.hasMore ?: true
    return HomeRefreshUndoSnapshot(
        videos = sourceVideos.take(maxItems.coerceAtLeast(1)),
        pageIndex = sourcePageIndex,
        hasMore = sourceHasMore
    )
}

internal fun shouldExposeHomeRefreshUndo(
    refreshingCategory: HomeCategory,
    snapshot: HomeRefreshUndoSnapshot?
): Boolean {
    return refreshingCategory == HomeCategory.RECOMMEND && snapshot != null
}

internal fun shouldRefreshHomeUserInfoAfterFeedLoad(isLoadMore: Boolean): Boolean {
    return !isLoadMore
}

internal fun applyHomeFeedLoadFailure(
    current: CategoryContent,
    isLoadMore: Boolean,
    message: String,
): CategoryContent {
    val hasContent = current.videos.isNotEmpty() || current.liveRooms.isNotEmpty() ||
        current.followedLiveRooms.isNotEmpty()
    return current.copy(
        isLoading = false,
        error = message.takeIf { !isLoadMore && !hasContent },
        loadMoreError = message.takeIf { isLoadMore },
        refreshError = message.takeIf { !isLoadMore && hasContent },
    )
}

internal fun applyHomeRefreshUndoSnapshot(
    oldState: CategoryContent,
    snapshot: HomeRefreshUndoSnapshot
): CategoryContent {
    return oldState.copy(
        videos = snapshot.videos.toImmutableList(),
        pageIndex = snapshot.pageIndex,
        hasMore = snapshot.hasMore,
        isLoading = false,
        error = null,
        loadMoreError = null,
        refreshError = null,
    )
}
