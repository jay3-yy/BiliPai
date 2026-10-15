package com.android.purebilibili.feature.home.components

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppTopChromePolicy
import com.android.purebilibili.core.ui.AppTopTabPresentation

enum class TopTabMaterialMode {
    PLAIN,
    BLUR,
    LIQUID_GLASS
}

enum class TopTabClickAction {
    SELECT_TAB,
    SCROLL_TO_TOP
}

data class HomeTopPresetStyle(
    val presentation: AppTopTabPresentation,
    val indicatorStyle: TopTabIndicatorStyle,
    val search: HomeTopSearchStyle,
    val panel: HomeTopPanelStyle,
    val spacing: HomeTopSpacingStyle,
    val tabs: HomeTopTabsStyle,
    val actions: HomeTopActionStyle
) {
    val useUnifiedPanel: Boolean get() = panel.useUnified
    val showUnifiedPanelDivider: Boolean get() = panel.showDivider
    val searchBarHeight: Dp get() = search.barHeight
    val searchRevealDeadZone: Dp get() = search.revealDeadZone
    val searchRowHorizontalPadding: Dp get() = search.rowHorizontalPadding
    val searchPillHeight: Dp get() = search.pillHeight
    val searchContentHorizontalPadding: Dp get() = search.content.horizontalPadding
    val searchIconTextGap: Dp get() = search.content.iconTextGap
    val edgeControlGap: Dp get() = spacing.edgeControlGap
    val unifiedPanelHorizontalPadding: Dp get() = panel.horizontalPadding
    val unifiedPanelInnerPadding: Dp get() = panel.innerPadding
    val unifiedPanelCornerRadius: Dp get() = panel.cornerRadius
    val reservedContentBottomGap: Dp get() = panel.reservedContentBottomGap
    val embeddedTabHorizontalPadding: Dp get() = spacing.embeddedTabHorizontalPadding
    val tabHorizontalPaddingDocked: Dp get() = tabs.horizontalPadding.docked
    val tabHorizontalPaddingFloating: Dp get() = tabs.horizontalPadding.floating
    val searchToTabsSpacing: Dp get() = spacing.searchToTabs
    val tabsToContentSpacing: Dp get() = spacing.tabsToContent
    val searchCollapseExtraSpacing: Dp get() = spacing.searchCollapseExtra
    val continuousSlabOverlap: Dp get() = spacing.continuousSlabOverlap
    val tabRowHeightDocked: Dp get() = tabs.rowHeight.docked
    val tabRowHeightFloating: Dp get() = tabs.rowHeight.floating
    val md3VisualSpec: Md3TopTabVisualSpec get() = tabs.md3VisualSpec
    val actionButtonSizeDocked: Dp get() = actions.buttonSize.docked
    val actionButtonSizeFloating: Dp get() = actions.buttonSize.floating
    val actionButtonCornerDocked: Dp get() = actions.buttonCorner.docked
    val actionButtonCornerFloating: Dp get() = actions.buttonCorner.floating
    val actionIconSizeDocked: Dp get() = actions.iconSize.docked
    val actionIconSizeFloating: Dp get() = actions.iconSize.floating
}

data class HomeTopSearchStyle(
    val barHeight: Dp,
    val revealDeadZone: Dp,
    val rowHorizontalPadding: Dp,
    val pillHeight: Dp,
    val content: HomeTopSearchContentStyle
)

data class HomeTopSearchContentStyle(
    val horizontalPadding: Dp,
    val iconTextGap: Dp
)

data class HomeTopPanelStyle(
    val useUnified: Boolean,
    val showDivider: Boolean,
    val horizontalPadding: Dp,
    val innerPadding: Dp,
    val cornerRadius: Dp,
    val reservedContentBottomGap: Dp
)

data class HomeTopSpacingStyle(
    val edgeControlGap: Dp,
    val embeddedTabHorizontalPadding: Dp,
    /** Vertical gap between search row and top tab dock. */
    val searchToTabs: Dp,
    /**
     * Intentional air under the tab dock before the first feed row.
     * Kept near [searchToTabs] so search↔tabs and tabs↔cards feel balanced.
     */
    val tabsToContent: Dp,
    val searchCollapseExtra: Dp,
    val continuousSlabOverlap: Dp
)

