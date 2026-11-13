package com.android.purebilibili.feature.video.viewmodel

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/** 相关推荐让位于当前视频的详情与取流；主加载失败也允许继续加载推荐。 */
internal suspend fun awaitRelatedVideoRequest(
    primaryLoad: Job?,
    requestIsCurrent: () -> Boolean,
): Boolean {
    primaryLoad?.join()
    return requestIsCurrent()
}

/** Requests may finish during entry, but an obsolete result must never be published. */
internal suspend fun awaitRelatedVideoPublication(
    publicationAllowed: StateFlow<Boolean>,
    requestIsCurrent: () -> Boolean,
): Boolean {
    publicationAllowed.first { it }
    return requestIsCurrent()
}
