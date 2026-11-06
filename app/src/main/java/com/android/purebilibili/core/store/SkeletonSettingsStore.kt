package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.map

object SkeletonSettingsStore {
    internal val breathingKey = booleanPreferencesKey("skeleton_breathing_enabled")

    fun breathingEnabled(context: Context) = context.settingsDataStore.data.map {
        it[breathingKey] ?: false
    }

    suspend fun setBreathingEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { it[breathingKey] = enabled }
    }
}
