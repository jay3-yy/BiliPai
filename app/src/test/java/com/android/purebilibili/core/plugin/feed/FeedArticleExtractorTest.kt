package com.android.purebilibili.core.plugin.feed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedArticleExtractorTest {
    @Test
    fun `standalone reader receives metadata and the resolved source URL`() {
        val html = """
            <html><head><title>独立网页标题</title><meta name="author" content="文章作者"></head>
            <body><article><p>这是一篇独立网页文章，正文足够长，可以在应用内完整阅读而无需先创建订阅。</p></article></body></html>
        """.trimIndent()
        val article = assertNotNull(extractReadableArticle(html, "https://example.com/posts/final"))
        assertEquals("独立网页标题", article.title)
        assertEquals("https://example.com/posts/final", article.url)
        assertTrue(feedPlainText(article.html).contains("无需先创建订阅"))
    }

    @Test
    fun `local readability retains article while dropping comments and recommendations`() {
        val paragraph = "正文讲述本地阅读的工作方式，下载网页后提取文章段落，保留有用的标题与图片。".repeat(8)
        val html = """
            <html><body><nav>网站导航</nav>
            <div class="layout"><div class="writing"><h2>正文小节</h2>
            <p>$paragraph</p><p>最后一段。$paragraph</p></div>
            <div class="related-posts"><p>推荐干扰文字</p></div>
            <div id="comments"><p>评论干扰文字</p></div></div>
            <footer>页脚干扰文字</footer></body></html>
        """.trimIndent()

        val body = assertNotNull(extractArticleBody(html, "https://example.com/posts/1"))
        assertTrue(feedPlainText(body).contains("最后一段"))
        listOf("网站导航", "推荐干扰文字", "评论干扰文字", "页脚干扰文字").forEach {
            assertFalse(feedPlainText(body).contains(it))
        }
    }

    @Test
    fun `short Chinese article retains headings and lazy images with absolute URLs`() {
        val html = """
            <article class="entry-content"><h2>小节</h2>
            <p>这是一篇简短的中文文章，仍然需要保留正文和配图。</p>
            <img src="data:image/gif;base64,AAAA" data-src="../images/photo.jpg">
            <p><a href="../next?from=reader">下一篇</a></p></article>
        """.trimIndent()

        val body = assertNotNull(extractArticleBody(html, "https://example.com:8443/posts/one"))
        val blocks = parseFeedHtml(body)
        assertEquals("https://example.com:8443/images/photo.jpg", blocks.filterIsInstance<FeedBlock.Image>().single().url)
        assertTrue(body.contains("https://example.com:8443/next?from=reader"))
        assertTrue(blocks.filterIsInstance<FeedBlock.Heading>().isNotEmpty())
    }

    @Test
    fun `malformed nesting and angle brackets in attributes do not truncate the article`() {
        val html = """
            <article><div title="a > b"><p>第一段的正文足够长，包含正常的中文标点和完整的信息。
            <p>第二段不应该因为标签未关闭或属性中存在大于号而丢失。</div></article>
        """.trimIndent()
        assertTrue(feedPlainText(assertNotNull(extractArticleBody(html))).contains("第二段"))
    }

    @Test
    fun `reading blocks preserve links with greater-than signs in quoted attributes`() {
        val html = """
            <article class="entry-content">
                <p>这段正文介绍比较运算符，并用下方的链接给出进一步说明。</p>
                <p><a title="x > y" href="/comparison?left=x&amp;right=y">比较示例</a></p>
            </article>
        """.trimIndent()
        val article = assertNotNull(extractReadableArticle(html, "https://example.com/post"))

        for (blocks in listOf(article.readingBlocks(), parseFeedHtml(html, article.url))) {
            val link = blocks.filterIsInstance<FeedBlock.Paragraph>()
                .flatMap { it.inlines }.filterIsInstance<FeedInline.Link>().single()
            assertEquals(FeedInline.Link("比较示例", "https://example.com/comparison?left=x&right=y"), link)
        }
    }

    @Test
    fun `hidden text and executable content are not retained`() {
        val html = """
            <article><p>可以正常阅读的文章正文，长度足以被识别成一篇简短的文章。</p>
            <p hidden>隐藏干扰</p><p style="display: none">样式干扰</p>
            <script>window.secret = '脚本干扰';</script>
            <form><p>登录干扰</p></form></article>
        """.trimIndent()
        val text = feedPlainText(assertNotNull(extractArticleBody(html)))
        listOf("隐藏干扰", "样式干扰", "脚本干扰", "登录干扰").forEach { assertFalse(text.contains(it)) }
    }

    @Test
    fun `link directories and script-only pages leave the feed summary intact`() {
        val links = (1..30).joinToString("") { "<p><a href='/post/$it'>点击查看第 $it 篇文章的详情</a></p>" }
        assertNull(extractArticleBody("<body>$links</body>", "https://example.com/"))
        assertNull(extractArticleBody("<body><script>loadArticle()</script></body>"))
    }

    @Test
    fun `unreasonably large pages are rejected before extraction`() {
        assertNull(extractArticleBody("x".repeat(2 * 1024 * 1024 + 1)))
        assertNull(extractArticleBody("<body>" + "<span>x</span>".repeat(20_001) + "</body>"))
    }
}
