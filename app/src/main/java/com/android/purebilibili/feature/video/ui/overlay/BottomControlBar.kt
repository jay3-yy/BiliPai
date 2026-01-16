// File: feature/video/ui/overlay/BottomControlBar.kt
package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
//  Cupertino Icons - iOS SF Symbols 风格图标
import io.github.alexzhirkevich.cupertino.icons.CupertinoIcons
import io.github.alexzhirkevich.cupertino.icons.outlined.*
import io.github.alexzhirkevich.cupertino.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio

/**
 * Bottom Control Bar Component
 * 
 * Displays the bottom control bar with:
 * - Play/pause button
 * - Progress bar
 * - Time display
 * - Speed selector
 * - Fullscreen toggle
 * 
 * Requirement Reference: AC2.3 - Reusable BottomControlBar
 */

/**
 * Player progress data class
 */
data class PlayerProgress(
    val current: Long = 0L,
    val duration: Long = 0L,
    val buffered: Long = 0L
)

@Composable
fun BottomControlBar(
    isPlaying: Boolean,
    progress: PlayerProgress,
    isFullscreen: Boolean,
    currentSpeed: Float = 1.0f,
    currentRatio: VideoAspectRatio = VideoAspectRatio.FIT,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit = {},  //  [新增] 拖动进度条开始回调（清除弹幕）
    onSpeedClick: () -> Unit = {},
    onRatioClick: () -> Unit = {},
    onToggleFullscreen: () -> Unit,
    //  [新增] 竖屏模式弹幕开关
    danmakuEnabled: Boolean = true,
    onDanmakuToggle: () -> Unit = {},
    //  [新增] 竖屏模式清晰度选择
    currentQualityLabel: String = "",
    onQualityClick: () -> Unit = {},
    // 🖼️ [新增] 视频预览图数据
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData? = null,
    // 📖 [新增] 视频章节数据
    viewPoints: List<com.android.purebilibili.data.model.response.ViewPoint> = emptyList(),
    currentChapter: String? = null,
    onChapterClick: () -> Unit = {},
    // 📱 [新增] 竖屏全屏模式
    isVerticalVideo: Boolean = false,
    onPortraitFullscreen: () -> Unit = {},
    // 📲 [新增] 小窗模式
    onPipClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)  //  减小水平 padding
            .padding(bottom = 4.dp)
            //  只在全屏横屏时才需要避开导航栏
            // 竖屏时导航栏在页面底部，不在播放器区域内
            .let { if (isFullscreen) it.navigationBarsPadding() else it }
    ) {
        VideoProgressBar(
            currentPosition = progress.current,
            duration = progress.duration,
            bufferedPosition = progress.buffered,
            onSeek = onSeek,
            onSeekStart = onSeekStart,  //  传递给进度条
            videoshotData = videoshotData,
            viewPoints = viewPoints,
            currentChapter = currentChapter,
            onChapterClick = onChapterClick
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            //  使用 SpaceBetween 确保两端元素始终可见
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧：播放按钮和时间
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(36.dp)  //  缩小按钮
                ) {
                    Icon(
                        if (isPlaying) CupertinoIcons.Default.Pause else CupertinoIcons.Default.Play,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)  //  缩小图标
                    )
                }

                Text(
                    text = "${FormatUtils.formatDuration((progress.current / 1000).toInt())} / ${FormatUtils.formatDuration((progress.duration / 1000).toInt())}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp,  //  缩小字体
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
            
            // 中间：功能按钮（自适应空间）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.weight(1f)
            ) {
                // [问题13修复] 倍速和比例按钮仅在全屏时显示
                if (isFullscreen) {
                    // Speed button
                    Surface(
                        onClick = onSpeedClick,
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (currentSpeed == 1.0f) "倍速" else "${currentSpeed}x",
                            color = if (currentSpeed != 1.0f) MaterialTheme.colorScheme.primary else Color.White,
                            fontSize = 10.sp,  //  缩小字体
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)  //  缩小 padding
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(3.dp))  //  缩小间距
                    
                    //  Aspect Ratio button
                    Surface(
                        onClick = onRatioClick,
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = currentRatio.displayName,
                            color = if (currentRatio != VideoAspectRatio.FIT) MaterialTheme.colorScheme.primary else Color.White,
                            fontSize = 10.sp,  //  缩小字体
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)  //  缩小 padding
                        )
                    }
                }
                
                //  [新增] 竖屏模式弹幕开关和清晰度
                if (!isFullscreen) {
                    Spacer(modifier = Modifier.width(2.dp))  //  缩小间距
                    
                    IconButton(
                        onClick = onDanmakuToggle,
                        modifier = Modifier.size(26.dp)  //  缩小按钮
                    ) {
                        Icon(
                            if (danmakuEnabled) CupertinoIcons.Default.TextBubble else CupertinoIcons.Outlined.TextBubble,
                            contentDescription = if (danmakuEnabled) "关闭弹幕" else "开启弹幕",
                            tint = if (danmakuEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)  //  缩小图标
                        )
                    }
                    
                    // 📱 清晰度已移到顶部左上角，此处不再显示
                }
            }
            
            // 📱 右侧：全屏按钮
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 竖屏视频：显示"竖屏"文字按钮 + 横屏全屏图标
                if (isVerticalVideo && !isFullscreen) {
                    // 📱 竖屏全屏文字按钮 - 风格与倍速/比例按钮一致
                    Surface(
                        onClick = onPortraitFullscreen,
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "竖屏",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
                
                // 📲 [新增] 小窗按钮 - 仅竖屏非全屏时显示
                if (!isFullscreen) {
                    IconButton(
                        onClick = onPipClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            CupertinoIcons.Default.Pip,
                            contentDescription = "小窗播放",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                //  横屏全屏按钮 - 始终显示
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isFullscreen) CupertinoIcons.Default.ArrowDownRightAndArrowUpLeft else CupertinoIcons.Default.ArrowUpLeftAndArrowDownRight,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * Video Progress Bar - 自定义细进度条（支持拖动预览和章节标记）
 */
@Composable
fun VideoProgressBar(
    currentPosition: Long,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit = {},  //  [新增] 拖动开始回调（用于清除弹幕）
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData? = null,
    // 📖 [新增] 视频章节数据
    viewPoints: List<com.android.purebilibili.data.model.response.ViewPoint> = emptyList(),
    currentChapter: String? = null,
    onChapterClick: () -> Unit = {}
) {
    val progress = if (duration > 0) currentPosition.toFloat() / duration else 0f
    val bufferedProgress = if (duration > 0) bufferedPosition.toFloat() / duration else 0f
    var tempProgress by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var containerWidth by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(progress) {
        if (!isDragging) {
            tempProgress = progress
        }
    }
    
    val displayProgress = if (isDragging) tempProgress else progress
    val primaryColor = MaterialTheme.colorScheme.primary
    
    // 计算拖动时的目标时间
    val targetPositionMs = (tempProgress * duration).toLong()
    
    // 根据是否有章节标签和是否正在拖动计算高度
    val baseHeight = if (currentChapter != null) 40.dp else 24.dp
    val containerHeight = if (isDragging && videoshotData != null) 120.dp else baseHeight

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(containerHeight)  // 动态高度
            .pointerInput(Unit) {
                containerWidth = size.width.toFloat()
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek((newProgress * duration).toLong())
                }
            }
            .pointerInput(Unit) {
                containerWidth = size.width.toFloat()
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        tempProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        dragOffsetX = offset.x
                        onSeekStart()  //  拖动开始时清除弹幕
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        tempProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragOffsetX = change.position.x
                    },
                    onDragEnd = {
                        isDragging = false
                        onSeek((tempProgress * duration).toLong())
                    },
                    onDragCancel = {
                        isDragging = false
                        tempProgress = progress
                    }
                )
            }
    ) {
        // 🖼️ 拖动时显示预览气泡
        if (isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(bottom = 8.dp)
            ) {
                if (videoshotData != null && videoshotData.isValid) {
                    com.android.purebilibili.feature.video.ui.components.SeekPreviewBubble(
                        videoshotData = videoshotData,
                        targetPositionMs = targetPositionMs,
                        currentPositionMs = currentPosition,
                        durationMs = duration,
                        offsetX = dragOffsetX,
                        containerWidth = containerWidth
                    )
                } else {
                    // 无预览图时使用简化版气泡
                    com.android.purebilibili.feature.video.ui.components.SeekPreviewBubbleSimple(
                        targetPositionMs = targetPositionMs,
                        currentPositionMs = currentPosition,
                        offsetX = dragOffsetX,
                        containerWidth = containerWidth
                    )
                }
            }
        }
        
        // 进度条本体（放在底部）
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
        ) {
            // 📖 当前章节标签（如有）
            if (currentChapter != null) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onChapterClick)
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        CupertinoIcons.Default.ListBullet,
                        contentDescription = "章节",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = currentChapter,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // 背景轨道（带章节分隔线）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(1.5.dp))
                        .drawWithContent {
                            drawContent()
                            // 📖 绘制章节分隔线
                            if (duration > 0 && viewPoints.isNotEmpty()) {
                                viewPoints.forEach { point ->
                                    val position = point.fromMs.toFloat() / duration
                                    if (position > 0.01f && position < 0.99f) {
                                        val x = size.width * position
                                        drawLine(
                                            color = Color.White.copy(alpha = 0.8f),
                                            start = Offset(x, 0f),
                                            end = Offset(x, size.height),
                                            strokeWidth = 2f
                                        )
                                    }
                                }
                            }
                        }
                )
                
                // 缓冲进度
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedProgress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(1.5.dp))
                )
                
                // 当前进度
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(primaryColor, RoundedCornerShape(1.5.dp))
                )
                
                // 滑块（圆点）- 拖动时放大
                Box(
                    modifier = Modifier
                        .fillMaxWidth(displayProgress.coerceIn(0f, 1f))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(if (isDragging) 16.dp else 12.dp)
                            .offset(x = if (isDragging) 8.dp else 6.dp)
                            .background(primaryColor, androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
        }
    }
}
