package com.bilipai.aisummary.data

import com.bilipai.aisummary.model.AiSummaryConfig
import com.bilipai.aisummary.model.SummaryChapter
import com.bilipai.aisummary.model.SummaryPoint
import com.bilipai.aisummary.model.SummaryResult
import com.bilipai.aisummary.model.SubtitleLine
import com.bilipai.aisummary.model.SummaryStyle
import com.bilipai.aisummary.pipeline.PromptLibrary
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * 本地结果缓存：同一个视频 + 同一个模型 + 同一套 Prompt + 同一份字幕，不重复花钱。
 * 纯 java.io 实现，可在 JVM 上单测。
 */
class SummaryCache(
    private val dir: File,
    private val ttlMillis: Long = 30L * 24 * 3600 * 1000,
    private val maxFiles: Int = 200
) {
    init { dir.mkdirs() }

    /** ASR 转写单独存：避免「换了模型但没字幕」时重复下载音频、重复转写。 */
    private val asrDir = File(dir, "asr_transcripts").apply { mkdirs() }
    private val maxAsrFiles = 100

    fun cacheKey(bvid: String, cid: Long, config: AiSummaryConfig, subtitleHash: String): String =
        sha256(
            listOf(
                bvid, cid.toString(), config.baseUrl, config.model, config.style.id,
                config.detailLevel.id, PromptLibrary.PROMPT_VERSION.toString(), subtitleHash
            ).joinToString("|")
        )

    /**
     * 预检缓存键：**不依赖字幕哈希**。
     *
     * 无字幕视频必须先 ASR 才能算出字幕哈希，旧逻辑因此每次都要先下载 12MB 音频、
     * 上传转写，最后才查缓存——缓存等于白存。这个键只由视频与模型配置决定，
     * 可以在请求字幕/下载音频之前先查一次。
     */
    fun baseCacheKey(bvid: String, cid: Long, config: AiSummaryConfig): String = sha256(
        buildList {
            add("base"); add(bvid); add(cid.toString())
            add(config.baseUrl); add(config.model)
            add(config.style.id); add(config.detailLevel.id)
            add(PromptLibrary.PROMPT_VERSION.toString())
            // ASR 结果依赖转写模型与语言；只在开启 ASR 时纳入，避免无谓失效
            if (config.asrEnabled) {
                add(config.asrModel); add(config.asrLanguage)
            }
        }.joinToString("|")
    )

    /** ASR 转写缓存键：同一视频 + 同一 ASR 服务/模型/语言，可直接复用转写文本。 */
    fun asrCacheKey(bvid: String, cid: Long, config: AiSummaryConfig): String = sha256(
        listOf(
            "asr", bvid, cid.toString(),
            config.asrBaseUrl, config.asrModel, config.asrLanguage
        ).joinToString("|")
    )

    fun getAsr(key: String): List<SubtitleLine>? {
        val f = File(asrDir, "$key.json")
        if (!f.exists()) return null
        if (System.currentTimeMillis() - f.lastModified() > ttlMillis) {
            f.delete(); return null
        }
        return try {
            val arr = JSONObject(f.readText()).optJSONArray("lines") ?: return null
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SubtitleLine(
                    fromSec = o.optDouble("from", 0.0),
                    toSec = o.optDouble("to", 0.0),
                    content = o.optString("content")
                )
            }.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            f.delete(); null
        }
    }

    fun putAsr(key: String, lines: List<SubtitleLine>) {
        try {
            val arr = JSONArray()
            lines.forEach { l ->
                arr.put(JSONObject().apply {
                    put("from", l.fromSec); put("to", l.toSec); put("content", l.content)
                })
            }
            File(asrDir, "$key.json").writeText(
                JSONObject().put("lines", arr).toString()
            )
            trimAsr()
        } catch (e: Exception) {
            // 转写缓存失败不影响主流程
        }
    }

    fun get(key: String): SummaryResult? {
        val f = File(dir, "$key.json")
        if (!f.exists()) return null
        if (System.currentTimeMillis() - f.lastModified() > ttlMillis) {
            f.delete(); return null
        }
        return try { fromJson(JSONObject(f.readText())) } catch (e: Exception) {
            f.delete(); null
        }
    }

    fun put(key: String, result: SummaryResult) {
        try {
            File(dir, "$key.json").writeText(toJson(result).toString())
            trim()
        } catch (e: Exception) {
            // 缓存失败不影响主流程
        }
    }

    fun clear() {
        // 先清子目录，否则删 asr_transcripts 目录本身会失败（非空）
        asrDir.listFiles()?.forEach { it.delete() }
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun trimAsr() {
        val files = asrDir.listFiles { _, name -> name.endsWith(".json") } ?: return
        if (files.size <= maxAsrFiles) return
        files.sortedBy { it.lastModified() }
            .take(files.size - maxAsrFiles)
            .forEach { it.delete() }
    }

    private fun trim() {
        val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: return
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }
            .take(files.size - maxFiles)
            .forEach { it.delete() }
    }

    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    fun toJson(r: SummaryResult): JSONObject = JSONObject().apply {
        put("bvid", r.bvid); put("cid", r.cid); put("title", r.title)
        put("style", r.style.id); put("providerId", r.providerId); put("model", r.model)
        put("createdAtMillis", r.createdAtMillis)
        put("overview", r.overview)
        put("chapters", JSONArray().apply {
            r.chapters.forEach { c ->
                put(JSONObject().apply {
                    put("title", c.title); put("startSec", c.startSec); put("endSec", c.endSec)
                    put("points", JSONArray().apply {
                        c.points.forEach { p ->
                            put(JSONObject().apply {
                                put("text", p.text); put("start", p.startSec)
                            })
                        }
                    })
                })
            }
        })
        put("terms", JSONArray(r.terms))
        put("quotes", JSONArray(r.quotes))
        put("verdict", r.verdict)
        put("source", r.source.name)
        put("promptTokens", r.promptTokens)
        put("completionTokens", r.completionTokens)
        put("segmentCount", r.segmentCount)
        put("isFallback", r.isFallback)
        put("asrUsed", r.asrUsed)
    }

    fun fromJson(o: JSONObject): SummaryResult = SummaryResult(
        bvid = o.optString("bvid"),
        cid = o.optLong("cid"),
        title = o.optString("title"),
        style = SummaryStyle.fromId(o.optString("style")),
        providerId = o.optString("providerId"),
        model = o.optString("model"),
        createdAtMillis = o.optLong("createdAtMillis"),
        overview = o.optString("overview"),
        chapters = (o.optJSONArray("chapters") ?: JSONArray()).let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { c ->
                    SummaryChapter(
                        title = c.optString("title"),
                        startSec = c.optDouble("startSec", 0.0),
                        endSec = c.optDouble("endSec", 0.0),
                        points = (c.optJSONArray("points") ?: JSONArray()).let { p ->
                            // 兼容老缓存：早期 points 是纯字符串数组，没有每条要点自己的时间
                            (0 until p.length()).mapNotNull { j ->
                                when (val item = p.opt(j)) {
                                    is JSONObject -> item.optString("text").takeIf { it.isNotBlank() }
                                        ?.let { SummaryPoint(it, item.optDouble("start", -1.0)) }
                                    is String -> item.takeIf { it.isNotBlank() }?.let { SummaryPoint(it, -1.0) }
                                    else -> null
                                }
                            }
                        }
                    )
                }
            }
        },
        terms = stringList(o.optJSONArray("terms")),
        quotes = stringList(o.optJSONArray("quotes")),
        verdict = o.optString("verdict"),
        source = runCatching { SummaryResult.Source.valueOf(o.optString("source")) }
            .getOrDefault(SummaryResult.Source.CACHE),
        promptTokens = o.optInt("promptTokens"),
        completionTokens = o.optInt("completionTokens"),
        segmentCount = o.optInt("segmentCount"),
        isFallback = o.optBoolean("isFallback"),
        asrUsed = o.optBoolean("asrUsed")
    )

    private fun stringList(arr: JSONArray?): List<String> = arr?.let {
        (0 until it.length()).mapNotNull { i -> it.optString(i) }
    }.orEmpty()
}
