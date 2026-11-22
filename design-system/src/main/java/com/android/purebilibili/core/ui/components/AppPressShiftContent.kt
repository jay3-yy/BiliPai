package com.android.purebilibili.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * Press-driven horizontal shift with a physical spring bounce.
 *
 * Idle state centers [content] inside the available space; while [pressed] is true the
 * whole content travels to the leading edge and settles with a slight overshoot, like a
 * mass attached to a spring. Releasing springs it back to center.
 *
 * Apply globally to any search-entry pill: drive [pressed] from the same
 * [MutableInteractionSource] attached to the container's `clickable`.
 */
@Composable
fun AppPressShiftContent(
    pressed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val shiftProgress = animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.62f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pressShiftProgress",
    )
    Box(
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(minWidth = 0))
            val width = maxOf(constraints.minWidth, placeable.width)
            val height = maxOf(constraints.minHeight, placeable.height)
            val maxShiftPx = ((width - placeable.width) / 2f).coerceAtLeast(0f)
            layout(width, height) {
                val centerX = ((width - placeable.width) / 2f).coerceAtLeast(0f)
                val centerY = ((height - placeable.height) / 2f).coerceAtLeast(0f)
                val offsetX = (centerX - maxShiftPx * shiftProgress.value).roundToInt()
                placeable.placeRelative(offsetX, centerY.roundToInt())
            }
        },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Convenience: pressed state derived from the container's interaction source. */
@Composable
fun rememberPressShiftPressedState(interactionSource: MutableInteractionSource): Boolean {
    return interactionSource.collectIsPressedAsState().value
}
