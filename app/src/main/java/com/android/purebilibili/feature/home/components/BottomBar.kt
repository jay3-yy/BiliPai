// 文件路径: feature/home/components/BottomBar.kt
package com.android.purebilibili.feature.home.components

import android.os.Build
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
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
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
import com.android.purebilibili.core.ui.blur.shouldAllowRuntimeShaderBackedHazeEffect
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.core.ui.blur.BlurStyles
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.LocalGlobalWallpaperBackdropVisible
import com.android.purebilibili.core.ui.resolveGlobalWallpaperProtectiveColor
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
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
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
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
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
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.sign
import kotlin.math.sqrt
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode as MiuixNavigationBarDisplayMode
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
import top.yukonga.miuix.kmp.blur.sensor.rememberDeviceTilt
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val iosIndicatorSpecular: MiuixHighlight = MiuixHighlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, -0.3f, -0.05f),
            color = Color.White,
            intensity = 1f
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.8f, -0.5f),
            color = Color.White,
            intensity = 0.4f
        ),
        dualPeak = true
    )
)

private const val MIUIX_LIGHT_REF_X = 0.5f
private const val MIUIX_LIGHT_REF_Y = 0.7f
private const val MIUIX_GRAVITY_DIR_THRESHOLD_SQ = 0.01f

@Composable
private fun rememberGravityRotatedHighlight(
    base: MiuixHighlight,
    extraDegrees: Float = 0f
): MiuixHighlight {
    val baseStyle = base.style as BloomStroke
    val tilt by rememberDeviceTilt()
    val rotatedPrimary = remember(tilt, baseStyle.primaryLight, extraDegrees) {
        val gx = tilt.gravityX
        val gy = tilt.gravityY
        val gMagSq = gx * gx + gy * gy
        val (lx0, ly0) = if (gMagSq > MIUIX_GRAVITY_DIR_THRESHOLD_SQ) {
            val invMag = 1f / sqrt(gMagSq)
            (gx * invMag) to (gy * invMag)
        } else {
            0f to -1f
        }
        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val lx = c * lx0 - s * ly0
        val ly = s * lx0 + c * ly0
        baseStyle.primaryLight.copy(
            position = LightPosition(
                x = MIUIX_LIGHT_REF_X + lx,
                y = MIUIX_LIGHT_REF_Y + ly,
                z = baseStyle.primaryLight.position.z
            )
        )
    }
    return remember(base, baseStyle, rotatedPrimary) {
        base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary))
    }
}

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
        ScreenRoutes.Story.baseRoute
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
        { Icon(CupertinoIcons.Filled.Clock, contentDescription = null) },
        { Icon(CupertinoIcons.Outlined.Clock, contentDescription = null) },
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

internal fun resolveKernelSuBottomBarItemSlotWidth(
    dockWidth: Dp,
    horizontalPadding: Dp,
    itemCount: Int
): Dp {
    val safeItemCount = itemCount.coerceAtLeast(1)
    return ((dockWidth - (horizontalPadding * 2)) / safeItemCount)
        .coerceAtLeast(0.dp)
}

internal fun resolveKernelSuBottomBarItemCenterX(
    itemIndex: Int,
    itemWidth: Dp,
    horizontalPadding: Dp
): Dp {
    val safeIndex = itemIndex.coerceAtLeast(0)
    return horizontalPadding + (itemWidth * safeIndex) + (itemWidth / 2f)
}

internal data class KernelSuBottomBarSearchLayout(
    val dockWidth: Dp,
    val searchWidth: Dp,
    val gap: Dp
)

internal fun resolveKernelSuBottomBarSearchCircleSize(): Dp = 64.dp

internal fun resolveKernelSuExpandedHomeIconSize(): Dp = 28.dp

internal fun resolveKernelSuExpandedHomeIconScale(): Float = 0.92f

internal fun resolveKernelSuBottomBarSearchFieldExpanded(
    searchExpanded: Boolean,
    searchLayoutMode: BottomBarSearchLayoutMode
): Boolean {
    return searchExpanded && searchLayoutMode == BottomBarSearchLayoutMode.HOME_AND_SEARCH
}

