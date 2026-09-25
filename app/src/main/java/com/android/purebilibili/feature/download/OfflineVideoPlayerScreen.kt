package com.android.purebilibili.feature.download
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import com.android.purebilibili.core.util.LocalAppWindowAdaptiveInfo
import com.android.purebilibili.core.util.applyPlayerRequestedOrientation
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.android.purebilibili.core.ui.LocalNavigationBackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppCommentIcon
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.rememberAppPlayIcon
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.theme.resolveAdaptivePrimaryAccentColors
import com.android.purebilibili.core.store.DanmakuSettings
import com.android.purebilibili.core.store.SettingsManager
import com.android.purebilibili.core.store.DEFAULT_LONG_PRESS_SPEED
import com.android.purebilibili.core.store.resolveDanmakuSettingsScope
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.danmaku.configureAsPassiveDanmakuOverlay
import com.android.purebilibili.feature.video.danmaku.rememberDanmakuManager
import com.android.purebilibili.feature.video.ui.gesture.GestureLevelKind
import com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayContent
import com.android.purebilibili.feature.video.ui.gesture.resolveGestureLevelIcon
import com.android.purebilibili.feature.video.ui.gesture.rememberGestureLevelOverlayStyle
import com.android.purebilibili.feature.video.ui.section.VideoGestureMode
import com.android.purebilibili.danmaku.engine.DanmakuRenderView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

/**
 * 手势模式枚举
 */
