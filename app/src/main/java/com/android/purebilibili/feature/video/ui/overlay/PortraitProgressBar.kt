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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.android.purebilibili.data.model.response.VideoshotData
import com.android.purebilibili.feature.video.ui.components.CompactSeekPreview
import com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement
import com.android.purebilibili.feature.video.ui.components.SeekPreviewBubbleSimple
import com.android.purebilibili.feature.video.ui.components.resolveCompactSeekPreviewSize
import com.android.purebilibili.feature.video.ui.components.resolveSeekPreviewBubbleOffsetPx
import kotlin.math.roundToInt

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
    videoshotData: VideoshotData? = null,
    videoAspectRatio: Float? = null,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val layoutPolicy = remember(configuration.screenWidthDp) {
        resolvePortraitProgressBarLayoutPolicy(
            widthDp = configuration.screenWidthDp
        )
    }
    val touchHeight = layoutPolicy.touchAreaHeightDp.dp * PlayerChromeLayout.FullscreenHeightScale

    PlayerControlTouchBounds(height = touchHeight) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Use the larger margin, never fixed padding plus the gesture region.
            .windowInsetsPadding(
                WindowInsets.systemGestures.only(WindowInsetsSides.Horizontal).union(
                    WindowInsets(
                        left = PlayerChromeLayout.SeekEdgePaddingDp.dp,
                        right = PlayerChromeLayout.SeekEdgePaddingDp.dp,
                    )
                )
            )
            .padding(bottom = layoutPolicy.bottomPaddingDp.dp * PlayerChromeLayout.FullscreenHeightScale)
            .height(touchHeight),
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
            bufferProgress = bufferProgress,
            videoshotData = videoshotData,
            videoAspectRatio = videoAspectRatio
        )
    }
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
    bufferProgress: Float = 0f,
    videoshotData: VideoshotData? = null,
    videoAspectRatio: Float? = null,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    var dragTargetPositionMs by remember { mutableLongStateOf(seekPositionMs.coerceAtLeast(0L)) }
    var containerWidth by remember { mutableFloatStateOf(0f) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnSeekStart by rememberUpdatedState(onSeekStart)
    val currentOnSeekDragStart by rememberUpdatedState(onSeekDragStart)
    val currentOnSeekDragUpdate by rememberUpdatedState(onSeekDragUpdate)
    val currentOnSeekDragCancel by rememberUpdatedState(onSeekDragCancel)
    val activePositionMs = resolveSeekPreviewTargetPositionMs(
        displayPositionMs = seekPositionMs,
        dragTargetPositionMs = dragTargetPositionMs,
        isSeekScrubbing = isSeekScrubbing
    )
    val displayProgress = resolveProgressFraction(
        positionMs = activePositionMs,
        durationMs = duration
    ).takeIf { duration > 0L } ?: progress
    val currentPositionMs = seekPositionMs.coerceAtLeast(0L)
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
    val thumbSizePx = with(LocalDensity.current) { thumbSize.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .onSizeChanged { containerWidth = it.width.toFloat() }
            .pointerInput(duration) {
                var dragInProgress = false
                try {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragInProgress = true
                            val targetPositionMs = resolveSeekPositionFromTouch(
                                touchX = offset.x,
                                containerWidthPx = size.width.toFloat(),
                                durationMs = duration
                            )
                            dragTargetPositionMs = targetPositionMs
                            currentOnSeekStart()
                            currentOnSeekDragStart(targetPositionMs)
                        },
                        onDragEnd = {
                            dragInProgress = false
                            val committedProgress = if (duration > 0L) {
                                dragTargetPositionMs.toFloat() / duration.toFloat()
                            } else {
                                0f
                            }
                            currentOnSeek(committedProgress.coerceIn(0f, 1f))
                        },
                        onDragCancel = {
                            if (dragInProgress) {
                                dragInProgress = false
                                currentOnSeekDragCancel()
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val targetPositionMs = resolveSeekPositionFromTouch(
                                touchX = change.position.x,
                                containerWidthPx = size.width.toFloat(),
                                durationMs = duration
                            )
                            dragTargetPositionMs = targetPositionMs
                            currentOnSeekDragUpdate(targetPositionMs)
                        }
                    )
                } finally {
                    if (dragInProgress) currentOnSeekDragCancel()
                }
            }
            // 也支持点击跳转
            .pointerInput(duration) {
                detectTapGestures(
                    onTap = { offset ->
                        val targetPositionMs = resolveSeekPositionFromTouch(
                            touchX = offset.x,
                            containerWidthPx = size.width.toFloat(),
                            durationMs = duration
                        )
                        dragTargetPositionMs = targetPositionMs
                        currentOnSeekStart()
                        currentOnSeekDragStart(targetPositionMs)
                        currentOnSeekDragUpdate(targetPositionMs)
                        val committedProgress = if (duration > 0L) {
                            targetPositionMs.toFloat() / duration.toFloat()
                        } else {
                            0f
                        }
                        currentOnSeek(committedProgress.coerceIn(0f, 1f))
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

        Box(
            modifier = Modifier
                .fillMaxWidth(bufferProgress.coerceIn(0f, 1f))
                .height(barHeight)
                .background(
                    Color.White.copy(alpha = 0.55f),
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
            val previewPositionMs = dragTargetPositionMs.coerceAtLeast(0L)
            val thumbOffsetX = (containerWidth * displayProgress - thumbSizePx / 2f)
                .coerceIn(0f, (containerWidth - thumbSizePx).coerceAtLeast(0f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterStart)
            ) {
                val thumbModifier = Modifier
                    .offset { IntOffset(thumbOffsetX.roundToInt(), 0) }
                    .align(Alignment.CenterStart)
                Box(thumbModifier.size(thumbSize).background(Color.White, CircleShape))
            }

            if (videoshotData != null && videoshotData.isValid) {
                val isPortraitVideo = videoAspectRatio
                    ?.let { it.isFinite() && it > 0f && it < 1f }
                    ?: false
                val compactPreviewOffsetY = if (isPortraitVideo) {
                    layoutPolicy.compactPortraitPreviewOffsetYDp
                } else {
                    layoutPolicy.compactLandscapePreviewOffsetYDp
                }
                val compactPreviewSize = remember(
                    videoshotData.img_x_size,
                    videoshotData.img_y_size,
                    configuration.screenWidthDp,
                    videoAspectRatio
                ) {
                    resolveCompactSeekPreviewSize(
                        sourceWidthPx = videoshotData.img_x_size,
                        sourceHeightPx = videoshotData.img_y_size,
                        screenWidthDp = configuration.screenWidthDp,
                        videoAspectRatio = videoAspectRatio
                    )
                }
                val previewWidthPx = with(LocalDensity.current) {
                    compactPreviewSize.widthDp.dp.toPx()
                }
                val previewOffsetX = resolveSeekPreviewBubbleOffsetPx(
                    placement = SeekPreviewBubblePlacement.Anchored,
                    offsetX = containerWidth * displayProgress,
                    containerWidth = containerWidth,
                    bubbleWidthPx = previewWidthPx
                )
                val previewOffsetYPx = with(LocalDensity.current) {
                    compactPreviewOffsetY.dp.roundToPx()
                }
                CompactSeekPreview(
                    videoshotData = videoshotData,
                    targetPositionMs = previewPositionMs,
                    durationMs = duration,
                    videoAspectRatio = videoAspectRatio,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset { IntOffset(previewOffsetX, previewOffsetYPx) }
                )
            } else {
                SeekPreviewBubbleSimple(
                    targetPositionMs = previewPositionMs,
                    currentPositionMs = currentPositionMs,
                    offsetX = containerWidth * displayProgress,
                    containerWidth = containerWidth,
                    placement = SeekPreviewBubblePlacement.Anchored,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = layoutPolicy.bubbleOffsetYDp.dp)
                )
            }
        }
    }
}
