package com.android.purebilibili.feature.message.notification

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.messageNotificationSettingsStore by preferencesDataStore(name = "message_notification_settings")

internal enum class MessageNotificationMode { POWER_SAVING, MORE_TIMELY }

internal data class MessageNotificationSettings(
    val enabled: Boolean = false,
    val mode: MessageNotificationMode = MessageNotificationMode.POWER_SAVING,
    val residentEnabled: Boolean = false,
    val notifyMessageCenter: Boolean = true,
    val notifyDynamicUpdates: Boolean = true,
    val notifyLiveAlerts: Boolean = true,
)

internal object MessageNotificationSettingsStore {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val modeKey = stringPreferencesKey("mode")
    private val residentKey = booleanPreferencesKey("resident")
    private val messageCenterKey = booleanPreferencesKey("msg_center")
    private val dynamicKey = booleanPreferencesKey("dynamic")
    private val liveKey = booleanPreferencesKey("live")

    fun getSettings(context: Context): Flow<MessageNotificationSettings> =
        context.applicationContext.messageNotificationSettingsStore.data.map { prefs ->
            MessageNotificationSettings(
                enabled = prefs[enabledKey] ?: false,
                mode = MessageNotificationMode.entries.firstOrNull { it.name == prefs[modeKey] }
                    ?: MessageNotificationMode.POWER_SAVING,
                residentEnabled = prefs[residentKey] ?: false,
                notifyMessageCenter = prefs[messageCenterKey] ?: true,
                notifyDynamicUpdates = prefs[dynamicKey] ?: true,
                notifyLiveAlerts = prefs[liveKey] ?: true,
            )
        }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[enabledKey] = enabled }
    }

    suspend fun setMode(context: Context, mode: MessageNotificationMode) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[modeKey] = mode.name }
    }

    suspend fun setResidentEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[residentKey] = enabled }
    }

    suspend fun setMessageCenterEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[messageCenterKey] = enabled }
    }

    suspend fun setDynamicUpdatesEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[dynamicKey] = enabled }
    }

    suspend fun setLiveAlertsEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[liveKey] = enabled }
    }
}
