package com.android.purebilibili.feature.plugin

import com.android.purebilibili.plugin.sdk.PlaybackCdnCandidate

import java.net.URI
import java.security.MessageDigest

internal const val CDN_PREFETCH_SAFE_BUFFER_MS = 15_000L
internal const val CDN_PREFETCH_PROBE_BYTES = 32L * 1024L

/** The normal profile never invents a CDN address; it only ranks playurl candidates. */
enum class CdnOptimizationProfile {
    SAFE_SIGNED,
    LEGACY_EXPERIMENTAL
}

data class CdnAuthorizedCandidate(
    val url: String,
    val host: String = hostFromCdnUrl(url),
    val risky: Boolean = isRiskyCdnUrl(url)
)

data class CdnByteRange(
    val start: Long,
    val endInclusive: Long
) {
    val length: Long get() = (endInclusive - start + 1L).coerceAtLeast(0L)
}

data class CdnDashSegment(
    val range: CdnByteRange,
    val startTimeUs: Long,
    val durationUs: Long
)

data class CdnDashIndex(
    val segments: List<CdnDashSegment>,
    val timescale: Long
)

internal fun parseCdnByteRange(raw: String?): CdnByteRange? {
    val parts = raw?.trim()?.split('-', limit = 2) ?: return null
    val start = parts.getOrNull(0)?.toLongOrNull() ?: return null
    val end = parts.getOrNull(1)?.toLongOrNull() ?: return null
    return CdnByteRange(start, end).takeIf { it.length > 0L }
}

internal fun buildAuthorizedCdnCandidates(urls: List<String>): List<CdnAuthorizedCandidate> {
    return urls.asSequence()
        .filter { it.isNotBlank() }
        .map(::CdnAuthorizedCandidate)
        .distinctBy { it.url }
        .toList()
}

internal fun sortAuthorizedCdnCandidates(
    candidates: List<CdnAuthorizedCandidate>,
    healthByHost: Map<String, CdnCandidateHealth>,
    pinnedHost: String? = null
): List<CdnAuthorizedCandidate> {
    val normalizedPin = pinnedHost?.lowercase()?.takeIf { it.isNotBlank() }
    return candidates.sortedWith(
        compareByDescending<CdnAuthorizedCandidate> { it.host.equals(normalizedPin, ignoreCase = true) }
            .thenBy { it.risky }
            .thenByDescending { scoreCdnCandidate(healthByHost[it.host]) }
    )
}

internal fun sortSafeSignedPlaybackCandidates(
    candidates: List<PlaybackCdnCandidate>,
    healthByHost: Map<String, CdnCandidateHealth>,
    pinnedHost: String? = null
): List<PlaybackCdnCandidate> {
    val normalizedPin = pinnedHost?.lowercase()?.takeIf { it.isNotBlank() }
    return candidates.sortedWith(
        compareByDescending<PlaybackCdnCandidate> {
            hostFromCdnUrl(it.videoUrl).equals(normalizedPin, ignoreCase = true)
        }.thenBy {
            isRiskyCdnUrl(it.videoUrl) || it.audioUrl?.let(::isRiskyCdnUrl) == true
        }.thenByDescending {
            scoreCdnCandidate(healthByHost[hostFromCdnUrl(it.videoUrl)])
        }
    )
}

internal fun buildPlaybackCdnCacheKeys(candidates: List<PlaybackCdnCandidate>): Map<String, String> {
    return buildMap {
        candidates.forEach { candidate ->
            if (candidate.videoUrl.isNotBlank()) {
                put(candidate.videoUrl, buildCdnTrackCacheKey(trackId = "video", url = candidate.videoUrl))
            }
            candidate.audioUrl?.takeIf { it.isNotBlank() }?.let { audioUrl ->
                put(audioUrl, buildCdnTrackCacheKey(trackId = "audio", url = audioUrl))
            }
        }
    }
}

/** 常规镜像也可能通过 os=mcdn 标记资源来源，只看域名会漏判。 */
internal fun isRiskyCdnUrl(url: String): Boolean =
    isRiskyCdnHost(hostFromCdnUrl(url)) ||
        URI(url).rawQuery.orEmpty().split('&').any { it == "os=mcdn" }

internal fun isRiskyCdnHost(host: String): Boolean {
    val normalized = host.lowercase()
    return normalized.contains("mcdn") ||
        normalized.contains("pcdn") ||
        normalized.contains("gotcha") ||
        // 与 core-data 的 isPcdnUrl 保持一致，避免回退时把 PCDN 地址捡回来
        riskyCdnHostSuffixes.any { normalized.contains(it) } ||
        normalized.matches(Regex("^\\d{1,3}(?:\\.\\d{1,3}){3}$"))
}

/** 已知的第三方 PCDN 域名后缀，与 core-data/VideoResponse.pcdnHostSuffixes 同口径。 */
private val riskyCdnHostSuffixes = listOf(
    "szbdyd.com",
    "mountaintoys.cn",
    "qrstuvwxyzab.com",
    "zhenidc.cn",
)

