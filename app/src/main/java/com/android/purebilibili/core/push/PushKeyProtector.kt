package com.android.purebilibili.core.push

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 使用 AndroidKeyStore 包装本地推送对称密钥，避免明文密钥直接落盘。
 */
internal object PushKeyProtector {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "bilipai_custom_push_master_key_v1"
    private const val PREFIX = "ks1:"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128

    fun wrap(plainSecretBase64: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateMasterKey())
        val ciphertext = cipher.doFinal(plainSecretBase64.toByteArray(Charsets.UTF_8))
        val ivBase64 = Base64.getEncoder().encodeToString(cipher.iv)
        val ctBase64 = Base64.getEncoder().encodeToString(ciphertext)
        return "$PREFIX$ivBase64:$ctBase64"
    }

    fun unwrap(stored: String): String? {
        if (stored.isBlank()) return null
        if (!stored.startsWith(PREFIX)) return null
        val parts = stored.removePrefix(PREFIX).split(':', limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            val iv = Base64.getDecoder().decode(parts[0])
            val ct = Base64.getDecoder().decode(parts[1])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateMasterKey(),
                GCMParameterSpec(TAG_LENGTH_BITS, iv),
            )
            cipher.doFinal(ct).toString(Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }
}
