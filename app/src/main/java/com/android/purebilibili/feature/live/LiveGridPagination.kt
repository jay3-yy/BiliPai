package com.android.purebilibili.feature.live

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

internal const val LIVE_GRID_LOAD_MORE_KEY = "live_grid_load_more"

@Composable
internal fun LiveGridPagination(
    grid: LazyGridState,
    enabled: Boolean,
    nextPage: Int,
    onLoadMore: () -> Unit,
) {
    val loadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(grid, nextPage, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val layout = grid.layoutInfo
            layout.visibleItemsInfo.any {
                it.key == LIVE_GRID_LOAD_MORE_KEY &&
                    it.offset.y < layout.viewportEndOffset &&
                    it.offset.y + it.size.height > layout.viewportStartOffset
            }
        }.collect { visible ->
            if (visible) loadMore()
        }
    }
}
