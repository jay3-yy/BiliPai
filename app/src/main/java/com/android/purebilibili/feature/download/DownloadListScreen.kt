package com.android.purebilibili.feature.download

import com.android.purebilibili.core.util.responsiveContentWidth

import com.android.purebilibili.core.ui.components.AppTextButton as TextButton

import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.util.HapticType

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.appElasticPress


import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.android.purebilibili.core.ui.components.AppCheckbox
import com.android.purebilibili.core.ui.components.AppTextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

import com.android.purebilibili.core.ui.components.VideoListLayoutToggle
import com.android.purebilibili.core.ui.components.resolveVideoListColumns
import com.android.purebilibili.core.ui.components.rememberVideoListLayoutControl
import com.android.purebilibili.core.ui.components.videoListItemModifier
import com.android.purebilibili.core.ui.components.AnimatedVideoListItem
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppSpacingTokens

import androidx.compose.foundation.background
import com.android.purebilibili.core.ui.components.appActionClickable
import com.android.purebilibili.core.ui.components.appActionWithFeedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import com.android.purebilibili.core.ui.components.AppLazyVerticalGrid as LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
//  Material Icons
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.ImmersiveAppScaffold as AppScaffold
import com.android.purebilibili.core.ui.AppTopBar
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.feature.home.components.cards.HorizontalVideoCardFrame
import com.android.purebilibili.core.ui.components.AppCardDefaults
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.AppCircularProgressIndicator
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 *  离线缓存列表页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadListScreen(
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit,  // bvid - 在线播放
    onOfflineVideoClick: (String) -> Unit = {}  // 🔧 [新增] taskId - 离线播放
) {
    val context = LocalContext.current
    val actionScope = rememberCoroutineScope()
    val deleteDialogExitDelayMs = if (com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled) {
        com.android.purebilibili.core.ui.AppGlassPopupMotionSpec.UnanchoredExitMillis.toLong()
    } else 0L
    var deletingTaskId by remember { mutableStateOf<String?>(null) }
    val downloadHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.REFRESH)
    val listLayout = rememberVideoListLayoutControl(defaultSingleColumn = true)
    val gridState = rememberLazyGridState()
    val columns = resolveVideoListColumns(
        listLayout.singleColumn,
        androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat(),
    )
    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    val downloadsInitialized by DownloadManager.isInitialized.collectAsStateWithLifecycle()
    var isNetworkAvailable by remember(context) { mutableStateOf(NetworkUtils.isNetworkAvailable(context)) }
    val customDownloadPath by SettingsManager.getDownloadPath(context).collectAsStateWithLifecycle(initialValue = null)
    val downloadExportTreeUri by SettingsManager.getDownloadExportTreeUri(context).collectAsStateWithLifecycle(initialValue = null)
    val taskList = remember(tasks) { tasks.values.toList().sortedByDescending { it.createdAt } }
    var pendingDeleteTask by remember { mutableStateOf<com.android.purebilibili.feature.download.DownloadTask?>(null) }
    var dissolvingTaskIds by remember { mutableStateOf(setOf<String>()) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedTaskIds by remember { mutableStateOf(emptySet<String>()) }
    var pendingBatchDeleteIds by remember { mutableStateOf(emptySet<String>()) }
    var deletingBatch by remember { mutableStateOf(false) }
    val completedTaskIds = remember(taskList, dissolvingTaskIds) {
        taskList.filter { it.isComplete && it.id !in dissolvingTaskIds }.map { it.id }.toSet()
    }
    val selectedCompletedIds = selectedTaskIds.intersect(completedTaskIds)
    val exitSelection = {
        selectionMode = false
        selectedTaskIds = emptySet<String>()
        pendingBatchDeleteIds = emptySet<String>()
    }
    BackHandler(enabled = selectionMode) {
        if (!deletingBatch) exitSelection()
    }
    LaunchedEffect(completedTaskIds) {
        selectedTaskIds = selectedTaskIds.intersect(completedTaskIds)
        if (completedTaskIds.isEmpty() && !deletingBatch) exitSelection()
    }
    val currentDir = resolveDisplayedDownloadLocation(
        defaultManagedPath = remember(context) { SettingsManager.getDefaultDownloadPath(context) },
        customManagedPath = customDownloadPath,
        exportTreeUri = downloadExportTreeUri
    )

    LaunchedEffect(context) {
        while (true) {
            isNetworkAvailable = NetworkUtils.isNetworkAvailable(context)
            delay(1_500L)
        }
    }

    // Keep one sampling loop alive across progress emissions and reset each task's baseline independently.
    var downloadSpeedsByTask by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    val latestTasks by rememberUpdatedState(tasks)
    LaunchedEffect(Unit) {
        var previousSamples = emptyMap<String, Long>()
        while (true) {
            delay(1_000L)
            val samples = latestTasks.values
                .filter { it.status == DownloadStatus.DOWNLOADING }
                .associate { task ->
                    task.id to task.assets.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
                }
            downloadSpeedsByTask = samples.mapValues { (taskId, totalBytes) ->
                previousSamples[taskId]?.let { previous ->
                    (totalBytes - previous).coerceAtLeast(0L)
                } ?: 0L
            }
            previousSamples = samples
        }
    }

    AppScaffold(
        topBar = {
            AppTopBar(
                title = if (selectionMode) "已选 ${selectedCompletedIds.size} 项" else "离线缓存",
                navigationIcon = {
                    AppIconButton(onClick = {
                        if (selectionMode) {
                            if (!deletingBatch) exitSelection()
                        } else onBack()
                    }) {
                        AppIcon(rememberAppBackIcon(), contentDescription = "返回")
                    }
                },
                actions = {
                    val hasActive = taskList.any(::shouldPauseAllInclude)
                    val hasResumable = taskList.any(::shouldContinueAllInclude)
                    if (selectionMode) {
                        AppTextButton(onClick = exitSelection, enabled = !deletingBatch) {
                            AppText("完成")
                        }
                    } else if (completedTaskIds.isNotEmpty()) {
                        AppTextButton(onClick = { selectionMode = true }) {
                            AppText("管理")
                        }
                    }
                    if (!selectionMode && (hasActive || hasResumable)) {
                        TextButton(onClick = {
                            actionScope.launch {
                                if (hasActive) {
                                    taskList.filter(::shouldPauseAllInclude).forEach {
                                        DownloadManager.pauseDownload(it.id)
                                    }
                                } else {
                                    taskList.filter(::shouldContinueAllInclude).forEach {
                                        DownloadManager.startDownload(it.id)
                                    }
                                }
                            }
                        }, modifier = Modifier.appElasticPress()) {
                            AppText(
                                text = if (hasActive) "暂停全部" else "继续全部",
                                style = MaterialTheme.typography.labelMedium,
                                color = AppContentColors.accent()
                            )
                        }
                    }
                    VideoListLayoutToggle(
                        singleColumn = listLayout.singleColumn,
                        onClick = listLayout.toggle,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        // 遮挡铰链时整页内容落入最大安全区，软折痕允许跨越（与首页/动态/搜索一致）。
        com.android.purebilibili.core.ui.adaptive.AppHingeSafeContent(
            modifier = Modifier.fillMaxSize(),
        ) {
        if (!downloadsInitialized) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                AppCircularProgressIndicator()
            }
        } else if (taskList.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                com.android.purebilibili.core.ui.EmptyState(
                    message = "暂无缓存视频",
                    subtitle = "在视频详情页点击「缓存」按钮下载",
                    enableEasterEgg = false
                )
            }
        } else {
            val alignRows = columns > 1 && !com.android.purebilibili.core.ui.LocalFullVideoCardContentVisible.current
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .then(if (columns == 1) Modifier.responsiveContentWidth(com.android.purebilibili.core.util.resolveSingleColumnFeedMaxWidth()) else Modifier)
                    .fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp +
                        if (com.android.purebilibili.core.ui.rememberNowPlayingBarOverlayVisible()) {
                            // 听视频小横条悬浮时统一上浮避让（与首页/稍后再看一致）
                            com.android.purebilibili.core.ui.NowPlayingBarOverlayAvoidancePadding
                        } else {
                            0.dp
                        }
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (selectionMode) {
                    item(key = "batch_management", span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppTextButton(
                                enabled = !deletingBatch && completedTaskIds.isNotEmpty(),
                                onClick = {
                                    selectedTaskIds = if (selectedCompletedIds == completedTaskIds) {
                                        emptySet()
                                    } else completedTaskIds
                                },
                            ) {
                                AppText(if (selectedCompletedIds == completedTaskIds) "取消全选" else "全选")
                            }
                            Spacer(Modifier.weight(1f))
                            AppTextButton(
                                enabled = !deletingBatch && selectedCompletedIds.isNotEmpty(),
                                onClick = { pendingBatchDeleteIds = selectedCompletedIds.toSet() },
                            ) {
                                AppText(if (deletingBatch) "删除中…" else "删除 (${selectedCompletedIds.size})")
                            }
                        }
                    }
                }
                val taskKeys = taskList.map { it.id }
                val renderTask: @Composable (Int, Modifier) -> Unit = { index, cardModifier ->
                    val task = taskList[index]
                    val isDissolving = task.id in dissolvingTaskIds
                    AnimatedVideoListItem(modifier = cardModifier, enabled = true, useLookaheadBounds = false) {
                    com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard(
                        isDissolving = isDissolving,
                        onDissolveComplete = {
                            actionScope.launch {
                                try {
                                    DownloadManager.removeTask(task.id)
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    android.util.Log.e("DownloadList", "Failed to delete cache task ${task.id}", error)
                                    Toast.makeText(context, "删除失败，请重试", Toast.LENGTH_SHORT).show()
                                } finally {
                                    dissolvingTaskIds = dissolvingTaskIds - task.id
                                    deletingTaskId = null
                                }
                            }
                        },
                        cardId = task.id,
                        preset = com.android.purebilibili.core.ui.animation.DissolveAnimationPreset.TELEGRAM_FAST,
                        // 删除只作用于当前卡片，避免相邻内容在磨砂背景中持续抖动。
                        publishGlobalDissolveState = false,
                        keepInvisibleAfterDissolve = true,
                    ) {
                        val playableOffline = remember(task.filePath, task.status) {
                            isDownloadTaskPlayableOffline(task)
                        }
                        DownloadTaskItem(
                            task = task,
                            modifier = if (alignRows) Modifier.fillMaxHeight() else Modifier,
                            stacked = columns > 1,
                            onClick = {
                                if (selectionMode) {
                                    if (!deletingBatch && task.id in completedTaskIds) {
                                        selectedTaskIds = if (task.id in selectedTaskIds) {
                                            selectedTaskIds - task.id
                                        } else selectedTaskIds + task.id
                                    }
                                } else when (resolveDownloadTaskClickTarget(task, isNetworkAvailable = isNetworkAvailable)) {
                                    DownloadTaskClickTarget.OfflinePlayer -> onOfflineVideoClick(task.id)
                                    DownloadTaskClickTarget.OnlinePlayer -> {
                                        Toast.makeText(
                                            context,
                                            "本地文件不可用，已切换在线播放",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onVideoClick(task.bvid)
                                    }
                                    null -> Unit
                                }
                            },
                            onPauseResume = {
                                actionScope.launch {
                                    val currentTask = DownloadManager.tasks.value[task.id] ?: return@launch
                                    if (currentTask.isDownloading) {
                                        DownloadManager.pauseDownload(task.id)
                                    } else if (currentTask.canResume) {
                                        DownloadManager.startDownload(task.id)
                                    }
                                    val updatedStatus = DownloadManager.tasks.value[task.id]?.status
                                    if (updatedStatus != null && updatedStatus != currentTask.status) {
                                        downloadHaptic(HapticType.LIGHT)
                                    }
                                }
                            },
                            onDelete = {
                                if (deletingTaskId == null) pendingDeleteTask = task
                            },
                            selectionMode = selectionMode,
                            selected = task.id in selectedCompletedIds,
                            selectionEnabled = !deletingBatch && task.id in completedTaskIds,
                            offlinePlayable = playableOffline,
                            speedBytesPerSecond = if (task.status == DownloadStatus.DOWNLOADING) {
                                downloadSpeedsByTask[task.id] ?: 0L
                            } else {
                                0L
                            }
                        )
                    }
                    }
                }
                if (!alignRows) {
                    itemsIndexed(taskList, key = { _, task -> task.id }) { index, _ ->
                        renderTask(index, videoListItemModifier())
                    }
                } else {
                    com.android.purebilibili.feature.home.resolveHomeFeedAlignedRows(taskList.size, columns)
                        .forEach { row ->
                            item(key = "download_row_${taskKeys[row.first]}", span = { GridItemSpan(maxLineSpan) }) {
                                com.android.purebilibili.feature.home.HomeAlignedVideoRow(
                                    indices = row,
                                    itemKeys = taskKeys,
                                    columns = columns,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = videoListItemModifier().fillMaxWidth(),
                                    content = renderTask,
                                )
                            }
                        }
                }

                // [新增] 显示当前存储路径
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppText(
                            text = "共 ${taskList.size} 个 · 已用 " + formatDownloadStorageBytes(
                                taskList.sumOf { it.fileSize.coerceAtLeast(0L) }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText()
                        )
                        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                        AppText(
                            text = "存储位置",
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText()
                        )
                        Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))
                        AppText(
                            text = currentDir,
                            style = MaterialTheme.typography.labelSmall,
                            color = AppContentColors.secondaryText(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
        }
    }

    if (pendingBatchDeleteIds.isNotEmpty()) {
        AppAlertDialog(
            onDismissRequest = { pendingBatchDeleteIds = emptySet() },
            title = { AppText("批量删除缓存") },
            text = {
                AppText("确定删除选中的 ${pendingBatchDeleteIds.size} 个已缓存视频吗？视频、音频、封面和弹幕文件将一并清除，此操作无法撤销。")
            },
            confirmButton = {
                AppTextButton(enabled = !deletingBatch, onClick = {
                    if (deletingBatch || pendingBatchDeleteIds.isEmpty()) return@AppTextButton
                    val idsToDelete = pendingBatchDeleteIds.toSet()
                    pendingBatchDeleteIds = emptySet()
                    deletingBatch = true
                    actionScope.launch {
                        // 批量删除直接处理全部选中项，不依赖可见卡片的消散回调。
                        withContext(NonCancellable) {
                            var failedCount = 0
                            try {
                                delay(deleteDialogExitDelayMs)
                                idsToDelete.forEach { id ->
                                    if (DownloadManager.tasks.value[id]?.isComplete == true) {
                                        try {
                                            DownloadManager.removeTask(id)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            android.util.Log.e("DownloadList", "Failed to delete cache task $id", error)
                                            failedCount++
                                        }
                                    }
                                }
                                if (failedCount == 0) exitSelection()
                                Toast.makeText(
                                    context,
                                    if (failedCount == 0) "已删除所选缓存" else "$failedCount 项删除失败，请重试",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } finally {
                                deletingBatch = false
                            }
                        }
                    }
                }) {
                    AppText("删除", color = AppContentColors.error())
                }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingBatchDeleteIds = emptySet() }) { AppText("取消") }
            },
        )
    }

    // 删除确认：避免误触直接清掉已下载的文件
    pendingDeleteTask?.let { taskToDelete ->
        AppAlertDialog(
            onDismissRequest = { pendingDeleteTask = null },
            title = { AppText("删除缓存") },
            text = {
                AppText(
                    text = "确定删除「${taskToDelete.title}」吗？已下载的视频、音频和弹幕文件将一并清除。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (deletingTaskId != null) return@TextButton
                        deletingTaskId = taskToDelete.id
                        pendingDeleteTask = null
                        actionScope.launch {
                            // 玻璃弹窗退场完成后再改变背景；原生弹窗关闭后直接开始。
                            delay(deleteDialogExitDelayMs)
                            dissolvingTaskIds = dissolvingTaskIds + taskToDelete.id
                        }
                    }, modifier = Modifier.appElasticPress()
                ) {
                    AppText("删除", color = AppContentColors.error())
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTask = null }, modifier = Modifier.appElasticPress()) {
                    AppText("取消")
                }
            }
        )
    }
}


