package com.android.purebilibili.feature.home.components.cards

private const val COMPACT_STAT_BADGE_SPACING_DP = 6f
private const val COMPACT_DURATION_BADGE_SPACING_DP = 6f
private const val COMPACT_ONLINE_BADGE_MIN_WIDTH_DP = 52f
private const val HISTORY_PROGRESS_BAR_HEIGHT_DP = 2f
private const val HISTORY_PROGRESS_BAR_CLEARANCE_DP = 2f
private const val COMPACT_STATS_BASE_BOTTOM_PADDING_DP = 0f
private const val FLOATING_DURATION_BASE_BOTTOM_PADDING_DP = 4f
// 时长统计 pill：闹钟图标 + 文本，文本宽度按字符数估算（labelSmall 半角/全角混合）。
private const val DURATION_STAT_BASE_MIN_WIDTH_DP = 30f
private const val DURATION_STAT_MIN_WIDTH_DP = 44f
private const val DURATION_STAT_MAX_WIDTH_DP = 68f

internal data class VideoCardCompactCoverStatsLayout(
    val primaryMinWidthDp: Float,
    val secondaryMinWidthDp: Float,
    val showSecondaryStat: Boolean,
    val showOnlineCount: Boolean,
    val statsEndPaddingDp: Float
)

internal data class VideoCardCoverOverlayBottomLayout(
    val historyProgressBarHeightDp: Float,
    val compactStatsBottomPaddingDp: Float,
    val floatingDurationBottomPaddingDp: Float
)

internal fun resolveVideoCardPrimaryStatBadgeMinWidthDp(
    statText: String
): Float {
    val normalizedLength = statText.trim().length.coerceAtLeast(3)
    return (34f + normalizedLength * 6f).coerceIn(52f, 72f)
}

internal fun resolveVideoCardSecondaryStatBadgeMinWidthDp(
    statText: String
): Float {
    val normalizedLength = statText.trim().length.coerceAtLeast(3)
    return (40f + normalizedLength * 6f).coerceIn(58f, 76f)
}

/** 时长作为统计行 pill（闹钟图标 + 文本）时的最小宽度估算。 */
internal fun resolveVideoCardDurationStatMinWidthDp(
    durationText: String
): Float {
    val normalizedLength = durationText.trim().length.coerceAtLeast(4)
    return (DURATION_STAT_BASE_MIN_WIDTH_DP + normalizedLength * 6f)
        .coerceIn(DURATION_STAT_MIN_WIDTH_DP, DURATION_STAT_MAX_WIDTH_DP)
}

internal fun resolveVideoCardCompactCoverStatsLayout(
    availableWidthDp: Float,
    primaryStatText: String,
    secondaryStatText: String?,
    hasOnlineCount: Boolean,
    durationBadgeMinWidthDp: Float = 0f,
    durationStatMinWidthDp: Float = 0f
): VideoCardCompactCoverStatsLayout {
    val primaryMinWidth = resolveVideoCardPrimaryStatBadgeMinWidthDp(primaryStatText)
    val showSecondary = !secondaryStatText.isNullOrBlank()
    val secondaryMinWidth = if (showSecondary) {
        resolveVideoCardSecondaryStatBadgeMinWidthDp(secondaryStatText.orEmpty())
    } else {
        0f
    }
    val durationReserveWidth = if (durationBadgeMinWidthDp > 0f) {
        durationBadgeMinWidthDp + COMPACT_DURATION_BADGE_SPACING_DP
    } else {
        0f
    }
    // 时长作为统计 pill 时的宽度预算：只在 OUTSIDE_COVER（时长进统计行）时参与。
    val hasDurationStat = durationStatMinWidthDp > 0f
    val durationStatRequired = if (hasDurationStat) {
        durationStatMinWidthDp + COMPACT_STAT_BADGE_SPACING_DP
    } else {
        0f
    }
    val statsAvailableWidth = (availableWidthDp - durationReserveWidth).coerceAtLeast(0f)
    val requiredForPrimaryAndDuration = primaryMinWidth + durationStatRequired
    val requiredForPrimaryAndSecondary = requiredForPrimaryAndDuration +
        if (showSecondary) COMPACT_STAT_BADGE_SPACING_DP + secondaryMinWidth else 0f
    val requiredWithOnline = requiredForPrimaryAndSecondary +
        if (hasOnlineCount) COMPACT_STAT_BADGE_SPACING_DP + COMPACT_ONLINE_BADGE_MIN_WIDTH_DP else 0f

    return VideoCardCompactCoverStatsLayout(
        primaryMinWidthDp = primaryMinWidth,
        secondaryMinWidthDp = secondaryMinWidth,
        // 评论数不为时长让位；时长是否换行由封面布局按实际尺寸测量决定。
        showSecondaryStat = showSecondary,
        showOnlineCount = hasOnlineCount && statsAvailableWidth >= requiredWithOnline,
        statsEndPaddingDp = durationReserveWidth
    )
}

internal fun resolveVideoCardCoverOverlayBottomLayout(
    showHistoryProgressBar: Boolean
): VideoCardCoverOverlayBottomLayout {
    val progressReserve = HISTORY_PROGRESS_BAR_HEIGHT_DP + HISTORY_PROGRESS_BAR_CLEARANCE_DP
    return VideoCardCoverOverlayBottomLayout(
        historyProgressBarHeightDp = HISTORY_PROGRESS_BAR_HEIGHT_DP,
        compactStatsBottomPaddingDp = COMPACT_STATS_BASE_BOTTOM_PADDING_DP + progressReserve,
        floatingDurationBottomPaddingDp = FLOATING_DURATION_BASE_BOTTOM_PADDING_DP + progressReserve
    )
}
