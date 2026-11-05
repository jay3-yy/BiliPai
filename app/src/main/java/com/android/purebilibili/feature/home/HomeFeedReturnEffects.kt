package com.android.purebilibili.feature.home

import androidx.compose.runtime.*
import com.android.purebilibili.feature.home.policy.*
import kotlinx.coroutines.yield

@Composable
internal fun HomeFeedReturnEffects(
    state: HomeReturnState,
    pagerCoordinator: HomePagerCoordinator,
    headerScrollState: HomeHeaderScrollState,
    isTopLevelActive: Boolean,
    useRegularHomeGrid: Boolean,
    currentCategory: HomeCategory,
    popularSubCategory: PopularSubCategory,
    gridStates: Map<HomeCategory, HomeFeedGridState>,
    popularGridStates: Map<PopularSubCategory, HomeFeedGridState>,
) {
    // 详情/空间等覆盖首页期间 Pager 可能受导航过渡影响；返回后必须先由业务状态重新对齐。
    // 同时冻结 feed 滚动锚点：二级页返回后 contentPadding（底栏）与列表重测可能把视口“顶”下去一段。
    LaunchedEffect(isTopLevelActive, useRegularHomeGrid) {
        if (!isTopLevelActive) {
            val gridState = if (currentCategory == HomeCategory.POPULAR) {
                popularGridStates[popularSubCategory]
            } else {
                gridStates[currentCategory]
            }
            // Video clicks freeze the anchor synchronously before navigation starts. Do not replace
            // that clean snapshot with coordinates sampled after shared-bounds has begun remeasuring.
            if (gridState != null) {
                state.freezeAnchorIfMissing(captureHomeFeedScrollAnchor(
                    category = currentCategory,
                    popularSubCategory = popularSubCategory,
                    firstVisibleItemIndex = gridState.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = gridState.firstVisibleItemScrollOffset,
                    headerOffsetPx = headerScrollState.offsetPx,
                    regularGrid = gridState.regularLayout,
                ))
            }
            pagerCoordinator.hasSynced = false
            pagerCoordinator.lastDrivenCategory = null
        }
    }

    LaunchedEffect(isTopLevelActive, pagerCoordinator.hasSynced, state.pendingAnchor, useRegularHomeGrid) {
        val anchor = state.pendingAnchor
        if (
            !shouldRestoreHomeFeedScrollAnchor(
                isTopLevelActive = isTopLevelActive,
                hasSyncedPagerWithState = pagerCoordinator.hasSynced,
                anchor = anchor
            )
        ) {
            return@LaunchedEffect
        }
        val restoreAnchor = requireNotNull(anchor)
        // 等 Pager snap / 底栏 padding 落定后再校正，避免用过渡中的中间布局写回错误 offset。
        yield()
        if (state.pendingAnchor !== restoreAnchor) return@LaunchedEffect
        val gridState = if (restoreAnchor.category == HomeCategory.POPULAR) {
            popularGridStates[restoreAnchor.popularSubCategory]
        } else {
            gridStates[restoreAnchor.category]
        } ?: run {
            state.consumeAnchor(restoreAnchor)
            return@LaunchedEffect
        }
        val restorePlan = resolveHomeFeedScrollRestorePlan(
            currentIndex = gridState.firstVisibleItemIndex,
            currentOffset = gridState.firstVisibleItemScrollOffset,
            currentHeaderOffsetPx = headerScrollState.offsetPx,
            anchor = restoreAnchor,
            currentRegularGrid = gridState.regularLayout,
        )
        if (restorePlan.restoreGrid) {
            gridState.scrollToItem(
                restoreAnchor.firstVisibleItemIndex,
                restoreAnchor.firstVisibleItemScrollOffset
            )
        }
        if (state.pendingAnchor !== restoreAnchor) return@LaunchedEffect
        if (restorePlan.restoreHeader) {
            headerScrollState.setOffsetImmediately(restoreAnchor.headerOffsetPx)
        }
        state.consumeAnchor(restoreAnchor)
    }
}
