// 文件路径: feature/home/components/BottomBar.kt
package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorHeightDp
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.rememberResolvedAppIconStyle
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppBottomNavigationHost
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppNavigationBar
import com.android.purebilibili.core.ui.components.AppNavigationBarItem
import com.android.purebilibili.core.ui.components.AppPlatformNavigationBadge
import com.android.purebilibili.core.ui.components.AppPlatformNavigationBar
import com.android.purebilibili.core.ui.components.AppPlatformNavigationBarDisplayMode
import com.android.purebilibili.core.ui.components.AppPlatformNavigationBarItem
import com.android.purebilibili.core.ui.components.AppSurface

import com.android.purebilibili.core.ui.OpticalContrastPalette

import android.os.Build
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable  // [新增] 组合点击支持
import androidx.compose.foundation.ExperimentalFoundationApi // [新增]
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WatchLater
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.TextButton
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer  //  晃动动画
import androidx.compose.ui.graphics.shadow.Shadow as ComposeShadow
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.android.purebilibili.R
import com.android.purebilibili.navigation.ScreenRoutes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import com.android.purebilibili.core.ui.blur.shouldAllowDirectHazeLiquidGlassFallback
import com.android.purebilibili.core.ui.blur.shouldAllowHomeChromeLiquidGlass
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.resolveGlobalWallpaperProtectiveColor
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.theme.iOSSystemGray
import com.android.purebilibili.core.theme.iOSSystemGray3
import com.android.purebilibili.core.theme.iOSSystemGray6
import com.android.purebilibili.core.theme.iOSRed
import com.android.purebilibili.core.theme.BottomBarColors  // 统一底栏颜色配置
import com.android.purebilibili.core.theme.BottomBarColorPalette  // 调色板
import com.android.purebilibili.core.theme.LocalCornerRadiusScale

import kotlinx.coroutines.launch  //  延迟导航
import com.android.purebilibili.feature.home.LocalHomeScrollOffset
import com.android.purebilibili.feature.home.HomeVisualPalette
import com.android.purebilibili.core.ui.motion.BottomBarMotionProfile
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.motion.resolveBottomBarMotionSpec
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import com.android.purebilibili.feature.home.components.liquid.InnerShadow as MiuixInnerShadow
import com.android.purebilibili.feature.home.components.liquid.innerShadow as miuixInnerShadow
import com.android.purebilibili.feature.home.components.liquid.lens as miuixLens
import com.android.purebilibili.feature.home.components.liquid.rememberCombinedBackdrop as rememberMiuixCombinedBackdrop
import com.android.purebilibili.feature.home.components.liquid.vibrancy as miuixVibrancy
import androidx.compose.foundation.shape.RoundedCornerShape as RoundedCornerShapeAlias
import androidx.compose.ui.Modifier.Companion.then
import dev.chrisbanes.haze.hazeSource
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset
import com.android.purebilibili.core.store.BottomBarSearchAutoExpandMode
import com.android.purebilibili.core.store.BottomBarSearchLayoutMode
import com.android.purebilibili.core.store.LiquidGlassStyle // [New] Top-level enum
import com.android.purebilibili.core.store.LiquidGlassMode
import androidx.compose.animation.core.EaseOut
import kotlin.math.sign
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop as MiuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.blur as miuixBlur
import top.yukonga.miuix.kmp.blur.drawBackdrop as miuixDrawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight as MiuixHighlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults as MiuixNavigationBarDefaults
private val iosIndicatorSpecular: MiuixHighlight = MiuixHighlight(
    width = AppSpacingTokens.Micro / 2,
    alpha = 1f,
    style = BloomStroke(
        color = OpticalContrastPalette.Highlight.copy(alpha = 0.12f),
        innerBlurRadius = AppSpacingTokens.Micro,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = OpticalContrastPalette.Highlight,
            intensity = 1f
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = OpticalContrastPalette.Highlight,
            intensity = 0.4f
        ),
        dualPeak = true
    )
)

/**
 * 底部导航项枚举。图标由当前主题的导航图标策略统一解析。
 */
enum class BottomNavItem(
    val label: String,
    @StringRes val labelRes: Int,
    @StringRes val contentDescriptionRes: Int,
    val legacyAliases: List<String> = emptyList(),
    val route: String // [新增] 路由地址
) {
    HOME(
        "推荐",
        R.string.bottom_nav_home,
        R.string.bottom_nav_home,
        emptyList(),
        ScreenRoutes.Home.route
    ),
    DYNAMIC(
        "动态",
        R.string.bottom_nav_dynamic,
        R.string.bottom_nav_dynamic,
        emptyList(),
        ScreenRoutes.Dynamic.route
    ),
    STORY(
        "短视频",
        R.string.bottom_nav_story,
        R.string.bottom_nav_story,
        emptyList(),
        ScreenRoutes.Story.baseRoute
    ),
    HISTORY(
        "历史",
        R.string.bottom_nav_history,
        R.string.bottom_nav_history_desc,
        listOf("历史记录"),
        ScreenRoutes.History.route
    ),
    LISTEN_VIDEO(
        "听视频",
        R.string.bottom_nav_listen_video,
        R.string.bottom_nav_listen_video_desc,
        listOf("音乐"),
        ScreenRoutes.ListenVideo.route
    ),
    PROFILE(
        "我的",
        R.string.bottom_nav_profile,
        R.string.bottom_nav_profile_desc,
        listOf("个人中心"),
        ScreenRoutes.Profile.route
    ),
    FAVORITE(
        "收藏",
        R.string.bottom_nav_favorite,
        R.string.bottom_nav_favorite_desc,
        listOf("收藏夹"),
        ScreenRoutes.Favorite.route
    ),
    LIVE(
        "直播",
        R.string.bottom_nav_live,
        R.string.bottom_nav_live,
        emptyList(),
        ScreenRoutes.LiveList.route
    ),
    WATCHLATER(
        "稍后看",
        R.string.bottom_nav_watch_later,
        R.string.bottom_nav_watch_later_desc,
        listOf("稍后再看"),
        ScreenRoutes.WatchLater.route
    ),
    SETTINGS(
        "设置",
        R.string.bottom_nav_settings,
        R.string.bottom_nav_settings,
        emptyList(),
        ScreenRoutes.Settings.route
    ),
    PLUGINS(
        "插件",
        R.string.plugins_center_title,
        R.string.plugins_center_title,
        listOf("插件中心"),
        ScreenRoutes.PluginsSettings.createRoute()
    )
}

@Composable
internal fun resolveBottomNavItemLabel(
    item: BottomNavItem,
    customLabels: Map<String, String> = emptyMap(),
): String = customLabels[item.name]?.takeIf(String::isNotBlank) ?: stringResource(item.labelRes)

@Composable
internal fun resolveBottomNavItemContentDescription(item: BottomNavItem): String =
    stringResource(item.contentDescriptionRes)

internal fun resolveBottomNavItemLookupKeys(item: BottomNavItem): Set<String> {
    return linkedSetOf(
        item.name,
        item.name.lowercase(),
        item.name.uppercase(),
        item.route,
        item.route.lowercase(),
        item.route.uppercase(),
        item.label,
        item.label.lowercase(),
        *item.legacyAliases.toTypedArray()
    )
}

internal data class BottomBarLayoutPolicy(
    val horizontalPadding: Dp,
    val rowPadding: Dp,
    val maxBarWidth: Dp
)

internal enum class Md3BottomBarDisplayMode {
    IconAndText,
    IconOnly,
    TextOnly
}

internal data class Md3BottomBarFloatingChromeSpec(
    val cornerRadiusDp: Float,
    val horizontalOutsidePaddingDp: Float,
    val innerHorizontalPaddingDp: Float,
    val itemSpacingDp: Float,
    val shadowElevationDp: Float,
    val showDivider: Boolean
)

internal data class MaterialDockedBottomBarItemColors(
    val selectedIconColor: Color,
    val selectedTextColor: Color,
    val indicatorColor: Color,
    val unselectedIconColor: Color,
    val unselectedTextColor: Color
)

internal fun resolveMaterialDockedBottomBarItemColors(
    themePrimary: Color,
    onSurfaceVariant: Color,
    secondaryContainer: Color
): MaterialDockedBottomBarItemColors {
    return MaterialDockedBottomBarItemColors(
        selectedIconColor = themePrimary,
        selectedTextColor = themePrimary,
        indicatorColor = secondaryContainer,
        unselectedIconColor = onSurfaceVariant,
        unselectedTextColor = onSurfaceVariant
    )
}

internal fun resolveDockedBottomBarIndicatorColor(
    defaultColor: Color,
    hasUiSkinDecoration: Boolean,
): Color = if (hasUiSkinDecoration) Color.Transparent else defaultColor

internal fun resolveFloatingBottomBarContainerColor(
    defaultColor: Color,
    mode: FloatingBottomBarMode,
    hasUiSkinDecoration: Boolean,
): Color = if (mode == FloatingBottomBarMode.None && hasUiSkinDecoration) {
    Color.Transparent
} else {
    defaultColor
}

internal fun resolveMd3BottomBarFloatingChromeSpec(
    isFloating: Boolean
): Md3BottomBarFloatingChromeSpec {
    return if (isFloating) {
        Md3BottomBarFloatingChromeSpec(
            cornerRadiusDp = 50f,
            horizontalOutsidePaddingDp = 36f,
            innerHorizontalPaddingDp = 12f,
            itemSpacingDp = 12f,
            shadowElevationDp = 1f,
            showDivider = false
        )
    } else {
        Md3BottomBarFloatingChromeSpec(
            cornerRadiusDp = 0f,
            horizontalOutsidePaddingDp = 0f,
            innerHorizontalPaddingDp = 0f,
            itemSpacingDp = 0f,
            shadowElevationDp = 0f,
            showDivider = true
        )
    }
}

internal fun resolveMd3BottomBarDisplayMode(labelMode: Int): Md3BottomBarDisplayMode {
    return when (normalizeBottomBarLabelMode(labelMode)) {
        1 -> Md3BottomBarDisplayMode.IconOnly
        2 -> Md3BottomBarDisplayMode.TextOnly
        else -> Md3BottomBarDisplayMode.IconAndText
    }
}

internal fun shouldUseOfficialMd3FloatingToolbar(
    isFloating: Boolean,
    liquidGlassEnabled: Boolean,
): Boolean = isFloating && !liquidGlassEnabled

internal data class AndroidNativeBottomBarTuning(
    val cornerRadiusDp: Float,
    val shellShadowElevationDp: Float,
    val shellBlurRadiusDp: Float,
    val shellSurfaceAlpha: Float,
    val outerHorizontalPaddingDp: Float,
    val innerHorizontalPaddingDp: Float,
    val indicatorHeightDp: Float,
    val indicatorLensRadiusDp: Float
)

internal enum class SharedFloatingBottomBarIconStyle {
    MATERIAL,
    MIUIX
}

internal data class AndroidNativeIndicatorSpec(
    val usesLens: Boolean,
    val captureTintedContentLayer: Boolean
)

internal fun resolveSharedBottomBarCapsuleShape(): androidx.compose.ui.graphics.Shape =
    RoundedCornerShape(percent = 50)

internal fun resolveBiliPaiFloatingBottomBarWidth(
    containerWidth: Dp,
    itemCount: Int,
    minEdgePadding: Dp,
    labelMode: Int = 0,
    cornerRadius: Dp = AppSpacingTokens.DoubleExtraLarge
): Dp {
    val safeItemCount = itemCount.coerceAtLeast(1)
    val contentPadding = AppSpacingTokens.ExtraSmall
    val minimumItemWidth = cornerRadius.coerceAtLeast(AppSpacingTokens.None) * 2
    val contentPreferredItemWidth = when (normalizeBottomBarLabelMode(labelMode)) {
        1 -> minimumItemWidth
        2 -> AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall
        else -> AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraLarge + AppSpacingTokens.ExtraSmall
    }
    val preferredItemWidth = maxOf(contentPreferredItemWidth, minimumItemWidth)
    val preferredWidth = (preferredItemWidth * safeItemCount) + (contentPadding * 2)
    val minimumWidth = (minimumItemWidth * safeItemCount) + (contentPadding * 2)
    val widthCap = (containerWidth - (minEdgePadding * 2)).coerceAtLeast(minimumWidth)
    return minOf(preferredWidth, widthCap).coerceAtMost(containerWidth)
}

internal fun resolveBiliPaiBottomBarItemSlotWidth(
    dockWidth: Dp,
    horizontalPadding: Dp,
    itemCount: Int
): Dp {
    val safeItemCount = itemCount.coerceAtLeast(1)
    return ((dockWidth - (horizontalPadding * 2)) / safeItemCount)
        .coerceAtLeast(AppSpacingTokens.None)
}

internal fun resolveBiliPaiBottomBarItemCenterX(
    itemIndex: Int,
    itemWidth: Dp,
    horizontalPadding: Dp
): Dp {
    val safeIndex = itemIndex.coerceAtLeast(0)
    return horizontalPadding + (itemWidth * safeIndex) + (itemWidth / 2f)
}

internal data class BiliPaiBottomBarSearchLayout(
    val dockWidth: Dp,
    val searchWidth: Dp,
    val gap: Dp,
    val minimumIndicatorWidth: Dp,
)

internal fun resolveBiliPaiBottomBarSearchCircleSize(): Dp =
    AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Small

internal fun resolveBiliPaiExpandedHomeIconSize(): Dp = AppSpacingTokens.ExtraLarge + AppSpacingTokens.ExtraSmall

internal fun resolveBiliPaiExpandedHomeIconScale(): Float = 0.92f

internal fun resolveBiliPaiBottomBarSearchFieldExpanded(
    searchExpanded: Boolean,
    searchLayoutMode: BottomBarSearchLayoutMode
): Boolean {
    return searchExpanded && searchLayoutMode == BottomBarSearchLayoutMode.HOME_AND_SEARCH
}

internal fun resolveBiliPaiBottomBarSearchLayout(
    containerWidth: Dp,
    itemCount: Int,
    minEdgePadding: Dp,
    searchEnabled: Boolean,
    searchExpanded: Boolean,
    labelMode: Int = 0,
    cornerRadius: Dp = AppSpacingTokens.DoubleExtraLarge,
    searchLayoutMode: BottomBarSearchLayoutMode = BottomBarSearchLayoutMode.FULL_DOCK
): BiliPaiBottomBarSearchLayout {
    val baseDockWidth = resolveBiliPaiFloatingBottomBarWidth(
        containerWidth = containerWidth,
        itemCount = itemCount,
        minEdgePadding = minEdgePadding,
        labelMode = labelMode,
        cornerRadius = cornerRadius
    )
    if (!searchEnabled) {
        return BiliPaiBottomBarSearchLayout(
            dockWidth = baseDockWidth,
            searchWidth = AppSpacingTokens.None,
            gap = AppSpacingTokens.None,
            minimumIndicatorWidth = AppSpacingTokens.None,
        )
    }

    val gap = AppSpacingTokens.Small
    val paddedAvailable = (containerWidth - (minEdgePadding * 2)).coerceAtLeast(AppSpacingTokens.None)
    val fullAvailable = containerWidth.coerceAtLeast(AppSpacingTokens.None)
    val searchCircleSize = resolveBiliPaiBottomBarSearchCircleSize()
    val collapsedSearchWidth = searchCircleSize
    val compactHomeDockSize = searchCircleSize
    val expandedSearchWidth = minOf(
        AppSpacingTokens.TripleExtraLarge * 6 - AppSpacingTokens.Small,
        (paddedAvailable - compactHomeDockSize - gap).coerceAtLeast(
            AppSpacingTokens.TripleExtraLarge * 3 + AppSpacingTokens.DoubleExtraLarge
        )
    )
    val useCompactLayout = searchLayoutMode == BottomBarSearchLayoutMode.HOME_AND_SEARCH
    val targetSearchWidth = if (useCompactLayout && searchExpanded) {
        expandedSearchWidth
    } else {
        collapsedSearchWidth
    }
    val targetDockWidth = if (useCompactLayout && searchExpanded) {
        minOf(
            compactHomeDockSize,
            (paddedAvailable - targetSearchWidth - gap).coerceAtLeast(AppSpacingTokens.None)
        )
    } else {
        // Spend outer padding before shrinking navigation slots so icon+label
        // geometry stays close to the search-off dock.
        minOf(
            baseDockWidth,
            (fullAvailable - targetSearchWidth - gap).coerceAtLeast(AppSpacingTokens.None)
        )
    }
    return BiliPaiBottomBarSearchLayout(
        dockWidth = targetDockWidth,
        searchWidth = targetSearchWidth,
        gap = gap,
        // The indicator must match the navigation slot. Keeping the pre-search
        // width makes it overlap neighbouring destinations and shifts it at the dock edges.
        minimumIndicatorWidth = AppSpacingTokens.None,
    )
}

internal fun resolveBiliPaiBottomBarDockHeight(
    searchExpanded: Boolean,
    hasUiSkinDecoration: Boolean = false
): Dp {
    return if (searchExpanded) {
        resolveBiliPaiBottomBarSearchCircleSize()
    } else if (hasUiSkinDecoration) {
        resolveBottomBarSkinDockHeight()
    } else {
        // 照搬 HyperIsland 的壳高 64dp：指示器静止 56dp、按下 78dp、
        // 指示器 lens 绝对 10dp/14dp 都由此推导，静止时上下各留 4dp。
        com.android.purebilibili.core.ui.BottomBarReferenceShellHeightDp.dp
    }
}

internal fun resolveBiliPaiBottomBarIndicatorHeight(dockHeight: Dp): Dp {
    // 照搬 HyperIsland 的静止几何：64dp 壳配 56dp 指示器，也就是指示器 = 壳高 × 56/64，
    // 静止时上下各留出 4dp（以 64dp 壳计）比例的垂直边距。
    // 原先的 minOf(dockHeight, 56dp) 会让 56dp 壳的指示器与壳同高，静止 inset 变成 0。
    // 按下 bloom 的绝对高度不受影响：resolveMatchedLiquidIndicatorPressedScale 以新基准换算。
    val scaledHeight = resolveMatchedLiquidIndicatorHeightDp(dockHeight.value)
    return minOf(dockHeight, scaledHeight.coerceAtLeast(1f).dp)
}

internal fun resolveBiliPaiBottomBarSearchHeight(searchExpanded: Boolean): Dp {
    return if (searchExpanded) {
        AppChromeSizeTokens.MinimumTouchTarget
    } else {
        resolveBiliPaiBottomBarSearchCircleSize()
    }
}

internal fun resolveBottomBarRefractionCaptureWidth(
    dockWidth: Dp,
    launchAdjustedSearchGap: Dp,
    searchWidth: Dp,
    searchEnabled: Boolean
): Dp {
    return if (searchEnabled) {
        dockWidth + launchAdjustedSearchGap + searchWidth
    } else {
        dockWidth
    }
}

private data class BiliPaiBottomBarSearchLayoutState(
    val dockWidth: Dp,
    val dockHeight: Dp,
    val minimumIndicatorWidth: Dp,
    val searchWidth: Dp,
    val searchHeight: Dp,
    val searchGap: Dp,
    val launchAdjustedSearchGap: Dp,
    val shellHeight: Dp
)

