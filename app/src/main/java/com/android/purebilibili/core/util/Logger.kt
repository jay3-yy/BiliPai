// 文件路径: core/util/Logger.kt
package com.android.purebilibili.core.util

import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.core.performance.Android17Diagnostics
import com.android.purebilibili.core.performance.AbnormalProcessExitException
import com.android.purebilibili.core.performance.decodeNativeExitTrace
import com.android.purebilibili.core.performance.nativeExitTraceSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

private const val LOG_DIRECTORY_NAME = "logs"
private const val RUNTIME_LOG_FILE_NAME = "runtime.log"
private const val BASIC_LOG_FILE_NAME = "basic.log"
private const val CRASH_SNAPSHOT_FILE_NAME = "last_crash_log.txt"
private const val RAW_CRASH_TRACE_FILE_NAME = "last_crash_trace.pb"
private const val CRASH_SNAPSHOT_MARKER_FILE_NAME = "pending_crash.marker"
private const val TOMBSTONE_BEGIN = "----- BEGIN TOMBSTONE PROTOBUF BASE64 -----"
private const val TOMBSTONE_END = "----- END TOMBSTONE PROTOBUF BASE64 -----"
private const val DOWNLOAD_LOG_RELATIVE_PATH = "Download/BiliPai/logs"
internal const val ENHANCED_DIAGNOSTIC_LOG_PREFS_NAME = "diagnostic_logging"
internal const val ENHANCED_DIAGNOSTIC_LOG_PREF_KEY = "enhanced_enabled"

internal fun resolveLogPersistenceDir(baseDir: File): File = File(baseDir, LOG_DIRECTORY_NAME)

internal fun resolveRuntimeLogFile(baseDir: File): File =
    File(resolveLogPersistenceDir(baseDir), RUNTIME_LOG_FILE_NAME)

internal fun resolveBasicLogFile(baseDir: File): File =
    File(resolveLogPersistenceDir(baseDir), BASIC_LOG_FILE_NAME)

internal fun resolveCrashSnapshotFile(baseDir: File): File =
    File(resolveLogPersistenceDir(baseDir), CRASH_SNAPSHOT_FILE_NAME)

internal fun resolveRawCrashTraceFile(baseDir: File): File =
    File(resolveLogPersistenceDir(baseDir), RAW_CRASH_TRACE_FILE_NAME)

internal fun resolveCrashSnapshotMarkerFile(baseDir: File): File =
    File(resolveLogPersistenceDir(baseDir), CRASH_SNAPSHOT_MARKER_FILE_NAME)

internal fun resolvePlayerDiagnosticExportFileName(
    exportedAtMillis: Long
): String {
    val formatter = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    return "player_diagnostic_${formatter.format(Date(exportedAtMillis))}.txt"
}

internal fun shouldEnableVerboseRuntimeLogs(
    isDebugBuild: Boolean,
    verboseDebugLogsEnabled: Boolean,
    enhancedDiagnosticLoggingEnabled: Boolean,
): Boolean = (isDebugBuild && verboseDebugLogsEnabled) || enhancedDiagnosticLoggingEnabled

internal fun shouldEmitVerboseLogcat(
    isDebugBuild: Boolean,
    verboseDebugLogsEnabled: Boolean,
): Boolean = isDebugBuild && verboseDebugLogsEnabled

internal fun shouldCaptureRuntimeLogEntry(
    level: String,
    verboseRuntimeLogsEnabled: Boolean
): Boolean = when (level) {
    "W", "E" -> true
    else -> verboseRuntimeLogsEnabled
}

internal fun shouldPersistRuntimeLogEntry(
    level: String,
    verboseRuntimeLogPersistenceEnabled: Boolean
): Boolean = level == "W" || level == "E" || verboseRuntimeLogPersistenceEnabled

internal fun hasExportableDiagnostics(
    logCount: Int,
    hasCrashSnapshot: Boolean,
    processExitCount: Int,
    profilingArtifactCount: Int
): Boolean = logCount > 0 || hasCrashSnapshot || processExitCount > 0 || profilingArtifactCount > 0

/** Limits bytes, including multibyte UTF-8 messages and a single oversized entry. */
internal fun appendRollingDiagnosticLog(file: File, text: String, maxBytes: Int) {
    require(maxBytes > 0)
    file.parentFile?.mkdirs()
    val incoming = text.toByteArray(Charsets.UTF_8)
    if (file.length() + incoming.size <= maxBytes) {
        file.appendBytes(incoming)
        return
    }
    val existing = if (file.isFile) file.readBytes() else byteArrayOf()
    val combined = existing + incoming
    var start = (combined.size - maxBytes / 2).coerceAtLeast(0)
    // Do not begin a retained file in the middle of a UTF-8 code point.
    while (start < combined.size && (combined[start].toInt() and 0xC0) == 0x80) start++
    file.writeBytes(combined.copyOfRange(start, combined.size))
}

internal fun sanitizeLogMessage(message: String): String =
    LogCollector.sanitizeMessage(message)

/** Older snapshots embedded base64 directly. Never run text redaction across that payload. */
internal fun removeEmbeddedNativeTombstone(content: String): String {
    val start = content.indexOf(TOMBSTONE_BEGIN)
    if (start < 0) return content
    val end = content.indexOf(TOMBSTONE_END, start + TOMBSTONE_BEGIN.length)
    if (end < 0) return content.substring(0, start) + "原始回溯数据不可用（旧版导出不完整）\n"
    return content.replaceRange(
        start,
        end + TOMBSTONE_END.length,
        "原始回溯数据已从文本日志中移除；如有保留，可单独分享 .pb 附件"
    )
}

/** Keep multiline exceptions attached to their timestamp while merging basic and verbose logs. */
internal fun groupDiagnosticLogLines(lines: List<String>): List<String> {
    val entries = mutableListOf<String>()
    var current: StringBuilder? = null
    lines.forEach { line ->
        if (diagnosticTimestampKey(line) != null) {
            current?.toString()?.takeIf(String::isNotBlank)?.let(entries::add)
            current = StringBuilder(line)
        } else if (current != null) {
            current?.apply { append('\n'); append(line) }
        } else if (line.isNotBlank()) {
            entries.add(line)
        }
    }
    current?.toString()?.takeIf(String::isNotBlank)?.let(entries::add)
    return entries
}

