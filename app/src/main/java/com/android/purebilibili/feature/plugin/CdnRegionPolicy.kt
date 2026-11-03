package com.android.purebilibili.feature.plugin

import kotlinx.serialization.Serializable
import com.android.purebilibili.plugin.sdk.PlaybackCdnCandidate
import com.android.purebilibili.plugin.sdk.PlaybackCdnCandidateSource
import com.android.purebilibili.plugin.sdk.CdnHealthEvent
import com.android.purebilibili.plugin.sdk.CdnLineDiagnostic
import java.net.URI

@Serializable
data class IpLocationSnapshot(
    val addr: String = "",
    val country: String = "",
    val province: String = "",
    val city: String = "",
    val isp: String = ""
)

data class CdnRegionSelection(
    val region: String,
    val hosts: List<String>,
    val fallbackUsed: Boolean
)

data class CdnRewriteResult(
    val urls: List<String>,
    val rewrittenCount: Int
)

@Serializable
data class CdnCustomRule(
    val pattern: String = "",
    val replacement: String = "",
    val enabled: Boolean = true
)


enum class PlaybackCdnPreference(
    val storageValue: String,
    val displayName: String,
    val host: String? = null,
) {
    BASE_URL("base_url", "基础URL"),
    BACKUP_URL("backup_url", "备用URL"),
    ALI("ali", "ali（阿里云）", "upos-sz-mirrorali.bilivideo.com"),
    ALIB("alib", "alib（阿里云）", "upos-sz-mirroralib.bilivideo.com"),
    ALIO1("alio1", "alio1（阿里云）", "upos-sz-mirroralio1.bilivideo.com"),
    COS("cos", "cos（腾讯云）", "upos-sz-mirrorcos.bilivideo.com"),
    COSB("cosb", "cosb（腾讯云，VOD加速类型）", "upos-sz-mirrorcosb.bilivideo.com"),
    COSO1("coso1", "coso1（腾讯云）", "upos-sz-mirrorcoso1.bilivideo.com"),
    HW("hw", "hw（华为云，融合CDN）", "upos-sz-mirrorhw.bilivideo.com"),
    HWB("hwb", "hwb（华为云，融合CDN）", "upos-sz-mirrorhwb.bilivideo.com"),
    HWO1("hwo1", "hwo1（华为云，融合CDN）", "upos-sz-mirrorhwo1.bilivideo.com"),
    HW_08C("08c", "08c（华为云，融合CDN）", "upos-sz-mirror08c.bilivideo.com"),
    HW_08H("08h", "08h（华为云，融合CDN）", "upos-sz-mirror08h.bilivideo.com"),
    HW_08CT("08ct", "08ct（华为云，融合CDN）", "upos-sz-mirror08ct.bilivideo.com"),
    TF_HW("tf_hw", "tf_hw（华为云）", "upos-tf-all-hw.bilivideo.com"),
    TF_TX("tf_tx", "tf_tx（腾讯云）", "upos-tf-all-tx.bilivideo.com"),
    AKAMAI("akamai", "akamai（Akamai海外）", "upos-hz-mirrorakam.akamaized.net"),
    ALIOV("aliov", "aliov（阿里云海外）", "upos-sz-mirroraliov.bilivideo.com"),
    COSOV("cosov", "cosov（腾讯云海外）", "upos-sz-mirrorcosov.bilivideo.com"),
    HWOV("hwov", "hwov（华为云海外）", "upos-sz-mirrorhwov.bilivideo.com"),
    HK_BCACHE("hk_bcache", "hk_bcache（Bilibili海外）", "cn-hk-eq-bcache-01.bilivideo.com");

    companion object {
        fun fromStorageValue(value: String?): PlaybackCdnPreference =
            entries.firstOrNull { it.storageValue == value } ?: BASE_URL
    }
}

