// 文件路径: feature/home/components/iOSHomeHeader.kt
package com.android.purebilibili.feature.home.components

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.backdrops.LayerBackdrop
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.iOSTapEffect
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.feature.home.UserState
import com.android.purebilibili.core.theme.iOSSystemGray
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
import com.android.purebilibili.core.ui.effect.liquidGlassBackground
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.rememberAppInboxIcon
import com.android.purebilibili.core.ui.rememberAppSettingsIcon
import com.android.purebilibili.core.store.HomeHeaderBlurMode
import com.android.purebilibili.core.store.HomeSettings
import com.android.purebilibili.core.store.HomeTopLayoutOrder
import com.android.purebilibili.core.store.HomeTopRightAction
import com.android.purebilibili.feature.home.resolveHomeTopCategories
import com.android.purebilibili.feature.home.resolveHomeTopCollapsedHandleHeight
import com.android.purebilibili.feature.home.resolveHomeTopTabPresentationHeight
import com.android.purebilibili.feature.home.HomeGlassResolvedColors
import com.android.purebilibili.feature.home.rememberHomeGlassChromeColors
import com.android.purebilibili.feature.home.rememberHomeGlassPillColors
import com.android.purebilibili.feature.home.resolveHomeGlassChromeStyle
import com.android.purebilibili.feature.home.resolveHomeGlassPillStyle
import com.android.purebilibili.core.store.resolveHomeHeaderBlurEnabled
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.LocalAndroidNativeVariant
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.feature.home.LocalHomeScrollOffset
import com.android.purebilibili.navigation.resolveAppNavigationAppearance
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import com.android.purebilibili.core.ui.blur.shouldAllowRuntimeShaderBackedHazeEffect
import java.io.File

private const val HOME_HEADER_LIQUID_GLASS_ALPHA = 0.10f

internal data class HomeTopChromeMotionPolicy(
    val isScrolling: Boolean,
    val isTransitionRunning: Boolean
)

internal data class HomeTopLinkedBottomBarAppearance(
    val isFloating: Boolean,
    val blurEnabled: Boolean,
    val liquidGlassEnabled: Boolean
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
        return Color.White.copy(alpha = 0.98f)
    }
    return if (topAtmosphereTint.luminance() < 0.72f) {
        Color.White.copy(alpha = 0.98f)
    } else {
        Color(0xFF111820).copy(alpha = 0.96f)
    }
}

internal fun resolveHomeSkinTopTabUnselectedContentColor(contentColor: Color): Color =
    contentColor.copy(alpha = if (contentColor.luminance() > 0.5f) 0.84f else 0.78f)

internal fun shouldUseHomeSkinPlainTopTabs(uiSkinDecoration: HomeUiSkinDecoration?): Boolean =
    false

internal fun resolveHomeSkinTopTabIndicatorColor(contentColor: Color): Color =
    contentColor.copy(alpha = maxOf(contentColor.alpha, 0.92f))

internal fun resolveHomeSkinTopTabRowHeight(): Dp = 46.dp

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
    uiPreset: UiPreset,
    androidNativeVariant: AndroidNativeVariant
): HomeTopLinkedBottomBarAppearance {
    val resolvedHomeSettings = homeSettings ?: HomeSettings()
    val navigationAppearance = resolveAppNavigationAppearance(
        homeSettings = resolvedHomeSettings,
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant
    )
    return HomeTopLinkedBottomBarAppearance(
        isFloating = navigationAppearance.bottomBarFloating,
        blurEnabled = navigationAppearance.bottomBarBlurEnabled,
        liquidGlassEnabled = resolvedHomeSettings.isTopBarLiquidGlassEnabled
    )
}

internal fun formatHomeTopRightUnreadBadge(
    action: HomeTopRightAction,
    unreadCount: Int
): String? {
    if (action != HomeTopRightAction.INBOX || unreadCount <= 0) return null
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
        offsetX = 0.dp,
        offsetY = 0.dp,
        reservedEndWidth = 9.dp,
        minWidth = 18.dp,
        minHeight = 18.dp,
        horizontalPadding = 5.dp,
        verticalPadding = 1.dp
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
    val badgeText = formatHomeTopRightUnreadBadge(action, unreadCount) ?: return action.label
    return "${action.label}，$badgeText 条未读"
}

internal fun resolveHomeTopChromeLiquidGlassEnabled(
    homeSettings: HomeSettings?,
    uiPreset: UiPreset
): Boolean {
    val resolvedHomeSettings = homeSettings ?: HomeSettings()
    return resolvedHomeSettings.isTopBarLiquidGlassEnabled
}

internal fun resolveHomeTopTabIndicatorLiquidGlassEnabled(
    homeSettings: HomeSettings?,
    uiPreset: UiPreset
): Boolean {
    val resolvedHomeSettings = homeSettings ?: HomeSettings()
    if (resolveHomeTopChromeLiquidGlassEnabled(resolvedHomeSettings, uiPreset)) {
        return true
    }
    return uiPreset == UiPreset.MD3 && resolvedHomeSettings.androidNativeLiquidGlassEnabled
}

internal fun resolveHomeTopSearchLiquidGlassEnabled(
    homeSettings: HomeSettings?,
    uiPreset: UiPreset
): Boolean {
    val resolvedHomeSettings = homeSettings ?: HomeSettings()
    return resolvedHomeSettings.isHomeSearchLiquidGlassEnabled
}

internal fun resolveHomeTopChromeMaterialMode(
    isHeaderBlurEnabled: Boolean,
    isBottomBarBlurEnabled: Boolean,
    isLiquidGlassEnabled: Boolean,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): TopTabMaterialMode {
    return when {
        isLiquidGlassEnabled -> TopTabMaterialMode.LIQUID_GLASS
        !isHeaderBlurEnabled && !isBottomBarBlurEnabled -> TopTabMaterialMode.PLAIN
        else -> TopTabMaterialMode.BLUR
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

internal fun shouldDrawHomeTopSearchLegacyHighlight(
    uiPreset: UiPreset,
    useUnifiedTopPanel: Boolean,
    renderMode: HomeTopChromeRenderMode,
    refractionOverlayAlpha: Float
): Boolean {
    if (uiPreset != UiPreset.IOS || useUnifiedTopPanel) return false
    if (refractionOverlayAlpha > 0f) return false
    return renderMode != HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP &&
        renderMode != HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
}

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
    return if (isTabFloating) 2f else 0f
}

internal fun resolveNonNegativeHomeTopPadding(padding: Dp): Dp = padding.coerceAtLeast(0.dp)

internal fun resolveHomeTopTabYOffsetDp(isTabFloating: Boolean): Float {
    return if (isTabFloating) (-4f) else 0f
}

internal fun resolveHomeTopSearchBarHeight(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).searchBarHeight
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
    renderMode: HomeTopChromeRenderMode
): HomeTopPinnedChromeLayout {
    val visibleSearchBlockHeight = if (visibleSearchHeight > 0.dp) {
        searchToTabsSpacing + visibleSearchHeight
    } else {
        0.dp
    }
    val visibleChromeHeight = statusBarHeight + tabRowHeight + visibleSearchBlockHeight
    return HomeTopPinnedChromeLayout(
        tabTop = statusBarHeight + visibleSearchBlockHeight,
        searchTop = statusBarHeight,
        blurHeight = if (renderMode == HomeTopChromeRenderMode.PLAIN) 0.dp else visibleChromeHeight
    )
}

internal fun resolveHomeTopSearchRevealDeadZone(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).searchRevealDeadZone
}

internal fun resolveHomeTopVisibleSearchHeightPx(
    rawSearchHeightPx: Float,
    fullSearchHeightPx: Float,
    revealDeadZonePx: Float
): Float {
    if (fullSearchHeightPx <= 0f) return 0f
    val clampedRawHeight = rawSearchHeightPx.coerceIn(0f, fullSearchHeightPx)
    val clampedDeadZone = revealDeadZonePx.coerceIn(0f, fullSearchHeightPx - 0.5f)
    if (clampedDeadZone <= 0f) return clampedRawHeight
    if (clampedRawHeight <= clampedDeadZone) return 0f
    val normalizedFraction = (clampedRawHeight - clampedDeadZone) / (fullSearchHeightPx - clampedDeadZone)
    return (normalizedFraction * fullSearchHeightPx).coerceIn(0f, fullSearchHeightPx)
}

internal fun usesImmediateHomeTopSearchReveal(
    revealDeadZonePx: Float
): Boolean = revealDeadZonePx <= 0.01f

internal fun resolveHomeTopSearchContentRevealFraction(
    searchRevealFraction: Float,
    usesImmediateReveal: Boolean
): Float {
    val clampedFraction = searchRevealFraction.coerceIn(0f, 1f)
    if (!usesImmediateReveal) return clampedFraction
    return (clampedFraction * (0.72f + 0.28f * clampedFraction)).coerceIn(0f, 1f)
}

