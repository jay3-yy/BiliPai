package com.android.purebilibili.feature.home.components

import com.android.purebilibili.core.player.withAppVideoDecoderDiagnostics
import com.android.purebilibili.core.player.withAppVideoDecoderPreference

import com.android.purebilibili.core.ui.AppContentColors

import coil3.request.crossfade
import com.android.purebilibili.core.ui.components.AppHorizontalDivider
import com.android.purebilibili.core.ui.AdaptiveLoadingIndicator

import com.android.purebilibili.core.ui.AppSpacingTokens
import com.android.purebilibili.core.ui.AppChromeSizeTokens

import com.android.purebilibili.core.ui.MediaContrastPalette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.android.purebilibili.core.ui.rememberAppClearIcon
import com.android.purebilibili.core.ui.rememberAppPhotoIcon
import com.android.purebilibili.core.ui.rememberAppPlayIcon
import com.android.purebilibili.core.ui.rememberAppShareIcon
import com.android.purebilibili.core.ui.rememberAppVisibilityOffIcon
import com.android.purebilibili.core.ui.rememberAppWatchLaterIcon
import com.android.purebilibili.core.ui.AppShapes
import com.android.purebilibili.core.ui.AppSurfaceTokens
import com.android.purebilibili.core.ui.ContainerLevel
import com.android.purebilibili.core.ui.components.AppSurface
import com.android.purebilibili.core.util.FormatUtils
import com.android.purebilibili.data.model.response.VideoItem
import androidx.compose.material.icons.Icons
import com.android.purebilibili.core.ui.components.AppIcon
import com.android.purebilibili.core.util.HapticType
import com.android.purebilibili.core.util.rememberHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player

import androidx.compose.material.icons.rounded.Fullscreen
import com.android.purebilibili.core.ui.blur.unifiedBlur
import com.android.purebilibili.core.ui.blur.shouldAllowRenderEffectBackedHazeEffect
import com.android.purebilibili.core.ui.performance.isLowBlurBudgetForced
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.blur
import androidx.compose.ui.zIndex

internal fun shouldEnableSaveCoverAction(coverUrl: String): Boolean = coverUrl.isNotBlank()

internal fun shouldShowBlockCreatorAction(creatorMid: Long): Boolean = creatorMid > 0L

/**
 * iOS 3D Touch style Preview Dialog
 */
