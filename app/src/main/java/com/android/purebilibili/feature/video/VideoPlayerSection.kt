// 文件路径: feature/video/VideoPlayerSection.kt
package com.android.purebilibili.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings // 🔥 新增导入
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness7
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.util.FormatUtils
import kotlin.math.abs

enum class VideoGestureMode { None, Brightness, Volume, Seek }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerSection(
    playerState: VideoPlayerState,
    uiState: PlayerUiState,
    isFullscreen: Boolean,
    isInPipMode: Boolean,
    onToggleFullscreen: () -> Unit,
    onQualityChange: (Int, Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    // 控制器显示状态
    var showControls by remember { mutableStateOf(true) }

    var gestureMode by remember { mutableStateOf<VideoGestureMode>(VideoGestureMode.None) }
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var gesturePercent by remember { mutableFloatStateOf(0f) }
    var seekTargetTime by remember { mutableLongStateOf(0L) }
    var isGestureVisible by remember { mutableStateOf(false) }

    // 记录手势开始时的初始值
    var startVolume by remember { mutableIntStateOf(0) }
    var startBrightness by remember { mutableFloatStateOf(0f) }
    var totalDragDistanceY by remember { mutableFloatStateOf(0f) }

    fun getActivity(): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> context.baseContext as? Activity
        else -> null
    }

    // 播放器根容器
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 1. 点击事件：切换控制器显示
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls }
                )
            }
            // 2. 滑动事件：调节 进度/亮度/音量
            .pointerInput(isFullscreen, isInPipMode) {
                if (isFullscreen && !isInPipMode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isGestureVisible = true
                            gestureMode = VideoGestureMode.None
                            totalDragDistanceY = 0f

                            // 记录初始音量
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                            // 记录初始亮度 - 🔥🔥🔥【修复】平滑获取系统亮度
                            val attributes = getActivity()?.window?.attributes
                            val currentWindowBrightness = attributes?.screenBrightness ?: -1f

                            if (currentWindowBrightness < 0) {
                                // 如果当前是跟随系统(-1)，尝试获取系统当前的实际亮度值 (0-255)
                                try {
                                    val sysBrightness = Settings.System.getInt(
                                        context.contentResolver,
                                        Settings.System.SCREEN_BRIGHTNESS
                                    )
                                    // 转换为 0.0 - 1.0
                                    startBrightness = sysBrightness / 255f
                                } catch (e: Exception) {
                                    // 获取失败兜底为 0.5
                                    startBrightness = 0.5f
                                }
                            } else {
                                // 如果之前已经手动调节过，直接使用当前值
                                startBrightness = currentWindowBrightness
                            }
                        },
                        onDragEnd = {
                            if (gestureMode == VideoGestureMode.Seek) {
                                playerState.player.seekTo(seekTargetTime)
                                playerState.player.play()
                            }
                            isGestureVisible = false
                            gestureMode = VideoGestureMode.None
                        },
                        onDragCancel = {
                            isGestureVisible = false
                            gestureMode = VideoGestureMode.None
                        },
                        onDrag = { change, dragAmount ->
                            // 判定手势模式
                            if (gestureMode == VideoGestureMode.None) {
                                if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                    gestureMode = VideoGestureMode.Seek
                                    playerState.player.pause()
                                } else {
                                    // 左侧亮度，右侧音量
                                    gestureMode = if (change.position.x < size.width / 2) {
                                        VideoGestureMode.Brightness
                                    } else {
                                        VideoGestureMode.Volume
                                    }
                                }
                            }

                            when (gestureMode) {
                                VideoGestureMode.Seek -> {
                                    val duration = playerState.player.duration.coerceAtLeast(0L)
                                    val current = playerState.player.currentPosition
                                    val seekDelta = (dragAmount.x * 300).toLong()
                                    seekTargetTime = (current + seekDelta).coerceIn(0L, duration)
                                }
                                VideoGestureMode.Brightness -> {
                                    totalDragDistanceY -= dragAmount.y
                                    val deltaPercent = totalDragDistanceY / size.height
                                    val newBrightness = (startBrightness + deltaPercent).coerceIn(0f, 1f)

                                    getActivity()?.window?.attributes = getActivity()?.window?.attributes?.apply {
                                        screenBrightness = newBrightness
                                    }

                                    gesturePercent = newBrightness
                                    gestureIcon = Icons.Rounded.Brightness7
                                }
                                VideoGestureMode.Volume -> {
                                    totalDragDistanceY -= dragAmount.y
                                    val deltaPercent = totalDragDistanceY / size.height
                                    val newVolPercent = ((startVolume.toFloat() / maxVolume) + deltaPercent).coerceIn(0f, 1f)

                                    val targetVol = (newVolPercent * maxVolume).toInt()
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)

                                    gesturePercent = newVolPercent
                                    gestureIcon = Icons.Rounded.VolumeUp
                                }
                                else -> {}
                            }
                        }
                    )
                }
            }
    ) {
        // 1. ExoPlayer
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = playerState.player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. 弹幕
        if (!isInPipMode) {
            AndroidView(
                factory = {
                    val view = playerState.danmakuView
                    if (view.parent != null) {
                        (view.parent as ViewGroup).removeView(view)
                    }
                    view
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. 手势状态反馈 UI
        if (isGestureVisible && isFullscreen && !isInPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(120.dp)
                    .background(Color.Black.copy(0.7f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (gestureMode == VideoGestureMode.Seek) {
                        Text(
                            text = FormatUtils.formatDuration((seekTargetTime / 1000).toInt()),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    } else {
                        Icon(
                            imageVector = gestureIcon ?: Icons.Rounded.Brightness7,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${(gesturePercent * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        )
                    }
                }
            }
        }

        // 4. 控制层 Overlay
        if (uiState is PlayerUiState.Success && !isInPipMode) {
            VideoPlayerOverlay(
                player = playerState.player,
                title = uiState.info.title,
                isVisible = showControls,
                onToggleVisible = { showControls = !showControls },
                isFullscreen = isFullscreen,
                isDanmakuOn = playerState.isDanmakuOn,
                currentQualityLabel = uiState.qualityLabels.getOrNull(uiState.qualityIds.indexOf(uiState.currentQuality)) ?: "自动",
                qualityLabels = uiState.qualityLabels,
                onQualitySelected = { index ->
                    val id = uiState.qualityIds.getOrNull(index) ?: 0
                    onQualityChange(id, playerState.player.currentPosition)
                },
                onToggleDanmaku = { playerState.isDanmakuOn = !playerState.isDanmakuOn },
                onBack = onBack,
                onToggleFullscreen = onToggleFullscreen
            )
        }
    }
}