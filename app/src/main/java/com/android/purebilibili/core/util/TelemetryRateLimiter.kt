package com.android.purebilibili.core.util

/** Atomic duplicate suppression with bounded storage and an optional rolling total budget. */
internal class TelemetryRateLimiter(
    private val maxKeys: Int,
    private val maxEventsPerWindow: Int = Int.MAX_VALUE,
    private val windowMs: Long = 60_000L
) {
    private val lastAccepted = LinkedHashMap<String, Long>()
    private val acceptedTimes = java.util.ArrayDeque<Long>()

    init {
        require(maxKeys > 0 && maxEventsPerWindow > 0 && windowMs > 0)
    }

    @Synchronized
    fun shouldDrop(key: String, nowMs: Long, minIntervalMs: Long): Boolean {
        val previous = lastAccepted[key]
        if (previous != null && nowMs - previous < minIntervalMs) return true
        if (maxEventsPerWindow != Int.MAX_VALUE) {
            while (acceptedTimes.isNotEmpty() && nowMs - acceptedTimes.peekFirst() >= windowMs) {
                acceptedTimes.removeFirst()
            }
            if (acceptedTimes.size >= maxEventsPerWindow) return true
            acceptedTimes.addLast(nowMs)
        }
        lastAccepted.remove(key)
        if (lastAccepted.size >= maxKeys) {
            val oldest = lastAccepted.keys.iterator()
            oldest.next()
            oldest.remove()
        }
        lastAccepted[key] = nowMs
        return false
    }

    @Synchronized
    fun clear() {
        lastAccepted.clear()
        acceptedTimes.clear()
    }
}
