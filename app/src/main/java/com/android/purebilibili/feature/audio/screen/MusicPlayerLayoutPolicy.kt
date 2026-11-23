package com.android.purebilibili.feature.audio.screen

import com.android.purebilibili.core.util.AppFoldPosture
import com.android.purebilibili.core.util.AppHingeOrientation

internal const val MUSIC_PLAYER_EXPANDED_WIDTH_DP = 600
internal const val MUSIC_PLAYER_COMPACT_DOCK_BOTTOM_PADDING_DP = 70

internal fun resolveMusicPlayerPageTabs(): List<String> = listOf("封面", "歌词")

internal enum class MusicCoverStyle {
    APPLE_MUSIC_CARD,
    APPLE_MUSIC_SQUARE,
    TURNTABLE
}

internal fun resolveNextCoverStyle(current: MusicCoverStyle): MusicCoverStyle = when (current) {
    MusicCoverStyle.APPLE_MUSIC_CARD -> MusicCoverStyle.APPLE_MUSIC_SQUARE
    MusicCoverStyle.APPLE_MUSIC_SQUARE -> MusicCoverStyle.TURNTABLE
    MusicCoverStyle.TURNTABLE -> MusicCoverStyle.APPLE_MUSIC_CARD
}

internal fun resolveCoverStyleLabel(style: MusicCoverStyle): String = when (style) {
    MusicCoverStyle.APPLE_MUSIC_CARD -> "Apple Music 宽屏卡片"
    MusicCoverStyle.APPLE_MUSIC_SQUARE -> "Apple Music 方形专辑"
    MusicCoverStyle.TURNTABLE -> "经典黑胶唱盘"
}

internal fun resolveCoverStyleShortLabel(style: MusicCoverStyle): String = when (style) {
    MusicCoverStyle.APPLE_MUSIC_CARD -> "宽屏"
    MusicCoverStyle.APPLE_MUSIC_SQUARE -> "方图"
    MusicCoverStyle.TURNTABLE -> "转盘"
}

internal enum class MusicPlayerLayout {
    COMPACT_PAGER,
    COMPACT_LANDSCAPE,
    EXPANDED_SPLIT,
    TABLETOP,
    PIP_ARTWORK
}

internal enum class MusicPlayerLayoutPreference {
    AUTO,
    SPLIT,
    TABLETOP,
}

internal fun nextMusicPlayerLayoutPreference(
    current: MusicPlayerLayoutPreference,
): MusicPlayerLayoutPreference = when (current) {
    MusicPlayerLayoutPreference.AUTO -> MusicPlayerLayoutPreference.SPLIT
    MusicPlayerLayoutPreference.SPLIT -> MusicPlayerLayoutPreference.TABLETOP
    MusicPlayerLayoutPreference.TABLETOP -> MusicPlayerLayoutPreference.AUTO
}

internal fun resolveMusicPlayerLayoutPreferenceLabel(
    preference: MusicPlayerLayoutPreference,
): String = when (preference) {
    MusicPlayerLayoutPreference.AUTO -> "自动布局"
    MusicPlayerLayoutPreference.SPLIT -> "左右布局"
    MusicPlayerLayoutPreference.TABLETOP -> "上下陈列"
}

internal enum class ExpandedRightPaneTab {
    LYRICS,
    QUEUE
}

internal const val LARGE_SCREEN_MAX_CONTENT_WIDTH_DP = 1200

internal fun resolveLargeScreenGutterDp(widthDp: Int): Int =
    if (widthDp >= 840) 48 else 28

internal fun resolveLargeScreenHorizontalPaddingDp(widthDp: Int): Int =
    if (widthDp >= 840) 48 else 24

internal fun resolveMusicPlayerLayout(
    widthDp: Int,
    isInPipMode: Boolean,
    heightDp: Int = 800,
    fontScale: Float = 1f,
    preference: MusicPlayerLayoutPreference = MusicPlayerLayoutPreference.AUTO,
    posture: AppFoldPosture = AppFoldPosture.None,
    hingeOrientation: AppHingeOrientation = AppHingeOrientation.None,
    hingeStartDp: Int? = null,
    hingeEndDp: Int? = null,
    hasObstructingHinge: Boolean = false,
): MusicPlayerLayout = when {
    isInPipMode -> MusicPlayerLayout.PIP_ARTWORK
    hasObstructingHinge && hingeOrientation == AppHingeOrientation.Horizontal -> {
        if (hasUsableTabletopPanes(heightDp, hingeStartDp, hingeEndDp)) {
            MusicPlayerLayout.TABLETOP
        } else {
            MusicPlayerLayout.COMPACT_PAGER
        }
    }
    hasObstructingHinge && hingeOrientation == AppHingeOrientation.Vertical -> {
        if (hasUsableSplitPanes(widthDp, hingeStartDp, hingeEndDp)) {
            MusicPlayerLayout.EXPANDED_SPLIT
        } else {
            MusicPlayerLayout.COMPACT_PAGER
        }
    }
    widthDp >= 600 && widthDp > heightDp && heightDp < 480 -> MusicPlayerLayout.COMPACT_LANDSCAPE
    preference == MusicPlayerLayoutPreference.TABLETOP && heightDp >= 560 -> {
        MusicPlayerLayout.TABLETOP
    }
    preference == MusicPlayerLayoutPreference.SPLIT && hasRoomForTwoPanes(widthDp, heightDp, fontScale) -> {
        MusicPlayerLayout.EXPANDED_SPLIT
    }
    posture == AppFoldPosture.Tabletop && heightDp >= 480 -> MusicPlayerLayout.TABLETOP
    hasRoomForTwoPanes(widthDp, heightDp, fontScale) -> MusicPlayerLayout.EXPANDED_SPLIT
    else -> MusicPlayerLayout.COMPACT_PAGER
}

