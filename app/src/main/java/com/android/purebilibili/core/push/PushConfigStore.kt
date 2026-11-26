package com.android.purebilibili.core.push

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.customPushDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "custom_push_settings",
)

data class PushDeliveryLogEntry(
    val timestampMs: Long,
    val source: String,
    val accepted: Boolean,
    val summary: String,
)

data class CustomPushConfig(
    val mode: PushChannelMode = PushChannelMode.DISABLED,
    val allowPlaintext: Boolean = false,
    val unifiedPushDistributor: String = "",
    val unifiedPushInstanceToken: String = "",
    val unifiedPushEndpoint: String = "",
    val fcmToken: String = "",
    val fcmRelayUrl: String = "",
    val secretKeyBase64: String = "",
    val deliveryLogs: List<PushDeliveryLogEntry> = emptyList(),
)

object PushConfigStore {
    private val KEY_MODE = stringPreferencesKey("mode")
    private val KEY_ALLOW_PLAINTEXT = booleanPreferencesKey("allow_plaintext")
    private val KEY_UP_DISTRIBUTOR = stringPreferencesKey("up_distributor")
    private val KEY_UP_INSTANCE_TOKEN = stringPreferencesKey("up_instance_token")
    private val KEY_UP_ENDPOINT = stringPreferencesKey("up_endpoint")
    private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
    private val KEY_FCM_RELAY_URL = stringPreferencesKey("fcm_relay_url")
    private val KEY_WRAPPED_SECRET = stringPreferencesKey("wrapped_secret")
    private val KEY_RECENT_IDS = stringPreferencesKey("recent_event_ids")
    private val KEY_DELIVERY_LOGS = stringPreferencesKey("delivery_logs")

    private const val MAX_RECENT_IDS = 50
    private const val MAX_DELIVERY_LOGS = 20

    fun observeConfig(context: Context): Flow<CustomPushConfig> {
        val appContext = context.applicationContext
        return appContext.customPushDataStore.data.map(::decodeConfig)
    }

    suspend fun getConfig(context: Context): CustomPushConfig =
        observeConfig(context).first()

    suspend fun ensureInitialized(context: Context): CustomPushConfig {
        val appContext = context.applicationContext
        appContext.customPushDataStore.edit { prefs ->
            val currentSecret = prefs[KEY_WRAPPED_SECRET]?.let(PushKeyProtector::unwrap)
            if (currentSecret == null || !PushCrypto.isValidKeyBase64(currentSecret)) {
                prefs[KEY_WRAPPED_SECRET] = PushKeyProtector.wrap(PushCrypto.generateKeyBase64())
            }
            if (prefs[KEY_UP_INSTANCE_TOKEN].isNullOrBlank()) {
                prefs[KEY_UP_INSTANCE_TOKEN] = UUID.randomUUID().toString()
            }
        }
        return getConfig(appContext)
    }

    suspend fun setMode(context: Context, mode: PushChannelMode) {
        context.applicationContext.customPushDataStore.edit { prefs ->
            prefs[KEY_MODE] = mode.wireValue
        }
    }

    suspend fun setAllowPlaintext(context: Context, allow: Boolean) {
        context.applicationContext.customPushDataStore.edit { prefs ->
            prefs[KEY_ALLOW_PLAINTEXT] = allow
        }
    }

    suspend fun setUnifiedPushDistributor(context: Context, distributorPackage: String) {
        context.applicationContext.customPushDataStore.edit { prefs ->
            if (prefs[KEY_UP_DISTRIBUTOR] != distributorPackage) {
                prefs[KEY_UP_DISTRIBUTOR] = distributorPackage
                prefs[KEY_UP_ENDPOINT] = ""
            }
            if (prefs[KEY_UP_INSTANCE_TOKEN].isNullOrBlank()) {
                prefs[KEY_UP_INSTANCE_TOKEN] = UUID.randomUUID().toString()
            }
        }
    }

    suspend fun setUnifiedPushEndpoint(context: Context, endpoint: String) {
        context.applicationContext.customPushDataStore.edit { prefs ->
            prefs[KEY_UP_ENDPOINT] = endpoint.trim()
        }
    }

    suspend fun setFcmToken(context: Context, token: String) {
        context.applicationContext.customPushDataStore.edit { prefs ->
            prefs[KEY_FCM_TOKEN] = token.trim()
        }
    }

    suspend fun setFcmRelayUrl(context: Context, relayUrl: String) {
        context.applicationContext.customPushDataStore.edit { prefs ->
            prefs[KEY_FCM_RELAY_URL] = relayUrl.trim()
        }
    }

    suspend fun rotateSecretKey(context: Context): String {
        val newKey = PushCrypto.generateKeyBase64()
        context.applicationContext.customPushDataStore.edit { prefs ->
            prefs[KEY_WRAPPED_SECRET] = PushKeyProtector.wrap(newKey)
            prefs[KEY_RECENT_IDS] = "[]"
        }
        return newKey
    }

