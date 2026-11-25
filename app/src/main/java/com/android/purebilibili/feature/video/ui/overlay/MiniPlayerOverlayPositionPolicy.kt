package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.ui.unit.IntRect

internal data class MiniPlayerOverlayOffset(
    val x: Float,
    val y: Float
)

internal data class MiniPlayerResizeBounds(
    val minWidthDp: Float,
    val maxWidthDp: Float
)

internal fun clampMiniPlayerOverlayOffset(
    offsetX: Float,
    offsetY: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    miniPlayerWidthPx: Float,
    miniPlayerHeightPx: Float,
    outerPaddingPx: Float,
    topInsetPx: Float,
    bottomInsetPx: Float,
    hingeSafeRegions: List<IntRect> = emptyList(),
): MiniPlayerOverlayOffset {
    val minX = outerPaddingPx
    val maxX = (screenWidthPx - miniPlayerWidthPx - outerPaddingPx).coerceAtLeast(minX)
    val minY = outerPaddingPx + topInsetPx
    val maxY = (screenHeightPx - miniPlayerHeightPx - outerPaddingPx - bottomInsetPx).coerceAtLeast(minY)
    val baseX = offsetX.coerceIn(minX, maxX)
    val baseY = offsetY.coerceIn(minY, maxY)
    if (hingeSafeRegions.size <= 1) {
        return MiniPlayerOverlayOffset(x = baseX, y = baseY)
    }
    // 折叠屏半开/遮挡铰链存在多个安全分区时，将小窗夹进距离当前中心最近且能容纳小窗的安全区，
    // 防止小窗停靠在折痕正中。
    val targetCenterX = baseX + miniPlayerWidthPx * 0.5f
    val targetCenterY = baseY + miniPlayerHeightPx * 0.5f
    val fittingRegions = hingeSafeRegions.filter { region ->
        region.width >= miniPlayerWidthPx && region.height >= miniPlayerHeightPx
    }
    val chosenRegion = (fittingRegions.ifEmpty { hingeSafeRegions }).minByOrNull { region ->
        val rMinX = maxOf(minX, region.left.toFloat() + outerPaddingPx)
        val rMaxX = minOf(maxX, region.right.toFloat() - miniPlayerWidthPx - outerPaddingPx).coerceAtLeast(rMinX)
        val rMinY = maxOf(minY, region.top.toFloat() + outerPaddingPx)
        val rMaxY = minOf(maxY, region.bottom.toFloat() - miniPlayerHeightPx - outerPaddingPx).coerceAtLeast(rMinY)
        val cx = baseX.coerceIn(rMinX, rMaxX) + miniPlayerWidthPx * 0.5f
        val cy = baseY.coerceIn(rMinY, rMaxY) + miniPlayerHeightPx * 0.5f
        val dx = cx - targetCenterX
        val dy = cy - targetCenterY
        dx * dx + dy * dy
    } ?: return MiniPlayerOverlayOffset(x = baseX, y = baseY)
    val regionMinX = maxOf(minX, chosenRegion.left.toFloat() + outerPaddingPx)
    val regionMaxX = minOf(maxX, chosenRegion.right.toFloat() - miniPlayerWidthPx - outerPaddingPx)
        .coerceAtLeast(regionMinX)
    val regionMinY = maxOf(minY, chosenRegion.top.toFloat() + outerPaddingPx)
    val regionMaxY = minOf(maxY, chosenRegion.bottom.toFloat() - miniPlayerHeightPx - outerPaddingPx)
        .coerceAtLeast(regionMinY)
    return MiniPlayerOverlayOffset(
        x = baseX.coerceIn(regionMinX, regionMaxX),
        y = baseY.coerceIn(regionMinY, regionMaxY),
    )
}

internal fun resolveMiniPlayerInitialOverlayOffset(
    cardLeftPx: Float?,
    entryFromLeft: Boolean,
    screenWidthPx: Float,
    screenHeightPx: Float,
    miniPlayerWidthPx: Float,
    miniPlayerHeightPx: Float,
    outerPaddingPx: Float,
    topInsetPx: Float,
    bottomInsetPx: Float,
    hingeSafeRegions: List<IntRect> = emptyList(),
): MiniPlayerOverlayOffset {
    return clampMiniPlayerOverlayOffset(
        offsetX = cardLeftPx
            ?: if (entryFromLeft) {
                outerPaddingPx
            } else {
                screenWidthPx - miniPlayerWidthPx - outerPaddingPx
            },
        offsetY = resolveMiniPlayerDockedBottomOffsetY(
            screenHeightPx = screenHeightPx,
            miniPlayerHeightPx = miniPlayerHeightPx,
            outerPaddingPx = outerPaddingPx,
            bottomInsetPx = bottomInsetPx
        ),
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        miniPlayerWidthPx = miniPlayerWidthPx,
        miniPlayerHeightPx = miniPlayerHeightPx,
        outerPaddingPx = outerPaddingPx,
        topInsetPx = topInsetPx,
        bottomInsetPx = bottomInsetPx,
        hingeSafeRegions = hingeSafeRegions,
    )
}

