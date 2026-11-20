package com.android.purebilibili.feature.settings

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.android.purebilibili.app.DOWNLOAD_NOTIFICATION_CHANNEL_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val APP_UPDATE_WORK_NAME = "app_update_download"
private const val APP_UPDATE_WORK_TAG = "app_update_download"
private const val KEY_ASSET_NAME = "asset_name"
private const val KEY_ASSET_URL = "asset_url"
private const val KEY_MIRROR_URL = "mirror_url"
private const val KEY_ASSET_SIZE = "asset_size"
private const val KEY_EXPECTED_SHA256 = "expected_sha256"
private const val KEY_RELEASE_VERSION = "release_version"
private const val KEY_DOWNLOADED_BYTES = "downloaded_bytes"
private const val KEY_TOTAL_BYTES = "total_bytes"
private const val KEY_FILE_PATH = "file_path"
private const val KEY_ERROR_MESSAGE = "error_message"
private const val KEY_GITHUB_FALLBACK = "github_fallback"

private class AppUpdateChecksumException : IOException("安装包 SHA-256 校验失败")

internal class AppUpdateDownloadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val assetName = inputData.getString(KEY_ASSET_NAME)?.takeIf(String::isNotBlank)
            ?: return@withContext Result.failure(errorData("更新安装包名称缺失"))
        val assetUrl = inputData.getString(KEY_ASSET_URL)?.takeIf(String::isNotBlank)
            ?: return@withContext Result.failure(errorData("更新下载地址缺失"))
        val expectedSha256 = inputData.getString(KEY_EXPECTED_SHA256)
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
        val mirrorUrl = inputData.getString(KEY_MIRROR_URL)?.takeIf(String::isNotBlank)
        if (mirrorUrl != null && !isAppUpdateSha256(expectedSha256)) {
            return@withContext Result.failure(errorData("此版本未提供 SHA-256，请使用 GitHub 下载"))
        }
        val declaredSize = inputData.getLong(KEY_ASSET_SIZE, 0L).coerceAtLeast(0L)
        val updateDir = resolveAppUpdateCacheDir(applicationContext.cacheDir).apply { mkdirs() }
        val outputFile = File(updateDir, sanitizeAppUpdateFileName(assetName))
        val officialPartFile = File(updateDir, "$id.part")
        val mirrorPartFile = File(updateDir, "$id.mirror.part")
        val workContext = currentCoroutineContext()

        try {
            setForeground(createForegroundInfo())
            var completedPart = officialPartFile
            var usedGitHubFallback = false
            val totalBytes = if (mirrorUrl == null) {
                downloadApk(assetUrl, officialPartFile, declaredSize, expectedSha256)
            } else {
                try {
                    downloadApk(mirrorUrl, mirrorPartFile, declaredSize, expectedSha256).also {
                        completedPart = mirrorPartFile
                    }
                } catch (error: IOException) {
                    workContext.ensureActive()
                    mirrorPartFile.delete()
                    usedGitHubFallback = true
                    downloadApk(assetUrl, officialPartFile, declaredSize, expectedSha256, usedGitHubFallback = true)
                }
            }
            workContext.ensureActive()
            Files.move(completedPart.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            officialPartFile.delete()
            mirrorPartFile.delete()
            Result.success(
                Data.Builder()
                    .putString(KEY_FILE_PATH, outputFile.absolutePath)
                    .putLong(KEY_DOWNLOADED_BYTES, outputFile.length())
                    .putLong(KEY_TOTAL_BYTES, totalBytes)
                    .putBoolean(KEY_GITHUB_FALLBACK, usedGitHubFallback)
                    .build()
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: AppUpdateChecksumException) {
            officialPartFile.delete()
            Result.failure(errorData(error.message.orEmpty()))
        } catch (error: IOException) {
            Result.retry()
        } catch (error: Exception) {
            officialPartFile.delete()
            mirrorPartFile.delete()
            Result.failure(errorData(error.message ?: "更新下载失败"))
        } finally {
            if (!workContext.isActive) {
                officialPartFile.delete()
                mirrorPartFile.delete()
            }
        }
    }

    private suspend fun downloadApk(
        url: String,
        partFile: File,
        declaredSize: Long,
        expectedSha256: String?,
        usedGitHubFallback: Boolean = false,
    ): Long {
        val downloadContext = currentCoroutineContext()
        var existingBytes = partFile.length()
        var connection = openDownloadConnection(url, existingBytes)
        try {
            setProgress(progressData(existingBytes, declaredSize, usedGitHubFallback))
            var responseCode = connection.responseCode
            if (responseCode == 416 && existingBytes > 0L) {
                connection.disconnect()
                partFile.delete()
                existingBytes = 0L
                connection = openDownloadConnection(url, existingBytes)
                responseCode = connection.responseCode
            }
            val partialResponse = responseCode == HttpURLConnection.HTTP_PARTIAL
            if (responseCode != HttpURLConnection.HTTP_OK && !partialResponse) {
                throw IOException("更新下载失败: HTTP $responseCode")
            }
            if (partialResponse && connection.getHeaderField("Content-Range")?.startsWith("bytes $existingBytes-") != true) {
                partFile.delete()
                throw IOException("更新下载的续传范围不匹配")
            }
            if (!partialResponse) existingBytes = 0L
            val totalBytes = declaredSize.takeIf { it > 0L }
                ?: connection.contentLengthLong.takeIf { it > 0L }?.plus(existingBytes)
                ?: 0L
            var downloadedBytes = existingBytes
            var lastProgressReport = SystemClock.elapsedRealtime()
            setProgress(progressData(downloadedBytes, totalBytes, usedGitHubFallback))
            connection.inputStream.use { input ->
                FileOutputStream(partFile, partialResponse && existingBytes > 0L).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        downloadContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloadedBytes += read
                        if (totalBytes > 0L && downloadedBytes > totalBytes) {
                            throw IOException("安装包超过预期大小")
                        }
                        output.write(buffer, 0, read)
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastProgressReport >= 250L) {
                            setProgress(progressData(downloadedBytes, totalBytes, usedGitHubFallback))
                            lastProgressReport = now
                        }
                    }
                }
            }
            if (downloadedBytes == 0L || (totalBytes > 0L && downloadedBytes != totalBytes)) {
                throw IOException("安装包下载不完整")
            }
            if (expectedSha256 != null && !sha256(partFile).equals(expectedSha256, ignoreCase = true)) {
                throw AppUpdateChecksumException()
            }
            return downloadedBytes
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo()

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("正在下载应用更新")
            .setContentText("下载完成后可在应用内安装")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
        val notificationId = APP_UPDATE_WORK_NAME.hashCode()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun openDownloadConnection(url: String, existingBytes: Long): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", "BiliPai-AppUpdate")
            if (existingBytes > 0L) setRequestProperty("Range", "bytes=$existingBytes-")
        }
    }

    private fun progressData(downloadedBytes: Long, totalBytes: Long, usedGitHubFallback: Boolean): Data = Data.Builder()
        .putLong(KEY_DOWNLOADED_BYTES, downloadedBytes.coerceAtLeast(0L))
        .putLong(KEY_TOTAL_BYTES, totalBytes.coerceAtLeast(0L))
        .putBoolean(KEY_GITHUB_FALLBACK, usedGitHubFallback)
        .build()

    private fun errorData(message: String): Data = Data.Builder()
        .putString(KEY_ERROR_MESSAGE, message)
        .build()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}

