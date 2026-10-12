package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.LocalImmersiveTopChromeActive
import com.android.purebilibili.core.ui.rememberAppSegmentedControlPolicy
import com.android.purebilibili.core.ui.roundMatchedLiquidIndicatorHeightDp
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3SegmentedControl
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3TabRow
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixSegmentedControl
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixTabRow

data class AppSegmentOption<T>(
    val value: T,
    val label: String,
)

/**
 * MD3 非玻璃 Tab 行的指示器形态:下划线(PiliPlus 首页/视频详情)或
 * tonal 胶囊(PiliPlus 搜索页)。MIUIX 与液态玻璃分支不消费该参数。
 */
enum class AppTabRowIndicatorPresentation {
    UNDERLINE,
    TONAL_PILL,
}

enum class AppSegmentedChrome {
    LIQUID,
    NATIVE,
}

enum class AppSegmentedRenderer {
    MATERIAL3,
    MIUIX,
}

data class AppSegmentedControlColors(
    val outerContainerColor: Color,
    val activeContainerColor: Color,
    val activeContentColor: Color,
    val inactiveContentColor: Color,
)

data class AppMiuixSegmentedColors(
    val backgroundColor: Color,
    val contentColor: Color,
    val selectedBackgroundColor: Color,
    val selectedContentColor: Color,
)

data class AppLiquidSegmentedControlSpec(
    val itemWidthDp: Int,
    val heightDp: Int,
    val indicatorHeightDp: Int,
    val labelFontSizeSp: Int,
    val liquidGlassEffectsEnabled: Boolean,
    val tapPressRefractionEnabled: Boolean,
)

fun resolveAppSegmentedChrome(
    usesMaterialFallback: Boolean,
    nativeLiquidGlassEnabled: Boolean,
): AppSegmentedChrome = if (usesMaterialFallback && !nativeLiquidGlassEnabled) {
    AppSegmentedChrome.NATIVE
} else {
    AppSegmentedChrome.LIQUID
}

fun resolveAppSegmentedRenderer(usesNativeTabRow: Boolean): AppSegmentedRenderer =
    if (usesNativeTabRow) AppSegmentedRenderer.MIUIX else AppSegmentedRenderer.MATERIAL3

fun resolveAppSegmentedLabelFontSizeSp(
    optionCount: Int,
    longestLabelLength: Int,
): Float = when {
    optionCount >= 5 -> 12f
    optionCount >= 4 && longestLabelLength >= 3 -> 12f
    optionCount >= 4 -> 13f
    optionCount >= 3 && longestLabelLength >= 4 -> 13f
    longestLabelLength >= 7 -> 13f
    longestLabelLength >= 5 -> 14f
    else -> 15f
}

/** Preserve compact-label ratios while honoring the active theme and app font scale. */
fun resolveAppSegmentedLabelFontSize(
    themeLabelFontSize: androidx.compose.ui.unit.TextUnit,
    optionCount: Int,
    longestLabelLength: Int,
): androidx.compose.ui.unit.TextUnit =
    themeLabelFontSize * (resolveAppSegmentedLabelFontSizeSp(optionCount, longestLabelLength) / 15f)

fun shouldFillMaxWidthAppSegmentedControl(
    optionCount: Int,
    longestLabelLength: Int,
): Boolean = optionCount >= 2 || longestLabelLength >= 1

fun shouldUseCompactMiuixTabRow(
    optionCount: Int,
    scrollable: Boolean,
    compactWhenTwoOptions: Boolean,
): Boolean = optionCount == 2 && !scrollable && compactWhenTwoOptions

fun resolveCompactMiuixTabRowWidth(
    viewportWidth: Dp,
    minTabWidth: Dp,
    optionCount: Int,
    scrollable: Boolean,
): Dp = if (optionCount == 2 && !scrollable) minTabWidth * 2 else viewportWidth

internal fun resolveMeasuredTabMinWidth(
    requestedMinWidth: Dp,
    labelWidths: List<Dp>,
    horizontalPadding: Dp = 12.dp,
): Dp = maxOf(requestedMinWidth, (labelWidths.maxOrNull() ?: 0.dp) + horizontalPadding * 2)