internal fun resolveKernelSuBottomBarSearchLayout(
    containerWidth: Dp,
    itemCount: Int,
    minEdgePadding: Dp,
    searchEnabled: Boolean,
    searchExpanded: Boolean,
    searchLayoutMode: BottomBarSearchLayoutMode = BottomBarSearchLayoutMode.FULL_DOCK
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
    val minimumDockWidth = searchCircleSize
    val collapsedSearchWidth = searchCircleSize
    val compactHomeDockSize = searchCircleSize
    val expandedSearchWidth = minOf(
        280.dp,
        (availableWidth - compactHomeDockSize - gap).coerceAtLeast(176.dp)
    )
    val useCompactLayout = searchLayoutMode == BottomBarSearchLayoutMode.HOME_AND_SEARCH
    val targetSearchWidth = if (useCompactLayout && searchExpanded) {
        expandedSearchWidth
    } else {
        collapsedSearchWidth
    }
    val targetDockWidth = if (useCompactLayout && searchExpanded) {
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

internal fun resolveKernelSuBottomBarDockHeight(
    searchExpanded: Boolean,
    hasUiSkinDecoration: Boolean = false
): Dp {
    return if (searchExpanded) {
        resolveKernelSuBottomBarSearchCircleSize()
    } else if (hasUiSkinDecoration) {
        resolveBottomBarSkinDockHeight()
    } else {
        64.dp
    }
}

internal fun resolveKernelSuBottomBarSearchHeight(searchExpanded: Boolean): Dp {
    return 64.dp
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

private data class KernelSuBottomBarSearchLayoutState(
    val dockWidth: Dp,
    val dockHeight: Dp,
    val searchWidth: Dp,
    val searchHeight: Dp,
    val searchGap: Dp,
    val launchAdjustedSearchGap: Dp,
    val shellHeight: Dp
)

@Composable
private fun rememberKernelSuBottomBarSearchLayoutState(
    containerWidth: Dp,
    itemCount: Int,
    minEdgePadding: Dp,
    searchEnabled: Boolean,
    searchExpanded: Boolean,
    searchLayoutMode: BottomBarSearchLayoutMode,
    hasUiSkinDecoration: Boolean
): KernelSuBottomBarSearchLayoutState {
    val targetSearchLayout = resolveKernelSuBottomBarSearchLayout(
        containerWidth = containerWidth,
        itemCount = itemCount,
        minEdgePadding = minEdgePadding,
        searchEnabled = searchEnabled,
        searchExpanded = searchExpanded,
        searchLayoutMode = searchLayoutMode
    )
    if (!searchEnabled) {
        val dockWidth by animateDpAsState(
            targetValue = targetSearchLayout.dockWidth,
            animationSpec = tween(
                durationMillis = 260,
                easing = AppMotionEasing.Continuity
            ),
            label = "bottomBarDockWidth"
        )
        val dockHeight by animateDpAsState(
            targetValue = resolveKernelSuBottomBarDockHeight(
                searchExpanded = false,
                hasUiSkinDecoration = hasUiSkinDecoration
            ),
            animationSpec = tween(
                durationMillis = 220,
                easing = AppMotionEasing.Continuity
            ),
            label = "bottomBarDockHeight"
        )
        return KernelSuBottomBarSearchLayoutState(
            dockWidth = dockWidth,
            dockHeight = dockHeight,
            searchWidth = 0.dp,
            searchHeight = 0.dp,
            searchGap = 0.dp,
            launchAdjustedSearchGap = 0.dp,
            shellHeight = dockHeight
        )
    }

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
            searchExpanded = searchExpanded,
            hasUiSkinDecoration = hasUiSkinDecoration
        ),
        animationSpec = tween(
            durationMillis = 220,
            easing = AppMotionEasing.Continuity
        ),
        label = "bottomBarDockHeight"
    )
    val searchHeight by animateDpAsState(
        targetValue = resolveKernelSuBottomBarSearchHeight(
            searchExpanded = searchExpanded
        ),
        animationSpec = tween(
            durationMillis = 220,
            easing = AppMotionEasing.Continuity
        ),
        label = "bottomBarSearchHeight"
    )
    val shellHeight = if (dockHeight > searchHeight) dockHeight else searchHeight
    return KernelSuBottomBarSearchLayoutState(
        dockWidth = dockWidth,
        dockHeight = dockHeight,
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

internal fun resolveBottomBarVisibleItemsForSearchMode(
    visibleItems: List<BottomNavItem>,
    bottomBarSearchEnabled: Boolean,
    searchLayoutMode: BottomBarSearchLayoutMode = BottomBarSearchLayoutMode.FULL_DOCK
): List<BottomNavItem> {
    if (!bottomBarSearchEnabled) return visibleItems
    return if (searchLayoutMode == BottomBarSearchLayoutMode.HOME_AND_SEARCH) {
        listOf(BottomNavItem.HOME)
    } else {
        visibleItems
    }
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

internal fun shouldRenderBottomBarForegroundAboveIndicator(
    preset: BottomBarLiquidGlassPreset
): Boolean {
    return when (preset) {
        BottomBarLiquidGlassPreset.BILIPAI_TUNED,
        BottomBarLiquidGlassPreset.IOS26_REFINED -> false
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
    androidNativeVariant: AndroidNativeVariant = AndroidNativeVariant.MATERIAL3
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
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED
): Color {
    return resolveBottomBarGlassMaterialContainerColor(
        surfaceColor = surfaceColor,
        preset = liquidGlassPreset,
        glassEnabled = glassEnabled,
        fallbackAlpha = tuning.shellSurfaceAlpha
    )
}

internal fun resolveAndroidNativeFloatingBottomBarContainerColor(
    surfaceColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    blurEnabled: Boolean,
    blurIntensity: com.android.purebilibili.core.ui.blur.BlurIntensity,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    globalWallpaperVisible: Boolean = false
): Color {
    val resolvedColor = if (glassEnabled) {
        resolveAndroidNativeBottomBarContainerColor(
            surfaceColor = surfaceColor,
            tuning = tuning,
            glassEnabled = true,
            liquidGlassPreset = liquidGlassPreset
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
    blurEnabled: Boolean
): Boolean = liquidGlassEnabled

internal fun resolveBottomBarIndicatorEffectsEnabled(
    liquidGlassEnabled: Boolean,
    blurEnabled: Boolean
): Boolean = liquidGlassEnabled || blurEnabled

internal fun shouldUseBottomBarCaptureLens(
    liquidGlassEnabled: Boolean
): Boolean = liquidGlassEnabled

internal fun resolveKernelSuBottomBarShellColor(
    containerColor: Color,
    liquidGlassEnabled: Boolean,
    darkTheme: Boolean
): Color {
    return if (liquidGlassEnabled) {
        resolveKernelSuBottomBarContainerColor(darkTheme = darkTheme)
    } else {
        containerColor
    }
}

internal fun shouldBlurKernelSuBottomBarShell(
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
    shouldAllowRuntimeShaderBackedHazeEffect(sdkInt)

internal fun Modifier.kernelSuFloatingDockSurface(
    shape: androidx.compose.ui.graphics.Shape,
    backdrop: Backdrop?,
    containerColor: Color,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    drawShellLens: Boolean = true,
    blurRadius: Dp,
    hazeState: HazeState?,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    isScrolling: Boolean = false,
    materialScrollProgress: Float = if (isScrolling) 1f else 0f,
    materialMotionProgress: Float = 0f,
    materialPressProgress: Float = 0f
): Modifier = composed {
    val isDarkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.chromeBackground())
    val materialSpec: BottomBarGlassMaterialSpec = resolveBottomBarGlassMaterialSpec(
        preset = liquidGlassPreset,
        isDarkTheme = isDarkTheme,
        isScrolling = isScrolling,
        scrollProgress = materialScrollProgress,
        glassEnabled = glassEnabled,
        motionProgress = materialMotionProgress,
        pressProgress = materialPressProgress
    )
    val useHazeBlur = shouldUseAndroidNativeFloatingHazeBlur(
        blurEnabled = blurEnabled,
        glassEnabled = glassEnabled,
        hasHazeState = hazeState != null
    )
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
                val innerRimGlowProvider: (() -> InnerShadow)? = remember(
                    materialSpec.innerRimGlow,
                    isDarkTheme,
                    glassEnabled
                ) {
                    val innerRimGlow = materialSpec.innerRimGlow
                    if (glassEnabled && innerRimGlow != null) {
                        val cached = InnerShadow(
                            radius = innerRimGlow.radiusDp.dp,
                            alpha = innerRimGlow.alpha,
                            color = if (isDarkTheme) Color.White else Color.Black
                        )
                        val provider: () -> InnerShadow = { cached }
                        provider
                    } else {
                        null
                    }
                }
                drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        if (glassEnabled || (blurEnabled && !useHazeBlur)) {
                            if (materialSpec.vibrancy) {
                                vibrancy()
                            }
                            blur((materialSpec.blurRadiusDp?.dp ?: blurRadius).toPx())
                            if (
                                glassEnabled &&
                                drawShellLens &&
                                materialSpec.shellRefractionHeightDp > 0f &&
                                materialSpec.shellRefractionAmountDp > 0f
                            ) {
                                lens(
                                    refractionHeight = materialSpec.shellRefractionHeightDp.dp.toPx(),
                                    refractionAmount = materialSpec.shellRefractionAmountDp.dp.toPx()
                                )
                            }
                            val shellShader = materialSpec.shellShader
                            if (glassEnabled && drawShellLens && shellShader != null) {
                                val cornerPx = (size.height.coerceAtMost(size.width)) / 2f
                                val u = resolveLiquidGlassShaderUniforms(
                                    widthPx = size.width,
                                    heightPx = size.height,
                                    paddingPx = padding,
                                    cornerRadiusPx = cornerPx,
                                    thicknessPx = shellShader.thicknessDp.dp.toPx(),
                                    refractIndex = shellShader.refractIndex,
                                    refractIntensity = shellShader.refractIntensity,
                                    intensityScale = 1f
                                )
                                runtimeShaderEffect(
                                    key = LIQUID_GLASS_SHADER_KEY,
                                    shaderString = LIQUID_GLASS_SHADER,
                                    uniformShaderName = "img"
                                ) {
                                    setFloatUniform("resolution", u.resolutionX, u.resolutionY)
                                    setFloatUniform("center", u.centerX, u.centerY)
                                    setFloatUniform("size", u.halfWidth, u.halfHeight)
                                    setFloatUniform(
                                        "radius",
                                        u.cornerRadiusPx, u.cornerRadiusPx,
                                        u.cornerRadiusPx, u.cornerRadiusPx
                                    )
                                    setFloatUniform("thickness", u.thicknessPx)
                                    setFloatUniform("refract_index", u.refractIndex)
                                    setFloatUniform("refract_intensity", u.refractIntensity)
                                    setFloatUniform(
                                        "foreground_color_premultiplied",
                                        0f, 0f, 0f, 0f
                                    )
                                }
                            }
                        }
                    },
                    highlight = {
                        Highlight(
                            width = 1.dp,
                            alpha = if (glassEnabled) 0.75f else 0f
                        )
                    },
                    shadow = {
                        Shadow.Default.copy(
                            color = Color.Black.copy(
                                alpha = if (isDarkTheme) 0.2f else 0.1f
                            )
                        )
                    },
                    innerShadow = innerRimGlowProvider,
                    layerBlock = if (glassEnabled) {
                        {
                            val width = size.width.coerceAtLeast(1f)
                            val s = lerp(1f, 1f + 16.dp.toPx() / width, materialPressProgress)
                            scaleX = s
                            scaleY = s
                        }
                    } else null,
                    onDrawSurface = {
                        drawRect(containerColor)
                        if (materialSpec.foregroundTint.alpha > 0f) {
                            drawRect(materialSpec.foregroundTint)
                        }
                    }
                )
            } else {
                background(containerColor, shape)
            }
        }
        .clip(shape)
}

@Composable
internal fun Modifier.kernelSuMiuixFloatingDockSurface(
    shape: androidx.compose.ui.graphics.Shape,
    backdrop: MiuixBackdrop?,
    containerColor: Color,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    drawShellLens: Boolean = true,
    blurRadius: Dp,
    hazeState: HazeState?,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    isScrolling: Boolean = false,
    materialScrollProgress: Float = 0f,
    materialMotionProgress: Float = 0f,
    materialPressProgress: Float = 0f
): Modifier = composed {
    val isDarkTheme = resolveBottomBarDarkTheme(MiuixTheme.colorScheme.background)
    val useHazeBlur = shouldUseAndroidNativeFloatingHazeBlur(
        glassEnabled = glassEnabled,
        blurEnabled = blurEnabled,
        hasHazeState = hazeState != null
    )
    val baseHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = -45f)

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
                this
                    .dropShadow(
                        shape = shape,
                        shadow = ComposeShadow(
                            radius = 10.dp,
                            color = Color.Black,
                            alpha = if (isDarkTheme) 0.2f else 0.1f
                        )
                    )
                    .miuixDrawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            if (glassEnabled) {
                                miuixVibrancy()
                                miuixBlur(4.dp.toPx(), 4.dp.toPx())
                                if (drawShellLens) {
                                    miuixLens(
                                        refractionHeight = 24.dp.toPx(),
                                        refractionAmount = 24.dp.toPx()
                                    )
                                }
                            } else if (blurEnabled && !useHazeBlur) {
                                val radiusPx = blurRadius.toPx()
                                miuixBlur(radiusPx, radiusPx)
                            }
                        },
                        highlight = {
                            baseHighlight.copy(alpha = if (glassEnabled) 0.75f else 0f)
                        },
                        layerBlock = if (glassEnabled) {
                            {
                                val width = size.width.coerceAtLeast(1f)
                                val s = lerp(1f, 1f + 16.dp.toPx() / width, materialPressProgress)
                                scaleX = s
                                scaleY = s
                            }
                        } else null,
                        onDrawSurface = {
                            drawRect(containerColor)
                        }
                    )
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
    indicatorProgress: Float,
    indicatorBackdropEnabled: Boolean = true
): Color {
    if (glassEnabled && indicatorBackdropEnabled && indicatorProgress > 0.001f) {
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
    if (glassEnabled) {
        return unselectedColor
    }
    return lerpColor(
        start = unselectedColor,
        stop = selectedColor,
        fraction = clampedWeight
    )
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
    val readableBackgroundIsLight = skinTrimTint?.luminance()?.let { it >= 0.45f } == true
    val labelScrimColor = when {
        skinTrimTint == null -> Color.Transparent
        readableBackgroundIsLight -> Color.White
        else -> Color.Black
    }
    val labelScrimAlpha = 0f
    return BottomBarSkinContentColors(
        selectedColor = selectedColor,
        unselectedColor = unselectedColor,
        labelScrimColor = labelScrimColor,
        labelScrimAlpha = labelScrimAlpha
    )
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
        .padding(horizontal = 4.dp, vertical = 1.dp)
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

internal fun resolveKernelSuBottomBarContainerColor(darkTheme: Boolean): Color {
    val surfaceContainer = if (darkTheme) {
        Color(36, 36, 36)
    } else {
        Color.White
    }
    return surfaceContainer.copy(alpha = 0.4f)
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

private fun Md3BottomBarDisplayMode.toMiuixNavigationDisplayMode(): MiuixNavigationBarDisplayMode {
    return when (this) {
        Md3BottomBarDisplayMode.IconAndText -> MiuixNavigationBarDisplayMode.IconAndText
        Md3BottomBarDisplayMode.IconOnly -> MiuixNavigationBarDisplayMode.IconOnly
        Md3BottomBarDisplayMode.TextOnly -> MiuixNavigationBarDisplayMode.TextOnly
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
private const val KSU_INDICATOR_VELOCITY_NORMALIZATION_DIVISOR = 10f
private const val KSU_INDICATOR_VELOCITY_SCALE_X_MULTIPLIER = 0.75f
private const val KSU_INDICATOR_VELOCITY_SCALE_Y_MULTIPLIER = 0.25f
private const val KSU_INDICATOR_VELOCITY_CLAMP = 0.2f

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

internal fun resolveBottomBarShellHighlightAlpha(
    glassEnabled: Boolean,
    pressProgress: Float,
    motionProgress: Float = 0f,
    isDragging: Boolean = false
): Float {
    if (!glassEnabled) return 0f
    // 拖拽中保留一道地板:慢拖时 motion/press 都低,但高光应持续锚定在指示器上,
    // 让它「跟手」而非中途变暗掉队。高 motion 时不被地板压低。
    val dragFloor = if (isDragging) 0.6f else 0f
    return maxOf(pressProgress, motionProgress, dragFloor).coerceIn(0f, 1f)
}

internal fun resolveBottomBarInteractiveHighlightCenterX(
    indicatorTranslationXPx: Float,
    itemWidthPx: Float,
    panelOffsetPx: Float
): Float {
    return indicatorTranslationXPx + itemWidthPx * 0.5f + panelOffsetPx
}

private fun Modifier.bottomBarInteractiveHighlight(
    enabled: Boolean,
    alpha: Float,
    centerXPx: Float
): Modifier = composed {
    // 不直接引用平台着色器类：低版本系统在 Compose materialize modifier 时也可能解析类并崩溃。
    drawWithContent {
        val clampedAlpha = alpha.coerceIn(0f, 1f)
        drawContent()
        if (!enabled || clampedAlpha <= 0f) return@drawWithContent
        val center = Offset(
            x = centerXPx.coerceIn(0f, size.width),
            y = size.height * 0.5f
        )
        drawRect(
            color = Color.White.copy(alpha = 0.06f * clampedAlpha),
            blendMode = BlendMode.Plus
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.17f * clampedAlpha),
                    Color.Transparent
                ),
                center = center,
                radius = size.minDimension * 1.2f
            ),
            blendMode = BlendMode.Plus
        )
    }
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
    motionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec = resolveBottomBarMotionSpec()
): BottomBarIndicatorLayerTransform {
    val clampedProgress = motionProgress.coerceIn(0f, 1f)
    val clampedDragScaleProgress = dragScaleProgress.coerceIn(0f, 1f)
    val baseScale = lerp(
        start = 1f,
        stop = BOTTOM_BAR_INDICATOR_DRAG_SCALE_TARGET,
        fraction = clampedDragScaleProgress
    )
    val baseScaleX = dragScaleTransform?.scaleX ?: baseScale
    val baseScaleY = dragScaleTransform?.scaleY ?: baseScale
    // 对齐 KernelSU FloatingBottomBar 的胶囊速度形变和基础按压放大倍数。
    val velocity = if (isDragging || clampedDragScaleProgress > 0f) {
        velocityItemsPerSecond / KSU_INDICATOR_VELOCITY_NORMALIZATION_DIVISOR
    } else {
        0f
    }
    val velocityScaleX = (velocity * KSU_INDICATOR_VELOCITY_SCALE_X_MULTIPLIER)
        .coerceIn(-KSU_INDICATOR_VELOCITY_CLAMP, KSU_INDICATOR_VELOCITY_CLAMP)
    val velocityScaleY = (velocity * KSU_INDICATOR_VELOCITY_SCALE_Y_MULTIPLIER)
        .coerceIn(-KSU_INDICATOR_VELOCITY_CLAMP, KSU_INDICATOR_VELOCITY_CLAMP)
    return BottomBarIndicatorLayerTransform(
        scaleX = baseScaleX / (1f - velocityScaleX),
        scaleY = baseScaleY * (1f - velocityScaleY)
    )
}

@Composable
internal fun rememberBottomBarIndicatorDragScaleProgress(
    isDragging: Boolean
): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(isDragging) {
        progress.animateTo(
            targetValue = if (isDragging) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (isDragging) 90 else 220,
                easing = if (isDragging) EaseOut else FastOutSlowInEasing
            )
        )
    }
    return progress.value
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
            animationSpec = tween(
                durationMillis = 240,
                easing = LinearEasing
            )
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
            animationSpec = tween(
                durationMillis = 240,
                easing = FastOutSlowInEasing
            )
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
            animationSpec = tween(
                durationMillis = 260,
                easing = LinearEasing
            )
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
    searchLaunchKey: Int = 0,
    onSearchLaunchTransitionFinished: (Int) -> Unit = {},
    visibleItems: List<BottomNavItem> = listOf(BottomNavItem.HOME, BottomNavItem.DYNAMIC, BottomNavItem.HISTORY, BottomNavItem.PROFILE),
    itemColorIndices: Map<String, Int> = emptyMap(),
    dynamicUnreadCount: Int = 0,
    onToggleSidebar: (() -> Unit)? = null,
    // [NEW] LayerBackdrop for real background refraction (captures content behind the bar)
    backdrop: LayerBackdrop? = null,
    miuixBackdrop: MiuixLayerBackdrop? = null,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    isFeedScrollInProgress: Boolean = false,
    uiSkinDecoration: BottomBarUiSkinDecoration? = null
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
                miuixBackdrop = miuixBackdrop,
                homeSettings = homeSettings,
                onSearchClick = onSearchClick,
                onSearchKeywordSubmit = onSearchKeywordSubmit,
                searchLaunchKey = searchLaunchKey,
                onSearchLaunchTransitionFinished = onSearchLaunchTransitionFinished,                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget,
                isFeedScrollInProgress = isFeedScrollInProgress,
                uiSkinDecoration = uiSkinDecoration
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
                miuixBackdrop = miuixBackdrop,
                homeSettings = homeSettings,
                onSearchClick = onSearchClick,
                onSearchKeywordSubmit = onSearchKeywordSubmit,                motionTier = motionTier,
                isTransitionRunning = isTransitionRunning,
                forceLowBlurBudget = forceLowBlurBudget,
                isFeedScrollInProgress = isFeedScrollInProgress,
                uiSkinDecoration = uiSkinDecoration
            )
        }
        return
    }

    val isDarkTheme = AppSurfaceTokens.chromeBackground().red < 0.5f // Simple darkness check
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
    val windowSizeClass = com.android.purebilibili.core.util.LocalWindowSizeClass.current
    val isTablet = windowSizeClass.isTablet
    val globalWallpaperVisible = LocalGlobalWallpaperBackdropVisible.current
    if (isFloating) {
        val glassEnabled = homeSettings.isBottomBarLiquidGlassEnabled
        val tuning = resolveAndroidNativeBottomBarTuning(
            blurEnabled = glassEnabled || hazeState != null,
            darkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.chromeBackground())
        )
        val containerColor = resolveAndroidNativeFloatingBottomBarContainerColor(
            surfaceColor = MaterialTheme.colorScheme.surfaceContainer,
            tuning = tuning,
            glassEnabled = glassEnabled,
            blurEnabled = hazeState != null,
            blurIntensity = currentUnifiedBlurIntensity(),
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            globalWallpaperVisible = globalWallpaperVisible
        )
        KernelSuAlignedBottomBar(
            currentItem = currentItem,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = bottomBarVisibleItems,
            itemColorIndices = itemColorIndices,
            dynamicUnreadCount = dynamicUnreadCount,
            onToggleSidebar = onToggleSidebar,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            blurEnabled = hazeState != null,
            backdrop = backdrop,
            miuixBackdrop = miuixBackdrop,
            containerColor = containerColor,
            tuning = tuning,
            glassEnabled = glassEnabled,
            interactiveHighlightEnabled = homeSettings.bottomBarInteractiveHighlightEnabled,
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            iconStyle = SharedFloatingBottomBarIconStyle.CUPERTINO,
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
            onSearchLaunchTransitionFinished = onSearchLaunchTransitionFinished,            isFeedScrollInProgress = isFeedScrollInProgress,
            uiSkinDecoration = uiSkinDecoration
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
        miuixBackdrop = miuixBackdrop,
        homeSettings = homeSettings,
        onSearchClick = onSearchClick,
        onSearchKeywordSubmit = onSearchKeywordSubmit,
        motionTier = motionTier,
        isTransitionRunning = isTransitionRunning,
        forceLowBlurBudget = forceLowBlurBudget,
        uiSkinDecoration = uiSkinDecoration
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
    miuixBackdrop: MiuixLayerBackdrop?,
    homeSettings: com.android.purebilibili.core.store.HomeSettings,
    onSearchClick: () -> Unit,
    onSearchKeywordSubmit: (String) -> Unit,
    searchLaunchKey: Int = 0,
    onSearchLaunchTransitionFinished: (Int) -> Unit = {},    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    isFeedScrollInProgress: Boolean = false,
    uiSkinDecoration: BottomBarUiSkinDecoration? = null
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
        liquidGlassEnabled = homeSettings.isBottomBarLiquidGlassEnabled,
        blurEnabled = blurEnabled
    )
    val androidNativeTuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = glassEnabled || blurEnabled,
        darkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.chromeBackground()),
        androidNativeVariant = LocalAndroidNativeVariant.current
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
        selectedColor = dockedItemColors.selectedIconColor,
        unselectedColor = dockedItemColors.unselectedIconColor,
        skinTrimTint = uiSkinDecoration?.bottomTrimTint
    )

    if (isFloating) {
        KernelSuAlignedBottomBar(
            currentItem = currentItem,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = bottomBarVisibleItems,
            onToggleSidebar = onToggleSidebar,
            dynamicUnreadCount = dynamicUnreadCount,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            blurEnabled = blurEnabled,
            backdrop = backdrop,
            miuixBackdrop = miuixBackdrop,
            containerColor = containerColor,
            tuning = androidNativeTuning,
            glassEnabled = glassEnabled,
            interactiveHighlightEnabled = homeSettings.bottomBarInteractiveHighlightEnabled,
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            haptic = haptic,
            bottomBarSearchEnabled = homeSettings.isBottomBarSearchEnabled,
            bottomBarSearchAutoExpandMode = homeSettings.bottomBarSearchAutoExpandMode,
            bottomBarSearchLayoutMode = homeSettings.bottomBarSearchLayoutMode,
            onSearchClick = onSearchClick,
            onSearchKeywordSubmit = onSearchKeywordSubmit,
            searchLaunchKey = searchLaunchKey,
            onSearchLaunchTransitionFinished = onSearchLaunchTransitionFinished,            isFeedScrollInProgress = isFeedScrollInProgress,
            uiSkinDecoration = uiSkinDecoration
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
        DockedBottomBarSkinContainer(
            decoration = uiSkinDecoration
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                bottomBarVisibleItems.forEach { item ->
                    val itemLabel = resolveBottomNavItemLabel(item)
                    val itemContentDescription = resolveBottomNavItemContentDescription(item)
                    val skinIconPath = uiSkinDecoration?.iconPathFor(item, selected = currentItem == item)
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
                                    if (skinIconPath != null) {
                                        BottomBarSkinIcon(
                                            iconPath = skinIconPath,
                                            contentDescription = itemContentDescription
                                        )
                                    } else {
                                        Icon(
                                            imageVector = resolveMaterialBottomBarIcon(item = item, selected = currentItem == item),
                                            contentDescription = itemContentDescription
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.size(0.dp))
                            }
                        },
                        label = if (showText) {
                            {
                                Text(
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
                            indicatorColor = dockedItemColors.indicatorColor,
                            unselectedIconColor = skinDockedItemColors.unselectedColor,
                            unselectedTextColor = skinDockedItemColors.unselectedColor
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
                            {
                                Text(
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
    miuixBackdrop: MiuixLayerBackdrop?,
    homeSettings: com.android.purebilibili.core.store.HomeSettings,
    onSearchClick: () -> Unit,
    onSearchKeywordSubmit: (String) -> Unit,
    searchLaunchKey: Int = 0,
    onSearchLaunchTransitionFinished: (Int) -> Unit = {},    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    isFeedScrollInProgress: Boolean = false,
    uiSkinDecoration: BottomBarUiSkinDecoration? = null
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
    val displayMode = resolveMd3BottomBarDisplayMode(labelMode).toMiuixNavigationDisplayMode()
    val glassEnabled = resolveAndroidNativeBottomBarGlassEnabled(
        liquidGlassEnabled = homeSettings.isBottomBarLiquidGlassEnabled,
        blurEnabled = blurEnabled
    )
    val tuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = glassEnabled || blurEnabled,
        darkTheme = resolveBottomBarDarkTheme(MiuixTheme.colorScheme.background),
        androidNativeVariant = AndroidNativeVariant.MIUIX
    )
    val blurIntensity = currentUnifiedBlurIntensity()
    val baseSurfaceColor = if (isFloating) {
        MiuixTheme.colorScheme.surfaceContainer
    } else {
        MiuixTheme.colorScheme.surface
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
        KernelSuAlignedBottomBar(
            currentItem = currentItem,
            onItemClick = onItemClick,
            modifier = modifier,
            visibleItems = bottomBarVisibleItems,
            onToggleSidebar = onToggleSidebar,
            dynamicUnreadCount = dynamicUnreadCount,
            isTablet = isTablet,
            showIcon = showIcon,
            showText = showText,
            blurEnabled = blurEnabled,
            backdrop = backdrop,
            miuixBackdrop = miuixBackdrop,
            containerColor = containerColor,
            tuning = tuning,
            glassEnabled = glassEnabled,
            interactiveHighlightEnabled = homeSettings.bottomBarInteractiveHighlightEnabled,
            liquidGlassPreset = homeSettings.bottomBarLiquidGlassPreset,
            iconStyle = SharedFloatingBottomBarIconStyle.CUPERTINO,
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
            onSearchLaunchTransitionFinished = onSearchLaunchTransitionFinished,            isFeedScrollInProgress = isFeedScrollInProgress,
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
        MiuixNavigationBar(
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
            val skinItemColors = resolveBottomBarSkinContentColors(
                selectedColor = selectedItemColor,
                unselectedColor = unselectedItemColor,
                skinTrimTint = uiSkinDecoration?.bottomTrimTint
            )

            bottomBarVisibleItems.forEach { item ->
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
                    selectedColor = skinItemColors.selectedColor,
                    unselectedColor = skinItemColors.unselectedColor,
                    labelScrimColor = skinItemColors.labelScrimColor,
                    labelScrimAlpha = skinItemColors.labelScrimAlpha,
                    skinIconPath = uiSkinDecoration?.iconPathFor(item, selected = currentItem == item),
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
                modifier = Modifier.then(if (iconAndText) Modifier.padding(top = 8.dp) else Modifier)
            ) {
                if (skinIconPath != null) {
                    BottomBarSkinIcon(
                        iconPath = skinIconPath,
                        contentDescription = label,
                        size = resolveBottomBarMiuixSkinDockIconSize()
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = contentColor,
                        modifier = Modifier.size(26.dp)
                    )
                }
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
                ).bottomBarSkinLabelScrim(
                    color = labelScrimColor,
                    alpha = labelScrimAlpha
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
    miuixBackdrop: MiuixBackdrop?,
    containerColor: Color,
    tuning: AndroidNativeBottomBarTuning,
    glassEnabled: Boolean,
    interactiveHighlightEnabled: Boolean,
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
    bottomBarSearchLayoutMode: BottomBarSearchLayoutMode =
        BottomBarSearchLayoutMode.FULL_DOCK,
    onSearchClick: () -> Unit = {},
    onSearchKeywordSubmit: (String) -> Unit = {},
    searchLaunchKey: Int = 0,
    onSearchLaunchTransitionFinished: (Int) -> Unit = {},
    isFeedScrollInProgress: Boolean = false,
    uiSkinDecoration: BottomBarUiSkinDecoration? = null
) {
    val shellShape = resolveSharedBottomBarCapsuleShape()
    val tabsBackdrop = rememberMiuixLayerBackdrop()
    val isDarkTheme = resolveBottomBarDarkTheme(MiuixTheme.colorScheme.background)
    val ksuContainerColor = resolveKernelSuBottomBarShellColor(
        containerColor = containerColor,
        liquidGlassEnabled = glassEnabled,
        darkTheme = isDarkTheme
    )
    val shellBlurEnabled = shouldBlurKernelSuBottomBarShell(
        blurEnabled = blurEnabled
    )
    val indicatorEffectsEnabled = resolveBottomBarIndicatorEffectsEnabled(
        liquidGlassEnabled = glassEnabled,
        blurEnabled = blurEnabled
    )
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
    val isValidSelection = currentItem in visibleItems
    val baseSelectedColor = MaterialTheme.colorScheme.primary
    val baseUnselectedColor = MaterialTheme.colorScheme.onSurface
    val skinContentColors = resolveBottomBarSkinContentColors(
        selectedColor = baseSelectedColor,
        unselectedColor = baseUnselectedColor,
        skinTrimTint = uiSkinDecoration?.bottomTrimTint
    )
    val selectedColor = skinContentColors.selectedColor
    val unselectedColor = skinContentColors.unselectedColor
    val exportTintColor = resolveAndroidNativeExportTintColor(
        themeColor = selectedColor,
        darkTheme = isDarkTheme
    )
    val totalItems = allItems.size.coerceAtLeast(1)
    val dampedDragState = rememberDampedDragAnimationState(
        initialIndex = selectedIndex,
        itemCount = totalItems,
        motionSpec = bottomBarMotionSpec,
        notifyIndexChangedOnReleaseStart = false,
        holdPressUntilReleaseTargetSettles = true,
        onIndexChanged = { index ->
            when {
                index in visibleItems.indices -> onItemClick(visibleItems[index])
                isTablet && onToggleSidebar != null && index == visibleItems.size -> onToggleSidebar()
            }
        }
    )
    LaunchedEffect(selectedIndex, isValidSelection, dampedDragState) {
        if (isValidSelection) {
            dampedDragState.updateIndex(selectedIndex)
        }
    }
    // [KSU 对齐] 交互高光锚点:点击切换时高光直接在被点项绽放(对齐 KSU 按下即
    // snap 到触点),不随选中弹簧动画沿途滑动。拖拽时改为跟随实时指示器。
    var highlightAnchorIndex by remember { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        highlightAnchorIndex = selectedIndex
    }
    val pressMotionProgress by remember {
        derivedStateOf { dampedDragState.pressProgress }
    }
    val visualIndicatorPosition by remember(totalItems, dampedDragState) {
        derivedStateOf {
            resolveBottomBarVisualIndicatorPosition(
                rawPosition = dampedDragState.value,
                itemCount = totalItems
            )
        }
    }
    val edgeStrain by remember(totalItems, dampedDragState) {
        derivedStateOf {
            resolveBottomBarEdgeStrain(
                rawPosition = dampedDragState.value,
                itemCount = totalItems
            )
        }
    }
    val edgeCompressionScaleX by remember {
        derivedStateOf {
            resolveBottomBarEdgeCompressionScaleX(edgeStrain)
        }
    }
    val tunedRefractionMotionProfile = resolveBottomBarRefractionMotionProfile(
        position = visualIndicatorPosition,
        velocity = dampedDragState.velocityPxPerSecond,
        isDragging = dampedDragState.isDragging,
        motionSpec = bottomBarMotionSpec
    )
    val refractionMotionProfile = resolveBottomBarEffectiveRefractionMotionProfile(
        preset = liquidGlassPreset,
        profile = tunedRefractionMotionProfile
    )
    val effectivePressProgress = dampedDragState.pressProgress
    val motionProgress = maxOf(effectivePressProgress, refractionMotionProfile.progress)
    val indicatorDragScaleProgress = rememberBottomBarIndicatorDragScaleProgress(
        isDragging = dampedDragState.isDragging
    )
    val indicatorLayerScaleProgress = maxOf(indicatorDragScaleProgress, effectivePressProgress)
    val materialScrollProgress by animateFloatAsState(
        targetValue = if (isFeedScrollInProgress) 1f else 0f,
        animationSpec = tween(
            durationMillis = resolveBottomBarMaterialScrollAnimationDurationMillis(
                isScrolling = isFeedScrollInProgress
            ),
            easing = AppMotionEasing.Continuity
        ),
        label = "bottomBarMaterialScrollProgress"
    )
    var searchExpansionOverride by remember {
        mutableStateOf(BottomBarSearchExpansionOverride.FOLLOW_AUTO)
    }
    var searchQuery by remember { mutableStateOf("") }
    val searchLaunchMorphSpec = remember { resolveBottomBarSearchLaunchMorphSpec() }
    val searchEnabled = resolveBottomBarSearchEnabledForItem(
        currentItem = currentItem,
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
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.BottomCenter
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
        ) {
            val dockHorizontalPadding = 4.dp
            val dockContentPadding = if (uiSkinDecoration != null) {
                resolveBottomBarSkinDockContentPadding()
            } else {
                PaddingValues(dockHorizontalPadding)
            }
            val searchLayoutState = rememberKernelSuBottomBarSearchLayoutState(
                containerWidth = maxWidth,
                itemCount = totalItems,
                minEdgePadding = tuning.outerHorizontalPaddingDp.dp,
                searchEnabled = searchEnabled,
                searchExpanded = effectiveSearchExpanded,
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
            val visualSearchExpanded = resolveKernelSuBottomBarSearchFieldExpanded(
                searchExpanded = effectiveSearchExpanded,
                searchLayoutMode = bottomBarSearchLayoutMode
            )
            val dockContentAlpha by animateFloatAsState(
                targetValue = if (compactSearchLayout && effectiveSearchExpanded) 0f else 1f,
                animationSpec = tween(
                    durationMillis = 180,
                    easing = AppMotionEasing.Continuity
                ),
                label = "bottomBarDockContentAlpha"
            )
            val compactHomeAlpha by animateFloatAsState(
                targetValue = if (compactSearchLayout && effectiveSearchExpanded) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 180,
                    easing = AppMotionEasing.Continuity
                ),
                label = "bottomBarCompactHomeAlpha"
            )
            val shouldComposeDockContent = shouldComposeBottomBarDockContent(
                dockContentAlpha = dockContentAlpha,
                effectiveSearchExpanded = effectiveSearchExpanded
            )
            val compactHomeIconSize = resolveKernelSuExpandedHomeIconSize()
            val compactHomeIconScale = resolveKernelSuExpandedHomeIconScale()
            val indicatorWidth = resolveKernelSuBottomBarItemSlotWidth(
                dockWidth = dockWidth,
                horizontalPadding = dockHorizontalPadding,
                itemCount = totalItems
            )
            val itemWidthPx = with(density) { indicatorWidth.toPx() }.coerceAtLeast(1f)
            val dockWidthPx = with(density) { dockWidth.toPx() }.coerceAtLeast(1f)
            val panelOffsetPx by remember(density, dockWidthPx) {
                derivedStateOf {
                    val fraction = (dampedDragState.dragOffset / dockWidthPx).coerceIn(-1f, 1f)
                    with(density) {
                        4.dp.toPx() *
                            fraction.sign *
                            EaseOut.transform(abs(fraction))
                    }
                }
            }
            val indicatorTranslationXPx by remember(
                density,
                dockHorizontalPadding,
                indicatorWidth,
                visualIndicatorPosition
            ) {
                derivedStateOf {
                    with(density) {
                        (dockHorizontalPadding + indicatorWidth * visualIndicatorPosition).toPx()
                    }
                }
            }
            // [KSU 对齐] 高光位移:拖拽时跟随实时指示器,否则锚定到被点项,
            // 使点击切换时高光在目标项直接绽放,而非沿选中弹簧动画滑过。
            val highlightTranslationXPx by remember(
                density,
                dockHorizontalPadding,
                indicatorWidth,
                totalItems
            ) {
                derivedStateOf {
                    val position = if (dampedDragState.isDragging) {
                        visualIndicatorPosition
                    } else {
                        highlightAnchorIndex.toFloat()
                            .coerceIn(0f, (totalItems - 1).coerceAtLeast(0).toFloat())
                    }
                    with(density) {
                        (dockHorizontalPadding + indicatorWidth * position).toPx()
                    }
                }
            }
            val presetPanelOffsets = remember(liquidGlassPreset, panelOffsetPx) {
                resolveBottomBarPresetPanelOffsets(
                    preset = liquidGlassPreset,
                    rawPanelOffsetPx = panelOffsetPx
                )
            }
            val interactiveHighlightCenterXPx by remember(
                highlightTranslationXPx,
                itemWidthPx,
                presetPanelOffsets.indicatorPanelOffsetPx
            ) {
                derivedStateOf {
                    resolveBottomBarInteractiveHighlightCenterX(
                        indicatorTranslationXPx = highlightTranslationXPx,
                        itemWidthPx = itemWidthPx,
                        panelOffsetPx = presetPanelOffsets.indicatorPanelOffsetPx
                    )
                }
            }
            val foregroundAboveIndicator = shouldRenderBottomBarForegroundAboveIndicator(liquidGlassPreset)
            val backdropPresetProgress = resolveBottomBarEffectiveBackdropPresetProgress(
                preset = liquidGlassPreset,
                motionProgress = motionProgress,
                pressProgress = effectivePressProgress
            )
            val effectiveCaptureProgress = backdropPresetProgress.captureProgress
            val effectiveIndicatorProgress = backdropPresetProgress.indicatorProgress
            val isBottomBarPressActive =
                effectivePressProgress > BottomBarTransientAlphaThreshold
            val effectiveIndicatorEffectProgress = maxOf(
                effectiveIndicatorProgress,
                effectivePressProgress
            )
            val indicatorLensSpec = resolveBottomBarBackdropPresetIndicatorLens(
                progress = effectivePressProgress
            )
            val indicatorIdleSurfaceColor = resolveBottomBarIdleIndicatorSurfaceColor(
                preset = liquidGlassPreset,
                darkTheme = isDarkTheme
            )
            val shellHighlightAlpha = resolveBottomBarShellHighlightAlpha(
                glassEnabled = glassEnabled,
                pressProgress = effectivePressProgress,
                motionProgress = effectiveIndicatorEffectProgress,
                isDragging = dampedDragState.isDragging
            )
            val isBottomBarInteractionActive = dampedDragState.isDragging ||
                dampedDragState.isRunning ||
                dampedDragState.pressProgress > BottomBarTransientAlphaThreshold
            val shouldRenderRefractionCaptureRaw = shouldRenderBottomBarRefractionCapture(
                glassEnabled = glassEnabled,
                hasBackdrop = miuixBackdrop != null,
                captureProgress = effectiveCaptureProgress,
                isTransitionRunning = isTransitionRunning,
                isFeedScrollInProgress = isFeedScrollInProgress,
                isBottomBarInteractionActive = isBottomBarInteractionActive
            )
            val shouldRenderIndicatorBackdropRaw = shouldRenderBottomBarIndicatorBackdrop(
                glassEnabled = glassEnabled,
                hasContentBackdrop = miuixBackdrop != null,
                indicatorProgress = effectiveIndicatorEffectProgress,
                isTransitionRunning = isTransitionRunning,
                isBottomBarInteractionActive = isBottomBarInteractionActive,
                allowIdleGlassEffect = false,
                allowTransitionIndicatorPulse = isBottomBarPressActive
            )
            // [KSU 对齐] 玻璃开启时指示器/捕获层常驻。
            // KSU 的 tabsBackdrop 与移动指示器始终 composed、始终 drawBackdrop,
            // 没有 "idle 纯色背景 ↔ 玻璃 drawBackdrop" 的分支切换;BiliPai 原本按
            // 交互状态增删这些层,切换瞬间 tabsBackdrop 为空,指示器会直接采样到
            // 原始内容(首页视频画面)。常驻后 tabsBackdrop 始终有录制内容,
            // 由 progress 连续驱动 effects/surface,彻底消除该瞬态。
            val glassLayersAlwaysOn = glassEnabled && miuixBackdrop != null
            val shouldRenderRefractionCapture =
                glassLayersAlwaysOn || shouldRenderRefractionCaptureRaw
            val shouldRenderIndicatorBackdrop =
                glassLayersAlwaysOn || shouldRenderIndicatorBackdropRaw
            val shouldRenderIndicatorContentCapture =
                shouldComposeDockContent &&
                    (shouldRenderRefractionCapture || isBottomBarPressActive)
            val contentBackdrop = if (
                shouldRenderIndicatorBackdrop && miuixBackdrop != null
            ) {
                rememberMiuixCombinedBackdrop(miuixBackdrop, tabsBackdrop)
            } else {
                null
            }
            fun itemCoverage(index: Int): Float = resolveBottomBarItemCoverage(
                itemIndex = index,
                indicatorPosition = visualIndicatorPosition,
                currentSelectedIndex = selectedIndex,
                motionProgress = motionProgress
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

            fun visibleItemContentColor(
                item: BottomNavItem?,
                coverage: Float
            ): Color {
                val itemSelectedColor = selectedContentColor(item)
                return resolveBottomBarGlassVisibleContentColor(
                    unselectedColor = unselectedColor,
                    selectedColor = itemSelectedColor,
                    themeWeight = coverage,
                    glassEnabled = glassEnabled,
                    indicatorProgress = effectiveIndicatorEffectProgress,
                    indicatorBackdropEnabled = shouldRenderIndicatorBackdrop
                )
            }

            fun exportItemContentColor(
                item: BottomNavItem?,
                coverage: Float
            ): Color {
                if (isBottomBarPressActive && item != null) {
                    return selectedContentColor(item)
                }
                return resolveBottomBarGlassExportContentColor(
                    unselectedColor = unselectedColor,
                    selectedColor = selectedContentColor(item),
                    themeWeight = coverage,
                    glassEnabled = glassEnabled
                )
            }

            fun sampledItemScale(): Float = if (glassEnabled) {
                resolveBottomBarItemMotionScale(
                    coverage = 1f,
                    motionProgress = effectivePressProgress
                )
            } else {
                1f
            }

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
                    highlightAnchorIndex = index
                    dampedDragState.updateIndex(index)
                    performMaterialBottomBarTap(
                        haptic = haptic,
                        onClick = { onItemClick(item) }
                    )
                }
            }

            fun handleBottomBarSidebarClick() {
                if (onToggleSidebar != null) {
                    highlightAnchorIndex = visibleItems.size
                    dampedDragState.updateIndex(visibleItems.size)
                    performMaterialBottomBarTap(
                        haptic = haptic,
                        onClick = onToggleSidebar
                    )
                }
            }

            Row(
                modifier = Modifier
                    .height(shellHeight)
                    .align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KernelSuBottomBarShell(
                    modifier = Modifier
                        .width(dockWidth)
                        .height(dockHeight),
                    shellShape = shellShape,
                    miuixBackdrop = miuixBackdrop,
                    containerColor = ksuContainerColor,
                    blurEnabled = shellBlurEnabled,
                    glassEnabled = glassEnabled,
                    blurRadius = tuning.shellBlurRadiusDp.dp,
                    hazeState = hazeState,
                    motionTier = motionTier,
                    isTransitionRunning = isTransitionRunning,
                    forceLowBlurBudget = forceLowBlurBudget,
                    edgeCompressionScaleX = edgeCompressionScaleX,
                    shellProgress = backdropPresetProgress.shellProgress,
                    visiblePanelOffsetPx = presetPanelOffsets.visiblePanelOffsetPx,
                    interactiveHighlightEnabled = interactiveHighlightEnabled,
                    shellHighlightAlpha = shellHighlightAlpha,
                    interactiveHighlightCenterXPx = interactiveHighlightCenterXPx,
                    uiSkinDecoration = uiSkinDecoration,
                    liquidGlassPreset = liquidGlassPreset,
                    isScrolling = isFeedScrollInProgress,
                    materialScrollProgress = materialScrollProgress,
                    materialMotionProgress = motionProgress,
                    materialPressProgress = effectivePressProgress
                ) {

                if (shouldComposeDockContent) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(dockContentPadding)
                            .alpha(dockContentAlpha)
                            .zIndex(if (foregroundAboveIndicator) 1f else 0f)
                            .graphicsLayer { translationX = presetPanelOffsets.visiblePanelOffsetPx },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        visibleItems.forEachIndexed { index, item ->
                            val coverage = itemCoverage(index)
                            val contentColor = visibleItemContentColor(item, coverage)
                            AndroidNativeBottomBarItem(
                                item = item,
                                itemWidth = indicatorWidth,
                                label = resolveBottomNavItemLabel(item),
                                dynamicUnreadCount = dynamicUnreadCount,
                                selected = coverage >= 0.5f,
                                showIcon = showIcon,
                                showText = showText,
                                selectedColor = contentColor,
                                unselectedColor = unselectedColor,
                                contentColorOverride = contentColor,
                                iconStyle = iconStyle,
                                skinIconPath = uiSkinDecoration?.iconPathFor(item, selected = coverage >= 0.5f),
                                labelScrimColor = skinContentColors.labelScrimColor,
                                labelScrimAlpha = skinContentColors.labelScrimAlpha,
                                onClick = {},
                                interactive = false,
                                onPressChanged = dampedDragState::setPressed,
                                selectedIconAlpha = coverage,
                                scale = 1f
                            )
                        }

                        if (isTablet && onToggleSidebar != null) {
                            val coverage = itemCoverage(visibleItems.size)
                            val contentColor = visibleItemContentColor(null, coverage)
                            AndroidNativeBottomBarItem(
                                item = null,
                                itemWidth = indicatorWidth,
                                label = stringResource(R.string.sidebar_toggle),
                                dynamicUnreadCount = dynamicUnreadCount,
                                selected = coverage >= 0.5f,
                                showIcon = showIcon,
                                showText = showText,
                                selectedColor = contentColor,
                                unselectedColor = unselectedColor,
                                contentColorOverride = contentColor,
                                iconStyle = iconStyle,
                                labelScrimColor = skinContentColors.labelScrimColor,
                                labelScrimAlpha = skinContentColors.labelScrimAlpha,
                                onClick = {},
                                interactive = false,
                                onPressChanged = dampedDragState::setPressed,
                                selectedIconAlpha = coverage,
                                scale = 1f
                            )
                        }
                    }
                }

                if (shouldRenderIndicatorContentCapture && miuixBackdrop != null) {
                    Box(
                        modifier = Modifier
                            .width(dockWidth)
                            .height(56.dp)
                            .align(Alignment.CenterStart)
                            .clearAndSetSemantics {}
                            .alpha(0f)
                            .miuixLayerBackdrop(tabsBackdrop)
                            .graphicsLayer {
                                translationX = presetPanelOffsets.exportPanelOffsetPx
                            }
                            .miuixDrawBackdrop(
                                backdrop = miuixBackdrop,
                                shape = { shellShape },
                                effects = {
                                    if (shouldUseBottomBarCaptureLens(glassEnabled)) {
                                        miuixVibrancy()
                                    }
                                    miuixBlur(4.dp.toPx(), 4.dp.toPx())
                                    if (shouldUseBottomBarCaptureLens(glassEnabled)) {
                                        miuixLens(
                                            refractionHeight = 24.dp.toPx(),
                                            refractionAmount = 24.dp.toPx()
                                        )
                                    }
                                },
                                onDrawSurface = {
                                    drawRect(ksuContainerColor)
                                }
                            )
                    ) {
                        // 图标和文字统一染色，角标单独采样以保留红色。
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = dockHorizontalPadding)
                                .graphicsLayer(colorFilter = ColorFilter.tint(exportTintColor)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            visibleItems.forEachIndexed { index, item ->
                                val coverage = itemCoverage(index)
                                val contentColor = exportItemContentColor(item, coverage)
                                AndroidNativeBottomBarItem(
                                    item = item,
                                    itemWidth = indicatorWidth,
                                    label = resolveBottomNavItemLabel(item),
                                    dynamicUnreadCount = 0,
                                    selected = true,
                                    showIcon = showIcon,
                                    showText = showText,
                                    selectedColor = contentColor,
                                    unselectedColor = contentColor,
                                    contentColorOverride = contentColor,
                                    iconStyle = iconStyle,
                                    skinIconPath = uiSkinDecoration?.iconPathFor(item, selected = true),
                                    labelScrimColor = skinContentColors.labelScrimColor,
                                    labelScrimAlpha = skinContentColors.labelScrimAlpha,
                                    onClick = {},
                                    interactive = false,
                                    selectedIconAlpha = 1f,
                                    scale = sampledItemScale()
                                )
                            }

                            if (isTablet && onToggleSidebar != null) {
                                val coverage = itemCoverage(visibleItems.size)
                                val contentColor = exportItemContentColor(null, coverage)
                                AndroidNativeBottomBarItem(
                                    item = null,
                                    itemWidth = indicatorWidth,
                                    label = stringResource(R.string.sidebar_toggle),
                                    dynamicUnreadCount = 0,
                                    selected = true,
                                    showIcon = showIcon,
                                    showText = showText,
                                    selectedColor = contentColor,
                                    unselectedColor = contentColor,
                                    contentColorOverride = contentColor,
                                    iconStyle = iconStyle,
                                    labelScrimColor = skinContentColors.labelScrimColor,
                                    labelScrimAlpha = skinContentColors.labelScrimAlpha,
                                    onClick = {},
                                    interactive = false,
                                    selectedIconAlpha = 1f,
                                    scale = sampledItemScale()
                                )
                            }
                        }

                        if (showIcon && dynamicUnreadCount > 0) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = dockHorizontalPadding),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                visibleItems.forEach { item ->
                                    Box(
                                        modifier = Modifier
                                            .width(indicatorWidth)
                                            .fillMaxHeight(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (item == BottomNavItem.DYNAMIC) {
                                            BottomBarReminderBadgeAnchor(
                                                badgeText = formatBottomBarDynamicReminderBadge(
                                                    dynamicUnreadCount
                                                ),
                                                modifier = Modifier.offset(
                                                    y = if (showText) (-7).dp else 0.dp
                                                )
                                            ) {
                                                Spacer(modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }
                                }

                                if (isTablet && onToggleSidebar != null) {
                                    Spacer(
                                        modifier = Modifier
                                            .width(indicatorWidth)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                }

                KernelSuMiuixBottomBarIndicatorLayer(
                    visible = selectedIndex in visibleItems.indices,
                    dockContentAlpha = dockContentAlpha,
                    indicatorTranslationXPx = indicatorTranslationXPx,
                    indicatorPanelOffsetPx = presetPanelOffsets.indicatorPanelOffsetPx,
                    indicatorWidth = indicatorWidth,
                    shellShape = shellShape,
                    liquidGlassPreset = liquidGlassPreset,
                    contentBackdrop = contentBackdrop,
                    backdrop = miuixBackdrop,
                    indicatorLensSpec = indicatorLensSpec,
                    effectivePressProgress = effectivePressProgress,
                    indicatorIdleSurfaceColor = indicatorIdleSurfaceColor,
                    glassEnabled = glassEnabled,
                    indicatorEffectsEnabled = indicatorEffectsEnabled,
                    motionProgress = motionProgress,
                    velocityItemsPerSecond = dampedDragState.deformationVelocityItemsPerSecond,
                    isDragging = dampedDragState.isDragging,
                    indicatorLayerScaleProgress = indicatorLayerScaleProgress,
                    indicatorLayerScaleTransform = null,
                    bottomBarMotionSpec = bottomBarMotionSpec,
                    isDarkTheme = isDarkTheme
                )

                KernelSuBottomBarInputLayer(
                    visible = !(compactSearchLayout && effectiveSearchExpanded),
                    visibleItems = visibleItems,
                    isTablet = isTablet,
                    hasSidebarToggle = onToggleSidebar != null,
                    dockContentPadding = dockContentPadding,
                    dockWidth = dockWidth,
                    dockHeight = dockHeight,
                    visiblePanelOffsetPx = presetPanelOffsets.visiblePanelOffsetPx,
                    dampedDragState = dampedDragState,
                    itemWidthPx = itemWidthPx,
                    itemWidth = indicatorWidth,
                    onItemClick = ::handleBottomBarItemClick,
                    onSidebarClick = ::handleBottomBarSidebarClick
                )

                if (searchEnabled && compactSearchLayout && compactHomeAlpha > BottomBarTransientAlphaThreshold) {
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
                                    size = resolveBottomBarCompactSkinHomeIconSize(),
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = compactHomeIconScale
                                        scaleY = compactHomeIconScale
                                    }
                                )
                            } else {
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
            }

                KernelSuBottomBarSearchSlot(
                    visible = searchEnabled,
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
                    shape = shellShape,
                    miuixBackdrop = miuixBackdrop,
                    containerColor = ksuContainerColor,
                    blurEnabled = blurEnabled,
                    glassEnabled = glassEnabled,
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
                    materialScrollProgress = materialScrollProgress,
                    materialMotionProgress = 0f,
                    materialPressProgress = 0f
                )
        }
    }
}
}

@Composable
private fun KernelSuBottomBarShell(
    modifier: Modifier,
    shellShape: androidx.compose.ui.graphics.Shape,
    miuixBackdrop: MiuixBackdrop?,
    containerColor: Color,
    blurEnabled: Boolean,
    glassEnabled: Boolean,
    blurRadius: Dp,
    hazeState: HazeState?,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    edgeCompressionScaleX: Float,
    shellProgress: Float,
    visiblePanelOffsetPx: Float,
    interactiveHighlightEnabled: Boolean,
    shellHighlightAlpha: Float,
    interactiveHighlightCenterXPx: Float,
    uiSkinDecoration: BottomBarUiSkinDecoration?,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    isScrolling: Boolean,
    materialScrollProgress: Float,
    materialMotionProgress: Float,
    materialPressProgress: Float,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    translationX = visiblePanelOffsetPx
                    val bumpScale = if (glassEnabled && size.width > 0f) {
                        lerp(1f, 1f + 16.dp.toPx() / size.width, shellProgress)
                    } else {
                        1f
                    }
                    scaleX = edgeCompressionScaleX * bumpScale
                    scaleY = bumpScale
                }
                .kernelSuMiuixFloatingDockSurface(
                    shape = shellShape,
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
                    materialPressProgress = materialPressProgress
                )
                .bottomBarInteractiveHighlight(
                    enabled = glassEnabled && interactiveHighlightEnabled,
                    alpha = shellHighlightAlpha,
                    centerXPx = interactiveHighlightCenterXPx
                )
        )

        BottomBarSkinDecorativeTrim(
            decoration = uiSkinDecoration,
            modifier = Modifier.matchParentSize(),
            clipShape = shellShape
        )

        content()
    }
}

@Composable
private fun BoxScope.KernelSuMiuixBottomBarIndicatorLayer(
    visible: Boolean,
    dockContentAlpha: Float,
    indicatorTranslationXPx: Float,
    indicatorTranslationYPx: Float = 0f,
    indicatorPanelOffsetPx: Float,
    indicatorPanelOffsetYPx: Float = 0f,
    indicatorWidth: Dp,
    indicatorHeight: Dp = 56.dp,
    shellShape: androidx.compose.ui.graphics.Shape,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    contentBackdrop: MiuixBackdrop?,
    backdrop: MiuixBackdrop?,
    indicatorLensSpec: BottomBarBackdropPresetLensSpec,
    effectivePressProgress: Float,
    indicatorIdleSurfaceColor: Color,
    glassEnabled: Boolean,
    indicatorEffectsEnabled: Boolean = glassEnabled,
    motionProgress: Float,
    velocityItemsPerSecond: Float,
    isDragging: Boolean,
    indicatorLayerScaleProgress: Float,
    indicatorLayerScaleTransform: BottomBarIndicatorLayerTransform? = null,
    bottomBarMotionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec,
    isDarkTheme: Boolean,
    swapMotionAxes: Boolean = false,
    indicatorAlignment: Alignment = Alignment.CenterStart
) {
    if (!visible) return
    val rawIndicatorLayerTransform = if (indicatorEffectsEnabled) {
        resolveBottomBarIndicatorLayerTransform(
            motionProgress = motionProgress,
            velocityItemsPerSecond = velocityItemsPerSecond,
            isDragging = isDragging,
            dragScaleProgress = indicatorLayerScaleProgress,
            dragScaleTransform = indicatorLayerScaleTransform,
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
    val pillHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = 90f)
    val indicatorBackdrop = if (shouldUseBottomBarCombinedIndicatorBackdrop(liquidGlassPreset)) {
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
                if (indicatorBackdrop == null && indicatorEffectsEnabled) {
                    scaleX = indicatorLayerTransform.scaleX
                    scaleY = indicatorLayerTransform.scaleY
                }
            }
            .width(indicatorWidth)
            .height(indicatorHeight)
            .align(indicatorAlignment)
            .zIndex(2f)
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
                                    chromaticAberration = 0.5f
                                )
                            }
                        },
                        highlight = {
                            pillHighlight.copy(alpha = effectivePressProgress)
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
                                    Color.Black.copy(alpha = 0.03f * effectivePressProgress)
                                )
                            }
                        },
                        layerBlock = {
                            if (indicatorEffectsEnabled) {
                                scaleX = indicatorLayerTransform.scaleX
                                scaleY = indicatorLayerTransform.scaleY
                            }
                        }
                    ).miuixInnerShadow(shape = shellShape) {
                        MiuixInnerShadow(
                            radius = 8.dp * effectivePressProgress,
                            color = Color.Black.copy(alpha = 0.15f),
                            alpha = effectivePressProgress
                        )
                    }
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

@Composable
internal fun BoxScope.KernelSuBottomBarIndicatorLayer(
    visible: Boolean,
    dockContentAlpha: Float,
    indicatorTranslationXPx: Float,
    indicatorTranslationYPx: Float = 0f,
    indicatorPanelOffsetPx: Float,
    indicatorPanelOffsetYPx: Float = 0f,
    indicatorSettleReboundTransform: BottomBarClickPulseTransform,
    indicatorWidth: Dp,
    indicatorHeight: Dp = 56.dp,
    shellShape: androidx.compose.ui.graphics.Shape,
    liquidGlassPreset: BottomBarLiquidGlassPreset,
    contentBackdrop: Backdrop?,
    backdrop: Backdrop?,
    indicatorLensSpec: BottomBarBackdropPresetLensSpec,
    effectivePressProgress: Float,
    indicatorIdleSurfaceColor: Color,
    glassEnabled: Boolean,
    indicatorEffectsEnabled: Boolean = glassEnabled,
    motionProgress: Float,
    velocityItemsPerSecond: Float,
    isDragging: Boolean,
    indicatorLayerScaleProgress: Float,
    indicatorLayerScaleTransform: BottomBarIndicatorLayerTransform? = null,
    bottomBarMotionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec,
    isDarkTheme: Boolean,
    swapMotionAxes: Boolean = false,
    indicatorAlignment: Alignment = Alignment.CenterStart
) {
    if (!visible) return
    val rawIndicatorLayerTransform = if (indicatorEffectsEnabled) {
        resolveBottomBarIndicatorLayerTransform(
            motionProgress = motionProgress,
            velocityItemsPerSecond = velocityItemsPerSecond,
            isDragging = isDragging,
            dragScaleProgress = indicatorLayerScaleProgress,
            dragScaleTransform = indicatorLayerScaleTransform,
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
    val indicatorBackdrop = if (shouldUseBottomBarCombinedIndicatorBackdrop(liquidGlassPreset)) {
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
                scaleX = indicatorSettleReboundTransform.scaleX
                scaleY = indicatorSettleReboundTransform.scaleY
                if (indicatorBackdrop == null && indicatorEffectsEnabled) {
                    scaleX *= indicatorLayerTransform.scaleX
                    scaleY *= indicatorLayerTransform.scaleY
                }
            }
            .width(indicatorWidth)
            .height(indicatorHeight)
            .align(indicatorAlignment)
            .run {
                if (indicatorBackdrop != null) {
                    drawBackdrop(
                        backdrop = indicatorBackdrop,
                        shape = { shellShape },
                        effects = {
                            if (shouldUseBottomBarIndicatorLens(liquidGlassPreset)) {
                                lens(
                                    refractionHeight = indicatorLensSpec.refractionHeightDp.dp.toPx(),
                                    refractionAmount = indicatorLensSpec.refractionAmountDp.dp.toPx(),
                                    depthEffect = true,
                                    chromaticAberration = true
                                )
                            }
                        },
                        highlight = {
                            Highlight(
                                width = 1.dp,
                                alpha = effectivePressProgress
                            )
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
                                    Color.Black.copy(alpha = 0.03f * effectivePressProgress)
                                )
                            }
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 8.dp * effectivePressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = effectivePressProgress
                            )
                        },
                        layerBlock = {
                            if (indicatorEffectsEnabled) {
                                scaleX = indicatorLayerTransform.scaleX
                                scaleY = indicatorLayerTransform.scaleY
                            }
                        }
                    )
                } else {
                    background(
                        indicatorIdleSurfaceColor,
                        shellShape
                    )
                }
            }
    )
}

@Composable
private fun BoxScope.KernelSuBottomBarInputLayer(
    visible: Boolean,
    visibleItems: List<BottomNavItem>,
    isTablet: Boolean,
    hasSidebarToggle: Boolean,
    dockContentPadding: PaddingValues,
    dockWidth: Dp,
    dockHeight: Dp,
    visiblePanelOffsetPx: Float,
    dampedDragState: DampedDragAnimationState,
    itemWidthPx: Float,
    itemWidth: Dp,
    onItemClick: (Int, BottomNavItem) -> Unit,
    onSidebarClick: () -> Unit
) {
    if (!visible) return
    Row(
        modifier = Modifier
            .width(dockWidth)
            .height(dockHeight)
            .align(Alignment.Center)
            .padding(dockContentPadding)
            .alpha(0f)
            .graphicsLayer { translationX = visiblePanelOffsetPx }
            .horizontalDragGesture(
                dragState = dampedDragState,
                itemWidthPx = itemWidthPx
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibleItems.forEachIndexed { index, item ->
            BottomBarInputTarget(
                itemWidth = itemWidth,
                onClick = { onItemClick(index, item) },
                onPressChanged = dampedDragState::setPressed
            )
        }

        if (isTablet && hasSidebarToggle) {
            BottomBarInputTarget(
                itemWidth = itemWidth,
                onClick = onSidebarClick,
                onPressChanged = dampedDragState::setPressed
            )
        }
    }
}

@Composable
private fun KernelSuBottomBarSearchSlot(
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
    materialPressProgress: Float
) {
    if (!visible) return
    Spacer(modifier = Modifier.width(launchAdjustedSearchGap))
    Box(
        modifier = Modifier
            .width(searchWidth)
            .height(searchHeight)
    ) {
        KernelSuBottomBarSearchCapsule(
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
            materialPressProgress = materialPressProgress
        )
    }
}

@Composable
private fun KernelSuBottomBarSearchCapsule(
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
    materialPressProgress: Float
) {
    var searchLongPressHeld by remember { mutableStateOf(false) }
    val currentOnCompactClick by rememberUpdatedState(onCompactClick)
    val currentOnSubmit by rememberUpdatedState(onSubmit)
    val currentHaptic by rememberUpdatedState(haptic)
    val launchSearchFromExpandedBlankQuery = expanded && query.isBlank()
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
        currentHaptic(HapticType.MEDIUM)
        while (searchLongPressHeld) {
            delay(90L)
            currentHaptic(HapticType.SELECTION)
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
                scaleX = longPressHorizontalScale
            }
            .kernelSuMiuixFloatingDockSurface(
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
                materialPressProgress = materialPressProgress
            )
            .then(
                if (!expanded) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                try {
                                    awaitRelease()
                                } finally {
                                    if (searchLongPressHeld) {
                                        searchLongPressHeld = false
                                    }
                                }
                            },
                            onTap = {
                                currentHaptic(HapticType.LIGHT)
                                currentOnCompactClick()
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
        KernelSuBottomBarSearchVisualContent(
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
            interactive = true
        )
        if (launchSearchFromExpandedBlankQuery) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        currentHaptic(HapticType.LIGHT)
                        currentOnSubmit()
                    }
            )
        }
    }
}

