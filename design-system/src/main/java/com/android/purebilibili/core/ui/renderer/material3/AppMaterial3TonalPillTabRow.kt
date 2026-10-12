package com.android.purebilibili.core.ui.renderer.material3

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppSegmentOption
import com.android.purebilibili.core.ui.components.resolveElasticTabIndicatorBounds

/**
 * PiliPlus 风格的 tonal 胶囊 Tab 行(MD3 非玻璃次级筛选行专用)。
 *
 * 选中项衬一块 secondaryContainer 圆角胶囊(20dp 圆角),文字切换为 onSecondaryContainer,
 * 未选中为 outline;行内没有分隔线与默认水波纹。动画与 AppElasticTabIndicator 共用同一套
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
    scrollState: ScrollState? = null,
    selectedContainerColor: androidx.compose.ui.graphics.Color? = null,
    selectedContentColor: androidx.compose.ui.graphics.Color? = null,
    unselectedContentColor: androidx.compose.ui.graphics.Color? = null,
    indicatorPositionProvider: (() -> Float)? = null,
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
    val textStyle = TextStyle(
        fontSize = resolvedFontSize,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0f.sp,
    )

    // 胶囊沿条目矩形插值滑动;条目矩形要么来自文本测量(hug),要么来自等分栅格(fixed)。
    val itemWidths: List<Dp> = if (scrollable) {
        val textMeasurer = rememberTextMeasurer()
        remember(options, resolvedFontSize, density) {
            options.map { option ->
                val textWidth = with(density) {
                    textMeasurer.measure(
                        text = option.label,
                        style = textStyle,
                        maxLines = 1,
                    ).size.width.toDp()
                }
                textWidth + resolveTonalPillTabItemHorizontalPadding() * 2
            }
        }
    } else {
        emptyList()
    }

    val animatedPosition by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = if (!com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current ||
            indicatorPositionProvider != null) snap() else spring(),
        label = "tonalPillTabPosition",
    )
    val followPosition = indicatorPositionProvider?.invoke()
    val pillPosition = followPosition ?: animatedPosition

    fun pillBounds(lefts: List<Dp>, widths: List<Dp>): Pair<Dp, Dp> {
        val bounds = resolveElasticTabIndicatorBounds(
            position = pillPosition,
            tabLeftsDp = lefts.map { it.value },
            tabWidthsDp = widths.map { it.value },
            contentWidthsDp = widths.map { it.value },
            matchContentSize = false,
        )
        return bounds.leftDp.dp to bounds.widthDp.dp
    }

    val pillColor = selectedContainerColor ?: colorScheme.secondaryContainer
    val pillShape = RoundedCornerShape(resolveTonalPillTabCornerRadius())
    val verticalInset = resolveTonalPillTabVerticalInset()

    if (scrollable) {
        val itemLefts = remember(itemWidths) {
            buildList {
                var cursor = 0f
                itemWidths.forEach { width ->
                    add(cursor)
                    cursor += width.value
                }
            }.map { it.dp }
        }
        val (pillLeft, pillWidth) = pillBounds(itemLefts, itemWidths)
        Box(
            modifier = modifier
                .widthIn(max = LocalConfigurationMaxWidth())
                .height(height)
                .horizontalScroll(rowScrollState),
        ) {
            Box(
                modifier = Modifier
                    .offset(x = pillLeft)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .padding(vertical = verticalInset)
                    .clip(pillShape)
                    .background(pillColor),
            )
            Row(modifier = Modifier.height(height)) {
                options.forEachIndexed { index, option ->
                    TonalPillTabItem(
                        label = option.label,
                        selected = index == selectedIndex,
                        enabled = enabled,
                        labelColor = if (index == selectedIndex) {
                            selectedContentColor ?: colorScheme.onSecondaryContainer
                        } else {
                            unselectedContentColor ?: colorScheme.outline
                        },
                        textStyle = textStyle,
                        onClick = { onSelectionChange(option.value) },
                        modifier = Modifier
                            .width(itemWidths[index])
                            .fillMaxHeight(),
                    )
                }
            }
        }
    } else {
        BoxWithConstraints(modifier = modifier.height(height)) {
            val cellWidth = maxWidth / options.size
            val itemLefts = options.indices.map { (cellWidth * it) }
            val itemWidthList = options.indices.map { cellWidth }
            val (pillLeft, pillWidth) = pillBounds(itemLefts, itemWidthList)
            Box(
                modifier = Modifier
                    .offset(x = pillLeft)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .padding(vertical = verticalInset)
                    .clip(pillShape)
                    .background(pillColor),
            )
            Row(modifier = Modifier.fillMaxWidth().height(height)) {
                options.forEachIndexed { index, option ->
                    TonalPillTabItem(
                        label = option.label,
                        selected = index == selectedIndex,
                        enabled = enabled,
                        labelColor = if (index == selectedIndex) {
                            selectedContentColor ?: colorScheme.onSecondaryContainer
                        } else {
                            unselectedContentColor ?: colorScheme.outline
                        },
                        textStyle = textStyle,
                        onClick = { onSelectionChange(option.value) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        fillCell = true,
                    )
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
    fillCell: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentPadding = if (fillCell) {
        Modifier.padding(horizontal = resolveTonalPillTabItemHorizontalPadding())
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(resolveTonalPillTabCornerRadius()))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClickLabel = label,
            ) { onClick() }
            .semantics {
                role = Role.Tab
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = labelColor,
            maxLines = 1,
            style = textStyle,
            modifier = Modifier.then(contentPadding),
        )
    }
}
