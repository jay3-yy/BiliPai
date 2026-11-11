package com.bilipai.aisummary.data

import com.bilipai.aisummary.model.SummaryChapter
import com.bilipai.aisummary.model.SummaryPoint
import com.bilipai.aisummary.model.SummaryException
import com.bilipai.aisummary.model.SummaryResult
import com.bilipai.aisummary.model.SummaryStyle
import com.bilipai.aisummary.pipeline.PointTimer
import com.bilipai.aisummary.util.HttpGet
import com.bilipai.aisummary.util.WbiSigner
import org.json.JSONArray
import org.json.JSONObject

/**
 * 调用 B 站官方 AI 总结接口 /x/web-interface/view/conclusion/get。
 *
 * 用途：**兜底**——当视频没有字幕（或全部字幕链路失败）且用户在设置里允许时，
 * 直接展示官方已生成的摘要，不消耗用户自己的模型额度。
 * 与 BiliPai 原生「AI 总结」入口读的是同一份数据，覆盖率也完全一致。
 *
 * 字段解析做了容错：不同时期 / 不同视频返回的 model_result 结构略有差异，
 * 常见键包括 summary、parts/outline 数组里的 timestamp、content、part、title。
 */
class OfficialConclusionApi(
    private val httpGet: HttpGet,
    private val signer: WbiSigner,
    private val cookieProvider: () -> String = { "" }
) {
    fun fetch(
        bvid: String,
        cid: Long,
        upMid: Long,
        title: String,
        style: SummaryStyle
    ): SummaryResult {
        val params = LinkedHashMap<String, String>().apply {
            put("bvid", bvid)
            put("cid", cid.toString())
            if (upMid > 0) put("up_mid", upMid.toString())
        }
        val q = signer.signedQuery(params)
        val resp = httpGet.get(
            "$CONCLUSION_URL?$q",
            mapOf(
                "User-Agent" to WbiSigner.UA,
                "Referer" to "https://www.bilibili.com/video/$bvid",
                "Cookie" to cookieProvider()
            )
        )
        if (!resp.isSuccessful) {
            throw SummaryException("官方 AI 总结接口返回 HTTP ${resp.code}")
        }
        val root = try { JSONObject(resp.body) } catch (e: Exception) {
            throw SummaryException("官方 AI 总结返回内容无法解析")
        }
        if (root.optInt("code", -1) != 0) {
            throw SummaryException(
                when (root.optInt("code")) {
                    -101 -> "官方 AI 总结需要登录（当前 Cookie 无效）"
                    -403 -> "官方 AI 总结拒绝访问（风控）"
                    else -> "官方 AI 总结不可用：${root.optString("message", "未知错误")}"
                }, retryable = false
            )
        }
        val data = root.optJSONObject("data") ?: throw SummaryException("官方 AI 总结无数据")
        when (data.optInt("code", -1)) {
            -1 -> throw SummaryException("该视频不支持官方 AI 总结（敏感内容）", retryable = false)
            1 -> {
                // code=1 有两种含义，用 stid 区分：
                //   stid 非空 → 已加入生成队列（B 站正在做，稍后再试有意义）
                //   stid 为空 → 未识别到语音（永远不会有官方总结）
                val stid = data.optString("stid")
                if (stid.isNotBlank() && stid != "0") {
                    throw SummaryException(
                        "B 站官方 AI 总结还在排队生成中，稍后再试（或关闭「优先使用官方总结」直接用你的模型）",
                        retryable = true
                    )
                }
                throw SummaryException("官方 AI 总结未识别到语音，无法生成", retryable = false)
            }
        }
        val model = data.optJSONObject("model_result")
            ?: data.optJSONObject("result")
            ?: throw SummaryException("官方 AI 总结内容为空", retryable = false)

        val overview = firstNonBlank(
            model.optString("summary"),
            model.optString("abstract"),
            model.optString("result")
        )
        val chapters = parseChapters(model)
        if (overview.isBlank() && chapters.isEmpty()) {
            throw SummaryException("官方 AI 总结内容为空", retryable = false)
        }
        // 官方 part_outline 通常带 timestamp，但确实存在全是 0 / 缺失的返回；
        // 这里统一补齐，保证同一章节里每条要点的时间不会完全一样。
        val timedChapters = PointTimer.assign(chapters, emptyList(), 0.0)
        return SummaryResult(
            bvid = bvid,
            cid = cid,
            title = title,
            style = style,
            providerId = "bilibili-official",
            model = "bilibili-index",
            createdAtMillis = System.currentTimeMillis(),
            overview = overview.ifBlank { chapters.firstOrNull()?.pointTexts?.firstOrNull().orEmpty() },
            chapters = timedChapters,
            source = SummaryResult.Source.OFFICIAL_CONCLUSION,
            isFallback = true
        )
    }

    /**
     * B 站官方结构（见 BiliPai 的 AiSummaryResponse.kt）：
     * model_result.summary: String
     * model_result.outline[]: { title, timestamp(秒), part_outline[]: { timestamp, content } }
     * 其余字段名做兼容，应对接口调整。
     */
    private fun parseChapters(model: JSONObject): List<SummaryChapter> {
        val outline = model.optJSONArray("outline")
        if (outline != null) {
            val parsed = ArrayList<SummaryChapter>()
            for (i in 0 until outline.length()) {
                val o = outline.optJSONObject(i) ?: continue
                val start = firstDouble(o, "timestamp", "start", "from", "time")
                val points = ArrayList<SummaryPoint>()
                val parts = o.optJSONArray("part_outline") ?: o.optJSONArray("partOutline")
                if (parts != null) {
                    for (j in 0 until parts.length()) {
                        val p = parts.optJSONObject(j) ?: continue
                        val text = firstNonBlank(p.optString("content"), p.optString("text"))
                        if (text.isBlank()) continue
                        // 官方接口的 part_outline 自带 timestamp，别再丢掉（否则每条要点都显示章节起点）
                        val t = firstDouble(p, "timestamp", "start", "from", "time")
                        points += SummaryPoint(text, if (t > 0) t else -1.0)
                    }
                }
                if (points.isEmpty()) {
                    firstNonBlank(o.optString("content"), o.optString("summary"))
                        .takeIf { it.isNotBlank() }?.let { points += SummaryPoint(it, start) }
                }
                if (points.isEmpty()) continue
                parsed += SummaryChapter(
                    title = firstNonBlank(o.optString("title"), o.optString("name"))
                        .ifBlank { "第 ${i + 1} 段" },
                    startSec = start,
                    points = points
                )
            }
            if (parsed.isNotEmpty()) return parsed
        }
        // 兼容其它时期的结构：parts / chapters 数组
        val arr: JSONArray = model.optJSONArray("parts")
            ?: model.optJSONArray("chapters")
            ?: return emptyList()
        val out = ArrayList<SummaryChapter>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val start = firstDouble(o, "timestamp", "start", "from", "time")
            val content = firstNonBlank(
                o.optString("content"), o.optString("part"),
                o.optString("summary"), o.optString("text")
            )
            val chapterTitle = firstNonBlank(o.optString("title"), o.optString("name"))
                .ifBlank { "第 ${i + 1} 段" }
            if (content.isBlank()) continue
            out += SummaryChapter(
                title = chapterTitle,
                startSec = start,
                points = content.split(Regex("[；;\\n]"))
                    .map { it.trim() }.filter { it.isNotBlank() }
                    .map { SummaryPoint(it, start) }
            )
        }
        return out
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun firstDouble(o: JSONObject, vararg keys: String): Double {
        keys.forEach { k ->
            val v = o.optDouble(k, Double.NaN)
            if (!v.isNaN()) return v
        }
        return 0.0
    }

    companion object {
        const val CONCLUSION_URL = "https://api.bilibili.com/x/web-interface/view/conclusion/get"
    }
}
