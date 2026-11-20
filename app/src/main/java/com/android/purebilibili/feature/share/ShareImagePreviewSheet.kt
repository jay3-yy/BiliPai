package com.android.purebilibili.feature.share

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import com.android.purebilibili.core.ui.LocalPredictiveBackGestureEnabled
import com.android.purebilibili.core.ui.ModalWindowBackHandler
import com.android.purebilibili.core.ui.motion.rememberExpandablePreviewMotion
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.*
import com.android.purebilibili.core.util.rememberStoragePermissionState
import com.android.purebilibili.feature.screenshot.saveAppScreenshotBitmapToGallery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareImagePreviewSheet(document: ShareImageDocument, onDismiss: () -> Unit, modifier: Modifier = Modifier, capturedFiles: List<File> = emptyList(), loadDocument: (suspend () -> ShareImageDocument)? = null) {
    val context = LocalContext.current
    val isDarkTheme = com.android.purebilibili.core.ui.isAppInDarkTheme()
    val scope = rememberCoroutineScope()
    var footer by remember(document.key) { mutableStateOf(true) }
    var files by remember(document.key) { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember(document.key) { mutableStateOf(true) }
    var busy by remember(document.key) { mutableStateOf(false) }
    var error by remember(document.key) { mutableStateOf<String?>(null) }
    var status by remember(document.key) { mutableStateOf<String?>(null) }
    val savedFiles = remember(document.key) { mutableSetOf<String>() }
    var retry by remember(document.key) { mutableIntStateOf(0) }
    suspend fun save() {
        if (busy || loading || files.isEmpty()) return
        busy = true
        error = null
        try {
            files.forEach { file ->
                if (file.absolutePath in savedFiles) return@forEach
                val saved = withContext(Dispatchers.IO) {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: kotlin.error("读取图片失败")
                    try { saveAppScreenshotBitmapToGallery(context, bitmap, "BiliPai_${file.name}") } finally { bitmap.recycle() }
                }
                check(saved) { "已保存 ${savedFiles.size}/${files.size} 张，其余保存失败，请重试" }
                savedFiles += file.absolutePath
            }
            status = "已保存 ${files.size} 张图片到相册"
        } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "保存失败" } finally { busy = false }
    }
    val permission = rememberStoragePermissionState { granted -> if (granted) scope.launch { save() } }
    LaunchedEffect(document, footer, retry, isDarkTheme) {
        loading = true
        files = emptyList()
        error = null
        status = null
        savedFiles.clear()
        try { files = capturedFiles.ifEmpty { renderShareImages(context, loadDocument?.invoke() ?: document, footer, isDarkTheme) } } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "生成图片失败，请重试" } finally { loading = false }
    }
    // 生成结果变更后重建预览会话，旧图片及其几何锚点不跨内容复用。
    val motion = key(document.key, files) { rememberExpandablePreviewMotion() }
    var selectedPath by rememberSaveable(document.key, files) { mutableStateOf<String?>(null) }
    val selected = files.firstOrNull { it.absolutePath == selectedPath }
    var selectedPainter by remember(document.key, files) { mutableStateOf<AsyncImagePainter?>(null) }
    // 点击时沿用已加载的缩略图 Painter，避免过渡首帧重新请求图片。
    val painter = selected?.let { selectedPainter ?: rememberAsyncImagePainter(it) }
    val scroll = rememberScrollState()
    val previewActive = motion.expanded && selected != null
    AppModalBottomSheet(
        modifier = modifier,
        onDismissRequest = { if (!busy) onDismiss() },
        dismissOnBackPress = !previewActive && !busy,
    ) {
        ModalWindowBackHandler(
            enabled = previewActive,
            predictive = motion.predictive && LocalPredictiveBackGestureEnabled.current,
            onStarted = motion.onBackStarted,
            onProgress = motion.onBackProgress,
            onCancelled = motion.onBackCancelled,
            onCompleted = motion.close,
        )
        LocalNavigationBackHandler(
            enabled = previewActive && Build.VERSION.SDK_INT < 33,
            onBackCompleted = motion.close,
        )
        ShareImagePreviewContainer(
            progress = { motion.progress.value },
            expanded = previewActive,
            painter = painter,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp),
            compact = { imageModifier, viewportModifier ->
                Column(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText("${document.title}图片预览")
                    if (loading) AppText("正在加载完整内容并生成图片…")
                    if (files.size > 1) AppText("内容较长，已分为 ${files.size} 张图片，保存和分享均包含全部内容")
                    if (files.isNotEmpty()) AppText("点击图片展开查看")
                    AppLazyColumn(
                        Modifier.fillMaxWidth().weight(1f, fill = false).then(viewportModifier),
                        userScrollEnabled = !previewActive,
                    ) {
                        items(files, key = { it.absolutePath }) { file ->
                            val bounds = remember(file) { shareImageAspectRatio(file) }
                            val thumbnailPainter = rememberAsyncImagePainter(file)
                            val image = Modifier
                                .fillMaxWidth()
                                .aspectRatio(bounds)
                                .clickable(
                                    enabled = !busy && !previewActive,
                                    role = Role.Button,
                                    onClickLabel = "展开图片",
                                ) {
                                    selectedPainter = thumbnailPainter
                                    selectedPath = file.absolutePath
                                    scope.launch { scroll.scrollTo(0) }
                                    motion.open()
                                }
                                .then(if (file == selected) imageModifier else Modifier)
                            if (file == selected && painter != null) {
                                Image(painter, "分享图片 ${files.indexOf(file) + 1}", modifier = image)
                            } else {
                                Image(thumbnailPainter, "分享图片 ${files.indexOf(file) + 1}", modifier = image)
                            }
                        }
                    }
                    if (capturedFiles.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                        AppCheckbox(checked = footer, onCheckedChange = { if (!busy && !loading) footer = it })
                        AppText("显示二维码和署名页脚")
                    }
                    error?.let { AppText(it); AppTextButton(onClick = { retry++ }, enabled = !loading && !busy) { AppText("重新生成") } }
                    status?.let { AppText(it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppTextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.weight(1f)) { AppText("关闭") }
                        AppButton(onClick = {
                            try { shareImageFiles(context, files) } catch (_: Exception) { error = "无法打开图片分享，请重试" }
                        }, enabled = !loading && !busy && files.isNotEmpty(), modifier = Modifier.weight(1f)) { AppText("分享") }
                        AppButton(onClick = { if (permission.isGranted) scope.launch { save() } else permission.request() }, enabled = !loading && !busy && files.isNotEmpty(), modifier = Modifier.weight(1f)) { AppText(if (busy) "保存中…" else "保存") }
                    }
                }
            },
            preview = { imageModifier, viewportModifier ->
                Column(Modifier.fillMaxSize().navigationBarsPadding().padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        AppText("图片 ${files.indexOf(selected) + 1} / ${files.size}", modifier = Modifier.weight(1f))
                        AppTextButton(onClick = motion.close, enabled = previewActive) { AppText("收起") }
                    }
                    Box(
                        Modifier.fillMaxWidth().weight(1f).clipToBounds().then(viewportModifier)
                            .verticalScroll(scroll, enabled = previewActive),
                    ) {
                        if (selected != null && painter != null) {
                            val ratio = remember(selected) { shareImageAspectRatio(selected) }
                            Image(
                                painter,
                                "分享图片 ${files.indexOf(selected) + 1}，可上下滚动查看",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().aspectRatio(ratio).then(imageModifier),
                            )
                        }
                    }
                }
            },
        )
    }
}

private fun shareImageAspectRatio(file: File): Float {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        BitmapFactory.decodeFile(file.absolutePath, this)
    }
    return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
        bounds.outWidth.toFloat() / bounds.outHeight
    } else 1f
}