internal object AppUpdateDownloadCoordinator {
    fun enqueue(
        context: Context,
        asset: AppUpdateAsset,
        releaseVersion: String,
        expectedSha256: String?,
        mirrorUrl: String? = null,
    ) {
        val request = OneTimeWorkRequestBuilder<AppUpdateDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putString(KEY_ASSET_NAME, asset.name)
                    .putString(KEY_ASSET_URL, asset.downloadUrl)
                    .putString(KEY_MIRROR_URL, mirrorUrl)
                    .putLong(KEY_ASSET_SIZE, asset.sizeBytes)
                    .putString(KEY_EXPECTED_SHA256, expectedSha256.orEmpty())
                    .putString(KEY_RELEASE_VERSION, releaseVersion)
                    .build()
            )
            .addTag(APP_UPDATE_WORK_TAG)
            .addTag(releaseTag(releaseVersion))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            APP_UPDATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(APP_UPDATE_WORK_NAME)
    }

    fun observeState(
        context: Context,
        releaseVersion: String,
        checksumProvided: Boolean,
    ): Flow<AppUpdateDownloadState> = WorkManager.getInstance(context.applicationContext)
        .getWorkInfosForUniqueWorkFlow(APP_UPDATE_WORK_NAME)
        .map { infos ->
            infos.firstOrNull { releaseTag(releaseVersion) in it.tags }
                ?.toAppUpdateDownloadState(checksumProvided)
                ?: AppUpdateDownloadState()
        }.distinctUntilChanged()
}

private fun releaseTag(releaseVersion: String): String = "$APP_UPDATE_WORK_TAG:$releaseVersion"

private fun WorkInfo.toAppUpdateDownloadState(checksumProvided: Boolean): AppUpdateDownloadState {
    val data = if (state == WorkInfo.State.SUCCEEDED || state == WorkInfo.State.FAILED) outputData else progress
    val downloadedBytes = data.getLong(KEY_DOWNLOADED_BYTES, 0L)
    val totalBytes = data.getLong(KEY_TOTAL_BYTES, 0L)
    val status = when (state) {
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> AppUpdateDownloadStatus.QUEUED
        WorkInfo.State.RUNNING -> AppUpdateDownloadStatus.DOWNLOADING
        WorkInfo.State.SUCCEEDED -> AppUpdateDownloadStatus.COMPLETED
        WorkInfo.State.FAILED -> AppUpdateDownloadStatus.FAILED
        WorkInfo.State.CANCELLED -> AppUpdateDownloadStatus.IDLE
    }
    return AppUpdateDownloadState(
        status = status,
        progress = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        filePath = data.getString(KEY_FILE_PATH),
        errorMessage = data.getString(KEY_ERROR_MESSAGE),
        checksumProvided = checksumProvided,
        usedGitHubFallback = data.getBoolean(KEY_GITHUB_FALLBACK, false),
    )
}

internal fun sanitizeAppUpdateFileName(name: String): String =
    name.replace(Regex("[^A-Za-z0-9._-]"), "_")
