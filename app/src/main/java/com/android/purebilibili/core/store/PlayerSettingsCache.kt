package com.android.purebilibili.core.store

import android.content.Context
import com.android.purebilibili.core.player.ripper.DEFAULT_THREAD_RIPPER_CONCURRENCY
import com.android.purebilibili.core.player.ripper.DEFAULT_THREAD_RIPPER_ENABLED
import com.android.purebilibili.core.player.ripper.DEFAULT_THREAD_RIPPER_MULTI_HOST
import com.android.purebilibili.core.util.Logger

/**
 * 播放器设置缓存
 * 
 * 在 Application 启动时初始化，避免每次创建播放器时读取 SharedPreferences
 * 
 * 性能优化:
 * - 内存缓存硬件解码设置
 * - 避免重复 I/O 读取
 */
object PlayerSettingsCache {
    private const val TAG = "PlayerSettingsCache"
    private const val PREFS_NAME = "player_settings_cache"
    private const val KEY_HW_DECODE = "hw_decode_enabled"
    private const val KEY_SEEK_FAST = "seek_fast_enabled"
    private const val KEY_PLAYER_DIAGNOSTIC_LOGGING = "player_diagnostic_logging_enabled"
    private const val KEY_DASH_SEGMENT_REQUESTS_ENABLED = "dash_segment_requests_enabled"
    private const val KEY_THREAD_RIPPER_ENABLED = "thread_ripper_enabled"
    private const val KEY_THREAD_RIPPER_CONCURRENCY = "thread_ripper_concurrency"
    private const val KEY_THREAD_RIPPER_MULTI_HOST = "thread_ripper_multi_host"
    private const val LEGACY_HW_DECODE_PREFS_NAME = "hw_decode_cache"
    
    // 内存缓存
    @Volatile
    private var hwDecodeEnabled: Boolean? = null
    
    @Volatile
    private var seekFastEnabled: Boolean? = null

    @Volatile
    private var playerDiagnosticLoggingEnabled: Boolean? = null

    @Volatile
    private var dashSegmentRequestsEnabled: Boolean? = null

    @Volatile
    private var threadRipperEnabled: Boolean? = null

    @Volatile
    private var threadRipperConcurrency: Int? = null

    @Volatile
    private var threadRipperMultiHostEnabled: Boolean? = null
    
