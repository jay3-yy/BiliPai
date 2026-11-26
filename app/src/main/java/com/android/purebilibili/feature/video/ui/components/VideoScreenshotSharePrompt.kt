package com.android.purebilibili.feature.video.ui.components

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.feature.video.util.captureAndSaveVideoScreenshotUri
import com.android.purebilibili.feature.video.util.buildScreenshotFileName
import com.android.purebilibili.feature.video.util.saveScreenshotToGalleryUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Captures a frame, saves it first, then offers to share that exact saved image or invoke [onScreenshotSaved]. */
@Composable
internal fun rememberVideoScreenshotAction(
    captureBitmap: (suspend (PlayerView, Int, Int) -> Bitmap?)? = null,
    onScreenshotSaved: ((Uri) -> Unit)? = null,
): (PlayerView, Int, Int, String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingShareUri by remember { mutableStateOf<Uri?>(null) }
    var capturing by remember { mutableStateOf(false) }

    pendingShareUri?.let { uri ->
        AppAlertDialog(
            onDismissRequest = { pendingShareUri = null },
            title = { AppText("截图已保存") },
            text = { AppText("已保存到相册（PNG），是否分享这张截图？") },
            confirmButton = {
                AppTextButton(onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            clipData = ClipData.newRawUri("视频截图", uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享视频截图"))
                        pendingShareUri = null
                    } catch (_: Exception) {
                        Toast.makeText(context, "无法打开分享，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }) { AppText("分享") }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingShareUri = null }) { AppText("暂不分享") }
            },
        )
    }

    return { playerView, width, height, title ->
        if (!capturing && pendingShareUri == null) {
            capturing = true
            scope.launch {
                try {
                    val uri = if (captureBitmap == null) {
                        captureAndSaveVideoScreenshotUri(
                            context = context,
                            playerView = playerView,
                            videoWidth = width,
                            videoHeight = height,
                            videoTitle = title,
                        )
                    } else {
                        captureBitmap(playerView, width, height)?.let { bitmap ->
                            try {
                                saveScreenshotToGalleryUri(
                                    context, bitmap,
                                    buildScreenshotFileName(title, System.currentTimeMillis()),
                                )
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    if (uri == null) {
                        Toast.makeText(context, "截图失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    } else if (onScreenshotSaved != null) {
                        onScreenshotSaved(uri)
                    } else {
                        pendingShareUri = uri
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    Toast.makeText(context, "截图失败，请稍后重试", Toast.LENGTH_SHORT).show()
                } finally {
                    capturing = false
                }
            }
        }
    }
}
