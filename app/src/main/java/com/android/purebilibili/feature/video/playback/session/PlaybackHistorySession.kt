package com.android.purebilibili.feature.video.playback.session

internal data class PlaybackHistoryProgress(
    val bvid: String,
    val cid: Long,
    val positionMs: Long,
    val durationMs: Long
)

/** A restored position or a prepared source is not evidence that this media was watched. */
internal class PlaybackHistorySession {
    private var bvid: String = ""
    private var cid: Long = 0L
    private var started = false

    fun bindMedia(bvid: String, cid: Long, preserveStarted: Boolean = false) {
        if (preserveStarted && this.bvid == bvid && this.cid == cid) return
        this.bvid = bvid
        this.cid = cid
        started = false
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying && bvid.isNotBlank() && cid > 0L) started = true
    }

    fun canWriteHistory(bvid: String, cid: Long): Boolean =
        started && bvid.isNotBlank() && cid > 0L && this.bvid == bvid && this.cid == cid

    fun captureProgress(
        bvid: String,
        cid: Long,
        positionMs: Long,
        durationMs: Long,
        playbackEnded: Boolean
    ): PlaybackHistoryProgress? {
        if (!canWriteHistory(bvid, cid) || durationMs <= 0L) return null
        return PlaybackHistoryProgress(
            bvid = bvid,
            cid = cid,
            positionMs = if (playbackEnded) durationMs else positionMs.coerceAtLeast(0L),
            durationMs = durationMs
        )
    }
}
