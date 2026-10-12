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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.vector.ImageVector
import com.android.purebilibili.feature.live.rememberLiveChromePalette
import com.android.purebilibili.feature.live.resolveLivePlayerControlVisualSpec

@Composable
private fun LivePlayerIconButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    stateDescription: String? = null,
    modifier: Modifier = Modifier
) {
    require(label.isNotBlank()) { "Live player icon button label must not be blank" }

    val palette = rememberLiveChromePalette()
    val playerChromeProfile = rememberAppPlayerChromeProfile()
    val visualSpec = remember(playerChromeProfile.tabPresentation) {
        resolveLiveVisualSpec(playerChromeProfile.tabPresentation)
    }
    val touchTargetSize = visualSpec.playerButtonTouchTargetDp.dp
    val visualSize = visualSpec.playerButtonVisualSizeDp.dp

    Box(
        modifier = modifier
            .size(touchTargetSize)
            .appElasticPress(enabled = enabled, visualSize = visualSize)
            .clickable(
                enabled = enabled,
                role = if (stateDescription == null) Role.Button else Role.Switch,
                onClickLabel = label,
                onClick = onClick
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
            color = if (selected) palette.accentSoft else palette.scrim.copy(alpha = 0.48f),
            modifier = Modifier
                .size(visualSize)
                .alpha(if (enabled) 1f else 0.38f)
                .border(
                    AppSurfaceTokens.OutlineWidth,
                    if (selected) palette.accent.copy(alpha = 0.4f) else LiveStatusPalette.MediaContent.copy(alpha = 0.10f),
                    CircleShape
                )
        ) {
            Box(contentAlignment = Alignment.Center) {
                AppIcon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) palette.accentStrong else LiveStatusPalette.MediaContent
                )
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
    modifier: Modifier = Modifier,
    isChatVisible: Boolean = true,
    onToggleChat: () -> Unit = {},
    showChatToggle: Boolean = false,
    // 新增：弹幕开关
    isDanmakuEnabled: Boolean = true,
    onToggleDanmaku: () -> Unit = {},
    onOpenDanmakuSettings: () -> Unit = {},
    onOpenBlockSettings: () -> Unit = {},
    // [新增] 刷新
    onRefresh: () -> Unit = {},
    isAudioOnly: Boolean = false,
    onToggleAudioOnly: () -> Unit = {},
    isBackgroundPlaybackEnabled: Boolean = true,
    onToggleBackgroundPlayback: () -> Unit = {},
    onOpenShutdownTimer: () -> Unit = {},
    onOpenPlayerInfo: () -> Unit = {},
    onOpenSend: () -> Unit = {},
    videoFitDesc: String = "",
    onVideoFitClick: () -> Unit = {},
    currentQualityDesc: String = "",
    onQualityClick: () -> Unit = {},
    // [新增] 手动线路切换
    currentSourceDesc: String = "",
    onSourceClick: () -> Unit = {},
    onOpenMore: () -> Unit = {},
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
    onLike: ((Int) -> Unit)? = null,
    gesturePolicy: LivePlayerGesturePolicy = LivePlayerGesturePolicy(true, true),
    usePortraitControls: Boolean = false,
    isClearScreen: Boolean = false,
    onPortraitTap: () -> Unit = {},
    onOpenPortraitMore: () -> Unit = {},
) {
    var isControlsVisible by remember { mutableStateOf(true) }
    // 全屏锁屏状态（仅 showLockButton 场景生效）
    var isLocked by rememberSaveable { mutableStateOf(false) }
    val palette = rememberLiveChromePalette()
    val controlVisualSpec = remember { resolveLivePlayerControlVisualSpec() }
    
    // 自动隐藏控制器（锁定时保持隐藏）
    LaunchedEffect(isControlsVisible, isPlaying, isLocked) {
        if (isControlsVisible && !isLocked && isPlaying) {
            kotlinx.coroutines.delay(3000)
            isControlsVisible = false
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
    // 竖向直播不响应中间区域的全屏手势。
    var gestureZone by remember { mutableStateOf(LiveGestureZone.None) }
    var centerDragAccumulator by remember { mutableFloatStateOf(0f) }
    val latestPlayPause by rememberUpdatedState(onPlayPause)
    val latestToggleFullscreen by rememberUpdatedState(onToggleFullscreen)
    val latestToggleDanmaku by rememberUpdatedState(onToggleDanmaku)
    val latestPortraitTap by rememberUpdatedState(onPortraitTap)
    val latestOpenPortraitMore by rememberUpdatedState(onOpenPortraitMore)
    val latestOnLike by rememberUpdatedState(onLike)
    val latestOnScale by rememberUpdatedState(onScale)
    
    // 锁定时控制栏强制隐藏
    val effectiveControlsVisible = isControlsVisible && !isLocked && !usePortraitControls

    Box(
        modifier = modifier
            .fillMaxSize()
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
                        .pointerInput(usePortraitControls, gesturePolicy.doubleTapPlayback) {
                            detectTapGestures(
                                onTap = {
                                    if (usePortraitControls) latestPortraitTap()
                                    else isControlsVisible = !isControlsVisible
                                },
                                onDoubleTap = {
                                    if (gesturePolicy.doubleTapPlayback) {
                                        latestPlayPause()
                                    } else if (usePortraitControls) {
                                        latestOnLike?.invoke(1)
                                    }
                                },
                                onLongPress = if (usePortraitControls) {
                                    { latestOpenPortraitMore() }
                                } else null,
                            )
                        }
                        .pointerInput(usePortraitControls, isDanmakuEnabled, isClearScreen) {
                            if (usePortraitControls) {
                                var horizontalDrag = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { horizontalDrag = 0f },
                                    onHorizontalDrag = { change, dragAmount ->
                                        horizontalDrag += dragAmount
                                        change.consume()
                                    },
                                    onDragEnd = {
                                        val threshold = size.width * 0.16f
                                        if (!isClearScreen && horizontalDrag <= -threshold && isDanmakuEnabled) {
                                            latestToggleDanmaku()
                                        } else if (!isClearScreen && horizontalDrag >= threshold && !isDanmakuEnabled) {
                                            latestToggleDanmaku()
                                        }
                                    },
                                    onDragCancel = { horizontalDrag = 0f },
                                )
                            }
                        }
                        .pointerInput(gesturePolicy.centerDragFullscreen, isClearScreen) {
                            val screenHeight = size.height.toFloat()
                            val screenWidth = size.width.toFloat()

                            // 使用 Float 累积变化量，解决"不跟手"问题
                            var volumeAccumulator = 0f
                            var brightnessAccumulator = 0f

                            var maxVolume = 0

                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    // 三分区：左 1/3 亮度、右 1/3 音量、中间 1/3 上下滑切换全屏
                                    gestureZone = when {
                                        isClearScreen -> LiveGestureZone.None
                                        offset.x < screenWidth / 3f -> LiveGestureZone.Brightness
                                        offset.x > 2f * screenWidth / 3f -> LiveGestureZone.Volume
                                        gesturePolicy.centerDragFullscreen -> LiveGestureZone.FullscreenToggle
                                        else -> LiveGestureZone.None
                                    }
                                    centerDragAccumulator = 0f
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
                                    // 中间区域：上下滑动超过阈值则切换全屏
                                    if (gestureZone == LiveGestureZone.FullscreenToggle &&
                                        kotlin.math.abs(centerDragAccumulator) > screenHeight * 0.15f
                                    ) {
                                        latestToggleFullscreen()
                                    }
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
                                        }
                                        LiveGestureZone.None -> {}
                                    }
                                }
                            )
                        }
                }
            )
    ) {
        // 与普通视频共用同一 Host：亮度贴左边缘，音量贴右边缘。
        GestureLevelOverlayHost(
            visible = isGestureVisible,
            mode = if (gestureKind == GestureLevelKind.Brightness) {
                VideoGestureMode.Brightness
            } else {
                VideoGestureMode.Volume
            },
            percent = gesturePercent,
        )
        
        // Common controls for portrait, landscape and every app theme.
        AnimatedVisibility(
            visible = showTopBar && effectiveControlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(palette.scrim.copy(alpha = 0.8f), Color.Transparent)))
                    .then(if (applyTopSystemBarPadding) Modifier.statusBarsPadding() else Modifier)
                    .padding(horizontal = AppSpacingTokens.Small, vertical = AppSpacingTokens.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LivePlayerIconButton(backIcon, "返回", false, true, onBack)
                AppText(title, color = LiveStatusPalette.MediaContent, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                LivePlayerIconButton(Icons.AutoMirrored.Outlined.Send, "发送弹幕", false, true, onOpenSend)
                LivePlayerIconButton(Icons.Outlined.SettingsInputComponent, "直播线路", false, true, onSourceClick)
                LivePlayerIconButton(Icons.Outlined.MoreHoriz, "更多播放设置", false, true, onOpenMore)
            }
        }
        AnimatedVisibility(
            visible = effectiveControlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomControlsBottomPadding),
        ) {
            BoxWithConstraints {
                val compact = maxWidth < 520.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, palette.scrim.copy(alpha = 0.8f))))
                        .then(if (applyBottomSystemBarPadding) Modifier.navigationBarsPadding() else Modifier)
                        .padding(horizontal = AppSpacingTokens.Small, vertical = AppSpacingTokens.ExtraSmall),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LivePlayerIconButton(if (isPlaying) Icons.Outlined.Pause else playIcon, if (isPlaying) "暂停" else "播放", false, true, onPlayPause)
                    LivePlayerIconButton(refreshIcon, "刷新直播", false, true, onRefresh)
                    Spacer(Modifier.weight(1f))
                    if (!compact) LivePlayerIconButton(Icons.Outlined.Block, "屏蔽设置", false, true, onOpenBlockSettings)
                    LivePlayerIconButton(commentIcon, if (isDanmakuEnabled) "关闭弹幕" else "开启弹幕", isDanmakuEnabled, true, onToggleDanmaku, if (isDanmakuEnabled) "已开启" else "已关闭")
                    LivePlayerIconButton(Icons.Outlined.Settings, "弹幕设置", false, true, onOpenDanmakuSettings)
                    if (!compact && currentQualityDesc.isNotBlank()) {
                        AppSurface(onClick = onQualityClick, shape = AppShapes.container(ContainerLevel.Pill), color = palette.scrim.copy(alpha = 0.4f)) {
                            AppText(currentQualityDesc, color = LiveStatusPalette.MediaContent, modifier = Modifier.padding(horizontal = AppSpacingTokens.Small, vertical = AppSpacingTokens.Small))
                        }
                    }
                    if (!compact) LivePlayerIconButton(Icons.Outlined.AspectRatio, "画面比例", false, true, onVideoFitClick)
                    // Compact mode keeps the same tools in one visible menu instead of a tiny scroll strip.
                    LivePlayerIconButton(Icons.Outlined.MoreHoriz, "更多播放设置", false, true, onOpenMore)
                    LivePlayerIconButton(if (isFullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, if (isFullscreen) "退出全屏" else "进入全屏", false, true, onToggleFullscreen)
                }
            }
        }
        if (effectiveControlsVisible && showLockButton) {
            LivePlayerIconButton(Icons.Outlined.LockOpen, "锁定", false, true, { isLocked = true }, modifier = Modifier.align(Alignment.CenterStart).padding(start = AppSpacingTokens.Small))
            LivePlayerIconButton(Icons.Outlined.PhotoCamera, "截图", false, true, onCaptureScreenshot, modifier = Modifier.align(Alignment.CenterEnd).padding(end = AppSpacingTokens.Small))
        }
        if (effectiveControlsVisible && videoScale != 1f) {
            AppSurface(onClick = onResetScale, shape = AppShapes.container(ContainerLevel.Pill), color = palette.scrim.copy(alpha = 0.6f), modifier = Modifier.align(Alignment.CenterEnd).padding(end = 56.dp)) {
                AppText("恢复缩放", color = LiveStatusPalette.MediaContent, modifier = Modifier.padding(AppSpacingTokens.Small))
            }
        }
        // 4. 锁定状态解锁按钮（垂直居左，始终可见）
        if (isLocked) {
            LivePlayerIconButton(
                icon = Icons.Outlined.Lock,
                label = "解锁",
                selected = true,
                enabled = true,
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
