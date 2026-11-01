package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints

/**
 * Keep the natural tallest-card height for every material. Slot modifiers go on the real
 * card root, avoiding an extra Box (and its intrinsic traversal) around every card.
 *
 * 按列宽查询自然高度后，每张卡只实际测量一次；普通 Layout 不允许同一遍测量中
 * 用不同约束重复测量同一个子项。保留最长内容决定行高的语义，不固定文字区高度，
 * 标题、宽度或字号变化后行高可以缩回。玻璃、MD3 与 Miuix 共用这条测量路径。
 */
@Composable
internal fun HomeAlignedVideoRow(
    indices: IntRange,
    itemKeys: List<String>,
    columns: Int,
    horizontalArrangement: Arrangement.Horizontal,
    modifier: Modifier = Modifier,
    content: @Composable (Int, Modifier) -> Unit,
) {
    Layout(
        content = {
            indices.forEach { index ->
                key(itemKeys[index]) {
                    content(index, Modifier)
                }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val slotCount = columns.coerceAtLeast(measurables.size)
        val gapPx = horizontalArrangement.spacing.roundToPx()
        val gapTotal = gapPx * (slotCount - 1).coerceAtLeast(0)
        val availableWidth = (constraints.maxWidth - gapTotal).coerceAtLeast(0)
        val columnWidth = availableWidth / slotCount
        val remainder = availableWidth % slotCount
        // Distribute rounding pixels instead of rounding every column up past the row bounds.
        val slotWidths = IntArray(measurables.size) { columnWidth + if (it < remainder) 1 else 0 }
        val naturalMax = measurables.indices.maxOf { measurables[it].minIntrinsicHeight(slotWidths[it]) }
        val rowHeight = naturalMax.coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeables = measurables.mapIndexed { index, measurable ->
            measurable.measure(Constraints.fixed(slotWidths[index], rowHeight))
        }
        layout(constraints.maxWidth, rowHeight) {
            var x = 0
            placeables.forEach { placeable ->
                placeable.placeRelative(x, 0)
                x += placeable.width + gapPx
            }
        }
    }
}
