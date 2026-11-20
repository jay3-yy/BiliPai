package com.android.purebilibili.feature.video.playback.next

/** An immutable, clickable snapshot. Looking ahead never advances the playback queue. */
internal data class NextWatchSuggestion(
    val sourceBvid: String,
    val sourceCid: Long,
    val bvid: String,
    val cid: Long,
    val title: String,
    val cover: String,
    val label: String,
    val pageIndex: Int? = null,
    val playlistIndex: Int? = null,
)

internal fun isNextWatchWindow(durationMs: Long, positionMs: Long): Boolean =
    durationMs > 5_000 && positionMs >= 0 && durationMs - positionMs in 1..5_000

/** Media seconds, rounded up so the hint never shows zero before playback ends. */
internal fun nextWatchRemainingSeconds(durationMs: Long, positionMs: Long): Int? {
    if (!isNextWatchWindow(durationMs, positionMs)) return null
    return ((durationMs - positionMs + 999) / 1_000).toInt()
}

/** Same-video CID navigation, independent of collection membership. */
internal fun resolvePageWatchTarget(
    info: com.android.purebilibili.data.model.response.ViewInfo,
    forward: Boolean = true
): NextWatchSuggestion? {
    val step = PiliPlaybackOrder.pageStep(info.bvid)
    val pageIndex = info.pages.indexOfFirst { it.cid == info.cid }
    val nextPageIndex = pageIndex + if (forward) step else -step
    if (pageIndex >= 0 && nextPageIndex in info.pages.indices) {
        val next = info.pages[nextPageIndex]
        return NextWatchSuggestion(info.bvid, info.cid, info.bvid, next.cid,
            next.part, info.pic, "下一 P", pageIndex = nextPageIndex)
    }
    return null
}

/** Cross-video collection navigation; external queues must never select this target. */
internal fun resolveSeasonWatchTarget(
    info: com.android.purebilibili.data.model.response.ViewInfo,
    forward: Boolean = true,
    loopAtEnd: Boolean = false,
): NextWatchSuggestion? {
    val step = PiliPlaybackOrder.seasonStep(info)
    val episodes = info.ugc_season?.episodesInPublishOrder().orEmpty()
    val index = com.android.purebilibili.feature.video.viewmodel.resolveUgcSeasonEpisodeIndex(
        episodes, info.bvid, info.cid)
    if (index < 0) return null
    val nextEpisodeIndex = index + if (forward) step else -step
    val targetIndex = when {
        nextEpisodeIndex in episodes.indices -> nextEpisodeIndex
        loopAtEnd -> Math.floorMod(nextEpisodeIndex, episodes.size)
        else -> return null
    }
    val next = episodes[targetIndex]
    if (next.bvid.isNotBlank() && (next.bvid != info.bvid || next.cid != info.cid)) {
        return NextWatchSuggestion(info.bvid, info.cid, next.bvid, next.cid,
            next.title, next.arc?.pic.orEmpty(), "下一集")
    }
    return null
}

/** Non-external collection navigation finishes the current video's parts first. */
internal fun resolveCollectionNextWatch(
    info: com.android.purebilibili.data.model.response.ViewInfo,
    loopAtEnd: Boolean = false,
): NextWatchSuggestion? = resolvePageWatchTarget(info) ?: resolveSeasonWatchTarget(info, loopAtEnd = loopAtEnd)
