package com.android.purebilibili.feature.plugin

internal const val CDN_WINDOW_BYTES = 1024L * 1024L
internal const val CDN_MIN_PARALLEL_BYTES = 256L * 1024L
internal const val CDN_MAX_CONNECTIONS = 4
internal const val CDN_ENHANCED_MAX_CONNECTIONS = 32
internal const val CDN_HEAD_PROBE_BYTES = 64L * 1024L
internal const val CDN_MIN_PIECE_BYTES = 128L * 1024L
internal const val CDN_DEFAULT_PIECE_BYTES = 256L * 1024L
internal const val CDN_MAX_PIECE_BYTES = 512L * 1024L

internal data class CdnContentRange(val start: Long, val end: Long, val total: Long)

internal fun parseCdnContentRange(value: String?): CdnContentRange? {
    val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
        .matchEntire(value?.trim().orEmpty()) ?: return null
    val (start, end, total) = match.destructured
    val first = start.toLongOrNull() ?: return null
    val last = end.toLongOrNull() ?: return null
    val size = total.toLongOrNull() ?: return null
    return CdnContentRange(first, last, size).takeIf { first >= 0 && last >= first && size > last }
}

internal fun isExactCdnRange(status: Int, header: String?, range: CdnByteRange): Boolean {
    val actual = parseCdnContentRange(header) ?: return false
    return status == 206 && actual.start == range.start && actual.end == range.endInclusive
}

internal fun splitCdnRange(start: Long, length: Long, connections: Int): List<CdnByteRange> {
    if (start < 0 || length <= 0 || length - 1 > Long.MAX_VALUE - start) return emptyList()
    val count = connections.coerceIn(1, CDN_MAX_CONNECTIONS).coerceAtMost(length.toIntSafe())
    val chunk = length / count
    return (0 until count).map { index ->
        val first = start + index * chunk
        CdnByteRange(first, if (index == count - 1) start + length - 1 else first + chunk - 1)
    }
}

private fun Long.toIntSafe(): Int = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

internal fun resolveCdnConnections(bufferMs: Long, speedBps: Long, bitrateBps: Long, limited: Boolean): Int = when {
    limited -> 1
    bufferMs >= 30_000 -> 1
    bufferMs < 5_000 || (bitrateBps > 0 && speedBps * 8 < bitrateBps) -> 3
    else -> 2
}

internal fun splitAdaptiveCdnPieces(
    start: Long,
    length: Long,
    targetConnections: Int,
    maxConnections: Int = CDN_ENHANCED_MAX_CONNECTIONS,
    startupBoundaryBytes: Long = 0L,
): List<CdnByteRange> {
    if (start < 0 || length <= 0 || length - 1 > Long.MAX_VALUE - start) return emptyList()
    val segments = if (startupBoundaryBytes in 1 until length) {
        listOf(
            start to startupBoundaryBytes,
            (start + startupBoundaryBytes) to (length - startupBoundaryBytes),
        )
    } else {
        listOf(start to length)
    }
    val desiredCount = targetConnections.coerceIn(1, maxConnections.coerceIn(1, CDN_ENHANCED_MAX_CONNECTIONS))
    return buildList {
        for ((segStart, segLen) in segments) {
            if (desiredCount <= 1) {
                var offset = 0L
                while (offset < segLen) {
                    val chunk = minOf(CDN_WINDOW_BYTES, segLen - offset)
                    add(CdnByteRange(segStart + offset, segStart + offset + chunk - 1))
                    offset += chunk
                }
            } else {
                val minPieces = ((segLen + CDN_MAX_PIECE_BYTES - 1L) / CDN_MAX_PIECE_BYTES)
                    .toIntSafe().coerceAtLeast(1)
                val maxPieces = (segLen / CDN_MIN_PIECE_BYTES).toIntSafe().coerceAtLeast(1)
                val pieceCount = maxOf(desiredCount, minPieces)
                    .coerceAtMost(maxPieces)
                    .coerceAtMost(segLen.toIntSafe())
                    .coerceAtLeast(1)
                val chunk = segLen / pieceCount
                for (index in 0 until pieceCount) {
                    val first = segStart + index * chunk
                    val end = if (index == pieceCount - 1) segStart + segLen - 1 else first + chunk - 1
                    add(CdnByteRange(first, end))
                }
            }
        }
    }
}

internal fun shouldHedgePiece(
    elapsedMs: Long,
    sinceLastProgressMs: Long,
    receivedBytes: Int,
    pieceLength: Int,
    bufferMs: Long,
    sessionConnectionBps: Long,
    alternateHostBps: Long,
): Boolean {
    if (pieceLength <= 0 || receivedBytes >= pieceLength) return false
    if (sinceLastProgressMs >= 900L) return true
    if (bufferMs >= 15_000L || elapsedMs < 1_200L) return false
    val currentRateBps = receivedBytes.toLong() * 1_000L / elapsedMs.coerceAtLeast(1L)
    if (alternateHostBps > 0L && alternateHostBps * 10L > currentRateBps * 15L) return true
    if (sessionConnectionBps > 0L && currentRateBps * 10L < sessionConnectionBps * 6L) return true
    return false
}

/** Half-life of historical playback penalties; new events do not indefinitely refresh old ones. */
internal fun decayCdnHealth(health: CdnCandidateHealth, nowMs: Long): CdnCandidateHealth {
    val baseline = health.healthWindowStartedAtMs.takeIf { it > 0 }
        ?: health.lastUpdatedAtMs.takeIf { it > 0 } ?: nowMs
    val periods = ((nowMs - baseline).coerceAtLeast(0) / (30 * 60_000L)).coerceAtMost(16).toInt()
    val staleProbe = health.lastProbeAtMs > 0 && nowMs - health.lastProbeAtMs > 2 * 60_000L
    return health.copy(
        firstFrameTimeoutCount = health.firstFrameTimeoutCount ushr periods,
        bufferingCount = health.bufferingCount ushr periods,
        playbackErrorCount = health.playbackErrorCount ushr periods,
        readyCount = health.readyCount ushr periods,
        manualProbeLatencyMs = if (staleProbe) null else health.manualProbeLatencyMs,
        manualProbeSpeedKbps = if (staleProbe) null else health.manualProbeSpeedKbps,
        healthWindowStartedAtMs = if (periods > 0) nowMs else baseline
    )
}
