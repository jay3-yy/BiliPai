package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.feature.video.player.ExternalPlaylistSource
import kotlin.math.roundToInt

internal fun hasMeaningfulVideoPlayerBoundsChange(
    oldBounds: android.graphics.Rect?,
    newBounds: android.graphics.Rect?,
    tolerancePx: Int = 3,
): Boolean = when {
    oldBounds == null && newBounds == null -> false
    oldBounds == null || newBounds == null -> true
    else -> {
        kotlin.math.abs(oldBounds.left - newBounds.left) > tolerancePx ||
            kotlin.math.abs(oldBounds.top - newBounds.top) > tolerancePx ||
            kotlin.math.abs(oldBounds.right - newBounds.right) > tolerancePx ||
            kotlin.math.abs(oldBounds.bottom - newBounds.bottom) > tolerancePx
    }
}

internal fun shouldHandleVideoDetailDisposeAsNavigationExit(
    isNavigatingToAudioMode: Boolean,
    isNavigatingToMiniMode: Boolean,
    isMiniModeActive: Boolean,
    isChangingConfigurations: Boolean,
    isNavigatingToVideo: Boolean
): Boolean {
    return !isNavigatingToAudioMode &&
        !isNavigatingToMiniMode &&
        !isMiniModeActive &&
        !isChangingConfigurations &&
        !isNavigatingToVideo
}

internal fun shouldRestoreVideoDetailOrientationOnDispose(
    isNavigatingToAudioMode: Boolean,
    isChangingConfigurations: Boolean,
    isNavigatingToVideo: Boolean,
): Boolean = !isNavigatingToAudioMode && !isChangingConfigurations && !isNavigatingToVideo

internal fun resolveIsNavigatingToVideoDuringDispose(
    localNavigatingToVideo: Boolean,
    managerNavigatingToVideo: Boolean
): Boolean {
    return localNavigatingToVideo || managerNavigatingToVideo
}

internal fun shouldMarkReturningStateOnVideoDetailDispose(
    shouldHandleAsNavigationExit: Boolean
): Boolean {
    return shouldHandleAsNavigationExit
}

internal fun shouldClearStaleReturningStateOnVideoDetailEnter(
    isReturningFromDetail: Boolean
): Boolean {
    return isReturningFromDetail
}


internal fun shouldShowExternalPlaylistQueueBarByPolicy(
    isExternalPlaylist: Boolean,
    externalPlaylistSource: ExternalPlaylistSource,
    playlistSize: Int
): Boolean {
    val sourceCanShowQueue = when (externalPlaylistSource) {
        ExternalPlaylistSource.WATCH_LATER,
        ExternalPlaylistSource.FAVORITE,
        ExternalPlaylistSource.SPACE -> true
        ExternalPlaylistSource.NONE,
        ExternalPlaylistSource.UNKNOWN -> false
    }
    return isExternalPlaylist &&
        sourceCanShowQueue &&
        playlistSize > 0
}

internal fun shouldShowExternalPlaylistQueueBarOnContentTab(
    queueAvailable: Boolean,
    selectedTabIndex: Int
): Boolean {
    return queueAvailable && selectedTabIndex != VIDEO_CONTENT_COMMENT_TAB_INDEX
}

internal fun resolveExternalPlaylistQueueTitle(
    externalPlaylistSource: ExternalPlaylistSource
): String {
    return when (externalPlaylistSource) {
        ExternalPlaylistSource.WATCH_LATER -> "稍后再看"
        ExternalPlaylistSource.FAVORITE -> "收藏夹"
        ExternalPlaylistSource.SPACE -> "UP主视频"
        ExternalPlaylistSource.NONE,
        ExternalPlaylistSource.UNKNOWN -> "播放队列"
    }
}

internal fun normalizePlaylistCoverUrlForUi(rawUrl: String?): String {
    val url = rawUrl?.trim().orEmpty()
    if (url.isBlank()) return ""
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://", ignoreCase = true) -> "https://${url.substring(7)}"
        else -> url
    }
}

