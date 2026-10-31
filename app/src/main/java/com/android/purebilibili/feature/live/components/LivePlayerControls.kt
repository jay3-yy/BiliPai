package com.android.purebilibili.feature.live.components
import com.android.purebilibili.core.ui.components.appElasticPress
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText

import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.android.purebilibili.core.ui.components.appActionClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.android.purebilibili.feature.video.ui.gesture.detectPlayerTapGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.components.AppWindowAction
import com.android.purebilibili.core.ui.components.appGlassMenuSource
import com.android.purebilibili.core.ui.components.AppWindowActionMenu
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import com.android.purebilibili.core.ui.rememberAppBackIcon
import com.android.purebilibili.core.ui.rememberAppCommentIcon
import com.android.purebilibili.core.ui.rememberAppPlayerChromeProfile
import com.android.purebilibili.core.ui.rememberAppPlayIcon
import com.android.purebilibili.core.ui.rememberAppRefreshIcon
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.feature.live.resolveLiveVisualSpec
import com.android.purebilibili.feature.live.LiveStatusPalette
import com.android.purebilibili.feature.live.LivePlayerGesturePolicy
import com.android.purebilibili.feature.live.shouldToggleLiveFullscreenForDrag
import com.android.purebilibili.feature.video.ui.gesture.GestureLevelKind
import com.android.purebilibili.feature.video.ui.gesture.GestureLevelOverlayHost
import com.android.purebilibili.feature.video.ui.section.VideoGestureMode
import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AspectRatio
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.purebilibili.feature.live.rememberLiveChromePalette
import com.android.purebilibili.feature.live.resolveLivePlayerControlVisualSpec
import com.android.purebilibili.feature.video.ui.overlay.PlayerGlassIconButtonSize
import com.android.purebilibili.feature.video.ui.overlay.PlayerControlGlassDock
import com.android.purebilibili.feature.video.ui.overlay.PlayerControlsBackdropHost
import com.android.purebilibili.feature.video.ui.overlay.LocalPlayerControlGlassEnabled
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.feature.video.ui.components.NativeDanmakuToggleButton
import com.android.purebilibili.feature.video.ui.components.PiliPlusDanmakuSettingsIcon
import com.android.purebilibili.feature.video.ui.components.PiliPlusDanmakuSendIcon
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback

