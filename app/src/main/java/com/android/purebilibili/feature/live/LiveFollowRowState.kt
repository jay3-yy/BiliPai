package com.android.purebilibili.feature.live

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.android.purebilibili.core.store.AccountSessionIdentity

/** 横栏位置与已处理的刷新轮次一起保存；重挂载不重复消费旧的刷新完成状态。 */
@Composable
internal fun rememberLiveFollowRowState(
    accountIdentity: AccountSessionIdentity,
    refreshCompletion: Long,
): LazyListState = key(accountIdentity) {
    val state = rememberLazyListState()
    var handledRefreshCompletion by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(refreshCompletion, state) {
        if (refreshCompletion > handledRefreshCompletion) {
            state.scrollToItem(0)
            handledRefreshCompletion = refreshCompletion
        }
    }
    state
}
