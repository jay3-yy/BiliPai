package com.android.purebilibili.core.player

/** 至少 95%，且距片尾不超过 60 秒。 */
private const val MIN_COMPLETION_PERCENT = 0.95f
private const val COMPLETION_TAIL_MS = 60_000L

/**
 * 返回「视为看完」的位置：两小时的视频要进最后 60 秒，而不是 95% 就判完。
 * 只影响本地续播和片尾清理，不改上报接口。
 */
fun resolvePlaybackCompletionPositionMs(durationMs: Long): Long {
    if (durationMs <= 0L) return 0L
    val percentPositionMs = (durationMs * MIN_COMPLETION_PERCENT).toLong()
    val tailPositionMs = (durationMs - COMPLETION_TAIL_MS).coerceAtLeast(0L)
    return maxOf(percentPositionMs, tailPositionMs)
}
