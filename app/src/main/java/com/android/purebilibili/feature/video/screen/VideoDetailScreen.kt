package com.android.purebilibili.feature.video.screen

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoComposerViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoSupplementViewModel

/**
 * Navigation-facing video detail entry point.
 *
 * ViewModel acquisition and the public route contract stay here; screen orchestration lives in
 * [VideoDetailScreenStateHolder], while transition and platform behavior have dedicated owners.
 */
@Composable
fun VideoDetailScreen(
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
    viewModel: VideoPlaybackViewModel = viewModel(),
    engagementViewModel: VideoEngagementViewModel = viewModel(),
    composerViewModel: VideoComposerViewModel = viewModel(),
    supplementViewModel: VideoSupplementViewModel = viewModel(),
    commentViewModel: VideoCommentViewModel = viewModel(),
    onBgmClick: (BgmInfo) -> Unit = {},
) {
    val visualWorkAllowed = com.android.purebilibili.core.ui.LocalVisualWorkAllowed.current &&
        isVisible && !keepLoadedContentForBackPreview
    androidx.compose.runtime.CompositionLocalProvider(
        com.android.purebilibili.core.ui.LocalVisualWorkAllowed provides visualWorkAllowed,
    ) {
        VideoDetailScreenStateHolder(
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
            engagementViewModel = engagementViewModel,
            composerViewModel = composerViewModel,
            supplementViewModel = supplementViewModel,
            commentViewModel = commentViewModel,
            onBgmClick = onBgmClick,
        )
    }
}