internal fun resolveHomeTopSearchContentTranslationYPx(
    searchRevealFraction: Float,
    searchBarHeightPx: Float,
    usesImmediateReveal: Boolean
): Float {
    if (!usesImmediateReveal || searchBarHeightPx <= 0f) return 0f
    val clampedFraction = searchRevealFraction.coerceIn(0f, 1f)
    val maxShiftPx = minOf(searchBarHeightPx * 0.18f, 10f)
    return -maxShiftPx * (1f - clampedFraction)
}

internal fun resolveHomeHeaderScrollLayout(
    headerOffsetPx: Float,
    searchBarHeightPx: Float,
    searchCollapseDistancePx: Float,
    tabRowHeightPx: Float,
    isHeaderCollapseEnabled: Boolean,
    searchRevealDeadZonePx: Float = 0f,
    usesImmediateSearchReveal: Boolean = false
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
    val clampedOffsetPx = headerOffsetPx.coerceIn(-effectiveCollapseDistancePx, 0f)
    val currentSearchHeightPx = resolveHomeTopVisibleSearchHeightPx(
        rawSearchHeightPx = searchBarHeightPx + clampedOffsetPx,
        fullSearchHeightPx = searchBarHeightPx,
        revealDeadZonePx = searchRevealDeadZonePx
    )
    val rawSearchRevealFraction = if (searchBarHeightPx > 0f) {
        (currentSearchHeightPx / searchBarHeightPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val searchAlpha = resolveHomeTopSearchContentRevealFraction(
        searchRevealFraction = rawSearchRevealFraction,
        usesImmediateReveal = usesImmediateSearchReveal
    )
    return HomeHeaderScrollLayout(
        searchBarHeightPx = currentSearchHeightPx,
        searchAlpha = searchAlpha,
        tabRowHeightPx = tabRowHeightPx,
        tabAlpha = 1f
    )
}

internal fun resolveHomeTopTabRowHeight(
    isTabFloating: Boolean,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    labelMode: Int = com.android.purebilibili.core.store.SettingsManager.TopTabLabelMode.TEXT_ONLY
): Dp {
    val style = resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode)
    return if (isTabFloating) {
        style.tabRowHeightFloating
    } else {
        style.tabRowHeightDocked
    }
}

internal fun resolveHomeTopSearchRowHorizontalPadding(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).searchRowHorizontalPadding
}

internal fun resolveHomeTopSearchPillHeight(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).searchPillHeight
}

internal fun resolveHomeTopSearchContentHorizontalPadding(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).searchContentHorizontalPadding
}

internal fun resolveHomeTopSearchIconTextGap(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).searchIconTextGap
}

internal fun resolveHomeTopSearchContainerShape(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Shape {
    if (uiPreset == UiPreset.IOS) return resolveSharedBottomBarCapsuleShape()
    return AppShapes.resolveContainerShape(
        level = ContainerLevel.Pill,
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant
    )
}

internal fun resolveHomeTopEdgeButtonShape(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Shape {
    return if (uiPreset == UiPreset.IOS) {
        CircleShape
    } else {
        AppShapes.resolveContainerShape(
            level = ContainerLevel.Dialog,
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant
        )
    }
}

internal fun resolveHomeTopAvatarOuterSize(): Dp = 40.dp

internal fun resolveHomeTopAvatarInnerSize(): Dp = 40.dp

internal fun resolveHomeTopSettingsButtonSize(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).actionButtonSizeDocked
    } else {
        40.dp
    }
}

internal fun resolveHomeTopSettingsIconSize(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).actionIconSizeDocked
    } else {
        20.dp
    }
}

internal fun resolveHomeTopEdgeControlGap(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).edgeControlGap
}

internal fun shouldUseUnifiedHomeTopPanel(uiPreset: UiPreset = UiPreset.IOS): Boolean {
    return resolveHomeTopPresetStyle(
        uiPreset = uiPreset,
        androidNativeVariant = AndroidNativeVariant.MATERIAL3,
        labelMode = 2
    ).useUnifiedPanel
}

internal fun shouldUseDetachedHomeTopTabDock(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Boolean {
    return uiPreset == UiPreset.IOS ||
        (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX)
}

internal fun resolveHomeTopUnifiedPanelHorizontalPadding(uiPreset: UiPreset = UiPreset.IOS): Dp {
    return resolveHomeTopPresetStyle(
        uiPreset = uiPreset,
        androidNativeVariant = AndroidNativeVariant.MATERIAL3,
        labelMode = 2
    ).unifiedPanelHorizontalPadding
}

internal fun resolveHomeTopUnifiedPanelInnerPadding(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    collapsedIntoStatusBar: Boolean = false
): Dp {
    if (collapsedIntoStatusBar) return 2.dp
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).unifiedPanelInnerPadding
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
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    collapsedIntoStatusBar: Boolean = false
): Dp {
    if (collapsedIntoStatusBar) return 0.dp
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).unifiedPanelCornerRadius
}

internal fun resolveHomeTopReservedContentBottomGap(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).reservedContentBottomGap
}

internal fun resolveHomeTopEmbeddedTabHorizontalPadding(uiPreset: UiPreset = UiPreset.IOS): Dp {
    return resolveHomeTopPresetStyle(
        uiPreset = uiPreset,
        androidNativeVariant = AndroidNativeVariant.MATERIAL3,
        labelMode = 2
    ).embeddedTabHorizontalPadding
}

internal fun resolveHomeTopTabHorizontalPadding(
    isTabFloating: Boolean,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    val style = resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2)
    return if (isTabFloating) {
        style.tabHorizontalPaddingFloating
    } else {
        style.tabHorizontalPaddingDocked
    }
}

internal fun resolveHomeTopSearchToTabsSpacing(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).searchToTabsSpacing
}

internal fun resolveHomeTopSearchCollapseExtraSpacing(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).searchCollapseExtraSpacing
}

internal fun resolveHomeTopSearchCollapseDistance(
    searchBarHeight: Dp,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return searchBarHeight +
        resolveHomeTopSearchToTabsSpacing(uiPreset, androidNativeVariant) +
        resolveHomeTopSearchCollapseExtraSpacing(uiPreset, androidNativeVariant)
}

internal fun shouldUseIntegratedCollapsedHomeTopBar(
    searchRevealFraction: Float,
    uiPreset: UiPreset = UiPreset.IOS
): Boolean {
    return uiPreset == UiPreset.IOS && searchRevealFraction <= 0.02f
}

internal fun resolveHomeTopContinuousSlabOverlap(
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    return resolveHomeTopPresetStyle(uiPreset, androidNativeVariant, labelMode = 2).continuousSlabOverlap
}

internal fun resolveHomeTopContinuousSlabShape(uiPreset: UiPreset = UiPreset.IOS): Shape {
    return RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
}

internal fun resolveHomeTopReservedListPadding(
    statusBarHeight: Dp,
    searchBarHeight: Dp,
    tabRowHeight: Dp,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Dp {
    val useUnifiedPanel = shouldUseUnifiedHomeTopPanel(uiPreset)
    val reservedListGap = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
        4.dp
    } else {
        0.dp
    }
    val chromeHeight = if (useUnifiedPanel) {
        searchBarHeight +
            tabRowHeight +
            (resolveHomeTopUnifiedPanelInnerPadding(uiPreset, androidNativeVariant) * 2) +
            resolveHomeTopSearchToTabsSpacing(uiPreset, androidNativeVariant) +
            reservedListGap
    } else {
        searchBarHeight + resolveHomeTopSearchToTabsSpacing(uiPreset, androidNativeVariant) + tabRowHeight
    }
    return statusBarHeight + chromeHeight
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
    uiPreset: UiPreset = UiPreset.IOS
): HomeTopChromeRenderMode {
    return when (renderMode) {
        HomeTopChromeRenderMode.BLUR -> HomeTopChromeRenderMode.BLUR
        else -> HomeTopChromeRenderMode.PLAIN
    }
}

internal fun resolveHomeTopContinuousSlabHeight(
    statusBarHeight: Dp,
    searchBarHeight: Dp,
    tabRowHeight: Dp,
    renderMode: HomeTopChromeRenderMode,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    hasVisibleTopContent: Boolean = true
): Dp {
    return resolveHomeTopPinnedChromeLayout(
        statusBarHeight = statusBarHeight,
        visibleSearchHeight = if (hasVisibleTopContent) searchBarHeight else 0.dp,
        tabRowHeight = if (hasVisibleTopContent) tabRowHeight else 0.dp,
        searchToTabsSpacing = 0.dp,
        renderMode = renderMode
    ).blurHeight
}

internal fun resolveHomeTopContinuousSlabSurfaceColor(
    baseColor: Color,
    blurAlpha: Float,
    uiPreset: UiPreset = UiPreset.IOS,
    renderMode: HomeTopChromeRenderMode
): Color {
    if (renderMode == HomeTopChromeRenderMode.PLAIN) return Color.Transparent
    if (renderMode != HomeTopChromeRenderMode.BLUR) {
        return baseColor.copy(alpha = maxOf(baseColor.alpha, blurAlpha))
    }
    return if (uiPreset == UiPreset.MD3) {
        baseColor.copy(alpha = maxOf(baseColor.alpha, blurAlpha))
    } else {
        Color.Transparent
    }
}