internal fun applyPlaybackCdnPreference(
    candidates: List<PlaybackCdnCandidate>,
    preference: PlaybackCdnPreference,
    pluginApplied: Boolean = false,
): List<PlaybackCdnCandidate> {
    if (candidates.isEmpty()) return candidates

    // ① 设置里选了具体线路：最高优先，无条件排到最前。
    val host = preference.host
    if (host != null) {
        // 不管原候选是 PCDN 还是常规 CDN，改 host 后就是设置里的线路
        // （upgcxcode 路径与签名在 host 之间通用）。
        val base = candidates.first()
        val preferred = base.copy(
            videoUrl = rewriteBilivideoHost(base.videoUrl, host) ?: base.videoUrl,
            audioUrl = base.audioUrl?.let { rewriteBilivideoHost(it, host) ?: it },
        )
        val rest = excludePcdnCandidates(candidates)
        return (listOf(preferred) + rest).distinctBy { it.videoUrl to it.audioUrl }
    }

    // ② 插件（CDN 智能选线）已开启：尊重插件给出的顺序，不再额外过滤。
    //    插件内部已用 risky 标记把 PCDN 排到最后，再过滤等于推翻它的判断。
    if (pluginApplied) {
        return if (preference == PlaybackCdnPreference.BACKUP_URL) {
            candidates.drop(1) + candidates.take(1)
        } else {
            candidates
        }
    }

    // ③ 插件没开：排除 PCDN，整批都是 PCDN 时换 host 兜底。
    val usable = excludePcdnCandidates(candidates)
    if (preference == PlaybackCdnPreference.BACKUP_URL) {
        return usable.drop(1) + usable.take(1)
    }
    return usable
}

/**
 * 处理候选里的 PCDN 地址。
 *
 * 不丢弃，而是保留 upgcxcode 路径与签名、把 host 换成常规线路——直接丢弃会让
 * 只下发 PCDN 的视频彻底打不开。顺序保持不变，换完 host 的地址照常参与选线。
 */
private fun excludePcdnCandidates(
    candidates: List<PlaybackCdnCandidate>
): List<PlaybackCdnCandidate> {
    return candidates.map { candidate ->
        // 视频和音频的 host 是独立下发的，B 站会只把其中一条分到 PCDN。
        // 以前只看 videoUrl，视频走常规 CDN、音频走 PCDN 时整个候选被跳过，
        // 音频就带着解析不了的 mcdn 域名进了播放器。
        val videoRisky = isRiskyCdnHost(hostOf(candidate.videoUrl))
        val audioRisky = candidate.audioUrl?.let { isRiskyCdnHost(hostOf(it)) } == true
        if (!videoRisky && !audioRisky) return@map candidate
        candidate.copy(
            videoUrl = if (videoRisky) {
                rewriteBilivideoHost(candidate.videoUrl, FALLBACK_CDN_HOST) ?: candidate.videoUrl
            } else {
                candidate.videoUrl
            },
            audioUrl = candidate.audioUrl?.let {
                if (audioRisky) rewriteBilivideoHost(it, FALLBACK_CDN_HOST) ?: it else it
            },
        )
    }
}

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")

/** 整批候选都是 PCDN 时改写的目标线路。 */
private const val FALLBACK_CDN_HOST = "upos-sz-mirrorali.bilivideo.com"

data class CdnCustomRuleValidation(
    val rule: CdnCustomRule,
    val error: String? = null
)

@Serializable
data class CdnCandidateHealth(
    val host: String = "",
    val firstFrameTimeoutCount: Int = 0,
    val bufferingCount: Int = 0,
    val playbackErrorCount: Int = 0,
    val readyCount: Int = 0,
    val manualProbeLatencyMs: Long? = null,
    val manualProbeSpeedKbps: Long? = null,
    val lastProbeAtMs: Long = 0L,
    val lastUpdatedAtMs: Long = 0L,
    val healthWindowStartedAtMs: Long = 0L
)


data class CdnProbeLimit(
    val maxCandidates: Int = 5,
    val cooldownMs: Long = CDN_MANUAL_PROBE_COOLDOWN_MS
)

data class CdnProbeCandidate(
    val url: String,
    val host: String,
    val allowed: Boolean,
    val cooldownRemainingMs: Long
)


data class CdnHostDiagnostic(
    val host: String,
    val statusLabel: String,
    val latencyMs: Long?,
    val speedKbps: Long?,
    val errorCount: Int,
    val lastProbeAtMs: Long
)