data class HomeTopTabsStyle(
    val horizontalPadding: HomeTopDpPair,
    val rowHeight: HomeTopDpPair,
    val md3VisualSpec: Md3TopTabVisualSpec
)

data class HomeTopActionStyle(
    val buttonSize: HomeTopDpPair,
    val buttonCorner: HomeTopDpPair,
    val iconSize: HomeTopDpPair
)

data class HomeTopDpPair(
    val docked: Dp,
    val floating: Dp
)

/**
 * Edge controls retain the former dialog radii while their renderer is selected
 * through the shared top-chrome policy.
 */
internal fun resolveHomeTopEdgeButtonCornerRadius(
    chromePolicy: AppTopChromePolicy,
): Dp = when (chromePolicy.tabPresentation) {
    AppTopTabPresentation.MOVING_CAPSULE -> 0.dp
    AppTopTabPresentation.MATERIAL_UNDERLINE -> 12.6.dp
    AppTopTabPresentation.TONAL_CAPSULE -> 16.1.dp
}

internal fun resolveHomeTopPresetStyle(
    chromePolicy: AppTopChromePolicy,
    labelMode: Int
): HomeTopPresetStyle {
    val normalizedLabelMode = normalizeTopTabLabelMode(labelMode)
    val compactChrome = chromePolicy.compactChromeSpec
    return when (chromePolicy.tabPresentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> {
            HomeTopPresetStyle(
                presentation = chromePolicy.tabPresentation,
                indicatorStyle = TopTabIndicatorStyle.CAPSULE,
                search = HomeTopSearchStyle(
                    barHeight = compactChrome.primaryHeightDp.dp,
                    revealDeadZone = 8.dp,
                    rowHorizontalPadding = 14.dp,
                    pillHeight = compactChrome.primaryHeightDp.dp,
                    content = HomeTopSearchContentStyle(
                        horizontalPadding = compactChrome.inputHorizontalPaddingDp.dp,
                        iconTextGap = compactChrome.standardGapDp.dp
                    )
                ),
                panel = HomeTopPanelStyle(
                    useUnified = true,
                    showDivider = false,
                    horizontalPadding = 0.dp,
                    innerPadding = 6.dp,
                    cornerRadius = 32.dp,
                    reservedContentBottomGap = 5.dp
                ),
                spacing = HomeTopSpacingStyle(
                    edgeControlGap = 6.dp,
                    // Align the category strip with the avatar/search/settings row.
                    embeddedTabHorizontalPadding = 14.dp,
                    searchToTabs = 6.dp,
                    tabsToContent = 6.dp,
                    searchCollapseExtra = 0.dp,
                    continuousSlabOverlap = 0.dp
                ),
                tabs = HomeTopTabsStyle(
                    horizontalPadding = HomeTopDpPair(docked = 0.dp, floating = 2.dp),
                    rowHeight = HomeTopDpPair(
                        docked = resolveHomeTopDockShellHeight(isFloatingStyle = false),
                        floating = resolveHomeTopDockShellHeight(isFloatingStyle = true)
                    ),
                    md3VisualSpec = resolveMd3TopTabVisualSpec(
                        false,
                        AppTopTabPresentation.TONAL_CAPSULE,
                        normalizedLabelMode
                    )
                ),
                actions = HomeTopActionStyle(
                    buttonSize = HomeTopDpPair(
                        docked = resolveIosTopTabActionButtonSize(false),
                        floating = resolveIosTopTabActionButtonSize(true)
                    ),
                    buttonCorner = HomeTopDpPair(
                        docked = resolveIosTopTabActionButtonCorner(false),
                        floating = resolveIosTopTabActionButtonCorner(true)
                    ),
                    iconSize = HomeTopDpPair(
                        docked = resolveIosTopTabActionIconSize(false),
                        floating = resolveIosTopTabActionIconSize(true)
                    )
                )
            )
        }
        AppTopTabPresentation.TONAL_CAPSULE -> {
            HomeTopPresetStyle(
                // The home renderer routes this preset through its moving MD3 capsule,
                // which can reuse the same liquid-glass indicator as the bottom bar.
                presentation = AppTopTabPresentation.MATERIAL_UNDERLINE,
                indicatorStyle = TopTabIndicatorStyle.MATERIAL,
                search = HomeTopSearchStyle(
                    barHeight = compactChrome.primaryHeightDp.dp,
                    revealDeadZone = 0.dp,
                    rowHorizontalPadding = 14.dp,
                    pillHeight = compactChrome.primaryHeightDp.dp,
                    content = HomeTopSearchContentStyle(
                        horizontalPadding = compactChrome.inputHorizontalPaddingDp.dp,
                        iconTextGap = compactChrome.standardGapDp.dp
                    )
                ),
                panel = HomeTopPanelStyle(
                    useUnified = true,
                    showDivider = false,
                    horizontalPadding = 0.dp,
                    innerPadding = 9.dp,
                    cornerRadius = 18.dp,
                    reservedContentBottomGap = 12.dp
                ),
                spacing = HomeTopSpacingStyle(
                    edgeControlGap = 7.dp,
                    // Align the category strip with the avatar/search/settings row.
                    embeddedTabHorizontalPadding = 14.dp,
                    searchToTabs = 6.dp,
                    tabsToContent = 6.dp,
                    searchCollapseExtra = 5.dp,
                    continuousSlabOverlap = 20.dp
                ),
                tabs = HomeTopTabsStyle(
                    horizontalPadding = HomeTopDpPair(docked = 0.dp, floating = 2.dp),
                    rowHeight = HomeTopDpPair(
                        docked = resolveHomeTopDockShellHeight(isFloatingStyle = false),
                        floating = resolveHomeTopDockShellHeight(isFloatingStyle = true)
                    ),
                    md3VisualSpec = resolveMd3TopTabVisualSpec(
                        false,
                        AppTopTabPresentation.TONAL_CAPSULE,
                        normalizedLabelMode
                    )
                ),
                actions = HomeTopActionStyle(
                    buttonSize = HomeTopDpPair(
                        docked = resolveMd3TopTabActionButtonSize(false, AppTopTabPresentation.TONAL_CAPSULE),
                        floating = resolveMd3TopTabActionButtonSize(true, AppTopTabPresentation.TONAL_CAPSULE)
                    ),
                    buttonCorner = HomeTopDpPair(
                        docked = resolveMd3TopTabActionButtonCorner(false, AppTopTabPresentation.TONAL_CAPSULE),
                        floating = resolveMd3TopTabActionButtonCorner(true, AppTopTabPresentation.TONAL_CAPSULE)
                    ),
                    iconSize = HomeTopDpPair(
                        docked = resolveMd3TopTabActionIconSize(false, AppTopTabPresentation.TONAL_CAPSULE),
                        floating = resolveMd3TopTabActionIconSize(true, AppTopTabPresentation.TONAL_CAPSULE)
                    )
                )
            )
        }
        AppTopTabPresentation.MATERIAL_UNDERLINE -> {
            HomeTopPresetStyle(
                presentation = chromePolicy.tabPresentation,
                indicatorStyle = TopTabIndicatorStyle.MATERIAL,
                search = HomeTopSearchStyle(
                    barHeight = compactChrome.primaryHeightDp.dp,
                    revealDeadZone = 0.dp,
                    rowHorizontalPadding = 16.dp,
                    pillHeight = compactChrome.primaryHeightDp.dp,
                    content = HomeTopSearchContentStyle(
                        horizontalPadding = compactChrome.inputHorizontalPaddingDp.dp,
                        iconTextGap = compactChrome.standardGapDp.dp
                    )
                ),
                panel = HomeTopPanelStyle(
                    useUnified = true,
                    showDivider = true,
                    horizontalPadding = 0.dp,
                    innerPadding = 10.dp,
                    cornerRadius = 0.dp,
                    reservedContentBottomGap = 0.dp
                ),
                spacing = HomeTopSpacingStyle(
                    edgeControlGap = 8.dp,
                    // Align the category strip with the avatar/search/settings row.
                    embeddedTabHorizontalPadding = 16.dp,
                    searchToTabs = 6.dp,
                    tabsToContent = 6.dp,
                    searchCollapseExtra = 5.dp,
                    continuousSlabOverlap = 0.dp
                ),
                tabs = HomeTopTabsStyle(
                    horizontalPadding = HomeTopDpPair(docked = 0.dp, floating = 2.dp),
                    // Align with resolveMd3TopTabVisualSpec used by the tab row.
                    rowHeight = HomeTopDpPair(
                        docked = resolveHomeTopDockShellHeight(isFloatingStyle = false),
                        floating = resolveHomeTopDockShellHeight(isFloatingStyle = true)
                    ),
                    md3VisualSpec = resolveMd3TopTabVisualSpec(
                        false,
                        AppTopTabPresentation.MATERIAL_UNDERLINE,
                        normalizedLabelMode
                    )
                ),
                actions = HomeTopActionStyle(
                    buttonSize = HomeTopDpPair(
                        docked = resolveMd3TopTabActionButtonSize(false, AppTopTabPresentation.MATERIAL_UNDERLINE),
                        floating = resolveMd3TopTabActionButtonSize(true, AppTopTabPresentation.MATERIAL_UNDERLINE)
                    ),
                    buttonCorner = HomeTopDpPair(
                        docked = resolveMd3TopTabActionButtonCorner(false, AppTopTabPresentation.MATERIAL_UNDERLINE),
                        floating = resolveMd3TopTabActionButtonCorner(true, AppTopTabPresentation.MATERIAL_UNDERLINE)
                    ),
                    iconSize = HomeTopDpPair(
                        docked = resolveMd3TopTabActionIconSize(false, AppTopTabPresentation.MATERIAL_UNDERLINE),
                        floating = resolveMd3TopTabActionIconSize(true, AppTopTabPresentation.MATERIAL_UNDERLINE)
                    )
                )
            )
        }
    }
}

