package com.android.purebilibili.feature.home.components

import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.BottomBarSearchAutoExpandMode
import com.android.purebilibili.core.theme.AndroidNativeVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BottomBarLayoutPolicyTest {

    @Test
    fun `floating five items keeps compact width with safe per-item size`() {
        val policy = resolveBottomBarLayoutPolicy(
            containerWidth = 393.dp,
            itemCount = 5,
            isTablet = false,
            labelMode = 0,
            isFloating = true
        )

        val perItemWidth = (policy.maxBarWidth - (policy.rowPadding * 2)) / 5
        assertTrue(policy.maxBarWidth.value > 340f)
        assertTrue(policy.horizontalPadding.value < 26f)
        assertTrue(perItemWidth.value >= 52f)
    }

    @Test
    fun `android native md3e bottom bar tuning uses expressive container and indicator`() {
        val material3 = resolveAndroidNativeBottomBarTuning(
            blurEnabled = false,
            darkTheme = false,
            androidNativeVariant = AndroidNativeVariant.MATERIAL3
        )
        val expressive = resolveAndroidNativeBottomBarTuning(
            blurEnabled = false,
            darkTheme = false,
            androidNativeVariant = AndroidNativeVariant.MATERIAL3_EXPRESSIVE
        )

        assertTrue(expressive.cornerRadiusDp > material3.cornerRadiusDp)
        assertTrue(expressive.outerHorizontalPaddingDp > material3.outerHorizontalPaddingDp)
        assertTrue(expressive.indicatorHeightDp > material3.indicatorHeightDp)
        assertTrue(expressive.indicatorLensRadiusDp > material3.indicatorLensRadiusDp)
    }

    @Test
    fun `floating four items can use wider bar than five items`() {
        val policyForFour = resolveBottomBarLayoutPolicy(
            containerWidth = 393.dp,
            itemCount = 4,
            isTablet = false,
            labelMode = 0,
            isFloating = true
        )
        val policyForFive = resolveBottomBarLayoutPolicy(
            containerWidth = 393.dp,
            itemCount = 5,
            isTablet = false,
            labelMode = 0,
            isFloating = true
        )

        assertTrue(policyForFour.maxBarWidth.value > policyForFive.maxBarWidth.value)
    }

    @Test
    fun `kernelsu floating width uses intrinsic item width when space allows`() {
        val width = resolveKernelSuFloatingBottomBarWidth(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp
        )

        assertEquals(312.dp, width)
    }

    @Test
    fun `kernelsu floating width keeps safe edge padding on crowded phones`() {
        val width = resolveKernelSuFloatingBottomBarWidth(
            containerWidth = 393.dp,
            itemCount = 5,
            minEdgePadding = 20.dp
        )

        assertEquals(353.dp, width)
    }

    @Test
    fun `kernelsu search entry shares safe floating width while collapsed`() {
        val layout = resolveKernelSuBottomBarSearchLayout(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp,
            searchEnabled = true,
            searchExpanded = false
        )

        assertEquals(279.dp, layout.dockWidth)
        assertEquals(64.dp, layout.searchWidth)
        assertEquals(10.dp, layout.gap)
    }

    @Test
    fun `kernelsu search entry collapses dock to home capsule when expanded`() {
        val layout = resolveKernelSuBottomBarSearchLayout(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp,
            searchEnabled = true,
            searchExpanded = true
        )

        assertEquals(resolveKernelSuBottomBarSearchCircleSize(), layout.dockWidth)
        assertEquals(279.dp, layout.searchWidth)
        assertEquals(10.dp, layout.gap)
    }

    @Test
    fun `kernelsu expanded home dock copies search circle size`() {
        assertEquals(64.dp, resolveKernelSuBottomBarSearchCircleSize())
        assertEquals(64.dp, resolveKernelSuBottomBarDockHeight(searchExpanded = false))
        assertEquals(resolveKernelSuBottomBarSearchCircleSize(), resolveKernelSuBottomBarDockHeight(searchExpanded = true))
        assertEquals(64.dp, resolveKernelSuBottomBarSearchHeight(searchExpanded = false))
        assertEquals(58.dp, resolveKernelSuBottomBarSearchHeight(searchExpanded = true))
    }

    @Test
    fun `kernelsu expanded home icon matches compact search icon size`() {
        assertEquals(28.dp, resolveKernelSuExpandedHomeIconSize())
        assertEquals(0.92f, resolveKernelSuExpandedHomeIconScale(), 0.001f)
    }

    @Test
    fun `home top automatically expands bottom search`() {
        assertEquals(
            true,
            shouldAutoExpandBottomBarSearch(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
                homeScrollOffsetPx = 0f
            )
        )
        assertEquals(
            true,
            shouldAutoExpandBottomBarSearch(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
                homeScrollOffsetPx = 24f
            )
        )
    }

    @Test
    fun `bottom search auto collapses away from home top in top expand mode`() {
        assertEquals(
            false,
            shouldAutoExpandBottomBarSearch(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
                homeScrollOffsetPx = 96f
            )
        )
        assertEquals(
            false,
            shouldAutoExpandBottomBarSearch(
                currentItem = BottomNavItem.DYNAMIC,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
                homeScrollOffsetPx = 0f
            )
        )
        assertEquals(
            false,
            shouldAutoExpandBottomBarSearch(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = false,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
                homeScrollOffsetPx = 0f
            )
        )
    }

    @Test
    fun `bottom search entry only renders on searchable home item`() {
        assertEquals(
            true,
            resolveBottomBarSearchEnabledForItem(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true
            )
        )
        assertEquals(
            false,
            resolveBottomBarSearchEnabledForItem(
                currentItem = BottomNavItem.DYNAMIC,
                bottomBarSearchEnabled = true
            )
        )
        assertEquals(
            false,
            resolveBottomBarSearchEnabledForItem(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = false
            )
        )
    }

    @Test
    fun `bottom search auto expands away from home top in scroll expand mode`() {
        assertEquals(
            false,
            shouldAutoExpandBottomBarSearch(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_WHEN_SCROLLING_DOWN,
                homeScrollOffsetPx = 0f
            )
        )
        assertEquals(
            true,
            shouldAutoExpandBottomBarSearch(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_WHEN_SCROLLING_DOWN,
                homeScrollOffsetPx = 96f
            )
        )
        assertEquals(
            false,
            shouldAutoExpandBottomBarSearch(
                currentItem = BottomNavItem.DYNAMIC,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_WHEN_SCROLLING_DOWN,
                homeScrollOffsetPx = 96f
            )
        )
    }

    @Test
    fun `bottom search auto expansion can be disabled`() {
        assertEquals(
            false,
            shouldAutoExpandBottomBarSearch(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.DISABLED,
                homeScrollOffsetPx = 0f
            )
        )
        assertEquals(
            false,
            shouldAutoExpandBottomBarSearch(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.DISABLED,
                homeScrollOffsetPx = 96f
            )
        )
    }

    @Test
    fun `manual bottom search override wins over auto expansion`() {
        assertEquals(
            true,
            resolveEffectiveBottomBarSearchExpanded(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                shouldAutoExpand = false,
                expansionOverride = BottomBarSearchExpansionOverride.EXPANDED
            )
        )
        assertEquals(
            false,
            resolveEffectiveBottomBarSearchExpanded(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                shouldAutoExpand = true,
                expansionOverride = BottomBarSearchExpansionOverride.COLLAPSED
            )
        )
    }

    @Test
    fun `non home routes do not expand bottom search`() {
        assertEquals(
            false,
            resolveEffectiveBottomBarSearchExpanded(
                currentItem = BottomNavItem.HISTORY,
                bottomBarSearchEnabled = true,
                shouldAutoExpand = true,
                expansionOverride = BottomBarSearchExpansionOverride.EXPANDED
            )
        )
    }

    @Test
    fun `home icon click toggles search and dock only while already on home`() {
        assertEquals(
            BottomBarSearchExpansionOverride.EXPANDED,
            resolveBottomBarSearchExpansionOverrideOnNavItemClick(
                currentItem = BottomNavItem.HOME,
                clickedItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = false
            )
        )
        assertEquals(
            BottomBarSearchExpansionOverride.COLLAPSED,
            resolveBottomBarSearchExpansionOverrideOnNavItemClick(
                currentItem = BottomNavItem.HOME,
                clickedItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = true
            )
        )
        assertEquals(
            null,
            resolveBottomBarSearchExpansionOverrideOnNavItemClick(
                currentItem = BottomNavItem.HOME,
                clickedItem = BottomNavItem.DYNAMIC,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = false
            )
        )
        assertEquals(
            null,
            resolveBottomBarSearchExpansionOverrideOnNavItemClick(
                currentItem = BottomNavItem.HISTORY,
                clickedItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = false
            )
        )
    }

    @Test
    fun `docked mode stays full width with no horizontal inset`() {
        val policy = resolveBottomBarLayoutPolicy(
            containerWidth = 393.dp,
            itemCount = 5,
            isTablet = false,
            labelMode = 0,
            isFloating = false
        )

        assertEquals(0.dp, policy.horizontalPadding)
        assertEquals(393.dp, policy.maxBarWidth)
    }

    @Test
    fun `floating default bar trims height while keeping touch comfort`() {
        assertEquals(58f, resolveBottomBarFloatingHeightDp(labelMode = 1, isTablet = false))
        assertEquals(12f, resolveBottomBarBottomPaddingDp(isFloating = true, isTablet = false))
    }
}
