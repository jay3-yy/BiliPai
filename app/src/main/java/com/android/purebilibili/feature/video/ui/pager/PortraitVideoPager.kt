@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.android.purebilibili.feature.video.ui.pager

import androidx.compose.foundation.layout.navigationBarsPadding
import com.android.purebilibili.core.util.toUserFriendlyMessage

import com.android.purebilibili.feature.download.downloadAudioBackupUrls
import com.android.purebilibili.core.player.withAppVideoDecoderDiagnostics
import com.android.purebilibili.core.player.withAppVideoDecoderPreference

import com.android.purebilibili.feature.video.ui.gesture.detectPlayerTapGestures
import com.android.purebilibili.feature.video.danmaku.resolvePortraitDanmakuUpperRegion
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuLayerLineHeightPx
import com.android.purebilibili.feature.video.danmaku.resolveDanmakuTextSizePx
import com.android.purebilibili.feature.video.ui.section.DanmakuViewportHost

import com.android.purebilibili.data.repository.playableVideoQualityIds
import com.android.purebilibili.data.repository.advertisedVideoQualityIds
import com.android.purebilibili.feature.video.ui.overlay.NextWatchOverlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import com.android.purebilibili.feature.video.screen.VideoDetailDanmakuContextMenu
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.*
import com.android.purebilibili.core.ui.components.AppButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.appScrollBoundaryHaptics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowInsetsControllerCompat
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppWindowSystemUiController
import com.android.purebilibili.feature.video.screen.VideoDetailHiddenSystemBars
import com.android.purebilibili.feature.video.screen.VideoDetailSystemBarsApplySpec
import com.android.purebilibili.feature.video.screen.applyVideoDetailSystemBarsSpec
import com.android.purebilibili.feature.video.screen.findActivity
import com.android.purebilibili.feature.video.playback.session.isInMultiWindowPlaybackMode
import com.android.purebilibili.feature.video.screen.resolveVideoDetailSystemBarsApplySpec
import com.android.purebilibili.feature.video.screen.resolveVideoDetailSystemBarsVisibilityPolicy
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import com.android.purebilibili.feature.video.ui.section.requiresHdrSurfaceOutput
import com.android.purebilibili.feature.video.ui.section.shouldKeepVideoPlaybackAwake
import com.android.purebilibili.feature.video.ui.section.shouldShowLongPressSpeedFeedback
import com.android.purebilibili.feature.video.ui.section.shouldShowLongPressSpeedHintCloseButton
import com.android.purebilibili.feature.video.ui.section.shouldUseTextureSurfaceForFlip
import com.android.purebilibili.feature.video.ui.section.resolveHorizontalSeekDeltaMs
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.android.purebilibili.core.network.NetworkModule
import android.os.SystemClock
import com.android.purebilibili.core.player.HiResCompatibleRenderersFactory
import com.android.purebilibili.core.player.SeekTickHapticPolicy
import com.android.purebilibili.core.ui.AppHapticScene
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.core.util.VideoTrackCapabilities
import com.android.purebilibili.plugin.sdk.PlaybackCdnPlugin
import com.android.purebilibili.feature.plugin.PlaybackCdnPreference
import com.android.purebilibili.core.store.DanmakuSettingsScope
import com.android.purebilibili.core.store.PlaybackCompletionBehavior
import com.android.purebilibili.core.store.PortraitDanmakuDisplayAreaMode
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.TokenManager
import com.android.purebilibili.core.store.player.PlayerSettingsStore
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.repository.VideoRepository
import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.player.PlaylistManager
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.feature.video.danmaku.configureAsPassiveDanmakuOverlay
import com.android.purebilibili.feature.video.danmaku.rememberIsolatedDanmakuManager
import com.android.purebilibili.feature.video.playback.session.PlaybackSeekSessionState
import com.android.purebilibili.feature.video.playback.session.SEEK_PLAYBACK_RECOVERY_DELAY_MS
import com.android.purebilibili.feature.video.playback.session.shouldAttemptPlaybackRecoveryAfterSeek
import com.android.purebilibili.feature.video.playback.session.shouldShowPlaybackRecoveryUiAfterSeek
import com.android.purebilibili.feature.video.playback.session.cancelPlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.session.finishPlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.session.shouldUsePlaybackSeekSessionPosition
import com.android.purebilibili.feature.video.playback.session.startPlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.session.syncPlaybackSeekSession
import com.android.purebilibili.feature.video.playback.session.updatePlaybackSeekInteraction
import com.android.purebilibili.feature.video.playback.audio.AudioQualityOption
import com.android.purebilibili.feature.video.playback.audio.collectAudioStreamCandidates
import com.android.purebilibili.feature.video.playback.audio.resolveRequestedAudioQuality
import com.android.purebilibili.feature.video.ui.overlay.PlayerProgress
import com.android.purebilibili.feature.video.ui.components.UpPreviewSheet
import com.android.purebilibili.feature.video.ui.components.UP_PREVIEW_SHEET_HEIGHT_FRACTION
import com.android.purebilibili.feature.video.ui.components.resolvePortraitOverlaySheetExpansion
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.resolveSafeVideoAspectRatio
import com.android.purebilibili.feature.video.ui.overlay.FullscreenDoubleTapAction
import com.android.purebilibili.feature.video.ui.overlay.PortraitSubtitleHost
import com.android.purebilibili.feature.video.ui.overlay.nextFullscreenSeekFeedbackEvent
import com.android.purebilibili.feature.video.ui.overlay.resolveFullscreenDoubleTapAction
import com.android.purebilibili.feature.video.ui.overlay.resolvePortraitLetterboxBarHeightPx
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import com.android.purebilibili.feature.video.player.resolveHandleAudioFocusByPolicy
import com.android.purebilibili.feature.video.ui.section.FOREGROUND_SURFACE_RECOVERY_DELAY_MS
import com.android.purebilibili.feature.video.ui.section.resolveLongPressPlaybackParameters
import com.android.purebilibili.feature.video.ui.section.rebindPlayerSurfaceIfNeeded
import com.android.purebilibili.feature.video.ui.section.resolveRelativeSeekTargetPosition
import com.android.purebilibili.feature.video.ui.section.shouldKickPlaybackAfterSurfaceRecovery
import com.android.purebilibili.feature.video.viewmodel.PlaybackEndAction
import com.android.purebilibili.feature.video.viewmodel.CommentSubjectKey
import com.android.purebilibili.feature.video.viewmodel.resolveReplyCount
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoComposerViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementUiState
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementEvent
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementSeed
import com.android.purebilibili.feature.video.viewmodel.VideoSubjectSnapshot
import com.android.purebilibili.feature.video.viewmodel.VideoSupplementViewModel
import com.android.purebilibili.feature.video.viewmodel.toEngagementSeed
import com.android.purebilibili.feature.video.viewmodel.toSupplementSeed
import com.android.purebilibili.feature.video.viewmodel.resolvePlaybackCompletionRepeatMode
import com.android.purebilibili.feature.video.viewmodel.resolvePlaybackEndAction
import com.android.purebilibili.danmaku.engine.DanmakuRenderView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel


internal enum class PortraitFavoriteAction {
    ToggleFavorite,
    OpenFavoriteFolders
}

internal enum class PortraitDanmakuSurfaceMode {
    VideoViewport,
    Page,
    FullScreen,
}

private data class PortraitPrefetchWindowState(
    val anchorPage: Int,
    val candidatePage: Int?,
    val warmAnchor: Boolean,
    val pageCount: Int,
)

private data class PortraitPlaybackRecoverySnapshot(
    val bvid: String,
    val cid: Long,
    val positionMs: Long,
    val playWhenReady: Boolean,
)

private data class PortraitPlaybackFailure(
    val generation: Int,
    val snapshot: PortraitPlaybackRecoverySnapshot,
    val message: String,
)

/**
 * 竖屏无缝滑动播放页面 (TikTok Style)
 * 
 * @param initialBvid 初始视频 BVID
 * @param initialInfo 初始视频详情
 * @param recommendations 推荐视频列表
 * @param onBack 返回回调
 * @param onVideoChange 切换视频回调 (当滑动到新视频时通知外部)
 */

