package com.android.purebilibili.feature.settings.backup

import android.content.Context
import com.android.purebilibili.BuildConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal const val APP_BACKUP_SCHEMA_VERSION = 2
internal const val APP_BACKUP_MANIFEST_ENTRY = "manifest.json"

private const val ENTRY_DATASTORE_SETTINGS = "datastore/settings_prefs.preferences_pb"
private const val ENTRY_DATASTORE_PLUGIN = "datastore/plugin_prefs.preferences_pb"
private const val ENTRY_SHARED_PREFS_JSON_PLUGIN = "shared_prefs/json_plugins.xml"
private const val ENTRY_SHARED_PREFS_JS_LAYOUT_PRESETS = "shared_prefs/bilipai_js_layout_presets.xml"
private const val ENTRY_SHARED_PREFS_KEEPSAKES = "shared_prefs/completion_keepsakes.xml"
private const val ENTRY_SHARED_PREFS_TODAY_PROFILE = "shared_prefs/today_watch_profile.xml"
private const val ENTRY_SHARED_PREFS_TODAY_FEEDBACK = "shared_prefs/today_watch_feedback.xml"
private const val ENTRY_SHARED_PREFS_PLAYLIST_STATE = "shared_prefs/playlist_manager_state.xml"

private const val DIR_JSON_PLUGINS = "json_plugins/"
private const val DIR_SETTINGS_PROFILES = "files/settings-profiles/"
private const val DIR_LIQUID_GLASS_PROFILES = "files/liquid-glass-profiles/"

private const val MAX_RESTORE_ENTRY_BYTES = 16L * 1024L * 1024L

private val BACKUP_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())

private val backupManifestJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

enum class AppBackupScope(
    val id: String,
    val label: String,
    val description: String,
) {
    SETTINGS(
        id = "settings",
        label = "应用设置与预设",
        description = "外观、播放、手势、弹幕、导航与已保存配置预设",
    ),
    PLUGINS(
        id = "plugins",
        label = "插件与扩展",
        description = "插件开关、JSON 插件文件与布局预设",
    ),
    PERSONAL(
        id = "personal",
        label = "纪念与本地偏好",
        description = "完结纪念短评、今日推荐偏好与本地播单状态",
    );

    companion object {
        fun fromId(id: String): AppBackupScope? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
data class AppBackupManifest(
    val schemaVersion: Int = APP_BACKUP_SCHEMA_VERSION,
    val app: String = "BiliPai",
    val appVersion: String = "",
    val createdAtEpochMs: Long = 0L,
    val createdAtIso: String = "",
    val includedScopes: List<String> = emptyList(),
    val entryCount: Int = 0,
    val totalRawBytes: Long = 0L,
    val entries: List<String> = emptyList(),
)

data class AppBackupArchiveInspection(
    val manifest: AppBackupManifest?,
    val restorableEntryCount: Int,
    val detectedScopes: Set<AppBackupScope>,
    val isLegacyV1: Boolean,
)

data class AppBackupRestoreReport(
    val restoredCount: Int,
    val skippedCount: Int,
    val restoredScopes: Set<AppBackupScope>,
    val manifest: AppBackupManifest?,
)

internal fun classifyBackupEntryScope(rawEntryName: String): AppBackupScope? {
    val entryName = rawEntryName.trim()
    if (entryName.isEmpty() || entryName.contains("..") || entryName.startsWith("/") || entryName.contains('\\')) {
        return null
    }
    return when {
        entryName == ENTRY_DATASTORE_SETTINGS -> AppBackupScope.SETTINGS
        isSafeSingleJsonFileInDir(entryName, DIR_SETTINGS_PROFILES) -> AppBackupScope.SETTINGS
        isSafeSingleJsonFileInDir(entryName, DIR_LIQUID_GLASS_PROFILES) -> AppBackupScope.SETTINGS

        entryName == ENTRY_DATASTORE_PLUGIN -> AppBackupScope.PLUGINS
        entryName == ENTRY_SHARED_PREFS_JSON_PLUGIN -> AppBackupScope.PLUGINS
        entryName == ENTRY_SHARED_PREFS_JS_LAYOUT_PRESETS -> AppBackupScope.PLUGINS
        isSafeSingleJsonFileInDir(entryName, DIR_JSON_PLUGINS) -> AppBackupScope.PLUGINS

        entryName == ENTRY_SHARED_PREFS_KEEPSAKES -> AppBackupScope.PERSONAL
        entryName == ENTRY_SHARED_PREFS_TODAY_PROFILE -> AppBackupScope.PERSONAL
        entryName == ENTRY_SHARED_PREFS_TODAY_FEEDBACK -> AppBackupScope.PERSONAL
        entryName == ENTRY_SHARED_PREFS_PLAYLIST_STATE -> AppBackupScope.PERSONAL

        else -> null
    }
}

internal fun formatBackupSize(sizeBytes: Long): String {
    val safe = sizeBytes.coerceAtLeast(0L)
    return when {
        safe < 1024L -> "$safe B"
        safe < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", safe / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", safe / (1024.0 * 1024.0))
    }
}

internal fun formatBackupTimestamp(
    epochMs: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    if (epochMs <= 0L) return "未知时间"
    return BACKUP_TIME_FORMATTER.format(Instant.ofEpochMilli(epochMs).atZone(zoneId))
}

private fun isSafeSingleJsonFileInDir(entryName: String, dirPrefix: String): Boolean {
    if (!entryName.startsWith(dirPrefix)) return false
    val fileName = entryName.removePrefix(dirPrefix)
    if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) return false
    return fileName.endsWith(".json", ignoreCase = true)
}

