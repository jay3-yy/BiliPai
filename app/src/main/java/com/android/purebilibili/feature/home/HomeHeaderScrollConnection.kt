package com.android.purebilibili.feature.home

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.android.purebilibili.core.store.CommonListHeaderCollapseMode
import com.android.purebilibili.core.store.HomeBarHideType
import com.android.purebilibili.feature.home.policy.BottomBarVisibilityIntent
import com.android.purebilibili.feature.home.policy.canRevealHomeHeaderForList
import com.android.purebilibili.feature.home.policy.reduceHomePreScroll
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderRevealConsumedY
import com.android.purebilibili.feature.home.policy.resolveHomeHeaderListIndex
import com.android.purebilibili.feature.home.policy.shouldHandleHomeVerticalPreScroll

@Immutable
internal data class HomeHeaderScrollConfiguration(
    val collapseEnabled: Boolean,
    val collapseDistancePx: Float,
    val collapseTabs: Boolean,
    val bottomBarAutoHideEnabled: Boolean,
    val useSideNavigation: Boolean,
    val liquidGlassEnabled: Boolean,
    val hideType: HomeBarHideType,
    val collapseMode: CommonListHeaderCollapseMode = CommonListHeaderCollapseMode.SHOW_ON_REVERSE_SCROLL,
)

