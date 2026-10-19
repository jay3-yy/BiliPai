// 文件路径: data/repository/DanmakuContentRepository.kt
package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class DanmakuCacheStats(
    val rawEntryCount: Int,
    val segmentEntryCount: Int,
    val totalBytes: Long
)

private data class DanmakuSegmentCacheKey(
    val cid: Long,
    val segmentIndex: Int
)

/**
 * 弹幕读取与缓存切片（手机与 TV 共用）。
 *
 * 只负责网络拉取、解压与字节缓存；协议解析在 :danmaku-engine 的
 * `com.android.purebilibili.danmaku.parser.DanmakuParser`，两端调用同一实现。
 * 发送/点赞/举报/云同步/直播 WebSocket 等账号向操作仍在端侧 DanmakuRepository。
 */
object DanmakuContentRepository {
    // Cache eviction must not initialize Retrofit/OkHttp when no danmaku was loaded yet.
    private val api get() = NetworkModule.api

    const val DANMAKU_SEGMENT_DURATION_MS = 360_000L
    private const val DANMAKU_SEGMENT_SAFE_FALLBACK_COUNT = 3

    // 弹幕数据缓存 - 避免横竖屏切换等场景重复下载
    private val danmakuCache = LinkedHashMap<Long, ByteArray>(5, 0.75f, true)
    private const val MAX_DANMAKU_CACHE_COUNT = 3  // 最多缓存3个视频的弹幕
    private const val MAX_DANMAKU_CACHE_BYTES = 4L * 1024 * 1024
    private var danmakuCacheBytes = 0L

    // Protobuf 弹幕分段缓存
    private val danmakuSegmentCache =
        LinkedHashMap<DanmakuSegmentCacheKey, ByteArray>(12, 0.75f, true)
    private const val MAX_SEGMENT_CACHE_COUNT = 12
    private const val MAX_SEGMENT_CACHE_BYTES = 12L * 1024 * 1024
    private const val MAX_SEGMENT_PARALLELISM = 3
    private var danmakuSegmentCacheBytes = 0L

    /** 清除弹幕缓存 */
    fun clearCache() {
        synchronized(danmakuCache) {
            danmakuCache.clear()
            danmakuCacheBytes = 0L
        }
        synchronized(danmakuSegmentCache) {
            danmakuSegmentCache.clear()
            danmakuSegmentCacheBytes = 0L
        }
        android.util.Log.d("DanmakuRepo", "Danmaku cache cleared")
    }

    /** Evict only a segment whose bytes failed protocol parsing. */
    fun invalidateDanmakuSegment(cid: Long, segmentIndex: Int) {
        synchronized(danmakuSegmentCache) {
            danmakuSegmentCache.remove(DanmakuSegmentCacheKey(cid, segmentIndex))?.let {
                danmakuSegmentCacheBytes -= it.size.toLong()
            }
        }
    }

    fun getDanmakuCacheStats(): DanmakuCacheStats {
        val rawEntryCount = synchronized(danmakuCache) { danmakuCache.size }
        val segmentEntryCount = synchronized(danmakuSegmentCache) { danmakuSegmentCache.size }
        val totalBytes = synchronized(danmakuCache) { danmakuCacheBytes } +
            synchronized(danmakuSegmentCache) { danmakuSegmentCacheBytes }
        return DanmakuCacheStats(
            rawEntryCount = rawEntryCount,
            segmentEntryCount = segmentEntryCount,
            totalBytes = totalBytes
        )
    }

    fun resolveSegmentCount(
        durationMs: Long,
        metadataSegmentCount: Int?
    ): Int {
        // dmSge belongs to the requested cid and is therefore authoritative. During an in-place
        // page switch ExoPlayer can still expose the previous page's positive duration; preferring
        // that stale value truncates/extends the new cid's segment window until danmaku is toggled.
        val fromMetadata = metadataSegmentCount?.coerceAtLeast(0) ?: 0
        if (fromMetadata > 0) return fromMetadata

        val fromDuration = if (durationMs > 0) {
            ((durationMs + DANMAKU_SEGMENT_DURATION_MS - 1) / DANMAKU_SEGMENT_DURATION_MS).toInt()
        } else {
            0
        }
        if (fromDuration > 0) return fromDuration

        // duration 与 metadata 同时缺失时，默认预取 3 段，避免从非首段位置进入时“无弹幕”
        return DANMAKU_SEGMENT_SAFE_FALLBACK_COUNT
    }

