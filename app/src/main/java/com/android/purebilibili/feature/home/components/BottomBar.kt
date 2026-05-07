// 文件路径: feature/home/components/BottomBar.kt
package com.android.purebilibili.feature.home.components

// Duplicate import removed
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer  //  晃动动画
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
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
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.adaptive.MotionTier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.theme.iOSSystemGray
import com.android.purebilibili.core.theme.iOSSystemGray3
import com.android.purebilibili.core.theme.iOSSystemGray6
import com.android.purebilibili.core.theme.iOSRed
import com.android.purebilibili.core.theme.BottomBarColors  // 统一底栏颜色配置
import com.android.purebilibili.core.theme.BottomBarColorPalette  // 调色板
import com.android.purebilibili.core.theme.LocalCornerRadiusScale
import com.android.purebilibili.core.theme.LocalAndroidNativeVariant
import com.android.purebilibili.core.theme.LocalUiPreset
import com.android.purebilibili.core.theme.AndroidNativeVariant
import com.android.purebilibili.core.theme.UiPreset
import com.android.purebilibili.core.theme.iOSCornerRadius
import kotlinx.coroutines.launch  //  延迟导航
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import com.android.purebilibili.core.ui.animation.DampedDragAnimationState
import com.android.purebilibili.core.ui.animation.rememberDampedDragAnimationState
import com.android.purebilibili.core.ui.animation.horizontalDragGesture
import com.android.purebilibili.feature.home.LocalHomeScrollOffset
import com.android.purebilibili.core.ui.motion.BottomBarMotionProfile
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import com.android.purebilibili.core.ui.motion.resolveBottomBarMotionSpec
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import dev.chrisbanes.haze.hazeEffect // [New]
import dev.chrisbanes.haze.HazeStyle   // [New]
// [LayerBackdrop] AndroidLiquidGlass library for real background refraction
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.foundation.shape.RoundedCornerShape as RoundedCornerShapeAlias
import androidx.compose.ui.Modifier.Companion.then
import dev.chrisbanes.haze.hazeSource
import com.android.purebilibili.core.ui.effect.liquidGlass
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset
import com.android.purebilibili.core.store.BottomBarSearchAutoExpandMode
import com.android.purebilibili.core.store.LiquidGlassStyle // [New] Top-level enum
import com.android.purebilibili.core.store.LiquidGlassMode
import androidx.compose.foundation.isSystemInDarkTheme // [New] Theme detection for adaptive readability
import androidx.compose.animation.core.EaseOut
import kotlin.math.sign
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationDisplayMode as MiuixNavigationDisplayMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 底部导航项枚举 -  使用 iOS SF Symbols 风格图标
 * [HIG] 所有图标包含 contentDescription 用于无障碍访问
 */
enum class BottomNavItem(
    val label: String,
    @StringRes val labelRes: Int,
    @StringRes val contentDescriptionRes: Int,
    val legacyAliases: List<String> = emptyList(),
    val selectedIcon: @Composable () -> Unit,
    val unselectedIcon: @Composable () -> Unit,
    val route: String // [新增] 路由地址
) {
    HOME(
        "首页",
        R.string.bottom_nav_home,
        R.string.bottom_nav_home,
        emptyList(),
        { Icon(CupertinoIcons.Filled.House, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.House, contentDescription = null) },
        ScreenRoutes.Home.route
    ),
    DYNAMIC(
        "动态",
        R.string.bottom_nav_dynamic,
        R.string.bottom_nav_dynamic,
        emptyList(),
        { Icon(CupertinoIcons.Filled.Bell, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Bell, contentDescription = null) },
        ScreenRoutes.Dynamic.route
    ),
    STORY(
        "短视频",
        R.string.bottom_nav_story,
        R.string.bottom_nav_story,
        emptyList(),
        { Icon(CupertinoIcons.Filled.PlayCircle, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.PlayCircle, contentDescription = null) },
        ScreenRoutes.Story.route
    ),
    HISTORY(
        "历史",
        R.string.bottom_nav_history,
        R.string.bottom_nav_history_desc,
        listOf("历史记录"),
        { Icon(CupertinoIcons.Filled.Clock, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Clock, contentDescription = null) },
        ScreenRoutes.History.route
    ),
    PROFILE(
        "我的",
        R.string.bottom_nav_profile,
        R.string.bottom_nav_profile_desc,
        listOf("个人中心"),
        { Icon(CupertinoIcons.Filled.Person, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Person, contentDescription = null) },
        ScreenRoutes.Profile.route
    ),
    FAVORITE(
        "收藏",
        R.string.bottom_nav_favorite,
        R.string.bottom_nav_favorite_desc,
        listOf("收藏夹"),
        { Icon(CupertinoIcons.Filled.Star, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Star, contentDescription = null) },
        ScreenRoutes.Favorite.route
    ),
    LIVE(
        "直播",
        R.string.bottom_nav_live,
        R.string.bottom_nav_live,
        emptyList(),
        { Icon(CupertinoIcons.Filled.Video, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Video, contentDescription = null) },
        ScreenRoutes.LiveList.route
    ),
    WATCHLATER(
        "稍后看",
        R.string.bottom_nav_watch_later,
        R.string.bottom_nav_watch_later_desc,
        listOf("稍后再看"),
        { Icon(CupertinoIcons.Filled.Bookmark, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Bookmark, contentDescription = null) },
        ScreenRoutes.WatchLater.route
    ),
    SETTINGS(
        "设置",
        R.string.bottom_nav_settings,
        R.string.bottom_nav_settings,
        emptyList(),
        { Icon(CupertinoIcons.Filled.Gearshape, contentDescription = null) },
        { Icon(CupertinoIcons.Default.Gearshape, contentDescription = null) },
        ScreenRoutes.Settings.route
    )
}

@Composable
internal fun resolveBottomNavItemLabel(item: BottomNavItem): String = stringResource(item.labelRes)

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

private enum class SharedFloatingBottomBarIconStyle {
    MATERIAL,
    CUPERTINO
}

internal data class AndroidNativeIndicatorSpec(
    val usesLens: Boolean,
    val captureTintedContentLayer: Boolean
)

internal fun resolveSharedBottomBarCapsuleShape(): androidx.compose.ui.graphics.Shape =
    RoundedCornerShape(percent = 50)

internal fun resolveKernelSuFloatingBottomBarWidth(
    containerWidth: Dp,
    itemCount: Int,
    minEdgePadding: Dp
): Dp {
    val safeItemCount = itemCount.coerceAtLeast(1)
    val contentPadding = 4.dp
    val preferredWidth = (76.dp * safeItemCount) + (contentPadding * 2)
    val minimumWidth = (52.dp * safeItemCount) + (contentPadding * 2)
    val widthCap = (containerWidth - (minEdgePadding * 2)).coerceAtLeast(minimumWidth)
    return minOf(preferredWidth, widthCap).coerceAtMost(containerWidth)
}

internal data class KernelSuBottomBarSearchLayout(
    val dockWidth: Dp,
    val searchWidth: Dp,
    val gap: Dp
)

internal fun resolveKernelSuBottomBarSearchCircleSize(): Dp = 64.dp

internal fun resolveKernelSuExpandedHomeIconSize(): Dp = 28.dp

internal fun resolveKernelSuExpandedHomeIconScale(): Float = 0.92f

internal fun resolveKernelSuBottomBarSearchLayout(
    containerWidth: Dp,
    itemCount: Int,
    minEdgePadding: Dp,
    searchEnabled: Boolean,
    searchExpanded: Boolean
): KernelSuBottomBarSearchLayout {
    val baseDockWidth = resolveKernelSuFloatingBottomBarWidth(
        containerWidth = containerWidth,
        itemCount = itemCount,
        minEdgePadding = minEdgePadding
    )
    if (!searchEnabled) {
        return KernelSuBottomBarSearchLayout(
            dockWidth = baseDockWidth,
            searchWidth = 0.dp,
            gap = 0.dp
        )
    }

    val gap = 10.dp
    val availableWidth = (containerWidth - (minEdgePadding * 2)).coerceAtLeast(0.dp)
    val searchCircleSize = resolveKernelSuBottomBarSearchCircleSize()
    val compactHomeDockSize = searchCircleSize
    val minimumDockWidth = searchCircleSize
    val collapsedSearchWidth = searchCircleSize
    val expandedSearchWidth = minOf(
        280.dp,
        (availableWidth - compactHomeDockSize - gap).coerceAtLeast(176.dp)
    )
    val targetSearchWidth = if (searchExpanded) expandedSearchWidth else collapsedSearchWidth
    val targetDockWidth = if (searchExpanded) {
        compactHomeDockSize
    } else {
        minOf(baseDockWidth, (availableWidth - targetSearchWidth - gap).coerceAtLeast(minimumDockWidth))
    }
    return KernelSuBottomBarSearchLayout(
        dockWidth = targetDockWidth,
        searchWidth = targetSearchWidth,
        gap = gap
    )
}

internal fun resolveKernelSuBottomBarDockHeight(searchExpanded: Boolean): Dp {
    return if (searchExpanded) {
        resolveKernelSuBottomBarSearchCircleSize()
    } else {
        64.dp
    }
}

internal fun resolveKernelSuBottomBarSearchHeight(searchExpanded: Boolean): Dp {
    return if (searchExpanded) 58.dp else 64.dp
}