@Composable
private fun rememberBiliPaiBottomBarSearchLayoutState(
    containerWidth: Dp,
    itemCount: Int,
    minEdgePadding: Dp,
    searchEnabled: Boolean,
    searchExpanded: Boolean,
    labelMode: Int,
    searchLayoutMode: BottomBarSearchLayoutMode,
    hasUiSkinDecoration: Boolean
): BiliPaiBottomBarSearchLayoutState {
    val targetDockHeight = resolveBiliPaiBottomBarDockHeight(
        searchExpanded = searchExpanded,
        hasUiSkinDecoration = hasUiSkinDecoration
    )
    val targetSearchLayout = resolveBiliPaiBottomBarSearchLayout(
        containerWidth = containerWidth,
        itemCount = itemCount,
        minEdgePadding = minEdgePadding,
        searchEnabled = searchEnabled,
        searchExpanded = searchExpanded,
        labelMode = labelMode,
        cornerRadius = targetDockHeight / 2,
        searchLayoutMode = searchLayoutMode
    )
    if (!searchEnabled) {
        val dockWidth by animateDpAsState(
            targetValue = targetSearchLayout.dockWidth,
            animationSpec = bottomBarDockWidthMotionSpec(),
            label = "bottomBarDockWidth"
        )
        val dockHeight by animateDpAsState(
            targetValue = targetDockHeight,
            animationSpec = bottomBarChromeHeightMotionSpec(),
            label = "bottomBarDockHeight"
        )
        return BiliPaiBottomBarSearchLayoutState(
            dockWidth = dockWidth,
            dockHeight = dockHeight,
            minimumIndicatorWidth = targetSearchLayout.minimumIndicatorWidth,
            searchWidth = AppSpacingTokens.None,
            searchHeight = AppSpacingTokens.None,
            searchGap = AppSpacingTokens.None,
            launchAdjustedSearchGap = AppSpacingTokens.None,
            shellHeight = dockHeight
        )
    }

    val dockWidth by animateDpAsState(
        targetValue = targetSearchLayout.dockWidth,
        animationSpec = bottomBarDockWidthMotionSpec(),
        label = "bottomBarDockWidth"
    )
    val searchWidth by animateDpAsState(
        targetValue = targetSearchLayout.searchWidth,
        animationSpec = bottomBarDockWidthMotionSpec(),
        label = "bottomBarSearchWidth"
    )
    val searchGap by animateDpAsState(
        targetValue = targetSearchLayout.gap,
        animationSpec = bottomBarSearchGapMotionSpec(),
        label = "bottomBarSearchGap"
    )
    val dockHeight by animateDpAsState(
        targetValue = targetDockHeight,
        animationSpec = bottomBarChromeHeightMotionSpec(),
        label = "bottomBarDockHeight"
    )
    val searchHeight by animateDpAsState(
        targetValue = resolveBiliPaiBottomBarSearchHeight(
            searchExpanded = searchExpanded
        ),
        animationSpec = bottomBarChromeHeightMotionSpec(),
        label = "bottomBarSearchHeight"
    )
    val shellHeight = if (dockHeight > searchHeight) dockHeight else searchHeight
    return BiliPaiBottomBarSearchLayoutState(
        dockWidth = dockWidth,
        dockHeight = dockHeight,
        minimumIndicatorWidth = targetSearchLayout.minimumIndicatorWidth,
        searchWidth = searchWidth,
        searchHeight = searchHeight,
        searchGap = searchGap,
        launchAdjustedSearchGap = searchGap,
        shellHeight = shellHeight
    )
}

private const val BottomBarSearchTopThresholdPx = 32f
private const val BottomBarTransientAlphaThreshold = 0.001f

internal fun shouldAutoExpandBottomBarSearchAtThreshold(
    currentItem: BottomNavItem,
    bottomBarSearchEnabled: Boolean,
    autoExpandMode: BottomBarSearchAutoExpandMode,
    isPastTopThreshold: Boolean
): Boolean {
    if (!bottomBarSearchEnabled || currentItem != BottomNavItem.HOME) return false
    return when (autoExpandMode) {
        BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP -> !isPastTopThreshold
        BottomBarSearchAutoExpandMode.EXPAND_WHEN_SCROLLING_DOWN -> isPastTopThreshold
        BottomBarSearchAutoExpandMode.DISABLED -> false
    }
}

internal fun shouldAutoExpandBottomBarSearch(
    currentItem: BottomNavItem,
    bottomBarSearchEnabled: Boolean,
    autoExpandMode: BottomBarSearchAutoExpandMode,
    homeScrollOffsetPx: Float,
    topThresholdPx: Float = 32f
): Boolean {
    return shouldAutoExpandBottomBarSearchAtThreshold(
        currentItem = currentItem,
        bottomBarSearchEnabled = bottomBarSearchEnabled,
        autoExpandMode = autoExpandMode,
        isPastTopThreshold = homeScrollOffsetPx > topThresholdPx
    )
}

internal fun resolveBottomBarSearchEnabledForItem(
    currentItem: BottomNavItem,
    bottomBarSearchEnabled: Boolean
): Boolean {
    return bottomBarSearchEnabled && currentItem == BottomNavItem.HOME
}

internal fun shouldReserveBottomBarSearchLayout(
    bottomBarSearchEnabled: Boolean
): Boolean = bottomBarSearchEnabled

internal fun resolveBottomBarVisibleItemsForSearchMode(
    visibleItems: List<BottomNavItem>,
    bottomBarSearchEnabled: Boolean,
    searchLayoutMode: BottomBarSearchLayoutMode = BottomBarSearchLayoutMode.FULL_DOCK
): List<BottomNavItem> {
    return visibleItems
}

internal enum class BottomBarSearchExpansionOverride {
    FOLLOW_AUTO,
    EXPANDED,
    COLLAPSED
}

internal fun resolveEffectiveBottomBarSearchExpanded(
    currentItem: BottomNavItem,
    bottomBarSearchEnabled: Boolean,
    shouldAutoExpand: Boolean,
    expansionOverride: BottomBarSearchExpansionOverride
): Boolean {
    if (!bottomBarSearchEnabled || currentItem != BottomNavItem.HOME) return false
    return when (expansionOverride) {
        BottomBarSearchExpansionOverride.FOLLOW_AUTO -> shouldAutoExpand
        BottomBarSearchExpansionOverride.EXPANDED -> true
        BottomBarSearchExpansionOverride.COLLAPSED -> false
    }
}

internal fun resolveBottomBarSearchExpansionOverrideOnNavItemClick(
    currentItem: BottomNavItem,
    clickedItem: BottomNavItem,
    bottomBarSearchEnabled: Boolean,
    effectiveSearchExpanded: Boolean
): BottomBarSearchExpansionOverride? {
    // 首页标签只负责重选刷新；搜索展开/收起交给独立搜索槽位，避免两个意图共用一次点击。
    return null
}

internal fun resolveBottomBarSearchExpansionOverrideOnSearchClick(
    currentItem: BottomNavItem,
    bottomBarSearchEnabled: Boolean,
    effectiveSearchExpanded: Boolean,
    searchLayoutMode: BottomBarSearchLayoutMode = BottomBarSearchLayoutMode.FULL_DOCK
): BottomBarSearchExpansionOverride? {
    if (
        !bottomBarSearchEnabled ||
        currentItem != BottomNavItem.HOME ||
        searchLayoutMode != BottomBarSearchLayoutMode.HOME_AND_SEARCH
    ) {
        return null
    }
    return if (effectiveSearchExpanded) {
        BottomBarSearchExpansionOverride.COLLAPSED
    } else {
        BottomBarSearchExpansionOverride.EXPANDED
    }
}

internal fun shouldResetBottomBarSearchExpansionOverride(
    currentItem: BottomNavItem,
    bottomBarSearchEnabled: Boolean,
    shouldAutoExpand: Boolean,
    isPastTopThreshold: Boolean
): Boolean {
    return !bottomBarSearchEnabled ||
        currentItem != BottomNavItem.HOME ||
        (currentItem == BottomNavItem.HOME && !shouldAutoExpand && isPastTopThreshold)
}

internal fun shouldRenderBottomBarRefractionCapture(
    glassEnabled: Boolean,
    hasBackdrop: Boolean,
    captureProgress: Float,
    isTransitionRunning: Boolean = false,
    isFeedScrollInProgress: Boolean = false,
    isBottomBarInteractionActive: Boolean = false
): Boolean {
    if (!glassEnabled || !hasBackdrop || captureProgress <= BottomBarTransientAlphaThreshold) return false
    if (isTransitionRunning) return isBottomBarInteractionActive
    return shouldRenderBottomBarHeavyInteractiveEffects(
        isTransitionRunning = isTransitionRunning,
        isBottomBarInteractionActive = isBottomBarInteractionActive,
        progress = captureProgress
    )
}

internal fun shouldRenderBottomBarIndicatorBackdrop(
    glassEnabled: Boolean,
    hasContentBackdrop: Boolean,
    indicatorProgress: Float,
    isTransitionRunning: Boolean = false,
    isBottomBarInteractionActive: Boolean = false,
    allowIdleGlassEffect: Boolean = false,
    allowTransitionIndicatorPulse: Boolean = false
): Boolean {
    if (!glassEnabled || !hasContentBackdrop) return false
    if (isTransitionRunning && !allowTransitionIndicatorPulse) return false
    if (allowIdleGlassEffect && indicatorProgress > BottomBarTransientAlphaThreshold) return true
    if (allowTransitionIndicatorPulse && indicatorProgress > BottomBarTransientAlphaThreshold) return true
    return shouldRenderBottomBarHeavyInteractiveEffects(
        isTransitionRunning = isTransitionRunning,
        isBottomBarInteractionActive = isBottomBarInteractionActive,
        progress = indicatorProgress
    )
}

internal fun shouldRenderBottomBarHeavyInteractiveEffects(
    isTransitionRunning: Boolean,
    isBottomBarInteractionActive: Boolean,
    progress: Float
): Boolean {
    if (isTransitionRunning) return false
    return isBottomBarInteractionActive && progress > BottomBarTransientAlphaThreshold
}

internal fun shouldUseBottomBarCombinedIndicatorBackdrop(
    preset: BottomBarLiquidGlassPreset
): Boolean {
    // IOS26_REFINED 在指示器路径上完全等价 BILIPAI_TUNED，仅在壳层材质链上有差异
    return when (preset) {
        BottomBarLiquidGlassPreset.BILIPAI_TUNED,
        BottomBarLiquidGlassPreset.IOS26_REFINED -> true
    }
}

internal fun shouldUseBottomBarIndicatorLens(
    preset: BottomBarLiquidGlassPreset
): Boolean {
    return when (preset) {
        BottomBarLiquidGlassPreset.BILIPAI_TUNED,
        BottomBarLiquidGlassPreset.IOS26_REFINED -> true
    }
}

internal fun shouldComposeBottomBarDockContent(
    dockContentAlpha: Float,
    effectiveSearchExpanded: Boolean
): Boolean {
    return !effectiveSearchExpanded || dockContentAlpha > BottomBarTransientAlphaThreshold
}

internal fun resolveAndroidNativeBottomBarTuning(
    blurEnabled: Boolean,
    darkTheme: Boolean,
): AndroidNativeBottomBarTuning {
    return AndroidNativeBottomBarTuning(
        cornerRadiusDp = 32f,
        shellShadowElevationDp = if (darkTheme) 0.6f else 0.8f,
        shellBlurRadiusDp = if (blurEnabled) 12f else 0f,
        shellSurfaceAlpha = if (blurEnabled) 0.4f else 1f,
        outerHorizontalPaddingDp = 20f,
        innerHorizontalPaddingDp = 4f,
        indicatorHeightDp = 56f,
        indicatorLensRadiusDp = 24f
    )
}

internal fun resolveAndroidNativeBottomBarContainerColor(
    surfaceColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f)
): Color {
    return resolveBottomBarGlassMaterialContainerColor(
        surfaceColor = surfaceColor,
        preset = liquidGlassPreset,
        glassEnabled = glassEnabled,
        fallbackAlpha = tuning.shellSurfaceAlpha,
        liquidGlassTuning = liquidGlassTuning
    )
}

internal fun resolveAndroidNativeFloatingBottomBarContainerColor(
    surfaceColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    blurIntensity: com.android.purebilibili.core.ui.blur.BlurIntensity,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    globalWallpaperVisible: Boolean = false
): Color {
    val resolvedColor = if (glassEnabled) {
        resolveAndroidNativeBottomBarContainerColor(
            surfaceColor = surfaceColor,
            tuning = tuning,
            glassEnabled = true,
            liquidGlassPreset = liquidGlassPreset,
            liquidGlassTuning = liquidGlassTuning
        )
    } else {
        resolveBottomBarSurfaceColor(
            surfaceColor = surfaceColor,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    }
    if (!globalWallpaperVisible || resolvedColor.alpha == 0f) return resolvedColor
    val protectiveColor = resolveGlobalWallpaperProtectiveColor(
        baseColor = surfaceColor,
        lightAlpha = 0.68f,
        darkAlpha = 0.74f
    )
    return resolvedColor.copy(alpha = maxOf(resolvedColor.alpha, protectiveColor.alpha))
}

internal fun resolveAndroidNativeBottomBarGlassEnabled(
    liquidGlassEnabled: Boolean,
    blurEnabled: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT
): Boolean = liquidGlassEnabled && shouldAllowHomeChromeLiquidGlass(sdkInt)

internal fun resolveBottomBarIndicatorEffectsEnabled(
    liquidGlassEnabled: Boolean,
    blurEnabled: Boolean
): Boolean = liquidGlassEnabled || blurEnabled

internal fun shouldUseBottomBarCaptureLens(
    liquidGlassEnabled: Boolean
): Boolean = liquidGlassEnabled

internal fun resolveBiliPaiBottomBarShellColor(
    containerColor: Color,
    liquidGlassEnabled: Boolean,
    darkTheme: Boolean,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f)
): Color {
    return if (liquidGlassEnabled) {
        resolveBiliPaiBottomBarContainerColor(
            darkTheme = darkTheme,
            liquidGlassTuning = liquidGlassTuning
        )
    } else {
        containerColor
    }
}

internal fun shouldBlurBiliPaiBottomBarShell(
    blurEnabled: Boolean
): Boolean = blurEnabled

internal fun shouldUseAndroidNativeFloatingHazeBlur(
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    hasHazeState: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT
): Boolean = blurEnabled &&
    !glassEnabled &&
    hasHazeState &&
    shouldAllowRenderEffectBackedHazeEffect(sdkInt)

internal fun shouldRenderBottomBarLiquidGlassEffects(
    glassEnabled: Boolean,
    forceLowBlurBudget: Boolean,
): Boolean = glassEnabled && !forceLowBlurBudget

internal fun shouldUsePlainMiuixFloatingBar(
    glassEnabled: Boolean,
    blurEnabled: Boolean,
): Boolean = !glassEnabled && !blurEnabled

internal fun Modifier.biliPaiMiuixFloatingDockSurface(
    shape: androidx.compose.ui.graphics.Shape,
    backdrop: MiuixBackdrop?,
    containerColor: Color,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    drawShellLens: Boolean = true,
    /** Scales shell lens refraction for short docks; 1f = full strength. */
    shellLensIntensity: Float = 1f,
    blurRadius: Dp,
    hazeState: HazeState?,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    isScrolling: Boolean = false,
    materialScrollProgress: Float = 0f,
    materialMotionProgress: Float = 0f,
    materialPressProgress: Float = 0f,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f)
): Modifier = composed {
    // The global liquid-glass switch owns primary navigation chrome. Runtime jank
    // downgrades may trim secondary effects, but must not silently turn this surface solid.
    val effectiveForceLowBlurBudget = forceLowBlurBudget
    val isDarkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.background())
    val renderGlassEffects = shouldRenderBottomBarLiquidGlassEffects(
        glassEnabled = glassEnabled,
        forceLowBlurBudget = effectiveForceLowBlurBudget,
    )
    val useHazeBlur = shouldUseAndroidNativeFloatingHazeBlur(
        glassEnabled = renderGlassEffects,
        blurEnabled = blurEnabled,
        hasHazeState = hazeState != null
    )
    val materialSpec = resolveBottomBarGlassMaterialSpec(
        preset = liquidGlassPreset,
        isDarkTheme = isDarkTheme,
        isScrolling = isScrolling,
        scrollProgress = materialScrollProgress,
        glassEnabled = renderGlassEffects,
        motionProgress = materialMotionProgress,
        pressProgress = materialPressProgress,
        liquidGlassTuning = liquidGlassTuning
    )
    val baseHighlight = if (renderGlassEffects) {
        rememberBiliPaiGravityHighlight(iosIndicatorSpecular, extraDegrees = -45f)
    } else {
        null
    }
    val effectiveShellLensIntensity = shellLensIntensity.coerceIn(0f, 1f)

    this
        .then(
            if (useHazeBlur && hazeState != null) {
                Modifier.unifiedBlur(
                    hazeState = hazeState,
                    shape = shape,
                    surfaceType = BlurSurfaceType.BOTTOM_BAR,
                    motionTier = motionTier,
                    isScrolling = false,
                    isTransitionRunning = isTransitionRunning,
                    forceLowBudget = effectiveForceLowBlurBudget
                )
            } else {
                Modifier
            }
        )
        .run {
            if (
                backdrop != null &&
                !useHazeBlur &&
                (renderGlassEffects || blurEnabled)
            ) {
                this
                    .dropShadow(
                        shape = shape,
                        shadow = ComposeShadow(
                            radius = AppSpacingTokens.Small + AppSpacingTokens.Micro,
                            color = OpticalContrastPalette.Shadow,
                            alpha = (if (isDarkTheme) 0.2f else 0.1f) *
                                materialSpec.shadowAlphaScale
                        )
                    )
                    .miuixDrawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            if (renderGlassEffects) {
                                val resolvedShellRefractionAmountDp = if (drawShellLens) {
                                    materialSpec.shellRefractionAmountDp *
                                        effectiveShellLensIntensity
                                } else {
                                    0f
                                }
                                padding = maxOf(
                                    padding,
                                    resolveFloatingDockEffectPaddingDp(
                                        refractionAmountDp = resolvedShellRefractionAmountDp,
                                        pressBloomDp = AppSpacingTokens.Large.value,
                                    ).dp.toPx(),
                                )
                                if (materialSpec.vibrancy) {
                                    miuixVibrancy(liquidGlassTuning.saturation)
                                }
                                val resolvedBlurRadius =
                                    materialSpec.blurRadiusDp?.dp ?: AppSpacingTokens.ExtraSmall
                                miuixBlur(resolvedBlurRadius.toPx(), resolvedBlurRadius.toPx())
                                if (
                                    drawShellLens &&
                                    effectiveShellLensIntensity > 0f &&
                                    materialSpec.shellRefractionHeightDp > 0f &&
                                    materialSpec.shellRefractionAmountDp > 0f
                                ) {
                                    miuixLens(
                                        refractionHeight = (
                                            materialSpec.shellRefractionHeightDp *
                                                effectiveShellLensIntensity
                                            ).dp.toPx(),
                                        refractionAmount = (
                                            materialSpec.shellRefractionAmountDp *
                                                effectiveShellLensIntensity
                                            ).dp.toPx(),
                                        chromaticAberration = materialSpec.shellChromaticAberration
                                    )
                                }
                            } else if (blurEnabled && !useHazeBlur) {
                                val radiusPx = blurRadius.toPx()
                                miuixBlur(radiusPx, radiusPx)
                            }
                        },
                        highlight = {
                            baseHighlight?.value?.copy(
                                alpha = if (renderGlassEffects) {
                                    0.75f * materialSpec.highlightWidthScale *
                                        effectiveShellLensIntensity
                                } else {
                                    0f
                                }
                            )
                        },
                        layerBlock = if (renderGlassEffects) {
                            {
                                val width = size.width.coerceAtLeast(1f)
                                val s = lerp(1f, 1f + AppSpacingTokens.Large.toPx() / width, materialPressProgress)
                                scaleX = s
                                scaleY = s
                            }
                        } else null,
                        onDrawSurface = {
                            drawRect(containerColor)
                            if (liquidGlassTuning.contentReadabilityScrimAlpha > 0f) {
                                drawRect(
                                    (if (isDarkTheme) Color.Black else Color.White).copy(
                                        alpha = liquidGlassTuning.contentReadabilityScrimAlpha
                                    )
                                )
                            }
                            if (materialSpec.foregroundTint.alpha > 0f) {
                                drawRect(materialSpec.foregroundTint)
                            }
                        }
                    )
                    .run {
                        val innerRimGlow = materialSpec.innerRimGlow
                        if (renderGlassEffects && innerRimGlow != null) {
                            miuixInnerShadow(shape = shape) {
                                MiuixInnerShadow(
                                    radius = innerRimGlow.radiusDp.dp,
                                    color = if (isDarkTheme) {
                                        OpticalContrastPalette.Highlight
                                    } else {
                                        OpticalContrastPalette.Shadow
                                    },
                                    alpha = innerRimGlow.alpha
                                )
                            }
                        } else {
                            this
                        }
                    }
            } else {
                background(containerColor, shape)
            }
        }
        .clip(shape)
}

internal fun resolveAndroidNativeIndicatorSpec(
    isMoving: Boolean
): AndroidNativeIndicatorSpec {
    return AndroidNativeIndicatorSpec(
        usesLens = isMoving,
        captureTintedContentLayer = isMoving
    )
}

internal fun resolveAndroidNativeIndicatorColor(
    themeColor: Color,
    darkTheme: Boolean
): Color {
    val softened = androidx.compose.ui.graphics.lerp(
        start = themeColor,
        stop = OpticalContrastPalette.Highlight,
        fraction = if (darkTheme) 0.58f else 0.82f
    )
    return softened.copy(alpha = if (darkTheme) 0.42f else 0.82f)
}

internal fun resolveAndroidNativeExportTintColor(
    themeColor: Color,
    darkTheme: Boolean,
    containerColor: Color = Color.Unspecified,
    glassEnabled: Boolean = false
): Color {
    return themeColor
}

internal data class BottomBarSkinContentColors(
    val selectedColor: Color,
    val unselectedColor: Color,
    val labelScrimColor: Color = Color.Transparent,
    val labelScrimAlpha: Float = 0f
)

