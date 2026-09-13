package com.android.purebilibili.feature.audio.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.QueueMusic
import com.android.purebilibili.core.theme.LocalAppUiStyle
import com.android.purebilibili.core.theme.LocalSettingsLiquidGlassEnabled
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.ui.components.AppIconButton
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.motion.rememberSystemReduceMotion
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.android.purebilibili.feature.home.components.LiquidGlassTuning
import com.android.purebilibili.feature.home.components.LocalLiquidGlassRenderConfig
import com.android.purebilibili.feature.home.components.biliPaiFloatingDockShell
import com.android.purebilibili.feature.home.components.resolveSharedBottomBarCapsuleShape
import kotlin.math.abs
import top.yukonga.miuix.kmp.blur.Backdrop as MiuixBackdrop

internal data class AudioNowPlayingBarState(
    val title: String,
    val artist: String,
    val artistAvatarUrl: String = "",
    val coverUrl: String,
    val isPlaying: Boolean,
    val playbackSpeed: Float = 1f
)

@Composable
internal fun AudioNowPlayingBar(
    state: AudioNowPlayingBarState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onDismiss: () -> Unit,
    expandDestinationLabel: String = "听视频",
    glassEnabled: Boolean = LocalSettingsLiquidGlassEnabled.current,
    miuixBackdrop: MiuixBackdrop? = null,
    liquidGlassTuning: LiquidGlassTuning = LocalLiquidGlassRenderConfig.current.tuning,
    liftAboveBottomBar: Boolean = true,
    consumeNavigationBarsPadding: Boolean = true,
    dockHosted: Boolean = false,
    dockMergeProgress: Float = 0f,
    iconOnlyProgress: Float = 0f,
    surfaceMergeProgress: Float = dockMergeProgress,
    modifier: Modifier = Modifier
) {
    val mergeProgress = dockMergeProgress.coerceIn(0f, 1f)
    val searchProgress = iconOnlyProgress.coerceIn(0f, 1f)
    val primaryContentProgress = 1f - searchProgress
    val supplementalContentProgress = (1f - mergeProgress) * primaryContentProgress
    val chrome = resolveMusicPlayerChromeSpec(
        uiStyle = LocalAppUiStyle.current,
        glassEnabled = glassEnabled
    )
    val shape = resolveSharedBottomBarCapsuleShape()
    val containerColor = AppSurfaceTokens.surfaceContainer()
    val glassActive = glassEnabled && miuixBackdrop != null
    val reduceMotion = rememberSystemReduceMotion()
    val coverRotationDegrees = rememberMusicArtworkRotationDegrees(
        active = shouldRotateMusicArtwork(
            isPlaying = state.isPlaying,
            reduceMotion = reduceMotion
        ),
        contentKey = state.coverUrl,
        playbackSpeed = state.playbackSpeed
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (consumeNavigationBarsPadding) Modifier.navigationBarsPadding() else Modifier)
            .padding(
                start = if (dockHosted) 0.dp else chrome.horizontalPaddingDp.dp,
                end = if (dockHosted) 0.dp else chrome.horizontalPaddingDp.dp,
                bottom = when {
                    dockHosted -> 0.dp
                    liftAboveBottomBar -> 72.dp
                    !glassActive && chrome.uiStyle == com.android.purebilibili.core.theme.AppUiStyle.MATERIAL3 -> 16.dp
                    else -> 8.dp
                }
            )
            .clip(shape)
            .semantics { contentDescription = "当前视频：${state.title}，打开$expandDestinationLabel" }
            .clickable(onClick = onExpand)
            .audioNowPlayingSkipGesture(
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious
            ),
    ) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { alpha = 1f - surfaceMergeProgress.coerceIn(0f, 1f) }
                .biliPaiFloatingDockShell(
                    backdrop = miuixBackdrop,
                    containerColor = containerColor,
                    pressProgress = 0f,
                    shape = shape,
                    enabled = glassActive,
                    liquidGlassTuning = liquidGlassTuning,
                )
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (dockHosted) 56.dp else 64.dp)
                .padding(horizontal = (10f * primaryContentProgress).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (searchProgress >= 0.999f) Arrangement.Center else Arrangement.Start,
        ) {
            AsyncImage(
                model = state.coverUrl,
                contentDescription = null,
                modifier = Modifier
                    .size((40f - 8f * mergeProgress).dp)
                    .graphicsLayer { rotationZ = coverRotationDegrees() }
                    .clip(if (chrome.coverShapeIsCircle) CircleShape else AppShapes.container(ContainerLevel.Field)),
                contentScale = ContentScale.Crop
            )
            if (primaryContentProgress > 0.001f) {
                Spacer(Modifier.width(((10f - 4f * mergeProgress) * primaryContentProgress).dp))
                Column(
                    Modifier
                        .weight(1f)
                        .graphicsLayer { alpha = primaryContentProgress },
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppText(
                        text = state.title,
                        modifier = if (state.isPlaying) {
                            Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                        } else {
                            Modifier
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (supplementalContentProgress > 0.001f) {
                        Box(
                            modifier = Modifier
                                .height((20f * supplementalContentProgress).dp)
                                .clipToBounds()
                                .graphicsLayer { alpha = supplementalContentProgress },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                if (state.artistAvatarUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = state.artistAvatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                AppText(
                                    text = state.artist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .width((48f * primaryContentProgress).dp)
                        .height(48.dp)
                        .clipToBounds()
                        .graphicsLayer {
                            alpha = primaryContentProgress
                            scaleX = primaryContentProgress
                            scaleY = primaryContentProgress
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AppIconButton(onClick = onPlayPause, modifier = Modifier.size(48.dp)) {
                        AppIcon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "暂停" else "播放",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                if (supplementalContentProgress > 0.001f) {
                    Box(
                        modifier = Modifier
                            .width((48f * supplementalContentProgress).dp)
                            .height(48.dp)
                            .clipToBounds()
                            .graphicsLayer {
                                alpha = supplementalContentProgress
                                scaleX = supplementalContentProgress
                                scaleY = supplementalContentProgress
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIconButton(onClick = onExpand, modifier = Modifier.size(48.dp)) {
                            AppIcon(
                                Icons.Outlined.QueueMusic,
                                contentDescription = "打开$expandDestinationLabel",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .width((48f * supplementalContentProgress).dp)
                            .height(48.dp)
                            .clipToBounds()
                            .graphicsLayer {
                                alpha = supplementalContentProgress
                                scaleX = supplementalContentProgress
                                scaleY = supplementalContentProgress
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppIconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            AppIcon(
                                Icons.Filled.Close,
                                contentDescription = "关闭听视频条",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.audioNowPlayingSkipGesture(
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit
): Modifier = pointerInput(onSkipNext, onSkipPrevious) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragEnd = {
            if (abs(totalDrag) > 64f) {
                if (totalDrag < 0f) onSkipNext() else onSkipPrevious()
            }
            totalDrag = 0f
        },
        onDragCancel = { totalDrag = 0f }
    ) { _, dragAmount ->
        totalDrag += dragAmount
    }
}
