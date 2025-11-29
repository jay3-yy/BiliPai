// 文件路径: feature/video/VideoPlayerOverlay.kt
package com.android.purebilibili.feature.video

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.util.FormatUtils
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerOverlay(
    player: Player,
    title: String,
    isFullscreen: Boolean,
    isDanmakuOn: Boolean,
    currentQualityLabel: String,
    qualityLabels: List<String>,
    onQualitySelected: (Int) -> Unit,
    onToggleDanmaku: () -> Unit,
    onBack: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    // 控制器可见性
    var showControls by remember { mutableStateOf(true) }
    var showQualityMenu by remember { mutableStateOf(false) }

    // 播放状态
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var bufferedPosition by remember { mutableLongStateOf(0L) }

    // 自动隐藏控制器
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // 更新播放状态
    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            currentPosition = player.currentPosition
            duration = if (player.duration < 0) 0L else player.duration
            bufferedPosition = player.bufferedPosition
            delay(200)
        }
    }

    // 点击切换控制器显示
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showControls = !showControls }
    ) {
        // 顶部渐变遮罩（始终显示）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.7f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 底部渐变遮罩（始终显示）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        // 控制器内容（动画显示/隐藏）
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 顶部控制栏
                TopControlBar(
                    title = title,
                    isFullscreen = isFullscreen,
                    isDanmakuOn = isDanmakuOn,
                    currentQualityLabel = currentQualityLabel,
                    onBack = onBack,
                    onToggleDanmaku = onToggleDanmaku,
                    onQualityClick = { showQualityMenu = true }
                )

                Spacer(modifier = Modifier.weight(1f))

                // 底部控制栏
                BottomControlBar(
                    isPlaying = isPlaying,
                    currentPosition = currentPosition,
                    duration = duration,
                    bufferedPosition = bufferedPosition,
                    isFullscreen = isFullscreen,
                    onPlayPauseClick = {
                        if (isPlaying) player.pause() else player.play()
                    },
                    onSeek = { position -> player.seekTo(position) },
                    onToggleFullscreen = onToggleFullscreen
                )
            }
        }

        // 中央播放/暂停按钮（仅在显示控制器时）
        AnimatedVisibility(
            visible = showControls && !isPlaying,
            modifier = Modifier.align(Alignment.Center),
            enter = scaleIn(tween(200)) + fadeIn(tween(200)),
            exit = scaleOut(tween(200)) + fadeOut(tween(200))
        ) {
            Surface(
                onClick = { player.play() },
                color = Color.Black.copy(alpha = 0.6f),
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // 清晰度选择菜单
        if (showQualityMenu) {
            QualitySelectionMenu(
                qualities = qualityLabels,
                currentQuality = currentQualityLabel,
                onQualitySelected = { index ->
                    onQualitySelected(index)
                    showQualityMenu = false
                },
                onDismiss = { showQualityMenu = false }
            )
        }
    }
}

// 🔥 顶部控制栏
@Composable
fun TopControlBar(
    title: String,
    isFullscreen: Boolean,
    isDanmakuOn: Boolean,
    currentQualityLabel: String,
    onBack: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onQualityClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 返回按钮
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        // 标题（仅全屏时显示）
        if (isFullscreen) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // 弹幕开关
        IconButton(onClick = onToggleDanmaku) {
            Icon(
                if (isDanmakuOn) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                contentDescription = if (isDanmakuOn) "关闭弹幕" else "打开弹幕",
                tint = if (isDanmakuOn) BiliPink else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(24.dp)
            )
        }

        // 清晰度按钮
        Surface(
            onClick = onQualityClick,
            color = Color.White.copy(alpha = 0.15f),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Text(
                text = currentQualityLabel,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

// 🔥 底部控制栏
@Composable
fun BottomControlBar(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    isFullscreen: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleFullscreen: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        // 进度条
        VideoProgressBar(
            currentPosition = currentPosition,
            duration = duration,
            bufferedPosition = bufferedPosition,
            onSeek = onSeek
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 控制按钮行
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放/暂停
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 时间显示
            Text(
                text = "${FormatUtils.formatDuration((currentPosition / 1000).toInt())} / ${FormatUtils.formatDuration((duration / 1000).toInt())}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.weight(1f))

            // 全屏按钮
            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// 🔥 进度条组件
@Composable
fun VideoProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit
) {
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
    val buffered = if (duration > 0) bufferedPosition.toFloat() / duration else 0f

    var tempProgress by remember { mutableFloatStateOf(progress) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (!isDragging) tempProgress = progress
    }

    Column {
        Slider(
            value = if (isDragging) tempProgress else progress,
            onValueChange = {
                isDragging = true
                tempProgress = it
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek((tempProgress * duration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = BiliPink,
                activeTrackColor = BiliPink,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// 🔥 清晰度选择菜单
@Composable
fun QualitySelectionMenu(
    qualities: List<String>,
    currentQuality: String,
    onQualitySelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
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
        Surface(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* 阻止点击穿透 */ },
            color = Color(0xFF2B2B2B),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "清晰度",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )

                qualities.forEachIndexed { index, quality ->
                    val isSelected = quality == currentQuality

                    Surface(
                        onClick = { onQualitySelected(index) },
                        color = if (isSelected) BiliPink.copy(alpha = 0.2f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = quality,
                                color = if (isSelected) BiliPink else Color.White,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )

                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = BiliPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}