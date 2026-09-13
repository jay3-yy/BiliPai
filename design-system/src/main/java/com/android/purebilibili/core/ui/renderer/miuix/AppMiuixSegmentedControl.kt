package com.android.purebilibili.core.ui.renderer.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.adaptiveSquircleBackground
import com.android.purebilibili.core.ui.AppChromeSizeTokens
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppSegmentedControlColors
import com.android.purebilibili.core.ui.components.resolveAppMiuixSegmentedColors
import com.android.purebilibili.core.ui.components.resolveAppSegmentedSelectionIndex
import com.android.purebilibili.core.ui.components.resolveAppMiuixTabContentColor
import com.android.purebilibili.core.ui.components.resolveAppMiuixTabTrackColor
import com.android.purebilibili.core.ui.resolveRoundedControlVisualGeometry
import com.android.purebilibili.core.ui.resolveMiuixNonGlassControlGeometry
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.resolveAppSegmentedLabelFontSizeSp

@Composable
internal fun <T> AppMiuixSegmentedControl(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    enabled: Boolean,
    colors: AppSegmentedControlColors,
    preferredCornerRadius: Dp,
    height: Dp? = null,
    modifier: Modifier,
    onSelectionChange: (T) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val longestLabelLength = remember(options) {
        options.maxOfOrNull { it.label.length } ?: 0
    }
    val labelFontSize = remember(options.size, longestLabelLength) {
        resolveAppSegmentedLabelFontSizeSp(options.size, longestLabelLength).sp
    }
    val targetHeight = height ?: 36.dp
    val cornerRadius = 8.dp
    val tabColors = resolveAppMiuixSegmentedColors(colors)
    val nonGlassMiuix = isMiuixNonGlassEnabled()
    val trackColor = resolveAppMiuixTabTrackColor(
        nonGlassMiuix = nonGlassMiuix,
        trackColor = tabColors.backgroundColor,
    )
    val inactiveContentColor = resolveAppMiuixTabContentColor(
        nonGlassMiuix = nonGlassMiuix,
        inactiveContentColor = tabColors.contentColor,
        readableContentColor = tabColors.selectedContentColor,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .adaptiveSquircleBackground(
                color = trackColor,
                cornerRadius = cornerRadius + 3.dp,
            )
            .squircleClip(cornerRadius + 3.dp)
            .padding(if (nonGlassMiuix) 0.dp else 3.dp)
            .then(if (!enabled) Modifier.semantics { disabled() } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(if (nonGlassMiuix) 4.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val selected = option.value == selectedValue
            val itemBackground = when {
                selected -> tabColors.selectedBackgroundColor
                nonGlassMiuix -> tabColors.backgroundColor
                else -> Color.Transparent
            }
            val contentColor = if (selected) {
                tabColors.selectedContentColor
            } else {
                inactiveContentColor
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = targetHeight.coerceAtLeast(28.dp))
                    .then(
                        if (selected && !isDark) {
                            Modifier.dropShadow(
                                shape = RoundedCornerShape(cornerRadius),
                                shadow = Shadow(radius = 3.dp, color = Color.Black, alpha = 0.08f)
                            )
                        } else Modifier
                    )
                    .adaptiveSquircleBackground(
                        color = itemBackground,
                        cornerRadius = cornerRadius,
                    )
                    .squircleClip(cornerRadius)
                    .clickable(
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelectionChange(option.value) },
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                AppText(
                    text = option.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    fontSize = labelFontSize,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
internal fun <T> AppMiuixTabRow(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    enabled: Boolean,
    scrollable: Boolean,
    minTabWidth: Dp,
    colors: AppSegmentedControlColors,
    preferredCornerRadius: Dp,
    height: Dp? = null,
    modifier: Modifier,
    indicatorPositionProvider: (() -> Float)? = null,
    onSelectionChange: (T) -> Unit,
) {
    if (isMiuixNonGlassEnabled()) {
        AppMiuixNonGlassTabs(
            options = options,
            selectedValue = selectedValue,
            enabled = enabled,
            compact = !scrollable && options.size <= 2,
            scrollable = scrollable,
            minTabWidth = minTabWidth,
            colors = colors,
            height = height,
            modifier = modifier,
            onSelectionChange = onSelectionChange,
        )
        return
    }
    val selectedIndex = resolveAppSegmentedSelectionIndex(options, selectedValue)
    val scrollState = rememberLazyListState()
    val tabColors = resolveAppMiuixSegmentedColors(colors)
    val geometry = resolveRoundedControlVisualGeometry(
        preferredCornerRadius = preferredCornerRadius,
        nativeMinimumHeight = height ?: AppChromeSizeTokens.MinimumTouchTarget,
    )
    TabRow(
        tabs = options.map { it.label },
        selectedTabIndex = selectedIndex,
        onTabSelected = { index ->
            if (enabled) options.getOrNull(index)?.let { onSelectionChange(it.value) }
        },
        // Respect the caller's measured width so compact two-option controls do not
        // expand to the full parent and consume the adjacent action area.
        // Upstream paints a rectangular track and only rounds the selected item.
        // Clip the stationary viewport as well, including during horizontal scrolling.
        modifier = modifier.squircleClip(geometry.cornerRadius),
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = tabColors.backgroundColor,
            contentColor = tabColors.contentColor,
            selectedBackgroundColor = tabColors.selectedBackgroundColor,
            selectedContentColor = tabColors.selectedContentColor,
        ),
        // 非 scrollable（如频道/状态切换）：交给 Miuix 按容器宽度均分，与 Material TabRow
        // 一致；scrollable（如时间表/分类）：minTabWidth 兜底保证可读。
        minWidth = if (scrollable) minTabWidth else 0.dp,
        maxWidth = Dp.Infinity,
        height = geometry.height,
        cornerRadius = geometry.cornerRadius,
        itemSpacing = AppSpacingTokens.Small,
        listState = if (scrollable) scrollState else null,
    )
}

/** Native tabs own selection/press feedback; the wrapper supplies measured geometry, container, and outline suppression. */
@Composable
private fun <T> AppMiuixNonGlassTabs(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    enabled: Boolean,
    compact: Boolean,
    scrollable: Boolean = false,
    minTabWidth: Dp,
    colors: AppSegmentedControlColors,
    height: Dp? = null,
    modifier: Modifier,
    onSelectionChange: (T) -> Unit,
) {
    val labels = options.map { it.label }
    val selectedIndex = resolveAppSegmentedSelectionIndex(options, selectedValue)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val tabColors = resolveAppMiuixSegmentedColors(colors)
    val inactiveContentColor = resolveAppMiuixTabContentColor(
        nonGlassMiuix = true,
        inactiveContentColor = tabColors.contentColor,
        readableContentColor = tabColors.selectedContentColor,
    )
    // Match upstream TabItem: main text with body1 size, bold when selected.
    val style = MiuixTheme.textStyles.main.copy(
        fontSize = MiuixTheme.textStyles.body1.fontSize,
        fontWeight = FontWeight.Bold,
    )
    val labelSizes = remember(labels, style, measurer, density) {
        labels.map { measurer.measure(AnnotatedString(it), style, maxLines = 1).size }
    }
    val textHeight = with(density) { (labelSizes.maxOfOrNull { it.height } ?: 0).toDp() }
    val geometry = resolveMiuixNonGlassControlGeometry(compact, textHeight)
    val targetHeight = height ?: geometry.height
    val readableWidth = if (compact && !scrollable) {
        0.dp
    } else {
        if (scrollable) maxOf(AppChromeSizeTokens.MinimumTouchTarget, minTabWidth) else 0.dp
    }
    val scrollState = rememberLazyListState()
    LaunchedEffect(selectedIndex, scrollable, scrollState) {
        if (scrollable) scrollState.animateScrollToItem(selectedIndex)
    }
    Box(
        modifier = modifier
            .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget)
            .then(if (!enabled) Modifier.semantics { disabled() } else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (scrollable) {
            LazyRow(
                state = scrollState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
            ) {
                itemsIndexed(options) { index, option ->
                    AppMiuixNonGlassTabItem(
                        label = option.label,
                        selected = index == selectedIndex,
                        enabled = enabled,
                        visualHeight = targetHeight,
                        cornerRadius = geometry.cornerRadius,
                        backgroundColor = if (index == selectedIndex) {
                            tabColors.selectedBackgroundColor
                        } else {
                            tabColors.backgroundColor
                        },
                        contentColor = if (index == selectedIndex) {
                            tabColors.selectedContentColor
                        } else {
                            inactiveContentColor
                        },
                        modifier = Modifier.widthIn(min = readableWidth),
                        onClick = { onSelectionChange(option.value) },
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                options.forEachIndexed { index, option ->
                    AppMiuixNonGlassTabItem(
                        label = option.label,
                        selected = index == selectedIndex,
                        enabled = enabled,
                        visualHeight = targetHeight,
                        cornerRadius = geometry.cornerRadius,
                        backgroundColor = if (index == selectedIndex) {
                            tabColors.selectedBackgroundColor
                        } else {
                            tabColors.backgroundColor
                        },
                        contentColor = if (index == selectedIndex) {
                            tabColors.selectedContentColor
                        } else {
                            inactiveContentColor
                        },
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectionChange(option.value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppMiuixNonGlassTabItem(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    visualHeight: Dp,
    cornerRadius: Dp,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = AppChromeSizeTokens.MinimumTouchTarget)
            .clickable(
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(visualHeight)
                .adaptiveSquircleBackground(
                    color = backgroundColor,
                    cornerRadius = cornerRadius,
                )
                .squircleClip(cornerRadius)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            AppText(
                text = label,
                color = contentColor,
                fontSize = MiuixTheme.textStyles.body1.fontSize,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