/** 下载占用容量摘要（B / KB / MB / GB 自适应） */
internal fun formatDownloadStorageBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}


@Composable
internal fun DownloadTaskItem(
    task: DownloadTask,
    onClick: () -> Unit,
    onPauseResume: () -> Unit,
    onDelete: () -> Unit,
    stacked: Boolean = false,
    offlinePlayable: Boolean,
    speedBytesPerSecond: Long = 0L,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    selectionEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val feedbackClick = appActionWithFeedback(onClick)
    val typography = com.android.purebilibili.feature.home.components.cards.horizontalVideoCardTypography()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppShapes.container(ContainerLevel.Card))
            .background(AppSurfaceTokens.cardContainer())
            .then(
                if (selectionMode) Modifier.toggleable(
                    value = selected,
                    enabled = selectionEnabled,
                    role = Role.Checkbox,
                    onValueChange = { feedbackClick() },
                ) else Modifier.appActionClickable(onClick = onClick)
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 封面旁只放视频信息；下载明细与操作使用下方通栏，避免挤在半宽信息区。
        HorizontalVideoCardFrame(
            stacked = stacked,
            plainHorizontal = true,
            horizontalCoverMaxWidthDp = com.android.purebilibili.feature.home.components.cards.WIDE_LIST_COVER_MAX_WIDTH_DP,
            coverContent = {
                val localCoverFile = task.localCoverPath?.let { java.io.File(it) }
                val coverSource = if (localCoverFile?.exists() == true) {
                    localCoverFile
                } else {
                    val coverUrl = task.cover.let { url ->
                        if (url.startsWith("http://")) url.replace("http://", "https://")
                        else url
                    }
                    coil3.request.ImageRequest.Builder(LocalContext.current)
                        .data(coverUrl)
                        .httpHeaders(NetworkHeaders.Builder().set("Referer", "https://www.bilibili.com").build())
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = coverSource,
                    contentDescription = task.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            },
            coverOverlayContent = {
                if (!task.isComplete) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        when (task.status) {
                            DownloadStatus.QUEUED -> {
                                AppText("排队中", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                            DownloadStatus.DOWNLOADING, DownloadStatus.MERGING -> {
                                AppCircularProgressIndicator(
                                    progress = { resolveDownloadTaskProgress(task) },
                                    modifier = Modifier.size(32.dp),
                                    color = Color.White,
                                    strokeWidth = 3.dp
                                )
                            }
                            DownloadStatus.PAUSED -> {
                                AppText("已暂停", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                            DownloadStatus.FAILED -> {
                                AppText("失败", color = AppContentColors.error(), style = MaterialTheme.typography.labelMedium)
                            }
                            else -> {}
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(AppSpacingTokens.ExtraSmall)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            AppShapes.container(ContainerLevel.Tag)
                        )
                        .padding(horizontal = AppSpacingTokens.ExtraSmall, vertical = AppSpacingTokens.Micro)
                ) {
                    AppText(text = task.qualityDesc, color = Color.White, style = typography.statistic)
                }
            },
            infoContent = {
                AppText(
                    text = task.title,
                    style = typography.title,
                    maxLines = com.android.purebilibili.core.ui.videoCardTitleMaxLines(),
                    overflow = com.android.purebilibili.core.ui.videoCardTitleOverflow(),
                    color = AppContentColors.primaryText()
                )
            },
            supportingContent = {
                resolveDownloadTaskSecondaryText(task)?.let { secondaryText ->
                    AppText(
                        text = secondaryText,
                        style = typography.author,
                        color = AppContentColors.accent(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                AppText(
                    text = task.ownerName,
                    style = typography.author,
                    color = AppContentColors.secondaryText(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
        )

        val assetSummary = resolveDownloadAssetSummary(task)
        val assetTexts = listOfNotNull(
            assetSummary.videoText,
            assetSummary.audioText,
            assetSummary.danmakuText
        )
        val hasAssetSummary = assetTexts.isNotEmpty()
        val statusText = when (task.status) {
            DownloadStatus.QUEUED -> "排队中..."
            DownloadStatus.PENDING -> "等待中..."
            DownloadStatus.DOWNLOADING -> buildString {
                append("下载中 ${resolveDownloadTaskProgressPercent(task)}%")
                if (speedBytesPerSecond > 0L) {
                    append(" · ")
                    append(formatDownloadStorageBytes(speedBytesPerSecond))
                    append("/s")
                }
            }
            DownloadStatus.MERGING -> "处理中..."
            DownloadStatus.COMPLETED -> if (hasAssetSummary) null else "已完成"
            DownloadStatus.PAUSED -> "已暂停"
            DownloadStatus.FAILED -> task.errorMessage ?: "下载失败"
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (statusText != null) {
                AppText(
                    text = statusText,
                    modifier = Modifier.fillMaxWidth(),
                    style = typography.statistic,
                    color = when (task.status) {
                        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                        DownloadStatus.FAILED -> AppContentColors.error()
                        else -> AppContentColors.accent()
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (hasAssetSummary) {
                AppText(
                    text = assetTexts.joinToString(" · "),
                    modifier = Modifier.fillMaxWidth(),
                    style = typography.statistic,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (task.isComplete && !offlinePlayable) {
                AppText(
                    text = if (!task.exportedFileUri.isNullOrBlank()) {
                        "已导出到自定义目录，当前列表不直接离线播放"
                    } else {
                        "本地缓存文件不可用"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = typography.statistic,
                    color = AppContentColors.secondaryText(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                AppCheckbox(
                    checked = selected,
                    onCheckedChange = null,
                    enabled = selectionEnabled,
                    modifier = Modifier.size(48.dp),
                )
            } else {
                if (task.isDownloading || task.canResume) {
                    AppIconButton(onClick = onPauseResume, modifier = Modifier.size(48.dp)) {
                        AppIcon(
                            imageVector = if (task.isDownloading) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            contentDescription = if (task.isDownloading) "暂停" else "继续",
                            tint = AppContentColors.accent()
                        )
                    }
                }
                AppIconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    AppIcon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "删除",
                        tint = AppContentColors.error()
                    )
                }
            }
        }
    }
}
