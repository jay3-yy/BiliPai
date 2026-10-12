// 文件路径: feature/bangumi/ui/player/BangumiPlayerComponents.kt
package com.android.purebilibili.feature.bangumi.ui.player

import com.android.purebilibili.feature.video.ui.overlay.NextWatchOverlay
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppHorizontalDivider

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.Surface
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
// 🌈 Material Icons Extended - 亮度图标
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.data.model.response.Page
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.anime4k.Anime4KConfig
import com.android.purebilibili.feature.anime4k.gl.Anime4KGLSurfaceView
import com.android.purebilibili.feature.anime4k.isAnime4KGles3Available
import com.android.purebilibili.feature.anime4k.resolveInitialVideoEnhancementEnabled
import com.android.purebilibili.feature.anime4k.resolveAnime4KOutputDecision
import com.android.purebilibili.feature.video.danmaku.DanmakuManager
import com.android.purebilibili.danmaku.engine.DanmakuRenderView
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.feature.video.ui.components.AnimatedGesturePercentText
import com.android.purebilibili.feature.video.ui.components.SponsorSkipButton
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.resolveVideoViewportLayout
import com.android.purebilibili.feature.video.ui.components.toAnime4KDisplayScaleMode
import com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayContent
import com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelKind
import com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelOverlaySpec
import com.android.purebilibili.feature.video.ui.gesture.rememberGestureLevelOverlayStyle
import com.android.purebilibili.feature.video.ui.overlay.PlaybackDebugInfo
import com.android.purebilibili.feature.video.playback.audio.AudioQualityOption
import com.android.purebilibili.feature.video.ui.section.resolveLongPressPlaybackParameters
import com.android.purebilibili.feature.video.ui.section.VideoOutputRouter
import com.android.purebilibili.feature.video.ui.section.VideoGestureMode
import com.android.purebilibili.feature.video.ui.section.resolveVerticalGestureMode
import com.android.purebilibili.feature.video.ui.section.shouldTriggerFullscreenBySwipe
import com.android.purebilibili.feature.video.ui.section.resolveSystemStreamVolumeFromGesture
import com.android.purebilibili.feature.video.util.captureAndSaveVideoScreenshot
import com.android.purebilibili.data.model.response.SponsorSegment
import com.android.purebilibili.feature.plugin.Anime4KPlugin
import com.android.purebilibili.feature.bangumi.resolveBangumiDanmakuTopInsetDp
import com.android.purebilibili.feature.bangumi.resolveBangumiPlayerTopControlsPaddingTopDp
import com.android.purebilibili.core.store.DEFAULT_LONG_PRESS_SPEED
import com.android.purebilibili.core.store.SettingsManager
import kotlinx.coroutines.launch
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 * 手势模式枚举
 */
enum class BangumiGestureMode { None, Brightness, Volume, Seek, SwipeToFullscreen }

