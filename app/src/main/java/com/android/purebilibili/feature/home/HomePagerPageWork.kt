package com.android.purebilibili.feature.home

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/** 数据跟随实际曝光；翻页中的两页可显示内容，只有落位的当前页执行附加工作。 */
internal data class HomePagerPageWork(
    val visible: Boolean,
    val collectUpdates: Boolean,
    val runActiveWork: Boolean,
)

internal fun resolveHomePagerPageWork(
    visible: Boolean,
    currentPage: Boolean,
    pagerScrolling: Boolean,
    updatesAllowed: Boolean,
    workAllowed: Boolean,
    visualWorkActive: Boolean,
    unobscured: Boolean,
): HomePagerPageWork {
    val exposed = visible && visualWorkActive && unobscured
    return HomePagerPageWork(
        visible = visible,
        // 可见数据不依赖装饰任务的焦点/遮挡门控；转场冻结仍由 updatesAllowed 控制。
        collectUpdates = visible && updatesAllowed,
        runActiveWork = exposed && updatesAllowed && currentPage && !pagerScrolling && workAllowed,
    )
}

@Composable
internal fun rememberHomePagerPageVisible(pager: PagerState, page: Int): Boolean {
    val visible by remember(pager, page) {
        derivedStateOf {
            val layout = pager.layoutInfo
            if (layout.visiblePagesInfo.isEmpty()) {
                page == pager.currentPage
            } else {
                layout.visiblePagesInfo.any {
                    it.index == page && it.offset < layout.viewportEndOffset &&
                        it.offset + layout.pageSize > layout.viewportStartOffset
                }
            }
        }
    }
    return visible
}