/** Keeps the established liquid-glass sizing contract; measured widths are for Miuix non-glass. */
fun resolveLiquidGlassTabMinWidth(
    requestedMinWidth: Dp,
    labels: List<String>,
    allowLabelOverflow: Boolean,
): Dp {
    if (!allowLabelOverflow || labels.isEmpty()) return requestedMinWidth
    val estimatedWidth = labels.maxOfOrNull { label ->
        val textWidth = label.sumOf { char -> if (char.code in 0..127) 8 else 16 }
        textWidth + if (textWidth > 64) 28 else 24
    } ?: 0
    return maxOf(requestedMinWidth, estimatedWidth.coerceAtMost(320).dp)
}

/** Legacy liquid-glass compact sizing retained independently of non-glass measurement. */
private fun resolveLiquidGlassLabelContentMinWidth(labels: List<String>): Dp {
    if (labels.isEmpty()) return 0.dp
    val estimatedWidth = labels.maxOfOrNull { label ->
        val textWidth = label.sumOf { char -> if (char.code in 0..127) 8 else 16 }
        textWidth + if (textWidth > 64) 28 else 24
    } ?: 0
    return estimatedWidth.coerceIn(48, 320).dp
}

@Composable
fun rememberMeasuredTabMinWidth(
    requestedMinWidth: Dp,
    labels: List<String>,
    textStyle: androidx.compose.ui.text.TextStyle,
): Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    return androidx.compose.runtime.remember(requestedMinWidth, labels, textStyle, density, measurer) {
        resolveMeasuredTabMinWidth(
            requestedMinWidth,
            labels.map { label ->
                with(density) {
                    measurer.measure(
                        androidx.compose.ui.text.AnnotatedString(label), textStyle, maxLines = 1,
                    ).size.width.toDp()
                }
            },
        )
    }
}

enum class MiuixNonGlassTabItemWidthMode {
    CONTENT,
    EQUAL_TO_LONGEST_LABEL,
}

internal fun shouldEqualizeMiuixNonGlassTabItems(
    widthMode: MiuixNonGlassTabItemWidthMode,
    isMiuixNonGlass: Boolean,
    optionCount: Int,
): Boolean = widthMode == MiuixNonGlassTabItemWidthMode.EQUAL_TO_LONGEST_LABEL &&
    isMiuixNonGlass &&
    optionCount > 1

/** Upstream Miuix TabRow itemSpacing. */
internal const val MIUIX_NON_GLASS_TAB_ITEM_SPACING_DP = 9

internal fun shouldStretchMiuixNonGlassTabRowToTrack(
    compact: Boolean,
    scrollable: Boolean,
    optionCount: Int,
): Boolean = compact && !scrollable && optionCount in 1..2

internal fun resolveEqualMiuixNonGlassTabItemWidth(
    longestLabelWidth: Dp,
    minTabWidth: Dp,
    horizontalContentPadding: Dp = AppSpacingTokens.Small,
): Dp = maxOf(
    AppChromeSizeTokens.MinimumTouchTarget,
    minTabWidth,
    longestLabelWidth + horizontalContentPadding * 2,
)

internal fun resolveMiuixTabMinWidth(
    requestedMinWidth: Dp,
    sharedMinWidth: Dp,
    contentSizedItems: Boolean,
): Dp = if (contentSizedItems) requestedMinWidth else sharedMinWidth

/**
 * PiliPlus scrollable underlines hug the label. Material's 90dp floor, and the
 * 48–96dp touch floors used by pill tabs, leave a wide gap between short labels.
 */
internal fun resolvePiliPlusScrollableUnderlineMinWidth(): Dp = 0.dp

fun resolveMiuixNonGlassContentTabItemWidths(
    labelWidths: List<Dp>,
    minTabWidth: Dp,
    maxTabWidth: Dp = 320.dp,
    horizontalContentPadding: Dp = 24.dp,
): List<Dp> = labelWidths.map { labelWidth ->
    (labelWidth + horizontalContentPadding).coerceIn(minTabWidth, maxTabWidth)
}