/**
 * 增强版播放器视图
 * 支持：左侧亮度调节、右侧音量调节、进度拖动、弹幕显示、倍速、弹幕设置
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun BangumiPlayerView(
    exoPlayer: ExoPlayer,
    danmakuManager: DanmakuManager,
    danmakuEnabled: Boolean,
    onDanmakuToggle: () -> Unit = {},
    seasonId: Long = 0L,
    epId: Long = 0L,
    title: String = "",
    subtitle: String = "",
    bvid: String = "",
    aid: Long = 0L,
    cid: Long = 0L,
    coverUrl: String = "",
    currentVideoUrl: String = "",
    currentAudioUrl: String = "",
    debugInfo: PlaybackDebugInfo = PlaybackDebugInfo(),
    pages: List<Page> = emptyList(),
    currentPageIndex: Int = 0,
    onPageSelect: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    features: BangumiPlayerFeatures = BangumiPlayerFeatures(),
    isFullscreen: Boolean = false,
    currentQuality: Int = 0,
    acceptQuality: List<Int> = emptyList(),
    acceptDescription: List<String> = emptyList(),
    isLoggedIn: Boolean = false,
    isVip: Boolean = false,
    onQualityChange: (Int) -> Unit = {},
    requestedAudioQuality: Int = -1,
    selectedAudioQuality: Int = -1,
    availableAudioQualities: List<AudioQualityOption> = emptyList(),
    onAudioQualityChange: (Int) -> Unit = {},
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onScreenLockChanged: (Boolean) -> Unit = {},
    sponsorSegment: SponsorSegment? = null,
    showSponsorSkipButton: Boolean = false,
    onSponsorSkip: () -> Unit = {},
    onSponsorDismiss: () -> Unit = {},
    //  新增：倍速控制
    currentSpeed: Float = 1.0f,
    onSpeedChange: (Float) -> Unit = {},
    //  新增：弹幕设置
    danmakuOpacity: Float = 0.85f,
    danmakuFontScale: Float = 1.0f,
    danmakuSpeed: Float = 1.0f,
    danmakuDisplayArea: Float = 0.5f,
    danmakuMergeDuplicates: Boolean = true,
    danmakuDuplicateMergeWindowMs: Int = 500,
    danmakuDuplicateMergeCountThreshold: Int = 2,
    onDanmakuOpacityChange: (Float) -> Unit = {},
    onDanmakuFontScaleChange: (Float) -> Unit = {},
    onDanmakuSpeedChange: (Float) -> Unit = {},
    onDanmakuDisplayAreaChange: (Float) -> Unit = {},
    onDanmakuMergeDuplicatesChange: (Boolean) -> Unit = {},
    onDanmakuDuplicateMergeWindowMsChange: (Int) -> Unit = {},
    onDanmakuDuplicateMergeCountThresholdChange: (Int) -> Unit = {},
    isLiked: Boolean = false,
    coinCount: Int = 0,
    onToggleLike: () -> Unit = {},
    onCoin: () -> Unit = {},
    onReloadVideo: () -> Unit = {},
    onShowMessage: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val playerInteractionSettings by SettingsManager.getPlayerInteractionSettings(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.PlayerInteractionSettings()
        )
    val latestOnToggleFullscreen by rememberUpdatedState(onToggleFullscreen)
    val statusBarsInsetTopDp = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
        .value
    val topControlsPaddingTop = resolveBangumiPlayerTopControlsPaddingTopDp(
        isFullscreen = isFullscreen,
        statusBarsInsetDp = statusBarsInsetTopDp
    ).dp
    val danmakuTopInset = resolveBangumiDanmakuTopInsetDp(
        isFullscreen = isFullscreen,
        statusBarsInsetDp = statusBarsInsetTopDp
    ).dp
    
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    
    // 控制层状态
    var showControls by remember { mutableStateOf(true) }
    var isScreenLocked by rememberSaveable { mutableStateOf(false) }
    val latestOnScreenLockChanged by rememberUpdatedState(onScreenLockChanged)
    LaunchedEffect(isScreenLocked) {
        latestOnScreenLockChanged(isScreenLocked)
    }
    DisposableEffect(Unit) {
        onDispose { latestOnScreenLockChanged(false) }
    }
    var currentAspectRatio by remember { mutableStateOf(VideoAspectRatio.FIT) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val hostLifecycleStarted = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val registeredPlugins by PluginManager.pluginsFlow.collectAsStateWithLifecycle()
    val anime4kPluginInfo = registeredPlugins.firstOrNull { it.plugin.id == Anime4KPlugin.PLUGIN_ID }
    val anime4kPlugin = anime4kPluginInfo?.plugin as? Anime4KPlugin
    val anime4kConfig = if (anime4kPlugin == null) {
        Anime4KConfig()
    } else {
        anime4kPlugin.configState.collectAsStateWithLifecycle().value
    }
    val anime4kGlesAvailable = remember(context) { isAnime4KGles3Available(context) }
    var anime4kPipelineFailed by remember(exoPlayer) { mutableStateOf(false) }
    var anime4kInputSurface by remember(exoPlayer) { mutableStateOf<Surface?>(null) }
    var anime4kDisplayedFirstFrame by remember(currentVideoUrl, exoPlayer) { mutableStateOf(false) }
    var anime4kSurfaceViewRef by remember(exoPlayer) { mutableStateOf<Anime4KGLSurfaceView?>(null) }
    var videoEnhancementSessionOverride by remember(currentVideoUrl, exoPlayer) {
        mutableStateOf<Boolean?>(null)
    }
    val videoEnhancementSessionRequested = videoEnhancementSessionOverride
        ?: resolveInitialVideoEnhancementEnabled(
            pluginEnabled = anime4kPluginInfo?.enabled == true,
            config = anime4kConfig
        )
    val videoEnhancementEnabled = anime4kPluginInfo?.enabled == true &&
        videoEnhancementSessionRequested
    LaunchedEffect(anime4kConfig.algorithm) {
        anime4kPipelineFailed = false
    }
    var videoInputFormat by remember(exoPlayer) { mutableStateOf<Format?>(null) }
    var videoSizeState by remember(exoPlayer) {
        mutableStateOf(exoPlayer.videoSize.let { it.width to it.height })
    }
    val anime4kOutputDecision = remember(
        videoEnhancementEnabled,
        anime4kGlesAvailable,
        anime4kPipelineFailed,
        videoInputFormat,
        lifecycleState,
        features.isInPipMode,
        features.isAudioOnly,
    ) {
        resolveAnime4KOutputDecision(
            pluginEnabled = videoEnhancementEnabled,
            glAvailable = anime4kGlesAvailable && !anime4kPipelineFailed,
            colorTransfer = videoInputFormat?.colorInfo?.colorTransfer ?: 0,
            sampleMimeType = videoInputFormat?.sampleMimeType,
            isInPipMode = features.isInPipMode,
            isAudioOnly = features.isAudioOnly,
            hostLifecycleStarted = hostLifecycleStarted
        )
    }
    val requiresHdrSurface = com.android.purebilibili.feature.video.ui.section.requiresHdrSurfaceOutput(
        currentQualityId = currentQuality, colorTransfer = videoInputFormat?.colorInfo?.colorTransfer ?: 0,
    )
    val useTextureSurface = !requiresHdrSurface && (features.isFlippedHorizontal || features.isFlippedVertical)
    val shouldUseAnime4kPipeline = anime4kOutputDecision.shouldUsePipeline
    val shouldRenderAnime4kPipeline = shouldUseAnime4kPipeline &&
        videoSizeState.first > 0 &&
        videoSizeState.second > 0
    val anime4kBypassReason = anime4kOutputDecision.bypassReason
    val anime4kSurfaceReady = shouldRenderAnime4kPipeline && anime4kInputSurface != null
    val anime4kFrameVisible = anime4kSurfaceReady && anime4kDisplayedFirstFrame
    val videoOutputRouter = remember(exoPlayer) { VideoOutputRouter(exoPlayer) }

    val immersiveStatusBarBackdropEnabled by SettingsManager.getHideVideoPageStatusBar(context)
        .collectAsStateWithLifecycle(initialValue = SettingsManager.getHideVideoPageStatusBarSync(context))
    var firstFrameReady by remember(exoPlayer, cid) { mutableStateOf(false) }
    com.android.purebilibili.feature.video.ambient.BindPlayerAmbient(
        player = exoPlayer,
        playerView = playerViewRef,
        identity = cid,
        quality = currentQuality,
        width = videoSizeState.first,
        height = videoSizeState.second,
        foreground = hostLifecycleStarted,
        excluded = isFullscreen || features.isInPipMode || features.isAudioOnly,
        hdr = com.android.purebilibili.feature.video.ui.section.requiresHdrSurfaceOutput(
            currentQualityId = currentQuality,
            colorTransfer = videoInputFormat?.colorInfo?.colorTransfer ?: 0,
        ),
        anime4k = shouldUseAnime4kPipeline,
        transitioning = false,
        firstFrameReady = firstFrameReady,
        statusBar = immersiveStatusBarBackdropEnabled && !isFullscreen,
        diagnosticLogging = false,
    )

    DisposableEffect(exoPlayer, cid) {
        val analyticsListener = object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                videoInputFormat = format
            }
        }
        val playerListener = object : Player.Listener {
            override fun onRenderedFirstFrame() { firstFrameReady = true }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoSizeState = videoSize.width to videoSize.height
            }
        }
        exoPlayer.addAnalyticsListener(analyticsListener)
        exoPlayer.addListener(playerListener)
        onDispose {
            exoPlayer.removeAnalyticsListener(analyticsListener)
            exoPlayer.removeListener(playerListener)
        }
    }

    DisposableEffect(videoOutputRouter) {
        onDispose { videoOutputRouter.release() }
    }

    LaunchedEffect(hostLifecycleStarted, shouldRenderAnime4kPipeline, anime4kSurfaceViewRef) {
        val surfaceView = anime4kSurfaceViewRef ?: return@LaunchedEffect
        if (shouldRenderAnime4kPipeline && hostLifecycleStarted) {
            surfaceView.onResume()
        } else {
            surfaceView.onPause()
        }
    }

    LaunchedEffect(
        playerViewRef,
        anime4kInputSurface,
        shouldRenderAnime4kPipeline,
        hostLifecycleStarted
    ) {
        videoOutputRouter.update(
            playerView = playerViewRef,
            inputSurface = anime4kInputSurface,
            shouldBindDirectPlayerView = hostLifecycleStarted,
            shouldUseAnime4K = shouldRenderAnime4kPipeline
        )
    }
    
    // 手势状态
    var gestureMode by remember { mutableStateOf(BangumiGestureMode.None) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var startVolumeStep by remember { mutableIntStateOf(0) }
    var totalVolumeDragDistanceY by remember { mutableFloatStateOf(0f) }
    var seekPreviewPosition by remember { mutableLongStateOf(0L) }
    val longPressSpeed by SettingsManager.getLongPressSpeed(context)
        .collectAsState(initial = DEFAULT_LONG_PRESS_SPEED)
    var longPressOriginalPlaybackParameters by remember(exoPlayer) {
        mutableStateOf(exoPlayer.playbackParameters)
    }
    
    // 亮度状态
    var currentBrightness by remember {
        mutableFloatStateOf(
            try {
                android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (e: Exception) { 0.5f }
        )
    }
    
    // 播放器状态
    var isPlaying by remember { mutableStateOf(exoPlayer.isPlaying) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(1L) }
    
    // 监听播放器状态
    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            duration = exoPlayer.duration.coerceAtLeast(1L)
            currentPosition = exoPlayer.currentPosition
            if (gestureMode != BangumiGestureMode.Seek) {
                currentProgress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            }
            kotlinx.coroutines.delay(200)
        }
    }
    
    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(isScreenLocked, longPressSpeed, requestedAudioQuality, exoPlayer) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        if (isScreenLocked) return@detectDragGesturesAfterLongPress
                        longPressOriginalPlaybackParameters = exoPlayer.playbackParameters
                        exoPlayer.playbackParameters = resolveLongPressPlaybackParameters(
                            requestedSpeed = longPressSpeed,
                            currentAudioQuality = requestedAudioQuality
                        )
                    },
                    onDragEnd = {
                        exoPlayer.playbackParameters = longPressOriginalPlaybackParameters
                    },
                    onDragCancel = {
                        exoPlayer.playbackParameters = longPressOriginalPlaybackParameters
                    },
                    onDrag = { change, _ -> change.consume() }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = {
                        if (isScreenLocked) return@detectTapGestures
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }
                )
            }
            .then(
                Modifier.pointerInput(isFullscreen, isScreenLocked, playerInteractionSettings, exoPlayer) {
                    val screenWidth = size.width.toFloat()
                    val screenHeight = size.height.toFloat()
                    val minDragThreshold = 20.dp.toPx()
                    val boundaryPadding = 16.dp.toPx()
                    var dragStartX = 0f
                    var totalDragDistanceX = 0f
                    var totalDragDistanceY = 0f
                    
                    detectDragGestures(
                        onDragStart = { position ->
                            showControls = true
                            dragStartX = position.x
                            totalDragDistanceX = 0f
                            totalDragDistanceY = 0f
                            dragDelta = 0f
                            totalVolumeDragDistanceY = 0f
                            seekPreviewPosition = currentPosition
                            gestureMode = BangumiGestureMode.None
                        },
                        onDragEnd = {
                            if (gestureMode == BangumiGestureMode.Seek && kotlin.math.abs(dragDelta) > 20f) {
                                exoPlayer.seekTo(seekPreviewPosition)
                            }
                            if (!isScreenLocked && gestureMode == BangumiGestureMode.SwipeToFullscreen &&
                                shouldTriggerFullscreenBySwipe(
                                    isFullscreen = isFullscreen,
                                    reverseGesture = playerInteractionSettings.fullscreenGestureReverse,
                                    totalDragDistanceY = totalDragDistanceY,
                                    thresholdPx = 50.dp.toPx()
                                )
                            ) {
                                latestOnToggleFullscreen()
                            }
                            gestureMode = BangumiGestureMode.None
                        },
                        onDragCancel = { gestureMode = BangumiGestureMode.None },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (isScreenLocked) return@detectDragGestures
                            totalDragDistanceX += dragAmount.x
                            totalDragDistanceY += dragAmount.y
                            
                            if (gestureMode == BangumiGestureMode.None) {
                                if (kotlin.math.hypot(totalDragDistanceX, totalDragDistanceY) < minDragThreshold) {
                                    return@detectDragGestures
                                }
                                gestureMode = if (isFullscreen && kotlin.math.abs(totalDragDistanceX) > kotlin.math.abs(totalDragDistanceY)) {
                                    BangumiGestureMode.Seek
                                } else if (kotlin.math.abs(totalDragDistanceY) > kotlin.math.abs(totalDragDistanceX)) {
                                    when (resolveVerticalGestureMode(
                                        isFullscreen = isFullscreen,
                                        isSwipeUp = totalDragDistanceY < -minDragThreshold,
                                        startX = dragStartX,
                                        leftZoneEnd = (screenWidth / 3f - boundaryPadding).coerceAtLeast(0f),
                                        rightZoneStart = (screenWidth * 2f / 3f + boundaryPadding).coerceAtMost(screenWidth),
                                        portraitSwipeToFullscreenEnabled = playerInteractionSettings.portraitSwipeToFullscreenEnabled,
                                        centerSwipeToFullscreenEnabled = playerInteractionSettings.centerSwipeToFullscreenEnabled,
                                        slideVolumeBrightnessEnabled = playerInteractionSettings.slideVolumeBrightnessEnabled
                                    )) {
                                        VideoGestureMode.Brightness -> {
                                            gestureValue = currentBrightness
                                            BangumiGestureMode.Brightness
                                        }
                                        VideoGestureMode.Volume -> {
                                            startVolumeStep = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            gestureValue = if (maxVolume > 0) startVolumeStep.toFloat() / maxVolume.toFloat() else 0f
                                            BangumiGestureMode.Volume
                                        }
                                        VideoGestureMode.SwipeToFullscreen -> BangumiGestureMode.SwipeToFullscreen
                                        else -> BangumiGestureMode.None
                                    }
                                } else {
                                    BangumiGestureMode.None
                                }
                            }
                            
                            when (gestureMode) {
                                BangumiGestureMode.Brightness -> {
                                    gestureValue = (gestureValue - dragAmount.y / screenHeight).coerceIn(0f, 1f)
                                    currentBrightness = gestureValue
                                    (context as? Activity)?.window?.let { window ->
                                        val params = window.attributes
                                        params.screenBrightness = gestureValue
                                        window.attributes = params
                                    }
                                }
                                BangumiGestureMode.Volume -> {
                                    totalVolumeDragDistanceY += dragAmount.y
                                    val newVolumeStep = resolveSystemStreamVolumeFromGesture(
                                        startVolumeStep = startVolumeStep,
                                        maxVolumeStep = maxVolume,
                                        totalDragDistanceY = totalVolumeDragDistanceY,
                                        screenHeightPx = screenHeight,
                                        gestureSensitivity = 1.0f
                                    )
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        newVolumeStep,
                                        0
                                    )
                                    gestureValue = if (maxVolume > 0) {
                                        newVolumeStep.toFloat() / maxVolume.toFloat()
                                    } else {
                                        0f
                                    }
                                }
                                BangumiGestureMode.Seek -> {
                                    dragDelta += dragAmount.x
                                    val seekDelta = (dragDelta / screenWidth * duration).toLong()
                                    seekPreviewPosition = (currentPosition + seekDelta).coerceIn(0L, duration)
                                    currentProgress = (seekPreviewPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                }
                                else -> {}
                            }
                        }
                    )
                }
            )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val playerFrameViewport = remember(
                maxWidth,
                maxHeight,
                currentAspectRatio,
                density
            ) {
                with(density) {
                    resolveVideoViewportLayout(
                        containerWidth = maxWidth.roundToPx(),
                        containerHeight = maxHeight.roundToPx(),
                        aspectRatio = currentAspectRatio
                    )
                }
            }
            // 镜像直出使用 TextureView；HDR 保留 SurfaceView 输出。
            key(useTextureSurface) {
                AndroidView(
                    factory = { ctx ->
                        android.util.Log.w("BangumiPlayer", "🎬 PlayerView FACTORY: creating new view, player=${exoPlayer.hashCode()}, isFullscreen=$isFullscreen")
                        val baseView = if (useTextureSurface) {
                            android.view.LayoutInflater.from(ctx).inflate(
                                com.android.purebilibili.R.layout.view_player_texture, null, false
                            ) as PlayerView
                        } else PlayerView(ctx)
                        baseView.apply {
                            playerViewRef = this
                            // 普通直出必须显式绑定 player，否则未启用 Anime4K 时无视频输出
                            //（只有音频）。Anime4K 启用后由 VideoOutputRouter 主动解除本绑定并接管 surface。
                            player = exoPlayer
                            useController = false
                            keepScreenOn = true
                            resizeMode = currentAspectRatio.playerResizeMode
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { view ->
                        playerViewRef = view
                        view.resizeMode = currentAspectRatio.playerResizeMode
                        view.visibility = if (anime4kFrameVisible) View.INVISIBLE else View.VISIBLE
                        // MediaSource/Player 变化后确保直出绑定仍与播放器同步；
                        // Anime4K 接管期间不抢回绑定，避免与路由争抢 Surface。
                        if (view.player !== exoPlayer && !shouldRenderAnime4kPipeline) {
                            view.player = exoPlayer
                        }
                    },
                    modifier = with(density) {
                        Modifier.requiredSize(
                            width = playerFrameViewport.width.toDp(),
                            height = playerFrameViewport.height.toDp()
                        ).graphicsLayer {
                            scaleX = if (useTextureSurface && features.isFlippedHorizontal) -1f else 1f
                            scaleY = if (useTextureSurface && features.isFlippedVertical) -1f else 1f
                        }
                    }
                )
            }

            if (shouldRenderAnime4kPipeline) {
                AndroidView(
                    factory = { ctx ->
                        Anime4KGLSurfaceView(ctx, initialConfig = anime4kConfig).apply {
                            anime4kSurfaceViewRef = this
                            onInputSurfaceChanged = { surface ->
                                anime4kInputSurface = surface
                                if (surface == null) anime4kDisplayedFirstFrame = false
                            }
                            onFirstFrameRendered = {
                                anime4kDisplayedFirstFrame = true
                            }
                            onPipelineError = { error ->
                                Logger.e("BangumiPlayer", "Anime4K 管线不可用，已回退原始视频输出", error)
                                anime4kPipelineFailed = true
                                anime4kInputSurface = null
                            }
                            updateConfig(anime4kConfig)
                            updateInputSize(videoSizeState.first, videoSizeState.second)
                            updateFlip(features.isFlippedHorizontal, features.isFlippedVertical)
                            updateDisplayScaleMode(currentAspectRatio.toAnime4KDisplayScaleMode())
                            visibility = View.VISIBLE
                        }
                    },
                    update = { surfaceView ->
                        anime4kSurfaceViewRef = surfaceView
                        surfaceView.onInputSurfaceChanged = { surface ->
                            anime4kInputSurface = surface
                            if (surface == null) anime4kDisplayedFirstFrame = false
                        }
                        surfaceView.onFirstFrameRendered = {
                            anime4kDisplayedFirstFrame = true
                        }
                        surfaceView.onPipelineError = { error ->
                            Logger.e("BangumiPlayer", "Anime4K 管线不可用，已回退原始视频输出", error)
                            anime4kPipelineFailed = true
                            anime4kInputSurface = null
                        }
                        surfaceView.updateConfig(anime4kConfig)
                        surfaceView.updateInputSize(videoSizeState.first, videoSizeState.second)
                        surfaceView.updateFlip(features.isFlippedHorizontal, features.isFlippedVertical)
                        surfaceView.updateDisplayScaleMode(currentAspectRatio.toAnime4KDisplayScaleMode())
                        surfaceView.visibility = View.VISIBLE
                    },
                    modifier = with(density) {
                        Modifier.requiredSize(
                            width = playerFrameViewport.width.toDp(),
                            height = playerFrameViewport.height.toDp()
                        )
                    }
                )
            }
        }
        
        // 弹幕层 - 使用 DanmakuRenderEngine
        if (features.isAudioOnly) {
            coil3.compose.AsyncImage(
                model = com.android.purebilibili.core.util.FormatUtils.fixImageUrl(coverUrl),
                contentDescription = "番剧封面",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
        if (danmakuEnabled && !features.isAudioOnly && !features.isInPipMode) {
            key(danmakuManager) {
                AndroidView(
                    factory = { ctx ->
                        DanmakuRenderView(ctx).apply {
                            danmakuManager.isFullscreenSurface = isFullscreen
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            android.util.Log.w("BangumiPlayer", "🎯 DanmakuView factory: creating new view")
                            danmakuManager.attachView(this)
                        }
                    },
                    update = { view ->
                        danmakuManager.isFullscreenSurface = isFullscreen
                        if (view.width > 0 && view.height > 0) {
                            val sizeTag = "${view.width}x${view.height}"
                            if (view.tag != sizeTag) {
                                view.tag = sizeTag
                                android.util.Log.d("BangumiPlayer", " DanmakuView update: size=${view.width}x${view.height}")
                                danmakuManager.attachView(view)
                            }
                        }
                    },
                    onRelease = { view -> danmakuManager.detachView(view) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = danmakuTopInset)
                        .clipToBounds()
                )
            }
        }
        
        // 手势指示器（横屏：全部，竖屏：仅亮度和音量）
        val showGestureIndicator = gestureMode != BangumiGestureMode.None && 
            (isFullscreen || gestureMode == BangumiGestureMode.Brightness || gestureMode == BangumiGestureMode.Volume)
        if (showGestureIndicator) {
            BangumiGestureIndicator(
                mode = gestureMode,
                value = when (gestureMode) {
                    BangumiGestureMode.Brightness -> currentBrightness
                    BangumiGestureMode.Volume -> gestureValue
                    BangumiGestureMode.Seek -> currentProgress
                    else -> 0f
                },
                seekTime = if (gestureMode == BangumiGestureMode.Seek) seekPreviewPosition else null,
                duration = duration,
                modifier = if (gestureMode == BangumiGestureMode.Seek) {
                    Modifier.align(Alignment.Center)
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }
        
        NextWatchOverlay(
            player = exoPlayer,
            mediaKey = "bangumi:$epId:$cid",
            eligible = !features.isInPipMode && !showControls && !isScreenLocked && !showGestureIndicator &&
                exoPlayer.repeatMode != androidx.media3.common.Player.REPEAT_MODE_ONE,
            resolve = {
                pages.getOrNull(currentPageIndex + 1)?.let { next ->
                    com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion(
                        bvid, cid, bvid, next.cid, next.part, coverUrl, "下一集", pageIndex = currentPageIndex + 1
                    )
                }
            },
            play = { target ->
                val index = target.pageIndex
                if (index != null && target.sourceCid == cid && target.sourceBvid == bvid && index == currentPageIndex + 1 &&
                    pages.getOrNull(index)?.cid == target.cid) {
                    onPageSelect(index)
                    true
                } else false
            },
        )

        if (!features.isInPipMode && !features.isAudioOnly) {
            BangumiSubtitleOverlay(exoPlayer, features.subtitle, showControls, isFullscreen, playerViewRef?.height ?: 0)
        }
        BangumiPlayerOverlayHost(
            player = exoPlayer,
            features = features.copy(
                isFlippedHorizontal = features.isFlippedHorizontal && !requiresHdrSurface,
                isFlippedVertical = features.isFlippedVertical && !requiresHdrSurface,
                onFlipHorizontal = {
                    if (requiresHdrSurface) onShowMessage("HDR 画面暂不支持镜像，请先切换至普通画质")
                    else features.onFlipHorizontal()
                },
                onFlipVertical = {
                    if (requiresHdrSurface) onShowMessage("HDR 画面暂不支持翻转，请先切换至普通画质")
                    else features.onFlipVertical()
                },
            ),
            seasonId = seasonId,
            epId = epId,
            title = title,
            subtitle = subtitle,
            bvid = bvid,
            aid = aid,
            cid = cid,
            coverUrl = coverUrl,
            currentVideoUrl = currentVideoUrl,
            currentAudioUrl = currentAudioUrl,
            debugInfo = debugInfo,
            isVisible = showControls && gestureMode == BangumiGestureMode.None && !features.isInPipMode,
            onToggleVisible = { showControls = !showControls },
            isFullscreen = isFullscreen,
            isScreenLocked = isScreenLocked,
            onLockToggle = { isScreenLocked = !isScreenLocked },
            currentQuality = currentQuality,
            acceptQuality = acceptQuality,
            acceptDescription = acceptDescription,
            isLoggedIn = isLoggedIn,
            isVip = isVip,
            onQualityChange = onQualityChange,
            requestedAudioQuality = requestedAudioQuality,
            selectedAudioQuality = selectedAudioQuality,
            availableAudioQualities = availableAudioQualities,
            onAudioQualityChange = onAudioQualityChange,
            onPlaybackSpeedChange = onSpeedChange,
            onBack = onBack,
            onToggleFullscreen = onToggleFullscreen,
            danmakuEnabled = danmakuEnabled,
            onDanmakuToggle = onDanmakuToggle,
            danmakuOpacity = danmakuOpacity,
            danmakuFontScale = danmakuFontScale,
            danmakuSpeed = danmakuSpeed,
            danmakuDisplayArea = danmakuDisplayArea,
            danmakuMergeDuplicates = danmakuMergeDuplicates,
            danmakuDuplicateMergeWindowMs = danmakuDuplicateMergeWindowMs,
            danmakuDuplicateMergeCountThreshold = danmakuDuplicateMergeCountThreshold,
            onDanmakuOpacityChange = onDanmakuOpacityChange,
            onDanmakuFontScaleChange = onDanmakuFontScaleChange,
            onDanmakuSpeedChange = onDanmakuSpeedChange,
            onDanmakuDisplayAreaChange = onDanmakuDisplayAreaChange,
            onDanmakuMergeDuplicatesChange = onDanmakuMergeDuplicatesChange,
            onDanmakuDuplicateMergeWindowMsChange = onDanmakuDuplicateMergeWindowMsChange,
            onDanmakuDuplicateMergeCountThresholdChange = onDanmakuDuplicateMergeCountThresholdChange,
            currentAspectRatio = currentAspectRatio,
            onAspectRatioChange = { currentAspectRatio = it },
            pages = pages,
            currentPageIndex = currentPageIndex,
            onPageSelect = onPageSelect,
            isLiked = isLiked,
            coinCount = coinCount,
            onToggleLike = onToggleLike,
            onCoin = onCoin,
            onCaptureScreenshot = {
                val playerView = playerViewRef
                if (playerView == null) {
                    onShowMessage("截图失败，请稍后重试")
                    return@BangumiPlayerOverlayHost
                }
                scope.launch {
                    val success = captureAndSaveVideoScreenshot(
                        context = context,
                        playerView = playerView,
                        videoWidth = exoPlayer.videoSize.width,
                        videoHeight = exoPlayer.videoSize.height,
                        videoTitle = subtitle.ifBlank { title.ifBlank { "bangumi" } }
                    )
                    onShowMessage(if (success) "截图已保存到相册" else "截图失败，请稍后重试")
                }
            },
            onReloadVideo = onReloadVideo,
            anime4kEnabled = videoEnhancementEnabled,
            anime4kAvailable = anime4kGlesAvailable,
            anime4kBypassReason = anime4kBypassReason,
            videoEnhancementAlgorithm = anime4kConfig.algorithm,
            anime4kPreset = anime4kConfig.preset,
            fsrSharpness = anime4kConfig.fsrSharpness,
            onAnime4kToggle = { enabled ->
                anime4kPipelineFailed = false
                videoEnhancementSessionOverride = enabled
                scope.launch {
                    if (enabled && anime4kPluginInfo?.enabled != true) {
                        PluginManager.setEnabled(Anime4KPlugin.PLUGIN_ID, true)
                    }
                    Anime4KPlugin.getInstance()?.rememberCurrentVideoEnabled(enabled)
                }
            },
            onVideoEnhancementAlgorithmChange = { algorithm ->
                anime4kPlugin?.setAlgorithm(algorithm)
            },
            onAnime4kPresetChange = { preset ->
                anime4kPlugin?.setPreset(preset)
            },
            onFsrSharpnessChange = { sharpness ->
                anime4kPlugin?.setFsrSharpness(sharpness)
            },
            onShowMessage = onShowMessage
        )
        
        // 空降助手跳过按钮 (位置调整到进度条上方)
        SponsorSkipButton(
            segment = sponsorSegment,
            visible = showSponsorSkipButton,
            onSkip = onSponsorSkip,
            onDismiss = onSponsorDismiss,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 60.dp, end = 16.dp)  //  向上偏移避免与进度条重叠
        )
    }
}

/**
 * 手势指示器
 */
