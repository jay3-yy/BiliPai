package com.android.purebilibili.feature.video.screen

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/** 普通视频与番剧共用标题折叠手势；排序 dock 是否保留由宿主设置决定。 */
@Composable
internal fun rememberVideoCommentHeaderScrollConnection(
    listState: LazyListState,
    collapsePx: MutableFloatState,
    maxCollapsePx: () -> Float,
    enabled: Boolean,
    pagerScrolling: () -> Boolean = { false },
): NestedScrollConnection {
    val listAtTop by remember(listState) {
        derivedStateOf {
            isVideoContentCommentListAtTop(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }
    val latestMax by rememberUpdatedState(maxCollapsePx)
    val latestPagerScrolling by rememberUpdatedState(pagerScrolling)
    var wasAtTop by remember(listState) { mutableStateOf(listAtTop) }
    LaunchedEffect(listState, enabled, listAtTop, maxCollapsePx()) {
        collapsePx.floatValue = resolveVideoContentTabBarCollapsePxWhenListLeavesTop(
            collapsePx = collapsePx.floatValue,
            maxCollapsePx = latestMax(),
            listAtTop = listAtTop,
            enabled = enabled,
            returningToTop = listAtTop && !wasAtTop,
        )
        wasAtTop = listAtTop
    }
    return remember(listState, collapsePx, enabled, listAtTop) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val update = reduceVideoContentTabBarCollapseOnPreScroll(
                    collapsePx.floatValue, latestMax(), available.y, listAtTop,
                    enabled && !latestPagerScrolling(),
                ) ?: return Offset.Zero
                collapsePx.floatValue = update.nextCollapsePx
                return Offset(0f, update.consumedY)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val update = reduceVideoContentTabBarCollapseOnPostScroll(
                    collapsePx.floatValue, latestMax(), available.y, listAtTop,
                    enabled && !latestPagerScrolling(),
                ) ?: return Offset.Zero
                collapsePx.floatValue = update.nextCollapsePx
                return Offset(0f, update.consumedY)
            }
        }
    }
}
