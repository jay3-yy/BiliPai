package com.android.purebilibili.feature.list

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.CommonListHeaderCollapseMode
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.resolveHomeHeaderBlurEnabled
import com.android.purebilibili.core.ui.AppTopChromePolicy
import com.android.purebilibili.core.ui.AppTopTabPresentation

internal data class CommonListVideoCardAppearance(
    val glassEnabled: Boolean,
    val blurEnabled: Boolean,
    val showCoverGlassBadges: Boolean,
    val showInfoGlassBadges: Boolean
)

internal fun resolveCommonListSingleColumnMaxWidth(): Dp = 840.dp

internal fun resolveCommonListGridMinColumnWidth(isExpandedScreen: Boolean): Dp =
    if (isExpandedScreen) 240.dp else 170.dp

internal fun resolveFavoriteSubscribedFolderPreviewWidth(): Dp = 112.dp

internal data class CommonListFavoriteHeaderLayout(
    val searchBarHeightDp: Int,
    val searchBarHorizontalPaddingDp: Int,
    val searchBarVerticalPaddingDp: Int,
    val browseToggleHeightDp: Int,
    val browseToggleIndicatorHeightDp: Int,
    val browseToggleLabelFontSizeSp: Int,
    val browseToggleHorizontalPaddingDp: Int,
    val browseToggleTopPaddingDp: Int,
    val folderChipMinHeightDp: Int,
    val folderChipHorizontalPaddingDp: Int,
    val folderChipRowHorizontalPaddingDp: Int,
    val folderChipRowTopPaddingDp: Int,
    val folderChipSpacingDp: Int,
    val headerBottomPaddingDp: Int,
    val headerBackgroundAlphaMultiplier: Float
)

internal fun resolveCommonListHeaderBlurEnabled(
    homeSettings: HomeSettings,
): Boolean {
    return resolveHomeHeaderBlurEnabled(
        mode = homeSettings.headerBlurMode,
    )
}

internal fun shouldUseCommonListHeaderLocalBlur(
    headerBlurEnabled: Boolean,
    globalWallpaperVisible: Boolean
): Boolean = headerBlurEnabled && !globalWallpaperVisible

internal fun shouldUseFloatingCommonListHeaderChrome(
    isHistoryPage: Boolean,
    isFavoritePage: Boolean = false,
    globalLiquidGlassReuseEnabled: Boolean,
): Boolean = (isHistoryPage || isFavoritePage) && globalLiquidGlassReuseEnabled

/**
 * 通用列表页折叠位移上限：独立折叠开关开启后，标题/搜索/标签 Dock
 * 收起至状态栏安全区下方；「始终显示」则不产生折叠位移。
 */
internal fun resolveCommonListHeaderMaxCollapsePxForMode(
    collapseMode: CommonListHeaderCollapseMode,
    fixedTopBarHeightPx: Int,
    statusBarHeightPx: Float,
): Float = if (collapseMode == CommonListHeaderCollapseMode.ALWAYS_VISIBLE) {
    0f
} else {
    (fixedTopBarHeightPx.toFloat() - statusBarHeightPx).coerceAtLeast(0f)
}

internal fun resolveCommonListViewportTopPadding(headerHeight: Dp): Dp {
    return headerHeight.coerceAtLeast(0.dp)
}

/**
 * 首页式折叠只移走搜索/标题区，保留状态栏安全区与末尾标签 Dock。
 */
internal fun resolveCommonListHeaderMaxCollapsePx(
    headerHeightPx: Int,
    pinnedDockHeightPx: Int,
    topInsetPx: Float,
    retainPinnedDock: Boolean,
): Float {
    if (!retainPinnedDock) return headerHeightPx.coerceAtLeast(0).toFloat()
    return (headerHeightPx - pinnedDockHeightPx - topInsetPx).coerceAtLeast(0f)
}

/** Fully removes the history title while its following docks stop below the status bar. */
internal fun resolveHistoryTitleOffsetPx(
    headerOffsetPx: Float,
    maxCollapsePx: Float,
    titleHeightPx: Int,
): Int {
    if (titleHeightPx <= 0 || maxCollapsePx <= 0f) return 0
    val collapseFraction = (-headerOffsetPx / maxCollapsePx).coerceIn(0f, 1f)
    return (-titleHeightPx * collapseFraction).toInt()
}

internal fun resolveCommonListHeaderOffsetPx(
    currentOffsetPx: Float,
    scrollDeltaYPx: Float,
    maxCollapsePx: Float,
    isAtTop: Boolean,
    mode: CommonListHeaderCollapseMode
): Float {
    if (mode == CommonListHeaderCollapseMode.ALWAYS_VISIBLE || maxCollapsePx <= 0f) return 0f
    if (isAtTop && scrollDeltaYPx >= 0f) return 0f
    if (mode == CommonListHeaderCollapseMode.SHOW_AT_TOP_ONLY && scrollDeltaYPx > 0f) {
        return currentOffsetPx.coerceIn(-maxCollapsePx, 0f)
    }
    return (currentOffsetPx + scrollDeltaYPx).coerceIn(-maxCollapsePx, 0f)
}

