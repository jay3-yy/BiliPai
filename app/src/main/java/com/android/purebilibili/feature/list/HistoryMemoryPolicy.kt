package com.android.purebilibili.feature.list

import com.android.purebilibili.data.model.response.HistoryBusiness
import com.android.purebilibili.data.model.response.HistoryData
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class HistoryMemoryKind {
    ON_THIS_DAY,
    REWATCH_GEM,
}

data class HistoryMemoryPick(
    val kind: HistoryMemoryKind,
    val badgeText: String,
    val subtitle: String,
    val bvid: String,
    val cid: Long,
    val title: String,
    val coverUrl: String,
    val upName: String,
    val durationSec: Int,
    val viewAtSec: Long,
)

/**
 * 从观看历史中温和挑选 1 条「那年今日」或「久违重温」拾遗记录：
 * 1. 优先匹配同月同日且至少 1 年前的完整观看或视频历史；
 * 2. 否则从 14 天以前已看完（或观看超 60%）的历史视频中按当日日期稳定挑 1 条，避免每次刷新乱跳。
 */
internal fun pickHistoryMemoryFromData(
    items: List<HistoryData>,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): HistoryMemoryPick? {
    if (items.isEmpty()) return null
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val playableVideos = items.filter { item ->
        val bvid = item.history?.bvid.orEmpty()
        val business = HistoryBusiness.fromValue(item.history?.business.orEmpty())
        bvid.isNotBlank() &&
            item.view_at > 0L &&
            business in setOf(HistoryBusiness.ARCHIVE, HistoryBusiness.PGC, HistoryBusiness.CHEESE)
    }
    if (playableVideos.isEmpty()) return null

    // 1. 优先「那年今日」
    val onThisDay = playableVideos
        .mapNotNull { item ->
            val date = Instant.ofEpochSecond(item.view_at).atZone(zone).toLocalDate()
            val yearsAgo = today.year - date.year
            if (yearsAgo >= 1 && date.month == today.month && date.dayOfMonth == today.dayOfMonth) {
                yearsAgo to item
            } else {
                null
            }
        }
        .sortedWith(
            compareByDescending<Pair<Int, HistoryData>> { it.second.progress == -1 }
                .thenBy { it.first }
        )
        .firstOrNull()

    if (onThisDay != null) {
        val (yearsAgo, item) = onThisDay
        return item.toHistoryMemoryPick(
            kind = HistoryMemoryKind.ON_THIS_DAY,
            badgeText = "${yearsAgo} 年前的今天",
            subtitle = if (item.progress == -1) "那年今天完整看完了这部视频" else "那年今天曾点开过这部视频",
        )
    }

    // 2. 次选：14 天前完整看完（或观看 >= 60%）的久违重温
    val rewatchPool = playableVideos.mapNotNull { item ->
        val date = Instant.ofEpochSecond(item.view_at).atZone(zone).toLocalDate()
        val daysAgo = ChronoUnit.DAYS.between(date, today)
        val completedOrDeep = item.progress == -1 ||
            (item.duration > 0 && item.progress.toFloat() / item.duration.toFloat() >= 0.6f)
        if (daysAgo >= 14L && completedOrDeep) {
            daysAgo to item
        } else {
            null
        }
    }
    if (rewatchPool.isEmpty()) return null
    val stableIndex = ((today.toEpochDay() % rewatchPool.size) + rewatchPool.size).toInt() % rewatchPool.size
    val (daysAgo, picked) = rewatchPool[stableIndex]
    val timeHint = if (daysAgo >= 365L) {
        "${daysAgo / 365L} 年多前完整看过"
    } else if (daysAgo >= 30L) {
        "${daysAgo / 30L} 个月前完整看过"
    } else {
        "$daysAgo 天前完整看过"
    }
    return picked.toHistoryMemoryPick(
        kind = HistoryMemoryKind.REWATCH_GEM,
        badgeText = "往日重温",
        subtitle = timeHint,
    )
}

private fun HistoryData.toHistoryMemoryPick(
    kind: HistoryMemoryKind,
    badgeText: String,
    subtitle: String,
): HistoryMemoryPick {
    val rawCover = cover.ifBlank { covers.firstOrNull().orEmpty() }
    val normalizedCover = when {
        rawCover.startsWith("//") -> "https:$rawCover"
        rawCover.startsWith("http://") -> rawCover.replaceFirst("http://", "https://")
        else -> rawCover
    }
    return HistoryMemoryPick(
        kind = kind,
        badgeText = badgeText,
        subtitle = subtitle,
        bvid = history?.bvid.orEmpty(),
        cid = history?.cid ?: 0L,
        title = title.ifBlank { "未命名视频" },
        coverUrl = normalizedCover,
        upName = author_name,
        durationSec = duration.coerceAtLeast(0),
        viewAtSec = view_at,
    )
}
