package com.android.purebilibili.feature.home

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.*
import com.android.purebilibili.feature.home.policy.shouldTreatInitialHomePagerPageAsSyncedWithState
import com.android.purebilibili.navigation.animatePagerSelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 保留标签身份与对齐状态，集中统计程序切页；不持有 ViewModel 或独立协程作用域。 */
internal class HomePagerCoordinator(
    val pagerState: PagerState,
    val entries: List<HomeTopTabEntry>,
    retainedEntryState: MutableState<HomeTopTabEntry?>,
    syncedState: MutableState<Boolean>,
    drivenCategoryState: MutableState<HomeCategory?>,
    private val switchesInFlight: MutableIntState,
) {
    var retainedEntry by retainedEntryState
    var hasSynced by syncedState
    var lastDrivenCategory by drivenCategoryState
    val isSwitching by derivedStateOf { switchesInFlight.intValue > 0 }

    /** 点击时同步占有切页状态，不等待协程调度才阻止反向同步。 */
    fun selectPage(
        index: Int,
        animationSpec: FiniteAnimationSpec<Float>?,
        scope: CoroutineScope,
    ): Job {
        switchesInFlight.intValue++
        val job = scope.launch(start = CoroutineStart.LAZY) {
            animatePagerSelection(pagerState, index, animationSpec)
        }
        job.invokeOnCompletion { switchesInFlight.intValue-- }
        job.start()
        return job
    }

    suspend fun animateToPage(index: Int, animationSpec: FiniteAnimationSpec<Float>?) = trackSwitch {
        animatePagerSelection(pagerState, index, animationSpec)
    }

    suspend fun snapToPage(index: Int) = trackSwitch {
        pagerState.scrollToPage(index)
    }

    private suspend fun trackSwitch(block: suspend () -> Unit) {
        switchesInFlight.intValue++
        try {
            block()
        } finally {
            switchesInFlight.intValue--
        }
    }
}

@Composable
internal fun rememberHomePagerCoordinator(
    pagerState: PagerState,
    topTabEntries: List<HomeTopTabEntry>,
    currentCategory: HomeCategory,
    initialPage: Int,
): HomePagerCoordinator {
    // 标签重排只重置对齐会话，不把保留的标签身份解释成旧页码。
    val retainedEntry = remember(pagerState) {
        mutableStateOf(resolveHomeTopTabEntryOrNull(topTabEntries, initialPage))
    }
    val synced = remember(pagerState, topTabEntries) {
        mutableStateOf(shouldTreatInitialHomePagerPageAsSyncedWithState(
            initialEntry = resolveHomeTopTabEntryOrNull(topTabEntries, pagerState.currentPage),
            currentCategory = currentCategory,
        ))
    }
    val lastDrivenCategory = remember(pagerState, topTabEntries) {
        mutableStateOf(if (synced.value) currentCategory else null)
    }
    val switchesInFlight = remember(pagerState) { mutableIntStateOf(0) }
    return remember(pagerState, topTabEntries, retainedEntry, synced, lastDrivenCategory, switchesInFlight) {
        HomePagerCoordinator(pagerState, topTabEntries, retainedEntry, synced, lastDrivenCategory, switchesInFlight)
    }
}
