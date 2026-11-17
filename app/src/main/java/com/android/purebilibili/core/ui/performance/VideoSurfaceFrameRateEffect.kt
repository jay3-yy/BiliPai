@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.core.ui.performance

import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener

/** 只为自定义处理后的显示面提示内容帧率；原生播放面由 Media3 管理。 */
@Composable
internal fun VideoSurfaceFrameRateEffect(
    player: ExoPlayer,
    surfaceView: SurfaceView?,
    active: Boolean,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || surfaceView == null || !active) return

    DisposableEffect(player, surfaceView) {
        val holder = surfaceView.holder
        var appliedRate: Float? = null

        fun updateRate(frameRate: Float = player.videoFormat?.frameRate ?: 0f) {
            if (!holder.surface.isValid) return
            val rate = resolveVideoSurfaceFrameRate(
                frameRate = frameRate,
                speed = player.playbackParameters.speed,
                playing = player.isPlaying,
            )
            if (appliedRate == rate) return
            // 两参数 API 仅允许无缝切换；保留小数帧率，由系统选择显示档位。
            holder.surface.setFrameRate(rate, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
            appliedRate = rate
        }

        val surfaceCallback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                appliedRate = null
                updateRate()
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                updateRate()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                appliedRate = null
            }
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updateRate()
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?,
            ) {
                updateRate(format.frameRate)
            }
        }
        holder.addCallback(surfaceCallback)
        player.addListener(listener)
        player.addAnalyticsListener(analytics)
        updateRate()
        onDispose {
            holder.removeCallback(surfaceCallback)
            player.removeListener(listener)
            player.removeAnalyticsListener(analytics)
            if (appliedRate != null && appliedRate != 0f && holder.surface.isValid) {
                holder.surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
            }
        }
    }
}
