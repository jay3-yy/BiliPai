package com.android.purebilibili.feature.video.ui.overlay

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val PILI_HEADER_TRIPLE_DELAY_MILLIS = 200L
internal const val PILI_HEADER_TRIPLE_HOLD_MILLIS = 1200L
internal const val PILI_HEADER_TRIPLE_REVERSE_MILLIS = 400
private val PiliTripleEasing = AppMotionEasing.IosEaseInOut

internal fun resolvePiliHeaderTripleProgress(elapsedNanos: Long): Float =
    PiliTripleEasing.transform(
        (elapsedNanos.coerceAtLeast(0L) / (PILI_HEADER_TRIPLE_HOLD_MILLIS * 1_000_000f)).coerceIn(0f, 1f),
    )

/** Header-only timing follows TripleMixin, not the older detail-page long-press convention. */
@Stable
internal class PiliHeaderTriplePressState(private val scope: CoroutineScope) {
    var progress by mutableFloatStateOf(0f)
        private set
    private var delayed = false
    private var holding = false
    private var job: Job? = null

    fun start(hasTriple: Boolean, onStart: () -> Unit, onTriple: () -> Unit, onAlreadyTripled: () -> Unit) {
        job?.cancel()
        holding = true
        delayed = true
        job = scope.launch {
            delay(PILI_HEADER_TRIPLE_DELAY_MILLIS)
            delayed = false
            onStart()
            if (hasTriple) {
                onAlreadyTripled()
            } else {
                val startNanos = withFrameNanos { it }
                while (progress < 1f) {
                    progress = resolvePiliHeaderTripleProgress(withFrameNanos { it } - startNanos)
                }
                progress = 0f
                onTriple()
            }
        }
    }

    fun release(tapUp: Boolean = false, onLike: (() -> Unit)? = null) {
        if (!holding) return
        holding = false
        val shouldLike = tapUp && delayed
        delayed = false
        job?.cancel()
        job = if (progress > 0f) scope.launch {
            animate(progress, 0f, animationSpec = tween(PILI_HEADER_TRIPLE_REVERSE_MILLIS)) { value, _ ->
                progress = value
            }
        } else null
        if (shouldLike) onLike?.invoke()
    }
}

@Composable
internal fun rememberPiliHeaderTriplePressState(): PiliHeaderTriplePressState {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { PiliHeaderTriplePressState(scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(state, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) state.release()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            state.release()
        }
    }
    return state
}

@Composable
internal fun piliHeaderLikeGesture(
    state: PiliHeaderTriplePressState,
    hasTriple: Boolean,
    onLike: () -> Unit,
    onTriple: () -> Unit,
): Modifier {
    val latestHasTriple by rememberUpdatedState(hasTriple)
    val latestLike by rememberUpdatedState(onLike)
    val latestTriple by rememberUpdatedState(onTriple)
    val context = LocalContext.current
    val haptics = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS)
    return Modifier.pointerInput(state, context, haptics) {
        detectTapGestures(onPress = {
            state.start(
                hasTriple = latestHasTriple,
                onStart = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                onTriple = { latestTriple() },
                onAlreadyTripled = { Toast.makeText(context, "已完成三连", Toast.LENGTH_SHORT).show() },
            )
            try {
                state.release(tapUp = tryAwaitRelease(), onLike = { latestLike() })
            } finally {
                state.release()
            }
        })
    }
}
