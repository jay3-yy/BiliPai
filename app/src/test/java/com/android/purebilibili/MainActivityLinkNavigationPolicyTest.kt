package com.android.purebilibili

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.legacyRouteToBiliPaiNavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MainActivityLinkNavigationPolicyTest {
    @Test
    fun dynamicPreviewIntentCarriesCommentEntryAndActualReplyIdsAcrossColdDispatch() {
        listOf(
            "bilibili://following/detail/123?tab=comment" to
                BiliPaiNavKey.DynamicDetail("123", openComments = true),
            "https://t.bilibili.com/123?tab=comment" to
                BiliPaiNavKey.DynamicDetail("123", openComments = true),
            "bilibili://following/detail/123?tab=comment&root=456&comment_id=789" to
                BiliPaiNavKey.DynamicDetail("123", 456L, 789L, openComments = true),
            "bilibili://following/detail/123" to BiliPaiNavKey.DynamicDetail("123"),
        ).forEach { (link, expected) ->
            val target = BilibiliNavigationTargetParser.parse(link)!!
            val navigation = resolveMainActivityLinkNavigation(target)!!
            assertNull(navigation.pendingVideoId, link)
            assertEquals(expected, legacyRouteToBiliPaiNavKey(navigation.pendingNavigationRoute), link)
        }
    }

    @Test
    fun videoCommentIntentKeepsBothReplyIdsInsteadOfTakingIdOnlyPlaybackPath() {
        val target = BilibiliNavigationTargetParser.parse(
            "https://www.bilibili.com/video/BV1xx411c7mD?root_id=456#reply789"
        )!!
        val navigation = resolveMainActivityLinkNavigation(target)!!
        assertNull(navigation.pendingVideoId)
        val key = assertIs<BiliPaiNavKey.VideoDetail>(
            legacyRouteToBiliPaiNavKey(navigation.pendingNavigationRoute)
        )
        assertEquals("BV1xx411c7mD", key.bvid)
        assertEquals(456L, key.commentRootRpid)
        assertEquals(789L, key.commentTargetRpid)
    }

    @Test
    fun targetOnlyDynamicIntentPreservesUnresolvedIdentityForDetailLookup() {
        val target = BilibiliNavigationTargetParser.parse(
            "bilibili://comment/detail/17/123/0?comment_id=789"
        )!!
        val navigation = resolveMainActivityLinkNavigation(target)!!
        val key = assertIs<BiliPaiNavKey.CommentDetail>(
            legacyRouteToBiliPaiNavKey(navigation.pendingNavigationRoute)
        )
        assertEquals(123L, key.oid)
        assertEquals(0L, key.rootId)
        assertEquals(789L, key.targetId)
    }

    @Test
    fun musicCommentIntentKeepsBusinessAndEntryUriAcrossLegacyRouteEncoding() {
        val target = BilibiliNavigationTargetParser.parse(
            "bilibili://comment/detail/47/999/456/789?enterUri=https%3A%2F%2Fmusic.bilibili.com%2Fh5%2Fmusic-detail%3Fmusic_id%3DMA123"
        )!!
        val navigation = resolveMainActivityLinkNavigation(target)!!
        assertEquals(
            BiliPaiNavKey.CommentDetail(
                oid = 999L,
                rootId = 456L,
                targetId = 789L,
                type = 47,
                enterUri = "https://music.bilibili.com/h5/music-detail?music_id=MA123",
            ),
            legacyRouteToBiliPaiNavKey(navigation.pendingNavigationRoute),
        )
    }

    @Test
    fun weeklyColdStartPreservesHistoricalPeriod() {
        val navigation = resolveMainActivityLinkNavigation(BilibiliNavigationTarget.PopularFeed("weekly", 133))
        assertEquals("weekly_series?number=133", navigation?.pendingNavigationRoute)
        assertNull(navigation?.pendingVideoId)
    }


    @Test
    fun spaceTarget_mapsToSpaceRoute() {
        val navigation = resolveMainActivityLinkNavigation(
            BilibiliNavigationTarget.Space(123456L)
        )

        assertEquals("space/123456", navigation?.pendingNavigationRoute)
        assertNull(navigation?.pendingVideoId)
    }

    @Test
    fun liveTarget_mapsToLiveRoute() {
        val navigation = resolveMainActivityLinkNavigation(
            BilibiliNavigationTarget.Live(456789L)
        )

        assertEquals("live/456789?title=&uname=&site=bilibili", navigation?.pendingNavigationRoute)
    }

    @Test
    fun searchTarget_mapsToSearchRoute_andCarriesKeyword() {
        val navigation = resolveMainActivityLinkNavigation(
            BilibiliNavigationTarget.Search("黑神话")
        )

        assertEquals("search", navigation?.pendingNavigationRoute)
        assertEquals("黑神话", navigation?.pendingSearchKeyword)
    }


    @Test
    fun articleTarget_mapsToArticleRoute() {
        val navigation = resolveMainActivityLinkNavigation(
            BilibiliNavigationTarget.Article(12345L)
        )

        assertEquals("article/12345?title=", navigation?.pendingNavigationRoute)
    }

    @Test
    fun musicTarget_routesCopyrightMusicToNativeDetail() {
        val navigation = resolveMainActivityLinkNavigation(
            BilibiliNavigationTarget.Music("ma123")
        )

        assertEquals("bgm_detail?musicId=ma123&aid=0&cid=0&showVideos=false", navigation?.pendingNavigationRoute)
    }

    @Test
    fun unresolvedShortLink_extractsWebFallbackUrl() {
        val route = resolveIntentLinkFallbackUrl("https://b23.tv/cm-yaoyue-0-3jgPM")

        assertEquals(
            "https://b23.tv/cm-yaoyue-0-3jgPM",
            route
        )
    }

    @Test
    fun nonBilibiliLink_hasNoWebFallbackRoute() {
        val route = resolveIntentLinkFallbackUrl("https://example.com/demo")

        assertNull(route)
    }
}
