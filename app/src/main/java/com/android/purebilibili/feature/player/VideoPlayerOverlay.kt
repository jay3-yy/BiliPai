package com.android.purebilibili.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.theme.BiliPink
import com.android.purebilibili.core.util.FormatUtils
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerOverlay(
    player: ExoPlayer,
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
    var isVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showQualityMenu by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible, isPlaying) {
        if (isVisible && isPlaying) {
            delay(3000)
            isVisible = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = player.currentPosition
            val rawDuration = player.duration
            duration = if (rawDuration < 0) 0L else rawDuration
            isPlaying = player.isPlaying
            delay(500)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { isVisible = !isVisible }
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 顶部渐变
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                )
                // 底部渐变
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f))))
                )

                // 顶部操作栏
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(48.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    )
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                }

                // 中间播放按钮
                IconButton(
                    onClick = {
                        if (isPlaying) player.pause() else player.play()
                        isPlaying = !isPlaying
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(60.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // 底部控制栏
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                ) {
                    // 进度条行
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().height(32.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatDuration((currentPosition / 1000).toInt()),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = {
                                val seekTime = (it * duration).toLong()
                                player.seekTo(seekTime)
                                currentPosition = seekTime
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = BiliPink,
                                activeTrackColor = BiliPink,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            text = FormatUtils.formatDuration((duration / 1000).toInt()),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp
                        )
                    }

                    // 功能按钮行
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onToggleDanmaku) {
                            Icon(
                                imageVector = if (isDanmakuOn) Icons.Outlined.Subtitles else Icons.Outlined.SubtitlesOff,
                                contentDescription = "Danmaku",
                                tint = if (isDanmakuOn) BiliPink else Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            Surface(
                                onClick = { showQualityMenu = true },
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                                    Text(text = currentQualityLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            DropdownMenu(
                                expanded = showQualityMenu,
                                onDismissRequest = { showQualityMenu = false }
                            ) {
                                qualityLabels.forEachIndexed { index, label ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = { onQualitySelected(index); showQualityMenu = false }
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "Full",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}