package com.android.purebilibili.navigation

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.snap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.android.purebilibili.core.ui.motion.AppTabIndicatorMotion
import com.android.purebilibili.core.ui.motion.iosMorphTween

/** 底栏内容与指示器共用转场；远距离切换只交换两端页面，不穿过中间栏目。 */
@Stable
internal class MainBottomPagerState(
    private val transitionState: MutableTransitionState<Int>,
    val transition: Transition<Int>,
    private val indicatorPositionState: State<Float>,
    private val pageCount: State<Int>,
    private val reduceMotionState: State<Boolean>,
) {
    private var lastSettledPage by mutableIntStateOf(transitionState.currentState)
    val selectedPage: Int get() = transitionState.targetState
    // Transition 中途改向时 currentState 会变成上一个目标，它不一定真正落位过。
    val settledPage: Int get() = if (transitionState.isIdle) transitionState.currentState else lastSettledPage
    val isNavigating: Boolean get() = !transitionState.isIdle
    val isScrollInProgress: Boolean get() = isNavigating
    val reduceMotion: Boolean get() = reduceMotionState.value
    val indicatorPosition: Float get() = indicatorPositionState.value

    val indicatorPositionProvider: () -> Float = { indicatorPosition }
    val scrollInProgressProvider: () -> Boolean = { isScrollInProgress }
    val programmaticNavigationProvider: () -> Boolean = { isNavigating }
    val targetPositionProvider: () -> Float = { selectedPage.toFloat() }

    fun recordSettledPage() {
        if (transitionState.isIdle) lastSettledPage = transitionState.currentState
    }

    fun switchToPage(targetIndex: Int) {
        val lastPage = pageCount.value - 1
        if (lastPage < 0) return
        // Transition 从正在显示的位置改向；连续点击不取消旧协程再跳回 Pager 的预跳位置。
        transitionState.targetState = targetIndex.coerceIn(0, lastPage)
    }
}

@Composable
internal fun rememberMainBottomPagerState(
    pageCount: Int,
    initialPage: Int = 0,
    reduceMotion: Boolean = false,
): MainBottomPagerState {
    val transitionState = rememberSaveable(
        saver = Saver<MutableTransitionState<Int>, Int>(
            save = { it.targetState },
            restore = { MutableTransitionState(it) },
        ),
    ) { MutableTransitionState(initialPage) }
    val transition = rememberTransition(transitionState, label = "bottom-tab")
    val indicatorPosition = transition.animateFloat(
        transitionSpec = {
            if (reduceMotion) snap() else iosMorphTween(AppTabIndicatorMotion.DurationMillis)
        },
        label = "bottom-tab-indicator",
    ) { it.toFloat() }
    val latestPageCount = rememberUpdatedState(pageCount)
    val latestReduceMotion = rememberUpdatedState(reduceMotion)
    val state = remember(transitionState, transition, indicatorPosition) {
        MainBottomPagerState(transitionState, transition, indicatorPosition, latestPageCount, latestReduceMotion)
    }
    SideEffect { state.recordSettledPage() }
    return state
}
