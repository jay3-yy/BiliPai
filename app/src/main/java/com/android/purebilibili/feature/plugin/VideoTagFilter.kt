package com.android.purebilibili.feature.plugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/** Looks up actual archive tags only when the user has configured tag keywords. */
internal class VideoTagFilter(
    private val loadTags: suspend (String) -> List<String>,
    private val onFailure: (String, Exception) -> Unit = { _, _ -> },
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxCacheEntries: Int = 512,
    private val cacheTtlMs: Long = 30 * 60 * 1000L,
    private val batchTimeoutMs: Long = 4_000L,
) {
    private data class Entry(val tags: List<String>, val loadedAtMs: Long)

    private val cache = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private val requests = Semaphore(4)

    suspend fun <T> filter(
        items: List<T>,
        keywords: List<String>,
        bvid: (T) -> String,
        onBlocked: (T, String) -> Unit = { _, _ -> },
    ): List<T> {
        if (items.isEmpty() || keywords.isEmpty()) return items
        val normalizedKeywords = keywords.map(String::trim).filter(String::isNotEmpty).distinct()
        if (normalizedKeywords.isEmpty()) return items
        val matches = arrayOfNulls<String>(items.size)
        // The feed is already available: an auxiliary endpoint must not hold it for many
        // sequential network timeouts. Completed matches survive; unfinished lookups stay visible.
        // Only this deadline is consumed here; cancellation by the screen owner still propagates.
        withTimeoutOrNull(batchTimeoutMs) {
            coroutineScope {
                items.mapIndexed { index, item ->
                    async {
                        val id = bvid(item)
                        if (id.isNotBlank()) {
                            val tags = tagsFor(id)
                            matches[index] = normalizedKeywords.firstOrNull { keyword ->
                                tags.any { tag -> tag.contains(keyword, ignoreCase = true) }
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        if (matches.all { it == null }) return items
        return items.filterIndexed { index, item ->
            val keyword = matches[index]
            if (keyword != null) onBlocked(item, keyword)
            keyword == null
        }
    }

    private fun cachedTags(bvid: String): List<String>? = synchronized(cache) {
        val entry = cache[bvid] ?: return@synchronized null
        if (nowMs() - entry.loadedAtMs >= cacheTtlMs) {
            cache.remove(bvid)
            null
        } else entry.tags
    }

    private suspend fun tagsFor(bvid: String): List<String> {
        cachedTags(bvid)?.let { return it }
        return requests.withPermit {
            cachedTags(bvid)?.let { return@withPermit it }
            val tags = try {
                loadTags(bvid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // A failed lookup is not evidence that a video matches, and must not be cached.
                onFailure(bvid, error)
                return@withPermit emptyList()
            }
            synchronized(cache) {
                cache[bvid] = Entry(tags, nowMs())
                while (cache.size > maxCacheEntries) {
                    val oldest = cache.entries.iterator()
                    oldest.next()
                    oldest.remove()
                }
            }
            tags
        }
    }
}