internal fun shouldAutoExpandBottomBarSearch(
    currentItem: BottomNavItem,
    bottomBarSearchEnabled: Boolean,
    autoExpandMode: BottomBarSearchAutoExpandMode,
    homeScrollOffsetPx: Float,
    topThresholdPx: Float = 32f
): Boolean {
    if (!bottomBarSearchEnabled || currentItem != BottomNavItem.HOME) return false
    return when (autoExpandMode) {
        BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP -> homeScrollOffsetPx <= topThresholdPx
        BottomBarSearchAutoExpandMode.EXPAND_WHEN_SCROLLING_DOWN -> homeScrollOffsetPx > topThresholdPx
        BottomBarSearchAutoExpandMode.DISABLED -> false
    }
}

internal fun resolveBottomBarSearchEnabledForItem(
    currentItem: BottomNavItem,
    bottomBarSearchEnabled: Boolean
): Boolean {
    return bottomBarSearchEnabled && currentItem == BottomNavItem.HOME
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
    if (!bottomBarSearchEnabled || currentItem != BottomNavItem.HOME || clickedItem != BottomNavItem.HOME) {
        return null
    }
    return if (effectiveSearchExpanded) {
        BottomBarSearchExpansionOverride.COLLAPSED
    } else {
        BottomBarSearchExpansionOverride.EXPANDED
    }
}

internal fun resolveAndroidNativeBottomBarTuning(
    blurEnabled: Boolean,
    darkTheme: Boolean,
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
): AndroidNativeBottomBarTuning {
    if (androidNativeVariant == AndroidNativeVariant.MATERIAL3_EXPRESSIVE) {
        return AndroidNativeBottomBarTuning(
            cornerRadiusDp = 38f,
            shellShadowElevationDp = if (darkTheme) 0.8f else 1.1f,
            shellBlurRadiusDp = if (blurEnabled) 14f else 0f,
            shellSurfaceAlpha = if (blurEnabled) 0.46f else 1f,
            outerHorizontalPaddingDp = 22f,
            innerHorizontalPaddingDp = 6f,
            indicatorHeightDp = 60f,
            indicatorLensRadiusDp = 28f
        )
    }
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
    glassEnabled: Boolean
): Color {
    return if (glassEnabled) {
        surfaceColor.copy(alpha = if (surfaceColor.luminance() < 0.5f) 0.30f else 0.38f)
    } else {
        surfaceColor.copy(alpha = tuning.shellSurfaceAlpha)
    }
}

internal fun resolveAndroidNativeFloatingBottomBarContainerColor(
    surfaceColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    blurIntensity: com.android.purebilibili.core.ui.blur.BlurIntensity
): Color {
    return if (glassEnabled) {
        resolveAndroidNativeBottomBarContainerColor(
            surfaceColor = surfaceColor,
            tuning = tuning,
            glassEnabled = true
        )
    } else {
        resolveBottomBarSurfaceColor(
            surfaceColor = surfaceColor,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    }
}

internal fun resolveAndroidNativeBottomBarGlassEnabled(
    liquidGlassEnabled: Boolean,
    blurEnabled: Boolean
): Boolean = liquidGlassEnabled

internal fun shouldUseAndroidNativeFloatingHazeBlur(
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    hasHazeState: Boolean
): Boolean = blurEnabled && !glassEnabled && hasHazeState

internal fun Modifier.kernelSuFloatingDockSurface(
    shape: androidx.compose.ui.graphics.Shape,
    backdrop: Backdrop?,
    containerColor: Color,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    drawShellLens: Boolean = true,
    nativeVerticalProgress: Float = 0f,
    blurRadius: Dp,
    hazeState: HazeState?,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean
): Modifier = composed {
    val isDarkTheme = isSystemInDarkTheme()
    val useHazeBlur = shouldUseAndroidNativeFloatingHazeBlur(
        blurEnabled = blurEnabled,
        glassEnabled = glassEnabled,
        hasHazeState = hazeState != null
    )
    val surfaceLiquidGlassPreset = if (glassEnabled) {
        liquidGlassPreset
    } else {
        BottomBarLiquidGlassPreset.BILIPAI_TUNED
    }
    val nativeSpec = remember(blurRadius, nativeVerticalProgress) {
        resolveBottomBarBackdropNativeSurfaceSpec(
            blurRadiusDp = blurRadius.value,
            verticalProgress = nativeVerticalProgress
        )
    }

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
                    forceLowBudget = forceLowBlurBudget
                )
            } else {
                Modifier
            }
        )
        .run {
            if (backdrop != null && !useHazeBlur) {
                when (surfaceLiquidGlassPreset) {
                    BottomBarLiquidGlassPreset.BACKDROP_NATIVE -> drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            if (glassEnabled || (blurEnabled && !useHazeBlur)) {
                                vibrancy()
                                blur(nativeSpec.blurRadiusDp.dp.toPx())
                                if (glassEnabled && drawShellLens) {
                                    lens(
                                        refractionHeight = nativeSpec.refractionHeightDp.dp.toPx(),
                                        refractionAmount = nativeSpec.refractionAmountDp.dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = nativeSpec.chromaticAberration
                                    )
                                }
                            }
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = if (glassEnabled) nativeSpec.highlightAlpha else 0f)
                        },
                        shadow = {
                            Shadow.Default.copy(
                                color = Color.Black.copy(
                                    alpha = if (isDarkTheme) {
                                        nativeSpec.shadowAlpha
                                    } else {
                                        nativeSpec.shadowAlpha * 0.58f
                                    }
                                )
                            )
                        },
                        onDrawSurface = {
                            drawRect(
                                containerColor.copy(
                                    alpha = containerColor.alpha * nativeSpec.surfaceAlphaMultiplier
                                )
                            )
                        }
                    )
                    BottomBarLiquidGlassPreset.BILIPAI_TUNED -> drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            if (glassEnabled || (blurEnabled && !useHazeBlur)) {
                                vibrancy()
                                blur(blurRadius.toPx())
                                if (glassEnabled && drawShellLens) {
                                    lens(
                                        refractionHeight = 24.dp.toPx(),
                                        refractionAmount = 24.dp.toPx(),
                                        depthEffect = true,
                                        chromaticAberration = true
                                    )
                                }
                            }
                        },
                        highlight = {
                            Highlight.Default.copy(alpha = if (glassEnabled) 1f else 0f)
                        },
                        shadow = {
                            Shadow.Default.copy(
                                color = Color.Black.copy(alpha = if (isDarkTheme) 0.2f else 0.1f)
                            )
                        },
                        onDrawSurface = {
                            drawRect(containerColor)
                        }
                    )
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
        stop = Color.White,
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

internal fun resolveBottomBarGlassVisibleContentColor(
    unselectedColor: Color,
    selectedColor: Color,
    themeWeight: Float,
    glassEnabled: Boolean,
    indicatorProgress: Float
): Color {
    if (glassEnabled && indicatorProgress > 0.001f) {
        return unselectedColor
    }
    return lerpColor(
        start = unselectedColor,
        stop = selectedColor,
        fraction = themeWeight.coerceIn(0f, 1f)
    )
}

internal fun resolveBottomBarGlassExportContentColor(
    unselectedColor: Color,
    selectedColor: Color,
    themeWeight: Float,
    glassEnabled: Boolean
): Color {
    val clampedWeight = themeWeight.coerceIn(0f, 1f)
    if (glassEnabled && clampedWeight > 0.001f) {
        return selectedColor
    }
    return lerpColor(
        start = unselectedColor,
        stop = selectedColor,
        fraction = clampedWeight
    )
}

internal fun resolveAndroidNativeIdleIndicatorSurfaceColor(
    darkTheme: Boolean
): Color {
    return if (darkTheme) {
        Color.White.copy(alpha = 0.1f)
    } else {
        Color.Black.copy(alpha = 0.1f)
    }
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

private fun Md3BottomBarDisplayMode.toMiuixNavigationDisplayMode(): MiuixNavigationDisplayMode {
    return when (this) {
        Md3BottomBarDisplayMode.IconAndText -> MiuixNavigationDisplayMode.IconAndText
        Md3BottomBarDisplayMode.IconOnly -> MiuixNavigationDisplayMode.IconOnly
        Md3BottomBarDisplayMode.TextOnly -> MiuixNavigationDisplayMode.TextOnly
    }
}

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
private const val BOTTOM_BAR_INDICATOR_DRAG_SCALE_TARGET = 88f / 56f

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
    val exportCaptureWidthScale: Float,
    val forceChromaticAberration: Boolean,
    val indicatorLensAmountScale: Float,
    val indicatorLensHeightScale: Float,
    val chromaticBoostScale: Float
)

