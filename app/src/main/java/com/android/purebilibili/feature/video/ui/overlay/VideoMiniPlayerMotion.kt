package com.android.purebilibili.feature.video.ui.overlay

import android.content.res.Configuration
import android.os.Build
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.android.purebilibili.core.ui.motion.iosMorphTween
import com.android.purebilibili.feature.video.util.captureVideoAmbientFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

internal val LocalVideoMiniPlayerMotion = staticCompositionLocalOf<VideoMiniPlayerMotion?> { null }

private const val FRAME_CAPTURE_TIMEOUT_MS = 84L
private const val MINI_LAYOUT_TIMEOUT_MS = 200L
private const val MINI_MORPH_DURATION_MS = 300
private const val FULLSCREEN_LAYOUT_TIMEOUT_MS = 500L
private const val MAX_MORPH_FRAME_EDGE = 960
private const val MIN_MINI_MORPH_DURATION_MS = 100
private const val MAX_GESTURE_FRACTION = 0.82f
private const val RESTORED_GESTURE_THRESHOLD = 0.002f
private const val PREVIEW_IMMEDIATE_START_THRESHOLD = 0.04f
private const val PREVIEW_CATCH_UP_DURATION_MS = 72

internal data class MiniPlayerMotionTarget(
    val bounds: Rect,
    val cornerRadiusPx: Float,
    val rotationDegrees: Float = 0f,
)

internal fun MiniPlayerMotionTarget.interpolateTo(
    other: MiniPlayerMotionTarget,
    fraction: Float,
): MiniPlayerMotionTarget {
    if (fraction == 0f) return this
    if (fraction == 1f) return other
    fun interpolate(start: Float, end: Float): Float = start + (end - start) * fraction
    return MiniPlayerMotionTarget(
        bounds = Rect(
            interpolate(bounds.left, other.bounds.left),
            interpolate(bounds.top, other.bounds.top),
            interpolate(bounds.right, other.bounds.right),
            interpolate(bounds.bottom, other.bounds.bottom),
        ),
        cornerRadiusPx = interpolate(cornerRadiusPx, other.cornerRadiusPx),
        rotationDegrees = interpolate(rotationDegrees, other.rotationDegrees),
    )
}

internal class MiniPlayerFullscreenExpansion(
    val frame: VideoMiniPlayerHandoff,
    private val source: MiniPlayerMotionTarget,
    private val sourceWindowSize: IntSize,
    private val sourceRotation: Int,
) {
    var viewportBounds by mutableStateOf<Rect?>(null)

    fun startPose(rotation: Int): MiniPlayerMotionTarget {
        val center = source.bounds.center
        val (rotatedCenter, angle) = when ((rotation - sourceRotation + 4) % 4) {
            Surface.ROTATION_90 -> Offset(center.y, sourceWindowSize.width - center.x) to -90f
            Surface.ROTATION_270 -> Offset(sourceWindowSize.height - center.y, center.x) to 90f
            else -> return source
        }
        return source.copy(
            bounds = source.bounds.translate(rotatedCenter - center),
            rotationDegrees = angle,
        )
    }
}

internal class MiniPlayerBackPreview(
    val target: MiniPlayerMotionTarget,
    private val readProgress: () -> Float,
) {
    var releaseFraction = 0f
        private set

    fun fraction(): Float = readProgress().coerceIn(0f, 1f) * MAX_GESTURE_FRACTION

    fun poseFrom(source: MiniPlayerMotionTarget, catchUp: Float): MiniPlayerMotionTarget =
        source.interpolateTo(target, fraction() * catchUp)

    fun freezeForCommit(source: MiniPlayerMotionTarget, catchUp: Float): MiniPlayerMotionTarget {
        releaseFraction = fraction() * catchUp
        return source.interpolateTo(target, releaseFraction)
    }
}

