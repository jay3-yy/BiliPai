package com.android.purebilibili.feature.video.postcard

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppSwitch
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.core.util.rememberStoragePermissionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 原生截图保存后在安全角落浮现 4 秒的轻量化提示胶囊：
 * 既不打断一键直存截图节奏，又能一键进入「台词明信片」或直接分享原图。
 */
@Composable
internal fun PostcardCaptureHintPill(
    draft: VideoPostcardDraft?,
    onOpenPostcard: (VideoPostcardDraft) -> Unit,
    onExtractText: ((VideoPostcardDraft) -> Unit)? = null,
    onAnnotateFrame: ((VideoPostcardDraft) -> Unit)? = null,
    onShareRawImage: (Uri) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberSystemReduceMotion()
    val haptics = rememberAppHapticFeedback(AppHapticScene.RESULTS)

    LaunchedEffect(draft?.imageUri) {
        if (draft != null) {
            delay(4_500L)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = draft != null,
        enter = if (reduceMotion) fadeIn() else fadeIn() + slideInVertically { it / 2 },
        exit = if (reduceMotion) fadeOut() else fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        val active = draft ?: return@AnimatedVisibility
        AppSurface(
            shape = AppShapes.container(ContainerLevel.Card),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AsyncImage(
                    model = active.imageUri,
                    contentDescription = "已保存的截图预览",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 52.dp, height = 34.dp)
                        .clip(AppShapes.container(ContainerLevel.Chip)),
                )
                AppTextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpenPostcard(active)
                    },
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    AppText(" 台词明信片")
                }
                if (onExtractText != null) {
                    AppTextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onExtractText(active)
                        },
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        AppText(" 识字")
                    }
                }
                if (onAnnotateFrame != null) {
                    AppTextButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAnnotateFrame(active)
                        },
                    ) {
                        AppText("批注")
                    }
                }
                AppIconButton(
                    onClick = { onShareRawImage(active.imageUri) },
                    modifier = Modifier.size(34.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享截图",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(17.dp),
                    )
                }
                AppIconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(30.dp),
                ) {
                    AppIcon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭提示",
                        tint = AppContentColors.secondaryText(),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPostcardSheet(
    draft: VideoPostcardDraft,
    onDismiss: () -> Unit,
    onSaveToPrivateNote: ((quote: String, moodNote: String, positionMs: Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHapticFeedback(AppHapticScene.RESULTS)
    var template by remember(draft.imageUri) { mutableStateOf(VideoPostcardTemplate.CINEMA) }
    var quoteText by remember(draft.imageUri) { mutableStateOf(draft.initialQuote) }
    var moodNote by remember(draft.imageUri) { mutableStateOf("") }
    var showMetadata by remember(draft.imageUri) { mutableStateOf(true) }
    var previewBitmap by remember(draft.imageUri) { mutableStateOf<Bitmap?>(null) }
    var rendering by remember(draft.imageUri) { mutableStateOf(true) }
    var busy by remember(draft.imageUri) { mutableStateOf(false) }

    DisposableEffect(draft.imageUri) {
        onDispose {
            previewBitmap?.recycle()
            previewBitmap = null
        }
    }

    LaunchedEffect(draft.imageUri, template, quoteText, moodNote, showMetadata) {
        rendering = true
        delay(160L)
        val rendered = renderVideoPostcardBitmap(
            context = context,
            draft = draft,
            config = VideoPostcardRenderConfig(
                template = template,
                quoteText = quoteText,
                moodNote = moodNote,
                showMetadata = showMetadata,
            ),
        )
        val previous = previewBitmap
        previewBitmap = rendered
        if (previous != null && previous !== rendered) {
            previous.recycle()
        }
        rendering = false
    }

    suspend fun performSaveToGallery() {
        val currentBitmap = previewBitmap ?: return
        if (busy) return
        busy = true
        try {
            val savedUri = saveVideoPostcardToGallery(context, currentBitmap, draft.videoTitle)
            if (savedUri != null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(context, "台词明信片已保存到相册", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "保存失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } finally {
            busy = false
        }
    }

    val storagePermission = rememberStoragePermissionState { granted ->
        if (granted) scope.launch { performSaveToGallery() }
    }

    val maxSheetHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    AppModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        dismissOnBackPress = !busy,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = "台词明信片",
                    style = MaterialTheme.typography.titleMedium,
                    color = AppContentColors.primaryText(),
                )
                AppText(
                    text = formatPostcardTimestamp(draft.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = AppContentColors.secondaryText(),
                )
            }

            // 预览区
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 340.dp)
                    .clip(AppShapes.container(ContainerLevel.Card)),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = previewBitmap
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "台词明信片预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat())
                                    .coerceIn(0.6f, 2.2f)
                            ),
                    )
                }
                if (rendering) {
                    AdaptiveLoadingIndicator(size = 24.dp)
                }
            }

            // 版式切换
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VideoPostcardTemplate.entries.forEach { option ->
                    AppFilterChip(
                        selected = template == option,
                        onClick = { template = option },
                        label = { AppText(option.label) },
                    )
                }
            }

            // 附近神弹幕快捷选取
            if (draft.danmakuCandidates.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AppText(
                        text = "点选附近弹幕作为台词",
                        style = MaterialTheme.typography.labelMedium,
                        color = AppContentColors.secondaryText(),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        draft.danmakuCandidates.forEach { danmaku ->
                            AppFilterChip(
                                selected = quoteText == danmaku,
                                onClick = { quoteText = danmaku },
                                label = {
                                    AppText(
                                        text = danmaku,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }

            AppOutlinedTextField(
                value = quoteText,
                onValueChange = { quoteText = it.take(120) },
                label = { AppText("画面台词 / 弹幕摘录") },
                placeholder = { AppText("写下这一帧打动你的句子…") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            AppOutlinedTextField(
                value = moodNote,
                onValueChange = { moodNote = it.take(100) },
                label = { AppText("此刻心情（可选）") },
                placeholder = { AppText("留一句给未来的自己") },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AppText(
                    text = "显示片名、UP 主与时间码",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppContentColors.primaryText(),
                )
                AppSwitch(
                    checked = showMetadata,
                    onCheckedChange = { showMetadata = it },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onSaveToPrivateNote != null) {
                    AppOutlinedButton(
                        onClick = {
                            onSaveToPrivateNote(quoteText, moodNote, draft.positionMs)
                            onDismiss()
                        },
                        enabled = !busy && (quoteText.isNotBlank() || moodNote.isNotBlank()),
                        modifier = Modifier.weight(1f),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        AppText(" 存笔记")
                    }
                }
                AppOutlinedButton(
                    onClick = {
                        val currentBitmap = previewBitmap ?: return@AppOutlinedButton
                        scope.launch {
                            busy = true
                            try {
                                val shareUri = writeVideoPostcardShareFile(context, currentBitmap)
                                if (shareUri != null) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, shareUri)
                                        clipData = ClipData.newRawUri("台词明信片", shareUri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "分享台词明信片"))
                                } else {
                                    Toast.makeText(context, "生成分享图片失败", Toast.LENGTH_SHORT).show()
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                Toast.makeText(context, "无法打开分享，请稍后重试", Toast.LENGTH_SHORT).show()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = !busy && previewBitmap != null,
                    modifier = Modifier.weight(1f),
                ) {
                    AppText("分享")
                }
                AppButton(
                    onClick = {
                        if (storagePermission.isGranted) {
                            scope.launch { performSaveToGallery() }
                        } else {
                            storagePermission.request()
                        }
                    },
                    enabled = !busy && previewBitmap != null,
                    modifier = Modifier.weight(1f),
                ) {
                    AppText(if (busy) "处理中…" else "保存相册")
                }
            }
        }
    }
}
