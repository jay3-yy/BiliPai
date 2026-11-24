package com.android.purebilibili.feature.audio.screen

internal fun isAudioNowPlayingPlayerDestination(route: String?): Boolean {
    val routeBase = route?.substringBefore("?") ?: return false
    return routeBase.startsWith("video/") ||
        routeBase.startsWith("bangumi/play") ||
        routeBase == "live" ||
        routeBase.startsWith("live/") ||
        routeBase == "story" ||
        routeBase.startsWith("offline") ||
        routeBase.startsWith("native_music")
}

/** 自带底部评论输入条、聊天输入框或底部固定操作栏的独立页，独立悬浮听视频横条应避让隐藏。 */
internal fun hasAudioNowPlayingConflictingBottomBarDestination(route: String?): Boolean {
    val routeBase = route?.substringBefore("?") ?: return false
    return routeBase.startsWith("dynamic_detail/") ||
        routeBase.startsWith("article/") ||
        routeBase.startsWith("comment_detail/") ||
        routeBase.startsWith("chat/") ||
        routeBase.startsWith("bangumi/")
}

internal fun resolveAudioNowPlayingVisible(
    sessionActive: Boolean,
    isOnAudioModeScreen: Boolean,
    isInPipMode: Boolean,
    hasCurrentItem: Boolean,
    barEnabled: Boolean,
    isInMiniMode: Boolean = false,
    isVideoDetailDestination: Boolean = false,
    isLandscape: Boolean = false,
    isPlayerDestination: Boolean = false,
    isTabletLayout: Boolean = false,
    hasOwnBottomBar: Boolean = false,
): Boolean {
    return barEnabled &&
        sessionActive &&
        !isOnAudioModeScreen &&
        !isInPipMode &&
        !isInMiniMode &&
        hasCurrentItem &&
        !isVideoDetailDestination &&
        (!isLandscape || isTabletLayout) &&
        !isPlayerDestination &&
        !hasOwnBottomBar
}