internal fun resolveExternalPlaylistQueueListMaxHeightDp(screenHeightDp: Int): Int {
    val dynamicHeight = (screenHeightDp * 0.72f).roundToInt()
    return dynamicHeight.coerceIn(420, 680)
}

internal fun resolveExternalPlaylistQueueBottomSpacerDp(navigationBarBottomDp: Int): Int {
    return (navigationBarBottomDp + 8).coerceAtLeast(8)
}

internal enum class ExternalPlaylistQueueSheetPresentation {
    INLINE_HAZE,
    MODAL
}

internal fun resolveExternalPlaylistQueueSheetPresentation(
    requireRealtimeHaze: Boolean
): ExternalPlaylistQueueSheetPresentation {
    return if (requireRealtimeHaze) {
        ExternalPlaylistQueueSheetPresentation.INLINE_HAZE
    } else {
        ExternalPlaylistQueueSheetPresentation.MODAL
    }
}

internal fun shouldOpenCommentUrlInApp(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase().orEmpty()
    if (scheme !in setOf("http", "https", "bili", "bilibili")) return false
    val host = uri.host?.lowercase().orEmpty()
    return host.contains("bilibili.com") || host.contains("b23.tv")
}

internal sealed interface CommentUrlNavigationTarget {
    data class Video(val videoId: String) : CommentUrlNavigationTarget
    data class Search(val keyword: String) : CommentUrlNavigationTarget
    data class Space(val mid: Long) : CommentUrlNavigationTarget
}

internal fun resolveCommentUrlNavigationTarget(rawUrl: String): CommentUrlNavigationTarget? {
    val url = rawUrl.trim()
    if (url.isEmpty()) return null
    return when (val target = com.android.purebilibili.core.util.BilibiliNavigationTargetParser.parse(url)) {
        is com.android.purebilibili.core.util.BilibiliNavigationTarget.Video -> {
            target.videoId.trim()
                .takeIf { it.isNotEmpty() }
                ?.let(CommentUrlNavigationTarget::Video)
        }

        is com.android.purebilibili.core.util.BilibiliNavigationTarget.Search -> {
            target.keyword.trim()
                .takeIf { it.isNotEmpty() }
                ?.let(CommentUrlNavigationTarget::Search)
        }

        is com.android.purebilibili.core.util.BilibiliNavigationTarget.Space -> {
            target.mid.takeIf { it > 0L }?.let(CommentUrlNavigationTarget::Space)
        }

        else -> null
    }
}

internal fun resolveDanmakuDialogTopReservePx(
    isLandscape: Boolean,
    isFullscreenMode: Boolean,
    isPortraitFullscreen: Boolean,
    playerBottomPx: Int?,
    fallbackPlayerBottomPx: Int = 0
): Int {
    if (isLandscape || isFullscreenMode || isPortraitFullscreen) return 0
    return (playerBottomPx ?: fallbackPlayerBottomPx).coerceAtLeast(0)
}



internal fun shouldApplyPipParamsUpdate(
    pipModeEnabled: Boolean,
    modeChanged: Boolean,
    boundsChanged: Boolean,
    elapsedSinceLastUpdateMs: Long,
    minUpdateIntervalMs: Long = 400L
): Boolean {
    if (!pipModeEnabled) return false
    if (modeChanged) return true
    if (!boundsChanged) return false
    return elapsedSinceLastUpdateMs >= minUpdateIntervalMs
}

internal fun shouldDismissCommentThreadDetailForPip(
    wasInPipMode: Boolean,
    isInPipMode: Boolean,
    subReplyVisible: Boolean
): Boolean {
    return !wasInPipMode && isInPipMode && subReplyVisible
}

internal fun shouldAutoEnterAudioModeFromRoute(
    startAudioFromRoute: Boolean,
    hasAutoEnteredAudioMode: Boolean,
    isVideoLoadSuccess: Boolean
): Boolean {
    return startAudioFromRoute && !hasAutoEnteredAudioMode && isVideoLoadSuccess
}

