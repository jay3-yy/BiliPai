package com.android.purebilibili.feature.download

import kotlinx.serialization.Serializable

/**
 * 下载任务状态
 */
@Serializable
enum class DownloadStatus {
    QUEUED,         // 已加入队列，等待轮到
    PENDING,        // 等待下载
    DOWNLOADING,    // 下载中
    MERGING,        // 合并音视频中
    COMPLETED,      // 已完成
    FAILED,         // 失败
    PAUSED          // 已暂停
}

@Serializable
data class DownloadOptions(
    val includeDanmaku: Boolean = true
)

@Serializable
enum class DownloadAssetKind {
    VIDEO,
    AUDIO,
    COVER,
    DANMAKU
}

@Serializable
enum class DownloadAssetStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    SKIPPED
}

@Serializable
data class DownloadAssetState(
    val kind: DownloadAssetKind,
    val status: DownloadAssetStatus = DownloadAssetStatus.PENDING,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val filePath: String? = null,
    val tempPath: String? = null,
    val errorMessage: String? = null,
    val segmentCount: Int = 0,
    val sha256: String? = null,
)

/**
 * 下载任务数据模型
 */
@Serializable
data class DownloadTask(
    val aid: Long = 0L,
    val bvid: String,
    val cid: Long,
    val title: String,
    val episodeLabel: String? = null,
    val groupKey: String? = null,
    val groupTitle: String? = null,
    val episodeSortIndex: Int = 0,
    val episodeCount: Int = 1,
    val cover: String,
    val ownerName: String,
    val ownerFace: String,
    val duration: Int,           // 时长（秒）
    val quality: Int,            // 画质 ID
    val qualityDesc: String,     // 画质描述（如 "1080P"）
    val videoUrl: String,        // 视频流 URL
    val audioUrl: String,        // 音频流 URL
    val status: DownloadStatus = DownloadStatus.PENDING,
    val progress: Float = 0f,    // 0.0 ~ 1.0
    val videoProgress: Float = 0f,
    val audioProgress: Float = 0f,
    val filePath: String? = null,// 下载完成后的文件路径
    val fileSize: Long = 0,      // 文件大小（字节）
    val downloadedSize: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null,
    val localCoverPath: String? = null, // 🖼️ [新增] 本地缓存的封面路径
    val customSaveDir: String? = null,   // 📂 [新增] 自定义保存目录（单视频独立路径）
    val exportedFileUri: String? = null, // 📁 [新增] 导出到用户授权目录后的 URI
    val isAudioOnly: Boolean = false,    // 🎵 [新增] 仅下载音频
    val isVerticalVideo: Boolean = false,
    val lastPlaybackPositionMs: Long = 0L,
    val options: DownloadOptions = DownloadOptions(),
    val assets: List<DownloadAssetState> = emptyList(),
    val localDanmakuSegmentPaths: List<String> = emptyList(),
    val localDanmakuMetadataPath: String? = null,
    val workToken: String? = null,
    val pgcEpisodeId: Long = 0L,
    val pgcSeasonId: Long = 0L,
    val isCourse: Boolean = false,
    val urlsRefreshedAt: Long = 0L,
    val downloadAttempts: Int = 0,
    val videoBackupUrls: List<String> = emptyList(),
    val audioBackupUrls: List<String> = emptyList(),
    val automaticRetries: Int = 0,
    val retryNotBeforeMs: Long = 0L,
) {
    val id: String get() = if (isAudioOnly) "${bvid}_${cid}_audio" else "${bvid}_${cid}_$quality"
    
    val isComplete: Boolean get() = status == DownloadStatus.COMPLETED
    val isDownloading: Boolean get() = status == DownloadStatus.DOWNLOADING || status == DownloadStatus.MERGING
    val canResume: Boolean get() = status == DownloadStatus.PAUSED || status == DownloadStatus.FAILED
    val isFailed: Boolean get() = status == DownloadStatus.FAILED
    val isWaitingForRetry: Boolean get() = status == DownloadStatus.QUEUED && retryNotBeforeMs > 0L
}

/**
 * 下载画质选项
 */
data class QualityOption(
    val id: Int,
    val desc: String,
    val videoUrl: String,
    val audioUrl: String
)