internal class VideoMiniPlayerHandoff(
    val player: Player,
    sourceView: PlayerView,
    source: Rect,
    val image: ImageBitmap,
    val preview: MiniPlayerBackPreview? = null,
) {
    private val sourcePose = MiniPlayerMotionTarget(source, cornerRadiusPx = 0f)
    private val gestureCatchUp = preview?.let {
        Animatable(if (it.fraction() <= PREVIEW_IMMEDIATE_START_THRESHOLD) 1f else 0f)
    }

    var commitPose by mutableStateOf(sourcePose.takeIf { preview == null })
        private set

    fun previewPose(): MiniPlayerMotionTarget =
        preview?.poseFrom(sourcePose, gestureCatchUp?.value ?: 1f) ?: sourcePose

    suspend fun connectPreview() {
        val catchUp = gestureCatchUp ?: return
        if (catchUp.value < 1f) catchUp.animateTo(1f, iosMorphTween(PREVIEW_CATCH_UP_DURATION_MS))
    }

    fun commit(fromVisiblePreview: Boolean) {
        val catchUp = if (fromVisiblePreview) gestureCatchUp?.value ?: 1f else 0f
        commitPose = preview?.freezeForCommit(sourcePose, catchUp) ?: sourcePose
    }

    var target by mutableStateOf<MiniPlayerMotionTarget?>(null)
    var landed by mutableStateOf(false)
    var destinationCoverLaidOut by mutableStateOf(false)
    var firstFrameRendered by mutableStateOf(false)
        private set

    private val sourceView = WeakReference(sourceView)
    private var firstFrameListener: Player.Listener? = null

    fun ownsSource(view: PlayerView?): Boolean = view != null && sourceView.get() === view

    // Register before assigning PlayerView.player so even paused playback can report its new surface.
    fun observeFirstFrame() {
        if (firstFrameListener != null || firstFrameRendered) return
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameRendered = true
                stopObservingFirstFrame()
            }
        }
        firstFrameListener = listener
        player.addListener(listener)
    }

    fun stopObservingFirstFrame() {
        firstFrameListener?.let(player::removeListener)
        firstFrameListener = null
    }
}

