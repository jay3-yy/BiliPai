package com.android.purebilibili.feature.home.components

import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.BottomBarSearchAutoExpandMode
import com.android.purebilibili.core.store.BottomBarSearchLayoutMode
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
    fun `bilipai floating width uses intrinsic item width when space allows`() {
        val width = resolveBiliPaiFloatingBottomBarWidth(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp,
            labelMode = 0
        )

        assertEquals(328.dp, width)
    }

    @Test
    fun `bilipai floating width keeps safe edge padding on crowded phones`() {
        val width = resolveBiliPaiFloatingBottomBarWidth(
            containerWidth = 393.dp,
            itemCount = 5,
            minEdgePadding = 20.dp,
            labelMode = 0
        )

        assertEquals(353.dp, width)
    }

    @Test
    fun `bilipai floating width follows icon label presentation mode`() {
        val iconOnlyWidth = resolveBiliPaiFloatingBottomBarWidth(
            containerWidth = 393.dp,
            itemCount = 5,
            minEdgePadding = 20.dp,
            labelMode = 1
        )
        val iconAndTextWidth = resolveBiliPaiFloatingBottomBarWidth(
            containerWidth = 393.dp,
            itemCount = 5,
            minEdgePadding = 20.dp,
            labelMode = 0
        )
        val textOnlyWidth = resolveBiliPaiFloatingBottomBarWidth(
            containerWidth = 393.dp,
            itemCount = 5,
            minEdgePadding = 20.dp,
            labelMode = 2
        )

        assertEquals(344.dp, iconOnlyWidth)
        assertEquals(353.dp, iconAndTextWidth)
        assertEquals(353.dp, textOnlyWidth)
    }

    @Test
    fun `bilipai icon only width follows capsule corner radius`() {
        val compactCorners = resolveBiliPaiFloatingBottomBarWidth(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp,
            labelMode = 1,
            cornerRadius = 28.dp
        )
        val rounderCorners = resolveBiliPaiFloatingBottomBarWidth(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp,
            labelMode = 1,
            cornerRadius = 36.dp
        )

        assertEquals(248.dp, compactCorners)
        assertEquals(312.dp, rounderCorners)
    }

    @Test
    fun `bilipai item slot width matches indicator geometry on crowded phones`() {
        val slotWidth = resolveBiliPaiBottomBarItemSlotWidth(
            dockWidth = 353.dp,
            horizontalPadding = 4.dp,
            itemCount = 5
        )

        assertEquals(69.dp, slotWidth)
        assertEquals(
            314.5.dp,
            resolveBiliPaiBottomBarItemCenterX(
                itemIndex = 4,
                itemWidth = slotWidth,
                horizontalPadding = 4.dp
            )
        )
    }

    @Test
    fun `bilipai search entry shares safe floating width while collapsed`() {
        val layout = resolveBiliPaiBottomBarSearchLayout(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp,
            searchEnabled = true,
            searchExpanded = false
        )

        assertEquals(328.dp, layout.dockWidth)
        assertEquals(56.dp, layout.searchWidth)
        assertEquals(8.dp, layout.gap)
        assertEquals(0.dp, layout.minimumIndicatorWidth)
        assertEquals(76.dp, layout.indicatorReferenceWidth)
    }

    @Test
    fun `five item dock keeps indicator aligned to its compressed slot beside search`() {
        val layout = resolveBiliPaiBottomBarSearchLayout(
            containerWidth = 393.dp,
            itemCount = 5,
            minEdgePadding = 20.dp,
            searchEnabled = true,
            searchExpanded = false,
        )

        assertEquals(329.dp, layout.dockWidth)
        assertEquals(0.dp, layout.minimumIndicatorWidth)
        assertEquals(65.8.dp, layout.indicatorReferenceWidth)
        assertTrue(
            resolveBiliPaiBottomBarItemSlotWidth(
                dockWidth = layout.dockWidth,
                horizontalPadding = 12.dp,
                itemCount = 5,
            ) >= 48.dp
        )
    }

    @Test
    fun `five item dock preserves minimum touch width on narrow phone with search`() {
        val layout = resolveBiliPaiBottomBarSearchLayout(
            containerWidth = 360.dp,
            itemCount = 5,
            minEdgePadding = 20.dp,
            searchEnabled = true,
            searchExpanded = false,
        )

        assertTrue(
            resolveBiliPaiBottomBarItemSlotWidth(
                dockWidth = layout.dockWidth,
                horizontalPadding = 12.dp,
                itemCount = 5,
            ) >= 48.dp
        )
    }

    @Test
    fun `search layout stays within available width when minimum targets cannot all fit`() {
        val layout = resolveBiliPaiBottomBarSearchLayout(
            containerWidth = 320.dp,
            itemCount = 5,
            minEdgePadding = 20.dp,
            searchEnabled = true,
            searchExpanded = false,
        )

        assertTrue(layout.dockWidth + layout.gap + layout.searchWidth <= 320.dp)
    }

    @Test
    fun `bilipai search entry keeps dock wider than home capsule when expanded`() {
        val layout = resolveBiliPaiBottomBarSearchLayout(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp,
            searchEnabled = true,
            searchExpanded = true,
            searchLayoutMode = BottomBarSearchLayoutMode.FULL_DOCK
        )

        assertEquals(328.dp, layout.dockWidth)
        assertEquals(56.dp, layout.searchWidth)
        assertEquals(8.dp, layout.gap)
    }

    @Test
    fun `search entry spends edge padding before compressing navigation slots`() {
        val withoutSearch = resolveBiliPaiFloatingBottomBarWidth(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp,
            labelMode = 0,
        )
        val withSearch = resolveBiliPaiBottomBarSearchLayout(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp,
            searchEnabled = true,
            searchExpanded = false,
        )

        assertEquals(withoutSearch, withSearch.dockWidth)
        assertEquals(328.dp, withSearch.dockWidth)
    }

    @Test
    fun `compact search layout keeps home dock with expanded search field`() {
        val layout = resolveBiliPaiBottomBarSearchLayout(
            containerWidth = 393.dp,
            itemCount = 4,
            minEdgePadding = 20.dp,
            searchEnabled = true,
            searchExpanded = true,
            searchLayoutMode = BottomBarSearchLayoutMode.HOME_AND_SEARCH
        )

        assertEquals(resolveBiliPaiBottomBarSearchCircleSize(), layout.dockWidth)
        assertEquals(280.dp, layout.searchWidth)
        assertEquals(8.dp, layout.gap)
    }

    @Test
    fun `full dock search layout keeps compact search visual when auto expanded`() {
        assertEquals(
            false,
            resolveBiliPaiBottomBarSearchFieldExpanded(
                searchExpanded = true,
                searchLayoutMode = BottomBarSearchLayoutMode.FULL_DOCK
            )
        )
    }

    @Test
    fun `compact search layout renders expanded search field`() {
        assertEquals(
            true,
            resolveBiliPaiBottomBarSearchFieldExpanded(
                searchExpanded = true,
                searchLayoutMode = BottomBarSearchLayoutMode.HOME_AND_SEARCH
            )
        )
    }

    @Test
    fun `bilipai expanded home dock copies search circle size`() {
        assertEquals(56.dp, resolveBiliPaiBottomBarSearchCircleSize())
        // The ordinary dock and adjacent search circle share a compact resting height.
        for (searchExpanded in listOf(false, true)) {
            assertEquals(
                52.dp,
                resolveBiliPaiBottomBarDockHeight(searchExpanded, nativeMiuixNavigation = true),
            )
        }
        assertEquals(56.dp, resolveBiliPaiBottomBarDockHeight(searchExpanded = false))
        assertEquals(
            64.dp,
            resolveBiliPaiBottomBarDockHeight(
                searchExpanded = false,
                hasUiSkinDecoration = true,
            )
        )
        assertEquals(resolveBiliPaiBottomBarSearchCircleSize(), resolveBiliPaiBottomBarDockHeight(searchExpanded = true))
        assertEquals(
            resolveBottomBarSkinDockHeight(),
            resolveBiliPaiBottomBarDockHeight(searchExpanded = true, hasUiSkinDecoration = true),
        )
        // 照搬 HyperIsland 的 64/56 静止几何：指示器 = 壳高 × 56/64。
        // 64dp 壳 → 56dp 指示器（上下各 4dp）；56dp 短壳按同一比例得 49dp。
        assertEquals(49.dp, resolveBiliPaiBottomBarIndicatorHeight(56.dp))
        assertEquals(56.dp, resolveBiliPaiBottomBarIndicatorHeight(64.dp))
        assertEquals(56.dp, resolveBiliPaiBottomBarSearchHeight(searchExpanded = false))
        assertEquals(48.dp, resolveBiliPaiBottomBarSearchHeight(searchExpanded = true))
    }

    @Test
    fun `bilipai expanded home icon matches compact search icon size`() {
        assertEquals(28.dp, resolveBiliPaiExpandedHomeIconSize())
        assertEquals(0.92f, resolveBiliPaiExpandedHomeIconScale(), 0.001f)
    }

    @Test
    fun `bottom bar refraction capture follows full visible bar while search is enabled`() {
        val captureWidth = resolveBottomBarRefractionCaptureWidth(
            dockWidth = 289.dp,
            launchAdjustedSearchGap = 8.dp,
            searchWidth = 56.dp,
            searchEnabled = true
        )

        assertEquals(353.dp, captureWidth)
    }

    @Test
    fun `bottom bar refraction capture matches dock width without search`() {
        val captureWidth = resolveBottomBarRefractionCaptureWidth(
            dockWidth = 353.dp,
            launchAdjustedSearchGap = 10.dp,
            searchWidth = 64.dp,
            searchEnabled = false
        )

        assertEquals(353.dp, captureWidth)
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
    fun `bottom search auto expansion follows threshold bucket not raw scroll pixels`() {
        assertEquals(
            true,
            shouldAutoExpandBottomBarSearchAtThreshold(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
                isPastTopThreshold = false
            )
        )
        assertEquals(
            true,
            shouldAutoExpandBottomBarSearchAtThreshold(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_WHEN_SCROLLING_DOWN,
                isPastTopThreshold = true
            )
        )
        assertEquals(
            false,
            shouldAutoExpandBottomBarSearchAtThreshold(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
                isPastTopThreshold = true
            )
        )
        assertEquals(
            false,
            shouldAutoExpandBottomBarSearchAtThreshold(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                autoExpandMode = BottomBarSearchAutoExpandMode.DISABLED,
                isPastTopThreshold = false
            )
        )
    }

    @Test
    fun `bottom search auto expand stays gated to the home item`() {
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
    fun `search layout stays reserved after leaving home so dock geometry does not jump`() {
        assertEquals(true, shouldReserveBottomBarSearchLayout(bottomBarSearchEnabled = true))
        assertEquals(false, shouldReserveBottomBarSearchLayout(bottomBarSearchEnabled = false))
        assertEquals(
            resolveBiliPaiBottomBarSearchLayout(
                containerWidth = 393.dp,
                itemCount = 4,
                minEdgePadding = 20.dp,
                searchEnabled = true,
                searchExpanded = false,
            ).dockWidth,
            resolveBiliPaiBottomBarSearchLayout(
                containerWidth = 393.dp,
                itemCount = 4,
                minEdgePadding = 20.dp,
                searchEnabled = shouldReserveBottomBarSearchLayout(true),
                searchExpanded = false,
            ).dockWidth,
        )
    }

    @Test
    fun `bottom search mode preserves configured dock tabs`() {
        assertEquals(
            listOf(
                BottomNavItem.HOME,
                BottomNavItem.DYNAMIC,
                BottomNavItem.HISTORY,
                BottomNavItem.PROFILE
            ),
            resolveBottomBarVisibleItemsForSearchMode(
                visibleItems = listOf(
                    BottomNavItem.HOME,
                    BottomNavItem.DYNAMIC,
                    BottomNavItem.HISTORY,
                    BottomNavItem.PROFILE
                ),
                bottomBarSearchEnabled = true
            )
        )
        assertEquals(
            listOf(BottomNavItem.DYNAMIC, BottomNavItem.HISTORY),
            resolveBottomBarVisibleItemsForSearchMode(
                visibleItems = listOf(BottomNavItem.DYNAMIC, BottomNavItem.HISTORY),
                bottomBarSearchEnabled = true
            )
        )
        assertEquals(
            listOf(BottomNavItem.HOME, BottomNavItem.DYNAMIC),
            resolveBottomBarVisibleItemsForSearchMode(
                visibleItems = listOf(BottomNavItem.HOME, BottomNavItem.DYNAMIC),
                bottomBarSearchEnabled = false
            )
        )
    }

    @Test
    fun `compact bottom search mode preserves tabs for unfolding`() {
        assertEquals(
            listOf(BottomNavItem.HOME, BottomNavItem.DYNAMIC, BottomNavItem.HISTORY, BottomNavItem.PROFILE),
            resolveBottomBarVisibleItemsForSearchMode(
                visibleItems = listOf(
                    BottomNavItem.HOME,
                    BottomNavItem.DYNAMIC,
                    BottomNavItem.HISTORY,
                    BottomNavItem.PROFILE
                ),
                bottomBarSearchEnabled = true,
                searchLayoutMode = BottomBarSearchLayoutMode.HOME_AND_SEARCH
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
    fun `bottom search override reset only follows route enabled state and threshold bucket`() {
        assertEquals(
            false,
            shouldResetBottomBarSearchExpansionOverride(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                shouldAutoExpand = true,
                isPastTopThreshold = false
            )
        )
        assertEquals(
            true,
            shouldResetBottomBarSearchExpansionOverride(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                shouldAutoExpand = false,
                isPastTopThreshold = true
            )
        )
        assertEquals(
            true,
            shouldResetBottomBarSearchExpansionOverride(
                currentItem = BottomNavItem.DYNAMIC,
                bottomBarSearchEnabled = true,
                shouldAutoExpand = false,
                isPastTopThreshold = false
            )
        )
    }

    @Test
    fun `bottom bar search performance guards keep expensive layers transient`() {
        assertEquals(
            false,
            shouldRenderBottomBarRefractionCapture(
                glassEnabled = true,
                hasBackdrop = true,
                captureProgress = 0.01f,
                isFeedScrollInProgress = false
            )
        )
        assertEquals(
            true,
            shouldRenderBottomBarRefractionCapture(
                glassEnabled = true,
                hasBackdrop = true,
                captureProgress = 0.01f,
                isFeedScrollInProgress = false,
                isBottomBarInteractionActive = true
            )
        )
        assertEquals(
            false,
            shouldRenderBottomBarRefractionCapture(
                glassEnabled = true,
                hasBackdrop = true,
                captureProgress = 0.001f,
                isFeedScrollInProgress = false
            )
        )
        assertEquals(
            false,
            shouldRenderBottomBarRefractionCapture(
                glassEnabled = false,
                hasBackdrop = true,
                captureProgress = 0.3f,
                isFeedScrollInProgress = false
            )
        )
        assertEquals(
            true,
            shouldComposeBottomBarDockContent(
                dockContentAlpha = 0.02f,
                effectiveSearchExpanded = true
            )
        )
        assertEquals(
            false,
            shouldComposeBottomBarDockContent(
                dockContentAlpha = 0f,
                effectiveSearchExpanded = true
            )
        )
        assertEquals(
            true,
            shouldComposeBottomBarDockContent(
                dockContentAlpha = 0f,
                effectiveSearchExpanded = false
            )
        )
    }

    @Test
    fun `feed scrolling skips idle bottom bar refraction capture`() {
        assertEquals(
            false,
            shouldRenderBottomBarRefractionCapture(
                glassEnabled = true,
                hasBackdrop = true,
                captureProgress = 0.01f,
                isFeedScrollInProgress = true,
                isBottomBarInteractionActive = false
            )
        )
    }

    @Test
    fun `feed scrolling keeps bottom bar refraction capture during direct interaction`() {
        assertEquals(
            true,
            shouldRenderBottomBarRefractionCapture(
                glassEnabled = true,
                hasBackdrop = true,
                captureProgress = 0.01f,
                isFeedScrollInProgress = true,
                isBottomBarInteractionActive = true
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
    fun `home icon click does not toggle bottom search`() {
        assertEquals(
            null,
            resolveBottomBarSearchExpansionOverrideOnNavItemClick(
                currentItem = BottomNavItem.HOME,
                clickedItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = false
            )
        )
        assertEquals(
            null,
            resolveBottomBarSearchExpansionOverrideOnNavItemClick(
                currentItem = BottomNavItem.HOME,
                clickedItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = true
            )
        )
    }

    @Test
    fun `search tab click opens search entry instead of toggling dock into compact mode`() {
        assertEquals(
            null,
            resolveBottomBarSearchExpansionOverrideOnSearchClick(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = false
            )
        )
        assertEquals(
            null,
            resolveBottomBarSearchExpansionOverrideOnSearchClick(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = true
            )
        )
        assertEquals(
            null,
            resolveBottomBarSearchExpansionOverrideOnSearchClick(
                currentItem = BottomNavItem.HISTORY,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = false
            )
        )
    }

    @Test
    fun `compact search layout toggles expanded search from search tab click`() {
        assertEquals(
            BottomBarSearchExpansionOverride.EXPANDED,
            resolveBottomBarSearchExpansionOverrideOnSearchClick(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = false,
                searchLayoutMode = BottomBarSearchLayoutMode.HOME_AND_SEARCH
            )
        )
        assertEquals(
            BottomBarSearchExpansionOverride.COLLAPSED,
            resolveBottomBarSearchExpansionOverrideOnSearchClick(
                currentItem = BottomNavItem.HOME,
                bottomBarSearchEnabled = true,
                effectiveSearchExpanded = true,
                searchLayoutMode = BottomBarSearchLayoutMode.HOME_AND_SEARCH
            )
        )
    }

    @Test
    fun `bottom search collapses after keyword submit and keeps blank submit open`() {
        assertEquals(
            BottomBarSearchExpansionOverride.COLLAPSED,
            resolveBottomBarSearchExpansionOverrideAfterSubmit(hasKeyword = true)
        )
        assertEquals(
            null,
            resolveBottomBarSearchExpansionOverrideAfterSubmit(hasKeyword = false)
        )
    }

    @Test
    fun `bottom search ime is user tap only`() {
        assertEquals(true, shouldRequestBottomBarSearchIme(pendingUserImeRequest = true))
        assertEquals(false, shouldRequestBottomBarSearchIme(pendingUserImeRequest = false))
    }

    @Test
    fun `bottom search ime is dismissed while scrolling expanded capsule`() {
        assertEquals(
            true,
            shouldDismissBottomBarSearchImeOnScroll(isScrolling = true, isSearchExpanded = true)
        )
        assertEquals(
            false,
            shouldDismissBottomBarSearchImeOnScroll(isScrolling = true, isSearchExpanded = false)
        )
        assertEquals(
            false,
            shouldDismissBottomBarSearchImeOnScroll(isScrolling = false, isSearchExpanded = true)
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

    @Test
    fun `plain md3 floating bar uses official floating toolbar`() {
        assertEquals(
            true,
            shouldUseOfficialMd3FloatingToolbar(
                isFloating = true,
                liquidGlassEnabled = false,
            ),
        )
        assertEquals(
            false,
            shouldUseOfficialMd3FloatingToolbar(
                isFloating = true,
                liquidGlassEnabled = true,
            ),
        )
        assertEquals(
            false,
            shouldUseOfficialMd3FloatingToolbar(
                isFloating = false,
                liquidGlassEnabled = false,
            ),
        )
    }
}