fun resolveAppLiquidSegmentedControlSpec(
    itemCount: Int,
    hasExternalBackdrop: Boolean,
    longestLabelLength: Int = 0,
): AppLiquidSegmentedControlSpec {
    // 液金分段控件为固定视觉形态，不随主题风格变化（历史实现固定使用 iOS 紧凑尺寸 44dp）。
    val liquidControlHeightDp = 44
    return AppLiquidSegmentedControlSpec(
        itemWidthDp = if (itemCount >= 4) 56 else 66,
        heightDp = liquidControlHeightDp,
        indicatorHeightDp = roundMatchedLiquidIndicatorHeightDp(liquidControlHeightDp.toFloat()),
        labelFontSizeSp = resolveAppSegmentedLabelFontSizeSp(
            optionCount = itemCount,
            longestLabelLength = longestLabelLength,
        ).toInt(),
        liquidGlassEffectsEnabled = hasExternalBackdrop,
        tapPressRefractionEnabled = false,
    )
}

fun resolveAppSegmentedLiquidGlassRequest(
    forceLiquidIndicator: Boolean,
    hasExternalBackdrop: Boolean,
): Boolean? = if (forceLiquidIndicator && hasExternalBackdrop) true else null

fun resolveAppSegmentedControlColors(
    usesMaterialColorTokens: Boolean,
    materialPrimaryContainer: Color,
    materialOnPrimaryContainer: Color,
    materialSurfaceContainerHigh: Color,
    materialOnSurfaceVariant: Color,
    miuixSecondaryContainer: Color,
    miuixOnSecondaryContainer: Color,
    miuixSurfaceContainerHigh: Color,
    miuixOnSurfaceVariantSummary: Color,
): AppSegmentedControlColors = if (usesMaterialColorTokens) {
    AppSegmentedControlColors(
        outerContainerColor = materialSurfaceContainerHigh,
        activeContainerColor = materialPrimaryContainer,
        activeContentColor = materialOnPrimaryContainer,
        inactiveContentColor = materialOnSurfaceVariant,
    )
} else {
    AppSegmentedControlColors(
        outerContainerColor = miuixSurfaceContainerHigh,
        activeContainerColor = miuixSecondaryContainer,
        activeContentColor = miuixOnSecondaryContainer,
        inactiveContentColor = miuixOnSurfaceVariantSummary,
    )
}

fun resolveAppMiuixSegmentedColors(
    colors: AppSegmentedControlColors,
): AppMiuixSegmentedColors = AppMiuixSegmentedColors(
    backgroundColor = colors.outerContainerColor,
    contentColor = colors.inactiveContentColor,
    selectedBackgroundColor = colors.activeContainerColor,
    selectedContentColor = colors.activeContentColor,
)

/** Only individual outlined items need opaque fills over scrolling chrome; the track stays clear. */
fun shouldUseOpaqueMiuixTabBackdrop(nonGlassMiuix: Boolean, immersiveTopChrome: Boolean): Boolean =
    nonGlassMiuix && immersiveTopChrome

fun resolveAppMiuixTabTrackColor(
    nonGlassMiuix: Boolean,
    trackColor: Color,
): Color = if (nonGlassMiuix) Color.Transparent else trackColor

fun resolveAppMiuixTabContentColor(
    nonGlassMiuix: Boolean,
    inactiveContentColor: Color,
    readableContentColor: Color,
): Color = if (nonGlassMiuix) readableContentColor else inactiveContentColor

fun <T> resolveAppSegmentedSelectionIndex(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
): Int {
    if (options.isEmpty()) return 0
    return options.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)
}

