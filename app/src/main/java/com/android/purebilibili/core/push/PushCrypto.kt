package com.android.purebilibili.core.push

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPushEnvelope(
    val version: Int = PushCrypto.PUSH_PROTOCOL_VERSION,
    val algorithm: String = PushCrypto.PUSH_CIPHER_ALGORITHM_ID,
    val ivBase64: String,
    val ciphertextBase64: String,
)

object PushCrypto {
    const val PUSH_PROTOCOL_VERSION = 1
    const val PUSH_CIPHER_ALGORITHM_ID = "aes-256-gcm"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_LENGTH_BYTES = 32
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    private val secureRandom = SecureRandom()
    val DEFAULT_AAD: ByteArray = "bilipai-push-v1".toByteArray(Charsets.UTF_8)

    fun generateKeyBase64(): String {
        val bytes = ByteArray(KEY_LENGTH_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun decodeKeyOrNull(keyBase64: String): ByteArray? {
        val trimmed = keyBase64.trim()
        if (trimmed.isEmpty()) return null
        val bytes = try {
            Base64.getDecoder().decode(trimmed)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return bytes.takeIf { it.size == KEY_LENGTH_BYTES }
    }

    fun isValidKeyBase64(keyBase64: String): Boolean = decodeKeyOrNull(keyBase64) != null

    fun encrypt(
        plaintextUtf8: ByteArray,
        keyBase64: String,
        aad: ByteArray = DEFAULT_AAD,
    ): EncryptedPushEnvelope {
        val keyBytes = requireNotNull(decodeKeyOrNull(keyBase64)) {
            "Push encryption key must be 32-byte Base64"
        }
        val iv = ByteArray(IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(TAG_LENGTH_BITS, iv),
        )
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(plaintextUtf8)
        return EncryptedPushEnvelope(
            version = PUSH_PROTOCOL_VERSION,
            algorithm = PUSH_CIPHER_ALGORITHM_ID,
            ivBase64 = Base64.getEncoder().encodeToString(iv),
            ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext),
        )
    }

    @Throws(GeneralSecurityException::class, IllegalArgumentException::class)
    fun decrypt(
        envelope: EncryptedPushEnvelope,
        keyBase64: String,
        aad: ByteArray = DEFAULT_AAD,
    ): ByteArray {
        require(envelope.version == PUSH_PROTOCOL_VERSION) {
            "Unsupported push protocol version: ${envelope.version}"
        }
        require(envelope.algorithm == PUSH_CIPHER_ALGORITHM_ID) {
            "Unsupported push cipher algorithm: ${envelope.algorithm}"
        }
        val keyBytes = requireNotNull(decodeKeyOrNull(keyBase64)) {
            "Invalid local push encryption key"
        }
        val iv = Base64.getDecoder().decode(envelope.ivBase64.trim())
        require(iv.size == IV_LENGTH_BYTES) {
            "AES-GCM IV must be 12 bytes"
        }
        val ciphertext = Base64.getDecoder().decode(envelope.ciphertextBase64.trim())
        require(ciphertext.size > TAG_LENGTH_BITS / 8) {
            "Ciphertext is too short"
        }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, "AES"),
            GCMParameterSpec(TAG_LENGTH_BITS, iv),
        )
        cipher.updateAAD(aad)
        return cipher.doFinal(ciphertext)
    }
}