internal fun resolveBottomBarSkinContentColors(
    selectedColor: Color,
    unselectedColor: Color,
    skinTrimTint: Color?
): BottomBarSkinContentColors {
    if (skinTrimTint == null) {
        return BottomBarSkinContentColors(
            selectedColor = selectedColor,
            unselectedColor = unselectedColor,
        )
    }
    val readableSelectedColor = resolveReadableBottomBarSkinForeground(
        preferredColor = selectedColor,
        backgroundColor = skinTrimTint,
    )
    val readableUnselectedColor = resolveReadableBottomBarSkinForeground(
        preferredColor = unselectedColor,
        backgroundColor = skinTrimTint,
    )
    val labelScrimColor = if (readableUnselectedColor.luminance() >= 0.5f) {
        OpticalContrastPalette.Shadow
    } else {
        OpticalContrastPalette.Highlight
    }
    return BottomBarSkinContentColors(
        selectedColor = readableSelectedColor,
        unselectedColor = readableUnselectedColor,
        labelScrimColor = labelScrimColor,
        labelScrimAlpha = 0f,
    )
}

internal fun resolveReadableBottomBarSkinForeground(
    preferredColor: Color,
    backgroundColor: Color,
): Color {
    val opaquePreferred = preferredColor.copy(alpha = 1f)
    if (bottomBarColorContrastRatio(opaquePreferred, backgroundColor) >= 3f) {
        return preferredColor
    }
    return listOf(OpticalContrastPalette.Shadow, OpticalContrastPalette.Highlight)
        .maxBy { candidate -> bottomBarColorContrastRatio(candidate, backgroundColor) }
}

private fun bottomBarColorContrastRatio(foreground: Color, background: Color): Float {
    val lighter = maxOf(foreground.luminance(), background.luminance())
    val darker = minOf(foreground.luminance(), background.luminance())
    return (lighter + 0.05f) / (darker + 0.05f)
}

@Composable
private fun Modifier.bottomBarSkinLabelScrim(
    color: Color,
    alpha: Float
): Modifier {
    if (alpha <= 0f) return this
    return this
        .clip(AppShapes.container(ContainerLevel.Chip))
        .background(color.copy(alpha = alpha.coerceIn(0f, 1f)))
        .padding(horizontal = AppSpacingTokens.ExtraSmall, vertical = AppSpacingTokens.Micro / 2)
}

internal fun resolveAndroidNativeIdleIndicatorSurfaceColor(
    darkTheme: Boolean
): Color {
    return if (darkTheme) {
        OpticalContrastPalette.Highlight.copy(alpha = 0.1f)
    } else {
        OpticalContrastPalette.Shadow.copy(alpha = 0.1f)
    }
}

internal fun resolveBiliPaiBottomBarContainerColor(
    darkTheme: Boolean,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f)
): Color {
    val surfaceContainer = if (darkTheme) {
        HomeVisualPalette.BiliPaiDarkSurface
    } else {
        OpticalContrastPalette.Highlight
    }
    return surfaceContainer.copy(alpha = liquidGlassTuning.surfaceAlpha)
}

internal fun resolveBottomBarDarkTheme(backgroundColor: Color): Boolean {
    return backgroundColor.luminance() < 0.5f
}

internal fun resolveBottomBarIdleIndicatorSurfaceColor(
    preset: BottomBarLiquidGlassPreset,
    darkTheme: Boolean
): Color {
    return resolveAndroidNativeIdleIndicatorSurfaceColor(darkTheme)
}

internal fun resolveAndroidNativePanelOffsetFraction(
    position: Float,
    velocity: Float
): Float {
    val fractionalOffset = position - position.roundToInt().toFloat()
    if (abs(fractionalOffset) > 0.001f) {
        return fractionalOffset.coerceIn(-1f, 1f)
    }
    return (velocity / 2200f).coerceIn(-0.18f, 0.18f)
}

internal fun Md3BottomBarDisplayMode.toAppPlatformNavigationDisplayMode(): AppPlatformNavigationBarDisplayMode {
    return when (this) {
        Md3BottomBarDisplayMode.IconAndText -> AppPlatformNavigationBarDisplayMode.ICON_AND_TEXT
        Md3BottomBarDisplayMode.IconOnly -> AppPlatformNavigationBarDisplayMode.ICON_ONLY
        Md3BottomBarDisplayMode.TextOnly -> AppPlatformNavigationBarDisplayMode.ICON_WITH_SELECTED_LABEL
    }
}

/** Official [MiuixNavigationBarItem] cannot host skin bitmaps or label scrims. */
internal fun shouldUseMiuixOfficialNavigationBarItem(
    skinIconPath: String?,
    labelScrimAlpha: Float
): Boolean = skinIconPath == null && labelScrimAlpha <= 0f

internal fun resolveMiuixDockedBottomBarItemColor(
    selected: Boolean,
    selectedColor: Color,
    unselectedColor: Color
): Color = if (selected) selectedColor else unselectedColor

internal fun resolveBottomBarFloatingHeightDp(
    labelMode: Int,
    isTablet: Boolean
): Float {
    return when (labelMode) {
        0 -> if (isTablet) 72f else 66f
        2 -> if (isTablet) 54f else 52f
        else -> if (isTablet) 64f else 58f
    }
}

internal fun normalizeBottomBarLabelMode(requestedLabelMode: Int): Int = when (requestedLabelMode) {
    0, 1, 2 -> requestedLabelMode
    else -> 0
}

internal fun shouldShowBottomBarIcon(labelMode: Int): Boolean {
    return when (normalizeBottomBarLabelMode(labelMode)) {
        2 -> false
        else -> true
    }
}

internal fun shouldShowBottomBarDynamicReminderBadge(
    item: BottomNavItem?,
    unreadCount: Int
): Boolean = item == BottomNavItem.DYNAMIC && unreadCount > 0

internal fun formatBottomBarDynamicReminderBadge(unreadCount: Int): String? {
    return when {
        unreadCount <= 0 -> null
        unreadCount > 999 -> "999+"
        else -> unreadCount.toString()
    }
}

internal fun shouldShowBottomBarText(labelMode: Int): Boolean {
    return when (normalizeBottomBarLabelMode(labelMode)) {
        1 -> false
        else -> true
    }
}

internal fun resolveBottomBarBottomPaddingDp(
    isFloating: Boolean,
    isTablet: Boolean
): Float {
    if (!isFloating) return 0f
    return if (isTablet) 18f else 12f
}

internal data class BottomBarIndicatorPolicy(
    val widthMultiplier: Float,
    val minWidthDp: Float,
    val maxWidthDp: Float,
    val maxWidthToItemRatio: Float,
    val clampToBounds: Boolean,
    val edgeInsetDp: Float
)

internal data class BottomBarIndicatorVisualPolicy(
    val isInMotion: Boolean,
    val shouldRefract: Boolean,
    val useNeutralTint: Boolean
)

internal const val BOTTOM_BAR_REFRACTION_IDLE_HOLD_MS = 96L
internal const val BOTTOM_BAR_INDICATOR_DRAG_SCALE_TARGET =
    com.android.purebilibili.core.ui.BottomBarReferencePressedScale

internal fun resolveBottomBarCaptureSafeInsetDp(
    indicatorWidthDp: Float,
    refractionHeightDp: Float,
    refractionAmountDp: Float,
    panelOffsetDp: Float,
    dragScaleTarget: Float = BOTTOM_BAR_INDICATOR_DRAG_SCALE_TARGET
): Float {
    val scaleOverflowDp = (
        indicatorWidthDp.coerceAtLeast(0f) *
            (dragScaleTarget.coerceAtLeast(1f) - 1f) /
            2f
        )
    return scaleOverflowDp +
        maxOf(refractionHeightDp, refractionAmountDp).coerceAtLeast(0f) +
        kotlin.math.abs(panelOffsetDp)
}
private const val BILIPAI_INDICATOR_VELOCITY_NORMALIZATION_DIVISOR = 10f
private const val BILIPAI_INDICATOR_VELOCITY_SCALE_X_MULTIPLIER = 0.75f
private const val BILIPAI_INDICATOR_VELOCITY_CLAMP = 0.2f
internal fun resolveBottomBarIndicatorVisualPolicyWithHold(
    basePolicy: BottomBarIndicatorVisualPolicy,
    keepRefractionLayerAlive: Boolean
): BottomBarIndicatorVisualPolicy {
    return if (basePolicy.shouldRefract || !keepRefractionLayerAlive) {
        basePolicy
    } else {
        basePolicy.copy(shouldRefract = true)
    }
}

internal data class BottomBarRefractionLayerPolicy(
    val captureTintedContentLayer: Boolean,
    val useCombinedBackdrop: Boolean
)

internal data class BottomBarRefractionMotionProfile(
    val progress: Float,
    val exportPanelOffsetFraction: Float,
    val indicatorPanelOffsetFraction: Float,
    val visiblePanelOffsetFraction: Float,
    val visibleSelectionEmphasis: Float,
    val exportSelectionEmphasis: Float,
    val exportCaptureWidthScale: Float
)

internal data class BottomBarPresetPanelOffsets(
    val visiblePanelOffsetPx: Float,
    val exportPanelOffsetPx: Float,
    val indicatorPanelOffsetPx: Float
)

internal data class BottomBarBackdropPresetLensSpec(
    val refractionHeightDp: Float,
    val refractionAmountDp: Float
)

internal data class BottomBarBackdropPresetProgress(
    val shellProgress: Float,
    val captureProgress: Float,
    val indicatorProgress: Float
)

internal data class BottomBarSearchLaunchMorphSpec(
    val expandDurationMillis: Int,
    val postHandoffResetDelayMillis: Long
)

internal fun resolveBottomBarSearchLaunchMorphSpec(): BottomBarSearchLaunchMorphSpec {
    return BottomBarSearchLaunchMorphSpec(
        expandDurationMillis = 190,
        postHandoffResetDelayMillis = 40L
    )
}

internal data class BottomBarItemMotionVisual(
    val coverage: Float,
    val scale: Float,
) {
    val themeWeight: Float get() = coverage
    val useSelectedIcon: Boolean get() = coverage >= 0.5f
    val selectedIconAlpha: Float get() = coverage
}

internal data class BottomBarClickPulseTransform(
    val scaleX: Float,
    val scaleY: Float = 1f
)

internal data class BottomBarIndicatorLayerTransform(
    val scaleX: Float,
    val scaleY: Float
)

internal fun resolveBottomBarClickPulseTransform(
    progress: Float
): BottomBarClickPulseTransform {
    val clamped = progress.coerceIn(0f, 1f)
    val compressionEnd = 0.18f
    val compressionAmount = 0.055f
    val reboundAmount = 0.18f
    val scaleX = when {
        clamped >= 1f -> 1f
        clamped <= compressionEnd -> {
            val pressProgress = (clamped / compressionEnd).coerceIn(0f, 1f)
            1f - compressionAmount * EaseOut.transform(pressProgress)
        }
        else -> {
            val releaseProgress = ((clamped - compressionEnd) / (1f - compressionEnd)).coerceIn(0f, 1f)
            val damping = ((1f - releaseProgress) * exp(-3.0 * releaseProgress)).toFloat()
            val wave = (
                -compressionAmount * cos(PI * releaseProgress) +
                    reboundAmount * sin(PI * releaseProgress)
                ).toFloat()
            1f + damping * wave
        }
    }
    return BottomBarClickPulseTransform(scaleX = scaleX)
}

internal fun resolveBottomBarLiquidGlassLensProgress(
    motionProgress: Float,
    idleProgress: Float = 0f
): Float {
    return lerp(
        idleProgress.coerceIn(0f, 1f),
        1f,
        motionProgress.coerceIn(0f, 1f)
    )
}

internal fun resolveBottomBarLiquidGlassHighlightAlpha(
    motionProgress: Float
): Float {
    return resolveBottomBarLiquidGlassLensProgress(
        motionProgress = motionProgress,
        idleProgress = 0.22f
    )
}

internal fun resolveBottomBarIndicatorGlowAlpha(
    glassEnabled: Boolean,
    pressProgress: Float,
    motionProgress: Float = 0f
): Float {
    if (!glassEnabled) return 0f
    return maxOf(pressProgress, motionProgress).coerceIn(0f, 1f)
}

internal fun resolveBottomBarBackdropPresetCaptureLens(
    progress: Float
): BottomBarBackdropPresetLensSpec {
    val clamped = progress.coerceIn(0f, 1f)
    return BottomBarBackdropPresetLensSpec(
        refractionHeightDp = 24f * clamped,
        refractionAmountDp = 24f * clamped
    )
}

internal fun resolveBottomBarBackdropPresetIndicatorLens(
    progress: Float
): BottomBarBackdropPresetLensSpec {
    val clamped = progress.coerceIn(0f, 1f)
    return BottomBarBackdropPresetLensSpec(
        refractionHeightDp = 10f * clamped,
        refractionAmountDp = 14f * clamped
    )
}

internal fun resolveBottomBarBackdropPresetProgress(
    motionProgress: Float,
    verticalProgress: Float,
    pressProgress: Float
): BottomBarBackdropPresetProgress {
    val clampedMotion = motionProgress.coerceIn(0f, 1f)
    val clampedPress = pressProgress.coerceIn(0f, 1f)
    return BottomBarBackdropPresetProgress(
        shellProgress = clampedPress,
        captureProgress = maxOf(clampedMotion, clampedPress * 0.72f),
        indicatorProgress = maxOf(clampedMotion, clampedPress)
    )
}

internal fun resolveBottomBarEffectiveBackdropPresetProgress(
    preset: BottomBarLiquidGlassPreset,
    motionProgress: Float,
    pressProgress: Float
): BottomBarBackdropPresetProgress {
    val base = resolveBottomBarBackdropPresetProgress(
        motionProgress = motionProgress,
        verticalProgress = 0f,
        pressProgress = pressProgress
    )
    return when (preset) {
        BottomBarLiquidGlassPreset.BILIPAI_TUNED,
        BottomBarLiquidGlassPreset.IOS26_REFINED -> base
    }
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveBottomBarIndicatorLayerTransform(
    motionProgress: Float,
    velocityItemsPerSecond: Float,
    isDragging: Boolean = true,
    dragScaleProgress: Float = if (isDragging) 1f else 0f,
    dragScaleTransform: BottomBarIndicatorLayerTransform? = null,
    dragScaleTarget: Float = BOTTOM_BAR_INDICATOR_DRAG_SCALE_TARGET,
    motionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec = resolveBottomBarMotionSpec()
): BottomBarIndicatorLayerTransform {
    val clampedProgress = motionProgress.coerceIn(0f, 1f)
    val clampedDragScaleProgress = dragScaleProgress.coerceIn(0f, 1f)
    val baseScale = lerp(
        start = 1f,
        stop = dragScaleTarget.coerceAtLeast(1f),
        fraction = clampedDragScaleProgress
    )
    val baseScaleX = dragScaleTransform?.scaleX ?: baseScale
    val baseScaleY = dragScaleTransform?.scaleY ?: baseScale
    // 对齐 BiliPai FloatingBottomBar 的胶囊速度形变和基础按压放大倍数。
    val velocity = if (isDragging || clampedDragScaleProgress > 0f) {
        velocityItemsPerSecond / BILIPAI_INDICATOR_VELOCITY_NORMALIZATION_DIVISOR
    } else {
        0f
    }
    // HyperIsland: the leading/trailing direction affects both longitudinal stretch and
    // cross-axis compression, producing the asymmetric liquid handoff while dragging.
    val velocityScaleX = (velocity * BILIPAI_INDICATOR_VELOCITY_SCALE_X_MULTIPLIER)
        .coerceIn(-BILIPAI_INDICATOR_VELOCITY_CLAMP, BILIPAI_INDICATOR_VELOCITY_CLAMP)
    val velocityScaleY = (velocity * 0.25f)
        .coerceIn(-BILIPAI_INDICATOR_VELOCITY_CLAMP, BILIPAI_INDICATOR_VELOCITY_CLAMP)
    return BottomBarIndicatorLayerTransform(
        scaleX = baseScaleX / (1f - velocityScaleX),
        scaleY = baseScaleY * (1f - velocityScaleY)
    )
}

/**
 * 指示器按下放大的进度。
 *
 * 照搬 HyperIsland LiquidGlassNavigationBar 的
 * `scaleXAnimation.animateTo(78f / 56f, spring(0.6f, 250f, 0.001f))`：
 * 进入与退出共用同一条 spring(0.6f, 250f)，不再使用 BiliPai 早先的
 * tween(90ms 进入 / 220ms 退出)。
 */
@Composable
internal fun rememberBottomBarIndicatorDragScaleProgress(
    isDragging: Boolean
): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(isDragging) {
        progress.animateTo(
            targetValue = if (isDragging) 1f else 0f,
            animationSpec = spring(
                dampingRatio = 0.6f,
                stiffness = 250f,
                visibilityThreshold = 0.001f
            )
        )
    }
    return progress.value
}

/**
 * 指示器按下/拖拽放大的 X/Y 形变。
 *
 * 照搬 HyperIsland LiquidGlassNavigationBar 的两条独立 Animatable：
 * `scaleXAnimation.animateTo(78f / 56f, spring(0.6f, 250f, 0.001f))` 与
 * `scaleYAnimation.animateTo(78f / 56f, spring(0.7f, 250f, 0.001f))` ——
 * BiliPai 不再把两条弹簧压成单条 progress。
 */
@Composable
internal fun rememberBottomBarIndicatorLayerScaleTransform(
    active: Boolean,
    target: Float = BOTTOM_BAR_INDICATOR_DRAG_SCALE_TARGET
): BottomBarIndicatorLayerTransform {
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    LaunchedEffect(active, target) {
        val resolvedTarget = if (active) target.coerceAtLeast(1f) else 1f
        launch { scaleX.animateTo(resolvedTarget, spring(0.6f, 250f, 0.001f)) }
        launch { scaleY.animateTo(resolvedTarget, spring(0.7f, 250f, 0.001f)) }
    }
    return BottomBarIndicatorLayerTransform(scaleX = scaleX.value, scaleY = scaleY.value)
}

internal fun resolveBottomBarVisualIndicatorPosition(
    rawPosition: Float,
    itemCount: Int
): Float {
    if (itemCount <= 1) return 0f
    return rawPosition.coerceIn(0f, (itemCount - 1).toFloat())
}

internal fun resolveBottomBarEdgeStrain(
    rawPosition: Float,
    itemCount: Int
): Float {
    if (itemCount <= 1) return 0f
    val visualPosition = resolveBottomBarVisualIndicatorPosition(
        rawPosition = rawPosition,
        itemCount = itemCount
    )
    return (rawPosition - visualPosition).coerceIn(-1f, 1f)
}

internal fun resolveBottomBarEdgeCompressionScaleX(
    edgeStrain: Float,
    maxCompression: Float = 0.035f
): Float {
    val progress = abs(edgeStrain).coerceIn(0f, 1f)
    return 1f - maxCompression * EaseOut.transform(progress)
}

internal fun resolveBottomBarSettleReboundTransform(
    progress: Float
): BottomBarClickPulseTransform {
    val clamped = progress.coerceIn(0f, 1f)
    val compressionEnd = 0.20f
    val compressionScaleXAmount = 0.035f
    val compressionScaleYAmount = 0.028f
    val reboundScaleXAmount = 0.085f
    val reboundScaleYAmount = 0.075f
    if (clamped >= 1f) {
        return BottomBarClickPulseTransform(scaleX = 1f, scaleY = 1f)
    }
    if (clamped <= compressionEnd) {
        val compressionProgress = (clamped / compressionEnd).coerceIn(0f, 1f)
        val easedProgress = EaseOut.transform(compressionProgress)
        return BottomBarClickPulseTransform(
            scaleX = 1f - compressionScaleXAmount * easedProgress,
            scaleY = 1f + compressionScaleYAmount * easedProgress
        )
    }
    val releaseProgress = ((clamped - compressionEnd) / (1f - compressionEnd)).coerceIn(0f, 1f)
    val damping = ((1f - releaseProgress) * exp(-3.2 * releaseProgress)).toFloat()
    val reboundWave = damping * sin(PI * releaseProgress).toFloat()
    return BottomBarClickPulseTransform(
        scaleX = 1f + reboundScaleXAmount * reboundWave,
        scaleY = 1f + reboundScaleYAmount * reboundWave
    )
}

@Composable
internal fun rememberBottomBarClickPulseTransform(
    pulseKey: Int
): BottomBarClickPulseTransform {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = bottomBarClickPulseMotionSpec()
        )
    }
    return resolveBottomBarClickPulseTransform(progress.value)
}

@Composable
private fun rememberBottomBarTapSwitchPressProgress(
    pulseKey: Int
): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey <= 0) return@LaunchedEffect
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = bottomBarTapReleaseMotionSpec()
        )
    }
    return progress.value
}

@Composable
internal fun rememberBottomBarSettleReboundTransform(
    pulseKey: Int
): BottomBarClickPulseTransform {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = bottomBarSettleReboundMotionSpec()
        )
    }
    return resolveBottomBarSettleReboundTransform(progress.value)
}

