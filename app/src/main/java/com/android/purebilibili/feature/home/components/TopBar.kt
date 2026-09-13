// 文件路径: feature/home/components/TopBar.kt
package com.android.purebilibili.feature.home.components

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.resolveScrollableTabIndicatorFollowDeltaPx
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle

import com.android.purebilibili.core.ui.OpticalContrastPalette
import com.android.purebilibili.feature.home.HomeVisualPalette

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.MenuOpen
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SportsEsports

import androidx.compose.animation.*
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.foundation.ExperimentalFoundationApi // [Added]
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.AppIconStyle
import com.android.purebilibili.core.ui.AppSemanticIconFamily
import com.android.purebilibili.core.ui.AppTopTabPresentation
import com.android.purebilibili.core.ui.rememberAppTopChromePolicy
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.feature.home.UserState
import com.android.purebilibili.feature.home.HomeCategory
import com.android.purebilibili.feature.home.resolveHomeTopCategories
import com.android.purebilibili.core.store.BottomBarLiquidGlassPreset
import com.android.purebilibili.core.store.LiquidGlassStyle
import com.android.purebilibili.core.store.LiquidGlassReadabilityMode
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.blur.currentUnifiedBlurIntensity
import com.android.purebilibili.feature.home.components.liquid.rememberCombinedBackdrop as rememberMiuixCombinedBackdrop
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop as rememberMiuixLayerBackdrop
import dev.chrisbanes.haze.HazeState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import com.android.purebilibili.core.ui.components.KeepLazyTabSelectionVisible
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.combinedClickable // [Added]
import java.io.File

private const val IOS_TOP_TAB_CONTENT_PADDING_DP = 2f
private const val TOP_TAB_WIDE_CENTER_BREAKPOINT_DP = 600f
private const val TOP_TAB_INDICATOR_SETTLE_TIMEOUT_MILLIS = 1_500L

// 指示器拖动释放后允许 spring 飞掷动画 settle 的兜底时长；
// 超过此时长仍未收到 onSettled 回调则强制解除 engaged，避免位置竞争。

internal fun resolveFloatingIndicatorStartPaddingPx(
    baseInsetPx: Float,
    leftBiasPx: Float
): Float = (baseInsetPx - leftBiasPx).coerceAtLeast(0f)

internal fun resolveTopTabRowHorizontalPaddingDp(
    isFloatingStyle: Boolean,
    edgeToEdge: Boolean = false,
    labelMode: Int = 0
): Float {
    if (edgeToEdge) return 0f
    if (isFloatingStyle) return 0f
    // Text-only MD3/Miuix: drop the extra 4dp so the first indicator sits closer to the edge.
    return if (normalizeTopTabLabelMode(labelMode) == 2) 0f else 4f
}

// Slightly tighter than before so rest capsule nearly fills the dock (bottom-bar feel),
// while drag scale still overflows the chrome edge.
internal fun resolveTopTabDockIndicatorHorizontalGapDp(
    hasOuterChromeSurface: Boolean,
    isLiquidGlassReuseEnabled: Boolean = false
): Float {
    val standardGap = if (hasOuterChromeSurface) 2f else 2f
    return if (isLiquidGlassReuseEnabled) {
        (standardGap - 1f).coerceAtLeast(1f)
    } else {
        standardGap
    }
}

internal fun resolveTopTabDockIndicatorVerticalGapDp(
    hasOuterChromeSurface: Boolean,
    isLiquidGlassReuseEnabled: Boolean = false
): Float {
    val standardGap = if (hasOuterChromeSurface) 3f else 3f
    return if (isLiquidGlassReuseEnabled) {
        (standardGap - 1f).coerceAtLeast(1f)
    } else {
        standardGap
    }
}

/**
 * Same 4dp start/end inset as [FloatingBottomBar] so the first and last
 * selected capsules sit inside the stadium end-caps without empty glass caps.
 */
internal fun resolveTopTabDockEndInsetDp(
    wrapContent: Boolean,
    isFloatingStyle: Boolean
): Float = if (wrapContent || isFloatingStyle) 4f else 0f

/**
 * 顶部 Tab 的视觉背景保持 30dp 高；36dp 行高留出上下各 3dp 的呼吸空间。
 */
internal fun resolveTopTabIndicatorShape(
    showIcon: Boolean,
    showText: Boolean,
): Shape =
    if (showIcon && showText) {
        RoundedCornerShape(12.dp)
    } else {
        resolveSharedBottomBarCapsuleShape()
    }

internal fun resolveTopTabDockIndicatorWidthDp(
    itemWidthDp: Float,
    horizontalGapDp: Float,
    minWidthDp: Float = 0f
): Float {
    if (itemWidthDp <= 0f) return 0f
    val maxWidth = (itemWidthDp - horizontalGapDp.coerceAtLeast(0f) * 2f)
        .coerceAtLeast(0f)
    val minWidth = minWidthDp.coerceIn(0f, itemWidthDp)
    return maxWidth.coerceAtLeast(minWidth)
}

/** Interpolates liquid capsule width between adjacent tab labels during pager motion. */
internal fun resolveTopTabInterpolatedIndicatorWidthDp(
    position: Float,
    itemWidthDp: Float,
    horizontalGapDp: Float,
    contentWidthsDp: List<Float>,
): Float {
    if (itemWidthDp <= 0f) return 0f
    val slotMax = resolveTopTabDockIndicatorWidthDp(itemWidthDp, horizontalGapDp)
    if (contentWidthsDp.isEmpty()) return slotMax
    val clamped = position.coerceIn(0f, contentWidthsDp.lastIndex.toFloat())
    val start = clamped.toInt()
    val end = (start + 1).coerceAtMost(contentWidthsDp.lastIndex)
    val contentWidth = androidx.compose.ui.util.lerp(
        contentWidthsDp[start], contentWidthsDp[end], clamped - start
    ) + horizontalGapDp * 2f
    return contentWidth.coerceIn(horizontalGapDp * 2f + 1f, slotMax)
}

internal fun resolveTopTabDockIndicatorHeightDp(
    rowHeightDp: Float,
    verticalGapDp: Float,
    minHeightDp: Float,
    indicatorWidthDp: Float = Float.POSITIVE_INFINITY
): Float {
    if (rowHeightDp <= 0f) return 0f
    val maxHeight = (rowHeightDp - verticalGapDp.coerceAtLeast(0f) * 2f)
        .coerceAtLeast(0f)
    val minHeight = minHeightDp.coerceIn(0f, rowHeightDp)
    return resolveSegmentedControlIndicatorHeightDp(
        slotWidthDp = indicatorWidthDp,
        indicatorHeightDp = maxHeight
    ).coerceAtLeast(minHeight)
}

/**
 * Preferred per-tab width when the floating dock **wraps content** instead of stretching
 * full width (icon / text density drives dock length).
 */
internal fun resolveTopTabWrapItemWidthDp(
    labelMode: Int,
    isFloatingStyle: Boolean = true
): Float {
    // Keep wrap-dock preferred widths at least the multi-slot floor so iOS / MD3 / Miuix
    // floating docks never pack tighter than the readable minimum.
    val floor = resolveMd3TopTabMinItemWidthDp(labelMode)
    val preferred = when (normalizeTopTabLabelMode(labelMode)) {
        // 图文混合模式至少要容纳 18dp 图标、6dp 间距和两三个汉字，
        // 否则文字会退化为单独的省略号。
        0 -> if (isFloatingStyle) 84f else 80f // icon + text
        1 -> if (isFloatingStyle) 56f else 52f // icon only
        else -> if (isFloatingStyle) 72f else 68f // text only
    }
    return preferred.coerceAtLeast(floor)
}

/**
 * Whether the top dock should shrink to tab content instead of fillMaxWidth.
 * Floating / bottom-bar-matched docks: wrap so right side isn't empty chrome.
 * Embedded / full-bleed rows keep stretch.
 */
internal fun shouldWrapTopTabDockWidth(
    isFloatingStyle: Boolean,
    hasOuterChromeSurface: Boolean,
    edgeToEdge: Boolean
): Boolean {
    if (edgeToEdge) return false
    return isFloatingStyle || hasOuterChromeSurface
}

/**
 * Dock content width = itemWidth × tabCount (+ optional horizontal content padding).
 * Clamped to [maxWidthDp] so small phones still fill when content is wider.
 */
internal fun resolveTopTabDockWrapWidthDp(
    itemWidthDp: Float,
    categoryCount: Int,
    maxWidthDp: Float,
    contentPaddingHorizontalDp: Float = 0f
): Float {
    if (itemWidthDp <= 0f || categoryCount <= 0) return 0f
    val content = itemWidthDp * categoryCount + contentPaddingHorizontalDp.coerceAtLeast(0f) * 2f
    if (maxWidthDp <= 0f) return content
    return content.coerceIn(0f, maxWidthDp)
}

/**
 * Item width for wrap dock: use content-driven preferred width when it fits;
 * otherwise fall back to dividing the available max width (scrollable denser slots).
 */
internal fun resolveTopTabDockItemWidthDp(
    maxWidthDp: Float,
    categoryCount: Int,
    labelMode: Int,
    isFloatingStyle: Boolean,
    wrapContent: Boolean,
    fillItemWidthDp: Float
): Float {
    if (!wrapContent || categoryCount <= 0) return fillItemWidthDp
    val preferred = resolveTopTabWrapItemWidthDp(labelMode, isFloatingStyle)
    val endInset = resolveTopTabDockEndInsetDp(
        wrapContent = true,
        isFloatingStyle = isFloatingStyle
    )
    val wrapWidth = resolveTopTabDockWrapWidthDp(
        itemWidthDp = preferred,
        categoryCount = categoryCount,
        maxWidthDp = maxWidthDp,
        contentPaddingHorizontalDp = endInset
    )
    // Preferred pack fits: use content-driven item width.
    if (wrapWidth <= maxWidthDp + 0.01f && preferred * categoryCount + endInset * 2f <= maxWidthDp + 0.01f) {
        return preferred
    }
    // Overflow: pack into available width.
    return fillItemWidthDp
}

internal fun resolveTopTabDockIndicatorOffsetPx(
    slotTranslationPx: Float,
    horizontalGapPx: Float
): Float = slotTranslationPx + horizontalGapPx.coerceAtLeast(0f)

internal fun resolveTopTabEdgeAwarePanelOffsetPx(
    position: Float,
    lastIndex: Int,
    panelOffsetPx: Float,
): Float {
    if (lastIndex <= 0 || panelOffsetPx == 0f) return 0f
    val clampedPosition = position.coerceIn(0f, lastIndex.toFloat())
    val outwardDistance = if (panelOffsetPx > 0f) {
        lastIndex - clampedPosition
    } else {
        clampedPosition
    }
    // Fade only the outward inertia during the final quarter-slot. At the hard edge the
    // indicator stays inside the dock, avoiding a second outline from the shell end-cap.
    val edgeFactor = (outwardDistance / 0.25f).coerceIn(0f, 1f)
    return panelOffsetPx * edgeFactor
}

internal fun resolveTopTabVisibleSlots(
    categoryCount: Int,
    longestLabelLength: Int = 0
): Int {
    val cappedCategoryCount = categoryCount.coerceAtMost(SettingsManager.MAX_TOP_TABS)
    if (cappedCategoryCount in 1..3) return cappedCategoryCount
    if (cappedCategoryCount <= 4) return 4
    return if (longestLabelLength >= 8) 4 else 5
}

internal fun resolveMd3TopTabVisibleSlots(): Int = 3

internal fun resolveMd3TopTabLayoutVisibleSlots(
    categoryCount: Int,
    labelMode: Int,
    showPartitionAction: Boolean,
    fontScale: Float = 1f,
    containerWidthDp: Float = 0f
): Int {
    val hasSupportedLabelMode = normalizeTopTabLabelMode(labelMode) in 0..2
    val cappedCategoryCount = categoryCount.coerceAtMost(SettingsManager.MAX_TOP_TABS)
    val baseSlots = if (!showPartitionAction && hasSupportedLabelMode && cappedCategoryCount >= 4) {
        if (fontScale > 1.15f) {
            cappedCategoryCount.coerceAtMost(4)
        } else {
            cappedCategoryCount
        }
    } else {
        resolveMd3TopTabVisibleSlots()
    }
    if (containerWidthDp <= 0f) return baseSlots
    val minWidth = resolveMd3TopTabMinItemWidthDp(labelMode)
    val maxFit = (containerWidthDp / minWidth).toInt().coerceAtLeast(1)
    return baseSlots.coerceAtMost(maxFit)
}

internal fun resolveIosTopTabLayoutVisibleSlots(
    categoryCount: Int,
    labelMode: Int,
    containerWidthDp: Float = 0f
): Int = resolveMd3TopTabLayoutVisibleSlots(
    categoryCount = categoryCount,
    labelMode = labelMode,
    showPartitionAction = false,
    containerWidthDp = containerWidthDp
)

internal fun resolveIosTopTabItemWidthDp(
    containerWidthDp: Float,
    categoryCount: Int,
    labelMode: Int
): Float {
    val usableWidth = (containerWidthDp - IOS_TOP_TAB_CONTENT_PADDING_DP * 2f)
        .coerceAtLeast(0f)
    return resolveMd3TopTabItemWidthDp(
        containerWidthDp = usableWidth,
        visibleSlots = resolveIosTopTabLayoutVisibleSlots(
            categoryCount = categoryCount,
            labelMode = labelMode,
            containerWidthDp = usableWidth
        ),
        labelMode = labelMode
    )
}

/**
 * Minimum slot width so labels/icons stay readable in the compact dock.
 *
 * Budget for text-only: outer 3dp×2 + content 4dp×2 + ~30dp for two CJK glyphs ≈ 44dp,
 * then add a little for semi-bold / font padding. Icon+text also needs 18+6 for glyph+gap.
 * Prefer scrolling over squeezing every tab into the viewport as pure "...".
 */
internal fun resolveMd3TopTabMinItemWidthDp(labelMode: Int): Float {
    return when (normalizeTopTabLabelMode(labelMode)) {
        0 -> 80f // icon + text
        1 -> 48f // icon only
        else -> 64f // text only — two CJK characters with compact padding
    }
}

internal fun resolveMd3TopTabMaxItemWidthDp(labelMode: Int): Float {
    return when (normalizeTopTabLabelMode(labelMode)) {
        0 -> 96f
        1 -> 64f
        else -> 88f
    }
}

