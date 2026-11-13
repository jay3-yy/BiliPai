@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import com.android.purebilibili.feature.video.ui.VideoDetailShapes
import com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState

/**
 * 信息行右端提供 AI 总结与视频笔记入口，内容在各自面板中展示。
 */
@Composable
fun VideoSupplementStatsActions(
    showAiSummary: Boolean,
    showNote: Boolean,
    onAiSummaryClick: () -> Unit,
    onNoteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showAiSummary && !showNote) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAiSummary) {
            Box(
                Modifier.size(28.dp).clickable(onClickLabel = "AI 总结", onClick = onAiSummaryClick),
                contentAlignment = Alignment.Center,
            ) {
                com.android.purebilibili.core.ui.ClientAiSummaryIcon(Modifier.size(18.dp))
            }
        }
        if (showNote) {
            SupplementEntryIcon(
                icon = Icons.Outlined.EditNote,
                label = "视频笔记",
                onClick = onNoteClick,
            )
        }
    }
}

@Composable
private fun SupplementEntryIcon(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    // 统计区右侧入口使用 28dp 点击槽；时间与在线人数为它预留实际宽度，图标保持 18dp。
    Box(
        modifier = Modifier
            .size(28.dp)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = label,
            tint = AppContentColors.secondaryText(),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Bottom sheet hosting the full AI summary (or its loading/retry prompt). */
@Composable
fun AiSummarySheet(
    visible: Boolean,
    aiSummary: AiSummaryData?,
    promptState: AiSummaryPromptState?,
    onDismiss: () -> Unit,
    onTimestampClick: ((Long) -> Unit)?,
    onRetry: () -> Unit,
    onCreateNoteDraft: () -> Unit,
) {
    if (!visible) return
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            if (hasAiSummaryContent(aiSummary)) {
                AiSummaryCard(
                    aiSummary = aiSummary,
                    onTimestampClick = onTimestampClick,
                    onCreateNoteDraftClick = onCreateNoteDraft,
                    defaultExpanded = true,
                )
            } else if (promptState != null) {
                AiSummaryPromptCard(
                    promptState = promptState,
                    onActionClick = onRetry,
                )
            }
        }
    }
}

/** Bottom sheet hosting the video note list and its actions. */
@Composable
fun VideoNoteListSheet(
    visible: Boolean,
    noteState: VideoNoteUiState,
    isLoggedIn: Boolean,
    onDismiss: () -> Unit,
    onCreateOrEditClick: () -> Unit,
    onOfficialEditorClick: () -> Unit = {},
    onRetryClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: (VideoNoteEditorDocument) -> Unit,
    onPublicNoteClick: (Long, String) -> Unit,
    onAuthorClick: (Long) -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    if (!visible) return
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            VideoNoteCard(
                noteState = noteState,
                isLoggedIn = isLoggedIn,
                onCreateOrEditClick = onCreateOrEditClick,
                onRetryClick = onRetryClick,
                onDeleteClick = onDeleteClick,
                onShareClick = onShareClick,
                onPublicNoteClick = onPublicNoteClick,
                onAuthorClick = onAuthorClick,
                onLoadMore = onLoadMore,
                defaultCollapsed = false,
            )
            VideoNoteSheetFooter(
                isLoggedIn = isLoggedIn,
                onOfficialEditorClick = onOfficialEditorClick,
                onCreateOrEditClick = onCreateOrEditClick,
            )
        }
    }
}

/** PiliPlus 式底部操作栏：官方 H5 编辑器 + 内置编辑器双入口。 */
@Composable
private fun VideoNoteSheetFooter(
    isLoggedIn: Boolean,
    onOfficialEditorClick: () -> Unit,
    onCreateOrEditClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppButton(
            onClick = onOfficialEditorClick,
            enabled = isLoggedIn,
            modifier = Modifier.weight(1f),
        ) {
            AppText(if (isLoggedIn) "开始记笔记" else "登录后开始记笔记")
        }
        AppButton(
            onClick = onCreateOrEditClick,
            modifier = Modifier.weight(1f),
        ) {
            AppText("内置编辑器")
        }
    }
    if (!isLoggedIn) {
        AppText(
            text = "官方笔记编辑器需要登录 B 站账号",
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText(),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