@Suppress("UNUSED_PARAMETER")
internal fun resolveBottomBarItemCoverage(
    itemIndex: Int,
    indicatorPosition: Float,
    currentSelectedIndex: Int,
    motionProgress: Float
): Float {
    return (1f - abs(itemIndex.toFloat() - indicatorPosition)).coerceIn(0f, 1f)
}

internal fun resolveBottomBarItemMotionScale(
    coverage: Float,
    motionProgress: Float,
    maxScale: Float = 1.2f
): Float {
    val progress = motionProgress.coerceIn(0f, 1f)
    if (progress <= 0f) return 1f
    return lerp(1f, maxScale, coverage.coerceIn(0f, 1f) * progress)
}

internal fun resolveBottomBarSampledItemMotionScale(
    coverage: Float,
    motionProgress: Float,
    pressProgress: Float,
    maxScale: Float = 1.2f
): Float {
    val coverageScale = resolveBottomBarItemMotionScale(
        coverage = coverage,
        motionProgress = motionProgress,
        maxScale = maxScale
    )
    val pressScale = resolveBottomBarItemMotionScale(
        coverage = 1f,
        motionProgress = pressProgress,
        maxScale = maxScale
    )
    return maxOf(coverageScale, pressScale)
}

internal fun resolveBottomBarItemMotionVisual(
    itemIndex: Int,
    indicatorPosition: Float,
    currentSelectedIndex: Int,
    motionProgress: Float,
    selectionEmphasis: Float,
    maxScale: Float = 1.2f
): BottomBarItemMotionVisual {
    val coverage = resolveBottomBarItemCoverage(
        itemIndex = itemIndex,
        indicatorPosition = indicatorPosition,
        currentSelectedIndex = currentSelectedIndex,
        motionProgress = motionProgress
    )
    return BottomBarItemMotionVisual(
        coverage = coverage,
        scale = resolveBottomBarItemMotionScale(
            coverage = coverage,
            motionProgress = motionProgress,
            maxScale = maxScale
        )
    )
}

internal fun resolveBottomBarIndicatorVisualPolicy(
    position: Float,
    isDragging: Boolean,
    velocity: Float,
    useNeutralIndicatorTint: Boolean,
    motionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec = resolveBottomBarMotionSpec()
): BottomBarIndicatorVisualPolicy {
    val isFractional = abs(position - position.roundToInt().toFloat()) > 0.001f
    val isInMotion = isDragging ||
        isFractional ||
        abs(velocity) > motionSpec.refraction.movingVelocityThresholdPxPerSecond
    return BottomBarIndicatorVisualPolicy(
        isInMotion = isInMotion,
        shouldRefract = isInMotion,
        useNeutralTint = isInMotion && useNeutralIndicatorTint
    )
}

internal fun resolveBottomBarRefractionLayerPolicy(
    isFloating: Boolean,
    isLiquidGlassEnabled: Boolean,
    indicatorVisualPolicy: BottomBarIndicatorVisualPolicy
): BottomBarRefractionLayerPolicy {
    val captureTintedContentLayer =
        isFloating && isLiquidGlassEnabled && indicatorVisualPolicy.shouldRefract
    return BottomBarRefractionLayerPolicy(
        captureTintedContentLayer = captureTintedContentLayer,
        useCombinedBackdrop = captureTintedContentLayer
    )
}

internal fun resolveBottomBarRefractionMotionProfile(
    position: Float,
    velocity: Float,
    isDragging: Boolean,
    motionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec = resolveBottomBarMotionSpec()
): BottomBarRefractionMotionProfile {
    val signedFractionalOffset = position - position.roundToInt().toFloat()
    val fractionalProgress = (abs(signedFractionalOffset) * 2f).coerceIn(0f, 1f)
    val speedProgress = (abs(velocity) / motionSpec.refraction.speedProgressDivisorPxPerSecond)
        .coerceIn(0f, 1f)
    val baseProgress = fractionalProgress.coerceAtLeast(speedProgress)
    val rawProgress = when {
        isDragging -> baseProgress.coerceAtLeast(motionSpec.refraction.dragProgressFloor)
        baseProgress > motionSpec.refraction.motionDeadzone -> baseProgress
        else -> 0f
    }
    if (rawProgress <= 0f) {
        return BottomBarRefractionMotionProfile(
            progress = 0f,
            exportPanelOffsetFraction = 0f,
            indicatorPanelOffsetFraction = 0f,
            visiblePanelOffsetFraction = 0f,
            visibleSelectionEmphasis = 1f,
            exportSelectionEmphasis = 1f,
            exportCaptureWidthScale = 1f
        )
    }

    val progress = (rawProgress * rawProgress * (3f - 2f * rawProgress)).coerceIn(0f, 1f)
    val direction = when {
        abs(velocity) > 24f -> sign(velocity)
        abs(signedFractionalOffset) > 0.001f -> sign(signedFractionalOffset)
        else -> 0f
    }
    val panelOffsetFraction = direction * EaseOut.transform(progress)

    return BottomBarRefractionMotionProfile(
        progress = progress,
        exportPanelOffsetFraction = panelOffsetFraction * 0.5f,
        indicatorPanelOffsetFraction = panelOffsetFraction,
        visiblePanelOffsetFraction = panelOffsetFraction * 0.25f,
        visibleSelectionEmphasis = lerp(1f, 0.28f, progress),
        exportSelectionEmphasis = lerp(1f, 0.52f, progress),
        exportCaptureWidthScale = lerp(1f, 1.16f, progress)
    )
}

internal fun resolveBottomBarEffectiveRefractionMotionProfile(
    preset: BottomBarLiquidGlassPreset,
    profile: BottomBarRefractionMotionProfile
): BottomBarRefractionMotionProfile {
    return when (preset) {
        BottomBarLiquidGlassPreset.BILIPAI_TUNED,
        BottomBarLiquidGlassPreset.IOS26_REFINED -> profile
    }
}

internal fun resolveBottomBarPresetPanelOffsets(
    preset: BottomBarLiquidGlassPreset,
    rawPanelOffsetPx: Float
): BottomBarPresetPanelOffsets {
    return when (preset) {
        BottomBarLiquidGlassPreset.BILIPAI_TUNED,
        BottomBarLiquidGlassPreset.IOS26_REFINED -> BottomBarPresetPanelOffsets(
            visiblePanelOffsetPx = rawPanelOffsetPx,
            exportPanelOffsetPx = rawPanelOffsetPx,
            indicatorPanelOffsetPx = rawPanelOffsetPx
        )
    }
}

internal fun resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme: Boolean): Color {
    return if (isDarkTheme) {
        iOSSystemGray6
    } else {
        OpticalContrastPalette.Highlight
    }
}

internal fun resolveIosFloatingBottomIndicatorColor(
    themeColor: Color = Color.Unspecified,
    isDarkTheme: Boolean,
    visualPolicy: BottomBarIndicatorVisualPolicy,
    liquidGlassTuning: LiquidGlassTuning
): Color {
    val baseColor = resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = isDarkTheme)
    return baseColor.copy(alpha = liquidGlassTuning.indicatorTintAlpha)
}

internal fun resolveIosFloatingBottomIndicatorTintAlpha(
    visualPolicy: BottomBarIndicatorVisualPolicy,
    isDarkTheme: Boolean,
    liquidGlassProgress: Float,
    configuredAlpha: Float
): Float {
    val baseAlpha = resolveBottomBarIndicatorTintAlpha(
        shouldRefract = visualPolicy.shouldRefract,
        liquidGlassProgress = liquidGlassProgress,
        configuredAlpha = configuredAlpha
    )
    if (!visualPolicy.shouldRefract) return baseAlpha
    val movingAlphaFloor = if (isDarkTheme) 0.38f else 0.40f
    return baseAlpha.coerceAtLeast(movingAlphaFloor)
}

internal fun resolveBottomBarIndicatorTintAlpha(
    shouldRefract: Boolean,
    liquidGlassProgress: Float,
    configuredAlpha: Float
): Float {
    if (shouldRefract) return configuredAlpha
    val minAlpha = lerp(
        start = 0.38f,
        stop = 0.56f,
        fraction = liquidGlassProgress.coerceIn(0f, 1f)
    )
    return configuredAlpha.coerceAtLeast(minAlpha)
}

internal fun resolveBottomBarIndicatorTintAlpha(
    shouldRefract: Boolean,
    liquidGlassMode: LiquidGlassMode,
    configuredAlpha: Float
): Float {
    return resolveBottomBarIndicatorTintAlpha(
        shouldRefract = shouldRefract,
        liquidGlassProgress = when (liquidGlassMode) {
            LiquidGlassMode.CLEAR -> 0f
            LiquidGlassMode.BALANCED -> 0.5f
            LiquidGlassMode.FROSTED -> 1f
        },
        configuredAlpha = configuredAlpha
    )
}

internal fun resolveBottomBarIndicatorPolicy(itemCount: Int): BottomBarIndicatorPolicy {
    val topTuning = resolveTopTabVisualTuning()
    return if (itemCount >= 5) {
        BottomBarIndicatorPolicy(
            widthMultiplier = topTuning.floatingIndicatorWidthMultiplier + 0.02f,
            minWidthDp = topTuning.floatingIndicatorMinWidthDp + 2f,
            maxWidthDp = topTuning.floatingIndicatorMaxWidthDp + 2f,
            maxWidthToItemRatio = topTuning.floatingIndicatorMaxWidthToItemRatio + 0.02f,
            clampToBounds = true,
            edgeInsetDp = 2f
        )
    } else {
        BottomBarIndicatorPolicy(
            widthMultiplier = topTuning.floatingIndicatorWidthMultiplier + 0.04f,
            minWidthDp = topTuning.floatingIndicatorMinWidthDp + 4f,
            maxWidthDp = topTuning.floatingIndicatorMaxWidthDp + 4f,
            maxWidthToItemRatio = topTuning.floatingIndicatorMaxWidthToItemRatio + 0.04f,
            clampToBounds = true,
            edgeInsetDp = 2f
        )
    }
}

internal fun resolveBottomIndicatorHeightDp(
    labelMode: Int,
    isTablet: Boolean,
    itemCount: Int
): Float {
    return when {
        labelMode == 0 && isTablet && itemCount >= 5 -> 56f
        labelMode == 0 && isTablet -> 60f
        labelMode == 0 && itemCount >= 5 -> 50f
        labelMode == 0 -> 58f
        else -> 54f
    }
}

internal fun resolveBottomBarLayoutPolicy(
    containerWidth: Dp,
    itemCount: Int,
    isTablet: Boolean,
    labelMode: Int,
    isFloating: Boolean
): BottomBarLayoutPolicy {
    if (!isFloating) {
        return BottomBarLayoutPolicy(
            horizontalPadding = AppSpacingTokens.None,
            rowPadding = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall,
            maxBarWidth = containerWidth
        )
    }

    val safeItemCount = itemCount.coerceAtLeast(1)
    val rowPadding = when {
        isTablet && safeItemCount >= 6 -> AppSpacingTokens.Large
        isTablet -> AppSpacingTokens.Large + AppSpacingTokens.Micro
        safeItemCount >= 5 -> AppSpacingTokens.Medium
        else -> AppSpacingTokens.Large
    }
    val normalizedLabelMode = when (labelMode) {
        0, 1, 2 -> labelMode
        else -> 0
    }
    val minItemWidth = when (normalizedLabelMode) {
        0 -> if (isTablet) AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Medium + AppSpacingTokens.Micro else AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraSmall
        2 -> if (isTablet) AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Medium else AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraSmall
        else -> if (isTablet) AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Small + AppSpacingTokens.Micro else AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Micro
    }
    val preferredItemWidth = when (normalizedLabelMode) {
        0 -> if (isTablet) AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall else AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.DoubleExtraLarge
        2 -> if (isTablet) AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.DoubleExtraLarge else AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraLarge + AppSpacingTokens.Micro
        else -> if (isTablet) AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraLarge + AppSpacingTokens.ExtraSmall else AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraLarge
    }
    val minBarWidth = (rowPadding * 2) + (minItemWidth * safeItemCount)
    val preferredBarWidth = (rowPadding * 2) + (preferredItemWidth * safeItemCount)

    val phoneRatio = when {
        safeItemCount >= 6 -> 0.84f
        safeItemCount == 5 -> 0.88f
        safeItemCount == 4 -> 0.92f
        else -> 0.93f
    }
    val widthRatio = if (isTablet) 0.86f else phoneRatio
    val visualCap = containerWidth * widthRatio
    val hardCap = if (isTablet) AppSpacingTokens.TripleExtraLarge * 13 + AppSpacingTokens.Large else AppSpacingTokens.TripleExtraLarge * 9
    val minEdgePadding = if (isTablet) AppSpacingTokens.Large else AppSpacingTokens.Small + AppSpacingTokens.Micro
    val containerCap = (containerWidth - (minEdgePadding * 2)).coerceAtLeast(AppSpacingTokens.None)
    val maxAllowed = minOf(hardCap, visualCap, containerCap)

    val resolvedBarWidth = maxOf(
        minBarWidth,
        minOf(preferredBarWidth, maxAllowed)
    ).coerceAtMost(containerWidth)

    val horizontalPadding = ((containerWidth - resolvedBarWidth) / 2).coerceAtLeast(AppSpacingTokens.None)
    return BottomBarLayoutPolicy(
        horizontalPadding = horizontalPadding,
        rowPadding = rowPadding,
        maxBarWidth = resolvedBarWidth
    )
}

/**
 *  iOS 风格磨砂玻璃底部导航栏
 * 
 * 特性：
 * - 实时磨砂玻璃效果 (使用 Haze 库)
 * - 悬浮圆角设计
 * - 自动适配深色/浅色模式
 * -  点击触觉反馈
 */
@Composable
fun FrostedBottomBar(
    currentItem: BottomNavItem = BottomNavItem.HOME,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    nowPlayingContent: (@Composable (Modifier, Float, Boolean, Float) -> Unit)? = null,
    hazeState: HazeState? = null,
    isFloating: Boolean = true,
    labelMode: Int = 1,
    homeSettings: com.android.purebilibili.core.store.HomeSettings = com.android.purebilibili.core.store.HomeSettings(),
    onHomeDoubleTap: () -> Unit = {},
    onDynamicDoubleTap: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSearchKeywordSubmit: (String) -> Unit = {},
    searchLaunchKey: Int = 0,
    onSearchLaunchTransitionFinished: (Int) -> Unit = {},
    visibleItems: List<BottomNavItem> = listOf(
        BottomNavItem.HOME,
        BottomNavItem.DYNAMIC,
        BottomNavItem.HISTORY,
        BottomNavItem.LISTEN_VIDEO,
        BottomNavItem.PROFILE
    ),
    itemColorIndices: Map<String, Int> = emptyMap(),
    itemLabels: Map<String, String> = emptyMap(),
    dynamicUnreadCount: Int = 0,
    onToggleSidebar: (() -> Unit)? = null,
    miuixBackdrop: MiuixLayerBackdrop? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    isFeedScrollInProgress: Boolean = false,
    collapseLinkedDock: Boolean = false,
    indicatorPositionProvider: (() -> Float)? = null,
    isPagerScrollInProgressProvider: () -> Boolean = { false },
    uiSkinDecoration: BottomBarUiSkinDecoration? = null
) {
    val foldPosture = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current.posture
    val forceBottomNavigation = foldPosture == com.android.purebilibili.core.util.AppFoldPosture.Book ||
        foldPosture == com.android.purebilibili.core.util.AppFoldPosture.Tabletop
    // Fold posture decides whether navigation stays at the bottom; window size still owns the
    // dock geometry so a large foldable does not shrink to phone-sized icons and indicators.
    val isTablet = com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet
    val effectiveToggleSidebar = onToggleSidebar.takeUnless { forceBottomNavigation }
    var lastHomeClickMs by remember { mutableLongStateOf(0L) }
    val resolvedItemClick: (BottomNavItem) -> Unit = { item ->
        val nowMs = SystemClock.elapsedRealtime()
        when (
            resolveHomeSideBarClickAction(
                item = item,
                nowMs = nowMs,
                lastHomeClickMs = lastHomeClickMs,
            )
        ) {
            HomeSideBarClickAction.HOME_DOUBLE_TAP -> onHomeDoubleTap()
            HomeSideBarClickAction.NAVIGATE -> onItemClick(item)
        }
        if (item == BottomNavItem.HOME) {
            lastHomeClickMs = nowMs
        }
    }
    ProvideBottomBarSkinMotion(uiSkinDecoration) {
        AppBottomNavigationHost(
            androidNativeLiquidGlassEnabled = homeSettings.androidNativeLiquidGlassEnabled,
            materialContent = { policy ->
                MaterialBottomBar(
                currentItem = currentItem,
                nowPlayingContent = nowPlayingContent,
                onItemClick = resolvedItemClick,
                modifier = modifier,
                visibleItems = visibleItems,
                itemLabels = itemLabels,
                onToggleSidebar = effectiveToggleSidebar,
                dynamicUnreadCount = dynamicUnreadCount,
                isFloating = isFloating,
                isTablet = isTablet,
                labelMode = labelMode,
                blurEnabled = hazeState != null,
                hazeState = hazeState,
                miuixBackdrop = miuixBackdrop,
                homeSettings = homeSettings,
                onSearchClick = onSearchClick,
                onSearchKeywordSubmit = onSearchKeywordSubmit,
                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget,
                isFeedScrollInProgress = isFeedScrollInProgress,
                collapseLinkedDock = collapseLinkedDock,
                indicatorPositionProvider = indicatorPositionProvider,
                isPagerScrollInProgressProvider = isPagerScrollInProgressProvider,
                uiSkinDecoration = uiSkinDecoration,
                sharedLiquidGlassEnabled = policy.liquidGlassEnabled,
                )
            },
            platformContent = { policy ->
                MiuixBottomBar(
                currentItem = currentItem,
                nowPlayingContent = nowPlayingContent,
                onItemClick = resolvedItemClick,
                modifier = modifier,
                visibleItems = visibleItems,
                itemLabels = itemLabels,
                onToggleSidebar = effectiveToggleSidebar,
                dynamicUnreadCount = dynamicUnreadCount,
                isFloating = isFloating,
                isTablet = isTablet,
                labelMode = labelMode,
                blurEnabled = hazeState != null,
                hazeState = hazeState,
                miuixBackdrop = miuixBackdrop,
                homeSettings = homeSettings,
                onSearchClick = onSearchClick,
                onSearchKeywordSubmit = onSearchKeywordSubmit,
                searchLaunchKey = searchLaunchKey,
                onSearchLaunchTransitionFinished = onSearchLaunchTransitionFinished,
                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget,
                isFeedScrollInProgress = isFeedScrollInProgress,
                collapseLinkedDock = collapseLinkedDock,
                indicatorPositionProvider = indicatorPositionProvider,
                isPagerScrollInProgressProvider = isPagerScrollInProgressProvider,
                uiSkinDecoration = uiSkinDecoration,
                sharedLiquidGlassEnabled = policy.liquidGlassEnabled,
                )
            },
        )
    }
}

