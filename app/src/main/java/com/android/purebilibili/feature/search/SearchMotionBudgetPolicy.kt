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
    startupSettled: Boolean,
    showResults: Boolean,
    query: String
): Boolean {
    return startupSettled && !showResults && query.isBlank()
}

/**
 * Search is a navigation destination rather than an IME-first overlay. Do not focus the empty
 * landing automatically: an automatic IME consumes the first system Back event, making a newly
 * opened search page appear to require two Back actions to leave.
 */
internal fun shouldAutoFocusSearchField(
    @Suppress("UNUSED_PARAMETER") startupSettled: Boolean,
    @Suppress("UNUSED_PARAMETER") query: String,
    @Suppress("UNUSED_PARAMETER") showResults: Boolean = false,
    @Suppress("UNUSED_PARAMETER") autoFocusConsumed: Boolean = false
): Boolean = false

enum class SearchBackAction {
    LEAVE_SEARCH
}

/**
 * Search has one navigation back action. Suggestions, IME focus, and result content are chrome
 * inside the same destination; consuming Back for them makes users press Back twice to leave.
 */
internal fun resolveSearchBackAction(
    @Suppress("UNUSED_PARAMETER") showResults: Boolean,
    @Suppress("UNUSED_PARAMETER") suggestionsVisible: Boolean,
    @Suppress("UNUSED_PARAMETER") searchFieldFocused: Boolean
): SearchBackAction = SearchBackAction.LEAVE_SEARCH

internal fun shouldClearSearchFocusWhenShowingResults(
    showResults: Boolean,
    previousShowResults: Boolean
): Boolean = showResults && !previousShowResults

/**
 * Typing in the results query must keep the result chrome so the top search field is not
 * disposed mid-edit (which drops IME and caret). Only an empty query returns to landing.
 */
internal fun shouldKeepResultsOnQueryChange(
    showResults: Boolean,
    newQuery: String
): Boolean = showResults && newQuery.isNotEmpty()

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
