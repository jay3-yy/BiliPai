package com.android.purebilibili.feature.audio.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.player.audio.PlayerAudioEffectPreset
import com.android.purebilibili.core.store.MultiProcessPreferences
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.dataStore
import com.android.purebilibili.core.store.player.PiliPlayerMenuStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal data class BackgroundAudioEffectPreferences(
    val preset: PlayerAudioEffectPreset = PlayerAudioEffectPreset.OFF,
    val amplification: Float = 1f,
)

/** 主进程读取设置，音频进程只读镜像，避免跨进程同时打开单进程设置 DataStore。 */
internal object SystemMediaPreferences {
    private val libraryProtectionKey = booleanPreferencesKey("protected_library")
    private val privateHistory = booleanPreferencesKey("private_history")
    private val audioEffectPresetKey = stringPreferencesKey("audio_effect_preset")
    private val audioAmplificationKey = floatPreferencesKey("audio_amplification")

    suspend fun observe(context: Context) {
        combine(
            SettingsManager.getPrivacyContentAuthenticationEnabled(context),
            SettingsManager.getPrivacyModeEnabled(context),
            context.dataStore.data.map { it[androidx.datastore.preferences.core.longPreferencesKey("session_mid")] }.distinctUntilChanged(),
            PiliPlayerMenuStore.audioEffectPreset(context),
            PiliPlayerMenuStore.amplification(context),
        ) { locked, privacy, accountMid, preset, amplification ->
            AudioMirrorSnapshot(locked, privacy, accountMid, preset, amplification)
        }.distinctUntilChanged()
            .collect { mirror ->
                MultiProcessPreferences.get(context, "system_media_preferences").edit {
                    it[libraryProtectionKey] = mirror.locked
                    it[privateHistory] = mirror.privacy
                    it[audioEffectPresetKey] = mirror.preset.storageKey
                    it[audioAmplificationKey] = mirror.amplification
                }
                com.android.purebilibili.feature.widget.PlaybackWidgetProvider.requestUpdate(context)
                com.android.purebilibili.feature.widget.WatchLaterBangumiWidgetProvider.requestUpdate(context)
                com.android.purebilibili.feature.tile.BackgroundAudioTileService.requestListeningState(context)
                com.android.purebilibili.feature.tile.IncognitoModeTileService.requestListeningState(context)
            }
    }

    private data class AudioMirrorSnapshot(
        val locked: Boolean,
        val privacy: Boolean,
        val accountMid: Long?,
        val preset: PlayerAudioEffectPreset,
        val amplification: Float,
    )

    fun observeAudioEffectPreferences(context: Context): Flow<BackgroundAudioEffectPreferences> =
        MultiProcessPreferences.get(context, "system_media_preferences").data.map { prefs ->
            BackgroundAudioEffectPreferences(
                preset = PlayerAudioEffectPreset.fromStorageKey(prefs[audioEffectPresetKey]),
                amplification = (prefs[audioAmplificationKey] ?: 1f).coerceIn(1f, 3f),
            )
        }.distinctUntilChanged()

    suspend fun updatePrivateHistory(context: Context, privateEnabled: Boolean) {
        MultiProcessPreferences.get(context, "system_media_preferences").edit {
            it[privateHistory] = privateEnabled
        }
    }

    suspend fun libraryProtected(context: Context): Boolean =
        MultiProcessPreferences.get(context, "system_media_preferences").data.first()[libraryProtectionKey] ?: true

    suspend fun reportHistory(context: Context): Boolean =
        MultiProcessPreferences.get(context, "system_media_preferences").data.first()[privateHistory] == false
}
