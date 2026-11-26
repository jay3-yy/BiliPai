package com.android.purebilibili.feature.login

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.PublicKey
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BiliPaiTransferRequest(
    val version: Int = 1,
    val transferId: String,
    val receiverDeviceId: String,
    val receiverPublicKey: String,
    val expiresAt: Long,
)

@Serializable
data class BiliPaiTransferEnvelope(
    val version: Int = 1,
    val transferId: String,
    val senderDeviceId: String,
    val senderPublicKey: String,
    val wrappedKey: String,
    val iv: String,
    val ciphertext: String,
    val signature: String,
    val expiresAt: Long,
)

typealias BiliPaiSessionBundle = com.android.purebilibili.data.session.BiliPaiSessionBundle

/** Version bump of the encrypted payload; receivers reject mismatches explicitly. */
const val BILIPAI_TRANSFER_PAYLOAD_VERSION = 2

@Serializable
data class BiliPaiTransferredJsPlugin(
    val manifest: com.android.purebilibili.core.plugin.js.BiliPaiJsPluginManifest,
    val script: String,
    val grantedCapabilities: Set<com.android.purebilibili.plugin.sdk.PluginCapability> = emptySet(),
    val enabled: Boolean = false,
)

/** Raw installable package (skin or external Kotlin plugin) re-shared from local storage. */
@Serializable
data class BiliPaiTransferredPackage(
    val name: String,
    val bytesBase64: String,
)

/** JSON rule plugins: their shared_prefs XML plus per-plugin JSON files. */
@Serializable
data class BiliPaiTransferredJsonRules(
    val sharedPrefsXml: String? = null,
    val files: List<BiliPaiTransferredFile> = emptyList(),
)

@Serializable
data class BiliPaiTransferredFile(val name: String, val content: String)

@Serializable
data class BiliPaiTransferPayload(
    val version: Int = BILIPAI_TRANSFER_PAYLOAD_VERSION,
    val session: BiliPaiSessionBundle,
    val contentOnly: Boolean = false,
    val settingsJson: String? = null,
    val jsonRules: BiliPaiTransferredJsonRules? = null,
    val jsPlugins: List<BiliPaiTransferredJsPlugin> = emptyList(),
    val skins: List<BiliPaiTransferredPackage> = emptyList(),
    val kotlinPlugins: List<BiliPaiTransferredPackage> = emptyList(),
    val keepsakes: List<com.android.purebilibili.feature.personal.CompletionKeepsakeRecord> = emptyList(),
) {
    fun isContentEmpty(): Boolean = settingsJson == null && jsonRules == null &&
        jsPlugins.isEmpty() && skins.isEmpty() && kotlinPlugins.isEmpty() && keepsakes.isEmpty()
}

object BiliPaiTransferCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun encodeRequest(request: BiliPaiTransferRequest): String =
        "bilipai://transfer/request?payload=${encode(json.encodeToString(BiliPaiTransferRequest.serializer(), request))}"

    fun decodeRequest(raw: String, now: Long = System.currentTimeMillis()): BiliPaiTransferRequest {
        require(raw.length <= MAX_QR_CHARS) { "二维码内容过大" }
        require(raw.startsWith("bilipai://transfer/request?payload=")) { "不是 BiliPai 传输二维码" }
        val request = json.decodeFromString(
            BiliPaiTransferRequest.serializer(), decode(raw.substringAfter("payload=")))
        require(request.version == 1 && request.transferId.isNotBlank()) { "传输请求版本无效" }
        require(request.expiresAt > now) { "传输请求已过期" }
        require(publicKey(request.receiverPublicKey).fingerprint() == request.receiverDeviceId) {
            "接收设备标识校验失败"
        }
        return request
    }

    fun encodeEnvelope(envelope: BiliPaiTransferEnvelope): String =
        "bilipai://transfer/envelope?payload=${encode(json.encodeToString(BiliPaiTransferEnvelope.serializer(), envelope))}"

    /** Keep the QR capacity decision in the codec, before the UI attempts to draw it. */
    internal fun encodeEnvelopeQrs(envelope: BiliPaiTransferEnvelope): List<String> {
        val encoded = encodeEnvelope(envelope)
        return if (encoded.length <= SINGLE_QR_MAX_CHARS) listOf(encoded)
            else BiliPaiTransferChunks.split(envelope.transferId, encoded)
    }

    fun decodeEnvelope(raw: String): BiliPaiTransferEnvelope {
        require(raw.length <= CHUNKED_ENVELOPE_MAX_CHARS) { "传输内容过大" }
        require(raw.startsWith("bilipai://transfer/envelope?payload=")) { "不是 BiliPai 加密传输二维码" }
        return json.decodeFromString(
            BiliPaiTransferEnvelope.serializer(), decode(raw.substringAfter("payload=")))
    }

    private fun publicKey(value: String): PublicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)))

    private fun PublicKey.fingerprint(): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(encoded), Base64.NO_WRAP or Base64.URL_SAFE)

    private fun encode(value: String): String = Base64.encodeToString(
        value.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)

    private fun decode(value: String): String = String(
        Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE), StandardCharsets.UTF_8)

    private const val MAX_QR_CHARS = 100_000

    /** Single-QR envelope cap; larger envelopes go through [BiliPaiTransferChunks] for easier scanning. */
    internal const val SINGLE_QR_MAX_CHARS = 1_100

    /** 128 x 1,500 base64 chunk characters decode to at most 144,000 envelope bytes. */
    private const val CHUNKED_ENVELOPE_MAX_CHARS = 144_000
}
