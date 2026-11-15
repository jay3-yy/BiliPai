package com.android.purebilibili.navigation

import androidx.compose.foundation.pager.PagerState
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * 底栏 HorizontalPager 状态。点击指示器与内容翻页分别连续运动。
 */
@Stable
internal class MainBottomPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
    private val reduceMotion: () -> Boolean = { false },
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    var navigationStartPage by mutableIntStateOf(pagerState.currentPage)
        private set

    /** Continuous position shared by the pager and the bottom-bar indicator. */
    val indicatorPosition: Float
        get() = pagerState.tabIndicatorPosition

    /** True for both user-driven and programmatic pager movement. */
    val isScrollInProgress: Boolean
        get() = pagerState.isScrollInProgress

    /** Stable adapters for UI components that observe values from snapshotFlow. */
    val indicatorPositionProvider: () -> Float = { indicatorPosition }
    val scrollInProgressProvider: () -> Boolean = { isScrollInProgress }
    val programmaticNavigationProvider: () -> Boolean = { isNavigating }
    val targetPositionProvider: () -> Float = {
        (if (isNavigating) selectedPage else pagerState.targetPage).toFloat()
    }

    private var navJob: Job? = null

    /**
     * MainPagerState 目标页切换：
     * - 先更新 [selectedPage]（底栏指示器跟目标页），目标页立即开始收集数据；
     * - 再驱动 Pager 到目标，远距离切换允许跳过中间页，指示器独立连续移动。
     */
    fun switchToPage(targetIndex: Int) {
        val lastPage = pagerState.pageCount - 1
        if (lastPage < 0) return
        val safeTargetIndex = targetIndex.coerceIn(0, lastPage)
        if (safeTargetIndex == selectedPage) return

        navJob?.cancel()

        navigationStartPage = pagerState.currentPage
        selectedPage = safeTargetIndex
        isNavigating = true

        // 先保存 Job 再启动；减少动态效果时切页可能同步完成，finally 仍须释放导航状态。
        navJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val myJob = coroutineContext.job
            try {
                // Pager 自身负责目标页的组合与远距离预跳，不在点击和反馈之间插入固定等待。
                animatePagerSelection(
                    pagerState, safeTargetIndex,
                    animationSpec = if (reduceMotion()) snap() else null,
                )
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != safeTargetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                    navigationStartPage = pagerState.currentPage
                }
            }
        }
        navJob?.start()
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
internal fun rememberMainBottomPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    reduceMotion: Boolean = false,
): MainBottomPagerState {
    val latestReduceMotion = rememberUpdatedState(reduceMotion)
    return remember(pagerState, coroutineScope) {
        MainBottomPagerState(
            pagerState = pagerState,
            coroutineScope = coroutineScope,
            reduceMotion = { latestReduceMotion.value },
        )
    }
}
