package com.android.purebilibili.feature.space

import com.android.purebilibili.data.model.response.VideoSortOrder
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import kotlin.math.roundToInt

internal data class SpaceSegmentedTabChromeSpec(
    val selectedIndex: Int,
    val heightDp: Int,
    val indicatorHeightDp: Int,
    val horizontalPaddingDp: Int,
    val itemWidthDp: Int?,
    val scrollable: Boolean,
    val liquidGlassEffectsEnabled: Boolean,
    val dragSelectionEnabled: Boolean
)

internal data class SpaceContributionToolbarSpec(
    val tabHeightDp: Int,
    val tabIndicatorHeightDp: Int,
    val collapsedTabWidthDp: Int,
    val expandedTabRailHeightDp: Int,
    val horizontalPaddingDp: Int,
    val showVideoActions: Boolean,
    val showTotalText: Boolean,
    val showPlayAllText: Boolean,
    val showSortText: Boolean,
    val collapseAfterTabSelection: Boolean
)

private const val SPACE_SEGMENTED_TAB_HORIZONTAL_PADDING_DP = 16
private const val SPACE_SCROLLABLE_CONTRIBUTION_ITEM_MIN_WIDTH_DP = 104
private const val SPACE_SCROLLABLE_CONTRIBUTION_ITEM_MAX_WIDTH_DP = 320
private const val SPACE_SCROLLABLE_CONTRIBUTION_ITEM_EMERGENCY_MIN_WIDTH_DP = 72
private const val SPACE_SECONDARY_MIN_VISIBLE_ITEM_COUNT = 3
private const val SPACE_SCROLLABLE_CONTRIBUTION_ITEM_TEXT_PADDING_DP = 44
private const val SPACE_SCROLLABLE_CONTRIBUTION_CJK_CHAR_WIDTH_DP = 15
private const val SPACE_SCROLLABLE_CONTRIBUTION_ASCII_CHAR_WIDTH_DP = 8
private const val SPACE_CONTRIBUTION_TOOLBAR_COMPACT_WIDTH_DP = 430
private const val SPACE_CONTRIBUTION_TOOLBAR_ROOMY_WIDTH_DP = 480

internal fun resolveSpaceMainTabChromeSpec(
    tabs: List<SpaceMainTabItem>,
    selectedTab: SpaceMainTab
): SpaceSegmentedTabChromeSpec {
    val selectedIndex = tabs.indexOfFirst { it.tab == selectedTab }.coerceAtLeast(0)
    return SpaceSegmentedTabChromeSpec(
        selectedIndex = selectedIndex,
        heightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp,
        indicatorHeightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp,
        horizontalPaddingDp = SPACE_SEGMENTED_TAB_HORIZONTAL_PADDING_DP,
        itemWidthDp = null,
        scrollable = tabs.size > 4,
        liquidGlassEffectsEnabled = true,
        dragSelectionEnabled = tabs.size > 1
    )
}

internal fun resolveSpaceContributionTabChromeSpec(
    tabs: List<SpaceContributionTab>,
    selectedTabId: String,
    selectedSubTab: SpaceSubTab
): SpaceSegmentedTabChromeSpec {
    val selectedIndex = tabs.indexOfFirst { it.id == selectedTabId }
        .takeIf { it >= 0 }
        ?: tabs.indexOfFirst { it.subTab == selectedSubTab }.coerceAtLeast(0)
    val scrollable = shouldScrollSpaceContributionTabs(tabs)
    return SpaceSegmentedTabChromeSpec(
        selectedIndex = selectedIndex,
        heightDp = AppChromeSizeTokens.CompactControlHeightDp,
        indicatorHeightDp = com.android.purebilibili.core.ui.roundMatchedLiquidIndicatorHeightDp(
            AppChromeSizeTokens.CompactControlHeightDp.toFloat()
        ),
        horizontalPaddingDp = SPACE_SEGMENTED_TAB_HORIZONTAL_PADDING_DP,
        itemWidthDp = resolveSpaceContributionTabItemWidthDp(tabs),
        scrollable = scrollable,
        liquidGlassEffectsEnabled = false,
        dragSelectionEnabled = false
    )
}

