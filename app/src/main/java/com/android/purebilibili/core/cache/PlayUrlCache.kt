// 文件路径: core/cache/PlayUrlCache.kt
package com.android.purebilibili.core.cache

import android.util.LruCache
import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.repository.videoPlaybackAccountScope

/**
 * 播放地址缓存管理器
 * 
 * 使用 LruCache 缓存视频播放 URL，减少重复网络请求。
 * 缓存上限 50 条，有效期 10 分钟。
 */
object PlayUrlCache {
    
    private const val TAG = "PlayUrlCache"
    // 键里带 accountScope（双账号时必须隔离，否则会串用不同账号拿到的画质）。
    // 账号数增加会让同一 bvid 的变体条目成倍增长，因此容量按账号空间留足余量，
    // 避免热条目被 LRU 挤掉后重进同一视频必然重新解析 playurl。
    private const val MAX_CACHE_SIZE = 160
    private const val CACHE_DURATION_MS = 10 * 60 * 1000L // 10 分钟
    
    /**
     * 缓存条目
     */
    data class CachedPlayUrl(
        val bvid: String,
        val cid: Long,
        val data: PlayUrlData,
        val quality: Int,
        val requestedQuality: Int?,
        val requestedAudioLang: String?,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val expiresAt: Long = resolvePlayUrlCacheExpiresAt(
            timestampMs = timestamp,
            maxAgeMs = CACHE_DURATION_MS,
            urls = buildList {
                data.dash?.video.orEmpty().forEach { track ->
                    add(track.baseUrl)
                    addAll(track.backupUrl.orEmpty())
                }
                val audios = data.dash?.audio.orEmpty() + data.dash?.dolby?.audio.orEmpty() +
                    listOfNotNull(data.dash?.flac?.audio)
                audios.forEach { track ->
                    add(track.baseUrl)
                    addAll(track.backupUrl.orEmpty())
                }
                data.durl.orEmpty().forEach { stream ->
                    add(stream.url)
                    addAll(stream.backupUrl.orEmpty())
                }
            }
        )
        
        fun isExpired(): Boolean = System.currentTimeMillis() >= expiresAt
    }
    
    /**
     * 生成缓存键
     */
    private fun generateKey(
        bvid: String,
        cid: Long,
        requestedQuality: Int?,
        audioLang: String?,
        accountScope: String
    ): String {
        val qualityKey = requestedQuality?.toString() ?: "auto"
        val audioKey = audioLang ?: "default"
        return "$accountScope:$bvid:$cid:q=$qualityKey:a=$audioKey"
    }

    private fun generateVideoPrefix(bvid: String, cid: Long, accountScope: String): String =
        "$accountScope:$bvid:$cid:"
    
