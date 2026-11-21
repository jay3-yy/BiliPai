package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal object ProcessMemoryOverlayPreferences {
    private val enabledKey = booleanPreferencesKey("diagnostics_process_memory_overlay")

    fun observeEnabled(context: Context) = context.settingsDataStore.data
        .map { it[enabledKey] ?: false }
        .distinctUntilChanged()

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[enabledKey] = enabled }
    }
}