internal fun resolveMd3TopTabItemWidthDp(
    containerWidthDp: Float,
    visibleSlots: Int = resolveMd3TopTabVisibleSlots(),
    labelMode: Int = 2
): Float {
    if (containerWidthDp <= 0f) return 96f
    val minWidth = resolveMd3TopTabMinItemWidthDp(labelMode)
    val maxWidth = resolveMd3TopTabMaxItemWidthDp(labelMode)
    if (visibleSlots >= 5) {
        return (containerWidthDp / visibleSlots).coerceIn(minWidth, maxWidth)
    }
    return (containerWidthDp / visibleSlots.coerceAtLeast(1)).coerceAtLeast(minWidth.coerceAtLeast(88f))
}

internal fun resolveFixedHomeTopTabItemWidthDp(
    containerWidthDp: Float,
    categoryCount: Int,
): Float {
    if (containerWidthDp <= 0f || categoryCount <= 0) return 0f
    return containerWidthDp / categoryCount
}

internal fun resolveMd3TopTabContentPaddingDp(
    containerWidthDp: Float,
    itemWidthDp: Float,
    categoryCount: Int,
    labelMode: Int = 0
): Float {
    if (containerWidthDp <= 0f || itemWidthDp <= 0f || categoryCount <= 0) return 0f
    val contentWidth = itemWidthDp * categoryCount
    val leftover = (containerWidthDp - contentWidth).coerceAtLeast(0f)
    // Phone rows with several categories stay lead-aligned for predictable reach and
    // overflow. Wide windows center the complete tab group inside the full-width dock.
    @Suppress("UNUSED_PARAMETER")
    val ignoredLabelMode = labelMode
    return if (containerWidthDp >= TOP_TAB_WIDE_CENTER_BREAKPOINT_DP || categoryCount < 3) {
        leftover / 2f
    } else {
        0f
    }
}

internal fun resolveMd3VisibleTabIndices(
    totalCount: Int,
    selectedIndex: Int,
    visibleSlots: Int = resolveMd3TopTabVisibleSlots()
): List<Int> {
    if (totalCount <= 0) return emptyList()
    return List(totalCount) { it }
}

internal fun resolveMd3SelectedVisibleIndex(
    visibleIndices: List<Int>,
    selectedIndex: Int
): Int {
    val resolved = visibleIndices.indexOf(selectedIndex)
    return if (resolved >= 0) resolved else 0
}

internal fun resolveTopTabMinItemWidthDp(isFloatingStyle: Boolean): Float {
    return if (isFloatingStyle) 72f else 64f
}

internal fun resolveTopTabItemWidthDp(
    containerWidthDp: Float,
    categoryCount: Int,
    isFloatingStyle: Boolean,
    longestLabelLength: Int = 0
): Float {
    if (containerWidthDp <= 0f) return resolveTopTabMinItemWidthDp(isFloatingStyle)
    val slots = resolveTopTabVisibleSlots(
        categoryCount = categoryCount,
        longestLabelLength = longestLabelLength
    ).coerceAtLeast(1)
    val baseWidth = containerWidthDp / slots
    return baseWidth.coerceAtLeast(resolveTopTabMinItemWidthDp(isFloatingStyle))
}

internal fun resolveTopTabVisibleCategorySlots(
    categoryCount: Int,
    longestLabelLength: Int = 0
): Int {
    return resolveTopTabVisibleSlots(
        categoryCount = categoryCount,
        longestLabelLength = longestLabelLength
    ).coerceAtMost(categoryCount.coerceAtLeast(1)).coerceAtLeast(1)
}

internal fun resolveTopTabActionSlotWidthDp(
    containerWidthDp: Float,
    categoryCount: Int,
    longestLabelLength: Int = 0
): Float {
    if (containerWidthDp <= 0f) return 0f
    val categorySlots = resolveTopTabVisibleCategorySlots(
        categoryCount = categoryCount,
        longestLabelLength = longestLabelLength
    )
    return containerWidthDp / (categorySlots + 1)
}

internal fun normalizeTopTabLabelMode(mode: Int): Int {
    return when (mode) {
        0, 1, 2 -> mode
        else -> 2
    }
}

internal fun shouldShowTopTabIcon(mode: Int): Boolean {
    val normalized = normalizeTopTabLabelMode(mode)
    return normalized == 0 || normalized == 1
}

internal fun shouldShowTopTabText(mode: Int): Boolean {
    val normalized = normalizeTopTabLabelMode(mode)
    return normalized == 0 || normalized == 2
}

internal fun resolveTopTabIconFamily(
    chromeIconFamily: AppSemanticIconFamily,
    iconStyle: AppIconStyle = AppIconStyle.AUTO
): AppSemanticIconFamily {
    return when {
        // MD3 官方推荐样式统一使用 Material 官方字形
        iconStyle == AppIconStyle.MD3_STANDARD -> AppSemanticIconFamily.MATERIAL
        else -> chromeIconFamily
    }
}

internal fun resolveMd3TopTabLabelMode(requestedLabelMode: Int): Int =
    normalizeTopTabLabelMode(requestedLabelMode)

private fun resolveTopTabCategoryForIcon(categoryKey: String): HomeCategory? {
    val normalizedKey = categoryKey.trim()
    if (normalizedKey.isEmpty()) return null

    return HomeCategory.entries.firstOrNull { category ->
        category.name.equals(normalizedKey, ignoreCase = true) || category.label == normalizedKey
    }
}

@Composable
internal fun resolveTopTabCategoryIcon(
    categoryKey: String,
    iconFamily: AppSemanticIconFamily = AppSemanticIconFamily.MATERIAL,
    selected: Boolean = false
): ImageVector {
    val category = resolveTopTabCategoryForIcon(categoryKey)
    return when (iconFamily) {
        AppSemanticIconFamily.MATERIAL -> when (category) {
            HomeCategory.RECOMMEND -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
            HomeCategory.FOLLOW -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
            HomeCategory.POPULAR -> if (selected) {
                Icons.AutoMirrored.Filled.TrendingUp
            } else {
                Icons.AutoMirrored.Outlined.TrendingUp
            }
            HomeCategory.LIVE -> if (selected) Icons.Filled.LiveTv else Icons.Outlined.LiveTv
            HomeCategory.ANIME -> if (selected) Icons.Filled.CollectionsBookmark else Icons.Outlined.CollectionsBookmark
            HomeCategory.GAME -> if (selected) Icons.Filled.SportsEsports else Icons.Outlined.SportsEsports
            HomeCategory.KNOWLEDGE -> if (selected) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb
            HomeCategory.TECH -> if (selected) Icons.Filled.SmartToy else Icons.Outlined.SmartToy
            else -> Icons.AutoMirrored.Outlined.MenuOpen
        }
        AppSemanticIconFamily.MIUIX -> resolveMiuixPreferredHomeNavigationIcon(
            tabId = category?.name ?: "PARTITION",
            selected = selected,
        )
    }
}

@Composable
internal fun resolveTopTabPartitionIcon(iconFamily: AppSemanticIconFamily): ImageVector {
    return if (iconFamily == AppSemanticIconFamily.MATERIAL) {
        Icons.AutoMirrored.Outlined.MenuOpen
    } else {
        resolveMiuixPreferredHomeNavigationIcon(tabId = "PARTITION")
    }
}

internal enum class Md3TopTabRowVariant {
    UNDERLINE_FIXED
}

internal fun resolveMd3TopTabRowVariant(): Md3TopTabRowVariant =
    Md3TopTabRowVariant.UNDERLINE_FIXED

internal fun resolveMd3TopTabActionButtonCorner(
    isFloatingStyle: Boolean,
    presentation: AppTopTabPresentation = AppTopTabPresentation.MATERIAL_UNDERLINE
) = if (presentation == AppTopTabPresentation.TONAL_CAPSULE) {
    if (isFloatingStyle) AppSpacingTokens.Large + AppSpacingTokens.Micro else AppSpacingTokens.Medium + AppSpacingTokens.Micro
} else {
    if (isFloatingStyle) AppSpacingTokens.Large else AppSpacingTokens.Medium
}

internal fun resolveMd3TopTabActionButtonSize(
    isFloatingStyle: Boolean,
    presentation: AppTopTabPresentation = AppTopTabPresentation.MATERIAL_UNDERLINE
) = if (presentation == AppTopTabPresentation.TONAL_CAPSULE) {
    if (isFloatingStyle) AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Micro else AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Medium
} else {
    if (isFloatingStyle) AppSpacingTokens.TripleExtraLarge else AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small + AppSpacingTokens.Micro
}

internal fun resolveMd3TopTabActionIconSize(
    isFloatingStyle: Boolean,
    presentation: AppTopTabPresentation = AppTopTabPresentation.MATERIAL_UNDERLINE
) = if (presentation == AppTopTabPresentation.TONAL_CAPSULE) {
    if (isFloatingStyle) AppSpacingTokens.ExtraLarge else AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro
} else {
    if (isFloatingStyle) AppSpacingTokens.ExtraLarge else AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro
}

internal fun resolveMd3TopTabActionContentBottomPadding(): Dp = AppSpacingTokens.ExtraSmall

internal fun resolveMd3TopTabVerticalLiftDp(): Float = 4f

internal fun resolveMd3TopTabRowVerticalTranslationDp(
    skinPlainStyle: Boolean,
    hasOuterChromeSurface: Boolean
): Float {
    if (skinPlainStyle || hasOuterChromeSurface) return 0f
    return -resolveMd3TopTabVerticalLiftDp()
}

internal fun resolveMd3TopTabIndicatorBottomPadding(): Dp = AppSpacingTokens.Small

internal fun resolveIconOnlyTopTabIndicatorWidth(): Dp = AppSpacingTokens.ExtraLarge

internal fun resolveHomeSkinTopTabActionButtonSize(): Dp = AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Medium

internal fun resolveHomeSkinTopTabActionIconSize(): Dp = AppSpacingTokens.ExtraLarge

internal fun resolveHomeSkinTopTabIndicatorBottomPadding(): Dp = AppSpacingTokens.ExtraSmall

internal fun resolveTopTabSkinStickerIconSize(showText: Boolean): Dp =
    if (showText) AppSpacingTokens.DoubleExtraLarge else AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall

internal fun resolveTopTabSkinPartitionIconSize(): Dp = AppSpacingTokens.DoubleExtraLarge

internal fun resolveTopTabSkinStickerIndicatorWidth(): Dp = AppSpacingTokens.ExtraLarge + AppSpacingTokens.ExtraSmall

internal fun resolveTopTabSkinStickerRowHeight(
    baseRowHeight: Dp,
    hasSkinStickerIcons: Boolean,
    showIcon: Boolean,
    showText: Boolean
): Dp {
    return if (hasSkinStickerIcons && showIcon && showText) {
        baseRowHeight.coerceAtLeast(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Large)
    } else {
        baseRowHeight
    }
}

internal fun resolveTopTabSkinStickerItemVerticalPadding(showText: Boolean): Dp =
    if (showText) AppSpacingTokens.Micro else AppSpacingTokens.ExtraSmall

/**
 * Keep this in sync with [resolveHomeTopPresetStyle]. Icon+text uses a taller track
 * because its content is stacked vertically like the bottom navigation bar.
 */
internal fun resolveIosTopTabRowHeight(
    isFloatingStyle: Boolean,
    labelMode: Int = SettingsManager.TopTabLabelMode.TEXT_ONLY
): Dp {
    val iconAndText = normalizeTopTabLabelMode(labelMode) == 0
    return if (isFloatingStyle) {
        if (iconAndText) 60.dp else AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Small
    } else {
        if (iconAndText) 56.dp else AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall
    }
}

internal fun resolveIosTopTabActionButtonSize(isFloatingStyle: Boolean): Dp =
    if (isFloatingStyle) AppSpacingTokens.TripleExtraLarge - AppSpacingTokens.Micro else AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.Medium

internal fun resolveIosTopTabActionButtonCorner(isFloatingStyle: Boolean): Dp =
    if (isFloatingStyle) AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro else AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall

internal fun resolveIosTopTabActionIconSize(isFloatingStyle: Boolean): Dp =
    if (isFloatingStyle) AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro / 2 else AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro

internal fun performHomeTopBarTap(
    haptic: (HapticType) -> Unit,
    onClick: () -> Unit,
    hapticType: HapticType = HapticType.LIGHT
) {
    haptic(hapticType)
    onClick()
}

/**
 * Q弹点击效果
 */
fun Modifier.premiumClickable(onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "scale"
    )
    this
        .scale(scale)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

/**
 *  iOS 风格悬浮顶栏
 * - 不贴边，有水平边距
 * - 圆角 + 毛玻璃效果
 */
@Composable
fun FluidHomeTopBar(
    user: UserState,
    onAvatarClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        //  悬浮式导航栏容器 - 增强视觉层次
        AppSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacingTokens.Large, vertical = AppSpacingTokens.Small),
            shape = AppShapes.borderedContainer(ContainerLevel.Floating),
            color = AppSurfaceTokens.cardContainer(),  //  使用预设感知表面色，适配深色模式
            shadowElevation = AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro,  // 添加阴影增加层次感
            tonalElevation = AppSpacingTokens.None,
            border = androidx.compose.foundation.BorderStroke(
                width = AppSpacingTokens.Micro / 2,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.ExtraSmall) // 稍微减小高度
                    .padding(horizontal = AppSpacingTokens.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                //  左侧：头像
                Box(
                    modifier = Modifier
                        .size(AppChromeSizeTokens.MinimumTouchTarget)
                        .clip(CircleShape)
                        .premiumClickable { onAvatarClick() }
                        .semantics { contentDescription = "个人中心" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall)
                            .clip(CircleShape)
                            .border(AppSpacingTokens.Micro / 2, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        if (user.isLogin && user.face.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(FormatUtils.fixImageUrl(user.face))
                                    .size(128, 128)
                                    .crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                AppText("未", fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(AppSpacingTokens.Medium))

                //  中间：搜索框
                val searchClickInteractionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall)
                        .clip(AppShapes.container(ContainerLevel.Pill))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = searchClickInteractionSource,
                            indication = null
                        ) {
                            onSearchClick()
                        }
                        .padding(horizontal = AppSpacingTokens.Medium),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(
                            Icons.Outlined.Search,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f),
                            modifier = Modifier.size(AppSpacingTokens.Large + AppSpacingTokens.Micro)
                        )
                        Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                        AppText(
                            text = "搜索视频、UP主...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = MaterialTheme.typography.labelMedium.fontSize,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                
                //  右侧：设置按钮
                AppIconButton(
                    onClick = onSettingsClick
                ) {
                    AppIcon(
                        Icons.Outlined.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro)
                    )
                }
            }
        }
    }
}

