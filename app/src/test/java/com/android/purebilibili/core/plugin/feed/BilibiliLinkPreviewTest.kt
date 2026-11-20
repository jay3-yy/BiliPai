package com.android.purebilibili.core.plugin.feed

import com.android.purebilibili.feature.web.shouldAttemptInstantArticle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl

class BilibiliLinkPreviewTest {
    @Test
    fun `bilibili metadata requests send only the public content ID`() {
        val links = mapOf(
            "https://www.bilibili.com/video/BV1xx411c7mD?p=2&SESSDATA=session" to
                "https://api.bilibili.com/x/web-interface/view?bvid=BV1xx411c7mD",
            "https://live.bilibili.com/6?from=feed" to
                "https://api.live.bilibili.com/room/v1/Room/get_info?room_id=6",
            "https://www.bilibili.com/bangumi/play/ss41410?access_key=token" to
                "https://api.bilibili.com/pgc/view/web/season?season_id=41410",
        )
        links.forEach { (link, endpoint) ->
            val page = assertNotNull(publicLinkPreviewUrl(link))
            assertEquals(endpoint.toHttpUrl(), BilibiliPreviewLookup.forPage(page)?.url)
        }

        val unrelated = "https://www.bilibili.com.evil.example/video/BV1xx411c7mD".toHttpUrl()
        assertNull(BilibiliPreviewLookup.forPage(unrelated))
    }

    @Test
    fun `toy game uses anonymous HTML metadata and keeps its web destination`() {
        val url = "https://www.bilibili.com/toy/pvz_hybrid_v319/index.html"
        val page = url.toHttpUrl()
        assertNull(BilibiliPreviewLookup.forPage(page))
        assertTrue(isPublicBilibiliToyPage(page))
        assertFalse(shouldAttemptInstantArticle(url))
        assertEquals(
            "植物大战僵尸 · 杂交版",
            parsePublicLinkPreview("<title>植物大战僵尸 · 杂交版</title>", page)?.title,
        )
    }

    @Test
    fun `bilibili short links resolve to a public content ID without forwarding share data`() {
        val short = assertNotNull(publicLinkPreviewUrl("https://b23.tv/1RLfKkP?share_session_id=sender"))
        assertTrue(isPublicBilibiliShortLink(short))
        assertEquals("https://b23.tv/1RLfKkP".toHttpUrl(), short)

        val page = assertNotNull(resolveBilibiliShortPreviewPage(
            short,
            "https://www.bilibili.com/video/BV1v1pa63Er1?buvid=device&mid=sender",
        ))
        assertEquals("https://www.bilibili.com/video/BV1v1pa63Er1".toHttpUrl(), page)
        assertEquals(
            "https://api.bilibili.com/x/web-interface/view?bvid=BV1v1pa63Er1".toHttpUrl(),
            BilibiliPreviewLookup.forPage(page)?.url,
        )
    }

    @Test
    fun `short link previews stop at off-site redirects and additional short links`() {
        val short = "https://b23.tv/1RLfKkP".toHttpUrl()
        listOf(
            "https://www.bilibili.com.example.org/video/BV1v1pa63Er1",
            "https://b23.tv/second",
            "http://www.bilibili.com/video/BV1v1pa63Er1",
            "https://user:password@www.bilibili.com/video/BV1v1pa63Er1",
        ).forEach { assertNull(resolveBilibiliShortPreviewPage(short, it), it) }
    }

    @Test
    fun `bangumi preview uses public title and upgrades the cover URL to HTTPS`() {
        val json = """
            {"code":0,"result":{"title":"间谍过家家","evaluate":"番剧简介",
              "cover":"http://i0.hdslb.com/bfs/bangumi/image/cover.png?viewer=42"}}
        """.trimIndent()
        assertEquals(
            PublicLinkPreview(
                "间谍过家家", "番剧简介", "https://i0.hdslb.com/bfs/bangumi/image/cover.png".toHttpUrl(),
            ),
            parseBilibiliLinkPreview(json),
        )
        assertNull(parseBilibiliLinkPreview("""{"code":-352,"message":"请求被拒绝"}"""))
    }
}
