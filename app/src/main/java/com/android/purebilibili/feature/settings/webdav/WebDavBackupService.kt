package com.android.purebilibili.feature.settings.webdav

import com.android.purebilibili.core.network.buildAppDns
import android.content.Context
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val ZIP_ENTRY_DATASTORE_SETTINGS = "datastore/settings_prefs.preferences_pb"
private const val ZIP_ENTRY_DATASTORE_PLUGIN = "datastore/plugin_prefs.preferences_pb"
private const val ZIP_ENTRY_SHARED_PREFS_JSON_PLUGIN = "shared_prefs/json_plugins.xml"
private const val ZIP_ENTRY_JSON_PLUGIN_DIR = "json_plugins/"

private const val XML_CONTENT_TYPE = "application/xml; charset=utf-8"
private const val ZIP_CONTENT_TYPE = "application/zip"

class WebDavBackupService(private val context: Context) {

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

    suspend fun testConnection(config: WebDavBackupConfig): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            validateConfig(config)
            val authHeader = Credentials.basic(config.username, config.password)
            val directoryUrl = ensureRemoteDirectory(config, authHeader)
            listBackupsInternal(directoryUrl, authHeader)
            Unit
        }
    }

    suspend fun listBackups(config: WebDavBackupConfig): Result<List<WebDavBackupEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            validateConfig(config)
            val authHeader = Credentials.basic(config.username, config.password)
            val directoryUrl = ensureRemoteDirectory(config, authHeader)
            listBackupsInternal(directoryUrl, authHeader)
        }
    }

    suspend fun backupNow(config: WebDavBackupConfig): Result<WebDavBackupEntry> = withContext(Dispatchers.IO) {
        runCatching {
            validateConfig(config)
            val authHeader = Credentials.basic(config.username, config.password)
            val directoryUrl = ensureRemoteDirectory(config, authHeader)

            val now = System.currentTimeMillis()
            val fileName = buildWebDavBackupFileName(now)
            val uploadUrl = "$directoryUrl/$fileName"
            val archiveBytes = buildBackupArchive(now)

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

            WebDavBackupEntry(
                fileName = fileName,
                href = uploadUrl,
                sizeBytes = archiveBytes.size.toLong(),
                lastModifiedEpochMs = now
            )
        }
    }

    suspend fun restoreLatest(config: WebDavBackupConfig): Result<WebDavBackupEntry> = withContext(Dispatchers.IO) {
        runCatching {
            validateConfig(config)
            val authHeader = Credentials.basic(config.username, config.password)
            val directoryUrl = ensureRemoteDirectory(config, authHeader)
            val entries = listBackupsInternal(directoryUrl, authHeader)
            val latest = selectLatestWebDavBackup(entries)
                ?: throw IllegalStateException("未找到可恢复的备份文件")

            val downloadUrl = resolveWebDavDownloadUrl(config.baseUrl, latest.href)
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

            val restoredCount = restoreFromBackupArchive(zipBytes)
            if (restoredCount <= 0) {
                throw IOException("备份文件无可恢复内容")
            }

            latest
        }
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
                WebDavDirectoryAction.EXISTS -> Unit // 已存在,跳过创建

                WebDavDirectoryAction.CREATE -> {
                    mkcolDirectory(current, authHeader)
                }

                WebDavDirectoryAction.CREATE_OR_VERIFY -> {
                    // 探测异常(部分服务器不支持对目录 Depth:0 PROPFIND):
                    // 回退幂等 MKCOL,再探测确认。
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

    /** PROPFIND Depth: 0 探测目录是否存在;返回 HTTP 状态码,网络异常返回 null。 */
    private fun probeDirectoryDepthZero(url: String, authHeader: String): Int? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", authHeader)
                .header("Depth", "0")
                .method("PROPFIND", buildWebDavPropfindBody().toRequestBody(XML_CONTENT_TYPE.toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { it.code }
        } catch (e: IOException) {
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

    private fun buildBackupArchive(nowEpochMs: Long): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val manifest = """
                {
                  "createdAtEpochMs": $nowEpochMs,
                  "createdAtIso": "${Instant.ofEpochMilli(nowEpochMs)}",
                  "appVersion": "${BuildConfig.VERSION_NAME}",
                  "scope": [
                    "$ZIP_ENTRY_DATASTORE_SETTINGS",
                    "$ZIP_ENTRY_DATASTORE_PLUGIN",
                    "$ZIP_ENTRY_SHARED_PREFS_JSON_PLUGIN",
                    "${ZIP_ENTRY_JSON_PLUGIN_DIR}*.json"
                  ]
                }
            """.trimIndent()
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            addFileIfExists(
                zip = zip,
                file = File(context.filesDir, "datastore/settings_prefs.preferences_pb"),
                entryName = ZIP_ENTRY_DATASTORE_SETTINGS
            )
            addFileIfExists(
                zip = zip,
                file = File(context.filesDir, "datastore/plugin_prefs.preferences_pb"),
                entryName = ZIP_ENTRY_DATASTORE_PLUGIN
            )

            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            addFileIfExists(
                zip = zip,
                file = File(sharedPrefsDir, "json_plugins.xml"),
                entryName = ZIP_ENTRY_SHARED_PREFS_JSON_PLUGIN
            )

            val pluginDir = File(context.filesDir, "json_plugins")
            if (pluginDir.exists() && pluginDir.isDirectory) {
                pluginDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                    ?.sortedBy { it.name }
                    ?.forEach { file ->
                        addFileIfExists(zip, file, "$ZIP_ENTRY_JSON_PLUGIN_DIR${file.name}")
                    }
            }
        }

        return output.toByteArray()
    }

    private fun addFileIfExists(zip: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists() || !file.isFile) return
        zip.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }

    private fun restoreFromBackupArchive(zipBytes: ByteArray): Int {
        var restored = 0

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val entryName = entry.name.trim()
                    val target = resolveRestoreTarget(entryName)
                    if (target != null) {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { output -> zipInput.copyTo(output) }
                        restored++
                    }
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }

        return restored
    }

    private fun resolveRestoreTarget(entryName: String): File? {
        if (entryName.contains("..")) return null
        return when {
            entryName == ZIP_ENTRY_DATASTORE_SETTINGS -> File(context.filesDir, "datastore/settings_prefs.preferences_pb")
            entryName == ZIP_ENTRY_DATASTORE_PLUGIN -> File(context.filesDir, "datastore/plugin_prefs.preferences_pb")
            entryName == ZIP_ENTRY_SHARED_PREFS_JSON_PLUGIN -> File(context.applicationInfo.dataDir, "shared_prefs/json_plugins.xml")
            entryName.startsWith(ZIP_ENTRY_JSON_PLUGIN_DIR) -> {
                val fileName = entryName.removePrefix(ZIP_ENTRY_JSON_PLUGIN_DIR)
                if (fileName.isBlank()) null else File(context.filesDir, "json_plugins/$fileName")
            }
            else -> null
        }
    }
}
