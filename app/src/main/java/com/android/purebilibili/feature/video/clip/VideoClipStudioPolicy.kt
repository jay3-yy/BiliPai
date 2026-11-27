package com.android.purebilibili.feature.video.clip

internal const val MIN_CLIP_DURATION_MS = 1_000L
internal const val MAX_CLIP_DURATION_MS = 60_000L
internal const val DEFAULT_CLIP_DURATION_MS = 15_000L

internal enum class VideoClipExportMode(
    val label: String,
    val extension: String,
    val mimeType: String,
) {
    MP4_VIDEO("MP4 视频片段", "mp4", "video/mp4"),
    M4A_AUDIO("M4A 纯音频", "m4a", "audio/mp4"),
}

internal data class VideoClipRange(
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)
}

internal fun resolveInitialClipRange(
    currentPositionMs: Long,
    totalDurationMs: Long,
): VideoClipRange {
    if (totalDurationMs <= 0L) return VideoClipRange(0L, 0L)
    if (totalDurationMs <= MIN_CLIP_DURATION_MS) return VideoClipRange(0L, totalDurationMs)
    val clampedCurrent = currentPositionMs.coerceIn(0L, totalDurationMs)
    val desiredSpan = DEFAULT_CLIP_DURATION_MS.coerceAtMost(totalDurationMs)
    val end = (clampedCurrent + desiredSpan).coerceAtMost(totalDurationMs)
    val start = (end - desiredSpan).coerceAtLeast(0L)
    return normalizeClipRange(start, end, totalDurationMs)
}

internal fun normalizeClipRange(
    startMs: Long,
    endMs: Long,
    totalDurationMs: Long,
): VideoClipRange {
    if (totalDurationMs <= 0L) return VideoClipRange(0L, 0L)
    val safeStart = startMs.coerceIn(0L, totalDurationMs)
    val safeEnd = endMs.coerceIn(safeStart, totalDurationMs)
    val minEnd = (safeStart + MIN_CLIP_DURATION_MS).coerceAtMost(totalDurationMs)
    val maxEnd = (safeStart + MAX_CLIP_DURATION_MS).coerceAtMost(totalDurationMs)
    val adjustedEnd = safeEnd.coerceIn(minEnd, maxEnd)
    val adjustedStart = if (adjustedEnd - safeStart < MIN_CLIP_DURATION_MS && totalDurationMs >= MIN_CLIP_DURATION_MS) {
        (adjustedEnd - MIN_CLIP_DURATION_MS).coerceAtLeast(0L)
    } else {
        safeStart
    }
    return VideoClipRange(startMs = adjustedStart, endMs = adjustedEnd)
}

internal fun buildClipOutputDisplayName(
    videoTitle: String,
    startMs: Long,
    endMs: Long,
    mode: VideoClipExportMode,
): String {
    val cleanTitle = videoTitle
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]+"), "_")
        .trim('_')
        .take(32)
        .ifBlank { "BiliPai_Clip" }
    val startTag = formatClipTimeTag(startMs)
    val endTag = formatClipTimeTag(endMs)
    return "${cleanTitle}_${startTag}-${endTag}.${mode.extension}"
}

internal fun formatClipTimeLabel(positionMs: Long): String {
    val totalSec = (positionMs.coerceAtLeast(0L) / 1_000L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02d:%02d".format(min, sec)
}

private fun formatClipTimeTag(positionMs: Long): String {
    val totalSec = (positionMs.coerceAtLeast(0L) / 1_000L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return "%02dm%02ds".format(min, sec)
}