/**
 * Whether the detail route should auto-jump into standalone portrait immersive pager.
 *
 * Entry decision tree (phone):
 * 1. **directPortraitEntry**（设置「竖屏直达」）→ standalone（可经详情 morph）
 * 2. **official inline**（默认）→ stay on detail; autoPortrait / initialVertical are soft hints only
 * 3. else if autoPortrait and vertical → standalone (legacy / non-inline surfaces)
 *
 * Note: home always passes autoPortrait=true + initialVertical for known vertical cards so
 * shared-element transition can prefer portrait geometry. That must NOT force standalone.
 */
internal fun shouldAutoEnterPortraitFullscreenFromRoute(
    autoEnterPortraitFromRoute: Boolean,
    startAudioFromRoute: Boolean,
    portraitExperienceEnabled: Boolean,
    useOfficialInlinePortraitDetailExperience: Boolean,
    allowStandalonePortraitAutoEnter: Boolean = true,
    isCurrentRouteVideoLoaded: Boolean,
    isVerticalVideo: Boolean,
    isPortraitFullscreen: Boolean,
    hasAutoEnteredPortraitFromRoute: Boolean,
    initialVerticalFromRoute: Boolean = false,
    directPortraitEntryFromRoute: Boolean = false,
    directPortraitEntryEnabled: Boolean = false,
): Boolean {
    if (
        startAudioFromRoute ||
        !portraitExperienceEnabled ||
        !allowStandalonePortraitAutoEnter ||
        !isCurrentRouteVideoLoaded ||
        isPortraitFullscreen ||
        hasAutoEnteredPortraitFromRoute
    ) {
        return false
    }
    // Route metadata can be missing or its direction preflight can fail. Once the player
    // knows the actual orientation, honor the setting before the inline-detail fallback.
    if (directPortraitEntryFromRoute || directPortraitEntryEnabled) {
        return isVerticalVideo || initialVerticalFromRoute
    }
    // 手机竖视频默认官方内联详情；autoPortrait/initialVertical 只影响过渡/布局，不自动进 pager。
    if (useOfficialInlinePortraitDetailExperience) {
        return false
    }
    if (!autoEnterPortraitFromRoute) {
        return false
    }
    return isVerticalVideo || initialVerticalFromRoute
}

/**
 * Whether presentation should **start already** in standalone portrait fullscreen.
 * Only explicit direct-entry intent does this; soft autoPortrait / initialVertical hints must not.
 */
@Suppress("UNUSED_PARAMETER")
internal fun shouldStartInPortraitFullscreenFromRouteHint(
    autoEnterPortraitFromRoute: Boolean,
    startAudioFromRoute: Boolean,
    initialVerticalFromRoute: Boolean,
    directPortraitEntryFromRoute: Boolean = false,
): Boolean {
    if (startAudioFromRoute) return false
    return directPortraitEntryFromRoute
}

internal fun shouldSyncMainPlayerToInternalBvid(
    isPortraitFullscreen: Boolean,
    routeBvid: String,
    currentBvid: String,
    currentBvidCid: Long,
    loadedBvid: String,
    loadedCid: Long
): Boolean {
    if (isPortraitFullscreen) return false
    if (currentBvid.isBlank()) return false
    if (loadedBvid != currentBvid && currentBvid == routeBvid) return false
    if (loadedBvid != currentBvid) return true
    val targetCid = currentBvidCid.takeIf { it > 0L } ?: return false
    val resolvedLoadedCid = loadedCid.takeIf { it > 0L } ?: return true
    return resolvedLoadedCid != targetCid
}

/**
 * Whether a mismatch between the presentation identity and the freshly loaded player identity is
 * the *expected* result of a player-initiated in-page advance (season/playlist auto-continue).
 * In that case the detail screen must neither reload the old video nor sync presentation:
 * auto-advance keeps the presentation identity stale exactly like route-driven playback does,
 * and syncing mid-playback would restart bvid-keyed UI state (cover reveal, controls) that the
 * screen has no fresh first-frame event to recover from.
 */
