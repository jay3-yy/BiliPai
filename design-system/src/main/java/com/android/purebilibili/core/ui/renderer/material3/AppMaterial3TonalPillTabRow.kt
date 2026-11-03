package com.android.purebilibili.core.ui.renderer.material3

import com.android.purebilibili.core.ui.components.ElasticTabIndicatorBounds
import com.android.purebilibili.core.ui.components.tabIndicatorBounds
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.AppExactTouchTargets
import com.android.purebilibili.core.ui.components.resolveAppTabSlotGeometry
import com.android.purebilibili.core.ui.components.resolveElasticTabIndicatorBounds

/**
 * PiliPlus 风格的 tonal 胶囊 Tab 行(MD3 非玻璃次级筛选行专用)。
 *
 * 选中项衬一块 secondaryContainer 圆角胶囊(20dp 圆角),文字切换为 onSecondaryContainer,
 * 未选中使用可读的次要文字色;行内没有分隔线与默认水波纹。动画与 AppElasticTabIndicator 共用同一套
 * PiliPlus 缓动插值:[indicatorPositionProvider](Pager 连续驱动)优先,否则按选中索引做
 * 位置动画。
 */
@Composable
fun <T> AppTonalPillTabRow(
    options: List<AppSegmentOption<T>>,
    selectedValue: T,
    onSelectionChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scrollable: Boolean = false,
    height: Dp = resolveTonalPillTabDefaultHeight(),
    labelFontSize: TextUnit = TextUnit.Unspecified,
    fixedItemWidth: Dp? = null,
    containerHorizontalPadding: Dp = 0.dp,
    scrollState: ScrollState? = null,
    selectedContainerColor: androidx.compose.ui.graphics.Color? = null,
    selectedContentColor: androidx.compose.ui.graphics.Color? = null,
    unselectedContentColor: androidx.compose.ui.graphics.Color? = null,
    indicatorPositionProvider: (() -> Float)? = null,
    centerSelectedTab: Boolean = false,
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOfFirst { it.value == selectedValue }.coerceAtLeast(0)
    val colorScheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val resolvedFontSize = if (labelFontSize.isSpecified) {
        labelFontSize
    } else {
        resolveTonalPillTabDefaultLabelFontSize()
    }
    val rowScrollState = scrollState ?: rememberScrollState()
    // Resolve inherited typography before measuring, just as Text does when rendering.
    val textStyle = LocalTextStyle.current.merge(
        TextStyle(
            fontSize = resolvedFontSize,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0f.sp,
        )
    )

    val measuredItemWidths: List<Dp> = if (scrollable && fixedItemWidth == null) {
        val textMeasurer = rememberTextMeasurer()
        remember(options, textStyle, density, textMeasurer) {
            options.map { option ->
                with(density) {
                    val labelWidthPx = textMeasurer.measure(
                        option.label, style = textStyle, maxLines = 1, softWrap = false,
                    ).size.width
                    // Padding rounds each edge separately. Adding dp first can leave the
                    // label one pixel short and ellipsize even a two-character title.
                    val paddingPx = resolveTonalPillTabItemHorizontalPadding().roundToPx()
                    (labelWidthPx + paddingPx * 2).toDp()
                }
            }
        }
    } else emptyList()

    val animatedPosition = animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (!com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current ||
            indicatorPositionProvider != null) snap() else spring(),
        label = "tonalPillTabPosition",
    )

    fun pillBounds(lefts: List<Dp>, widths: List<Dp>): ElasticTabIndicatorBounds {
        val bounds = resolveElasticTabIndicatorBounds(
            position = indicatorPositionProvider?.invoke() ?: animatedPosition.value,
            tabLeftsDp = lefts.map { it.value },
            tabWidthsDp = widths.map { it.value },
            contentWidthsDp = widths.map { it.value },
            matchContentSize = false,
        )
        return bounds
    }

    val pillColor = selectedContainerColor ?: colorScheme.secondaryContainer
    val pillShape = RoundedCornerShape(resolveTonalPillTabCornerRadius())
    val motionEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current
    AppExactTouchTargets {
        BoxWithConstraints(
            modifier = modifier
                .then(if (fixedItemWidth != null) {
                    Modifier.width(fixedItemWidth * options.size + containerHorizontalPadding * 2)
                } else if (scrollable) Modifier.widthIn(max = LocalConfigurationMaxWidth()) else Modifier.fillMaxWidth())
                .height(height),
        ) {
            val geometry = fixedItemWidth?.let {
                resolveAppTabSlotGeometry(options.size, it, containerHorizontalPadding, maxWidth, scrollable)
            }
            val padding = geometry?.horizontalPadding ?: minOf(containerHorizontalPadding, maxWidth / 2)
            val widths = if (geometry != null) List(options.size) { geometry.itemWidth } else if (scrollable) {
                measuredItemWidths
            } else List(options.size) { (maxWidth - padding * 2) / options.size }
            val lefts = remember(widths, padding) {
                var left = padding
                widths.map { width -> left.also { left += width } }
            }
            val contentWidth = widths.fold(padding * 2) { total, width -> total + width }
            val viewportWidth = minOf(contentWidth, maxWidth)
            LaunchedEffect(selectedIndex, widths, viewportWidth, rowScrollState.maxValue, motionEnabled, density, scrollable, centerSelectedTab) {
                if (scrollable) {
                    val left = with(density) { lefts[selectedIndex].roundToPx() }
                    val right = left + with(density) { widths[selectedIndex].roundToPx() }
                    val viewport = with(density) { viewportWidth.roundToPx() }
                    val target = when {
                        centerSelectedTab -> left + (right - left) / 2 - viewport / 2
                        left < rowScrollState.value -> left
                        right > rowScrollState.value + viewport -> right - viewport
                        else -> rowScrollState.value
                    }.coerceIn(0, rowScrollState.maxValue)
                    if (motionEnabled) {
                        rowScrollState.animateScrollTo(target)
                    } else rowScrollState.scrollTo(target)
                }
            }
            Box(
                modifier = Modifier.width(viewportWidth).height(height)
                    .horizontalScroll(rowScrollState, enabled = scrollable)
                    .selectableGroup(),
            ) {
                Box(
                    modifier = Modifier.tabIndicatorBounds { pillBounds(lefts, widths) }.fillMaxHeight()
                        .clip(pillShape).background(pillColor),
                )
                Row(modifier = Modifier.height(height).padding(horizontal = padding)) {
                    options.forEachIndexed { index, option ->
                        TonalPillTabItem(
                            label = option.label,
                            selected = index == selectedIndex,
                            enabled = enabled,
                            labelColor = if (index == selectedIndex) {
                                selectedContentColor ?: colorScheme.onSecondaryContainer
                            } else unselectedContentColor ?: com.android.purebilibili.core.ui.AppContentColors.secondaryText(),
                            textStyle = textStyle,
                            onClick = { onSelectionChange(option.value) },
                            modifier = Modifier.width(widths[index]).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

/** LocalConfiguration 屏宽上限,滚动行不允许超出视口。 */
@Composable
private fun LocalConfigurationMaxWidth(): Dp =
    androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp

@Composable
private fun TonalPillTabItem(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    labelColor: androidx.compose.ui.graphics.Color,
    textStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val labelPadding = Modifier.padding(horizontal = resolveTonalPillTabItemHorizontalPadding())
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(resolveTonalPillTabCornerRadius()))
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            style = textStyle,
            modifier = labelPadding,
        )
    }
}
