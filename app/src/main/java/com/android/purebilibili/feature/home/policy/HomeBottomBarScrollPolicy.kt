package com.android.purebilibili.feature.home.policy

internal data class HomeBottomBarScrollState(
    val firstVisibleItem: Int,
    val scrollOffset: Int
)

internal data class HomeBottomBarScrollUpdate(
    val state: HomeBottomBarScrollState,
    val visibilityIntent: BottomBarVisibilityIntent?
)

internal fun reduceHomeBottomBarListScroll(
    previousState: HomeBottomBarScrollState,
    firstVisibleItem: Int,
    scrollOffset: Int,
    isVideoNavigating: Boolean,
    contentInteractionRestored: Boolean = false,
    topRevealThresholdPx: Int = 100,
    offsetHysteresisPx: Int = 200
): HomeBottomBarScrollUpdate {    val nextState = HomeBottomBarScrollState(
        firstVisibleItem = firstVisibleItem,
        scrollOffset = scrollOffset
    )
    if (isVideoNavigating && !contentInteractionRestored) {
        return HomeBottomBarScrollUpdate(
            state = nextState,
            visibilityIntent = null
        )
    }

    val intent = when {
        firstVisibleItem == 0 && scrollOffset < topRevealThresholdPx -> BottomBarVisibilityIntent.SHOW
        firstVisibleItem > previousState.firstVisibleItem -> BottomBarVisibilityIntent.HIDE
        firstVisibleItem < previousState.firstVisibleItem -> BottomBarVisibilityIntent.SHOW
        scrollOffset > previousState.scrollOffset + offsetHysteresisPx -> BottomBarVisibilityIntent.HIDE
        scrollOffset < previousState.scrollOffset - offsetHysteresisPx -> BottomBarVisibilityIntent.SHOW
        else -> null
    }

    return HomeBottomBarScrollUpdate(
        state = nextState,
        visibilityIntent = intent
    )
}

/**
 * 底栏显隐采样的发射门槛：列表顶部绝对显隐区（首项且偏移低于显示阈值）逐帧上报，
 * 其余区域仅当可见条目变化或偏移自上次已结算采样累积跨过步长时上报。
 * 方向与滞回判定仍由 [reduceHomeBottomBarListScroll] 基于「上次已结算采样」完成；
 * 步长必须不大于 [reduceHomeBottomBarListScroll] 的同项滞回，否则慢滚会被吞掉。
 */
internal fun shouldEmitBottomBarScrollSample(
    previousEmittedItem: Int,
    previousEmittedOffset: Int,
    firstVisibleItem: Int,
    scrollOffset: Int,
    topRevealThresholdPx: Int = 100,
    sampleStepPx: Int = 40
): Boolean {
    if (firstVisibleItem != previousEmittedItem) return true
    if (firstVisibleItem == 0 && scrollOffset < topRevealThresholdPx) return true
    return kotlin.math.abs(scrollOffset - previousEmittedOffset) >= sampleStepPx
}

internal fun resolveBottomBarChromeScrollOffset(
    firstVisibleItem: Int,
    scrollOffset: Int,
    offscreenOffset: Float = 1000f
): Float {
    return if (firstVisibleItem == 0) {
        scrollOffset.coerceAtLeast(0).toFloat()
    } else {
        offscreenOffset
    }
}