@UnstableApi
@Composable
fun PortraitVideoPager(
    initialBvid: String,
    initialInfo: ViewInfo,
    recommendations: List<RelatedVideo>,
    recommendationsManagedExternally: Boolean = false,
    recommendationLoadInProgress: Boolean = false,
    recommendationLoadError: String? = null,
    onLoadMoreRecommendations: () -> Unit = {},
    onlyVerticalRecommendations: Boolean = true,
    isActive: Boolean = true,
    entryTransitionInProgress: Boolean = false,
    ownsSystemBars: Boolean = true,
    onBack: () -> Unit,
    onHomeClick: () -> Unit = onBack,
    onVideoChange: (String) -> Unit,
    /** 播放器准备切换媒体时同步：(bvid, requested/resolved cid, coverUrl)。 */
    onPlaybackIdentityChange: (String, Long, String) -> Unit = { _, _, _ -> },
    viewModel: VideoPlaybackViewModel,
    engagementViewModel: VideoEngagementViewModel,
    sharedPlayer: ExoPlayer? = null,
    useTextureSurfaceForNavigation: Boolean = false,
    isPlayerSurfaceReady: Boolean = true,
    /**
     * 系统小窗（PiP）：竖屏流只保留播放面。窗口由系统接管触摸，装饰层（标题 / 互动 /
     * 进度条 / 下一集提示）在小窗里既不可用也不该跟随缩放，因此整体不合成。
     */
    isInPipMode: Boolean = false,
    initialStartPositionMs: Long = 0L,
    /**
     * 竖屏流进度同步：(bvid, positionMs, cid, coverUrl)。
     * cover 必须是当前页封面，供切回横屏时避免闪成路由首个视频封面。
     */
    onProgressUpdate: (String, Long, Long, String) -> Unit = { _, _, _, _ -> },
    /** 离开竖屏前快照：(bvid, positionMs, cid, coverUrl)。 */
    onExitSnapshot: (String, Long, Long, String) -> Unit = { _, _, _, _ -> },
    onSearchClick: () -> Unit = {},
    onUserClick: (Long) -> Unit,
    onExitFullscreen: () -> Unit,
    menuActions: com.android.purebilibili.feature.video.ui.overlay.PiliPlayerMenuActions =
        com.android.purebilibili.feature.video.ui.overlay.PiliPlayerMenuActions(),
    onOpenBilibiliLink: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val favoriteQuickSaveDefaultFolder by com.android.purebilibili.core.store.FavoriteInteractionSettingsStore
        .getQuickSaveDefaultFolder(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val activity = remember(context) { context.findActivity() }
    val window = activity?.window
    val insetsController = remember(window, view) {
        if (window != null) WindowInsetsControllerCompat(window, view) else null
    }
    // 独立竖屏流自行管理系统栏；详情内的播放器由详情页统一管理。
    DisposableEffect(ownsSystemBars, isActive, window, insetsController) {
        if (!ownsSystemBars || !isActive || window == null || insetsController == null) {
            return@DisposableEffect onDispose {}
        }
        AppWindowSystemUiController.ensureEdgeToEdge(window)
        val snapshot = AppWindowSystemUiController.capture(window)
        val immersiveSpec = resolveVideoDetailSystemBarsApplySpec(
            visibilityPolicy = resolveVideoDetailSystemBarsVisibilityPolicy(
                isFullscreenMode = false,
                hideVideoPageStatusBar = false,
                isInPipMode = false,
                isScreenActive = true,
                isPortraitFullscreen = true
            ),
            useTabletLayout = false,
            isLightBackground = false,
            backgroundColor = ComposeColor.Black.toArgb(),
            transparentColor = ComposeColor.Transparent.toArgb(),
            blackColor = ComposeColor.Black.toArgb(),
            transientBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        )
        applyVideoDetailSystemBarsSpec(window, insetsController, immersiveSpec)
        onDispose {
            applyVideoDetailSystemBarsSpec(
                window,
                insetsController,
                VideoDetailSystemBarsApplySpec(
                    hiddenBars = VideoDetailHiddenSystemBars.NONE,
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
                    statusBarColor = snapshot.statusBarColor,
                    navigationBarColor = snapshot.navigationBarColor,
                    lightStatusBars = snapshot.lightStatusBars,
                    lightNavigationBars = snapshot.lightNavigationBars,
                ),
            )
        }
    }
    val composerViewModel: VideoComposerViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(key = "portrait_composer_$initialBvid")
    val supplementViewModel: VideoSupplementViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(key = "portrait_supplement_$initialBvid")
    val commentViewModel: VideoCommentViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            key = "portrait_comments_$initialBvid"
        )
    val playbackDomainState by viewModel.uiState.collectAsStateWithLifecycle()
    val knownEngagementState by engagementViewModel.uiState.collectAsStateWithLifecycle()
    val subjectSnapshot by viewModel.subjectSnapshot.collectAsStateWithLifecycle()
    val supplementSeed = (playbackDomainState as? VideoPlaybackUiState.Success)?.toSupplementSeed()
    LaunchedEffect(subjectSnapshot) {
        subjectSnapshot?.let(composerViewModel::bindSubject)
    }
    LaunchedEffect(subjectSnapshot, supplementSeed, isActive) {
        val subject = subjectSnapshot ?: return@LaunchedEffect
        val seed = supplementSeed ?: return@LaunchedEffect
        supplementViewModel.bindSubject(subject, seed)
        supplementViewModel.setVisible(isActive)
    }
    val useSharedPlayer = sharedPlayer != null
    val entryStartPositionMs = remember(initialBvid) { initialStartPositionMs.coerceAtLeast(0L) }
    val scope = rememberCoroutineScope()
    val engagementAccount by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val engagementSession = engagementAccount to TokenManager.sessDataCache
    val coinSuccessHaptic = rememberUpdatedState(
        com.android.purebilibili.feature.video.ui.components.rememberVideoActionSuccessHaptic(isActive)
    )
    val engagementOwner = remember(engagementSession, viewModel, engagementViewModel) {
        PortraitVideoEngagementStore(
            parentScope = scope,
            onState = { state -> engagementViewModel.recordExternalVideoEngagement(state, engagementSession) },
            onEvent = { _, event ->
                when (event) {
                    is VideoEngagementEvent.Message -> viewModel.toast(event.text)
                    is VideoEngagementEvent.CoinSucceeded -> {
                        // store 仅转发当前活动视频的事件；账号仍需按消费时身份校验。
                        if (com.android.purebilibili.feature.video.viewmodel.matchesVideoEngagementAccount(event.accountSession)) {
                            coinSuccessHaptic.value()
                        }
                    }
                    is VideoEngagementEvent.OpenFollowGroups -> viewModel.showFollowGroupDialogForUser(event.mid)
                    VideoEngagementEvent.InvalidateFavoriteFolders -> viewModel.invalidateFavoriteFolderCache()
                    is VideoEngagementEvent.LoadVideo -> onVideoChange(event.bvid)
                }
            },
            modelFactory = { VideoEngagementViewModel().apply { initWithContext(context) } },
        )
    }
    DisposableEffect(engagementOwner) { onDispose { engagementOwner.close() } }
    LaunchedEffect(engagementOwner) {
        engagementViewModel.setCoinDialogVisible(false)
        viewModel.dismissFavoriteFolderDialog()
    }
    val latestLoadMoreRecommendations by rememberUpdatedState(onLoadMoreRecommendations)
    val latestOnlyVerticalRecommendations by rememberUpdatedState(onlyVerticalRecommendations)
    val portraitVerticalVideoRatio by remember(context) {
        SettingsManager.getVerticalVideoRatio(context)
    }.collectAsStateWithLifecycle(
        initialValue = SettingsManager.getCurrentVerticalVideoRatio()
    )
    val latestVerticalVideoRatio by rememberUpdatedState(portraitVerticalVideoRatio)
    val danmakuManager = rememberIsolatedDanmakuManager(
        sessionKey = "portrait_danmaku_$initialBvid"
    )
    val danmakuScope = DanmakuSettingsScope.LANDSCAPE
    val selectedDanmakuMenu by viewModel.danmakuMenuState.collectAsStateWithLifecycle()
    LaunchedEffect(danmakuManager, selectedDanmakuMenu.visible) {
        if (!selectedDanmakuMenu.visible) danmakuManager.clearTapSelection()
    }
    LaunchedEffect(danmakuManager, selectedDanmakuMenu, danmakuManager.isTapSelectionActive, danmakuManager.tapInteractionEnabled) {
        if (selectedDanmakuMenu.visible &&
            selectedDanmakuMenu.tapAnchor?.owner === danmakuManager.tapSelectionOwner
        ) {
            if (!danmakuManager.tapInteractionEnabled) viewModel.hideDanmakuMenu()
            else if (selectedDanmakuMenu.expandedActions) danmakuManager.clearTapSelection()
            else if (!danmakuManager.isTapSelectionActive) viewModel.hideDanmakuMenu()
        }
    }
    DisposableEffect(danmakuManager) {
        onDispose { danmakuManager.clearTapSelection() }
    }
    DisposableEffect(danmakuManager, viewModel, isActive) {
        if (isActive) {
            danmakuManager.setOnDanmakuClickListener { text, dmid, userHash, isSelf, anchor ->
                viewModel.showDanmakuMenu(dmid, text, userHash, isSelf, anchor)
            }
        }
        onDispose { danmakuManager.setOnDanmakuClickListener(null) }
    }
    // 详情页内的竖屏播放器复用外层菜单；独立竖屏视频页在这里承载菜单。
    if (!useSharedPlayer) {
        val blockRulesRaw by remember(context, danmakuScope) {
            SettingsManager.getDanmakuBlockRulesRaw(context, danmakuScope)
        }.collectAsStateWithLifecycle(initialValue = "")
        VideoDetailDanmakuContextMenu(
            context = context,
            viewModel = viewModel,
            activeDanmakuBlockRulesRaw = blockRulesRaw,
            activeDanmakuScope = danmakuScope,
            sortPreferenceScope = scope
        )
    }

    val loadedDanmakuSettings by produceState<DanmakuSettings?>(
        initialValue = null,
        context,
        danmakuScope
    ) {
        SettingsManager.getDanmakuSettings(context, danmakuScope).collect { value = it }
    }
    val danmakuSettingsLoaded = loadedDanmakuSettings != null
    val danmakuSettings = loadedDanmakuSettings ?: DanmakuSettings()
    val danmakuEnabled = danmakuSettingsLoaded && danmakuSettings.enabled
    val danmakuOpacity = danmakuSettings.opacity
    val danmakuFontScale = danmakuSettings.fontScale
    val danmakuSpeed = danmakuSettings.speed
    val danmakuDisplayArea = danmakuSettings.displayArea
    val portraitDanmakuDisplayAreaMode = danmakuSettings.portraitDisplayAreaMode
    val danmakuMergeDuplicates = danmakuSettings.mergeDuplicates
    val danmakuDuplicateMergeWindowMs = danmakuSettings.duplicateMergeWindowMs
    val danmakuDuplicateMergeCountThreshold = danmakuSettings.duplicateMergeCountThreshold
    val danmakuAllowScroll = danmakuSettings.allowScroll
    val danmakuAllowTop = danmakuSettings.allowTop
    val danmakuAllowBottom = danmakuSettings.allowBottom
    val danmakuAllowColorful = danmakuSettings.allowColorful
    val danmakuAllowSpecial = danmakuSettings.allowSpecial
    val danmakuBlockRules = danmakuSettings.blockRules
    val danmakuSmartOcclusion = danmakuSettings.smartOcclusion
    val autoPlayEnabled by remember(context) {
        SettingsManager.getAutoPlay(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val externalPlaylistAutoContinueEnabled by remember(context) {
        SettingsManager.getExternalPlaylistAutoContinue(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val playbackCompletionBehavior by remember(context) {
        SettingsManager.getPlaybackCompletionBehavior(context)
    }.collectAsStateWithLifecycle(initialValue = PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC)
    val isExternalPlaylist by PlaylistManager.isExternalPlaylist.collectAsStateWithLifecycle()
    val prefetchVideoEnabled by remember(context) {
        SettingsManager.getPrefetchVideo(context)
    }.collectAsStateWithLifecycle(initialValue = false)
    val portraitPlayerSettings by remember(context) {
        SettingsManager.getPortraitPlayerSettings(context)
    }.collectAsStateWithLifecycle(
        initialValue = remember(context) {
            SettingsManager.PortraitPlayerSettings(
                longPressSpeedHintCloseEnabled = SettingsManager.getLongPressSpeedHintCloseEnabledSync(context),
                longPressSpeedHintHidden = SettingsManager.getLongPressSpeedHintHiddenSync(context),
                longPressSpeedHintScale = SettingsManager.getLongPressSpeedHintScaleSync(context),
                longPressSpeedHintAlpha = SettingsManager.getLongPressSpeedHintAlphaSync(context),
                letterboxAmbientHazeEnabled = SettingsManager.getPortraitLetterboxAmbientHazeSync(context),
            )
        },
    )
    val portraitWifiAvailable by remember(context) { portraitWifiAvailability(context.applicationContext) }
        .collectAsStateWithLifecycle(initialValue = false)
    // Align portrait pager playurl qn with detail-page playable default (Wi‑Fi / VIP / auto-highest).
    // 默认画质解析含 SP 与 ConnectivityManager 读取,不能在组合期同步执行;
    // 先以 0 占位,首个播放请求由占位守卫挡下,待解析完成后补发。
    var portraitSelectedQuality by remember { mutableIntStateOf(0) }
    var portraitDisplayedQuality by remember { mutableIntStateOf(0) }
    var portraitAvailableQualityIds by remember { mutableStateOf(listOf<Int>()) }
    var portraitSwitchableQualityIds by remember { mutableStateOf(listOf<Int>()) }
    LaunchedEffect(context) {
        val defaultQuality = withContext(Dispatchers.IO) {
            NetworkUtils.getPlayableDefaultQualityId(
                context = context,
                isLoggedIn = VideoRepository.isPlaybackLoggedIn(),
                isVip = VideoRepository.isPlaybackVip()
            )
        }
        if (portraitSelectedQuality <= 0) {
            val resolved = resolvePortraitPlaybackTargetQuality(defaultQuality)
            portraitSelectedQuality = resolved
            if (portraitDisplayedQuality <= 0) {
                portraitDisplayedQuality = resolved
            }
            if (portraitAvailableQualityIds.isEmpty()) {
                portraitAvailableQualityIds = listOf(resolved)
            }
        }
    }
    val portraitQualityLabel = remember(portraitDisplayedQuality) {
        resolvePortraitQualityLabel(portraitDisplayedQuality)
    }
    val portraitInitialRememberedAudioQuality = remember(context) {
        PlayerSettingsStore.getCachedLastSelectedAudioQuality(context)
    }
    val portraitInitialAudioQuality = remember(context, portraitInitialRememberedAudioQuality) {
        resolveRequestedAudioQuality(
            defaultAudioQuality = PlayerSettingsStore.getCachedDefaultAudioQuality(context),
            rememberedAudioQuality = portraitInitialRememberedAudioQuality
        )
    }
    var portraitRememberedAudioQuality by remember {
        mutableIntStateOf(portraitInitialRememberedAudioQuality)
    }
    var portraitRequestedAudioQuality by remember {
        mutableIntStateOf(portraitInitialAudioQuality)
    }
    var portraitSelectedAudioQuality by remember { mutableIntStateOf(-1) }
    var portraitAvailableAudioQualities by remember {
        mutableStateOf<List<AudioQualityOption>>(emptyList())
    }
    var portraitAiAudio by remember {
        mutableStateOf<com.android.purebilibili.data.model.response.AiAudioInfo?>(null)
    }
    var portraitAudioLanguage by remember { mutableStateOf<String?>(null) }
    var portraitAspectRatio by remember { mutableStateOf(VideoAspectRatio.FIT) }
    var portraitAudioOnly by rememberSaveable { mutableStateOf(false) }
    val isPortraitLoggedIn = VideoRepository.isPlaybackLoggedIn()
    val isPortraitVip = VideoRepository.isPlaybackVip()
    val portraitMediaSourceFactory = remember(context) {
        buildPortraitCachedMediaSourceFactory(context)
    }
    val portraitCdnPreferenceValue by remember(context) { SettingsManager.getPlaybackCdnPreference(context) }
        .collectAsStateWithLifecycle(initialValue = PlaybackCdnPreference.DEFAULT.storageValue)
    val portraitCdnPreference = PlaybackCdnPreference.fromStorageValue(portraitCdnPreferenceValue)
    val portraitPlaybackCdnPlugin = remember {
        PluginManager.getEnabledPlugins(PlaybackCdnPlugin::class).firstOrNull()
    }
    val recommendationShuffleSeed = remember(initialInfo.bvid, initialInfo.aid) {
        resolvePortraitRecommendationShuffleSeed(
            initialBvid = initialInfo.bvid,
            initialAid = initialInfo.aid
        )
    }

    val initialPageIndex = remember(initialBvid, initialInfo.bvid, recommendations) {
        resolvePortraitInitialPageIndex(
            initialBvid = initialBvid,
            initialInfoBvid = initialInfo.bvid,
            recommendations = recommendations
        )
    }
    // Stable lists: seed once, then only append (Story load-more / discovery / watch-later).
    // Do not recreate on every parent recommendations update — that resets page order mid-swipe.
    val recommendationItems = remember(initialInfo.bvid) {
        mutableStateListOf<RelatedVideo>()
    }
    val pageItems = remember(initialInfo.bvid) {
        mutableStateListOf<Any>(initialInfo)
    }
    // Immutable per-slot keys for VerticalPager: in-place enrichPortraitPageItemWithLoadedInfo
    // updates cid/bvid without changing the page's key, so PagerState never snaps back to page 0.
    val pageSlotIds = remember(initialInfo.bvid) {
        mutableStateListOf(0L)
    }
    var nextPageSlotId by remember(initialInfo.bvid) { mutableLongStateOf(1L) }
    fun resetPortraitPageItems(newItems: List<Any>) {
        pageItems.clear()
        pageSlotIds.clear()
        newItems.forEachIndexed { idx, entry ->
            pageItems.add(entry)
            pageSlotIds.add(if (idx == 0) 0L else nextPageSlotId++)
        }
    }
    fun appendPortraitPageItems(newItems: List<Any>) {
        newItems.forEach { entry ->
            pageItems.add(entry)
            pageSlotIds.add(nextPageSlotId++)
        }
    }
    fun insertPortraitPageItems(index: Int, newItems: List<Any>) {
        val safeIndex = index.coerceIn(0, pageItems.size)
        val ids = List(newItems.size) { nextPageSlotId++ }
        pageItems.addAll(safeIndex, newItems)
        pageSlotIds.addAll(safeIndex, ids)
    }
    var seededInitialRecommendations by remember(
        initialInfo.bvid,
        onlyVerticalRecommendations,
    ) { mutableStateOf(false) }
    val knownVideoAspectRatios = remember(initialInfo.bvid) {
        mutableStateMapOf<String, Float>().apply {
            resolveAspectRatioFromDimension(initialInfo.dimension)?.let { aspectRatio ->
                put(initialInfo.bvid, aspectRatio)
            }
        }
    }
    var watchLaterVideos by remember { mutableStateOf<List<RelatedVideo>>(emptyList()) }
    var isLoadingMoreRecommendations by remember { mutableStateOf(false) }
    val appendedRecommendationSeeds = remember { mutableStateListOf<String>() }
    var recommendationFeedCursor by rememberSaveable(initialInfo.bvid) { mutableIntStateOf(0) }
    var storyRecommendationDisplayId by rememberSaveable(initialInfo.bvid) { mutableIntStateOf(2) }
    var appliedExternalRecommendations by remember(initialInfo.bvid) {
        mutableStateOf<Pair<List<RelatedVideo>, Boolean>?>(null)
    }
    var emptyExternalRecommendationBatches by remember(initialInfo.bvid) { mutableIntStateOf(0) }
    var lastLoadedCollectionInfo by remember(initialInfo.bvid) {
        mutableStateOf<ViewInfo?>(null)
    }
    var lastLoadedCollectionInfoBvid by remember(initialInfo.bvid) {
        mutableStateOf(initialInfo.bvid)
    }

    LaunchedEffect(
        initialInfo.bvid,
        recommendations,
        onlyVerticalRecommendations,
        portraitVerticalVideoRatio,
        recommendationsManagedExternally
    ) {
        if (!seededInitialRecommendations && onlyVerticalRecommendations && pageItems.size > 1) {
            recommendationItems.clear()
            resetPortraitPageItems(listOf(initialInfo))
        }
        val filteredRecommendations = filterPortraitOnlyVerticalRecommendations(
            recommendations = recommendations,
            enabled = onlyVerticalRecommendations,
            minVerticalRatio = portraitVerticalVideoRatio,
        )
        if (!seededInitialRecommendations) {
            val seeded = if (recommendationsManagedExternally) {
                filteredRecommendations.distinctBy { it.bvid }
            } else withContext(Dispatchers.Default) {
                shufflePortraitRecommendations(
                    seed = recommendationShuffleSeed,
                    recommendations = filteredRecommendations,
                    precedingOwnerMid = initialInfo.owner.mid
                )
            }
            if (pageItems.size <= 1) {
                recommendationItems.clear()
                recommendationItems.addAll(seeded)
                appendPortraitPageItems(seeded)
            } else {
                val existingBvids = snapshotPortraitPageBvids(pageItems)
                val dedupedSeeded = seeded.filter { it.bvid.isNotBlank() && it.bvid !in existingBvids }
                if (dedupedSeeded.isNotEmpty()) {
                    recommendationItems.addAll(dedupedSeeded)
                    appendPortraitPageItems(dedupedSeeded)
                }
            }
            seededInitialRecommendations = true
            if (recommendationsManagedExternally) {
                appliedExternalRecommendations = recommendations to onlyVerticalRecommendations
                emptyExternalRecommendationBatches = if (seeded.isEmpty()) 1 else 0
            }
            return@LaunchedEffect
        }
        val existingBvids = snapshotPortraitPageBvids(pageItems)
        val appendItems = resolvePortraitExternalRecommendationAppendItems(
            currentInitialBvid = initialInfo.bvid,
            existingBvids = existingBvids,
            externalRecommendations = filteredRecommendations
        )
        if (appendItems.isEmpty()) {
            if (recommendationsManagedExternally) {
                appliedExternalRecommendations = recommendations to onlyVerticalRecommendations
                emptyExternalRecommendationBatches += 1
            }
            return@LaunchedEffect
        }
        val lastOwnerMid = pageItems.lastOrNull()?.let(::resolvePortraitPageOwnerMid) ?: 0L
        val shuffledAppend = if (recommendationsManagedExternally) appendItems else withContext(Dispatchers.Default) {
            shufflePortraitRecommendations(
                seed = resolvePortraitRecommendationAppendSeed(
                    baseSeed = recommendationShuffleSeed,
                    currentBvid = initialInfo.bvid
                ),
                recommendations = appendItems,
                precedingOwnerMid = lastOwnerMid
            )
        }
        recommendationItems.addAll(shuffledAppend)
        appendPortraitPageItems(shuffledAppend)
        if (recommendationsManagedExternally) {
            appliedExternalRecommendations = recommendations to onlyVerticalRecommendations
            emptyExternalRecommendationBatches = 0
        }
    }

    LaunchedEffect(Unit) {
        if (TokenManager.sessDataCache.isNullOrEmpty()) {
            watchLaterVideos = emptyList()
            return@LaunchedEffect
        }
        runCatching { NetworkModule.api.getWatchLaterList() }
            .onSuccess { response ->
                if (response.code == 0) {
                    watchLaterVideos = response.data?.list
                        .orEmpty()
                        .mapNotNull(::toRelatedVideoFromWatchLater)
                        .distinctBy { it.bvid }
                }
            }
            .onFailure { error ->
                if (error is CancellationException) throw error
                watchLaterVideos = emptyList()
            }
    }

    LaunchedEffect(watchLaterVideos, onlyVerticalRecommendations, recommendationsManagedExternally) {
        if (recommendationsManagedExternally) return@LaunchedEffect
        if (watchLaterVideos.isEmpty()) return@LaunchedEffect
        val existingBvids = withContext(Dispatchers.Main.immediate) {
            snapshotPortraitPageBvids(pageItems)
        }
        val appendItems = filterPortraitOnlyVerticalRecommendations(
            recommendations = watchLaterVideos.filter { it.bvid !in existingBvids },
            enabled = onlyVerticalRecommendations,
            minVerticalRatio = portraitVerticalVideoRatio,
        )
        if (appendItems.isNotEmpty()) {
            val (tailBvid, tailOwnerMid) = withContext(Dispatchers.Main.immediate) {
                val tail = pageItems.lastOrNull()
                (tail?.let(::resolvePortraitPagePlaybackIdentity)?.bvid.orEmpty()) to
                    (tail?.let(::resolvePortraitPageOwnerMid) ?: 0L)
            }
            val shuffledWatchLater = withContext(Dispatchers.Default) {
                shufflePortraitRecommendations(
                    seed = resolvePortraitRecommendationAppendSeed(
                        baseSeed = recommendationShuffleSeed,
                        currentBvid = tailBvid
                    ),
                    recommendations = appendItems,
                    precedingOwnerMid = tailOwnerMid
                )
            }
            withContext(Dispatchers.Main.immediate) {
                appendPortraitPageItems(shuffledWatchLater)
            }
        }
    }

    val pagerState = rememberPagerState(initialPage = initialPageIndex) {
        pageItems.size
    }
    LaunchedEffect(initialInfo.bvid, onlyVerticalRecommendations, recommendationsManagedExternally) {
        if (recommendationsManagedExternally) return@LaunchedEffect
        val discoveryRecommendations = VideoRepository.getHomeVideos(idx = 0)
            .getOrNull()
            .orEmpty()
            .mapNotNull(::toRelatedVideoForPortraitRecommendation)
        if (discoveryRecommendations.isEmpty()) return@LaunchedEffect

        val filteredDiscoveryRecommendations = filterPortraitOnlyVerticalRecommendations(
            recommendations = discoveryRecommendations,
            enabled = onlyVerticalRecommendations,
            minVerticalRatio = portraitVerticalVideoRatio,
        )
        val (existingBvids, existingRecommendations) = withContext(Dispatchers.Main.immediate) {
            recommendationFeedCursor = 1
            snapshotPortraitPageBvids(pageItems) to recommendationItems.toList()
        }
        val insertion = withContext(Dispatchers.Default) {
            val shuffledDiscoveryRecommendations = shufflePortraitRecommendations(
                seed = resolvePortraitRecommendationAppendSeed(
                    baseSeed = recommendationShuffleSeed,
                    currentBvid = initialInfo.bvid
                ),
                recommendations = filteredDiscoveryRecommendations,
                precedingOwnerMid = initialInfo.owner.mid
            )
            mergePortraitRecommendationAppendItems(
                currentBvid = initialInfo.bvid,
                existingBvids = existingBvids,
                existingRecommendations = existingRecommendations,
                fetchedRecommendations = shuffledDiscoveryRecommendations
            )
        }
        if (insertion.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.Main.immediate) {
            val insertNearHead = pagerState.currentPage == 0 && !pagerState.isScrollInProgress
            if (insertNearHead) {
                recommendationItems.addAll(0, insertion)
                insertPortraitPageItems(1, insertion)
            } else {
                recommendationItems.addAll(insertion)
                appendPortraitPageItems(insertion)
            }
        }
    }
    var currentPageScale by remember { mutableFloatStateOf(1f) }

    // [重构] 优先复用主播放器；仅在未传入 sharedPlayer 时才创建页内播放器
    val exoPlayer = sharedPlayer ?: remember(context) {
        val audioFocusEnabled = SettingsManager.getAudioFocusEnabledSync(context)
        val seekFastEnabled = com.android.purebilibili.core.store.PlayerSettingsCache.isSeekFastEnabled(context)
        val createdPlayer = ExoPlayer.Builder(context)
            // 始终使用 FirstQuarterAwareLoadControl，避免短视频切页时默认预缓冲 50 秒浪费流量与功耗
            .setLoadControl(com.android.purebilibili.feature.video.state.createVideoLoadControl(context))
            .setRenderersFactory(
                HiResCompatibleRenderersFactory(context)
                    .setExtensionRendererMode(
                        androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    )
                    .withAppVideoDecoderPreference(context)
            )
            .setSeekParameters(
                if (seekFastEnabled) {
                    androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC
                } else {
                    androidx.media3.exoplayer.SeekParameters.DEFAULT
                }
            )
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                resolveHandleAudioFocusByPolicy(audioFocusEnabled = audioFocusEnabled)
            )
            .setHandleAudioBecomingNoisy(true)
            .experimentalSetDynamicSchedulingEnabled(true)
            .build().withAppVideoDecoderDiagnostics(context)
            .apply {
                repeatMode = resolvePortraitPagerRepeatMode()
                volume = com.android.purebilibili.core.player.PlayerVolumeController
                    .preferredVolumeSync()
                setPlaybackSpeed(SettingsManager.getPreferredPlaybackSpeedSync(context))
            }
        com.android.purebilibili.core.lifecycle.AbandonableResource(createdPlayer) { it.release() }
    }.value
    LaunchedEffect(exoPlayer, playbackCompletionBehavior) {
        exoPlayer.repeatMode = resolvePlaybackCompletionRepeatMode(playbackCompletionBehavior)
    }
    val portraitPreferredCodec by viewModel.videoCodecPreference.collectAsStateWithLifecycle()
    val portraitSecondPreferredCodec by viewModel.videoSecondCodecPreference.collectAsStateWithLifecycle()
    val wifiPreloadQueue = remember(exoPlayer, portraitMediaSourceFactory, portraitPlaybackCdnPlugin) {
        PortraitWifiPreloadQueue(context.applicationContext, exoPlayer, portraitMediaSourceFactory, portraitPlaybackCdnPlugin)
    }
    var portraitPreloadSpeed by remember(exoPlayer) { mutableFloatStateOf(exoPlayer.playbackParameters.speed) }
    DisposableEffect(exoPlayer, wifiPreloadQueue) {
        val listener = object : Player.Listener {
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                portraitPreloadSpeed = playbackParameters.speed
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            wifiPreloadQueue.close()
        }
    }

    if (!useSharedPlayer) {
        DisposableEffect(exoPlayer) {
            onDispose {
                wifiPreloadQueue.close()
                exoPlayer.release()
            }
        }
    }

    val sharedPlayerHasFrameAtEntry = remember(useSharedPlayer, exoPlayer) {
        useSharedPlayer &&
            exoPlayer.videoSize.width > 0 &&
            exoPlayer.videoSize.height > 0
    }

    val sharedPlayerShouldResumeAtEntry = remember(useSharedPlayer, exoPlayer) {
        useSharedPlayer && (exoPlayer.playWhenReady || exoPlayer.isPlaying)
    }

    // [状态] 当前播放的视频 URL
    var currentPlayingBvid by remember(initialBvid, useSharedPlayer) {
        mutableStateOf(
            resolvePortraitInitialPlayingBvid(
                useSharedPlayer = useSharedPlayer,
                initialBvid = initialBvid
            )
        )
    }
    var currentPlayingCid by remember(initialInfo.cid, useSharedPlayer) {
        mutableLongStateOf(if (useSharedPlayer) initialInfo.cid else 0L)
    }
    var currentPlayingAid by remember(initialInfo.aid, useSharedPlayer) {
        mutableLongStateOf(if (useSharedPlayer) initialInfo.aid else 0L)
    }
    val portraitHdrSurfaceRequired =
        com.android.purebilibili.feature.video.ui.components.rememberVideoSurfaceHdrRequired(
            exoPlayer, portraitSelectedQuality,
        )
    val portraitEnhancement =
        com.android.purebilibili.feature.video.ui.components.rememberPortraitVideoEnhancementSession(
            player = exoPlayer,
            mediaIdentity = currentPlayingBvid to currentPlayingCid,
            requiresHdrSurfaceOutput = portraitHdrSurfaceRequired,
            isInPipMode = isInPipMode,
            isAudioOnly = portraitAudioOnly,
        )
    var portraitCachedDash by remember { mutableStateOf<Dash?>(null) }
    val portraitAvailableCodecIds = remember(portraitCachedDash) {
        com.android.purebilibili.feature.video.ui.overlay.resolvePiliAvailableCodecIds(portraitCachedDash?.video.orEmpty())
    }
    var portraitCurrentVideoUrl by remember { mutableStateOf("") }
    var portraitCurrentAudioUrl by remember { mutableStateOf("") }
    var portraitVideoUrlCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var portraitAudioUrlCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var lastCommittedPage by remember(useSharedPlayer) {
        mutableIntStateOf(if (useSharedPlayer) 0 else -1)
    }
    var activeLoadGeneration by remember { mutableIntStateOf(0) }
    var playbackLoadJob by remember { mutableStateOf<Job?>(null) }
    var requestedPlaybackMediaId by remember { mutableStateOf<String?>(null) }
    var requestedPlaybackSnapshot by remember {
        mutableStateOf<PortraitPlaybackRecoverySnapshot?>(
            if (useSharedPlayer) currentPlayingBvid?.let { bvid ->
                PortraitPlaybackRecoverySnapshot(
                    bvid, currentPlayingCid, exoPlayer.currentPosition.coerceAtLeast(0L), exoPlayer.playWhenReady
                )
            } else null
        )
    }
    var inactivitySnapshot by remember { mutableStateOf<PortraitPlaybackRecoverySnapshot?>(null) }
    var playbackFailure by remember { mutableStateOf<PortraitPlaybackFailure?>(null) }
    var hasAppliedSharedEntryResume by remember { mutableStateOf(false) }
    val pagePlaybackSnapshots = remember(initialBvid) { linkedMapOf<String, Pair<Long, Boolean>>() }
    var hasConsumedInitialSeek by remember(useSharedPlayer) { mutableStateOf(useSharedPlayer) }
    var pendingAutoPlayGeneration by remember { mutableIntStateOf(-1) }
    var renderedFirstFrameGeneration by remember(useSharedPlayer, sharedPlayerHasFrameAtEntry) {
        mutableIntStateOf(
            resolvePortraitInitialRenderedFirstFrameGeneration(
                useSharedPlayer = useSharedPlayer,
                sharedPlayerHasFrameAtEntry = sharedPlayerHasFrameAtEntry
            )
        )
    }
    var lastAutoAdvancedBvid by remember { mutableStateOf<String?>(null) }
    var portraitOverlayVisible by rememberSaveable(initialInfo.bvid) { mutableStateOf(true) }
    var portraitCleanScreen by rememberSaveable(initialInfo.bvid) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var canPlayInVisibleWindow by remember(lifecycleOwner, activity) {
        val currentState = lifecycleOwner.lifecycle.currentState
        mutableStateOf(
            currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                (currentState.isAtLeast(Lifecycle.State.STARTED) && activity.isInMultiWindowPlaybackMode())
        )
    }
    // 「后台播放」豁免：与横屏详情链路（VideoPlayerState.resolvePlaybackPauseDecision）
    // 共用 MiniPlayerManager.shouldContinueBackgroundAudio 的同一套策略，
    // 覆盖后台播放开关、退出即停、PiP 与导航离开等条件。
    var continueInBackgroundAudio by remember(lifecycleOwner) { mutableStateOf(false) }
    val isPortraitPlaybackAllowed = shouldAllowPortraitPlayback(
        isCurrentStoryTab = isActive,
        isLifecycleResumed = canPlayInVisibleWindow,
        continueInBackgroundAudio = continueInBackgroundAudio
    )
    val latestCurrentPlayingBvid by rememberUpdatedState(currentPlayingBvid)
    val latestCurrentPlayingCid by rememberUpdatedState(currentPlayingCid)
    val latestPortraitPlaybackAllowed by rememberUpdatedState(isPortraitPlaybackAllowed)

    fun capturePortraitInactivitySnapshot() {
        if (inactivitySnapshot != null) return
        val bvid = currentPlayingBvid ?: return
        val mediaId = exoPlayer.currentMediaItem?.mediaId
        inactivitySnapshot = if (mediaId == resolvePortraitMediaId(bvid, currentPlayingCid) ||
            (useSharedPlayer && mediaId == bvid)
        ) {
            PortraitPlaybackRecoverySnapshot(
                bvid, currentPlayingCid, exoPlayer.currentPosition.coerceAtLeast(0L), exoPlayer.playWhenReady
            )
        } else requestedPlaybackSnapshot?.takeIf { it.bvid == bvid }
    }

    // 独立竖屏播放器在切到后台继续听音频或纯音频模式时关闭视频轨道硬解，回前台可见窗口或系统画中画时保持视频输出
    val disablePortraitVideoTrack = if (useSharedPlayer) {
        portraitAudioOnly
    } else {
        portraitAudioOnly || (!canPlayInVisibleWindow && continueInBackgroundAudio && !isInPipMode)
    }
    DisposableEffect(exoPlayer, disablePortraitVideoTrack) {
        val videoTrackType = androidx.media3.common.C.TRACK_TYPE_VIDEO
        val wasDisabled = exoPlayer.trackSelectionParameters.disabledTrackTypes.contains(videoTrackType)
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(videoTrackType, disablePortraitVideoTrack)
            .build()
        onDispose {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(videoTrackType, wasDisabled)
                .build()
        }
    }
    // 独立竖屏播放器在后台暂停超过 15 秒后释放解码器与缓冲，回到可见窗口时由 inactivitySnapshot 恢复准备与进度
    LaunchedEffect(exoPlayer, useSharedPlayer, isPortraitPlaybackAllowed, canPlayInVisibleWindow) {
        if (useSharedPlayer || isPortraitPlaybackAllowed || canPlayInVisibleWindow) return@LaunchedEffect
        kotlinx.coroutines.delay(15_000L)
        if (!isPortraitPlaybackAllowed && !canPlayInVisibleWindow) {
            capturePortraitInactivitySnapshot()
            exoPlayer.stop()
        }
    }

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    canPlayInVisibleWindow = true
                    continueInBackgroundAudio = false
                }
                Lifecycle.Event.ON_START -> {
                    if (activity.isInMultiWindowPlaybackMode()) {
                        canPlayInVisibleWindow = true
                        continueInBackgroundAudio = false
                    }
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    if (event == Lifecycle.Event.ON_PAUSE &&
                        activity.isInMultiWindowPlaybackMode()
                    ) return@LifecycleEventObserver
                    val canContinue =
                        MiniPlayerManager.getInstance(context).shouldContinueBackgroundAudio()
                    continueInBackgroundAudio = canContinue
                    canPlayInVisibleWindow = false
                    if (!canContinue) {
                        capturePortraitInactivitySnapshot()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(danmakuManager, exoPlayer) {
        danmakuManager.attachPlayer(exoPlayer)
        onDispose { danmakuManager.detachPlayer(exoPlayer) }
    }

    LaunchedEffect(exoPlayer, isPortraitPlaybackAllowed) {
        if (isPortraitPlaybackAllowed) return@LaunchedEffect
        capturePortraitInactivitySnapshot()
        // 作废仍在请求播放详情/地址的任务，避免页面失活后旧结果重新开启播放。
        if (playbackLoadJob?.isActive == true || (isLoading && pendingAutoPlayGeneration >= 0)) {
            activeLoadGeneration += 1
        }
        playbackLoadJob?.cancel()
        requestedPlaybackMediaId = null
        pendingAutoPlayGeneration = -1
        isLoading = false
        exoPlayer.playWhenReady = false
        exoPlayer.pause()
    }

    DisposableEffect(exoPlayer, activeLoadGeneration, isPortraitPlaybackAllowed) {
        val listenerGeneration = activeLoadGeneration
        val autoPlayListener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (listenerGeneration != activeLoadGeneration || !isPortraitPlaybackAllowed ||
                    reason != Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                ) return
                val snapshot = requestedPlaybackSnapshot ?: return
                if (snapshot.bvid != currentPlayingBvid) return
                requestedPlaybackSnapshot = snapshot.copy(playWhenReady = playWhenReady)
                if (!playWhenReady) pendingAutoPlayGeneration = -1
            }

            override fun onPlayerError(error: PlaybackException) {
                if (listenerGeneration != activeLoadGeneration) return
                val bvid = currentPlayingBvid ?: return
                val mediaId = exoPlayer.currentMediaItem?.mediaId ?: return
                if (mediaId != resolvePortraitMediaId(bvid, currentPlayingCid) && !(useSharedPlayer && mediaId == bvid)) return
                val intent = requestedPlaybackSnapshot?.playWhenReady ?: exoPlayer.playWhenReady
                playbackFailure = PortraitPlaybackFailure(
                    listenerGeneration,
                    PortraitPlaybackRecoverySnapshot(bvid, currentPlayingCid, exoPlayer.currentPosition.coerceAtLeast(0L), intent),
                    "视频播放失败，请重试"
                )
                pendingAutoPlayGeneration = -1
                isLoading = false
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val bvid = latestCurrentPlayingBvid
                    val mediaId = exoPlayer.currentMediaItem?.mediaId
                    val matchingMedia = bvid != null && mediaId != null &&
                        (mediaId == resolvePortraitMediaId(bvid, latestCurrentPlayingCid) ||
                            (useSharedPlayer && mediaId == bvid))
                    if (matchingMedia) {
                        isLoading = false
                    }
                }
                if (playbackState == Player.STATE_READY &&
                    pendingAutoPlayGeneration == activeLoadGeneration &&
                    listenerGeneration == activeLoadGeneration &&
                    requestedPlaybackSnapshot?.playWhenReady == true &&
                    isPortraitPlaybackAllowed &&
                    !exoPlayer.isPlaying
                ) {
                    exoPlayer.playWhenReady = true
                    exoPlayer.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && pendingAutoPlayGeneration == activeLoadGeneration) {
                    pendingAutoPlayGeneration = -1
                }
            }

            override fun onRenderedFirstFrame() {
                if (listenerGeneration != activeLoadGeneration) return
                val bvid = currentPlayingBvid ?: return
                val mediaId = exoPlayer.currentMediaItem?.mediaId ?: return
                val matchingMedia = mediaId == resolvePortraitMediaId(bvid, currentPlayingCid) ||
                    (useSharedPlayer && listenerGeneration == 0 && mediaId == bvid)
                if (!matchingMedia) return
                renderedFirstFrameGeneration = listenerGeneration
                playbackFailure = null
                isLoading = false
            }
        }
        exoPlayer.addListener(autoPlayListener)
        onDispose {
            exoPlayer.removeListener(autoPlayListener)
        }
    }

    LaunchedEffect(useSharedPlayer, sharedPlayerShouldResumeAtEntry, isPortraitPlaybackAllowed) {
        if (useSharedPlayer && sharedPlayerShouldResumeAtEntry && isPortraitPlaybackAllowed &&
            !hasAppliedSharedEntryResume && inactivitySnapshot == null
        ) {
            hasAppliedSharedEntryResume = true
            exoPlayer.playWhenReady = true
            if (!exoPlayer.isPlaying) {
                exoPlayer.play()
            }
        }
    }

    DisposableEffect(
        exoPlayer,
        pagerState,
        pageItems.size,
        currentPlayingBvid,
        autoPlayEnabled,
        externalPlaylistAutoContinueEnabled,
        playbackCompletionBehavior,
        isExternalPlaylist,
        isPortraitPlaybackAllowed
    ) {
        val autoAdvanceListener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) handlePlaybackEnd()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) handlePlaybackEnd()
            }

            private fun handlePlaybackEnd() {
                if (!isPortraitPlaybackAllowed) return
                if (pagerState.isScrollInProgress) {
                    wifiPreloadQueue.clear(force = true)
                    return
                }
                val currentPage = pagerState.currentPage
                val nextItem = pageItems.getOrNull(currentPage + 1)
                val currentItem = pageItems.getOrNull(currentPage)
                val collectionInfo = lastLoadedCollectionInfo
                    ?.takeIf { it.bvid == lastLoadedCollectionInfoBvid }
                val hasCollectionFollowUp = shouldPortraitAutoContinueToNextItem(
                    currentItem = currentItem,
                    nextItem = nextItem,
                    currentLoadedInfo = collectionInfo
                )
                val currentIdentity = currentItem?.let(::resolvePortraitPagePlaybackIdentity)
                val nextIdentity = nextItem?.let(::resolvePortraitPagePlaybackIdentity)
                // Same-video parts are injected into pageItems before unrelated feed entries.
                val hasNextPage = hasCollectionFollowUp &&
                    currentIdentity?.bvid == nextIdentity?.bvid
                val playbackEndAction = resolvePlaybackEndAction(
                    behavior = playbackCompletionBehavior,
                    autoPlayEnabled = autoPlayEnabled,
                    isExternalPlaylist = isExternalPlaylist,
                    externalPlaylistAutoContinueEnabled = externalPlaylistAutoContinueEnabled,
                    hasNextPage = hasNextPage,
                    hasNextSeasonEpisode = hasCollectionFollowUp && !hasNextPage
                )
                when (playbackEndAction) {
                    PlaybackEndAction.STOP -> {
                        wifiPreloadQueue.clear(force = true)
                        return
                    }
                    PlaybackEndAction.REPEAT_CURRENT -> {
                        exoPlayer.seekTo(0)
                        exoPlayer.playWhenReady = true
                        exoPlayer.play()
                    }
                    PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST,
                    PlaybackEndAction.AUTO_CONTINUE -> {
                        val playingBvid = currentPlayingBvid ?: return
                        val advanceKey = "$playingBvid#$currentPlayingCid"
                        if (lastAutoAdvancedBvid == advanceKey) return
                        lastAutoAdvancedBvid = advanceKey

                        val nextPage = resolveNextPortraitPageAfterPlaybackEnd(
                            action = playbackEndAction,
                            currentPage = currentPage,
                            lastPage = pageItems.lastIndex
                        ) ?: return

                        scope.launch {
                            pagerState.animateScrollToPage(nextPage)
                        }
                    }
                    PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST_LOOP -> {
                        val playingBvid = currentPlayingBvid ?: return
                        val advanceKey = "$playingBvid#$currentPlayingCid"
                        if (lastAutoAdvancedBvid == advanceKey) return
                        lastAutoAdvancedBvid = advanceKey

                        val nextPage = resolveNextPortraitPageAfterPlaybackEnd(
                            action = PlaybackEndAction.PLAY_NEXT_IN_PLAYLIST_LOOP,
                            currentPage = pagerState.currentPage,
                            lastPage = pageItems.lastIndex
                        ) ?: return
                        scope.launch {
                            pagerState.animateScrollToPage(nextPage)
                        }
                    }
                }
            }
        }
        exoPlayer.addListener(autoAdvanceListener)
        onDispose {
            exoPlayer.removeListener(autoAdvanceListener)
        }
    }

    fun switchPortraitAudioQuality(audioQuality: Int): Boolean {
        val dash = portraitCachedDash ?: return false
        val videoUrl = portraitCurrentVideoUrl.takeIf { it.isNotBlank() } ?: return false
        val activeBvid = currentPlayingBvid?.takeIf { it.isNotBlank() } ?: return false
        val result = switchPortraitPlaybackAudioSource(
            player = exoPlayer,
            mediaSourceFactory = portraitMediaSourceFactory,
            dash = dash,
            currentVideoUrl = videoUrl,
            requestedAudioQuality = audioQuality,
            targetVideoQuality = resolvePortraitPlaybackTargetQuality(portraitSelectedQuality),
            mediaId = resolvePortraitMediaId(activeBvid, currentPlayingCid),
            cdnPlugin = portraitPlaybackCdnPlugin,
            preference = portraitCdnPreference,
        )
            ?: return false
        portraitCurrentVideoUrl = result.videoUrl
        portraitCurrentAudioUrl = result.audioUrl
        portraitAudioUrlCandidates = result.selection.selected?.track?.let { track ->
            (listOf(track.getValidUrl()) + track.backupUrl.orEmpty()).filter { it.isNotBlank() }.distinct()
        }.orEmpty()
        portraitRequestedAudioQuality = audioQuality
        portraitSelectedAudioQuality = result.selection.selectedPreferenceId
        portraitAvailableAudioQualities = result.selection.availableOptions
        portraitRememberedAudioQuality = audioQuality
        scope.launch {
            SettingsManager.setAudioQuality(context, audioQuality)
        }
        return true
    }

    fun requestPortraitPlaybackForPage(
        targetPage: Int,
        applyInitialSeekOnFirstPage: Boolean,
        forceReload: Boolean = false,
        resumePositionMs: Long? = null,
        playWhenReadyOverride: Boolean? = null,
        useMeasuredCdn: Boolean = true,
    ): Boolean {
        val item = pageItems.getOrNull(targetPage) ?: return false
        val playbackIdentity = resolvePortraitPagePlaybackIdentity(item) ?: return false
        val bvid = playbackIdentity.bvid
        val aid = playbackIdentity.aid
        val requestedCid = playbackIdentity.cid
        val requestedMediaId = resolvePortraitMediaId(bvid, requestedCid)
        val targetCover = when (item) {
            is ViewInfo -> item.pic
            is RelatedVideo -> item.pic
            else -> ""
        }
        val targetQuality = resolvePortraitPlaybackTargetQuality(portraitSelectedQuality)
        val targetAudioQuality = if (forceReload) {
            portraitRequestedAudioQuality
        } else {
            resolveRequestedAudioQuality(
                defaultAudioQuality = PlayerSettingsStore.getCachedDefaultAudioQuality(context),
                rememberedAudioQuality = portraitRememberedAudioQuality
            )
        }
        val targetAudioLanguage = if (forceReload) portraitAudioLanguage else null
        val targetCodec = viewModel.videoCodecPreference.value
        val targetSecondCodec = viewModel.videoSecondCodecPreference.value

        if (!latestPortraitPlaybackAllowed) {
            pendingAutoPlayGeneration = -1
            isLoading = false
            return false
        }

        if (portraitSelectedQuality <= 0) {
            // 默认画质尚未异步解析完成,解析完成后由 PortraitDefaultQualityEffect 补发请求。
            isLoading = false
            return false
        }

        if (!forceReload && requestedPlaybackMediaId == requestedMediaId && playbackLoadJob?.isActive == true) {
            return false
        }
        if (!forceReload && playbackFailure?.snapshot?.let {
                it.bvid == bvid && (requestedCid <= 0L || it.cid == requestedCid)
            } == true
        ) return false

        if (
            !forceReload &&
            shouldSkipPortraitReloadForCurrentMedia(
                currentPlayingBvid = currentPlayingBvid,
                targetBvid = bvid,
                currentPlayerMediaId = exoPlayer.currentMediaItem?.mediaId,
                targetCid = requestedCid,
                currentPlayingCid = currentPlayingCid
            )
        ) {
            isLoading = false
            return false
        }

        currentPlayingBvid?.takeIf { it.isNotBlank() && exoPlayer.currentMediaItem != null }?.let { playingBvid ->
            val mediaId = resolvePortraitMediaId(playingBvid, currentPlayingCid)
            pagePlaybackSnapshots[mediaId] = exoPlayer.currentPosition.coerceAtLeast(0L) to exoPlayer.playWhenReady
            if (pagePlaybackSnapshots.size > 128) {
                pagePlaybackSnapshots.remove(pagePlaybackSnapshots.keys.first())
            }
        }
        val targetSnapshot = pagePlaybackSnapshots[requestedMediaId]
        val resumePlayback = playWhenReadyOverride ?: if (forceReload) exoPlayer.playWhenReady else targetSnapshot?.second ?: true
        val seekResumeMs = resumePositionMs
            ?.coerceAtLeast(0L)
            ?: if (forceReload) exoPlayer.currentPosition.coerceAtLeast(0L) else targetSnapshot?.first
        val plannedPositionMs = seekResumeMs ?: if (applyInitialSeekOnFirstPage && !hasConsumedInitialSeek) {
            entryStartPositionMs
        } else 0L
        val preparedPlayback = if (!forceReload && portraitWifiAvailable && targetAudioLanguage == null) {
            wifiPreloadQueue.claim(
                playbackIdentity, targetQuality, targetAudioQuality, exoPlayer.playbackParameters.speed,
                targetCodec, targetSecondCodec, portraitCdnPreference,
            )
        } else null
        if (preparedPlayback == null) wifiPreloadQueue.clear(force = true)

        activeLoadGeneration += 1
        val requestGeneration = activeLoadGeneration
        playbackLoadJob?.cancel()
        requestedPlaybackMediaId = requestedMediaId
        requestedPlaybackSnapshot = PortraitPlaybackRecoverySnapshot(bvid, requestedCid, plannedPositionMs, resumePlayback)
        playbackFailure = null

        fun failPlayback(message: String) {
            if (!shouldApplyLoadResult(requestGeneration, activeLoadGeneration, bvid, currentPlayingBvid)) return
            playbackFailure = PortraitPlaybackFailure(
                requestGeneration,
                requestedPlaybackSnapshot
                    ?: PortraitPlaybackRecoverySnapshot(bvid, requestedCid, plannedPositionMs, resumePlayback),
                message
            )
            pendingAutoPlayGeneration = -1
            isLoading = false
        }

        if (preparedPlayback == null) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
        danmakuManager.clearForVideoChange()
        isLoading = true
        currentPlayingBvid = bvid
        portraitAvailableQualityIds = emptyList()
        portraitSwitchableQualityIds = emptyList()
        portraitAiAudio = null
        portraitCurrentVideoUrl = ""
        portraitCurrentAudioUrl = ""
        portraitVideoUrlCandidates = emptyList()
        portraitAudioUrlCandidates = emptyList()
        portraitAvailableAudioQualities = emptyList()
        currentPlayingCid = 0L
        currentPlayingAid = 0L
        onPlaybackIdentityChange(bvid, requestedCid, targetCover)
        pendingAutoPlayGeneration = if (resumePlayback) requestGeneration else -1
        renderedFirstFrameGeneration = -1

        playbackLoadJob = scope.launch {
            try {
                val result = preparedPlayback?.let { Result.success(it.info to it.playData) }
                    ?: VideoRepository.getPortraitPlaybackDetails(
                    bvid = bvid,
                    aid = aid,
                    requestedCid = requestedCid,
                    targetQuality = targetQuality,
                    audioLang = targetAudioLanguage,
                )

                result.fold(
                    onSuccess = { (info, playData) ->
                        if (!shouldApplyLoadResult(
                                requestGeneration = requestGeneration,
                                activeGeneration = activeLoadGeneration,
                                expectedBvid = bvid,
                                currentPlayingBvid = currentPlayingBvid
                            )
                        ) return@fold
                        val selectionSpeed = exoPlayer.playbackParameters.speed
                        val streamUrls = withContext(Dispatchers.Default) {
                            resolvePortraitPlaybackStreamUrls(
                                playData = playData,
                                targetQuality = targetQuality,
                                isHevcSupported = com.android.purebilibili.core.util.MediaUtils.isHevcSupportedForPlayback(context),
                                isAv1Supported = com.android.purebilibili.core.util.MediaUtils.isAv1SupportedForPlayback(context),
                                requestedAudioQuality = targetAudioQuality,
                                playbackSpeed = selectionSpeed,
                                preferCodec = targetCodec,
                                secondPreferCodec = targetSecondCodec,
                                videoTrackFilter = { VideoTrackCapabilities.filterForPlayback(context, it) },
                            )
                        } ?: run {
                                failPlayback("没有可播放的视频流，请重试")
                                return@fold
                            }
                        if (!shouldApplyLoadResult(
                                requestGeneration = requestGeneration,
                                activeGeneration = activeLoadGeneration,
                                expectedBvid = bvid,
                                currentPlayingBvid = currentPlayingBvid,
                            )
                        ) return@fold
                        val resolvedUrls = resolvePortraitPlaybackCdnUrls(
                            streamUrls = streamUrls,
                            cachedDashVideos = playData.dash?.video.orEmpty(),
                            cachedDashAudios = playData.dash
                                ?.let(::collectAudioStreamCandidates)
                                ?.map { it.track }
                                .orEmpty(),
                            targetQuality = targetQuality,
                            cdnPlugin = portraitPlaybackCdnPlugin,
                            preference = portraitCdnPreference,
                            measuredPreference = if (useMeasuredCdn) {
                                com.android.purebilibili.feature.plugin.CdnSpeedTest.recommendedPreference()
                            } else {
                                null
                            },
                        )
                        val dashVideoIds = playData.playableVideoQualityIds()
                        portraitSwitchableQualityIds = dashVideoIds
                        val availableQualityIds = resolvePortraitAvailableQualityIds(
                            acceptQualities = playData.advertisedVideoQualityIds(),
                            dashVideoIds = dashVideoIds
                        )
                        if (availableQualityIds.isNotEmpty()) {
                            portraitAvailableQualityIds = availableQualityIds
                        }
                        portraitDisplayedQuality = streamUrls.videoQualityId ?: resolvePortraitDisplayedQualityId(
                            requestedQuality = targetQuality,
                            returnedQuality = playData.quality,
                            dashVideoIds = dashVideoIds
                        )

                        val resolvedCid = info.cid.takeIf { it > 0L } ?: requestedCid
                        val mediaId = resolvePortraitMediaId(bvid, resolvedCid)
                        val videoItem = MediaItem.Builder()
                            .setUri(resolvedUrls.videoUrl)
                            .setMediaId(mediaId)
                            .build()
                        val videoSource = portraitMediaSourceFactory.createMediaSource(videoItem)
                        val finalSource = resolvedUrls.audioUrl?.takeIf { it.isNotEmpty() }?.let { audioUrl ->
                            val audioItem = MediaItem.Builder()
                                .setUri(audioUrl)
                                .setMediaId("audio_$mediaId")
                                .build()
                            val audioSource = portraitMediaSourceFactory.createMediaSource(audioItem)
                            MergingMediaSource(videoSource, audioSource)
                        } ?: videoSource

                        if (!shouldApplyLoadResult(
                                requestGeneration = requestGeneration,
                                activeGeneration = activeLoadGeneration,
                                expectedBvid = bvid,
                                currentPlayingBvid = currentPlayingBvid
                            )
                        ) {
                            com.android.purebilibili.core.util.Logger.d(
                                "PortraitVideoPager",
                                "Discarded stale video load for $bvid (request=$requestGeneration, active=$activeLoadGeneration, current=$currentPlayingBvid)"
                            )
                            return@fold
                        }

                        resolveAspectRatioFromDimension(info.dimension)?.let { aspectRatio ->
                            knownVideoAspectRatios[bvid] = aspectRatio
                            trimPortraitMetadataMap(knownVideoAspectRatios, keepKeys = setOf(bvid))
                        }
                        portraitCachedDash = playData.dash
                        portraitAiAudio = playData.aiAudio
                        portraitAudioLanguage = playData.curLanguage ?: targetAudioLanguage
                        portraitCurrentVideoUrl = resolvedUrls.videoUrl
                        portraitCurrentAudioUrl = resolvedUrls.audioUrl.orEmpty()
                        portraitRequestedAudioQuality = targetAudioQuality
                        portraitSelectedAudioQuality =
                            streamUrls.audioSelection?.selectedPreferenceId ?: -1
                        portraitAvailableAudioQualities =
                            streamUrls.audioSelection?.availableOptions.orEmpty()
                        portraitVideoUrlCandidates = playData.dash?.video
                            ?.firstOrNull { it.getValidUrl() == streamUrls.videoUrl }?.let { track ->
                                (listOf(track.getValidUrl()) + track.backupUrl.orEmpty())
                                    .filter { it.isNotBlank() }.distinct()
                            } ?: listOf(resolvedUrls.videoUrl)
                        portraitAudioUrlCandidates = streamUrls.audioSelection?.selected?.track?.let { track ->
                            (listOf(track.getValidUrl()) + track.backupUrl.orEmpty())
                                .filter { it.isNotBlank() }.distinct()
                        }.orEmpty()
                        currentPlayingCid = resolvedCid
                        currentPlayingAid = info.aid
                        requestedPlaybackSnapshot = requestedPlaybackSnapshot?.copy(cid = resolvedCid)
                        onPlaybackIdentityChange(bvid, resolvedCid, info.pic.ifBlank { targetCover })

                        // Keep only parts of the same multi-P video adjacent. UGC season episodes
                        // must not replace the diverse recommendation stream during normal swiping.
                        val currentPageIndex = pageItems.indexOfFirst { candidate ->
                            val identity = resolvePortraitPagePlaybackIdentity(candidate)
                            identity?.bvid == bvid &&
                                (requestedCid <= 0L || identity.cid == requestedCid || identity.cid <= 0L)
                        }.takeIf { it >= 0 } ?: pagerState.currentPage
                        // Story / 竖屏直达 seed 常无 owner；用详情回填，避免只显示 `@`
                        pageItems.getOrNull(currentPageIndex)?.let { existing ->
                            pageItems[currentPageIndex] = enrichPortraitPageItemWithLoadedInfo(
                                existing = existing,
                                loaded = info
                            )
                        }
                        val followUps = resolvePortraitCollectionFollowUps(
                            info = info,
                            currentCid = resolvedCid,
                            onlyVertical = latestOnlyVerticalRecommendations,
                            minVerticalRatio = latestVerticalVideoRatio,
                        )
                        val injectItems = resolvePortraitCollectionInjectionPlan(
                            pageItems = pageItems.toList(),
                            currentPage = currentPageIndex,
                            followUps = followUps
                        )
                        if (injectItems.isNotEmpty()) {
                            val insertAt = (currentPageIndex + 1).coerceIn(0, pageItems.size)
                            insertPortraitPageItems(insertAt, injectItems)
                            recommendationItems.addAll(
                                0,
                                injectItems.filter { item ->
                                    recommendationItems.none { existing ->
                                        existing.bvid == item.bvid && existing.cid == item.cid
                                    }
                                }
                            )
                        }
                        lastLoadedCollectionInfoBvid = info.bvid
                        lastLoadedCollectionInfo = info

                        // A pause while the address request was in flight supersedes its original autoplay.
                        val shouldPlay = latestPortraitPlaybackAllowed &&
                            (requestedPlaybackSnapshot?.playWhenReady ?: resumePlayback)
                        exoPlayer.playWhenReady = shouldPlay
                        val reusedPreparedMedia = preparedPlayback?.let {
                            wifiPreloadQueue.playClaimed(it, plannedPositionMs)
                        } == true
                        if (!reusedPreparedMedia) {
                            exoPlayer.setMediaSource(finalSource)
                            exoPlayer.prepare()
                        }

                        when {
                            seekResumeMs != null && seekResumeMs > 0L -> {
                                exoPlayer.seekTo(seekResumeMs)
                            }
                            applyInitialSeekOnFirstPage && entryStartPositionMs > 0 && !hasConsumedInitialSeek -> {
                                exoPlayer.seekTo(entryStartPositionMs)
                                hasConsumedInitialSeek = true
                            }
                        }

                        if (shouldPlay) {
                            exoPlayer.play()
                        }
                    },
                    onFailure = {
                        failPlayback("视频加载失败，请重试")
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                failPlayback("视频加载失败，请重试")
            }
        }
        return true
    }

    LaunchedEffect(
        exoPlayer, wifiPreloadQueue, portraitWifiAvailable, isPortraitPlaybackAllowed, canPlayInVisibleWindow,
        portraitSelectedQuality, portraitRequestedAudioQuality, portraitPreloadSpeed, activeLoadGeneration, isLoading,
        portraitPreferredCodec, portraitSecondPreferredCodec, portraitCdnPreference,
    ) {
        if (!portraitWifiAvailable || !isPortraitPlaybackAllowed || !canPlayInVisibleWindow || portraitSelectedQuality <= 0 || isLoading) {
            wifiPreloadQueue.clear()
            exoPlayer.setPreloadConfiguration(ExoPlayer.PreloadConfiguration.DEFAULT)
            return@LaunchedEffect
        }
        snapshotFlow {
            val settledPage = pagerState.settledPage
            val scrollingElsewhere = pagerState.isScrollInProgress &&
                pagerState.currentPage !in settledPage..(settledPage + 1)
            if (scrollingElsewhere || playbackFailure != null) null
            else pageItems.getOrNull(settledPage + 1)?.let(::resolvePortraitPagePlaybackIdentity)
        }.distinctUntilChanged().collectLatest { target ->
            if (target == null) {
                wifiPreloadQueue.clear()
                return@collectLatest
            }
            wifiPreloadQueue.warm(
                target, resolvePortraitPlaybackTargetQuality(portraitSelectedQuality),
                portraitRequestedAudioQuality, portraitPreloadSpeed,
                portraitPreferredCodec, portraitSecondPreferredCodec, portraitCdnPreference,
                onPreparedInfo = { info ->
                    resolveAspectRatioFromDimension(info.dimension)?.let { aspect ->
                        knownVideoAspectRatios[info.bvid] = aspect
                    }
                },
            )
        }
    }

    LaunchedEffect(isPortraitPlaybackAllowed, portraitSelectedQuality) {
        if (!isPortraitPlaybackAllowed || portraitSelectedQuality <= 0) return@LaunchedEffect
        val snapshot = inactivitySnapshot ?: return@LaunchedEffect
        inactivitySnapshot = null
        hasAppliedSharedEntryResume = true
        val page = pagerState.settledPage
        val identity = pageItems.getOrNull(page)?.let(::resolvePortraitPagePlaybackIdentity) ?: return@LaunchedEffect
        if (identity.bvid != snapshot.bvid || (identity.cid > 0L && snapshot.cid > 0L && identity.cid != snapshot.cid)) {
            return@LaunchedEffect
        }
        if (playbackFailure?.snapshot?.let {
                it.bvid == identity.bvid && (identity.cid <= 0L || it.cid <= 0L || identity.cid == it.cid)
            } == true
        ) return@LaunchedEffect
        val mediaId = exoPlayer.currentMediaItem?.mediaId
        val matchingMedia = mediaId == resolvePortraitMediaId(snapshot.bvid, snapshot.cid) ||
            (useSharedPlayer && mediaId == snapshot.bvid)
        if (matchingMedia && exoPlayer.playerError == null) {
            if (exoPlayer.playbackState == Player.STATE_IDLE) {
                if (snapshot.positionMs > 0L) {
                    exoPlayer.seekTo(snapshot.positionMs)
                }
                exoPlayer.prepare()
            }
            exoPlayer.playWhenReady = snapshot.playWhenReady
            if (snapshot.playWhenReady) exoPlayer.play() else exoPlayer.pause()
        } else {
            requestPortraitPlaybackForPage(
                targetPage = page,
                applyInitialSeekOnFirstPage = false,
                forceReload = true,
                resumePositionMs = snapshot.positionMs,
                playWhenReadyOverride = snapshot.playWhenReady
            )
        }
    }

    // One effect owns the entire prefetch window, including cancellation of obsolete tasks.
    LaunchedEffect(
        pagerState, pageItems, portraitSelectedQuality, portraitRequestedAudioQuality,
        prefetchVideoEnabled, portraitWifiAvailable, isPortraitPlaybackAllowed, canPlayInVisibleWindow, portraitPlaybackCdnPlugin, portraitCdnPreference,
        portraitPreferredCodec, portraitSecondPreferredCodec,
    ) {
        if (portraitSelectedQuality <= 0 || !isPortraitPlaybackAllowed || !canPlayInVisibleWindow) return@LaunchedEffect
        if (!portraitWifiAvailable && !prefetchVideoEnabled) return@LaunchedEffect
        val prefetchExecutor = PortraitPrefetchWindowExecutor<PortraitPagePlaybackIdentity> {
            resolvePortraitMediaId(it.bvid, it.cid)
        }
        val targetQuality = resolvePortraitPlaybackTargetQuality(portraitSelectedQuality)
        try {
            snapshotFlow {
                val anchorPage = pagerState.currentPage
                val candidatePage = resolvePortraitSwipePrefetchTargetPage(
                    isScrollInProgress = pagerState.isScrollInProgress,
                    currentPage = anchorPage,
                    currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                    lastPageIndex = pageItems.lastIndex
                )
                PortraitPrefetchWindowState(
                    anchorPage = anchorPage,
                    candidatePage = candidatePage,
                    warmAnchor = pagerState.isScrollInProgress && anchorPage != pagerState.settledPage,
                    pageCount = pageItems.size,
                )
            }.distinctUntilChanged().collect { window ->
                // Copy the queue and query connectivity only when the target window changes.
                val pageSnapshot = pageItems.toList()
                val preloadCount = resolvePortraitPlayUrlPreloadCount(
                    prefetchVideoEnabled = prefetchVideoEnabled,
                    isWifi = NetworkUtils.isWifi(context),
                    availableTargets = (pageSnapshot.size - window.anchorPage - 1).coerceAtLeast(0)
                )
                val targets = resolvePortraitPrefetchWindowTargets(
                    anchorPage = window.anchorPage,
                    pageItems = pageSnapshot,
                    preloadCount = preloadCount,
                    swipeTargetPage = window.candidatePage,
                    warmAnchor = window.warmAnchor
                )
                prefetchExecutor.updateTargets(scope = this, targets = targets) { target ->
                    val key = resolvePortraitMediaId(target.bvid, target.cid)
                    launch(Dispatchers.IO) {
                        try {
                            val playData = VideoRepository.preloadPortraitPlayUrl(
                                bvid = target.bvid,
                                cid = target.cid,
                                aid = target.aid,
                                targetQuality = targetQuality
                            ) ?: return@launch
                            val streamUrls = resolvePortraitPlaybackStreamUrls(
                                playData = playData,
                                targetQuality = targetQuality,
                                isHevcSupported = com.android.purebilibili.core.util.MediaUtils.isHevcSupportedForPlayback(context),
                                isAv1Supported = com.android.purebilibili.core.util.MediaUtils.isAv1SupportedForPlayback(context),
                                requestedAudioQuality = portraitRequestedAudioQuality,
                                preferCodec = portraitPreferredCodec,
                                secondPreferCodec = portraitSecondPreferredCodec,
                                videoTrackFilter = { VideoTrackCapabilities.filterForPlayback(context, it) },
                            ) ?: return@launch
                            val resolvedUrls = resolvePortraitPlaybackCdnUrls(
                                streamUrls = streamUrls,
                                cachedDashVideos = playData.dash?.video.orEmpty(),
                                cachedDashAudios = playData.dash
                                    ?.let(::collectAudioStreamCandidates)?.map { it.track }.orEmpty(),
                                targetQuality = targetQuality,
                                cdnPlugin = portraitPlaybackCdnPlugin,
                                preference = portraitCdnPreference,
                            )
                            prefetchPortraitPlaybackHead(context, resolvedUrls)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            com.android.purebilibili.core.util.Logger.w(
                                "PortraitVideoPager", "预取未完成: $key ${error.message}"
                            )
                        }
                    }
                }
            }
        } finally {
            prefetchExecutor.cancelAll()
        }
    }

    // 拖动期间仅预取；停稳后才替换媒体，回撤不会打断原视频。
    // 默认画质就绪后重新检查当前页。共享播放器的 BVID 是入口种子，
    // 不能据此认定媒体已准备；复用与在途请求去重由播放请求内部判断。
    LaunchedEffect(pagerState, pageItems, isPortraitPlaybackAllowed, portraitSelectedQuality) {
        snapshotFlow {
            if (pagerState.isScrollInProgress) null else pagerState.currentPage
        }
            .filterNotNull()
            .collect { committedPage ->
                val pageChanged = lastCommittedPage != committedPage
                lastCommittedPage = committedPage
                val item = pageItems.getOrNull(committedPage) ?: return@collect
                val playbackIdentity = resolvePortraitPagePlaybackIdentity(item) ?: return@collect
                val bvid = playbackIdentity.bvid

                if (pageChanged) {
                    commentViewModel.clearForVideoChange()
                    onVideoChange(bvid)
                }

                if (!isPortraitPlaybackAllowed) {
                    pendingAutoPlayGeneration = -1
                    isLoading = false
                    return@collect
                }

                val onlyVerticalEnabled = latestOnlyVerticalRecommendations
                val verticalRatioThreshold = latestVerticalVideoRatio
                if (
                    !recommendationsManagedExternally &&
                    shouldLoadMorePortraitRecommendations(
                        committedPage = committedPage,
                        totalItemsCount = pageItems.size,
                        isLoadingMoreRecommendations = isLoadingMoreRecommendations,
                        prefetchThreshold = resolvePortraitRecommendationPrefetchThreshold(
                            onlyVerticalRecommendations = onlyVerticalEnabled,
                        ),
                    ) &&
                    bvid !in appendedRecommendationSeeds
                ) {
                    appendedRecommendationSeeds.add(bvid)
                    if (appendedRecommendationSeeds.size > PORTRAIT_MAX_CACHED_PAGE_METADATA) {
                        appendedRecommendationSeeds.removeAt(0)
                    }
                    isLoadingMoreRecommendations = true
                    launch {
                        try {
                            val batchRequest = withContext(Dispatchers.Main.immediate) {
                                PortraitRecommendationBatchRequest(
                                    currentBvid = bvid,
                                    currentAid = currentPlayingAid,
                                    startingStoryDisplayId = storyRecommendationDisplayId,
                                    startingHomeFeedCursor = recommendationFeedCursor,
                                    onlyVertical = onlyVerticalEnabled,
                                    minVerticalRatio = verticalRatioThreshold,
                                    recommendationShuffleSeed = recommendationShuffleSeed,
                                    existingBvids = snapshotPortraitPageBvids(pageItems),
                                    existingRecommendations = recommendationItems.toList(),
                                    precedingOwnerMid = pageItems.lastOrNull()?.let(::resolvePortraitPageOwnerMid) ?: 0L,
                                )
                            }
                            val batchResult = fetchPortraitRecommendationAppendBatch(batchRequest)
                            withContext(Dispatchers.Main.immediate) {
                                storyRecommendationDisplayId = batchResult.nextStoryDisplayId
                                recommendationFeedCursor = batchResult.nextHomeFeedCursor
                                if (batchResult.appendItems.isNotEmpty()) {
                                    recommendationItems.addAll(batchResult.appendItems)
                                    appendPortraitPageItems(batchResult.appendItems)
                                }
                            }
                        } finally {
                            withContext(Dispatchers.Main.immediate) {
                                isLoadingMoreRecommendations = false
                            }
                        }
                    }
                }

                requestPortraitPlaybackForPage(
                    targetPage = committedPage,
                    applyInitialSeekOnFirstPage = committedPage == 0
                )
            }
    }

    LaunchedEffect(
        pagerState, recommendationsManagedExternally, recommendationLoadInProgress,
        recommendationLoadError, onlyVerticalRecommendations, recommendations, emptyExternalRecommendationBatches
    ) {
        if (!recommendationsManagedExternally || recommendationLoadInProgress || recommendationLoadError != null ||
            emptyExternalRecommendationBatches >= 3
        ) {
            return@LaunchedEffect
        }
        snapshotFlow {
            if (pagerState.isScrollInProgress ||
                appliedExternalRecommendations != (recommendations to onlyVerticalRecommendations)
            ) null else (pagerState.currentPage to pageItems.size)
        }.filterNotNull().collect { (page, count) ->
            if (shouldLoadMorePortraitRecommendations(
                    committedPage = page,
                    totalItemsCount = count,
                    isLoadingMoreRecommendations = false,
                    prefetchThreshold = resolvePortraitRecommendationPrefetchThreshold(onlyVerticalRecommendations)
                )
            ) latestLoadMoreRecommendations()
        }
    }

    LaunchedEffect(danmakuManager, currentPlayingCid, currentPlayingAid, danmakuEnabled, danmakuSettingsLoaded, exoPlayer) {
        if (shouldLoadPortraitDanmaku(danmakuSettingsLoaded, currentPlayingCid, danmakuEnabled)) {
            danmakuManager.updateSettings(danmakuSettings)
            danmakuManager.isEnabled = true
            var durationMs = exoPlayer.duration
            var retries = 0
            while (durationMs <= 0 && retries < 50) {
                delay(100)
                durationMs = exoPlayer.duration
                retries++
            }
            danmakuManager.loadDanmaku(
                currentPlayingCid,
                currentPlayingAid,
                durationMs.coerceAtLeast(0L),
                currentPlayingBvid.orEmpty()
            )
        } else {
            danmakuManager.isEnabled = false
        }
    }

    LaunchedEffect(viewModel, danmakuManager) {
        viewModel.danmakuSentEvent.collect { danmakuData ->
            danmakuManager.addLocalDanmaku(
                text = danmakuData.text,
                color = danmakuData.color,
                mode = danmakuData.mode,
                fontSize = danmakuData.fontSize,
                isVipGradualColor = danmakuData.isVipGradualColor,
            )
        }
    }

    LaunchedEffect(
        danmakuManager,
        danmakuSettings,
        danmakuSettingsLoaded
    ) {
        if (!danmakuSettingsLoaded) return@LaunchedEffect
        danmakuManager.updateSettings(danmakuSettings)
    }

    var portraitCommentOverlayActive by remember { mutableStateOf(false) }
    var portraitUpPreviewActive by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) {
        portraitCommentOverlayActive = false
        portraitUpPreviewActive = false
    }

    val latestOnUserClick by rememberUpdatedState(onUserClick)
    val handlePortraitUserClick = remember(onUserClick) {
        { mid: Long ->
            capturePortraitInactivitySnapshot()
            latestOnUserClick(mid)
        }
    }
    val jumpToPortraitPageForVideo = remember(pageItems, pagerState, scope) {
        jump@{ targetBvid: String, targetCid: Long, collectionContext: UgcSeason? ->
            val normalizedBvid = targetBvid.trim()
            if (normalizedBvid.isEmpty()) return@jump
            val targetIndex = resolvePortraitCollectionPageIndex(
                pageItems = pageItems,
                targetBvid = normalizedBvid,
                targetCid = targetCid
            )
            if (targetIndex >= 0) {
                pageItems[targetIndex] = buildPortraitCollectionPageItem(
                    existing = pageItems[targetIndex],
                    targetBvid = normalizedBvid,
                    targetCid = targetCid,
                    collectionContext = collectionContext,
                )
                scope.launch {
                    pagerState.animateScrollToPage(targetIndex)
                }
                return@jump
            }
            // Not yet in the pager list: insert as a dedicated page after current, then jump.
            val insertAt = (pagerState.currentPage + 1).coerceIn(0, pageItems.size)
            val inserted = buildPortraitCollectionPageItem(
                existing = null,
                targetBvid = normalizedBvid,
                targetCid = targetCid,
                collectionContext = collectionContext,
            )
            val itemIdentity = resolvePortraitPagePlaybackIdentity(inserted) ?: return@jump
            val key = portraitCollectionIdentityKey(itemIdentity.bvid, itemIdentity.cid)
            val exists = pageItems.any { candidate ->
                val identity = resolvePortraitPagePlaybackIdentity(candidate) ?: return@any false
                portraitCollectionIdentityKey(identity.bvid, identity.cid) == key
            }
            if (!exists && itemIdentity.bvid.isNotBlank()) {
                com.android.purebilibili.core.util.Logger.d(
                    "PortraitVideoPager",
                    "Insert missing portrait page for requested video: $normalizedBvid"
                )
                insertPortraitPageItems(insertAt, listOf(inserted))
                scope.launch {
                    pagerState.animateScrollToPage(insertAt)
                }
            }
        }
    }

    val pagerAdaptiveInfo = com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current
    val portraitPagerContent: @Composable () -> Unit = {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable(enabled = pagerAdaptiveInfo.hardwareKeyboardConnected)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown ||
                    portraitCommentOverlayActive ||
                    portraitUpPreviewActive
                ) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionUp, Key.PageUp -> {
                        val target = (pagerState.currentPage - 1).coerceAtLeast(0)
                        if (target != pagerState.currentPage) {
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        true
                    }
                    Key.DirectionDown, Key.PageDown -> {
                        val target = (pagerState.currentPage + 1).coerceAtMost((pageItems.size - 1).coerceAtLeast(0))
                        if (target != pagerState.currentPage) {
                            scope.launch { pagerState.animateScrollToPage(target) }
                        }
                        true
                    }
                    Key.Spacebar, Key.K -> {
                        com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction(exoPlayer)
                        true
                    }
                    else -> false
                }
            }
    ) {
    VerticalPager(
        state = pagerState,
        key = { index -> pageSlotIds.getOrNull(index) ?: (-index.toLong() - 1L) },
        userScrollEnabled = !entryTransitionInProgress && shouldEnablePortraitPagerUserScroll(
            scale = currentPageScale,
            commentOverlayActive = portraitCommentOverlayActive,
            upPreviewActive = portraitUpPreviewActive
        ),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .appScrollBoundaryHaptics(pagerState)
    ) { page ->
        val item = pageItems.getOrNull(page)
        val handleUserClick = handlePortraitUserClick

        if (item != null) {
            val itemBvid = when (item) {
                is ViewInfo -> item.bvid
                is RelatedVideo -> item.bvid
                else -> ""
            }
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                val stageWidthDp = com.android.purebilibili.feature.video.screen
                    .resolvePortraitPagerStageWidthDp(
                        containerWidthDp = maxWidth.value,
                        containerHeightDp = maxHeight.value,
                        isInPipMode = isInPipMode,
                    )
            VideoPageItem(
                item = item,
                isCurrentPage = page == pagerState.settledPage,
                onBack = onBack,
                onHomeClick = onHomeClick,
                viewModel = viewModel,
                commentViewModel = commentViewModel,
                engagementOwner = engagementOwner,
                knownEngagementState = knownEngagementState,
                favoriteQuickSaveDefaultFolder = favoriteQuickSaveDefaultFolder,
                exoPlayer = exoPlayer, // [核心] 传递共享播放器
                useTextureSurfaceForNavigation = useTextureSurfaceForNavigation,
                isPlayerSurfaceReady = isPlayerSurfaceReady,
                isInPipMode = isInPipMode,
                entryTransitionInProgress = entryTransitionInProgress,
                currentPlayingBvid = currentPlayingBvid, // [修复] 传递当前播放的 BVID 用于校验
                currentPlayingCid = currentPlayingCid,
                currentPlayingAid = currentPlayingAid,
                isPortraitPlaybackAllowed = isPortraitPlaybackAllowed,
                isLoading = if (page == pagerState.currentPage) isLoading else false, // 只有当前页显示 Loading
                danmakuManager = danmakuManager,
                danmakuEnabled = danmakuEnabled,
                danmakuSmartOcclusion = danmakuSmartOcclusion,
                portraitDanmakuDisplayAreaMode = portraitDanmakuDisplayAreaMode,
                onExitSnapshot = onExitSnapshot,
                onSearchClick = onSearchClick,
                onUserClick = handleUserClick,
                onOpenBilibiliLink = onOpenBilibiliLink,
                onExitFullscreen = onExitFullscreen,
                onProgressUpdate = onProgressUpdate,
                watchLaterVideos = watchLaterVideos,
                recommendationVideos = recommendationItems,
                knownVideoAspectRatio = knownVideoAspectRatios[itemBvid]
                    ?: (item as? ViewInfo)?.dimension?.let(::resolveAspectRatioFromDimension),
                loadedPageInfo = lastLoadedCollectionInfo
                    ?.takeIf { loadedInfo -> loadedInfo.bvid == itemBvid },
                qualityLabel = portraitQualityLabel,
                selectedQualityId = portraitSelectedQuality,
                availableQualityIds = portraitAvailableQualityIds,
                switchableQualityIds = portraitSwitchableQualityIds,
                requestedAudioQuality = portraitRequestedAudioQuality,
                selectedAudioQuality = portraitSelectedAudioQuality,
                availableAudioQualities = portraitAvailableAudioQualities,
                videoUrl = portraitCurrentVideoUrl,
                audioUrl = portraitCurrentAudioUrl,
                enhancement = portraitEnhancement,
                menuActions = menuActions.copy(
                    videoUrlCandidates = portraitVideoUrlCandidates,
                    audioUrlCandidates = portraitAudioUrlCandidates,
                ),
                availableCodecIds = portraitAvailableCodecIds,
                onReloadPlayback = {
                    requestPortraitPlaybackForPage(pagerState.currentPage, false, forceReload = true)
                },
                aiAudioInfo = portraitAiAudio,
                currentAudioLanguage = portraitAudioLanguage,
                onAudioLanguageSelected = { language ->
                    portraitAudioLanguage = language.takeUnless { it.isBlank() }
                    requestPortraitPlaybackForPage(
                        targetPage = pagerState.currentPage,
                        applyInitialSeekOnFirstPage = false,
                        forceReload = true,
                    )
                },
                onCodecSelected = { codec ->
                    viewModel.setVideoCodec(codec)
                    requestPortraitPlaybackForPage(pagerState.currentPage, false, forceReload = true)
                },
                onSecondCodecSelected = { codec ->
                    viewModel.setVideoSecondCodec(codec)
                    requestPortraitPlaybackForPage(pagerState.currentPage, false, forceReload = true)
                },
                aspectRatio = portraitAspectRatio,
                isAudioOnly = portraitAudioOnly,
                onAudioOnlyToggle = { portraitAudioOnly = !portraitAudioOnly },
                isLoggedIn = isPortraitLoggedIn,
                isVip = isPortraitVip,
                onQualitySelected = { qualityId ->
                    if (qualityId <= 0 || qualityId == portraitSelectedQuality) return@VideoPageItem
                    portraitSelectedQuality = qualityId
                    portraitDisplayedQuality = qualityId
                    requestPortraitPlaybackForPage(
                        targetPage = pagerState.currentPage,
                        applyInitialSeekOnFirstPage = false,
                        forceReload = true
                    )
                },
                onAudioQualitySelected = { audioQuality ->
                    switchPortraitAudioQuality(audioQuality)
                },
                onPlaybackSpeedSelected = { speed ->
                    val normalizedSpeed = speed.coerceAtLeast(0.1f)
                    exoPlayer.playbackParameters = PlaybackParameters(normalizedSpeed, 1.0f)
                },
                onAspectRatioChange = { ratio ->
                    // Runtime safety is applied per-page from actual video aspect.
                    portraitAspectRatio = ratio
                },
                hasRenderedFirstFrame = (renderedFirstFrameGeneration == activeLoadGeneration),
                initialProgressPositionMs = resolvePortraitInitialProgressPosition(
                    isFirstPage = page == 0,
                    initialStartPositionMs = entryStartPositionMs
                ),
                onCurrentPageScaleChange = { scale ->
                    if (page == pagerState.currentPage) {
                        currentPageScale = scale
                    }
                },
                onCommentOverlayActiveChange = { active ->
                    if (page == pagerState.currentPage) {
                        portraitCommentOverlayActive = active
                    }
                },
                onUpPreviewActiveChange = { active ->
                    if (page == pagerState.currentPage) {
                        portraitUpPreviewActive = active
                    }
                },
                portraitPlayerSettings = portraitPlayerSettings,
                portraitOverlayVisible = portraitOverlayVisible,
                portraitCleanScreen = portraitCleanScreen,
                onPortraitCleanScreenChange = { clean ->
                    if (page == pagerState.currentPage) {
                        portraitCleanScreen = clean
                        portraitOverlayVisible = !clean
                    }
                },
                onPortraitOverlayVisibleChange = { visible ->
                    if (page == pagerState.currentPage) {
                        portraitOverlayVisible = visible
                        if (visible) portraitCleanScreen = false
                    }
                },
                onRequestVideoChange = { targetBvid ->
                    val normalizedBvid = targetBvid.trim()
                    if (normalizedBvid.isEmpty()) return@VideoPageItem
                    // UpPreview / detail picks may sit outside the current portrait pager.
                    // jumpToPortraitPageForVideo scrolls when present and inserts when missing.
                    jumpToPortraitPageForVideo(normalizedBvid, 0L, null)
                },
                onRequestCollectionItem = jumpToPortraitPageForVideo,
                stageWidthDp = stageWidthDp,
            )
            }
        }
    }

        val visiblePlaybackIdentity = pageItems.getOrNull(pagerState.settledPage)
            ?.let(::resolvePortraitPagePlaybackIdentity)
        val visiblePlaybackFailure = playbackFailure?.takeIf { failure ->
            failure.generation == activeLoadGeneration &&
                visiblePlaybackIdentity != null && visiblePlaybackIdentity.bvid == failure.snapshot.bvid &&
                (visiblePlaybackIdentity.cid <= 0L || failure.snapshot.cid <= 0L ||
                    visiblePlaybackIdentity.cid == failure.snapshot.cid)
        }
        if (visiblePlaybackFailure != null && isPortraitPlaybackAllowed && !pagerState.isScrollInProgress &&
            !portraitCommentOverlayActive && !portraitUpPreviewActive
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AppText(visiblePlaybackFailure.message, color = Color.White)
                AppButton(onClick = {
                    val failure = playbackFailure ?: return@AppButton
                    val page = pagerState.settledPage
                    val identity = pageItems.getOrNull(page)?.let(::resolvePortraitPagePlaybackIdentity)
                        ?: return@AppButton
                    if (failure.generation != activeLoadGeneration || identity.bvid != failure.snapshot.bvid ||
                        (identity.cid > 0L && failure.snapshot.cid > 0L && identity.cid != failure.snapshot.cid) ||
                        pagerState.isScrollInProgress || !latestPortraitPlaybackAllowed
                    ) return@AppButton
                    VideoRepository.invalidateVideoLoadCaches(identity.bvid, identity.aid, failure.snapshot.cid)
                    requestPortraitPlaybackForPage(
                        targetPage = page,
                        applyInitialSeekOnFirstPage = false,
                        forceReload = true,
                        resumePositionMs = failure.snapshot.positionMs,
                        playWhenReadyOverride = failure.snapshot.playWhenReady,
                        useMeasuredCdn = false,
                    )
                }) {
                    AppText("重试播放")
                }
            }
        }

        if (recommendationsManagedExternally && pagerState.currentPage == pageItems.lastIndex &&
            (recommendationLoadError != null || emptyExternalRecommendationBatches >= 3) &&
            !recommendationLoadInProgress && !portraitCommentOverlayActive && !portraitUpPreviewActive
        ) {
            AppButton(
                onClick = {
                    emptyExternalRecommendationBatches = 0
                    latestLoadMoreRecommendations()
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp)
            ) {
                AppText("加载更多推荐 · 重试")
            }
        }

        NextWatchOverlay(
            player = exoPlayer,
            mediaKey = "portrait:$currentPlayingBvid:$currentPlayingCid",
            // 小窗里无法点击「接下来观看」，与限流播放器的 NextWatch 策略保持一致。
            eligible = isPortraitPlaybackAllowed && !isInPipMode && !isLoading && !pagerState.isScrollInProgress &&
                !portraitCommentOverlayActive && !portraitUpPreviewActive &&
                playbackCompletionBehavior != com.android.purebilibili.core.store.PlaybackCompletionBehavior.REPEAT_ONE,
            resolve = {
                val targetPage = if (pagerState.currentPage < pageItems.lastIndex) pagerState.currentPage + 1
                    else if (playbackCompletionBehavior == com.android.purebilibili.core.store.PlaybackCompletionBehavior.LOOP_PLAYLIST) 0 else -1
                val next = pageItems.getOrNull(targetPage)
                val info = when (next) {
                    is ViewInfo -> next
                    is RelatedVideo -> toViewInfoForPortraitDetail(next)
                    else -> null
                }
                if (info != null && info.bvid.isNotBlank() &&
                    (info.bvid != currentPlayingBvid || info.cid != currentPlayingCid)) {
                    com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion(
                        currentPlayingBvid.orEmpty(), currentPlayingCid, info.bvid, info.cid,
                        info.title, info.pic, if (isExternalPlaylist) "接下来观看" else "推荐观看",
                        playlistIndex = targetPage
                    )
                } else null
            },
            play = { target ->
                val next = pageItems.getOrNull(target.playlistIndex ?: -1)
                val info = when (next) {
                    is ViewInfo -> next
                    is RelatedVideo -> toViewInfoForPortraitDetail(next)
                    else -> null
                }
                if (target.sourceBvid == currentPlayingBvid && target.sourceCid == currentPlayingCid &&
                    info != null && info.bvid == target.bvid && info.cid == target.cid && target.playlistIndex != null) {
                    scope.launch { pagerState.animateScrollToPage(target.playlistIndex) }
                    true
                } else false
            },
        )
    }
    }
    // 折叠屏 Book/Tabletop 存在铰链时将 9:16 短视频收进首个安全屏，避免画面从正中被折痕切开。
    if (pagerAdaptiveInfo.shouldAvoidHinge && !isInPipMode) {
        com.android.purebilibili.core.ui.adaptive.AppHingePaneLayout(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            primaryContent = portraitPagerContent,
        )
    } else {
        portraitPagerContent()
    }

}

