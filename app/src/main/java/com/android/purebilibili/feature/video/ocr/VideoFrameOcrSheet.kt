package com.android.purebilibili.feature.video.ocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppOutlinedButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import com.android.purebilibili.core.ui.rememberAppHapticFeedback
import com.android.purebilibili.feature.video.note.formatVideoNoteTimestamp
import com.android.purebilibili.feature.video.postcard.VideoPostcardDraft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoFrameOcrSheet(
    draft: VideoPostcardDraft,
    onDismiss: () -> Unit,
    onInsertIntoNote: ((quote: String, moodNote: String, positionMs: Long) -> Unit)? = null,
) {
    val context = LocalContext.current
    val textHaptic = rememberAppHapticFeedback(AppHapticScene.TEXT)
    val maxListHeight = (LocalConfiguration.current.screenHeightDp * 0.45f).coerceIn(140f, 380f).dp
    var loading by remember(draft.imageUri) { mutableStateOf(true) }
    var blocks by remember(draft.imageUri) { mutableStateOf<List<VideoFrameOcrBlock>>(emptyList()) }
    var selectedIds by remember(draft.imageUri) { mutableStateOf<Set<Int>>(emptySet()) }
    var errorMessage by remember(draft.imageUri) { mutableStateOf<String?>(null) }

    LaunchedEffect(draft.imageUri) {
        loading = true
        errorMessage = null
        when (val result = OnDeviceFrameOcrEngine.recognizeFromUri(context, draft.imageUri)) {
            is VideoFrameOcrResult.Success -> {
                blocks = result.blocks
                selectedIds = result.blocks.mapTo(mutableSetOf()) { it.id }
            }
            is VideoFrameOcrResult.Failure -> {
                blocks = emptyList()
                selectedIds = emptySet()
                errorMessage = result.message
            }
        }
        loading = false
    }

    val selectedText = remember(blocks, selectedIds) {
        joinSelectedOcrText(blocks, selectedIds)
    }
    val timestampLabel = remember(draft.positionMs) {
        formatVideoNoteTimestamp((draft.positionMs / 1000L).coerceAtLeast(0L))
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        useWindowPredictiveBack = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = "画面识字（端侧离线）",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AppText(
                        text = "时间点 $timestampLabel · 勾选需要的文字段落",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (blocks.isNotEmpty()) {
                    val allSelected = selectedIds.size == blocks.size
                    AppTextButton(
                        onClick = {
                            selectedIds = if (allSelected) emptySet() else blocks.mapTo(mutableSetOf()) { it.id }
                        },
                    ) {
                        AppText(if (allSelected) "取消全选" else "全选")
                    }
                }
            }

            when {
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            AdaptiveLoadingIndicator()
                            AppText(
                                text = "正在本地识别画面文字…",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppContentColors.secondaryText(),
                            )
                        }
                    }
                }
                errorMessage != null -> {
                    AppText(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                blocks.isEmpty() -> {
                    AppText(
                        text = "当前截图中未检测到可读文字",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppContentColors.secondaryText(),
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxListHeight)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        blocks.forEach { block ->
                            val checked = block.id in selectedIds
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AppShapes.container(ContainerLevel.Card))
                                    .background(
                                        if (checked) {
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                        } else {
                                            Color.Transparent
                                        }
                                    )
                                    .clickable {
                                        selectedIds = if (checked) selectedIds - block.id else selectedIds + block.id
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { next ->
                                        selectedIds = if (next) selectedIds + block.id else selectedIds - block.id
                                    },
                                )
                                AppText(
                                    text = block.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f).padding(top = 6.dp),
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
                AppOutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("BiliPai OCR", selectedText))
                        textHaptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        Toast.makeText(context, "已复制识别文字", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    enabled = selectedText.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    AppText("复制选中")
                }
                if (onInsertIntoNote != null) {
                    AppButton(
                        onClick = {
                            textHaptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            onDismiss()
                            onInsertIntoNote(selectedText, "", draft.positionMs)
                        },
                        enabled = selectedText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        AppText("记入私有笔记")
                    }
                }
            }
        }
    }
}