internal fun mergeDiagnosticLogEntries(
    persistedEntries: List<String>,
    inMemoryEntries: List<String>,
): List<String> = (persistedEntries + inMemoryEntries)
    .filter(String::isNotBlank)
    .distinct()
    .sortedBy { diagnosticTimestampKey(it) ?: "9999-99-99 99:99:99.999" }

private fun diagnosticTimestampKey(entry: String): String? =
    entry.takeIf { it.length >= 25 && it[0] == '[' && it[24] == ']' }
        ?.substring(1, 24)

/** Keep the last ten minutes before this exit, never the new process' startup entries. */
internal fun selectLogsBeforeProcessExit(entries: List<String>, exitAtMillis: Long): List<String> {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        .apply { isLenient = false }
    val beforeExit = entries.distinct().mapNotNull { entry ->
        val time = diagnosticTimestampKey(entry)?.let { key ->
            runCatching { formatter.parse(key)?.time }.getOrNull()
        } ?: return@mapNotNull null
        if (time in (exitAtMillis - 10 * 60_000L)..exitAtMillis) time to entry else null
    }.sortedBy { it.first }
    val launchIndex = beforeExit.indexOfLast { (_, entry) ->
        entry.contains("I/StartupDiagnostics: stage=application_start,")
    }
    return beforeExit.drop(launchIndex.coerceAtLeast(0)).takeLast(120).map { it.second }
}

internal fun resolveLogArtifactDirsToClear(
    filesDir: File,
    cacheDir: File
): List<File> = listOf(
    resolveLogPersistenceDir(filesDir),
    resolveLogPersistenceDir(cacheDir)
).distinctBy { it.absolutePath }

internal fun hasPendingCrashSnapshot(
    markerExists: Boolean,
    snapshotExists: Boolean
): Boolean = markerExists && snapshotExists

internal fun buildCrashSnapshotContent(
    throwable: Throwable,
    entries: List<LogCollector.LogEntry>,
    exportedAtMillis: Long,
    appVersionName: String,
    versionCode: Int,
    manufacturer: String,
    model: String,
    androidRelease: String,
    apiLevel: Int,
    buildType: String = "unknown",
    buildCommit: String = "unknown",
    previousProcessLogs: List<String> = emptyList(),
): String {
    val headerDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    return buildString {
        appendLine("========================================")
        appendLine("BiliPai 崩溃日志快照")
        appendLine("========================================")
        appendLine("生成时间: ${headerDateFormat.format(Date(exportedAtMillis))}")
        appendLine("应用版本: $appVersionName ($versionCode)")
        appendLine("构建: $buildType / $buildCommit")
        appendLine("设备信息: $manufacturer $model")
        appendLine("Android版本: $androidRelease (API $apiLevel)")
        if (throwable is AbnormalProcessExitException) {
            appendLine("版本说明: 上方为采集本日志的应用版本；退出前版本以进程摘要为准")
            appendLine("退出前进程摘要: ${throwable.processContext ?: "不可用（旧版本或系统未保留）"}")
        }
        appendLine("异常类型: ${throwable.javaClass.simpleName}")
        appendLine("异常信息: ${sanitizeLogMessage(throwable.message.orEmpty())}")
        appendLine("========================================")
        appendLine()
        appendLine("----- Throwable -----")
        val nativeTrace = (throwable as? AbnormalProcessExitException)?.nativeTrace
        val throwableText = if (!nativeTrace.isNullOrBlank()) {
            buildString {
                appendLine(throwable.toString())
                appendLine()
                appendLine("----- 系统异常回溯摘要 -----")
                appendLine(nativeExitTraceSummary(nativeTrace))
                if (nativeTrace.contains(TOMBSTONE_BEGIN)) {
                    appendLine("原始回溯: 不在本文本内；如保存成功，可主动选择分享 .pb 附件")
                }
            }
        } else {
            throwable.stackTraceToString()
        }
        appendLine(sanitizeLogMessage(throwableText))
        if (throwable is AbnormalProcessExitException) {
            appendLine("----- 崩溃前持久化日志（按退出时间筛选） -----")
            if (previousProcessLogs.isEmpty()) appendLine("无可用记录；未落盘的末尾日志可能丢失")
            previousProcessLogs.forEach { appendLine(sanitizeLogMessage(it)) }
            appendLine("----- 当前重启日志（不代表崩溃前操作） -----")
        } else appendLine("----- Recent Logs -----")
        entries.forEach { appendLine(it.format()) }
    }
}

/**
 *  统一日志工具类
 * 
 * 默认在私有目录滚动保留警告、错误及最小启动诊断；用户主动开启增强诊断后，另行
 * 滚动保存脱敏后的 Debug/Info 日志，支持导出供用户反馈。
 */
object Logger {

    /** Fixed startup stage names only; no links, account identifiers or user input. */
    fun recordStartupStage(stage: String) {
        com.android.purebilibili.core.performance.ProcessExitContextRecorder.stage(stage)
        LogCollector.add(
            level = "I",
            tag = "StartupDiagnostics",
            message = "stage=$stage, app=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}), " +
                "build=${BuildConfig.BUILD_TYPE}/${BuildConfig.BUILD_COMMIT_SHA.take(12)}, " +
                "android=${android.os.Build.VERSION.RELEASE}(API ${android.os.Build.VERSION.SDK_INT}), " +
                "device=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}",
            persistToDisk = true,
            basicDiagnostic = true,
        )
    }

    private val debugVerboseLogsEnabled = shouldEmitVerboseLogcat(
        isDebugBuild = BuildConfig.DEBUG,
        verboseDebugLogsEnabled = BuildConfig.ENABLE_VERBOSE_DEBUG_LOGS,
    )
    @Volatile
    private var enhancedDiagnosticLoggingEnabled = false
    @Volatile
    private var diagnosticSessionRecorded = false

    @PublishedApi
    internal fun areVerboseRuntimeLogsEnabled(): Boolean = shouldEnableVerboseRuntimeLogs(
        isDebugBuild = BuildConfig.DEBUG,
        verboseDebugLogsEnabled = BuildConfig.ENABLE_VERBOSE_DEBUG_LOGS,
        enhancedDiagnosticLoggingEnabled = enhancedDiagnosticLoggingEnabled,
    )

    @PublishedApi
    internal fun isVerboseRuntimeLogPersistenceEnabled(): Boolean =
        enhancedDiagnosticLoggingEnabled ||
            (debugVerboseLogsEnabled && BuildConfig.ENABLE_VERBOSE_RUNTIME_LOG_PERSISTENCE)

