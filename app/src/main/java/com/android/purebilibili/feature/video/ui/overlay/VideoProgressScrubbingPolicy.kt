package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.video.progress.PbpRidgeSample
import kotlin.math.abs
import kotlin.math.roundToLong

@Immutable
internal data class VideoProgressScrubbingConfig(
    val fineScrubbingEnabled: Boolean = true,
    val snapAndOriginCancelEnabled: Boolean = true,
)

internal val LocalVideoProgressScrubbingConfig = compositionLocalOf {
    VideoProgressScrubbingConfig()
}

internal enum class ProgressScrubSnapKind {
    NONE,
    ORIGIN_CANCEL,
    CHAPTER,
    PEAK_HIGHLIGHT,
}

internal data class ProgressScrubSnapPoint(
    val positionMs: Long,
    val kind: ProgressScrubSnapKind,
    val label: String,
)

internal data class ProgressScrubResolution(
    val positionMs: Long,
    val fineScrubbingActive: Boolean,
    val snapKind: ProgressScrubSnapKind,
    val snapLabel: String? = null,
    val cancelledAtOrigin: Boolean = false,
)

internal data class JumpAheadCandidate(
    val targetPositionMs: Long,
    val label: String,
    val isChapter: Boolean,
)

internal const val FINE_SCRUB_ENTER_UPWARD_DP = 36f
internal const val FINE_SCRUB_EXIT_UPWARD_DP = 12f
internal const val FINE_SCRUB_SENSITIVITY_FACTOR = 0.25f

internal fun resolveFineScrubbingActive(
    currentlyActive: Boolean,
    enabled: Boolean,
    upwardDragPx: Float,
    enterThresholdPx: Float,
    exitThresholdPx: Float,
): Boolean {
    if (!enabled) return false
    return if (currentlyActive) {
        upwardDragPx > exitThresholdPx
    } else {
        upwardDragPx >= enterThresholdPx
    }
}

internal fun resolveFineScrubPositionMs(
    anchorPositionMs: Long,
    anchorTouchX: Float,
    currentTouchX: Float,
    containerWidthPx: Float,
    durationMs: Long,
): Long {
    if (durationMs <= 0L || containerWidthPx <= 0f) return anchorPositionMs.coerceAtLeast(0L)
    val deltaFraction = (currentTouchX - anchorTouchX) / containerWidthPx
    // 精准时间轴将全宽映射缩小为 25%（4 倍精度），长视频更精细、短视频也保留可控跨度。
    val effectiveWindowMs = (durationMs * FINE_SCRUB_SENSITIVITY_FACTOR).coerceAtLeast(
        minOf(durationMs.toFloat(), 60_000f)
    )
    val targetMs = anchorPositionMs + (deltaFraction * effectiveWindowMs).roundToLong()
    return targetMs.coerceIn(0L, durationMs)
}

internal fun extractProgressSnapPoints(
    durationMs: Long,
    viewPoints: List<ViewPoint>,
    pbpRidgeSamples: List<PbpRidgeSample>,
): List<ProgressScrubSnapPoint> {
    if (durationMs <= 0L) return emptyList()
    val result = mutableListOf<ProgressScrubSnapPoint>()

    viewPoints.forEach { vp ->
        val startMs = vp.from.toLong() * 1000L
        if (startMs in 1L until durationMs) {
            val title = vp.content.trim().ifEmpty { "章节" }
            result += ProgressScrubSnapPoint(
                positionMs = startMs,
                kind = ProgressScrubSnapKind.CHAPTER,
                label = title,
            )
        }
    }

    if (pbpRidgeSamples.size >= 3) {
        var lastPeakFraction = -1f
        for (i in 1 until pbpRidgeSamples.lastIndex) {
            val sample = pbpRidgeSamples[i]
            if (sample.intensity < 0.72f) continue
            if (sample.intensity < pbpRidgeSamples[i - 1].intensity) continue
            if (sample.intensity < pbpRidgeSamples[i + 1].intensity) continue
            if (lastPeakFraction >= 0f && sample.fraction - lastPeakFraction < 0.05f) continue
            val peakMs = (sample.fraction.coerceIn(0f, 1f) * durationMs).roundToLong()
            if (peakMs in 1L until durationMs) {
                // 若与章节起点过于接近，保留章节语义避免重复吸附
                val nearChapter = result.any { abs(it.positionMs - peakMs) < (durationMs * 0.015f).roundToLong() }
                if (!nearChapter) {
                    result += ProgressScrubSnapPoint(
                        positionMs = peakMs,
                        kind = ProgressScrubSnapKind.PEAK_HIGHLIGHT,
                        label = "高能时刻",
                    )
                    lastPeakFraction = sample.fraction
                }
            }
        }
    }

    return result.sortedBy { it.positionMs }
}

