package com.android.purebilibili.feature.download

import com.android.purebilibili.core.player.withAppVideoDecoderDiagnostics
import com.android.purebilibili.core.player.withAppVideoDecoderPreference

import com.android.purebilibili.feature.video.player.basicPlayerKeyboardShortcuts
import com.android.purebilibili.core.ui.AppContentColors

import com.android.purebilibili.feature.video.ui.overlay.NextWatchOverlay
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.util.applyPlayerRequestedOrientation
import com.android.purebilibili.core.util.layoutHinges
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppCommentIcon
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.rememberAppPlayIcon
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppTextButton
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.theme.resolveAdaptivePrimaryAccentColors
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.store.FullscreenMode
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.resolveDanmakuSettingsScope
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.danmaku.configureAsPassiveDanmakuOverlay
import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager
import com.android.purebilibili.feature.video.danmaku.rememberPlayerDanmakuTargetFrameRateHz
import com.android.purebilibili.feature.video.danmaku.shouldUseIndependentDanmakuSurface
import com.android.purebilibili.feature.video.ui.components.TwoFingerSpeedFeedbackOverlay
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.cancelPlayerViewViewportRefresh
import com.android.purebilibili.feature.video.ui.components.resolveVideoViewportLayout
import com.android.purebilibili.feature.video.ui.components.schedulePlayerViewViewportRefresh
import com.android.purebilibili.feature.video.ui.components.toVideoAspectRatio
import com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayContent
import com.android.purebilibili.feature.video.ui.gesture.SwipeToFullscreenHint
import com.android.purebilibili.feature.video.ui.gesture.TwoFingerSpeedGestureMode
import com.android.purebilibili.feature.video.ui.gesture.rememberGestureLevelOverlayStyle
import com.android.purebilibili.core.util.AppFoldPosture
import com.android.purebilibili.core.util.tabletopUpperViewportLayout
import com.android.purebilibili.feature.video.ui.gesture.detectEdgeSafeDragGestures
import com.android.purebilibili.feature.video.ui.gesture.detectPlayerTapGestures
import com.android.purebilibili.feature.video.ui.gesture.resolveLockedTwoFingerSpeedAxis
import com.android.purebilibili.feature.video.ui.gesture.resolveTwoFingerGesturePlaybackSpeed
import com.android.purebilibili.feature.video.ui.gesture.resolveTwoFingerSpeedGestureMode
import com.android.purebilibili.feature.video.ui.overlay.FullscreenDoubleTapAction
import com.android.purebilibili.feature.video.ui.overlay.nextFullscreenSeekFeedbackEvent
import com.android.purebilibili.feature.video.ui.overlay.resolveFullscreenDoubleTapAction
import com.android.purebilibili.feature.video.ui.section.FULLSCREEN_PLAYER_BOTTOM_GESTURE_EXCLUSION_DP
import com.android.purebilibili.feature.video.ui.section.FULLSCREEN_PLAYER_EDGE_GESTURE_EXCLUSION_DP
import com.android.purebilibili.feature.video.ui.section.INLINE_PLAYER_BOTTOM_GESTURE_EXCLUSION_DP
import com.android.purebilibili.feature.video.ui.section.INLINE_PLAYER_TOP_GESTURE_EXCLUSION_DP
import com.android.purebilibili.feature.video.ui.section.PlayerLongPressSpeedHint
import com.android.purebilibili.feature.video.ui.section.PlayerSeekFeedback
import com.android.purebilibili.feature.video.ui.section.PlayerSpeedLockZones
import com.android.purebilibili.feature.video.ui.section.SwipeToFullscreenDragThresholdDp
import com.android.purebilibili.feature.video.ui.section.resolveLongPressSpeedLockSensitivityPolicy
import com.android.purebilibili.feature.video.ui.section.resolveSwipeToFullscreenHintFraction
import com.android.purebilibili.feature.video.ui.section.resolveVerticalGestureMode
import com.android.purebilibili.feature.video.ui.section.resolveVideoPlayerGestureVerticalExclusions
import com.android.purebilibili.feature.video.ui.section.shouldAutoExitFullscreenOnPlaybackEnded
import com.android.purebilibili.feature.video.ui.section.shouldIgnoreVideoPlayerDragStart
import com.android.purebilibili.feature.video.ui.section.shouldIgnoreVideoPlayerHorizontalEdgeDragStart
import com.android.purebilibili.feature.video.ui.section.shouldLockLongPressSpeedInTargetZone
import com.android.purebilibili.feature.video.ui.section.shouldTriggerFullscreenBySwipe
import com.android.purebilibili.feature.video.ui.section.shouldUnlockLockedLongPressSpeedFromRightDownDrag
import com.android.purebilibili.feature.video.viewmodel.resolvePlaybackCompletionRepeatMode
import kotlinx.coroutines.flow.first
import com.android.purebilibili.feature.video.ui.section.VideoGestureMode
import com.android.purebilibili.feature.video.ui.section.resolveHorizontalSeekDeltaMs
import com.android.purebilibili.feature.video.ui.section.resolveRelativeSeekTargetPosition
import com.android.purebilibili.feature.video.ui.section.resolveVideoGestureMotionSpec
import com.android.purebilibili.feature.video.ui.section.resolveVideoPlayerUiLayoutPolicy
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction
import com.android.purebilibili.danmaku.engine.DanmakuRenderView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.feature.video.ui.section.rebindPlayerSurfaceIfNeeded
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

// Persists playback progress independently of the composable lifecycle.
private val playbackPersistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 🔧 [重构] 离线视频播放器
 * 支持完整手势功能：亮度、音量、进度调节、双击快进/后退、长按倍速
 */