@Composable
private fun KernelSuBottomBarSearchVisualContent(
    expanded: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    contentColor: Color,
    accentColor: Color,
    iconScale: Float,
    fieldAlpha: Float,
    interactive: Boolean
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
            tint = contentColor,
            modifier = Modifier
                .size(24.dp)
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
                )
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        )
        if (expanded) {
            Spacer(modifier = Modifier.width(10.dp))
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
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .alpha(fieldAlpha),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
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
        0.dp
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
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = resolveBottomBarSkinDockIconTopPadding())
                )
                Text(
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
                            start = 2.dp,
                            end = 2.dp,
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
                                    contentDescription = label
                                )
                            }
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
            if (showIcon && showText && iconLabelGap > 0.dp) {
                Spacer(modifier = Modifier.height(iconLabelGap))
            }
            if (showText) {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = if (shouldUseSkinItemLayout) {
                        resolveBottomBarSkinDockLabelFontSize()
                    } else {
                        11.sp
                    },
                    lineHeight = if (shouldUseSkinItemLayout) {
                        resolveBottomBarSkinDockLabelLineHeight()
                    } else {
                        14.sp
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
    BottomNavItem.WATCHLATER -> if (selected) Icons.Filled.WatchLater else Icons.Outlined.WatchLater
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
                        color = AppSurfaceTokens.cardContainer(),
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
