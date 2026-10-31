// 文件路径: feature/video/FullscreenPlayerOverlay.kt
package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager
import com.android.purebilibili.feature.video.danmaku.configureAsPassiveDanmakuOverlay
import com.android.purebilibili.feature.video.playback.policy.shouldHoldPlaybackTransitionPosition
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.ui.section.isInSeekCancelEscapeZone
import com.android.purebilibili.feature.video.ui.section.resolveHorizontalSeekDeltaMs
import com.android.purebilibili.feature.video.ui.section.rebindPlayerSurfaceIfNeeded
import com.android.purebilibili.feature.video.ui.section.shouldCommitGestureSeek
import com.android.purebilibili.feature.video.ui.section.shouldKeepVideoPlaybackAwake
import com.android.purebilibili.feature.video.ui.section.shouldEngageHorizontalPlayerSeek
import com.android.purebilibili.feature.video.ui.section.shouldTriggerSeekStepHaptic
import com.android.purebilibili.feature.video.usecase.applyPlaybackButtonUserAction
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction
import com.android.purebilibili.danmaku.engine.DanmakuRenderView

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import com.android.purebilibili.feature.video.ui.gesture.PlayerGestureInputSurface
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material3.*
// 🌈 Material Icons Extended - 亮度图标
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.store.FullscreenAspectRatio
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.AppWindowSystemUiController
import com.android.purebilibili.core.ui.findHostActivity
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.blur.BlurSurfaceType
import com.android.purebilibili.core.ui.blur.rememberRecoverableHazeState
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayContent
import com.android.purebilibili.feature.video.ui.gesture.detectPlayerTapGestures
import com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayStyle
import com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelOverlaySpec
import com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelKind
import com.android.purebilibili.feature.video.ui.gesture.rememberGestureLevelOverlayStyle
import com.android.purebilibili.feature.video.ui.section.VideoGestureMode
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.core.util.applyPlayerRequestedOrientation
import com.android.purebilibili.feature.video.ui.gesture.GestureMode
import com.android.purebilibili.feature.video.ui.gesture.GestureIndicator
import com.android.purebilibili.feature.video.ui.gesture.rememberPlayerGestureState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.feature.video.ui.components.AnimatedGesturePercentText
import com.android.purebilibili.feature.video.ui.components.DanmakuSettingsPanel
import com.android.purebilibili.feature.video.ui.components.NativeDanmakuToggleButton
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.PlaybackSpeed
import com.android.purebilibili.feature.video.ui.components.SpeedSelectionMenuPlacement
import com.android.purebilibili.feature.video.ui.components.resolveSafeVideoAspectRatio
import com.android.purebilibili.feature.video.ui.components.schedulePlayerViewViewportRefresh
import com.android.purebilibili.feature.video.ui.components.cancelPlayerViewViewportRefresh
import com.android.purebilibili.feature.video.ui.components.toFullscreenAspectRatio
import com.android.purebilibili.feature.video.ui.components.toVideoAspectRatio
import com.android.purebilibili.core.ui.common.copyOnLongPress
import androidx.lifecycle.compose.currentStateAsState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.chrisbanes.haze.HazeState
import com.android.purebilibili.core.ui.blur.hazeSourceCompat
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

private const val VISIBLE_TOP_CONTROLS_GESTURE_EXCLUSION_HEIGHT_DP = 96
private const val VISIBLE_BOTTOM_CONTROLS_GESTURE_EXCLUSION_HEIGHT_DP = 90

// Keep for backward compatibility, maps to new GestureMode
enum class FullscreenGestureMode { None, Brightness, Volume, Seek, SwipeToExit }

/** 中间区域竖直滑动退出全屏的触发阈值（占屏高比例）。 */
private const val FULLSCREEN_SWIPE_EXIT_THRESHOLD_FRACTION = 0.12f

internal fun resolveFullscreenVisibleBottomControlsGestureExclusionHeightDp(): Int {
    return VISIBLE_BOTTOM_CONTROLS_GESTURE_EXCLUSION_HEIGHT_DP
}

internal fun Key.toFullscreenShortcutKey(): FullscreenShortcutKey = when (this) {
    Key.Spacebar, Key.K -> FullscreenShortcutKey.Space
    Key.DirectionLeft -> FullscreenShortcutKey.Left
    Key.DirectionRight -> FullscreenShortcutKey.Right
    Key.Escape -> FullscreenShortcutKey.Escape
    Key.F, Key.Enter, Key.NumPadEnter -> FullscreenShortcutKey.KeyF
    Key.M -> FullscreenShortcutKey.KeyM
    Key.D -> FullscreenShortcutKey.KeyD
    else -> FullscreenShortcutKey.Other
}

internal fun resolveFullscreenPendingGestureSeekPosition(
    currentPositionMs: Long,
    pendingSeekPositionMs: Long?
): Long? {
    val targetPositionMs = pendingSeekPositionMs ?: return null
    return if (
        shouldHoldPlaybackTransitionPosition(
            playerPositionMs = currentPositionMs,
            transitionPositionMs = targetPositionMs
        )
    ) {
        targetPositionMs
    } else {
        null
    }
}

internal fun shouldRebindFullscreenSurfaceOnResume(
    hasPlayerView: Boolean,
    hasPlayer: Boolean
): Boolean {
    return hasPlayerView && hasPlayer
}

internal fun resolveFullscreenOverlayExitRequestedOrientation(
    originalRequestedOrientation: Int
): Int {
    return originalRequestedOrientation
}

internal fun shouldStartFullscreenDragGesture(
    gesturesEnabled: Boolean,
    showControls: Boolean,
    startY: Float,
    screenHeight: Float,
    statusBarExclusionZonePx: Float,
    visibleTopControlsHeightPx: Float,
    visibleBottomControlsHeightPx: Float
): Boolean {
    if (!gesturesEnabled || screenHeight <= 0f) return false
    if (startY < statusBarExclusionZonePx) return false
    if (!showControls) return true

    val topControlsBottom = visibleTopControlsHeightPx.coerceAtLeast(statusBarExclusionZonePx)
    val bottomControlsTop = (screenHeight - visibleBottomControlsHeightPx).coerceAtLeast(0f)
    return startY >= topControlsBottom && startY <= bottomControlsTop
}

