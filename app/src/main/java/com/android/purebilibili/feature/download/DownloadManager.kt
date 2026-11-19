package com.android.purebilibili.feature.download

import com.android.purebilibili.core.network.buildAppDns
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.util.isBilibiliMediaHost
import com.android.purebilibili.feature.plugin.PlaybackCdnPreference
import com.android.purebilibili.feature.plugin.rewriteBilivideoHost
import kotlinx.coroutines.flow.first
import android.util.AtomicFile
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.android.purebilibili.data.model.response.getBestAudio
import com.android.purebilibili.data.model.response.getBestVideo
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.repository.BangumiRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.FileOutputStream
import java.util.UUID

private const val DEFAULT_MUXER_SAMPLE_BUFFER_SIZE_BYTES = 1024 * 1024
private val PARTIAL_CONTENT_RANGE_REGEX = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")

internal fun isValidPartialContentResponse(
    responseCode: Int,
    contentRange: String?,
    requestedStart: Long,
    requestedEnd: Long,
    expectedTotal: Long? = null,
): Boolean {
    if (responseCode != 206) return false
    val match = PARTIAL_CONTENT_RANGE_REGEX.matchEntire(contentRange?.trim().orEmpty()) ?: return false
    val actualStart = match.groupValues[1].toLongOrNull() ?: return false
    val actualEnd = match.groupValues[2].toLongOrNull() ?: return false
    return actualStart == requestedStart && actualEnd == requestedEnd &&
        (expectedTotal == null || match.groupValues[3].toLongOrNull() == expectedTotal)
}

internal fun resolveMuxerSampleBufferSize(
    maxInputSizes: List<Int>,
    defaultBytes: Int = DEFAULT_MUXER_SAMPLE_BUFFER_SIZE_BYTES
): Int {
    val advertisedMax = maxInputSizes.filter { it > 0 }.maxOrNull() ?: 0
    return maxOf(defaultBytes, advertisedMax)
}

/**
 *  视频下载管理器
 * 
 * 功能：
 * - 管理下载任务队列
 * - 支持断点续传
 * - 音视频分离下载后合并
 * - 持久化存储下载状态
 */
