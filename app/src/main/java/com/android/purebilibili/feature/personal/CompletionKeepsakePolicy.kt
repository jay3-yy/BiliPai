package com.android.purebilibili.feature.personal

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
enum class CompletionKeepsakeKind(val badgeLabel: String) {
    BANGUMI("追番完结"),
    COLLECTION("合集完结"),
    MULTI_PART("全集看完"),
}

data class CompletionKeepsakePrompt(
    val id: String,
    val kind: CompletionKeepsakeKind,
    val title: String,
    val episodeCount: Int,
    val coverUrl: String,
    val bvid: String = "",
    val seasonId: Long = 0L,
)

@Serializable
data class CompletionKeepsakeRecord(
    val id: String,
    val kind: CompletionKeepsakeKind,
    val title: String,
    val episodeCount: Int,
    val coverUrl: String,
    val reflection: String,
    val completedAtMs: Long,
    val bvid: String = "",
    val seasonId: Long = 0L,
)

internal fun resolveBangumiCompletionKeepsake(
    seasonId: Long,
    title: String,
    coverUrl: String,
    episodeCount: Int,
    currentEpisodeIndex: Int,
): CompletionKeepsakePrompt? {
    if (seasonId <= 0L || episodeCount < 2) return null
    if (currentEpisodeIndex != episodeCount - 1) return null
    return CompletionKeepsakePrompt(
        id = "bangumi:$seasonId",
        kind = CompletionKeepsakeKind.BANGUMI,
        title = title.ifBlank { "番剧" },
        episodeCount = episodeCount,
        coverUrl = normalizeKeepsakeCover(coverUrl),
        seasonId = seasonId,
    )
}

internal fun resolveVideoSeriesCompletionKeepsake(
    bvid: String,
    title: String,
    coverUrl: String,
    cid: Long,
    pageCids: List<Long>,
    seasonId: Long? = null,
    seasonTitle: String? = null,
    seasonEpisodeBvids: List<String> = emptyList(),
): CompletionKeepsakePrompt? {
    if (bvid.isBlank()) return null

    // 优先检测合集最后一集（至少 2 集）
    if (seasonId != null && seasonId > 0L && seasonEpisodeBvids.size >= 2) {
        val lastEpisodeBvid = seasonEpisodeBvids.lastOrNull()
        if (lastEpisodeBvid == bvid) {
            return CompletionKeepsakePrompt(
                id = "collection:$seasonId",
                kind = CompletionKeepsakeKind.COLLECTION,
                title = seasonTitle?.takeIf { it.isNotBlank() } ?: title.ifBlank { "视频合集" },
                episodeCount = seasonEpisodeBvids.size,
                coverUrl = normalizeKeepsakeCover(coverUrl),
                bvid = bvid,
                seasonId = seasonId,
            )
        }
    }

    // 次选检测多 P 视频最后一 P（至少 2 P）
    if (pageCids.size >= 2 && cid > 0L && pageCids.lastOrNull() == cid) {
        return CompletionKeepsakePrompt(
            id = "multipart:$bvid",
            kind = CompletionKeepsakeKind.MULTI_PART,
            title = title.ifBlank { "多P视频" },
            episodeCount = pageCids.size,
            coverUrl = normalizeKeepsakeCover(coverUrl),
            bvid = bvid,
        )
    }

    return null
}

private fun normalizeKeepsakeCover(raw: String): String = when {
    raw.startsWith("//") -> "https:$raw"
    raw.startsWith("http://") -> raw.replaceFirst("http://", "https://")
    else -> raw
}

internal fun filterCompletionKeepsakes(
    records: List<CompletionKeepsakeRecord>,
    kindFilter: CompletionKeepsakeKind? = null,
    query: String = "",
): List<CompletionKeepsakeRecord> {
    val normalizedQuery = query.trim().lowercase()
    return records.filter { record ->
        val kindMatched = kindFilter == null || record.kind == kindFilter
        val queryMatched = normalizedQuery.isEmpty() ||
            record.title.lowercase().contains(normalizedQuery) ||
            record.reflection.lowercase().contains(normalizedQuery)
        kindMatched && queryMatched
    }
}

private val KEEPSAKE_MONTH_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM", Locale.US)
private val KEEPSAKE_MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("yyyy年M月", Locale.getDefault())

data class CompletionKeepsakeMonthGroup(
    val monthKey: String,
    val monthLabel: String,
    val records: List<CompletionKeepsakeRecord>,
)

internal fun groupCompletionKeepsakesByMonth(
    records: List<CompletionKeepsakeRecord>,
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<CompletionKeepsakeMonthGroup> {
    if (records.isEmpty()) return emptyList()
    val grouped = linkedMapOf<String, MutableList<CompletionKeepsakeRecord>>()
    val labels = mutableMapOf<String, String>()
    records
        .sortedByDescending { it.completedAtMs }
        .forEach { record ->
            val zoned = Instant.ofEpochMilli(record.completedAtMs).atZone(zoneId)
            val key = KEEPSAKE_MONTH_KEY_FORMAT.format(zoned)
            labels.putIfAbsent(key, KEEPSAKE_MONTH_LABEL_FORMAT.format(zoned))
            grouped.getOrPut(key) { mutableListOf() }.add(record)
        }
    return grouped.map { (key, list) ->
        CompletionKeepsakeMonthGroup(
            monthKey = key,
            monthLabel = labels[key] ?: key,
            records = list,
        )
    }
}
