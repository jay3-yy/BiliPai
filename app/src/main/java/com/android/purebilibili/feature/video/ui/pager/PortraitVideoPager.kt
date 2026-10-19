@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.feature.video.ui.overlay.NextWatchOverlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.android.purebilibili.feature.video.screen.VideoDetailDanmakuContextMenu
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.android.purebilibili.core.ui.AppWindowSystemUiController
import com.android.purebilibili.core.ui.AppWindowSystemUiSnapshot
import com.android.purebilibili.feature.video.screen.VideoDetailHiddenSystemBars
import com.android.purebilibili.feature.video.screen.VideoDetailSystemBarsApplySpec
import com.android.purebilibili.feature.video.screen.applyVideoDetailSystemBarsSpec
import com.android.purebilibili.feature.video.screen.findActivity
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
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
import com.android.purebilibili.core.player.HiResCompatibleRenderersFactory
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.util.NetworkUtils
import com.android.purebilibili.feature.plugin.PlaybackCdnPlugin
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
import com.android.purebilibili.feature.video.playback.audio.resolveAudioQualityControlPresentation
import com.android.purebilibili.feature.video.playback.audio.resolveRequestedAudioQuality
import com.android.purebilibili.feature.video.playback.policy.shouldRefreshPremiumAudioForPlaybackSpeedChange
import com.android.purebilibili.feature.video.ui.overlay.PlayerProgress
import com.android.purebilibili.feature.video.ui.components.AspectRatioMenu
import com.android.purebilibili.feature.video.ui.components.AudioQualitySelectionMenu
import com.android.purebilibili.feature.video.ui.components.QualitySelectionMenu
import com.android.purebilibili.feature.video.ui.components.SpeedSelectionMenuDialog
import com.android.purebilibili.feature.video.ui.components.UpPreviewSheet
import com.android.purebilibili.feature.video.ui.components.UP_PREVIEW_SHEET_HEIGHT_FRACTION
import com.android.purebilibili.feature.video.ui.components.resolvePortraitOverlaySheetExpansion
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.resolveSafeVideoAspectRatio
import com.android.purebilibili.feature.video.ui.overlay.FullscreenDoubleTapAction
import com.android.purebilibili.feature.video.ui.overlay.ImmersiveAmbientLetterboxBackdrop
import com.android.purebilibili.feature.video.ui.overlay.PortraitFullscreenOverlay
import com.android.purebilibili.feature.video.ui.overlay.PortraitSubtitleHost
import com.android.purebilibili.feature.video.ui.overlay.VIDEO_STATUS_BAR_AMBIENT_CAPTURE_INTERVAL_MS
import com.android.purebilibili.feature.video.ui.overlay.VIDEO_STATUS_BAR_AMBIENT_SAMPLE_HEIGHT_PX
import com.android.purebilibili.feature.video.ui.overlay.VIDEO_STATUS_BAR_AMBIENT_SAMPLE_WIDTH_PX
import com.android.purebilibili.feature.video.ui.overlay.nextFullscreenSeekFeedbackEvent
import com.android.purebilibili.feature.video.ui.overlay.resolveFullscreenDoubleTapAction
import com.android.purebilibili.feature.video.ui.overlay.resolvePortraitLetterboxBarHeightPx
import com.android.purebilibili.feature.video.ui.overlay.shouldShowPortraitSubtitleChip
import com.android.purebilibili.feature.video.util.captureVideoAmbientFrame
import com.android.purebilibili.feature.video.subtitle.SubtitleAutoPreference
import com.android.purebilibili.feature.video.subtitle.isSubtitleFeatureEnabledForUser
import com.android.purebilibili.feature.video.player.resolveHandleAudioFocusByPolicy
import com.android.purebilibili.feature.video.ui.section.FOREGROUND_SURFACE_RECOVERY_DELAY_MS
import com.android.purebilibili.feature.video.ui.section.resolveLongPressPlaybackParameters
import com.android.purebilibili.feature.video.ui.section.rebindPlayerSurfaceIfNeeded
import com.android.purebilibili.feature.video.ui.section.resolveRelativeSeekTargetPosition
import com.android.purebilibili.feature.video.ui.section.shouldKickPlaybackAfterSurfaceRecovery
import com.android.purebilibili.feature.video.viewmodel.PlaybackEndAction
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackUiState
import com.android.purebilibili.feature.video.viewmodel.VideoPlaybackViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoCommentViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoComposerViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementViewModel
import com.android.purebilibili.feature.video.viewmodel.VideoEngagementUiState
import com.android.purebilibili.feature.video.viewmodel.VideoSupplementViewModel
import com.android.purebilibili.feature.video.usecase.TripleActionResult
import com.android.purebilibili.feature.video.viewmodel.toEngagementSeed
import com.android.purebilibili.feature.video.viewmodel.toSupplementSeed
import com.android.purebilibili.feature.video.viewmodel.withEngagementUiState
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

internal data class PortraitVideoInteractionOverride(
    val isLiked: Boolean? = null,
    val isFavorited: Boolean? = null,
    val likeCount: Int? = null,
    val favoriteCount: Int? = null
)

internal data class PortraitVideoInteractionUiState(
    val isLiked: Boolean,
    val likeStatusKnown: Boolean,
    val isFavorited: Boolean,
    val likeCount: Int,
    val favoriteCount: Int
)

internal enum class PortraitFavoriteAction {
    ToggleFavorite,
    OpenFavoriteFolders
}

