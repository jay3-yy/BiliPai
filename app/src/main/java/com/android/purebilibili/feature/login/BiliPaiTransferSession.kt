package com.android.purebilibili.feature.login

import android.content.Context
import com.android.purebilibili.core.store.AccountSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.util.TreeMap
import java.util.UUID

/** Preserve structured cancellation while reporting recoverable transfer errors. */
internal inline fun <T> transferCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}

enum class BiliPaiTransferMode { RECEIVE, SEND }

sealed interface BiliPaiTransferState {
    data object Idle : BiliPaiTransferState
    data class WaitingForRequest(val request: BiliPaiTransferRequest) : BiliPaiTransferState
    data class RequestReady(val request: BiliPaiTransferRequest) : BiliPaiTransferState
    data class EnvelopeReady(val chunks: List<String>) : BiliPaiTransferState
    data class AwaitingConfirmation(val payload: BiliPaiTransferPayload) : BiliPaiTransferState
    data class Completed(
        val mid: Long,
        val importReport: BiliPaiTransferImportReport? = null,
    ) : BiliPaiTransferState
    data class Failed(val message: String) : BiliPaiTransferState
}

/** Accumulates chunk QRs until the full encrypted envelope can be reassembled. */
class BiliPaiTransferChunkBuffer {
    private val chunks = TreeMap<Int, BiliPaiTransferChunk>()
    var total: Int = 0
        private set
    var transferId: String = ""
        private set

    fun reset() {
        chunks.clear(); total = 0; transferId = ""
    }

    /** Returns received/total while incomplete, or the reassembled envelope when done. */
    fun offer(raw: String): Result<String?> {
        val chunk = BiliPaiTransferChunks.parse(raw)
            ?: return Result.failure(IllegalArgumentException("不是 BiliPai 传输分块"))
        if (total == 0) {
            total = chunk.total; transferId = chunk.transferId
        } else if (chunk.transferId != transferId || chunk.total != total) {
            return Result.failure(IllegalArgumentException("分块来自另一次传输，请重新开始接收"))
        }
        val previous = chunks[chunk.index]
        if (previous != null && previous != chunk) {
            return Result.failure(IllegalArgumentException("同一分块内容冲突，请重新开始接收"))
        }
        chunks[chunk.index] = chunk
        if (chunks.size < total) return Result.success(null)
        val envelope = BiliPaiTransferChunks.join(chunks.values)
            ?: return Result.failure(IllegalArgumentException("分块不完整，请重新接收"))
        return Result.success(envelope)
    }

    fun receivedCount(): Int = chunks.size

    fun missingIndices(): List<Int> {
        if (total <= 0) return emptyList()
        return (0 until total).filter { it !in chunks }
    }
}

/** Coordinates the two QR scans; it contains no camera or Compose code. */
class BiliPaiTransferSession(private val context: Context) {
    private val keys: KeyPair by lazy { BiliPaiTransferKeyStore.getOrCreate(context) }
    private var request: BiliPaiTransferRequest? = null
    val chunkBuffer = BiliPaiTransferChunkBuffer()
    var state: BiliPaiTransferState = BiliPaiTransferState.Idle
        private set

    fun beginReceive(now: Long = System.currentTimeMillis()): BiliPaiTransferRequest {
        clear()
        val created = BiliPaiTransferCodecRequestFactory.create(context, now)
        request = created
        state = BiliPaiTransferState.RequestReady(created)
        return created
    }

    fun acceptRequest(raw: String): BiliPaiTransferRequest {
        val parsed = BiliPaiTransferCodec.decodeRequest(raw)
        clear()
        request = parsed
        state = BiliPaiTransferState.WaitingForRequest(parsed)
        return parsed
    }

