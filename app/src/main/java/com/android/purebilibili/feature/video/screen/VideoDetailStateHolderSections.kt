package com.android.purebilibili.feature.video.screen

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.OrientationEventListener
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.store.AccountSessionStore
import com.android.purebilibili.core.store.FullscreenMode
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.resolveFilledButtonContainerColor
import com.android.purebilibili.core.ui.resolveFilledButtonContentColor
import com.android.purebilibili.core.util.AppDisplayContext
import com.android.purebilibili.core.util.PlayerRotationSensitivity
import com.android.purebilibili.core.util.applyPlayerRequestedOrientation
import com.android.purebilibili.data.model.VideoLoadError
import com.android.purebilibili.data.model.response.BgmInfo
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.video.progress.PbpProgressData
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.ui.components.VideoInlineSubReplyDetailContent
import com.android.purebilibili.feature.video.ui.overlay.resolveLandscapeEndDrawerLayoutPolicy
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.viewmodel.CommentUiState
import com.android.purebilibili.feature.video.viewmodel.SubReplyUiState
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState

/**
 * 拆出视频详情页自动旋转监听、错误态视图、横屏评论侧栏与平板布局分发，
 * 避免 VideoDetailScreenStateHolder 突破 ART 16,384 DEX 指令上限导致 AOT 退化。
 */
