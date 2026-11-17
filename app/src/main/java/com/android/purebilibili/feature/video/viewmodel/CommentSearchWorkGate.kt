package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.feature.video.playback.policy.VideoUiWorkPermission
import com.android.purebilibili.feature.video.playback.policy.runVisibleVideoUiWork
import kotlinx.coroutines.flow.MutableStateFlow

/** 搜索窗口拥有补拉许可；隐藏时取消当前页请求，恢复后只重做未完成的这一页。 */
internal class CommentSearchWorkGate {
    private val consumers = mutableSetOf<Any>()
    private val permission = MutableStateFlow(VideoUiWorkPermission.Hidden)

    val hasConsumers: Boolean get() = permission.value == VideoUiWorkPermission.Visible

    fun updateConsumer(owner: Any, active: Boolean) {
        if (active) consumers.add(owner) else consumers.remove(owner)
        permission.value = if (consumers.isEmpty()) VideoUiWorkPermission.Hidden
            else VideoUiWorkPermission.Visible
    }

    suspend fun <T> runRequest(work: suspend () -> T): T = runVisibleVideoUiWork(permission, work)
}
