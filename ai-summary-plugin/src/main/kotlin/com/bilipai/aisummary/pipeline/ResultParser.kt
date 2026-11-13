package com.bilipai.aisummary.pipeline

import com.bilipai.aisummary.model.SegmentSummary
import com.bilipai.aisummary.model.SummaryChapter
import com.bilipai.aisummary.model.SummaryPoint
import org.json.JSONArray
import org.json.JSONObject

/** reduce 阶段的中间产物，再由 SummaryPipeline 组装成 SummaryResult。 */
data class FinalDraft(
    val overview: String,
    val chapters: List<SummaryChapter>,
    val terms: List<String>,
    val quotes: List<String>,
    val verdict: String,
    val parsedAsJson: Boolean
)

/**
 * 容错解析器：模型并不总能乖乖返回严格 JSON。
 * 依次尝试：去代码围栏 → 截取首尾花括号 → 宽松字段读取 → 纯文本兜底。
 * 任何一步失败都不抛异常，保证用户至少能看到点东西。
 */
object ResultParser {

    fun extractJson(raw: String): String? {
        val trimmed = raw.trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(trimmed)?.groupValues?.get(1)?.trim()
        val candidate = fenced?.takeIf { it.startsWith('{') } ?: trimmed
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return candidate.substring(start, end + 1)
    }

    fun parseSegmentSummary(raw: String, index: Int, startSec: Double, endSec: Double): SegmentSummary {
        val json = extractJson(raw)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (json == null) {
            return SegmentSummary(
                index = index, startSec = startSec, endSec = endSec,
                points = salvagePoints(raw),
                parsedAsJson = false
            )
        }
        val points = stringList(json.opt("points"))
            .ifEmpty { stringList(json.opt("summary")) }
            .ifEmpty { json.optString("content").takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty() }
        val timestamps = (json.opt("timestamps") as? JSONArray)?.let { t ->
            (0 until t.length()).mapNotNull { parseTime(t.opt(it)) }
        }.orEmpty()
        return SegmentSummary(index, startSec, endSec, points, timestamps, parsedAsJson = true)
    }