/**
 * [HIG] iOS 风格可滑动分类标签栏。
 * - 所有分类水平平铺，支持系统惯性滚动。
 * - 使用轻量胶囊和文字强调，不再绘制顶部液态玻璃指示器。
 */
internal fun resolveTopTabUnselectedAlpha(): Float = 0.78f

internal fun resolveTopTabUnselectedColor(isLightMode: Boolean): Color {
    return if (isLightMode) {
        OpticalContrastPalette.Shadow.copy(alpha = 0.72f)
    } else {
        OpticalContrastPalette.Highlight.copy(alpha = 0.72f)
    }
}

internal fun resolveIosTopTabSelectedContentColor(
    colorScheme: ColorScheme,
    uiStyle: AppUiStyle = AppUiStyle.MIUIX
): Color = if (uiStyle == AppUiStyle.MIUIX) colorScheme.onSurface else colorScheme.primary

internal fun resolveIosTopTabCapsuleContainerColor(
    isDarkTheme: Boolean,
    selectionFraction: Float
): Color {
    val selectedAlpha = selectionFraction.coerceIn(0f, 1f)
    val baseColor = resolveBottomBarMovingIndicatorSurfaceColor(isDarkTheme = isDarkTheme)
    return baseColor.copy(alpha = 0.28f * selectedAlpha)
}

/** Same full-strength shell lens as [FloatingBottomBar] (24dp / 24dp). */
internal const val TOP_DOCK_SHELL_LENS_INTENSITY = 1f

internal fun Modifier.homeTopBottomBarMatchedSurface(
    renderMode: HomeTopChromeRenderMode,
    shape: Shape,
    hazeState: HazeState?,
    miuixBackdrop: MiuixBackdrop? = null,
    liquidGlassStyle: LiquidGlassStyle,
    liquidGlassTuning: LiquidGlassTuning?,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    motionTier: MotionTier,
    isTransitionRunning: Boolean,
    forceLowBlurBudget: Boolean,
    drawShellLens: Boolean = true,
    shellLensIntensity: Float = 1f,
    isScrolling: Boolean = false,
    materialScrollProgress: Float = if (isScrolling) 1f else 0f
): Modifier = composed {
    val isGlassEnabled = renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_BACKDROP ||
        renderMode == HomeTopChromeRenderMode.LIQUID_GLASS_HAZE
    val isBlurEnabled = renderMode != HomeTopChromeRenderMode.PLAIN
    val blurIntensity = currentUnifiedBlurIntensity()
    val isDarkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.chromeBackground())
    val tuning = resolveAndroidNativeBottomBarTuning(
        blurEnabled = isBlurEnabled || isGlassEnabled,
        darkTheme = isDarkTheme
    )
    val resolvedLiquidGlassTuning = liquidGlassTuning
        ?: resolveLiquidGlassTuning(liquidGlassStyle)
    // Same container tint as FloatingBottomBar / bottom dock.
    val containerColor = resolveAndroidNativeFloatingBottomBarContainerColor(
        surfaceColor = MaterialTheme.colorScheme.surfaceContainer,
        tuning = tuning,
        glassEnabled = isGlassEnabled,
        blurEnabled = isBlurEnabled,
        blurIntensity = blurIntensity,
        liquidGlassPreset = liquidGlassPreset,
        liquidGlassTuning = resolvedLiquidGlassTuning
    )
    if (isGlassEnabled && miuixBackdrop != null) {
        // BiliPai outer dock shell (same stack as bottom FloatingBottomBar).
        this.biliPaiFloatingDockShell(
            backdrop = miuixBackdrop,
            containerColor = containerColor,
            pressProgress = 0f,
            shape = shape,
            enabled = true,
            drawLens = drawShellLens,
            lensIntensity = shellLensIntensity,
            liquidGlassTuning = resolvedLiquidGlassTuning,
        )
    } else {
        this.bottomBarMatchedLiquidDockSurface(
            shape = shape,
            backdrop = miuixBackdrop,
            containerColor = containerColor,
            blurEnabled = isBlurEnabled,
            glassEnabled = false,
            drawShellLens = false,
            shellLensIntensity = shellLensIntensity,
            blurRadius = tuning.shellBlurRadiusDp.dp,
            hazeState = hazeState,
            motionTier = motionTier,
            isTransitionRunning = isTransitionRunning,
            forceLowBlurBudget = forceLowBlurBudget,
            liquidGlassPreset = liquidGlassPreset,
            liquidGlassTuning = resolvedLiquidGlassTuning,
            isScrollInProgressProvider = { isScrolling },
            materialScrollProgressOverride = materialScrollProgress
        )
    }.then(
        // Miuix blur does not dim its sampled backdrop in dark mode. Add a
        // restrained scrim above the material to reduce bright background bleed.
        if (isDarkTheme) {
            Modifier.background(Color.Black.copy(alpha = 0.10f), shape)
        } else {
            Modifier
        }
    )
}

