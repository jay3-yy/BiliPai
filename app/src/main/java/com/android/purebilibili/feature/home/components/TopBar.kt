// 文件路径: feature/home/components/TopBar.kt
package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.ui.AppContentColors

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppPressShiftContent
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.res.vectorResource
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
    // 与底栏一致：胶囊几乎贴满 dock 高度，上下各只留 1dp 呼吸边。
    val standardGap = 1f
    return if (isLiquidGlassReuseEnabled) {
        (standardGap - 0.5f).coerceAtLeast(0.5f)
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
 * 顶部胶囊指示器与底栏同规格：几乎贴满 dock 高度（上下各 1dp），宽度在内容
 * 宽之外各留 14dp 内边距，50% 圆角呈扁圆。
 */
/**
 * 顶部胶囊指示器与底栏共用同一枚扁圆（50% 圆角）形状：液态玻璃、Miuix 非
 * 玻璃、MD3 dock 胶囊全部一致，仅皮肤平铺与 MD3 原生下划线走各自路径。
 */
internal fun resolveTopTabIndicatorShape(
    showIcon: Boolean,
    showText: Boolean,
    isMiuixNonGlass: Boolean = false,
): Shape = resolveSharedBottomBarCapsuleShape()

/**
 * Resolves the vertical center offset (in Dp) of the MD3 native underline indicator
 * relative to the center of the tab row container.
 *
 * Tab content (icons and text) is vertically centered inside the container.
 * - Text-only tabs: Text line-height is ~20sp centered, meaning the text bottom
 *   is ~10dp below the container center. An offset of +14dp places the underline directly below the text.
 * - Icon-and-text tabs: Total height is ~42dp centered, placing the text bottom ~21dp below center.
 *   An offset of +26dp places the underline directly below the text.
 * - Icon-only tabs: Icon is ~18-24dp centered, placing the icon bottom ~9-12dp below center.
 *   An offset of +13dp places the underline directly below the icon.
 */
internal fun resolveMd3TopTabUnderlineCenterOffsetDp(
    showIcon: Boolean,
    showText: Boolean,
): Float {
    return when {
        showIcon && showText -> 26f
        showIcon -> 13f
        else -> 14f
    }
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
internal const val TOP_TAB_INDICATOR_CONTENT_PADDING_DP = 14f

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
    // 胶囊两侧各留一圈水平内边距，才能呈现底栏同款扁圆；宽度仍受槽位上限约束。
    val contentWidth = androidx.compose.ui.util.lerp(
        contentWidthsDp[start], contentWidthsDp[end], clamped - start
    ) + TOP_TAB_INDICATOR_CONTENT_PADDING_DP * 2f + horizontalGapDp * 2f
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
    val normalizedKey = categoryKey.trim()
    if (normalizedKey.equals("SUBSCRIPTIONS", ignoreCase = true)) {
        return when (iconFamily) {
            AppSemanticIconFamily.MATERIAL -> ImageVector.vectorResource(
                com.android.purebilibili.R.drawable.ms_rss_feed_24
            )
            AppSemanticIconFamily.MIUIX -> resolveMiuixPreferredHomeNavigationIcon(
                tabId = "SUBSCRIPTIONS",
                selected = selected,
            )
        }
    }
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
        if (iconAndText) 52.dp else AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall
    } else {
        if (iconAndText) 48.dp else AppSpacingTokens.DoubleExtraLarge
    }
}

/**
 * 顶部 dock 专用壳高：此前直接复用底栏 64dp 壳（按纯图标调校），顶部塞入
 * 图标+双行文案后整体偏大。独立收缩一档，底栏不受影响。
 */
internal fun resolveHomeTopDockShellHeight(isFloatingStyle: Boolean): Dp =
    if (isFloatingStyle) 52.dp else 48.dp

internal fun resolveIosTopTabActionButtonSize(isFloatingStyle: Boolean): Dp =
    if (isFloatingStyle) AppSpacingTokens.DoubleExtraLarge + AppSpacingTokens.ExtraSmall else AppSpacingTokens.DoubleExtraLarge

internal fun resolveIosTopTabActionButtonCorner(isFloatingStyle: Boolean): Dp =
    if (isFloatingStyle) AppSpacingTokens.ExtraLarge else AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall

