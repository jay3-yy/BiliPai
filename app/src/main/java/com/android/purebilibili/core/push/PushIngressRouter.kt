package com.android.purebilibili.core.push

import android.content.Context

data class PushIngressOutcome(
    val accepted: Boolean,
    val detail: String,
)

object PushIngressRouter {
    suspend fun handleIncoming(
        context: Context,
        source: PushIngressSource,
        rawBytes: ByteArray,
    ): PushIngressOutcome {
        val appContext = context.applicationContext
        val config = PushConfigStore.ensureInitialized(appContext)

        // 仅允许当前选中的远程通道或本地自测通过，避免切换通道后残留消息重复弹出。
        if (source == PushIngressSource.UNIFIED_PUSH && config.mode != PushChannelMode.UNIFIED_PUSH) {
            return recordRejected(appContext, source, "channel_inactive")
        }
        if (source == PushIngressSource.FCM && config.mode != PushChannelMode.FCM_RELAY) {
            return recordRejected(appContext, source, "channel_inactive")
        }

        val decodeResult = PushPayloadParser.parse(
            rawBytes = rawBytes,
            secretKeyBase64 = config.secretKeyBase64,
            allowPlaintext = config.allowPlaintext || source == PushIngressSource.LOCAL_TEST,
        )
        val parsed = when (decodeResult) {
            is PushDecodeResult.Rejected -> {
                return recordRejected(appContext, source, decodeResult.reason)
            }
            is PushDecodeResult.Success -> decodeResult
        }

        if (PushConfigStore.isDuplicateAndRecord(appContext, parsed.payload.id)) {
            return recordRejected(appContext, source, "duplicate:${parsed.payload.id}")
        }

        val posted = BiliPaiNotificationDispatcher.dispatch(appContext, parsed.payload)
        if (!posted) {
            return recordRejected(appContext, source, "notification_permission_or_channel_off")
        }

        val flags = buildList {
            add(if (parsed.wasEncrypted) "AES-GCM" else "明文")
            if (parsed.strippedUnsafeUrl) add("已过滤非白名单链接")
            if (parsed.strippedUnsafeImage) add("已过滤非白名单图片")
        }.joinToString(" · ")
        val summary = "${parsed.payload.title} ($flags)"
        PushConfigStore.appendLog(appContext, source, accepted = true, summary = summary)
        return PushIngressOutcome(accepted = true, detail = summary)
    }

    suspend fun runLocalSelfTest(
        context: Context,
        encryptedLoopback: Boolean,
    ): PushIngressOutcome {
        val appContext = context.applicationContext
        val config = PushConfigStore.ensureInitialized(appContext)
        val nowSeconds = System.currentTimeMillis() / 1000L
        val samplePayload = PushPayload(
            id = "test_${if (encryptedLoopback) "enc" else "plain"}_$nowSeconds",
            type = PushNotificationType.TEST,
            title = if (encryptedLoopback) "自建推送 · 加密回环测试通过" else "自建推送 · 本地测试通知",
            body = if (encryptedLoopback) {
                "已通过本地 AES-256-GCM 加密与解密验签，点按将打开动态页"
            } else {
                "通知权限、渠道与深度链接跳转正常，点按将打开动态页"
            },
            url = "bilipai://dynamic",
            timestampSeconds = nowSeconds,
        )
        val plaintextBytes = PushPayloadParser.encodePlaintextJson(samplePayload).toByteArray(Charsets.UTF_8)
        val wireBytes = if (encryptedLoopback) {
            val envelope = PushCrypto.encrypt(plaintextBytes, config.secretKeyBase64)
            PushPayloadParser.encodeEncryptedEnvelopeJson(envelope).toByteArray(Charsets.UTF_8)
        } else {
            plaintextBytes
        }
        return handleIncoming(
            context = appContext,
            source = PushIngressSource.LOCAL_TEST,
            rawBytes = wireBytes,
        )
    }

    private suspend fun recordRejected(
        context: Context,
        source: PushIngressSource,
        reason: String,
    ): PushIngressOutcome {
        PushConfigStore.appendLog(context, source, accepted = false, summary = reason)
        return PushIngressOutcome(accepted = false, detail = reason)
    }
}
