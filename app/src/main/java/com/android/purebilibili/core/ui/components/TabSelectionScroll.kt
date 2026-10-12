package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlin.math.roundToInt

internal fun resolveTabSelectionLeadingSpacePx(itemWidthPx: Float, viewportWidthPx: Float): Int =
    ((viewportWidthPx - itemWidthPx).coerceAtLeast(0f) / 2f).roundToInt()

internal fun resolveTabSelectionScrollOffsetPx(
    focusPosition: Float,
    itemWidthPx: Float,
    viewportWidthPx: Float,
    maxScrollPx: Int,
    contentPaddingPx: Float = 0f,
): Int {
    if (!focusPosition.isFinite() || focusPosition <= 0f || itemWidthPx <= 0f || viewportWidthPx <= 0f) {
        return 0
    }
    return (contentPaddingPx + focusPosition * itemWidthPx -
        resolveTabSelectionLeadingSpacePx(itemWidthPx, viewportWidthPx))
        .roundToInt()
        .coerceIn(0, maxScrollPx.coerceAtLeast(0))
}

internal enum class TabSelectionRailScrollMode {
    /** Indicator is mid-glide: lock-step absolute scroll so the rail tracks continuously. */
    LOCK_STEP,
    /** Idle selection/geometry change: one animated center hop. */
    ANIMATE,
    /** Idle without entrance animation. */
    INSTANT,
}

internal fun resolveTabSelectionRailScrollMode(
    continuousFollow: Boolean,
    entranceAnimationEnabled: Boolean,
): TabSelectionRailScrollMode = when {
    continuousFollow -> TabSelectionRailScrollMode.LOCK_STEP
    entranceAnimationEnabled -> TabSelectionRailScrollMode.ANIMATE
    else -> TabSelectionRailScrollMode.INSTANT
}

/** Keeps a continuously moving indicator inside the visible rail during a long drag. */
internal fun resolveScrollableTabIndicatorFollowDeltaPx(
    indicatorPosition: Float,
    itemWidthPx: Float,
    viewportWidthPx: Float,
    currentScrollPx: Float,
    contentPaddingPx: Float = 0f,
    edgePaddingPx: Float = 0f,
): Float {
    if (!indicatorPosition.isFinite() || !currentScrollPx.isFinite() ||
        itemWidthPx <= 0f || viewportWidthPx <= 0f
    ) {
        return 0f
    }
    val indicatorLeftPx = contentPaddingPx + indicatorPosition * itemWidthPx - currentScrollPx
    val indicatorRightPx = indicatorLeftPx + itemWidthPx
    return when {
        indicatorLeftPx < edgePaddingPx -> indicatorLeftPx - edgePaddingPx
        indicatorRightPx > viewportWidthPx - edgePaddingPx ->
            indicatorRightPx - (viewportWidthPx - edgePaddingPx)
        else -> 0f
    }
}

/**
 * Selection and viewport changes move the rail; manual scrolling does not re-trigger it.
 *
 * [focusPosition] is the continuous rail focus (tab index + fraction). While [continuousFollow]
 * is true the rail lock-steps to that focus so it glides with the indicator; when idle it performs
 * a single center hop (animated when entrance animation is on). With [centerSelection] disabled,
 * it preserves whole-slot alignment and scrolls only far enough to expose the selected tab.
 */
