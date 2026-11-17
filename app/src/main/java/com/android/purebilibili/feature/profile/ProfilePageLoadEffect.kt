package com.android.purebilibili.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** 页面重挂载保留账号刷新消费记录；失活期间的新记录在再次活跃时处理。 */
@Composable
internal fun ProfilePageLoadEffect(
    isActive: Boolean,
    accountSessionRefreshGeneration: Int,
    onLoad: (force: Boolean) -> Unit,
) {
    var handledGeneration by rememberSaveable { mutableIntStateOf(0) }
    val latestLoad by rememberUpdatedState(onLoad)
    LaunchedEffect(isActive, accountSessionRefreshGeneration) {
        if (isActive) {
            val force = shouldForceProfileLoadForAccountSessionRefresh(
                isCurrentPage = true,
                accountSessionRefreshGeneration = accountSessionRefreshGeneration,
                handledAccountSessionRefreshGeneration = handledGeneration,
            )
            latestLoad(force)
            if (force) handledGeneration = accountSessionRefreshGeneration
        }
    }
}
