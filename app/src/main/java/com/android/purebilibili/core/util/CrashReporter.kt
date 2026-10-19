package com.android.purebilibili.core.util

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.android.purebilibili.BuildConfig
import com.android.purebilibili.core.lifecycle.BackgroundManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.CancellationException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

private val LIVE_STAGE_WHITESPACE_REGEX = Regex("\\s+")
private val SENSITIVE_CRASH_CUSTOM_KEYS = setOf(
    "video_bvid",
    "danmaku_cid",
    "live_room_id",
    "live_room_title",
    "live_anchor_name",
    "fatal_live_room_id"
)

internal fun sanitizeLiveTraceStage(stage: String): String {
    return stage
        .trim()
        .replace(LIVE_STAGE_WHITESPACE_REGEX, "_")
        .take(80)
}

internal fun shouldUpdateLiveTraceStage(lastStage: String, nextStage: String): Boolean {
    val normalizedNext = sanitizeLiveTraceStage(nextStage)
    if (normalizedNext.isBlank()) return false
    return normalizedNext != lastStage
}

internal fun liveSessionDurationMs(nowElapsedMs: Long, sessionStartElapsedMs: Long): Long {
    return (nowElapsedMs - sessionStartElapsedMs).coerceAtLeast(0L)
}

internal fun shouldWriteCrashCustomKey(previousValue: Any?, nextValue: Any): Boolean {
    return previousValue != nextValue
}

internal fun shouldPersistLocalCrashSnapshot(crashTrackingEnabled: Boolean): Boolean {
    return true
}

internal fun isSensitiveCrashCustomKey(key: String): Boolean {
    return key in SENSITIVE_CRASH_CUSTOM_KEYS
}

internal fun resolveCrashlyticsUserId(mid: Long?): String = ""

private const val MIN_NON_FATAL_HEADROOM_BYTES = 8L * 1024 * 1024
private const val MIN_NON_FATAL_HEADROOM_RATIO = 0.03

internal fun shouldRecordNonFatalEvent(
    maxMemoryBytes: Long,
    totalMemoryBytes: Long,
    freeMemoryBytes: Long
): Boolean {
    val availableBytes = (maxMemoryBytes - totalMemoryBytes + freeMemoryBytes).coerceAtLeast(0L)
    val requiredBytes = max(
        MIN_NON_FATAL_HEADROOM_BYTES,
        (maxMemoryBytes * MIN_NON_FATAL_HEADROOM_RATIO).toLong()
    )
    return availableBytes >= requiredBytes
}

internal fun shouldReportApiFailure(
    callCanceled: Boolean,
    throwable: Throwable
): Boolean {
    if (callCanceled) return false

    var cause: Throwable? = throwable
    while (cause != null) {
        if (cause is CancellationException) return false
        if (cause.message?.trim()?.equals("Canceled", ignoreCase = true) == true ||
            cause.message?.trim()?.equals("Cancelled", ignoreCase = true) == true
        ) {
            return false
        }
        cause = cause.cause
    }
    return true
}

internal fun normalizeApiErrorEndpoint(endpoint: String): String {
    val normalized = endpoint.substringBefore("?").trim().take(160)
    val pathStart = normalized.indexOf('/')
    val method = if (pathStart > 0) normalized.substring(0, pathStart).trim() else ""
    val path = if (pathStart >= 0) normalized.substring(pathStart) else normalized
    val groupedPath = when {
        path.startsWith("/bfs/") -> path.split('/').take(3).joinToString("/")
        path.startsWith("/videoshotpvhdboss/") -> "/videoshotpvhdboss"
        path.startsWith("/v1/resource/upgcxcode/") -> "/v1/resource/upgcxcode"
        else -> path
    }
    return if (method.isBlank()) groupedPath else "$method $groupedPath"
}

// Routine connectivity failures and server throttling need a lower reporting frequency.
internal fun apiFailureReportIntervalMs(httpCode: Int): Long = when (httpCode) {
    -1, 412, 429 -> 300_000L
    else -> 60_000L
}

