package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.ProtoWire
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MessageImRepositoryTest {
    @Test
    fun keepsOpaquePaginationAndPrivateIdentity() {
        val cursor = ProtoWire.message(ProtoWire.bytes(1, ProtoWire.message(
            ProtoWire.int32(1, 1), ProtoWire.bytes(2, ProtoWire.message(ProtoWire.int64(1, 123), ProtoWire.int64(2, 456))),
        )), ProtoWire.bool(2, true))
        val id = ProtoWire.bytes(1, ProtoWire.int64(1, 42))
        val payload = ProtoWire.message(
            ProtoWire.bytes(1, cursor), ProtoWire.bytes(5, session(id, "用户", "一条消息")),
            ProtoWire.bytes(6, ProtoWire.message(ProtoWire.string(1, "一键已读"), ProtoWire.int32(4, 1))),
        )
        val page = MessageImRepository.parseSessions(payload, 1)
        assertContentEquals(cursor, page.pagination)
        assertTrue(page.hasMore)
        assertEquals(1, page.menu.single().type)
        val entry = page.sessions.single()
        assertContentEquals(id, entry.id)
        assertEquals(42L, entry.item.talker_id)
        assertEquals(1, entry.item.session_type)
        assertEquals("用户", entry.item.account_info?.name)
        assertTrue(entry.isPrivate)
        assertEquals(entry.key, entry.item.identity_key)
    }

    @Test
    fun folderAndSystemIdentitiesNeverBecomePrivateTalkerIds() {
        val folderId = ProtoWire.bytes(3, ProtoWire.int32(1, 4))
        val systemId = ProtoWire.bytes(4, ProtoWire.int32(1, 8))
        val page = MessageImRepository.parseSessions(ProtoWire.message(
            ProtoWire.bytes(3, session(folderId, "未关注", "消息一")),
            ProtoWire.bytes(3, session(systemId, "系统通知", "消息二")),
        ), 2)
        assertEquals(2, page.sessions.first().folderPage)
        assertFalse(page.sessions.first().isPrivate)
        assertTrue(page.sessions.last().systemNotice)
        assertFalse(page.sessions.last().isPrivate)
        assertTrue(page.sessions.first().item.talker_id < 0)
        assertFalse(page.hasMore)
    }

    @Test
    fun stableSessionKeySurvivesSummaryAndTimestampUpdates() {
        val id = ProtoWire.bytes(3, ProtoWire.int32(1, 6))
        val first = MessageImRepository.parseSessions(ProtoWire.bytes(5, session(id, "拦截", "旧摘要")), 1).sessions.single()
        val second = MessageImRepository.parseSessions(ProtoWire.bytes(5, session(id, "拦截", "新摘要")), 1).sessions.single()
        assertEquals(first.key, second.key)
        assertEquals(first.item.identity_key, second.item.identity_key)
        assertEquals(4, first.folderPage)
    }

    @Test
    fun settingSwitchRoundTripPreservesMetadataAndFalseValue() {
        val setting = MessageImRepository.parseSetting(4, ProtoWire.message(
            ProtoWire.bytes(1, ProtoWire.message(ProtoWire.bool(1, true), ProtoWire.string(2, "接收提醒"), ProtoWire.string(3, "说明"))),
            ProtoWire.string(99, "future"),
            ProtoWire.fixed64(100, 0x123456789abcdef),
            ProtoWire.fixed32(101, 123456),
        ))
        val request = ImFields(MessageImRepository.buildSettingRequest(setting, enabled = false))
        val entry = ImFields(request.bytes(1))
        assertEquals(4L, entry.number(1))
        val raw = ImFields(entry.bytes(2))
        assertEquals("future", raw.text(99))
        assertEquals(0x123456789abcdefL, raw.fields.first { it.number == 100 }.fixed64)
        assertEquals(123456, raw.fields.first { it.number == 101 }.fixed32)
        val switch = ImFields(raw.bytes(1))
        assertEquals(0L, switch.number(1))
        assertEquals("接收提醒", switch.text(2))
        assertEquals("说明", switch.text(3))
    }

    @Test
    fun windowSelectionUpdatesOnlySelectionAndSummary() {
        fun option(type: Int, text: String, selected: Boolean) = ProtoWire.bytes(2, ProtoWire.message(
            ProtoWire.int32(1, type), ProtoWire.string(2, text), ProtoWire.bool(3, selected),
        ))
        val setting = MessageImRepository.parseSetting(24, ProtoWire.bytes(3, ProtoWire.message(
            ProtoWire.string(3, "接收范围"), ProtoWire.string(5, "所有人"),
            ProtoWire.bytes(7, ProtoWire.message(ProtoWire.string(1, "接收范围"), option(1, "所有人", true), option(2, "关注的人", false))),
        )))
        val request = ImFields(MessageImRepository.buildSettingRequest(setting, selectedType = 2))
        val updated = MessageImRepository.parseSetting(24, ImFields(request.bytes(1)).bytes(2))
        assertEquals("关注的人", updated.summary)
        assertFalse(updated.options.first().selected)
        assertTrue(updated.options.last().selected)
        assertEquals("接收范围", updated.title)
    }

    @Test
    fun numericUnreadZeroAndDotAreDistinct() {
        val id = ProtoWire.bytes(1, ProtoWire.int64(1, 42))
        fun payload(style: Int, number: Long) = ProtoWire.bytes(5, ProtoWire.message(
            ProtoWire.bytes(1, id), ProtoWire.bytes(3, ProtoWire.message(ProtoWire.int32(1, style), ProtoWire.int64(2, number))),
        ))
        val zero = MessageImRepository.parseSessions(payload(2, 0), 1).sessions.single()
        val dot = MessageImRepository.parseSessions(payload(1, 0), 1).sessions.single()
        assertEquals(0, zero.item.unread_count)
        assertFalse(zero.unreadDot)
        assertTrue(dot.unreadDot)
    }

    @Test
    fun gatewayUnavailabilityIsScopedToAccountAndExpiresAfterTtl() {
        MessageImRepository.resetGatewayUnavailable()
        try {
            MessageImRepository.markGatewayUnavailable(currentMid = 1001L, nowMs = 1_000L)

            assertTrue(MessageImRepository.isGatewayUnavailable(currentMid = 1001L, nowMs = 1_000L))
            assertFalse(MessageImRepository.isGatewayUnavailable(currentMid = 2002L, nowMs = 1_000L))
            assertFalse(MessageImRepository.isGatewayUnavailable(currentMid = null, nowMs = 1_000L))

            val beforeExpiry = 1_000L + MessageImRepository.GATEWAY_UNAVAILABLE_TTL_MS - 1L
            val atExpiry = 1_000L + MessageImRepository.GATEWAY_UNAVAILABLE_TTL_MS
            assertTrue(MessageImRepository.isGatewayUnavailable(currentMid = 1001L, nowMs = beforeExpiry))
            assertFalse(MessageImRepository.isGatewayUnavailable(currentMid = 1001L, nowMs = atExpiry))
        } finally {
            MessageImRepository.resetGatewayUnavailable()
        }
    }

    private fun session(id: ByteArray, name: String, summary: String): ByteArray = ProtoWire.message(
        ProtoWire.bytes(1, id), ProtoWire.bytes(2, ProtoWire.string(1, name)),
        ProtoWire.bytes(4, ProtoWire.string(1, summary)), ProtoWire.int64(5, 1_770_000_000_000_000),
    )
}
