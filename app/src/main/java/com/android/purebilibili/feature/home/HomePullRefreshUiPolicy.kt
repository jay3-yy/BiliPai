package com.android.purebilibili.feature.home

import com.android.purebilibili.core.ui.AppPullRefreshIndicatorStyle
import com.android.purebilibili.core.ui.AppPullRefreshMotionStyle
import kotlin.math.max
import kotlin.math.min

internal fun resolveRequiredPullDistanceDp(
    thresholdDp: Float,
    dragMultiplier: Float
): Float {
    if (dragMultiplier <= 0f) return Float.POSITIVE_INFINITY
    return thresholdDp / dragMultiplier
}

internal fun shouldResetToTopOnRefreshStart(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int
): Boolean {
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
}

internal fun shouldResetToTopAfterIncrementalRefresh(
    currentCategory: HomeCategory,
    newItemsCount: Int?,
    isRefreshing: Boolean,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int
): Boolean {
    if (currentCategory != HomeCategory.RECOMMEND && currentCategory != HomeCategory.FOLLOW) {
        return false
    }
    if ((newItemsCount ?: 0) <= 0) return false
    if (isRefreshing) return false
    // 关注流新内容是 prepend：LazyGrid 会按 key 锚住旧卡片，即使 index=0 也必须强制回顶。
    if (currentCategory == HomeCategory.FOLLOW) return true
    return shouldResetToTopOnRefreshStart(
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset
    )
}

internal fun shouldShowReleaseToRefreshHint(
    progress: Float,
    isRefreshing: Boolean,
    isStateAnimating: Boolean
): Boolean {
    if (isRefreshing) return false
    if (progress < 1f) return false
    return !isStateAnimating
}

internal fun resolvePullRefreshHintText(
    progress: Float,
    isRefreshing: Boolean,
    isStateAnimating: Boolean
): String {
    return when {
        isRefreshing -> "正在刷新..."
        shouldShowReleaseToRefreshHint(
            progress = progress,
            isRefreshing = isRefreshing,
            isStateAnimating = isStateAnimating
        ) -> "松手刷新"
        progress > 0f -> "下拉刷新..."
        else -> ""
    }
}

internal fun resolvePullIndicatorTranslationY(
    dragOffsetPx: Float,
    indicatorHeightPx: Float,
    minGapPx: Float,
    isRefreshing: Boolean
): Float {
    if (isRefreshing) return 0f
    if (dragOffsetPx <= 0f) return -indicatorHeightPx
    val centeredY = (dragOffsetPx / 2f) - (indicatorHeightPx / 2f)
    val maxAllowedY = dragOffsetPx - indicatorHeightPx - minGapPx
    return min(centeredY, maxAllowedY)
}

internal fun resolvePullContentMaxOffsetDp(
    indicatorStyle: AppPullRefreshIndicatorStyle
): Float {
    return when (indicatorStyle) {
        AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE -> 172f
        else -> 140f
    }
}

/** Native indicator's lower edge follows fraction * threshold; feed stays below that edge. */
internal fun resolveMd3PullContentOffsetPx(
    distanceFraction: Float,
    indicatorThresholdPx: Float,
    gapPx: Float,
): Float {
    val distance = distanceFraction.coerceAtLeast(0f)
    return distance * indicatorThresholdPx + distance.coerceAtMost(1f) * gapPx
}

internal fun resolveMd3ScreenshotPullOffsetFraction(distanceFraction: Float): Float {
    val clamped = distanceFraction.coerceIn(0f, 1.12f)
    val resistance = 1f + 0.35f * clamped
    return clamped / resistance
}

internal fun resolvePullContentOffsetFraction(
    distanceFraction: Float,
    isRefreshing: Boolean,
    motionStyle: AppPullRefreshMotionStyle = AppPullRefreshMotionStyle.CUPERTINO,
    indicatorStyle: AppPullRefreshIndicatorStyle = AppPullRefreshIndicatorStyle.CUPERTINO
): Float {
    if (isRefreshing) return 0f
    if (indicatorStyle == AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE) {
        return resolveMd3ScreenshotPullOffsetFraction(distanceFraction)
    }
    val clampedDistance = distanceFraction.coerceAtMost(2f).coerceAtLeast(0f)
    return clampedDistance * 0.5f
}

internal fun resolveStablePullContentOffsetFraction(
    distanceFraction: Float,
    isRefreshing: Boolean,
    isStateAnimating: Boolean,
    motionStyle: AppPullRefreshMotionStyle = AppPullRefreshMotionStyle.CUPERTINO,
    indicatorStyle: AppPullRefreshIndicatorStyle = AppPullRefreshIndicatorStyle.CUPERTINO
): Float {
    val currentOffset = resolvePullContentOffsetFraction(
        distanceFraction = distanceFraction,
        isRefreshing = isRefreshing,
        motionStyle = motionStyle,
        indicatorStyle = indicatorStyle
    )
    if (!isRefreshing && !isStateAnimating && distanceFraction <= 0f) return 0f
    return currentOffset
}

internal fun shouldSnapPullOffsetToFinger(
    distanceFraction: Float,
    isRefreshing: Boolean,
    isStateAnimating: Boolean,
    indicatorStyle: AppPullRefreshIndicatorStyle = AppPullRefreshIndicatorStyle.CUPERTINO
): Boolean {
    if (isRefreshing) return false
    if (indicatorStyle == AppPullRefreshIndicatorStyle.MATERIAL_SCREENSHOT_HANDLE) {
        return distanceFraction > 0f
    }
    if (isStateAnimating) return false
    return distanceFraction > 0f
}

internal fun resolveMd3ScreenshotRefreshIndicatorHeightDp(
    progress: Float,
    isRefreshing: Boolean
): Float {
    if (isRefreshing) return 42f
    val clampedProgress = progress.coerceIn(0f, 1.35f)
    return 44f + (clampedProgress * 26f)
}

internal fun resolveMd3ScreenshotRefreshIndicatorTotalHeightDp(
    indicatorHeightDp: Float,
    hasHintText: Boolean
): Float {
    val verticalPaddingDp = 16f
    val hintBlockDp = if (hasHintText) 30f else 0f
    return max(0f, indicatorHeightDp) + verticalPaddingDp + hintBlockDp
}

internal fun resolveMd3ScreenshotRefreshIndicatorTranslationY(
    dragOffsetPx: Float,
    indicatorTotalHeightPx: Float,
    minGapPx: Float
): Float {
    if (dragOffsetPx <= 0f || indicatorTotalHeightPx <= 0f) return 0f
    val centeredInGap = (dragOffsetPx - indicatorTotalHeightPx) / 2f
    val maxTop = (dragOffsetPx - indicatorTotalHeightPx - minGapPx).coerceAtLeast(0f)
    return centeredInGap.coerceIn(0f, maxTop)
}
