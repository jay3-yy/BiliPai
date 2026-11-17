package com.android.purebilibili.data.repository

import com.android.purebilibili.core.store.parseCommentKeywords
import com.android.purebilibili.data.model.response.*
import org.junit.Assert.*
import org.junit.Test

class CommentKeywordFilterTest {
    private fun reply(id: Long, text: String) = ReplyItem(rpid = id, content = ReplyContent(message = text))

    @Test
    fun manualUserBlocksApplyToRootAndNestedRepliesWithoutKeywords() {
        val blocked = reply(1, "普通评论").copy(member = ReplyMember(mid = "42"))
        val kept = reply(2, "正常内容").copy(replies = listOf(blocked), rcount = 8)
        val data = ReplyData(replies = listOf(blocked, kept), root = blocked, grpcNextOffset = "next")
        val result = filterCommentReplies(data, emptyList(), blockedMids = setOf(42))
        assertEquals(listOf(2L), result.replies!!.map { it.rpid })
        assertTrue(result.replies!!.single().replies!!.isEmpty())
        assertEquals(8, result.replies!!.single().rcount)
        assertNull(result.root)
        assertEquals("next", result.grpcNextOffset)
    }

    @Test
    fun keywordsAreLiteralCaseInsensitiveAndCleanEveryReplyPlacement() {
        val blocked = reply(1, "包含 SPOILER 与 [剧透]")
        val child = reply(3, "普通回复")
        val parent = reply(2, "正常评论").copy(replies = listOf(blocked, child), rcount = 20)
        val data = ReplyData(
            cursor = ReplyCursor(isEnd = false, allCount = 100), grpcNextOffset = "next",
            replies = listOf(blocked, parent), hots = listOf(blocked), topReplies = listOf(blocked),
            top = ReplyTop(admin = blocked, upper = parent, vote = blocked),
            upper = ReplyUpper(top = blocked), root = blocked,
        )
        val filtered = filterCommentReplies(data, listOf("spoiler", "[剧透]"))
        assertEquals(listOf(2L), filtered.replies!!.map { it.rpid })
        assertEquals(listOf(child), filtered.replies!!.single().replies)
        assertEquals(20, filtered.replies!!.single().rcount)
        assertTrue(filtered.hots!!.isEmpty())
        assertTrue(filtered.topReplies!!.isEmpty())
        assertNull(filtered.root)
        assertNull(filtered.top!!.admin)
        assertNull(filtered.top!!.vote)
        assertEquals(listOf(child), filtered.top!!.upper!!.replies)
        assertNull(filtered.upper!!.top)
        assertEquals(data.cursor, filtered.cursor)
        assertEquals(data.grpcNextOffset, filtered.grpcNextOffset)
        assertEquals(data.mainListContinuation, filtered.mainListContinuation)
        // 方括号是普通文本，不按正则解释。
        assertEquals(listOf(parent), filterCommentReplies(data.copy(replies = listOf(parent)), listOf("[其他]")).replies)
    }

    @Test
    fun emptyKeywordListDisablesKeywordFilteringWithoutDisablingGoodsFiltering() {
        val goods = reply(1, "https://gaoneng.bilibili.com/tetris/item")
        val normal = reply(2, "普通评论")
        val data = ReplyData(replies = listOf(goods, normal))
        assertSame(data, filterCommentReplies(data, emptyList()))
        assertEquals(listOf(normal), filterCommentReplies(data, emptyList(), blockGoods = true).replies)
        assertEquals(emptyList<ReplyItem>(), filterCommentReplies(data, listOf("普通"), blockGoods = true).replies)
        assertEquals(listOf("剧透", "Spoiler"), parseCommentKeywords(" 剧透 \r\n\nSpoiler\n剧透 "))
    }
}
