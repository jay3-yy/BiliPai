package com.android.purebilibili.feature.home.components.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/** 按当前字体和卡片约束测量；同行放不下时，时长移到封面内下一行右侧。 */
@Composable
internal fun VideoCardCoverMetadataLayout(
    durationAtEnd: Boolean,
    modifier: Modifier = Modifier,
    stats: @Composable () -> Unit,
    duration: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { stats() }
            Box { duration() }
        },
    ) { measurables, constraints ->
        val spacing = 6.dp.roundToPx()
        val rowSpacing = 2.dp.roundToPx()
        val naturalStatsWidth = measurables[0].maxIntrinsicWidth(Constraints.Infinity)
        val durationPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val hasDuration = durationPlaceable.width > 0
        val stacked = hasDuration &&
            naturalStatsWidth + spacing + durationPlaceable.width > constraints.maxWidth
        val statsWidth = if (stacked || !hasDuration) constraints.maxWidth else {
            (constraints.maxWidth - spacing - durationPlaceable.width).coerceAtLeast(0)
        }
        val statsPlaceable = measurables[0].measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = statsWidth),
        )
        val height = if (stacked) {
            statsPlaceable.height + rowSpacing + durationPlaceable.height
        } else maxOf(statsPlaceable.height, durationPlaceable.height)
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            statsPlaceable.placeRelative(0, if (stacked) 0 else (height - statsPlaceable.height) / 2)
            durationPlaceable.placeRelative(
                x = if (stacked || durationAtEnd) constraints.maxWidth - durationPlaceable.width
                    else statsPlaceable.width + spacing,
                y = if (stacked) statsPlaceable.height + rowSpacing
                    else (height - durationPlaceable.height) / 2,
            )
        }
    }
}
