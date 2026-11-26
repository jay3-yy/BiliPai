package com.android.purebilibili.feature.login

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Encrypts an arbitrary serializable payload for the receiver key carried by a request QR. */
object BiliPaiTransferCrypto {
    private const val RSA = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
    private const val AES = "AES/GCM/NoPadding"
    private const val SIGNATURE = "SHA256withRSA"
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }

    fun encrypt(
        sender: KeyPair,
        senderDeviceId: String,
        request: BiliPaiTransferRequest,
        bundle: BiliPaiSessionBundle,
        now: Long = System.currentTimeMillis(),
    ): BiliPaiTransferEnvelope =
        encryptSerialized(sender, senderDeviceId, request, BiliPaiSessionBundle.serializer(), bundle, now)

    fun decrypt(receiverPrivate: PrivateKey, request: BiliPaiTransferRequest,
                envelope: BiliPaiTransferEnvelope, now: Long = System.currentTimeMillis()): BiliPaiSessionBundle =
        decryptSerialized(receiverPrivate, request, envelope, BiliPaiSessionBundle.serializer(), now)

    fun encryptPayload(
        sender: KeyPair,
        senderDeviceId: String,
        request: BiliPaiTransferRequest,
        payload: BiliPaiTransferPayload,
        now: Long = System.currentTimeMillis(),
    ): BiliPaiTransferEnvelope =
        encryptSerialized(sender, senderDeviceId, request, BiliPaiTransferPayload.serializer(), payload, now)

    fun decryptPayload(receiverPrivate: PrivateKey, request: BiliPaiTransferRequest,
                envelope: BiliPaiTransferEnvelope, now: Long = System.currentTimeMillis()): BiliPaiTransferPayload =
        decryptSerialized(receiverPrivate, request, envelope, BiliPaiTransferPayload.serializer(), now)

    private fun <T> encryptSerialized(
        sender: KeyPair,
        senderDeviceId: String,
        request: BiliPaiTransferRequest,
        serializer: KSerializer<T>,
        value: T,
        now: Long,
    ): BiliPaiTransferEnvelope {
        require(request.expiresAt > now) { "传输请求已过期" }
        val rawPlaintext = json.encodeToString(serializer, value).toByteArray(StandardCharsets.UTF_8)
        val plaintext = compressIfSmaller(rawPlaintext)
        require(plaintext.size <= MAX_PAYLOAD_BYTES) { "传输内容过大，请减少所选内容后重试" }
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES).apply {
            init(Cipher.ENCRYPT_MODE, aes, GCMParameterSpec(128, iv))
        }
        val ciphertext = cipher.doFinal(plaintext)
        val wrapped = Cipher.getInstance(RSA).run {
            init(Cipher.ENCRYPT_MODE, publicKey(request.receiverPublicKey))
            doFinal(aes.encoded)
        }
        val senderPublic = b64(sender.public.encoded)
        val canonical = canonical(request.transferId, senderDeviceId, senderPublic,
            b64(wrapped), b64(iv), b64(ciphertext), request.expiresAt)
        val signature = Signature.getInstance(SIGNATURE).run {
            initSign(sender.private); update(canonical.toByteArray(StandardCharsets.UTF_8)); sign()
        }
        return BiliPaiTransferEnvelope(1, request.transferId, senderDeviceId, senderPublic,
            b64(wrapped), b64(iv), b64(ciphertext), b64(signature), request.expiresAt)
    }

    private fun <T> decryptSerialized(
        receiverPrivate: PrivateKey,
        request: BiliPaiTransferRequest,
        envelope: BiliPaiTransferEnvelope,
        serializer: KSerializer<T>,
        now: Long,
    ): T {
        require(envelope.version == 1 && envelope.transferId == request.transferId) { "传输会话不匹配" }
        require(envelope.expiresAt > now) { "传输数据已过期" }
        require(envelope.ciphertext.length <= MAX_CIPHERTEXT_FIELD_LENGTH) { "加密数据过大" }
        val sender = publicKey(envelope.senderPublicKey)
        val canonical = canonical(envelope.transferId, envelope.senderDeviceId, envelope.senderPublicKey,
            envelope.wrappedKey, envelope.iv, envelope.ciphertext, envelope.expiresAt)
        require(Signature.getInstance(SIGNATURE).run {
            initVerify(sender); update(canonical.toByteArray(StandardCharsets.UTF_8)); verify(unb64(envelope.signature))
        }) { "传输签名校验失败" }
        val aes = Cipher.getInstance(RSA).run {
            init(Cipher.DECRYPT_MODE, receiverPrivate); doFinal(unb64(envelope.wrappedKey))
        }
        val decrypted = Cipher.getInstance(AES).run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(aes, "AES"), GCMParameterSpec(128, unb64(envelope.iv)))
            doFinal(unb64(envelope.ciphertext))
        }
        val plaintext = decompressIfGzipped(decrypted)
        return json.decodeFromString(serializer, String(plaintext, StandardCharsets.UTF_8))
    }

    private fun compressIfSmaller(raw: ByteArray): ByteArray {
        if (raw.size < GZIP_MIN_BYTES) return raw
        val out = ByteArrayOutputStream(raw.size / 2)
        GZIPOutputStream(out).use { it.write(raw) }
        val compressed = out.toByteArray()
        return if (compressed.size < raw.size) compressed else raw
    }

    private fun decompressIfGzipped(bytes: ByteArray): ByteArray {
        val isGzip = bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()
        if (!isGzip) return bytes
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        GZIPInputStream(ByteArrayInputStream(bytes)).use { gzip ->
            while (true) {
                val read = gzip.read(buffer)
                if (read <= 0) break
                total += read
                require(total <= MAX_UNCOMPRESSED_BYTES) { "解压后的传输内容过大" }
                out.write(buffer, 0, read)
            }
        }
        return out.toByteArray()
    }

    private fun publicKey(value: String): PublicKey = java.security.KeyFactory.getInstance("RSA")
        .generatePublic(java.security.spec.X509EncodedKeySpec(unb64(value)))
    private fun canonical(vararg values: Any): String = values.joinToString(".")
    private fun b64(value: ByteArray): String = Base64.encodeToString(value, Base64.NO_WRAP or Base64.URL_SAFE)
    private fun unb64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP or Base64.URL_SAFE)

    // Chunked QR transport lifts the single-QR capacity; the caps remain as sanity limits.
    // 128 chunks x 1_500 chars of base64url carry at most ~144k envelope chars.
    private const val MAX_CIPHERTEXT_FIELD_LENGTH = 200_000
    // Leave room for AES/RSA fields and both envelope base64 layers inside 128 chunks.
    private const val MAX_PAYLOAD_BYTES = 79_000
    private const val GZIP_MIN_BYTES = 512
    private const val MAX_UNCOMPRESSED_BYTES = 512_000
}
