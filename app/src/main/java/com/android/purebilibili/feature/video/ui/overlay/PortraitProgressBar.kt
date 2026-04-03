package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.feature.video.playback.session.PlaybackSeekUiState
import com.android.purebilibili.feature.video.playback.session.cancelPlaybackSeekSession
import com.android.purebilibili.feature.video.playback.session.finishPlaybackSeekSession
import com.android.purebilibili.feature.video.playback.session.resolvePlaybackSeekDisplayProgress
import com.android.purebilibili.feature.video.playback.session.settlePlaybackSeekSession
import com.android.purebilibili.feature.video.playback.session.shouldHoldPlaybackSeekSettledProgress
import com.android.purebilibili.feature.video.playback.session.startPlaybackSeekSession
import com.android.purebilibili.feature.video.playback.session.updatePlaybackSeekSession

private const val PORTRAIT_PROGRESS_SETTLED_TOLERANCE = 0.01f

internal fun shouldHoldPortraitSettledProgress(
    progress: Float,
    pendingSettledProgress: Float?,
    tolerance: Float = PORTRAIT_PROGRESS_SETTLED_TOLERANCE
): Boolean {
    return shouldHoldPlaybackSeekSettledProgress(
        playbackProgress = progress,
        pendingSettledProgress = pendingSettledProgress,
        tolerance = tolerance
    )
}

internal fun resolvePortraitProgressDisplayProgress(
    progress: Float,
    dragProgress: Float,
    isDragging: Boolean,
    pendingSettledProgress: Float?
): Float {
    return resolvePlaybackSeekDisplayProgress(
        playbackProgress = progress,
        state = PlaybackSeekUiState(
            isScrubbing = isDragging,
            dragProgress = dragProgress,
            pendingSettledProgress = pendingSettledProgress
        ),
        tolerance = PORTRAIT_PROGRESS_SETTLED_TOLERANCE
    )
}

/**
 * 竖屏模式下的底部容器 (含进度条)
 */
@Composable
fun PortraitBottomContainer(
    progress: Float,
    duration: Long,
    bufferProgress: Float = 0f,
    seekPositionMs: Long = (progress * duration).toLong(),
    isSeekScrubbing: Boolean = false,
    onSeek: (Long) -> Unit,
    onSeekStart: () -> Unit,
    onSeekDragStart: (Long) -> Unit = {},
    onSeekDragUpdate: (Long) -> Unit = {},
    onSeekDragCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolvePortraitProgressBarLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                bottom = layoutPolicy.bottomPaddingDp.dp,
                start = layoutPolicy.horizontalPaddingDp.dp,
                end = layoutPolicy.horizontalPaddingDp.dp
            )
            .height(layoutPolicy.touchAreaHeightDp.dp)
        ,
        contentAlignment = Alignment.Center
    ) {
         ThinWigglyProgressBar(
            progress = progress,
            seekPositionMs = seekPositionMs,
            isSeekScrubbing = isSeekScrubbing,
            layoutPolicy = layoutPolicy,
            onSeek = { fraction ->
                 val target = (fraction * duration).toLong()
                 onSeek(target)
            },
            onSeekStart = onSeekStart,
            onSeekDragStart = onSeekDragStart,
            onSeekDragUpdate = onSeekDragUpdate,
            onSeekDragCancel = onSeekDragCancel,
            duration = duration, // 传递时长用于显示
            bufferProgress = bufferProgress
        )
    }
}

/**
 * 抖音风格细条进度条
 * - 平时：细条 (2dp)
 * - 拖拽中：变粗 (8dp) + 显示当前时间
 */
