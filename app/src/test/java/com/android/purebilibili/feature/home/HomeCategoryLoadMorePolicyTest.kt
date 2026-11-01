package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeCategoryLoadMorePolicyTest {

    @Test
    fun largeGridStartsLoadingTwoRowsBeforeTheEnd() {
        assertFalse(shouldRequestHomeCategoryLoadMore(100, 85, false, true, true, preloadItemCount = 14))
        assertTrue(shouldRequestHomeCategoryLoadMore(100, 86, false, true, true, preloadItemCount = 14))
        assertFalse(shouldRequestHomeCategoryLoadMore(100, 86, true, true, true, preloadItemCount = 14))
    }

    @Test
    fun emptyPlaceholderOnlyPageDoesNotRequestLoadMore() {
        assertFalse(
            shouldRequestHomeCategoryLoadMore(
                totalItems = 2,
                lastVisibleItemIndex = 1,
                isLoading = false,
                hasMore = true,
                hasVisibleContent = false
            )
        )
    }

    @Test
    fun populatedPageRequestsLoadMoreNearEnd() {
        assertTrue(
            shouldRequestHomeCategoryLoadMore(
                totalItems = 12,
                lastVisibleItemIndex = 9,
                isLoading = false,
                hasMore = true,
                hasVisibleContent = true
            )
        )
    }
}