internal fun resolveHomeTopPanelChromeRenderMode(
    renderMode: HomeTopChromeRenderMode,
    uiPreset: UiPreset = UiPreset.IOS,
    useUnifiedPanel: Boolean = false
): HomeTopChromeRenderMode {
    if (useUnifiedPanel) return HomeTopChromeRenderMode.PLAIN
    return resolveHomeTopLocalChromeRenderMode(
        renderMode = renderMode,
        uiPreset = uiPreset
    )
}

internal fun resolveHomeTopSearchChromeRenderMode(
    renderMode: HomeTopChromeRenderMode,
    uiPreset: UiPreset = UiPreset.IOS,
    useUnifiedPanel: Boolean = false,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): HomeTopChromeRenderMode {
    if (useUnifiedPanel) {
        if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
            return when (renderMode) {
                HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
                HomeTopChromeRenderMode.LIQUID_GLASS_HAZE,
                HomeTopChromeRenderMode.BLUR -> renderMode
                HomeTopChromeRenderMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
            }
        }
        return when (renderMode) {
            HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP,
            HomeTopChromeRenderMode.LIQUID_GLASS_HAZE,
            HomeTopChromeRenderMode.BLUR -> renderMode
            HomeTopChromeRenderMode.PLAIN -> HomeTopChromeRenderMode.PLAIN
        }
    }
    return resolveHomeTopLocalChromeRenderMode(
        renderMode = renderMode,
        uiPreset = uiPreset
    )
}

internal fun resolveHomeTopUnifiedTabChromeRenderMode(
    localTabChromeRenderMode: HomeTopChromeRenderMode,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    useUnifiedLiquidChrome: Boolean
): HomeTopChromeRenderMode {
    if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
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
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): HomeTopChromeRenderMode {
    if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
        return resolveHomeTopLocalChromeRenderMode(
            renderMode = renderMode,
            uiPreset = uiPreset
        )
    }
    // 统一面板关闭外层 slab 后，标签行需要保留自己的模糊承托区域。
    if (renderMode == HomeTopChromeRenderMode.BLUR) {
        return HomeTopChromeRenderMode.BLUR
    }
    return resolveHomeTopLocalChromeRenderMode(
        renderMode = renderMode,
        uiPreset = uiPreset
    )
}

internal fun resolveHomeTopUnifiedTabSurfaceColor(
    tabContainerColor: Color,
    tabOverlayAlpha: Float,
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    useUnifiedLiquidChrome: Boolean,
    tabChromeRenderMode: HomeTopChromeRenderMode = HomeTopChromeRenderMode.PLAIN
): Color {
    if (uiPreset == UiPreset.MD3 && androidNativeVariant == AndroidNativeVariant.MIUIX) {
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
        Color.White.copy(alpha = alpha)
    } else {
        Color.Black.copy(alpha = alpha)
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
        Color.White.copy(alpha = alpha)
    } else {
        Color.Black.copy(alpha = alpha)
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
            Color.Black.copy(alpha = 0.14f)
        } else {
            Color.White.copy(alpha = 0.22f)
        }
    }
    if (renderMode == HomeTopChromeRenderMode.BLUR) {
        return if (isLightMode) {
            Color.White.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.18f)
        }
    }
    return if (isLightMode) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color.White.copy(alpha = 0.12f)
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
        Color.White.copy(alpha = alpha)
    } else {
        Color.Black.copy(alpha = alpha)
    }
}