@Composable
private fun LightweightHomeTopTabs(
    presentation: AppTopTabPresentation,
    categories: List<String>,
    categoryKeys: List<String>,
    selectedIndex: Int,
    onCategorySelected: (Int) -> Unit,
    onPartitionClick: () -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState?,
    labelMode: Int,
    isFloatingStyle: Boolean,
    edgeToEdge: Boolean,
    skinPlainStyle: Boolean = false,
    skinPlainContentColor: Color? = null,
    isLiquidGlassEnabled: Boolean = false,
    liquidGlassStyle: LiquidGlassStyle = LiquidGlassStyle.CLASSIC,
    liquidGlassTuning: LiquidGlassTuning? = null,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    miuixBackdrop: MiuixBackdrop? = null,
    topTabSkinIconPaths: Map<String, TopTabSkinIconPaths> = emptyMap(),
    partitionSkinIconPath: String? = null,
    hasOuterChromeSurface: Boolean = false,
    /** When non-null, overrides [shouldWrapTopTabDockWidth] so shell and tabs share one decision. */
    wrapDockWidth: Boolean? = null,
    /**
     * Cap on the dock width (top controls' combined width) so the tab strip never
     * extends beyond the avatar / settings alignment. [Float.POSITIVE_INFINITY] keeps
     * legacy full-bleed docks.
     */
    maxDockWidthDp: Float = Float.POSITIVE_INFINITY,
    isTransitionRunning: Boolean = false,
    showPartitionAction: Boolean = true,
    isViewportSyncEnabled: Boolean = true,
    forceMaterialUnderline: Boolean = false
) {
    val chromePolicy = rememberAppTopChromePolicy()
    val resolvedLiquidGlassTuning = remember(liquidGlassStyle, liquidGlassTuning) {
        liquidGlassTuning ?: resolveLiquidGlassTuning(liquidGlassStyle)
    }
    val adaptiveReadabilityEnabled = isLiquidGlassEnabled &&
        resolvedLiquidGlassTuning.readabilityMode == LiquidGlassReadabilityMode.ADAPTIVE
    val adaptiveReadabilityState = rememberLiquidGlassAdaptiveReadabilityState(
        enabled = adaptiveReadabilityEnabled,
    )
    val adaptiveTopTabContentColor = rememberLiquidGlassAdaptiveContentColor(
        stableColor = MaterialTheme.colorScheme.onSurfaceVariant,
        state = adaptiveReadabilityState,
        enabled = adaptiveReadabilityEnabled,
    )
    val haptic = com.android.purebilibili.core.util.rememberHapticFeedback()
    val scrollChannel = com.android.purebilibili.feature.home.LocalHomeScrollChannel.current
    val normalizedLabelMode = normalizeTopTabLabelMode(labelMode)
    val topTabIconFamily = resolveTopTabIconFamily(
        chromeIconFamily = chromePolicy.effectiveIconFamily,
        iconStyle = chromePolicy.iconStyle
    )
    val showIcon = shouldShowTopTabIcon(normalizedLabelMode)
    val showText = shouldShowTopTabText(normalizedLabelMode)
    val effectivePresentation = when {
        skinPlainStyle || forceMaterialUnderline -> AppTopTabPresentation.MATERIAL_UNDERLINE
        // Retired Miuix TONAL_CAPSULE callers must not revive the old per-item fill.
        presentation == AppTopTabPresentation.TONAL_CAPSULE ->
            AppTopTabPresentation.MATERIAL_UNDERLINE
        else -> presentation
    }
    val useFloatingBottomBarDock = shouldHomeTopTabUseFloatingBottomBarDock(
        skinPlainStyle = skinPlainStyle,
        hasSkinStickerIcons = topTabSkinIconPaths.isNotEmpty() ||
            !partitionSkinIconPath.isNullOrBlank(),
        presentation = effectivePresentation,
        liquidGlassEnabled = isLiquidGlassEnabled,
        selectionIndicatorStyle = resolveHomeSelectionIndicatorStyle(
            uiStyle = LocalAppUiStyle.current,
            liquidGlassEnabled = isLiquidGlassEnabled,
            forceMaterialUnderline = forceMaterialUnderline,
        ),
    )
    val topTabMotionSpec = remember { resolveSegmentedControlMotionSpec() }
    val baseRowHeight = if (useFloatingBottomBarDock) {
        resolveBiliPaiBottomBarDockHeight(searchExpanded = false)
    } else if (skinPlainStyle) {
        resolveHomeSkinTopTabRowHeight()
    } else when (effectivePresentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> resolveIosTopTabRowHeight(isFloatingStyle, normalizedLabelMode)
        AppTopTabPresentation.MATERIAL_UNDERLINE -> resolveMd3TopTabVisualSpec(
            isFloatingStyle = isFloatingStyle,
            labelMode = normalizedLabelMode
        ).rowHeight
        AppTopTabPresentation.TONAL_CAPSULE -> resolveMd3TopTabVisualSpec(
            isFloatingStyle = false,
            presentation = AppTopTabPresentation.TONAL_CAPSULE,
            labelMode = normalizedLabelMode
        ).rowHeight
    }
    val hasSkinStickerIcons = topTabSkinIconPaths.isNotEmpty() || !partitionSkinIconPath.isNullOrBlank()
    val rowHeight = resolveTopTabSkinStickerRowHeight(
        baseRowHeight = baseRowHeight,
        hasSkinStickerIcons = hasSkinStickerIcons,
        showIcon = showIcon,
        showText = showText
    )
    val actionButtonSize = if (skinPlainStyle) {
        resolveHomeSkinTopTabActionButtonSize()
    } else when (effectivePresentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> resolveIosTopTabActionButtonSize(isFloatingStyle)
        AppTopTabPresentation.MATERIAL_UNDERLINE -> resolveMd3TopTabActionButtonSize(isFloatingStyle)
        AppTopTabPresentation.TONAL_CAPSULE -> resolveMd3TopTabActionButtonSize(
            isFloatingStyle = false,
            presentation = AppTopTabPresentation.TONAL_CAPSULE
        )
    }
    val actionButtonCorner = if (skinPlainStyle) {
        AppSpacingTokens.None
    } else when (effectivePresentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> resolveIosTopTabActionButtonCorner(isFloatingStyle)
        AppTopTabPresentation.MATERIAL_UNDERLINE -> resolveMd3TopTabActionButtonCorner(isFloatingStyle)
        AppTopTabPresentation.TONAL_CAPSULE -> resolveMd3TopTabActionButtonCorner(
            isFloatingStyle = false,
            presentation = AppTopTabPresentation.TONAL_CAPSULE
        )
    }
    val actionIconSize = if (skinPlainStyle) {
        resolveHomeSkinTopTabActionIconSize()
    } else when (effectivePresentation) {
        AppTopTabPresentation.MOVING_CAPSULE -> resolveIosTopTabActionIconSize(isFloatingStyle)
        AppTopTabPresentation.MATERIAL_UNDERLINE -> resolveMd3TopTabActionIconSize(isFloatingStyle)
        AppTopTabPresentation.TONAL_CAPSULE -> resolveMd3TopTabActionIconSize(
            isFloatingStyle = false,
            presentation = AppTopTabPresentation.TONAL_CAPSULE
        )
    }
    val listState = rememberLazyListState()
    var tabViewportLeftInWindowPx by remember { mutableFloatStateOf(Float.NaN) }
    var selectedItemLeftInWindowPx by remember { mutableFloatStateOf(Float.NaN) }
    val pagerIsDragging = rememberTopTabPagerDragHeld(pagerState)
    val currentPositionProvider = remember(pagerState, selectedIndex) {
        {
            resolveTopTabIndicatorRenderPosition(
                selectedIndex = selectedIndex,
                pagerCurrentPage = pagerState?.currentPage,
                pagerTargetPage = pagerState?.targetPage,
                pagerCurrentPageOffsetFraction = pagerState?.currentPageOffsetFraction,
                pagerIsScrolling = pagerState?.isScrollInProgress == true
            )
        }
    }
    val selectedContentPositionProvider = remember(pagerState, selectedIndex) {
        {
            resolveTopTabSelectedContentPosition(
                selectedIndex = selectedIndex,
                pagerCurrentPage = pagerState?.currentPage,
                pagerTargetPage = pagerState?.targetPage,
                pagerCurrentPageOffsetFraction = pagerState?.currentPageOffsetFraction,
                pagerIsScrolling = pagerState?.isScrollInProgress == true
            )
        }
    }
    val pagerScrollingProvider = remember(pagerState) {
        { pagerState?.isScrollInProgress == true }
    }
    val density = LocalDensity.current

    LaunchedEffect(selectedIndex, categories.size) {
        selectedItemLeftInWindowPx = Float.NaN
    }
    KeepLazyTabSelectionVisible(listState, selectedIndex)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = rowHeight)
            .padding(
                horizontal = resolveTopTabRowHorizontalPaddingDp(
                    isFloatingStyle = isFloatingStyle,
                    edgeToEdge = edgeToEdge,
                    labelMode = normalizedLabelMode
                ).dp
            )
    ) {
        val wrapDock = wrapDockWidth ?: shouldWrapTopTabDockWidth(
            isFloatingStyle = isFloatingStyle,
            hasOuterChromeSurface = hasOuterChromeSurface,
            edgeToEdge = edgeToEdge
        )
        // 分栏 dock 最大宽度 = 顶部三控件合计宽度，与外壳共享同一上限。
        val effectiveMaxDockWidth = minOf(maxWidth.value, maxDockWidthDp)
        val fillItemWidthDp = when (effectivePresentation) {
            AppTopTabPresentation.MOVING_CAPSULE -> resolveIosTopTabItemWidthDp(
                containerWidthDp = effectiveMaxDockWidth,
                categoryCount = categories.size,
                labelMode = normalizedLabelMode
            )
            AppTopTabPresentation.MATERIAL_UNDERLINE,
            AppTopTabPresentation.TONAL_CAPSULE -> if (forceMaterialUnderline) {
                resolveFixedHomeTopTabItemWidthDp(
                    containerWidthDp = effectiveMaxDockWidth,
                    categoryCount = categories.size,
                )
            } else {
                resolveMd3TopTabItemWidthDp(
                    containerWidthDp = effectiveMaxDockWidth,
                    visibleSlots = resolveMd3TopTabLayoutVisibleSlots(
                        categoryCount = categories.size,
                        labelMode = normalizedLabelMode,
                        showPartitionAction = showPartitionAction,
                        fontScale = density.fontScale,
                        containerWidthDp = effectiveMaxDockWidth
                    ),
                    labelMode = normalizedLabelMode
                )
            }
        }
        val itemWidthDp = resolveTopTabDockItemWidthDp(
            maxWidthDp = effectiveMaxDockWidth,
            categoryCount = categories.size,
            labelMode = normalizedLabelMode,
            isFloatingStyle = isFloatingStyle,
            wrapContent = wrapDock,
            fillItemWidthDp = fillItemWidthDp
        )
        val itemWidth = itemWidthDp.dp
        // Prefer content-driven dock length; parent chrome also uses this policy so shell + tabs match.
        val dockEndInsetDp = resolveTopTabDockEndInsetDp(
            wrapContent = wrapDock,
            isFloatingStyle = isFloatingStyle
        )
        val dockContentWidthDp = if (wrapDock) {
            resolveTopTabDockWrapWidthDp(
                itemWidthDp = itemWidthDp,
                categoryCount = categories.size,
                maxWidthDp = effectiveMaxDockWidth,
                contentPaddingHorizontalDp = dockEndInsetDp
            )
        } else {
            effectiveMaxDockWidth
        }
        if (useFloatingBottomBarDock) {
            val floatingDockHeight = resolveBiliPaiBottomBarDockHeight(searchExpanded = false)
            val floatingDockWidth = resolveHomeTopTabFloatingDockWidth(
                containerWidth = effectiveMaxDockWidth.dp,
                itemCount = categories.size,
                labelMode = normalizedLabelMode,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                HomeTopTabFloatingDock(
                    categories = categories,
                    categoryKeys = categoryKeys,
                    selectedIndex = selectedIndex,
                    onSelected = onCategorySelected,
                    onReselected = {
                        scrollChannel?.trySend(
                            com.android.purebilibili.feature.home.HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH
                        )
                    },
                    showIcon = showIcon,
                    showText = showText,
                    iconFamily = topTabIconFamily,
                    itemWidth = null,
                    labelFontSize = resolveFloatingDockLabelFontSize(
                        showIcon = showIcon,
                        showText = showText,
                    ),
                    liquidGlassEffectsEnabled = isLiquidGlassEnabled,
                    miuixBackdrop = miuixBackdrop,
                    liquidGlassPreset = liquidGlassPreset,
                    liquidGlassTuning = resolvedLiquidGlassTuning,
                    indicatorPositionProvider = currentPositionProvider,
                    isScrollInProgressProvider = pagerScrollingProvider,
                    modifier = Modifier
                        .width(floatingDockWidth)
                        .height(floatingDockHeight),
                )
            }
        } else {
        // Match the bottom bar's actual app-surface luminance. The system theme can differ
        // from the active app theme and previously produced a dark gray capture on light pages.
        val isDarkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.background())
        val centerTabsInWideWindow = maxWidth.value >= TOP_TAB_WIDE_CENTER_BREAKPOINT_DP
        val md3ContentPadding = if (wrapDock || isFloatingStyle) {
            if (!wrapDock && centerTabsInWideWindow) {
                resolveMd3TopTabContentPaddingDp(
                    containerWidthDp = maxWidth.value,
                    itemWidthDp = itemWidth.value,
                    categoryCount = categories.size,
                    labelMode = normalizedLabelMode
                ).dp
            } else {
                dockEndInsetDp.dp
            }
        } else if (effectivePresentation != AppTopTabPresentation.MOVING_CAPSULE) {
            resolveMd3TopTabContentPaddingDp(
                containerWidthDp = maxWidth.value,
                itemWidthDp = itemWidth.value,
                categoryCount = categories.size,
                labelMode = normalizedLabelMode
            ).dp
        } else {
            AppSpacingTokens.None
        }
        val topTabHorizontalPadding = when {
            wrapDock -> dockEndInsetDp.dp
            centerTabsInWideWindow -> md3ContentPadding
            isFloatingStyle -> dockEndInsetDp.dp
            effectivePresentation == AppTopTabPresentation.MOVING_CAPSULE ->
                IOS_TOP_TAB_CONTENT_PADDING_DP.dp
            else -> md3ContentPadding
        }
        val textMeasurer = rememberTextMeasurer()
        val md3ContentWidths = remember(categories, normalizedLabelMode, density, textMeasurer) {
            categories.map { category ->
                val textWidth = if (showText) {
                    with(density) {
                        textMeasurer.measure(
                            text = category,
                            style = TextStyle(
                                fontSize = resolveTopTabLabelTextSizeSp(normalizedLabelMode).sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1
                        ).size.width.toDp()
                    }
                } else {
                    0.dp
                }
                val iconWidth = if (showIcon) {
                    resolveTopTabIconSizeDp(normalizedLabelMode).dp
                } else {
                    0.dp
                }
                maxOf(textWidth, iconWidth) + AppSpacingTokens.ExtraSmall * 2
            }
        }
        val md3IndicatorWidth = md3ContentWidths.getOrElse(selectedIndex) {
            AppSpacingTokens.ExtraLarge + AppSpacingTokens.ExtraSmall
        }
        val dockIndicatorHorizontalGap = resolveTopTabDockIndicatorHorizontalGapDp(
            hasOuterChromeSurface = hasOuterChromeSurface,
            isLiquidGlassReuseEnabled = isLiquidGlassEnabled
        ).dp
        val dockIndicatorVerticalGap = resolveTopTabDockIndicatorVerticalGapDp(
            hasOuterChromeSurface = hasOuterChromeSurface
        ).dp
        val md3TopTabRowVerticalTranslationPx = with(density) {
            resolveMd3TopTabRowVerticalTranslationDp(
                skinPlainStyle = skinPlainStyle,
                hasOuterChromeSurface = hasOuterChromeSurface
            ).dp.toPx()
        }
        val rowScrollOffsetPx by remember(itemWidth, density, listState) {
            derivedStateOf {
                with(density) {
                    listState.firstVisibleItemIndex * itemWidth.toPx() +
                        listState.firstVisibleItemScrollOffset
                }
            }
        }
        val rowScrollStartPadding = with(density) { (-rowScrollOffsetPx).toDp() }
        HomeTopTabMotionLayer {
        val pagerIsScrolling = pagerScrollingProvider()
        var topTabIndicatorDirectDragActive by remember(pagerState) {
            mutableStateOf(false)
        }
        var topTabIndicatorDirectDragPosition by remember(pagerState, categories.size) {
            mutableFloatStateOf(selectedIndex.toFloat())
        }
        var topTabIndicatorSettlingTarget by remember(pagerState, categories.size) {
            mutableStateOf<Int?>(null)
        }
        val selectedIndexLatest = rememberUpdatedState(selectedIndex)
        val onCategorySelectedLatest = rememberUpdatedState(onCategorySelected)
        val indicatorDragItemWidthPx = with(density) { itemWidth.toPx() }
        val indicatorDragEdgePaddingPx = with(density) { AppSpacingTokens.Medium.toPx() }
        val followIndicatorInViewport: (Float) -> Unit = { position ->
            if (isViewportSyncEnabled) {
                val layoutInfo = listState.layoutInfo
                val currentScrollPx =
                    listState.firstVisibleItemIndex * indicatorDragItemWidthPx +
                        listState.firstVisibleItemScrollOffset.toFloat()
                listState.dispatchRawDelta(
                    resolveScrollableTabIndicatorFollowDeltaPx(
                        indicatorPosition = position,
                        itemWidthPx = indicatorDragItemWidthPx,
                        viewportWidthPx = layoutInfo.viewportSize.width.toFloat(),
                        currentScrollPx = currentScrollPx,
                        contentPaddingPx = with(density) { topTabHorizontalPadding.toPx() },
                        edgePaddingPx = indicatorDragEdgePaddingPx,
                    )
                )
            }
        }
        LaunchedEffect(
            pagerState,
            currentPositionProvider,
            indicatorDragItemWidthPx,
            topTabHorizontalPadding,
            isViewportSyncEnabled,
        ) {
            val activePager = pagerState ?: return@LaunchedEffect
            snapshotFlow {
                if (activePager.isScrollInProgress) currentPositionProvider() else null
            }.collect { position ->
                position?.let(followIndicatorInViewport)
            }
        }
        val indicatorDraggableState = rememberDraggableState { dragAmountPx ->
            if (!topTabIndicatorDirectDragActive || indicatorDragItemWidthPx <= 0f) {
                return@rememberDraggableState
            }
            val nextPosition = (
                topTabIndicatorDirectDragPosition + dragAmountPx / indicatorDragItemWidthPx
            ).coerceIn(0f, categories.lastIndex.coerceAtLeast(0).toFloat())
            topTabIndicatorDirectDragPosition = nextPosition
            followIndicatorInViewport(nextPosition)
        }
        LaunchedEffect(pagerState, topTabIndicatorSettlingTarget, categories.size) {
            val activePager = pagerState ?: return@LaunchedEffect
            val target = topTabIndicatorSettlingTarget ?: return@LaunchedEffect
            withTimeoutOrNull(TOP_TAB_INDICATOR_SETTLE_TIMEOUT_MILLIS) {
                snapshotFlow {
                    Triple(
                        activePager.currentPage,
                        activePager.currentPageOffsetFraction,
                        activePager.isScrollInProgress,
                    )
                }.first { (page, offset, scrolling) ->
                    !scrolling && page == target && abs(offset) < 0.001f
                }
            }
            topTabIndicatorSettlingTarget = null
        }
        val currentPositionFromPager = currentPositionProvider()
        val selectedContentPositionFromPager = selectedContentPositionProvider()
        val topTabIndicatorOwnsPosition = topTabIndicatorDirectDragActive ||
            topTabIndicatorSettlingTarget != null
        val currentPosition = if (topTabIndicatorOwnsPosition) {
            topTabIndicatorDirectDragPosition
        } else {
            currentPositionFromPager
        }
        val selectedContentPosition = if (topTabIndicatorOwnsPosition) {
            topTabIndicatorDirectDragPosition
        } else {
            selectedContentPositionFromPager
        }
        val topTabIndicatorPosition = currentPosition
        val topTabContentPosition = if (effectivePresentation == AppTopTabPresentation.MOVING_CAPSULE) {
            selectedContentPosition
        } else {
            currentPosition
        }
        val iosCapsulePosition = selectedContentPosition
        val indicatorIsInteracting = pagerIsDragging || pagerIsScrolling ||
            topTabIndicatorDirectDragActive
        val topTabShouldStretchIndicator = shouldDeformTopTabIndicator(
            position = topTabIndicatorPosition,
            isInMotion = indicatorIsInteracting
        )
        val topTabVelocityPositionTracker = remember { FloatArray(1) { topTabIndicatorPosition } }
        val topTabVelocityTimeTracker = remember { LongArray(1) { System.nanoTime() } }
        val topTabPagerVelocityItemsPerSecond = resolveTopTabPagerVelocityItemsPerSecond(
            currentPosition = topTabIndicatorPosition,
            previousPosition = topTabVelocityPositionTracker[0],
            elapsedNanos = (System.nanoTime() - topTabVelocityTimeTracker[0]).coerceAtLeast(1L)
        )
        SideEffect {
            topTabVelocityPositionTracker[0] = topTabIndicatorPosition
            topTabVelocityTimeTracker[0] = System.nanoTime()
        }
        val topTabMotionVelocityItemsPerSecond = topTabPagerVelocityItemsPerSecond
        val topTabMotionVelocityPxPerSecond = with(density) {
            topTabMotionVelocityItemsPerSecond * itemWidth.toPx()
        }
        val topTabIndicatorInteractionSource = remember { MutableInteractionSource() }
        val topTabIndicatorPressed by topTabIndicatorInteractionSource.collectIsPressedAsState()
        // 照搬 HyperIsland LiquidGlassNavigationBar：指示器放大只由这一条 scale 弹簧驱动
        // （spring(0.6f, 250f, 0.001f)），拖拽、翻页与按下都会启动它。
        val topTabIndicatorScaleProgress = rememberBottomBarIndicatorDragScaleProgress(
            isDragging = topTabShouldStretchIndicator || topTabIndicatorPressed
        )
        val topTabPressProgress = rememberBottomBarIndicatorDragScaleProgress(
            isDragging = topTabIndicatorPressed
        )
        val topTabIndicatorLayerScaleProgress = resolveTopTabIndicatorScaleProgress(
            dragScaleProgress = topTabIndicatorScaleProgress,
            pressProgress = topTabPressProgress
        )
        val topTabRefractionMotionProfile = resolveBottomBarRefractionMotionProfile(
            position = topTabIndicatorPosition,
            velocity = topTabMotionVelocityPxPerSecond,
            isDragging = indicatorIsInteracting,
            motionSpec = topTabMotionSpec
        )
        val rawTopTabPanelOffsetPx = resolveTopTabMatchedPanelOffsetPx(
            dragPanelOffsetPx = 0f,
            pagerPanelOffsetFraction = topTabRefractionMotionProfile.indicatorPanelOffsetFraction,
            maxOffsetPx = with(density) { AppSpacingTokens.ExtraSmall.toPx() },
            dragActive = false
        )
        val topTabPanelOffsetPx = resolveTopTabEdgeAwarePanelOffsetPx(
            position = topTabIndicatorPosition,
            lastIndex = categories.lastIndex,
            panelOffsetPx = rawTopTabPanelOffsetPx,
        )
        // Pager swipes have no direct press event. Reuse the bottom-bar drag-scale animation
        // as their effective press so the indicator surface fades and lens ramps identically.
        val topTabLensProgress = topTabIndicatorLayerScaleProgress
        val md3LiquidCapsuleWidth = resolveTopTabInterpolatedIndicatorWidthDp(
            position = topTabIndicatorPosition,
            itemWidthDp = itemWidth.value,
            horizontalGapDp = dockIndicatorHorizontalGap.value,
            contentWidthsDp = md3ContentWidths.map { it.value },
        ).dp
        val dockIndicatorHeight = resolveTopTabDockIndicatorHeightDp(
            rowHeightDp = rowHeight.value,
            verticalGapDp = dockIndicatorVerticalGap.value,
            minHeightDp = com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorHeightDp(
                rowHeight.value
            ),
            indicatorWidthDp = md3LiquidCapsuleWidth.value
        ).dp
        val topTabIndicatorGeometry = remember(rowHeight, dockIndicatorHeight) {
            com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorGeometry(
                dockHeightDp = rowHeight.value,
                indicatorHeightDp = dockIndicatorHeight.value,
            )
        }
        val topTabIndicatorLayerTransform = resolveTopTabIndicatorLayerTransform(
            motionProgress = topTabIndicatorScaleProgress,
            velocityItemsPerSecond = topTabMotionVelocityItemsPerSecond,
            dragScaleTarget = topTabIndicatorGeometry.pressedScale,
            dragScaleTransform = rememberBottomBarIndicatorLayerScaleTransform(
                active = topTabShouldStretchIndicator || topTabIndicatorPressed,
                target = topTabIndicatorGeometry.pressedScale
            ),
            motionSpec = topTabMotionSpec
        )
        // Selected-tab pill position: item slot center minus half the pill width, so the
        // capsule follows the pager offset and the row scroll while staying inside the dock.
        val md3IndicatorTranslationXPx by remember(
            topTabIndicatorPosition,
            itemWidth,
            md3LiquidCapsuleWidth,
            density,
            listState
        ) {
            derivedStateOf {
                with(density) {
                    resolveMd3TopTabIndicatorTranslationPx(
                        absolutePagerPosition = topTabIndicatorPosition,
                        itemWidthPx = itemWidth.toPx(),
                        rowScrollOffsetPx = rowScrollOffsetPx,
                        indicatorWidthPx = md3LiquidCapsuleWidth.toPx(),
                        contentPaddingPx = md3ContentPadding.toPx()
                    )
                }
            }
        }
        val shouldUseMovingIosCapsule = effectivePresentation == AppTopTabPresentation.MOVING_CAPSULE &&
            !skinPlainStyle &&
            !hasSkinStickerIcons
        val shouldUseLiquidGlassIndicator = isLiquidGlassEnabled &&
            !skinPlainStyle &&
            !hasSkinStickerIcons
        val homeSelectionIndicatorStyle = resolveHomeSelectionIndicatorStyle(
            uiStyle = LocalAppUiStyle.current,
            liquidGlassEnabled = isLiquidGlassEnabled,
            forceMaterialUnderline = forceMaterialUnderline,
        )
        val shouldUseHomeCapsule =
            homeSelectionIndicatorStyle == HomeSelectionIndicatorStyle.CAPSULE
        // 玻璃开启或 Miuix 主题使用胶囊；仅 Material3 的非玻璃路径使用短下划线。
        val shouldUseMd3LiquidCapsule = effectivePresentation == AppTopTabPresentation.MATERIAL_UNDERLINE &&
            !skinPlainStyle &&
            !hasSkinStickerIcons &&
            shouldUseHomeCapsule &&
            !hasOuterChromeSurface
        val shouldUseMd3DockBackedCapsule = effectivePresentation == AppTopTabPresentation.MATERIAL_UNDERLINE &&
            !skinPlainStyle &&
            !hasSkinStickerIcons &&
            shouldUseHomeCapsule &&
            hasOuterChromeSurface
        val shouldUseMd3NativeUnderline = effectivePresentation == AppTopTabPresentation.MATERIAL_UNDERLINE &&
            !hasSkinStickerIcons &&
            !shouldUseMd3DockBackedCapsule &&
            !shouldUseMd3LiquidCapsule
        val shouldPrimeTopTabLiquidGlassCapture =
            isLiquidGlassEnabled &&
                !skinPlainStyle &&
                !hasSkinStickerIcons
        // Miuix-only capture (no legacy dual path).
        val topTabMiuixContentBackdrop = rememberMiuixLayerBackdrop()
        val topTabIndicatorVisualPolicy = resolveTopTabIndicatorVisualPolicy(
            position = topTabIndicatorPosition,
            interacting = indicatorIsInteracting,
            velocityPxPerSecond = topTabMotionVelocityPxPerSecond,
            useNeutralIndicatorTint = shouldUseLiquidGlassIndicator
        )
        val topTabIndicatorBackdropPolicy = resolveTopTabIndicatorBackdropPolicy(
            effectiveLiquidGlassEnabled = shouldUseLiquidGlassIndicator,
            hasBackdrop = miuixBackdrop != null,
            indicatorVisualPolicy = topTabIndicatorVisualPolicy
        )
        // Match the bottom bar's two-source topology. The local source first records the
        // already-frosted dock material and tinted labels, so the indicator never falls
        // back to a raw-page-only frame during idle/gesture transitions.
        val effectiveTopTabMiuixContentBackdrop =
            if (topTabIndicatorBackdropPolicy.useCombinedBackdrop && miuixBackdrop != null) {
                rememberMiuixCombinedBackdrop(miuixBackdrop, topTabMiuixContentBackdrop)
            } else {
                topTabMiuixContentBackdrop
            }
        val topTabIndicatorCaptureSurfaceColor =
            resolveBiliPaiBottomBarContainerColor(darkTheme = isDarkTheme)
        val useTopTabGlassColorPath = resolveTopTabUsesGlassExportForSelectedGlyphs(
            liquidGlassEnabled = shouldUseLiquidGlassIndicator,
        )
        val topTabVisibleContentZIndex = if (useTopTabGlassColorPath) 0f else 2f
        val topTabThemeColor = MaterialTheme.colorScheme.primary
        val stableTopTabExportTintColor = resolveAndroidNativeExportTintColor(
            themeColor = topTabThemeColor,
            darkTheme = isDarkTheme
        )
        // Adaptive readability only changes neutral, unselected content. The moving
        // indicator keeps the app theme tint so enabling adaptation cannot erase it.
        val topTabExportTintColor = stableTopTabExportTintColor
        val topTabExportMonochromeColor = resolveSharedLiquidExportMonochromeColor(
            darkTheme = isDarkTheme
        )
        val measuredSelectedItemLeftPx by remember(shouldUseMovingIosCapsule) {
            derivedStateOf {
                if (!shouldUseMovingIosCapsule ||
                    tabViewportLeftInWindowPx.isNaN() ||
                    selectedItemLeftInWindowPx.isNaN()
                ) {
                    null
                } else {
                    selectedItemLeftInWindowPx - tabViewportLeftInWindowPx
                }
            }
        }
        val iosCapsuleTargetTranslationXPx by remember(
            iosCapsulePosition,
            measuredSelectedItemLeftPx,
            itemWidth,
            density,
            rowScrollOffsetPx,
            pagerState,
            pagerIsDragging
        ) {
            derivedStateOf {
                with(density) {
                    resolveIosTopTabCapsuleTargetTranslationPx(
                        measuredSelectedItemLeftPx = measuredSelectedItemLeftPx,
                        absolutePagerPosition = iosCapsulePosition,
                        itemWidthPx = itemWidth.toPx(),
                        rowScrollOffsetPx = rowScrollOffsetPx,
                        contentPaddingPx = topTabHorizontalPadding.toPx(),
                        followPagerPosition = pagerIsDragging || pagerIsScrolling ||
                            topTabIndicatorOwnsPosition
                    )
                }
            }
        }
        val shouldAnimateIosCapsule = shouldAnimateIosTopTabCapsule(
            pagerIsDragging = pagerIsDragging || topTabIndicatorOwnsPosition,
            pagerIsScrolling = pagerIsScrolling
        )
        val animatedIosCapsuleTranslationXPx by animateFloatAsState(
            targetValue = iosCapsuleTargetTranslationXPx,
            // Pager/indicator drags already provide a continuous position. Keep the backing
            // animation snapped to that position while they own motion, otherwise switching
            // back to the animated value can expose a second, delayed settle.
            animationSpec = if (shouldAnimateIosCapsule) {
                iosTopTabCapsuleMotionSpec()
            } else {
                snap()
            },
            label = "iosTopTabCapsuleTranslation"
        )
        val iosCapsuleTranslationXPx = if (shouldAnimateIosCapsule) {
            animatedIosCapsuleTranslationXPx
        } else {
            iosCapsuleTargetTranslationXPx
        }
        var md3UnderlineTargetIndex by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(selectedIndex) {
            if (selectedIndex == md3UnderlineTargetIndex) {
                md3UnderlineTargetIndex = null
            }
        }
        if (pagerIsDragging || topTabIndicatorOwnsPosition) {
            md3UnderlineTargetIndex = null
        }
        val safeMd3TargetIndex = (md3UnderlineTargetIndex ?: pagerState?.targetPage ?: selectedIndex)
            .coerceIn(categories.indices)
        val previousMd3TargetIndex = remember { mutableIntStateOf(selectedIndex) }
        val movingRight = remember(safeMd3TargetIndex) {
            safeMd3TargetIndex >= previousMd3TargetIndex.intValue
        }
        val targetIndicatorWidthPx = with(density) {
            if (showIcon && !showText) {
                resolveIconOnlyTopTabIndicatorWidth().toPx()
            } else {
                md3ContentWidths.getOrElse(safeMd3TargetIndex) { md3IndicatorWidth }.toPx()
            }
        }
        val targetBounds = resolveMd3TopTabTargetBounds(
            targetIndex = safeMd3TargetIndex,
            itemWidthPx = with(density) { itemWidth.toPx() },
            indicatorWidthPx = targetIndicatorWidthPx,
            contentPaddingPx = with(density) { md3ContentPadding.toPx() }
        )
        val shouldAnimateMd3Tap = shouldUseMd3NativeUnderline && shouldAnimateMd3TopTabUnderline(
            pagerIsDragging = pagerIsDragging,
            topTabIndicatorOwnsPosition = topTabIndicatorOwnsPosition
        )
        val isMiuixUnderline = LocalAppUiStyle.current == AppUiStyle.MIUIX
        val leftAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = if (shouldAnimateMd3Tap) {
            if (isMiuixUnderline) {
                spring(
                    dampingRatio = if (movingRight) 0.68f else 0.78f,
                    stiffness = if (movingRight) Spring.StiffnessMedium else Spring.StiffnessLow
                )
            } else {
                tween(
                    durationMillis = MD3_TOP_TAB_INDICATOR_DURATION_MILLIS,
                    easing = if (movingRight) Md3TopTabIndicatorAccelerate else Md3TopTabIndicatorDecelerate
                )
            }
        } else {
            snap()
        }
        val rightAnimationSpec: androidx.compose.animation.core.AnimationSpec<Float> = if (shouldAnimateMd3Tap) {
            if (isMiuixUnderline) {
                spring(
                    dampingRatio = if (movingRight) 0.78f else 0.68f,
                    stiffness = if (movingRight) Spring.StiffnessLow else Spring.StiffnessMedium
                )
            } else {
                tween(
                    durationMillis = MD3_TOP_TAB_INDICATOR_DURATION_MILLIS,
                    easing = if (movingRight) Md3TopTabIndicatorDecelerate else Md3TopTabIndicatorAccelerate
                )
            }
        } else {
            snap()
        }
        val animatedMd3UnderlineLeftPx by animateFloatAsState(
            targetValue = targetBounds.leftPx,
            animationSpec = leftAnimationSpec,
            label = "md3UnderlineLeft"
        )
        val animatedMd3UnderlineRightPx by animateFloatAsState(
            targetValue = targetBounds.rightPx,
            animationSpec = rightAnimationSpec,
            label = "md3UnderlineRight"
        )
        SideEffect {
            previousMd3TargetIndex.intValue = safeMd3TargetIndex
        }
        val effectiveTopTabContentPosition = if (shouldUseMd3NativeUnderline && shouldAnimateMd3Tap) {
            resolveMd3TopTabTapContentPosition(
                animatedLeftPx = animatedMd3UnderlineLeftPx,
                animatedRightPx = animatedMd3UnderlineRightPx,
                itemWidthPx = with(density) { itemWidth.toPx() },
                contentPaddingPx = with(density) { md3ContentPadding.toPx() },
                fallbackIndex = safeMd3TargetIndex,
                categoryCount = categories.size
            )
        } else {
            topTabContentPosition
        }
        Row(
            modifier = Modifier
                .then(
                    if (wrapDock) {
                        // A compact dock remains content-sized but is centered in wide chrome.
                        Modifier
                            .width(dockContentWidthDp.dp)
                            .fillMaxHeight()
                            .align(Alignment.Center)
                    } else {
                        Modifier.fillMaxSize()
                    }
                )
                .graphicsLayer {
                    translationY = if (effectivePresentation == AppTopTabPresentation.MATERIAL_UNDERLINE) {
                        md3TopTabRowVerticalTranslationPx
                    } else {
                        0f
                    }
                }
                .trackLiquidGlassAdaptiveReadability(
                    state = adaptiveReadabilityState,
                    enabled = adaptiveReadabilityEnabled,
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onGloballyPositioned { coordinates ->
                        tabViewportLeftInWindowPx = coordinates.boundsInWindow().left
                    }
            ) {
                val topTabContentPadding = PaddingValues(horizontal = topTabHorizontalPadding)
                // Read LazyRow motion from the layer phase so the hidden export and visible row
                // are transformed in the same frame without scroll-driven recomposition.
                val topTabListScrollOffsetPxProvider = {
                    with(density) {
                        listState.firstVisibleItemIndex * itemWidth.toPx() +
                            listState.firstVisibleItemScrollOffset.toFloat()
                    }
                }
                val topTabIndicatorPanelOffsetPx =
                    if (shouldUseLiquidGlassIndicator) topTabPanelOffsetPx else 0f
                val topTabHorizontalPaddingPx = with(density) { topTabHorizontalPadding.toPx() }
                // Keep the sampled and visible labels fixed. Moving this whole group makes the
                // tab strip rebound with the indicator and desynchronizes backdrop sampling from
                // LazyRow's own gesture transform. Only the indicator receives the liquid offset.
                Box(modifier = Modifier.fillMaxSize()) {
                // Match the bottom bar: keep the export capture inside the dock band.
                // When the indicator scales beyond it, the combined backdrop exposes the
                // page source above and below instead of stretching dock material outward.
                // Capture layer: BiliPai-style tabsBackdrop (export-tinted glyphs under glass).
                if (shouldPrimeTopTabLiquidGlassCapture) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clearAndSetSemantics {}
                            .alpha(0f)
                            .zIndex(0f)
                            .run {
                                if (miuixBackdrop != null) {
                                    miuixLayerBackdrop(topTabMiuixContentBackdrop)
                                        .graphicsLayer {
                                            // Only mirror LazyRow content origin (padding - scroll).
                                            translationX =
                                                topTabHorizontalPaddingPx -
                                                    topTabListScrollOffsetPxProvider()
                                        }
                                        .biliPaiFloatingDockCaptureSurface(
                                            backdrop = miuixBackdrop,
                                            containerColor = topTabIndicatorCaptureSurfaceColor,
                                            shape = resolveTopTabIndicatorShape(showIcon = showIcon, showText = showText),
                                            liquidGlassTuning = resolvedLiquidGlassTuning,
                                        )
                                } else {
                                    // No page backdrop: still record local tint layer for indicator.
                                    miuixLayerBackdrop(topTabMiuixContentBackdrop)
                                        .graphicsLayer {
                                            translationX =
                                                topTabHorizontalPaddingPx -
                                                    topTabListScrollOffsetPxProvider()
                                        }
                                        .background(topTabIndicatorCaptureSurfaceColor)
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            categories.forEachIndexed { index, category ->
                                val categoryKey = categoryKeys.getOrNull(index) ?: category
                                LightweightTopTabItem(
                                    presentation = effectivePresentation,
                                    iconFamily = topTabIconFamily,
                                    category = category,
                                    categoryKey = categoryKey,
                                    index = index,
                                    selectionFraction = 1f,
                                    selectedIndex = selectedIndex,
                                    showIcon = showIcon,
                                    showText = showText,
                                    itemWidth = itemWidth,
                                    skinPlainStyle = false,
                                    drawContainer = false,
                                    skinIconPaths = null,
                                    hasSkinStickerIcon = false,
                                    useClickIndication = false,
                                    colorMode = TopTabLiquidColorMode.GLASS_EXPORT,
                                    exportMonochromeColor = topTabExportMonochromeColor,
                                    modifier = Modifier.graphicsLayer(
                                        colorFilter = ColorFilter.tint(topTabExportTintColor)
                                    ),
                                    onClick = {}
                                )
                            }
                        }
                    }
                }
                LazyRow(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        // At rest, keep glyphs visible above the idle capsule. During the
                        // glass motion path they must sit below it, so exported tint moves
                        // with the indicator instead of leaving the old glyph color on top.
                        .zIndex(topTabVisibleContentZIndex),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    // Stretch overscroll transforms only the visible LazyRow, not the hidden
                    // backdrop export row. That mismatch duplicates selected glyphs and makes the
                    // entire top strip shake at its bounds, so this chrome has no edge rebound.
                    overscrollEffect = null,
                    contentPadding = topTabContentPadding
                ) {
                    itemsIndexed(
                        items = categories,
                        key = { index, category -> categoryKeys.getOrNull(index) ?: category }
                    ) { index, category ->
                        val categoryKey = categoryKeys.getOrNull(index) ?: category
                        val selectionFraction = (1f - abs(effectiveTopTabContentPosition - index.toFloat())).coerceIn(0f, 1f)
                        val drawItemContainer = shouldDrawLightweightTopTabItemContainer(
                            presentation = effectivePresentation,
                            skinPlainStyle = skinPlainStyle,
                            hasSkinStickerIcon = hasSkinStickerIcons
                        )
                        val measuredItemModifier = if (shouldUseMovingIosCapsule && index == selectedIndex) {
                            Modifier.onGloballyPositioned { coordinates ->
                                selectedItemLeftInWindowPx = coordinates.boundsInWindow().left
                            }
                        } else {
                            Modifier
                        }
                        LightweightTopTabItem(
                            presentation = effectivePresentation,
                            iconFamily = topTabIconFamily,
                            category = category,
                            categoryKey = categoryKey,
                            index = index,
                            selectionFraction = selectionFraction,
                            selectedIndex = selectedIndex,
                            showIcon = showIcon,
                            showText = showText,
                            itemWidth = itemWidth,
                            skinPlainStyle = skinPlainStyle,
                            skinPlainContentColor = skinPlainContentColor,
                            drawContainer = drawItemContainer,
                            skinIconPaths = topTabSkinIconPaths[categoryKey.trim().uppercase()],
                            hasSkinStickerIcon = hasSkinStickerIcons,
                            useClickIndication = shouldUseLightweightTopTabItemClickIndication(
                                presentation = effectivePresentation,
                                skinPlainStyle = skinPlainStyle,
                                usesCapsuleIndicator = shouldUseMovingIosCapsule ||
                                    shouldUseMd3LiquidCapsule ||
                                    shouldUseMd3DockBackedCapsule
                            ),
                            // Glass path: neutral glyphs under the BiliPai indicator.
                            colorMode = if (useTopTabGlassColorPath) {
                                TopTabLiquidColorMode.GLASS_VISIBLE
                            } else {
                                TopTabLiquidColorMode.NORMAL
                            },
                            adaptiveContentColorOverride = adaptiveTopTabContentColor
                                .takeIf { adaptiveReadabilityEnabled },
                            modifier = measuredItemModifier.graphicsLayer {
                                alpha = resolveTopTabVisibleContentAlpha(
                                    useGlassColorPath = useTopTabGlassColorPath,
                                    selectionFraction = selectionFraction,
                                )
                            },
                            onClick = {
                                performHomeTopBarTap(haptic = haptic, onClick = {
                                    when (resolveTopTabClickAction(index, selectedIndex)) {
                                        TopTabClickAction.SELECT_TAB -> {
                                            md3UnderlineTargetIndex = index
                                            onCategorySelected(index)
                                        }
                                        TopTabClickAction.SCROLL_TO_TOP -> scrollChannel?.trySend(
                                            com.android.purebilibili.feature.home.HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH
                                        )
                                    }
                                })
                            }
                        )
                    }
                }
                val indicatorGestureWidth = if (shouldUseMovingIosCapsule) {
                    resolveTopTabDockIndicatorWidthDp(
                        itemWidthDp = itemWidth.value,
                        horizontalGapDp = dockIndicatorHorizontalGap.value
                    ).dp
                } else {
                    md3LiquidCapsuleWidth
                }
                val indicatorGestureTranslationXPx = if (shouldUseMovingIosCapsule) {
                    resolveTopTabDockIndicatorOffsetPx(
                        slotTranslationPx = iosCapsuleTranslationXPx,
                        horizontalGapPx = with(density) { dockIndicatorHorizontalGap.toPx() }
                    )
                } else {
                    md3IndicatorTranslationXPx
                }
                val indicatorGestureVisible = shouldUseMovingIosCapsule ||
                    shouldUseMd3DockBackedCapsule ||
                    shouldUseMd3LiquidCapsule
                val indicatorDragModifier = if (pagerState != null && categories.size > 1) {
                    Modifier.draggable(
                        state = indicatorDraggableState,
                        orientation = Orientation.Horizontal,
                        onDragStarted = {
                            topTabIndicatorSettlingTarget = null
                            topTabIndicatorDirectDragPosition = currentPositionProvider()
                                .coerceIn(0f, categories.lastIndex.toFloat())
                            topTabIndicatorDirectDragActive = true
                        },
                        onDragStopped = {
                            val targetIndex = resolveTopTabIndicatorDragTargetIndex(
                                position = topTabIndicatorDirectDragPosition,
                                itemCount = categories.size,
                            )
                            topTabIndicatorDirectDragPosition = targetIndex.toFloat()
                            topTabIndicatorDirectDragActive = false
                            if (targetIndex != selectedIndexLatest.value) {
                                haptic(HapticType.SELECTION)
                                topTabIndicatorSettlingTarget = targetIndex
                                onCategorySelectedLatest.value(targetIndex)
                            }
                        },
                    )
                } else {
                    Modifier
                }
                val indicatorGestureModifier = Modifier
                    .clickable(
                        interactionSource = topTabIndicatorInteractionSource,
                        indication = null
                    ) {
                        performHomeTopBarTap(
                            haptic = haptic,
                            onClick = {
                                scrollChannel?.trySend(
                                    com.android.purebilibili.feature.home.HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH
                                )
                            }
                        )
                    }
                    .clearAndSetSemantics {}
                // Keep the indicator between its capture layer and the visible tab content.
                // The indicator owns its panel offset; clip=false lets its bottom-bar motion
                // transform exceed the dock chrome without moving the label/capture layers.
                // Inner moving indicator — same BiliPai stack as FloatingBottomBar indicator.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .graphicsLayer { clip = false }
                ) {
                    val indicatorCombinedBackdrop =
                        if (shouldUseLiquidGlassIndicator) effectiveTopTabMiuixContentBackdrop else null
                    val indicatorScaleX = topTabIndicatorLayerTransform.scaleX
                    val indicatorScaleY = topTabIndicatorLayerTransform.scaleY
                    // Velocity stretch is already folded into the shared bottom-bar transform.
                    // Keep the BiliPai layer velocity neutral to avoid applying it twice.
                    val indicatorVelocity = 0f
                    if (shouldUseMovingIosCapsule) {
                        val indicatorWidth = resolveTopTabDockIndicatorWidthDp(
                            itemWidthDp = itemWidth.value,
                            horizontalGapDp = dockIndicatorHorizontalGap.value
                        ).dp
                        BiliPaiFloatingDockIndicator(
                            visible = true,
                            translationXPx = resolveTopTabDockIndicatorOffsetPx(
                                slotTranslationPx = iosCapsuleTranslationXPx,
                                horizontalGapPx = with(density) {
                                    dockIndicatorHorizontalGap.toPx()
                                }
                            ),
                            panelOffsetPx = topTabIndicatorPanelOffsetPx,
                            width = indicatorWidth,
                            height = dockIndicatorHeight,
                            combinedBackdrop = indicatorCombinedBackdrop,
                            pressProgress = topTabLensProgress,
                            scaleX = indicatorScaleX,
                            scaleY = indicatorScaleY,
                            velocity = indicatorVelocity,
                            isDark = isDarkTheme,
                            shape = resolveTopTabIndicatorShape(showIcon = showIcon, showText = showText),
                            liquidGlassTuning = resolvedLiquidGlassTuning
                        )
                    }
                    if (shouldUseMd3DockBackedCapsule) {
                        BiliPaiFloatingDockIndicator(
                            visible = true,
                            translationXPx = md3IndicatorTranslationXPx,
                            panelOffsetPx = topTabIndicatorPanelOffsetPx,
                            width = md3LiquidCapsuleWidth,
                            height = dockIndicatorHeight,
                            combinedBackdrop = indicatorCombinedBackdrop,
                            pressProgress = topTabLensProgress,
                            scaleX = indicatorScaleX,
                            scaleY = indicatorScaleY,
                            velocity = indicatorVelocity,
                            isDark = isDarkTheme,
                            shape = resolveTopTabIndicatorShape(showIcon = showIcon, showText = showText),
                            liquidGlassTuning = resolvedLiquidGlassTuning
                        )
                    }
                    if (shouldUseMd3LiquidCapsule) {
                        BiliPaiFloatingDockIndicator(
                            visible = true,
                            translationXPx = md3IndicatorTranslationXPx,
                            panelOffsetPx = topTabIndicatorPanelOffsetPx,
                            width = md3LiquidCapsuleWidth,
                            height = dockIndicatorHeight,
                            combinedBackdrop = indicatorCombinedBackdrop,
                            pressProgress = topTabLensProgress,
                            scaleX = indicatorScaleX,
                            scaleY = indicatorScaleY,
                            velocity = indicatorVelocity,
                            isDark = isDarkTheme,
                            shape = resolveTopTabIndicatorShape(showIcon = showIcon, showText = showText),
                            liquidGlassTuning = resolvedLiquidGlassTuning
                        )
                    }
                }
                if (indicatorGestureVisible) {
                    // 透明层同时承接点击回顶与胶囊直拖；普通页面区域仍由 Pager 接管侧滑。
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .graphicsLayer {
                                translationX = indicatorGestureTranslationXPx +
                                    topTabIndicatorPanelOffsetPx
                            }
                            .width(indicatorGestureWidth)
                            .height(dockIndicatorHeight)
                            .zIndex(3f)
                            .then(indicatorGestureModifier)
                            .then(indicatorDragModifier)
                    )
                }
                } // stable export + visible content with indicator-only motion

                // 非玻璃 MD3 与皮肤顶栏使用单层短指示线，始终位于内容底部居中。
                if (shouldUseMd3NativeUnderline) {
                    val indicatorColor = when {
                        skinPlainContentColor != null -> resolveHomeSkinTopTabIndicatorColor(skinPlainContentColor)
                        LocalAppUiStyle.current == AppUiStyle.MIUIX -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.primary
                    }
                    val iconOnlyIndicator = showIcon && !showText
                    val nativeIndicatorWidth = if (iconOnlyIndicator) {
                        resolveIconOnlyTopTabIndicatorWidth()
                    } else {
                        val clampedPosition = topTabIndicatorPosition
                            .coerceIn(0f, categories.lastIndex.toFloat())
                        val startIndex = clampedPosition.toInt()
                        val endIndex = (startIndex + 1).coerceAtMost(categories.lastIndex)
                        lerp(
                            md3ContentWidths.getOrElse(startIndex) { md3IndicatorWidth }.value,
                            md3ContentWidths.getOrElse(endIndex) { md3IndicatorWidth }.value,
                            clampedPosition - startIndex
                        ).dp
                    }
                    val nativeUnderlineBounds = if (shouldAnimateMd3Tap) {
                        resolveMd3TopTabUnderlineTapBounds(
                            animatedLeftPx = animatedMd3UnderlineLeftPx,
                            animatedRightPx = animatedMd3UnderlineRightPx,
                            rowScrollOffsetPx = rowScrollOffsetPx
                        )
                    } else {
                        with(density) {
                            resolveMd3TopTabUnderlineBounds(
                                absolutePagerPosition = topTabIndicatorPosition,
                                itemWidthPx = itemWidth.toPx(),
                                rowScrollOffsetPx = rowScrollOffsetPx,
                                indicatorWidthPx = nativeIndicatorWidth.toPx(),
                                contentPaddingPx = md3ContentPadding.toPx(),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .graphicsLayer {
                                translationX = nativeUnderlineBounds.translationXPx
                            }
                            .offset(y = -AppSpacingTokens.ExtraSmall)
                            .width(with(density) { nativeUnderlineBounds.widthPx.toDp() })
                            .height(AppSpacingTokens.Micro * 1.5f)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(indicatorColor)
                    )
                }
            }

            if (showPartitionAction) {
                Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))

                Box(
                    modifier = Modifier
                        .size(actionButtonSize)
                        .then(
                            if (skinPlainStyle) {
                                Modifier
                            } else {
                                Modifier.clip(RoundedCornerShape(actionButtonCorner))
                            }
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = LocalIndication.current
                        ) {
                            performHomeTopBarTap(haptic = haptic, onClick = onPartitionClick)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (!partitionSkinIconPath.isNullOrBlank()) {
                        AsyncImage(
                            model = File(partitionSkinIconPath),
                            contentDescription = "浏览全部分区",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(resolveTopTabSkinPartitionIconSize())
                        )
                    } else {
                        AppIcon(
                            resolveTopTabPartitionIcon(topTabIconFamily),
                            contentDescription = "浏览全部分区",
                            tint = skinPlainContentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(actionIconSize)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall + AppSpacingTokens.Micro))
            }
        }
        }
        }
    }
}

