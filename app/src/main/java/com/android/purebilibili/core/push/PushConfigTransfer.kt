package com.android.purebilibili.core.push

import org.json.JSONException
import org.json.JSONObject

data class ImportedPushConfig(
    val secretKeyBase64: String? = null,
    val fcmRelayUrl: String? = null,
    val suggestedMode: PushChannelMode? = null,
)

sealed interface PushConfigImportResult {
    data class Success(val config: ImportedPushConfig) : PushConfigImportResult
    data class Invalid(val reason: String) : PushConfigImportResult
}

/**
 * 推送配置的二维码/JSON 导入导出解析：
 * - 导出紧凑 JSON，直接兼容 tools/push-test/send_push.mjs 与服务端配置；
 * - 导入仅接受共享密钥、HTTPS 中转网关与可选通道建议，不覆盖本机专属的 Endpoint/FCM Token。
 */
object PushConfigTransfer {
    fun encodeQrPayload(config: CustomPushConfig): String {
        return JSONObject().apply {
            put("v", PushCrypto.PUSH_PROTOCOL_VERSION)
            put("mode", config.mode.wireValue)
            put("cipher", PushCrypto.PUSH_CIPHER_ALGORITHM_ID)
            put("aad", String(PushCrypto.DEFAULT_AAD, Charsets.UTF_8))
            put("secretKey", config.secretKeyBase64)
            when (config.mode) {
                PushChannelMode.UNIFIED_PUSH -> {
                    if (config.unifiedPushEndpoint.isNotBlank()) {
                        put("endpoint", config.unifiedPushEndpoint)
                    }
                }
                PushChannelMode.FCM_RELAY -> {
                    if (config.fcmToken.isNotBlank()) {
                        put("fcmToken", config.fcmToken)
                    }
                    if (config.fcmRelayUrl.isNotBlank()) {
                        put("relayUrl", config.fcmRelayUrl)
                    }
                }
                PushChannelMode.DISABLED -> {
                    if (config.fcmRelayUrl.isNotBlank()) {
                        put("relayUrl", config.fcmRelayUrl)
                    }
                }
            }
        }.toString()
    }

    fun parseImportedPayload(raw: String): PushConfigImportResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return PushConfigImportResult.Invalid("二维码内容为空")

        val json = try {
            JSONObject(trimmed)
        } catch (_: JSONException) {
            return PushConfigImportResult.Invalid("不是有效的 BiliPai 推送配置二维码")
        }

        if (json.has("v") && json.optInt("v", -1) != PushCrypto.PUSH_PROTOCOL_VERSION) {
            return PushConfigImportResult.Invalid("不支持的推送配置版本")
        }
        val cipher = json.optString("cipher", "").trim()
        if (cipher.isNotEmpty() && cipher != PushCrypto.PUSH_CIPHER_ALGORITHM_ID) {
            return PushConfigImportResult.Invalid("不支持的加密算法：$cipher")
        }

        val rawSecret = json.optString("secretKey", "").trim().ifEmpty { null }
        if (rawSecret != null && !PushCrypto.isValidKeyBase64(rawSecret)) {
            return PushConfigImportResult.Invalid("共享密钥格式无效（需 32 字节 Base64）")
        }

        val rawRelayUrl = json.optString("relayUrl", "").trim().ifEmpty { null }
        if (rawRelayUrl != null && !rawRelayUrl.startsWith("https://")) {
            return PushConfigImportResult.Invalid("中转网关地址必须使用 https://")
        }

        val rawMode = json.optString("mode", "").trim().ifEmpty { null }
        val suggestedMode = rawMode?.let { value ->
            PushChannelMode.entries.firstOrNull { it.wireValue == value }
                ?: return PushConfigImportResult.Invalid("未知的推送通道：$value")
        }

        if (rawSecret == null && rawRelayUrl == null) {
            return PushConfigImportResult.Invalid("配置中未包含可导入的共享密钥或中转网关地址")
        }

        return PushConfigImportResult.Success(
            ImportedPushConfig(
                secretKeyBase64 = rawSecret,
                fcmRelayUrl = rawRelayUrl,
                suggestedMode = suggestedMode,
            ),
        )
    }
}
