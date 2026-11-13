package com.bilipai.aisummary.pipeline

import com.bilipai.aisummary.model.SubtitleLine
import com.bilipai.aisummary.model.SummaryChapter
import com.bilipai.aisummary.model.SummaryPoint

/**
 * 给每条要点补上**它自己**的时间。
 *
 * 为什么需要这一步：模型在 reduce 阶段把逐段要点合并成章节时，经常只给章节一个
 * `start`，要点本身不带时间；早期实现直接把章节起点当成每条要点的时间，
 * UI 上就表现为「同一个章节里所有小节时间完全一样」，点击也只能跳到章节开头。
 *
 * 三级策略（越靠前越可信）：
 * 1. 要点自带 `[mm:ss]` 前缀（prompt 已要求，且 [ResultParser] 会抽出来）；
 * 2. 拿要点正文和字幕行做字符二元组重合度匹配，取最像的那行的时间；
 * 3. 都没有就在章节时间区间里均匀插值，至少保证时间不同、可点击、单调递增。
 */
object PointTimer {

    /** 匹配阈值：低于它认为「不可信」，宁可插值也不要瞎跳。 */
    private const val MIN_MATCH_SCORE = 0.15

    /**
     * @param durationSec 视频总时长；官方接口拿不到时传 0
     * @param defaultSpanSec 最后一章没有结束时间时的兜底跨度
     */
    fun assign(
        chapters: List<SummaryChapter>,
        lines: List<SubtitleLine>,
        durationSec: Double,
        defaultSpanSec: Double = 60.0
    ): List<SummaryChapter> {
        if (chapters.isEmpty()) return chapters
        val lastLineEnd = lines.lastOrNull()?.toSec ?: 0.0
        val total = maxOf(durationSec, lastLineEnd)

        return chapters.mapIndexed { index, chapter ->
            val start = chapter.startSec.coerceAtLeast(0.0)
            val end = chapterEnd(chapter, index, chapters, total, defaultSpanSec)
            val span = (end - start).coerceAtLeast(0.0)
            val candidates = lines.filter { it.fromSec >= start - 1.0 && it.fromSec <= end + 1.0 }
                .ifEmpty { lines }
            val n = chapter.points.size
            // 同一章节内已用过的时间（秒级）：重复的会被当成「没定位到」重新分配，
            // 否则模型给所有要点都写同一个 [00:00] 时，UI 又变成一节内时间全一样。
            val usedSeconds = HashSet<Long>()
            var previous = start
            val points = chapter.points.mapIndexed { i, point ->
                val fallback = if (n <= 1) start else start + span * (i + 0.5) / n
                var resolved: Double? = null
                if (point.hasTime && point.startSec >= start - 1.0 && point.startSec <= end + 1.0) {
                    val key = point.startSec.toLong()
                    if (usedSeconds.add(key)) resolved = point.startSec
                }
                if (resolved == null) {
                    val matched = matchLine(point.text, candidates)
                    if (matched != null && usedSeconds.add(matched.toLong())) resolved = matched
                }
                var value = (resolved ?: fallback).coerceIn(start, maxOf(start, end))
                // 保证单调不减，避免时间来回跳
                value = value.coerceAtLeast(previous).coerceAtMost(maxOf(start, end))
                previous = value
                point.copy(startSec = value)
            }
            chapter.copy(points = points)
        }
    }

    private fun chapterEnd(
        chapter: SummaryChapter,
        index: Int,
        chapters: List<SummaryChapter>,
        total: Double,
        defaultSpanSec: Double
    ): Double {
        val start = chapter.startSec
        val base = when {
            chapter.endSec > start + 1.0 -> chapter.endSec
            chapters.getOrNull(index + 1)?.startSec?.let { it > start + 1.0 } == true ->
                chapters[index + 1].startSec
            total > start + 1.0 -> total
            else -> start + defaultSpanSec
        }
        // 要点自带的时间可能已经超出估算的章节跨度（官方接口常见），
        // 不能因为估算就把真实时间裁掉。
        val maxPointTime = chapter.points.filter { it.hasTime }.maxOfOrNull { it.startSec } ?: -1.0
        val extended = maxOf(base, maxPointTime + 1.0)
        return if (total > start + 1.0) minOf(extended, total) else extended
    }

    /** 用字符二元组 Dice 系数找最相似的字幕行。 */
    private fun matchLine(text: String, candidates: List<SubtitleLine>): Double? {
        val target = bigrams(text)
        if (target.size < 3) return null
        var best: SubtitleLine? = null
        var bestScore = 0.0
        candidates.forEach { line ->
            val score = dice(target, bigrams(line.content))
            if (score > bestScore) {
                bestScore = score
                best = line
            }
        }
        return if (bestScore >= MIN_MATCH_SCORE) best?.fromSec else null
    }

    private fun dice(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.count { it in b }
        return 2.0 * intersection / (a.size + b.size)
    }

    private fun bigrams(text: String): Set<String> {
        val cleaned = text.filter { it.isLetterOrDigit() }.lowercase()
        if (cleaned.length < 2) return emptySet()
        val out = HashSet<String>(cleaned.length)
        for (i in 0 until cleaned.length - 1) out += cleaned.substring(i, i + 2)
        return out
    }
}
