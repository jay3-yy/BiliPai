package com.android.purebilibili.data.repository

import com.android.purebilibili.core.subtitle.SubtitleCue
import java.util.LinkedHashMap

data class SubtitleCueMemoryCacheStats(
    val entryCount: Int,
    val totalCueCount: Int,
    val estimatedBytes: Long,
)

/** 历史字幕按使用顺序淘汰；当前播放持有的列表不随缓存淘汰而修改。 */
internal class SubtitleCueMemoryCache(
    private val maxEntries: Int = 48,
    private val maxBytes: Long = 4L * 1024 * 1024,
) {
    private class Entry(val cues: List<SubtitleCue>, val bytes: Long)
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var bytes = 0L
    private var generation = 0L

    init {
        require(maxEntries > 0 && maxBytes > 0)
    }

    @Synchronized
    operator fun get(key: String): List<SubtitleCue>? = entries[key]?.cues

    @Synchronized
    fun generation(): Long = generation

    @Synchronized
    fun put(key: String, cues: List<SubtitleCue>, expectedGeneration: Long) {
        // 内存清理后，之前尚在下载或解析的请求仍可返回播放，但不再回填历史缓存。
        if (expectedGeneration != generation) return
        val entryBytes = estimateBytes(key, cues)
        entries.remove(key)?.let { bytes -= it.bytes }
        // 单份超预算字幕照常返回给播放会话，不挤掉其他小字幕。
        if (entryBytes > maxBytes) return
        val oldest = entries.entries.iterator()
        while (entries.size >= maxEntries || bytes + entryBytes > maxBytes) {
            val entry = oldest.next()
            bytes -= entry.value.bytes
            oldest.remove()
        }
        entries[key] = Entry(cues, entryBytes)
        bytes += entryBytes
    }

    @Synchronized
    fun stats() = SubtitleCueMemoryCacheStats(entries.size, entries.values.sumOf { it.cues.size }, bytes)

    @Synchronized
    fun clear() {
        entries.clear()
        bytes = 0L
        generation++
    }

    private fun estimateBytes(key: String, cues: List<SubtitleCue>): Long =
        // 对象/容器开销为估算；文本按 UTF-16 计，逐字文本与时间戳对象一并计入。
        512L + key.length * 2L + cues.sumOf { cue ->
            96L + cue.content.length * 2L + cue.words.sumOf { word -> 64L + word.text.length * 2L }
        }
}
