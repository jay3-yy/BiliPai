package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.ProtoWire
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class CommentGrpcRepositoryTest {

    @Test
    fun `input disabled empty main list does not trigger empty payload fallback`() {
        val subject = ProtoWire.message(ProtoWire.bool(13, true))
        val data = CommentGrpcRepository.parseMainListReply(ProtoWire.message(
            ProtoWire.bytes(1, ProtoWire.message(ProtoWire.bool(4, true))),
            ProtoWire.bytes(3, subject),
        ))
        assertEquals(true, data.control?.inputDisable)
        assertEquals(true, data.cursor.isEnd)
        assertFalse(shouldFallbackHotCommentReadOnEmptySuccess(1, 3, 0, data))
        assertFalse(shouldFallbackCommentReadOnEmptyRenderableSuccess(0, data))
    }

    @Test
    fun `main list keeps curated title and empty guidance without triggering fallback`() {
        val subject = ProtoWire.message(
            ProtoWire.string(17, "精选评论"),
            ProtoWire.string(7, "评论经筛选后公开"),
            ProtoWire.string(21, "还没有精选评论"),
            ProtoWire.int64(16, 20L),
        )
        val data = CommentGrpcRepository.parseMainListReply(ProtoWire.message(ProtoWire.bytes(3, subject)))
        assertEquals("精选评论", data.control?.sectionNotice())
        assertEquals("还没有精选评论", data.control?.emptyMessage())
        assertFalse(shouldFallbackHotCommentReadOnEmptySuccess(1, 3, 0, data))
        assertFalse(shouldFallbackCommentReadOnEmptyRenderableSuccess(0, data))
    }

    @Test
    fun `parseMainListReply keeps the vote card above comments`() {
        val firstOption = ProtoWire.message(
            ProtoWire.int64(1, 0L),
            ProtoWire.string(2, "天下第一"),
            ProtoWire.int64(3, 90L),
        )
        val secondOption = ProtoWire.message(
            ProtoWire.int64(1, 1L),
            ProtoWire.string(2, "天下第二"),
            ProtoWire.int64(3, 10L),
        )
        val voteCard = ProtoWire.message(
            ProtoWire.int64(1, 123L),
            ProtoWire.string(2, "去年约基奇排第几？"),
            ProtoWire.int64(3, 100L),
            ProtoWire.bytes(4, firstOption),
            ProtoWire.bytes(4, secondOption),
            ProtoWire.int64(5, 0L),
        )
        val response = ProtoWire.message(ProtoWire.bytes(23, voteCard))

        val parsed = CommentGrpcRepository.parseMainListReply(response).voteCard

        assertEquals(123L, parsed?.voteId)
        assertEquals("去年约基奇排第几？", parsed?.title)
        assertEquals(100L, parsed?.count)
        assertEquals(listOf(0L, 1L), parsed?.options?.map { it.idx })
        assertEquals(listOf(90L, 10L), parsed?.options?.map { it.count })
        assertEquals(0L, parsed?.myVoteOption)
    }

    @Test
    fun `buildMainListRequest keeps required fields and pagination offset`() {
        val request = CommentGrpcRepository.buildMainListRequest(
            oid = 100L,
            type = 1,
            mode = CommentGrpcRepository.MODE_HOT,
            nextOffset = "offset-token"
        )
        val fields = ProtoWire.parseFields(request)

        assertEquals(100L, fields.first { it.number == 1 }.varint)
        assertEquals(1L, fields.first { it.number == 2 }.varint)
        assertEquals(3L, fields.first { it.number == 9 }.varint)

        val pagination = ProtoWire.parseFields(fields.first { it.number == 10 }.bytes)
        assertEquals(20L, pagination.first { it.number == 1 }.varint)
        assertEquals("offset-token", ProtoWire.stringValue(pagination.first { it.number == 2 }))
    }

    @Test
    fun `parseMainListReply maps minimal grpc response to reply data`() {
        val member = ProtoWire.message(
            ProtoWire.int64(1, 42L),
            ProtoWire.string(2, "测试用户"),
            ProtoWire.string(4, "https://example.com/avatar.jpg"),
            ProtoWire.int64(5, 6L),
            ProtoWire.int64(6, 1L),
            ProtoWire.int64(8, 1L),
            ProtoWire.string(11, "https://example.com/pendant.png"),
            ProtoWire.int32(32, 1)
        )
        val url = ProtoWire.message(
            ProtoWire.string(1, "视频标题"),
            ProtoWire.string(3, "https://example.com/icon.png"),
            ProtoWire.string(4, "bilibili://video/BV1testtest"),
            ProtoWire.string(13, "https://www.bilibili.com/video/BV1testtest")
        )
        val urlEntry = ProtoWire.message(
            ProtoWire.string(1, "https://b23.tv/demo"),
            ProtoWire.bytes(2, url)
        )
        val atEntry = ProtoWire.message(
            ProtoWire.string(1, "路人"),
            ProtoWire.int64(2, 12345L)
        )
        val content = ProtoWire.message(
            ProtoWire.string(1, "你好 @路人 https://b23.tv/demo"),
            ProtoWire.bytes(5, urlEntry),
            ProtoWire.bytes(7, atEntry)
        )
        val label = ProtoWire.message(
            ProtoWire.string(1, "UP主觉得很赞"),
            ProtoWire.string(4, "#FB7299")
        )
        val control = ProtoWire.message(
            ProtoWire.int64(1, 1L),
            ProtoWire.bool(3, true),
            ProtoWire.bool(12, true),
            ProtoWire.bytes(19, label),
            ProtoWire.string(25, "IP属地：上海")
        )
        val reply = ProtoWire.message(
            ProtoWire.int64(2, 777L),
            ProtoWire.int64(3, 100L),
            ProtoWire.int64(4, 1L),
            ProtoWire.int64(5, 42L),
            ProtoWire.int64(9, 9L),
            ProtoWire.int64(10, 1700000000L),
            ProtoWire.int64(11, 2L),
            ProtoWire.bytes(12, content),
            ProtoWire.bytes(13, member),
            ProtoWire.bytes(14, control)
        )
        val cursor = ProtoWire.message(
            ProtoWire.int64(1, 2L),
            ProtoWire.bool(4, false)
        )
        val subject = ProtoWire.message(
            ProtoWire.int64(1, 999L),
            ProtoWire.bool(11, true),
            ProtoWire.string(14, "根占位"),
            ProtoWire.string(15, "子占位"),
            ProtoWire.int64(16, 10L),
            ProtoWire.int32(26, 1)
        )
        val pagination = ProtoWire.message(ProtoWire.string(1, "next-token"))
        val response = ProtoWire.message(
            ProtoWire.bytes(1, cursor),
            ProtoWire.bytes(2, reply),
            ProtoWire.bytes(3, subject),
            ProtoWire.bytes(20, pagination)
        )

        val data = CommentGrpcRepository.parseMainListReply(response)
        val first = data.replies.orEmpty().first()

        assertEquals(10, data.getAllCount())
        assertFalse(data.cursor.isEnd)
        assertEquals("next-token", data.grpcNextOffset)
        assertEquals(999L, data.upper?.mid)
        assertEquals("根占位", data.control?.rootInputText)
        assertEquals(1, data.control?.uploadPictureIconState)
        assertEquals(777L, first.rpid)
        assertEquals(1, first.action)
        assertEquals("测试用户", first.member.uname)
        assertEquals(6, first.member.levelInfo.currentLevel)
        assertEquals(1, first.member.officialVerify.type)
        assertEquals(1, first.member.vip?.vipStatus)
        assertEquals("https://example.com/pendant.png", first.member.pendant?.image)
        assertEquals(true, first.replyControl?.isUpTop)
        assertEquals(true, first.replyControl?.upReply)
        assertEquals("IP属地：上海", first.replyControl?.location)
        assertEquals("UP主觉得很赞", first.cardLabels?.firstOrNull()?.textContent)
        assertEquals(12345L, first.content.atNameToMid["路人"])
        assertEquals("视频标题", first.content.urls["https://b23.tv/demo"]?.title)
    }

    @Test
    fun `parseDetailListReply maps root replies to reply data`() {
        val child = ProtoWire.message(
            ProtoWire.int64(2, 888L),
            ProtoWire.int64(3, 100L),
            ProtoWire.int64(5, 43L),
            ProtoWire.bytes(12, ProtoWire.message(ProtoWire.string(1, "二级回复"))),
            ProtoWire.bytes(13, ProtoWire.message(ProtoWire.int64(1, 43L), ProtoWire.string(2, "回复者")))
        )
        val root = ProtoWire.message(
            ProtoWire.bytes(1, child),
            ProtoWire.int64(2, 777L),
            ProtoWire.int64(11, 6L),
            ProtoWire.bytes(12, ProtoWire.message(ProtoWire.string(1, "根评论"))),
            ProtoWire.bytes(13, ProtoWire.message(ProtoWire.int64(1, 42L), ProtoWire.string(2, "楼主")))
        )
        val cursor = ProtoWire.message(ProtoWire.bool(4, false))
        val subject = ProtoWire.message(ProtoWire.int64(16, 6L))
        val pagination = ProtoWire.message(ProtoWire.string(1, "detail-next"))
        val response = ProtoWire.message(
            ProtoWire.bytes(1, cursor),
            ProtoWire.bytes(2, subject),
            ProtoWire.bytes(3, root),
            ProtoWire.bytes(8, pagination)
        )

        val data = CommentGrpcRepository.parseDetailListReply(response)

        assertEquals(6, data.getAllCount())
        assertEquals("detail-next", data.grpcNextOffset)
        assertEquals(1, data.replies.orEmpty().size)
        assertEquals(888L, data.replies.orEmpty().first().rpid)
        assertEquals("二级回复", data.replies.orEmpty().first().content.message)
    }

    @Test
    fun `buildDialogListRequest keeps dialog id and pagination offset`() {
        val request = CommentGrpcRepository.buildDialogListRequest(
            oid = 100L,
            type = 1,
            root = 777L,
            dialog = 999L,
            nextOffset = "dialog-offset"
        )

        val fields = ProtoWire.parseFields(request)
        assertEquals(100L, fields.first { it.number == 1 }.varint)
        assertEquals(777L, fields.first { it.number == 3 }.varint)
        assertEquals(999L, fields.first { it.number == 4 }.varint)

        val pagination = ProtoWire.parseFields(fields.first { it.number == 6 }.bytes)
        assertEquals("dialog-offset", ProtoWire.stringValue(pagination.first { it.number == 2 }))
    }

    @Test
    fun `parseDialogListReply maps replies directly`() {
        val reply = ProtoWire.message(
            ProtoWire.int64(2, 901L),
            ProtoWire.bytes(12, ProtoWire.message(ProtoWire.string(1, "对话回复"))),
            ProtoWire.bytes(13, ProtoWire.message(ProtoWire.int64(1, 45L), ProtoWire.string(2, "对话用户")))
        )
        val subject = ProtoWire.message(ProtoWire.int64(16, 1L))
        val response = ProtoWire.message(
            ProtoWire.bytes(2, subject),
            ProtoWire.bytes(3, reply),
            ProtoWire.bytes(5, ProtoWire.message(ProtoWire.string(1, "dialog-next")))
        )

        val data = CommentGrpcRepository.parseDialogListReply(response)

        assertEquals(1, data.getAllCount())
        assertEquals("dialog-next", data.grpcNextOffset)
        assertEquals(901L, data.replies.orEmpty().first().rpid)
    }

    @Test
    fun `parseMainListReply keeps cesu8 surrogate text from grpc comments`() {
        val content = ProtoWire.message(
            ProtoWire.bytes(
                1,
                byteArrayOf(
                    0xED.toByte(), 0xA0.toByte(), 0xBD.toByte(),
                    0xED.toByte(), 0xB4.toByte(), 0xB9.toByte()
                ) + "倒数第四集".toByteArray(Charsets.UTF_8)
            )
        )
        val reply = ProtoWire.message(
            ProtoWire.int64(2, 777L),
            ProtoWire.bytes(12, content),
            ProtoWire.bytes(13, ProtoWire.message(ProtoWire.int64(1, 42L), ProtoWire.string(2, "测试用户")))
        )
        val response = ProtoWire.message(ProtoWire.bytes(2, reply))

        val data = CommentGrpcRepository.parseMainListReply(response)

        assertEquals("🔹倒数第四集", data.replies.orEmpty().first().content.message)
    }
}
