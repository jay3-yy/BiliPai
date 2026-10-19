package com.android.purebilibili.core.util

import androidx.media3.common.PlaybackException
import com.android.purebilibili.data.model.VideoLoadError

internal fun playbackErrorCategory(code: Int): String = when (code) {
    PlaybackException.ERROR_CODE_TIMEOUT,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "timeout"
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "network"
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "http"
    in 2000..2999 -> "io"
    in 3000..3999 -> "source_format"
    in 4000..4999 -> "decoder"
    in 5000..5999 -> "audio_output"
    in 6000..6999 -> "drm"
    else -> "other"
}

internal fun loadErrorCategory(error: VideoLoadError): String = when (error) {
    VideoLoadError.NetworkError -> "network"
    VideoLoadError.Timeout -> "timeout"
    VideoLoadError.WbiSignatureError -> "request_verification"
    VideoLoadError.VideoNotFound -> "unavailable"
    VideoLoadError.RegionRestricted -> "region_restricted"
    VideoLoadError.VipRequired -> "vip_required"
    VideoLoadError.CidNotFound -> "missing_media_info"
    VideoLoadError.PlayUrlEmpty -> "empty_play_url"
    is VideoLoadError.RateLimited, is VideoLoadError.GlobalCooldown -> "cooldown"
    is VideoLoadError.ApiError -> "api"
    is VideoLoadError.UnknownError -> "other"
}

internal data class QualitySwitchTelemetryResult(
    val fromQuality: Int,
    val requestedQuality: Int,
    val actualQuality: Int?,
    val source: String,
    val outcome: String,
    val reason: String,
    val durationMs: Long
)

/** One terminal result per accepted request; applying a URL is not yet playback readiness. */
internal class QualitySwitchTelemetryAttempt(
    val fromQuality: Int,
    val requestedQuality: Int,
    val source: String,
    private val startedAtMs: Long
) {
    private var actualQuality: Int? = null
    private var fallback = false
    private var finished = false

    fun applied(actual: Int, wasFallback: Boolean) {
        actualQuality = actual
        fallback = wasFallback || actual != requestedQuality
    }

    fun poll(nowMs: Long, isCurrent: Boolean, ready: Boolean): QualitySwitchTelemetryResult? {
        if (!isCurrent) return finish("canceled", "media_changed", nowMs)
        if (actualQuality != null && ready) {
            return finish(if (fallback) "fallback" else "success", "ready", nowMs)
        }
        if (nowMs - startedAtMs >= 30_000L) return finish("timeout", "not_ready", nowMs)
        return null
    }

    fun finish(outcome: String, reason: String, nowMs: Long): QualitySwitchTelemetryResult? {
        if (finished) return null
        finished = true
        return QualitySwitchTelemetryResult(
            fromQuality, requestedQuality, actualQuality, source, outcome, reason,
            (nowMs - startedAtMs).coerceAtLeast(0L)
        )
    }
}
