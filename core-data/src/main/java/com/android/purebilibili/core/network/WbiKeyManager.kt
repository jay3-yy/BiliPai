// 文件路径: core/network/WbiKeyManager.kt
package com.android.purebilibili.core.network

import android.content.Context
import com.android.purebilibili.core.store.TokenManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * WBI 签名密钥管理器
 * 
 * 统一管理 WBI 签名所需的 imgKey 和 subKey，
 * 支持内存缓存、持久化存储和并发安全的刷新机制。
 */
object WbiKeyManager {
    
    private const val TAG = "WbiKeyManager"
    private const val SP_NAME = "wbi_keys_sp"
    private const val SP_KEY_IMG = "wbi_img_key"
    private const val SP_KEY_SUB = "wbi_sub_key"
    private const val SP_KEY_TIMESTAMP = "wbi_timestamp"
    private const val SP_KEY_EXPIRES = "wbi_expires_at"
    
    // key 刷新失败时的兜底有效期：24 小时
    private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
    // 预刷新阈值：剩余时间少于 1 小时时预刷新
    private const val PREFRESH_THRESHOLD_MS = 60 * 60 * 1000L
    // 到期前 30 分钟刷新
    private const val REFRESH_MARGIN_MS = 30 * 60 * 1000L
    
    // 内存缓存
    @Volatile
    private var cachedKeys: Pair<String, String>? = null
    @Volatile
    private var cacheTimestamp: Long = 0
    @Volatile
    private var cacheExpiresAt: Long = 0
    
    // 刷新互斥锁，防止并发刷新
    private val refreshMutex = Mutex()
    
    /**
     * 获取 WBI 密钥
     * 
     * 优先从内存缓存获取，如果缓存无效则从网络刷新。
     * 
     * @return Result 包含 (imgKey, subKey) 或错误
     */
    suspend fun getWbiKeys(): Result<Pair<String, String>> {
        // 1. 检查内存缓存
        val cached = cachedKeys
        if (cached != null && isCacheValid()) {
            com.android.purebilibili.core.network.CoreDataLog.d(TAG, " Using cached WBI keys")
            return Result.success(cached)
        }
        
        // 2. 需要刷新，使用互斥锁确保单次刷新
        return refreshMutex.withLock {
            // 双重检查：可能在等待锁的过程中其他协程已刷新
            val rechecked = cachedKeys
            if (rechecked != null && isCacheValid()) {
                com.android.purebilibili.core.network.CoreDataLog.d(TAG, " Using cached WBI keys (after lock)")
                return@withLock Result.success(rechecked)
            }
            
            // 执行刷新
            refreshKeysInternal()
        }
    }
    
    /**
     * 强制刷新 WBI 密钥
     */
    suspend fun refreshKeys(): Result<Pair<String, String>> {
        return refreshMutex.withLock {
            refreshKeysInternal()
        }
    }
    
