package com.android.purebilibili.feature.video.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalAppThemeConfig
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.ui.motion.AppMotionEasing

/** The central play/pause target follows the visibility of the rest of the player controls. */
@Composable
internal fun CenterPlaybackControl(
    visible: Boolean,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    diameter: Dp,
    modifier: Modifier = Modifier,
    viewportTopInset: Dp = 0.dp,
    isBuffering: Boolean = false,
) {
    val glass = LocalAppThemeConfig.current.liquidGlassEnabled
    // Inline chrome also covers the status-bar backdrop; that band is not part of the video viewport.
    Box(
        modifier = modifier.fillMaxSize().padding(top = viewportTopInset),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.testTag("player_play_pause"),
            enter = fadeIn(tween(200, easing = AppMotionEasing.Continuity)),
            exit = fadeOut(tween(200, easing = AppMotionEasing.Continuity)),
        ) {
            PlayerControlTouchBounds(height = diameter) {
                PlayerControlGlassDock(
                    enabled = true,
                    shape = CircleShape,
                    modifier = Modifier.size(diameter),
                ) {
                    AppSurface(
                        shape = CircleShape,
                        color = if (glass) Color.Transparent else Color.Black.copy(alpha = 0.38f),
                        border = if (glass) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        AppIconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Native icon-button implementations must not shift the glyph within the target.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                AppIcon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "暂停播放" else "开始播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(diameter * 0.48f),
                                )
                            }
                        }
                    }
                    if (isBuffering) {
                        BiliTvAnimatedBufferingIndicator(
                            tint = Color.White,
                            size = 20.dp,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp),
                        )
                    }
                }
            }
        }
    }
}
