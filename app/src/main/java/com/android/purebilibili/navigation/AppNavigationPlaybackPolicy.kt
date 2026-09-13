package com.android.purebilibili.navigation

import androidx.lifecycle.Lifecycle
import com.android.purebilibili.navigation3.BiliPaiNavKey

internal fun shouldStopPlaybackEagerlyOnVideoRouteExit(
    fromRoute: String?,
    toRoute: String?
): Boolean {
    if (toRoute.isNullOrBlank()) return false
    val toRouteBase = toRoute.substringBefore("?")
    return isVideoDetailRoute(fromRoute) &&
        !isVideoDetailRoute(toRouteBase) &&
        toRouteBase != ScreenRoutes.AudioMode.route &&
        !toRouteBase.startsWith("space/")
}

internal fun shouldDeferBottomBarRevealOnVideoReturn(
    isReturningFromDetail: Boolean,
    activeBottomTabRoute: String?,
    cardTransitionEnabled: Boolean
): Boolean {
    return false
}

internal fun shouldDelayBottomBarRevealAfterVideoReturn(
    isReturningFromDetail: Boolean,
    isBottomBarDestination: Boolean,
    cardTransitionEnabled: Boolean
): Boolean {
    return isReturningFromDetail &&
        isBottomBarDestination &&
        cardTransitionEnabled
}

internal fun resolveVideoReturnBottomBarRevealDelayMs(
    cardTransitionEnabled: Boolean,
    isQuickReturnFromDetail: Boolean
): Long {
    if (!cardTransitionEnabled) return 0L
    return if (isQuickReturnFromDetail) 120L else 160L
}

internal fun shouldClearReturningStateWhenDisposingVideoDestination(
    stillInVideoRoute: Boolean
): Boolean {
    return stillInVideoRoute
}

internal fun isVideoCardReturnTargetRoute(route: String?): Boolean {
    val routeBase = route?.substringBefore("?") ?: return false
    return isVideoDetailRoute(routeBase) ||
        routeBase == "main_host" ||
        routeBase == ScreenRoutes.Home.route ||
        routeBase == ScreenRoutes.ListenVideo.route ||
        routeBase == ScreenRoutes.History.route ||
        routeBase == ScreenRoutes.Favorite.route ||
        routeBase == ScreenRoutes.LikedVideos.route ||
        routeBase == ScreenRoutes.WatchLater.route ||
        routeBase == ScreenRoutes.Search.route ||
        routeBase == ScreenRoutes.Dynamic.route ||
        routeBase.startsWith("dynamic_detail/") ||
        routeBase == ScreenRoutes.Partition.route ||
        routeBase.startsWith("category/") ||
        routeBase.startsWith("season_series_detail/") ||
        routeBase.startsWith("space/")
}

internal fun shouldMarkNavigationLeaveBeforeVideoExit(
    isMiniMode: Boolean
): Boolean = !isMiniMode

internal fun isVideoDetailRoute(route: String?): Boolean {
    return route?.startsWith("${VideoRoute.base}/") == true
}

internal fun resolveAudioNowPlayingBarExpandRoute(
    opensAudioMode: Boolean,
    bvid: String,
    cid: Long,
    coverUrl: String,
): String = if (opensAudioMode) {
    ScreenRoutes.AudioMode.createRoute(bvid = bvid, cid = cid)
} else {
    VideoRoute.createRoute(bvid = bvid, cid = cid, coverUrl = coverUrl)
}

internal fun shouldEnableVideoDetailSharedTransition(
    cardTransitionEnabled: Boolean,
    sourceRoute: String?,
): Boolean {
    return cardTransitionEnabled && !isVideoDetailRoute(sourceRoute?.substringBefore("?"))
}

internal fun shouldShareAudioModeViewModelWithPreviousEntry(
    previousRoute: String?,
    previousLifecycleState: Lifecycle.State?
): Boolean {
    return previousLifecycleState?.isAtLeast(Lifecycle.State.CREATED) == true &&
        isVideoDetailRoute(previousRoute)
}

internal fun shouldNavigateAudioModeBackToCurrentVideo(
    previousVideoBvid: String?,
    currentVideoBvid: String
): Boolean {
    val normalizedCurrentBvid = currentVideoBvid.trim()
    if (normalizedCurrentBvid.isEmpty()) return false
    return previousVideoBvid?.trim() != normalizedCurrentBvid
}

internal data class AudioModeInitialLoadRequest(
    val bvid: String,
    val cid: Long,
    val resumePositionMs: Long
)

internal fun resolveAudioModeInitialLoadRequest(
    key: BiliPaiNavKey.AudioMode,
    hasDisplayState: Boolean
): AudioModeInitialLoadRequest? {
    if (hasDisplayState) return null
    val sourceBvid = key.sourceBvid.trim()
    if (sourceBvid.isEmpty()) return null
    return AudioModeInitialLoadRequest(
        bvid = sourceBvid,
        cid = key.sourceCid.coerceAtLeast(0L),
        resumePositionMs = key.sourceResumePositionMs.coerceAtLeast(0L)
    )
}
