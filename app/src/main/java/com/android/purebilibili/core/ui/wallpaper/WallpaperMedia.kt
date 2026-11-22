package com.android.purebilibili.core.ui.wallpaper

import android.graphics.drawable.Animatable
import android.net.Uri
import android.view.LayoutInflater
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.asDrawable
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.android.purebilibili.R
import kotlin.math.min
import kotlin.math.roundToInt

internal fun isVideoWallpaper(uri: String): Boolean =
    uri.substringBefore('?').substringBefore('#').substringAfterLast('.').lowercase() in
        setOf("mp4", "m4v", "webm", "mkv", "mov", "3gp", "video")

internal enum class WallpaperMediaLoadState { LOADING, READY, ERROR }

/** Images and TextureView videos use the same crop geometry when a transform is supplied. */
@Composable
internal fun WallpaperMedia(
    uri: String,
    modifier: Modifier = Modifier,
    imageModel: Any = uri,
    alignment: Alignment = Alignment.Center,
    playbackEnabled: Boolean = true,
    video: Boolean = isVideoWallpaper(uri),
    transformProvider: (() -> WallpaperTransform)? = null,
    playbackPriority: Int = 0,
    onLoadState: (WallpaperMediaLoadState) -> Unit = {},
    onSourceSize: (IntSize) -> Unit = {},
) {
    val context = LocalContext.current
    val coordinator = LocalWallpaperPlaybackCoordinator.current
    val lease = remember(uri) { Any() }
    val playbackGate = com.android.purebilibili.core.ui.rememberDecorativeAnimationGate(
        respectComponentMotion = false,
    )
    val eligible = playbackEnabled && playbackGate.active
    val sourceSize = remember(uri) { mutableStateOf(IntSize.Zero) }
    var loadState by remember(uri) { mutableStateOf(WallpaperMediaLoadState.LOADING) }
    val latestLoadCallback by rememberUpdatedState(onLoadState)
    val latestSizeCallback by rememberUpdatedState(onSourceSize)
    LaunchedEffect(uri, loadState) { latestLoadCallback(loadState) }
    LaunchedEffect(uri, sourceSize.value) { latestSizeCallback(sourceSize.value) }
    val resolvedVideo by key(uri, video) {
        produceState<Boolean?>(initialValue = if (video) true else if (uri.startsWith("content:")) null else false) {
            value = video || withContext(Dispatchers.IO) {
                if (uri.startsWith("content:")) {
                    runCatching { context.contentResolver.getType(Uri.parse(uri))?.startsWith("video/") == true }
                        .getOrDefault(false)
                } else false
            }
        }
    }
    val media: @Composable (Modifier) -> Unit = { mediaModifier ->
        if (resolvedVideo == null) {
            Box(mediaModifier)
        } else if (resolvedVideo == true) {
            val player = remember(context, uri) {
                ExoPlayer.Builder(context).build().apply {
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri(Uri.parse(uri)))
                }
            }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        sourceSize.value = IntSize(
                            (videoSize.width * videoSize.pixelWidthHeightRatio).roundToInt(),
                            videoSize.height,
                        )
                    }
                    override fun onRenderedFirstFrame() { loadState = WallpaperMediaLoadState.READY }
                    override fun onPlayerError(error: PlaybackException) { loadState = WallpaperMediaLoadState.ERROR }
                }
                player.addListener(listener)
                player.prepare()
                onDispose {
                    coordinator?.release(lease)
                    player.removeListener(listener)
                    player.release()
                }
            }
            DisposableEffect(coordinator, player, lease, eligible, playbackPriority) {
                if (eligible && coordinator != null) {
                    coordinator.acquire(lease, playbackPriority) { player.playWhenReady = it }
                } else {
                    player.playWhenReady = eligible
                }
                onDispose {
                    if (coordinator != null) coordinator.release(lease)
                    else player.playWhenReady = false
                }
            }
            AndroidView(
                modifier = mediaModifier,
                factory = {
                    (LayoutInflater.from(it).inflate(R.layout.wallpaper_video, null) as PlayerView).apply {
                        useController = false
                        resizeMode = if (transformProvider == null) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else AspectRatioFrameLayout.RESIZE_MODE_FIT
                        this.player = player
                        isFocusable = false
                        isClickable = false
                        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }
                },
                update = { it.player = player },
                onRelease = { it.player = null },
            )
        } else {
            var animation by remember(uri) { mutableStateOf<Animatable?>(null) }
            DisposableEffect(coordinator, animation, lease, eligible, playbackPriority) {
                val current = animation
                if (current != null && eligible && coordinator != null) {
                    coordinator.acquire(lease, playbackPriority) { if (it) current.start() else current.stop() }
                } else if (eligible) current?.start() else current?.stop()
                onDispose {
                    coordinator?.release(lease)
                    current?.stop()
                }
            }
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = if (transformProvider == null) ContentScale.Crop else ContentScale.Fit,
                alignment = if (transformProvider == null) alignment else Alignment.Center,
                modifier = mediaModifier,
                onSuccess = {
                    sourceSize.value = IntSize(it.result.image.width, it.result.image.height)
                    val drawable = it.result.image.asDrawable(context.resources) as? Animatable
                    drawable?.stop()
                    animation = drawable
                    loadState = WallpaperMediaLoadState.READY
                },
                onError = { loadState = WallpaperMediaLoadState.ERROR },
            )
        }
    }
    val gatedModifier = modifier.then(playbackGate.modifier)
    if (transformProvider == null) {
        media(gatedModifier)
    } else {
        // Fit the full source inside a viewport-sized child, then transform its layer to
        // the shared crop rect. Never resize a TextureView to a giant zoomed bitmap size.
        // All gesture/state reads stay in the layer phase, outside composition and layout.
        Box(modifier = gatedModifier.clipToBounds()) {
            media(Modifier.fillMaxSize().graphicsLayer {
                val source = sourceSize.value
                val width = size.width.coerceAtLeast(1f)
                val height = size.height.coerceAtLeast(1f)
                val sw = source.width.takeIf { it > 0 }?.toFloat() ?: width
                val sh = source.height.takeIf { it > 0 }?.toFloat() ?: height
                val fitScale = min(width / sw, height / sh)
                val crop = resolveWallpaperCropGeometry(width, height, sw, sh, transformProvider())
                val layerScale = crop.width / (sw * fitScale)
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = layerScale
                scaleY = layerScale
                translationX = crop.left - (width - sw * fitScale) * layerScale / 2f
                translationY = crop.top - (height - sh * fitScale) * layerScale / 2f
            })
        }
    }
}
