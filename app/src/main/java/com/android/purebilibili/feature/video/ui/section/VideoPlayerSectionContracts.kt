package com.android.purebilibili.feature.video.ui.section

import android.os.Bundle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.SponsorProgressMarker
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.model.response.VideoshotData
import com.android.purebilibili.data.model.response.ViewPoint
import com.android.purebilibili.feature.plugin.CdnLineDiagnostic
import com.android.purebilibili.feature.video.player.PlayMode
import com.android.purebilibili.feature.video.progress.PbpProgressData
import com.android.purebilibili.feature.video.state.VideoPlayerState
import com.android.purebilibili.feature.video.subtitle.SubtitleDisplayMode
import com.android.purebilibili.feature.video.viewmodel.SponsorContributionUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState

/**
 * Stable boundary for the player renderer.
 *
 * Keep the public composable parameter list small. Adding another playback or presentation value
 * here must not add another JVM parameter to the generated Compose entry point.
 */
internal data class VideoPlayerSectionState(
    val playerState: VideoPlayerState,
    val uiState: VideoPlaybackUiState,
    val isFullscreen: Boolean = false,
    val isInPipMode: Boolean = false,
    val contentTopInset: Dp = 0.dp,
    val transitionEnabled: Boolean = true,
    val transitionChromeAlphaProvider: () -> Float = { 1f },
    val danmakuHostActive: Boolean = true,
    val suppressTransientOverlaysForTransition: Boolean = false,
    val endDrawerRequestKey: Int = 0,
    val landscapeCommentPanelVisible: Boolean = false,
    val landscapeCommentPanelOnLeft: Boolean = true,
    val landscapeCommentCloseProgressProvider: () -> Float = { 0f },
    val danmakuComposerVisible: Boolean = false,
    val isSendingDanmakuComposer: Boolean = false,
    val danmakuComposerInitialText: String = "",
    val danmakuComposerInitialAttentionCommand: Boolean = false,
    val danmakuComposerInitialColor: Int = 16777215,
    val danmakuComposerInitialMode: Int = 1,
    val danmakuComposerInitialFontSize: Int = 25,
    val bvid: String = "",
    val coverUrl: String = "",
    val stationaryListCoverUrl: String = "",
    val stationaryListCoverCacheKey: String = "",
    val stationaryListCoverDecodeWidthPx: Int = 0,
    val stationaryListCoverDecodeHeightPx: Int = 0,
    val sharedElementBvid: String = "",
    val sponsorSegment: SponsorSegment? = null,
    val showSponsorSkipButton: Boolean = false,
    val sponsorContributionState: SponsorContributionUiState = SponsorContributionUiState(),
    val currentCdnIndex: Int = 0,
    val cdnCount: Int = 1,
    val cdnLineDiagnostics: List<CdnLineDiagnostic> = emptyList(),
    val isCdnProbing: Boolean = false,
    val isAudioOnly: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val videoshotData: VideoshotData? = null,
    val viewPoints: List<ViewPoint> = emptyList(),
    val sponsorMarkers: List<SponsorProgressMarker> = emptyList(),
    val pbpProgressData: PbpProgressData? = null,
    val isVerticalVideo: Boolean = false,
    val isPortraitFullscreen: Boolean = false,
    val viewportWidthDpOverride: Int? = null,
    val currentCodec: String = "hev1",
    val currentSecondCodec: String = "avc1",
    val currentAudioQuality: Int = -1,
    val onlineCount: String = "",
    val currentPlayMode: PlayMode = PlayMode.SEQUENTIAL,
    val relatedVideos: List<RelatedVideo> = emptyList(),
    val ugcSeason: UgcSeason? = null,
    val isFollowed: Boolean = false,
    val isLiked: Boolean = false,
    val isCoined: Boolean = false,
    val isFavorited: Boolean = false,
    val hasFavoritePlaylist: Boolean = false,
    val forceCoverOnly: Boolean = false,
    val preserveCurrentFrameOnFullscreenChange: Boolean = false,
    val liveBackPreview: Boolean = false,
    val useTextureSurfaceForNavigation: Boolean = false,
    val predictiveBackCancelRecoveryGeneration: Int = 0,
    val allowLivePlayerSharedElement: Boolean = true,
    val sourceRouteForSharedElement: String? = null,
    val preserveSourceCardCornerDuringSharedReturn: Boolean = false,
    val suppressSubtitleOverlay: Boolean = false,
    val subtitleDisplayModePreferenceOverride: SubtitleDisplayMode? = null,
)

