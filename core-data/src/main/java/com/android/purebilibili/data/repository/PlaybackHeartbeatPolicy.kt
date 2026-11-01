package com.android.purebilibili.data.repository

fun resolvePlaybackHeartbeatPlayType(
    playedTimeSec: Long,
    realPlayedTimeSec: Long
): Int {
    return if (playedTimeSec <= 0L && realPlayedTimeSec <= 0L) 1 else 0
}

fun buildPlaybackHeartbeatFields(
    bvid: String,
    aid: Long,
    cid: Long,
    epid: Long = 0L,
    sid: Long = 0L,
    mid: Long?,
    playedTimeSec: Long,
    realPlayedTimeSec: Long,
    startTsSec: Long,
    csrf: String,
    videoType: Int = 3,
    subType: Int? = null,
    /** 视频总时长（秒），官方字段 video_duration。 */
    videoDurationSec: Long = 0L,
    /** 本次播放进度（秒），官方字段 last_play_progress_time。 */
    lastProgressSec: Long = 0L,
    /** 本次播放的最大进度（秒），官方字段 max_play_progress_time。 */
    maxProgressSec: Long = 0L,
    /** 播放会话 ID，官方字段 session。 */
    session: String = "",
): Map<String, String> {
    val safePlayedTimeSec = playedTimeSec.coerceAtLeast(0L)
    val safeRealPlayedTimeSec = realPlayedTimeSec.coerceAtLeast(0L)
    return buildMap {
        aid.takeIf { it > 0L }?.let { put("aid", it.toString()) }
        bvid.takeIf { it.isNotBlank() }?.let { put("bvid", it) }
        cid.takeIf { it > 0L }?.let { put("cid", it.toString()) }
        epid.takeIf { it > 0L }?.let { put("epid", it.toString()) }
        sid.takeIf { it > 0L }?.let { put("sid", it.toString()) }
        mid?.takeIf { it > 0L }?.let { put("mid", it.toString()) }
        put("played_time", safePlayedTimeSec.toString())
        put("real_played_time", safeRealPlayedTimeSec.toString())
        // 官方心跳还带这几个进度/时长字段，缺了服务端只能算 VV，统计不到观看进度。
        if (videoDurationSec > 0L) put("video_duration", videoDurationSec.toString())
        if (lastProgressSec > 0L) put("last_play_progress_time", lastProgressSec.toString())
        if (maxProgressSec > 0L) put("max_play_progress_time", maxProgressSec.toString())
        put("total_time", safeRealPlayedTimeSec.toString())
        put("actual_played_time", safeRealPlayedTimeSec.toString())
        session.takeIf { it.isNotBlank() }?.let { put("session", it) }
        put("realtime", safeRealPlayedTimeSec.toString())
        put("start_ts", startTsSec.coerceAtLeast(0L).toString())
        put("type", videoType.toString())
        subType?.takeIf { it > 0 }?.let { put("sub_type", it.toString()) }
        put("dt", "2")
        put("outer", "0")
        put(
            "play_type",
            resolvePlaybackHeartbeatPlayType(
                playedTimeSec = safePlayedTimeSec,
                realPlayedTimeSec = safeRealPlayedTimeSec
            ).toString()
        )
        put("spmid", "333.788.0.0")
        put("from_spmid", "")
        put("refer_url", "")
        csrf.takeIf { it.isNotBlank() }?.let { put("csrf", it) }
    }
}
