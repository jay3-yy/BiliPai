package com.android.purebilibili.feature.download

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.crossfade
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.components.appActionWithFeedback
import com.android.purebilibili.core.ui.rememberAppMoreIcon
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoCardFrame

/** 缓存卡片右下角「回到原视频页面」入口。 */
@Composable
internal fun OriginalVideoPageButton(
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = if (compact) 28.dp else 30.dp)
            .clip(AppShapes.container(ContainerLevel.Pill))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f))
            .appActionClickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 8.dp else 10.dp,
                vertical = if (compact) 4.dp else 5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AppIcon(
            imageVector = Icons.Outlined.OpenInNew,
            contentDescription = null,
            tint = AppContentColors.secondaryText(),
            modifier = Modifier.size(if (compact) 13.dp else 14.dp),
        )
        AppText(
            text = "原视频",
            style = MaterialTheme.typography.labelSmall,
            color = AppContentColors.primaryText(),
            maxLines = 1,
            softWrap = false,
        )
    }
}

internal fun resolveMultiPartRowTitle(task: DownloadTask, fallbackIndex: Int): String {
    val label = task.episodeLabel?.trim().orEmpty()
    if (label.isNotEmpty()) return label
    val sortIdx = resolveOfflineEpisodeSortIndex(task)
    val prefix = if (sortIdx in 1 until Int.MAX_VALUE) "P$sortIdx" else "P${fallbackIndex + 1}"
    return "$prefix · ${task.title}"
}