@Composable
private fun LivePlayerIconButton(
    icon: ImageVector?,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    stateDescription: String? = null,
    /** 点击振动档位；null 关闭（如调用方已自行触发更强的状态反馈）。 */
    hapticType: HapticType? = HapticType.LIGHT,
    modifier: Modifier = Modifier,
    glassShape: androidx.compose.ui.graphics.Shape = CircleShape,
    iconContent: (@Composable (Color) -> Unit)? = null,
) {
    require(label.isNotBlank()) { "Live player icon button label must not be blank" }

    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val visualSpec = remember(playerChromeProfile.tabPresentation) {
        resolveLiveVisualSpec(playerChromeProfile.tabPresentation)
    }
    val glass = LocalPlayerControlGlassEnabled.current
    val touchTargetSize = visualSpec.playerButtonTouchTargetDp.dp
    val visualSize = visualSpec.playerButtonVisualSizeDp.dp
    val haptic = rememberHapticFeedback()

    LivePlayerGlassDock(
        enabled = glass,
        shape = glassShape,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .then(if (glass) Modifier.size(PlayerGlassIconButtonSize) else Modifier.size(touchTargetSize))
                .appElasticPress(enabled = enabled && !glass, visualSize = visualSize)
                .clickable(
                    enabled = enabled,
                    role = if (stateDescription == null) Role.Button else Role.Switch,
                    onClickLabel = label,
                    onClick = {
                        hapticType?.let(haptic)
                        onClick()
                    }
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = label
                    if (stateDescription != null) {
                        this.selected = selected
                        this.stateDescription = stateDescription
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AppSurface(
                shape = CircleShape,
                // 未开启仍是可用操作，保持图标对比度；状态由底衬与语义区分。
                color = if (enabled && stateDescription != null && selected) {
                    LiveStatusPalette.MediaContent.copy(alpha = 0.16f)
                } else Color.Transparent,
                modifier = Modifier
                    .size(visualSize)
                    .alpha(if (enabled) 1f else 0.38f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val tint = LiveStatusPalette.MediaContent
                    if (iconContent != null) iconContent(tint) else AppIcon(
                        imageVector = requireNotNull(icon), contentDescription = null,
                        tint = tint, modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** 直播共用控制层：点击显隐、双击播放、亮度/音量、全屏、缩放、锁定与截图。 */

// 手势分区
private enum class LiveGestureZone {
    None,
    Brightness,
    Volume,
    FullscreenToggle
}
@Composable
fun LivePlayerControls(
    isPlaying: Boolean,
    isFullscreen: Boolean,
    showTopBar: Boolean = true,
    title: String,
    subtitle: String = "",
    onPlayPause: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit,
    onOpenRank: () -> Unit = {},
    modifier: Modifier = Modifier,
    // 新增：弹幕开关
    isDanmakuEnabled: Boolean = true,
    onToggleDanmaku: () -> Unit = {},
    onOpenDanmakuSettings: () -> Unit = {},
    danmakuSettingsSource: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
    danmakuSettingsVisible: Boolean = false,
    onOpenBlockSettings: () -> Unit = {},
    // [新增] 刷新
    onRefresh: () -> Unit = {},
    isAudioOnly: Boolean = false,
    onToggleAudioOnly: () -> Unit = {},
    isBackgroundPlaybackEnabled: Boolean = true,
    onToggleBackgroundPlayback: () -> Unit = {},
    onOpenShutdownTimer: () -> Unit = {},
    onOpenPlayerInfo: () -> Unit = {},
    onOpenVolume: () -> Unit = {},
    onOpenPlaybackPreferences: () -> Unit = {},
    onOpenSend: () -> Unit = {},
    showInteractionToggle: Boolean = false,
    interactionWindowVisible: Boolean = false,
    interactionWindowSource: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
    onToggleInteractionWindow: () -> Unit = {},
    videoFitDesc: String = "",
    videoFitOptions: List<AppWindowAction> = emptyList(),
    currentQualityDesc: String = "",
    qualityOptions: List<AppWindowAction> = emptyList(),
    // [新增] 手动线路切换
    onSourceClick: () -> Unit = {},
    videoScale: Float = 1f,
    onScale: (Float) -> Unit = {},
    onResetScale: () -> Unit = {},
    showPipButton: Boolean = false,
    onEnterPip: () -> Unit = {},
    applyTopSystemBarPadding: Boolean = true,
    applyBottomSystemBarPadding: Boolean = true,
    bottomControlsBottomPadding: Dp = AppSpacingTokens.None,
    // [新增] 全屏锁屏：锁定后隐藏控制栏并禁用全部手势，仅保留解锁按钮
    showLockButton: Boolean = false,
    // [新增] 截图当前帧并保存到相册
    onCaptureScreenshot: () -> Unit = {},
    // [新增] 点赞连击
    gesturePolicy: LivePlayerGesturePolicy = LivePlayerGesturePolicy.Shared,
    fullscreenGestureReverse: Boolean = false,
    captureDanmakuTap: (androidx.compose.ui.geometry.Offset) -> com.android.purebilibili.danmaku.engine.DanmakuClickCapture? = { null },
    onDanmakuInteractionEnabledChange: (Boolean) -> Unit = {},
    // 玻璃调用方显式开启，横竖屏共用材质；原生路径沿用既有尺寸和背景。
    glassEnabled: Boolean = false,
    onBackdropRequiredChange: (Boolean) -> Unit = {},
    // 手势按请求意图判定方向，旋转尚未完成时重复上滑不会反向取消全屏。
    fullscreenRequested: Boolean = isFullscreen,
) {
    var controlsWindowOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val latestCaptureDanmakuTap by rememberUpdatedState(captureDanmakuTap)
    var isControlsVisible by remember { mutableStateOf(true) }
    var menuExpanded by remember { mutableStateOf(false) }
    // 全屏锁屏状态（仅 showLockButton 场景生效）
    var isLocked by rememberSaveable { mutableStateOf(false) }
    val latestInteractionEnabledChange by rememberUpdatedState(onDanmakuInteractionEnabledChange)
    DisposableEffect(isLocked) {
        latestInteractionEnabledChange(!isLocked)
        onDispose { latestInteractionEnabledChange(false) }
    }
    val palette = rememberLiveChromePalette()
    val controlVisualSpec = remember { resolveLivePlayerControlVisualSpec() }

    val autoHide = com.android.purebilibili.feature.video.ui.overlay.rememberPlayerControlsAutoHide(3_000L)
    // 自动隐藏控制器（锁定时保持隐藏）
    LaunchedEffect(isControlsVisible, isPlaying, isLocked, menuExpanded, danmakuSettingsVisible, autoHide.timeoutMillis) {
        val timeout = autoHide.timeoutMillis ?: return@LaunchedEffect
        if (isControlsVisible && !isLocked && isPlaying && !menuExpanded && !danmakuSettingsVisible) {
            kotlinx.coroutines.delay(timeout)
            if (autoHide.canHide()) isControlsVisible = false
        }
    }

    // 退出全屏时自动解锁，避免非全屏卡在锁定状态
    LaunchedEffect(isFullscreen) {
        if (!isFullscreen) {
            isLocked = false
            isControlsVisible = true
        }
    }

    // 手势调节状态
    var isGestureVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val backIcon = rememberAppBackIcon()
    val commentIcon = rememberAppCommentIcon()
    val playIcon = rememberAppPlayIcon()
    val refreshIcon = rememberAppRefreshIcon()
    var gestureKind by remember { mutableStateOf(GestureLevelKind.Volume) }
    var gesturePercent by remember { mutableFloatStateOf(0f) }
    // 中部手势按当前全屏状态决定进入或退出。
    var gestureZone by remember { mutableStateOf(LiveGestureZone.None) }
    val latestPlayPause by rememberUpdatedState(onPlayPause)
    val latestToggleFullscreen by rememberUpdatedState(onToggleFullscreen)
    val latestFullscreen by rememberUpdatedState(fullscreenRequested)
    val latestFullscreenGestureReverse by rememberUpdatedState(fullscreenGestureReverse)
    val latestOnScale by rememberUpdatedState(onScale)
    // 锁定/解锁、双击播放与手势切全屏的状态切换反馈；按钮点击由 LivePlayerIconButton 统一触发。
    val stateHaptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.PLAYER_GESTURES)

    // 锁定时控制栏强制隐藏
    val effectiveControlsVisible = (isControlsVisible || interactionWindowVisible) && !isLocked
    // 将需求交给页面控制录制周期；锁定时解锁按钮仍消费玻璃背景。
    SideEffect {
        onBackdropRequiredChange(effectiveControlsVisible || isLocked || isGestureVisible || menuExpanded || danmakuSettingsVisible)
    }

    val glass = glassEnabled && com.android.purebilibili.core.ui.LocalAppThemeConfig.current.liquidGlassEnabled
    PlayerControlsBackdropHost(
        enabled = glass,
        modifier = modifier
            .then(autoHide.focusModifier)
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .onGloballyPositioned { controlsWindowOffset = it.positionInWindow() }
            // 锁定时移除全部手势（单击/双击/亮度/音量拖拽）
            .then(
                if (isLocked) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        latestOnScale(event.calculateZoom())
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        }
                        .pointerInput(gesturePolicy.doubleTapPlayback) {
                            detectPlayerTapGestures(
                                doubleTapSlopPx = 100.dp.toPx(),
                                captureSingleTap = { latestCaptureDanmakuTap(it + controlsWindowOffset) },
                                onTap = { isControlsVisible = !isControlsVisible },
                                onDoubleTap = { if (gesturePolicy.doubleTapPlayback) {
                                    stateHaptic(HapticType.LIGHT)
                                    latestPlayPause()
                                } },
                            )
                        }
                        .pointerInput(gesturePolicy.centerDragFullscreen) {
                            val screenHeight = size.height.toFloat()
                            val screenWidth = size.width.toFloat()

                            // 使用 Float 累积变化量，解决"不跟手"问题
                            var volumeAccumulator = 0f
                            var brightnessAccumulator = 0f
                            var centerDragAccumulator = 0f
                            var centerDragResolved = false

                            var maxVolume = 0

                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    // 三分区：左 1/3 亮度、右 1/3 音量、中间 1/3 上下滑切换全屏
                                    gestureZone = when {
                                        offset.x < screenWidth / 3f -> LiveGestureZone.Brightness
                                        offset.x > 2f * screenWidth / 3f -> LiveGestureZone.Volume
                                        gesturePolicy.centerDragFullscreen -> LiveGestureZone.FullscreenToggle
                                        else -> LiveGestureZone.None
                                    }
                                    centerDragAccumulator = 0f
                                    centerDragResolved = false
                                    when (gestureZone) {
                                        LiveGestureZone.Brightness -> {
                                            // 左侧：亮度
                                            val windowAttr = activity?.window?.attributes?.screenBrightness ?: -1f
                                            brightnessAccumulator = if (windowAttr >= 0) {
                                                windowAttr
                                            } else {
                                                try {
                                                    val sysBrightness = android.provider.Settings.System.getInt(
                                                        context.contentResolver,
                                                        android.provider.Settings.System.SCREEN_BRIGHTNESS
                                                    )
                                                    sysBrightness / 255f
                                                } catch (e: Exception) {
                                                    0.5f
                                                }
                                            }
                                        }
                                        LiveGestureZone.Volume -> {
                                            // 右侧：音量
                                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            volumeAccumulator = currentVol.toFloat()
                                        }
                                        else -> {}
                                    }
                                    isGestureVisible = gestureZone == LiveGestureZone.Brightness ||
                                        gestureZone == LiveGestureZone.Volume
                                },
                                onDragEnd = {
                                    isGestureVisible = false
                                    gestureZone = LiveGestureZone.None
                                },
                                onDragCancel = {
                                    isGestureVisible = false
                                    gestureZone = LiveGestureZone.None
                                },
                                onVerticalDrag = { _, dragAmount ->
                                    // 灵敏度基于屏幕高度: 拖动全屏高度 = 100% 调整
                                    val sensitivity = screenHeight
                                    val delta = -dragAmount / sensitivity

                                    when (gestureZone) {
                                        LiveGestureZone.Brightness -> {
                                            // 调节亮度
                                            // 亮度范围 0.0 ~ 1.0 (增加拖动系数使调节稍快一点，比如 1.5 倍)
                                            val targetBrightness = (brightnessAccumulator + delta * 1.5f).coerceIn(0.01f, 1f)
                                            brightnessAccumulator = targetBrightness // 更新累积值以保持连续性

                                            val lp = activity?.window?.attributes
                                            lp?.screenBrightness = targetBrightness
                                            activity?.window?.attributes = lp

                                            gestureKind = GestureLevelKind.Brightness
                                            gesturePercent = targetBrightness
                                        }
                                        LiveGestureZone.Volume -> {
                                            // 调节音量 (maxVolume 比如 15)
                                            if (maxVolume > 0) {
                                                // 音量需要映射到 0~maxVolume
                                                val targetVolFloat = (volumeAccumulator + delta * maxVolume * 1.2f).coerceIn(0f, maxVolume.toFloat())
                                                volumeAccumulator = targetVolFloat

                                                val newVolInt = targetVolFloat.toInt()
                                                val currentVolInt = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                                                if (newVolInt != currentVolInt) {
                                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolInt, 0)
                                                    // 注意：不要在这里重置 volumeAccumulator，否则会丢失小数部分导致卡顿
                                                }

                                                val volumePercent = if (maxVolume > 0) {
                                                    newVolInt.toFloat() / maxVolume.toFloat()
                                                } else {
                                                    0f
                                                }
                                                gestureKind = GestureLevelKind.Volume
                                                gesturePercent = volumePercent
                                            }
                                        }
                                        LiveGestureZone.FullscreenToggle -> {
                                            centerDragAccumulator += dragAmount
                                            val threshold = 2.5.dp.toPx()
                                            if (!centerDragResolved && kotlin.math.abs(centerDragAccumulator) > threshold) {
                                                centerDragResolved = true
                                                if (shouldToggleLiveFullscreenForDrag(
                                                        dragY = centerDragAccumulator,
                                                        threshold = threshold,
                                                        isFullscreen = latestFullscreen,
                                                        reverse = latestFullscreenGestureReverse,
                                                    )
                                                ) {
                                                    stateHaptic(HapticType.MEDIUM)
                                                    latestToggleFullscreen()
                                                }
                                            }
                                        }
                                        LiveGestureZone.None -> {}
                                    }
                                }
                            )
                        }
                }
            )
    ) {
        // 亮度、音量与普通视频共用同一反馈入口；全屏使用完整玻璃轨道，内嵌播放使用紧凑尺寸。
        GestureLevelOverlayHost(
            visible = isGestureVisible,
            compact = if (glass) !isFullscreen else true,
            mode = if (gestureKind == GestureLevelKind.Brightness) {
                VideoGestureMode.Brightness
            } else {
                VideoGestureMode.Volume
            },
            percent = gesturePercent,
            glassBackdrop = com.android.purebilibili.feature.video.ui.overlay.LocalPlayerPageBackdrop.current
                .takeIf { glass },
            glassEnabled = glass,
            // 44dp 提示与顶部 34dp 玻璃按钮共用中心线（顶部间距 13dp）。
            glassTopInset = if (isFullscreen) 8.dp else 16.dp,
            glassApplyTopSystemInsets = applyTopSystemBarPadding,
            glassTopSystemInsets = WindowInsets.statusBars,
            glassContentColorOverride = Color.White,
        )

        // 横竖屏共用按钮顺序；短窗口仅把放不下的文字工具收进同一菜单。
        AnimatedVisibility(
            visible = showTopBar && effectiveControlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            BoxWithConstraints {
                val compactTop = maxWidth < (600 * androidx.compose.ui.platform.LocalDensity.current.fontScale).dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!glass) Modifier.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))) else Modifier)
                        .then(if (applyTopSystemBarPadding) Modifier.statusBarsPadding() else Modifier)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (glass) 6.dp else 0.dp),
                ) {
                    if (isFullscreen) LivePlayerIconButton(backIcon, "返回", false, true, onBack, glassShape = CircleShape)
                    Column(Modifier.weight(1f).padding(end = 10.dp)) {
                        AppText(title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isFullscreen && subtitle.isNotBlank()) {
                            AppText(subtitle, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.appActionClickable(onClick = onOpenRank))
                        }
                    }
                    if (isFullscreen && !compactTop) {
                        LivePlayerStatus(Modifier.padding(end = 10.dp))
                    }
                    if (showInteractionToggle) LivePlayerIconButton(
                        Icons.Outlined.ChatBubbleOutline, "弹幕互动窗", interactionWindowVisible, true,
                        onToggleInteractionWindow, stateDescription = if (interactionWindowVisible) "已打开" else "已关闭",
                        modifier = if (glass && interactionWindowSource != null) {
                            Modifier.appGlassMenuSource(interactionWindowSource, onToggleInteractionWindow)
                        } else Modifier,
                    )
                    if (!compactTop && showPipButton) LivePlayerIconButton(Icons.Outlined.PictureInPictureAlt, "画中画", false, true, onEnterPip, glassShape = CircleShape)
                    if (!compactTop) LivePlayerIconButton(Icons.Outlined.MusicNote, "仅播放音频", isAudioOnly, true, onToggleAudioOnly, if (isAudioOnly) "已开启" else "已关闭", glassShape = CircleShape)
                    if (!compactTop) LivePlayerIconButton(Icons.Outlined.PlayCircleOutline, "后台播放", isBackgroundPlaybackEnabled, true, onToggleBackgroundPlayback, if (isBackgroundPlaybackEnabled) "已开启" else "已关闭", glassShape = CircleShape)
                    if (!compactTop) LivePlayerIconButton(Icons.Outlined.Timer, "定时关闭", false, true, onOpenShutdownTimer, glassShape = CircleShape)
                    LivePlayerActionMenu(
                        compact = true,
                        glassShape = CircleShape,
                        onExpandedChange = { menuExpanded = it },
                        anchorModifier = if (glass) Modifier.size(PlayerGlassIconButtonSize) else Modifier.size(30.dp),
                        groups = listOf(buildList {
                            add(AppWindowAction("切换路线", onClick = onSourceClick))
                            add(AppWindowAction("播放信息", onClick = onOpenPlayerInfo))
                            add(AppWindowAction("播放器音量", onClick = onOpenVolume))
                            add(AppWindowAction("直播偏好", onClick = onOpenPlaybackPreferences))
                            if (compactTop) {
                                if (showPipButton) add(AppWindowAction("画中画", onClick = onEnterPip))
                                add(AppWindowAction(if (isAudioOnly) "恢复视频" else "仅播放音频", onClick = onToggleAudioOnly))
                                add(AppWindowAction(if (isBackgroundPlaybackEnabled) "关闭后台播放" else "开启后台播放", onClick = onToggleBackgroundPlayback))
                                add(AppWindowAction("定时关闭", onClick = onOpenShutdownTimer))
                            }
                        }),
                    ) { AppIcon(Icons.Outlined.Settings, "播放设置", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        }
        AnimatedVisibility(
            visible = effectiveControlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomControlsBottomPadding),
        ) {
            BoxWithConstraints {
                val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
                val compact = maxWidth < ((if (glass) 520 else 360) * fontScale).dp
                val collapseTools = glass && maxWidth < (400 * fontScale).dp
                val collapseRefresh = glass && maxWidth < (320 * fontScale).dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (!glass) Modifier.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))) else Modifier)
                        .then(if (applyBottomSystemBarPadding) Modifier.navigationBarsPadding() else Modifier)
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (glass) 6.dp else 0.dp),
                ) {
                    LivePlayerIconButton(if (isPlaying) Icons.Outlined.Pause else playIcon, if (isPlaying) "暂停" else "播放", false, true, onPlayPause)
                    if (!collapseRefresh) LivePlayerIconButton(refreshIcon, "刷新", false, true, onRefresh)
                    Spacer(Modifier.weight(1f))
                    if (!collapseTools) LivePlayerIconButton(Icons.Outlined.Block, "屏蔽", false, true, onOpenBlockSettings)
                    LivePlayerDanmakuControls(
                        enabled = isDanmakuEnabled,
                        showSettings = isFullscreen || !collapseTools,
                        onToggle = onToggleDanmaku,
                        onOpenSettings = onOpenDanmakuSettings,
                        source = danmakuSettingsSource,
                    )
                    if (isFullscreen) LivePlayerIconButton(
                        null, "发弹幕", false, true, onOpenSend, glassShape = CircleShape,
                        iconContent = { tint -> PiliPlusDanmakuSendIcon(tint = tint, size = 22.dp) },
                    )
                    if (!compact) {
                        LivePlayerActionMenu(
                            compact = true,
                            onExpandedChange = { menuExpanded = it },
                            anchorModifier = Modifier.height(if (glass) 34.dp else 30.dp).widthIn(min = 50.dp, max = 90.dp),
                            groups = listOf(videoFitOptions),
                        ) {
                            AppText(videoFitDesc, color = Color.White, fontSize = 13.sp, maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp), overflow = TextOverflow.Ellipsis)
                        }
                        LivePlayerActionMenu(
                            compact = true,
                            onExpandedChange = { menuExpanded = it },
                            anchorModifier = Modifier.height(if (glass) 34.dp else 30.dp).widthIn(min = 50.dp, max = 90.dp),
                            groups = listOf(qualityOptions),
                        ) {
                            AppText(currentQualityDesc, color = Color.White, fontSize = 13.sp, maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp), overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        LivePlayerActionMenu(
                            sourceOverride = danmakuSettingsSource.takeIf { collapseTools },
                            compact = true,
                            onExpandedChange = { menuExpanded = it },
                            anchorModifier = if (glass) Modifier.size(PlayerGlassIconButtonSize) else Modifier.size(30.dp),
                            groups = listOf(buildList {
                                if (collapseRefresh) add(AppWindowAction("刷新", onClick = onRefresh))
                                if (collapseTools) {
                                    add(AppWindowAction("屏蔽", onClick = onOpenBlockSettings))
                                    add(AppWindowAction("弹幕设置", onClick = onOpenDanmakuSettings))
                                }
                                add(AppWindowAction("画面比例：$videoFitDesc", children = videoFitOptions))
                                add(AppWindowAction("画质：$currentQualityDesc", children = qualityOptions))
                            }),
                        ) { AppIcon(Icons.Outlined.Tune, if (collapseTools) "播放工具" else "画面比例与画质", tint = Color.White, modifier = Modifier.size(18.dp)) }
                    }
                    LivePlayerIconButton(if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (isFullscreen) "退出全屏" else "全屏", false, true, onToggleFullscreen)
                }
            }
        }
        if (effectiveControlsVisible && showLockButton) {
            LivePlayerIconButton(Icons.Outlined.LockOpen, "锁定", false, true, { isLocked = true }, hapticType = HapticType.MEDIUM, glassShape = CircleShape, modifier = Modifier.align(Alignment.CenterStart).padding(start = AppSpacingTokens.Small))
            LivePlayerIconButton(Icons.Outlined.PhotoCamera, "截图", false, true, onCaptureScreenshot, glassShape = CircleShape, modifier = Modifier.align(Alignment.CenterEnd).padding(end = AppSpacingTokens.Small))
        }
        if (effectiveControlsVisible && videoScale != 1f) {
            LivePlayerGlassDock(
                enabled = glass,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 56.dp),
            ) {
                AppSurface(
                    onClick = { stateHaptic(HapticType.LIGHT); onResetScale() },
                    shape = AppShapes.container(ContainerLevel.Pill),
                    color = if (glass) Color.Transparent else palette.scrim.copy(alpha = 0.6f),
                ) {
                    AppText("恢复缩放", color = LiveStatusPalette.MediaContent, modifier = Modifier.padding(AppSpacingTokens.Small))
                }
            }
        }
        // 4. 锁定状态解锁按钮（垂直居左，始终可见）
        if (isLocked) {
            LivePlayerIconButton(
                icon = Icons.Outlined.Lock,
                label = "解锁",
                glassShape = CircleShape,
                selected = true,
                enabled = true,
                hapticType = HapticType.MEDIUM,
                onClick = {
                    isLocked = false
                    isControlsVisible = true
                },
                stateDescription = "已锁定，点击解锁",
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = AppSpacingTokens.Large)
            )
        }
    }
}