private class MiniPlayerBackGesture(
    val player: Player,
    val preview: MiniPlayerBackPreview,
) {
    var captureFinished = false
    var queuedCommit: (() -> Unit)? = null
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class VideoMiniPlayerMotion(
    private val scope: CoroutineScope,
    private val hostView: View,
) {
    private data class Source(val view: PlayerView, val output: View, val bounds: Rect)

    private val sources = LinkedHashMap<PlayerView, () -> LayoutCoordinates?>()
    private var overlayHost: LayoutCoordinates? = null
    private var capturingPlayer: Player? = null
    private var capturingFullscreen = false
    private var backGesture by mutableStateOf<MiniPlayerBackGesture?>(null)
    private var restoreJob: Job? = null

    var handoff by mutableStateOf<VideoMiniPlayerHandoff?>(null)
        private set

    var fullscreenExpansion by mutableStateOf<MiniPlayerFullscreenExpansion?>(null)
        private set

    fun expandFullscreen(
        view: PlayerView,
        card: MiniPlayerMotionTarget,
        isCurrent: () -> Boolean,
        onExpand: () -> Unit,
    ): Boolean {
        if (capturingFullscreen) return true
        val player = view.player ?: return false
        val output = view.videoSurfaceView ?: return false
        val host = overlayHost?.takeIf { it.isAttached } ?: return false
        val rotation = view.display?.rotation ?: return false
        val source = card.copy(bounds = card.bounds.translate(-host.positionInWindow()))
        val windowSize = host.size
        capturingFullscreen = true
        scope.launch {
            try {
                val frame = capture(player, Source(view, output, card.bounds))
                if (!isCurrent()) {
                    frame?.image?.asAndroidBitmap()?.recycle()
                    return@launch
                }
                if (frame != null) {
                    fullscreenExpansion = MiniPlayerFullscreenExpansion(frame, source, windowSize, rotation)
                }
                onExpand()
            } finally {
                capturingFullscreen = false
            }
        }
        return true
    }

    fun finishFullscreenExpansion(expansion: MiniPlayerFullscreenExpansion) {
        if (fullscreenExpansion === expansion) {
            expansion.frame.stopObservingFirstFrame()
            fullscreenExpansion = null
        }
    }

    val ownsNavigationMotion: Boolean
        get() = backGesture != null || handoff != null

    fun recordOverlayHost(coordinates: LayoutCoordinates) {
        overlayHost = coordinates
    }

    fun initialTarget(
        windowWidthDp: Int,
        windowHeightDp: Int,
        density: Density,
        layoutDirection: LayoutDirection,
        videoAspectRatio: Float,
        cardLeftPx: Float?,
        entryFromLeft: Boolean,
        cornerRadiusDp: (MiniPlayerOverlayLayoutPolicy) -> Int,
    ): MiniPlayerMotionTarget? {
        val host = overlayHost?.takeIf { it.isAttached } ?: return null
        val layout = resolveMiniPlayerOverlayLayoutPolicy(windowWidthDp)
        val resizeBounds = resolveMiniPlayerResizeBounds(
            defaultWidthDp = layout.miniPlayerWidthDp,
            defaultHeightDp = layout.miniPlayerHeightDp,
            screenWidthDp = windowWidthDp,
            screenHeightDp = windowHeightDp,
            outerPaddingDp = layout.outerPaddingDp,
            topInsetDp = layout.dragTopInsetDp,
            bottomInsetDp = layout.dragBottomInsetDp,
            videoAspectRatio = videoAspectRatio,
        )
        val dimensions = layout.dimensionsForWindow(
            videoAspectRatio = videoAspectRatio,
            widthDp = layout.miniPlayerWidthDp.toFloat().coerceIn(resizeBounds.minWidthDp, resizeBounds.maxWidthDp),
            screenWidthDp = windowWidthDp,
            screenHeightDp = windowHeightDp,
        )
        return with(density) {
            val width = dimensions.widthDp.dp.roundToPx().coerceAtMost(host.size.width)
            val height = dimensions.heightDp.dp.roundToPx().coerceAtMost(host.size.height)
            val offset = resolveMiniPlayerInitialOverlayOffset(
                cardLeftPx = cardLeftPx,
                entryFromLeft = entryFromLeft,
                screenWidthPx = windowWidthDp.dp.toPx(),
                screenHeightPx = windowHeightDp.dp.toPx(),
                miniPlayerWidthPx = dimensions.widthDp.dp.toPx(),
                miniPlayerHeightPx = dimensions.heightDp.dp.toPx(),
                outerPaddingPx = layout.outerPaddingDp.dp.toPx(),
                topInsetPx = layout.dragTopInsetDp.dp.toPx(),
                bottomInsetPx = layout.dragBottomInsetDp.dp.toPx(),
            )
            val x = if (layoutDirection == LayoutDirection.Ltr) {
                offset.x.roundToInt()
            } else {
                host.size.width - offset.x.roundToInt() - width
            }
            val origin = host.positionInWindow() + Offset(x.toFloat(), offset.y.roundToInt().toFloat())
            MiniPlayerMotionTarget(
                bounds = Rect(origin.x, origin.y, origin.x + width, origin.y + height),
                cornerRadiusPx = cornerRadiusDp(layout).dp.toPx(),
            )
        }
    }

    fun addSource(view: PlayerView, coordinates: () -> LayoutCoordinates?) {
        sources[view] = coordinates
    }

    fun removeSource(view: PlayerView) {
        sources.remove(view)
    }

    private fun sourceFor(player: Player): Source? {
        val (view, sourceCoordinates) = sources.entries.lastOrNull { (view, _) ->
            view.player === player && view.isAttachedToWindow && view.isShown &&
                view.windowToken == hostView.windowToken
        } ?: return null
        val coordinates = sourceCoordinates()?.takeIf { it.isAttached } ?: return null
        val video = view.videoSurfaceView ?: return null
        if (video.width <= 0 || video.height <= 0 || !video.isShown) return null
        val localVideo = android.graphics.Rect(0, 0, video.width, video.height)
        view.offsetDescendantRectToMyCoords(video, localVideo)
        val topLeft = coordinates.localToWindow(Offset(localVideo.left.toFloat(), localVideo.top.toFloat()))
        val bottomRight = coordinates.localToWindow(Offset(localVideo.right.toFloat(), localVideo.bottom.toFloat()))
        val bounds = Rect(topLeft, bottomRight)
        return if (bounds.isEmpty) null else Source(view, video, bounds)
    }

    private suspend fun capture(
        player: Player,
        source: Source,
        preview: MiniPlayerBackPreview? = null,
    ): VideoMiniPlayerHandoff? {
        val width = source.output.width
        val height = source.output.height
        if (width <= 0 || height <= 0) return null
        val scale = minOf(1f, MAX_MORPH_FRAME_EDGE.toFloat() / maxOf(width, height))
        val frame = withTimeoutOrNull(FRAME_CAPTURE_TIMEOUT_MS) {
            captureVideoAmbientFrame(
                playerView = source.view,
                targetWidth = (width * scale).roundToInt().coerceAtLeast(1),
                targetHeight = (height * scale).roundToInt().coerceAtLeast(1),
            )
        } ?: return null
        if (source.view.player !== player || !source.view.isAttachedToWindow || !source.view.isShown) {
            frame.recycle()
            return null
        }
        return VideoMiniPlayerHandoff(player, source.view, source.bounds, frame.asImageBitmap(), preview)
    }

    fun startBackGesture(
        player: Player,
        target: MiniPlayerMotionTarget,
        progress: () -> Float,
        isCurrentSource: () -> Boolean,
    ): Boolean {
        // A second swipe during cancel reuses the original frame and the same navigation driver.
        val restoringGesture = backGesture
        if (restoreJob?.isActive == true && restoringGesture != null && restoringGesture.player === player) {
            if (!restoringGesture.captureFinished || handoff != null) {
                restoreJob?.cancel()
                restoreJob = null
                return true
            }
            discardBackGesture()
        }
        if (ownsNavigationMotion || capturingPlayer != null) return false
        val source = sourceFor(player) ?: return false
        val canKeepSourceSurface = source.output is TextureView ||
            (source.output is SurfaceView && Build.VERSION.SDK_INT >= 34)
        if (!canKeepSourceSurface) return false

        val gesture = MiniPlayerBackGesture(player, MiniPlayerBackPreview(target, progress))
        backGesture = gesture
        scope.launch {
            val frame = capture(player, source, gesture.preview)
            if (backGesture !== gesture) {
                frame?.image?.asAndroidBitmap()?.recycle()
                return@launch
            }
            if (!isCurrentSource()) {
                frame?.image?.asAndroidBitmap()?.recycle()
                discardBackGesture()
                return@launch
            }
            gesture.captureFinished = true
            if (restoreJob?.isActive == true) {
                frame?.image?.asAndroidBitmap()?.recycle()
            } else {
                handoff = frame
            }
            gesture.queuedCommit?.invoke()
        }
        return true
    }

    fun cancelBackGesture(): Boolean {
        val gesture = backGesture ?: return false
        if (restoreJob?.isActive == true) return true
        restoreJob = scope.launch {
            snapshotFlow { gesture.preview.fraction() }.first { it <= RESTORED_GESTURE_THRESHOLD }
            if (backGesture === gesture) discardBackGesture()
        }
        return true
    }

    fun discardBackGesture() {
        val gesture = backGesture ?: return
        backGesture = null
        restoreJob?.cancel()
        restoreJob = null
        handoff?.takeIf { it.preview === gesture.preview }?.let { finish(it) }
    }

    fun tryStart(
        player: Player,
        canCommit: () -> Boolean,
        onCommit: (hasMotion: Boolean) -> Unit,
    ): Boolean {
        val gesture = backGesture?.takeIf { it.player === player }
        if (gesture != null) {
            restoreJob?.cancel()
            restoreJob = null
            val alreadyVisible = handoff?.preview === gesture.preview
            val complete: () -> Unit = {
                val frame = handoff?.takeIf { it.preview === gesture.preview }
                backGesture = null
                if (!canCommit()) {
                    frame?.let { finish(it) }
                } else {
                    frame?.commit(fromVisiblePreview = alreadyVisible)
                    onCommit(frame != null)
                }
            }
            if (gesture.captureFinished) complete() else gesture.queuedCommit = complete
            return true
        }
        val currentMotionPlayer = capturingPlayer ?: handoff?.player
        if (currentMotionPlayer != null) return currentMotionPlayer === player
        val source = sourceFor(player) ?: return false
        capturingPlayer = player
        scope.launch {
            try {
                val frame = capture(player, source)
                if (!canCommit()) {
                    frame?.image?.asAndroidBitmap()?.recycle()
                    return@launch
                }
                if (frame == null) {
                    onCommit(false)
                    return@launch
                }
                handoff = frame
                withFrameNanos { }
                if (canCommit() && handoff === frame) {
                    onCommit(true)
                } else {
                    finish(frame)
                    if (canCommit()) onCommit(false)
                }
            } finally {
                capturingPlayer = null
            }
        }
        return true
    }

    fun finish(motion: VideoMiniPlayerHandoff, coverTransferred: Boolean = false) {
        if (handoff !== motion) return
        if (!coverTransferred) motion.stopObservingFirstFrame()
        handoff = null
    }
}

@Composable
internal fun VideoMiniPlayerMotionLayer(
    motion: VideoMiniPlayerMotion,
    enabled: Boolean,
    miniPlayerVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(motion, enabled) {
        if (!enabled) motion.discardBackGesture()
    }
    val handoff = motion.handoff ?: return
    val commitPose = handoff.commitPose
    val progress = remember(handoff) { Animatable(0f) }
    val path = remember(handoff) { Path() }
    var origin by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(motion, handoff) {
        onDispose { motion.finish(handoff) }
    }
    LaunchedEffect(handoff, commitPose, enabled) {
        if (!enabled) {
            motion.finish(handoff)
            return@LaunchedEffect
        }
        if (commitPose == null) {
            handoff.connectPreview()
            return@LaunchedEffect
        }
        var coverTransferred = false
        try {
            withTimeoutOrNull(MINI_LAYOUT_TIMEOUT_MS) {
                snapshotFlow { handoff.target }.filterNotNull().first()
            } ?: return@LaunchedEffect
            val releasedAt = handoff.preview?.releaseFraction ?: 0f
            val duration = (MINI_MORPH_DURATION_MS * (1f - releasedAt)).roundToInt()
                .coerceAtLeast(MIN_MINI_MORPH_DURATION_MS)
            progress.animateTo(1f, iosMorphTween(duration))
            handoff.landed = true
            // Transfer the cover to the mini-player so its controls stay accessible even
            // if the decoder needs longer to draw to its new surface.
            coverTransferred = withTimeoutOrNull(MINI_LAYOUT_TIMEOUT_MS) {
                snapshotFlow { handoff.destinationCoverLaidOut }.first { it }
                withFrameNanos { }
                true
            } == true
        } finally {
            motion.finish(handoff, coverTransferred)
        }
    }
    if (!enabled) return
    // On navigation or PiP changes, an already-started handoff cannot cover a different playback UI.
    LaunchedEffect(handoff, miniPlayerVisible) {
        if (handoff.target != null && !miniPlayerVisible) motion.finish(handoff)
    }
    Canvas(
        modifier = modifier.fillMaxSize()
            .zIndex(110f)
            .onGloballyPositioned { origin = it.positionInWindow() },
    ) {
        val pose = if (commitPose == null) {
            handoff.previewPose()
        } else {
            commitPose.interpolateTo(handoff.target ?: commitPose, progress.value)
        }
        drawMiniPlayerFrame(handoff.image, pose.copy(bounds = pose.bounds.translate(-origin)), path)
    }
}

@Composable
internal fun MiniPlayerFullscreenExpansionLayer(
    motion: VideoMiniPlayerMotion,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val expansion = motion.fullscreenExpansion ?: return
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current
    val rotation = view.display?.rotation ?: Surface.ROTATION_0
    val progress = remember(expansion) { Animatable(0f) }
    val opacity = remember(expansion) { Animatable(1f) }
    val path = remember(expansion) { Path() }
    var origin by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(motion, expansion) {
        onDispose { motion.finishFullscreenExpansion(expansion) }
    }
    LaunchedEffect(expansion, enabled) {
        try {
            if (!enabled) return@LaunchedEffect
            withTimeoutOrNull(FULLSCREEN_LAYOUT_TIMEOUT_MS) {
                snapshotFlow { expansion.viewportBounds }.filterNotNull().first()
            } ?: return@LaunchedEffect
            progress.animateTo(1f, iosMorphTween(MINI_MORPH_DURATION_MS))
            withTimeoutOrNull(MINI_LAYOUT_TIMEOUT_MS) {
                snapshotFlow { expansion.frame.firstFrameRendered }.first { it }
            }
            opacity.animateTo(0f, iosMorphTween(80))
        } finally {
            motion.finishFullscreenExpansion(expansion)
        }
    }
    if (!enabled) return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .zIndex(110f)
            .onGloballyPositioned { origin = it.positionInWindow() }
            .graphicsLayer { alpha = opacity.value },
    ) {
        val start = expansion.startPose(rotation)
        val viewport = expansion.viewportBounds
        val pose = if (viewport == null) start else start.interpolateTo(
            MiniPlayerMotionTarget(viewport.translate(-origin), cornerRadiusPx = 0f),
            progress.value,
        )
        if (landscape) drawRect(Color.Black)
        drawMiniPlayerFrame(expansion.frame.image, pose, path)
    }
}

private fun DrawScope.drawMiniPlayerFrame(
    image: ImageBitmap,
    pose: MiniPlayerMotionTarget,
    path: Path,
) {
    val bounds = pose.bounds
    val scale = minOf(bounds.width / image.width, bounds.height / image.height)
    val width = (image.width * scale).roundToInt().coerceAtLeast(1)
    val height = (image.height * scale).roundToInt().coerceAtLeast(1)
    path.reset()
    path.addRoundRect(RoundRect(bounds, CornerRadius(pose.cornerRadiusPx)))
    withTransform({
        if (pose.rotationDegrees != 0f) rotate(pose.rotationDegrees, pivot = bounds.center)
        clipPath(path)
    }) {
        drawRect(Color.Black, topLeft = bounds.topLeft, size = bounds.size)
        drawImage(
            image = image,
            dstOffset = IntOffset(
                (bounds.center.x - width / 2f).roundToInt(),
                (bounds.center.y - height / 2f).roundToInt(),
            ),
            dstSize = IntSize(width, height),
        )
    }
}
