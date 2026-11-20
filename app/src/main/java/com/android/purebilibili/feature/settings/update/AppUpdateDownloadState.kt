package com.android.purebilibili.feature.settings

internal enum class AppUpdateDownloadStatus {
    IDLE,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

internal data class AppUpdateDownloadState(
    val status: AppUpdateDownloadStatus = AppUpdateDownloadStatus.IDLE,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String? = null,
    val errorMessage: String? = null,
    val checksumProvided: Boolean = false,
    val usedGitHubFallback: Boolean = false,
)

internal fun startAppUpdateDownload(totalBytes: Long): AppUpdateDownloadState {
    return AppUpdateDownloadState(
        status = AppUpdateDownloadStatus.DOWNLOADING,
        progress = 0f,
        downloadedBytes = 0L,
        totalBytes = totalBytes.coerceAtLeast(0L)
    )
}

internal fun updateAppUpdateDownloadProgress(
    current: AppUpdateDownloadState,
    downloadedBytes: Long
): AppUpdateDownloadState {
    val safeDownloadedBytes = downloadedBytes.coerceAtLeast(0L)
    val progress = if (current.totalBytes > 0L) {
        (safeDownloadedBytes.toFloat() / current.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    return current.copy(
        status = AppUpdateDownloadStatus.DOWNLOADING,
        downloadedBytes = safeDownloadedBytes,
        progress = progress,
        errorMessage = null
    )
}

internal fun completeAppUpdateDownload(
    current: AppUpdateDownloadState,
    filePath: String
): AppUpdateDownloadState {
    return current.copy(
        status = AppUpdateDownloadStatus.COMPLETED,
        progress = 1f,
        downloadedBytes = current.totalBytes.coerceAtLeast(current.downloadedBytes),
        filePath = filePath,
        errorMessage = null
    )
}

internal fun failAppUpdateDownload(
    current: AppUpdateDownloadState,
    errorMessage: String
): AppUpdateDownloadState {
    return current.copy(
        status = AppUpdateDownloadStatus.FAILED,
        errorMessage = errorMessage
    )
}
