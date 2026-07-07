package com.android.purebilibili.feature.settings

import androidx.compose.runtime.Composable
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

/**
 * Settings 内局部返回（overlay / drill-down / 搜索态）使用 [NavigationBackHandler]，
 * 保留系统预测性返回手势预览。
 */
@Composable
internal fun SettingsLocalBackHandler(
    enabled: Boolean = true,
    onBackCompleted: () -> Unit,
) {
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = enabled,
        onBackCompleted = onBackCompleted,
    )
}