/**
 *  全屏播放器覆盖层
 *
 * 从小窗展开时直接显示全屏播放器
 * 包含：亮度调节、音量调节、进度滑动等完整功能
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FullscreenPlayerOverlay(
    miniPlayerManager: MiniPlayerManager,
    onDismiss: () -> Unit,
    onNavigateToDetail: () -> Unit
) {
    val autoHide = rememberPlayerControlsAutoHide(4_000L)
    val context = LocalContext.current
    val density = LocalDensity.current
    val player = miniPlayerManager.player
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val hostLifecycleStarted = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    //  [新增] 弹幕设置面板状态
    var showDanmakuSettings by remember { mutableStateOf(false) }

    //  播放速度状态
    var playbackSpeed by remember(player) { mutableFloatStateOf(player?.playbackParameters?.speed ?: 1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    //  视频比例状态
    val aspectRatioSession = com.android.purebilibili.feature.video.ui.components.rememberPlayerAspectRatioSession(player, miniPlayerManager.currentBvid.orEmpty())
    val sessionAspectRatio by aspectRatioSession.selection.collectAsStateWithLifecycle()
    val fixedFullscreenAspectRatio by SettingsManager
        .getFullscreenAspectRatio(context)
        .collectAsStateWithLifecycle(initialValue = FullscreenAspectRatio.FIT
        )
    var playerVideoSize by remember(player) { mutableStateOf(player?.videoSize ?: androidx.media3.common.VideoSize.UNKNOWN) }
    val isVerticalContent = playerVideoSize.width > 0 && playerVideoSize.height > playerVideoSize.width
    var aspectRatio by remember(aspectRatioSession) {
        mutableStateOf(
            resolveSafeVideoAspectRatio(
                preferred = sessionAspectRatio ?: fixedFullscreenAspectRatio.toVideoAspectRatio(),
                isVerticalVideo = isVerticalContent
            )
        )
    }
    var showRatioMenu by remember { mutableStateOf(false) }

    //  画质选择菜单状态
    var showQualityMenu by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val rootFocusRequester = remember { FocusRequester() }
    val inputDevicePolicy = com.android.purebilibili.core.ui.adaptive.resolveInputDevicePolicy(
        com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo.current,
    )
    val scope = rememberCoroutineScope()
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var keepFullscreenPlaybackAwake by remember(player) {
        mutableStateOf(
            player?.let {
                shouldKeepVideoPlaybackAwake(
                    playWhenReady = it.playWhenReady,
                    isPlaying = it.isPlaying,
                    playbackState = it.playbackState
                )
            } ?: false
        )
    }
    //  共享弹幕管理器（横竖屏切换保持状态，同时可用于手势 seek 同步）
    val danmakuManager = rememberDanmakuManager(miniPlayerManager.currentBvid ?: player ?: miniPlayerManager)

    val danmakuScope = com.android.purebilibili.core.store.DanmakuSettingsScope.LANDSCAPE
    val danmakuSettings by SettingsManager
        .getDanmakuSettings(context, danmakuScope)
        .collectAsStateWithLifecycle(initialValue = DanmakuSettings(),
            context = kotlin.coroutines.EmptyCoroutineContext
        )
    val danmakuEnabled = danmakuSettings.enabled

    DisposableEffect(player) {
        val exoPlayer = player
        if (exoPlayer == null) {
            keepFullscreenPlaybackAwake = false
            onDispose { }
        } else {
            fun updateAwakeState() {
                keepFullscreenPlaybackAwake = shouldKeepVideoPlaybackAwake(
                    playWhenReady = exoPlayer.playWhenReady,
                    isPlaying = exoPlayer.isPlaying,
                    playbackState = exoPlayer.playbackState
                )
            }
            updateAwakeState()
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateAwakeState()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateAwakeState()
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    updateAwakeState()
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
            }
        }
    }

    // 手势状态
    var gestureMode by remember { mutableStateOf(FullscreenGestureMode.None) }
    var gestureValue by remember { mutableFloatStateOf(0f) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragVerticalDelta by remember { mutableFloatStateOf(0f) }
    var seekPreviewPosition by remember { mutableLongStateOf(0L) }
    var gestureSeekStartPosition by remember { mutableLongStateOf(0L) }
    var lastSeekHapticTargetMs by remember { mutableLongStateOf(0L) }
    // 拖动 seek 时手指进入顶部角落逃生口时置真，松手即取消本次 seek
    var seekCancelPending by remember { mutableStateOf(false) }
    var swipeExitAccumulatedY by remember { mutableFloatStateOf(0f) }
    val haptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.PLAYER_GESTURES)
    val seekSceneHaptic = com.android.purebilibili.core.ui.rememberAppHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.PLAYER_SEEK)
    val playerInteractionSettings by SettingsManager
        .getPlayerInteractionSettings(context)
        .collectAsStateWithLifecycle(
            initialValue = com.android.purebilibili.core.store.PlayerInteractionSettings()
        )
    val doubleTapSeekEnabled = playerInteractionSettings.doubleTapSeekEnabled
    val seekForwardSeconds = playerInteractionSettings.seekForwardSeconds
    val seekBackwardSeconds = playerInteractionSettings.seekBackwardSeconds

    // 亮度状态
    var currentBrightness by remember {
        mutableFloatStateOf(
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (e: Exception) { 0.5f }
        )
    }

    // 播放器状态 — 用当前 player 位置 seed，避免全屏重建时先显示 00:00
    var isPlaying by remember { mutableStateOf(player?.isPlaying ?: false) }
    var currentProgress by remember {
        mutableFloatStateOf(
            run {
                val p = player ?: return@run 0f
                val d = p.duration
                if (d > 0L) (p.currentPosition.toFloat() / d.toFloat()).coerceIn(0f, 1f) else 0f
            }
        )
    }
    var currentPosition by remember {
        mutableLongStateOf(player?.currentPosition?.coerceAtLeast(0L) ?: 0L)
    }
    var duration by remember {
        mutableLongStateOf(
            player?.duration?.takeIf { it > 0L } ?: 0L
        )
    }
    var pendingGestureSeekPositionMs by remember { mutableStateOf<Long?>(null) }
    val currentClockText by produceState(initialValue = formatCurrentClock(), hostLifecycleStarted) {
        if (!hostLifecycleStarted) {
            value = formatCurrentClock()
            return@produceState
        }
        while (true) {
            value = formatCurrentClock()
            val now = System.currentTimeMillis()
            val nextMinuteDelay = (60_000L - (now % 60_000L)).coerceAtLeast(1_000L)
            delay(nextMinuteDelay)
        }
    }

    DisposableEffect(player) {
        val exoPlayer = player
        if (exoPlayer == null) {
            onDispose { }
        } else {
            playbackSpeed = exoPlayer.playbackParameters.speed
            val speedListener = object : Player.Listener {
                override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                    playbackSpeed = playbackParameters.speed
                }
            }
            exoPlayer.addListener(speedListener)
            onDispose {
                exoPlayer.removeListener(speedListener)
            }
        }
    }

    DisposableEffect(player) {
        val exoPlayer = player
        if (exoPlayer == null) {
            playerVideoSize = androidx.media3.common.VideoSize.UNKNOWN
            onDispose { }
        } else {
            playerVideoSize = exoPlayer.videoSize
            val listener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    playerVideoSize = videoSize
                }
            }
            exoPlayer.addListener(listener)
            onDispose { exoPlayer.removeListener(listener) }
        }
    }

    LaunchedEffect(fixedFullscreenAspectRatio, isVerticalContent, sessionAspectRatio, aspectRatioSession) {
        aspectRatio = resolveSafeVideoAspectRatio(
            preferred = sessionAspectRatio ?: fixedFullscreenAspectRatio.toVideoAspectRatio(),
            isVerticalVideo = isVerticalContent
        )
    }

    // 进入全屏时设置横屏和沉浸式
    DisposableEffect(lifecycleOwner, player, playerViewRef) {
        val activity = context.findHostActivity() ?: return@DisposableEffect onDispose {}
        val window = activity.window
        val originalOrientation = activity.requestedOrientation
        val originalSystemUi = AppWindowSystemUiController.capture(window).let { snapshot ->
            //  [修复] 播放器实例重建会重启本 effect，此时系统栏已被本覆盖层隐藏；
            //  快照若照实记录会把"隐藏"当原始状态，退出全屏后系统栏将无法恢复。
            if (snapshot.systemBarsVisible) snapshot else snapshot.copy(systemBarsVisible = true)
        }
        val desktopFullscreenRequested =
            AppWindowSystemUiController.requestDesktopFullscreen(activity, enter = true)

        // 设置横屏
        activity.applyPlayerRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)

        //  首次进入时应用沉浸式
        AppWindowSystemUiController.enterImmersive(window)

        //  [关键修复] 生命周期观察器：返回前台时重新应用沉浸式模式
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                AppWindowSystemUiController.enterImmersive(window)
                val view = playerViewRef
                val exoPlayer = player
                if (shouldRebindFullscreenSurfaceOnResume(
                        hasPlayerView = view != null,
                        hasPlayer = exoPlayer != null
                    )
                ) {
                    rebindPlayerSurfaceIfNeeded(
                        playerView = view!!,
                        player = exoPlayer!!
                    )
                    Logger.d("FullscreenPlayer", "🎬 ON_RESUME fullscreen surface rebind applied")
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            //  移除生命周期观察器
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)

            activity.applyPlayerRequestedOrientation(
                resolveFullscreenOverlayExitRequestedOrientation(
                    originalRequestedOrientation = originalOrientation
                )
            )

            AppWindowSystemUiController.restore(window, originalSystemUi)
            if (desktopFullscreenRequested) {
                AppWindowSystemUiController.requestDesktopFullscreen(activity, enter = false)
            }

            // 取消屏幕常亮
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(context, keepFullscreenPlaybackAwake) {
        val hostWindow = context.findHostActivity()?.window
        if (keepFullscreenPlaybackAwake) {
            hostWindow?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            hostWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (keepFullscreenPlaybackAwake) {
                hostWindow?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // 监听播放器状态
    LaunchedEffect(player, showControls, gestureMode, hostLifecycleStarted) {
        if (!shouldPollFullscreenPlayerProgress(
                playerExists = player != null,
                hostLifecycleStarted = hostLifecycleStarted
            )
        ) {
            return@LaunchedEffect
        }
        while (isActive) {
            player?.let {
                isPlaying = it.isPlaying
                duration = resolveSeekableDurationMs(
                    playbackDurationMs = it.duration,
                    fallbackDurationMs = miniPlayerManager.duration
                )
                currentPosition = it.currentPosition
                pendingGestureSeekPositionMs = resolveFullscreenPendingGestureSeekPosition(
                    currentPositionMs = currentPosition,
                    pendingSeekPositionMs = pendingGestureSeekPositionMs
                )
                if (gestureMode != FullscreenGestureMode.Seek && duration > 0L) {
                    currentProgress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                }
            }
            val pollInterval = resolveFullscreenPlayerPollingIntervalMs(
                isPlaying = isPlaying,
                showControls = showControls,
                isSeekingGesture = gestureMode == FullscreenGestureMode.Seek
            )
            delay(pollInterval)
        }
    }

    // 自动隐藏控制按钮
    LaunchedEffect(showControls, lastInteractionTime, gestureMode, isPlaying, autoHide.timeoutMillis) {
        val timeout = autoHide.timeoutMillis ?: return@LaunchedEffect
        if (
            shouldAutoHideFullscreenControls(
                showControls = showControls,
                gestureMode = gestureMode,
                isPlaying = isPlaying
            )
        ) {
            delay(timeout)
            if (autoHide.canHide() && System.currentTimeMillis() - lastInteractionTime >= timeout) {
                showControls = false
            }
        }
    }

    // [问题6修复] 弹幕设置面板打开时禁用手势
    val gesturesEnabled = !showDanmakuSettings && !showSpeedMenu && !showRatioMenu &&
        !showQualityMenu && !showContextMenu

    val closeTopLayerOrExit: () -> Unit = {
        when {
            showContextMenu -> showContextMenu = false
            showQualityMenu -> showQualityMenu = false
            showRatioMenu -> showRatioMenu = false
            showSpeedMenu -> showSpeedMenu = false
            showDanmakuSettings -> showDanmakuSettings = false
            else -> onNavigateToDetail()
        }
    }
    val backEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val backProgress =
        (backEventState.transitionState as? NavigationEventTransitionState.InProgress)
            ?.latestEvent
            ?.progress
            ?: 0f
    NavigationBackHandler(
        state = backEventState,
        isBackEnabled = true,
        onBackCancelled = {
            lastInteractionTime = System.currentTimeMillis()
        },
        onBackCompleted = closeTopLayerOrExit,
    )
    LaunchedEffect(rootFocusRequester, inputDevicePolicy.enableKeyboardNavigation) {
        if (inputDevicePolicy.enableKeyboardNavigation) {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // [问题8修复] 状态栏排除区域高度（像素）
    val statusBarExclusionZonePx = with(density) { 40.dp.toPx() }
    val visibleTopControlsHeightPx = with(density) {
        VISIBLE_TOP_CONTROLS_GESTURE_EXCLUSION_HEIGHT_DP.dp.toPx()
    }
    val visibleBottomControlsHeightPx = with(density) {
        resolveFullscreenVisibleBottomControlsGestureExclusionHeightDp().dp.toPx()
    }
    val overlayHazeState = rememberRecoverableHazeState()
    val displayedProgressState = remember(
        currentPosition,
        duration,
        seekPreviewPosition,
        pendingGestureSeekPositionMs,
        gestureMode,
        player?.bufferedPosition
    ) {
        resolveDisplayedPlayerProgress(
            progress = PlayerProgress(
                current = currentPosition,
                duration = duration,
                buffered = player?.bufferedPosition ?: 0L
            ),
            previewPositionMs = seekPreviewPosition,
            previewActive = gestureMode == FullscreenGestureMode.Seek,
            playbackTransitionPositionMs = pendingGestureSeekPositionMs
        )
    }

    Box(
        modifier = Modifier
            .then(autoHide.focusModifier)
            .fillMaxSize()
            .graphicsLayer {
                val progress = backProgress.coerceIn(0f, 1f)
                scaleX = 1f - progress * 0.035f
                scaleY = 1f - progress * 0.035f
                alpha = 1f - progress * 0.12f
            }
            .background(Color.Black)
            .hazeSourceCompat(overlayHazeState)
            .focusRequester(rootFocusRequester)
            .onPreviewKeyEvent { event ->
                val action = resolveFullscreenKeyboardAction(
                    key = event.key.toFullscreenShortcutKey(),
                    isKeyDown = event.type == KeyEventType.KeyDown,
                    hasCommandModifier = event.isCtrlPressed || event.isAltPressed ||
                        event.isMetaPressed || event.isShiftPressed,
                    shortcutsEnabled = inputDevicePolicy.enableKeyboardNavigation &&
                        (gesturesEnabled || event.key == Key.Escape),
                )
                when (action) {
                    FullscreenKeyboardAction.PlayPause -> {
                        player?.let(::togglePlayerPlaybackFromUserAction)
                        showControls = true
                        lastInteractionTime = System.currentTimeMillis()
                        true
                    }
                    FullscreenKeyboardAction.SeekBackward,
                    FullscreenKeyboardAction.SeekForward -> {
                        val targetPlayer = player ?: return@onPreviewKeyEvent false
                        val deltaMs = if (action == FullscreenKeyboardAction.SeekBackward) {
                            -seekBackwardSeconds * 1_000L
                        } else {
                            seekForwardSeconds * 1_000L
                        }
                        val upperBound = targetPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                        val targetPosition = (targetPlayer.currentPosition + deltaMs)
                            .coerceIn(0L, upperBound)
                        pendingGestureSeekPositionMs = targetPosition
                        seekPlayerFromUserAction(targetPlayer, targetPosition)
                        danmakuManager.seekTo(targetPosition)
                        showControls = true
                        lastInteractionTime = System.currentTimeMillis()
                        true
                    }
                    FullscreenKeyboardAction.CloseTopLayer,
                    FullscreenKeyboardAction.ToggleFullscreen -> {
                        closeTopLayerOrExit()
                        true
                    }
                    FullscreenKeyboardAction.ToggleMute -> {
                        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        if (currentVolume > 0) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                        } else {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume / 3, AudioManager.FLAG_SHOW_UI)
                        }
                        true
                    }
                    FullscreenKeyboardAction.ToggleDanmaku -> {
                        val newValue = !danmakuEnabled
                        danmakuManager.isEnabled = newValue
                        scope.launch {
                            SettingsManager.setDanmakuEnabled(context, newValue, danmakuScope)
                        }
                        true
                    }
                    FullscreenKeyboardAction.ToggleLock -> false
                    FullscreenKeyboardAction.None -> false
                }
            }
            .focusable(enabled = inputDevicePolicy.enableKeyboardNavigation)
            .semantics {
                contentDescription = "全屏视频播放器"
                stateDescription = if (isPlaying) "正在播放" else "已暂停"
            }
            .pointerInput(density) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            val position = event.changes.firstOrNull()?.position ?: continue
                            contextMenuOffset = with(density) {
                                DpOffset(position.x.toDp(), position.y.toDp())
                            }
                            showContextMenu = true
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
            }
            .pointerInput(
                gesturesEnabled,
                doubleTapSeekEnabled,
                seekForwardSeconds,
                seekBackwardSeconds
            ) {
                if (!gesturesEnabled) return@pointerInput

                val screenWidth = size.width.toFloat()

                detectPlayerTapGestures(
                    doubleTapSlopPx = android.view.ViewConfiguration.get(context).scaledDoubleTapSlop.toFloat(),
                    onTap = {
                        showControls = !showControls
                        if (showControls) lastInteractionTime = System.currentTimeMillis()
                    },
                    onDoubleTap = { offset ->
                        // 分区双击策略可由设置和当前播放意图控制。
                        val relativeX = if (screenWidth > 0f) offset.x / screenWidth else 0.5f
                        player?.let { p ->
                            when (
                                resolveFullscreenDoubleTapAction(
                                    relativeX = relativeX,
                                    doubleTapSeekEnabled = doubleTapSeekEnabled,
                                    playWhenReady = p.playWhenReady,
                                    isPlaying = p.isPlaying,
                                    playbackState = p.playbackState
                                )
                            ) {
                                FullscreenDoubleTapAction.SeekBackward -> {
                                    val seekMs = seekBackwardSeconds * 1000L
                                    val newPos = (p.currentPosition - seekMs).coerceAtLeast(0L)
                                    pendingGestureSeekPositionMs = newPos
                                    seekPlayerFromUserAction(p, newPos)
                                    danmakuManager.seekTo(newPos)
                                }
                                FullscreenDoubleTapAction.SeekForward -> {
                                    val seekMs = seekForwardSeconds * 1000L
                                    val durationLimit = p.duration.coerceAtLeast(0L)
                                    val target = p.currentPosition + seekMs
                                    val newPos = if (durationLimit > 0L) {
                                        target.coerceAtMost(durationLimit)
                                    } else {
                                        target
                                    }
                                    pendingGestureSeekPositionMs = newPos
                                    seekPlayerFromUserAction(p, newPos)
                                    danmakuManager.seekTo(newPos)
                                }
                                FullscreenDoubleTapAction.TogglePlayPause -> {
                                    togglePlayerPlaybackFromUserAction(p)
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(player, gesturesEnabled, playerInteractionSettings, showControls) {
                if (gestureMode == FullscreenGestureMode.Seek) {
                    gestureMode = FullscreenGestureMode.None
                    seekCancelPending = false
                }
                if (!gesturesEnabled) {
                    return@pointerInput
                }

                val screenWidth = size.width.toFloat()
                val screenHeight = size.height.toFloat()
                var dragGestureActive = false

                detectDragGestures(
                    onDragStart = { offset ->
                        dragGestureActive = shouldStartFullscreenDragGesture(
                            gesturesEnabled = gesturesEnabled,
                            showControls = showControls,
                            startY = offset.y,
                            screenHeight = screenHeight,
                            statusBarExclusionZonePx = statusBarExclusionZonePx,
                            visibleTopControlsHeightPx = visibleTopControlsHeightPx,
                            visibleBottomControlsHeightPx = visibleBottomControlsHeightPx
                        )
                        if (!dragGestureActive) {
                            gestureMode = FullscreenGestureMode.None
                            return@detectDragGestures
                        }

                        showControls = true
                        lastInteractionTime = System.currentTimeMillis()
                        dragDelta = 0f
                        dragVerticalDelta = 0f
                        swipeExitAccumulatedY = 0f
                        seekCancelPending = false

                        // 根据起始位置决定手势类型
                        gestureMode = when {
                            offset.x < screenWidth * 0.3f -> {
                                gestureValue = currentBrightness
                                FullscreenGestureMode.Brightness
                            }
                            offset.x > screenWidth * 0.7f -> {
                                gestureValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                                FullscreenGestureMode.Volume
                            }
                            else -> {
                                seekPreviewPosition = currentPosition
                                gestureSeekStartPosition = currentPosition
                                lastSeekHapticTargetMs = currentPosition
                                FullscreenGestureMode.None
                            }
                        }
                    },
                    onDragEnd = {
                        if (
                            gestureMode == FullscreenGestureMode.SwipeToExit &&
                            swipeExitAccumulatedY > screenHeight * FULLSCREEN_SWIPE_EXIT_THRESHOLD_FRACTION
                        ) {
                            haptic.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                            )
                            onDismiss()
                        } else if (
                            dragGestureActive &&
                            gestureMode == FullscreenGestureMode.Seek &&
                            !seekCancelPending &&
                            shouldCommitGestureSeek(
                                currentPositionMs = gestureSeekStartPosition,
                                targetPositionMs = seekPreviewPosition
                            )
                        ) {
                            player?.let {
                                pendingGestureSeekPositionMs = seekPreviewPosition
                                seekPlayerFromUserAction(it, seekPreviewPosition)
                                danmakuManager.seekTo(seekPreviewPosition)
                            }
                        }
                        dragGestureActive = false
                        gestureMode = FullscreenGestureMode.None
                        seekCancelPending = false
                    },
                    onDragCancel = {
                        dragGestureActive = false
                        gestureMode = FullscreenGestureMode.None
                    },
                    onDrag = { change, dragAmount ->
                        if (!dragGestureActive) return@detectDragGestures
                        change.consume()
                        if (gestureMode == FullscreenGestureMode.None) {
                            dragDelta += dragAmount.x
                            dragVerticalDelta += dragAmount.y
                            if (shouldEngageHorizontalPlayerSeek(dragDelta, dragVerticalDelta)) {
                                gestureMode = FullscreenGestureMode.Seek
                                seekSceneHaptic.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                )
                            } else if (
                                abs(dragVerticalDelta) >= 1f &&
                                abs(dragVerticalDelta) > abs(dragDelta) * 1.2f
                            ) {
                                // 中央区域竖直滑动：下滑退出全屏（上滑不响应，避免与系统手势冲突）
                                gestureMode = FullscreenGestureMode.SwipeToExit
                                swipeExitAccumulatedY = dragVerticalDelta
                            }
                        } else if (gestureMode == FullscreenGestureMode.Seek) {
                            dragDelta += dragAmount.x
                        } else if (gestureMode == FullscreenGestureMode.SwipeToExit) {
                            swipeExitAccumulatedY += dragAmount.y
                        }
                        when (gestureMode) {
                            FullscreenGestureMode.Brightness -> {
                                gestureValue = (gestureValue - dragAmount.y / screenHeight).coerceIn(0f, 1f)
                                currentBrightness = gestureValue
                                context.findHostActivity()?.window?.let { window ->
                                    val params = window.attributes
                                    params.screenBrightness = gestureValue
                                    window.attributes = params
                                }
                            }
                            FullscreenGestureMode.Volume -> {
                                gestureValue = (gestureValue - dragAmount.y / screenHeight).coerceIn(0f, 1f)
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (gestureValue * maxVolume).toInt(),
                                    0
                                )
                            }
                            FullscreenGestureMode.Seek -> {
                                val seekDelta = resolveHorizontalSeekDeltaMs(
                                    isFullscreen = true,
                                    fullscreenSwipeSeekEnabled = playerInteractionSettings.fullscreenSwipeSeekEnabled,
                                    totalDragDistanceX = dragDelta,
                                    containerWidthPx = screenWidth,
                                    fullscreenSwipeSeekSeconds = playerInteractionSettings.fullscreenSwipeSeekSeconds,
                                    inlineSwipeSeekSeconds = playerInteractionSettings.inlineSwipeSeekSeconds,
                                    gestureSensitivity = playerInteractionSettings.gestureSensitivity,
                                    durationMs = duration,
                                    useRelativeDuration = playerInteractionSettings.swipeSeekUseRelativeDuration,
                                    relativeSeekPercent = playerInteractionSettings.swipeSeekRelativePercent
                                )
                                if (seekDelta != null) {
                                    seekPreviewPosition = (gestureSeekStartPosition + seekDelta).coerceIn(0L, duration)
                                    seekCancelPending = isInSeekCancelEscapeZone(
                                        positionX = change.position.x,
                                        positionY = change.position.y,
                                        containerWidthPx = screenWidth,
                                        containerHeightPx = screenHeight
                                    )
                                    currentProgress = if (duration > 0L) {
                                        (seekPreviewPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                    if (
                                        shouldTriggerSeekStepHaptic(
                                            previousTargetMs = lastSeekHapticTargetMs,
                                            currentTargetMs = seekPreviewPosition
                                        )
                                    ) {
                                        seekSceneHaptic.performHapticFeedback(
                                            androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                        )
                                        lastSeekHapticTargetMs = seekPreviewPosition
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                )
            }
    ) {
        AppDropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = contextMenuOffset,
        ) {
            AppDropdownMenuItem(
                text = { AppText(if (isPlaying) "暂停" else "播放") },
                onClick = {
                    showContextMenu = false
                    player?.let(::togglePlayerPlaybackFromUserAction)
                },
            )
            AppDropdownMenuItem(
                text = { AppText("快退 ${seekBackwardSeconds} 秒") },
                onClick = {
                    showContextMenu = false
                    player?.let { targetPlayer ->
                        val target = (targetPlayer.currentPosition - seekBackwardSeconds * 1_000L)
                            .coerceAtLeast(0L)
                        seekPlayerFromUserAction(targetPlayer, target)
                        danmakuManager.seekTo(target)
                    }
                },
            )
            AppDropdownMenuItem(
                text = { AppText("快进 ${seekForwardSeconds} 秒") },
                onClick = {
                    showContextMenu = false
                    player?.let { targetPlayer ->
                        val durationLimit = targetPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                        val target = (targetPlayer.currentPosition + seekForwardSeconds * 1_000L)
                            .coerceAtMost(durationLimit)
                        seekPlayerFromUserAction(targetPlayer, target)
                        danmakuManager.seekTo(target)
                    }
                },
            )
            AppDropdownMenuItem(
                text = { AppText("退出全屏") },
                onClick = {
                    showContextMenu = false
                    onNavigateToDetail()
                },
            )
        }
        val danmakuOpacity = danmakuSettings.opacity
        val danmakuFontScale = danmakuSettings.fontScale
        val danmakuSpeed = danmakuSettings.speed
        val danmakuDisplayArea = danmakuSettings.displayArea
        val danmakuMergeDuplicates = danmakuSettings.mergeDuplicates
        val danmakuDuplicateMergeWindowMs = danmakuSettings.duplicateMergeWindowMs
        val danmakuDuplicateMergeCountThreshold = danmakuSettings.duplicateMergeCountThreshold
        val danmakuAllowScroll = danmakuSettings.allowScroll
        val danmakuAllowTop = danmakuSettings.allowTop
        val danmakuAllowBottom = danmakuSettings.allowBottom
        val danmakuAllowColorful = danmakuSettings.allowColorful
        val danmakuAllowSpecial = danmakuSettings.allowSpecial
        val danmakuSmartOcclusion = danmakuSettings.smartOcclusion
        val danmakuBlockRulesRaw = danmakuSettings.blockRulesRaw
        val danmakuBlockRules = danmakuSettings.blockRules
        //  获取当前 cid 并加载弹幕
        val currentCid = miniPlayerManager.currentCid
        LaunchedEffect(danmakuManager, currentCid, danmakuEnabled, player) {
            if (currentCid > 0 && danmakuEnabled) {
                danmakuManager.updateSettings(settings = danmakuSettings)
                danmakuManager.isEnabled = true
                // UP 主弹幕徽章：全屏路径经小窗管理器，owner.mid 从其缓存的详情状态取
                danmakuManager.bindUpOwnerMid(
                    miniPlayerManager.cachedUiState?.info?.owner?.mid ?: 0L
                )

                // 等待播放器 duration 可用后再加载弹幕，启用 Protobuf API
                var durationMs = player?.duration ?: 0L
                var retries = 0
                while (durationMs <= 0 && retries < 50) {
                    delay(100)
                    durationMs = player?.duration ?: 0L
                    retries++
                }

                danmakuManager.loadDanmaku(
                    currentCid,
                    miniPlayerManager.currentAid,
                    durationMs,
                    miniPlayerManager.currentBvid.orEmpty()
                )
            } else if (!danmakuEnabled) {
                danmakuManager.isEnabled = false
            }
        }

        //  弹幕设置变化时实时应用
        LaunchedEffect(danmakuManager, danmakuSettings) {
            danmakuManager.updateSettings(settings = danmakuSettings)
        }

        // 播放器 owner 成对绑定；渲染 View 由 AndroidView.onRelease 独立解绑。
        DisposableEffect(danmakuManager, player) {
            player?.let { danmakuManager.attachPlayer(it) }
            onDispose {
                player?.let(danmakuManager::detachPlayer)
            }
        }

        // 视频播放器
        player?.let { exoPlayer ->
            val useTextureSurface = com.android.purebilibili.feature.video.ui.components.rememberCompatibleVideoTextureSurface(
                exoPlayer, miniPlayerManager.cachedUiState?.currentQuality ?: 0,
            )
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val viewportLayout = remember(maxWidth, maxHeight, aspectRatio, playerVideoSize) {
                    with(density) {
                        com.android.purebilibili.feature.video.ui.components.resolveVideoViewportLayout(
                            containerWidth = maxWidth.roundToPx(),
                            containerHeight = maxHeight.roundToPx(),
                            aspectRatio = aspectRatio,
                            videoWidth = playerVideoSize.width,
                            videoHeight = playerVideoSize.height,
                        )
                    }
                }
                val viewportModifier = with(density) {
                    Modifier
                        .requiredSize(
                            width = viewportLayout.width.toDp(),
                            height = viewportLayout.height.toDp()
                        )
                }

                LaunchedEffect(playerViewRef, exoPlayer, viewportLayout, aspectRatio, playerVideoSize) {
                    playerViewRef?.let { view ->
                        schedulePlayerViewViewportRefresh(
                            playerView = view,
                            resizeMode = aspectRatio.playerResizeMode,
                            expectedWidth = viewportLayout.width,
                            expectedHeight = viewportLayout.height,
                            videoWidth = playerVideoSize.width,
                            videoHeight = playerVideoSize.height,
                        )
                    }
                }

                key(exoPlayer, useTextureSurface) {
                AndroidView(
                    factory = { ctx ->
                        com.android.purebilibili.feature.video.ui.components.createVideoPlayerView(ctx, useTextureSurface).apply {
                            this.player = exoPlayer
                            useController = false
                            keepScreenOn = keepFullscreenPlaybackAwake
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            resizeMode = aspectRatio.playerResizeMode
                            playerViewRef = this
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                        playerView.keepScreenOn = keepFullscreenPlaybackAwake
                        playerView.resizeMode = aspectRatio.playerResizeMode
                        playerViewRef = playerView
                    },
                    onRelease = { view ->
                        cancelPlayerViewViewportRefresh(view)
                        if (playerViewRef === view) playerViewRef = null
                        view.player = null
                    },
                    modifier = viewportModifier
                )
                }

                if (danmakuEnabled) {
                    key(danmakuManager) {
                        AndroidView(
                            factory = { ctx ->
                                DanmakuRenderView(ctx).apply {
                                    danmakuManager.isFullscreenSurface = true
                                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    configureAsPassiveDanmakuOverlay()
                                    danmakuManager.attachView(this)
                                    com.android.purebilibili.core.util.Logger.d("FullscreenDanmaku", " DanmakuView (RenderEngine) created for fullscreen")
                                }
                            },
                            update = { view ->
                                danmakuManager.isFullscreenSurface = true
                                if (view.width > 0 && view.height > 0) {
                                    val sizeTag = "${view.width}x${view.height}"
                                    if (view.tag != sizeTag) {
                                        view.tag = sizeTag
                                        danmakuManager.attachView(view)
                                        com.android.purebilibili.core.util.Logger.d("FullscreenDanmaku", " DanmakuView update: size=${view.width}x${view.height}")
                                    }
                                }
                            },
                            onRelease = { view -> danmakuManager.detachView(view) },
                            modifier = viewportModifier
                        )
                    }
                }
            }
        }

        // Same stable hit surface as inline/fullscreen detail playback; controls stay above it.
        PlayerGestureInputSurface(modifier = Modifier.matchParentSize())

        // 手势指示器（SwipeToExit 无需指示器）
        if (gestureMode != FullscreenGestureMode.None &&
            gestureMode != FullscreenGestureMode.SwipeToExit
        ) {
            GestureIndicator(
                mode = gestureMode,
                value = when (gestureMode) {
                    FullscreenGestureMode.Brightness -> currentBrightness
                    FullscreenGestureMode.Volume -> gestureValue
                    FullscreenGestureMode.Seek -> currentProgress
                    else -> 0f
                },
                seekTime = if (gestureMode == FullscreenGestureMode.Seek) seekPreviewPosition else null,
                duration = duration,
                hazeState = overlayHazeState,
                // Level overlays (esp. MIUIX edge rails) need full-size host for side alignment.
                modifier = if (gestureMode == FullscreenGestureMode.Seek) {
                    Modifier.align(Alignment.Center)
                } else {
                    Modifier.fillMaxSize()
                }
            )
        }

        //  Seek 逃生口提示：拖动进度时手指进入顶部角落，松手取消进退
        AnimatedVisibility(
            visible = gestureMode == FullscreenGestureMode.Seek && seekCancelPending,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 96.dp),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300))
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = 4.dp
            ) {
                AppText(
                    text = "松开手指，取消进退",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // 控制层
        AnimatedVisibility(
            visible = showControls && gestureMode == FullscreenGestureMode.None,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(300))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部渐变 + 返回按钮和标题
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        AppIconButton(onClick = onNavigateToDetail) {
                            AppIcon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "返回详情页", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        AppText(
                            text = miniPlayerManager.currentTitle,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .copyOnLongPress(miniPlayerManager.currentTitle, "视频标题")
                        )

                        AppText(
                            text = currentClockText,
                            color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 8.dp)
                        )

                        //  [新增] 弹幕开关按钮；开启态跟随选中主题色，与玻璃底栏共用 selectionAccent 入口。
                        val danmakuActiveColor = AppContentColors.selectionAccent(
                            com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled,
                        )
                        val danmakuInactiveColor = Color.White.copy(alpha = 0.74f)
                        NativeDanmakuToggleButton(
                            enabled = danmakuEnabled,
                            onToggle = {
                                val newValue = !danmakuEnabled
                                danmakuManager.isEnabled = newValue
                                scope.launch {
                                    SettingsManager.setDanmakuEnabled(context, newValue, danmakuScope)
                                }
                                com.android.purebilibili.core.util.Logger.d(
                                    "FullscreenDanmaku",
                                    " Danmaku toggle: $newValue",
                                )
                            },
                            activeTint = danmakuActiveColor,
                            inactiveTint = danmakuInactiveColor,
                        )

                        //  [新增] 弹幕设置按钮
                        AppIconButton(onClick = { showDanmakuSettings = true }) {
                            AppIcon(Icons.Outlined.Settings, "弹幕设置", tint = Color.White)
                        }
                    }
                }

                //  [修改] 移除中间大按钮，改为在底部控制栏左侧显示

                // 底部进度条和控制按钮
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).align(Alignment.Center)
                    ) {
                        // 进度条行
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AppIconButton(
                                onClick = {
                                    lastInteractionTime = System.currentTimeMillis()
                                    player?.let {
                                        applyPlaybackButtonUserAction(
                                            player = it,
                                            isShowingPauseIcon = isPlaying
                                        )
                                    }
                                },
                            ) {
                                AppIcon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "暂停" else "播放",
                                    tint = AppContentColors.accent(),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            AppText(
                                FormatUtils.formatDuration((displayedProgressState.current / 1000).toInt()),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )

                            var isDragging by remember { mutableStateOf(false) }

                            ThinWigglyProgressBar(
                                progress = currentProgress,
                                seekPositionMs = displayedProgressState.current,
                                isSeekScrubbing = isDragging,
                                layoutPolicy = resolvePortraitProgressBarLayoutPolicy(
                                    LocalConfiguration.current.screenWidthDp
                                ),
                                onSeek = { newProgress ->
                                    isDragging = false
                                    val seekableDuration = resolveSeekableDurationMs(
                                        playbackDurationMs = duration,
                                        fallbackDurationMs = miniPlayerManager.duration
                                    )
                                    val newPosition = (newProgress * seekableDuration).toLong()
                                    player?.let {
                                        pendingGestureSeekPositionMs = newPosition
                                        seekPlayerFromUserAction(it, newPosition)
                                        danmakuManager.seekTo(newPosition)
                                    }
                                    currentProgress = newProgress
                                },
                                onSeekStart = {
                                    danmakuManager.prepareForSeekScrub()
                                    isDragging = true
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onSeekDragCancel = {
                                    isDragging = false
                                    danmakuManager.cancelSeekScrub()
                                },
                                duration = duration,
                                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            )

                            AppText(FormatUtils.formatDuration((duration / 1000).toInt()), color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        //  底部控制按钮行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 倍速按钮
                            FullscreenControlButton(
                                text = PlaybackSpeed.formatSpeed(playbackSpeed),
                                isHighlighted = playbackSpeed != 1.0f,
                                onClick = { showSpeedMenu = true }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            // 比例按钮
                            FullscreenControlButton(
                                text = aspectRatio.displayName,
                                isHighlighted = aspectRatio != VideoAspectRatio.FIT,
                                onClick = { showRatioMenu = true }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            //  弹幕设置按钮（横屏/全屏底栏右侧）
                            FullscreenControlButton(
                                text = "弹幕",
                                isHighlighted = false,
                                onClick = { showDanmakuSettings = true }
                            )
                        }
                    }
                }
            }
        }

        //  [新增] 弹幕设置面板
        if (showDanmakuSettings) {
            //  使用本地状态确保滑动条可以更新
            var localOpacity by remember(danmakuOpacity) { mutableFloatStateOf(danmakuOpacity) }
            var localFontScale by remember(danmakuFontScale) { mutableFloatStateOf(danmakuFontScale) }
            var localFontWeight by remember(danmakuSettings.fontWeight) {
                mutableIntStateOf(danmakuSettings.fontWeight)
            }
            var localSpeed by remember(danmakuSpeed) { mutableFloatStateOf(danmakuSpeed) }
            var localDisplayArea by remember(danmakuDisplayArea) { mutableFloatStateOf(danmakuDisplayArea) }
            var localStrokeWidth by remember(danmakuSettings.strokeWidth) {
                mutableFloatStateOf(danmakuSettings.strokeWidth)
            }
            var localLineHeight by remember(danmakuSettings.lineHeight) {
                mutableFloatStateOf(danmakuSettings.lineHeight)
            }
            var localScrollDurationSeconds by remember(danmakuSettings.scrollDurationSeconds) {
                mutableFloatStateOf(danmakuSettings.scrollDurationSeconds)
            }
            var localStaticDurationSeconds by remember(danmakuSettings.staticDurationSeconds) {
                mutableFloatStateOf(danmakuSettings.staticDurationSeconds)
            }
            var localScrollFixedVelocity by remember(danmakuSettings.scrollFixedVelocity) {
                mutableStateOf(danmakuSettings.scrollFixedVelocity)
            }
            var localStaticDanmakuToScroll by remember(danmakuSettings.staticDanmakuToScroll) {
                mutableStateOf(danmakuSettings.staticDanmakuToScroll)
            }
            var localMassiveMode by remember(danmakuSettings.massiveMode) {
                mutableStateOf(danmakuSettings.massiveMode)
            }
            var localMergeDuplicates by remember(danmakuMergeDuplicates) { mutableStateOf(danmakuMergeDuplicates) }
            var localDuplicateMergeWindowMs by remember(danmakuDuplicateMergeWindowMs) {
                mutableIntStateOf(danmakuDuplicateMergeWindowMs)
            }
            var localDuplicateMergeCountThreshold by remember(danmakuDuplicateMergeCountThreshold) {
                mutableIntStateOf(danmakuDuplicateMergeCountThreshold)
            }
            var localAllowScroll by remember(danmakuAllowScroll) { mutableStateOf(danmakuAllowScroll) }
            var localAllowTop by remember(danmakuAllowTop) { mutableStateOf(danmakuAllowTop) }
            var localAllowBottom by remember(danmakuAllowBottom) { mutableStateOf(danmakuAllowBottom) }
            var localAllowColorful by remember(danmakuAllowColorful) { mutableStateOf(danmakuAllowColorful) }
            var localAllowSpecial by remember(danmakuAllowSpecial) { mutableStateOf(danmakuAllowSpecial) }
            var localWeightFilterLevel by remember(danmakuSettings.weightFilterLevel) {
                mutableIntStateOf(danmakuSettings.weightFilterLevel)
            }
            var localHideInteractiveCommands by remember(danmakuSettings.hideInteractiveCommands) {
                mutableStateOf(danmakuSettings.hideInteractiveCommands)
            }
            var localSmartOcclusion by remember(danmakuSmartOcclusion) { mutableStateOf(danmakuSmartOcclusion) }
            var localBlockRulesRaw by remember(danmakuBlockRulesRaw) { mutableStateOf(danmakuBlockRulesRaw) }
            var localFullscreenPanelWidthMode by remember(danmakuSettings.fullscreenPanelWidthMode) {
                mutableStateOf(danmakuSettings.fullscreenPanelWidthMode)
            }
            var localPortraitDisplayAreaMode by remember(danmakuSettings.portraitDisplayAreaMode) {
                mutableStateOf(danmakuSettings.portraitDisplayAreaMode)
            }

            DanmakuSettingsPanel(
                isFullscreen = true,
                onSliderPreview = { parameter, next ->
                    com.android.purebilibili.feature.video.ui.components.applyDanmakuSliderPreview(danmakuManager, parameter, next)
                },
                settingsScope = danmakuScope,
                opacity = localOpacity,
                fontScale = localFontScale,
                showAdvancedSection = true,
                fontWeight = localFontWeight,
                speed = localSpeed,
                displayArea = localDisplayArea,
                strokeWidth = localStrokeWidth,
                lineHeight = localLineHeight,
                scrollDurationSeconds = localScrollDurationSeconds,
                staticDurationSeconds = localStaticDurationSeconds,
                scrollFixedVelocity = localScrollFixedVelocity,
                staticDanmakuToScroll = localStaticDanmakuToScroll,
                massiveMode = localMassiveMode,
                mergeDuplicates = localMergeDuplicates,
                duplicateMergeWindowMs = localDuplicateMergeWindowMs,
                duplicateMergeCountThreshold = localDuplicateMergeCountThreshold,
                allowScroll = localAllowScroll,
                allowTop = localAllowTop,
                allowBottom = localAllowBottom,
                allowColorful = localAllowColorful,
                allowSpecial = localAllowSpecial,
                weightFilterLevel = localWeightFilterLevel,
                hideInteractiveCommands = localHideInteractiveCommands,
                showBlockRuleEditor = true,
                showSmartOcclusionSection = true,
                blockRulesRaw = localBlockRulesRaw,
                smartOcclusion = localSmartOcclusion,
                fullscreenWidthMode = localFullscreenPanelWidthMode,
                portraitDisplayAreaMode = localPortraitDisplayAreaMode,
                onOpacityChange = {
                    localOpacity = it
                    danmakuManager.opacity = it
                    scope.launch { SettingsManager.setDanmakuOpacity(context, it, danmakuScope) }
                },
                onFontScaleChange = {
                    localFontScale = it
                    danmakuManager.fontScale = it
                    scope.launch { SettingsManager.setDanmakuFontScale(context, it, danmakuScope) }
                },
                onFontWeightChange = {
                    localFontWeight = it
                    danmakuManager.fontWeight = it
                    scope.launch { SettingsManager.setDanmakuFontWeight(context, it, danmakuScope) }
                },
                onSpeedChange = {
                    localSpeed = it
                    danmakuManager.speedFactor = it
                    scope.launch { SettingsManager.setDanmakuSpeed(context, it, danmakuScope) }
                },
                onDisplayAreaChange = {
                    localDisplayArea = it
                    danmakuManager.displayArea = it
                    scope.launch { SettingsManager.setDanmakuArea(context, it, danmakuScope) }
                },
                onStrokeWidthChange = {
                    localStrokeWidth = it
                    danmakuManager.strokeWidth = it
                    scope.launch { SettingsManager.setDanmakuStrokeWidth(context, it, danmakuScope) }
                },
                onLineHeightChange = {
                    localLineHeight = it
                    danmakuManager.lineHeight = it
                    scope.launch { SettingsManager.setDanmakuLineHeight(context, it, danmakuScope) }
                },
                onScrollDurationSecondsChange = {
                    localScrollDurationSeconds = it
                    danmakuManager.scrollDurationSeconds = it
                    scope.launch {
                        SettingsManager.setDanmakuScrollDurationSeconds(context, it, danmakuScope)
                    }
                },
                onStaticDurationSecondsChange = {
                    localStaticDurationSeconds = it
                    danmakuManager.staticDurationSeconds = it
                    scope.launch {
                        SettingsManager.setDanmakuStaticDurationSeconds(context, it, danmakuScope)
                    }
                },
                onScrollFixedVelocityChange = {
                    localScrollFixedVelocity = it
                    danmakuManager.scrollFixedVelocity = it
                    scope.launch {
                        SettingsManager.setDanmakuScrollFixedVelocity(context, it, danmakuScope)
                    }
                },
                onStaticDanmakuToScrollChange = {
                    localStaticDanmakuToScroll = it
                    danmakuManager.staticDanmakuToScroll = it
                    scope.launch { SettingsManager.setDanmakuStaticToScroll(context, it, danmakuScope) }
                },
                onMassiveModeChange = {
                    localMassiveMode = it
                    danmakuManager.massiveMode = it
                    scope.launch { SettingsManager.setDanmakuMassiveMode(context, it, danmakuScope) }
                },
                onMergeDuplicatesChange = {
                    localMergeDuplicates = it
                    // 需要在 Manager 中添加临时变量或直接持久化
                    // 对于 Switch 这种立即生效的 Prefernce，直接存就行
                    scope.launch { SettingsManager.setDanmakuMergeDuplicates(context, it, danmakuScope) }
                },
                onDuplicateMergeWindowMsChange = {
                    localDuplicateMergeWindowMs = it
                    scope.launch { SettingsManager.setDanmakuDuplicateMergeWindowMs(context, it, danmakuScope) }
                },
                onDuplicateMergeCountThresholdChange = {
                    localDuplicateMergeCountThreshold = it
                    scope.launch { SettingsManager.setDanmakuDuplicateMergeCountThreshold(context, it, danmakuScope) }
                },
                onAllowScrollChange = {
                    localAllowScroll = it
                    scope.launch { SettingsManager.setDanmakuAllowScroll(context, it, danmakuScope) }
                },
                onAllowTopChange = {
                    localAllowTop = it
                    scope.launch { SettingsManager.setDanmakuAllowTop(context, it, danmakuScope) }
                },
                onAllowBottomChange = {
                    localAllowBottom = it
                    scope.launch { SettingsManager.setDanmakuAllowBottom(context, it, danmakuScope) }
                },
                onAllowColorfulChange = {
                    localAllowColorful = it
                    scope.launch { SettingsManager.setDanmakuAllowColorful(context, it, danmakuScope) }
                },
                onAllowSpecialChange = {
                    localAllowSpecial = it
                    scope.launch { SettingsManager.setDanmakuAllowSpecial(context, it, danmakuScope) }
                },
                onWeightFilterLevelChange = {
                    localWeightFilterLevel = it
                    danmakuManager.weightFilterLevel = it
                    scope.launch { SettingsManager.setDanmakuWeightFilterLevel(context, it) }
                },
                onHideInteractiveCommandsChange = {
                    localHideInteractiveCommands = it
                    scope.launch { SettingsManager.setDanmakuHideInteractiveCommands(context, it) }
                },
                onSmartOcclusionChange = {
                    localSmartOcclusion = it
                    scope.launch { SettingsManager.setDanmakuSmartOcclusion(context, it, danmakuScope) }
                },
                onBlockRulesRawChange = {
                    localBlockRulesRaw = it
                    scope.launch { SettingsManager.setDanmakuBlockRulesRaw(context, it, danmakuScope) }
                },
                onFullscreenWidthModeChange = {
                    localFullscreenPanelWidthMode = it
                    scope.launch { SettingsManager.setDanmakuFullscreenPanelWidthMode(context, it) }
                },
                onPortraitDisplayAreaModeChange = {
                    localPortraitDisplayAreaMode = it
                    scope.launch { SettingsManager.setPortraitDanmakuDisplayAreaMode(context, it) }
                },
                onDismiss = { showDanmakuSettings = false }
            )
        }

        //  播放速度选择菜单
        if (showSpeedMenu) {
            com.android.purebilibili.feature.video.ui.components.SpeedSelectionMenu(
                currentSpeed = playbackSpeed,
                onSpeedSelected = { speed ->
                    playbackSpeed = speed
                    player?.setPlaybackSpeed(speed)
                    scope.launch {
                        SettingsManager.setLastPlaybackSpeed(context, speed)
                    }
                    showSpeedMenu = false
                    lastInteractionTime = System.currentTimeMillis()
                },
                onDismiss = { showSpeedMenu = false },
                placement = SpeedSelectionMenuPlacement.RIGHT_SIDE
            )
        }

        //  视频比例选择菜单
        if (showRatioMenu) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .pointerInput(Unit) {
                        detectTapGestures { showRatioMenu = false }
                    },
                contentAlignment = Alignment.Center
            ) {
                com.android.purebilibili.feature.video.ui.components.AspectRatioMenu(
                    currentRatio = aspectRatio,
                    onRatioSelected = { ratio ->
                        val safeRatio = resolveSafeVideoAspectRatio(
                            preferred = ratio,
                            isVerticalVideo = isVerticalContent
                        )
                        aspectRatio = safeRatio
                        aspectRatioSession.select(safeRatio)
                        scope.launch { aspectRatioSession.saveIfRemembered(context, safeRatio) }
                        showRatioMenu = false
                        lastInteractionTime = System.currentTimeMillis()
                    },
                    onDismiss = { showRatioMenu = false }
                )
            }
        }
    }
}

@Composable
private fun GestureIndicator(
    mode: FullscreenGestureMode,
    value: Float,
    seekTime: Long?,
    duration: Long,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val overlayStyle = rememberGestureLevelOverlayStyle(playerChromeProfile.tabPresentation)
    val overlayShape = AppShapes.container(ContainerLevel.Card)
    if (mode == FullscreenGestureMode.Seek) {
        AppSurface(
            modifier = modifier.then(
                if (hazeState != null) {
                    Modifier.unifiedBlur(
                        hazeState = hazeState,
                        shape = overlayShape,
                        surfaceType = BlurSurfaceType.OVERLAY
                    )
                } else {
                    Modifier
                }
            ),
            shape = overlayShape,
            color = Color.Black.copy(alpha = 0.74f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.58f))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(min = 128.dp, max = 190.dp)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                AppText(
                    "${FormatUtils.formatDuration(((seekTime ?: 0) / 1000).toInt())} / ${FormatUtils.formatDuration((duration / 1000).toInt())}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        val mappedMode = when (mode) {
            FullscreenGestureMode.Brightness -> VideoGestureMode.Brightness
            FullscreenGestureMode.Volume -> VideoGestureMode.Volume
            else -> VideoGestureMode.None
        }
        val kind = resolveGestureLevelKind(mappedMode)
        val sideAlignment = if (kind != null) {
            resolveGestureLevelOverlaySpec(
                style = overlayStyle,
                kind = kind,
                percent = value,
                brightnessColor = androidx.compose.ui.res.colorResource(com.android.purebilibili.R.color.player_brightness_accent),
            ).alignment
        } else {
            Alignment.Center
        }
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = sideAlignment
        ) {
            GestureLevelOverlayContent(
                mode = mappedMode,
                percent = value,
                style = overlayStyle,
                modifier = if (overlayStyle == GestureLevelOverlayStyle.Miuix) {
                    Modifier.padding(horizontal = 22.dp)
                } else {
                    Modifier
                }
            )
        }
    }
}

/**
 *  全屏底部控制按钮
 */
@Composable
private fun FullscreenControlButton(
    text: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    AppSurface(
        onClick = onClick,
        shape = AppShapes.container(ContainerLevel.Chip),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        AppText(
            text = text,
            color = if (isHighlighted) AppContentColors.accent() else Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun formatCurrentClock(): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date())
}
