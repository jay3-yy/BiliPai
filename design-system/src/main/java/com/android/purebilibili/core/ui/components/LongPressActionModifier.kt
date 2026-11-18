package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

@Composable
internal fun Modifier.onLongPressAction(
    enabled: Boolean,
    onLongPress: () -> Unit,
): Modifier {
    if (!enabled) return this
    val latestLongPress by rememberUpdatedState(appLongClickWithFeedback(
        onLongClick = onLongPress,
        scene = com.android.purebilibili.core.ui.AppHapticScene.TEXT,
    ))
    return pointerInput(Unit) {
        val timeoutMs = viewConfiguration.longPressTimeoutMillis.coerceAtLeast(1L)
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val start = down.position
            val completedBeforeTimeout = withTimeoutOrNull(timeoutMs) {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: return@withTimeoutOrNull true
                    if (start.exceedsSlop(change.position, touchSlop) || !change.pressed) {
                        return@withTimeoutOrNull true
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                true
            }
            if (completedBeforeTimeout == null) {
                latestLongPress()
                waitForUpOrCancellation()
            }
        }
    }
}

private fun Offset.exceedsSlop(current: Offset, touchSlop: Float): Boolean {
    val dx = current.x - x
    val dy = current.y - y
    return hypot(dx.toDouble(), dy.toDouble()) > touchSlop
}