    fun init(context: Context) {
        val applicationContext = context.applicationContext
        LogCollector.init(applicationContext)
        enhancedDiagnosticLoggingEnabled = applicationContext
            .getSharedPreferences(ENHANCED_DIAGNOSTIC_LOG_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(ENHANCED_DIAGNOSTIC_LOG_PREF_KEY, false)
        if (enhancedDiagnosticLoggingEnabled) {
            recordDiagnosticSessionStart(applicationContext)
        }
    }

    fun configureEnhancedDiagnosticLogging(context: Context, enabled: Boolean) {
        val applicationContext = context.applicationContext
        LogCollector.init(applicationContext)
        enhancedDiagnosticLoggingEnabled = enabled
        if (enabled) {
            recordDiagnosticSessionStart(applicationContext)
        } else {
            diagnosticSessionRecorded = false
            LogCollector.clearRuntimeDiagnostics()
        }
    }

    private fun recordDiagnosticSessionStart(context: Context) {
        if (diagnosticSessionRecorded) return
        synchronized(this) {
            if (diagnosticSessionRecorded) return
            diagnosticSessionRecorded = true
        }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val configuration = context.resources.configuration
        LogCollector.add(
            level = "I",
            tag = "Diagnostics",
            message = "增强诊断已开启；仅保存在应用私有目录，导出前会再次脱敏，滚动上限=256KB",
            persistToDisk = true,
        )
        LogCollector.add(
            level = "I",
            tag = "Diagnostics",
            message = "app=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}), " +
                "build=${BuildConfig.BUILD_COMMIT_SHA.take(12)}, debug=${BuildConfig.DEBUG}",
            persistToDisk = true,
        )
        LogCollector.add(
            level = "I",
            tag = "Diagnostics",
            message = "device=${android.os.Build.MANUFACTURER}/${android.os.Build.MODEL}, " +
                "android=${android.os.Build.VERSION.RELEASE}(API ${android.os.Build.VERSION.SDK_INT}), " +
                "abis=${android.os.Build.SUPPORTED_ABIS.joinToString()}, locale=${configuration.locales[0]}, " +
                "densityDpi=${configuration.densityDpi}, memoryClassMb=${activityManager?.memoryClass}, " +
                "lowRam=${activityManager?.isLowRamDevice}",
            persistToDisk = true,
        )
    }
    
    /**
     * Debug 日志 - Debug 包输出到 Logcat；正式版仅在用户主动开启增强诊断后收集
     */
    fun d(tag: String, message: String) {
        val verboseRuntimeLogsEnabled = areVerboseRuntimeLogsEnabled()
        if (!verboseRuntimeLogsEnabled) return
        if (debugVerboseLogsEnabled) Log.d(tag, message)
        if (shouldCaptureRuntimeLogEntry("D", verboseRuntimeLogsEnabled)) {
            LogCollector.add(
                level = "D",
                tag = tag,
                message = message,
                persistToDisk = shouldPersistRuntimeLogEntry(
                    "D",
                    isVerboseRuntimeLogPersistenceEnabled(),
                )
            )
        }
    }

    inline fun d(tag: String, message: () -> String) {
        if (!areVerboseRuntimeLogsEnabled()) return
        d(tag, message())
    }
    
    /**
     * Info 日志 - Debug 包输出到 Logcat；正式版仅在用户主动开启增强诊断后收集
     */
    fun i(tag: String, message: String) {
        val verboseRuntimeLogsEnabled = areVerboseRuntimeLogsEnabled()
        if (!verboseRuntimeLogsEnabled) return
        if (debugVerboseLogsEnabled) Log.i(tag, message)
        if (shouldCaptureRuntimeLogEntry("I", verboseRuntimeLogsEnabled)) {
            LogCollector.add(
                level = "I",
                tag = tag,
                message = message,
                persistToDisk = shouldPersistRuntimeLogEntry(
                    "I",
                    isVerboseRuntimeLogPersistenceEnabled(),
                )
            )
        }
    }

    inline fun i(tag: String, message: () -> String) {
        if (!areVerboseRuntimeLogsEnabled()) return
        i(tag, message())
    }
    
    /**
     * Warning 日志 - 始终输出
     */
    fun w(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else message
        
        if (BuildConfig.DEBUG && throwable != null) {
            Log.w(tag, message, throwable)
        } else if (BuildConfig.DEBUG) {
            Log.w(tag, message)
        } else {
            Log.w(tag, sanitizeLogMessage(fullMessage))
        }
        val verboseRuntimeLogsEnabled = areVerboseRuntimeLogsEnabled()
        if (shouldCaptureRuntimeLogEntry("W", verboseRuntimeLogsEnabled)) {
            LogCollector.add(
                level = "W",
                tag = tag,
                message = fullMessage,
                persistToDisk = shouldPersistRuntimeLogEntry(
                    "W",
                    isVerboseRuntimeLogPersistenceEnabled(),
                )
            )
        }
    }
    
    /**
     * Error 日志 - 始终输出
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else message
        
        if (BuildConfig.DEBUG && throwable != null) {
            Log.e(tag, message, throwable)
        } else if (BuildConfig.DEBUG) {
            Log.e(tag, message)
        } else {
            Log.e(tag, sanitizeLogMessage(fullMessage))
        }
        val verboseRuntimeLogsEnabled = areVerboseRuntimeLogsEnabled()
        if (shouldCaptureRuntimeLogEntry("E", verboseRuntimeLogsEnabled)) {
            LogCollector.add(
                level = "E",
                tag = tag,
                message = fullMessage,
                persistToDisk = shouldPersistRuntimeLogEntry(
                    "E",
                    isVerboseRuntimeLogPersistenceEnabled(),
                )
            )
        }
    }

    fun persistCrashSnapshot(throwable: Throwable) {
        LogCollector.persistCrashSnapshot(throwable)
    }

    fun getPendingCrashSnapshotPath(context: Context): String? {
        init(context)
        return LogCollector.getPendingCrashSnapshotFile()?.absolutePath
    }

    /** Returns whether a crash snapshot is waiting for the user to review/share. */
    fun hasPendingCrashSnapshot(context: Context): Boolean =
        getPendingCrashSnapshotPath(context) != null

    fun clearPendingCrashSnapshot(context: Context) {
        init(context)
        LogCollector.clearPendingCrashSnapshot()
    }

    fun sharePendingCrashSnapshot(context: Context): Boolean {
        init(context)
        return LogCollector.sharePendingCrashSnapshot(context)
    }

    fun getPrivateLogArtifactsSize(context: Context): Long {
        init(context)
        val persistedLogDir = resolveLogPersistenceDir(context.filesDir)
        return if (!persistedLogDir.exists()) {
            0L
        } else {
            persistedLogDir.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        }
    }

    fun clearPrivateLogArtifacts(context: Context) {
        init(context)
        LogCollector.clear()
        LogCollector.clearPendingCrashSnapshot()
        resolveLogArtifactDirsToClear(
            filesDir = context.filesDir,
            cacheDir = context.cacheDir
        ).forEach { dir ->
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        }
    }

    fun exportPlayerDiagnostic(
        context: Context,
        content: String,
        exportedAtMillis: Long = System.currentTimeMillis()
    ): String? {
        init(context)
        val fileName = resolvePlayerDiagnosticExportFileName(exportedAtMillis)
        return LogCollector.saveTextArtifact(
            context = context,
            fileName = fileName,
            content = content,
            replaceExisting = false
        )
    }
}

/** Immutable patterns are shared across logging threads; replacement order stays in sanitizeMessage. */
private object LogRedactionPatterns {
    val sessionCookie = Regex("SESSDATA=[^;\\s]+")
    val csrfCookie = Regex("bili_jct=[^;\\s]+")
    val userCookie = Regex("DedeUserID=[^;\\s]+")
    val userCookieChecksum = Regex("DedeUserID__ckMd5=[^;\\s]+")
    val sidCookie = Regex("sid=[^;\\s]+")
    val buvid3Cookie = Regex("buvid3=[^;\\s]+")
    val buvid4Cookie = Regex("buvid4=[^;\\s]+")
    val nutCookie = Regex("b_nut=[^;\\s]+")
    val uuidCookie = Regex("_uuid=[^;\\s]+")
    val accessToken = Regex("access_token=[^&\\s]+")
    val refreshToken = Regex("refresh_token=[^&\\s]+")
    val accessKey = Regex("access_key=[^&\\s]+")
    val appKey = Regex("appkey=[^&\\s]+")
    val signature = Regex("sign=[^&\\s]+")
    val csrf = Regex("csrf=[^&\\s]+")
    val jsonToken = Regex("\"token\":\"[^\"]+\"")
    val jsonCsrf = Regex("\"csrf\":\"[^\"]+\"")
    val authorization = Regex("(?i)Authorization\\s*[:=]\\s*[^\\r\\n]+")
    val bearer = Regex("Bearer\\s+[^\\s]+")
    val cookieHeader = Regex("(?i)(cookie|set-cookie)\\s*[:=]\\s*[^\\r\\n]+")
    val password = Regex("(?i)(password|passwd|pwd|sms_code|captcha|challenge|validate)[=:]\\s*[^&\\s,}]+")
    val userMid = Regex("mid[=:]\\s*\\d{4,}")
    val jsonMid = Regex("\"mid\":\\s*\\d+")
    val userUid = Regex("uid[=:]\\s*\\d{4,}")
    val jsonUid = Regex("\"uid\":\\s*\\d+")
    val visitorMid = Regex("vmid[=:]\\s*\\d+")
    val phone = Regex("\\b1[3-9]\\d{9}\\b")
    val email = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    val ipv4 = Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")
    val ipv6 = Regex("\\b[0-9a-fA-F:]{15,}\\b")
    val mac = Regex("([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}")
    val androidDataPath = Regex("/data/user/\\d+/[^/]+/")
    val androidStoragePath = Regex("/storage/emulated/\\d+/")
    val linuxHomePath = Regex("/home/[^/]+/")
    val macHomePath = Regex("/Users/[^/]+/")
    val deviceId = Regex("device_id=[^&\\s]+")
    val jsonDeviceId = Regex("\"device_id\":\"[^\"]+\"")
    val androidId = Regex("android_id=[^&\\s]+")
    val imei = Regex("imei=[^&\\s]+")
    val jsonFace = Regex("\"face\":\"[^\"]+\"")
    val jsonPhone = Regex("\"tel\":\"[^\"]+\"")
    val jsonName = Regex("\"name\":\"[^\"]{2,}\"")
    val bvid = Regex("BV[0-9A-Za-z]{10}")
    val avid = Regex("\\bav\\d{4,}\\b", RegexOption.IGNORE_CASE)
    val jsonAid = Regex("\"aid\":\\s*\\d+")
    val cid = Regex("\\bcid[=:]\\s*\\d+")
    val jsonCid = Regex("\"cid\":\\s*\\d+")
    val roomId = Regex("room_id[=:]\\s*\\d+")
    val camelRoomId = Regex("roomId[=:]\\s*\\d+")
    val seasonId = Regex("season_id[=:]\\s*\\d+")
    val episodeId = Regex("ep_id[=:]\\s*\\d+")
    val keyword = Regex("keyword=[^&\\s]+")
    val jsonKeyword = Regex("\"keyword\":\"[^\"]+\"")
    val searchText = Regex("Search:\\s*[^\\n]+")
    val inputText = Regex("(?i)\\b(content|message_text|query)[=:]\\s*[^&\\r\\n]+")
    val jsonInputText = Regex("(?i)\"(content|message_text|query)\"\\s*:\\s*\"[^\"]*\"")
    val videoTitle = Regex("video_title=[^&\\s]{3,}")
    val jsonTitle = Regex("\"title\":\"[^\"]{3,}\"")
}

/**
 *  日志收集器
 * 
 * 使用环形缓冲区保留最近 1000 条日志，支持导出分享
 */
object LogCollector {
    