internal fun selectCdnRegionForLocation(
    location: IpLocationSnapshot,
    catalog: Map<String, List<String>>,
    @Suppress("UNUSED_PARAMETER")
    fallbackRegion: () -> String = { "" }
): CdnRegionSelection {
    val available = catalog.filterValues { it.isNotEmpty() }
    val overseasHosts = available["海外"]
    val normalizedCountry = normalizeRegionName(location.country)

    if (available.isEmpty()) {
        return CdnRegionSelection(region = "", hosts = emptyList(), fallbackUsed = true)
    }

    val directCandidates = listOf(location.city, location.province, location.country)
        .flatMap { value -> listOf(value, normalizeRegionName(value)) }
        .filter { it.isNotBlank() }

    directCandidates.firstNotNullOfOrNull { candidate ->
        available[candidate]?.let { hosts ->
            return CdnRegionSelection(
                candidate,
                sortCdnRegionHostsForIsp(hosts, location.isp),
                fallbackUsed = false
            )
        }
    }

    if (normalizedCountry.isNotBlank() && normalizedCountry !in mainlandCountryNames) {
        overseasHosts?.let {
            return CdnRegionSelection(
                "海外",
                sortCdnRegionHostsForIsp(it, location.isp),
                fallbackUsed = false
            )
        }
    }

    val alias = resolveMainlandRegionAlias(location)
    if (alias != null) {
        available[alias]?.let {
            return CdnRegionSelection(
                alias,
                sortCdnRegionHostsForIsp(it, location.isp),
                fallbackUsed = false
            )
        }
    }

    return CdnRegionSelection(region = "", hosts = emptyList(), fallbackUsed = true)
}

internal fun rewriteCdnUrlCandidates(
    originalUrls: List<String>,
    preferredHosts: List<String>
): CdnRewriteResult {
    if (originalUrls.isEmpty() || preferredHosts.isEmpty()) {
        return CdnRewriteResult(urls = originalUrls.distinct(), rewrittenCount = 0)
    }

    val rewritten = buildList {
        originalUrls.forEach { original ->
            preferredHosts.forEach { host ->
                rewriteBilivideoHost(original, host)?.let { add(it) }
            }
        }
        addAll(originalUrls)
    }.distinct()

    return CdnRewriteResult(
        urls = rewritten,
        rewrittenCount = (rewritten.size - originalUrls.distinct().size).coerceAtLeast(0)
    )
}

internal fun buildPlaybackCdnCandidates(
    videoUrls: List<String>,
    audioUrls: List<String>
): List<PlaybackCdnCandidate> {
    val fallbackAudioUrl = audioUrls.firstOrNull { it.isNotBlank() }
    return videoUrls.filter { it.isNotBlank() }.mapIndexed { index, videoUrl ->
        PlaybackCdnCandidate(
            videoUrl = videoUrl,
            audioUrl = audioUrls.getOrNull(index)?.takeIf { it.isNotBlank() } ?: fallbackAudioUrl,
            source = PlaybackCdnCandidateSource.ORIGINAL
        )
    }.distinctBy { it.videoUrl to it.audioUrl }
}

internal fun rewritePlaybackCdnCandidatesForRegion(
    candidates: List<PlaybackCdnCandidate>,
    preferredHosts: List<String>
): List<PlaybackCdnCandidate> {
    if (preferredHosts.isEmpty()) return candidates
    return buildList {
        candidates.forEach { candidate ->
            preferredHosts.forEach { host ->
                val videoUrl = rewriteBilivideoHost(candidate.videoUrl, host) ?: candidate.videoUrl
                val audioUrl = candidate.audioUrl?.let { rewriteBilivideoHost(it, host) ?: it }
                if (videoUrl != candidate.videoUrl || audioUrl != candidate.audioUrl) {
                    add(candidate.copy(videoUrl = videoUrl, audioUrl = audioUrl, source = PlaybackCdnCandidateSource.REGION))
                }
            }
        }
    }.distinctBy { it.videoUrl to it.audioUrl }
}

