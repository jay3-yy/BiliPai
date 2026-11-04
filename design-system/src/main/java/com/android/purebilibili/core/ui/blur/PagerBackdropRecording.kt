package com.android.purebilibili.core.ui.blur

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

/** 背景录制跟随实际曝光；不同于动画/后台工作门控，横滑中露出的两页仍需采样。 */
val LocalChromeBackdropRecordingAllowed = compositionLocalOf { true }

internal fun pagerBackdropIntersectsViewport(
    pageOffset: Int,
    pageSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Boolean = pageSize > 0 && viewportEnd > viewportStart &&
    pageOffset < viewportEnd && pageOffset + pageSize > viewportStart

@Composable
fun rememberPagerChromeBackdropRecordingAllowed(state: PagerState, page: Int): Boolean {
    val visible by remember(state, page) {
        derivedStateOf {
            val layout = state.layoutInfo
            if (layout.visiblePagesInfo.isEmpty()) {
                // 首次测量前仅预热初始页，不能让预加载页一并开始录制。
                page == state.currentPage
            } else {
                layout.visiblePagesInfo.any {
                    it.index == page && pagerBackdropIntersectsViewport(
                        pageOffset = it.offset,
                        pageSize = layout.pageSize,
                        viewportStart = layout.viewportStartOffset,
                        viewportEnd = layout.viewportEndOffset,
                    )
                }
            }
        }
    }
    return LocalChromeBackdropRecordingAllowed.current && visible
}
