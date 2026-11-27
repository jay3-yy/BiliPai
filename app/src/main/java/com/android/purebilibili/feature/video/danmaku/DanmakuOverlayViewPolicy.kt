@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.feature.video.danmaku

import android.os.Build
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.android.purebilibili.core.ui.adaptive.DeviceThermalBudgetState
import com.android.purebilibili.core.ui.adaptive.DeviceThermalTier
import com.android.purebilibili.core.util.prefersTextureVideoSurface
import com.android.purebilibili.danmaku.engine.DanmakuRenderView

internal fun DanmakuRenderView.configureAsPassiveDanmakuOverlay() {
    // 原生视图不抢触摸；Compose 确认单击后通过 DanmakuManager.captureTap 做命中检测。
    isClickable = false
    isLongClickable = false
    isFocusable = false
    isFocusableInTouchMode = false
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    setRendererTouchable(false)
    setOnTouchListener { _, _ -> false }
}

/**
 * 判定弹幕层是否可以安全挂载到独立的媒体叠加 `SurfaceView`：
 * - 需 Android 11+（`Surface.setFrameRate`、硬件 Canvas 与 HWC `SurfaceControl.Transaction.setAlpha` 均可用，
 *   避免 Android 10 无 LTPO 帧率提示却额外占用独立全屏 Surface 显存与 HWC 图层）；
 * - 仅在主视频使用标准 `SurfaceView` 且未启用 Anime4K（其自身已占用 mediaOverlay Z 序）、
 *   未录制液态玻璃 `playerPageBackdrop`、未处于 PiP、非 Android 13 Texture 兼容白名单设备时启用。
 */
internal fun shouldUseIndependentDanmakuSurface(
    useTextureVideoSurface: Boolean,
    anime4kActive: Boolean,
    capturePlayerPage: Boolean,
    isInPipMode: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
    preferCompatibleTextureSurface: Boolean = prefersTextureVideoSurface(),
): Boolean {
    if (sdkInt < Build.VERSION_CODES.R) return false
    if (useTextureVideoSurface || anime4kActive || capturePlayerPage || isInPipMode) return false
    if (preferCompatibleTextureSurface) return false
    return true
}

/**
 * 为弹幕渲染层解析 LTPO / SurfaceFlinger 帧率提示（Hz）：
 * - 暂停或隐藏时返回 `0f`，释放帧率锁定以便 LTPO 面板回落到 1~10Hz；
 * - 热临界降到 `30f`；升温档位在 <=30fps 视频下对齐 `30f`、否则 `60f`；
 * - 正常档位对常见 30/60fps 视频提示 `60f`（与视频帧率成整数倍，允许 120Hz LTPO 回落至 60Hz），
 *   对 24fps 电影/番剧与 >60fps 高帧率视频返回 `0f` 保留原生 VSync，避免 3:2 pulldown 抖动。
 */
internal fun resolveDanmakuTargetFrameRateHz(
    videoFrameRateHz: Float,
    playbackSpeed: Float,
    isPlaying: Boolean,
    thermalTier: DeviceThermalTier,
): Float {
    if (!isPlaying) return 0f
    val safeSpeed = if (playbackSpeed.isFinite() && playbackSpeed > 0f) playbackSpeed else 1f
    val effectiveVideoFps = if (videoFrameRateHz.isFinite() && videoFrameRateHz > 1f) {
        (videoFrameRateHz * safeSpeed).coerceIn(1f, 240f)
    } else {
        0f
    }
    return when (thermalTier) {
        DeviceThermalTier.CRITICAL -> 30f
        DeviceThermalTier.ELEVATED -> if (effectiveVideoFps in 1f..31f) 30f else 60f
        DeviceThermalTier.NORMAL -> if (effectiveVideoFps in 28f..61f) 60f else 0f
    }
}

private fun readPlayerVideoFrameRateHz(player: Player): Float {
    val decoderRate = (player as? ExoPlayer)?.videoFormat?.frameRate ?: 0f
    if (decoderRate.isFinite() && decoderRate > 1f) return decoderRate
    val groups = player.currentTracks.groups
    for (i in 0 until groups.size) {
        val group = groups[i]
        if (group.type != C.TRACK_TYPE_VIDEO || !group.isSelected) continue
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSelected(trackIndex)) continue
            val trackRate = group.getTrackFormat(trackIndex).frameRate
            if (trackRate.isFinite() && trackRate > 1f) return trackRate
        }
    }
    return 0f
}

/**
 * 响应式观察播放器视频帧率、倍速与设备热档，确保首个视频帧解码出格式或切倍速后立即刷新弹幕 LTPO 帧率提示。
 * 仅监听离散事件，不产生逐帧轮询或多余重组。
 */
@Composable
internal fun rememberPlayerDanmakuTargetFrameRateHz(
    player: Player?,
    isPlaying: Boolean,
): Float {
    val thermalTier by DeviceThermalBudgetState.tierFlow.collectAsStateWithLifecycle()
    var videoFrameRateHz by remember(player) {
        mutableFloatStateOf(player?.let(::readPlayerVideoFrameRateHz) ?: 0f)
    }
    var playbackSpeed by remember(player) {
        mutableFloatStateOf(player?.playbackParameters?.speed ?: 1f)
    }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose {}
        videoFrameRateHz = readPlayerVideoFrameRateHz(player)
        playbackSpeed = player.playbackParameters.speed
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                videoFrameRateHz = readPlayerVideoFrameRateHz(player)
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val nextRate = readPlayerVideoFrameRateHz(player)
                if (nextRate > 0f) videoFrameRateHz = nextRate
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                playbackSpeed = playbackParameters.speed
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val nextRate = readPlayerVideoFrameRateHz(player)
                    if (nextRate > 0f) videoFrameRateHz = nextRate
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return resolveDanmakuTargetFrameRateHz(
        videoFrameRateHz = videoFrameRateHz,
        playbackSpeed = playbackSpeed,
        isPlaying = isPlaying,
        thermalTier = thermalTier,
    )
}
