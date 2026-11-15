package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.grpc.BiliGrpcClient
import com.android.purebilibili.core.network.grpc.ProtoWire
import com.android.purebilibili.core.store.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

internal data class MessageShareTarget(
    val mid: Long,
    val name: String,
    val avatarUrl: String,
)

internal object MessageShareGrpcRepository {
    private const val SHARE_LIST_PATH = "/bilibili.im.interface.v1.ImInterface/ShareList"
    private const val SEND_MESSAGE_PATH = "/bilibili.im.interface.v1.ImInterface/SendMsg"

    suspend fun getShareTargets(size: Int = 5): Result<List<MessageShareTarget>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = BiliGrpcClient.requestCancellable(
                    path = SHARE_LIST_PATH,
                    message = ProtoWire.int32(1, size.coerceIn(1, 30)),
                )
                parseMessageShareTargets(response)
            }.onFailure { error ->
                if (error is CancellationException) throw error
            }
        }

    /**
     * Sends a share-v2 or common content card through the IM protocol.
     * The web send endpoint accepts text messages reliably but is inconsistent for
     * share-v2 cards, so dynamic sharing deliberately stays on the IM gRPC path.
     */
    suspend fun sendDynamicShare(
        receiverId: Long,
        content: String,
        messageType: Int = 7,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            require(receiverId > 0L) { "无效的接收者" }
            require(content.isNotBlank()) { "分享内容为空" }
            val senderUid = TokenManager.midCache?.takeIf { it > 0L }
                ?: error("无法获取用户信息，请重新登录")
            val response = BiliGrpcClient.requestCancellable(
                path = SEND_MESSAGE_PATH,
                message = buildDynamicShareSendRequest(
                    senderUid = senderUid,
                    receiverId = receiverId,
                    content = content,
                    messageType = messageType,
                    timestampSeconds = System.currentTimeMillis() / 1000,
                    deviceId = UUID.randomUUID().toString(),
                ),
            )
            ProtoWire.parseFields(response)
                .firstOrNull { it.number == 1 && it.wireType == ProtoWire.WIRE_VARINT }
                ?.varint
                ?: 0L
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
    }
}

internal fun buildDynamicShareSendRequest(
    senderUid: Long,
    receiverId: Long,
    content: String,
    timestampSeconds: Long,
    deviceId: String,
    messageType: Int = 7,
): ByteArray {
    val message = ProtoWire.message(
        ProtoWire.int64(1, senderUid),
        ProtoWire.int32(2, 1),
        ProtoWire.int64(3, receiverId),
        ProtoWire.int32(5, messageType),
        ProtoWire.string(6, content),
        ProtoWire.int64(8, timestampSeconds),
        ProtoWire.int32(16, 1),
    )
    return ProtoWire.message(
        ProtoWire.bytes(1, message),
        ProtoWire.string(5, deviceId),
    )
}

internal fun parseMessageShareTargets(bytes: ByteArray): List<MessageShareTarget> =
    ProtoWire.parseFields(bytes)
        .asSequence()
        .filter { it.number == 1 && it.wireType == ProtoWire.WIRE_LENGTH_DELIMITED }
        .mapNotNull { field ->
            val fields = ProtoWire.parseFields(field.bytes)
            val mid = fields.firstOrNull { it.number == 1 }?.varint ?: 0L
            if (mid <= 0L) return@mapNotNull null
            MessageShareTarget(
                mid = mid,
                name = fields.firstOrNull { it.number == 2 }?.let(ProtoWire::stringValue).orEmpty(),
                avatarUrl = fields.firstOrNull { it.number == 3 }?.let(ProtoWire::stringValue).orEmpty(),
            )
        }
        .distinctBy(MessageShareTarget::mid)
        .toList()