/**
 * 统一应用备份归档引擎：
 * - 按作用域（设置/插件/纪念与本地偏好）打包与选择性恢复，显式排除登录凭据与 WebDAV 密码。
 * - 兼容旧版 v1 备份包（仅含 settings/plugin DataStore 与 json_plugins）。
 * - 恢复写入采用临时文件原子替换与规范路径边界校验，避免中途中断破坏本地持久化文件。
 */
internal class AppBackupArchiveEngine(
    private val filesDir: File,
    private val sharedPrefsDir: File,
    private val appVersion: String,
) {
    constructor(context: Context) : this(
        filesDir = context.filesDir,
        sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs"),
        appVersion = BuildConfig.VERSION_NAME,
    )

    fun createArchive(
        scopes: Set<AppBackupScope> = AppBackupScope.entries.toSet(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ): ByteArray {
        val activeScopes = scopes.ifEmpty { AppBackupScope.entries.toSet() }
        val sources = collectSourceEntries(activeScopes)
        val totalRawBytes = sources.sumOf { it.second.length() }
        val manifest = AppBackupManifest(
            schemaVersion = APP_BACKUP_SCHEMA_VERSION,
            appVersion = appVersion,
            createdAtEpochMs = nowEpochMs,
            createdAtIso = Instant.ofEpochMilli(nowEpochMs).toString(),
            includedScopes = activeScopes.map { it.id }.sorted(),
            entryCount = sources.size,
            totalRawBytes = totalRawBytes,
            entries = sources.map { it.first },
        )

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(APP_BACKUP_MANIFEST_ENTRY))
            zip.write(backupManifestJson.encodeToString(AppBackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            for ((entryName, file) in sources) {
                if (!file.exists() || !file.isFile) continue
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    fun inspectArchive(zipBytes: ByteArray): AppBackupArchiveInspection {
        var manifest: AppBackupManifest? = null
        val detectedScopes = linkedSetOf<AppBackupScope>()
        var restorableEntries = 0

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.trim()
                    if (name == APP_BACKUP_MANIFEST_ENTRY) {
                        val raw = readBoundedBytes(zip).toString(Charsets.UTF_8)
                        manifest = runCatching {
                            backupManifestJson.decodeFromString(AppBackupManifest.serializer(), raw)
                        }.getOrNull()
                    } else {
                        val scope = classifyBackupEntryScope(name)
                        if (scope != null) {
                            detectedScopes += scope
                            restorableEntries++
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val isLegacyV1 = manifest == null || (manifest?.schemaVersion ?: 1) < APP_BACKUP_SCHEMA_VERSION
        return AppBackupArchiveInspection(
            manifest = manifest,
            restorableEntryCount = restorableEntries,
            detectedScopes = detectedScopes,
            isLegacyV1 = isLegacyV1,
        )
    }

    fun restoreArchive(
        zipBytes: ByteArray,
        allowedScopes: Set<AppBackupScope> = AppBackupScope.entries.toSet(),
    ): AppBackupRestoreReport {
        val activeScopes = allowedScopes.ifEmpty { AppBackupScope.entries.toSet() }
        var manifest: AppBackupManifest? = null
        var restoredCount = 0
        var skippedCount = 0
        val restoredScopes = linkedSetOf<AppBackupScope>()

        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.trim()
                    if (name == APP_BACKUP_MANIFEST_ENTRY) {
                        val raw = readBoundedBytes(zip).toString(Charsets.UTF_8)
                        manifest = runCatching {
                            backupManifestJson.decodeFromString(AppBackupManifest.serializer(), raw)
                        }.getOrNull()
                    } else {
                        val scope = classifyBackupEntryScope(name)
                        val target = if (scope != null && scope in activeScopes) resolveTargetFile(name) else null
                        if (scope != null && target != null) {
                            val bytes = readBoundedBytes(zip)
                            writeFileAtomically(target, bytes)
                            restoredCount++
                            restoredScopes += scope
                        } else {
                            skippedCount++
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        return AppBackupRestoreReport(
            restoredCount = restoredCount,
            skippedCount = skippedCount,
            restoredScopes = restoredScopes,
            manifest = manifest,
        )
    }

    private fun collectSourceEntries(scopes: Set<AppBackupScope>): List<Pair<String, File>> {
        val result = mutableListOf<Pair<String, File>>()

        fun addIfPresent(entryName: String, file: File) {
            if (file.exists() && file.isFile) {
                result += entryName to file
            }
        }

        fun addJsonDirectory(entryPrefix: String, dir: File) {
            if (!dir.exists() || !dir.isDirectory) return
            dir.listFiles()
                ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?.forEach { file ->
                    result += "$entryPrefix${file.name}" to file
                }
        }

        if (AppBackupScope.SETTINGS in scopes) {
            addIfPresent(ENTRY_DATASTORE_SETTINGS, File(filesDir, "datastore/settings_prefs.preferences_pb"))
            addJsonDirectory(DIR_SETTINGS_PROFILES, File(filesDir, "settings-profiles"))
            addJsonDirectory(DIR_LIQUID_GLASS_PROFILES, File(filesDir, "liquid-glass-profiles"))
        }

        if (AppBackupScope.PLUGINS in scopes) {
            addIfPresent(ENTRY_DATASTORE_PLUGIN, File(filesDir, "datastore/plugin_prefs.preferences_pb"))
            addIfPresent(ENTRY_SHARED_PREFS_JSON_PLUGIN, File(sharedPrefsDir, "json_plugins.xml"))
            addIfPresent(ENTRY_SHARED_PREFS_JS_LAYOUT_PRESETS, File(sharedPrefsDir, "bilipai_js_layout_presets.xml"))
            addJsonDirectory(DIR_JSON_PLUGINS, File(filesDir, "json_plugins"))
        }

        if (AppBackupScope.PERSONAL in scopes) {
            addIfPresent(ENTRY_SHARED_PREFS_KEEPSAKES, File(sharedPrefsDir, "completion_keepsakes.xml"))
            addIfPresent(ENTRY_SHARED_PREFS_TODAY_PROFILE, File(sharedPrefsDir, "today_watch_profile.xml"))
            addIfPresent(ENTRY_SHARED_PREFS_TODAY_FEEDBACK, File(sharedPrefsDir, "today_watch_feedback.xml"))
            addIfPresent(ENTRY_SHARED_PREFS_PLAYLIST_STATE, File(sharedPrefsDir, "playlist_manager_state.xml"))
        }

        return result
    }

    private fun resolveTargetFile(entryName: String): File? {
        val (baseDir, candidate) = when {
            entryName == ENTRY_DATASTORE_SETTINGS -> filesDir to File(filesDir, "datastore/settings_prefs.preferences_pb")
            entryName == ENTRY_DATASTORE_PLUGIN -> filesDir to File(filesDir, "datastore/plugin_prefs.preferences_pb")
            entryName == ENTRY_SHARED_PREFS_JSON_PLUGIN -> sharedPrefsDir to File(sharedPrefsDir, "json_plugins.xml")
            entryName == ENTRY_SHARED_PREFS_JS_LAYOUT_PRESETS -> sharedPrefsDir to File(sharedPrefsDir, "bilipai_js_layout_presets.xml")
            entryName == ENTRY_SHARED_PREFS_KEEPSAKES -> sharedPrefsDir to File(sharedPrefsDir, "completion_keepsakes.xml")
            entryName == ENTRY_SHARED_PREFS_TODAY_PROFILE -> sharedPrefsDir to File(sharedPrefsDir, "today_watch_profile.xml")
            entryName == ENTRY_SHARED_PREFS_TODAY_FEEDBACK -> sharedPrefsDir to File(sharedPrefsDir, "today_watch_feedback.xml")
            entryName == ENTRY_SHARED_PREFS_PLAYLIST_STATE -> sharedPrefsDir to File(sharedPrefsDir, "playlist_manager_state.xml")
            entryName.startsWith(DIR_JSON_PLUGINS) -> {
                val fileName = entryName.removePrefix(DIR_JSON_PLUGINS)
                filesDir to File(filesDir, "json_plugins/$fileName")
            }
            entryName.startsWith(DIR_SETTINGS_PROFILES) -> {
                val fileName = entryName.removePrefix(DIR_SETTINGS_PROFILES)
                filesDir to File(filesDir, "settings-profiles/$fileName")
            }
            entryName.startsWith(DIR_LIQUID_GLASS_PROFILES) -> {
                val fileName = entryName.removePrefix(DIR_LIQUID_GLASS_PROFILES)
                filesDir to File(filesDir, "liquid-glass-profiles/$fileName")
            }
            else -> return null
        }

        val canonicalBase = baseDir.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        val basePath = canonicalBase.path.let { if (it.endsWith(File.separator)) it else it + File.separator }
        if (!canonicalCandidate.path.startsWith(basePath)) return null
        return candidate
    }

    private fun readBoundedBytes(input: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > MAX_RESTORE_ENTRY_BYTES) {
                throw IllegalStateException("备份内单文件体积超出安全限制")
            }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private fun writeFileAtomically(target: File, bytes: ByteArray) {
        val parent = target.parentFile ?: error("无效的目标路径")
        if (!parent.exists()) parent.mkdirs()
        val temp = File(parent, "${target.name}.restore.tmp")
        try {
            temp.outputStream().use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            if (!temp.renameTo(target)) {
                target.writeBytes(bytes)
                temp.delete()
            }
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }
}