/**
 * Event boundary for the player renderer. Keep callbacks grouped here so new actions do not grow
 * the generated Compose method signature or mix navigation with rendering state.
 */
internal data class VideoPlayerSectionActions(
    val resolveNextWatch: () -> com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion? = { null },
    val playNextWatch: (com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion) -> Boolean = { false },
    val onToggleFullscreen: () -> Unit,
    val onQualityChange: (Int) -> Unit,
    val onBack: () -> Unit,
    val onHomeClick: (() -> Unit)? = null,
    val onLandscapeCommentClick: () -> Unit = {},
    val onDanmakuInputClick: () -> Unit = {},
    val onDismissDanmakuComposer: () -> Unit = {},
    val onSendDanmakuComposer: (
        message: String,
        color: Int,
        mode: Int,
        fontSize: Int,
        attentionCommand: Boolean,
    ) -> Unit = { _, _, _, _, _ -> },
    val onDanmakuComposerDraftChange: (String, Boolean) -> Unit = { _, _ -> },
    val onDanmakuComposerSelectionChange: (Int, Int, Int) -> Unit = { _, _, _ -> },
    val onDoubleTapLike: () -> Unit = {},
    val onSponsorSkip: () -> Unit = {},
    val onSponsorDismiss: () -> Unit = {},
    val onSponsorVote: (Int) -> Unit = {},
    val onSponsorContributionMarkBoundary: () -> Unit = {},
    val onSponsorContributionMarkWholeVideo: () -> Unit = {},
    val onSponsorContributionCategoryChange: (String) -> Unit = {},
    val onSponsorContributionActionTypeChange: (String) -> Unit = {},
    val onSponsorContributionSubmit: () -> Unit = {},
    val onSponsorContributionCancel: () -> Unit = {},
    val onReloadVideo: () -> Unit = {},
    val onSwitchCdn: () -> Unit = {},
    val onSwitchCdnTo: (Int) -> Unit = {},
    val onProbeCdnCandidates: () -> Unit = {},
    val onAudioOnlyToggle: () -> Unit = {},
    val onSleepTimerChange: (Int?) -> Unit = {},
    val onUserSeek: (Long) -> Unit = {},
    val onPortraitFullscreen: () -> Unit = {},
    val onPipClick: () -> Unit = {},
    val onCodecChange: (String) -> Unit = {},
    val onSecondCodecChange: (String) -> Unit = {},
    val onAudioQualityChange: (Int) -> Unit = {},
    val onPlaybackSpeedChange: (Float) -> Boolean = { false },
    val onAudioLangChange: (String) -> Unit = {},
    val onSaveCover: () -> Unit = {},
    val onDownloadAudio: () -> Unit = {},
    val onPlayModeClick: () -> Unit = {},
    val onRelatedVideoClick: (String, Bundle?) -> Unit = { _, _ -> },
    val onToggleFollow: () -> Unit = {},
    val onToggleLike: () -> Unit = {},
    val onDislike: () -> Unit = {},
    val onCoin: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
    val onTriple: () -> Unit = {},
    val onPageSelect: (Int) -> Unit = {},
    val onFavoritePlaylistClick: () -> Unit = {},
    val onSubtitleDisplayModePreferenceOverrideChange: (SubtitleDisplayMode) -> Unit = {},
    val onSubtitleTrackSelected: (String) -> Unit = {},
    val onLikeDanmaku: (Long) -> Unit = {},
    val onLikeDanmakuToggle: (dmid: Long, like: Boolean) -> Unit = { _, _ -> },
    val likedDanmakuIds: kotlinx.coroutines.flow.StateFlow<Set<Long>> =
        kotlinx.coroutines.flow.MutableStateFlow(emptySet()),
    val onReportDanmaku: (dmid: Long, reason: Int) -> Unit = { _, _ -> },
    val onRecallDanmaku: (Long) -> Unit = {},
)