internal enum class PortraitDanmakuSurfaceMode {
    VideoViewport,
    Page
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
    onRotateToLandscape: () -> Unit
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
    // Immersive: hide status + nav bars while portrait pager is active (Story + detail overlay).
    //  [修复] 沉浸前捕获系统栏快照，退出时还原进入前的真实外观（含图标明暗），
    //  而不是硬编码透明色 + 深色图标，避免与进入前的页面外观不一致。
    var preImmersiveSystemBarsSnapshot by remember(window) {
        mutableStateOf<AppWindowSystemUiSnapshot?>(null)
    }
    LaunchedEffect(isActive, window, insetsController) {
        if (!isActive || window == null || insetsController == null) return@LaunchedEffect
        AppWindowSystemUiController.ensureEdgeToEdge(window)
        preImmersiveSystemBarsSnapshot =
            AppWindowSystemUiController.capture(window).let { snapshot ->
                // 快照若在系统栏已被隐藏时取得，照实记录会让退出恢复走到 hide 分支。
                if (snapshot.systemBarsVisible) snapshot else snapshot.copy(systemBarsVisible = true)
            }
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
    }
    DisposableEffect(isActive, window, insetsController) {
        onDispose {
            if (window == null || insetsController == null) return@onDispose
            // Restore bars when leaving portrait immersive (detail returns to inline / Story pops).
            val snapshot = preImmersiveSystemBarsSnapshot
            if (snapshot != null) {
                val restoreSpec = VideoDetailSystemBarsApplySpec(
                    hiddenBars = VideoDetailHiddenSystemBars.NONE,
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
                    statusBarColor = snapshot.statusBarColor,
                    navigationBarColor = snapshot.navigationBarColor,
                    lightStatusBars = snapshot.lightStatusBars,
                    lightNavigationBars = snapshot.lightNavigationBars
                )
                applyVideoDetailSystemBarsSpec(window, insetsController, restoreSpec)
            } else {
                val restoreSpec = VideoDetailSystemBarsApplySpec(
                    hiddenBars = VideoDetailHiddenSystemBars.NONE,
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT,
                    statusBarColor = ComposeColor.Transparent.toArgb(),
                    navigationBarColor = ComposeColor.Transparent.toArgb(),
                    lightStatusBars = false,
                    lightNavigationBars = false
                )
                applyVideoDetailSystemBarsSpec(window, insetsController, restoreSpec)
            }
            insetsController.show(WindowInsetsCompat.Type.systemBars())
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
    val engagementState by engagementViewModel.uiState.collectAsStateWithLifecycle()
    val subjectSnapshot by viewModel.subjectSnapshot.collectAsStateWithLifecycle()
    val engagementSeed = (playbackDomainState as? VideoPlaybackUiState.Success)?.toEngagementSeed()
    val supplementSeed = (playbackDomainState as? VideoPlaybackUiState.Success)?.toSupplementSeed()
    LaunchedEffect(subjectSnapshot, engagementSeed) {
        val subject = subjectSnapshot ?: return@LaunchedEffect
        val seed = engagementSeed ?: return@LaunchedEffect
        engagementViewModel.bindSubject(subject, seed)
        composerViewModel.bindSubject(subject)
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
    val latestLoadMoreRecommendations by rememberUpdatedState(onLoadMoreRecommendations)
    val danmakuManager = rememberIsolatedDanmakuManager(
        sessionKey = "portrait_danmaku_$initialBvid"
    )
    val danmakuScope = DanmakuSettingsScope.PORTRAIT
    DisposableEffect(danmakuManager, viewModel, isActive) {
        if (isActive) {
            danmakuManager.setOnDanmakuClickListener { text, dmid, userHash, isSelf ->
                viewModel.showDanmakuMenu(dmid, text, userHash, isSelf)
            }
        }
        onDispose { danmakuManager.setOnDanmakuClickListener(null) }
    }
    // 详情页内的竖屏播放器复用外层菜单；独立竖屏视频页在这里承载菜单。
    if (!useSharedPlayer) {
        val blockRulesRaw by SettingsManager.getDanmakuBlockRulesRaw(context, danmakuScope)
            .collectAsStateWithLifecycle(initialValue = "")
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
    val autoPlayEnabled by SettingsManager
        .getAutoPlay(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val externalPlaylistAutoContinueEnabled by SettingsManager
        .getExternalPlaylistAutoContinue(context)
        .collectAsStateWithLifecycle(initialValue = true
        )
    val playbackCompletionBehavior by SettingsManager
        .getPlaybackCompletionBehavior(context)
        .collectAsStateWithLifecycle(initialValue = PlaybackCompletionBehavior.CONTINUE_CURRENT_LOGIC
        )
    val isExternalPlaylist by PlaylistManager.isExternalPlaylist.collectAsStateWithLifecycle()
    val prefetchVideoEnabled by SettingsManager.getPrefetchVideo(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val portraitWifiAvailable by remember(context) { portraitWifiAvailability(context.applicationContext) }
        .collectAsStateWithLifecycle(initialValue = false)
    // Align portrait pager playurl qn with detail-page playable default (Wi‑Fi / VIP / auto-highest).
    // 默认画质解析含 SP 与 ConnectivityManager 读取,不能在组合期同步执行;
    // 先以 0 占位,首个播放请求由占位守卫挡下,待解析完成后补发。
    var portraitSelectedQuality by remember { mutableIntStateOf(0) }
    var portraitDisplayedQuality by remember { mutableIntStateOf(0) }
    var portraitAvailableQualityIds by remember { mutableStateOf(listOf<Int>()) }
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
    var portraitAspectRatio by remember { mutableStateOf(VideoAspectRatio.FIT) }
    val isPortraitLoggedIn = VideoRepository.isPlaybackLoggedIn()
    val isPortraitVip = VideoRepository.isPlaybackVip()
    val portraitMediaSourceFactory = remember(context) {
        buildPortraitCachedMediaSourceFactory(context)
    }
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

    LaunchedEffect(initialInfo.bvid, recommendations, onlyVerticalRecommendations, recommendationsManagedExternally) {
        val filteredRecommendations = filterPortraitOnlyVerticalRecommendations(
            recommendations = recommendations,
            enabled = onlyVerticalRecommendations,
        )
        if (!seededInitialRecommendations) {
            val seeded = if (recommendationsManagedExternally) {
                filteredRecommendations.distinctBy { it.bvid }
            } else shufflePortraitRecommendations(
                seed = recommendationShuffleSeed,
                recommendations = filteredRecommendations,
                precedingOwnerMid = initialInfo.owner.mid
            )
            recommendationItems.clear()
            recommendationItems.addAll(seeded)
            pageItems.clear()
            pageItems.add(initialInfo)
            pageItems.addAll(seeded)
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
        val shuffledAppend = if (recommendationsManagedExternally) appendItems else shufflePortraitRecommendations(
            seed = resolvePortraitRecommendationAppendSeed(
                baseSeed = recommendationShuffleSeed,
                currentBvid = initialInfo.bvid
            ),
            recommendations = appendItems,
            precedingOwnerMid = pageItems.lastOrNull()?.let(::resolvePortraitPageOwnerMid) ?: 0L
        )
        recommendationItems.addAll(shuffledAppend)
        pageItems.addAll(shuffledAppend)
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
        )
        if (appendItems.isNotEmpty()) {
            withContext(Dispatchers.Main.immediate) {
                pageItems.addAll(
                    shufflePortraitRecommendations(
                        seed = resolvePortraitRecommendationAppendSeed(
                            baseSeed = recommendationShuffleSeed,
                            currentBvid = pageItems.lastOrNull()
                                ?.let(::resolvePortraitPagePlaybackIdentity)
                                ?.bvid
                                .orEmpty()
                        ),
                        recommendations = appendItems,
                        precedingOwnerMid = pageItems.lastOrNull()?.let(::resolvePortraitPageOwnerMid) ?: 0L
                    )
                )
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
        )
        val shuffledDiscoveryRecommendations = shufflePortraitRecommendations(
            seed = resolvePortraitRecommendationAppendSeed(
                baseSeed = recommendationShuffleSeed,
                currentBvid = initialInfo.bvid
            ),
            recommendations = filteredDiscoveryRecommendations,
            precedingOwnerMid = initialInfo.owner.mid
        )
        val insertion = withContext(Dispatchers.Main.immediate) {
            recommendationFeedCursor = 1
            mergePortraitRecommendationAppendItems(
                currentBvid = initialInfo.bvid,
                existingBvids = snapshotPortraitPageBvids(pageItems),
                existingRecommendations = recommendationItems.toList(),
                fetchedRecommendations = shuffledDiscoveryRecommendations
            )
        }
        if (insertion.isEmpty()) return@LaunchedEffect

        withContext(Dispatchers.Main.immediate) {
            val insertNearHead = pagerState.currentPage == 0 && !pagerState.isScrollInProgress
            if (insertNearHead) {
                recommendationItems.addAll(0, insertion)
                pageItems.addAll(1, insertion)
            } else {
                recommendationItems.addAll(insertion)
                pageItems.addAll(insertion)
            }
        }
    }
    var currentPageScale by remember { mutableFloatStateOf(1f) }

    // [重构] 优先复用主播放器；仅在未传入 sharedPlayer 时才创建页内播放器
    val exoPlayer = sharedPlayer ?: remember(context) {
        val audioFocusEnabled = SettingsManager.getAudioFocusEnabledSync(context)
        ExoPlayer.Builder(context)
            .setRenderersFactory(
                HiResCompatibleRenderersFactory(context)
                    .setExtensionRendererMode(
                        androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    )
            )
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                resolveHandleAudioFocusByPolicy(audioFocusEnabled = audioFocusEnabled)
            )
            .build()
            .apply {
                repeatMode = resolvePortraitPagerRepeatMode()
                volume = com.android.purebilibili.core.player.PlayerVolumeController
                    .preferredVolumeSync()
                setPlaybackSpeed(SettingsManager.getPreferredPlaybackSpeedSync(context))
            }
    }
    LaunchedEffect(exoPlayer, playbackCompletionBehavior) {
        exoPlayer.repeatMode = resolvePlaybackCompletionRepeatMode(playbackCompletionBehavior)
    }
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
    var portraitCachedDash by remember { mutableStateOf<Dash?>(null) }
    var portraitCurrentVideoUrl by remember { mutableStateOf("") }
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
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    // 「后台播放」豁免：与横屏详情链路（VideoPlayerState.resolvePlaybackPauseDecision）
    // 共用 MiniPlayerManager.shouldContinueBackgroundAudio 的同一套策略，
    // 覆盖后台播放开关、退出即停、PiP 与导航离开等条件。
    var continueInBackgroundAudio by remember(lifecycleOwner) { mutableStateOf(false) }
    val isPortraitPlaybackAllowed = shouldAllowPortraitPlayback(
        isCurrentStoryTab = isActive,
        isLifecycleResumed = isLifecycleResumed,
        continueInBackgroundAudio = continueInBackgroundAudio
    )
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

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isLifecycleResumed = true
                    continueInBackgroundAudio = false
                }
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> {
                    val canContinue =
                        MiniPlayerManager.getInstance(context).shouldContinueBackgroundAudio()
                    continueInBackgroundAudio = canContinue
                    if (!canContinue) {
                        capturePortraitInactivitySnapshot()
                        isLifecycleResumed = false
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
                requestedPlaybackSnapshot = requestedPlaybackSnapshot?.copy(playWhenReady = playWhenReady)
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
                if (playbackState == Player.STATE_READY &&
                    pendingAutoPlayGeneration == activeLoadGeneration &&
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
                val playbackEndAction = resolvePlaybackEndAction(
                    behavior = playbackCompletionBehavior,
                    autoPlayEnabled = autoPlayEnabled,
                    isExternalPlaylist = isExternalPlaylist,
                    externalPlaylistAutoContinueEnabled = externalPlaylistAutoContinueEnabled,
                    hasNextPageOrSeasonTarget = hasCollectionFollowUp
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

    fun switchPortraitAudioQuality(
        audioQuality: Int,
        persistManualSelection: Boolean
    ): Boolean {
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
            cdnPlugin = portraitPlaybackCdnPlugin
        )
            ?: return false
        portraitCurrentVideoUrl = result.videoUrl
        portraitRequestedAudioQuality = audioQuality
        portraitSelectedAudioQuality = result.selection.selectedPreferenceId
        portraitAvailableAudioQualities = result.selection.availableOptions
        if (persistManualSelection) {
            portraitRememberedAudioQuality = audioQuality
            scope.launch {
                SettingsManager.setAudioQuality(context, audioQuality)
            }
        }
        return true
    }

    fun requestPortraitPlaybackForPage(
        targetPage: Int,
        applyInitialSeekOnFirstPage: Boolean,
        forceReload: Boolean = false,
        resumePositionMs: Long? = null,
        playWhenReadyOverride: Boolean? = null,
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
        val preparedPlayback = if (!forceReload && portraitWifiAvailable) {
            wifiPreloadQueue.claim(playbackIdentity, targetQuality, targetAudioQuality, exoPlayer.playbackParameters.speed)
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
                PortraitPlaybackRecoverySnapshot(bvid, requestedCid, plannedPositionMs, resumePlayback),
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
                    targetQuality = targetQuality
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
                        val streamUrls = resolvePortraitPlaybackStreamUrls(
                            playData = playData,
                            targetQuality = targetQuality,
                            requestedAudioQuality = targetAudioQuality,
                            playbackSpeed = exoPlayer.playbackParameters.speed
                        ) ?: run {
                                failPlayback("没有可播放的视频流，请重试")
                                return@fold
                            }
                        val resolvedUrls = resolvePortraitPlaybackCdnUrls(
                            streamUrls = streamUrls,
                            cachedDashVideos = playData.dash?.video.orEmpty(),
                            cachedDashAudios = playData.dash
                                ?.let(::collectAudioStreamCandidates)
                                ?.map { it.track }
                                .orEmpty(),
                            targetQuality = targetQuality,
                            cdnPlugin = portraitPlaybackCdnPlugin
                        )
                        val dashVideoIds = playData.dash?.video?.map { it.id }?.distinct().orEmpty()
                        val availableQualityIds = resolvePortraitAvailableQualityIds(
                            acceptQualities = playData.accept_quality,
                            dashVideoIds = dashVideoIds
                        )
                        if (availableQualityIds.isNotEmpty()) {
                            portraitAvailableQualityIds = availableQualityIds
                        }
                        portraitDisplayedQuality = resolvePortraitDisplayedQualityId(
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
                        }
                        portraitCachedDash = playData.dash
                        portraitCurrentVideoUrl = resolvedUrls.videoUrl
                        portraitRequestedAudioQuality = targetAudioQuality
                        portraitSelectedAudioQuality =
                            streamUrls.audioSelection?.selectedPreferenceId ?: -1
                        portraitAvailableAudioQualities =
                            streamUrls.audioSelection?.availableOptions.orEmpty()
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
                        val followUps = if (
                            onlyVerticalRecommendations &&
                            info.dimension?.isVertical != true
                        ) {
                            emptyList()
                        } else {
                            resolvePortraitCollectionFollowUps(
                                info = info,
                                currentCid = resolvedCid,
                            )
                        }
                        val injectItems = resolvePortraitCollectionInjectionPlan(
                            pageItems = pageItems.toList(),
                            currentPage = currentPageIndex,
                            followUps = followUps
                        )
                        if (injectItems.isNotEmpty()) {
                            val insertAt = (currentPageIndex + 1).coerceIn(0, pageItems.size)
                            pageItems.addAll(insertAt, injectItems)
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

                        exoPlayer.playWhenReady = latestPortraitPlaybackAllowed && resumePlayback
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

                        if (latestPortraitPlaybackAllowed && resumePlayback) {
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
        exoPlayer, wifiPreloadQueue, portraitWifiAvailable, isPortraitPlaybackAllowed,
        portraitSelectedQuality, portraitRequestedAudioQuality, portraitPreloadSpeed, activeLoadGeneration, isLoading
    ) {
        if (!portraitWifiAvailable || !isPortraitPlaybackAllowed || portraitSelectedQuality <= 0 || isLoading) {
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
                portraitRequestedAudioQuality, portraitPreloadSpeed
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
            if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
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
        prefetchVideoEnabled, portraitWifiAvailable, isPortraitPlaybackAllowed, portraitPlaybackCdnPlugin
    ) {
        if (portraitSelectedQuality <= 0 || !isPortraitPlaybackAllowed) return@LaunchedEffect
        if (!portraitWifiAvailable && !prefetchVideoEnabled) return@LaunchedEffect
        val prefetchJobs = mutableMapOf<String, Job>()
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
                val desiredKeys = targets.map { resolvePortraitMediaId(it.bvid, it.cid) }.toSet()
                prefetchJobs.keys.filter { it !in desiredKeys }.forEach { key ->
                    prefetchJobs.remove(key)?.cancel()
                }
                targets.forEach { target ->
                    val key = resolvePortraitMediaId(target.bvid, target.cid)
                    if (key in prefetchJobs) return@forEach
                    prefetchJobs[key] = launch(Dispatchers.IO) {
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
                                requestedAudioQuality = portraitRequestedAudioQuality
                            ) ?: return@launch
                            val resolvedUrls = resolvePortraitPlaybackCdnUrls(
                                streamUrls = streamUrls,
                                cachedDashVideos = playData.dash?.video.orEmpty(),
                                cachedDashAudios = playData.dash
                                    ?.let(::collectAudioStreamCandidates)?.map { it.track }.orEmpty(),
                                targetQuality = targetQuality,
                                cdnPlugin = portraitPlaybackCdnPlugin
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
            prefetchJobs.values.forEach { it.cancel() }
        }
    }

    // 默认画质异步解析完成后补发首屏播放请求:
    // 组合页流程可能早于解析完成触发并被占位守卫挡下,这里负责补发。
    // 用户手动切档时 currentPlayingBvid 非空,不会误触。
    LaunchedEffect(portraitSelectedQuality) {
        if (portraitSelectedQuality <= 0 || !currentPlayingBvid.isNullOrBlank() || pageItems.isEmpty()) {
            return@LaunchedEffect
        }
        val initialPage = pagerState.currentPage
        requestPortraitPlaybackForPage(
            targetPage = initialPage,
            applyInitialSeekOnFirstPage = initialPage == 0
        )
    }

    // 拖动期间仅预取；停稳后才替换媒体，回撤不会打断原视频。
    LaunchedEffect(pagerState, pageItems, isPortraitPlaybackAllowed) {
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

                if (
                    !recommendationsManagedExternally &&
                    shouldLoadMorePortraitRecommendations(
                        committedPage = committedPage,
                        totalItemsCount = pageItems.size,
                        isLoadingMoreRecommendations = isLoadingMoreRecommendations,
                        prefetchThreshold = resolvePortraitRecommendationPrefetchThreshold(
                            onlyVerticalRecommendations = onlyVerticalRecommendations,
                        ),
                    ) &&
                    bvid !in appendedRecommendationSeeds
                ) {
                    appendedRecommendationSeeds.add(bvid)
                    isLoadingMoreRecommendations = true
                    launch {
                        try {
                            val (existingBvids, existingRecommendations, startingFeedCursor) = withContext(Dispatchers.Main.immediate) {
                                Triple(
                                    snapshotPortraitPageBvids(pageItems),
                                    recommendationItems.toList(),
                                    recommendationFeedCursor
                                )
                            }
                            val verifiedHomeRecommendations = mutableListOf<RelatedVideo>()
                            var nextFeedCursor = startingFeedCursor
                            var fetchAttempts = 0
                            val fetchAttemptLimit = resolvePortraitRecommendationFetchAttemptLimit(
                                onlyVerticalRecommendations = onlyVerticalRecommendations,
                            )
                            while (
                                fetchAttempts < fetchAttemptLimit &&
                                verifiedHomeRecommendations.size < 8
                            ) {
                                val homeFeedPage = VideoRepository.getHomeVideos(idx = nextFeedCursor)
                                    .getOrNull()
                                    .orEmpty()
                                    .mapNotNull(::toRelatedVideoForPortraitRecommendation)
                                nextFeedCursor += 1
                                fetchAttempts += 1
                                verifiedHomeRecommendations +=
                                    filterPortraitOnlyVerticalRecommendations(
                                        recommendations = homeFeedPage,
                                        enabled = onlyVerticalRecommendations,
                                    )
                            }
                            withContext(Dispatchers.Main.immediate) {
                                recommendationFeedCursor = nextFeedCursor
                            }
                            val relatedFallbackRecommendations = if (verifiedHomeRecommendations.size < 8) {
                                VideoRepository.getRelatedVideos(bvid)
                            } else {
                                emptyList()
                            }
                            val verticalFetchedRecommendations =
                                filterPortraitOnlyVerticalRecommendations(
                                    recommendations =
                                        verifiedHomeRecommendations + relatedFallbackRecommendations,
                                    enabled = onlyVerticalRecommendations,
                                )
                            val shuffledFetchedRecommendations = shufflePortraitRecommendations(
                                seed = resolvePortraitRecommendationAppendSeed(
                                    baseSeed = recommendationShuffleSeed,
                                    currentBvid = bvid
                                ),
                                recommendations = verticalFetchedRecommendations,
                                precedingOwnerMid = withContext(Dispatchers.Main.immediate) {
                                    pageItems.lastOrNull()?.let(::resolvePortraitPageOwnerMid) ?: 0L
                                }
                            )
                            val appendItems = mergePortraitRecommendationAppendItems(
                                currentBvid = bvid,
                                existingBvids = existingBvids,
                                existingRecommendations = existingRecommendations,
                                fetchedRecommendations = shuffledFetchedRecommendations
                            )
                            if (appendItems.isNotEmpty()) {
                                withContext(Dispatchers.Main.immediate) {
                                    recommendationItems.addAll(appendItems)
                                    pageItems.addAll(appendItems)
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

    Box(modifier = Modifier.fillMaxSize()) {
    VerticalPager(
        state = pagerState,
        userScrollEnabled = shouldEnablePortraitPagerUserScroll(
            scale = currentPageScale,
            commentOverlayActive = portraitCommentOverlayActive,
            upPreviewActive = portraitUpPreviewActive
        ),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) { page ->
        val item = pageItems.getOrNull(page)
        val handleUserClick: (Long) -> Unit = { mid ->
            capturePortraitInactivitySnapshot()
            onUserClick(mid)
        }
        val jumpToPortraitPageForVideo: (String, Long, UgcSeason?) -> Unit =
            jump@{ targetBvid, targetCid, collectionContext ->
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
                    pageItems.add(insertAt, inserted)
                    scope.launch {
                        pagerState.animateScrollToPage(insertAt)
                    }
                }
            }

        if (item != null) {
            val itemBvid = when (item) {
                is ViewInfo -> item.bvid
                is RelatedVideo -> item.bvid
                else -> ""
            }
            VideoPageItem(
                item = item,
                isCurrentPage = page == pagerState.settledPage,
                onBack = onBack,
                onHomeClick = onHomeClick,
                viewModel = viewModel,
                commentViewModel = commentViewModel,
                engagementState = engagementState,
                favoriteQuickSaveDefaultFolder = favoriteQuickSaveDefaultFolder,
                onToggleFollow = engagementViewModel::toggleFollow,
                onToggleLike = engagementViewModel::toggleLike,
                onTripleAction = engagementViewModel::doTripleAction,
                onOpenCoinDialog = engagementViewModel::openCoinDialog,
                exoPlayer = exoPlayer, // [核心] 传递共享播放器
                useTextureSurfaceForNavigation = useTextureSurfaceForNavigation,
                isPlayerSurfaceReady = isPlayerSurfaceReady,
                isInPipMode = isInPipMode,
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
                onRotateToLandscape = onRotateToLandscape,
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
                requestedAudioQuality = portraitRequestedAudioQuality,
                selectedAudioQuality = portraitSelectedAudioQuality,
                availableAudioQualities = portraitAvailableAudioQualities,
                aspectRatio = portraitAspectRatio,
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
                    switchPortraitAudioQuality(
                        audioQuality = audioQuality,
                        persistManualSelection = true
                    )
                },
                onPlaybackSpeedSelected = { speed ->
                    val previousSpeed = exoPlayer.playbackParameters.speed
                    val normalizedSpeed = speed.coerceAtLeast(0.1f)
                    exoPlayer.playbackParameters = PlaybackParameters(normalizedSpeed, 1.0f)
                    if (shouldRefreshPremiumAudioForPlaybackSpeedChange(
                            requestedAudioQuality = portraitRequestedAudioQuality,
                            previousPlaybackSpeed = previousSpeed,
                            nextPlaybackSpeed = normalizedSpeed
                        )
                    ) {
                        switchPortraitAudioQuality(
                            audioQuality = portraitRequestedAudioQuality,
                            persistManualSelection = false
                        )
                    }
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
                portraitOverlayVisible = portraitOverlayVisible,
                onPortraitOverlayVisibleChange = { visible ->
                    portraitOverlayVisible = visible
                },
                onRequestVideoChange = { targetBvid ->
                    val normalizedBvid = targetBvid.trim()
                    if (normalizedBvid.isEmpty()) return@VideoPageItem
                    // UpPreview / detail picks may sit outside the current portrait pager.
                    // jumpToPortraitPageForVideo scrolls when present and inserts when missing.
                    jumpToPortraitPageForVideo(normalizedBvid, 0L, null)
                },
                onRequestCollectionItem = jumpToPortraitPageForVideo
            )
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
                        playWhenReadyOverride = failure.snapshot.playWhenReady
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
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 96.dp)
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

    com.android.purebilibili.feature.video.ui.components.CoinDialog(
        visible = engagementState.coinDialogVisible,
        currentCoinCount = engagementState.coinCount,
        userBalance = engagementState.userCoinBalance,
        maxCoins = engagementState.coinLimit,
        onDismiss = { engagementViewModel.setCoinDialogVisible(false) },
        onConfirm = { count, alsoLike ->
            engagementViewModel.doCoin(count = count, alsoLike = alsoLike)
        }
    )
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
    engagementState: VideoEngagementUiState,
    favoriteQuickSaveDefaultFolder: Boolean,
    onToggleFollow: (Long?, Boolean?) -> Unit,
    onToggleLike: (Long?, String?, Boolean?, ((Boolean) -> Unit)?) -> Unit,
    onTripleAction: (Long?, String?, Boolean?, Int?, Boolean?, ((TripleActionResult) -> Unit)?) -> Unit,
    onOpenCoinDialog: () -> Unit,
    exoPlayer: ExoPlayer,
    useTextureSurfaceForNavigation: Boolean,
    isPlayerSurfaceReady: Boolean,
    /** 系统小窗：只保留播放面与弹幕（受「小窗和画中画中不显示弹幕」约束）。 */
    isInPipMode: Boolean = false,
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
    onRotateToLandscape: () -> Unit,
    onProgressUpdate: (String, Long, Long, String) -> Unit,
    watchLaterVideos: List<RelatedVideo>,
    recommendationVideos: List<RelatedVideo>,
    knownVideoAspectRatio: Float?,
    loadedPageInfo: ViewInfo?,
    qualityLabel: String,
    selectedQualityId: Int,
    availableQualityIds: List<Int>,
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
    hasRenderedFirstFrame: Boolean,
    initialProgressPositionMs: Long,
    onCurrentPageScaleChange: (Float) -> Unit,
    onCommentOverlayActiveChange: (Boolean) -> Unit = {},
    onUpPreviewActiveChange: (Boolean) -> Unit,
    portraitOverlayVisible: Boolean,
    onPortraitOverlayVisibleChange: (Boolean) -> Unit,
    onRequestVideoChange: (String) -> Unit,
    onRequestCollectionItem: (bvid: String, cid: Long, collectionContext: UgcSeason?) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val portraitPageBackdrop = com.android.purebilibili.core.ui.blur.rememberChromeBackdropSource()
    val portraitGlassSupported = com.android.purebilibili.feature.video.ui.section.supportsPlayerPageGlass(
        liquidGlassEnabled = com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
        fullscreen = true,
        portraitFullscreen = true,
        hdr = requiresHdrSurfaceOutput(
            currentQualityId = selectedQualityId,
            colorTransfer = exoPlayer.videoFormat?.colorInfo?.colorTransfer ?: 0,
        ),
        anime4k = false,
        audioOnly = false,
        pip = isInPipMode,
    )
    val portraitGlassVisibility = androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isCurrentPage && portraitOverlayVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween<Float>(
            com.android.purebilibili.feature.video.ui.overlay.PLAYER_GLASS_VISIBILITY_DURATION_MS,
        ),
        label = "portraitPlayerGlassCapture",
    )
    val portraitGlassCaptureVisible by remember(portraitGlassVisibility) {
        derivedStateOf { portraitGlassVisibility.value > 0f }
    }
    val capturePortraitPage = portraitGlassSupported && isCurrentPage && isPortraitPlaybackAllowed &&
        (portraitOverlayVisible || portraitGlassCaptureVisible)

    val longPressSpeed by SettingsManager
        .getLongPressSpeed(context)
        .collectAsStateWithLifecycle(initialValue = 2.0f
        )
    val longPressSpeedHintCloseEnabled by SettingsManager
        .getLongPressSpeedHintCloseEnabled(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getLongPressSpeedHintCloseEnabledSync(context)
        )
    val longPressSpeedHintHidden by SettingsManager
        .getLongPressSpeedHintHidden(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getLongPressSpeedHintHiddenSync(context)
        )
    val longPressSpeedHintScale by SettingsManager
        .getLongPressSpeedHintScale(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getLongPressSpeedHintScaleSync(context)
        )
    val longPressSpeedHintAlpha by SettingsManager
        .getLongPressSpeedHintAlpha(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getLongPressSpeedHintAlphaSync(context)
        )
    val doubleTapSeekEnabled by SettingsManager
        .getDoubleTapSeekEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val seekForwardSeconds by SettingsManager
        .getSeekForwardSeconds(context)
        .collectAsStateWithLifecycle(initialValue = 10)
    val seekBackwardSeconds by SettingsManager
        .getSeekBackwardSeconds(context)
        .collectAsStateWithLifecycle(initialValue = 10)
    // 竖屏流此前没有小窗概念，弹幕会无条件跟随；进入 PiP 后与播放器共用同一套
    // 「小窗和画中画中不显示弹幕」设置，避免两条播放面在小窗里策略不一致。
    val pipNoDanmakuEnabled by SettingsManager
        .getPipNoDanmakuEnabled(context)
        .collectAsStateWithLifecycle(initialValue = false)
    val pipDanmakuVisible = !(isInPipMode && pipNoDanmakuEnabled)
    val playerInteractionSettings by SettingsManager
        .getPlayerInteractionSettings(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.PlayerInteractionSettings()
        )
    val currentAudioQuality = requestedAudioQuality
    val bvid = if (item is ViewInfo) item.bvid else (item as RelatedVideo).bvid
    val itemAid = if (item is ViewInfo) item.aid else (item as RelatedVideo).aid
    val currentUiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val currentSuccess = (currentUiState as? VideoPlaybackUiState.Success)
        ?.withEngagementUiState(engagementState)
    
    // [修复] 手动监听 ExoPlayer 播放状态，确保 UI 及时更新
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentPlaybackSpeed by remember(exoPlayer) {
        mutableFloatStateOf(exoPlayer.playbackParameters.speed)
    }
    var showSpeedMenu by rememberSaveable(bvid) { mutableStateOf(false) }
    var showAudioQualityMenu by rememberSaveable(bvid) { mutableStateOf(false) }
    var showQualityMenu by rememberSaveable(bvid) { mutableStateOf(false) }
    var showRatioMenu by rememberSaveable(bvid) { mutableStateOf(false) }
    var showSubtitlePanel by rememberSaveable(bvid) { mutableStateOf(false) }
    var subtitleTrackAvailable by remember(bvid) { mutableStateOf(false) }
    var subtitleOverlayEnabled by remember(bvid) { mutableStateOf(false) }
    val audioQualityPresentation = remember(availableAudioQualities, selectedAudioQuality) {
        resolveAudioQualityControlPresentation(
            options = availableAudioQualities,
            selectedAudioQuality = selectedAudioQuality
        )
    }
    val subtitleAutoPreference by SettingsManager
        .getSubtitleAutoPreference(context)
        .collectAsStateWithLifecycle(initialValue = SubtitleAutoPreference.OFF)
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
                currentPlayingBvid = currentPlayingBvid,
                playerVideoWidth = exoPlayer.videoSize.width,
                playerVideoHeight = exoPlayer.videoSize.height,
                knownVideoAspectRatio = knownVideoAspectRatio
            )
        )
    }
    
    DisposableEffect(exoPlayer) {
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

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                currentPlaybackSpeed = playbackParameters.speed
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    currentVideoAspect = resolvePortraitRuntimeVideoAspectRatio(
                        knownVideoAspectRatio = knownVideoAspectRatio,
                        playerVideoWidth = videoSize.width,
                        playerVideoHeight = videoSize.height
                    )
                }
            }
        }
        exoPlayer.addListener(listener)
        currentPlaybackSpeed = exoPlayer.playbackParameters.speed
        updateAwakeState()
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // [逻辑] 只有当播放器正在播放当前视频时，才显示 PlayerView
    val isPlayerReadyForThisVideo = bvid == currentPlayingBvid
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

    LaunchedEffect(
        playerViewRef,
        isCurrentPage,
        isPlayerReadyForThisVideo,
        currentPlayingBvid,
        exoPlayer.videoSize
    ) {
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
        isPlayerReadyForThisVideo
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
    var showCommentSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var showUpPreview by remember(bvid) { mutableStateOf(false) }
    var commentSheetVisibilityProgress by remember { mutableFloatStateOf(0f) }
    var upPreviewVisibilityProgress by remember(bvid) { mutableFloatStateOf(0f) }
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
            isLandscape = portraitPageWidthPx > portraitPageHeightPx
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
    // UP 预览 / 简介半屏打开时必须关掉页级 pointerInput。
    // 父层 awaitFirstDown(requireUnconsumed=false) 会在整段单指滑动中占住手势循环，
    // LazyVerticalGrid 收不到跟手滚动（评论半屏靠 playerGesturesEnabled=false 才正常）。
    val playerGesturesEnabled =
        commentExpansionTransform.playerGesturesEnabled &&
            !showDetailSheet

    LaunchedEffect(isCurrentPage, bvid) {
        if (!isCurrentPage) {
            showCommentSheet = false
            showDetailSheet = false
            showUpPreview = false
            showSubtitlePanel = false
            showAudioQualityMenu = false
            showQualityMenu = false
            showRatioMenu = false
            showSpeedMenu = false
            commentSheetVisibilityProgress = 0f
            upPreviewVisibilityProgress = 0f
            onCommentOverlayActiveChange(false)
            onUpPreviewActiveChange(false)
        }
    }

    LaunchedEffect(
        isCurrentPage,
        showCommentSheet,
        subReplyState.visible,
        commentSheetVisibilityProgress
    ) {
        onCommentOverlayActiveChange(
            isCurrentPage && shouldBlockPortraitPagerScrollForCommentOverlay(
                commentSheetVisible = showCommentSheet,
                subReplyVisible = subReplyState.visible,
                commentVisibilityProgress = commentSheetVisibilityProgress
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
    var progressState by remember(bvid, initialDuration, initialProgressPositionMs) {
        mutableStateOf(
            PlayerProgress(
                current = initialProgressPositionMs.coerceAtLeast(0L),
                duration = initialDuration,
                buffered = initialProgressPositionMs.coerceAtLeast(0L)
            )
        )
    }
    var seekSession by remember(bvid, initialProgressPositionMs) {
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
    LaunchedEffect(isCurrentPage, exoPlayer, hasRenderedFirstFrame, isPortraitPlaybackAllowed) {
        if (isCurrentPage && isPortraitPlaybackAllowed) {
            while (true) {
                if (isPlayerReadyForThisVideo) {
                    val playerPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val effectivePosition = if (
                        playerPosition == 0L &&
                        progressState.current > 0L &&
                        !hasRenderedFirstFrame
                    ) {
                        progressState.current
                    } else {
                        playerPosition
                    }
                    val realDuration = if (exoPlayer.duration > 0) exoPlayer.duration else initialDuration
                    seekSession = syncPlaybackSeekSession(
                        state = seekSession,
                        playbackPositionMs = effectivePosition,
                        hasPlaybackResumedAfterPendingSeek = exoPlayer.isPlaying
                    )
                    progressState = PlayerProgress(
                        current = seekSession.sliderPositionMs,
                        duration = realDuration,
                        buffered = exoPlayer.bufferedPosition
                    )
                    if (exoPlayer.isPlaying || effectivePosition > 0L) {
                        if (shouldCommitPortraitProgressToDetailState(
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
                    }
                }
                    if (portraitOverlayVisible) {
                        delay(200L)
                    } else {
                        delay(1000L)
                    }
            }
        }
    }

    LaunchedEffect(
        seekSession.pendingSeekPositionMs,
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
        // 竖屏长按只是临时倍速，不走详情页 ViewModel，避免用入口视频状态触发音频流重建。
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

    var controlsInteractionGeneration by remember(bvid) { mutableLongStateOf(0L) }
    var controlsPointerPressed by remember(bvid) { mutableStateOf(false) }
    var controlsLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { owner, _ ->
            controlsLifecycleResumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val danmakuInputVisible by viewModel.showDanmakuInputDialog.collectAsStateWithLifecycle()
    val favoriteFoldersVisible by viewModel.favoriteFolderDialogVisible.collectAsStateWithLifecycle()
    val controlsOverlayOpen = showCommentSheet || subReplyState.visible || showDetailSheet ||
        showUpPreview || commentSheetVisibilityProgress > 0.001f || upPreviewVisibilityProgress > 0.001f ||
        showSpeedMenu || showAudioQualityMenu || showQualityMenu || showRatioMenu || showSubtitlePanel ||
        danmakuInputVisible || favoriteFoldersVisible
    val autoHidePortraitControls = shouldAutoHidePortraitControls(
        isCurrentPage = isCurrentPage,
        playbackActive = controlsLifecycleResumed && isPortraitPlaybackAllowed &&
            isPlayerReadyForThisVideo && isPlaying && !isLoading,
        controlsVisible = portraitOverlayVisible,
        userInteracting = controlsPointerPressed || isLongPressing || seekSession.isSliderMoving,
        overlayOpen = controlsOverlayOpen,
    )
    val latestOnPortraitOverlayVisibleChange by rememberUpdatedState(onPortraitOverlayVisibleChange)
    LaunchedEffect(bvid, isCurrentPage) {
        if (isCurrentPage) latestOnPortraitOverlayVisibleChange(true)
    }
    // User activity and blocking overlays restart the countdown; playback-position
    // polling does not. Hidden controls stay hidden until the existing tap gesture.
    LaunchedEffect(bvid, autoHidePortraitControls, controlsInteractionGeneration) {
        if (!autoHidePortraitControls) return@LaunchedEffect
        delay(PORTRAIT_CONTROLS_AUTO_HIDE_DELAY_MS)
        // A down event can arrive before recomposition cancels this effect.
        if (!controlsPointerPressed && !isLongPressing && !seekSession.isSliderMoving) {
            latestOnPortraitOverlayVisibleChange(false)
        }
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
    val letterboxAmbientHazeEnabled by SettingsManager
        .getPortraitLetterboxAmbientHaze(context)
        .collectAsStateWithLifecycle(
            initialValue = SettingsManager.getPortraitLetterboxAmbientHazeSync(context),
        )
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
    val letterboxBarHeightDp = with(density) { letterboxBarHeightPx.toDp() }
    val letterboxAmbientFrame = remember(bvid) { mutableStateOf<ImageBitmap?>(null) }
    val shouldCaptureLetterboxAmbient = shouldCapturePortraitLetterboxAmbientFrame(
        isCurrentPage = isCurrentPage,
        letterboxAmbientHazeEnabled = letterboxAmbientHazeEnabled,
        letterboxBarHeightPx = letterboxBarHeightPx,
        isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
    )
    LaunchedEffect(
        lifecycleOwner,
        playerViewRef,
        shouldCaptureLetterboxAmbient,
        isPlaying,
        bvid,
    ) {
        if (!shouldCaptureLetterboxAmbient) {
            letterboxAmbientFrame.value = null
            return@LaunchedEffect
        }
        val playerView = playerViewRef ?: return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                if (playerView.isAttachedToWindow && playerView.width > 0 && playerView.height > 0) {
                    letterboxAmbientFrame.value = captureVideoAmbientFrame(
                        playerView = playerView,
                        targetWidth = VIDEO_STATUS_BAR_AMBIENT_SAMPLE_WIDTH_PX,
                        targetHeight = VIDEO_STATUS_BAR_AMBIENT_SAMPLE_HEIGHT_PX,
                    )?.asImageBitmap()
                }
                if (!isPlaying) break
                delay(VIDEO_STATUS_BAR_AMBIENT_CAPTURE_INTERVAL_MS)
            }
        }
    }

    val danmakuTapCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { danmakuTapCoordinates.value = it }
            .onSizeChanged { size ->
                portraitPageWidthPx = size.width
                portraitPageHeightPx = size.height
            }
            .pointerInput(isCurrentPage, bvid) {
                if (!isCurrentPage) return@pointerInput
                // Observe controls and video gestures without taking their input.
                // Initial-pass observation includes child buttons, progress scrubs,
                // and menus whose own handlers consume the pointer in Main.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    controlsPointerPressed = true
                    controlsInteractionGeneration++
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        controlsPointerPressed = false
                        controlsInteractionGeneration++
                    }
                }
            }
            .pointerInput(isCurrentPage, bvid, playerGesturesEnabled) {
                if (!isCurrentPage || !playerGesturesEnabled) return@pointerInput

                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var observedMultiTouch = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount == 0) {
                            break
                        }
                        if (pressedCount < 2) {
                            if (observedMultiTouch) {
                                break
                            }
                            continue
                        }
                        observedMultiTouch = true

                        if (isLongPressing) {
                            applyPortraitTemporaryPlaybackParameters(longPressOriginPlaybackParameters)
                            isLongPressing = false
                            showLongPressSpeedFeedback = false
                        }

                        val pan = event.calculatePan()
                        val zoom = event.calculateZoom()

                        if (zoom != 1f || pan != Offset.Zero) {
                            val updatedScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = updatedScale

                            if (updatedScale > 1f) {
                                val maxPanX = (size.width * updatedScale - size.width) / 2f
                                val maxPanY = (size.height * updatedScale - size.height) / 2f
                                panX = (panX + pan.x * updatedScale).coerceIn(-maxPanX, maxPanX)
                                panY = (panY + pan.y * updatedScale).coerceIn(-maxPanY, maxPanY)
                                onPortraitOverlayVisibleChange(false)
                            } else {
                                panX = 0f
                                panY = 0f
                            }

                            event.changes.forEach { change ->
                                if (change.position != change.previousPosition) {
                                    change.consume()
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(
                longPressSpeed,
                currentAudioQuality,
                isCurrentPage,
                playerGesturesEnabled,
                portraitOverlayVisible,
                danmakuEnabled,
                danmakuManager,
                doubleTapSeekEnabled,
                seekForwardSeconds,
                seekBackwardSeconds
            ) {
                var capturedDanmakuTap: (() -> Unit)? = null
                detectTapGestures(
                    onPress = { offset ->
                        capturedDanmakuTap = null
                        val candidate = if (playerGesturesEnabled && isCurrentPage && danmakuEnabled &&
                            shouldHandlePortraitTapGesture(scale = scale)
                        ) danmakuManager.captureTap(danmakuTapCoordinates.value, offset) else null
                        try {
                            if (tryAwaitRelease() && !isLongPressing) capturedDanmakuTap = candidate
                        } finally {
                            // 弹幕单击捕获与长按倍速共用 onPress，松手或取消都恢复原播放参数。
                            if (isLongPressing) {
                                applyPortraitTemporaryPlaybackParameters(longPressOriginPlaybackParameters)
                                isLongPressing = false
                                showLongPressSpeedFeedback = false
                            }
                        }
                    },
                    onTap = {
                        if (
                            !playerGesturesEnabled ||
                            !isCurrentPage ||
                            !shouldHandlePortraitTapGesture(scale = scale)
                        ) {
                            return@detectTapGestures
                        }
                        capturedDanmakuTap?.let { click ->
                            capturedDanmakuTap = null
                            click()
                            return@detectTapGestures
                        }
                        onPortraitOverlayVisibleChange(
                            resolvePortraitOverlayVisibilityAfterTap(portraitOverlayVisible)
                        )
                    },
                    onDoubleTap = { offset ->
                        if (
                            !playerGesturesEnabled ||
                            !shouldHandlePortraitTapGesture(scale = scale) ||
                            !isCurrentPage
                        ) {
                            return@detectTapGestures
                        }
                        val screenWidth = size.width.toFloat()
                        val relativeX = if (screenWidth > 0f) {
                            offset.x / screenWidth
                        } else {
                            0.5f
                        }
                        when (
                            resolveFullscreenDoubleTapAction(
                                relativeX = relativeX,
                                doubleTapSeekEnabled = doubleTapSeekEnabled,
                                playWhenReady = exoPlayer.playWhenReady,
                                isPlaying = exoPlayer.isPlaying,
                                playbackState = exoPlayer.playbackState
                            )
                        ) {
                            FullscreenDoubleTapAction.SeekBackward -> {
                                val seekMs = seekBackwardSeconds * 1000L
                                val newPos = resolveRelativeSeekTargetPosition(
                                    currentPositionMs = exoPlayer.currentPosition,
                                    deltaMs = -seekMs,
                                    durationMs = exoPlayer.duration
                                )
                                seekPlayerFromUserAction(exoPlayer, newPos)
                                danmakuManager.seekTo(newPos)
                                progressState = progressState.copy(current = newPos)
                                val feedback = nextFullscreenSeekFeedbackEvent(
                                    previousGeneration = seekFeedbackGeneration,
                                    deltaSeconds = -seekBackwardSeconds
                                )
                                seekFeedbackGeneration = feedback.generation
                                seekFeedbackText = feedback.text
                                seekFeedbackVisible = true
                            }
                            FullscreenDoubleTapAction.SeekForward -> {
                                val seekMs = seekForwardSeconds * 1000L
                                val newPos = resolveRelativeSeekTargetPosition(
                                    currentPositionMs = exoPlayer.currentPosition,
                                    deltaMs = seekMs,
                                    durationMs = exoPlayer.duration
                                )
                                seekPlayerFromUserAction(exoPlayer, newPos)
                                danmakuManager.seekTo(newPos)
                                progressState = progressState.copy(current = newPos)
                                val feedback = nextFullscreenSeekFeedbackEvent(
                                    previousGeneration = seekFeedbackGeneration,
                                    deltaSeconds = seekForwardSeconds
                                )
                                seekFeedbackGeneration = feedback.generation
                                seekFeedbackText = feedback.text
                                seekFeedbackVisible = true
                            }
                            FullscreenDoubleTapAction.TogglePlayPause -> {
                                togglePlayerPlaybackFromUserAction(exoPlayer)
                            }
                        }
                    },
                    onLongPress = {
                        if (
                            !playerGesturesEnabled ||
                            !shouldHandlePortraitLongPressGesture(scale = scale)
                        ) {
                            return@detectTapGestures
                        }
                        if (!isCurrentPage) return@detectTapGestures
                        longPressOriginPlaybackParameters = exoPlayer.playbackParameters
                        val longPressPlaybackParameters = resolveLongPressPlaybackParameters(
                            requestedSpeed = longPressSpeed,
                            currentAudioQuality = currentAudioQuality
                        )
                        effectiveLongPressSpeed = longPressPlaybackParameters.speed
                        applyPortraitTemporaryPlaybackParameters(longPressPlaybackParameters)
                        isLongPressing = true
                        longPressSpeedHintDismissed = false
                        showLongPressSpeedFeedback = true
                    }
                )
            }
            // 进度调整手势
            .pointerInput(
                bvid,
                exoPlayer,
                progressState.duration,
                scale,
                isCurrentPage,
                playerGesturesEnabled,
                playerInteractionSettings
            ) {
                var totalDragDistanceX = 0f
                try {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isSeekGesture = false
                            totalDragDistanceX = 0f
                            if (
                                isCurrentPage &&
                                playerGesturesEnabled &&
                                progressState.duration > 0L &&
                                size.width > 0 &&
                                shouldHandlePortraitSeekGesture(scale = scale)
                            ) {
                                seekStartPosition = exoPlayer.currentPosition
                                    .coerceIn(0L, progressState.duration)
                                seekTargetPosition = seekStartPosition
                                isSeekGesture = true
                            }
                        },
                        onDragEnd = {
                            try {
                                if (isCurrentPage && isSeekGesture) {
                                    seekPlayerFromUserAction(exoPlayer, seekTargetPosition)
                                    danmakuManager.seekTo(seekTargetPosition)
                                }
                            } finally {
                                isSeekGesture = false
                                totalDragDistanceX = 0f
                            }
                        },
                        onDragCancel = {
                            isSeekGesture = false
                            totalDragDistanceX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (isCurrentPage && isSeekGesture && progressState.duration > 0L) {
                                totalDragDistanceX += dragAmount
                                val seekDelta = resolveHorizontalSeekDeltaMs(
                                    isFullscreen = true,
                                    fullscreenSwipeSeekEnabled = playerInteractionSettings.fullscreenSwipeSeekEnabled,
                                    totalDragDistanceX = totalDragDistanceX,
                                    containerWidthPx = size.width.toFloat(),
                                    fullscreenSwipeSeekSeconds = playerInteractionSettings.fullscreenSwipeSeekSeconds,
                                    inlineSwipeSeekSeconds = playerInteractionSettings.inlineSwipeSeekSeconds,
                                    gestureSensitivity = playerInteractionSettings.gestureSensitivity,
                                    durationMs = progressState.duration,
                                    useRelativeDuration = playerInteractionSettings.swipeSeekUseRelativeDuration,
                                    relativeSeekPercent = playerInteractionSettings.swipeSeekRelativePercent
                                )
                                if (seekDelta != null) {
                                    seekTargetPosition = (seekStartPosition + seekDelta)
                                        .coerceIn(0L, progressState.duration)
                                }
                            }
                        }
                    )
                } finally {
                    isSeekGesture = false
                }
            }
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
        val danmakuSurfaceMode = resolvePortraitDanmakuSurfaceMode(
            currentVideoAspect = currentVideoAspect,
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
        val pageDanmakuModifier = mediaLayerModifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = panX
                translationY = panY
            }


        // 横屏 letterbox 上下黑边：照搬播放页沉浸状态栏的动态 haze（默认可关）
        if (letterboxBarHeightPx > 0) {
            ImmersiveAmbientLetterboxBackdrop(
                ambientFrame = letterboxAmbientFrame,
                height = letterboxBarHeightDp,
                useAmbientHaze = letterboxAmbientHazeEnabled,
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(0f),
            )
            ImmersiveAmbientLetterboxBackdrop(
                ambientFrame = letterboxAmbientFrame,
                height = letterboxBarHeightDp,
                useAmbientHaze = letterboxAmbientHazeEnabled,
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(0f),
            )
        }

        // Wait for persisted surface settings while retaining format-aware HDR selection.
        if (isCurrentPage && isPlayerReadyForThisVideo && isPlayerSurfaceReady) {
            val surfaceHdrRequired = com.android.purebilibili.feature.video.ui.components.rememberVideoSurfaceHdrRequired(
                exoPlayer, selectedQualityId,
            )
            val preferTexture = shouldUseTextureSurfaceForFlip(
                isFlippedHorizontal = false,
                isFlippedVertical = false,
                navigationTransformEnabled = useTextureSurfaceForNavigation || portraitGlassSupported,
                requiresHdrSurfaceOutput = surfaceHdrRequired,
            )
            PortraitVideoViewportContainer(
                currentVideoAspect = portraitPagerViewportAspect,
                modifier = mediaLayerModifier,
                fillContainer = portraitPagerFillContainer
            ) {
                key(currentPlayingBvid, bvid, preferTexture) {
                    Box(
                        modifier = viewportTransformModifier
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val basePlayerView = if (preferTexture) {
                                    android.view.LayoutInflater.from(ctx)
                                        .inflate(
                                            com.android.purebilibili.R.layout.view_player_texture,
                                            null,
                                            false,
                                        ) as PlayerView
                                } else {
                                    PlayerView(ctx)
                                }
                                basePlayerView.apply {
                                    playerViewRef = this
                                    player = exoPlayer
                                    useController = false
                                    keepScreenOn = shouldKeepPortraitPagerItemAwake
                                    resizeMode = portraitPagerResizeMode
                                    setKeepContentOnPlayerReset(true)
                                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                                }
                            },
                            update = { view ->
                                playerViewRef = view
                                if (view.player != exoPlayer) {
                                    view.player = exoPlayer
                                }
                                view.keepScreenOn = shouldKeepPortraitPagerItemAwake
                                // mode 变化：Media3 会 remeasure；同 mode 但 FILL 容器尺寸变：强制刷新一次。
                                val modeChanged = view.resizeMode != portraitPagerResizeMode
                                val sizeTag =
                                    "vp:${view.width}x${view.height}:$portraitPagerResizeMode:$portraitPagerFillContainer"
                                val sizeChanged = view.tag != sizeTag
                                com.android.purebilibili.feature.video.ui.components.applyPlayerViewResizeMode(
                                    playerView = view,
                                    resizeMode = portraitPagerResizeMode,
                                    forceRelayout = false,
                                )
                                if (modeChanged || (portraitPagerFillContainer && sizeChanged)) {
                                    view.tag = sizeTag
                                    com.android.purebilibili.feature.video.ui.components.schedulePlayerViewViewportRefresh(
                                        playerView = view,
                                        resizeMode = portraitPagerResizeMode,
                                    )
                                }
                            },
                            onRelease = { view ->
                                if (playerViewRef === view) playerViewRef = null
                                view.player = null
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (shouldComposePortraitDanmakuOverlay(
                                danmakuEnabled = danmakuEnabled && pipDanmakuVisible,
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
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        if (
            shouldComposePortraitDanmakuOverlay(
                danmakuEnabled = danmakuEnabled && pipDanmakuVisible,
                surfaceMode = danmakuSurfaceMode,
                expectedMode = PortraitDanmakuSurfaceMode.Page,
                isCurrentPage = isCurrentPage,
                isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
            )
        ) {
            PortraitDanmakuOverlay(
                danmakuManager = danmakuManager,
                videoWidth = exoPlayer.videoSize.width,
                videoHeight = exoPlayer.videoSize.height,
                resizeMode = playerViewRef?.resizeMode ?: portraitPagerResizeMode,
                modifier = pageDanmakuModifier.then(
                    if (shouldInsetPortraitDanmakuFromStatusBar(danmakuSurfaceMode)) {
                        Modifier.padding(top = pageDanmakuTopInset)
                    } else {
                        Modifier
                    }
                )
            )
        }

        // 封面图 (在加载中、未匹配到视频、或未开始播放时显示)
        val showCover = !isPlayerSurfaceReady || shouldShowPortraitCover(
            isLoading = isLoading,
            isCurrentPage = isCurrentPage,
            isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
            hasRenderedFirstFrame = hasRenderedFirstFrame
        )
        
        if (showCover) {
            Box(
                modifier = mediaLayerModifier.background(Color.Black)
            ) {
                if (shouldUseViewportBoundPortraitCover(
                        isCurrentPage = isCurrentPage,
                        isPlayerReadyForThisVideo = isPlayerReadyForThisVideo,
                        hasRenderedFirstFrame = hasRenderedFirstFrame
                    )
                ) {
                    PortraitVideoViewportContainer(
                        currentVideoAspect = resolvePortraitCoverViewportAspect(
                            currentVideoAspect = currentVideoAspect,
                            hasRenderedFirstFrame = hasRenderedFirstFrame || knownVideoAspectRatio != null
                        ),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        AsyncImage(
                            model = FormatUtils.fixImageUrl(cover),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = resolvePortraitCoverContentScale()
                        )
                    }
                } else {
                    AsyncImage(
                        model = FormatUtils.fixImageUrl(cover),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = resolvePortraitCoverContentScale()
                    )
                }

                if (isLoading && isCurrentPage) {
                    AdaptiveLoadingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
            }
        }

        } // media backdrop source

        // 滑动进度提示
        if (isSeekGesture && progressState.duration > 0) {
            val gestureVideoshotData = currentSuccess?.videoshotData
            if (gestureVideoshotData != null && gestureVideoshotData.isValid) {
                val previewConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
                val gesturePreviewSize = remember(
                    gestureVideoshotData.img_x_size,
                    gestureVideoshotData.img_y_size,
                    previewConfiguration.screenWidthDp
                ) {
                    com.android.purebilibili.feature.video.ui.components.resolveCompactSeekPreviewSize(
                        sourceWidthPx = gestureVideoshotData.img_x_size,
                        sourceHeightPx = gestureVideoshotData.img_y_size,
                        screenWidthDp = previewConfiguration.screenWidthDp,
                        // 使用真实视频宽比：横屏内容预览不再被强行裁成 9:16
                        videoAspectRatio = currentVideoAspect
                    )
                }
                val previewWidthPx = with(density) { gesturePreviewSize.widthDp.dp.toPx() }
                val previewProgress = (
                    seekTargetPosition / progressState.duration.toFloat()
                ).coerceIn(0f, 1f)
                val previewOffsetX =
                    com.android.purebilibili.feature.video.ui.components.resolveSeekPreviewBubbleOffsetPx(
                        placement = com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement.Anchored,
                        offsetX = portraitPageWidthPx * previewProgress,
                        containerWidth = portraitPageWidthPx.toFloat(),
                        bubbleWidthPx = previewWidthPx
                    )
                val previewBottomOffsetPx = with(density) { 120.dp.roundToPx() }
                com.android.purebilibili.feature.video.ui.components.CompactSeekPreview(
                    videoshotData = gestureVideoshotData,
                    targetPositionMs = seekTargetPosition,
                    durationMs = progressState.duration,
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
                val totalTimeText = FormatUtils.formatDuration(progressState.duration)
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
        AnimatedVisibility(
            visible = shouldShowLongPressSpeedFeedback(
                isLongPressing = isLongPressing,
                isPlaybackSurfaceActive = isCurrentPage,
                hintDismissed = longPressSpeedHintDismissed,
                hintHidden = longPressSpeedHintHidden,
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                // 状态栏可见（关闭沉浸式/瞬态显示）时随系统栏 inset 下移，避免被预留位遮挡。
                .padding(top = (pageDanmakuTopInset + 16.dp).coerceAtLeast(48.dp)),
            enter = fadeIn(animationSpec = tween(200)) + slideInVertically(initialOffsetY = { -it }),
            exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { -it })
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "fast_forward_portrait")
            val arrow1Alpha by infiniteTransition.animateFloat(
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
            val arrow2Alpha by infiniteTransition.animateFloat(
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
            val arrow3Alpha by infiniteTransition.animateFloat(
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                // 透明度设置作用于整个提示（箭头 + 文字 + 关闭按钮）。
                modifier = Modifier
                    .graphicsLayer { alpha = longPressSpeedHintAlpha }
                    .padding(
                        horizontal = 8.dp * longPressSpeedHintScale,
                        vertical = 5.dp * longPressSpeedHintScale
                    )
            ) {
                val arrowAlphas = listOf(arrow1Alpha, arrow2Alpha, arrow3Alpha)
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
                            color = Color.White.copy(alpha = alpha)
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

        // Overlay & Interaction
    val isCurrentModelVideo = (currentUiState as? VideoPlaybackUiState.Success)?.info?.bvid == bvid
    val interactionAccount by TokenManager.accountIdentity.collectAsStateWithLifecycle()
    val interactionSession = interactionAccount to TokenManager.sessDataCache
    val favoriteSaveEvent by viewModel.favoriteFolderSaveEvent.collectAsStateWithLifecycle()
    var consumedFavoriteSaveEventVersion by remember(bvid) { mutableLongStateOf(0L) }
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
    val showVideoDeclarations by remember(context) {
        com.android.purebilibili.core.store.SettingsManager.getVideoArgueMsgShown(context)
    }.collectAsStateWithLifecycle(initialValue = true)
    val videoDeclarations = remember(portraitDetailInfo, showVideoDeclarations) {
        if (!showVideoDeclarations) emptyList() else buildList {
            portraitDetailInfo?.argueInfo?.argueMsg?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            if (portraitDetailInfo?.rights?.noReprint == 1) add("未经作者授权，请勿转载")
        }.distinct()
    }
    // seed 进场可能没有 owner；加载成功后用 Success.info 补齐 UP 名/头像。
    val authorName = portraitDetailInfo?.owner?.name?.takeIf { it.isNotBlank() } ?: seedAuthorName
    val authorFace = portraitDetailInfo?.owner?.face?.takeIf { it.isNotBlank() } ?: seedAuthorFace
    val authorMid = portraitDetailInfo?.owner?.mid?.takeIf { it > 0L } ?: seedAuthorMid
    var portraitInteractionOverride by remember(bvid, activeAid, authorMid, interactionSession) {
        mutableStateOf(PortraitVideoInteractionOverride())
    }
    val interactionSuccess = currentSuccess?.takeIf {
        it.info.bvid == bvid && it.info.aid == activeAid && it.info.owner.mid == authorMid
    }
    val resolvedInteractionState = resolvePortraitVideoInteractionUiState(
        targetBvid = bvid,
        fallbackStat = stat,
        sharedState = interactionSuccess,
        localOverride = portraitInteractionOverride
    )
    // UP 主弹幕徽章：详情就绪后绑定 owner.mid（seed 进场无 owner 时会在详情回填后重新绑定）
    LaunchedEffect(danmakuManager, authorMid) {
        danmakuManager.bindUpOwnerMid(authorMid)
    }
    val followStatusKnown = interactionSuccess?.followStatusKnown == true
    val isFollowing = followStatusKnown && interactionSuccess?.isFollowing == true
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
                com.android.purebilibili.core.store.DanmakuSettingsScope.PORTRAIT
            )
        }
        Unit
    }
    val canHandlePortraitInteraction = shouldHandlePortraitVideoInteraction(
        isCurrentPage = isCurrentPage,
        aid = activeAid,
        bvid = bvid
    )

    LaunchedEffect(favoriteSaveEvent?.version, activeAid) {
        val event = favoriteSaveEvent ?: return@LaunchedEffect
        if (event.aid != activeAid || event.version == consumedFavoriteSaveEventVersion) {
            return@LaunchedEffect
        }
        val nextFavoriteCount = (
            resolvedInteractionState.favoriteCount +
                when {
                    event.isFavorited == resolvedInteractionState.isFavorited -> 0
                    event.isFavorited -> 1
                    else -> -1
                }
            ).coerceAtLeast(0)
        portraitInteractionOverride = portraitInteractionOverride.copy(
            isFavorited = event.isFavorited,
            favoriteCount = nextFavoriteCount
        )
        consumedFavoriteSaveEventVersion = event.version
    }

    LaunchedEffect(isCurrentPage, authorMid) {
        if (isCurrentPage && authorMid > 0L) {
            viewModel.ensureFollowStatus(authorMid)
        }
    }

    CompositionLocalProvider(
        com.android.purebilibili.feature.video.ui.overlay.LocalPlayerPageBackdrop provides
            portraitPageBackdrop.backdrop.takeIf {
                capturePortraitPage && portraitPageBackdrop.isReady &&
                    (!isPlayerReadyForThisVideo || playerViewRef?.videoSurfaceView is android.view.TextureView)
            },
    ) {
    // 系统小窗不合成竖屏流装饰层：标题 / 互动 / 进度条 / 旋转等控件在小窗里无法操作，
    // 缩放后只会变成错误渲染，播放面本身仍在下方正常输出。
    if (!isInPipMode) {
    PortraitFullscreenOverlay(
            playbackReady = isCurrentPage && !isLoading && isPlayerReadyForThisVideo,
            title = title,
            declarations = videoDeclarations,
            ugcSeason = portraitDetailInfo?.ugc_season,
            currentBvid = bvid,
            currentCid = portraitDetailInfo?.cid ?: 0L,
            authorName = authorName,
            authorFace = authorFace,
            staff = portraitDetailInfo?.staff.orEmpty(),
            ownerMid = authorMid,
            onStaffMemberClick = { mid ->
                if (isCurrentPage && mid > 0L) {
                    onUserClick(mid)
                }
            },
            isPlaying = if (isCurrentPage) {
                isPlaying || shouldShowPlaybackRecoveryUiAfterSeek(
                    state = seekSession,
                    playWhenReady = exoPlayer.playWhenReady,
                    isPlaying = exoPlayer.isPlaying,
                    playbackState = exoPlayer.playbackState
                )
            } else {
                false
            },
            progress = progressState,
            
            statView = if(isCurrentModelVideo && currentSuccess != null) currentSuccess.info.stat.view else stat.view,
            statLike = resolvedInteractionState.likeCount,
            statCoin = if (isCurrentModelVideo) {
                engagementState.coinCount.takeIf { it > 0 }
                    ?: currentSuccess?.info?.stat?.coin
                    ?: stat.coin
            } else {
                stat.coin
            },
            statDanmaku = if(isCurrentModelVideo && currentSuccess != null) currentSuccess.info.stat.danmaku else stat.danmaku,
            statReply = if(isCurrentModelVideo && currentSuccess != null) currentSuccess.info.stat.reply else stat.reply,
            statFavorite = resolvedInteractionState.favoriteCount,
            statShare = if(isCurrentModelVideo && currentSuccess != null) currentSuccess.info.stat.share else stat.share,
            
            isLiked = resolvedInteractionState.isLiked,
            likeStatusKnown = resolvedInteractionState.likeStatusKnown,
            isCoined = isCurrentModelVideo && engagementState.coinCount > 0,
            isFavorited = resolvedInteractionState.isFavorited,
            
            isFollowing = isFollowing,
            followStatusKnown = followStatusKnown,
            onFollowClick = {
                if (canHandlePortraitInteraction && authorMid > 0L) onToggleFollow(authorMid, isFollowing)
            },
            
            onDetailClick = {
                if (portraitDetailInfo != null) {
                    showDetailSheet = true
                }
            },
            onTitleClick = {
                if (portraitDetailInfo != null) {
                    showDetailSheet = true
                }
            },
            onAuthorClick = {
                if (isCurrentPage && (portraitDetailInfo?.owner?.mid ?: 0L) > 0L) {
                    showUpPreview = true
                    // Sync before next frame so pager/page pointerInput release immediately.
                    onUpPreviewActiveChange(true)
                }
            },
            onLikeClick = {
                if (canHandlePortraitInteraction) {
                    onToggleLike(activeAid, bvid, resolvedInteractionState.isLiked) { }
                }
            },
            onLikeLongClick = {
                if (canHandlePortraitInteraction && resolvedInteractionState.likeStatusKnown) {
                    val currentInteractionState = resolvedInteractionState
                    onTripleAction(
                        activeAid,
                        bvid,
                        currentInteractionState.isLiked,
                        currentSuccess?.coinCount ?: 0,
                        currentInteractionState.isFavorited
                    ) { }
                }
            },
            onCoinClick = {
                if (canHandlePortraitInteraction) {
                    onOpenCoinDialog()
                }
            },
            onFavoriteClick = {
                if (canHandlePortraitInteraction) {
                    when (
                        resolvePortraitFavoriteAction(
                            isLongPress = false,
                            quickSaveDefaultFolder = favoriteQuickSaveDefaultFolder,
                        )
                    ) {
                        PortraitFavoriteAction.ToggleFavorite -> {
                            val currentFavoriteState = resolvedInteractionState.isFavorited
                            val currentFavoriteCount = resolvedInteractionState.favoriteCount
                            viewModel.toggleFavorite()
                            val nextFavorited = !currentFavoriteState
                            val nextFavoriteCount = (
                                currentFavoriteCount +
                                    if (nextFavorited) 1 else -1
                                ).coerceAtLeast(0)
                            portraitInteractionOverride = portraitInteractionOverride.copy(
                                isFavorited = nextFavorited,
                                favoriteCount = nextFavoriteCount
                            )
                        }
                        PortraitFavoriteAction.OpenFavoriteFolders -> {
                            viewModel.showFavoriteFolderDialog(activeAid)
                        }
                    }
                }
            },
            onFavoriteLongClick = {
                if (canHandlePortraitInteraction) {
                    viewModel.showFavoriteFolderDialog(activeAid)
                }
            },
            onCommentClick = { showCommentSheet = true },
            onShareClick = {
                val shareText = buildPortraitShareText(title = title, bvid = bvid)
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share too..."))
            },
            
            currentSpeed = currentPlaybackSpeed,
            currentQualityLabel = qualityLabel,
            currentAudioQualityLabel = audioQualityPresentation.label,
            isHiResAudioSelected = audioQualityPresentation.showHiResBadge,
            isDolbyAudioSelected = audioQualityPresentation.showDolbyBadge,
            currentRatio = aspectRatio,
            danmakuEnabled = danmakuEnabled,
            isStatusBarHidden = true,
            videoshotData = currentSuccess?.videoshotData,
            videoAspectRatio = currentVideoAspect,
            isPlaybackRecovering = isCurrentPage && shouldShowPlaybackRecoveryUiAfterSeek(
                state = seekSession,
                playWhenReady = exoPlayer.playWhenReady,
                isPlaying = exoPlayer.isPlaying,
                playbackState = exoPlayer.playbackState
            ),
            
            onBack = {
                onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid, cover)
                onBack()
            },
            onHomeClick = {
                onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid, cover)
                onHomeClick()
            },
            onPlayPause = {
                if (isCurrentPage) {
                    togglePlayerPlaybackFromUserAction(exoPlayer)
                }
            },
            onSeek = {
                if (isCurrentPage) {
                    val resolvedDuration = progressState.duration
                        .takeIf { durationMs -> durationMs > 0L }
                        ?: exoPlayer.duration.coerceAtLeast(0L)
                    val targetPosition = resolvePortraitCommittedSeekPosition(
                        requestedPositionMs = it,
                        durationMs = resolvedDuration
                    )
                    val commitResult = finishPlaybackSeekInteraction(
                        updatePlaybackSeekInteraction(
                            state = seekSession,
                            positionMs = targetPosition
                        )
                    )
                    seekSession = commitResult.state
                    progressState = progressState.copy(current = commitResult.committedPositionMs)
                    seekPlayerFromUserAction(
                        player = exoPlayer,
                        positionMs = commitResult.committedPositionMs,
                        shouldResumePlaybackOverride = commitResult.shouldResumePlayback
                    )
                    danmakuManager.seekTo(commitResult.committedPositionMs)
                }
            },
            onSeekStart = { danmakuManager.prepareForSeekScrub() },
            seekPositionMs = seekSession.sliderPositionMs,
            isSeekScrubbing = seekSession.isSliderMoving,
            onSeekDragStart = { position ->
                seekSession = startPlaybackSeekInteraction(
                    state = seekSession,
                    player = exoPlayer,
                    positionMs = position
                )
            },
            onSeekDragUpdate = { position ->
                seekSession = updatePlaybackSeekInteraction(
                    state = seekSession,
                    positionMs = position
                )
            },
            onSeekDragCancel = {
                seekSession = cancelPlaybackSeekInteraction(seekSession)
                danmakuManager.cancelSeekScrub()
            },
            onSpeedClick = {
                if (isCurrentPage) {
                    showSpeedMenu = true
                    showAudioQualityMenu = false
                    showSubtitlePanel = false
                    onPortraitOverlayVisibleChange(true)
                }
            },
            onQualityClick = {
                if (isCurrentPage) {
                    showQualityMenu = true
                    showAudioQualityMenu = false
                    showSubtitlePanel = false
                    onPortraitOverlayVisibleChange(true)
                }
            },
            onAudioQualityClick = {
                if (isCurrentPage) {
                    showAudioQualityMenu = true
                    showQualityMenu = false
                    showSubtitlePanel = false
                    onPortraitOverlayVisibleChange(true)
                }
            },
            onRatioClick = {
                if (isCurrentPage) {
                    showRatioMenu = true
                    showAudioQualityMenu = false
                    showSubtitlePanel = false
                    onPortraitOverlayVisibleChange(true)
                }
            },
            showSubtitleChip = shouldShowPortraitSubtitleChip(
                featureEnabled = isSubtitleFeatureEnabledForUser(),
                trackAvailable = subtitleTrackAvailable
            ),
            subtitleEnabled = subtitleOverlayEnabled,
            onSubtitleClick = {
                if (isCurrentPage) {
                    showSubtitlePanel = !showSubtitlePanel
                    showAudioQualityMenu = false
                    showQualityMenu = false
                    showRatioMenu = false
                    showSpeedMenu = false
                    onPortraitOverlayVisibleChange(true)
                }
            },
            onDanmakuToggle = toggleDanmaku,
            onDanmakuInputClick = {
                if (isCurrentPage) {
                    viewModel.showDanmakuSendDialog()
                }
            },
            onToggleStatusBar = { },
            onSearchClick = {
                onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid, cover)
                onSearchClick()
            },
            onMoreClick = {
                showDetailSheet = true
            },
            onRotateToLandscape = {
                onExitSnapshot(bvid, exoPlayer.currentPosition, snapshotCid, cover)
                onRotateToLandscape()
            },
            
            showControls = resolvePortraitOverlayControlsVisible(
                portraitOverlayVisible = portraitOverlayVisible,
                showDetailSheet = showDetailSheet
            ),
            commentExpansionProgress = commentSheetVisibilityProgress
        )
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
                showDetailSheet = showDetailSheet
            ),
            commentExpansionProgress = commentSheetVisibilityProgress,
            subtitleAutoPreference = subtitleAutoPreference,
            isMuted = exoPlayer.volume <= 0f,
            onSubtitleTrackSelected = { trackKey ->
                viewModel.selectSubtitleTrack(trackKey)
            },
            showSubtitlePanel = showSubtitlePanel && isCurrentPage,
            onShowSubtitlePanelChange = { show ->
                showSubtitlePanel = show
            },
            onSubtitleEnabledChange = { enabled ->
                subtitleOverlayEnabled = enabled
            },
            onTrackAvailableChange = { available ->
                subtitleTrackAvailable = available
            }
        )
        }

        if (showQualityMenu && isCurrentPage) {
            val detailQualityIds = if (isCurrentModelVideo) {
                currentSuccess?.qualityIds.orEmpty()
            } else {
                emptyList()
            }
            val qualityIds = resolvePortraitQualityMenuIds(
                portraitQualityIds = availableQualityIds,
                detailQualityIds = detailQualityIds,
                selectedQualityId = selectedQualityId,
            )
            QualitySelectionMenu(
                qualities = resolvePortraitQualityMenuLabels(qualityIds),
                qualityIds = qualityIds,
                switchableQualityIds = qualityIds,
                currentQuality = qualityLabel,
                isLoggedIn = isLoggedIn,
                isVip = isVip,
                onQualitySelected = { index ->
                    qualityIds.getOrNull(index)?.let(onQualitySelected)
                    showQualityMenu = false
                },
                onDismiss = { showQualityMenu = false },
                useDialog = true
            )
        }

        if (showAudioQualityMenu && isCurrentPage) {
            AudioQualitySelectionMenu(
                options = availableAudioQualities,
                requestedAudioQuality = requestedAudioQuality,
                onAudioQualitySelected = { audioQuality ->
                    onAudioQualitySelected(audioQuality)
                    showAudioQualityMenu = false
                },
                onDismiss = { showAudioQualityMenu = false }
            )
        }

        if (showRatioMenu && isCurrentPage) {
            AspectRatioMenu(
                currentRatio = aspectRatio,
                onRatioSelected = { ratio ->
                    onAspectRatioChange(ratio)
                    showRatioMenu = false
                },
                onDismiss = { showRatioMenu = false }
            )
        }

        if (showSpeedMenu && isCurrentPage) {
            SpeedSelectionMenuDialog(
                currentSpeed = currentPlaybackSpeed,
                onSpeedSelected = { speed ->
                    val normalizedSpeed = speed.coerceAtLeast(0.1f)
                    currentPlaybackSpeed = normalizedSpeed
                    onPlaybackSpeedSelected(normalizedSpeed)
                    scope.launch {
                        SettingsManager.setLastPlaybackSpeed(context, normalizedSpeed)
                    }
                    showSpeedMenu = false
                },
                onDismiss = { showSpeedMenu = false }
            )
        }

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
                ?: progressState.duration.takeIf { it > 0L },
            onRootCommentClick = { viewModel.openRootCommentComposer() },
            onReplyClick = { reply ->
                viewModel.setReplyingTo(reply)
                viewModel.showCommentInputDialog()
            },
            onUserClick = onUserClick
        )

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
            val commentState by commentViewModel.commentState.collectAsStateWithLifecycle()
            val commentFraudDetectionEnabled by com.android.purebilibili.core.store.SettingsManager
                .getCommentFraudDetectionEnabled(context)
                .collectAsStateWithLifecycle(initialValue = true
        )

            LaunchedEffect(activeAid, commentFraudDetectionEnabled) {
                viewModel.commentSentEvent.collect { reply ->
                    commentViewModel.onExternalCommentSent(
                        aid = activeAid,
                        newReply = reply,
                        fraudDetectionEnabled = commentFraudDetectionEnabled
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
            visible = showDetailSheet,
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
            UpPreviewSheet(
                visible = showUpPreview,
                owner = info.owner,
                isFollowing = isFollowing,
                followStatusKnown = followStatusKnown,
                followerCount = currentSuccess?.ownerFollowerCount,
                videoCount = currentSuccess?.ownerVideoCount,
                seedVideos = upPreviewSeedVideos,
                onDismiss = {
                    showUpPreview = false
                    onUpPreviewActiveChange(false)
                },
                onFollowClick = {
                    if (canHandlePortraitInteraction && authorMid > 0L) onToggleFollow(authorMid, isFollowing)
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
    return if (isPlayerReadyForThisVideo) currentPlayingAid.coerceAtLeast(0L) else itemAid
}

internal fun resolvePortraitOverlayControlsVisible(
    portraitOverlayVisible: Boolean,
    showDetailSheet: Boolean
): Boolean {
    return portraitOverlayVisible && !showDetailSheet
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

internal fun resolvePortraitVideoInteractionUiState(
    targetBvid: String,
    fallbackStat: Stat,
    sharedState: VideoPlaybackUiState.Success?,
    localOverride: PortraitVideoInteractionOverride? = null
): PortraitVideoInteractionUiState {
    val currentSharedState = sharedState?.takeIf { it.info.bvid == targetBvid }
    val base = if (currentSharedState != null) {
        PortraitVideoInteractionUiState(
            isLiked = currentSharedState.isLiked,
            likeStatusKnown = currentSharedState.likeStatusKnown,
            isFavorited = currentSharedState.isFavorited,
            likeCount = currentSharedState.info.stat.like,
            favoriteCount = currentSharedState.info.stat.favorite
        )
    } else {
        PortraitVideoInteractionUiState(
            isLiked = false,
            likeStatusKnown = false,
            isFavorited = false,
            likeCount = fallbackStat.like,
            favoriteCount = fallbackStat.favorite
        )
    }
    if (localOverride == null) {
        return base
    }
    return PortraitVideoInteractionUiState(
        isLiked = localOverride.isLiked ?: base.isLiked,
        likeStatusKnown = localOverride.isLiked != null || base.likeStatusKnown,
        isFavorited = localOverride.isFavorited ?: base.isFavorited,
        likeCount = localOverride.likeCount ?: base.likeCount,
        favoriteCount = localOverride.favoriteCount ?: base.favoriteCount
    )
}

internal fun resolvePortraitTripleActionOverride(
    currentState: PortraitVideoInteractionUiState,
    likeSuccess: Boolean,
    favoriteSuccess: Boolean
): PortraitVideoInteractionOverride {
    val nextLiked = currentState.isLiked || likeSuccess
    val nextFavorited = currentState.isFavorited || favoriteSuccess
    val likeDelta = if (!currentState.isLiked && nextLiked) 1 else 0
    val favoriteDelta = if (!currentState.isFavorited && nextFavorited) 1 else 0
    return PortraitVideoInteractionOverride(
        isLiked = nextLiked.takeIf { currentState.likeStatusKnown || likeSuccess },
        isFavorited = nextFavorited,
        likeCount = (currentState.likeCount + likeDelta).coerceAtLeast(0),
        favoriteCount = (currentState.favoriteCount + favoriteDelta).coerceAtLeast(0)
    )
}

@Composable
private fun PortraitDanmakuOverlay(
    danmakuManager: DanmakuManager,
    videoWidth: Int,
    videoHeight: Int,
    resizeMode: Int,
    modifier: Modifier = Modifier
) {
    key(danmakuManager) {
        AndroidView(
            factory = { ctx ->
                DanmakuRenderView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    // 弹幕层必须 passive，否则会吞掉竖屏全屏的全部触控。
                    configureAsPassiveDanmakuOverlay()
                    danmakuManager.attachView(this)
                }
            },
            update = { view ->
                view.configureAsPassiveDanmakuOverlay()
                val viewportTag = "$videoWidth:$videoHeight:$resizeMode:${view.width}x${view.height}"
                if (view.width > 0 && view.height > 0 && view.tag != viewportTag) {
                    view.tag = viewportTag
                    danmakuManager.attachView(view)
                }
            },
            onRelease = { view -> danmakuManager.detachView(view) },
            modifier = modifier
        )
    }
}

internal fun resolvePortraitPagerRepeatMode(): Int = Player.REPEAT_MODE_OFF

internal fun resolvePortraitDanmakuSurfaceMode(
    currentVideoAspect: Float,
    displayAreaMode: PortraitDanmakuDisplayAreaMode
): PortraitDanmakuSurfaceMode {
    return when (displayAreaMode) {
        PortraitDanmakuDisplayAreaMode.VIDEO_VIEWPORT -> PortraitDanmakuSurfaceMode.VideoViewport
        PortraitDanmakuDisplayAreaMode.SCREEN_TOP -> PortraitDanmakuSurfaceMode.Page
    }
}

internal fun shouldInsetPortraitDanmakuFromStatusBar(
    surfaceMode: PortraitDanmakuSurfaceMode
): Boolean {
    return surfaceMode == PortraitDanmakuSurfaceMode.Page
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
    isLandscape: Boolean = false
): Int {
    if (fillContainer || isLandscape) return 0
    return if (currentVideoAspect > 1f) -48 else 0
}

@Composable
internal fun PortraitVideoViewportContainer(
    currentVideoAspect: Float,
    modifier: Modifier = Modifier,
    viewportModifier: Modifier = Modifier,
    fillContainer: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val viewportSize = with(density) {
            resolvePortraitVideoViewportSize(
                containerWidth = maxWidth.roundToPx(),
                containerHeight = maxHeight.roundToPx(),
                currentVideoAspect = currentVideoAspect,
                fillContainer = fillContainer
            )
        }
        val isLandscape = maxWidth > maxHeight

        Box(
            modifier = viewportModifier
                .size(
                    width = with(density) { viewportSize.width.toDp() },
                    height = with(density) { viewportSize.height.toDp() }
                )
                .align(Alignment.Center)
                .offset(
                    y = resolvePortraitVideoViewportVerticalOffsetDp(
                        currentVideoAspect = currentVideoAspect,
                        fillContainer = fillContainer,
                        isLandscape = isLandscape
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
    val shouldSwap = normalizedRotate == 90 || normalizedRotate == 270
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