internal fun resolveHomeTopEdgeControlBorderColor(
    isLightMode: Boolean,
    renderMode: HomeTopChromeRenderMode
): Color {
    return when (renderMode) {
        HomeTopChromeRenderMode.PLAIN -> if (isLightMode) {
            Color.Black.copy(alpha = 0.12f)
        } else {
            Color.White.copy(alpha = 0.20f)
        }
        HomeTopChromeRenderMode.BLUR -> if (isLightMode) {
            Color.White.copy(alpha = 0.16f)
        } else {
            Color.White.copy(alpha = 0.16f)
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
        Color.White.copy(alpha = alpha)
    } else {
        Color.Black.copy(alpha = alpha)
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
    uiPreset: UiPreset = UiPreset.IOS
): HomeTopChromeRenderMode {
    if (uiPreset == UiPreset.MD3 && renderMode == HomeTopChromeRenderMode.BLUR) {
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
    isTransitionRunning: Boolean
): Boolean {
    if (!hasHeaderBlur) return false
    if (topTabMaterialMode == TopTabMaterialMode.PLAIN) return false
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
        Color.Black
    } else {
        Color.White.copy(alpha = 0.92f)
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
        Color.White.copy(alpha = alpha)
    } else {
        Color.Black.copy(alpha = (alpha * 0.72f).coerceAtLeast(0.05f))
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
        borderColor = Color.White.copy(alpha = colors.borderColor.alpha * 0.48f),
        highlightColor = Color.White.copy(alpha = colors.highlightColor.alpha * 0.28f)
    )
}

internal fun resolveHomeTopContainerColors(
    uiPreset: UiPreset,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3,
    emphasized: Boolean,
    blurEnabled: Boolean,
    fallbackColors: HomeGlassResolvedColors,
    surfaceContainerColor: Color,
    surfaceContainerHighColor: Color,
    outlineVariantColor: Color
): HomeGlassResolvedColors {
    if (uiPreset != UiPreset.MD3) return fallbackColors
    if (blurEnabled) {
        val baseColor = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
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
                    if (androidNativeVariant == AndroidNativeVariant.MIUIX) 0.16f else if (emphasized) 0.18f else 0.14f
                )
            ),
            highlightColor = Color.Transparent
        )
    }
    return HomeGlassResolvedColors(
        containerColor = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
            surfaceContainerColor
        } else if (emphasized) {
            surfaceContainerHighColor
        } else {
            surfaceContainerColor
        },
        borderColor = outlineVariantColor.copy(
            alpha = if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
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
    uiPreset: UiPreset = UiPreset.IOS,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): Boolean {
    return resolveHomeTopPresetStyle(
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant,
        labelMode = 2
    ).showUnifiedPanelDivider
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
        shape === androidx.compose.ui.graphics.RectangleShape -> RoundedCornerShape(size = 0.dp)
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
    backdrop: LayerBackdrop?,
    liquidStyle: LiquidGlassStyle,
    liquidGlassTuning: LiquidGlassTuning? = null,
    motionTier: MotionTier,
    isScrolling: Boolean,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    preferFlatGlass: Boolean = false,
    darkThemeWhiteOverlayMultiplier: Float = 0.86f
): Modifier = composed {
    val isLiquidGlassMode = renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
        renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
    val scrollState = LocalHomeScrollOffset.current
    val resolvedTuning = remember(liquidStyle, liquidGlassTuning) {
        liquidGlassTuning ?: resolveLiquidGlassTuning(liquidStyle)
    }
    val style = HomeTopChromeSurfaceStyle(
        blurSurfaceType = resolveHomeTopBlurSurfaceType(renderMode),
        preferFlatGlass = preferFlatGlass,
        depthEffect = liquidGlassTuning?.depthEffectEnabled != false,
        refractionAmountScrollMultiplier = if (isLiquidGlassMode) 0.016f else 0f,
        refractionAmountScrollCap = if (isLiquidGlassMode) 12f else 0f,
        surfaceAlphaScrollMultiplier = if (isLiquidGlassMode) 0.00012f else 0f,
        surfaceAlphaScrollCap = if (isLiquidGlassMode) 0.03f else 0f,
        darkThemeWhiteOverlayMultiplier = if (isLiquidGlassMode) {
            darkThemeWhiteOverlayMultiplier
        } else {
            1f
        },
        useTuningSurfaceAlpha = isLiquidGlassMode,
        hazeBackgroundAlphaMultiplier = 1f
    )
    val lensShape = resolveHomeTopChromeLensShape(shape)
    val surfaceTreatment = resolveHomeTopChromeSurfaceTreatment(
        renderMode = renderMode,
        preferFlatGlass = style.preferFlatGlass
    )
    val isDarkTheme = isSystemInDarkTheme()
    // [Optimization] 将滚动读取延迟到 draw 阶段:在 drawBackdrop 的 effects/onDrawSurface 与
    // liquidGlassBackground 的 graphicsLayer 内读取 LocalHomeScrollOffset,折射保持实时联动却不
    // 触发逐帧重组,同时避免"冻结滚动耦合"会在滑动停止瞬间产生的折射突跳(停止闪烁)。
    val scrollCoupledOffsetProvider: () -> Float = {
        scrollState.floatValue * resolvedTuning.scrollCoupledRefractionAmount
    }
    // 滚动无关的基线(scrollOffset = 0);滚动耦合增量在 draw 阶段按需叠加。
    val baseBackdropSpec = resolveHomeTopChromeBackdropSpec(
        tuning = resolvedTuning,
        scrollOffset = 0f,
        isDarkTheme = isDarkTheme,
        style = style
    )
    val baseSurfaceColor = resolveHomeTopChromeSurfaceColor(surfaceColor, baseBackdropSpec, style)
    // 匹配 KSU drawBackdrop 路径的模糊半径，确保 LIQUID_GLASS_HAZE 视觉强度一致
    val hazeLiquidBlurRadius = if (isLiquidGlassMode) {
        resolvedTuning.backdropBlurRadius * (0.08f + resolvedTuning.progress * 0.92f)
    } else {
        0f
    }
    // 匹配 KSU lens(refractionAmount=24) 的折射强度
    // AGSL RuntimeShader 天然比 KSU lens() 效果弱，将 refractIntensity 按比例增强
    val hazeRefractIntensity = if (renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE) {
        (resolvedTuning.refractIntensity * 4f).coerceAtMost(1f)
    } else {
        resolvedTuning.refractIntensity
    }

    when (renderMode) {
        HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP -> {
            if (surfaceTreatment == HomeTopChromeSurfaceTreatment.FLAT_GLASS && backdrop != null) {
                this.drawBackdrop(
                    backdrop = backdrop,
                    shape = { lensShape ?: shape },
                    effects = {
                        blur(
                            resolvedTuning.backdropBlurRadius *
                                (0.08f + resolvedTuning.progress * 0.92f)
                        )
                    },
                    onDrawSurface = {
                        val drawSpec = resolveHomeTopChromeBackdropSpec(
                            tuning = resolvedTuning,
                            scrollOffset = scrollCoupledOffsetProvider(),
                            isDarkTheme = isDarkTheme,
                            style = style
                        )
                        drawRect(resolveHomeTopChromeSurfaceColor(surfaceColor, drawSpec, style))
                        drawRect(Color.White.copy(alpha = drawSpec.whiteOverlayAlpha))
                    }
                )
            } else if (backdrop != null && lensShape != null) {
                this.drawBackdrop(
                    backdrop = backdrop,
                    shape = { lensShape },
                    effects = {
                        blur(
                            resolvedTuning.backdropBlurRadius *
                                (0.08f + resolvedTuning.progress * 0.92f)
                        )
                        val lensSpec = resolveHomeTopChromeBackdropSpec(
                            tuning = resolvedTuning,
                            scrollOffset = scrollCoupledOffsetProvider(),
                            isDarkTheme = isDarkTheme,
                            style = style
                        )
                        if (lensSpec.refractionAmount > 0.5f) {
                            lens(
                                refractionHeight = resolvedTuning.refractionHeight,
                                refractionAmount = lensSpec.refractionAmount,
                                depthEffect = style.depthEffect && resolvedTuning.depthEffectEnabled,
                                chromaticAberration = resolvedTuning.chromaticAberrationAmount > 0.01f
                            )
                        }
                    },
                    onDrawSurface = {
                        val drawSpec = resolveHomeTopChromeBackdropSpec(
                            tuning = resolvedTuning,
                            scrollOffset = scrollCoupledOffsetProvider(),
                            isDarkTheme = isDarkTheme,
                            style = style
                        )
                        drawRect(resolveHomeTopChromeSurfaceColor(surfaceColor, drawSpec, style))
                        drawRect(Color.White.copy(alpha = drawSpec.whiteOverlayAlpha))
                    }
                )
            } else if (backdrop != null) {
                this.drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        blur(
                            resolvedTuning.backdropBlurRadius *
                                (0.08f + resolvedTuning.progress * 0.92f)
                        )
                    },
                    onDrawSurface = {
                        val drawSpec = resolveHomeTopChromeBackdropSpec(
                            tuning = resolvedTuning,
                            scrollOffset = scrollCoupledOffsetProvider(),
                            isDarkTheme = isDarkTheme,
                            style = style
                        )
                        drawRect(resolveHomeTopChromeSurfaceColor(surfaceColor, drawSpec, style))
                        drawRect(Color.White.copy(alpha = drawSpec.whiteOverlayAlpha))
                    }
                )
            } else {
                this.background(surfaceColor, shape)
            }
        }

        HomeTopChromeRenderMode.LIQUID_GLASS_HAZE -> {
            if (hazeState != null && shouldAllowRuntimeShaderBackedHazeEffect(Build.VERSION.SDK_INT)) {
                if (surfaceTreatment == HomeTopChromeSurfaceTreatment.FLAT_GLASS) {
                    this
                        .hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                tint = null,
                                blurRadius = hazeLiquidBlurRadius.dp,
                                noiseFactor = 0f
                            )
                        ) {
                            blurredEdgeTreatment = resolveUnifiedBlurredEdgeTreatment(shape)
                        }
                        .background(baseSurfaceColor, shape)
                } else {
                    this
                        .hazeEffect(
                            state = hazeState,
                            style = HazeStyle(
                                tint = null,
                                blurRadius = hazeLiquidBlurRadius.dp,
                                noiseFactor = 0f
                            )
                        ) {
                            blurredEdgeTreatment = resolveUnifiedBlurredEdgeTreatment(shape)
                        }
                        .liquidGlassBackground(
                            refractIntensity = hazeRefractIntensity,
                            scrollOffsetProvider = scrollCoupledOffsetProvider,
                            backgroundColor = if (isLiquidGlassMode && baseBackdropSpec.whiteOverlayAlpha > 0f) {
                                val wa = baseBackdropSpec.whiteOverlayAlpha
                                // 合成白色叠加层，匹配 drawBackdrop 的 drawRect(resolvedSurfaceColor) + drawRect(Color.White(wa))
                                Color(
                                    alpha = baseSurfaceColor.alpha + wa * (1f - baseSurfaceColor.alpha),
                                    red = wa + (1f - wa) * baseSurfaceColor.red,
                                    green = wa + (1f - wa) * baseSurfaceColor.green,
                                    blue = wa + (1f - wa) * baseSurfaceColor.blue
                                )
                            } else {
                                baseSurfaceColor
                            }
                        )
                }
            } else {
                this.background(surfaceColor, shape)
            }
        }

        HomeTopChromeRenderMode.BLUR -> {
            this
                .then(
                    if (hazeState != null) {
                        Modifier.unifiedBlur(
                            hazeState = hazeState,
                            shape = shape,
                            surfaceType = style.blurSurfaceType,
                            motionTier = motionTier,
                            isScrolling = isScrolling,
                            isTransitionRunning = isTransitionRunning,
                            forceLowBudget = forceLowBlurBudget
                        )
                    } else {
                        Modifier
                    }
                )
                .background(surfaceColor, shape)
        }

        HomeTopChromeRenderMode.PLAIN -> {
            this.background(surfaceColor, shape)
        }
    }
}

/**
 *  简洁版首页头部 (带滚动隐藏/显示动画)
 * 
 *  [Refactor] 现在改为由外部通过 NestedScrollConnection 直接控制高度和透明度，
 *  实现了 1:1 的物理跟手效果，消除了漂浮感。
 */
