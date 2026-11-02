package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.ProtoWire
import com.android.purebilibili.data.model.response.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MobileCommentPaginationTest {
    @Test
    fun `first main page sends independent sort mode without legacy cursor`() {
        for (mode in listOf(CommentGrpcRepository.MODE_TIME, CommentGrpcRepository.MODE_HOT)) {
            val fields = ProtoWire.parseFields(CommentGrpcRepository.buildMainListRequest(
                oid = 1L, type = 17, mode = mode, nextOffset = null,
            ))
            assertEquals(mode.toLong(), fields.single { it.number == 9 }.varint)
            assertFalse(fields.any { it.number == 3 || it.number == 10 })
        }
    }

    @Test
    fun `loaded comments with offset and zero numeric cursor remain valid for the next request`() {
        // 新分页响应只给 offset，旧 cursor.next 可以为 0；不能因此丢弃已经返回的正文。
        val data = CommentGrpcRepository.parseMainListReply(ProtoWire.message(
            ProtoWire.bytes(1, ProtoWire.message(ProtoWire.bool(4, false))),
            ProtoWire.bytes(2, ProtoWire.message(ProtoWire.int64(1, 123L))),
            ProtoWire.bytes(3, ProtoWire.message(ProtoWire.int64(16, 50L))),
            ProtoWire.bytes(20, ProtoWire.message(ProtoWire.string(1, "opaque-next-page"))),
        ))
        assertEquals(123L, data.replies.orEmpty().single().rpid)
        val next = requireNotNull(data.mainListContinuation)
        assertEquals(CommentMainListRoute.GRPC, next.route)
        assertEquals(0L, next.next)
        assertEquals("opaque-next-page", next.offset)
        requireAdvancingMobileCommentCursor(data, null)

        val fields = ProtoWire.parseFields(CommentGrpcRepository.buildMainListRequest(
            oid = 1L, type = 17, mode = 3, nextOffset = next.offset,
        ))
        assertEquals(3L, fields.single { it.number == 9 }.varint)
        assertFalse(fields.any { it.number == 3 })
        val pagination = ProtoWire.parseFields(fields.single { it.number == 10 }.bytes)
        assertEquals("opaque-next-page", ProtoWire.stringValue(pagination.single { it.number == 2 }))
    }

    @Test
    fun `REST numeric next comes from response rather than local page`() {
        val data = resolveMobileRestCommentPage(
            ReplyData(cursor = ReplyCursor(allCount = 100, next = 73)), page = 2, pageSize = 30,
        )
        assertEquals(73L, data.mainListContinuation?.next)
        assertEquals(mapOf("next" to "73"), CommentRepository.resolveCommentMainListPaginationParameters(
            page = 3, paginationOffset = null, cursorNext = data.mainListContinuation?.next,
        ))
        assertFalse(data.cursor.isEnd)
    }

    @Test
    fun `legacy pages stay on legacy route and finish using root count`() {
        val data = ReplyData(page = ReplyPage(num = 1, size = 20, count = 21, acount = 500),
            replies = listOf(ReplyItem(rpid = 1)))
        val first = resolveMobileRestCommentPage(data, 1, 20)
        assertEquals(CommentMainListRoute.REST_LEGACY, first.mainListContinuation?.route)
        assertFalse(first.cursor.isEnd)
        val last = resolveMobileRestCommentPage(data.copy(page = data.page.copy(num = 2)), 2, 20)
        assertTrue(last.cursor.isEnd)
        assertNull(last.mainListContinuation)
    }

    @Test
    fun `empty or duplicate batches can continue while cursor advances`() {
        val previous = CommentMainListContinuation(CommentMainListRoute.GRPC, offset = "previous")
        val data = ReplyData(mainListContinuation = previous.copy(offset = "next"))
        requireAdvancingMobileCommentCursor(data, previous)
        assertFailsWith<IllegalArgumentException> {
            requireAdvancingMobileCommentCursor(data.copy(mainListContinuation = previous), previous)
        }
        assertFailsWith<IllegalArgumentException> {
            requireAdvancingMobileCommentCursor(data.copy(mainListContinuation = null), previous)
        }
    }

    @Test
    fun `numeric cursor alone cannot continue grpc offset pagination`() {
        assertFailsWith<IllegalArgumentException> {
            requireAdvancingMobileCommentCursor(ReplyData(
                mainListContinuation = CommentMainListContinuation(CommentMainListRoute.GRPC, next = 9_000_000_000L),
            ), null)
        }
        requireAdvancingMobileCommentCursor(ReplyData(cursor = ReplyCursor(isEnd = true)), null)
    }

    @Test
    fun `legacy empty intermediate pages continue and repeated offset is rejected`() {
        val sparse = resolveMobileRestCommentPage(ReplyData(
            page = ReplyPage(num = 2, size = 20, count = 100),
        ), 2, 20)
        assertFalse(sparse.cursor.isEnd)
        assertEquals(3L, sparse.mainListContinuation?.next)
        val previous = CommentMainListContinuation(CommentMainListRoute.REST_MAIN, 2, "same-offset")
        assertFailsWith<IllegalArgumentException> {
            requireAdvancingMobileCommentCursor(ReplyData(
                mainListContinuation = previous.copy(next = 3),
            ), previous)
        }
    }

    @Test
    fun `location supplementation keeps main pagination and pinned ordering`() {
        val continuation = CommentMainListContinuation(CommentMainListRoute.GRPC, offset = "ranked-page-2")
        val top = ReplyItem(rpid = 9, like = 100)
        val data = ReplyData(
            replies = listOf(ReplyItem(rpid = 2)), top = ReplyTop(upper = top),
            mainListContinuation = continuation,
        )
        val merged = mergeCommentReplyLocations(data, listOf(
            top.copy(like = 0, replyControl = ReplyControl(location = "IP属地：北京")),
        ))
        assertEquals(continuation, merged.mainListContinuation)
        assertEquals(100, merged.collectTopReplies().single().like)
        assertEquals("IP属地：北京", merged.collectTopReplies().single().replyControl?.location)
        assertEquals(data.replies, merged.replies)
    }

    @Test
    fun `thread missing or repeating cursor is an error while empty advancing page continues`() {
        requireAdvancingSubReplyCursor(ReplyData(grpcNextOffset = "next"), "previous")
        requireAdvancingSubReplyCursor(ReplyData(cursor = ReplyCursor(isEnd = true)), "previous")
        assertFailsWith<IllegalArgumentException> {
            requireAdvancingSubReplyCursor(ReplyData(), "previous")
        }
        assertFailsWith<IllegalArgumentException> {
            requireAdvancingSubReplyCursor(ReplyData(grpcNextOffset = "previous"), "previous")
        }
    }

    @Test
    fun `real empty success ends but missing continuation with remaining count fails`() {
        assertTrue(resolveMobileRestCommentPage(ReplyData(), 1, 20).cursor.isEnd)
        assertFailsWith<IllegalArgumentException> {
            resolveMobileRestCommentPage(ReplyData(cursor = ReplyCursor(allCount = 10)), 1, 20)
        }
    }
}
