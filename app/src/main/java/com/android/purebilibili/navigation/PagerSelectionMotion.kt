package com.android.purebilibili.navigation

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import kotlin.math.abs

/** A weak key keeps removed pagers collectible; sessions never hold their pager. */
private val tabSelectionMotions = java.util.WeakHashMap<PagerState, androidx.compose.runtime.MutableState<TabSelectionMotion?>>()

private class TabSelectionMotion(val target: Int, start: Float) {
    var position by androidx.compose.runtime.mutableFloatStateOf(start)
}

private fun tabSelectionState(pager: PagerState) = tabSelectionMotions.getOrPut(pager) {
    androidx.compose.runtime.mutableStateOf(null)
}

internal val PagerState.isTabSelectionInProgress: Boolean
    get() = tabSelectionState(this).value != null

/** Selection semantics and label colors use the clicked destination, never a pre-jump page. */
internal val PagerState.tabSelectedPage: Int
    get() = (tabSelectionState(this).value?.target ?: currentPage).coerceIn(0, (pageCount - 1).coerceAtLeast(0))

/** Clicks animate the rail independently; touch scrolling always uses the actual page offset. */
internal val PagerState.tabIndicatorPosition: Float
    get() = tabSelectionState(this).value?.position ?: (currentPage + currentPageOffsetFraction)

/**
 * Click navigation lets Pager skip distant intermediate pages while the rail moves continuously.
 * Both animations share a spec and lifetime; interruption releases the rail to the touch position.
 * This changes motion ownership only, across MD3, Miuix and glass, without changing their geometry.
 */
internal suspend fun animatePagerSelection(
    pagerState: PagerState,
    targetPage: Int,
    animationSpec: FiniteAnimationSpec<Float>? = null,
) {
    val lastPage = pagerState.pageCount - 1
    if (lastPage < 0) return
    val target = targetPage.coerceIn(0, lastPage)
    val state = tabSelectionState(pagerState)
    // Selection state and click callbacks can request the same destination in the same frame.
    if (state.value?.target == target) return
    if (target == pagerState.currentPage && abs(pagerState.currentPageOffsetFraction) <= 0.001f && state.value == null) return
    val start = pagerState.tabIndicatorPosition
    val motion = TabSelectionMotion(target, start)
    val spec = animationSpec ?: tween<Float>(durationMillis = 300, easing = EaseInOut)
    state.value = motion
    try {
        kotlinx.coroutines.coroutineScope {
            val indicator = launch {
                animate(start, target.toFloat(), animationSpec = spec) { value, _ ->
                    motion.position = value
                }
            }
            // The framework pre-jumps near distant destinations instead of composing every page.
            pagerState.animateScrollToPage(target, animationSpec = spec)
            indicator.join()
        }
    } finally {
        // An interrupted old coroutine cannot clear a newer click's rail animation.
        if (state.value === motion) state.value = null
    }
}
