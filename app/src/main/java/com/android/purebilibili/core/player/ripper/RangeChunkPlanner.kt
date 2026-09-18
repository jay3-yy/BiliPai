package com.android.purebilibili.core.player.ripper

/** 单个字节块的请求范围，[start, start + length) 。 */
internal data class RangeChunk(
    val index: Int,
    val start: Long,
    val length: Long
) {
    val endInclusive: Long get() = start + length - 1
}

/** 解析 `Content-Range: bytes s-e/total` 后的结果，total 为 `*` 时为 null。 */
internal data class ContentRange(
    val start: Long,
    val endInclusive: Long,
    val total: Long?
) {
    val length: Long get() = endInclusive - start + 1
}

internal const val RIPPER_MIN_CHUNK_BYTES = 64L * 1024L
internal const val RIPPER_MAX_CHUNK_BYTES = 8L * 1024L * 1024L
internal const val RIPPER_MAX_CONCURRENCY = 64

private val CONTENT_RANGE_RE = Regex("""^bytes\s+(\d+)-(\d+)/(\d+|\*)$""", RegexOption.IGNORE_CASE)

internal fun parseContentRange(value: String?): ContentRange? {
    val match = CONTENT_RANGE_RE.matchEntire(value?.trim().orEmpty()) ?: return null
    val start = match.groupValues[1].toLongOrNull() ?: return null
    val end = match.groupValues[2].toLongOrNull() ?: return null
    val total = if (match.groupValues[3] == "*") null else match.groupValues[3].toLongOrNull() ?: return null
    if (end < start) return null
    if (total != null && total <= end) return null
    return ContentRange(start, end, total)
}

internal fun clampRipperConcurrency(value: Int): Int = value.coerceIn(1, RIPPER_MAX_CONCURRENCY)

internal fun clampRipperChunkBytes(value: Long): Long = value.coerceIn(RIPPER_MIN_CHUNK_BYTES, RIPPER_MAX_CHUNK_BYTES)

/**
 * 从 [position] 开始、在 [remaining] 字节内规划下一个块。
 *
 * 前几块用较小的 [firstChunkBytes]，让首帧尽快到达；之后用 [chunkBytes]。
 * [remaining] 为 null 表示总长度未知，此时只受块大小限制。
 */
internal fun planNextChunk(
    index: Int,
    position: Long,
    remaining: Long?,
    chunkBytes: Long,
    firstChunkBytes: Long = chunkBytes,
    warmupChunks: Int = 2
): RangeChunk? {
    if (remaining != null && remaining <= 0L) return null
    val preferred = if (index < warmupChunks) minOf(firstChunkBytes, chunkBytes) else chunkBytes
    val length = if (remaining == null) preferred else minOf(preferred, remaining)
    if (length <= 0L) return null
    return RangeChunk(index, position, length)
}

/**
 * 把 [start, start + length) 一次性拆成不超过 [concurrency] 个块，每块不小于 [minChunkBytes]。
 * 与浏览器插件 range-core 的 `splitRange` 行为一致，用于已知长度的短请求。
 */
internal fun splitRange(
    start: Long,
    length: Long,
    concurrency: Int,
    minChunkBytes: Long = RIPPER_MIN_CHUNK_BYTES
): List<RangeChunk> {
    if (length <= 0L) return emptyList()
    val limit = clampRipperConcurrency(concurrency)
    val minimum = maxOf(RIPPER_MIN_CHUNK_BYTES, minChunkBytes)
    val count = maxOf(1L, minOf(limit.toLong(), (length + minimum - 1) / minimum)).toInt()
    val base = length / count
    val extra = length % count
    var cursor = start
    return List(count) { index ->
        val size = base + if (index < extra) 1 else 0
        RangeChunk(index, cursor, size).also { cursor += size }
    }
}
