package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import com.android.purebilibili.core.ui.AppPredictiveDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DanmakuSettingsPanelHost(
    isFullscreen: Boolean,
    layoutPolicy: DanmakuSettingsPanelLayoutPolicy,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    source: com.android.purebilibili.core.ui.components.AppGlassMenuSource? =
        com.android.purebilibili.core.ui.components.LocalAppGlassMenuSource.current,
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
                PlayerNativeMenuTheme { content(onDismiss) }
            }
        }) return
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)
    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            visible = false
            scope.launch { delay(350); latestDismiss() }
        }
    }
    LaunchedEffect(Unit) { visible = true }
    PlayerNativeMenuTheme {
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
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
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.32f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = dismiss,
                        ))
                    AnimatedVisibility(
                        visible = visible,
                        enter = slideIn(tween(350)) { size ->
                            if (layoutPolicy.anchor == DanmakuSettingsPanelAnchor.End) IntOffset(size.width, 0)
                            else IntOffset(0, size.height)
                        },
                        exit = slideOut(tween(350)) { size ->
                            if (layoutPolicy.anchor == DanmakuSettingsPanelAnchor.End) IntOffset(size.width, 0)
                            else IntOffset(0, size.height)
                        },
                    ) { content(dismiss) }
                }
            }
        }
    }
}
