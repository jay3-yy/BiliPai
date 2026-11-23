package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.ScrollBarAdapter
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import kotlin.math.abs

@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AppLazyVerticalStaggeredGrid(
    columns: StaggeredGridCells,
    modifier: Modifier = Modifier,
    state: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    verticalItemSpacing: Dp = 0.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(0.dp),
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    overscrollEffect: OverscrollEffect? = rememberOverscrollEffect(),
    content: LazyStaggeredGridScope.() -> Unit,
) {
    Box(modifier, propagateMinConstraints = true) {
        CompositionLocalProvider(
            LocalListAuxiliaryWorkAllowed provides
                (LocalListAuxiliaryWorkAllowed.current && !state.isScrollInProgress),
        ) {
            LazyVerticalStaggeredGrid(
                modifier = if (userScrollEnabled) {
                    Modifier.appScrollBoundaryHaptics(state, reverseLayout)
                } else Modifier,
                columns = columns, state = state, contentPadding = contentPadding,
                reverseLayout = reverseLayout, verticalItemSpacing = verticalItemSpacing,
                horizontalArrangement = horizontalArrangement, flingBehavior = flingBehavior,
                userScrollEnabled = userScrollEnabled, overscrollEffect = overscrollEffect, content = content,
            )
        }
        if (LocalAppUiStyle.current == AppUiStyle.MIUIX && userScrollEnabled && LocalScrollBarAllowed.current) {
            val adapter = remember(state) { StaggeredGridScrollBarAdapter(state) }
            Box(Modifier.matchParentSize()) {
                VerticalScrollBar(
                    adapter = adapter,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    trackPadding = contentPadding, reverseLayout = reverseLayout,
                )
            }
        }
    }
}

/**
 * Miuix has no staggered-grid adapter. Estimate the thumb from rendered lanes, and seek only
 * within loaded items. Short drags scroll by pixels; long drags jump by index. Unequal card
 * heights mean the thumb is approximate, as with other lazy lists' estimated content sizes.
 * Layout information is read by the scrollbar, never by the screen's composition.
 */
@OptIn(ExperimentalScrollBarApi::class)
private class StaggeredGridScrollBarAdapter(private val state: LazyStaggeredGridState) : ScrollBarAdapter {
    private val laneCount: Int
        get() = state.layoutInfo.visibleItemsInfo.map { it.offset.x }.distinct().size.coerceAtLeast(1)
    private val itemAdvance: Double
        get() {
            val layout = state.layoutInfo
            val items = layout.visibleItemsInfo
            val height = if (items.isEmpty()) 1.0 else items.sumOf { it.size.height.toDouble() } / items.size
            return ((height + layout.mainAxisItemSpacing) / laneCount).coerceAtLeast(1.0)
        }
    override val viewportSize: Double get() = state.layoutInfo.viewportSize.height.toDouble()
    override val contentSize: Double
        get() {
            if (!state.canScrollBackward && !state.canScrollForward) return viewportSize
            val layout = state.layoutInfo
            return (layout.totalItemsCount * itemAdvance + layout.beforeContentPadding + layout.afterContentPadding)
                .coerceAtLeast(viewportSize + 1.0)
        }
    override val scrollOffset: Double
        get() {
            val maxOffset = (contentSize - viewportSize).coerceAtLeast(0.0)
            if (!state.canScrollBackward) return 0.0
            if (!state.canScrollForward) return maxOffset
            return (state.firstVisibleItemIndex * itemAdvance + state.firstVisibleItemScrollOffset.toDouble())
                .coerceIn(0.0, maxOffset)
        }
    override suspend fun scrollTo(scrollOffset: Double) {
        val count = state.layoutInfo.totalItemsCount
        if (count == 0) return
        val maxOffset = (contentSize - viewportSize).coerceAtLeast(0.0)
        val target = scrollOffset.coerceIn(0.0, maxOffset)
        val delta = target - this.scrollOffset
        when {
            target == 0.0 -> state.scrollToItem(0)
            target == maxOffset -> state.scrollToItem(count - 1)
            abs(delta) <= viewportSize -> state.scrollBy(delta.toFloat())
            else -> {
                val advance = itemAdvance
                val index = (target / advance).toInt().coerceIn(0, count - 1)
                val offset = (target - index * advance).toInt().coerceAtLeast(0)
                state.scrollToItem(index, offset)
            }
        }
    }
}
