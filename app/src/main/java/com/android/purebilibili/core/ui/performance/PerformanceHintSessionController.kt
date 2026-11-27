package com.android.purebilibili.core.ui.performance

import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import android.os.Process
import androidx.annotation.RequiresApi
import kotlin.math.roundToLong

internal const val DEFAULT_ADPF_TARGET_FRAME_NANOS = 16_666_667L // 60fps
internal const val MIN_ADPF_REPORT_INTERVAL_NANOS = 8_000_000L
private const val ADPF_48FPS_TARGET_NANOS = 20_833_333L
private const val ADPF_30FPS_TARGET_NANOS = 33_333_333L
private const val ADPF_24FPS_TARGET_NANOS = 41_666_667L
private const val MIN_TARGET_FPS = 24f
private const val MAX_TARGET_FPS = 240f
private const val MIN_ADPF_TARGET_NANOS = 4_000_000L
private const val MAX_CONTINUOUS_FRAME_INTERVAL_NANOS = 66_666_667L // 15fps 下界；超过视为暂停/seek 间隙

/** 根据目标帧率计算单帧目标工作时长（纳秒）。 */
internal fun resolveTargetFrameDurationNanos(refreshRateHz: Float): Long {
    val safeHz = if (refreshRateHz.isFinite() && refreshRateHz > 0f) {
        refreshRateHz.coerceIn(MIN_TARGET_FPS, MAX_TARGET_FPS)
    } else {
        60f
    }
    return (1_000_000_000.0 / safeHz.toDouble()).roundToLong().coerceAtLeast(MIN_ADPF_TARGET_NANOS)
}

/**
 * 解析设备实际可用的 ADPF 上报节流周期（纳秒）：
 * - 返回 `null` 表示系统明确报告不支持（`preferredUpdateRateNanos < 0`）；
 * - 对返回 `0` 的 OEM 强制施加下限，避免每帧无节流发起 Binder/HAL 调用。
 */
internal fun resolveEffectiveAdpfUpdateRateNanos(preferredUpdateRateNanos: Long): Long? {
    if (preferredUpdateRateNanos < 0L) return null
    return preferredUpdateRateNanos.coerceAtLeast(MIN_ADPF_REPORT_INTERVAL_NANOS)
}

/**
 * 将自定义 GL 渲染线程观察到的帧间隔量化为常见视频档位（60/48/30/24fps），
 * 避免 24fps 番剧误用 16.67ms 目标触发过度拉频，同时避免微小解码抖动引起频繁更新目标。
 */
internal fun resolveAdaptiveGlTargetFrameDurationNanos(
    observedFrameIntervalNanos: Long,
    explicitFrameRateHz: Float = 0f,
): Long {
    if (explicitFrameRateHz.isFinite() && explicitFrameRateHz > 1f) {
        return resolveTargetFrameDurationNanos(explicitFrameRateHz)
    }
    if (observedFrameIntervalNanos !in MIN_ADPF_TARGET_NANOS..MAX_CONTINUOUS_FRAME_INTERVAL_NANOS) {
        return DEFAULT_ADPF_TARGET_FRAME_NANOS
    }
    return when {
        observedFrameIntervalNanos <= 18_500_000L -> DEFAULT_ADPF_TARGET_FRAME_NANOS
        observedFrameIntervalNanos <= 27_000_000L -> ADPF_48FPS_TARGET_NANOS
        observedFrameIntervalNanos <= 36_500_000L -> ADPF_30FPS_TARGET_NANOS
        else -> ADPF_24FPS_TARGET_NANOS
    }
}

/** 校验工作时长合法性并遵循有效上报间隔；使用显式 `hasReported` 避免 `System.nanoTime()` 原点为 0 的哨兵歧义。 */
internal fun shouldReportPerformanceHintSample(
    nowNanos: Long,
    lastReportNanos: Long,
    hasReported: Boolean,
    preferredUpdateRateNanos: Long,
    actualDurationNanos: Long,
): Boolean {
    if (actualDurationNanos <= 0L) return false
    if (!hasReported) return true
    val minInterval = resolveEffectiveAdpfUpdateRateNanos(preferredUpdateRateNanos) ?: return false
    return (nowNanos - lastReportNanos) >= minInterval
}

/**
 * Android 12+ ADPF（`PerformanceHintManager.Session`）轻量控制器，专用于 HWUI 未纳管的应用自建渲染线程
 * （例如 `Anime4KPipelineRenderer` 的 GL 渲染线程）：
 * - 校验线程 TID 变化（Android 14+ 走 `setThreads`，低版本关闭重建）；
 * - 缓存设备不支持状态，避免反复发起失败的 Binder IPC；
 * - Android 15+ 支持 `setPreferPowerEfficiency` 与 `DeviceThermalBudgetState` 联动；
 * - 切后台、暂停或释放资源时立即 `close()`。
 */