@Composable
internal fun rememberHomeHeaderScrollConnection(
    configuration: HomeHeaderScrollConfiguration,
    pagerState: PagerState,
    topTabEntries: List<HomeTopTabEntry>,
    activeGridStateProvider: () -> HomeFeedGridState?,
    subscriptionListState: LazyStaggeredGridState,
    partitionListState: LazyListState = rememberLazyListState(),
    revealLocked: Boolean,
    headerOffsetProvider: () -> Float,
    globalScrollOffset: MutableFloatState,
    onHeaderOffsetChanged: (Float, Boolean) -> Unit,
    onTabsCollapsedChanged: (Boolean) -> Unit,
    onBottomBarVisibleChanged: (Boolean) -> Unit,
    refreshInteractionActiveProvider: () -> Boolean = { false },
): NestedScrollConnection {
    val currentRefreshInteractionActive = rememberUpdatedState(refreshInteractionActiveProvider)
    val currentActiveGridState = rememberUpdatedState(activeGridStateProvider)
    val currentOnHeaderOffsetChanged = rememberUpdatedState(onHeaderOffsetChanged)
    val currentOnTabsCollapsedChanged = rememberUpdatedState(onTabsCollapsedChanged)
    val currentOnBottomBarVisibleChanged = rememberUpdatedState(onBottomBarVisibleChanged)
    return remember(
        configuration,
        pagerState,
        topTabEntries,
        subscriptionListState,
        partitionListState,
        revealLocked,
        headerOffsetProvider,
        globalScrollOffset,
    ) {
        object : NestedScrollConnection {
            private var lastUserVerticalDelta = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (revealLocked || pagerState.isScrollInProgress || !shouldHandleHomeVerticalPreScroll(
                        deltaX = available.x,
                        deltaY = available.y,
                    )
                ) {
                    return Offset.Zero
                }
                if (source == NestedScrollSource.UserInput) lastUserVerticalDelta = available.y
                val currentEntry = resolveHomeTopTabEntryOrNull(
                    topTabEntries,
                    pagerState.currentPage,
                )
                val onSubscriptionTab = currentEntry == HomeTopTabEntry.Subscriptions
                val onPartitionTab = currentEntry == HomeTopTabEntry.Partition
                val activeGridState = currentActiveGridState.value()
                val headerListIndex = resolveHomeHeaderListIndex(
                    displayedEntryIsSubscription = onSubscriptionTab,
                    categoryFirstVisibleIndex = activeGridState?.firstVisibleItemIndex ?: 0,
                    subscriptionFirstVisibleIndex = subscriptionListState.firstVisibleItemIndex,
                )
                // 仅回顶模式依据当前列表位置决定是否恢复；反向恢复模式允许浏览中展开。
                val canRevealHeader = if (onPartitionTab) partitionListState.firstVisibleItemIndex == 0 else canRevealHomeHeaderForList(
                    firstVisibleItemIndex = headerListIndex,
                    listMissing = !onSubscriptionTab && activeGridState == null,
                )
                val listAtTop = when {
                    onPartitionTab -> !partitionListState.canScrollBackward
                    onSubscriptionTab -> !subscriptionListState.canScrollBackward
                    else -> activeGridState?.canScrollBackward == false
                }
                // 刷新保护只在当前列表顶部生效，避免请求中或切页残留状态强制展开。
                if (currentRefreshInteractionActive.value() && listAtTop) {
                    currentOnHeaderOffsetChanged.value(0f, false)
                    currentOnTabsCollapsedChanged.value(false)
                    return Offset.Zero
                }
                val oldHeaderOffset = headerOffsetProvider()
                val revealConsumedY = resolveHomeHeaderRevealConsumedY(
                    currentHeaderOffsetPx = oldHeaderOffset,
                    deltaY = available.y,
                    listAtTop = listAtTop,
                    collapseEnabled = configuration.collapseEnabled,
                )
                val scrollUpdate = reduceHomePreScroll(
                    currentHeaderOffsetPx = oldHeaderOffset,
                    deltaY = available.y,
                    minHeaderOffsetPx = -configuration.collapseDistancePx,
                    canRevealHeader = canRevealHeader,
                    collapseMode = configuration.collapseMode,
                    isHeaderCollapseEnabled = configuration.collapseEnabled,
                    isBottomBarAutoHideEnabled = configuration.bottomBarAutoHideEnabled,
                    useSideNavigation = configuration.useSideNavigation,
                    liquidGlassEnabled = configuration.liquidGlassEnabled,
                    currentGlobalScrollOffset = globalScrollOffset.floatValue,
                    hideType = configuration.hideType,
                    // 惯性不在列表中途改变顶栏方向；到顶后仍允许剩余位移展开。
                    headerDeltaY = if (source == NestedScrollSource.UserInput || listAtTop) available.y else 0f,
                    isHeaderRevealLocked = revealLocked,
                    headerConsumedY = revealConsumedY,
                    listAtTop = listAtTop,
                )
                if (scrollUpdate.headerOffsetPx != oldHeaderOffset) {
                    currentOnHeaderOffsetChanged.value(
                        scrollUpdate.headerOffsetPx,
                        scrollUpdate.shouldAnimateHeader,
                    )
                }
                currentOnTabsCollapsedChanged.value(
                    configuration.collapseTabs && !revealLocked &&
                        headerOffsetProvider() < -0.5f
                )
                scrollUpdate.globalScrollOffset?.let { globalScrollOffset.floatValue = it }
                when (scrollUpdate.bottomBarVisibilityIntent) {
                    BottomBarVisibilityIntent.SHOW -> currentOnBottomBarVisibleChanged.value(true)
                    BottomBarVisibilityIntent.HIDE -> currentOnBottomBarVisibleChanged.value(false)
                    null -> Unit
                }
                if (headerOffsetProvider() >= -0.5f) {
                    currentOnTabsCollapsedChanged.value(false)
                }
                // 浏览时只观察列表；贴顶下拉先展开顶栏，剩余位移由刷新容器处理。
                return Offset(0f, revealConsumedY)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (revealLocked || pagerState.isScrollInProgress || !configuration.collapseEnabled || available.y <= 0f) return Offset.Zero
                val currentEntry = resolveHomeTopTabEntryOrNull(
                    topTabEntries, pagerState.currentPage,
                )
                val onSubscriptionTab = currentEntry == HomeTopTabEntry.Subscriptions
                val activeList = if (onSubscriptionTab) subscriptionListState else currentActiveGridState.value()
                if (if (currentEntry == HomeTopTabEntry.Partition) partitionListState.canScrollBackward else activeList?.canScrollBackward == true) return Offset.Zero
                // 内嵌页面自行持有列表状态，向父级交出的向下剩余位移表示内容已到顶。
                // 同一帧内列表刚到顶时，用剩余位移展开；手指或惯性恰好结束也不依赖下一次 preScroll。
                val oldOffset = headerOffsetProvider()
                val reveal = minOf(available.y, (-oldOffset).coerceAtLeast(0f))
                if (reveal <= 0f) return Offset.Zero
                val nextOffset = oldOffset + reveal
                currentOnHeaderOffsetChanged.value(nextOffset, false)
                currentOnTabsCollapsedChanged.value(configuration.collapseTabs && nextOffset < -0.5f)
                return Offset(0f, reveal)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!revealLocked && !pagerState.isScrollInProgress && configuration.collapseEnabled &&
                    resolveHomeTopTabEntryOrNull(topTabEntries, pagerState.currentPage) == HomeTopTabEntry.Partition &&
                    lastUserVerticalDelta > 0f && !partitionListState.canScrollBackward && headerOffsetProvider() < -0.5f
                ) {
                    currentOnHeaderOffsetChanged.value(0f, true)
                    currentOnTabsCollapsedChanged.value(false)
                }
                lastUserVerticalDelta = 0f
                return Velocity.Zero
            }
        }
    }
}