@UnstableApi
@Composable
private fun VideoPageItem(
    item: Any,
    isCurrentPage: Boolean,
    onBack: () -> Unit,
    onHomeClick: () -> Unit,
    viewModel: VideoPlaybackViewModel,
    commentViewModel: VideoCommentViewModel,
    engagementOwner: PortraitVideoEngagementStore,
    knownEngagementState: VideoEngagementUiState,
    favoriteQuickSaveDefaultFolder: Boolean,
    exoPlayer: ExoPlayer,
    useTextureSurfaceForNavigation: Boolean,
    isPlayerSurfaceReady: Boolean,
    /** 系统小窗：只保留播放面与弹幕（受「小窗和画中画中不显示弹幕」约束）。 */
    isInPipMode: Boolean = false,
    entryTransitionInProgress: Boolean = false,
    currentPlayingBvid: String?, // [新增]
    currentPlayingCid: Long,
    currentPlayingAid: Long,
    isPortraitPlaybackAllowed: Boolean,
    isLoading: Boolean,
    danmakuManager: DanmakuManager,
    danmakuEnabled: Boolean,
    danmakuSmartOcclusion: Boolean,
    portraitDanmakuDisplayAreaMode: PortraitDanmakuDisplayAreaMode,
    onExitSnapshot: (String, Long, Long, String) -> Unit,
    onSearchClick: () -> Unit,
    onUserClick: (Long) -> Unit,
    onOpenBilibiliLink: ((String) -> Unit)?,
    onExitFullscreen: () -> Unit,
    onProgressUpdate: (String, Long, Long, String) -> Unit,
    watchLaterVideos: List<RelatedVideo>,
    recommendationVideos: List<RelatedVideo>,
    knownVideoAspectRatio: Float?,
    loadedPageInfo: ViewInfo?,
    qualityLabel: String,
    selectedQualityId: Int,
    availableQualityIds: List<Int>,
    switchableQualityIds: List<Int>,
    requestedAudioQuality: Int,
    selectedAudioQuality: Int,
    availableAudioQualities: List<AudioQualityOption>,
    aspectRatio: VideoAspectRatio,
    isLoggedIn: Boolean,
    isVip: Boolean,
    onQualitySelected: (Int) -> Unit,
    onAudioQualitySelected: (Int) -> Unit,
    onPlaybackSpeedSelected: (Float) -> Unit,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    isAudioOnly: Boolean,
    onAudioOnlyToggle: () -> Unit,
    aiAudioInfo: com.android.purebilibili.data.model.response.AiAudioInfo?,
    currentAudioLanguage: String?,
    onAudioLanguageSelected: (String) -> Unit,
    onCodecSelected: (String) -> Unit,
    onSecondCodecSelected: (String) -> Unit,
    availableCodecIds: List<String>,
    videoUrl: String,
    audioUrl: String,
    menuActions: com.android.purebilibili.feature.video.ui.overlay.PiliPlayerMenuActions,
    onReloadPlayback: () -> Unit,
    enhancement: com.android.purebilibili.feature.video.ui.components.PortraitVideoEnhancementSession,
    hasRenderedFirstFrame: Boolean,
    initialProgressPositionMs: Long,
    onCurrentPageScaleChange: (Float) -> Unit,
    onCommentOverlayActiveChange: (Boolean) -> Unit = {},
    onUpPreviewActiveChange: (Boolean) -> Unit,
    portraitOverlayVisible: Boolean,
    portraitCleanScreen: Boolean,
    portraitPlayerSettings: SettingsManager.PortraitPlayerSettings,
    onPortraitCleanScreenChange: (Boolean) -> Unit,
    onPortraitOverlayVisibleChange: (Boolean) -> Unit,
    onRequestVideoChange: (String) -> Unit,
    onRequestCollectionItem: (bvid: String, cid: Long, collectionContext: UgcSeason?) -> Unit,
    stageWidthDp: Float? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val portraitSnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val showPortraitFeedback: (String) -> Unit = { feedbackMessage ->
        if (feedbackMessage.isNotBlank()) {
            scope.launch {
                portraitSnackbarHostState.currentSnackbarData?.dismiss()
                portraitSnackbarHostState.showSnackbar(
                    message = feedbackMessage,
                    duration = androidx.compose.material3.SnackbarDuration.Short,
                )
            }
        }
    }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var portraitPageInViewport by remember { mutableStateOf(false) }
    val portraitVisualWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive() &&
        portraitPageInViewport
    val surfaceHdrRequired = if (isCurrentPage) {
        com.android.purebilibili.feature.video.ui.components.rememberVideoSurfaceHdrRequired(
            exoPlayer, selectedQualityId,
        )
    } else requiresHdrSurfaceOutput(selectedQualityId)
    val portraitPageBackdrop = com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
    val portraitGlassSupported = com.android.purebilibili.feature.video.ui.section.supportsPlayerPageGlass(
        liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
        fullscreen = true,
        portraitFullscreen = true,
        hdr = requiresHdrSurfaceOutput(
            currentQualityId = selectedQualityId,
            colorTransfer = exoPlayer.videoFormat?.colorInfo?.colorTransfer ?: 0,
        ),
        anime4k = enhancement.pipelineActive,
        audioOnly = isAudioOnly,
        pip = isInPipMode,
    )
    // 后台音频许可不作为采集许可；只有当前页实际可见且前台聚焦时保留退场采样。
    val portraitCaptureActive = portraitVisualWorkActive && isCurrentPage && isPortraitPlaybackAllowed
    val portraitGlassVisibility = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (portraitGlassSupported && portraitCaptureActive && portraitOverlayVisible) 1f else 0f,
        animationSpec = if (!portraitCaptureActive || !portraitGlassSupported) {
            androidx.compose.animation.core.snap<Float>()
        } else androidx.compose.animation.core.tween<Float>(
            com.android.purebilibili.feature.video.ui.overlay.PLAYER_GLASS_VISIBILITY_DURATION_MS,
        ),
        label = "portraitPlayerGlassCapture",
    )
    val portraitGlassCaptureVisible by remember(portraitGlassVisibility) {
        derivedStateOf { portraitGlassVisibility.value > 0f }
    }
    val capturePortraitPage = portraitGlassSupported && portraitCaptureActive &&
        com.android.purebilibili.core.ui.blur.LocalChromeBackdropRecordingAllowed.current &&
        (portraitOverlayVisible || portraitGlassCaptureVisible)

    val longPressSpeed = portraitPlayerSettings.longPressSpeed
    val longPressSpeedHintCloseEnabled = portraitPlayerSettings.longPressSpeedHintCloseEnabled
    val longPressSpeedHintHidden = portraitPlayerSettings.longPressSpeedHintHidden
    val longPressSpeedHintScale = portraitPlayerSettings.longPressSpeedHintScale
    val longPressSpeedHintAlpha = portraitPlayerSettings.longPressSpeedHintAlpha
    val doubleTapSeekEnabled = portraitPlayerSettings.doubleTapSeekEnabled
    val seekForwardSeconds = portraitPlayerSettings.seekForwardSeconds
    val seekBackwardSeconds = portraitPlayerSettings.seekBackwardSeconds
    val playerInteractionSettings by remember(context) {
        SettingsManager.getPlayerInteractionSettings(context)
    }.collectAsStateWithLifecycle(
        initialValue = com.android.purebilibili.core.store.PlayerInteractionSettings()
    )
    // 竖屏流此前没有小窗概念，弹幕会无条件跟随；进入 PiP 后与播放器共用同一套
    // 「小窗和画中画中不显示弹幕」设置，避免两条播放面在小窗里策略不一致。
    val pipNoDanmakuEnabled = portraitPlayerSettings.pipNoDanmakuEnabled
    val pipDanmakuVisible = !(isInPipMode && pipNoDanmakuEnabled)
    val currentAudioQuality = requestedAudioQuality
    val bvid = if (item is ViewInfo) item.bvid else (item as RelatedVideo).bvid
    // 进入系统画中画时保留已启用的 TextureView，避免 surface 类型切换触发 PlayerView 销毁重建导致小窗闪烁；
    // HDR 场景仍由 shouldUsePlayerPageTexture 强制使用 SurfaceView。
    var hasUsedPortraitGlassTexture by remember(bvid) { mutableStateOf(portraitGlassSupported) }
    LaunchedEffect(portraitGlassSupported) {
        if (portraitGlassSupported) hasUsedPortraitGlassTexture = true
    }
    val itemAid = if (item is ViewInfo) item.aid else (item as RelatedVideo).aid
    val currentUiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val currentSuccess = currentUiState as? VideoPlaybackUiState.Success
    
    // [修复] 手动监听 ExoPlayer 播放状态，确保 UI 及时更新
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var portraitSubtitleControls by remember(bvid) {
        mutableStateOf(com.android.purebilibili.feature.video.ui.overlay.SubtitleControlUiState())
    }
    var portraitSubtitleCallbacks by remember(bvid) {
        mutableStateOf(com.android.purebilibili.feature.video.ui.overlay.SubtitleControlCallbacks())
    }
    var portraitScreenLocked by rememberSaveable(bvid) { mutableStateOf(false) }
    LaunchedEffect(isInPipMode) {
        if (isInPipMode && portraitScreenLocked) portraitScreenLocked = false
    }
    val portraitLockHaptic = com.android.purebilibili.core.util.rememberHapticFeedback(
        com.android.purebilibili.core.ui.AppHapticScene.PLAYER_GESTURES
    )
    var portraitFlippedHorizontal by rememberSaveable(bvid) { mutableStateOf(false) }
    var portraitFlippedVertical by rememberSaveable(bvid) { mutableStateOf(false) }
    val capturePortraitScreenshot =
        com.android.purebilibili.feature.video.ui.components.rememberVideoScreenshotAction(
            captureBitmap = { _, width, height -> enhancement.captureScreenshot(width, height) }
        )
    val portraitShareMenuSource = com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource()
    var pendingPortraitShare by remember(bvid, currentPlayingCid) {
        mutableStateOf<com.android.purebilibili.feature.video.share.VideoSharePayload?>(null)
    }
    com.android.purebilibili.feature.video.share.VideoShareSheetHost(
        payload = pendingPortraitShare.takeIf { isCurrentPage },
        glassSource = portraitShareMenuSource,
        onDismiss = { pendingPortraitShare = null },
    )
    var portraitPlayerMenuVisible by remember(bvid) { mutableStateOf(false) }
    var showPortraitExternalPlaylist by rememberSaveable(bvid) { mutableStateOf(false) }
    val portraitExternalPlaylist by PlaylistManager.playlist.collectAsStateWithLifecycle()
    val portraitHasExternalPlaylist by PlaylistManager.isExternalPlaylist.collectAsStateWithLifecycle()
    var portraitEngagementModalVisible by remember(bvid) { mutableStateOf(false) }
    val portraitAnyMenuVisible = pendingPortraitShare != null || portraitPlayerMenuVisible || showPortraitExternalPlaylist || portraitEngagementModalVisible
    val portraitViewPoints by viewModel.viewPoints.collectAsStateWithLifecycle()
    val portraitPbpData by viewModel.pbpProgressData.collectAsStateWithLifecycle()
    val portraitDanmakuScope = DanmakuSettingsScope.LANDSCAPE
    val portraitDanmakuSettings by remember(context, portraitDanmakuScope) {
        SettingsManager.getDanmakuSettings(context, portraitDanmakuScope)
    }.collectAsStateWithLifecycle(initialValue = DanmakuSettings())
    val portraitCodec by viewModel.videoCodecPreference.collectAsStateWithLifecycle()
    val portraitSecondCodec by viewModel.videoSecondCodecPreference.collectAsStateWithLifecycle()
    val portraitPlayMode by PlaylistManager.playMode.collectAsStateWithLifecycle()
    val portraitPbpRidgeSamples = remember(portraitPbpData, exoPlayer.duration) {
        portraitPbpData?.let {
            com.android.purebilibili.feature.video.progress.buildPbpRidgeSamples(
                it, exoPlayer.duration.coerceAtLeast(0L)
            )
        }.orEmpty()
    }
    val subtitleAutoPreference = portraitPlayerSettings.subtitleAutoPreference
    var keepPortraitPagerAwake by remember(exoPlayer) {
        mutableStateOf(
            shouldKeepVideoPlaybackAwake(
                playWhenReady = exoPlayer.playWhenReady,
                isPlaying = exoPlayer.isPlaying,
                playbackState = exoPlayer.playbackState
            )
        )
    }
    var currentVideoAspect by remember(bvid, currentPlayingBvid, knownVideoAspectRatio) {
        mutableFloatStateOf(
            resolvePortraitInitialVideoAspectRatio(
                itemBvid = bvid,
                currentPlayingBvid = currentPlayingBvid.takeIf {
                    shouldSkipPortraitReloadForCurrentMedia(
                        currentPlayingBvid, bvid, exoPlayer.currentMediaItem?.mediaId,
                        currentPlayingCid, currentPlayingCid,
                    )
                },
                playerVideoWidth = exoPlayer.videoSize.width,
                playerVideoHeight = exoPlayer.videoSize.height,
                knownVideoAspectRatio = knownVideoAspectRatio
            )
        )
    }
    
    DisposableEffect(exoPlayer, bvid, isCurrentPage, currentPlayingBvid, currentPlayingCid, knownVideoAspectRatio) {
        fun updateAwakeState() {
            keepPortraitPagerAwake = shouldKeepVideoPlaybackAwake(
                playWhenReady = exoPlayer.playWhenReady,
                isPlaying = exoPlayer.isPlaying,
                playbackState = exoPlayer.playbackState
            )
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying_: Boolean) {
                isPlaying = isPlaying_
                updateAwakeState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateAwakeState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updateAwakeState()
            }


            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (isCurrentPage && shouldSkipPortraitReloadForCurrentMedia(
                        currentPlayingBvid, bvid, exoPlayer.currentMediaItem?.mediaId,
                        currentPlayingCid, currentPlayingCid,
                    ) && videoSize.width > 0 && videoSize.height > 0
                ) {
                    currentVideoAspect = resolvePortraitRuntimeVideoAspectRatio(
                        knownVideoAspectRatio = knownVideoAspectRatio,
                        playerVideoWidth = videoSize.width,
                        playerVideoHeight = videoSize.height
                    )
                }
            }
        }
        exoPlayer.addListener(listener)
        updateAwakeState()
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // [逻辑] 只有当播放器正在播放当前视频时，才显示 PlayerView
    val isPlayerReadyForThisVideo = bvid == currentPlayingBvid
    val pageVideoSize = if (isCurrentPage && shouldSkipPortraitReloadForCurrentMedia(
            currentPlayingBvid, bvid, exoPlayer.currentMediaItem?.mediaId,
            currentPlayingCid, currentPlayingCid,
        )
    ) exoPlayer.videoSize else VideoSize.UNKNOWN
    val shouldKeepPortraitPagerItemAwake = isPortraitPlaybackAllowed &&
        keepPortraitPagerAwake &&
        isPlayerReadyForThisVideo
    val snapshotCid = if (isPlayerReadyForThisVideo && currentPlayingCid > 0L) {
        currentPlayingCid
    } else {
        0L
    }
    val activeAid = resolvePortraitActiveAid(
        isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
        itemAid = itemAid,
        currentPlayingAid = currentPlayingAid
    )
    var portraitComposerVisible by rememberSaveable(bvid, snapshotCid) { mutableStateOf(false) }
    var portraitComposerSending by remember(bvid, snapshotCid) { mutableStateOf(false) }
    var portraitComposerText by rememberSaveable(bvid, snapshotCid) { mutableStateOf("") }
    var portraitComposerAttention by rememberSaveable(bvid, snapshotCid) { mutableStateOf(false) }
    var portraitComposerColor by rememberSaveable(bvid, snapshotCid) { mutableIntStateOf(16777215) }
    var portraitComposerMode by rememberSaveable(bvid, snapshotCid) { mutableIntStateOf(1) }
    var portraitComposerFontSize by rememberSaveable(bvid, snapshotCid) { mutableIntStateOf(25) }
    DisposableEffect(exoPlayer, portraitComposerVisible, bvid, snapshotCid) {
        val mediaId = exoPlayer.currentMediaItem?.mediaId
        val resumePlayback = portraitComposerVisible && isCurrentPage && exoPlayer.isPlaying
        if (portraitComposerVisible && isCurrentPage) exoPlayer.pause()
        onDispose {
            if (resumePlayback && exoPlayer.currentMediaItem?.mediaId == mediaId) {
                exoPlayer.play()
            }
        }
    }

    LaunchedEffect(
        playerViewRef,
        isCurrentPage,
        isPlayerReadyForThisVideo,
        currentPlayingBvid,
        exoPlayer.videoSize,
        enhancement.pipelineActive,
    ) {
        if (enhancement.pipelineActive) return@LaunchedEffect
        val view = playerViewRef ?: return@LaunchedEffect
        if (!shouldRebindSharedPlayerSurfaceOnAttach(
                isCurrentPage = isCurrentPage,
                isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
                hasPlayerView = true,
                videoWidth = exoPlayer.videoSize.width,
                videoHeight = exoPlayer.videoSize.height
            )
        ) {
            return@LaunchedEffect
        }

        // Force the shared player to hand over its surface to the portrait pager view.
        rebindPlayerSurfaceIfNeeded(playerView = view, player = exoPlayer)
    }

    DisposableEffect(
        lifecycleOwner,
        danmakuManager,
        exoPlayer,
        playerViewRef,
        isCurrentPage,
        isPlayerReadyForThisVideo,
        enhancement.pipelineActive,
    ) {
        var hasObservedHostPause = false
        var recoveryJob: kotlinx.coroutines.Job? = null
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                hasObservedHostPause = true
                recoveryJob?.cancel()
                return@LifecycleEventObserver
            }
            if (event != androidx.lifecycle.Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            // Registering a new observer immediately delivers the current lifecycle state.
            // Surface/page changes are handled above and must not trigger foreground recovery.
            if (!hasObservedHostPause) return@LifecycleEventObserver
            hasObservedHostPause = false
            val view = playerViewRef
            if (enhancement.pipelineActive) return@LifecycleEventObserver
            if (
                !shouldRecoverPortraitPagerSurfaceOnResume(
                    isCurrentPage = isCurrentPage,
                    isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
                    hasPlayerView = view != null
                )
            ) {
                return@LifecycleEventObserver
            }

            view?.let { playerView ->
                rebindPlayerSurfaceIfNeeded(playerView = playerView, player = exoPlayer)
            }
            recoveryJob?.cancel()
            recoveryJob = scope.launch {
                delay(FOREGROUND_SURFACE_RECOVERY_DELAY_MS)
                val retryView = playerViewRef ?: return@launch
                if (enhancement.pipelineActive) return@launch
                if (
                    !shouldRecoverPortraitPagerSurfaceOnResume(
                        isCurrentPage = isCurrentPage,
                        isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
                        hasPlayerView = true
                    )
                ) {
                    return@launch
                }

                rebindPlayerSurfaceIfNeeded(playerView = retryView, player = exoPlayer)
                if (
                    shouldKickPlaybackAfterSurfaceRecovery(
                        playWhenReady = exoPlayer.playWhenReady,
                        isPlaying = exoPlayer.isPlaying,
                        playbackState = exoPlayer.playbackState
                    )
                ) {
                    exoPlayer.play()
                }
                danmakuManager.recoverAfterForeground(
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    playWhenReady = exoPlayer.playWhenReady,
                    playbackState = exoPlayer.playbackState,
                    preserveTimeline = true
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            recoveryJob?.cancel()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val title = if (item is ViewInfo) item.title else (item as RelatedVideo).title
    val cover = if (item is ViewInfo) item.pic else (item as RelatedVideo).pic
    val seedAuthorName = if (item is ViewInfo) item.owner.name else (item as RelatedVideo).owner.name
    val seedAuthorFace = if (item is ViewInfo) item.owner.face else (item as RelatedVideo).owner.face
    val seedAuthorMid = if (item is ViewInfo) item.owner.mid else (item as RelatedVideo).owner.mid

    // 提取时长
    val initialDuration = if (item is RelatedVideo) {
        item.duration * 1000L
    } else if (item is ViewInfo) {
        (item.pages.firstOrNull()?.duration ?: 0L) * 1000L
    } else {
        0L
    }

    // 互动状态
    var showCommentSheet by remember(bvid) { mutableStateOf(false) }
    var showDetailSheet by remember(bvid) { mutableStateOf(false) }
    var detailSheetTransitionActive by remember(bvid) { mutableStateOf(false) }
    var showUpPreview by remember(bvid) { mutableStateOf(false) }
    var commentSheetVisibilityProgress by remember(bvid) { mutableFloatStateOf(0f) }
    var upPreviewVisibilityProgress by remember(bvid) { mutableFloatStateOf(0f) }
    val portraitCommentStateHandle = commentViewModel.commentState.collectAsStateWithLifecycle()
    val subReplyState by commentViewModel.subReplyState.collectAsStateWithLifecycle()
    var portraitPageWidthPx by remember { mutableIntStateOf(0) }
    var portraitPageHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val isVerticalContent = currentVideoAspect < 1f
    val portraitPagerFillContainer = resolvePortraitPagerFillContainer(
        aspectRatio = aspectRatio,
        isVerticalContent = isVerticalContent
    )
    val portraitPagerViewportAspect = resolvePortraitPagerViewportAspect(
        aspectRatio = aspectRatio,
        currentVideoAspect = currentVideoAspect,
        isVerticalContent = isVerticalContent
    )
    val portraitPagerResizeMode = resolvePortraitPagerResizeMode(
        aspectRatio = aspectRatio,
        isVerticalContent = isVerticalContent
    )
    val portraitViewportVerticalOffsetPx = with(density) {
        resolvePortraitVideoViewportVerticalOffsetDp(
            currentVideoAspect = portraitPagerViewportAspect,
            fillContainer = portraitPagerFillContainer,
            isLandscape = portraitPageWidthPx > portraitPageHeightPx,
            liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
        ).dp.toPx()
    }
    // 评论 / UP 半屏共用上缩：取较大进度；下拉 UP 预览时 progress 下降，视频跟手回位。
    val overlaySheetExpansion = resolvePortraitOverlaySheetExpansion(
        commentVisibilityProgress = commentSheetVisibilityProgress,
        upPreviewVisibilityProgress = upPreviewVisibilityProgress,
        commentSheetHeightFraction = 0.60f,
        upPreviewSheetHeightFraction = UP_PREVIEW_SHEET_HEIGHT_FRACTION,
    )
    // UP 预览：缩放贴合顶区可用高度（对齐官方上浮幅度）；评论仍用固定 0.58。
    val upPreviewDominatesOverlay =
        upPreviewVisibilityProgress >= commentSheetVisibilityProgress &&
            upPreviewVisibilityProgress > 0.001f
    val commentExpansionTransform = resolvePortraitCommentPlayerTransform(
        commentVisibilityProgress = overlaySheetExpansion.progress,
        containerWidthPx = portraitPageWidthPx,
        containerHeightPx = portraitPageHeightPx,
        currentVideoAspect = portraitPagerViewportAspect,
        viewportVerticalOffsetPx = portraitViewportVerticalOffsetPx,
        fillContainer = portraitPagerFillContainer,
        commentSheetHeightFraction = overlaySheetExpansion.sheetHeightFraction,
        fitToAvailableBand = upPreviewDominatesOverlay,
    )
    val danmakuInputVisible by viewModel.showDanmakuInputDialog.collectAsStateWithLifecycle()
    val commentInputVisible by viewModel.showCommentDialog.collectAsStateWithLifecycle()
    // UP 预览 / 简介半屏打开时必须关掉页级 pointerInput。
    // 父层 awaitFirstDown(requireUnconsumed=false) 会在整段单指滑动中占住手势循环，
    // LazyVerticalGrid 收不到跟手滚动（评论半屏靠 playerGesturesEnabled=false 才正常）。
    val playerGesturesEnabled =
        commentExpansionTransform.playerGesturesEnabled &&
            !showCommentSheet && !subReplyState.visible && !showUpPreview &&
            !showDetailSheet && !detailSheetTransitionActive && !portraitAnyMenuVisible && !portraitScreenLocked && !portraitComposerVisible &&
            !danmakuInputVisible && !commentInputVisible

    LaunchedEffect(isCurrentPage, bvid) {
        if (!isCurrentPage) {
            showCommentSheet = false
            showDetailSheet = false
            detailSheetTransitionActive = false
            showUpPreview = false
            pendingPortraitShare = null
            showPortraitExternalPlaylist = false
            portraitComposerVisible = false
            commentSheetVisibilityProgress = 0f
            upPreviewVisibilityProgress = 0f
            onCommentOverlayActiveChange(false)
            onUpPreviewActiveChange(false)
        }
    }

    LaunchedEffect(
        isCurrentPage,
        showCommentSheet,
        showDetailSheet,
        detailSheetTransitionActive,
        commentInputVisible,
        danmakuInputVisible,
        subReplyState.visible,
        commentSheetVisibilityProgress,
        portraitAnyMenuVisible,
        portraitScreenLocked,
        portraitComposerVisible,
    ) {
        onCommentOverlayActiveChange(
            isCurrentPage && (
                portraitAnyMenuVisible || portraitScreenLocked || portraitComposerVisible ||
                    commentInputVisible || danmakuInputVisible ||
                    shouldBlockPortraitPagerScrollForCommentOverlay(
                        commentSheetVisible = showCommentSheet,
                        subReplyVisible = subReplyState.visible,
                        commentVisibilityProgress = commentSheetVisibilityProgress,
                        detailSheetVisible = showDetailSheet || detailSheetTransitionActive,
                    )
                )
        )
    }

    LaunchedEffect(isCurrentPage, showUpPreview, upPreviewVisibilityProgress) {
        // 收起动画中 progress 仍 >0，继续挡竖滑，避免半屏回弹时被 pager 抢走手势
        onUpPreviewActiveChange(
            isCurrentPage && (showUpPreview || upPreviewVisibilityProgress > 0.001f)
        )
    }

    // 进度状态 (从播放器获取)
    val portraitProgress = remember(bvid, snapshotCid, initialDuration, initialProgressPositionMs) {
        mutableStateOf(
            PlayerProgress(
                current = initialProgressPositionMs.coerceAtLeast(0L),
                duration = initialDuration,
                buffered = initialProgressPositionMs.coerceAtLeast(0L)
            )
        )
    }
    var progressState by portraitProgress
    val progressDuration by remember(portraitProgress) {
        derivedStateOf { portraitProgress.value.duration }
    }
    var seekSession by remember(bvid, snapshotCid, initialProgressPositionMs) {
        mutableStateOf(
            syncPlaybackSeekSession(
                state = PlaybackSeekSessionState(),
                playbackPositionMs = initialProgressPositionMs.coerceAtLeast(0L)
            )
        )
    }
    
    // 进度提交节流：避免每 200ms 把位置写回详情页巨型状态。
    var lastProgressCommitBvid by remember(bvid) { mutableStateOf<String?>(null) }
    var lastProgressCommitCid by remember(bvid) { mutableLongStateOf(0L) }
    var lastProgressCommitPositionMs by remember(bvid) { mutableLongStateOf(-1L) }
    // 将交互标志与持续变化的播放位置分开，页面只观察低频状态。
    val seekSliderMoving by remember(bvid, snapshotCid, initialProgressPositionMs) {
        derivedStateOf { seekSession.isSliderMoving }
    }
    val pendingSeekPosition by remember(bvid, snapshotCid, initialProgressPositionMs) {
        derivedStateOf { seekSession.pendingSeekPositionMs }
    }

    com.android.purebilibili.feature.video.ui.overlay.rememberVideoPlayerOverlayProgress(
        player = exoPlayer,
        bvid = bvid,
        cid = snapshotCid,
        videoDuration = initialDuration,
        controlsVisible = portraitOverlayVisible,
        samplingActive = isCurrentPage && isPortraitPlaybackAllowed &&
            isPlayerReadyForThisVideo && portraitVisualWorkActive,
        hasPendingSeekResume = pendingSeekPosition != null,
        highFrequencyProgressActive = false,
        onProgress = { sample ->
            val effectivePosition = if (sample.current == 0L &&
                progressState.current > 0L && !hasRenderedFirstFrame
            ) progressState.current else sample.current
            seekSession = syncPlaybackSeekSession(
                state = seekSession,
                playbackPositionMs = effectivePosition,
                hasPlaybackResumedAfterPendingSeek = exoPlayer.isPlaying,
                playbackEnded = exoPlayer.playbackState == Player.STATE_ENDED,
            )
            progressState = sample.copy(current = seekSession.sliderPositionMs)
            if ((exoPlayer.isPlaying || effectivePosition > 0L) &&
                shouldCommitPortraitProgressToDetailState(
                    previousBvid = lastProgressCommitBvid,
                    previousCid = lastProgressCommitCid,
                    previousPositionMs = lastProgressCommitPositionMs,
                    nextBvid = bvid,
                    nextCid = snapshotCid,
                    nextPositionMs = effectivePosition,
                )
            ) {
                lastProgressCommitBvid = bvid
                lastProgressCommitCid = snapshotCid
                lastProgressCommitPositionMs = effectivePosition
                onProgressUpdate(bvid, effectivePosition, snapshotCid, cover)
            }
        },
    )

    LaunchedEffect(
        pendingSeekPosition,
        exoPlayer.playWhenReady,
        exoPlayer.isPlaying,
        exoPlayer.playbackState,
        isCurrentPage,
        isPortraitPlaybackAllowed
    ) {
        if (!isCurrentPage || !isPortraitPlaybackAllowed) return@LaunchedEffect
        if (!shouldAttemptPlaybackRecoveryAfterSeek(
                state = seekSession,
                playWhenReady = exoPlayer.playWhenReady,
                isPlaying = exoPlayer.isPlaying,
                playbackState = exoPlayer.playbackState
            )
        ) {
            return@LaunchedEffect
        }

        delay(SEEK_PLAYBACK_RECOVERY_DELAY_MS)
        if (!shouldAttemptPlaybackRecoveryAfterSeek(
                state = seekSession,
                playWhenReady = exoPlayer.playWhenReady,
                isPlaying = exoPlayer.isPlaying,
                playbackState = exoPlayer.playbackState
            )
        ) {
            return@LaunchedEffect
        }

        if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
            exoPlayer.prepare()
        }
        exoPlayer.playWhenReady = true
        exoPlayer.play()
    }
    
    // 手势调整进度状态
    var isSeekGesture by remember { mutableStateOf(false) }
    // 中央横滑调进度的刻度触感：拖起轻振，跨过整秒时短振，快速滑动按 60ms 限频。
    val seekGestureHaptic = rememberHapticFeedback(AppHapticScene.PLAYER_SEEK)
    val seekGestureHapticPolicy = remember { SeekTickHapticPolicy() }
    var seekStartPosition by remember { mutableLongStateOf(0L) }
    var seekTargetPosition by remember { mutableLongStateOf(0L) }
    var isLongPressing by remember { mutableStateOf(false) }
    var longPressOriginPlaybackParameters by remember { mutableStateOf(PlaybackParameters.DEFAULT) }
    var effectiveLongPressSpeed by remember { mutableFloatStateOf(longPressSpeed) }
    var showLongPressSpeedFeedback by remember { mutableStateOf(false) }
    var longPressSpeedHintDismissed by remember(bvid) { mutableStateOf(false) }
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackVisible by remember { mutableStateOf(false) }
    var seekFeedbackGeneration by remember { mutableLongStateOf(0L) }
    var scale by remember(bvid) { mutableFloatStateOf(1f) }
    var panX by remember(bvid) { mutableFloatStateOf(0f) }
    var panY by remember(bvid) { mutableFloatStateOf(0f) }

    fun resetViewportTransform() {
        scale = 1f
        panX = 0f
        panY = 0f
    }

    fun applyPortraitTemporaryPlaybackParameters(parameters: PlaybackParameters) {
        // 临时倍速作用于当前竖屏页面的播放器，松手时还原原始参数。
        exoPlayer.playbackParameters = parameters
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            resetViewportTransform()
        }
    }

    LaunchedEffect(isCurrentPage, isLongPressing, longPressOriginPlaybackParameters) {
        if (shouldRestorePortraitLongPressSpeed(isLongPressing = isLongPressing, isCurrentPage = isCurrentPage)) {
            applyPortraitTemporaryPlaybackParameters(longPressOriginPlaybackParameters)
            isLongPressing = false
            showLongPressSpeedFeedback = false
        }
    }

    val playerLifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val controlsOverlayOpen = showCommentSheet || subReplyState.visible || showDetailSheet || detailSheetTransitionActive ||
        showUpPreview || commentSheetVisibilityProgress > 0.001f || upPreviewVisibilityProgress > 0.001f ||
        portraitAnyMenuVisible || portraitScreenLocked || portraitComposerVisible ||
        danmakuInputVisible || commentInputVisible
    com.android.purebilibili.core.ui.LocalNavigationBackHandler(
        enabled = isCurrentPage && portraitCleanScreen && !controlsOverlayOpen && !isInPipMode,
    ) {
        onPortraitCleanScreenChange(false)
    }
    val latestOnPortraitOverlayVisibleChange by rememberUpdatedState(onPortraitOverlayVisibleChange)
    LaunchedEffect(bvid, isCurrentPage) {
        if (isCurrentPage) latestOnPortraitOverlayVisibleChange(!portraitCleanScreen)
    }

    val latestIsLongPressing by rememberUpdatedState(isLongPressing)
    val latestLongPressOriginPlaybackParameters by rememberUpdatedState(longPressOriginPlaybackParameters)
    DisposableEffect(exoPlayer) {
        onDispose {
            if (latestIsLongPressing) {
                exoPlayer.playbackParameters = latestLongPressOriginPlaybackParameters
            }
        }
    }

    LaunchedEffect(isCurrentPage, scale, onCurrentPageScaleChange) {
        onCurrentPageScaleChange(if (isCurrentPage) scale else 1f)
    }

    // 横屏视频在竖屏页上下 letterbox 黑边：动态采样模糊（默认开，设置可关）
    val letterboxAmbientHazeEnabled = portraitPlayerSettings.letterboxAmbientHazeEnabled
    val letterboxViewportSize = remember(
        portraitPageWidthPx,
        portraitPageHeightPx,
        portraitPagerViewportAspect,
        portraitPagerFillContainer,
    ) {
        resolvePortraitVideoViewportSize(
            containerWidth = portraitPageWidthPx,
            containerHeight = portraitPageHeightPx,
            currentVideoAspect = portraitPagerViewportAspect,
            fillContainer = portraitPagerFillContainer,
        )
    }
    val letterboxBarHeightPx = resolvePortraitLetterboxBarHeightPx(
        containerHeightPx = portraitPageHeightPx,
        viewportHeightPx = letterboxViewportSize.height,
        fillContainer = portraitPagerFillContainer,
    )
    val shouldCaptureLetterboxAmbient = shouldCapturePortraitLetterboxAmbientFrame(
        isCurrentPage = isCurrentPage,
        letterboxAmbientHazeEnabled = letterboxAmbientHazeEnabled,
        letterboxBarHeightPx = letterboxBarHeightPx,
        isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
    )
    val portraitAmbient = com.android.purebilibili.feature.video.ambient.rememberPortraitAmbientPresentation(
        player = exoPlayer,
        playerView = playerViewRef,
        identity = bvid to snapshotCid,
        quality = selectedQualityId,
        width = letterboxViewportSize.width,
        height = letterboxViewportSize.height,
        enabled = shouldCaptureLetterboxAmbient && isPortraitPlaybackAllowed && !isInPipMode && !isAudioOnly,
        firstFrameReady = hasRenderedFirstFrame && isPlayerSurfaceReady,
        hdr = requiresHdrSurfaceOutput(
            currentQualityId = selectedQualityId,
            colorTransfer = exoPlayer.videoFormat?.colorInfo?.colorTransfer ?: 0,
        ),
        anime4k = enhancement.pipelineActive,
    )

    val danmakuTapCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestPortraitOverlayVisible = rememberUpdatedState(portraitOverlayVisible)
    val latestPortraitCleanScreen = rememberUpdatedState(portraitCleanScreen)
    val latestDanmakuEnabled = rememberUpdatedState(danmakuEnabled)
    val latestDoubleTapSeekEnabled = rememberUpdatedState(doubleTapSeekEnabled)
    val latestSeekForwardSeconds = rememberUpdatedState(seekForwardSeconds)
    val latestSeekBackwardSeconds = rememberUpdatedState(seekBackwardSeconds)
    val latestLongPressSpeed = rememberUpdatedState(longPressSpeed)
    val latestCurrentAudioQuality = rememberUpdatedState(currentAudioQuality)
    val latestProgressDuration = rememberUpdatedState(progressDuration)
    val latestPlayerInteractionSettings = rememberUpdatedState(playerInteractionSettings)
    val portraitGestureCallbacks = remember(exoPlayer, danmakuManager, bvid) {
        PortraitPageGestureCallbacks(
            getScale = { scale },
            setScale = { scale = it },
            getPanX = { panX },
            setPanX = { panX = it },
            getPanY = { panY },
            setPanY = { panY = it },
            resetViewportTransform = ::resetViewportTransform,
            isLongPressing = { isLongPressing },
            onCancelLongPress = {
                applyPortraitTemporaryPlaybackParameters(longPressOriginPlaybackParameters)
                isLongPressing = false
                showLongPressSpeedFeedback = false
            },
            onStartLongPress = { origin, applied ->
                longPressOriginPlaybackParameters = origin
                effectiveLongPressSpeed = applied.speed
                applyPortraitTemporaryPlaybackParameters(applied)
                isLongPressing = true
                portraitLockHaptic(HapticType.MEDIUM)
                longPressSpeedHintDismissed = false
                showLongPressSpeedFeedback = true
            },
            onToggleOverlay = onPortraitOverlayVisibleChange,
            onExitCleanScreen = { onPortraitCleanScreenChange(false) },
            onSeekCommitted = { newPos -> progressState = progressState.copy(current = newPos) },
            onSeekFeedback = { deltaSeconds ->
                val feedback = nextFullscreenSeekFeedbackEvent(
                    previousGeneration = seekFeedbackGeneration,
                    deltaSeconds = deltaSeconds,
                )
                seekFeedbackGeneration = feedback.generation
                seekFeedbackText = feedback.text
                seekFeedbackVisible = true
            },
            isSeekGestureActive = { isSeekGesture },
            setSeekGestureActive = { isSeekGesture = it },
            getSeekStartPosition = { seekStartPosition },
            setSeekStartPosition = { seekStartPosition = it },
            getSeekTargetPosition = { seekTargetPosition },
            setSeekTargetPosition = { seekTargetPosition = it },
            triggerSeekHaptic = { seekGestureHaptic(it) },
            triggerLockHaptic = { portraitLockHaptic(it) },
        )
    }
    // Overlay & Interaction state shared by the inner portrait stage and outer sheets.
    val isCurrentModelVideo = (currentUiState as? VideoPlaybackUiState.Success)?.info?.bvid == bvid
    val hasMatchingPageState = isCurrentModelVideo && currentSuccess?.info?.cid == snapshotCid
    com.android.purebilibili.feature.video.screen.VideoOnlineCountVisibilityEffect(
        viewModel = viewModel,
        bvid = bvid,
        cid = snapshotCid,
        pageActive = isCurrentPage && isPortraitPlaybackAllowed && !isInPipMode,
    )
    val portraitSupplementActive = isCurrentPage && isPortraitPlaybackAllowed &&
        portraitVisualWorkActive && !isInPipMode && !entryTransitionInProgress
    val portraitPagePlayerInfo by rememberPortraitPagePlayerInfo(
        bvid = bvid,
        cid = snapshotCid,
        active = portraitSupplementActive,
        hasSharedState = hasMatchingPageState,
    )
    val portraitHeatmapEnabled = portraitPlayerSettings.portraitHeatmapEnabled
    val portraitPagePbp by rememberPortraitPageHeatmap(
        bvid = bvid,
        cid = snapshotCid,
        aid = activeAid,
        durationMs = progressDuration,
        enabled = portraitHeatmapEnabled,
        active = portraitSupplementActive,
        hasSharedState = hasMatchingPageState,
    )
    val displayedPortraitRidgeSamples = remember(
        portraitHeatmapEnabled, hasMatchingPageState, portraitPbpRidgeSamples, portraitPagePbp, progressDuration,
    ) {
        if (!portraitHeatmapEnabled) emptyList()
        else if (hasMatchingPageState) portraitPbpRidgeSamples else portraitPagePbp?.let {
            com.android.purebilibili.feature.video.progress.buildPbpRidgeSamples(it, progressDuration)
        }.orEmpty()
    }
    val stat = if (item is ViewInfo) item.stat else (item as RelatedVideo).stat
    val fallbackDetailInfo = when (item) {
        is ViewInfo -> item
        is RelatedVideo -> toViewInfoForPortraitDetail(item)
        else -> null
    }
    val portraitDetailInfo = resolvePortraitDetailInfo(
        targetBvid = bvid,
        sharedInfo = currentSuccess?.info,
        loadedPageInfo = loadedPageInfo,
        fallbackInfo = fallbackDetailInfo,
    )
    val engagementSubject = VideoSubjectSnapshot(
        bvid = bvid, cid = snapshotCid, aid = activeAid, ownerMid = seedAuthorMid,
        title = title, coverUrl = cover, durationMs = progressDuration, generation = 1L,
    )
    val loadedEngagementInfo = loadedPageInfo?.takeIf {
        resolvePortraitEngagementSubject(engagementSubject, it) != null
    } ?: currentSuccess?.info?.takeIf {
        resolvePortraitEngagementSubject(engagementSubject, it) != null
    }
    val initialOwnerMid = loadedEngagementInfo?.owner?.mid?.takeIf { it > 0L }
        ?: portraitDetailInfo?.owner?.mid?.takeIf { it > 0L } ?: seedAuthorMid
    val pageEngagementSession = engagementOwner.session(
        subject = VideoSubjectSnapshot(
            bvid = bvid, cid = snapshotCid, aid = activeAid, ownerMid = initialOwnerMid,
            title = title, coverUrl = cover, durationMs = progressDuration,
            isRepost = loadedEngagementInfo?.isRepost ?: false, generation = 1L,
        ),
        info = loadedEngagementInfo,
        seed = resolvePortraitEngagementSeed(
            bvid, activeAid, initialOwnerMid, loadedEngagementInfo, stat,
            currentSuccess, knownEngagementState,
        ),
    )
    val pageEngagementState by pageEngagementSession.model.uiState.collectAsStateWithLifecycle()
    val ownerInfo = pageEngagementSession.resolvedInfo ?: portraitDetailInfo
    val authorName = ownerInfo?.owner?.name?.takeIf { it.isNotBlank() } ?: seedAuthorName
    val authorFace = ownerInfo?.owner?.face?.takeIf { it.isNotBlank() } ?: seedAuthorFace
    val authorMid = pageEngagementState.subject?.ownerMid?.takeIf { it > 0L } ?: initialOwnerMid
    val engagementToken = remember(bvid, snapshotCid) { Any() }
    val latestEngagementNavigation by rememberUpdatedState(onRequestVideoChange)
    DisposableEffect(engagementOwner, pageEngagementSession, engagementToken, isCurrentPage) {
        if (isCurrentPage) {
            engagementOwner.activate(engagementToken, pageEngagementSession) { latestEngagementNavigation(it) }
        }
        onDispose { engagementOwner.deactivate(engagementToken) }
    }
    // UP 主弹幕徽章：详情就绪后绑定 owner.mid（seed 进场无 owner 时会在详情回填后重新绑定）
    LaunchedEffect(danmakuManager, authorMid) {
        danmakuManager.bindUpOwnerMid(authorMid)
    }
    val followStatusKnown = pageEngagementState.followStatusKnown
    val isFollowing = pageEngagementState.isFollowing
    val detailVideoList = remember(bvid, watchLaterVideos, recommendationVideos) {
        buildPortraitDetailVideoList(
            currentBvid = bvid,
            watchLaterVideos = watchLaterVideos,
            recommendationVideos = recommendationVideos
        )
    }
    val upPreviewSeedVideos = remember(detailVideoList.videos, authorMid, bvid) {
        detailVideoList.videos.filter { candidate ->
            candidate.owner.mid == authorMid && candidate.bvid != bvid
        }
    }
    val toggleDanmaku: () -> Unit = {
        val next = !danmakuEnabled
        danmakuManager.isEnabled = next
        scope.launch {
            SettingsManager.setDanmakuEnabled(
                context,
                next,
                portraitDanmakuScope
            )
        }
        Unit
    }
    val canHandlePortraitInteraction = shouldHandlePortraitVideoInteraction(
        isCurrentPage = isCurrentPage,
        aid = activeAid,
        bvid = bvid
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
    val useSideCommentPane = stageWidthDp != null && maxWidth >= 840.dp && !isInPipMode
    val sideCommentWidthDp = (maxWidth * 0.36f).coerceIn(360.dp, 440.dp)
    val stageShiftXState = animateDpAsState(
        targetValue = if (useSideCommentPane && showCommentSheet) -(sideCommentWidthDp / 2f) else 0.dp,
        animationSpec = tween(durationMillis = 240),
        label = "portraitStageShiftX",
    )
    Box(
        modifier = (if (stageWidthDp != null) {
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(stageShiftXState.value.roundToPx(), 0) }
                .width(stageWidthDp.dp)
                .fillMaxHeight()
        } else {
            Modifier.fillMaxSize()
        })
            .background(Color.Black)
            .onGloballyPositioned {
                danmakuTapCoordinates.value = it
                val bounds = it.boundsInWindow()
                val rootBounds = it.findRootCoordinates().boundsInWindow()
                portraitPageInViewport = it.isAttached && bounds.width > 0f && bounds.height > 0f &&
                    bounds.overlaps(rootBounds)
            }
            .onSizeChanged { size ->
                portraitPageWidthPx = size.width
                portraitPageHeightPx = size.height
            }
            .portraitPageGestures(
                context = context,
                bvid = bvid,
                exoPlayer = exoPlayer,
                danmakuManager = danmakuManager,
                danmakuTapCoordinates = danmakuTapCoordinates,
                isCurrentPage = isCurrentPage,
                playerGesturesEnabled = playerGesturesEnabled,
                seekGestureHapticPolicy = seekGestureHapticPolicy,
                overlayVisibleState = latestPortraitOverlayVisible,
                cleanScreenState = latestPortraitCleanScreen,
                danmakuEnabledState = latestDanmakuEnabled,
                doubleTapSeekEnabledState = latestDoubleTapSeekEnabled,
                seekForwardSecondsState = latestSeekForwardSeconds,
                seekBackwardSecondsState = latestSeekBackwardSeconds,
                longPressSpeedState = latestLongPressSpeed,
                currentAudioQualityState = latestCurrentAudioQuality,
                progressDurationState = latestProgressDuration,
                playerInteractionSettingsState = latestPlayerInteractionSettings,
                callbacks = portraitGestureCallbacks,
            )
    ) {
        // The portrait pager hides system bars for immersive playback. In that state the
        // visible status-bar inset becomes zero even though the camera cutout still occupies
        // the top edge. Keep the danmaku page surface below the largest relevant safe inset.
        val pageDanmakuTopInset = with(density) {
            resolvePortraitDanmakuTopInsetPx(
                visibleStatusBarTopPx = WindowInsets.statusBars.getTop(this),
                statusBarsIgnoringVisibilityTopPx =
                    WindowInsets.statusBarsIgnoringVisibility.getTop(this),
                displayCutoutTopPx = WindowInsets.displayCutout.getTop(this),
            ).toDp()
        }

        // Record only media siblings; controls consume this source outside the recording box.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (capturePortraitPage) portraitPageBackdrop.modifier else Modifier),
        ) {
        val mediaLayerModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = commentExpansionTransform.scale
                scaleY = commentExpansionTransform.scale
                translationY = commentExpansionTransform.translationYPx
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
        val danmakuVideoViewport = resolvePortraitPlayerViewportSize(
            containerWidth = portraitPageWidthPx,
            containerHeight = portraitPageHeightPx,
            currentVideoAspect = portraitPagerViewportAspect,
            fillContainer = portraitPagerFillContainer,
            aspectRatio = aspectRatio,
            videoWidth = exoPlayer.videoSize.width,
            videoHeight = exoPlayer.videoSize.height,
            liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
        )
        val upperDanmakuRegion = resolvePortraitDanmakuUpperRegion(
            containerWidthPx = portraitPageWidthPx,
            containerHeightPx = portraitPageHeightPx,
            videoViewportWidthPx = danmakuVideoViewport.width,
            videoViewportHeightPx = danmakuVideoViewport.height,
            videoWidth = exoPlayer.videoSize.width,
            videoHeight = exoPlayer.videoSize.height,
            fitVideoInsideViewport = !portraitPagerFillContainer,
            viewportOffsetYPx = portraitViewportVerticalOffsetPx,
            safeTopPx = with(density) { pageDanmakuTopInset.roundToPx() },
            minimumHeightPx = resolveDanmakuLayerLineHeightPx(
                fontSize = resolveDanmakuTextSizePx(
                    context.resources.displayMetrics.density,
                    portraitDanmakuSettings.fontScale,
                    isFullscreen = true,
                ),
                lineHeightMultiplier = portraitDanmakuSettings.lineHeight,
            ),
            videoScale = scale,
            videoTranslationYPx = panY,
        )
        val danmakuSurfaceMode = resolvePortraitDanmakuSurfaceMode(
            upperRegionAvailable = upperDanmakuRegion != null,
            displayAreaMode = portraitDanmakuDisplayAreaMode
        )
        val viewportTransformModifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = panX
                translationY = panY
            }


        // 留白与横屏共用动态环境光，仅在视频边缘外扩散，不再放大整帧模糊填充。
        if (portraitAmbient != null) {
            com.android.purebilibili.feature.video.ambient.PlayerAmbientGlow(
                presentation = portraitAmbient,
                fullscreen = true,
                modifier = mediaLayerModifier.zIndex(0f),
            )
        }

        // Wait for persisted surface settings while retaining format-aware HDR selection.
        if (isCurrentPage && isPlayerReadyForThisVideo && isPlayerSurfaceReady && !isAudioOnly) {
            val preferTexture = com.android.purebilibili.feature.video.ui.section.shouldUsePlayerPageTexture(
                glassSupported = portraitGlassSupported,
                retainedGlassTexture = hasUsedPortraitGlassTexture,
                hdr = surfaceHdrRequired,
                existingTextureRequest = shouldUseTextureSurfaceForFlip(
                    isFlippedHorizontal = portraitFlippedHorizontal && !surfaceHdrRequired,
                    isFlippedVertical = portraitFlippedVertical && !surfaceHdrRequired,
                    navigationTransformEnabled = useTextureSurfaceForNavigation,
                    requiresHdrSurfaceOutput = surfaceHdrRequired,
                ),
            )
            PortraitVideoViewportContainer(
                currentVideoAspect = portraitPagerViewportAspect,
                modifier = mediaLayerModifier,
                fillContainer = portraitPagerFillContainer,
                aspectRatio = aspectRatio,
                videoWidth = pageVideoSize.width,
                videoHeight = pageVideoSize.height,
            ) {
                key(currentPlayingBvid, currentPlayingCid, bvid, preferTexture) {
                    Box(
                        modifier = viewportTransformModifier
                    ) {
                        com.android.purebilibili.feature.video.ui.components.PortraitVideoEnhancementSurface(
                            session = enhancement,
                            preferTexture = preferTexture,
                            resizeMode = portraitPagerResizeMode,
                            keepScreenOn = shouldKeepPortraitPagerItemAwake,
                            flippedHorizontal = portraitFlippedHorizontal && !surfaceHdrRequired,
                            flippedVertical = portraitFlippedVertical && !surfaceHdrRequired,
                            onPlayerViewChanged = { view -> playerViewRef = view },
                            modifier = Modifier.fillMaxSize(),
                        )

                        if (shouldComposePortraitDanmakuOverlay(
                                danmakuEnabled = danmakuEnabled && pipDanmakuVisible && !portraitCleanScreen,
                                surfaceMode = danmakuSurfaceMode,
                                expectedMode = PortraitDanmakuSurfaceMode.VideoViewport,
                                isCurrentPage = isCurrentPage,
                                isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
                            )
                        ) {
                            PortraitDanmakuOverlay(
                                danmakuManager = danmakuManager,
                                videoWidth = exoPlayer.videoSize.width,
                                videoHeight = exoPlayer.videoSize.height,
                                resizeMode = playerViewRef?.resizeMode ?: portraitPagerResizeMode,
                                // 沉浸时挖孔仍占顶部边缘：与 Page 表面共用同一安全 inset，
                                // 首行弹幕不得被屏幕边缘/挖孔裁切。
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = pageDanmakuTopInset)
                            )
                        }
                    }
                }
            }
        }

        if (
            shouldComposePortraitDanmakuOverlay(
                danmakuEnabled = danmakuEnabled && pipDanmakuVisible && !portraitCleanScreen,
                surfaceMode = danmakuSurfaceMode,
                expectedMode = PortraitDanmakuSurfaceMode.Page,
                isCurrentPage = isCurrentPage,
                isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
            )
        ) {
            val region = requireNotNull(upperDanmakuRegion)
            Box(modifier = mediaLayerModifier) {
                PortraitDanmakuOverlay(
                    danmakuManager = danmakuManager,
                    videoWidth = exoPlayer.videoSize.width,
                    videoHeight = exoPlayer.videoSize.height,
                    resizeMode = playerViewRef?.resizeMode ?: portraitPagerResizeMode,
                    fillDisplayArea = true,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = with(density) { region.topPx.toDp() })
                        .fillMaxWidth()
                        .height(with(density) { region.heightPx.toDp() }),
                )
            }
        }

        if (
            shouldComposePortraitDanmakuOverlay(
                danmakuEnabled = danmakuEnabled && pipDanmakuVisible && !portraitCleanScreen,
                surfaceMode = danmakuSurfaceMode,
                expectedMode = PortraitDanmakuSurfaceMode.FullScreen,
                isCurrentPage = isCurrentPage,
                isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
            )
        ) {
            Box(modifier = mediaLayerModifier) {
                PortraitDanmakuOverlay(
                    danmakuManager = danmakuManager,
                    videoWidth = exoPlayer.videoSize.width,
                    videoHeight = exoPlayer.videoSize.height,
                    resizeMode = playerViewRef?.resizeMode ?: portraitPagerResizeMode,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = pageDanmakuTopInset),
                )
            }
        }

        // Decoder readiness alone is insufficient for enhanced GL output. Hand off only
        // after this page's actual output has drawn the matching video's first frame.
        val showCover = !isPlayerSurfaceReady || shouldShowPortraitCover(
            isLoading = isLoading,
            isCurrentPage = isCurrentPage,
            isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
            // 当前输出已经出画时，不再依赖另一个监听器的首帧事件到达顺序。
            hasRenderedFirstFrame = hasRenderedFirstFrame || enhancement.hasRenderedOutputFrame,
            hasRenderedSurfaceFrame = enhancement.hasRenderedOutputFrame,
        )
        
        if (showCover) {
            PortraitVideoCoverLayer(
                cover = cover,
                currentVideoAspect = currentVideoAspect,
                viewportAspect = portraitPagerViewportAspect,
                fillContainer = portraitPagerFillContainer,
                aspectRatio = aspectRatio,
                resizeMode = portraitPagerResizeMode,
                videoWidth = pageVideoSize.width,
                videoHeight = pageVideoSize.height,
                showBuffering = isLoading && isCurrentPage && !portraitOverlayVisible,
                modifier = mediaLayerModifier,
            )
        }

        } // media backdrop source

        PortraitZoomResetPill(
            visible = isCurrentPage && !isInPipMode && scale > 1.01f,
            scale = scale,
            onReset = {
                portraitLockHaptic(HapticType.LIGHT)
                resetViewportTransform()
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // 滑动进度提示
        if (isSeekGesture && progressDuration > 0) {
            val gestureVideoshotData = currentSuccess?.videoshotData
            if (gestureVideoshotData != null && gestureVideoshotData.isValid) {
                val previewConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
                val gesturePreviewSize = remember(
                    gestureVideoshotData.img_x_size,
                    gestureVideoshotData.img_y_size,
                    previewConfiguration.screenWidthDp,
                    previewConfiguration.screenHeightDp,
                    currentVideoAspect,
                ) {
                    com.android.purebilibili.feature.video.ui.components.resolveCompactSeekPreviewSize(
                        sourceWidthPx = gestureVideoshotData.img_x_size,
                        sourceHeightPx = gestureVideoshotData.img_y_size,
                        screenWidthDp = previewConfiguration.screenWidthDp,
                        // 使用真实视频宽比：横屏内容预览不再被强行裁成 9:16
                        videoAspectRatio = currentVideoAspect,
                        containerHeightDp = previewConfiguration.screenHeightDp,
                    )
                }
                val previewWidthPx = with(density) { gesturePreviewSize.widthDp.dp.toPx() }
                val previewProgress = (
                    seekTargetPosition / progressDuration.toFloat()
                ).coerceIn(0f, 1f)
                val previewOffsetX =
                    com.android.purebilibili.feature.video.ui.components.resolveSeekPreviewBubbleOffsetPx(
                        placement = com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement.Anchored,
                        offsetX = portraitPageWidthPx * previewProgress,
                        containerWidth = portraitPageWidthPx.toFloat(),
                        bubbleWidthPx = previewWidthPx
                    )
                val previewBottomOffsetPx = with(density) {
                    (if (previewConfiguration.screenHeightDp < 320) 64.dp else 120.dp).roundToPx()
                }
                com.android.purebilibili.feature.video.ui.components.CompactSeekPreview(
                    videoshotData = gestureVideoshotData,
                    targetPositionMs = seekTargetPosition,
                    durationMs = progressDuration,
                    videoAspectRatio = currentVideoAspect,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset {
                            androidx.compose.ui.unit.IntOffset(
                                x = previewOffsetX,
                                y = -previewBottomOffsetPx
                            )
                        }
                )
            } else {
                val targetTimeText = FormatUtils.formatDuration(seekTargetPosition)
                val totalTimeText = FormatUtils.formatDuration(progressDuration)
                val deltaMs = seekTargetPosition - seekStartPosition
                val deltaText = if (deltaMs >= 0) {
                    "+${FormatUtils.formatDuration(deltaMs)}"
                } else {
                    "-${FormatUtils.formatDuration(-deltaMs)}"
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            AppShapes.container(ContainerLevel.Card)
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AppText(
                        text = "$targetTimeText / $totalTimeText",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AppText(
                        text = deltaText,
                        color = com.android.purebilibili.core.ui.MediaContrastPalette.Foreground,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        val partResumeNotice = currentSuccess?.partResumeNotice
        if (partResumeNotice != null && partResumeNotice.bvid == bvid &&
            isCurrentPage && isPlayerReadyForThisVideo && isPlayerSurfaceReady &&
            hasRenderedFirstFrame && !isLoading &&
            !isInPipMode && !portraitScreenLocked
        ) {
            com.android.purebilibili.feature.video.ui.section.PartResumeNoticeChip(
                notice = partResumeNotice,
                onJump = viewModel::jumpToLastPlayedPart,
                onDismiss = viewModel::dismissPartResumeNotice,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (pageDanmakuTopInset + 56.dp).coerceAtLeast(72.dp))
                    .zIndex(118f),
            )
        }

        // 双击左右区快进/后退视觉反馈
        LaunchedEffect(seekFeedbackGeneration) {
            if (seekFeedbackGeneration > 0L) {
                delay(800)
                seekFeedbackVisible = false
            }
        }
        AnimatedVisibility(
            visible = seekFeedbackVisible && isCurrentPage && !isSeekGesture,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(animationSpec = tween(120)),
            exit = fadeOut(animationSpec = tween(180))
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.Black.copy(alpha = 0.75f), AppShapes.container(ContainerLevel.Floating)),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    text = seekFeedbackText.orEmpty(),
                    color = com.android.purebilibili.core.ui.MediaContrastPalette.Foreground,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 长按倍速提示（透明背景 + 循环箭头动画，位于视频上方）
        val longPressFeedbackVisible = shouldShowLongPressSpeedFeedback(
            isLongPressing = isLongPressing,
            isPlaybackSurfaceActive = isCurrentPage,
            hintDismissed = longPressSpeedHintDismissed,
            hintHidden = longPressSpeedHintHidden,
        )
        val longPressMotionEnabled = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
            !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
        AnimatedVisibility(
            visible = longPressFeedbackVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 状态栏可见（关闭沉浸式/瞬态显示）时随系统栏 inset 下移，避免被预留位遮挡。
                .padding(top = (pageDanmakuTopInset + 16.dp).coerceAtLeast(48.dp)),
            enter = if (longPressMotionEnabled) {
                fadeIn(animationSpec = tween(200)) + slideInVertically(initialOffsetY = { -it })
            } else androidx.compose.animation.EnterTransition.None,
            exit = if (longPressMotionEnabled) {
                fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { -it })
            } else androidx.compose.animation.ExitTransition.None,
        ) {
            val animationGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate()
            val arrowAlphas = if (longPressFeedbackVisible && animationGate.active) {
                val infiniteTransition = rememberInfiniteTransition(label = "fast_forward_portrait")
                val arrow1Alpha = infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 900
                            0.3f at 0
                            1.0f at 300
                            0.3f at 600
                            0.3f at 900
                        },
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "arrow1"
                )
                val arrow2Alpha = infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 900
                            0.3f at 0
                            0.3f at 300
                            1.0f at 600
                            0.3f at 900
                        },
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "arrow2"
                )
                val arrow3Alpha = infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 900
                            0.3f at 0
                            0.3f at 600
                            1.0f at 900
                        },
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "arrow3"
                )
                listOf(arrow1Alpha, arrow2Alpha, arrow3Alpha)
            } else {
                val staticAlpha = rememberUpdatedState(0.65f)
                listOf(staticAlpha, staticAlpha, staticAlpha)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                // 透明度设置作用于整个提示（箭头 + 文字 + 关闭按钮）。
                modifier = Modifier
                    .then(animationGate.modifier)
                    .graphicsLayer { alpha = longPressSpeedHintAlpha }
                    .padding(
                        horizontal = 8.dp * longPressSpeedHintScale,
                        vertical = 5.dp * longPressSpeedHintScale
                    )
            ) {
                arrowAlphas.forEach { alpha ->
                    Canvas(
                        modifier = Modifier.size(10.dp * longPressSpeedHintScale)
                    ) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(
                            path = path,
                            color = Color.White.copy(alpha = alpha.value)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(5.dp * longPressSpeedHintScale))
                AppText(
                    text = "${effectiveLongPressSpeed}x",
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize * longPressSpeedHintScale,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.25f),
                            offset = Offset(1f, 1f),
                            blurRadius = 2f
                        )
                    )
                )
                if (shouldShowLongPressSpeedHintCloseButton(longPressSpeedHintCloseEnabled)) {
                    AppIconButton(
                        onClick = { longPressSpeedHintDismissed = true },
                        modifier = Modifier.size(36.dp * longPressSpeedHintScale),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭倍速提示",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp * longPressSpeedHintScale),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isCurrentPage && scale > 1.05f,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 104.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            AppButton(
                onClick = {
                    resetViewportTransform()
                    onPortraitOverlayVisibleChange(true)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White
                ),
                shape = AppShapes.container(ContainerLevel.Floating)
            ) {
                AppIcon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "还原画面"
                )
                Spacer(modifier = Modifier.width(8.dp))
                AppText(
                    text = "还原画面",
                    fontWeight = FontWeight.Bold
                )
            }
        }

    val favoriteFolderMenuAnchor = com.android.purebilibili.feature.video.ui.components.rememberFavoriteFolderMenuAnchor()
    PortraitVideoEngagementUi(
        session = pageEngagementSession,
        favoriteFolderAnchor = favoriteFolderMenuAnchor,
        visible = isCurrentPage && !isInPipMode,
        onModalVisibilityChanged = { portraitEngagementModalVisible = it },
    )

    CompositionLocalProvider(
        com.android.purebilibili.feature.video.ui.components.LocalFavoriteFolderMenuAnchor provides favoriteFolderMenuAnchor,
        com.android.purebilibili.feature.video.ui.overlay.LocalPlayerPageBackdrop provides
            portraitPageBackdrop.backdrop.takeIf {
                capturePortraitPage && portraitPageBackdrop.isReady &&
                    (!isPlayerReadyForThisVideo || playerViewRef?.videoSurfaceView is android.view.TextureView)
            },
    ) {
    // 系统小窗不合成竖屏流装饰层：标题 / 互动 / 进度条 / 旋转等控件在小窗里无法操作，
    // 缩放后只会变成错误渲染，播放面本身仍在下方正常输出。
    // 设备接力入口保留在右上角操作栏，不在顶部居中叠加胶囊，避免重复及与挖孔屏冲突。
    if (!isInPipMode && !entryTransitionInProgress) {
        val portraitPlaybackRecovering by remember(
            bvid, snapshotCid, initialProgressPositionMs, exoPlayer, isCurrentPage,
            isPlaying, exoPlayer.playWhenReady, exoPlayer.playbackState,
        ) {
            derivedStateOf {
                isCurrentPage && shouldShowPlaybackRecoveryUiAfterSeek(
                    state = seekSession,
                    playWhenReady = exoPlayer.playWhenReady,
                    isPlaying = exoPlayer.isPlaying,
                    playbackState = exoPlayer.playbackState,
                )
            }
        }
        val diagnosticLoggingEnabled = portraitPlayerSettings.diagnosticLoggingEnabled
        val displayedCommentCount by remember(
            portraitCommentStateHandle, activeAid, bvid, portraitDetailInfo,
        ) {
            derivedStateOf {
                portraitCommentStateHandle.value.resolveReplyCount(
                    subject = CommentSubjectKey(activeAid),
                    detailCount = portraitDetailInfo?.takeIf { it.bvid == bvid }?.stat?.reply,
                )
            }
        }
        val portraitOverlayState = com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlayState(
                player = exoPlayer,
                title = title,
                playerDiagnosticLoggingEnabled = diagnosticLoggingEnabled && isCurrentPage &&
                    isPlayerReadyForThisVideo && isPortraitPlaybackAllowed &&
                    playerLifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
                    !isInPipMode && !entryTransitionInProgress,
                debugInfo = com.android.purebilibili.feature.video.ui.overlay.PlaybackDebugInfo(
                    firstFrame = if (hasRenderedFirstFrame) "rendered" else "",
                ),
                isVisible = isCurrentPage && resolvePortraitOverlayControlsVisible(
                    portraitOverlayVisible = portraitOverlayVisible,
                    showDetailSheet = showDetailSheet || detailSheetTransitionActive,
                    otherSheetVisible = showCommentSheet || subReplyState.visible || showUpPreview ||
                        commentSheetVisibilityProgress > 0.001f || upPreviewVisibilityProgress > 0.001f,
                ),
                isFullscreen = true,
                playerViewportSize = androidx.compose.ui.unit.IntSize(portraitPageWidthPx, portraitPageHeightPx),
                currentQualityLabel = qualityLabel,
                qualityLabels = resolvePortraitQualityMenuLabels(availableQualityIds),
                qualityIds = availableQualityIds,
                switchableQualityIds = switchableQualityIds,
                isLoggedIn = isLoggedIn,
                isVip = isVip,
                bvid = bvid,
                cid = snapshotCid,
                currentAid = activeAid,
                videoOwnerName = authorName,
                videoOwnerFace = authorFace,
                videoTitle = title,
                videoDuration = progressDuration,
                coverUrl = cover,
                currentQuality = selectedQualityId,
                currentVideoUrl = videoUrl,
                currentAudioUrl = audioUrl,
                isScreenLocked = portraitScreenLocked,
                isFlippedHorizontal = portraitFlippedHorizontal && !surfaceHdrRequired,
                isFlippedVertical = portraitFlippedVertical && !surfaceHdrRequired,
                isAudioOnly = isAudioOnly,
                currentAspectRatio = aspectRatio,
                currentAudioQuality = requestedAudioQuality,
                selectedAudioQuality = selectedAudioQuality,
                availableAudioQualities = availableAudioQualities,
                isVerticalVideo = currentVideoAspect < 1f,
                isSeekScrubbing = seekSliderMoving,
                progressSource = portraitProgress,
                progressSamplingActive = isCurrentPage && isPortraitPlaybackAllowed,
                highFrequencyProgressActive = false,
                hasPendingSeekResume = portraitPlaybackRecovering,
                // This route owns the cover/gestures; shared chrome owns playback and recovery feedback.
                isBuffering = false,
                hasRenderedFirstFrame = hasRenderedFirstFrame,
                suppressTransientOverlaysForTransition = true,
                danmakuEnabled = danmakuEnabled,
                danmakuSettingsScope = portraitDanmakuScope,
                danmakuOpacity = portraitDanmakuSettings.opacity,
                danmakuFontScale = portraitDanmakuSettings.fontScale,
                danmakuFontWeight = portraitDanmakuSettings.fontWeight,
                danmakuSpeed = portraitDanmakuSettings.speed,
                danmakuDisplayArea = portraitDanmakuSettings.displayArea,
                danmakuStrokeWidth = portraitDanmakuSettings.strokeWidth,
                danmakuLineHeight = portraitDanmakuSettings.lineHeight,
                danmakuScrollDurationSeconds = portraitDanmakuSettings.scrollDurationSeconds,
                danmakuStaticDurationSeconds = portraitDanmakuSettings.staticDurationSeconds,
                danmakuScrollFixedVelocity = portraitDanmakuSettings.scrollFixedVelocity,
                danmakuStaticToScroll = portraitDanmakuSettings.staticDanmakuToScroll,
                danmakuMassiveMode = portraitDanmakuSettings.massiveMode,
                danmakuMergeDuplicates = portraitDanmakuSettings.mergeDuplicates,
                danmakuDuplicateMergeWindowMs = portraitDanmakuSettings.duplicateMergeWindowMs,
                danmakuDuplicateMergeCountThreshold = portraitDanmakuSettings.duplicateMergeCountThreshold,
                danmakuAllowScroll = portraitDanmakuSettings.allowScroll,
                danmakuAllowTop = portraitDanmakuSettings.allowTop,
                danmakuAllowBottom = portraitDanmakuSettings.allowBottom,
                danmakuAllowColorful = portraitDanmakuSettings.allowColorful,
                danmakuAllowSpecial = portraitDanmakuSettings.allowSpecial,
                danmakuWeightFilterLevel = portraitDanmakuSettings.weightFilterLevel,
                danmakuHideInteractiveCommands = portraitDanmakuSettings.hideInteractiveCommands,
                danmakuBlockRulesRaw = portraitDanmakuSettings.blockRulesRaw,
                danmakuFullscreenPanelWidthMode = portraitDanmakuSettings.fullscreenPanelWidthMode,
                portraitDanmakuDisplayAreaMode = portraitDanmakuSettings.portraitDisplayAreaMode,
                danmakuSmartOcclusion = portraitDanmakuSettings.smartOcclusion,
                currentCodec = portraitCodec,
                availableCodecIds = availableCodecIds,
                currentSecondCodec = portraitSecondCodec,
                aiAudioInfo = aiAudioInfo,
                currentAudioLang = currentAudioLanguage,
                onlineCount = currentSuccess?.takeIf { hasMatchingPageState }?.onlineCount
                    ?.takeIf { it.isNotBlank() }
                    ?: portraitPagePlayerInfo?.onlineCount?.let { "${it}人正在看" }.orEmpty(),
                subtitleControlState = portraitSubtitleControls,
                subtitleTracks = if (hasMatchingPageState) currentSuccess?.subtitleTracks.orEmpty() else
                    com.android.purebilibili.feature.video.subtitle.mapPlayerInfoSubtitleTracks(
                        portraitPagePlayerInfo?.subtitle?.subtitles.orEmpty()
                    ),
                acquireNativeSubtitles = !hasMatchingPageState,
                nativeSubtitleTracksKnown = hasMatchingPageState || portraitPagePlayerInfo != null,
                nativeSubtitleAcquisitionActive = portraitSupplementActive && portraitPagePlayerInfo != null,
                danmakuComposerVisible = portraitComposerVisible,
                isSendingDanmakuComposer = portraitComposerSending,
                danmakuComposerInitialText = portraitComposerText,
                danmakuComposerInitialAttentionCommand = portraitComposerAttention,
                danmakuComposerInitialColor = portraitComposerColor,
                danmakuComposerInitialMode = portraitComposerMode,
                danmakuComposerInitialFontSize = portraitComposerFontSize,
                videoshotData = currentSuccess?.takeIf { hasMatchingPageState }?.videoshotData,
                viewPoints = if (hasMatchingPageState) portraitViewPoints else portraitPagePlayerInfo?.viewPoints.orEmpty(),
                pbpRidgeSamples = displayedPortraitRidgeSamples,
                pages = portraitDetailInfo?.pages.orEmpty(),
                currentPageIndex = portraitDetailInfo?.pages?.indexOfFirst { it.cid == snapshotCid }
                    ?.coerceAtLeast(0) ?: 0,
                ugcSeason = portraitDetailInfo?.ugc_season,
                relatedVideos = recommendationVideos,
                currentPlayMode = portraitPlayMode,
                isFollowed = isFollowing,
                followStatusKnown = followStatusKnown,
                isLiked = pageEngagementState.isLiked,
                likeStatusKnown = pageEngagementState.likeStatusKnown,
                isCoined = pageEngagementState.coinCount > 0,
                isFavorited = pageEngagementState.isFavorited,
                isDisliked = pageEngagementState.isDisliked,
                hasExternalPlaylist = portraitHasExternalPlaylist && portraitExternalPlaylist.isNotEmpty(),
                commentCount = displayedCommentCount?.toLong(),
                engagementCountsKnown = portraitDetailInfo?.bvid == bvid,
                likeCount = pageEngagementState.likeCount.toLong(),
                favoriteCount = pageEngagementState.favoriteCount.toLong(),
                totalCoinCount = portraitDetailInfo?.stat?.coin?.toLong() ?: 0L,
                shareCount = portraitDetailInfo?.stat?.share?.toLong() ?: 0L,
                coinCount = pageEngagementState.coinCount,
                anime4kAvailable = enhancement.available,
                anime4kEnabled = enhancement.enabled,
                anime4kBypassReason = enhancement.bypassReason,
                videoEnhancementAlgorithm = enhancement.config.algorithm,
                anime4kPreset = enhancement.config.preset,
                fsrSharpness = enhancement.config.fsrSharpness,
            )
        val portraitOverlayActions = com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlayActions(
                onToggleVisible = null,
                onEnterCleanScreen = {
                    onPortraitCleanScreenChange(true)
                    showPortraitFeedback("已清屏，轻触画面或返回恢复操作栏")
                },
                onOpenVideoDetails = { showDetailSheet = true },
                onOpenAuthorPreview = portraitDetailInfo?.takeIf { it.owner.mid > 0L }?.let {
                    { showUpPreview = true }
                },
                onQualitySelected = { index -> availableQualityIds.getOrNull(index)?.let(onQualitySelected) },
                onQualityChange = onQualitySelected,
                onAudioQualityChange = onAudioQualitySelected,
                onPlaybackSpeedChange = onPlaybackSpeedSelected,
                onAspectRatioChange = onAspectRatioChange,
                onCodecChange = onCodecSelected,
                onSecondCodecChange = onSecondCodecSelected,
                onAudioLangChange = onAudioLanguageSelected,
                onFloatingPanelVisibilityChange = { portraitPlayerMenuVisible = it },
                onDislike = {
                    if (canHandlePortraitInteraction) {
                        pageEngagementSession.toggleDislike()
                    }
                },
                onPipClick = {
                    val hostActivity = context.findActivity()
                    if (android.os.Build.VERSION.SDK_INT >= 26 && hostActivity != null &&
                        hostActivity.packageManager.hasSystemFeature(
                            android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
                        )
                    ) {
                        try {
                            val params = android.app.PictureInPictureParams.Builder()
                                .setAspectRatio(com.android.purebilibili.core.util.resolveSafeAndroidPipRational(
                                    exoPlayer.videoSize.width, exoPlayer.videoSize.height
                                )).build()
                            if (!hostActivity.enterPictureInPictureMode(params)) {
                                showPortraitFeedback("无法进入画中画，请检查系统权限")
                            }
                        } catch (_: IllegalStateException) {
                            showPortraitFeedback("暂时无法进入画中画")
                        } catch (_: IllegalArgumentException) {
                            showPortraitFeedback("设备不支持当前画中画参数")
                        }
                    } else {
                        showPortraitFeedback("设备不支持画中画")
                    }
                },
                onLockToggle = {
                    portraitLockHaptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                    portraitScreenLocked = !portraitScreenLocked
                },
                onFlipHorizontal = {
                    if (surfaceHdrRequired) {
                        showPortraitFeedback("HDR 原色输出不支持画面翻转，请先切换至普通画质")
                    } else portraitFlippedHorizontal = !portraitFlippedHorizontal
                },
                onFlipVertical = {
                    if (surfaceHdrRequired) {
                        showPortraitFeedback("HDR 原色输出不支持画面翻转，请先切换至普通画质")
                    } else portraitFlippedVertical = !portraitFlippedVertical
                },
                onAudioOnlyToggle = onAudioOnlyToggle,
                onCaptureScreenshot = {
                    val playerView = playerViewRef
                    if (playerView == null) {
                        showPortraitFeedback("截图失败：播放器未就绪")
                    } else {
                        capturePortraitScreenshot(
                            playerView,
                            exoPlayer.videoSize.width,
                            exoPlayer.videoSize.height,
                            title,
                        )
                    }
                },
                onReloadVideo = onReloadPlayback,
                onAnime4kToggle = enhancement::setEnabled,
                onVideoEnhancementAlgorithmChange = enhancement::setAlgorithm,
                onAnime4kPresetChange = enhancement::setPreset,
                onFsrSharpnessChange = enhancement::setFsrSharpness,
                menuActions = menuActions.copy(
                    onWatchLater = null,
                    onShowNotes = menuActions.onShowNotes.takeIf { hasMatchingPageState },
                    onDownloadVideo = menuActions.onDownloadVideo.takeIf { hasMatchingPageState },
                    onReplacePlaybackSources = null,
                ),
                onTripleAction = {
                    if (canHandlePortraitInteraction) pageEngagementSession.tripleAction()
                },
                onQuickFavorite = {
                    if (canHandlePortraitInteraction) pageEngagementSession.openFavoriteFolders()
                },
                onSaveCover = {
                    scope.launch {
                        val saved = com.android.purebilibili.feature.download.DownloadManager
                            .saveImageToGallery(context, cover, title)
                        showPortraitFeedback(if (saved) "封面已保存到相册" else "保存失败")
                    }
                },
                onDownloadAudio = {
                    if (audioUrl.isBlank()) {
                        showPortraitFeedback("无法获取音频地址")
                    } else {
                        viewModel.enqueueAudioDownload(
                            com.android.purebilibili.feature.download.DownloadTask(
                                bvid = bvid,
                                cid = snapshotCid,
                                title = title,
                                cover = cover,
                                ownerName = authorName,
                                ownerFace = authorFace,
                                duration = (progressDuration / 1000L).toInt(),
                                quality = 0,
                                qualityDesc = "音频",
                                videoUrl = "",
                                audioUrl = audioUrl,
                                audioBackupUrls = currentSuccess?.takeIf { hasMatchingPageState }
                                    ?.cachedDashAudios.orEmpty().downloadAudioBackupUrls(audioUrl),
                                isAudioOnly = true,
                                isVerticalVideo = currentVideoAspect < 1f,
                            )
                        )
                    }
                },
                onDanmakuOpacityChange = { scope.launch { SettingsManager.setDanmakuOpacity(context, it, portraitDanmakuScope) } },
                onDanmakuFontScaleChange = { scope.launch { SettingsManager.setDanmakuFontScale(context, it, portraitDanmakuScope) } },
                onDanmakuFontWeightChange = { scope.launch { SettingsManager.setDanmakuFontWeight(context, it, portraitDanmakuScope) } },
                onDanmakuSpeedChange = { scope.launch { SettingsManager.setDanmakuSpeed(context, it, portraitDanmakuScope) } },
                onDanmakuDisplayAreaChange = { scope.launch { SettingsManager.setDanmakuArea(context, it, portraitDanmakuScope) } },
                onDanmakuStrokeWidthChange = { scope.launch { SettingsManager.setDanmakuStrokeWidth(context, it, portraitDanmakuScope) } },
                onDanmakuLineHeightChange = { scope.launch { SettingsManager.setDanmakuLineHeight(context, it, portraitDanmakuScope) } },
                onDanmakuScrollDurationSecondsChange = {
                    scope.launch { SettingsManager.setDanmakuScrollDurationSeconds(context, it, portraitDanmakuScope) }
                },
                onDanmakuStaticDurationSecondsChange = {
                    scope.launch { SettingsManager.setDanmakuStaticDurationSeconds(context, it, portraitDanmakuScope) }
                },
                onDanmakuScrollFixedVelocityChange = {
                    scope.launch { SettingsManager.setDanmakuScrollFixedVelocity(context, it, portraitDanmakuScope) }
                },
                onDanmakuStaticToScrollChange = { scope.launch { SettingsManager.setDanmakuStaticToScroll(context, it, portraitDanmakuScope) } },
                onDanmakuMassiveModeChange = { scope.launch { SettingsManager.setDanmakuMassiveMode(context, it, portraitDanmakuScope) } },
                onDanmakuMergeDuplicatesChange = { scope.launch { SettingsManager.setDanmakuMergeDuplicates(context, it, portraitDanmakuScope) } },
                onDanmakuDuplicateMergeWindowMsChange = {
                    scope.launch { SettingsManager.setDanmakuDuplicateMergeWindowMs(context, it, portraitDanmakuScope) }
                },
                onDanmakuDuplicateMergeCountThresholdChange = {
                    scope.launch { SettingsManager.setDanmakuDuplicateMergeCountThreshold(context, it, portraitDanmakuScope) }
                },
                onDanmakuAllowScrollChange = { scope.launch { SettingsManager.setDanmakuAllowScroll(context, it, portraitDanmakuScope) } },
                onDanmakuAllowTopChange = { scope.launch { SettingsManager.setDanmakuAllowTop(context, it, portraitDanmakuScope) } },
                onDanmakuAllowBottomChange = { scope.launch { SettingsManager.setDanmakuAllowBottom(context, it, portraitDanmakuScope) } },
                onDanmakuAllowColorfulChange = { scope.launch { SettingsManager.setDanmakuAllowColorful(context, it, portraitDanmakuScope) } },
                onDanmakuAllowSpecialChange = { scope.launch { SettingsManager.setDanmakuAllowSpecial(context, it, portraitDanmakuScope) } },
                onDanmakuWeightFilterLevelChange = {
                    scope.launch { SettingsManager.setDanmakuWeightFilterLevel(context, it) }
                },
                onDanmakuHideInteractiveCommandsChange = {
                    scope.launch { SettingsManager.setDanmakuHideInteractiveCommands(context, it) }
                },
                onDanmakuBlockRulesRawChange = { scope.launch { SettingsManager.setDanmakuBlockRulesRaw(context, it, portraitDanmakuScope) } },
                onDanmakuSmartOcclusionChange = { scope.launch { SettingsManager.setDanmakuSmartOcclusion(context, it, portraitDanmakuScope) } },
                onDanmakuFullscreenPanelWidthModeChange = {
                    scope.launch { SettingsManager.setDanmakuFullscreenPanelWidthMode(context, it) }
                },
                onPortraitDanmakuDisplayAreaModeChange = {
                    scope.launch { SettingsManager.setPortraitDanmakuDisplayAreaMode(context, it) }
                },
                onBack = {
                    onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid, cover)
                    onBack()
                },
                onHomeClick = {
                    onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid, cover)
                    onHomeClick()
                },
                onToggleFullscreen = {
                    onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid, cover)
                    onExitFullscreen()
                },
                onPortraitFullscreen = {
                    onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid, cover)
                    onExitFullscreen()
                },
                onLandscapeCommentClick = { showCommentSheet = true },
                onDanmakuToggle = toggleDanmaku,
                onDanmakuInputClick = {
                    when {
                        !isLoggedIn -> showPortraitFeedback("请先登录")
                        com.android.purebilibili.data.repository.DanmakuRepository.isDanmakuServerDisabled(snapshotCid) ->
                            showPortraitFeedback("UP主已关闭弹幕")
                        isCurrentPage && snapshotCid > 0L -> portraitComposerVisible = true
                        else -> showPortraitFeedback("视频未加载")
                    }
                },
                onDismissDanmakuComposer = { portraitComposerVisible = false },
                onDanmakuComposerDraftChange = { text, attention ->
                    portraitComposerText = text
                    portraitComposerAttention = attention
                },
                onDanmakuComposerSelectionChange = { color, mode, fontSize ->
                    portraitComposerColor = color
                    portraitComposerMode = mode
                    portraitComposerFontSize = fontSize
                },
                onSendDanmakuComposer = { message, color, mode, fontSize, attention ->
                    if (!portraitComposerSending && isCurrentPage && snapshotCid > 0L) {
                        val requestCid = snapshotCid
                        val requestAid = activeAid
                        val positionMs = exoPlayer.currentPosition
                        val requestMediaId = exoPlayer.currentMediaItem?.mediaId
                        portraitComposerSending = true
                        scope.launch {
                            try {
                                val gradual = color == com.android.purebilibili.feature.video.ui.components.DANMAKU_SEND_VIP_GRADUAL_COLOR
                                val actualColor = if (gradual) 16777215 else color
                                val result = if (attention) {
                                    com.android.purebilibili.data.repository.DanmakuRepository.sendAttentionCommandDanmaku(
                                        aid = requestAid, cid = requestCid, progress = positionMs,
                                    ).map { Unit }
                                } else {
                                    com.android.purebilibili.data.repository.DanmakuRepository.sendDanmaku(
                                        aid = requestAid,
                                        cid = requestCid,
                                        message = message,
                                        progress = positionMs,
                                        color = actualColor,
                                        fontSize = fontSize,
                                        mode = mode,
                                        colorful = gradual,
                                    ).map { Unit }
                                }
                                result.onSuccess {
                                    portraitComposerText = ""
                                    portraitComposerAttention = false
                                    portraitComposerVisible = false
                                    if (!attention && exoPlayer.currentMediaItem?.mediaId == requestMediaId) {
                                        danmakuManager.addLocalDanmaku(
                                            text = message, color = actualColor, mode = mode,
                                            fontSize = fontSize, isVipGradualColor = gradual,
                                        )
                                    }
                                    showPortraitFeedback("发送成功")
                                }.onFailure { error ->
                                    showPortraitFeedback(error.toUserFriendlyMessage("发送失败"))
                                }
                            } finally {
                                portraitComposerSending = false
                            }
                        }
                    }
                },
                subtitleControlCallbacks = portraitSubtitleCallbacks,
                onSeekStart = { danmakuManager.prepareForSeekScrub() },
                onSeekDragStart = { position ->
                    seekSession = startPlaybackSeekInteraction(
                        state = seekSession,
                        player = exoPlayer,
                        positionMs = position,
                    )
                },
                onSeekDragUpdate = { position ->
                    seekSession = updatePlaybackSeekInteraction(seekSession, position)
                },
                onSeekDragCancel = {
                    seekSession = cancelPlaybackSeekInteraction(seekSession)
                    danmakuManager.cancelSeekScrub()
                },
                onSeekTo = { requestedPosition ->
                    if (isCurrentPage) {
                        val targetPosition = resolvePortraitCommittedSeekPosition(
                            requestedPositionMs = requestedPosition,
                            durationMs = progressDuration.takeIf { it > 0L }
                                ?: exoPlayer.duration.coerceAtLeast(0L),
                        )
                        val commitResult = finishPlaybackSeekInteraction(
                            updatePlaybackSeekInteraction(seekSession, targetPosition)
                        )
                        seekSession = commitResult.state
                        progressState = progressState.copy(current = commitResult.committedPositionMs)
                        seekPlayerFromUserAction(
                            player = exoPlayer,
                            positionMs = commitResult.committedPositionMs,
                            shouldResumePlaybackOverride = commitResult.shouldResumePlayback,
                        )
                        danmakuManager.seekTo(commitResult.committedPositionMs)
                    }
                },
                progressDisplayOverridePositionProvider = {
                    if (seekSliderMoving) seekSession.sliderPositionMs else progressState.current
                },
                onPlayModeClick = { PlaylistManager.togglePlayMode() },
                onExternalPlaylistClick = { showPortraitExternalPlaylist = true },
                onPageSelect = { index ->
                    portraitDetailInfo?.pages?.getOrNull(index)?.let { page ->
                        onRequestCollectionItem(bvid, page.cid, portraitDetailInfo.ugc_season)
                    }
                },
                onDrawerVideoClick = { targetBvid, options ->
                    val targetCid = options?.getLong(
                        com.android.purebilibili.feature.video.screen.VIDEO_NAV_TARGET_CID_KEY
                    ) ?: 0L
                    onRequestCollectionItem(targetBvid, targetCid, portraitDetailInfo?.ugc_season)
                },
                onToggleFollow = {
                    if (canHandlePortraitInteraction) pageEngagementSession.toggleFollow()
                },
                onToggleLike = {
                    if (canHandlePortraitInteraction) {
                        pageEngagementSession.toggleLike()
                    }
                },
                onCoin = { if (canHandlePortraitInteraction) pageEngagementSession.openCoinDialog() },
                onToggleFavorite = {
                    if (canHandlePortraitInteraction) {
                        when (resolvePortraitFavoriteAction(false, favoriteQuickSaveDefaultFolder)) {
                            PortraitFavoriteAction.ToggleFavorite -> pageEngagementSession.toggleFavorite()
                            PortraitFavoriteAction.OpenFavoriteFolders -> pageEngagementSession.openFavoriteFolders()
                        }
                    }
                },
                shareMenuSource = portraitShareMenuSource,
                onShare = {
                    val info = currentSuccess?.info
                    pendingPortraitShare = com.android.purebilibili.feature.video.share.buildVideoSharePayload(
                        title = title, bvid = bvid, coverUrl = info?.pic.orEmpty(),
                        upName = info?.owner?.name.orEmpty(), authorId = info?.owner?.mid ?: 0L,
                        page = info?.pages?.indexOfFirst { it.cid == currentPlayingCid }?.coerceAtLeast(0)?.plus(1) ?: 1,
                        positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    )
                },
            )
        // 竖屏与横屏共用功能组件，玻璃材质在控制栏内部适配。
        if (isCurrentPage && !portraitCleanScreen) {
            com.android.purebilibili.feature.video.ui.overlay.VideoPlayerOverlay(
                state = portraitOverlayState,
                actions = portraitOverlayActions,
            )
            val portraitDecoderState by remember(exoPlayer) {
                com.android.purebilibili.core.player.VideoDecoderDiagnostics.observe(exoPlayer)
            }.collectAsStateWithLifecycle()
            val portraitDebugInfo = remember(portraitDecoderState) {
                com.android.purebilibili.feature.video.ui.overlay.PlaybackDebugInfo(
                    videoDecoder = portraitDecoderState.decoderName,
                    videoDecoderType = portraitDecoderState.kind.label,
                    videoDecoderPreference = portraitDecoderState.preference,
                    videoDecoderFallbackReason = portraitDecoderState.fallbackReason,
                    videoDecoderDropWarning = portraitDecoderState.dropRateWarning,
                )
            }
            val portraitQualityLabels = remember(availableQualityIds) {
                resolvePortraitQualityMenuLabels(availableQualityIds)
            }
            var portraitSoftwareOverloadVisible by remember(bvid, snapshotCid) { mutableStateOf(false) }
            val portraitBannerUseGlass = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.playerGlassEnabled &&
                com.android.purebilibili.feature.video.ui.overlay.LocalPlayerPageBackdrop.current != null
            val portraitBannerModifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 56.dp, start = 12.dp, end = 12.dp)
            com.android.purebilibili.feature.video.ui.overlay.SoftwareDecodeOverloadOverlayHost(
                bvid = bvid,
                cid = snapshotCid,
                debugInfo = portraitDebugInfo,
                currentQuality = selectedQualityId,
                qualityIds = availableQualityIds,
                qualityLabels = portraitQualityLabels,
                switchableQualityIds = switchableQualityIds,
                isScreenLocked = portraitScreenLocked,
                suppressTransientOverlays = false,
                onQualitySelected = { index ->
                    availableQualityIds.getOrNull(index)?.let(onQualitySelected)
                },
                onReloadVideo = onReloadPlayback,
                useGlass = portraitBannerUseGlass,
                onVisibilityChanged = { portraitSoftwareOverloadVisible = it },
                modifier = portraitBannerModifier,
            )
            com.android.purebilibili.feature.video.ui.overlay.WeakNetworkDegradeOverlayHost(
                player = exoPlayer,
                mediaKey = "portrait:$bvid:$snapshotCid",
                currentQuality = selectedQualityId,
                qualityIds = availableQualityIds,
                qualityLabels = portraitQualityLabels,
                switchableQualityIds = switchableQualityIds,
                isScreenLocked = portraitScreenLocked,
                suppressedByHigherPriorityBanner = portraitSoftwareOverloadVisible,
                isBufferingSuppressed = seekSliderMoving,
                onSelectLowerQuality = { target -> onQualitySelected(target.qualityId) },
                useGlass = portraitBannerUseGlass,
                modifier = portraitBannerModifier,
            )
        }
    }

    }

        if (showPortraitExternalPlaylist && isCurrentPage && !isInPipMode) {
            com.android.purebilibili.feature.video.ui.components.PlayerSelectionPopup(
                title = "播放列表",
                onDismissRequest = { showPortraitExternalPlaylist = false },
                maxHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.6f).dp,
                minWidth = 220.dp,
                maxWidth = 360.dp,
            ) {
                portraitExternalPlaylist.forEach { entry ->
                    com.android.purebilibili.feature.video.ui.components.PlayerSelectionItem(
                        text = entry.title,
                        selected = entry.bvid == bvid && (entry.cid == snapshotCid || entry.cid <= 0L),
                        onClick = {
                            showPortraitExternalPlaylist = false
                            onRequestCollectionItem(entry.bvid, entry.cid, null)
                        },
                    )
                }
            }
        }

        val pageCidForSubtitle = snapshotCid.takeIf { it > 0L }
            ?: portraitDetailInfo?.cid
            ?: 0L
        // 与播放器字幕叠层保持同一条策略：小窗里不合成字幕（shouldKeepSubtitleOverlayMounted）。
        if (!isInPipMode) {
        PortraitSubtitleHost(
            success = currentSuccess,
            player = exoPlayer,
            pageBvid = bvid,
            pageCid = pageCidForSubtitle,
            isCurrentPage = isCurrentPage,
            controlsVisible = resolvePortraitOverlayControlsVisible(
                portraitOverlayVisible = portraitOverlayVisible,
                showDetailSheet = showDetailSheet || detailSheetTransitionActive,
                otherSheetVisible = showCommentSheet || subReplyState.visible || showUpPreview ||
                    commentSheetVisibilityProgress > 0.001f || upPreviewVisibilityProgress > 0.001f,
            ),
            commentExpansionProgress = commentSheetVisibilityProgress,
            subtitleAutoPreference = subtitleAutoPreference,
            isMuted = exoPlayer.volume <= 0f,
            onSubtitleTrackSelected = { trackKey ->
                viewModel.selectSubtitleTrack(trackKey)
            },
            onControlsChange = { controls, callbacks ->
                portraitSubtitleControls = controls
                portraitSubtitleCallbacks = callbacks
            },
        )
        }
        } // inner portrait stage Box


        CompositionLocalProvider(
            com.android.purebilibili.feature.share.LocalReplyShareContext provides
                com.android.purebilibili.feature.share.videoReplyShareContext(
                    bvid = bvid,
                    title = currentSuccess?.info?.title?.ifBlank { null } ?: title,
                    cover = currentSuccess?.info?.pic?.ifBlank { null } ?: cover,
                    page = currentSuccess?.info?.pages?.indexOfFirst { it.cid == currentPlayingCid }
                        ?.coerceAtLeast(0)?.plus(1) ?: 1,
                ),
        ) {
            PortraitCommentSheet(
                visible = showCommentSheet,
                active = isCurrentPage,
                onDismiss = {
                    showCommentSheet = false
                },
                onVisibilityProgressChange = { progress ->
                    commentSheetVisibilityProgress = progress
                },
                commentViewModel = commentViewModel,
                aid = activeAid,
                upMid = authorMid,
                expectedReplyCount = if (isCurrentModelVideo && currentSuccess != null) currentSuccess.info.stat.reply else stat.reply,
                emoteMap = currentSuccess?.emoteMap ?: emptyMap(),
                maxTimestampMs = currentSuccess?.videoDurationMs?.takeIf { it > 0L }
                    ?: progressDuration.takeIf { it > 0L },
                onRootCommentClick = { viewModel.openRootCommentComposer() },
                onReplyClick = { reply ->
                    viewModel.setReplyingTo(reply)
                    viewModel.showCommentInputDialog()
                },
                onUserClick = onUserClick,
                onOpenBilibiliLink = onOpenBilibiliLink,
                preferEndSideSheet = useSideCommentPane,
            )
        }

        if (isCurrentPage) {
            val showCommentInput by viewModel.showCommentDialog.collectAsStateWithLifecycle()
            val isSendingComment by viewModel.isSendingComment.collectAsStateWithLifecycle()
            val replyingToComment by viewModel.replyingToComment.collectAsStateWithLifecycle()
            val emotePackages by viewModel.emotePackages.collectAsStateWithLifecycle()
            val mentionSearchState by viewModel.commentMentionSearchState.collectAsStateWithLifecycle()
            val composerDrafts by viewModel.composerDrafts.collectAsStateWithLifecycle()
            val draftVideoId = composerDrafts.videoId
            val draftSessionId = composerDrafts.comments.sessionId
            val draftReplyRpid = replyingToComment?.rpid
            val draftKey = remember(draftVideoId, draftSessionId, draftReplyRpid) {
                "$draftVideoId:$draftReplyRpid:$draftSessionId"
            }
            val commentDraft = composerDrafts.comments.drafts[
                com.android.purebilibili.feature.video.viewmodel.commentComposerDraftKey(draftReplyRpid)
            ] ?: com.android.purebilibili.feature.video.viewmodel.CommentComposerDraft()
            val commentState by portraitCommentStateHandle
            LaunchedEffect(activeAid) {
                viewModel.commentSentEvent.collect { event ->
                    commentViewModel.onExternalCommentSent(
                        aid = event.aid,
                        newReply = event.reply,
                        senderAccount = event.account,
                    )
                }
            }

            com.android.purebilibili.feature.video.ui.components.CommentInputDialog(
                visible = showCommentInput,
                draftKey = draftKey,
                onDismiss = {
                    val current = viewModel.composerDrafts.value
                    if (current.videoId == draftVideoId && current.comments.sessionId == draftSessionId) {
                        viewModel.hideCommentInputDialog()
                    }
                },
                isSending = isSendingComment,
                replyToName = replyingToComment?.member?.uname,
                inputHint = if (replyingToComment != null) commentState.childInputHint else commentState.rootInputHint,
                canInputComment = commentState.canInputComment,
                emotePackages = emotePackages,
                mentionUsers = mentionSearchState.users,
                isMentionSearching = mentionSearchState.isLoading,
                mentionSearchError = mentionSearchState.errorMessage,
                onMentionSearchQueryChange = viewModel::searchCommentMentionUsers,
                initialText = commentDraft.text,
                initialImageUris = commentDraft.imageUris,
                initialSyncToDynamic = commentDraft.syncToDynamic,
                onDraftChange = { text, images, sync ->
                    viewModel.updateCommentDraft(draftVideoId, draftSessionId, draftReplyRpid, text, images, sync)
                },
                currentVideoPositionMsProvider = { exoPlayer.currentPosition.coerceAtLeast(0L) },
                onSend = { message, imageUris, syncToDynamic ->
                    val current = viewModel.composerDrafts.value
                    if (current.videoId == draftVideoId && current.comments.sessionId == draftSessionId &&
                        viewModel.replyingToComment.value?.rpid == draftReplyRpid &&
                        viewModel.showCommentDialog.value
                    ) {
                        viewModel.sendComment(
                            inputMessage = message,
                            imageUris = imageUris,
                            syncToDynamic = syncToDynamic,
                            targetAid = activeAid
                        )
                    }
                }
            )
        }
        
        PortraitDetailSheet(
            visible = isCurrentPage && showDetailSheet,
            modifier = Modifier.fillMaxSize(),
            onActiveChange = { detailSheetTransitionActive = it },
            onDismiss = {
                showDetailSheet = false
            },
            info = portraitDetailInfo,
            currentCid = currentPlayingCid.takeIf { isCurrentPage } ?: portraitDetailInfo?.cid ?: 0L,
            recommendationTitle = detailVideoList.title,
            recommendations = detailVideoList.videos,
            onRecommendationClick = { targetBvid ->
                showDetailSheet = false
                onRequestVideoChange(targetBvid)
            },
            onCollectionItemClick = { targetBvid, targetCid ->
                showDetailSheet = false
                onRequestCollectionItem(
                    targetBvid,
                    targetCid,
                    portraitDetailInfo?.ugc_season,
                )
            },
            onAuthorClick = { mid ->
                showDetailSheet = false
                onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid, cover)
                onUserClick(mid)
            },
            danmakuEnabled = danmakuEnabled,
            onDanmakuToggle = toggleDanmaku
        )

        portraitDetailInfo?.takeIf { it.owner.mid > 0L }?.let { info ->
            val ownerStats = currentSuccess?.takeIf { it.info.owner.mid == info.owner.mid }
            UpPreviewSheet(
                visible = isCurrentPage && showUpPreview,
                owner = info.owner,
                isFollowing = isFollowing,
                followStatusKnown = followStatusKnown,
                followerCount = ownerStats?.ownerFollowerCount,
                videoCount = ownerStats?.ownerVideoCount,
                seedVideos = upPreviewSeedVideos,
                onDismiss = {
                    showUpPreview = false
                },
                onFollowClick = {
                    if (canHandlePortraitInteraction) pageEngagementSession.toggleFollow()
                },
                onEnterSpace = { mid ->
                    showUpPreview = false
                    onUpPreviewActiveChange(false)
                    onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid, cover)
                    onUserClick(mid)
                },
                onVideoClick = { targetBvid, _ ->
                    showUpPreview = false
                    onUpPreviewActiveChange(false)
                    onRequestVideoChange(targetBvid)
                },
                onVisibilityProgressChange = { progress ->
                    upPreviewVisibilityProgress = progress
                },
            )
        }
        com.android.purebilibili.core.ui.components.AppSnackbarHost(
            hostState = portraitSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        )
    }
}