internal data class BottomBarVerticalGlassMotionProfile(
    val progress: Float
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

internal data class BottomBarBackdropNativeSurfaceSpec(
    val blurRadiusDp: Float,
    val refractionHeightDp: Float,
    val refractionAmountDp: Float,
    val surfaceAlphaMultiplier: Float,
    val highlightAlpha: Float,
    val shadowAlpha: Float,
    val chromaticAberration: Boolean
)

internal data class BottomBarItemMotionVisual(
    val themeWeight: Float,
    val scale: Float,
    val useSelectedIcon: Boolean,
    val selectedIconAlpha: Float
)

internal data class BottomBarSettlePulseTransform(
    val scale: Float,
    val translationYDp: Float
)

internal data class BottomBarClickPulseTransform(
    val scaleX: Float,
    val scaleY: Float = 1f
)

internal data class BottomBarIndicatorLayerTransform(
    val scaleX: Float,
    val scaleY: Float
)

private data class BottomBarSettlePulseSnapshot(
    val indicatorPosition: Float,
    val isIndicatorRunning: Boolean,
    val isIndicatorDragging: Boolean
)

internal fun shouldTriggerBottomBarSettlePulse(
    hasObservedInitialSelection: Boolean,
    selectedIndex: Int,
    indicatorPosition: Float,
    isIndicatorRunning: Boolean,
    isIndicatorDragging: Boolean,
    settleTolerance: Float = 0.005f
): Boolean {
    return hasObservedInitialSelection &&
        selectedIndex >= 0 &&
        !isIndicatorRunning &&
        !isIndicatorDragging &&
        abs(indicatorPosition - selectedIndex.toFloat()) <= settleTolerance
}

internal fun resolveBottomBarSettlePulseTransform(
    progress: Float
): BottomBarSettlePulseTransform {
    val clamped = progress.coerceIn(0f, 1f)
    val scale = when {
        clamped <= 0.35f -> lerp(1f, 1.14f, clamped / 0.35f)
        clamped <= 0.65f -> lerp(1.14f, 0.96f, (clamped - 0.35f) / 0.30f)
        else -> lerp(0.96f, 1f, (clamped - 0.65f) / 0.35f)
    }
    val translationYDp = when {
        clamped <= 0.35f -> lerp(0f, -2.5f, clamped / 0.35f)
        clamped <= 0.65f -> lerp(-2.5f, 1.0f, (clamped - 0.35f) / 0.30f)
        else -> lerp(1.0f, 0f, (clamped - 0.65f) / 0.35f)
    }
    return BottomBarSettlePulseTransform(
        scale = scale,
        translationYDp = translationYDp
    )
}

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

internal fun resolveBottomBarVerticalGlassMotionProfile(
    scrollOffsetPx: Float,
    glassEnabled: Boolean,
    scrollRangePx: Float = 180f
): BottomBarVerticalGlassMotionProfile {
    if (!glassEnabled || scrollRangePx <= 0f) {
        return BottomBarVerticalGlassMotionProfile(
            progress = 0f
        )
    }

    val rawProgress = (scrollOffsetPx.coerceAtLeast(0f) / scrollRangePx).coerceIn(0f, 1f)
    val progress = rawProgress * rawProgress * (3f - 2f * rawProgress)
    return BottomBarVerticalGlassMotionProfile(
        progress = progress
    )
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
    return BottomBarBackdropPresetProgress(
        shellProgress = pressProgress.coerceIn(0f, 1f),
        captureProgress = clampedMotion,
        indicatorProgress = clampedMotion
    )
}

internal fun resolveBottomBarIndicatorLayerTransform(
    motionProgress: Float,
    velocityItemsPerSecond: Float,
    motionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec = resolveBottomBarMotionSpec()
): BottomBarIndicatorLayerTransform {
    val clampedProgress = motionProgress.coerceIn(0f, 1f)
    val baseScale = lerp(1f, BOTTOM_BAR_INDICATOR_DRAG_SCALE_TARGET, clampedProgress)
    val velocity = velocityItemsPerSecond / 10f
    val velocityScaleX = (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
    val velocityScaleY = (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
    return BottomBarIndicatorLayerTransform(
        scaleX = baseScale / (1f - velocityScaleX),
        scaleY = baseScale * (1f - velocityScaleY)
    )
}

@Composable
private fun rememberBottomBarSettlePulseKey(
    selectedIndex: Int,
    isValidSelection: Boolean,
    dampedDragState: DampedDragAnimationState
): Int {
    var pulseKey by remember { mutableIntStateOf(0) }
    var hasObservedInitialSelection by remember { mutableStateOf(false) }

    LaunchedEffect(selectedIndex, isValidSelection, dampedDragState) {
        if (!isValidSelection) return@LaunchedEffect
        dampedDragState.updateIndex(selectedIndex)
        if (!hasObservedInitialSelection) {
            hasObservedInitialSelection = true
            return@LaunchedEffect
        }
        snapshotFlow {
            BottomBarSettlePulseSnapshot(
                indicatorPosition = dampedDragState.value,
                isIndicatorRunning = dampedDragState.isRunning,
                isIndicatorDragging = dampedDragState.isDragging
            )
        }.filter { snapshot ->
            shouldTriggerBottomBarSettlePulse(
                hasObservedInitialSelection = hasObservedInitialSelection,
                selectedIndex = selectedIndex,
                indicatorPosition = snapshot.indicatorPosition,
                isIndicatorRunning = snapshot.isIndicatorRunning,
                isIndicatorDragging = snapshot.isIndicatorDragging
            )
        }.first()
        pulseKey += 1
    }

    return pulseKey
}

@Composable
private fun rememberBottomBarClickPulseTransform(
    pulseKey: Int
): BottomBarClickPulseTransform {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 240,
                easing = LinearEasing
            )
        )
    }
    return resolveBottomBarClickPulseTransform(progress.value)
}

@Composable
private fun rememberBottomBarSettlePulseTransform(
    pulseKey: Int
): BottomBarSettlePulseTransform {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pulseKey) {
        if (pulseKey <= 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 240)
        )
        progress.snapTo(0f)
    }
    return resolveBottomBarSettlePulseTransform(progress.value)
}

internal fun resolveBottomBarMotionThemeWeight(
    itemIndex: Int,
    indicatorWeight: Float,
    currentSelectedIndex: Int,
    isIdle: Boolean
): Float {
    val clampedWeight = indicatorWeight.coerceIn(0f, 1f)
    if (!isIdle) return clampedWeight
    return if (itemIndex == currentSelectedIndex) 1f else 0f
}

internal fun resolveBottomBarItemMotionVisual(
    itemIndex: Int,
    indicatorPosition: Float,
    currentSelectedIndex: Int,
    motionProgress: Float,
    selectionEmphasis: Float,
    maxScale: Float = 1.2f
): BottomBarItemMotionVisual {
    val normalizedMotionProgress = motionProgress.coerceIn(0f, 1f)
    val indicatorWeight = (1f - abs(itemIndex.toFloat() - indicatorPosition)).coerceIn(0f, 1f)
    val isIdle = normalizedMotionProgress <= 0f
    val idleSelected = itemIndex == currentSelectedIndex
    val themeWeight = resolveBottomBarMotionThemeWeight(
        itemIndex = itemIndex,
        indicatorWeight = indicatorWeight,
        currentSelectedIndex = currentSelectedIndex,
        isIdle = isIdle
    )
    val scale = if (isIdle) {
        1f
    } else {
        lerp(1f, maxScale, indicatorWeight * normalizedMotionProgress)
    }

    val useSelectedIcon = if (isIdle) {
        idleSelected
    } else {
        indicatorWeight >= 0.5f
    }

    return BottomBarItemMotionVisual(
        themeWeight = themeWeight,
        scale = scale,
        useSelectedIcon = useSelectedIcon,
        selectedIconAlpha = themeWeight
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
            exportCaptureWidthScale = 1f,
            forceChromaticAberration = false,
            indicatorLensAmountScale = 1f,
            indicatorLensHeightScale = 1f,
            chromaticBoostScale = 1f
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
        exportCaptureWidthScale = lerp(1f, 1.16f, progress),
        forceChromaticAberration = progress > 0.02f,
        indicatorLensAmountScale = lerp(1f, 1.34f, progress),
        indicatorLensHeightScale = lerp(1f, 1.18f, progress),
        chromaticBoostScale = lerp(1f, 1.72f, progress)
    )
}

internal fun resolveBottomBarEffectiveRefractionMotionProfile(
    preset: BottomBarLiquidGlassPreset,
    profile: BottomBarRefractionMotionProfile
): BottomBarRefractionMotionProfile {
    return when (preset) {
        BottomBarLiquidGlassPreset.BILIPAI_TUNED -> profile
        BottomBarLiquidGlassPreset.BACKDROP_NATIVE -> profile.copy(
            exportPanelOffsetFraction = profile.exportPanelOffsetFraction * 0.12f,
            indicatorPanelOffsetFraction = profile.indicatorPanelOffsetFraction * 0.16f,
            visiblePanelOffsetFraction = 0f,
            visibleSelectionEmphasis = lerp(1f, profile.visibleSelectionEmphasis, 0.32f),
            exportSelectionEmphasis = lerp(1f, profile.exportSelectionEmphasis, 0.24f),
            exportCaptureWidthScale = 1f,
            forceChromaticAberration = false,
            indicatorLensAmountScale = 1f,
            indicatorLensHeightScale = 1f,
            chromaticBoostScale = 1f
        )
    }
}

internal fun resolveBottomBarBackdropNativeSurfaceSpec(
    blurRadiusDp: Float,
    verticalProgress: Float = 0f
): BottomBarBackdropNativeSurfaceSpec {
    val progress = verticalProgress.coerceIn(0f, 1f)
    val transparentBlurRadiusDp = lerp(
        start = minOf(blurRadiusDp * 0.36f, 7.2f),
        stop = minOf(blurRadiusDp * 0.46f, 8.6f),
        fraction = progress
    )
    return BottomBarBackdropNativeSurfaceSpec(
        blurRadiusDp = transparentBlurRadiusDp,
        refractionHeightDp = lerp(16f, 26f, progress),
        refractionAmountDp = lerp(14f, 22f, progress),
        surfaceAlphaMultiplier = lerp(0.58f, 0.40f, progress),
        highlightAlpha = lerp(0.26f, 0.48f, progress),
        shadowAlpha = lerp(0.14f, 0.22f, progress),
        chromaticAberration = false
    )
}

