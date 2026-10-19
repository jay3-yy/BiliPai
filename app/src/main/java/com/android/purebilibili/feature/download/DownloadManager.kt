package com.android.purebilibili.feature.download

import com.android.purebilibili.core.network.buildAppDns
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.android.purebilibili.data.model.response.getBestAudio
import com.android.purebilibili.data.model.response.getBestVideo
import com.android.purebilibili.data.repository.VideoRepository
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

private const val DEFAULT_MUXER_SAMPLE_BUFFER_SIZE_BYTES = 1024 * 1024
private val PARTIAL_CONTENT_RANGE_REGEX = Regex("""bytes (\d+)-(\d+)/(?:\d+|\*)""")

internal fun isValidPartialContentResponse(
    responseCode: Int,
    contentRange: String?,
    requestedStart: Long,
    requestedEnd: Long
): Boolean {
    if (responseCode != 206) return false
    val match = PARTIAL_CONTENT_RANGE_REGEX.matchEntire(contentRange?.trim().orEmpty()) ?: return false
    val actualStart = match.groupValues[1].toLongOrNull() ?: return false
    val actualEnd = match.groupValues[2].toLongOrNull() ?: return false
    return actualStart == requestedStart && actualEnd == requestedEnd
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
        .build()
    private val assetDownloader = ResumableAssetDownloader(client)
    
    private val initializationMutex = Mutex()
    private val initializationReady = CompletableDeferred<Unit>()
    private val persistenceMutex = Mutex()
    private val schedulingMutex = Mutex()
    private val taskControlMutex = Mutex()
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
        initializationReady.await()
        return withTaskControl {
            val newTask = task.copy(status = DownloadStatus.QUEUED, errorMessage = null)
            while (true) {
                val current = _tasks.value
                val existing = current[task.id]
                if (existing != null && existing.status != DownloadStatus.FAILED && existing.status != DownloadStatus.PAUSED) {
                    return@withTaskControl false
                }
                if (_tasks.compareAndSet(current, current + (task.id to newTask))) break
            }
            saveTasks()
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
            updateTask(taskId) { it.copy(status = DownloadStatus.QUEUED, errorMessage = null) }
            scheduleNextQueuedDownload()
        }
    }
    
    /**
     * 🔧 [新增] 执行下载（由 WorkManager 调用）
     * @throws Exception 下载失败时抛出异常
     */
    suspend fun executeDownload(taskId: String) {
        initializationReady.await()
        val task = _tasks.value[taskId] 
            ?: throw IllegalStateException("任务不存在: $taskId")
        if (task.status != DownloadStatus.PENDING && task.status != DownloadStatus.DOWNLOADING) {
            throw CancellationException("任务未处于可执行状态: ${task.status}")
        }
        downloadTask(taskId)
    }
    
    /**
     * 🔧 [新增] 标记下载失败（由 WorkManager 调用）
     */
    suspend fun markFailed(taskId: String, errorMessage: String) {
        initializationReady.await()
        updateTask(taskId) {
            it.copy(status = DownloadStatus.FAILED, errorMessage = errorMessage)
        }
        scheduleNextQueuedDownload()
    }

    fun shouldTreatWorkerCancellationAsFinished(taskId: String): Boolean {
        val status = _tasks.value[taskId]?.status
        return resolveDownloadWorkerCancellationDecision(status) == DownloadWorkerCancellationDecision.FINISH
    }

    suspend fun markInterruptedForRetry(taskId: String, errorMessage: String) {
        initializationReady.await()
        updateTask(taskId) { task ->
            when (task.status) {
                DownloadStatus.PAUSED,
                DownloadStatus.COMPLETED -> task
                else -> task.copy(status = DownloadStatus.PENDING, errorMessage = errorMessage)
            }
        }
    }

    suspend fun pauseForSystemForegroundTimeout(taskId: String) {
        initializationReady.await()
        updateTask(taskId) { task ->
            when (task.status) {
                DownloadStatus.COMPLETED,
                DownloadStatus.PAUSED -> task
                else -> task.copy(
                    status = DownloadStatus.PAUSED,
                    errorMessage = "下载已暂停：Android 15 后台前台服务接近系统时限，请稍后手动继续"
                )
            }
        }
    }
    
    /**
     * 暂停下载
     */
    suspend fun pauseDownload(taskId: String) {
        initializationReady.await()
        val context = appContext ?: return
        withTaskControl {
            updateTask(taskId) { it.copy(status = DownloadStatus.PAUSED) }
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
    private suspend fun downloadTask(taskId: String) {
        var task = _tasks.value[taskId] ?: throw IllegalStateException("任务不存在: $taskId")
        updateTask(task.id) { it.copy(status = DownloadStatus.DOWNLOADING) }

        // 串行队列里排了很久的任务，入队时解析的 DASH 地址大概率已过期
        if (shouldRefreshStaleDownloadUrls(
                createdAtMs = task.createdAt,
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
        
        // 1. 下载视频流 (如果不是仅音频模式)
        if (!task.isAudioOnly) {
            val videoResult = downloadFileWithUrlRefresh(task, isVideoStream = true, file = videoFile) { progress ->
                // 如果不仅音频，总进度 = (video + audio) / 2
                updateTaskInMemory(task.id) {
                    it.copy(videoProgress = progress, progress = (progress + it.audioProgress) / 2)
                        .withAssetState(
                            buildDownloadAssetProgressState(
                                kind = DownloadAssetKind.VIDEO,
                                file = videoFile,
                                progress = progress
                            )
                        )
                }
            }
            updateTaskInMemory(task.id) {
                it.withAssetState(
                    DownloadAssetState(
                        kind = DownloadAssetKind.VIDEO,
                        status = DownloadAssetStatus.COMPLETED,
                        totalBytes = videoResult.totalBytes,
                        downloadedBytes = videoResult.downloadedBytes,
                        filePath = videoFile.absolutePath,
                        segmentCount = videoResult.segmentCount
                    )
                )
            }
        } else {
             updateTaskInMemory(task.id) {
                 it.copy(videoProgress = 1f)
                     .withAssetState(DownloadAssetState(kind = DownloadAssetKind.VIDEO, status = DownloadAssetStatus.SKIPPED))
             }
        }
        
        // 2. 下载音频流
        val audioResult = downloadFileWithUrlRefresh(task, isVideoStream = false, file = audioFile) { progress ->
            updateTaskInMemory(task.id) {
                val totalProgress = if (task.isAudioOnly) progress else (it.videoProgress + progress) / 2
                it.copy(audioProgress = progress, progress = totalProgress)
                    .withAssetState(
                        buildDownloadAssetProgressState(
                            kind = DownloadAssetKind.AUDIO,
                            file = audioFile,
                            progress = progress
                        )
                    )
            }
        }
        updateTaskInMemory(task.id) {
            it.withAssetState(
                DownloadAssetState(
                    kind = DownloadAssetKind.AUDIO,
                    status = DownloadAssetStatus.COMPLETED,
                    totalBytes = audioResult.totalBytes,
                    downloadedBytes = audioResult.downloadedBytes,
                    filePath = audioFile.absolutePath,
                    segmentCount = audioResult.segmentCount
                )
            )
        }
        
        // 3. 合并音视频 (或直接处理音频)
        updateTask(task.id) { it.copy(status = DownloadStatus.MERGING, progress = 0.95f) }
        if (outputFile.exists()) {
            outputFile.delete()
        }
        
        if (task.isAudioOnly) {
            // 仅音频模式：直接将音频文件作为输出（或者是转换为 m4a/mp3，这里直接用音频流）
            // B站音频流通常是 m4s (AAC) 或 m4a。直接改名或复制。
            // 为了兼容性，封装进 MP4 容器（即使只有音频轨）通常更安全，或者直接复制。
            // 简单起见，尝试直接复制。注意后缀名问题。现在 outputFile 是 .mp4。
            // 使用 MediaMuxer 仅封装音频轨也是一种方法，能保证 metadata 正确。
            mergeVideoAudio(null, audioFile, outputFile)
        } else {
            mergeVideoAudio(videoFile, audioFile, outputFile)
        }
        
        // 4. 清理临时文件
        videoFile.delete()
        audioFile.delete()
        
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
        updateTask(task.id) { 
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
        _tasks.value[task.id]?.takeIf { it.status == DownloadStatus.COMPLETED }?.let { completed ->
            com.android.purebilibili.core.events.BrandSuccessEvents.downloadCompleted(
                taskId = completed.id,
                createdAt = completed.createdAt,
                title = completed.title
            )
        }
        scheduleNextQueuedDownload()
        
        com.android.purebilibili.core.util.Logger.d("DownloadManager", "✅ Download completed: ${task.title} (AudioOnly: ${task.isAudioOnly})")
    }
    
    /**
     * 单线程下载单个文件，支持基于已有临时文件的续传。
     */
    private suspend fun downloadFile(
        url: String, 
        file: File, 
        taskId: String,
        onProgress: (Float) -> Unit
    ): HttpDownloadAssetResult = withContext(Dispatchers.IO) {
        ensureTaskCanRun(taskId)
        assetDownloader.download(
            request = HttpDownloadAssetRequest(
                url = url,
                outputFile = file,
                headers = buildBilibiliDownloadHeaders()
            ),
            ensureActive = { ensureTaskCanRun(taskId) },
            onProgress = { downloadedBytes, totalBytes ->
                if (totalBytes > 0L) {
                    onProgress(downloadedBytes.toFloat() / totalBytes.toFloat())
                }
            }
        )
    }

    private suspend fun downloadFileWithUrlRefresh(
        task: DownloadTask,
        isVideoStream: Boolean,
        file: File,
        onProgress: (Float) -> Unit
    ): HttpDownloadAssetResult {
        var activeTask = task
        var initialUrl = if (isVideoStream) activeTask.videoUrl else activeTask.audioUrl
        if (initialUrl.isBlank()) {
            activeTask = refreshTaskDownloadUrls(activeTask)
            initialUrl = if (isVideoStream) activeTask.videoUrl else activeTask.audioUrl
        }
        if (initialUrl.isBlank()) {
            throw IllegalStateException(if (isVideoStream) "视频地址为空" else "音频地址为空")
        }

        try {
            return downloadFile(initialUrl, file, task.id, onProgress)
        } catch (error: Exception) {
            if (!shouldRefreshDownloadUrlAfterFailure(error)) throw error

            val refreshedTask = refreshTaskDownloadUrls(activeTask)
            val refreshedUrl = if (isVideoStream) refreshedTask.videoUrl else refreshedTask.audioUrl
            if (refreshedUrl.isBlank() || refreshedUrl == initialUrl) {
                throw error
            }

            com.android.purebilibili.core.util.Logger.w(
                "DownloadManager",
                "🔄 Download URL expired, retrying with refreshed source: task=${task.id}, stream=${if (isVideoStream) "video" else "audio"}"
            )
            return downloadFile(refreshedUrl, file, task.id, onProgress)
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
        progress: Float
    ): DownloadAssetState {
        val partFile = File(file.parentFile, "${file.name}.part")
        val downloadedBytes = partFile.takeIf(File::exists)?.length() ?: file.takeIf(File::exists)?.length() ?: 0L
        val totalBytes = if (progress > 0f) {
            (downloadedBytes / progress).toLong()
        } else {
            0L
        }
        return DownloadAssetState(
            kind = kind,
            status = DownloadAssetStatus.DOWNLOADING,
            totalBytes = totalBytes.coerceAtLeast(downloadedBytes),
            downloadedBytes = downloadedBytes,
            filePath = file.absolutePath,
            tempPath = partFile.absolutePath
        )
    }

    private fun shouldRefreshDownloadUrlAfterFailure(error: Throwable): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return message.contains("http 401") ||
            message.contains("http 403") ||
            message.contains("http 416") ||
            message.contains("forbidden") ||
            message.contains("empty response")
    }

    private suspend fun refreshTaskDownloadUrls(task: DownloadTask): DownloadTask {
        return try {
            val requestedQuality = task.quality.takeIf { it > 0 } ?: 64
            val playUrlData = VideoRepository.getPlayUrlData(task.bvid, task.cid, requestedQuality)
                ?: return task
            val refreshedAudioUrl = playUrlData.dash?.getBestAudio()?.getValidUrl().orEmpty()
            val refreshedVideo = if (task.isAudioOnly) null else playUrlData.dash?.getBestVideo(task.quality)
            val refreshedVideoUrl = refreshedVideo?.getValidUrl().orEmpty()

            // 刷新后源站可能不再提供原画质：同步实际画质，避免文件与“1080P”标签不符
            val refreshedQualityDesc = refreshedVideo?.id
                ?.takeIf { it != task.quality }
                ?.let { id ->
                    com.android.purebilibili.data.model.VideoQuality.fromCode(id)?.description
                }
                ?: task.qualityDesc

            val refreshedTask = when {
                task.isAudioOnly && refreshedAudioUrl.isNotBlank() -> {
                    task.copy(audioUrl = refreshedAudioUrl)
                }
                !task.isAudioOnly && refreshedVideoUrl.isNotBlank() && refreshedAudioUrl.isNotBlank() -> {
                    task.copy(
                        videoUrl = refreshedVideoUrl,
                        audioUrl = refreshedAudioUrl,
                        quality = refreshedVideo?.id ?: task.quality,
                        qualityDesc = refreshedQualityDesc
                    )
                }
                else -> task
            }

            if (refreshedTask != task) {
                updateTaskInMemory(task.id) {
                    it.copy(
                        videoUrl = refreshedTask.videoUrl,
                        audioUrl = refreshedTask.audioUrl,
                        quality = refreshedTask.quality,
                        qualityDesc = refreshedTask.qualityDesc
                    )
                }
            }
            refreshedTask
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            com.android.purebilibili.core.util.Logger.w(
                "DownloadManager",
                "⚠️ Failed to refresh download URLs for task=${task.id}",
                error
            )
            task
        }
    }

    private suspend fun enqueueDownload(taskId: String) {
        val context = appContext ?: return
        val task = _tasks.value[taskId] ?: return
        if (task.status != DownloadStatus.QUEUED) return
        updateTask(taskId) { current ->
            if (current.status == DownloadStatus.QUEUED) {
                current.copy(status = DownloadStatus.PENDING, errorMessage = null)
            } else current
        }
        if (_tasks.value[taskId]?.status == DownloadStatus.PENDING) {
            DownloadWorker.enqueue(context, taskId)
        }
    }

    private suspend fun scheduleNextQueuedDownload() = withContext(Dispatchers.IO + NonCancellable) {
        schedulingMutex.withLock {
            resolveNextQueuedDownloadTaskIds(_tasks.value.values).forEach { enqueueDownload(it) }
        }
    }

    private fun ensureTaskCanRun(taskId: String) {
        val task = _tasks.value[taskId] ?: throw CancellationException("任务已不存在")
        if (!isDownloadTaskActive(task)) {
            throw CancellationException("任务已停止: ${task.status}")
        }
    }
    
    private fun loadTasks() {
        try {
            val file = tasksFile ?: return
            if (file.exists()) {
                val content = file.readText()
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
                val tempFile = File(parent, "${file.name}.tmp")
                tempFile.writeText(content)
                if (!tempFile.renameTo(file)) {
                    file.writeText(content)
                    tempFile.delete()
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

    private fun exportToUserSelectedDirectory(
        context: Context,
        sourceFile: File,
        title: String,
        qualityDesc: String,
        isAudioOnly: Boolean
    ): String? {
        val treeUriString = com.android.purebilibili.core.store.SettingsManager
            .getDownloadExportTreeUriSync(context)
            ?: return null

        return runCatching {
            val treeUri = Uri.parse(treeUriString)
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@runCatching null

            val extension = if (isAudioOnly) "m4a" else "mp4"
            val mimeType = if (isAudioOnly) "audio/mp4" else "video/mp4"
            val displayName = buildSafeExportDisplayName(
                title = title,
                qualityDesc = qualityDesc,
                extension = extension
            )

            treeDoc.findFile(displayName)?.delete()
            val target = treeDoc.createFile(mimeType, displayName)
                ?: return@runCatching null

            sourceFile.inputStream().use { input ->
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                    input.copyTo(output)
                } ?: return@runCatching null
            }
            target.uri.toString()
        }.onFailure { e ->
            com.android.purebilibili.core.util.Logger.w(
                "DownloadManager",
                "⚠️ Failed to export to SAF directory",
                e
            )
        }.getOrNull()
    }
}
