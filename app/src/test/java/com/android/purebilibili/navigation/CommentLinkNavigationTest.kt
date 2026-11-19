package com.android.purebilibili.navigation

import com.android.purebilibili.core.util.BilibiliNavigationTargetParser
import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.navigation3.BiliPaiNavKey
import com.android.purebilibili.navigation3.legacyRouteToBiliPaiNavKey
import com.android.purebilibili.navigation3.toLegacyRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CommentLinkNavigationTest {
    @Test
    fun topicSearchAndSharedDestinationsOpenThroughBothCommentEntrypoints() {
        listOf(
            "bilibili://search?keyword=%E5%8E%9F%E7%A5%9E" to BiliPaiNavKey.Search("原神"),
            "bilibili://m.bilibili.com/topic-detail?topic_id=1028161" to BiliPaiNavKey.TopicDetail(1028161),
            "https://www.bilibili.com/h5/topic-detail?topic_id=1028161" to BiliPaiNavKey.TopicDetail(1028161),
            "bilibili://following/detail/cv123" to BiliPaiNavKey.ArticleDetail(123),
            "bilibili://medialist/detail/456" to BiliPaiNavKey.SeasonSeriesDetail("favorite", 456, 0),
            "https://www.bilibili.com/medialist/detail/ml456?mid=78" to
                BiliPaiNavKey.SeasonSeriesDetail("favorite", 456, 78),
        ).forEach { (link, expected) ->
            val nativeKeys = mutableListOf<BiliPaiNavKey>()
            assertTrue(dispatchNativeLinkNavigation(
                target = BilibiliNavigationTargetParser.parse(link)!!,
                onVideo = { error("Unexpected video") },
                onSearch = { nativeKeys += BiliPaiNavKey.Search(it) },
                onKey = { nativeKeys += it },
                onRoute = { error("Unexpected legacy route") },
                onPopularFeed = { error("Unexpected feed") },
            ))
            assertEquals(expected, nativeKeys.single(), link)
            val messageKeys = mutableListOf<BiliPaiNavKey>()
            dispatchMessageLinkNavigation(
                rawLink = link,
                currentRoute = "comment_detail",
                onVideo = { error("Unexpected video") },
                onVideoRoute = { _, _ -> error("Unexpected video") },
                onKey = { messageKeys += it },
                onRoute = { error("Unexpected legacy route") },
            )
            assertEquals(expected, messageKeys.single(), link)
        }
    }

    @Test
    fun videoPartProgressAndReplyLocationSurviveCommentDispatch() {
        listOf(
            "cid=321&t=12.5" to 12_500L,
            "cid=321&dm_progress=4500" to 1_500L,
            "cid=321&dm_progress=1800" to 0L,
            "cid=321&start_progress=0&dm_progress=9000&t=15" to 0L,
        ).forEach { (query, expectedStart) ->
            val link = "https://www.bilibili.com/video/BV1xx411c7mD?$query&root=456#reply789"
            val keys = mutableListOf<BiliPaiNavKey>()
            val target = BilibiliNavigationTargetParser.parse(link)!!
            fun verify(key: BiliPaiNavKey) {
                val video = assertIs<BiliPaiNavKey.VideoDetail>(key)
                assertEquals(321L, video.cid)
                assertEquals(expectedStart, video.linkStartPositionMs)
                assertEquals(456L, video.commentRootRpid)
                assertEquals(789L, video.commentTargetRpid)
            }
            assertTrue(dispatchNativeLinkNavigation(
                target = target,
                onVideo = { error("Playback location was dropped") },
                onSearch = { error("Unexpected search") },
                onKey = { keys += it },
                onRoute = { error("Unexpected legacy route") },
                onPopularFeed = { error("Unexpected feed") },
            ))
            verify(keys.single())
            dispatchMessageLinkNavigation(
                rawLink = link,
                currentRoute = "comment_detail",
                onVideo = { error("Playback location was dropped") },
                onVideoRoute = { route, _ -> verify(legacyRouteToBiliPaiNavKey(route)) },
                onKey = { error("Expected a video route") },
                onRoute = { error("Unexpected legacy route") },
            )
        }
    }

    @Test
    fun videoPageNumberRequiresLookupOnlyWhenCidIsAbsent() {
        val link = "https://www.bilibili.com/video/BV1xx411c7mD?p=2&t=8"
        val byPage = assertIs<BilibiliNavigationTarget.Video>(BilibiliNavigationTargetParser.parse(link))
        assertEquals(2, byPage.page)
        assertEquals(8000L, byPage.startPositionMs)
        assertTrue(byPage.needsPageCid)
        val byCid = assertIs<BilibiliNavigationTarget.Video>(BilibiliNavigationTargetParser.parse("$link&cid=321"))
        assertEquals(321L, byCid.cid)
        kotlin.test.assertFalse(byCid.needsPageCid)
    }

    @Test
    fun browserWrappedWebpageOpensTheInnerAddress() {
        val wrapper = "bilibili://browser/?url=https%3A%2F%2Fexample.com%2Farticle%3Fid%3D5"
        assertEquals(
            BilibiliLinkNavigationAction.InAppWeb("https://example.com/article?id=5"),
            resolveBilibiliLinkNavigationAction(wrapper)
        )
    }

    @Test
    fun dynamicPreviewLinksReachCommentAwareKeysThroughNativeAndMessageDispatch() {
        listOf(
            "bilibili://following/detail/123?tab=comment" to
                BiliPaiNavKey.DynamicDetail("123", openComments = true),
            "https://t.bilibili.com/123?tab=comment" to
                BiliPaiNavKey.DynamicDetail("123", openComments = true),
            "bilibili://following/detail/123?tab=comment&root=456&comment_id=789" to
                BiliPaiNavKey.DynamicDetail("123", 456L, 789L, openComments = true),
            "bilibili://following/detail/123" to BiliPaiNavKey.DynamicDetail("123"),
        ).forEach { (link, expected) ->
            val nativeKeys = mutableListOf<BiliPaiNavKey>()
            assertTrue(dispatchNativeLinkNavigation(
                target = BilibiliNavigationTargetParser.parse(link)!!,
                onVideo = { error("Dynamic preview is not a video") },
                onSearch = { error("Unexpected search") },
                onKey = { nativeKeys += it },
                onRoute = { error("Unexpected legacy route") },
                onPopularFeed = { error("Unexpected feed") },
            ))
            assertEquals(expected, nativeKeys.single(), link)

            val messageKeys = mutableListOf<BiliPaiNavKey>()
            dispatchMessageLinkNavigation(
                rawLink = link,
                currentRoute = "message",
                onVideo = { error("Dynamic preview is not a video") },
                onVideoRoute = { _, _ -> error("Dynamic preview is not a video") },
                onKey = { messageKeys += it },
                onRoute = { error("Unexpected legacy route") },
            )
            assertEquals(expected, messageKeys.single(), link)
            assertEquals(expected, legacyRouteToBiliPaiNavKey(nativeKeys.single().toLegacyRoute()), link)
            assertEquals(expected, legacyRouteToBiliPaiNavKey(messageKeys.single().toLegacyRoute()), link)
        }
    }

    @Test
    fun nativeVideoCommentLinksUseLocationAwareDestinationInsteadOfIdOnlyCallback() {
        val links = listOf(
            "https://www.bilibili.com/video/BV1xx411c7mD?root=456#reply789",
            "bilibili://browser/?url=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2FBV1xx411c7mD%3Froot%3D456%23reply789",
        )
        links.forEach { link ->
            val keys = mutableListOf<BiliPaiNavKey>()
            assertTrue(dispatchNativeLinkNavigation(
                target = BilibiliNavigationTargetParser.parse(link)!!,
                onVideo = { error("ID-only playback drops the comment location") },
                onSearch = { error("Unexpected search") },
                onKey = { keys += it },
                onRoute = { error("Unexpected legacy route") },
                onPopularFeed = { error("Unexpected feed") },
            ))
            val key = assertIs<BiliPaiNavKey.VideoDetail>(keys.single())
            assertEquals("BV1xx411c7mD", key.bvid)
            assertEquals(456L, key.commentRootRpid)
            assertEquals(789L, key.commentTargetRpid)
        }
    }

    @Test
    fun notificationArticleCommentKeepsArticleIdentityWhenEntryUriPointsToOpus() {
        val keys = mutableListOf<BiliPaiNavKey>()
        dispatchMessageLinkNavigation(
            rawLink = "bilibili://comment/msg_fold/12/123/456/789?enterUri=https%3A%2F%2Fwww.bilibili.com%2Fopus%2F999",
            currentRoute = "message",
            onVideo = { error("Article is not a video") },
            onVideoRoute = { _, _ -> error("Article is not a video") },
            onKey = { keys += it },
            onRoute = { error("Unexpected legacy route") },
        )
        assertEquals(BiliPaiNavKey.ArticleDetail(123L, commentRootRpid = 456L, commentTargetRpid = 789L), keys.single())
    }

    @Test
    fun musicCommentRoutePreservesNonVideoSubjectIdentity() {
        val keys = mutableListOf<BiliPaiNavKey>()
        dispatchMessageLinkNavigation(
            rawLink = "https://www.bilibili.com/h5/comment/sub?oid=999&pageType=47&root_id=456&comment_id=789",
            currentRoute = "bgm_detail",
            onVideo = { error("Music comment OID is not an aid") },
            onVideoRoute = { _, _ -> error("Music comment OID is not an aid") },
            onKey = { keys += it },
            onRoute = { error("Unexpected legacy route") },
        )
        assertEquals(BiliPaiNavKey.CommentDetail(oid = 999L, rootId = 456L, targetId = 789L, type = 47, enterUri = ""), keys.single())
    }

    @Test
    fun targetOnlyDynamicLinkPreservesTargetForThreadResolution() {
        val keys = mutableListOf<BiliPaiNavKey>()
        dispatchMessageLinkNavigation(
            rawLink = "bilibili://comment/detail/17/123/0?comment_id=789",
            currentRoute = "message",
            onVideo = { error("Unexpected video") },
            onVideoRoute = { _, _ -> error("Unexpected video") },
            onKey = { keys += it },
            onRoute = { error("Unexpected legacy route") },
        )
        assertEquals(BiliPaiNavKey.CommentDetail(123L, 0L, 789L, 17, "bilibili://following/detail/123"), keys.single())
    }
}
