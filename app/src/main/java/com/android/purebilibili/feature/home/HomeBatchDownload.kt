package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppModalBottomSheet
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import com.android.purebilibili.core.ui.components.AppFloatingToolbar
import com.android.purebilibili.core.ui.components.AppSnackbarHost
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.feature.download.BatchDownloadDialog
import com.android.purebilibili.feature.download.DownloadManager
import com.android.purebilibili.feature.download.FeedBatchDownloadInput
import com.android.purebilibili.feature.download.FeedBatchDownloadReport
import com.android.purebilibili.feature.download.FeedDownloadVideo
import com.android.purebilibili.feature.download.enqueueFeedBatchDownload
import com.android.purebilibili.feature.download.loadFeedBatchDownloadInput
import com.android.purebilibili.feature.download.summarizeBatchDownloadQueueResult
import kotlinx.coroutines.launch

internal fun VideoItem.supportsFeedDownload(): Boolean =
    bvid.startsWith("BV", ignoreCase = true) &&
        contentType != "bangumi" && contentType != "picture" && episodeId <= 0L

internal class HomeDownloadSelection {
    private val selected = mutableStateMapOf<String, FeedDownloadVideo>()
    var active by mutableStateOf(false)
        private set
    val count: Int get() = selected.size
    var request by mutableStateOf<List<FeedDownloadVideo>?>(null)
        private set

    fun contains(bvid: String): Boolean = selected.containsKey(bvid)

    fun start(item: VideoItem) {
        active = true
        selected[item.bvid] = FeedDownloadVideo(item.bvid, item.title)
    }

    fun toggle(item: VideoItem) {
        if (!item.supportsFeedDownload()) return
        if (selected.remove(item.bvid) == null) selected[item.bvid] = FeedDownloadVideo(item.bvid, item.title)
    }

    fun openSingle(item: VideoItem) {
        request = listOf(FeedDownloadVideo(item.bvid, item.title))
    }

    fun openSelected() {
        if (selected.isNotEmpty()) request = selected.values.toList()
    }

    fun dismissRequest() {
        request = null
    }

    fun clearSelection() {
        active = false
        selected.clear()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeBatchDownloadHost(
    selection: HomeDownloadSelection,
    pageActive: Boolean,
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var submitting by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<FeedBatchDownloadReport?>(null) }
    val request = selection.request
    LocalNavigationBackHandler(enabled = pageActive && selection.active && request == null) {
        selection.clearSelection()
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AppSnackbarHost(hostState = snackbar, modifier = Modifier.fillMaxWidth())
        if (pageActive && selection.active) {
            AppFloatingToolbar {
                AppText("已选 ${selection.count} 个", modifier = Modifier.padding(horizontal = 12.dp))
                AppTextButton(onClick = selection::clearSelection) { AppText("取消") }
                AppTextButton(enabled = selection.count > 0, onClick = selection::openSelected) { AppText("下载") }
            }
        }
    }
    if (submitting) {
        AppAlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { AppText("正在加入下载队列…") },
        )
    } else if (request != null) {
        var input by remember(request) { mutableStateOf<FeedBatchDownloadInput?>(null) }
        var retry by remember(request) { mutableIntStateOf(0) }
        LaunchedEffect(request, retry) { input = loadFeedBatchDownloadInput(request) }
        val loaded = input
        if (loaded == null || loaded.qualities.isEmpty()) {
            AppModalBottomSheet(onDismissRequest = selection::dismissRequest) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppText(if (loaded == null) "正在读取分 P 和下载画质…" else "未获取到可下载画质")
                    if (loaded != null) {
                        if (loaded.failures.isNotEmpty()) {
                            Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                                loaded.failures.forEach { AppText("${it.title}：${it.reason}") }
                            }
                        }
                        AppTextButton(onClick = {
                            input = null
                            retry++
                        }) { AppText("重试") }
                    }
                }
            }
        } else {
            val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
            val existingIds = remember(tasks) {
                tasks.values.filter { !it.isFailed && !it.isAudioOnly }.map { "${it.bvid}#${it.cid}" }.toSet()
            }
            BatchDownloadDialog(
                title = request.singleOrNull()?.title ?: "已选 ${request.size} 个视频",
                candidates = loaded.candidates,
                qualityOptions = loaded.qualities,
                currentQuality = loaded.preferredQuality,
                downloadedIds = existingIds,
                groupPartsByVideo = true,
                candidateLoadNotice = loaded.failures.takeIf { it.isNotEmpty() }
                    ?.let { "有 ${it.size} 个视频未能读取分 P，其余视频可继续下载。" },
                onDismiss = selection::dismissRequest,
                onConfirm = { qualityId, options, candidates ->
                    if (!submitting) {
                        submitting = true
                        selection.dismissRequest()
                        val quality = loaded.qualities.first { it.first == qualityId }
                        scope.launch {
                            val queued = try {
                                enqueueFeedBatchDownload(quality, options, candidates)
                            } finally {
                                submitting = false
                            }
                            val failures = loaded.failures + queued.failures
                            val result = queued.result.copy(failedCount = failures.size)
                            selection.clearSelection()
                            if (failures.isNotEmpty()) {
                                report = FeedBatchDownloadReport(result, failures)
                            } else if (snackbar.showSnackbar(
                                    summarizeBatchDownloadQueueResult(result),
                                    actionLabel = "查看下载",
                                ) == SnackbarResult.ActionPerformed
                            ) {
                                onOpenDownloads()
                            }
                        }
                    }
                },
            )
        }
    }
    report?.let { completed ->
        AppAlertDialog(
            onDismissRequest = { report = null },
            title = { AppText(summarizeBatchDownloadQueueResult(completed.result)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    completed.failures.forEach { AppText("${it.title}：${it.reason}") }
                }
            },
            dismissButton = { AppTextButton(onClick = { report = null }) { AppText("关闭") } },
            confirmButton = {
                AppTextButton(onClick = {
                    report = null
                    onOpenDownloads()
                }) { AppText("查看下载") }
            },
        )
    }
}
