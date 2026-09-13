// 文件路径: feature/video/MiniPlayerOverlay.kt
package com.android.purebilibili.feature.video.ui.overlay
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.core.util.Logger

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.ui.components.AppCard
import com.android.purebilibili.core.ui.components.AppCardDefaults
import com.android.purebilibili.core.ui.components.AppCardShape
import com.android.purebilibili.core.ui.components.AppLinearProgressIndicator
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.feature.video.usecase.seekPlayerFromUserAction
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.motion.iosMorphTween
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion

private const val TAG = "MiniPlayerOverlay"
private const val AUTO_HIDE_DELAY_MS = 3000L
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
    modifier: Modifier = Modifier
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
        MaterialTheme.colorScheme.primary
    }

    val padding = layoutPolicy.outerPaddingDp.dp
    val headerHeight = layoutPolicy.headerHeightDp.dp // 顶部可拖动区域高度
    val touchSlopPx = LocalViewConfiguration.current.touchSlop
    val resizeBounds = remember(configuration.screenWidthDp, configuration.screenHeightDp, layoutPolicy) {
        resolveMiniPlayerResizeBounds(
            defaultWidthDp = layoutPolicy.miniPlayerWidthDp,
            defaultHeightDp = layoutPolicy.miniPlayerHeightDp,
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            outerPaddingDp = layoutPolicy.outerPaddingDp,
            topInsetDp = layoutPolicy.dragTopInsetDp,
            bottomInsetDp = layoutPolicy.dragBottomInsetDp
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
    val miniPlayerAspectRatio = remember(layoutPolicy) {
        layoutPolicy.miniPlayerWidthDp.toFloat() / layoutPolicy.miniPlayerHeightDp.coerceAtLeast(1)
    }
    val miniPlayerHeightDp = miniPlayerWidthDp / miniPlayerAspectRatio
    val miniPlayerWidth = miniPlayerWidthDp.dp
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
    var offsetX by remember(initialOverlayOffset) { mutableFloatStateOf(initialOverlayOffset.x) }
    var offsetY by remember(initialOverlayOffset) { mutableFloatStateOf(initialOverlayOffset.y) }
    
    // 控制按钮显示状态
    var showControls by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // [新增] 贴边隐藏状态
    var isStashed by remember { mutableStateOf(false) }
    // 记录隐藏在哪一侧 (Left or Right), 默认为 Right，后续根据位置计算
    var stashSide by remember { mutableStateOf(com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.RIGHT) }
    
    // [新增] Stashed 状态下的 Y 轴偏移量（允许在隐藏时上下拖动）
    var stashedOffsetY by remember(initialOverlayOffset) { mutableFloatStateOf(initialOverlayOffset.y) }

    
    // 进度拖动状态
    var isDraggingProgress by remember { mutableStateOf(false) }
    var dragProgressDelta by remember { mutableFloatStateOf(0f) }
    var dragProgressStartPosition by remember { mutableLongStateOf(0L) }
    var seekPreviewPosition by remember { mutableLongStateOf(0L) }
    
    // 位置拖动状态
    var isDraggingPosition by remember { mutableStateOf(false) }
    var isResizing by remember { mutableStateOf(false) }
    var contentDragIntent by remember { mutableStateOf(MiniPlayerContentDragIntent.UNDECIDED) }
    var contentDragTotalX by remember { mutableFloatStateOf(0f) }
    var contentDragTotalY by remember { mutableFloatStateOf(0f) }
    val chrome = resolveMiniPlayerOverlayChrome(
        showControls = showControls,
        isDraggingProgress = isDraggingProgress,
        isDraggingPosition = isDraggingPosition,
        isResizing = isResizing
    )
    
    // 播放器状态
    //  [修复] 在退出动画期间保持旧 Player 引用，防止画面跳变
    // 当 isMiniMode 为 false (正在退出) 时，不再更新 player，保持最后一帧画面
    val rawPlayer = miniPlayerManager.player
    var player by remember { mutableStateOf(rawPlayer) }
    
    if (miniPlayerManager.isMiniMode && rawPlayer != null) {
        player = rawPlayer
    }
    
    var isPlaying by remember { mutableStateOf(player?.isPlaying ?: false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    val shouldPollProgress = shouldPollMiniPlayerProgress(
        playerExists = player != null,
        hostLifecycleStarted = hostLifecycleStarted,
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
            isPlaying = currentIsPlaying
            duration = currentPlayer.duration.coerceAtLeast(1L)
            currentPosition = currentPlayer.currentPosition
            if (!isDraggingProgress) {
                currentProgress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            }
            delay(resolveMiniPlayerPollingIntervalMs(isPlaying = currentIsPlaying))
        }
    }
    
    // 自动隐藏控制按钮
    LaunchedEffect(showControls, lastInteractionTime) {
        if (showControls && !isDraggingPosition && !isDraggingProgress && !isResizing) {
            delay(AUTO_HIDE_DELAY_MS)
            if (System.currentTimeMillis() - lastInteractionTime >= AUTO_HIDE_DELAY_MS) {
                showControls = false
            }
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
        val clamped = clampMiniPlayerOverlayOffset(
            offsetX = offsetX + deltaX,
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

    fun snapMiniPlayerToNearestHorizontalEdge() {
        offsetX = if (offsetX < screenWidthPx / 2 - miniPlayerWidthPx / 2) {
            paddingPx
        } else {
            screenWidthPx - miniPlayerWidthPx - paddingPx
        }
        clampCurrentOffset()
    }

    LaunchedEffect(
        miniPlayerWidthPx,
        miniPlayerHeightPx,
        screenWidthPx,
        screenHeightPx
    ) {
        clampCurrentOffset()
    }

    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = if (isDraggingPosition || isResizing || reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
        },
        label = "offsetX"
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = targetOffsetY,
        animationSpec = if (isDraggingPosition || isResizing || reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)
        },
        label = "offsetY"
    )

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

    AnimatedVisibility(
        visible = miniPlayerManager.isMiniMode && miniPlayerManager.isActive,
        enter = enterTransition,
        exit = exitTransition,
            modifier = modifier.zIndex(100f)
    ) {
        if (isStashed) {
            // [新增] 贴边隐藏的小胶囊视图
            StashedMiniPlayerView(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) }
                    .zIndex(101f),
                layoutPolicy = layoutPolicy,
                shellVisual = shellVisual,
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
                    offsetY = stashedOffsetY
                    clampCurrentOffset()
                },
                onDrag = { deltaY ->
                    stashedOffsetY = (stashedOffsetY + deltaY).coerceIn(
                        paddingPx + dragTopInsetPx,
                        screenHeightPx - with(density) { layoutPolicy.stashedHeightDp.dp.toPx() } - paddingPx - dragBottomInsetPx
                    )
                }
            )
        } else {
            // 正常播放器视图
            val miniPlayerCornerRadius = shellVisual.cardCornerRadiusDp.dp
            val miniPlayerShape = RoundedCornerShape(miniPlayerCornerRadius)
            AppCard(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetX.roundToInt(), animatedOffsetY.roundToInt()) }
                    .width(miniPlayerWidth)
                    .height(miniPlayerHeight)
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
                    ),
                shape = AppCardShape.Uniform(miniPlayerCornerRadius),
                colors = AppCardDefaults.colors(containerColor = Color.Black),
            ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 视频画面
                player?.let { exoPlayer ->
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = exoPlayer
                                useController = false
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = { view -> view.player = exoPlayer },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(shellVisual.cardCornerRadiusDp.dp))
                            //  视频区域：左右滑动调节进度（直播模式禁用）
                            .pointerInput(miniPlayerManager.isLiveMode, miniPlayerWidthPx, duration, touchSlopPx) {
                                detectDragGestures(
                                    onDragStart = {
                                        contentDragIntent = MiniPlayerContentDragIntent.UNDECIDED
                                        contentDragTotalX = 0f
                                        contentDragTotalY = 0f
                                        dragProgressDelta = 0f
                                        dragProgressStartPosition = currentPosition.coerceAtLeast(0L)
                                        showControls = true
                                        lastInteractionTime = System.currentTimeMillis()
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
                                                lastInteractionTime = System.currentTimeMillis()
                                            }
                                            MiniPlayerContentDragIntent.MOVE -> {
                                                isDraggingPosition = false
                                                snapMiniPlayerToNearestHorizontalEdge()
                                                lastInteractionTime = System.currentTimeMillis()
                                            }
                                            MiniPlayerContentDragIntent.UNDECIDED -> Unit
                                        }
                                        contentDragIntent = MiniPlayerContentDragIntent.UNDECIDED
                                        contentDragTotalX = 0f
                                        contentDragTotalY = 0f
                                    },
                                    onDragCancel = {
                                        isDraggingProgress = false
                                        isDraggingPosition = false
                                        dragProgressDelta = 0f
                                        dragProgressStartPosition = 0L
                                        lastInteractionTime = System.currentTimeMillis()
                                        contentDragIntent = MiniPlayerContentDragIntent.UNDECIDED
                                        contentDragTotalX = 0f
                                        contentDragTotalY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        contentDragTotalX += dragAmount.x
                                        contentDragTotalY += dragAmount.y
                                        if (contentDragIntent == MiniPlayerContentDragIntent.UNDECIDED) {
                                            contentDragIntent = resolveMiniPlayerContentDragIntent(
                                                totalDragX = contentDragTotalX,
                                                totalDragY = contentDragTotalY,
                                                seekEnabled = !miniPlayerManager.isLiveMode,
                                                touchSlopPx = touchSlopPx
                                            )
                                            if (contentDragIntent == MiniPlayerContentDragIntent.SEEK) {
                                                isDraggingProgress = true
                                                dragProgressDelta = 0f
                                                dragProgressStartPosition = currentPosition.coerceAtLeast(0L)
                                                seekPreviewPosition = dragProgressStartPosition
                                            } else if (contentDragIntent == MiniPlayerContentDragIntent.MOVE) {
                                                isDraggingPosition = true
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
                                                currentProgress = (seekPreviewPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                            }
                                            MiniPlayerContentDragIntent.MOVE -> {
                                                moveMiniPlayerBy(dragAmount.x, dragAmount.y)
                                            }
                                            MiniPlayerContentDragIntent.UNDECIDED -> Unit
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        showControls = !showControls
                                        if (showControls) {
                                            lastInteractionTime = System.currentTimeMillis()
                                        }
                                    },
                                    onDoubleTap = { onExpandClick() }
                                )
                            }
                    )
                }

                //  顶部拖动区域 - 用于移动小窗位置
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .align(Alignment.TopCenter)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    isDraggingPosition = true
                                    showControls = true
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onDragEnd = {
                                    isDraggingPosition = false
                                    snapMiniPlayerToNearestHorizontalEdge()
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onDragCancel = {
                                    isDraggingPosition = false
                                    lastInteractionTime = System.currentTimeMillis()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    moveMiniPlayerBy(dragAmount.x, dragAmount.y)
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

                        // 标题
                        AppText(
                            text = miniPlayerManager.currentTitle,
                            color = Color.White,
                            fontSize = layoutPolicy.titleFontSp.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(
                                    start = layoutPolicy.titleStartPaddingDp.dp,
                                    end = layoutPolicy.titleEndPaddingDp.dp
                                )
                        )

                        //  右上角按钮组
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = layoutPolicy.headerButtonRowEndPaddingDp.dp),
                            horizontalArrangement = Arrangement.spacedBy(layoutPolicy.headerButtonSpacingDp.dp)
                        ) {
                            // [新增] 贴边隐藏按钮
                            AppSurface(
                                onClick = {
                                    // 计算最近的边
                                    val centerX = offsetX + miniPlayerWidthPx / 2
                                    stashSide = if (centerX < screenWidthPx / 2) {
                                        com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.LEFT
                                    } else {
                                        com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.RIGHT
                                    }
                                    stashedOffsetY = offsetY
                                    isStashed = true
                                },
                                modifier = Modifier.size(layoutPolicy.headerButtonSizeDp.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                AppIcon(
                                    imageVector = Icons.Outlined.Remove, // 使用 Remove 图标作为隐藏/最小化
                                    contentDescription = "隐藏",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(layoutPolicy.headerButtonIconPaddingDp.dp)
                                        .size(layoutPolicy.headerButtonIconSizeDp.dp)
                                )
                            }

                            // 展开按钮
                            if (onPictureInPictureClick != null) {
                                AppSurface(
                                    onClick = onPictureInPictureClick,
                                    modifier = Modifier.size(layoutPolicy.headerButtonSizeDp.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.5f)
                                ) {
                                    AppIcon(
                                        imageVector = Icons.Outlined.PictureInPictureAlt,
                                        contentDescription = "切换到画中画",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(layoutPolicy.headerButtonIconPaddingDp.dp)
                                            .size(layoutPolicy.headerButtonIconSizeDp.dp)
                                    )
                                }
                            }

                            // 展开按钮
                            AppSurface(
                                onClick = { onExpandClick() },
                                modifier = Modifier.size(layoutPolicy.headerButtonSizeDp.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f)
                            ) {
                                AppIcon(
                                    imageVector = Icons.Outlined.FullscreenExit,
                                    contentDescription = "展开",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(layoutPolicy.headerButtonIconPaddingDp.dp)
                                        .size(layoutPolicy.headerButtonIconSizeDp.dp)
                                )
                            }

                            // 关闭按钮
                            AppSurface(
                                onClick = { miniPlayerManager.dismiss() },
                                modifier = Modifier.size(layoutPolicy.headerButtonSizeDp.dp),
                                shape = CircleShape,
                                color = com.android.purebilibili.core.theme.iOSRed.copy(alpha = 0.7f)
                            ) {
                                AppIcon(
                                    imageVector = clearIcon,
                                    contentDescription = "关闭",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .padding(layoutPolicy.headerButtonIconPaddingDp.dp)
                                        .size(layoutPolicy.headerButtonIconSizeDp.dp)
                                )
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

                    // 播放/暂停按钮
                    AppSurface(
                        onClick = { 
                            lastInteractionTime = System.currentTimeMillis()
                            player?.let { if (it.isPlaying) it.pause() else it.play() }
                        },
                        modifier = Modifier.align(Alignment.Center),
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.9f)
                    ) {
                        AppIcon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(layoutPolicy.centerPlayIconPaddingDp.dp)
                                .size(layoutPolicy.centerPlayIconSizeDp.dp)
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
                    } else if (chrome.showDragHint) {
                        AppText(
                            text = if (miniPlayerManager.isLiveMode) "拖动小窗移动 | 双击展开" else "拖动小窗移动 | 左右滑动调进度",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = layoutPolicy.dragHintFontSp.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = layoutPolicy.dragHintBottomPaddingDp.dp)
                        )
                    }
                    
                    // 📺 [新增] 直播角标
                    if (miniPlayerManager.isLiveMode) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 8.dp, top = 8.dp)
                                .background(
                                    color = Color(0xFFFF4444).copy(alpha = 0.9f),
                                    shape = AppShapes.container(ContainerLevel.Tag)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            AppText(
                                text = "直播",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
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

                if (chrome.showResizeHandle) {
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
                                        clampCurrentOffset()
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onDragCancel = {
                                        isResizing = false
                                        clampCurrentOffset()
                                        lastInteractionTime = System.currentTimeMillis()
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val currentWidthPx = with(density) { miniPlayerWidthDp.dp.toPx() }
                                        val resizedWidthPx = resolveResizedMiniPlayerWidth(
                                            currentWidthPx = currentWidthPx,
                                            dragDeltaX = dragAmount.x,
                                            dragDeltaY = dragAmount.y,
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
                            imageVector = Icons.Outlined.FullscreenExit,
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

/**
 * [新增] 贴边隐藏的小胶囊视图
 */
@Composable
private fun StashedMiniPlayerView(
    modifier: Modifier,
    layoutPolicy: MiniPlayerOverlayLayoutPolicy,
    shellVisual: MiniPlayerOverlayShellVisual,
    accentColor: Color,
    side: com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition,
    onUnstash: () -> Unit,
    onDrag: (Float) -> Unit
) {
    val isLeft = side == com.android.purebilibili.core.util.CardPositionManager.CardHorizontalPosition.LEFT
    // 形状：贴边的一侧是平的，另一侧是圆的
    val stashedCorner =
        shellVisual.cardCornerRadiusDp.dp + layoutPolicy.stashedSideCornerExtraDp.dp
    val shape = if (isLeft) {
        AppShapes.endRounded(stashedCorner)
    } else {
        AppShapes.startRounded(stashedCorner)
    }

    AppSurface(
        modifier = modifier
            .width(layoutPolicy.stashedWidthDp.dp)
            .height(layoutPolicy.stashedHeightDp.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    }
                )
        },
        shape = shape,
        color = accentColor.copy(alpha = 0.9f),
        shadowElevation = if (shellVisual.useThemePrimaryAccent) {
            (layoutPolicy.stashedShadowDp * 0.55f).dp
        } else {
            layoutPolicy.stashedShadowDp.dp
        },
        onClick = onUnstash
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppIcon(
                imageVector = if (isLeft) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
                contentDescription = "Show",
                tint = Color.White,
                modifier = Modifier.size(layoutPolicy.stashedIconSizeDp.dp)
            )
        }
    }
}

private fun formatMiniTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
