package com.android.purebilibili.feature.home


internal data class HomePerformanceConfig(
    val headerBlurEnabled: Boolean,
    val bottomBarBlurEnabled: Boolean,
    val topBarLiquidGlassEnabled: Boolean,
    val homeSearchLiquidGlassEnabled: Boolean,
    val bottomBarLiquidGlassEnabled: Boolean,
    val cardAnimationEnabled: Boolean,
    val cardTransitionEnabled: Boolean,
    val isDataSaverActive: Boolean,
    val preloadAheadCount: Int
) {
    val isAnyLiquidGlassEnabled: Boolean
        get() = topBarLiquidGlassEnabled ||
            homeSearchLiquidGlassEnabled ||
            bottomBarLiquidGlassEnabled
}

internal fun resolveHomePreloadAheadCount(
    isDataSaverActive: Boolean,
    normalPreloadAheadCount: Int
): Int {
    if (isDataSaverActive) return 0
    return normalPreloadAheadCount.coerceAtLeast(0).coerceAtMost(2)
}

internal fun resolveHomeCoverPreloadRange(
    isDataSaverActive: Boolean,
    isScrollInProgress: Boolean,
    lastVisibleIndex: Int,
    totalItemCount: Int,
    preloadAheadCount: Int
): IntRange? {
    if (isDataSaverActive || isScrollInProgress || lastVisibleIndex !in 0 until totalItemCount) return null
    val effectiveAheadCount = resolveHomePreloadAheadCount(
        isDataSaverActive = false,
        normalPreloadAheadCount = preloadAheadCount
    )
    if (effectiveAheadCount <= 0) return null

    val preloadStart = (lastVisibleIndex + 1)
        .coerceAtLeast(0)
        .coerceAtMost(totalItemCount)
    val preloadEndExclusive = (preloadStart + effectiveAheadCount).coerceAtMost(totalItemCount)
    if (preloadStart >= preloadEndExclusive) return null
    return preloadStart until preloadEndExclusive
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveHomePerformanceConfig(
    supportsIndependentLiquidGlass: Boolean = true,
    headerBlurEnabled: Boolean,
    bottomBarBlurEnabled: Boolean,
    topBarLiquidGlassEnabled: Boolean,
    homeSearchLiquidGlassEnabled: Boolean = false,
    bottomBarLiquidGlassEnabled: Boolean,
    androidNativeLiquidGlassEnabled: Boolean = true,
    cardAnimationEnabled: Boolean,
    cardTransitionEnabled: Boolean,
    isDataSaverActive: Boolean,
    smartVisualGuardEnabled: Boolean,
    normalPreloadAheadCount: Int = 5
): HomePerformanceConfig {
    // Feature retired: keep parameter for compatibility, but never apply runtime smoothness downgrade.
    val shouldPrioritizeSmoothness = false
    val effectiveDataSaver = isDataSaverActive
    // 场景选择与总开关共同控制材质，关闭的场景保留原生模糊与布局策略。
    val effectiveTopBarLiquidGlass = androidNativeLiquidGlassEnabled && topBarLiquidGlassEnabled
    val effectiveHomeSearchLiquidGlass = androidNativeLiquidGlassEnabled && homeSearchLiquidGlassEnabled
    val effectiveBottomBarLiquidGlass = androidNativeLiquidGlassEnabled && bottomBarLiquidGlassEnabled
    val effectivePreloadAheadCount = when {
        shouldPrioritizeSmoothness -> normalPreloadAheadCount.coerceAtLeast(0).coerceAtMost(2)
        else -> resolveHomePreloadAheadCount(
            isDataSaverActive = effectiveDataSaver,
            normalPreloadAheadCount = normalPreloadAheadCount
        )
    }

    return HomePerformanceConfig(
        headerBlurEnabled = headerBlurEnabled,
        bottomBarBlurEnabled = bottomBarBlurEnabled,
        topBarLiquidGlassEnabled = effectiveTopBarLiquidGlass,
        homeSearchLiquidGlassEnabled = effectiveHomeSearchLiquidGlass,
        bottomBarLiquidGlassEnabled = effectiveBottomBarLiquidGlass,
        cardAnimationEnabled = cardAnimationEnabled,
        cardTransitionEnabled = cardTransitionEnabled,
        isDataSaverActive = effectiveDataSaver,
        preloadAheadCount = effectivePreloadAheadCount
    )
}
