package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.store.SettingsManager

internal enum class DetailRelatedVisibility {
    WAITING_FOR_PREFERENCE,
    EMPTY,
    VISIBLE,
}

internal fun DetailRelatedVisibility.isVisible(): Boolean =
    this == DetailRelatedVisibility.VISIBLE

// DataStore 尚未读出偏好时不要先画骨架或卡片，避免关闭该功能的用户看到一闪而过的推荐区。
internal fun resolveDetailRelatedVisibility(showPreference: Boolean?): DetailRelatedVisibility =
    when (showPreference) {
        null -> DetailRelatedVisibility.WAITING_FOR_PREFERENCE
        false -> DetailRelatedVisibility.EMPTY
        true -> DetailRelatedVisibility.VISIBLE
    }

@Composable
internal fun rememberDetailRelatedVisibility(): DetailRelatedVisibility {
    val context = LocalContext.current
    val showDetailRelatedVideos by remember(context) { SettingsManager.getShowDetailRelatedVideos(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    return resolveDetailRelatedVisibility(showDetailRelatedVideos)
}
