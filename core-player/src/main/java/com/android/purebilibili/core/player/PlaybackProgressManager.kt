// File: feature/video/controller/PlaybackProgressManager.kt
package com.android.purebilibili.core.player

import android.content.Context
import android.content.SharedPreferences
import com.android.purebilibili.core.network.CoreDataLog as Logger
import com.android.purebilibili.core.store.AccountPurpose
import com.android.purebilibili.core.store.AccountSessionStore
import com.android.purebilibili.core.store.TokenManager

/**
 * Playback Progress Manager
 * 
 * Handles playback progress save/restore with persistent storage:
 * - Cache playback position for each video
 * - Save current position when switching videos
 * - Restore position when returning to a video
 * - Persist progress to SharedPreferences
 * 
 * Requirement Reference: AC1.4 - Progress managed by PlaybackProgressManager
 */
class PlaybackProgressManager internal constructor(
    private val accountMidProvider: (() -> Long)? = null,
) {
    constructor() : this(accountMidProvider = null)
    
    companion object {
        private const val TAG = "PlaybackProgressManager"
        private const val PREFS_NAME = "video_progress"
        private const val GUEST_SCOPE_PREFIX = "guest:"
        private const val MIN_PROGRESS_TO_SAVE = 5000L // 5秒以上才保存
        // 有界化上限：每看一个视频最多写 2 个 key（cid 级 + bvid 级），4096 条覆盖极长的
        // 观看历史；超过后按最旧淘汰，内存与磁盘 XML 都保持有界。
        private const val MAX_CACHED_PROGRESS_ENTRIES = 4096

        @Volatile
        private var instance: PlaybackProgressManager? = null

        fun getInstance(context: Context): PlaybackProgressManager {
            return instance ?: synchronized(this) {
                instance ?: PlaybackProgressManager().also {
                    it.init(context)
                    instance = it
                }
            }
        }
    }

    // savedAtMs 仅用于内存淘汰排序，不落盘；loadFromPrefs 里按加载顺序编号。
    private data class ProgressEntry(val positionMs: Long, val savedAtMs: Long)

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    // [性能优化] 使用 ConcurrentHashMap 保障主线程在滑动渲染热路径上的无锁极速读取（O(1) Lock-free）
    private val memoryCache = java.util.concurrent.ConcurrentHashMap<String, ProgressEntry>()

    private fun effectiveAccountMid(): Long {
        accountMidProvider?.let { return it().coerceAtLeast(0L) }
        val context = appContext
        val purposeMid = if (context != null) {
            AccountSessionStore.getPurposeAccountMid(context, AccountPurpose.WATCH_HISTORY)
        } else {
            null
        }
        return (purposeMid ?: TokenManager.accountIdentity.value.mid ?: TokenManager.midCache ?: 0L)
            .coerceAtLeast(0L)
    }

    private fun buildLegacyProgressKey(bvid: String, cid: Long): String {
        return if (cid > 0L) "$bvid#$cid" else bvid
    }

    private fun buildScopedProgressKey(accountMid: Long, bvid: String, cid: Long): String {
        val prefix = if (accountMid > 0L) "$accountMid:" else GUEST_SCOPE_PREFIX
        return "$prefix${buildLegacyProgressKey(bvid, cid)}"
    }
    
    /**
     * Initialize with context (for SharedPreferences)
     */
    fun init(context: Context) {
        val application = context.applicationContext
        appContext = application
        if (prefs == null) {
            prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            AccountSessionStore.getPurposeAccountMid(application, AccountPurpose.WATCH_HISTORY)
            loadFromPrefs()
            Logger.d(TAG, "Initialized with ${memoryCache.size} cached positions")
        }
    }
    
    /**
     * Save playback position
     */
    fun savePosition(bvid: String, cid: Long, positionMs: Long, durationMs: Long = 0L) {
        if (bvid.isEmpty() || positionMs < MIN_PROGRESS_TO_SAVE) return
        val accountMid = effectiveAccountMid()
        val key = buildScopedProgressKey(accountMid, bvid, cid)
        
        // 至少 95% 且距片尾不超过 60 秒，才视为已看完并清除记录
        val completionPositionMs = resolvePlaybackCompletionPositionMs(durationMs)
        if (durationMs > 0 && positionMs >= completionPositionMs) {
            clearPosition(bvid, cid)
            Logger.d(TAG, "Video $key completed (${positionMs}ms / ${durationMs}ms), cleared progress")
            return
        }

        val savedAtMs = System.currentTimeMillis()
        memoryCache[key] = ProgressEntry(positionMs, savedAtMs)
        val bvidFallbackKey = if (cid > 0L) buildScopedProgressKey(accountMid, bvid, cid = 0L) else null
        if (bvidFallbackKey != null) {
            memoryCache[bvidFallbackKey] = ProgressEntry(positionMs, savedAtMs)
        }
        evictOldestIfNeeded()

        prefs?.edit()?.apply {
            putLong(key, positionMs)
            if (bvidFallbackKey != null) {
                // 空间页等入口拿不到 cid 时，用 bvid 级进度兜底；播放器加载后仍优先 cid 精确进度。
                putLong(bvidFallbackKey, positionMs)
            }
        }?.apply()
        Logger.d(TAG, "Saved position for $key: ${positionMs}ms")
    }

    fun savePosition(bvid: String, positionMs: Long, durationMs: Long = 0L) {
        savePosition(bvid, cid = 0L, positionMs = positionMs, durationMs = durationMs)
    }
    
    /**
     * Save position (without duration check)
     */
    fun savePosition(bvid: String, positionMs: Long) {
        savePosition(bvid, cid = 0L, positionMs = positionMs, durationMs = 0L)
    }
    
    /**
     * Get cached playback position
     * [性能关键路径] 滑动列表热路径，严禁加锁、严禁同步读磁盘 SharedPreferences、严禁高频输出日志。
     */
    fun getCachedPosition(bvid: String, cid: Long): Long {
        if (bvid.isEmpty()) return 0L
        val accountMid = effectiveAccountMid()
        val key = buildScopedProgressKey(accountMid, bvid, cid)
        memoryCache[key]?.positionMs?.takeIf { it > 0L }?.let { return it }
        if (accountMid == 0L) {
            val legacyKey = buildLegacyProgressKey(bvid, cid)
            return memoryCache[legacyKey]?.positionMs?.takeIf { it > 0L } ?: 0L
        }
        return 0L
    }

    fun getCachedPosition(bvid: String): Long {
        return getCachedPosition(bvid, cid = 0L)
    }
    
    /**
     * Clear position cache for a specific video
     */
    fun clearPosition(bvid: String, cid: Long) {
        val accountMid = effectiveAccountMid()
        val key = buildScopedProgressKey(accountMid, bvid, cid)
        val bvidKey = if (cid > 0L) buildScopedProgressKey(accountMid, bvid, cid = 0L) else null
        val legacyKey = if (accountMid == 0L) buildLegacyProgressKey(bvid, cid) else null
        val legacyBvidKey = if (accountMid == 0L && cid > 0L) buildLegacyProgressKey(bvid, cid = 0L) else null
        memoryCache.remove(key)
        bvidKey?.let(memoryCache::remove)
        legacyKey?.let(memoryCache::remove)
        legacyBvidKey?.let(memoryCache::remove)
        prefs?.edit()?.apply {
            remove(key)
            bvidKey?.let(::remove)
            legacyKey?.let(::remove)
            legacyBvidKey?.let(::remove)
        }?.apply()
        Logger.d(TAG, "Cleared position for $key")
    }

    fun clearPosition(bvid: String) {
        clearPosition(bvid, cid = 0L)
    }
    
    /**
     * Clear all position caches
     */
    fun clearAll() {
        memoryCache.clear()
        prefs?.edit()?.clear()?.apply()
        Logger.d(TAG, "Cleared all positions")
    }
    
    /**
     * Check if there is cached position for a video
     */
    fun hasPosition(bvid: String, cid: Long): Boolean {
        return getCachedPosition(bvid, cid) > 0
    }

    fun hasPosition(bvid: String): Boolean {
        return hasPosition(bvid, cid = 0L)
    }
    
    /**
     * Get the number of cached positions
     */
    fun getCacheSize(): Int {
        return memoryCache.size
    }
    
    /**
     * Load positions from SharedPreferences to memory cache
     */
    private fun loadFromPrefs() {
        // savedAtMs 仅用于内存淘汰排序；磁盘条目无时间信息，按加载顺序编号即可。
        var loadOrder = 0L
        prefs?.all?.forEach { (key, value) ->
            if (value is Long && value > 0) {
                memoryCache[key] = ProgressEntry(value, loadOrder++)
            }
        }
    }

    internal fun seedLegacyEntryForTest(rawKey: String, positionMs: Long) {
        if (positionMs > 0L) {
            memoryCache[rawKey] = ProgressEntry(positionMs, 0L)
        }
    }

    /** 超过 [MAX_CACHED_PROGRESS_ENTRIES] 时按最旧淘汰，内存与磁盘同步删除保持有界。 */
    private fun evictOldestIfNeeded() {
        val overflow = memoryCache.size - MAX_CACHED_PROGRESS_ENTRIES
        if (overflow <= 0) return
        val evicted = memoryCache.entries.asSequence()
            .sortedBy { it.value.savedAtMs }
            .take(overflow)
            .map { it.key }
            .toList()
        val editor = prefs?.edit()
        evicted.forEach { key ->
            memoryCache.remove(key)
            editor?.remove(key)
        }
        editor?.apply()
        Logger.d(TAG, "Evicted ${evicted.size} oldest progress entries")
    }
}
