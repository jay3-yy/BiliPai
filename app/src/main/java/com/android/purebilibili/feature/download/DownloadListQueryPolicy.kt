package com.android.purebilibili.feature.download

import com.android.purebilibili.core.util.PinyinUtils
import java.text.Collator
import java.util.Locale

internal enum class DownloadListSortOrder(
    val storageKey: String,
    val label: String,
) {
    RECENT_FIRST("recent_first", "最近缓存"),
    OLDEST_FIRST("oldest_first", "最早缓存"),
    SIZE_DESC("size_desc", "文件从大到小"),
    DURATION_DESC("duration_desc", "时长从长到短"),
    TITLE_ASC("title_asc", "标题 A-Z"),
    STATUS_FIRST("status_first", "下载中优先"),
    ;

    companion object {
        fun fromStorageKey(raw: String?): DownloadListSortOrder {
            return entries.firstOrNull { it.storageKey == raw } ?: RECENT_FIRST
        }
    }
}

internal fun resolveDownloadTaskSortSizeBytes(task: DownloadTask): Long {
    if (task.fileSize > 0L) return task.fileSize
    val assetBytes = task.assets.sumOf { asset ->
        maxOf(asset.totalBytes, asset.downloadedBytes, 0L)
    }
    return maxOf(task.downloadedSize.coerceAtLeast(0L), assetBytes)
}

internal fun resolveDownloadStatusSortRank(status: DownloadStatus): Int = when (status) {
    DownloadStatus.DOWNLOADING,
    DownloadStatus.MERGING -> 0
    DownloadStatus.QUEUED,
    DownloadStatus.PENDING -> 1
    DownloadStatus.PAUSED -> 2
    DownloadStatus.FAILED -> 3
    DownloadStatus.COMPLETED -> 4
}

internal fun matchesDownloadTaskQuery(
    task: DownloadTask,
    query: String,
): Boolean {
    val tokens = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return true
    return tokens.all { token -> matchesDownloadTaskToken(task, token) }
}

private fun matchesDownloadTaskToken(
    task: DownloadTask,
    token: String,
): Boolean {
    if (PinyinUtils.matches(task.title, token)) return true
    if (task.ownerName.isNotBlank() && PinyinUtils.matches(task.ownerName, token)) return true
    val episodeLabel = task.episodeLabel
    if (!episodeLabel.isNullOrBlank() && PinyinUtils.matches(episodeLabel, token)) return true
    val groupTitle = task.groupTitle
    if (!groupTitle.isNullOrBlank() && PinyinUtils.matches(groupTitle, token)) return true
    if (task.bvid.contains(token, ignoreCase = true)) return true
    if (task.qualityDesc.contains(token, ignoreCase = true)) return true
    if (task.isAudioOnly && (PinyinUtils.matches("音频", token) || "audio".contains(token, ignoreCase = true))) {
        return true
    }
    return false
}

internal fun filterAndSortDownloadTasks(
    tasks: Collection<DownloadTask>,
    query: String,
    sortOrder: DownloadListSortOrder,
): List<DownloadTask> {
    val filtered = if (query.isBlank()) {
        tasks.toList()
    } else {
        tasks.filter { matchesDownloadTaskQuery(it, query) }
    }
    if (filtered.size <= 1) return filtered
    return filtered.sortedWith(resolveDownloadTaskComparator(sortOrder))
}

private fun resolveDownloadTaskComparator(
    sortOrder: DownloadListSortOrder,
): Comparator<DownloadTask> {
    return when (sortOrder) {
        DownloadListSortOrder.RECENT_FIRST -> compareByDescending<DownloadTask> { it.createdAt }
            .thenBy { it.episodeSortIndex }
            .thenBy { it.id }

        DownloadListSortOrder.OLDEST_FIRST -> compareBy<DownloadTask> { it.createdAt }
            .thenBy { it.episodeSortIndex }
            .thenBy { it.id }

        DownloadListSortOrder.SIZE_DESC -> compareByDescending<DownloadTask> { resolveDownloadTaskSortSizeBytes(it) }
            .thenByDescending { it.createdAt }
            .thenBy { it.id }

        DownloadListSortOrder.DURATION_DESC -> compareByDescending<DownloadTask> { it.duration }
            .thenByDescending { it.createdAt }
            .thenBy { it.id }

        DownloadListSortOrder.TITLE_ASC -> {
            val collator = Collator.getInstance(Locale.CHINA)
            Comparator<DownloadTask> { a, b ->
                val titleCompare = collator.compare(a.title.trim(), b.title.trim())
                if (titleCompare != 0) return@Comparator titleCompare
                val episodeCompare = a.episodeSortIndex.compareTo(b.episodeSortIndex)
                if (episodeCompare != 0) return@Comparator episodeCompare
                val timeCompare = b.createdAt.compareTo(a.createdAt)
                if (timeCompare != 0) return@Comparator timeCompare
                a.id.compareTo(b.id)
            }
        }

        DownloadListSortOrder.STATUS_FIRST -> compareBy<DownloadTask> { resolveDownloadStatusSortRank(it.status) }
            .thenByDescending { it.createdAt }
            .thenBy { it.episodeSortIndex }
            .thenBy { it.id }
    }
}

