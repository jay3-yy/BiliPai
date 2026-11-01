package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HomeCoverLoadRangeTest {
    @Test
    fun windowIncludesOneRowOnBothSides() {
        assertEquals(6..15, resolveHomeCoverLoadRange(8, 13, 100, 2, false))
        assertEquals(4..19, resolveHomeCoverLoadRange(8, 15, 100, 4, false))
    }

    @Test
    fun windowClampsToFeedEdges() {
        assertEquals(0..5, resolveHomeCoverLoadRange(0, 3, 100, 2, false))
        assertEquals(94..99, resolveHomeCoverLoadRange(96, 99, 100, 2, false))
    }

    @Test
    fun dataSaverOnlyAllowsVisibleCards() {
        assertEquals(8..13, resolveHomeCoverLoadRange(8, 13, 100, 2, true))
    }

    @Test
    fun headersAndUnknownLayoutDoNotInventAVideoWindow() {
        assertNull(resolveHomeCoverLoadRange(null, null, 100, 2, false))
        assertNull(resolveHomeCoverLoadRange(0, 0, 0, 2, false))
        assertNull(resolveHomeCoverLoadRange(10, 8, 100, 2, false))
    }

    @Test
    fun individualCardsPreloadAfterTheLastVisibleVideo() {
        val keys = resolveHomeCategoryVideoGridKeys(List(30) { VideoItem(bvid = "BV$it") })
        val ranges = resolveHomeFeedVideoRangesByLazyKey(keys)
        val visible = resolveHomeVisibleVideoRange(setOf(keys[8], keys[9], "home_hero_carousel"), ranges)!!
        assertEquals(8..9, visible)
        assertEquals(10..11, resolveHomeCoverPreloadRange(false, false, visible.last, keys.size, 2))
    }

    @Test
    fun waterfallUsesDisplayedOrderAfterCarouselItemsAreExcluded() {
        val displayed = listOf(8, 2, 12, 4, 9).map { VideoItem(bvid = "BV$it") }
        val keys = resolveHomeCategoryVideoGridKeys(displayed)
        val ranges = resolveHomeFeedVideoRangesByLazyKey(keys)
        val visible = resolveHomeVisibleVideoRange(setOf(keys[2], keys[0]), ranges)!!

        assertEquals(0..2, visible)
        val preload = resolveHomeCoverPreloadRange(false, false, visible.last, displayed.size, 2)!!
        assertEquals(listOf("BV4", "BV9"), preload.map { displayed[it].bvid })
        assertNull(resolveHomeVisibleVideoRange(setOf("home_hero_carousel", "feed_chrome_top_inset"), ranges))
    }
}
