// 文件路径: feature/video/MiniPlayerOverlay.kt
package com.android.purebilibili.feature.video.ui.overlay

import com.android.purebilibili.core.ui.MediaContrastPalette

import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.core.util.Logger

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardDefaults
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppDropdownMenu
import com.android.purebilibili.core.ui.components.AppDropdownMenuItem
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.android.purebilibili.core.ui.findHostActivity
import com.android.purebilibili.core.ui.rememberVisualWorkActive
import com.android.purebilibili.core.ui.animation.gl.ThanosEffectView
import com.android.purebilibili.core.ui.animation.gl.isThanosEffectSupported
import com.android.purebilibili.feature.audio.screen.captureAudioNowPlayingWindowSnapshot
import com.android.purebilibili.feature.video.util.captureVideoScreenshot
import kotlin.math.abs
import kotlin.math.roundToInt
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.motion.iosMorphTween
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion

private const val TAG = "MiniPlayerOverlay"
private const val MINI_PLAYER_VISIBILITY_DURATION_MILLIS = 280

/**
 *  小窗播放器覆盖层
 *
 * 交互说明：
 * - 拖动顶部标题栏区域 → 移动小窗位置
 * - 在视频区域左右滑动 → 调节播放进度
 * - 单击 → 显示/隐藏控制按钮
 * - 双击 → 展开到全屏
 * - 点击关闭按钮(×) → 关闭小窗
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun MiniPlayerOverlay(
    miniPlayerManager: MiniPlayerManager,
    onExpandClick: () -> Unit,
    onPictureInPictureClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * 系统 PIP 期间保持本 overlay 挂载但渲染空占位：视频 surface 让位给专用
     * PIP 渲染面，同时 AnimatedVisibility 不经历 exit/enter，退出 PIP 时
     * 迷你播放器不再重放飞入动画。
     */
    suppressContentForPip: Boolean = false,
) {
    key(miniPlayerManager.miniPlayerSessionId) {
        MiniPlayerSessionOverlay(miniPlayerManager, onExpandClick, onPictureInPictureClick, modifier, suppressContentForPip)
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun MiniPlayerSessionOverlay(
    miniPlayerManager: MiniPlayerManager,
    onExpandClick: () -> Unit,
    onPictureInPictureClick: (() -> Unit)?,
    modifier: Modifier,
    suppressContentForPip: Boolean,
) {
    val clearIcon = rememberAppClearIcon()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateAsState()
    val hostLifecycleStarted = lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)

    //  [调试] 仅在状态变化时记录，避免组合阶段高频日志
    val currentMode = miniPlayerManager.getCurrentMode()
    LaunchedEffect(currentMode, miniPlayerManager.isMiniMode, miniPlayerManager.isActive) {
        Logger.d(
            "MiniPlayerOverlay",
            "Overlay state changed: mode=$currentMode, isMiniMode=${miniPlayerManager.isMiniMode}, isActive=${miniPlayerManager.isActive}"
        )
    }

    //  [简化] 小窗可见性由 AnimatedVisibility 的 isMiniMode && isActive 控制
    // 不再需要额外的模式检查


    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val reduceMotion = rememberSystemReduceMotion()
    val visualWorkActive = rememberVisualWorkActive()
    val context = LocalContext.current
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolveMiniPlayerOverlayLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }
    val shellVisual = remember(layoutPolicy, playerChromeProfile) {
        resolveMiniPlayerOverlayShellVisual(
            layout = layoutPolicy,
            chromeProfile = playerChromeProfile,
        )
    }
    val accentColor = if (shellVisual.useThemePrimaryAccent) {
        AppSurfaceTokens.primary()
    } else {
        AppContentColors.accent()
    }

    val padding = layoutPolicy.outerPaddingDp.dp
    val headerHeight = 48.dp // 顶部可拖动区域高度
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    val resizeBounds = remember(configuration.screenWidthDp, configuration.screenHeightDp, layoutPolicy, miniPlayerManager.videoAspectRatio) {
        resolveMiniPlayerResizeBounds(
            defaultWidthDp = layoutPolicy.miniPlayerWidthDp,
            defaultHeightDp = layoutPolicy.miniPlayerHeightDp,
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            outerPaddingDp = layoutPolicy.outerPaddingDp,
            topInsetDp = layoutPolicy.dragTopInsetDp,
            bottomInsetDp = layoutPolicy.dragBottomInsetDp,
            videoAspectRatio = miniPlayerManager.videoAspectRatio,
        )
    }
    var miniPlayerWidthDp by rememberSaveable(configuration.screenWidthDp) {
        mutableFloatStateOf(layoutPolicy.miniPlayerWidthDp.toFloat())
    }
    LaunchedEffect(resizeBounds) {
        miniPlayerWidthDp = miniPlayerWidthDp.coerceIn(
            resizeBounds.minWidthDp,
            resizeBounds.maxWidthDp
        )
    }
    val adaptiveDimensions = remember(miniPlayerManager.videoAspectRatio, miniPlayerWidthDp, layoutPolicy,
        configuration.screenWidthDp, configuration.screenHeightDp) {
        resolveAdaptiveMiniPlayerDimensions(
            videoAspectRatio = miniPlayerManager.videoAspectRatio,
            currentWidthDp = miniPlayerWidthDp.coerceIn(resizeBounds.minWidthDp, resizeBounds.maxWidthDp),
            defaultHeightDp = layoutPolicy.miniPlayerHeightDp.toFloat(),
            maxWidthDp = (configuration.screenWidthDp - layoutPolicy.outerPaddingDp * 2).coerceAtLeast(1).toFloat(),
            maxHeightDp = (configuration.screenHeightDp - layoutPolicy.outerPaddingDp * 2 -
                layoutPolicy.dragTopInsetDp - layoutPolicy.dragBottomInsetDp).coerceAtLeast(1).toFloat(),
        )
    }
    val miniPlayerAspectRatio = adaptiveDimensions.aspectRatio
    val miniPlayerWidthDpEffective = adaptiveDimensions.widthDp
    val miniPlayerHeightDp = adaptiveDimensions.heightDp
    val miniPlayerWidth = miniPlayerWidthDpEffective.dp
    val miniPlayerHeight = miniPlayerHeightDp.dp

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val miniPlayerWidthPx = with(density) { miniPlayerWidth.toPx() }
    val miniPlayerHeightPx = with(density) { miniPlayerHeight.toPx() }
    val paddingPx = with(density) { padding.toPx() }
    val dragTopInsetPx = with(density) { layoutPolicy.dragTopInsetDp.dp.toPx() }
    val dragBottomInsetPx = with(density) { layoutPolicy.dragBottomInsetDp.dp.toPx() }

    //  [新增] 获取卡片位置信息，用于初始落位和动画适配
    val cardBounds = com.android.purebilibili.core.util.CardPositionManager.lastClickedCardBounds
    val cardPosition = com.android.purebilibili.core.util.CardPositionManager.cardHorizontalPosition
    val entryFromLeft = miniPlayerManager.entryFromLeft

    val initialOverlayOffset = remember(
        entryFromLeft,
        cardBounds,
        screenWidthPx,
        screenHeightPx,
        miniPlayerWidthPx,
        miniPlayerHeightPx,
        paddingPx,
        dragTopInsetPx,
        dragBottomInsetPx
    ) {
        resolveMiniPlayerInitialOverlayOffset(
            cardLeftPx = cardBounds?.left,
            entryFromLeft = entryFromLeft,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            miniPlayerWidthPx = miniPlayerWidthPx,
            miniPlayerHeightPx = miniPlayerHeightPx,
            outerPaddingPx = paddingPx,
            topInsetPx = dragTopInsetPx,
            bottomInsetPx = dragBottomInsetPx
        )
    }

    //  [修复] 首次打开前先夹进可见区域，避免右侧按钮跑出屏幕
    var offsetX by remember { mutableFloatStateOf(initialOverlayOffset.x) }
    var offsetY by remember { mutableFloatStateOf(initialOverlayOffset.y) }

    // 控制按钮显示状态
    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // [新增] 贴边隐藏状态
    var isStashed by remember { mutableStateOf(false) }
    // 记录隐藏在哪一侧 (Left or Right), 默认为 Right，后续根据位置计算
    var stashSide by remember { mutableStateOf(com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.RIGHT) }

    // [新增] Stashed 状态下的 Y 轴偏移量（允许在隐藏时上下拖动）
    var stashedOffsetY by remember { mutableFloatStateOf(initialOverlayOffset.y) }


    // 进度拖动状态
    var isDraggingProgress by remember { mutableStateOf(false) }
    var dragProgressDelta by remember { mutableFloatStateOf(0f) }
    var dragProgressStartPosition by remember { mutableLongStateOf(0L) }
    var seekPreviewPosition by remember { mutableLongStateOf(0L) }

    // 位置拖动状态
    var isDraggingPosition by remember { mutableStateOf(false) }
    var rawDragOffsetX by remember { mutableStateOf<Float?>(null) }
    var edgeStashSide by remember { mutableStateOf<MiniPlayerEdgeStashSide?>(null) }
    val edgeStashThresholdPx = with(density) { 28.dp.toPx() }
    val stashHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.PLAYER_GESTURES)
    val stashProgress = animateFloatAsState(
        targetValue = if (isStashed) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else tween(220, easing = FastOutSlowInEasing),
        label = "miniPlayerEdgeCollapse",
    )
    val showExpandedPlayer by remember { derivedStateOf { stashProgress.value < 0.999f } }
    val showStashHandle by remember { derivedStateOf { stashProgress.value > 0.001f } }

    var isResizing by remember { mutableStateOf(false) }
    var contentDragIntent by remember { mutableStateOf(MiniPlayerContentDragIntent.UNDECIDED) }
    var contentDragTotalX by remember { mutableFloatStateOf(0f) }
    var contentDragTotalY by remember { mutableFloatStateOf(0f) }
    var moreExpanded by remember { mutableStateOf(false) }
    val chrome = resolveMiniPlayerOverlayChrome(
        showControls = showControls || moreExpanded,
        isDraggingProgress = isDraggingProgress,
        isDraggingPosition = isDraggingPosition,
        isResizing = isResizing
    )

    // 播放器状态
    //  [修复] 在退出动画期间保持旧 Player 引用，防止画面跳变
    // 当 isMiniMode 为 false (正在退出) 时，不再更新 player，保持最后一帧画面
    val rawPlayer = miniPlayerManager.player
    var player by remember { mutableStateOf(rawPlayer) }

    SideEffect {
        if (miniPlayerManager.isMiniMode && rawPlayer != null && player !== rawPlayer) player = rawPlayer
    }

    var isPlaying by remember { mutableStateOf(player?.isPlaying ?: false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    var isSeekable by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val observedPlayer = player
        fun syncPlaybackState() {
            isPlaying = observedPlayer?.let {
                it.playWhenReady && it.playbackState != Player.STATE_ENDED && it.playbackState != Player.STATE_IDLE
            } == true
            isBuffering = observedPlayer?.let { it.playWhenReady && it.playbackState == Player.STATE_BUFFERING } == true
            duration = observedPlayer?.duration?.coerceAtLeast(0L) ?: 0L
            isSeekable = observedPlayer?.isCurrentMediaItemSeekable == true && duration > 0L
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = syncPlaybackState()
        }
        observedPlayer?.addListener(listener)
        syncPlaybackState()
        onDispose { observedPlayer?.removeListener(listener) }
    }

    val sessionId = miniPlayerManager.miniPlayerSessionId
    val dismissScope = rememberCoroutineScope()
    val cardCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val playerViewRef = remember { arrayOfNulls<PlayerView>(1) }
    var dismissing by remember { mutableStateOf(false) }
    var contentHidden by remember { mutableStateOf(false) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }
    var particleView by remember { mutableStateOf<ThanosEffectView?>(null) }

    fun finishDismiss() {
        if (!dismissing) return
        dismissJob?.cancel()
        dismissJob = null
        particleView?.dispose()
        particleView = null
        if (miniPlayerManager.miniPlayerSessionId == sessionId && miniPlayerManager.isMiniMode) {
            // Closing has its own particle handoff; avoid a second sliding exit.
            miniPlayerManager.exitMiniMode(animate = false)
            miniPlayerManager.dismiss()
        }
    }

    fun dismissMiniPlayer() {
        if (dismissing) return
        dismissing = true
        moreExpanded = false
        showControls = false
        player?.pause()
        val window = context.findHostActivity()?.window
        if (reduceMotion || !visualWorkActive || suppressContentForPip || window == null ||
            !isThanosEffectSupported(context)
        ) {
            finishDismiss()
            return
        }
        dismissJob = dismissScope.launch {
            try {
                withFrameNanos { }
                val bounds = cardCoordinates[0]?.takeIf { it.isAttached }?.boundsInWindow()
                val view = playerViewRef[0]
                val videoSurface = view?.videoSurfaceView
                if (bounds == null || bounds.isEmpty || view == null || videoSurface == null ||
                    videoSurface.width <= 0 || videoSurface.height <= 0
                ) {
                    finishDismiss()
                    return@launch
                }
                val snapshot = withTimeoutOrNull(500L) {
                    captureAudioNowPlayingWindowSnapshot(
                        window, bounds, with(density) { shellVisual.cardCornerRadiusDp.dp.toPx() },
                    )
                }
                if (snapshot == null) { finishDismiss(); return@launch }
                val bitmap = snapshot.bitmap
                var transferred = false
                try {
                    // SurfaceView is a separate layer; copy the actual player frame into the
                    // window snapshot so both surface types dissolve with the visible video.
                    val frame = withTimeoutOrNull(500L) {
                        captureVideoScreenshot(view, videoSurface.width, videoSurface.height)
                    }
                    if (frame == null) { finishDismiss(); return@launch }
                    try {
                        val location = IntArray(2)
                        videoSurface.getLocationInWindow(location)
                        val left = (location[0] - snapshot.windowBounds.left).toFloat()
                        val top = (location[1] - snapshot.windowBounds.top).toFloat()
                        val radius = with(density) { shellVisual.cardCornerRadiusDp.dp.toPx() }
                        val path = android.graphics.Path().apply {
                            addRoundRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), radius, radius, android.graphics.Path.Direction.CW)
                        }
                        android.graphics.Canvas(bitmap).apply {
                            clipPath(path)
                            drawBitmap(frame, null, android.graphics.RectF(left, top, left + videoSurface.width, top + videoSurface.height),
                                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                        }
                    } finally { frame.recycle() }
                    if (!dismissing || miniPlayerManager.miniPlayerSessionId != sessionId || !visualWorkActive) return@launch
                    particleView = ThanosEffectView.attach(
                        window = window,
                        bitmap = bitmap,
                        windowBounds = android.graphics.RectF(snapshot.windowBounds),
                        onFirstFrame = {
                            contentHidden = true
                            playerViewRef[0]?.visibility = android.view.View.INVISIBLE
                        },
                        onComplete = { finishDismiss() },
                    )
                    transferred = particleView != null
                    if (!transferred) { finishDismiss(); return@launch }
                    delay(5_000L)
                    finishDismiss()
                } finally {
                    if (!transferred && !bitmap.isRecycled) bitmap.recycle()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Logger.w(TAG, "Mini player dissolve failed: ${error.message}")
                finishDismiss()
            }
        }
    }

    LaunchedEffect(dismissing, visualWorkActive, suppressContentForPip, reduceMotion) {
        if (dismissing && (!visualWorkActive || suppressContentForPip || reduceMotion)) finishDismiss()
    }
    DisposableEffect(sessionId) {
        onDispose {
            dismissJob?.cancel()
            particleView?.dispose()
            if (dismissing && miniPlayerManager.miniPlayerSessionId == sessionId && miniPlayerManager.isMiniMode) {
                miniPlayerManager.exitMiniMode(animate = false)
                miniPlayerManager.dismiss()
            }
        }
    }


    val shouldPollProgress = shouldPollMiniPlayerProgress(
        playerExists = player != null,
        hostLifecycleStarted = hostLifecycleStarted && visualWorkActive && !suppressContentForPip && !dismissing && !isStashed,
        isMiniMode = miniPlayerManager.isMiniMode,
        isActive = miniPlayerManager.isActive,
        isLiveMode = miniPlayerManager.isLiveMode  // 📺 直播不轮询进度
    )
    // 仅在小窗真实可用时轮询播放器状态，避免后台空转。
    LaunchedEffect(player, shouldPollProgress, isDraggingProgress) {
        if (!shouldPollProgress) return@LaunchedEffect
        while (true) {
            val currentPlayer = player ?: break
            val currentIsPlaying = currentPlayer.isPlaying
            duration = currentPlayer.duration.coerceAtLeast(0L)
            currentPosition = currentPlayer.currentPosition
            if (!isDraggingProgress) {
                currentProgress = if (duration > 0L) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            }
            delay(resolveMiniPlayerPollingIntervalMs(isPlaying = currentIsPlaying))
        }
    }

    // Any gesture/menu change cancels the old timer. Restart only after interaction ends.
    val autoHide = rememberPlayerControlsAutoHide(3_000L)
    val interactionBusy = isDraggingPosition || isDraggingProgress || isResizing || moreExpanded
    LaunchedEffect(showControls, lastInteractionTime, interactionBusy, isStashed, hostLifecycleStarted, autoHide.timeoutMillis) {
        val timeout = autoHide.timeoutMillis ?: return@LaunchedEffect
        if (showControls && !interactionBusy && !isStashed && hostLifecycleStarted) {
            delay(timeout)
            if (autoHide.canHide()) showControls = false
        }
    }

    // 动画 - 只有在非拖动时才使用动画
    // 如果是 Stashed 状态，TargetX 应该在屏幕边缘外只露一点，或者贴在边缘
    val targetOffsetX = if (isStashed) {
        if (stashSide == com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.LEFT) {
            0f // 左侧贴边
        } else {
            // 右侧贴边
            with(density) { screenWidthPx - layoutPolicy.stashedWidthDp.dp.toPx() }
        }
    } else {
        offsetX
    }

    val targetOffsetY = if (isStashed) stashedOffsetY else offsetY

    fun clampCurrentOffset() {
        val clamped = resolveMiniPlayerOffsetAfterSizeChanged(
            offsetX = offsetX,
            offsetY = offsetY,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            miniPlayerWidthPx = miniPlayerWidthPx,
            miniPlayerHeightPx = miniPlayerHeightPx,
            outerPaddingPx = paddingPx,
            topInsetPx = dragTopInsetPx,
            bottomInsetPx = dragBottomInsetPx
        )
        offsetX = clamped.x
        offsetY = clamped.y
    }

    fun moveMiniPlayerBy(deltaX: Float, deltaY: Float) {
        val rawX = (rawDragOffsetX ?: offsetX) + deltaX
        rawDragOffsetX = rawX
        val nextSide = resolveMiniPlayerEdgeStashSide(
            rawX, paddingPx, (screenWidthPx - miniPlayerWidthPx - paddingPx).coerceAtLeast(paddingPx),
            edgeStashThresholdPx, edgeStashSide,
        )
        if (nextSide != null && nextSide != edgeStashSide) stashHaptic(HapticType.LIGHT)
        edgeStashSide = nextSide
        val clamped = clampMiniPlayerOverlayOffset(
            offsetX = rawX,
            offsetY = offsetY + deltaY,
            screenWidthPx = screenWidthPx,
            screenHeightPx = screenHeightPx,
            miniPlayerWidthPx = miniPlayerWidthPx,
            miniPlayerHeightPx = miniPlayerHeightPx,
            outerPaddingPx = paddingPx,
            topInsetPx = dragTopInsetPx,
            bottomInsetPx = dragBottomInsetPx
        )
        offsetX = clamped.x
        offsetY = clamped.y
    }

    // X 轴速度注入状态：声明须先于 snapMiniPlayerToNearestHorizontalEdge
    // 等局部函数，Kotlin 局部函数不能前向引用局部 val。
    val offsetXAnimatable = remember { Animatable(targetOffsetX) }
    var pendingSnapVelocityX by remember { mutableFloatStateOf(0f) }
    val dragVelocityTracker = remember { VelocityTracker() }

    fun snapMiniPlayerToNearestHorizontalEdge() {
        offsetX = if (offsetX < screenWidthPx / 2 - miniPlayerWidthPx / 2) {
            paddingPx
        } else {
            screenWidthPx - miniPlayerWidthPx - paddingPx
        }
        clampCurrentOffset()
    }

    /** 读取累积的手势速度并转交给 X 轴贴边动画，随后清空累积。 */
    fun handOffDragVelocityToSnapAnimation() {
        pendingSnapVelocityX = dragVelocityTracker.calculateVelocity().x
        dragVelocityTracker.resetTracking()
    }

    fun stashMiniPlayer(side: com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition) {
        stashSide = side
        val stashHeightPx = with(density) { layoutPolicy.stashedHeightDp.dp.toPx() }
        val minY = paddingPx + dragTopInsetPx
        val maxY = (screenHeightPx - stashHeightPx - paddingPx - dragBottomInsetPx).coerceAtLeast(minY)
        stashedOffsetY = (offsetY + (miniPlayerHeightPx - stashHeightPx) / 2f).coerceIn(minY, maxY)
        rawDragOffsetX = null
        edgeStashSide = null
        isDraggingPosition = false
        showControls = false
        pendingSnapVelocityX = 0f
        dragVelocityTracker.resetTracking()
        isStashed = true
    }

    fun beginPositionDrag() {
        rawDragOffsetX = offsetX
        edgeStashSide = null
        isDraggingPosition = true
        showControls = true
        lastInteractionTime = System.currentTimeMillis()
        dragVelocityTracker.resetTracking()
    }

    fun finishPositionDrag(cancelled: Boolean) {
        val side = if (cancelled) null else edgeStashSide
        isDraggingPosition = false
        rawDragOffsetX = null
        edgeStashSide = null
        if (side != null) {
            stashMiniPlayer(if (side == MiniPlayerEdgeStashSide.LEFT) {
                com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.LEFT
            } else com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.RIGHT)
        } else if (cancelled) {
            dragVelocityTracker.resetTracking()
            pendingSnapVelocityX = 0f
            clampCurrentOffset()
        } else {
            handOffDragVelocityToSnapAnimation()
            snapMiniPlayerToNearestHorizontalEdge()
        }
        lastInteractionTime = System.currentTimeMillis()
    }

    // Pointer owners survive recomposition: geometry and navigation callbacks must stay current.
    val latestMoveMiniPlayer by rememberUpdatedState(::moveMiniPlayerBy)
    val latestBeginPositionDrag by rememberUpdatedState(::beginPositionDrag)
    val latestFinishPositionDrag by rememberUpdatedState(::finishPositionDrag)
    val latestClampMiniPlayer by rememberUpdatedState(::clampCurrentOffset)
    val latestExpand by rememberUpdatedState(onExpandClick)
    val latestMiniPlayerWidthPx by rememberUpdatedState(miniPlayerWidthPx)

    LaunchedEffect(
        miniPlayerWidthPx,
        miniPlayerHeightPx,
        screenWidthPx,
        screenHeightPx
    ) {
        latestClampMiniPlayer()
        val minY = paddingPx + dragTopInsetPx
        val maxY = (screenHeightPx - with(density) { layoutPolicy.stashedHeightDp.dp.toPx() } - paddingPx - dragBottomInsetPx)
            .coerceAtLeast(minY)
        stashedOffsetY = stashedOffsetY.coerceIn(minY, maxY)
    }

    LaunchedEffect(
        targetOffsetX,
        isDraggingPosition,
        isResizing,
        isStashed,
        reduceMotion
    ) {
        if (isDraggingPosition || isResizing || reduceMotion) {
            offsetXAnimatable.snapTo(targetOffsetX)
            pendingSnapVelocityX = 0f
        } else {
            val initialVelocity = pendingSnapVelocityX
            pendingSnapVelocityX = 0f
            offsetXAnimatable.animateTo(
                targetValue = targetOffsetX,
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
                initialVelocity = initialVelocity,
            )
        }
    }
    val animatedOffsetX = offsetXAnimatable.value
    val animatedOffsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = if (isDraggingPosition || isResizing || reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
        },
        label = "offsetY"
    )

    // 回报小窗屏幕边界，供 PIP sourceRectHint 做无缝过渡。
    LaunchedEffect(
        animatedOffsetX,
        animatedOffsetY,
        miniPlayerWidthPx,
        miniPlayerHeightPx,
        suppressContentForPip
    ) {
        miniPlayerManager.miniPlayerSourceBoundsPx = if (suppressContentForPip) {
            null
        } else {
            android.graphics.Rect(
                animatedOffsetX.roundToInt(),
                animatedOffsetY.roundToInt(),
                (animatedOffsetX + miniPlayerWidthPx).roundToInt(),
                (animatedOffsetY + miniPlayerHeightPx).roundToInt(),
            )
        }
    }

    val visibilitySlideSpec: FiniteAnimationSpec<IntOffset> =
        iosMorphTween(MINI_PLAYER_VISIBILITY_DURATION_MILLIS)
    val visibilityFadeSpec: FiniteAnimationSpec<Float> =
        iosMorphTween(if (reduceMotion) 160 else MINI_PLAYER_VISIBILITY_DURATION_MILLIS)
    val enterTransition = if (reduceMotion) {
        fadeIn(animationSpec = visibilityFadeSpec)
    } else {
        (when (cardPosition) {
            com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.LEFT ->
                slideInHorizontally(animationSpec = visibilitySlideSpec, initialOffsetX = { -it })
            com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.RIGHT ->
                slideInHorizontally(animationSpec = visibilitySlideSpec, initialOffsetX = { it })
            com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.MIDDLE ->
                slideInVertically(animationSpec = visibilitySlideSpec, initialOffsetY = { -it })
        }) + fadeIn(animationSpec = visibilityFadeSpec)
    }
    val exitTransition = if (!miniPlayerManager.shouldAnimateExit) {
        ExitTransition.None
    } else if (reduceMotion) {
        fadeOut(animationSpec = visibilityFadeSpec)
    } else {
        (when (cardPosition) {
            com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.LEFT ->
                slideOutHorizontally(animationSpec = visibilitySlideSpec, targetOffsetX = { -it })
            com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.RIGHT ->
                slideOutHorizontally(animationSpec = visibilitySlideSpec, targetOffsetX = { it })
            com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.MIDDLE ->
                slideOutVertically(animationSpec = visibilitySlideSpec, targetOffsetY = { -it })
        }) + fadeOut(animationSpec = visibilityFadeSpec)
    }

    val edgePreviewSide = if (isDraggingPosition && !isStashed) rawDragOffsetX?.let { raw ->
        when {
            raw < paddingPx -> MiniPlayerEdgeStashSide.LEFT
            raw > screenWidthPx - miniPlayerWidthPx - paddingPx -> MiniPlayerEdgeStashSide.RIGHT
            else -> null
        }
    } else null
    val edgePreviewProgress = when (edgePreviewSide) {
        MiniPlayerEdgeStashSide.LEFT -> (paddingPx - (rawDragOffsetX ?: paddingPx)) / edgeStashThresholdPx
        MiniPlayerEdgeStashSide.RIGHT -> ((rawDragOffsetX ?: 0f) - (screenWidthPx - miniPlayerWidthPx - paddingPx)) / edgeStashThresholdPx
        null -> 0f
    }.coerceIn(0f, 1f)

    AnimatedVisibility(
        visible = miniPlayerManager.isMiniMode && miniPlayerManager.isActive,
        enter = enterTransition,
        exit = exitTransition,
            modifier = modifier.zIndex(100f)
    ) {
        if (edgePreviewSide != null && edgePreviewProgress > 0.1f && !suppressContentForPip) {
            val previewHeightPx = with(density) { layoutPolicy.stashedHeightDp.dp.toPx() }
            val previewMinY = paddingPx + dragTopInsetPx
            val previewMaxY = (screenHeightPx - previewHeightPx - paddingPx - dragBottomInsetPx).coerceAtLeast(previewMinY)
            MiniPlayerEdgeStashPreview(
                side = edgePreviewSide,
                progress = edgePreviewProgress,
                armed = edgeStashSide != null,
                accentColor = accentColor,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            if (edgePreviewSide == MiniPlayerEdgeStashSide.LEFT) 0 else
                                (screenWidthPx - with(density) { layoutPolicy.stashedWidthDp.dp.toPx() }).roundToInt(),
                            (offsetY + (miniPlayerHeightPx - previewHeightPx) / 2f).coerceIn(previewMinY, previewMaxY).roundToInt(),
                        )
                    }
                    .size(layoutPolicy.stashedWidthDp.dp, layoutPolicy.stashedHeightDp.dp)
                    .zIndex(102f),
            )
        }
        if (showStashHandle && !suppressContentForPip) {
            // [新增] 贴边隐藏的小胶囊视图
            StashedMiniPlayerView(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) }
                    .zIndex(101f)
                    .graphicsLayer { alpha = ((stashProgress.value - 0.35f) / 0.65f).coerceIn(0f, 1f) },
                layoutPolicy = layoutPolicy,
                accentColor = accentColor,
                side = stashSide,
                onUnstash = {
                    isStashed = false
                    // 恢复时，X 坐标应该弹回正常位置
                    offsetX = if (stashSide == com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.LEFT) {
                        paddingPx
                    } else {
                        screenWidthPx - miniPlayerWidthPx - paddingPx
                    }
                    // Y 坐标保持同步
                    offsetY = stashedOffsetY - (miniPlayerHeightPx - with(density) { layoutPolicy.stashedHeightDp.dp.toPx() }) / 2f
                    showControls = true
                    lastInteractionTime = System.currentTimeMillis()
                    latestClampMiniPlayer()
                },
                onDrag = { deltaY ->
                    stashedOffsetY = (stashedOffsetY + deltaY).coerceIn(
                        paddingPx + dragTopInsetPx,
                        (screenHeightPx - with(density) { layoutPolicy.stashedHeightDp.dp.toPx() } - paddingPx - dragBottomInsetPx)
                            .coerceAtLeast(paddingPx + dragTopInsetPx)
                    )
                }
            )
        }
        if (suppressContentForPip) {
            // PIP 占位：保持组合与可见状态，不渲染视频 surface 和控件。
            Box(modifier = Modifier.fillMaxSize())
        } else if (showExpandedPlayer) {
            // 正常播放器视图
            val miniPlayerCornerRadius = shellVisual.cardCornerRadiusDp.dp
            val miniPlayerShape = RoundedCornerShape(miniPlayerCornerRadius)
            AppCard(
                modifier = Modifier
                    .then(autoHide.focusModifier)
                    .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) }
                    .width(miniPlayerWidth)
                    .height(miniPlayerHeight)
                    .onGloballyPositioned { cardCoordinates[0] = it }
                    .graphicsLayer {
                        val progress = stashProgress.value
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = 1f + (layoutPolicy.stashedWidthDp / miniPlayerWidthDpEffective - 1f) * progress
                        scaleY = 1f + (layoutPolicy.stashedHeightDp / miniPlayerHeightDp - 1f) * progress
                        alpha = if (contentHidden) 0f else (1f - progress * 1.6f).coerceIn(0f, 1f)
                    }
                    .shadow(
                        shellVisual.cardShadowDp.dp,
                        miniPlayerShape,
                    )
                    .then(
                        if (shellVisual.cardElevationDp > 0) {
                            // MD3 keeps its existing card elevation; the MIUIX policy resolves to 0.
                            Modifier.shadow(shellVisual.cardElevationDp.dp, miniPlayerShape)
                        } else {
                            Modifier
                        },
                    )
                    .pointerInput(resizeBounds, isStashed, dismissing) {
                        if (isStashed || dismissing) return@pointerInput
                        awaitEachGesture {
                            var isPinching = false
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val activePointers = event.changes.filter { it.pressed }
                                    if (activePointers.size >= 2) {
                                        val zoom = event.calculateZoom()
                                        if (zoom != 1f) {
                                            miniPlayerWidthDp = (miniPlayerWidthDp * zoom).coerceIn(
                                                resizeBounds.minWidthDp,
                                                resizeBounds.maxWidthDp
                                            )
                                        }
                                        isPinching = true
                                        isResizing = true
                                        showControls = true
                                        activePointers.forEach { it.consume() }
                                    } else if (isPinching) {
                                        activePointers.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })

                            } finally {
                                if (isPinching) {
                                    isResizing = false
                                    latestClampMiniPlayer()
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                            }
                        }
                    },
                shape = AppCardShape.Uniform(miniPlayerCornerRadius),
                colors = AppCardDefaults.colors(containerColor = Color.Black),
            ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 视频画面
                player?.let { exoPlayer ->
                    val useTextureSurface = com.android.purebilibili.feature.video.ui.components.rememberCompatibleVideoTextureSurface(
                        exoPlayer, miniPlayerManager.cachedUiState?.currentQuality ?: 0,
                    )
                    key(exoPlayer, useTextureSurface) {
                    AndroidView(
                        factory = { ctx ->
                            com.android.purebilibili.feature.video.ui.components.createVideoPlayerView(ctx, useTextureSurface).apply {
                                playerViewRef[0] = this
                                this.player = exoPlayer
                                useController = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            }
                        },
                        update = { view -> view.player = exoPlayer },
                        onRelease = { view ->
                            if (playerViewRef[0] === view) playerViewRef[0] = null
                            view.player = null
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(shellVisual.cardCornerRadiusDp.dp))
                            //  视频区域：左右滑动调节进度（直播模式禁用）
                            .pointerInput(miniPlayerManager.isLiveMode, miniPlayerWidthPx, duration, isSeekable, touchSlopPx, isStashed, dismissing) {
                                if (isStashed || dismissing) return@pointerInput
                                detectDragGestures(
                                    onDragStart = {
                                        contentDragIntent = MiniPlayerContentDragIntent.UNDECIDED
                                        contentDragTotalX = 0f
                                        contentDragTotalY = 0f
                                        dragProgressDelta = 0f
                                        dragProgressStartPosition = currentPosition.coerceAtLeast(0L)
                                        showControls = true
                                        lastInteractionTime = System.currentTimeMillis()
                                        dragVelocityTracker.resetTracking()
                                    },
                                    onDragEnd = {
                                        when (contentDragIntent) {
                                            MiniPlayerContentDragIntent.SEEK -> {
                                                if (abs(dragProgressDelta) > 10f) {
                                                    val newPosition = resolveMiniPlayerSeekTargetPosition(
                                                        dragStartPositionMs = dragProgressStartPosition,
                                                        dragDeltaPx = dragProgressDelta,
                                                        miniPlayerWidthPx = miniPlayerWidthPx,
                                                        durationMs = duration
                                                    )
                                                    player?.let { seekPlayerFromUserAction(it, newPosition) }
                                                }
                                                isDraggingProgress = false
                                                dragProgressDelta = 0f
                                                dragProgressStartPosition = 0L
                                                dragVelocityTracker.resetTracking()
                                                lastInteractionTime = System.currentTimeMillis()
                                            }
                                            MiniPlayerContentDragIntent.MOVE -> {
                                                latestFinishPositionDrag(false)
                                            }
                                            MiniPlayerContentDragIntent.UNDECIDED -> Unit
                                        }
                                        contentDragIntent = MiniPlayerContentDragIntent.UNDECIDED
                                        contentDragTotalX = 0f
                                        contentDragTotalY = 0f
                                    },
                                    onDragCancel = {
                                        if (isDraggingPosition) latestFinishPositionDrag(true)
                                        isDraggingProgress = false
                                        isDraggingPosition = false
                                        dragProgressDelta = 0f
                                        dragProgressStartPosition = 0L
                                        dragVelocityTracker.resetTracking()
                                        lastInteractionTime = System.currentTimeMillis()
                                        contentDragIntent = MiniPlayerContentDragIntent.UNDECIDED
                                        contentDragTotalX = 0f
                                        contentDragTotalY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragVelocityTracker.addPosition(change.uptimeMillis, change.position)
                                        contentDragTotalX += dragAmount.x
                                        contentDragTotalY += dragAmount.y
                                        if (contentDragIntent == MiniPlayerContentDragIntent.UNDECIDED) {
                                            contentDragIntent = resolveMiniPlayerContentDragIntent(
                                                totalDragX = contentDragTotalX,
                                                totalDragY = contentDragTotalY,
                                                seekEnabled = !miniPlayerManager.isLiveMode && isSeekable,
                                                touchSlopPx = touchSlopPx
                                            )
                                            if (contentDragIntent == MiniPlayerContentDragIntent.SEEK) {
                                                isDraggingProgress = true
                                                dragProgressDelta = 0f
                                                dragProgressStartPosition = currentPosition.coerceAtLeast(0L)
                                                seekPreviewPosition = dragProgressStartPosition
                                            } else if (contentDragIntent == MiniPlayerContentDragIntent.MOVE) {
                                                latestBeginPositionDrag()
                                            }
                                        }

                                        when (contentDragIntent) {
                                            MiniPlayerContentDragIntent.SEEK -> {
                                                dragProgressDelta += dragAmount.x
                                                seekPreviewPosition = resolveMiniPlayerSeekTargetPosition(
                                                    dragStartPositionMs = dragProgressStartPosition,
                                                    dragDeltaPx = dragProgressDelta,
                                                    miniPlayerWidthPx = miniPlayerWidthPx,
                                                    durationMs = duration
                                                )
                                                currentProgress = if (duration > 0L) (seekPreviewPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                                            }
                                            MiniPlayerContentDragIntent.MOVE -> {
                                                latestMoveMiniPlayer(dragAmount.x, dragAmount.y)
                                            }
                                            MiniPlayerContentDragIntent.UNDECIDED -> Unit
                                        }
                                    }
                                )
                            }
                            .pointerInput(isStashed, dismissing) {
                                if (isStashed || dismissing) return@pointerInput
                                detectTapGestures(
                                    onTap = {
                                        showControls = !showControls
                                        if (showControls) {
                                            lastInteractionTime = System.currentTimeMillis()
                                        }
                                    },
                                    onDoubleTap = { latestExpand() }
                                )
                            }
                    )
                    }
                }

                player?.let { currentPlayer ->
                    PlayerBufferingIndicator(player = currentPlayer, modifier = Modifier.align(Alignment.Center))
                }

                //  顶部拖动区域 - 用于移动小窗位置
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .align(Alignment.TopCenter)
                        .pointerInput(isStashed, dismissing) {
                            if (isStashed || dismissing) return@pointerInput
                            detectDragGestures(
                                onDragStart = {
                                    latestBeginPositionDrag()
                                },
                                onDragEnd = {
                                    latestFinishPositionDrag(false)
                                },
                                onDragCancel = {
                                    latestFinishPositionDrag(true)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragVelocityTracker.addPosition(change.uptimeMillis, change.position)
                                    latestMoveMiniPlayer(dragAmount.x, dragAmount.y)
                                }
                            )
                        }
                ) {
                    if (chrome.showHeaderChrome) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = 0.6f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Header content and actions share one measured row; badges cannot cover titles.
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (miniPlayerWidthDpEffective >= 200f) {
                                AppText(
                                    text = miniPlayerManager.currentTitle,
                                    color = Color.White,
                                    fontSize = layoutPolicy.titleFontSp.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                                )
                            } else Spacer(Modifier.weight(1f))
                            com.android.purebilibili.core.ui.components.AppGlassMenuAnchor(onOpen = { moreExpanded = true }) {
                                AppIconButton(onClick = { moreExpanded = true }, modifier = Modifier.size(48.dp)) {
                                    AppIcon(Icons.Outlined.MoreVert, "小窗更多操作", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                AppDropdownMenu(expanded = moreExpanded, onDismissRequest = {
                                    moreExpanded = false
                                    lastInteractionTime = System.currentTimeMillis()
                                }) {
                                    AppDropdownMenuItem(text = { AppText("返回播放详情") }, onClick = {
                                        moreExpanded = false
                                        onExpandClick()
                                    })
                                    AppDropdownMenuItem(text = { AppText("贴边隐藏") }, onClick = {
                                        moreExpanded = false
                                        stashMiniPlayer(if (offsetX + miniPlayerWidthPx / 2 < screenWidthPx / 2) {
                                            com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.LEFT
                                        } else com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.RIGHT)
                                    })
                                    if (onPictureInPictureClick != null) {
                                        AppDropdownMenuItem(text = { AppText("系统画中画") }, onClick = {
                                            moreExpanded = false
                                            onPictureInPictureClick()
                                        })
                                    }
                                    if (miniPlayerWidthDpEffective < 112f) {
                                        AppDropdownMenuItem(text = { AppText("关闭小窗") }, onClick = {
                                            moreExpanded = false
                                            dismissMiniPlayer()
                                        })
                                    }
                                }
                            }
                            if (miniPlayerWidthDpEffective >= 112f) {
                                AppIconButton(onClick = { dismissMiniPlayer() }, modifier = Modifier.size(48.dp)) {
                                    AppIcon(clearIcon, "关闭小窗", tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                // 控制层 - 播放按钮等（位于中间和底部）
                if (chrome.showCenterControls) {
                    // 底部渐变
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(layoutPolicy.controlsGradientHeightDp.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                                )
                            )
                    )

                    // 缓冲期间也保留暂停入口，透明底让加载反馈仍可见。
                    AppSurface(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            player?.let { com.android.purebilibili.feature.video.usecase.togglePlayerPlaybackFromUserAction(it) }
                        },
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                        shape = CircleShape,
                        color = if (isBuffering) Color.Transparent else Color.Black.copy(alpha = 0.55f)
                    ) {
                        AppIcon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(12.dp)
                                .size(24.dp)
                        )
                    }

                    // 底部提示
                    if (chrome.showSeekHint) {
                        AppSurface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = layoutPolicy.seekHintBottomPaddingDp.dp),
                            shape = RoundedCornerShape(shellVisual.seekHintCornerRadiusDp.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            val timeText = "${formatMiniTime(seekPreviewPosition)} / ${formatMiniTime(duration)}"
                            AppText(
                                text = timeText,
                                color = Color.White,
                                fontSize = layoutPolicy.seekHintFontSp.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(
                                    horizontal = layoutPolicy.seekHintHorizontalPaddingDp.dp,
                                    vertical = layoutPolicy.seekHintVerticalPaddingDp.dp
                                )
                            )
                        }
                    }

                }


                // 进度条 - 仅视频模式显示（直播没有进度）
                if (!miniPlayerManager.isLiveMode && chrome.showProgressBar) {
                AppLinearProgressIndicator(
                    progress = { currentProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layoutPolicy.progressBarHeightDp.dp)
                        .align(Alignment.BottomCenter)
                        .alpha(chrome.progressBarAlpha)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = shellVisual.cardCornerRadiusDp.dp,
                                bottomEnd = shellVisual.cardCornerRadiusDp.dp
                            )
                        ),
                    color = if (isDraggingProgress) Color.Yellow else accentColor,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                }

                if (chrome.showResizeHandle && miniPlayerWidthDpEffective >= 180f && !dismissing) {
                    AppSurface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(48.dp)
                            .pointerInput(resizeBounds, miniPlayerAspectRatio) {
                                detectDragGestures(
                                    onDragStart = {
                                        isResizing = true
                                        showControls = true
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onDragEnd = {
                                        isResizing = false
                                        latestClampMiniPlayer()
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onDragCancel = {
                                        isResizing = false
                                        latestClampMiniPlayer()
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val currentWidthPx = with(density) { miniPlayerWidthDp.dp.toPx() }
                                        val resizedWidthPx = resolveResizedMiniPlayerWidth(
                                            currentWidthPx = currentWidthPx,
                                            dragDeltaX = dragAmount.x * currentWidthPx / latestMiniPlayerWidthPx.coerceAtLeast(1f),
                                            dragDeltaY = dragAmount.y * currentWidthPx / latestMiniPlayerWidthPx.coerceAtLeast(1f),
                                            aspectRatio = miniPlayerAspectRatio,
                                            minWidthPx = with(density) { resizeBounds.minWidthDp.dp.toPx() },
                                            maxWidthPx = with(density) { resizeBounds.maxWidthDp.dp.toPx() }
                                        )
                                        miniPlayerWidthDp = with(density) { resizedWidthPx.toDp().value }
                                    }
                                )
                            },
                        shape = AppShapes.diagonalTopStartBottomEnd(
                            topStart = AppShapes.containerCornerDp(ContainerLevel.Card),
                            bottomEnd = shellVisual.cardCornerRadiusDp.dp,
                        ),
                        color = Color.Black.copy(alpha = 0.35f)
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.OpenInFull,
                            contentDescription = "拖动调整小窗大小",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(15.dp)
                        )
                    }
                }
            }
        }
    }
}
}

