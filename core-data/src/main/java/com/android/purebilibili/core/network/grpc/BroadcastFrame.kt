package com.android.purebilibili.core.network.grpc

internal const val BROADCAST_CONTROL = "/bilibili.broadcast.v1.Broadcast/"
internal const val IM_NOTIFY_PATH = "/bilibili.broadcast.message.im.Notify/WatchNotify"

internal data class BroadcastFrame(
    val path: String,
    val messageId: Long,
    val sequence: Long,
    val requiresAck: Boolean,
    val ackOrigin: String,
    val messageType: Long,
    val status: Long,
    val payload: ByteArray,
)

internal object BroadcastFrames {
    fun read(source: okio.BufferedSource): BroadcastFrame? {
        if (source.exhausted()) return null
        val flag = source.readByte()
        val length = source.readInt()
        require(length >= 0 && (flag.toInt() == 0 || flag.toInt() == 1)) { "消息广播帧头无效" }
        val framed = okio.Buffer().writeByte(flag.toInt()).writeInt(length)
            .write(source.readByteArray(length.toLong())).readByteArray()
        return decode(ProtoWire.unframe(framed))
    }

    fun encode(path: String, sequence: Long, type: String, payload: ByteArray): ByteArray {
        val options = ProtoWire.message(
            ProtoWire.int64(1, sequence), ProtoWire.int64(2, sequence),
            ProtoWire.int64(6, System.currentTimeMillis()),
        )
        val body = ProtoWire.message(
            ProtoWire.string(1, "type.googleapis.com/$type"), ProtoWire.bytes(2, payload),
        )
        return ProtoWire.message(ProtoWire.bytes(1, options), ProtoWire.string(2, path), ProtoWire.bytes(3, body))
    }

    fun decode(bytes: ByteArray): BroadcastFrame {
        val fields = ProtoWire.parseFields(bytes)
        val options = ProtoWire.parseFields(fields.firstOrNull { it.number == 1 }?.bytes ?: byteArrayOf())
        val status = ProtoWire.parseFields(options.firstOrNull { it.number == 4 }?.bytes ?: byteArrayOf())
        val body = ProtoWire.parseFields(fields.firstOrNull { it.number == 3 }?.bytes ?: byteArrayOf())
        return BroadcastFrame(
            path = fields.firstOrNull { it.number == 2 }?.bytes?.toString(Charsets.UTF_8).orEmpty(),
            messageId = options.firstOrNull { it.number == 1 }?.varint ?: 0L,
            sequence = options.firstOrNull { it.number == 2 }?.varint ?: 0L,
            requiresAck = options.firstOrNull { it.number == 3 }?.varint == 1L,
            ackOrigin = options.firstOrNull { it.number == 5 }?.bytes?.toString(Charsets.UTF_8).orEmpty(),
            messageType = options.firstOrNull { it.number == 7 }?.varint ?: 0L,
            status = status.firstOrNull { it.number == 1 }?.varint ?: 0L,
            payload = body.firstOrNull { it.number == 2 }?.bytes ?: byteArrayOf(),
        )
    }

    fun ack(frame: BroadcastFrame): ByteArray = ProtoWire.message(
        ProtoWire.int64(1, frame.messageId), ProtoWire.string(2, frame.ackOrigin),
        ProtoWire.string(3, frame.path), ProtoWire.int64(4, frame.messageType),
    )
}
