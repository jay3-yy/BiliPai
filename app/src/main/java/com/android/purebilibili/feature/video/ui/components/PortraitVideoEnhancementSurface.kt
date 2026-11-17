@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.ui.components

import com.android.purebilibili.core.plugin.BuiltinPluginIds

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.Surface
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.android.purebilibili.R
import com.android.purebilibili.core.plugin.PluginManager
import com.android.purebilibili.core.util.Logger
import com.android.purebilibili.feature.anime4k.*
import com.android.purebilibili.feature.anime4k.gl.Anime4KDisplayScaleMode
import com.android.purebilibili.feature.anime4k.gl.Anime4KGLSurfaceView
import com.android.purebilibili.plugin.sdk.Anime4KConfig
import com.android.purebilibili.plugin.sdk.Anime4KPreset
import com.android.purebilibili.plugin.sdk.VideoEnhancementAlgorithm
import com.android.purebilibili.plugin.sdk.VideoEnhancementPluginApi
import com.android.purebilibili.feature.video.ui.section.VideoOutputRouter
import com.android.purebilibili.feature.video.util.captureSurfaceViewBitmap
import com.android.purebilibili.feature.video.util.captureVideoScreenshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Output state belongs to one media identity and one player, never to a pager position. */
@Stable
internal class PortraitVideoEnhancementSession internal constructor(internal val player: ExoPlayer) {
    var available by mutableStateOf(false)
        internal set
    var enabled by mutableStateOf(false)
        internal set
    var config by mutableStateOf(Anime4KConfig())
        internal set
    var bypassReason by mutableStateOf(Anime4KBypassReason.DISABLED)
        internal set
    internal var requested by mutableStateOf<Boolean?>(null)
    internal var failed by mutableStateOf(false)
    internal var usePipeline by mutableStateOf(false)
    internal var inputSurface by mutableStateOf<Surface?>(null)
    internal var firstFrame by mutableStateOf(false)
    internal var nativeFirstFrame by mutableStateOf(false)
    internal var nativeView by mutableStateOf<PlayerView?>(null)
    internal var glView by mutableStateOf<Anime4KGLSurfaceView?>(null)
    internal var videoSize by mutableStateOf(VideoSize.UNKNOWN)
    internal val router = VideoOutputRouter(player)
    internal var changeEnabled: (Boolean) -> Unit = {}
    internal var plugin: VideoEnhancementPluginApi? = null
    val pipelineActive: Boolean get() = usePipeline && inputSurface != null
    // 封面直接读取当前输出的状态，避免页面重组再次清空已收到的首帧。
    val hasRenderedOutputFrame: Boolean
        get() = if (pipelineActive) firstFrame else nativeFirstFrame

    fun setEnabled(value: Boolean) = changeEnabled(value)
    fun setAlgorithm(value: VideoEnhancementAlgorithm) {
        failed = false
        plugin?.setAlgorithm(value)
    }
    fun setPreset(value: Anime4KPreset) {
        failed = false
        plugin?.setPreset(value)
    }
    fun setFsrSharpness(value: Float) { plugin?.setFsrSharpness(value) }

    /** Read the visible GL output, not the detached PlayerView's stale decoder surface. */
    suspend fun captureScreenshot(width: Int, height: Int): Bitmap? = withContext(Dispatchers.Main.immediate) {
        if (pipelineActive) {
            val view = glView ?: return@withContext null
            if (!firstFrame || !view.holder.surface.isValid) return@withContext null
            captureSurfaceViewBitmap(view, view.width.coerceAtLeast(1), view.height.coerceAtLeast(1))
        } else {
            nativeView?.let { captureVideoScreenshot(it, width, height) }
        }
    }
}

