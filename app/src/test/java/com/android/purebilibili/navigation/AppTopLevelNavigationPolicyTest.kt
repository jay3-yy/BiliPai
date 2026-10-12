package com.android.purebilibili.navigation

import com.android.purebilibili.feature.home.components.BottomNavItem
import com.android.purebilibili.navigation3.BiliPaiNavKey
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppTopLevelNavigationPolicyTest {

    @Test
    fun videoDetailNeverReservesSidebarSpace() {
        // Ordinary playback also needs the full width, even when launched from a sidebar tab.
        assertFalse(shouldMountSidebarForNavigation(true, true))
        assertFalse(shouldMountSidebarForNavigation(false, true))
    }

    @Test
    fun returningFromVideoRestoresSidebarOnAnEligibleDestination() {
        assertTrue(shouldMountSidebarForNavigation(true, false))
        assertFalse(shouldMountSidebarForNavigation(true, true))
        assertTrue(shouldMountSidebarForNavigation(true, false))
    }

    @Test
    fun destinationsWithoutSidebarRemainWithoutSidebar() {
        assertFalse(shouldMountSidebarForNavigation(false, false))
    }

    @Test
    fun returnsSkip_whenCurrentRouteAlreadyMatchesTarget() {
        val action = resolveTopLevelNavigationAction(
            currentRoute = ScreenRoutes.Profile.route,
            targetRoute = ScreenRoutes.Profile.route,
            hasTargetInBackStack = true
        )

        assertEquals(TopLevelNavigationAction.SKIP, action)
    }

    @Test
    fun returnsPopExisting_whenTargetExistsInBackStack() {
        val action = resolveTopLevelNavigationAction(
            currentRoute = ScreenRoutes.History.route,
            targetRoute = ScreenRoutes.Profile.route,
            hasTargetInBackStack = true
        )

        assertEquals(TopLevelNavigationAction.POP_EXISTING, action)
    }

    @Test
    fun returnsNavigateWithRestore_whenTargetNotInBackStack() {
        val action = resolveTopLevelNavigationAction(
            currentRoute = ScreenRoutes.History.route,
            targetRoute = ScreenRoutes.Profile.route,
            hasTargetInBackStack = false
        )

        assertEquals(TopLevelNavigationAction.NAVIGATE_WITH_RESTORE, action)
    }

    @Test
    fun selectedBottomBarTap_requestsReselect_insteadOfNavigate() {
        val action = resolveBottomBarSelectionAction(
            currentItem = BottomNavItem.HOME,
            tappedItem = BottomNavItem.HOME
        )

        assertEquals(BottomBarSelectionAction.RESELECT, action)
    }

    @Test
    fun liveAndWatchLaterBottomBarTaps_alsoUseReselectScrollToTop() {
        assertEquals(
            BottomBarSelectionAction.RESELECT,
            resolveBottomBarSelectionAction(
                currentItem = BottomNavItem.LIVE,
                tappedItem = BottomNavItem.LIVE
            )
        )
        assertTrue(shouldScrollToTopOnBottomBarReselect(BottomNavItem.LIVE))
        assertTrue(shouldScrollToTopOnBottomBarReselect(BottomNavItem.WATCHLATER))
        assertTrue(shouldScrollToTopOnBottomBarReselect(BottomNavItem.HOME))
        assertFalse(shouldScrollToTopOnBottomBarReselect(BottomNavItem.STORY))
        assertFalse(shouldScrollToTopOnBottomBarReselect(BottomNavItem.SETTINGS))
    }

    @Test
    fun matchingHistoryBottomBarTap_alsoUsesReselectAction() {
        assertEquals(
            BottomBarSelectionAction.RESELECT,
            resolveBottomBarSelectionAction(
                currentItem = BottomNavItem.HISTORY,
                tappedItem = BottomNavItem.HISTORY
            )
        )
        assertEquals(
            BottomBarSelectionAction.RESELECT,
            resolveBottomBarSelectionAction(
                currentItem = BottomNavItem.PROFILE,
                tappedItem = BottomNavItem.PROFILE
            )
        )
    }

    @Test
    fun nonReselectBottomBarTap_keepsNavigateAction() {
        assertEquals(
            BottomBarSelectionAction.NAVIGATE,
            resolveBottomBarSelectionAction(
                currentItem = BottomNavItem.HISTORY,
                tappedItem = BottomNavItem.HOME
            )
        )
        assertEquals(
            BottomBarSelectionAction.NAVIGATE,
            resolveBottomBarSelectionAction(
                currentItem = BottomNavItem.HOME,
                tappedItem = BottomNavItem.DYNAMIC
            )
        )
    }

    @Test
    fun systemBackFromRetainedBottomTab_returnsToHomeBeforeFinishingActivity() {
        assertEquals(
            AppSystemBackAction.RETURN_TO_HOME_TAB,
            resolveAppSystemBackAction(
                isAtMainHostRoot = true,
                currentBottomItem = BottomNavItem.FAVORITE,
                homeItem = BottomNavItem.HOME
            )
        )
        assertEquals(
            AppSystemBackAction.RETURN_TO_HOME_TAB,
            resolveAppSystemBackAction(
                isAtMainHostRoot = true,
                currentBottomItem = BottomNavItem.HISTORY,
                homeItem = BottomNavItem.HOME
            )
        )
    }

    @Test
    fun appBackHandlerInterceptsOnlyAppOwnedBackActions() {
        assertTrue(
            shouldInterceptSystemBackForAppAction(
                action = AppSystemBackAction.RETURN_TO_HOME_TAB
            )
        )
        assertFalse(
            shouldInterceptSystemBackForAppAction(
                action = AppSystemBackAction.NAVIGATE_UP
            )
        )
        assertFalse(
            shouldInterceptSystemBackForAppAction(
                action = AppSystemBackAction.FINISH_ACTIVITY
            )
        )
    }

    @Test
    fun mainHostTabBackHandler_isComposedAfterNavDisplaySoItCanOwnTabBackAction() {
        val sourceFile = listOf(
            File("app/src/main/java/com/android/purebilibili/navigation/AppNavigation.kt"),
            File("src/main/java/com/android/purebilibili/navigation/AppNavigation.kt")
        ).first { it.exists() }
        val source = sourceFile.readText()
        val navDisplayIndex = source.indexOf("BiliPaiNavDisplayHost(")
        val tabBackHandlerIndex = source.indexOf("MainHostTabBackHandler(")

        assertTrue(navDisplayIndex >= 0)
        assertTrue(tabBackHandlerIndex >= 0)
        assertTrue(
            tabBackHandlerIndex > navDisplayIndex,
            "MainHostTabBackHandler 必须在 NavDisplay 之后组合，才能接管 Tab 二级返回。"
        )
    }

    @Test
    fun systemBackOnHomeTab_usesBackStackOrFinishesActivity() {
        assertEquals(
            AppSystemBackAction.NAVIGATE_UP,
            resolveAppSystemBackAction(
                isAtMainHostRoot = false,
                currentBottomItem = BottomNavItem.HOME,
                homeItem = BottomNavItem.HOME
            )
        )
        assertEquals(
            AppSystemBackAction.FINISH_ACTIVITY,
            resolveAppSystemBackAction(
                isAtMainHostRoot = true,
                currentBottomItem = BottomNavItem.HOME,
                homeItem = BottomNavItem.HOME
            )
        )
    }

    @Test
    fun visibleBottomTabRoute_mapsToPagerPage() {
        val visibleItems = listOf(
            BottomNavItem.HOME,
            BottomNavItem.DYNAMIC,
            BottomNavItem.HISTORY,
            BottomNavItem.PROFILE
        )

        assertEquals(
            1,
            resolveBottomPagerPageForRoute(
                route = ScreenRoutes.Dynamic.route,
                visibleItems = visibleItems
            )
        )
        assertEquals(
            2,
            resolveBottomPagerPageForRoute(
                route = ScreenRoutes.History.route,
                visibleItems = visibleItems
            )
        )
    }

    @Test
    fun bottomPagerSwitch_doesNotRebuildNavigation3MainHost() {
        assertFalse(
            shouldResetNavigation3BackStackForBottomPager(
                currentStack = listOf(BiliPaiNavKey.MainHost)
            )
        )
    }

    @Test
    fun bottomPagerSwitch_restoresNavigation3MainHostFromSecondaryDestination() {
        assertTrue(
            shouldResetNavigation3BackStackForBottomPager(
                currentStack = listOf(
                    BiliPaiNavKey.MainHost,
                    BiliPaiNavKey.Settings
                )
            )
        )
    }

    @Test
    fun bottomPagerSaveableStateKey_followsTabIdentityInsteadOfPageIndex() {
        assertEquals(
            "bottom:${ScreenRoutes.Home.route}",
            resolveBottomPagerSaveableStateKey(BottomNavItem.HOME)
        )
        assertEquals(
            "bottom:${ScreenRoutes.Profile.route}",
            resolveBottomPagerSaveableStateKey(BottomNavItem.PROFILE)
        )
    }

    @Test
    fun secondaryRoute_doesNotMapToBottomPagerPage() {
        val visibleItems = listOf(
            BottomNavItem.HOME,
            BottomNavItem.DYNAMIC,
            BottomNavItem.HISTORY,
            BottomNavItem.PROFILE
        )

        assertNull(
            resolveBottomPagerPageForRoute(
                route = ScreenRoutes.Search.route,
                visibleItems = visibleItems
            )
        )
        assertNull(
            resolveBottomPagerPageForRoute(
                route = VideoRoute.route,
                visibleItems = visibleItems
            )
        )
    }

    @Test
    fun bottomPagerSelection_clampsInvalidPageToHome() {
        val visibleItems = listOf(
            BottomNavItem.HOME,
            BottomNavItem.DYNAMIC,
            BottomNavItem.HISTORY
        )

        assertEquals(
            BottomNavItem.HOME,
            resolveBottomPagerItemForPage(
                page = -1,
                visibleItems = visibleItems
            )
        )
        assertEquals(
            BottomNavItem.HOME,
            resolveBottomPagerItemForPage(
                page = 99,
                visibleItems = visibleItems
            )
        )
    }

    @Test
    fun videoSourceRoute_matchesCurrentRouteWhenNavigationTopIsSourcePage() {
        assertEquals(
            ScreenRoutes.Dynamic.route,
            resolveVideoCardSourceRouteForNavigation(
                currentRoute = ScreenRoutes.Dynamic.route,
                videoBvid = "BV1xx411c7mD",
                lastClickedVideoSourceKey = "${ScreenRoutes.Dynamic.route}:BV1xx411c7mD",
                visibleBottomBarRoutes = setOf(
                    ScreenRoutes.Home.route,
                    ScreenRoutes.Dynamic.route,
                    ScreenRoutes.History.route,
                    ScreenRoutes.Profile.route
                )
            )
        )
    }

    @Test
    fun videoSourceRoute_matchesVisibleBottomPagerRouteWhenNavigationTopIsMainHost() {
        assertEquals(
            ScreenRoutes.Dynamic.route,
            resolveVideoCardSourceRouteForNavigation(
                currentRoute = "main_host",
                videoBvid = "BV1xx411c7mD",
                lastClickedVideoSourceKey = "${ScreenRoutes.Dynamic.route}:BV1xx411c7mD",
                visibleBottomBarRoutes = setOf(
                    ScreenRoutes.Home.route,
                    ScreenRoutes.Dynamic.route,
                    ScreenRoutes.History.route,
                    ScreenRoutes.Profile.route
                )
            )
        )
    }

    @Test
    fun videoSourceRoute_preservesHomeCategorySourceWhenNavigationTopIsMainHost() {
        val sourceRoute = "home?category=FOLLOW"

        assertEquals(
            sourceRoute,
            resolveVideoCardSourceRouteForNavigation(
                currentRoute = "main_host",
                videoBvid = "BV1xx411c7mD",
                lastClickedVideoSourceKey = "$sourceRoute:BV1xx411c7mD",
                visibleBottomBarRoutes = setOf(
                    ScreenRoutes.Home.route,
                    ScreenRoutes.Dynamic.route,
                    ScreenRoutes.History.route,
                    ScreenRoutes.Profile.route
                )
            )
        )
    }

    @Test
    fun videoSourceRoute_rejectsMismatchedClickedVideoKey() {
        assertNull(
            resolveVideoCardSourceRouteForNavigation(
                currentRoute = "main_host",
                videoBvid = "BV1xx411c7mD",
                lastClickedVideoSourceKey = "${ScreenRoutes.Dynamic.route}:BV9xx411c7mD",
                visibleBottomBarRoutes = setOf(
                    ScreenRoutes.Home.route,
                    ScreenRoutes.Dynamic.route,
                    ScreenRoutes.History.route,
                    ScreenRoutes.Profile.route
                )
            )
        )
    }

    @Test
    fun videoSourceRoute_acceptsNonBottomBarHostsLikeSearchAndSpace() {
        assertEquals(
            ScreenRoutes.Search.route,
            resolveVideoCardSourceRouteForNavigation(
                currentRoute = "main_host",
                videoBvid = "BV1search",
                lastClickedVideoSourceKey = "${ScreenRoutes.Search.route}:BV1search",
                visibleBottomBarRoutes = setOf(
                    ScreenRoutes.Home.route,
                    ScreenRoutes.Dynamic.route
                )
            )
        )
        assertEquals(
            "space/42",
            resolveVideoCardSourceRouteForNavigation(
                currentRoute = "main_host",
                videoBvid = "BV1space",
                lastClickedVideoSourceKey = "space/42:BV1space",
                visibleBottomBarRoutes = setOf(ScreenRoutes.Home.route)
            )
        )
        assertEquals(
            "video/BV_PARENT",
            resolveVideoCardSourceRouteForNavigation(
                currentRoute = "main_host",
                videoBvid = "BV_RELATED",
                lastClickedVideoSourceKey = "video/BV_PARENT:BV_RELATED",
                visibleBottomBarRoutes = setOf(ScreenRoutes.Home.route)
            )
        )
    }

    @Test
    fun mainHostUsesCurrentBottomPagerItemAsActiveBottomRoute() {
        assertEquals(
            ScreenRoutes.Home.route,
            resolveActiveBottomTabRoute(
                currentKey = BiliPaiNavKey.MainHost,
                currentBottomItem = BottomNavItem.HOME
            )
        )
        assertEquals(
            ScreenRoutes.Dynamic.route,
            resolveActiveBottomTabRoute(
                currentKey = BiliPaiNavKey.MainHost,
                currentBottomItem = BottomNavItem.DYNAMIC
            )
        )
    }

    @Test
    fun unresolvedMainHostUsesCurrentBottomPagerItemAsActiveBottomRoute() {
        assertEquals(
            ScreenRoutes.Home.route,
            resolveActiveBottomTabRoute(
                currentKey = null,
                currentBottomItem = BottomNavItem.HOME
            )
        )
        assertEquals(
            ScreenRoutes.Dynamic.route,
            resolveActiveBottomTabRoute(
                currentKey = BiliPaiNavKey.Unknown("main_host"),
                currentBottomItem = BottomNavItem.DYNAMIC
            )
        )
    }

    @Test
    fun directTopLevelKeyUsesItsOwnRouteForBottomBarDestination() {
        assertEquals(
            ScreenRoutes.Home.route,
            resolveActiveBottomTabRoute(
                currentKey = BiliPaiNavKey.Home,
                currentBottomItem = BottomNavItem.DYNAMIC
            )
        )
        assertEquals(
            ScreenRoutes.Dynamic.route,
            resolveActiveBottomTabRoute(
                currentKey = BiliPaiNavKey.Dynamic,
                currentBottomItem = BottomNavItem.HOME
            )
        )
    }

    @Test
    fun bottomBarShowsForConfiguredMainHostAndDirectTopLevelRoutes() {
        val defaultRoutes = setOf(
            ScreenRoutes.Home.route,
            ScreenRoutes.Dynamic.route,
            ScreenRoutes.History.route,
            ScreenRoutes.Profile.route
        )

        assertTrue(
            shouldShowBottomBarForNavigation(
                activeRoute = resolveActiveBottomTabRoute(BiliPaiNavKey.MainHost, BottomNavItem.HOME),
                visibleBottomBarRoutes = defaultRoutes,
                useSideNavigation = false,
                shouldHideBottomBarOnTablet = false,
                shouldDeferReveal = false
            )
        )
        assertTrue(
            shouldShowBottomBarForNavigation(
                activeRoute = resolveActiveBottomTabRoute(BiliPaiNavKey.Home, BottomNavItem.HOME),
                visibleBottomBarRoutes = defaultRoutes,
                useSideNavigation = false,
                shouldHideBottomBarOnTablet = false,
                shouldDeferReveal = false
            )
        )
        assertTrue(
            shouldShowBottomBarForNavigation(
                activeRoute = resolveActiveBottomTabRoute(BiliPaiNavKey.Dynamic, BottomNavItem.HOME),
                visibleBottomBarRoutes = defaultRoutes,
                useSideNavigation = false,
                shouldHideBottomBarOnTablet = false,
                shouldDeferReveal = false
            )
        )
    }

    @Test
    fun bottomBarHidesForUnconfiguredHomeAndExcludedNavigationStates() {
        val routesWithoutHome = setOf(
            ScreenRoutes.Dynamic.route,
            ScreenRoutes.History.route,
            ScreenRoutes.Profile.route
        )
        val defaultRoutes = routesWithoutHome + ScreenRoutes.Home.route

        assertFalse(
            shouldShowBottomBarForNavigation(
                activeRoute = resolveActiveBottomTabRoute(BiliPaiNavKey.Home, BottomNavItem.DYNAMIC),
                visibleBottomBarRoutes = routesWithoutHome,
                useSideNavigation = false,
                shouldHideBottomBarOnTablet = false,
                shouldDeferReveal = false
            )
        )
        assertFalse(
            shouldShowBottomBarForNavigation(
                activeRoute = ScreenRoutes.Story.createRoute(bvid = "BV1test", cid = 1L),
                visibleBottomBarRoutes = defaultRoutes + ScreenRoutes.Story.baseRoute,
                useSideNavigation = false,
                shouldHideBottomBarOnTablet = false,
                shouldDeferReveal = false
            )
        )
        assertFalse(
            shouldShowBottomBarForNavigation(
                activeRoute = ScreenRoutes.Settings.route,
                visibleBottomBarRoutes = defaultRoutes + ScreenRoutes.Settings.route,
                useSideNavigation = false,
                shouldHideBottomBarOnTablet = true,
                shouldDeferReveal = false
            )
        )
        assertFalse(
            shouldShowBottomBarForNavigation(
                activeRoute = ScreenRoutes.Home.route,
                visibleBottomBarRoutes = defaultRoutes,
                useSideNavigation = true,
                shouldHideBottomBarOnTablet = false,
                shouldDeferReveal = false
            )
        )
        assertFalse(
            shouldShowBottomBarForNavigation(
                activeRoute = ScreenRoutes.Home.route,
                visibleBottomBarRoutes = defaultRoutes,
                useSideNavigation = false,
                shouldHideBottomBarOnTablet = false,
                shouldDeferReveal = true
            )
        )
    }

    @Test
    fun bottomPagerNavigationDuration_scalesWithPageDistance() {
        assertEquals(300, resolveBottomPagerNavigationDurationMillis(pageDistance = 1))
        assertEquals(300, resolveBottomPagerNavigationDurationMillis(pageDistance = 2))
        assertEquals(500, resolveBottomPagerNavigationDurationMillis(pageDistance = 4))
    }

    @Test
    fun bottomPagerPreload_followsVisibleBottomBarCountAfterReady() {
        assertEquals(0, resolveBottomPagerBeyondViewportPageCount(pageCount = 4, contentReady = false))
        assertEquals(0, resolveBottomPagerBeyondViewportPageCount(pageCount = 1, contentReady = true))
        assertEquals(3, resolveBottomPagerBeyondViewportPageCount(pageCount = 4, contentReady = true))
        assertEquals(4, resolveBottomPagerBeyondViewportPageCount(pageCount = 5, contentReady = true))
        assertEquals(4, resolveBottomPagerBeyondViewportPageCount(pageCount = 9, contentReady = true))
    }

    @Test
    fun bottomPagerVisibleItems_dropInvalidIdsAndClampToFive() {
        assertEquals(
            listOf(
                BottomNavItem.HOME,
                BottomNavItem.DYNAMIC,
                BottomNavItem.HISTORY,
                BottomNavItem.PROFILE,
                BottomNavItem.FAVORITE
            ),
            resolveVisibleBottomBarItems(
                orderedVisibleTabIds = listOf(
                    "HOME",
                    "DYNAMIC",
                    "INVALID",
                    "HISTORY",
                    "PROFILE",
                    "FAVORITE",
                    "LIVE"
                )
            )
        )
    }

    @Test
    fun bottomPagerVisibleItems_deduplicateBeforeApplyingPageLimit() {
        val items = resolveVisibleBottomBarItems(
            listOf("PROFILE", "HOME", "PROFILE", "DYNAMIC", "SETTINGS", "HISTORY")
        )
        assertEquals(
            listOf(BottomNavItem.PROFILE, BottomNavItem.HOME, BottomNavItem.DYNAMIC,
                BottomNavItem.SETTINGS, BottomNavItem.HISTORY),
            items,
        )
        val keys = items.map(::resolveBottomPagerSaveableStateKey)
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun bottomPagerVisibleItems_emptyOrInvalidSettingsKeepOneHomePage() {
        assertEquals(listOf(BottomNavItem.HOME), resolveVisibleBottomBarItems(emptyList()))
        assertEquals(listOf(BottomNavItem.HOME), resolveVisibleBottomBarItems(listOf("INVALID")))
    }

    @Test
    fun bottomPagerNavigation_keepsSaveableKeysUniqueForFarJump() {
        val visibleItems = listOf(
            BottomNavItem.HOME,
            BottomNavItem.DYNAMIC,
            BottomNavItem.HISTORY,
            BottomNavItem.PROFILE,
            BottomNavItem.SETTINGS
        )

        val saveableKeys = visibleItems.indices
            .filter { page ->
                shouldComposeBottomPagerPage(
                    item = visibleItems[page],
                    page = page,
                    currentPage = 3,
                    selectedPage = 4,
                    isNavigating = true,
                    navigationStartPage = 0,
                    contentReady = true
                )
            }
            .map { page ->
                resolveBottomPagerSaveableStateKey(visibleItems[page])
            }

        assertEquals(saveableKeys.distinct(), saveableKeys)
    }

    @Test
    fun bottomPagerLayoutKeys_allowOldLayoutQueriesAfterSettingsRemoveTab() {
        val before = listOf(
            BottomNavItem.HOME, BottomNavItem.DYNAMIC, BottomNavItem.HISTORY,
            BottomNavItem.PROFILE, BottomNavItem.SETTINGS
        )
        val after = before.dropLast(1)
        // 旧布局仍查询五页，新配置已经只剩四页。
        val keys = before.indices.map { resolveBottomPagerLayoutKey(it, after) }
        assertEquals(keys.size, keys.toSet().size)
        after.forEachIndexed { index, item ->
            assertEquals(resolveBottomPagerSaveableStateKey(item), keys[index])
        }
        assertFalse(keys.last() in before.map(::resolveBottomPagerSaveableStateKey))
        // 重新启用时恢复标签自身的键，保留原页面状态。
        assertEquals(
            resolveBottomPagerSaveableStateKey(BottomNavItem.SETTINGS),
            resolveBottomPagerLayoutKey(4, before)
        )
    }

    @Test
    fun bottomPagerUserScroll_isDisabledToAvoidAccidentalTabSwitch() {
        assertFalse(shouldEnableBottomPagerUserScroll())
    }

    @Test
    fun bottomPagerDuringNavigation_composesAllPagesWhenContentReady() {
        // BiliPai: contentReady → every tab stays mounted through animateScrollBy.
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.HOME,
                page = 0,
                currentPage = 1,
                selectedPage = 0,
                isNavigating = true,
                navigationStartPage = 4,
                contentReady = true
            )
        )
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.LIVE,
                page = 4,
                currentPage = 3,
                selectedPage = 0,
                isNavigating = true,
                navigationStartPage = 4,
                contentReady = true
            )
        )
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.DYNAMIC,
                page = 1,
                currentPage = 1,
                selectedPage = 0,
                isNavigating = true,
                navigationStartPage = 4,
                contentReady = true
            )
        )
        // Intermediate pages stay real (not empty Box) so far tab → home scrolls solidly.
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.HISTORY,
                page = 2,
                currentPage = 3,
                selectedPage = 0,
                isNavigating = true,
                navigationStartPage = 4,
                contentReady = true
            )
        )
    }

    @Test
    fun bottomPagerAfterReady_keepsSettledPagesComposedWhenNotNavigating() {
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.DYNAMIC,
                page = 1,
                currentPage = 1,
                selectedPage = 3,
                isNavigating = false,
                navigationStartPage = 1,
                contentReady = true
            )
        )
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.HISTORY,
                page = 2,
                currentPage = 1,
                selectedPage = 3,
                isNavigating = false,
                navigationStartPage = 1,
                contentReady = true
            )
        )
    }

    @Test
    fun bottomPagerAfterReady_keepsNonStoryPagesComposedWhenSettled() {
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.PROFILE,
                page = 3,
                currentPage = 3,
                selectedPage = 3,
                isNavigating = false,
                navigationStartPage = 3,
                contentReady = true
            )
        )
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.HOME,
                page = 0,
                currentPage = 3,
                selectedPage = 3,
                isNavigating = false,
                navigationStartPage = 3,
                contentReady = true
            )
        )
    }

    @Test
    fun bottomPagerAfterReady_doesNotPrecomposeInactiveStoryPlayer() {
        assertFalse(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.STORY,
                page = 2,
                currentPage = 0,
                selectedPage = 0,
                isNavigating = false,
                navigationStartPage = 0,
                contentReady = true
            )
        )
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.STORY,
                page = 2,
                currentPage = 0,
                selectedPage = 2,
                isNavigating = true,
                navigationStartPage = 0,
                contentReady = true
            )
        )
    }

    @Test
    fun bottomPagerRenderBudget_downgradesOnlyWhileNavigating() {
        val navigating = resolveBottomPagerRenderBudget(isNavigating = true)
        val settled = resolveBottomPagerRenderBudget(isNavigating = false)

        assertTrue(navigating.isTransitionRunning)
        assertTrue(navigating.forceLowBlurBudget)
        assertTrue(navigating.deferProfileImmersiveBackground)
        assertFalse(settled.isTransitionRunning)
        assertFalse(settled.forceLowBlurBudget)
        assertFalse(settled.deferProfileImmersiveBackground)
    }

    @Test
    fun storyBottomPagerPage_staysComposedAfterContentReadyLikeBiliPai() {
        // BiliPai mounts every page after contentReady; active work still uses settledPage.
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.STORY,
                page = 3,
                currentPage = 0,
                selectedPage = 1,
                isNavigating = false,
                navigationStartPage = 0,
                contentReady = true
            )
        )
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.STORY,
                page = 3,
                currentPage = 3,
                selectedPage = 1,
                isNavigating = false,
                navigationStartPage = 3,
                contentReady = false
            )
        )
        assertFalse(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.STORY,
                page = 3,
                currentPage = 0,
                selectedPage = 1,
                isNavigating = false,
                navigationStartPage = 0,
                contentReady = false
            )
        )
    }

    @Test
    fun settingsBottomPagerPage_staysComposedAfterContentReadyLikeBiliPai() {
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.SETTINGS,
                page = 4,
                currentPage = 0,
                selectedPage = 0,
                isNavigating = false,
                navigationStartPage = 0,
                contentReady = true
            )
        )
        assertTrue(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.SETTINGS,
                page = 4,
                currentPage = 4,
                selectedPage = 0,
                isNavigating = false,
                navigationStartPage = 4,
                contentReady = false
            )
        )
        assertFalse(
            shouldComposeBottomPagerPage(
                item = BottomNavItem.SETTINGS,
                page = 4,
                currentPage = 0,
                selectedPage = 0,
                isNavigating = false,
                navigationStartPage = 0,
                contentReady = false
            )
        )
    }

    @Test
    fun appNavigationUsesMainBottomPagerStateForRenderBudget() {
        val sourceFile = listOf(
            File("app/src/main/java/com/android/purebilibili/navigation/AppNavigation.kt"),
            File("src/main/java/com/android/purebilibili/navigation/AppNavigation.kt")
        ).first { it.exists() }
        val source = sourceFile.readText()

        assertTrue(source.contains("rememberMainBottomPagerState("))
        assertTrue(source.contains("resolveBottomPagerRenderBudget(isNavigating = mainBottomPagerState.isNavigating)"))
        assertFalse(source.contains("pendingBottomTabTransitionRoute"))
        assertFalse(source.contains("resolveBottomTabTransitionTargetRoute"))
        assertFalse(source.contains("shouldUseInstantBottomTabTransition"))
    }

    @Test
    fun homeRoute_bypassesGlobalNavigationDebounce() {
        assertTrue(
            canProceedWithNavigation(
                currentTimeMillis = 1_000L,
                lastNavigationTimeMillis = 950L,
                debounceWindowMillis = 300L,
                bypassDebounce = shouldBypassNavigationDebounceForRoute(ScreenRoutes.Home.route)
            )
        )
    }

    @Test
    fun dynamicRoute_bypassesGlobalNavigationDebounce() {
        assertTrue(
            canProceedWithNavigation(
                currentTimeMillis = 1_000L,
                lastNavigationTimeMillis = 950L,
                debounceWindowMillis = 300L,
                bypassDebounce = shouldBypassNavigationDebounceForRoute(ScreenRoutes.Dynamic.route)
            )
        )
    }

    @Test
    fun profileRoute_bypassesGlobalNavigationDebounce() {
        assertTrue(
            canProceedWithNavigation(
                currentTimeMillis = 1_000L,
                lastNavigationTimeMillis = 950L,
                debounceWindowMillis = 300L,
                bypassDebounce = shouldBypassNavigationDebounceForRoute(ScreenRoutes.Profile.route)
            )
        )
    }

    @Test
    fun nonHomeRoute_stillRespectsGlobalNavigationDebounce() {
        assertFalse(
            canProceedWithNavigation(
                currentTimeMillis = 1_000L,
                lastNavigationTimeMillis = 950L,
                debounceWindowMillis = 300L,
                bypassDebounce = shouldBypassNavigationDebounceForRoute(ScreenRoutes.Search.route)
            )
        )
    }

    @Test
    fun profileShortcuts_preserveProfileStackSoBackReturnsToProfile() {
        assertTrue(shouldPreserveProfileStackForShortcut(ScreenRoutes.Settings.route))
        assertTrue(shouldPreserveProfileStackForShortcut(ScreenRoutes.History.route))
        assertTrue(shouldPreserveProfileStackForShortcut(ScreenRoutes.Favorite.route))
        assertTrue(shouldPreserveProfileStackForShortcut(ScreenRoutes.WatchLater.route))
        assertTrue(shouldPreserveProfileStackForShortcut(ScreenRoutes.DownloadList.route))
        assertTrue(shouldPreserveProfileStackForShortcut(ScreenRoutes.Inbox.route))
        assertTrue(shouldPreserveProfileStackForShortcut(ScreenRoutes.Following.route))
        assertTrue(shouldPreserveProfileStackForShortcut(ScreenRoutes.Following.createRoute(123L)))
    }
}