    private const val MAX_ENTRIES = 1000
    private const val DUPLICATE_SUPPRESS_WINDOW_MS = 250L
    private const val MAX_PERSISTED_LOG_BYTES = 256 * 1024
    private const val MAX_BASIC_LOG_BYTES = 64 * 1024
    private const val MAX_LOG_MESSAGE_CHARS = 16 * 1024
    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val diskWriter = Executors.newSingleThreadExecutor()
    private var lastEntryFingerprint: String? = null
    private var lastEntryTimestamp: Long = 0L
    @Volatile
    private var appContext: Context? = null
    
    /**
     * 日志条目
     */
    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun format(): String {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
            return "[$time] $level/$tag: $message"
        }
    }
    
    /**
     * 添加日志条目。敏感信息在进入内存和磁盘前即被移除，导出时再做一次纵深防御。
     */
    fun add(
        level: String,
        tag: String,
        message: String,
        persistToDisk: Boolean = true,
        basicDiagnostic: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val sanitizedTag = sanitizeMessage(tag).take(80)
        val sanitizedMessage = sanitizeMessage(message).let { value ->
            if (value.length <= MAX_LOG_MESSAGE_CHARS) value
            else value.take(MAX_LOG_MESSAGE_CHARS) + "…[truncated]"
        }
        val fingerprint = "$level|$sanitizedTag|$sanitizedMessage"
        var entryToPersist: LogEntry? = null
        synchronized(lock) {
            // 高频重复日志直接抑制，避免日志风暴拖垮主线程
            if (fingerprint == lastEntryFingerprint &&
                now - lastEntryTimestamp <= DUPLICATE_SUPPRESS_WINDOW_MS) {
                lastEntryTimestamp = now
                return
            }

            lastEntryFingerprint = fingerprint
            lastEntryTimestamp = now

            entryToPersist = LogEntry(
                timestamp = now,
                level = level,
                tag = sanitizedTag,
                message = sanitizedMessage
            )
            buffer.addLast(entryToPersist)

            while (buffer.size > MAX_ENTRIES) {
                if (buffer.isNotEmpty()) {
                    buffer.pollFirst()
                } else {
                    break
                }
            }
        }

        if (persistToDisk) {
            entryToPersist?.let { appendEntryToRuntimeFile(it, basicDiagnostic) }
        }
    }