internal fun resolveCommonListHeaderOffsetAfterContentScroll(
    currentOffsetPx: Float,
    contentConsumedDeltaYPx: Float,
    maxCollapsePx: Float,
    isAtTop: Boolean,
    mode: CommonListHeaderCollapseMode
): Float = resolveCommonListHeaderOffsetPx(
    currentOffsetPx = currentOffsetPx,
    scrollDeltaYPx = contentConsumedDeltaYPx,
    maxCollapsePx = maxCollapsePx,
    isAtTop = isAtTop,
    mode = mode
)

internal fun resolveCommonListHeaderOffsetForSettledContent(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    maxCollapsePx: Float,
    mode: CommonListHeaderCollapseMode
): Float {
    if (mode == CommonListHeaderCollapseMode.ALWAYS_VISIBLE || maxCollapsePx <= 0f) return 0f
    return if (firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) {
        0f
    } else {
        -maxCollapsePx
    }
}

internal fun resolveCommonListVideoCardAppearance(
    homeSettings: HomeSettings,
    liquidGlassEnabled: Boolean,
): CommonListVideoCardAppearance {
    val headerBlurEnabled = resolveCommonListHeaderBlurEnabled(
        homeSettings = homeSettings,
    )
    return CommonListVideoCardAppearance(
        glassEnabled = liquidGlassEnabled,
        blurEnabled = headerBlurEnabled || homeSettings.isBottomBarBlurEnabled,
        showCoverGlassBadges = false,
        showInfoGlassBadges = false
    )
}

internal fun resolveCommonListFavoriteHeaderLayout(
    topChromePolicy: AppTopChromePolicy,
): CommonListFavoriteHeaderLayout {
    val compactChrome = topChromePolicy.compactChromeSpec
    return when (topChromePolicy.tabPresentation) {
        AppTopTabPresentation.TONAL_CAPSULE -> {
            CommonListFavoriteHeaderLayout(
                searchBarHeightDp = compactChrome.primaryHeightDp,
                searchBarHorizontalPaddingDp = 16,
                searchBarVerticalPaddingDp = 6,
                browseToggleHeightDp = compactChrome.primaryHeightDp,
                browseToggleIndicatorHeightDp = 30,
                browseToggleLabelFontSizeSp = 14,
                browseToggleHorizontalPaddingDp = 16,
                browseToggleTopPaddingDp = 2,
                folderChipMinHeightDp = compactChrome.chipHeightDp,
                folderChipHorizontalPaddingDp = 12,
                folderChipRowHorizontalPaddingDp = 16,
                folderChipRowTopPaddingDp = 6,
                folderChipSpacingDp = 8,
                headerBottomPaddingDp = 6,
                headerBackgroundAlphaMultiplier = 0.84f
            )
        }
        AppTopTabPresentation.MATERIAL_UNDERLINE -> {
            CommonListFavoriteHeaderLayout(
                searchBarHeightDp = compactChrome.primaryHeightDp,
                searchBarHorizontalPaddingDp = 16,
                searchBarVerticalPaddingDp = 6,
                browseToggleHeightDp = compactChrome.primaryHeightDp,
                browseToggleIndicatorHeightDp = 30,
                browseToggleLabelFontSizeSp = 14,
                browseToggleHorizontalPaddingDp = 16,
                browseToggleTopPaddingDp = 2,
                folderChipMinHeightDp = compactChrome.chipHeightDp,
                folderChipHorizontalPaddingDp = 12,
                folderChipRowHorizontalPaddingDp = 16,
                folderChipRowTopPaddingDp = 6,
                folderChipSpacingDp = 8,
                headerBottomPaddingDp = 6,
                headerBackgroundAlphaMultiplier = 0.86f
            )
        }
        AppTopTabPresentation.MOVING_CAPSULE -> {
            CommonListFavoriteHeaderLayout(
                searchBarHeightDp = compactChrome.primaryHeightDp,
                searchBarHorizontalPaddingDp = 16,
                searchBarVerticalPaddingDp = 6,
                browseToggleHeightDp = compactChrome.primaryHeightDp,
                browseToggleIndicatorHeightDp = 30,
                browseToggleLabelFontSizeSp = 14,
                browseToggleHorizontalPaddingDp = 16,
                browseToggleTopPaddingDp = 2,
                folderChipMinHeightDp = compactChrome.compactChipHeightDp,
                folderChipHorizontalPaddingDp = 11,
                folderChipRowHorizontalPaddingDp = 12,
                folderChipRowTopPaddingDp = 6,
                folderChipSpacingDp = 8,
                headerBottomPaddingDp = 4,
                headerBackgroundAlphaMultiplier = 0.82f
            )
        }
    }
}
