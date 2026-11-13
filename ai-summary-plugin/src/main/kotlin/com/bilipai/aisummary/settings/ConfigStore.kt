package com.bilipai.aisummary.settings

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.bilipai.aisummary.model.AiSummaryConfig
import com.bilipai.aisummary.model.AiSummaryDefaults
import com.bilipai.aisummary.model.SummaryDetailLevel
import com.bilipai.aisummary.model.SummaryStyle
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 配置持久化。
 *
 * 安全约定（很重要）：
 * - apiKey 只用 Android Keystore 里的 AES-GCM 密钥加密后存密文，绝不写明文、绝不进日志；
 * - 其余非敏感字段存普通 SharedPreferences JSON；
 * - 宿主 App 若开启 android:allowBackup，需在备份规则里排除本文件（见 README）。
 */
interface ConfigStore {
    fun load(): AiSummaryConfig
    fun save(config: AiSummaryConfig)
    fun clearKey()
}

/** 用于 JVM 单元测试 / 预览的内存实现。 */
class InMemoryConfigStore(initial: AiSummaryConfig = AiSummaryConfig()) : ConfigStore {
    @Volatile
    private var current: AiSummaryConfig = initial
    override fun load(): AiSummaryConfig = current
    override fun save(config: AiSummaryConfig) { current = config }
    override fun clearKey() { current = current.copy(apiKey = "") }
}

