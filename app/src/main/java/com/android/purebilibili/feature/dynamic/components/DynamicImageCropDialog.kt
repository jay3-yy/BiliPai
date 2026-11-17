package com.android.purebilibili.feature.dynamic.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppPredictiveDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.toBitmap
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.feature.screenshot.AppScreenshotCropRect
import com.android.purebilibili.feature.screenshot.AppScreenshotRegionOverlay
import com.android.purebilibili.feature.screenshot.cropAppScreenshotBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
internal fun DynamicImageCropDialog(source: String, onDismiss: () -> Unit, onCropped: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    var selected by remember(source) { mutableStateOf<AppScreenshotCropRect?>(null) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(source) {
        try {
            val result = SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context).data(source).size(4096, 4096).allowHardware(false)
                    .apply { if (source.startsWith("https://") || source.startsWith("http://")) {
                        httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com/").build())
                    } }.build()
            )
            bitmap = (result as? SuccessResult)?.image?.toBitmap()
            if (bitmap == null) error = "图片加载失败，请重试"
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            error = "图片加载失败，请重试"
        }
    }
    if (error != null) {
        AppAlertDialog(
            onDismissRequest = onDismiss,
            title = { AppText("裁剪图片") },
            text = { AppText(error.orEmpty()) },
            confirmButton = { AppDialogAction(onClick = onDismiss) { AppText("确定") } },
        )
    } else {
        Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
                AppText("拖拽选择区域，移动边缘或裁剪框调整", color = Color.White, modifier = Modifier.padding(16.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val image = bitmap
                    if (image == null) {
                        AppText("正在加载图片…", color = Color.White)
                    } else {
                        val ratio = image.width.toFloat() / image.height.coerceAtLeast(1)
                        val width = minOf(maxWidth, maxHeight * ratio)
                        val height = width / ratio
                        Box(Modifier.width(width).height(height)) {
                            AppScreenshotRegionOverlay(
                                bitmap = image,
                                saving = saving,
                                showControls = false,
                                adjustableSelection = true,
                                onSelectionChanged = { selected = it },
                                onCancel = onDismiss,
                                onSaveRegion = {},
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppOutlinedButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.weight(1f)) { AppText("取消") }
                    AppButton(onClick = {
                        val image = bitmap ?: return@AppButton
                        val rect = selected ?: return@AppButton
                        saving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    val cropped = cropAppScreenshotBitmap(image, rect) ?: error("无法裁剪图片")
                                    try {
                                        val directory = File(context.cacheDir, "dynamic_crops")
                                        check(directory.isDirectory || directory.mkdirs()) { "无法保存裁剪图片" }
                                        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 7L * 24 * 60 * 60 * 1000 }?.forEach { it.delete() }
                                        val file = File(directory, "${UUID.randomUUID()}.png")
                                        try {
                                            file.outputStream().use { check(cropped.compress(Bitmap.CompressFormat.PNG, 100, it)) { "无法保存裁剪图片" } }
                                            check(file.length() <= 15L * 1024 * 1024) { "裁剪后的图片超过 15 MB，请选择更小区域" }
                                        } catch (e: Exception) {
                                            file.delete()
                                            throw e
                                        }
                                        Uri.fromFile(file).toString()
                                    } finally {
                                        if (cropped !== image) cropped.recycle()
                                    }
                                }
                            }
                            saving = false
                            result.fold(onSuccess = onCropped, onFailure = { error = it.message ?: "裁剪失败" })
                        }
                    }, enabled = selected != null && !saving, modifier = Modifier.weight(1f)) {
                        AppText(if (saving) "保存中…" else "完成")
                    }
                }
            }
        }
    }
}
