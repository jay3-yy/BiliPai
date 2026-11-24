package com.android.purebilibili.feature.video.ui.section

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.components.AppButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

// 独立的提示渲染边界，状态与手势处理仍由播放器持有；各材质沿用原有组件与布局。
@Composable
internal fun PlayerSeekPreview(
    visible: Boolean,
    videoshotData: com.android.purebilibili.data.model.response.VideoshotData?,
    player: Player,
    isPortraitFullscreen: Boolean,
    seekTargetTime: Long,
    startPosition: Long,
    modifier: Modifier = Modifier,
) {
    if (visible) {
        // 🖼️ Seek 模式：显示带缩略图的预览气泡
        // zIndex must sit above forced return cover (100f) so landscape seek feedback is never buried.
        BoxWithConstraints(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            if (videoshotData != null && videoshotData.isValid) {
                if (isPortraitFullscreen) {
                    com.android.purebilibili.feature.video.ui.components.CompactSeekPreview(
                        videoshotData = videoshotData,
                        targetPositionMs = seekTargetTime,
                        durationMs = player.duration,
                        // 使用播放器实际视频宽比，避免横屏内容预览被压扁/裁切
                        videoAspectRatio = player.videoSize.takeIf { it.width > 0 && it.height > 0 }
                            ?.let { it.width.toFloat() / it.height.toFloat() }
                            ?: com.android.purebilibili.feature.video.ui.components.PORTRAIT_SEEK_PREVIEW_ASPECT_RATIO,
                        containerHeightDp = maxHeight.value.toInt()
                    )
                } else {
                    // 普通播放器保留横向预览；竖屏全屏统一使用大尺寸 9:16 预览。
                    com.android.purebilibili.feature.video.ui.components.SeekPreviewBubble(
                        videoshotData = videoshotData,
                        targetPositionMs = seekTargetTime,
                        currentPositionMs = startPosition,
                        durationMs = player.duration,
                        offsetX = 0f,
                        containerWidth = 0f,
                        placement = com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement.Centered,
                        containerHeightDp = maxHeight.value.toInt(),
                    )
                }
            } else {
                com.android.purebilibili.feature.video.ui.components.SeekPreviewBubbleSimple(
                    targetPositionMs = seekTargetTime,
                    currentPositionMs = startPosition,
                    offsetX = 0f,
                    containerWidth = 0f,
                    placement = com.android.purebilibili.feature.video.ui.components.SeekPreviewBubblePlacement.Centered
                )
            }
        }
    }
}