@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun OfflineVideoPlayerScreen(
    taskId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val displayContext = LocalAppWindowAdaptiveInfo.current.displayContext
    val appWindowAdaptiveInfo = LocalAppWindowAdaptiveInfo.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val miniPlayerManager = remember(context) { MiniPlayerManager.getInstance(context) }
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val backIcon = rememberAppBackIcon()
    val commentIcon = rememberAppCommentIcon()
    val playIcon = rememberAppPlayIcon()
    val gestureLevelOverlayStyle =
        rememberGestureLevelOverlayStyle(playerChromeProfile.tabPresentation)

    val tasks by DownloadManager.tasks.collectAsStateWithLifecycle()
    var currentTaskId by remember(taskId) { mutableStateOf(taskId) }
    val danmakuManager = rememberDanmakuManager("offline:$currentTaskId")
    val configuration = LocalConfiguration.current
    val danmakuSettingsScope = remember(configuration.orientation) {
        resolveDanmakuSettingsScope(
            isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        )
    }
    val danmakuSettings by SettingsManager
        .getDanmakuSettings(context, danmakuSettingsScope)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings())
    val playerInteractionSettings by SettingsManager
        .getPlayerInteractionSettings(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.PlayerInteractionSettings()
        )
    val longPressSpeed = playerInteractionSettings.longPressSpeed
    val twoFingerSpeedMode = resolveTwoFingerSpeedGestureMode(
        verticalEnabled = playerInteractionSettings.twoFingerVerticalSpeedEnabled,
        horizontalEnabled = playerInteractionSettings.twoFingerHorizontalSpeedEnabled,
    )
    val playbackSpeedOptions by remember(context) { SettingsManager.getPlaybackSpeedOptions(context) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val fullscreenMode: FullscreenMode? by remember(context) { SettingsManager.getFullscreenMode(context) }
        .collectAsStateWithLifecycle(initialValue = null)
    val completionBehavior by remember(context) { SettingsManager.getPlaybackCompletionBehavior(context) }
        .collectAsStateWithLifecycle(initialValue = remember(context) { SettingsManager.getPlaybackCompletionBehaviorSync(context) })
    val uiLayoutPolicy = resolveVideoPlayerUiLayoutPolicy(
        widthDp = configuration.screenWidthDp,
        windowHeightDp = configuration.screenHeightDp,
    )
    val gestureMotionSpec = remember { resolveVideoGestureMotionSpec() }
    val task = tasks[currentTaskId]

    // === 状态管理 ===
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var playerVideoSize by remember(currentTaskId) { mutableStateOf(VideoSize.UNKNOWN) }
    val isVerticalVideo = if (playerVideoSize.width > 0 && playerVideoSize.height > 0) {
        playerVideoSize.height > playerVideoSize.width
    } else {
        task?.isVerticalVideo == true
    }
    var fullscreenForNextEpisode by remember(taskId) { mutableStateOf<Boolean?>(null) }
    var restartEpisodeFromBeginning by remember(taskId) { mutableStateOf(false) }

    var isFullscreen by remember(currentTaskId) {
        mutableStateOf(fullscreenForNextEpisode == true)
    }
    val viewportAspectRatio = if (isFullscreen) {
        playerInteractionSettings.fixedFullscreenAspectRatio.toVideoAspectRatio()
    } else {
        VideoAspectRatio.FIT
    }
    var twoFingerSpeedGestureActive by remember(currentTaskId) { mutableStateOf(false) }
    var twoFingerSpeedFeedbackVisible by remember(currentTaskId, twoFingerSpeedMode) { mutableStateOf(false) }
    var twoFingerSpeedFeedback by remember(currentTaskId) { mutableFloatStateOf(1f) }
    // 手势状态
    var gestureMode by remember { mutableStateOf(VideoGestureMode.None) }
    var gesturePercent by remember { mutableFloatStateOf(0f) }
    var isGestureVisible by remember { mutableStateOf(false) }
    var fullscreenSwipeProgress by remember { mutableFloatStateOf(0f) }
    val showFullscreenSwipeHint by remember {
        derivedStateOf { gestureMode == VideoGestureMode.SwipeToFullscreen && fullscreenSwipeProgress > 0f }
    }

    // 进度拖动状态
    var seekTargetTime by remember { mutableLongStateOf(0L) }
    var startPosition by remember { mutableLongStateOf(0L) }
    var totalDragDistanceX by remember { mutableFloatStateOf(0f) }
    var totalDragDistanceY by remember { mutableFloatStateOf(0f) }
    var startVolume by remember { mutableIntStateOf(0) }
    var startBrightness by remember { mutableFloatStateOf(0.5f) }

    // 双击跳转反馈
    var seekFeedbackGeneration by remember(currentTaskId) { mutableLongStateOf(0L) }
    var seekFeedbackText by remember(currentTaskId) { mutableStateOf<String?>(null) }
    var seekFeedbackVisible by remember(currentTaskId) { mutableStateOf(false) }

    // 长按倍速状态
    var isLongPressing by remember(currentTaskId) { mutableStateOf(false) }
    var longPressSpeedLocked by remember(currentTaskId) { mutableStateOf(false) }
    val playerGestureHaptic = com.android.purebilibili.core.util.rememberHapticFeedback(
        com.android.purebilibili.core.ui.AppHapticScene.PLAYER_GESTURES
    )
    val playerSeekHaptic = com.android.purebilibili.core.util.rememberHapticFeedback(
        com.android.purebilibili.core.ui.AppHapticScene.PLAYER_SEEK
    )
    var originalPlaybackParameters by remember(currentTaskId) { mutableStateOf(PlaybackParameters.DEFAULT) }
    var longPressSpeedHintDismissed by remember { mutableStateOf(false) }
    var danmakuEnabled by remember(currentTaskId) { mutableStateOf(true) }

    LaunchedEffect(danmakuManager, danmakuSettings) {
        danmakuManager.updateSettings(settings = danmakuSettings)
    }
    LaunchedEffect(currentTaskId, danmakuSettings.enabled) {
        danmakuEnabled = danmakuSettings.enabled
    }

    if (task == null || task.filePath == null) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText("视频文件不存在", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                AppButton(onClick = onBack) { AppText("返回") }
            }
        }
        return
    }

    val file = File(task.filePath)
    if (!file.exists()) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AppText("视频文件已被删除", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                AppButton(onClick = onBack) { AppText("返回") }
            }
        }
        return
    }

    val episodeQueue = remember(tasks, task.id) {
        resolveOfflineEpisodeQueue(
            tasks = tasks.values,
            currentTask = task
        )
    }
    val currentEpisodeIndex = remember(episodeQueue, task.id) {
        episodeQueue.indexOfFirst { it.id == task.id }
    }

    // 创建播放器
    val player = remember(context, task.id, file.absolutePath) {
        ExoPlayer.Builder(context)
            .setRenderersFactory(
                com.android.purebilibili.core.player.HiResCompatibleRenderersFactory(context)
                    .setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    .withAppVideoDecoderPreference(context)
            )
            .experimentalSetDynamicSchedulingEnabled(true)
            .build().withAppVideoDecoderDiagnostics(context)
    }
    var playerView by remember(player) { mutableStateOf<PlayerView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var foregroundRecoveryGeneration by remember(player) { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, player) {
        var hasObservedPause = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasObservedPause = true
                Lifecycle.Event.ON_RESUME -> if (hasObservedPause) {
                    hasObservedPause = false
                    foregroundRecoveryGeneration++
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lifecycleOwner, player, playerView, foregroundRecoveryGeneration) {
        if (foregroundRecoveryGeneration == 0) return@LaunchedEffect
        val view = playerView ?: return@LaunchedEffect
        // 等前台轨道恢复与当前布局提交后再重绑；横竖屏共用此恢复入口。
        withFrameNanos { }
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            return@LaunchedEffect
        }
        val needsSurfaceRecovery = miniPlayerManager.consumeForegroundSurfaceRecoveryNeed()
        if (!task.isAudioOnly && needsSurfaceRecovery) {
            // PlayerView 仍持有同一个 player 时，直接赋值不会恢复已被后台清除的输出。
            rebindPlayerSurfaceIfNeeded(view, player)
        }
        danmakuManager.recoverAfterForeground(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            playWhenReady = player.playWhenReady,
            playbackState = player.playbackState,
            preserveTimeline = !needsSurfaceRecovery,
        )
    }

    var playbackFailure by remember(player) { mutableStateOf<OfflinePlaybackFailure?>(null) }
    var retryVersion by remember(player) { mutableIntStateOf(0) }
    var lastKnownPlaybackPosition by remember(player) {
        mutableLongStateOf(task.lastPlaybackPositionMs.coerceAtLeast(0L))
    }
    val offlineSessionRegistered = remember(file.exists(), task.filePath) {
        shouldRegisterOfflinePlaybackSession(
            fileExists = file.exists(),
            filePath = task.filePath
        )
    }
    val offlineMiniPlayerPayload = remember(task) {
        resolveOfflineMiniPlayerPayload(task)
    }
    val localDanmakuSource by produceState(
        initialValue = LocalDanmakuSource(),
        key1 = task.localDanmakuSegmentPaths,
        key2 = task.localDanmakuMetadataPath
    ) {
        value = if (task.localDanmakuSegmentPaths.isEmpty()) {
            LocalDanmakuSource()
        } else {
            withContext(Dispatchers.IO) {
                DownloadDanmakuAssetService.readLocalSource(task)
            }
        }
    }
    val danmakuAvailable = shouldShowOfflineDanmakuControl(
        localSegmentCount = localDanmakuSource.totalFileCount,
        isAudioOnly = task.isAudioOnly
    )
    val showDanmakuLayer = shouldShowOfflineDanmakuLayer(
        localSegmentCount = localDanmakuSource.totalFileCount,
        isAudioOnly = task.isAudioOnly,
        danmakuEnabled = danmakuEnabled
    )

    fun persistCurrentPlaybackPosition(activeTask: DownloadTask, activePlayer: ExoPlayer) {
        // Read ExoPlayer state on its application thread before dispatching persistence.
        val positionMs = resolveOfflinePersistedPlaybackPosition(
            currentPositionMs = if (
                activePlayer.playerError != null || activePlayer.playbackState == Player.STATE_IDLE
            ) {
                lastKnownPlaybackPosition
            } else {
                activePlayer.currentPosition
            },
            durationMs = activePlayer.duration
        )
        playbackPersistenceScope.launch {
            DownloadManager.updatePlaybackPosition(
                taskId = activeTask.id,
                positionMs = positionMs
            )
        }
    }

    // 进度状态
    val progressState by produceState(
        initialValue = ProgressInfo(0L, 0L, 0L),
        key1 = player,
        key2 = showControls
    ) {
        while (isActive) {
            val duration = if (player.duration < 0) 0L else player.duration
            value = ProgressInfo(
                current = player.currentPosition,
                duration = duration,
                buffered = player.bufferedPosition
            )
            isPlaying = player.isPlaying
            if (player.playbackState == Player.STATE_READY && player.playerError == null) {
                lastKnownPlaybackPosition = player.currentPosition.coerceAtLeast(0L)
            }
            delay(if (showControls) 200L else 500L)
        }
    }

    // 自动隐藏控制栏
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // 双击反馈自动消失
    LaunchedEffect(seekFeedbackGeneration) {
        if (seekFeedbackGeneration > 0L) {
            delay(800)
            seekFeedbackVisible = false
        }
    }
    LaunchedEffect(currentTaskId, twoFingerSpeedFeedbackVisible, twoFingerSpeedGestureActive, isFullscreen, playbackFailure) {
        if (!isFullscreen || playbackFailure != null) {
            twoFingerSpeedFeedbackVisible = false
        } else if (twoFingerSpeedFeedbackVisible && !twoFingerSpeedGestureActive) {
            delay(900L)
            twoFingerSpeedFeedbackVisible = false
        }
    }

    // 获取 Activity
    fun getActivity(): Activity? = activity

    fun applyWindowMode(fullscreen: Boolean, mode: FullscreenMode) {
        val act = getActivity() ?: return
        val requestedOrientation = when (
            resolveOfflineRequestedOrientationMode(
                isFullscreen = fullscreen,
                displayContext = displayContext,
                fullscreenMode = mode,
                isVerticalVideo = isVerticalVideo,
            )
        ) {
            OfflineRequestedOrientationMode.Unspecified ->
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OfflineRequestedOrientationMode.SensorLandscape ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OfflineRequestedOrientationMode.Portrait ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            OfflineRequestedOrientationMode.KeepCurrent -> null
        }
        if (requestedOrientation != null) {
            act.applyPlayerRequestedOrientation(
                requestedOrientation = requestedOrientation,
                displayContext = displayContext,
            )
        }
        if (fullscreen) {
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    var hasAutoEnteredFullscreen by remember(currentTaskId) { mutableStateOf(false) }

    // 全屏切换函数
    fun toggleFullscreen() {
        hasAutoEnteredFullscreen = true
        isFullscreen = !isFullscreen
    }

    fun seekToPosition(targetPositionMs: Long) {
        val duration = player.duration.coerceAtLeast(0L)
        val safeTarget = if (duration > 0L) targetPositionMs.coerceIn(0L, duration) else targetPositionMs.coerceAtLeast(0L)
        val shouldResume = shouldResumePlaybackAfterOfflineSeek(
            playbackState = player.playbackState,
            wasPlayingBeforeSeek = player.isPlaying,
            targetPositionMs = safeTarget,
            durationMs = duration
        )
        player.seekTo(safeTarget)
        if (shouldResume) {
            player.play()
        }
    }

    fun switchEpisode(targetTaskId: String, fromBeginning: Boolean = false) {
        if (targetTaskId == task.id) {
            if (fromBeginning) {
                seekToPosition(0L)
                player.play()
            }
            return
        }
        persistCurrentPlaybackPosition(task, player)
        fullscreenForNextEpisode = isFullscreen
        restartEpisodeFromBeginning = fromBeginning
        currentTaskId = targetTaskId
        showControls = true
    }

    fun finishLongPressSpeedGesture() {
        if (!isLongPressing) return
        if (!longPressSpeedLocked) player.playbackParameters = originalPlaybackParameters
        isLongPressing = false
    }

    val speedLockEnabled = playerInteractionSettings.longPressSpeedLockEnabled
    val speedLockSensitivity = remember(isFullscreen) { resolveLongPressSpeedLockSensitivityPolicy(isFullscreen) }
    LaunchedEffect(player, speedLockEnabled) {
        if (!speedLockEnabled && longPressSpeedLocked) {
            longPressSpeedLocked = false
            player.playbackParameters = originalPlaybackParameters
        }
    }

    val onPlaybackEnded: () -> Unit by rememberUpdatedState(newValue = {
        isPlaying = false
        val target = resolveOfflineEpisodeAfterPlayback(
            queue = episodeQueue,
            currentIndex = currentEpisodeIndex,
            behavior = completionBehavior,
            autoPlayEnabled = SettingsManager.getAutoPlaySync(context),
        )
        if (shouldAutoExitFullscreenOnPlaybackEnded(
                mode = playerInteractionSettings.autoExitFullscreenMode,
                isFullscreen = isFullscreen,
                playbackState = Player.STATE_ENDED,
                willContinueToNextItem = target != null,
            )
        ) {
            toggleFullscreen()
        }
        if (target != null) {
            switchEpisode(target.id, fromBeginning = true)
        } else {
            showControls = true
        }
    })
    val onPlaybackStarted: () -> Unit by rememberUpdatedState(newValue = {
        if (!task.isAudioOnly && fullscreenForNextEpisode == null && !hasAutoEnteredFullscreen &&
            playerInteractionSettings.autoEnterFullscreenEnabled
        ) {
            hasAutoEnteredFullscreen = true
            isFullscreen = true
        }
    })

    // 返回键处理
    LocalNavigationBackHandler(enabled = isFullscreen) { toggleFullscreen() }

    var previousOfflineDisplayRole by remember {
        mutableStateOf(displayContext.foldableDisplayRole)
    }
    LaunchedEffect(activity, displayContext.foldableDisplayRole) {
        if (
            com.android.purebilibili.core.util.shouldReleaseOrientationLockOnDisplayRoleChange(
                previousRole = previousOfflineDisplayRole,
                nextRole = displayContext.foldableDisplayRole,
            )
        ) {
            activity?.applyPlayerRequestedOrientation(
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                displayContext = displayContext,
            )
        }
        previousOfflineDisplayRole = displayContext.foldableDisplayRole
    }
    LaunchedEffect(activity, displayContext, isFullscreen, fullscreenMode, isVerticalVideo) {
        fullscreenMode?.let { applyWindowMode(isFullscreen, it) }
    }
    DisposableEffect(activity) {
        onDispose {
            activity?.applyPlayerRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            activity?.let { act ->
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(player) {
        var hasStartedPlayback = false
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    hasStartedPlayback = true
                    onPlaybackStarted()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackFailure = resolveOfflinePlaybackFailure(error.errorCode)
                isPlaying = false
                showControls = true
                isGestureVisible = false
                seekFeedbackVisible = false
                if (isLongPressing || longPressSpeedLocked) {
                    player.playbackParameters = originalPlaybackParameters
                    isLongPressing = false
                    longPressSpeedLocked = false
                }
                player.pause()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) playbackFailure = null
                if (playbackState == Player.STATE_ENDED && hasStartedPlayback) onPlaybackEnded()
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    SideEffect {
        val repeatMode = resolvePlaybackCompletionRepeatMode(completionBehavior)
        if (player.repeatMode != repeatMode) player.repeatMode = repeatMode
    }

    LaunchedEffect(player, retryVersion) {
        val shouldStart = retryVersion > 0 || fullscreenForNextEpisode != null ||
            SettingsManager.getClickToPlay(context).first()
        val restoredPosition = when {
            retryVersion > 0 -> lastKnownPlaybackPosition
            restartEpisodeFromBeginning -> 0L
            else -> task.lastPlaybackPositionMs.coerceAtLeast(0L)
        }
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)), restoredPosition)
        player.playWhenReady = shouldStart
        player.prepare()
    }

    DisposableEffect(player, task.id) {
        danmakuManager.attachPlayer(player)
        onDispose {
            persistCurrentPlaybackPosition(task, player)
            danmakuManager.detachPlayer(player)
            if (miniPlayerManager.isPlayerManaged(player)) {
                miniPlayerManager.dismiss()
            } else {
                miniPlayerManager.clearExternalPlayerIfMatches(player)
            }
            isLongPressing = false
            player.release()
        }
    }

    LaunchedEffect(danmakuManager, task.id, localDanmakuSource) {
        if (localDanmakuSource.totalFileCount > 0) {
            danmakuManager.loadLocalDanmaku(
                cid = task.cid,
                standardSegmentPaths = localDanmakuSource.standardSegmentPaths,
                specialSegmentPaths = localDanmakuSource.specialSegmentPaths
            )
        }
    }

    LaunchedEffect(danmakuManager, showDanmakuLayer) {
        // Data commits and player callbacks also consult the engine's enabled state.
        // Hiding only the View lets an asynchronous load turn it visible again.
        danmakuManager.isEnabled = showDanmakuLayer
    }

    DisposableEffect(player, offlineSessionRegistered, offlineMiniPlayerPayload) {
        if (offlineSessionRegistered) {
            miniPlayerManager.setVideoInfo(
                bvid = offlineMiniPlayerPayload.bvid,
                title = offlineMiniPlayerPayload.title,
                cover = offlineMiniPlayerPayload.coverUrl,
                owner = offlineMiniPlayerPayload.owner,
                cid = offlineMiniPlayerPayload.cid,
                externalPlayer = player
            )
            miniPlayerManager.updateMediaMetadata(
                title = offlineMiniPlayerPayload.title,
                artist = offlineMiniPlayerPayload.owner,
                coverUrl = offlineMiniPlayerPayload.coverUrl
            )
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!offlineSessionRegistered) return
                miniPlayerManager.updateMediaMetadata(
                    title = offlineMiniPlayerPayload.title,
                    artist = offlineMiniPlayerPayload.owner,
                    coverUrl = offlineMiniPlayerPayload.coverUrl
                )
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                playerVideoSize = videoSize
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(player, task.id) {
        var lastPersistedPosition = task.lastPlaybackPositionMs
        while (isActive) {
            val resolvedPosition = resolveOfflinePersistedPlaybackPosition(
                currentPositionMs = player.currentPosition,
                durationMs = player.duration
            )
            if (
                player.playerError == null && player.playbackState == Player.STATE_READY &&
                abs(resolvedPosition - lastPersistedPosition) >= 2_000L
            ) {
                DownloadManager.updatePlaybackPosition(task.id, resolvedPosition)
                lastPersistedPosition = resolvedPosition
            }
            delay(2_000L)
        }
    }

    // 半开折叠姿态：媒体与控件整体收进首个安全区，不跨物理铰链（对齐主视频/番剧/直播）。
    val offlinePlayerSurface: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .basicPlayerKeyboardShortcuts(
                    player = player,
                    onToggleFullscreen = { toggleFullscreen() },
                    onToggleDanmaku = { danmakuEnabled = !danmakuEnabled },
                )
                .background(Color.Black)
                .pointerInput(player, playbackFailure, isFullscreen, twoFingerSpeedMode, playbackSpeedOptions) {
                    if (playbackFailure != null || !isFullscreen || task.isAudioOnly ||
                        twoFingerSpeedMode == TwoFingerSpeedGestureMode.Off || playbackSpeedOptions.isEmpty()
                    ) {
                        return@pointerInput
                    }
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var ownsGesture = false
                        var speedGestureCancelled = false
                        var startSpeed: Float? = null
                        var totalPanX = 0f
                        var totalPanY = 0f
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pressed = event.changes.count { it.pressed }
                                if (pressed >= 2 && !ownsGesture) {
                                    ownsGesture = true
                                    finishLongPressSpeedGesture()
                                    isGestureVisible = false
                                    gestureMode = VideoGestureMode.None
                                }
                                // 双指接管后，剩余手指抬起前也不能触发单指手势。
                                if (ownsGesture) event.changes.forEach { it.consume() }
                                if (pressed == 0) break
                                if (pressed == 1) {
                                    if (ownsGesture) {
                                        speedGestureCancelled = true
                                        twoFingerSpeedGestureActive = false
                                    }
                                    continue
                                }

                                if (abs(event.calculateZoom() - 1f) > 0.005f) speedGestureCancelled = true
                                if (speedGestureCancelled) continue
                                val pan = event.calculatePan()
                                totalPanX += pan.x
                                totalPanY += pan.y
                                if (startSpeed == null) {
                                    val axis = resolveLockedTwoFingerSpeedAxis(
                                        mode = twoFingerSpeedMode,
                                        totalDragX = totalPanX,
                                        totalDragY = totalPanY,
                                        thresholdPx = viewConfiguration.touchSlop * 1.5f,
                                    )
                                    if (axis == null) continue
                                    startSpeed = player.playbackParameters.speed
                                    twoFingerSpeedGestureActive = true
                                    showControls = false
                                }
                                val speed = resolveTwoFingerGesturePlaybackSpeed(
                                    startSpeed = startSpeed,
                                    mode = twoFingerSpeedMode,
                                    totalDragX = totalPanX,
                                    totalDragY = totalPanY,
                                    containerWidthPx = size.width.toFloat(),
                                    containerHeightPx = size.height.toFloat(),
                                    supportedSpeeds = playbackSpeedOptions,
                                )
                                if (abs(player.playbackParameters.speed - speed) > 0.001f) {
                                    longPressSpeedLocked = false
                                    player.setPlaybackSpeed(speed)
                                }
                                twoFingerSpeedFeedback = speed
                                twoFingerSpeedFeedbackVisible = true
                            }
                        } finally {
                            twoFingerSpeedGestureActive = false
                        }
                    }
                }
                // 🎛️ 拖拽手势：亮度/音量/进度
                .pointerInput(player, playbackFailure, isFullscreen, playerInteractionSettings) {
                    isGestureVisible = false
                    gestureMode = VideoGestureMode.None
                    if (playbackFailure != null) return@pointerInput
                    var dragStartX = 0f
                    val fullscreenSwipeThresholdPx = SwipeToFullscreenDragThresholdDp.toPx()
                    fullscreenSwipeProgress = 0f
                    detectEdgeSafeDragGestures(
                        shouldIgnoreStart = { downOffset ->
                            // 边缘防误触（基于初始落点，避免全屏底部快速上滑越过排除区）
                            val gestureExclusions = resolveVideoPlayerGestureVerticalExclusions(
                                containerHeightPx = size.height.toFloat(),
                                isFullscreen = isFullscreen,
                                controlsVisible = showControls,
                                requestedBottomControlsExclusionPx = 0f,
                                inlineTopExclusionPx = INLINE_PLAYER_TOP_GESTURE_EXCLUSION_DP.dp.toPx(),
                                inlineBottomExclusionPx = INLINE_PLAYER_BOTTOM_GESTURE_EXCLUSION_DP.dp.toPx(),
                                fullscreenEdgeExclusionPx = FULLSCREEN_PLAYER_EDGE_GESTURE_EXCLUSION_DP.dp.toPx(),
                                fullscreenBottomExclusionPx = FULLSCREEN_PLAYER_BOTTOM_GESTURE_EXCLUSION_DP.dp.toPx(),
                            )
                            shouldIgnoreVideoPlayerDragStart(
                                offsetY = downOffset.y,
                                containerHeightPx = size.height.toFloat(),
                                topGestureExclusionPx = gestureExclusions.topPx,
                                bottomGestureExclusionPx = gestureExclusions.bottomPx,
                            ) || shouldIgnoreVideoPlayerHorizontalEdgeDragStart(
                                offsetX = downOffset.x,
                                containerWidthPx = size.width.toFloat(),
                                isFullscreen = isFullscreen,
                                edgeGestureExclusionPx = FULLSCREEN_PLAYER_EDGE_GESTURE_EXCLUSION_DP.dp.toPx(),
                            )
                        },
                        onDragStart = { offset ->
                            isGestureVisible = true
                            gestureMode = VideoGestureMode.None
                            dragStartX = offset.x
                            totalDragDistanceY = 0f
                            totalDragDistanceX = 0f

                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            startPosition = player.currentPosition
                            seekTargetTime = startPosition

                            val attributes = getActivity()?.window?.attributes
                            val currentWindowBrightness = attributes?.screenBrightness ?: -1f

                            if (currentWindowBrightness < 0) {
                                try {
                                    val sysBrightness = Settings.System.getInt(
                                        context.contentResolver,
                                        Settings.System.SCREEN_BRIGHTNESS
                                    )
                                    startBrightness = sysBrightness / 255f
                                } catch (e: Exception) {
                                    startBrightness = 0.5f
                                }
                            } else {
                                startBrightness = currentWindowBrightness
                            }
                        },
                        onDragEnd = {
                            when (gestureMode) {
                                VideoGestureMode.Seek -> seekToPosition(seekTargetTime)
                                VideoGestureMode.SwipeToFullscreen -> {
                                    if (shouldTriggerFullscreenBySwipe(
                                            isFullscreen = isFullscreen,
                                            reverseGesture = playerInteractionSettings.fullscreenGestureReverse,
                                            totalDragDistanceY = -totalDragDistanceY,
                                            thresholdPx = fullscreenSwipeThresholdPx,
                                        )
                                    ) {
                                        toggleFullscreen()
                                    }
                                }
                                else -> Unit
                            }
                            fullscreenSwipeProgress = 0f
                            isGestureVisible = false
                            gestureMode = VideoGestureMode.None
                        },
                        onDragCancel = {
                            fullscreenSwipeProgress = 0f
                            isGestureVisible = false
                            gestureMode = VideoGestureMode.None
                        },
                        onDrag = { _, dragAmount ->
                            if (isGestureVisible) {
                                // 确定手势类型
                                if (gestureMode == VideoGestureMode.None) {
                                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                        gestureMode = VideoGestureMode.Seek
                                    } else {
                                        gestureMode = resolveVerticalGestureMode(
                                            isFullscreen = isFullscreen,
                                            isSwipeUp = dragAmount.y < 0,
                                            startX = dragStartX,
                                            leftZoneEnd = size.width * 0.3f,
                                            rightZoneStart = size.width * 0.7f,
                                            portraitSwipeToFullscreenEnabled = !task.isAudioOnly &&
                                                !playerInteractionSettings.fullscreenGestureReverse &&
                                                playerInteractionSettings.portraitSwipeToFullscreenEnabled,
                                            centerSwipeToFullscreenEnabled = !task.isAudioOnly &&
                                                playerInteractionSettings.centerSwipeToFullscreenEnabled,
                                            slideVolumeBrightnessEnabled = playerInteractionSettings.slideVolumeBrightnessEnabled,
                                        )
                                        when (gestureMode) {
                                            VideoGestureMode.None -> isGestureVisible = false
                                            VideoGestureMode.Brightness -> gesturePercent = startBrightness
                                            VideoGestureMode.Volume -> gesturePercent = startVolume.toFloat() / maxVolume
                                            else -> Unit
                                        }
                                    }
                                }

                                when (gestureMode) {
                                    VideoGestureMode.Seek -> {
                                        totalDragDistanceX += dragAmount.x
                                        val duration = player.duration.coerceAtLeast(0L)
                                        val seekDelta = resolveHorizontalSeekDeltaMs(
                                            totalDragDistanceX = totalDragDistanceX,
                                            containerWidthPx = size.width.toFloat(),
                                            gestureSensitivity = playerInteractionSettings.gestureSensitivity,
                                            durationMs = duration,
                                            fixedSeekEnabled = isFullscreen && playerInteractionSettings.fullscreenSwipeSeekEnabled,
                                            fixedSeekSeconds = playerInteractionSettings.swipeSeekSeconds,
                                        )
                                        if (seekDelta != null) {
                                            seekTargetTime = (startPosition + seekDelta).coerceIn(0L, duration)
                                        }
                                    }
                                    VideoGestureMode.Brightness -> {
                                        totalDragDistanceY -= dragAmount.y
                                        val deltaPercent = totalDragDistanceY / size.height *
                                            playerInteractionSettings.gestureSensitivity
                                        val newBrightness = (startBrightness + deltaPercent).coerceIn(0f, 1f)

                                        if (abs(newBrightness - gesturePercent) > 0.02f) {
                                            getActivity()?.window?.attributes = getActivity()?.window?.attributes?.apply {
                                                screenBrightness = newBrightness
                                            }
                                            if (playerInteractionSettings.setSystemBrightnessEnabled &&
                                                Settings.System.canWrite(context)
                                            ) {
                                                Settings.System.putInt(
                                                    context.contentResolver,
                                                    Settings.System.SCREEN_BRIGHTNESS,
                                                    (newBrightness * 255f).roundToInt().coerceIn(1, 255),
                                                )
                                            }
                                            gesturePercent = newBrightness
                                        }
                                    }
                                    VideoGestureMode.Volume -> {
                                        totalDragDistanceY -= dragAmount.y
                                        val deltaPercent = totalDragDistanceY / size.height *
                                            playerInteractionSettings.gestureSensitivity
                                        val newVolPercent = (startVolume.toFloat() / maxVolume + deltaPercent)
                                            .coerceIn(0f, 1f)
                                        val targetVol = (newVolPercent * maxVolume).toInt()
                                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                        gesturePercent = newVolPercent
                                    }
                                    VideoGestureMode.SwipeToFullscreen -> {
                                        totalDragDistanceY -= dragAmount.y
                                        fullscreenSwipeProgress = resolveSwipeToFullscreenHintFraction(
                                            isFullscreen = isFullscreen,
                                            reverseGesture = playerInteractionSettings.fullscreenGestureReverse,
                                            totalDragDistanceY = -totalDragDistanceY,
                                            thresholdPx = fullscreenSwipeThresholdPx,
                                        )
                                    }
                                    VideoGestureMode.None -> Unit
                                }
                            }
                        }
                    )
                }
                .pointerInput(player, playbackFailure, longPressSpeed, speedLockEnabled, speedLockSensitivity) {
                    if (playbackFailure != null) return@pointerInput
                    val lockZonePx = speedLockSensitivity.lockZoneHeightDp.dp.toPx()
                    val minDragPx = speedLockSensitivity.minDragDistanceDp.dp.toPx()
                    var startX = 0f
                    var startY = 0f
                    var accumulatedDragY = 0f
                    var startedAt = 0L
                    try {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                startX = offset.x
                                startY = offset.y
                                accumulatedDragY = 0f
                                startedAt = SystemClock.elapsedRealtime()
                                if (!longPressSpeedLocked) {
                                    originalPlaybackParameters = player.playbackParameters
                                }
                                player.setPlaybackSpeed(longPressSpeed)
                                longPressSpeedHintDismissed = false
                                isLongPressing = true
                                playerGestureHaptic(com.android.purebilibili.core.util.HapticType.MEDIUM)
                            },
                            onDragEnd = ::finishLongPressSpeedGesture,
                            onDragCancel = ::finishLongPressSpeedGesture,
                            onDrag = { change, dragAmount ->
                                if (speedLockEnabled) {
                                    accumulatedDragY += dragAmount.y
                                    if (shouldUnlockLockedLongPressSpeedFromRightDownDrag(
                                            longPressSpeedLocked = longPressSpeedLocked,
                                            isLongPressing = isLongPressing,
                                            startX = startX,
                                            startY = startY,
                                            currentY = change.position.y,
                                            containerWidthPx = size.width.toFloat(),
                                            holdDurationMs = SystemClock.elapsedRealtime() - startedAt,
                                            minDownDragPx = minDragPx,
                                        )
                                    ) {
                                        longPressSpeedLocked = false
                                        finishLongPressSpeedGesture()
                                        playerGestureHaptic(com.android.purebilibili.core.util.HapticType.MEDIUM)
                                    } else if (shouldLockLongPressSpeedInTargetZone(
                                            isLongPressing = isLongPressing,
                                            alreadyLocked = longPressSpeedLocked,
                                            currentPointerY = change.position.y,
                                            containerHeightPx = size.height.toFloat(),
                                            lockZoneHeightPx = lockZonePx,
                                            accumulatedDragYPx = accumulatedDragY,
                                            minDragDistancePx = minDragPx,
                                        )
                                    ) {
                                        longPressSpeedLocked = true
                                        playerGestureHaptic(com.android.purebilibili.core.util.HapticType.MEDIUM)
                                    }
                                }
                                change.consume()
                            },
                        )
                    } finally {
                        finishLongPressSpeedGesture()
                    }
                }
                // 点击与双击
                .pointerInput(
                    player,
                    playbackFailure,
                    playerInteractionSettings.doubleTapSeekEnabled,
                    playerInteractionSettings.seekForwardSeconds,
                    playerInteractionSettings.seekBackwardSeconds,
                ) {
                    if (playbackFailure != null) return@pointerInput
                    detectPlayerTapGestures(
                        doubleTapSlopPx = android.view.ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat(),
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            val action = resolveFullscreenDoubleTapAction(
                                relativeX = offset.x / size.width,
                                doubleTapSeekEnabled = playerInteractionSettings.doubleTapSeekEnabled,
                                playWhenReady = player.playWhenReady,
                                isPlaying = player.isPlaying,
                                playbackState = player.playbackState,
                            )
                            val deltaSeconds = when (action) {
                                FullscreenDoubleTapAction.SeekForward -> playerInteractionSettings.seekForwardSeconds
                                FullscreenDoubleTapAction.SeekBackward -> -playerInteractionSettings.seekBackwardSeconds
                                FullscreenDoubleTapAction.TogglePlayPause -> {
                                    togglePlayerPlaybackFromUserAction(player)
                                    playerGestureHaptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                                    return@detectPlayerTapGestures
                                }
                            }
                            playerSeekHaptic(com.android.purebilibili.core.util.HapticType.LIGHT)
                            seekToPosition(
                                resolveRelativeSeekTargetPosition(
                                    currentPositionMs = player.currentPosition,
                                    deltaMs = deltaSeconds * 1000L,
                                    durationMs = player.duration,
                                )
                            )
                            val feedback = nextFullscreenSeekFeedbackEvent(seekFeedbackGeneration, deltaSeconds)
                            seekFeedbackGeneration = feedback.generation
                            seekFeedbackText = feedback.text
                            seekFeedbackVisible = true
                        },
                    )
                }
        ) {
            // 画面比例只约束视频视口；悬停态（Tabletop）收进上半屏，控制条留于下半屏。
            BoxWithConstraints(
                modifier = Modifier
                    .tabletopUpperViewportLayout(
                        enabled = appWindowAdaptiveInfo.posture == AppFoldPosture.Tabletop,
                        adaptiveInfo = appWindowAdaptiveInfo,
                    )
                    .fillMaxSize()
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val viewport = remember(maxWidth, maxHeight, density, viewportAspectRatio, playerVideoSize) {
                    with(density) {
                        resolveVideoViewportLayout(
                            containerWidth = maxWidth.roundToPx(),
                            containerHeight = maxHeight.roundToPx(),
                            aspectRatio = viewportAspectRatio,
                            videoWidth = playerVideoSize.width,
                            videoHeight = playerVideoSize.height,
                        )
                    }
                }
                val resizeMode = viewportAspectRatio.playerResizeMode
                LaunchedEffect(playerView, player, viewport, resizeMode, playerVideoSize) {
                    playerView?.let { view ->
                        schedulePlayerViewViewportRefresh(
                            playerView = view,
                            resizeMode = resizeMode,
                            expectedWidth = viewport.width,
                            expectedHeight = viewport.height,
                            videoWidth = playerVideoSize.width,
                            videoHeight = playerVideoSize.height,
                        )
                    }
                }
                val sharedSurfaceVisible = com.android.purebilibili.navigation3.LocalNavigationSharedSurfaceVisible.current
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            setEnableComposeSurfaceSyncWorkaround(true)
                            this.player = player
                            useController = false
                            this.resizeMode = resizeMode
                            keepScreenOn = true
                            visibility = if (sharedSurfaceVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                            playerView = this
                        }
                    },
                    update = { view ->
                        view.player = player
                        view.resizeMode = resizeMode
                        view.keepScreenOn = playbackFailure == null && isPlaying
                        view.visibility = if (sharedSurfaceVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                    },
                    onRelease = { view ->
                        cancelPlayerViewViewportRefresh(view)
                        view.player = null
                        if (playerView === view) playerView = null
                    },
                    modifier = with(density) {
                        Modifier.requiredSize(viewport.width.toDp(), viewport.height.toDp())
                    },
                )
                // 封面常驻就绪，避免返回隐藏 PlayerView 首帧才异步挂载封面造成闪黑。
                if (task.cover.isNotBlank()) {
                    AsyncImage(
                        model = task.cover,
                        contentDescription = if (!sharedSurfaceVisible) task.title else null,
                        contentScale = ContentScale.Crop,
                        modifier = with(density) {
                            Modifier
                                .requiredSize(viewport.width.toDp(), viewport.height.toDp())
                                .graphicsLayer {
                                    alpha = if (!sharedSurfaceVisible) 1f else 0f
                                }
                        },
                    )
                }
            }

            if (danmakuAvailable) {
                val sharedDanmakuVisible = com.android.purebilibili.navigation3.LocalNavigationSharedSurfaceVisible.current
                val useIndependentDanmakuSurface = shouldUseIndependentDanmakuSurface(
                    useTextureVideoSurface = false,
                    anime4kActive = false,
                    capturePlayerPage = false,
                    isInPipMode = false,
                )
                val danmakuOverlayVisible = showDanmakuLayer && sharedDanmakuVisible
                val danmakuTargetFrameRateHz = rememberPlayerDanmakuTargetFrameRateHz(
                    player = player,
                    isPlaying = isPlaying && danmakuOverlayVisible && !task.isAudioOnly,
                )
                key(danmakuManager, useIndependentDanmakuSurface) {
                    val lastTargetFrameRateRef = remember { floatArrayOf(0f) }
                    AndroidView(
                        factory = { ctx ->
                            DanmakuRenderView(
                                context = ctx,
                                usesIndependentSurface = useIndependentDanmakuSurface,
                            ).apply {
                                danmakuManager.useFullscreenTextSize = isFullscreen
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                configureAsPassiveDanmakuOverlay()
                                setSurfaceOverlayVisible(danmakuOverlayVisible)
                                engine.setTargetFrameRateHz(danmakuTargetFrameRateHz)
                                lastTargetFrameRateRef[0] = danmakuTargetFrameRateHz
                                danmakuManager.attachView(this)
                            }
                        },
                        update = { view ->
                            danmakuManager.useFullscreenTextSize = isFullscreen
                            view.visibility = if (danmakuOverlayVisible) {
                                android.view.View.VISIBLE
                            } else {
                                android.view.View.GONE
                            }
                            view.setSurfaceOverlayVisible(danmakuOverlayVisible)
                            if (lastTargetFrameRateRef[0] != danmakuTargetFrameRateHz) {
                                view.engine.setTargetFrameRateHz(danmakuTargetFrameRateHz)
                                lastTargetFrameRateRef[0] = danmakuTargetFrameRateHz
                            }
                            if (view.width > 0 && view.height > 0) {
                                val sizeTag = "${view.width}x${view.height}"
                                if (view.tag != sizeTag) {
                                    view.tag = sizeTag
                                    danmakuManager.attachView(view)
                                }
                            }
                        },
                        onRelease = { view ->
                            if (lastTargetFrameRateRef[0] != 0f) {
                                view.engine.setTargetFrameRateHz(0f)
                                lastTargetFrameRateRef[0] = 0f
                            }
                            danmakuManager.detachView(view)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // 2. 封面图（播放前显示，或是纯音频模式常驻显示）
            val showCover = (!player.isPlaying && player.playbackState == Player.STATE_IDLE) || task.isAudioOnly
            AnimatedVisibility(visible = showCover, enter = fadeIn(), exit = fadeOut()) {
                val localCoverFile = task.localCoverPath?.let { File(it) }
                AsyncImage(
                    model = if (localCoverFile?.exists() == true) localCoverFile else task.cover,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                )
            }

            // 3. 手势指示器（亮度/音量/进度）
            if (isGestureVisible) {
                if (gestureMode == VideoGestureMode.Seek) {
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val durationSeconds = (player.duration / 1000).coerceAtLeast(1)
                            val targetSeconds = (seekTargetTime / 1000).toInt()
                            AppText(
                                text = "${FormatUtils.formatDuration(targetSeconds)} / ${FormatUtils.formatDuration(durationSeconds.toInt())}",
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val deltaSeconds = (seekTargetTime - startPosition) / 1000
                            val sign = if (deltaSeconds > 0) "+" else ""
                            if (deltaSeconds != 0L) {
                                AppText(
                                    text = "($sign${deltaSeconds}s)",
                                    color = com.android.purebilibili.core.ui.MediaContrastPalette.Foreground,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                } else if (
                    gestureMode == VideoGestureMode.Brightness ||
                    gestureMode == VideoGestureMode.Volume
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        GestureLevelOverlayContent(
                            mode = if (gestureMode == VideoGestureMode.Brightness) {
                                VideoGestureMode.Brightness
                            } else {
                                VideoGestureMode.Volume
                            },
                            percent = gesturePercent,
                            style = gestureLevelOverlayStyle,
                            modifier = Modifier
                                .align(
                                    if (playerChromeProfile.effects.usesTonalContainerTreatment) {
                                        if (gestureMode == VideoGestureMode.Volume) {
                                            Alignment.CenterEnd
                                        } else {
                                            Alignment.CenterStart
                                        }
                                    } else {
                                        Alignment.Center
                                    }
                                )
                                .then(
                                    if (playerChromeProfile.effects.usesTonalContainerTreatment) {
                                        Modifier.padding(horizontal = 22.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }

            PlayerSeekFeedback(
                visible = seekFeedbackVisible,
                seekFeedbackText = seekFeedbackText,
                uiLayoutPolicy = uiLayoutPolicy,
                modifier = Modifier.align(Alignment.Center),
            )

            TwoFingerSpeedFeedbackOverlay(
                visible = isFullscreen && playbackFailure == null && twoFingerSpeedFeedbackVisible,
                speed = twoFingerSpeedFeedback,
                mode = twoFingerSpeedMode,
            )

            SwipeToFullscreenHint(
                visible = isGestureVisible && showFullscreenSwipeHint,
                progressProvider = { fullscreenSwipeProgress },
                isFullscreen = isFullscreen,
            )

            PlayerSpeedLockZones(
                visible = speedLockEnabled && isLongPressing && !longPressSpeedLocked,
                longPressSpeedLockSensitivity = speedLockSensitivity,
                gestureMotionSpec = gestureMotionSpec,
                modifier = Modifier.matchParentSize(),
            )

            PlayerLongPressSpeedHint(
                visible = isLongPressing && !playerInteractionSettings.longPressSpeedHintHidden &&
                    !longPressSpeedHintDismissed,
                longPressSpeedLocked = longPressSpeedLocked,
                effectiveLongPressSpeed = longPressSpeed,
                longPressSpeedHintScale = playerInteractionSettings.longPressSpeedHintScale,
                longPressSpeedHintAlpha = playerInteractionSettings.longPressSpeedHintAlpha,
                longPressSpeedHintCloseEnabled = playerInteractionSettings.longPressSpeedHintCloseEnabled,
                gestureMotionSpec = gestureMotionSpec,
                onDismiss = { longPressSpeedHintDismissed = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp),
            )

            // 6. 顶部渐变遮罩
            NextWatchOverlay(
                player = player,
                mediaKey = "offline:${task.id}",
                eligible = !showControls && playbackFailure == null && !task.isAudioOnly &&
                    gestureMode == VideoGestureMode.None && !isLongPressing && !twoFingerSpeedGestureActive &&
                    resolvePlaybackCompletionRepeatMode(completionBehavior) != Player.REPEAT_MODE_ONE,
                resolve = {
                    episodeQueue.getOrNull(currentEpisodeIndex + 1)?.let { next ->
                        com.android.purebilibili.feature.video.playback.next.NextWatchSuggestion(
                            task.bvid, task.cid, next.bvid, next.cid, next.title, next.cover, "下一集",
                            playlistIndex = currentEpisodeIndex + 1
                        )
                    }
                },
                play = { target ->
                    val next = episodeQueue.getOrNull(target.playlistIndex ?: -1)
                    if (target.sourceBvid == task.bvid && target.sourceCid == task.cid &&
                        next != null && next.bvid == target.bvid && next.cid == target.cid) {
                        switchEpisode(next.id)
                        true
                    } else false
                },
            )

            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                )
            }

            // 7. 底部渐变遮罩
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
            }

            // 8. 顶部控制栏
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconButton(onClick = { if (isFullscreen) toggleFullscreen() else onBack() }) {
                        AppIcon(
                            backIcon,
                            contentDescription = "返回",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(AppSpacingTokens.Small))

                    AppText(
                        text = task.episodeLabel?.takeIf { it.isNotBlank() } ?: task.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 9. 底部控制栏
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = AppSpacingTokens.Medium, vertical = AppSpacingTokens.Small)
                ) {
                    // 进度条
                    OfflineProgressBar(
                        currentPosition = progressState.current,
                        duration = progressState.duration,
                        bufferedPosition = progressState.buffered,
                        onSeek = { seekToPosition(it) }
                    )

                    Spacer(modifier = Modifier.height(AppSpacingTokens.ExtraSmall))

                    if (episodeQueue.size > 1 && currentEpisodeIndex >= 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = AppSpacingTokens.ExtraSmall),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacingTokens.Small)
                        ) {
                            AppSurface(
                                onClick = {
                                    val previousTask = episodeQueue.getOrNull(currentEpisodeIndex - 1) ?: return@AppSurface
                                    switchEpisode(previousTask.id)
                                },
                                enabled = currentEpisodeIndex > 0,
                                color = Color.White.copy(alpha = if (currentEpisodeIndex > 0) 0.15f else 0.08f),
                                shape = AppShapes.container(ContainerLevel.Chip)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIcon(
                                        Icons.Outlined.SkipPrevious,
                                        contentDescription = "上一集",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                                    AppText("上一集", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            AppText(
                                text = "${currentEpisodeIndex + 1}/${episodeQueue.size}",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelMedium
                            )
                            AppSurface(
                                onClick = {
                                    val nextTask = episodeQueue.getOrNull(currentEpisodeIndex + 1) ?: return@AppSurface
                                    switchEpisode(nextTask.id)
                                },
                                enabled = currentEpisodeIndex < episodeQueue.lastIndex,
                                color = Color.White.copy(alpha = if (currentEpisodeIndex < episodeQueue.lastIndex) 0.15f else 0.08f),
                                shape = AppShapes.container(ContainerLevel.Chip)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppText("下一集", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                    Spacer(modifier = Modifier.width(AppSpacingTokens.ExtraSmall))
                                    AppIcon(
                                        Icons.Outlined.SkipNext,
                                        contentDescription = "下一集",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 控制按钮行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val activeControlColors = resolveAdaptivePrimaryAccentColors(MaterialTheme.colorScheme)
                        // 播放/暂停按钮
                        AppIconButton(
                            onClick = { togglePlayerPlaybackFromUserAction(player) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            AppIcon(
                                if (isPlaying) Icons.Outlined.Pause else playIcon,
                                contentDescription = if (isPlaying) "暂停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 时间显示
                        AppText(
                            text = "${FormatUtils.formatDuration((progressState.current / 1000).toInt())} / ${FormatUtils.formatDuration((progressState.duration / 1000).toInt())}",
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.labelMedium
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        if (danmakuAvailable) {
                            AppSurface(
                                onClick = { danmakuEnabled = !danmakuEnabled },
                                color = if (danmakuEnabled) {
                                    activeControlColors.backgroundColor.copy(alpha = 0.9f)
                                } else {
                                    Color.White.copy(alpha = 0.15f)
                                },
                                shape = AppShapes.container(ContainerLevel.Chip),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    AppIcon(
                                        commentIcon,
                                        contentDescription = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                                        tint = if (danmakuEnabled) activeControlColors.contentColor else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // 📺 全屏按钮
                        AppSurface(
                            onClick = { toggleFullscreen() },
                            color = if (!isFullscreen) {
                                activeControlColors.backgroundColor.copy(alpha = 0.9f)
                            } else {
                                Color.White.copy(alpha = 0.15f)
                            },
                            shape = AppShapes.container(ContainerLevel.Chip),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                AppIcon(
                                    if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                    contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                                    tint = if (!isFullscreen) activeControlColors.contentColor else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 10. 中央播放按钮（暂停时显示）
            AnimatedVisibility(
                visible = showControls && !isPlaying && playbackFailure == null,
                modifier = Modifier.align(Alignment.Center),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                AppSurface(
                    onClick = {
                        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                        player.play()
                    },
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AppIcon(
                            playIcon,
                            contentDescription = "播放",
                            tint = Color.White.copy(alpha = 0.95f),
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }
            playbackFailure?.let { failure ->
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f))
                        .pointerInput(failure) { detectTapGestures(onTap = {}) }
                        .safeDrawingPadding()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppText("播放失败", color = Color.White, style = MaterialTheme.typography.titleLarge)
                        AppText(
                            failure.message,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        if (failure.canRetry) {
                            AppButton(onClick = {
                                playbackFailure = null
                                showControls = true
                                retryVersion++
                            }) { AppText("重新播放") }
                        }
                        AppTextButton(onClick = onBack) { AppText("返回", color = Color.White) }
                    }
                }
            }
        }
    }
    // 无二级内容的播放器仅避让物理遮挡铰链；软折痕跨整窗，避免半开下半屏留黑。
    val offlineOccludingHingePresent = appWindowAdaptiveInfo.foldingFeature
        .layoutHinges()
        .any { it.isOccluding }
    if (offlineOccludingHingePresent) {
        com.android.purebilibili.core.ui.adaptive.AppHingePaneLayout(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            primaryContent = offlinePlayerSurface,
        )
    } else {
        offlinePlayerSurface()
    }
}

/**
 * 进度信息
 */
private data class ProgressInfo(
    val current: Long,
    val duration: Long,
    val buffered: Long
)

/**
 * 简化版进度条
 */
@Composable
private fun OfflineProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit
) {
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
    val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration else 0f
    var tempProgress by remember { mutableFloatStateOf(progress) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (!isDragging) {
            tempProgress = progress
        }
    }

    val displayProgress = if (isDragging) tempProgress else progress
    val primaryColor = AppContentColors.accent()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(duration) {
                detectTapGestures { offset ->
                    val targetPosition = resolveOfflineSeekPositionFromTouch(
                        touchX = offset.x,
                        containerWidthPx = size.width.toFloat(),
                        durationMs = duration
                    )
                    tempProgress = if (duration > 0L) {
                        targetPosition.toFloat() / duration.toFloat()
                    } else {
                        0f
                    }
                    onSeek(targetPosition)
                }
            }
            .pointerInput(duration) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        tempProgress = resolveOfflineSeekProgressFromTouch(
                            touchX = offset.x,
                            containerWidthPx = size.width.toFloat()
                        )
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        tempProgress = resolveOfflineSeekProgressFromTouch(
                            touchX = change.position.x,
                            containerWidthPx = size.width.toFloat()
                        )
                    },
                    onDragEnd = {
                        val targetPosition = (tempProgress.coerceIn(0f, 1f) * duration.coerceAtLeast(0L)).toLong()
                        isDragging = false
                        onSeek(targetPosition)
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

        // 缓冲进度
        Box(
            modifier = Modifier
                .fillMaxWidth(bufferedProgress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.5f), AppShapes.container(ContainerLevel.Micro))
        )

        // 当前进度
        Box(
            modifier = Modifier
                .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                .height(3.dp)
                .background(primaryColor, AppShapes.container(ContainerLevel.Micro))
        )

        // 滑块（圆点）
        Box(modifier = Modifier.fillMaxWidth(displayProgress.coerceIn(0f, 1f))) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(if (isDragging) 16.dp else 12.dp)
                    .offset { IntOffset(x = (if (isDragging) 8.dp else 6.dp).roundToPx(), y = 0) }
                    .background(primaryColor, CircleShape)
            )
        }
    }
}

