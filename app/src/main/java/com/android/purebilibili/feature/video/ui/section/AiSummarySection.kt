package com.android.purebilibili.feature.video.ui.section
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppContentCard
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.data.model.response.AiSummaryData
import com.android.purebilibili.feature.video.ui.VideoDetailShapes
import com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptState
import com.android.purebilibili.feature.video.viewmodel.AiSummaryPromptTone
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.Text as MiuixText

/**
 * AI Video Summary Card — adaptive native card (M3 Card / Miuix Card).
 */
@Composable
fun AiSummaryCard(
    aiSummary: AiSummaryData?,
    onTimestampClick: ((Long) -> Unit)? = null,
    onCreateNoteDraftClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (!hasAiSummaryContent(aiSummary)) return

    val modelResult = requireNotNull(aiSummary?.modelResult)
    val collapsedPreview = remember(modelResult.summary, modelResult.outline) {
        modelResult.summary.takeIf { it.isNotBlank() } ?: "查看分段总结和时间点"
    }
    var expanded by remember { mutableStateOf(false) }
    val useMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow

    AppContentCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            // The Miuix expansion owns its height animation; do not animate it twice.
            .then(if (useMiuix) Modifier else Modifier.animateContentSize()),
        containerColor = containerColor,
    ) {
        if (useMiuix) {
            MiuixAiSummaryHeader(
                preview = collapsedPreview,
                expanded = expanded,
                onToggle = { expanded = !expanded }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LeadingIconBadge(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    AppIcon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = "AI 总结",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AppText(
                        text = collapsedPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                AppIcon(
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = if (useMiuix) {
                expandVertically(
                    animationSpec = folmeSpring(damping = 1f, response = 0.35f),
                    expandFrom = Alignment.Top
                ) + fadeIn(animationSpec = folmeSpring(damping = 1f, response = 0.25f))
            } else {
                fadeIn() + expandVertically()
            },
            exit = if (useMiuix) {
                shrinkVertically(
                    animationSpec = folmeSpring(damping = 1f, response = 0.35f),
                    shrinkTowards = Alignment.Top
                ) + fadeOut(animationSpec = folmeSpring(damping = 1f, response = 0.25f))
            } else {
                fadeOut() + shrinkVertically()
            }
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                AppHorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                if (modelResult.summary.isNotBlank()) {
                    AppText(
                        text = modelResult.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(
                            bottom = if (modelResult.outline.isNotEmpty()) 12.dp else 0.dp
                        ),
                    )
                }

                if (modelResult.outline.isNotEmpty()) {
                    modelResult.outline.forEach { outlineItem ->
                        OutlineItemRow(
                            title = outlineItem.title,
                            timestamp = outlineItem.timestamp,
                            onClick = { onTimestampClick?.invoke(outlineItem.timestamp * 1000L) },
                        )
                        outlineItem.partOutline.forEach { part ->
                            OutlineItemRow(
                                title = part.content,
                                timestamp = part.timestamp,
                                isSubItem = true,
                                onClick = { onTimestampClick?.invoke(part.timestamp * 1000L) },
                            )
                        }
                    }
                }

                if (onCreateNoteDraftClick != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    VideoDetailSecondaryButton(
                        onClick = onCreateNoteDraftClick,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        AppIcon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        AppText("生成笔记草稿")
                    }
                }
            }
        }
    }
}

@Composable
private fun MiuixAiSummaryHeader(
    preview: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arrowRotation = animateFloatAsState(
        targetValue = if (expanded) 270f else 90f,
        animationSpec = folmeSpring(damping = 1f, response = 0.35f),
        label = "ai-summary-expand-arrow"
    )
    // BasicComponent supplies Miuix's native row layout, touch target and indication.
    // Body content stays outside its clickable area so timestamps do not collapse it.
    BasicComponent(
        modifier = modifier.semantics { stateDescription = if (expanded) "已展开" else "已收起" },
        onClick = onToggle,
        onClickLabel = if (expanded) "收起 AI 总结" else "展开 AI 总结",
        role = Role.Button,
        startAction = {
            LeadingIconBadge(containerColor = AppSurfaceTokens.primary().copy(alpha = 0.12f)) {
                AppIcon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = AppSurfaceTokens.primary(),
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        endActions = {
            AppIcon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = AppSurfaceTokens.onSurfaceVariantActions(),
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = arrowRotation.value }
            )
        }
    ) {
        MiuixText(
            text = "AI 总结",
            style = MiuixTheme.textStyles.headline1,
            fontWeight = FontWeight.Medium,
            color = AppSurfaceTokens.onSurface()
        )
        MiuixText(
            text = preview,
            style = MiuixTheme.textStyles.body2,
            color = AppSurfaceTokens.onSurfaceVariantSummary(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun AiSummaryPromptCard(
    promptState: AiSummaryPromptState,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val containerColor = when (promptState.tone) {
        AiSummaryPromptTone.INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        AiSummaryPromptTone.MUTED -> MaterialTheme.colorScheme.surfaceContainerLow
        AiSummaryPromptTone.WARNING -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    }
    val accentColor = when (promptState.tone) {
        AiSummaryPromptTone.INFO -> MaterialTheme.colorScheme.primary
        AiSummaryPromptTone.MUTED -> MaterialTheme.colorScheme.onSurfaceVariant
        AiSummaryPromptTone.WARNING -> MaterialTheme.colorScheme.error
    }

    AppContentCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        containerColor = containerColor,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LeadingIconBadge(containerColor = accentColor.copy(alpha = 0.12f)) {
                if (promptState.tone == AiSummaryPromptTone.INFO) {
                    AdaptiveLoadingIndicator(
                        size = 16.dp,
                        strokeWidth = 2.dp,
                        color = accentColor,
                    )
                } else {
                    AppIcon(
                        imageVector = if (promptState.tone == AiSummaryPromptTone.WARNING) {
                            Icons.Outlined.ErrorOutline
                        } else {
                            Icons.Outlined.Info
                        },
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                AppText(
                    text = promptState.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                AppText(
                    text = promptState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (!promptState.actionLabel.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.height(8.dp))
            AppTextButton(
                onClick = onActionClick,
                modifier = Modifier.align(Alignment.End),
            ) {
                AppText(promptState.actionLabel)
            }
        }
    }
}

@Composable
private fun LeadingIconBadge(
    containerColor: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(VideoDetailShapes.leadingIcon())
            .background(containerColor),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** 主条目圆点 + 间距；子条目与标题列左缘对齐。 */
internal val OutlineBulletSlotWidth = 18.dp
/**
 * 时间戳列固定宽。芯片必须 [fillMaxWidth]，否则比例数字会让时钟图标左右参差
 * （右缘对齐时左缘仍不齐）。
 */
internal val OutlineTimestampColumnWidth = 76.dp

@Composable
private fun OutlineItemRow(
    title: String,
    timestamp: Long,
    isSubItem: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // 固定前导槽：主条目标圆点，子条目留白，标题列左缘一致。
        Box(
            modifier = Modifier
                .width(OutlineBulletSlotWidth)
                .padding(top = 6.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (!isSubItem) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }

        AppText(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
        )

        // 固定列宽 + 满宽芯片 + 表内居中，时钟图标与 mm:ss 形成垂直列。
        Box(
            modifier = Modifier
                .width(OutlineTimestampColumnWidth)
                .padding(top = 2.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            AppSurface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = AppShapes.container(ContainerLevel.Tag),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    AppIcon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    AppText(
                        text = formatAiSummaryTimestamp(timestamp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** mm:ss，秒级时间点；使用等宽数字特性减少视觉漂移。 */
internal fun formatAiSummaryTimestamp(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val m = safe / 60
    val s = safe % 60
    return "%02d:%02d".format(m, s)
}
