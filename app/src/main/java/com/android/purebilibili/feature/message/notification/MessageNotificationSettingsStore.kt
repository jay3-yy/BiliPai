package com.android.purebilibili.feature.message.notification

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.store.MultiProcessPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.messageNotificationSettingsStore
    get() = MultiProcessPreferences.get(this, "message_notification_settings")

internal enum class MessageNotificationMode { POWER_SAVING, MORE_TIMELY }

internal data class MessageNotificationSettings(
    val enabled: Boolean = false,
    val mode: MessageNotificationMode = MessageNotificationMode.POWER_SAVING,
    val residentEnabled: Boolean = false,
    val notifyPrivateMessages: Boolean = true,
    val notifyReplies: Boolean = true,
    val notifyAtMe: Boolean = true,
    val notifyLikes: Boolean = true,
    val notifySystemNotices: Boolean = true,
    val notifyDynamicUpdates: Boolean = true,
    val notifyLiveAlerts: Boolean = true,
)

internal object MessageNotificationSettingsStore {
    private val enabledKey = booleanPreferencesKey("enabled")
    private val modeKey = stringPreferencesKey("mode")
    private val residentKey = booleanPreferencesKey("resident")
    private val privateMessagesKey = booleanPreferencesKey("private_msg")
    private val repliesKey = booleanPreferencesKey("reply")
    private val atMeKey = booleanPreferencesKey("at_me")
    private val likesKey = booleanPreferencesKey("like_me")
    private val systemNoticesKey = booleanPreferencesKey("system_notice")
    private val legacyMessageCenterKey = booleanPreferencesKey("msg_center")
    private val dynamicKey = booleanPreferencesKey("dynamic")
    private val liveKey = booleanPreferencesKey("live")

    fun getSettings(context: Context): Flow<MessageNotificationSettings> =
        context.applicationContext.messageNotificationSettingsStore.data.map { prefs ->
            // One-shot legacy migration: the former single "msg_center" switch covered
            // all five subcategories. If it exists, propagate its value to every new
            // granular key that hasn't been set yet, then delete the legacy key so the
            // store never carries it again.
            val resolved = if (legacyMessageCenterKey in prefs) {
                val legacyValue = prefs[legacyMessageCenterKey] != false
                context.applicationContext.messageNotificationSettingsStore.edit { mutable ->
                    if (privateMessagesKey !in mutable) mutable[privateMessagesKey] = legacyValue
                    if (repliesKey !in mutable) mutable[repliesKey] = legacyValue
                    if (atMeKey !in mutable) mutable[atMeKey] = legacyValue
                    if (likesKey !in mutable) mutable[likesKey] = legacyValue
                    if (systemNoticesKey !in mutable) mutable[systemNoticesKey] = legacyValue
                    mutable.remove(legacyMessageCenterKey)
                }
            } else prefs
            MessageNotificationSettings(
                enabled = resolved[enabledKey] ?: false,
                mode = MessageNotificationMode.entries.firstOrNull { it.name == resolved[modeKey] }
                    ?: MessageNotificationMode.POWER_SAVING,
                residentEnabled = resolved[residentKey] ?: false,
                notifyPrivateMessages = resolved[privateMessagesKey] ?: true,
                notifyReplies = resolved[repliesKey] ?: true,
                notifyAtMe = resolved[atMeKey] ?: true,
                notifyLikes = resolved[likesKey] ?: true,
                notifySystemNotices = resolved[systemNoticesKey] ?: true,
                notifyDynamicUpdates = resolved[dynamicKey] ?: true,
                notifyLiveAlerts = resolved[liveKey] ?: true,
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

    suspend fun setPrivateMessagesEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[privateMessagesKey] = enabled }
    }

    suspend fun setRepliesEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[repliesKey] = enabled }
    }

    suspend fun setAtMeEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[atMeKey] = enabled }
    }

    suspend fun setLikesEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[likesKey] = enabled }
    }

    suspend fun setSystemNoticesEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[systemNoticesKey] = enabled }
    }

    suspend fun setDynamicUpdatesEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[dynamicKey] = enabled }
    }

    suspend fun setLiveAlertsEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.messageNotificationSettingsStore.edit { it[liveKey] = enabled }
    }
}
