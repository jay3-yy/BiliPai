package com.android.purebilibili.core.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.android.purebilibili.core.ui.rememberDecorativeAnimationGate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.util.IdentityHashMap

// Main-thread UI bridge: registration lasts only as long as the rendered scroll container.
// Scroll state remains the identity; no page names or separately stored scroll positions.
private val scrollToTopFeedback = IdentityHashMap<ScrollableState, () -> Unit>()

/** A parent gesture can own content displacement until its native settlement finishes. */
val LocalAppScrollToTopFeedbackAllowed = compositionLocalOf { true }


/** Call after an explicit return to top, only if the content was previously scrolled. */
fun ScrollableState.notifyAppScrollToTop() {
    scrollToTopFeedback[this]?.invoke()
}

/**
 * Finite feedback for programmatic returns. Gesture edge feedback stays with OverscrollEffect.
 * Apply to the scroll content, keeping fixed chrome and scrollbars outside the translation.
 * Glass and native materials share the same displacement without changing their geometry.
 */
@Composable
fun rememberAppScrollToTopFeedbackModifier(
    state: ScrollableState,
    overscrollEffect: OverscrollEffect? = null,
    enabled: Boolean = true,
    reverseLayout: Boolean = false,
): Modifier {
    val gate = rememberDecorativeAnimationGate()
    val density = LocalDensity.current.density
    // Finish once the remaining displacement is visually negligible at any screen density.
    val offset = remember(state, density) { Animatable(0f, visibilityThreshold = 0.1f * density) }
    val requests = remember(state) { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    val latestEffect = rememberUpdatedState(overscrollEffect)
    val latestEnabled = rememberUpdatedState(enabled && LocalAppScrollToTopFeedbackAllowed.current)
    val velocity = 180f * density * if (reverseLayout) -1f else 1f

    DisposableEffect(state, gate, requests, offset) {
        val onReturn: () -> Unit = {
            if (gate.active && latestEnabled.value && !state.isScrollInProgress &&
                latestEffect.value?.isInProgress != true && !offset.isRunning
            ) {
                requests.tryEmit(Unit)
            }
            Unit
        }
        scrollToTopFeedback[state] = onReturn
        onDispose {
            if (scrollToTopFeedback[state] === onReturn) scrollToTopFeedback.remove(state)
        }
    }
    LaunchedEffect(state, gate, requests, offset, velocity) {
        merge(
            requests.map { true },
            snapshotFlow { gate.active && latestEnabled.value && !state.isScrollInProgress }
                .filter { !it },
        ).collectLatest { pulse ->
            // Keep the event collector mounted through scrolling: a completed return can
            // notify before the next frame. Hiding or a new scroll cancels the finite pulse.
            if (!pulse) offset.snapTo(0f)
            if (pulse && gate.active && latestEnabled.value && !state.isScrollInProgress) {
                // Duplicate notifications do not reset an in-flight displacement.
                offset.animateTo(
                    targetValue = 0f,
                    initialVelocity = velocity,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f),
                )
            }
        }
    }
    return gate.modifier.graphicsLayer {
        translationY = if (gate.active && latestEnabled.value && !state.isScrollInProgress) offset.value else 0f
    }
}