internal fun resolveMiniPlayerOffsetAfterSizeChanged(
    offsetX: Float,
    offsetY: Float,
    screenWidthPx: Float,
    screenHeightPx: Float,
    miniPlayerWidthPx: Float,
    miniPlayerHeightPx: Float,
    outerPaddingPx: Float,
    topInsetPx: Float,
    bottomInsetPx: Float,
    hingeSafeRegions: List<IntRect> = emptyList(),
): MiniPlayerOverlayOffset {
    return clampMiniPlayerOverlayOffset(
        offsetX = offsetX,
        offsetY = offsetY,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        miniPlayerWidthPx = miniPlayerWidthPx,
        miniPlayerHeightPx = miniPlayerHeightPx,
        outerPaddingPx = outerPaddingPx,
        topInsetPx = topInsetPx,
        bottomInsetPx = bottomInsetPx,
        hingeSafeRegions = hingeSafeRegions,
    )
}

internal fun resolveMiniPlayerDockedBottomOffsetY(
    screenHeightPx: Float,
    miniPlayerHeightPx: Float,
    outerPaddingPx: Float,
    bottomInsetPx: Float
): Float {
    return screenHeightPx - miniPlayerHeightPx - outerPaddingPx - bottomInsetPx
}

internal fun resolveMiniPlayerResizeBounds(
    defaultWidthDp: Int,
    defaultHeightDp: Int,
    screenWidthDp: Int,
    screenHeightDp: Int,
    outerPaddingDp: Int,
    topInsetDp: Int,
    bottomInsetDp: Int,
    videoAspectRatio: Float = defaultWidthDp.toFloat() / defaultHeightDp.coerceAtLeast(1),
): MiniPlayerResizeBounds {
    val availableWidth = (screenWidthDp - outerPaddingDp * 2).coerceAtLeast(1).toFloat()
    val availableHeight = (screenHeightDp - outerPaddingDp * 2 - topInsetDp - bottomInsetDp)
        .coerceAtLeast(1).toFloat()
    val reference = resolveAdaptiveMiniPlayerDimensions(videoAspectRatio, defaultWidthDp.toFloat(), defaultHeightDp.toFloat())
    val maxScale = minOf(1.75f, availableWidth / reference.widthDp, availableHeight / reference.heightDp)
    val maxWidth = (defaultWidthDp * maxScale).coerceAtLeast(1f)
    return MiniPlayerResizeBounds(
        minWidthDp = (defaultWidthDp * 0.75f).coerceAtLeast(168f).coerceAtMost(maxWidth),
        maxWidthDp = maxWidth,
    )
}

internal enum class MiniPlayerEdgeStashSide { LEFT, RIGHT }

/** Ordinary edge docking never arms hiding. Pull outward; a smaller release threshold avoids flicker. */
internal fun resolveMiniPlayerEdgeStashSide(
    rawOffsetX: Float,
    minOffsetX: Float,
    maxOffsetX: Float,
    thresholdPx: Float,
    previousSide: MiniPlayerEdgeStashSide? = null,
): MiniPlayerEdgeStashSide? {
    if (!rawOffsetX.isFinite() || !minOffsetX.isFinite() || !maxOffsetX.isFinite() ||
        !thresholdPx.isFinite() || thresholdPx <= 0f || maxOffsetX < minOffsetX) return null
    val leftThreshold = thresholdPx * if (previousSide == MiniPlayerEdgeStashSide.LEFT) 0.65f else 1f
    val rightThreshold = thresholdPx * if (previousSide == MiniPlayerEdgeStashSide.RIGHT) 0.65f else 1f
    return when {
        minOffsetX - rawOffsetX >= leftThreshold -> MiniPlayerEdgeStashSide.LEFT
        rawOffsetX - maxOffsetX >= rightThreshold -> MiniPlayerEdgeStashSide.RIGHT
        else -> null
    }
}
