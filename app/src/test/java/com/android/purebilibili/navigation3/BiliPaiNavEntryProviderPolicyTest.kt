package com.android.purebilibili.navigation3

import com.android.purebilibili.navigation.ScreenRoutes
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BiliPaiNavEntryProviderPolicyTest {

    @Test
    fun subscribedFavoriteCollectionUsesSharedElementRouteLayer() {
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.SeasonSeriesDetail(
                type = "favorite_season",
                id = 1324105L,
                mid = 39366561L,
                title = "一天体重测试系列",
                sharedElementTransition = true
            ),
            cardTransitionEnabled = true,
            sourceMetadata = BiliPaiNavSourceMetadata()
        )

        assertEquals(BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT, transitions.pop)
        assertEquals(BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT, transitions.predictivePop)
    }

    @Test
    fun collectionWithoutSharedElementSourceKeepsFallbackRouteLayer() {
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.SeasonSeriesDetail(
                type = "favorite_season",
                id = 1324105L,
                mid = 39366561L,
                title = "一天体重测试系列"
            ),
            cardTransitionEnabled = true,
            sourceMetadata = BiliPaiNavSourceMetadata()
        )

        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.pop)
    }

    @Test
    fun subscribedFavoriteCollectionPopKeepsSharedElementRouteLayer() {
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT,
            fromRoute = "season_series_detail",
            toRoute = "main_host",
            cardTransitionEnabled = true,
            sharedElementPopReady = true,
            sourceMetadata = BiliPaiNavSourceMetadata()
        )

        assertEquals(BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT, transition)
    }

    @Test
    fun sharedReadyMetadataAloneDoesNotDisableRouteLayerForReturnTarget() {
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.Home,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true
            )
        )

        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.pop)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.predictivePop)
    }

    @Test
    fun homeVideoPushUsesNoOpRouteLayerWithRecordedBounds() {
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = "home"),
            cardTransitionEnabled = true,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true
            )
        )

        assertEquals(BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.pop)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.predictivePop)
    }

    @Test
    fun seasonDetailVideoPushNearHeaderStillUsesSharedElementRouteLayer() {
        val sourceRoute = "season_series_detail/favorite_season/1324105"
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = sourceRoute),
            cardTransitionEnabled = true,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "$sourceRoute:BV1",
                sourceRoute = sourceRoute,
                clickedBoundsRecorded = true,
                cardFullyVisible = false
            )
        )

        assertEquals(BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.pop)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.predictivePop)
    }

    @Test
    fun homeVideoPushWithDisabledSharedTransitionUsesLeftSourceFallback() {
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = "home"),
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true,
                cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT
            )
        )

        assertEquals(BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_FORWARD_FROM_LEFT, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.pop)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.predictivePop)
    }

    @Test
    fun homeVideoPushWithDisabledSharedTransitionUsesRightSourceFallback() {
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = "home"),
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true,
                cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_RIGHT
            )
        )

        assertEquals(BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_FORWARD_FROM_RIGHT, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.pop)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.predictivePop)
    }

    @Test
    fun homeVideoPushWithInvisibleSourceKeepsFallbackRouteLayer() {
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = "home"),
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = false,
                cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT
            )
        )

        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.pop)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.predictivePop)
    }

    @Test
    fun bottomTabForwardNavigationKeepsFallbackBecausePagerOwnsTabMotion() {
        val visibleRoutes = setOf(
            ScreenRoutes.Home.route,
            ScreenRoutes.Dynamic.route,
            ScreenRoutes.History.route,
            ScreenRoutes.Profile.route
        )

        assertEquals(
            BiliPaiNavRouteTransition.FALLBACK,
            resolveBiliPaiNavEntryForwardRouteTransition(
                defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                fromRoute = ScreenRoutes.Home.route,
                toRoute = ScreenRoutes.Profile.route,
                visibleBottomBarRoutes = visibleRoutes
            )
        )
        assertEquals(
            BiliPaiNavRouteTransition.FALLBACK,
            resolveBiliPaiNavEntryForwardRouteTransition(
                defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                fromRoute = ScreenRoutes.Search.route,
                toRoute = ScreenRoutes.Profile.route,
                visibleBottomBarRoutes = visibleRoutes
            )
        )
    }

    @Test
    fun mainHostForwardToSpaceUsesSpaceForwardTransition() {
        val visibleRoutes = setOf(
            ScreenRoutes.Home.route,
            ScreenRoutes.Dynamic.route,
            ScreenRoutes.History.route,
            ScreenRoutes.Profile.route
        )

        assertEquals(
            BiliPaiNavRouteTransition.SPACE_FORWARD,
            resolveBiliPaiNavEntryForwardRouteTransition(
                defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                fromRoute = BiliPaiNavKey.MainHost.routeBase,
                toRoute = ScreenRoutes.Space.route,
                visibleBottomBarRoutes = visibleRoutes
            )
        )
    }

    @Test
    fun settingsHierarchyUsesIosPushForwardTransition() {
        assertEquals(
            BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_FORWARD,
            resolveBiliPaiNavEntryForwardRouteTransition(
                defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                fromRoute = ScreenRoutes.Settings.route,
                toRoute = "settings_category",
                visibleBottomBarRoutes = emptySet(),
            )
        )
        assertEquals(
            BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_FORWARD,
            resolveBiliPaiNavEntryForwardRouteTransition(
                defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                fromRoute = "settings_category",
                toRoute = "appearance_settings",
                visibleBottomBarRoutes = emptySet(),
            )
        )
    }

    @Test
    fun settingsDirectChildFromRootUsesFallbackUntilCategoryRoute() {
        assertEquals(
            BiliPaiNavRouteTransition.FALLBACK,
            resolveBiliPaiNavEntryForwardRouteTransition(
                defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                fromRoute = ScreenRoutes.Settings.route,
                toRoute = "appearance_settings",
                visibleBottomBarRoutes = emptySet(),
            )
        )
    }

    @Test
    fun settingsInnerPagesFromActiveMainHostUseFallbackUntilCategoryRoute() {
        assertEquals(
            BiliPaiNavRouteTransition.FALLBACK,
            resolveBiliPaiNavEntryForwardRouteTransition(
                defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                fromRoute = BiliPaiNavKey.MainHost.routeBase,
                toRoute = ScreenRoutes.AppearanceSettings.route,
                visibleBottomBarRoutes = setOf(ScreenRoutes.Settings.route),
                activeMainHostRoute = ScreenRoutes.Settings.route,
            )
        )
    }

    @Test
    fun settingsInnerPagesFromInactiveMainHostKeepFallbackTransition() {
        assertEquals(
            BiliPaiNavRouteTransition.FALLBACK,
            resolveBiliPaiNavEntryForwardRouteTransition(
                defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                fromRoute = BiliPaiNavKey.MainHost.routeBase,
                toRoute = ScreenRoutes.AppearanceSettings.route,
                visibleBottomBarRoutes = setOf(ScreenRoutes.Settings.route),
                activeMainHostRoute = ScreenRoutes.Home.route
            )
        )
    }

    @Test
    fun settingsHierarchyPopUsesIosPushTransition() {
        assertEquals(
            BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP,
            resolveBiliPaiNavEntryPopRouteTransition(
                defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                fromRoute = "appearance_settings",
                toRoute = "settings_category",
                sourceMetadata = BiliPaiNavSourceMetadata(),
            )
        )
    }

    @Test
    fun messageInnerPagesUseLightSiblingForwardTransition() {
        val messageChildren = listOf(
            "message/reply_me",
            "message/at_me",
            "message/like_me",
            "message/system_notice",
            "chat"
        )

        messageChildren.forEach { childRoute ->
            assertEquals(
                BiliPaiNavRouteTransition.LIGHT_SIBLING_FORWARD,
                resolveBiliPaiNavEntryForwardRouteTransition(
                    defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                    fromRoute = ScreenRoutes.Inbox.route,
                    toRoute = childRoute,
                    visibleBottomBarRoutes = emptySet()
                )
            )
        }
    }

    @Test
    fun liveInnerPagesUseLightSiblingForwardTransition() {
        val liveChildren = listOf(
            "live_area",
            "live_search",
            "live_following"
        )

        liveChildren.forEach { childRoute ->
            assertEquals(
                BiliPaiNavRouteTransition.LIGHT_SIBLING_FORWARD,
                resolveBiliPaiNavEntryForwardRouteTransition(
                    defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                    fromRoute = ScreenRoutes.LiveList.route,
                    toRoute = childRoute,
                    visibleBottomBarRoutes = emptySet()
                )
            )
        }
    }

    @Test
    fun searchInnerPagesUseLightSiblingForwardTransition() {
        val searchChildren = listOf(
            "search_trending",
            "topic"
        )

        searchChildren.forEach { childRoute ->
            assertEquals(
                BiliPaiNavRouteTransition.LIGHT_SIBLING_FORWARD,
                resolveBiliPaiNavEntryForwardRouteTransition(
                    defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                    fromRoute = ScreenRoutes.Search.route,
                    toRoute = childRoute,
                    visibleBottomBarRoutes = emptySet()
                )
            )
        }
    }

    @Test
    fun lightSiblingPopReturnsFromChildToDomainRoot() {
        val cases = listOf(
            Triple("appearance_settings", "settings_category", BiliPaiNavRouteTransition.SETTINGS_IOS_PUSH_POP),
            Triple("message/reply_me", ScreenRoutes.Inbox.route, BiliPaiNavRouteTransition.LIGHT_SIBLING_POP),
            Triple("live_area", ScreenRoutes.LiveList.route, BiliPaiNavRouteTransition.LIGHT_SIBLING_POP),
            Triple("topic", ScreenRoutes.Search.route, BiliPaiNavRouteTransition.LIGHT_SIBLING_POP)
        )

        cases.forEach { (fromRoute, toRoute, expectedTransition) ->
            assertEquals(
                expectedTransition,
                resolveBiliPaiNavEntryPopRouteTransition(
                    defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                    fromRoute = fromRoute,
                    toRoute = toRoute,
                    sourceMetadata = BiliPaiNavSourceMetadata()
                )
            )
        }
    }

    @Test
    fun homeVideoPushWithoutRecordedBoundsKeepsForwardFallback() {
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = "home"),
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = false,
                cardFullyVisible = true,
                cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT
            )
        )

        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.pop)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.predictivePop)
    }

    @Test
    fun videoPushWithStaleSharedSourceKeepsForwardFallback() {
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.VideoDetail(bvid = "BV2", sourceRoute = "home"),
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true,
                cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT
            )
        )

        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.pop)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.predictivePop)
    }

    @Test
    fun nonHomeVideoPushUsesNoOpRouteLayerWithMatchingVisibleSourceCard() {
        val transitions = resolveBiliPaiNavEntryRouteTransitions(
            key = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = "history"),
            cardTransitionEnabled = true,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "history:BV1",
                sourceRoute = "history",
                clickedBoundsRecorded = true,
                cardFullyVisible = true
            )
        )

        assertEquals(BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT, transitions.forward)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.pop)
        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transitions.predictivePop)
    }

    @Test
    fun entryPopWithParameterizedCardSourceUsesNoOpRouteLayer() {
        listOf(
            "category/1" to "category",
            "space/123" to "space",
            "dynamic_detail/456" to "dynamic_detail"
        ).forEach { (sourceRoute, targetRouteBase) ->
            val transition = resolveBiliPaiNavEntryPopRouteTransition(
                defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
                fromRoute = "video",
                toRoute = targetRouteBase,
                cardTransitionEnabled = true,
                sourceMetadata = BiliPaiNavSourceMetadata(
                    sourceKey = "$sourceRoute:BV1",
                    sourceRoute = sourceRoute,
                    clickedBoundsRecorded = true,
                    cardFullyVisible = true
                )
            )

            assertEquals(BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT, transition, sourceRoute)
        }
    }

    @Test
    fun entryPopWithMismatchedParameterizedCardSourceKeepsFallbackRouteLayer() {
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "space",
            cardTransitionEnabled = true,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "category/1:BV1",
                sourceRoute = "category/1",
                clickedBoundsRecorded = true,
                cardFullyVisible = true
            )
        )

        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transition)
    }

    @Test
    fun entryPopToMainHostUsesActiveBottomTabAsSharedElementSource() {
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "main_host",
            cardTransitionEnabled = true,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true
            ),
            activeMainHostRoute = "home"
        )

        assertEquals(BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT, transition)
    }

    @Test
    fun entryPopToMainHostWithRecordedSourceIgnoresStaleVisibilityForSharedElement() {
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "main_host",
            cardTransitionEnabled = true,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = false
            ),
            activeMainHostRoute = "home"
        )

        assertEquals(BiliPaiNavRouteTransition.NO_OP_SHARED_ELEMENT, transition)
    }

    @Test
    fun entryPopWithDisabledSharedTransition_popToMainHostStillSlidesHorizontally() {
        // 实际栈是 [MainHost, VideoDetail]，pop 时 toRoute = "main_host"，
        // 必须保证 main_host 也算 card-return-target，否则 entry-level 元数据上的
        // popTransitionSpec 会绕开方向化分支，退化成 fade。
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "main_host",
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true,
                cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT
            )
        )

        assertEquals(BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_RETURN_TO_LEFT, transition)
    }

    @Test
    fun entryPopWithDisabledSharedTransition_popToMainHostWithoutDirectionFallsBackToRight() {
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "main_host",
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = null,
                sourceRoute = null,
                clickedBoundsRecorded = false,
                cardFullyVisible = false,
                cardSourceDirection = BiliPaiNavCardSourceDirection.NONE
            )
        )

        assertEquals(BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_RETURN_TO_RIGHT, transition)
    }

    @Test
    fun entryPopWithDisabledSharedTransition_noDirectionFallsBackToRight() {
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "home",
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true,
                cardSourceDirection = BiliPaiNavCardSourceDirection.NONE
            )
        )

        assertEquals(BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_RETURN_TO_RIGHT, transition)
    }

    @Test
    fun entryPopWithDisabledSharedTransition_scrolledOutCardStillSlidesHorizontally() {
        // 详情中卡片已滚出视口 → cardFullyVisible=false。
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "home",
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = false,
                cardSourceDirection = BiliPaiNavCardSourceDirection.NONE
            )
        )

        assertEquals(BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_RETURN_TO_RIGHT, transition)
    }

    @Test
    fun entryPopWithDisabledSharedTransition_deepLinkEntryStillSlidesHorizontally() {
        // 深链进入详情 → 没有任何源信息，期望兜底向右滑出。
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "dynamic",
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = null,
                sourceRoute = null,
                clickedBoundsRecorded = false,
                cardFullyVisible = false,
                cardSourceDirection = BiliPaiNavCardSourceDirection.NONE
            )
        )

        assertEquals(BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_RETURN_TO_RIGHT, transition)
    }

    @Test
    fun entryPopWithDisabledSharedTransition_nonCardReturnTargetStaysFallback() {
        // VideoDetail → 非 card-return-target（例如 audio_mode、settings 等）保持 FALLBACK。
        val transition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "audio_mode",
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true,
                cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT
            )
        )

        assertEquals(BiliPaiNavRouteTransition.FALLBACK, transition)
    }

    @Test
    fun entryPopWithDisabledSharedTransitionUsesSourceDirectionFallback() {
        val leftTransition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "home",
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true,
                cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_LEFT
            )
        )
        val rightTransition = resolveBiliPaiNavEntryPopRouteTransition(
            defaultTransition = BiliPaiNavRouteTransition.FALLBACK,
            fromRoute = "video",
            toRoute = "home",
            cardTransitionEnabled = false,
            sourceMetadata = BiliPaiNavSourceMetadata(
                sourceKey = "home:BV1",
                sourceRoute = "home",
                clickedBoundsRecorded = true,
                cardFullyVisible = true,
                cardSourceDirection = BiliPaiNavCardSourceDirection.SOURCE_RIGHT
            )
        )

        assertEquals(BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_RETURN_TO_LEFT, leftTransition)
        assertEquals(BiliPaiNavRouteTransition.CARD_DISABLED_VIDEO_RETURN_TO_RIGHT, rightTransition)
    }

    @Test
    fun providerUsesTypedVideoEntryContentKey() {
        val provider = biliPaiNavEntryProvider(
            sourceMetadata = BiliPaiNavSourceMetadata(),
            content = {}
        )
        val key = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = "search")
        val entry = provider(key)

        assertEquals(key.toString(), entry.contentKey)
        assertTrue(entry.metadata.isNotEmpty())
    }

    @Test
    fun providerDoesNotOwnPredictivePopTransition() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/navigation3/BiliPaiNavEntryProvider.kt"),
            File("src/main/java/com/android/purebilibili/navigation3/BiliPaiNavEntryProvider.kt")
        ).first { it.exists() }.readText()

        assertFalse(source.contains("NavDisplay.predictivePopTransitionSpec"))
    }
}
