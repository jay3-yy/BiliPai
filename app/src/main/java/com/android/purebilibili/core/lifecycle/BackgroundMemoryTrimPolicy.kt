package com.android.purebilibili.core.lifecycle

import android.content.ComponentCallbacks2

/** 短暂切换保留热图片；持续后台在缓存进程可能冻结前释放页面像素并收紧预算。 */
internal const val BACKGROUND_IMAGE_TRIM_DELAY_MS = 5_000L
internal const val BACKGROUND_IMAGE_CACHE_LIGHT_MAX_BYTES = 64L * 1024 * 1024
internal const val BACKGROUND_IMAGE_CACHE_MAX_BYTES = 32L * 1024 * 1024

/**
 * 封面代表色表只存 Color 对象（128 条约几 KB），砍到 16 条省不下任何可观内存，
 * 却会让回前台后的几十张卡片排队重做「磁盘读 + 解码 + Palette 聚类」。
 * 因此即使真实内存压力下也只收敛到这个上限。
 */
internal const val BACKGROUND_COVER_COLOR_CACHE_MAX_ENTRIES = 64

internal fun resolveBackgroundImageCacheTrimTargetBytes(
    cacheSizeBytes: Long,
    backgroundElapsedMs: Long,
): Long {
    val budget = if (backgroundElapsedMs >= BACKGROUND_IMAGE_TRIM_DELAY_MS) {
        BACKGROUND_IMAGE_CACHE_MAX_BYTES
    } else {
        BACKGROUND_IMAGE_CACHE_LIGHT_MAX_BYTES
    }
    return cacheSizeBytes.coerceIn(0L, budget)
}

internal fun shouldTrimImageCacheAfterBackgroundDelay(
    isInBackground: Boolean,
    isPipActiveOrPending: Boolean,
    backgroundElapsedMs: Long,
): Boolean = isInBackground && !isPipActiveOrPending &&
    backgroundElapsedMs >= BACKGROUND_IMAGE_TRIM_DELAY_MS

/**
 * 应用级后台内存编排计划。
 * 由 Application.onTrimMemory / onLowMemory 解析后分发给图片缓存与播放器。
 */
internal data class BackgroundMemoryTrimPlan(
    /** 图片内存缓存目标档位；null 表示完全不碰缓存。 */
    val imageCacheTrimLevel: Int?,
    val clearImageMemoryCache: Boolean,
    val notifyPlayerHeavyOptimization: Boolean,
    val requestIdlePlaybackRelease: Boolean,
    /** 播放地址 / 视频详情 / 字幕等可随时重建的派生缓存。 */
    val releaseDerivedCaches: Boolean,
    /** 空闲 TCP / TLS 连接。清空后回前台所有请求都要重新握手。 */
    val releaseIdleNetworkConnections: Boolean
)

internal fun resolveBackgroundMemoryTrimPlan(level: Int): BackgroundMemoryTrimPlan {
    return when (level) {
        // 用户只是切到桌面 / 多任务（约 2~3 秒）。真正的收敛已经由 BackgroundManager
        // 驱动的后台预算完成，这里不再二次砍半，否则回前台要为同一批封面重复付出解码代价。
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> BackgroundMemoryTrimPlan(
            imageCacheTrimLevel = null,
            clearImageMemoryCache = false,
            notifyPlayerHeavyOptimization = false,
            requestIdlePlaybackRelease = false,
            releaseDerivedCaches = false,
            releaseIdleNetworkConnections = false
        )

        // 前台但内存偏低：适度收敛图片缓存并通知播放器降配，保留派生缓存与连接。
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> BackgroundMemoryTrimPlan(
            imageCacheTrimLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            clearImageMemoryCache = false,
            notifyPlayerHeavyOptimization = true,
            requestIdlePlaybackRelease = false,
            releaseDerivedCaches = false,
            releaseIdleNetworkConnections = false
        )

        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> BackgroundMemoryTrimPlan(
            imageCacheTrimLevel = ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            clearImageMemoryCache = false,
            notifyPlayerHeavyOptimization = true,
            requestIdlePlaybackRelease = true,
            releaseDerivedCaches = true,
            releaseIdleNetworkConnections = true
        )

        // 进程已进入后台候选区：此时用户看不到界面，可以放心释放全部可重建资源。
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
        ComponentCallbacks2.TRIM_MEMORY_MODERATE,
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> BackgroundMemoryTrimPlan(
            imageCacheTrimLevel = ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            clearImageMemoryCache = true,
            notifyPlayerHeavyOptimization = true,
            requestIdlePlaybackRelease = level != ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            releaseDerivedCaches = true,
            releaseIdleNetworkConnections = true
        )

        else -> BackgroundMemoryTrimPlan(
            imageCacheTrimLevel = null,
            clearImageMemoryCache = false,
            notifyPlayerHeavyOptimization = false,
            requestIdlePlaybackRelease = false,
            releaseDerivedCaches = false,
            releaseIdleNetworkConnections = false
        )
    }
}
