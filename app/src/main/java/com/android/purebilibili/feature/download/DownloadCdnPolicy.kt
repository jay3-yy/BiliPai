@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.download

import androidx.media3.datasource.HttpDataSource
import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.feature.plugin.rewriteBilivideoHost
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal const val MAX_DOWNLOAD_CDN_CANDIDATES = 3

internal fun downloadCdnCandidates(preferred: String, primary: String, backups: List<String>): List<String> =
    (listOf(preferred, primary) + backups).filter { it.toHttpUrlOrNull() != null }.distinct()

internal fun DashVideo.downloadBackupUrls(primary: String = getValidUrl()): List<String> =
    downloadCdnCandidates(baseUrl, "", backupUrl.orEmpty()).filterNot { it == primary }

internal fun DashAudio.downloadBackupUrls(primary: String = getValidUrl()): List<String> =
    downloadCdnCandidates(baseUrl, "", backupUrl.orEmpty()).filterNot { it == primary }

internal fun List<DashVideo>.downloadVideoBackupUrls(primary: String): List<String> =
    firstOrNull { matchesDownloadUrl(primary, it.getValidUrl(), it.baseUrl, it.backupUrl.orEmpty()) }
        ?.downloadBackupUrls(primary).orEmpty()

internal fun List<DashAudio>.downloadAudioBackupUrls(primary: String): List<String> =
    firstOrNull { matchesDownloadUrl(primary, it.getValidUrl(), it.baseUrl, it.backupUrl.orEmpty()) }
        ?.downloadBackupUrls(primary).orEmpty()

internal fun Dash.allDownloadAudioTracks(): List<DashAudio> =
    audio.orEmpty() + dolby?.audio.orEmpty() + listOfNotNull(flac?.audio)

private fun matchesDownloadUrl(primary: String, selected: String, base: String, backups: List<String>): Boolean {
    val host = primary.toHttpUrlOrNull()?.host ?: return false
    return (listOf(selected, base) + backups).any { url ->
        url == primary || rewriteBilivideoHost(url, host) == primary
    }
}

internal fun shouldSwitchDownloadCdn(error: Exception): Boolean =
    when (error) {
        is HttpDataSource.InvalidResponseCodeException -> error.responseCode in setOf(401, 403, 404, 410, 416) ||
            isRetryableDownloadFailure(error)
        is DownloadRangeMismatchException, is DownloadTrackIntegrityException -> true
        else -> isRetryableDownloadFailure(error)
    }
