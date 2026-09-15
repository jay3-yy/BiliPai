package com.android.purebilibili.feature.message.notification

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.messageNotificationStateStore by preferencesDataStore(name = "message_notification_state")

// Worker, resident polling, and disabling/resetting share one transaction boundary.
internal val messageNotificationMutex = Mutex()

@Serializable
internal data class AccountNotificationState(
    val initialized: Set<String> = emptySet(),
    val dynamicBaseline: String = "",
    val seenDynamicIds: List<String> = emptyList(),
    val seenReplyIds: List<Long> = emptyList(),
    val seenAtIds: List<Long> = emptyList(),
    val seenLikeIds: List<Long> = emptyList(),
    val seenSystemCursors: List<Long> = emptyList(),
    val sessionMsgKeys: Map<String, Long> = emptyMap(),
    val liveSessions: Map<Long, Long> = emptyMap(),
)

internal object MessageNotificationStateStore {
    private val statesKey = stringPreferencesKey("states_json")
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
        context.applicationContext.messageNotificationStateStore.edit { it.remove(statesKey) }
    }
}
