package com.android.purebilibili.feature.plugin

import java.net.URI

enum class CdnParallelMirrorMode(
    val storageValue: String,
    val label: String,
    val description: String,
) {
    SAFE_SIGNED(
        storageValue = "safe_signed",
        label = "仅签名",
        description = "仅使用 B 站当前返回的签名线路，不合成额外镜像",
    ),
    MAINLAND_UPOS(
        storageValue = "mainland_upos",
        label = "大陆节点",
        description = "海外观看冷门/4K 视频推荐：从多个大陆官方 UPOS 节点并发拉取，失败自动回退原线路",
    ),
    OVERSEAS_UPOS(
        storageValue = "overseas_upos",
        label = "海外节点",
        description = "优先使用海外与香港官方镜像节点并发拉取",
    ),
    CUSTOM_HOSTS(
        storageValue = "custom_hosts",
        label = "自定义",
        description = "仅在手动指定的 B 站官方 CDN 域名间并发拉取",
    );

    companion object {
        fun fromStorageValue(value: String?): CdnParallelMirrorMode =
            entries.firstOrNull { it.storageValue == value } ?: SAFE_SIGNED
    }
}

enum class CdnConcurrencyPreset(
    val storageValue: Int,
    val label: String,
    val maxConnections: Int,
    val isAuto: Boolean,
) {
    CONSERVATIVE(
        storageValue = 4,
        label = "保守",
        maxConnections = CDN_MAX_CONNECTIONS,
        isAuto = false,
    ),
    AUTO(
        storageValue = 0,
        label = "自动",
        maxConnections = CDN_ENHANCED_MAX_CONNECTIONS,
        isAuto = true,
    ),
    THREADS_8(
        storageValue = 8,
        label = "8",
        maxConnections = 8,
        isAuto = false,
    ),
    THREADS_16(
        storageValue = 16,
        label = "16",
        maxConnections = 16,
        isAuto = false,
    ),
    THREADS_32(
        storageValue = 32,
        label = "32",
        maxConnections = 32,
        isAuto = false,
    );

    companion object {
        fun fromStorageValue(value: Int?): CdnConcurrencyPreset =
            entries.firstOrNull { it.storageValue == value } ?: CONSERVATIVE
    }
}

data class CdnParallelConfig(
    val active: Boolean = false,
    val parallel: Boolean = false,
    val mirrorMode: CdnParallelMirrorMode = CdnParallelMirrorMode.SAFE_SIGNED,
    val concurrencyPreset: CdnConcurrencyPreset = CdnConcurrencyPreset.CONSERVATIVE,
    val allowCellular: Boolean = false,
    val customHosts: List<String> = emptyList(),
) {
    val isEnhanced: Boolean
        get() = mirrorMode != CdnParallelMirrorMode.SAFE_SIGNED ||
            concurrencyPreset != CdnConcurrencyPreset.CONSERVATIVE

    val maxConnections: Int
        get() = if (isEnhanced) concurrencyPreset.maxConnections else CDN_MAX_CONNECTIONS

    val allowOpenEndedParallel: Boolean
        get() = active && parallel && isEnhanced
}

internal val BTR_MAINLAND_UPOS_HOSTS: List<String> = listOf(
    "upos-sz-mirrorali.bilivideo.com",
    "upos-sz-mirrorhw.bilivideo.com",
    "upos-sz-mirrorbos.bilivideo.com",
    "upos-sz-mirror08c.bilivideo.com",
    "upos-sz-mirrorbd.bilivideo.com",
    "upos-sz-mirror14b.bilivideo.com",
    "upos-sz-estgoss.bilivideo.com",
    "upos-sz-mirrorcos.bilivideo.com",
    "upos-sz-mirrorcoso1.bilivideo.com",
)

internal val BTR_OVERSEAS_UPOS_HOSTS: List<String> = listOf(
    "upos-sz-mirrorcosov.bilivideo.com",
    "upos-sz-mirroraliov.bilivideo.com",
    "cn-hk-eq-01-01.bilivideo.com",
    "cn-hk-eq-01-03.bilivideo.com",
)

internal const val CDN_EWMA_MIN_SAMPLE_BYTES = 48 * 1024
internal const val CDN_MEASUREMENT_TTL_MS = 90_000L

private val BILI_MEDIA_HOST_SUFFIXES = listOf(
    "bilivideo.com",
    "bilivideo.cn",
    "akamaized.net",
)

private val HOSTNAME_LABEL_REGEX =
    Regex("^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+$")

