package com.android.purebilibili.navigation3

/** 异步导航只能完成仍属于当前来源页的最后一次请求。 */
internal class VideoNavigationRequestTracker {
    private var sequence = 0L

    fun begin(
        backStack: List<BiliPaiNavKey>,
        sourceGeneration: Long,
    ): VideoNavigationRequest = VideoNavigationRequest(++sequence, backStack.toList(), sourceGeneration)

    fun invalidate() {
        sequence += 1
    }

    fun isCurrent(
        request: VideoNavigationRequest,
        backStack: List<BiliPaiNavKey>,
        sourceGeneration: Long,
    ): Boolean = request.sequence == sequence &&
        request.backStack == backStack && request.sourceGeneration == sourceGeneration
}

internal data class VideoNavigationRequest(
    val sequence: Long,
    val backStack: List<BiliPaiNavKey>,
    val sourceGeneration: Long,
)