@Composable
fun ThinWigglyProgressBar(
    progress: Float,
    seekPositionMs: Long,
    isSeekScrubbing: Boolean,
    layoutPolicy: PortraitProgressBarLayoutPolicy,
    onSeek: (Float) -> Unit,
    onSeekStart: () -> Unit,
    onSeekDragStart: (Long) -> Unit = {},
    onSeekDragUpdate: (Long) -> Unit = {},
    onSeekDragCancel: () -> Unit = {},
    duration: Long,
    bufferProgress: Float = 0f
) {
    val displayProgress = if (duration > 0L) {
        seekPositionMs.toFloat() / duration.toFloat()
    } else {
        progress
    }
    var dragTargetPositionMs by remember { mutableLongStateOf(seekPositionMs.coerceAtLeast(0L)) }
    LaunchedEffect(seekPositionMs, isSeekScrubbing) {
        if (!isSeekScrubbing) {
            dragTargetPositionMs = seekPositionMs.coerceAtLeast(0L)
        }
    }
    
    // 动画状态
    val barHeight by animateDpAsState(
        targetValue = if (isSeekScrubbing) {
            layoutPolicy.draggingTrackHeightDp.dp
        } else {
            layoutPolicy.idleTrackHeightDp.dp
        },
        label = "barHeight"
    )
    
    val thumbSize by animateDpAsState(
        targetValue = if (isSeekScrubbing) layoutPolicy.draggingThumbSizeDp.dp else 0.dp,
        label = "thumbSize"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val targetPositionMs = if (duration > 0L) {
                            ((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong()
                        } else {
                            0L
                        }
                        dragTargetPositionMs = targetPositionMs
                        onSeekStart()
                        onSeekDragStart(targetPositionMs)
                    },
                    onDragEnd = {
                        val committedProgress = if (duration > 0L) {
                            dragTargetPositionMs.toFloat() / duration.toFloat()
                        } else {
                            0f
                        }
                        onSeek(committedProgress.coerceIn(0f, 1f))
                    },
                    onDragCancel = {
                        onSeekDragCancel()
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val targetPositionMs = if (duration > 0L) {
                            ((dragTargetPositionMs.toFloat() / duration.toFloat()) + dragAmount / size.width)
                                .coerceIn(0f, 1f)
                                .times(duration.toFloat())
                                .toLong()
                        } else {
                            0L
                        }
                        dragTargetPositionMs = targetPositionMs
                        onSeekDragUpdate(targetPositionMs)
                    }
                )
            }
            // 也支持点击跳转
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        val targetPositionMs = if (duration > 0L) {
                            ((offset.x / size.width).coerceIn(0f, 1f) * duration).toLong()
                        } else {
                            0L
                        }
                        dragTargetPositionMs = targetPositionMs
                        onSeekStart()
                        onSeekDragStart(targetPositionMs)
                        val released = tryAwaitRelease()
                        if (released) {
                            onSeekDragUpdate(targetPositionMs)
                            val committedProgress = if (duration > 0L) {
                                targetPositionMs.toFloat() / duration.toFloat()
                            } else {
                                0f
                            }
                            onSeek(committedProgress.coerceIn(0f, 1f))
                        } else {
                            onSeekDragCancel()
                        }
                    }
                ) 
            }
        ,
        contentAlignment = Alignment.CenterStart
    ) {
        // 背景轨道
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(
                    Color.White.copy(alpha = 0.3f),
                    RoundedCornerShape(layoutPolicy.trackCornerRadiusDp.dp)
                )
        )
        
        // 进度 (当前进度)
        Box(
            modifier = Modifier
                .fillMaxWidth(displayProgress)
                .height(barHeight)
                .background(
                    Color.White.copy(alpha = 0.9f),
                    RoundedCornerShape(layoutPolicy.trackCornerRadiusDp.dp)
                )
        )
        
        // 滑块 (Thumb) - 仅拖拽时显示
        if (isSeekScrubbing) {
            // 使用 Box + BiasAlignment 来定位滑块
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart
            ) {
                 val bias = (displayProgress * 2f) - 1f
                 
                 Box(
                     modifier = Modifier
                         .size(thumbSize)
                         .align(BiasAlignment(bias, 0f))
                         .background(Color.White, CircleShape)
                 )
            }
        
            // 拖拽时的气泡提示 (上方)
             // 计算时间文本
             val currentMs = (duration * displayProgress).toLong()
             val timeText = FormatUtils.formatDuration(currentMs) + " / " + FormatUtils.formatDuration(duration)
             
             Box(
                 modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = layoutPolicy.bubbleOffsetYDp.dp)
             ) {
                 Text(
                     text = timeText,
                     color = Color.White,
                     fontSize = layoutPolicy.bubbleFontSp.sp,
                     fontWeight = FontWeight.Bold,
                     style = MaterialTheme.typography.titleLarge,
                     modifier = Modifier
                         .background(
                             Color.Black.copy(alpha = 0.5f),
                             RoundedCornerShape(layoutPolicy.bubbleCornerRadiusDp.dp)
                         )
                         .padding(
                             horizontal = layoutPolicy.bubbleHorizontalPaddingDp.dp,
                             vertical = layoutPolicy.bubbleVerticalPaddingDp.dp
                         )
                 )
             }
        }
    }
}