@Composable
private fun MaterialBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    nowPlayingContent: (@Composable (Modifier, Float, Boolean, Float) -> Unit)? = null,
    visibleItems: List<BottomNavItem>,
    itemLabels: Map<String, String>,
    onToggleSidebar: (() -> Unit)?,
    dynamicUnreadCount: Int,
    isFloating: Boolean,
    isTablet: Boolean,
    labelMode: Int,
    blurEnabled: Boolean,
    hazeState: HazeState?,
    miuixBackdrop: MiuixLayerBackdrop?,
    homeSettings: com.android.purebilibili.core.store.HomeSettings,
    onSearchClick: () -> Unit,
    onSearchKeywordSubmit: (String) -> Unit,
    searchLaunchKey: Int = 0,
    onSearchLaunchTransitionFinished: (Int) -> Unit = {},
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    isFeedScrollInProgress: Boolean = false,
    collapseLinkedDock: Boolean = false,
    indicatorPositionProvider: (() -> Float)? = null,
    isPagerScrollInProgressProvider: () -> Boolean = { false },
    uiSkinDecoration: BottomBarUiSkinDecoration? = null,
    sharedLiquidGlassEnabled: Boolean,
) {
    val haptic = rememberHapticFeedback()
    val normalizedLabelMode = normalizeBottomBarLabelMode(labelMode)
    val showIcon = shouldShowBottomBarIcon(normalizedLabelMode)
    val showText = shouldShowBottomBarText(normalizedLabelMode)
    val bottomBarVisibleItems = remember(
        visibleItems,
        homeSettings.isBottomBarSearchEnabled,
        homeSettings.bottomBarSearchLayoutMode
    ) {
        resolveBottomBarVisibleItemsForSearchMode(
            visibleItems = visibleItems,
            bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
            searchLayoutMode = homeSettings.bottomBarSearchLayoutMode
        )
    }
    val glassEnabled = resolveAndroidNativeBottomBarGlassEnabled(
        liquidGlassEnabled = sharedLiquidGlassEnabled,
        blurEnabled = blurEnabled
    )
    val liquidGlassTuning = remember(
        homeSettings.liquidGlassProgress,
        homeSettings.liquidGlassAdvancedSettings,
        homeSettings.liquidGlassReadabilityMode,
    ) {
        resolveLiquidGlassTuning(
            homeSettings.liquidGlassProgress,
            homeSettings.liquidGlassAdvancedSettings,
            homeSettings.liquidGlassReadabilityMode,
        )
    }
    val androidNativeTuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = glassEnabled || blurEnabled,
        darkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.chromeBackground()),
    )
    val blurIntensity = currentUnifiedBlurIntensity()
    val baseSurfaceColor = if (isFloating) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        AppSurfaceTokens.cardContainer()
    }
    val globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
    val containerColor = if (isFloating) {
        resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = baseSurfaceColor,
            tuning = androidNativeTuning,
            glassEnabled = glassEnabled,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity,
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            liquidGlassTuning = liquidGlassTuning,
            globalWallpaperVisible = globalWallpaperVisible
        )
    } else {
        resolveBottomBarSurfaceColor(
            surfaceColor = baseSurfaceColor,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    }
    val dockedItemColors = resolveMaterialDockedBottomBarItemColors(
        themePrimary = MaterialTheme.colorScheme.primary,
        onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant,
        secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    )
    val skinDockedItemColors = resolveBottomBarSkinContentColors(
        selectedColor = uiSkinDecoration?.bottomSelectedTint
            ?.takeUnless { it == Color.Unspecified }
            ?: dockedItemColors.selectedIconColor,
        unselectedColor = uiSkinDecoration?.bottomUnselectedTint
            ?.takeUnless { it == Color.Unspecified }
            ?: dockedItemColors.unselectedIconColor,
        skinTrimTint = uiSkinDecoration?.bottomTrimTint
    )
    val dockedIndicatorColor = resolveDockedBottomBarIndicatorColor(
        defaultColor = dockedItemColors.indicatorColor,
        hasUiSkinDecoration = uiSkinDecoration != null,
    )

    if (
        !homeSettings.isBottomBarSearchEnabled && nowPlayingContent == null &&
        shouldUseOfficialMd3FloatingToolbar(
            isFloating = isFloating,
            liquidGlassEnabled = glassEnabled,
        )
    ) {
        OfficialMd3FloatingBottomBar(
            currentItem = currentItem,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = bottomBarVisibleItems,
            itemLabels = itemLabels,
            onToggleSidebar = onToggleSidebar,
            dynamicUnreadCount = dynamicUnreadCount,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            searchEnabled = shouldReserveBottomBarSearchLayout(
                bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
            ),
            onSearchClick = onSearchClick,
            haptic = haptic,
        )
        return
    }

    if (isFloating) {
        BiliPaiFloatingBottomBar(
            currentItem = currentItem,
            nowPlayingContent = nowPlayingContent,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = bottomBarVisibleItems,
            itemLabels = itemLabels,
            onToggleSidebar = onToggleSidebar,
            dynamicUnreadCount = dynamicUnreadCount,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            labelMode = normalizedLabelMode,
            blurEnabled = blurEnabled,
            miuixBackdrop = miuixBackdrop,
            containerColor = containerColor,
            tuning = androidNativeTuning,
            glassEnabled = glassEnabled,
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            liquidGlassTuning = liquidGlassTuning,
            navigationIconCrossScaleEnabled = homeSettings.navigationIconCrossScaleEnabled,
            haptic = haptic,
            bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
            bottomBarSearchAutoExpandMode = homeSettings.bottomBarSearchAutoExpandMode,
            bottomBarSearchLayoutMode = homeSettings.bottomBarSearchLayoutMode,
            onSearchClick = onSearchClick,
            onSearchKeywordSubmit = onSearchKeywordSubmit,
            searchLaunchKey = searchLaunchKey,
            onSearchLaunchTransitionFinished = onSearchLaunchTransitionFinished,
            isFeedScrollInProgress = isFeedScrollInProgress,
            collapseLinkedDock = collapseLinkedDock,
            indicatorPositionProvider = indicatorPositionProvider,
            isPagerScrollInProgressProvider = isPagerScrollInProgressProvider,
            uiSkinDecoration = uiSkinDecoration
        )
        return
    }

    AppSurface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (blurEnabled && hazeState != null) {
                    Modifier.unifiedBlur(
                        hazeState = hazeState,
                        surfaceType = BlurSurfaceType.BOTTOM_BAR,
                        motionTier = motionTier,
                        isScrolling = false,
                        isTransitionRunning = isTransitionRunning,
                        forceLowBudget = forceLowBlurBudget
                    )
                } else {
                    Modifier
                }
            ),
        tonalElevation = if (blurEnabled) AppSpacingTokens.None else AppSpacingTokens.ExtraSmall - AppSpacingTokens.Micro / 2,
        shadowElevation = AppSpacingTokens.None,
        color = containerColor
    ) {
        DockedBottomBarSkinContainer(
            decoration = uiSkinDecoration
        ) {
            AppNavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = AppSpacingTokens.None,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                bottomBarVisibleItems.forEach { item ->
                    val itemLabel = resolveBottomNavItemLabel(item, itemLabels)
                    val itemContentDescription = resolveBottomNavItemContentDescription(item)
                    val skinIconPath = uiSkinDecoration?.iconPathFor(item, selected = currentItem == item)
                    AppNavigationBarItem(
                        selected = currentItem == item,
                        onClick = {
                            performMaterialBottomBarTap(
                                haptic = haptic,
                                onClick = { onItemClick(item) }
                            )
                        },
                        icon = {
                            if (showIcon) {
                                BottomBarReminderBadgeAnchor(
                                    item = item,
                                    unreadCount = dynamicUnreadCount
                                ) {
                                    if (skinIconPath != null) {
                                        BottomBarSkinIcon(
                                            iconPath = skinIconPath,
                                            contentDescription = itemContentDescription,
                                            selected = currentItem == item,
                                        )
                                    } else {
                                        MaterialBottomBarAnimatedIcon(
                                            item = item,
                                            selected = currentItem == item,
                                            contentDescription = itemContentDescription
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.size(AppSpacingTokens.None))
                            }
                        },
                        label = if (showText) {
                            {
                                AppText(
                                    text = itemLabel,
                                    modifier = Modifier.bottomBarSkinLabelScrim(
                                        color = skinDockedItemColors.labelScrimColor,
                                        alpha = skinDockedItemColors.labelScrimAlpha
                                    )
                                )
                            }
                        } else {
                            null
                        },
                        alwaysShowLabel = showText,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = skinDockedItemColors.selectedColor,
                            selectedTextColor = skinDockedItemColors.selectedColor,
                            indicatorColor = dockedIndicatorColor,
                            unselectedIconColor = skinDockedItemColors.unselectedColor,
                            unselectedTextColor = skinDockedItemColors.unselectedColor
                        )
                    )
                }

                if (isTablet && onToggleSidebar != null) {
                    val sidebarLabel = stringResource(R.string.sidebar_toggle)
                    AppNavigationBarItem(
                        selected = false,
                        onClick = {
                            performMaterialBottomBarTap(
                                haptic = haptic,
                                onClick = onToggleSidebar
                            )
                        },
                        icon = {
                            if (showIcon) {
                                AppIcon(
                                    imageVector = Icons.AutoMirrored.Outlined.MenuOpen,
                                    contentDescription = sidebarLabel
                                )
                            } else {
                                Spacer(modifier = Modifier.size(AppSpacingTokens.None))
                            }
                        },
                        label = if (showText) {
                            {
                                AppText(
                                    text = sidebarLabel,
                                    modifier = Modifier.bottomBarSkinLabelScrim(
                                        color = skinDockedItemColors.labelScrimColor,
                                        alpha = skinDockedItemColors.labelScrimAlpha
                                    )
                                )
                            }
                        } else {
                            null
                        },
                        alwaysShowLabel = showText,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = dockedItemColors.selectedIconColor,
                            selectedTextColor = dockedItemColors.selectedTextColor,
                            indicatorColor = dockedItemColors.indicatorColor,
                            unselectedIconColor = dockedItemColors.unselectedIconColor,
                            unselectedTextColor = dockedItemColors.unselectedTextColor
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OfficialMd3FloatingBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: List<BottomNavItem>,
    itemLabels: Map<String, String>,
    onToggleSidebar: (() -> Unit)?,
    dynamicUnreadCount: Int,
    isTablet: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    searchEnabled: Boolean,
    onSearchClick: () -> Unit,
    haptic: (HapticType) -> Unit,
) {
    val toolbarContent: @Composable RowScope.() -> Unit = {
        visibleItems.forEach { item ->
            val selected = currentItem == item
            val label = resolveBottomNavItemLabel(item, itemLabels)
            val onClick = {
                performMaterialBottomBarTap(
                    haptic = haptic,
                    onClick = { onItemClick(item) },
                )
            }
            val icon: @Composable () -> Unit = {
                BottomBarReminderBadgeAnchor(
                    item = item,
                    unreadCount = dynamicUnreadCount,
                    floatingCompact = true,
                ) {
                    AppIcon(
                        imageVector = resolveMaterialBottomBarIcon(item, selected),
                        contentDescription = if (showText) null else label,
                    )
                }
            }

            when {
                showIcon && showText && selected -> FilledTonalButton(
                    onClick = onClick,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    icon()
                    Spacer(Modifier.width(8.dp))
                    AppText(text = label, maxLines = 1)
                }
                showIcon && selected -> FilledTonalIconButton(onClick = onClick) { icon() }
                showIcon -> IconButton(onClick = onClick) { icon() }
                selected -> FilledTonalButton(onClick = onClick) {
                    AppText(text = label, maxLines = 1)
                }
                else -> TextButton(onClick = onClick) {
                    AppText(text = label, maxLines = 1)
                }
            }
        }

        if (isTablet && onToggleSidebar != null) {
            IconButton(
                onClick = {
                    performMaterialBottomBarTap(haptic = haptic, onClick = onToggleSidebar)
                },
            ) {
                AppIcon(
                    imageVector = Icons.AutoMirrored.Outlined.MenuOpen,
                    contentDescription = stringResource(R.string.sidebar_toggle),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = FloatingToolbarDefaults.ScreenOffset,
                end = FloatingToolbarDefaults.ScreenOffset,
                bottom = FloatingToolbarDefaults.ScreenOffset +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (searchEnabled) {
            HorizontalFloatingToolbar(
                expanded = true,
                floatingActionButton = {
                    FloatingToolbarDefaults.StandardFloatingActionButton(
                        onClick = {
                            performMaterialBottomBarTap(haptic = haptic, onClick = onSearchClick)
                        },
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.common_search),
                        )
                    }
                },
                content = toolbarContent,
            )
        } else {
            HorizontalFloatingToolbar(
                expanded = true,
                content = toolbarContent,
            )
        }
    }
}

@Composable
private fun MaterialBottomBarAnimatedIcon(
    item: BottomNavItem,
    selected: Boolean,
    contentDescription: String?,
) {
    val transform = rememberNavigationSelectionTransform(
        selected = selected,
        label = "${item.name}_md3_bottom_bar",
    )

    AppIcon(
        imageVector = resolveMaterialBottomBarIcon(item = item, selected = selected),
        contentDescription = contentDescription,
        modifier = Modifier.graphicsLayer {
            scaleX = transform.scale()
            scaleY = transform.scale()
            rotationZ = transform.rotationDegrees()
        },
    )
}

@Composable
private fun MiuixBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    nowPlayingContent: (@Composable (Modifier, Float, Boolean, Float) -> Unit)? = null,
    visibleItems: List<BottomNavItem>,
    itemLabels: Map<String, String>,
    onToggleSidebar: (() -> Unit)?,
    dynamicUnreadCount: Int,
    isFloating: Boolean,
    isTablet: Boolean,
    labelMode: Int,
    blurEnabled: Boolean,
    hazeState: HazeState?,
    miuixBackdrop: MiuixLayerBackdrop?,
    homeSettings: com.android.purebilibili.core.store.HomeSettings,
    onSearchClick: () -> Unit,
    onSearchKeywordSubmit: (String) -> Unit,
    searchLaunchKey: Int = 0,
    onSearchLaunchTransitionFinished: (Int) -> Unit = {},
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    isFeedScrollInProgress: Boolean = false,
    collapseLinkedDock: Boolean = false,
    indicatorPositionProvider: (() -> Float)? = null,
    isPagerScrollInProgressProvider: () -> Boolean = { false },
    uiSkinDecoration: BottomBarUiSkinDecoration? = null,
    sharedLiquidGlassEnabled: Boolean,
) {
    val haptic = rememberHapticFeedback()
    val normalizedLabelMode = normalizeBottomBarLabelMode(labelMode)
    val showIcon = shouldShowBottomBarIcon(normalizedLabelMode)
    val showText = shouldShowBottomBarText(normalizedLabelMode)
    val resolvedIconStyle = rememberResolvedAppIconStyle()
    val sharedBarIconStyle = if (resolvedIconStyle == AppIconStyle.MD3_STANDARD) {
        SharedFloatingBottomBarIconStyle.MATERIAL
    } else {
        SharedFloatingBottomBarIconStyle.MIUIX
    }
    val bottomBarVisibleItems = remember(
        visibleItems,
        homeSettings.isBottomBarSearchEnabled,
        homeSettings.bottomBarSearchLayoutMode
    ) {
        resolveBottomBarVisibleItemsForSearchMode(
            visibleItems = visibleItems,
            bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
            searchLayoutMode = homeSettings.bottomBarSearchLayoutMode
        )
    }
    val displayMode = resolveMd3BottomBarDisplayMode(labelMode).toAppPlatformNavigationDisplayMode()
    val glassEnabled = resolveAndroidNativeBottomBarGlassEnabled(
        liquidGlassEnabled = sharedLiquidGlassEnabled,
        blurEnabled = blurEnabled
    )
    val liquidGlassTuning = remember(
        homeSettings.liquidGlassProgress,
        homeSettings.liquidGlassAdvancedSettings,
        homeSettings.liquidGlassReadabilityMode,
    ) {
        resolveLiquidGlassTuning(
            homeSettings.liquidGlassProgress,
            homeSettings.liquidGlassAdvancedSettings,
            homeSettings.liquidGlassReadabilityMode,
        )
    }
    val tuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = glassEnabled || blurEnabled,
        darkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.background()),
    )
    val blurIntensity = currentUnifiedBlurIntensity()
    val baseSurfaceColor = if (isFloating) {
        AppSurfaceTokens.surfaceContainer()
    } else {
        AppSurfaceTokens.surface()
    }
    val globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
    val containerColor = if (isFloating) {
        resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = baseSurfaceColor,
            tuning = tuning,
            glassEnabled = glassEnabled,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity,
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            liquidGlassTuning = liquidGlassTuning,
            globalWallpaperVisible = globalWallpaperVisible
        )
    } else {
        resolveBottomBarSurfaceColor(
            surfaceColor = baseSurfaceColor,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    }
    if (isFloating) {
        BiliPaiFloatingBottomBar(
            currentItem = currentItem,
            nowPlayingContent = nowPlayingContent,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = bottomBarVisibleItems,
            itemLabels = itemLabels,
            onToggleSidebar = onToggleSidebar,
            dynamicUnreadCount = dynamicUnreadCount,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            labelMode = normalizedLabelMode,
            blurEnabled = blurEnabled,
            miuixBackdrop = miuixBackdrop,
            containerColor = containerColor,
            tuning = tuning,
            glassEnabled = glassEnabled,
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            liquidGlassTuning = liquidGlassTuning,
            iconStyle = sharedBarIconStyle,
            navigationIconCrossScaleEnabled = homeSettings.navigationIconCrossScaleEnabled,
            haptic = haptic,
            hazeState = hazeState,
            motionTier = motionTier,
            isTransitionRunning = isTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget,
            bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
            bottomBarSearchAutoExpandMode = homeSettings.bottomBarSearchAutoExpandMode,
            bottomBarSearchLayoutMode = homeSettings.bottomBarSearchLayoutMode,
            onSearchClick = onSearchClick,
            onSearchKeywordSubmit = onSearchKeywordSubmit,
            searchLaunchKey = searchLaunchKey,
            onSearchLaunchTransitionFinished = onSearchLaunchTransitionFinished,
            isFeedScrollInProgress = isFeedScrollInProgress,
            collapseLinkedDock = collapseLinkedDock,
            indicatorPositionProvider = indicatorPositionProvider,
            isPagerScrollInProgressProvider = isPagerScrollInProgressProvider,
            uiSkinDecoration = uiSkinDecoration
        )
        return
    }

    val barModifier = modifier
        .fillMaxWidth()
        .then(
            if (blurEnabled && hazeState != null) {
                Modifier.unifiedBlur(
                    hazeState = hazeState,
                    surfaceType = BlurSurfaceType.BOTTOM_BAR,
                    motionTier = motionTier,
                    isScrolling = false,
                    isTransitionRunning = isTransitionRunning,
                    forceLowBudget = forceLowBlurBudget
                )
            } else {
                Modifier
            }
        )

    DockedBottomBarSkinContainer(
        decoration = uiSkinDecoration,
        modifier = barModifier.background(containerColor)
    ) {
        AppPlatformNavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (uiSkinDecoration != null) {
                        Modifier.height(resolveBottomBarSkinDockHeight())
                    } else {
                        Modifier
                    }
                ),
            color = Color.Transparent,
            showDivider = false,
            defaultWindowInsetsPadding = true,
            mode = displayMode
        ) {
            val selectedItemColor = MaterialTheme.colorScheme.primary
            val unselectedItemColor = MaterialTheme.colorScheme.onSurfaceVariant
            val dockedIndicatorColor = resolveDockedBottomBarIndicatorColor(
                defaultColor = MaterialTheme.colorScheme.secondaryContainer,
                hasUiSkinDecoration = uiSkinDecoration != null,
            )
        val skinItemColors = resolveBottomBarSkinContentColors(
                selectedColor = uiSkinDecoration?.bottomSelectedTint
                    ?.takeUnless { it == Color.Unspecified }
                    ?: selectedItemColor,
            unselectedColor = uiSkinDecoration?.bottomUnselectedTint
                ?.takeUnless { it == Color.Unspecified }
                ?: unselectedItemColor,
                skinTrimTint = uiSkinDecoration?.bottomTrimTint
            )

            bottomBarVisibleItems.forEach { item ->
                val itemLabel = resolveBottomNavItemLabel(item, itemLabels)
                val skinIconPath = uiSkinDecoration?.iconPathFor(item, selected = currentItem == item)
                val reminderBadgeText = formatBottomBarDynamicReminderBadge(
                    if (shouldShowBottomBarDynamicReminderBadge(item, dynamicUnreadCount)) {
                        dynamicUnreadCount
                    } else {
                        0
                    }
                )
                val onItemTap = {
                    performMaterialBottomBarTap(
                        haptic = haptic,
                        onClick = { onItemClick(item) }
                    )
                }
                if (
                    shouldUseMiuixOfficialNavigationBarItem(
                        skinIconPath = skinIconPath,
                        labelScrimAlpha = skinItemColors.labelScrimAlpha
                    )
                ) {
                    AppPlatformNavigationBarItem(
                        selected = currentItem == item,
                        onClick = onItemTap,
                        icon = resolveSharedBottomBarIcon(
                            item = item,
                            selected = currentItem == item,
                            iconStyle = sharedBarIconStyle
                        ),
                        label = itemLabel,
                        colors = MiuixNavigationBarDefaults.navigationBarItemColors(
                            unselectedContentColor = skinItemColors.unselectedColor,
                            selectedContentColor = skinItemColors.selectedColor,
                        ),
                        badge = reminderBadgeText?.let { badgeText ->
                            {
                                AppPlatformNavigationBadge {
                                    AppText(text = badgeText)
                                }
                            }
                        }
                    )
                } else {
                    MiuixDockedBottomBarItem(
                        selected = currentItem == item,
                        onClick = onItemTap,
                        icon = resolveSharedBottomBarIcon(
                            item = item,
                            selected = currentItem == item,
                            iconStyle = sharedBarIconStyle
                        ),
                        label = itemLabel,
                        showIcon = showIcon,
                        showText = showText,
                        selectedColor = skinItemColors.selectedColor,
                        unselectedColor = skinItemColors.unselectedColor,
                        labelScrimColor = skinItemColors.labelScrimColor,
                        labelScrimAlpha = skinItemColors.labelScrimAlpha,
                        indicatorColor = dockedIndicatorColor,
                        skinIconPath = skinIconPath,
                        reminderBadgeText = reminderBadgeText
                    )
                }
            }

            if (isTablet && onToggleSidebar != null) {
                val sidebarLabel = stringResource(R.string.sidebar_toggle)
                MiuixDockedBottomBarItem(
                    selected = false,
                    onClick = {
                        performMaterialBottomBarTap(
                            haptic = haptic,
                            onClick = onToggleSidebar
                        )
                    },
                    icon = resolveSharedBottomBarSidebarIcon(sharedBarIconStyle),
                    label = sidebarLabel,
                    showIcon = showIcon,
                    showText = showText,
                    selectedColor = skinItemColors.selectedColor,
                    unselectedColor = skinItemColors.unselectedColor,
                    labelScrimColor = skinItemColors.labelScrimColor,
                    labelScrimAlpha = skinItemColors.labelScrimAlpha
                )
            }
        }
    }
}

