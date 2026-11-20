package com.android.purebilibili.feature.video.playback.next

import com.android.purebilibili.data.model.response.UgcEpisode
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.model.response.ViewInfo
import java.util.concurrent.ConcurrentHashMap

internal fun UgcEpisode.publishTimestamp(): Long? =
    arc?.pubdate?.takeIf { it > 0L } ?: arc?.ctime?.takeIf { it > 0L }

// Equal or missing timestamps retain their relative order from the collection response.
internal fun List<UgcEpisode>.inPublishOrder(): List<UgcEpisode> =
    sortedBy { it.publishTimestamp() ?: Long.MAX_VALUE }

internal fun UgcSeason.episodesInPublishOrder(): List<UgcEpisode> =
    sections.flatMap { it.episodes }.inPublishOrder()

/** Shared by displayed episode order, lookahead, autoplay, and explicit previous/next. */
internal object PiliPlaybackOrder {
    private val reversed = ConcurrentHashMap<String, Boolean>()
    private fun key(seasonId: Long, bvid: String): String = if (seasonId > 0) "season:$seasonId" else "video:$bvid"
    fun setReversed(seasonId: Long, bvid: String, value: Boolean) {
        reversed[key(seasonId, bvid)] = value
    }
    fun pageStep(bvid: String): Int = step(0L, bvid)

    fun seasonStep(info: ViewInfo): Int {
        val season = info.ugc_season
        val id = season?.id?.takeIf { it > 0 }
            ?: season?.sections?.firstOrNull { it.season_id > 0 }?.season_id ?: 0L
        return step(id, info.bvid)
    }

    private fun step(seasonId: Long, bvid: String): Int =
        if (reversed[key(seasonId, bvid)] == true) -1 else 1
}