internal fun resolveProgressScrubTarget(
    rawTargetPositionMs: Long,
    originPositionMs: Long,
    durationMs: Long,
    hasLeftOrigin: Boolean,
    fineScrubbingActive: Boolean,
    snapAndOriginCancelEnabled: Boolean,
    snapPoints: List<ProgressScrubSnapPoint>,
): ProgressScrubResolution {
    val clamped = rawTargetPositionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
    if (!snapAndOriginCancelEnabled || durationMs <= 0L) {
        return ProgressScrubResolution(
            positionMs = clamped,
            fineScrubbingActive = fineScrubbingActive,
            snapKind = ProgressScrubSnapKind.NONE,
        )
    }

    val originCancelThresholdMs = (durationMs * 0.018f).roundToLong().coerceIn(1_500L, 5_000L)
    if (hasLeftOrigin && abs(clamped - originPositionMs) <= originCancelThresholdMs) {
        return ProgressScrubResolution(
            positionMs = originPositionMs.coerceIn(0L, durationMs),
            fineScrubbingActive = fineScrubbingActive,
            snapKind = ProgressScrubSnapKind.ORIGIN_CANCEL,
            snapLabel = "松手取消跳转",
            cancelledAtOrigin = true,
        )
    }

    // 精准时间轴模式主打逐秒微调，不强行磁吸；普通模式对章节和高能峰值做轻磁吸。
    if (!fineScrubbingActive && snapPoints.isNotEmpty()) {
        val snapToleranceMs = (durationMs * 0.012f).roundToLong().coerceIn(1_000L, 4_000L)
        val nearest = snapPoints.minByOrNull { abs(it.positionMs - clamped) }
        if (nearest != null && abs(nearest.positionMs - clamped) <= snapToleranceMs) {
            return ProgressScrubResolution(
                positionMs = nearest.positionMs,
                fineScrubbingActive = false,
                snapKind = nearest.kind,
                snapLabel = nearest.label,
            )
        }
    }

    return ProgressScrubResolution(
        positionMs = clamped,
        fineScrubbingActive = fineScrubbingActive,
        snapKind = ProgressScrubSnapKind.NONE,
    )
}

internal fun hasLeftOriginThreshold(
    currentPositionMs: Long,
    originPositionMs: Long,
    durationMs: Long,
): Boolean {
    if (durationMs <= 0L) return false
    val leaveThresholdMs = (durationMs * 0.03f).roundToLong().coerceIn(2_500L, 8_000L)
    return abs(currentPositionMs - originPositionMs) >= leaveThresholdMs
}

internal fun resolveFilmstripFramePositionsMs(
    centerPositionMs: Long,
    durationMs: Long,
    frameCount: Int = 5,
): List<Long> {
    if (durationMs <= 0L || frameCount <= 0) return emptyList()
    val stepMs = (durationMs / 40L).coerceIn(2_000L, 15_000L)
    val half = frameCount / 2
    return (-half..half).take(frameCount).map { index ->
        (centerPositionMs + index * stepMs).coerceIn(0L, durationMs)
    }
}

/**
 * 双击快进后的高能空降建议（Jump Ahead）：
 * 优先查找前方 8s~120s 内的弹幕热度峰值，其次查找前方下一章节起点。
 */
internal fun resolveDoubleTapJumpAheadCandidate(
    currentPositionMs: Long,
    durationMs: Long,
    viewPoints: List<ViewPoint>,
    pbpRidgeSamples: List<PbpRidgeSample>,
): JumpAheadCandidate? {
    if (durationMs <= 15_000L) return null
    val minAheadMs = currentPositionMs + 8_000L
    val maxAheadMs = (currentPositionMs + 120_000L).coerceAtMost(durationMs - 2_000L)
    if (minAheadMs >= maxAheadMs) return null

    val snapPoints = extractProgressSnapPoints(durationMs, viewPoints, pbpRidgeSamples)
    val aheadPeak = snapPoints.firstOrNull {
        it.kind == ProgressScrubSnapKind.PEAK_HIGHLIGHT && it.positionMs in minAheadMs..maxAheadMs
    }
    if (aheadPeak != null) {
        return JumpAheadCandidate(
            targetPositionMs = aheadPeak.positionMs,
            label = "跳到高能处",
            isChapter = false,
        )
    }

    val aheadChapter = snapPoints.firstOrNull {
        it.kind == ProgressScrubSnapKind.CHAPTER && it.positionMs in minAheadMs..maxAheadMs
    }
    if (aheadChapter != null) {
        val chapterTitle = aheadChapter.label.take(10)
        return JumpAheadCandidate(
            targetPositionMs = aheadChapter.positionMs,
            label = "跳到「$chapterTitle」",
            isChapter = true,
        )
    }

    return null
}