    fun init(context: Context) {
        if (appContext === context.applicationContext) return
        appContext = context.applicationContext
        runCatching {
            resolveLogPersistenceDir(context.filesDir).mkdirs()
        }.onFailure {
            Log.e("LogCollector", "初始化持久化日志目录失败", it)
        }
    }
    
    /**
     *  隐私脱敏：移除敏感信息
     * 
     * 覆盖范围：
     * - Cookie 值 (SESSDATA, bili_jct, DedeUserID 等)
     * - Token / Key (access_token, refresh_token 等)
     * - 用户标识 (mid, uid, 手机号, 邮箱)
     * - 网络信息 (IP 地址, MAC 地址)
     * - 文件路径 (可能包含用户名)
     * - 其他敏感参数
     */
    internal fun sanitizeMessage(message: String): String {
        var sanitized = message
        
        // ========== Cookie 脱敏 ==========
        sanitized = sanitized.replace(LogRedactionPatterns.sessionCookie, "SESSDATA=***")
        sanitized = sanitized.replace(LogRedactionPatterns.csrfCookie, "bili_jct=***")
        sanitized = sanitized.replace(LogRedactionPatterns.userCookie, "DedeUserID=***")
        sanitized = sanitized.replace(LogRedactionPatterns.userCookieChecksum, "DedeUserID__ckMd5=***")
        sanitized = sanitized.replace(LogRedactionPatterns.sidCookie, "sid=***")
        sanitized = sanitized.replace(LogRedactionPatterns.buvid3Cookie, "buvid3=***")
        sanitized = sanitized.replace(LogRedactionPatterns.buvid4Cookie, "buvid4=***")
        sanitized = sanitized.replace(LogRedactionPatterns.nutCookie, "b_nut=***")
        sanitized = sanitized.replace(LogRedactionPatterns.uuidCookie, "_uuid=***")
        
        // ========== Token / Key 脱敏 ==========
        sanitized = sanitized.replace(LogRedactionPatterns.accessToken, "access_token=***")
        sanitized = sanitized.replace(LogRedactionPatterns.refreshToken, "refresh_token=***")
        sanitized = sanitized.replace(LogRedactionPatterns.accessKey, "access_key=***")
        sanitized = sanitized.replace(LogRedactionPatterns.appKey, "appkey=***")
        sanitized = sanitized.replace(LogRedactionPatterns.signature, "sign=***")
        sanitized = sanitized.replace(LogRedactionPatterns.csrf, "csrf=***")
        sanitized = sanitized.replace(LogRedactionPatterns.jsonToken, "\"token\":\"***\"")
        sanitized = sanitized.replace(LogRedactionPatterns.jsonCsrf, "\"csrf\":\"***\"")
        sanitized = sanitized.replace(
            LogRedactionPatterns.authorization,
            "Authorization: ***"
        )
        sanitized = sanitized.replace(LogRedactionPatterns.bearer, "Bearer ***")
        sanitized = sanitized.replace(
            LogRedactionPatterns.cookieHeader,
            "$1: ***"
        )
        sanitized = sanitized.replace(
            LogRedactionPatterns.password,
            "$1=***"
        )
        
        // ========== 用户 ID 脱敏 ==========
        // Bilibili mid/uid (通常为 6-11 位数字，在特定上下文中)
        sanitized = sanitized.replace(LogRedactionPatterns.userMid, "mid=***")
        sanitized = sanitized.replace(LogRedactionPatterns.jsonMid, "\"mid\":***")
        sanitized = sanitized.replace(LogRedactionPatterns.userUid, "uid=***")
        sanitized = sanitized.replace(LogRedactionPatterns.jsonUid, "\"uid\":***")
        sanitized = sanitized.replace(LogRedactionPatterns.visitorMid, "vmid=***")
        
        // ========== 手机号脱敏 (11位中国手机号) ==========
        sanitized = sanitized.replace(LogRedactionPatterns.phone, "1**********")
        
        // ========== 邮箱脱敏 ==========
        sanitized = sanitized.replace(LogRedactionPatterns.email) {
            val email = it.value
            val atIndex = email.indexOf('@')
            if (atIndex > 2) {
                email.substring(0, 2) + "***" + email.substring(atIndex)
            } else {
                "***" + email.substring(atIndex)
            }
        }
        
        // ========== IP 地址脱敏 ==========
        // IPv4
        sanitized = sanitized.replace(LogRedactionPatterns.ipv4) {
            val parts = it.value.split(".")
            if (parts.size == 4 && parts.all { p -> p.toIntOrNull() in 0..255 }) {
                "${parts[0]}.***.***.*"
            } else {
                it.value
            }
        }
        // IPv6 (简化处理)
        sanitized = sanitized.replace(LogRedactionPatterns.ipv6, "***:***:***")
        
        // ========== MAC 地址脱敏 ==========
        sanitized = sanitized.replace(LogRedactionPatterns.mac, "**:**:**:**:**:**")
        
        // ========== 文件路径脱敏 (隐藏用户名) ==========
        // Android 路径
        sanitized = sanitized.replace(LogRedactionPatterns.androidDataPath, "/data/user/0/***/")
        sanitized = sanitized.replace(LogRedactionPatterns.androidStoragePath, "/storage/emulated/0/")
        // 通用 home 目录
        sanitized = sanitized.replace(LogRedactionPatterns.linuxHomePath, "/home/***/")
        sanitized = sanitized.replace(LogRedactionPatterns.macHomePath, "/Users/***/")
        
        // ========== 设备标识脱敏 ==========
        sanitized = sanitized.replace(LogRedactionPatterns.deviceId, "device_id=***")
        sanitized = sanitized.replace(LogRedactionPatterns.jsonDeviceId, "\"device_id\":\"***\"")
        sanitized = sanitized.replace(LogRedactionPatterns.androidId, "android_id=***")
        sanitized = sanitized.replace(LogRedactionPatterns.imei, "imei=***")
        
        // ========== 敏感 JSON 字段脱敏 ==========
        sanitized = sanitized.replace(LogRedactionPatterns.jsonFace, "\"face\":\"***\"")
        sanitized = sanitized.replace(LogRedactionPatterns.jsonPhone, "\"tel\":\"***\"")
        sanitized = sanitized.replace(LogRedactionPatterns.jsonName) {
            // 保留名字首字符
            val name = it.value
            val start = name.indexOf(":\"") + 2
            val end = name.lastIndexOf("\"")
            if (end > start + 1) {
                "\"name\":\"${name[start]}***\""
            } else {
                it.value
            }
        }
        
        // ========== 🎬 视频内容脱敏（保护用户观看记录隐私） ==========
        // 视频 BVID
        sanitized = sanitized.replace(LogRedactionPatterns.bvid, "BV***")
        // 视频 AID/AV 号
        sanitized = sanitized.replace(LogRedactionPatterns.avid, "av***")
        sanitized = sanitized.replace(LogRedactionPatterns.jsonAid, "\"aid\":***")
        // CID
        sanitized = sanitized.replace(LogRedactionPatterns.cid, "cid=***")
        sanitized = sanitized.replace(LogRedactionPatterns.jsonCid, "\"cid\":***")
        // 直播房间号
        sanitized = sanitized.replace(LogRedactionPatterns.roomId, "room_id=***")
        sanitized = sanitized.replace(LogRedactionPatterns.camelRoomId, "roomId=***")
        // Season ID (番剧)
        sanitized = sanitized.replace(LogRedactionPatterns.seasonId, "season_id=***")
        sanitized = sanitized.replace(LogRedactionPatterns.episodeId, "ep_id=***")
        
        // ========== 🔍 搜索关键词脱敏 ==========
        sanitized = sanitized.replace(LogRedactionPatterns.keyword, "keyword=***")
        sanitized = sanitized.replace(LogRedactionPatterns.jsonKeyword, "\"keyword\":\"***\"")
        sanitized = sanitized.replace(LogRedactionPatterns.searchText, "Search: ***")

        // 私信、评论草稿等用户输入内容不进入诊断日志。
        sanitized = sanitized.replace(
            LogRedactionPatterns.inputText,
            "$1=***"
        )
        sanitized = sanitized.replace(
            LogRedactionPatterns.jsonInputText,
            "\"$1\":\"***\""
        )
        
        // ========== 📝 视频标题脱敏（仅保留前两个字符） ==========
        sanitized = sanitized.replace(LogRedactionPatterns.videoTitle) {
            val title = it.value.substringAfter("=")
            "video_title=${title.take(2)}***"
        }
        sanitized = sanitized.replace(LogRedactionPatterns.jsonTitle) {
            val content = it.value
            val titleStart = content.indexOf(":\"") + 2
            val title = content.substring(titleStart, content.length - 1)
            "\"title\":\"${title.take(2)}***\""
        }
        
        return sanitized
    }
    
