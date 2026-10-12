package com.android.purebilibili.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppNativeSegmentedControl
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppSegmentedChrome
import com.android.purebilibili.core.ui.components.resolveAppLiquidSegmentedControlSpec
import com.android.purebilibili.core.ui.components.resolveAppSegmentedChrome
import com.android.purebilibili.core.ui.components.resolveAppSegmentedSelectionIndex
import com.android.purebilibili.core.ui.rememberAppSegmentedControlPolicy
import com.android.purebilibili.feature.home.components.BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_HEIGHT_DP
import com.android.purebilibili.feature.home.components.BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_INDICATOR_HEIGHT_DP
import com.android.purebilibili.feature.home.components.BottomBarLiquidSegmentedControl

@Composable
internal fun <T> AppSegmentedControl(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_HEIGHT_DP.dp,
    indicatorHeight: Dp = BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_INDICATOR_HEIGHT_DP.dp,
    labelFontSize: TextUnit = TextUnit.Unspecified,
    tapPressRefractionEnabled: Boolean = true,
    dragSelectionEnabled: Boolean = true,
    containerColorOverride: Color? = null,
    indicatorIdleSurfaceColorOverride: Color? = null,
    onSelectionChange: (T) -> Unit,
) {
    if (options.isEmpty()) return
    val policy = rememberAppSegmentedControlPolicy()
    val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    when (
        resolveAppSegmentedChrome(
            usesMaterialFallback = policy.usesMaterialFallback,
            nativeLiquidGlassEnabled = liquidGlassEnabled,
        )
    ) {
        AppSegmentedChrome.NATIVE -> AppNativeSegmentedControl(
            options = options,
            selectedValue = selectedValue,
            modifier = modifier,
            enabled = enabled,
            onSelectionChange = onSelectionChange,
        )
        AppSegmentedChrome.LIQUID -> AppLiquidSegmentedControlHost(
            options = options,
            selectedValue = selectedValue,
            modifier = modifier,
            enabled = enabled,
            height = height,
            indicatorHeight = indicatorHeight,
            labelFontSize = labelFontSize,
            tapPressRefractionEnabled = tapPressRefractionEnabled,
            dragSelectionEnabled = dragSelectionEnabled,
            containerColorOverride = containerColorOverride,
            indicatorIdleSurfaceColorOverride = indicatorIdleSurfaceColorOverride,
            onSelectionChange = onSelectionChange,
        )
    }
}

@Composable
private fun <T> AppLiquidSegmentedControlHost(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    modifier: Modifier,
    enabled: Boolean,
    height: Dp,
    indicatorHeight: Dp,
    labelFontSize: TextUnit,
    tapPressRefractionEnabled: Boolean,
    dragSelectionEnabled: Boolean,
    containerColorOverride: Color?,
    indicatorIdleSurfaceColorOverride: Color?,
    onSelectionChange: (T) -> Unit,
) {
    val selectedIndex = resolveAppSegmentedSelectionIndex(options, selectedValue)
    val longestLabelLength = options.maxOfOrNull { it.label.length } ?: 0
    val spec = resolveAppLiquidSegmentedControlSpec(
        itemCount = options.size,
        hasExternalBackdrop = true,
        longestLabelLength = longestLabelLength,
    )
    val usesDefaultBottomBarSizing =
        height == BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_HEIGHT_DP.dp &&
            indicatorHeight == BOTTOM_BAR_LIQUID_SEGMENTED_CONTROL_INDICATOR_HEIGHT_DP.dp &&
            labelFontSize == TextUnit.Unspecified
    val resolvedHeight = if (usesDefaultBottomBarSizing) spec.heightDp.dp else height
    val resolvedIndicatorHeight =
        if (usesDefaultBottomBarSizing) spec.indicatorHeightDp.dp else indicatorHeight
    val resolvedLabelFontSize =
        if (labelFontSize == TextUnit.Unspecified) com.android.purebilibili.core.ui.appTabLabelFontSize() else labelFontSize
    val resolvedTapPressRefractionEnabled =
        if (usesDefaultBottomBarSizing) spec.tapPressRefractionEnabled else tapPressRefractionEnabled

    BottomBarLiquidSegmentedControl(
        items = options.map { it.label },
        selectedIndex = selectedIndex,
        onSelected = { index ->
            options.getOrNull(index)?.let { onSelectionChange(it.value) }
        },
        modifier = modifier,
        enabled = enabled,
        itemWidth = null,
        height = resolvedHeight,
        indicatorHeight = resolvedIndicatorHeight,
        labelFontSize = resolvedLabelFontSize,
        liquidGlassEffectsEnabled = spec.liquidGlassEffectsEnabled,
        dragSelectionEnabled = dragSelectionEnabled,
        tapPressRefractionEnabled = resolvedTapPressRefractionEnabled,
        containerColorOverride = containerColorOverride,
        indicatorIdleSurfaceColorOverride = indicatorIdleSurfaceColorOverride,
    )
}