internal fun resolvePortraitInitialPageIndex(
    initialBvid: String,
    initialInfoBvid: String,
    recommendations: List<RelatedVideo>
): Int {
    if (initialBvid == initialInfoBvid) return 0
    val recommendationIndex = recommendations.indexOfFirst { it.bvid == initialBvid }
    if (recommendationIndex < 0) return 0
    return recommendationIndex + 1
}

internal fun shouldHandlePortraitVideoInteraction(
    isCurrentPage: Boolean,
    aid: Long,
    bvid: String
): Boolean {
    return isCurrentPage && aid > 0L && bvid.isNotBlank()
}

internal fun resolvePortraitActiveAid(
    isPlayerReadyForThisVideo: Boolean,
    itemAid: Long,
    currentPlayingAid: Long
): Long {
    // 切换开始时播放器先清空 AID；此时列表身份仍有效，不能用未知值覆盖。
    return if (isPlayerReadyForThisVideo && currentPlayingAid > 0L) currentPlayingAid
        else itemAid.coerceAtLeast(0L)
}

internal fun resolvePortraitOverlayControlsVisible(
    portraitOverlayVisible: Boolean,
    showDetailSheet: Boolean,
    otherSheetVisible: Boolean = false,
): Boolean {
    return portraitOverlayVisible && !showDetailSheet && !otherSheetVisible
}