    /**
     * 获取所有日志条目
     */
    fun getEntries(): List<LogEntry> = synchronized(lock) { buffer.toList() }
    
    /**
     * 获取日志条目数量
     */
    fun getCount(): Int = synchronized(lock) { buffer.size }
    
    /**
     * 清空日志
     */
    fun clear() {
        synchronized(lock) {
            buffer.clear()
            lastEntryFingerprint = null
            lastEntryTimestamp = 0L
        }
    }

    fun clearRuntimeDiagnostics() {
        synchronized(lock) {
            buffer.removeAll { it.level != "W" && it.level != "E" && it.tag != "StartupDiagnostics" }
            lastEntryFingerprint = null
        }
        val context = appContext ?: return
        diskWriter.execute {
            runCatching {
                resolveRuntimeLogFile(context.filesDir).delete()
            }.onFailure {
                Log.e("LogCollector", "清理增强诊断日志失败", it)
            }
        }
    }

    fun persistCrashSnapshot(throwable: Throwable) {
        val context = appContext ?: return
        val rawTrace = (throwable as? AbnormalProcessExitException)
            ?.nativeTrace?.let(::decodeNativeExitTrace)
        val sanitizedEntries = getEntries().map { entry ->
            entry.copy(message = sanitizeMessage(entry.message))
        }
        runCatching {
            val exitTimestamp = (throwable as? AbnormalProcessExitException)?.exitTimestampMillis
            val previousLogs = if (exitTimestamp != null) {
                selectLogsBeforeProcessExit(
                    listOf(resolveBasicLogFile(context.filesDir), resolveRuntimeLogFile(context.filesDir))
                        .flatMap { file ->
                            runCatching { if (file.isFile) file.readLines() else emptyList() }
                                .getOrDefault(emptyList()).let(::groupDiagnosticLogLines)
                        },
                    exitTimestamp,
                )
            } else emptyList()
            val content = buildCrashSnapshotContent(
                throwable = throwable,
                entries = sanitizedEntries,
                exportedAtMillis = System.currentTimeMillis(),
                appVersionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                manufacturer = android.os.Build.MANUFACTURER,
                model = android.os.Build.MODEL,
                androidRelease = android.os.Build.VERSION.RELEASE,
                apiLevel = android.os.Build.VERSION.SDK_INT,
                buildType = BuildConfig.BUILD_TYPE,
                buildCommit = BuildConfig.BUILD_COMMIT_SHA,
                previousProcessLogs = previousLogs,
            )
            val snapshotFile = resolveCrashSnapshotFile(context.filesDir)
            val rawTraceFile = resolveRawCrashTraceFile(context.filesDir)
            val markerFile = resolveCrashSnapshotMarkerFile(context.filesDir)
            snapshotFile.parentFile?.mkdirs()
            // A later Java crash must not leave an unrelated earlier native trace attached.
            rawTraceFile.delete()
            rawTrace?.let { bytes ->
                runCatching { rawTraceFile.writeBytes(bytes) }
                    .onFailure { Log.e("LogCollector", "保存原始系统回溯失败", it) }
            }
            snapshotFile.writeText(content)
            markerFile.writeText(System.currentTimeMillis().toString())
        }.onFailure {
            Log.e("LogCollector", "写入崩溃快照失败", it)
        }
    }