internal fun resolveHomeTopTabMaterialMode(headerBlurEnabled: Boolean): TopTabMaterialMode {
    return if (headerBlurEnabled) TopTabMaterialMode.BLUR else TopTabMaterialMode.PLAIN
}

internal fun resolveTopTabClickAction(
    index: Int,
    selectedIndex: Int
): TopTabClickAction {
    return if (index == selectedIndex) {
        TopTabClickAction.SCROLL_TO_TOP
    } else {
        TopTabClickAction.SELECT_TAB
    }
}

internal fun resolveTopTabRenderMaterialMode(
    liquidGlassEnabled: Boolean,
    hasHazeState: Boolean
): TopTabMaterialMode {
    return when {
        liquidGlassEnabled -> TopTabMaterialMode.LIQUID_GLASS
        hasHazeState -> TopTabMaterialMode.BLUR
        else -> TopTabMaterialMode.PLAIN
    }
}

enum class TopTabIndicatorStyle {
    CAPSULE,
    MATERIAL
}

// Compact top docks scale the 56/64 rest fill from the 64dp home bottom bar.
internal const val CompactTopTabIndicatorHeightDp = 30f
internal const val CompactTopTabIndicatorCornerDp = 9f

data class TopTabVisualTuning(
    val nonFloatingIndicatorHeightDp: Float = CompactTopTabIndicatorHeightDp,
    val nonFloatingIndicatorCornerDp: Float = CompactTopTabIndicatorCornerDp,
    val nonFloatingIndicatorWidthRatio: Float = 0.72f,
    val nonFloatingIndicatorMinWidthDp: Float = 52f,
    val nonFloatingIndicatorHorizontalInsetDp: Float = 18f,
    val floatingIndicatorWidthMultiplier: Float = 1.18f,
    val floatingIndicatorMinWidthDp: Float = 88f,
    val floatingIndicatorMaxWidthDp: Float = 120f,
    val floatingIndicatorMaxWidthToItemRatio: Float = 1.18f,
    val floatingIndicatorHeightDp: Float = CompactTopTabIndicatorHeightDp,
    val tabTextSizeSp: Float = 15f,
    val tabTextLineHeightSp: Float = 20f,
    val tabContentMinHeightDp: Float = 30f,
    val tabIconWithTextSizeDp: Float = 18f,
    val tabIconOnlySizeDp: Float = 18f,
    val tabIconTextSpacingDp: Float = 6f
)

