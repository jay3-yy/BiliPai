// 文件路径: feature/home/components/HomeHeader.kt
package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.rememberAppElasticPressState
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppSearchEntry

import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppTopChromePolicy
import com.android.purebilibili.core.ui.resolveAppSearchGlyphOpticalScale
import com.android.purebilibili.core.ui.blur.TopSolidProgressiveFadeOverlay
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import com.android.purebilibili.core.ui.resolveAppInboxIcon
import com.android.purebilibili.core.ui.resolveAppSearchIcon
import com.android.purebilibili.core.ui.resolveAppSettingsIcon
import com.android.purebilibili.core.ui.rememberAppSemanticVisualPolicy
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy
import com.android.purebilibili.core.ui.rememberContentCardSurfaceSpec

import com.android.purebilibili.core.ui.OpticalContrastPalette
import com.android.purebilibili.feature.home.HomeVisualPalette
import com.android.purebilibili.feature.home.resolveHomeTopSearchRowMetrics

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance  //  状态栏亮度计算
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.components.AppExactTouchTargets
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.feature.home.UserState
import com.android.purebilibili.core.store.LiquidGlassStyle
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.shouldAllowDirectHazeLiquidGlassFallback
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.core.ui.blur.resolveUnifiedBlurredEdgeTreatment
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.BlurIntensity
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.store.HomeHeaderBlurMode
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.HomeTopLayoutOrder

import com.android.purebilibili.core.store.HomeTopRightAction
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset

import com.android.purebilibili.feature.home.resolveHomeTopCategories
import com.android.purebilibili.feature.home.resolveHomeTopCollapsedHandleHeight
import com.android.purebilibili.feature.home.resolveHomeTopTabPresentationHeight
import com.android.purebilibili.feature.home.HomeGlassResolvedColors
import com.android.purebilibili.feature.home.rememberHomeGlassChromeColors
import com.android.purebilibili.feature.home.rememberHomeGlassPillColors
import com.android.purebilibili.feature.home.resolveHomeGlassChromeStyle
import com.android.purebilibili.feature.home.resolveHomeGlassPillStyle
import com.android.purebilibili.feature.home.components.liquid.vibrancy
import com.android.purebilibili.core.store.resolveGlobalLiquidGlassReuseEnabled
import com.android.purebilibili.core.store.resolveHomeHeaderBlurEnabled
import com.android.purebilibili.navigation.resolveAppNavigationAppearance
import java.io.File
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.blur.drawBackdrop as miuixDrawBackdrop
import top.yukonga.miuix.kmp.blur.blur as miuixBlur
import top.yukonga.miuix.kmp.blur.ProgressiveBlur

private const val HOME_HEADER_LIQUID_GLASS_ALPHA = 0.10f

internal data class HomeTopActionIcons(
    val search: androidx.compose.ui.graphics.vector.ImageVector,
    val settings: androidx.compose.ui.graphics.vector.ImageVector,
    val inbox: androidx.compose.ui.graphics.vector.ImageVector,
)

internal fun resolveHomeTopActionIcons(iconFamily: AppSemanticIconFamily): HomeTopActionIcons =
    when (iconFamily) {
        AppSemanticIconFamily.MATERIAL -> HomeTopActionIcons(
            search = resolveAppSearchIcon(),
            settings = resolveAppSettingsIcon(),
            inbox = resolveAppInboxIcon(),
        )
        AppSemanticIconFamily.MIUIX -> HomeTopActionIcons(
            search = MiuixIcons.Search,
            settings = MiuixIcons.Settings,
            inbox = MiuixIcons.Messages,
        )
    }

internal data class HomeTopChromeMotionPolicy(
    val isScrolling: Boolean,
    val isTransitionRunning: Boolean
)

internal data class HomeTopLinkedBottomBarAppearance(
    val isFloating: Boolean,
    val blurEnabled: Boolean,
    val liquidGlassEnabled: Boolean
)

internal fun shouldExportHomeTopActionIconThroughLiquidGlass(
    usesMatchedTopControls: Boolean,
    renderMode: HomeTopChromeRenderMode,
    hasBackdrop: Boolean,
): Boolean = usesMatchedTopControls &&
    hasBackdrop &&
    (
        renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
            renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
    )

internal fun resolveHomeSkinSearchSurfaceColor(
    defaultSurfaceColor: Color,
    skinTint: Color?,
    useUnifiedTopPanel: Boolean
): Color {
    if (skinTint == null || useUnifiedTopPanel) return defaultSurfaceColor
    val targetAlpha = defaultSurfaceColor.alpha.coerceAtLeast(0.72f)
    return androidx.compose.ui.graphics.lerp(
        start = defaultSurfaceColor.copy(alpha = targetAlpha),
        stop = skinTint.copy(alpha = targetAlpha),
        fraction = 0.36f
    )
}

internal fun resolveHomeSkinTopTabContentColor(
    topAtmosphereTint: Color,
    hasTopAtmosphereImage: Boolean = false,
    darkTheme: Boolean = false
): Color {
    if (hasTopAtmosphereImage && darkTheme) {
        return OpticalContrastPalette.Highlight.copy(alpha = 0.98f)
    }
    return if (topAtmosphereTint.luminance() < 0.72f) {
        OpticalContrastPalette.Highlight.copy(alpha = 0.98f)
    } else {
        HomeVisualPalette.IosDarkChrome.copy(alpha = 0.96f)
    }
}

internal fun resolveHomeSkinTopTabUnselectedContentColor(contentColor: Color): Color =
    contentColor.copy(alpha = if (contentColor.luminance() > 0.5f) 0.84f else 0.78f)

internal fun resolveHomeSkinTopTabIndicatorColor(contentColor: Color): Color =
    contentColor.copy(alpha = maxOf(contentColor.alpha, 0.92f))

internal fun resolveHomeSkinTopTabRowHeight(): Dp = AppSpacingTokens.TripleExtraLarge - AppSpacingTokens.Micro

internal enum class HomeTopChromeRenderMode {
    PLAIN,
    BLUR,
    LIQUID_GLASS_HAZE,
    LIQUID_GLASS_BACKDROP
}

internal enum class HomeTopChromeSurfaceTreatment {
    STRUCTURED_GLASS,
    FLAT_GLASS
}

internal fun resolveHomeTopLinkedBottomBarAppearance(
    homeSettings: HomeSettings?,
    presentation: AppTopTabPresentation,
): HomeTopLinkedBottomBarAppearance {
    val resolvedHomeSettings = homeSettings ?: HomeSettings()
    val navigationAppearance = resolveAppNavigationAppearance(
        homeSettings = resolvedHomeSettings,
    )
    return HomeTopLinkedBottomBarAppearance(
        isFloating = navigationAppearance.bottomBarFloating,
        blurEnabled = navigationAppearance.bottomBarBlurEnabled && !(
            presentation == AppTopTabPresentation.MATERIAL_UNDERLINE &&
                !resolvedHomeSettings.androidNativeLiquidGlassEnabled
            ),
        liquidGlassEnabled = resolveHomeTopChromeLiquidGlassEnabled(
            homeSettings = resolvedHomeSettings,
        )
    )
}

internal fun shouldUseLegacyHomeTopTabs(
    liquidGlassEnabled: Boolean,
    bottomBarFloating: Boolean,
): Boolean = !liquidGlassEnabled && !bottomBarFloating

/** 与底栏红点共用哨兵值：-1 表示"有未读但不显示数字"。 */
internal const val HOME_TOP_RIGHT_BADGE_DOT = -1

internal fun formatHomeTopRightUnreadBadge(
    action: HomeTopRightAction,
    unreadCount: Int
): String? {
    if (action != HomeTopRightAction.INBOX || unreadCount == 0) return null
    if (unreadCount == HOME_TOP_RIGHT_BADGE_DOT) return "•"
    return if (unreadCount > 99) "99+" else unreadCount.toString()
}

internal data class HomeTopRightUnreadBadgeLayout(
    val offsetX: Dp,
    val offsetY: Dp,
    val reservedEndWidth: Dp,
    val minWidth: Dp,
    val minHeight: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp
)

internal fun resolveHomeTopRightUnreadBadgeLayout(): HomeTopRightUnreadBadgeLayout {
    return HomeTopRightUnreadBadgeLayout(
        offsetX = AppSpacingTokens.None,
        offsetY = AppSpacingTokens.None,
        reservedEndWidth = AppSpacingTokens.Small + AppSpacingTokens.Micro / 2,
        minWidth = AppSpacingTokens.Large + AppSpacingTokens.Micro,
        minHeight = AppSpacingTokens.Large + AppSpacingTokens.Micro,
        horizontalPadding = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro / 2,
        verticalPadding = AppSpacingTokens.Micro / 2
    )
}

internal fun resolveHomeTopRightActionSlotWidth(
    buttonSize: Dp,
    badgeLayout: HomeTopRightUnreadBadgeLayout,
    hasUnreadBadge: Boolean
): Dp = if (hasUnreadBadge) buttonSize + badgeLayout.reservedEndWidth else buttonSize

internal fun resolveHomeTopRightActionContentDescription(
    action: HomeTopRightAction,
    unreadCount: Int
): String {
    if (action != HomeTopRightAction.INBOX || unreadCount == 0) return action.label
    if (unreadCount == HOME_TOP_RIGHT_BADGE_DOT) return "${action.label}，有未读消息"
    val badgeText = formatHomeTopRightUnreadBadge(action, unreadCount) ?: return action.label
    return "${action.label}，$badgeText 条未读"
}

internal fun resolveHomeTopChromeLiquidGlassEnabled(
    homeSettings: HomeSettings?,
): Boolean {
    val resolvedHomeSettings = homeSettings ?: HomeSettings()
    return resolveGlobalLiquidGlassReuseEnabled(
        androidNativeLiquidGlassEnabled = resolvedHomeSettings.androidNativeLiquidGlassEnabled,
    )
}

internal fun resolveHomeTopTabIndicatorLiquidGlassEnabled(
    homeSettings: HomeSettings?,
): Boolean {
    return resolveHomeTopChromeLiquidGlassEnabled(homeSettings)
}

internal fun resolveHomeTopSearchLiquidGlassEnabled(
    homeSettings: HomeSettings?,
): Boolean {
    return resolveHomeTopChromeLiquidGlassEnabled(homeSettings)
}

internal fun resolveHomeTopChromeMaterialMode(
    isHeaderBlurEnabled: Boolean,
    isBottomBarBlurEnabled: Boolean,
    isLiquidGlassEnabled: Boolean,
    isProgressiveTopBlurEnabled: Boolean = false,
): TopTabMaterialMode {
    return when {
        isLiquidGlassEnabled -> TopTabMaterialMode.LIQUID_GLASS
        isProgressiveTopBlurEnabled || isHeaderBlurEnabled || isBottomBarBlurEnabled -> TopTabMaterialMode.BLUR
        else -> TopTabMaterialMode.PLAIN
    }
}

internal fun resolveHomeTopChromeRenderMode(
    materialMode: TopTabMaterialMode,
    isGlassSupported: Boolean,
    hasBackdrop: Boolean,
    hasHazeState: Boolean,
    allowHazeLiquidGlassFallback: Boolean = true
): HomeTopChromeRenderMode {
    return when (materialMode) {
        TopTabMaterialMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
        TopTabMaterialMode.BLUR -> HomeTopChromeRenderMode.BLUR
        TopTabMaterialMode.LIQUID_GLASS -> when {
            isGlassSupported && hasBackdrop -> HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
            isGlassSupported && hasHazeState && allowHazeLiquidGlassFallback ->
                HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            hasHazeState -> HomeTopChromeRenderMode.BLUR
            else -> HomeTopChromeRenderMode.PLAIN
        }
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun shouldDrawHomeTopSearchLegacyHighlight(
    presentation: AppTopTabPresentation,
    useUnifiedTopPanel: Boolean,
    renderMode: HomeTopChromeRenderMode,
    refractionOverlayAlpha: Float
): Boolean = false

internal fun resolveHomeTopChromeSurfaceTreatment(
    renderMode: HomeTopChromeRenderMode,
    preferFlatGlass: Boolean
): HomeTopChromeSurfaceTreatment {
    if (!preferFlatGlass) return HomeTopChromeSurfaceTreatment.STRUCTURED_GLASS
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> HomeTopChromeSurfaceTreatment.FLAT_GLASS
        HomeTopChromeRenderMode.BLUR,
        HomeTopChromeRenderMode.PLAIN -> HomeTopChromeSurfaceTreatment.STRUCTURED_GLASS
    }
}

internal fun resolveHomeHeaderSurfaceAlpha(
    isGlassEnabled: Boolean,
    blurEnabled: Boolean,
    blurIntensity: BlurIntensity
): Float {
    if (!blurEnabled) return 1f
    if (isGlassEnabled) return HOME_HEADER_LIQUID_GLASS_ALPHA
    return BlurStyles.getBackgroundAlpha(blurIntensity)
}

internal fun resolveHomeTopBlurContainerAlpha(
    blurIntensity: BlurIntensity
): Float = BlurStyles.getBackgroundAlpha(blurIntensity)

internal fun resolveHomeTopTabOverlayAlpha(
    materialMode: TopTabMaterialMode,
    isTabFloating: Boolean,
    containerAlpha: Float
): Float {
    return when (materialMode) {
        TopTabMaterialMode.PLAIN -> if (isTabFloating) containerAlpha else 1f
        TopTabMaterialMode.BLUR -> containerAlpha
        TopTabMaterialMode.LIQUID_GLASS -> containerAlpha
    }
}

internal fun resolveHomeTopTabVerticalPaddingDp(isTabFloating: Boolean): Float {
    // Keep a hairline inset so floating glass doesn't weld to the reserved track edge.
    return if (isTabFloating) 1f else 0f
}

internal fun resolveNonNegativeHomeTopPadding(padding: Dp): Dp = padding.coerceAtLeast(AppSpacingTokens.None)

internal fun resolveHomeTopTabYOffsetDp(isTabFloating: Boolean): Float {
    // Mild lift toward search; list padding subtracts the same amount so tabs↔cards stays even.
    return if (isTabFloating) (-2f) else 0f
}

internal fun resolveHomeTopSearchBarHeight(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).searchBarHeight
}

internal data class HomeHeaderScrollLayout(
    val searchBarHeightPx: Float,
    val searchAlpha: Float,
    val tabRowHeightPx: Float,
    val tabAlpha: Float
)

internal data class HomeTopPinnedChromeLayout(
    val tabTop: Dp,
    val searchTop: Dp,
    val blurHeight: Dp
)

