package com.android.purebilibili.feature.video.ui.components

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

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
    var closeRequested by remember(visible) { mutableStateOf(false) }
    val inputActive = visible && !closeRequested
    val panelMotion = rememberInputPanelMotion(inputActive) {
        if (closeRequested) onDismiss()
    }
    val requestDismiss: () -> Unit = { if (visible) closeRequested = true }
    if (panelMotion.mounted) {
        Dialog(
            onDismissRequest = requestDismiss,
            properties = DialogProperties(
                dismissOnBackPress = inputActive,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            InputPanelBackHandler(panelMotion, inputActive, requestDismiss)
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
                Box(Modifier.inputPanelMotion(panelMotion), propagateMinConstraints = true) {
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
                        modifier = Modifier.widthIn(max = 450.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}
