package com.android.purebilibili.feature.settings.webdav

import android.content.Context
import android.net.Uri
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.buildAppDns
import com.android.purebilibili.feature.settings.backup.AppBackupArchiveEngine
import com.android.purebilibili.feature.settings.backup.AppBackupArchiveInspection
import com.android.purebilibili.feature.settings.backup.AppBackupRestoreReport
import com.android.purebilibili.feature.settings.backup.AppBackupScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private const val XML_CONTENT_TYPE = "application/xml; charset=utf-8"
private const val ZIP_CONTENT_TYPE = "application/zip"

data class LocalBackupExportResult(
    val fileName: String,
    val sizeBytes: Long,
    val scopes: Set<AppBackupScope>,
)

data class LocalBackupInspectionSession(
    val uri: Uri,
    val zipBytes: ByteArray,
    val inspection: AppBackupArchiveInspection,
)

class WebDavBackupService(private val context: Context) {

    private val archiveEngine = AppBackupArchiveEngine(context)

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(buildAppDns())
            .protocols(NetworkModule.resolveSharedNetworkProtocols())
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    suspend fun exportLocalBackup(
        uri: Uri,
        scopes: Set<AppBackupScope> = AppBackupScope.entries.toSet(),
    ): Result<LocalBackupExportResult> = withContext(Dispatchers.IO) {
        runCatchingCancelable {
            val now = System.currentTimeMillis()
            val fileName = buildWebDavBackupFileName(now)
            val bytes = archiveEngine.createArchive(scopes = scopes, nowEpochMs = now)
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
            } ?: throw IOException("无法写入所选备份文件")
            LocalBackupExportResult(
                fileName = fileName,
                sizeBytes = bytes.size.toLong(),
                scopes = scopes,
            )
        }
    }

    suspend fun inspectLocalBackup(uri: Uri): Result<LocalBackupInspectionSession> = withContext(Dispatchers.IO) {
        runCatchingCancelable {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("无法读取所选备份文件")
            val inspection = archiveEngine.inspectArchive(bytes)
            if (inspection.restorableEntryCount <= 0) {
                throw IOException("该文件不包含可恢复的 BiliPai 备份内容")
            }
            LocalBackupInspectionSession(
                uri = uri,
                zipBytes = bytes,
                inspection = inspection,
            )
        }
    }

    suspend fun restoreLocalBackup(
        session: LocalBackupInspectionSession,
        scopes: Set<AppBackupScope> = AppBackupScope.entries.toSet(),
    ): Result<AppBackupRestoreReport> = withContext(Dispatchers.IO) {
        runCatchingCancelable {
            val report = archiveEngine.restoreArchive(session.zipBytes, allowedScopes = scopes)
            if (report.restoredCount <= 0) {
                throw IOException("未匹配到所选范围内的可恢复文件")
            }
            report
        }
    }

    suspend fun testConnection(config: WebDavBackupConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancelable {
            validateConfig(config)
            val authHeader = Credentials.basic(config.username, config.password)
            val directoryUrl = ensureRemoteDirectory(config, authHeader)
            listBackupsInternal(directoryUrl, authHeader)
            Unit
        }
    }

    suspend fun listBackups(config: WebDavBackupConfig): Result<List<WebDavBackupEntry>> = withContext(Dispatchers.IO) {
        runCatchingCancelable {
            validateConfig(config)
            val authHeader = Credentials.basic(config.username, config.password)
            val directoryUrl = ensureRemoteDirectory(config, authHeader)
            listBackupsInternal(directoryUrl, authHeader)
        }
    }

    suspend fun backupNow(
        config: WebDavBackupConfig,
        scopes: Set<AppBackupScope> = AppBackupScope.entries.toSet(),
    ): Result<WebDavBackupEntry> = withContext(Dispatchers.IO) {
        runCatchingCancelable {
            validateConfig(config)
            val authHeader = Credentials.basic(config.username, config.password)
            val directoryUrl = ensureRemoteDirectory(config, authHeader)

            val now = System.currentTimeMillis()
            val fileName = buildWebDavBackupFileName(now)
            val uploadUrl = "$directoryUrl/$fileName"
            val archiveBytes = archiveEngine.createArchive(scopes = scopes, nowEpochMs = now)

            val putRequest = Request.Builder()
                .url(uploadUrl)
                .header("Authorization", authHeader)
                .put(archiveBytes.toRequestBody(ZIP_CONTENT_TYPE.toMediaType()))
                .build()

            httpClient.newCall(putRequest).execute().use { response ->
                if (response.code !in setOf(200, 201, 204)) {
                    throw IOException("上传失败: HTTP ${response.code}")
                }
            }

            if (config.maxRemoteBackups > 0) {
                pruneOlderBackupsBestEffort(config, directoryUrl, authHeader)
            }

            WebDavBackupEntry(
                fileName = fileName,
                href = uploadUrl,
                sizeBytes = archiveBytes.size.toLong(),
                lastModifiedEpochMs = now,
            )
        }
    }

    suspend fun restoreLatest(
        config: WebDavBackupConfig,
        scopes: Set<AppBackupScope> = AppBackupScope.entries.toSet(),
    ): Result<WebDavBackupEntry> = withContext(Dispatchers.IO) {
        runCatchingCancelable {
            validateConfig(config)
            val authHeader = Credentials.basic(config.username, config.password)
            val directoryUrl = ensureRemoteDirectory(config, authHeader)
            val entries = listBackupsInternal(directoryUrl, authHeader)
            val latest = selectLatestWebDavBackup(entries)
                ?: throw IllegalStateException("未找到可恢复的备份文件")
            restoreEntryInternal(config, authHeader, latest, scopes)
            latest
        }
    }

    suspend fun restoreEntry(
        config: WebDavBackupConfig,
        entry: WebDavBackupEntry,
        scopes: Set<AppBackupScope> = AppBackupScope.entries.toSet(),
    ): Result<AppBackupRestoreReport> = withContext(Dispatchers.IO) {
        runCatchingCancelable {
            validateConfig(config)
            val authHeader = Credentials.basic(config.username, config.password)
            restoreEntryInternal(config, authHeader, entry, scopes)
        }
    }

    suspend fun deleteBackup(
        config: WebDavBackupConfig,
        entry: WebDavBackupEntry,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancelable {
            validateConfig(config)
            val authHeader = Credentials.basic(config.username, config.password)
            val deleteUrl = resolveWebDavDownloadUrl(config.baseUrl, entry.href)
            val request = Request.Builder()
                .url(deleteUrl)
                .header("Authorization", authHeader)
                .delete()
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.code !in setOf(200, 202, 204, 404)) {
                    throw IOException("删除云端备份失败: HTTP ${response.code}")
                }
            }
            Unit
        }
    }

    private fun pruneOlderBackupsBestEffort(
        config: WebDavBackupConfig,
        directoryUrl: String,
        authHeader: String,
    ) {
        val entries = try {
            listBackupsInternal(directoryUrl, authHeader)
        } catch (_: IOException) {
            return
        }
        val toPrune = selectWebDavBackupsToPrune(entries, config.maxRemoteBackups)
        for (oldEntry in toPrune) {
            try {
                val deleteUrl = resolveWebDavDownloadUrl(config.baseUrl, oldEntry.href)
                val request = Request.Builder()
                    .url(deleteUrl)
                    .header("Authorization", authHeader)
                    .delete()
                    .build()
                httpClient.newCall(request).execute().close()
            } catch (_: IOException) {
                // 单条旧快照清理失败不影响已上传成功的新快照
            }
        }
    }

    private fun restoreEntryInternal(
        config: WebDavBackupConfig,
        authHeader: String,
        entry: WebDavBackupEntry,
        scopes: Set<AppBackupScope>,
    ): AppBackupRestoreReport {
        val downloadUrl = resolveWebDavDownloadUrl(config.baseUrl, entry.href)
        val getRequest = Request.Builder()
            .url(downloadUrl)
            .header("Authorization", authHeader)
            .get()
            .build()

        val zipBytes = httpClient.newCall(getRequest).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("下载失败: HTTP ${response.code}")
            }
            response.body.bytes()
        }

        val report = archiveEngine.restoreArchive(zipBytes, allowedScopes = scopes)
        if (report.restoredCount <= 0) {
            throw IOException("备份文件无可恢复内容")
        }
        return report
    }

    private fun listBackupsInternal(directoryUrl: String, authHeader: String): List<WebDavBackupEntry> {
        val propfindBody = buildWebDavPropfindBody()
        val collectionUrl = ensureWebDavCollectionUrl(directoryUrl)

        val request = Request.Builder()
            .url(collectionUrl)
            .header("Authorization", authHeader)
            .header("Depth", "1")
            .method("PROPFIND", propfindBody.toRequestBody(XML_CONTENT_TYPE.toMediaType()))
            .build()

        val xml = httpClient.newCall(request).execute().use { response ->
            if (response.code !in setOf(200, 207)) {
                throw IOException("读取目录失败: HTTP ${response.code} (PROPFIND ${request.url.encodedPath})")
            }
            response.body.string()
        }

        return parseWebDavBackupEntries(xml)
    }

    private fun ensureRemoteDirectory(config: WebDavBackupConfig, authHeader: String): String {
        val baseUrl = normalizeWebDavBaseUrl(config.baseUrl)
        val remoteDir = normalizeWebDavRemoteDir(config.remoteDir)
        val segments = remoteDir.split('/').filter { it.isNotBlank() }

        var current = baseUrl
        for (segment in segments) {
            current = "$current/$segment"
            val action = resolveWebDavDirectoryAction(
                probeStatus = probeDirectoryDepthZero(current, authHeader)
            )
            when (action) {
                WebDavDirectoryAction.EXISTS -> Unit
                WebDavDirectoryAction.CREATE -> mkcolDirectory(current, authHeader)
                WebDavDirectoryAction.CREATE_OR_VERIFY -> {
                    mkcolDirectory(current, authHeader)
                    val confirmStatus = probeDirectoryDepthZero(current, authHeader)
                    if (confirmStatus !in setOf(200, 207)) {
                        throw IOException(
                            resolveWebDavDirectoryProbeError(confirmStatus ?: 0, current)
                        )
                    }
                }
            }
        }
        return current
    }

    private fun probeDirectoryDepthZero(url: String, authHeader: String): Int? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .header("Depth", "0")
                .method("PROPFIND", buildWebDavPropfindBody().toRequestBody(XML_CONTENT_TYPE.toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { it.code }
        } catch (_: IOException) {
            null
        }
    }

    private fun mkcolDirectory(url: String, authHeader: String) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authHeader)
            .method("MKCOL", ByteArray(0).toRequestBody(null))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code !in setOf(200, 201, 204, 301, 405)) {
                throw IOException("创建远端目录失败: HTTP ${response.code} ($url)")
            }
        }
    }

    private fun validateConfig(config: WebDavBackupConfig) {
        if (normalizeWebDavBaseUrl(config.baseUrl).isBlank()) {
            throw IllegalArgumentException("请先填写 WebDAV 服务器地址")
        }
        if (config.username.trim().isBlank()) {
            throw IllegalArgumentException("请先填写用户名")
        }
        if (config.password.isBlank()) {
            throw IllegalArgumentException("请先填写密码")
        }
    }

    private inline fun <T> runCatchingCancelable(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
