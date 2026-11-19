package com.android.purebilibili.feature.download

internal fun normalizeRestoredDownloadTask(task: DownloadTask): DownloadTask {
    val normalizedStatusTask = when (task.status) {
        DownloadStatus.QUEUED,
        DownloadStatus.PENDING,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.MERGING -> task.copy(status = DownloadStatus.QUEUED)
        else -> task
    }
    return sanitizeDownloadTask(normalizedStatusTask)
}

internal fun shouldPersistDownloadTaskUpdate(
    previous: DownloadTask,
    updated: DownloadTask
): Boolean {
    return previous.status != updated.status ||
        previous.workToken != updated.workToken ||
        previous.videoUrl != updated.videoUrl ||
        previous.audioUrl != updated.audioUrl ||
        previous.videoBackupUrls != updated.videoBackupUrls ||
        previous.audioBackupUrls != updated.audioBackupUrls ||
        previous.urlsRefreshedAt != updated.urlsRefreshedAt ||
        previous.downloadAttempts != updated.downloadAttempts ||
        previous.automaticRetries != updated.automaticRetries ||
        previous.retryNotBeforeMs != updated.retryNotBeforeMs ||
        previous.filePath != updated.filePath ||
        previous.fileSize != updated.fileSize ||
        previous.downloadedSize != updated.downloadedSize ||
        previous.errorMessage != updated.errorMessage ||
        previous.localCoverPath != updated.localCoverPath ||
        previous.customSaveDir != updated.customSaveDir ||
        previous.exportedFileUri != updated.exportedFileUri ||
        previous.lastPlaybackPositionMs != updated.lastPlaybackPositionMs ||
        previous.options != updated.options ||
        previous.assets != updated.assets ||
        previous.localDanmakuSegmentPaths != updated.localDanmakuSegmentPaths ||
        previous.localDanmakuMetadataPath != updated.localDanmakuMetadataPath
}
