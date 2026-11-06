package com.android.purebilibili.feature.home

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import com.android.purebilibili.core.ui.rememberHomeStaggeredGridState

/** Each layout retains its own saved viewport; an index is never transferred between layouts. */
internal class HomeFeedGridState(
    val regular: LazyGridState,
    val waterfall: LazyStaggeredGridState,
    val regularLayout: Boolean,
) : ScrollableState by (if (regularLayout) regular else waterfall) {
    private val visibleKeyCache = HomeVisibleItemKeyCache()

    val firstVisibleItemIndex: Int
        get() = if (regularLayout) regular.firstVisibleItemIndex else waterfall.firstVisibleItemIndex
    val firstVisibleItemScrollOffset: Int
        get() = if (regularLayout) regular.firstVisibleItemScrollOffset else waterfall.firstVisibleItemScrollOffset
    val totalItemsCount: Int
        get() = if (regularLayout) regular.layoutInfo.totalItemsCount else waterfall.layoutInfo.totalItemsCount
    val lastVisibleItemIndex: Int
        get() = if (regularLayout) regular.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            else waterfall.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1

    fun visibleItemKeys(): Set<Any> = if (regularLayout) {
        visibleKeyCache.keys(regular.layoutInfo.visibleItemsInfo) { it.key }
    } else {
        visibleKeyCache.keys(waterfall.layoutInfo.visibleItemsInfo) { it.key }
    }

    fun anyVisibleItem(predicate: (Any) -> Boolean): Boolean = if (regularLayout) {
        regular.layoutInfo.visibleItemsInfo.any { predicate(it.key) }
    } else {
        waterfall.layoutInfo.visibleItemsInfo.any { predicate(it.key) }
    }

    val viewportWidthPx: Int
        get() = if (regularLayout) regular.layoutInfo.viewportSize.width else waterfall.layoutInfo.viewportSize.width

    /** 仅在终止快照采样时调用，不为滚动新增逐帧收集。 */
    fun captureMemoryAnchor(density: Density, columns: Int): HomeFeedScrollAnchor? {
        val items = if (regularLayout) {
            regular.layoutInfo.visibleItemsInfo.map { Triple(it.key, it.offset.y, it.offset.x) }
        } else waterfall.layoutInfo.visibleItemsInfo.map { Triple(it.key, it.offset.y, it.offset.x) }
        val first = items.filter { (it.first as? String)?.startsWith("home_video_") == true }
            .minWithOrNull(compareBy({ it.second }, { it.third })) ?: return null
        return HomeFeedScrollAnchor(
            key = first.first as String,
            scrollOffsetDp = -first.second / density.density,
            regularLayout = regularLayout,
            viewportWidthPx = viewportWidthPx,
            columns = columns,
            density = density.density,
            fontScale = density.fontScale,
        )
    }

    suspend fun scrollToItem(index: Int, scrollOffset: Int = 0) {
        if (regularLayout) regular.scrollToItem(index, scrollOffset) else waterfall.scrollToItem(index, scrollOffset)
    }

    suspend fun animateScrollToItem(index: Int, scrollOffset: Int = 0) {
        if (regularLayout) regular.animateScrollToItem(index, scrollOffset)
        else waterfall.animateScrollToItem(index, scrollOffset)
    }
}

@Composable
internal fun rememberHomeFeedGridState(regularLayout: Boolean): HomeFeedGridState {
    val regular = rememberLazyGridState()
    val waterfall = rememberHomeStaggeredGridState()
    return remember(regular, waterfall, regularLayout) { HomeFeedGridState(regular, waterfall, regularLayout) }
}
