package com.android.purebilibili.core.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

object TokenManager {
    private val SESSDATA_KEY = stringPreferencesKey("sessdata")
    private val BUVID3_KEY = stringPreferencesKey("buvid3")

    @Volatile
    var sessDataCache: String? = null
        // 🔥 核心修改：移除 private set，允许外部直接赋值，或保持 private 但通过 saveCookies 更新
        // 这里为了安全，我们保持 set 为 private，但在 saveCookies 里必须赋值
        private set

    @Volatile
    var buvid3Cache: String? = null
        private set

    fun init(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            context.dataStore.data.collect { prefs ->
                sessDataCache = prefs[SESSDATA_KEY]
                if (prefs[BUVID3_KEY] == null) {
                    val newBuvid = generateBuvid3()
                    saveBuvid3(context, newBuvid)
                } else {
                    buvid3Cache = prefs[BUVID3_KEY]
                }
            }
        }
    }

    // 🔥 核心修改：保存时同时更新内存缓存
    suspend fun saveCookies(context: Context, sessData: String) {
        // 1. 立即更新内存缓存，拦截器马上就能读到
        sessDataCache = sessData

        // 2. 异步保存到硬盘
        context.dataStore.edit { prefs ->
            prefs[SESSDATA_KEY] = sessData
        }
    }

    private suspend fun saveBuvid3(context: Context, buvid3: String) {
        buvid3Cache = buvid3
        context.dataStore.edit { prefs ->
            prefs[BUVID3_KEY] = buvid3
        }
    }

    fun getSessData(context: Context): Flow<String?> {
        return context.dataStore.data.map { prefs -> prefs[SESSDATA_KEY] }
    }

    suspend fun clear(context: Context) {
        sessDataCache = null
        context.dataStore.edit {
            it.remove(SESSDATA_KEY)
        }
    }

    private fun generateBuvid3(): String {
        return UUID.randomUUID().toString().replace("-", "") + "infoc"
    }
}