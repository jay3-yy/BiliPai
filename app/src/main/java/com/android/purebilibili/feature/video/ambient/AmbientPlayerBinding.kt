package com.android.purebilibili.feature.video.ambient

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.runtime.*
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import com.android.purebilibili.core.store.SettingsManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.android.purebilibili.core.ui.LocalVideoDetailNavigationTransitionScope

internal val LocalAmbientPresentation = staticCompositionLocalOf<AmbientPresentation?> { null }
internal val LocalAmbientController = staticCompositionLocalOf<AmbientFrameController?> { null }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun BindPlayerAmbient(
    player: Player,
    playerView: PlayerView?,
    identity: Any,
    quality: Int,
    width: Int,
    height: Int,
    foreground: Boolean,
    excluded: Boolean,
    hdr: Boolean,
    anime4k: Boolean,
    transitioning: Boolean,
    firstFrameReady: Boolean,
    statusBar: Boolean,
    diagnosticLogging: Boolean,
) {
    val context = LocalContext.current
    val presentation = LocalAmbientPresentation.current ?: return
    val controller = LocalAmbientController.current ?: return
    val settings by SettingsManager.getVideoAmbientSettings(context).collectAsStateWithLifecycle(AmbientSettings())
    val environment = rememberAmbientEnvironment(settings.enabled || statusBar)
    // 分栏和独立播放器不属于 NavDisplay entry；此时由页面可见性控制采集。
    val navTransition = LocalVideoDetailNavigationTransitionScope.current
    val navigationSettled = {
        navTransition == null || (!navTransition.isRunning && kotlin.math.abs(navTransition.relativeDepth) <= 0.001f)
    }
    var refresh by remember(player) { mutableIntStateOf(0) }
    var playing by remember(player) { mutableStateOf(player.isPlaying) }
    var seekSettlesAt by remember(player) { mutableLongStateOf(0L) }
    var seekReady by remember(player) { mutableStateOf(true) }
    var surfaceGeneration by remember(playerView) { mutableIntStateOf(0) }
    val surface = playerView?.videoSurfaceView
    val bindingOwner = remember(presentation, controller, player, surface) { Any() }
    var surfaceAvailable by remember(surface) {
        mutableStateOf(when (surface) {
            is SurfaceView -> surface.holder.surface.isValid
            is TextureView -> surface.isAvailable
            else -> false
        })
    }
    DisposableEffect(player, surface) {
        presentation.requiredRefresh = refresh
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { surfaceAvailable = true; refresh++ }
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                seekReady = false
                seekSettlesAt = android.os.SystemClock.elapsedRealtime() + 200
                refresh++
                presentation.requiredRefresh = refresh
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                // Capture the final displayed frame on pause as well as on resume.
                refresh++
                presentation.requiredRefresh = refresh
            }
            override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_READY) refresh++ }
        }
        player.addListener(listener)
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) { surfaceAvailable = true; surfaceGeneration++; refresh++ }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { surfaceGeneration++; refresh++ }
            override fun surfaceDestroyed(holder: SurfaceHolder) { surfaceAvailable = false; surfaceGeneration++ }
        }
        (surface as? SurfaceView)?.holder?.addCallback(callback)
        onDispose {
            player.removeListener(listener)
            (surface as? SurfaceView)?.holder?.removeCallback(callback)
        }
    }
    var textureIdentity by remember(surface) { mutableStateOf((surface as? TextureView)?.surfaceTexture) }
    // Observe existing draws; never install over Media3's SurfaceTextureListener and
    // never schedule a timer/draw just to rediscover the same texture.
    val glowAllowed = settings.enabled && !hdr && !anime4k && !environment.severe
    val motionAllowed = com.android.purebilibili.core.ui.LocalComponentMotionEnabled.current &&
        !com.android.purebilibili.core.ui.LocalSystemReduceMotion.current
    val visualActive = com.android.purebilibili.core.ui.rememberVisualWorkActive()
    val surfaceObservationActive = visualActive && motionAllowed && foreground && !excluded && (glowAllowed || statusBar)
    DisposableEffect(surface, surfaceObservationActive) {
        val texture = (surface as? TextureView)?.takeIf { surfaceObservationActive }
        val observer = texture?.viewTreeObserver
        val listener = android.view.ViewTreeObserver.OnPreDrawListener {
            textureIdentity = texture?.surfaceTexture
            surfaceAvailable = texture?.isAvailable == true
            true
        }
        observer?.addOnPreDrawListener(listener)
        onDispose { if (observer?.isAlive == true) observer.removeOnPreDrawListener(listener) }
    }
    val recoveryReady = rememberUpdatedState {
        visualActive && foreground && !excluded && !transitioning && navigationSettled()
    }
    LaunchedEffect(player) {
        snapshotFlow { recoveryReady.value() }.collect { ready ->
            if (ready) {
                refresh++
                presentation.requiredRefresh = refresh
            }
        }
    }
    val active = surfaceAvailable && visualActive && foreground && !excluded && !transitioning && firstFrameReady && (glowAllowed || statusBar)
    LaunchedEffect(seekSettlesAt) {
        seekReady = false
        delay((seekSettlesAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        seekReady = true
    }
    val latestPolicy = rememberUpdatedState {
        val moving = transitioning || !navigationSettled()
        AmbientRunPolicy(active && !moving && seekReady, glowAllowed, playing && motionAllowed,
            settings.powerSaving || environment.saving, refresh, diagnosticLogging)
    }
    val surfaceLocation = remember(surface) { IntArray(2) }
    SideEffect {
        presentation.claimBinding(bindingOwner)
        presentation.layoutEnabled = glowAllowed && !excluded
        presentation.visible = glowAllowed && active
        presentation.visibilityGate = { !transitioning && navigationSettled() }
        presentation.videoBoundsInWindow = {
            surface?.takeIf { it.isAttachedToWindow && it.width > 0 && it.height > 0 }?.let {
                it.getLocationInWindow(surfaceLocation)
                androidx.compose.ui.geometry.Rect(
                    surfaceLocation[0].toFloat(), surfaceLocation[1].toFloat(),
                    (surfaceLocation[0] + it.width).toFloat(), (surfaceLocation[1] + it.height).toFloat(),
                )
            }
        }
        presentation.opacity = settings.opacity
        presentation.aspectRatio = width.coerceAtLeast(1).toFloat() / height.coerceAtLeast(1)
    }
    // Navigation progress is deliberately read inside the loop/draw provider, not high-frequency composition.
    LaunchedEffect(controller, surface, identity, quality, width, height, surfaceGeneration, textureIdentity) {
        controller.invalidate()
        // Visibility changes pause the loop through its policy, retaining the source and final
        // frame. Only a new surface/media session clears captured content.
        if (surface is SurfaceView || surface is TextureView) {
            controller.run(ViewAmbientFrameSource(surface), width, height) { latestPolicy.value() }
        }
    }
    DisposableEffect(presentation, controller, bindingOwner) {
        onDispose {
            if (presentation.releaseBinding(bindingOwner)) controller.invalidate()
        }
    }
}