internal fun resolveHomeTopPinnedChromeLayout(
    statusBarHeight: Dp,
    visibleSearchHeight: Dp,
    tabRowHeight: Dp,
    searchToTabsSpacing: Dp,
    renderMode: HomeTopChromeRenderMode,
    includeTabInBlur: Boolean = true,
): HomeTopPinnedChromeLayout {
    val visibleSearchBlockHeight = if (visibleSearchHeight > AppSpacingTokens.None) {
        searchToTabsSpacing + visibleSearchHeight
    } else {
        AppSpacingTokens.None
    }
    val visibleChromeHeight = statusBarHeight + visibleSearchBlockHeight +
        if (includeTabInBlur) tabRowHeight else AppSpacingTokens.None
    return HomeTopPinnedChromeLayout(
        tabTop = statusBarHeight + visibleSearchBlockHeight,
        searchTop = statusBarHeight,
        blurHeight = if (renderMode == HomeTopChromeRenderMode.PLAIN) AppSpacingTokens.None else visibleChromeHeight
    )
}

internal fun resolveHomeTopSearchRevealDeadZone(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).searchRevealDeadZone
}

internal fun resolveHomeTopSearchContentTranslationYPx(
    searchRevealFraction: Float,
    searchBarHeightPx: Float,
): Float = -searchBarHeightPx.coerceAtLeast(0f) * (1f - searchRevealFraction.coerceIn(0f, 1f))

internal fun resolveHomeHeaderScrollLayout(
    headerOffsetPx: Float,
    searchBarHeightPx: Float,
    searchCollapseDistancePx: Float,
    tabRowHeightPx: Float,
    isHeaderCollapseEnabled: Boolean,
): HomeHeaderScrollLayout {
    if (!isHeaderCollapseEnabled) {
        return HomeHeaderScrollLayout(
            searchBarHeightPx = searchBarHeightPx,
            searchAlpha = 1f,
            tabRowHeightPx = tabRowHeightPx,
            tabAlpha = 1f
        )
    }
    val effectiveCollapseDistancePx = searchCollapseDistancePx.coerceAtLeast(searchBarHeightPx)
    val revealFraction = if (effectiveCollapseDistancePx > 0f) {
        (1f + headerOffsetPx / effectiveCollapseDistancePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 整段行高与间距共享进度，不设置恢复死区，也不把行内容压缩或淡出。
    return HomeHeaderScrollLayout(
        searchBarHeightPx = searchBarHeightPx.coerceAtLeast(0f) * revealFraction,
        searchAlpha = if (revealFraction > 0f && searchBarHeightPx > 0f) 1f else 0f,
        tabRowHeightPx = tabRowHeightPx,
        tabAlpha = 1f
    )
}

internal fun resolveHomeTopTabRowHeight(
    isTabFloating: Boolean,
    chromePolicy: AppTopChromePolicy,
    labelMode: Int = com.android.purebilibili.core.store.SettingsManager.TopTabLabelMode.TEXT_ONLY
): Dp {
    if (isTabFloating) return FloatingBottomBarDefaultShellHeight
    val style = resolveHomeTopPresetStyle(chromePolicy, labelMode)
    return style.tabRowHeightDocked
}

internal fun resolveHomeTopSearchRowHorizontalPadding(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).searchRowHorizontalPadding
}

internal fun resolveHomeTopSearchPillHeight(
    chromePolicy: AppTopChromePolicy,
): Dp = chromePolicy.compactChromeSpec.primaryHeightDp.dp

internal fun resolveHomeTopSearchContentHorizontalPadding(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).searchContentHorizontalPadding
}

internal fun resolveHomeTopSearchIconTextGap(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).searchIconTextGap
}

internal fun resolveHomeTopSearchContainerShape(
    @Suppress("UNUSED_PARAMETER") chromePolicy: AppTopChromePolicy,
): Shape {
    return resolveSharedBottomBarCapsuleShape()
}

internal fun resolveHomeTopEdgeButtonShape(
    @Suppress("UNUSED_PARAMETER") chromePolicy: AppTopChromePolicy,
): Shape = CircleShape

/**
 * 顶部行统一控件高度：头像、搜索胶囊、设置按钮共用（36dp），两主题一致。
 * 与下方分栏 tab 行（32/36dp）保持同一视觉尺度。
 */
internal fun resolveHomeTopEdgeControlHeight(): Dp =
    AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall

internal fun resolveHomeTopAvatarOuterSize(): Dp = resolveHomeTopEdgeControlHeight()

internal fun resolveHomeTopAvatarInnerSize(): Dp = resolveHomeTopEdgeControlHeight()

internal fun resolveHomeTopSettingsButtonSize(
    @Suppress("UNUSED_PARAMETER") chromePolicy: AppTopChromePolicy,
): Dp {
    // 与头像、搜索胶囊统一控件高度（36dp），两主题一致。
    return resolveHomeTopEdgeControlHeight()
}

internal fun resolveHomeTopSettingsIconSize(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return if (chromePolicy.tabPresentation == AppTopTabPresentation.TONAL_CAPSULE) {
        resolveHomeTopPresetStyle(chromePolicy, labelMode = 2)
            .actionIconSizeDocked
            .coerceAtMost(18.dp)
    } else {
        AppSpacingTokens.Large + AppSpacingTokens.Micro
    }
}

internal fun resolveHomeTopEdgeControlGap(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).edgeControlGap
}

internal fun shouldUseUnifiedHomeTopPanel(chromePolicy: AppTopChromePolicy): Boolean {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).useUnifiedPanel
}

internal fun shouldUseDetachedHomeTopTabDock(
    presentation: AppTopTabPresentation,
    liquidGlassEnabled: Boolean = true,
): Boolean {
    if (!liquidGlassEnabled) return false
    return presentation != AppTopTabPresentation.MATERIAL_UNDERLINE
}

internal fun resolveHomeTopUnifiedPanelHorizontalPadding(chromePolicy: AppTopChromePolicy): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).unifiedPanelHorizontalPadding
}

internal fun resolveHomeTopUnifiedPanelInnerPadding(
    chromePolicy: AppTopChromePolicy,
    collapsedIntoStatusBar: Boolean = false
): Dp {
    if (collapsedIntoStatusBar) return AppSpacingTokens.Micro
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).unifiedPanelInnerPadding
}

internal fun shouldRenderHomeTopUnifiedPanelChrome(
    searchHeightDp: Float,
    tabHeightDp: Float,
    integratedCollapsedTopBar: Boolean,
    minVisibleHeightDp: Float = 0.5f
): Boolean {
    return integratedCollapsedTopBar ||
        searchHeightDp > minVisibleHeightDp ||
        tabHeightDp > minVisibleHeightDp
}

internal fun resolveHomeTopUnifiedPanelCornerRadius(
    chromePolicy: AppTopChromePolicy,
    collapsedIntoStatusBar: Boolean = false
): Dp {
    if (collapsedIntoStatusBar) return AppSpacingTokens.None
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).unifiedPanelCornerRadius
}

internal fun resolveHomeTopReservedContentBottomGap(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).reservedContentBottomGap
}

internal fun resolveHomeTopEmbeddedTabHorizontalPadding(chromePolicy: AppTopChromePolicy): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).embeddedTabHorizontalPadding
}

internal fun resolveHomeTopTabHorizontalPadding(
    @Suppress("UNUSED_PARAMETER") isTabFloating: Boolean,
    chromePolicy: AppTopChromePolicy,
): Dp {
    // 分栏轨道与搜索行共用同一水平内边距，保证 tab 与顶部三控件左右对齐。
    return resolveHomeTopSearchRowHorizontalPadding(chromePolicy)
}

/**
 * 顶部三控件（头像 + 搜索胶囊 + 设置按钮）在屏幕上的合计宽度。
 * 搜索胶囊 weight 撑满整行，合计宽度即整行内容宽度；复用于下方分栏 tab 的
 * 最大宽度（左右对齐约束）。
 */
internal fun resolveHomeTopControlsContentWidthDp(
    containerWidthDp: Dp,
    chromePolicy: AppTopChromePolicy,
): Dp {
    val rowPadding = resolveHomeTopSearchRowHorizontalPadding(chromePolicy)
    return (containerWidthDp - rowPadding * 2f).coerceAtLeast(AppSpacingTokens.None)
}

internal fun resolveHomeTopSearchToTabsSpacing(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).searchToTabsSpacing
}

internal fun resolveHomeTopTabsToContentSpacing(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).tabsToContentSpacing
}

internal fun resolveHomeTopSearchCollapseExtraSpacing(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).searchCollapseExtraSpacing
}

internal fun resolveHomeTopSearchCollapseDistance(
    searchBarHeight: Dp,
    chromePolicy: AppTopChromePolicy,
): Dp {
    return searchBarHeight +
        resolveHomeTopSearchToTabsSpacing(chromePolicy) +
        resolveHomeTopSearchCollapseExtraSpacing(chromePolicy)
}

internal fun shouldUseIntegratedCollapsedHomeTopBar(
    searchRevealFraction: Float,
    presentation: AppTopTabPresentation,
): Boolean {
    return presentation == AppTopTabPresentation.MOVING_CAPSULE && searchRevealFraction <= 0.02f
}

internal fun resolveHomeTopContinuousSlabOverlap(
    chromePolicy: AppTopChromePolicy,
): Dp {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).continuousSlabOverlap
}

internal fun resolveHomeTopContinuousSlabShape(): Shape {
    return RoundedCornerShape(bottomStart = AppSpacingTokens.Medium, bottomEnd = AppSpacingTokens.Medium)
}

internal fun resolveHomeTopReservedListPadding(
    statusBarHeight: Dp,
    searchBarHeight: Dp,
    tabRowHeight: Dp,
    chromePolicy: AppTopChromePolicy,
    isTabFloating: Boolean = false
): Dp {
    val useUnifiedPanel = shouldUseUnifiedHomeTopPanel(chromePolicy)
    val searchToTabs = resolveHomeTopSearchToTabsSpacing(chromePolicy)
    val tabsToContent = resolveHomeTopTabsToContentSpacing(chromePolicy)
    // Floating dock is shifted up via [resolveHomeTopTabYOffsetDp]; fold the same delta into
    // list padding so the visual gap under the dock matches [tabsToContent], not tabsToContent+|offset|.
    val floatingDockLift = resolveHomeTopTabYOffsetDp(isTabFloating).dp
    val chromeHeight = if (useUnifiedPanel) {
        searchBarHeight +
            tabRowHeight +
            (resolveHomeTopUnifiedPanelInnerPadding(chromePolicy) * 2) +
            searchToTabs
    } else {
        searchBarHeight + searchToTabs + tabRowHeight
    }
    return statusBarHeight + chromeHeight + tabsToContent + floatingDockLift
}

internal fun resolveHomeTopBlurContainerColors(
    colors: HomeGlassResolvedColors,
    surfaceColor: Color,
    blurIntensity: BlurIntensity
): HomeGlassResolvedColors {
    return colors.copy(
        containerColor = resolveBottomBarSurfaceColor(
            surfaceColor = surfaceColor,
            blurEnabled = true,
            blurIntensity = blurIntensity
        )
    )
}

internal fun resolveHomeTopBlurSurfaceType(
    renderMode: HomeTopChromeRenderMode
): BlurSurfaceType {
    return when (renderMode) {
        HomeTopChromeRenderMode.BLUR -> BlurSurfaceType.HEADER
        else -> BlurSurfaceType.HEADER
    }
}

internal fun resolveHomeTopContinuousSlabRenderMode(
    renderMode: HomeTopChromeRenderMode,
): HomeTopChromeRenderMode {
    return when (renderMode) {
        HomeTopChromeRenderMode.BLUR -> HomeTopChromeRenderMode.BLUR
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
        HomeTopChromeRenderMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
    }
}

internal fun resolveEffectiveHomeTopContinuousSlabRenderMode(
    renderMode: HomeTopChromeRenderMode,
    liquidGlassEnabled: Boolean,
    integratedCollapsedTopBar: Boolean,
): HomeTopChromeRenderMode {
    // Compact controls replace their local chrome, but the status bar still needs blur.
    if (renderMode == HomeTopChromeRenderMode.BLUR) return renderMode
    return if (liquidGlassEnabled || integratedCollapsedTopBar) {
        HomeTopChromeRenderMode.PLAIN
    } else {
        renderMode
    }
}

internal fun resolveHomeTopContinuousSlabHeight(
    statusBarHeight: Dp,
    searchBarHeight: Dp,
    tabRowHeight: Dp,
    renderMode: HomeTopChromeRenderMode,
    hasVisibleTopContent: Boolean = true
): Dp {
    return resolveHomeTopPinnedChromeLayout(
        statusBarHeight = statusBarHeight,
        visibleSearchHeight = if (hasVisibleTopContent) searchBarHeight else AppSpacingTokens.None,
        tabRowHeight = if (hasVisibleTopContent) tabRowHeight else AppSpacingTokens.None,
        searchToTabsSpacing = AppSpacingTokens.None,
        renderMode = renderMode
    ).blurHeight
}

/** 原生分类保留保护底色；渐进特效使用透光遮罩，普通磨砂与无特效维持原有底色。 */
internal fun resolveHomeNativeTopProtectionAlpha(
    liquidGlassActive: Boolean,
    blurActive: Boolean,
    contentRevealFraction: Float = 1f,
    progressiveChromeActive: Boolean = false,
): Float = when {
    liquidGlassActive -> 0f
    progressiveChromeActive -> 0.24f * contentRevealFraction.coerceIn(0f, 1f)
    blurActive -> 0.96f * contentRevealFraction.coerceIn(0f, 1f)
    else -> contentRevealFraction.coerceIn(0f, 1f)
}

internal fun resolveHomeTopContinuousSlabSurfaceColor(
    baseColor: Color,
    blurAlpha: Float,
    usesNativeContainerTreatment: Boolean,
    renderMode: HomeTopChromeRenderMode
): Color {
    if (renderMode == HomeTopChromeRenderMode.PLAIN) return Color.Transparent
    // Liquid controls already own their local glass surfaces. The continuous slab only samples
    // the backdrop; another tinted fill here becomes a visible rectangular panel behind them.
    if (renderMode != HomeTopChromeRenderMode.BLUR) return Color.Transparent
    return if (usesNativeContainerTreatment) {
        baseColor.copy(alpha = maxOf(baseColor.alpha, blurAlpha))
    } else {
        Color.Transparent
    }
}

internal fun resolveHomeTopPanelChromeRenderMode(
    renderMode: HomeTopChromeRenderMode,
    usesNativeContainerTreatment: Boolean,
    useUnifiedPanel: Boolean = false
): HomeTopChromeRenderMode {
    if (useUnifiedPanel) return HomeTopChromeRenderMode.PLAIN
    return resolveHomeTopLocalChromeRenderMode(
        renderMode = renderMode,
        usesNativeContainerTreatment = usesNativeContainerTreatment,
    )
}

internal fun resolveHomeTopSearchChromeRenderMode(
    renderMode: HomeTopChromeRenderMode,
    useUnifiedPanel: Boolean = false,
    usesNativeContainerTreatment: Boolean,
): HomeTopChromeRenderMode {
    if (useUnifiedPanel) {
        return when (renderMode) {
            HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            HomeTopChromeRenderMode.LIQUID_GLASS_HAZE,
            HomeTopChromeRenderMode.BLUR -> renderMode
            HomeTopChromeRenderMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
        }
    }
    return resolveHomeTopLocalChromeRenderMode(
        renderMode = renderMode,
        usesNativeContainerTreatment = usesNativeContainerTreatment,
    )
}

