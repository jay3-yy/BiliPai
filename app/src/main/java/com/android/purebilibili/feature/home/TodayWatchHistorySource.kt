package com.android.purebilibili.feature.home

import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.repository.HistoryRepository
import com.android.purebilibili.data.repository.HistoryResult
import kotlinx.coroutines.CancellationException

internal const val TODAY_WATCH_HISTORY_CACHE_TTL_MS = 10 * 60 * 1000L

internal data class TodayWatchHistorySample(
    val videos: List<VideoItem>,
    val unavailable: Boolean = false,
)

/** 单账号的历史采样缓存。只提供挂起操作，任务生命周期和账号校验仍由调用方持有。 */
internal class TodayWatchHistorySource(
    private val loadPage: suspend (max: Long, viewAt: Long, business: String) -> Result<HistoryResult> =
        { max, viewAt, business -> HistoryRepository.getHistoryList(ps = 50, max = max, viewAt = viewAt, business = business) },
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private var cachedVideos: List<VideoItem> = emptyList()
    private var loadedAtMillis = 0L
    private var complete = false

    fun clear() {
        cachedVideos = emptyList()
        loadedAtMillis = 0L
        complete = false
    }

    suspend fun load(
        forceReload: Boolean,
        sampleLimit: Int,
        ensureCurrent: suspend () -> Unit,
    ): TodayWatchHistorySample {
        ensureCurrent()
        val now = nowMillis()
        val limit = sampleLimit.coerceIn(20, 120)
        if (!forceReload && cachedVideos.isNotEmpty() &&
            (cachedVideos.size >= limit || complete) &&
            now - loadedAtMillis < TODAY_WATCH_HISTORY_CACHE_TTL_MS
        ) {
            return TodayWatchHistorySample(cachedVideos.take(limit))
        }
        val merged = mutableListOf<VideoItem>()
        var max = 0L
        var viewAt = 0L
        var business = ""
        var pages = 0
        var reachedEnd = false
        while (merged.size < limit && pages < 3) {
            val result = loadPage(max, viewAt, business)
            ensureCurrent()
            // Repository Result 中的取消仍须向上游传播，不能记成可复用的历史缓存。
            val failure = result.exceptionOrNull()
            if (failure is CancellationException) throw failure
            val page = result.getOrNull()
            if (page == null) {
                if (pages == 0) return TodayWatchHistorySample(emptyList(), unavailable = true)
                break
            }
            merged += page.list.map { it.toVideoItem() }
            pages++
            val cursor = page.cursor
            if (cursor == null || cursor.max <= 0L || page.list.isEmpty()) {
                reachedEnd = true
                break
            }
            max = cursor.max
            viewAt = cursor.view_at
            business = cursor.business
        }
        cachedVideos = merged.filter { it.bvid.isNotBlank() }.distinctBy { it.bvid }
        loadedAtMillis = now
        complete = reachedEnd
        return TodayWatchHistorySample(cachedVideos.take(limit))
    }
}
