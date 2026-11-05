package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeRefreshPolicyTest {

    @Test
    fun normalRefreshOnlyDeduplicatesWithinResponse() {
        val output = selectHomeFeedIncomingVideos(
            responseVideos = listOf(
                VideoItem(bvid = "BV1"),
                VideoItem(bvid = "BV1"),
                VideoItem(bvid = "BV2"),
            ),
            currentVideos = listOf(VideoItem(bvid = "BV1")),
            isLoadMore = false,
            isIncrementalRefresh = false,
        )

        assertEquals(listOf("BV1", "BV2"), output.map { it.bvid })
    }

    @Test
    fun loadMoreExcludesOnlyVideosAlreadyInCurrentList() {
        val output = selectHomeFeedIncomingVideos(
            responseVideos = listOf(
                VideoItem(bvid = "BV1"),
                VideoItem(bvid = "BV2"),
                VideoItem(bvid = "BV3"),
            ),
            currentVideos = listOf(VideoItem(bvid = "BV1")),
            isLoadMore = true,
            isIncrementalRefresh = false,
        )

        assertEquals(listOf("BV2", "BV3"), output.map { it.bvid })
    }

    @Test
    fun incrementalRefreshExcludesCurrentListAndKeepsEvenBatch() {
        val output = selectHomeFeedIncomingVideos(
            responseVideos = listOf(
                VideoItem(bvid = "BV1"),
                VideoItem(bvid = "BV2"),
                VideoItem(bvid = "BV3"),
                VideoItem(bvid = "BV4"),
            ),
            currentVideos = listOf(VideoItem(bvid = "BV1")),
            isLoadMore = false,
            isIncrementalRefresh = true,
        )

        assertEquals(listOf("BV2", "BV3"), output.map { it.bvid })
    }

    @Test
    fun trimIncrementalRefreshVideosToEvenCount_keepsSingleItem() {
        val input = listOf(VideoItem(bvid = "BV1"))

        val output = trimIncrementalRefreshVideosToEvenCount(input)

        assertEquals(listOf("BV1"), output.map { it.bvid })
    }

    @Test
    fun trimIncrementalRefreshVideosToEvenCount_dropsLastWhenOddAndMoreThanOne() {
        val input = listOf(
            VideoItem(bvid = "BV1"),
            VideoItem(bvid = "BV2"),
            VideoItem(bvid = "BV3")
        )

        val output = trimIncrementalRefreshVideosToEvenCount(input)

        assertEquals(listOf("BV1", "BV2"), output.map { it.bvid })
    }

    @Test
    fun trimIncrementalRefreshVideosToEvenCount_keepsAllWhenEven() {
        val input = listOf(
            VideoItem(bvid = "BV1"),
            VideoItem(bvid = "BV2"),
            VideoItem(bvid = "BV3"),
            VideoItem(bvid = "BV4")
        )

        val output = trimIncrementalRefreshVideosToEvenCount(input)

        assertEquals(listOf("BV1", "BV2", "BV3", "BV4"), output.map { it.bvid })
    }

    @Test
    fun resolveRecommendFeedRequestIndex_advancesForManualRefreshAndLoadMore() {
        assertEquals(
            0,
            resolveRecommendFeedRequestIndex(
                isLoadMore = false,
                isManualRefresh = false,
                currentRefreshIndex = 0
            )
        )
        assertEquals(
            4,
            resolveRecommendFeedRequestIndex(
                isLoadMore = false,
                isManualRefresh = true,
                currentRefreshIndex = 3
            )
        )
        assertEquals(
            4,
            resolveRecommendFeedRequestIndex(
                isLoadMore = true,
                isManualRefresh = false,
                currentRefreshIndex = 3
            )
        )
    }

    @Test
    fun resolvePagedFeedPageToFetch_advancesOnManualRefreshForRegionStyleFeeds() {
        assertEquals(
            1,
            resolvePagedFeedPageToFetch(
                isLoadMore = false,
                isManualRefresh = false,
                currentPageIndex = 3,
                advanceOnManualRefresh = true
            )
        )
        assertEquals(
            4,
            resolvePagedFeedPageToFetch(
                isLoadMore = false,
                isManualRefresh = true,
                currentPageIndex = 3,
                advanceOnManualRefresh = true
            )
        )
        assertEquals(
            1,
            resolvePagedFeedPageToFetch(
                isLoadMore = false,
                isManualRefresh = true,
                currentPageIndex = 3,
                advanceOnManualRefresh = false
            )
        )
        assertEquals(
            4,
            resolvePagedFeedPageToFetch(
                isLoadMore = true,
                isManualRefresh = false,
                currentPageIndex = 3,
                advanceOnManualRefresh = true
            )
        )
    }

    @Test
    fun shouldAdvancePagedFeedOnManualRefresh_forRegionAndPopularComprehensiveOnly() {
        assertTrue(
            shouldAdvancePagedFeedOnManualRefresh(
                category = HomeCategory.GAME,
                popularSubCategory = PopularSubCategory.COMPREHENSIVE
            )
        )
        assertTrue(
            shouldAdvancePagedFeedOnManualRefresh(
                category = HomeCategory.POPULAR,
                popularSubCategory = PopularSubCategory.COMPREHENSIVE
            )
        )
        assertFalse(
            shouldAdvancePagedFeedOnManualRefresh(
                category = HomeCategory.POPULAR,
                popularSubCategory = PopularSubCategory.RANKING
            )
        )
        assertFalse(
            shouldAdvancePagedFeedOnManualRefresh(
                category = HomeCategory.RECOMMEND,
                popularSubCategory = PopularSubCategory.COMPREHENSIVE
            )
        )
        assertFalse(
            shouldAdvancePagedFeedOnManualRefresh(
                category = HomeCategory.FOLLOW,
                popularSubCategory = PopularSubCategory.COMPREHENSIVE
            )
        )
    }

    @Test
    fun resolvePagedFeedPageIndexAfterFetch_keepsAdvancedPageAndWrapsOnEmpty() {
        assertEquals(
            2,
            resolvePagedFeedPageIndexAfterFetch(
                isLoadMore = false,
                isManualRefresh = true,
                advanceOnManualRefresh = true,
                pageToFetch = 2,
                incomingCount = 8,
                previousPageIndex = 1
            )
        )
        assertEquals(
            0,
            resolvePagedFeedPageIndexAfterFetch(
                isLoadMore = false,
                isManualRefresh = true,
                advanceOnManualRefresh = true,
                pageToFetch = 5,
                incomingCount = 0,
                previousPageIndex = 4
            )
        )
        assertEquals(
            1,
            resolvePagedFeedPageIndexAfterFetch(
                isLoadMore = false,
                isManualRefresh = true,
                advanceOnManualRefresh = false,
                pageToFetch = 1,
                incomingCount = 8,
                previousPageIndex = 3
            )
        )
        assertEquals(
            4,
            resolvePagedFeedPageIndexAfterFetch(
                isLoadMore = true,
                isManualRefresh = false,
                advanceOnManualRefresh = true,
                pageToFetch = 4,
                incomingCount = 8,
                previousPageIndex = 3
            )
        )
    }

    @Test
    fun resolveHomeFollowRefreshNewItemsCount_usesApiUpdateNumOnlyWithBaseline() {
        assertEquals(
            null,
            resolveHomeFollowRefreshNewItemsCount(
                usedUpdateBaseline = false,
                apiUpdateNum = 0,
                insertedVideoCount = 0
            )
        )
        assertEquals(
            0,
            resolveHomeFollowRefreshNewItemsCount(
                usedUpdateBaseline = true,
                apiUpdateNum = 0,
                insertedVideoCount = 0
            )
        )
        assertEquals(
            2,
            resolveHomeFollowRefreshNewItemsCount(
                usedUpdateBaseline = true,
                apiUpdateNum = 5,
                insertedVideoCount = 2
            )
        )
        assertEquals(
            0,
            resolveHomeFollowRefreshNewItemsCount(
                usedUpdateBaseline = true,
                apiUpdateNum = 3,
                insertedVideoCount = 0
            )
        )
    }

    @Test
    fun shouldFullReplaceFollowFeedAfterBaselineProbe_onlyWhenIncrementalDisabledAndHasUpdates() {
        assertTrue(
            shouldFullReplaceFollowFeedAfterBaselineProbe(
                incrementalRefreshEnabled = false,
                apiUpdateNum = 3
            )
        )
        assertFalse(
            shouldFullReplaceFollowFeedAfterBaselineProbe(
                incrementalRefreshEnabled = true,
                apiUpdateNum = 3
            )
        )
        assertFalse(
            shouldFullReplaceFollowFeedAfterBaselineProbe(
                incrementalRefreshEnabled = false,
                apiUpdateNum = 0
            )
        )
    }

    @Test
    fun shouldAdvanceRecommendFeedRequestIndex_whenRecommendRequestReturnedAnyValidVideo() {
        assertTrue(
            shouldAdvanceRecommendFeedRequestIndex(
                category = HomeCategory.RECOMMEND,
                isLoadMore = false,
                isManualRefresh = true,
                validVideoCount = 8
            )
        )
        assertTrue(
            shouldAdvanceRecommendFeedRequestIndex(
                category = HomeCategory.RECOMMEND,
                isLoadMore = true,
                isManualRefresh = false,
                validVideoCount = 8
            )
        )
        assertFalse(
            shouldAdvanceRecommendFeedRequestIndex(
                category = HomeCategory.RECOMMEND,
                isLoadMore = false,
                isManualRefresh = true,
                validVideoCount = 0
            )
        )
        assertFalse(
            shouldAdvanceRecommendFeedRequestIndex(
                category = HomeCategory.POPULAR,
                isLoadMore = false,
                isManualRefresh = true,
                validVideoCount = 8
            )
        )
    }

    @Test
    fun shouldHandleRefreshNewItemsEvent_requiresPositiveAndGreaterKey() {
        assertFalse(shouldHandleRefreshNewItemsEvent(refreshKey = 0L, handledKey = 0L))
        assertFalse(shouldHandleRefreshNewItemsEvent(refreshKey = 10L, handledKey = 10L))
        assertFalse(shouldHandleRefreshNewItemsEvent(refreshKey = 9L, handledKey = 10L))
        assertTrue(shouldHandleRefreshNewItemsEvent(refreshKey = 11L, handledKey = 10L))
    }

    @Test
    fun buildHomeRefreshUndoSnapshot_returnsNull_forNonRecommendCategory() {
        val snapshot = buildHomeRefreshUndoSnapshot(
            refreshingCategory = HomeCategory.POPULAR,
            recommendCategoryState = CategoryContent(
                videos = listOf(VideoItem(bvid = "BV1")).toImmutableList(),
                pageIndex = 3,
                hasMore = false
            ),
            fallbackVideos = listOf(VideoItem(bvid = "BV2")).toImmutableList()
        )

        assertEquals(null, snapshot)
    }

    @Test
    fun buildHomeRefreshUndoSnapshot_returnsNull_whenRecommendListEmpty() {
        val snapshot = buildHomeRefreshUndoSnapshot(
            refreshingCategory = HomeCategory.RECOMMEND,
            recommendCategoryState = CategoryContent(videos = persistentListOf()),
            fallbackVideos = persistentListOf()
        )

        assertEquals(null, snapshot)
    }

    @Test
    fun buildHomeRefreshUndoSnapshot_limitsItemsAndPreservesPagingState() {
        val videos = (1..25).map { index -> VideoItem(bvid = "BV$index") }
        val snapshot = buildHomeRefreshUndoSnapshot(
            refreshingCategory = HomeCategory.RECOMMEND,
            recommendCategoryState = CategoryContent(
                videos = videos.toImmutableList(),
                pageIndex = 4,
                hasMore = false
            ),
            fallbackVideos = persistentListOf()
        )

        assertEquals(20, snapshot?.videos?.size)
        assertEquals("BV1", snapshot?.videos?.firstOrNull()?.bvid)
        assertEquals("BV20", snapshot?.videos?.lastOrNull()?.bvid)
        assertEquals(4, snapshot?.pageIndex)
        assertFalse(snapshot?.hasMore ?: true)
    }

    @Test
    fun applyHomeRefreshUndoSnapshot_restoresVideosAndPagingState() {
        val oldState = CategoryContent(
            videos = listOf(VideoItem(bvid = "NEW")).toImmutableList(),
            pageIndex = 8,
            hasMore = true
        )
        val snapshot = HomeRefreshUndoSnapshot(
            videos = listOf(VideoItem(bvid = "OLD-1"), VideoItem(bvid = "OLD-2")),
            pageIndex = 2,
            hasMore = false
        )

        val restored = applyHomeRefreshUndoSnapshot(oldState, snapshot)

        assertEquals(listOf("OLD-1", "OLD-2"), restored.videos.map { it.bvid })
        assertEquals(2, restored.pageIndex)
        assertFalse(restored.hasMore)
    }

    @Test
    fun shouldExposeHomeRefreshUndo_onlyWhenRecommendAndSnapshotExists() {
        assertFalse(
            shouldExposeHomeRefreshUndo(
                refreshingCategory = HomeCategory.POPULAR,
                snapshot = HomeRefreshUndoSnapshot(videos = listOf(VideoItem(bvid = "BV1")), pageIndex = 1, hasMore = true)
            )
        )
        assertFalse(
            shouldExposeHomeRefreshUndo(
                refreshingCategory = HomeCategory.RECOMMEND,
                snapshot = null
            )
        )
        assertTrue(
            shouldExposeHomeRefreshUndo(
                refreshingCategory = HomeCategory.RECOMMEND,
                snapshot = HomeRefreshUndoSnapshot(videos = listOf(VideoItem(bvid = "BV1")), pageIndex = 1, hasMore = true)
            )
        )
    }

    @Test
    fun shouldShowRecommendOldContentDivider_requiresRevealForCurrentRefresh() {
        assertFalse(
            shouldShowRecommendOldContentDivider(
                currentCategory = HomeCategory.RECOMMEND,
                refreshNewItemsKey = 12L,
                revealedRefreshKey = 0L,
                anchorBvid = "BV1",
                oldContentStartIndex = 3
            )
        )
        assertFalse(
            shouldShowRecommendOldContentDivider(
                currentCategory = HomeCategory.RECOMMEND,
                refreshNewItemsKey = 12L,
                revealedRefreshKey = 11L,
                anchorBvid = "BV1",
                oldContentStartIndex = 3
            )
        )
        assertTrue(
            shouldShowRecommendOldContentDivider(
                currentCategory = HomeCategory.RECOMMEND,
                refreshNewItemsKey = 12L,
                revealedRefreshKey = 12L,
                anchorBvid = "BV1",
                oldContentStartIndex = 3
            )
        )
        assertFalse(
            shouldShowRecommendOldContentDivider(
                currentCategory = HomeCategory.POPULAR,
                refreshNewItemsKey = 12L,
                revealedRefreshKey = 12L,
                anchorBvid = "BV1",
                oldContentStartIndex = 3
            )
        )
    }

    @Test
    fun shouldShowRecommendOldContentDivider_respectsRefreshTipVisibleToggle() {
        assertFalse(
            shouldShowRecommendOldContentDivider(
                currentCategory = HomeCategory.RECOMMEND,
                refreshNewItemsKey = 12L,
                revealedRefreshKey = 12L,
                anchorBvid = "BV1",
                oldContentStartIndex = 3,
                refreshTipVisible = false
            )
        )
        assertTrue(
            shouldShowRecommendOldContentDivider(
                currentCategory = HomeCategory.RECOMMEND,
                refreshNewItemsKey = 12L,
                revealedRefreshKey = 12L,
                anchorBvid = "BV1",
                oldContentStartIndex = 3,
                refreshTipVisible = true
            )
        )
    }

    @Test
    fun resolveHomeRefreshKeptOldItemCount_capsLongOldContent() {
        assertEquals(30, resolveHomeRefreshKeptOldItemCount(30))
        assertEquals(200, resolveHomeRefreshKeptOldItemCount(200))
        assertEquals(50, resolveHomeRefreshKeptOldItemCount(201))
        assertEquals(50, resolveHomeRefreshKeptOldItemCount(500))
        assertEquals(80, resolveHomeRefreshKeptOldItemCount(500, threshold = 200, keepCount = 80))
    }

    @Test
    fun oldContentDividerContainerIsTranslucentPrimaryTint() {
        val lightScheme = androidx.compose.material3.lightColorScheme(
            primaryContainer = androidx.compose.ui.graphics.Color(0xFFEADDFF),
        )
        val color = resolveOldContentDividerContainerColor(lightScheme)

        assertEquals(lightScheme.primaryContainer, color.copy(alpha = 1f))
        assertEquals(OLD_CONTENT_DIVIDER_CONTAINER_ALPHA, color.alpha)
        // 半透明是刻意的：让模糊壁纸背景透出，与毛玻璃卡片观感一致。
        assertTrue(color.alpha < 1f)
    }
}
