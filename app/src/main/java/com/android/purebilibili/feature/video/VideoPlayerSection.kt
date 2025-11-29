// 文件路径: feature/video/VideoPlayerSection.kt
package com.android.purebilibili.feature.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.util.FormatUtils
import kotlin.math.abs

enum class GestureMode { None, Brightness, Volume, Seek }

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

    var gestureMode by remember { mutableStateOf(GestureMode.None) }
    var gestureIcon by remember { mutableStateOf<ImageVector?>(null) }
    var gesturePercent by remember { mutableFloatStateOf(0f) }
    var seekTargetTime by remember { mutableLongStateOf(0L) }
    var isGestureVisible by remember { mutableStateOf(false) }

    // 辅助函数：获取 Activity 用于调整亮度
    fun getActivity(): Activity? = when (context) {
        is Activity -> context
        is ContextWrapper -> context.baseContext as? Activity
        else -> null
    }

    // 播放器根容器
    Box(
        modifier = Modifier
            .fillMaxSize() // 填满外层容器 (即 VideoDetailScreen 中的那个 Box)
            .background(Color.Black)
            .pointerInput(Unit) {
                // 仅在全屏且非画中画模式下启用手势
                if (isFullscreen && !isInPipMode) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isGestureVisible = true
                            gestureMode = GestureMode.None
                            // 简单的左右分区判定
                            if (offset.x < size.width / 2) { /* 左侧 */ }
                        },
                        onDragEnd = {
                            if (gestureMode == GestureMode.Seek) {
                                playerState.player.seekTo(seekTargetTime)
                            }
                            isGestureVisible = false
                            gestureMode = GestureMode.None
                        },
                        onDragCancel = {
                            isGestureVisible = false
                            gestureMode = GestureMode.None
                        },
                        onDrag = { change, dragAmount ->
                            if (gestureMode == GestureMode.None) {
                                // 判断水平还是垂直移动
                                if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                    gestureMode = GestureMode.Seek
                                } else {
                                    gestureMode = if (change.position.x < size.width / 2) GestureMode.Brightness else GestureMode.Volume
                                }
                            }

                            when (gestureMode) {
                                GestureMode.Seek -> {
                                    val duration = playerState.player.duration.coerceAtLeast(0L)
                                    val current = playerState.player.currentPosition
                                    // 简单的滑动比例算法
                                    val seekDelta = (dragAmount.x * 200).toLong() // 灵敏度调节
                                    seekTargetTime = (current + seekDelta).coerceIn(0L, duration)
                                }
                                GestureMode.Brightness -> {
                                    val delta = -dragAmount.y / (size.height / 2)
                                    gesturePercent = (0.5f + delta).coerceIn(0f, 1f)
                                    getActivity()?.window?.attributes = getActivity()?.window?.attributes?.apply {
                                        screenBrightness = gesturePercent
                                    }
                                    gestureIcon = Icons.Rounded.Brightness7
                                }
                                GestureMode.Volume -> {
                                    val delta = -dragAmount.y / (size.height / 2)
                                    gesturePercent = (0.5f + delta).coerceIn(0f, 1f)
                                    val targetVol = (gesturePercent * maxVolume).toInt()
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                    gestureIcon = Icons.Rounded.VolumeUp
                                }
                                else -> {}
                            }
                        }
                    )
                }
            }
    ) {
        // 1. ExoPlayer 视图
        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = playerState.player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    useController = false // 禁用原生控制条，使用自定义 Overlay
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. 弹幕视图 (非画中画时显示)
        if (!isInPipMode) {
            AndroidView(
                factory = { playerState.danmakuView },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. 手势状态反馈 UI (中心弹窗)
        if (isGestureVisible && isFullscreen && !isInPipMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(120.dp)
                    .background(Color.Black.copy(0.7f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (gestureMode == GestureMode.Seek) {
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
                    }
                }
            }
        }

        // 4. 控制层 Overlay (仅当数据加载成功且非画中画时显示)
        if (uiState is PlayerUiState.Success && !isInPipMode) {
            VideoPlayerOverlay(
                player = playerState.player,
                title = uiState.info.title,
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