    suspend fun importConfig(context: Context, imported: ImportedPushConfig) {
        context.applicationContext.customPushDataStore.edit { prefs ->
            imported.secretKeyBase64?.let { key ->
                prefs[KEY_WRAPPED_SECRET] = PushKeyProtector.wrap(key)
                prefs[KEY_RECENT_IDS] = "[]"
            }
            imported.fcmRelayUrl?.let { relay ->
                prefs[KEY_FCM_RELAY_URL] = relay
            }
        }
    }

    /**
     * 原子检查事件 ID 是否已处理过；若未见过则写入滑动窗口并返回 false。
     */
    suspend fun isDuplicateAndRecord(context: Context, eventId: String): Boolean {
        var duplicated = false
        context.applicationContext.customPushDataStore.edit { prefs ->
            val ids = decodeStringList(prefs[KEY_RECENT_IDS]).toMutableList()
            if (eventId in ids) {
                duplicated = true
            } else {
                ids.add(eventId)
                while (ids.size > MAX_RECENT_IDS) {
                    ids.removeAt(0)
                }
                prefs[KEY_RECENT_IDS] = JSONArray(ids).toString()
            }
        }
        return duplicated
    }

    suspend fun appendLog(
        context: Context,
        source: PushIngressSource,
        accepted: Boolean,
        summary: String,
    ) {
        val entry = PushDeliveryLogEntry(
            timestampMs = System.currentTimeMillis(),
            source = source.label,
            accepted = accepted,
            summary = summary.take(160),
        )
        context.applicationContext.customPushDataStore.edit { prefs ->
            val list = decodeLogs(prefs[KEY_DELIVERY_LOGS]).toMutableList()
            list.add(0, entry)
            while (list.size > MAX_DELIVERY_LOGS) {
                list.removeAt(list.lastIndex)
            }
            prefs[KEY_DELIVERY_LOGS] = encodeLogs(list)
        }
    }

    suspend fun clearLogs(context: Context) {
        context.applicationContext.customPushDataStore.edit { prefs ->
            prefs[KEY_DELIVERY_LOGS] = "[]"
        }
    }

    fun exportServerConfigJson(config: CustomPushConfig): String {
        return JSONObject().apply {
            put("v", PushCrypto.PUSH_PROTOCOL_VERSION)
            put("mode", config.mode.wireValue)
            put("cipher", PushCrypto.PUSH_CIPHER_ALGORITHM_ID)
            put("aad", String(PushCrypto.DEFAULT_AAD, Charsets.UTF_8))
            put("secretKey", config.secretKeyBase64)
            when (config.mode) {
                PushChannelMode.UNIFIED_PUSH -> {
                    put("endpoint", config.unifiedPushEndpoint)
                }
                PushChannelMode.FCM_RELAY -> {
                    put("fcmToken", config.fcmToken)
                    if (config.fcmRelayUrl.isNotBlank()) {
                        put("relayUrl", config.fcmRelayUrl)
                    }
                }
                PushChannelMode.DISABLED -> Unit
            }
        }.toString(2)
    }

    private fun decodeConfig(prefs: Preferences): CustomPushConfig {
        return CustomPushConfig(
            mode = PushChannelMode.fromWireValue(prefs[KEY_MODE]),
            allowPlaintext = prefs[KEY_ALLOW_PLAINTEXT] ?: false,
            unifiedPushDistributor = prefs[KEY_UP_DISTRIBUTOR].orEmpty(),
            unifiedPushInstanceToken = prefs[KEY_UP_INSTANCE_TOKEN].orEmpty(),
            unifiedPushEndpoint = prefs[KEY_UP_ENDPOINT].orEmpty(),
            fcmToken = prefs[KEY_FCM_TOKEN].orEmpty(),
            fcmRelayUrl = prefs[KEY_FCM_RELAY_URL].orEmpty(),
            secretKeyBase64 = prefs[KEY_WRAPPED_SECRET]?.let(PushKeyProtector::unwrap).orEmpty(),
            deliveryLogs = decodeLogs(prefs[KEY_DELIVERY_LOGS]),
        )
    }

    private fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    array.optString(i).takeIf { it.isNotEmpty() }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun decodeLogs(raw: String?): List<PushDeliveryLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        PushDeliveryLogEntry(
                            timestampMs = obj.optLong("t", 0L),
                            source = obj.optString("s", ""),
                            accepted = obj.optBoolean("ok", false),
                            summary = obj.optString("m", ""),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeLogs(logs: List<PushDeliveryLogEntry>): String {
        val array = JSONArray()
        for (entry in logs) {
            array.put(
                JSONObject().apply {
                    put("t", entry.timestampMs)
                    put("s", entry.source)
                    put("ok", entry.accepted)
                    put("m", entry.summary)
                },
            )
        }
        return array.toString()
    }
}
