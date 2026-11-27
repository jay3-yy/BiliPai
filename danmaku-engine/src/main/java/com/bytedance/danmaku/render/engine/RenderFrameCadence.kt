package com.bytedance.danmaku.render.engine

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Choreographer cadence controller.
 *
 * - Default (`targetFrameRateHz <= 0f`): native VSync cadence (zero skipped VSyncs).
 * - With a positive `targetFrameRateHz`: applies software VSync pacing ONLY when the
 *   display refresh rate is an integer multiple of the target (e.g. 120Hz -> 60/30Hz,
 *   90Hz -> 30Hz, 60Hz -> 30Hz) or unknown, so non-divisor panels (e.g. 90Hz with 60Hz
 *   target) never degrade into uneven 45fps judder while still allowing `Surface.setFrameRate`
 *   to switch LTPO panels natively in hardware.
 */
internal fun isHarmonicFrameRateDivisor(
    displayRefreshRateHz: Float,
    targetFrameRateHz: Float,
): Boolean {
    if (!targetFrameRateHz.isFinite() || targetFrameRateHz <= 0f) return false
    if (!displayRefreshRateHz.isFinite() || displayRefreshRateHz <= 0f) return true
    if (displayRefreshRateHz <= targetFrameRateHz + 1.5f) return false
    val ratio = displayRefreshRateHz / targetFrameRateHz
    val nearest = ratio.roundToInt()
    return nearest >= 2 && abs(ratio - nearest) <= 0.08f
}

internal class RenderFrameCadence {
    private var lastFrameNs: Long? = null
    private var targetFrameRateHz: Float = 0f
    private var displayRefreshRateHz: Float = 0f
    private var minFrameIntervalNs: Long = 0L

    fun updateCadence(targetFps: Float, displayHz: Float = 0f) {
        val sanitizedTarget = if (targetFps.isFinite() && targetFps > 0f) targetFps else 0f
        val sanitizedDisplay = if (displayHz.isFinite() && displayHz > 0f) displayHz else 0f
        if (targetFrameRateHz == sanitizedTarget && displayRefreshRateHz == sanitizedDisplay) return
        targetFrameRateHz = sanitizedTarget
        displayRefreshRateHz = sanitizedDisplay
        minFrameIntervalNs = if (isHarmonicFrameRateDivisor(sanitizedDisplay, sanitizedTarget)) {
            val nominalNs = (1_000_000_000.0 / sanitizedTarget).toLong()
            // Allow 1/8th-frame VSync jitter tolerance so minor Choreographer phase drift
            // never drops a harmonic tick onto the next cycle.
            (nominalNs - nominalNs / 8L).coerceAtLeast(1L)
        } else {
            0L
        }
    }

    fun shouldRender(frameTimeNs: Long): Boolean {
        val prev = lastFrameNs ?: return true
        if (frameTimeNs <= prev) return false
        val minInterval = minFrameIntervalNs
        return minInterval <= 0L || (frameTimeNs - prev) >= minInterval
    }

    fun onFrame(frameTimeNs: Long) {
        lastFrameNs = frameTimeNs
    }

    fun reset() {
        lastFrameNs = null
    }
}