internal class PerformanceHintSessionController(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var api31Session: Any? = null
    private var sessionTid: Int = 0
    private var sessionUnsupported: Boolean = false
    private var effectiveUpdateRateNanos: Long = MIN_ADPF_REPORT_INTERVAL_NANOS
    private var targetDurationNanos: Long = 0L
    private var lastReportNanos: Long = 0L
    private var hasReported: Boolean = false
    private var preferPowerEfficiency: Boolean = false
    private var appliedPowerEfficiency: Boolean? = null

    fun ensureStartedForCurrentThread(targetFrameDurationNanos: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val safeTarget = targetFrameDurationNanos.coerceAtLeast(MIN_ADPF_TARGET_NANOS)
        val currentTid = Process.myTid()
        synchronized(lock) {
            if (sessionUnsupported) return
            ensureStartedApi31(currentTid = currentTid, safeTarget = safeTarget)
        }
    }

    fun setPreferPowerEfficiency(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        synchronized(lock) {
            preferPowerEfficiency = enabled
            applyPowerEfficiencyApi35Locked()
        }
    }

    fun reportWorkDuration(actualDurationNanos: Long, nowNanos: Long = System.nanoTime()) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        synchronized(lock) {
            if (api31Session == null) return
            if (!shouldReportPerformanceHintSample(
                    nowNanos = nowNanos,
                    lastReportNanos = lastReportNanos,
                    hasReported = hasReported,
                    preferredUpdateRateNanos = effectiveUpdateRateNanos,
                    actualDurationNanos = actualDurationNanos,
                )
            ) {
                return
            }
            lastReportNanos = nowNanos
            hasReported = true
            reportApi31Locked(actualDurationNanos)
        }
    }

    fun close() {
        synchronized(lock) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                closeApi31Locked()
            }
            api31Session = null
            sessionTid = 0
            targetDurationNanos = 0L
            lastReportNanos = 0L
            hasReported = false
            appliedPowerEfficiency = null
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun ensureStartedApi31(currentTid: Int, safeTarget: Long) {
        val existing = api31Session as? PerformanceHintManager.Session
        if (existing != null) {
            if (sessionTid != currentTid) {
                val updatedThreads = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    runCatching {
                        existing.setThreads(intArrayOf(currentTid))
                        sessionTid = currentTid
                        true
                    }.getOrDefault(false)
                } else {
                    false
                }
                if (!updatedThreads) {
                    closeApi31Locked()
                    api31Session = null
                    sessionTid = 0
                    targetDurationNanos = 0L
                    lastReportNanos = 0L
                    hasReported = false
                    appliedPowerEfficiency = null
                }
            }
            val retained = api31Session as? PerformanceHintManager.Session
            if (retained != null) {
                if (targetDurationNanos != safeTarget) {
                    runCatching {
                        retained.updateTargetWorkDuration(safeTarget)
                        targetDurationNanos = safeTarget
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    applyPowerEfficiencyApi35Locked()
                }
                return
            }
        }

        val manager = appContext.getSystemService(PerformanceHintManager::class.java)
        if (manager == null) {
            sessionUnsupported = true
            return
        }
        val preferredRate = runCatching { manager.preferredUpdateRateNanos }.getOrDefault(-1L)
        val resolvedRate = resolveEffectiveAdpfUpdateRateNanos(preferredRate)
        if (resolvedRate == null) {
            sessionUnsupported = true
            return
        }
        val created = runCatching {
            manager.createHintSession(intArrayOf(currentTid), safeTarget)
        }.getOrNull()
        if (created == null) {
            sessionUnsupported = true
            return
        }
        api31Session = created
        sessionTid = currentTid
        effectiveUpdateRateNanos = resolvedRate
        targetDurationNanos = safeTarget
        lastReportNanos = 0L
        hasReported = false
        appliedPowerEfficiency = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            applyPowerEfficiencyApi35Locked()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun applyPowerEfficiencyApi35Locked() {
        val session = api31Session as? PerformanceHintManager.Session ?: return
        if (appliedPowerEfficiency == preferPowerEfficiency) return
        runCatching {
            session.setPreferPowerEfficiency(preferPowerEfficiency)
            appliedPowerEfficiency = preferPowerEfficiency
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun reportApi31Locked(actualDurationNanos: Long) {
        val session = api31Session as? PerformanceHintManager.Session ?: return
        runCatching { session.reportActualWorkDuration(actualDurationNanos) }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun closeApi31Locked() {
        val session = api31Session as? PerformanceHintManager.Session ?: return
        runCatching { session.close() }
    }
}
