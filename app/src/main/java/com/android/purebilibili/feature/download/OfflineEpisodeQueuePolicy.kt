package com.android.purebilibili.feature.download

import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import com.android.purebilibili.feature.video.viewmodel.PlaybackEndAction
import com.android.purebilibili.feature.video.viewmodel.resolvePlaybackEndAction
import java.io.File

private val PAGE_LABEL_REGEX = Regex("""^\s*[Pp](\d+)\b""")

internal fun resolveOfflineEpisodeQueue(
    tasks: Collection<DownloadTask>,
    currentTask: DownloadTask
): List<DownloadTask> {
    val currentGroupKey = currentTask.groupKey?.trim().orEmpty()
    return tasks
        .filter { candidate ->
            candidate.isComplete &&
                candidate.isAudioOnly == currentTask.isAudioOnly &&
                candidate.filePath?.let { File(it).exists() } == true &&
                when {
                    currentGroupKey.isNotBlank() -> candidate.groupKey == currentGroupKey
                    else -> candidate.bvid == currentTask.bvid
                }
        }
        .groupBy { it.bvid to it.cid }
        .values
        .map { variants ->
            variants.firstOrNull { it.id == currentTask.id } ?: variants.maxBy { it.createdAt }
        }
        .sortedWith(
            compareBy<DownloadTask>(
                { resolveOfflineEpisodeSortIndex(it) },
                { it.createdAt },
                { it.cid }
            )
        )
}

internal fun resolveOfflineEpisodeSortIndex(task: DownloadTask): Int {
    if (task.episodeSortIndex > 0) return task.episodeSortIndex
    val fromLabel = PAGE_LABEL_REGEX.find(task.episodeLabel.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (fromLabel != null && fromLabel > 0) return fromLabel
    return Int.MAX_VALUE
}

internal fun resolveOfflineEpisodeAfterPlayback(
    queue: List<DownloadTask>,
    currentIndex: Int,
    behavior: PlaybackCompletionBehavior,
    autoPlayEnabled: Boolean,
): DownloadTask? {
    if (currentIndex !in queue.indices) return null
    val nextEpisode = queue.getOrNull(currentIndex + 1)
    return when (
        resolvePlaybackEndAction(
            behavior = behavior,
            autoPlayEnabled = autoPlayEnabled,
            isExternalPlaylist = false,
            externalPlaylistAutoContinueEnabled = false,
            hasNextPage = nextEpisode != null,
            hasNextSeasonEpisode = false,
        )
    ) {
        PlaybackEndAction.AUTO_CONTINUE, PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST -> nextEpisode
        PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST_LOOP -> nextEpisode ?: queue.first()
        PlaybackEndAction.REPEAT_CURRENT -> queue[currentIndex]
        PlaybackEndAction.STOP -> null
    }
}
