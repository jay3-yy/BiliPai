package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HomeCategoryContentStateTest {
    @Test
    fun switchingCategoryProjectsItsCachedContentLoadingAndErrorTogether() {
        val loading = CategoryContent(videos = persistentListOf(video("following")), isLoading = true, error = "following error")
        val state = HomeUiState(
            videos = persistentListOf(video("recommend")),
            categoryStates = persistentMapOf(HomeCategory.FOLLOW to loading),
        ).selectHomeCategory(HomeCategory.FOLLOW)
        assertEquals(HomeCategory.FOLLOW, state.currentCategory)
        assertSame(loading.videos, state.videos)
        assertTrue(state.isLoading)
        assertEquals("following error", state.error)
    }

    @Test
    fun completingAnInactivePopularRequestDoesNotReplaceTheVisibleCategory() {
        val visible = CategoryContent(videos = persistentListOf(video("recommend")), isLoading = true)
        val state = HomeUiState(
            categoryStates = persistentMapOf(HomeCategory.RECOMMEND to visible),
            popularSubCategory = PopularSubCategory.RANKING,
        ).selectHomeCategory(HomeCategory.RECOMMEND)
        val updated = state.updateHomePopularContent(PopularSubCategory.RANKING) {
            it.copy(error = "ranking failed", isLoading = false)
        }
        assertSame(visible.videos, updated.videos)
        assertTrue(updated.isLoading)
        assertEquals(null, updated.error)
        val selected = updated.selectHomeCategory(HomeCategory.POPULAR)
        assertEquals("ranking failed", selected.error)
        assertFalse(selected.isLoading)
        assertEquals(selected.homeCategoryContent(HomeCategory.POPULAR), selected.categoryStates[HomeCategory.POPULAR])
    }

    @Test
    fun oldPopularSubcategoryCleanupDoesNotClearTheNewSubcategoryLoading() {
        val ranking = CategoryContent(isLoading = true)
        val state = HomeUiState(
            currentCategory = HomeCategory.POPULAR,
            popularSubCategory = PopularSubCategory.RANKING,
            popularCategoryStates = persistentMapOf(
                PopularSubCategory.COMPREHENSIVE to CategoryContent(isLoading = true),
                PopularSubCategory.RANKING to ranking,
            ),
        ).selectHomeCategory(HomeCategory.POPULAR)
        val updated = state.updateHomePopularContent(PopularSubCategory.COMPREHENSIVE) { it.copy(isLoading = false) }
        assertTrue(updated.isLoading)
        assertSame(ranking, updated.homeCategoryContent(HomeCategory.POPULAR))
        assertFalse(updated.homeCategoryContent(HomeCategory.POPULAR, PopularSubCategory.COMPREHENSIVE).isLoading)
    }

    @Test
    fun removingAPopularCardUpdatesItsCanonicalCacheAndSurvivesSwitchingBack() {
        val state = HomeUiState(
            currentCategory = HomeCategory.POPULAR,
            popularCategoryStates = persistentMapOf(
                PopularSubCategory.COMPREHENSIVE to CategoryContent(videos = persistentListOf(video("removed"))),
            ),
        )
        val updated = state.updateHomeCategoryContent(HomeCategory.POPULAR) { it.copy(videos = persistentListOf()) }
            .selectHomePopularSubCategory(PopularSubCategory.RANKING)
            .selectHomePopularSubCategory(PopularSubCategory.COMPREHENSIVE)
        assertTrue(updated.videos.isEmpty())
        assertTrue(updated.homeCategoryContent(HomeCategory.POPULAR).videos.isEmpty())
        assertTrue(updated.categoryStates.getValue(HomeCategory.POPULAR).videos.isEmpty())
    }

    @Test
    fun refilteringVisitsEachPopularCacheOnceAndCannotRestoreBlockedCardsOnTabSwitch() {
        val mixed = CategoryContent(videos = persistentListOf(video("blocked"), video("retained")))
        val state = HomeUiState(
            categoryStates = persistentMapOf(HomeCategory.POPULAR to mixed, HomeCategory.RECOMMEND to mixed),
            popularCategoryStates = PopularSubCategory.entries.associateWith { mixed }.toPersistentMap(),
        )
        val visited = mutableListOf<Pair<HomeCategory, PopularSubCategory?>>()
        val filtered = state.mapHomeCategoryContent { category, subCategory, content ->
            visited += category to subCategory
            content.copy(videos = content.videos.filterNot { it.bvid == "blocked" }.toImmutableList())
        }
        assertEquals(1 + PopularSubCategory.entries.size, visited.size)
        PopularSubCategory.entries.forEach { subCategory ->
            assertEquals(1, visited.count { it == (HomeCategory.POPULAR to subCategory) })
            val selected = filtered.selectHomePopularSubCategory(subCategory).selectHomeCategory(HomeCategory.POPULAR)
            assertEquals(listOf("retained"), selected.videos.map { it.bvid })
        }
    }

    private fun video(bvid: String) = VideoItem(bvid = bvid)
}
