package com.android.purebilibili.feature.video.ui.overlay

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
    bottomInsetPx: Float
): MiniPlayerOverlayOffset {
    val minX = outerPaddingPx
    val maxX = (screenWidthPx - miniPlayerWidthPx - outerPaddingPx).coerceAtLeast(minX)
    val minY = outerPaddingPx + topInsetPx
    val maxY = (screenHeightPx - miniPlayerHeightPx - outerPaddingPx - bottomInsetPx).coerceAtLeast(minY)
    return MiniPlayerOverlayOffset(
        x = offsetX.coerceIn(minX, maxX),
        y = offsetY.coerceIn(minY, maxY)
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
    bottomInsetPx: Float
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
        bottomInsetPx = bottomInsetPx
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
    bottomInsetPx: Float
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
        bottomInsetPx = bottomInsetPx
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
