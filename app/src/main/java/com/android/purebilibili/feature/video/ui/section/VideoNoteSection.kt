package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.appHorizontalScroll as horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppAssistChip
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import com.android.purebilibili.core.ui.components.AppContentCard
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppOutlinedTextField
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import androidx.compose.material3.rememberModalBottomSheetState
import coil3.compose.AsyncImage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.util.LocalWindowSizeClass
import com.android.purebilibili.core.util.WindowWidthSizeClass
import com.android.purebilibili.feature.video.note.VideoNoteBlock
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.VideoNoteLoadStatus
import com.android.purebilibili.feature.video.note.VideoNotePublicPreview
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import com.android.purebilibili.feature.video.ui.VideoDetailShapes
import com.android.purebilibili.feature.video.note.hasUnsavedVideoNoteDraft
import com.android.purebilibili.feature.video.note.resolveVideoNotePrimaryActionLabel
import com.android.purebilibili.feature.video.note.resolveVideoNoteEmptyMessage
import com.android.purebilibili.feature.video.note.shouldShowVideoNoteBody
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichText
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
    onAuthorClick: (Long) -> Unit = {},
    onLoadMore: () -> Unit = {},
    defaultCollapsed: Boolean = true,
    modifier: Modifier = Modifier
) {
    val hasUnsavedDraft = hasUnsavedVideoNoteDraft(noteState)
    val isMaterial3 = LocalAppUiStyle.current == AppUiStyle.MATERIAL3
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
            .padding(horizontal = if (isMaterial3) 16.dp else 12.dp, vertical = 6.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = if (isMaterial3) 16.dp else 12.dp,
            vertical = if (isMaterial3) 14.dp else 10.dp,
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
                    color = AppContentColors.secondaryText(),
                    maxLines = if (defaultCollapsed) 1 else 2,
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
                    color = AppContentColors.accent(),
                )
            }
            if (!noteState.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                AppText(
                    text = noteState.errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppContentColors.error(),
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
                    color = AppContentColors.secondaryText(),
                )
                Spacer(modifier = Modifier.height(6.dp))
                val visibleNotes = if (defaultCollapsed) {
                    noteState.publicNotes.take(2)
                } else {
                    noteState.publicNotes
                }
                visibleNotes.forEachIndexed { index, note ->
                    PublicNoteListRow(
                        note = note,
                        onNoteClick = { onPublicNoteClick(note.cvid, note.webUrl) },
                        onAuthorClick = { onAuthorClick(note.authorMid) },
                    )
                    if (!defaultCollapsed &&
                        index == visibleNotes.lastIndex &&
                        !noteState.publicNotesEnd &&
                        !noteState.publicNotesLoadingMore
                    ) {
                        LaunchedEffect(visibleNotes.size) { onLoadMore() }
                    }
                }
                if (!defaultCollapsed && noteState.publicNotesLoadingMore) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        AdaptiveLoadingIndicator(size = 18.dp, strokeWidth = 2.dp)
                    }
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
    onTimestampClick: (Long) -> Unit,
    onShare: (VideoNoteEditorDocument) -> Unit,
    onSave: (VideoNoteEditorDocument) -> Unit,
    currentTimestampProvider: () -> VideoNoteBlock.Timestamp? = { null }
) {
    if (!noteState.editorVisible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember(noteState.editorDocument.title) { mutableStateOf(noteState.editorDocument.title) }
    val richTextState = rememberRichTextState()
    // 本次编辑会话中在光标处插入的时间戳（保存时与原文档时间戳合并后按标记回填）。
    val pendingTimestamps = remember { mutableStateListOf<VideoNoteBlock.Timestamp>() }

    LaunchedEffect(noteState.editorVisible, noteState.editorDocument) {
        if (noteState.editorVisible) {
            pendingTimestamps.clear()
            richTextState.setHtml(documentToHtml(noteState.editorDocument))
        }
    }

    val isBold = richTextState.currentSpanStyle.fontWeight == FontWeight.Bold
    val isItalic = richTextState.currentSpanStyle.fontStyle == FontStyle.Italic
    val isUnderline = richTextState.currentSpanStyle.textDecoration?.contains(TextDecoration.Underline) == true
    val isStrikethrough = richTextState.currentSpanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true
    val isHighlighted = richTextState.currentSpanStyle.background == VideoNoteHighlightColor
    val isBulletList = richTextState.isUnorderedList
    val charCount = richTextState.annotatedString.text.length
    val isWideEditor = LocalWindowSizeClass.current.widthSizeClass != WindowWidthSizeClass.Compact
    // 窄屏专用：预览模式把编辑区换成只读实时预览（同一 RichTextState，零解析开销）。
    var previewMode by remember { mutableStateOf(false) }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .imePadding()
                .padding(horizontal = 18.dp)
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
                isBold = isBold,
                isItalic = isItalic,
                isUnderline = isUnderline,
                isStrikethrough = isStrikethrough,
                isHighlighted = isHighlighted,
                isBulletList = isBulletList,
                previewMode = previewMode && !isWideEditor,
                onPreviewToggle = { previewMode = !previewMode },
                onBoldClick = { richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                onItalicClick = { richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                onUnderlineClick = {
                    richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                },
                onStrikethroughClick = {
                    richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                },
                onHighlightClick = {
                    richTextState.toggleSpanStyle(SpanStyle(background = VideoNoteHighlightColor))
                },
                onBulletClick = { richTextState.toggleUnorderedList() },
                onTimestampClick = {
                    currentTimestampProvider()?.let { timestamp ->
                        pendingTimestamps += timestamp
                        richTextState.addTextAfterSelection("[${timestamp.label}]")
                    }
                },
                onUndoClick = { richTextState.history.undo() },
                onRedoClick = { richTextState.history.redo() }
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (isWideEditor) {
                // 平板/折叠屏展开态：左侧编辑区 + 右侧（时间戳 / 实时预览）面板。
                Row(modifier = Modifier.weight(1f)) {
                    VideoNoteEditorField(
                        richTextState = richTextState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    VideoNoteSidePanel(
                        richTextState = richTextState,
                        document = noteState.editorDocument,
                        pendingTimestamps = pendingTimestamps,
                        charCount = charCount,
                        onTimestampClick = onTimestampClick,
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                    )
                }
            } else if (previewMode) {
                VideoNotePreviewPane(
                    richTextState = richTextState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                VideoNoteEditorField(
                    richTextState = richTextState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (!isWideEditor) {
                VideoNoteTimestampChips(
                    document = noteState.editorDocument,
                    pendingTimestamps = pendingTimestamps,
                    onTimestampClick = onTimestampClick
                )
            }
            if (!noteState.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                AppText(
                    text = noteState.errorMessage,
                    color = AppContentColors.error(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppText(
                    text = "$charCount 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText(),
                    modifier = Modifier.weight(1f)
                )
                VideoDetailSecondaryButton(
                    onClick = {
                        val document = buildVideoNoteDocument(
                            title = title,
                            richTextState = richTextState,
                            baseDocument = noteState.editorDocument,
                            pendingTimestamps = pendingTimestamps
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
                        val document = buildVideoNoteDocument(
                            title = title,
                            richTextState = richTextState,
                            baseDocument = noteState.editorDocument,
                            pendingTimestamps = pendingTimestamps
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

private fun buildVideoNoteDocument(
    title: String,
    richTextState: RichTextState,
    baseDocument: VideoNoteEditorDocument,
    pendingTimestamps: List<VideoNoteBlock.Timestamp>
): VideoNoteEditorDocument {
    val knownTimestamps = (baseDocument.blocks.filterIsInstance<VideoNoteBlock.Timestamp>() + pendingTimestamps)
        .distinctBy { it.label }
    return htmlToDocument(
        title = title,
        html = richTextState.toHtml(),
        timestamps = knownTimestamps
    )
}

@Composable
internal fun VideoNoteEditorField(
    richTextState: RichTextState,
    modifier: Modifier = Modifier
) {
    // richeditor 的编辑器没有内建纵向滚动，长内容依赖外层 scroll 容器。
    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
    ) {
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
                    .clip(VideoDetailShapes.field())
                    .background(AppSurfaceTokens.surfaceContainer())
                    .padding(10.dp)
            )
        } else {
            RichTextEditor(
                state = richTextState,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(VideoDetailShapes.field())
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(10.dp)
            )
        }
    }
}

/** 宽屏右侧面板：时间戳列表 / 实时预览 双 tab。 */
@Composable
internal fun VideoNoteSidePanel(
    richTextState: RichTextState,
    document: VideoNoteEditorDocument,
    pendingTimestamps: List<VideoNoteBlock.Timestamp>,
    charCount: Int,
    onTimestampClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    Surface(
        modifier = modifier,
        shape = VideoDetailShapes.field(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoNotePanelTab(
                    label = "时间戳",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f),
                )
                VideoNotePanelTab(
                    label = "实时预览",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (selectedTab == 0) {
                VideoNoteTimestampList(
                    document = document,
                    pendingTimestamps = pendingTimestamps,
                    onTimestampClick = onTimestampClick,
                )
            } else {
                VideoNotePreviewContent(
                    richTextState = richTextState,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            AppText(
                text = "$charCount 字 · 支持 B 站官方笔记样式",
                style = MaterialTheme.typography.labelSmall,
                color = AppContentColors.secondaryText(),
            )
        }
    }
}

@Composable
private fun VideoNotePanelTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nativeNavigation = com.android.purebilibili.core.ui.AppInteractionColors.nativeNavigation()
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(VideoDetailShapes.field())
            .background(
                if (selected) nativeNavigation?.selectedContainer ?: MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppText(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) nativeNavigation?.selectedContent ?: MaterialTheme.colorScheme.onPrimaryContainer
            else AppContentColors.secondaryText(),
        )
    }
}

@Composable
private fun VideoNoteTimestampList(
    document: VideoNoteEditorDocument,
    pendingTimestamps: List<VideoNoteBlock.Timestamp>,
    onTimestampClick: (Long) -> Unit,
) {
    val timestamps = document.blocks.filterIsInstance<VideoNoteBlock.Timestamp>() + pendingTimestamps
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        AppText(
            text = "点击跳转到对应进度",
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.secondaryText(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (timestamps.isEmpty()) {
            AppText(
                text = "播放中点工具栏的时钟按钮，可在光标处插入当前时间点。",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.secondaryText(),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                timestamps.forEach { timestamp ->
                    VideoDetailSecondaryButton(
                        onClick = { onTimestampClick(timestamp.seconds * 1000L) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AppText(timestamp.label)
                    }
                }
            }
        }
    }
}

/**
 * 实时预览：BasicRichText 与编辑器共享同一个 RichTextState，
 * 编辑区每次输入预览即时刷新，无需重新解析。
 */
@Composable
internal fun VideoNotePreviewPane(
    richTextState: RichTextState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = VideoDetailShapes.field(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            AppText(
                text = "实时预览",
                style = MaterialTheme.typography.labelMedium,
                color = AppContentColors.secondaryText(),
            )
            Spacer(modifier = Modifier.height(6.dp))
            BasicRichText(state = richTextState)
        }
    }
}

@Composable
internal fun VideoNotePreviewContent(
    richTextState: RichTextState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        BasicRichText(state = richTextState)
    }
}

@Composable
internal fun VideoNoteEditorToolbar(
    isBold: Boolean,
    isItalic: Boolean,
    isUnderline: Boolean,
    isStrikethrough: Boolean,
    isHighlighted: Boolean,
    isBulletList: Boolean,
    previewMode: Boolean = false,
    onPreviewToggle: () -> Unit = {},
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit,
    onStrikethroughClick: () -> Unit,
    onHighlightClick: () -> Unit,
    onBulletClick: () -> Unit,
    onTimestampClick: () -> Unit = {},
    showTimestampButton: Boolean = true,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit
) {
    // 横向滚动 + ≥40dp 触控目标：触控笔精确点选与手指操作都稳定。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        VideoNoteToolbarToggle(
            onClick = onBoldClick,
            active = isBold,
            content = { AppText("B", fontWeight = FontWeight.Bold) }
        )
        VideoNoteToolbarToggle(
            onClick = onItalicClick,
            active = isItalic,
            content = {
                AppText("I", fontStyle = FontStyle.Italic)
            }
        )
        VideoNoteToolbarToggle(
            onClick = onUnderlineClick,
            active = isUnderline,
            content = {
                AppText(
                    "U",
                    textDecoration = TextDecoration.Underline
                )
            }
        )
        VideoNoteToolbarToggle(
            onClick = onStrikethroughClick,
            active = isStrikethrough,
            content = {
                AppText(
                    "S",
                    textDecoration = TextDecoration.LineThrough
                )
            }
        )
        VideoNoteToolbarToggle(
            onClick = onHighlightClick,
            active = isHighlighted,
            content = {
                AppIcon(
                    Icons.Outlined.FormatColorFill,
                    contentDescription = "高亮",
                    tint = if (isHighlighted) AppContentColors.onAccent()
                    else AppContentColors.secondaryText(),
                )
            }
        )
        VideoNoteToolbarToggle(
            onClick = onBulletClick,
            active = isBulletList,
            content = {
                AppIcon(
                    Icons.Outlined.FormatListBulleted,
                    contentDescription = "无序列表",
                    tint = if (isBulletList) AppContentColors.onAccent()
                    else AppContentColors.secondaryText(),
                )
            }
        )
        if (showTimestampButton) {
            VideoNoteToolbarToggle(
                onClick = onTimestampClick,
                active = false,
                content = {
                    AppIcon(Icons.Outlined.AccessTime, contentDescription = "插入时间点")
                }
            )
        }
        VideoNoteToolbarToggle(
            onClick = onPreviewToggle,
            active = previewMode,
            content = {
                AppIcon(
                    if (previewMode) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (previewMode) "返回编辑" else "实时预览"
                )
            }
        )
        VideoNoteToolbarToggle(
            onClick = onUndoClick,
            active = false,
            content = {
                AppIcon(Icons.AutoMirrored.Outlined.Undo, contentDescription = "撤销")
            }
        )
        VideoNoteToolbarToggle(
            onClick = onRedoClick,
            active = false,
            content = {
                AppIcon(Icons.AutoMirrored.Outlined.Redo, contentDescription = "重做")
            }
        )
    }
}

/** 工具栏开关按钮：激活态用主色底标示当前光标处样式（对齐常见移动端编辑器）。 */
@Composable
internal fun VideoNoteToolbarToggle(
    onClick: () -> Unit,
    active: Boolean,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(VideoDetailShapes.field())
            .background(
                if (active) AppContentColors.accent()
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
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

/** 官方等级徽章素材（与 PiliPlus 同源）：硬核会员显示 lv6_s，等级超过 6 按 6 处理。 */
private fun noteLevelBadgeRes(note: VideoNotePublicPreview): Int? {
    return when {
        note.authorSenior -> com.android.purebilibili.R.drawable.lv6_s
        note.authorLevel <= 0 -> null
        note.authorLevel >= 6 -> com.android.purebilibili.R.drawable.lv6
        else -> when (note.authorLevel) {
            1 -> com.android.purebilibili.R.drawable.lv1
            2 -> com.android.purebilibili.R.drawable.lv2
            3 -> com.android.purebilibili.R.drawable.lv3
            4 -> com.android.purebilibili.R.drawable.lv4
            5 -> com.android.purebilibili.R.drawable.lv5
            else -> null
        }
    }
}

@Composable
private fun PublicNoteListRow(
    note: VideoNotePublicPreview,
    onNoteClick: () -> Unit,
    onAuthorClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(VideoDetailShapes.field())
            .clickable(onClick = onNoteClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = note.authorFace.takeIf { it.isNotBlank() },
            contentDescription = note.authorName,
            modifier = Modifier
                .size(34.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .clickable(onClick = onAuthorClick),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = note.authorName.ifBlank { "B站用户" },
                    style = MaterialTheme.typography.labelLarge,
                    color = AppContentColors.secondaryText(),
                    modifier = Modifier.clickable(onClick = onAuthorClick),
                )
                noteLevelBadgeRes(note)?.let { badgeRes ->
                    Spacer(modifier = Modifier.width(6.dp))
                    Image(
                        painter = painterResource(badgeRes),
                        contentDescription = "Lv${note.authorLevel}",
                        modifier = Modifier
                            .height(12.dp)
                            .clickable(onClick = onAuthorClick),
                    )
                }
            }
            if (note.pubtime.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                AppText(
                    text = note.pubtime,
                    style = MaterialTheme.typography.labelSmall,
                    color = AppContentColors.secondaryText(),
                )
            }
            if (note.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                AppText(
                    text = note.summary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            AppText(
                text = "查看全部",
                style = MaterialTheme.typography.bodySmall,
                color = AppContentColors.accent(),
            )
        }
    }
}

@Composable
private fun IconBox() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(VideoDetailShapes.leadingIcon())
            .background(AppContentColors.accent().copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            imageVector = Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            tint = AppContentColors.accent(),
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
    pendingTimestamps: List<VideoNoteBlock.Timestamp>,
    onTimestampClick: (Long) -> Unit
) {
    val timestamps = document.blocks.filterIsInstance<VideoNoteBlock.Timestamp>() + pendingTimestamps
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

/** 与 VideoNoteContentCodec 的高亮色一致（B 站官方笔记背景色），保证所见即所得。 */
internal val VideoNoteHighlightColor = Color(0xFFFFF359)

private const val NOTE_HTML_STYLE = "background-color:#FFF359"

/**
 * 文档 → 编辑器 HTML。走 richeditor 的 HTML 通道而不是 Markdown：
 * Markdown 解析器不支持高亮（`==` 会按字面显示），HTML 通道可完整往返
 * 粗体/斜体/下划线/删除线/高亮/列表。
 */
internal fun documentToHtml(document: VideoNoteEditorDocument): String {
    val builder = StringBuilder()
    document.blocks.forEach { block ->
        when (block) {
            is VideoNoteBlock.Text -> {
                val content = block.text.removeSuffix("\n")
                if (content.isBlank()) return@forEach
                if (block.unorderedList) {
                    builder.append("<ul>")
                    content.split('\n').filter { it.isNotBlank() }.forEach { line ->
                        builder.append("<li>")
                        builder.append(
                            wrapNoteHtmlStyle(
                                text = escapeNoteHtmlText(line),
                                bold = block.bold,
                                italic = block.italic,
                                underline = block.underline,
                                strikethrough = block.strikethrough,
                                highlight = block.highlight,
                            )
                        )
                        builder.append("</li>")
                    }
                    builder.append("</ul>")
                } else {
                    builder.append("<p>")
                    builder.append(
                        wrapNoteHtmlStyle(
                            text = escapeNoteHtmlText(content).replace("\n", "<br>"),
                            bold = block.bold,
                            italic = block.italic,
                            underline = block.underline,
                            strikethrough = block.strikethrough,
                            highlight = block.highlight,
                        )
                    )
                    builder.append("</p>")
                }
            }
            is VideoNoteBlock.Quote -> {
                val content = block.text.removeSuffix("\n")
                if (content.isBlank()) return@forEach
                builder.append("<blockquote>")
                builder.append(escapeNoteHtmlText(content).replace("\n", "<br>"))
                builder.append("</blockquote>")
            }
            is VideoNoteBlock.Timestamp -> builder.append("<p>[${block.label}]</p>")
        }
    }
    return builder.toString().ifEmpty { "<p></p>" }
}

private fun escapeNoteHtmlText(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

private fun wrapNoteHtmlStyle(
    text: String,
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    strikethrough: Boolean,
    highlight: Boolean,
): String {
    var result = text
    if (bold) result = "<b>$result</b>"
    if (italic) result = "<i>$result</i>"
    if (underline) result = "<u>$result</u>"
    if (strikethrough) result = "<s>$result</s>"
    if (highlight) result = "<span style=\"$NOTE_HTML_STYLE\">$result</span>"
    return result
}

/** 编辑器产出的机器 HTML 的一次解析结果（段落级别，粗粒度样式与文档模型对齐）。 */
private data class NoteHtmlParagraph(
    val html: String,
    val unorderedList: Boolean,
    val quote: Boolean = false
)

/**
 * 编辑器 HTML → 文档。toHtml() 的输出是机器生成的扁平结构（p/li/b/i/u/s + background span），
 * 这里按段落提取文本与整段样式；时间戳以 `[label]` 文本标记保留，由 timestamps 按序回填。
 */
internal fun htmlToDocument(
    title: String,
    html: String,
    timestamps: List<VideoNoteBlock.Timestamp>
): VideoNoteEditorDocument {
    val paragraphs = parseNoteHtmlParagraphs(html)
    val runs = paragraphs.mapNotNull { paragraph ->
        val plain = decodeNoteHtmlText(paragraph.html)
        if (plain.isBlank()) return@mapNotNull null
        NoteStyleRun(
            text = plain,
            bold = paragraph.html.contains("<b>") || paragraph.html.contains("<strong>"),
            italic = paragraph.html.contains("<i>") || paragraph.html.contains("<em>"),
            underline = paragraph.html.contains("<u>"),
            strikethrough = paragraph.html.contains("<s>") ||
                paragraph.html.contains("<strike>") ||
                paragraph.html.contains("<del>"),
            highlight = paragraph.html.contains("background", ignoreCase = true),
            unorderedList = paragraph.unorderedList,
            quote = paragraph.quote
        )
    }
    val blocks = mutableListOf<VideoNoteBlock>()
    val remainingRuns = mergeConsecutiveNoteRuns(runs)
    val timestampQueue = ArrayDeque(timestamps.distinctBy { it.label })
    remainingRuns.forEach { run ->
        var segment = run.text
        // 时间戳标记按出现顺序回填为时间戳块。
        while (true) {
            val next = timestampQueue.firstOrNull() ?: break
            val marker = "[${next.label}]"
            val index = segment.indexOf(marker)
            if (index < 0) {
                timestampQueue.removeFirst()
                continue
            }
            segment.take(index).takeIf { it.isNotEmpty() }?.let {
                blocks += run.toTextBlock(it)
            }
            blocks += next
            timestampQueue.removeFirst()
            segment = segment.drop(index + marker.length)
        }
        if (segment.isNotBlank() || blocks.isEmpty()) {
            blocks += run.toTextBlock(segment)
        }
    }
    timestampQueue.forEach { blocks += it }
    return VideoNoteEditorDocument(
        title = title,
        blocks = blocks.ifEmpty { listOf(VideoNoteBlock.Text("")) }
    )
}

private data class NoteStyleRun(
    val text: String,
    val bold: Boolean,
    val italic: Boolean,
    val underline: Boolean,
    val strikethrough: Boolean,
    val highlight: Boolean,
    val unorderedList: Boolean,
    val quote: Boolean = false
) {
    fun toTextBlock(text: String): VideoNoteBlock =
        if (quote) {
            VideoNoteBlock.Quote(text = text)
        } else {
            VideoNoteBlock.Text(
                text = text,
                bold = bold,
                italic = italic,
                underline = underline,
                strikethrough = strikethrough,
                highlight = highlight,
                unorderedList = unorderedList
            )
        }
}

private fun mergeConsecutiveNoteRuns(runs: List<NoteStyleRun>): List<NoteStyleRun> {
    if (runs.isEmpty()) return runs
    val merged = mutableListOf<NoteStyleRun>()
    runs.forEach { run ->
        val last = merged.lastOrNull()
        if (last != null && last.bold == run.bold && last.italic == run.italic &&
            last.underline == run.underline && last.strikethrough == run.strikethrough &&
            last.highlight == run.highlight && last.unorderedList == run.unorderedList &&
            last.quote == run.quote
        ) {
            merged[merged.lastIndex] = last.copy(
                text = last.text + "\n" + run.text
            )
        } else {
            merged += run
        }
    }
    return merged
}

/** 解析 toHtml() 输出为有序段落（p/li），兼容 ul/ol 与 h1-h6 块。 */
private fun parseNoteHtmlParagraphs(html: String): List<NoteHtmlParagraph> {
    val paragraphs = mutableListOf<NoteHtmlParagraph>()
    val blockRegex = Regex(
        pattern = "<(p|li|h[1-6]|blockquote)[^>]*>(.*?)</\\1>",
        options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    blockRegex.findAll(html).forEach { match ->
        val tagName = match.groupValues[1].lowercase()
        paragraphs += NoteHtmlParagraph(
            html = match.groupValues[2],
            unorderedList = tagName == "li",
            quote = tagName == "blockquote"
        )
    }
    if (paragraphs.isEmpty() && html.isNotBlank()) {
        // 兜底：没有块级标签时把整体当纯文本。
        paragraphs += NoteHtmlParagraph(html = html, unorderedList = false)
    }
    return paragraphs
}

private fun decodeNoteHtmlText(html: String): String {
    return html
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .trim('\n', ' ')
}