internal fun resolveHomeTopUnifiedTabChromeRenderMode(
    localTabChromeRenderMode: HomeTopChromeRenderMode,
    usesTonalContainerTreatment: Boolean,
    useUnifiedLiquidChrome: Boolean
): HomeTopChromeRenderMode {
    if (usesTonalContainerTreatment) {
        return localTabChromeRenderMode
    }
    return if (useUnifiedLiquidChrome) {
        localTabChromeRenderMode
    } else if (localTabChromeRenderMode == HomeTopChromeRenderMode.BLUR) {
        HomeTopChromeRenderMode.BLUR
    } else {
        HomeTopChromeRenderMode.PLAIN
    }
}

internal fun resolveHomeTopUnifiedLocalTabChromeRenderMode(
    renderMode: HomeTopChromeRenderMode,
    usesNativeContainerTreatment: Boolean,
    usesTonalContainerTreatment: Boolean,
): HomeTopChromeRenderMode {
    if (usesTonalContainerTreatment) {
        return resolveHomeTopLocalChromeRenderMode(
            renderMode = renderMode,
            usesNativeContainerTreatment = usesNativeContainerTreatment,
        )
    }
    // 统一面板关闭外层 slab 后，标签行需要保留自己的模糊承托区域。
    if (renderMode == HomeTopChromeRenderMode.BLUR) {
        return HomeTopChromeRenderMode.BLUR
    }
    return resolveHomeTopLocalChromeRenderMode(
        renderMode = renderMode,
        usesNativeContainerTreatment = usesNativeContainerTreatment,
    )
}

internal fun resolveHomeTopTabDockChromeRenderMode(
    detachedTopTabDock: Boolean,
    localTabChromeRenderMode: HomeTopChromeRenderMode,
    hasHazeState: Boolean,
): HomeTopChromeRenderMode {
    return if (
        detachedTopTabDock &&
        localTabChromeRenderMode == HomeTopChromeRenderMode.PLAIN &&
        hasHazeState
    ) {
        HomeTopChromeRenderMode.BLUR
    } else {
        localTabChromeRenderMode
    }
}

internal fun shouldApplyHomeTopTabDockHaze(
    embeddedInUnifiedPanel: Boolean,
    continuousSlabRenderMode: HomeTopChromeRenderMode,
): Boolean = !(
    embeddedInUnifiedPanel &&
        continuousSlabRenderMode == HomeTopChromeRenderMode.BLUR
    )

internal fun resolveHomeTopUnifiedTabSurfaceColor(
    tabContainerColor: Color,
    tabOverlayAlpha: Float,
    usesTonalContainerTreatment: Boolean,
    useUnifiedLiquidChrome: Boolean,
    tabChromeRenderMode: HomeTopChromeRenderMode = HomeTopChromeRenderMode.PLAIN
): Color {
    if (usesTonalContainerTreatment) {
        return tabContainerColor.copy(alpha = tabOverlayAlpha)
    }
    return if (useUnifiedLiquidChrome || tabChromeRenderMode == HomeTopChromeRenderMode.BLUR) {
        tabContainerColor.copy(alpha = tabOverlayAlpha)
    } else {
        Color.Transparent
    }
}

internal fun resolveHomeTopDetachedTabDockSurfaceColor(
    isLightMode: Boolean,
    renderMode: HomeTopChromeRenderMode
): Color {
    val alpha = when (renderMode) {
        HomeTopChromeRenderMode.PLAIN -> if (isLightMode) 0.58f else 0.64f
        HomeTopChromeRenderMode.BLUR -> if (isLightMode) 0.46f else 0.58f
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> if (isLightMode) 0.34f else 0.42f
    }
    return if (isLightMode) {
        OpticalContrastPalette.Highlight.copy(alpha = alpha)
    } else {
        OpticalContrastPalette.Shadow.copy(alpha = alpha)
    }
}

internal fun resolveHomeTopUnifiedSearchContainerColor(
    isLightMode: Boolean,
    renderMode: HomeTopChromeRenderMode = HomeTopChromeRenderMode.BLUR
): Color {
    val alpha = when (renderMode) {
        HomeTopChromeRenderMode.PLAIN -> if (isLightMode) 0.62f else 0.42f
        HomeTopChromeRenderMode.BLUR -> if (isLightMode) 0.38f else 0.32f
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> if (isLightMode) 0.34f else 0.18f
    }
    return if (isLightMode) {
        OpticalContrastPalette.Highlight.copy(alpha = alpha)
    } else {
        OpticalContrastPalette.Shadow.copy(alpha = alpha)
    }
}

internal fun resolveHomeTopSearchDarkWhiteOverlayMultiplier(
    isLightMode: Boolean
): Float {
    return if (isLightMode) 0.86f else 0.30f
}

internal fun resolveHomeTopUnifiedSearchBorderColor(
    isLightMode: Boolean,
    renderMode: HomeTopChromeRenderMode = HomeTopChromeRenderMode.BLUR
): Color {
    if (renderMode == HomeTopChromeRenderMode.PLAIN) {
        return if (isLightMode) {
            OpticalContrastPalette.Shadow.copy(alpha = 0.14f)
        } else {
            OpticalContrastPalette.Highlight.copy(alpha = 0.22f)
        }
    }
    if (renderMode == HomeTopChromeRenderMode.BLUR) {
        return if (isLightMode) {
            OpticalContrastPalette.Highlight.copy(alpha = 0.22f)
        } else {
            OpticalContrastPalette.Highlight.copy(alpha = 0.18f)
        }
    }
    return if (isLightMode) {
        OpticalContrastPalette.Highlight.copy(alpha = 0.20f)
    } else {
        OpticalContrastPalette.Highlight.copy(alpha = 0.12f)
    }
}

internal fun resolveHomeTopEdgeControlContainerColor(
    isLightMode: Boolean,
    renderMode: HomeTopChromeRenderMode
): Color {
    val alpha = when (renderMode) {
        HomeTopChromeRenderMode.PLAIN -> if (isLightMode) 0.58f else 0.40f
        HomeTopChromeRenderMode.BLUR -> if (isLightMode) 0.38f else 0.32f
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> if (isLightMode) 0.12f else 0.14f
    }
    return if (isLightMode) {
        OpticalContrastPalette.Highlight.copy(alpha = alpha)
    } else {
        OpticalContrastPalette.Shadow.copy(alpha = alpha)
    }
}

internal fun resolveHomeTopEdgeControlBorderColor(
    isLightMode: Boolean,
    renderMode: HomeTopChromeRenderMode
): Color {
    return when (renderMode) {
        HomeTopChromeRenderMode.PLAIN -> if (isLightMode) {
            OpticalContrastPalette.Shadow.copy(alpha = 0.12f)
        } else {
            OpticalContrastPalette.Highlight.copy(alpha = 0.20f)
        }
        HomeTopChromeRenderMode.BLUR -> if (isLightMode) {
            OpticalContrastPalette.Highlight.copy(alpha = 0.16f)
        } else {
            OpticalContrastPalette.Highlight.copy(alpha = 0.16f)
        }
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> Color.Transparent
    }
}

internal fun resolveHomeTopUnifiedPanelReadabilityColor(
    isLightMode: Boolean,
    renderMode: HomeTopChromeRenderMode
): Color {
    val alpha = when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.18f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.20f
        HomeTopChromeRenderMode.BLUR -> 0.16f
        HomeTopChromeRenderMode.PLAIN -> 0f
    }
    return if (isLightMode) {
        OpticalContrastPalette.Highlight.copy(alpha = alpha)
    } else {
        OpticalContrastPalette.Shadow.copy(alpha = alpha)
    }
}

internal fun resolveHomeTopWideChromePreferFlatGlass(
    renderMode: HomeTopChromeRenderMode
): Boolean {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> false
        HomeTopChromeRenderMode.BLUR,
        HomeTopChromeRenderMode.PLAIN -> true
    }
}

internal fun resolveHomeTopLocalChromeRenderMode(
    renderMode: HomeTopChromeRenderMode,
    usesNativeContainerTreatment: Boolean,
): HomeTopChromeRenderMode {
    if (usesNativeContainerTreatment && renderMode == HomeTopChromeRenderMode.BLUR) {
        return HomeTopChromeRenderMode.BLUR
    }
    return when (renderMode) {
        HomeTopChromeRenderMode.BLUR -> HomeTopChromeRenderMode.PLAIN
        else -> renderMode
    }
}

internal fun resolveHomeTopChromeMotionPolicy(
    renderMode: HomeTopChromeRenderMode,
    isScrolling: Boolean,
    isTransitionRunning: Boolean
): HomeTopChromeMotionPolicy {
    return if (renderMode == HomeTopChromeRenderMode.BLUR) {
        HomeTopChromeMotionPolicy(
            isScrolling = false,
            isTransitionRunning = false
        )
    } else {
        HomeTopChromeMotionPolicy(
            isScrolling = isScrolling,
            isTransitionRunning = isTransitionRunning
        )
    }
}

internal fun resolveHomeTopTabChromeMotionPolicy(
    renderMode: HomeTopChromeRenderMode,
    isScrolling: Boolean,
    isTransitionRunning: Boolean
): HomeTopChromeMotionPolicy {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE,
        HomeTopChromeRenderMode.BLUR -> HomeTopChromeMotionPolicy(
            isScrolling = false,
            isTransitionRunning = false
        )
        HomeTopChromeRenderMode.PLAIN -> resolveHomeTopChromeMotionPolicy(
            renderMode = renderMode,
            isScrolling = isScrolling,
            isTransitionRunning = isTransitionRunning
        )
    }
}

internal fun shouldEnableTopTabSecondaryBlur(
    hasHeaderBlur: Boolean,
    topTabMaterialMode: TopTabMaterialMode,
    isScrolling: Boolean,
    isTransitionRunning: Boolean,
    isEmbeddedInUnifiedPanel: Boolean = false,
): Boolean {
    if (!hasHeaderBlur) return false
    if (topTabMaterialMode == TopTabMaterialMode.PLAIN) return false
    // The continuous header slab already samples everything behind an embedded tab row.
    // A second Haze pass here only thickens the material and records the same pixels twice.
    if (isEmbeddedInUnifiedPanel) return false
    if (topTabMaterialMode == TopTabMaterialMode.LIQUID_GLASS && (isScrolling || isTransitionRunning)) {
        return false
    }
    return true
}

internal fun resolveHomeHeaderTabBorderAlpha(
    isTabFloating: Boolean,
    isTabGlassEnabled: Boolean
): Float {
    return 0f
}

internal fun resolveHomeTopChromeReadabilityAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.26f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.28f
        HomeTopChromeRenderMode.BLUR -> 0.30f
        HomeTopChromeRenderMode.PLAIN -> 0.16f
    }
}

internal fun resolveHomeTopSearchContentAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.88f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.90f
        HomeTopChromeRenderMode.BLUR -> 0.92f
        HomeTopChromeRenderMode.PLAIN -> 0.78f
    }
}

internal fun resolveHomeTopForegroundColor(
    isLightMode: Boolean
): Color {
    return if (isLightMode) {
        OpticalContrastPalette.Shadow
    } else {
        OpticalContrastPalette.Highlight.copy(alpha = 0.92f)
    }
}

internal fun resolveHomeTopInnerUnderlayColor(
    isLightMode: Boolean,
    renderMode: HomeTopChromeRenderMode,
    softenWideChrome: Boolean = false
): Color {
    val alpha = resolveHomeTopTabContentUnderlayAlpha(
        renderMode = renderMode,
        softenWideChrome = softenWideChrome
    )
    return if (isLightMode) {
        OpticalContrastPalette.Highlight.copy(alpha = alpha)
    } else {
        OpticalContrastPalette.Shadow.copy(alpha = (alpha * 0.72f).coerceAtLeast(0.05f))
    }
}

internal fun resolveHomeTopChromeHighlightOverlayColor(
    baseColor: Color,
    renderMode: HomeTopChromeRenderMode,
    softenWideChrome: Boolean
): Color {
    if (!softenWideChrome) return baseColor
    val alphaMultiplier = when (renderMode) {
        HomeTopChromeRenderMode.BLUR -> 0.42f
        else -> 1f
    }
    return baseColor.copy(alpha = baseColor.alpha * alphaMultiplier)
}

internal fun tuneHomeTopGlassColors(
    colors: HomeGlassResolvedColors,
    isLightMode: Boolean,
    emphasized: Boolean
): HomeGlassResolvedColors {
    if (isLightMode) return colors
    return colors.copy(
        containerColor = colors.containerColor.copy(alpha = colors.containerColor.alpha * if (emphasized) 0.74f else 0.68f),
        borderColor = OpticalContrastPalette.Highlight.copy(alpha = colors.borderColor.alpha * 0.48f),
        highlightColor = OpticalContrastPalette.Highlight.copy(alpha = colors.highlightColor.alpha * 0.28f)
    )
}

internal fun resolveHomeTopContainerColors(
    usesNativeContainerTreatment: Boolean,
    usesTonalContainerTreatment: Boolean,
    emphasized: Boolean,
    blurEnabled: Boolean,
    fallbackColors: HomeGlassResolvedColors,
    surfaceContainerColor: Color,
    surfaceContainerHighColor: Color,
    outlineVariantColor: Color
): HomeGlassResolvedColors {
    if (!usesNativeContainerTreatment) return fallbackColors
    if (blurEnabled) {
        val baseColor = if (usesTonalContainerTreatment) {
            surfaceContainerColor
        } else if (emphasized) {
            surfaceContainerHighColor
        } else {
            surfaceContainerColor
        }
        return HomeGlassResolvedColors(
            containerColor = baseColor.copy(alpha = fallbackColors.containerColor.alpha),
            borderColor = outlineVariantColor.copy(
                alpha = fallbackColors.borderColor.alpha.coerceAtLeast(
                    if (usesTonalContainerTreatment) 0.16f else if (emphasized) 0.18f else 0.14f
                )
            ),
            highlightColor = Color.Transparent
        )
    }
    return HomeGlassResolvedColors(
        containerColor = if (usesTonalContainerTreatment) {
            surfaceContainerColor
        } else if (emphasized) {
            surfaceContainerHighColor
        } else {
            surfaceContainerColor
        },
        borderColor = outlineVariantColor.copy(
            alpha = if (usesTonalContainerTreatment) {
                if (emphasized) 0.44f else 0.34f
            } else if (emphasized) {
                0.55f
            } else {
                0.42f
            }
        ),
        highlightColor = Color.Transparent
    )
}

internal fun resolveHomeTopActionIconAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.86f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.88f
        HomeTopChromeRenderMode.BLUR -> 0.90f
        HomeTopChromeRenderMode.PLAIN -> 0.78f
    }
}

