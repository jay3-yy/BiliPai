package com.android.purebilibili.core.util

internal data class PlaybackAnalyticsUpdate(
    val started: Boolean = false,
    val checkpoints: List<Int> = emptyList(),
    val completed: Boolean = false,
    val watchTimeSeconds: Long = 0L
)

/** Per-media playback milestones. Media identity is used locally and never added to events. */
internal class PlaybackAnalyticsTracker {
    private var mediaKey = ""
    private var lastPositionMs: Long? = null
    private var lastSampleMs: Long? = null
    private var wasPlaying = false
    private var watchedMs = 0L
    private var started = false
    private var completed = false
    private var advancedSinceSeek = false
    private val reportedCheckpoints = mutableSetOf<Int>()
    private val progressCheckpoints = intArrayOf(25, 50, 75)

    fun reset() {
        mediaKey = ""
        lastPositionMs = null
        lastSampleMs = null
        wasPlaying = false
        watchedMs = 0L
        started = false
        completed = false
        advancedSinceSeek = false
        reportedCheckpoints.clear()
    }

    fun sample(
        key: String,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        nowMs: Long,
        discontinuity: Boolean = false,
        ended: Boolean = false
    ): PlaybackAnalyticsUpdate {
        if (key != mediaKey) {
            reset()
            mediaKey = key
        }
        val position = positionMs.coerceAtLeast(0L)
        val previousPosition = lastPositionMs
        val elapsed = lastSampleMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
        if (wasPlaying) watchedMs += elapsed
        val advanced = !discontinuity && previousPosition != null &&
            position > previousPosition && elapsed > 0L && (wasPlaying || isPlaying)
        if (discontinuity) advancedSinceSeek = false
        if (advanced) advancedSinceSeek = true
        val didStart = !started && isPlaying && durationMs > 0L
        if (didStart) started = true
        val checkpoints = mutableListOf<Int>()
        if (started && advanced && durationMs > 0L) {
            for (checkpoint in progressCheckpoints) {
                val threshold = durationMs / 100L * checkpoint + durationMs % 100L * checkpoint / 100L
                if (previousPosition != null && previousPosition < threshold && position >= threshold &&
                    reportedCheckpoints.add(checkpoint)
                ) checkpoints.add(checkpoint)
            }
        }
        // An immediate end after seeking to the end is not a natural completion.
        val atEnd = durationMs > 0L && position >= (durationMs - 1000L).coerceAtLeast(0L)
        val didComplete = ended && atEnd && started && advancedSinceSeek && !completed
        if (didComplete) {
            completed = true
            if (reportedCheckpoints.add(100)) checkpoints.add(100)
        }
        lastPositionMs = position
        lastSampleMs = nowMs
        wasPlaying = isPlaying
        return PlaybackAnalyticsUpdate(didStart, checkpoints, didComplete, watchedMs / 1000L)
    }
}