@Composable
internal fun PlayerSeekCancelHint(
    visible: Boolean,
    gestureMotionSpec: VideoGestureMotionSpec,
    modifier: Modifier = Modifier,
) {
    //  Seek 逃生口提示：拖动进度时手指进入顶部角落，松手取消进退
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(
            animationSpec = tween(gestureMotionSpec.orientationHintEnterFadeDurationMillis)
        ),
        exit = fadeOut(
            animationSpec = tween(gestureMotionSpec.orientationHintExitDurationMillis)
        )
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            shadowElevation = 4.dp
        ) {
            AppText(
                text = "松开手指，取消进退",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
internal fun PlayerOrientationHint(
    visible: Boolean,
    orientationHintText: String,
    gestureMotionSpec: VideoGestureMotionSpec,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(gestureMotionSpec.orientationHintEnterFadeDurationMillis)) +
            scaleIn(
                initialScale = 0.85f,
                animationSpec = tween(gestureMotionSpec.orientationHintEnterTransformDurationMillis)
            ) +
            slideInVertically(
                initialOffsetY = { -it / 5 },
                animationSpec = tween(gestureMotionSpec.orientationHintEnterTransformDurationMillis)
            ),
        exit = fadeOut(animationSpec = tween(gestureMotionSpec.orientationHintExitDurationMillis)) +
            scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(gestureMotionSpec.orientationHintExitDurationMillis)
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIcon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
            AppText(
                text = orientationHintText,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}

@Composable
internal fun PlayerSeekFeedback(
    visible: Boolean,
    seekFeedbackText: String?,
    uiLayoutPolicy: VideoPlayerUiLayoutPolicy,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(initialScale = 0.5f) + fadeIn(),
        exit = scaleOut(targetScale = 0.8f) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .size(uiLayoutPolicy.seekFeedbackSizeDp.dp)
                .background(Color.Black.copy(0.75f), AppShapes.container(ContainerLevel.Floating)),
            contentAlignment = Alignment.Center
        ) {
            AppText(
                text = seekFeedbackText ?: "",
                color = com.android.purebilibili.core.ui.MediaContrastPalette.Foreground,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
internal fun PlayerRestorePictureButton(
    visible: Boolean,
    uiLayoutPolicy: VideoPlayerUiLayoutPolicy,
    onRestore: () -> Unit,
    label: String = "还原画面",
    modifier: Modifier = Modifier,
) {
    //  [新增] 缩放还原按钮 (仅在放大时显示)
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        AppButton(
            onClick = onRestore,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.6f),
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(
                horizontal = uiLayoutPolicy.restoreButtonHorizontalPaddingDp.dp,
                vertical = uiLayoutPolicy.restoreButtonVerticalPaddingDp.dp
            ),
            shape = AppShapes.container(ContainerLevel.Floating)
        ) {
            AppIcon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = label,
                modifier = Modifier.size(uiLayoutPolicy.restoreButtonIconSizeDp.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AppText(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
internal fun PlayerSpeedLockZones(
    visible: Boolean,
    longPressSpeedLockSensitivity: LongPressSpeedLockSensitivityPolicy,
    gestureMotionSpec: VideoGestureMotionSpec,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(gestureMotionSpec.longPressHintDurationMillis)),
        exit = fadeOut(animationSpec = tween(gestureMotionSpec.longPressHintDurationMillis))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val lockZoneVisual = resolveLongPressSpeedLockZoneVisualPolicy()
            val zoneModifier = Modifier
                .fillMaxWidth()
                .height(longPressSpeedLockSensitivity.lockZoneHeightDp.dp)
            val markerColor = AppContentColors.accent()
            Box(modifier = zoneModifier.align(Alignment.TopCenter)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(lockZoneVisual.edgeGradientHeightDp.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    markerColor.copy(alpha = lockZoneVisual.edgeGradientAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(lockZoneVisual.centerMarkerWidthFraction)
                        .height(lockZoneVisual.centerMarkerHeightDp.dp)
                        .clip(AppShapes.container(ContainerLevel.Pill))
                        .background(markerColor.copy(alpha = lockZoneVisual.centerMarkerAlpha))
                )
            }
            Box(
                modifier = zoneModifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = lockZoneVisual.bottomVisualOffsetDp.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(lockZoneVisual.edgeGradientHeightDp.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    markerColor.copy(alpha = lockZoneVisual.edgeGradientAlpha)
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(lockZoneVisual.centerMarkerWidthFraction)
                        .height(lockZoneVisual.centerMarkerHeightDp.dp)
                        .clip(AppShapes.container(ContainerLevel.Pill))
                        .background(markerColor.copy(alpha = lockZoneVisual.centerMarkerAlpha))
                )
            }
        }
    }
}

@Composable
internal fun PlayerLongPressSpeedHint(
    visible: Boolean,
    longPressSpeedLocked: Boolean,
    effectiveLongPressSpeed: Float,
    longPressSpeedHintScale: Float,
    longPressSpeedHintAlpha: Float,
    longPressSpeedHintCloseEnabled: Boolean,
    gestureMotionSpec: VideoGestureMotionSpec,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 长按倍速提示保持轻量，避免遮挡视频主体内容。
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(gestureMotionSpec.longPressHintDurationMillis)) +
            slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut(animationSpec = tween(gestureMotionSpec.longPressHintDurationMillis)) +
            slideOutVertically(targetOffsetY = { -it })
    ) {
        AppSurface(
            shape = AppShapes.scaledContainer(ContainerLevel.Field, longPressSpeedHintScale),
            color = Color.Black.copy(alpha = longPressSpeedHintAlpha),
            contentColor = Color.White,
            tonalElevation = 0.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppText(
                    text = if (longPressSpeedLocked) {
                        "已锁定 ${effectiveLongPressSpeed}x"
                    } else {
                        "倍速播放中 ${effectiveLongPressSpeed}x"
                    },
                    modifier = Modifier.padding(
                        start = 8.dp * longPressSpeedHintScale,
                        end = if (shouldShowLongPressSpeedHintCloseButton(longPressSpeedHintCloseEnabled)) {
                            2.dp
                        } else {
                            8.dp * longPressSpeedHintScale
                        },
                        top = 5.dp * longPressSpeedHintScale,
                        bottom = 5.dp * longPressSpeedHintScale,
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize * longPressSpeedHintScale,
                        fontWeight = FontWeight.Medium
                    )
                )
                if (shouldShowLongPressSpeedHintCloseButton(longPressSpeedHintCloseEnabled)) {
                    AppIconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp * longPressSpeedHintScale),
                    ) {
                        AppIcon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "关闭倍速提示",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp * longPressSpeedHintScale),
                        )
                    }
                }
            }
        }
    }
}
