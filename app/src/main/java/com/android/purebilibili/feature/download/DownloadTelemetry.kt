@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.download

import androidx.media3.datasource.HttpDataSource
import com.android.purebilibili.core.util.AnalyticsHelper
import java.io.IOException
import kotlinx.coroutines.CancellationException

internal enum class DownloadStage {
    STREAM_RESOLUTION, STREAM_REFRESH, QUEUE, SCHEDULING, FOREGROUND, PREPARATION, VIDEO_TRANSFER,
    AUDIO_TRANSFER, CDN_FALLBACK, TRACK_INTEGRITY, MUX, EXPORT, COMPLETE,
}

internal enum class DownloadOutcome {
    ACCEPTED, DUPLICATE, STARTED, COMPLETED, FAILED, RETRY, PAUSED, CANCELLED, INTERRUPTED,
}

internal enum class DownloadFailureReason {
    HTTP, NETWORK, INTEGRITY, IO, PERMISSION, UNAVAILABLE, OTHER,
}

/** Each observation belongs to the collection settings that were active when it started. */
internal class DownloadTelemetry {
    private val generation = AnalyticsHelper.collectionGeneration().takeIf { AnalyticsHelper.isCollectionEnabled() }

    fun record(
        stage: DownloadStage,
        outcome: DownloadOutcome,
        error: Throwable? = null,
        unavailable: Boolean = false,
        attempt: Int? = null,
    ) {
        val collection = generation ?: return
        val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
        val httpStatus = causes.filterIsInstance<HttpDataSource.InvalidResponseCodeException>()
            .firstOrNull()?.responseCode
        val reason = when {
            httpStatus != null -> DownloadFailureReason.HTTP
            causes.any(::isRetryableDownloadFailure) -> DownloadFailureReason.NETWORK
            causes.any { it is SecurityException } -> DownloadFailureReason.PERMISSION
            causes.any { it is DownloadTrackIntegrityException } -> DownloadFailureReason.INTEGRITY
            causes.any { it is IOException } -> DownloadFailureReason.IO
            unavailable || error is DownloadStreamUnavailableException -> DownloadFailureReason.UNAVAILABLE
            error != null -> DownloadFailureReason.OTHER
            else -> null
        }
        AnalyticsHelper.logDownloadStage(collection, stage, outcome, attempt, reason, httpStatus)
    }
}

internal class DownloadStreamUnavailableException(message: String) : IllegalStateException(message)

internal suspend fun <T> observeDownloadStreamResolution(
    resolve: suspend () -> T,
): T {
    val telemetry = DownloadTelemetry()
    return try {
        resolve().also { stream ->
            telemetry.record(
                DownloadStage.STREAM_RESOLUTION,
                if (stream == null) DownloadOutcome.FAILED else DownloadOutcome.COMPLETED,
                unavailable = stream == null,
            )
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        telemetry.record(DownloadStage.STREAM_RESOLUTION, DownloadOutcome.FAILED, error)
        throw error
    }
}
