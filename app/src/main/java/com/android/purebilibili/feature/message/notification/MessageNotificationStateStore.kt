package com.android.purebilibili.feature.message.notification

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.store.MultiProcessPreferences
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.messageNotificationStateStore
    get() = MultiProcessPreferences.get(this, "message_notification_state")

@Serializable
internal data class AccountNotificationState(
    val initialized: Set<String> = emptySet(),
    // Ignore the legacy end-of-scan maximum on upgrade; null forces a safe full scan.
    val dynamicProbeBaseline: String? = null,
    // A present empty value means that this UP was scanned successfully but had no dynamics.
    val dynamicUpBaselines: Map<Long, String> = emptyMap(),
    // Includes overflow entries represented only by the Android group summary.
    val pendingDynamicIds: Map<String, Long> = emptyMap(),
    val pendingPrivateSessionKeys: Set<String> = emptySet(),
    // Null means no count baseline yet; old ID fields are ignored when decoding saved state.
    val replyUnreadCount: Int? = null,
    val atUnreadCount: Int? = null,
    val likeUnreadCount: Int? = null,
    val seenSystemCursors: List<Long> = emptyList(),
    val sessionMsgKeys: Map<String, Long> = emptyMap(),
    val liveSessions: Map<Long, Long> = emptyMap(),
)

internal object MessageNotificationStateStore {
    private val statesKey = stringPreferencesKey("states_json")
    private val lastCheckMid = longPreferencesKey("last_check_mid")
    private val lastCheckAt = longPreferencesKey("last_check_elapsed_ms")
    private val lastCheckBoot = longPreferencesKey("last_check_boot")

    suspend fun checkedRecently(context: Context, mid: Long): Boolean {
        val prefs = context.applicationContext.messageNotificationStateStore.data.first()
        val boot = android.provider.Settings.Global.getInt(context.contentResolver, "boot_count", -1).toLong()
        return shouldSkipRecentNotificationCheck(
            mid, prefs[lastCheckMid], android.os.SystemClock.elapsedRealtime(), prefs[lastCheckAt],
            boot, prefs[lastCheckBoot]
        )
    }

    suspend fun markChecked(context: Context, mid: Long) {
        val boot = android.provider.Settings.Global.getInt(context.contentResolver, "boot_count", -1).toLong()
        context.applicationContext.messageNotificationStateStore.edit {
            it[lastCheckMid] = mid
            it[lastCheckAt] = android.os.SystemClock.elapsedRealtime()
            it[lastCheckBoot] = boot
        }
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(raw: String?): Map<String, AccountNotificationState> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, AccountNotificationState>>(raw)
        } catch (_: SerializationException) {
            // Corrupt state is re-baselined silently instead of replaying history.
            emptyMap()
        } catch (_: IllegalArgumentException) {
            emptyMap()
        }
    }

    suspend fun getState(context: Context, mid: Long): AccountNotificationState =
        decode(context.applicationContext.messageNotificationStateStore.data.first()[statesKey])[mid.toString()]
            ?: AccountNotificationState()

    suspend fun putState(context: Context, mid: Long, state: AccountNotificationState) {
        context.applicationContext.messageNotificationStateStore.edit { prefs ->
            prefs[statesKey] = json.encodeToString(decode(prefs[statesKey]) + (mid.toString() to state))
        }
    }

    suspend fun clearAll(context: Context) {
        context.applicationContext.messageNotificationStateStore.edit { it.clear() }
    }
}
