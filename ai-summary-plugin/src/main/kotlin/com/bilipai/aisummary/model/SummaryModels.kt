package com.bilipai.aisummary.model

/** 一条字幕。时间戳单位为秒，与 B 站 subtitle JSON 的 body[].from / .to 一致。 */
data class SubtitleLine(
    val fromSec: Double,
    val toSec: Double,
    val content: String
)

/** 由 [com.bilipai.aisummary.pipeline.SubtitleSegmenter] 切出的一个分段。 */
data class SubtitleChunk(
    val index: Int,
    val total: Int,
    val startSec: Double,
    val endSec: Double,
    val lines: List<SubtitleLine>
) {
    /** 喂给模型的带时间戳文本。 */
    fun toPromptText(): String = buildString {
        lines.forEach { line ->
            append('[').append(formatTime(line.fromSec)).append("] ")
            append(line.content.trim()).append('\n')
        }
    }

    companion object {
        fun formatTime(sec: Double): String {
            val total = sec.coerceAtLeast(0.0).toLong()
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
        }
    }
}

/** 单个分段的中间结果（map 阶段）。 */
data class SegmentSummary(
    val index: Int,
    val startSec: Double,
    val endSec: Double,
    val points: List<String>,
    /** 该段内值得回看的时间点（秒），由模型给出。 */
    val timestamps: List<Double> = emptyList(),
    /** 模型输出是否是完整可解析的 JSON；false 表示被截断/格式坏，已在别处补救。 */
    val parsedAsJson: Boolean = true
)

/**
 * 章节里的一条要点。
 *
 * [startSec] 是该要点**自己**的时间（用于点击跳转），`< 0` 表示暂时没有可靠时间，
 * 由 [com.bilipai.aisummary.pipeline.PointTimer] 补齐。
 * 早期版本只存字符串、UI 里所有要点共用章节起点，看起来「一节里时间全一样」。
 */
data class SummaryPoint(
    val text: String,
    val startSec: Double = -1.0
) {
    val hasTime: Boolean get() = startSec >= 0
}

/** 最终笔记里的一个章节。 */
data class SummaryChapter(
    val title: String,
    val startSec: Double,
    val endSec: Double = 0.0,
    val points: List<SummaryPoint> = emptyList()
) {
    /** 只要文本（导出、兜底总览等场景用）。 */
    val pointTexts: List<String> get() = points.map { it.text }
}

/** 一次总结的完整结果，可直接序列化进缓存。 */
data class SummaryResult(
    val bvid: String,
    val cid: Long,
    val title: String,
    val style: SummaryStyle,
    val providerId: String,
    val model: String,
    val createdAtMillis: Long,
    /** 3~5 句总览。 */
    val overview: String,
    val chapters: List<SummaryChapter> = emptyList(),
    val terms: List<String> = emptyList(),
    val quotes: List<String> = emptyList(),
    /** 模型给出的「是否值得看/适合谁」判断，可为空。 */
    val verdict: String = "",
    val source: Source = Source.LLM_SUBTITLE,
    /** 实际消耗的输入/输出 token 估算（若服务返回 usage 则取真实值）。 */
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val segmentCount: Int = 0,
    /** true 表示结果来自官方 conclusion 兜底，而非用户自己的模型。 */
    val isFallback: Boolean = false,
    /** true 表示字幕来自 ASR 转写（视频本身没有字幕）。缓存回读时也要保留。 */
    val asrUsed: Boolean = false
) {
    enum class Source { LLM_SUBTITLE, LLM_ASR, OFFICIAL_CONCLUSION, CACHE }

    /**
     * 来源标签，用于在 UI 上明确「这份总结是谁给的」。
     *
     * - 官方：[Source.OFFICIAL_CONCLUSION] 或 isFallback
     * - 自定义模型：[Source.LLM_SUBTITLE]，带上模型名
     * - 缓存：[Source.CACHE]，同时标注缓存里原本的来源与生成时间
     */
    val sourceLabel: String
        get() {
            val origin = when {
                isFallback || providerId == "bilibili-official" -> "B 站官方 AI 总结"
                asrUsed -> "自定义模型 · $model（ASR 转写字幕）"
                else -> "自定义模型 · $model"
            }
            return if (source == Source.CACHE) {
                val whenText = if (createdAtMillis > 0) {
                    " · 生成于 " + formatCacheTime(createdAtMillis)
                } else ""
                "本地缓存（原始来源：$origin）$whenText"
            } else {
                origin
            }
        }

    /**
     * 极简来源标记，用于卡片折叠态（只能显示 summary 首行时）。
     * 形如 `〔官方〕` / `〔自定义模型〕` / `〔缓存〕`。
     */
    val sourceShortTag: String
        get() = when {
            source == Source.CACHE -> "〔缓存〕"
            isFallback || providerId == "bilibili-official" -> "〔官方〕"
            asrUsed -> "〔ASR〕"
            else -> "〔自定义模型〕"
        }

    private fun formatCacheTime(millis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        return "%02d-%02d %02d:%02d".format(
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH),
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE)
        )
    }

    fun toMarkdown(): String = buildString {
        append("# ").append(title).append("\n\n")
        if (bvid.isNotBlank()) append("> 链接：https://www.bilibili.com/video/").append(bvid).append("\n\n")
        append("> 来源：").append(sourceLabel).append("\n\n")
        append("## 总览\n\n").append(overview).append("\n\n")
        if (chapters.isNotEmpty()) {
            append("## 章节\n\n")
            chapters.forEach { ch ->
                append("### [").append(SubtitleChunk.formatTime(ch.startSec)).append("] ")
                    .append(ch.title).append("\n\n")
                ch.points.forEach { p ->
                    append("- ")
                    if (p.hasTime) {
                        append('[').append(SubtitleChunk.formatTime(p.startSec)).append("] ")
                    }
                    append(p.text).append('\n')
                }
                append('\n')
            }
        }
        if (terms.isNotEmpty()) {
            append("## 术语\n\n")
            terms.forEach { append("- ").append(it).append('\n') }
            append('\n')
        }
        if (quotes.isNotEmpty()) {
            append("## 金句 / 关键论断\n\n")
            quotes.forEach { append("> ").append(it).append("\n\n") }
        }
        if (verdict.isNotBlank()) append("## 观看建议\n\n").append(verdict).append('\n')
    }
}

/** 抽屉 UI 的状态机。 */
sealed interface SummaryUiState {
    data object Idle : SummaryUiState
    data object NeedConfig : SummaryUiState
    data class Loading(
        val stage: String,
        val doneSegments: Int = 0,
        val totalSegments: Int = 0
    ) : SummaryUiState
    data class Success(val result: SummaryResult, val fromCache: Boolean = false) : SummaryUiState
    data class Error(val message: String, val retryable: Boolean = true) : SummaryUiState
}

/** 流水线抛出的可展示错误。 */
class SummaryException(
    message: String,
    val retryable: Boolean = true,
    cause: Throwable? = null
) : Exception(message, cause)
