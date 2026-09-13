package com.android.purebilibili.feature.video.ui.section

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.Share
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppAssistChip
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import com.android.purebilibili.core.ui.components.AppContentCard
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.feature.video.note.VideoNoteBlock
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.VideoNoteLoadStatus
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import com.android.purebilibili.feature.video.ui.VideoDetailShapes
import com.android.purebilibili.feature.video.note.hasUnsavedVideoNoteDraft
import com.android.purebilibili.feature.video.note.resolveVideoNotePrimaryActionLabel
import com.android.purebilibili.feature.video.note.resolveVideoNoteEmptyMessage
import com.android.purebilibili.feature.video.note.shouldShowVideoNoteBody
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VideoNoteCard(
    noteState: VideoNoteUiState,
    isLoggedIn: Boolean,
    onCreateOrEditClick: () -> Unit,
    onRetryClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onShareClick: (VideoNoteEditorDocument) -> Unit,
    onPublicNoteClick: (Long, String) -> Unit,
    defaultCollapsed: Boolean = false,
    modifier: Modifier = Modifier
) {
    val hasUnsavedDraft = hasUnsavedVideoNoteDraft(noteState)
    val primaryActionLabel = resolveVideoNotePrimaryActionLabel(noteState)
    var userExpanded by remember(defaultCollapsed) { mutableStateOf(!defaultCollapsed) }
    val showBody = shouldShowVideoNoteBody(
        defaultCollapsed = defaultCollapsed,
        userExpanded = userExpanded
    )
    val primaryActionEnabled = (isLoggedIn || hasUnsavedDraft) &&
        !noteState.forbidNoteEntrance &&
        !noteState.saving
    val showSecondaryActions = noteState.privateNoteDocument != null ||
        noteState.status == VideoNoteLoadStatus.ERROR

    AppContentCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 14.dp,
        ),
    ) {
        // Header: icon + title/subtitle | primary action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconBox()
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = "视频笔记",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(modifier = Modifier.height(2.dp))
                AppText(
                    text = resolveNoteSubtitle(noteState, isLoggedIn),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (noteState.status == VideoNoteLoadStatus.LOADING) {
                AdaptiveLoadingIndicator(size = 18.dp, strokeWidth = 2.dp)
            } else if (showBody) {
                VideoNotePrimaryActionButton(
                    label = primaryActionLabel,
                    enabled = primaryActionEnabled,
                    onClick = onCreateOrEditClick,
                )
            }
            if (defaultCollapsed) {
                AppTextButton(onClick = { userExpanded = !userExpanded }) {
                    AppText(if (showBody) "收起" else "展开")
                }
            }
        }

        if (showBody) {
            if (!noteState.feedbackMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AppText(
                    text = noteState.feedbackMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!noteState.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AppText(
                    text = noteState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (showSecondaryActions) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    noteState.privateNoteDocument?.let { privateDocument ->
                        VideoDetailSecondaryButton(
                            onClick = { onShareClick(privateDocument) },
                        ) {
                            AppIcon(
                                Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AppText("分享")
                        }
                        VideoDetailSecondaryButton(
                            onClick = onDeleteClick,
                            enabled = !noteState.deleting,
                        ) {
                            AppIcon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            AppText("删除")
                        }
                    }
                    if (noteState.status == VideoNoteLoadStatus.ERROR) {
                        AppTextButton(onClick = onRetryClick) {
                            AppText("重试")
                        }
                    }
                }
            }

            if (noteState.publicNotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AppText(
                    text = "公开笔记 ${noteState.publicNoteCount} 篇",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                noteState.publicNotes.take(2).forEach { note ->
                    AppText(
                        text = note.title.ifBlank { note.summary },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(VideoDetailShapes.field())
                            .clickable { onPublicNoteClick(note.cvid, note.webUrl) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoNotePrimaryActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AppButton(
        onClick = onClick,
        enabled = enabled,
        colors = if (LocalAppUiStyle.current == AppUiStyle.MATERIAL3) {
            ButtonDefaults.buttonColors(
                containerColor = colorScheme.primaryContainer,
                contentColor = colorScheme.onPrimaryContainer,
            )
        } else {
            null // AppButton selects the native Miuix primary colors.
        },
    ) {
        if (label != "新建") {
            AppIcon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        AppText(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoNoteEditorSheet(
    noteState: VideoNoteUiState,
    onDismiss: () -> Unit,
    onDocumentChange: (VideoNoteEditorDocument) -> Unit,
    onInsertTimestamp: () -> Unit,
    onTimestampClick: (Long) -> Unit,
    onShare: (VideoNoteEditorDocument) -> Unit,
    onSave: (VideoNoteEditorDocument) -> Unit
) {
    if (!noteState.editorVisible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(noteState.editorDocument.title) { mutableStateOf(noteState.editorDocument.title) }
    var markdown by remember(noteState.editorDocument) { mutableStateOf(documentToMarkdown(noteState.editorDocument)) }
    val richTextState = rememberRichTextState()

    LaunchedEffect(markdown) {
        richTextState.setMarkdown(markdown)
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(560.dp)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            AppText(
                text = if (noteState.editorFromAiSummary) "AI 笔记草稿" else "视频笔记",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(12.dp))
            AppOutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { AppText("标题") },
                labelText = "标题",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            VideoNoteEditorToolbar(
                onBoldClick = {
                    markdown = wrapSelectionFallback(richTextState.toMarkdown(), "**")
                    richTextState.setMarkdown(markdown)
                },
                onHighlightClick = {
                    markdown = richTextState.toMarkdown() + "\n==高亮重点=="
                    richTextState.setMarkdown(markdown)
                },
                onBulletClick = {
                    markdown = richTextState.toMarkdown() + "\n- "
                    richTextState.setMarkdown(markdown)
                },
                onTimestampClick = onInsertTimestamp,
                onUndoClick = {
                    richTextState.history.undo()
                    markdown = richTextState.toMarkdown()
                },
                onRedoClick = {
                    richTextState.history.redo()
                    markdown = richTextState.toMarkdown()
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
                // Keep the same rich-text state/history, with Miuix field colors and shape.
                BasicRichTextEditor(
                    state = richTextState,
                    textStyle = MiuixTheme.textStyles.body1.copy(
                        color = AppSurfaceTokens.onSurface()
                    ),
                    cursorBrush = SolidColor(AppSurfaceTokens.primary()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(VideoDetailShapes.field())
                        .background(AppSurfaceTokens.surfaceContainer())
                        .padding(10.dp)
                )
            } else {
                RichTextEditor(
                    state = richTextState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(VideoDetailShapes.field())
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            VideoNoteTimestampChips(
                document = noteState.editorDocument,
                onTimestampClick = onTimestampClick
            )
            if (!noteState.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AppText(
                    text = noteState.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                VideoDetailSecondaryButton(
                    onClick = {
                        val document = markdownToDocument(
                            title = title,
                            markdown = richTextState.toMarkdown(),
                            timestamps = noteState.editorDocument.blocks.filterIsInstance<VideoNoteBlock.Timestamp>()
                        )
                        onDocumentChange(document)
                        onShare(document)
                    },
                    enabled = !noteState.saving
                ) {
                    AppIcon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    AppText("分享")
                }
                Spacer(modifier = Modifier.width(8.dp))
                AppTextButton(onClick = onDismiss) {
                    AppText("取消")
                }
                Spacer(modifier = Modifier.width(8.dp))
                AppButton(
                    onClick = {
                        val nextMarkdown = richTextState.toMarkdown()
                        val document = markdownToDocument(
                            title = title,
                            markdown = nextMarkdown,
                            timestamps = noteState.editorDocument.blocks.filterIsInstance<VideoNoteBlock.Timestamp>()
                        )
                        onDocumentChange(document)
                        onSave(document)
                    },
                    enabled = !noteState.saving
                ) {
                    if (noteState.saving) {
                        AdaptiveLoadingIndicator(size = 16.dp, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    AppText("保存")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun VideoNoteDeleteConfirmDialog(
    visible: Boolean,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText("删除视频笔记") },
        text = { AppText("删除后无法在 BiliPai 内恢复。确认要删除这条笔记吗？") },
        confirmButton = {
            AppTextButton(onClick = onConfirm, enabled = !deleting) {
                AppText(if (deleting) "删除中" else "删除")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, enabled = !deleting) {
                AppText("取消")
            }
        }
    )
}

@Composable
private fun IconBox() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(VideoDetailShapes.leadingIcon())
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            imageVector = Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun VideoNoteEditorToolbar(
    onBoldClick: () -> Unit,
    onHighlightClick: () -> Unit,
    onBulletClick: () -> Unit,
    onTimestampClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AppIconButton(onClick = onBoldClick) { AppText("B", fontWeight = FontWeight.Bold) }
        AppIconButton(onClick = onHighlightClick) {
            AppIcon(Icons.Outlined.FormatColorFill, contentDescription = "高亮")
        }
        AppIconButton(onClick = onBulletClick) { AppText("-") }
        AppIconButton(onClick = onTimestampClick) {
            AppIcon(Icons.Outlined.AccessTime, contentDescription = "插入时间点")
        }
        AppIconButton(onClick = onUndoClick) {
            AppIcon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "撤销")
        }
        AppIconButton(onClick = onRedoClick) {
            AppIcon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "重做")
        }
    }
}

@Composable
private fun VideoNoteTimestampChips(
    document: VideoNoteEditorDocument,
    onTimestampClick: (Long) -> Unit
) {
    val timestamps = document.blocks.filterIsInstance<VideoNoteBlock.Timestamp>()
    if (timestamps.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        timestamps.forEach { timestamp ->
            if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
                VideoDetailSecondaryButton(
                    onClick = { onTimestampClick(timestamp.seconds * 1000L) }
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    AppText(timestamp.label)
                }
            } else {
                AppAssistChip(
                    onClick = { onTimestampClick(timestamp.seconds * 1000L) },
                    label = { AppText(timestamp.label) },
                    leadingIcon = {
                        AppIcon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }
    }
}

private fun resolveNoteSubtitle(noteState: VideoNoteUiState, isLoggedIn: Boolean): String {
    return when {
        noteState.privateNoteDocument != null -> noteState.privateNoteSummary.ifBlank {
            "这条视频已有私有笔记。"
        }
        hasUnsavedVideoNoteDraft(noteState) -> "草稿还在，点继续编辑可以把这一段认真留下来。"
        else -> resolveVideoNoteEmptyMessage(isLoggedIn, noteState.forbidNoteEntrance)
    }
}

private fun documentToMarkdown(document: VideoNoteEditorDocument): String {
    return document.blocks.joinToString(separator = "") { block ->
        when (block) {
            is VideoNoteBlock.Text -> when {
                block.unorderedList -> "- ${block.text.removePrefix("- ")}"
                block.bold -> "**${block.text}**"
                block.highlight -> "==${block.text}=="
                else -> block.text
            }
            is VideoNoteBlock.Timestamp -> "[${block.label}]"
        }
    }
}

private fun markdownToDocument(
    title: String,
    markdown: String,
    timestamps: List<VideoNoteBlock.Timestamp>
): VideoNoteEditorDocument {
    val blocks = mutableListOf<VideoNoteBlock>()
    var remaining = markdown
    timestamps.forEach { timestamp ->
        val marker = "[${timestamp.label}]"
        val index = remaining.indexOf(marker)
        if (index >= 0) {
            remaining.take(index).takeIf { it.isNotEmpty() }?.let { blocks += textBlockFromMarkdown(it) }
            blocks += timestamp
            remaining = remaining.drop(index + marker.length)
        }
    }
    if (remaining.isNotEmpty()) blocks += textBlockFromMarkdown(remaining)
    return VideoNoteEditorDocument(title = title, blocks = blocks.ifEmpty { listOf(VideoNoteBlock.Text("")) })
}

private fun textBlockFromMarkdown(markdown: String): VideoNoteBlock.Text {
    val trimmed = markdown.trim('\n')
    return when {
        trimmed.startsWith("- ") -> VideoNoteBlock.Text(trimmed.removePrefix("- ") + "\n", unorderedList = true)
        trimmed.startsWith("**") && trimmed.endsWith("**") -> VideoNoteBlock.Text(trimmed.removeSurrounding("**"), bold = true)
        trimmed.startsWith("==") && trimmed.endsWith("==") -> VideoNoteBlock.Text(trimmed.removeSurrounding("=="), highlight = true)
        else -> VideoNoteBlock.Text(markdown)
    }
}

private fun wrapSelectionFallback(value: String, wrapper: String): String {
    return if (value.isBlank()) "$wrapper$wrapper" else "$wrapper$value$wrapper"
}