    /**
     * 内部刷新逻辑
     */
    private suspend fun refreshKeysInternal(): Result<Pair<String, String>> {
        com.android.purebilibili.core.network.CoreDataLog.d(TAG, " Refreshing WBI keys from network...")

        // 先刷新签名 key，失败回退 nav。
        try {
            val ticketKeys = refreshKeysFromTicket()
            if (ticketKeys != null) {
                cachedKeys = ticketKeys
                persistCache()
                com.android.purebilibili.core.network.CoreDataLog.d(TAG, " WBI keys refreshed via ticket")
                return Result.success(ticketKeys)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, " Ticket refresh failed: ${e.message}")
        }

        cacheExpiresAt = 0
        return try {
            val api = NetworkModule.api
            val navResp = api.getNavInfo()
            val wbiImg = navResp.data?.wbi_img

            if (wbiImg != null) {
                val imgKey = wbiImg.img_url.substringAfterLast("/").substringBefore(".")
                val subKey = wbiImg.sub_url.substringAfterLast("/").substringBefore(".")

                cachedKeys = Pair(imgKey, subKey)
                cacheTimestamp = System.currentTimeMillis()
                persistCache()

                com.android.purebilibili.core.network.CoreDataLog.d(TAG, " WBI keys refreshed from nav")
                Result.success(Pair(imgKey, subKey))
            } else {
                android.util.Log.e(TAG, " WBI keys not found in response")
                Result.failure(Exception("WBI keys not found in nav response"))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e(TAG, " Failed to refresh WBI keys: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun refreshKeysFromTicket(): Pair<String, String>? {
        val timestamp = System.currentTimeMillis() / 1000
        val response = NetworkModule.api.getWebTicket(
            keyId = "ec02",
            hexsign = hmacSha256Hex("ts$timestamp", "XgwsnGZ1p"),
            timestamp = timestamp,
            csrf = TokenManager.csrfCache.orEmpty(),
        )
        if (response.code != 0) return null
        val data = response.data ?: return null
        val imgKey = extractWbiKey(data.nav?.img.orEmpty())
        val subKey = extractWbiKey(data.nav?.sub.orEmpty())
        if (imgKey.isBlank() || subKey.isBlank()) return null

        cacheTimestamp = System.currentTimeMillis()
        cacheExpiresAt = if (data.createdAt > 0 && data.ttl > 0) {
            (data.createdAt + data.ttl) * 1000
        } else {
            0
        }
        return Pair(imgKey, subKey)
    }

    private fun extractWbiKey(value: String): String {
        if (value.isBlank()) return ""
        return value.substringAfterLast('/').substringBefore('.').ifBlank { value }
    }

    private fun hmacSha256Hex(data: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun persistCache() {
        try {
            NetworkModule.appContext?.let(::persistToStorage)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w(TAG, " Failed to persist WBI keys: ${e.message}")
        }
    }

    /**
     * 使缓存失效
     */
    fun invalidateCache() {
        com.android.purebilibili.core.network.CoreDataLog.d(TAG, " Invalidating WBI keys cache")
        cachedKeys = null
        cacheTimestamp = 0
        cacheExpiresAt = 0
    }

    /**
     * 检查缓存是否有效
     */
    private fun isCacheValid(): Boolean {
        if (cachedKeys == null) return false
        val now = System.currentTimeMillis()
        return if (cacheExpiresAt > 0) {
            now + REFRESH_MARGIN_MS < cacheExpiresAt
        } else {
            now - cacheTimestamp < CACHE_DURATION_MS
        }
    }

    /**
     * 检查是否需要预刷新（剩余时间 < 1 小时）
     */
    fun shouldPrefresh(): Boolean {
        val now = System.currentTimeMillis()
        val remaining = if (cacheExpiresAt > 0) {
            cacheExpiresAt - now
        } else {
            (cacheTimestamp + CACHE_DURATION_MS) - now
        }
        return remaining < PREFRESH_THRESHOLD_MS
    }
    
    /**
     * 持久化到本地存储
     */
    fun persistToStorage(context: Context) {
        val keys = cachedKeys ?: return
        
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE).edit()
            .putString(SP_KEY_IMG, keys.first)
            .putString(SP_KEY_SUB, keys.second)
            .putLong(SP_KEY_TIMESTAMP, cacheTimestamp)
            .putLong(SP_KEY_EXPIRES, cacheExpiresAt)
            .apply()
        
        com.android.purebilibili.core.network.CoreDataLog.d(TAG, " WBI keys persisted to storage")
    }
    
    /**
     * 预热持久化存储:提前触发 SP 文件的异步磁盘加载。
     * 启动任务在主线程同步调用 [restoreFromStorage],若此时尚未读过该文件,
     * 主线程会阻塞在首次磁盘读;Application.onCreate 早期在 IO 线程调用本方法,
     * 通常能让主线程读到时命中框架的内存缓存。
     */
    fun prewarmStorage(context: Context) {
        context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 从本地存储恢复
     */
    fun restoreFromStorage(context: Context): Boolean {
        val sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        val imgKey = sp.getString(SP_KEY_IMG, null)
        val subKey = sp.getString(SP_KEY_SUB, null)
        val timestamp = sp.getLong(SP_KEY_TIMESTAMP, 0)
        val expiresAt = sp.getLong(SP_KEY_EXPIRES, 0)
        
        if (imgKey != null && subKey != null && timestamp > 0) {
            cachedKeys = Pair(imgKey, subKey)
            cacheTimestamp = timestamp
            cacheExpiresAt = expiresAt
            
            if (isCacheValid()) {
                com.android.purebilibili.core.network.CoreDataLog.d(TAG, " WBI keys restored from storage")
                return true
            } else {
                com.android.purebilibili.core.network.CoreDataLog.d(TAG, "⏰ Restored WBI keys are expired")
                invalidateCache()
            }
        } else {
            com.android.purebilibili.core.network.CoreDataLog.d(TAG, " No WBI keys found in storage")
        }
        
        return false
    }
    
    /**
     * 获取缓存统计信息（调试用）
     */
    fun getStats(): String {
        val hasKeys = cachedKeys != null
        val age = if (cacheTimestamp > 0) {
            (System.currentTimeMillis() - cacheTimestamp) / 1000 / 60
        } else 0
        return "WbiKeyManager: hasKeys=$hasKeys, ageMinutes=$age, valid=${isCacheValid()}"
    }
}
