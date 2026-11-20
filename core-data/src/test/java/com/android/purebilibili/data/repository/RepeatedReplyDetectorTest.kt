package com.android.purebilibili.data.repository

import com.android.purebilibili.core.store.ContentFilterHistory
import com.android.purebilibili.core.store.ContentFilterReason
import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.core.store.FilteredContentKind
import com.android.purebilibili.data.model.response.ReplyContent
import com.android.purebilibili.data.model.response.ReplyItem
import com.android.purebilibili.data.model.response.ReplyMember
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatedReplyDetectorTest {
    private val template = "大家赶快看看这次官方公布的完整时间安排，我刚刚在直播间认真记下每一项活动的日期和具体细节。"

    @Test
    fun `finds a cross-account burst with minor edits across pages and threads`() {
        val root = reply(1, 11)
        val second = reply(2, 22, template.replace("刚刚", "刚才"))
        val child = reply(3, 33, template.replace("。", "！！！")).copy(root = 1L)
        val detector = RepeatedReplyDetector()

        assertTrue(detector.detect(sequenceOf(root, second), emptySet()).isEmpty())
        assertEquals(setOf(1L, 2L, 3L), detector.detect(sequenceOf(root.copy(replies = listOf(child)), second), emptySet()))
        val edited = child.copy(content = ReplyContent("有人知道这个视频里的背景音乐从哪里来吗"))
        assertTrue(detector.detect(sequenceOf(root.copy(replies = listOf(edited)), second), emptySet()).isEmpty())
    }

    @Test
    fun `leaves short chants, single-author repeats and bursts split across subjects alone`() {
        val detector = RepeatedReplyDetector()
        val replies = listOf(reply(1, 11), reply(2, 22), reply(3, 33))
        assertTrue(detector.detect(replies.asSequence().map { it.copy(content = ReplyContent("支持支持！")) }, emptySet()).isEmpty())
        assertTrue(detector.detect(replies.asSequence().map { it.copy(mid = 11) }, emptySet()).isEmpty())
        assertTrue(detector.detect(sequenceOf(replies[0], replies[1], replies[2].copy(oid = 2)), emptySet()).isEmpty())
        assertTrue(detector.detect(sequenceOf(replies[0], replies[1], replies[2].copy(ctime = 4000)), emptySet()).isEmpty())
        assertTrue(detector.detect(replies.asSequence(), setOf(33L)).isEmpty())
    }

    @Test
    fun `opt-in folds comments and records their originals once per reply`() {
        val replies = listOf(reply(7601, 11), reply(7602, 22), reply(7603, 33))
        val rules = ContentFilterSnapshot(keywordFilteringEnabled = true, filterRepeatedReplies = true)
        val filter = ReplyContentFilter(
            rules, replies.asSequence(), currentMid = 0L, foldDetectedReplies = true, recordHits = true,
        )
        repeat(2) {
            assertTrue(filter.filter(replies).all { it.repeatedReplyFolded && !it.aiBotFolded })
        }
        val history = ContentFilterHistory.snapshot(FilteredContentKind.COMMENT)
            .filter { it.id in setOf("7601", "7602", "7603") }
        assertEquals(3, history.size)
        assertTrue(history.all { it.text == template && it.reason == ContentFilterReason.REPEATED_REPLY })

        val disabled = ReplyContentFilter(
            rules.copy(filterRepeatedReplies = false), replies.asSequence(), currentMid = 0L, foldDetectedReplies = true,
        )
        assertFalse(disabled.filter(replies).any { it.repeatedReplyFolded })
    }

    private fun reply(id: Long, mid: Long, message: String = template) = ReplyItem(
        rpid = id, oid = 1L, mid = mid, ctime = 1000L,
        member = ReplyMember(mid = mid.toString(), uname = "观众$mid"),
        content = ReplyContent(message),
    )
}
