package com.android.purebilibili.feature.plugin

internal val CDN_AUTO_CONCURRENCY_LADDER = intArrayOf(4, 6, 8, 12, 16, 24, 32)

/**
 * Adaptive thread ladder controller inspired by BTR's `createAutoConcurrency`:
 * starts with a brief startup/seek boost, steps up when buffer/first-byte latency shows pressure,
 * verifies each upward step after a 10s throughput trial, and backs off hard on HTTP 412/429.
 */
internal class CdnAutoConcurrencyController(
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private data class Bucket(val atMs: Long, val bytes: Long)
    private data class Rest(val untilMs: Long, val hard: Boolean)
    private data class Trial(val fromLevel: Int, val baselineBps: Long, val checkAtMs: Long)

    private var level: Int = DEFAULT_BASE_LEVEL
    private var changedAtMs: Long = 0L
    private var startupBaseLevel: Int? = null
    private var startupUntilMs: Long = 0L
    private var trial: Trial? = null
    private val resting = mutableMapOf<Int, Rest>()
    private val buckets = ArrayDeque<Bucket>()
    private var lastReason: String = "稳态"

    @Synchronized
    fun onNewSession(maxCap: Int) {
        val now = nowMs()
        val maxIdx = maxLadderIndex(maxCap)
        val targetStartup = minOf(maxIdx, if (maxCap >= 16) STARTUP_LEVEL_HIGH else STARTUP_LEVEL_MED)
        if (level < targetStartup) {
            startupBaseLevel = level
            level = targetStartup
            changedAtMs = now
            startupUntilMs = now + STARTUP_MAX_MS
            trial = null
            lastReason = "起播加速"
        } else {
            startupBaseLevel = null
            startupUntilMs = 0L
        }
    }

    @Synchronized
    fun onBytesDelivered(bytes: Int) {
        if (bytes <= 0) return
        val now = nowMs()
        pruneBuckets(now)
        val last = buckets.lastOrNull()
        if (last != null && now - last.atMs < BUCKET_MS) {
            buckets[buckets.lastIndex] = last.copy(bytes = last.bytes + bytes)
        } else {
            buckets.addLast(Bucket(atMs = now, bytes = bytes.toLong()))
        }
    }

    @Synchronized
    fun onFirstByteSlow(firstByteMs: Long, saturated: Boolean, maxCap: Int) {
        if (!saturated || firstByteMs < SLOW_FIRST_BYTE_MS) return
        val now = nowMs()
        maybeStepUp(now = now, maxCap = maxCap, reason = "首包较慢")
    }

    @Synchronized
    fun onPlaybackSample(
        bufferMs: Long,
        bitrateBps: Long,
        saturated: Boolean,
        maxCap: Int,
    ) {
        val now = nowMs()
        val maxIdx = maxLadderIndex(maxCap)
        if (level > maxIdx) {
            level = maxIdx
            trial = null
        }

        val base = startupBaseLevel
        if (base != null && (bufferMs >= STARTUP_COMFORT_BUFFER_MS || now >= startupUntilMs)) {
            startupBaseLevel = null
            startupUntilMs = 0L
            if (level > base && trial == null) {
                level = base.coerceAtMost(maxIdx)
                changedAtMs = now
                lastReason = "起播结束"
            }
        }

        val currentTrial = trial
        if (currentTrial != null && now >= currentTrial.checkAtMs) {
            val currentBps = windowThroughputBps(now)
            trial = null
            if (currentTrial.baselineBps > 0L && currentBps * 100L < currentTrial.baselineBps * 112L) {
                resting[level] = Rest(untilMs = now + SOFT_REST_MS, hard = false)
                level = currentTrial.fromLevel.coerceAtMost(maxIdx)
                changedAtMs = now
                lastReason = "增益不足回退"
            } else {
                lastReason = "稳态"
            }
            return
        }

        val bps = windowThroughputBps(now)
        val underBitrate = bitrateBps > 0L && bps > 0L && bps * 8L < bitrateBps && saturated
        if (bufferMs < LOW_BUFFER_MS || underBitrate) {
            maybeStepUp(now = now, maxCap = maxCap, reason = if (bufferMs < LOW_BUFFER_MS) "低缓冲爬坡" else "带宽不足爬坡")
        }
    }

    @Synchronized
    fun onPushback(status: Int) {
        if (status != 412 && status != 429) return
        val now = nowMs()
        startupBaseLevel = null
        startupUntilMs = 0L
        trial = null
        resting[level] = Rest(untilMs = now + PUSHBACK_REST_MS, hard = true)
        val nextLevel = (level - 2).coerceAtLeast(0)
        if (nextLevel != level) {
            level = nextLevel
            changedAtMs = now
        }
        lastReason = "限流回退"
    }

    @Synchronized
    fun resolveTargetConnections(
        bufferMs: Long,
        bitrateBps: Long,
        maxCap: Int,
        isAuto: Boolean,
    ): Pair<Int, String> {
        val now = nowMs()
        val clampedCap = maxCap.coerceIn(1, CDN_ENHANCED_MAX_CONNECTIONS)
        val hardCapIdx = hardCapIndex(now)
        val hardCapThreads = CDN_AUTO_CONCURRENCY_LADDER[hardCapIdx].coerceAtMost(clampedCap)

        if (!isAuto) {
            val fixed = when {
                isInHardPushback(now) -> 1
                bufferMs >= HIGH_BUFFER_MS -> minOf(clampedCap, 2)
                else -> clampedCap
            }
            val reason = when {
                isInHardPushback(now) -> "限流保护"
                bufferMs >= HIGH_BUFFER_MS -> "缓冲充足"
                else -> "固定线程"
            }
            return fixed to reason
        }

        val currentThreads = CDN_AUTO_CONCURRENCY_LADDER[level.coerceIn(0, maxLadderIndex(clampedCap))]
            .coerceAtMost(hardCapThreads)
        val effective = when {
            isInHardPushback(now) -> minOf(currentThreads, 2)
            bufferMs >= HIGH_BUFFER_MS && startupBaseLevel == null -> minOf(currentThreads, 4)
            else -> currentThreads
        }
        val reason = when {
            isInHardPushback(now) -> "限流回退"
            bufferMs >= HIGH_BUFFER_MS && startupBaseLevel == null -> "缓冲充足"
            else -> lastReason
        }
        return effective.coerceAtLeast(1) to reason
    }

    @Synchronized
    fun currentWindowThroughputBps(): Long = windowThroughputBps(nowMs())

    @Synchronized
    fun reset() {
        level = DEFAULT_BASE_LEVEL
        changedAtMs = 0L
        startupBaseLevel = null
        startupUntilMs = 0L
        trial = null
        resting.clear()
        buckets.clear()
        lastReason = "稳态"
    }

    private fun maybeStepUp(now: Long, maxCap: Int, reason: String) {
        if (now - changedAtMs < STEP_COOLDOWN_MS) return
        val maxIdx = minOf(maxLadderIndex(maxCap), hardCapIndex(now))
        if (level >= maxIdx) return
        val next = level + 1
        val rest = resting[next]
        if (rest != null) {
            if (rest.untilMs > now) return
            resting.remove(next)
        }
        val baseline = windowThroughputBps(now)
        startupBaseLevel = null
        startupUntilMs = 0L
        trial = Trial(fromLevel = level, baselineBps = baseline, checkAtMs = now + TRIAL_MS)
        level = next
        changedAtMs = now
        lastReason = reason
    }

    private fun hardCapIndex(now: Long): Int {
        var cap = CDN_AUTO_CONCURRENCY_LADDER.lastIndex
        val expired = mutableListOf<Int>()
        for ((idx, rest) in resting) {
            if (rest.untilMs <= now) {
                expired += idx
            } else if (rest.hard && idx - 1 < cap) {
                cap = (idx - 1).coerceAtLeast(0)
            }
        }
        expired.forEach(resting::remove)
        return cap
    }

    private fun isInHardPushback(now: Long): Boolean {
        return resting.values.any { it.hard && it.untilMs > now }
    }

    private fun maxLadderIndex(maxCap: Int): Int {
        val cap = maxCap.coerceAtLeast(1)
        val idx = CDN_AUTO_CONCURRENCY_LADDER.indexOfLast { it <= cap }
        return if (idx >= 0) idx else 0
    }

    private fun pruneBuckets(now: Long) {
        while (buckets.isNotEmpty() && now - buckets.first().atMs > WINDOW_MS) {
            buckets.removeFirst()
        }
    }

    private fun windowThroughputBps(now: Long): Long {
        pruneBuckets(now)
        if (buckets.isEmpty()) return 0L
        val totalBytes = buckets.sumOf { it.bytes }
        val spanMs = (buckets.last().atMs - buckets.first().atMs + BUCKET_MS).coerceAtLeast(1_000L)
        return totalBytes * 1_000L / spanMs
    }

    private companion object {
        const val DEFAULT_BASE_LEVEL = 2 // 8 threads
        const val STARTUP_LEVEL_MED = 3 // 12 threads
        const val STARTUP_LEVEL_HIGH = 4 // 16 threads
        const val STARTUP_COMFORT_BUFFER_MS = 15_000L
        const val STARTUP_MAX_MS = 25_000L
        const val STEP_COOLDOWN_MS = 2_500L
        const val TRIAL_MS = 10_000L
        const val WINDOW_MS = 5_000L
        const val BUCKET_MS = 250L
        const val SOFT_REST_MS = 90_000L
        const val PUSHBACK_REST_MS = 180_000L
        const val LOW_BUFFER_MS = 6_000L
        const val HIGH_BUFFER_MS = 30_000L
        const val SLOW_FIRST_BYTE_MS = 1_500L
    }
}
