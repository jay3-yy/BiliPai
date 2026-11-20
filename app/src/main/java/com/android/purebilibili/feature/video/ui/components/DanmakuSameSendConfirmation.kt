package com.android.purebilibili.feature.video.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton

@Composable
internal fun DanmakuSameSendConfirmation(
    text: String,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    errorMessage: String? = null,
) {
    AppAlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        title = { AppText("发送同款弹幕？") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText("将在当前播放位置发送：\n$text")
                errorMessage?.let { AppText(it, color = AppContentColors.error()) }
            }
        },
        confirmButton = {
            AppTextButton(onClick = onConfirm, enabled = !isSending && text.isNotBlank()) {
                AppText(if (isSending) "发送中…" else "发送")
            }
        },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !isSending) { AppText("取消") } },
    )
}
