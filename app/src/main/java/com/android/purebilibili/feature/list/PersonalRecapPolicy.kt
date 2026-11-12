package com.android.purebilibili.feature.list

import com.android.purebilibili.core.plugin.feed.FeedReadingStore
import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.HistoryData
import com.android.purebilibili.data.repository.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** 回顾时间窗：今日 / 近七天 / 近一个月。 */
@kotlinx.serialization.Serializable
enum class PersonalRecapWindow(val label: String) {
    TODAY("今日"),
    LAST_SEVEN_DAYS("近七天"),
    LAST_MONTH("近一个月"),
}

/** 窗口起点：今日取当天零点，其余按滚动 24h 窗口。 */
fun resolvePersonalRecapWindowStart(nowMs: Long, window: PersonalRecapWindow): Long {
    return when (window) {
        PersonalRecapWindow.TODAY -> {
            val zone = ZoneId.systemDefault()
            // LocalDate.ofInstant requires API 34; this chain is available from API 26.
            Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
                .atStartOfDay(zone).toInstant().toEpochMilli()
        }
        PersonalRecapWindow.LAST_SEVEN_DAYS -> nowMs - 7L * 24 * 60 * 60 * 1000
        PersonalRecapWindow.LAST_MONTH -> nowMs - 30L * 24 * 60 * 60 * 1000
    }
}

data class PersonalRssRecapStats(
    val readCount: Int = 0,
    val topSourceKey: String? = null,
    val readTimestampsMs: List<Long> = emptyList(),
)

@kotlinx.serialization.Serializable
data class PersonalUpRecap(
    val mid: Long,
    val name: String,
    val face: String,
    val watchCount: Int,
    val totalDurationSec: Long,
)

@kotlinx.serialization.Serializable
data class PersonalVideoRecapStats(
    val videoCount: Int = 0,
    val totalDurationSec: Long = 0L,
    val finishedCount: Int = 0,
    val topUps: List<PersonalUpRecap> = emptyList(),
    val watchTimestampsMs: List<Long> = emptyList(),
    val historyMayBeIncomplete: Boolean = false,
)

/** RSS 侧回顾：统计窗口内已读条数与最常读的订阅源（sourceId）。 */
fun aggregatePersonalRssRecap(
    readTimestamps: Map<String, Long>,
    windowStartMs: Long,
): PersonalRssRecapStats {
    val inWindow = readTimestamps.filterValues { it >= windowStartMs }
    if (inWindow.isEmpty()) return PersonalRssRecapStats()
    // key 格式为 "${sourceId}\u001f${id}"，取首段聚合来源。
    val topSourceKey = inWindow.keys
        .mapNotNull { it.split('\u001f').firstOrNull()?.takeIf(String::isNotBlank) }
        .groupingBy { it }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
    return PersonalRssRecapStats(
        readCount = inWindow.size,
        topSourceKey = topSourceKey,
        readTimestampsMs = inWindow.values.toList(),
    )
}

/** 视频侧回顾：观看数、总时长与最近爱看的 UP 主（按观看次数排序）。 */
fun aggregatePersonalVideoRecap(
    items: List<HistoryData>,
    windowStartSec: Long,
): PersonalVideoRecapStats {
    val inWindow = items.filter {
        it.view_at >= windowStartSec && HistoryBusiness.fromValue(it.history?.business.orEmpty()) in
            setOf(HistoryBusiness.ARCHIVE, HistoryBusiness.PGC, HistoryBusiness.CHEESE)
    }
    if (inWindow.isEmpty()) return PersonalVideoRecapStats()
    val durationOf: (HistoryData) -> Long = { item ->
        // progress == -1 表示看完，用完整时长；否则按已看进度计。
        if (item.progress == -1) item.duration.coerceAtLeast(0).toLong()
        else item.progress.coerceIn(0, item.duration.coerceAtLeast(0)).toLong()
    }
    val totalDurationSec = inWindow.sumOf(durationOf)
    val topUps = inWindow
        .filter { it.author_mid > 0L }
        .groupBy { it.author_mid }
        .map { (mid, entries) ->
            PersonalUpRecap(
                mid = mid,
                name = entries.firstOrNull()?.author_name.orEmpty().ifBlank { "UP主" },
                face = entries.firstOrNull()?.author_face.orEmpty(),
                watchCount = entries.size,
                totalDurationSec = entries.sumOf(durationOf),
            )
        }
        .sortedWith(compareByDescending<PersonalUpRecap> { it.watchCount }.thenByDescending { it.totalDurationSec })
    return PersonalVideoRecapStats(
        videoCount = inWindow.size,
        totalDurationSec = totalDurationSec,
        finishedCount = inWindow.count { it.progress == -1 },
        topUps = topUps,
        watchTimestampsMs = inWindow.map { it.view_at * 1000L },
    )
}

/**
 * 按时间窗拉满视频历史（游标分页，view_at 单调递减），供回顾聚合。
 * 上限 [maxPages] 页防止重度用户无限翻页；失败交给调用方展示不可用状态。
 */
suspend fun fetchPersonalVideoRecapHistory(
    windowStartMs: Long,
    maxPages: Int = 10,
): List<HistoryData> = withContext(Dispatchers.IO) {
    val collected = mutableListOf<HistoryData>()
    var cursorMax = 0L
    var cursorViewAt = 0L
    for (pageIndex in 0 until maxPages) {
        val result = HistoryRepository.getHistoryList(ps = 30, max = cursorMax, viewAt = cursorViewAt)
            .getOrThrow()
        val page = result.list
        if (page.isEmpty()) break
        collected += page
        val last = page.last()
        if (last.view_at * 1000L < windowStartMs) break
        val cursor = result.cursor ?: break
        if (cursor.max <= 0L || (cursor.max == cursorMax && cursor.view_at == cursorViewAt)) break
        cursorMax = cursor.max
        cursorViewAt = cursor.view_at
    }
    collected
}

/** 每次进入历史页或完成刷新重新读取，避免跨账号和删除后的旧缓存。 */
object PersonalRecapRepository {
    suspend fun videoRecap(windowStartMs: Long): PersonalVideoRecapStats {
        val items = fetchPersonalVideoRecapHistory(windowStartMs)
        return aggregatePersonalVideoRecap(
            items = items,
            windowStartSec = windowStartMs / 1000L,
        ).copy(
            historyMayBeIncomplete = items.size >= 300 &&
                (items.lastOrNull()?.view_at ?: 0L) >= windowStartMs / 1000L,
        )
    }

    suspend fun rssRecap(context: android.content.Context, windowStartMs: Long): PersonalRssRecapStats =
        aggregatePersonalRssRecap(
            readTimestamps = FeedReadingStore.loadTimestamps(context),
            windowStartMs = windowStartMs,
        )
}