internal fun normalizeBiliCdnHost(raw: String): String? {
    val trimmed = raw.trim().lowercase()
    if (trimmed.isEmpty() || trimmed.length > 253) return null
    val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
    val host = runCatching { URI(candidate).host.orEmpty().lowercase() }.getOrDefault("")
    if (!HOSTNAME_LABEL_REGEX.matches(host)) return null
    val trusted = BILI_MEDIA_HOST_SUFFIXES.any { suffix ->
        host == suffix || host.endsWith(".$suffix")
    }
    return host.takeIf { trusted }
}

internal fun parseCustomCdnHosts(raw: String): Pair<List<String>, List<String>> {
    val tokens = raw.split(',', ';', '\n', '\r', ' ', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val valid = mutableListOf<String>()
    val invalid = mutableListOf<String>()
    for (token in tokens) {
        val normalized = normalizeBiliCdnHost(token)
        if (normalized != null) {
            if (normalized !in valid) valid += normalized
        } else {
            invalid += token
        }
    }
    return valid.take(16) to invalid
}

internal fun isAkamaiCdnUrl(url: String): Boolean {
    return hostFromCdnUrl(url).endsWith(".akamaized.net")
}

internal fun isRelayMediaUrl(url: String): Boolean {
    if (isRiskyCdnUrl(url)) return true
    val path = runCatching { URI(url).rawPath.orEmpty() }.getOrDefault("")
    return path.startsWith("/v1/resource/", ignoreCase = true)
}

internal fun swapPortableMediaHost(
    rawUrl: String,
    targetHost: String,
    allowAkamaiDonor: Boolean = false,
): String? {
    if (!allowAkamaiDonor && isAkamaiCdnUrl(rawUrl)) return null
    val normalizedHost = normalizeBiliCdnHost(targetHost) ?: return null
    val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
    if (!uri.scheme.equals("https", ignoreCase = true)) return null
    if (!isHostPortableCdnUrl(rawUrl)) return null
    val path = uri.rawPath.orEmpty()
    if (path.isBlank()) return null
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
    return "https://$normalizedHost$path$query$fragment"
}

internal fun buildParallelMirrorUrls(
    originalUrls: List<String>,
    mode: CdnParallelMirrorMode,
    customHosts: List<String> = emptyList(),
): List<String> {
    val originals = originalUrls
        .map { it.trim() }
        .filter { url ->
            runCatching {
                val uri = URI(url)
                uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    !uri.rawPath.isNullOrBlank()
            }.getOrDefault(false)
        }
        .distinct()
    if (originals.isEmpty() || mode == CdnParallelMirrorMode.SAFE_SIGNED) {
        return originals
    }

    val canonicalOriginals = originals.map { url ->
        runCatching {
            val uri = URI(url)
            if (uri.port == 443) {
                val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
                val query = uri.rawQuery?.let { "?$it" }.orEmpty()
                val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
                "https://$userInfo${uri.host}${uri.rawPath}$query$fragment"
            } else {
                url
            }
        }.getOrDefault(url)
    }.distinct()

    val normalizedCustom = customHosts.mapNotNull(::normalizeBiliCdnHost).distinct()
    val targetHosts = when (mode) {
        CdnParallelMirrorMode.CUSTOM_HOSTS ->
            normalizedCustom.ifEmpty { BTR_MAINLAND_UPOS_HOSTS }
        CdnParallelMirrorMode.OVERSEAS_UPOS -> BTR_OVERSEAS_UPOS_HOSTS
        CdnParallelMirrorMode.MAINLAND_UPOS -> BTR_MAINLAND_UPOS_HOSTS
        CdnParallelMirrorMode.SAFE_SIGNED -> return originals
    }

    val ordinary = canonicalOriginals.filterNot(::isRelayMediaUrl)
    val portableOrdinary = ordinary.filter(::isHostPortableCdnUrl)
    val donor = portableOrdinary.firstOrNull { !isAkamaiCdnUrl(it) }

    val synthetic = if (donor != null) {
        targetHosts.mapNotNull { host -> swapPortableMediaHost(donor, host, allowAkamaiDonor = false) }
    } else {
        targetHosts.flatMap { host ->
            portableOrdinary.mapNotNull { url -> swapPortableMediaHost(url, host, allowAkamaiDonor = true) }
        }
    }

    val allowedOriginals = when {
        mode == CdnParallelMirrorMode.CUSTOM_HOSTS && normalizedCustom.isNotEmpty() ->
            canonicalOriginals.filter { hostFromCdnUrl(it) in normalizedCustom }
        mode == CdnParallelMirrorMode.OVERSEAS_UPOS ->
            ordinary.filter { hostFromCdnUrl(it) !in BTR_MAINLAND_UPOS_HOSTS }
        else ->
            ordinary.filter { hostFromCdnUrl(it) in BTR_MAINLAND_UPOS_HOSTS }
    }

    val combined = (allowedOriginals + synthetic).distinct()
    return when {
        combined.isNotEmpty() -> combined
        ordinary.isNotEmpty() -> ordinary
        else -> canonicalOriginals
    }
}

/**
 * Tracks 0-byte failures per `(host, pathAndQuery)` and attributes HTTP 4xx blame between
 * CDN nodes and signed addresses using proven-good sets, matching BTR's `createBanList`.
 */
internal class CdnSessionBanList(
    private val strikeLimit: Int = 2,
) {
    private data class StrikeKey(
        val node: String,
        val address: String,
        val refused: Boolean,
    )

    private sealed interface BanTarget {
        data class Node(val host: String) : BanTarget
        data class Address(val address: String) : BanTarget
        data class Pair(val host: String, val address: String) : BanTarget
    }

    private val emptyReplies = mutableMapOf<StrikeKey, Int>()
    private val goodNodes = mutableSetOf<String>()
    private val goodAddresses = mutableSetOf<String>()
    private var banned: Set<BanTarget> = emptySet()

    @Synchronized
    fun recordFailure(url: String, receivedBytes: Long, httpStatus: Int?): Boolean {
        if (receivedBytes > 0L) return false
        if (httpStatus == 412 || httpStatus == 429) return false
        val node = hostFromCdnUrl(url)
        val address = addressOf(url)
        if (node.isBlank()) return false
        val refused = httpStatus != null && httpStatus in 400..499
        val key = StrikeKey(node = node, address = address, refused = refused)
        emptyReplies[key] = (emptyReplies[key] ?: 0) + 1
        return recompute()
    }

    @Synchronized
    fun recordSuccess(url: String) {
        val node = hostFromCdnUrl(url)
        val address = addressOf(url)
        if (node.isBlank()) return
        val changed = goodNodes.add(node) or (address.isNotBlank() && goodAddresses.add(address))
        if (changed) recompute()
    }

    @Synchronized
    fun allows(url: String): Boolean {
        val node = hostFromCdnUrl(url)
        val address = addressOf(url)
        return BanTarget.Node(node) !in banned &&
            BanTarget.Address(address) !in banned &&
            BanTarget.Pair(node, address) !in banned
    }

    @Synchronized
    fun isNodeBanned(host: String): Boolean = BanTarget.Node(host.lowercase()) in banned

    @Synchronized
    fun bannedHosts(): Set<String> =
        banned.filterIsInstance<BanTarget.Node>().mapTo(linkedSetOf()) { it.host }

    @Synchronized
    fun reset() {
        emptyReplies.clear()
        goodNodes.clear()
        goodAddresses.clear()
        banned = emptySet()
    }

    private fun recompute(): Boolean {
        val strikes = mutableMapOf<BanTarget, Int>()
        for ((key, count) in emptyReplies) {
            val target: BanTarget? = when {
                !key.refused -> BanTarget.Node(key.node)
                key.node in goodNodes && key.address in goodAddresses -> BanTarget.Pair(key.node, key.address)
                key.node in goodNodes -> BanTarget.Address(key.address)
                key.address in goodAddresses -> BanTarget.Node(key.node)
                else -> null
            }
            if (target != null) {
                strikes[target] = (strikes[target] ?: 0) + count
            }
        }
        val nextBanned = strikes.filterValues { it >= strikeLimit }.keys.toSet()
        val added = nextBanned.any { it !in banned }
        banned = nextBanned
        return added
    }

    private fun addressOf(url: String): String = runCatching {
        val uri = URI(url)
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        path + query
    }.getOrDefault("")
}

internal fun updateNodeEwmaBps(previousBps: Long, sampleBps: Long): Long {
    if (sampleBps <= 0L) return previousBps.coerceAtLeast(0L)
    if (previousBps <= 0L) return sampleBps
    return (previousBps * 65L + sampleBps * 35L) / 100L
}

/**
 * Selects a bounded candidate pool combining the fastest freshly measured hosts with 1-2
 * exploration slots for unmeasured or stale (>90s) hosts so new mirrors can prove themselves.
 */
internal fun selectParallelCandidates(
    pool: List<String>,
    speedsByHost: Map<String, Long>,
    measuredAtByHost: Map<String, Long>,
    nowMs: Long,
    width: Int,
    exploreCursor: Int,
): Pair<List<String>, Int> {
    if (pool.isEmpty() || width <= 0) return emptyList<String>() to exploreCursor
    fun isFresh(url: String): Boolean {
        val host = hostFromCdnUrl(url)
        val speed = speedsByHost[host] ?: 0L
        val measuredAt = measuredAtByHost[host] ?: 0L
        return speed > 0L && measuredAt > 0L && nowMs - measuredAt <= CDN_MEASUREMENT_TTL_MS
    }

    val measured = pool.filter(::isFresh)
        .sortedByDescending { speedsByHost[hostFromCdnUrl(it)] ?: 0L }
    val unmeasured = pool.filterNot(::isFresh)
    val effectiveWidth = width.coerceAtMost(pool.size)
    val explorePlaces = if (unmeasured.isEmpty()) {
        0
    } else {
        minOf(unmeasured.size, maxOf(1, effectiveWidth - measured.size))
    }
    val measuredLimit = (effectiveWidth - explorePlaces).coerceAtLeast(0)
    val selected = LinkedHashSet<String>(effectiveWidth)
    measured.take(measuredLimit).forEach { selected += it }
    repeat(explorePlaces) { offset ->
        val index = ((exploreCursor % unmeasured.size) + unmeasured.size + offset) % unmeasured.size
        selected += unmeasured[index]
    }
    for (url in pool) {
        if (selected.size >= effectiveWidth) break
        selected += url
    }
    val nextCursor = if (unmeasured.isEmpty()) 0 else (exploreCursor + explorePlaces) % unmeasured.size
    return selected.toList() to nextCursor
}

/**
 * Distributes [pieceCount] primary piece URLs proportionally to measured EWMA speed, capping
 * unmeasured exploration candidates to a single trial piece so slow nodes cannot stall a window.
 */
internal fun assignPieceUrlsBySpeed(
    pieceCount: Int,
    candidates: List<String>,
    speedOf: (String) -> Long,
): List<String> {
    if (pieceCount <= 0 || candidates.isEmpty()) return emptyList()
    if (candidates.size == 1) return List(pieceCount) { candidates.first() }

    val measured = candidates.filter { speedOf(it) > 0L }
        .sortedByDescending(speedOf)
    val unmeasured = candidates.filter { speedOf(it) <= 0L }
    if (measured.isEmpty()) {
        return List(pieceCount) { index -> candidates[index % candidates.size] }
    }

    val assignments = mutableListOf<String>()
    val trialCount = minOf(unmeasured.size, maxOf(1, pieceCount / 4), (pieceCount - 1).coerceAtLeast(0))
    val exploreSlots = unmeasured.take(trialCount)
    val remainingPieces = pieceCount - exploreSlots.size

    val totalSpeed = measured.sumOf { speedOf(it).coerceAtLeast(1L) }.toDouble()
    val quotas = measured.map { url ->
        val exact = remainingPieces * (speedOf(url).toDouble() / totalSpeed)
        val floor = exact.toInt()
        Triple(url, floor, exact - floor)
    }.toMutableList()
    var assigned = quotas.sumOf { it.second }
    val remainderOrder = quotas.indices.sortedByDescending { quotas[it].third }
    var r = 0
    while (assigned < remainingPieces && remainderOrder.isNotEmpty()) {
        val idx = remainderOrder[r % remainderOrder.size]
        val current = quotas[idx]
        quotas[idx] = current.copy(second = current.second + 1)
        assigned++
        r++
    }

    // Interleave high-speed nodes and place single trial pieces near the middle/tail so startup
    // head pieces stay on the proven fastest mirrors.
    val buckets = quotas.map { (url, count, _) -> ArrayDeque(List(count) { url }) }
    while (assignments.size < remainingPieces) {
        var added = false
        for (bucket in buckets) {
            if (bucket.isNotEmpty() && assignments.size < remainingPieces) {
                assignments += bucket.removeFirst()
                added = true
            }
        }
        if (!added) break
    }
    exploreSlots.forEach { trialUrl ->
        val insertAt = minOf(assignments.size, maxOf(1, assignments.size / 2))
        assignments.add(insertAt, trialUrl)
    }
    while (assignments.size < pieceCount) {
        assignments += measured.first()
    }
    return assignments.take(pieceCount)
}
