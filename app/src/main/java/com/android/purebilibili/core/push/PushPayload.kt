package com.android.purebilibili.core.push

import com.android.purebilibili.app.CUSTOM_PUSH_DYNAMIC_CHANNEL_ID
import com.android.purebilibili.app.CUSTOM_PUSH_LIVE_CHANNEL_ID
import com.android.purebilibili.app.CUSTOM_PUSH_MESSAGE_CHANNEL_ID
import com.android.purebilibili.app.CUSTOM_PUSH_TEST_CHANNEL_ID
import com.android.purebilibili.app.CUSTOM_PUSH_VIDEO_CHANNEL_ID
import org.json.JSONException
import org.json.JSONObject
import java.net.URI
import kotlin.math.abs

enum class PushNotificationType(
    val wireValue: String,
    val channelId: String,
) {
    VIDEO("video", CUSTOM_PUSH_VIDEO_CHANNEL_ID),
    LIVE("live", CUSTOM_PUSH_LIVE_CHANNEL_ID),
    DYNAMIC("dynamic", CUSTOM_PUSH_DYNAMIC_CHANNEL_ID),
    MESSAGE("message", CUSTOM_PUSH_MESSAGE_CHANNEL_ID),
    TEST("test", CUSTOM_PUSH_TEST_CHANNEL_ID);

    companion object {
        fun fromWireValue(raw: String?): PushNotificationType =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: VIDEO
    }
}

data class PushPayload(
    val version: Int = PushCrypto.PUSH_PROTOCOL_VERSION,
    val id: String,
    val type: PushNotificationType,
    val title: String,
    val body: String,
    val url: String? = null,
    val cover: String? = null,
    val avatar: String? = null,
    val group: String? = null,
    val timestampSeconds: Long,
)

sealed interface PushDecodeResult {
    data class Success(
        val payload: PushPayload,
        val wasEncrypted: Boolean,
        val strippedUnsafeUrl: Boolean = false,
        val strippedUnsafeImage: Boolean = false,
    ) : PushDecodeResult

    data class Rejected(val reason: String) : PushDecodeResult
}

object PushPayloadParser {
    const val MAX_RAW_PAYLOAD_BYTES = 16 * 1024
    const val MAX_CLOCK_SKEW_SECONDS = 10 * 60L
    private const val MAX_TITLE_LENGTH = 120
    private const val MAX_BODY_LENGTH = 500
    private const val MAX_ID_LENGTH = 128

    private val ALLOWED_WEB_HOSTS = setOf(
        "www.bilibili.com",
        "bilibili.com",
        "m.bilibili.com",
        "live.bilibili.com",
        "space.bilibili.com",
        "t.bilibili.com",
        "search.bilibili.com",
        "music.bilibili.com",
        "b23.tv",
    )

    private val ALLOWED_BILIPAI_SHORTCUT_HOSTS = setOf(
        "search",
        "dynamic",
        "favorite",
        "history",
        "inbox",
        "live",
    )

    private val ALLOWED_IMAGE_DOMAIN_SUFFIXES = listOf(
        "hdslb.com",
        "bilivideo.com",
        "bilibili.com",
    )

