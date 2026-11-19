package com.android.purebilibili.navigation3.predictiveback

/** Maps a navigation driver's return progress to the visible pose of the floating card. */
internal class VideoCardReturnProgress {
    private var gestureStart: Float? = null
    private var lastSeekRaw = 0f
    private var lastSeekVisual = 0f
    private var activeSettlement: Any? = null
    private var releaseRaw = 0f
    private var releaseVisual = 0f
    private var visual = 0f

    fun seek(rawReturn: Float, gestureProgress: Float): Float {
        if (activeSettlement != null) {
            // Picking the floating card up during a spring starts at its visible pose.
            gestureStart = visual
            activeSettlement = null
        }
        val start = gestureStart ?: (rawReturn - gestureProgress).also { gestureStart = it }
        lastSeekRaw = rawReturn
        lastSeekVisual = resolveVideoCardSeekReturn(start, gestureProgress)
        visual = lastSeekVisual
        return visual
    }

    fun settle(rawReturn: Float, gestureProgress: Float, settlement: Any, committing: Boolean): Float {
        if (activeSettlement !== settlement) {
            activeSettlement = settlement
            releaseRaw = if (gestureStart != null) lastSeekRaw else rawReturn
            releaseVisual = if (gestureStart != null) {
                lastSeekVisual
            } else {
                resolveVideoCardSeekReturn(rawReturn - gestureProgress, gestureProgress)
            }
        }
        val raw = rawReturn.coerceIn(0f, 1f)
        val start = releaseRaw.coerceIn(0f, 1f)
        val startVisual = releaseVisual.coerceIn(0f, 1f)
        visual = if (committing) {
            if (start >= 1f) 1f else {
                startVisual + (1f - startVisual) * ((raw - start) / (1f - start)).coerceIn(0f, 1f)
            }
        } else {
            if (start <= 0f) 0f else startVisual * (raw / start).coerceIn(0f, 1f)
        }
        return visual
    }

    fun follow(rawReturn: Float, gestureActive: Boolean): Float {
        if (!gestureActive) reset()
        visual = rawReturn
        return visual
    }

    fun reset() {
        gestureStart = null
        activeSettlement = null
        visual = 0f
    }
}

internal fun resolveVideoCardSeekReturn(startReturn: Float, gestureProgress: Float): Float =
    (startReturn + gestureProgress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
