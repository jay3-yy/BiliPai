package com.android.purebilibili.feature.audio.screen

import kotlin.math.abs
import kotlin.math.roundToLong

internal const val TURNTABLE_TONEARM_PLAYING_ANGLE_DEG = -4f
internal const val TURNTABLE_TONEARM_RESTING_ANGLE_DEG = -28f
internal const val TURNTABLE_SCRUB_MS_PER_DP = 140f
internal const val TURNTABLE_SCRUB_TICK_STEP_MS = 2_000L
internal const val TURNTABLE_SCRUB_MAX_DELTA_MS = 60_000L

/**
 * 播放且未在手动搓盘时，唱针轻落在唱片纹路上；暂停或搓盘时自动抬起。
 */
internal fun resolveTurntableTonearmAngleDeg(
    isPlaying: Boolean,
    isScrubbing: Boolean,
): Float = if (isPlaying && !isScrubbing) {
    TURNTABLE_TONEARM_PLAYING_ANGLE_DEG
} else {
    TURNTABLE_TONEARM_RESTING_ANGLE_DEG
}

/**
 * 将黑胶唱片上的水平微搓距离（dp）换算为平滑进度微调毫秒数，限制在 ±60 秒内。
 */
internal fun resolveTurntableScrubDeltaMs(totalDragDp: Float): Long =
    (totalDragDp * TURNTABLE_SCRUB_MS_PER_DP)
        .roundToLong()
        .coerceIn(-TURNTABLE_SCRUB_MAX_DELTA_MS, TURNTABLE_SCRUB_MAX_DELTA_MS)

/**
 * 计算离散阻尼刻度步号：仅在跨过新的 2 秒刻度时返回新步号，避免重复震动。
 */
internal fun resolveTurntableScrubTickStep(
    accumulatedDeltaMs: Long,
    lastStep: Long,
    stepMs: Long = TURNTABLE_SCRUB_TICK_STEP_MS,
): Long? {
    if (stepMs <= 0L) return null
    val currentStep = accumulatedDeltaMs / stepMs
    return currentStep.takeIf { it != lastStep }
}

internal fun formatTurntableScrubBadge(deltaMs: Long): String {
    val seconds = (deltaMs / 1000L)
    return when {
        seconds > 0L -> "+${seconds}s"
        seconds < 0L -> "-${abs(seconds)}s"
        else -> "0s"
    }
}
