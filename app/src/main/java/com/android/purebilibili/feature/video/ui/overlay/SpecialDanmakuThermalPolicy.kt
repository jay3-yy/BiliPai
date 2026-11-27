package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.runtime.withFrameNanos
import com.android.purebilibili.core.ui.adaptive.DeviceThermalTier

private const val SPECIAL_DANMAKU_60HZ_INTERVAL_NS = 14_583_333L // 16.67ms - 1/8 jitter tolerance
private const val SPECIAL_DANMAKU_30HZ_INTERVAL_NS = 29_166_667L // 33.33ms - 1/8 jitter tolerance
private const val SPECIAL_DANMAKU_HEAVY_SCENE_THRESHOLD = 12

/**
 * 为高级弹幕（Mode 7）与 BAS 代码弹幕（Mode 9）解析热控最小帧间隔（ns）：
 * - `NORMAL`：`0L`，保持原生 VSync 全帧率；
 * - `ELEVATED`：轻量场景收敛至 60Hz，高密度场景（>12 个活跃对象）收敛至 30Hz；
 * - `CRITICAL`：统一收敛至 30Hz，避免主窗口高负载动画加剧降频与发热。
 */
internal fun resolveSpecialDanmakuMinFrameIntervalNs(
    thermalTier: DeviceThermalTier,
    activeCount: Int,
): Long = when (thermalTier) {
    DeviceThermalTier.NORMAL -> 0L
    DeviceThermalTier.ELEVATED -> if (activeCount > SPECIAL_DANMAKU_HEAVY_SCENE_THRESHOLD) {
        SPECIAL_DANMAKU_30HZ_INTERVAL_NS
    } else {
        SPECIAL_DANMAKU_60HZ_INTERVAL_NS
    }
    DeviceThermalTier.CRITICAL -> SPECIAL_DANMAKU_30HZ_INTERVAL_NS
}

/**
 * 始终在 VSync 边界等待下一允许提交的帧时间戳，避免 `delay` 引入相位拍频抖动。
 */
internal suspend fun awaitNextSpecialDanmakuFrame(
    lastFrameNs: Long,
    minIntervalNs: Long,
): Long {
    while (true) {
        val frameNs = withFrameNanos { it }
        if (minIntervalNs <= 0L || lastFrameNs <= 0L || (frameNs - lastFrameNs) >= minIntervalNs) {
            return frameNs
        }
    }
}