data class TopTabVisualState(
    val floating: Boolean,
    val materialMode: TopTabMaterialMode
)

data class Md3TopTabVisualSpec(
    val rowHeight: Dp,
    val selectedCapsuleHeight: Dp,
    val selectedCapsuleCornerRadius: Dp,
    val selectedCapsuleTonalElevation: Dp,
    val selectedCapsuleShadowElevation: Dp,
    val itemHorizontalPadding: Dp,
    val iconSize: Dp,
    val labelTextSize: TextUnit,
    val labelLineHeight: TextUnit,
    val iconLabelSpacing: Dp
)

fun resolveTopTabVisualTuning(): TopTabVisualTuning = TopTabVisualTuning()

fun resolveTopTabVisualTuning(presentation: AppTopTabPresentation): TopTabVisualTuning {
    return when (presentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> TopTabVisualTuning(
            nonFloatingIndicatorHeightDp = CompactTopTabIndicatorHeightDp,
            nonFloatingIndicatorCornerDp = CompactTopTabIndicatorCornerDp,
            nonFloatingIndicatorWidthRatio = 1.18f,
            nonFloatingIndicatorMinWidthDp = 84f,
            nonFloatingIndicatorHorizontalInsetDp = 0f,
            floatingIndicatorWidthMultiplier = 1.18f,
            floatingIndicatorMinWidthDp = 88f,
            floatingIndicatorMaxWidthDp = 120f,
            floatingIndicatorMaxWidthToItemRatio = 1.18f,
            floatingIndicatorHeightDp = CompactTopTabIndicatorHeightDp,
            tabTextSizeSp = 15f,
            tabTextLineHeightSp = 20f,
            tabContentMinHeightDp = 30f,
            tabIconWithTextSizeDp = 18f,
            tabIconOnlySizeDp = 18f,
            tabIconTextSpacingDp = 6f
        )
        AppTopTabPresentation.MATERIAL_UNDERLINE,
        AppTopTabPresentation.TONAL_CAPSULE -> resolveTopTabVisualTuning()
    }
}