@Composable
private fun DockedBottomBarSkinContainer(
    decoration: BottomBarUiSkinDecoration?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        BottomBarSkinDecorativeTrim(
            decoration = decoration,
            modifier = Modifier.matchParentSize()
        )
        content()
    }
}

@Composable
private fun RowScope.MiuixDockedBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    showIcon: Boolean,
    showText: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    labelScrimColor: Color = Color.Transparent,
    labelScrimAlpha: Float = 0f,
    indicatorColor: Color = Color.Transparent,
    skinIconPath: String? = null,
    reminderBadgeText: String? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val baseContentColor = resolveMiuixDockedBottomBarItemColor(
        selected = selected,
        selectedColor = selectedColor,
        unselectedColor = unselectedColor
    )
    // MD3 官方选中态:secondaryContainer 指示器 + onSecondaryContainer 图标
    val showIndicator = selected && indicatorColor != Color.Transparent && skinIconPath == null
    val iconTint = if (showIndicator) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        baseContentColor
    }
    val contentColor by animateColorAsState(
        targetValue = if (isPressed) {
            baseContentColor.copy(alpha = if (selected) 0.62f else 0.54f)
        } else {
            baseContentColor
        },
        label = "${label}_miuix_docked_bottom_bar_color"
    )
    val iconAndText = showIcon && showText
    val textOnly = !showIcon && showText

    Column(
        modifier = Modifier
            .height(resolveMiuixDockedBottomBarItemHeight(skinIconPath != null))
            .weight(1f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { currentOnClick() }
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (iconAndText) Arrangement.Top else Arrangement.Center
    ) {
        if (showIcon) {
            BottomBarReminderBadgeAnchor(
                badgeText = reminderBadgeText,
                modifier = Modifier.then(if (iconAndText) Modifier.padding(top = AppSpacingTokens.Small) else Modifier)
            ) {
                if (skinIconPath != null) {
                    BottomBarSkinIcon(
                        iconPath = skinIconPath,
                        contentDescription = label,
                        selected = selected,
                        size = resolveBottomBarMiuixSkinDockIconSize()
                    )
                } else {
                    val iconGlyph: @Composable () -> Unit = {
                        AppIcon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = iconTint,
                            modifier = Modifier.size(AppSpacingTokens.ExtraLarge + AppSpacingTokens.Micro)
                        )
                    }
                    if (showIndicator) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(indicatorColor)
                                .padding(
                                    horizontal = AppSpacingTokens.Large,
                                    vertical = AppSpacingTokens.ExtraSmall
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            iconGlyph()
                        }
                    } else {
                        iconGlyph()
                    }
                }
            }
        }
        if (showText) {
            AppText(
                text = label,
                color = contentColor,
                textAlign = TextAlign.Center,
                fontSize = if (textOnly) {
                    MaterialTheme.typography.labelMedium.fontSize
                } else {
                    MaterialTheme.typography.labelSmall.fontSize
                },
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier.then(
                    if (iconAndText) {
                        Modifier.padding(bottom = AppSpacingTokens.Small)
                    } else {
                        Modifier.padding(vertical = AppSpacingTokens.Small)
                    }
                ).bottomBarSkinLabelScrim(
                    color = labelScrimColor,
                    alpha = labelScrimAlpha
                )
            )
        }
    }
}

@Composable
private fun BiliPaiFloatingBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    nowPlayingContent: (@Composable (Modifier, Float, Boolean, Float) -> Unit)? = null,
    visibleItems: List<BottomNavItem>,
    itemLabels: Map<String, String> = emptyMap(),
    itemColorIndices: Map<String, Int> = emptyMap(),
    dynamicUnreadCount: Int = 0,
    onToggleSidebar: (() -> Unit)?,
    isTablet: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    labelMode: Int,
    blurEnabled: Boolean,
    miuixBackdrop: MiuixBackdrop?,
    containerColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    liquidGlassTuning: LiquidGlassTuning,
    iconStyle: SharedFloatingBottomBarIconStyle = SharedFloatingBottomBarIconStyle.MATERIAL,
    navigationIconCrossScaleEnabled: Boolean = false,
    haptic: (HapticType) -> Unit,
    hazeState: HazeState? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    bottomBarSearchEnabled: Boolean = false,
    bottomBarSearchAutoExpandMode: BottomBarSearchAutoExpandMode =
        BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
    bottomBarSearchLayoutMode: BottomBarSearchLayoutMode =
        BottomBarSearchLayoutMode.FULL_DOCK,
    onSearchClick: () -> Unit = {},
    onSearchKeywordSubmit: (String) -> Unit = {},
    searchLaunchKey: Int = 0,
    onSearchLaunchTransitionFinished: (Int) -> Unit = {},
    isFeedScrollInProgress: Boolean = false,
    collapseLinkedDock: Boolean = false,
    indicatorPositionProvider: (() -> Float)? = null,
    isPagerScrollInProgressProvider: () -> Boolean = { false },
    uiSkinDecoration: BottomBarUiSkinDecoration? = null
) {
    if (bottomBarSearchEnabled || nowPlayingContent != null) {
        LinkedBottomDock(
            currentItem = currentItem,
            firstItem = visibleItems.firstOrNull() ?: BottomNavItem.HOME,
            firstLabel = resolveBottomNavItemLabel(visibleItems.firstOrNull() ?: BottomNavItem.HOME, itemLabels),
            searchEnabled = bottomBarSearchEnabled,
            collapseRequested = collapseLinkedDock,
            onSearchClick = onSearchClick,
            onSearchKeywordSubmit = onSearchKeywordSubmit,
            containerColor = containerColor,
            backdrop = miuixBackdrop,
            glassEnabled = glassEnabled && !forceLowBlurBudget,
            liquidGlassTuning = liquidGlassTuning,
            iconStyle = iconStyle,
            nowPlayingContent = nowPlayingContent,
            modifier = modifier,
            navigationContent = {
                BiliPaiFloatingBottomBarChrome(
                    currentItem = currentItem,
                    onItemClick = onItemClick,
                    modifier = Modifier,
                    visibleItems = visibleItems,
                    itemLabels = itemLabels,
                    itemColorIndices = itemColorIndices,
                    dynamicUnreadCount = dynamicUnreadCount,
                    onToggleSidebar = onToggleSidebar,
                    isTablet = isTablet,
                    showIcon = showIcon,
                    showText = showText,
                    labelMode = labelMode,
                    blurEnabled = blurEnabled,
                    miuixBackdrop = miuixBackdrop,
                    containerColor = containerColor,
                    tuning = tuning,
                    glassEnabled = glassEnabled,
                    liquidGlassPreset = liquidGlassPreset,
                    liquidGlassTuning = liquidGlassTuning,
                    iconStyle = iconStyle,
                    navigationIconCrossScaleEnabled = navigationIconCrossScaleEnabled,
                    haptic = haptic,
                    hazeState = hazeState,
                    motionTier = motionTier,
                    isTransitionRunning = isTransitionRunning,
                    forceLowBlurBudget = forceLowBlurBudget,
                    bottomBarSearchEnabled = false,
                    bottomBarSearchAutoExpandMode = bottomBarSearchAutoExpandMode,
                    bottomBarSearchLayoutMode = bottomBarSearchLayoutMode,
                    onSearchClick = onSearchClick,
                    onSearchKeywordSubmit = onSearchKeywordSubmit,
                    searchLaunchKey = searchLaunchKey,
                    onSearchLaunchTransitionFinished = onSearchLaunchTransitionFinished,
                    isFeedScrollInProgress = isFeedScrollInProgress,
                    indicatorPositionProvider = indicatorPositionProvider,
                    isPagerScrollInProgressProvider = isPagerScrollInProgressProvider,
                    uiSkinDecoration = uiSkinDecoration,
                    embeddedDock = true,
                )
            },
        )
    } else {
        BiliPaiFloatingBottomBarChrome(
            currentItem = currentItem,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = visibleItems,
            itemLabels = itemLabels,
            itemColorIndices = itemColorIndices,
            dynamicUnreadCount = dynamicUnreadCount,
            onToggleSidebar = onToggleSidebar,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            labelMode = labelMode,
            blurEnabled = blurEnabled,
            miuixBackdrop = miuixBackdrop,
            containerColor = containerColor,
            tuning = tuning,
            glassEnabled = glassEnabled,
            liquidGlassPreset = liquidGlassPreset,
            liquidGlassTuning = liquidGlassTuning,
            iconStyle = iconStyle,
            navigationIconCrossScaleEnabled = navigationIconCrossScaleEnabled,
            haptic = haptic,
            hazeState = hazeState,
            motionTier = motionTier,
            isTransitionRunning = isTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget,
            bottomBarSearchEnabled = bottomBarSearchEnabled,
            bottomBarSearchAutoExpandMode = bottomBarSearchAutoExpandMode,
            bottomBarSearchLayoutMode = bottomBarSearchLayoutMode,
            onSearchClick = onSearchClick,
            onSearchKeywordSubmit = onSearchKeywordSubmit,
            searchLaunchKey = searchLaunchKey,
            onSearchLaunchTransitionFinished = onSearchLaunchTransitionFinished,
            isFeedScrollInProgress = isFeedScrollInProgress,
            indicatorPositionProvider = indicatorPositionProvider,
            isPagerScrollInProgressProvider = isPagerScrollInProgressProvider,
            uiSkinDecoration = uiSkinDecoration,
        )
    }
}