@Composable
internal fun KeepScrollableTabSelectionVisible(
    scrollState: ScrollState,
    selectedIndex: Int,
    itemWidthPx: Float,
    contentPaddingPx: Float = 0f,
    focusPosition: () -> Float = { selectedIndex.toFloat() },
    continuousFollow: () -> Boolean = { false },
    centerSelection: Boolean = true,
) {
    val initialSelectionPositioned = remember(scrollState) { object { var value = false } }
    val entranceAnimationEnabled = LocalAppThemeConfig.current.uiEntranceAnimationEnabled
    val focusPositionLatest by rememberUpdatedState(focusPosition)
    val continuousFollowLatest by rememberUpdatedState(continuousFollow)
    LaunchedEffect(scrollState, itemWidthPx, contentPaddingPx, entranceAnimationEnabled, centerSelection) {
        // maxValue is unknown before the scroll container is measured. Also follow resizes
        // without restarting this effect on every animation frame or fighting a manual swipe.
        snapshotFlow {
            MeasuredTabRailFocus(
                scrollState.maxValue, scrollState.viewportSize,
                focusPositionLatest(), continuousFollowLatest(),
            )
        }
            .filter { it.maxScrollPx != Int.MAX_VALUE && it.viewportWidthPx > 0 }
            .collectLatest { (maxScrollPx, measuredViewportPx, focus, continuous) ->
                val centeredTarget = resolveTabSelectionScrollOffsetPx(
                    focus, itemWidthPx, measuredViewportPx.toFloat(), maxScrollPx, contentPaddingPx,
                )
                val target = if (centerSelection || itemWidthPx <= 0f || !focus.isFinite()) {
                    centeredTarget
                } else {
                    // Keep whole slots at both viewport edges. Read the manual scroll
                    // offset here, outside snapshotFlow, so swiping remains user-owned.
                    val visibleCount = kotlin.math.floor(
                        ((measuredViewportPx - contentPaddingPx * 2) / itemWidthPx + 0.001f).toDouble(),
                    ).toInt().coerceAtLeast(1)
                    val first = (scrollState.value / itemWidthPx).roundToInt()
                    val selected = focus.roundToInt().coerceAtLeast(0)
                    val start = when {
                        selected < first -> selected
                        selected >= first + visibleCount -> selected - visibleCount + 1
                        else -> first
                    }
                    (start * itemWidthPx).roundToInt().coerceIn(0, maxScrollPx)
                }
                when (
                    resolveTabSelectionRailScrollMode(
                        continuousFollow = continuous,
                        entranceAnimationEnabled = entranceAnimationEnabled && initialSelectionPositioned.value,
                    )
                ) {
                    TabSelectionRailScrollMode.LOCK_STEP -> scrollState.scrollTo(target)
                    TabSelectionRailScrollMode.ANIMATE -> scrollState.animateScrollTo(target)
                    TabSelectionRailScrollMode.INSTANT -> scrollState.scrollTo(target)
                }
                initialSelectionPositioned.value = true
            }
    }
}

/** Home's equal-width LazyRow uses the same centered selection, clamped by LazyListState. */
@Composable
internal fun KeepLazyTabSelectionVisible(
    listState: LazyListState,
    selectedIndex: Int,
) {
    val animate = LocalAppThemeConfig.current.uiEntranceAnimationEnabled
    LaunchedEffect(listState, selectedIndex, animate) {
        snapshotFlow {
            val info = listState.layoutInfo
            // Observe geometry, not offsets: dragging the row must remain under user control.
            TabRailGeometry(
                viewportWidthPx = info.viewportSize.width,
                itemWidthPx = info.visibleItemsInfo.firstOrNull()?.size ?: 0,
                contentPaddingPx = info.beforeContentPadding,
                itemCount = info.totalItemsCount,
            )
        }.filter { it.viewportWidthPx > 0 && it.itemWidthPx > 0 && it.itemCount > 0 }
            .collectLatest { geometry ->
                if (!listState.canScrollBackward && !listState.canScrollForward) return@collectLatest
                val target = selectedIndex.coerceIn(0, geometry.itemCount - 1)
                val offset = geometry.contentPaddingPx - resolveTabSelectionLeadingSpacePx(
                    geometry.itemWidthPx.toFloat(), geometry.viewportWidthPx.toFloat(),
                )
                if (animate) {
                    listState.animateScrollToItem(target, scrollOffset = offset)
                } else {
                    listState.scrollToItem(target, scrollOffset = offset)
                }
            }
    }
}

private data class TabRailGeometry(
    val viewportWidthPx: Int,
    val itemWidthPx: Int,
    val contentPaddingPx: Int,
    val itemCount: Int,
)

private data class MeasuredTabRailFocus(
    val maxScrollPx: Int,
    val viewportWidthPx: Int,
    val position: Float,
    val continuous: Boolean,
)