internal fun validateCdnCustomRules(rules: List<CdnCustomRule>): List<CdnCustomRuleValidation> {
    return rules.map { rule ->
        val error = when {
            !rule.enabled -> null
            rule.pattern.isBlank() -> "匹配表达式不能为空"
            rule.replacement.isBlank() -> "替换内容不能为空"
            else -> runCatching { Regex(rule.pattern) }
                .exceptionOrNull()
                ?.message
                ?.let { "正则表达式无效：$it" }
        }
        CdnCustomRuleValidation(rule = rule, error = error)
    }
}

internal fun rewritePlaybackCdnCandidatesForCustomRules(
    candidates: List<PlaybackCdnCandidate>,
    rules: List<CdnCustomRule>
): List<PlaybackCdnCandidate> {
    return rewritePlaybackCdnCandidatesForCompiledCustomRules(candidates, compileCdnCustomRules(rules))
}

internal fun compileCdnCustomRules(rules: List<CdnCustomRule>): List<Pair<CdnCustomRule, Regex>> {
    return validateCdnCustomRules(rules)
        .filter { it.rule.enabled && it.error == null }
        .map { it.rule to Regex(it.rule.pattern) }
}

internal fun rewritePlaybackCdnCandidatesForCompiledCustomRules(
    candidates: List<PlaybackCdnCandidate>,
    compiledRules: List<Pair<CdnCustomRule, Regex>>
): List<PlaybackCdnCandidate> {
    if (compiledRules.isEmpty()) return emptyList()

    val allowedHosts = candidates.flatMap { listOfNotNull(hostFromCdnUrl(it.videoUrl), it.audioUrl?.let(::hostFromCdnUrl)) }
        .filter { it.isNotBlank() }
        .toSet()
    return candidates.mapNotNull { candidate ->
        val rewrittenVideo = rewriteCdnUrlByCustomRules(candidate.videoUrl, compiledRules, allowedHosts)
        val rewrittenAudio = candidate.audioUrl?.let { rewriteCdnUrlByCustomRules(it, compiledRules, allowedHosts) }
        if (rewrittenVideo == candidate.videoUrl && rewrittenAudio == candidate.audioUrl) {
            null
        } else {
            candidate.copy(
                videoUrl = rewrittenVideo,
                audioUrl = rewrittenAudio,
                source = PlaybackCdnCandidateSource.CUSTOM
            )
        }
    }.distinctBy { it.videoUrl to it.audioUrl }
}

private fun rewriteCdnUrlByCustomRules(
    originalUrl: String,
    rules: List<Pair<CdnCustomRule, Regex>>,
    allowedHosts: Set<String>
): String {
    rules.forEach { (rule, regex) ->
        if (!regex.containsMatchIn(originalUrl)) return@forEach
        val replaced = runCatching { regex.replaceFirst(originalUrl, rule.replacement) }.getOrNull()
            ?: return@forEach
        if (isAllowedCustomCdnUrl(replaced, allowedHosts)) return replaced
        return originalUrl
    }
    return originalUrl
}

private fun isAllowedCustomCdnUrl(url: String, allowedHosts: Set<String>): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    return uri.scheme.equals("https", ignoreCase = true) &&
        (host == "bilivideo.com" || host.endsWith(".bilivideo.com") || host in allowedHosts)
}

internal fun selectPlaybackCdnCandidatesForMode(
    customCandidates: List<PlaybackCdnCandidate>,
    regionCandidates: List<PlaybackCdnCandidate>,
    originalCandidates: List<PlaybackCdnCandidate>,
    strictCustomCdn: Boolean,
    healthByHost: Map<String, CdnCandidateHealth>
): List<PlaybackCdnCandidate> {
    val candidates = if (strictCustomCdn && customCandidates.isNotEmpty()) {
        customCandidates
    } else {
        customCandidates + regionCandidates + originalCandidates
    }
    return sortPlaybackCdnCandidatesByHealth(
        candidates = candidates.distinctBy { it.videoUrl to it.audioUrl },
        healthByHost = healthByHost
    )
}

