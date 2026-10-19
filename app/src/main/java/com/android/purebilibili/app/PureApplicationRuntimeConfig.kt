package com.android.purebilibili.app

import android.os.Build
import com.android.purebilibili.core.lifecycle.BackgroundManager
import com.android.purebilibili.core.lifecycle.BackgroundMemoryTrimPlan
import com.android.purebilibili.core.lifecycle.resolveBackgroundMemoryTrimPlan as resolveTrimPlan
import com.android.purebilibili.core.util.AnalyticsHelper
import com.android.purebilibili.core.util.CrashReporter

internal object PureApplicationRuntimeConfig {
    const val TAG: String = "PureApplication"

    fun shouldBlockStartupForHomeVisualDefaultsMigration(): Boolean = false

    fun shouldDeferPlaylistRestoreAtStartup(): Boolean = true

    fun shouldDeferTelemetryInitAtStartup(): Boolean = true

    fun deferredNonCriticalStartupDelayMs(): Long = 900L

    fun shouldRequestDex2OatProfileInstall(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.N

    fun dex2OatProfileInstallDelayMs(): Long = 2_500L

    fun resolveImageMemoryCachePercent(): Double = 0.10

    fun resolveImageMemoryCacheMaxBytes(memoryClassMb: Int, isLowRamDevice: Boolean): Long {
        val proportionalBudget = (
            memoryClassMb.coerceAtLeast(1).toLong() * 1024 * 1024 * resolveImageMemoryCachePercent()
        ).toLong()
        // App cache budgets, not Android 17's process limits. Leave room for video and danmaku.
        val absoluteLimit = (if (isLowRamDevice) 32L else 96L) * 1024 * 1024
        return minOf(proportionalBudget, absoluteLimit)
    }

    fun resolveBackgroundMemoryTrimPlan(level: Int): BackgroundMemoryTrimPlan {
        return resolveTrimPlan(level)
    }

    fun resolveImageMemoryCacheTrimLevel(level: Int): Int? {
        return resolveTrimPlan(level).imageCacheTrimLevel
    }

    fun shouldClearImageMemoryCacheOnTrimLevel(level: Int): Boolean {
        return resolveTrimPlan(level).clearImageMemoryCache
    }

    fun shouldNotifyPlayerHeavyOptimizationOnTrimLevel(level: Int): Boolean {
        return resolveTrimPlan(level).notifyPlayerHeavyOptimization
    }

    fun shouldRequestIdlePlaybackReleaseOnTrimLevel(level: Int): Boolean {
        return resolveTrimPlan(level).requestIdlePlaybackRelease
    }

    fun shouldReleaseDerivedCachesOnTrimLevel(level: Int): Boolean {
        return resolveTrimPlan(level).releaseDerivedCaches
    }

    fun shouldReleaseIdleNetworkConnectionsOnTrimLevel(level: Int): Boolean {
        return resolveTrimPlan(level).releaseIdleNetworkConnections
    }

    fun createTelemetryBackgroundStateListener(): BackgroundManager.BackgroundStateListener =
        TelemetryBackgroundStateListener

    internal object TelemetryBackgroundStateListener : BackgroundManager.BackgroundStateListener {
        override fun onEnterBackground() {
            AnalyticsHelper.onAppBackground()
            CrashReporter.setAppForegroundState(false)
        }

        override fun onEnterForeground() {
            AnalyticsHelper.onAppForeground()
            CrashReporter.setAppForegroundState(true)
        }
    }
}
