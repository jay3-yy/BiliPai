package com.android.purebilibili.data.repository

import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.data.model.response.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class GoodsReplyFilterTest {
    @Test
    fun `goods thread root remains visible but goods children and conversation anchors are filtered`() {
        val goods = ReplyContent(message = "https://gaoneng.bilibili.com/tetris/demo")
        val normal = ReplyItem(rpid = 3)
        val root = ReplyItem(rpid = 1, content = goods, replies = listOf(ReplyItem(rpid = 2, content = goods), normal))
        val filter = ReplyContentFilter(ContentFilterSnapshot(blockGoodsReplies = true), sequenceOf(root))

        assertEquals(root.copy(replies = listOf(normal)), filter.filter(root, keepThreadContext = true, isThreadRoot = true))
        assertNull(filter.filter(root, keepThreadContext = true))
    }

    @Test
    fun `goods only pages advance to visible replies without losing raw replies or the latest cursor`() = runTest {
        val goods = ReplyContent(message = "https://gaoneng.bilibili.com/tetris/demo")
        val firstAd = ReplyItem(rpid = 1, content = goods)
        val secondAd = ReplyItem(rpid = 2, content = goods)
        val normal = ReplyItem(rpid = 3)
        val second = CommentMainListContinuation(CommentMainListRoute.GRPC, offset = "second")
        val third = second.copy(offset = "third")
        val fourth = second.copy(offset = "fourth")
        val first = ReplyData(
            replies = listOf(firstAd), cursor = ReplyCursor(isEnd = false, allCount = 123),
            control = ReplyPageControl(rootInputText = "写评论"), mainListContinuation = second,
        )
        val requests = mutableListOf<CommentMainListContinuation>()
        val result = continueGoodsFilteredCommentPage(first, includeFeatured = true) { cursor ->
            requests += cursor
            Result.success(when (cursor) {
                second -> first.copy(replies = listOf(secondAd), mainListContinuation = third)
                third -> ReplyData(
                    replies = listOf(normal), cursor = ReplyCursor(isEnd = false),
                    mainListContinuation = fourth, grpcNextOffset = "fourth",
                )
                else -> error("Unexpected cursor: $cursor")
            })
        }.getOrThrow()

        assertEquals(listOf(second, third), requests)
        assertEquals(listOf(firstAd, secondAd, normal), result.replies)
        assertEquals(first.control, result.control)
        assertEquals(123, result.getAllCount())
        assertEquals(fourth, result.mainListContinuation)
        assertEquals("fourth", result.grpcNextOffset)
    }

    @Test
    fun `visible pinned reply and end of list both stop goods page skipping`() = runTest {
        val ad = ReplyItem(rpid = 1, content = ReplyContent(message = "https://gaoneng.bilibili.com/tetris/demo"))
        val withPin = ReplyData(
            replies = listOf(ad), topReplies = listOf(ReplyItem(rpid = 2)), cursor = ReplyCursor(isEnd = false),
            mainListContinuation = CommentMainListContinuation(CommentMainListRoute.GRPC, offset = "next"),
        )
        val end = withPin.copy(topReplies = null, cursor = ReplyCursor(isEnd = true), mainListContinuation = null)
        for (page in listOf(withPin, end)) {
            val result = continueGoodsFilteredCommentPage(page, includeFeatured = true) { error("No next request expected") }
            assertEquals(page, result.getOrThrow())
        }
    }

    @Test
    fun `grpc goods extra survives decoding into the shared reply model`() {
        val wire = com.android.purebilibili.core.network.grpc.ProtoWire
        val url = wire.message(wire.bytes(10, wire.message(wire.int64(5, 1L))))
        val entry = wire.message(wire.string(1, "https://b23.tv/product"), wire.bytes(2, url))
        val content = wire.message(wire.string(1, "商品"), wire.bytes(5, entry))
        val reply = wire.message(wire.int64(2, 1L), wire.bytes(12, content))
        val data = CommentGrpcRepository.parseMainListReply(wire.message(wire.bytes(2, reply)))
        assertTrue(isGoodsReply(data.replies!!.single()))
    }

    @Test
    fun `goods metadata and tetris links are filtered without matching ordinary shopping discussions`() {
        val ordinary = ReplyItem(content = ReplyContent(message = "这个商品的价格是否合理？"))
        assertFalse(isGoodsReply(ordinary))
        assertTrue(isGoodsReply(ordinary.copy(content = ReplyContent(message = "https://gaoneng.bilibili.com/tetris/demo"))))
        listOf("""{"goods_cm_control":1}""", """{"goods_item_id":"123"}""", """{"goods_prefetched_cache":"cached"}""").forEach { extra ->
            val url = Json.decodeFromString<ReplyContentUrl>("""{"extra":$extra}""")
            assertTrue(isGoodsReply(ReplyItem(content = ReplyContent(urls = mapOf("link" to url)))))
        }
        assertFalse(isGoodsReply(ReplyItem(content = ReplyContent(urls = mapOf(
            "link" to Json.decodeFromString<ReplyContentUrl>("""{"extra":{"goods_cm_control":0}}""")
        )))))
        assertTrue(isGoodsReply(ReplyItem(content = ReplyContent(urls = mapOf("link" to ReplyContentUrl(isGoods = true))))))
    }

    @Test
    fun `filter cleans pinned hot and nested replies while preserving counts and paging`() {
        val ad = ReplyItem(rpid = 1, content = ReplyContent(message = "https://gaoneng.bilibili.com/tetris/demo"))
        val normal = ReplyItem(rpid = 2, replies = listOf(ad, ReplyItem(rpid = 3)), rcount = 99)
        val data = ReplyData(
            cursor = ReplyCursor(isEnd = false, allCount = 123), grpcNextOffset = "next",
            replies = listOf(ad, normal), hots = listOf(ad), topReplies = listOf(ad),
            top = ReplyTop(admin = ad, upper = normal), upper = ReplyUpper(top = ad), root = normal,
        )
        val clean = filterGoodsReplies(data)
        assertEquals(listOf(2L), clean.replies!!.map { it.rpid })
        assertEquals(listOf(3L), clean.replies!!.single().replies!!.map { it.rpid })
        assertEquals(99, clean.replies!!.single().rcount)
        assertTrue(clean.hots!!.isEmpty())
        assertTrue(clean.topReplies!!.isEmpty())
        assertNull(clean.top!!.admin)
        assertNotNull(clean.top!!.upper)
        assertNull(clean.upper!!.top)
        assertEquals(data.cursor, clean.cursor)
        assertEquals(data.grpcNextOffset, clean.grpcNextOffset)
        assertEquals(data.mainListContinuation, clean.mainListContinuation)
    }
}
