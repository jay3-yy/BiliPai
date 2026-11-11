package com.bilipai.aisummary.pipeline

import com.bilipai.aisummary.model.SubtitleChunk
import com.bilipai.aisummary.model.SubtitleLine
import kotlin.math.max

/**
 * 字幕分段器（纯逻辑，无 Android 依赖，可直接 JVM 单测）。
 *
 * 目标：
 * 1. 每段输入字符数不超过 budget，避免长视频撑爆上下文；
 * 2. 尽量在句子边界断开，别把一句话劈成两半；
 * 3. 相邻段之间保留 overlap 字符的重叠，降低跨段要点丢失；
 * 4. 每段携带 startSec/endSec，供最终章节时间戳回写。
 */
class SubtitleSegmenter(
    private val budgetChars: Int = 4500,
    overlapChars: Int = 160
) {
    /** 自动收敛非法组合，避免设置页把 overlap 配到 >= budget/2 时直接抛异常。 */
    private val overlapChars: Int =
        overlapChars.coerceIn(0, (budgetChars / 2 - 1).coerceAtLeast(0))

    init {
        require(budgetChars >= 500) { "budgetChars 太小" }
    }

    fun segment(lines: List<SubtitleLine>): List<SubtitleChunk> {
        if (lines.isEmpty()) return emptyList()
        val chunks = ArrayList<SubtitleChunk>()
        var cursor = 0
        var safetyGuard = 0
        while (cursor < lines.size) {
            if (++safetyGuard > MAX_CHUNKS) break // 防御性，避免异常数据导致死循环
            val start = cursor
            var chars = 0
            var end = cursor
            while (end < lines.size && chars < budgetChars) {
                chars += lines[end].content.length + TIMESTAMP_OVERHEAD
                end++
            }
            // 若还有剩余，尝试把切点回退到最近的句末标点（但至少保留 60% 预算）
            var cut = end
            if (end < lines.size) {
                val minKeep = max(1, ((end - start) * 0.6).toInt())
                for (p in end - 1 downTo start + minKeep) {
                    if (isSentenceEnd(lines[p].content)) { cut = p + 1; break }
                }
            }
            val slice = lines.subList(start, cut)
            chunks += SubtitleChunk(
                index = chunks.size,
                total = -1, // 二遍回填
                startSec = slice.first().fromSec,
                endSec = slice.last().toSec,
                lines = slice.toList()
            )
            // 已处理到结尾：直接结束，避免重叠回溯在末尾再切出一个重复分段。
            if (cut >= lines.size) break
            // 下一段从重叠区开始
            var back = 0
            var p = cut - 1
            while (p > start && back < overlapChars) {
                back += lines[p].content.length
                p--
            }
            cursor = max(p + 1, start + 1)
        }
        val total = chunks.size
        return chunks.map { it.copy(total = total) }
    }

    private fun isSentenceEnd(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return false
        return t.last() in SENTENCE_END
    }

    /** 粗略 token 估算：中文按 1 字 ≈ 1.5 token，英文按 4 字符 ≈ 1 token。 */
    companion object {
        private const val TIMESTAMP_OVERHEAD = 12 // "[mm:ss] " 等格式开销
        private const val MAX_CHUNKS = 500
        private val SENTENCE_END = charArrayOf('。', '！', '？', '；', '!', '?', '.', '\n')

        fun estimateTokens(text: String): Int {
            var cjk = 0
            var other = 0
            text.forEach {
                if (it.code in 0x4E00..0x9FFF || it.code in 0x3400..0x4DBF) cjk++ else other++
            }
            return (cjk * 1.5 + other / 4.0 + 0.5).toInt()
        }
    }
}
