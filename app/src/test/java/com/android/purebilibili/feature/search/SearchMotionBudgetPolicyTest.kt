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
    fun landingDataStartsForEmptyLandingWithoutWaitingForMotion() {
        assertTrue(shouldBootstrapSearchLandingData(showResults = false, query = ""))
        assertFalse(shouldBootstrapSearchLandingData(showResults = true, query = ""))
        assertFalse(shouldBootstrapSearchLandingData(showResults = false, query = "test"))
    }

    @Test
    fun autoFocus_waitsForEntryAndOnlyStartsForAnEmptyDraft() {
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
        assertTrue(
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
    fun emptySearchEntry_returnsFromResultsToLandingAndThenLeavesSearch() {
        for (focused in listOf(false, true)) {
            for (suggestions in listOf(false, true)) {
                assertEquals(
                    SearchBackAction.RETURN_TO_LANDING,
                    resolveSearchBackAction(true, suggestions, focused, openedWithKeyword = false),
                )
                assertEquals(
                    SearchBackAction.LEAVE_SEARCH,
                    resolveSearchBackAction(false, suggestions, focused, openedWithKeyword = false),
                )
            }
        }
    }

    @Test
    fun keywordSearchEntry_leavesResultsWithoutInventingLandingHistory() {
        for (focused in listOf(false, true)) {
            for (suggestions in listOf(false, true)) {
                assertEquals(
                    SearchBackAction.LEAVE_SEARCH,
                    resolveSearchBackAction(true, suggestions, focused, openedWithKeyword = true),
                )
                assertEquals(
                    SearchBackAction.LEAVE_SEARCH,
                    resolveSearchBackAction(false, suggestions, focused, openedWithKeyword = true),
                )
            }
        }
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
            SearchUiState(showResults = true).withEditedSearchQuery("129x").showResults
        )
        assertTrue(
            SearchUiState(showResults = true).withEditedSearchQuery("12").showResults
        )
        assertTrue(
            SearchUiState(showResults = true).withEditedSearchQuery("").showResults
        )
        assertFalse(
            SearchUiState(showResults = false).withEditedSearchQuery("129").showResults
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
        assertTrue(
            SearchUiState(showResults = true).withEditedSearchQuery("").showResults
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
            SearchUiState(showResults = true).withEditedSearchQuery("动").showResults
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
}