    suspend fun createEnvelope(
        bundle: BiliPaiSessionBundle,
        selection: BiliPaiTransferContentSelection = BiliPaiTransferContentSelection(),
        contentOnly: Boolean = false,
    ): List<String> = withContext(Dispatchers.IO) {
        val target = request ?: error("请先扫描接收设备请求")
        val payload = BiliPaiTransferContent.collect(context, selection, bundle, contentOnly = contentOnly)
        val envelope = BiliPaiTransferCrypto.encryptPayload(keys, keys.public.fingerprint(), target, payload)
        val chunks = BiliPaiTransferCodec.encodeEnvelopeQrs(envelope)
        state = BiliPaiTransferState.EnvelopeReady(chunks)
        chunks
    }

    /**
     * Feeds a scanned QR (single envelope or one chunk). Returns the decoded payload
     * when the transfer is complete, null while more chunks are expected.
     */
    fun acceptEnvelope(raw: String): BiliPaiTransferPayload? {
        val target = request ?: error("请先创建或扫描传输请求")
        val chunk = BiliPaiTransferChunks.parse(raw)
        val envelopeRaw = if (chunk != null) {
            require(chunk.transferId == target.transferId) { "分块来自另一次传输" }
            val joined = chunkBuffer.offer(raw).getOrElse { error(it.message ?: "分块无效") }
            joined ?: return null
        } else raw
        val envelope = BiliPaiTransferCodec.decodeEnvelope(envelopeRaw)
        val payload = BiliPaiTransferCrypto.decryptPayload(keys.private, target, envelope)
        if (payload.contentOnly) {
            require(!payload.isContentEmpty()) { "传输备份内容为空" }
        } else {
            require(payload.session.mid > 0L && payload.session.sessData.isNotBlank()) { "传输账号会话无效" }
        }
        require(payload.version == BILIPAI_TRANSFER_PAYLOAD_VERSION) { "传输载荷版本不一致，请升级两端 BiliPai" }
        state = BiliPaiTransferState.AwaitingConfirmation(payload)
        return payload
    }

    suspend fun confirmImport(
        payload: BiliPaiTransferPayload,
        approvedPluginKeys: Set<String> = emptySet(),
        importSession: Boolean = !payload.contentOnly,
    ): Boolean = withContext(Dispatchers.IO) {
        check((state as? BiliPaiTransferState.AwaitingConfirmation)?.payload == payload) {
            "请先接收并确认当前传输内容"
        }
        val shouldImportSession = importSession && !payload.contentOnly
        if (shouldImportSession) {
            val sessionOk = AccountSessionStore.importTransferredSession(context, payload.session)
            if (!sessionOk) {
                return@withContext false
            }
        }
        val keepsakeMidOverride = if (shouldImportSession) {
            payload.session.mid.takeIf { it > 0L }
        } else {
            com.android.purebilibili.core.store.TokenManager.midCache?.takeIf { it > 0L }
        }
        val report = transferCatching {
            BiliPaiTransferContent.apply(
                context = context,
                payload = payload,
                approvedPluginKeys = approvedPluginKeys,
                keepsakeAccountMidOverride = keepsakeMidOverride,
            )
        }.getOrElse {
            BiliPaiTransferImportReport(items = listOf(
                BiliPaiTransferImportItemResult("内容导入", false, it.message)))
        }
        val completedMid = if (shouldImportSession) {
            payload.session.mid
        } else {
            com.android.purebilibili.core.store.TokenManager.midCache ?: 0L
        }
        state = BiliPaiTransferState.Completed(completedMid, report)
        true
    }

    fun clear() {
        request = null
        chunkBuffer.reset()
        state = BiliPaiTransferState.Idle
    }
}

private object BiliPaiTransferCodecRequestFactory {
    fun create(context: Context, now: Long): BiliPaiTransferRequest =
        BiliPaiTransferRequest(
            transferId = UUID.randomUUID().toString(),
            receiverDeviceId = BiliPaiTransferKeyStore.getOrCreate(context).public.fingerprint(),
            receiverPublicKey = android.util.Base64.encodeToString(
                BiliPaiTransferKeyStore.getOrCreate(context).public.encoded,
                android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE),
            expiresAt = now + 10 * 60 * 1000L,
        )
}

private fun java.security.PublicKey.fingerprint(): String = android.util.Base64.encodeToString(
    java.security.MessageDigest.getInstance("SHA-256").digest(encoded),
    android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE)
