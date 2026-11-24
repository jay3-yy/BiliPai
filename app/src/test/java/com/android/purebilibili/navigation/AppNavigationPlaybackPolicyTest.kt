package com.android.purebilibili.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppNavigationPlaybackPolicyTest {

    @Test
    fun miniPlayerTransition_doesNotMarkPlaybackAsNavigationLeave() {
        assertFalse(shouldMarkNavigationLeaveBeforeVideoExit(isMiniMode = true))
        assertTrue(shouldMarkNavigationLeaveBeforeVideoExit(isMiniMode = false))
    }

    @Test
    fun relatedDetailEnablesSharedTransitionWhenSourceRoutePresent() {
        assertTrue(
            shouldEnableVideoDetailSharedTransition(
                cardTransitionEnabled = true,
                sourceRoute = "video/BV_PARENT",
            )
        )
        assertFalse(
            shouldEnableVideoDetailSharedTransition(
                cardTransitionEnabled = true,
                sourceRoute = null,
            )
        )
        assertTrue(
            shouldEnableVideoDetailSharedTransition(
                cardTransitionEnabled = true,
                sourceRoute = ScreenRoutes.Home.route,
            )
        )
        assertFalse(
            shouldEnableVideoDetailSharedTransition(
                cardTransitionEnabled = false,
                sourceRoute = ScreenRoutes.Home.route,
            )
        )
    }

    @Test
    fun leavingVideoToHome_shouldStopPlaybackEagerly() {
        assertTrue(
            shouldStopPlaybackEagerlyOnVideoRouteExit(
                fromRoute = VideoRoute.route,
                toRoute = ScreenRoutes.Home.route,
                isVideoEntryRetained = false,
            )
        )
    }

    @Test
    fun coveringRetainedVideo_doesNotDestroyItsPlaybackIntentBeforeSessionSuspension() {
        for (target in listOf("search?keyword=android", ScreenRoutes.Settings.route, "video/BVchild")) {
            assertFalse(
                shouldStopPlaybackEagerlyOnVideoRouteExit(
                    fromRoute = "video/BVparent",
                    toRoute = target,
                    isVideoEntryRetained = true,
                ),
                target,
            )
        }
        assertTrue(
            shouldStopPlaybackEagerlyOnVideoRouteExit(
                fromRoute = "video/BVparent",
                toRoute = "search?keyword=android",
                isVideoEntryRetained = false,
            )
        )
    }

    @Test
    fun leavingVideoToAudioMode_shouldNotStopPlaybackEagerly() {
        assertFalse(
            shouldStopPlaybackEagerlyOnVideoRouteExit(
                fromRoute = VideoRoute.route,
                toRoute = ScreenRoutes.AudioMode.route,
                isVideoEntryRetained = false,
            )
        )
    }

    @Test
    fun leavingVideoToUpSpace_shouldEnterMiniPlayerInsteadOfStoppingEagerly() {
        assertFalse(
            shouldStopPlaybackEagerlyOnVideoRouteExit(
                fromRoute = VideoRoute.route,
                toRoute = ScreenRoutes.Space.createRoute(123L),
                isVideoEntryRetained = false,
            )
        )
    }

    @Test
    fun switchingBetweenVideoRoutes_shouldNotStopPlaybackEagerly() {
        assertFalse(
            shouldStopPlaybackEagerlyOnVideoRouteExit(
                fromRoute = VideoRoute.route,
                toRoute = VideoRoute.route,
                isVideoEntryRetained = false,
            )
        )
    }

    @Test
    fun leavingVideoWithUnknownTargetRoute_shouldNotStopPlaybackEagerly() {
        assertFalse(
            shouldStopPlaybackEagerlyOnVideoRouteExit(
                fromRoute = VideoRoute.route,
                toRoute = null,
                isVideoEntryRetained = false,
            )
        )
    }

    @Test
    fun allVideoCardRoutes_supportSharedElementReturn() {
        listOf(
            "main_host",
            ScreenRoutes.Home.route,
            ScreenRoutes.ListenVideo.route,
            ScreenRoutes.History.route,
            ScreenRoutes.Favorite.route,
            ScreenRoutes.WatchLater.route,
            ScreenRoutes.Search.route,
            ScreenRoutes.Dynamic.route,
            "dynamic_detail/123",
            ScreenRoutes.Partition.route,
            "category/1",
            "season_series_detail/series/1/2/title/owner",
            "space/123",
            "chat/123/1?name=朋友",
        ).forEach { route ->
            assertTrue(isVideoCardReturnTargetRoute(route), "共享元素返回应支持来源路由：$route")
        }
        assertTrue(isVideoCardReturnTargetRoute("video/BV1?cid=11"))
        assertFalse(isVideoCardReturnTargetRoute(ScreenRoutes.Settings.route))
    }

    @Test
    fun returningToHomeWithCardTransition_shouldNotDeferBottomBarReveal() {
        assertFalse(
            shouldDeferBottomBarRevealOnVideoReturn(
                isReturningFromDetail = true,
                activeBottomTabRoute = ScreenRoutes.Home.route,
                cardTransitionEnabled = true
            )
        )
    }

    @Test
    fun returningToMainHostHomeTabWithCardTransition_shouldNotDeferBottomBarReveal() {
        assertFalse(
            shouldDeferBottomBarRevealOnVideoReturn(
                isReturningFromDetail = true,
                activeBottomTabRoute = ScreenRoutes.Home.route,
                cardTransitionEnabled = true
            )
        )
    }

    @Test
    fun returningToMainHostNonHomeTab_shouldNotDeferBottomBarReveal() {
        assertFalse(
            shouldDeferBottomBarRevealOnVideoReturn(
                isReturningFromDetail = true,
                activeBottomTabRoute = ScreenRoutes.Dynamic.route,
                cardTransitionEnabled = true
            )
        )
    }

    @Test
    fun returningToHomeWithCardTransitionDisabled_shouldNotDeferBottomBarReveal() {
        assertFalse(
            shouldDeferBottomBarRevealOnVideoReturn(
                isReturningFromDetail = true,
                activeBottomTabRoute = ScreenRoutes.Home.route,
                cardTransitionEnabled = false
            )
        )
    }

    @Test
    fun notReturningFromDetail_shouldNotDeferBottomBarReveal() {
        assertFalse(
            shouldDeferBottomBarRevealOnVideoReturn(
                isReturningFromDetail = false,
                activeBottomTabRoute = ScreenRoutes.Home.route,
                cardTransitionEnabled = true
            )
        )
    }

    @Test
    fun returningButStillOnNonHomeRoute_shouldNotDeferBottomBarReveal() {
        assertFalse(
            shouldDeferBottomBarRevealOnVideoReturn(
                isReturningFromDetail = true,
                activeBottomTabRoute = VideoRoute.route,
                cardTransitionEnabled = true
            )
        )
    }

    @Test
    fun videoReturnBottomBarRevealDelay_onlyAppliesToSharedTransitionReturnToBottomDestination() {
        assertTrue(
            shouldDelayBottomBarRevealAfterVideoReturn(
                isReturningFromDetail = true,
                isBottomBarDestination = true,
                cardTransitionEnabled = true
            )
        )
        assertFalse(
            shouldDelayBottomBarRevealAfterVideoReturn(
                isReturningFromDetail = false,
                isBottomBarDestination = true,
                cardTransitionEnabled = true
            )
        )
        assertFalse(
            shouldDelayBottomBarRevealAfterVideoReturn(
                isReturningFromDetail = true,
                isBottomBarDestination = false,
                cardTransitionEnabled = true
            )
        )
        assertFalse(
            shouldDelayBottomBarRevealAfterVideoReturn(
                isReturningFromDetail = true,
                isBottomBarDestination = true,
                cardTransitionEnabled = false
            )
        )
    }

    @Test
    fun videoReturnBottomBarRevealDelay_usesShortVisualSettleWindow() {
        assertEquals(
            0L,
            resolveVideoReturnBottomBarRevealDelayMs(
                cardTransitionEnabled = false,
                isQuickReturnFromDetail = false
            )
        )
        assertEquals(
            120L,
            resolveVideoReturnBottomBarRevealDelayMs(
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = true
            )
        )
        assertEquals(
            160L,
            resolveVideoReturnBottomBarRevealDelayMs(
                cardTransitionEnabled = true,
                isQuickReturnFromDetail = false
            )
        )
    }

}
