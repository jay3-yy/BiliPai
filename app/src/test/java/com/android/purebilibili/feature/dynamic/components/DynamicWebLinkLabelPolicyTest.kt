package com.android.purebilibili.feature.dynamic.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.RichTextNode
import kotlin.test.Test
import kotlin.test.assertEquals

class DynamicWebLinkLabelPolicyTest {
    private val rawUrl = "https://www.biligame.com/detail?id=108406"

    @Test
    fun plainSeedAndStructuredDetailUseTheSameLabel() {
        val seed = render(DynamicDesc(text = "前往下载：$rawUrl"))
        val detail = render(
            DynamicDesc(
                text = "前往下载：网页链接",
                rich_text_nodes = listOf(
                    RichTextNode(type = "TEXT", text = "前往下载："),
                    RichTextNode(type = "WEB", text = "网页链接", jump_url = rawUrl),
                ),
            ),
        )

        assertEquals("前往下载：网页链接", seed.text)
        assertEquals(seed.text, detail.text)
        assertEquals(listOf("URL:$rawUrl"), seed.linkTags())
        assertEquals(seed.linkTags(), detail.linkTags())
    }

    @Test
    fun structuredWebLabelKeepsUrlFromOriginalTextWhenJumpTargetIsMissing() {
        val rendered = render(
            DynamicDesc(
                text = "介绍页：网页链接",
                rich_text_nodes = listOf(
                    RichTextNode(type = "TEXT", text = "介绍页："),
                    RichTextNode(type = "WEB", text = "网页链接", orig_text = rawUrl),
                ),
            ),
        )

        assertEquals("介绍页：网页链接", rendered.text)
        assertEquals(listOf("URL:$rawUrl"), rendered.linkTags())
    }

    @Test
    fun rawWebNodeKeepsItsJumpTarget() {
        val jumpUrl = "https://t.bilibili.com/1015637114125025318"
        val rendered = render(
            DynamicDesc(
                text = rawUrl,
                rich_text_nodes = listOf(
                    RichTextNode(type = "RICH_TEXT_NODE_TYPE_WEB", text = rawUrl, jump_url = jumpUrl),
                ),
            ),
        )

        assertEquals("网页链接", rendered.text)
        assertEquals(listOf("URL:$jumpUrl"), rendered.linkTags())
    }

    @Test
    fun proseInTheSameNodeAsAUrlRemainsOutsideTheLinkRange() {
        val prefix = "具体可以看这篇："
        val suffix = "。下面是正文。"
        val nodeText = "$prefix$rawUrl$suffix"
        val desc = DynamicDesc(
            text = nodeText,
            rich_text_nodes = listOf(RichTextNode(type = "WEB", text = nodeText, jump_url = rawUrl)),
        )

        listOf(true to "网页链接", false to rawUrl).forEach { (collapse, label) ->
            val rendered = render(desc, collapseWebLinks = collapse)
            val link = rendered.getLinkAnnotations(0, rendered.length).single()
            assertEquals("$prefix$label$suffix", rendered.text)
            assertEquals(label, rendered.text.substring(link.start, link.end))
            assertEquals(listOf("URL:$rawUrl"), rendered.linkTags())
        }
    }

    @Test
    fun textOnlyOpusParagraphCollapsesLinksBeforeLayout() {
        val text = "说明\n前往下载：$rawUrl\n下一段"
        val rendered = render(
            DynamicDesc(text = text, rich_text_nodes = listOf(RichTextNode(type = "TEXT", text = text))),
        )

        assertEquals("说明\n前往下载：网页链接\n下一段", rendered.text)
        assertEquals(listOf("URL:$rawUrl"), rendered.linkTags())
    }

    @Test
    fun feedRenderingKeepsExistingUrlLabels() {
        val rendered = render(DynamicDesc(text = rawUrl), collapseWebLinks = false)

        assertEquals(rawUrl, rendered.text)
        assertEquals(listOf("URL:$rawUrl"), rendered.linkTags())
    }

    @Test
    fun titledWebAndVideoNodesKeepTheirLabels() {
        val rendered = render(
            DynamicDesc(
                text = "下载游戏观看视频",
                rich_text_nodes = listOf(
                    RichTextNode(type = "WEB", text = "下载游戏", jump_url = rawUrl),
                    RichTextNode(type = "BV", text = "观看视频", jump_url = "https://www.bilibili.com/video/BV1xx411c7mD/"),
                ),
            ),
        )

        assertEquals("下载游戏观看视频", rendered.text)
        assertEquals(2, rendered.linkTags().size)
    }

    @Test
    fun multipleLinksKeepSeparateTargetsAndSurroundingText() {
        val secondUrl = "http://example.com/page?a=1&b=2"
        val rendered = render(DynamicDesc(text = "第一个：$rawUrl，第二个：$secondUrl。"))

        assertEquals("第一个：网页链接，第二个：网页链接。", rendered.text)
        assertEquals(listOf("URL:$rawUrl", "URL:$secondUrl"), rendered.linkTags())
    }

    @Test
    fun topicMentionAndVoteNodesAreNotCollapsed() {
        val rendered = render(
            DynamicDesc(
                text = "#话题#@用户投票$rawUrl",
                rich_text_nodes = listOf(
                    RichTextNode(type = "TOPIC", text = "#话题#", rid = "123"),
                    RichTextNode(type = "AT", text = "@用户", rid = "456"),
                    RichTextNode(type = "VOTE", text = "投票", rid = "789"),
                    RichTextNode(type = "TEXT", text = rawUrl),
                ),
            ),
        )

        assertEquals("#话题#@用户投票网页链接", rendered.text)
        assertEquals(listOf("TOPIC:123", "USER:456", "VOTE:789", "URL:$rawUrl"), rendered.linkTags())
    }

    @Test
    fun emoteExpansionAlsoUsesCollapsedLinkLabels() {
        val rendered = buildDynamicRichTextAnnotatedString(
            desc = DynamicDesc(text = "$rawUrl [微笑] $rawUrl"),
            primaryColor = Color.Blue,
            textColor = Color.Black,
            extraEmoteUrlMap = mapOf("[微笑]" to "https://example.com/smile.png"),
            collapseWebLinks = true,
        )

        assertEquals("网页链接 [微笑] 网页链接", rendered.text)
        assertEquals(listOf("URL:$rawUrl", "URL:$rawUrl"), rendered.linkTags())
    }

    @Test
    fun nonWebSchemesAndCustomLabelsAreNotCollapsed() {
        listOf("ftp://example.com/file", "file:///tmp/file", "下载地址", "网页链接").forEach { label ->
            assertEquals(label, resolveDynamicWebLinkDisplayText(label, collapseWebLinks = true))
        }
        assertEquals("网页链接", resolveDynamicWebLinkDisplayText("//example.com/page", collapseWebLinks = true))
        assertEquals(" \n网页链接\n ", resolveDynamicWebLinkDisplayText(" \n$rawUrl\n ", collapseWebLinks = true))
    }

    private fun render(desc: DynamicDesc, collapseWebLinks: Boolean = true): AnnotatedString =
        buildDynamicRichTextAnnotatedString(
            desc = desc,
            primaryColor = Color.Blue,
            textColor = Color.Black,
            collapseWebLinks = collapseWebLinks,
        )

    private fun AnnotatedString.linkTags(): List<String> = getLinkAnnotations(0, length)
        .mapNotNull { annotation -> (annotation.item as? LinkAnnotation.Clickable)?.tag }
}