@Composable
fun iOSHomeHeader(
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
    onPartitionClick: () -> Unit = {},  //  新增：分区按钮回调
    hazeState: HazeState? = null,  // 保留参数兼容性，但不用于模糊
    onStatusBarDoubleTap: () -> Unit = {},
    //  [新增] 下拉刷新状态
    isRefreshing: Boolean = false,
    pullProgress: Float = 0f,  // 0.0 ~ 1.0+ 下拉进度
    pagerState: androidx.compose.foundation.pager.PagerState? = null, // [New] PagerState for sync
    // [New] LayerBackdrop for liquid glass effect
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
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
    val uiPreset = LocalUiPreset.current
    val androidNativeVariant = LocalAndroidNativeVariant.current
    val shouldUseSkinPlainTopTabs = shouldUseHomeSkinPlainTopTabs(uiSkinDecoration)
    val haptic = rememberHapticFeedback()
    val density = LocalDensity.current
    val resolvedHeaderBlurMode = homeSettings?.headerBlurMode ?: HomeHeaderBlurMode.FOLLOW_PRESET
    val isHeaderBlurEnabled = remember(resolvedHeaderBlurMode, uiPreset) {
        resolveHomeHeaderBlurEnabled(
            mode = resolvedHeaderBlurMode,
            uiPreset = uiPreset
        )
    }
    val linkedBottomBarAppearance = remember(homeSettings, uiPreset, androidNativeVariant) {
        resolveHomeTopLinkedBottomBarAppearance(
            homeSettings = homeSettings,
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant
        )
    }
    val edgeButtonShape = resolveHomeTopEdgeButtonShape(uiPreset, androidNativeVariant)
    val searchContainerShape = resolveHomeTopSearchContainerShape(uiPreset, androidNativeVariant)
    val searchIcon = if (uiPreset == UiPreset.MD3) Icons.Outlined.Search else CupertinoIcons.Default.MagnifyingGlass
    val topRightAction = homeSettings?.homeTopRightAction ?: HomeTopRightAction.SETTINGS
    val settingsIcon = rememberAppSettingsIcon()
    val inboxIcon = rememberAppInboxIcon()
    val topRightActionIcon = if (topRightAction == HomeTopRightAction.INBOX) inboxIcon else settingsIcon
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
    val topChromeLiquidGlassEnabled = resolveHomeTopChromeLiquidGlassEnabled(
        homeSettings = homeSettings,
        uiPreset = uiPreset
    )

    // 状态栏高度
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    
    // [Feature] Liquid Glass Logic
    val topChromeMaterialMode = resolveHomeTopChromeMaterialMode(
        isHeaderBlurEnabled = isHeaderBlurEnabled,
        isBottomBarBlurEnabled = linkedBottomBarAppearance.blurEnabled,
        isLiquidGlassEnabled = topChromeLiquidGlassEnabled,
        androidNativeVariant = androidNativeVariant
    )
    val isGlassEnabled = topChromeMaterialMode == TopTabMaterialMode.LIQUID_GLASS
    val isTopChromeBlurEnabled = topChromeMaterialMode != TopTabMaterialMode.PLAIN
    val searchLiquidGlassEnabled = resolveHomeTopSearchLiquidGlassEnabled(
        homeSettings = homeSettings,
        uiPreset = uiPreset
    )
    val searchChromeMaterialMode = resolveHomeTopChromeMaterialMode(
        isHeaderBlurEnabled = isHeaderBlurEnabled,
        isBottomBarBlurEnabled = linkedBottomBarAppearance.blurEnabled,
        isLiquidGlassEnabled = searchLiquidGlassEnabled,
        androidNativeVariant = androidNativeVariant
    )
    val isSearchGlassEnabled = searchChromeMaterialMode == TopTabMaterialMode.LIQUID_GLASS
    val isSearchBlurEnabled = searchChromeMaterialMode != TopTabMaterialMode.PLAIN

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
    val enableTopTabSecondaryBlur = shouldEnableTopTabSecondaryBlur(
        hasHeaderBlur = hazeState != null,
        topTabMaterialMode = topChromeMaterialMode,
        isScrolling = isScrolling,
        isTransitionRunning = isTransitionRunning
    )
    val isGlassSupported = shouldAllowHomeChromeLiquidGlass(Build.VERSION.SDK_INT)
    val allowHazeLiquidGlassFallback = shouldAllowDirectHazeLiquidGlassFallback(Build.VERSION.SDK_INT)
    val liquidStyle = homeSettings?.liquidGlassStyle ?: LiquidGlassStyle.CLASSIC
    val bottomBarLiquidGlassPreset = homeSettings?.bottomBarLiquidGlassPreset
        ?: HomeSettings().bottomBarLiquidGlassPreset
    val liquidGlassTuning = remember(
        homeSettings?.liquidGlassProgress,
        liquidStyle
    ) {
        resolveLiquidGlassTuning(liquidStyle)
    }
    val topChromeRenderMode = resolveHomeTopChromeRenderMode(
        materialMode = topChromeMaterialMode,
        isGlassSupported = isGlassSupported,
        hasBackdrop = backdrop != null,
        hasHazeState = hazeState != null,
        allowHazeLiquidGlassFallback = allowHazeLiquidGlassFallback
    )
    val surfaceColor = AppSurfaceTokens.cardContainer()
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val surfaceContainerHighColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val tabShape = RoundedCornerShape(if (isTabFloating) 22.dp else 0.dp)
    val tabSurfaceColor = surfaceColor
    val isLightMode = surfaceColor.luminance() > 0.5f
    val effectiveTabMaterialMode = resolveEffectiveHomeHeaderTabMaterialMode(
        materialMode = topChromeMaterialMode,
        interactionBudget = interactionBudget
    )
    val usePlainMd3TopTabUnderline = shouldUsePlainMd3TopTabUnderline(
        uiPreset = uiPreset,
        liquidGlassEnabled = topChromeLiquidGlassEnabled
    )
    val drawTopTabOuterChromeSurface = shouldDrawHomeTopTabOuterChromeSurface(
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant,
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
        uiPreset,
        androidNativeVariant
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
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant,
            emphasized = false,
            blurEnabled = !isGlassEnabled && isTopChromeBlurEnabled,
            fallbackColors = resolved,
            surfaceContainerColor = surfaceContainerColor,
            surfaceContainerHighColor = surfaceContainerHighColor,
            outlineVariantColor = outlineVariantColor
        )
    }
    val rawSearchPillColors = tuneHomeTopGlassColors(
        colors = rememberHomeGlassPillColors(
            glassEnabled = isSearchGlassEnabled,
            blurEnabled = isSearchBlurEnabled,
            emphasized = true,
            baseColor = AppSurfaceTokens.cardContainer()
        ),
        isLightMode = isLightMode,
        emphasized = true
    )
    val searchPillColors = remember(
        rawSearchPillColors,
        isSearchGlassEnabled,
        isSearchBlurEnabled,
        blurIntensity,
        uiPreset,
        androidNativeVariant
    ) {
        val resolved = if (!isSearchGlassEnabled && isSearchBlurEnabled) {
            resolveHomeTopBlurContainerColors(
                colors = rawSearchPillColors,
                surfaceColor = surfaceColor,
                blurIntensity = blurIntensity
            )
        } else {
            rawSearchPillColors
        }
        resolveHomeTopContainerColors(
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant,
            emphasized = true,
            blurEnabled = !isSearchGlassEnabled && isSearchBlurEnabled,
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
    val searchPillStyle = remember(isSearchGlassEnabled, isSearchBlurEnabled) {
        resolveHomeGlassPillStyle(
            glassEnabled = isSearchGlassEnabled,
            blurEnabled = isSearchBlurEnabled,
            emphasized = true
        )
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
            hasBackdrop = backdrop != null,
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
    val useUnifiedTopPanel = shouldUseUnifiedHomeTopPanel(uiPreset)
    val useDetachedTopTabDock = shouldUseDetachedHomeTopTabDock(uiPreset, androidNativeVariant)
    val embedTopTabsInUnifiedPanel = useUnifiedTopPanel && !useDetachedTopTabDock
    val topPanelChromeRenderMode = resolveHomeTopPanelChromeRenderMode(
        renderMode = topChromeRenderMode,
        uiPreset = uiPreset,
        useUnifiedPanel = useUnifiedTopPanel
    )
    val searchChromeBaseRenderMode = resolveHomeTopChromeRenderMode(
        materialMode = searchChromeMaterialMode,
        isGlassSupported = isGlassSupported,
        hasBackdrop = backdrop != null,
        hasHazeState = hazeState != null,
        allowHazeLiquidGlassFallback = allowHazeLiquidGlassFallback
    )
    val searchChromeRenderMode = resolveHomeTopSearchChromeRenderMode(
        renderMode = searchChromeBaseRenderMode,
        uiPreset = uiPreset,
        useUnifiedPanel = useUnifiedTopPanel,
        androidNativeVariant = androidNativeVariant
    )
    // 搜索栏液态玻璃必须复用顶部标签 dock 的材质链，避免单独的搜索胶囊渲染分支产生质感偏差。
    val useBottomBarMatchedTopControls =
        searchChromeRenderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
            searchChromeRenderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
    val localTopChromeRenderMode = resolveHomeTopLocalChromeRenderMode(
        renderMode = topChromeRenderMode,
        uiPreset = uiPreset
    )
    val localTabChromeRenderMode = resolveHomeTopLocalChromeRenderMode(
        renderMode = tabChromeRenderMode,
        uiPreset = uiPreset
    )
    val continuousSlabRenderMode = resolveHomeTopContinuousSlabRenderMode(
        renderMode = topChromeRenderMode,
        uiPreset = uiPreset
    )

    val headerOffsetQuantizationPx = with(density) { 4.dp.toPx() }
    val currentHeaderOffsetProvider by rememberUpdatedState(headerOffsetProvider)
    val headerOffset by remember(headerOffsetQuantizationPx) {
        derivedStateOf {
            com.android.purebilibili.feature.home.policy.quantizeHomeHeaderOffset(
                offsetPx = currentHeaderOffsetProvider(),
                stepPx = headerOffsetQuantizationPx
            )
        }
    }
    
    val searchBarHeightDp = resolveHomeTopSearchBarHeight(uiPreset, androidNativeVariant)
    val tabRowHeightDp = resolveHomeTopTabRowHeight(
        isTabFloating = isTabFloating,
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant,
        labelMode = homeSettings?.topTabLabelMode
            ?: com.android.purebilibili.core.store.SettingsManager.TopTabLabelMode.TEXT_ONLY
    )
    val searchCollapseDistanceDp = resolveHomeTopSearchCollapseDistance(
        searchBarHeight = searchBarHeightDp,
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant
    )
    val searchRevealDeadZoneDp = resolveHomeTopSearchRevealDeadZone(uiPreset, androidNativeVariant)
    val searchBarHeightPx = with(density) { searchBarHeightDp.toPx() }
    val searchCollapseDistancePx = with(density) { searchCollapseDistanceDp.toPx() }
    val searchRevealDeadZonePx = with(density) { searchRevealDeadZoneDp.toPx() }
    val tabRowHeightPx = with(density) { tabRowHeightDp.toPx() }

    val scrollLayout = remember(
        headerOffset,
        searchBarHeightPx,
        searchCollapseDistancePx,
        searchRevealDeadZonePx,
        tabRowHeightPx,
        isHeaderCollapseEnabled
    ) {
        resolveHomeHeaderScrollLayout(
            headerOffsetPx = headerOffset,
            searchBarHeightPx = searchBarHeightPx,
            searchCollapseDistancePx = searchCollapseDistancePx,
            tabRowHeightPx = tabRowHeightPx,
            isHeaderCollapseEnabled = isHeaderCollapseEnabled,
            searchRevealDeadZonePx = searchRevealDeadZonePx,
            usesImmediateSearchReveal = usesImmediateHomeTopSearchReveal(searchRevealDeadZonePx)
        )
    }
    val currentSearchHeight = with(density) { scrollLayout.searchBarHeightPx.toDp() }
    val searchAlpha = scrollLayout.searchAlpha
    val expandedTabHeight = with(density) { scrollLayout.tabRowHeightPx.toDp() }
    val currentTabHeight by animateDpAsState(
        targetValue = resolveHomeTopTabPresentationHeight(
            expandedHeight = expandedTabHeight,
            isCollapsed = topTabsVisible && topTabsCollapsed,
            collapsedHandleHeight = if (isHeaderCollapseEnabled || isTopTabsAutoCollapseEnabled) {
                0.dp
            } else {
                resolveHomeTopCollapsedHandleHeight()
            }
        ),
        animationSpec = AppMotionTokens.standardSpec(),
        label = "currentTabHeight"
    )
    val tabAlpha = scrollLayout.tabAlpha
    val searchRevealFraction = if (searchBarHeightPx > 0f) {
        (scrollLayout.searchBarHeightPx / searchBarHeightPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val usesImmediateSearchReveal = remember(searchRevealDeadZonePx) {
        usesImmediateHomeTopSearchReveal(searchRevealDeadZonePx)
    }
    val searchContentRevealFraction = remember(searchRevealFraction, usesImmediateSearchReveal) {
        resolveHomeTopSearchContentRevealFraction(
            searchRevealFraction = searchRevealFraction,
            usesImmediateReveal = usesImmediateSearchReveal
        )
    }
    val searchContentTranslationYPx = remember(
        searchRevealFraction,
        searchBarHeightPx,
        usesImmediateSearchReveal
    ) {
        resolveHomeTopSearchContentTranslationYPx(
            searchRevealFraction = searchRevealFraction,
            searchBarHeightPx = searchBarHeightPx,
            usesImmediateReveal = usesImmediateSearchReveal
        )
    }
    val integratedCollapsedTopBar = shouldUseIntegratedCollapsedHomeTopBar(
        searchRevealFraction = searchRevealFraction,
        uiPreset = uiPreset
    )
    val unifiedPanelCornerRadius = resolveHomeTopUnifiedPanelCornerRadius(
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant,
        collapsedIntoStatusBar = integratedCollapsedTopBar
    )
    val unifiedPanelShape = if (unifiedPanelCornerRadius == 0.dp) {
        androidx.compose.ui.graphics.RectangleShape
    } else {
        RoundedCornerShape(unifiedPanelCornerRadius)
    }
    val unifiedPanelHorizontalPadding = resolveHomeTopUnifiedPanelHorizontalPadding(uiPreset)
    val unifiedPanelInnerPadding = resolveHomeTopUnifiedPanelInnerPadding(
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant,
        collapsedIntoStatusBar = integratedCollapsedTopBar
    )
    val searchToTabsSpacing = resolveHomeTopSearchToTabsSpacing(uiPreset, androidNativeVariant)
    val currentSearchToTabsSpacing = searchToTabsSpacing * searchContentRevealFraction
    val currentUnifiedDividerBottomSpacing = 4.dp * searchContentRevealFraction

    val tabHorizontalPadding by animateDpAsState(
        targetValue = resolveHomeTopTabHorizontalPadding(
            isTabFloating = isTabFloating,
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant
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
        targetValue = if (uiPreset == UiPreset.MD3) 0.dp else if (isTabFloating) 8.dp else 0.dp,
        animationSpec = AppMotionTokens.standardSpec(),
        label = "tabShadowElevation"
    )
    val effectiveTabShadowElevation = if (interactionBudget == HomeInteractionMotionBudget.REDUCED) 0.dp else tabShadowElevation
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
    val effectiveContinuousSlabRenderMode = if (integratedCollapsedTopBar) {
        topPanelChromeRenderMode
    } else {
        continuousSlabRenderMode
    }
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
        uiPreset = uiPreset,
        androidNativeVariant = androidNativeVariant
    )
    val effectiveTabChromeRenderMode = if (useUnifiedTopPanel) {
        resolveHomeTopUnifiedTabChromeRenderMode(
            localTabChromeRenderMode = unifiedLocalTabChromeRenderMode,
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant,
            useUnifiedLiquidChrome = useUnifiedLiquidChrome
        )
    } else {
        localTabChromeRenderMode
    }
    val topTabDockChromeRenderMode = if (
        useDetachedTopTabDock &&
        unifiedLocalTabChromeRenderMode == HomeTopChromeRenderMode.PLAIN &&
        hazeState != null
    ) {
        HomeTopChromeRenderMode.BLUR
    } else {
        unifiedLocalTabChromeRenderMode
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
            uiPreset = uiPreset,
            androidNativeVariant = androidNativeVariant,
            useUnifiedLiquidChrome = useUnifiedLiquidChrome,
            tabChromeRenderMode = effectiveTabChromeRenderMode
        )
    } else {
        tabSurfaceColor.copy(alpha = tabOverlayAlpha)
    }
    val skinTintedTabSurfaceColor = uiSkinDecoration?.topAtmosphereTint?.copy(
        alpha = effectiveTabSurfaceColor.alpha.coerceAtLeast(0.36f)
    ) ?: effectiveTabSurfaceColor
    val renderUnifiedTopPanelChrome = embedTopTabsInUnifiedPanel && shouldRenderHomeTopUnifiedPanelChrome(
        searchHeightDp = currentSearchHeight.value,
        tabHeightDp = currentTabHeight.value,
        integratedCollapsedTopBar = integratedCollapsedTopBar
    )
    val drawUnifiedTopPanelChrome =
        renderUnifiedTopPanelChrome && effectiveTopPanelChromeRenderMode != HomeTopChromeRenderMode.PLAIN
    val drawTopSearchDivider =
        useUnifiedTopPanel &&
            shouldShowUnifiedHomeTopPanelDivider(uiPreset, androidNativeVariant) &&
            drawUnifiedTopPanelChrome &&
            currentSearchHeight > 0.dp &&
            searchRevealFraction > 0f
    val useTopTabBottomBarMatchedDock =
        useUnifiedTopPanel &&
            effectiveTabMaterialMode == TopTabMaterialMode.LIQUID_GLASS &&
            (
                topTabDockChromeRenderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
                    topTabDockChromeRenderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
            )
    val drawTopTabDockChrome = drawTopTabOuterChromeSurface || useTopTabBottomBarMatchedDock || useDetachedTopTabDock
    val currentTabToSearchSpacing = currentSearchToTabsSpacing + if (drawTopSearchDivider) {
        1.dp + currentUnifiedDividerBottomSpacing
    } else {
        0.dp
    }
    val pinnedChromeLayout = resolveHomeTopPinnedChromeLayout(
        statusBarHeight = statusBarHeight,
        visibleSearchHeight = currentSearchHeight,
        tabRowHeight = currentTabHeight,
        searchToTabsSpacing = currentTabToSearchSpacing,
        renderMode = effectiveContinuousSlabRenderMode
    )
    val continuousSlabHeight = pinnedChromeLayout.blurHeight
    val isTopTabViewportSyncEnabled = resolveHomeTopTabViewportSyncEnabled(
        currentTabHeightDp = currentTabHeight.value,
        tabAlpha = tabAlpha,
        tabContentAlpha = tabContentAlpha
    )
    val tabBorderAlpha = if (isTabFloating) tabChromeStyle.borderAlpha else 0f
    val topAtmosphereImagePath = uiSkinDecoration?.topAtmosphereImagePath
    val topLayoutOrder = homeSettings?.homeTopLayoutOrder ?: HomeTopLayoutOrder.SEARCH_THEN_TABS
    val topTabsContent: @Composable () -> Unit = {
        HomeTopTabChrome(
            currentTabHeight = currentTabHeight,
            tabAlpha = tabAlpha,
            tabContentAlpha = tabContentAlpha,
            containerZIndex = if (useUnifiedTopPanel) 0f else -1f,
            tabHorizontalPadding = if (embedTopTabsInUnifiedPanel) {
                resolveNonNegativeHomeTopPadding(resolveHomeTopEmbeddedTabHorizontalPadding(uiPreset))
            } else {
                resolveNonNegativeHomeTopPadding(tabHorizontalPadding)
            },
            tabVerticalPadding = if (embedTopTabsInUnifiedPanel) {
                0.dp
            } else {
                resolveNonNegativeHomeTopPadding(tabVerticalPadding)
            },
            tabVerticalOffset = if (embedTopTabsInUnifiedPanel) 0.dp else tabVerticalOffset,
            isTabFloating = if (embedTopTabsInUnifiedPanel) false else isTabFloating,
            effectiveTabShadowElevation = if (embedTopTabsInUnifiedPanel) 0.dp else effectiveTabShadowElevation,
            tabShape = if (useUnifiedTopPanel) {
                resolveSharedBottomBarCapsuleShape()
            } else {
                tabShape
            },
            tabChromeRenderMode = if (useTopTabBottomBarMatchedDock) {
                topTabDockChromeRenderMode
            } else {
                effectiveTabChromeRenderMode
            },
            tabSurfaceColor = skinTintedTabSurfaceColor,
            hazeState = hazeState,
            backdrop = backdrop,
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
            tabHighlightColor = if (embedTopTabsInUnifiedPanel) {
                Color.Transparent
            } else {
                resolveHomeTopChromeHighlightOverlayColor(
                    baseColor = tabChromeColors.highlightColor,
                    renderMode = tabChromeRenderMode,
                    softenWideChrome = true
                )
            },
            tabContentUnderlayColor = if (embedTopTabsInUnifiedPanel) {
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
            drawChromeSurface = drawTopTabDockChrome,
            useBottomBarMatchedSurface = useTopTabBottomBarMatchedDock,
            drawMatchedShellLens = useTopTabBottomBarMatchedDock
        ) {
            CategoryTabRow(
                categories = topCategories,
                categoryKeys = topCategoryKeys,
                selectedIndex = categoryIndex,
                onCategorySelected = { index ->
                    if (topTabsVisible) onCategorySelected(index)
                },
                onPartitionClick = {
                    if (topTabsVisible) onPartitionClick()
                },
                pagerState = pagerState,
                labelMode = homeSettings?.topTabLabelMode
                    ?: com.android.purebilibili.core.store.SettingsManager.TopTabLabelMode.TEXT_ONLY,
                isLiquidGlassEnabled = resolveHomeTopTabIndicatorLiquidGlassEnabled(
                    homeSettings = homeSettings,
                    uiPreset = uiPreset
                ) && isGlassSupported,
                liquidGlassStyle = liquidStyle,
                liquidGlassTuning = liquidGlassTuning,
                hazeState = hazeState,
                backdrop = backdrop,
                isFloatingStyle = isTabFloating,
                edgeToEdge = integratedCollapsedTopBar,
                hasOuterChromeSurface = drawTopTabDockChrome,
                interactionBudget = interactionBudget,
                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget,
                isViewportSyncEnabled = isTopTabViewportSyncEnabled,
                skinPlainStyle = shouldUseSkinPlainTopTabs,
                skinPlainContentColor = null,
                topTabSkinIconPaths = uiSkinDecoration?.topTabSkinIconPaths.orEmpty(),
                partitionSkinIconPath = uiSkinDecoration?.topTabPartitionIconPath(),
                forceMaterialUnderline = usePlainMd3TopTabUnderline
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(10f)
    ) {
        if (effectiveContinuousSlabRenderMode != HomeTopChromeRenderMode.PLAIN) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(continuousSlabHeight)
                    .homeTopChromeSurface(
                        renderMode = effectiveContinuousSlabRenderMode,
                        shape = resolveHomeTopContinuousSlabShape(uiPreset),
                        surfaceColor = resolveHomeTopContinuousSlabSurfaceColor(
                            baseColor = headerChromeColors.containerColor,
                            blurAlpha = backgroundAlpha,
                            uiPreset = uiPreset,
                            renderMode = effectiveContinuousSlabRenderMode
                        ),
                        hazeState = hazeState,
                        backdrop = backdrop,
                        liquidStyle = liquidStyle,
                        liquidGlassTuning = liquidGlassTuning,
                        motionTier = motionTier,
                        isScrolling = topChromeMotionPolicy.isScrolling,
                        isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                        forceLowBlurBudget = forceLowBlurBudget
                    )
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp) // Reset padding, controlled by spacer
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
            // 高度和透明度由外部直接控制，实现物理跟手
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (embedTopTabsInUnifiedPanel) {
                            Modifier
                                .padding(horizontal = unifiedPanelHorizontalPadding)
                                .clip(unifiedPanelShape)
                                .then(
                                    if (drawUnifiedTopPanelChrome) {
                                        Modifier.homeTopChromeSurface(
                                            renderMode = effectiveTopPanelChromeRenderMode,
                                            shape = unifiedPanelShape,
                                            surfaceColor = headerChromeColors.containerColor,
                                            hazeState = hazeState,
                                            backdrop = backdrop,
                                            liquidStyle = liquidStyle,
                                            liquidGlassTuning = liquidGlassTuning,
                                            motionTier = motionTier,
                                            isScrolling = topChromeMotionPolicy.isScrolling,
                                            isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                            forceLowBlurBudget = forceLowBlurBudget,
                                            preferFlatGlass = resolveHomeTopWideChromePreferFlatGlass(
                                                effectiveTopPanelChromeRenderMode
                                            )
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .then(
                                    if (
                                        drawUnifiedTopPanelChrome &&
                                        !integratedCollapsedTopBar &&
                                        !useUnifiedLiquidChrome
                                    ) {
                                        Modifier.border(0.8.dp, headerChromeColors.borderColor, unifiedPanelShape)
                                    } else {
                                        Modifier
                                    }
                                )
                        } else {
                            Modifier
                        }
                    )
            ) {
                if (!topAtmosphereImagePath.isNullOrBlank()) {
                    AsyncImage(
                        model = File(topAtmosphereImagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.30f)
                            .clearAndSetSemantics {}
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        headerChromeColors.containerColor.copy(alpha = 0.52f)
                                    )
                                )
                            )
                            .clearAndSetSemantics {}
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
                                )
                            )
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (embedTopTabsInUnifiedPanel) {
                                Modifier.padding(
                                    horizontal = if (integratedCollapsedTopBar) 0.dp else unifiedPanelInnerPadding,
                                    vertical = if (renderUnifiedTopPanelChrome) {
                                        unifiedPanelInnerPadding
                                    } else {
                                        0.dp
                                    }
                                )
                            } else {
                                Modifier
                            }
                        )
                ) {
                    if (topLayoutOrder == HomeTopLayoutOrder.TABS_THEN_SEARCH) {
                        topTabsContent()
                        if (drawTopSearchDivider) {
                            Spacer(modifier = Modifier.height(currentSearchToTabsSpacing))
                            HorizontalDivider(
                                thickness = 1.dp,
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
                            .graphicsLayer {
                                alpha = searchAlpha
                                translationY = searchContentTranslationYPx
                            }
                            .clip(androidx.compose.ui.graphics.RectangleShape)
                    ) {
                        Row(
	                            modifier = Modifier
	                                .fillMaxWidth()
	                                .height(searchBarHeightDp)
	                                .padding(
	                                    horizontal = if (embedTopTabsInUnifiedPanel) {
	                                        0.dp
	                                    } else {
	                                        resolveHomeTopSearchRowHorizontalPadding(uiPreset, androidNativeVariant)
	                                    }
	                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(resolveHomeTopAvatarOuterSize())
                                    .then(
                                        if (uiPreset == UiPreset.MD3) {
                                            Modifier.clickable {
                                                performHomeTopBarTap(haptic = haptic, onClick = onAvatarClick)
                                            }
                                        } else {
                                            Modifier.iOSTapEffect { onAvatarClick() }
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(resolveHomeTopAvatarInnerSize())
                                        .clip(edgeButtonShape)
                                        .then(
                                            if (useUnifiedTopPanel) {
                                                if (useUnifiedLiquidChrome) {
                                                    Modifier
                                                        .homeTopChromeSurface(
                                                            renderMode = localTopChromeRenderMode,
                                                            shape = edgeButtonShape,
                                                            surfaceColor = headerChromeColors.containerColor,
                                                            hazeState = hazeState,
                                                            backdrop = backdrop,
                                                            liquidStyle = liquidStyle,
                                                            liquidGlassTuning = liquidGlassTuning,
                                                            motionTier = motionTier,
                                                            isScrolling = topChromeMotionPolicy.isScrolling,
                                                            isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                            forceLowBlurBudget = forceLowBlurBudget
                                                        )
                                                } else {
                                                    Modifier.border(
                                                        width = 0.8.dp,
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
                                                        backdrop = backdrop,
                                                        liquidStyle = liquidStyle,
                                                        liquidGlassTuning = liquidGlassTuning,
                                                        motionTier = motionTier,
                                                        isScrolling = topChromeMotionPolicy.isScrolling,
                                                        isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                        forceLowBlurBudget = forceLowBlurBudget
                                                    )
                                                    .border(1.dp, headerChromeColors.borderColor, edgeButtonShape)
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
                                        fallbackTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(resolveHomeTopEdgeControlGap(uiPreset, androidNativeVariant)))

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(resolveHomeTopSearchPillHeight(uiPreset, androidNativeVariant)),
                                contentAlignment = Alignment.Center
                            ) {
                                val isTablet = com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet
                                val stableSearchContentColor = if (uiPreset == UiPreset.MD3) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else if (isLightMode) {
                                    topForegroundColor
                                } else {
                                    Color.White.copy(alpha = 0.96f)
                                }
                                val searchPillContent: @Composable () -> Unit = {
                                    HomeTopSearchPillContent(
                                        searchIcon = searchIcon,
                                        contentColor = stableSearchContentColor,
                                        textFontSize = if (uiPreset == UiPreset.MD3) {
                                            if (isTablet) 15.sp else 14.sp
                                        } else {
                                            if (isTablet) 16.sp else 15.sp
                                        },
                                        iconTextGap = resolveHomeTopSearchIconTextGap(uiPreset, androidNativeVariant)
                                    )
                                }
                                val searchClickInteractionSource = remember { MutableInteractionSource() }
                                val defaultSearchSurfaceColor = if (useUnifiedTopPanel) {
                                    resolveHomeTopUnifiedSearchContainerColor(
                                        isLightMode = isLightMode,
                                        renderMode = searchChromeRenderMode
                                    )
                                } else {
                                    searchPillColors.containerColor
                                }
                                val skinSearchSurfaceColor = resolveHomeSkinSearchSurfaceColor(
                                    defaultSurfaceColor = defaultSearchSurfaceColor,
                                    skinTint = uiSkinDecoration?.searchCapsuleTint,
                                    useUnifiedTopPanel = useUnifiedTopPanel
                                )
                                Box(
                                    modifier = Modifier
                                        .widthIn(max = 640.dp)
                                        .fillMaxWidth()
                                        .height(resolveHomeTopSearchPillHeight(uiPreset, androidNativeVariant))
                                        .clip(searchContainerShape)
                                        .then(
                                            if (useBottomBarMatchedTopControls) {
                                                Modifier.homeTopBottomBarMatchedSurface(
                                                    renderMode = searchChromeRenderMode,
                                                    shape = searchContainerShape,
                                                    hazeState = hazeState,
                                                    backdrop = backdrop,
                                                    liquidGlassStyle = liquidStyle,
                                                    liquidGlassTuning = liquidGlassTuning,
                                                    liquidGlassPreset = bottomBarLiquidGlassPreset,
                                                    motionTier = motionTier,
                                                    isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                    forceLowBlurBudget = forceLowBlurBudget
                                                )
                                            } else {
                                                Modifier.homeTopChromeSurface(
                                                    renderMode = searchChromeRenderMode,
                                                    shape = searchContainerShape,
                                                    surfaceColor = skinSearchSurfaceColor,
                                                    hazeState = hazeState,
                                                    backdrop = backdrop,
                                                    liquidStyle = liquidStyle,
                                                    liquidGlassTuning = liquidGlassTuning,
                                                    motionTier = motionTier,
                                                    isScrolling = topChromeMotionPolicy.isScrolling,
                                                    isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                    forceLowBlurBudget = forceLowBlurBudget,
                                                    preferFlatGlass = resolveHomeTopWideChromePreferFlatGlass(
                                                        searchChromeRenderMode
                                                    ),
                                                    darkThemeWhiteOverlayMultiplier = resolveHomeTopSearchDarkWhiteOverlayMultiplier(
                                                        isLightMode = isLightMode
                                                    )
                                                )
                                            }
                                        )
                                        .border(
                                            width = 0.8.dp,
                                            color = if (useBottomBarMatchedTopControls) {
                                                Color.Transparent
                                            } else if (useUnifiedTopPanel) {
                                                resolveHomeTopUnifiedSearchBorderColor(
                                                    isLightMode = isLightMode,
                                                    renderMode = searchChromeRenderMode
                                                )
                                            } else {
                                                searchPillColors.borderColor
                                            },
                                            shape = searchContainerShape
                                        )
                                        .clickable(
                                            interactionSource = searchClickInteractionSource,
                                            indication = null
                                        ) {
                                            haptic(HapticType.LIGHT)
                                            onSearchClick()
                                        }
                                        .padding(horizontal = resolveHomeTopSearchContentHorizontalPadding(uiPreset, androidNativeVariant)),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (
                                        shouldDrawHomeTopSearchLegacyHighlight(
                                            uiPreset = uiPreset,
                                            useUnifiedTopPanel = useUnifiedTopPanel,
                                            renderMode = searchChromeRenderMode,
                                            refractionOverlayAlpha = 0f
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(14.dp)
                                                .align(Alignment.TopCenter)
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            resolveHomeTopChromeHighlightOverlayColor(
                                                                baseColor = searchPillColors.highlightColor,
                                                                renderMode = topChromeRenderMode,
                                                                softenWideChrome = true
                                                            ),
                                                            Color.Transparent
                                                        )
                                                    )
                                            )
                                        )
                                    }
                                    searchPillContent()
                                }
                            }

                            Spacer(modifier = Modifier.width(resolveHomeTopEdgeControlGap(uiPreset, androidNativeVariant)))

                            val topRightActionButtonSize = resolveHomeTopSettingsButtonSize(uiPreset, androidNativeVariant)
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
                                        .clip(edgeButtonShape)
                                        .then(
                                            if (useUnifiedTopPanel) {
                                                if (useBottomBarMatchedTopControls) {
                                                    Modifier
                                                        .homeTopBottomBarMatchedSurface(
                                                            renderMode = localTopChromeRenderMode,
                                                            shape = edgeButtonShape,
                                                            hazeState = hazeState,
                                                            backdrop = backdrop,
                                                            liquidGlassStyle = liquidStyle,
                                                            liquidGlassTuning = liquidGlassTuning,
                                                            liquidGlassPreset = bottomBarLiquidGlassPreset,
                                                            motionTier = motionTier,
                                                            isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                            forceLowBlurBudget = forceLowBlurBudget,
                                                            drawShellLens = false
                                                        )
                                                } else {
                                                    Modifier
                                                        .background(
                                                            resolveHomeTopEdgeControlContainerColor(
                                                                isLightMode = isLightMode,
                                                                renderMode = localTopChromeRenderMode
                                                            )
                                                        )
                                                        .border(
                                                            width = 0.8.dp,
                                                            color = resolveHomeTopEdgeControlBorderColor(
                                                                isLightMode = isLightMode,
                                                                renderMode = localTopChromeRenderMode
                                                            ),
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
                                                        backdrop = backdrop,
                                                        liquidStyle = liquidStyle,
                                                        liquidGlassTuning = liquidGlassTuning,
                                                        motionTier = motionTier,
                                                        isScrolling = topChromeMotionPolicy.isScrolling,
                                                        isTransitionRunning = topChromeMotionPolicy.isTransitionRunning,
                                                        forceLowBlurBudget = forceLowBlurBudget
                                                    )
                                                    .border(0.8.dp, headerChromeColors.borderColor, edgeButtonShape)
                                            }
                                        )
                                        .then(
                                            if (uiPreset == UiPreset.MD3) {
                                                Modifier.clickable {
                                                    performHomeTopBarTap(haptic = haptic, onClick = onTopRightActionClick)
                                                }
                                            } else {
                                                Modifier.iOSTapEffect {
                                                    haptic(HapticType.LIGHT)
                                                    onTopRightActionClick()
                                                }
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        topRightActionIcon,
                                        contentDescription = topRightActionContentDescription,
                                        tint = if (isLightMode) {
                                            topForegroundColor
                                        } else {
                                            topForegroundColor.copy(alpha = topActionIconAlpha)
                                        },
                                        modifier = Modifier.size(resolveHomeTopSettingsIconSize(uiPreset, androidNativeVariant))
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

                    if (topLayoutOrder == HomeTopLayoutOrder.SEARCH_THEN_TABS) {
                        if (drawTopSearchDivider) {
                            Spacer(modifier = Modifier.height(currentSearchToTabsSpacing))
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = headerChromeColors.borderColor.copy(
                                    alpha = resolveHomeTopUnifiedPanelDividerAlpha(topChromeRenderMode) *
                                        searchRevealFraction
                                )
                            )
                            Spacer(modifier = Modifier.height(currentUnifiedDividerBottomSpacing))
                        } else {
                            Spacer(modifier = Modifier.height(currentSearchToTabsSpacing))
                        }

                        topTabsContent()
                    }
                }
            }
        }
    }
}