    fun getPendingCrashSnapshotFile(): File? {
        val context = appContext ?: return null
        val snapshotFile = resolveCrashSnapshotFile(context.filesDir)
        val markerFile = resolveCrashSnapshotMarkerFile(context.filesDir)
        return snapshotFile.takeIf {
            hasPendingCrashSnapshot(
                markerExists = markerFile.exists(),
                snapshotExists = snapshotFile.exists()
            )
        }
    }

    fun hasRawCrashTrace(context: Context): Boolean =
        resolveCrashSnapshotFile(context.filesDir).isFile &&
            resolveRawCrashTraceFile(context.filesDir).let { it.isFile && it.length() > 0L }

    fun clearPendingCrashSnapshot() {
        val context = appContext ?: return
        runCatching {
            resolveCrashSnapshotMarkerFile(context.filesDir).delete()
        }.onFailure {
            Log.e("LogCollector", "清理崩溃快照标记失败", it)
        }
    }

    fun sharePendingCrashSnapshot(context: Context): Boolean {
        val snapshotFile = getPendingCrashSnapshotFile() ?: return false
        return runCatching {
            val cacheDir = File(context.cacheDir, LOG_DIRECTORY_NAME)
            cacheDir.mkdirs()
            val shareFile = File(cacheDir, CRASH_SNAPSHOT_FILE_NAME)
            shareFile.writeText(sanitizeMessage(removeEmbeddedNativeTombstone(snapshotFile.readText())))
            shareLogFileFromCache(context, shareFile)
            true
        }.getOrElse {
            Log.e("LogCollector", "分享崩溃快照失败", it)
            false
        }
    }
    
