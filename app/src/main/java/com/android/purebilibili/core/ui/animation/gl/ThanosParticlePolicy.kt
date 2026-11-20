package com.android.purebilibili.core.ui.animation.gl

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal const val THANOS_LONGEVITY = 1.5f
internal const val THANOS_TIME_SCALE = 1.15f
internal const val THANOS_TAIL_SECONDS = 0.9f
internal const val THANOS_DURATION_SECONDS = THANOS_LONGEVITY + THANOS_TAIL_SECONDS
internal const val THANOS_QUICK_TIME_SCALE = THANOS_DURATION_SECONDS / 0.7f
internal const val THANOS_PARTICLE_STRIDE_BYTES = 28

internal fun supportsThanosGlVersion(glEsVersion: Int): Boolean = glEsVersion >= 0x30000

internal data class ThanosParticleGrid(
    val columns: Int,
    val rows: Int,
    val pointSize: Float,
) {
    val count: Int get() = columns * rows
}

/** Port of ThanosEffect.Animation.calcParticlesGrid; budget replaces SharedConfig's tier. */
internal fun resolveThanosParticleGrid(
    width: Int,
    height: Int,
    density: Float,
    maxParticles: Int,
): ThanosParticleGrid {
    require(width > 0 && height > 0)
    val spacing = max(density * 0.4f, 1f)
    val count = (width.toDouble() * height / (spacing * spacing)).toInt()
        .coerceIn(10, maxParticles.coerceAtLeast(10))
    val aspect = width.toDouble() / height
    var rows = sqrt(count / aspect).roundToInt().coerceAtLeast(1)
    var columns = (count.toDouble() / rows).roundToInt().coerceAtLeast(1)
    while (columns * rows < count) {
        if (columns.toDouble() / rows < aspect) columns++ else rows++
    }
    return ThanosParticleGrid(columns, rows, max(width.toFloat() / columns, height.toFloat() / rows))
}

internal fun shouldNotifyParticleAnimationComplete(
    hasAnimationCompleted: Boolean,
    currentTimeSec: Float,
    animationDurationSec: Float,
): Boolean = !hasAnimationCompleted && currentTimeSec > animationDurationSec

// Begin UI reflow only during the final 180 ms; particle simulation remains unchanged.
internal const val THANOS_REFLOW_OVERLAP_SECONDS = 0.18f
internal fun shouldBeginThanosReflow(
    currentTimeSec: Float,
    timeScale: Float = THANOS_TIME_SCALE,
): Boolean = currentTimeSec >= THANOS_DURATION_SECONDS - THANOS_REFLOW_OVERLAP_SECONDS * timeScale
