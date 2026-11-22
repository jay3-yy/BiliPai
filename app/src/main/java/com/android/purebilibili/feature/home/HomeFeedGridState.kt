package com.android.purebilibili.feature.home

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Density
import com.android.purebilibili.core.util.animateScrollToTop
import com.android.purebilibili.core.ui.rememberHomeStaggeredGridState

/**
 * 一次滚动采样：只有索引，不含任何像素偏移。
 *
 * 偏移量一旦进入采样，`LazyListState` 的每帧位移都会触发发射，预取就成了逐帧重算；
 * 而预取只需要知道"现在看到哪几项"，行级精度足够。
 *
 * @property firstVisibleIndex 首个可见 lazy item 下标；空视口时为 0。
 * @property lastVisibleIndex 最后一个可见 lazy item 下标；空视口时为 -1。
 * @property visibleItemCount 当前可见 lazy item 数量（含 header 等非视频项）。
 */
internal data class HomeFeedScrollSample(
    val firstVisibleIndex: Int,
    val lastVisibleIndex: Int,
    val visibleItemCount: Int,
)

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

    /** The leading item's actual position, including when it is visible in the padding. */
    fun remainingScrollToTopPx(): Int? {
        val top = if (regularLayout) {
            regular.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset?.y
        } else {
            waterfall.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset?.y
        }
        return top?.let { (-it).coerceAtLeast(0) }
    }

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

    /**
     * 采样当前视口的行级窗口，供预取泵在 `snapshotFlow` 里以**内容相等**去重。
     *
     * 刻意不读取 `firstVisibleItemScrollOffset`：只要读它，一次 fling 里每帧都会产生新样本，
     * 与文件顶部"不为滚动新增逐帧收集"的约定冲突。索引只在跨行时变化，粒度正好够用。
     */
    fun scrollSample(): HomeFeedScrollSample = if (regularLayout) {
        val visible = regular.layoutInfo.visibleItemsInfo
        HomeFeedScrollSample(
            firstVisibleIndex = regular.firstVisibleItemIndex,
            lastVisibleIndex = visible.maxOfOrNull { it.index } ?: -1,
            visibleItemCount = visible.size,
        )
    } else {
        val visible = waterfall.layoutInfo.visibleItemsInfo
        HomeFeedScrollSample(
            firstVisibleIndex = waterfall.firstVisibleItemIndex,
            lastVisibleIndex = visible.maxOfOrNull { it.index } ?: -1,
            visibleItemCount = visible.size,
        )
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

    /** 保持刷新前的顶部位置，避免增量插入后继续锚定原首项。 */
    fun keepTopOnNextLayout() {
        if (regularLayout) regular.requestScrollToItem(0) else waterfall.requestScrollToItem(0)
    }

    suspend fun animateScrollToItem(index: Int, scrollOffset: Int = 0) {
        if (regularLayout) regular.animateScrollToItem(index, scrollOffset)
        else waterfall.animateScrollToItem(index, scrollOffset)
    }

    /** Route return actions to the native state that owns the rendered grid. */
    suspend fun animateScrollToTop(fast: Boolean = false) {
        if (regularLayout) regular.animateScrollToTop(fast)
        else waterfall.animateScrollToTop(fast)
    }
}

@Composable
internal fun rememberHomeFeedGridState(regularLayout: Boolean): HomeFeedGridState {
    val regular = rememberLazyGridState()
    val waterfall = rememberHomeStaggeredGridState()
    return remember(regular, waterfall, regularLayout) { HomeFeedGridState(regular, waterfall, regularLayout) }
}
