package com.android.purebilibili.core.performance

import kotlin.math.ceil

/** 固定 1ms 桶，最后一桶表示超过 500ms；不保留逐帧对象。 */
internal class PerformanceFrameSummary {
    private val histogram = LongArray(502)
    var frames = 0L
        private set
    var jankyFrames = 0L
        private set
    var longFrames100Ms = 0L
        private set
    var totalDurationNanos = 0L
        private set
    var maxDurationNanos = 0L
        private set

    fun add(durationNanos: Long, isJank: Boolean) {
        val bucket = ceil(durationNanos / 1_000_000.0).toInt().coerceIn(0, 501)
        histogram[bucket]++
        frames++
        totalDurationNanos += durationNanos
        if (isJank) jankyFrames++
        if (durationNanos >= 100_000_000L) longFrames100Ms++
        maxDurationNanos = maxOf(maxDurationNanos, durationNanos)
    }

    fun merge(other: PerformanceFrameSummary) {
        histogram.indices.forEach { histogram[it] += other.histogram[it] }
        frames += other.frames
        jankyFrames += other.jankyFrames
        longFrames100Ms += other.longFrames100Ms
        totalDurationNanos += other.totalDurationNanos
        maxDurationNanos = maxOf(maxDurationNanos, other.maxDurationNanos)
    }

    fun snapshot() = PerformanceFrameSummary().also { copy ->
        histogram.copyInto(copy.histogram)
        copy.frames = frames
        copy.totalDurationNanos = totalDurationNanos
        copy.jankyFrames = jankyFrames
        copy.longFrames100Ms = longFrames100Ms
        copy.maxDurationNanos = maxDurationNanos
    }

    // 无帧时没有百分位；501 明确表示 >500ms，不能作为精确耗时解释。
    fun percentileUpperBoundMs(percentile: Double): Int? {
        if (frames == 0L) return null
        val rank = ceil(frames * percentile).toLong().coerceAtLeast(1)
        var count = 0L
        return histogram.indices.first { count += histogram[it]; count >= rank }
    }
}
