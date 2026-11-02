package com.android.purebilibili.feature.video.playback.loader

import com.android.purebilibili.data.model.VideoLoadError
import com.android.purebilibili.feature.video.usecase.VideoLoadResult

internal sealed interface PlaybackLoadResult {
    val request: PlaybackRequest
    val cachedPositionMs: Long

    data class Success(
        override val request: PlaybackRequest,
        override val cachedPositionMs: Long,
        val payload: VideoLoadResult.Success
    ) : PlaybackLoadResult

    data class Error(
        override val request: PlaybackRequest,
        override val cachedPositionMs: Long,
        val error: VideoLoadError,
        val canRetry: Boolean
    ) : PlaybackLoadResult

    companion object {
        fun from(
            request: PlaybackRequest,
            cachedPositionMs: Long,
            result: VideoLoadResult
        ): PlaybackLoadResult {
            return when (result) {
                is VideoLoadResult.Success -> Success(
                    request = request,
                    cachedPositionMs = if (request.ignoreSavedProgress) 0L else result.serverPositionMs?.coerceAtLeast(0L) ?: cachedPositionMs,
                    payload = result
                )
                is VideoLoadResult.Error -> Error(
                    request = request,
                    cachedPositionMs = cachedPositionMs,
                    error = result.error,
                    canRetry = result.canRetry
                )
            }
        }
    }
}
