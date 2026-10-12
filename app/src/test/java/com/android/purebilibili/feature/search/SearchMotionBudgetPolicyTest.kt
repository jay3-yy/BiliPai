package com.android.purebilibili.feature.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchMotionBudgetPolicyTest {

    @Test
    fun activeSearchInteraction_reducesBudget() {
        assertEquals(
            SearchMotionBudget.REDUCED,
            resolveSearchMotionBudget(
                hasQuery = true,
                isSearching = true,
                isScrolling = false
            )
        )
        assertEquals(
            SearchMotionBudget.REDUCED,
            resolveSearchMotionBudget(
                hasQuery = true,
                isSearching = false,
                isScrolling = true
            )
        )
    }

    @Test
    fun scrollingResults_keepsHazeEnabled() {
        val budget = resolveSearchMotionBudget(
            hasQuery = true,
            isSearching = false,
            isScrolling = true
        )

        assertEquals(SearchMotionBudget.REDUCED, budget)
        assertTrue(
            shouldEnableSearchHazeSource(
                isSearching = false
            )
        )
    }

    @Test
    fun idleSearchState_keepsFullBudgetAndHaze() {
        val budget = resolveSearchMotionBudget(
            hasQuery = false,
            isSearching = false,
            isScrolling = false
        )

        assertEquals(SearchMotionBudget.FULL, budget)
        assertTrue(
            shouldEnableSearchHazeSource(
                isSearching = false,
                startupSettled = true
            )
        )
    }

    @Test
    fun activeSearchRequest_disablesHazeSource() {
        assertFalse(
            shouldEnableSearchHazeSource(
                isSearching = true,
                startupSettled = true
            )
        )
    }

    @Test
    fun activeResultRequest_keepsChromeBackdropSourceMounted() {
        assertTrue(
            shouldKeepSearchChromeBackdropSource(
                progressiveTopBlurEnabled = false,
                liquidGlassEnabled = true,
            )
        )
        assertTrue(
            shouldKeepSearchChromeBackdropSource(
                progressiveTopBlurEnabled = true,
                liquidGlassEnabled = false,
            )
        )
        assertFalse(
            shouldKeepSearchChromeBackdropSource(
                progressiveTopBlurEnabled = false,
                liquidGlassEnabled = false,
            )
        )
    }

    @Test
    fun startupPending_forcesReducedMotionAndDisablesHaze() {
        assertEquals(
            SearchMotionBudget.REDUCED,
            resolveEffectiveSearchMotionBudget(
                startupSettled = false,
                baseBudget = SearchMotionBudget.FULL
            )
        )
        assertFalse(
            shouldEnableSearchHazeSource(
                isSearching = false,
                startupSettled = false
            )
        )
    }

    @Test
    fun landingBootstrap_onlyRunsAfterStartupSettlesOnEmptyLandingState() {
        assertFalse(
            shouldBootstrapSearchLandingData(
                startupSettled = false,
                showResults = false,
                query = ""
            )
        )
        assertFalse(
            shouldBootstrapSearchLandingData(
                startupSettled = true,
                showResults = true,
                query = ""
            )
        )
        assertFalse(
            shouldBootstrapSearchLandingData(
                startupSettled = true,
                showResults = false,
                query = "test"
            )
        )
        assertTrue(
            shouldBootstrapSearchLandingData(
                startupSettled = true,
                showResults = false,
                query = ""
            )
        )
    }

    @Test
    fun autoFocus_isDisabledForSearchDestinationEntry() {
        assertFalse(
            shouldAutoFocusSearchField(
                startupSettled = false,
                query = ""
            )
        )
        assertFalse(
            shouldAutoFocusSearchField(
                startupSettled = true,
                query = "abc"
            )
        )
        assertFalse(
            shouldAutoFocusSearchField(
                startupSettled = true,
                query = ""
            )
        )
    }

    @Test
    fun autoFocus_remainsDisabledForResultsAndConsumedState() {
        assertFalse(
            shouldAutoFocusSearchField(
                startupSettled = true,
                query = "",
                showResults = true
            )
        )
        assertFalse(
            shouldAutoFocusSearchField(
                startupSettled = true,
                query = "",
                autoFocusConsumed = true
            )
        )
    }

    @Test
    fun searchBackAction_alwaysLeavesSearchDestination() {
        assertEquals(
            SearchBackAction.LEAVE_SEARCH,
            resolveSearchBackAction(
                showResults = true,
                suggestionsVisible = true,
                searchFieldFocused = false
            )
        )
        assertEquals(
            SearchBackAction.LEAVE_SEARCH,
            resolveSearchBackAction(
                showResults = true,
                suggestionsVisible = false,
                searchFieldFocused = true
            )
        )
        assertEquals(
            SearchBackAction.LEAVE_SEARCH,
            resolveSearchBackAction(
                showResults = true,
                suggestionsVisible = false,
                searchFieldFocused = false
            )
        )
        assertEquals(
            SearchBackAction.LEAVE_SEARCH,
            resolveSearchBackAction(
                showResults = false,
                suggestionsVisible = false,
                searchFieldFocused = false
            )
        )
    }

    @Test
    fun clearFocus_onlyWhenEnteringResults() {
        assertTrue(
            shouldClearSearchFocusWhenShowingResults(
                showResults = true,
                previousShowResults = false
            )
        )
        assertFalse(
            shouldClearSearchFocusWhenShowingResults(
                showResults = true,
                previousShowResults = true
            )
        )
        assertFalse(
            shouldClearSearchFocusWhenShowingResults(
                showResults = false,
                previousShowResults = true
            )
        )
    }

    @Test
    fun queryEdit_onResultsKeepsResultsChrome() {
        assertTrue(
            shouldKeepResultsOnQueryChange(
                showResults = true,
                newQuery = "129x"
            )
        )
        assertTrue(
            shouldKeepResultsOnQueryChange(
                showResults = true,
                newQuery = "12"
            )
        )
        assertFalse(
            shouldKeepResultsOnQueryChange(
                showResults = true,
                newQuery = ""
            )
        )
        assertFalse(
            shouldKeepResultsOnQueryChange(
                showResults = false,
                newQuery = "129"
            )
        )
    }

    @Test
    fun focusedQueryClear_retainsImeAndIgnoresLandingKeywordUntilGuardElapses() {
        assertEquals(300L, SEARCH_LANDING_KEYWORD_CLICK_GUARD_MS)
        assertTrue(
            shouldRetainSearchImeWhenQueryCleared(
                fieldFocused = true,
                previousQuery = "动画",
                newQuery = ""
            )
        )
        assertFalse(
            shouldKeepResultsOnQueryChange(
                showResults = true,
                newQuery = ""
            )
        )
        val clearedAtElapsedMs = 1_000L
        assertFalse(
            shouldAcceptSearchLandingKeywordClick(
                clearedAtElapsedMs = clearedAtElapsedMs,
                nowElapsedMs = clearedAtElapsedMs + SEARCH_LANDING_KEYWORD_CLICK_GUARD_MS - 1L
            )
        )
        assertTrue(
            shouldAcceptSearchLandingKeywordClick(
                clearedAtElapsedMs = clearedAtElapsedMs,
                nowElapsedMs = clearedAtElapsedMs + SEARCH_LANDING_KEYWORD_CLICK_GUARD_MS
            )
        )
        assertTrue(
            shouldAcceptSearchLandingKeywordClick(
                clearedAtElapsedMs = -1L,
                nowElapsedMs = clearedAtElapsedMs
            )
        )
        assertFalse(
            shouldRetainSearchImeWhenQueryCleared(
                fieldFocused = false,
                previousQuery = "动画",
                newQuery = ""
            )
        )
        assertFalse(
            shouldRetainSearchImeWhenQueryCleared(
                fieldFocused = true,
                previousQuery = "",
                newQuery = ""
            )
        )
        assertFalse(
            shouldRetainSearchImeWhenQueryCleared(
                fieldFocused = true,
                previousQuery = "动画",
                newQuery = "动"
            )
        )
        assertTrue(
            shouldKeepResultsOnQueryChange(
                showResults = true,
                newQuery = "动"
            )
        )
    }

    @Test
    fun scrollingResults_shouldNotForceLowHeaderBlurBudget() {
        assertFalse(
            shouldForceLowBudgetSearchHeaderBlur(
                isSearching = false,
                isScrollingResults = true
            )
        )
    }

    @Test
    fun activeSearchRequest_shouldForceLowHeaderBlurBudget() {
        assertTrue(
            shouldForceLowBudgetSearchHeaderBlur(
                isSearching = true,
                isScrollingResults = false
            )
        )
    }

    @Test
    fun searchCardTransition_followsGlobalSettingRegardlessOfBudget() {
        assertTrue(
            resolveEffectiveSearchCardTransitionEnabled(
                cardTransitionEnabled = true,
                motionBudget = SearchMotionBudget.FULL,
                isReturningFromVideoDetail = false
            )
        )
        assertTrue(
            resolveEffectiveSearchCardTransitionEnabled(
                cardTransitionEnabled = true,
                motionBudget = SearchMotionBudget.REDUCED,
                isReturningFromVideoDetail = false
            )
        )
        assertTrue(
            resolveEffectiveSearchCardTransitionEnabled(
                cardTransitionEnabled = true,
                motionBudget = SearchMotionBudget.REDUCED,
                isReturningFromVideoDetail = true
            )
        )
        assertFalse(
            resolveEffectiveSearchCardTransitionEnabled(
                cardTransitionEnabled = false,
                motionBudget = SearchMotionBudget.FULL,
                isReturningFromVideoDetail = false
            )
        )
    }

    @Test
    fun newSearchSession_resetsResultScroll() {
        assertTrue(
            shouldResetSearchResultScroll(
                searchSessionId = 3L,
                showResults = true,
                lastResetSessionId = 2L
            )
        )
    }

    @Test
    fun restoredSearchScreen_doesNotResetExistingResultScroll() {
        assertFalse(
            shouldResetSearchResultScroll(
                searchSessionId = 3L,
                showResults = true,
                lastResetSessionId = 3L
            )
        )
    }
}