@Composable
fun BangumiGestureIndicator(
    mode: BangumiGestureMode,
    value: Float,
    seekTime: Long?,
    duration: Long,
    modifier: Modifier = Modifier
) {
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val overlayStyle = rememberGestureLevelOverlayStyle(playerChromeProfile.tabPresentation)
    when (mode) {
        BangumiGestureMode.Brightness, BangumiGestureMode.Volume -> {
            val mappedMode = if (mode == BangumiGestureMode.Brightness) {
                VideoGestureMode.Brightness
            } else {
                VideoGestureMode.Volume
            }
            val kind = resolveGestureLevelKind(mappedMode) ?: return
            val alignment = resolveGestureLevelOverlaySpec(
                style = overlayStyle,
                kind = kind,
                percent = value,
                brightnessColor = androidx.compose.ui.res.colorResource(com.android.purebilibili.R.color.player_brightness_accent),
            ).alignment
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = alignment
            ) {
                GestureLevelOverlayContent(
                    mode = mappedMode,
                    percent = value,
                    style = overlayStyle,
                    modifier = if (playerChromeProfile.effects.usesTonalContainerTreatment) {
                        Modifier.padding(horizontal = 22.dp)
                    } else {
                        Modifier
                    }
                )
            }
        }
        BangumiGestureMode.Seek -> {
            AppSurface(
                modifier = modifier,
                shape = AppShapes.container(ContainerLevel.Card),
                color = Color.Black.copy(alpha = 0.74f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.58f)),
                shadowElevation = 6.dp,
                tonalElevation = 0.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    AppText(
                        "${FormatUtils.formatDuration(((seekTime ?: 0) / 1000).toInt())} / ${FormatUtils.formatDuration((duration / 1000).toInt())}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        else -> Unit
    }
}

/**
 * 可拖动的迷你进度条（竖屏模式） - 紧凑样式
 */
@Composable
fun BangumiMiniProgressBar(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var bufferedProgress by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    
    // 定期更新进度
    LaunchedEffect(player) {
        while (true) {
            if (player.duration > 0 && !isDragging) {
                progress = player.currentPosition.toFloat() / player.duration
                bufferedProgress = player.bufferedPosition.toFloat() / player.duration
            }
            kotlinx.coroutines.delay(200)
        }
    }
    
    Box(
        modifier = modifier
            .height(12.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    val seekPosition = (fraction * player.duration).toLong()
                    player.seekTo(seekPosition)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        val seekPosition = (dragProgress * player.duration).toLong()
                        player.seekTo(seekPosition)
                        isDragging = false
                    },
                    onDragCancel = { isDragging = false },
                    onDrag = { _, dragAmount ->
                        dragProgress = (dragProgress + dragAmount.x / size.width).coerceIn(0f, 1f)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // 进度条容器 - 实际显示的细条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.DarkGray.copy(alpha = 0.5f))
        ) {
            // 缓冲进度
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(bufferedProgress.coerceIn(0f, 1f))
                    .background(Color.White.copy(alpha = 0.3f))
            )
            // 播放进度
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((if (isDragging) dragProgress else progress).coerceIn(0f, 1f))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/**
 * 番剧画质选择菜单
 */
@Composable
fun BangumiQualityMenu(
    qualities: List<String>,
    qualityIds: List<Int>,
    currentQualityId: Int,
    onQualitySelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    fun getQualityTag(qn: Int): String? {
        return when (qn) {
            127, 126, 125, 120, 116, 112 -> "大会员"
            else -> null
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        AppSurface(
            modifier = Modifier
                .widthIn(min = 200.dp, max = 280.dp)
                .clip(AppShapes.container(ContainerLevel.Card))
                .clickable(enabled = false) {},
            color = Color(0xFF2B2B2B),
            shape = AppShapes.container(ContainerLevel.Card),
            tonalElevation = 8.dp
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                AppText(
                    text = "画质选择",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                AppHorizontalDivider(color = Color.White.copy(0.1f))
                
                qualities.forEachIndexed { index, quality ->
                    val qn = qualityIds.getOrNull(index) ?: 0
                    val isSelected = qn == currentQualityId
                    val tag = getQualityTag(qn)
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQualitySelected(qn) }
                            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppText(
                            text = quality,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        
                        if (tag != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AppSurface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = AppShapes.container(ContainerLevel.Tag)
                            ) {
                                AppText(
                                    text = tag,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        if (isSelected) {
                            AppIcon(
                                Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 *  [优化] 细进度条组件 - 参考普通视频播放器的 VideoProgressBar 样式
 * 3dp 高度的细进度条，带圆角和可拖动的圆点滑块
 */
@Composable
fun BangumiSlimProgressBar(
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var tempProgress by remember { mutableFloatStateOf(progress) }
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // 同步外部进度
    LaunchedEffect(progress) {
        if (!isDragging) {
            tempProgress = progress
        }
    }
    
    val displayProgress = if (isDragging) tempProgress else progress
    
    Box(
        modifier = modifier
            .height(48.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onProgressChange(newProgress)
                    onSeekFinished()
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        tempProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        onProgressChange(tempProgress)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        tempProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        onProgressChange(tempProgress)
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeekFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        tempProgress = progress
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // 背景轨道
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.3f), AppShapes.container(ContainerLevel.Micro))
        )
        
        // 当前进度
        Box(
            modifier = Modifier
                .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(primaryColor, AppShapes.container(ContainerLevel.Micro))
        )
        
        // 滑块（圆点）- 拖动时放大
        Box(
            modifier = Modifier
                .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(if (isDragging) 16.dp else 12.dp)
                    .offset { IntOffset(x = (if (isDragging) 8.dp else 6.dp).roundToPx(), y = 0) }
                    .background(primaryColor, androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}
