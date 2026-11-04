package com.android.purebilibili.feature.audio.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.android.purebilibili.core.store.MultiProcessPreferences
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.dataStore
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 主进程读取设置，音频进程只读镜像，避免跨进程同时打开单进程设置 DataStore。 */
internal object SystemMediaPreferences {
    private val libraryProtectionKey = booleanPreferencesKey("protected_library")
    private val privateHistory = booleanPreferencesKey("private_history")

    suspend fun observe(context: Context) {
        combine(SettingsManager.getPrivacyContentAuthenticationEnabled(context),
            SettingsManager.getPrivacyModeEnabled(context),
            context.dataStore.data.map { it[androidx.datastore.preferences.core.longPreferencesKey("session_mid")] }.distinctUntilChanged()
        ) { locked, privacy, account -> Triple(locked, privacy, account) }
            .collect { (locked, privacy, _) ->
                MultiProcessPreferences.get(context, "system_media_preferences").edit {
                    it[libraryProtectionKey] = locked
                    it[privateHistory] = privacy
                }
                com.android.purebilibili.feature.widget.PlaybackWidgetProvider.requestUpdate(context)
            }
    }

    suspend fun libraryProtected(context: Context): Boolean =
        MultiProcessPreferences.get(context, "system_media_preferences").data.first()[libraryProtectionKey] ?: true

    suspend fun reportHistory(context: Context): Boolean =
        MultiProcessPreferences.get(context, "system_media_preferences").data.first()[privateHistory] == false
}
