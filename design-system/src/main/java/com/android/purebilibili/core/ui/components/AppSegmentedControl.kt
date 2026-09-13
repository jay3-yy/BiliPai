package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.rememberAppSegmentedControlPolicy
import com.android.purebilibili.core.ui.roundMatchedLiquidIndicatorHeightDp
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3SegmentedControl
import com.android.purebilibili.core.ui.renderer.material3.AppMaterial3TabRow
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixSegmentedControl
import com.android.purebilibili.core.ui.renderer.miuix.AppMiuixTabRow

data class AppSegmentOption<T>(
    val value: T,
    val label: String,
)

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

fun resolveReadableNativeTabMinWidth(
    requestedMinWidth: Dp,
    labels: List<String>,
    allowLabelOverflow: Boolean,
): Dp {
    if (!allowLabelOverflow || labels.isEmpty()) return requestedMinWidth
    val maxEstimatedWidthDp = labels.maxOfOrNull { label ->
        val textWidth = label.sumOf { char ->
            if (char.code in 0..127) 8 else 16
        }
        val padding = if (textWidth > 64) 28 else 24
        textWidth + padding
    } ?: 0
    val boundedEstimatedWidthDp = maxEstimatedWidthDp.coerceAtMost(320)
    return maxOf(requestedMinWidth, boundedEstimatedWidthDp.dp)
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
    onSelectionChange: (T) -> Unit,
) {
    if (options.isEmpty()) return
    val policy = rememberAppSegmentedControlPolicy()
    val materialColors = MaterialTheme.colorScheme
    val trackColor = AppSurfaceTokens.surfaceContainer()
    val activeCardColor = AppSurfaceTokens.surfaceContainerHighest()
    val activeTextColor = AppSurfaceTokens.onSurface()
    val inactiveTextColor = AppSurfaceTokens.onSurfaceContainerHigh()
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
    scrollable: Boolean = false,
    forceEqualWidth: Boolean = false,
    minTabWidth: Dp = 72.dp,
    compactMiuixWhenTwoOptions: Boolean = true,
    height: Dp? = null,
    allowLabelOverflow: Boolean = false,
    forceMaterial3: Boolean = false,
    indicatorPositionProvider: (() -> Float)? = null,
    onSelectionChange: (T) -> Unit,
) {
    if (options.isEmpty()) return
    val readableMinTabWidth = resolveReadableNativeTabMinWidth(
        requestedMinWidth = minTabWidth,
        labels = options.map { it.label },
        allowLabelOverflow = allowLabelOverflow,
    )
    val effectiveScrollable = !forceEqualWidth &&
        (scrollable || options.size > 3 || (readableMinTabWidth > minTabWidth && (!compactMiuixWhenTwoOptions || options.size > 2)))
    val viewportBoundedModifier = modifier.widthIn(
        max = LocalConfiguration.current.screenWidthDp.dp,
    )
    val policy = rememberAppSegmentedControlPolicy()
    val materialColors = MaterialTheme.colorScheme
    val trackColor = AppSurfaceTokens.surfaceContainer()
    val activeCardColor = AppSurfaceTokens.surfaceContainerHighest()
    val activeTextColor = AppSurfaceTokens.onSurface()
    val inactiveTextColor = AppSurfaceTokens.onSurfaceContainerHigh()
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
    val targetTabWidth = if (effectiveScrollable) readableMinTabWidth else minTabWidth
    when (if (forceMaterial3) AppSegmentedRenderer.MATERIAL3 else resolveAppSegmentedRenderer(policy.usesNativeTabRow)) {
        AppSegmentedRenderer.MATERIAL3 -> AppMaterial3TabRow(
            options = options,
            selectedValue = selectedValue,
            enabled = enabled,
            scrollable = effectiveScrollable,
            minTabWidth = targetTabWidth,
            allowLabelOverflow = allowLabelOverflow,
            indicatorPositionProvider = indicatorPositionProvider,
            modifier = viewportBoundedModifier,
            onSelectionChange = onSelectionChange,
        )
        AppSegmentedRenderer.MIUIX -> AppMiuixTabRow(
            options = options,
            selectedValue = selectedValue,
            enabled = enabled,
            scrollable = effectiveScrollable,
            minTabWidth = targetTabWidth,
            colors = colors,
            preferredCornerRadius = policy.preferredCornerRadius,
            height = height,
            modifier = if (!com.android.purebilibili.core.ui.isMiuixNonGlassEnabled() &&
                !effectiveScrollable && options.size == 2) {
                viewportBoundedModifier.requiredWidth(minTabWidth * options.size)
            } else {
                viewportBoundedModifier
            },
            indicatorPositionProvider = indicatorPositionProvider,
            onSelectionChange = onSelectionChange,
        )
    }
}
