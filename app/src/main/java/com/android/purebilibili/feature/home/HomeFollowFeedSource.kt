package com.android.purebilibili.feature.home

import com.android.purebilibili.data.repository.DynamicFeedFetchResult
import com.android.purebilibili.data.repository.DynamicFeedScope
import com.android.purebilibili.data.repository.DynamicRepository
import kotlinx.coroutines.CancellationException

internal data class HomeFollowFeedResponse(
    val feed: DynamicFeedFetchResult,
    val forceReplace: Boolean,
    val refreshUpdateNum: Int,
)

/** 关注视频流的基线探测与整页获取；不持有页面、账号状态或协程作用域。 */
internal class HomeFollowFeedSource(
    private val baseline: () -> String = {
        DynamicRepository.currentUpdateBaseline(scope = DynamicFeedScope.HOME_FOLLOW, type = "video")
    },
    private val loadFeed: suspend (refresh: Boolean, incremental: Boolean) -> Result<DynamicFeedFetchResult> = { refresh, incremental ->
        DynamicRepository.getDynamicFeed(
            refresh = refresh,
            scope = DynamicFeedScope.HOME_FOLLOW,
            type = "video",
            incrementalRefresh = incremental,
        )
    },
) {
    suspend fun load(
        isLoadMore: Boolean,
        isManualRefresh: Boolean,
        incrementalRefreshEnabled: Boolean,
        ensureCurrent: suspend () -> Unit,
    ): Result<HomeFollowFeedResponse> {
        ensureCurrent()
        val probeWithBaseline = isManualRefresh && !isLoadMore && baseline().isNotBlank()
        val result = loadFeed(
            !isLoadMore,
            probeWithBaseline || (!isLoadMore && incrementalRefreshEnabled),
        )
        ensureCurrent()
        val feed = result.getOrElse { error ->
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        val forceReplace = probeWithBaseline && shouldFullReplaceFollowFeedAfterBaselineProbe(
            incrementalRefreshEnabled = incrementalRefreshEnabled,
            apiUpdateNum = feed.updateNum,
        )
        if (!forceReplace) {
            return Result.success(HomeFollowFeedResponse(feed, false, feed.updateNum))
        }
        // 关闭增量刷新但探测到新内容时，以整页结果替换；提示数量仍取探测结果。
        val fullResult = loadFeed(true, false)
        ensureCurrent()
        val fullFeed = fullResult.getOrElse { error ->
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        return Result.success(HomeFollowFeedResponse(fullFeed, true, feed.updateNum))
    }

    fun hasMore(): Boolean = DynamicRepository.hasMoreData(scope = DynamicFeedScope.HOME_FOLLOW, type = "video")

    fun syncPagination(feed: DynamicFeedFetchResult) {
        DynamicRepository.syncPaginationAfterRefresh(
            scope = DynamicFeedScope.HOME_FOLLOW,
            type = "video",
            offset = feed.nextOffset,
            hasMore = feed.hasMore,
        )
    }
}