internal fun resolveTopTabContentScale(
    selectionFraction: Float,
    showIcon: Boolean,
    showText: Boolean,
    presentation: AppTopTabPresentation
): Float {
    if (showIcon && showText) return 1f

    val clampedFraction = selectionFraction.coerceIn(0f, 1f)
    val maxScale = when (presentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> 1.03f
        AppTopTabPresentation.MATERIAL_UNDERLINE,
        AppTopTabPresentation.TONAL_CAPSULE -> 1.04f
    }
    return 1f + ((maxScale - 1f) * clampedFraction)
}

internal fun resolveMd3TopTabVisualSpec(
    isFloatingStyle: Boolean,
    presentation: AppTopTabPresentation = AppTopTabPresentation.TONAL_CAPSULE,
    labelMode: Int = 2
): Md3TopTabVisualSpec {
    val normalizedLabelMode = normalizeTopTabLabelMode(labelMode)
    val showIconAndText = normalizedLabelMode == 0
    if (presentation == AppTopTabPresentation.TONAL_CAPSULE) {
        return if (isFloatingStyle) {
            Md3TopTabVisualSpec(
                rowHeight = if (showIconAndText) 52.dp else 36.dp,
                selectedCapsuleHeight = 30.dp,
                selectedCapsuleCornerRadius = 9.dp,
                selectedCapsuleTonalElevation = 0.dp,
                selectedCapsuleShadowElevation = 0.dp,
                itemHorizontalPadding = 10.dp,
                iconSize = 18.dp,
                labelTextSize = 15.sp,
                labelLineHeight = 20.sp,
                iconLabelSpacing = if (showIconAndText) 6.dp else 0.dp
            )
        } else {
            Md3TopTabVisualSpec(
                rowHeight = if (showIconAndText) 48.dp else 32.dp,
                selectedCapsuleHeight = 30.dp,
                selectedCapsuleCornerRadius = 9.dp,
                selectedCapsuleTonalElevation = 0.dp,
                selectedCapsuleShadowElevation = 0.dp,
                itemHorizontalPadding = 10.dp,
                iconSize = 18.dp,
                labelTextSize = 15.sp,
                labelLineHeight = 20.sp,
                iconLabelSpacing = if (showIconAndText) 6.dp else 0.dp
            )
        }
    }

    // MATERIAL_UNDERLINE on the home dock shares the compact liquid track with TONAL/Miuix.
    // Row heights track the shrunken top dock shell (52/48) so labels never clip.
    return if (isFloatingStyle) {
        Md3TopTabVisualSpec(
            rowHeight = if (showIconAndText) 52.dp else 36.dp,
            selectedCapsuleHeight = CompactTopTabIndicatorHeightDp.dp,
            selectedCapsuleCornerRadius = CompactTopTabIndicatorCornerDp.dp,
            selectedCapsuleTonalElevation = 0.dp,
            selectedCapsuleShadowElevation = 0.dp,
            itemHorizontalPadding = 10.dp,
            iconSize = 18.dp,
            labelTextSize = 15.sp,
            labelLineHeight = 20.sp,
            iconLabelSpacing = if (showIconAndText) 6.dp else 0.dp
        )
    } else {
        Md3TopTabVisualSpec(
            rowHeight = if (showIconAndText) 48.dp else 32.dp,
            selectedCapsuleHeight = CompactTopTabIndicatorHeightDp.dp,
            selectedCapsuleCornerRadius = CompactTopTabIndicatorCornerDp.dp,
            selectedCapsuleTonalElevation = 0.dp,
            selectedCapsuleShadowElevation = 0.dp,
            itemHorizontalPadding = 10.dp,
            iconSize = 18.dp,
            labelTextSize = 15.sp,
            labelLineHeight = 20.sp,
            iconLabelSpacing = if (showIconAndText) 6.dp else 0.dp
        )
    }
}

