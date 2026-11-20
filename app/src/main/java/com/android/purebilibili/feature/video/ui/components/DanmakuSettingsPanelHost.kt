package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.theme.AppUiStyle
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.ui.AppDialogWindowExitEffect
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.ModalWindowBackHandler
import com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect
import com.android.purebilibili.core.ui.motion.AppMotionTokens
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun DanmakuSettingsPanelHost(
    isFullscreen: Boolean,
    layoutPolicy: DanmakuSettingsPanelLayoutPolicy,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    source: com.android.purebilibili.core.ui.components.AppGlassMenuSource? =
        com.android.purebilibili.core.ui.components.LocalAppGlassMenuSource.current,
    uiStyle: AppUiStyle = AppUiStyle.MATERIAL3,
    content: @Composable (requestDismiss: () -> Unit) -> Unit,
) {
    // 玻璃只替换容器与开合动画，设置内容、回调和原生布局策略共用。
    if (com.android.purebilibili.core.ui.components.AppGlassMenu(
            expanded = true,
            onDismissRequest = onDismiss,
            title = "弹幕设置",
            source = source,
            placement = if (source?.isAvailable == true) {
                com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.ANCHOR_END
            } else if (isFullscreen) com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.END
            else com.android.purebilibili.core.ui.components.AppGlassMenuPlacement.CENTER,
            minWidth = layoutPolicy.minWidthDp.dp,
            maxWidth = layoutPolicy.maxWidthDp.dp,
            maxHeight = layoutPolicy.maxHeightDp.dp,
            modifier = modifier,
            interactiveContent = true,
        ) {
            CompositionLocalProvider(LocalPlayerGlassSettingsSurface provides true) {
                PlayerNativeMenuTheme(uiStyle) { content(onDismiss) }
            }
        }) return
    val offset = remember { mutableFloatStateOf(1f) }
    val reduceMotion = rememberSystemReduceMotion() || !LocalComponentMotionEnabled.current
    val motion = if (reduceMotion) snap<Float>() else AppMotionTokens.resolveBottomSheetSlideSpec<Float>(uiStyle)
    var gestureStart by remember { mutableFloatStateOf(0f) }
    var closing by remember { mutableStateOf(false) }
    var settlement by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)
    fun settle(target: Float) {
        settlement?.cancel()
        settlement = scope.launch {
            animate(offset.floatValue, target, animationSpec = motion) { value, _ ->
                offset.floatValue = value.coerceIn(0f, 1f)
            }
            if (target == 1f) latestDismiss()
        }
    }
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            settle(1f)
        }
    }
    LaunchedEffect(Unit) { settle(0f) }
    PlayerNativeMenuTheme(uiStyle) {
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            AppDialogWindowExitEffect(offset)
            // 预测返回预览、撤销和松手共用面板位移，松手从当前画面继续滑出。
            ModalWindowBackHandler(
                enabled = !closing,
                predictive = !reduceMotion,
                onStarted = {
                    settlement?.cancel()
                    gestureStart = offset.floatValue
                },
                onProgress = { progress ->
                    offset.floatValue = gestureStart + (1f - gestureStart) * progress * 0.25f
                },
                onCancelled = { if (!closing) settle(0f) },
                onCompleted = dismiss,
            )
            ModalWindowBlurBehindEffect(enabled = false)
            com.android.purebilibili.core.ui.AppPopupWindowHost(enableWindowMaterial = false) {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = when (layoutPolicy.anchor) {
                        DanmakuSettingsPanelAnchor.Bottom -> Alignment.BottomCenter
                        DanmakuSettingsPanelAnchor.End -> Alignment.CenterEnd
                        DanmakuSettingsPanelAnchor.Center -> Alignment.Center
                    },
                ) {
                    Box(
                        Modifier.matchParentSize()
                            .graphicsLayer { alpha = 1f - offset.floatValue }
                            .background(Color.Black.copy(alpha = 0.32f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = dismiss,
                            ),
                    )
                    Box(Modifier.graphicsLayer {
                        val fraction = offset.floatValue
                        if (layoutPolicy.anchor == DanmakuSettingsPanelAnchor.End) {
                            translationX = size.width * fraction
                        } else {
                            translationY = size.height * fraction
                        }
                    }) { content(dismiss) }
                }
            }
        }
    }
}