@Composable
internal fun rememberPortraitVideoEnhancementSession(
    player: ExoPlayer,
    mediaIdentity: Any,
    requiresHdrSurfaceOutput: Boolean,
    isInPipMode: Boolean = false,
    isAudioOnly: Boolean = false,
): PortraitVideoEnhancementSession {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateAsState()
    val started = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val scope = rememberCoroutineScope()
    val session = remember(player, mediaIdentity) { PortraitVideoEnhancementSession(player) }
    val plugins by PluginManager.pluginsFlow.collectAsStateWithLifecycle()
    val info = plugins.firstOrNull { it.plugin.id == BuiltinPluginIds.VIDEO_ENHANCEMENT }
    val plugin = PluginManager.getPlugin(VideoEnhancementPluginApi::class)
    val config = if (plugin != null) plugin.configState.collectAsStateWithLifecycle().value else Anime4KConfig()
    val glesAvailable = remember(context) { isAnime4KGles3Available(context) }
    var inputFormat by remember(player) { mutableStateOf(player.videoFormat) }
    DisposableEffect(session) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                if (!session.pipelineActive && session.nativeView?.player === player) {
                    session.nativeFirstFrame = true
                }
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) { session.videoSize = videoSize }
            override fun onEvents(player: Player, events: Player.Events) {
                inputFormat = session.player.videoFormat
            }
        }
        session.videoSize = player.videoSize
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            session.usePipeline = false
            session.router.release()
            session.nativeView?.takeIf { it.player === player }?.player = null
            session.inputSurface = null
            session.glView = null
        }
    }
    val requested = session.requested ?: resolveInitialVideoEnhancementEnabled(info?.enabled == true, config)
    val enabled = info?.enabled == true && requested
    val decision = resolveAnime4KOutputDecision(
        pluginEnabled = enabled,
        glAvailable = glesAvailable && !session.failed,
        colorTransfer = if (requiresHdrSurfaceOutput) C.COLOR_TRANSFER_ST2084 else inputFormat?.colorInfo?.colorTransfer ?: 0,
        sampleMimeType = inputFormat?.sampleMimeType,
        isInPipMode = isInPipMode,
        isAudioOnly = isAudioOnly,
        hostLifecycleStarted = started,
    )
    val available = plugin != null && glesAvailable && !session.failed &&
        !requiresHdrSurfaceOutput && !isAnime4kHdrOrDolbyVision(
            inputFormat?.colorInfo?.colorTransfer ?: 0, inputFormat?.sampleMimeType,
        ) && !isInPipMode && !isAudioOnly
    SideEffect {
        session.config = config
        session.plugin = plugin
        session.enabled = enabled
        session.available = available
        session.bypassReason = decision.bypassReason
        session.usePipeline = decision.shouldUsePipeline
        session.changeEnabled = { value ->
            session.requested = value
            session.failed = false
            scope.launch {
                if (value && info?.enabled != true) PluginManager.setEnabled(BuiltinPluginIds.VIDEO_ENHANCEMENT, true)
                plugin?.rememberCurrentVideoEnabled(value)
            }
        }
    }
    LaunchedEffect(session, session.nativeView, session.inputSurface, session.usePipeline, started) {
        val view = session.nativeView
        if (session.pipelineActive) session.nativeFirstFrame = false
        if (!session.pipelineActive && view != null && view.player !== player) {
            session.nativeFirstFrame = false
            view.player = player
        }
        session.router.update(view, session.inputSurface, true, session.usePipeline)
    }
    LaunchedEffect(session, session.glView, session.usePipeline, started) {
        session.glView?.let { if (session.usePipeline && started) it.onResume() else it.onPause() }
    }
    com.android.purebilibili.core.ui.performance.VideoSurfaceFrameRateEffect(
        player = player,
        surfaceView = session.glView,
        active = session.pipelineActive && started,
    )
    LaunchedEffect(session, session.pipelineActive) {
        if (!session.pipelineActive) return@LaunchedEffect
        var startedAt: Long? = null
        while (isActive && !session.firstFrame) {
            delay(120L)
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                startedAt = null
                continue
            }
            val now = android.os.SystemClock.elapsedRealtime()
            val beginning = startedAt ?: now.also { startedAt = it }
            if (shouldFallbackAnime4KBeforeFirstFrame(true, session.inputSurface != null,
                    session.firstFrame, player.playWhenReady, player.mediaItemCount, now - beginning)) {
                session.failed = true
                break
            }
        }
    }
    return session
}