@Composable
internal fun DownloadMultiPartGroupItem(
    group: DownloadListEntry.MultiPartGroup,
    expanded: Boolean,
    stacked: Boolean,
    selectionMode: Boolean,
    selectedTaskIds: Set<String>,
    completedTaskIds: Set<String>,
    refreshingDanmakuTaskIds: Set<String>,
    downloadSpeedsByTask: Map<String, Long>,
    onToggleExpand: () -> Unit,
    onToggleGroupSelection: () -> Unit,
    onOpenOriginalVideo: () -> Unit,
    onRefreshGroupDanmaku: () -> Unit,
    onDeleteGroup: () -> Unit,
    onPartClick: (DownloadTask) -> Unit,
    onPartPauseResume: (DownloadTask) -> Unit,
    onPartRefreshDanmaku: (DownloadTask) -> Unit,
    onPartDelete: (DownloadTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = group.primaryTask
    val completedParts = group.tasks.filter { it.isComplete }
    val groupCompletedIds = completedParts.map { it.id }.filter { it in completedTaskIds }
    val allGroupSelected = groupCompletedIds.isNotEmpty() && groupCompletedIds.all { it in selectedTaskIds }
    val selectionEnabled = groupCompletedIds.isNotEmpty()
    val totalSizeBytes = group.tasks.sumOf { resolveDownloadTaskSortSizeBytes(it) }
    val isRefreshingAny = group.tasks.any { it.id in refreshingDanmakuTaskIds }
    val hasActiveDownload = group.tasks.any { it.isDownloading }

    val cardShape = AppShapes.container(ContainerLevel.Card)
    val cardSurface = if (stacked || expanded || allGroupSelected || hasActiveDownload) {
        AppSurfaceTokens.cardContainer()
    } else {
        Color.Transparent
    }
    val typography = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography()
    val feedbackToggle = appActionWithFeedback(onToggleGroupSelection)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(cardSurface)
            .padding(
                horizontal = if (!stacked && !expanded && !allGroupSelected && !hasActiveDownload) 0.dp else AppSpacingTokens.Medium,
                vertical = if (!stacked && !expanded && !allGroupSelected && !hasActiveDownload) AppSpacingTokens.ExtraSmall else AppSpacingTokens.Medium,
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (selectionMode) Modifier.toggleable(
                        value = allGroupSelected,
                        enabled = selectionEnabled,
                        role = Role.Checkbox,
                        onValueChange = { feedbackToggle() },
                    ) else Modifier.appActionClickable(onClick = onToggleExpand)
                ),
            verticalArrangement = Arrangement.spacedBy(AppSpacingTokens.ExtraSmall),
        ) {
            HorizontalVideoCardFrame(
                stacked = stacked,
                plainHorizontal = true,
                horizontalCoverMaxWidthDp = com.android.purebilibili.feature.home.components.cards.WIDE_LIST_COVER_MAX_WIDTH_DP,
                coverContent = {
                    val localCoverFile = primary.localCoverPath?.let { java.io.File(it) }
                    val coverSource = if (localCoverFile?.exists() == true) {
                        localCoverFile
                    } else {
                        val coverUrl = primary.cover.let { url ->
                            if (url.startsWith("http://")) url.replace("http://", "https://") else url
                        }
                        coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com").build())
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = coverSource,
                        contentDescription = group.groupTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                coverOverlayContent = {
                    if (hasActiveDownload) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val avgProgress = group.tasks.map { resolveDownloadTaskProgress(it) }.average().toFloat()
                            AppCircularProgressIndicator(
                                progress = { avgProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.size(32.dp),
                                color = Color.White,
                                strokeWidth = 3.dp,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(AppSpacingTokens.ExtraSmall)
                            .background(
                                Color.Black.copy(alpha = 0.72f),
                                AppShapes.container(ContainerLevel.Tag),
                            )
                            .padding(horizontal = AppSpacingTokens.ExtraSmall, vertical = AppSpacingTokens.Micro),
                    ) {
                        val badgeText = if (completedParts.size == group.tasks.size) {
                            "共 ${group.tasks.size} P"
                        } else {
                            "${completedParts.size}/${group.tasks.size} P"
                        }
                        AppText(text = badgeText, color = Color.White, style = typography.statistic)
                    }
                },
                infoContent = {
                    AppText(
                        text = group.groupTitle,
                        style = typography.title,
                        maxLines = com.android.purebilibili.core.ui.videoCardTitleMaxLines(),
                        overflow = com.android.purebilibili.core.ui.videoCardTitleOverflow(),
                        color = AppContentColors.primaryText(),
                    )
                },
                supportingContent = {
                    AppText(
                        text = "分P合集 · ${group.tasks.size} 个分P",
                        style = typography.author,
                        color = AppContentColors.accent(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = primary.ownerName,
                        style = typography.author,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )

            val summary = buildString {
                if (completedParts.size == group.tasks.size) {
                    append("已缓存 ${group.tasks.size} P")
                } else {
                    append("已缓存 ${completedParts.size}/${group.tasks.size} P")
                }
                if (totalSizeBytes > 0L) {
                    append(" · ")
                    append(formatDownloadStorageBytes(totalSizeBytes))
                }
            }
            val groupActionMenu: @Composable () -> Unit = {
                AppWindowActionMenu(
                    anchorModifier = Modifier.size(if (stacked) 34.dp else 36.dp),
                    groups = listOf(
                        buildList {
                            if (completedParts.isNotEmpty()) {
                                add(
                                    AppWindowAction(
                                        label = if (isRefreshingAny) "正在更新弹幕…" else "更新全部弹幕",
                                        icon = Icons.Outlined.Refresh,
                                        enabled = !isRefreshingAny,
                                        onClick = onRefreshGroupDanmaku,
                                    )
                                )
                            }
                            add(
                                AppWindowAction(
                                    label = "删除全部分P",
                                    icon = Icons.Outlined.Delete,
                                    iconTint = AppContentColors.error(),
                                    onClick = onDeleteGroup,
                                )
                            )
                        }
                    ),
                ) {
                    AppIcon(rememberAppMoreIcon(), contentDescription = "分P合集操作")
                }
            }
            if (stacked) {
                // 多列窄卡将摘要与操作拆成两行，避免缓存大小被挤压成竖排换行。
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AppText(
                        text = summary,
                        style = typography.statistic,
                        color = AppContentColors.secondaryText(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isRefreshingAny) {
                        AppText(
                            text = "正在更新弹幕…",
                            style = typography.statistic,
                            color = AppContentColors.accent(),
                            maxLines = 1,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectionMode) {
                            AppCheckbox(
                                checked = allGroupSelected,
                                onCheckedChange = null,
                                enabled = selectionEnabled,
                                modifier = Modifier.size(36.dp),
                            )
                        } else {
                            if (group.bvid.isNotBlank()) {
                                OriginalVideoPageButton(onClick = onOpenOriginalVideo, compact = true)
                            }
                            groupActionMenu()
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        AppText(
                            text = summary,
                            style = typography.statistic,
                            color = AppContentColors.secondaryText(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isRefreshingAny) {
                            AppText(
                                text = "正在更新弹幕…",
                                style = typography.statistic,
                                color = AppContentColors.accent(),
                                maxLines = 1,
                            )
                        }
                    }
                    if (selectionMode) {
                        AppCheckbox(
                            checked = allGroupSelected,
                            onCheckedChange = null,
                            enabled = selectionEnabled,
                            modifier = Modifier.size(48.dp),
                        )
                    } else {
                        if (group.bvid.isNotBlank()) {
                            OriginalVideoPageButton(onClick = onOpenOriginalVideo)
                        }
                        AppIconButton(onClick = onToggleExpand, modifier = Modifier.size(36.dp)) {
                            AppIcon(
                                imageVector = if (expanded) {
                                    Icons.Outlined.KeyboardArrowUp
                                } else {
                                    Icons.Outlined.KeyboardArrowDown
                                },
                                contentDescription = if (expanded) "收起分P" else "展开分P",
                                tint = AppContentColors.secondaryText(),
                            )
                        }
                        groupActionMenu()
                    }
                }
            }
        }

        if (!stacked && !selectionMode) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacingTokens.ExtraSmall),
                ) {
                    AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    group.tasks.forEachIndexed { idx, part ->
                        DownloadMultiPartChildRow(
                            task = part,
                            index = idx,
                            isRefreshingDanmaku = part.id in refreshingDanmakuTaskIds,
                            speedBytesPerSecond = if (part.status == DownloadStatus.DOWNLOADING) {
                                downloadSpeedsByTask[part.id] ?: 0L
                            } else 0L,
                            onClick = { onPartClick(part) },
                            onPauseResume = { onPartPauseResume(part) },
                            onRefreshDanmaku = { onPartRefreshDanmaku(part) },
                            onDelete = { onPartDelete(part) },
                        )
                        if (idx < group.tasks.lastIndex) {
                            AppHorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DownloadMultiPartChildRow(
    task: DownloadTask,
    index: Int,
    isRefreshingDanmaku: Boolean,
    speedBytesPerSecond: Long,
    onClick: () -> Unit,
    onPauseResume: () -> Unit,
    onRefreshDanmaku: () -> Unit,
    onDelete: () -> Unit,
) {
    val rowTitle = resolveMultiPartRowTitle(task, index)
    val subtitle = when {
        isRefreshingDanmaku -> "正在更新弹幕…"
        task.isComplete -> buildString {
            append(task.qualityDesc)
            if (task.fileSize > 0L) {
                append(" · ")
                append(formatDownloadStorageBytes(task.fileSize))
            }
            if (task.assetState(DownloadAssetKind.DANMAKU)?.status == DownloadAssetStatus.FAILED) {
                append(" · 弹幕失败")
            }
        }
        task.status == DownloadStatus.DOWNLOADING -> buildString {
            append("下载中 ${resolveDownloadTaskProgressPercent(task)}%")
            if (speedBytesPerSecond > 0L) {
                append(" · ")
                append(formatDownloadStorageBytes(speedBytesPerSecond))
                append("/s")
            }
        }
        task.status == DownloadStatus.PAUSED -> "已暂停"
        task.status == DownloadStatus.FAILED -> task.errorMessage ?: "下载失败"
        task.status == DownloadStatus.MERGING -> "处理中..."
        else -> "排队中..."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .appActionClickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            AppText(
                text = rowTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = AppContentColors.primaryText(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            AppText(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isRefreshingDanmaku || task.isDownloading -> AppContentColors.accent()
                    task.isFailed -> AppContentColors.error()
                    else -> AppContentColors.secondaryText()
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (task.isComplete) {
            AppWindowActionMenu(
                anchorModifier = Modifier.size(36.dp),
                groups = listOf(
                    listOf(
                        AppWindowAction(
                            label = if (isRefreshingDanmaku) "正在更新弹幕…" else "更新弹幕",
                            icon = Icons.Outlined.Refresh,
                            enabled = !isRefreshingDanmaku,
                            onClick = onRefreshDanmaku,
                        ),
                        AppWindowAction(
                            label = "删除缓存",
                            icon = Icons.Outlined.Delete,
                            iconTint = AppContentColors.error(),
                            onClick = onDelete,
                        ),
                    )
                ),
            ) {
                AppIcon(rememberAppMoreIcon(), contentDescription = "分P操作")
            }
        } else {
            val canPause = task.isDownloading || task.isWaitingForRetry
            if (canPause || task.canResume) {
                AppIconButton(onClick = onPauseResume, modifier = Modifier.size(36.dp)) {
                    AppIcon(
                        imageVector = if (canPause) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (canPause) "暂停" else "继续",
                        tint = AppContentColors.accent(),
                    )
                }
            }
            AppIconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                AppIcon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = AppContentColors.error(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadMultiPartGroupSheet(
    group: DownloadListEntry.MultiPartGroup,
    refreshingDanmakuTaskIds: Set<String>,
    downloadSpeedsByTask: Map<String, Long>,
    onDismiss: () -> Unit,
    onOpenOriginalVideo: () -> Unit,
    onRefreshGroupDanmaku: () -> Unit,
    onPartClick: (DownloadTask) -> Unit,
    onPartPauseResume: (DownloadTask) -> Unit,
    onPartRefreshDanmaku: (DownloadTask) -> Unit,
    onPartDelete: (DownloadTask) -> Unit,
) {
    val isRefreshingAny = group.tasks.any { it.id in refreshingDanmakuTaskIds }
    val hasCompleted = group.tasks.any { it.isComplete }
    AppModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AppText(
                        text = group.groupTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AppContentColors.primaryText(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AppText(
                        text = "共 ${group.tasks.size} 个分P",
                        style = MaterialTheme.typography.labelSmall,
                        color = AppContentColors.secondaryText(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                if (group.bvid.isNotBlank()) {
                    OriginalVideoPageButton(
                        onClick = {
                            onDismiss()
                            onOpenOriginalVideo()
                        }
                    )
                }
                if (hasCompleted) {
                    AppIconButton(
                        enabled = !isRefreshingAny,
                        onClick = onRefreshGroupDanmaku,
                        modifier = Modifier.size(40.dp),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "更新全部弹幕",
                            tint = AppContentColors.accent(),
                        )
                    }
                }
            }
            AppHorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                itemsIndexed(
                    items = group.tasks,
                    key = { _, part -> part.id },
                    contentType = { _, part -> if (part.isComplete) "part_complete" else "part_active" },
                ) { idx, part ->
                    DownloadMultiPartChildRow(
                        task = part,
                        index = idx,
                        isRefreshingDanmaku = part.id in refreshingDanmakuTaskIds,
                        speedBytesPerSecond = if (part.status == DownloadStatus.DOWNLOADING) {
                            downloadSpeedsByTask[part.id] ?: 0L
                        } else 0L,
                        onClick = {
                            onDismiss()
                            onPartClick(part)
                        },
                        onPauseResume = { onPartPauseResume(part) },
                        onRefreshDanmaku = { onPartRefreshDanmaku(part) },
                        onDelete = { onPartDelete(part) },
                    )
                    if (idx < group.tasks.lastIndex) {
                        AppHorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
