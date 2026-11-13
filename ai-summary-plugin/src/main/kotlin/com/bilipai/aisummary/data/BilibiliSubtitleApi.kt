package com.bilipai.aisummary.data

import com.bilipai.aisummary.model.SubtitleLine
import com.bilipai.aisummary.model.SummaryException
import com.bilipai.aisummary.util.HttpGet
import com.bilipai.aisummary.util.HttpResponse
import com.bilipai.aisummary.util.WbiSigner
import org.json.JSONArray
import org.json.JSONObject

data class SubtitleFetchResult(
    val lines: List<SubtitleLine>,
    val label: String,
    val language: String
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * 获取 B 站字幕。
 *
 * 主链路：/x/player/wbi/v2?bvid=&cid=（WBI 签名 + 登录 Cookie）→ subtitle.subtitles[]
 *         → subtitle_url → body[{from,to,content}]
 * 兜底链路：旧 /x/player/v2（无需 WBI，部分场景仍可用）。
 *
 * 参考：bilibili-API-collect、bilibili-video-summary/references/api.md
 * 风控提示：缺 WBI 签名或未登录时，AI 字幕常被降级/返回 -412，因此 Cookie 由宿主注入。
 */
class BilibiliSubtitleApi(
    private val httpGet: HttpGet,
    private val signer: WbiSigner,
    private val cookieProvider: () -> String = { "" }
) {
    fun fetch(bvid: String, cid: Long): SubtitleFetchResult {
        val cookie = cookieProvider()
        val headers = baseHeaders(cookie)

        val primary = runCatching {
            val q = signer.signedQuery(mapOf("bvid" to bvid, "cid" to cid.toString()))
            httpGet.get("$PLAYER_WBI_V2?$q", headers)
        }.getOrNull()

        var response = primary?.takeIf { it.isSuccessful }
        if (response == null) {
            // 旧端点兜底：不签名，但必须带 Cookie 才能拿到 AI 字幕。
            response = httpGet.get(
                "$PLAYER_V2?bvid=${enc(bvid)}&cid=$cid", headers
            )
        }
        if (!response.isSuccessful) {
            throw SummaryException("字幕接口返回 HTTP ${response.code}（可能被风控，请确认已登录 B 站）")
        }

        val data = try {
            JSONObject(response.body).optJSONObject("data")
        } catch (e: Exception) {
            null
        } ?: throw SummaryException("字幕接口返回内容无法解析")

        val subtitles = data.optJSONObject("subtitle")?.optJSONArray("subtitles")
            ?: JSONArray()
        if (subtitles.length() == 0) {
            throw SummaryException(
                "没有可用字幕轨（CC 与 AI 字幕都没有）。未登录时 B 站会返回空列表，请确认 Cookie 含 SESSDATA",
                retryable = false
            )
        }

        val chosen = chooseSubtitle(subtitles)
            ?: throw SummaryException("字幕轨存在但无可用语言", retryable = false)
        val url = normalizeUrl(chosen.url)

        val bodyResp = httpGet.get(url, headers)
        if (!bodyResp.isSuccessful) {
            throw SummaryException("字幕文件下载失败：HTTP ${bodyResp.code}")
        }
        val lines = parseBody(bodyResp)
        if (lines.isEmpty()) {
            throw SummaryException("字幕文件为空或格式不兼容", retryable = false)
        }
        return SubtitleFetchResult(lines = lines, label = chosen.label, language = chosen.language)
    }

    private fun parseBody(resp: HttpResponse): List<SubtitleLine> {
        val arr = try {
            JSONObject(resp.body).optJSONArray("body") ?: JSONArray(resp.body)
        } catch (e: Exception) {
            JSONArray()
        }
        val out = ArrayList<SubtitleLine>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val text = o.optString("content").trim()
            if (text.isEmpty()) continue
            val from = o.optDouble("from", 0.0)
            val to = o.optDouble("to", from)
            out += SubtitleLine(fromSec = from, toSec = maxOf(from, to), content = text)
        }
        // 去重（AI 字幕偶有重复帧）并保持时间顺序
        return out.distinctBy { it.fromSec to it.content }.sortedBy { it.fromSec }
    }

    private fun chooseSubtitle(arr: JSONArray): SubtitleChoice? {
        data class Candidate(val url: String, val lan: String, val isAi: Boolean, val score: Int)
        val list = ArrayList<Candidate>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.optString("subtitle_url")
            if (url.isBlank()) continue
            val lan = o.optString("lan")
            // AI 字幕可能表现为 lan="ai-zh"，也可能只在 URL 里带 aisubtitle 域名，
            // 两种都要识别，否则会被当成普通外语字幕排到最后。
            val isAi = url.contains("aisubtitle", ignoreCase = true) ||
                lan.startsWith("ai-", ignoreCase = true)
            val isChinese = lan.contains("zh", ignoreCase = true)
            val score = when {
                isChinese && !isAi -> 100
                isChinese && isAi -> 80
                isAi -> 60
                else -> 20
            }
            list += Candidate(url, lan, isAi, score)
        }
        val best = list.maxByOrNull { it.score } ?: return null
        val label = if (best.isAi) "B 站 AI 字幕" else "UP 主 CC 字幕"
        return SubtitleChoice(best.url, label, best.lan)
    }

    private fun baseHeaders(cookie: String) = mapOf(
        "User-Agent" to WbiSigner.UA,
        "Referer" to WbiSigner.REFERER,
        "Cookie" to cookie
    )

    /**
     * subtitle_url 是协议相对地址（//aisubtitle.hdslb.com/...?auth_key=...）。
     * auth_key 分钟级有效，所以拿到轨道列表后必须立刻下载，且 URL 尾部的
     * `-0-<32位hex>` 不能截断，否则 CDN 返回 403。
     */
    private fun normalizeUrl(url: String): String = when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> "https://" + url.removePrefix("http://")
        else -> url
    }

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    private data class SubtitleChoice(val url: String, val label: String, val language: String)

    companion object {
        const val PLAYER_WBI_V2 = "https://api.bilibili.com/x/player/wbi/v2"
        const val PLAYER_V2 = "https://api.bilibili.com/x/player/v2"
    }
}
