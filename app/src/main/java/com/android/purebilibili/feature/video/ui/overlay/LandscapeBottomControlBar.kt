// 文件路径: feature/video/ui/overlay/LandscapeBottomControlBar.kt
package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
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
//  已改用 MaterialTheme.colorScheme.primary
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio

/**
 *  横屏底部控制栏（官方 B 站样式）
 * 
 * 布局结构：
 * - 上层：进度条 + 时间
 * - 下层：播放按钮 | 弹幕输入框 | 字幕 | 倍速 | 画质
 */
@Composable
fun LandscapeBottomControlBar(
    isPlaying: Boolean,
    progress: PlayerProgress,
    currentSpeed: Float = 1.0f,
    currentRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    danmakuEnabled: Boolean = true,
    //  [新增] 清晰度相关参数
    currentQualityLabel: String = "自动",
    onQualityClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit = {},  //  [新增] 拖动进度条开始回调（清除弹幕）
    onSpeedClick: () -> Unit = {},
    onRatioClick: () -> Unit = {},
    onDanmakuToggle: () -> Unit = {},
    onDanmakuInputClick: () -> Unit = {},
    onToggleFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.8f)
                    )
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            //  上层：进度条 + 时间
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 时间显示
                Text(
                    text = FormatUtils.formatDuration((progress.current / 1000).toInt()),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 进度条
                var tempProgress by remember { mutableFloatStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }
                val progressValue = if (progress.duration > 0) 
                    progress.current.toFloat() / progress.duration 
                else 0f
                
                LaunchedEffect(progressValue) {
                    if (!isDragging) {
                        tempProgress = progressValue
                    }
                }
                
                Slider(
                    value = if (isDragging) tempProgress else progressValue,
                    onValueChange = {
                        if (!isDragging) {
                            onSeekStart()  //  拖动开始时清除弹幕
                        }
                        isDragging = true
                        tempProgress = it
                    },
                    onValueChangeFinished = {
                        isDragging = false
                        onSeek((tempProgress * progress.duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 总时间
                Text(
                    text = FormatUtils.formatDuration((progress.duration / 1000).toInt()),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            //  下层：控制按钮 + 弹幕输入框
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 播放/暂停按钮
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (isPlaying) CupertinoIcons.Default.Pause else CupertinoIcons.Default.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 弹幕输入框（占据中间空间）
                LandscapeDanmakuInput(
                    onClick = onDanmakuInputClick,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // 弹幕开关按钮
                IconButton(
                    onClick = onDanmakuToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (danmakuEnabled) CupertinoIcons.Default.TextBubble else CupertinoIcons.Default.TextBubble,
                        contentDescription = "弹幕开关",
                        tint = if (danmakuEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                //  [官方适配] 清晰度按钮
                LandscapeControlButton(
                    text = currentQualityLabel,
                    isHighlighted = true,  // 清晰度始终高亮显示
                    onClick = onQualityClick
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                //  [官方适配] 倍速按钮
                LandscapeControlButton(
                    text = if (currentSpeed == 1.0f) "倍速" else "${currentSpeed}x",
                    isHighlighted = currentSpeed != 1.0f,
                    onClick = onSpeedClick
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                //  [官方适配] 画面比例按钮
                LandscapeControlButton(
                    text = currentRatio.displayName,
                    isHighlighted = currentRatio != VideoAspectRatio.FIT,
                    onClick = onRatioClick
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                // 全屏按钮
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        CupertinoIcons.Default.ArrowDownRightAndArrowUpLeft,
                        contentDescription = "退出全屏",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 *  横屏控制栏按钮
 */
@Composable
private fun LandscapeControlButton(
    text: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = if (isHighlighted) MaterialTheme.colorScheme.primary else Color.White,
            fontSize = 11.sp,
            fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
