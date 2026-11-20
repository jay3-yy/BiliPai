package com.android.purebilibili.feature.download

internal fun resolveDownloadTaskSecondaryText(task: DownloadTask): String? {
    val label = task.episodeLabel?.trim().orEmpty()
    if (label.isBlank()) return null
    val title = task.title.trim()
    return label.takeUnless {
        title == it || title.endsWith(" - $it") || title.endsWith(" · $it")
    }
}
