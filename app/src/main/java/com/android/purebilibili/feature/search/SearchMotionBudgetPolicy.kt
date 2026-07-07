package com.android.purebilibili.feature.search

internal enum class SearchMotionBudget {
    FULL,
    REDUCED
}

internal fun resolveSearchMotionBudget(
    hasQuery: Boolean,
    isSearching: Boolean,
    isScrolling: Boolean
): SearchMotionBudget {
    return if (isSearching || (hasQuery && isScrolling)) {
        SearchMotionBudget.REDUCED
    } else {
        SearchMotionBudget.FULL
    }
}

internal fun shouldEnableSearchHazeSource(
    isSearching: Boolean,
    startupSettled: Boolean = true
): Boolean = startupSettled && !isSearching

internal fun resolveEffectiveSearchMotionBudget(
    startupSettled: Boolean,
    baseBudget: SearchMotionBudget
): SearchMotionBudget {
    return if (startupSettled) baseBudget else SearchMotionBudget.REDUCED
}

/**
 * 搜索结果卡片是否启用共享元素过渡。
 *
 * 与首页一致：仅跟随全局 [cardTransitionEnabled]，不再受搜索结果滚动/加载 budget 门控。
 * budget 仍用于 haze、header blur、进场动画等轻量效果；sharedBounds 若因 REDUCED 未挂载，
 * 点击视频会退化为普通 fade，与首页卡片放大过渡不一致。
 */
internal fun resolveEffectiveSearchCardTransitionEnabled(
    cardTransitionEnabled: Boolean,
    @Suppress("UNUSED_PARAMETER") motionBudget: SearchMotionBudget,
    @Suppress("UNUSED_PARAMETER") isReturningFromVideoDetail: Boolean,
): Boolean {
    return cardTransitionEnabled
}

internal fun shouldBootstrapSearchLandingData(
    startupSettled: Boolean,
    showResults: Boolean,
    query: String
): Boolean {
    return startupSettled && !showResults && query.isBlank()
}

internal fun shouldAutoFocusSearchField(
    startupSettled: Boolean,
    query: String
): Boolean {
    return startupSettled && query.isBlank()
}

internal fun shouldForceLowBudgetSearchHeaderBlur(
    isSearching: Boolean,
    isScrollingResults: Boolean
): Boolean {
    return isSearching && !isScrollingResults
}
