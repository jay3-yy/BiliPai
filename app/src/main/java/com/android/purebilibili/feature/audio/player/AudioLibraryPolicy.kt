package com.android.purebilibili.feature.audio.player

import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.player.PlaylistItem

internal fun selectAudioLibraryRequest(
    saved: BackgroundAudioSnapshot?,
    queue: List<PlaylistItem>,
    index: Int,
    accountMid: Long?,
): BackgroundAudioSnapshot {
    require(index in queue.indices)
    require(saved == null || saved.accountMid == accountMid) { "Account changed" }
    val selected = queue[index]
    val samePart = saved?.current?.let { it.bvid == selected.bvid && it.cid == selected.cid } == true
    return saved?.copy(queue = queue, index = index, audioUrl = "", playing = false,
        positionMs = if (samePart) saved.positionMs else 0L)
        ?: BackgroundAudioSnapshot(queue, index, 0L, false, 1f, accountMid, PlayMode.SEQUENTIAL)
}
