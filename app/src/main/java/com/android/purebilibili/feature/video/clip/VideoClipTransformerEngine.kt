@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.clip

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.android.purebilibili.MainActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Clock
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultDecoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExoPlayerAssetLoader
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.notification.liveupdate.LiveUpdateDownloadState
import com.android.purebilibili.core.notification.liveupdate.LiveUpdateNotificationFactory
import com.android.purebilibili.core.player.PlaybackMediaCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 基于 AndroidX Media3 Transformer 1.11.1 的硬件切片与纯音频提取引擎：
 * - 复用 PlaybackMediaCache + 带 Referer 请求头的 OkHttpDataSource；
 * - 支持 B 站 DASH 音视频分离轨并行混流导出 MP4 或无视频高速提取 M4A；
 * - 导出进度通过 Live Updates 通知实时呈现，结果原子落盘至系统 MediaStore。
 */
internal object VideoClipTransformerEngine {

    private const val CLIP_EXPORT_NOTIFICATION_ID = 43_025

    suspend fun exportClip(
        context: Context,
        videoTitle: String,
        videoUrl: String,
        audioUrl: String,
        range: VideoClipRange,
        mode: VideoClipExportMode,
        onProgress: (Int) -> Unit = {},
    ): Uri {
        val appContext = context.applicationContext ?: context
        val clipDir = File(appContext.cacheDir, "media3_clips").apply { mkdirs() }
        val tempFile = File(clipDir, "clip_${System.currentTimeMillis()}.${mode.extension}")
        val displayName = buildClipOutputDisplayName(
            videoTitle = videoTitle,
            startMs = range.startMs,
            endMs = range.endMs,
            mode = mode,
        )
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val returnToAppIntent = PendingIntent.getActivity(
            appContext,
            CLIP_EXPORT_NOTIFICATION_ID,
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        fun postProgressNotification(percent: Int) {
            if (notificationManager == null) return
            val notification = LiveUpdateNotificationFactory.buildDownloadNotification(
                context = appContext,
                state = LiveUpdateDownloadState(
                    title = "正在导出：${videoTitle.ifBlank { displayName }}",
                    progressPercent = percent,
                    remainingCount = 1,
                    paused = false,
                ),
                contentIntent = returnToAppIntent,
            )
            notificationManager.notify(CLIP_EXPORT_NOTIFICATION_ID, notification)
        }

        try {
            postProgressNotification(0)
            runTransformerOnMainThread(
                context = appContext,
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                range = range,
                mode = mode,
                outputPath = tempFile.absolutePath,
                onProgress = { pct ->
                    onProgress(pct)
                    postProgressNotification(pct)
                },
            )
            onProgress(100)
            return withContext(Dispatchers.IO) {
                persistClipToMediaStore(
                    context = appContext,
                    sourceFile = tempFile,
                    displayName = displayName,
                    mode = mode,
                )
            }
        } finally {
            notificationManager?.cancel(CLIP_EXPORT_NOTIFICATION_ID)
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private suspend fun runTransformerOnMainThread(
        context: Context,
        videoUrl: String,
        audioUrl: String,
        range: VideoClipRange,
        mode: VideoClipExportMode,
        outputPath: String,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.Main.immediate) {
        val headers = mapOf(
            "Referer" to "https://www.bilibili.com",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
        )
        val upstreamFactory = OkHttpDataSource.Factory(NetworkModule.playbackOkHttpClient)
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = PlaybackMediaCache.buildCachedDataSourceFactory(context, upstreamFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val decoderFactory = DefaultDecoderFactory.Builder(context)
            .setEnableDecoderFallback(true)
            .build()
        val assetLoaderFactory = ExoPlayerAssetLoader.Factory(
            context,
            decoderFactory,
            Clock.DEFAULT,
            mediaSourceFactory,
        )

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(range.startMs)
            .setEndPositionMs(range.endMs)
            .build()

        val composition = buildClipComposition(
            videoUrl = videoUrl,
            audioUrl = audioUrl,
            clipping = clipping,
            mode = mode,
        )

        var transformer: Transformer? = null
        val progressJob = launch {
            val holder = ProgressHolder()
            while (isActive) {
                delay(300L)
                val active = transformer ?: continue
                if (active.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    onProgress(holder.progress.coerceIn(0, 99))
                }
            }
        }
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val instance = Transformer.Builder(context)
                    .setLooper(Looper.getMainLooper())
                    .setAssetLoaderFactory(assetLoaderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            if (continuation.isActive) {
                                continuation.resume(Unit)
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(exportException)
                            }
                        }
                    })
                    .build()
                transformer = instance
                continuation.invokeOnCancellation {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        runCatching { instance.cancel() }
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            runCatching { instance.cancel() }
                        }
                    }
                }
                instance.start(composition, outputPath)
            }
        } finally {
            progressJob.cancel()
        }
    }

    private fun buildClipComposition(
        videoUrl: String,
        audioUrl: String,
        clipping: MediaItem.ClippingConfiguration,
        mode: VideoClipExportMode,
    ): Composition {
        val sequences = mutableListOf<EditedMediaItemSequence>()
        when (mode) {
            VideoClipExportMode.MP4_VIDEO -> {
                require(videoUrl.isNotBlank()) { "当前视频流地址不可用" }
                val hasSeparateAudio = audioUrl.isNotBlank() && audioUrl != videoUrl
                val videoItem = MediaItem.Builder()
                    .setUri(videoUrl)
                    .setClippingConfiguration(clipping)
                    .build()
                val editedVideo = EditedMediaItem.Builder(videoItem)
                    .setRemoveAudio(hasSeparateAudio)
                    .build()
                sequences += EditedMediaItemSequence.Builder(editedVideo).build()

                if (hasSeparateAudio) {
                    val audioItem = MediaItem.Builder()
                        .setUri(audioUrl)
                        .setClippingConfiguration(clipping)
                        .build()
                    val editedAudio = EditedMediaItem.Builder(audioItem)
                        .setRemoveVideo(true)
                        .build()
                    sequences += EditedMediaItemSequence.Builder(editedAudio).build()
                }
            }
            VideoClipExportMode.M4A_AUDIO -> {
                val targetUrl = audioUrl.ifBlank { videoUrl }
                require(targetUrl.isNotBlank()) { "当前音频流地址不可用" }
                val audioItem = MediaItem.Builder()
                    .setUri(targetUrl)
                    .setClippingConfiguration(clipping)
                    .build()
                val editedAudio = EditedMediaItem.Builder(audioItem)
                    .setRemoveVideo(true)
                    .build()
                sequences += EditedMediaItemSequence.Builder(editedAudio).build()
            }
        }
        return Composition.Builder(sequences).build()
    }

    private fun persistClipToMediaStore(
        context: Context,
        sourceFile: File,
        displayName: String,
        mode: VideoClipExportMode,
    ): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val (collectionUri, relativeDir) = when (mode) {
                VideoClipExportMode.MP4_VIDEO ->
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/BiliPai/Clips"
                VideoClipExportMode.M4A_AUDIO ->
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MUSIC}/BiliPai/Clips"
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mode.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val insertedUri = resolver.insert(collectionUri, values)
                ?: error("无法创建系统媒体库记录")
            try {
                resolver.openOutputStream(insertedUri)?.use { output ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                } ?: error("无法写入系统媒体库")
                val doneValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(insertedUri, doneValues, null, null)
                return insertedUri
            } catch (cancelled: CancellationException) {
                resolver.delete(insertedUri, null, null)
                throw cancelled
            } catch (error: Exception) {
                resolver.delete(insertedUri, null, null)
                throw error
            }
        }

        val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Clips").apply { mkdirs() }
        val target = File(fallbackDir, displayName)
        sourceFile.copyTo(target, overwrite = true)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
    }
}
