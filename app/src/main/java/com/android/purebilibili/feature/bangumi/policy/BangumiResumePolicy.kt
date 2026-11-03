package com.android.purebilibili.feature.bangumi

import com.android.purebilibili.data.model.response.BangumiEpisode
import com.android.purebilibili.data.model.response.BangumiDetail

internal const val BANGUMI_HEARTBEAT_INTERVAL_MS = 15_000L

internal data class BangumiResumeTarget(
    val epId: Long,
    val resumePositionMs: Long
)

internal data class BangumiDetailRequest(
    val seasonId: Long,
    val epId: Long
)

internal fun resolveBangumiDetailRequest(
    seasonId: Long,
    epId: Long
): BangumiDetailRequest {
    return if (epId > 0L) {
        BangumiDetailRequest(seasonId = 0L, epId = epId)
    } else {
        BangumiDetailRequest(seasonId = seasonId.coerceAtLeast(0L), epId = 0L)
    }
}

internal fun bangumiAllEpisodes(detail: BangumiDetail): List<BangumiEpisode> =
    detail.episodes.orEmpty() + detail.section.orEmpty().flatMap { it.episodes.orEmpty() }

/** 上下集与连续播放只在当前分区中进行。 */
internal fun resolveBangumiPlaybackEpisodes(detail: BangumiDetail, episodeId: Long): List<BangumiEpisode> {
    val main = detail.episodes.orEmpty()
    if (main.any { it.id == episodeId }) return main
    return detail.section.orEmpty().firstOrNull { section ->
        section.episodes.orEmpty().any { it.id == episodeId }
    }?.episodes.orEmpty()
}

internal fun resolveBangumiAutoResumeTarget(
    detail: BangumiDetail,
    routeEpId: Long,
    autoResumeEnabled: Boolean
): BangumiResumeTarget? {
    if (!autoResumeEnabled || routeEpId > 0L) return null

    val progress = detail.userStatus?.progress ?: return null
    val lastEpId = progress.lastEpId.takeIf { it > 0L } ?: return null
    val episodes = bangumiAllEpisodes(detail)
    if (episodes.isNotEmpty() && episodes.none { it.id == lastEpId }) return null

    return BangumiResumeTarget(
        epId = lastEpId,
        resumePositionMs = resolveBangumiResumePositionMs(progress.lastTime)
    )
}

/** 默认选择依次使用指定视频、指定剧集、观看记录、首个正片或附加分区剧集。 */
internal fun resolveBangumiInitialEpisode(
    detail: BangumiDetail,
    preferredAid: Long,
    routeEpId: Long,
    autoResumeEnabled: Boolean
): BangumiResumeTarget? {
    val episodes = bangumiAllEpisodes(detail)
    if (episodes.isEmpty()) return null
    fun targetForEpisode(epId: Long): BangumiResumeTarget {
        val progress = detail.userStatus?.progress
        val position = if (autoResumeEnabled && progress?.lastEpId == epId) {
            resolveBangumiResumePositionMs(progress.lastTime)
        } else 0L
        return BangumiResumeTarget(epId, position)
    }
    preferredAid.takeIf { it > 0L }
        ?.let { aid -> episodes.firstOrNull { it.aid == aid }?.let { return targetForEpisode(it.id) } }
    routeEpId.takeIf { it > 0L }
        ?.let { epId -> episodes.firstOrNull { it.id == epId }?.let { return targetForEpisode(it.id) } }
    return resolveBangumiAutoResumeTarget(
        detail = detail,
        routeEpId = routeEpId,
        autoResumeEnabled = autoResumeEnabled
    ) ?: episodes.firstOrNull()?.let { BangumiResumeTarget(it.id, 0L) }
}

internal fun resolveBangumiResumePositionMs(lastTimeSec: Long): Long {
    return lastTimeSec.coerceAtLeast(0L) * 1000L
}

internal fun resolveBangumiPlaybackStartPositionMs(
    routeResumePositionMs: Long,
    savedEpisodePositionMs: Long
): Long {
    return routeResumePositionMs.takeIf { it > 0L }
        ?: savedEpisodePositionMs.coerceAtLeast(0L)
}

internal fun shouldSendBangumiPlaybackHeartbeat(
    isPlaying: Boolean,
    bvid: String,
    cid: Long,
    currentPositionMs: Long,
    epid: Long = 0L,
    sid: Long = 0L
): Boolean {
    // 部分番剧集没有 bvid；只要 epid+sid 齐全即可上报观看历史（对齐 PiliPlus 的 epid/sid 心跳）
    return isPlaying &&
        (bvid.isNotBlank() || (epid > 0L && sid > 0L)) &&
        cid > 0L &&
        currentPositionMs >= 0L
}
