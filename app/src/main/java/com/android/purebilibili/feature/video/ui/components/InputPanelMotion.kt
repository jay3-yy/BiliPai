package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.ui.AppDialogBackState
import com.android.purebilibili.core.ui.AppDialogPredictiveBackHandler
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.rememberAppBottomSheetMotion

internal class InputPanelMotion(
    internal val visibility: Animatable<Float, AnimationVector1D>,
    internal val back: AppDialogBackState,
) {
    var mounted by mutableStateOf(false)
        internal set
    var isOpen by mutableStateOf(false)
        internal set

    internal fun visibleFraction(): Float = visibility.value * (1f - back.progress)
}

@Composable
internal fun rememberInputPanelMotion(
    active: Boolean,
    onClosed: () -> Unit,
): InputPanelMotion {
    val motion = rememberAppBottomSheetMotion()
    val style = LocalAppUiStyle.current
    val panel = remember { InputPanelMotion(Animatable(0f), AppDialogBackState()) }
    val latestOnClosed by rememberUpdatedState(onClosed)
    LaunchedEffect(active, motion, style, panel) {
        if (active) {
            panel.mounted = true
            if (panel.back.progress > 0f) {
                panel.visibility.snapTo(panel.visibleFraction())
                panel.back.reset()
            }
        } else {
            panel.isOpen = false
            if (!panel.mounted) return@LaunchedEffect
        }
        val spec = if (motion.reducedMotion) {
            tween<Float>(if (active) motion.contentEnterFadeDurationMillis else motion.contentExitFadeDurationMillis)
        } else {
            AppMotionTokens.resolveBottomSheetSlideSpec<Float>(style)
        }
        panel.visibility.animateTo(if (active) 1f else 0f, spec)
        panel.isOpen = active
        if (!active) {
            panel.mounted = false
            panel.back.reset()
            latestOnClosed()
        }
    }
    return panel
}

@Composable
internal fun InputPanelBackHandler(
    motion: InputPanelMotion,
    active: Boolean,
    onDismiss: () -> Unit,
) {
    AppDialogPredictiveBackHandler(
        state = motion.back,
        enabled = active,
        animateCompletion = false,
        // 面板持有手势结束时的位置，剩余的关闭进度从这一帧继续。
        retainPreview = { true },
        onBackCompleted = onDismiss,
    )
}

@Composable
internal fun Modifier.inputPanelMotion(panel: InputPanelMotion): Modifier {
    val reduceMotion = rememberAppBottomSheetMotion().reducedMotion
    return graphicsLayer {
        val visible = panel.visibleFraction()
        alpha = visible
        if (!reduceMotion) translationY = size.height * (1f - visible)
    }
}