class AndroidConfigStore(context: Context) : ConfigStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): AiSummaryConfig {
        val raw = prefs.getString(KEY_CONFIG_JSON, null) ?: return AiSummaryConfig()
        return try {
            val o = JSONObject(raw)
            AiSummaryConfig(
                providerId = o.optString("providerId", AiSummaryDefaults.PROVIDER_ID),
                baseUrl = o.optString("baseUrl", AiSummaryDefaults.BASE_URL),
                apiKey = decrypt(o.optString("keyCipher", "")),
                model = o.optString("model", AiSummaryDefaults.MODEL),
                temperature = o.optDouble("temperature", 0.3).toFloat(),
                maxTokensPerSegment = o.optInt("maxTokensPerSegment", 1600),
                chunkCharBudget = o.optInt("chunkCharBudget", 4500),
                overlapChars = o.optInt("overlapChars", 160),
                maxConcurrency = o.optInt("maxConcurrency", 2).coerceIn(1, 6),
                timeoutSeconds = o.optInt("timeoutSeconds", 90).coerceIn(10, 600),
                maxRetries = o.optInt("maxRetries", 2).coerceIn(0, 5),
                style = SummaryStyle.fromId(o.optString("style")),
                detailLevel = SummaryDetailLevel.fromId(o.optString("detailLevel")),
                preferOfficialSummary = o.optBoolean("preferOfficialSummary", false),
                fallbackToOfficial = o.optBoolean("fallbackToOfficial", true),
                cacheEnabled = o.optBoolean("cacheEnabled", true),
                allowForceRefresh = o.optBoolean("allowForceRefresh", true),
                saveAsNoteDraft = o.optBoolean("saveAsNoteDraft", true),
                asrEnabled = o.optBoolean("asrEnabled", false),
                asrBaseUrl = o.optString("asrBaseUrl", "https://api.groq.com/openai/v1"),
                asrApiKey = decrypt(o.optString("asrKeyCipher", "")),
                asrModel = o.optString("asrModel", "whisper-large-v3-turbo"),
                asrLanguage = o.optString("asrLanguage", "zh"),
                asrProxy = o.optString("asrProxy", ""),
                asrMaxBytes = o.optLong("asrMaxBytes", 25L * 1024 * 1024),
                asrTimeoutSeconds = o.optInt("asrTimeoutSeconds", 180).coerceIn(30, 900)
            )
        } catch (e: Exception) {
            // 配置损坏时不要崩，退回默认值；Key 丢失时要求用户重新填写。
            AiSummaryConfig()
        }
    }

    override fun save(config: AiSummaryConfig) {
        val existingJson = try {
            JSONObject(prefs.getString(KEY_CONFIG_JSON, "{}") ?: "{}")
        } catch (e: Exception) { JSONObject() }
        val existingCipher = existingJson.optString("keyCipher", "")
        val existingAsrCipher = existingJson.optString("asrKeyCipher", "")
        val o = JSONObject().apply {
            put("providerId", config.providerId)
            put("baseUrl", com.bilipai.aisummary.util.InputNormalizer.normalizeBaseUrl(config.baseUrl))
            put("model", com.bilipai.aisummary.util.InputNormalizer.normalizeModel(config.model))
            put("temperature", config.temperature.toDouble())
            put("maxTokensPerSegment", config.maxTokensPerSegment)
            put("chunkCharBudget", config.chunkCharBudget)
            put("overlapChars", config.overlapChars)
            put("maxConcurrency", config.maxConcurrency)
            put("timeoutSeconds", config.timeoutSeconds)
            put("maxRetries", config.maxRetries)
            put("style", config.style.id)
            put("detailLevel", config.detailLevel.id)
            put("preferOfficialSummary", config.preferOfficialSummary)
            put("fallbackToOfficial", config.fallbackToOfficial)
            put("cacheEnabled", config.cacheEnabled)
            put("allowForceRefresh", config.allowForceRefresh)
            put("saveAsNoteDraft", config.saveAsNoteDraft)
            put("asrEnabled", config.asrEnabled)
            put("asrBaseUrl", com.bilipai.aisummary.util.InputNormalizer.normalizeBaseUrl(config.asrBaseUrl))
            put("asrModel", com.bilipai.aisummary.util.InputNormalizer.normalizeModel(config.asrModel))
            put("asrLanguage", config.asrLanguage)
            put("asrProxy", config.asrProxy.trim())
            put("asrMaxBytes", config.asrMaxBytes)
            put("asrTimeoutSeconds", config.asrTimeoutSeconds)
            // ASR Key 与 LLM Key 同等对待：只存密文，加密失败则保留旧密文
            if (config.asrApiKey.isNotBlank()) {
                val c = encrypt(config.asrApiKey)
                if (c.isNotBlank()) put("asrKeyCipher", c)
                else if (existingAsrCipher.isNotBlank()) put("asrKeyCipher", existingAsrCipher)
            } else if (existingAsrCipher.isNotBlank()) {
                put("asrKeyCipher", existingAsrCipher)
            }
            // 只有新填了 Key 才重新加密；只改温度/模型时复用已有密文，不抹掉 Key。
            // 加密失败（Keystore 异常）时保留旧密文，绝不写入空串覆盖。
            if (config.apiKey.isNotBlank()) {
                val cipher = encrypt(config.apiKey)
                if (cipher.isNotBlank()) put("keyCipher", cipher)
                else if (existingCipher.isNotBlank()) put("keyCipher", existingCipher)
            } else if (existingCipher.isNotBlank()) {
                put("keyCipher", existingCipher)
            }
        }
        prefs.edit().putString(KEY_CONFIG_JSON, o.toString()).apply()
    }

    override fun clearKey() {
        val o = try {
            JSONObject(prefs.getString(KEY_CONFIG_JSON, "{}") ?: "{}")
        } catch (e: Exception) { JSONObject() }
        o.remove("keyCipher")
        o.remove("asrKeyCipher")
        prefs.edit().putString(KEY_CONFIG_JSON, o.toString()).apply()
    }

    // ---------------- Android Keystore AES-GCM ----------------

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        if (plain.isBlank()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val merged = ByteArray(iv.size + body.size).also {
                iv.copyInto(it, 0)
                body.copyInto(it, iv.size)
            }
            Base64.encodeToString(merged, Base64.NO_WRAP)
        } catch (e: Exception) {
            "" // 加密失败绝不退回明文存储
        }
    }

    private fun decrypt(cipherText: String): String {
        if (cipherText.isBlank()) return ""
        return try {
            val merged = Base64.decode(cipherText, Base64.NO_WRAP)
            if (merged.size <= GCM_IV_LENGTH) return ""
            val iv = merged.copyOfRange(0, GCM_IV_LENGTH)
            val body = merged.copyOfRange(GCM_IV_LENGTH, merged.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            "" // 密钥失效（如用户清过数据）时要求重新填 Key
        }
    }

    private companion object {
        const val PREFS_NAME = "bilipai_ai_summary_config"
        const val KEY_CONFIG_JSON = "config_json"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "bilipai_aisummary_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