    fun parseFinal(raw: String): FinalDraft {
        val json = extractJson(raw)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (json == null) {
            // 输出被截断时绝不能把原始 JSON 当作正文展示给用户：能抢救出 overview 就用它，
            // 否则留空交给分段要点兜底。完全不是 JSON 的纯文本仍然可读，直接保留。
            val trimmed = raw.trim()
            val looksLikeJson = trimmed.startsWith("{") ||
                trimmed.contains("\"overview\"") || trimmed.contains("\"chapters\"")
            return FinalDraft(
                overview = if (looksLikeJson) salvageOverview(raw) else trimmed,
                chapters = emptyList(), terms = emptyList(), quotes = emptyList(),
                verdict = "", parsedAsJson = false
            )
        }
        val chapters = ArrayList<SummaryChapter>()
        val arr = json.optJSONArray("chapters")
            ?: json.optJSONArray("outline")
            ?: json.optJSONArray("parts")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val start = parseTime(o.opt("start")) ?: parseTime(o.opt("timestamp")) ?: 0.0
                chapters += SummaryChapter(
                    title = o.optString("title").ifBlank { "第 ${i + 1} 段" },
                    startSec = start,
                    endSec = parseTime(o.opt("end")) ?: 0.0,
                    points = pointList(o.opt("points")).ifEmpty {
                        pointList(o.opt("content")).ifEmpty {
                            o.optString("summary").takeIf { it.isNotBlank() }
                                ?.let { listOf(SummaryPoint(it, start)) }.orEmpty()
                        }
                    }
                )
            }
        }
        val rawOverview = json.optString("overview").ifBlank { json.optString("summary") }
        val overview = if (rawOverview.trimStart().startsWith("{")) salvageOverview(rawOverview) else rawOverview
        return FinalDraft(
            overview = overview,
            chapters = chapters,
            terms = stringList(json.opt("terms")),
            quotes = stringList(json.opt("quotes")),
            verdict = json.optString("verdict").ifBlank { json.optString("recommendation") },
            parsedAsJson = true
        )
    }

    /**
     * 从被截断的 JSON 里抢救 `"overview": "..."`。抢不到就返回空串，
     * 交给流水线用分段要点兜底——宁可信息少，也不展示半截 JSON。
     */
    private fun salvageOverview(raw: String): String {
        val m = Regex("\"overview\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(raw)
        val text = m?.groupValues?.get(1)?.let { unescape(it) }?.trim().orEmpty()
        return if (text.startsWith("{") || text.contains("\"chapters\"")) "" else text
    }

    /** 从被截断的 JSON 里抢救 points 数组中的完整字符串，避免把 `{"points": [` 当要点。 */
    private fun salvagePoints(raw: String): List<String> {
        val body = Regex("\"points\"\\s*:\\s*\\[([\\s\\S]*?)\\]").find(raw)?.groupValues?.get(1)
            ?: Regex("\"points\"\\s*:\\s*\\[([\\s\\S]*)").find(raw)?.groupValues?.get(1).orEmpty()
        val items = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(body)
            .map { unescape(it.groupValues[1]).trim() }
            .filter { it.length > 3 && !it.contains("\"points\"") && !it.startsWith("{") }
            .take(12)
            .toList()
        if (items.isNotEmpty()) return items
        return raw.lines()
            .map { it.trim().trim('{', '}', '[', ']', ',', '"').trim() }
            .filter { it.length > 4 && it.none { c -> c in "{}[]" } && !it.contains("\"points\"") }
            .take(8)
    }

    private fun unescape(s: String): String = s
        .replace("\\\"", "\"")
        .replace("\\n", "\n")
        .replace("\\r", "\r")
        .replace("\\t", "\t")
        .replace("\\\\", "\\")

    /** 解析时间戳：支持 372、"372"、"06:12"、"1:02:03"、"[06:12]"、{"seconds":372}。 */
    fun parseTime(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble().takeIf { it >= 0 }
            is String -> parseTimeString(value)
            is JSONObject -> parseTime(value.opt("seconds")) ?: parseTime(value.opt("value"))
            else -> null
        }
    }

    private fun parseTimeString(s: String): Double? {
        val t = s.trim().trim('[', ']', '（', '）', '(', ')')
        if (t.isEmpty()) return null
        val numeric = t.toDoubleOrNull()
        if (numeric != null) return numeric.takeIf { it >= 0 }
        val parts = t.split(':').mapNotNull { it.trim().toDoubleOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    /**
     * 解析要点数组，并抽掉模型写在要点开头的 `[mm:ss]` 前缀，作为该要点自己的时间。
     *
     * 支持两种形态：
     * - 字符串：`"[01:23] 要点正文"` / `"要点正文"`（前缀可有可无，多个前缀只取第一个）
     * - 对象：`{"t":"01:23","text":"要点正文"}` / `{"start":83,"content":"..."}`
     */
    fun pointList(value: Any?): List<SummaryPoint> = when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { i ->
            when (val item = value.opt(i)) {
                is String -> pointFromString(item)
                is JSONObject -> {
                    val text = firstNonBlank(item.optString("text"), item.optString("content"))
                    if (text.isBlank()) null else {
                        val t = parseTime(item.opt("t"))
                            ?: parseTime(item.opt("start"))
                            ?: parseTime(item.opt("timestamp"))
                        pointFromString(text, t)
                    }
                }
                else -> null
            }
        }
        is String -> listOfNotNull(pointFromString(value))
        else -> emptyList()
    }

    /** 单条要点解析（供流水线兜底路径复用，行为与 [pointList] 一致）。 */
    fun parsePoint(raw: String): SummaryPoint? = pointFromString(raw)

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    /** 从 `[mm:ss] 正文` 里拆出时间；没有前缀时 [forcedSec] 生效（可能为 null）。 */
    private fun pointFromString(raw: String, forcedSec: Double? = null): SummaryPoint? {
        var text = raw.trim()
        var time: Double? = null
        val prefix = Regex("^\\s*\\[([0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?)\\]\\s*")
        while (true) {
            val m = prefix.find(text) ?: break
            if (time == null) time = parseTimeString(m.groupValues[1])
            text = text.removeRange(m.range).trim()
        }
        if (text.isBlank()) return null
        val start = time ?: forcedSec ?: -1.0
        return SummaryPoint(text, start)
    }

    private fun stringList(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { i ->
            val s = value.opt(i)
            when (s) {
                is String -> s.trim().takeIf { it.isNotBlank() }
                is JSONObject -> s.optString("text").ifBlank { s.optString("content") }
                    .trim().takeIf { it.isNotBlank() }
                else -> null
            }
        }
        is String -> listOf(value.trim()).filter { it.isNotBlank() }
        else -> emptyList()
    }
}
