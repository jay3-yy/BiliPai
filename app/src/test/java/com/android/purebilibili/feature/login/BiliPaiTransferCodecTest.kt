package com.android.purebilibili.feature.login

import java.security.KeyPairGenerator
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
class BiliPaiTransferCodecTest {
    @Test
    fun request_roundTrips_andValidatesReceiverKeyFingerprint() {
        val now = 1_700_000_000_000L
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val fingerprint = java.util.Base64.getUrlEncoder()
            .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(key.public.encoded))
        val request = BiliPaiTransferRequest(
            version = 1,
            transferId = UUID.randomUUID().toString(),
            receiverDeviceId = fingerprint,
            receiverPublicKey = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.public.encoded),
            expiresAt = now + 60_000,
        )
        assertEquals(
            request,
            BiliPaiTransferCodec.decodeRequest(BiliPaiTransferCodec.encodeRequest(request), now = now)
        )
        assertThrows(IllegalArgumentException::class.java) {
            BiliPaiTransferCodec.decodeRequest(
                BiliPaiTransferCodec.encodeRequest(request.copy(receiverDeviceId = "wrong-fingerprint")),
                now = now
            )
        }
    }

    @Test
    fun expiredRequest_isRejected() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val request = BiliPaiTransferRequest(
            version = 1,
            transferId = "id",
            receiverDeviceId = java.util.Base64.getUrlEncoder()
                .encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(key.public.encoded)),
            receiverPublicKey = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(key.public.encoded),
            expiresAt = 0L,
        )
        assertThrows(IllegalArgumentException::class.java) {
            BiliPaiTransferCodec.decodeRequest(BiliPaiTransferCodec.encodeRequest(request), now = 0L)
        }
    }
}
