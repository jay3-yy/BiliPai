package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.lifecycle.PendingResource
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.state.shouldReuseMiniPlayerAtEntry
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel

/**
 * Navigation-facing video detail entry point.
 *
 * ViewModel acquisition and the public route contract stay here; screen orchestration lives in
 * [VideoDetailScreenStateHolder], while transition and platform behavior have dedicated owners.
 */
@Composable
internal fun VideoDetailScreen(
    bvid: String,
    cid: Long = 0L,
    coverUrl: String = "",
    title: String = "",
    startInFullscreen: Boolean = false,
    startAudioFromRoute: Boolean = false,
    autoEnterPortraitFromRoute: Boolean = false,
    initialVerticalFromRoute: Boolean = false,
    directPortraitEntryFromRoute: Boolean = false,
    resumePositionMsFromRoute: Long = 0L,
    linkStartPositionMs: Long? = null,
    suppressPortraitAutoEnter: Boolean = false,
    openCommentRootRpidFromRoute: Long = 0L,
    openCommentTargetRpidFromRoute: Long = 0L,
    sourceRouteForSharedElement: String? = null,
    keepLoadedContentForBackPreview: Boolean = false,
    bindLivePlayerForBackPreview: Boolean = keepLoadedContentForBackPreview,
    predictiveBackCancelRecoveryGeneration: Int = 0,
    isReturningFromDetail: Boolean = false,
    isQuickReturningFromDetail: Boolean = false,
    onMarkReturningFromDetail: () -> Unit = {},
    onClearReturningFromDetail: () -> Unit = {},
    transitionEnabled: Boolean = false,
    transitionEnterDurationMillis: Int = 320,
    onBack: () -> Unit,
    onHomeClick: () -> Unit = onBack,
    onNavigateToAudioMode: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onSearchKeywordClick: (String) -> Unit = {},
    onOpenBilibiliLink: ((String) -> Unit)? = null,
    onRedirectToBangumi: ((String) -> Unit)? = onOpenBilibiliLink,
    onVideoClick: (String, android.os.Bundle?) -> Unit,
    onReplaceVideoDetail: (String, Long, String, Long) -> Boolean = { _, _, _, _ -> false },
    onUpClick: (Long) -> Unit = {},
    onUpClickWithVideo: ((Long, String) -> Unit)? = null,
    miniPlayerManager: MiniPlayerManager? = null,
    isInPipMode: Boolean = false,
    isVisible: Boolean = true,
    isPlaybackSessionActive: Boolean = isVisible,
    isOrientationOwner: Boolean = isVisible && !keepLoadedContentForBackPreview,
    onImmersivePlaybackChanged: (Boolean) -> Unit = {},
    onPlaybackIdentityChanged: (String, Long) -> Unit = { _, _ -> },
    onBgmClick: (BgmInfo) -> Unit = {},
    preparedPlayer: PendingResource<ExoPlayer>? = null,
) {
    val context = LocalContext.current
    val reuseAtEntry = remember(context, bvid, cid) {
        val manager = miniPlayerManager ?: MiniPlayerManager.getInstance(context)
        shouldReuseMiniPlayerAtEntry(
            isMiniPlayerActive = manager.isActive,
            miniPlayerBvid = manager.currentBvid,
            miniPlayerCid = manager.currentCid,
            hasMiniPlayerInstance = manager.player != null,
            requestBvid = bvid,
            requestCid = cid,
        )
    }
    val pending = rememberVideoDetailEntryPlayer(
        context = context,
        bvid = bvid,
        cid = cid,
        preparedPlayer = preparedPlayer,
        reuseAtEntry = reuseAtEntry,
        canPrepare = isVisible || isPlaybackSessionActive,
    )
    val horizontalAdaptationEnabled = rememberVideoDetailHorizontalAdaptation(context)
    if (!reuseAtEntry && pending == null) {
        VideoDetailOpeningPreview(
            bvid = bvid,
            coverUrl = coverUrl,
            isVertical = initialVerticalFromRoute,
            fullscreen = startInFullscreen || isInPipMode || (
                !suppressPortraitAutoEnter && shouldStartInPortraitFullscreenFromRouteHint(
                    autoEnterPortraitFromRoute = autoEnterPortraitFromRoute,
                    startAudioFromRoute = startAudioFromRoute,
                    initialVerticalFromRoute = initialVerticalFromRoute,
                    directPortraitEntryFromRoute = directPortraitEntryFromRoute,
                )
            ),
            horizontalAdaptationEnabled = horizontalAdaptationEnabled.value,
        )
        return
    }

    val viewModel: VideoPlaybackViewModel = viewModel()
    val takePreparedPlayer = remember(pending) { pending?.let { it::take } }
    val visualWorkAllowed = LocalVisualWorkAllowed.current && isVisible && !keepLoadedContentForBackPreview
    CompositionLocalProvider(
        LocalVisualWorkAllowed provides visualWorkAllowed,
    ) {
        ProvideVideoUiWork(viewModel) {
            VideoDetailScreenStateHolder(
                takePreparedPlayer = takePreparedPlayer,
                horizontalAdaptationEnabled = horizontalAdaptationEnabled,
                bvid = bvid,
                cid = cid,
                coverUrl = coverUrl,
                title = title,
                startInFullscreen = startInFullscreen,
                startAudioFromRoute = startAudioFromRoute,
                autoEnterPortraitFromRoute = autoEnterPortraitFromRoute,
                initialVerticalFromRoute = initialVerticalFromRoute,
                directPortraitEntryFromRoute = directPortraitEntryFromRoute,
                resumePositionMsFromRoute = resumePositionMsFromRoute,
                linkStartPositionMs = linkStartPositionMs,
                suppressPortraitAutoEnter = suppressPortraitAutoEnter,
                openCommentRootRpidFromRoute = openCommentRootRpidFromRoute,
                openCommentTargetRpidFromRoute = openCommentTargetRpidFromRoute,
                sourceRouteForSharedElement = sourceRouteForSharedElement,
                keepLoadedContentForBackPreview = keepLoadedContentForBackPreview,
                bindLivePlayerForBackPreview = bindLivePlayerForBackPreview,
                predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
                isReturningFromDetail = isReturningFromDetail,
                isQuickReturningFromDetail = isQuickReturningFromDetail,
                onMarkReturningFromDetail = onMarkReturningFromDetail,
                onClearReturningFromDetail = onClearReturningFromDetail,
                transitionEnabled = transitionEnabled,
                transitionEnterDurationMillis = transitionEnterDurationMillis,
                onBack = onBack,
                onHomeClick = onHomeClick,
                onNavigateToAudioMode = onNavigateToAudioMode,
                onNavigateToSearch = onNavigateToSearch,
                onSearchKeywordClick = onSearchKeywordClick,
                onOpenBilibiliLink = onOpenBilibiliLink,
                onRedirectToBangumi = onRedirectToBangumi,
                onVideoClick = onVideoClick,
                onReplaceVideoDetail = onReplaceVideoDetail,
                onUpClick = onUpClick,
                onUpClickWithVideo = onUpClickWithVideo,
                miniPlayerManager = miniPlayerManager,
                isInPipMode = isInPipMode,
                isVisible = isVisible,
                isPlaybackSessionActive = isPlaybackSessionActive,
                isOrientationOwner = isOrientationOwner,
                onImmersivePlaybackChanged = onImmersivePlaybackChanged,
                onPlaybackIdentityChanged = onPlaybackIdentityChanged,
                viewModel = viewModel,
                onBgmClick = onBgmClick,
            )
        }
    }

    // 小窗在后台构造期间变为可复用时，播放器侧优先接管小窗实例。
    if (pending?.isAvailable == true) SideEffect { pending.dispose() }
}