@Composable
fun <T> AppNativeSegmentedControl(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    indicatorPositionProvider: (() -> Float)? = null,
    onSelectionChange: (T) -> Unit,
) {
    if (options.isEmpty()) return
    val policy = rememberAppSegmentedControlPolicy()
    val materialColors = MaterialTheme.colorScheme
    val isImmersiveTopChrome = LocalImmersiveTopChromeActive.current
    val isMiuixNonGlass = isMiuixNonGlassEnabled()
    val trackColor = if (isImmersiveTopChrome || isMiuixNonGlass) {
        Color.Transparent
    } else {
        AppSurfaceTokens.surfaceContainerHigh()
    }
    val activeCardColor = if (isMiuixNonGlass) {
        AppSurfaceTokens.surfaceContainerHigh()
    } else {
        AppSurfaceTokens.surfaceContainer()
    }
    val activeTextColor = AppSurfaceTokens.onSurface()
    val inactiveTextColor = AppSurfaceTokens.onSurfaceVariantSummary()
    val colors = resolveAppSegmentedControlColors(
        usesMaterialColorTokens = policy.usesMaterialColorTokens,
        materialPrimaryContainer = materialColors.primaryContainer,
        materialOnPrimaryContainer = materialColors.onPrimaryContainer,
        materialSurfaceContainerHigh = materialColors.surfaceContainerHigh,
        materialOnSurfaceVariant = materialColors.onSurfaceVariant,
        miuixSecondaryContainer = activeCardColor,
        miuixOnSecondaryContainer = activeTextColor,
        miuixSurfaceContainerHigh = trackColor,
        miuixOnSurfaceVariantSummary = inactiveTextColor,
    )
    when (resolveAppSegmentedRenderer(policy.usesNativeTabRow)) {
        AppSegmentedRenderer.MATERIAL3 -> AppMaterial3SegmentedControl(
            options = options,
            selectedValue = selectedValue,
            enabled = enabled,
            colors = colors,
            modifier = modifier,
            onSelectionChange = onSelectionChange,
        )
        AppSegmentedRenderer.MIUIX -> AppMiuixSegmentedControl(
            options = options,
            selectedValue = selectedValue,
            enabled = enabled,
            colors = colors,
            preferredCornerRadius = policy.preferredCornerRadius,
            modifier = modifier,
            indicatorPositionProvider = indicatorPositionProvider,
            onSelectionChange = onSelectionChange,
        )
    }
}

/**
 * Theme-adaptive page tabs. Material 3 renders a primary tab row; MIUIX renders
 * its native TabRow. Use this for sibling pages, and segmented buttons for
 * compact option selection inside a page.
 */
