@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.download

import androidx.media3.datasource.HttpDataSource
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

internal fun DownloadTask.deferAutomaticRetry(nowMs: Long): DownloadTask? {
    val delayMs = when (automaticRetries) {
        0 -> 30_000L
        1 -> 60_000L
        else -> return null
    }
    return copy(
        status = DownloadStatus.QUEUED,
        automaticRetries = automaticRetries + 1,
        retryNotBeforeMs = nowMs + delayMs,
    )
}

internal fun DownloadTask.makeRetryReady(token: String?): DownloadTask? =
    if (isWaitingForRetry && workToken == token) copy(retryNotBeforeMs = 0L) else null

internal fun isRetryableDownloadFailure(error: Throwable): Boolean {
    if (error is HttpDataSource.InvalidResponseCodeException) {
        return error.responseCode == 408 || error.responseCode == 429 || error.responseCode in 500..599
    }
    if (error is EOFException) return true
    if (error !is HttpDataSource.HttpDataSourceException) return false
    return generateSequence(error.cause) { it.cause }.any {
        it is SocketTimeoutException || it is ConnectException || it is NoRouteToHostException ||
            it is UnknownHostException || it is SocketException || it is EOFException
    }
}