internal fun sortPlaybackCdnCandidatesByHealth(
    candidates: List<PlaybackCdnCandidate>,
    healthByHost: Map<String, CdnCandidateHealth>
): List<PlaybackCdnCandidate> {
    return candidates.mapIndexed { index, candidate ->
        CdnPlaybackCandidateScore(
            candidate = candidate,
            index = index,
            sourcePriority = when (candidate.source) {
                PlaybackCdnCandidateSource.CUSTOM -> 3
                PlaybackCdnCandidateSource.REGION -> 2
                PlaybackCdnCandidateSource.ORIGINAL -> 1
            },
            healthScore = scoreCdnCandidate(healthByHost[hostFromCdnUrl(candidate.videoUrl)])
        )
    }.sortedWith(
        compareByDescending<CdnPlaybackCandidateScore> { it.sourcePriority }
            .thenByDescending { it.healthScore }
            .thenBy { it.index }
    ).map { it.candidate }
}

private data class CdnPlaybackCandidateScore(
    val candidate: PlaybackCdnCandidate,
    val index: Int,
    val sourcePriority: Int,
    val healthScore: Int
)

internal fun sortCdnCandidatesByHealth(
    urls: List<String>,
    healthByHost: Map<String, CdnCandidateHealth>
): List<String> {
    return urls.distinct().mapIndexed { index, url ->
        val host = hostFromCdnUrl(url)
        Triple(url, index, scoreCdnCandidate(healthByHost[host]))
    }.sortedWith(
        compareByDescending<Triple<String, Int, Int>> { it.third }
            .thenBy { it.second }
    ).map { it.first }
}

internal fun scoreCdnCandidate(health: CdnCandidateHealth?): Int {
    if (health == null) return 1_000
    val speedScore = ((health.manualProbeSpeedKbps ?: 0L) / 128L).coerceAtMost(500L).toInt()
    val latencyPenalty = ((health.manualProbeLatencyMs ?: 0L) / 20L).coerceAtMost(250L).toInt()
    val readyScore = health.readyCount.coerceAtMost(10) * 25
    val errorPenalty = health.playbackErrorCount * 220 +
        health.firstFrameTimeoutCount * 180 +
        health.bufferingCount * 55
    return 1_000 + speedScore + readyScore - latencyPenalty - errorPenalty
}

internal fun recordCdnHealthEvent(
    current: CdnCandidateHealth,
    event: CdnHealthEvent,
    nowMs: Long
): CdnCandidateHealth {
    return when (event) {
        CdnHealthEvent.PLAYBACK_READY -> current.copy(
            readyCount = current.readyCount + 1,
            lastUpdatedAtMs = nowMs
        )
        CdnHealthEvent.FIRST_FRAME_TIMEOUT -> current.copy(
            firstFrameTimeoutCount = current.firstFrameTimeoutCount + 1,
            lastUpdatedAtMs = nowMs
        )
        CdnHealthEvent.AUDIO_TRACK_MISSING,
        CdnHealthEvent.PLAYER_ERROR -> current.copy(
            playbackErrorCount = current.playbackErrorCount + 1,
            lastUpdatedAtMs = nowMs
        )
        CdnHealthEvent.BUFFERING -> current.copy(
            bufferingCount = current.bufferingCount + 1,
            lastUpdatedAtMs = nowMs
        )
    }
}

internal fun recordCdnProbeResult(
    current: CdnCandidateHealth,
    latencyMs: Long?,
    speedKbps: Long?,
    success: Boolean,
    nowMs: Long
): CdnCandidateHealth {
    val base = current.copy(
        manualProbeLatencyMs = latencyMs,
        manualProbeSpeedKbps = speedKbps,
        lastProbeAtMs = nowMs,
        lastUpdatedAtMs = nowMs
    )
    return if (success) {
        base.copy(readyCount = base.readyCount + 1)
    } else {
        base.copy(playbackErrorCount = base.playbackErrorCount + 1)
    }
}