    /**
     * 缓存实例（淘汰时同步移除二级索引，避免长期前台会话中 variantsByVideo 残留失效键）
     */
    private val cache: LruCache<String, CachedPlayUrl> = object : LruCache<String, CachedPlayUrl>(MAX_CACHE_SIZE) {
        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: CachedPlayUrl,
            newValue: CachedPlayUrl?
        ) {
            if (evicted && newValue == null) {
                val parts = key.split(":", limit = 4)
                if (parts.size >= 3) {
                    unindexKey(key, "${parts[0]}:${parts[1]}:${parts[2]}:")
                }
            }
        }
    }

    /**
     * 「视频前缀 → 该视频的全部变体键」二级索引。
     *
     * 未命中回退查询（[findNewestValidCacheForVideo]）与整视频失效（[invalidate]）原实现
     * 都要 `cache.snapshot()` 复制整表再逐个前缀匹配——容量提到 160、账号变体成倍后，
     * 这个整表拷贝会成为主线程上的可观开销。索引把这两条路径降为「只遍历该视频的变体」。
     *
     * 一致性约束：索引键只增不减会缓慢膨胀，因此在 [put] 覆盖旧条目、[invalidate] 与
     * [clear] 时同步维护；LruCache 自身淘汰条目不感知，故读取路径在发现索引键已不在缓存
     * 中时会顺手清理该键。
     */
    private val variantsByVideo = HashMap<String, MutableSet<String>>()

    private fun indexKey(key: String, videoPrefix: String) {
        variantsByVideo.getOrPut(videoPrefix) { LinkedHashSet() }.add(key)
    }

    private fun unindexKey(key: String, videoPrefix: String) {
        val variants = variantsByVideo[videoPrefix] ?: return
        variants.remove(key)
        if (variants.isEmpty()) variantsByVideo.remove(videoPrefix)
    }

    private fun variantsFor(videoPrefix: String): List<Pair<String, CachedPlayUrl>> {
        val variants = variantsByVideo[videoPrefix] ?: return emptyList()
        val resolved = ArrayList<Pair<String, CachedPlayUrl>>(variants.size)
        val stale = ArrayList<String>(0)
        variants.forEach { key ->
            val entry = cache.get(key)
            if (entry == null) {
                // LRU 已淘汰该键：顺手从索引移除，避免索引无界增长。
                stale.add(key)
            } else {
                resolved.add(key to entry)
            }
        }
        stale.forEach { variants.remove(it) }
        if (variants.isEmpty()) variantsByVideo.remove(videoPrefix)
        return resolved
    }
    
    /**
     * 获取缓存的播放地址
     * 
     * @param bvid 视频 BV 号
     * @param cid 视频 CID
     * @param requestedQuality 请求画质（null 表示自动）
     * @param audioLang 音轨语言（null 表示默认）
     * @return 缓存的播放数据，如果缓存不存在或已过期则返回 null
     */
    @Synchronized
    fun get(
        bvid: String,
        cid: Long,
        requestedQuality: Int? = null,
        audioLang: String? = null,
        accountScope: String = videoPlaybackAccountScope()
    ): PlayUrlData? {
        val key = generateKey(bvid, cid, requestedQuality, audioLang, accountScope)
        val exact = cache.get(key)

        val (matchedKey, cached) = when {
            exact != null -> key to exact
            requestedQuality == null && audioLang == null -> {
                findNewestValidCacheForVideo(bvid, cid, accountScope)
                    ?: (key to null)
            }
            else -> key to null
        }

        return when {
            cached == null -> {
                com.android.purebilibili.core.util.Logger.d(
                    TAG,
                    " Cache miss: bvid=$bvid, cid=$cid, reqQ=${requestedQuality ?: "auto"}, lang=${audioLang ?: "default"}"
                )
                null
            }
            cached.isExpired() -> {
                com.android.purebilibili.core.util.Logger.d(
                    TAG,
                    "⏰ Cache expired: bvid=$bvid, cid=$cid, reqQ=${requestedQuality ?: "auto"}, lang=${audioLang ?: "default"}"
                )
                cache.remove(matchedKey)
                unindexKey(matchedKey, generateVideoPrefix(bvid, cid, accountScope))
                null
            }
            else -> {
                val remainingMs = cached.expiresAt - System.currentTimeMillis()
                com.android.purebilibili.core.util.Logger.d(
                    TAG,
                    " Cache hit: bvid=$bvid, cid=$cid, reqQ=${cached.requestedQuality ?: "auto"}, actualQ=${cached.quality}, lang=${cached.requestedAudioLang ?: "default"}, expires in ${remainingMs / 1000}s"
                )
                cached.data
            }
        }
    }
    
    /**
     * 添加播放地址到缓存
     * 
     * @param bvid 视频 BV 号
     * @param cid 视频 CID
     * @param data 播放数据
     * @param quality 请求画质（null 表示自动）
     * @param audioLang 音轨语言（null 表示默认）
     */
    @Synchronized
    fun put(
        bvid: String,
        cid: Long,
        data: PlayUrlData,
        quality: Int? = null,
        audioLang: String? = null,
        accountScope: String = videoPlaybackAccountScope()
    ) {
        val key = generateKey(bvid, cid, quality, audioLang, accountScope)
        val entry = CachedPlayUrl(
            bvid = bvid,
            cid = cid,
            data = data,
            quality = data.quality,
            requestedQuality = quality,
            requestedAudioLang = audioLang
        )
        if (entry.isExpired()) {
            cache.remove(key)
            unindexKey(key, generateVideoPrefix(bvid, cid, accountScope))
            return
        }
        cache.put(key, entry)
        indexKey(key, generateVideoPrefix(bvid, cid, accountScope))
        com.android.purebilibili.core.util.Logger.d(
            TAG,
            " Cached: bvid=$bvid, cid=$cid, reqQ=${quality ?: "auto"}, actualQ=${data.quality}, lang=${audioLang ?: "default"}"
        )
    }
    
    /**
     * 使指定视频的缓存失效
     */
    @Synchronized
    fun invalidate(
        bvid: String,
        cid: Long,
        requestedQuality: Int? = null,
        audioLang: String? = null,
        accountScope: String = videoPlaybackAccountScope()
    ) {
        if (requestedQuality == null && audioLang == null) {
            val prefix = generateVideoPrefix(bvid, cid, accountScope)
            var removedCount = 0
            // 只遍历该视频的变体，不再整表复制。
            variantsFor(prefix).forEach { (key, _) ->
                cache.remove(key)
                removedCount++
            }
            variantsByVideo.remove(prefix)
            com.android.purebilibili.core.util.Logger.d(
                TAG,
                " Invalidated all variants: bvid=$bvid, cid=$cid, removed=$removedCount"
            )
            return
        }

        val key = generateKey(bvid, cid, requestedQuality, audioLang, accountScope)
        cache.remove(key)
        unindexKey(key, generateVideoPrefix(bvid, cid, accountScope))
        com.android.purebilibili.core.util.Logger.d(
            TAG,
            " Invalidated variant: bvid=$bvid, cid=$cid, reqQ=${requestedQuality ?: "auto"}, lang=${audioLang ?: "default"}"
        )
    }
    
    /**
     * 清除所有缓存
     */
    @Synchronized
    fun clear() {
        cache.evictAll()
        variantsByVideo.clear()
        com.android.purebilibili.core.util.Logger.d(TAG, " Cache cleared")
    }
    
    /**
     * 获取当前缓存大小
     */
    fun size(): Int = cache.size()
    
    /**
     * 获取缓存统计信息（调试用）
     */
    fun getStats(): String {
        return "PlayUrlCache: size=${size()}, maxSize=$MAX_CACHE_SIZE, " +
               "hitCount=${cache.hitCount()}, missCount=${cache.missCount()}"
    }

    private fun findNewestValidCacheForVideo(bvid: String, cid: Long, accountScope: String): Pair<String, CachedPlayUrl>? {
        val prefix = generateVideoPrefix(bvid, cid, accountScope)
        var candidate: Pair<String, CachedPlayUrl>? = null
        val expiredKeys = ArrayList<String>(0)

        // 只遍历该视频的变体集合，不再复制整表。
        variantsFor(prefix).forEach { (key, entry) ->
            if (entry.isExpired()) {
                cache.remove(key)
                expiredKeys.add(key)
                return@forEach
            }
            val currentCandidate = candidate
            if (currentCandidate == null || entry.timestamp > currentCandidate.second.timestamp) {
                candidate = key to entry
            }
        }
        expiredKeys.forEach { unindexKey(it, prefix) }

        return candidate
    }
}
