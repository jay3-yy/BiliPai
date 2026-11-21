package com.android.purebilibili.feature.video.screen

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import com.android.purebilibili.core.util.resolveSafeAndroidPipRational
import com.android.purebilibili.feature.video.player.MiniPlayerManager
import com.android.purebilibili.feature.video.player.buildPipPlaybackRemoteActions
import com.android.purebilibili.feature.video.ui.section.shouldKeepVideoPlaybackAwake
import kotlinx.coroutines.flow.collect

@Composable
internal fun VideoDetailPipParamsEffect(
    context: Context,
    activity: Activity?,
    playerBoundsProvider: () -> Rect?,
    pipModeEnabled: Boolean,
    player: Player?,
) {
    var lastPipBounds by remember(player) { mutableStateOf<Rect?>(null) }
    var lastPipModeEnabled by remember(player) { mutableStateOf<Boolean?>(null) }
    var lastPipUpdateElapsedMs by remember(player) { mutableLongStateOf(0L) }
    val latestPlayer by rememberUpdatedState(player)
    val latestPlayerBoundsProvider by rememberUpdatedState(playerBoundsProvider)
    val supportsPip = remember(activity) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity?.packageManager?.hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE
            ) == true
    }

    // Android gesture navigation can enter PiP before onUserLeaveHint. Keep its
    // eligibility synchronized with the player, rather than a cached UI state.
    DisposableEffect(activity, player, pipModeEnabled) {
        var lastAutoEnterEnabled: Boolean? = null
        fun updateAutoEnter() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsPip && activity != null) {
                val enabled = pipModeEnabled && player?.isPlaying == true &&
                    MiniPlayerManager.getInstance(context).shouldEnterPip()
                if (lastAutoEnterEnabled == enabled) return
                lastAutoEnterEnabled = enabled
                activity.setPictureInPictureParams(
                    android.app.PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(enabled)
                        .build()
                )
            }
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = updateAutoEnter()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updateAutoEnter()
        }
        player?.addListener(listener)
        updateAutoEnter()
        onDispose {
            player?.removeListener(listener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supportsPip && activity != null) {
                activity.setPictureInPictureParams(
                    android.app.PictureInPictureParams.Builder()
                        .setAutoEnterEnabled(false)
                        .build()
                )
            }
        }
    }

    LaunchedEffect(activity, player, pipModeEnabled) {
        if (!pipModeEnabled || !supportsPip || activity == null) return@LaunchedEffect
        // 布局回调只更新画中画来源区域，详情根组件无需订阅几何变化。
        snapshotFlow { latestPlayerBoundsProvider() }.collect { bounds ->
            val modeChanged = lastPipModeEnabled == null || lastPipModeEnabled != pipModeEnabled
            val boundsChanged = hasMeaningfulVideoPlayerBoundsChange(lastPipBounds, bounds)
            val now = android.os.SystemClock.elapsedRealtime()
            if (!shouldApplyPipParamsUpdate(
                    pipModeEnabled = pipModeEnabled,
                    modeChanged = modeChanged,
                    boundsChanged = boundsChanged,
                    elapsedSinceLastUpdateMs = now - lastPipUpdateElapsedMs,
                )
            ) return@collect

            lastPipBounds = bounds?.let(::Rect)
            lastPipModeEnabled = pipModeEnabled
            lastPipUpdateElapsedMs = now
            val videoWidth = latestPlayer?.videoSize?.width ?: 0
            val videoHeight = latestPlayer?.videoSize?.height ?: 0
            val params = android.app.PictureInPictureParams.Builder()
                .setAspectRatio(resolveSafeAndroidPipRational(videoWidth, videoHeight))
                .setActions(buildPipPlaybackRemoteActions(context, latestPlayer))
                .apply {
                    bounds?.let(::setSourceRectHint)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setAutoEnterEnabled(
                            pipModeEnabled && latestPlayer?.isPlaying == true &&
                                MiniPlayerManager.getInstance(context).shouldEnterPip()
                        )
                        setSeamlessResizeEnabled(pipModeEnabled)
                    }
                }
                .build()
            activity.setPictureInPictureParams(params)
        }
    }
}

/** 播放及缓冲保留常亮，暂停、结束和空闲立即释放常亮资格。 */
@Composable
internal fun rememberVideoPlaybackKeepAwake(player: Player): Boolean {
    val shouldKeepAwake by produceState(
        initialValue = shouldKeepVideoPlaybackAwake(
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            playbackState = player.playbackState,
        ),
        key1 = player,
    ) {
        fun update() {
            value = shouldKeepVideoPlaybackAwake(
                playWhenReady = player.playWhenReady,
                isPlaying = player.isPlaying,
                playbackState = player.playbackState,
            )
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = update()
            override fun onPlaybackStateChanged(playbackState: Int) = update()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = update()
        }
        player.addListener(listener)
        update()
        awaitDispose { player.removeListener(listener) }
    }
    return shouldKeepAwake
}

@Composable
internal fun VideoDetailKeepScreenOnEffect(
    window: Window?,
    player: Player,
) {
    val shouldKeepAwake = rememberVideoPlaybackKeepAwake(player)
    DisposableEffect(window, shouldKeepAwake) {
        if (shouldKeepAwake) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (shouldKeepAwake) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
internal fun VideoDetailSystemBarsEffect(
    view: View,
    window: Window?,
    insetsController: WindowInsetsControllerCompat?,
    isScreenActive: Boolean,
    spec: VideoDetailSystemBarsApplySpec,
    reapplyGeneration: Int = 0,
    playerBackdropDarkIcons: State<Boolean>? = null,
) {
    // The generation re-commits the same policy after a cancelled return or a retained route
    // becomes topmost again.
    val darkIcons = playerBackdropDarkIcons?.value == true
    val effectiveSpec = remember(spec, reapplyGeneration, darkIcons) {
        if (darkIcons) spec.copy(lightStatusBars = true) else spec
    }
    SideEffect {
        if (view.isInEditMode || !isScreenActive || window == null || insetsController == null) return@SideEffect
        applyVideoDetailSystemBarsSpec(window, insetsController, effectiveSpec)
    }
}