internal fun resolveCdnProbeCandidates(
    urls: List<String>,
    healthByHost: Map<String, CdnCandidateHealth>,
    nowMs: Long,
    limit: CdnProbeLimit = CdnProbeLimit()
): List<CdnProbeCandidate> {
    return urls.distinctBy(::hostFromCdnUrl)
        .filter { hostFromCdnUrl(it).isNotBlank() }
        .take(limit.maxCandidates)
        .map { url ->
            val host = hostFromCdnUrl(url)
            val elapsed = nowMs - (healthByHost[host]?.lastProbeAtMs ?: 0L)
            val cooldownRemaining = (limit.cooldownMs - elapsed).coerceAtLeast(0L)
            CdnProbeCandidate(
                url = url,
                host = host,
                allowed = cooldownRemaining == 0L,
                cooldownRemainingMs = cooldownRemaining
            )
        }
}

internal fun buildCdnLineDiagnostics(
    urls: List<String>,
    healthByHost: Map<String, CdnCandidateHealth>,
    sources: List<PlaybackCdnCandidateSource> = emptyList()
): List<CdnLineDiagnostic> {
    return urls.distinct().mapIndexed { index, url ->
        val host = hostFromCdnUrl(url)
        val health = healthByHost[host]
        val errorCount = (health?.playbackErrorCount ?: 0) + (health?.firstFrameTimeoutCount ?: 0)
        CdnLineDiagnostic(
            index = index,
            host = host.ifBlank { "未知线路" },
            displayName = resolvePlaybackCdnDisplayName(host, index),
            statusLabel = resolveCdnHealthStatusLabel(health),
            latencyMs = health?.manualProbeLatencyMs,
            speedKbps = health?.manualProbeSpeedKbps,
            errorCount = errorCount,
            bufferingCount = health?.bufferingCount ?: 0,
            lastProbeAtMs = health?.lastProbeAtMs ?: 0L,
            score = scoreCdnCandidate(health),
            sourceLabel = sources.getOrNull(index)?.let(::cdnCandidateSourceLabel)
        )
    }
}

/** User-facing CDN names aligned with PiliPlus' playback CDN selector. */
internal fun resolvePlaybackCdnDisplayName(host: String, index: Int): String {
    val normalizedHost = host.lowercase()
    return PILIPLUS_CDN_DISPLAY_NAMES[normalizedHost]
        ?: when {
            normalizedHost.contains("mcdn") -> "mcdn（Bilibili）"
            normalizedHost.contains("bcache") -> "bcache（Bilibili）"
            index == 0 -> "基础URL"
            else -> "备用URL"
        }
}

private val PILIPLUS_CDN_DISPLAY_NAMES = mapOf(
    "upos-sz-mirrorali.bilivideo.com" to "ali（阿里云）",
    "upos-sz-mirroralib.bilivideo.com" to "alib（阿里云）",
    "upos-sz-mirroralio1.bilivideo.com" to "alio1（阿里云）",
    "upos-sz-mirrorcos.bilivideo.com" to "cos（腾讯云）",
    "upos-sz-mirrorcosb.bilivideo.com" to "cosb（腾讯云，VOD加速类型）",
    "upos-sz-mirrorcoso1.bilivideo.com" to "coso1（腾讯云）",
    "upos-sz-mirrorhw.bilivideo.com" to "hw（华为云，融合CDN）",
    "upos-sz-mirrorhwb.bilivideo.com" to "hwb（华为云，融合CDN）",
    "upos-sz-mirrorhwo1.bilivideo.com" to "hwo1（华为云，融合CDN）",
    "upos-sz-mirror08c.bilivideo.com" to "08c（华为云，融合CDN）",
    "upos-sz-mirror08h.bilivideo.com" to "08h（华为云，融合CDN）",
    "upos-sz-mirror08ct.bilivideo.com" to "08ct（华为云，融合CDN）",
    "upos-tf-all-hw.bilivideo.com" to "tf_hw（华为云）",
    "upos-tf-all-tx.bilivideo.com" to "tf_tx（腾讯云）",
    "upos-hz-mirrorakam.akamaized.net" to "akamai（Akamai海外）",
    "upos-sz-mirroraliov.bilivideo.com" to "aliov（阿里云海外）",
    "upos-sz-mirrorcosov.bilivideo.com" to "cosov（腾讯云海外）",
    "upos-sz-mirrorhwov.bilivideo.com" to "hwov（华为云海外）",
    "cn-hk-eq-bcache-01.bilivideo.com" to "hk_bcache（Bilibili海外）"
)

