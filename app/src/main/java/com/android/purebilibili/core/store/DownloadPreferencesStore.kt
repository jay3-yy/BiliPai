package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.first

internal data class DownloadPreferences(
    val rememberSelection: Boolean = false,
    val quality: Int? = null,
    val includeDanmaku: Boolean = true,
)

internal object DownloadPreferencesStore {
    private val rememberKey = booleanPreferencesKey("download_remember_selection")
    private val qualityKey = intPreferencesKey("download_preferred_quality")
    private val danmakuKey = booleanPreferencesKey("download_include_danmaku")

    suspend fun read(context: Context): DownloadPreferences {
        val preferences = context.settingsDataStore.data.first()
        return DownloadPreferences(
            rememberSelection = preferences[rememberKey] ?: false,
            quality = preferences[qualityKey],
            includeDanmaku = preferences[danmakuKey] ?: true,
        )
    }

    suspend fun save(context: Context, selection: DownloadPreferences) {
        context.settingsDataStore.edit { preferences ->
            preferences[rememberKey] = selection.rememberSelection
            if (selection.rememberSelection) {
                preferences[qualityKey] = requireNotNull(selection.quality)
                preferences[danmakuKey] = selection.includeDanmaku
            } else {
                preferences.remove(qualityKey)
                preferences.remove(danmakuKey)
            }
        }
    }
}
