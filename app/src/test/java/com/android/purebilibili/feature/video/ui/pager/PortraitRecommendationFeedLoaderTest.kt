package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.RelatedVideo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortraitRecommendationFeedLoaderTest {

    @Test
    fun `fetchPortraitRecommendationAppendBatch prioritizes story then falls back to home and related while filtering vertical`() = runTest {
        val storyCalls = mutableListOf<Int>()
        val homeCalls = mutableListOf<Int>()
        var relatedCalls = 0

        val result = fetchPortraitRecommendationAppendBatch(
            request = PortraitRecommendationBatchRequest(
                currentBvid = "BV_CUR",
                currentAid = 100L,
                startingStoryDisplayId = 2,
                startingHomeFeedCursor = 0,
                onlyVertical = true,
                minVerticalRatio = 1.2f,
                recommendationShuffleSeed = 42,
                existingBvids = setOf("BV_CUR", "BV_EXISTING"),
                existingRecommendations = emptyList(),
                precedingOwnerMid = 1L,
                targetBatchSize = 3,
            ),
            fetchStoryPage = { _, _, displayId ->
                storyCalls += displayId
                when (displayId) {
                    2 -> listOf(
                        video("BV_STORY_LAND", ownerMid = 10L, isVertical = false),
                        video("BV_STORY_VERT", ownerMid = 11L, isVertical = true),
                    )
                    else -> emptyList()
                }
            },
            fetchHomePage = { cursor ->
                homeCalls += cursor
                when (cursor) {
                    0 -> listOf(
                        video("BV_EXISTING", ownerMid = 12L, isVertical = true),
                        video("BV_HOME_VERT", ownerMid = 13L, isVertical = true),
                    )
                    else -> emptyList()
                }
            },
            fetchRelated = {
                relatedCalls += 1
                listOf(
                    video("BV_REL_LAND", ownerMid = 14L, isVertical = false),
                    video("BV_REL_VERT", ownerMid = 15L, isVertical = true),
                )
            },
            filterVertical = { items, enabled, _ ->
                if (!enabled) items else items.filter { it.isVertical == true }
            },
        )

        assertEquals(listOf(2, 3), storyCalls)
        assertEquals(listOf(0, 1), homeCalls)
        assertEquals(1, relatedCalls)
        assertEquals(4, result.nextStoryDisplayId)
        assertEquals(2, result.nextHomeFeedCursor)
        assertEquals(
            setOf("BV_STORY_VERT", "BV_HOME_VERT", "BV_REL_VERT"),
            result.appendItems.map { it.bvid }.toSet()
        )
        assertTrue(result.appendItems.none { it.bvid == "BV_EXISTING" || it.bvid == "BV_CUR" })
    }

    @Test
    fun `fetchPortraitRecommendationAppendBatch skips home and related when story already fills target batch`() = runTest {
        var homeCalled = false
        var relatedCalled = false

        val result = fetchPortraitRecommendationAppendBatch(
            request = PortraitRecommendationBatchRequest(
                currentBvid = "BV_CUR",
                currentAid = 100L,
                startingStoryDisplayId = 2,
                startingHomeFeedCursor = 5,
                onlyVertical = true,
                minVerticalRatio = 1.0f,
                recommendationShuffleSeed = 7,
                existingBvids = setOf("BV_CUR"),
                existingRecommendations = emptyList(),
                precedingOwnerMid = 0L,
                targetBatchSize = 2,
            ),
            fetchStoryPage = { _, _, _ ->
                listOf(
                    video("BV_S1", ownerMid = 21L, isVertical = true),
                    video("BV_S2", ownerMid = 22L, isVertical = true),
                )
            },
            fetchHomePage = {
                homeCalled = true
                emptyList()
            },
            fetchRelated = {
                relatedCalled = true
                emptyList()
            },
            filterVertical = { items, enabled, _ ->
                if (!enabled) items else items.filter { it.isVertical == true }
            },
        )

        assertEquals(false, homeCalled)
        assertEquals(false, relatedCalled)
        assertEquals(3, result.nextStoryDisplayId)
        assertEquals(5, result.nextHomeFeedCursor)
        assertEquals(setOf("BV_S1", "BV_S2"), result.appendItems.map { it.bvid }.toSet())
    }

    private fun video(bvid: String, ownerMid: Long, isVertical: Boolean) = RelatedVideo(
        bvid = bvid,
        aid = ownerMid * 10L,
        title = "Title $bvid",
        owner = Owner(mid = ownerMid, name = "UP $ownerMid"),
        isVertical = isVertical,
    )
}