internal fun cdnCandidateSourceLabel(source: PlaybackCdnCandidateSource): String = when (source) {
    PlaybackCdnCandidateSource.CUSTOM -> "自定义"
    PlaybackCdnCandidateSource.REGION -> "属地优选"
    PlaybackCdnCandidateSource.ORIGINAL -> "原始"
}

internal fun buildCdnHostDiagnostics(
    hosts: List<String>,
    healthByHost: Map<String, CdnCandidateHealth>
): List<CdnHostDiagnostic> {
    return hosts.distinct().map { host ->
        val health = healthByHost[host]
        CdnHostDiagnostic(
            host = host,
            statusLabel = resolveCdnHealthStatusLabel(health),
            latencyMs = health?.manualProbeLatencyMs,
            speedKbps = health?.manualProbeSpeedKbps,
            errorCount = (health?.playbackErrorCount ?: 0) + (health?.firstFrameTimeoutCount ?: 0),
            lastProbeAtMs = health?.lastProbeAtMs ?: 0L
        )
    }
}

internal fun resolveCdnHealthStatusLabel(health: CdnCandidateHealth?): String {
    if (health == null || health.lastUpdatedAtMs <= 0L) return "未检测"
    val errors = health.playbackErrorCount + health.firstFrameTimeoutCount
    return when {
        errors >= 2 -> "不稳定"
        health.bufferingCount >= 3 -> "易缓冲"
        health.manualProbeSpeedKbps != null && health.manualProbeSpeedKbps >= 2_048L -> "速度较好"
        health.manualProbeLatencyMs != null && health.manualProbeLatencyMs <= 250L -> "延迟较低"
        health.readyCount > 0 -> "可用"
        else -> "已检测"
    }
}

internal fun shouldRefreshCdnIpLocation(
    enabled: Boolean,
    nowMs: Long,
    lastRefreshMs: Long,
    ttlMs: Long = CDN_REGION_LOCATION_TTL_MS,
    hasSelection: Boolean
): Boolean {
    if (!enabled) return false
    if (!hasSelection) return true
    if (lastRefreshMs <= 0L) return true
    return nowMs - lastRefreshMs >= ttlMs
}

internal fun resolveCdnRegionHosts(
    region: String,
    cachedHosts: List<String>,
    catalog: Map<String, List<String>>,
    isp: String = ""
): List<String> {
    val catalogHosts = catalog[region].orEmpty()
    if (cachedHosts.isEmpty()) return sortCdnRegionHostsForIsp(catalogHosts, isp)
    if (catalogHosts.isEmpty()) return sortCdnRegionHostsForIsp(cachedHosts.distinct(), isp)
    return if (cachedHosts.all { it in catalogHosts }) {
        sortCdnRegionHostsForIsp(cachedHosts.distinct(), isp)
    } else {
        sortCdnRegionHostsForIsp(catalogHosts, isp)
    }
}

internal fun hasUsableCdnRegionSelection(
    region: String,
    cachedHosts: List<String>,
    catalog: Map<String, List<String>>
): Boolean {
    if (region.isBlank() || cachedHosts.isEmpty()) return false
    val catalogHosts = catalog[region].orEmpty()
    if (catalogHosts.isEmpty()) return true
    return cachedHosts.all { it in catalogHosts }
}

internal const val CDN_REGION_LOCATION_TTL_MS: Long = 24L * 60L * 60L * 1000L
internal const val CDN_MANUAL_PROBE_COOLDOWN_MS: Long = 10L * 60L * 1000L

internal fun sortCdnRegionHostsForIsp(
    hosts: List<String>,
    isp: String
): List<String> {
    val carrier = resolveCarrierKey(isp)
    if (carrier == null) return hosts.distinct()
    return hosts.distinct().sortedWith(
        compareByDescending<String> { host -> scoreHostForCarrier(host, carrier) }
    )
}

