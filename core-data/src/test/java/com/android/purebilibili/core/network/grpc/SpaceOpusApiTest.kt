package com.android.purebilibili.core.network.grpc

import com.android.purebilibili.data.model.response.displayImageUrls
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpaceOpusApiTest {
    @Test
    fun requestCarriesHostFilterAndTheServerCursor() {
        val request = ProtoWire.parseFields(buildSpaceOpusRequest(42L, "next-2", "article"))
        assertEquals(42L, request.single { it.number == 1 }.varint)
        assertEquals("article", ProtoWire.stringValue(request.single { it.number == 4 }))
        val pagination = ProtoWire.parseFields(request.single { it.number == 3 }.bytes)
        assertEquals(20L, pagination.single { it.number == 1 }.varint)
        assertEquals("next-2", ProtoWire.stringValue(pagination.single { it.number == 2 }))
    }

    @Test
    fun replyRetainsOpusIdentityCollectionNavigationAndThemeSpecificCovers() {
        val id = 987654321012345678L
        val item = ProtoWire.message(
            ProtoWire.int64(2, id),
            ProtoWire.bytes(3, ProtoWire.bytes(6, ProtoWire.string(1, "图文正文"))),
            ProtoWire.bytes(4, ProtoWire.message(
                ProtoWire.bytes(1, ProtoWire.string(1, "https://i0.hdslb.com/light.jpg")),
                ProtoWire.bytes(7, ProtoWire.string(1, "https://i0.hdslb.com/dark.jpg")),
                ProtoWire.bytes(4, ProtoWire.message(ProtoWire.int32(1, 6), ProtoWire.string(2, "2.3万"))),
            )),
        )
        val collection = ProtoWire.message(
            ProtoWire.bytes(1, ProtoWire.message(ProtoWire.int64(1, 99L),
                ProtoWire.bytes(2, ProtoWire.bytes(1, ProtoWire.string(1, "文集"))))),
            ProtoWire.string(2, "https://i0.hdslb.com/collection.jpg"), ProtoWire.string(4, "系列文章"),
        )
        val page = parseSpaceOpusReply(ProtoWire.message(
            ProtoWire.bytes(1, item), ProtoWire.bytes(2, ProtoWire.string(1, "next-2")),
            ProtoWire.bytes(3, ProtoWire.bytes(2, collection)),
        ))
        val opus = page.items.single()
        assertEquals(id, opus.id)
        assertEquals("图文正文", opus.title)
        assertEquals("https://www.bilibili.com/opus/$id", opus.jump_url)
        assertEquals(listOf("https://i0.hdslb.com/light.jpg"), opus.displayImageUrls())
        assertEquals(listOf("https://i0.hdslb.com/dark.jpg"), opus.displayImageUrls(darkTheme = true))
        assertEquals(listOf("2.3万点赞"), opus.displayStats)
        assertEquals("next-2", page.next)
        assertEquals(SpaceOpusCollection(99L, "文集", "https://i0.hdslb.com/collection.jpg", "系列文章"), page.collections.single())
        val end = parseSpaceOpusReply(ProtoWire.message())
        assertTrue(end.next.isEmpty())
        assertTrue(end.items.isEmpty())
    }
}
