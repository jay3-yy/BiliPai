package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Releases mutations retained across fullscreen without resetting list offsets or the page. */
internal suspend fun stopVideoDetailInlineScrolls(
    introListState: ScrollableState,
    commentListState: ScrollableState,
    pagerState: ScrollableState,
): Unit = coroutineScope {
    // Default priority cannot interrupt a drag or tab animation running at UserInput priority.
    // Skip idle states: an unopened LazyListState/PagerState may still be awaiting its first layout.
    // This is input lifecycle recovery for every material; native layout and motion stay unchanged.
    if (introListState.isScrollInProgress) {
        launch { introListState.stopScroll(MutatePriority.PreventUserInput) }
    }
    if (commentListState.isScrollInProgress) {
        launch { commentListState.stopScroll(MutatePriority.PreventUserInput) }
    }
    if (pagerState.isScrollInProgress) {
        launch {
            pagerState.stopScroll(MutatePriority.PreventUserInput)
            (pagerState as? PagerState)?.let(::snapPagerToSettledPageIfMisaligned)
        }
    } else {
        (pagerState as? PagerState)?.let(::snapPagerToSettledPageIfMisaligned)
    }
}

/** 仅修正空闲态的半页错位，不改变已选页码。 */
internal fun snapPagerToSettledPageIfMisaligned(pagerState: PagerState) {
    if (pagerState.pageCount <= 0) return
    if (abs(pagerState.currentPageOffsetFraction) <= 0.001f) return
    pagerState.requestScrollToPage(
        page = pagerState.currentPage.coerceIn(0, pagerState.pageCount - 1),
        pageOffsetFraction = 0f,
    )
}