internal fun resolveMd3TopTabSelectedContainerColor(
    colorScheme: ColorScheme,
    presentation: AppTopTabPresentation = AppTopTabPresentation.MATERIAL_UNDERLINE,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3
): androidx.compose.ui.graphics.Color = when {
    uiStyle == AppUiStyle.MIUIX -> colorScheme.primary
    presentation == AppTopTabPresentation.TONAL_CAPSULE -> colorScheme.secondaryContainer
    else -> colorScheme.primary
}

internal fun resolveMd3TopTabSelectedIconColor(
    colorScheme: ColorScheme,
    presentation: AppTopTabPresentation = AppTopTabPresentation.MATERIAL_UNDERLINE,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3
): androidx.compose.ui.graphics.Color = when {
    uiStyle == AppUiStyle.MIUIX -> colorScheme.primary
    presentation == AppTopTabPresentation.TONAL_CAPSULE -> colorScheme.onSecondaryContainer
    else -> colorScheme.primary
}

internal fun resolveMd3TopTabSelectedLabelColor(
    colorScheme: ColorScheme,
    presentation: AppTopTabPresentation = AppTopTabPresentation.MATERIAL_UNDERLINE,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3
): androidx.compose.ui.graphics.Color = when {
    uiStyle == AppUiStyle.MIUIX -> colorScheme.primary
    presentation == AppTopTabPresentation.TONAL_CAPSULE -> colorScheme.onSecondaryContainer
    else -> colorScheme.primary
}

internal fun resolveMd3TopTabUnselectedIconColor(
    colorScheme: ColorScheme
): androidx.compose.ui.graphics.Color = colorScheme.onSurfaceVariant

internal fun resolveMd3TopTabUnselectedLabelColor(
    colorScheme: ColorScheme
): androidx.compose.ui.graphics.Color = colorScheme.onSurfaceVariant

internal fun resolveMd3TopTabIconTint(
    selectionFraction: Float,
    colorScheme: ColorScheme,
    presentation: AppTopTabPresentation = AppTopTabPresentation.MATERIAL_UNDERLINE,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3
) = androidx.compose.ui.graphics.lerp(
    resolveMd3TopTabUnselectedIconColor(colorScheme),
    resolveMd3TopTabSelectedIconColor(colorScheme, presentation, uiStyle),
    selectionFraction.coerceIn(0f, 1f)
)

internal fun resolveMd3TopTabLabelTint(
    selectionFraction: Float,
    colorScheme: ColorScheme,
    presentation: AppTopTabPresentation = AppTopTabPresentation.MATERIAL_UNDERLINE,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3
) = androidx.compose.ui.graphics.lerp(
    resolveMd3TopTabUnselectedLabelColor(colorScheme),
    resolveMd3TopTabSelectedLabelColor(colorScheme, presentation, uiStyle),
    selectionFraction.coerceIn(0f, 1f)
)

internal fun resolveTopTabIndicatorStyle(presentation: AppTopTabPresentation): TopTabIndicatorStyle {
    return if (presentation == AppTopTabPresentation.MOVING_CAPSULE) {
        TopTabIndicatorStyle.CAPSULE
    } else {
        TopTabIndicatorStyle.MATERIAL
    }
}