    fun parse(
        rawBytes: ByteArray,
        secretKeyBase64: String,
        allowPlaintext: Boolean,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000L,
    ): PushDecodeResult {
        if (rawBytes.isEmpty()) return PushDecodeResult.Rejected("empty_payload")
        if (rawBytes.size > MAX_RAW_PAYLOAD_BYTES) return PushDecodeResult.Rejected("payload_too_large")

        val rawText = rawBytes.toString(Charsets.UTF_8).trim()
        val outer = try {
            JSONObject(rawText)
        } catch (_: JSONException) {
            return PushDecodeResult.Rejected("invalid_json")
        }

        val hasEncryptionHeader = outer.has("enc") || outer.has("ct") || outer.has("iv")
        val wasEncrypted: Boolean
        val inner: JSONObject

        if (hasEncryptionHeader) {
            val version = outer.optInt("v", -1)
            val algorithm = outer.optString("enc", "")
            val iv = outer.optString("iv", "")
            val ct = outer.optString("ct", "")
            if (version != PushCrypto.PUSH_PROTOCOL_VERSION) {
                return PushDecodeResult.Rejected("unsupported_version")
            }
            if (algorithm != PushCrypto.PUSH_CIPHER_ALGORITHM_ID) {
                return PushDecodeResult.Rejected("unsupported_cipher")
            }
            if (iv.isBlank() || ct.isBlank()) {
                return PushDecodeResult.Rejected("malformed_envelope")
            }
            val decryptedBytes = try {
                PushCrypto.decrypt(
                    envelope = EncryptedPushEnvelope(
                        version = version,
                        algorithm = algorithm,
                        ivBase64 = iv,
                        ciphertextBase64 = ct,
                    ),
                    keyBase64 = secretKeyBase64,
                )
            } catch (_: Exception) {
                return PushDecodeResult.Rejected("decryption_failed")
            }
            inner = try {
                JSONObject(decryptedBytes.toString(Charsets.UTF_8))
            } catch (_: JSONException) {
                return PushDecodeResult.Rejected("invalid_decrypted_json")
            }
            wasEncrypted = true
        } else {
            // 强制不降级：默认或未开启明文调试时，拒绝一切明文包。
            if (!allowPlaintext) {
                return PushDecodeResult.Rejected("plaintext_rejected")
            }
            inner = outer
            wasEncrypted = false
        }

        val version = inner.optInt("v", PushCrypto.PUSH_PROTOCOL_VERSION)
        if (version != PushCrypto.PUSH_PROTOCOL_VERSION) {
            return PushDecodeResult.Rejected("unsupported_payload_version")
        }

        val title = inner.optString("title", "").trim().take(MAX_TITLE_LENGTH)
        if (title.isEmpty()) {
            return PushDecodeResult.Rejected("missing_title")
        }
        val body = inner.optString("body", "").trim().take(MAX_BODY_LENGTH)
        val rawId = inner.optString("id", "").trim().take(MAX_ID_LENGTH)
        val ts = inner.optLong("ts", 0L)

        if (wasEncrypted) {
            if (rawId.isEmpty()) {
                return PushDecodeResult.Rejected("missing_id")
            }
            if (ts <= 0L) {
                return PushDecodeResult.Rejected("missing_timestamp")
            }
            if (abs(nowEpochSeconds - ts) > MAX_CLOCK_SKEW_SECONDS) {
                return PushDecodeResult.Rejected("timestamp_expired")
            }
        } else if (ts > 0L && abs(nowEpochSeconds - ts) > MAX_CLOCK_SKEW_SECONDS) {
            return PushDecodeResult.Rejected("timestamp_expired")
        }

        val effectiveId = rawId.ifEmpty {
            "plain_${nowEpochSeconds}_${title.hashCode()}"
        }
        val rawUrl = inner.optString("url", "").trim().ifEmpty { null }
        val safeUrl = sanitizeTargetUrl(rawUrl)
        val rawCover = inner.optString("cover", "").trim().ifEmpty { null }
        val safeCover = sanitizeImageUrl(rawCover)
        val rawAvatar = inner.optString("avatar", "").trim().ifEmpty { null }
        val safeAvatar = sanitizeImageUrl(rawAvatar)

        return PushDecodeResult.Success(
            payload = PushPayload(
                version = version,
                id = effectiveId,
                type = PushNotificationType.fromWireValue(inner.optString("type", "")),
                title = title,
                body = body,
                url = safeUrl,
                cover = safeCover,
                avatar = safeAvatar,
                group = inner.optString("group", "").trim().take(64).ifEmpty { null },
                timestampSeconds = if (ts > 0L) ts else nowEpochSeconds,
            ),
            wasEncrypted = wasEncrypted,
            strippedUnsafeUrl = rawUrl != null && safeUrl == null,
            strippedUnsafeImage = (rawCover != null && safeCover == null) ||
                (rawAvatar != null && safeAvatar == null),
        )
    }

    fun encodePlaintextJson(payload: PushPayload): String {
        return JSONObject().apply {
            put("v", payload.version)
            put("id", payload.id)
            put("type", payload.type.wireValue)
            put("title", payload.title)
            put("body", payload.body)
            payload.url?.let { put("url", it) }
            payload.cover?.let { put("cover", it) }
            payload.avatar?.let { put("avatar", it) }
            payload.group?.let { put("group", it) }
            put("ts", payload.timestampSeconds)
        }.toString()
    }

    fun encodeEncryptedEnvelopeJson(envelope: EncryptedPushEnvelope): String {
        return JSONObject().apply {
            put("v", envelope.version)
            put("enc", envelope.algorithm)
            put("iv", envelope.ivBase64)
            put("ct", envelope.ciphertextBase64)
        }.toString()
    }

    /**
     * 仅放行 B 站内容链接与安全快捷路由，拒绝插件安装链接和任意外部网站。
     */
    fun sanitizeTargetUrl(rawUrl: String?): String? {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotEmpty() && it.length <= 1024 } ?: return null
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        return when (scheme) {
            "https", "http" -> {
                if (host in ALLOWED_WEB_HOSTS) trimmed else null
            }
            "bilibili", "bili" -> {
                if (host.isNotEmpty()) trimmed else null
            }
            "bilipai" -> {
                if (host in ALLOWED_BILIPAI_SHORTCUT_HOSTS) trimmed else null
            }
            else -> null
        }
    }

    /**
     * 仅放行 HTTPS 的 B 站官方图床域名，避免外链图片追踪设备 IP。
     */
    fun sanitizeImageUrl(rawUrl: String?): String? {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotEmpty() && it.length <= 1024 } ?: return null
        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val allowed = ALLOWED_IMAGE_DOMAIN_SUFFIXES.any { suffix ->
            host == suffix || host.endsWith(".$suffix")
        }
        return if (allowed) trimmed else null
    }
}
