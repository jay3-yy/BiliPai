package com.android.purebilibili.core.ui.renderer.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import com.android.purebilibili.core.ui.components.resolveMeasuredTabVisibilityDelta
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection
import com.android.purebilibili.core.ui.LocalImmersiveTopChromeActive
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.shouldUseOpaqueMiuixTabBackdrop
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
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
import com.android.purebilibili.core.ui.resolveRoundedControlVisualGeometry
import com.android.purebilibili.core.ui.resolveMiuixNonGlassControlGeometry
import com.android.purebilibili.core.ui.isMiuixNonGlassEnabled
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.squircle.squircleBorder
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
import com.android.purebilibili.core.ui.components.resolveAppSegmentedLabelFontSize
import com.android.purebilibili.core.ui.components.resolveMiuixNonGlassContentTabItemWidths

import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.lerp

@Composable
internal fun <T> AppMiuixSegmentedControl(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    enabled: Boolean,
    colors: AppSegmentedControlColors,
    preferredCornerRadius: Dp,
    height: Dp? = null,
    modifier: Modifier,
    indicatorPositionProvider: (() -> Float)? = null,
    onSelectionChange: (T) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val longestLabelLength = remember(options) {
        options.maxOfOrNull { it.label.length } ?: 0
    }
    val labelFontSize = resolveAppSegmentedLabelFontSize(
        MaterialTheme.typography.labelLarge.fontSize, options.size, longestLabelLength
    )
    val targetHeight = height ?: 36.dp
    val cornerRadius = 8.dp
    val tabColors = resolveAppMiuixSegmentedColors(colors)
    val nonGlassMiuix = isMiuixNonGlassEnabled()
    val inactiveItemBackground = if (shouldUseOpaqueMiuixTabBackdrop(nonGlassMiuix, LocalImmersiveTopChromeActive.current)) {
        AppSurfaceTokens.groupedListContainer()
    } else tabColors.backgroundColor
    val inactiveContentColor = resolveAppMiuixTabContentColor(
        nonGlassMiuix = nonGlassMiuix,
        inactiveContentColor = tabColors.contentColor,
        readableContentColor = tabColors.contentColor,
    )
    val selectedIndex = resolveAppSegmentedSelectionIndex(options, selectedValue)
    val currentPosition = indicatorPositionProvider?.invoke()
    val outlineColor = MiuixTheme.colorScheme.outline

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!enabled) Modifier.semantics { disabled() } else Modifier),
        horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val fraction = if (currentPosition != null) {
                (currentPosition - index).absoluteValue.coerceIn(0f, 1f)
            } else {
                if (index == selectedIndex) 0f else 1f
            }
            val isSelected = fraction < 0.5f

            val itemBackground = if (currentPosition != null) {
                lerp(
                    tabColors.selectedBackgroundColor,
                    inactiveItemBackground,
                    fraction
                )
            } else {
                when {
                    isSelected -> tabColors.selectedBackgroundColor
                    else -> inactiveItemBackground
                }
            }

            val contentColor = if (currentPosition != null) {
                lerp(
                    tabColors.selectedContentColor,
                    inactiveContentColor,
                    fraction
                )
            } else {
                if (isSelected) {
                    tabColors.selectedContentColor
                } else {
                    inactiveContentColor
                }
            }

            val shadowAlpha = if (!isDark) {
                ((1f - fraction) * 0.08f).coerceAtLeast(0f)
            } else 0f

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = targetHeight.coerceAtLeast(28.dp))
                    .then(
                        if (shadowAlpha > 0.005f) {
                            Modifier.dropShadow(
                                shape = RoundedCornerShape(cornerRadius),
                                shadow = Shadow(radius = 3.dp, color = Color.Black, alpha = shadowAlpha)
                            )
                        } else Modifier
                    )
                    .adaptiveSquircleBackground(
                        color = itemBackground,
                        cornerRadius = cornerRadius,
                    )
                    .squircleBorder(
                        width = { if (isSelected) 0.dp else 1.dp },
                        color = { outlineColor },
                        cornerRadius = cornerRadius,
                    )
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
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
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
    equalizeScrollableItemWidths: Boolean = false,
    centerContent: Boolean = false,
    contentSizedNonGlassItems: Boolean = false,
    contentSizedNonGlassMaxItemWidth: Dp = 320.dp,
    drawNonGlassTrack: Boolean = false,
    onSelectionChange: (T) -> Unit,
) {
    if (contentSizedNonGlassItems && scrollable) {
        AppMiuixNonGlassTabs(
            options = options,
            selectedValue = selectedValue,
            enabled = enabled,
            compact = false,
            scrollable = true,
            minTabWidth = minTabWidth,
            colors = colors,
            height = height,
            modifier = modifier,
            equalizeScrollableItemWidths = equalizeScrollableItemWidths,
            centerContent = centerContent,
            contentSizedItems = true,
            contentSizedMaxItemWidth = contentSizedNonGlassMaxItemWidth,
            drawTrack = drawNonGlassTrack,
            onSelectionChange = onSelectionChange,
        )
        return
    }
    if (isMiuixNonGlassEnabled()) {
        if (options.size <= 2 && !scrollable) {
            AppMiuixSegmentedControl(
                options = options,
                selectedValue = selectedValue,
                enabled = enabled,
                colors = colors,
                preferredCornerRadius = preferredCornerRadius,
                height = height,
                modifier = modifier,
                indicatorPositionProvider = indicatorPositionProvider,
                onSelectionChange = onSelectionChange,
            )
            return
        }
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
            equalizeScrollableItemWidths = equalizeScrollableItemWidths,
            centerContent = centerContent,
            contentSizedItems = contentSizedNonGlassItems,
            contentSizedMaxItemWidth = contentSizedNonGlassMaxItemWidth,
            drawTrack = drawNonGlassTrack,
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

/** Miuix tabs delegate to the upstream TabRow unless caller-requested content sizing is active. */
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
    equalizeScrollableItemWidths: Boolean = false,
    centerContent: Boolean = false,
    contentSizedItems: Boolean = false,
    contentSizedMaxItemWidth: Dp = 320.dp,
    drawTrack: Boolean = true,
    onSelectionChange: (T) -> Unit,
) {
    val labels = options.map { it.label }
    val selectedIndex = resolveAppSegmentedSelectionIndex(options, selectedValue)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val tabColors = resolveAppMiuixSegmentedColors(colors)
    val style = MiuixTheme.textStyles.main.copy(
        fontSize = MiuixTheme.textStyles.body1.fontSize,
        fontWeight = FontWeight.Bold,
    )
    val labelSizes = remember(labels, style, measurer, density) {
        labels.map { measurer.measure(AnnotatedString(it), style, maxLines = 1).size }
    }
    val textHeight = with(density) { (labelSizes.maxOfOrNull { it.height } ?: 0).toDp() }
    val geometry = resolveMiuixNonGlassControlGeometry(compact, textHeight)
    if (contentSizedItems && scrollable) {
        AppMiuixContentSizedNonGlassTabs(
            options = options,
            selectedValue = selectedValue,
            selectedIndex = selectedIndex,
            enabled = enabled,
            itemWidths = resolveMiuixNonGlassContentTabItemWidths(
                labelWidths = labelSizes.map { with(density) { it.width.toDp() } },
                minTabWidth = minTabWidth,
                maxTabWidth = contentSizedMaxItemWidth,
            ),
            colors = colors,
            height = height ?: geometry.height,
            modifier = modifier,
            centerContent = centerContent,
            drawTrack = drawTrack,
            onSelectionChange = onSelectionChange,
        )
        return
    }
    // Share upstream's item geometry with the drawing layer; read offsets only during draw.
    val listState = rememberLazyListState()
    val opaqueItems = shouldUseOpaqueMiuixTabBackdrop(true, LocalImmersiveTopChromeActive.current)
    val itemBackground = AppSurfaceTokens.groupedListContainer()
    // Keep the upstream TabRow defaults for a scrollable rail. The app-level 48dp
    // accessibility minimum is too narrow once upstream's 12dp item padding is
    // applied, which turns otherwise readable Chinese labels into ellipses.
    val tabRowMinWidth = if (scrollable) {
        maxOf(minTabWidth, TabRowDefaults.TabRowMinWidth)
    } else {
        0.dp
    }
    val tabRowMaxWidth = if (scrollable) {
        maxOf(TabRowDefaults.TabRowMaxWidth, tabRowMinWidth)
    } else {
        Dp.Infinity
    }
    TabRow(
        tabs = labels,
        selectedTabIndex = selectedIndex,
        onTabSelected = { index ->
            if (enabled) options.getOrNull(index)?.let { onSelectionChange(it.value) }
        },
        modifier = modifier
            .squircleClip(geometry.cornerRadius)
            .drawBehind {
                if (opaqueItems) clipRect {
                    listState.layoutInfo.visibleItemsInfo.forEach { item ->
                        val left = if (layoutDirection == LayoutDirection.Rtl) {
                            size.width - item.offset - item.size
                        } else item.offset.toFloat()
                        val path = Path().apply {
                            addSquircleRect(item.size.toFloat(), size.height, geometry.cornerRadius.toPx())
                        }
                        translate(left = left) { drawPath(path, itemBackground) }
                    }
                }
            }
            .then(if (!enabled) Modifier.semantics { disabled() } else Modifier),
        colors = TabRowDefaults.tabRowColors(
            backgroundColor = if (drawTrack) tabColors.backgroundColor else Color.Transparent,
            contentColor = tabColors.contentColor,
            selectedBackgroundColor = tabColors.selectedBackgroundColor,
            selectedContentColor = tabColors.selectedContentColor,
        ),
        minWidth = tabRowMinWidth,
        maxWidth = tabRowMaxWidth,
        height = height ?: geometry.height,
        cornerRadius = geometry.cornerRadius,
        itemSpacing = AppSpacingTokens.Small,
        listState = listState,
    )
}

@Composable
private fun <T> AppMiuixContentSizedNonGlassTabs(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    selectedIndex: Int,
    enabled: Boolean,
    itemWidths: List<Dp>,
    colors: AppSegmentedControlColors,
    height: Dp,
    modifier: Modifier,
    centerContent: Boolean,
    drawTrack: Boolean,
    onSelectionChange: (T) -> Unit,
) {
    val tabColors = resolveAppMiuixSegmentedColors(colors)
    val outlineColor = MiuixTheme.colorScheme.outline
    val inactiveItemBackground = if (shouldUseOpaqueMiuixTabBackdrop(true, LocalImmersiveTopChromeActive.current)) {
        AppSurfaceTokens.groupedListContainer()
    } else Color.Transparent
    val listState = rememberLazyListState()
    val initialSelectionPositioned = remember(listState) { object { var value = false } }
    val motionEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    val density = LocalDensity.current
    LaunchedEffect(selectedIndex, itemWidths, motionEnabled, density) {
        // Observe resize/measurement, not scroll offsets, so manual scrolling stays free.
        snapshotFlow { listState.layoutInfo.viewportSize.width to listState.layoutInfo.totalItemsCount }
            .filter { (width, count) -> width > 0 && count > 0 }
            .collectLatest {
                val animateSelection = motionEnabled && initialSelectionPositioned.value
                val target = selectedIndex.coerceIn(0, options.lastIndex)
                val info = listState.layoutInfo
                val viewportWidth = info.viewportEndOffset - info.viewportStartOffset
                val itemWidthPx = with(density) {
                    itemWidths.getOrElse(target) { 48.dp }.roundToPx()
                }
                // Center the selected category so adjacent categories retain usable
                // hit targets even when the selected button was already fully visible.
                // LazyRow clamps the movement at either end of the rail.
                val leadingSpace = ((viewportWidth - itemWidthPx) / 2).coerceAtLeast(0)
                val item = info.visibleItemsInfo.firstOrNull { it.index == target }
                if (item == null) {
                    if (animateSelection) {
                        listState.animateScrollToItem(target, scrollOffset = -leadingSpace)
                    } else {
                        listState.scrollToItem(target, scrollOffset = -leadingSpace)
                    }
                } else {
                    val delta = if (item.size <= viewportWidth) {
                        (item.offset - info.viewportStartOffset - leadingSpace).toFloat()
                    } else {
                        resolveMeasuredTabVisibilityDelta(
                            item.offset, item.offset + item.size,
                            info.viewportStartOffset, info.viewportEndOffset,
                        ).toFloat()
                    }
                    if (delta != 0f) {
                        if (animateSelection) listState.animateScrollBy(delta) else listState.scrollBy(delta)
                    }
                }
                initialSelectionPositioned.value = true
            }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .squircleClip(8.dp)
            .background(if (drawTrack) tabColors.backgroundColor else Color.Transparent)
            .then(if (!enabled) Modifier.semantics { disabled() } else Modifier),
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = options.size) },
            horizontalArrangement = Arrangement.spacedBy(
                AppSpacingTokens.Small,
                if (centerContent) Alignment.CenterHorizontally else Alignment.Start,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(options) { index, option ->
                val selected = option.value == selectedValue
                Box(
                    modifier = Modifier
                        .width(itemWidths.getOrElse(index) { 48.dp })
                        .height(height)
                        .adaptiveSquircleBackground(
                            color = if (selected) tabColors.selectedBackgroundColor else inactiveItemBackground,
                            cornerRadius = 8.dp,
                        )
                        .squircleBorder(
                            width = { if (selected) 0.dp else 1.dp },
                            color = { outlineColor },
                            cornerRadius = 8.dp,
                        )
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.Tab,
                            onClick = { onSelectionChange(option.value) },
                        )
                        .semantics {
                            collectionItemInfo = CollectionItemInfo(
                                rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1,
                            )
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = option.label,
                        modifier = Modifier.wrapContentWidth(unbounded = true),
                        color = if (selected) {
                            tabColors.selectedContentColor
                        } else {
                            tabColors.contentColor
                        },
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = MiuixTheme.textStyles.body1.fontSize,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        }
    }
}
