package com.android.purebilibili.feature.home

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.runtime.*
import com.android.purebilibili.feature.home.policy.*
import com.android.purebilibili.core.util.CardPositionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

/** Pager 与业务分类双向同步的唯一监听入口，只有落位和对齐边界参与组合。 */
@Composable
internal fun HomePagerSyncEffects(
    coordinator: HomePagerCoordinator,
    currentCategory: HomeCategory,
    displayedTabIndex: Int,
    isTopLevelActive: Boolean,
    animationSpec: FiniteAnimationSpec<Float>?,
    onDisplayedTabIndexChange: (Int) -> Unit,
    onCategoryChange: (HomeCategory) -> Unit,
) {
    val pagerState = coordinator.pagerState
    val topTabEntries = coordinator.entries
    val currentDisplayedTabIndex by rememberUpdatedState(displayedTabIndex)
    val updateDisplayedIndex by rememberUpdatedState(onDisplayedTabIndexChange)
    val switchCategory by rememberUpdatedState(onCategoryChange)
    // 分类切换标记属于本次分类会话，不等待下一次详情返回才清除。
    LaunchedEffect(currentCategory) {
        if (CardPositionManager.isSwitchingCategory) {
            delay(300)
            CardPositionManager.isSwitchingCategory = false
        }
    }
    // [修复] 仅在完成首次“状态->Pager”对齐后，才允许“Pager->状态”反向同步，避免返回首页时误跳分类。
    LaunchedEffect(
        pagerState,
        topTabEntries,
        coordinator.hasSynced,
        currentCategory,
        isTopLevelActive
    ) {
        if (!isTopLevelActive) return@LaunchedEffect
        if (!coordinator.hasSynced) return@LaunchedEffect
        snapshotFlow { pagerState.currentPage to pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { (page, scrolling) ->
                val currentCategoryIndex = topTabEntries
                    .indexOf(HomeTopTabEntry.Category(currentCategory))
                    .takeIf { it >= 0 } ?: 0
                val settledEntry = resolveHomeTopTabEntryOrNull(topTabEntries, page)
                val settledCategory = (settledEntry as? HomeTopTabEntry.Category)?.category
                val settledAction = resolveHomePagerSettledAction(
                    isTopLevelActive = isTopLevelActive,
                    hasSyncedPagerWithState = coordinator.hasSynced,
                    pagerCurrentPage = page,
                    pagerScrolling = scrolling,
                    currentCategoryIndex = currentCategoryIndex,
                    settledCategory = settledCategory,
                    programmaticPageSwitchInProgress = coordinator.isSwitching
                )
                if (!scrolling && currentDisplayedTabIndex != page) {
                    updateDisplayedIndex(page)
                }
                if (!scrolling && settledEntry != null) {
                    coordinator.retainedEntry = settledEntry
                }
                when (settledAction) {
                    HomePagerSettledAction.NONE -> return@collect
                    HomePagerSettledAction.SWITCH_CATEGORY -> {
                        val target = settledCategory ?: return@collect
                        switchCategory(target)
                    }
                }
            }
    }

    // [P2] 当前分类被隐藏时，自动落到首个可见分类
    LaunchedEffect(topTabEntries) {
        val visibleCategories = topTabEntries.mapNotNull { (it as? HomeTopTabEntry.Category)?.category }
        val firstVisible = visibleCategories.firstOrNull() ?: return@LaunchedEffect
        if (currentCategory !in visibleCategories) {
            updateDisplayedIndex(0)
            switchCategory(firstVisible)
        }
    }

    // [CrashFix] 顶栏配置变化导致页数收缩时，先钳制 pager 当前页，避免越界
    LaunchedEffect(topTabEntries.size) {
        if (topTabEntries.isEmpty()) return@LaunchedEffect
        val lastIndex = topTabEntries.lastIndex
        if (pagerState.currentPage > lastIndex) {
            coordinator.snapToPage(lastIndex)
        }
    }

    // [修复] 状态变化时驱动 Pager：首次使用无动画对齐，后续用动画跟随
    LaunchedEffect(currentCategory, topTabEntries, isTopLevelActive, animationSpec) {
        if (!isTopLevelActive) return@LaunchedEffect
        val targetPage = resolveHomePagerTargetPage(
            topTabEntries = topTabEntries,
            retainedEntry = coordinator.retainedEntry,
            currentCategory = currentCategory,
            hasSyncedPagerWithState = coordinator.hasSynced
        )
        if (targetPage < 0) return@LaunchedEffect
        if (shouldUseInitialHomePagerSnap(
                hasSyncedPagerWithState = coordinator.hasSynced,
                targetPage = targetPage
            )
        ) {
            coordinator.snapToPage(targetPage)
            coordinator.hasSynced = true
            coordinator.retainedEntry = resolveHomeTopTabEntryOrNull(topTabEntries, targetPage)
            coordinator.lastDrivenCategory = currentCategory
            return@LaunchedEffect
        }
        if (shouldSkipHomePagerStateDrive(
                hasSyncedPagerWithState = coordinator.hasSynced,
                lastDrivenCategory = coordinator.lastDrivenCategory,
                currentCategory = currentCategory
            )
        ) {
            return@LaunchedEffect
        }
        if (targetPage == pagerState.currentPage && !pagerState.isScrollInProgress) {
            coordinator.lastDrivenCategory = currentCategory
            return@LaunchedEffect
        }
        if (shouldAnimateHomePagerToCategory(
                hasSyncedPagerWithState = coordinator.hasSynced,
                targetPage = targetPage,
                pagerCurrentPage = pagerState.currentPage,
                pagerScrolling = pagerState.isScrollInProgress,
                programmaticPageSwitchInProgress = coordinator.isSwitching
            )
        ) {
            coordinator.animateToPage(targetPage, animationSpec)
            coordinator.lastDrivenCategory = currentCategory
        }
    }
}
