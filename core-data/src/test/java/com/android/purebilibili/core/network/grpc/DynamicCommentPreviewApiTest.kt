package com.android.purebilibili.core.network.grpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DynamicCommentPreviewApiTest {
    @Test
    fun authorBodyAndCommentEmojiRemainOneLocatedRichSummary() {
        val summary = interaction(
            description(10, "作者", uri = "bilibili://space/42", rid = "42"),
            description(1, "：真实评论"),
            description(9, "", original = "[星星眼]", icon = "https://i.example/emoji.png"),
            uri = "bilibili://comment/detail?oid=413967238&type=11&root=318843506177",
            params = mapOf("comment_root_id" to "318843506177", "tracking" to "opaque"),
        )
        val preview = parseDynamicCommentPreviewReply(page(card("100", summary))).getValue("100")
        assertEquals(413967238L, preview.commentOid)
        assertEquals(11, preview.commentType)
        val item = preview.interaction!!.items.single()
        assertEquals(1, item.type)
        assertTrue(item.uri.endsWith("root=318843506177"))
        assertEquals(mapOf("comment_root_id" to "318843506177", "tracking" to "opaque"), item.extend_click_param)
        assertEquals("作者：真实评论[星星眼]", item.desc!!.text)
        val nodes = item.desc!!.rich_text_nodes
        assertEquals(listOf("RICH_TEXT_NODE_TYPE_AT", "RICH_TEXT_NODE_TYPE_TEXT", "RICH_TEXT_NODE_TYPE_EMOJI"), nodes.map { it.type })
        assertEquals("42", nodes[0].rid)
        assertEquals("bilibili://space/42", nodes[0].jump_url)
        assertEquals("", nodes[2].text)
        assertEquals("[星星眼]", nodes[2].orig_text)
        assertEquals("[星星眼]", nodes[2].emoji!!.text)
        assertEquals("https://i.example/emoji.png", nodes[2].emoji!!.icon_url)
    }

    @Test
    fun verifiedDescriptionEnumsDoNotTurnResourceIdsIntoUsersOrFloors() {
        val expected = listOf(
            1 to "TEXT", 2 to "AT", 3 to "LOTTERY", 4 to "VOTE", 5 to "TOPIC", 6 to "GOODS",
            7 to "BV", 8 to "AV", 9 to "EMOJI", 10 to "AT", 11 to "CV", 12 to "VC",
            13 to "WEB", 14 to "TAOBAO", 15 to "MAIL", 16 to "OGV_SEASON", 17 to "OGV_EP",
            18 to "SEARCH_WORD", 999 to "TEXT",
        )
        val descriptions = expected.map { (type, _) ->
            description(type, "token$type", uri = "https://example.com/$type", rid = "resource-$type")
        }
        val parsed = parseDynamicCommentPreviewReply(page(card("100", interaction(*descriptions.toTypedArray()))))
        val item = parsed.getValue("100").interaction!!.items.single()
        val nodes = item.desc!!.rich_text_nodes
        assertEquals(expected.map { "RICH_TEXT_NODE_TYPE_${it.second}" }, nodes.map { it.type })
        assertEquals(1, nodes.count { it.emoji != null })
        assertEquals("resource-8", nodes[7].rid)
        assertEquals("https://example.com/8", nodes[7].jump_url)
        assertTrue(item.extend_click_param.isEmpty())
        assertTrue(item.uri.isEmpty())
    }

    @Test
    fun onePageKeepsDifferentDynamicsAndForwardedOriginalsIndependentlyAddressable() {
        val original = card("100", interaction(description(1, "相同正文"), params = mapOf("comment_root_id" to "111")), oid = 123L, type = 1)
        val forward = card("200", interaction(description(1, "相同正文"), params = mapOf("comment_root_id" to "222")), original = original)
        val other = card("300", interaction(description(1, "相同正文"), params = mapOf("comment_root_id" to "333")))
        val parsed = parseDynamicCommentPreviewReply(page(forward, other))
        assertEquals(setOf("100", "200", "300"), parsed.keys)
        assertEquals("111", parsed.getValue("100").interaction!!.items.single().extend_click_param["comment_root_id"])
        assertEquals("222", parsed.getValue("200").interaction!!.items.single().extend_click_param["comment_root_id"])
        assertEquals("333", parsed.getValue("300").interaction!!.items.single().extend_click_param["comment_root_id"])
        assertEquals(123L, parsed.getValue("100").commentOid)
        assertEquals(1, parsed.getValue("100").commentType)
        assertEquals(413967238L, parsed.getValue("200").commentOid)
    }

    @Test
    fun repeatedCommentItemsDoNotShareClickMetadataOrBorrowTheirNeighboursFloor() {
        val parsed = parseDynamicCommentPreviewReply(page(card(
            "100",
            interaction(description(1, "第一条"), uri = "bilibili://comment/111", params = mapOf("comment_root_id" to "111")),
            extraInteractions = listOf(
                interaction(description(1, "未提供楼层")),
                interaction(description(1, "第三条"), uri = "bilibili://comment/333", params = mapOf("comment_root_id" to "333")),
            ),
        )))
        val items = parsed.getValue("100").interaction!!.items
        assertEquals(listOf("第一条", "未提供楼层", "第三条"), items.map { it.desc!!.text })
        assertEquals(listOf("111", null, "333"), items.map { it.extend_click_param["comment_root_id"] })
        assertEquals(listOf("bilibili://comment/111", "", "bilibili://comment/333"), items.map { it.uri })
    }

    @Test
    fun hitWithoutSummaryDiffersFromMissingCardAndEmptyInteractionIsStillAHit() {
        // Explicit zero-length ModuleInteraction: ProtoWire.bytes intentionally omits empty messages.
        val emptyModule = byteArrayOf(0x62, 0)
        val parsed = parseDynamicCommentPreviewReply(page(card("100"), card("200", rawInteractionModule = emptyModule)))
        assertTrue(parsed.containsKey("100"))
        assertNull(parsed.getValue("100").interaction)
        assertNotNull(parsed.getValue("200").interaction)
        assertTrue(parsed.getValue("200").interaction!!.items.isEmpty())
        assertFalse(parsed.containsKey("300"))
    }

    @Test
    fun likeAvatarCoverAndUnknownIconsNeverBecomeComments() {
        val parsed = parseDynamicCommentPreviewReply(page(card("100", interaction(
            description(1, "点赞提示"), icon = 1, params = mapOf("comment_root_id" to "111"),
        ), extraInteractions = listOf(
            interaction(description(1, "头像"), icon = 2),
            interaction(description(1, "封面"), icon = 3),
            interaction(description(1, "未来图标"), icon = 99),
        ))))
        assertTrue(parsed.getValue("100").interaction!!.items.isEmpty())
    }

    @Test
    fun zeroDefaultCommentIconAndLongOpaqueRootStringsAreNotTruncated() {
        val root = "184467440737095516159999999999999999999"
        val parsed = parseDynamicCommentPreviewReply(page(card("18446744073709551615", interaction(
            description(1, "评论"), icon = null,
            params = mapOf("comment_root_id" to root),
        ))))
        val preview = parsed.getValue("18446744073709551615")
        assertEquals("18446744073709551615", preview.dynamicId)
        assertEquals(root, preview.interaction!!.items.single().extend_click_param["comment_root_id"])
    }

    @Test
    fun missingBusinessSubjectAndDescriptionIdsDoNotCreateFallbackTargets() {
        val parsed = parseDynamicCommentPreviewReply(page(card("100", interaction(
            description(10, "作者", rid = "42"), description(1, "评论", rid = "123456"),
        ), oid = null, type = null)))
        val preview = parsed.getValue("100")
        assertEquals(0L, preview.commentOid)
        assertEquals(0, preview.commentType)
        val item = preview.interaction!!.items.single()
        assertTrue(item.extend_click_param.isEmpty())
        assertTrue(item.uri.isEmpty())
    }

    @Test
    fun mismatchedInteractionDynamicAndInvalidCardIdsCannotLeakAnotherSummary() {
        val parsed = parseDynamicCommentPreviewReply(page(
            card("100", interaction(description(1, "别的动态"), dynamicId = "200", params = mapOf("comment_root_id" to "111"))),
            card("0", interaction(description(1, "无效ID"))),
            card("not-an-id", interaction(description(1, "无效ID"))),
        ) + ProtoWire.string(63, "future response field"))
        assertEquals(setOf("100"), parsed.keys)
        assertTrue(parsed.getValue("100").interaction!!.items.isEmpty())
    }

    @Test
    fun nestedForwardsUseTheExistingSixteenLevelLimit() {
        var nested = card("1")
        for (id in 2..16) nested = card(id.toString(), original = nested)
        assertEquals(16, parseDynamicCommentPreviewReply(page(nested)).size)
        try {
            parseDynamicCommentPreviewReply(page(card("17", original = nested)))
            fail("An unsupported forwarding depth must not be accepted")
        } catch (_: IllegalStateException) {
            // Same depth policy as the existing dynamic protobuf parser.
        }
    }

    private fun page(vararg cards: ByteArray): ByteArray =
        ProtoWire.message(*cards.map { ProtoWire.bytes(1, it) }.toTypedArray())

    private fun card(
        id: String,
        interaction: ByteArray? = null,
        oid: Long? = 413967238L,
        type: Int? = 11,
        original: ByteArray? = null,
        extraInteractions: List<ByteArray> = emptyList(),
        rawInteractionModule: ByteArray? = null,
    ): ByteArray {
        val reply = ProtoWire.message(
            oid?.let { ProtoWire.int64(4, it) } ?: byteArrayOf(),
            type?.let { ProtoWire.int32(3, it) } ?: byteArrayOf(),
        )
        val fields = mutableListOf(
            ProtoWire.int32(1, if (original == null) 7 else 1),
            ProtoWire.bytes(4, ProtoWire.message(ProtoWire.string(1, id), ProtoWire.bytes(18, reply))),
        )
        val interactions = listOfNotNull(interaction) + extraInteractions
        if (interactions.isNotEmpty()) fields += ProtoWire.bytes(3, ProtoWire.bytes(12,
            ProtoWire.message(*interactions.map { ProtoWire.bytes(1, it) }.toTypedArray())))
        if (rawInteractionModule != null) fields += ProtoWire.bytes(3, rawInteractionModule)
        if (original != null) fields += ProtoWire.bytes(3, ProtoWire.bytes(5, ProtoWire.bytes(6, ProtoWire.bytes(1, original))))
        return ProtoWire.message(*fields.toTypedArray())
    }

    private fun interaction(
        vararg descriptions: ByteArray,
        icon: Int? = 0,
        uri: String = "",
        dynamicId: String = "",
        params: Map<String, String> = emptyMap(),
    ): ByteArray = ProtoWire.message(
        icon?.let { ProtoWire.int32(1, it) } ?: byteArrayOf(),
        ProtoWire.message(*descriptions.map { ProtoWire.bytes(2, it) }.toTypedArray()),
        ProtoWire.string(3, uri),
        ProtoWire.string(4, dynamicId),
        ProtoWire.message(*params.map { (key, value) ->
            ProtoWire.bytes(12, ProtoWire.message(ProtoWire.string(1, key), ProtoWire.string(2, value)))
        }.toTypedArray()),
    )

    private fun description(
        type: Int,
        text: String,
        original: String = "",
        uri: String = "",
        rid: String = "",
        icon: String = "",
    ): ByteArray = ProtoWire.message(
        ProtoWire.string(1, text), ProtoWire.int32(2, type), ProtoWire.string(3, uri),
        ProtoWire.string(6, icon), ProtoWire.string(8, rid), ProtoWire.string(10, original),
    )
}
