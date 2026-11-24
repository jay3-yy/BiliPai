package com.android.purebilibili.feature.video.ui.overlay.glass

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.playback.audio.resolveAudioQualityControlPresentation
import com.android.purebilibili.feature.video.ui.components.glass.*
import com.android.purebilibili.feature.video.ui.overlay.PlayerProgress
import com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlayActions
import com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlayState
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction

private enum class PortraitControlMenu { SPEED, QUALITY, AUDIO, RATIO, SUBTITLE }

/** 玻璃竖屏流保留独立布局，互动与播放操作继续使用当前内容的状态和回调。 */
@Composable
internal fun PortraitPagerControls(
    state: VideoPlayerOverlayState,
    actions: VideoPlayerOverlayActions,
    info: ViewInfo?,
    stat: Stat,
    progress: PlayerProgress,
    playbackReady: Boolean,
    videoAspectRatio: Float,
    commentExpansionProgress: Float,
    onDetailClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onStaffMemberClick: (Long) -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember(state.bvid, state.cid) { mutableStateOf<PortraitControlMenu?>(null) }
    var speed by remember(state.player) { mutableFloatStateOf(state.player.playbackParameters.speed) }
    DisposableEffect(state.player) {
        val player = state.player
        val listener = object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                speed = playbackParameters.speed
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val latestVisibility by rememberUpdatedState(actions.onFloatingPanelVisibilityChange)
    val anyMenuVisible = menu != null || state.danmakuComposerVisible
    LaunchedEffect(anyMenuVisible) { latestVisibility(anyMenuVisible) }
    DisposableEffect(Unit) { onDispose { latestVisibility(false) } }
    val context = androidx.compose.ui.platform.LocalContext.current
    val showVideoDeclarations by com.android.purebilibili.core.store.SettingsManager
        .getVideoArgueMsgShown(context)
        .collectAsStateWithLifecycle(initialValue = true)
    val audio = resolveAudioQualityControlPresentation(
        selectedAudioQuality = state.selectedAudioQuality,
        options = state.availableAudioQualities,
    )
    PortraitFullscreenOverlay(
        modifier = modifier,
        title = state.title,
        declarations = if (showVideoDeclarations) buildList {
            info?.argueInfo?.argueMsg?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            if (info?.rights?.noReprint == 1) add("未经作者授权，请勿转载")
        }.distinct() else emptyList(),
        ugcSeason = info?.ugc_season,
        currentBvid = state.bvid,
        currentCid = state.cid,
        authorName = state.videoOwnerName,
        authorFace = state.videoOwnerFace,
        staff = info?.staff.orEmpty(),
        ownerMid = info?.owner?.mid ?: 0L,
        onStaffMemberClick = onStaffMemberClick,
        isPlaying = state.player.isPlaying || state.hasPendingSeekResume,
        progress = progress,
        statView = stat.view,
        statLike = state.likeCount.toInt(),
        statCoin = stat.coin,
        statDanmaku = stat.danmaku,
        statReply = stat.reply,
        statFavorite = state.favoriteCount.toInt(),
        statShare = stat.share,
        isLiked = state.isLiked,
        likeStatusKnown = state.likeStatusKnown,
        isCoined = state.isCoined,
        isFavorited = state.isFavorited,
        onLikeClick = actions.onToggleLike,
        onLikeLongClick = actions.onTripleAction,
        onCoinClick = actions.onCoin,
        onFavoriteClick = actions.onToggleFavorite,
        onFavoriteLongClick = actions.onQuickFavorite,
        onCommentClick = actions.onLandscapeCommentClick,
        onShareClick = { actions.onShare?.invoke() },
        isFollowing = state.isFollowed,
        followStatusKnown = state.followStatusKnown,
        onFollowClick = actions.onToggleFollow,
        onDetailClick = onDetailClick,
        onTitleClick = onDetailClick,
        onAuthorClick = onAuthorClick,
        currentSpeed = speed,
        currentQualityLabel = state.currentQualityLabel,
        currentAudioQualityLabel = audio.label,
        isHiResAudioSelected = audio.showHiResBadge,
        isDolbyAudioSelected = audio.showDolbyBadge,
        currentRatio = state.currentAspectRatio,
        danmakuEnabled = state.danmakuEnabled,
        isStatusBarHidden = true,
        showControls = state.isVisible,
        commentExpansionProgress = commentExpansionProgress,
        videoshotData = state.videoshotData,
        videoAspectRatio = videoAspectRatio,
        isPlaybackRecovering = state.hasPendingSeekResume,
        playbackReady = playbackReady,
        onBack = actions.onBack,
        onHomeClick = actions.onHomeClick,
        onPlayPause = { togglePlayerPlaybackFromUserAction(state.player) },
        onSeek = { actions.onSeekTo?.invoke(it) },
        onSeekStart = actions.onSeekStart,
        seekPositionMs = state.progressDisplayOverridePositionMs ?: progress.current,
        isSeekScrubbing = state.isSeekScrubbing,
        onSeekDragStart = actions.onSeekDragStart,
        onSeekDragUpdate = actions.onSeekDragUpdate,
        onSeekDragCancel = actions.onSeekDragCancel,
        onSpeedClick = { menu = PortraitControlMenu.SPEED },
        onQualityClick = { menu = PortraitControlMenu.QUALITY },
        onAudioQualityClick = { menu = PortraitControlMenu.AUDIO },
        onRatioClick = { menu = PortraitControlMenu.RATIO },
        showSubtitleChip = state.subtitleControlState.trackAvailable,
        subtitleEnabled = state.subtitleControlState.enabled,
        onSubtitleClick = { menu = PortraitControlMenu.SUBTITLE },
        onDanmakuToggle = actions.onDanmakuToggle,
        onDanmakuInputClick = actions.onDanmakuInputClick,
        onToggleStatusBar = {},
        onRotateToLandscape = actions.onToggleFullscreen,
        onSearchClick = onSearchClick,
        isAudioOnly = state.isAudioOnly,
        onAudioModeClick = actions.onAudioOnlyToggle,
        onMoreClick = onDetailClick,
    )
    when (menu) {
        PortraitControlMenu.SPEED -> SpeedSelectionMenuDialog(
            currentSpeed = speed,
            onSpeedSelected = { actions.onPlaybackSpeedChange?.invoke(it); menu = null },
            onDismiss = { menu = null },
        )
        PortraitControlMenu.QUALITY -> QualitySelectionMenu(
            qualities = state.qualityLabels,
            qualityIds = state.qualityIds,
            switchableQualityIds = state.switchableQualityIds,
            currentQuality = state.currentQualityLabel,
            isLoggedIn = state.isLoggedIn,
            isVip = state.isVip,
            onQualitySelected = { actions.onQualitySelected(it); menu = null },
            onDismiss = { menu = null },
        )
        PortraitControlMenu.AUDIO -> AudioQualitySelectionMenuDialog(
            options = state.availableAudioQualities,
            requestedAudioQuality = state.currentAudioQuality,
            onAudioQualitySelected = { actions.onAudioQualityChange(it); menu = null },
            onDismiss = { menu = null },
        )
        PortraitControlMenu.RATIO -> AspectRatioMenu(
            currentRatio = state.currentAspectRatio,
            onRatioSelected = { actions.onAspectRatioChange(it); menu = null },
            onDismiss = { menu = null },
        )
        PortraitControlMenu.SUBTITLE -> {
            // 玻璃宿主可用时字幕菜单与同排倍速、画质等菜单共用统一玻璃弹窗；
            // 关闭玻璃或所在窗口无宿主时保持原有深色面板，两条路径互不影响。
            if (com.android.purebilibili.core.ui.components.appGlassMenuAvailable()) {
                PortraitSubtitleGlassMenu(
                    displayMode = state.subtitleControlState.displayMode,
                    primaryLabel = state.subtitleControlState.primaryLabel,
                    secondaryLabel = state.subtitleControlState.secondaryLabel,
                    primaryAvailable = state.subtitleControlState.primaryAvailable,
                    secondaryAvailable = state.subtitleControlState.secondaryAvailable,
                    trackOptions = state.subtitleControlState.trackOptions,
                    largeTextEnabled = state.subtitleControlState.largeTextEnabled,
                    positionLocked = state.subtitleControlState.positionLocked,
                    onDismiss = { menu = null },
                    onDisplayModeChange = { actions.subtitleControlCallbacks.onDisplayModeChange(it) },
                    onTrackSelected = { actions.subtitleControlCallbacks.onTrackSelected(it) },
                    onLargeTextChange = actions.subtitleControlCallbacks.onLargeTextChange,
                    onPositionLockedChange = actions.subtitleControlCallbacks.onPositionLockedChange,
                )
            } else {
                PortraitSubtitlePanel(
                    modifier = Modifier.padding(end = 12.dp, bottom = (
                        com.android.purebilibili.feature.video.ui.overlay.resolvePortraitSubtitleBottomPaddingDp(
                            controlsVisible = state.isVisible && !state.subtitleControlState.positionLocked,
                            commentExpansionProgress = commentExpansionProgress,
                        ) + 8
                    ).dp),
                    displayMode = state.subtitleControlState.displayMode,
                    primaryLabel = state.subtitleControlState.primaryLabel,
                    secondaryLabel = state.subtitleControlState.secondaryLabel,
                    primaryAvailable = state.subtitleControlState.primaryAvailable,
                    secondaryAvailable = state.subtitleControlState.secondaryAvailable,
                    trackOptions = state.subtitleControlState.trackOptions,
                    largeTextEnabled = state.subtitleControlState.largeTextEnabled,
                    positionLocked = state.subtitleControlState.positionLocked,
                    onDismiss = { menu = null },
                    onDisplayModeChange = {
                        actions.subtitleControlCallbacks.onDisplayModeChange(it)
                        menu = null
                    },
                    onTrackSelected = {
                        actions.subtitleControlCallbacks.onTrackSelected(it)
                        menu = null
                    },
                    onLargeTextChange = actions.subtitleControlCallbacks.onLargeTextChange,
                    onPositionLockedChange = actions.subtitleControlCallbacks.onPositionLockedChange,
                )
            }
        }
        null -> Unit
    }
    if (state.danmakuComposerVisible) {
        LandscapeDanmakuComposer(
            visible = true,
            onDismiss = actions.onDismissDanmakuComposer,
            onSend = actions.onSendDanmakuComposer,
            isSending = state.isSendingDanmakuComposer,
            initialText = state.danmakuComposerInitialText,
            initialAttentionCommand = state.danmakuComposerInitialAttentionCommand,
            initialColor = state.danmakuComposerInitialColor,
            initialMode = state.danmakuComposerInitialMode,
            initialFontSize = state.danmakuComposerInitialFontSize,
            onDraftChange = actions.onDanmakuComposerDraftChange,
            onSelectionChange = actions.onDanmakuComposerSelectionChange,
        )
    }
}