internal fun shouldSkipInternalBvidSyncForPlayerInitiatedAdvance(
    loadedBvid: String,
    inPageInitiatedBvid: String?
): Boolean {
    val pendingBvid = inPageInitiatedBvid?.trim().takeUnless { it.isNullOrEmpty() } ?: return false
    return pendingBvid == loadedBvid.trim()
}

internal fun resolveVideoDetailPlaybackTargetCid(
    routeBvid: String,
    routeCid: Long,
    currentBvid: String,
    currentBvidCid: Long
): Long {
    currentBvidCid.takeIf { it > 0L }?.let { return it }
    return routeCid.takeIf {
        it > 0L && currentBvid.trim() == routeBvid.trim()
    } ?: 0L
}

internal fun resolveAutoPlayOverrideForInternalBvidSync(
    forceAutoPlay: Boolean
): Boolean? {
    return if (forceAutoPlay) true else null
}

internal fun shouldSwitchCollectionVideoInsideCurrentDetailPage(
    targetBvid: String,
    currentBvid: String,
    ugcSeason: UgcSeason?
): Boolean {
    val normalizedTargetBvid = targetBvid.trim()
    if (normalizedTargetBvid.isBlank() || normalizedTargetBvid == currentBvid.trim()) {
        return false
    }
    return ugcSeason
        ?.sections
        .orEmpty()
        .flatMap { section -> section.episodes }
        .any { episode -> episode.bvid == normalizedTargetBvid } == true
}

/**
 * Prefer episode cover when switching collection items in-page.
 * Falls back to empty so callers can keep a previous cover if needed.
 */
internal fun resolveUgcSeasonEpisodeCoverUrl(
    ugcSeason: UgcSeason?,
    targetBvid: String,
    targetCid: Long = 0L
): String {
    val normalizedBvid = targetBvid.trim()
    if (normalizedBvid.isBlank()) return ""
    val episodes = ugcSeason
        ?.sections
        .orEmpty()
        .flatMap { section -> section.episodes }
    val matched = if (targetCid > 0L) {
        episodes.firstOrNull { episode ->
            episode.bvid == normalizedBvid && episode.cid == targetCid
        } ?: episodes.firstOrNull { episode -> episode.bvid == normalizedBvid }
    } else {
        episodes.firstOrNull { episode -> episode.bvid == normalizedBvid }
    }
    return matched?.arc?.pic?.trim().orEmpty()
}

internal data class VideoPlayerSectionTarget(
    val bvid: String,
    val entryCoverUrl: String,
    /**
     * Shared-element identity for the player shell. Keep route-entry bvid stable during in-page
     * collection switches so SharedTransition does not rekey the live surface into a black hole.
     */
    val sharedElementBvid: String
)

internal fun resolveVideoPlayerSectionTarget(
    routeBvid: String,
    routeCoverUrl: String,
    currentBvid: String,
    switchedCoverUrl: String = ""
): VideoPlayerSectionTarget {
    val normalizedCurrentBvid = currentBvid.trim()
    val normalizedRouteBvid = routeBvid.trim()
    val resolvedBvid = normalizedCurrentBvid.ifBlank { normalizedRouteBvid }
    val normalizedSwitchedCover = switchedCoverUrl.trim()
    val normalizedRouteCover = routeCoverUrl.trim()
    // 竖屏流滑到非路由片后，必须优先用该片封面；否则横屏过渡会闪路由首个视频封面。
    val resolvedCoverUrl = when {
        resolvedBvid != normalizedRouteBvid && normalizedSwitchedCover.isNotEmpty() ->
            normalizedSwitchedCover
        resolvedBvid == normalizedRouteBvid && normalizedRouteCover.isNotEmpty() ->
            normalizedRouteCover
        normalizedSwitchedCover.isNotEmpty() -> normalizedSwitchedCover
        normalizedRouteCover.isNotEmpty() -> normalizedRouteCover
        else -> ""
    }
    val sharedElementBvid = normalizedRouteBvid.ifBlank { resolvedBvid }
    return VideoPlayerSectionTarget(
        bvid = resolvedBvid,
        entryCoverUrl = resolvedCoverUrl,
        sharedElementBvid = sharedElementBvid
    )
}