@Composable
private fun HomeTopTabMotionLayer(
    content: @Composable () -> Unit
) {
    content()
}

internal enum class TopTabLiquidColorMode {
    /** Normal selected/unselected lerp. */
    NORMAL,
    /** Visible layer while glass is sliding — neutral so theme color lives under glass. */
    GLASS_VISIBLE,
    /** Hidden export layer monochrome glyphs before theme ColorFilter.tint. */
    GLASS_EXPORT
}

internal fun resolveTopTabVisibleContentAlpha(
    useGlassColorPath: Boolean,
    selectionFraction: Float,
): Float = if (useGlassColorPath) {
    1f - selectionFraction.coerceIn(0f, 1f)
} else {
    1f
}

/**
 * Liquid glass keeps selected icon+text on the export/indicator layer. The visible
 * row must fade that slot even at rest; otherwise the last tab's end-cap samples a
 * second copy (文字和图标都会重影).
 */
internal fun resolveTopTabUsesGlassExportForSelectedGlyphs(
    liquidGlassEnabled: Boolean,
): Boolean = liquidGlassEnabled

@Composable
private fun LightweightTopTabItem(
    presentation: AppTopTabPresentation,
    iconFamily: AppSemanticIconFamily,
    category: String,
    categoryKey: String,
    index: Int,
    selectionFraction: Float,
    selectedIndex: Int,
    showIcon: Boolean,
    showText: Boolean,
    itemWidth: Dp,
    skinPlainStyle: Boolean = false,
    skinPlainContentColor: Color? = null,
    drawContainer: Boolean = true,
    skinIconPaths: TopTabSkinIconPaths? = null,
    hasSkinStickerIcon: Boolean = false,
    useClickIndication: Boolean = true,
    colorMode: TopTabLiquidColorMode = TopTabLiquidColorMode.NORMAL,
    exportMonochromeColor: Color = OpticalContrastPalette.Highlight,
    adaptiveContentColorOverride: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val uiStyle = LocalAppUiStyle.current
    val isDarkTheme = resolveBottomBarDarkTheme(AppSurfaceTokens.background())
    val selected = selectionFraction > 0.5f || index == selectedIndex
    val skinIconPath = skinIconPaths?.pathFor(selected)
    val unselectedIcon = resolveTopTabCategoryIcon(
        categoryKey = categoryKey,
        iconFamily = iconFamily,
        selected = false
    )
    val selectedIcon = resolveTopTabCategoryIcon(
        categoryKey = categoryKey,
        iconFamily = iconFamily,
        selected = true
    )
    val selectedColor = when {
        skinPlainStyle -> skinPlainContentColor ?: colorScheme.onSurface
        uiStyle == AppUiStyle.MIUIX -> colorScheme.onSurface
        presentation == AppTopTabPresentation.MOVING_CAPSULE ->
            resolveIosTopTabSelectedContentColor(colorScheme, uiStyle)
        presentation == AppTopTabPresentation.MATERIAL_UNDERLINE ->
            resolveMd3TopTabSelectedLabelColor(colorScheme, presentation, uiStyle)
        presentation == AppTopTabPresentation.TONAL_CAPSULE ->
            resolveMd3TopTabSelectedLabelColor(colorScheme, presentation, uiStyle)
        else -> colorScheme.primary
    }
    val unselectedColor = if (skinPlainStyle) {
        resolveHomeSkinTopTabUnselectedContentColor(skinPlainContentColor ?: colorScheme.onSurface)
    } else {
        colorScheme.onSurfaceVariant
    }
    val contentColor = when (colorMode) {
        TopTabLiquidColorMode.GLASS_EXPORT -> exportMonochromeColor
        TopTabLiquidColorMode.GLASS_VISIBLE -> adaptiveContentColorOverride ?: unselectedColor
        TopTabLiquidColorMode.NORMAL -> androidx.compose.ui.graphics.lerp(
            adaptiveContentColorOverride ?: unselectedColor,
            selectedColor,
            selectionFraction
        )
    }
    val containerColor = when {
        !drawContainer || colorMode == TopTabLiquidColorMode.GLASS_EXPORT -> Color.Transparent
        skinPlainStyle -> Color.Transparent
        presentation == AppTopTabPresentation.MOVING_CAPSULE && colorMode == TopTabLiquidColorMode.NORMAL ->
            resolveIosTopTabCapsuleContainerColor(
                isDarkTheme = isDarkTheme,
                selectionFraction = selectionFraction
            )
        presentation == AppTopTabPresentation.MATERIAL_UNDERLINE -> Color.Transparent
        // TONAL_CAPSULE is retained only as an input compatibility value; never restore
        // its old per-item selected background.
        presentation == AppTopTabPresentation.TONAL_CAPSULE -> Color.Transparent
        colorMode == TopTabLiquidColorMode.GLASS_VISIBLE -> Color.Transparent
        else -> Color.Transparent
    }
    val itemShape = when {
        skinPlainStyle -> androidx.compose.ui.graphics.RectangleShape
        presentation == AppTopTabPresentation.MOVING_CAPSULE -> resolveSharedBottomBarCapsuleShape()
        presentation == AppTopTabPresentation.MATERIAL_UNDERLINE -> androidx.compose.ui.graphics.RectangleShape
        else -> androidx.compose.ui.graphics.RectangleShape
    }
    // Compact dock: keep side padding small so five tabs do not collapse to "...".
    val itemContentHorizontalPadding = AppSpacingTokens.ExtraSmall

    Box(
        modifier = modifier
            .width(itemWidth)
            .fillMaxHeight()
            .padding(
                horizontal = AppSpacingTokens.ExtraSmall - AppSpacingTokens.Micro / 2,
                vertical = if (hasSkinStickerIcon) {
                    resolveTopTabSkinStickerItemVerticalPadding(showText = showText)
                } else {
                    resolveTopTabDockIndicatorVerticalGapDp(hasOuterChromeSurface = false).dp
                }
            )
            .clip(itemShape)
            .background(containerColor, itemShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = if (useClickIndication) LocalIndication.current else null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = itemContentHorizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (showIcon) {
                if (!skinIconPath.isNullOrBlank()) {
                    AsyncImage(
                        model = File(skinIconPath),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(resolveTopTabSkinStickerIconSize(showText = showText))
                    )
                } else {
                    TopTabBlendedIcon(
                        unselectedIcon = unselectedIcon,
                        selectedIcon = selectedIcon,
                        selectedAlpha = selectionFraction,
                        tint = contentColor,
                        modifier = Modifier.size(
                            resolveTopTabIconSizeDp(if (showText) 0 else 1).dp
                        )
                    )
                }
            }
            if (showIcon && showText) {
                Spacer(modifier = Modifier.height(resolveTopTabIconTextSpacingDp(0).dp))
            }
            if (showText) {
                val labelMode = when {
                    showIcon && showText -> 0
                    showIcon -> 1
                    else -> 2
                }
                AppText(
                    text = category,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = resolveTopTabLabelTextSizeSp(labelMode).sp,
                    lineHeight = resolveTopTabLabelLineHeightSp(labelMode).sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor
                )
            }
            if (hasSkinStickerIcon && showText) {
                Spacer(modifier = Modifier.height(AppSpacingTokens.Micro))
                Box(
                    modifier = Modifier
                        .width(resolveTopTabSkinStickerIndicatorWidth())
                        .height(AppSpacingTokens.Micro)
                        .clip(AppShapes.container(ContainerLevel.Pill))
                        .background(selectedColor)
                        .alpha(selectionFraction)
                )
            }
        }
    }
}

