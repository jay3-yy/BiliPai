package com.android.purebilibili.core.push

import com.android.purebilibili.core.push.fcm.BiliPaiFirebaseMessagingService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PushCryptoAndPayloadTest {

    @Test
    fun encryptedPayloadRoundTripsWithAes256Gcm() {
        val key = PushCrypto.generateKeyBase64()
        val now = 1_760_000_000L
        val original = PushPayload(
            id = "live_6_$now",
            type = PushNotificationType.LIVE,
            title = "测试主播 开播了",
            body = "点击直达直播间",
            url = "https://live.bilibili.com/6",
            cover = "https://i0.hdslb.com/bfs/live/cover.jpg",
            avatar = "https://i1.hdslb.com/bfs/face/avatar.jpg",
            group = "uid_123",
            timestampSeconds = now,
        )
        val envelope = PushCrypto.encrypt(
            plaintextUtf8 = PushPayloadParser.encodePlaintextJson(original).toByteArray(Charsets.UTF_8),
            keyBase64 = key,
        )
        val wireBytes = PushPayloadParser.encodeEncryptedEnvelopeJson(envelope).toByteArray(Charsets.UTF_8)

        val result = PushPayloadParser.parse(
            rawBytes = wireBytes,
            secretKeyBase64 = key,
            allowPlaintext = false,
            nowEpochSeconds = now + 30L,
        )

        val success = assertIs<PushDecodeResult.Success>(result)
        assertTrue(success.wasEncrypted)
        assertFalse(success.strippedUnsafeUrl)
        assertFalse(success.strippedUnsafeImage)
        assertEquals(original, success.payload)
    }

    @Test
    fun wrongKeyOrTamperedCiphertextIsRejected() {
        val keyA = PushCrypto.generateKeyBase64()
        val keyB = PushCrypto.generateKeyBase64()
        val now = 1_760_000_000L
        val payload = PushPayload(
            id = "video_1",
            type = PushNotificationType.VIDEO,
            title = "新投稿",
            body = "标题",
            timestampSeconds = now,
        )
        val envelope = PushCrypto.encrypt(
            plaintextUtf8 = PushPayloadParser.encodePlaintextJson(payload).toByteArray(Charsets.UTF_8),
            keyBase64 = keyA,
        )
        val wireBytes = PushPayloadParser.encodeEncryptedEnvelopeJson(envelope).toByteArray(Charsets.UTF_8)

        val wrongKeyResult = PushPayloadParser.parse(
            rawBytes = wireBytes,
            secretKeyBase64 = keyB,
            allowPlaintext = false,
            nowEpochSeconds = now,
        )
        assertEquals(PushDecodeResult.Rejected("decryption_failed"), wrongKeyResult)

        val tamperedEnvelope = envelope.copy(
            ciphertextBase64 = envelope.ciphertextBase64.dropLast(2) + "AA",
        )
        val tamperedBytes = PushPayloadParser.encodeEncryptedEnvelopeJson(tamperedEnvelope)
            .toByteArray(Charsets.UTF_8)
        val tamperedResult = PushPayloadParser.parse(
            rawBytes = tamperedBytes,
            secretKeyBase64 = keyA,
            allowPlaintext = false,
            nowEpochSeconds = now,
        )
        assertEquals(PushDecodeResult.Rejected("decryption_failed"), tamperedResult)
    }

    @Test
    fun plaintextIsRejectedUnlessExplicitlyAllowed() {
        val key = PushCrypto.generateKeyBase64()
        val now = 1_760_000_000L
        val plainBytes = PushPayloadParser.encodePlaintextJson(
            PushPayload(
                id = "plain_1",
                type = PushNotificationType.TEST,
                title = "明文消息",
                body = "仅内网调试",
                timestampSeconds = now,
            ),
        ).toByteArray(Charsets.UTF_8)

        val rejected = PushPayloadParser.parse(
            rawBytes = plainBytes,
            secretKeyBase64 = key,
            allowPlaintext = false,
            nowEpochSeconds = now,
        )
        assertEquals(PushDecodeResult.Rejected("plaintext_rejected"), rejected)

        val allowed = PushPayloadParser.parse(
            rawBytes = plainBytes,
            secretKeyBase64 = key,
            allowPlaintext = true,
            nowEpochSeconds = now,
        )
        val success = assertIs<PushDecodeResult.Success>(allowed)
        assertFalse(success.wasEncrypted)
        assertEquals("明文消息", success.payload.title)
    }

    @Test
    fun expiredTimestampIsRejectedToPreventReplay() {
        val key = PushCrypto.generateKeyBase64()
        val now = 1_760_000_000L
        val oldPayload = PushPayload(
            id = "replay_1",
            type = PushNotificationType.MESSAGE,
            title = "私信",
            body = "历史消息",
            timestampSeconds = now - PushPayloadParser.MAX_CLOCK_SKEW_SECONDS - 5L,
        )
        val envelope = PushCrypto.encrypt(
            plaintextUtf8 = PushPayloadParser.encodePlaintextJson(oldPayload).toByteArray(Charsets.UTF_8),
            keyBase64 = key,
        )
        val wireBytes = PushPayloadParser.encodeEncryptedEnvelopeJson(envelope).toByteArray(Charsets.UTF_8)

        val result = PushPayloadParser.parse(
            rawBytes = wireBytes,
            secretKeyBase64 = key,
            allowPlaintext = false,
            nowEpochSeconds = now,
        )
        assertEquals(PushDecodeResult.Rejected("timestamp_expired"), result)
    }

    @Test
    fun unsafeUrlsAndNonBilibiliImagesAreStripped() {
        val key = PushCrypto.generateKeyBase64()
        val now = 1_760_000_000L

        assertNull(PushPayloadParser.sanitizeTargetUrl("bilipai://plugin/install?url=https://evil.test/x.bpx"))
        assertNull(PushPayloadParser.sanitizeTargetUrl("https://bilipai.app/plugin/install?url=https://evil.test/x.bpx"))
        assertNull(PushPayloadParser.sanitizeTargetUrl("https://evil.example.com/phishing"))
        assertEquals(
            "https://www.bilibili.com/video/BV1GJ411x7h7",
            PushPayloadParser.sanitizeTargetUrl("https://www.bilibili.com/video/BV1GJ411x7h7"),
        )
        assertEquals("bilipai://dynamic", PushPayloadParser.sanitizeTargetUrl("bilipai://dynamic"))

        assertNull(PushPayloadParser.sanitizeImageUrl("http://i0.hdslb.com/bfs/archive/a.jpg"))
        assertNull(PushPayloadParser.sanitizeImageUrl("https://tracker.evil.com/pixel.png"))
        assertEquals(
            "https://i0.hdslb.com/bfs/archive/a.jpg",
            PushPayloadParser.sanitizeImageUrl("https://i0.hdslb.com/bfs/archive/a.jpg"),
        )

        val unsafePayload = PushPayload(
            id = "unsafe_1",
            type = PushNotificationType.VIDEO,
            title = "测试",
            body = "正文",
            url = "bilipai://plugin/install?url=https://evil.test/a.bpx",
            cover = "https://tracker.evil.com/cover.jpg",
            timestampSeconds = now,
        )
        val envelope = PushCrypto.encrypt(
            plaintextUtf8 = PushPayloadParser.encodePlaintextJson(unsafePayload).toByteArray(Charsets.UTF_8),
            keyBase64 = key,
        )
        val result = PushPayloadParser.parse(
            rawBytes = PushPayloadParser.encodeEncryptedEnvelopeJson(envelope).toByteArray(Charsets.UTF_8),
            secretKeyBase64 = key,
            allowPlaintext = false,
            nowEpochSeconds = now,
        )
        val success = assertIs<PushDecodeResult.Success>(result)
        assertTrue(success.strippedUnsafeUrl)
        assertTrue(success.strippedUnsafeImage)
        assertNull(success.payload.url)
        assertNull(success.payload.cover)
    }

    @Test
    fun fcmDataMapExtractionSupportsDirectPayloadAndFlatFields() {
        val direct = BiliPaiFirebaseMessagingService.extractRawPayloadBytes(
            mapOf("payload" to """{"v":1,"enc":"aes-256-gcm","iv":"abc","ct":"def"}"""),
        )
        assertEquals("""{"v":1,"enc":"aes-256-gcm","iv":"abc","ct":"def"}""", direct?.toString(Charsets.UTF_8))

        val flat = BiliPaiFirebaseMessagingService.extractRawPayloadBytes(
            mapOf("v" to "1", "enc" to "aes-256-gcm", "iv" to "abc", "ct" to "def"),
        )
        assertTrue(flat?.toString(Charsets.UTF_8)?.contains("\"enc\":\"aes-256-gcm\"") == true)
    }

    @Test
    fun qrConfigExportAndImportRoundTripsAndValidatesSecurityBounds() {
        val key = PushCrypto.generateKeyBase64()
        val exported = PushConfigTransfer.encodeQrPayload(
            CustomPushConfig(
                mode = PushChannelMode.FCM_RELAY,
                fcmToken = "device_token_123",
                fcmRelayUrl = "https://relay.example.workers.dev/push",
                secretKeyBase64 = key,
            ),
        )

        val imported = assertIs<PushConfigImportResult.Success>(
            PushConfigTransfer.parseImportedPayload(exported),
        )
        assertEquals(key, imported.config.secretKeyBase64)
        assertEquals("https://relay.example.workers.dev/push", imported.config.fcmRelayUrl)
        assertEquals(PushChannelMode.FCM_RELAY, imported.config.suggestedMode)

        assertIs<PushConfigImportResult.Invalid>(
            PushConfigTransfer.parseImportedPayload("""{"v":1,"secretKey":"too-short"}"""),
        )
        assertIs<PushConfigImportResult.Invalid>(
            PushConfigTransfer.parseImportedPayload("""{"v":1,"relayUrl":"http://insecure.example/push"}"""),
        )
        assertIs<PushConfigImportResult.Invalid>(
            PushConfigTransfer.parseImportedPayload("https://www.bilibili.com/video/BV1GJ411x7h7"),
        )
    }
}
