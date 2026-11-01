package com.android.purebilibili.feature.home.policy

import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.feature.home.HomeCategory
import com.android.purebilibili.feature.home.PopularSubCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeFeedScrollRestorePolicyTest {

    @Test
    fun changingLayoutKeepsItsOwnViewportInsteadOfApplyingTheOtherLayoutsIndex() {
        val anchor = captureHomeFeedScrollAnchor(
            HomeCategory.RECOMMEND, PopularSubCategory.COMPREHENSIVE,
            firstVisibleItemIndex = 30,
            firstVisibleItemScrollOffset = 20,
            headerOffsetPx = -28f,
            regularGrid = false,
        )
        assertEquals(
            HomeFeedScrollRestorePlan(restoreGrid = false, restoreHeader = true),
            resolveHomeFeedScrollRestorePlan(4, 12, 0f, anchor, currentRegularGrid = true),
        )
        assertEquals(
            HomeFeedScrollRestorePlan(restoreGrid = true, restoreHeader = true),
            resolveHomeFeedScrollRestorePlan(4, 12, 0f, anchor, currentRegularGrid = false),
        )
    }

    @Test
    fun capture_normalizesNegativeScrollValues() {
        val anchor = captureHomeFeedScrollAnchor(
            category = HomeCategory.RECOMMEND,
            popularSubCategory = PopularSubCategory.COMPREHENSIVE,
            firstVisibleItemIndex = -2,
            firstVisibleItemScrollOffset = -8,
            headerOffsetPx = -42f
        )

        assertEquals(0, anchor.firstVisibleItemIndex)
        assertEquals(0, anchor.firstVisibleItemScrollOffset)
        assertEquals(-42f, anchor.headerOffsetPx)
    }

    @Test
    fun restoreGate_requiresTopLevelSyncedAnchor() {
        val anchor = captureHomeFeedScrollAnchor(
            category = HomeCategory.FOLLOW,
            popularSubCategory = PopularSubCategory.COMPREHENSIVE,
            firstVisibleItemIndex = 3,
            firstVisibleItemScrollOffset = 20,
            headerOffsetPx = -16f
        )

        assertFalse(
            shouldRestoreHomeFeedScrollAnchor(
                isTopLevelActive = false,
                hasSyncedPagerWithState = true,
                anchor = anchor
            )
        )
        assertFalse(
            shouldRestoreHomeFeedScrollAnchor(
                isTopLevelActive = true,
                hasSyncedPagerWithState = false,
                anchor = anchor
            )
        )
        assertFalse(
            shouldRestoreHomeFeedScrollAnchor(
                isTopLevelActive = true,
                hasSyncedPagerWithState = true,
                anchor = null
            )
        )
        assertTrue(
            shouldRestoreHomeFeedScrollAnchor(
                isTopLevelActive = true,
                hasSyncedPagerWithState = true,
                anchor = anchor
            )
        )
    }

    @Test
    fun restorePlan_correctsGridAndHeaderIndependently() {
        val anchor = captureHomeFeedScrollAnchor(
            category = HomeCategory.RECOMMEND,
            popularSubCategory = PopularSubCategory.COMPREHENSIVE,
            firstVisibleItemIndex = 4,
            firstVisibleItemScrollOffset = 36,
            headerOffsetPx = -28f
        )

        assertEquals(
            HomeFeedScrollRestorePlan(restoreGrid = false, restoreHeader = false),
            resolveHomeFeedScrollRestorePlan(
                currentIndex = 4,
                currentOffset = 36,
                currentHeaderOffsetPx = -28f,
                anchor = anchor
            )
        )
        assertEquals(
            HomeFeedScrollRestorePlan(restoreGrid = true, restoreHeader = false),
            resolveHomeFeedScrollRestorePlan(
                currentIndex = 5,
                currentOffset = 36,
                currentHeaderOffsetPx = -28f,
                anchor = anchor
            )
        )
        assertEquals(
            HomeFeedScrollRestorePlan(restoreGrid = true, restoreHeader = false),
            resolveHomeFeedScrollRestorePlan(
                currentIndex = 4,
                currentOffset = 80,
                currentHeaderOffsetPx = -28f,
                anchor = anchor
            )
        )
        assertEquals(
            HomeFeedScrollRestorePlan(restoreGrid = false, restoreHeader = true),
            resolveHomeFeedScrollRestorePlan(
                currentIndex = 4,
                currentOffset = 36,
                currentHeaderOffsetPx = 0f,
                anchor = anchor
            )
        )
        assertEquals(
            HomeFeedScrollRestorePlan(restoreGrid = true, restoreHeader = true),
            resolveHomeFeedScrollRestorePlan(
                currentIndex = 5,
                currentOffset = 80,
                currentHeaderOffsetPx = 0f,
                anchor = anchor
            )
        )
    }

    @Test
    fun restorePlan_preservesIndependentToleranceBoundaries() {
        val anchor = captureHomeFeedScrollAnchor(
            category = HomeCategory.POPULAR,
            popularSubCategory = PopularSubCategory.COMPREHENSIVE,
            firstVisibleItemIndex = 4,
            firstVisibleItemScrollOffset = 36,
            headerOffsetPx = -28f
        )
        for (offset in listOf(35, 36, 37)) {
            for (header in listOf(-28.5f, -28f, -27.5f)) {
                assertEquals(
                    HomeFeedScrollRestorePlan(restoreGrid = false, restoreHeader = false),
                    resolveHomeFeedScrollRestorePlan(4, offset, header, anchor)
                )
            }
        }
        assertEquals(
            HomeFeedScrollRestorePlan(restoreGrid = true, restoreHeader = false),
            resolveHomeFeedScrollRestorePlan(4, 34, -28.5f, anchor)
        )
        assertEquals(
            HomeFeedScrollRestorePlan(restoreGrid = false, restoreHeader = true),
            resolveHomeFeedScrollRestorePlan(4, 37, -27.25f, anchor)
        )
    }

    @Test
    fun bottomBarListPadding_staysReservedForScrollHideAndAlwaysVisible() {
        assertFalse(
            shouldReserveHomeBottomBarListPadding(
                useSideNavigation = true,
                bottomBarVisibilityMode = SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE
            )
        )
        assertFalse(
            shouldReserveHomeBottomBarListPadding(
                useSideNavigation = false,
                bottomBarVisibilityMode = SettingsManager.BottomBarVisibilityMode.ALWAYS_HIDDEN
            )
        )
        assertTrue(
            shouldReserveHomeBottomBarListPadding(
                useSideNavigation = false,
                bottomBarVisibilityMode = SettingsManager.BottomBarVisibilityMode.ALWAYS_VISIBLE
            )
        )
        assertTrue(
            shouldReserveHomeBottomBarListPadding(
                useSideNavigation = false,
                bottomBarVisibilityMode = SettingsManager.BottomBarVisibilityMode.SCROLL_HIDE
            )
        )
    }
}
