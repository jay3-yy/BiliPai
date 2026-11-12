package com.android.purebilibili.feature.video.ui.components

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

class CommentMultipleMentionInsertTest {
    @Test
    fun `batch replaces active search once and preserves surrounding text`() {
        val (text, cursor) = insertCommentMentionsText("你好 @搜 后文", 5, listOf("甲", "乙"))
        assertEquals("你好 @甲 @乙  后文", text)
        assertEquals(TextRange(9), cursor)
    }

    @Test
    fun `batch inserts at caret without replacing an earlier completed mention`() {
        val (text, cursor) = insertCommentMentionsText("@之前 正文后文", 6, listOf("甲", "乙"))
        assertEquals("@之前 正文@甲 @乙 后文", text)
        assertEquals(TextRange(12), cursor)
    }

    @Test
    fun `empty selection preserves the active query`() {
        assertEquals("你好 @搜" to TextRange(5), insertCommentMentionsText("你好 @搜", 5, emptyList()))
    }
}
