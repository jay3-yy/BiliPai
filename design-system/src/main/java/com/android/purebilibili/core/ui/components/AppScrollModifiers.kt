package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Modifier.appVerticalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier {
    val modifier = if (enabled) appScrollBoundaryHaptics(state, reverseScrolling) else this
    return modifier.verticalScroll(state, enabled, flingBehavior, reverseScrolling)
}

@Composable
fun Modifier.appVerticalScroll(
    state: ScrollState,
    overscrollEffect: OverscrollEffect?,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier {
    val modifier = if (enabled) appScrollBoundaryHaptics(state, reverseScrolling) else this
    return modifier.verticalScroll(state, overscrollEffect, enabled, flingBehavior, reverseScrolling)
}

@Composable
fun Modifier.appHorizontalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false,
): Modifier {
    val modifier = if (enabled) {
        appScrollBoundaryHaptics(state, reverseScrolling, Orientation.Horizontal)
    } else this
    return modifier.horizontalScroll(state, enabled, flingBehavior, reverseScrolling)
}
