package com.android.purebilibili.feature.download

import java.io.File

internal data class DownloadCleanupTargets(
    val filePaths: Set<String>,
    val taskDirectoryPath: String
)

internal fun resolveDownloadCleanupTargets(
    taskId: String,
    task: DownloadTask,
    taskDirectoryPath: String
): DownloadCleanupTargets {
    val directory = File(taskDirectoryPath)
    val extension = if (task.isAudioOnly) "m4a" else "mp4"
    val filePaths = buildSet {
        add(File(directory, "${taskId}_video.m4s").absolutePath)
        add(File(directory, "${taskId}_audio.m4s").absolutePath)
        add(File(directory, "${taskId}.$extension").absolutePath)
        add(File(directory, "${taskId}_cover.jpg").absolutePath)
        task.filePath?.takeIf { it.isNotBlank() }?.let(::add)
        task.localCoverPath?.takeIf { it.isNotBlank() }?.let(::add)
        task.localDanmakuMetadataPath?.takeIf { it.isNotBlank() }?.let(::add)
        task.localDanmakuSegmentPaths.filter { it.isNotBlank() }.forEach(::add)
        task.assets.mapNotNull { it.filePath?.takeIf(String::isNotBlank) }.forEach(::add)
        task.assets.mapNotNull { it.tempPath?.takeIf(String::isNotBlank) }.forEach(::add)
        directory.listFiles { file ->
            file.name.startsWith("${taskId}_") &&
                (file.name.endsWith(".part") || file.name.endsWith(".source") || file.name.contains(".part.chunk"))
        }?.forEach { add(it.absolutePath) }
    }
    return DownloadCleanupTargets(
        filePaths = filePaths,
        taskDirectoryPath = directory.absolutePath
    )
}