private enum class GestureMode { None, Brightness, Volume, Seek }

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
    val longPressSpeed by SettingsManager
        .getLongPressSpeed(context)
        .collectAsStateWithLifecycle(initialValue = DEFAULT_LONG_PRESS_SPEED)
    val task = tasks[currentTaskId]
    
    // === 状态管理 ===
    var isFullscreen by remember(currentTaskId, task?.isAudioOnly, task?.isVerticalVideo) {
        mutableStateOf(
            resolveOfflineVideoStartFullscreen(
                isAudioOnly = task?.isAudioOnly ?: false,
                isVerticalVideo = task?.isVerticalVideo ?: false
            )
        )
    }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var initialOrientationResolved by remember(currentTaskId) { mutableStateOf(false) }
    
    // 手势状态
    var gestureMode by remember { mutableStateOf(GestureMode.None) }
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var gesturePercent by remember { mutableFloatStateOf(0f) }
    var isGestureVisible by remember { mutableStateOf(false) }
    
    // 进度拖动状态
    var seekTargetTime by remember { mutableLongStateOf(0L) }
    var startPosition by remember { mutableLongStateOf(0L) }
    var totalDragDistanceX by remember { mutableFloatStateOf(0f) }
    var totalDragDistanceY by remember { mutableFloatStateOf(0f) }
    var startVolume by remember { mutableIntStateOf(0) }
    var startBrightness by remember { mutableFloatStateOf(0.5f) }
    
    // 双击跳转反馈
    var seekFeedbackText by remember { mutableStateOf<String?>(null) }
    var seekFeedbackVisible by remember { mutableStateOf(false) }
    
    // 长按倍速状态
    var isLongPressing by remember { mutableStateOf(false) }
    var originalSpeed by remember { mutableFloatStateOf(1.0f) }
    var longPressSpeedVisible by remember { mutableStateOf(false) }
    var danmakuEnabled by remember(currentTaskId) { mutableStateOf(true) }

    LaunchedEffect(danmakuManager, danmakuSettings) {
        danmakuManager.updateSettings(settings = danmakuSettings)
    }
    LaunchedEffect(currentTaskId, danmakuSettings.enabled) {
        danmakuEnabled = danmakuSettings.enabled
    }
    
    // 双击跳转秒数
    val seekForwardSeconds = 10
    val seekBackwardSeconds = 10
    
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
    val player = remember(file.absolutePath) {
        ExoPlayer.Builder(context).build()
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
        DownloadManager.updatePlaybackPosition(
            taskId = activeTask.id,
            positionMs = resolveOfflinePersistedPlaybackPosition(
                currentPositionMs = activePlayer.currentPosition,
                durationMs = activePlayer.duration
            )
        )
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
    LaunchedEffect(seekFeedbackVisible) {
        if (seekFeedbackVisible) {
            delay(800)
            seekFeedbackVisible = false
        }
    }
    
    // 长按倍速提示自动消失
    LaunchedEffect(longPressSpeedVisible) {
        if (longPressSpeedVisible) {
            delay(1000)
            longPressSpeedVisible = false
        }
    }
    
    // 获取 Activity
    fun getActivity(): Activity? = activity
    
    fun applyWindowMode(fullscreen: Boolean) {
        val act = getActivity() ?: return
        val requestedOrientation = when (
            resolveOfflineRequestedOrientationMode(
                isFullscreen = fullscreen,
                displayContext = displayContext,
            )
        ) {
            OfflineRequestedOrientationMode.Unspecified ->
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            OfflineRequestedOrientationMode.SensorLandscape ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OfflineRequestedOrientationMode.Portrait ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        act.applyPlayerRequestedOrientation(
            requestedOrientation = requestedOrientation,
            displayContext = displayContext,
        )
        if (fullscreen) {
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 全屏切换函数
    fun toggleFullscreen() {
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

    fun switchEpisode(targetTaskId: String) {
        if (targetTaskId == task.id) return
        persistCurrentPlaybackPosition(task, player)
        currentTaskId = targetTaskId
        showControls = true
    }
    
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
    LaunchedEffect(activity, displayContext, isFullscreen) {
        applyWindowMode(isFullscreen)
    }

    LaunchedEffect(player, file.absolutePath, task.id) {
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        val restoredPosition = task.lastPlaybackPositionMs.coerceAtLeast(0L)
        if (restoredPosition > 0L) {
            player.seekTo(restoredPosition)
        } else {
            player.seekTo(0L)
        }
        player.playWhenReady = true
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
            player.release()
            activity?.applyPlayerRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            activity?.let { act ->
                val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
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
        if (showDanmakuLayer) {
            danmakuManager.show()
        } else {
            danmakuManager.hide()
        }
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

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (initialOrientationResolved || task.isAudioOnly) return
                if (task.isVerticalVideo) {
                    initialOrientationResolved = true
                    return
                }
                val videoSize = player.videoSize
                if (videoSize.width > 0 && videoSize.height > 0) {
                    isFullscreen = videoSize.width >= videoSize.height
                    initialOrientationResolved = true
                }
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
            if (abs(resolvedPosition - lastPersistedPosition) >= 2_000L) {
                DownloadManager.updatePlaybackPosition(task.id, resolvedPosition)
                lastPersistedPosition = resolvedPosition
            }
            delay(2_000L)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 🎛️ 拖拽手势：亮度/音量/进度
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        // 边缘防误触
                        val density = context.resources.displayMetrics.density
                        val safeZonePx = 48 * density
                        val screenHeight = size.height
                        val isEdgeGesture = offset.y < safeZonePx || offset.y > (screenHeight - safeZonePx)
                        
                        if (isEdgeGesture) {
                            isGestureVisible = false
                            gestureMode = GestureMode.None
                        } else {
                            isGestureVisible = true
                            gestureMode = GestureMode.None
                            totalDragDistanceY = 0f
                            totalDragDistanceX = 0f
                            
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            startPosition = player.currentPosition
                            
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
                        }
                    },
                    onDragEnd = {
                        if (gestureMode == GestureMode.Seek) {
                            seekToPosition(seekTargetTime)
                        }
                        isGestureVisible = false
                        gestureMode = GestureMode.None
                    },
                    onDragCancel = {
                        isGestureVisible = false
                        gestureMode = GestureMode.None
                    },
                    onDrag = { change, dragAmount ->
                        if (!isGestureVisible && gestureMode == GestureMode.None) {
                            // 在 safe zone 中启动被忽略
                        } else {
                            // 确定手势类型
                            if (gestureMode == GestureMode.None) {
                                if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                    gestureMode = GestureMode.Seek
                                } else {
                                    val screenWidth = context.resources.displayMetrics.widthPixels
                                    gestureMode = if (change.position.x < screenWidth / 2) {
                                        GestureMode.Brightness
                                    } else {
                                        GestureMode.Volume
                                    }
                                }
                            }
                            
                            when (gestureMode) {
                                GestureMode.Seek -> {
                                    totalDragDistanceX += dragAmount.x
                                    val duration = player.duration.coerceAtLeast(0L)
                                    val seekDelta = (totalDragDistanceX * 200).toLong()
                                    seekTargetTime = (startPosition + seekDelta).coerceIn(0L, duration)
                                }
                                GestureMode.Brightness -> {
                                    totalDragDistanceY -= dragAmount.y
                                    val screenHeight = context.resources.displayMetrics.heightPixels
                                    val deltaPercent = totalDragDistanceY / screenHeight
                                    val newBrightness = (startBrightness + deltaPercent).coerceIn(0f, 1f)
                                    
                                    if (abs(newBrightness - gesturePercent) > 0.02f) {
                                        getActivity()?.window?.attributes = getActivity()?.window?.attributes?.apply {
                                            screenBrightness = newBrightness
                                        }
                                        gesturePercent = newBrightness
                                    }
                                    gestureIcon = resolveGestureLevelIcon(
                                        style = gestureLevelOverlayStyle,
                                        kind = GestureLevelKind.Brightness,
                                        percent = gesturePercent
                                    )
                                }
                                GestureMode.Volume -> {
                                    totalDragDistanceY -= dragAmount.y
                                    val screenHeight = context.resources.displayMetrics.heightPixels
                                    val deltaPercent = totalDragDistanceY / screenHeight
                                    val newVolPercent = ((startVolume.toFloat() / maxVolume) + deltaPercent).coerceIn(0f, 1f)
                                    val targetVol = (newVolPercent * maxVolume).toInt()
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    gesturePercent = newVolPercent
                                    
                                    gestureIcon = resolveGestureLevelIcon(
                                        style = gestureLevelOverlayStyle,
                                        kind = GestureLevelKind.Volume,
                                        percent = gesturePercent
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                )
            }
            // 🖱️ 点击/双击/长按手势
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onLongPress = {
                        // 长按倍速
                        originalSpeed = player.playbackParameters.speed
                        player.setPlaybackSpeed(longPressSpeed)
                        isLongPressing = true
                        longPressSpeedVisible = true
                    },
                    onDoubleTap = { offset ->
                        val screenWidth = size.width
                        when {
                            // 右侧 1/3：快进
                            offset.x > screenWidth * 2 / 3 -> {
                                val seekMs = seekForwardSeconds * 1000L
                                val newPos = (player.currentPosition + seekMs).coerceAtMost(player.duration.coerceAtLeast(0L))
                                seekToPosition(newPos)
                                seekFeedbackText = "+${seekForwardSeconds}s"
                                seekFeedbackVisible = true
                            }
                            // 左侧 1/3：后退
                            offset.x < screenWidth / 3 -> {
                                val seekMs = seekBackwardSeconds * 1000L
                                val newPos = (player.currentPosition - seekMs).coerceAtLeast(0L)
                                seekToPosition(newPos)
                                seekFeedbackText = "-${seekBackwardSeconds}s"
                                seekFeedbackVisible = true
                            }
                            // 中间：暂停/播放
                            else -> {
                                player.playWhenReady = !player.playWhenReady
                            }
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        // 松开时恢复原速度
                        if (isLongPressing) {
                            player.setPlaybackSpeed(originalSpeed)
                            isLongPressing = false
                            longPressSpeedVisible = false
                        }
                    }
                )
            }
    ) {
        // 1. PlayerView
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    keepScreenOn = true
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (danmakuAvailable) {
            AndroidView(
                factory = { ctx ->
                    DanmakuRenderView(ctx).apply {
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        configureAsPassiveDanmakuOverlay()
                        danmakuManager.attachView(this)
                    }
                },
                update = { view ->
                    view.visibility = if (showDanmakuLayer) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
                    if (view.width > 0 && view.height > 0) {
                        val sizeTag = "${view.width}x${view.height}"
                        if (view.tag != sizeTag) {
                            view.tag = sizeTag
                            danmakuManager.attachView(view)
                        }
                    }
                },
                onRelease = { view -> danmakuManager.detachView(view) },
                modifier = Modifier.fillMaxSize()
            )
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
            if (gestureMode == GestureMode.Seek) {
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
                                color = if (deltaSeconds > 0) Color.Green else Color.Red,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            } else if (
                gestureMode == GestureMode.Brightness ||
                gestureMode == GestureMode.Volume
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    GestureLevelOverlayContent(
                        mode = if (gestureMode == GestureMode.Brightness) {
                            VideoGestureMode.Brightness
                        } else {
                            VideoGestureMode.Volume
                        },
                        percent = gesturePercent,
                        style = gestureLevelOverlayStyle,
                        modifier = Modifier
                            .align(
                                if (playerChromeProfile.effects.usesTonalContainerTreatment) {
                                    if (gestureMode == GestureMode.Volume) {
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
        
        // 4. 双击跳转反馈
        AnimatedVisibility(
            visible = seekFeedbackVisible,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(initialScale = 0.5f) + fadeIn(),
            exit = scaleOut(targetScale = 0.8f) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color.Black.copy(0.75f), AppShapes.container(ContainerLevel.Floating)),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    text = seekFeedbackText ?: "",
                    color = if (seekFeedbackText?.startsWith("+") == true) Color.Green else Color.Red,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
        
        // 5. 长按倍速提示
        AnimatedVisibility(
            visible = longPressSpeedVisible,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 100.dp),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            AppSurface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = AppShapes.container(ContainerLevel.Floating)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(
                        Icons.Outlined.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AppText(
                        text = "${longPressSpeed}x 倍速播放中",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // 6. 顶部渐变遮罩
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
                        onClick = {
                            if (player.playbackState == Player.STATE_ENDED) {
                                player.seekTo(0)
                                player.play()
                            } else if (player.isPlaying) {
                                player.pause()
                            } else {
                                player.play()
                            }
                        },
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
            visible = showControls && !isPlaying,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            AppSurface(
                onClick = { player.play() },
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
    val primaryColor = MaterialTheme.colorScheme.primary
    
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