@Composable
internal fun VideoDetailPhoneAutoRotateEffects(
    context: Context,
    activity: Activity?,
    autoRotateEnabled: Boolean,
    sensorAutoRotateEnabled: Boolean,
    systemAutoRotateEnabled: Boolean,
    isFullscreenMode: Boolean,
    fullscreenMode: FullscreenMode,
    useTabletLayout: Boolean,
    orientationPolicyDevice: Boolean,
    isOrientationDrivenFullscreen: Boolean,
    manualPortraitHoldActive: Boolean,
    onReleaseManualPortraitHold: () -> Unit,
    isActivityInMultiWindowMode: Boolean,
    isPipMode: Boolean,
    isPortraitFullscreen: Boolean,
    displayContext: AppDisplayContext,
    isFullscreenPlayerLocked: Boolean,
    orientationRequestsAllowed: Boolean,
    latestOrientationRequestsAllowed: State<Boolean>,
    userRequestedFullscreen: Boolean,
    onClearUserRequestedFullscreen: () -> Unit,
    landscapeHoldEnabled: Boolean,
    rotationSensitivity: PlayerRotationSensitivity,
    lastLandscapeAppliedAtMsState: MutableState<Long?>,
    lastPortraitAppliedAtMsState: MutableState<Long?>,
) {
    LaunchedEffect(
        autoRotateEnabled,
        systemAutoRotateEnabled,
        isFullscreenMode,
        orientationPolicyDevice,
        isOrientationDrivenFullscreen,
        fullscreenMode,
        manualPortraitHoldActive,
        isActivityInMultiWindowMode,
        isPipMode,
        isPortraitFullscreen,
        displayContext,
        isFullscreenPlayerLocked,
        orientationRequestsAllowed,
        userRequestedFullscreen,
    ) {
        if (!orientationRequestsAllowed || isFullscreenPlayerLocked) {
            lastLandscapeAppliedAtMsState.value = null
            lastPortraitAppliedAtMsState.value = null
            return@LaunchedEffect
        }
        if (!shouldObservePhoneAutoRotate(
                autoRotateEnabled = sensorAutoRotateEnabled,
                isCompactDevice = orientationPolicyDevice,
                isOrientationDrivenFullscreen = isOrientationDrivenFullscreen,
                fullscreenMode = fullscreenMode,
                manualPortraitHoldActive = manualPortraitHoldActive,
                isInMultiWindowMode = isActivityInMultiWindowMode,
                isInPictureInPictureMode = isPipMode,
                isPortraitFullscreen = isPortraitFullscreen,
                observeWhenAutoRotateDisabled = displayContext.isFoldableCoverWindow,
                isFullscreenMode = isFullscreenMode,
                manualFullscreenRequested = userRequestedFullscreen,
                systemAutoRotateEnabled = systemAutoRotateEnabled,
            )
        ) {
            lastLandscapeAppliedAtMsState.value = null
            lastPortraitAppliedAtMsState.value = null
        }
    }

    DisposableEffect(
        activity,
        autoRotateEnabled,
        systemAutoRotateEnabled,
        isFullscreenMode,
        fullscreenMode,
        useTabletLayout,
        orientationPolicyDevice,
        isOrientationDrivenFullscreen,
        manualPortraitHoldActive,
        isActivityInMultiWindowMode,
        isPipMode,
        isPortraitFullscreen,
        displayContext,
        isFullscreenPlayerLocked,
        orientationRequestsAllowed,
        userRequestedFullscreen,
        landscapeHoldEnabled,
        rotationSensitivity,
    ) {
        val hostActivity = activity
        if (
            !orientationRequestsAllowed || hostActivity == null ||
            isFullscreenPlayerLocked ||
            !shouldObservePhoneAutoRotate(
                autoRotateEnabled = sensorAutoRotateEnabled,
                isCompactDevice = orientationPolicyDevice,
                isOrientationDrivenFullscreen = isOrientationDrivenFullscreen,
                fullscreenMode = fullscreenMode,
                manualPortraitHoldActive = manualPortraitHoldActive,
                isInMultiWindowMode = isActivityInMultiWindowMode,
                isInPictureInPictureMode = isPipMode,
                isPortraitFullscreen = isPortraitFullscreen,
                observeWhenAutoRotateDisabled = displayContext.isFoldableCoverWindow,
                isFullscreenMode = isFullscreenMode,
                manualFullscreenRequested = userRequestedFullscreen,
                systemAutoRotateEnabled = systemAutoRotateEnabled,
            ) ||
            !isOrientationDrivenFullscreen
        ) {
            return@DisposableEffect onDispose {}
        }

        val orientationListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (!latestOrientationRequestsAllowed.value) return
                if (manualPortraitHoldActive) {
                    if (shouldReleasePhoneManualPortraitHold(orientation)) {
                        onReleaseManualPortraitHold()
                    }
                    return
                }
                // 关闭自动切换时仅允许手动全屏跟随左右横屏；旧配置中的横屏状态
                // 不能重新触发横屏请求，与上面的竖屏锁定策略保持一致。
                if (!sensorAutoRotateEnabled && !userRequestedFullscreen) return
                val isCurrentlyLandscape =
                    hostActivity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val targetOrientation = resolvePhoneAutoRotateRequestedOrientation(
                    orientationDegrees = orientation,
                    isCurrentlyLandscape = isCurrentlyLandscape,
                    landscapeHoldEnabled = landscapeHoldEnabled,
                    portraitSnapDegrees = rotationSensitivity.thresholds.portraitSnap,
                    landscapeEnterMinDegrees = rotationSensitivity.thresholds.landscapeEnterMin,
                    landscapeEnterMaxDegrees = rotationSensitivity.thresholds.landscapeEnterMax,
                    landscapeKeepMinDegrees = rotationSensitivity.thresholds.landscapeKeepMin,
                    landscapeKeepMaxDegrees = rotationSensitivity.thresholds.landscapeKeepMax,
                    useExactLandscapeSide = orientationPolicyDevice,
                    // A manual fullscreen press can happen while the device is still upright.
                    // Wait for one physical landscape observation before allowing the sensor
                    // to treat portrait as an explicit rotate-back gesture.
                    allowPortraitTransitions = shouldAllowPhoneSensorPortraitTransition(
                        autoRotateEnabled = sensorAutoRotateEnabled,
                        manualFullscreenRequested = userRequestedFullscreen,
                    ),
                )
                val nowMs = SystemClock.elapsedRealtime()
                val targetToApply = resolvePhoneAutoRotateTargetToApply(
                    candidateOrientation = targetOrientation,
                    lastLandscapeAppliedAtMs = lastLandscapeAppliedAtMsState.value,
                    nowMs = nowMs,
                    lastPortraitAppliedAtMs = lastPortraitAppliedAtMsState.value,
                ) ?: return
                hostActivity.applyPlayerRequestedOrientation(
                    requestedOrientation = targetToApply,
                    displayContext = displayContext,
                )
                if (targetToApply == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                    onClearUserRequestedFullscreen()
                    lastPortraitAppliedAtMsState.value = nowMs
                    lastLandscapeAppliedAtMsState.value = null
                } else if (isLandscapeRequestedOrientation(targetToApply)) {
                    if (shouldReleaseManualFullscreenRequestAfterSensorTarget(
                            manualFullscreenRequested = userRequestedFullscreen,
                            sensorTargetOrientation = targetToApply,
                            autoRotateEnabled = sensorAutoRotateEnabled,
                        )
                    ) {
                        onClearUserRequestedFullscreen()
                    }
                    lastLandscapeAppliedAtMsState.value = nowMs
                    lastPortraitAppliedAtMsState.value = null
                }
            }
        }

        if (orientationListener.canDetectOrientation()) {
            orientationListener.enable()
        }

        onDispose {
            orientationListener.disable()
            // Fullscreen/configuration changes can immediately register a new listener.
            // Keep the settle guard across that handoff; the effect above clears it
            // when orientation observation actually stops.
        }
    }
}