internal fun resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme: Boolean): Color {
    return if (isDarkTheme) {
        iOSSystemGray6
    } else {
        Color.White
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
            horizontalPadding = 0.dp,
            rowPadding = 20.dp,
            maxBarWidth = containerWidth
        )
    }

    val safeItemCount = itemCount.coerceAtLeast(1)
    val rowPadding = when {
        isTablet && safeItemCount >= 6 -> 16.dp
        isTablet -> 18.dp
        safeItemCount >= 5 -> 12.dp
        else -> 16.dp
    }
    val normalizedLabelMode = when (labelMode) {
        0, 1, 2 -> labelMode
        else -> 0
    }
    val minItemWidth = when (normalizedLabelMode) {
        0 -> if (isTablet) 62.dp else 52.dp
        2 -> if (isTablet) 60.dp else 52.dp
        else -> if (isTablet) 58.dp else 50.dp
    }
    val preferredItemWidth = when (normalizedLabelMode) {
        0 -> if (isTablet) 84.dp else 80.dp
        2 -> if (isTablet) 80.dp else 74.dp
        else -> if (isTablet) 76.dp else 72.dp
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
    val hardCap = if (isTablet) 640.dp else 432.dp
    val minEdgePadding = if (isTablet) 16.dp else 10.dp
    val containerCap = (containerWidth - (minEdgePadding * 2)).coerceAtLeast(0.dp)
    val maxAllowed = minOf(hardCap, visualCap, containerCap)

    val resolvedBarWidth = maxOf(
        minBarWidth,
        minOf(preferredBarWidth, maxAllowed)
    ).coerceAtMost(containerWidth)

    val horizontalPadding = ((containerWidth - resolvedBarWidth) / 2).coerceAtLeast(0.dp)
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
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FrostedBottomBar(
    currentItem: BottomNavItem = BottomNavItem.HOME,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    isFloating: Boolean = true,
    labelMode: Int = 1,
    homeSettings: com.android.purebilibili.core.store.HomeSettings = com.android.purebilibili.core.store.HomeSettings(),
    onHomeDoubleTap: () -> Unit = {},
    onDynamicDoubleTap: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSearchKeywordSubmit: (String) -> Unit = {},
    visibleItems: List<BottomNavItem> = listOf(BottomNavItem.HOME, BottomNavItem.DYNAMIC, BottomNavItem.HISTORY, BottomNavItem.PROFILE),
    itemColorIndices: Map<String, Int> = emptyMap(),
    dynamicUnreadCount: Int = 0,
    onToggleSidebar: (() -> Unit)? = null,
    // [NEW] Scroll offset for liquid glass refraction effect
    scrollOffset: Float = 0f,
    // [NEW] LayerBackdrop for real background refraction (captures content behind the bar)
    backdrop: LayerBackdrop? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false
) {
    if (LocalUiPreset.current == UiPreset.MD3) {
        val androidNativeVariant = LocalAndroidNativeVariant.current
        if (androidNativeVariant == AndroidNativeVariant.MIUIX) {
            MiuixBottomBar(
                currentItem = currentItem,
                onItemClick = onItemClick,
                modifier = modifier,
                visibleItems = visibleItems,
                onToggleSidebar = onToggleSidebar,
                dynamicUnreadCount = dynamicUnreadCount,
                isFloating = isFloating,
                isTablet = com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet,
                labelMode = labelMode,
                blurEnabled = hazeState != null,
                hazeState = hazeState,
                backdrop = backdrop,
                homeSettings = homeSettings,
                onSearchClick = onSearchClick,
                onSearchKeywordSubmit = onSearchKeywordSubmit,
                scrollOffset = scrollOffset,
                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget
            )
        } else {
            MaterialBottomBar(
                currentItem = currentItem,
                onItemClick = onItemClick,
                modifier = modifier,
                visibleItems = visibleItems,
                onToggleSidebar = onToggleSidebar,
                dynamicUnreadCount = dynamicUnreadCount,
                isFloating = isFloating,
                isTablet = com.android.purebilibili.core.util.LocalWindowSizeClass.current.isTablet,
                labelMode = labelMode,
                blurEnabled = hazeState != null,
                hazeState = hazeState,
                backdrop = backdrop,
                homeSettings = homeSettings,
                onSearchClick = onSearchClick,
                onSearchKeywordSubmit = onSearchKeywordSubmit,
                scrollOffset = scrollOffset,
                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget
            )
        }
        return
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.red < 0.5f // Simple darkness check
    val haptic = rememberHapticFeedback()
    val normalizedLabelMode = normalizeBottomBarLabelMode(labelMode)
    val showIcon = shouldShowBottomBarIcon(normalizedLabelMode)
    val showText = shouldShowBottomBarText(normalizedLabelMode)
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    val isTablet = windowSizeClass.isTablet
    if (isFloating) {
        val glassEnabled = homeSettings.isBottomBarLiquidGlassEnabled
        val tuning = resolveAndroidNativeBottomBarTuning(
            blurEnabled = glassEnabled || hazeState != null,
            darkTheme = isSystemInDarkTheme()
        )
        val containerColor = resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = MaterialTheme.colorScheme.surfaceContainer,
            tuning = tuning,
            glassEnabled = glassEnabled,
            blurEnabled = hazeState != null,
            blurIntensity = currentUnifiedBlurIntensity()
        )
        KernelSuAlignedBottomBar(
            currentItem = currentItem,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = visibleItems,
            itemColorIndices = itemColorIndices,
            dynamicUnreadCount = dynamicUnreadCount,
            onToggleSidebar = onToggleSidebar,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            blurEnabled = hazeState != null,
            backdrop = backdrop,
            containerColor = containerColor,
            tuning = tuning,
            glassEnabled = glassEnabled,
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            iconStyle = SharedFloatingBottomBarIconStyle.CUPERTINO,
            haptic = haptic,
            hazeState = hazeState,
            motionTier = motionTier,
            isTransitionRunning = isTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget,
            bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
            bottomBarSearchAutoExpandMode = homeSettings.bottomBarSearchAutoExpandMode,
            onSearchClick = onSearchClick,
            onSearchKeywordSubmit = onSearchKeywordSubmit,
            scrollOffset = scrollOffset
        )
        return
    }

    MaterialBottomBar(
        currentItem = currentItem,
        onItemClick = onItemClick,
        modifier = modifier,
        visibleItems = visibleItems,
        onToggleSidebar = onToggleSidebar,
        dynamicUnreadCount = dynamicUnreadCount,
        isFloating = false,
        isTablet = isTablet,
        labelMode = labelMode,
        blurEnabled = hazeState != null,
        hazeState = hazeState,
        backdrop = backdrop,
        homeSettings = homeSettings,
        onSearchClick = onSearchClick,
        onSearchKeywordSubmit = onSearchKeywordSubmit,
        scrollOffset = scrollOffset,
        motionTier = motionTier,
        isTransitionRunning = isTransitionRunning,
        forceLowBlurBudget = forceLowBlurBudget
    )
}

@Composable
private fun MaterialBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: List<BottomNavItem>,
    onToggleSidebar: (() -> Unit)?,
    dynamicUnreadCount: Int,
    isFloating: Boolean,
    isTablet: Boolean,
    labelMode: Int,
    blurEnabled: Boolean,
    hazeState: HazeState?,
    backdrop: LayerBackdrop?,
    homeSettings: com.android.purebilibili.core.store.HomeSettings,
    onSearchClick: () -> Unit,
    onSearchKeywordSubmit: (String) -> Unit,
    scrollOffset: Float,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean
) {
    val haptic = rememberHapticFeedback()
    val normalizedLabelMode = normalizeBottomBarLabelMode(labelMode)
    val showIcon = shouldShowBottomBarIcon(normalizedLabelMode)
    val showText = shouldShowBottomBarText(normalizedLabelMode)
    val glassEnabled = resolveAndroidNativeBottomBarGlassEnabled(
        liquidGlassEnabled = homeSettings.isBottomBarLiquidGlassEnabled,
        blurEnabled = blurEnabled
    )
    val androidNativeTuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = glassEnabled || blurEnabled,
        darkTheme = isSystemInDarkTheme(),
        androidNativeVariant = LocalAndroidNativeVariant.current
    )
    val blurIntensity = currentUnifiedBlurIntensity()
    val baseSurfaceColor = if (isFloating) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val containerColor = if (isFloating) {
        resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = baseSurfaceColor,
            tuning = androidNativeTuning,
            glassEnabled = glassEnabled,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
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

    if (isFloating) {
        KernelSuAlignedBottomBar(
            currentItem = currentItem,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = visibleItems,
            onToggleSidebar = onToggleSidebar,
            dynamicUnreadCount = dynamicUnreadCount,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            blurEnabled = blurEnabled,
            backdrop = backdrop,
            containerColor = containerColor,
            tuning = androidNativeTuning,
            glassEnabled = glassEnabled,
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            haptic = haptic,
            bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
            bottomBarSearchAutoExpandMode = homeSettings.bottomBarSearchAutoExpandMode,
            onSearchClick = onSearchClick,
            onSearchKeywordSubmit = onSearchKeywordSubmit,
            scrollOffset = scrollOffset
        )
        return
    }

    Surface(
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
        tonalElevation = if (blurEnabled) 0.dp else 3.dp,
        shadowElevation = 0.dp,
        color = containerColor
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
            modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            visibleItems.forEach { item ->
                val itemLabel = resolveBottomNavItemLabel(item)
                val itemContentDescription = resolveBottomNavItemContentDescription(item)
                NavigationBarItem(
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
                                Icon(
                                    imageVector = resolveMaterialBottomBarIcon(item = item, selected = currentItem == item),
                                    contentDescription = itemContentDescription
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(0.dp))
                        }
                    },
                    label = if (showText) {
                        { Text(itemLabel) }
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

            if (isTablet && onToggleSidebar != null) {
                val sidebarLabel = stringResource(R.string.sidebar_toggle)
                NavigationBarItem(
                    selected = false,
                    onClick = {
                        performMaterialBottomBarTap(
                            haptic = haptic,
                            onClick = onToggleSidebar
                        )
                    },
                    icon = {
                        if (showIcon) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuOpen,
                                contentDescription = sidebarLabel
                            )
                        } else {
                            Spacer(modifier = Modifier.size(0.dp))
                        }
                    },
                    label = if (showText) {
                        { Text(sidebarLabel) }
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

@Composable
private fun MiuixBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: List<BottomNavItem>,
    onToggleSidebar: (() -> Unit)?,
    dynamicUnreadCount: Int,
    isFloating: Boolean,
    isTablet: Boolean,
    labelMode: Int,
    blurEnabled: Boolean,
    hazeState: HazeState?,
    backdrop: LayerBackdrop?,
    homeSettings: com.android.purebilibili.core.store.HomeSettings,
    onSearchClick: () -> Unit,
    onSearchKeywordSubmit: (String) -> Unit,
    scrollOffset: Float,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean
) {
    val haptic = rememberHapticFeedback()
    val normalizedLabelMode = normalizeBottomBarLabelMode(labelMode)
    val showIcon = shouldShowBottomBarIcon(normalizedLabelMode)
    val showText = shouldShowBottomBarText(normalizedLabelMode)
    val displayMode = resolveMd3BottomBarDisplayMode(labelMode).toMiuixNavigationDisplayMode()
    val glassEnabled = resolveAndroidNativeBottomBarGlassEnabled(
        liquidGlassEnabled = homeSettings.isBottomBarLiquidGlassEnabled,
        blurEnabled = blurEnabled
    )
    val tuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = glassEnabled || blurEnabled,
        darkTheme = isSystemInDarkTheme(),
        androidNativeVariant = AndroidNativeVariant.MIUIX
    )
    val blurIntensity = currentUnifiedBlurIntensity()
    val baseSurfaceColor = if (isFloating) {
        MiuixTheme.colorScheme.surfaceContainer
    } else {
        MiuixTheme.colorScheme.surface
    }
    val containerColor = if (isFloating) {
        resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = baseSurfaceColor,
            tuning = tuning,
            glassEnabled = glassEnabled,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    } else {
        resolveBottomBarSurfaceColor(
            surfaceColor = baseSurfaceColor,
            blurEnabled = blurEnabled,
            blurIntensity = blurIntensity
        )
    }
    if (isFloating) {
        KernelSuAlignedBottomBar(
            currentItem = currentItem,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = visibleItems,
            onToggleSidebar = onToggleSidebar,
            dynamicUnreadCount = dynamicUnreadCount,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            blurEnabled = blurEnabled,
            backdrop = backdrop,
            containerColor = containerColor,
            tuning = tuning,
            glassEnabled = glassEnabled,
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            iconStyle = SharedFloatingBottomBarIconStyle.CUPERTINO,
            haptic = haptic,
            hazeState = hazeState,
            motionTier = motionTier,
            isTransitionRunning = isTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget,
            bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
            bottomBarSearchAutoExpandMode = homeSettings.bottomBarSearchAutoExpandMode,
            onSearchClick = onSearchClick,
            onSearchKeywordSubmit = onSearchKeywordSubmit,
            scrollOffset = scrollOffset
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

    MiuixNavigationBar(
        modifier = barModifier,
        color = containerColor,
        showDivider = false,
        defaultWindowInsetsPadding = true,
        mode = displayMode
    ) {
        val selectedItemColor = MaterialTheme.colorScheme.primary
        val unselectedItemColor = MaterialTheme.colorScheme.onSurfaceVariant

        visibleItems.forEach { item ->
            val itemLabel = resolveBottomNavItemLabel(item)
            MiuixDockedBottomBarItem(
                selected = currentItem == item,
                onClick = {
                    performMaterialBottomBarTap(
                        haptic = haptic,
                        onClick = { onItemClick(item) }
                    )
                },
                icon = resolveMaterialBottomBarIcon(item, currentItem == item),
                label = itemLabel,
                showIcon = showIcon,
                showText = showText,
                selectedColor = selectedItemColor,
                unselectedColor = unselectedItemColor,
                reminderBadgeText = formatBottomBarDynamicReminderBadge(
                    if (shouldShowBottomBarDynamicReminderBadge(item, dynamicUnreadCount)) {
                        dynamicUnreadCount
                    } else {
                        0
                    }
                )
            )
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
                icon = Icons.AutoMirrored.Outlined.MenuOpen,
                label = sidebarLabel,
                showIcon = showIcon,
                showText = showText,
                selectedColor = selectedItemColor,
                unselectedColor = unselectedItemColor
            )
        }
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
    reminderBadgeText: String? = null
) {
    var isPressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val baseContentColor = resolveMiuixDockedBottomBarItemColor(
        selected = selected,
        selectedColor = selectedColor,
        unselectedColor = unselectedColor
    )
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
            .height(64.dp)
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
                modifier = Modifier.then(if (iconAndText) Modifier.padding(top = 8.dp) else Modifier)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        if (showText) {
            Text(
                text = label,
                color = contentColor,
                textAlign = TextAlign.Center,
                fontSize = if (textOnly) 14.sp else 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                modifier = Modifier.then(
                    if (iconAndText) {
                        Modifier.padding(bottom = 8.dp)
                    } else {
                        Modifier.padding(vertical = 8.dp)
                    }
                )
            )
        }
    }
}

@Composable
private fun KernelSuAlignedBottomBar(
    currentItem: BottomNavItem,
    onItemClick: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
    visibleItems: List<BottomNavItem>,
    itemColorIndices: Map<String, Int> = emptyMap(),
    dynamicUnreadCount: Int = 0,
    onToggleSidebar: (() -> Unit)?,
    isTablet: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    blurEnabled: Boolean,
    backdrop: Backdrop?,
    containerColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    iconStyle: SharedFloatingBottomBarIconStyle = SharedFloatingBottomBarIconStyle.MATERIAL,
    haptic: (HapticType) -> Unit,
    hazeState: HazeState? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    bottomBarSearchEnabled: Boolean = false,
    bottomBarSearchAutoExpandMode: BottomBarSearchAutoExpandMode =
        BottomBarSearchAutoExpandMode.EXPAND_AT_HOME_TOP,
    onSearchClick: () -> Unit = {},
    onSearchKeywordSubmit: (String) -> Unit = {},
    scrollOffset: Float = 0f
) {
    val shellShape = resolveSharedBottomBarCapsuleShape()
    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val bottomBarMotionSpec = remember {
        resolveBottomBarMotionSpec(profile = BottomBarMotionProfile.ANDROID_NATIVE_FLOATING)
    }
    val allItems = remember(visibleItems, isTablet, onToggleSidebar) {
        buildList {
            addAll(visibleItems)
            if (isTablet && onToggleSidebar != null) add(null)
        }
    }
    val selectedIndex = visibleItems.indexOf(currentItem).coerceAtLeast(0)
    val homeIndex = visibleItems.indexOf(BottomNavItem.HOME)
    val isValidSelection = currentItem in visibleItems
    val isDarkTheme = isSystemInDarkTheme()
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurface
    val totalItems = allItems.size.coerceAtLeast(1)
    val dampedDragState = rememberDampedDragAnimationState(
        initialIndex = selectedIndex,
        itemCount = totalItems,
        motionSpec = bottomBarMotionSpec,
        notifyIndexChangedOnReleaseStart = true,
        holdPressUntilReleaseTargetSettles = true,
        onIndexChanged = { index ->
            when {
                index in visibleItems.indices -> onItemClick(visibleItems[index])
                isTablet && onToggleSidebar != null && index == visibleItems.size -> onToggleSidebar()
            }
        }
    )
    val selectedSettlePulseKey = rememberBottomBarSettlePulseKey(
        selectedIndex = selectedIndex,
        isValidSelection = isValidSelection,
        dampedDragState = dampedDragState
    )
    val pressMotionProgress by remember {
        derivedStateOf { dampedDragState.pressProgress }
    }
    val tunedRefractionMotionProfile = resolveBottomBarRefractionMotionProfile(
        position = dampedDragState.value,
        velocity = dampedDragState.velocityPxPerSecond,
        isDragging = dampedDragState.isDragging,
        motionSpec = bottomBarMotionSpec
    )
    val refractionMotionProfile = resolveBottomBarEffectiveRefractionMotionProfile(
        preset = liquidGlassPreset,
        profile = tunedRefractionMotionProfile
    )
    val motionProgress = maxOf(pressMotionProgress, refractionMotionProfile.progress)
    val verticalGlassProfile = resolveBottomBarVerticalGlassMotionProfile(
        scrollOffsetPx = scrollOffset,
        glassEnabled = glassEnabled
    )
    val contentBackdrop = if (backdrop != null) {
        rememberCombinedBackdrop(backdrop, tabsBackdrop)
    } else {
        null
    }
    var searchExpansionOverride by remember {
        mutableStateOf(BottomBarSearchExpansionOverride.FOLLOW_AUTO)
    }
    var searchQuery by remember { mutableStateOf("") }
    var homeClickPulseKey by remember { mutableIntStateOf(0) }
    val homeClickPulseTransform = rememberBottomBarClickPulseTransform(homeClickPulseKey)
    val searchEnabled = resolveBottomBarSearchEnabledForItem(
        currentItem = currentItem,
        bottomBarSearchEnabled = bottomBarSearchEnabled
    )
    val homeScrollOffset = LocalHomeScrollOffset.current
    val shouldAutoExpandSearch by remember(searchEnabled, currentItem, homeScrollOffset.floatValue) {
        derivedStateOf {
            shouldAutoExpandBottomBarSearch(
                currentItem = currentItem,
                bottomBarSearchEnabled = searchEnabled,
                autoExpandMode = bottomBarSearchAutoExpandMode,
                homeScrollOffsetPx = homeScrollOffset.floatValue
            )
        }
    }
    val effectiveSearchExpanded = resolveEffectiveBottomBarSearchExpanded(
        currentItem = currentItem,
        bottomBarSearchEnabled = searchEnabled,
        shouldAutoExpand = shouldAutoExpandSearch,
        expansionOverride = searchExpansionOverride
    )
    LaunchedEffect(currentItem, searchEnabled, shouldAutoExpandSearch, homeScrollOffset.floatValue) {
        val shouldResetSearchOverride = !searchEnabled ||
            currentItem != BottomNavItem.HOME ||
            (currentItem == BottomNavItem.HOME && !shouldAutoExpandSearch && homeScrollOffset.floatValue > 32f)
        if (shouldResetSearchOverride) {
            searchExpansionOverride = BottomBarSearchExpansionOverride.FOLLOW_AUTO
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            val shellHeight = 64.dp
            val contentPadding = 4.dp
            val targetSearchLayout = resolveKernelSuBottomBarSearchLayout(
                containerWidth = maxWidth,
                itemCount = totalItems,
                minEdgePadding = tuning.outerHorizontalPaddingDp.dp,
                searchEnabled = searchEnabled,
                searchExpanded = effectiveSearchExpanded
            )
            val dockWidth by animateDpAsState(
                targetValue = targetSearchLayout.dockWidth,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = AppMotionEasing.Continuity
                ),
                label = "bottomBarDockWidth"
            )
            val searchWidth by animateDpAsState(
                targetValue = targetSearchLayout.searchWidth,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = AppMotionEasing.Continuity
                ),
                label = "bottomBarSearchWidth"
            )
            val searchGap by animateDpAsState(
                targetValue = targetSearchLayout.gap,
                animationSpec = tween(
                    durationMillis = 240,
                    easing = AppMotionEasing.Continuity
                ),
                label = "bottomBarSearchGap"
            )
            val dockHeight by animateDpAsState(
                targetValue = resolveKernelSuBottomBarDockHeight(
                    searchExpanded = effectiveSearchExpanded
                ),
                animationSpec = tween(
                    durationMillis = 220,
                    easing = AppMotionEasing.Continuity
                ),
                label = "bottomBarDockHeight"
            )
            val searchHeight by animateDpAsState(
                targetValue = resolveKernelSuBottomBarSearchHeight(
                    searchExpanded = effectiveSearchExpanded
                ),
                animationSpec = tween(
                    durationMillis = 220,
                    easing = AppMotionEasing.Continuity
                ),
                label = "bottomBarSearchHeight"
            )
            val dockContentAlpha by animateFloatAsState(
                targetValue = if (effectiveSearchExpanded) 0f else 1f,
                animationSpec = tween(
                    durationMillis = 180,
                    easing = AppMotionEasing.Continuity
                ),
                label = "bottomBarDockContentAlpha"
            )
            val compactHomeAlpha by animateFloatAsState(
                targetValue = if (effectiveSearchExpanded) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 180,
                    easing = AppMotionEasing.Continuity
                ),
                label = "bottomBarCompactHomeAlpha"
            )
            val compactHomeIconSize = resolveKernelSuExpandedHomeIconSize()
            val compactHomeIconScale = resolveKernelSuExpandedHomeIconScale()
            val indicatorWidth = (dockWidth - (contentPadding * 2)) / totalItems
            val itemWidthPx = with(density) { indicatorWidth.toPx() }.coerceAtLeast(1f)
            val panelOffsetPx by remember(density, itemWidthPx) {
                derivedStateOf {
                    val fraction = (dampedDragState.dragOffset / itemWidthPx).coerceIn(-1f, 1f)
                    with(density) {
                        bottomBarMotionSpec.refraction.panelOffsetMaxDp.dp.toPx() *
                            fraction.sign *
                            EaseOut.transform(abs(fraction))
                    }
                }
            }
            val backdropPresetProgress = resolveBottomBarBackdropPresetProgress(
                motionProgress = motionProgress,
                verticalProgress = verticalGlassProfile.progress,
                pressProgress = dampedDragState.pressProgress
            )
            val captureLensSpec = resolveBottomBarBackdropPresetCaptureLens(
                progress = backdropPresetProgress.captureProgress
            )
            val indicatorLensSpec = resolveBottomBarBackdropPresetIndicatorLens(
                progress = backdropPresetProgress.indicatorProgress
            )
            val captureHighlightAlpha = resolveBottomBarLiquidGlassHighlightAlpha(
                backdropPresetProgress.captureProgress
            )
            val indicatorHighlightAlpha = resolveBottomBarLiquidGlassHighlightAlpha(
                backdropPresetProgress.indicatorProgress
            )
            fun itemVisual(
                index: Int,
                selectionEmphasis: Float
            ): BottomBarItemMotionVisual = resolveBottomBarItemMotionVisual(
                itemIndex = index,
                indicatorPosition = dampedDragState.value,
                currentSelectedIndex = selectedIndex,
                motionProgress = motionProgress,
                selectionEmphasis = selectionEmphasis
            )

            fun selectedContentColor(item: BottomNavItem?): Color {
                if (item == null) return selectedColor
                val binding = resolveBottomBarItemColorBinding(
                    item = item,
                    itemColorIndices = itemColorIndices
                )
                return resolveBottomBarSelectedContentColor(
                    item = item,
                    binding = binding,
                    themeColor = selectedColor
                )
            }

            fun itemContentColor(
                item: BottomNavItem?,
                visual: BottomBarItemMotionVisual
            ): Color = lerpColor(
                unselectedColor,
                selectedContentColor(item),
                visual.themeWeight
            )

            fun visibleItemContentColor(
                item: BottomNavItem?,
                visual: BottomBarItemMotionVisual
            ): Color = resolveBottomBarGlassVisibleContentColor(
                unselectedColor = unselectedColor,
                selectedColor = selectedContentColor(item),
                themeWeight = visual.themeWeight,
                glassEnabled = glassEnabled,
                indicatorProgress = backdropPresetProgress.indicatorProgress
            )

            fun exportItemContentColor(
                item: BottomNavItem?,
                visual: BottomBarItemMotionVisual
            ): Color = resolveBottomBarGlassExportContentColor(
                unselectedColor = unselectedColor,
                selectedColor = selectedContentColor(item),
                themeWeight = visual.themeWeight,
                glassEnabled = glassEnabled
            )

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
                ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            translationX = panelOffsetPx
                            val progress = backdropPresetProgress.shellProgress
                            if (glassEnabled && size.width > 0f) {
                                val bumpScale = lerp(1f, 1f + 16.dp.toPx() / size.width, progress)
                                scaleX = bumpScale
                                scaleY = bumpScale
                            }
                        }
                        .kernelSuFloatingDockSurface(
                            shape = shellShape,
                            backdrop = backdrop,
                            containerColor = containerColor,
                            blurEnabled = blurEnabled,
                            glassEnabled = glassEnabled,
                            liquidGlassPreset = liquidGlassPreset,
                            nativeVerticalProgress = verticalGlassProfile.progress,
                            blurRadius = tuning.shellBlurRadiusDp.dp,
                            hazeState = hazeState,
                            motionTier = motionTier,
                            isTransitionRunning = isTransitionRunning,
                            forceLowBlurBudget = forceLowBlurBudget
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .alpha(dockContentAlpha)
                        .graphicsLayer { translationX = panelOffsetPx },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visibleItems.forEachIndexed { index, item ->
                        val visual = itemVisual(
                            index = index,
                            selectionEmphasis = refractionMotionProfile.visibleSelectionEmphasis
                        )
                        val contentColor = visibleItemContentColor(item, visual)
                        AndroidNativeBottomBarItem(
                            item = item,
                            label = resolveBottomNavItemLabel(item),
                            dynamicUnreadCount = dynamicUnreadCount,
                            selected = visual.useSelectedIcon,
                            showIcon = showIcon,
                            showText = showText,
                            selectedColor = contentColor,
                            unselectedColor = unselectedColor,
                            contentColorOverride = contentColor,
                            iconStyle = iconStyle,
                            onClick = {},
                            interactive = false,
                            selectedIconAlpha = visual.selectedIconAlpha,
                            scale = if (glassEnabled) visual.scale else 1f,
                            settlePulseKey = if (index == selectedIndex) selectedSettlePulseKey else 0,
                            clickPulseKey = if (item == BottomNavItem.HOME) homeClickPulseKey else 0
                        )
                    }

                    if (isTablet && onToggleSidebar != null) {
                        val visual = itemVisual(
                            index = visibleItems.size,
                            selectionEmphasis = refractionMotionProfile.visibleSelectionEmphasis
                        )
                        val contentColor = visibleItemContentColor(null, visual)
                        AndroidNativeBottomBarItem(
                            item = null,
                            label = stringResource(R.string.sidebar_toggle),
                            dynamicUnreadCount = dynamicUnreadCount,
                            selected = visual.useSelectedIcon,
                            showIcon = showIcon,
                            showText = showText,
                            selectedColor = contentColor,
                            unselectedColor = unselectedColor,
                            contentColorOverride = contentColor,
                            iconStyle = iconStyle,
                            onClick = {},
                            interactive = false,
                            selectedIconAlpha = visual.selectedIconAlpha,
                            scale = if (glassEnabled) visual.scale else 1f
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            translationX = panelOffsetPx
                            scaleX = refractionMotionProfile.exportCaptureWidthScale
                        }
                        .run {
                            if (backdrop != null && glassEnabled) {
                                when (liquidGlassPreset) {
                                    BottomBarLiquidGlassPreset.BACKDROP_NATIVE -> {
                                        val nativeCaptureSpec = resolveBottomBarBackdropNativeSurfaceSpec(
                                            blurRadiusDp = tuning.shellBlurRadiusDp,
                                            verticalProgress = verticalGlassProfile.progress
                                        )
                                        drawBackdrop(
                                            backdrop = backdrop,
                                            shape = { shellShape },
                                            effects = {
                                                vibrancy()
                                                blur(nativeCaptureSpec.blurRadiusDp.dp.toPx())
                                                lens(
                                                    refractionHeight = nativeCaptureSpec.refractionHeightDp.dp.toPx(),
                                                    refractionAmount = nativeCaptureSpec.refractionAmountDp.dp.toPx(),
                                                    depthEffect = true,
                                                    chromaticAberration = nativeCaptureSpec.chromaticAberration
                                                )
                                            },
                                            onDrawSurface = {
                                                drawRect(
                                                    containerColor.copy(
                                                        alpha = containerColor.alpha *
                                                            nativeCaptureSpec.surfaceAlphaMultiplier
                                                    )
                                                )
                                            }
                                        )
                                    }
                                    BottomBarLiquidGlassPreset.BILIPAI_TUNED -> drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { shellShape },
                                        effects = {
                                            vibrancy()
                                            blur(tuning.shellBlurRadiusDp.dp.toPx())
                                            lens(
                                                refractionHeight = captureLensSpec.refractionHeightDp.dp.toPx(),
                                                refractionAmount = captureLensSpec.refractionAmountDp.dp.toPx(),
                                                depthEffect = true,
                                                chromaticAberration = true
                                            )
                                        },
                                        highlight = {
                                            Highlight.Default.copy(alpha = captureHighlightAlpha)
                                        },
                                        onDrawSurface = {
                                            drawRect(containerColor)
                                        }
                                    )
                                }
                            } else {
                                this
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        visibleItems.forEachIndexed { index, item ->
                            val visual = itemVisual(
                                index = index,
                                selectionEmphasis = refractionMotionProfile.exportSelectionEmphasis
                            )
                            val contentColor = exportItemContentColor(item, visual)
                            AndroidNativeBottomBarItem(
                                item = item,
                                label = resolveBottomNavItemLabel(item),
                                dynamicUnreadCount = dynamicUnreadCount,
                                selected = visual.useSelectedIcon,
                                showIcon = showIcon,
                                showText = showText,
                                selectedColor = contentColor,
                                unselectedColor = contentColor,
                                contentColorOverride = contentColor,
                                iconStyle = iconStyle,
                                onClick = {},
                                interactive = false,
                                selectedIconAlpha = visual.selectedIconAlpha,
                                scale = if (glassEnabled) visual.scale else 1f
                            )
                        }

                        if (isTablet && onToggleSidebar != null) {
                            val visual = itemVisual(
                                index = visibleItems.size,
                                selectionEmphasis = refractionMotionProfile.exportSelectionEmphasis
                            )
                            val contentColor = exportItemContentColor(null, visual)
                            AndroidNativeBottomBarItem(
                                item = null,
                                label = stringResource(R.string.sidebar_toggle),
                                dynamicUnreadCount = dynamicUnreadCount,
                                selected = visual.useSelectedIcon,
                                showIcon = showIcon,
                                showText = showText,
                                selectedColor = contentColor,
                                unselectedColor = contentColor,
                                contentColorOverride = contentColor,
                                iconStyle = iconStyle,
                                onClick = {},
                                interactive = false,
                                selectedIconAlpha = visual.selectedIconAlpha,
                                scale = if (glassEnabled) visual.scale else 1f
                            )
                        }
                    }
                }

                if (selectedIndex in visibleItems.indices) {
                    Box(
                        modifier = Modifier
                            .alpha(dockContentAlpha)
                            .offset(x = contentPadding + indicatorWidth * dampedDragState.value)
                            .graphicsLayer {
                                translationX = panelOffsetPx
                                if (selectedIndex == homeIndex) {
                                    scaleX = homeClickPulseTransform.scaleX
                                    scaleY = homeClickPulseTransform.scaleY
                                }
                            }
                            .width(indicatorWidth)
                            .height(56.dp)
                            .align(Alignment.CenterStart)
                            .run {
                                if (
                                    glassEnabled &&
                                    contentBackdrop != null &&
                                    backdropPresetProgress.indicatorProgress > 0f
                                ) {
                                    drawBackdrop(
                                        backdrop = contentBackdrop,
                                        shape = { shellShape },
                                        effects = {
                                            lens(
                                                refractionHeight = indicatorLensSpec.refractionHeightDp.dp.toPx(),
                                                refractionAmount = indicatorLensSpec.refractionAmountDp.dp.toPx(),
                                                depthEffect = true,
                                                chromaticAberration = true
                                            )
                                        },
                                        highlight = {
                                            Highlight.Default.copy(alpha = indicatorHighlightAlpha)
                                        },
                                        shadow = {
                                            Shadow(alpha = if (glassEnabled) indicatorHighlightAlpha else 0f)
                                        },
                                        innerShadow = {
                                            InnerShadow(
                                                radius = 8.dp * backdropPresetProgress.indicatorProgress,
                                                alpha = if (glassEnabled) indicatorHighlightAlpha else 0f
                                            )
                                        },
                                        layerBlock = {
                                            if (glassEnabled) {
                                                val indicatorLayerTransform = resolveBottomBarIndicatorLayerTransform(
                                                    motionProgress = motionProgress,
                                                    velocityItemsPerSecond = dampedDragState.velocity,
                                                    motionSpec = bottomBarMotionSpec
                                                )
                                                scaleX = indicatorLayerTransform.scaleX
                                                scaleY = indicatorLayerTransform.scaleY
                                            }
                                        }
                                    )
                                } else {
                                    background(
                                        resolveAndroidNativeIdleIndicatorSurfaceColor(
                                            darkTheme = isDarkTheme
                                        ),
                                        shellShape
                                    )
                                }
                            }
                    )
                }

                if (!effectiveSearchExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding)
                            .alpha(0f)
                            .graphicsLayer { translationX = panelOffsetPx }
                            .horizontalDragGesture(
                                dragState = dampedDragState,
                                itemWidthPx = itemWidthPx
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    visibleItems.forEachIndexed { index, item ->
                        val visual = itemVisual(
                            index = index,
                            selectionEmphasis = refractionMotionProfile.visibleSelectionEmphasis
                        )
                        val contentColor = visibleItemContentColor(item, visual)
                        AndroidNativeBottomBarItem(
                            item = item,
                            label = resolveBottomNavItemLabel(item),
                            dynamicUnreadCount = dynamicUnreadCount,
                            selected = visual.useSelectedIcon,
                            showIcon = showIcon,
                            showText = showText,
                            selectedColor = contentColor,
                            unselectedColor = unselectedColor,
                            contentColorOverride = contentColor,
                            iconStyle = iconStyle,
                            onClick = {
                                val searchOverride = resolveBottomBarSearchExpansionOverrideOnNavItemClick(
                                    currentItem = currentItem,
                                    clickedItem = item,
                                    bottomBarSearchEnabled = searchEnabled,
                                    effectiveSearchExpanded = effectiveSearchExpanded
                                )
                                if (item == BottomNavItem.HOME) {
                                    homeClickPulseKey += 1
                                }
                                if (searchOverride != null) {
                                    haptic(HapticType.LIGHT)
                                    searchExpansionOverride = searchOverride
                                } else {
                                    dampedDragState.updateIndex(index)
                                    performMaterialBottomBarTap(
                                        haptic = haptic,
                                        onClick = { onItemClick(item) }
                                    )
                                }
                            },
                            interactive = true,
                            onPressChanged = dampedDragState::setPressed,
                            selectedIconAlpha = visual.selectedIconAlpha,
                            scale = if (glassEnabled) visual.scale else 1f
                        )
                    }

                    if (isTablet && onToggleSidebar != null) {
                        val visual = itemVisual(
                            index = visibleItems.size,
                            selectionEmphasis = refractionMotionProfile.visibleSelectionEmphasis
                        )
                        val contentColor = visibleItemContentColor(null, visual)
                        AndroidNativeBottomBarItem(
                            item = null,
                            label = stringResource(R.string.sidebar_toggle),
                            dynamicUnreadCount = dynamicUnreadCount,
                            selected = visual.useSelectedIcon,
                            showIcon = showIcon,
                            showText = showText,
                            selectedColor = contentColor,
                            unselectedColor = unselectedColor,
                            contentColorOverride = contentColor,
                            iconStyle = iconStyle,
                            onClick = {
                                dampedDragState.updateIndex(visibleItems.size)
                                performMaterialBottomBarTap(
                                    haptic = haptic,
                                    onClick = onToggleSidebar
                                )
                            },
                            interactive = true,
                            onPressChanged = dampedDragState::setPressed,
                            selectedIconAlpha = visual.selectedIconAlpha,
                            scale = if (glassEnabled) visual.scale else 1f
                        )
                    }
                    }
                }

                if (searchEnabled) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(compactHomeAlpha)
                            .then(
                                if (effectiveSearchExpanded) {
                                    Modifier.clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        homeClickPulseKey += 1
                                        val searchOverride = resolveBottomBarSearchExpansionOverrideOnNavItemClick(
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
                        Box(
                            modifier = Modifier.graphicsLayer {
                                scaleX = homeClickPulseTransform.scaleX
                                scaleY = homeClickPulseTransform.scaleY
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = CupertinoIcons.Filled.House,
                                contentDescription = null,
                                tint = if (currentItem == BottomNavItem.HOME) selectedColor else unselectedColor,
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

                if (searchEnabled) {
                    Spacer(modifier = Modifier.width(searchGap))
                    KernelSuBottomBarSearchCapsule(
                        width = searchWidth,
                        height = searchHeight,
                        expanded = effectiveSearchExpanded,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onExpandChange = { expanded ->
                            haptic(HapticType.LIGHT)
                            searchExpansionOverride = if (expanded) {
                                BottomBarSearchExpansionOverride.EXPANDED
                            } else {
                                BottomBarSearchExpansionOverride.COLLAPSED
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
                        shape = shellShape,
                        backdrop = backdrop,
                        containerColor = containerColor,
                        blurEnabled = blurEnabled,
                        glassEnabled = glassEnabled,
                        liquidGlassPreset = liquidGlassPreset,
                        nativeVerticalProgress = verticalGlassProfile.progress,
                        blurRadius = tuning.shellBlurRadiusDp.dp,
                        hazeState = hazeState,
                        motionTier = motionTier,
                        isTransitionRunning = isTransitionRunning,
                        forceLowBlurBudget = forceLowBlurBudget,
                        contentColor = unselectedColor,
                        accentColor = selectedColor,
                        haptic = haptic
                    )
                }
        }
    }
}
}

@Composable
private fun KernelSuBottomBarSearchCapsule(
    width: Dp,
    height: Dp,
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onExpandChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    backdrop: Backdrop?,
    containerColor: Color,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    nativeVerticalProgress: Float,
    blurRadius: Dp,
    hazeState: HazeState?,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    contentColor: Color,
    accentColor: Color,
    haptic: (HapticType) -> Unit
) {
    var searchClickPulseKey by remember { mutableIntStateOf(0) }
    var searchLongPressHeld by remember { mutableStateOf(false) }
    val searchClickPulseTransform = rememberBottomBarClickPulseTransform(searchClickPulseKey)
    val longPressHorizontalScale by animateFloatAsState(
        targetValue = if (searchLongPressHeld) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = 560f
        ),
        label = "bottomBarSearchLongPressHorizontalScale"
    )
    LaunchedEffect(searchLongPressHeld) {
        if (!searchLongPressHeld) return@LaunchedEffect
        haptic(HapticType.MEDIUM)
        while (searchLongPressHeld) {
            delay(90L)
            haptic(HapticType.SELECTION)
        }
    }
    val fieldAlpha by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(
            durationMillis = 180,
            easing = AppMotionEasing.Continuity
        ),
        label = "bottomBarSearchFieldAlpha"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (expanded) 0.92f else 1f,
        animationSpec = tween(
            durationMillis = 180,
            easing = AppMotionEasing.Continuity
        ),
        label = "bottomBarSearchIconScale"
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .graphicsLayer {
                scaleX = searchClickPulseTransform.scaleX * longPressHorizontalScale
                scaleY = searchClickPulseTransform.scaleY
            }
            .kernelSuFloatingDockSurface(
                shape = shape,
                backdrop = backdrop,
                containerColor = containerColor,
                blurEnabled = blurEnabled,
                glassEnabled = glassEnabled,
                liquidGlassPreset = liquidGlassPreset,
                nativeVerticalProgress = nativeVerticalProgress,
                blurRadius = blurRadius,
                hazeState = hazeState,
                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget
            )
            .then(
                if (!expanded) {
                    Modifier.pointerInput(onExpandChange) {
                        detectTapGestures(
                            onPress = {
                                try {
                                    awaitRelease()
                                } finally {
                                    if (searchLongPressHeld) {
                                        searchLongPressHeld = false
                                        searchClickPulseKey += 1
                                    }
                                }
                            },
                            onTap = {
                                searchClickPulseKey += 1
                                onExpandChange(true)
                            },
                            onLongPress = {
                                searchLongPressHeld = true
                            }
                        )
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (expanded) 16.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
        ) {
            Icon(
                imageVector = CupertinoIcons.Default.MagnifyingGlass,
                contentDescription = "搜索",
                tint = if (expanded) accentColor else contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .then(
                        if (expanded) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    searchClickPulseKey += 1
                                    onSubmit()
                                }
                            )
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            if (expanded) {
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = contentColor),
                    cursorBrush = SolidColor(accentColor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    modifier = Modifier
                        .weight(1f)
                        .alpha(fieldAlpha),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isBlank()) {
                                Text(
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
            }
        }
    }
}

@Composable
private fun RowScope.AndroidNativeBottomBarItem(
    item: BottomNavItem?,
    label: String,
    dynamicUnreadCount: Int = 0,
    selected: Boolean,
    showIcon: Boolean,
    showText: Boolean,
    selectedColor: Color,
    unselectedColor: Color,
    contentColorOverride: Color? = null,
    iconStyle: SharedFloatingBottomBarIconStyle,
    onClick: () -> Unit,
    interactive: Boolean,
    onPressChanged: (Boolean) -> Unit = {},
    selectedIconAlpha: Float = if (selected) 1f else 0f,
    scale: Float = 1f,
    settlePulseKey: Int = 0,
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
    val density = LocalDensity.current
    val settlePulseTransform = rememberBottomBarSettlePulseTransform(settlePulseKey)
    val clickPulseTransform = rememberBottomBarClickPulseTransform(clickPulseKey)
    val settlePulseTranslationYPx = with(density) {
        settlePulseTransform.translationYDp.dp.toPx()
    }
    val currentOnPressChanged by rememberUpdatedState(onPressChanged)

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
            .weight(1f)
            .defaultMinSize(minWidth = 76.dp)
            .fillMaxHeight()
            .clip(resolveSharedBottomBarCapsuleShape())
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showIcon) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = settlePulseTransform.scale
                        scaleY = settlePulseTransform.scale
                        translationY = settlePulseTranslationYPx
                    },
                    contentAlignment = Alignment.Center
                ) {
                    CompositionLocalProvider(LocalContentColor provides contentColor) {
                        when {
                            item == null && iconStyle == SharedFloatingBottomBarIconStyle.CUPERTINO -> {
                                Icon(
                                    imageVector = CupertinoIcons.Outlined.SidebarLeft,
                                    contentDescription = label
                                )
                            }
                            item == null -> {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.MenuOpen,
                                    contentDescription = label
                                )
                            }
                            iconStyle == SharedFloatingBottomBarIconStyle.CUPERTINO -> {
                                BottomBarBlendedCupertinoIcon(
                                    item = item,
                                    unreadCount = dynamicUnreadCount,
                                    selectedAlpha = selectedIconAlpha,
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
            if (showText) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

private fun resolveMaterialBottomBarIcon(
    item: BottomNavItem,
    selected: Boolean
): ImageVector = when (item) {
    BottomNavItem.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
    BottomNavItem.DYNAMIC -> if (selected) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone
    BottomNavItem.STORY -> if (selected) Icons.Filled.PlayCircle else Icons.Outlined.PlayCircleOutline
    BottomNavItem.HISTORY -> if (selected) Icons.Filled.History else Icons.Outlined.History
    BottomNavItem.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
    BottomNavItem.FAVORITE -> if (selected) Icons.Filled.CollectionsBookmark else Icons.Outlined.CollectionsBookmark
    BottomNavItem.LIVE -> if (selected) Icons.Filled.LiveTv else Icons.Outlined.LiveTv
    BottomNavItem.WATCHLATER -> if (selected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder
    BottomNavItem.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}

@Composable
private fun BottomBarBlendedCupertinoIcon(
    item: BottomNavItem,
    unreadCount: Int = 0,
    selectedAlpha: Float,
    contentColor: Color
) {
    val clampedSelectedAlpha = selectedAlpha.coerceIn(0f, 1f)
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        BottomBarReminderBadgeAnchor(
            item = item,
            unreadCount = unreadCount
        ) {
            Box(
                modifier = Modifier.alpha(1f - clampedSelectedAlpha),
                contentAlignment = Alignment.Center
            ) {
                item.unselectedIcon()
            }
            Box(
                modifier = Modifier.alpha(clampedSelectedAlpha),
                contentAlignment = Alignment.Center
            ) {
                item.selectedIcon()
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
    contentColor: Color
) {
    val clampedSelectedAlpha = selectedAlpha.coerceIn(0f, 1f)
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        BottomBarReminderBadgeAnchor(
            item = item,
            unreadCount = unreadCount
        ) {
            Icon(
                imageVector = resolveMaterialBottomBarIcon(item, selected = false),
                contentDescription = contentDescription,
                modifier = Modifier.alpha(1f - clampedSelectedAlpha)
            )
            Icon(
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
        content = content
    )
}

@Composable
private fun BottomBarReminderBadgeAnchor(
    badgeText: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        content()
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = (-8).dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .background(iOSRed, CircleShape)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
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
        return Color.Black
    }
    val shouldUseDarkForeground = liquidGlassProgress >= 0.62f && contentLuminance > 0.6f
    return if (shouldUseDarkForeground) {
        Color.Black.copy(alpha = 0.82f)
    } else {
        Color.White.copy(
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