internal fun resolveHomeTopUnifiedPanelDividerAlpha(
    renderMode: HomeTopChromeRenderMode
): Float {
    return when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0f
        HomeTopChromeRenderMode.BLUR -> 0.18f
        HomeTopChromeRenderMode.PLAIN -> 0.12f
    }
}

internal fun shouldShowUnifiedHomeTopPanelDivider(
    chromePolicy: AppTopChromePolicy,
): Boolean {
    return resolveHomeTopPresetStyle(chromePolicy, labelMode = 2).showUnifiedPanelDivider
}

internal fun resolveHomeTopTabContentUnderlayAlpha(
    renderMode: HomeTopChromeRenderMode,
    softenWideChrome: Boolean = false
): Float {
    val base = when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> 0.10f
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> 0.12f
        HomeTopChromeRenderMode.BLUR -> 0.14f
        HomeTopChromeRenderMode.PLAIN -> 0.08f
    }
    return if (softenWideChrome && renderMode == HomeTopChromeRenderMode.BLUR) {
        (base * 0.42f).coerceAtLeast(0.04f)
    } else {
        base
    }
}

internal fun resolveHomeTopChromeLensShape(shape: Shape): Shape? {
    return when {
        shape is CornerBasedShape -> shape
        shape === CircleShape -> RoundedCornerShape(percent = 50)
        shape === androidx.compose.ui.graphics.RectangleShape -> RoundedCornerShape(size = AppSpacingTokens.None)
        else -> null
    }
}

private data class HomeTopChromeSurfaceStyle(
    val blurSurfaceType: BlurSurfaceType,
    val preferFlatGlass: Boolean,
    val depthEffect: Boolean,
    val refractionAmountScrollMultiplier: Float,
    val refractionAmountScrollCap: Float,
    val surfaceAlphaScrollMultiplier: Float,
    val surfaceAlphaScrollCap: Float,
    val darkThemeWhiteOverlayMultiplier: Float,
    val useTuningSurfaceAlpha: Boolean,
    val hazeBackgroundAlphaMultiplier: Float
)

private data class HomeTopChromeBackdropSpec(
    val refractionAmount: Float,
    val surfaceAlpha: Float,
    val whiteOverlayAlpha: Float
)

private fun resolveHomeTopChromeBackdropSpec(
    tuning: LiquidGlassTuning,
    scrollOffset: Float,
    isDarkTheme: Boolean,
    style: HomeTopChromeSurfaceStyle
): HomeTopChromeBackdropSpec {
    val refractionAmount = if (tuning.scrollCoupledRefractionAmount > 0f) {
        tuning.refractionAmount + (
            scrollOffset * style.refractionAmountScrollMultiplier * tuning.scrollCoupledRefractionAmount
        ).coerceIn(0f, style.refractionAmountScrollCap * tuning.scrollCoupledRefractionAmount)
    } else {
        tuning.refractionAmount
    }
    val surfaceAlpha = if (tuning.scrollCoupledRefractionAmount > 0f) {
        tuning.surfaceAlpha + (
            scrollOffset * style.surfaceAlphaScrollMultiplier * tuning.scrollCoupledRefractionAmount
        ).coerceIn(0f, style.surfaceAlphaScrollCap * tuning.scrollCoupledRefractionAmount)
    } else {
        tuning.surfaceAlpha
    }
    val whiteOverlayAlpha = if (isDarkTheme) {
        tuning.whiteOverlayAlpha * style.darkThemeWhiteOverlayMultiplier
    } else {
        tuning.whiteOverlayAlpha
    }
    return HomeTopChromeBackdropSpec(
        refractionAmount = refractionAmount,
        surfaceAlpha = surfaceAlpha,
        whiteOverlayAlpha = whiteOverlayAlpha
    )
}

private fun resolveHomeTopChromeSurfaceColor(
    surfaceColor: Color,
    backdropSpec: HomeTopChromeBackdropSpec,
    style: HomeTopChromeSurfaceStyle
): Color {
    return if (style.useTuningSurfaceAlpha) {
        surfaceColor.copy(alpha = backdropSpec.surfaceAlpha)
    } else {
        surfaceColor
    }
}

internal fun Modifier.homeTopChromeSurface(
    renderMode: HomeTopChromeRenderMode,
    shape: Shape,
    surfaceColor: Color,
    hazeState: HazeState?,
    miuixBackdrop: top.yukonga.miuix.kmp.blur.Backdrop? = null,
    liquidStyle: LiquidGlassStyle,
    liquidGlassTuning: LiquidGlassTuning? = null,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    motionTier: MotionTier,
    isScrolling: Boolean,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    useProgressiveTopBlur: Boolean = false,
    useProgressiveTopFade: Boolean = false,
    preferFlatGlass: Boolean = false,
    darkThemeWhiteOverlayMultiplier: Float = 0.86f
): Modifier = composed {
    val isLiquidGlassMode = renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
        renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
    // Compact controls reuse the bottom-bar material. The full-width top slab deliberately keeps
    // the progressive blur path so its lower edge fades into content instead of becoming a clipped
    // glass-shell boundary.
    if (isLiquidGlassMode && !useProgressiveTopBlur && !useProgressiveTopFade) {
        return@composed this.homeTopBottomBarMatchedSurface(
            renderMode = renderMode,
            shape = shape,
            hazeState = hazeState,
            miuixBackdrop = miuixBackdrop,
            liquidGlassStyle = liquidStyle,
            liquidGlassTuning = liquidGlassTuning,
            liquidGlassPreset = liquidGlassPreset,
            motionTier = motionTier,
            isTransitionRunning = isTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget,
            // 顶栏/搜索小胶囊关闭 shell lens，避免 iOS 主题复用安卓原生液态玻璃时的边沿虾线。
            drawShellLens = false,
            isScrolling = isScrolling
        )
    }

    when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> {
            this
                .biliPaiProgressiveTopBlur(
                    backdrop = miuixBackdrop,
                    enabled = useProgressiveTopBlur,
                    shape = shape,
                    blurRadiusDp = liquidGlassTuning?.progressiveBlurRadius
                        ?: BILIPAI_PROGRESSIVE_TOP_BLUR_RADIUS_DP,
                    gradient = ProgressiveBlur.Top.copy(
                        startFraction = liquidGlassTuning?.progressiveBlurStartFraction ?: 0f,
                        endFraction = liquidGlassTuning?.progressiveBlurEndFraction
                            ?: ProgressiveBlur.Top.endFraction,
                        curve = liquidGlassTuning?.progressiveBlurCurve
                            ?: BILIPAI_PROGRESSIVE_TOP_BLUR_FALLOFF_CURVE,
                    ),
                )
                .background(if (useProgressiveTopBlur || useProgressiveTopFade) Color.Transparent else surfaceColor, shape)
        }

        HomeTopChromeRenderMode.BLUR -> {
            val isProgressiveBlurActive = shouldUseBiliPaiProgressiveTopBlur(
                enabled = useProgressiveTopBlur,
                hasBackdrop = miuixBackdrop != null,
            )
            this
                .then(
                    if (isProgressiveBlurActive) {
                        Modifier.biliPaiProgressiveTopBlur(
                            backdrop = miuixBackdrop,
                            enabled = true,
                            shape = shape,
                            blurRadiusDp = liquidGlassTuning?.progressiveBlurRadius
                                ?: BILIPAI_PROGRESSIVE_TOP_BLUR_RADIUS_DP,
                            gradient = ProgressiveBlur.Top.copy(
                                startFraction = liquidGlassTuning?.progressiveBlurStartFraction ?: 0f,
                                endFraction = liquidGlassTuning?.progressiveBlurEndFraction
                                    ?: ProgressiveBlur.Top.endFraction,
                                curve = liquidGlassTuning?.progressiveBlurCurve
                                    ?: BILIPAI_PROGRESSIVE_TOP_BLUR_FALLOFF_CURVE,
                            ),
                        )
                    } else if (miuixBackdrop != null && !useProgressiveTopFade &&
                        !isLowBlurBudgetForced(forceLowBlurBudget)
                    ) {
                        // Miuix home captures only Backdrop; Haze is intentionally absent.
                        val density = LocalDensity.current
                        val blurRadiusPx = with(density) { 25.dp.toPx() }
                        val shapeBlock = remember(shape) { { shape } }
                        // 普通磨砂也保持效果回调身份，滚动引发的无关重组不重建效果缓存。
                        remember(miuixBackdrop, shapeBlock, blurRadiusPx) {
                            Modifier.miuixDrawBackdrop(
                                backdrop = miuixBackdrop,
                                shape = shapeBlock,
                                effects = { miuixBlur(blurRadiusPx, blurRadiusPx) },
                            )
                        }
                    } else if (hazeState != null && !useProgressiveTopFade) {
                        Modifier.unifiedBlur(
                            hazeState = hazeState,
                            shape = shape,
                            surfaceType = resolveHomeTopBlurSurfaceType(renderMode),
                            motionTier = motionTier,
                            isScrolling = isScrolling,
                            isTransitionRunning = isTransitionRunning,
                            forceLowBudget = forceLowBlurBudget
                        )
                    } else {
                        Modifier
                    }
                )
                .background(if (isProgressiveBlurActive || useProgressiveTopFade) Color.Transparent else surfaceColor, shape)
        }

        HomeTopChromeRenderMode.PLAIN -> {
            this.background(if (useProgressiveTopFade) Color.Transparent else surfaceColor, shape)
        }
    }
}

@Composable
private fun HomeHeaderControlBounds(
    modifier: Modifier,
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    AppExactTouchTargets {
        BoxWithConstraints(modifier = modifier, content = content)
    }
}

/**
 *  简洁版首页头部 (带滚动隐藏/显示动画)
 *
 *  [Refactor] 现在改为由外部通过 NestedScrollConnection 直接控制高度和透明度，
 *  实现了 1:1 的物理跟手效果，消除了漂浮感。
 */
