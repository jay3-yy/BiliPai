package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ReplyRichTextGesturesTest {

    @Test
    fun `stripLinkAnnotations returns original instance when text has no links`() {
        val source = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.Blue, fontWeight = FontWeight.SemiBold)) {
                append("回复者: ")
            }
            append("普通评论正文")
        }

        val result = source.stripLinkAnnotations()

        assertSame(source, result)
    }

    @Test
    fun `stripLinkAnnotations removes links while preserving text, spanStyles, and paragraphStyles`() {
        val listener = LinkInteractionListener {}
        val source = buildAnnotatedString {
            withStyle(ParagraphStyle(textAlign = TextAlign.Start)) {
                append(
                    buildRichCommentAnnotatedString(
                        text = "回复 @目标用户：这是正文 https://example.com",
                        prefix = AnnotatedString("作者: "),
                        atNameToMid = mapOf("目标用户" to 99L),
                        color = Color.Black,
                        urlColor = Color.Blue,
                        linkListener = listener,
                    )
                )
            }
        }
        assertTrue(source.hasLinkAnnotations(0, source.length))

        val stripped = source.stripLinkAnnotations()

        assertFalse(stripped.hasLinkAnnotations(0, stripped.length))
        assertTrue(stripped.getLinkAnnotations(0, stripped.length).isEmpty())
        assertEquals(source.text, stripped.text)
        assertEquals(source.spanStyles, stripped.spanStyles)
        assertEquals(source.paragraphStyles, stripped.paragraphStyles)
    }

    @Test
    fun `stripLinkAnnotations preserves inlineContent string annotations even inside a link`() {
        val listener = LinkInteractionListener {}
        val source = buildAnnotatedString {
            appendInlineContent(id = "comment_up_badge", alternateText = "UP")
            append(" 回复 @B：正文 ")
            withLink(LinkAnnotation.Clickable(tag = "preview_like_1", linkInteractionListener = listener)) {
                appendInlineContent(id = "comment_preview_like", alternateText = "点赞")
            }
        }
        val expectedStringAnnotations = source.getStringAnnotations(0, source.length)
        assertTrue(expectedStringAnnotations.size >= 2)

        val stripped = source.stripLinkAnnotations()

        assertFalse(stripped.hasLinkAnnotations(0, stripped.length))
        assertEquals(source.text, stripped.text)
        assertEquals(expectedStringAnnotations, stripped.getStringAnnotations(0, stripped.length))
    }

    @Test
    fun `stripLinkAnnotations keeps base span style from TextLinkStyles when present`() {
        val listener = LinkInteractionListener {}
        val linkStyle = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)
        val source = buildAnnotatedString {
            append("prefix ")
            withLink(
                LinkAnnotation.Clickable(
                    tag = "URL:https://example.com",
                    styles = TextLinkStyles(style = linkStyle),
                    linkInteractionListener = listener,
                )
            ) {
                append("link")
            }
        }

        val stripped = source.stripLinkAnnotations()

        assertFalse(stripped.hasLinkAnnotations(0, stripped.length))
        assertEquals("prefix link", stripped.text)
        assertTrue(
            stripped.spanStyles.any {
                it.item == linkStyle && it.start == "prefix ".length && it.end == "prefix link".length
            }
        )
    }
}
