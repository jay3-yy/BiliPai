package com.android.purebilibili.feature.home.components.cards

/**
 * Shared metrics for every side-by-side video row
 * (partition, related, space contributions, personal lists).
 *
 * Transparent video rows use a 100dp-high cover with the selected aspect ratio. Narrow windows
 * can shrink it; full titles and large fonts only grow the info area, keeping the cover unchanged.
 */
internal const val HORIZONTAL_VIDEO_CARD_COVER_ASPECT_RATIO = 16f / 10f
internal const val HORIZONTAL_VIDEO_CARD_COVER_WIDTH_DP = 140
internal const val WIDE_LIST_COVER_MAX_WIDTH_DP = 220f
internal const val HORIZONTAL_VIDEO_CARD_COVER_INFO_GAP_DP = 10
internal const val HORIZONTAL_VIDEO_CARD_MIN_HEIGHT_DP = 90f
internal const val HORIZONTAL_VIDEO_CARD_INFO_PADDING_DP = 8
internal const val HORIZONTAL_VIDEO_CARD_INFO_SPACING_DP = 4
internal const val HORIZONTAL_VIDEO_CARD_ACTION_INSET_DP = 4
internal const val HORIZONTAL_VIDEO_STAT_ROW_SPACING_DP = 8
internal const val HORIZONTAL_VIDEO_STAT_WRAP_SPACING_DP = 2
internal const val HORIZONTAL_VIDEO_STAT_ICON_SIZE_DP = 13
internal const val HORIZONTAL_VIDEO_STAT_ICON_TEXT_GAP_DP = 2

/** 横卡封面在手机上按行宽分配，大屏上停止放大，为列表密度和信息区保留空间。 */
internal fun resolveHorizontalVideoCoverWidthDp(availableWidthDp: Float): Float {
    val fraction = 0.55f - 0.10f * ((availableWidthDp - 320f) / 160f).coerceIn(0f, 1f)
    return minOf(availableWidthDp * fraction, WIDE_LIST_COVER_MAX_WIDTH_DP)
}

/** 真实横卡与加载占位按同一可用行宽收缩，避免窄分栏加载完成后封面跳宽。 */
internal fun resolvePlainHorizontalVideoCoverWidthDp(
    availableWidthDp: Float,
    coverAspectRatio: Float,
): Float {
    val width = availableWidthDp.coerceAtLeast(0f)
    val fraction = 0.55f - 0.10f * ((width - 320f) / 160f).coerceIn(0f, 1f)
    return minOf(
        100f * coverAspectRatio,
        width * fraction,
        (width - HORIZONTAL_VIDEO_CARD_COVER_INFO_GAP_DP).coerceAtLeast(0f),
    )
}

internal fun resolveHorizontalVideoCoverHeightDp(
    coverWidthDp: Float = HORIZONTAL_VIDEO_CARD_COVER_WIDTH_DP.toFloat(),
    coverAspectRatio: Float = HORIZONTAL_VIDEO_CARD_COVER_ASPECT_RATIO,
): Float {
    val safeRatio = coverAspectRatio.coerceAtLeast(1f)
    return coverWidthDp / safeRatio
}

internal fun resolveHorizontalVideoCardMinimumHeightDp(
    fontScale: Float,
    coverAspectRatio: Float = HORIZONTAL_VIDEO_CARD_COVER_ASPECT_RATIO,
): Float = maxOf(
    resolveHorizontalVideoCoverHeightDp(coverAspectRatio = coverAspectRatio),
    HORIZONTAL_VIDEO_CARD_MIN_HEIGHT_DP * fontScale.coerceAtLeast(1f),
)

@androidx.compose.runtime.Composable
internal fun horizontalVideoCardTypography() = com.android.purebilibili.core.ui.feedContentTypography(
    com.android.purebilibili.core.ui.FeedTitleHierarchy.Standard,
)