internal fun resolveSpaceSecondarySwitchChromeSpec(
    items: List<SpaceContributionTab>,
    selectedId: String,
    uiStyle: AppUiStyle = AppUiStyle.MIUIX,
): SpaceSegmentedTabChromeSpec {
    val itemWidthDp = resolveSpaceContributionTabItemWidthDpFromTitles(items.map { it.title })
    val horizontalPaddingDp = if (uiStyle == AppUiStyle.MATERIAL3) 0 else SPACE_SEGMENTED_TAB_HORIZONTAL_PADDING_DP
    return SpaceSegmentedTabChromeSpec(
        selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0),
        heightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp,
        indicatorHeightDp = AppChromeSizeTokens.BottomBarMatchedSegmentedIndicatorHeightDp,
        horizontalPaddingDp = horizontalPaddingDp,
        itemWidthDp = itemWidthDp,
        scrollable = items.size > 3,
        liquidGlassEffectsEnabled = true,
        dragSelectionEnabled = items.size > 1
    )
}

internal fun resolveSpaceContributionToolbarSpec(
    widthDp: Int,
    selectedSubTab: SpaceSubTab,
    tabCount: Int,
    selectedTitle: String = ""
): SpaceContributionToolbarSpec {
    val showVideoActions = selectedSubTab == SpaceSubTab.VIDEO ||
        selectedSubTab == SpaceSubTab.CHARGING_VIDEO
    val compactActions = widthDp < SPACE_CONTRIBUTION_TOOLBAR_COMPACT_WIDTH_DP || tabCount > 2
    val roomy = widthDp >= SPACE_CONTRIBUTION_TOOLBAR_ROOMY_WIDTH_DP && tabCount <= 2
    return SpaceContributionToolbarSpec(
        tabHeightDp = 40,
        tabIndicatorHeightDp = com.android.purebilibili.core.ui.roundMatchedLiquidIndicatorHeightDp(40f),
        collapsedTabWidthDp = resolveSpaceContributionCollapsedTabWidthDp(selectedTitle, widthDp),
        expandedTabRailHeightDp = 40,
        horizontalPaddingDp = 12,
        showVideoActions = showVideoActions,
        showTotalText = showVideoActions && roomy,
        showPlayAllText = showVideoActions && !compactActions,
        showSortText = showVideoActions && !compactActions,
        // Keep contribution categories expanded after pick so entries stay discoverable.
        collapseAfterTabSelection = false
    )
}

internal fun resolveSpaceContributionCollapsedTabWidthDp(title: String, widthDp: Int): Int {
    val normalizedTitle = title.trim()
    val minimumWidth = if (normalizedTitle.length <= 2) 88 else 104
    val maximumWidth = minOf(156, (widthDp * 0.45f).roundToInt().coerceAtLeast(minimumWidth))
    val containsWideText = normalizedTitle.any { it.code > 127 }
    val estimatedWidth = if (containsWideText) {
        estimateSpaceContributionTabTitleWidthDp(normalizedTitle)
    } else {
        minimumWidth
    }
    return estimatedWidth.coerceIn(minimumWidth, maximumWidth)
}

internal fun resolveSpaceVideoSortCompactLabel(order: VideoSortOrder): String {
    return when (order) {
        VideoSortOrder.PUBDATE -> "最新"
        VideoSortOrder.OLDEST_PUBDATE -> "最早"
        VideoSortOrder.CLICK -> "播放"
        VideoSortOrder.STOW -> "收藏"
    }
}

private fun shouldScrollSpaceContributionTabs(tabs: List<SpaceContributionTab>): Boolean {
    return tabs.size > 3
}

