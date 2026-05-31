package com.android.purebilibili.feature.home.policy

import com.android.purebilibili.feature.home.resolveNextHomeGlobalScrollOffset
import kotlin.math.abs

internal enum class BottomBarVisibilityIntent {
    SHOW,
    HIDE
}

internal data class HomeScrollUpdate(
    val headerOffsetPx: Float,
    val bottomBarVisibilityIntent: BottomBarVisibilityIntent?,
    val globalScrollOffset: Float?
)

internal data class HomeHeaderSettleTransition(
    val targetOffsetPx: Float,
    val shouldAnimate: Boolean
)

internal fun shouldHandleHomeVerticalPreScroll(
    deltaX: Float,
    deltaY: Float,
    minimumVerticalDeltaPx: Float = 0.5f
): Boolean {
    val absoluteDeltaY = abs(deltaY)
    if (absoluteDeltaY < minimumVerticalDeltaPx) return false
    return absoluteDeltaY >= abs(deltaX)
}

internal fun resolveHomeHeaderSettleTransition(
    currentHeaderOffsetPx: Float,
    targetHeaderOffsetPx: Float,
    animationThresholdPx: Float = 0.5f
): HomeHeaderSettleTransition {
    return HomeHeaderSettleTransition(
        targetOffsetPx = targetHeaderOffsetPx,
        shouldAnimate = abs(currentHeaderOffsetPx - targetHeaderOffsetPx) > animationThresholdPx
    )
}

internal fun resolveHomeHeaderTransitionRunning(
    isFeedScrolling: Boolean,
    isPagerScrolling: Boolean,
    isHeaderSettleAnimating: Boolean
): Boolean {
    return isFeedScrolling || isPagerScrolling || isHeaderSettleAnimating
}

internal fun shouldExpandHomeHeaderForSettledPage(
    currentHeaderOffsetPx: Float,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int
): Boolean {
    if (currentHeaderOffsetPx >= 0f) return false
    if (firstVisibleItemIndex != 0) return false
    return firstVisibleItemScrollOffset == 0
}

internal fun resolveHomeHeaderOffsetForSettledPage(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    maxHeaderCollapsePx: Float
): Float {
    if (maxHeaderCollapsePx <= 0f) return 0f
    if (firstVisibleItemIndex != 0) return -maxHeaderCollapsePx
    return -firstVisibleItemScrollOffset.toFloat().coerceIn(0f, maxHeaderCollapsePx)
}

internal fun reduceHomePreScroll(
    currentHeaderOffsetPx: Float,
    deltaY: Float,
    minHeaderOffsetPx: Float,
    canRevealHeader: Boolean,
    isHeaderCollapseEnabled: Boolean,
    isBottomBarAutoHideEnabled: Boolean,
    useSideNavigation: Boolean,
    liquidGlassEnabled: Boolean,
    currentGlobalScrollOffset: Float,
    bottomBarVisibilityThresholdPx: Float = 10f
): HomeScrollUpdate {
    val nextHeaderOffset = when {
        !isHeaderCollapseEnabled -> 0f
        deltaY > 0f && !canRevealHeader -> minHeaderOffsetPx
        else -> (currentHeaderOffsetPx + deltaY).coerceIn(minHeaderOffsetPx, 0f)
    }

    val nextBottomBarIntent = when {
        !isBottomBarAutoHideEnabled || useSideNavigation -> null
        deltaY <= -bottomBarVisibilityThresholdPx -> BottomBarVisibilityIntent.HIDE
        deltaY >= bottomBarVisibilityThresholdPx -> BottomBarVisibilityIntent.SHOW
        else -> null
    }

    return HomeScrollUpdate(
        headerOffsetPx = nextHeaderOffset,
        bottomBarVisibilityIntent = nextBottomBarIntent,
        globalScrollOffset = resolveNextHomeGlobalScrollOffset(
            currentOffset = currentGlobalScrollOffset,
            scrollDeltaY = deltaY,
            liquidGlassEnabled = liquidGlassEnabled
        )
    )
}
