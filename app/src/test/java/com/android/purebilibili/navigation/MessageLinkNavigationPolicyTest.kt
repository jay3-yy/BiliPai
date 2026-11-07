package com.android.purebilibili.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageLinkNavigationPolicyTest {
    @Test
    fun dynamicCommentTabUsesDynamicCommentActionEvenWithoutReplyIds() {
        assertEquals(
            MessageLinkNavigationAction.DynamicComment("123", 0L, openComments = true),
            resolveMessageLinkNavigationAction("bilibili://following/detail/123?tab=comment"),
        )
        assertEquals(
            MessageLinkNavigationAction.DynamicComment("123", 456L, 789L, openComments = true),
            resolveMessageLinkNavigationAction("bilibili://following/detail/123?tab=comment&root=456&comment_id=789"),
        )
        assertEquals(
            MessageLinkNavigationAction.Dynamic("123"),
            resolveMessageLinkNavigationAction("bilibili://following/detail/123"),
        )
    }


    @Test
    fun articleLinksPreserveRootAndTargetAcrossEntryForms() {
        val links = listOf(
            "https://www.bilibili.com/read/cv123?comment_root_id=456#reply789",
            "bilibili://read/cv123?root_reply_id=456&comment_secondary_id=789",
            "https://www.bilibili.com/h5/comment/sub?oid=123&pageType=12&root=456&comment_secondary_id=789",
            "bilibili://comment/msg_fold/12/123/456/789?enterUri=https%3A%2F%2Fwww.bilibili.com%2Fopus%2F999",
            "bilibili://browser/?url=https%3A%2F%2Fwww.bilibili.com%2Fread%2Fcv123%3Froot_id%3D456%26comment_id%3D789",
        )
        links.forEach { link ->
            assertEquals(MessageLinkNavigationAction.Article(123L, 456L, 789L), resolveMessageLinkNavigationAction(link), link)
        }
    }

    @Test
    fun articleFragmentAlonePreservesTargetWithoutInventingRoot() {
        assertEquals(
            MessageLinkNavigationAction.Article(123L, 0L, 456L),
            resolveMessageLinkNavigationAction("https://www.bilibili.com/read/cv123#reply456"),
        )
    }

    @Test
    fun commentEntryFormsShareRootAliasesAndNormalizeRootTargetEquality() {
        listOf(
            "https://www.bilibili.com/h5/comment/sub?oid=123&pageType=1&root_id=456&comment_id=456",
            "bilibili://comment/detail/1/123/456?comment_id=456",
            "bilibili://video/123?root=456&comment_id=456",
        ).forEach { link ->
            assertEquals(
                MessageLinkNavigationAction.CommentDetail(123L, 456L, 0L, 1, "bilibili://video/123"),
                resolveMessageLinkNavigationAction(link),
                link,
            )
        }
    }

    @Test
    fun encodedFragmentsAndQueriesKeepLocationAndQueryTargetWins() {
        assertEquals(
            MessageLinkNavigationAction.VideoComment("BV1xx411c7mD", 456L, 789L),
            resolveMessageLinkNavigationAction("https://www.bilibili.com/video/BV1xx411c7mD?root=%34%35%36#reply%37%38%39"),
        )
        assertEquals(
            MessageLinkNavigationAction.VideoComment("BV1xx411c7mD", 456L, 999L),
            resolveMessageLinkNavigationAction("https://www.bilibili.com/video/BV1xx411c7mD?root=456&comment_id=999#reply789"),
        )
    }

    @Test
    fun targetOnlyLinksOpenResolvableDetailWithoutPretendingTargetIsRoot() {
        assertEquals(
            MessageLinkNavigationAction.CommentDetail(123L, 0L, 789L, 1, "bilibili://video/123"),
            resolveMessageLinkNavigationAction("bilibili://comment/detail/1/123/0?comment_id=789"),
        )
        assertEquals(
            MessageLinkNavigationAction.CommentDetail(123L, 0L, 789L, 17, "bilibili://following/detail/123"),
            resolveMessageLinkNavigationAction("https://www.bilibili.com/h5/comment/sub?oid=123&pageType=17&reply_id=789"),
        )
    }

    @Test
    fun malformedCommentRoutesDoNotCreateNativeCommentDestinations() {
        listOf(
            "bilibili://comment/detail/0/123/456",
            "bilibili://comment/detail/-1/123/456",
            "bilibili://comment/detail/1/0/456",
            "bilibili://comment/detail/1/-1/456",
            "bilibili://comment/detail/1/123/-1",
            "bilibili://comment/detail/1/123/0",
        ).forEach { link ->
            assertEquals(MessageLinkNavigationAction.Web(""), resolveMessageLinkNavigationAction(link), link)
        }
        listOf(
            "https://bilibili.com.example.org/h5/comment/sub?oid=123&root=456",
            "https://www.bilibili.com/h5/comment/sub?oid=-1&root=456",
            "https://www.bilibili.com/h5/comment/sub?oid=123&root=456&pageType=0",
        ).forEach { link ->
            assertEquals(MessageLinkNavigationAction.Web(link), resolveMessageLinkNavigationAction(link), link)
        }
    }

    @Test
    fun musicCommentBusinessKeepsOidTypeAndExplicitEntryUri() {
        val enterUri = "https://music.bilibili.com/h5/music-detail?music_id=MA123"
        assertEquals(
            MessageLinkNavigationAction.CommentDetail(999L, 456L, 789L, 47, enterUri),
            resolveMessageLinkNavigationAction(
                "bilibili://comment/detail/47/999/456/789?enterUri=https%3A%2F%2Fmusic.bilibili.com%2Fh5%2Fmusic-detail%3Fmusic_id%3DMA123"
            ),
        )
        assertEquals(
            MessageLinkNavigationAction.CommentDetail(999L, 456L, 789L, 47, ""),
            resolveMessageLinkNavigationAction("https://www.bilibili.com/h5/comment/sub?oid=999&pageType=47&root=456&comment_id=789"),
        )
    }

    @Test
    fun wrappedUnknownWebLinkStillOpensItsOriginalUrl() {
        assertEquals(
            MessageLinkNavigationAction.Web("https://www.bilibili.com/blackboard/activity"),
            resolveMessageLinkNavigationAction("bilibili://browser/?url=https%3A%2F%2Fwww.bilibili.com%2Fblackboard%2Factivity"),
        )
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesAidDeepLinkWithCommentRootToCommentDetail() {
        val action = resolveMessageLinkNavigationAction(
            "bilibili://video/115391124741470?page=0&comment_root_id=279569905408"
        )

        val commentAction = assertIs<MessageLinkNavigationAction.CommentDetail>(action)
        assertEquals(115391124741470L, commentAction.oid)
        assertEquals(279569905408L, commentAction.rootReplyId)
        assertEquals(0L, commentAction.targetReplyId)
        assertEquals(1, commentAction.businessId)
        assertEquals("bilibili://video/115391124741470", commentAction.enterUri)
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesWebVideoFragmentReplyToVideoComment() {
        val action = resolveMessageLinkNavigationAction(
            "https://www.bilibili.com/video/BV1xx411c7mD?comment_root_id=1#reply2"
        )

        val videoAction = assertIs<MessageLinkNavigationAction.VideoComment>(action)
        assertEquals("BV1xx411c7mD", videoAction.videoId)
        assertEquals(1L, videoAction.rootReplyId)
        assertEquals(2L, videoAction.targetReplyId)
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesVideoDeepLinkWithoutCommentToVideo() {
        val action = resolveMessageLinkNavigationAction(
            "bilibili://video/115391124741470?page=0"
        )

        val videoAction = assertIs<MessageLinkNavigationAction.Video>(action)
        assertEquals("av115391124741470", videoAction.videoId)
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesLikelyDynamicCommentToCommentDetail() {
        val action = resolveMessageLinkNavigationAction(
            "bilibili://comment/detail/1/1199344045210468386/265141324256"
        )

        val commentAction = assertIs<MessageLinkNavigationAction.CommentDetail>(action)
        assertEquals(1199344045210468386L, commentAction.oid)
        assertEquals(265141324256L, commentAction.rootReplyId)
        assertEquals(0L, commentAction.targetReplyId)
        assertEquals(17, commentAction.businessId)
        assertEquals("bilibili://following/detail/1199344045210468386", commentAction.enterUri)
    }

    @Test
    fun resolveMessageLinkNavigationAction_decodesEncodedEnterUriCompatibly() {
        val action = resolveMessageLinkNavigationAction(
            "bilibili://comment/detail/1/1199344045210468386/265141324256" +
                "?enterUri=bilibili%3A%2F%2Ffollowing%2Fdetail%2F1199344045210468386" +
                "&comment_id=265141324999"
        )

        val commentAction = assertIs<MessageLinkNavigationAction.CommentDetail>(action)
        assertEquals(1199344045210468386L, commentAction.oid)
        assertEquals(265141324256L, commentAction.rootReplyId)
        assertEquals(265141324999L, commentAction.targetReplyId)
        assertEquals(17, commentAction.businessId)
        assertEquals("bilibili://following/detail/1199344045210468386", commentAction.enterUri)
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesOpusCommentLinkToCommentDetail() {
        val action = resolveMessageLinkNavigationAction(
            "bilibili://opus/detail/1073543151725051921?comment_root_id=265141324256&comment_on=1"
        )

        val commentAction = assertIs<MessageLinkNavigationAction.CommentDetail>(action)
        assertEquals(1073543151725051921L, commentAction.oid)
        assertEquals(265141324256L, commentAction.rootReplyId)
        assertEquals(0L, commentAction.targetReplyId)
        assertEquals(17, commentAction.businessId)
        assertEquals("bilibili://following/detail/1073543151725051921", commentAction.enterUri)
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesArticleCommentBusinessToArticle() {
        val action = resolveMessageLinkNavigationAction(
            "bilibili://comment/detail/12/34646640/265141324256"
        )

        val commentAction = assertIs<MessageLinkNavigationAction.Article>(action)
        assertEquals(34646640L, commentAction.articleId)
        assertEquals(265141324256L, commentAction.rootReplyId)
        assertEquals(0L, commentAction.targetReplyId)
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesAnchorParameterToTargetReplyId() {
        val action = resolveMessageLinkNavigationAction(
            "bilibili://comment/detail/17/832703053858603029/238686570016/?anchor=238686628816"
        )

        val commentAction = assertIs<MessageLinkNavigationAction.CommentDetail>(action)
        assertEquals(832703053858603029L, commentAction.oid)
        assertEquals(238686570016L, commentAction.rootReplyId)
        assertEquals(238686628816L, commentAction.targetReplyId)
        assertEquals(17, commentAction.businessId)
        assertEquals("bilibili://following/detail/832703053858603029", commentAction.enterUri)
    }

    @Test
    fun buildMessageFeedCommentNavigationLink_handlesQueryOnlyNativeUri() {
        val link = com.android.purebilibili.feature.message.feed.buildMessageFeedCommentNavigationLink(
            nativeUri = "?comment_id=238686628816",
            uri = "https://www.bilibili.com/video/BV1xx411c7mD",
            businessId = 1,
            subjectId = 115391124741470L,
            rootId = 238686570016L,
            sourceId = 238686628816L,
            targetId = 238686628816L,
        )

        assertEquals(
            "bilibili://comment/detail/1/115391124741470/238686570016?comment_id=238686628816&enterUri=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2FBV1xx411c7mD",
            link
        )
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesMsgFoldVideoComment() {
        val action = resolveMessageLinkNavigationAction(
            "bilibili://comment/msg_fold/1/22222/33333/11111/?enterUri=bilibili%3A%2F%2Fvideo%2F22222"
        )

        val commentAction = assertIs<MessageLinkNavigationAction.CommentDetail>(action)
        assertEquals(22222L, commentAction.oid)
        assertEquals(33333L, commentAction.rootReplyId)
        assertEquals(11111L, commentAction.targetReplyId)
        assertEquals(1, commentAction.businessId)
        assertEquals("bilibili://video/22222", commentAction.enterUri)
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesH5CommentSubToCommentDetail() {
        val action = resolveMessageLinkNavigationAction(
            "https://www.bilibili.com/h5/comment/sub?oid=12345&pageType=1&root=67890&comment_secondary_id=11111"
        )

        val commentAction = assertIs<MessageLinkNavigationAction.CommentDetail>(action)
        assertEquals(12345L, commentAction.oid)
        assertEquals(67890L, commentAction.rootReplyId)
        assertEquals(11111L, commentAction.targetReplyId)
        assertEquals(1, commentAction.businessId)
        assertEquals("bilibili://video/12345", commentAction.enterUri)
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesH5CommentSubToDynamicCommentDetail() {
        val action = resolveMessageLinkNavigationAction(
            "https://www.bilibili.com/h5/comment/sub?oid=832703053858603029&pageType=17&root=238686570016&comment_secondary_id=238686628816"
        )

        val commentAction = assertIs<MessageLinkNavigationAction.CommentDetail>(action)
        assertEquals(832703053858603029L, commentAction.oid)
        assertEquals(238686570016L, commentAction.rootReplyId)
        assertEquals(238686628816L, commentAction.targetReplyId)
        assertEquals(17, commentAction.businessId)
        assertEquals("bilibili://following/detail/832703053858603029", commentAction.enterUri)
    }

    @Test
    fun resolveMessageLinkNavigationAction_resolvesBrowserUrlRecursively() {
        val action = resolveMessageLinkNavigationAction(
            "bilibili://browser/?url=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2FBV1xx411c7mD%3Fcomment_root_id%3D123%26comment_secondary_id%3D456"
        )

        val videoAction = assertIs<MessageLinkNavigationAction.VideoComment>(action)
        assertEquals("BV1xx411c7mD", videoAction.videoId)
        assertEquals(123L, videoAction.rootReplyId)
        assertEquals(456L, videoAction.targetReplyId)
    }

    @Test
    fun resolveMessageLinkNavigationAction_routesArticleSchemeToArticle() {
        val action = resolveMessageLinkNavigationAction(
            "bilibili://article/40679479?jump_opus=1"
        )

        val articleAction = assertIs<MessageLinkNavigationAction.Article>(action)
        assertEquals(40679479L, articleAction.articleId)
    }

    @Test
    fun resolveMessageLinkNavigationAction_doesNotRouteCustomSchemeToWeb() {
        val action = resolveMessageLinkNavigationAction("bilibili://unknown_feature/xyz")
        val webAction = assertIs<MessageLinkNavigationAction.Web>(action)
        assertEquals("", webAction.url)
    }

    @Test
    fun buildMessageFeedCommentNavigationLink_buildsCommentLinkWhenNativeUriIsPlainVideoUrl() {
        val link = com.android.purebilibili.feature.message.feed.buildMessageFeedCommentNavigationLink(
            nativeUri = "https://www.bilibili.com/video/BV1xx411c7mD",
            uri = "https://www.bilibili.com/video/BV1xx411c7mD",
            businessId = 1,
            subjectId = 115391124741470L,
            rootId = 238686570016L,
            sourceId = 238686628816L,
            targetId = 238686628816L,
            business = "视频"
        )

        assertEquals(
            "bilibili://comment/detail/1/115391124741470/238686570016?comment_id=238686628816&enterUri=https%3A%2F%2Fwww.bilibili.com%2Fvideo%2FBV1xx411c7mD",
            link
        )
    }

    @Test
    fun buildMessageFeedCommentNavigationLink_infersBusinessIdFromBusinessText() {
        val link = com.android.purebilibili.feature.message.feed.buildMessageFeedCommentNavigationLink(
            nativeUri = "",
            uri = "https://t.bilibili.com/832703053858603029",
            businessId = 0,
            subjectId = 832703053858603029L,
            rootId = 238686570016L,
            sourceId = 238686628816L,
            targetId = 238686628816L,
            business = "动态"
        )

        assertEquals(
            "bilibili://comment/detail/17/832703053858603029/238686570016?comment_id=238686628816&enterUri=https%3A%2F%2Ft.bilibili.com%2F832703053858603029",
            link
        )
    }
    @Test
    fun messageWithoutRootKeepsSourceReplyAsUnresolvedTarget() {
        val link = com.android.purebilibili.feature.message.feed.buildMessageFeedCommentNavigationLink(
            nativeUri = null, uri = null, businessId = 1, subjectId = 123L,
            rootId = 0L, sourceId = 789L, targetId = 0L,
        )
        assertEquals(
            "bilibili://comment/detail/1/123/0?comment_id=789&enterUri=bilibili%3A%2F%2Fvideo%2F123",
            link,
        )
    }
}