internal fun shouldUseMd3TopTabMaterialIndicator(
    presentation: AppTopTabPresentation,
    liquidGlassEnabled: Boolean
): Boolean {
    return resolveTopTabIndicatorStyle(presentation) == TopTabIndicatorStyle.MATERIAL
}

fun resolveTopTabLabelTextSizeSp(labelMode: Int): Float {
    val tuning = resolveTopTabVisualTuning()
    return when (normalizeTopTabLabelMode(labelMode)) {
        0 -> resolveMd3TopTabVisualSpec(isFloatingStyle = false, labelMode = labelMode).labelTextSize.value
        2 -> tuning.tabTextSizeSp
        else -> tuning.tabTextSizeSp
    }
}

fun resolveTopTabLabelLineHeightSp(labelMode: Int): Float {
    return when (normalizeTopTabLabelMode(labelMode)) {
        0 -> resolveMd3TopTabVisualSpec(isFloatingStyle = false, labelMode = labelMode).labelLineHeight.value
        else -> {
            val tuning = resolveTopTabVisualTuning()
            val textSize = resolveTopTabLabelTextSizeSp(labelMode)
            maxOf(tuning.tabTextLineHeightSp, textSize)
        }
    }
}

fun resolveTopTabContentMinHeightDp(labelMode: Int = 2): Float {
    return when (normalizeTopTabLabelMode(labelMode)) {
        0 -> 30f
        else -> resolveTopTabVisualTuning().tabContentMinHeightDp
    }
}

fun resolveTopTabContentVerticalPaddingDp(labelMode: Int): Float {
    return when (normalizeTopTabLabelMode(labelMode)) {
        0 -> 5f
        else -> 5f
    }
}

fun resolveTopTabIconSizeDp(labelMode: Int): Float {
    val tuning = resolveTopTabVisualTuning()
    return when (normalizeTopTabLabelMode(labelMode)) {
        0 -> tuning.tabIconWithTextSizeDp
        1 -> tuning.tabIconOnlySizeDp
        else -> 0f
    }
}

/** Native top-dock standalone icons use a standard 24dp glyph; glass keeps its existing dimensions. */
internal fun resolveHomeTopDockIconSizeDp(labelMode: Int, liquidGlassEnabled: Boolean): Float {
    val baseSize = resolveTopTabIconSizeDp(labelMode)
    return if (!liquidGlassEnabled && normalizeTopTabLabelMode(labelMode) == 1) {
        maxOf(baseSize, 24f)
    } else {
        baseSize
    }
}

fun resolveTopTabIconTextSpacingDp(labelMode: Int): Float {
    return if (normalizeTopTabLabelMode(labelMode) == 0) {
        resolveTopTabVisualTuning().tabIconTextSpacingDp
    } else {
        0f
    }
}

fun resolveTopTabStyle(
    isBottomBarFloating: Boolean,
    isBottomBarBlurEnabled: Boolean,
    isLiquidGlassEnabled: Boolean
): TopTabVisualState {
    val materialMode = when {
        isLiquidGlassEnabled -> TopTabMaterialMode.LIQUID_GLASS
        isBottomBarBlurEnabled -> TopTabMaterialMode.BLUR
        else -> TopTabMaterialMode.PLAIN
    }

    return TopTabVisualState(
        floating = isBottomBarFloating,
        materialMode = materialMode
    )
}

internal fun resolveEffectiveHomeHeaderTabMaterialMode(
    materialMode: TopTabMaterialMode,
    interactionBudget: HomeInteractionMotionBudget
): TopTabMaterialMode {
    return materialMode
}

internal fun resolveEffectiveTopTabLiquidGlassEnabled(
    isLiquidGlassEnabled: Boolean,
    interactionBudget: HomeInteractionMotionBudget
): Boolean {
    return isLiquidGlassEnabled
}

@Suppress("UNUSED_PARAMETER")
internal fun shouldDrawHomeTopTabOuterChromeSurface(
    presentation: AppTopTabPresentation,
    materialMode: TopTabMaterialMode
): Boolean {
    // The full-width track is a liquid-glass affordance. In blur/plain modes it turns
    // into a high-contrast white pill that competes with the search field, so let the
    // tab row sit directly on the continuous top backdrop instead.
    return materialMode == TopTabMaterialMode.LIQUID_GLASS
}
