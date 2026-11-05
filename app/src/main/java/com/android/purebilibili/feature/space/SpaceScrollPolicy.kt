package com.android.purebilibili.feature.space

import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** 任一顶部模糊或纯色渐变开启时，内容连续穿过固定顶栏；全部关闭才折叠资料并吸顶标签。 */
internal fun shouldPinSpaceContentTabs(
    progressiveTopBlurEnabled: Boolean,
    headerBlurEnabled: Boolean = false,
    progressiveTopFadeEnabled: Boolean = false,
): Boolean = !progressiveTopBlurEnabled && !headerBlurEnabled && !progressiveTopFadeEnabled

/** 标签到达工具栏下缘后显示顶层副本；原 item 回收后仍保持吸顶。 */
internal fun shouldShowSpacePinnedTabs(
    tabsOffsetPx: Int?,
    beforeContentPaddingPx: Int,
    chromeBottomPx: Int,
    firstVisibleItemIndex: Int,
): Boolean = if (tabsOffsetPx != null) {
    tabsOffsetPx + beforeContentPaddingPx <= chromeBottomPx
} else firstVisibleItemIndex > 1

/** 资料头和标签是各分页共用的前两个 item，切页时必须连同标签的局部偏移一起对齐。 */
internal fun alignSpacePageHeader(
    source: LazyStaggeredGridState,
    target: LazyStaggeredGridState,
) {
    if (source.firstVisibleItemIndex <= 1) {
        target.requestScrollToItem(source.firstVisibleItemIndex, source.firstVisibleItemScrollOffset)
    } else if (target.firstVisibleItemIndex <= 1) {
        target.requestScrollToItem(1)
    }
}

/** 根据资料实际剩余高度收尾，长简介、大字号和宽屏不共用固定折叠距离。 */
internal fun resolveSpaceHeaderClosingFraction(
    remainingHeightPx: Float,
    toolbarHeightPx: Float,
): Float = (1f - remainingHeightPx / toolbarHeightPx.coerceAtLeast(1f)).coerceIn(0f, 1f)

/** 定位前先确保标签经过测量，避免首屏尚未出现的标签被当作零高度。 */
internal suspend fun LazyStaggeredGridState.measureSpacePinnedTabsHeightPx(): Int {
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == "space_tabs" }?.let { return it.size.height }
    scrollToItem(1)
    return snapshotFlow {
        layoutInfo.visibleItemsInfo.firstOrNull { it.key == "space_tabs" }?.size?.height
    }.filterNotNull().first()
}