@Composable
fun HomeHeader(
    headerOffsetProvider: () -> Float, // [Optimization] Defer state read to prevent parent recomposition
    isHeaderCollapseEnabled: Boolean = true,
    isTopTabsAutoCollapseEnabled: Boolean = false,
    isTopTabsManualCollapseEnabled: Boolean = true,
    user: UserState,
    onAvatarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onInboxClick: () -> Unit = {},
    topRightUnreadCount: Int = 0,
    onSearchClick: () -> Unit,
    topCategories: List<String> = resolveHomeTopCategories().map { it.label },
    topCategoryKeys: List<String> = resolveHomeTopCategories().map { it.name },
    categoryIndex: Int,
    onCategorySelected: (Int) -> Unit,
    onCategoryReselected: (() -> Unit)? = null,
    onPartitionClick: () -> Unit = {},  //  新增：分区按钮回调
    hazeState: HazeState? = null,  // 保留参数兼容性，但不用于模糊
    onStatusBarDoubleTap: () -> Unit = {},
    //  [新增] 下拉刷新状态
    isRefreshing: Boolean = false,
    pullProgress: Float = 0f,  // 0.0 ~ 1.0+ 下拉进度
    pagerState: androidx.compose.foundation.pager.PagerState? = null, // [New] PagerState for sync
    // Miuix is the single liquid-glass renderer for the home chrome.
    miuixBackdrop: top.yukonga.miuix.kmp.blur.Backdrop? = null,
    homeSettings: com.android.purebilibili.core.store.HomeSettings? = null,
    topTabsVisible: Boolean = true,
    topTabsCollapsed: Boolean = false,
    onTopTabsCollapsedChange: (Boolean) -> Unit = {},
    motionTier: MotionTier = MotionTier.Normal,
    isScrolling: Boolean = false,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    interactionBudget: HomeInteractionMotionBudget = HomeInteractionMotionBudget.FULL,
    uiSkinDecoration: HomeUiSkinDecoration? = null
) {
    val topChromePolicy = rememberAppTopChromePolicy()
    val semanticVisualPolicy = rememberAppSemanticVisualPolicy()
    val contentCardSurfaceSpec = rememberContentCardSurfaceSpec()
    val usesNativeContainerTreatment = semanticVisualPolicy.prefersNativeChrome
    val usesTonalContainerTreatment = contentCardSurfaceSpec.usesTonalContainerTreatment
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.NAVIGATION)
    val density = LocalDensity.current
    val topChromeLiquidGlassEnabled = resolveHomeTopChromeLiquidGlassEnabled(
        homeSettings = homeSettings,
    )
    val resolvedHeaderBlurMode = homeSettings?.headerBlurMode ?: HomeHeaderBlurMode.FOLLOW_PRESET
    val isHeaderBlurEnabled = remember(resolvedHeaderBlurMode, topChromeLiquidGlassEnabled) {
        if (topChromeLiquidGlassEnabled) {
            false
        } else {
            resolveHomeHeaderBlurEnabled(
                mode = resolvedHeaderBlurMode,
            )
        }
    }
    val linkedBottomBarAppearance = remember(
        homeSettings,
        topChromePolicy.tabPresentation,
    ) {
        resolveHomeTopLinkedBottomBarAppearance(
            homeSettings = homeSettings,
            presentation = topChromePolicy.tabPresentation,
        )
    }
    val edgeButtonShape = resolveHomeTopEdgeButtonShape(topChromePolicy)
    val topActionIcons = resolveHomeTopActionIcons(semanticVisualPolicy.effectiveIconFamily)
    val searchIconOpticalScale = resolveAppSearchGlyphOpticalScale(semanticVisualPolicy.effectiveIconFamily)
    val topRightAction = homeSettings?.homeTopRightAction ?: HomeTopRightAction.SETTINGS
    val topRightActionIcon = if (topRightAction == HomeTopRightAction.INBOX) {
        topActionIcons.inbox
    } else {
        topActionIcons.settings
    }
    val topRightActionContentDescription = resolveHomeTopRightActionContentDescription(
        action = topRightAction,
        unreadCount = topRightUnreadCount
    )
    val topRightUnreadBadge = formatHomeTopRightUnreadBadge(
        action = topRightAction,
        unreadCount = topRightUnreadCount
    )
    val topRightUnreadBadgeLayout = resolveHomeTopRightUnreadBadgeLayout()
    val onTopRightActionClick = if (topRightAction == HomeTopRightAction.INBOX) {
        onInboxClick
    } else {
        onSettingsClick
    }
    val useLegacyHomeTopTabs = shouldUseLegacyHomeTopTabs(
        liquidGlassEnabled = topChromeLiquidGlassEnabled,
        bottomBarFloating = linkedBottomBarAppearance.isFloating,
    )

    // 状态栏高度
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val appThemeConfig = com.android.purebilibili.core.ui.LocalAppThemeConfig.current
    val progressiveTopBlurEnabled = appThemeConfig.progressiveTopBlurEnabled
    val progressiveTopFadeEnabled = appThemeConfig.progressiveTopFadeEnabled

    // [Feature] Liquid Glass Logic
    val topChromeMaterialMode = resolveHomeTopChromeMaterialMode(
        isHeaderBlurEnabled = isHeaderBlurEnabled,
        isBottomBarBlurEnabled = linkedBottomBarAppearance.blurEnabled,
        isLiquidGlassEnabled = topChromeLiquidGlassEnabled,
        isProgressiveTopBlurEnabled = progressiveTopBlurEnabled,
    )
    val isGlassEnabled = topChromeMaterialMode == TopTabMaterialMode.LIQUID_GLASS
    val isTopChromeBlurEnabled = topChromeMaterialMode != TopTabMaterialMode.PLAIN
    val searchLiquidGlassEnabled = resolveHomeTopSearchLiquidGlassEnabled(
        homeSettings = homeSettings,
    )
    // Local search/settings surfaces follow their own header/glass switches.
    // Progressive header blur and bottom dock blur must not frost these controls.
    val searchChromeMaterialMode = resolveHomeTopChromeMaterialMode(
        isHeaderBlurEnabled = isHeaderBlurEnabled,
        isBottomBarBlurEnabled = false,
        isLiquidGlassEnabled = searchLiquidGlassEnabled,
        isProgressiveTopBlurEnabled = false,
    )
    //  读取当前模糊强度以确定背景透明度
    val blurIntensity = currentUnifiedBlurIntensity()
    val backgroundAlpha = resolveHomeHeaderSurfaceAlpha(
        isGlassEnabled = isGlassEnabled,
        blurEnabled = isTopChromeBlurEnabled,
        blurIntensity = blurIntensity
    )

    val topTabStyle = resolveTopTabStyle(
        isBottomBarFloating = linkedBottomBarAppearance.isFloating,
        isBottomBarBlurEnabled = isHeaderBlurEnabled,
        isLiquidGlassEnabled = topChromeLiquidGlassEnabled
    )
    val isTabFloating = topTabStyle.floating
    val isTabGlassEnabled = topChromeMaterialMode == TopTabMaterialMode.LIQUID_GLASS
    val isTabBlurEnabled = topChromeMaterialMode == TopTabMaterialMode.BLUR
    val topTrimImagePath = uiSkinDecoration?.topAtmosphereImagePath
    val hasIllustratedHeader = !topTrimImagePath.isNullOrBlank() ||
        !uiSkinDecoration?.topTabBackgroundImagePath.isNullOrBlank()
    val useUnifiedTopPanel = !hasIllustratedHeader && shouldUseUnifiedHomeTopPanel(topChromePolicy)
    val useDetachedTopTabDock = shouldUseDetachedHomeTopTabDock(
        presentation = topChromePolicy.tabPresentation,
        liquidGlassEnabled = topChromeLiquidGlassEnabled,
    )
    val embedTopTabsInUnifiedPanel = useUnifiedTopPanel && !useDetachedTopTabDock
    val enableTopTabSecondaryBlur = shouldEnableTopTabSecondaryBlur(
        hasHeaderBlur = hazeState != null,
        topTabMaterialMode = topChromeMaterialMode,
        isScrolling = isScrolling,
        isTransitionRunning = isTransitionRunning,
        isEmbeddedInUnifiedPanel = embedTopTabsInUnifiedPanel,
    )
    val isGlassSupported = shouldAllowHomeChromeLiquidGlass(Build.VERSION.SDK_INT)
    val allowHazeLiquidGlassFallback = shouldAllowDirectHazeLiquidGlassFallback(Build.VERSION.SDK_INT)
    val liquidStyle = homeSettings?.liquidGlassStyle ?: LiquidGlassStyle.CLASSIC
    val bottomBarLiquidGlassPreset = homeSettings?.bottomBarLiquidGlassPreset
        ?: HomeSettings().bottomBarLiquidGlassPreset
    val liquidGlassProgress = homeSettings?.liquidGlassProgress ?: 0.5f
    val liquidGlassAdvancedSettings = homeSettings?.liquidGlassAdvancedSettings
        ?: HomeSettings().liquidGlassAdvancedSettings
    val liquidGlassReadabilityMode = homeSettings?.liquidGlassReadabilityMode
        ?: HomeSettings().liquidGlassReadabilityMode
    val liquidGlassTuning = remember(
        liquidGlassProgress,
        liquidGlassAdvancedSettings,
        liquidGlassReadabilityMode,
    ) {
        resolveLiquidGlassTuning(
            liquidGlassProgress,
            liquidGlassAdvancedSettings,
            liquidGlassReadabilityMode,
        )
    }
    val topChromeRenderMode = resolveHomeTopChromeRenderMode(
        materialMode = topChromeMaterialMode,
        isGlassSupported = isGlassSupported,
        hasBackdrop = miuixBackdrop != null,
        hasHazeState = hazeState != null,
        allowHazeLiquidGlassFallback = allowHazeLiquidGlassFallback
    )
    val surfaceColor = AppSurfaceTokens.cardContainer()
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHighColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val tabShape = RoundedCornerShape(if (isTabFloating) AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro else AppSpacingTokens.None)
    val tabSurfaceColor = surfaceColor
    val isLightMode = surfaceColor.luminance() > 0.5f
    val effectiveTabMaterialMode = resolveEffectiveHomeHeaderTabMaterialMode(
        materialMode = topChromeMaterialMode,
        interactionBudget = interactionBudget
    )
    // 同时关闭液态玻璃与悬浮底栏时恢复旧式纯文字分栏：选中项仅显示短下划线。
    val drawTopTabOuterChromeSurface = shouldDrawHomeTopTabOuterChromeSurface(
        presentation = topChromePolicy.tabPresentation,
        materialMode = effectiveTabMaterialMode
    )
    val rawHeaderChromeColors = tuneHomeTopGlassColors(
        colors = rememberHomeGlassChromeColors(
            glassEnabled = isGlassEnabled,
            blurEnabled = isTopChromeBlurEnabled
        ),
        isLightMode = isLightMode,
        emphasized = false
    )
    val headerChromeColors = remember(
        rawHeaderChromeColors,
        isGlassEnabled,
        isTopChromeBlurEnabled,
        blurIntensity,
        usesNativeContainerTreatment,
        usesTonalContainerTreatment,
    ) {
        val resolved = if (!isGlassEnabled && isTopChromeBlurEnabled) {
            resolveHomeTopBlurContainerColors(
                colors = rawHeaderChromeColors,
                surfaceColor = surfaceColor,
                blurIntensity = blurIntensity
            )
        } else {
            rawHeaderChromeColors
        }
        resolveHomeTopContainerColors(
            usesNativeContainerTreatment = usesNativeContainerTreatment,
            usesTonalContainerTreatment = usesTonalContainerTreatment,
            emphasized = false,
            blurEnabled = !isGlassEnabled && isTopChromeBlurEnabled,
            fallbackColors = resolved,
            surfaceContainerColor = surfaceContainerColor,
            surfaceContainerHighColor = surfaceContainerHighColor,
            outlineVariantColor = outlineVariantColor
        )
    }
    val rawTabChromeColors = tuneHomeTopGlassColors(
        colors = rememberHomeGlassChromeColors(
            glassEnabled = effectiveTabMaterialMode == TopTabMaterialMode.LIQUID_GLASS,
            blurEnabled = enableTopTabSecondaryBlur || effectiveTabMaterialMode != TopTabMaterialMode.PLAIN
        ),
        isLightMode = isLightMode,
        emphasized = false
    )
    val tabChromeColors = remember(rawTabChromeColors, effectiveTabMaterialMode, blurIntensity) {
        if (effectiveTabMaterialMode == TopTabMaterialMode.BLUR) {
            resolveHomeTopBlurContainerColors(
                colors = rawTabChromeColors,
                surfaceColor = tabSurfaceColor,
                blurIntensity = blurIntensity
            )
        } else {
            rawTabChromeColors
        }
    }
    val tabChromeStyle = remember(effectiveTabMaterialMode, enableTopTabSecondaryBlur) {
        resolveHomeGlassChromeStyle(
            glassEnabled = effectiveTabMaterialMode == TopTabMaterialMode.LIQUID_GLASS,
            blurEnabled = enableTopTabSecondaryBlur || effectiveTabMaterialMode != TopTabMaterialMode.PLAIN
        )
    }
    val topForegroundColor = resolveHomeTopForegroundColor(isLightMode = isLightMode)
    val topSearchContentAlpha = resolveHomeTopSearchContentAlpha(topChromeRenderMode)
    val topActionIconAlpha = resolveHomeTopActionIconAlpha(topChromeRenderMode)
    val topChromeMotionPolicy = resolveHomeTopChromeMotionPolicy(
        renderMode = topChromeRenderMode,
        isScrolling = isScrolling,
        isTransitionRunning = isTransitionRunning
    )
    val tabChromeRenderMode = when (effectiveTabMaterialMode) {
        TopTabMaterialMode.LIQUID_GLASS -> resolveHomeTopChromeRenderMode(
            materialMode = effectiveTabMaterialMode,
            isGlassSupported = isGlassSupported,
            hasBackdrop = miuixBackdrop != null,
            hasHazeState = hazeState != null,
            allowHazeLiquidGlassFallback = allowHazeLiquidGlassFallback
        )
        TopTabMaterialMode.BLUR -> if (enableTopTabSecondaryBlur) {
            HomeTopChromeRenderMode.BLUR
        } else {
            HomeTopChromeRenderMode.PLAIN
        }
        TopTabMaterialMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
    }
    val tabChromeMotionPolicy = resolveHomeTopTabChromeMotionPolicy(
        renderMode = tabChromeRenderMode,
        isScrolling = isScrolling,
        isTransitionRunning = isTransitionRunning
    )
    val topPanelChromeRenderMode = resolveHomeTopPanelChromeRenderMode(
        renderMode = topChromeRenderMode,
        usesNativeContainerTreatment = usesNativeContainerTreatment,
        useUnifiedPanel = useUnifiedTopPanel
    )
    val searchChromeBaseRenderMode = resolveHomeTopChromeRenderMode(
        materialMode = searchChromeMaterialMode,
        isGlassSupported = isGlassSupported,
        hasBackdrop = miuixBackdrop != null,
        hasHazeState = hazeState != null,
        allowHazeLiquidGlassFallback = allowHazeLiquidGlassFallback
    )
    val searchChromeRenderMode = resolveHomeTopSearchChromeRenderMode(
        renderMode = searchChromeBaseRenderMode,
        useUnifiedPanel = useUnifiedTopPanel,
        usesNativeContainerTreatment = usesNativeContainerTreatment,
    )
    val useTopControlBlur = searchChromeMaterialMode == TopTabMaterialMode.BLUR
    // Only sample the sibling feed source supplied by HomeScreen. The navigation-wide
    // backdrop captures this header too, so using it as a fallback creates a RenderNode cycle.
    // Until the feed source is ready, keep the tinted/blur fallback without recording ourselves.
    val topActionBackdrop = miuixBackdrop
    val topActionGlassActive = appThemeConfig.liquidGlassEnabled && topActionBackdrop != null
    val topActionElasticState = rememberAppElasticPressState(
        visualSize = resolveHomeTopSettingsButtonSize(topChromePolicy),
    )
    val topActionSurfaceModifier = if (topActionGlassActive) {
        Modifier.biliPaiFloatingDockShell(
            backdrop = topActionBackdrop,
            interactionLayerBlock = topActionElasticState.layerBlock,
            interactionState = topActionElasticState,
            containerColor = AppSurfaceTokens.surfaceContainerHigh(),
            pressProgress = 0f,
            shape = edgeButtonShape,
            lensIntensity = resolveFloatingDockGeometryScale(
                resolveHomeTopSettingsButtonSize(topChromePolicy).value,
            ),
            liquidGlassTuning = liquidGlassTuning,
        )
    } else {
        Modifier.homeTopChromeSurface(
            renderMode = if (appThemeConfig.liquidGlassEnabled || useTopControlBlur) {
                HomeTopChromeRenderMode.BLUR
            } else {
                searchChromeRenderMode
            },
            shape = edgeButtonShape,
            surfaceColor = resolveHomeTopEdgeControlContainerColor(
                isLightMode = isLightMode,
                renderMode = if (appThemeConfig.liquidGlassEnabled || useTopControlBlur) {
                    HomeTopChromeRenderMode.BLUR
                } else {
                    searchChromeRenderMode
                },
            ),
            hazeState = hazeState,
            miuixBackdrop = topActionBackdrop,
            liquidStyle = liquidStyle,
            liquidGlassTuning = liquidGlassTuning,
            liquidGlassPreset = bottomBarLiquidGlassPreset,
            motionTier = motionTier,
            isScrolling = topChromeMotionPolicy.isScrolling,
            isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget,
        )
    }
    // 搜索栏液态玻璃必须复用顶部标签 dock 的材质链，避免单独的搜索胶囊渲染分支产生质感偏差。
    val useBottomBarMatchedTopControls = resolveHomeTopSearchLiquidGlassEnabled(homeSettings)
    val localTopChromeRenderMode = resolveHomeTopLocalChromeRenderMode(
        renderMode = topChromeRenderMode,
        usesNativeContainerTreatment = usesNativeContainerTreatment,
    )
    val localTabChromeRenderMode = resolveHomeTopLocalChromeRenderMode(
        renderMode = tabChromeRenderMode,
        usesNativeContainerTreatment = usesNativeContainerTreatment,
    )
    val continuousSlabRenderMode = resolveHomeTopContinuousSlabRenderMode(
        renderMode = topChromeRenderMode,
    )

    // 顶栏与嵌入页使用连续偏移，避免慢拖时按固定 dp 阶梯跳动。
    val currentHeaderOffsetProvider by rememberUpdatedState(headerOffsetProvider)
    val headerOffset by remember {
        derivedStateOf {
            currentHeaderOffsetProvider()
        }
    }

    val hideTopTabs = homeSettings?.hideTopTabs == true
    val bottomBarSearchEnabled = homeSettings?.isBottomBarSearchEnabled == true &&
        homeSettings?.keepHomeTopSearchWithBottomSearch != true
    val topSearchMetrics = resolveHomeTopSearchRowMetrics(
        configuredHeight = resolveHomeTopSearchBarHeight(topChromePolicy),
        configuredTabsSpacing = resolveHomeTopSearchToTabsSpacing(topChromePolicy),
        bottomBarSearchEnabled = homeSettings?.isBottomBarSearchEnabled == true,
        hideTopTabs = hideTopTabs,
        keepTopSearch = homeSettings?.keepHomeTopSearchWithBottomSearch == true,
    )
    val searchBarHeightDp = topSearchMetrics.height
    val topTabLabelMode = homeSettings?.topTabLabelMode
        ?: com.android.purebilibili.core.store.SettingsManager.TopTabLabelMode.TEXT_ONLY
    val tabRowHeightDp = if (hideTopTabs) {
        AppSpacingTokens.None
    } else {
        resolveHomeTopTabRowHeight(
            isTabFloating = isTabFloating,
            chromePolicy = topChromePolicy,
            labelMode = topTabLabelMode
        )
    }
    val searchCollapseDistanceDp = com.android.purebilibili.feature.home.resolveHomeTopSearchScrollDistance(
        searchBarHeight = searchBarHeightDp,
        searchToTabsSpacing = topSearchMetrics.tabsSpacing,
        extraSpacing = resolveHomeTopSearchCollapseExtraSpacing(topChromePolicy),
        hideTopTabs = hideTopTabs,
    )
    val searchBarHeightPx = with(density) { searchBarHeightDp.toPx() }
    val searchCollapseDistancePx = with(density) { searchCollapseDistanceDp.toPx() }
    val tabRowHeightPx = with(density) { tabRowHeightDp.toPx() }

    val scrollLayout = remember(
        headerOffset,
        searchBarHeightPx,
        searchCollapseDistancePx,
        tabRowHeightPx,
        isHeaderCollapseEnabled
    ) {
        resolveHomeHeaderScrollLayout(
            headerOffsetPx = headerOffset,
            searchBarHeightPx = searchBarHeightPx,
            searchCollapseDistancePx = searchCollapseDistancePx,
            tabRowHeightPx = tabRowHeightPx,
            isHeaderCollapseEnabled = isHeaderCollapseEnabled,
        )
    }
    val currentSearchHeight = with(density) { scrollLayout.searchBarHeightPx.toDp() }
    val expandedTabHeight = with(density) { scrollLayout.tabRowHeightPx.toDp() }
    val currentTabHeight by animateDpAsState(
        targetValue = if (hideTopTabs) {
            AppSpacingTokens.None
        } else {
            resolveHomeTopTabPresentationHeight(
                expandedHeight = expandedTabHeight,
                isCollapsed = topTabsVisible && topTabsCollapsed,
                collapsedHandleHeight = if (isHeaderCollapseEnabled || isTopTabsAutoCollapseEnabled) {
                    AppSpacingTokens.None
                } else {
                    resolveHomeTopCollapsedHandleHeight()
                }
            )
        },
        animationSpec = AppMotionTokens.standardSpec(),
        label = "currentTabHeight"
    )
    val tabAlpha = scrollLayout.tabAlpha
    val searchRevealFraction = if (searchBarHeightPx > 0f) {
        (scrollLayout.searchBarHeightPx / searchBarHeightPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    // 裁剪留在固定外层；只移动完整高度的行，避免搜索控件被父约束压扁。
    val searchContentTranslationProvider = rememberUpdatedState {
        resolveHomeTopSearchContentTranslationYPx(
            searchRevealFraction = searchRevealFraction,
            searchBarHeightPx = searchBarHeightPx,
        )
    }
    val integratedCollapsedTopBar = shouldUseIntegratedCollapsedHomeTopBar(
        searchRevealFraction = searchRevealFraction,
        presentation = topChromePolicy.tabPresentation,
    )
    val unifiedPanelCornerRadius = resolveHomeTopUnifiedPanelCornerRadius(
        chromePolicy = topChromePolicy,
        collapsedIntoStatusBar = integratedCollapsedTopBar
    )
    val unifiedPanelShape = if (unifiedPanelCornerRadius == AppSpacingTokens.None) {
        androidx.compose.ui.graphics.RectangleShape
    } else {
        RoundedCornerShape(unifiedPanelCornerRadius)
    }
    val unifiedPanelHorizontalPadding = resolveHomeTopUnifiedPanelHorizontalPadding(topChromePolicy)
    val unifiedPanelInnerPadding = resolveHomeTopUnifiedPanelInnerPadding(
        chromePolicy = topChromePolicy,
        collapsedIntoStatusBar = integratedCollapsedTopBar
    )
    val searchToTabsSpacing = topSearchMetrics.tabsSpacing
    val currentSearchToTabsSpacing = searchToTabsSpacing * searchRevealFraction
    val currentUnifiedDividerBottomSpacing = AppSpacingTokens.ExtraSmall * searchRevealFraction

    val tabHorizontalPadding by animateDpAsState(
        targetValue = resolveHomeTopTabHorizontalPadding(
            isTabFloating = isTabFloating,
            chromePolicy = topChromePolicy,
        ),
        animationSpec = AppMotionTokens.standardSpec(),
        label = "tabHorizontalPadding"
    )
    val tabVerticalPadding by animateDpAsState(
        targetValue = resolveHomeTopTabVerticalPaddingDp(isTabFloating).dp,
        animationSpec = AppMotionTokens.standardSpec(),
        label = "tabVerticalPadding"
    )
    val tabVerticalOffset by animateDpAsState(
        targetValue = resolveHomeTopTabYOffsetDp(isTabFloating).dp,
        animationSpec = AppMotionTokens.standardSpec(),
        label = "tabVerticalOffset"
    )
    val tabShadowElevation by animateDpAsState(
        targetValue = if (useDetachedTopTabDock && usesNativeContainerTreatment) {
            // Native (MIUIX) detached capsule dock always lifts a hair so the long pill
            // reads as a floating segment over the feed below.
            AppSpacingTokens.Small
        } else if (usesNativeContainerTreatment) {
            AppSpacingTokens.None
        } else if (isTabFloating) {
            AppSpacingTokens.Small
        } else {
            AppSpacingTokens.None
        },
        animationSpec = AppMotionTokens.standardSpec(),
        label = "tabShadowElevation"
    )
    val effectiveTabShadowElevation = if (interactionBudget == HomeInteractionMotionBudget.REDUCED) AppSpacingTokens.None else tabShadowElevation
    val tabOverlayAlpha = resolveHomeTopTabOverlayAlpha(
        materialMode = effectiveTabMaterialMode,
        isTabFloating = isTabFloating,
        containerAlpha = tabChromeColors.containerColor.alpha
    )
    val tabContentAlpha by animateFloatAsState(
        targetValue = if (topTabsVisible && !topTabsCollapsed) 1f else 0f,
        animationSpec = AppMotionTokens.standardSpec(),
        label = "tabContentAlpha"
    )
    val effectiveContinuousSlabRenderMode = resolveEffectiveHomeTopContinuousSlabRenderMode(
        renderMode = continuousSlabRenderMode,
        liquidGlassEnabled = isGlassEnabled || topChromeLiquidGlassEnabled,
        integratedCollapsedTopBar = integratedCollapsedTopBar,
    )
    val effectiveTopPanelChromeRenderMode = if (integratedCollapsedTopBar) {
        HomeTopChromeRenderMode.PLAIN
    } else {
        topPanelChromeRenderMode
    }
    val useUnifiedLiquidChrome = embedTopTabsInUnifiedPanel &&
        (
            effectiveTopPanelChromeRenderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
                effectiveTopPanelChromeRenderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
        )
    val unifiedLocalTabChromeRenderMode = resolveHomeTopUnifiedLocalTabChromeRenderMode(
        renderMode = tabChromeRenderMode,
        usesNativeContainerTreatment = usesNativeContainerTreatment,
        usesTonalContainerTreatment = usesTonalContainerTreatment,
    )
    val effectiveTabChromeRenderMode = if (useUnifiedTopPanel) {
        resolveHomeTopUnifiedTabChromeRenderMode(
            localTabChromeRenderMode = unifiedLocalTabChromeRenderMode,
            usesTonalContainerTreatment = usesTonalContainerTreatment,
            useUnifiedLiquidChrome = useUnifiedLiquidChrome
        )
    } else {
        localTabChromeRenderMode
    }
    val topTabDockChromeRenderMode = resolveHomeTopTabDockChromeRenderMode(
        detachedTopTabDock = useDetachedTopTabDock,
        localTabChromeRenderMode = unifiedLocalTabChromeRenderMode,
        hasHazeState = hazeState != null,
    )
    val topTabDockHazeState = hazeState.takeIf {
        shouldApplyHomeTopTabDockHaze(
            embeddedInUnifiedPanel = embedTopTabsInUnifiedPanel,
            continuousSlabRenderMode = continuousSlabRenderMode,
        )
    }
    val effectiveTabSurfaceColor = if (useDetachedTopTabDock) {
        resolveHomeTopDetachedTabDockSurfaceColor(
            isLightMode = isLightMode,
            renderMode = topTabDockChromeRenderMode
        )
    } else if (useUnifiedTopPanel) {
        resolveHomeTopUnifiedTabSurfaceColor(
            tabContainerColor = tabChromeColors.containerColor,
            tabOverlayAlpha = tabOverlayAlpha,
            usesTonalContainerTreatment = usesTonalContainerTreatment,
            useUnifiedLiquidChrome = useUnifiedLiquidChrome,
            tabChromeRenderMode = effectiveTabChromeRenderMode
        )
    } else {
        tabSurfaceColor.copy(alpha = tabOverlayAlpha)
    }
    val renderUnifiedTopPanelChrome = embedTopTabsInUnifiedPanel && shouldRenderHomeTopUnifiedPanelChrome(
        searchHeightDp = currentSearchHeight.value,
        tabHeightDp = currentTabHeight.value,
        integratedCollapsedTopBar = integratedCollapsedTopBar
    )
    val drawUnifiedTopPanelChrome =
        renderUnifiedTopPanelChrome && effectiveTopPanelChromeRenderMode != HomeTopChromeRenderMode.PLAIN
    val drawTopSearchDivider =
        !hideTopTabs &&
            useUnifiedTopPanel &&
            shouldShowUnifiedHomeTopPanelDivider(topChromePolicy) &&
            drawUnifiedTopPanelChrome &&
            currentSearchHeight > AppSpacingTokens.None &&
            searchRevealFraction > 0f
    val topTabLiquidGlassEnabled = resolveHomeTopChromeLiquidGlassEnabled(homeSettings)
    // 移动胶囊只绘制选中项，标签轨道直接承接顶部连续模糊层，避免搜索框下方
    // 再出现一整块高对比的白色 dock。其余 presentation 仍保留独立轨道以保证可读性。
    val drawTopTabDockChrome = drawTopTabOuterChromeSurface
    val useTopTabBottomBarMatchedDock = drawTopTabDockChrome
    val topTabInnerOwnsFloatingDockShell =
        useTopTabBottomBarMatchedDock || topTabLiquidGlassEnabled
    // Floating dock shell + tabs share one wrap decision so glass length matches content.
    val wrapTopTabDockFloatingStyle = if (embedTopTabsInUnifiedPanel) false else isTabFloating
    val wrapTopTabDockHasOuterChrome = drawTopTabDockChrome && !embedTopTabsInUnifiedPanel
    val wrapTopTabDockWidth = shouldWrapTopTabDockWidth(
        isFloatingStyle = wrapTopTabDockFloatingStyle,
        hasOuterChromeSurface = wrapTopTabDockHasOuterChrome,
        edgeToEdge = integratedCollapsedTopBar
    )
    val currentTabToSearchSpacing = if (hideTopTabs) {
        AppSpacingTokens.None
    } else {
        currentSearchToTabsSpacing + if (drawTopSearchDivider) {
            AppSpacingTokens.Micro / 2 + currentUnifiedDividerBottomSpacing
        } else {
            AppSpacingTokens.None
        }
    }
    val isProgressiveBlurRequested = progressiveTopBlurEnabled
    val isProgressiveBlurEligible = shouldUseBiliPaiProgressiveTopBlur(
        enabled = isProgressiveBlurRequested && !isHeaderBlurEnabled,
        hasBackdrop = true,
        blurRadiusDp = liquidGlassTuning.progressiveBlurRadius,
    ) && !isLowBlurBudgetForced(forceLowBlurBudget)
    val isProgressiveBlurActive = isProgressiveBlurEligible && miuixBackdrop != null
    val isProgressiveFadeActive = progressiveTopFadeEnabled && !isHeaderBlurEnabled
    val topChromeWallpaperPalette = if (!hasIllustratedHeader) {
        currentTopChromeWallpaperPalette(MaterialTheme.colorScheme.background)
    } else null
    val wallpaperTopReadability = topChromeWallpaperPalette?.readability?.top
    val wallpaperProtectionActive = isProgressiveBlurActive && wallpaperTopReadability != null
    // 仅调整渐进模糊/纯色渐隐的保护层，关闭特效后的原生容器保持原有材质。
    val topFadeSurfaceColor = resolveTopChromeFadeColor(
        surfaceColor = MaterialTheme.colorScheme.background,
        foregroundColor = topForegroundColor,
        blurred = isProgressiveBlurActive,
        backgroundColor = topChromeWallpaperPalette?.topColor,
        readabilityBand = wallpaperTopReadability,
    )

    val pinnedChromeLayout = resolveHomeTopPinnedChromeLayout(
        statusBarHeight = statusBarHeight,
        visibleSearchHeight = currentSearchHeight,
        tabRowHeight = currentTabHeight,
        searchToTabsSpacing = currentTabToSearchSpacing,
        renderMode = if (isProgressiveBlurRequested || isProgressiveFadeActive) HomeTopChromeRenderMode.BLUR else effectiveContinuousSlabRenderMode,
        // 连续背景始终覆盖顶部 Dock；独立轨道只负责自身材质与前景可读性。
        includeTabInBlur = true,
    )
    val progressiveBlurBottomExtension = resolveProgressiveTopBlurBottomExtension(
        // 背景首次录制就绪前也保留有效特效的几何范围，避免渐隐高度随后跳变。
        enabled = isProgressiveBlurEligible ||
            (!isGlassEnabled && !topChromeLiquidGlassEnabled && isProgressiveFadeActive),
        endFraction = if (isProgressiveBlurEligible) liquidGlassTuning.progressiveBlurEndFraction else 1f,
    )
    val baseContinuousSlabHeight = pinnedChromeLayout.blurHeight + progressiveBlurBottomExtension
    val pinnedChromeContentHeight = pinnedChromeLayout.tabTop + currentTabHeight
    val isTopTabViewportSyncEnabled = resolveHomeTopTabViewportSyncEnabled(
        currentTabHeightDp = currentTabHeight.value,
        tabAlpha = tabAlpha,
        tabContentAlpha = tabContentAlpha
    )
    val tabBorderAlpha = if (isTabFloating) tabChromeStyle.borderAlpha else 0f
    val topLayoutOrder = homeSettings?.homeTopLayoutOrder ?: HomeTopLayoutOrder.SEARCH_THEN_TABS
    val nativeProgressiveLayout = !isGlassEnabled && !topChromeLiquidGlassEnabled &&
        (isProgressiveBlurEligible || isProgressiveFadeActive)
    val nativeProgressiveProtection = nativeProgressiveLayout &&
        (isProgressiveBlurActive || isProgressiveFadeActive)
    val panelVerticalInset = if (embedTopTabsInUnifiedPanel && renderUnifiedTopPanelChrome) {
        unifiedPanelInnerPadding
    } else 0.dp
    val protectionTabTop = statusBarHeight + panelVerticalInset +
        (if (topLayoutOrder == HomeTopLayoutOrder.SEARCH_THEN_TABS) {
            currentSearchHeight + currentTabToSearchSpacing
        } else 0.dp) + (if (embedTopTabsInUnifiedPanel) 0.dp else tabVerticalOffset)
    val protectionTabBottom = protectionTabTop + currentTabHeight
    val protectionContentBottom = maxOf(
        pinnedChromeContentHeight + panelVerticalInset * 2,
        protectionTabBottom,
    )
    val continuousSlabHeight = if (nativeProgressiveLayout) {
        maxOf(baseContinuousSlabHeight, protectionContentBottom + 32.dp)
    } else baseContinuousSlabHeight
    val protectionHeight = maxOf(continuousSlabHeight, protectionContentBottom + 32.dp)
    val tabProtectionVisibility = if (!hideTopTabs && expandedTabHeight > 0.dp) {
        (tabAlpha * tabContentAlpha * (currentTabHeight / expandedTabHeight).coerceIn(0f, 1f)).coerceIn(0f, 1f)
    } else 0f
    // 只有非玻璃渐进顶栏限制模糊清晰端，分类保护另由遮罩承担；玻璃继续使用原始参数。
    // 不依赖分类显隐插值，收起分类或交换搜索/分类顺序后仍在实际控件底部归零。
    val continuousSlabTuning = if (nativeProgressiveLayout) {
        val gradient = homeTopReadabilityBlurGradient(
            gradient = ProgressiveBlur.Top.copy(
                startFraction = liquidGlassTuning.progressiveBlurStartFraction,
                endFraction = liquidGlassTuning.progressiveBlurEndFraction,
                curve = liquidGlassTuning.progressiveBlurCurve,
            ),
            contentBottomFraction = (protectionContentBottom / continuousSlabHeight).coerceIn(0f, 1f),
        )
        liquidGlassTuning.copy(
            progressiveBlurStartFraction = gradient.startFraction,
            progressiveBlurEndFraction = gradient.endFraction,
        )
    } else liquidGlassTuning
    val topTabsContent: @Composable (Dp) -> Unit = { maxDockWidth ->
        val renderTabs: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxWidth()) {
            uiSkinDecoration?.topTabBackgroundImagePath?.let { tabBackground ->
                AsyncImage(
                    model = File(tabBackground),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier.matchParentSize().clearAndSetSemantics {},
                )
            }
            HomeTopTabChrome(
                currentTabHeight = currentTabHeight,
                tabAlpha = tabAlpha,
                tabContentAlpha = tabContentAlpha,
                // The illustrated tab background is a sibling behind this chrome.
                containerZIndex = if (hasIllustratedHeader || useUnifiedTopPanel) 0f else -1f,
                // 分栏 dock 最大宽度 = 顶部三控件合计宽度，保证左右对齐。
                maxDockWidth = maxDockWidth,
                tabHorizontalPadding = if (embedTopTabsInUnifiedPanel) {
                    resolveNonNegativeHomeTopPadding(resolveHomeTopEmbeddedTabHorizontalPadding(topChromePolicy))
                } else {
                    resolveNonNegativeHomeTopPadding(tabHorizontalPadding)
                },
                tabVerticalPadding = if (embedTopTabsInUnifiedPanel || topTabInnerOwnsFloatingDockShell) {
                    AppSpacingTokens.None
                } else {
                    resolveNonNegativeHomeTopPadding(tabVerticalPadding)
                },
                tabVerticalOffset = if (embedTopTabsInUnifiedPanel) AppSpacingTokens.None else tabVerticalOffset,
                isTabFloating = if (hasIllustratedHeader || embedTopTabsInUnifiedPanel) false else isTabFloating,
                effectiveTabShadowElevation = if (embedTopTabsInUnifiedPanel) AppSpacingTokens.None else effectiveTabShadowElevation,
                tabShape = if (useUnifiedTopPanel) {
                    resolveSharedBottomBarCapsuleShape()
                } else {
                    tabShape
                },
                tabChromeRenderMode = if (hasIllustratedHeader) {
                    HomeTopChromeRenderMode.PLAIN
                } else if (useTopTabBottomBarMatchedDock) {
                    topTabDockChromeRenderMode
                } else {
                    effectiveTabChromeRenderMode
                },
                tabSurfaceColor = if (hasIllustratedHeader) Color.Transparent else effectiveTabSurfaceColor,
                hazeState = topTabDockHazeState,
                miuixBackdrop = miuixBackdrop,
                liquidStyle = liquidStyle,
                liquidGlassTuning = liquidGlassTuning,
                liquidGlassPreset = bottomBarLiquidGlassPreset,
                motionTier = motionTier,
                isScrolling = tabChromeMotionPolicy.isScrolling,
                isTransitionRunning = tabChromeMotionPolicy.isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget,
                preferFlatGlass = !embedTopTabsInUnifiedPanel,
                tabBorderAlpha = if (embedTopTabsInUnifiedPanel) {
                    0f
                } else {
                    tabBorderAlpha
                },
                tabHighlightColor = Color.Transparent,
                tabContentUnderlayColor = if (hasIllustratedHeader || embedTopTabsInUnifiedPanel) {
                    Color.Transparent
                } else {
                    resolveHomeTopInnerUnderlayColor(
                        isLightMode = isLightMode,
                        renderMode = tabChromeRenderMode,
                        softenWideChrome = true
                    )
                },
                gestureEnabled = topTabsVisible &&
                    isTopTabsManualCollapseEnabled &&
                    !isHeaderCollapseEnabled &&
                    !isTopTabsAutoCollapseEnabled,
                isTabsCollapsed = topTabsCollapsed,
                onTabsCollapsedChange = onTopTabsCollapsedChange,
                drawChromeSurface = !hasIllustratedHeader && shouldHomeTopTabChromeDrawOuterShell(
                    drawOuterChrome = drawTopTabDockChrome,
                    innerOwnsFloatingDock = topTabInnerOwnsFloatingDockShell,
                ),
                useBottomBarMatchedSurface = !hasIllustratedHeader && useTopTabBottomBarMatchedDock,
                drawMatchedShellLens = topTabLiquidGlassEnabled,
                matchedShellLensIntensity = resolveFloatingDockGeometryScale(
                    currentTabHeight.value
                ),
                // Floating / matched dock: length follows icon+text × tab count (no full-bleed empty glass).
                wrapDockWidth = !hasIllustratedHeader && wrapTopTabDockWidth,
                dockCategoryCount = topCategories.size,
                dockLabelMode = topTabLabelMode,
            ) {
                CategoryTabRow(
                    categories = topCategories,
                    categoryKeys = topCategoryKeys,
                    selectedIndex = categoryIndex,
                    onCategorySelected = { index ->
                        if (topTabsVisible) onCategorySelected(index)
                    },
                    onCategoryReselected = onCategoryReselected,
                    onPartitionClick = {
                        if (topTabsVisible) onPartitionClick()
                    },
                    pagerState = pagerState,
                    labelMode = if (hasIllustratedHeader) 2 else topTabLabelMode,
                    isLiquidGlassEnabled = resolveHomeTopTabIndicatorLiquidGlassEnabled(
                        homeSettings = homeSettings,
                    ),
                    liquidGlassStyle = liquidStyle,
                    liquidGlassTuning = liquidGlassTuning,
                    liquidGlassPreset = bottomBarLiquidGlassPreset,
                    hazeState = hazeState,
                    miuixBackdrop = miuixBackdrop,
                    skinPlainStyle = hasIllustratedHeader,
                    skinPlainContentColor = uiSkinDecoration?.takeIf { hasIllustratedHeader }?.let {
                        resolveHomeSkinTopTabContentColor(
                            topAtmosphereTint = it.topAtmosphereTint,
                            hasTopAtmosphereImage = true,
                            darkTheme = !isLightMode,
                        )
                    },
                    isFloatingStyle = !hasIllustratedHeader && isTabFloating,
                    edgeToEdge = integratedCollapsedTopBar,
                    hasOuterChromeSurface = !hasIllustratedHeader && drawTopTabDockChrome,
                    // Same wrap decision as HomeTopTabChrome so shell length matches tab content.
                    wrapDockWidth = !hasIllustratedHeader && wrapTopTabDockWidth,
                    interactionBudget = interactionBudget,
                    motionTier = motionTier,
                    isTransitionRunning = isTransitionRunning,
                    forceLowBlurBudget = forceLowBlurBudget,
                    isViewportSyncEnabled = isTopTabViewportSyncEnabled,
                    maxDockWidthDp = maxDockWidth.value,
                    forceMaterialUnderline = useLegacyHomeTopTabs &&
                        LocalAppUiStyle.current == AppUiStyle.MATERIAL3
                )
            }
        }
        }

        if (bottomBarSearchEnabled && !hideTopTabs) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentTabHeight)
                    .padding(horizontal = AppSpacingTokens.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .appElasticPress(visualSize = resolveHomeTopAvatarInnerSize())
                        .appActionClickable { performHomeTopBarTap(haptic = haptic, onClick = onAvatarClick) }
                        .semantics { contentDescription = "个人中心" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.size(resolveHomeTopAvatarInnerSize()).clip(CircleShape),
                    ) {
                        HomeTopAvatarContent(
                            user = user,
                            shape = CircleShape,
                            fallbackBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    renderTabs()
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .appElasticPress(
                            visualSize = resolveHomeTopSettingsButtonSize(topChromePolicy),
                            state = topActionElasticState,
                            transformInBackdrop = topActionGlassActive,
                        )
                        .appActionClickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Button,
                        ) {
                            performHomeTopBarTap(haptic = haptic, onClick = onTopRightActionClick)
                        }
                        .semantics { contentDescription = topRightActionContentDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(resolveHomeTopSettingsButtonSize(topChromePolicy))
                            .then(topActionSurfaceModifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIcon(
                            imageVector = topRightActionIcon,
                            contentDescription = null,
                            tint = topForegroundColor,
                            modifier = Modifier.size(resolveHomeTopSettingsIconSize(topChromePolicy)),
                        )
                    }
                    if (topRightUnreadBadge != null) {
                        HomeTopUnreadBadge(
                            text = topRightUnreadBadge,
                            layout = topRightUnreadBadgeLayout,
                            borderColor = AppSurfaceTokens.cardContainer(),
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }
                }
            }
        } else {
            renderTabs()
        }
    }

    HomeHeaderControlBounds(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(10f)
    ) {
        val fullTopDockWidth = maxWidth
        // Plain/native tabs align to the top controls. The bottom-bar-backed dock uses the
        // full parent width so its width resolver receives the same constraint as the bottom dock.
        val topControlsContentWidth = resolveHomeTopControlsContentWidthDp(
            containerWidthDp = maxWidth,
            chromePolicy = topChromePolicy
        )
        val shouldRenderContinuousSlab = effectiveContinuousSlabRenderMode != HomeTopChromeRenderMode.PLAIN ||
            isProgressiveBlurActive ||
            isProgressiveFadeActive
        if (shouldRenderContinuousSlab) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(continuousSlabHeight)
                    .homeTopChromeSurface(
                        renderMode = if (isProgressiveBlurActive || isProgressiveFadeActive) HomeTopChromeRenderMode.BLUR else effectiveContinuousSlabRenderMode,
                        shape = resolveHomeTopContinuousSlabShape(),
                        surfaceColor = resolveHomeTopContinuousSlabSurfaceColor(
                            baseColor = headerChromeColors.containerColor,
                            blurAlpha = backgroundAlpha,
                            usesNativeContainerTreatment = usesNativeContainerTreatment,
                            renderMode = effectiveContinuousSlabRenderMode
                        ),
                        hazeState = hazeState,
                        miuixBackdrop = miuixBackdrop,
                        liquidStyle = liquidStyle,
                        liquidGlassTuning = continuousSlabTuning,
                        liquidGlassPreset = bottomBarLiquidGlassPreset,
                        motionTier = motionTier,
                        isScrolling = topChromeMotionPolicy.isScrolling,
                        isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                        forceLowBlurBudget = forceLowBlurBudget,
                        useProgressiveTopBlur = isProgressiveBlurRequested,
                        useProgressiveTopFade = isProgressiveFadeActive,
                    )
            ) {
                if (isProgressiveFadeActive || wallpaperProtectionActive) {
                    TopSolidProgressiveFadeOverlay(
                        surfaceColor = topFadeSurfaceColor,
                        fadeHeight = continuousSlabHeight,
                        protectedFraction = if (wallpaperTopReadability != null) 0.65f else 0f,
                    )
                }
            }
        }
        val nativeTopProtectionAlpha = resolveHomeNativeTopProtectionAlpha(
            liquidGlassActive = isGlassEnabled || topChromeLiquidGlassEnabled,
            blurActive = isHeaderBlurEnabled || isProgressiveBlurActive,
            progressiveChromeActive = isProgressiveBlurActive || isProgressiveFadeActive,
            contentRevealFraction = if (isProgressiveBlurActive || isProgressiveFadeActive) {
                maxOf(
                    searchRevealFraction,
                    if (expandedTabHeight > 0.dp) {
                        (currentTabHeight / expandedTabHeight).coerceIn(0f, 1f)
                    } else 0f,
                )
            } else 1f,
        )
        // 非玻璃渐进顶栏按控件位置保护分类文字，尾部渐隐；普通磨砂与无特效仍用原来的实色遮罩。
        if (nativeTopProtectionAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (nativeProgressiveProtection) protectionHeight else pinnedChromeContentHeight)
                    .then(
                        if (nativeProgressiveProtection) {
                            Modifier.homeTopReadabilityProtection(
                                color = MaterialTheme.colorScheme.background,
                                contentBottom = protectionContentBottom,
                                tabTop = protectionTabTop,
                                tabBottom = protectionTabBottom,
                                baseAlpha = nativeTopProtectionAlpha,
                                tabVisibility = tabProtectionVisibility,
                            )
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.background.copy(alpha = nativeTopProtectionAlpha))
                        }
                    )
            )
        }
        // The skin head artwork belongs to the complete pinned header, not only the
        // search/tabs panel. Drawing it here lets the same crop continue behind the
        // transparent status bar while the controls remain layered above it.
        if (!topTrimImagePath.isNullOrBlank()) {
            AsyncImage(
                model = File(topTrimImagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pinnedChromeContentHeight)
                    .clearAndSetSemantics {}
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pinnedChromeContentHeight)
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.72f to Color.Transparent,
                            1.00f to (uiSkinDecoration?.topAtmosphereTint
                                ?: headerChromeColors.containerColor).copy(alpha = 0.42f),
                        )
                    )
                    .clearAndSetSemantics {}
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = AppSpacingTokens.None) // Reset padding, controlled by spacer
        ) {
            // 1. Status Bar Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                haptic(HapticType.LIGHT)
                                onStatusBarDoubleTap()
                            }
                        )
                    }
            )

            // 2. Search Bar + Avatar + right action
            // The panel material is a sibling behind the controls: clipping a material ancestor
            // also clips elastic avatars/actions even when the search row is fully expanded.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (embedTopTabsInUnifiedPanel) {
                            Modifier.padding(horizontal = unifiedPanelHorizontalPadding)
                        } else Modifier
                    )
            ) {
                if (embedTopTabsInUnifiedPanel && drawUnifiedTopPanelChrome) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .homeTopChromeSurface(
                                renderMode = effectiveTopPanelChromeRenderMode,
                                shape = unifiedPanelShape,
                                surfaceColor = headerChromeColors.containerColor,
                                hazeState = hazeState,

                                miuixBackdrop = miuixBackdrop,

                                liquidStyle = liquidStyle,
                                liquidGlassTuning = liquidGlassTuning,
                                liquidGlassPreset = bottomBarLiquidGlassPreset,
                                motionTier = motionTier,
                                isScrolling = topChromeMotionPolicy.isScrolling,
                                isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                forceLowBlurBudget = forceLowBlurBudget,
                                preferFlatGlass = resolveHomeTopWideChromePreferFlatGlass(
                                    effectiveTopPanelChromeRenderMode
                                )
                            )
                            .then(
                                if (
                                    drawUnifiedTopPanelChrome &&
                                    !integratedCollapsedTopBar &&
                                    !useUnifiedLiquidChrome
                                ) {
                                    Modifier.border(AppSpacingTokens.Micro * 0.4f, headerChromeColors.borderColor, unifiedPanelShape)
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
                if (
                    drawUnifiedTopPanelChrome &&
                    useUnifiedTopPanel &&
                    !integratedCollapsedTopBar &&
                    !useUnifiedLiquidChrome
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                resolveHomeTopUnifiedPanelReadabilityColor(
                                    isLightMode = isLightMode,
                                    renderMode = effectiveTopPanelChromeRenderMode
                                ),
                                shape = unifiedPanelShape
                            )
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (embedTopTabsInUnifiedPanel) {
                                Modifier.padding(
                                    horizontal = if (integratedCollapsedTopBar) AppSpacingTokens.None else unifiedPanelInnerPadding,
                                    vertical = if (renderUnifiedTopPanelChrome) {
                                        unifiedPanelInnerPadding
                                    } else {
                                        AppSpacingTokens.None
                                    }
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (!hideTopTabs && topLayoutOrder == HomeTopLayoutOrder.TABS_THEN_SEARCH) {
                        topTabsContent(
                            if (topTabInnerOwnsFloatingDockShell || useLegacyHomeTopTabs) {
                                fullTopDockWidth
                            } else {
                                topControlsContentWidth
                            }
                        )
                        if (drawTopSearchDivider) {
                            Spacer(modifier = Modifier.height(currentSearchToTabsSpacing))
                            AppHorizontalDivider(
                                thickness = AppSpacingTokens.Micro / 2,
                                color = headerChromeColors.borderColor.copy(
                                    alpha = resolveHomeTopUnifiedPanelDividerAlpha(topChromeRenderMode) *
                                        searchRevealFraction
                                )
                            )
                            Spacer(modifier = Modifier.height(currentUnifiedDividerBottomSpacing))
                        } else {
                            Spacer(modifier = Modifier.height(currentSearchToTabsSpacing))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(currentSearchHeight)
                            .then(
                                // Only crop while collapsing the search row. At full height,
                                // avatar/action deformation must be allowed outside its layout bounds.
                                if (searchRevealFraction < 1f) {
                                    Modifier.clip(androidx.compose.ui.graphics.RectangleShape)
                                } else Modifier
                            )
                            .then(
                                if (currentSearchHeight == AppSpacingTokens.None) {
                                    Modifier.clearAndSetSemantics {}
                                } else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                                .height(searchBarHeightDp)
                                .graphicsLayer {
                                    translationY = searchContentTranslationProvider.value()
                                }
                                .padding(
                                    horizontal = if (embedTopTabsInUnifiedPanel) {
                                        AppSpacingTokens.None
                                    } else {
                                        resolveHomeTopSearchRowHorizontalPadding(topChromePolicy)
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(resolveHomeTopAvatarOuterSize())
                                    .appElasticPress(visualSize = resolveHomeTopAvatarInnerSize())
                                    .appActionClickable {
                                        performHomeTopBarTap(haptic = haptic, onClick = onAvatarClick)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(resolveHomeTopAvatarInnerSize())
                                        .then(
                                            if (useBottomBarMatchedTopControls) {
                                                Modifier
                                            } else {
                                                Modifier.clip(edgeButtonShape)
                                            }
                                        )
                                        .then(
                                            if (useBottomBarMatchedTopControls) {
                                                Modifier.homeTopBottomBarMatchedSurface(
                                                    renderMode = searchChromeRenderMode,
                                                    shape = edgeButtonShape,
                                                    hazeState = hazeState,
                                                    miuixBackdrop = miuixBackdrop,
                                                    liquidGlassStyle = liquidStyle,
                                                    liquidGlassTuning = liquidGlassTuning,
                                                    liquidGlassPreset = bottomBarLiquidGlassPreset,
                                                    motionTier = motionTier,
                                                    isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                    forceLowBlurBudget = forceLowBlurBudget,
                                                    drawShellLens = true,
                                                    shellLensIntensity = resolveFloatingDockGeometryScale(
                                                        resolveHomeTopAvatarInnerSize().value
                                                    ),
                                                    isScrolling = topChromeMotionPolicy.isScrolling
                                                )
                                            } else if (useUnifiedTopPanel) {
                                                if (useUnifiedLiquidChrome) {
                                                    Modifier
                                                        .homeTopChromeSurface(
                                                            renderMode = localTopChromeRenderMode,
                                                            shape = edgeButtonShape,
                                                            surfaceColor = headerChromeColors.containerColor,
                                                            hazeState = hazeState,

                                                            miuixBackdrop = miuixBackdrop,

                                                            liquidStyle = liquidStyle,
                                                            liquidGlassTuning = liquidGlassTuning,
                                                            liquidGlassPreset = bottomBarLiquidGlassPreset,
                                                            motionTier = motionTier,
                                                            isScrolling = topChromeMotionPolicy.isScrolling,
                                                            isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                            forceLowBlurBudget = forceLowBlurBudget
                                                        )
                                                } else {
                                                    Modifier.border(
                                                        width = AppSpacingTokens.Micro * 0.4f,
                                                        color = headerChromeColors.borderColor.copy(alpha = 0.7f),
                                                        shape = edgeButtonShape
                                                    )
                                                }
                                            } else {
                                                Modifier
                                                    .homeTopChromeSurface(
                                                        renderMode = localTopChromeRenderMode,
                                                        shape = edgeButtonShape,
                                                        surfaceColor = headerChromeColors.containerColor,
                                                        hazeState = hazeState,

                                                        miuixBackdrop = miuixBackdrop,

                                                        liquidStyle = liquidStyle,
                                                        liquidGlassTuning = liquidGlassTuning,
                                                        liquidGlassPreset = bottomBarLiquidGlassPreset,
                                                        motionTier = motionTier,
                                                        isScrolling = topChromeMotionPolicy.isScrolling,
                                                        isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                        forceLowBlurBudget = forceLowBlurBudget
                                                    )
                                                    .border(AppSpacingTokens.Micro / 2, headerChromeColors.borderColor, edgeButtonShape)
                                            }
                                        )
                                ) {
                                    HomeTopAvatarContent(
                                        user = user,
                                        shape = edgeButtonShape,
                                        fallbackBackgroundColor = if (useUnifiedTopPanel) {
                                            if (useUnifiedLiquidChrome) {
                                                Color.Transparent
                                            } else {
                                                topForegroundColor.copy(alpha = 0.10f)
                                            }
                                        } else {
                                            headerChromeColors.containerColor
                                        },
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(resolveHomeTopEdgeControlGap(topChromePolicy)))

                            // Search placement only hides the middle pill. With tabs hidden,
                            // this row still owns the avatar and settings in both materials.
                            if (bottomBarSearchEnabled) {
                                Spacer(modifier = Modifier.weight(1f))
                            } else {
                                BottomBarMatchedReusableLiquidDock(
                                    shape = resolveHomeTopSearchContainerShape(topChromePolicy),
                                    modifier = Modifier
                                        .weight(1f)
                                        .widthIn(max = AppSpacingTokens.TripleExtraLarge * 13 + AppSpacingTokens.Large)
                                        // 搜索胶囊与头像/消息按钮共用 36dp 边缘控件高度；关闭液态玻璃时
                                        // 不再回退到库组件原生高度（48/56dp）。
                                        .height(resolveHomeTopEdgeControlHeight())
                                        .then(
                                            if (useTopControlBlur && !useBottomBarMatchedTopControls) {
                                                Modifier.homeTopChromeSurface(
                                                    renderMode = HomeTopChromeRenderMode.BLUR,
                                                    shape = resolveHomeTopSearchContainerShape(topChromePolicy),
                                                    surfaceColor = resolveHomeTopUnifiedSearchContainerColor(
                                                        isLightMode = isLightMode,
                                                        renderMode = HomeTopChromeRenderMode.BLUR,
                                                    ),
                                                    hazeState = hazeState,
                                                    miuixBackdrop = miuixBackdrop,
                                                    liquidStyle = liquidStyle,
                                                    liquidGlassTuning = liquidGlassTuning,
                                                    liquidGlassPreset = bottomBarLiquidGlassPreset,
                                                    motionTier = motionTier,
                                                    isScrolling = topChromeMotionPolicy.isScrolling,
                                                    isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                    forceLowBlurBudget = forceLowBlurBudget,
                                                )
                                            } else {
                                                Modifier
                                            }
                                        ),
                                    backdrop = miuixBackdrop,
                                    reuseEnabled = true,
                                    elasticFeedbackEnabled = true,
                                    elasticFeedbackStrength = com.android.purebilibili.core.ui.components.APP_SEARCH_ELASTIC_FEEDBACK_STRENGTH,
                                    liquidGlassEffectsEnabled =
                                        searchChromeMaterialMode == TopTabMaterialMode.LIQUID_GLASS,
                                    useNeutralLiquidContainer = true,
                                    drawShellLens = true,
                                    shellLensIntensity = resolveFloatingDockGeometryScale(
                                        resolveHomeTopEdgeControlHeight().value
                                    ),
                                    isScrollInProgressProvider = { topChromeMotionPolicy.isScrolling },
                                    // 此入口只有文字和图标，不消费外壳背景；原生回退不受影响。
                                    captureShellForContent = false,
                                ) { liquidChromeActive ->
                                    if (liquidChromeActive) {
                                        val searchPillInteractionSource = remember { MutableInteractionSource() }
                                        val searchPillPressed by searchPillInteractionSource.collectIsPressedAsState()
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .appActionClickable(
                                                    interactionSource = searchPillInteractionSource,
                                                    role = Role.Button,
                                                ) {
                                                    haptic(HapticType.LIGHT)
                                                    onSearchClick()
                                                }
                                                .padding(horizontal = resolveHomeTopSearchContentHorizontalPadding(topChromePolicy)),
                                            contentAlignment = Alignment.CenterStart,
                                        ) {
                                            HomeTopSearchPillContent(
                                                searchIcon = topActionIcons.search,
                                                contentColor = topForegroundColor,
                                                textFontSize = MaterialTheme.typography.bodyLarge.fontSize,
                                                iconTextGap = resolveHomeTopSearchIconTextGap(topChromePolicy),
                                                modifier = Modifier.fillMaxWidth(),
                                                pressed = searchPillPressed,
                                                searchIconOpticalScale = searchIconOpticalScale,
                                            )
                                        }
                                    } else {
                                        AppSearchEntry(
                                            onClick = {
                                                haptic(HapticType.LIGHT)
                                                onSearchClick()
                                            },
                                            placeholder = "搜索视频、UP主...",
                                            centeredContent = true,
                                            containerColor = if (useTopControlBlur) {
                                                Color.Transparent
                                            } else {
                                                Color.Unspecified
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(resolveHomeTopEdgeControlGap(topChromePolicy)))

                            val topRightActionButtonSize = resolveHomeTopSettingsButtonSize(topChromePolicy)
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(
                                        resolveHomeTopRightActionSlotWidth(
                                            buttonSize = topRightActionButtonSize,
                                            badgeLayout = topRightUnreadBadgeLayout,
                                            hasUnreadBadge = topRightUnreadBadge != null
                                        )
                                    )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .size(topRightActionButtonSize)
                                        .appElasticPress(
                                            visualSize = topRightActionButtonSize,
                                            state = topActionElasticState,
                                            transformInBackdrop = topActionGlassActive,
                                        )
                                        .then(topActionSurfaceModifier)
                                        .appActionClickable(
                                            interactionSource = null,
                                            indication = null,
                                            role = Role.Button,
                                        ) {
                                            performHomeTopBarTap(haptic = haptic, onClick = onTopRightActionClick)
                                        }
                                        .semantics {
                                            contentDescription = topRightActionContentDescription
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Keep the glyph inside the moving shell. Sampling an unmoving
                                    // exported glyph layer shifts/crops it as the consumer stretches.
                                    AppIcon(
                                        topRightActionIcon,
                                        contentDescription = null,
                                        tint = if (hasIllustratedHeader) {
                                            OpticalContrastPalette.Highlight
                                        } else if (isLightMode) {
                                            topForegroundColor
                                        } else {
                                            topForegroundColor.copy(alpha = topActionIconAlpha)
                                        },
                                        modifier = Modifier.size(
                                            resolveHomeTopSettingsIconSize(topChromePolicy)
                                        ),
                                    )
                                }
                                if (topRightUnreadBadge != null) {
                                    HomeTopUnreadBadge(
                                        text = topRightUnreadBadge,
                                        layout = topRightUnreadBadgeLayout,
                                        borderColor = AppSurfaceTokens.cardContainer(),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(
                                                x = topRightUnreadBadgeLayout.offsetX,
                                                y = topRightUnreadBadgeLayout.offsetY
                                            )
                                    )
                                }
                            }
                        }
                    }

                    if (!hideTopTabs && topLayoutOrder == HomeTopLayoutOrder.SEARCH_THEN_TABS) {
                        if (drawTopSearchDivider) {
                            Spacer(modifier = Modifier.height(currentSearchToTabsSpacing))
                            AppHorizontalDivider(
                                thickness = AppSpacingTokens.Micro / 2,
                                color = headerChromeColors.borderColor.copy(
                                    alpha = resolveHomeTopUnifiedPanelDividerAlpha(topChromeRenderMode) *
                                        searchRevealFraction
                                )
                            )
                            Spacer(modifier = Modifier.height(currentUnifiedDividerBottomSpacing))
                        } else {
                            Spacer(modifier = Modifier.height(currentSearchToTabsSpacing))
                        }

                        topTabsContent(
                            if (topTabInnerOwnsFloatingDockShell || useLegacyHomeTopTabs) {
                                fullTopDockWidth
                            } else {
                                topControlsContentWidth
                            }
                        )
                    }
                }
            }
        }
    }
}