internal sealed interface DownloadListEntry {
    val key: String
    val tasks: List<DownloadTask>

    data class Single(val task: DownloadTask) : DownloadListEntry {
        override val key: String get() = task.id
        override val tasks: List<DownloadTask> get() = listOf(task)
    }

    data class MultiPartGroup(
        val bvid: String,
        val groupTitle: String,
        override val tasks: List<DownloadTask>,
    ) : DownloadListEntry {
        override val key: String get() = "group_$bvid"
        val primaryTask: DownloadTask get() = tasks.first()
    }
}

internal fun resolveMultiPartGroupTitle(tasks: List<DownloadTask>): String {
    val explicit = tasks.firstNotNullOfOrNull { it.groupTitle?.trim()?.takeIf(String::isNotEmpty) }
    if (explicit != null) return explicit
    val first = tasks.firstOrNull() ?: return ""
    val rawTitle = first.title.trim()
    val label = first.episodeLabel?.trim().orEmpty()
    if (label.isNotEmpty()) {
        for (separator in listOf(" - ", " · ")) {
            val suffix = "$separator$label"
            if (rawTitle.endsWith(suffix) && rawTitle.length > suffix.length) {
                return rawTitle.removeSuffix(suffix).trim()
            }
        }
    }
    return rawTitle
}

/**
 * 将同一 BV 号下的多个分P任务合并为单个分组条目；单分P视频保持独立卡片。
 * 分组在列表中的位置保持其首个任务在当前排序下的位置，组内按分P序号升序排列。
 */
internal fun buildDownloadListEntries(
    tasks: List<DownloadTask>,
    groupMultiPart: Boolean,
): List<DownloadListEntry> {
    if (!groupMultiPart || tasks.size <= 1) {
        return tasks.map(DownloadListEntry::Single)
    }
    val groupedByBvid = LinkedHashMap<String, MutableList<DownloadTask>>()
    tasks.forEach { task ->
        val normalizedBvid = task.bvid.trim()
        if (normalizedBvid.isNotEmpty()) {
            groupedByBvid.getOrPut(normalizedBvid) { mutableListOf() }.add(task)
        }
    }
    val emittedBvids = HashSet<String>()
    val entries = ArrayList<DownloadListEntry>(tasks.size)
    tasks.forEach { task ->
        val normalizedBvid = task.bvid.trim()
        if (normalizedBvid.isEmpty()) {
            entries.add(DownloadListEntry.Single(task))
            return@forEach
        }
        val bucket = groupedByBvid[normalizedBvid].orEmpty()
        if (bucket.size <= 1) {
            entries.add(DownloadListEntry.Single(task))
        } else if (emittedBvids.add(normalizedBvid)) {
            val orderedParts = bucket.sortedWith(
                compareBy<DownloadTask>(
                    { resolveOfflineEpisodeSortIndex(it) },
                    { it.createdAt },
                    { it.cid },
                    { it.id },
                )
            )
            entries.add(
                DownloadListEntry.MultiPartGroup(
                    bvid = normalizedBvid,
                    groupTitle = resolveMultiPartGroupTitle(orderedParts),
                    tasks = orderedParts,
                )
            )
        }
    }
    return entries
}

internal fun resolveDownloadSelectionAllToggle(
    visibleCompletedIds: Set<String>,
    currentSelected: Set<String>,
): Set<String> {
    if (visibleCompletedIds.isEmpty()) return currentSelected
    val allVisibleSelected = visibleCompletedIds.all { it in currentSelected }
    return if (allVisibleSelected) {
        currentSelected - visibleCompletedIds
    } else {
        currentSelected + visibleCompletedIds
    }
}