/** 菜单沿用真实触发源，横竖屏玻璃材质与普通视频控件共用背景。 */
@Composable
private fun LivePlayerActionMenu(
    compact: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    anchorModifier: Modifier,
    groups: List<List<AppWindowAction>>,
    modifier: Modifier = Modifier,
    glassShape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(percent = 50),
    sourceOverride: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
    content: @Composable () -> Unit,
) {
    val glass = LocalPlayerControlGlassEnabled.current
    val source = sourceOverride ?: com.android.purebilibili.core.ui.components.rememberAppGlassMenuSource()
    LivePlayerGlassDock(
        enabled = glass,
        // 外层材质与内容共用捕获层，菜单打开后不在原位留下玻璃空壳。
        modifier = modifier.then(if (glass) Modifier.appGlassMenuSource(source) else Modifier),
        shape = glassShape,
    ) {
        AppWindowActionMenu(
            source = source.takeIf { glass },
            sourceCapturedByParent = glass,
            compact = compact,
            onExpandedChange = onExpandedChange,
            anchorModifier = anchorModifier,
            groups = groups,
            content = content,
        )
    }
}

/** 材质关闭时不提供玻璃专用的图标装饰与交互局部值。 */
@Composable
private fun LivePlayerGlassDock(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    content: @Composable BoxScope.() -> Unit,
) {
    if (enabled) {
        PlayerControlGlassDock(enabled = true, modifier = modifier, shape = shape, content = content)
    } else {
        Box(modifier = modifier, content = content)
    }
}

