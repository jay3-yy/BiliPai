package com.android.purebilibili.feature.download

import java.io.File

internal enum class DownloadTaskClickTarget {
    OfflinePlayer,
    OnlinePlayer
}

internal fun resolveDownloadTaskClickTarget(
    task: DownloadTask,
    isNetworkAvailable: Boolean,
    playableOffline: Boolean = isDownloadTaskPlayableOffline(task),
): DownloadTaskClickTarget? {
    return when {
        playableOffline -> DownloadTaskClickTarget.OfflinePlayer
        // 本地文件不可用（如被系统清理）但有网络：回退到在线播放，而不是点击无响应
        task.isComplete && isNetworkAvailable -> DownloadTaskClickTarget.OnlinePlayer
        else -> null
    }
}

/** 可被“暂停全部”命中的任务 */
internal fun shouldPauseAllInclude(task: DownloadTask): Boolean =
    isDownloadTaskActive(task) || task.status == DownloadStatus.QUEUED

/** 可被“继续全部”命中的任务 */
internal fun shouldContinueAllInclude(task: DownloadTask): Boolean =
    task.status == DownloadStatus.PAUSED || task.status == DownloadStatus.FAILED

internal fun isDownloadTaskPlayableOffline(task: DownloadTask): Boolean {
    return task.isComplete &&
        !task.filePath.isNullOrBlank() &&
        File(task.filePath).exists()
}