object DownloadManager {
    
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val client = OkHttpClient.Builder()
        .dns(buildAppDns())
        .protocols(com.android.purebilibili.core.network.NetworkModule.resolveSharedNetworkProtocols())
        .followRedirects(true)
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val acceptsAccountCookie = request.url.isHttps && isBilibiliMediaHost(request.url.host)
            chain.proceed(if (acceptsAccountCookie) request else request.newBuilder().removeHeader("Cookie").build())
        }
        .build()
    private val assetDownloader = ResumableAssetDownloader(client)
    
    private val initializationMutex = Mutex()
    private val initializationReady = CompletableDeferred<Unit>()
    private val persistenceMutex = Mutex()
    private val schedulingMutex = Mutex()
    private val taskControlMutex = Mutex()
    private val transferLocks = Array(32) { Mutex() }

    private fun transferLock(taskId: String): Mutex = transferLocks[(taskId.hashCode() and Int.MAX_VALUE) % transferLocks.size]
    private var lastPersistedTasks: Map<String, DownloadTask>? = null
    
    // 下载任务状态
    private val _tasks = MutableStateFlow<Map<String, DownloadTask>>(emptyMap())
    val tasks: StateFlow<Map<String, DownloadTask>> = _tasks.asStateFlow()
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()
    
    // 🔧 [移除] downloadJobs 已被 WorkManager 替代
    
    // 下载目录
    @Volatile
    private var downloadDir: File? = null
    private var tasksFile: File? = null
    private var appContext: Context? = null

    private fun safeExternalFilesRoot(context: Context): File? {
        return runCatching { context.getExternalFilesDir(null) }
            .onFailure { error ->
                com.android.purebilibili.core.util.Logger.w(
                    "DownloadManager",
                    "⚠️ External files dir unavailable, falling back to internal storage",
                    error
                )
            }
            .getOrNull()
    }
    
    /**
     * 初始化（在 Application 中调用）
     */
    suspend fun init(context: Context) {
        initializationMutex.withLock {
            if (!initializationReady.isCompleted) {
                withContext(Dispatchers.IO + NonCancellable) {
                    appContext = context.applicationContext
                    val initialPath = com.android.purebilibili.core.store.SettingsManager
                        .getDownloadPathSync(context)
                    downloadDir = resolveDownloadDir(context, initialPath)
                    tasksFile = File(context.filesDir, "download_tasks.json")
                    loadTasks()
                    // 恢复已保存的等待状态，WorkManager 保留原有的唤醒请求。
                    for (task in _tasks.value.values.filter { it.isWaitingForRetry }) {
                        enqueueRetryWakeup(task)
                    }
                    _isInitialized.value = true
                    initializationReady.complete(Unit)
                }
            }
        }
        scheduleNextQueuedDownload()
    }

    suspend fun awaitInitialization() {
        initializationReady.await()
    }

    /** Application owns this process-lifetime settings collection. */
    suspend fun observeDownloadPath(context: Context) {
        initializationReady.await()
        withContext(Dispatchers.IO) {
            com.android.purebilibili.core.store.SettingsManager.getDownloadPath(context)
                .collect { customPath ->
                    downloadDir = resolveDownloadDir(context, customPath)
                }
        }
    }

    /**
     * 获取下载目录
     */
    fun getDownloadDir(): File = downloadDir ?: throw IllegalStateException("DownloadManager not initialized")
    
    /**
     * 添加下载任务
     */
    suspend fun addTask(task: DownloadTask): Boolean {
        val telemetry = DownloadTelemetry()
        initializationReady.await()
        return withTaskControl {
            val newTask = task.copy(status = DownloadStatus.QUEUED, errorMessage = null)
            while (true) {
                val current = _tasks.value
                val existing = current[task.id]
                if (existing != null && existing.status != DownloadStatus.FAILED && existing.status != DownloadStatus.PAUSED) {
                    telemetry.record(DownloadStage.QUEUE, DownloadOutcome.DUPLICATE)
                    return@withTaskControl false
                }
                if (_tasks.compareAndSet(current, current + (task.id to newTask))) break
            }
            saveTasks()
            telemetry.record(DownloadStage.QUEUE, DownloadOutcome.ACCEPTED)
            scheduleNextQueuedDownload()
            true
        }
    }
    
    /**
     * 开始下载（使用 WorkManager 调度）
     */
    suspend fun startDownload(taskId: String) {
        initializationReady.await()
        withTaskControl {
            val task = _tasks.value[taskId] ?: return@withTaskControl
            if (isDownloadTaskActive(task) || task.status == DownloadStatus.QUEUED) return@withTaskControl
            updateTask(taskId) {
                it.copy(status = DownloadStatus.QUEUED, errorMessage = null, automaticRetries = 0)
            }
            scheduleNextQueuedDownload()
        }
    }
    
    /**
     * 🔧 [新增] 执行下载（由 WorkManager 调用）
     * @throws Exception 下载失败时抛出异常
     */
    internal suspend fun beginWorkerAttempt(taskId: String, workToken: String?): Int? {
        updateWorkerTask(taskId, workToken) { it.copy(downloadAttempts = it.downloadAttempts + 1) }
        return _tasks.value[taskId]?.takeIf {
            it.workToken == workToken && isDownloadTaskActive(it)
        }?.downloadAttempts
    }

    internal suspend fun executeDownload(
        taskId: String,
        workToken: String?,
        onStage: (DownloadStage) -> Unit,
    ) {
        initializationReady.await()
        transferLock(taskId).withLock {
            updateWorkerTask(taskId, workToken) { it.copy(status = DownloadStatus.DOWNLOADING, errorMessage = null) }
            ensureTaskCanRun(taskId, workToken)
            downloadTask(taskId, workToken, onStage)
        }
    }

    internal fun ownsActiveWork(taskId: String, workToken: String?): Boolean =
        _tasks.value[taskId]?.let { it.workToken == workToken && isDownloadTaskActive(it) } == true

    internal suspend fun finishWorkerAttempt(
        taskId: String,
        workToken: String?,
        status: DownloadStatus,
        message: String,
    ) = withContext(NonCancellable) {
        updateWorkerTask(taskId, workToken) { it.copy(status = status, errorMessage = message) }
        if (status != DownloadStatus.PENDING) scheduleNextQueuedDownload()
    }

    internal suspend fun deferDownloadRetry(taskId: String, workToken: String?): Boolean = withTaskControl {
        val task = _tasks.value[taskId]?.takeIf { it.workToken == workToken && isDownloadTaskActive(it) }
            ?: return@withTaskControl false
        val waiting = task.deferAutomaticRetry(System.currentTimeMillis()) ?: return@withTaskControl false
        updateTask(taskId) {
            it.copy(
                status = waiting.status,
                automaticRetries = waiting.automaticRetries,
                retryNotBeforeMs = waiting.retryNotBeforeMs,
            )
        }
        val scheduled = enqueueRetryWakeup(waiting)
        scheduleNextQueuedDownload()
        scheduled
    }

    internal suspend fun makeDownloadRetryReady(taskId: String, workToken: String?) = withTaskControl {
        val task = _tasks.value[taskId] ?: return@withTaskControl
        val ready = task.makeRetryReady(workToken) ?: return@withTaskControl
        updateTask(taskId) { it.copy(retryNotBeforeMs = ready.retryNotBeforeMs) }
        scheduleNextQueuedDownload()
    }

    private suspend fun enqueueRetryWakeup(task: DownloadTask): Boolean {
        val telemetry = DownloadTelemetry()
        return try {
            DownloadWorker.enqueue(
                context = checkNotNull(appContext),
                taskId = task.id,
                workToken = checkNotNull(task.workToken),
                retryDelayMs = (task.retryNotBeforeMs - System.currentTimeMillis()).coerceAtLeast(0L),
            )
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            updateTask(task.id) {
                it.copy(
                    status = DownloadStatus.FAILED,
                    retryNotBeforeMs = 0L,
                    errorMessage = "无法安排下载重试：${error.message.orEmpty()}",
                )
            }
            telemetry.record(DownloadStage.SCHEDULING, DownloadOutcome.FAILED, error)
            false
        }
    }

    private suspend fun updateWorkerTask(
        taskId: String,
        workToken: String?,
        update: (DownloadTask) -> DownloadTask,
    ) = updateTask(taskId) { task ->
        if (task.workToken == workToken && isDownloadTaskActive(task)) update(task) else task
    }

    /**
     * 暂停下载
     */
    suspend fun pauseDownload(taskId: String) {
        initializationReady.await()
        val context = appContext ?: return
        withTaskControl {
            val task = _tasks.value[taskId] ?: return@withTaskControl
            if (task.status != DownloadStatus.QUEUED && !isDownloadTaskActive(task)) return@withTaskControl
            updateTask(taskId) { current ->
                if (current.status == DownloadStatus.QUEUED || isDownloadTaskActive(current)) {
                    current.copy(status = DownloadStatus.PAUSED, retryNotBeforeMs = 0L)
                } else current
            }
            // 先持久化用户暂停状态，再取消 Worker，避免系统取消回调误判为需要重试。
            DownloadWorker.cancel(context, taskId)
            scheduleNextQueuedDownload()
        }
    }
    
    /**
     * 删除任务
     */
    suspend fun removeTask(taskId: String) {
        initializationReady.await()
        val context = appContext ?: return
        withTaskControl {
            var removedTask: DownloadTask? = null
            _tasks.update { current ->
                removedTask = current[taskId]
                current - taskId
            }
            saveTasks()
            DownloadWorker.cancel(context, taskId)
            transferLock(taskId).withLock {
                removedTask?.let { task ->
                    val cleanupTargets = resolveDownloadCleanupTargets(
                        taskId = taskId,
                        task = task,
                        taskDirectoryPath = getTaskDir(taskId).absolutePath
                    )
                    cleanupTargets.filePaths.forEach { path ->
                        runCatching { File(path).delete() }
                    }
                    task.exportedFileUri?.let { deleteExportedFile(context, it) }
                    val taskDir = File(cleanupTargets.taskDirectoryPath)
                    if (taskDir.exists() && taskDir.isDirectory && taskDir.listFiles().isNullOrEmpty()) {
                        taskDir.delete()
                    }
                }
            }
            scheduleNextQueuedDownload()
        }
    }

    suspend fun updatePlaybackPosition(taskId: String, positionMs: Long) {
        initializationReady.await()
        updateTask(taskId) { current ->
            current.copy(lastPlaybackPositionMs = positionMs.coerceAtLeast(0L))
        }
    }
    
    /**
     * 获取任务状态
     */
    fun getVideoTask(bvid: String, cid: Long): DownloadTask? {
        return _tasks.value.values
            .filter { !it.isAudioOnly && it.bvid == bvid && it.cid == cid }
            .sortedWith(
                compareByDescending<DownloadTask> { it.isComplete }
                    .thenByDescending { it.isDownloading }
                    .thenByDescending { it.createdAt }
            )
            .firstOrNull()
    }
    
    /**
     * 执行下载
     */
    private suspend fun downloadTask(taskId: String, workToken: String?, onStage: (DownloadStage) -> Unit) {
        var task = _tasks.value[taskId]?.takeIf { it.workToken == workToken }
            ?: throw CancellationException("下载任务已取消")

        onStage(DownloadStage.PREPARATION)
        // 串行队列里排了很久的任务，入队时解析的 DASH 地址大概率已过期
        if (shouldRefreshStaleDownloadUrls(
                createdAtMs = maxOf(task.createdAt, task.urlsRefreshedAt),
                nowMs = System.currentTimeMillis()
            )
        ) {
            com.android.purebilibili.core.util.Logger.d(
                "DownloadManager",
                "🔄 Stale queued task, refreshing playurl before download: $taskId"
            )
            task = refreshTaskDownloadUrls(task)
        }
        
        val videoFile = getVideoFile(task.id)
        val audioFile = getAudioFile(task.id)
        val outputFile = getOutputFile(task.id)
        val coverFile = getCoverFile(task.id)
        
        // 🖼️ 0. 下载封面图片（用于离线显示）
        try {
            updateAssetState(task.id, DownloadAssetKind.COVER) {
                it.copy(status = DownloadAssetStatus.DOWNLOADING, errorMessage = null)
            }
            if (!coverFile.exists() || coverFile.length() <= 0L) {
                downloadCoverImage(task.cover, coverFile)
                com.android.purebilibili.core.util.Logger.d("DownloadManager", "🖼️ Cover downloaded: ${coverFile.name}")
            }
            updateTask(task.id) {
                it.copy(localCoverPath = coverFile.absolutePath)
                    .withAssetState(
                        DownloadAssetState(
                            kind = DownloadAssetKind.COVER,
                            status = DownloadAssetStatus.COMPLETED,
                            totalBytes = coverFile.length(),
                            downloadedBytes = coverFile.length(),
                            filePath = coverFile.absolutePath
                        )
                    )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateAssetState(task.id, DownloadAssetKind.COVER) {
                it.copy(status = DownloadAssetStatus.FAILED, errorMessage = e.message)
            }
            com.android.purebilibili.core.util.Logger.w("DownloadManager", "⚠️ Cover download failed, will use network URL", e)
        }

        var localDanmakuSegmentPaths = task.localDanmakuSegmentPaths
        var localDanmakuMetadataPath = task.localDanmakuMetadataPath
        try {
            val danmakuResult = DownloadDanmakuAssetService.download(
                task = task,
                taskDir = getTaskDir(task.id),
                updateState = { state ->
                    updateTaskInMemory(task.id) { it.withAssetState(state) }
                }
            )
            localDanmakuSegmentPaths = danmakuResult.segmentPaths
            localDanmakuMetadataPath = danmakuResult.metadataPath
            updateTask(task.id) {
                it.copy(
                    localDanmakuSegmentPaths = localDanmakuSegmentPaths,
                    localDanmakuMetadataPath = localDanmakuMetadataPath
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            updateAssetState(task.id, DownloadAssetKind.DANMAKU) {
                it.copy(status = DownloadAssetStatus.FAILED, errorMessage = e.message ?: "弹幕下载失败")
            }
            com.android.purebilibili.core.util.Logger.w("DownloadManager", "⚠️ Danmaku download failed, video download continues", e)
        }
        
        if (!task.isAudioOnly) {
            onStage(DownloadStage.VIDEO_TRANSFER)
            downloadAndVerifyTrack(task, DownloadAssetKind.VIDEO, videoFile)
        } else {
            updateTaskInMemory(task.id) {
                it.copy(videoProgress = 1f)
                    .withAssetState(DownloadAssetState(kind = DownloadAssetKind.VIDEO, status = DownloadAssetStatus.SKIPPED))
            }
        }

        onStage(DownloadStage.AUDIO_TRANSFER)
        ensureTaskCanRun(taskId, workToken)
        val audioTask = _tasks.value[taskId]?.takeIf { it.workToken == workToken }
            ?: throw CancellationException("音频下载已取消")
        downloadAndVerifyTrack(audioTask, DownloadAssetKind.AUDIO, audioFile)

        onStage(DownloadStage.MUX)
        currentCoroutineContext().ensureActive()
        updateWorkerTask(task.id, workToken) { it.copy(status = DownloadStatus.MERGING, progress = 0.95f) }
        ensureTaskCanRun(task.id, workToken)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        mergeVideoAudio(videoFile.takeUnless { task.isAudioOnly }, audioFile, outputFile)

        currentCoroutineContext().ensureActive()
        ensureTaskCanRun(task.id, workToken)
        onStage(DownloadStage.EXPORT)
        val exportedUri = appContext?.let { context ->
            exportToUserSelectedDirectory(
                context = context,
                sourceFile = outputFile,
                title = task.title,
                qualityDesc = task.qualityDesc,
                isAudioOnly = task.isAudioOnly
            )
        }

        // 5. 更新状态
        updateWorkerTask(task.id, workToken) {
            it.copy(
                status = DownloadStatus.COMPLETED, 
                progress = 1f,
                filePath = outputFile.absolutePath,
                exportedFileUri = exportedUri,
                fileSize = outputFile.length(),
                localDanmakuSegmentPaths = localDanmakuSegmentPaths,
                localDanmakuMetadataPath = localDanmakuMetadataPath
            ) 
        }
        val completed = _tasks.value[task.id]?.takeIf {
            it.workToken == workToken && it.status == DownloadStatus.COMPLETED
        }
        if (completed != null) {
            onStage(DownloadStage.COMPLETE)
            videoFile.delete()
            audioFile.delete()
            downloadSourceFile(videoFile).delete()
            downloadSourceFile(audioFile).delete()
            com.android.purebilibili.core.events.BrandSuccessEvents.downloadCompleted(
                taskId = completed.id,
                createdAt = completed.createdAt,
                title = completed.title
            )
        } else if (exportedUri != null) {
            appContext?.let { deleteExportedFile(it, exportedUri) }
        }
        scheduleNextQueuedDownload()
        
        com.android.purebilibili.core.util.Logger.d("DownloadManager", "✅ Download completed: ${task.title} (AudioOnly: ${task.isAudioOnly})")
    }
    
    private suspend fun downloadAndVerifyTrack(task: DownloadTask, kind: DownloadAssetKind, file: File) {
        val context = currentCoroutineContext()
        val checkActive = {
            context.ensureActive()
            ensureTaskCanRun(task.id, task.workToken)
        }
        checkActive()
        val saved = task.assetState(kind)
        val local = if (saved?.sha256 != null && file.isFile) {
            try {
                val sha256 = verifyDownloadTrackDigest(file, kind, saved.totalBytes, saved.sha256, checkActive)
                VerifiedDownloadTrack(saved.totalBytes, saved.segmentCount, sha256)
            } catch (error: DownloadTrackIntegrityException) {
                rejectDownloadedTrack(task, kind, file, error)
                null
            }
        } else null

        val verified = try {
            local ?: downloadFileWithUrlRefresh(task, kind, file) { downloadedBytes, totalBytes ->
                val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                updateTaskInMemory(task.id) {
                    if (it.workToken != task.workToken || !isDownloadTaskActive(it)) it
                    else it.withTrackProgress(kind, progress).withAssetState(
                        buildDownloadAssetProgressState(kind, file, downloadedBytes, totalBytes)
                    )
                }
            }
        } catch (error: DownloadTrackIntegrityException) {
            updateWorkerTask(task.id, task.workToken) {
                it.withAssetState(DownloadAssetState(kind, DownloadAssetStatus.FAILED, errorMessage = error.message))
            }
            throw error
        }
        checkActive()
        updateWorkerTask(task.id, task.workToken) {
            it.withTrackProgress(kind, 1f).withAssetState(
                DownloadAssetState(
                    kind = kind,
                    status = DownloadAssetStatus.COMPLETED,
                    totalBytes = verified.totalBytes,
                    downloadedBytes = verified.totalBytes,
                    filePath = file.absolutePath,
                    segmentCount = verified.segmentCount,
                    sha256 = verified.sha256,
                )
            )
        }
    }

    private fun DownloadTask.withTrackProgress(kind: DownloadAssetKind, fraction: Float): DownloadTask =
        if (kind == DownloadAssetKind.VIDEO) {
            copy(videoProgress = fraction, progress = (fraction + audioProgress) / 2)
        } else {
            copy(audioProgress = fraction, progress = if (isAudioOnly) fraction else (videoProgress + fraction) / 2)
        }

    private suspend fun rejectDownloadedTrack(
        task: DownloadTask,
        kind: DownloadAssetKind,
        file: File,
        error: DownloadTrackIntegrityException,
    ) {
        currentCoroutineContext().ensureActive()
        ensureTaskCanRun(task.id, task.workToken)
        DownloadTelemetry().record(DownloadStage.TRACK_INTEGRITY, DownloadOutcome.FAILED, error)
        assetDownloader.discardTrackFiles(file)
        updateWorkerTask(task.id, task.workToken) {
            it.withTrackProgress(kind, 0f).withAssetState(DownloadAssetState(kind))
        }
    }

    private suspend fun downloadVerifiedCandidate(
        url: String,
        file: File,
        task: DownloadTask,
        kind: DownloadAssetKind,
        onProgress: (Long, Long) -> Unit,
    ): VerifiedDownloadTrack {
        val context = currentCoroutineContext()
        suspend fun verify(result: HttpDownloadAssetResult): VerifiedDownloadTrack = try {
            requireReadableDownloadTrack(file, kind)
            val sha256 = verifyDownloadTrackDigest(file, kind, result.downloadedBytes, expectedSha256 = null) {
                context.ensureActive()
                ensureTaskCanRun(task.id, task.workToken)
            }
            VerifiedDownloadTrack(result.downloadedBytes, result.segmentCount, sha256)
        } catch (error: DownloadTrackIntegrityException) {
            rejectDownloadedTrack(task, kind, file, error)
            throw error
        }

        val result = downloadFile(url, file, task, onProgress)
        return try {
            verify(result)
        } catch (error: DownloadTrackIntegrityException) {
            // 旧任务的本地成品可能只核对过长度，清理后从相同地址完整下载一次。
            if (!result.reusedCompletedFile) throw error
            verify(downloadFile(url, file, task, onProgress))
        }
    }

    /**
     * 单线程下载单个文件，支持基于已有临时文件的续传。
     */
    private suspend fun downloadFile(
        url: String, 
        file: File, 
        task: DownloadTask,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): HttpDownloadAssetResult = withContext(Dispatchers.IO) {
        ensureTaskCanRun(task.id, task.workToken)
        val progressGate = DownloadProgressUpdateGate()
        assetDownloader.download(
            request = HttpDownloadAssetRequest(
                url = url,
                outputFile = file,
                headers = buildBilibiliDownloadHeaders()
            ),
            ensureActive = { ensureTaskCanRun(task.id, task.workToken) },
            onProgress = { downloadedBytes, totalBytes ->
                if (totalBytes > 0L) {
                    progressGate.emit(downloadedBytes, totalBytes, onProgress)
                }
            }
        )
    }

    /**
     * 应用设置里的「播放 CDN 偏好」。
     *
     * 这个设置原先只作用于播放器，下载链路完全没读：用户选了 ali / cos 之类线路，
     * 下载仍然落在 mcdn（PCDN）上，屏蔽 mcdn 就直接失败。这里按设置把 host 改写过去；
     * PCDN 域名同样参与改写（upgcxcode 路径与签名在 host 之间通用）。
     */
    private suspend fun applyCdnPreference(url: String): String {
        if (url.isBlank()) return url
        val ctx = appContext ?: return url
        return try {
            val stored = SettingsManager.getPlaybackCdnPreference(ctx).first()
            val host = PlaybackCdnPreference.fromStorageValue(stored).host ?: return url
            rewriteBilivideoHost(url, host) ?: url
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            url
        }
    }

    private suspend fun downloadFileWithUrlRefresh(
        task: DownloadTask,
        kind: DownloadAssetKind,
        file: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit
    ): VerifiedDownloadTrack {
        val isVideoStream = kind == DownloadAssetKind.VIDEO
        val telemetry = DownloadTelemetry()
        val attempted = mutableSetOf<String>()
        var lastError: Exception? = null
        var retryableError: Exception? = null
        var integrityError: DownloadTrackIntegrityException? = null
        var activeTask = task
        var switchedCdn = false
        var needsFreshUrl = false

        try {
            for (round in 0..1) {
                val primary = if (isVideoStream) activeTask.videoUrl else activeTask.audioUrl
                val backups = if (isVideoStream) activeTask.videoBackupUrls else activeTask.audioBackupUrls
                val candidates = downloadCdnCandidates(applyCdnPreference(primary), primary, backups)
                    .withIndex()
                    .filterNot { it.value in attempted }
                    .take(MAX_DOWNLOAD_CDN_CANDIDATES)

                for ((index, url) in candidates) {
                    currentCoroutineContext().ensureActive()
                    ensureTaskCanRun(task.id, task.workToken)
                    attempted.add(url)
                    if (index > 0) switchedCdn = true
                    try {
                        val result = downloadVerifiedCandidate(url, file, task, kind, onProgress)
                        if (switchedCdn) {
                            val outcome = if (index > 0) DownloadOutcome.COMPLETED else DownloadOutcome.FAILED
                            telemetry.record(
                                DownloadStage.CDN_FALLBACK, outcome,
                                error = lastError.takeIf { outcome == DownloadOutcome.FAILED },
                            )
                        }
                        return result
                    } catch (error: Exception) {
                        if (!shouldSwitchDownloadCdn(error)) throw error
                        lastError = error
                        if (error is DownloadTrackIntegrityException) integrityError = error
                        if (isRetryableDownloadFailure(error)) {
                            retryableError = error
                        } else {
                            needsFreshUrl = true
                        }
                    }
                }

                if (round == 0) {
                    // 旧任务未保存备用地址，刷新一次即可取得当前轨道的线路。
                    if (candidates.isNotEmpty() && backups.isNotEmpty() && !needsFreshUrl) break
                    currentCoroutineContext().ensureActive()
                    ensureTaskCanRun(task.id, task.workToken)
                    activeTask = refreshTaskDownloadUrls(activeTask, isVideoStream)
                }
            }
            throw retryableError ?: integrityError ?: lastError
                ?: DownloadStreamUnavailableException(if (isVideoStream) "视频地址为空" else "音频地址为空")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (switchedCdn) telemetry.record(DownloadStage.CDN_FALLBACK, DownloadOutcome.FAILED, error)
            throw error
        }
    }
    
    /**
     * 使用 Android MediaMuxer 合并音视频
     * 将分离的视频流和音频流合并为完整的 MP4 文件
     */
    @android.annotation.SuppressLint("WrongConstant")
    private suspend fun mergeVideoAudio(video: File?, audio: File, output: File) = withContext(Dispatchers.IO) {
        // 提取器/Muxer 提升到 try 外，异常路径也能释放，避免泄漏 native 资源
        var muxer: android.media.MediaMuxer? = null
        val videoExtractor = android.media.MediaExtractor()
        val audioExtractor = android.media.MediaExtractor()
        try {
            com.android.purebilibili.core.util.Logger.d("DownloadManager", " Starting MediaMuxer merge...")

            // 创建 MediaMuxer
            muxer = android.media.MediaMuxer(
                output.absolutePath,
                android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            // 提取视频轨道 (仅当 video 不为空时)
            var videoTrackMaxInputSize = -1
            if (video != null) {
                videoExtractor.setDataSource(video.absolutePath)
            }
            var videoTrackIndex = -1
            var videoMuxerTrackIndex = -1
            
            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoExtractor.selectTrack(i)
                    videoMuxerTrackIndex = muxer!!.addTrack(format)
                    videoTrackIndex = i
                    if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        videoTrackMaxInputSize = format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
                    }
                    break
                }
            }
            
            // 如果仅音频且 video 为空，跳过视频轨检查
            if (video != null && videoTrackIndex == -1) {
                 // Video file exists but no track found?
            }
            
            // 提取音频轨道
            audioExtractor.setDataSource(audio.absolutePath)
            var audioTrackIndex = -1
            var audioMuxerTrackIndex = -1
            var audioTrackMaxInputSize = -1
            
            for (i in 0 until audioExtractor.trackCount) {
                val format = audioExtractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioExtractor.selectTrack(i)
                    audioMuxerTrackIndex = muxer!!.addTrack(format)
                    audioTrackIndex = i
                    if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        audioTrackMaxInputSize = format.getInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
                    }
                    break
                }
            }
            
            if ((video != null && videoTrackIndex == -1) || audioTrackIndex == -1) {
                com.android.purebilibili.core.util.Logger.e("DownloadManager", " Failed to find video or audio track")
                throw IllegalStateException(
                    "音视频轨道解析失败（videoTrack=$videoTrackIndex, audioTrack=$audioTrackIndex）"
                )
            }
            
            // 开始合并
            muxer!!.start()
            
            val buffer = java.nio.ByteBuffer.allocate(
                resolveMuxerSampleBufferSize(
                    maxInputSizes = listOf(videoTrackMaxInputSize, audioTrackMaxInputSize)
                )
            )
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            
            // 写入视频数据 (如果有)
            if (video != null && videoTrackIndex != -1 && videoMuxerTrackIndex != -1) {
                while (true) {
                    ensureActive()
                    buffer.clear()
                    val sampleSize = videoExtractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    
                    bufferInfo.offset = 0
                    bufferInfo.size = sampleSize
                    bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                    bufferInfo.flags = videoExtractor.sampleFlags
                    
                    muxer!!.writeSampleData(videoMuxerTrackIndex, buffer, bufferInfo)
                    videoExtractor.advance()
                }
            }
            
            // 写入音频数据
            while (true) {
                ensureActive()
                buffer.clear()
                val sampleSize = audioExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                
                bufferInfo.offset = 0
                bufferInfo.size = sampleSize
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                
                muxer!!.writeSampleData(audioMuxerTrackIndex, buffer, bufferInfo)
                audioExtractor.advance()
            }
            
            // 清理
            videoExtractor.release()
            audioExtractor.release()
            muxer!!.stop()
            muxer!!.release()
            
            com.android.purebilibili.core.util.Logger.d("DownloadManager", " MediaMuxer merge completed: ${output.name}")
            
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e("DownloadManager", " MediaMuxer merge failed", e)
            runCatching { muxer?.release() }
            videoExtractor.release()
            audioExtractor.release()
            // 删除半成品并向上抛出：任务转入可重试的失败态，
            // 避免静默产出无音轨文件或（纯音频任务）空文件被标记为已完成
            output.delete()
            if (e is CancellationException) throw e
            throw IllegalStateException("音视频合成失败: ${e.message}", e)
        }
    }
    
    private fun getTaskDir(taskId: String): File {
        val task = _tasks.value[taskId]
        val externalRoot = appContext?.let(::safeExternalFilesRoot)
        val appScopedRoot = externalRoot?.absolutePath
        val legacyCustomPath = if (appScopedRoot != null) {
            sanitizeLegacyCustomPath(task?.customSaveDir, appScopedRoot)
        } else {
            null
        }

        val dir = legacyCustomPath?.let { File(it) } ?: getDownloadDir()
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getVideoFile(taskId: String) = File(getTaskDir(taskId), "${taskId}_video.m4s")
    private fun getAudioFile(taskId: String) = File(getTaskDir(taskId), "${taskId}_audio.m4s")
    private fun getOutputFile(taskId: String): File {
        val task = _tasks.value[taskId]
        val extension = if (task?.isAudioOnly == true) "m4a" else "mp4"
        return File(getTaskDir(taskId), "${taskId}.$extension")
    }
    private fun getCoverFile(taskId: String) = File(getTaskDir(taskId), "${taskId}_cover.jpg")
    
    /**
     * 🖼️ [新增] 下载封面图片
     */
    private suspend fun downloadCoverImage(coverUrl: String, file: File) = withContext(Dispatchers.IO) {
        val url = coverUrl.let {
            if (it.startsWith("http://")) it.replace("http://", "https://") else it
        }
        
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Referer", "https://www.bilibili.com")
            .build()
        
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }
        
        response.body.byteStream().use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
    }
    
    /** Once a control action changes memory, finish its persistence and scheduling together. */
    private suspend fun <T> withTaskControl(action: suspend () -> T): T =
        taskControlMutex.withLock {
            withContext(Dispatchers.IO + NonCancellable) { action() }
        }

    private suspend fun updateTask(
        taskId: String,
        update: (DownloadTask) -> DownloadTask
    ) = withContext(NonCancellable) {
        if (updateTaskInMemory(taskId, checkPersistence = true, update = update)) saveTasks()
    }

    /** Callback-safe: progress updates never serialize or access the task file. */
    private fun updateTaskInMemory(
        taskId: String,
        checkPersistence: Boolean = false,
        update: (DownloadTask) -> DownloadTask
    ): Boolean {
        var shouldPersist = false
        _tasks.update { tasks ->
            val current = tasks[taskId] ?: return false
            val updated = sanitizeDownloadTask(update(current))
            shouldPersist = checkPersistence && shouldPersistDownloadTaskUpdate(current, updated)
            if (updated == current) tasks else tasks + (taskId to updated)
        }
        return shouldPersist
    }

    private suspend fun updateAssetState(
        taskId: String,
        kind: DownloadAssetKind,
        update: (DownloadAssetState) -> DownloadAssetState
    ) {
        updateTask(taskId) { task ->
            val current = task.assetState(kind) ?: DownloadAssetState(kind = kind)
            task.withAssetState(update(current))
        }
    }

    private fun buildDownloadAssetProgressState(
        kind: DownloadAssetKind,
        file: File,
        downloadedBytes: Long,
        totalBytes: Long
    ): DownloadAssetState {
        val partFile = File(file.parentFile, "${file.name}.part")
        return DownloadAssetState(
            kind = kind,
            status = DownloadAssetStatus.DOWNLOADING,
            totalBytes = totalBytes.coerceAtLeast(downloadedBytes),
            downloadedBytes = downloadedBytes.coerceAtLeast(0L),
            filePath = file.absolutePath,
            tempPath = partFile.absolutePath
        )
    }

    private suspend fun refreshTaskDownloadUrls(
        task: DownloadTask,
        isVideoStream: Boolean? = null,
    ): DownloadTask {
        val telemetry = DownloadTelemetry()
        return try {
            val requestedQuality = task.quality.takeIf { it > 0 } ?: 64
            val episodeId = task.pgcEpisodeId.takeIf { it > 0L }
                ?: task.bvid.takeIf { it.startsWith("ep") }?.substring(2)?.toLongOrNull()
            val dash = if (episodeId != null) {
                BangumiRepository.getBangumiPlayUrl(
                    epId = episodeId,
                    qn = requestedQuality,
                    cid = task.cid,
                    bvid = task.bvid.takeIf { it.startsWith("BV", ignoreCase = true) },
                    seasonId = task.pgcSeasonId.takeIf { it > 0L },
                    aid = task.aid,
                    isCourse = task.isCourse,
                ).getOrThrow().takeUnless { it.isDrm || it.isPreview }?.dash
            } else {
                VideoRepository.getPlayUrlDataForDownload(task.bvid, task.cid, requestedQuality)?.dash
            }
            if (dash == null) {
                telemetry.record(DownloadStage.STREAM_REFRESH, DownloadOutcome.FAILED, unavailable = true)
                return task
            }
            val video = if (isVideoStream != false && !task.isAudioOnly) {
                val paths = (listOf(task.videoUrl) + task.videoBackupUrls).map { Uri.parse(it).encodedPath }
                val exactTracks = dash.video.filter { it.id == task.quality && it.getValidUrl().isNotBlank() }
                exactTracks.firstOrNull {
                    (listOf(it.getValidUrl()) + it.downloadBackupUrls()).any { url ->
                        Uri.parse(url).encodedPath in paths
                    }
                } ?: dash.copy(video = exactTracks).getBestVideo(task.quality)
            } else null
            val audio = if (isVideoStream != true) {
                val paths = (listOf(task.audioUrl) + task.audioBackupUrls).map { Uri.parse(it).encodedPath }
                dash.allDownloadAudioTracks().firstOrNull {
                    it.getValidUrl().isNotBlank() &&
                        (listOf(it.getValidUrl()) + it.downloadBackupUrls()).any { url ->
                            Uri.parse(url).encodedPath in paths
                        }
                } ?: dash.getBestAudio()
            } else null
            val refreshedTask = task.copy(
                videoUrl = video?.getValidUrl() ?: task.videoUrl,
                audioUrl = audio?.getValidUrl() ?: task.audioUrl,
                videoBackupUrls = video?.downloadBackupUrls() ?: task.videoBackupUrls,
                audioBackupUrls = audio?.downloadBackupUrls() ?: task.audioBackupUrls,
                urlsRefreshedAt = if (audio != null && (task.isAudioOnly || video != null)) {
                    System.currentTimeMillis()
                } else task.urlsRefreshedAt,
            )
            if (refreshedTask != task) {
                updateWorkerTask(task.id, task.workToken) {
                    it.copy(
                        videoUrl = refreshedTask.videoUrl,
                        audioUrl = refreshedTask.audioUrl,
                        videoBackupUrls = refreshedTask.videoBackupUrls,
                        audioBackupUrls = refreshedTask.audioBackupUrls,
                        urlsRefreshedAt = refreshedTask.urlsRefreshedAt,
                    )
                }
            }
            val resolved = video != null || audio != null
            telemetry.record(
                DownloadStage.STREAM_REFRESH,
                if (resolved) DownloadOutcome.COMPLETED else DownloadOutcome.FAILED,
                unavailable = !resolved,
            )
            refreshedTask
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            com.android.purebilibili.core.util.Logger.w(
                "DownloadManager",
                "⚠️ Failed to refresh download URLs for task=${task.id}",
                error
            )
            telemetry.record(DownloadStage.STREAM_REFRESH, DownloadOutcome.FAILED, error)
            task
        }
    }

    private suspend fun enqueueDownload(taskId: String): Boolean {
        val context = checkNotNull(appContext)
        val workToken = UUID.randomUUID().toString()
        updateTask(taskId) { current ->
            if (current.status == DownloadStatus.QUEUED && !current.isWaitingForRetry) {
                current.copy(status = DownloadStatus.PENDING, errorMessage = null, workToken = workToken)
            } else current
        }
        if (!ownsActiveWork(taskId, workToken)) return false
        val telemetry = DownloadTelemetry()
        return try {
            DownloadWorker.enqueue(context, taskId, workToken)
            telemetry.record(DownloadStage.SCHEDULING, DownloadOutcome.ACCEPTED)
            true
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            updateWorkerTask(taskId, workToken) {
                it.copy(status = DownloadStatus.FAILED, errorMessage = "无法调度下载：${error.message.orEmpty()}")
            }
            telemetry.record(DownloadStage.SCHEDULING, DownloadOutcome.FAILED, error)
            false
        }
    }

    private suspend fun scheduleNextQueuedDownload() = withContext(Dispatchers.IO + NonCancellable) {
        schedulingMutex.withLock {
            while (true) {
                val next = resolveNextQueuedDownloadTaskIds(_tasks.value.values)
                if (next.isEmpty()) break
                var deferred = false
                for (taskId in next) if (!enqueueDownload(taskId)) deferred = true
                if (!deferred) break
            }
        }
    }

    private fun ensureTaskCanRun(taskId: String, workToken: String?) {
        if (!ownsActiveWork(taskId, workToken)) throw CancellationException("下载任务已停止：$taskId")
    }

    private fun loadTasks() {
        try {
            val file = tasksFile ?: return
            val atomicFile = AtomicFile(file)
            // 旧备份也交给原子读取恢复，避免主文件缺失时丢掉可恢复的任务。
            if (file.exists() || File(file.path + ".bak").exists()) {
                val content = atomicFile.openRead().bufferedReader().use { it.readText() }
                val list = json.decodeFromString<List<DownloadTask>>(content)
                _tasks.value = list
                    .map(::normalizeRestoredDownloadTask)
                    .associateBy { it.id }
            }
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e("DownloadManager", "Failed to load tasks", e)
        }
    }
    
    private suspend fun saveTasks() = withContext(Dispatchers.IO + NonCancellable) {
        persistenceMutex.withLock {
            try {
                val file = tasksFile ?: return@withLock
                // Read after acquiring the writer lock: queued saves cannot overwrite newer state.
                val snapshot = _tasks.value
                if (snapshot == lastPersistedTasks) return@withLock
                val content = json.encodeToString(snapshot.values.toList())
                val parent = file.parentFile ?: return@withLock
                if (!parent.exists()) parent.mkdirs()
                val atomicFile = AtomicFile(file)
                val output = atomicFile.startWrite()
                try {
                    output.write(content.toByteArray(Charsets.UTF_8))
                    atomicFile.finishWrite(output)
                } catch (failure: Throwable) {
                    atomicFile.failWrite(output)
                    throw failure
                }
                lastPersistedTasks = snapshot
            } catch (e: Exception) {
                com.android.purebilibili.core.util.Logger.e("DownloadManager", "Failed to save tasks", e)
            }
        }
    }

    /**
     * 解析下载目录 (已提取为辅助方法)
     */
    private fun resolveDownloadDir(context: Context, customPath: String?): File {
        return try {
            val resolvedDir = resolveManagedDownloadDirectory(
                filesDir = context.filesDir,
                externalFilesRoot = safeExternalFilesRoot(context),
                customPath = customPath
            )
            if (customPath.isNullOrBlank()) {
                com.android.purebilibili.core.util.Logger.d(
                    "DownloadManager",
                    "✅ Resolved managed dir: ${resolvedDir.absolutePath}"
                )
            } else if (resolvedDir.absolutePath == customPath) {
                com.android.purebilibili.core.util.Logger.d(
                    "DownloadManager",
                    "✅ Resolved custom dir: ${resolvedDir.absolutePath}"
                )
            } else {
                com.android.purebilibili.core.util.Logger.w(
                    "DownloadManager",
                    "⚠️ Custom path unavailable/writable, using fallback: $customPath -> ${resolvedDir.absolutePath}"
                )
            }
            resolvedDir
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e("DownloadManager", "❌ Failed to resolve custom path: $customPath", e)
            File(context.filesDir, "downloads").apply { mkdirs() }
        }
    }
    
    /**
     * 🖼️ [新增] 保存图片到相册
     */
    suspend fun saveImageToGallery(context: Context, url: String, title: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. 下载图片
             val request = Request.Builder()
                .url(url.replace("http://", "https://"))
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body
            val bytes = body.bytes()
            
            // 2. 插入 MediaStore
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$title.jpg")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BiliPai")
                }
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) 
                ?: return@withContext false
                
            resolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            
            return@withContext true
        } catch (e: Exception) {
            com.android.purebilibili.core.util.Logger.e("DownloadManager", "Failed to save image", e)
            return@withContext false
        }
    }

    private fun deleteExportedFile(context: Context, exportedUri: String) {
        runCatching {
            DocumentFile.fromSingleUri(context, Uri.parse(exportedUri))?.delete()
        }.onFailure { e ->
            com.android.purebilibili.core.util.Logger.w(
                "DownloadManager",
                "⚠️ Failed to delete exported file: $exportedUri",
                e
            )
        }
    }

    private suspend fun exportToUserSelectedDirectory(
        context: Context,
        sourceFile: File,
        title: String,
        qualityDesc: String,
        isAudioOnly: Boolean
    ): String? {
        val treeUriString = com.android.purebilibili.core.store.SettingsManager
            .getDownloadExportTreeUriSync(context)
            ?: return null

        val exportContext = currentCoroutineContext()
        val telemetry = DownloadTelemetry()
        return runCatching {
            val treeUri = Uri.parse(treeUriString)
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw IOException("导出目录不可用")

            val extension = if (isAudioOnly) "m4a" else "mp4"
            val mimeType = if (isAudioOnly) "audio/mp4" else "video/mp4"
            val displayName = buildSafeExportDisplayName(
                title = title,
                qualityDesc = qualityDesc,
                extension = extension
            )

            treeDoc.findFile(displayName)?.delete()
            val target = treeDoc.createFile(mimeType, displayName)
                ?: throw IOException("无法创建导出文件")

            try {
                sourceFile.inputStream().use { input ->
                    val stream = context.contentResolver.openOutputStream(target.uri, "w")
                        ?: throw IOException("无法打开导出文件")
                    stream.use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            exportContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
                exportContext.ensureActive()
                target.uri.toString()
            } catch (error: Exception) {
                runCatching { target.delete() }
                throw error
            }
        }.onSuccess {
            telemetry.record(DownloadStage.EXPORT, DownloadOutcome.COMPLETED)
        }.onFailure { e ->
            if (e is CancellationException) throw e
            telemetry.record(DownloadStage.EXPORT, DownloadOutcome.FAILED, e)
            com.android.purebilibili.core.util.Logger.w(
                "DownloadManager",
                "⚠️ Failed to export to SAF directory",
                e
            )
        }.getOrNull()
    }
}