@Composable
internal fun BoxScope.VideoDetailLandscapeCommentPanelHost(
    success: VideoPlaybackUiState.Success,
    playerState: VideoPlayerState,
    commentStateHandle: State<CommentUiState>,
    subReplyStateHandle: State<SubReplyUiState>,
    landscapeCommentListState: LazyListState,
    commentMemberDecorationsEnabled: Boolean,
    commentActions: VideoDetailCommentActions,
    playbackActions: VideoDetailPlaybackActions,
    navigateToUserSpaceFromVideo: (Long) -> Unit,
    openCommentUrl: (String) -> Unit,
    isOnLeft: Boolean,
    closeProgress: MutableFloatState,
    screenWidthDp: Int,
    onDismiss: () -> Unit,
    onSwitchSide: () -> Unit,
) {
    VideoDetailCommentStateScope(commentStateHandle, subReplyStateHandle) { commentState, subReplyState ->
        LandscapeCommentPanel(
            info = success.info,
            listState = landscapeCommentListState,
            replies = commentState.replies,
            replyCount = commentState.replyCount,
            emoteMap = success.emoteMap,
            isRepliesLoading = commentState.isRepliesLoading,
            isRepliesRefreshing = commentState.isRepliesRefreshing,
            repliesError = commentState.repliesError,
            isRepliesEnd = commentState.isRepliesEnd,
            videoTags = success.videoTags,
            paginationPage = commentState.nextPage,
            pinnedReplyIds = commentState.pinnedReplyIds,
            voteCard = commentState.voteCard,
            pageControl = commentState.pageControl,
            sortMode = commentState.sortMode,
            currentMid = commentState.currentMid,
            showUpFlag = commentState.showUpFlag,
            showIdentityDecorations = commentMemberDecorationsEnabled,
            dissolvingIds = commentState.dissolvingIds,
            likedComments = commentState.likedComments,
            onSortModeChange = commentActions.setSortMode,
            onUpClick = navigateToUserSpaceFromVideo,
            onSubReplyClick = commentActions.openSubReply,
            onRootCommentClick = playbackActions.openRootCommentComposer,
            onCommentReplyClick = playbackActions.replyTo,
            onLoadMoreReplies = commentActions.loadComments,
            onRefreshReplies = commentActions.refreshComments,
            onDeleteComment = commentActions.deleteComment,
            onDissolveStart = commentActions.startDissolve,
            onCommentLike = commentActions.likeComment,
            onCommentHate = commentActions.hateComment,
            hatedComments = commentState.hatedComments,
            onCommentUrlClick = openCommentUrl,
            onReportComment = commentActions.reportComment,
            onToggleTopComment = commentActions.toggleTopComment,
            onCheckCommentFraud = commentActions.checkCommentFraud,
            onTimestampClick = { position -> seekPlayerFromUserAction(playerState.player, position) },
            onDismiss = onDismiss,
            onSwitchSide = onSwitchSide,
            isOnLeft = isOnLeft,
            closeProgress = closeProgress,
            drawerWidth = resolveLandscapeEndDrawerLayoutPolicy(screenWidthDp).drawerWidthDp.dp,
            threadContent = if (subReplyState.visible) {
                { onImagePreview ->
                    VideoInlineSubReplyDetailContent(
                        state = subReplyState,
                        commentState = commentState,
                        emoteMap = success.emoteMap,
                        maxTimestampMs = success.videoDurationMs.takeIf { it > 0L },
                        onLoadMore = commentActions.loadMoreSubReplies,
                        onRefresh = commentActions.refreshSubReplies,
                        onSortModeChange = commentActions.setSubReplySortMode,
                        onDismiss = commentActions.closeSubReply,
                        onRootCommentClick = playbackActions.openRootCommentComposer,
                        onTimestampClick = { positionMs ->
                            seekPlayerFromUserAction(playerState.player, positionMs)
                            commentActions.closeSubReply()
                        },
                        onImagePreview = onImagePreview,
                        onReplyClick = playbackActions.replyTo,
                        onConversationClick = commentActions.openSubReplyConversation,
                        onConversationBack = commentActions.closeSubReplyConversation,
                        onDissolveStart = commentActions.startSubDissolve,
                        onDeleteComment = commentActions.deleteSubComment,
                        onCheckCommentFraud = commentActions.checkCommentFraud,
                        onCommentLike = commentActions.likeComment,
                        onCommentHate = commentActions.hateComment,
                        onReportComment = commentActions.reportComment,
                        onToggleTopComment = commentActions.toggleTopComment,
                        onUrlClick = openCommentUrl,
                        showIdentityDecorations = commentMemberDecorationsEnabled,
                        onAvatarClick = { mid ->
                            mid.toLongOrNull()?.let(navigateToUserSpaceFromVideo) ?: Unit
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .align(if (isOnLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight(),
        )
    }
}

@Composable
internal fun VideoDetailTabletLayoutHost(
    shouldAvoidHinge: Boolean,
    playerState: VideoPlayerState,
    uiState: VideoPlaybackUiState,
    detailContentState: VideoPlaybackUiState.Success?,
    commentStateHandle: State<CommentUiState>,
    engagementState: VideoEngagementUiState,
    subReplyStateHandle: State<SubReplyUiState>,
    downloadProgress: State<Float>,
    commentMemberDecorationsEnabled: Boolean,
    playbackActions: VideoDetailPlaybackActions,
    engagementActions: VideoDetailEngagementActions,
    commentActions: VideoDetailCommentActions,
    configuration: Configuration,
    isVerticalVideo: Boolean,
    sleepTimerMinutes: Int?,
    viewPoints: List<ViewPoint>,
    pbpProgressData: PbpProgressData?,
    bvid: String,
    danmakuManager: DanmakuManager,
    coverUrl: String,
    onBack: () -> Unit,
    onUpClick: (Long) -> Unit,
    onNavigateToAudioMode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onPortraitFullscreen: () -> Unit,
    isInPipMode: Boolean,
    onPipClick: () -> Unit,
    isPortraitFullscreen: Boolean,
    onHomeClick: () -> Unit,
    currentCodec: String,
    onCodecChange: (String) -> Unit,
    currentSecondCodec: String,
    onSecondCodecChange: (String) -> Unit,
    currentAudioQuality: Int,
    onAudioQualityChange: (Int) -> Unit,
    transitionEnabled: Boolean,
    onRelatedVideoClick: (String, Bundle?) -> Unit,
    onBgmClick: (BgmInfo) -> Unit,
    showUpBadge: Boolean,
    onSearchKeywordClick: (String) -> Unit,
    onOpenBilibiliLink: ((String) -> Unit)?,
    currentPlayMode: PlayMode,
    forceCoverOnlyOnReturn: Boolean,
    predictiveBackCancelRecoveryGeneration: Int,
    liveSurfaceCardTransitionEnabled: Boolean,
    isPlayerSurfaceReady: Boolean,
    primaryContentReady: Boolean,
    commentsContentReady: Boolean,
    relatedContentReady: Boolean,
    videoAiSummaryEntryEnabled: Boolean,
    videoNoteEnabled: Boolean,
    videoNoteDefaultCollapsed: Boolean,
    externalQueueTitle: String,
    externalQueueContent: (@Composable () -> Unit)?,
    playerContent: (@Composable (Modifier) -> Unit)?,
    transitionChromeAlphaProvider: () -> Float,
    wallpaperCardBackingAlpha: (() -> Float)?,
) {
    if (shouldAvoidHinge) {
        // 半开折痕和展开后的实体铰链都按实际安全区域切分。
        TabletVideoLayout(
            playerState = playerState,
            uiState = uiState,
            detailContentState = detailContentState,
            commentStateHandle = commentStateHandle,
            engagementState = engagementState,
            subReplyStateHandle = subReplyStateHandle,
            downloadProgress = downloadProgress,
            commentMemberDecorationsEnabled = commentMemberDecorationsEnabled,
            playbackActions = playbackActions,
            engagementActions = engagementActions,
            commentActions = commentActions,
            configuration = configuration,
            isVerticalVideo = isVerticalVideo,
            sleepTimerMinutes = sleepTimerMinutes,
            viewPoints = viewPoints,
            pbpProgressData = pbpProgressData,
            bvid = bvid,
            danmakuManager = danmakuManager,
            coverUrl = coverUrl,
            onBack = onBack,
            onUpClick = onUpClick,
            onNavigateToAudioMode = onNavigateToAudioMode,
            onToggleFullscreen = onToggleFullscreen,
            onPortraitFullscreen = onPortraitFullscreen,
            isInPipMode = isInPipMode,
            onPipClick = onPipClick,
            isPortraitFullscreen = isPortraitFullscreen,
            onHomeClick = onHomeClick,
            currentCodec = currentCodec,
            onCodecChange = onCodecChange,
            currentSecondCodec = currentSecondCodec,
            onSecondCodecChange = onSecondCodecChange,
            currentAudioQuality = currentAudioQuality,
            onAudioQualityChange = onAudioQualityChange,
            transitionEnabled = transitionEnabled,
            onRelatedVideoClick = onRelatedVideoClick,
            onBgmClick = onBgmClick,
            showUpBadge = showUpBadge,
            onSearchKeywordClick = onSearchKeywordClick,
            onOpenBilibiliLink = onOpenBilibiliLink,
            currentPlayMode = currentPlayMode,
            onPlayModeClick = { PlaylistManager.togglePlayMode() },
            forceCoverOnlyOnReturn = forceCoverOnlyOnReturn,
            predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
            liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
            isPlayerSurfaceReady = isPlayerSurfaceReady,
            primaryContentReady = primaryContentReady,
            commentsContentReady = commentsContentReady,
            relatedContentReady = relatedContentReady,
            videoAiSummaryEntryEnabled = videoAiSummaryEntryEnabled,
            videoNoteEnabled = videoNoteEnabled,
            videoNoteDefaultCollapsed = videoNoteDefaultCollapsed,
            externalQueueTitle = externalQueueTitle,
            externalQueueContent = externalQueueContent,
            playerContent = playerContent,
            transitionChromeAlphaProvider = transitionChromeAlphaProvider,
            wallpaperCardBackingAlpha = wallpaperCardBackingAlpha,
        )
    } else {
        LargeScreenVideoLayout(
            playerState = playerState,
            uiState = uiState,
            detailContentState = detailContentState,
            commentStateHandle = commentStateHandle,
            engagementState = engagementState,
            subReplyStateHandle = subReplyStateHandle,
            downloadProgress = downloadProgress,
            commentMemberDecorationsEnabled = commentMemberDecorationsEnabled,
            playbackActions = playbackActions,
            engagementActions = engagementActions,
            commentActions = commentActions,
            configuration = configuration,
            isVerticalVideo = isVerticalVideo,
            sleepTimerMinutes = sleepTimerMinutes,
            viewPoints = viewPoints,
            pbpProgressData = pbpProgressData,
            bvid = bvid,
            danmakuManager = danmakuManager,
            coverUrl = coverUrl,
            onBack = onBack,
            onUpClick = onUpClick,
            onBgmClick = onBgmClick,
            onNavigateToAudioMode = onNavigateToAudioMode,
            onToggleFullscreen = onToggleFullscreen,
            onPortraitFullscreen = onPortraitFullscreen,
            isInPipMode = isInPipMode,
            onPipClick = onPipClick,
            isPortraitFullscreen = isPortraitFullscreen,
            onHomeClick = onHomeClick,
            transitionEnabled = transitionEnabled,
            currentCodec = currentCodec,
            onCodecChange = onCodecChange,
            currentSecondCodec = currentSecondCodec,
            onSecondCodecChange = onSecondCodecChange,
            currentAudioQuality = currentAudioQuality,
            onAudioQualityChange = onAudioQualityChange,
            onRelatedVideoClick = onRelatedVideoClick,
            showUpBadge = showUpBadge,
            onSearchKeywordClick = onSearchKeywordClick,
            onOpenBilibiliLink = onOpenBilibiliLink,
            currentPlayMode = currentPlayMode,
            onPlayModeClick = { PlaylistManager.togglePlayMode() },
            forceCoverOnlyOnReturn = forceCoverOnlyOnReturn,
            predictiveBackCancelRecoveryGeneration = predictiveBackCancelRecoveryGeneration,
            liveSurfaceCardTransitionEnabled = liveSurfaceCardTransitionEnabled,
            isPlayerSurfaceReady = isPlayerSurfaceReady,
            primaryContentReady = primaryContentReady,
            commentsContentReady = commentsContentReady,
            relatedContentReady = relatedContentReady,
            videoAiSummaryEntryEnabled = videoAiSummaryEntryEnabled,
            videoNoteEnabled = videoNoteEnabled,
            videoNoteDefaultCollapsed = videoNoteDefaultCollapsed,
            externalQueueTitle = externalQueueTitle,
            externalQueueContent = externalQueueContent,
            playerContent = playerContent,
            transitionChromeAlphaProvider = transitionChromeAlphaProvider,
            wallpaperCardBackingAlpha = wallpaperCardBackingAlpha,
        )
    }
}

@Composable
internal fun VideoDetailErrorContent(
    errorState: VideoPlaybackUiState.Error,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            AppText(
                text = when (errorState.error) {
                    is VideoLoadError.NetworkError -> "📡"
                    is VideoLoadError.VideoNotFound -> "🔍"
                    is VideoLoadError.RegionRestricted -> "🌐"
                    is VideoLoadError.RateLimited -> "⏳"
                    is VideoLoadError.GlobalCooldown -> ""
                    is VideoLoadError.PlayUrlEmpty -> "⚡"
                    else -> ""
                },
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(16.dp))
            AppText(
                text = errorState.msg,
                color = AppContentColors.primaryText(),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )

            when (errorState.error) {
                is VideoLoadError.GlobalCooldown,
                is VideoLoadError.PlayUrlEmpty -> {
                    Spacer(Modifier.height(8.dp))
                    AppText(
                        text = " 建议：切换 WiFi/移动数据 或 清除缓存后重试",
                        color = AppContentColors.secondaryText(),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                is VideoLoadError.RateLimited -> {
                    Spacer(Modifier.height(8.dp))
                    AppText(
                        text = " 该视频可能暂时不可用，请尝试其他视频",
                        color = AppContentColors.secondaryText(),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {}
            }

            val showRetryButton = errorState.canRetry ||
                errorState.error is VideoLoadError.RateLimited ||
                errorState.error is VideoLoadError.PlayUrlEmpty
            if (showRetryButton) {
                Spacer(Modifier.height(24.dp))
                AppButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = resolveFilledButtonContainerColor(MaterialTheme.colorScheme),
                        contentColor = resolveFilledButtonContentColor(MaterialTheme.colorScheme),
                    ),
                ) {
                    AppText(
                        text = when (errorState.error) {
                            is VideoLoadError.RateLimited -> "强制重试"
                            is VideoLoadError.GlobalCooldown -> "清除冷却并重试"
                            else -> "重试"
                        },
                    )
                }
            }

            // 大会员受限时，提示用已保存的大会员账号播放
            val isVipRequiredError =
                errorState.error is VideoLoadError.VipRequired ||
                    (errorState.error as? VideoLoadError.ApiError)?.code == -10403 ||
                    (errorState.error as? VideoLoadError.UnknownError)
                        ?.throwable?.message?.contains("大会员") == true
            if (isVipRequiredError) {
                val vipPlaybackCandidates = remember(errorState.error) {
                    val currentPlaybackMid = NetworkModule.playbackAccount()?.mid
                    AccountSessionStore
                        .getAccounts(context)
                        .filter { account ->
                            account.isVip &&
                                account.sessData.isNotBlank() &&
                                account.mid != currentPlaybackMid
                        }
                }
                val vipCandidate = vipPlaybackCandidates.firstOrNull()
                if (vipCandidate != null) {
                    Spacer(Modifier.height(16.dp))
                    AppButton(
                        onClick = {
                            AccountSessionStore.setPlaybackAccountMid(context, vipCandidate.mid)
                            Toast.makeText(
                                context,
                                "已使用「${vipCandidate.name.ifBlank { "UID ${vipCandidate.mid}" }}」的大会员播放",
                                Toast.LENGTH_SHORT,
                            ).show()
                            onRetry()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                        ),
                    ) {
                        AppText("使用「${vipCandidate.name.ifBlank { "UID ${vipCandidate.mid}" }}」的大会员播放")
                    }
                    Spacer(Modifier.height(8.dp))
                    AppText(
                        text = "可在「我的 - 账号与播放」中更换播放账号",
                        color = AppContentColors.secondaryText(),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
