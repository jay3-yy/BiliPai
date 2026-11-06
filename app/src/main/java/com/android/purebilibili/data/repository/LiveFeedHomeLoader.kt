package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.LiveFeedHomeSnapshot
import com.android.purebilibili.data.model.response.LiveRoom
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** 推荐内容先交付，关注分页只补充同一轮快照，不延后房间列表展示。 */
internal suspend fun completeLiveFeedHome(
    snapshot: LiveFeedHomeSnapshot,
    supplementFollow: Boolean,
    onRoomsReady: suspend (LiveFeedHomeSnapshot) -> Unit,
    loadFollowedRooms: suspend () -> Result<LivePagedResult<LiveRoom>>,
): LiveFeedHomeSnapshot {
    currentCoroutineContext().ensureActive()
    onRoomsReady(snapshot)
    if (!supplementFollow || !needsLiveFollowSupplement(snapshot)) return snapshot
    val followed = loadFollowedRooms()
    currentCoroutineContext().ensureActive()
    return followed.fold(
        onSuccess = { supplementLiveFollowSnapshot(snapshot, it.items, it.totalCount) },
        onFailure = { snapshot.copy(followError = it.message ?: "关注直播加载失败") },
    )
}
