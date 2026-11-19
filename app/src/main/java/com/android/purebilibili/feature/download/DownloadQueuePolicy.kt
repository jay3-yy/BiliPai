package com.android.purebilibili.feature.download

internal fun isDownloadTaskActive(task: DownloadTask): Boolean {
    return when (task.status) {
        DownloadStatus.PENDING,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.MERGING -> true
        else -> false
    }
}

/** 默认并发下载数：WorkManager 每个任务独立 worker，串行仅由调度策略保证 */
internal const val DEFAULT_MAX_CONCURRENT_DOWNLOADS = 2

internal fun resolveNextQueuedDownloadTaskIds(
    tasks: Collection<DownloadTask>,
    maxConcurrent: Int = DEFAULT_MAX_CONCURRENT_DOWNLOADS
): List<String> {
    val slots = (maxConcurrent - tasks.count(::isDownloadTaskActive)).coerceAtLeast(0)
    if (slots == 0) return emptyList()

    return tasks
        .asSequence()
        .filter { it.status == DownloadStatus.QUEUED && !it.isWaitingForRetry }
        .sortedWith(compareBy<DownloadTask> { it.createdAt }.thenBy { it.id })
        .take(slots)
        .map { it.id }
        .toList()
}

/**
 * 批量任务串行排队，入队时解析的 DASH 地址可能几小时后才被消费；
 * 距任务创建超过阈值时，执行前先刷新一次播放地址。
 */
internal fun shouldRefreshStaleDownloadUrls(
    createdAtMs: Long,
    nowMs: Long,
    thresholdMs: Long = STALE_DOWNLOAD_URL_REFRESH_THRESHOLD_MS
): Boolean = createdAtMs in 1 until nowMs && (nowMs - createdAtMs) >= thresholdMs

internal const val STALE_DOWNLOAD_URL_REFRESH_THRESHOLD_MS = 30L * 60_000L
