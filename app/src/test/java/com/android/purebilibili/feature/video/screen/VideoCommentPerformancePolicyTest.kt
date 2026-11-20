package com.android.purebilibili.feature.video.screen

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCommentPerformancePolicyTest {

    @Test
    fun `comment list should load more only when user scrolls near the end`() {
        assertFalse(
            shouldLoadMoreVideoComments(
                lastVisibleItemIndex = 5,
                totalItemsCount = 20,
                isLoading = false,
                isEnd = false
            )
        )
        assertTrue(
            shouldLoadMoreVideoComments(
                lastVisibleItemIndex = 17,
                totalItemsCount = 20,
                isLoading = false,
                isEnd = false
            )
        )
    }

    @Test
    fun `comment list should not load more while already loading or ended`() {
        assertFalse(
            shouldLoadMoreVideoComments(
                lastVisibleItemIndex = 19,
                totalItemsCount = 20,
                isLoading = true,
                isEnd = false
            )
        )
        assertFalse(
            shouldLoadMoreVideoComments(
                lastVisibleItemIndex = 19,
                totalItemsCount = 20,
                isLoading = false,
                isEnd = true
            )
        )
    }

    @Test
    fun `lightweight comment rendering is enabled only for visible comment tab during playback`() {
        assertTrue(
            shouldUseLightweightCommentRendering(
                selectedTabIndex = 1,
                isVideoPlaying = true
            )
        )
        assertFalse(
            shouldUseLightweightCommentRendering(
                selectedTabIndex = 0,
                isVideoPlaying = true
            )
        )
        assertFalse(
            shouldUseLightweightCommentRendering(
                selectedTabIndex = 1,
                isVideoPlaying = false
            )
        )
    }

    @Test
    fun `lightweight comment rendering also applies while comment list is scrolling`() {
        assertTrue(
            shouldUseLightweightCommentRendering(
                selectedTabIndex = 1,
                isVideoPlaying = false,
                isCommentListScrolling = true
            )
        )
        assertFalse(
            shouldUseLightweightCommentRendering(
                selectedTabIndex = 1,
                isVideoPlaying = false,
                isCommentListScrolling = false
            )
        )
    }
}