@Composable
fun <T> AppNativeTabRow(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    centerContent: Boolean = false,
    scrollable: Boolean = false,
    forceEqualWidth: Boolean = false,
    minTabWidth: Dp = 72.dp,
    compactMiuixWhenTwoOptions: Boolean = true,
    height: Dp? = null,
    allowLabelOverflow: Boolean = false,
    forceMaterial3: Boolean = false,
    indicatorPresentation: AppTabRowIndicatorPresentation = AppTabRowIndicatorPresentation.UNDERLINE,
    indicatorPositionProvider: (() -> Float)? = null,
    miuixNonGlassItemWidthMode: MiuixNonGlassTabItemWidthMode =
        MiuixNonGlassTabItemWidthMode.CONTENT,
    contentSizedMiuixNonGlassItems: Boolean = false,
    contentSizedMiuixNonGlassMaxItemWidth: Dp = 320.dp,
    // Miuix non-glass tabs keep their individual item surfaces, without adding
    // an extra full-width dock behind the items. Callers that intentionally own
    // a track (for example a liquid-glass rail) can still opt in explicitly.
    drawMiuixNonGlassTrack: Boolean = false,
    onSelectionChange: (T) -> Unit,
) {
    if (options.isEmpty()) return
    val labelStyle = if (isMiuixNonGlassEnabled()) {
        top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.main.copy(
            fontSize = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1.fontSize,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    } else {
        MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
    val labelContentMinWidth = rememberMeasuredTabMinWidth(
        requestedMinWidth = AppChromeSizeTokens.MinimumTouchTarget,
        labels = options.map { it.label },
        textStyle = labelStyle,
    )
    val nonGlassMiuix = isMiuixNonGlassEnabled()
    val resolvedLabelContentMinWidth = if (nonGlassMiuix) {
        labelContentMinWidth
    } else {
        resolveLiquidGlassLabelContentMinWidth(options.map { it.label })
    }
    val readableMinTabWidth = if (nonGlassMiuix) {
        maxOf(minTabWidth, labelContentMinWidth)
    } else {
        resolveLiquidGlassTabMinWidth(minTabWidth, options.map { it.label }, allowLabelOverflow)
    }
    val compactItemWidth = maxOf(minTabWidth, readableMinTabWidth, resolvedLabelContentMinWidth)
    val equalizeMiuixNonGlassItems = shouldEqualizeMiuixNonGlassTabItems(
        widthMode = miuixNonGlassItemWidthMode,
        isMiuixNonGlass = com.android.purebilibili.core.ui.isMiuixNonGlassEnabled(),
        optionCount = options.size,
    )
    val effectiveScrollable = !forceEqualWidth &&
        (equalizeMiuixNonGlassItems || scrollable || options.size > 3 ||
            (readableMinTabWidth > minTabWidth && options.size > 2))
    // Ordinary Miuix rails own each item's surface and hit target. Upstream TabRow
    // uses one moving background with cached equal-width geometry instead.
    val useContentSizedMiuixItems = nonGlassMiuix &&
        miuixNonGlassItemWidthMode == MiuixNonGlassTabItemWidthMode.CONTENT &&
        effectiveScrollable
    val policy = rememberAppSegmentedControlPolicy()
    val materialColors = MaterialTheme.colorScheme
    val isImmersiveTopChrome = LocalImmersiveTopChromeActive.current
    val isMiuixNonGlass = isMiuixNonGlassEnabled()
    val trackColor = if (isImmersiveTopChrome || isMiuixNonGlass) {
        Color.Transparent
    } else {
        AppSurfaceTokens.surfaceContainerHigh()
    }
    val activeCardColor = if (isMiuixNonGlass) {
        AppSurfaceTokens.surfaceContainerHigh()
    } else {
        AppSurfaceTokens.surfaceContainer()
    }
    val activeTextColor = AppSurfaceTokens.onSurface()
    val inactiveTextColor = AppSurfaceTokens.onSurfaceVariantSummary()
    val colors = resolveAppSegmentedControlColors(
        usesMaterialColorTokens = policy.usesMaterialColorTokens,
        materialPrimaryContainer = materialColors.primaryContainer,
        materialOnPrimaryContainer = materialColors.onPrimaryContainer,
        materialSurfaceContainerHigh = materialColors.surfaceContainerHigh,
        materialOnSurfaceVariant = materialColors.onSurfaceVariant,
        miuixSecondaryContainer = activeCardColor,
        miuixOnSecondaryContainer = activeTextColor,
        miuixSurfaceContainerHigh = trackColor,
        miuixOnSurfaceVariantSummary = inactiveTextColor,
    )
    val targetTabWidth = if (effectiveScrollable && !useContentSizedMiuixItems) {
        readableMinTabWidth
    } else {
        compactItemWidth
    }
    when (if (forceMaterial3) AppSegmentedRenderer.MATERIAL3 else resolveAppSegmentedRenderer(policy.usesNativeTabRow)) {
        AppSegmentedRenderer.MATERIAL3 -> AppMaterial3TabRow(
            options = options,
            selectedValue = selectedValue,
            enabled = enabled,
            scrollable = effectiveScrollable,
            centerContent = centerContent,
            // MD3 underlines follow PiliPlus: each tab is as wide as its label.
            // Miuix keeps the touch/content floor above.
            minTabWidth = resolvePiliPlusScrollableUnderlineMinWidth(),
            allowLabelOverflow = allowLabelOverflow,
            indicatorPresentation = indicatorPresentation,
            indicatorPositionProvider = indicatorPositionProvider,
            modifier = modifier,
            onSelectionChange = onSelectionChange,
        )
        AppSegmentedRenderer.MIUIX -> AppMiuixTabRow(
            options = options,
            selectedValue = selectedValue,
            enabled = enabled,
            scrollable = effectiveScrollable,
            // Content-sized tabs measure each label independently. A shared longest-label
            // minimum would enlarge every short tab when a long collection title appears.
            minTabWidth = resolveMiuixTabMinWidth(
                requestedMinWidth = minTabWidth,
                sharedMinWidth = targetTabWidth,
                contentSizedItems = useContentSizedMiuixItems,
            ),
            colors = colors,
            preferredCornerRadius = policy.preferredCornerRadius,
            height = height,
            modifier = if (shouldUseCompactMiuixTabRow(options.size, effectiveScrollable, compactMiuixWhenTwoOptions)) {
                modifier.width(compactItemWidth * options.size)
            } else {
                modifier
            },
            indicatorPositionProvider = indicatorPositionProvider,
            equalizeScrollableItemWidths = equalizeMiuixNonGlassItems,
            centerContent = centerContent,
            contentSizedNonGlassItems = useContentSizedMiuixItems,
            contentSizedNonGlassMaxItemWidth = contentSizedMiuixNonGlassMaxItemWidth,
            drawNonGlassTrack = drawMiuixNonGlassTrack,
            onSelectionChange = onSelectionChange,
        )
    }
}
