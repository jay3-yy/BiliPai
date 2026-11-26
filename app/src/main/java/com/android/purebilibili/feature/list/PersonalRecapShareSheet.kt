package com.android.purebilibili.feature.list

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppFilterChip
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appVerticalScroll
import com.android.purebilibili.core.ui.isAppInDarkTheme
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.core.util.rememberStoragePermissionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PersonalRecapShareSheet(
    payload: PersonalRecapCardPayload,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHapticFeedback(AppHapticScene.RESULTS)
    val initialTheme = if (isAppInDarkTheme()) PersonalRecapCardTheme.NIGHT_GLOW else PersonalRecapCardTheme.WARM_PAPER
    var selectedTheme by remember(payload.windowLabel) { mutableStateOf(initialTheme) }
    var previewBitmap by remember(payload) { mutableStateOf<Bitmap?>(null) }
    var rendering by remember(payload) { mutableStateOf(true) }
    var busy by remember(payload) { mutableStateOf(false) }

    DisposableEffect(payload) {
        onDispose {
            previewBitmap?.recycle()
            previewBitmap = null
        }
    }

    LaunchedEffect(payload, selectedTheme) {
        rendering = true
        val rendered = renderPersonalRecapCardBitmap(payload, selectedTheme)
        val previous = previewBitmap
        previewBitmap = rendered
        if (previous != null && previous !== rendered) previous.recycle()
        rendering = false
    }

    suspend fun saveCard() {
        val current = previewBitmap ?: return
        if (busy) return
        busy = true
        try {
            val saved = savePersonalRecapCardToGallery(context, current)
            if (saved != null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(context, "时光卡片已保存到相册", Toast.LENGTH_SHORT).show()
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
        if (granted) scope.launch { saveCard() }
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    AppModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        dismissOnBackPress = !busy,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .navigationBarsPadding()
                .appVerticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppText(
                text = "时光纪念卡片",
                style = MaterialTheme.typography.titleMedium,
                color = AppContentColors.primaryText(),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 380.dp)
                    .clip(AppShapes.container(ContainerLevel.Card)),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = previewBitmap
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "时光纪念卡片预览",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(
                                (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat())
                                    .coerceIn(0.6f, 1.8f)
                            ),
                    )
                }
                if (rendering) AdaptiveLoadingIndicator(size = 24.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PersonalRecapCardTheme.entries.forEach { theme ->
                    AppFilterChip(
                        selected = selectedTheme == theme,
                        onClick = { selectedTheme = theme },
                        label = { AppText(theme.label) },
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppOutlinedButton(
                    enabled = !busy && previewBitmap != null,
                    onClick = {
                        val current = previewBitmap ?: return@AppOutlinedButton
                        scope.launch {
                            busy = true
                            try {
                                val uri = writePersonalRecapShareFile(context, current)
                                if (uri != null) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/png"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newRawUri("时光纪念卡片", uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "分享时光纪念卡片"))
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
                    modifier = Modifier.weight(1f),
                ) { AppText("分享") }
                AppButton(
                    enabled = !busy && previewBitmap != null,
                    onClick = {
                        if (storagePermission.isGranted) scope.launch { saveCard() }
                        else storagePermission.request()
                    },
                    modifier = Modifier.weight(1f),
                ) { AppText(if (busy) "处理中…" else "保存相册") }
            }
        }
    }
}