internal fun maskIpAddressForLog(addr: String): String {
    val trimmed = addr.trim()
    if (trimmed.isBlank()) return ""
    val ipv4Parts = trimmed.split(".")
    if (ipv4Parts.size == 4 && ipv4Parts.all { it.isNotBlank() }) {
        return "${ipv4Parts[0]}.${ipv4Parts[1]}.*.*"
    }
    val ipv6Parts = trimmed.split(":")
    if (ipv6Parts.size > 2) {
        return ipv6Parts.take(2).joinToString(":") + ":*"
    }
    return "***"
}

private val mainlandCountryNames = setOf(
    "中国",
    "中国大陆",
    "中华人民共和国"
)

private val provinceRegionAliases = mapOf(
    "上海" to "上海",
    "北京" to "北京",
    "天津" to "天津",
    "重庆" to "成都",
    "广东" to "广州",
    "四川" to "成都",
    "陕西" to "西安",
    "湖北" to "武汉",
    "江苏" to "南京",
    "黑龙江" to "哈市",
    "内蒙古" to "呼市",
    "香港" to "香港",
    "澳门" to "澳门"
)

private val cityRegionAliases = mapOf(
    "重庆" to "成都",
    "广州" to "广州",
    "深圳" to "深圳",
    "成都" to "成都",
    "西安" to "西安",
    "武汉" to "武汉",
    "南京" to "南京",
    "哈尔滨" to "哈市",
    "呼和浩特" to "呼市",
    "香港" to "香港",
    "澳门" to "澳门"
)

private fun resolveCarrierKey(isp: String): String? {
    val normalized = isp.trim().lowercase()
    return when {
        normalized.contains("电信") || normalized.contains("chinanet") ||
            normalized.contains("telecom") -> "ct"
        normalized.contains("移动") || normalized.contains("cmcc") ||
            normalized.contains("mobile") -> "cm"
        normalized.contains("联通") || normalized.contains("unicom") -> "cu"
        normalized.contains("广电") || normalized.contains("broadcast") -> "gd"
        else -> null
    }
}

private fun scoreHostForCarrier(host: String, carrier: String): Int {
    val normalized = host.lowercase()
    return when {
        normalized.contains("-$carrier-") -> 3
        normalized.contains("-$carrier.") -> 3
        normalized.contains("$carrier-") -> 2
        normalized.contains(carrier) -> 1
        else -> 0
    }
}

private fun resolveMainlandRegionAlias(location: IpLocationSnapshot): String? {
    val city = normalizeRegionName(location.city)
    cityRegionAliases[city]?.let { return it }

    val province = normalizeRegionName(location.province)
    return provinceRegionAliases[province]
}

private fun normalizeRegionName(value: String): String {
    return value.trim()
        .removeSuffix("特别行政区")
        .removeSuffix("壮族自治区")
        .removeSuffix("回族自治区")
        .removeSuffix("维吾尔自治区")
        .removeSuffix("自治区")
        .removeSuffix("省")
        .removeSuffix("市")
}

internal fun rewriteBilivideoHost(url: String, newHost: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val originalHost = uri.host ?: return null
    if (originalHost.equals(newHost, ignoreCase = true)) return null
    // 能否换 host 取决于路径：upgcxcode 的路径与签名在 host 之间通用。
    // 不按域名白名单判断，否则 mountaintoys.cn / qrstuvwxyzab.com 这类 PCDN
    // 和裸 IP 会被挡掉，设置里的线路就失效了。
    val path = uri.rawPath.orEmpty()
    if (!path.contains("/upgcxcode/") && !path.contains("/v1/resource")) return null
    if (newHost.isBlank()) return null

    val scheme = uri.scheme ?: return null
    val userInfo = uri.rawUserInfo?.let { "$it@" }.orEmpty()
    val port = if (uri.port >= 0) ":${uri.port}" else ""
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    val fragment = uri.rawFragment?.let { "#$it" }.orEmpty()
    return "$scheme://$userInfo$newHost$port$path$query$fragment"
}

internal fun hostFromCdnUrl(url: String): String {
    return runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
}
