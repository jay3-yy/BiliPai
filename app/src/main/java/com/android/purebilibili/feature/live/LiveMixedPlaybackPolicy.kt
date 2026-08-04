package com.android.purebilibili.feature.live

import com.android.purebilibili.data.model.response.CodecInfo
import com.android.purebilibili.data.model.response.FormatInfo
import com.android.purebilibili.data.model.response.LiveDurl
import com.android.purebilibili.data.model.response.LivePlayUrlData
import com.android.purebilibili.data.model.response.LiveQuality
import com.android.purebilibili.data.model.response.Playurl
import com.android.purebilibili.data.model.response.PlayurlInfo
import com.android.purebilibili.data.model.response.StreamInfo
import com.android.purebilibili.data.model.response.UrlInfo

/**
 * 「web+app 双平台混合取流」策略。
 *
 * Bilibili 直播间取流时，web 端（platform=web）与 app 端（platform=android）返回的
 * 流/画质候选可能不一致：app 端有时会返回 web 端缺失的更高画质或更优流。
 * 本策略将两个平台的 [LivePlayUrlData] 合并为单份数据，按 (protocol, format, codec) 去重
 * 取并集，画质列表按 qn 去重，从而让上层播放器获得两平台的候选并集。
 *
 * 纯函数、无副作用，便于单元测试。
 */
internal object LiveMixedPlaybackPolicy {

    /**
     * 合并 web 与 app 两份直播流数据。
     *
     * - 若 [web] 为空返回 null（无可播放数据）。
     * - 若 [app] 为空则原样返回 [web]（app 取流失败时回退，不改变现有行为）。
     * - 否则合并两者：stream 按 (protocol, format, codec) 去重取并集，画质列表按 qn 去重。
     */
    fun merge(web: LivePlayUrlData?, app: LivePlayUrlData?): LivePlayUrlData? {
        if (web == null) {
            return null
        }
        if (app == null) {
            return web
        }

        val mergedStreams = mergeStreams(web, app)
        val mergedPlayurl = Playurl(
            stream = mergedStreams,
            gQnDesc = mergeQualityLists(web.playurl_info?.playurl?.gQnDesc, app.playurl_info?.playurl?.gQnDesc)
        )
        return web.copy(
            durl = mergeDurls(web.durl, app.durl),
            quality_description = mergeQualityLists(web.quality_description, app.quality_description),
            current_quality = maxOf(web.current_quality, app.current_quality),
            playurl_info = PlayurlInfo(playurl = mergedPlayurl)
        )
    }

    /**
     * 若 web 端已存在同 (codec, baseUrl) 的流，则 app 端不重复添加（去重）。
     */
    private fun mergeStreams(web: LivePlayUrlData, app: LivePlayUrlData): List<StreamInfo>? {
        val webStreams = web.playurl_info?.playurl?.stream.orEmpty()
        val appStreams = app.playurl_info?.playurl?.stream.orEmpty()
        if (webStreams.isEmpty()) {
            return appStreams.ifEmpty { null }
        }
        if (appStreams.isEmpty()) {
            return webStreams
        }

        val seen = webStreams
            .flatMap { it.format.orEmpty() }
            .flatMap { it.codec.orEmpty() }
            .mapNotNull(::codecKey)
            .toHashSet()

        val appOnly = appStreams.flatMap { stream ->
            stream.format.orEmpty().map { format ->
                val apps = format.codec.orEmpty().filterNot { codec ->
                    codecKey(codec)?.let { it in seen } == true
                }.map { rememberUrlInfo(it) }
                if (apps.isEmpty()) {
                    null
                } else {
                    stream.copy(format = listOf(format.copy(codec = apps)))
                }
            }
        }.filterNotNull()

        return if (appOnly.isEmpty()) webStreams else webStreams + appOnly
    }

    private fun codecKey(codec: CodecInfo): String? {
        val baseUrl = codec.baseUrl.takeIf { it.isNotBlank() } ?: return null
        return "${codec.codecName}|$baseUrl"
    }

    /**
     * 保留 app 端 codec 的完整 url_info（宿主/extra），避免合并后丢失取流地址。
     */
    private fun rememberUrlInfo(codec: CodecInfo): CodecInfo {
        val hosts = codec.url_info.orEmpty().mapNotNull { it.host.takeIf { h -> h.isNotBlank() } }
        if (hosts.isEmpty()) {
            return codec
        }
        return codec.copy(
            url_info = codec.url_info.orEmpty().ifEmpty {
                listOf(UrlInfo(host = hosts.first(), extra = ""))
            }
        )
    }

    private fun mergeQualityLists(
        web: List<LiveQuality>?,
        app: List<LiveQuality>?
    ): List<LiveQuality>? {
        val merged = linkedMapOf<Int, String>()
        (web.orEmpty() + app.orEmpty()).forEach { quality ->
            if (quality.qn > 0 && quality.desc.isNotBlank()) {
                merged.putIfAbsent(quality.qn, quality.desc)
            }
        }
        return merged.entries.map { (qn, desc) -> LiveQuality(qn = qn, desc = desc) }.ifEmpty { null }
    }

    private fun mergeDurls(web: List<LiveDurl>?, app: List<LiveDurl>?): List<LiveDurl>? {
        val webDurls = web.orEmpty()
        val appDurls = app.orEmpty()
        if (webDurls.isEmpty()) {
            return appDurls.ifEmpty { null }
        }
        if (appDurls.isEmpty()) {
            return webDurls
        }
        val seen = webDurls.map { it.url }.toHashSet()
        val extra = appDurls.filterNot { it.url in seen }
        return if (extra.isEmpty()) webDurls else webDurls + extra
    }
}