package com.android.purebilibili.feature.video.screen

internal enum class DetailRelatedVisibility {
    WAITING_FOR_PREFERENCE,
    EMPTY,
    VISIBLE,
}

// DataStore 尚未读出偏好时不要先画骨架或卡片，避免关闭该功能的用户看到一闪而过的推荐区。
internal fun resolveDetailRelatedVisibility(showPreference: Boolean?): DetailRelatedVisibility =
    when (showPreference) {
        null -> DetailRelatedVisibility.WAITING_FOR_PREFERENCE
        false -> DetailRelatedVisibility.EMPTY
        true -> DetailRelatedVisibility.VISIBLE
    }