/**
 * 崩溃报告工具类
 *
 * 封装 Firebase Crashlytics，提供统一的错误上报接口：
 * - 全局崩溃前上下文补充
 * - 非致命错误上报与限流
 * - 用户/会话上下文 key 维护
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val RATE_LIMIT_WINDOW_MS = 60_000L
    private const val RATE_LIMIT_MAX_KEYS = 300
    private const val CUSTOM_KEY_CACHE_MAX_KEYS = 256

    @Volatile
    private var isEnabled: Boolean = false

    @Volatile
    private var globalHandlerInstalled = false

    private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null
    // A rolling budget prevents distinct failures from flooding the SDK during an outage.
    private val nonFatalRateLimiter = TelemetryRateLimiter(
        maxKeys = RATE_LIMIT_MAX_KEYS,
        maxEventsPerWindow = 8,
        windowMs = RATE_LIMIT_WINDOW_MS
    )
    private val lastCustomKeyValues = ConcurrentHashMap<String, Any>()
    private val crashlytics: FirebaseCrashlytics
        get() = FirebaseCrashlytics.getInstance()

    @Volatile
    private var lastAppForegroundState: Boolean? = null

    @Volatile
    private var liveSessionActive: Boolean = false

    @Volatile
    private var liveSessionRoomId: Long = 0L

    @Volatile
    private var liveSessionStartElapsedMs: Long = 0L

    @Volatile
    private var liveLastStage: String = ""

    /** Load consent before early business calls, leaving SDK setup in the deferred task. */
    fun prepare(enabled: Boolean) {
        isEnabled = enabled
    }

    /**
     * 基础初始化：写入稳定环境信息
     */
    fun init(context: Context) {
        try {
            setCustomKey("app_version", BuildConfig.VERSION_NAME)
            setCustomKey("version_code", BuildConfig.VERSION_CODE)
            setCustomKey("build_type", BuildConfig.BUILD_TYPE)
            setCustomKey("device_model", Build.MODEL ?: "unknown")
            setCustomKey("device_brand", Build.BRAND ?: "unknown")
            setCustomKey("android_version", Build.VERSION.SDK_INT)
            setCustomKey("locale", Locale.getDefault().toLanguageTag())
            setCustomKey(
                "process_name",
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    Application.getProcessName()
                } else {
                    context.packageName
                }
            )
            setCustomKey("app_in_foreground", !BackgroundManager.isInBackground)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init crash context", e)
        }
    }

    /**
     * 启用/禁用 Crashlytics 收集
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        if (!enabled) nonFatalRateLimiter.clear()
        try {
            crashlytics.setCrashlyticsCollectionEnabled(enabled)
            Logger.d(TAG, " Crashlytics collection ${if (enabled) "enabled" else "disabled"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Crashlytics enabled state", e)
        }
    }

    /**
     * 安装全局未捕获异常处理器：
     * 在崩溃真正上报前，补充最后上下文信息。
     */
    fun installGlobalExceptionHandler() {
        if (globalHandlerInstalled) return
        globalHandlerInstalled = true

        previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (shouldPersistLocalCrashSnapshot(crashTrackingEnabled = isEnabled)) {
                    // 本地崩溃快照只写入设备，不依赖 Crashlytics 同意状态。
                    Logger.persistCrashSnapshot(throwable)
                }
                if (isEnabled) {
                    @Suppress("DEPRECATION")
                    val fatalThreadId = thread.id
                    setCustomKey("fatal_thread_name", thread.name)
                    setCustomKey("fatal_thread_id", fatalThreadId)
                    setCustomKey("app_in_foreground", !BackgroundManager.isInBackground)
                    setCustomKey("fatal_in_live_session", liveSessionActive)
                    if (liveSessionActive) {
                        setCustomKey("fatal_live_room_id", liveSessionRoomId)
                        setCustomKey("fatal_live_stage", liveLastStage.ifBlank { "unknown" })
                        setCustomKey(
                            "fatal_live_session_uptime_ms",
                            liveSessionDurationMs(
                                nowElapsedMs = SystemClock.elapsedRealtime(),
                                sessionStartElapsedMs = liveSessionStartElapsedMs
                            )
                        )
                    }
                    log("FATAL: ${throwable.javaClass.simpleName}: ${throwable.message.orEmpty().take(200)}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to enrich fatal crash context", e)
            } finally {
                previousUncaughtHandler?.uncaughtException(thread, throwable)
                    ?: run {
                        android.os.Process.killProcess(android.os.Process.myPid())
                        kotlin.system.exitProcess(10)
                    }
            }
        }
    }

    /**
     * 记录前后台状态，便于排查崩溃发生场景
     */
    fun setAppForegroundState(inForeground: Boolean) {
        if (!isEnabled) return
        if (lastAppForegroundState == inForeground) return
        lastAppForegroundState = inForeground
        try {
            setCustomKey("app_in_foreground", inForeground)
            crashlytics.log("App ${if (inForeground) "foreground" else "background"}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set app foreground state", e)
        }
    }

    /**
     * 标记当前页面
     */
    fun setLastScreen(screenName: String) {
        if (!isEnabled) return
        try {
            setCustomKey("last_screen", screenName.take(100))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set last screen", e)
        }
    }

    /**
     * 标记最近一次业务事件
     */
    fun setLastEvent(eventName: String) {
        if (!isEnabled) return
        try {
            setCustomKey("last_event", eventName.take(100))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set last event", e)
        }
    }

    /**
     * 同步用户会话信息（匿名/已登录、VIP、无痕模式）
     */
    fun syncUserContext(mid: Long?, isVip: Boolean?, privacyModeEnabled: Boolean?) {
        if (!isEnabled) return
        try {
            crashlytics.setUserId(resolveCrashlyticsUserId(mid))
            setCustomKey("is_logged_in", mid != null && mid > 0)
            isVip?.let { setCustomKey("is_vip", it) }
            privacyModeEnabled?.let { setCustomKey("privacy_mode", it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync user context", e)
        }
    }

    /**
     * 记录非致命异常
     * 用于捕获的异常，不会导致崩溃但需要追踪
     */
    fun logException(e: Throwable, message: String? = null) {
        if (!isEnabled) return
        if (!hasNonFatalReportingHeadroom()) return
        if (!shouldReportApiFailure(callCanceled = false, throwable = e)) return
        val origin = e.stackTrace.firstOrNull()?.let { "${it.className}.${it.methodName}" }.orEmpty()
        val key = "exception:${e.javaClass.name}:$origin"
        if (shouldDropByRateLimit(key)) return

        try {
            message?.let { crashlytics.log(it) }
            crashlytics.recordException(e)
            Logger.e(TAG, " Exception logged: ${e.message}", e)
        } catch (ex: Exception) {
            Log.e(TAG, "Failed to log exception", ex)
        }
    }

    /**
     * 记录自定义日志
     * 这些日志会在崩溃报告中显示，帮助定位问题
     */
    fun log(message: String) {
        if (!isEnabled) return
        try {
            crashlytics.log(message)
            Logger.d(TAG, " Log: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log message", e)
        }
    }

    /**
     * 设置用户标识符（用于追踪特定用户的问题）
     * 注意：请勿设置可识别个人身份的信息
     */
    fun setUserId(userId: String) {
        if (!isEnabled) return
        try {
            crashlytics.setUserId("")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set user ID", e)
        }
    }

    /**
     * 设置自定义键值对（崩溃时会附带这些信息）
     */
    fun setCustomKey(key: String, value: String) {
        if (!isEnabled) return
        if (isSensitiveCrashCustomKey(key)) return
        if (!shouldCacheAndWriteCustomKey(key, value)) return
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set custom key", e)
        }
    }

    /**
     * 设置 Boolean 类型的自定义键
     */
    fun setCustomKey(key: String, value: Boolean) {
        if (!isEnabled) return
        if (isSensitiveCrashCustomKey(key)) return
        if (!shouldCacheAndWriteCustomKey(key, value)) return
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set custom key", e)
        }
    }

    /**
     * 设置 Int 类型的自定义键
     */
    fun setCustomKey(key: String, value: Int) {
        if (!isEnabled) return
        if (isSensitiveCrashCustomKey(key)) return
        if (!shouldCacheAndWriteCustomKey(key, value)) return
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set custom key", e)
        }
    }

    /**
     * 设置 Long 类型的自定义键
     */
    fun setCustomKey(key: String, value: Long) {
        if (!isEnabled) return
        if (isSensitiveCrashCustomKey(key)) return
        if (!shouldCacheAndWriteCustomKey(key, value)) return
        try {
            crashlytics.setCustomKey(key, value)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set custom key", e)
        }
    }

    // ========== 视频播放错误上报 ==========

    /**
     * 上报视频播放错误
     * @param bvid 视频 BV 号
     * @param errorType 错误类型 (如 "no_play_url", "network_error", "decode_error")
     * @param errorMessage 错误详情
     * @param exception 可选的异常对象
     */
    fun reportVideoError(
        bvid: String,
        errorType: String,
        errorMessage: String,
        exception: Throwable? = null
    ) {
        if (!isEnabled) return
        if (!hasNonFatalReportingHeadroom()) return
        val key = "video:${errorType.take(80)}"
        if (shouldDropByRateLimit(key)) return

        try {
            setCustomKey("video_bvid", bvid.take(120))
            setCustomKey("video_error_type", errorType)
            exception?.let { setCustomKey("video_exception_type", it.javaClass.simpleName.take(80)) }
            crashlytics.log("Video Error: [$errorType] ${errorMessage.take(300)}")
            crashlytics.recordException(
                buildSanitizedThrowable(
                    VideoPlaybackException(errorType, errorMessage),
                    exception
                )
            )
            Logger.e(TAG, " Video error reported: [$errorType] $errorMessage", exception)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report video error", e)
        }
    }

    /**
     * 上报 API/网络错误
     * @param endpoint API 端点（不含 query）
     * @param httpCode HTTP 状态码（IO 异常可传 -1）
     * @param errorMessage 错误详情
     */
    fun reportApiError(
        endpoint: String,
        httpCode: Int,
        errorMessage: String,
        bvid: String? = null
    ) {
        if (!isEnabled) return
        if (!hasNonFatalReportingHeadroom()) return
        val safeEndpoint = normalizeApiErrorEndpoint(endpoint)
        val key = "api:$httpCode:$safeEndpoint"
        if (shouldDropByRateLimit(key, apiFailureReportIntervalMs(httpCode))) return

        try {
            crashlytics.setCustomKey("api_endpoint", safeEndpoint)
            crashlytics.setCustomKey("api_http_code", httpCode)
            crashlytics.log("API Error: [$httpCode] $safeEndpoint - ${errorMessage.take(300)}")
            crashlytics.recordException(ApiException(safeEndpoint, httpCode, errorMessage))
            Logger.e(TAG, "API error: [$httpCode] $safeEndpoint - $errorMessage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report API error", e)
        }
    }

    /**
     * 上报弹幕加载错误
     */
    fun reportDanmakuError(cid: Long, errorMessage: String, exception: Throwable? = null) {
        if (!isEnabled) return
        if (!hasNonFatalReportingHeadroom()) return
        val key = "danmaku:${exception?.javaClass?.name ?: "load_failure"}"
        if (shouldDropByRateLimit(key)) return

        try {
            setCustomKey("danmaku_cid", cid)
            exception?.let { setCustomKey("danmaku_exception_type", it.javaClass.simpleName.take(80)) }
            crashlytics.log("Danmaku Error: ${errorMessage.take(300)}")
            crashlytics.recordException(
                buildSanitizedThrowable(
                    DanmakuException(cid, errorMessage),
                    exception
                )
            )
            Logger.e(TAG, " Danmaku error reported: $errorMessage", exception)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report danmaku error", e)
        }
    }

    /**
     * 上报直播播放错误
     */
    fun reportLiveError(
        roomId: Long,
        errorType: String,
        errorMessage: String,
        exception: Throwable? = null
    ) {
        if (!isEnabled) return
        if (!hasNonFatalReportingHeadroom()) return
        val key = "live:${errorType.take(80)}"
        if (shouldDropByRateLimit(key)) return

        try {
            setCustomKey("live_room_id", roomId)
            setCustomKey("live_error_type", errorType)
            exception?.let { setCustomKey("live_exception_type", it.javaClass.simpleName.take(80)) }
            crashlytics.log("Live Error: [$errorType] ${errorMessage.take(300)}")
            crashlytics.recordException(
                buildSanitizedThrowable(
                    LiveStreamException(roomId, errorType, errorMessage),
                    exception
                )
            )
            Logger.e(TAG, " Live error reported: [$errorType] $errorMessage", exception)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report live error", e)
        }
    }

    /**
     * 标记直播会话开始（仅在进入直播页时调用，无常驻开销）
     */
    fun markLiveSessionStart(roomId: Long, title: String, uname: String) {
        if (!isEnabled || roomId <= 0) return

        val isSameSession = liveSessionActive && liveSessionRoomId == roomId
        liveSessionActive = true
        liveSessionRoomId = roomId
        if (!isSameSession) {
            liveSessionStartElapsedMs = SystemClock.elapsedRealtime()
            liveLastStage = ""
        }

        try {
            setCustomKey("live_session_active", true)
            setCustomKey("live_room_id", roomId)
            setCustomKey("live_room_title", title.take(120))
            setCustomKey("live_anchor_name", uname.take(80))
            markLivePlaybackStage("session_start")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark live session start", e)
        }
    }

    /**
     * 标记直播会话阶段（去重后才写入，避免高频日志）
     */
    fun markLivePlaybackStage(stage: String) {
        if (!isEnabled || !liveSessionActive) return
        if (!shouldUpdateLiveTraceStage(lastStage = liveLastStage, nextStage = stage)) return

        val normalizedStage = sanitizeLiveTraceStage(stage)
        liveLastStage = normalizedStage
        try {
            setCustomKey("live_last_stage", normalizedStage)
            setCustomKey(
                "live_session_uptime_ms",
                liveSessionDurationMs(
                    nowElapsedMs = SystemClock.elapsedRealtime(),
                    sessionStartElapsedMs = liveSessionStartElapsedMs
                )
            )
            log("LIVE_STAGE:$normalizedStage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark live playback stage", e)
        }
    }

    /**
     * 标记直播会话结束
     */
    fun markLiveSessionEnd(reason: String) {
        if (!isEnabled || !liveSessionActive) return

        val normalizedReason = sanitizeLiveTraceStage(reason).ifBlank { "exit" }
        try {
            val duration = liveSessionDurationMs(
                nowElapsedMs = SystemClock.elapsedRealtime(),
                sessionStartElapsedMs = liveSessionStartElapsedMs
            )
            setCustomKey("live_session_uptime_ms", duration)
            setCustomKey("live_last_stage", "session_end_$normalizedReason")
            setCustomKey("live_session_active", false)
            log("LIVE_END:reason=$normalizedReason,durationMs=$duration")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark live session end", e)
        } finally {
            liveSessionActive = false
            liveSessionRoomId = 0L
            liveSessionStartElapsedMs = 0L
            liveLastStage = ""
        }
    }

    /**
     * 手动触发崩溃（仅用于测试）
     */
    fun testCrash() {
        throw RuntimeException("CrashReporter Test Crash")
    }

    private fun shouldDropByRateLimit(
        key: String,
        minIntervalMs: Long = RATE_LIMIT_WINDOW_MS
    ): Boolean = nonFatalRateLimiter.shouldDrop(key, SystemClock.elapsedRealtime(), minIntervalMs)

    private fun hasNonFatalReportingHeadroom(): Boolean {
        val runtime = Runtime.getRuntime()
        return shouldRecordNonFatalEvent(
            maxMemoryBytes = runtime.maxMemory(),
            totalMemoryBytes = runtime.totalMemory(),
            freeMemoryBytes = runtime.freeMemory()
        )
    }

    private fun shouldCacheAndWriteCustomKey(key: String, value: Any): Boolean {
        if (key.isBlank()) return false
        val previous = lastCustomKeyValues.put(key, value)
        if (lastCustomKeyValues.size > CUSTOM_KEY_CACHE_MAX_KEYS) {
            val keepEntries = lastCustomKeyValues.entries.take(CUSTOM_KEY_CACHE_MAX_KEYS / 2)
            lastCustomKeyValues.clear()
            keepEntries.forEach { entry ->
                lastCustomKeyValues[entry.key] = entry.value
            }
        }
        return shouldWriteCrashCustomKey(previousValue = previous, nextValue = value)
    }

    private fun <T : Throwable> buildSanitizedThrowable(sanitized: T, original: Throwable?): T {
        if (original == null) return sanitized
        sanitized.stackTrace = original.stackTrace
        return sanitized
    }
}

// ========== 自定义异常类（用于 Crashlytics 分类） ==========

/**
 * 视频播放异常
 */
class VideoPlaybackException(
    val errorType: String,
    override val message: String
) : Exception("[$errorType] $message")

/**
 * API 请求异常
 */
class ApiException(
    val endpoint: String,
    val httpCode: Int,
    override val message: String
) : Exception("[$httpCode] $endpoint: $message")

/**
 * 弹幕加载异常
 */
class DanmakuException(
    val cid: Long,
    override val message: String
) : Exception("Danmaku error: $message")

/**
 * 直播播放异常
 */
class LiveStreamException(
    val roomId: Long,
    val errorType: String,
    override val message: String
) : Exception("[$errorType] Live stream error: $message")
