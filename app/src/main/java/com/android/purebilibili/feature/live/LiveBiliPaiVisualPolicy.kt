package com.android.purebilibili.feature.live

import androidx.compose.ui.graphics.Color
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.core.ui.CompactCapsuleChromeSpec

internal data class LiveBiliPaiHomeMetrics(
    val safeSpaceDp: Int,
    val cardSpaceDp: Int,
    val cardRadiusDp: Int,
    val coverAspectRatio: Float,
    val followAvatarSizeDp: Int,
    val followItemExtentDp: Int
)

internal data class LiveVisualSpec(
    val homeMetrics: LiveBiliPaiHomeMetrics,
    val maxContentWidthDp: Int,
    val roomCardDetailsMinHeightDp: Int,
    val playerButtonTouchTargetDp: Int,
    val playerButtonVisualSizeDp: Int,
    val playerQualityDialogWidthDp: Int,
    val emptyStateContainerSizeDp: Int,
    val emptyStateIconSizeDp: Int,
)

internal data class LiveChatInputVisualSpec(
    val controlSizeDp: Int,
    val inputFieldHeightDp: Int,
    val iconSizeDp: Int,
    val sendButtonSizeDp: Int,
    val sendIconOffsetXDp: Int,
    val sendIconOffsetYDp: Int,
    val overlayMessageSpaceDp: Int,
    val likeBadgeOffsetXDp: Int,
    val likeBadgeOffsetYDp: Int,
)

internal data class LiveSheetVisualSpec(
    val emoticonListMaxHeightDp: Int,
    val emoticonImageHeightDp: Int,
    val contributionListMaxHeightDp: Int,
)

internal data class LivePlayerControlVisualSpec(
    val rowHeightDp: Int,
    val iconSizeDp: Int,
)

internal data class LiveBiliPaiChipColors(
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
    val unselectedContainerColor: Color,
    val unselectedContentColor: Color
)

internal data class LiveBiliPaiChatBubbleTokens(
    val cornerRadiusDp: Int,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val fontSizeSp: Int,
    val backgroundAlpha: Float,
    val nameAlpha: Float
)

internal data class LiveBiliPaiRoomColorTokens(
    val baseBackgroundColor: Color,
    val backdropImageAlpha: Float,
    val inputContainerAlpha: Float,
    val inputOverlayColor: Color,
    val inputContentColor: Color
)

internal data class LiveInteractionSegmentedControlSpec(
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val heightDp: Int,
    val indicatorHeightDp: Int,
    val labelFontSizeSp: Int
)

internal data class LandscapeLiveChatVisualSpec(
    val headerFontSizeSp: Int,
    val subtitleFontSizeSp: Int,
    val medalFontSizeSp: Int,
    val messageFontSizeSp: Int,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val medalHorizontalPaddingDp: Int,
    val medalVerticalPaddingDp: Float,
)

internal data class LiveMedalBadgeVisualSpec(
    val verticalPaddingDp: Float = 0.5f,
    val dividerWidthDp: Float = 0.5f,
    val heightDp: Int = 16,
    val cornerRadiusDp: Int = 4,
    val horizontalPaddingDp: Int = 4,
    val fontSizeSp: Int = 10,
)


internal fun resolveLiveVisualSpec(
    tabPresentation: AppTopTabPresentation,
): LiveVisualSpec {
    val safeSpaceDp = when (tabPresentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> 12
        AppTopTabPresentation.TONAL_CAPSULE -> 16
        AppTopTabPresentation.MATERIAL_UNDERLINE -> 18
    }
    val cardSpaceDp = when (tabPresentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> 8
        AppTopTabPresentation.TONAL_CAPSULE -> 10
        AppTopTabPresentation.MATERIAL_UNDERLINE -> 12
    }
    val playerButtonVisualSizeDp = when (tabPresentation) {
        AppTopTabPresentation.MOVING_CAPSULE,
        AppTopTabPresentation.TONAL_CAPSULE -> 38
        AppTopTabPresentation.MATERIAL_UNDERLINE -> 40
    }
    val roomCardDetailsMinHeightDp = when (tabPresentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> 90
        AppTopTabPresentation.TONAL_CAPSULE -> 95
        AppTopTabPresentation.MATERIAL_UNDERLINE -> 88
    }
    return LiveVisualSpec(
        homeMetrics = LiveBiliPaiHomeMetrics(
            safeSpaceDp = safeSpaceDp,
            cardSpaceDp = cardSpaceDp,
            cardRadiusDp = 10,
            coverAspectRatio = 16f / 10f,
            followAvatarSizeDp = 45,
            followItemExtentDp = 70,
        ),
        maxContentWidthDp = 1200,
        roomCardDetailsMinHeightDp = roomCardDetailsMinHeightDp,
        playerButtonTouchTargetDp = 40,
        playerButtonVisualSizeDp = playerButtonVisualSizeDp,
        playerQualityDialogWidthDp = 280,
        emptyStateContainerSizeDp = 64,
        emptyStateIconSizeDp = 28,
    )
}

internal fun resolveLiveChatInputVisualSpec(): LiveChatInputVisualSpec = LiveChatInputVisualSpec(
    controlSizeDp = 30,
    inputFieldHeightDp = 30,
    iconSizeDp = 20,
    sendButtonSizeDp = 30,
    sendIconOffsetXDp = -2,
    sendIconOffsetYDp = 2,
    overlayMessageSpaceDp = 10,
    likeBadgeOffsetXDp = 8,
    likeBadgeOffsetYDp = -6,
)