/** 横竖屏共用普通视频的弹幕字形；玻璃开关与设置分别使用圆形材质。 */
@Composable
private fun LivePlayerDanmakuControls(
    enabled: Boolean,
    showSettings: Boolean,
    onToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    source: com.android.purebilibili.core.ui.components.AppGlassMenuSource? = null,
    modifier: Modifier = Modifier,
) {
    val glass = LocalPlayerControlGlassEnabled.current
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val nativeSize = remember(playerChromeProfile.tabPresentation) {
        resolveLiveVisualSpec(playerChromeProfile.tabPresentation).playerButtonTouchTargetDp.dp
    }
    val buttonModifier = if (glass) Modifier.size(PlayerGlassIconButtonSize) else Modifier.size(nativeSize)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (glass) 4.dp else 0.dp),
    ) {
        LivePlayerGlassDock(enabled = glass, shape = CircleShape) {
            NativeDanmakuToggleButton(
                enabled = enabled,
                onToggle = onToggle,
                activeTint = AppContentColors.selectionAccent(glass),
                inactiveTint = Color.White.copy(alpha = 0.74f),
                modifier = buttonModifier,
                iconSize = 22.dp,
            )
        }
        if (showSettings) {
            LivePlayerGlassDock(
                enabled = glass,
                shape = CircleShape,
                modifier = if (glass && source != null) Modifier.appGlassMenuSource(source, onOpenSettings) else Modifier,
            ) {
                AppIconButton(onClick = onOpenSettings, modifier = buttonModifier) {
                    PiliPlusDanmakuSettingsIcon(tint = Color.White.copy(alpha = 0.9f), size = 22.dp)
                }
            }
        }
    }
}
