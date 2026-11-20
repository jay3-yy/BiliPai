package com.android.purebilibili.navigation3

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BiliPaiVideoSourcePolicyTest {

    @Test
    fun listAndRelatedDetailUseTheSameWholeCardMorphMode() {
        assertTrue(isRelatedVideoCardMorphSourceRoute("video/BV_PARENT"))
        assertFalse(isRelatedVideoCardMorphSourceRoute("partition"))
        assertFalse(isRelatedVideoCardMorphSourceRoute("home?category=1"))
        listOf(
            "partition",
            "home",
            "home?category=1",
            "search",
            "dynamic/123",
            "space/456",
            "watchlater",
            "favorite",
            "history",
        ).forEach { sourceRoute ->
            assertEquals(
                BiliPaiVideoCardMorphMode.PRIMARY_WHOLE_CARD,
                resolveBiliPaiVideoCardMorphMode(
                    cardTransitionEnabled = true,
                    reduceMotion = false,
                    sourceRoute = sourceRoute,
                    hasUsableSourceBounds = true,
                ),
            )
            assertTrue(
                shouldUseMiuixVideoCardMorph(
                    cardTransitionEnabled = true,
                    reduceMotion = false,
                    sourceRoute = sourceRoute,
                    hasUsableSourceBounds = true,
                ),
                "Expected card morph for source=$sourceRoute",
            )
        }
        assertEquals(
            BiliPaiVideoCardMorphMode.PRIMARY_WHOLE_CARD,
            resolveBiliPaiVideoCardMorphMode(
                cardTransitionEnabled = true,
                reduceMotion = false,
                sourceRoute = "video/BV_PARENT",
                hasUsableSourceBounds = true,
            ),
        )
        assertTrue(
            shouldUseMiuixVideoCardMorph(
                cardTransitionEnabled = true,
                reduceMotion = false,
                sourceRoute = "video/BV_PARENT",
                hasUsableSourceBounds = true,
            ),
            "相关推荐应与首页一样使用整卡 Morph",
        )
        assertFalse(
            shouldUseMiuixVideoCardMorph(
                cardTransitionEnabled = true,
                reduceMotion = false,
                sourceRoute = "partition",
                hasUsableSourceBounds = false,
            )
        )
        assertEquals(
            BiliPaiVideoCardMorphMode.NONE,
            resolveBiliPaiVideoCardMorphMode(
                cardTransitionEnabled = true,
                reduceMotion = true,
                sourceRoute = "video/BV_PARENT",
                hasUsableSourceBounds = true,
            ),
        )
    }

    @Test
    fun searchVideoUsesSearchSourceRouteAndIndependentSourceKey() {
        val source = resolveBiliPaiVideoSource(
            bvid = "BV1",
            explicitSourceRoute = null,
            currentKey = BiliPaiNavKey.Search(),
            previousSourceRoute = null
        )

        assertEquals("search", source.route)
        assertEquals("search:BV1", source.key)
    }

    @Test
    fun videoLinkNavigationUsesImmediateParentInsteadOfPreviousListSource() {
        val source = resolveBiliPaiVideoSource(
            bvid = "BV2",
            explicitSourceRoute = null,
            currentKey = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = "home"),
            previousSourceRoute = "home"
        )

        assertEquals("video/BV1", source.route)
        assertEquals("video/BV1:BV2", source.key)
    }

    @Test
    fun relatedVideoNavigationUsesExplicitParentDetailSourceRoute() {
        val source = resolveBiliPaiVideoSource(
            bvid = "BV2",
            explicitSourceRoute = "video/BV1",
            currentKey = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = "home"),
            previousSourceRoute = "home"
        )

        assertEquals("video/BV1", source.route)
        assertEquals("video/BV1:BV2", source.key)
    }

    @Test
    fun videoToVideoWithoutPreviousSourceFallsBackToParentVideoHost() {
        val source = resolveBiliPaiVideoSource(
            bvid = "BV2",
            explicitSourceRoute = null,
            currentKey = BiliPaiNavKey.VideoDetail(bvid = "BV1", sourceRoute = null),
            previousSourceRoute = null
        )

        assertEquals("video/BV1", source.route)
        assertEquals("video/BV1:BV2", source.key)
    }

    @Test
    fun explicitSourceRouteIsNormalizedBeforeKeyGeneration() {
        val source = resolveBiliPaiVideoSource(
            bvid = "BV1",
            explicitSourceRoute = "search?keyword=test",
            currentKey = BiliPaiNavKey.Home,
            previousSourceRoute = null
        )

        assertEquals("search", source.route)
        assertEquals("search:BV1", source.key)
    }

    @Test
    fun homeTopTabSourceRouteKeepsQueryForIndependentSourceKey() {
        val source = resolveBiliPaiVideoSource(
            bvid = "BV1",
            explicitSourceRoute = "home?category=POPULAR",
            currentKey = BiliPaiNavKey.Home,
            previousSourceRoute = null
        )

        assertEquals("home?category=POPULAR", source.route)
        assertEquals("home?category=POPULAR:BV1", source.key)
    }
}
