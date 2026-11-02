package com.android.purebilibili.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BilibiliNavigationTargetParserTest {
    @Test
    fun dynamicCommentTabRequestsCommentsWithoutInventingReplyIds() {
        listOf(
            "bilibili://following/detail/123?tab=comment",
            "bili://following/detail/123?tab=%63omment",
            "https://t.bilibili.com/123?tab=comment",
            "t.bilibili.com/123?tab=comment",
            "//t.bilibili.com/123?tab=comment",
            "https://www.bilibili.com/opus/123?tab=COMMENT",
            "bilibili://browser/?url=bilibili%3A%2F%2Ffollowing%2Fdetail%2F123%3Ftab%3Dcomment",
        ).forEach { link ->
            val target = assertIs<BilibiliNavigationTarget.Dynamic>(BilibiliNavigationTargetParser.parse(link), link)
            assertEquals(BilibiliNavigationTarget.Dynamic("123", openComments = true), target, link)
            assertTrue(target.hasCommentLocation, link)
        }
    }

    @Test
    fun dynamicCommentTabAndActualReplyLocationArePreservedTogether() {
        listOf(
            "bilibili://following/detail/123?tab=comment&root=456&comment_id=789",
            "https://t.bilibili.com/123?tab=comment&root=456#reply789",
            "bilibili://browser/?url=bilibili%3A%2F%2Ffollowing%2Fdetail%2F123%3Ftab%3Dcomment%26root%3D456%26comment_id%3D789",
            "bilibili://browser/?url=bilibili%3A%2F%2Ffollowing%2Fdetail%2F123%3Ftab%3Dcomment&root=456&comment_id=789",
        ).forEach { link ->
            assertEquals(
                BilibiliNavigationTarget.Dynamic("123", 456L, 789L, openComments = true),
                BilibiliNavigationTargetParser.parse(link),
                link,
            )
        }
    }

    @Test
    fun normalDynamicLinksAndOtherContentDoNotAcquireCommentTabIntent() {
        listOf(
            "bilibili://following/detail/123",
            "https://t.bilibili.com/123",
            "bilibili://following/detail/123?tab=all",
        ).forEach { link ->
            val target = assertIs<BilibiliNavigationTarget.Dynamic>(BilibiliNavigationTargetParser.parse(link), link)
            assertFalse(target.openComments, link)
            assertFalse(target.hasCommentLocation, link)
        }
        assertEquals(
            BilibiliNavigationTarget.Video("av123"),
            BilibiliNavigationTargetParser.parse("bilibili://video/123?tab=comment"),
        )
        assertEquals(
            BilibiliNavigationTarget.Article(123L),
            BilibiliNavigationTargetParser.parse("bilibili://article/123?tab=comment"),
        )
    }

    @Test
    fun contentLinksKeepCommentLocationAcrossVideoAndDynamicIdentities() {
        listOf(
            "https://www.bilibili.com/video/BV1xx411c7mD?comment_root_id=456#reply789" to
                BilibiliNavigationTarget.Video("BV1xx411c7mD", 456L, 789L),
            "bilibili://video/123?root=456&comment_id=789" to
                BilibiliNavigationTarget.Video("av123", 456L, 789L),
            "https://t.bilibili.com/123?root_id=456&reply_id=789" to
                BilibiliNavigationTarget.Dynamic("123", 456L, 789L),
            "bilibili://following/detail/123?root_reply_id=456&target_id=789" to
                BilibiliNavigationTarget.Dynamic("123", 456L, 789L),
            "bilibili://browser/?url=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2FBV1xx411c7mD%3Froot%3D456%23reply789" to
                BilibiliNavigationTarget.Video("BV1xx411c7mD", 456L, 789L),
        ).forEach { (link, expected) ->
            assertEquals(expected, BilibiliNavigationTargetParser.parse(link), link)
        }
    }

    @Test
    fun officialComment3RoutesKeepBusinessRootAndSecondaryReply() {
        listOf(
            "bilibili://comment3/main/123/1?rp_id=456&sub_rp_id=789" to
                BilibiliNavigationTarget.CommentDetail(123L, 456L, 789L, 1, "bilibili://video/123"),
            "bili://comment3/main/888/11?rp_id=456&sub_rp_id=789" to
                BilibiliNavigationTarget.CommentDetail(888L, 456L, 789L, 11),
            "bilibili://comment3/main/123/17?rp_id=456&sub_rp_id=789" to
                BilibiliNavigationTarget.CommentDetail(123L, 456L, 789L, 17, "bilibili://following/detail/123"),
        ).forEach { (link, expected) ->
            assertEquals(expected, BilibiliNavigationTargetParser.parse(link), link)
        }
    }

    @Test
    fun officialReplyAliasesAlsoWorkOnContentLinksWithoutInventingARoot() {
        assertEquals(
            BilibiliNavigationTarget.Dynamic("123", 456L, 789L),
            BilibiliNavigationTargetParser.parse("bilibili://following/detail/123?rp_id=456&sub_rp_id=789"),
        )
        assertEquals(
            BilibiliNavigationTarget.Video("av123", 0L, 789L),
            BilibiliNavigationTargetParser.parse("bilibili://comment3/main/123/1?rp_id=0&sub_rp_id=789"),
        )
        assertEquals(
            BilibiliNavigationTarget.CommentDetail(123L, 456L, 0L, 17, "bilibili://following/detail/123"),
            BilibiliNavigationTargetParser.parse("bilibili://comment3/main/123/17?rp_id=456&sub_rp_id=456"),
        )
    }

    @Test
    fun officialComment3RejectsInvalidSubjectsAndMissingRealLocation() {
        listOf(
            "bilibili://comment3/main/0/17?rp_id=456",
            "bilibili://comment3/main/123/0?rp_id=456",
            "bilibili://comment3/main/123/11?rp_id=-1&sub_rp_id=0",
            "bilibili://comment3/main/123/11?rp_id=9223372036854775808",
            "bilibili://comment3/main/123/11?sub_rp_id=789",
        ).forEach { assertNull(BilibiliNavigationTargetParser.parse(it), it) }
    }

    @Test
    fun nativePathRootWinsWhileQueryTargetOverridesPathAndFragment() {
        assertEquals(
            BilibiliNavigationTarget.CommentDetail(123L, 456L, 789L, 1, "bilibili://video/123"),
            BilibiliNavigationTargetParser.parse("bilibili://comment/detail/1/123/456/111?root=999&comment_id=789#reply222"),
        )
    }

    @Test
    fun legacyPictureAndMusicCommentBusinessesDoNotBecomeVideoIdentities() {
        listOf(11, 16, 47).forEach { type ->
            assertEquals(
                BilibiliNavigationTarget.CommentDetail(123L, 456L, 789L, type, ""),
                BilibiliNavigationTargetParser.parse("bilibili://comment/detail/$type/123/456/789"),
                type.toString(),
            )
        }
    }

    @Test
    fun nonPositiveOrOverflowingSubjectIdsCannotProduceCommentTargets() {
        listOf("0", "-1", "9223372036854775808").forEach { oid ->
            assertNull(BilibiliNavigationTargetParser.parse("bilibili://comment/detail/1/$oid/456"), oid)
            assertNull(BilibiliNavigationTargetParser.parse("https://www.bilibili.com/h5/comment/sub?oid=$oid&root=456"), oid)
        }
        assertNull(BilibiliNavigationTargetParser.parse("https://www.bilibili.com/video/av0?root=456"))
        assertNull(BilibiliNavigationTargetParser.parse("bilibili://following/detail/0?root=456"))
    }

    @Test
    fun sourceVideoParametersDoNotOverrideLinkDestination() {
        listOf(
            "https://www.bilibili.com/blackboard/activity?bvid=BV1xx411c7mD",
            "https://b23.tv/shop?from=av170001",
            "bilibili://mall/home?source=BV1xx411c7mD"
        ).forEach { url ->
            assertNull(BilibiliNavigationTargetParser.parse(url), url)
            assertNull(BilibiliUrlParser.parse(url).getVideoId(), url)
            assertNull(BilibiliUrlParser.parseDeepLink(url).getVideoId(), url)
        }
        assertEquals(
            BilibiliNavigationTarget.Article(123),
            BilibiliNavigationTargetParser.parse("https://www.bilibili.com/read/cv123?from=BV1xx411c7mD")
        )
        assertEquals(
            BilibiliNavigationTarget.Video("BV1xx411c7mD"),
            BilibiliNavigationTargetParser.parse("https://www.bilibili.com/video/BV1xx411c7mD?from=av170001")
        )
    }

    @Test
    fun externalLookalikesAndWrappedLinksCannotClaimNativeTargets() {
        listOf(
            "https://bilibili.com.example.org/video/BV1xx411c7mD",
            "https://fakeb23.tv/read/cv123",
            "https://bilibili.com@evil.example/read/cv123",
            "https://example.org/?url=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2FBV1xx411c7mD",
            "https://example.org/posts/BV1xx411c7mD",
        ).forEach { assertNull(BilibiliNavigationTargetParser.parse(it), it) }
    }

    @Test
    fun genuineNativeTargetsKeepPriorityAcrossContentTypes() {
        assertIs<BilibiliNavigationTarget.Video>(BilibiliNavigationTargetParser.parse("https://www.bilibili.com/video/BV1xx411c7mD"))
        assertIs<BilibiliNavigationTarget.Article>(BilibiliNavigationTargetParser.parse("https://www.bilibili.com/read/cv123"))
        assertIs<BilibiliNavigationTarget.Dynamic>(BilibiliNavigationTargetParser.parse("https://t.bilibili.com/1199344045210468386"))
        assertIs<BilibiliNavigationTarget.Space>(BilibiliNavigationTargetParser.parse("https://space.bilibili.com/123"))
        assertIs<BilibiliNavigationTarget.Live>(BilibiliNavigationTargetParser.parse("https://live.bilibili.com/123"))
        assertIs<BilibiliNavigationTarget.BangumiEpisode>(BilibiliNavigationTargetParser.parse("https://www.bilibili.com/bangumi/play/ep123"))
    }

    @Test
    fun weeklyLinksPreservePeriodAndIgnoreInvalidNumbers() {
        for (url in listOf(
            "bilibili://popular/weekly?number=133",
            "https://www.bilibili.com/v/popular/weekly?num=133",
            "https://m.bilibili.com/v/popular/weekly?number=133"
        )) {
            assertEquals(BilibiliNavigationTarget.PopularFeed("weekly", 133), BilibiliNavigationTargetParser.parse(url))
        }
        for (number in listOf("0", "-1", "invalid", "2147483648")) {
            assertEquals(BilibiliNavigationTarget.PopularFeed("weekly"),
                BilibiliNavigationTargetParser.parse("bilibili://popular/weekly?number=$number"))
        }
        assertNull(BilibiliNavigationTargetParser.parse("https://example.com/v/popular/weekly?num=133"))
    }


    @Test
    fun parse_commentSpaceSchema_resolvesSpaceTarget() {
        val target = BilibiliNavigationTargetParser.parse("bilibili://space/495695169")

        assertIs<BilibiliNavigationTarget.Space>(target)
        assertEquals(495695169L, target.mid)
    }

    @Test
    fun parse_wrappedSpaceUrl_resolvesSpaceTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "bilibili://browser?url=https%3A%2F%2Fspace.bilibili.com%2F123456"
        )

        assertIs<BilibiliNavigationTarget.Space>(target)
        assertEquals(123456L, target.mid)
    }

    @Test
    fun parse_wrappedLiveUrl_resolvesLiveTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "bilibili://browser?url=https%3A%2F%2Flive.bilibili.com%2F456789"
        )

        assertIs<BilibiliNavigationTarget.Live>(target)
        assertEquals(456789L, target.roomId)
    }

    @Test
    fun parse_bangumiSeasonUrl_resolvesSeasonTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "https://www.bilibili.com/bangumi/play/ss39708"
        )

        assertIs<BilibiliNavigationTarget.BangumiSeason>(target)
        assertEquals(39708L, target.seasonId)
    }

    @Test
    fun parse_cheeseSeasonUrl_resolvesSeasonTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "https://www.bilibili.com/cheese/play/ss150"
        )

        assertIs<BilibiliNavigationTarget.BangumiSeason>(target)
        assertEquals(150L, target.seasonId)
    }

    @Test
    fun parse_cheeseEpisodeUrl_resolvesEpisodeTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "https://www.bilibili.com/cheese/play/ep2425"
        )

        assertIs<BilibiliNavigationTarget.BangumiEpisode>(target)
        assertEquals(2425L, target.epId)
    }

    @Test
    fun parse_cheeseCustomSchemeSeason_resolvesSeasonTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "bilibili://cheese/season/150"
        )

        assertIs<BilibiliNavigationTarget.BangumiSeason>(target)
        assertEquals(150L, target.seasonId)
    }

    @Test
    fun parse_cheeseCustomSchemePlay_resolvesSeasonTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "bilibili://cheese/play/ss150"
        )

        assertIs<BilibiliNavigationTarget.BangumiSeason>(target)
        assertEquals(150L, target.seasonId)
    }

    @Test
    fun parse_bangumiMediaUrl_resolvesMediaTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "https://www.bilibili.com/bangumi/media/md28237119"
        )

        assertIs<BilibiliNavigationTarget.BangumiSeason>(target)
        assertEquals(0L, target.seasonId)
        assertEquals(28237119L, target.mediaId)
        assertEquals(
            target,
            BilibiliNavigationTargetParser.parse("md28237119")
        )
    }

    @Test
    fun parse_articleReadUrl_resolvesArticleTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "https://www.bilibili.com/read/cv12345"
        )

        assertIs<BilibiliNavigationTarget.Article>(target)
        assertEquals(12345L, target.articleId)
    }

    @Test
    fun parse_articleMobileReadUrl_resolvesArticleTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "https://www.bilibili.com/read/mobile?id=67890"
        )

        assertIs<BilibiliNavigationTarget.Article>(target)
        assertEquals(67890L, target.articleId)
    }

    @Test
    fun parse_nonReadBilibiliUrlWithId_doesNotResolveArticleTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "https://www.bilibili.com/blackboard/activity-test?id=12345"
        )

        assertNull(target)
    }

    @Test
    fun parse_followingDetailDeepLink_resolvesDynamicTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "bilibili://following/detail/1015637114125025318"
        )

        assertIs<BilibiliNavigationTarget.Dynamic>(target)
        assertEquals("1015637114125025318", target.dynamicId)
    }

    @Test
    fun parse_videoDeepLinkWithLikelyDynamicId_resolvesDynamicTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "bilibili://video/1199344045210468386"
        )

        assertIs<BilibiliNavigationTarget.Dynamic>(target)
        assertEquals("1199344045210468386", target.dynamicId)
    }

    @Test
    fun parse_searchDeepLink_resolvesSearchTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "bilibili://search?keyword=%E9%BB%91%E7%A5%9E%E8%AF%9D"
        )

        assertIs<BilibiliNavigationTarget.Search>(target)
        assertEquals("黑神话", target.keyword)
    }

    @Test
    fun parse_searchHttpUrl_resolvesSearchTarget() {
        val target = BilibiliNavigationTargetParser.parse(
            "https://search.bilibili.com/all?keyword=oppo%205g"
        )

        assertIs<BilibiliNavigationTarget.Search>(target)
        assertEquals("oppo 5g", target.keyword)
    }

    @Test
    fun parse_nonBilibiliUrl_returnsNull() {
        val target = BilibiliNavigationTargetParser.parse("https://example.com/video/1")

        assertNull(target)
    }
}
