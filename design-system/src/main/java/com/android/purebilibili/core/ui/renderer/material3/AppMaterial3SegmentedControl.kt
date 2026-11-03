package com.android.purebilibili.core.ui.renderer.material3

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.components.AppTabRowIndicatorPresentation
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppSegmentedControlColors
import com.android.purebilibili.core.ui.components.AppPrimaryScrollableTabRow
import com.android.purebilibili.core.ui.components.AppPrimaryTabRow
import com.android.purebilibili.core.ui.components.resolveAppSegmentedLabelFontSize
import com.android.purebilibili.core.ui.components.resolveAppSegmentedSelectionIndex
import com.android.purebilibili.core.ui.components.resolveAppTabSlotGeometry

@Composable
internal fun <T> AppMaterial3SegmentedControl(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    enabled: Boolean,
    colors: AppSegmentedControlColors,
    modifier: Modifier,
    height: Dp? = null,
    itemWidth: Dp? = null,
    labelFontSize: TextUnit = TextUnit.Unspecified,
    containerHorizontalPadding: Dp = 0.dp,
    onSelectionChange: (T) -> Unit,
) {
    val longestLabelLength = remember(options) {
        options.maxOfOrNull { it.label.length } ?: 0
    }
    val resolvedLabelFontSize = if (labelFontSize.isSpecified) labelFontSize else resolveAppSegmentedLabelFontSize(
        MaterialTheme.typography.labelLarge.fontSize, options.size, longestLabelLength
    )
    BoxWithConstraints(
        modifier = if (itemWidth != null) {
            modifier.width(itemWidth * options.size + containerHorizontalPadding * 2)
        } else modifier.fillMaxWidth(),
    ) {
    val padding = if (itemWidth != null) {
        resolveAppTabSlotGeometry(options.size, itemWidth, containerHorizontalPadding, maxWidth, false).horizontalPadding
    } else containerHorizontalPadding
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = padding)
            .then(if (height != null) Modifier.height(height) else Modifier),
        space = if (itemWidth != null) 0.dp else SegmentedButtonDefaults.BorderWidth,
    ) {
        options.forEachIndexed { index, option ->
            val selected = option.value == selectedValue
            SegmentedButton(
                selected = selected,
                onClick = { onSelectionChange(option.value) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.activeContainerColor,
                    activeContentColor = colors.activeContentColor,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = colors.inactiveContentColor,
                    disabledActiveContainerColor = colors.activeContainerColor.copy(alpha = 0.35f),
                    disabledActiveContentColor = colors.activeContentColor.copy(alpha = 0.55f),
                    disabledInactiveContainerColor = Color.Transparent,
                    disabledInactiveContentColor = colors.inactiveContentColor.copy(alpha = 0.45f),
                ),
                border = SegmentedButtonDefaults.borderStroke(color = MaterialTheme.colorScheme.outline),
                modifier = Modifier.weight(1f)
                    .then(if (height != null) Modifier.height(height) else Modifier),
                icon = {
                    if (itemWidth == null && options.size <= 3) SegmentedButtonDefaults.Icon(active = selected)
                },
            ) {
                Text(
                    text = option.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = resolvedLabelFontSize),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
    }
}

@Composable
internal fun <T> AppMaterial3TabRow(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    enabled: Boolean,
    scrollable: Boolean,
    centerContent: Boolean = false,
    minTabWidth: Dp,
    modifier: Modifier,
    allowLabelOverflow: Boolean = false,
    indicatorPresentation: AppTabRowIndicatorPresentation = AppTabRowIndicatorPresentation.UNDERLINE,
    indicatorPositionProvider: (() -> Float)? = null,
    height: Dp? = null,
    fixedItemWidth: Dp? = null,
    labelFontSize: TextUnit = TextUnit.Unspecified,
    containerHorizontalPadding: Dp = 0.dp,
    centerSelectedTab: Boolean = false,
    onSelectionChange: (T) -> Unit,
) {
    val selectedIndex = resolveAppSegmentedSelectionIndex(options, selectedValue)
    // Tabs are navigation labels, so adding categories must not shrink their text.
    // Use the same theme size for the pill, underline, and width measurement paths.
    val labelStyle = if (labelFontSize.isSpecified) {
        MaterialTheme.typography.titleMedium.copy(fontSize = labelFontSize)
    } else MaterialTheme.typography.titleMedium
    if (indicatorPresentation == AppTabRowIndicatorPresentation.TONAL_PILL) {
        AppTonalPillTabRow(
            options = options,
            selectedValue = selectedValue,
            onSelectionChange = onSelectionChange,
            modifier = if (centerContent) modifier.wrapContentWidth(Alignment.CenterHorizontally) else modifier,
            enabled = enabled,
            scrollable = scrollable,
            height = height ?: resolveTonalPillTabDefaultHeight(),
            labelFontSize = labelStyle.fontSize,
            fixedItemWidth = fixedItemWidth,
            containerHorizontalPadding = containerHorizontalPadding,
            indicatorPositionProvider = indicatorPositionProvider,
            centerSelectedTab = centerSelectedTab,
        )
        return
    }
    // Only native MD3 underlines need label geometry. Glass pills and Miuix keep
    // their own sizing; equal-width hit targets and Tab layout stay unchanged.
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelContentWidths = options.map { option ->
        with(density) {
            textMeasurer.measure(
                text = option.label,
                style = labelStyle.copy(
                    fontWeight = if (option.value == selectedValue) FontWeight.SemiBold else FontWeight.Medium,
                ),
                maxLines = 1,
            ).size.width.toDp()
        }
    }
    val tabs: @Composable (Dp?) -> Unit = { slotWidth ->
        options.forEach { option ->
            val selected = option.value == selectedValue
            // Keep Tab's `text =` slot so TabRow can subtract HorizontalTextPadding
            // when sizing the underline. Overflow the 16.dp padding instead of
            // ellipsizing 直播间 / UP主 / 默认排序 when many tabs share one row.
            // TabRow's default contentColor is primary for every tab; pin the M3
            // standard so only the selected label carries the theme color.
            Tab(
                selected = selected,
                onClick = { onSelectionChange(option.value) },
                enabled = enabled,
                modifier = Modifier
                    .then(if (slotWidth != null) Modifier.width(slotWidth).clipToBounds() else Modifier)
                    .then(if (height != null) Modifier.height(height) else Modifier),
                selectedContentColor = AppContentColors.link(),
                unselectedContentColor = AppContentColors.secondaryText(),
                text = {
                    Text(
                        text = option.label,
                        modifier = Modifier.then(
                            if (allowLabelOverflow) {
                                Modifier.wrapContentWidth(
                                    align = Alignment.CenterHorizontally,
                                    unbounded = true,
                                )
                            } else {
                                Modifier
                            }
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = if (allowLabelOverflow) {
                            TextOverflow.Visible
                        } else {
                            TextOverflow.Clip
                        },
                        style = labelStyle,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    )
                },
            )
        }
    }
    if (fixedItemWidth != null) {
        BoxWithConstraints(
            modifier = modifier.width(fixedItemWidth * options.size + containerHorizontalPadding * 2),
            contentAlignment = if (centerContent) Alignment.Center else Alignment.CenterStart,
        ) {
            val geometry = resolveAppTabSlotGeometry(
                options.size, fixedItemWidth, containerHorizontalPadding, maxWidth, scrollable,
            )
            if (scrollable) {
                AppPrimaryScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    modifier = Modifier.width(geometry.containerWidth),
                    containerColor = Color.Transparent,
                    edgePadding = geometry.horizontalPadding,
                    minTabWidth = geometry.itemWidth,
                    indicatorPositionProvider = indicatorPositionProvider,
                    labelContentWidths = labelContentWidths,
                    tabs = { tabs(geometry.itemWidth) },
                )
            } else {
                AppPrimaryTabRow(
                    selectedTabIndex = selectedIndex,
                    modifier = Modifier.width(geometry.containerWidth).padding(horizontal = geometry.horizontalPadding),
                    containerColor = Color.Transparent,
                    indicatorPositionProvider = indicatorPositionProvider,
                    labelContentWidths = labelContentWidths,
                    tabs = { tabs(geometry.itemWidth) },
                )
            }
        }
    } else if (scrollable) {
        val style = labelStyle.copy(fontWeight = FontWeight.SemiBold)
        val contentWidth = options.fold(containerHorizontalPadding * 2) { width, option ->
            width + maxOf(minTabWidth, with(density) {
                textMeasurer.measure(option.label, style = style, maxLines = 1).size.width.toDp()
            } + 32.dp)
        }
        BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
            AppPrimaryScrollableTabRow(
                selectedTabIndex = selectedIndex,
                modifier = if (centerContent) Modifier.width(minOf(contentWidth, maxWidth)) else Modifier.fillMaxWidth(),
                containerColor = Color.Transparent,
                edgePadding = containerHorizontalPadding,
                minTabWidth = minTabWidth,
                indicatorPositionProvider = indicatorPositionProvider,
                labelContentWidths = labelContentWidths,
                tabs = { tabs(null) },
            )
        }
    } else {
        AppPrimaryTabRow(
            selectedTabIndex = selectedIndex,
            modifier = modifier.fillMaxWidth().padding(horizontal = containerHorizontalPadding),
            containerColor = Color.Transparent,
            indicatorPositionProvider = indicatorPositionProvider,
            labelContentWidths = labelContentWidths,
            tabs = { tabs(null) },
        )
    }
}
