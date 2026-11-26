package com.android.purebilibili.feature.video.postcard

import android.net.Uri
import com.android.purebilibili.feature.video.note.VideoNoteBlock
import com.android.purebilibili.feature.video.note.formatVideoNoteTimestamp
import kotlin.math.abs

enum class VideoPostcardTemplate(val label: String) {
    CINEMA("电影胶片"),
    POLAROID("相纸明信片"),
    ATMOSPHERE("流光卡片"),
}

data class VideoPostcardDraft(
    val imageUri: Uri,
    val videoTitle: String,
    val upName: String,
    val positionMs: Long,
    val initialQuote: String,
    val danmakuCandidates: List<String>,
    val capturedAtMillis: Long = System.currentTimeMillis(),
)

data class VideoPostcardRenderConfig(
    val template: VideoPostcardTemplate = VideoPostcardTemplate.CINEMA,
    val quoteText: String = "",
    val moodNote: String = "",
    val showMetadata: Boolean = true,
)

internal fun sanitizePostcardLine(text: String, maxChars: Int = 96): String {
    val normalized = text
        .replace('\r', ' ')
        .replace(Regex("[ \t]+"), " ")
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
    if (normalized.length <= maxChars) return normalized
    return normalized.take((maxChars - 1).coerceAtLeast(1)).trimEnd() + "…"
}

/**
 * 优先采用当前时刻活跃字幕（双语时合并两行），无字幕时留空以便用户从邻近弹幕挑选或自由输入。
 */
internal fun resolvePostcardInitialQuote(
    primarySubtitle: String?,
    secondarySubtitle: String?,
    nearbyDanmaku: List<String> = emptyList(),
): String {
    val primary = primarySubtitle?.let { sanitizePostcardLine(it, maxChars = 72) }.orEmpty()
    val secondary = secondarySubtitle?.let { sanitizePostcardLine(it, maxChars = 72) }.orEmpty()
    return when {
        primary.isNotEmpty() && secondary.isNotEmpty() && primary != secondary ->
            sanitizePostcardLine("$primary\n$secondary", maxChars = 110)
        primary.isNotEmpty() -> primary
        secondary.isNotEmpty() -> secondary
        else -> nearbyDanmaku.firstOrNull()?.let { sanitizePostcardLine(it, maxChars = 72) }.orEmpty()
    }
}

/**
 * 从已加载弹幕中挑选距离当前播放时间点最近的若干条干净弹幕（去重、去过短噪音）。
 */
internal fun selectNearbyPostcardDanmaku(
    candidates: List<Pair<Long, String>>,
    positionMs: Long,
    windowMs: Long = 5_000L,
    limit: Int = 6,
): List<String> {
    if (candidates.isEmpty() || limit <= 0) return emptyList()
    val safePosition = positionMs.coerceAtLeast(0L)
    val seen = LinkedHashSet<String>()
    return candidates
        .asSequence()
        .mapNotNull { (timeMs, rawText) ->
            val distance = abs(timeMs - safePosition)
            if (distance > windowMs) return@mapNotNull null
            val cleaned = sanitizePostcardLine(rawText, maxChars = 48).replace('\n', ' ')
            if (cleaned.length < 2) return@mapNotNull null
            distance to cleaned
        }
        .sortedBy { it.first }
        .mapNotNull { (_, text) ->
            if (seen.add(text)) text else null
        }
        .take(limit)
        .toList()
}

internal fun formatPostcardTimestamp(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/**
 * 将台词明信片转成私有笔记区块（时间戳 + 引用台词 + 可选心情短注）。
 */
internal fun buildPostcardNoteBlocks(
    quote: String,
    moodNote: String,
    positionMs: Long,
    cid: Long,
    pageIndex: Int,
    cidCount: Int,
): List<VideoNoteBlock> {
    val seconds = (positionMs / 1000L).coerceAtLeast(0L)
    val cleanedQuote = sanitizePostcardLine(quote, maxChars = 160)
    val cleanedMood = sanitizePostcardLine(moodNote, maxChars = 160)
    return buildList {
        add(
            VideoNoteBlock.Timestamp(
                seconds = seconds,
                cid = cid,
                index = pageIndex.coerceAtLeast(0),
                cidCount = cidCount.coerceAtLeast(1),
                label = formatVideoNoteTimestamp(seconds),
            )
        )
        if (cleanedQuote.isNotEmpty()) {
            add(VideoNoteBlock.Quote(cleanedQuote))
        }
        if (cleanedMood.isNotEmpty()) {
            add(VideoNoteBlock.Text(cleanedMood))
        }
    }
}