internal fun resolveCdnPrefetchSegmentCount(bufferedDurationMs: Long): Int {
    return when {
        bufferedDurationMs < CDN_PREFETCH_SAFE_BUFFER_MS -> 0
        bufferedDurationMs < 30_000L -> 3
        bufferedDurationMs < 60_000L -> 5
        else -> 8
    }
}

/**
 * Produces a stable key for a single media track across signed mirrors. The supplied track id
 * is intentionally part of the key so video and audio paths can never share cache spans.
 */
internal fun buildCdnTrackCacheKey(trackId: String, url: String): String {
    val path = runCatching { URI(url).rawPath.orEmpty() }.getOrDefault("")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest((trackId + '\u0000' + path).toByteArray())
        .take(12)
        .joinToString("") { "%02x".format(it) }
    return "bili-cdn-track:$digest"
}

/** Parses an ISO-BMFF sidx response whose first byte maps to [indexRangeStart]. */
internal fun parseCdnSidx(bytes: ByteArray, indexRangeStart: Long): CdnDashIndex? {
    var offset = 0
    while (offset + 8 <= bytes.size) {
        val size32 = readUInt32(bytes, offset) ?: return null
        val type = bytes.copyOfRange(offset + 4, offset + 8).decodeToString()
        val headerSize = when (size32) {
            1L -> 16
            0L -> bytes.size - offset
            else -> 8
        }
        val boxSize = when (size32) {
            1L -> readUInt64(bytes, offset + 8) ?: return null
            0L -> (bytes.size - offset).toLong()
            else -> size32
        }
        if (boxSize < headerSize || offset + boxSize > bytes.size) return null
        if (type == "sidx") {
            return parseSidxBox(
                bytes = bytes,
                boxOffset = offset,
                boxSize = boxSize.toInt(),
                headerSize = headerSize,
                absoluteBoxOffset = indexRangeStart + offset
            )
        }
        offset += boxSize.toInt()
    }
    return null
}

private fun parseSidxBox(
    bytes: ByteArray,
    boxOffset: Int,
    boxSize: Int,
    headerSize: Int,
    absoluteBoxOffset: Long
): CdnDashIndex? {
    var cursor = boxOffset + headerSize
    val version = bytes.getOrNull(cursor)?.toInt()?.and(0xff) ?: return null
    cursor += 4 // version + flags
    cursor += 4 // reference id
    val timescale = readUInt32(bytes, cursor) ?: return null
    if (timescale <= 0L) return null
    cursor += 4
    val earliestPresentationTime: Long
    val firstOffset: Long
    if (version == 0) {
        earliestPresentationTime = readUInt32(bytes, cursor) ?: return null
        cursor += 4
        firstOffset = readUInt32(bytes, cursor) ?: return null
        cursor += 4
    } else if (version == 1) {
        earliestPresentationTime = readUInt64(bytes, cursor) ?: return null
        cursor += 8
        firstOffset = readUInt64(bytes, cursor) ?: return null
        cursor += 8
    } else {
        return null
    }
    cursor += 2 // reserved
    val referenceCount = readUInt16(bytes, cursor) ?: return null
    cursor += 2
    var segmentOffset = absoluteBoxOffset + boxSize + firstOffset
    var segmentTime = earliestPresentationTime
    val segments = buildList {
        repeat(referenceCount) {
            val reference = readUInt32(bytes, cursor) ?: return null
            cursor += 4
            val referenceType = reference shr 31
            val referencedSize = reference and 0x7fff_ffffL
            val duration = readUInt32(bytes, cursor) ?: return null
            cursor += 4
            cursor += 4 // starts_with_SAP, SAP type, SAP delta time
            if (referencedSize <= 0L) return null
            if (referenceType == 0L) {
                add(
                    CdnDashSegment(
                        range = CdnByteRange(segmentOffset, segmentOffset + referencedSize - 1L),
                        startTimeUs = segmentTime * 1_000_000L / timescale,
                        durationUs = duration * 1_000_000L / timescale
                    )
                )
            }
            segmentOffset += referencedSize
            segmentTime += duration
        }
    }
    return CdnDashIndex(segments = segments, timescale = timescale)
}

private fun readUInt16(bytes: ByteArray, offset: Int): Int? {
    if (offset < 0 || offset + 2 > bytes.size) return null
    return ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
}

private fun readUInt32(bytes: ByteArray, offset: Int): Long? {
    if (offset < 0 || offset + 4 > bytes.size) return null
    return (0 until 4).fold(0L) { value, index ->
        (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
    }
}

private fun readUInt64(bytes: ByteArray, offset: Int): Long? {
    if (offset < 0 || offset + 8 > bytes.size) return null
    return (0 until 8).fold(0L) { value, index ->
        (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
    }
}
