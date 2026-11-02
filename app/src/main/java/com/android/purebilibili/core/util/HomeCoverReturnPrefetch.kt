package com.android.purebilibili.core.util

import android.content.Context
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 首页封面「返回预热」注册表。
 *
 * 详情页停留期间首页 scene 被 Nav3 卸掉，Coil 可能因内存压力逐出首页封面缓存；
 * 返回落位瞬间首页 live 恢复，封面若需重新解码（网络/磁盘）会出现
 * 「冻结帧有图 → live 占位 → 图加载完成」的闪变窗口。
 *
 * 卡片进入可见工作范围或封面请求变化时上报身份与解码尺寸；
 * 返回时优先预热源卡，使用与列表相同的请求身份与尺寸。
 */
internal data class HomeCoverReturnPrefetchEntry(
    val bvid: String,
    val url: String,
    val cacheKey: String,
    val decodeWidthPx: Int = 0,
    val decodeHeightPx: Int = 0,
)

internal const val HOME_COVER_RETURN_PREFETCH_MAX_BYTES = 16L * 1024 * 1024

/** 按 ARGB 上限估算；未测量的封面不得触发原图预热。 */
internal fun HomeCoverReturnPrefetchEntry.estimatedDecodeBytes(): Long? {
    if (decodeWidthPx <= 0 || decodeHeightPx <= 0) return null
    val pixels = decodeWidthPx.toLong() * decodeHeightPx
    return if (pixels > Long.MAX_VALUE / 4L) null else pixels * 4L
}

internal object HomeCoverReturnPrefetchRegistry {

    private const val MAX_REGISTERED_ENTRIES = 64

    // 插入序 = 最近可见序（LRU 语义）。dispose 不清除：详情打开后首页 scene
    // 被卸掉，返回时注册表必须仍保有「打开详情前可视」的快照。
    private val entries = LinkedHashMap<String, HomeCoverReturnPrefetchEntry>()

    /** 首页卡片可见时上报。幂等：同 cacheKey 重复上报只刷新顺序，不产生重复项。 */
    fun onCardVisible(entry: HomeCoverReturnPrefetchEntry) {
        if (entry.url.isBlank() || entry.cacheKey.isBlank()) return
        if (entry.estimatedDecodeBytes() == null) return
        // Maintain oldest-first order in O(1); reverse only when a return requests a snapshot.
        entries.remove(entry.cacheKey)
        entries[entry.cacheKey] = entry
        while (entries.size > MAX_REGISTERED_ENTRIES) {
            val oldest = entries.entries.iterator()
            oldest.next()
            oldest.remove()
        }
    }

    fun snapshot(): List<HomeCoverReturnPrefetchEntry> = entries.values.toList().asReversed()

    internal fun clearForTest() {
        entries.clear()
    }
}

/**
 * 返回预热候选决策：源卡优先，其余按最近可见序，按数量和解码字节预算截断。
 * 纯函数，便于单测锁定。
 */
internal fun resolveHomeCoverReturnPrefetchCandidates(
    visibleEntries: List<HomeCoverReturnPrefetchEntry>,
    sourceBvid: String?,
    maxCount: Int = 24,
    maxDecodeBytes: Long = HOME_COVER_RETURN_PREFETCH_MAX_BYTES,
): List<HomeCoverReturnPrefetchEntry> {
    if (maxCount <= 0 || maxDecodeBytes <= 0L) return emptyList()
    val normalizedSourceBvid = sourceBvid?.trim()?.takeIf { it.isNotEmpty() }
    val result = LinkedHashMap<String, HomeCoverReturnPrefetchEntry>()
    var remainingBytes = maxDecodeBytes
    fun addWithinBudget(entry: HomeCoverReturnPrefetchEntry) {
        if (entry.url.isBlank() || entry.cacheKey.isBlank() || result.containsKey(entry.cacheKey)) return
        val bytes = entry.estimatedDecodeBytes() ?: return
        if (bytes > remainingBytes) return
        result[entry.cacheKey] = entry
        remainingBytes -= bytes
    }
    val sourceEntry = visibleEntries.firstOrNull {
        it.bvid == normalizedSourceBvid && it.url.isNotBlank() && it.cacheKey.isNotBlank() &&
            it.estimatedDecodeBytes()?.let { bytes -> bytes <= maxDecodeBytes } == true
    }
    if (sourceEntry != null) {
        addWithinBudget(sourceEntry)
    }
    for (entry in visibleEntries) {
        if (entry.url.isBlank() || entry.cacheKey.isBlank()) continue
        if (result.size >= maxCount) break
        addWithinBudget(entry)
    }
    return result.values.toList()
}

/**
 * Caller owns cancellation. Limit outstanding decodes instead of enqueueing all covers in the
 * input callback; sized cards retain their exact decode size and cache identity.
 */
internal suspend fun prefetchHomeCoverImages(
    context: Context,
    entries: List<HomeCoverReturnPrefetchEntry>,
    imageLoader: ImageLoader? = null,
) {
    if (entries.isEmpty()) return
    val loader = imageLoader ?: SingletonImageLoader.get(context)
    // 入口再次校验，避免直接调用绕过尺寸和批次预算。
    val boundedEntries = resolveHomeCoverReturnPrefetchCandidates(entries, sourceBvid = null)
    prefetchHomeCoversWithBudget(boundedEntries) { entry ->
        loader.execute(
            ImageRequest.Builder(context.applicationContext)
                .data(entry.url)
                .size(entry.decodeWidthPx, entry.decodeHeightPx)
                .homeCoverDecodePolicy()
                .memoryCacheKey(entry.cacheKey)
                .diskCacheKey(entry.cacheKey)
                .build()
        )
    }
}

internal const val HOME_COVER_RETURN_PREFETCH_PARALLELISM = 2

internal suspend fun prefetchHomeCoversWithBudget(
    entries: List<HomeCoverReturnPrefetchEntry>,
    load: suspend (HomeCoverReturnPrefetchEntry) -> Unit,
) {
    val permits = Semaphore(HOME_COVER_RETURN_PREFETCH_PARALLELISM)
    coroutineScope {
        entries.map { entry ->
            async {
                permits.withPermit {
                    load(entry)
                }
            }
        }.awaitAll()
    }
}