internal fun resolveSpaceContributionTabItemWidthDp(tabs: List<SpaceContributionTab>): Int {
    return resolveSpaceContributionTabItemWidthDpFromTitles(tabs.map { it.title })
}

private fun resolveSpaceContributionTabItemWidthDpFromTitles(titles: List<String>): Int {
    val widestTitle = titles.maxOfOrNull(::estimateSpaceContributionTabTitleWidthDp) ?: 0
    return widestTitle.coerceIn(
        SPACE_SCROLLABLE_CONTRIBUTION_ITEM_MIN_WIDTH_DP,
        SPACE_SCROLLABLE_CONTRIBUTION_ITEM_MAX_WIDTH_DP
    )
}

internal fun shouldScrollSpaceSecondarySwitch(
    itemCount: Int,
    itemWidthDp: Int,
    viewportWidthDp: Int,
    containerHorizontalPaddingDp: Int
): Boolean {
    val contentWidthDp = itemCount * itemWidthDp + containerHorizontalPaddingDp * 2
    return contentWidthDp > viewportWidthDp
}

internal fun resolveSpaceSecondarySwitchNonGlassMinTabWidthDp(): Int {
    return AppChromeSizeTokens.MinimumTouchTarget.value.toInt()
}

internal fun shouldScrollSpaceSecondarySwitchForNonGlass(itemCount: Int): Boolean {
    // A single item still needs the content-sized Miuix rail. Otherwise it
    // falls back to the equal-width segmented control and fills the viewport.
    return itemCount > 0
}

internal fun resolveSpaceSecondarySwitchAdaptiveItemWidthDp(
    preferredItemWidthDp: Int,
    itemCount: Int,
    viewportWidthDp: Int,
    containerHorizontalPaddingDp: Int
): Int {
    if (itemCount <= 0 || viewportWidthDp <= 0) return preferredItemWidthDp
    val visibleItemCount = minOf(itemCount, SPACE_SECONDARY_MIN_VISIBLE_ITEM_COUNT)
    val availableWidthDp =
        (viewportWidthDp - containerHorizontalPaddingDp * 2).coerceAtLeast(0)
    val widthForVisibleItemsDp = availableWidthDp / visibleItemCount
    return minOf(preferredItemWidthDp, widthForVisibleItemsDp)
        .coerceAtLeast(SPACE_SCROLLABLE_CONTRIBUTION_ITEM_EMERGENCY_MIN_WIDTH_DP)
}

internal fun resolveSpaceSecondarySwitchDragScrollDeltaPx(
    indicatorPosition: Float,
    itemWidthPx: Float,
    viewportWidthPx: Float,
    currentScrollPx: Float,
    containerHorizontalPaddingPx: Float,
    edgePaddingPx: Float
): Float {
    if (itemWidthPx <= 0f || viewportWidthPx <= 0f) return 0f
    val indicatorLeftPx =
        containerHorizontalPaddingPx + indicatorPosition * itemWidthPx - currentScrollPx
    val indicatorRightPx = indicatorLeftPx + itemWidthPx
    return when {
        indicatorLeftPx < edgePaddingPx -> indicatorLeftPx - edgePaddingPx
        indicatorRightPx > viewportWidthPx - edgePaddingPx ->
            indicatorRightPx - (viewportWidthPx - edgePaddingPx)
        else -> 0f
    }
}

private fun estimateSpaceContributionTabTitleWidthDp(title: String): Int {
    val textWidth = title.sumOf { char ->
        if (char.code in 0..127) {
            SPACE_SCROLLABLE_CONTRIBUTION_ASCII_CHAR_WIDTH_DP
        } else {
            SPACE_SCROLLABLE_CONTRIBUTION_CJK_CHAR_WIDTH_DP
        }
    }
    return textWidth + SPACE_SCROLLABLE_CONTRIBUTION_ITEM_TEXT_PADDING_DP
}