@Composable
private fun TopTabBlendedIcon(
    unselectedIcon: ImageVector,
    selectedIcon: ImageVector,
    selectedAlpha: Float,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val progress = selectedAlpha.coerceIn(0f, 1f)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (unselectedIcon == selectedIcon) {
            AppIcon(
                imageVector = unselectedIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.matchParentSize()
            )
            return
        }
        AppIcon(
            imageVector = unselectedIcon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .matchParentSize()
                .alpha(1f - progress)
        )
        AppIcon(
            imageVector = selectedIcon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .matchParentSize()
                .alpha(progress)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryTabRow(
    categories: List<String> = resolveHomeTopCategories().map { it.label },
    categoryKeys: List<String> = resolveHomeTopCategories().map { it.name },
    selectedIndex: Int = 0,
    onCategorySelected: (Int) -> Unit = {},
    onPartitionClick: () -> Unit = {},
    pagerState: androidx.compose.foundation.pager.PagerState? = null, // [New] PagerState for sync
    labelMode: Int = 2,
    isLiquidGlassEnabled: Boolean = false,
    liquidGlassStyle: LiquidGlassStyle = LiquidGlassStyle.CLASSIC,
    liquidGlassTuning: LiquidGlassTuning? = null,
    liquidGlassPreset: BottomBarLiquidGlassPreset = BottomBarLiquidGlassPreset.BILIPAI_TUNED,
    hazeState: HazeState? = null,
    miuixBackdrop: MiuixBackdrop? = null,
    isFloatingStyle: Boolean = false,
    edgeToEdge: Boolean = false,
    hasOuterChromeSurface: Boolean = false,
    /** Shared with [HomeTopTabChrome.wrapDockWidth] so glass shell and tabs stay the same length. */
    wrapDockWidth: Boolean? = null,
    /** Cap on the dock width (top controls' combined width) so tabs stay left-right aligned. */
    maxDockWidthDp: Float = Float.POSITIVE_INFINITY,
    interactionBudget: HomeInteractionMotionBudget = HomeInteractionMotionBudget.FULL,
    motionTier: MotionTier = MotionTier.Normal,
    isTransitionRunning: Boolean = false,
    forceLowBlurBudget: Boolean = false,
    isViewportSyncEnabled: Boolean = true,
    skinPlainStyle: Boolean = false,
    skinPlainContentColor: Color? = null,
    topTabSkinIconPaths: Map<String, TopTabSkinIconPaths> = emptyMap(),
    partitionSkinIconPath: String? = null,
    forceMaterialUnderline: Boolean = false
) {
    val chromePolicy = rememberAppTopChromePolicy()
    val presetStyle = resolveHomeTopPresetStyle(
        chromePolicy = chromePolicy,
        labelMode = labelMode
    )
    val showPartitionAction = false
    val hasSkinStickerIcons = topTabSkinIconPaths.isNotEmpty() || !partitionSkinIconPath.isNullOrBlank()
    LightweightHomeTopTabs(
        presentation = presetStyle.presentation,
        categories = categories,
        categoryKeys = categoryKeys,
        selectedIndex = selectedIndex,
        onCategorySelected = onCategorySelected,
        onPartitionClick = onPartitionClick,
        pagerState = pagerState,
        labelMode = labelMode,
        isFloatingStyle = isFloatingStyle,
        edgeToEdge = edgeToEdge,
        skinPlainStyle = skinPlainStyle,
        skinPlainContentColor = skinPlainContentColor,
        isLiquidGlassEnabled = isLiquidGlassEnabled,
        liquidGlassStyle = liquidGlassStyle,
        liquidGlassTuning = liquidGlassTuning,
        liquidGlassPreset = liquidGlassPreset,
        miuixBackdrop = miuixBackdrop,
        topTabSkinIconPaths = topTabSkinIconPaths,
        partitionSkinIconPath = partitionSkinIconPath,
        hasOuterChromeSurface = hasOuterChromeSurface,
        wrapDockWidth = wrapDockWidth,
        maxDockWidthDp = maxDockWidthDp,
        isTransitionRunning = isTransitionRunning,
        showPartitionAction = showPartitionAction,
        isViewportSyncEnabled = isViewportSyncEnabled,
        forceMaterialUnderline = forceMaterialUnderline
    )
}

@Composable
private fun rememberTopTabPagerDragHeld(
    pagerState: androidx.compose.foundation.pager.PagerState?
): Boolean {
    if (pagerState == null) return false
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
    return isDragged
}

internal fun resolveTopTabIndicatorVelocity(
    horizontalVelocityPxPerSecond: Float
): Float {
    // 顶部指示器仅响应横向分页滑动，避免页面纵向滚动触发胶囊形变。
    return horizontalVelocityPxPerSecond.coerceIn(-4200f, 4200f)
}

internal fun resolveTopTabPagerVelocityItemsPerSecond(
    currentPosition: Float,
    previousPosition: Float,
    elapsedNanos: Long
): Float {
    if (elapsedNanos <= 0L) return 0f
    val elapsedSeconds = elapsedNanos / 1_000_000_000f
    if (elapsedSeconds <= 0f) return 0f
    return ((currentPosition - previousPosition) / elapsedSeconds).coerceIn(-12f, 12f)
}

internal fun resolveTopTabIndicatorDragTargetIndex(
    position: Float,
    itemCount: Int,
): Int {
    if (itemCount <= 0) return 0
    return position.roundToInt().coerceIn(0, itemCount - 1)
}

internal fun resolveTopTabIndicatorLayerVelocityItemsPerSecond(
    motionVelocityItemsPerSecond: Float
): Float = motionVelocityItemsPerSecond

internal fun shouldTopTabIndicatorBeInteracting(
    pagerIsDragging: Boolean = false,
    pagerIsScrolling: Boolean,
    combinedVelocityPxPerSecond: Float,
    liquidGlassEnabled: Boolean
): Boolean {
    if (pagerIsDragging) return true
    if (pagerIsScrolling) return true
    val combinedThreshold = if (liquidGlassEnabled) 20f else 60f
    return abs(combinedVelocityPxPerSecond) > combinedThreshold
}

internal fun resolveTopTabIndicatorInteractionReleaseDelayMillis(
    liquidGlassEnabled: Boolean
): Long {
    return if (liquidGlassEnabled) 140L else 0L
}

internal fun shouldTopTabIndicatorUseRefraction(
    position: Float,
    interacting: Boolean,
    velocityPxPerSecond: Float,
    positionEpsilon: Float = 0.015f,
    velocityEpsilon: Float = 45f
): Boolean {
    val fractional = abs(position - position.roundToInt().toFloat()) > positionEpsilon
    if (fractional) return true
    return abs(velocityPxPerSecond) > velocityEpsilon
}

internal fun shouldDeformTopTabIndicator(
    position: Float,
    isInMotion: Boolean,
    positionEpsilon: Float = 0.015f
): Boolean {
    if (!isInMotion) return false
    return abs(position - position.roundToInt().toFloat()) > positionEpsilon
}

internal fun resolveTopTabIndicatorVisualPolicy(
    position: Float,
    interacting: Boolean,
    velocityPxPerSecond: Float,
    useNeutralIndicatorTint: Boolean
): BottomBarIndicatorVisualPolicy {
    val shouldRefract = shouldTopTabIndicatorUseRefraction(
        position = position,
        interacting = interacting,
        velocityPxPerSecond = velocityPxPerSecond
    )
    return BottomBarIndicatorVisualPolicy(
        isInMotion = shouldRefract,
        shouldRefract = shouldRefract,
        useNeutralTint = shouldRefract && useNeutralIndicatorTint
    )
}

internal fun resolveTopTabStaticIndicatorVisualPolicy(
    useNeutralIndicatorTint: Boolean
): BottomBarIndicatorVisualPolicy {
    return BottomBarIndicatorVisualPolicy(
        isInMotion = false,
        shouldRefract = false,
        useNeutralTint = useNeutralIndicatorTint
    )
}

internal fun resolveTopTabIndicatorLayerTransform(
    motionProgress: Float,
    velocityItemsPerSecond: Float,
    dragScaleTarget: Float = BOTTOM_BAR_INDICATOR_DRAG_SCALE_TARGET,
    dragScaleTransform: BottomBarIndicatorLayerTransform? = null,
    motionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec =
        resolveSegmentedControlMotionSpec()
): BottomBarIndicatorLayerTransform {
    val bottomBarTransform = resolveBottomBarIndicatorLayerTransform(
        motionProgress = motionProgress,
        velocityItemsPerSecond = velocityItemsPerSecond,
        isDragging = true,
        dragScaleProgress = motionProgress,
        dragScaleTransform = dragScaleTransform,
        dragScaleTarget = dragScaleTarget,
        motionSpec = motionSpec
    )
    return bottomBarTransform
}

internal fun resolveTopTabIndicatorScaleProgress(
    dragScaleProgress: Float,
    pressProgress: Float
): Float {
    return maxOf(dragScaleProgress, pressProgress).coerceIn(0f, 1f)
}

internal fun resolveTopTabMatchedPanelOffsetPx(
    dragPanelOffsetPx: Float,
    pagerPanelOffsetFraction: Float,
    maxOffsetPx: Float,
    dragActive: Boolean
): Float {
    if (dragActive) return dragPanelOffsetPx
    return pagerPanelOffsetFraction.coerceIn(-1f, 1f) * maxOffsetPx.coerceAtLeast(0f)
}

internal fun resolveTopTabNeutralIndicatorColor(
    isDarkTheme: Boolean,
    alpha: Float
): Color {
    val baseColor = if (isDarkTheme) {
        HomeVisualPalette.SearchFieldDark
    } else {
        HomeVisualPalette.SearchFieldLight
    }
    return baseColor.copy(alpha = alpha)
}

internal fun resolveTopTabNeutralIndicatorTintAlpha(
    isDarkTheme: Boolean,
    configuredAlpha: Float
): Float {
    val floor = if (isDarkTheme) 0.38f else 0.42f
    return configuredAlpha.coerceAtLeast(floor)
}

internal data class TopTabIndicatorBackdropPolicy(
    val useIndicatorBackdrop: Boolean,
    val useCombinedBackdrop: Boolean
)

internal fun resolveTopTabIndicatorBackdropPolicy(
    effectiveLiquidGlassEnabled: Boolean,
    hasBackdrop: Boolean,
    indicatorVisualPolicy: BottomBarIndicatorVisualPolicy
): TopTabIndicatorBackdropPolicy {
    if (!effectiveLiquidGlassEnabled) {
        return TopTabIndicatorBackdropPolicy(
            useIndicatorBackdrop = indicatorVisualPolicy.shouldRefract && hasBackdrop,
            useCombinedBackdrop = false
        )
    }

    return TopTabIndicatorBackdropPolicy(
        useIndicatorBackdrop = true,
        // Same as the bottom bar: raw page + an export layer that already contains the
        // frosted dock material and selected-content tint.
        useCombinedBackdrop = hasBackdrop
    )
}

internal data class TopTabRefractionMotionProfile(
    val lensAmountScale: Float,
    val lensHeightScale: Float,
    val chromaticBoostScale: Float,
    val forceChromaticAberration: Boolean,
    val visibleSelectionEmphasis: Float,
    val exportSelectionEmphasis: Float,
    val indicatorPanelOffsetFraction: Float,
    val visiblePanelOffsetFraction: Float,
    val exportPanelOffsetFraction: Float
)

internal fun resolveTopTabRefractionMotionProfile(
    position: Float,
    shouldRefract: Boolean,
    velocityPxPerSecond: Float,
    liquidGlassEnabled: Boolean
): TopTabRefractionMotionProfile {
    if (!shouldRefract || !liquidGlassEnabled) {
        return TopTabRefractionMotionProfile(
            lensAmountScale = 1f,
            lensHeightScale = 1f,
            chromaticBoostScale = 1f,
            forceChromaticAberration = false,
            visibleSelectionEmphasis = 1f,
            exportSelectionEmphasis = 1f,
            indicatorPanelOffsetFraction = 0f,
            visiblePanelOffsetFraction = 0f,
            exportPanelOffsetFraction = 0f
        )
    }
    val bottomMotionSpec = resolveSegmentedControlMotionSpec()
    val bottomProfile = resolveBottomBarRefractionMotionProfile(
        position = position,
        velocity = velocityPxPerSecond,
        isDragging = true,
        motionSpec = bottomMotionSpec
    )
    return TopTabRefractionMotionProfile(
        lensAmountScale = 1f,
        lensHeightScale = 1f,
        chromaticBoostScale = 1f,
        forceChromaticAberration = bottomProfile.progress > 0.02f,
        visibleSelectionEmphasis = bottomProfile.visibleSelectionEmphasis,
        exportSelectionEmphasis = bottomProfile.exportSelectionEmphasis,
        indicatorPanelOffsetFraction = bottomProfile.indicatorPanelOffsetFraction,
        visiblePanelOffsetFraction = bottomProfile.visiblePanelOffsetFraction,
        exportPanelOffsetFraction = bottomProfile.exportPanelOffsetFraction
    )
}

internal fun resolveTopTabRefractionMotionProfile(
    shouldRefract: Boolean,
    velocityPxPerSecond: Float,
    liquidGlassEnabled: Boolean
): TopTabRefractionMotionProfile {
    return resolveTopTabRefractionMotionProfile(
        position = 0f,
        shouldRefract = shouldRefract,
        velocityPxPerSecond = velocityPxPerSecond,
        liquidGlassEnabled = liquidGlassEnabled
    )
}

internal fun resolveTopTabItemMotionVisual(
    itemIndex: Int,
    indicatorPosition: Float,
    currentSelectedIndex: Int,
    isInMotion: Boolean,
    selectionEmphasis: Float
): BottomBarItemMotionVisual {
    return resolveBottomBarItemMotionVisual(
        itemIndex = itemIndex,
        indicatorPosition = indicatorPosition,
        currentSelectedIndex = currentSelectedIndex,
        motionProgress = if (isInMotion) 1f else 0f,
        selectionEmphasis = selectionEmphasis
    )
}

internal fun resolveTopTabHorizontalDeltaPx(
    positionDeltaPages: Float,
    tabWidthPx: Float,
    deadZonePages: Float = 0.0012f
): Float {
    if (tabWidthPx <= 0f) return 0f
    if (abs(positionDeltaPages) < deadZonePages) return 0f
    return positionDeltaPages * tabWidthPx
}

internal fun resolveTopTabIndicatorViewportShiftPx(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffsetPx: Int,
    tabWidthPx: Float
): Float {
    if (tabWidthPx <= 0f) return 0f
    if (firstVisibleItemIndex < 0) return 0f
    val clampedScrollOffsetPx = firstVisibleItemScrollOffsetPx.coerceAtLeast(0)
    return firstVisibleItemIndex * tabWidthPx + clampedScrollOffsetPx.toFloat()
}

internal fun resolveTopTabIndicatorViewportClampShiftPx(
    rowScrollOffsetPx: Float,
    indicatorPanelOffsetPx: Float
): Float {
    // 手动横向滚动顶栏只改变标签列表视口，不应把选中指示器夹到当前视口里。
    return 0f
}

@Composable
fun CategoryTabItem(
    category: String,
    categoryKey: String = category,
    index: Int,
    selectedIndex: Int,
    currentPosition: Float,
    primaryColor: Color,
    unselectedColor: Color,
    labelMode: Int,
    isInMotion: Boolean = false,
    selectionEmphasis: Float = 1f,
    isInteractive: Boolean = true,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit = {}
) {
     val chromePolicy = rememberAppTopChromePolicy()
     val motionVisual = remember(
         index,
         currentPosition,
         selectedIndex,
         isInMotion,
         selectionEmphasis
     ) {
         resolveTopTabItemMotionVisual(
             itemIndex = index,
             indicatorPosition = currentPosition,
             currentSelectedIndex = selectedIndex,
             isInMotion = isInMotion,
             selectionEmphasis = selectionEmphasis
         )
     }
     val selectionFraction = motionVisual.themeWeight

     // 单层文本渲染，避免双层交叉透明带来的发虚/重影。
     val contentColor = androidx.compose.ui.graphics.lerp(
         unselectedColor,
         primaryColor,
         selectionFraction
     )
     val normalizedLabelMode = normalizeTopTabLabelMode(labelMode)
     val showIcon = shouldShowTopTabIcon(normalizedLabelMode)
     val showText = shouldShowTopTabText(normalizedLabelMode)
     val unselectedIcon = resolveTopTabCategoryIcon(
         categoryKey = categoryKey,
         iconFamily = chromePolicy.effectiveIconFamily,
         selected = false
     )
     val selectedIcon = resolveTopTabCategoryIcon(
         categoryKey = categoryKey,
         iconFamily = chromePolicy.effectiveIconFamily,
         selected = true
     )
     val iconSize = resolveTopTabIconSizeDp(normalizedLabelMode).dp
     val textSize = resolveTopTabLabelTextSizeSp(normalizedLabelMode).sp
     val textLineHeight = resolveTopTabLabelLineHeightSp(normalizedLabelMode).sp
     val contentMinHeight = resolveTopTabContentMinHeightDp(normalizedLabelMode).dp
     val contentVerticalPadding = resolveTopTabContentVerticalPaddingDp(normalizedLabelMode).dp
     val iconTextSpacing = resolveTopTabIconTextSpacingDp(normalizedLabelMode).dp
     
     val targetScale = resolveTopTabContentScale(
         selectionFraction = selectionFraction,
         showIcon = showIcon,
         showText = showText,
         presentation = chromePolicy.tabPresentation
     )
     
     // Font weight change still triggers relayout, but it's discrete (only happens at 0.6 threshold)
     // This is acceptable as it doesn't happen every frame.
     val fontWeight = if (selectionFraction > 0.6f) FontWeight.SemiBold else FontWeight.Medium

     val haptic = com.android.purebilibili.core.util.rememberHapticFeedback()

     Box(
         modifier = Modifier
             .clip(AppShapes.container(ContainerLevel.Pill))
             .then(
                 if (isInteractive) {
                     Modifier.combinedClickable(
                         interactionSource = remember { MutableInteractionSource() },
                         indication = null,
                         onClick = { onClick() },
                         onDoubleClick = onDoubleTap
                     )
                 } else {
                     Modifier
                 }
             )
             .padding(horizontal = AppSpacingTokens.Small, vertical = contentVerticalPadding)
             .heightIn(min = contentMinHeight),
         contentAlignment = Alignment.Center
     ) {
         if (showIcon && showText) {
             Column(
                 horizontalAlignment = Alignment.CenterHorizontally,
                 verticalArrangement = Arrangement.Center,
                 modifier = Modifier.graphicsLayer {
                     scaleX = targetScale
                     scaleY = targetScale
                     transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                 }
             ) {
                TopTabBlendedIcon(
                     unselectedIcon = unselectedIcon,
                     selectedIcon = selectedIcon,
                     selectedAlpha = selectionFraction,
                     tint = contentColor,
                     modifier = Modifier.size(iconSize)
                 )
                 Spacer(modifier = Modifier.height(iconTextSpacing))
                 AppText(
                     text = category,
                     color = contentColor,
                     fontSize = textSize,
                     fontWeight = fontWeight,
                     lineHeight = textLineHeight,
                     maxLines = 1,
                     overflow = TextOverflow.Ellipsis
                 )
             }
         } else if (showIcon) {
            TopTabBlendedIcon(
                unselectedIcon = unselectedIcon,
                selectedIcon = selectedIcon,
                 selectedAlpha = selectionFraction,
                 tint = contentColor,
                 modifier = Modifier
                     .size(iconSize)
                     .graphicsLayer {
                         scaleX = targetScale
                         scaleY = targetScale
                         transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                     }
             )
         } else {
             AppText(
                 text = category,
                 color = contentColor,
                 fontSize = textSize,
                 fontWeight = fontWeight,
                 lineHeight = textLineHeight,
                 modifier = Modifier.graphicsLayer {
                     scaleX = targetScale
                     scaleY = targetScale
                     transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                 },
                 maxLines = 1,
                 overflow = TextOverflow.Ellipsis
             )
         }
     }
}
