package com.android.purebilibili.core.store.player

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.android.purebilibili.core.store.settingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Player amplification is independent of Android's music-stream volume. */
object PiliPlayerMenuStore {
    private val amplificationKey = floatPreferencesKey("pili_player_amplification")
    private val exitAppKey = booleanPreferencesKey("pili_sleep_exit_app")
    // 与设置页共用同一个 key。原先播放界面单独存 "pili_player_cdn_service"，
    // 和设置页的 "playback_cdn_preference" 互不相通，表现为「设置里选了 ali，
    // 播放界面仍然显示基础URL，而且只有播放界面里的选择真正生效」。
    private val cdnServiceKey = stringPreferencesKey("playback_cdn_preference")

    fun cdnService(context: Context): Flow<String> = context.settingsDataStore.data.map {
        cdnServiceStorageToName(
            it[cdnServiceKey] ?: com.android.purebilibili.feature.plugin.PlaybackCdnPreference.DEFAULT.storageValue
        )
    }

    suspend fun setCdnService(context: Context, service: String) {
        context.settingsDataStore.edit { it[cdnServiceKey] = cdnServiceNameToStorage(service) }
    }

    /** 设置页存的 storageValue → 播放界面使用的枚举名。 */
    private fun cdnServiceStorageToName(value: String): String = when (value) {
        "base_url" -> "BASE"
        "backup_url" -> "BACKUP"
        "08c" -> "HW_08C"
        "08h" -> "HW_08H"
        "08ct" -> "HW_08CT"
        "tf_hw" -> "TF_HW"
        "tf_tx" -> "TF_TX"
        "hk_bcache" -> "HK_BCACHE"
        else -> value.uppercase()
    }

    /** 播放界面的枚举名 → 设置页使用的 storageValue。 */
    private fun cdnServiceNameToStorage(name: String): String = when (name) {
        "BASE" -> "base_url"
        "BACKUP" -> "backup_url"
        "HW_08C" -> "08c"
        "HW_08H" -> "08h"
        "HW_08CT" -> "08ct"
        "TF_HW" -> "tf_hw"
        "TF_TX" -> "tf_tx"
        "HK_BCACHE" -> "hk_bcache"
        else -> name.lowercase()
    }

    fun amplification(context: Context): Flow<Float> = context.settingsDataStore.data.map {
        (it[amplificationKey] ?: 1f).coerceIn(1f, 3f)
    }
    suspend fun setAmplification(context: Context, value: Float) {
        context.settingsDataStore.edit { it[amplificationKey] = value.coerceIn(1f, 3f) }
    }
    fun sleepExitApp(context: Context): Flow<Boolean> = context.settingsDataStore.data.map { it[exitAppKey] ?: false }
    suspend fun setSleepExitApp(context: Context, value: Boolean) {
        context.settingsDataStore.edit { it[exitAppKey] = value }
    }
    fun reversedPages(context: Context, bvid: String): Flow<Boolean> =
        context.settingsDataStore.data.map { it[booleanPreferencesKey("pili_player_reverse_$bvid")] ?: false }
    suspend fun setReversedPages(context: Context, bvid: String, value: Boolean) {
        context.settingsDataStore.edit { it[booleanPreferencesKey("pili_player_reverse_$bvid")] = value }
    }
}
