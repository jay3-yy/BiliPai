package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import kotlin.math.hypot
import kotlin.math.max

/** An event bridge, not render state: registering the drawer action must not recompose the host. */
private class DanmakuPanelDismissAction {
    var request: (() -> Unit)? = null
}

internal fun useDanmakuControlCenterMotion(
    isFullscreen: Boolean,
    anchor: DanmakuSettingsPanelAnchor,
    liquidGlassEnabled: Boolean,
    componentMotionEnabled: Boolean,
    reduceMotion: Boolean,
): Boolean = isFullscreen && anchor == DanmakuSettingsPanelAnchor.End &&
    liquidGlassEnabled && componentMotionEnabled && !reduceMotion

@Composable
internal fun DanmakuSettingsPanelHost(
    isFullscreen: Boolean,
    layoutPolicy: DanmakuSettingsPanelLayoutPolicy,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (requestDismiss: () -> Unit) -> Unit,
) {
    val reduceMotion = rememberSystemReduceMotion()
    // Only the fullscreen glass end panel gets a moving host. All other presentations
    // keep the original dialog anchor, dimensions, scrim and immediate dismissal.
    val motionEnabled = useDanmakuControlCenterMotion(
        isFullscreen = isFullscreen,
        anchor = layoutPolicy.anchor,
        liquidGlassEnabled = LocalAppThemeConfig.current.liquidGlassEnabled,
        componentMotionEnabled = LocalComponentMotionEnabled.current,
        reduceMotion = reduceMotion,
    )
    val dismissAction = remember { DanmakuPanelDismissAction() }
    val latestDismiss = rememberUpdatedState(onDismiss)
    val requestDismiss: () -> Unit = {
        if (motionEnabled) {
            dismissAction.request?.invoke() ?: latestDismiss.value()
        } else latestDismiss.value()
    }
    val latestRequestDismiss = rememberUpdatedState(requestDismiss)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    Dialog(
        onDismissRequest = requestDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        ModalWindowBlurBehindEffect(enabled = false)
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = when (layoutPolicy.anchor) {
                DanmakuSettingsPanelAnchor.Bottom -> Alignment.BottomCenter
                DanmakuSettingsPanelAnchor.End -> Alignment.CenterEnd
                DanmakuSettingsPanelAnchor.Center -> Alignment.Center
            },
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = if (isFullscreen) 0.08f else 0.22f))
                    .pointerInput(touchSlop) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var maxDragDistancePx = 0f
                            var active = true
                            while (active) {
                                val event = awaitPointerEvent(PointerEventPass.Final)
                                event.changes.forEach { change ->
                                    val distance = hypot(
                                        (change.position.x - down.position.x).toDouble(),
                                        (change.position.y - down.position.y).toDouble(),
                                    ).toFloat()
                                    maxDragDistancePx = max(maxDragDistancePx, distance)
                                    if (change.pressed || change.previousPressed) change.consume()
                                }
                                active = event.changes.any { it.pressed }
                            }
                            if (shouldDismissDanmakuSettingsPanelFromBackdropGesture(maxDragDistancePx, touchSlop)) {
                                latestRequestDismiss.value()
                            }
                        }
                    },
            )
            if (motionEnabled) {
                LandscapeSidePanel(
                    visible = true,
                    edge = LandscapeSidePanelEdge.End,
                    width = layoutPolicy.maxWidthDp.dp,
                    onDismiss = onDismiss,
                    enableControlCenterMotion = true,
                ) { closeDrawer ->
                    SideEffect { dismissAction.request = closeDrawer }
                    DisposableEffect(dismissAction) {
                        onDispose { dismissAction.request = null }
                    }
                    content(closeDrawer)
                }
            } else {
                content(onDismiss)
            }
        }
    }
}
