package com.android.purebilibili.feature.home.components.cards

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/** 先测量时长并预留空间，统计项在剩余宽度内与时长保持同行。 */
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
        val durationPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val hasDuration = durationPlaceable.width > 0
        val statsWidth = if (!hasDuration) constraints.maxWidth else {
            (constraints.maxWidth - spacing - durationPlaceable.width).coerceAtLeast(0)
        }
        val statsPlaceable = measurables[0].measure(
            constraints.copy(minWidth = 0, minHeight = 0, maxWidth = statsWidth),
        )
        val height = maxOf(statsPlaceable.height, durationPlaceable.height)
        layout(constraints.maxWidth, constraints.constrainHeight(height)) {
            statsPlaceable.placeRelative(0, (height - statsPlaceable.height) / 2)
            durationPlaceable.placeRelative(
                x = if (durationAtEnd) constraints.maxWidth - durationPlaceable.width
                    else statsPlaceable.width + spacing,
                y = (height - durationPlaceable.height) / 2,
            )
        }
    }
}
