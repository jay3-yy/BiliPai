package com.android.purebilibili.feature.video.annotation

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.feature.video.note.formatVideoNoteTimestamp
import com.android.purebilibili.feature.video.postcard.VideoPostcardDraft
import com.android.purebilibili.feature.video.util.buildScreenshotFileName
import com.android.purebilibili.feature.video.util.saveScreenshotToGalleryUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 面向平板手写笔（含笔压感动态线宽）与触屏的暂停画面批注弹层：
 * 支持多色圈画、撤销、清空、高清合成保存到相册并可一键带时间戳写入私有笔记。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoFrameAnnotationSheet(
    draft: VideoPostcardDraft,
    onDismiss: () -> Unit,
    onSaveToPrivateNote: ((quote: String, moodNote: String, positionMs: Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sourceBitmap by remember(draft.imageUri) { mutableStateOf<Bitmap?>(null) }
    var loading by remember(draft.imageUri) { mutableStateOf(true) }
    var saving by remember(draft.imageUri) { mutableStateOf(false) }
    var savedUri by remember(draft.imageUri) { mutableStateOf<Uri?>(null) }
    var activeColorArgb by remember { mutableIntStateOf(defaultAnnotationColorArgb()) }
    var activeWidthRatio by remember { mutableFloatStateOf(0.011f) }
    val strokes = remember(draft.imageUri) { mutableStateListOf<VideoAnnotationStroke>() }
    val livePoints = remember(draft.imageUri) { mutableStateListOf<NormalizedStrokePoint>() }

    LaunchedEffect(draft.imageUri) {
        loading = true
        sourceBitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(draft.imageUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.getOrNull()
        }
        loading = false
    }

    DisposableEffect(sourceBitmap) {
        val current = sourceBitmap
        onDispose { current?.recycle() }
    }

    val timestampLabel = remember(draft.positionMs) {
        formatVideoNoteTimestamp((draft.positionMs / 1000L).coerceAtLeast(0L))
    }

    AppModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        useWindowPredictiveBack = !saving,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        AppText("画面批注（支持手写笔压感）", style = MaterialTheme.typography.titleMedium)
                        AppText(
                            text = "时间点 $timestampLabel · 圈画重点后可保存相册或记入笔记",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        AppTextButton(
                            onClick = {
                                if (strokes.isNotEmpty()) {
                                    strokes.removeAt(strokes.lastIndex)
                                    savedUri = null
                                }
                            },
                            enabled = strokes.isNotEmpty() && !saving,
                        ) { AppText("撤销") }
                        AppTextButton(
                            onClick = {
                                strokes.clear()
                                savedUri = null
                            },
                            enabled = strokes.isNotEmpty() && !saving,
                        ) { AppText("清空") }
                    }
                }

                val bitmap = sourceBitmap
                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            contentAlignment = Alignment.Center,
                        ) {
                            AdaptiveLoadingIndicator()
                        }
                    }
                    bitmap == null -> {
                        AppText(
                            text = "无法读取截图画面，请重新截图后再试",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> {
                        val imageAspect = (bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat())
                            .coerceIn(0.6f, 2.4f)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(imageAspect)
                                .clip(AppShapes.container(ContainerLevel.Card))
                                .pointerInput(activeColorArgb, activeWidthRatio, saving) {
                                    if (saving) return@pointerInput
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        val w = size.width.toFloat().coerceAtLeast(1f)
                                        val h = size.height.toFloat().coerceAtLeast(1f)
                                        livePoints.clear()
                                        livePoints += NormalizedStrokePoint(
                                            x = (down.position.x / w).coerceIn(0f, 1f),
                                            y = (down.position.y / h).coerceIn(0f, 1f),
                                            pressure = down.pressure,
                                        )
                                        savedUri = null
                                        down.consume()
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            livePoints += NormalizedStrokePoint(
                                                x = (change.position.x / w).coerceIn(0f, 1f),
                                                y = (change.position.y / h).coerceIn(0f, 1f),
                                                pressure = change.pressure,
                                            )
                                            change.consume()
                                        }
                                        if (livePoints.isNotEmpty()) {
                                            strokes += VideoAnnotationStroke(
                                                colorArgb = activeColorArgb,
                                                widthRatio = activeWidthRatio,
                                                points = livePoints.toList(),
                                            )
                                            livePoints.clear()
                                        }
                                    }
                                },
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "待批注的视频画面",
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val minSide = minOf(size.width, size.height).coerceAtLeast(1f)
                                fun drawStroke(colorArgb: Int, widthRatio: Float, points: List<NormalizedStrokePoint>) {
                                    if (points.isEmpty()) return
                                    val avgPressure = points.map { it.pressure }.average().toFloat()
                                    val strokeWidthPx = (widthRatio * minSide * resolveStylusStrokeWidthScale(avgPressure))
                                        .coerceIn(2f, minSide * 0.06f)
                                    val strokeColor = Color(colorArgb)
                                    if (points.size == 1) {
                                        drawCircle(
                                            color = strokeColor,
                                            radius = strokeWidthPx * 0.5f,
                                            center = Offset(points[0].x * size.width, points[0].y * size.height),
                                        )
                                    } else {
                                        val path = Path().apply {
                                            moveTo(points[0].x * size.width, points[0].y * size.height)
                                            for (i in 1 until points.size) {
                                                lineTo(points[i].x * size.width, points[i].y * size.height)
                                            }
                                        }
                                        drawPath(
                                            path = path,
                                            color = strokeColor,
                                            style = Stroke(
                                                width = strokeWidthPx,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round,
                                            ),
                                        )
                                    }
                                }
                                strokes.forEach { drawStroke(it.colorArgb, it.widthRatio, it.points) }
                                if (livePoints.isNotEmpty()) {
                                    drawStroke(activeColorArgb, activeWidthRatio, livePoints)
                                }
                            }
                        }
                    }
                }

                // 笔色与粗细工具栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VIDEO_ANNOTATION_PALETTE.forEach { swatch ->
                            val argb = swatch.toArgb()
                            val selected = activeColorArgb == argb
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape,
                                    )
                                    .clickable(enabled = !saving) { activeColorArgb = argb },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("细" to 0.007f, "中" to 0.011f, "粗" to 0.018f).forEach { (label, ratio) ->
                            val selected = kotlin.math.abs(activeWidthRatio - ratio) < 0.001f
                            AppTextButton(
                                onClick = { activeWidthRatio = ratio },
                                enabled = !saving,
                            ) {
                                AppText(
                                    text = if (selected) "[$label]" else label,
                                    color = if (selected) MaterialTheme.colorScheme.primary else AppContentColors.secondaryText(),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val readyUri = savedUri
                if (readyUri != null) {
                    AppOutlinedButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(Intent.EXTRA_STREAM, readyUri)
                                    clipData = ClipData.newRawUri("视频批注图", readyUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "分享批注截图"))
                            }.onFailure {
                                Toast.makeText(context, "无法打开分享", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { AppText("分享批注图") }
                } else {
                    AppOutlinedButton(
                        onClick = {
                            val bmp = sourceBitmap ?: return@AppOutlinedButton
                            saving = true
                            scope.launch {
                                try {
                                    val rendered = withContext(Dispatchers.Default) {
                                        renderAnnotatedBitmap(bmp, strokes.toList())
                                    }
                                    val uri = try {
                                        saveScreenshotToGalleryUri(
                                            context = context,
                                            bitmap = rendered,
                                            fileName = buildScreenshotFileName(
                                                "${draft.videoTitle}_note",
                                                System.currentTimeMillis(),
                                            ),
                                        )
                                    } finally {
                                        rendered.recycle()
                                    }
                                    if (uri != null) {
                                        savedUri = uri
                                        Toast.makeText(context, "批注图已保存到相册", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "保存批注图失败", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving && sourceBitmap != null,
                        modifier = Modifier.weight(1f),
                    ) { AppText(if (saving) "保存中…" else "保存相册") }
                }

                if (onSaveToPrivateNote != null) {
                    AppButton(
                        onClick = {
                            onDismiss()
                            onSaveToPrivateNote(
                                draft.initialQuote.ifBlank { "画面批注记录（$timestampLabel）" },
                                "已保存批注画面（共 ${strokes.size} 笔标注）",
                                draft.positionMs,
                            )
                        },
                        enabled = !saving && sourceBitmap != null,
                        modifier = Modifier.weight(1f),
                    ) { AppText("记入私有笔记") }
                }
            }
        }
    }
}