    /**
     * 获取 XML 格式弹幕原始数据（旧版 API，后备路径）
     */
    suspend fun getDanmakuRawData(cid: Long): ByteArray? = withContext(Dispatchers.IO) {
        // 先检查缓存
        synchronized(danmakuCache) {
            danmakuCache[cid]?.let { return@withContext it }
        }

        try {
            val responseBody = api.getDanmakuXml(cid)
            val bytes = responseBody.bytes()

            if (bytes.isEmpty()) {
                android.util.Log.w("DanmakuRepo", "Danmaku response is empty!")
                return@withContext null
            }

            val result: ByteArray?

            // 检查首字节判断是否压缩；XML 以 '<' 开头 (0x3C)
            if (bytes[0] == 0x3C.toByte()) {
                result = bytes
            } else {
                // 尝试 Deflate 解压
                result = try {
                    val inflater = java.util.zip.Inflater(true) // nowrap=true
                    inflater.setInput(bytes)
                    val outputStream = java.io.ByteArrayOutputStream(bytes.size * 3)
                    val tempBuffer = ByteArray(1024)
                    while (!inflater.finished()) {
                        val count = inflater.inflate(tempBuffer)
                        if (count == 0) {
                            if (inflater.needsInput()) break
                            if (inflater.needsDictionary()) break
                        }
                        outputStream.write(tempBuffer, 0, count)
                    }
                    inflater.end()
                    outputStream.toByteArray()
                } catch (e: Exception) {
                    android.util.Log.e("DanmakuRepo", "Deflate failed: ${e.message}")
                    // 解压失败，返回原始数据
                    bytes
                }
            }

            // 存入缓存（限制条目数与字节数）
            if (result != null && result.isNotEmpty()) {
                val entrySize = result.size.toLong()
                if (entrySize <= MAX_DANMAKU_CACHE_BYTES) {
                    synchronized(danmakuCache) {
                        danmakuCache.remove(cid)?.let { danmakuCacheBytes -= it.size.toLong() }

                        val iterator = danmakuCache.entries.iterator()
                        while (iterator.hasNext() &&
                            (danmakuCache.size >= MAX_DANMAKU_CACHE_COUNT ||
                                danmakuCacheBytes + entrySize > MAX_DANMAKU_CACHE_BYTES)
                        ) {
                            val eldest = iterator.next()
                            danmakuCacheBytes -= eldest.value.size.toLong()
                            iterator.remove()
                        }
                        danmakuCache[cid] = result
                        danmakuCacheBytes += entrySize
                    }
                }
            }

            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DanmakuRepo", "getDanmakuRawData failed: ${e.message}")
            null
        }
    }

    /**
     * 获取 Protobuf 格式弹幕单段（每段 6 分钟，下标从 1 开始）
     */
    suspend fun getDanmakuSegment(
        cid: Long,
        segmentIndex: Int
    ): ByteArray? = withContext(Dispatchers.IO) {
        require(segmentIndex >= 1) { "segmentIndex must be one-based" }
        val cacheKey = DanmakuSegmentCacheKey(cid, segmentIndex)
        synchronized(danmakuSegmentCache) {
            danmakuSegmentCache[cacheKey]?.let { return@withContext it }
        }

        val bytes = try {
            api.getDanmakuSeg(oid = cid, segmentIndex = segmentIndex).bytes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("DanmakuRepo", "Segment $segmentIndex failed: ${e.message}")
            return@withContext null
        }
        // A successful empty protobuf body is a valid segment with no comments.
        // Reserve null for fetch failures so playback can retry those segments.

        val entrySize = bytes.size.toLong()
        if (entrySize <= MAX_SEGMENT_CACHE_BYTES) {
            synchronized(danmakuSegmentCache) {
                danmakuSegmentCache.remove(cacheKey)?.let { removed ->
                    danmakuSegmentCacheBytes -= removed.size.toLong()
                }
                val iterator = danmakuSegmentCache.entries.iterator()
                while (
                    iterator.hasNext() &&
                    (danmakuSegmentCache.size >= MAX_SEGMENT_CACHE_COUNT ||
                        danmakuSegmentCacheBytes + entrySize > MAX_SEGMENT_CACHE_BYTES)
                ) {
                    val eldest = iterator.next()
                    danmakuSegmentCacheBytes -= eldest.value.size.toLong()
                    iterator.remove()
                }
                danmakuSegmentCache[cacheKey] = bytes
                danmakuSegmentCacheBytes += entrySize
            }
        }
        bytes
    }

    /**
     * 并发拉取整支视频的所有分段
     *
     * @param cid 视频 cid
     * @param durationMs 视频时长 (毫秒)，用于计算所需分段数
     * @param metadataSegmentCount 弹幕元数据返回的总分段数（可选）
     */
    suspend fun getDanmakuSegments(
        cid: Long,
        durationMs: Long,
        metadataSegmentCount: Int? = null
    ): List<ByteArray> = withContext(Dispatchers.IO) {
        val segmentCount = resolveSegmentCount(durationMs, metadataSegmentCount)

        data class SegmentResult(val index: Int, val bytes: ByteArray)

        // 并发获取分段，限制并发度避免过载
        val segmentResults = coroutineScope {
            val semaphore = Semaphore(MAX_SEGMENT_PARALLELISM)
            (1..segmentCount).map { index ->
                async {
                    semaphore.withPermit {
                        try {
                            getDanmakuSegment(cid, index)?.let { SegmentResult(index, it) }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            android.util.Log.w("DanmakuRepo", "Segment $index failed: ${e.message}")
                            null
                        }
                    }
                }
            }.awaitAll()
        }

        segmentResults
            .filterNotNull()
            .sortedBy { it.index }
            .map { it.bytes }
    }
}
