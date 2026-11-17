package com.android.purebilibili.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** 自定义对话框共用返回预览；表面、尺寸与窗口属性继续由调用方决定。 */
@Composable
fun AppPredictiveDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val back = remember { AppDialogBackState() }
        AppDialogPredictiveBackHandler(
            state = back,
            enabled = properties.dismissOnBackPress,
            animateCompletion = LocalPredictiveBackGestureEnabled.current,
            onBackCompleted = onDismissRequest,
        )
        Box(
            modifier = Modifier.appDialogBackMotion(state = back, progress = { back.progress }),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}