    /**
     * 导出日志到文件并通过系统分享
     * 
     * 日志会保存到 Download/BiliPai/logs/ 目录，方便 MT 管理器等工具直接访问
     */
    fun exportAndShare(
        context: Context,
        includeSystemDiagnostics: Boolean = true,
        includeRawCrashTrace: Boolean = false,
    ) {
        init(context)
        // Queue behind pending writes: exporting immediately after an error must include it.
        // File reads, MediaStore writes and trace copies must not block the recovery UI.
        diskWriter.execute {
            try {
                val persistedEntries = listOf(
                    resolveBasicLogFile(context.filesDir),
                    resolveRuntimeLogFile(context.filesDir),
                ).flatMap { file ->
                    runCatching { if (file.isFile) file.readLines() else emptyList() }
                        .getOrDefault(emptyList())
                        .let(::groupDiagnosticLogLines)
                }
                val logEntries = mergeDiagnosticLogEntries(
                    persistedEntries,
                    getEntries().map { it.format() },
                )
                // Dismissing the prompt only clears its marker, not the retained evidence.
                val crashContent = resolveCrashSnapshotFile(context.filesDir)
                    .takeIf(File::isFile)?.readText()
                    ?.let(::removeEmbeddedNativeTombstone)
                    ?.let(::sanitizeMessage)
                val rawTraceFile = resolveRawCrashTraceFile(context.filesDir)
                    .takeIf { includeRawCrashTrace && it.isFile && it.length() > 0L &&
                        !crashContent.isNullOrBlank() }
                val exportedAt = Date()
                val exportStamp = fileDateFormat.format(exportedAt)
                val shareCacheDir = File(context.cacheDir, LOG_DIRECTORY_NAME).apply { mkdirs() }
                val rawShareFile = rawTraceFile?.let { source ->
                    val target = File(shareCacheDir, "bilipai_native_trace_${exportStamp}.pb")
                    runCatching { source.copyTo(target, overwrite = true) }
                        .onFailure {
                            target.delete()
                            Log.e("LogCollector", "复制原始系统回溯失败", it)
                        }
                        .getOrNull()
                }
                val recentProcessExits = if (includeSystemDiagnostics) {
                    Android17Diagnostics.recentProcessExitSummaries(context)
                } else emptyList()
                val retainedProfiles = if (includeSystemDiagnostics) {
                    Android17Diagnostics.retainedArtifacts(context)
                } else emptyList()
                if (!hasExportableDiagnostics(
                        logCount = logEntries.size,
                        hasCrashSnapshot = !crashContent.isNullOrBlank(),
                        processExitCount = recentProcessExits.size,
                        profilingArtifactCount = retainedProfiles.size,
                    )) {
                    showExportToast(context, "暂无日志记录")
                    return@execute
                }
                val content = buildString {
                    appendLine("BiliPai 诊断日志")
                    appendLine("导出时间: ${dateFormat.format(exportedAt)}")
                    appendLine("应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("构建: ${BuildConfig.BUILD_TYPE} / ${BuildConfig.BUILD_COMMIT_SHA}")
                    appendLine("设备信息: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Android版本: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("系统构建指纹: ${android.os.Build.FINGERPRINT}")
                    appendLine("应用目标 API: ${context.applicationInfo.targetSdkVersion}")
                    appendLine("基础诊断: 默认保留，滚动上限 64KB")
                    appendLine("增强诊断: ${if (Logger.areVerboseRuntimeLogsEnabled()) "已开启" else "未开启"}")
                    appendLine("内容: ${if (crashContent.isNullOrBlank()) "无崩溃快照" else "最近崩溃快照"}；" +
                        "系统退出 ${recentProcessExits.size} 条；运行日志 ${logEntries.size} 条")
                    appendLine("原始系统回溯: ${if (rawShareFile != null) "另附 .pb 文件" else "未包含"}")
                    appendLine("性能诊断附件: ${retainedProfiles.size} 个")
                    appendLine("隐私说明: 文本日志已脱敏；原始回溯仅在明确选择后分享")
                    appendLine()
                    appendLine("========== 1. 最近一次崩溃 ==========")
                    if (!crashContent.isNullOrBlank()) {
                        appendLine(crashContent)
                    } else {
                        appendLine("无已保存的崩溃快照")
                    }
                    appendLine("========== 2. 系统退出记录 ==========")
                    if (recentProcessExits.isEmpty()) appendLine("无记录或本次未采集")
                    recentProcessExits.forEach { appendLine(sanitizeMessage(it)) }
                    appendLine("========== 3. 运行日志 ==========")
                    if (logEntries.isEmpty()) appendLine("无记录")
                    logEntries.forEach { appendLine(sanitizeMessage(it)) }
                }
                val fileName = "bilipai_log_${exportStamp}.txt"
                val savedPath = saveToExternalDownload(context, fileName, content)
                val shareFile = File(shareCacheDir, fileName).apply { writeText(content) }
                val profilingFiles = if (retainedProfiles.isNotEmpty()) {
                    Android17Diagnostics.copyRetainedArtifactsTo(context, shareCacheDir)
                } else emptyList()
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (context is android.app.Activity && (context.isFinishing || context.isDestroyed)) {
                        return@post
                    }
                    val message = if (savedPath != null) {
                        "文本日志已保存到 $savedPath"
                    } else "日志已准备好，请选择分享方式"
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    shareLogFilesFromCache(
                        context,
                        listOfNotNull(shareFile, rawShareFile) + profilingFiles
                    )
                }
            } catch (error: Exception) {
                Log.e("LogCollector", "导出日志失败", error)
                showExportToast(context, "日志导出失败，请重试")
            }
        }
    }

    private fun showExportToast(context: Context, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun saveTextArtifact(
        context: Context,
        fileName: String,
        content: String,
        replaceExisting: Boolean = false
    ): String? {
        return saveToExternalDownload(
            context = context,
            fileName = fileName,
            content = content,
            replaceExisting = replaceExisting
        )
    }
    
    /**
     *  保存日志到外部 Download 目录
     * 
     * 路径: /storage/emulated/0/Download/BiliPai/logs/xxx.txt
     * MT管理器路径: Download/BiliPai/logs/
     */
    private fun saveToExternalDownload(
        context: Context,
        fileName: String,
        content: String,
        replaceExisting: Boolean = false
    ): String? {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                if (replaceExisting) {
                    val existingUri = findExistingDownloadUri(context, fileName)
                    if (existingUri != null) {
                        context.contentResolver.delete(existingUri, null, null)
                    }
                }
                // Android 10+ 使用 MediaStore API
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(android.provider.MediaStore.Downloads.RELATIVE_PATH, DOWNLOAD_LOG_RELATIVE_PATH)
                }
                
                val uri = context.contentResolver.insert(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                    }
                    "$DOWNLOAD_LOG_RELATIVE_PATH/$fileName"
                }
            } else {
                // Android 9 及以下直接写入
                @Suppress("DEPRECATION")
                val downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                )
                val logDir = File(downloadDir, "BiliPai/logs")
                logDir.mkdirs()
                val logFile = File(logDir, fileName)
                logFile.writeText(content)
                logFile.absolutePath
            }
        } catch (e: Exception) {
            Log.w("LogCollector", "无法保存到外部存储", e)
            null
        }
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun findExistingDownloadUri(context: Context, fileName: String): android.net.Uri? {
        val projection = arrayOf(
            android.provider.MediaStore.Downloads._ID
        )
        val selection =
            "${android.provider.MediaStore.Downloads.DISPLAY_NAME}=? AND " +
                "${android.provider.MediaStore.Downloads.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(fileName, DOWNLOAD_LOG_RELATIVE_PATH)
        return context.contentResolver.query(
            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val idIndex = cursor.getColumnIndex(android.provider.MediaStore.Downloads._ID)
            if (idIndex < 0) return@use null
            val id = cursor.getLong(idIndex)
            android.content.ContentUris.withAppendedId(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                id
            )
        }
    }
    
    /**
     * 分享日志文件（从缓存目录）
     */
    private fun shareLogFileFromCache(context: Context, logFile: File) {
        shareLogFilesFromCache(context, listOf(logFile))
    }

    private fun shareLogFilesFromCache(context: Context, files: List<File>) {
        try {
            val validFiles = files.filter(File::isFile)
            val uris = ArrayList(validFiles.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            })
            if (uris.isEmpty()) return
            val shareIntent = Intent(
                if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            ).apply {
                type = if (uris.size == 1 && validFiles.single().extension == "txt") {
                    "text/plain"
                } else {
                    "application/octet-stream"
                }
                if (uris.size == 1) {
                    putExtra(Intent.EXTRA_STREAM, uris.single())
                } else {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
                putExtra(Intent.EXTRA_SUBJECT, "BiliPai 日志反馈")
                putExtra(
                    Intent.EXTRA_TEXT,
                    if (uris.size == 1) "请查看附件中的日志文件"
                    else "请查看附件中的日志与用户主动导出的性能诊断文件"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "分享日志"))
        } catch (e: Exception) {
            Log.e("LogCollector", "分享失败", e)
        }
    }

    private fun appendEntryToRuntimeFile(entry: LogEntry, basicDiagnostic: Boolean = false) {
        val context = appContext ?: return
        diskWriter.execute {
            runCatching {
                val sanitizedEntry = entry.format() + "\n"
                val isBasic = basicDiagnostic || entry.level == "W" || entry.level == "E"
                appendRollingDiagnosticLog(
                    file = if (isBasic) resolveBasicLogFile(context.filesDir)
                        else resolveRuntimeLogFile(context.filesDir),
                    text = sanitizedEntry,
                    maxBytes = if (isBasic) MAX_BASIC_LOG_BYTES else MAX_PERSISTED_LOG_BYTES,
                )
            }.onFailure {
                Log.e("LogCollector", "持久化运行日志失败", it)
            }
        }
    }

}
