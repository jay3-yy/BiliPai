package com.android.purebilibili.feature.home

import com.android.purebilibili.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTopCategoryPolicyTest {

    @Test
    fun `top categories should not contain anime`() {
        assertFalse(resolveHomeTopCategories().contains(HomeCategory.ANIME))
    }

    @Test
    fun `top categories keep stable primary order`() {
        assertEquals(
            listOf(
                HomeCategory.RECOMMEND,
                HomeCategory.FOLLOW,
                HomeCategory.POPULAR,
                HomeCategory.LIVE,
                HomeCategory.GAME
            ),
            resolveHomeTopCategories()
        )
    }

    @Test
    fun `top tab entries keep five default categories`() {
        assertEquals(
            listOf(
                HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
                HomeTopTabEntry.Category(HomeCategory.FOLLOW),
                HomeTopTabEntry.Category(HomeCategory.POPULAR),
                HomeTopTabEntry.Category(HomeCategory.LIVE),
                HomeTopTabEntry.Category(HomeCategory.GAME)
            ),
            resolveHomeTopTabEntries()
        )
        assertEquals(
            listOf("RECOMMEND", "FOLLOW", "POPULAR", "LIVE", "GAME"),
            resolveDefaultHomeTopTabIds()
        )
    }

    @Test
    fun `top categories should keep compact count for header readability`() {
        assertEquals(5, resolveHomeTopCategories().size)
    }

    @Test
    fun `tab index and category mapping should be consistent`() {
        val categories = resolveHomeTopCategories()
        categories.forEachIndexed { index, category ->
            assertEquals(index, resolveHomeTopTabIndex(category))
            assertEquals(category, resolveHomeCategoryForTopTab(index))
        }
    }

    @Test
    fun `tab entry key and label should support partition`() {
        val entries = resolveHomeTopTabEntries(
            customOrderIds = listOf("PARTITION"),
            visibleIds = setOf("PARTITION")
        )

        assertEquals(HomeTopTabEntry.Partition, resolveHomeTopTabEntryOrNull(entries, 0))
        assertEquals(HOME_TOP_PARTITION_TAB_ID, resolveHomeTopTabEntryKey(entries, 0))
        assertEquals("分区", resolveHomeTopTabEntryLabel(HomeTopTabEntry.Partition))
    }

    @Test
    fun `custom order and visibility should be applied without recommend pinning`() {
        val categories = resolveHomeTopCategories(
            customOrderIds = listOf("LIVE", "TECH", "RECOMMEND", "FOLLOW"),
            visibleIds = setOf("LIVE", "TECH", "FOLLOW")
        )

        assertEquals(
            listOf(
                HomeCategory.LIVE,
                HomeCategory.TECH,
                HomeCategory.FOLLOW
            ),
            categories
        )
    }

    @Test
    fun `custom top tab entries should allow partition or any category first`() {
        val entries = resolveHomeTopTabEntries(
            customOrderIds = listOf("PARTITION", "LIVE", "RECOMMEND"),
            visibleIds = setOf("PARTITION", "LIVE")
        )

        assertEquals(
            listOf(
                HomeTopTabEntry.Partition,
                HomeTopTabEntry.Category(HomeCategory.LIVE)
            ),
            entries
        )
    }

    @Test
    fun `legacy default top tab settings should keep five default categories`() {
        val entries = resolveHomeTopTabEntries(
            customOrderIds = listOf("RECOMMEND", "FOLLOW", "POPULAR", "LIVE", "GAME"),
            visibleIds = setOf("RECOMMEND", "FOLLOW", "POPULAR", "LIVE", "GAME")
        )

        assertEquals(
            listOf(
                HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
                HomeTopTabEntry.Category(HomeCategory.FOLLOW),
                HomeTopTabEntry.Category(HomeCategory.POPULAR),
                HomeTopTabEntry.Category(HomeCategory.LIVE),
                HomeTopTabEntry.Category(HomeCategory.GAME)
            ),
            entries
        )
    }

    @Test
    fun `invalid custom ids should fallback to default set`() {
        val categories = resolveHomeTopCategories(
            customOrderIds = listOf("UNKNOWN", "INVALID"),
            visibleIds = setOf("???")
        )

        assertTrue(categories.contains(HomeCategory.RECOMMEND))
        assertEquals(resolveHomeTopCategories(), categories)
    }

    @Test
    fun `safe category resolve should not crash on out of range index`() {
        val categories = listOf(
            HomeCategory.RECOMMEND,
            HomeCategory.FOLLOW,
            HomeCategory.POPULAR
        )

        assertEquals(HomeCategory.FOLLOW, resolveHomeTopCategoryOrNull(categories, 1))
        assertEquals(null, resolveHomeTopCategoryOrNull(categories, 5))
    }

    @Test
    fun `safe key resolve should fallback to index when out of range`() {
        val categories = listOf(
            HomeCategory.RECOMMEND,
            HomeCategory.FOLLOW
        )

        assertEquals(HomeCategory.RECOMMEND.ordinal, resolveHomeTopCategoryKey(categories, 0))
        assertEquals(5, resolveHomeTopCategoryKey(categories, 5))
    }

    @Test
    fun `home top categories should map to localized string resources`() {
        assertEquals(R.string.home_category_recommend, resolveHomeCategoryLabelRes(HomeCategory.RECOMMEND))
        assertEquals(R.string.home_category_follow, resolveHomeCategoryLabelRes(HomeCategory.FOLLOW))
        assertEquals(R.string.home_category_popular, resolveHomeCategoryLabelRes(HomeCategory.POPULAR))
        assertEquals(R.string.home_category_live, resolveHomeCategoryLabelRes(HomeCategory.LIVE))
        assertEquals(R.string.home_category_game, resolveHomeCategoryLabelRes(HomeCategory.GAME))
    }

    @Test
    fun `ensureSubscriptionHomeTab retains custom ordered subscription when enabled`() {
        val entries = listOf(
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
            HomeTopTabEntry.Subscriptions,
            HomeTopTabEntry.Category(HomeCategory.FOLLOW),
        )

        val result = ensureSubscriptionHomeTab(
            entries = entries,
            feedsEnabled = true,
            visibleIds = setOf("RECOMMEND", "SUBSCRIPTIONS", "FOLLOW")
        )

        assertEquals(entries, result)
    }

    @Test
    fun `ensureSubscriptionHomeTab strips subscription when feeds disabled`() {
        val entries = listOf(
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
            HomeTopTabEntry.Subscriptions,
            HomeTopTabEntry.Category(HomeCategory.FOLLOW),
        )

        val result = ensureSubscriptionHomeTab(
            entries = entries,
            feedsEnabled = false,
            visibleIds = setOf("RECOMMEND", "SUBSCRIPTIONS", "FOLLOW")
        )

        assertEquals(
            listOf(
                HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
                HomeTopTabEntry.Category(HomeCategory.FOLLOW)
            ),
            result
        )
    }

    @Test
    fun `ensureSubscriptionHomeTab appends subscription on legacy default when enabled`() {
        val entries = resolveHomeTopTabEntries()

        val result = ensureSubscriptionHomeTab(
            entries = entries,
            feedsEnabled = true,
            visibleIds = null
        )

        assertEquals(entries + HomeTopTabEntry.Subscriptions, result)
    }

    @Test
    fun `ensureSubscriptionHomeTab strips subscription if custom visibleIds excludes it`() {
        val entries = listOf(
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
            HomeTopTabEntry.Subscriptions,
            HomeTopTabEntry.Category(HomeCategory.LIVE)
        )

        val result = ensureSubscriptionHomeTab(
            entries = entries,
            feedsEnabled = true,
            visibleIds = setOf("RECOMMEND", "LIVE")
        )

        assertEquals(
            listOf(
                HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
                HomeTopTabEntry.Category(HomeCategory.LIVE)
            ),
            result
        )
    }
}