internal fun resolvePortraitOverlayVisibilityAfterTap(currentlyVisible: Boolean): Boolean {
    return !currentlyVisible
}

internal fun resolvePortraitFavoriteAction(
    isLongPress: Boolean,
    quickSaveDefaultFolder: Boolean,
): PortraitFavoriteAction {
    return when {
        isLongPress -> PortraitFavoriteAction.OpenFavoriteFolders
        quickSaveDefaultFolder -> PortraitFavoriteAction.ToggleFavorite
        else -> PortraitFavoriteAction.OpenFavoriteFolders
    }
}



@Composable
private fun PortraitDanmakuOverlay(
    danmakuManager: DanmakuManager,
    videoWidth: Int,
    videoHeight: Int,
    resizeMode: Int,
    fillDisplayArea: Boolean = false,
    modifier: Modifier = Modifier
) {
    DanmakuViewportHost(modifier, fillDisplayArea = fillDisplayArea) { viewport ->
        key(danmakuManager) {
            AndroidView(
                factory = { ctx ->
                    DanmakuRenderView(ctx).apply {
                        danmakuManager.updateViewport(viewport)
                        addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                            if (view.width > 0 && view.height > 0) danmakuManager.attachView(this)
                        }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        // 弹幕层必须 passive，否则会吞掉竖屏全屏的全部触控。
                        configureAsPassiveDanmakuOverlay()
                        danmakuManager.attachView(this)
                    }
                },
                update = { view ->
                    view.configureAsPassiveDanmakuOverlay()
                    danmakuManager.updateViewport(viewport)
                    val viewportTag = "$videoWidth:$videoHeight:$resizeMode:$fillDisplayArea:${view.width}x${view.height}"
                    if (view.width > 0 && view.height > 0 && view.tag != viewportTag) {
                        view.tag = viewportTag
                        danmakuManager.attachView(view)
                    }
                },
                onRelease = { view -> danmakuManager.detachView(view) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

internal fun resolvePortraitPagerRepeatMode(): Int = Player.REPEAT_MODE_OFF

internal fun resolvePortraitDanmakuSurfaceMode(
    upperRegionAvailable: Boolean,
    displayAreaMode: PortraitDanmakuDisplayAreaMode
): PortraitDanmakuSurfaceMode {
    return when (displayAreaMode) {
        PortraitDanmakuDisplayAreaMode.FULL_SCREEN -> PortraitDanmakuSurfaceMode.FullScreen
        PortraitDanmakuDisplayAreaMode.SCREEN_TOP -> {
            if (upperRegionAvailable) PortraitDanmakuSurfaceMode.Page
            else PortraitDanmakuSurfaceMode.VideoViewport
        }
        PortraitDanmakuDisplayAreaMode.VIDEO_VIEWPORT -> PortraitDanmakuSurfaceMode.VideoViewport
    }
}


/**
 * Resolves the top safe inset for the page-level portrait danmaku surface.
 *
 * During immersive fullscreen, [WindowInsets.statusBars] reports zero because the bar is
 * hidden. The ignoring-visibility and display-cutout insets preserve the physical area that
 * can still be covered by system chrome or a camera hole/notch.
 */
internal fun resolvePortraitDanmakuTopInsetPx(
    visibleStatusBarTopPx: Int,
    statusBarsIgnoringVisibilityTopPx: Int,
    displayCutoutTopPx: Int,
): Int = maxOf(
    visibleStatusBarTopPx,
    statusBarsIgnoringVisibilityTopPx,
    displayCutoutTopPx,
    0,
)

internal fun shouldLoadPortraitDanmaku(
    settingsLoaded: Boolean,
    cid: Long,
    danmakuEnabled: Boolean
): Boolean {
    return settingsLoaded && cid > 0L && danmakuEnabled
}

/**
 * Letterbox ambient capture is settings-driven and only for the active ready page.
 * Dual-host composition must already be gone before this can run on the main thread.
 */
internal fun shouldCapturePortraitLetterboxAmbientFrame(
    isCurrentPage: Boolean,
    letterboxAmbientHazeEnabled: Boolean,
    letterboxBarHeightPx: Int,
    isPlayerReadyForThisVideo: Boolean,
): Boolean {
    return isCurrentPage &&
        letterboxAmbientHazeEnabled &&
        letterboxBarHeightPx > 0 &&
        isPlayerReadyForThisVideo
}

internal fun shouldComposePortraitDanmakuOverlay(
    danmakuEnabled: Boolean,
    surfaceMode: PortraitDanmakuSurfaceMode,
    expectedMode: PortraitDanmakuSurfaceMode,
    isCurrentPage: Boolean,
    isPlayerReadyForThisVideo: Boolean,
): Boolean {
    return danmakuEnabled &&
        surfaceMode == expectedMode &&
        isCurrentPage &&
        isPlayerReadyForThisVideo
}

internal data class PortraitVideoViewportSize(
    val width: Int,
    val height: Int
)

internal fun resolvePortraitVideoViewportSize(
    containerWidth: Int,
    containerHeight: Int,
    currentVideoAspect: Float,
    fillContainer: Boolean
): PortraitVideoViewportSize {
    val safeWidth = containerWidth.coerceAtLeast(1)
    val safeHeight = containerHeight.coerceAtLeast(1)
    if (fillContainer) {
        return PortraitVideoViewportSize(width = safeWidth, height = safeHeight)
    }
    val safeAspect = currentVideoAspect.coerceAtLeast(0.1f)
    val containerAspect = safeWidth.toFloat() / safeHeight.toFloat()
    return if (safeAspect > containerAspect) {
        PortraitVideoViewportSize(
            width = safeWidth,
            height = (safeWidth / safeAspect).roundToInt().coerceIn(1, safeHeight)
        )
    } else {
        PortraitVideoViewportSize(
            width = (safeHeight * safeAspect).roundToInt().coerceIn(1, safeWidth),
            height = safeHeight
        )
    }
}

internal fun resolvePortraitPagerFillContainer(
    aspectRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    isVerticalContent: Boolean = true
): Boolean {
    val safe = resolveSafeVideoAspectRatio(
        preferred = aspectRatio,
        isVerticalVideo = isVerticalContent
    )
    return safe == VideoAspectRatio.FILL || safe == VideoAspectRatio.STRETCH
}

/**
 * Viewport aspect used when not filling the whole page.
 * Fixed 16:9 / 4:3 modes force that frame (parity with landscape fullscreen).
 * FIT uses the source video aspect so letterboxing stays natural.
 */
internal fun resolvePortraitPagerViewportAspect(
    aspectRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    currentVideoAspect: Float,
    isVerticalContent: Boolean = true
): Float {
    val safe = resolveSafeVideoAspectRatio(
        preferred = aspectRatio,
        isVerticalVideo = isVerticalContent
    )
    val fixed = safe.targetAspectRatio?.takeIf { it.isFinite() && it > 0f }
    if (fixed != null) return fixed
    return currentVideoAspect.coerceAtLeast(0.1f)
}

internal fun resolvePortraitPagerResizeMode(
    aspectRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    isVerticalContent: Boolean = true
): Int {
    return resolveSafeVideoAspectRatio(
        preferred = aspectRatio,
        isVerticalVideo = isVerticalContent
    ).playerResizeMode
}

internal fun shouldAllowPortraitPlayback(
    isCurrentStoryTab: Boolean,
    isLifecycleResumed: Boolean,
    continueInBackgroundAudio: Boolean = false
): Boolean {
    if (continueInBackgroundAudio) return isCurrentStoryTab
    return isCurrentStoryTab && isLifecycleResumed
}

internal fun resolvePortraitVideoViewportVerticalOffsetDp(
    currentVideoAspect: Float,
    fillContainer: Boolean,
    isLandscape: Boolean = false,
    liquidGlassEnabled: Boolean = false,
): Int {
    if (!liquidGlassEnabled || fillContainer || isLandscape) return 0
    return if (currentVideoAspect > 1f) -48 else 0
}

internal fun resolvePortraitPlayerViewportSize(
    containerWidth: Int,
    containerHeight: Int,
    currentVideoAspect: Float,
    fillContainer: Boolean,
    aspectRatio: VideoAspectRatio?,
    videoWidth: Int,
    videoHeight: Int,
    liquidGlassEnabled: Boolean,
): PortraitVideoViewportSize {
    // Without decoded dimensions, retain the current item's known aspect. The native
    // viewport helper otherwise defaults to landscape and moves the poster at first frame.
    return if (aspectRatio != null && !liquidGlassEnabled && videoWidth > 0 && videoHeight > 0) {
        com.android.purebilibili.feature.video.ui.components.resolveVideoViewportLayout(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            aspectRatio = aspectRatio,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
        ).let { PortraitVideoViewportSize(it.width, it.height) }
    } else {
        resolvePortraitVideoViewportSize(
            containerWidth = containerWidth,
            containerHeight = containerHeight,
            currentVideoAspect = currentVideoAspect,
            fillContainer = fillContainer,
        )
    }
}

@Composable
internal fun PortraitVideoViewportContainer(
    currentVideoAspect: Float,
    modifier: Modifier = Modifier,
    viewportModifier: Modifier = Modifier,
    fillContainer: Boolean = false,
    aspectRatio: VideoAspectRatio? = null,
    videoWidth: Int = 0,
    videoHeight: Int = 0,
    content: @Composable BoxScope.() -> Unit
) {
    val liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        val viewportSize = with(density) {
            resolvePortraitPlayerViewportSize(
                containerWidth = maxWidth.roundToPx(),
                containerHeight = maxHeight.roundToPx(),
                currentVideoAspect = currentVideoAspect,
                fillContainer = fillContainer,
                aspectRatio = aspectRatio,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                liquidGlassEnabled = liquidGlassEnabled,
            )
        }
        val isLandscape = maxWidth > maxHeight

        Box(
            modifier = viewportModifier
                .requiredSize(
                    width = with(density) { viewportSize.width.toDp() },
                    height = with(density) { viewportSize.height.toDp() }
                )
                .align(Alignment.Center)
                .offset(
                    y = resolvePortraitVideoViewportVerticalOffsetDp(
                        currentVideoAspect = currentVideoAspect,
                        fillContainer = fillContainer,
                        isLandscape = isLandscape,
                        liquidGlassEnabled = liquidGlassEnabled,
                    ).dp
                )
        ) {
            content()
        }
    }
}

internal fun resolvePortraitInitialVideoAspectRatio(
    itemBvid: String,
    currentPlayingBvid: String?,
    playerVideoWidth: Int,
    playerVideoHeight: Int,
    knownVideoAspectRatio: Float? = null
): Float {
    knownVideoAspectRatio
        ?.takeIf { it > 0f }
        ?.let { return it }
    val hasValidPlayerSize = playerVideoWidth > 0 && playerVideoHeight > 0
    return if (itemBvid == currentPlayingBvid && hasValidPlayerSize) {
        playerVideoWidth.toFloat() / playerVideoHeight.toFloat()
    } else {
        9f / 16f
    }
}

internal fun resolvePortraitRuntimeVideoAspectRatio(
    knownVideoAspectRatio: Float?,
    playerVideoWidth: Int,
    playerVideoHeight: Int
): Float {
    val safeKnownAspect = knownVideoAspectRatio?.takeIf { it > 0f }
    if (playerVideoWidth <= 0 || playerVideoHeight <= 0) {
        return safeKnownAspect ?: (9f / 16f)
    }
    val playerAspect = playerVideoWidth.toFloat() / playerVideoHeight.toFloat()
    if (safeKnownAspect == null) return playerAspect
    val knownPortrait = safeKnownAspect < 1f
    val playerPortrait = playerAspect < 1f
    return if (knownPortrait != playerPortrait) {
        safeKnownAspect
    } else {
        playerAspect
    }
}

internal fun resolvePortraitInitialRenderedFirstFrameGeneration(
    useSharedPlayer: Boolean,
    sharedPlayerHasFrameAtEntry: Boolean
): Int {
    return if (useSharedPlayer && sharedPlayerHasFrameAtEntry) 0 else -1
}

internal fun resolveAspectRatioFromDimension(
    dimension: com.android.purebilibili.data.model.response.Dimension?
): Float? {
    val source = dimension ?: return null
    if (source.width <= 0 || source.height <= 0) return null
    val normalizedRotate = ((source.rotate % 360) + 360) % 360
    val shouldSwap = source.rotate == 1 || normalizedRotate == 90 || normalizedRotate == 270
    val effectiveWidth = if (shouldSwap) source.height else source.width
    val effectiveHeight = if (shouldSwap) source.width else source.height
    if (effectiveWidth <= 0 || effectiveHeight <= 0) return null
    return effectiveWidth.toFloat() / effectiveHeight.toFloat()
}

internal fun shouldRebindSharedPlayerSurfaceOnAttach(
    isCurrentPage: Boolean,
    isPlayerReadyForThisVideo: Boolean,
    hasPlayerView: Boolean,
    videoWidth: Int,
    videoHeight: Int
): Boolean {
    return isCurrentPage && isPlayerReadyForThisVideo && hasPlayerView
}