@Composable
fun VideoPreviewDialog(
    video: VideoItem,
    onDismiss: () -> Unit,
    onWatchLater: () -> Unit,
    onShare: () -> Unit,
    onSaveCover: (() -> Unit)? = null,
    onPlay: () -> Unit, // Navigate to Full Screen
    onNotInterested: (() -> Unit)? = null,
    isNotInterestedDissolving: Boolean = false,
    onNotInterestedDissolveComplete: () -> Unit = {},
    keepOpenDuringNotInterestedDissolve: Boolean = false,
    onBlockCreator: (() -> Unit)? = null,
    onGetPreviewUrl: suspend (String, Long) -> String? = { _, _ -> null }, // [New] Fetch Url
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    miuixBackdrop: Backdrop? = null,
    backgroundBlurEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val haptic = rememberHapticFeedback(com.android.purebilibili.core.ui.AppHapticScene.VIDEO_ACTIONS)
    val context = LocalContext.current
    val clearIcon = rememberAppClearIcon()
    val photoIcon = rememberAppPhotoIcon()
    val playIcon = rememberAppPlayIcon()
    val shareIcon = rememberAppShareIcon()
    val blockCreatorIcon = rememberAppVisibilityOffIcon()
    val watchLaterIcon = rememberAppWatchLaterIcon()
    val allowBackgroundBlur = backgroundBlurEnabled &&
        shouldAllowRenderEffectBackedHazeEffect(android.os.Build.VERSION.SDK_INT) &&
        !isLowBlurBudgetForced()
    val backgroundBlurRadiusPx = with(LocalDensity.current) { 24.dp.toPx() }
    val backgroundBlurModifier = when {
        allowBackgroundBlur && miuixBackdrop != null -> remember(miuixBackdrop, backgroundBlurRadiusPx) {
            Modifier.drawBackdrop(
                backdrop = miuixBackdrop,
                shape = { RectangleShape },
                effects = { blur(backgroundBlurRadiusPx, backgroundBlurRadiusPx) },
            )
        }
        allowBackgroundBlur && hazeState != null -> Modifier.unifiedBlur(hazeState = hazeState)
        else -> Modifier
    }

    // Playback State
    var isPlaying by remember(video.bvid, video.cid) { androidx.compose.runtime.mutableStateOf(false) }
    var videoUrl by remember(video.bvid, video.cid) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var isLoading by remember(video.bvid, video.cid) { androidx.compose.runtime.mutableStateOf(false) }
    val currentGetPreviewUrl by androidx.compose.runtime.rememberUpdatedState(onGetPreviewUrl)
    val previewWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()

    // Fetch URL when isPlaying becomes true
    androidx.compose.runtime.LaunchedEffect(video.bvid, video.cid, isPlaying, previewWorkActive) {
        if (!isPlaying || !previewWorkActive) {
            isLoading = false
        } else if (videoUrl == null) {
            isLoading = true
            try {
                videoUrl = kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                    currentGetPreviewUrl(video.bvid, video.cid)
                }
                if (videoUrl.isNullOrBlank()) {
                    isLoading = false
                    isPlaying = false
                    android.widget.Toast.makeText(context, "暂时无法预览，可点击立即播放", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                isLoading = false
                isPlaying = false
                android.widget.Toast.makeText(context, "预览加载失败，点击封面可重试", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handle Back Press manually since we are not in a Dialog anymore
    com.android.purebilibili.core.ui.LocalNavigationBackHandler(enabled = true) {
        if (!isNotInterestedDissolving) {
            if (isPlaying) isPlaying = false else onDismiss()
        }
    }

    Box(
        modifier = modifier
            .clickable(
                enabled = !isNotInterestedDissolving,
                interactionSource = null,
                indication = null,
                onClick = { onDismiss() } // Always dismiss on background click
            ),
        contentAlignment = Alignment.Center
    ) {
        // Sample only the sibling feed source. The foreground menu/player stays sharp,
        // and the scrim is drawn over the blurred background rather than underneath it.
        Box(
            Modifier.matchParentSize()
                .then(backgroundBlurModifier)
                .background(MediaContrastPalette.Scrim.copy(alpha = 0.6f))
        )
        com.android.purebilibili.core.ui.animation.MaybeDissolvableVideoCard(
            isDissolving = isNotInterestedDissolving,
            onDissolveComplete = onNotInterestedDissolveComplete,
            cardId = "preview-not-interested-${video.bvid}",
            preset = com.android.purebilibili.core.ui.animation.DissolveAnimationPreset.TELEGRAM_FAST,
            collapseAfterDissolve = false,
            publishGlobalDissolveState = false,
            modifier = Modifier.width(AppSpacingTokens.TripleExtraLarge * 6 + AppSpacingTokens.Medium),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Remove padding between items by putting them in one Surface
                    .clip(AppShapes.container(ContainerLevel.Card)) // Clip the whole card
                    .clickable(enabled = false) {}, // Prevent clicks from passing through to background
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                AppSurface(
                    color = AppSurfaceTokens.cardContainer(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                            // 1. Media Area (Cover or Player)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.6f)
                                    .background(MediaContrastPalette.Scrim)
                                    .clickable { // Toggle Play/Pause
                                        haptic(HapticType.MEDIUM)
                                        isPlaying = !isPlaying
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Cover Image (Always visible as background or placeholder)
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(FormatUtils.fixImageUrl(video.pic))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Video Player (Visible when playing)
                                if (isPlaying) {
                                    videoUrl?.let { url ->
                                        DisposableVideoPlayer(
                                            url = url,
                                            onLoadingChanged = { isLoading = it },
                                            onPlaybackError = {
                                                videoUrl = null
                                                isLoading = false
                                                isPlaying = false
                                                android.widget.Toast.makeText(context, "预览播放失败，点击封面可重试", android.widget.Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    }

                                    // Loading Indicator
                                    if (isLoading) {
                                        AdaptiveLoadingIndicator(
                                            color = MediaContrastPalette.Foreground,
                                            size = AppSpacingTokens.DoubleExtraLarge - AppSpacingTokens.Micro,
                                        )
                                    }
                                } else {
                                    // Play Icon Overlay (Hint that it's clickable)
                                    AppIcon(
                                        imageVector = playIcon,
                                        contentDescription = "播放视频预览",
                                        tint = MediaContrastPalette.Foreground.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .size(AppChromeSizeTokens.MinimumTouchTarget)
                                            .background(MediaContrastPalette.Scrim.copy(alpha = 0.3f), androidx.compose.foundation.shape.CircleShape)
                                            .padding(AppSpacingTokens.Medium)
                                    )
                                }
                            }

                            // 2. Title & Info
                            Column(modifier = Modifier.padding(AppSpacingTokens.Large)) {
                                AppText(
                                    text = video.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(AppSpacingTokens.Small))
                                AppText(
                                    text = "${video.owner.name} · ${FormatUtils.formatStat(video.stat.view.toLong())}播放",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AppContentColors.secondaryText()
                                )
                            }

                            MenuDivider()

                            // 3. Action Menu (Integrated)
                            // Play Immediately / Fullscreen
                            PreviewMenuItem(
                                text = if (isPlaying) "全屏播放" else "立即播放",
                                icon = if (isPlaying) Icons.Rounded.Fullscreen else playIcon,
                                onClick = {
                                    haptic(HapticType.MEDIUM)
                                    onPlay() // Go to Full Screen
                                    onDismiss()
                                }
                            )

                            MenuDivider()

                            // Watch Later
                            PreviewMenuItem(
                                text = "稍后再看",
                                icon = watchLaterIcon,
                                onClick = {
                                    haptic(HapticType.MEDIUM)
                                    onWatchLater()
                                    onDismiss()
                                }
                            )

                            if (onSaveCover != null && shouldEnableSaveCoverAction(video.pic)) {
                                MenuDivider()
                                PreviewMenuItem(
                                    text = "保存封面",
                                    icon = photoIcon,
                                    onClick = {
                                        haptic(HapticType.MEDIUM)
                                        onSaveCover()
                                        onDismiss()
                                    }
                                )
                            }

                            MenuDivider()

                            // Share
                            PreviewMenuItem(
                                text = "分享",
                                icon = shareIcon,
                                onClick = {
                                    haptic(HapticType.LIGHT)
                                    onShare() // Use passed callback if needed, but logic is likely external?
                                    // Wait, the implementation passed in HomeScreen handles data.
                                    // But here we are inside VideoPreviewDialog which takes `onShare: () -> Unit`.
                                    // Let's check HomeScreen's implementation of onShare.
                                    onDismiss() // Closing dialog
                                }
                            )

                            if (onBlockCreator != null && shouldShowBlockCreatorAction(video.owner.mid)) {
                                MenuDivider()
                                PreviewMenuItem(
                                    text = "屏蔽 UP 主",
                                    icon = blockCreatorIcon,
                                    isDestructive = true,
                                    onClick = {
                                        haptic(HapticType.HEAVY)
                                        onBlockCreator()
                                        onDismiss()
                                    }
                                )
                            }

                            if (onNotInterested != null) {
                                MenuDivider()
                                PreviewMenuItem(
                                    text = "不感兴趣",
                                    icon = clearIcon,
                                    isDestructive = true,
                                    onClick = {
                                        haptic(HapticType.HEAVY)
                                        onNotInterested()
                                        if (!keepOpenDuringNotInterestedDissolve) onDismiss()
                                    }
                                )
                            }
                    }
                }
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
// Shared media behavior only: MD3, Miuix and glass keep their existing layout/style.
fun DisposableVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
    onLoadingChanged: (Boolean) -> Unit = {},
    onPlaybackError: () -> Unit = {},
    onFirstFrame: () -> Unit = {},
) {
    val context = LocalContext.current
    val visualWorkActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val currentOnLoadingChanged by androidx.compose.runtime.rememberUpdatedState(onLoadingChanged)
    val currentOnPlaybackError by androidx.compose.runtime.rememberUpdatedState(onPlaybackError)
    val currentOnFirstFrame by androidx.compose.runtime.rememberUpdatedState(onFirstFrame)
    val player = remember(context, url) {
        val upstream = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(
            com.android.purebilibili.core.network.NetworkModule.playbackOkHttpClient
        ).setDefaultRequestProperties(
            mapOf("Referer" to "https://www.bilibili.com/", "User-Agent" to "Mozilla/5.0")
        )
        ExoPlayer.Builder(context)
            .setRenderersFactory(
                androidx.media3.exoplayer.DefaultRenderersFactory(context).withAppVideoDecoderPreference(context)
            )
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(upstream))
            .build().withAppVideoDecoderDiagnostics(context).apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
            }
    }

    androidx.compose.runtime.DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                currentOnLoadingChanged(playbackState == Player.STATE_BUFFERING)
            }

            override fun onRenderedFirstFrame() {
                currentOnFirstFrame()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                currentOnLoadingChanged(false)
                currentOnPlaybackError()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    androidx.compose.runtime.LaunchedEffect(player, url) {
        currentOnLoadingChanged(true)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }
    androidx.compose.runtime.LaunchedEffect(player, visualWorkActive) {
        player.playWhenReady = visualWorkActive
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
private fun PreviewMenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AppSpacingTokens.TripleExtraLarge + AppSpacingTokens.Small)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacingTokens.Large + AppSpacingTokens.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AppText(
                text = text,
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                fontWeight = FontWeight.Normal,
                color = if (isDestructive) AppContentColors.error() else AppContentColors.primaryText()
            )
            AppIcon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) AppContentColors.error() else AppContentColors.accent(),
                modifier = Modifier.size(AppSpacingTokens.ExtraLarge - AppSpacingTokens.Micro)
            )
        }
    }
}

@Composable
private fun MenuDivider() {
    AppHorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        thickness = AppSpacingTokens.Micro / 4
    )
}
