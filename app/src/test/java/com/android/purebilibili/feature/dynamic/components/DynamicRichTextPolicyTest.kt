package com.android.purebilibili.feature.dynamic.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.RichTextNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DynamicRichTextPolicyTest {

private fun AnnotatedString.firstLinkTagMatching(predicate: (String) -> Boolean): String? =
    getLinkAnnotations(0, length)
        .mapNotNull { (it.item as? LinkAnnotation.Clickable)?.tag }
        .firstOrNull(predicate)

private fun AnnotatedString.firstLinkTag(prefix: String): String? =
    firstLinkTagMatching { it.startsWith(prefix) }
    private fun AnnotatedString.firstLinkRange(prefix: String): AnnotatedString.Range<String>? =
        getLinkAnnotations(0, length).firstNotNullOfOrNull { annotation ->
            val tag = (annotation.item as? LinkAnnotation.Clickable)?.tag
                ?: return@firstNotNullOfOrNull null
            if (!tag.startsWith(prefix)) return@firstNotNullOfOrNull null
            AnnotatedString.Range(tag.removePrefix(prefix), annotation.start, annotation.end)
        }


    @Test
    fun resolveDynamicDescForImages_hidesStandaloneImagePlaceholderWhenMediaExists() {
        val desc = DynamicDesc(
            text = "[图片]",
            rich_text_nodes = listOf(RichTextNode(type = "TEXT", text = "[图片]"))
        )

        val resolved = resolveDynamicDescForImages(desc, hasImages = true)

        assertFalse(shouldRenderDynamicRichText(resolved))
    }

    @Test
    fun resolveDynamicDescForImages_keepsImagePlaceholderWhenMediaMissing() {
        val desc = DynamicDesc(text = "【图片】")

        val resolved = resolveDynamicDescForImages(desc, hasImages = false)

        assertTrue(shouldRenderDynamicRichText(resolved))
        assertEquals("【图片】", resolved.text)
    }

    @Test
    fun resolveDynamicDescForImages_stripsInlinePlaceholderButKeepsRealText() {
        val desc = DynamicDesc(text = "正文 [图片]")

        val resolved = resolveDynamicDescForImages(desc, hasImages = true)

        assertTrue(shouldRenderDynamicRichText(resolved))
        assertEquals("正文", resolved.text)
    }

    @Test
    fun resolveDynamicDescForImages_hidesRepeatedPlaceholderLinesWhenMediaExists() {
        val desc = DynamicDesc(
            text = "第一行\n[图片]\n【图片】\n第二行",
            rich_text_nodes = listOf(
                RichTextNode(type = "TEXT", text = "第一行\n"),
                RichTextNode(type = "TEXT", text = "[图片]"),
                RichTextNode(type = "TEXT", text = "【图片】"),
                RichTextNode(type = "TEXT", text = "\n第二行")
            )
        )

        val resolved = resolveDynamicDescForImages(desc, hasImages = true)

        assertTrue(shouldRenderDynamicRichText(resolved))
        assertEquals("第一行\n第二行", resolved.text)
        assertEquals(
            resolved.text,
            buildDynamicRichTextAnnotatedString(resolved, primaryColor = Color.Blue, textColor = Color.Black).text,
        )
    }

    @Test
    fun resolveDynamicOpusSummaryDescForImages_stripsPlaceholderLinesBeforeRenderingSummary() {
        val resolved = resolveDynamicOpusSummaryDescForImages(
            text = "正文\n[图片]\n[图片]\n[图片]",
            richTextNodes = listOf(
                RichTextNode(type = "TEXT", text = "正文\n"),
                RichTextNode(type = "TEXT", text = "[图片]\n"),
                RichTextNode(type = "TEXT", text = "[图片]\n"),
                RichTextNode(type = "TEXT", text = "[图片]")
            ),
            hasImages = true
        )

        assertNotNull(resolved)
        assertEquals("正文", resolved.text)
        val richNodeText = resolved.rich_text_nodes.joinToString(separator = "") { it.text }
        assertEquals("正文", richNodeText)
        assertFalse(richNodeText.contains("[图片]"))
        assertTrue(shouldRenderDynamicRichText(resolved))
    }

    @Test
    fun normalizeDynamicBodyText_trimsEdgeNewlinesOnly() {
        assertEquals("正文", normalizeDynamicBodyText("正文\n\n"))
        assertEquals("正文", normalizeDynamicBodyText("\n正文"))
        assertEquals("第一行\n\n第二行", normalizeDynamicBodyText("第一行\n\n第二行"))
    }

    @Test
    fun resolveDynamicDescForImages_trimsTrailingNewlinesEvenWithoutPlaceholders() {
        val resolved = resolveDynamicDescForImages(
            desc = DynamicDesc(
                text = "「芙蓉」篇\n\n",
                rich_text_nodes = listOf(
                    RichTextNode(type = "TEXT", text = "「芙蓉」篇\n"),
                    RichTextNode(type = "TEXT", text = "\n"),
                )
            ),
            hasImages = true
        )

        assertEquals("「芙蓉」篇", resolved.text)
        assertEquals(
            "「芙蓉」篇",
            resolved.rich_text_nodes.joinToString(separator = "") { it.text }.trimEnd()
        )
    }

    @Test
    fun buildDynamicRichTextAnnotatedString_prefersNodeJumpUrlForClickableLink() {
        val desc = DynamicDesc(
            text = "https://b23.tv/cm-yaoyue-0-3jgPM iPhone16系列至高直降千元起",
            rich_text_nodes = listOf(
                RichTextNode(
                    type = "WEB",
                    text = "https://b23.tv/cm-yaoyue-0-3jgPM",
                    jump_url = "https://t.bilibili.com/1015637114125025318"
                ),
                RichTextNode(
                    type = "TEXT",
                    text = " iPhone16系列至高直降千元起"
                )
            )
        )

        val annotated = buildDynamicRichTextAnnotatedString(
            desc = desc,
            primaryColor = Color.Blue,
            textColor = Color.Black
        )

        val annotation = annotated.firstLinkTag(DYNAMIC_RICH_TEXT_LINK_URL_PREFIX)?.removePrefix(DYNAMIC_RICH_TEXT_LINK_URL_PREFIX)

        assertNotNull(annotation)
        assertEquals("https://t.bilibili.com/1015637114125025318", annotation)
        assertEquals("https://b23.tv/cm-yaoyue-0-3jgPM iPhone16系列至高直降千元起", annotated.text)
    }

    @Test
    fun buildDynamicRichTextAnnotatedString_marksStructuredBvTitleAsClickableLink() {
        val annotated = buildDynamicRichTextAnnotatedString(
            desc = DynamicDesc(
                rich_text_nodes = listOf(
                    RichTextNode(
                        type = "RICH_TEXT_NODE_TYPE_BV",
                        text = "视频标题",
                        jump_url = "https://www.bilibili.com/video/BV1xx411c7mD/",
                    )
                )
            ),
            primaryColor = Color.Blue,
            textColor = Color.Black,
        )

        val annotation = annotated.firstLinkTag(DYNAMIC_RICH_TEXT_LINK_URL_PREFIX)?.removePrefix(DYNAMIC_RICH_TEXT_LINK_URL_PREFIX)

        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD/", annotation)
        assertEquals("视频标题", annotated.text)
        assertEquals(Color.Blue, annotated.spanStyles.single().item.color)
    }

    @Test
    fun buildDynamicRichTextAnnotatedString_usesVoteRidForClickableVote() {
        val annotated = buildDynamicRichTextAnnotatedString(
            desc = DynamicDesc(
                rich_text_nodes = listOf(
                    RichTextNode(
                        type = "RICH_TEXT_NODE_TYPE_VOTE",
                        text = "选择你支持的选项",
                        rid = "3925886",
                    )
                )
            ),
            primaryColor = Color.Blue,
            textColor = Color.Black,
        )

        val annotation = annotated.firstLinkTag(DYNAMIC_RICH_TEXT_LINK_VOTE_PREFIX)?.removePrefix(DYNAMIC_RICH_TEXT_LINK_VOTE_PREFIX)

        assertEquals("3925886", annotation)
        assertEquals(Color.Blue, annotated.spanStyles.single().item.color)
    }

    @Test
    fun buildDynamicRichTextAnnotatedString_detectsPlainTextUrlWhenNodesMissing() {
        val desc = DynamicDesc(
            text = "https://b23.tv/cm-yaoyue-0-3jgPM iPhone16系列至高直降千元起"
        )

        val annotated = buildDynamicRichTextAnnotatedString(
            desc = desc,
            primaryColor = Color.Blue,
            textColor = Color.Black
        )

        val annotation = annotated.firstLinkTag(DYNAMIC_RICH_TEXT_LINK_URL_PREFIX)?.removePrefix(DYNAMIC_RICH_TEXT_LINK_URL_PREFIX)

        assertNotNull(annotation)
        assertEquals("https://b23.tv/cm-yaoyue-0-3jgPM", annotation)
        val linkRange = annotated.getLinkAnnotations(0, annotated.length).single()
        assertEquals(0, linkRange.start)
        assertEquals("https://b23.tv/cm-yaoyue-0-3jgPM".length, linkRange.end)
    }

    @Test
    fun buildDynamicRichTextAnnotatedString_usesFullTextWhenNodesAreTruncated() {
        val desc = DynamicDesc(
            text = "第一段\n第二段\n第三段",
            rich_text_nodes = listOf(RichTextNode(type = "TEXT", text = "第一段\n"))
        )

        val annotated = buildDynamicRichTextAnnotatedString(
            desc = desc,
            primaryColor = Color.Blue,
            textColor = Color.Black
        )

        assertFalse(shouldUseDynamicRichTextNodes(desc))
        assertEquals(desc.text, annotated.text)
    }

    @Test
    fun resolveDynamicRichTextOpenMode_usesInAppForShortLink() {
        val mode = resolveDynamicRichTextOpenMode(
            "https://b23.tv/cm-yaoyue-0-3jgPM"
        )

        assertEquals(DynamicRichTextOpenMode.IN_APP, mode)
    }

    @Test
    fun resolveDynamicRichTextOpenMode_usesInAppForBilibiliWebLink() {
        val mode = resolveDynamicRichTextOpenMode(
            "https://www.bilibili.com/opus/1015637114125025318"
        )

        assertEquals(DynamicRichTextOpenMode.IN_APP, mode)
    }

    @Test
    fun resolveDynamicRichTextOpenMode_usesInAppForDirectDynamicLink() {
        val mode = resolveDynamicRichTextOpenMode(
            "https://t.bilibili.com/1015637114125025318"
        )

        assertEquals(DynamicRichTextOpenMode.IN_APP, mode)
    }

    @Test
    fun resolveDynamicRichTextOpenMode_usesExternalForNonBilibiliLink() {
        val mode = resolveDynamicRichTextOpenMode(
            "https://example.com/demo"
        )

        assertEquals(DynamicRichTextOpenMode.EXTERNAL, mode)
    }

    @Test
    fun resolveDynamicRichTextOpenMode_returnsNullForBlankInput() {
        val mode = resolveDynamicRichTextOpenMode("   ")

        assertNull(mode)
    }

    @Test
    fun buildDynamicRichTextAnnotatedString_marksAtMentionWithUserAnnotation() {
        val desc = DynamicDesc(
            text = "@影视飓风 你好",
            rich_text_nodes = listOf(
                RichTextNode(
                    type = "RICH_TEXT_NODE_TYPE_AT",
                    text = "@影视飓风",
                    rid = "946974"
                ),
                RichTextNode(type = "TEXT", text = " 你好")
            )
        )

        val annotated = buildDynamicRichTextAnnotatedString(
            desc = desc,
            primaryColor = Color.Blue,
            textColor = Color.Black
        )

        val annotation = annotated.firstLinkRange(DYNAMIC_RICH_TEXT_LINK_USER_PREFIX)

        assertNotNull(annotation)
        assertEquals("946974", annotation.item)
        assertEquals(0, annotation.start)
        assertEquals("@影视飓风".length, annotation.end)
    }

    @Test
    fun buildDynamicRichTextAnnotatedString_marksTopicWithNativeTopicAnnotation() {
        val annotated = buildDynamicRichTextAnnotatedString(
            desc = DynamicDesc(
                text = "#新机来了!# 后续正文",
                rich_text_nodes = listOf(
                    RichTextNode(
                        type = "RICH_TEXT_NODE_TYPE_TOPIC",
                        orig_text = "#新机来了!#",
                        rid = "1314000",
                    ),
                    RichTextNode(type = "TEXT", text = " 后续正文"),
                ),
            ),
            primaryColor = Color.Blue,
            textColor = Color.Black,
        )

        val annotation = assertNotNull(annotated.firstLinkRange(DYNAMIC_RICH_TEXT_LINK_TOPIC_ID_PREFIX))

        assertEquals("1314000", annotation.item)
        assertEquals("#新机来了!#", annotated.text.substring(annotation.start, annotation.end))
    }

    @Test
    fun resolveDynamicRichTextTopicId_fallsBackToTopicJumpUrl() {
        assertEquals(
            1028161L,
            resolveDynamicRichTextTopicId(
                RichTextNode(
                    type = "TOPIC",
                    text = "#话题#",
                    jump_url = "bilibili://m.bilibili.com/topic-detail?topic_id=1028161",
                )
            )
        )
    }

    @Test
    fun buildDynamicRichTextAnnotatedString_keepsMentionWhenPreviewNodesAreTruncated() {
        val annotated = buildDynamicRichTextAnnotatedString(
            desc = DynamicDesc(
                text = "前面的完整正文 @影视飓风 后续内容",
                // The feed may omit surrounding TEXT nodes while retaining the
                // actionable mention metadata.
                rich_text_nodes = listOf(
                    RichTextNode(
                        type = "RICH_TEXT_NODE_TYPE_AT",
                        orig_text = "@影视飓风 ",
                        rid = "946974",
                    ),
                ),
            ),
            primaryColor = Color.Blue,
            textColor = Color.Black,
        )

        val annotation = assertNotNull(annotated.firstLinkRange(DYNAMIC_RICH_TEXT_LINK_USER_PREFIX))

        assertEquals("前面的完整正文 @影视飓风 后续内容", annotated.text)
        assertEquals("946974", annotation.item)
        assertEquals("@影视飓风", annotated.text.substring(annotation.start, annotation.end).trim())
    }

    @Test
    fun buildDynamicRichTextAnnotatedString_skipsUserAnnotationWhenAtRidMissing() {
        val desc = DynamicDesc(
            rich_text_nodes = listOf(
                RichTextNode(type = "AT", text = "@匿名用户")
            )
        )

        val annotated = buildDynamicRichTextAnnotatedString(
            desc = desc,
            primaryColor = Color.Blue,
            textColor = Color.Black
        )

        assertTrue(
            annotated
                .firstLinkTag(DYNAMIC_RICH_TEXT_LINK_USER_PREFIX)
                .let { it == null }
        )
        assertEquals("@匿名用户", annotated.text)
    }

    @Test
    fun resolveDynamicRichTextUserMid_parsesPositiveRid() {
        assertEquals(
            946974L,
            resolveDynamicRichTextUserMid(RichTextNode(type = "AT", text = "@UP", rid = "946974"))
        )
        assertNull(resolveDynamicRichTextUserMid(RichTextNode(type = "AT", text = "@UP", rid = "0")))
        assertNull(resolveDynamicRichTextUserMid(RichTextNode(type = "AT", text = "@UP")))
    }

    @Test
    fun resolveDynamicRichTextUserMid_fallsBackToSpaceJumpUrl() {
        assertEquals(
            267776898L,
            resolveDynamicRichTextUserMid(
                RichTextNode(
                    type = "AT",
                    text = "@奇妙的摸鱼禁止",
                    rid = "",
                    jump_url = "//space.bilibili.com/267776898"
                )
            )
        )
    }

    @Test
    fun buildDynamicRichText_rendersEmojiNodesAsInlineContent() {
        val desc = DynamicDesc(
            text = "新年快乐[豹富][豹富]",
            rich_text_nodes = listOf(
                RichTextNode(type = "RICH_TEXT_NODE_TYPE_TEXT", text = "新年快乐"),
                RichTextNode(
                    type = "RICH_TEXT_NODE_TYPE_EMOJI",
                    text = "[豹富]",
                    emoji = com.android.purebilibili.data.model.response.EmojiInfo(
                        icon_url = "https://i0.hdslb.com/bfs/emote/baofu.png",
                        text = "[豹富]"
                    )
                ),
                RichTextNode(
                    type = "RICH_TEXT_NODE_TYPE_EMOJI",
                    text = "[豹富]",
                    emoji = com.android.purebilibili.data.model.response.EmojiInfo(
                        icon_url = "https://i0.hdslb.com/bfs/emote/baofu.png",
                        text = "[豹富]"
                    )
                )
            )
        )

        val result = buildDynamicRichText(
            desc = desc,
            primaryColor = Color.Blue,
            textColor = Color.Black
        )

        assertTrue(shouldUseDynamicRichTextNodes(desc))
        assertEquals(
            "https://i0.hdslb.com/bfs/emote/baofu.png",
            result.emojiUrlById["[豹富]"]
        )
        val emojiRanges = result.annotatedString.getStringAnnotations(0, result.annotatedString.length)
            .filter { it.item in result.emojiUrlById }
        assertEquals(2, emojiRanges.size)
        assertEquals(
            listOf("[豹富]", "[豹富]"),
            emojiRanges.map { result.annotatedString.text.substring(it.start, it.end) },
        )
    }

    @Test
    fun buildDynamicRichText_expandsShortcodesInPlainTextWithExtraEmoteMap() {
        val desc = DynamicDesc(text = "大家好[tv_doge][tv_doge]")
        val result = buildDynamicRichText(
            desc = desc,
            primaryColor = Color.Blue,
            textColor = Color.Black,
            extraEmoteUrlMap = mapOf(
                "[tv_doge]" to "https://i0.hdslb.com/bfs/emote/tv_doge.png"
            )
        )

        assertEquals(
            "https://i0.hdslb.com/bfs/emote/tv_doge.png",
            result.emojiUrlById["[tv_doge]"]
        )
        val emojiRanges = result.annotatedString.getStringAnnotations(0, result.annotatedString.length)
            .filter { it.item == "[tv_doge]" }
        assertEquals(listOf(3 to 12, 12 to 21), emojiRanges.map { it.start to it.end })
    }

    @Test
    fun buildDynamicRichText_expandsDecoratedDogeShortcodeFromCatalog() {
        val token = "[doge_金饰]"
        val result = buildDynamicRichText(
            desc = DynamicDesc(text = "媒体会结束了$token"),
            primaryColor = Color.Blue,
            textColor = Color.Black,
            extraEmoteUrlMap = mapOf(
                token to "https://i0.hdslb.com/bfs/emote/doge_gold.png",
            ),
        )

        assertEquals(
            "https://i0.hdslb.com/bfs/emote/doge_gold.png",
            result.emojiUrlById[token],
        )
        val emojiRanges = result.annotatedString.getStringAnnotations(0, result.annotatedString.length)
            .filter { it.item == token }
        assertEquals(listOf(6 to 15), emojiRanges.map { it.start to it.end })
    }

    @Test
    fun buildDynamicRichText_keepsCompleteBodyWhenEmojiNodesAreShorter() {
        val desc = DynamicDesc(
            text = "第一段\n第二段\n第三段[豹富]",
            rich_text_nodes = listOf(
                RichTextNode(type = "TEXT", text = "第一段"),
                RichTextNode(
                    type = "EMOJI",
                    text = "[豹富]",
                    emoji = com.android.purebilibili.data.model.response.EmojiInfo(
                        icon_url = "https://i0.hdslb.com/bfs/emote/baofu.png",
                        text = "[豹富]"
                    )
                )
            )
        )

        val result = buildDynamicRichText(
            desc = desc,
            primaryColor = Color.Blue,
            textColor = Color.Black,
        )

        assertFalse(shouldUseDynamicRichTextNodes(desc))
        assertEquals(desc.text, result.annotatedString.text)
        val emojiRanges = result.annotatedString.getStringAnnotations(0, result.annotatedString.length)
            .filter { it.item == "[豹富]" }
        assertEquals(listOf(11 to 15), emojiRanges.map { it.start to it.end })
    }

    @Test
    fun resolveDynamicOpusTextBlockRichDesc_preservesFullBlockAndEmojiMetadata() {
        val preferred = DynamicDesc(
            text = "摘要[豹富]",
            rich_text_nodes = listOf(
                RichTextNode(type = "TEXT", text = "摘要"),
                RichTextNode(
                    type = "EMOJI",
                    text = "[豹富]",
                    emoji = com.android.purebilibili.data.model.response.EmojiInfo(
                        icon_url = "https://i0.hdslb.com/bfs/emote/baofu.png",
                        text = "[豹富]",
                    ),
                ),
            ),
        )

        val resolved = resolveDynamicOpusTextBlockRichDesc(
            blockText = "完整正文第一行\n完整正文第二行[豹富]",
            preferredDesc = preferred,
        )

        assertEquals("完整正文第一行\n完整正文第二行[豹富]", resolved?.text)
        assertEquals(preferred.rich_text_nodes, resolved?.rich_text_nodes)
    }

    @Test
    fun resolveDynamicOpusTextBlockRichDesc_keepsPlainShortcodesForCatalogExpansion() {
        val preferred = DynamicDesc(
            text = "摘要",
            rich_text_nodes = listOf(RichTextNode(type = "TEXT", text = "摘要")),
        )

        val resolved = resolveDynamicOpusTextBlockRichDesc(
            blockText = "正文[UPOWER_3546635395139954_舔舔]",
            preferredDesc = preferred,
        )

        assertEquals("正文[UPOWER_3546635395139954_舔舔]", resolved?.text)
        assertEquals(preferred.rich_text_nodes, resolved?.rich_text_nodes)
    }

    @Test
    fun resolveDynamicOpusTextBlockRichDesc_prefersBodyMentionMetadata() {
        val bodyNodes = listOf(
            RichTextNode(type = "RICH_TEXT_NODE_TYPE_TEXT", text = "谢谢"),
            RichTextNode(type = "RICH_TEXT_NODE_TYPE_AT", text = "@叽米", rid = "12345"),
        )

        val resolved = resolveDynamicOpusTextBlockRichDesc(
            blockText = "谢谢@叽米",
            preferredDesc = DynamicDesc(text = "预览摘要"),
            blockRichTextNodes = bodyNodes,
        )

        val richDesc = assertNotNull(resolved)
        assertEquals(12345L, resolveDynamicRichTextUserMid(richDesc.rich_text_nodes.last()))
        val rendered = buildDynamicRichTextAnnotatedString(richDesc, primaryColor = Color.Blue, textColor = Color.Black)
        assertEquals("谢谢@叽米", rendered.text)
        val mention = assertNotNull(rendered.firstLinkRange(DYNAMIC_RICH_TEXT_LINK_USER_PREFIX))
        assertEquals("12345", mention.item)
        assertEquals(2 to 5, mention.start to mention.end)
    }

    @Test
    fun resolveDynamicOpusTextBlockRichDesc_mergesPreviewMentionIntoLongerBody() {
        val preferred = DynamicDesc(
            text = "谢谢@叽米",
            rich_text_nodes = listOf(
                RichTextNode(type = "RICH_TEXT_NODE_TYPE_TEXT", text = "谢谢"),
                RichTextNode(type = "RICH_TEXT_NODE_TYPE_AT", text = "@叽米", rid = "12345"),
            ),
        )

        val resolved = resolveDynamicOpusTextBlockRichDesc(
            blockText = "谢谢@叽米，后续正文继续。",
            preferredDesc = preferred,
            blockRichTextNodes = listOf(
                RichTextNode(
                    type = "RICH_TEXT_NODE_TYPE_TEXT",
                    text = "谢谢@叽米，后续正文继续。",
                )
            ),
        )

        assertEquals("谢谢@叽米，后续正文继续。", resolved?.text)
        val mention = resolved?.rich_text_nodes?.single { it.type.endsWith("AT") }
        assertEquals("@叽米", mention?.text)
        assertEquals(12345L, mention?.let(::resolveDynamicRichTextUserMid))
        val resolvedDesc = resolved ?: error("expected rich body description")
        val annotation = buildDynamicRichTextAnnotatedString(
            desc = resolvedDesc,
            primaryColor = Color.Blue,
            textColor = Color.Black,
        ).firstLinkTag(DYNAMIC_RICH_TEXT_LINK_USER_PREFIX)
            ?.removePrefix(DYNAMIC_RICH_TEXT_LINK_USER_PREFIX)
        assertEquals("12345", annotation)
    }

    @Test
    fun resolveDynamicOpusTextBlockRichDesc_matchesMentionFromOrigTextWhenTextIsBlank() {
        val resolved = resolveDynamicOpusTextBlockRichDesc(
            blockText = "前文 @影视飓风 后文",
            preferredDesc = DynamicDesc(text = "摘要"),
            blockRichTextNodes = listOf(
                RichTextNode(
                    type = "RICH_TEXT_NODE_TYPE_AT",
                    orig_text = "@影视飓风 ",
                    rid = "946974",
                ),
            ),
        ) ?: error("expected rich body description")

        val richAnnotated = buildDynamicRichTextAnnotatedString(
            desc = resolved,
            primaryColor = Color.Blue,
            textColor = Color.Black,
        )
        val annotation = richAnnotated
            .firstLinkTag(DYNAMIC_RICH_TEXT_LINK_USER_PREFIX)
            ?.removePrefix(DYNAMIC_RICH_TEXT_LINK_USER_PREFIX)

        assertEquals("前文 @影视飓风 后文", richAnnotated.text)
        assertEquals("946974", annotation)
    }

    @Test
    fun buildDynamicRichText_usesMergedSummaryEmojiMetadataForFullBodyShortcode() {
        val shortcode = "[UPOWER_3546635395139954_舔舔]"
        val iconUrl = "https://i0.hdslb.com/bfs/garb/upower.png"
        val result = buildDynamicRichText(
            desc = DynamicDesc(
                text = "完整正文$shortcode",
                rich_text_nodes = listOf(
                    RichTextNode(type = "TEXT", text = "完整正文$shortcode"),
                    RichTextNode(
                        type = "EMOJI",
                        text = shortcode,
                        emoji = com.android.purebilibili.data.model.response.EmojiInfo(
                            icon_url = iconUrl,
                            text = shortcode,
                        ),
                    ),
                ),
            ),
            primaryColor = Color.Magenta,
            textColor = Color.White,
        )

        assertEquals(iconUrl, result.emojiUrlById[shortcode])
        assertEquals("完整正文$shortcode", result.annotatedString.text)
    }

    @Test
    fun resolvePreferredDynamicDesc_prefersSideWithRenderableEmoji() {
        val plain = DynamicDesc(text = "画完芽衣，大家新年快乐[豹富][豹富]")
        val withEmoji = DynamicDesc(
            text = "画完芽衣，大家新年快乐[豹富][豹富]",
            rich_text_nodes = listOf(
                RichTextNode(type = "TEXT", text = "画完芽衣，大家新年快乐"),
                RichTextNode(
                    type = "EMOJI",
                    text = "[豹富]",
                    emoji = com.android.purebilibili.data.model.response.EmojiInfo(
                        icon_url = "https://i0.hdslb.com/bfs/emote/baofu.png",
                        text = "[豹富]"
                    )
                )
            )
        )

        val preferred = resolvePreferredDynamicDesc(primary = plain, fallback = withEmoji)
        assertEquals(withEmoji, preferred)
    }

    @Test
    fun resolveDynamicEmojiIconUrl_fallsBackToWebpAndHttps() {
        val url = resolveDynamicEmojiIconUrl(
            com.android.purebilibili.data.model.response.EmojiInfo(
                icon_url = "",
                webp_url = "http://i0.hdslb.com/bfs/emote/a.webp",
                text = "[x]"
            )
        )
        assertEquals("https://i0.hdslb.com/bfs/emote/a.webp", url)
    }

    @Test
    fun resolveDynamicOpusTextBlockRichDesc_enrichesEmojiNodeFromPreferredDescWhenBlockHasAtNode() {
        val preferredEmoji = RichTextNode(
            type = "EMOJI",
            text = "[豹富]",
            emoji = com.android.purebilibili.data.model.response.EmojiInfo(
                icon_url = "https://i0.hdslb.com/bfs/emote/baofu.png",
                text = "[豹富]"
            )
        )
        val preferredDesc = DynamicDesc(
            text = "感谢@测试 恭喜[豹富]",
            rich_text_nodes = listOf(
                RichTextNode(type = "AT", text = "@测试", rid = "12345"),
                preferredEmoji
            )
        )
        val blockNodes = listOf(
            RichTextNode(type = "TEXT", text = "感谢"),
            RichTextNode(type = "AT", text = "@测试", rid = "12345"),
            RichTextNode(type = "TEXT", text = " 恭喜[豹富]")
        )

        val resolved = resolveDynamicOpusTextBlockRichDesc(
            blockText = "感谢@测试 恭喜[豹富]",
            preferredDesc = preferredDesc,
            blockRichTextNodes = blockNodes
        )

        assertNotNull(resolved)
        val emojiNode = resolved.rich_text_nodes.firstOrNull { it.type.contains("EMOJI", ignoreCase = true) }
        assertNotNull(emojiNode)
        assertEquals("https://i0.hdslb.com/bfs/emote/baofu.png", emojiNode.emoji?.icon_url)
    }

    @Test
    fun collectDynamicEmojiUrlMap_extractsUrlsFromNodesWithEmoji() {
        val node = RichTextNode(
            type = "EMOJI",
            text = "[豹富]",
            orig_text = "[豹富]",
            emoji = com.android.purebilibili.data.model.response.EmojiInfo(
                icon_url = "https://i0.hdslb.com/bfs/emote/baofu.png",
                text = "[豹富]"
            )
        )
        val map = collectDynamicEmojiUrlMap(listOf(node))
        assertEquals("https://i0.hdslb.com/bfs/emote/baofu.png", map["[豹富]"])
    }
}

