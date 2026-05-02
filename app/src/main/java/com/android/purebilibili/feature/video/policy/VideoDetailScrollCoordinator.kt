package com.android.purebilibili.feature.video.policy

import kotlin.math.abs

internal data class VideoDetailScrollUpdate(
    val nextOffsetPx: Float,
    val consumedDeltaPx: Float
)

internal fun reduceVideoDetailPreScroll(
    currentOffsetPx: Float,
    deltaPx: Float,
    minOffsetPx: Float,
    inlinePortraitScrollEnabled: Boolean,
    isPortraitFullscreen: Boolean,
    minUpdateDeltaPx: Float = 0.75f
): VideoDetailScrollUpdate? {
    if (!inlinePortraitScrollEnabled || isPortraitFullscreen) return null
    if (deltaPx >= 0f) return null
    return reduceVideoDetailScrollOffset(
        currentOffsetPx = currentOffsetPx,
        deltaPx = deltaPx,
        minOffsetPx = minOffsetPx,
        minUpdateDeltaPx = minUpdateDeltaPx
    )
}

internal fun reduceVideoDetailPostScroll(
    currentOffsetPx: Float,
    deltaPx: Float,
    minOffsetPx: Float,
    inlinePortraitScrollEnabled: Boolean,
    isPortraitFullscreen: Boolean,
    minUpdateDeltaPx: Float = 0.75f
): VideoDetailScrollUpdate? {
    if (!inlinePortraitScrollEnabled || isPortraitFullscreen) return null
    if (deltaPx <= 0f) return null
    return reduceVideoDetailScrollOffset(
        currentOffsetPx = currentOffsetPx,
        deltaPx = deltaPx,
        minOffsetPx = minOffsetPx,
        minUpdateDeltaPx = minUpdateDeltaPx
    )
}

internal fun reduceVideoDetailDragHandleScroll(
    currentOffsetPx: Float,
    deltaPx: Float,
    minOffsetPx: Float,
    isPortraitFullscreen: Boolean,
    minUpdateDeltaPx: Float = 0.75f
): VideoDetailScrollUpdate? {
    if (isPortraitFullscreen) return null
    return reduceVideoDetailScrollOffset(
        currentOffsetPx = currentOffsetPx,
        deltaPx = deltaPx,
        minOffsetPx = minOffsetPx,
        minUpdateDeltaPx = minUpdateDeltaPx
    )
}

internal fun resolveVideoDetailCollapseProgress(
    playerHeightOffsetPx: Float,
    collapseRangePx: Float,
    isPortraitFullscreen: Boolean
): Float {
    if (isPortraitFullscreen) return 0f
    if (collapseRangePx <= 0f) return 0f
    val effectiveOffset = playerHeightOffsetPx.coerceAtMost(0f)
    return (abs(effectiveOffset) / collapseRangePx).coerceIn(0f, 1f)
}

private fun reduceVideoDetailScrollOffset(
    currentOffsetPx: Float,
    deltaPx: Float,
    minOffsetPx: Float,
    maxOffsetPx: Float = 0f,
    minUpdateDeltaPx: Float
): VideoDetailScrollUpdate? {
    if (abs(deltaPx) < minUpdateDeltaPx) return null
    val nextOffset = (currentOffsetPx + deltaPx).coerceIn(minOffsetPx, maxOffsetPx)
    val consumedDelta = nextOffset - currentOffsetPx
    if (abs(consumedDelta) < minUpdateDeltaPx) return null
    return VideoDetailScrollUpdate(
        nextOffsetPx = nextOffset,
        consumedDeltaPx = consumedDelta
    )
}
