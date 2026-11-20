package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.ui.AppContentColors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.android.purebilibili.core.ui.components.AppIcon
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.feature.video.ui.gesture.TwoFingerSpeedGestureMode
import dev.chrisbanes.haze.HazeState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.SwapVert
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.ContainerLevel

@Composable
fun BoxScope.TwoFingerSpeedFeedbackOverlay(
    visible: Boolean,
    speed: Float,
    mode: TwoFingerSpeedGestureMode,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier
) {
    if (mode == TwoFingerSpeedGestureMode.Off) return

    val shape = AppShapes.container(ContainerLevel.Floating)
    val cueText = when (mode) {
        TwoFingerSpeedGestureMode.Vertical -> "双指上下调速"
        TwoFingerSpeedGestureMode.Horizontal -> "双指左右调速"
        TwoFingerSpeedGestureMode.Off -> ""
    }
    val cueIcon = when (mode) {
        TwoFingerSpeedGestureMode.Vertical -> Icons.Outlined.SwapVert
        TwoFingerSpeedGestureMode.Horizontal -> Icons.Outlined.SwapHoriz
        TwoFingerSpeedGestureMode.Off -> Icons.Outlined.SwapVert
    }
    val gradientColors = listOf(
        AppContentColors.accent().copy(alpha = 0.18f),
        MaterialTheme.colorScheme.surface.copy(alpha = 0.10f),
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
        modifier = modifier
            .align(Alignment.TopCenter)
            .padding(top = 18.dp)
    ) {
        // 没有背景采样的播放器也需要在亮画面上保持文字对比度。
        val blurModifier = if (hazeState != null) Modifier.unifiedBlur(hazeState) else Modifier
        AppSurface(
            color = if (hazeState != null) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.06f)
            } else {
                Color.Black.copy(alpha = 0.72f)
            },
            contentColor = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            shape = shape,
            modifier = Modifier
                .clip(shape)
                .then(blurModifier)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = shape
                )
                .widthIn(min = 176.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Brush.horizontalGradient(gradientColors))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        AppIcon(
                            imageVector = cueIcon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.86f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        AppText(
                            text = cueText,
                            color = Color.White.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    AppText(
                        text = PlaybackSpeed.formatSpeedFull(speed),
                        color = Color.White.copy(alpha = 0.98f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
