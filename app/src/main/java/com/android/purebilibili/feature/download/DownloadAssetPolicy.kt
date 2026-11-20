package com.android.purebilibili.feature.download

private const val DEFAULT_PARALLEL_CHUNK_THRESHOLD_BYTES = 8L * 1024L * 1024L

internal data class DownloadRangeProbe(
    val totalBytes: Long,
    val supportsRanges: Boolean,
    val strongETag: String? = null,
)

internal data class DownloadChunkRange(
    val index: Int,
    val startBytes: Long,
    val endBytesInclusive: Long
) {
    val sizeBytes: Long get() = endBytesInclusive - startBytes + 1L
}

internal data class DownloadAssetSummary(
    val videoText: String?,
    val audioText: String?,
    val danmakuText: String?
)

internal fun DownloadTask.assetState(kind: DownloadAssetKind): DownloadAssetState? {
    return assets.lastOrNull { it.kind == kind }
}

internal fun DownloadTask.withAssetState(next: DownloadAssetState): DownloadTask {
    val replaced = assets.filterNot { it.kind == next.kind } + next
    return copy(assets = replaced.sortedBy { it.kind.ordinal })
}

internal fun shouldUseParallelChunks(
    totalBytes: Long,
    supportsRanges: Boolean,
    thresholdBytes: Long = DEFAULT_PARALLEL_CHUNK_THRESHOLD_BYTES,
    maxChunks: Int = 4
): Boolean {
    return supportsRanges && totalBytes >= thresholdBytes && maxChunks > 1
}

internal fun shouldReuseCompletedAsset(
    outputBytes: Long,
    totalBytes: Long
): Boolean = totalBytes > 0L && outputBytes == totalBytes

internal fun shouldDiscardCompletedAsset(
    outputBytes: Long,
    totalBytes: Long
): Boolean = totalBytes > 0L && outputBytes > totalBytes

internal fun resolveCompletedChunkBytes(
    chunkBytes: Long,
    expectedBytes: Long
): Long {
    return if (chunkBytes in 1L..expectedBytes) chunkBytes else 0L
}

internal fun isDownloadChunkComplete(
    chunkBytes: Long,
    expectedBytes: Long
): Boolean = chunkBytes == expectedBytes

internal fun resolveDownloadChunkRanges(
    totalBytes: Long,
    maxChunks: Int = 4
): List<DownloadChunkRange> {
    if (totalBytes <= 0L || maxChunks <= 1) {
        return listOf(DownloadChunkRange(index = 0, startBytes = 0L, endBytesInclusive = (totalBytes - 1L).coerceAtLeast(0L)))
    }
    val chunkCount = maxChunks.coerceAtMost(totalBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt().coerceAtLeast(1))
    val baseSize = totalBytes / chunkCount
    val remainder = totalBytes % chunkCount
    var start = 0L
    return (0 until chunkCount).map { index ->
        val size = baseSize + if (index < remainder) 1L else 0L
        val end = start + size - 1L
        DownloadChunkRange(index = index, startBytes = start, endBytesInclusive = end).also {
            start = end + 1L
        }
    }
}

internal fun parseDownloadContentRange(contentRange: String?): DownloadRangeProbe? {
    val match = Regex("""bytes\s+(\d+)-(\d+)/(\d+|\*)""")
        .matchEntire(contentRange?.trim().orEmpty())
        ?: return null
    val total = match.groupValues[3].toLongOrNull() ?: 0L
    return DownloadRangeProbe(
        totalBytes = total.coerceAtLeast(0L),
        supportsRanges = true
    )
}

internal fun resolveDownloadAssetSummary(task: DownloadTask): DownloadAssetSummary {
    fun textFor(kind: DownloadAssetKind, label: String): String? {
        val state = task.assetState(kind) ?: return null
        return when (state.status) {
            DownloadAssetStatus.DOWNLOADING -> "$label ${formatAssetProgressPercent(state)}%"
            DownloadAssetStatus.COMPLETED -> "${label}完成"
            DownloadAssetStatus.FAILED -> "${label}失败"
            DownloadAssetStatus.SKIPPED -> "${label}跳过"
            DownloadAssetStatus.PENDING -> "${label}等待"
        }
    }
    return DownloadAssetSummary(
        videoText = textFor(DownloadAssetKind.VIDEO, "视频"),
        audioText = textFor(DownloadAssetKind.AUDIO, "音频"),
        danmakuText = textFor(DownloadAssetKind.DANMAKU, "弹幕")
    )
}

private fun formatAssetProgressPercent(state: DownloadAssetState): Int {
    if (state.totalBytes <= 0L) return 0
    return ((state.downloadedBytes.coerceAtLeast(0L).toDouble() / state.totalBytes.toDouble()) * 100.0)
        .toInt()
        .coerceIn(0, 100)
}
