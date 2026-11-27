package com.android.purebilibili.feature.video.clip

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppRichHapticPattern
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppSlider
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.core.ui.rememberRichHapticFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoClipStudioSheet(
    videoTitle: String,
    videoUrl: String,
    audioUrl: String,
    initialPositionMs: Long,
    totalDurationMs: Long,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val seekRichHaptic = rememberRichHapticFeedback(AppHapticScene.PLAYER_SEEK)
    val hasValidDuration = totalDurationMs > MIN_CLIP_DURATION_MS
    val initialRange = remember(videoUrl, audioUrl, hasValidDuration) {
        resolveInitialClipRange(initialPositionMs, totalDurationMs)
    }
    var startMs by remember(videoUrl, audioUrl, hasValidDuration) { mutableLongStateOf(initialRange.startMs) }
    var endMs by remember(videoUrl, audioUrl, hasValidDuration) { mutableLongStateOf(initialRange.endMs) }
    var lastHapticSec by remember(videoUrl, audioUrl, hasValidDuration) { mutableLongStateOf(-1L) }
    var exportMode by remember { mutableStateOf(VideoClipExportMode.MP4_VIDEO) }
    var exporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var exportedUri by remember { mutableStateOf<Uri?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val normalized = remember(startMs, endMs, totalDurationMs) {
        normalizeClipRange(startMs, endMs, totalDurationMs)
    }
    val hasValidSource = when (exportMode) {
        VideoClipExportMode.MP4_VIDEO -> videoUrl.isNotBlank()
        VideoClipExportMode.M4A_AUDIO -> audioUrl.isNotBlank() || videoUrl.isNotBlank()
    }

    AppModalBottomSheet(
        onDismissRequest = { if (!exporting) onDismiss() },
        useWindowPredictiveBack = !exporting,
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AppText(
                        text = "随手切片工坊（Media3 硬件导出）",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AppText(
                        text = videoTitle.ifBlank { "当前视频" },
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    VideoClipExportMode.entries.forEach { mode ->
                        val selected = exportMode == mode
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(AppShapes.container(ContainerLevel.Card))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable(enabled = !exporting) {
                                    exportMode = mode
                                    exportedUri = null
                                    errorMessage = null
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppText(
                                text = mode.label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    AppContentColors.primaryText()
                                },
                            )
                        }
                    }
                }

                if (totalDurationMs > MIN_CLIP_DURATION_MS) {
                    val maxFloat = totalDurationMs.toFloat()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            AppText("起点 ${formatClipTimeLabel(normalized.startMs)}", style = MaterialTheme.typography.labelMedium)
                            AppText(
                                "时长 ${normalized.durationMs / 1_000L} 秒（上限 60 秒）",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppContentColors.secondaryText(),
                            )
                        }
                        AppSlider(
                            value = normalized.startMs.toFloat(),
                            onValueChange = { raw ->
                                val nextStart = raw.roundToLong()
                                val nextRange = normalizeClipRange(nextStart, maxOf(endMs, nextStart + MIN_CLIP_DURATION_MS), totalDurationMs)
                                startMs = nextRange.startMs
                                endMs = nextRange.endMs
                                exportedUri = null
                                errorMessage = null
                                val sec = nextRange.startMs / 1_000L
                                if (sec != lastHapticSec) {
                                    lastHapticSec = sec
                                    seekRichHaptic(AppRichHapticPattern.SEEK_TICK)
                                }
                            },
                            valueRange = 0f..maxFloat,
                            enabled = !exporting,
                        )
                        AppText("终点 ${formatClipTimeLabel(normalized.endMs)}", style = MaterialTheme.typography.labelMedium)
                        AppSlider(
                            value = normalized.endMs.toFloat(),
                            onValueChange = { raw ->
                                val nextEnd = raw.roundToLong()
                                val nextStart = if (nextEnd < startMs + MIN_CLIP_DURATION_MS) {
                                    (nextEnd - MIN_CLIP_DURATION_MS).coerceAtLeast(0L)
                                } else if (nextEnd - startMs > MAX_CLIP_DURATION_MS) {
                                    (nextEnd - MAX_CLIP_DURATION_MS).coerceAtLeast(0L)
                                } else {
                                    startMs
                                }
                                val nextRange = normalizeClipRange(nextStart, nextEnd, totalDurationMs)
                                startMs = nextRange.startMs
                                endMs = nextRange.endMs
                                exportedUri = null
                                errorMessage = null
                                val sec = nextRange.endMs / 1_000L
                                if (sec != lastHapticSec) {
                                    lastHapticSec = sec
                                    seekRichHaptic(AppRichHapticPattern.SEEK_TICK)
                                }
                            },
                            valueRange = 0f..maxFloat,
                            enabled = !exporting,
                        )
                    }
                } else {
                    AppText(
                        text = "当前流时长未知或过短，暂不支持区间截取",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                }

                if (exporting) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { (exportProgress.coerceIn(0, 100)) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        AppText(
                            text = "正在硬件导出 ${exportProgress.coerceIn(0, 100)}%…",
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                }

                errorMessage?.let { msg ->
                    AppText(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (exportedUri != null) {
                    AppText(
                        text = "已导出至系统媒体库，可直接分享给好友",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppOutlinedButton(
                    onClick = onDismiss,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                ) {
                    AppText("关闭")
                }
                val readyUri = exportedUri
                if (readyUri != null) {
                    AppButton(
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = exportMode.mimeType
                                    putExtra(Intent.EXTRA_STREAM, readyUri)
                                    clipData = ClipData.newRawUri("BiliPai 片段", readyUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "分享导出片段"))
                            }.onFailure {
                                Toast.makeText(context, "无法打开系统分享", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText("立即分享")
                    }
                } else {
                    AppButton(
                        onClick = {
                            exporting = true
                            exportProgress = 0
                            exportedUri = null
                            errorMessage = null
                            scope.launch {
                                try {
                                    val resultUri = VideoClipTransformerEngine.exportClip(
                                        context = context,
                                        videoTitle = videoTitle,
                                        videoUrl = videoUrl,
                                        audioUrl = audioUrl,
                                        range = normalized,
                                        mode = exportMode,
                                        onProgress = { pct -> exportProgress = pct },
                                    )
                                    exportedUri = resultUri
                                    exportProgress = 100
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    errorMessage = "导出失败：${error.message ?: "请检查网络或切换片段区间"}"
                                } finally {
                                    exporting = false
                                }
                            }
                        },
                        enabled = !exporting && hasValidSource && normalized.durationMs >= MIN_CLIP_DURATION_MS,
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText("开始导出")
                    }
                }
            }
        }
    }
}