/** Only the video output branch; viewport, gestures, danmaku and paging stay with the host. */
@Composable
internal fun PortraitVideoEnhancementSurface(
    session: PortraitVideoEnhancementSession,
    preferTexture: Boolean,
    resizeMode: Int,
    keepScreenOn: Boolean,
    flippedHorizontal: Boolean,
    flippedVertical: Boolean,
    onPlayerViewChanged: (PlayerView?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestViewChanged by rememberUpdatedState(onPlayerViewChanged)
    val scaleMode = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> Anime4KDisplayScaleMode.STRETCH
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Anime4KDisplayScaleMode.CROP
        else -> Anime4KDisplayScaleMode.FIT
    }
    Box(modifier) {
        key(session, preferTexture) {
            AndroidView(
                factory = { ctx ->
                    val view = if (preferTexture) LayoutInflater.from(ctx)
                        .inflate(R.layout.view_player_texture, null, false) as PlayerView else PlayerView(ctx)
                    view.apply {
                        session.nativeFirstFrame = false
                        session.nativeView = this
                        latestViewChanged(this)
                        player = if (!session.pipelineActive) session.player else null
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    }
                },
                update = { view ->
                    session.nativeView = view
                    latestViewChanged(view)
                    if (!session.pipelineActive && view.player !== session.player) {
                        session.nativeFirstFrame = false
                        view.player = session.player
                    }
                    view.keepScreenOn = keepScreenOn
                    // 原生布局完成及视频格式变化都需要恢复视口；不能只依赖组合时的旧 View 尺寸。
                    // 玻璃、MD3 与 Miuix 共用出画修复，不改变各自的容器比例。
                    schedulePlayerViewViewportRefresh(
                        playerView = view,
                        resizeMode = resizeMode,
                        expectedWidth = view.width,
                        expectedHeight = view.height,
                        videoWidth = session.videoSize.width,
                        videoHeight = session.videoSize.height,
                    )
                    view.visibility = if (session.pipelineActive && session.firstFrame) View.INVISIBLE else View.VISIBLE
                },
                onRelease = { view ->
                    cancelPlayerViewViewportRefresh(view)
                    if (session.nativeView === view) {
                        session.nativeFirstFrame = false
                        session.nativeView = null
                        latestViewChanged(null)
                    }
                    view.player = null
                },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = if (flippedHorizontal) -1f else 1f
                    scaleY = if (flippedVertical) -1f else 1f
                },
            )
        }
        if (session.usePipeline) key(session) {
            AndroidView(
                factory = { ctx ->
                    Anime4KGLSurfaceView(ctx, initialConfig = session.config).apply {
                        session.firstFrame = false
                        session.glView = this
                        onInputSurfaceChanged = { surface ->
                            if (session.glView === this) {
                                session.inputSurface = surface
                                if (surface == null) session.firstFrame = false
                            }
                        }
                        this.onFirstFrameRendered = {
                            if (session.glView === this) {
                                session.firstFrame = true
                            }
                        }
                        onPipelineError = { error ->
                            if (session.glView === this) {
                                Logger.e("PortraitEnhancement", "GL output failed; restoring native output", error)
                                session.failed = true
                            }
                        }
                    }
                },
                update = { view ->
                    view.updateConfig(session.config)
                    view.updateInputSize(session.videoSize.width, session.videoSize.height)
                    view.updateFlip(flippedHorizontal, flippedVertical)
                    view.updateDisplayScaleMode(scaleMode)
                },
                onRelease = { view ->
                    view.onInputSurfaceChanged = {}
                    view.onFirstFrameRendered = {}
                    view.onPipelineError = {}
                    if (session.glView === view) {
                        session.router.update(session.nativeView, null, true, false)
                        session.glView = null
                        session.inputSurface = null
                        session.firstFrame = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
