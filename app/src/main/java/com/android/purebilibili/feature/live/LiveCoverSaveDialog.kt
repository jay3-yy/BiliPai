package com.android.purebilibili.feature.live

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.download.DownloadManager
import kotlinx.coroutines.launch

@Composable
internal fun LiveCoverSaveDialog(card: LiveRoomCardUiModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("保存直播封面") },
        text = { AppText(card.title) },
        confirmButton = {
            AppTextButton(onClick = {
                scope.launch {
                    val saved = DownloadManager.saveImageToGallery(context, card.coverUrl, card.title)
                    Toast.makeText(context, if (saved) "封面已保存到相册" else "封面保存失败，请重试", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }) { AppText("保存") }
        },
        dismissButton = { AppTextButton(onClick = onDismiss) { AppText("取消") } },
    )
}
