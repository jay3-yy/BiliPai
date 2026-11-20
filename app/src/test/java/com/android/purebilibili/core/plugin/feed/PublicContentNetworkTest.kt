package com.android.purebilibili.core.plugin.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class PublicContentNetworkTest {
    @Test
    fun `public client has no account cookie jar authenticators or application interceptors`() {
        val client = createPublicContentHttpClient()
        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
        assertSame(Authenticator.NONE, client.authenticator)
        assertSame(Authenticator.NONE, client.proxyAuthenticator)
        assertEquals(0, client.interceptors.size)
        assertEquals(1, client.networkInterceptors.size)
        assertNull(client.cache)
    }

    @Test
    fun `feed and image requests lose credentials but retain conditional fetch and range headers`() {
        val request = Request.Builder().url("https://feeds.example.com/article")
            .header("Cookie", "SESSDATA=test-session")
            .header("Authorization", "Bearer test-token")
            .header("Access-Token", "test-access")
            .header("X-Bili-Mid", "123")
            .header("Referer", "https://www.bilibili.com/?access_key=test-access")
            .header("If-None-Match", "public-etag")
            .header("Range", "bytes=0-1023")
            .build()
        val safe = sanitizePublicContentRequest(request)
        listOf("Cookie", "Authorization", "Access-Token", "X-Bili-Mid", "Referer").forEach {
            assertNull(safe.header(it))
        }
        assertEquals("public-etag", safe.header("If-None-Match"))
        assertEquals("bytes=0-1023", safe.header("Range"))
    }

    @Test
    fun `token aliases encoded parameter names and userinfo are removed without losing article IDs`() {
        val request = Request.Builder()
            .url("https://user:password@example.com/article?id=42&access_key=a&Access-Token=b&%61ccess_token=c&refresh_token=d&SESSDATA=e&bili_jct=f&csrf=g")
            .build()
        val url = sanitizePublicContentRequest(request).url
        assertEquals(setOf("id"), url.queryParameterNames)
        assertEquals("42", url.queryParameter("id"))
        assertEquals("", url.username)
        assertEquals("", url.password)
    }

    @Test
    fun `redirect destinations and CDN images cannot reintroduce credentials`() {
        val redirected = Request.Builder()
            .url("https://images.example.net/photo?access_token=redirect-token&size=large")
            .header("Cookie", "redirect-session")
            .header("Authorization", "redirect-auth")
            .build()
        val safe = sanitizePublicContentRequest(redirected)
        assertEquals("https://images.example.net/photo?size=large", safe.url.toString())
        assertNull(safe.header("Cookie"))
        assertNull(safe.header("Authorization"))
        val cdn = sanitizePublicContentRequest(Request.Builder().url("https://i0.hdslb.com/a.jpg")
            .header("Cookie", "test-session").header("Referer", "https://example.com/?access_token=test").build())
        assertEquals("https://www.bilibili.com/", cdn.header("Referer"))
        assertNull(cdn.header("Cookie"))
        assertFalse(safe.url.toString().contains("redirect-token"))
    }

    @Test
    fun `automatic previews strip share parameters and never add image referers`() {
        val previewUrl = publicLinkPreviewUrl("https://example.com/post?share_id=42&SESSDATA=session#part")
        assertEquals("https://example.com/post".toHttpUrl(), previewUrl)
        val image = Request.Builder().url("https://i0.hdslb.com/a.jpg")
            .header("Cookie", "SESSDATA=test-session")
            .header("Authorization", "Bearer test-token")
            .header("Referer", "https://example.com/post")
            .build()
        val safe = sanitizePublicContentRequest(image, includeBiliImageReferer = false)
        assertNull(safe.header("Cookie"))
        assertNull(safe.header("Authorization"))
        assertNull(safe.header("Referer"))
        assertFalse(publicLinkPreviewHttpClient.followRedirects)
    }

    @Test
    fun `preview handles a pasted article address with a trailing no-break space`() {
        val page = "https://developer.arm.com/community/posts/tcs23"
        assertEquals(page.toHttpUrl(), publicLinkPreviewUrl("$page\u00A0"))
        assertEquals(page.toHttpUrl(), publicLinkPreviewUrl("$page%C2%A0?share=1"))
    }

    @Test
    fun `article preview prefers the main heading when HTML title describes the site`() {
        val html = """
            <head><title>Arm Community</title><meta name="description" content="Arm community site"></head>
            <main><h1>Introducing TCS23</h1><p>An overview of the new platform.</p></main>
        """.trimIndent()
        assertEquals(
            PublicLinkPreview("Introducing TCS23", "An overview of the new platform.", null),
            parsePublicLinkPreview(html, "https://developer.arm.com/community/posts/tcs23".toHttpUrl()),
        )
    }

    @Test
    fun `partial HTML can use a complete article heading and summary before the main element ends`() {
        val page = "https://developer.arm.com/community/posts/tcs23".toHttpUrl()
        val head = "<html><head><title>Arm Community</title></head><body>"
        assertNull(parsePublicLinkPreview(head + "<main>", page, isComplete = false))
        assertNull(parsePublicLinkPreview(head + "<main><h1>Introducing TCS23</h1><p>An overview", page, isComplete = false))
        assertEquals(
            PublicLinkPreview("Introducing TCS23", "An overview of the new platform.", null),
            parsePublicLinkPreview(
                head + "<main><h1>Introducing TCS23</h1><p>An overview of the new platform.</p><section>",
                page,
                isComplete = false,
            ),
        )
    }

    @Test
    fun `twitter card metadata supplies the preview when Open Graph is absent`() {
        val html = """
            <head>
                <meta name="twitter:title" content="Article title">
                <meta name="twitter:description" content="Article summary">
                <meta name="twitter:image" content="/cover.png">
            </head>
            <body><main>
        """.trimIndent()
        val page = "https://example.com/article".toHttpUrl()
        assertEquals(
            PublicLinkPreview("Article title", "Article summary", "https://example.com/cover.png".toHttpUrl()),
            parsePublicLinkPreview(html, page, isComplete = false),
        )
    }

    @Test
    fun `public page redirects stay on the source site and drop tracking data`() {
        val page = "https://developer.arm.com/old".toHttpUrl()
        assertEquals(
            "https://community.arm.com/new".toHttpUrl(),
            resolvePublicPreviewRedirect(page, "https://community.arm.com/new?token=secret#section"),
        )
        listOf(
            "https://arm.com.example.org/new",
            "http://community.arm.com/new",
            "https://192.168.1.1/new",
        ).forEach { assertNull(resolvePublicPreviewRedirect(page, it), it) }
    }

    @Test
    fun `preview metadata resolves public relative covers without sending their tracking parameters`() {
        val html = """
            <meta property="og:title" content="Public article">
            <meta property="og:description" content="Article summary">
            <meta property="og:image" content="/cover.jpg?viewer=42">
        """.trimIndent()
        val url = "https://example.com/post".toHttpUrl()
        assertEquals(
            PublicLinkPreview("Public article", "Article summary", "https://example.com/cover.jpg".toHttpUrl()),
            parsePublicLinkPreview(html, url),
        )
        assertEquals(
            PublicLinkPreview("Public article", "Article summary", null),
            parsePublicLinkPreview(html.replace("/cover.jpg?viewer=42", "https://192.168.1.1/cover.jpg"), url),
        )
    }

    @Test
    fun `public image client adds a same site referer and stays anonymous`() {
        val client = publicContentImageHttpClient
        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
        assertSame(Authenticator.NONE, client.authenticator)
        assertSame(Authenticator.NONE, client.proxyAuthenticator)
        assertEquals(0, client.interceptors.size)
        assertEquals(2, client.networkInterceptors.size)
        assertNull(client.cache)
    }

    @Test
    fun `hotlink protected image requests regain the article site as referer`() {
        val sanitized = sanitizePublicContentRequest(
            Request.Builder().url("https://cdnfile.sspai.com/2026/10/16/pic.jpeg?imageView2/2/w/1120").build(),
        )
        assertNull(sanitized.header("Referer"))
        assertEquals("https://sspai.com/", withPublicImageReferer(sanitized).header("Referer"))
    }

    @Test
    fun `gcores cover images use the canonical www site after sanitizing`() {
        val request = Request.Builder()
            .url("https://image.gcores.com/cover.png?x-oss-process=image/resize,w_626")
            .header("Referer", "https://www.bilibili.com/?access_key=test")
            .header("Cookie", "test-session")
            .build()
        val safe = withPublicImageReferer(sanitizePublicContentRequest(request))
        assertEquals("https://www.gcores.com/", safe.header("Referer"))
        assertNull(safe.header("Cookie"))
        assertEquals("image/resize,w_626", safe.url.queryParameter("x-oss-process"))
        assertEquals(
            "https://example.com/",
            publicImageReferer("https://gcores.com.example.com/cover.png".toHttpUrl()),
        )
    }

    @Test
    fun `image referer follows the public site root without touching the bilibili CDN rule`() {
        assertEquals("https://sspai.com/", publicImageReferer("https://cdnfile.sspai.com/a.jpeg".toHttpUrl()))
        assertEquals("https://example.com/", publicImageReferer("https://example.com/a.jpg".toHttpUrl()))
        assertEquals("https://example.com.cn/", publicImageReferer("https://img.example.com.cn/a.jpg".toHttpUrl()))
        assertEquals("http://127.0.0.1/", publicImageReferer("http://127.0.0.1:8080/a.jpg".toHttpUrl()))
        val bilibili = withPublicImageReferer(
            sanitizePublicContentRequest(Request.Builder().url("https://i0.hdslb.com/a.jpg").build()),
        )
        assertEquals("https://www.bilibili.com/", bilibili.header("Referer"))
    }
}