@Composable
private fun BiliPaiFloatingBottomBarChrome(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    embeddedDock: Boolean = false,
    visibleItems: List<BottomNavItem>,
    itemLabels: Map<String, String> = emptyMap(),
    itemColorIndices: Map<String, Int> = emptyMap(),
    dynamicUnreadCount: Int = 0,
    onToggleSidebar: (() -> Unit)?,
    isTablet: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    labelMode: Int,
    blurEnabled: Boolean,
    miuixBackdrop: MiuixBackdrop?,
    containerColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    liquidGlassTuning: LiquidGlassTuning,
    iconStyle: SharedFloatingBottomBarIconStyle = SharedFloatingBottomBarIconStyle.MATERIAL,
    navigationIconCrossScaleEnabled: Boolean = false,
    haptic: (HapticType) -> Unit,
    hazeState: HazeState? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    bottomBarSearchEnabled: Boolean = false,
    bottomBarSearchAutoExpandMode: BottomBarSearchAutoExpandMode =
        BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
    bottomBarSearchLayoutMode: BottomBarSearchLayoutMode =
        BottomBarSearchLayoutMode.FULL_DOCK,
    onSearchClick: () -> Unit = {},
    onSearchKeywordSubmit: (String) -> Unit = {},
    searchLaunchKey: Int = 0,
    onSearchLaunchTransitionFinished: (Int) -> Unit = {},
    isFeedScrollInProgress: Boolean = false,
    indicatorPositionProvider: (() -> Float)? = null,
    isPagerScrollInProgressProvider: () -> Boolean = { false },
    uiSkinDecoration: BottomBarUiSkinDecoration? = null
) {
    // BiliPai 对齐：材质/动效由 FloatingBottomBar 三层结构承担；
    // 本函数仅编排 BiliPai 特性（search / skin / badge / tablet sidebar）。
    val isDarkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.background())
    val effectiveGlassEnabled = shouldRenderBottomBarLiquidGlassEffects(
        glassEnabled = glassEnabled,
        forceLowBlurBudget = forceLowBlurBudget,
    )
    val biliPaiContainerColor = resolveBiliPaiBottomBarShellColor(
        containerColor = containerColor,
        liquidGlassEnabled = effectiveGlassEnabled,
        darkTheme = isDarkTheme,
        liquidGlassTuning = liquidGlassTuning
    )
    val allItems = remember(visibleItems, isTablet, onToggleSidebar) {
        buildList {
            addAll(visibleItems)
            if (isTablet && onToggleSidebar != null) add(null)
        }
    }
    val selectedIndex = visibleItems.indexOf(currentItem).coerceAtLeast(0)
    val isValidSelection = currentItem in visibleItems
    val baseSelectedColor = MaterialTheme.colorScheme.primary
    val baseUnselectedColor = if (iconStyle == SharedFloatingBottomBarIconStyle.MATERIAL) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val skinContentColors = resolveBottomBarSkinContentColors(
        selectedColor = uiSkinDecoration?.bottomSelectedTint
            ?.takeUnless { it == Color.Unspecified }
            ?: baseSelectedColor,
        unselectedColor = uiSkinDecoration?.bottomUnselectedTint
            ?.takeUnless { it == Color.Unspecified }
            ?: baseUnselectedColor,
        skinTrimTint = uiSkinDecoration?.bottomTrimTint
    )
    val readableContentColor = MaterialTheme.colorScheme.onSurface
    val selectedColor = lerpColor(
        skinContentColors.selectedColor,
        readableContentColor,
        liquidGlassTuning.contentReadabilityBoost * 0.65f,
    )
    val unselectedColor = lerpColor(
        skinContentColors.unselectedColor,
        readableContentColor,
        liquidGlassTuning.contentReadabilityBoost,
    ).copy(alpha = 1f)
    val totalItems = allItems.size.coerceAtLeast(1)

    var searchExpansionOverride by remember {
        mutableStateOf(BottomBarSearchExpansionOverride.FOLLOW_AUTO)
    }
    var searchQuery by remember { mutableStateOf("") }
    val searchLaunchMorphSpec = remember { resolveBottomBarSearchLaunchMorphSpec() }
    val searchEnabled = resolveBottomBarSearchEnabledForItem(
        currentItem = currentItem,
        bottomBarSearchEnabled = bottomBarSearchEnabled
    )
    val searchLayoutReserved = shouldReserveBottomBarSearchLayout(
        bottomBarSearchEnabled = bottomBarSearchEnabled
    )
    val homeScrollOffset = LocalHomeScrollOffset.current
    val isPastSearchAutoExpandTopThreshold by remember(homeScrollOffset) {
        derivedStateOf {
            homeScrollOffset.floatValue > BottomBarSearchTopThresholdPx
        }
    }
    val shouldAutoExpandSearch by remember(
        searchEnabled,
        currentItem,
        bottomBarSearchAutoExpandMode,
        isPastSearchAutoExpandTopThreshold
    ) {
        derivedStateOf {
            shouldAutoExpandBottomBarSearchAtThreshold(
                currentItem = currentItem,
                bottomBarSearchEnabled = searchEnabled,
                autoExpandMode = bottomBarSearchAutoExpandMode,
                isPastTopThreshold = isPastSearchAutoExpandTopThreshold
            )
        }
    }
    val effectiveSearchExpanded = resolveEffectiveBottomBarSearchExpanded(
        currentItem = currentItem,
        bottomBarSearchEnabled = searchEnabled,
        shouldAutoExpand = shouldAutoExpandSearch,
        expansionOverride = searchExpansionOverride
    )
    LaunchedEffect(
        currentItem,
        searchEnabled,
        shouldAutoExpandSearch,
        isPastSearchAutoExpandTopThreshold
    ) {
        val shouldResetSearchOverride = shouldResetBottomBarSearchExpansionOverride(
            currentItem = currentItem,
            bottomBarSearchEnabled = searchEnabled,
            shouldAutoExpand = shouldAutoExpandSearch,
            isPastTopThreshold = isPastSearchAutoExpandTopThreshold
        )
        if (shouldResetSearchOverride) {
            searchExpansionOverride = BottomBarSearchExpansionOverride.FOLLOW_AUTO
        }
    }
    LaunchedEffect(searchLaunchKey) {
        if (searchLaunchKey <= 0 || !searchEnabled) return@LaunchedEffect
        delay(searchLaunchMorphSpec.expandDurationMillis.toLong())
        onSearchLaunchTransitionFinished(searchLaunchKey)
        delay(searchLaunchMorphSpec.postHandoffResetDelayMillis)
    }

    val floatingMode = when {
        effectiveGlassEnabled && miuixBackdrop != null -> FloatingBottomBarMode.LiquidGlass
        blurEnabled && miuixBackdrop != null -> FloatingBottomBarMode.Blur
        else -> FloatingBottomBarMode.None
    }
    val usePlainMiuixFloatingBar = shouldUsePlainMiuixFloatingBar(
        glassEnabled = effectiveGlassEnabled,
        blurEnabled = blurEnabled,
    )
    val floatingContainerColor = if (usePlainMiuixFloatingBar) {
        biliPaiContainerColor.copy(alpha = 1f)
    } else {
        resolveFloatingBottomBarContainerColor(
            defaultColor = biliPaiContainerColor,
            mode = floatingMode,
            hasUiSkinDecoration = uiSkinDecoration != null,
        )
    }
    val floatingColors = FloatingBottomBarColors(
        containerColor = floatingContainerColor,
        indicatorColor = selectedColor,
        contentColor = unselectedColor,
        activeContentColor = selectedColor
    )

    fun handleBottomBarItemClick(index: Int, item: BottomNavItem) {
        val searchOverride = resolveBottomBarSearchExpansionOverrideOnNavItemClick(
            currentItem = currentItem,
            clickedItem = item,
            bottomBarSearchEnabled = searchEnabled,
            effectiveSearchExpanded = effectiveSearchExpanded
        )
        if (searchOverride != null) {
            haptic(HapticType.LIGHT)
            searchExpansionOverride = searchOverride
        } else {
            performMaterialBottomBarTap(
                haptic = haptic,
                onClick = { onItemClick(item) }
            )
        }
    }

    fun handleBottomBarSidebarClick() {
        if (onToggleSidebar != null) {
            performMaterialBottomBarTap(
                haptic = haptic,
                onClick = onToggleSidebar
            )
        }
    }

    fun handleSelected(index: Int) {
        when {
            index in visibleItems.indices -> {
                val item = visibleItems[index]
                val searchOverride = resolveBottomBarSearchExpansionOverrideOnNavItemClick(
                    currentItem = currentItem,
                    clickedItem = item,
                    bottomBarSearchEnabled = searchEnabled,
                    effectiveSearchExpanded = effectiveSearchExpanded
                )
                if (searchOverride != null) {
                    searchExpansionOverride = searchOverride
                } else {
                    onItemClick(item)
                }
            }
            isTablet && onToggleSidebar != null && index == visibleItems.size -> onToggleSidebar()
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    bottom = if (embeddedDock) 0.dp else AppSpacingTokens.Medium +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
        ) {
            val searchLayoutState = rememberBiliPaiBottomBarSearchLayoutState(
                containerWidth = maxWidth,
                itemCount = totalItems,
                minEdgePadding = tuning.outerHorizontalPaddingDp.dp,
                searchEnabled = searchLayoutReserved,
                searchExpanded = effectiveSearchExpanded,
                labelMode = labelMode,
                searchLayoutMode = bottomBarSearchLayoutMode,
                hasUiSkinDecoration = uiSkinDecoration != null
            )
            val dockWidth = searchLayoutState.dockWidth
            val searchWidth = searchLayoutState.searchWidth
            val searchHeight = searchLayoutState.searchHeight
            val launchAdjustedSearchGap = searchLayoutState.launchAdjustedSearchGap
            val dockHeight = searchLayoutState.dockHeight
            val shellHeight = searchLayoutState.shellHeight
            val compactSearchLayout =
                bottomBarSearchLayoutMode == BottomBarSearchLayoutMode.HOME_AND_SEARCH
            val visualSearchExpanded = resolveBiliPaiBottomBarSearchFieldExpanded(
                searchExpanded = effectiveSearchExpanded,
                searchLayoutMode = bottomBarSearchLayoutMode
            )
            val animatedDockContentAlpha by animateFloatAsState(
                targetValue = if (compactSearchLayout && effectiveSearchExpanded) 0f else 1f,
                animationSpec = bottomBarContentVisibilityMotionSpec(),
                label = "bottomBarDockContentAlpha"
            )
            val animatedCompactHomeAlpha by animateFloatAsState(
                targetValue = if (compactSearchLayout && effectiveSearchExpanded) 1f else 0f,
                animationSpec = bottomBarContentVisibilityMotionSpec(),
                label = "bottomBarCompactHomeAlpha"
            )
            // 非玻璃路径没有独立的采样/导出层遮蔽交叉淡化；两套推荐内容同时存在时，
            // 大号首页图标会直接压到原 Dock 的“推荐”文字上。关闭玻璃时改为原子切换。
            val useImmediatePlainHomeSwap = !effectiveGlassEnabled &&
                compactSearchLayout && effectiveSearchExpanded
            val dockContentAlpha = if (useImmediatePlainHomeSwap) 0f else animatedDockContentAlpha
            val compactHomeAlpha = if (useImmediatePlainHomeSwap) 1f else animatedCompactHomeAlpha
            val shouldComposeDockContent = shouldComposeBottomBarDockContent(
                dockContentAlpha = dockContentAlpha,
                effectiveSearchExpanded = effectiveSearchExpanded
            )
            val compactHomeIconSize = resolveBiliPaiExpandedHomeIconSize()
            val compactHomeIconScale = resolveBiliPaiExpandedHomeIconScale()
            val selectedIndexForBar = if (isValidSelection) selectedIndex else selectedIndex
            // Keep the selection provider stable. BiliPai derives a plain Int from currentItem,
            // so passing `{ selectedIndexForBar }` would replace the
            // lambda/state identity on every tab change while FloatingBottomBar's long-lived
            // snapshotFlow still observes the previous state. Keep stable providers at this
            // integration boundary and let their State values follow recomposition instead.
            val selectedIndexForBarState = rememberUpdatedState(selectedIndexForBar)
            val handleSelectedState = rememberUpdatedState<(Int) -> Unit>(
                newValue = { index -> handleSelected(index) }
            )
            val floatingSelectedIndex = remember(selectedIndexForBarState) {
                { selectedIndexForBarState.value }
            }
            val floatingOnSelected = remember(selectedIndexForBarState, handleSelectedState) {
                { index: Int ->
                    // External page/tap synchronization only animates the indicator. User drag
                    // is the path that needs to submit a new selection.
                    if (index != selectedIndexForBarState.value) {
                        handleSelectedState.value(index)
                    }
                }
            }
            val floatingOnReselected = remember(selectedIndexForBarState, handleSelectedState) {
                { handleSelectedState.value(selectedIndexForBarState.value) }
            }

            Row(
                modifier = Modifier
                    .height(shellHeight)
                    .align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(dockWidth)
                        .height(dockHeight)
                        // Allow reminder badges to paint slightly outside the dock band.
                        .graphicsLayer { clip = false }
                ) {
                    // Skin trim behind the BiliPai glass dock so it never steals hits.
                    BottomBarSkinDecorativeTrim(
                        decoration = uiSkinDecoration,
                        modifier = Modifier.matchParentSize(),
                        clipShape = resolveSharedBottomBarCapsuleShape()
                    )
                    if (shouldComposeDockContent) {
                        val dockModifier = Modifier
                            .width(dockWidth)
                            .height(dockHeight)
                            .alpha(dockContentAlpha)
                            .graphicsLayer { clip = false }
                        val dockContent: @Composable RowScope.() -> Unit = {
                            visibleItems.forEachIndexed { index, item ->
                                val label = resolveBottomNavItemLabel(item, itemLabels)
                                val routeSelected = currentItem == item
                                val selected = index == selectedIndexForBar ||
                                    LocalFloatingBottomBarActiveContent.current
                                val skinIconPath = uiSkinDecoration?.iconPathFor(
                                    item,
                                    selected = routeSelected
                                )
                                val reminderBadgeText = formatBottomBarDynamicReminderBadge(
                                    if (shouldShowBottomBarDynamicReminderBadge(item, dynamicUnreadCount)) {
                                        dynamicUnreadCount
                                    } else {
                                        0
                                    }
                                )
                                FloatingBottomBarItem(
                                    onClick = { handleBottomBarItemClick(index, item) },
                                    selected = index == selectedIndexForBar,
                                    itemIndex = index,
                                    iconCrossScaleEnabled = navigationIconCrossScaleEnabled,
                                ) {
                                    FloatingBottomBarTabVisual(
                                        item = item,
                                        label = label,
                                        selected = selected,
                                        showIcon = showIcon,
                                        showText = showText,
                                        iconStyle = iconStyle,
                                        skinIconPath = skinIconPath,
                                        dynamicUnreadCount = dynamicUnreadCount,
                                        labelScrimColor = skinContentColors.labelScrimColor,
                                        labelScrimAlpha = skinContentColors.labelScrimAlpha,
                                        reminderBadgeText = reminderBadgeText
                                    )
                                }
                            }

                            if (isTablet && onToggleSidebar != null) {
                                val sidebarLabel = stringResource(R.string.sidebar_toggle)
                                FloatingBottomBarItem(
                                    onClick = ::handleBottomBarSidebarClick,
                                    selected = false,
                                    itemIndex = visibleItems.size,
                                    iconCrossScaleEnabled = navigationIconCrossScaleEnabled,
                                ) {
                                    FloatingBottomBarTabVisual(
                                        item = null,
                                        label = sidebarLabel,
                                        selected = LocalFloatingBottomBarActiveContent.current,
                                        showIcon = showIcon,
                                        showText = showText,
                                        iconStyle = iconStyle,
                                        skinIconPath = null,
                                        dynamicUnreadCount = 0,
                                        labelScrimColor = skinContentColors.labelScrimColor,
                                        labelScrimAlpha = skinContentColors.labelScrimAlpha,
                                        reminderBadgeText = null
                                    )
                                }
                            }
                        }
                        if (usePlainMiuixFloatingBar) {
                            PlainMiuixFloatingBottomBar(
                                selectedIndex = selectedIndexForBar,
                                onSelected = floatingOnSelected,
                                onReselected = floatingOnReselected,
                                tabsCount = totalItems,
                                modifier = dockModifier,
                                colors = floatingColors,
                                content = dockContent,
                            )
                        } else {
                            FloatingBottomBar(
                                selectedIndex = floatingSelectedIndex,
                                onSelected = floatingOnSelected,
                                onReselected = floatingOnReselected,
                                backdrop = miuixBackdrop,
                                tabsCount = totalItems,
                                modifier = dockModifier,
                                mode = floatingMode,
                                colors = floatingColors,
                                shellHeight = dockHeight,
                                indicatorHeight = resolveBiliPaiBottomBarIndicatorHeight(dockHeight),
                                minimumIndicatorWidth = searchLayoutState.minimumIndicatorWidth,
                                indicatorPositionProvider = indicatorPositionProvider,
                                isScrollInProgressProvider = isPagerScrollInProgressProvider,
                                liquidGlassTuning = liquidGlassTuning,
                            ) {
                                dockContent()
                            }
                        }
                    }

                    if (searchEnabled && compactSearchLayout &&
                        compactHomeAlpha > BottomBarTransientAlphaThreshold
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .alpha(compactHomeAlpha)
                                .then(
                                    if (effectiveSearchExpanded) {
                                        Modifier.clickable(
                                            interactionSource = remember {
                                                MutableInteractionSource()
                                            },
                                            indication = null
                                        ) {
                                            val searchOverride =
                                                resolveBottomBarSearchExpansionOverrideOnNavItemClick(
                                                    currentItem = currentItem,
                                                    clickedItem = BottomNavItem.HOME,
                                                    bottomBarSearchEnabled = searchEnabled,
                                                    effectiveSearchExpanded = effectiveSearchExpanded
                                                )
                                            if (searchOverride != null) {
                                                haptic(HapticType.LIGHT)
                                                searchExpansionOverride = searchOverride
                                            } else {
                                                performMaterialBottomBarTap(
                                                    haptic = haptic,
                                                    onClick = { onItemClick(BottomNavItem.HOME) }
                                                )
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val homeSkinIconPath = uiSkinDecoration?.iconPathFor(
                                BottomNavItem.HOME,
                                selected = currentItem == BottomNavItem.HOME
                            )
                            if (homeSkinIconPath != null) {
                                BottomBarSkinIcon(
                                    iconPath = homeSkinIconPath,
                                    contentDescription = null,
                                    selected = currentItem == BottomNavItem.HOME,
                                    size = resolveBottomBarCompactSkinHomeIconSize(),
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = compactHomeIconScale
                                        scaleY = compactHomeIconScale
                                    }
                                )
                            } else {
                                AppIcon(
                                    imageVector = if (iconStyle == SharedFloatingBottomBarIconStyle.MIUIX) {
                                        resolveHomeNavigationBarIcon(
                                            item = BottomNavItem.HOME,
                                            selected = currentItem == BottomNavItem.HOME
                                        )
                                    } else {
                                        resolveMaterialBottomBarIcon(
                                            item = BottomNavItem.HOME,
                                            selected = currentItem == BottomNavItem.HOME
                                        )
                                    },
                                    contentDescription = null,
                                    tint = if (currentItem == BottomNavItem.HOME) {
                                        selectedColor
                                    } else {
                                        unselectedColor
                                    },
                                    modifier = Modifier
                                        .size(compactHomeIconSize)
                                        .graphicsLayer {
                                            scaleX = compactHomeIconScale
                                            scaleY = compactHomeIconScale
                                        }
                                )
                            }
                        }
                    }
                }

                BiliPaiBottomBarSearchSlot(
                    visible = searchLayoutReserved,
                    launchAdjustedSearchGap = launchAdjustedSearchGap,
                    searchWidth = searchWidth,
                    searchHeight = searchHeight,
                    expanded = visualSearchExpanded,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onCompactClick = {
                        val searchOverride = resolveBottomBarSearchExpansionOverrideOnSearchClick(
                            currentItem = currentItem,
                            bottomBarSearchEnabled = searchEnabled,
                            effectiveSearchExpanded = effectiveSearchExpanded,
                            searchLayoutMode = bottomBarSearchLayoutMode
                        )
                        if (searchOverride != null) {
                            searchExpansionOverride = searchOverride
                        } else {
                            onSearchClick()
                        }
                    },
                    onSubmit = {
                        val keyword = searchQuery.trim()
                        if (keyword.isEmpty()) {
                            onSearchClick()
                        } else {
                            onSearchKeywordSubmit(keyword)
                        }
                    },
                    shape = resolveSharedBottomBarCapsuleShape(),
                    miuixBackdrop = miuixBackdrop,
                    containerColor = biliPaiContainerColor,
                    blurEnabled = blurEnabled,
                    glassEnabled = effectiveGlassEnabled,
                    blurRadius = tuning.shellBlurRadiusDp.dp,
                    hazeState = hazeState,
                    motionTier = motionTier,
                    isTransitionRunning = isTransitionRunning,
                    forceLowBlurBudget = forceLowBlurBudget,
                    contentColor = unselectedColor,
                    accentColor = selectedColor,
                    haptic = haptic,
                    liquidGlassPreset = liquidGlassPreset,
                    isScrolling = isFeedScrollInProgress,
                    materialScrollProgress = 0f,
                    materialMotionProgress = 0f,
                    materialPressProgress = 0f,
                    liquidGlassTuning = liquidGlassTuning,
                    iconStyle = iconStyle
                )
            }
        }
    }
}

/**
 * FloatingBottomBar 槽内的 Tab 视觉内容：图标 + 文案 + 角标。
 * 内容色优先读 [LocalFloatingBottomBarContentColor]（BiliPai 契约）。
 */
