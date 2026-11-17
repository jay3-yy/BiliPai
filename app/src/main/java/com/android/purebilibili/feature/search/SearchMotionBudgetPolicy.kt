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

/**
 * Keep the Miuix backdrop source mounted across result-type requests. Recreating it while a tab
 * request is in flight briefly exposes the plain chrome until the new source records its first
 * frame, which appears as a flash when switching between video, bangumi, and other result tabs.
 */
internal fun shouldKeepSearchChromeBackdropSource(
    progressiveTopBlurEnabled: Boolean,
    liquidGlassEnabled: Boolean,
): Boolean = progressiveTopBlurEnabled || liquidGlassEnabled

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
    showResults: Boolean,
    query: String
): Boolean = !showResults && query.isBlank()

/** 空白搜索入口只自动进入编辑一次；结果页与已消费的入口不重新唤起输入法。 */
internal fun shouldAutoFocusSearchField(
    startupSettled: Boolean,
    query: String,
    showResults: Boolean = false,
    autoFocusConsumed: Boolean = false
): Boolean = startupSettled && query.isBlank() && !showResults && !autoFocusConsumed

enum class SearchBackAction {
    RETURN_TO_LANDING,
    LEAVE_SEARCH
}

/**
 * Only a session opened on the empty search landing has a landing destination to return to.
 * Focus and suggestions are editing state, not evidence that this destination was entered there.
 */
internal fun resolveSearchBackAction(
    showResults: Boolean,
    @Suppress("UNUSED_PARAMETER") suggestionsVisible: Boolean,
    @Suppress("UNUSED_PARAMETER") searchFieldFocused: Boolean,
    openedWithKeyword: Boolean,
): SearchBackAction = if (showResults && !openedWithKeyword) {
    SearchBackAction.RETURN_TO_LANDING
} else {
    SearchBackAction.LEAVE_SEARCH
}

internal fun shouldClearSearchFocusWhenShowingResults(
    showResults: Boolean,
    previousShowResults: Boolean
): Boolean = showResults && !previousShowResults

/** Editing the draft, including clearing it, never changes the active search surface. */
internal fun SearchUiState.withEditedSearchQuery(newQuery: String): SearchUiState = copy(
    query = newQuery,
    suggestions = if (newQuery.isBlank()) emptyList() else suggestions,
)

/**
 * Keep the IME when a focused field goes from a non-empty query to empty.
 * Unfocused clears, already-empty text, and non-empty edits return false so submit and
 * Back still dismiss the keyboard through the existing path.
 */
internal fun shouldRetainSearchImeWhenQueryCleared(
    fieldFocused: Boolean,
    previousQuery: String,
    newQuery: String,
): Boolean = fieldFocused && previousQuery.isNotEmpty() && newQuery.isEmpty()

/** Ignore landing keyword clicks for this long after a focused clear (adjustResize). */
internal const val SEARCH_LANDING_KEYWORD_CLICK_GUARD_MS = 300L

/**
 * [clearedAtElapsedMs] is negative when no focused clear has been recorded.
 * A click at or after [SEARCH_LANDING_KEYWORD_CLICK_GUARD_MS] is accepted.
 */
internal fun shouldAcceptSearchLandingKeywordClick(
    clearedAtElapsedMs: Long,
    nowElapsedMs: Long,
): Boolean {
    if (clearedAtElapsedMs < 0L) return true
    return nowElapsedMs - clearedAtElapsedMs >= SEARCH_LANDING_KEYWORD_CLICK_GUARD_MS
}

internal fun shouldForceLowBudgetSearchHeaderBlur(
    isSearching: Boolean,
    isScrollingResults: Boolean
): Boolean {
    return isSearching && !isScrollingResults
}