    /**
     * 初始化缓存（在 Application.onCreate 中调用）
     */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        hwDecodeEnabled = readAndMigrateHwDecodeEnabled(context, prefs)
        seekFastEnabled = prefs.getBoolean(KEY_SEEK_FAST, true)
        playerDiagnosticLoggingEnabled = prefs.getBoolean(
            KEY_PLAYER_DIAGNOSTIC_LOGGING,
            DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED
        )
        dashSegmentRequestsEnabled = prefs.getBoolean(
            KEY_DASH_SEGMENT_REQUESTS_ENABLED,
            DEFAULT_DASH_SEGMENT_REQUESTS_ENABLED
        )
        threadRipperEnabled = prefs.getBoolean(KEY_THREAD_RIPPER_ENABLED, DEFAULT_THREAD_RIPPER_ENABLED)
        threadRipperConcurrency = prefs.getInt(KEY_THREAD_RIPPER_CONCURRENCY, DEFAULT_THREAD_RIPPER_CONCURRENCY)
        threadRipperMultiHostEnabled = prefs.getBoolean(KEY_THREAD_RIPPER_MULTI_HOST, DEFAULT_THREAD_RIPPER_MULTI_HOST)
        Logger.d(
            TAG,
            "✅ 初始化完成: hwDecode=$hwDecodeEnabled, seekFast=$seekFastEnabled, " +
                "playerDiagnosticLogging=$playerDiagnosticLoggingEnabled, " +
                "dashSegmentRequests=$dashSegmentRequestsEnabled"
        )
    }
    
    /**
     * 获取硬件解码设置（优先使用内存缓存）
     */
    fun isHwDecodeEnabled(context: Context): Boolean {
        return hwDecodeEnabled ?: run {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val value = readAndMigrateHwDecodeEnabled(context, prefs)
            hwDecodeEnabled = value
            value
        }
    }
    
    /**
     * 设置硬件解码开关
     */
    fun setHwDecodeEnabled(context: Context, enabled: Boolean) {
        hwDecodeEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HW_DECODE, enabled)
            .apply()
        Logger.d(TAG, "💾 硬件解码设置已更新: $enabled")
    }
    
    /**
     * 获取快速 Seek 设置
     */
    fun isSeekFastEnabled(context: Context): Boolean {
        return seekFastEnabled ?: run {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val value = prefs.getBoolean(KEY_SEEK_FAST, true)
            seekFastEnabled = value
            value
        }
    }
    
    /**
     * 设置快速 Seek 开关
     */
    fun setSeekFastEnabled(context: Context, enabled: Boolean) {
        seekFastEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SEEK_FAST, enabled)
            .apply()
        Logger.d(TAG, "💾 快速 Seek 设置已更新: $enabled")
    }

    /**
     * 获取播放器诊断日志开关
     */
    fun isPlayerDiagnosticLoggingEnabled(context: Context): Boolean {
        return playerDiagnosticLoggingEnabled ?: run {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val value = prefs.getBoolean(
                KEY_PLAYER_DIAGNOSTIC_LOGGING,
                DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED
            )
            playerDiagnosticLoggingEnabled = value
            value
        }
    }

    /**
     * 获取播放器诊断日志开关（仅读取内存缓存）
     */
    fun isPlayerDiagnosticLoggingEnabled(): Boolean {
        return playerDiagnosticLoggingEnabled ?: DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED
    }

    /**
     * 设置播放器诊断日志开关
     */
    fun setPlayerDiagnosticLoggingEnabled(context: Context, enabled: Boolean) {
        playerDiagnosticLoggingEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PLAYER_DIAGNOSTIC_LOGGING, enabled)
            .apply()
        Logger.d(TAG, "💾 播放器诊断日志设置已更新: $enabled")
    }

    /**
     * 获取 DASH 分段请求开关。
     */
    fun isDashSegmentRequestsEnabled(context: Context): Boolean {
        return dashSegmentRequestsEnabled ?: run {
            val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(
                    KEY_DASH_SEGMENT_REQUESTS_ENABLED,
                    DEFAULT_DASH_SEGMENT_REQUESTS_ENABLED
                )
            dashSegmentRequestsEnabled = value
            value
        }
    }

    /**
     * 设置 DASH 分段请求开关。
     */
    fun setDashSegmentRequestsEnabled(context: Context, enabled: Boolean) {
        dashSegmentRequestsEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DASH_SEGMENT_REQUESTS_ENABLED, enabled)
            .apply()
        Logger.d(TAG, "💾 DASH 分段请求设置已更新: $enabled")
    }
    
    /** 线程撕裂者：多线程 Range 并发下载是否开启。 */
    fun isThreadRipperEnabled(context: Context): Boolean {
        return threadRipperEnabled ?: run {
            val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_THREAD_RIPPER_ENABLED, DEFAULT_THREAD_RIPPER_ENABLED)
            threadRipperEnabled = value
            value
        }
    }

    fun setThreadRipperEnabled(context: Context, enabled: Boolean) {
        threadRipperEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_THREAD_RIPPER_ENABLED, enabled)
            .apply()
        Logger.d(TAG, "💾 线程撕裂者开关已更新: $enabled")
    }

    /** 线程撕裂者：单条轨道的并发块数上限。 */
    fun getThreadRipperConcurrency(context: Context): Int {
        return threadRipperConcurrency ?: run {
            val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_THREAD_RIPPER_CONCURRENCY, DEFAULT_THREAD_RIPPER_CONCURRENCY)
            threadRipperConcurrency = value
            value
        }
    }

    fun setThreadRipperConcurrency(context: Context, concurrency: Int) {
        threadRipperConcurrency = concurrency
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_THREAD_RIPPER_CONCURRENCY, concurrency)
            .apply()
        Logger.d(TAG, "💾 线程撕裂者并发数已更新: $concurrency")
    }

    /** 线程撕裂者：是否把块分摊到多个大陆 CDN 主机。 */
    fun isThreadRipperMultiHostEnabled(context: Context): Boolean {
        return threadRipperMultiHostEnabled ?: run {
            val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_THREAD_RIPPER_MULTI_HOST, DEFAULT_THREAD_RIPPER_MULTI_HOST)
            threadRipperMultiHostEnabled = value
            value
        }
    }

    fun setThreadRipperMultiHostEnabled(context: Context, enabled: Boolean) {
        threadRipperMultiHostEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_THREAD_RIPPER_MULTI_HOST, enabled)
            .apply()
        Logger.d(TAG, "💾 线程撕裂者多线路分摊已更新: $enabled")
    }

    /**
     * 强制刷新缓存（设置页面修改后调用）
     */
    fun refresh(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        hwDecodeEnabled = readAndMigrateHwDecodeEnabled(context, prefs)
        seekFastEnabled = prefs.getBoolean(KEY_SEEK_FAST, true)
        playerDiagnosticLoggingEnabled = prefs.getBoolean(
            KEY_PLAYER_DIAGNOSTIC_LOGGING,
            DEFAULT_PLAYER_DIAGNOSTIC_LOGGING_ENABLED
        )
        dashSegmentRequestsEnabled = prefs.getBoolean(
            KEY_DASH_SEGMENT_REQUESTS_ENABLED,
            DEFAULT_DASH_SEGMENT_REQUESTS_ENABLED
        )
        threadRipperEnabled = prefs.getBoolean(KEY_THREAD_RIPPER_ENABLED, DEFAULT_THREAD_RIPPER_ENABLED)
        threadRipperConcurrency = prefs.getInt(KEY_THREAD_RIPPER_CONCURRENCY, DEFAULT_THREAD_RIPPER_CONCURRENCY)
        threadRipperMultiHostEnabled = prefs.getBoolean(KEY_THREAD_RIPPER_MULTI_HOST, DEFAULT_THREAD_RIPPER_MULTI_HOST)
        Logger.d(
            TAG,
            "🔄 缓存已刷新: hwDecode=$hwDecodeEnabled, seekFast=$seekFastEnabled, " +
                "playerDiagnosticLogging=$playerDiagnosticLoggingEnabled, " +
                "dashSegmentRequests=$dashSegmentRequestsEnabled"
        )
    }

    private fun readAndMigrateHwDecodeEnabled(
        context: Context,
        prefs: android.content.SharedPreferences
    ): Boolean {
        val legacyPrefs = context.getSharedPreferences(LEGACY_HW_DECODE_PREFS_NAME, Context.MODE_PRIVATE)
        val currentExists = prefs.contains(KEY_HW_DECODE)
        val legacyExists = legacyPrefs.contains(KEY_HW_DECODE)
        val value = resolveMigratedHwDecodeValue(
            currentExists = currentExists,
            currentValue = prefs.getBoolean(KEY_HW_DECODE, true),
            legacyExists = legacyExists,
            legacyValue = legacyPrefs.getBoolean(KEY_HW_DECODE, true)
        )

        // 老版本写入过 hw_decode_cache；迁移到播放器实际读取的缓存，保留用户选择。
        if (!currentExists && legacyExists) {
            prefs.edit()
                .putBoolean(KEY_HW_DECODE, value)
                .apply()
            Logger.d(TAG, "🔁 已迁移硬件解码缓存: hwDecode=$value")
        }

        return value
    }
}

internal fun resolveMigratedHwDecodeValue(
    currentExists: Boolean,
    currentValue: Boolean,
    legacyExists: Boolean,
    legacyValue: Boolean
): Boolean {
    return when {
        currentExists -> currentValue
        legacyExists -> legacyValue
        else -> true
    }
}
