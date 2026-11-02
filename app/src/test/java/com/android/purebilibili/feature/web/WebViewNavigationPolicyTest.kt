package com.android.purebilibili.feature.web

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.core.util.hasCommentLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebViewNavigationPolicyTest {
    @Test
    fun spaceSubpagesKeepTheirWebPathWhileRootProfilesStillOpenNatively() {
        listOf("upload/video", "upower/rank", "favlist").forEach { path ->
            listOf(false, true).forEach { gesture ->
                assertEquals(WebViewNavigationAction.AllowWebLoad,
                    resolveWebViewNavigationAction("https://space.bilibili.com/42/$path", gesture))
            }
        }
        assertEquals(WebViewNavigationAction.DispatchTarget(BilibiliNavigationTarget.Space(42L)),
            resolveWebViewNavigationAction("https://space.bilibili.com/42", true))
        assertIs<WebViewNavigationAction.OpenExternal>(resolveWebViewNavigationAction("https://space.bilibili.com.example.org/42/upload/video", true))
    }

    @Test
    fun dynamicPreviewLinksRetainCommentIntentForHostDispatchInsteadOfIdOnlyCallback() {
        listOf(
            "bilibili://following/detail/123?tab=comment" to
                BilibiliNavigationTarget.Dynamic("123", openComments = true),
            "https://t.bilibili.com/123?tab=comment" to
                BilibiliNavigationTarget.Dynamic("123", openComments = true),
            "bilibili://following/detail/123?tab=comment&root=456&comment_id=789" to
                BilibiliNavigationTarget.Dynamic("123", 456L, 789L, openComments = true),
        ).forEach { (link, expected) ->
            val action = assertIs<WebViewNavigationAction.DispatchTarget>(
                resolveWebViewNavigationAction(link, hasUserGesture = true),
            )
            assertEquals(expected, action.target, link)
            assertTrue(action.target.hasCommentLocation, link)
        }
    }

    @Test
    fun gestureCommentDeepLinksDispatchWithoutDroppingLocationInWebConversion() {
        listOf(
            "bilibili://video/123?root=456&comment_id=789" to
                BilibiliNavigationTarget.Video("av123", 456L, 789L),
            "bilibili://article/123?root=456#reply789" to
                BilibiliNavigationTarget.Article(123L, 456L, 789L),
            "bilibili://comment/detail/47/999/456/789" to
                BilibiliNavigationTarget.CommentDetail(999L, 456L, 789L, 47, ""),
        ).forEach { (link, target) ->
            assertEquals(
                WebViewNavigationAction.DispatchTarget(target),
                resolveWebViewNavigationAction(link, hasUserGesture = true),
                link,
            )
            assertEquals(
                WebViewNavigationAction.Block,
                resolveWebViewNavigationAction(link, hasUserGesture = false),
                link,
            )
        }
    }

    @Test
    fun webCommentLinksKeepLocationForHostCommentLinkDispatch() {
        assertEquals(
            WebViewNavigationAction.DispatchTarget(BilibiliNavigationTarget.Video("BV1xx411c7mD", 456L, 789L)),
            resolveWebViewNavigationAction(
                "https://www.bilibili.com/video/BV1xx411c7mD?root=456#reply789",
                hasUserGesture = true,
            ),
        )
    }


    @Test
    fun shoppingRedirectOpensExternallyWithoutUserGesture() {
        val url = "https://item.jd.com/123.html"
        val action = resolveWebViewNavigationAction(url, hasUserGesture = false)
        assertEquals(WebViewNavigationAction.OpenExternal(url), action)
    }

    @Test
    fun officialShortLinkContinuesLoadingToFollowRedirects() {
        assertIs<WebViewNavigationAction.AllowWebLoad>(
            resolveWebViewNavigationAction("https://b23.tv/shopping", hasUserGesture = false)
        )
    }

    @Test
    fun bilibiliLookalikeHostsOpenExternally() {
        listOf("bilibili.com.example.org", "fakeb23.tv", "b23.tv.example.org").forEach { host ->
            val url = "https://$host/shop"
            assertEquals(
                WebViewNavigationAction.OpenExternal(url),
                resolveWebViewNavigationAction(url, hasUserGesture = false)
            )
        }
    }

    @Test
    fun officialActivityPageStaysInWebView() {
        assertIs<WebViewNavigationAction.AllowWebLoad>(
            resolveWebViewNavigationAction(
                "https://www.bilibili.com/blackboard/activity", hasUserGesture = false
            )
        )
    }

    @Test
    fun resolveWebViewNavigationAction_blocksAutoAppVideoDeepLink() {
        val action = resolveWebViewNavigationAction(
            urlString = "bilibili://video/170001",
            hasUserGesture = false
        )

        assertIs<WebViewNavigationAction.Block>(action)
    }

    @Test
    fun resolveWebViewNavigationAction_convertsGestureVideoDeepLinkToWebUrl() {
        val action = resolveWebViewNavigationAction(
            urlString = "bilibili://video/170001",
            hasUserGesture = true
        )

        val loadAction = assertIs<WebViewNavigationAction.LoadInWebView>(action)
        assertEquals("https://m.bilibili.com/video/av170001", loadAction.url)
    }

    @Test
    fun resolveWebViewNavigationAction_dispatchesGestureVideoDeepLinkWithDynamicId() {
        val action = resolveWebViewNavigationAction(
            urlString = "bilibili://video/1199344045210468386",
            hasUserGesture = true
        )

        val dispatchAction = assertIs<WebViewNavigationAction.DispatchTarget>(action)
        val target = assertIs<BilibiliNavigationTarget.Dynamic>(dispatchAction.target)
        assertEquals("1199344045210468386", target.dynamicId)
    }

    @Test
    fun resolveWebViewNavigationAction_dispatchesDynamicWebLinks() {
        val action = resolveWebViewNavigationAction(
            urlString = "https://t.bilibili.com/1199344045210468386",
            hasUserGesture = true
        )

        val dispatchAction = assertIs<WebViewNavigationAction.DispatchTarget>(action)
        val target = assertIs<BilibiliNavigationTarget.Dynamic>(dispatchAction.target)
        assertEquals("1199344045210468386", target.dynamicId)
    }

    @Test
    fun resolveWebViewNavigationAction_dispatchesMobileOpusLinks() {
        val action = resolveWebViewNavigationAction(
            urlString = "https://m.bilibili.com/opus/1199344045210468386",
            hasUserGesture = true
        )

        val dispatchAction = assertIs<WebViewNavigationAction.DispatchTarget>(action)
        val target = assertIs<BilibiliNavigationTarget.Dynamic>(dispatchAction.target)
        assertEquals("1199344045210468386", target.dynamicId)
    }
}
