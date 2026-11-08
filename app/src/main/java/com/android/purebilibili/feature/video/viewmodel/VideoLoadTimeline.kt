package com.android.purebilibili.feature.video.viewmodel

internal enum class VideoLoadStage {
    DETAILS_READY,
    PLAY_URL_READY,
    PLAYER_PREPARE_REQUESTED,
    FIRST_FRAME,
    RELATED_REQUEST_STARTED,
    RELATED_RESULT
}

internal class VideoLoadTimeline(private val nowMs: () -> Long) {
    private var requestToken = -1L
    private var startedAtMs = 0L
    private val stages = mutableSetOf<VideoLoadStage>()

    fun start(token: Long) {
        requestToken = token
        startedAtMs = nowMs()
        stages.clear()
    }

    fun mark(stage: VideoLoadStage, token: Long): Long? {
        if (token != requestToken) return null
        if (stage == VideoLoadStage.FIRST_FRAME && VideoLoadStage.PLAYER_PREPARE_REQUESTED !in stages) {
            return null
        }
        if (!stages.add(stage)) return null
        return (nowMs() - startedAtMs).coerceAtLeast(0L)
    }
}
