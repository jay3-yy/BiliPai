package com.android.purebilibili.core.ui.transition

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class VideoCardLandingEvent(
    val sourceKey: String,
    val sourceLayout: VideoCardSourceLayout,
    val createdAtNanos: Long,
    /** 导航驱动提交速度（进度/秒）；返回键/程序化返回为 0。 */
    val releaseVelocity: Float = 0f,
)

/** A short-lived, single-consumer handoff survives source-card disposal and re-creation. */
internal class VideoCardLandingFeedbackState(
    private val nowNanos: () -> Long = System::nanoTime,
) {
    var event: VideoCardLandingEvent? by mutableStateOf(null)
        private set
    // Claiming must not invalidate snapshotFlow and cancel the animation it just started.
    private var claimedEvent: VideoCardLandingEvent? = null

    fun publish(
        sourceKey: String?,
        sourceLayout: VideoCardSourceLayout,
        committedReturn: Boolean,
        releaseVelocity: Float = 0f,
    ) {
        if (!committedReturn || sourceKey.isNullOrBlank()) return
        event = VideoCardLandingEvent(sourceKey, sourceLayout, nowNanos(), releaseVelocity)
    }

    fun claim(candidate: VideoCardLandingEvent, sourceKey: String?): Boolean {
        if (candidate !== event || candidate === claimedEvent || candidate.sourceKey != sourceKey) return false
        // A freshly restored slot may need a frame or two; a later scroll into view must not bounce.
        if (nowNanos() - candidate.createdAtNanos !in 0L..120_000_000L) return false
        claimedEvent = candidate
        return true
    }

    fun clear() {
        event = null
        claimedEvent = null
    }
}