internal fun resolveIosTopTabActionIconSize(isFloatingStyle: Boolean): Dp =
    AppSpacingTokens.ExtraLarge - AppSpacingTokens.ExtraSmall

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
                                AppIcon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = if (user.isLogin) "用户头像" else "未登录，点击登录",
                                    tint = AppContentColors.accent(),
                                    modifier = Modifier.fillMaxSize(0.6f),
                                )
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
                    val searchPressed by searchClickInteractionSource.collectIsPressedAsState()
                    AppPressShiftContent(
                        pressed = searchPressed,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(
                                Icons.Outlined.Search,
                                null,
                                tint = AppContentColors.secondaryText().copy(0.5f),
                                modifier = Modifier.size(AppSpacingTokens.Large + AppSpacingTokens.Micro)
                            )
                            Spacer(modifier = Modifier.width(AppSpacingTokens.Small))
                            AppText(
                                text = "搜索视频、UP主...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                color = AppContentColors.secondaryText(),
                                maxLines = 1
                            )
                        }
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
                        tint = AppContentColors.primaryText().copy(alpha = 0.7f),
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
): Color = colorScheme.primary

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
    materialScrollProgress: Float = if (isScrolling) 1f else 0f,
    interactionState: com.android.purebilibili.core.ui.components.AppElasticPressState? = null,
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
            interactionState = interactionState,
            interactionLayerBlock = interactionState?.layerBlock,
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
    forceMaterialUnderline: Boolean = false,
    dockHorizontalPadding: Dp = AppSpacingTokens.ExtraSmall,
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
        stableColor = AppContentColors.primaryText(),
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
        skinPlainStyle || forceMaterialUnderline || LocalAppUiStyle.current == AppUiStyle.MATERIAL3 ->
            AppTopTabPresentation.MATERIAL_UNDERLINE
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
            forceMaterialUnderline = forceMaterialUnderline || LocalAppUiStyle.current == AppUiStyle.MATERIAL3,
        ),
    )
    val topTabMotionSpec = remember { resolveSegmentedControlMotionSpec() }
    val baseRowHeight = if (useFloatingBottomBarDock) {
        resolveHomeTopDockShellHeight(isFloatingStyle)
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
        val isMiuixOfficialTabs = shouldUseOfficialMiuixHomeTopTabs(
            uiStyle = LocalAppUiStyle.current,
            liquidGlassEnabled = isLiquidGlassEnabled,
        )
        val wrapDock = (wrapDockWidth ?: shouldWrapTopTabDockWidth(
            isFloatingStyle = isFloatingStyle,
            hasOuterChromeSurface = hasOuterChromeSurface,
            edgeToEdge = edgeToEdge
        )) || isMiuixOfficialTabs
        // 分栏 dock 最大宽度 = 顶部三控件合计宽度，与外壳共享同一上限。
        val effectiveMaxDockWidth = minOf(maxWidth.value, maxDockWidthDp)
        val fillItemWidthDp = when (effectivePresentation) {
            AppTopTabPresentation.MOVING_CAPSULE -> resolveIosTopTabItemWidthDp(
                containerWidthDp = effectiveMaxDockWidth,
                categoryCount = categories.size,
                labelMode = normalizedLabelMode
            )
            AppTopTabPresentation.MATERIAL_UNDERLINE,
            AppTopTabPresentation.TONAL_CAPSULE -> if (isMiuixOfficialTabs) {
                resolveTopTabWrapItemWidthDp(
                    labelMode = normalizedLabelMode,
                    isFloatingStyle = isFloatingStyle
                )
            } else if (forceMaterialUnderline) {
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
            // Compact text geometry belongs only to liquid glass; native and icon layouts retain their sizes.
            val floatingDockHeight = if (isLiquidGlassEnabled && showText && !showIcon) {
                AppChromeSizeTokens.BottomBarMatchedSegmentedControlHeightDp.dp
            } else {
                resolveHomeTopDockShellHeight(isFloatingStyle)
            }
            val floatingDockWidth = resolveHomeTopTabFloatingDockWidth(
                containerWidth = effectiveMaxDockWidth.dp,
                itemCount = categories.size,
                labelMode = normalizedLabelMode,
                shellHeight = floatingDockHeight,
            )
            val floatingLabelFontSize = resolveFloatingDockLabelFontSize(
                showIcon = showIcon,
                showText = showText,
                fontScale = density.fontScale,
            )
            // Native pure-text top tabs were visually undersized; enlarge this mode only.
            val resolvedFloatingLabelFontSize = if (!isLiquidGlassEnabled && showText && !showIcon) {
                (floatingLabelFontSize.value + 2f).sp
            } else {
                floatingLabelFontSize
            }
            val minimumSlotWidth = com.android.purebilibili.core.ui.components.rememberMeasuredTabMinWidth(
                requestedMinWidth = maxOf(
                    (floatingDockHeight - AppSpacingTokens.ExtraSmall * 2).coerceAtLeast(0.dp),
                    if (showIcon) resolveHomeTopDockIconSizeDp(
                        if (showText) 0 else 1,
                        liquidGlassEnabled = isLiquidGlassEnabled,
                    ).dp +
                        AppSpacingTokens.Small * 2 else AppChromeSizeTokens.MinimumTouchTarget,
                ),
                labels = if (showText) categories else emptyList(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = resolvedFloatingLabelFontSize,
                    fontWeight = FontWeight.Bold,
                ),
            )
            val dockContentPadding = dockHorizontalPadding * 2
            val minimumContentWidth = minimumSlotWidth * categories.size + dockContentPadding
            val needsDockScroll = minimumContentWidth > effectiveMaxDockWidth.dp
            val visibleSlotCount = kotlin.math.floor(
                ((effectiveMaxDockWidth.dp - dockContentPadding) / minimumSlotWidth).toDouble(),
            ).toInt().coerceIn(1, categories.size.coerceAtLeast(1))
            val scrollingSlotWidth = ((effectiveMaxDockWidth.dp - dockContentPadding) /
                visibleSlotCount).coerceAtLeast(minimumSlotWidth)
            val fittedDockWidth = maxOf(floatingDockWidth, minimumContentWidth)
                .coerceAtMost(effectiveMaxDockWidth.dp)
            val dockScrollState = androidx.compose.foundation.rememberScrollState()
            if (needsDockScroll) {
                com.android.purebilibili.core.ui.components.KeepScrollableTabSelectionVisible(
                    scrollState = dockScrollState,
                    selectedIndex = selectedIndex,
                    itemWidthPx = with(density) { scrollingSlotWidth.toPx() },
                    contentPaddingPx = with(density) { dockHorizontalPadding.toPx() },
                    focusPosition = currentPositionProvider,
                    continuousFollow = pagerScrollingProvider,
                    centerSelection = false,
                )
            }
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
                    itemWidth = scrollingSlotWidth.takeIf { needsDockScroll },
                    dockHeight = floatingDockHeight,
                    horizontalPadding = dockHorizontalPadding,
                    scrollState = dockScrollState.takeIf { needsDockScroll },
                    labelFontSize = resolvedFloatingLabelFontSize,
                    liquidGlassEffectsEnabled = isLiquidGlassEnabled,
                    miuixBackdrop = miuixBackdrop,
                    liquidGlassPreset = liquidGlassPreset,
                    liquidGlassTuning = resolvedLiquidGlassTuning,
                    indicatorPositionProvider = currentPositionProvider,
                    isScrollInProgressProvider = pagerScrollingProvider,
                    modifier = Modifier
                        .width(if (needsDockScroll) effectiveMaxDockWidth.dp else fittedDockWidth)
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
        val md3ContentWidths = remember(
            categories,
            normalizedLabelMode,
            isLiquidGlassEnabled,
            density,
            textMeasurer,
        ) {
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
                    resolveHomeTopDockIconSizeDp(
                        normalizedLabelMode,
                        liquidGlassEnabled = isLiquidGlassEnabled,
                    ).dp
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
        // Like upstream's 56dp capture inside its 64dp shell, record only the resting
        // indicator band. Its top/bottom material boundary is part of the lens input.
        val topTabCaptureHeight = resolveTopTabDockIndicatorHeightDp(
            rowHeightDp = rowHeight.value,
            verticalGapDp = dockIndicatorVerticalGap.value,
            minHeightDp = com.android.purebilibili.core.ui.resolveMatchedLiquidIndicatorHeightDp(rowHeight.value),
            indicatorWidthDp = resolveTopTabDockIndicatorWidthDp(itemWidth.value, dockIndicatorHorizontalGap.value),
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
        // 位置以 State 形式持有：pager fraction 与指示器直拖位置均为 snapshot state。
        // 外层组合体不直接逐帧读取（那会让整个 chrome 在横滑期间每帧重组）；
        // 连续值由指示器 overlay 子组件和 derivedStateOf 离散包装在各自 scope 内读取。
        val pagerIndicatorPositionState = remember(pagerState, selectedIndex) {
            derivedStateOf { currentPositionProvider() }
        }
        val pagerContentPositionState = remember(pagerState, selectedIndex) {
            derivedStateOf { selectedContentPositionProvider() }
        }
        val topTabIndicatorOwnsPosition = topTabIndicatorDirectDragActive ||
            topTabIndicatorSettlingTarget != null
        // Gesture ownership replaces the derived State object. Every downstream cached
        // calculation must track that object, rather than retain the pre-drag pager state.
        val topTabIndicatorPositionState = remember(topTabIndicatorOwnsPosition, pagerIndicatorPositionState) {
            derivedStateOf {
                if (topTabIndicatorOwnsPosition) {
                    topTabIndicatorDirectDragPosition
                } else {
                    pagerIndicatorPositionState.value
                }
            }
        }
        val iosCapsulePositionState = remember(topTabIndicatorOwnsPosition, pagerContentPositionState) {
            derivedStateOf {
                if (topTabIndicatorOwnsPosition) {
                    topTabIndicatorDirectDragPosition
                } else {
                    pagerContentPositionState.value
                }
            }
        }
        val topTabContentPositionState = remember(
            topTabIndicatorOwnsPosition,
            effectivePresentation,
            iosCapsulePositionState,
            topTabIndicatorPositionState,
        ) {
            derivedStateOf {
                if (effectivePresentation == AppTopTabPresentation.MOVING_CAPSULE) {
                    iosCapsulePositionState.value
                } else {
                    topTabIndicatorPositionState.value
                }
            }
        }
        val indicatorIsInteracting = pagerIsDragging || pagerIsScrolling ||
            topTabIndicatorDirectDragActive
        val topTabShouldStretchIndicator by remember(indicatorIsInteracting, topTabIndicatorPositionState) {
            derivedStateOf {
                shouldDeformTopTabIndicator(
                    position = topTabIndicatorPositionState.value,
                    isInMotion = indicatorIsInteracting
                )
            }
        }
        // 速度跟踪器必须是普通可变字段而非 snapshot state：derivedStateOf 的
        // 计算体既读又写它们，若为 state 会自失效形成重组死循环（静止时全局
        // 锁 120Hz）。作为纯记忆字段写入不触发任何失效，仅位置 state 变化
        // 才重算，空闲时无读取即无开销。
        val topTabVelocityTracker = remember {
            object {
                var previousPosition = Float.NaN
                var previousNanos = 0L
            }
        }
        val topTabMotionVelocityItemsPerSecondState = remember(topTabIndicatorPositionState) {
            derivedStateOf {
                val position = topTabIndicatorPositionState.value
                val now = System.nanoTime()
                val previousPosition = topTabVelocityTracker.previousPosition
                val velocity = if (previousPosition.isNaN()) {
                    0f
                } else {
                    resolveTopTabPagerVelocityItemsPerSecond(
                        currentPosition = position,
                        previousPosition = previousPosition,
                        elapsedNanos = (now - topTabVelocityTracker.previousNanos).coerceAtLeast(1L)
                    )
                }
                topTabVelocityTracker.previousPosition = position
                topTabVelocityTracker.previousNanos = now
                velocity
            }
        }
        val topTabMotionVelocityPxPerSecondState = remember(density, itemWidth, topTabMotionVelocityItemsPerSecondState) {
            derivedStateOf {
                topTabMotionVelocityItemsPerSecondState.value * with(density) {
                    itemWidth.toPx()
                }
            }
        }
        val topTabIndicatorInteractionSource = remember { MutableInteractionSource() }
        val topTabIndicatorPressed by topTabIndicatorInteractionSource.collectIsPressedAsState()
        // 指示器缩放弹簧、折射面板偏移、胶囊宽高与平移等连续管线全部下沉到
        // LightweightTopTabIndicatorOverlay，在子组件 scope 内逐帧求值，
        // 避免横滑期间外层 chrome 整体重组。
        // Selected-tab pill position: item slot center minus half the pill width, so the
        // capsule follows the pager offset and the row scroll while staying inside the dock.
        // （推导移入 LightweightTopTabIndicatorOverlay，位置经 State 注入。）
        val shouldUseMovingIosCapsule = effectivePresentation == AppTopTabPresentation.MOVING_CAPSULE &&
            !skinPlainStyle &&
            !hasSkinStickerIcons
        val homeSelectionIndicatorStyle = resolveHomeSelectionIndicatorStyle(
            uiStyle = LocalAppUiStyle.current,
            liquidGlassEnabled = isLiquidGlassEnabled,
            forceMaterialUnderline = forceMaterialUnderline || LocalAppUiStyle.current == AppUiStyle.MATERIAL3,
        )
        val shouldUseHomeCapsule =
            homeSelectionIndicatorStyle == HomeSelectionIndicatorStyle.CAPSULE
        // 开启液态玻璃时使用胶囊；关闭后 MD3 使用下划线。
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
        // Underlines do not render exported glyphs; keep selected labels in the visible row.
        val shouldUseLiquidGlassIndicator = isLiquidGlassEnabled &&
            !skinPlainStyle &&
            !hasSkinStickerIcons &&
            !shouldUseMd3NativeUnderline
        val shouldPrimeTopTabLiquidGlassCapture =
            isLiquidGlassEnabled &&
                !skinPlainStyle &&
                !hasSkinStickerIcons
        // Miuix-only capture (no legacy dual path).
        val topTabMiuixContentBackdrop = rememberMiuixLayerBackdrop()
        // 视觉策略只产出离散决策（是否折射/中性着色），包一层 derivedStateOf：
        // 位置/速度仍逐帧进入推导，但只有布尔结果变化才通知外层重组。
        val topTabIndicatorVisualPolicy by remember(
            shouldUseLiquidGlassIndicator,
            topTabIndicatorPositionState,
            indicatorIsInteracting,
            topTabMotionVelocityPxPerSecondState,
        ) {
            derivedStateOf {
                resolveTopTabIndicatorVisualPolicy(
                    position = topTabIndicatorPositionState.value,
                    interacting = indicatorIsInteracting,
                    velocityPxPerSecond = topTabMotionVelocityPxPerSecondState.value,
                    useNeutralIndicatorTint = shouldUseLiquidGlassIndicator
                )
            }
        }
        val topTabIndicatorBackdropPolicy = resolveTopTabIndicatorBackdropPolicy(
            effectiveLiquidGlassEnabled = shouldUseLiquidGlassIndicator,
            hasBackdrop = miuixBackdrop != null,
            indicatorVisualPolicy = topTabIndicatorVisualPolicy
        )
        // Match the bottom bar's two-source topology. The local source first records the
        // already-frosted dock material and tinted labels, so the indicator never falls
        // back to a raw-page-only frame during idle/gesture transitions.
        val effectiveTopTabMiuixContentBackdrop = when {
            !topTabIndicatorBackdropPolicy.useIndicatorBackdrop -> null
            topTabIndicatorBackdropPolicy.useCombinedBackdrop && miuixBackdrop != null ->
                rememberMiuixCombinedBackdrop(miuixBackdrop, topTabMiuixContentBackdrop)
            else -> topTabMiuixContentBackdrop
        }
        val topTabIndicatorCaptureSurfaceColor =
            resolveBiliPaiBottomBarContainerColor(
                darkTheme = isDarkTheme,
                liquidGlassTuning = resolvedLiquidGlassTuning,
                surfaceColor = AppSurfaceTokens.surfaceContainer(),
            )
        val useTopTabGlassColorPath = resolveTopTabUsesGlassExportForSelectedGlyphs(
            liquidGlassEnabled = shouldUseLiquidGlassIndicator,
        )
        val topTabVisibleContentZIndex = if (useTopTabGlassColorPath) 0f else 2f
        val topTabThemeColor = AppContentColors.selectionAccent(shouldUseLiquidGlassIndicator)
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
        val measuredSelectedItemLeftPxState = remember(shouldUseMovingIosCapsule) {
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
        // iOS 胶囊平移管线（含回弹动画）移入 LightweightTopTabIndicatorOverlay；
        // measuredSelectedItemLeftPx 经 State 注入子组件。
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
        val shouldAnimateMd3Tap = shouldUseMd3NativeUnderline &&
            !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current &&
            shouldAnimateMd3TopTabUnderline(
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
        // 非委托持有：动画值只在 derivedStateOf / overlay 子组件内读取，
        // 点按动画期间不逐帧通知外层。
        val animatedMd3UnderlineLeftPxState = animateFloatAsState(
            targetValue = targetBounds.leftPx,
            animationSpec = leftAnimationSpec,
            label = "md3UnderlineLeft"
        )
        val animatedMd3UnderlineRightPxState = animateFloatAsState(
            targetValue = targetBounds.rightPx,
            animationSpec = rightAnimationSpec,
            label = "md3UnderlineRight"
        )
        SideEffect {
            previousMd3TargetIndex.intValue = safeMd3TargetIndex
        }
        val effectiveTopTabContentPositionState = remember(
            shouldUseMd3NativeUnderline,
            shouldAnimateMd3Tap,
            topTabContentPositionState,
            density,
            itemWidth,
            md3ContentPadding,
            safeMd3TargetIndex,
            categories.size,
        ) {
            derivedStateOf {
                if (shouldUseMd3NativeUnderline && shouldAnimateMd3Tap) {
                    resolveMd3TopTabTapContentPosition(
                        animatedLeftPx = animatedMd3UnderlineLeftPxState.value,
                        animatedRightPx = animatedMd3UnderlineRightPxState.value,
                        itemWidthPx = with(density) { itemWidth.toPx() },
                        contentPaddingPx = with(density) { md3ContentPadding.toPx() },
                        fallbackIndex = safeMd3TargetIndex,
                        categoryCount = categories.size
                    )
                } else {
                    topTabContentPositionState.value
                }
            }
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
                val topTabHorizontalPaddingPx = with(density) { topTabHorizontalPadding.toPx() }
                // Keep the sampled and visible labels fixed. Moving this whole group makes the
                // tab strip rebound with the indicator and desynchronizes backdrop sampling from
                // LazyRow's own gesture transform. Only the indicator receives the liquid offset.
                val captureHighlightScope = rememberCoroutineScope()
                val topTabCaptureHighlight = if (shouldPrimeTopTabLiquidGlassCapture &&
                    com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
                ) {
                    remember(captureHighlightScope, listState, rowHeight, topTabCaptureHeight, density, itemWidth, topTabHorizontalPadding) {
                        com.android.purebilibili.feature.home.components.miuix.InteractiveHighlight(
                            animationScope = captureHighlightScope,
                            position = { _, point ->
                                androidx.compose.ui.geometry.Offset(
                                    point.x - topTabHorizontalPaddingPx + topTabListScrollOffsetPxProvider(),
                                    point.y - with(density) { (rowHeight - topTabCaptureHeight).toPx() / 2f },
                                )
                            },
                        )
                    }
                } else null
                Box(
                    modifier = Modifier.fillMaxSize()
                        .then(topTabCaptureHighlight?.gestureModifier ?: Modifier),
                ) {
                // Match the bottom bar's resting-indicator capture height. Sampling the
                // full shell erases the narrow material boundary used by rim dispersion.
                // Capture layer: BiliPai-style tabsBackdrop (export-tinted glyphs under glass).
                if (shouldPrimeTopTabLiquidGlassCapture) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(topTabCaptureHeight)
                            .align(Alignment.Center)
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
                                            shape = resolveTopTabIndicatorShape(
                                                showIcon = showIcon,
                                                showText = showText,
                                                isMiuixNonGlass = isMiuixOfficialTabs,
                                            ),
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
                            }
                            .then(topTabCaptureHighlight?.modifier ?: Modifier),
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
                        val selectionFraction = (1f - abs(effectiveTopTabContentPositionState.value - index.toFloat())).coerceIn(0f, 1f)
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
                // 指示器连续管线（速度→折射→宽高→平移→缩放弹簧）全部在子组件 scope
                // 内逐帧求值；外层仅以 State/lambda 注入位置与离散开关。
                LightweightTopTabIndicatorOverlay(
                    positionState = topTabIndicatorPositionState,
                    capsulePositionState = iosCapsulePositionState,
                    measuredSelectedItemLeftPxState = measuredSelectedItemLeftPxState,
                    velocityItemsPerSecondState = topTabMotionVelocityItemsPerSecondState,
                    velocityPxPerSecondState = topTabMotionVelocityPxPerSecondState,
                    density = density,
                    itemWidth = itemWidth,
                    dockIndicatorHorizontalGap = dockIndicatorHorizontalGap,
                    dockIndicatorVerticalGap = dockIndicatorVerticalGap,
                    rowHeight = rowHeight,
                    topTabHorizontalPadding = topTabHorizontalPadding,
                    rowScrollOffsetPxProvider = { rowScrollOffsetPx },
                    categoriesLastIndex = categories.lastIndex,
                    effectivePresentation = effectivePresentation,
                    shouldUseMovingIosCapsule = shouldUseMovingIosCapsule,
                    shouldUseMd3DockBackedCapsule = shouldUseMd3DockBackedCapsule,
                    shouldUseMd3LiquidCapsule = shouldUseMd3LiquidCapsule,
                    shouldUseLiquidGlassIndicator = shouldUseLiquidGlassIndicator,
                    indicatorCombinedBackdrop = effectiveTopTabMiuixContentBackdrop,
                    isDarkTheme = isDarkTheme,
                    liquidGlassTuning = resolvedLiquidGlassTuning,
                    topTabMotionSpec = topTabMotionSpec,
                    indicatorIsInteracting = indicatorIsInteracting,
                    pagerIsDragging = pagerIsDragging,
                    pagerIsScrolling = pagerIsScrolling,
                    ownsPosition = topTabIndicatorOwnsPosition,
                    indicatorPressed = topTabIndicatorPressed,
                    shouldStretchIndicator = topTabShouldStretchIndicator,
                    md3ContentWidths = md3ContentWidths,
                    showIcon = showIcon,
                    showText = showText,
                    isMiuixOfficialTabs = isMiuixOfficialTabs,
                    indicatorInteractionSource = topTabIndicatorInteractionSource,
                    indicatorDraggableState = indicatorDraggableState,
                    indicatorDragEnabled = pagerState != null && categories.size > 1,
                    onIndicatorDragStarted = {
                        topTabIndicatorSettlingTarget = null
                        topTabIndicatorDirectDragPosition = currentPositionProvider()
                            .coerceIn(0f, categories.lastIndex.toFloat())
                        topTabIndicatorDirectDragActive = true
                    },
                    onIndicatorDragStopped = {
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
                    onIndicatorTap = {
                        performHomeTopBarTap(
                            haptic = haptic,
                            onClick = {
                                scrollChannel?.trySend(
                                    com.android.purebilibili.feature.home.HomeScrollRequest.SCROLL_TO_TOP_OR_REFRESH
                                )
                            }
                        )
                    },
                )
                } // stable export + visible content with indicator-only motion

                // 非玻璃 MD3 与皮肤顶栏使用单层短指示线，始终位于内容底部居中。
                val useNativeTonalPill = shouldUseMd3NativeUnderline &&
                    !skinPlainStyle && !forceMaterialUnderline &&
                    LocalAppUiStyle.current == AppUiStyle.MATERIAL3
                if (useNativeTonalPill) {
                    val pillColor = MaterialTheme.colorScheme.secondaryContainer
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            // This is a sibling of the content container. Child zIndex
                            // cannot lift glyphs above a later sibling's opaque fill.
                            .zIndex(-1f)
                            .drawBehind {
                                val pillWidth = (itemWidth.toPx() - 8.dp.toPx()).coerceAtLeast(0f)
                                val pillHeight = (size.height - 8.dp.toPx()).coerceAtLeast(0f)
                                val left = resolveMd3TopTabIndicatorTranslationPx(
                                    absolutePagerPosition = topTabIndicatorPositionState.value,
                                    itemWidthPx = itemWidth.toPx(),
                                    rowScrollOffsetPx = rowScrollOffsetPx,
                                    indicatorWidthPx = pillWidth,
                                    contentPaddingPx = topTabHorizontalPadding.toPx(),
                                )
                                drawRoundRect(
                                    color = pillColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(left, 4.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(pillWidth, pillHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillHeight / 2f),
                                )
                            },
                    )
                }
                if (shouldUseMd3NativeUnderline && !useNativeTonalPill) {
                    LightweightTopTabMd3Underline(
                        positionState = topTabIndicatorPositionState,
                        density = density,
                        itemWidth = itemWidth,
                        categoriesLastIndex = categories.lastIndex,
                        md3ContentWidths = md3ContentWidths,
                        md3IndicatorWidth = md3IndicatorWidth,
                        md3ContentPadding = md3ContentPadding,
                        showIcon = showIcon,
                        showText = showText,
                        shouldAnimateMd3Tap = shouldAnimateMd3Tap,
                        animatedUnderlineLeftPxProvider = { animatedMd3UnderlineLeftPxState.value },
                        animatedUnderlineRightPxProvider = { animatedMd3UnderlineRightPxState.value },
                        rowScrollOffsetPxProvider = { rowScrollOffsetPx },
                        skinPlainContentColor = skinPlainContentColor,
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
                            tint = skinPlainContentColor ?: AppContentColors.secondaryText(),
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

/**
 * 指示器连续管线（速度→折射面板偏移→胶囊宽高/几何→缩放弹簧→平移）与三个胶囊
 * 指示器、手势层的宿主。位置/速度经 [State] 注入，在自身 scope 内逐帧求值；
 * 外层 chrome 只持有离散决策，不再随 pager fraction 每帧重组。
 */
@Composable
private fun BoxScope.LightweightTopTabIndicatorOverlay(
    positionState: androidx.compose.runtime.State<Float>,
    capsulePositionState: androidx.compose.runtime.State<Float>,
    measuredSelectedItemLeftPxState: androidx.compose.runtime.State<Float?>,
    velocityItemsPerSecondState: androidx.compose.runtime.State<Float>,
    velocityPxPerSecondState: androidx.compose.runtime.State<Float>,
    density: androidx.compose.ui.unit.Density,
    itemWidth: Dp,
    dockIndicatorHorizontalGap: Dp,
    dockIndicatorVerticalGap: Dp,
    rowHeight: Dp,
    topTabHorizontalPadding: Dp,
    rowScrollOffsetPxProvider: () -> Float,
    categoriesLastIndex: Int,
    effectivePresentation: AppTopTabPresentation,
    shouldUseMovingIosCapsule: Boolean,
    shouldUseMd3DockBackedCapsule: Boolean,
    shouldUseMd3LiquidCapsule: Boolean,
    shouldUseLiquidGlassIndicator: Boolean,
    indicatorCombinedBackdrop: MiuixBackdrop?,
    isDarkTheme: Boolean,
    liquidGlassTuning: LiquidGlassTuning,
    topTabMotionSpec: com.android.purebilibili.core.ui.motion.BottomBarMotionSpec,
    indicatorIsInteracting: Boolean,
    pagerIsDragging: Boolean,
    pagerIsScrolling: Boolean,
    ownsPosition: Boolean,
    indicatorPressed: Boolean,
    shouldStretchIndicator: Boolean,
    md3ContentWidths: List<Dp>,
    showIcon: Boolean,
    showText: Boolean,
    isMiuixOfficialTabs: Boolean,
    indicatorInteractionSource: MutableInteractionSource,
    indicatorDraggableState: androidx.compose.foundation.gestures.DraggableState,
    indicatorDragEnabled: Boolean,
    onIndicatorDragStarted: () -> Unit,
    onIndicatorDragStopped: () -> Unit,
    onIndicatorTap: () -> Unit,
) {
    val topTabIndicatorScaleProgress = rememberBottomBarIndicatorDragScaleProgress(
        isDragging = shouldStretchIndicator || indicatorPressed
    )
    val topTabPressProgress = rememberBottomBarIndicatorDragScaleProgress(
        isDragging = indicatorPressed
    )
    val topTabIndicatorLayerScaleProgress = resolveTopTabIndicatorScaleProgress(
        dragScaleProgress = topTabIndicatorScaleProgress,
        pressProgress = topTabPressProgress
    )
    // Pager swipes have no direct press event. Reuse the bottom-bar drag-scale animation
    // as their effective press so the indicator surface fades and lens ramps identically.
    val topTabLensProgress = topTabIndicatorLayerScaleProgress
    val topTabIndicatorPosition = positionState.value
    val topTabRefractionMotionProfile = resolveBottomBarRefractionMotionProfile(
        position = topTabIndicatorPosition,
        velocity = velocityPxPerSecondState.value,
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
        lastIndex = categoriesLastIndex,
        panelOffsetPx = rawTopTabPanelOffsetPx,
    )
    val topTabIndicatorPanelOffsetPx =
        if (shouldUseLiquidGlassIndicator) topTabPanelOffsetPx else 0f
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
        velocityItemsPerSecond = velocityItemsPerSecondState.value,
        dragScaleTarget = topTabIndicatorGeometry.pressedScale,
        dragScaleTransform = rememberBottomBarIndicatorLayerScaleTransform(
            active = shouldStretchIndicator || indicatorPressed,
            target = topTabIndicatorGeometry.pressedScale
        ),
        motionSpec = topTabMotionSpec
    )
    val indicatorScaleX = topTabIndicatorLayerTransform.scaleX
    val indicatorScaleY = topTabIndicatorLayerTransform.scaleY
    // Velocity stretch is already folded into the shared bottom-bar transform.
    // Keep the BiliPai layer velocity neutral to avoid applying it twice.
    val indicatorVelocity = 0f
    val capsuleTargetTranslationXPx by remember(
        density,
        itemWidth,
        topTabHorizontalPadding,
        pagerIsDragging,
        pagerIsScrolling,
        ownsPosition,
        capsulePositionState,
        measuredSelectedItemLeftPxState,
        rowScrollOffsetPxProvider,
    ) {
        derivedStateOf {
            with(density) {
                resolveIosTopTabCapsuleTargetTranslationPx(
                    measuredSelectedItemLeftPx = measuredSelectedItemLeftPxState.value,
                    absolutePagerPosition = capsulePositionState.value,
                    itemWidthPx = itemWidth.toPx(),
                    rowScrollOffsetPx = rowScrollOffsetPxProvider(),
                    contentPaddingPx = topTabHorizontalPadding.toPx(),
                    followPagerPosition = pagerIsDragging || pagerIsScrolling || ownsPosition
                )
            }
        }
    }
    val shouldAnimateIosCapsule = shouldAnimateIosTopTabCapsule(
        pagerIsDragging = pagerIsDragging || ownsPosition,
        pagerIsScrolling = pagerIsScrolling
    )
    val animatedIosCapsuleTranslationXPx by animateFloatAsState(
        targetValue = capsuleTargetTranslationXPx,
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
        capsuleTargetTranslationXPx
    }
    val md3IndicatorTranslationXPx by remember(
        density,
        itemWidth,
        topTabHorizontalPadding,
        positionState,
        md3LiquidCapsuleWidth,
        rowScrollOffsetPxProvider,
    ) {
        derivedStateOf {
            with(density) {
                resolveMd3TopTabIndicatorTranslationPx(
                    absolutePagerPosition = positionState.value,
                    itemWidthPx = itemWidth.toPx(),
                    rowScrollOffsetPx = rowScrollOffsetPxProvider(),
                    indicatorWidthPx = md3LiquidCapsuleWidth.toPx(),
                    contentPaddingPx = topTabHorizontalPadding.toPx()
                )
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(1f)
            .graphicsLayer { clip = false }
    ) {
        // Velocity stretch is already folded into the shared bottom-bar transform.
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
                dockHeight = rowHeight,
                combinedBackdrop = indicatorCombinedBackdrop,
                pressProgress = topTabLensProgress,
                scaleX = indicatorScaleX,
                scaleY = indicatorScaleY,
                velocity = indicatorVelocity,
                isDark = isDarkTheme,
                shape = resolveTopTabIndicatorShape(
                    showIcon = showIcon,
                    showText = showText,
                    isMiuixNonGlass = isMiuixOfficialTabs,
                ),
                liquidGlassTuning = liquidGlassTuning
            )
        }
        if (shouldUseMd3DockBackedCapsule) {
            BiliPaiFloatingDockIndicator(
                visible = true,
                translationXPx = md3IndicatorTranslationXPx,
                panelOffsetPx = topTabIndicatorPanelOffsetPx,
                width = md3LiquidCapsuleWidth,
                height = dockIndicatorHeight,
                dockHeight = rowHeight,
                combinedBackdrop = indicatorCombinedBackdrop,
                pressProgress = topTabLensProgress,
                scaleX = indicatorScaleX,
                scaleY = indicatorScaleY,
                velocity = indicatorVelocity,
                isDark = isDarkTheme,
                shape = resolveTopTabIndicatorShape(
                    showIcon = showIcon,
                    showText = showText,
                    isMiuixNonGlass = isMiuixOfficialTabs,
                ),
                liquidGlassTuning = liquidGlassTuning
            )
        }
        if (shouldUseMd3LiquidCapsule) {
            BiliPaiFloatingDockIndicator(
                visible = true,
                translationXPx = md3IndicatorTranslationXPx,
                panelOffsetPx = topTabIndicatorPanelOffsetPx,
                width = md3LiquidCapsuleWidth,
                height = dockIndicatorHeight,
                dockHeight = rowHeight,
                combinedBackdrop = indicatorCombinedBackdrop,
                pressProgress = topTabLensProgress,
                scaleX = indicatorScaleX,
                scaleY = indicatorScaleY,
                velocity = indicatorVelocity,
                isDark = isDarkTheme,
                shape = resolveTopTabIndicatorShape(
                    showIcon = showIcon,
                    showText = showText,
                    isMiuixNonGlass = isMiuixOfficialTabs,
                ),
                liquidGlassTuning = liquidGlassTuning
            )
        }
    }
    if (shouldUseMovingIosCapsule || shouldUseMd3DockBackedCapsule || shouldUseMd3LiquidCapsule) {
        // 透明层同时承接点击回顶与胶囊直拖；普通页面区域仍由 Pager 接管侧滑。
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
        val indicatorDragModifier = if (indicatorDragEnabled) {
            Modifier.draggable(
                state = indicatorDraggableState,
                orientation = Orientation.Horizontal,
                onDragStarted = { _ -> onIndicatorDragStarted() },
                onDragStopped = { _ -> onIndicatorDragStopped() },
            )
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    translationX = indicatorGestureTranslationXPx + topTabIndicatorPanelOffsetPx
                }
                .width(indicatorGestureWidth)
                .height(dockIndicatorHeight)
                .zIndex(3f)
                .clickable(
                    interactionSource = indicatorInteractionSource,
                    indication = null
                ) {
                    onIndicatorTap()
                }
                .clearAndSetSemantics {}
                .then(indicatorDragModifier)
        )
    }
}

/**
 * 非玻璃 MD3 与皮肤顶栏的单层短指示线。位置经 [positionState] 注入，
 * 宽度/边界在本组件 scope 内逐帧插值。
 */
@Composable
private fun BoxScope.LightweightTopTabMd3Underline(
    positionState: androidx.compose.runtime.State<Float>,
    density: androidx.compose.ui.unit.Density,
    itemWidth: Dp,
    categoriesLastIndex: Int,
    md3ContentWidths: List<Dp>,
    md3IndicatorWidth: Dp,
    md3ContentPadding: Dp,
    showIcon: Boolean,
    showText: Boolean,
    shouldAnimateMd3Tap: Boolean,
    animatedUnderlineLeftPxProvider: () -> Float,
    animatedUnderlineRightPxProvider: () -> Float,
    rowScrollOffsetPxProvider: () -> Float,
    skinPlainContentColor: Color?,
) {
    val indicatorColor = when {
        skinPlainContentColor != null -> resolveHomeSkinTopTabIndicatorColor(skinPlainContentColor)
        else -> AppContentColors.accent()
    }
    val iconOnlyIndicator = showIcon && !showText
    val nativeIndicatorWidth = if (iconOnlyIndicator) {
        resolveIconOnlyTopTabIndicatorWidth()
    } else {
        val clampedPosition = positionState.value
            .coerceIn(0f, categoriesLastIndex.toFloat())
        val startIndex = clampedPosition.toInt()
        val endIndex = (startIndex + 1).coerceAtMost(categoriesLastIndex)
        lerp(
            md3ContentWidths.getOrElse(startIndex) { md3IndicatorWidth }.value,
            md3ContentWidths.getOrElse(endIndex) { md3IndicatorWidth }.value,
            clampedPosition - startIndex
        ).dp
    }
    val nativeUnderlineBounds = if (shouldAnimateMd3Tap) {
        resolveMd3TopTabUnderlineTapBounds(
            animatedLeftPx = animatedUnderlineLeftPxProvider(),
            animatedRightPx = animatedUnderlineRightPxProvider(),
            rowScrollOffsetPx = rowScrollOffsetPxProvider()
        )
    } else {
        with(density) {
            resolveMd3TopTabUnderlineBounds(
                absolutePagerPosition = positionState.value,
                itemWidthPx = itemWidth.toPx(),
                rowScrollOffsetPx = rowScrollOffsetPxProvider(),
                indicatorWidthPx = nativeIndicatorWidth.toPx(),
                contentPaddingPx = md3ContentPadding.toPx(),
            )
        }
    }
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .graphicsLayer {
                translationX = nativeUnderlineBounds.translationXPx
            }
            .offset(
                y = resolveMd3TopTabUnderlineCenterOffsetDp(
                    showIcon = showIcon,
                    showText = showText
                ).dp
            )
            .width(with(density) { nativeUnderlineBounds.widthPx.toDp() })
            .height(AppSpacingTokens.Micro * 1.5f)
            .clip(RoundedCornerShape(percent = 50))
            .background(indicatorColor)
    )
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
        com.android.purebilibili.core.ui.isMiuixNonGlassEnabled() -> AppContentColors.accent()
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
        TopTabLiquidColorMode.GLASS_VISIBLE -> adaptiveContentColorOverride ?: AppContentColors.primaryText()
        TopTabLiquidColorMode.NORMAL -> androidx.compose.ui.graphics.lerp(
            adaptiveContentColorOverride ?: unselectedColor,
            selectedColor,
            selectionFraction
        )
    }
    val containerColor = when {
        !drawContainer || colorMode == TopTabLiquidColorMode.GLASS_EXPORT -> Color.Transparent
        skinPlainStyle -> Color.Transparent
        com.android.purebilibili.core.ui.isMiuixNonGlassEnabled() &&
            presentation == AppTopTabPresentation.MOVING_CAPSULE &&
            colorMode == TopTabLiquidColorMode.NORMAL ->
            AppSurfaceTokens.selectionContainer().let { selectionColor ->
                selectionColor.copy(alpha = selectionColor.alpha * selectionFraction.coerceIn(0f, 1f))
            }
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
                            resolveHomeTopDockIconSizeDp(
                                if (showText) 0 else 1,
                                liquidGlassEnabled = colorMode != TopTabLiquidColorMode.NORMAL,
                            ).dp
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
    forceMaterialUnderline: Boolean = false,
    dockHorizontalPadding: Dp = AppSpacingTokens.ExtraSmall,
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
        forceMaterialUnderline = forceMaterialUnderline || LocalAppUiStyle.current == AppUiStyle.MATERIAL3,
        dockHorizontalPadding = dockHorizontalPadding,
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
        // Non-glass tabs do not record a content backdrop. Sampling that empty layer
        // during a drag produces a black capsule; keep the solid material throughout.
        return TopTabIndicatorBackdropPolicy(
            useIndicatorBackdrop = false,
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
     val iconSize = resolveHomeTopDockIconSizeDp(
         normalizedLabelMode,
         liquidGlassEnabled = false,
     ).dp
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
