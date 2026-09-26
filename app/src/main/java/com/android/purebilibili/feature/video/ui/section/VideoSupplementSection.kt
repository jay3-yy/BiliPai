@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.feature.video.ui.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
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
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import com.android.purebilibili.feature.video.ui.VideoDetailShapes
import com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState

/**
 * Compact entry row replacing the previously inline AI summary / video note cards.
 * Heavy content lives in [AiSummarySheet] / [VideoNoteListSheet], mirroring the
 * PiliPlus detail-page pattern (small trigger + bottom sheet).
 */
@Composable
fun VideoSupplementEntryRow(
    showAiSummary: Boolean,
    showNote: Boolean,
    onAiSummaryClick: () -> Unit,
    onNoteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showAiSummary && !showNote) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showAiSummary) {
            SupplementEntryPill(
                icon = Icons.Outlined.AutoAwesome,
                label = "AI 总结",
                onClick = onAiSummaryClick,
            )
        }
        if (showNote) {
            SupplementEntryPill(
                icon = Icons.Outlined.EditNote,
                label = "笔记",
                onClick = onNoteClick,
            )
        }
    }
}

@Composable
private fun SupplementEntryPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = VideoDetailShapes.field(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            AppText(
                text = label,
                style = MaterialTheme.typography.labelLarge,
            )
        }
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
    onRetryClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: (VideoNoteEditorDocument) -> Unit,
    onPublicNoteClick: (Long, String) -> Unit,
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
                defaultCollapsed = false,
            )
        }
    }
}
