package com.android.purebilibili.feature.plugin

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import com.android.purebilibili.core.player.PlaybackMediaCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

internal data class CdnDashPrefetchRequest(
    val candidates: List<String>,
    val indexRange: CdnByteRange,
    val trackCacheKey: String,
    val bufferedDurationMs: Long,
    val frontierPositionMs: Long
)

internal data class CdnDashPrefetchResult(
    val plannedSegments: Int,
    val cachedSegments: Int,
    val selectedHosts: List<String>
)

/**
 * Stateless, caller-owned DASH prefetch operation. It is deliberately suspend-only so player
 * sessions control cancellation on seeks, low buffer, replacement, and teardown.
 */
internal class CdnDashSegmentPrefetcher(
    private val context: Context,
    private val client: OkHttpClient
) {
    // OkHttpDataSource 与 CacheWriter 属 media3 unstable API：预取链路在应用层封装后消费，opt-in 标记会级联污染全部调用方。
    @SuppressLint("UnsafeOptInUsageError")
    suspend fun prefetch(request: CdnDashPrefetchRequest): CdnDashPrefetchResult = withContext(Dispatchers.IO) {
        val targetCount = resolveCdnPrefetchSegmentCount(request.bufferedDurationMs)
        if (targetCount == 0 || request.candidates.isEmpty()) {
            return@withContext CdnDashPrefetchResult(0, 0, emptyList())
        }
        val index = loadIndex(request.candidates, request.indexRange) ?: return@withContext CdnDashPrefetchResult(0, 0, emptyList())
        val frontierUs = request.frontierPositionMs.coerceAtLeast(0L) * 1_000L
        val segments = index.segments
            .filter { it.startTimeUs >= frontierUs }
            .take(targetCount)
        val selectedHosts = mutableListOf<String>()
        segments.forEach { segment ->
            currentCoroutineContext().ensureActive()
            // Live transfer samples rank nodes; do not download probe bytes for every segment.
            val winner = CdnTransferRuntime.rank(request.candidates).firstOrNull() ?: return@forEach
            val (upstreamFactory, cancelUpstream) = PlaybackMediaCache.buildCancelableOkHttpDataSourceFactory(
                callFactory = client,
                defaultRequestProperties = PLAYBACK_HEADERS,
            )
            try {
                PlaybackMediaCache.prefetchRange(
                    context = context,
                    upstreamFactory = upstreamFactory,
                    url = Uri.parse(winner),
                    cacheKey = request.trackCacheKey,
                    position = segment.range.start,
                    length = segment.range.length,
                    onCancelUpstream = cancelUpstream,
                )
                selectedHosts += hostFromCdnUrl(winner)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
            }
        }
        CdnDashPrefetchResult(
            plannedSegments = segments.size,
            cachedSegments = selectedHosts.size,
            selectedHosts = selectedHosts
        )
    }

    private suspend fun loadIndex(candidates: List<String>, range: CdnByteRange): CdnDashIndex? {
        candidates.forEach { url ->
            val bytes = readRange(url, range) ?: return@forEach
            parseCdnSidx(bytes, range.start)?.let { return it }
        }
        return null
    }

    private suspend fun readRange(url: String, range: CdnByteRange): ByteArray? {
        if (range.start < 0 || range.endInclusive < range.start || range.length !in 1..CDN_WINDOW_BYTES) return null
        return try {
            readExactCdnRange(client, url, range).bytes
        } catch (error: CancellationException) {
            throw error
        } catch (_: java.io.IOException) {
            null
        }
    }

    private companion object {
        val PLAYBACK_HEADERS = mapOf(
            "Referer" to "https://www.bilibili.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )
    }
}
