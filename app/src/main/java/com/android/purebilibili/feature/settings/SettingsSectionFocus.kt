package com.android.purebilibili.feature.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun SettingsSectionFocusEffect(
    listState: LazyListState,
    target: SettingsSearchTarget,
    sectionKeys: List<String>,
    legacyKeys: Map<String, String> = emptyMap(),
) {
    val request by SettingsSearchFocusController.request.collectAsStateWithLifecycle()
    LaunchedEffect(request?.token, target, sectionKeys) {
        val current = request ?: return@LaunchedEffect
        if (current.target != target) return@LaunchedEffect
        val index = resolveSettingsSectionFocusIndex(
            sectionKeys,
            current.settingId,
            fallbackKey = legacyKeys[current.focusId] ?: current.focusId,
        )
        if (index == null) {
            SettingsSearchFocusController.clear(current.token)
            return@LaunchedEffect
        }
        if (current.settingId == null) {
            listState.animateScrollToItem(index)
            SettingsSearchFocusController.clear(current.token)
        } else {
            listState.scrollToItem(index)
            clearUnclaimedSettingsFocusRequest(current.token)
        }
    }
}
