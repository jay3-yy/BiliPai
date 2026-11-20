package com.android.purebilibili.feature.video.ui.overlay

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** PiliPlus CDNService order, labels and host policies, not aliases for current candidates. */
enum class PiliCdnService(val label: String, val host: String? = null) {
    AUTO("自动优选"),
    BASE("基础URL（不推荐）"), BACKUP("备用URL"),
    ALI("ali（阿里云）", "upos-sz-mirrorali.bilivideo.com"),
    ALIB("alib（阿里云）", "upos-sz-mirroralib.bilivideo.com"),
    ALIO1("alio1（阿里云）", "upos-sz-mirroralio1.bilivideo.com"),
    COS("cos（腾讯云）", "upos-sz-mirrorcos.bilivideo.com"),
    COSB("cosb（腾讯云，VOD加速类型）", "upos-sz-mirrorcosb.bilivideo.com"),
    COSO1("coso1（腾讯云）", "upos-sz-mirrorcoso1.bilivideo.com"),
    HW("hw（华为云，融合CDN）", "upos-sz-mirrorhw.bilivideo.com"),
    HWB("hwb（华为云，融合CDN）", "upos-sz-mirrorhwb.bilivideo.com"),
    HWO1("hwo1（华为云，融合CDN）", "upos-sz-mirrorhwo1.bilivideo.com"),
    HW_08C("08c（华为云，融合CDN）", "upos-sz-mirror08c.bilivideo.com"),
    HW_08H("08h（华为云，融合CDN）", "upos-sz-mirror08h.bilivideo.com"),
    HW_08CT("08ct（华为云，融合CDN）", "upos-sz-mirror08ct.bilivideo.com"),
    TF_HW("tf_hw（华为云）", "upos-tf-all-hw.bilivideo.com"),
    TF_TX("tf_tx（腾讯云）", "upos-tf-all-tx.bilivideo.com"),
    AKAMAI("akamai（Akamai海外）", "upos-hz-mirrorakam.akamaized.net"),
    ALIOV("aliov（阿里云海外）", "upos-sz-mirroraliov.bilivideo.com"),
    COSOV("cosov（腾讯云海外）", "upos-sz-mirrorcosov.bilivideo.com"),
    HWOV("hwov（华为云海外）", "upos-sz-mirrorhwov.bilivideo.com"),
    HK_BCACHE("hk_bcache（Bilibili海外）", "cn-hk-eq-bcache-01.bilivideo.com");
}

private val mirrorPattern = Regex("^https?://(?:upos-\\w+-(?!302)\\w+|(?:upos|proxy)-tf-[^/]+)\\.(?:bilivideo|akamaized)\\.(?:com|net)/upgcxcode")
private val mcdnPattern = Regex("^https?://(?:(?:(?:\\d{1,3}\\.){3}\\d{1,3}|[^/]+\\.mcdn\\.bilivideo\\.(?:com|cn|net))(?::\\d{1,5})?/v\\d/resource)")

/** Keeps signed path/query bytes intact while replacing only the authority's host. */
private fun replaceCdnHost(url: String, host: String, forceHttps: Boolean = false): String {
    val uri = URI(url)
    val prefixEnd = url.indexOf("://") + 3
    val authorityEnd = prefixEnd + uri.rawAuthority.length
    val result = url.replaceRange(prefixEnd, authorityEnd, host)
    return if (forceHttps) "https" + result.substring(result.indexOf(':')) else result
}

internal fun selectPiliCdnUrl(urls: List<String>, service: PiliCdnService): String {
    val candidates = urls.filter { it.isNotBlank() }
    require(candidates.isNotEmpty()) { "当前媒体没有可用播放地址" }
    if (service == PiliCdnService.BASE) {
        return candidates.firstOrNull {
            !com.android.purebilibili.feature.plugin.isRiskyCdnUrl(it)
        } ?: candidates.first()
    }
    if (service == PiliCdnService.BACKUP || service == PiliCdnService.AUTO) {
        val normalized = com.android.purebilibili.feature.plugin
            .normalizePlaybackCdnCandidates(
                candidates.map { url ->
                    com.android.purebilibili.plugin.sdk.PlaybackCdnCandidate(
                        videoUrl = url,
                        audioUrl = null,
                        source = com.android.purebilibili.plugin.sdk.PlaybackCdnCandidateSource.ORIGINAL,
                    )
                }
            )
            .map { it.videoUrl }
        return candidates.firstOrNull {
            !com.android.purebilibili.feature.plugin.isRiskyCdnUrl(it)
        } ?: normalized.firstOrNull {
            !com.android.purebilibili.feature.plugin.isRiskyCdnUrl(it)
        } ?: normalized.first()
    }
    var mcdnTf: String? = null
    var mcdnUpgcxcode: String? = null
    for (url in candidates) {
        val uri = URI(url)
        val query = uri.rawQuery.orEmpty().split('&').associate {
            val pair = it.split('=', limit = 2)
            pair[0] to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        if (mirrorPattern.containsMatchIn(url)) {
            if (query["os"] == "mcdn") mcdnUpgcxcode = url
            else return if (service == PiliCdnService.BACKUP) url else replaceCdnHost(url, requireNotNull(service.host))
        }
        if (mcdnPattern.containsMatchIn(url)) { mcdnTf = url; continue }
        if (url.contains("/upgcxcode/") &&
            com.android.purebilibili.feature.plugin.isHostPortableCdnUrl(url)
        ) {
            mcdnUpgcxcode = url
            continue
        }
        if (uri.host?.endsWith("szbdyd.com") == true) {
            return replaceCdnHost(url, query["xy_usource"] ?: service.host ?: requireNotNull(PiliCdnService.ALI.host), true)
        }
    }
    return mcdnUpgcxcode?.let { replaceCdnHost(it, service.host ?: requireNotNull(PiliCdnService.ALI.host)) }
        ?: mcdnTf?.let { "https://proxy-tf-all-ws.bilivideo.com?url=" + URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        ?: candidates.last()
}