@Composable
private fun ColumnScope.FloatingBottomBarTabVisual(
    item: BottomNavItem?,
    label: String,
    selected: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    iconStyle: SharedFloatingBottomBarIconStyle,
    skinIconPath: String?,
    dynamicUnreadCount: Int,
    labelScrimColor: Color,
    labelScrimAlpha: Float,
    reminderBadgeText: String?
) {
    val localColor = LocalFloatingBottomBarContentColor.current
    val selectedContentColor = LocalFloatingBottomBarSelectedContentColor.current
    val contentColor = when {
        selected && selectedContentColor != Color.Unspecified -> selectedContentColor
        localColor != Color.Unspecified -> localColor
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val selectedAlpha = if (selected) 1f else 0f
    val selectionScale = LocalFloatingBottomBarItemSelectionScale.current
    val density = LocalDensity.current

    if (showIcon) {
        Box(
            modifier = Modifier.graphicsLayer {
                val scale = selectionScale()
                scaleX = scale
                scaleY = scale
                translationY = with(density) {
                    -resolveNavigationIconSelectionLiftDp(scale).dp.toPx()
                }
                clip = false
            },
            contentAlignment = Alignment.Center,
        ) {
            if (skinIconPath != null) {
                BottomBarReminderBadgeAnchor(
                    badgeText = reminderBadgeText,
                    floatingCompact = true
                ) {
                    BottomBarSkinIcon(
                        iconPath = skinIconPath,
                        contentDescription = label,
                        selected = selected,
                    )
                }
            } else if (item == null) {
                AppIcon(
                    imageVector = if (iconStyle == SharedFloatingBottomBarIconStyle.MIUIX) {
                        resolveMiuixPreferredHomeNavigationIcon(tabId = "PARTITION")
                    } else {
                        Icons.AutoMirrored.Outlined.MenuOpen
                    },
                    contentDescription = label,
                    tint = contentColor
                )
            } else if (iconStyle == SharedFloatingBottomBarIconStyle.MIUIX) {
                BottomBarBlendedMiuixIcon(
                    item = item,
                    unreadCount = dynamicUnreadCount,
                    selectedAlpha = selectedAlpha,
                    contentDescription = label,
                    contentColor = contentColor,
                    floatingCompactBadge = true
                )
            } else {
                BottomBarBlendedMaterialIcon(
                    item = item,
                    unreadCount = dynamicUnreadCount,
                    selectedAlpha = selectedAlpha,
                    contentDescription = label,
                    contentColor = contentColor,
                    floatingCompactBadge = true
                )
            }
        }
    }
    if (showText) {
        AppText(
            text = label,
            color = contentColor,
            fontSize = resolveFloatingDockLabelFontSize(
                showIcon = showIcon,
                showText = showText,
            ),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.bottomBarSkinLabelScrim(
                color = labelScrimColor,
                alpha = if (skinIconPath != null) labelScrimAlpha else 0f
            )
        )
    }
}

@Composable
internal fun BoxScope.BiliPaiMiuixBottomBarIndicatorLayer(
    visible: Boolean,
    dockContentAlpha: Float,
    indicatorTranslationXPx: Float,
    indicatorTranslationYPx: Float = 0f,
    indicatorPanelOffsetPx: Float,
    indicatorPanelOffsetYPx: Float = 0f,
    indicatorWidth: Dp,
    indicatorHeight: Dp = AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Small,
    shellShape: androidx.compose.ui.graphics.Shape,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    contentBackdrop: MiuixBackdrop?,
    backdrop: MiuixBackdrop?,
    indicatorLensSpec: BottomBarBackdropPresetLensSpec,
    liquidGlassTuning: LiquidGlassTuning = resolveLiquidGlassTuning(progress = 0.5f),
    effectivePressProgress: Float,
    indicatorIdleSurfaceColor: Color,
    glassEnabled: Boolean,
    indicatorEffectsEnabled: Boolean = glassEnabled,
    motionProgress: Float,
    velocityItemsPerSecond: Float,
    isDragging: Boolean,
    indicatorLayerScaleProgress: Float,
    indicatorLayerScaleTransform: BottomBarIndicatorLayerTransform? = null,
    dragScaleTarget: Float = BOTTOM_BAR_INDICATOR_DRAG_SCALE_TARGET,
    bottomBarMotionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec,
    isDarkTheme: Boolean,
    swapMotionAxes: Boolean = false,
    indicatorAlignment: Alignment = Alignment.CenterStart,
    interactionModifier: Modifier = Modifier
) {
    if (!visible) return
    val effectiveIndicatorEffectsEnabled = indicatorEffectsEnabled
    val rawIndicatorLayerTransform = if (effectiveIndicatorEffectsEnabled) {
        resolveBottomBarIndicatorLayerTransform(
            motionProgress = motionProgress,
            velocityItemsPerSecond = velocityItemsPerSecond,
            isDragging = isDragging,
            dragScaleProgress = indicatorLayerScaleProgress,
            dragScaleTransform = indicatorLayerScaleTransform,
            dragScaleTarget = dragScaleTarget,
            motionSpec = bottomBarMotionSpec
        )
    } else {
        BottomBarIndicatorLayerTransform(scaleX = 1f, scaleY = 1f)
    }
    val indicatorLayerTransform = if (swapMotionAxes) {
        BottomBarIndicatorLayerTransform(
            scaleX = rawIndicatorLayerTransform.scaleY,
            scaleY = rawIndicatorLayerTransform.scaleX
        )
    } else {
        rawIndicatorLayerTransform
    }
    val pillHighlight = if (glassEnabled) {
        rememberBiliPaiGravityHighlight(
            iosIndicatorSpecular,
            extraDegrees = if (swapMotionAxes) 0f else 90f,
        )
    } else {
        null
    }
    val indicatorBackdrop = if (!glassEnabled) {
        null
    } else if (shouldUseBottomBarCombinedIndicatorBackdrop(liquidGlassPreset)) {
        contentBackdrop
    } else {
        backdrop
    }
    Box(
        modifier = Modifier
            .alpha(dockContentAlpha)
            .graphicsLayer {
                translationX = indicatorTranslationXPx + indicatorPanelOffsetPx
                translationY = indicatorTranslationYPx + indicatorPanelOffsetYPx
                if (indicatorBackdrop == null && effectiveIndicatorEffectsEnabled) {
                    scaleX = indicatorLayerTransform.scaleX
                    scaleY = indicatorLayerTransform.scaleY
                }
            }
            .width(indicatorWidth)
            .height(indicatorHeight)
            .align(indicatorAlignment)
            .zIndex(2f)
            .then(interactionModifier)
            .run {
                if (indicatorBackdrop != null) {
                    miuixDrawBackdrop(
                        backdrop = indicatorBackdrop,
                        shape = { shellShape },
                        effects = {
                            if (shouldUseBottomBarIndicatorLens(liquidGlassPreset)) {
                                miuixLens(
                                    refractionHeight = indicatorLensSpec.refractionHeightDp.dp.toPx(),
                                    refractionAmount = indicatorLensSpec.refractionAmountDp.dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration =
                                        resolveLiquidGlassIndicatorChromaticAberration(
                                            liquidGlassTuning
                                        )
                                )
                            }
                        },
                        highlight = {
                            pillHighlight?.value?.copy(alpha = effectivePressProgress)
                        },
                        onDrawSurface = {
                            val surfaceFade = (1f - effectivePressProgress).coerceIn(0f, 1f)
                            if (surfaceFade > 0f) {
                                drawRect(
                                    color = indicatorIdleSurfaceColor,
                                    alpha = surfaceFade
                                )
                            }
                            if (effectivePressProgress > 0f) {
                                drawRect(
                                    OpticalContrastPalette.Shadow.copy(alpha = 0.03f * effectivePressProgress)
                                )
                            }
                        },
                        layerBlock = {
                            if (effectiveIndicatorEffectsEnabled) {
                                scaleX = indicatorLayerTransform.scaleX
                                scaleY = indicatorLayerTransform.scaleY
                            }
                        }
                    ).miuixInnerShadow(shape = shellShape) {
                        MiuixInnerShadow(
                            radius = AppSpacingTokens.Small * effectivePressProgress,
                            color = OpticalContrastPalette.Shadow.copy(alpha = 0.15f),
                            alpha = effectivePressProgress
                        )
                    }
                } else {
                    this
                }
            }
    )
}

@Composable
private fun BiliPaiBottomBarSearchSlot(
    visible: Boolean,
    launchAdjustedSearchGap: Dp,
    searchWidth: Dp,
    searchHeight: Dp,
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onCompactClick: () -> Unit,
    onSubmit: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    miuixBackdrop: MiuixBackdrop?,
    containerColor: Color,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    blurRadius: Dp,
    hazeState: HazeState?,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    contentColor: Color,
    accentColor: Color,
    haptic: (HapticType) -> Unit,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    isScrolling: Boolean,
    materialScrollProgress: Float,
    materialMotionProgress: Float,
    materialPressProgress: Float,
    liquidGlassTuning: LiquidGlassTuning,
    iconStyle: SharedFloatingBottomBarIconStyle
) {
    if (!visible) return
    Spacer(modifier = Modifier.width(launchAdjustedSearchGap))
    Box(
        modifier = Modifier
            .width(searchWidth)
            .height(searchHeight)
    ) {
        BiliPaiBottomBarSearchCapsule(
            width = searchWidth,
            height = searchHeight,
            expanded = expanded,
            query = query,
            onQueryChange = onQueryChange,
            onCompactClick = onCompactClick,
            onSubmit = onSubmit,
            shape = shape,
            miuixBackdrop = miuixBackdrop,
            containerColor = containerColor,
            blurEnabled = blurEnabled,
            glassEnabled = glassEnabled,
            blurRadius = blurRadius,
            hazeState = hazeState,
            motionTier = motionTier,
            isTransitionRunning = isTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget,
            contentColor = contentColor,
            accentColor = accentColor,
            haptic = haptic,
            liquidGlassPreset = liquidGlassPreset,
            isScrolling = isScrolling,
            materialScrollProgress = materialScrollProgress,
            materialMotionProgress = materialMotionProgress,
            materialPressProgress = materialPressProgress,
            liquidGlassTuning = liquidGlassTuning,
            iconStyle = iconStyle
        )
    }
}

@Composable
private fun BiliPaiBottomBarSearchCapsule(
    width: Dp,
    height: Dp,
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onCompactClick: () -> Unit,
    onSubmit: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    miuixBackdrop: MiuixBackdrop?,
    containerColor: Color,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    blurRadius: Dp,
    hazeState: HazeState?,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    contentColor: Color,
    accentColor: Color,
    haptic: (HapticType) -> Unit,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    isScrolling: Boolean,
    materialScrollProgress: Float,
    materialMotionProgress: Float,
    materialPressProgress: Float,
    liquidGlassTuning: LiquidGlassTuning,
    iconStyle: SharedFloatingBottomBarIconStyle
) {
    val currentOnCompactClick by rememberUpdatedState(onCompactClick)
    val currentOnSubmit by rememberUpdatedState(onSubmit)
    val currentHaptic by rememberUpdatedState(haptic)
    val fieldAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = bottomBarContentVisibilityMotionSpec(),
        label = "bottomBarSearchFieldAlpha"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (expanded) 0.92f else 1f,
        animationSpec = bottomBarContentVisibilityMotionSpec(),
        label = "bottomBarSearchIconScale"
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .biliPaiMiuixFloatingDockSurface(
                shape = shape,
                backdrop = miuixBackdrop,
                containerColor = containerColor,
                blurEnabled = blurEnabled,
                glassEnabled = glassEnabled,
                blurRadius = blurRadius,
                hazeState = hazeState,
                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget,
                liquidGlassPreset = liquidGlassPreset,
                isScrolling = isScrolling,
                materialScrollProgress = materialScrollProgress,
                materialMotionProgress = materialMotionProgress,
                materialPressProgress = materialPressProgress,
                liquidGlassTuning = liquidGlassTuning
            )
            .then(
                if (!expanded) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClick = {
                            currentHaptic(HapticType.LIGHT)
                            currentOnCompactClick()
                        },
                    )
                } else {
                    Modifier.semantics {
                        contentDescription = "搜索输入框"
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        BiliPaiBottomBarSearchVisualContent(
            expanded = expanded,
            query = query,
            onQueryChange = onQueryChange,
            onSubmit = {
                currentOnSubmit()
            },
            contentColor = contentColor,
            accentColor = accentColor,
            iconScale = iconScale,
            fieldAlpha = fieldAlpha,
            interactive = true,
            iconStyle = iconStyle
        )
    }
}

@Composable
internal fun BiliPaiBottomBarSearchVisualContent(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    iconScale: Float,
    fieldAlpha: Float,
    interactive: Boolean,
    iconStyle: SharedFloatingBottomBarIconStyle
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded, interactive) {
        if (expanded && interactive) focusRequester.requestFocus()
    }
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (expanded) AppSpacingTokens.Large else AppSpacingTokens.None),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(AppChromeSizeTokens.MinimumTouchTarget)
                .then(
                    if (expanded && interactive) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSubmit
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(
                imageVector = if (iconStyle == SharedFloatingBottomBarIconStyle.MIUIX) {
                    MiuixIcons.Search
                } else {
                    Icons.Outlined.Search
                },
                contentDescription = "搜索",
                tint = contentColor,
                modifier = Modifier
                    .size(AppSpacingTokens.ExtraLarge)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.width(AppSpacingTokens.Small + AppSpacingTokens.Micro))
            if (interactive) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
                    cursorBrush = SolidColor(accentColor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .weight(1f)
                        .alpha(fieldAlpha),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isBlank()) {
                                AppText(
                                    text = "搜索",
                                    color = contentColor.copy(alpha = 0.45f),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(fieldAlpha),
                    contentAlignment = Alignment.CenterStart
                ) {
                    AppText(
                        text = query.ifBlank { "搜索" },
                        color = if (query.isBlank()) {
                            contentColor.copy(alpha = 0.45f)
                        } else {
                            contentColor
                        },
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomBarInputTarget(
    itemWidth: Dp,
    onClick: () -> Unit,
    onPressChanged: (Boolean) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val currentOnPressChanged by rememberUpdatedState(onPressChanged)

    LaunchedEffect(isPressed) {
        onPressChanged(isPressed)
    }
    DisposableEffect(Unit) {
        onDispose {
            currentOnPressChanged(false)
        }
    }

    Box(
        modifier = Modifier
            .width(itemWidth)
            .fillMaxHeight()
            .clip(resolveSharedBottomBarCapsuleShape())
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    )
}

@Composable
private fun RowScope.AndroidNativeBottomBarItem(
    item: BottomNavItem?,
    itemWidth: Dp,
    label: String,
    dynamicUnreadCount: Int = 0,
    selected: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    contentColorOverride: Color? = null,
    iconStyle: SharedFloatingBottomBarIconStyle,
    skinIconPath: String? = null,
    labelScrimColor: Color = Color.Transparent,
    labelScrimAlpha: Float = 0f,
    onClick: () -> Unit,
    interactive: Boolean,
    onPressChanged: (Boolean) -> Unit = {},
    selectedIconAlpha: Float = if (selected) 1f else 0f,
    scale: Float = 1f,
    clickPulseKey: Int = 0
) {
    val animatedContentColor by animateColorAsState(
        targetValue = if (selected) selectedColor else unselectedColor,
        label = "${label}_android_native_bottom_bar_color"
    )
    val contentColor = resolveAndroidNativeBottomBarItemContentColor(
        contentColorOverride = contentColorOverride,
        animatedContentColor = animatedContentColor
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val clickPulseTransform = rememberBottomBarClickPulseTransform(clickPulseKey)
    val currentOnPressChanged by rememberUpdatedState(onPressChanged)
    val shouldUseSkinItemLayout = skinIconPath != null && showIcon && showText
    val skinIconPathForLayout = if (shouldUseSkinItemLayout) skinIconPath else null
    val iconLabelGap = if (shouldUseSkinItemLayout) {
        resolveBottomBarSkinIconLabelGap()
    } else {
        AppSpacingTokens.None
    }

    LaunchedEffect(isPressed, interactive) {
        if (interactive) {
            onPressChanged(isPressed)
        }
    }
    DisposableEffect(interactive) {
        onDispose {
            if (interactive) {
                currentOnPressChanged(false)
            }
        }
    }

    Box(
        modifier = Modifier
            .width(itemWidth)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale * clickPulseTransform.scaleX
                scaleY = scale * clickPulseTransform.scaleY
            }
            .then(
                if (interactive) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (skinIconPathForLayout != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                BottomBarSkinIcon(
                    iconPath = skinIconPathForLayout,
                    contentDescription = label,
                    selected = selected,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = resolveBottomBarSkinDockIconTopPadding())
                )
                AppText(
                    text = label,
                    color = contentColor,
                    fontSize = resolveBottomBarSkinDockLabelFontSize(),
                    lineHeight = resolveBottomBarSkinDockLabelLineHeight(),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .bottomBarSkinLabelScrim(
                            color = labelScrimColor,
                            alpha = labelScrimAlpha
                        )
                        .padding(
                            start = AppSpacingTokens.Micro,
                            end = AppSpacingTokens.Micro,
                            bottom = resolveBottomBarSkinDockLabelBottomPadding()
                        )
                )
            }
        } else {
            Column(
                modifier = Modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
            if (showIcon) {
                Box(
                    modifier = Modifier,
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        when {
                            skinIconPath != null -> {
                                BottomBarSkinIcon(
                                    iconPath = skinIconPath,
                                    contentDescription = label,
                                    selected = selected,
                                )
                            }
                            item == null && iconStyle == SharedFloatingBottomBarIconStyle.MIUIX -> {
                                AppIcon(
                                    imageVector = resolveMiuixPreferredHomeNavigationIcon(tabId = "PARTITION"),
                                    contentDescription = label
                                )
                            }
                            item == null -> {
                                AppIcon(
                                    imageVector = Icons.AutoMirrored.Outlined.MenuOpen,
                                    contentDescription = label
                                )
                            }
                            iconStyle == SharedFloatingBottomBarIconStyle.MIUIX -> {
                                BottomBarBlendedMiuixIcon(
                                    item = item,
                                    unreadCount = dynamicUnreadCount,
                                    selectedAlpha = selectedIconAlpha,
                                    contentDescription = label,
                                    contentColor = contentColor
                                )
                            }
                            else -> {
                                BottomBarBlendedMaterialIcon(
                                    item = item,
                                    unreadCount = dynamicUnreadCount,
                                    selectedAlpha = selectedIconAlpha,
                                    contentDescription = label,
                                    contentColor = contentColor
                                )
                            }
                        }
                    }
                }
            }
            if (showIcon && showText && iconLabelGap > AppSpacingTokens.None) {
                Spacer(modifier = Modifier.height(iconLabelGap))
            }
            if (showText) {
                AppText(
                    text = label,
                    color = contentColor,
                    fontSize = if (shouldUseSkinItemLayout) {
                        resolveBottomBarSkinDockLabelFontSize()
                    } else {
                        MaterialTheme.typography.labelSmall.fontSize
                    },
                    lineHeight = if (shouldUseSkinItemLayout) {
                        resolveBottomBarSkinDockLabelLineHeight()
                    } else {
                        MaterialTheme.typography.labelMedium.lineHeight
                    },
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.bottomBarSkinLabelScrim(
                        color = labelScrimColor,
                        alpha = if (skinIconPath != null) labelScrimAlpha else 0f
                    )
                )
            }
            }
        }
    }
}

internal fun resolveMaterialBottomBarIcon(
    item: BottomNavItem,
    selected: Boolean
): ImageVector = when (item) {
    BottomNavItem.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    BottomNavItem.DYNAMIC -> if (selected) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone
    BottomNavItem.STORY -> if (selected) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircleOutline
    BottomNavItem.HISTORY -> if (selected) Icons.Filled.History else Icons.Outlined.History
    BottomNavItem.LISTEN_VIDEO -> if (selected) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic
    BottomNavItem.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    BottomNavItem.FAVORITE -> if (selected) Icons.Filled.CollectionsBookmark else Icons.Outlined.CollectionsBookmark
    BottomNavItem.LIVE -> if (selected) Icons.Filled.LiveTv else Icons.Outlined.LiveTv
    BottomNavItem.WATCHLATER -> if (selected) Icons.Filled.WatchLater else Icons.Outlined.WatchLater
    BottomNavItem.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
    BottomNavItem.PLUGINS -> if (selected) Icons.Filled.Extension else Icons.Outlined.Extension
}

@Composable
internal fun resolveHomeNavigationBarIcon(
    item: BottomNavItem,
    selected: Boolean
): ImageVector = resolveMiuixBottomNavigationIcon(item, selected)

@Composable
private fun resolveSharedBottomBarIcon(
    item: BottomNavItem,
    selected: Boolean,
    iconStyle: SharedFloatingBottomBarIconStyle
): ImageVector = when (iconStyle) {
    SharedFloatingBottomBarIconStyle.MATERIAL -> resolveMaterialBottomBarIcon(item, selected)
    SharedFloatingBottomBarIconStyle.MIUIX -> resolveHomeNavigationBarIcon(item, selected)
}

@Composable
private fun resolveSharedBottomBarSidebarIcon(
    iconStyle: SharedFloatingBottomBarIconStyle
): ImageVector = when (iconStyle) {
    SharedFloatingBottomBarIconStyle.MATERIAL -> Icons.AutoMirrored.Outlined.MenuOpen
    SharedFloatingBottomBarIconStyle.MIUIX ->
        resolveMiuixPreferredHomeNavigationIcon(tabId = "PARTITION")
}

@Composable
private fun BottomBarBlendedMiuixIcon(
    item: BottomNavItem,
    unreadCount: Int = 0,
    selectedAlpha: Float,
    contentDescription: String?,
    contentColor: Color,
    floatingCompactBadge: Boolean = false
) {
    val clampedSelectedAlpha = selectedAlpha.coerceIn(0f, 1f)
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        BottomBarReminderBadgeAnchor(
            item = item,
            unreadCount = unreadCount,
            floatingCompact = floatingCompactBadge
        ) {
            val idleIcon = resolveHomeNavigationBarIcon(item, selected = false)
            val activeIcon = resolveHomeNavigationBarIcon(item, selected = true)
            if (idleIcon == activeIcon) {
                AppIcon(
                    imageVector = idleIcon,
                    contentDescription = contentDescription
                )
            } else {
                AppIcon(
                    imageVector = idleIcon,
                    contentDescription = contentDescription,
                    modifier = Modifier.alpha(1f - clampedSelectedAlpha)
                )
                AppIcon(
                    imageVector = activeIcon,
                    contentDescription = null,
                    modifier = Modifier.alpha(clampedSelectedAlpha)
                )
            }
        }
    }
}

@Composable
private fun BottomBarBlendedMaterialIcon(
    item: BottomNavItem,
    unreadCount: Int = 0,
    selectedAlpha: Float,
    contentDescription: String?,
    contentColor: Color,
    floatingCompactBadge: Boolean = false
) {
    val clampedSelectedAlpha = selectedAlpha.coerceIn(0f, 1f)
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        BottomBarReminderBadgeAnchor(
            item = item,
            unreadCount = unreadCount,
            floatingCompact = floatingCompactBadge
        ) {
            AppIcon(
                imageVector = resolveMaterialBottomBarIcon(item, selected = false),
                contentDescription = contentDescription,
                modifier = Modifier.alpha(1f - clampedSelectedAlpha)
            )
            AppIcon(
                imageVector = resolveMaterialBottomBarIcon(item, selected = true),
                contentDescription = null,
                modifier = Modifier.alpha(clampedSelectedAlpha)
            )
        }
    }
}

@Composable
private fun BottomBarReminderBadgeAnchor(
    item: BottomNavItem?,
    unreadCount: Int = 0,
    modifier: Modifier = Modifier,
    floatingCompact: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    BottomBarReminderBadgeAnchor(
        badgeText = formatBottomBarDynamicReminderBadge(
            if (shouldShowBottomBarDynamicReminderBadge(item, unreadCount)) {
                unreadCount
            } else {
                0
            }
        ),
        modifier = modifier,
        floatingCompact = floatingCompact,
        content = content
    )
}

@Composable
private fun BottomBarReminderBadgeAnchor(
    badgeText: String?,
    modifier: Modifier = Modifier,
    /**
     * Floating BiliPai dock is short (64dp shell). Use a tighter badge offset so the
     * pill does not clip the top of the reminder badge.
     */
    floatingCompact: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val badgeOffsetX = if (floatingCompact) {
        AppSpacingTokens.Small
    } else {
        AppSpacingTokens.Medium
    }
    val badgeOffsetY = if (floatingCompact) {
        -AppSpacingTokens.Micro
    } else {
        -AppSpacingTokens.Small
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        content()
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = badgeOffsetX, y = badgeOffsetY)
                    .defaultMinSize(
                        minWidth = AppSpacingTokens.Large + AppSpacingTokens.Micro,
                        minHeight = AppSpacingTokens.Large + AppSpacingTokens.Micro
                    )
                    .background(iOSRed, CircleShape)
                    .border(
                        width = AppSpacingTokens.Micro / 2,
                        color = AppSurfaceTokens.cardContainer(),
                        shape = CircleShape
                    )
                    .padding(
                        horizontal = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro / 2,
                        vertical = AppSpacingTokens.Micro / 2
                    ),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    text = badgeText,
                    color = OpticalContrastPalette.Highlight,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

internal fun resolveBottomBarSurfaceColor(
    surfaceColor: Color,
    blurEnabled: Boolean,
    blurIntensity: com.android.purebilibili.core.ui.blur.BlurIntensity
): Color {
    val alpha = if (blurEnabled) {
        BlurStyles.getBackgroundAlpha(blurIntensity)
    } else {
        return surfaceColor
    }
    return surfaceColor.copy(alpha = alpha)
}

internal fun shouldUseHomeCombinedClickable(
    item: BottomNavItem,
    isSelected: Boolean
): Boolean {
    return item == BottomNavItem.HOME && isSelected
}

internal enum class BottomBarPrimaryTapAction {
    Navigate,
    HomeReselect
}

internal fun resolveBottomBarPrimaryTapAction(
    item: BottomNavItem,
    isSelected: Boolean
): BottomBarPrimaryTapAction {
    return if (item == BottomNavItem.HOME && isSelected) {
        BottomBarPrimaryTapAction.HomeReselect
    } else {
        BottomBarPrimaryTapAction.Navigate
    }
}

internal fun performBottomBarPrimaryTap(
    item: BottomNavItem,
    isSelected: Boolean,
    haptic: (HapticType) -> Unit,
    onNavigate: () -> Unit,
    onHomeReselect: () -> Unit
) {
    haptic(HapticType.LIGHT)
    when (resolveBottomBarPrimaryTapAction(item, isSelected)) {
        BottomBarPrimaryTapAction.Navigate -> onNavigate()
        BottomBarPrimaryTapAction.HomeReselect -> onHomeReselect()
    }
}

internal fun performMaterialBottomBarTap(
    haptic: (HapticType) -> Unit,
    onClick: () -> Unit
) {
    haptic(HapticType.LIGHT)
    onClick()
}

internal fun shouldAcceptBottomBarTap(
    tappedItem: BottomNavItem,
    lastTappedItem: BottomNavItem?,
    currentTimeMillis: Long,
    lastTapTimeMillis: Long,
    debounceWindowMillis: Long
): Boolean {
    if (lastTappedItem == null) return true
    if (tappedItem != lastTappedItem) return true
    return currentTimeMillis - lastTapTimeMillis > debounceWindowMillis
}

internal fun shouldUseBottomReselectCombinedClickable(
    item: BottomNavItem,
    isSelected: Boolean
): Boolean {
    return isSelected && item == BottomNavItem.DYNAMIC
}

internal data class BottomBarItemColorBinding(
    val colorIndex: Int,
    val hasCustomAccent: Boolean
)

internal fun resolveBottomBarItemColorBinding(
    item: BottomNavItem,
    itemColorIndices: Map<String, Int>
): BottomBarItemColorBinding {
    if (itemColorIndices.isEmpty()) {
        return BottomBarItemColorBinding(colorIndex = 0, hasCustomAccent = false)
    }

    val match = resolveBottomNavItemLookupKeys(item).firstNotNullOfOrNull { key ->
        itemColorIndices[key]
    }
    return if (match != null) {
        BottomBarItemColorBinding(colorIndex = match, hasCustomAccent = true)
    } else {
        BottomBarItemColorBinding(colorIndex = 0, hasCustomAccent = false)
    }
}

internal fun resolveBottomBarSelectedContentColor(
    item: BottomNavItem,
    binding: BottomBarItemColorBinding,
    themeColor: Color
): Color {
    return if (binding.hasCustomAccent) {
        BottomBarColors.getColorByIndex(binding.colorIndex)
    } else {
        themeColor
    }
}

internal fun resolveAndroidNativeBottomBarItemContentColor(
    contentColorOverride: Color?,
    animatedContentColor: Color
): Color {
    return contentColorOverride ?: animatedContentColor
}

internal fun resolveBottomBarSlidingContentColor(
    unselectedColor: Color,
    selectedColor: Color,
    selectionFraction: Float,
    isPending: Boolean
): Color {
    val fraction = selectionFraction.coerceIn(0f, 1f)
    if (isPending) return selectedColor
    return lerpColor(
        start = unselectedColor,
        stop = selectedColor,
        fraction = fraction
    )
}

internal fun resolveBottomBarReadableContentColor(
    isLightMode: Boolean,
    liquidGlassProgress: Float,
    contentLuminance: Float
): Color {
    if (isLightMode) {
        return OpticalContrastPalette.Shadow
    }
    val shouldUseDarkForeground = liquidGlassProgress >= 0.62f && contentLuminance > 0.6f
    return if (shouldUseDarkForeground) {
        OpticalContrastPalette.Shadow.copy(alpha = 0.82f)
    } else {
        OpticalContrastPalette.Highlight.copy(
            alpha = if (liquidGlassProgress < 0.35f) 0.97f else 0.95f
        )
    }
}

internal fun resolveIos26BottomIndicatorGrayColor(isDarkTheme: Boolean): Color {
    return if (isDarkTheme) {
        // Dark mode: brighter neutral gray to float above dark glass.
        iOSSystemGray3
    } else {
        // Light mode: deeper neutral gray to stay visible on bright background.
        iOSSystemGray
    }
}