private fun hasRoomForTwoPanes(widthDp: Int, heightDp: Int, fontScale: Float): Boolean {
    val minimumHeight = if (fontScale >= 1.3f) 560 else 480
    // Two readable 280dp panes + 28dp gutter + 48dp outer padding.
    return widthDp >= 636 && heightDp >= minimumHeight
}

private fun hasUsableSplitPanes(
    widthDp: Int,
    hingeStartDp: Int?,
    hingeEndDp: Int?,
): Boolean {
    if (hingeStartDp == null || hingeEndDp == null) return widthDp >= 636
    return hingeStartDp - MUSIC_PLAYER_HINGE_CLEARANCE_DP >= 280 &&
        widthDp - hingeEndDp - MUSIC_PLAYER_HINGE_CLEARANCE_DP >= 280
}

private fun hasUsableTabletopPanes(
    heightDp: Int,
    hingeStartDp: Int?,
    hingeEndDp: Int?,
): Boolean {
    if (hingeStartDp == null || hingeEndDp == null) return heightDp >= 480
    return hingeStartDp - MUSIC_PLAYER_HINGE_CLEARANCE_DP >= 180 &&
        heightDp - hingeEndDp - MUSIC_PLAYER_HINGE_CLEARANCE_DP >= 220
}

internal const val MUSIC_PLAYER_HINGE_CLEARANCE_DP = 16

internal data class MusicTabletopPaneSizes(
    val upperHeightDp: Int,
    val hingeGapDp: Int,
    val lowerHeightDp: Int,
)

internal fun resolveMusicTabletopPaneSizes(
    availableHeightDp: Int,
    hingeStartDp: Int? = null,
    hingeEndDp: Int? = null,
): MusicTabletopPaneSizes {
    val validHinge = hingeStartDp != null && hingeEndDp != null &&
        hingeStartDp in 1 until availableHeightDp &&
        hingeEndDp in (hingeStartDp + 1)..availableHeightDp
    if (!validHinge) {
        val upper = (availableHeightDp * 0.48f).toInt()
        return MusicTabletopPaneSizes(
            upperHeightDp = upper,
            hingeGapDp = 0,
            lowerHeightDp = (availableHeightDp - upper).coerceAtLeast(0),
        )
    }

    val upper = (hingeStartDp!! - MUSIC_PLAYER_HINGE_CLEARANCE_DP).coerceAtLeast(0)
    val gap = (hingeEndDp!! - hingeStartDp + MUSIC_PLAYER_HINGE_CLEARANCE_DP * 2)
        .coerceAtLeast(0)
    return MusicTabletopPaneSizes(
        upperHeightDp = upper,
        hingeGapDp = gap,
        lowerHeightDp = (availableHeightDp - upper - gap).coerceAtLeast(0),
    )
}

internal fun resolveMusicArtworkSizeDp(
    availableWidthDp: Int,
    availableHeightDp: Int,
    layout: MusicPlayerLayout
): Int {
    if (availableWidthDp <= 0 || availableHeightDp <= 0) return 0
    return when (layout) {
        MusicPlayerLayout.COMPACT_PAGER -> minOf(
            (availableWidthDp - 48).coerceAtLeast(0),
            (availableHeightDp - 80).coerceAtLeast(0),
            // 288 为两行标题 + 控制区留出空间，避免矮屏滚动后裁切底部 dock
            288
        )
        MusicPlayerLayout.COMPACT_LANDSCAPE -> minOf(
            (availableWidthDp * 0.38f).toInt(),
            (availableHeightDp - 48).coerceAtLeast(0),
            280,
        )
        MusicPlayerLayout.EXPANDED_SPLIT -> minOf(
            (availableWidthDp / 2 - 40).coerceAtLeast(0),
            (availableHeightDp - 220).coerceAtLeast(100),
            360
        )
        MusicPlayerLayout.TABLETOP -> minOf(
            (availableWidthDp / 2 - 48).coerceAtLeast(0),
            (availableHeightDp / 2 - 48).coerceAtLeast(100),
            285
        )
        MusicPlayerLayout.PIP_ARTWORK -> minOf(availableWidthDp, availableHeightDp)
    }
}
