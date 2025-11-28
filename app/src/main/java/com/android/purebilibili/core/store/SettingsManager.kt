package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 独立的 DataStore，避免和 User Token 混在一起
val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

object SettingsManager {
    // 定义 Key
    private val KEY_AUTO_PLAY = booleanPreferencesKey("auto_play")
    private val KEY_HW_DECODE = booleanPreferencesKey("hw_decode")
    private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode")

    // --- 读取 (Get) ---
    // 默认开启自动播放
    fun getAutoPlay(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { it[KEY_AUTO_PLAY] ?: true }

    // 默认开启硬解
    fun getHwDecode(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { it[KEY_HW_DECODE] ?: true }

    // 默认跟随系统 (这里默认关，由 UI 层决定初始值)
    fun getDarkMode(context: Context): Flow<Boolean> = context.settingsDataStore.data
        .map { it[KEY_DARK_MODE] ?: false }

    // --- 写入 (Set) ---
    suspend fun setAutoPlay(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_PLAY] = value }
    }

    suspend fun setHwDecode(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[KEY_HW_DECODE] = value }
    }

    suspend fun setDarkMode(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[KEY_DARK_MODE] = value }
    }
}