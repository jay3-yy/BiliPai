package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.plugin.VideoTagFilter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeFeedBackgroundMergeTest {
    private fun videos(vararg ids: String) = ids.map { VideoItem(bvid = it) }

    @Test
    fun appendKeepsOrderAndRemovesCrossPageDuplicates() = runTest {
        val result = prepareHomeFeedMerge(
            videos("A", "B"), videos("B", "C", "C", "D"),
            isLoadMore = true, isIncrementalRefresh = false, keySelector = { it.bvid },
        )
        assertEquals(listOf("A", "B", "C", "D"), result.videos.map { it.bvid })
        assertEquals(listOf("C", "D"), result.incomingVideos.map { it.bvid })
    }

    @Test
    fun rebaseDoesNotRestoreACardRemovedWhileRequestWasRunning() = runTest {
        val result = prepareHomeFeedMerge(
            currentVideos = videos("B"), responseVideos = videos("A", "C"),
            isLoadMore = true, isIncrementalRefresh = false, keySelector = { it.bvid },
            requestVideos = videos("A", "B"),
        )
        assertEquals(listOf("B", "C"), result.videos.map { it.bvid })
    }

    @Test
    fun incrementalRefreshPreservesEvenBatchAndAddedCount() = runTest {
        val result = prepareHomeFeedMerge(
            videos("A", "B"), videos("B", "C", "D", "E"),
            isLoadMore = false, isIncrementalRefresh = true, keySelector = { it.bvid },
        )
        assertEquals(listOf("C", "D", "A", "B"), result.videos.map { it.bvid })
        assertEquals(2, result.addedCount)
    }

    @Test
    fun replacementRefreshKeepsExistingSelectionRules() = runTest {
        val result = prepareHomeFeedMerge(
            videos("A"), videos("A", "B", "B"),
            isLoadMore = false, isIncrementalRefresh = false, keySelector = { it.bvid },
        )
        assertEquals(listOf("A", "B"), result.videos.map { it.bvid })
    }

    @Test
    fun incrementalRefreshAppliesTagRulesToRetainedCardsAndCountsOnlyNewCards() = runTest {
        val tagFilter = VideoTagFilter(loadTags = { id ->
            if (id == "blocked") listOf("剧透") else listOf("日常")
        })
        val filter: suspend (List<VideoItem>) -> List<VideoItem> = { items ->
            tagFilter.filter(items, listOf("剧透"), VideoItem::bvid)
        }
        val result = prepareHomeFeedMerge(
            currentVideos = videos("blocked", "retained"),
            responseVideos = filter(videos("blocked", "new1", "new2")),
            isLoadMore = false,
            isIncrementalRefresh = true,
            keySelector = { it.bvid },
            filterRetainedVideos = filter,
        )
        assertEquals(listOf("new1", "new2", "retained"), result.videos.map { it.bvid })
        assertEquals(2, result.addedCount)
    }
}