internal fun resolveLiveSheetVisualSpec(): LiveSheetVisualSpec = LiveSheetVisualSpec(
    emoticonListMaxHeightDp = 420,
    emoticonImageHeightDp = 34,
    contributionListMaxHeightDp = 360,
)

internal fun resolveLivePlayerControlVisualSpec(): LivePlayerControlVisualSpec =
    LivePlayerControlVisualSpec(
        rowHeightDp = 40,
        iconSizeDp = 14,
    )

internal fun resolveLiveBiliPaiHomeMetrics(): LiveBiliPaiHomeMetrics {
    return resolveLiveVisualSpec(
        tabPresentation = AppTopTabPresentation.MOVING_CAPSULE,
    ).homeMetrics
}

internal fun resolveLiveBiliPaiHomeMetrics(
    tabPresentation: AppTopTabPresentation,
): LiveBiliPaiHomeMetrics {
    return resolveLiveVisualSpec(tabPresentation).homeMetrics
}

internal fun resolveLiveBiliPaiGridColumns(
    widthDp: Int,
    isTabletLayout: Boolean,
): Int {
    // Same density rule for phones, tablets and resizable windows.
    return ((widthDp.coerceAtLeast(1) + 239) / 240).coerceAtLeast(1)
}

internal fun resolveLiveBiliPaiChipColors(
    selectedContainer: Color,
    selectedContent: Color,
    unselectedContent: Color
): LiveBiliPaiChipColors {
    return LiveBiliPaiChipColors(
        selectedContainerColor = selectedContainer,
        selectedContentColor = selectedContent,
        unselectedContainerColor = Color.Transparent,
        unselectedContentColor = unselectedContent
    )
}

internal fun resolveLiveBiliPaiChatBubbleTokens(
    isOverlay: Boolean,
    isDark: Boolean
): LiveBiliPaiChatBubbleTokens {
    val backgroundAlpha = when {
        isOverlay -> 0f
        else -> 0.08f
    }
    val nameAlpha = if (isOverlay) 0.90f else 0.60f
    return LiveBiliPaiChatBubbleTokens(
        cornerRadiusDp = 14,
        horizontalPaddingDp = 10,
        verticalPaddingDp = 4,
        fontSizeSp = 14,
        backgroundAlpha = backgroundAlpha,
        nameAlpha = nameAlpha
    )
}

internal fun shouldRenderLiveDanmaku(
    text: String,
    emoticonUrl: String?
): Boolean {
    return text.isNotBlank() || !emoticonUrl.isNullOrBlank()
}

internal fun shouldRenderLiveDanmakuImageEmoticon(emoticonUrl: String?): Boolean {
    return !emoticonUrl.isNullOrBlank()
}

internal fun shouldStopLivePlaybackOnRouteDispose(isChangingConfigurations: Boolean): Boolean {
    return !isChangingConfigurations
}

internal fun resolveLiveInteractionSegmentedControlSpec(
    compactChrome: CompactCapsuleChromeSpec,
): LiveInteractionSegmentedControlSpec {
    return LiveInteractionSegmentedControlSpec(
        horizontalPaddingDp = compactChrome.chipHorizontalPaddingDp,
        verticalPaddingDp = 2,
        heightDp = compactChrome.primaryHeightDp,
        indicatorHeightDp = com.android.purebilibili.core.ui.roundMatchedLiquidIndicatorHeightDp(
            compactChrome.primaryHeightDp.toFloat()
        ),
        labelFontSizeSp = 14
    )
}

internal fun resolveLandscapeLiveChatVisualSpec(): LandscapeLiveChatVisualSpec {
    return LandscapeLiveChatVisualSpec(
        headerFontSizeSp = 13,
        subtitleFontSizeSp = 11,
        medalFontSizeSp = 9,
        messageFontSizeSp = 14,
        horizontalPaddingDp = 12,
        verticalPaddingDp = 10,
        medalHorizontalPaddingDp = 3,
        medalVerticalPaddingDp = 0.5f,
    )
}

internal fun resolveLiveMedalColor(colorInt: Int): Color {
    return if (colorInt != 0) Color(colorInt) else LiveStatusPalette.MedalFallback
}

internal fun resolveLiveSuperChatColor(colorInt: Int): Color {
    return if (colorInt != 0) Color(colorInt) else LiveStatusPalette.SuperChatFallback
}

internal fun resolveLiveMedalBadgeVisualSpec(): LiveMedalBadgeVisualSpec {
    return LiveMedalBadgeVisualSpec(
        verticalPaddingDp = 0.5f,
        dividerWidthDp = 0.5f,
        heightDp = 16,
        cornerRadiusDp = 4,
        horizontalPaddingDp = 4,
        fontSizeSp = 10,
    )
}

internal fun resolveLiveLevelColor(level: Int): Color = when {
    level >= 40 -> LiveStatusPalette.LevelHigh
    level >= 20 -> LiveStatusPalette.LevelMedium
    else -> LiveStatusPalette.LevelLow
}

internal fun resolveLiveBiliPaiRoomColorTokens(
    inputOverlayColor: Color = LiveStatusPalette.MediaContent,
    inputContentColor: Color = LiveStatusPalette.InputContent,
): LiveBiliPaiRoomColorTokens {
    return LiveBiliPaiRoomColorTokens(
        baseBackgroundColor = LiveStatusPalette.MediaScrim,
        backdropImageAlpha = 0.60f,
        inputContainerAlpha = 0.10f,
        inputOverlayColor = inputOverlayColor,
        inputContentColor = inputContentColor
    )
}