/** Non-interactive edge preview shares the collapsed handle's silhouette. */
@Composable
private fun MiniPlayerEdgeStashPreview(
    side: MiniPlayerEdgeStashSide,
    progress: Float,
    armed: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val left = side == MiniPlayerEdgeStashSide.LEFT
    Box(modifier.graphicsLayer {
        alpha = 0.3f + 0.7f * progress
        scaleX = 0.88f + 0.12f * progress
        scaleY = 0.88f + 0.12f * progress
        transformOrigin = TransformOrigin(if (left) 0f else 1f, 0.5f)
    }) {
        MiniPlayerEdgeHandle(
            left = left,
            highlighted = armed,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize(),
        )
        if (armed) {
            AppSurface(
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset(x = if (left) 48.dp else (-96).dp)
                    // 提示框独立测量，不受侧边拉手的窄宽度约束。
                    .wrapContentSize(Alignment.CenterStart, unbounded = true)
                    .width(88.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.78f),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText(
                        text = "松手收起",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * [新增] 贴边隐藏的小胶囊视图
 */
@Composable
private fun StashedMiniPlayerView(
    modifier: Modifier,
    layoutPolicy: MiniPlayerOverlayLayoutPolicy,
    accentColor: Color,
    side: com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition,
    onUnstash: () -> Unit,
    onDrag: (Float) -> Unit
) {
    val latestOnDrag by rememberUpdatedState(onDrag)
    val isLeft = side == com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.LEFT
    Box(
        modifier = modifier
            .width(layoutPolicy.stashedWidthDp.dp)
            .height(layoutPolicy.stashedHeightDp.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    latestOnDrag(dragAmount.y)
                }
            }
            .clickable(role = Role.Button, onClickLabel = "恢复小窗", onClick = onUnstash)
            .semantics { contentDescription = "恢复小窗，可上下拖动" },
    ) {
        MiniPlayerEdgeHandle(
            left = isLeft,
            highlighted = false,
            accentColor = accentColor,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Visible strip is narrower than its touch region and continues beyond the physical screen edge. */
@Composable
private fun MiniPlayerEdgeHandle(
    left: Boolean,
    highlighted: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val outline = AppSurfaceTokens.onSurface().copy(alpha = if (highlighted) 0.25f else 0.12f)
    val gripColor = if (highlighted) accentColor else AppSurfaceTokens.onSurfaceSecondary()
    Box(modifier) {
        AppSurface(
            modifier = Modifier
                .align(if (left) Alignment.CenterStart else Alignment.CenterEnd)
                .offset(x = if (left) (-8).dp else 8.dp)
                .size(width = 22.dp, height = 44.dp),
            shape = RoundedCornerShape(14.dp),
            color = AppSurfaceTokens.surfaceContainerHigh().copy(alpha = 0.96f),
            border = BorderStroke(0.8.dp, outline),
            shadowElevation = 2.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 2.dp, height = 16.dp)
                    .background(gripColor.copy(alpha = 0.8f), RoundedCornerShape(1.dp)))
            }
        }
    }
}

private fun formatMiniTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
