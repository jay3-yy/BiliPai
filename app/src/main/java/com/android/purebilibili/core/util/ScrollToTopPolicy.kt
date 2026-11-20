package com.android.purebilibili.core.util

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ScrollToTopPlan(
    val preJumpIndex: Int?,
    val animateTargetIndex: Int = 0
)

/**
 * 长列表回顶策略：
 * - 近距离直接平滑到顶部
 * - 已知当前视口容量时，远距离先定位到约两屏外，再平滑到顶部
 * - fast = true 时（如底栏点击/重选回顶/双击回顶），先定位到约一个视口外，
 *   再完成最后一屏动画。窗口按实际可见项数计算，避免大屏多列时固定 2 项落在同一行，
 *   看起来只有一次硬跳和一小段卡顿动画
 * - 未提供视口容量的旧调用继续使用固定分段策略
 */
fun resolveScrollToTopPlan(
    firstVisibleItemIndex: Int,
    visibleItemCount: Int? = null,
    fast: Boolean = false,
): ScrollToTopPlan {
    val index = firstVisibleItemIndex.coerceAtLeast(0)
    val measuredViewportItems = visibleItemCount?.takeIf { it > 0 }
    val preJump = if (fast) {
        val fastWindow = measuredViewportItems?.coerceIn(4, 12) ?: 4
        fastWindow.takeIf { index > fastWindow }
    } else if (measuredViewportItems != null) {
        val viewportItems = measuredViewportItems.coerceIn(4, 16)
        val animatedWindowItems = (viewportItems * 2).coerceIn(8, 32)
        val directAnimationLimit = animatedWindowItems + viewportItems
        animatedWindowItems.takeIf { index > directAnimationLimit }
    } else {
        when {
            index > 180 -> 28
            index > 96 -> 20
            index > 36 -> 12
            index > 14 -> 6
            else -> null
        }
    }
    return ScrollToTopPlan(preJumpIndex = preJump)
}

fun resolveFastScrollToTopPlan(
    firstVisibleItemIndex: Int,
    visibleItemCount: Int? = null,
): ScrollToTopPlan = resolveScrollToTopPlan(
    firstVisibleItemIndex = firstVisibleItemIndex,
    visibleItemCount = visibleItemCount,
    fast = true,
)

fun shouldShowScrollToTop(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    offsetThresholdPx: Int = 600,
): Boolean {
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset >= offsetThresholdPx
}

suspend fun LazyListState.animateScrollToTop(fast: Boolean = false) {
    val plan = resolveScrollToTopPlan(
        firstVisibleItemIndex = firstVisibleItemIndex,
        visibleItemCount = layoutInfo.visibleItemsInfo.size,
        fast = fast,
    )
    plan.preJumpIndex?.let { scrollToItem(it) }
    animateScrollToItem(plan.animateTargetIndex)
}

suspend fun LazyGridState.animateScrollToTop(fast: Boolean = false) {
    val plan = resolveScrollToTopPlan(
        firstVisibleItemIndex = firstVisibleItemIndex,
        visibleItemCount = layoutInfo.visibleItemsInfo.size,
        fast = fast,
    )
    plan.preJumpIndex?.let { scrollToItem(it) }
    animateScrollToItem(plan.animateTargetIndex)
}

suspend fun LazyStaggeredGridState.animateScrollToTop(fast: Boolean = false) {
    val plan = resolveScrollToTopPlan(
        firstVisibleItemIndex = firstVisibleItemIndex,
        visibleItemCount = layoutInfo.visibleItemsInfo.size,
        fast = fast,
    )
    plan.preJumpIndex?.let { scrollToItem(it) }
    animateScrollToItem(plan.animateTargetIndex)
}

/** A drag can cancel the scroll mutation without stopping the caller's request collector. */
internal suspend fun animateScrollWhileActive(
    isActive: () -> Boolean,
    scroll: suspend () -> Unit,
) = coroutineScope {
    if (!isActive()) return@coroutineScope false
    val animation = launch { scroll() }
    val visibility = launch {
        snapshotFlow(isActive).first { !it }
        animation.cancel()
    }
    try {
        animation.join()
        !animation.isCancelled
    } finally {
        visibility.cancel()
    }
}
