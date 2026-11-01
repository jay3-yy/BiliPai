package com.android.purebilibili.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedAlignedRowsPolicyTest {

    @Test
    fun homeOldContentLocatorCountsVideoItemsAndEveryFullWidthHeader() {
        assertEquals(13, resolveHomeOldContentGridItemIndex(8, 30, dividerIndex = 8, headerItemCount = 4))
        assertEquals(7, resolveHomeOldContentGridItemIndex(3, 30, dividerIndex = 8, headerItemCount = 4))
        assertEquals(12, resolveHomeOldContentGridItemIndex(8, 30, dividerIndex = null, headerItemCount = 4))
    }

    @Test
    fun truncatedCards_advanceInCompleteRows() {
        assertEquals(
            listOf(0..3, 4..7, 8..9),
            resolveHomeFeedAlignedRows(itemCount = 10, columns = 4),
        )
    }

    @Test
    fun oldContentDivider_startsAFreshAlignedRow() {
        assertEquals(
            listOf(0..3, 4..5, 6..9),
            resolveHomeFeedAlignedRows(itemCount = 10, columns = 4, dividerIndex = 6),
        )
    }

    @Test
    fun emptyFeed_hasNoRows() {
        assertEquals(
            emptyList<IntRange>(),
            resolveHomeFeedAlignedRows(itemCount = 0, columns = 4),
        )
    }
}
