package com.android.purebilibili.core.network.grpc

import java.io.EOFException
import okio.Buffer
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BroadcastFrameTest {
    private fun serverFrame(path: String, payload: ByteArray, ack: Boolean = false): ByteArray = ProtoWire.message(
        ProtoWire.bytes(1, ProtoWire.message(
            ProtoWire.int64(1, 123L), ProtoWire.int64(2, 7L), ProtoWire.bool(3, ack),
            ProtoWire.string(5, "im"), ProtoWire.int64(7, 2L),
        )),
        ProtoWire.string(2, path),
        ProtoWire.bytes(3, ProtoWire.message(
            ProtoWire.string(1, "type.googleapis.com/bilibili.broadcast.message.im.NotifyRsp"),
            ProtoWire.bytes(2, payload),
        )),
    )

    @Test
    fun readsConsecutiveUncompressedAndCompressedMessagesWithoutLosingBoundaries() {
        val first = byteArrayOf(8, 1)
        val second = ByteArray(1024) { 42 }
        val input = Buffer()
            .write(ProtoWire.frame(serverFrame(IM_NOTIFY_PATH, first), gzipMinLength = Int.MAX_VALUE))
            .write(ProtoWire.frame(serverFrame(IM_NOTIFY_PATH, second), gzipMinLength = 0))
        assertContentEquals(first, BroadcastFrames.read(input)!!.payload)
        assertContentEquals(second, BroadcastFrames.read(input)!!.payload)
        assertNull(BroadcastFrames.read(input))
    }

    @Test
    fun messageAckPreservesServerIdentityOriginPathAndType() {
        val frame = BroadcastFrames.decode(serverFrame(IM_NOTIFY_PATH, byteArrayOf(8, 1), ack = true))
        assertTrue(frame.requiresAck)
        assertEquals(7L, frame.sequence)
        val ack = ProtoWire.parseFields(BroadcastFrames.ack(frame))
        assertEquals(123L, ack.single { it.number == 1 }.varint)
        assertEquals("im", ack.single { it.number == 2 }.bytes.toString(Charsets.UTF_8))
        assertEquals(IM_NOTIFY_PATH, ack.single { it.number == 3 }.bytes.toString(Charsets.UTF_8))
        assertEquals(2L, ack.single { it.number == 4 }.varint)
    }

    @Test
    fun truncatedPayloadCannotBeMisreadAsCompleteNotification() {
        val bytes = ProtoWire.frame(serverFrame(IM_NOTIFY_PATH, byteArrayOf(8, 1)))
        assertFailsWith<EOFException> { BroadcastFrames.read(Buffer().write(bytes.copyOf(bytes.size - 1))) }
    }

    @Test
    fun authAndSubscribeCarryControlPayloadInAnyEnvelope() {
        val auth = ProtoWire.message(ProtoWire.string(1, "guid"), ProtoWire.string(2, "connection"))
        val bytes = BroadcastFrames.encode(BROADCAST_CONTROL + "Auth", 1, "bilibili.broadcast.v1.AuthReq", auth)
        val fields = ProtoWire.parseFields(bytes)
        val body = ProtoWire.parseFields(fields.single { it.number == 3 }.bytes)
        assertEquals("type.googleapis.com/bilibili.broadcast.v1.AuthReq", body.single { it.number == 1 }.bytes.toString(Charsets.UTF_8))
        assertContentEquals(auth, body.single { it.number == 2 }.bytes)
        val subscribe = BroadcastFrames.decode(BroadcastFrames.encode(
            BROADCAST_CONTROL + "Subscribe", 2, "bilibili.broadcast.v1.TargetPath", ProtoWire.string(1, IM_NOTIFY_PATH),
        ))
        assertEquals(IM_NOTIFY_PATH, ProtoWire.parseFields(subscribe.payload).single().bytes.toString(Charsets.UTF_8))
    }
}
