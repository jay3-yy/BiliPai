package com.android.purebilibili.feature.home.policy

import com.android.purebilibili.feature.home.HomeCategory
import com.android.purebilibili.feature.home.HomeTopTabEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomePagerSyncPolicyTest {

    @Test
    fun topPagerIgnoresBackGestureWhileVideoDetailCoversHome() {
        assertFalse(shouldEnableHomeTopPagerUserScroll(isTopLevelActive = false))
        assertTrue(shouldEnableHomeTopPagerUserScroll(isTopLevelActive = true))
    }

    @Test
    fun topPagerEnablesUserScrollEvenWhenHideTopTabsIsTrue() {
        assertTrue(
            shouldEnableHomeTopPagerUserScroll(
                isTopLevelActive = true,
                hideTopTabs = true
            )
        )
        assertFalse(
            shouldEnableHomeTopPagerUserScroll(
                isTopLevelActive = false,
                hideTopTabs = true
            )
        )
    }

    @Test
    fun pagerToCategorySync_waitsUntilScrollingStops() {
        val shouldSwitch = shouldSwitchHomeCategoryFromPager(
            isTopLevelActive = true,
            hasSyncedPagerWithState = true,
            pagerCurrentPage = 2,
            pagerScrolling = true,
            currentCategoryIndex = 1
        )

        assertFalse(shouldSwitch)
    }

    @Test
    fun pagerToCategorySync_requiresInitialSync() {
        val shouldSwitch = shouldSwitchHomeCategoryFromPager(
            isTopLevelActive = true,
            hasSyncedPagerWithState = false,
            pagerCurrentPage = 2,
            pagerScrolling = false,
            currentCategoryIndex = 1
        )

        assertFalse(shouldSwitch)
    }

    @Test
    fun pagerToCategorySync_switchesOnlyWhenSettledPageDiffers() {
        val shouldSwitch = shouldSwitchHomeCategoryFromPager(
            isTopLevelActive = true,
            hasSyncedPagerWithState = true,
            pagerCurrentPage = 2,
            pagerScrolling = false,
            currentCategoryIndex = 1
        )

        assertTrue(shouldSwitch)
    }

    @Test
    fun pagerToCategorySync_pausesWhileVideoDetailCoversHome() {
        val shouldSwitch = shouldSwitchHomeCategoryFromPager(
            isTopLevelActive = false,
            hasSyncedPagerWithState = true,
            pagerCurrentPage = 1,
            pagerScrolling = false,
            currentCategoryIndex = 2
        )

        assertFalse(shouldSwitch)
    }

    @Test
    fun pagerToCategorySync_waitsDuringProgrammaticPageSwitch() {
        val shouldSwitch = shouldSwitchHomeCategoryFromPager(
            isTopLevelActive = true,
            hasSyncedPagerWithState = true,
            pagerCurrentPage = 0,
            pagerScrolling = false,
            currentCategoryIndex = 1,
            programmaticPageSwitchInProgress = true
        )

        assertFalse(shouldSwitch)
    }

    @Test
    fun pagerSettledAction_switchesToLive_whenSettledCategoryIsLive() {
        val action = resolveHomePagerSettledAction(
            isTopLevelActive = true,
            hasSyncedPagerWithState = true,
            pagerCurrentPage = 2,
            pagerScrolling = false,
            currentCategoryIndex = 1,
            settledCategory = HomeCategory.LIVE
        )

        assertEquals(HomePagerSettledAction.SWITCH_CATEGORY, action)
    }

    @Test
    fun homeTopLiveAndAnimeCategories_displayInlineIndependentPages() {
        assertTrue(shouldDisplayHomeTopCategoryInline(HomeCategory.LIVE))
        assertTrue(shouldDisplayHomeTopCategoryInline(HomeCategory.ANIME))
        assertTrue(shouldDisplayHomeTopCategoryInline(HomeCategory.RECOMMEND))
    }

    @Test
    fun pagerSettledAction_switchesToAnime_whenSettledCategoryIsAnime() {
        val action = resolveHomePagerSettledAction(
            isTopLevelActive = true,
            hasSyncedPagerWithState = true,
            pagerCurrentPage = 2,
            pagerScrolling = false,
            currentCategoryIndex = 1,
            settledCategory = HomeCategory.ANIME
        )

        assertEquals(HomePagerSettledAction.SWITCH_CATEGORY, action)
    }

    @Test
    fun pagerSettledAction_switchesCategory_forRegularSettledCategory() {
        val action = resolveHomePagerSettledAction(
            isTopLevelActive = true,
            hasSyncedPagerWithState = true,
            pagerCurrentPage = 2,
            pagerScrolling = false,
            currentCategoryIndex = 1,
            settledCategory = HomeCategory.POPULAR
        )

        assertEquals(HomePagerSettledAction.SWITCH_CATEGORY, action)
    }

    @Test
    fun pagerSettledAction_isNone_whenPagerShouldNotSync() {
        val action = resolveHomePagerSettledAction(
            isTopLevelActive = true,
            hasSyncedPagerWithState = true,
            pagerCurrentPage = 1,
            pagerScrolling = false,
            currentCategoryIndex = 1,
            settledCategory = HomeCategory.LIVE
        )

        assertEquals(HomePagerSettledAction.NONE, action)
    }

    @Test
    fun pagerSettledAction_isNone_duringProgrammaticPageSwitch() {
        val action = resolveHomePagerSettledAction(
            isTopLevelActive = true,
            hasSyncedPagerWithState = true,
            pagerCurrentPage = 0,
            pagerScrolling = false,
            currentCategoryIndex = 1,
            settledCategory = HomeCategory.RECOMMEND,
            programmaticPageSwitchInProgress = true
        )

        assertEquals(HomePagerSettledAction.NONE, action)
    }

    @Test
    fun initialPagerSync_usesSnapWhenTargetExists() {
        assertTrue(
            shouldUseInitialHomePagerSnap(
                hasSyncedPagerWithState = false,
                targetPage = 0
            )
        )
    }

    @Test
    fun restoredPagerPage_requiresResync_whenItDoesNotMatchCurrentCategory() {
        assertFalse(
            shouldTreatInitialHomePagerPageAsSyncedWithState(
                initialEntry = HomeTopTabEntry.Category(HomeCategory.POPULAR),
                currentCategory = HomeCategory.RECOMMEND
            )
        )
    }

    @Test
    fun pagerStateDrive_skipsWhenCategoryWasAlreadyDriven() {
        assertTrue(
            shouldSkipHomePagerStateDrive(
                hasSyncedPagerWithState = true,
                lastDrivenCategory = HomeCategory.RECOMMEND,
                currentCategory = HomeCategory.RECOMMEND
            )
        )
        assertFalse(
            shouldSkipHomePagerStateDrive(
                hasSyncedPagerWithState = true,
                lastDrivenCategory = HomeCategory.RECOMMEND,
                currentCategory = HomeCategory.LIVE
            )
        )
    }

    @Test
    fun pagerAnimation_skipsWhenAlreadyOnTarget() {
        assertFalse(
            shouldAnimateHomePagerToCategory(
                hasSyncedPagerWithState = true,
                targetPage = 2,
                pagerCurrentPage = 2,
                pagerScrolling = false,
                programmaticPageSwitchInProgress = false
            )
        )
    }

    @Test
    fun pagerAnimation_skipsDuplicateStateSyncDuringProgrammaticTopTabSelection() {
        assertFalse(
            shouldAnimateHomePagerToCategory(
                hasSyncedPagerWithState = true,
                targetPage = 3,
                pagerCurrentPage = 1,
                pagerScrolling = false,
                programmaticPageSwitchInProgress = true
            )
        )
    }

    @Test
    fun pagerAnimation_runsAfterInitialSyncWhenPagerIsIdle() {
        assertTrue(
            shouldAnimateHomePagerToCategory(
                hasSyncedPagerWithState = true,
                targetPage = 3,
                pagerCurrentPage = 1,
                pagerScrolling = false,
                programmaticPageSwitchInProgress = false
            )
        )
    }

    @Test
    fun initialTopTabPage_restoresPartitionDisplayedIndex() {
        val entries = listOf(
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
            HomeTopTabEntry.Category(HomeCategory.POPULAR),
            HomeTopTabEntry.Partition
        )

        assertEquals(
            2,
            resolveHomeInitialTopTabPage(
                topTabEntries = entries,
                currentCategory = HomeCategory.RECOMMEND,
                displayedTabIndex = 2
            )
        )
        assertTrue(
            shouldTreatInitialHomePagerPageAsSyncedWithState(
                initialEntry = entries[2],
                currentCategory = HomeCategory.RECOMMEND
            )
        )
    }

    @Test
    fun initialTopTabPage_ignoresStaleCategoryDisplayedIndex() {
        val entries = listOf(
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
            HomeTopTabEntry.Category(HomeCategory.POPULAR),
            HomeTopTabEntry.Partition
        )

        assertEquals(
            1,
            resolveHomeInitialTopTabPage(
                topTabEntries = entries,
                currentCategory = HomeCategory.POPULAR,
                displayedTabIndex = 0
            )
        )
    }

    @Test
    fun pagerRestore_usesCurrentCategoryInsteadOfStaleRetainedCategory() {
        val reorderedEntries = listOf(
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
            HomeTopTabEntry.Category(HomeCategory.POPULAR),
            HomeTopTabEntry.Category(HomeCategory.FOLLOW)
        )

        assertEquals(
            1,
            resolveHomePagerTargetPage(
                topTabEntries = reorderedEntries,
                retainedEntry = HomeTopTabEntry.Category(HomeCategory.FOLLOW),
                currentCategory = HomeCategory.POPULAR,
                hasSyncedPagerWithState = false
            )
        )
    }

    @Test
    fun pagerRestore_resolvesPopularByStableEntryWhenRecommendMovesBack() {
        val reorderedEntries = listOf(
            HomeTopTabEntry.Category(HomeCategory.FOLLOW),
            HomeTopTabEntry.Category(HomeCategory.POPULAR),
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND)
        )

        assertEquals(
            1,
            resolveHomePagerTargetPage(
                topTabEntries = reorderedEntries,
                retainedEntry = HomeTopTabEntry.Category(HomeCategory.POPULAR),
                currentCategory = HomeCategory.POPULAR,
                hasSyncedPagerWithState = false
            )
        )
    }

    @Test
    fun pagerRestore_keepsPartitionInsteadOfFallingBackToCurrentCategory() {
        val entries = listOf(
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
            HomeTopTabEntry.Partition,
            HomeTopTabEntry.Category(HomeCategory.POPULAR)
        )

        assertEquals(
            1,
            resolveHomePagerTargetPage(
                topTabEntries = entries,
                retainedEntry = HomeTopTabEntry.Partition,
                currentCategory = HomeCategory.RECOMMEND,
                hasSyncedPagerWithState = false
            )
        )
    }

    @Test
    fun pagerStateDrive_followsCurrentCategoryAfterReturnSyncCompletes() {
        val entries = listOf(
            HomeTopTabEntry.Category(HomeCategory.FOLLOW),
            HomeTopTabEntry.Category(HomeCategory.POPULAR),
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND)
        )

        assertEquals(
            2,
            resolveHomePagerTargetPage(
                topTabEntries = entries,
                retainedEntry = HomeTopTabEntry.Category(HomeCategory.FOLLOW),
                currentCategory = HomeCategory.RECOMMEND,
                hasSyncedPagerWithState = true
            )
        )
    }

    @Test
    fun pagerRestore_fallsBackToCurrentCategoryWhenRetainedEntryWasHidden() {
        val entries = listOf(
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
            HomeTopTabEntry.Category(HomeCategory.POPULAR)
        )

        assertEquals(
            1,
            resolveHomePagerTargetPage(
                topTabEntries = entries,
                retainedEntry = HomeTopTabEntry.Category(HomeCategory.FOLLOW),
                currentCategory = HomeCategory.POPULAR,
                hasSyncedPagerWithState = false
            )
        )
    }

    @Test
    fun customTabOrder_restoresEveryCategoryStablyWithoutCyclingOnReturn() {
        val customEntries = listOf(
            HomeTopTabEntry.Category(HomeCategory.FOLLOW),
            HomeTopTabEntry.Category(HomeCategory.LIVE),
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
            HomeTopTabEntry.Category(HomeCategory.POPULAR)
        )

        customEntries.forEachIndexed { expectedIndex, entry ->
            val category = (entry as HomeTopTabEntry.Category).category
            assertEquals(
                expectedIndex,
                resolveHomeInitialTopTabPage(
                    topTabEntries = customEntries,
                    currentCategory = category,
                    displayedTabIndex = expectedIndex,
                    displayedTabEntry = entry
                )
            )
            assertTrue(
                shouldTreatInitialHomePagerPageAsSyncedWithState(
                    initialEntry = entry,
                    currentCategory = category,
                    displayedTabEntry = entry
                )
            )
        }
    }

    @Test
    fun initialTopTabPage_doesNotJumpToPartitionWhenPartitionMovesIntoOldCategorySlot() {
        val reorderedEntries = listOf(
            HomeTopTabEntry.Category(HomeCategory.FOLLOW),
            HomeTopTabEntry.Category(HomeCategory.LIVE),
            HomeTopTabEntry.Partition,
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND)
        )

        assertEquals(
            3,
            resolveHomeInitialTopTabPage(
                topTabEntries = reorderedEntries,
                currentCategory = HomeCategory.RECOMMEND,
                displayedTabIndex = 2,
                displayedTabEntry = HomeTopTabEntry.Category(HomeCategory.RECOMMEND)
            )
        )
        assertFalse(
            shouldTreatInitialHomePagerPageAsSyncedWithState(
                initialEntry = HomeTopTabEntry.Partition,
                currentCategory = HomeCategory.RECOMMEND,
                displayedTabEntry = HomeTopTabEntry.Category(HomeCategory.RECOMMEND)
            )
        )
    }

    @Test
    fun initialTopTabPage_tracksPartitionIdentityWhenPartitionReorders() {
        val reorderedEntries = listOf(
            HomeTopTabEntry.Partition,
            HomeTopTabEntry.Category(HomeCategory.FOLLOW),
            HomeTopTabEntry.Category(HomeCategory.LIVE),
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND)
        )

        assertEquals(
            0,
            resolveHomeInitialTopTabPage(
                topTabEntries = reorderedEntries,
                currentCategory = HomeCategory.RECOMMEND,
                displayedTabIndex = 2,
                displayedTabEntry = HomeTopTabEntry.Partition
            )
        )
        assertFalse(
            shouldTreatInitialHomePagerPageAsSyncedWithState(
                initialEntry = HomeTopTabEntry.Category(HomeCategory.LIVE),
                currentCategory = HomeCategory.RECOMMEND,
                displayedTabEntry = HomeTopTabEntry.Partition
            )
        )
    }

    @Test
    fun hiddenCategoryFallback_targetsFirstVisibleCategoryInsteadOfPartitionAtZero() {
        val entries = listOf(
            HomeTopTabEntry.Partition,
            HomeTopTabEntry.Category(HomeCategory.FOLLOW),
            HomeTopTabEntry.Category(HomeCategory.LIVE)
        )

        assertEquals(
            1 to HomeCategory.FOLLOW,
            resolveHomeFallbackVisibleCategoryTab(
                topTabEntries = entries,
                currentCategory = HomeCategory.RECOMMEND
            )
        )
        assertEquals(
            1,
            resolveHomeInitialTopTabPage(
                topTabEntries = entries,
                currentCategory = HomeCategory.RECOMMEND,
                displayedTabIndex = 0,
                displayedTabEntry = HomeTopTabEntry.Category(HomeCategory.RECOMMEND)
            )
        )
    }

    @Test
    fun externalCategorySwitch_overridesPartitionRetainedEntryAndDrivesPager() {
        val entries = listOf(
            HomeTopTabEntry.Category(HomeCategory.RECOMMEND),
            HomeTopTabEntry.Partition,
            HomeTopTabEntry.Category(HomeCategory.POPULAR)
        )

        assertEquals(
            2,
            resolveHomePagerTargetPage(
                topTabEntries = entries,
                retainedEntry = HomeTopTabEntry.Partition,
                currentCategory = HomeCategory.POPULAR,
                hasSyncedPagerWithState = true,
                displayedTabEntry = HomeTopTabEntry.Category(HomeCategory.POPULAR)
            )
        )
        assertFalse(
            shouldSkipHomePagerStateDrive(
                hasSyncedPagerWithState = true,
                lastDrivenCategory = HomeCategory.POPULAR,
                currentCategory = HomeCategory.POPULAR,
                lastDrivenEntry = HomeTopTabEntry.Partition,
                targetEntry = HomeTopTabEntry.Category(HomeCategory.POPULAR)
            )
        )
    }
}
