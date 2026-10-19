package com.android.purebilibili.feature.video.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.android.purebilibili.core.ui.rememberAppBottomSheetMotion

@Composable
internal fun PlainLandscapeDanmakuComposer(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSend: (message: String, color: Int, mode: Int, fontSize: Int, attentionCommand: Boolean) -> Unit,
    isSending: Boolean,
    initialColor: Int,
    initialMode: Int,
    initialFontSize: Int,
    initialText: String,
    initialAttentionCommand: Boolean,
    onDraftChange: (String, Boolean) -> Unit,
    onSelectionChange: (color: Int, mode: Int, fontSize: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetMotion = rememberAppBottomSheetMotion()
    val visibility = remember { MutableTransitionState(false) }
    var closeRequested by remember { mutableStateOf(false) }
    val latestDismiss by rememberUpdatedState(onDismiss)
    val inputActive = visible && !closeRequested
    LaunchedEffect(visible) {
        closeRequested = false
        visibility.targetState = visible
    }
    LaunchedEffect(inputActive) { visibility.targetState = inputActive }
    LaunchedEffect(visibility.isIdle, visibility.currentState, closeRequested) {
        if (visibility.isIdle && !visibility.currentState && closeRequested) latestDismiss()
    }
    val requestDismiss: () -> Unit = {
        closeRequested = true
        visibility.targetState = false
    }
    if (visibility.currentState || visibility.targetState) {
        Dialog(
            onDismissRequest = requestDismiss,
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            val view = LocalView.current
            SideEffect {
                ((view.parent as? DialogWindowProvider) ?: (view as? DialogWindowProvider))
                    ?.window?.setWindowAnimations(0)
            }
            Box(
                modifier = modifier.fillMaxSize().imePadding()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = requestDismiss,
                    )
                )
                AnimatedVisibility(
                    visibleState = visibility,
                    enter = sheetMotion.contentEnter,
                    exit = sheetMotion.contentExit,
                    label = "plainLandscapeDanmakuInput",
                ) {
                    LandscapeDanmakuComposerEditor(
                        visible = inputActive,
                        onSend = { message, color, mode, size, attention ->
                            if (inputActive) onSend(message, color, mode, size, attention)
                        },
                        isSending = isSending,
                        initialColor = initialColor,
                        initialMode = initialMode,
                        initialFontSize = initialFontSize,
                        initialText = initialText,
                        initialAttentionCommand = initialAttentionCommand,
                        onDraftChange = onDraftChange,
                        onSelectionChange = onSelectionChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
