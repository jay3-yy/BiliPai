package com.android.purebilibili.data.repository

/** 官方客户端把「剩最后 5 秒」也算看完，此时 progress 上报 -1。 */
private const val VIDEO_HISTORY_FINISH_THRESHOLD_MS = 5_000L

/** 视频观看进度上报类型，UGC 固定 3。 */
private const val VIDEO_HISTORY_REPORT_TYPE = 3

/**
 * 构造 `/x/v2/history/report` 的表单字段。
 *
 * 这是推荐系统判断「已看过」的主信号：官方客户端在 onPause、播放器可用性变化、
 * 离开页面、切后台这几个事件上都会调用，没有最小时长门槛，只看进度变化是否超过 1 秒。
 *
 * @param progressMs 当前播放位置（毫秒）
 * @param durationMs 视频总时长（毫秒）
 */
fun buildVideoHistoryReportFields(
    aid: Long,
    cid: Long,
    progressMs: Long,
    durationMs: Long,
    startTsSec: Long,
    deviceTsSec: Long,
    csrf: String,
    source: String = "player-old",
    scene: String = "front",
): Map<String, String>? {
    if (aid <= 0L || cid <= 0L || csrf.isBlank()) return null
    val durationSec = (durationMs.coerceAtLeast(0L) / 1000L)
    // 时长未知时不报 -1，否则服务端会认为「一点就看完」。
    if (durationSec <= 0L) return null
    val positionMs = progressMs.coerceAtLeast(0L)
    val finished = positionMs + VIDEO_HISTORY_FINISH_THRESHOLD_MS >= durationMs
    val progressSec = if (finished) -1L else (positionMs / 1000L)
    return mapOf(
        "aid" to aid.toString(),
        "cid" to cid.toString(),
        "source" to source,
        "duration" to durationSec.toString(),
        "progress" to progressSec.toString(),
        "type" to VIDEO_HISTORY_REPORT_TYPE.toString(),
        "device_ts" to deviceTsSec.toString(),
        "start_ts" to startTsSec.toString(),
        "scene" to scene,
        "csrf" to csrf,
    )
}
