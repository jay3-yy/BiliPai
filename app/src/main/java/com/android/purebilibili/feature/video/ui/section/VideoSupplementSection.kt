@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.feature.video.note.VideoNoteEditorDocument
import com.android.purebilibili.feature.video.note.VideoNoteUiState
import com.android.purebilibili.feature.video.ui.components.EpisodeSelectionSheet
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
                Modifier
                    .size(width = 26.dp, height = 20.dp)
                    .clickable(onClickLabel = "AI 总结", onClick = onAiSummaryClick),
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
    // 统计区右侧入口与首行统计文字垂直居中对齐，图标保持 18dp。
    Box(
        modifier = Modifier
            .size(width = 26.dp, height = 20.dp)
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

/**
 * AI 总结面板：竖屏播放器下方以内联面板展开（不遮挡视频），其余场景以底部弹窗展示。
 */
@Composable
fun AiSummarySheet(
    visible: Boolean,
    aiSummary: AiSummaryData?,
    promptState: AiSummaryPromptState?,
    onDismiss: () -> Unit,
    onTimestampClick: ((Long) -> Unit)?,
    onRetry: () -> Unit,
    onCreateNoteDraft: () -> Unit,
    inlinePanel: Boolean = false,
) {
    if (!visible) return
    EpisodeSelectionSheet(
        onDismiss = onDismiss,
        inlinePanel = inlinePanel,
    ) { requestDismiss ->
        com.android.purebilibili.feature.video.screen.VideoUiWorkConsumerEffect(
            com.android.purebilibili.feature.video.playback.policy.VideoUiWorkKind.AI_SUMMARY,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (inlinePanel) Modifier.fillMaxSize() else Modifier),
        ) {
            if (inlinePanel) {
                AiSummaryInlineDragHandle(onDismiss = requestDismiss)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (inlinePanel) Modifier.weight(1f) else Modifier)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
            ) {
                if (hasAiSummaryContent(aiSummary)) {
                    AiSummaryPanelBody(
                        aiSummary = requireNotNull(aiSummary),
                        onTimestampClick = onTimestampClick,
                        onCreateNoteDraftClick = {
                            requestDismiss()
                            onCreateNoteDraft()
                        },
                    )
                } else {
                    AiSummaryPromptCard(
                        promptState = promptState
                            ?: com.android.purebilibili.feature.video.viewmodel.initialAiSummaryPromptState(),
                        onActionClick = onRetry,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiSummaryInlineDragHandle(
    onDismiss: () -> Unit,
) {
    val dismissThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    var dragDistancePx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(35.dp)
            .clickable(onClickLabel = "关闭 AI 总结", onClick = onDismiss)
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    dragDistancePx = (dragDistancePx + delta).coerceAtLeast(0f)
                },
                onDragStopped = { velocity ->
                    if (dragDistancePx >= dismissThresholdPx || velocity > 800f) {
                        onDismiss()
                    }
                    dragDistancePx = 0f
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AppContentColors.accent()),
        )
    }
}

@Composable
internal fun LandscapeAiSummaryDrawerContent(
    aiSummary: AiSummaryData?,
    aiSummaryPrompt: AiSummaryPromptState?,
    onTimestampClick: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val hasSummary = hasAiSummaryContent(aiSummary)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
    ) {
        if (hasSummary && aiSummary != null) {
            AiSummaryPanelBody(
                aiSummary = aiSummary,
                onTimestampClick = onTimestampClick,
                onCreateNoteDraftClick = {},
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppText(
                    text = aiSummaryPrompt?.title ?: "当前视频暂无 AI 总结",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = AppContentColors.primaryText(),
                )
                aiSummaryPrompt?.message?.takeIf { it.isNotBlank() }?.let { detail ->
                    AppText(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AiSummaryPanelBody(
    aiSummary: AiSummaryData,
    onTimestampClick: ((Long) -> Unit)?,
    onCreateNoteDraftClick: () -> Unit,
) {
    val modelResult = aiSummary.modelResult ?: return
    val summary = modelResult.summary.trim()
    val outline = modelResult.outline
    val accentColor = AppContentColors.accent()
    val primaryTextColor = AppContentColors.primaryText()

    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (summary.isNotEmpty()) {
                AppText(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.5.sp,
                    ),
                    color = primaryTextColor,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                if (outline.isNotEmpty()) {
                    HorizontalDivider(
                        thickness = 6.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                        modifier = Modifier.padding(vertical = 7.dp),
                    )
                }
            }

            if (outline.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                ) {
                    outline.forEachIndexed { index, outlineItem ->
                        if (index != 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (outlineItem.title.isNotBlank()) {
                            AppText(
                                text = outlineItem.title,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 21.sp,
                                ),
                                color = primaryTextColor,
                            )
                        }
                        if (outlineItem.partOutline.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            outlineItem.partOutline.forEach { part ->
                                val timestampText = formatAiSummaryTimestamp(part.timestamp)
                                val annotated = buildAnnotatedString {
                                    if (onTimestampClick != null) {
                                        withLink(
                                            LinkAnnotation.Clickable(
                                                tag = "ai_summary_ts_${part.timestamp}",
                                                styles = TextLinkStyles(
                                                    style = SpanStyle(
                                                        color = accentColor,
                                                        fontFeatureSettings = "tnum",
                                                    ),
                                                ),
                                                linkInteractionListener = {
                                                    onTimestampClick(part.timestamp * 1000L)
                                                },
                                            ),
                                        ) {
                                            append(timestampText)
                                        }
                                    } else {
                                        append(timestampText)
                                    }
                                    append(' ')
                                    append(part.content)
                                }
                                AppText(
                                    text = annotated,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 14.sp,
                                        lineHeight = 21.sp,
                                    ),
                                    color = primaryTextColor,
                                    modifier = Modifier.padding(bottom = 2.dp),
                                )
                            }
                        } else if (outlineItem.timestamp >= 0L && onTimestampClick != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val timestampText = formatAiSummaryTimestamp(outlineItem.timestamp)
                            val annotated = buildAnnotatedString {
                                withLink(
                                    LinkAnnotation.Clickable(
                                        tag = "ai_summary_section_ts_${outlineItem.timestamp}",
                                        styles = TextLinkStyles(
                                            style = SpanStyle(
                                                color = accentColor,
                                                fontFeatureSettings = "tnum",
                                            ),
                                        ),
                                        linkInteractionListener = {
                                            onTimestampClick(outlineItem.timestamp * 1000L)
                                        },
                                    ),
                                ) {
                                    append(timestampText)
                                }
                            }
                            AppText(
                                text = annotated,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                ),
                                color = accentColor,
                            )
                        }
                    }
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        VideoDetailSecondaryButton(onClick = onCreateNoteDraftClick) {
            com.android.purebilibili.core.ui.ClientAiSummaryIcon(Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            AppText("生成笔记草稿")
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
