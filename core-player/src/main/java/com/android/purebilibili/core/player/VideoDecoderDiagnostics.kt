@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.android.purebilibili.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer.DecoderInitializationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap

enum class VideoDecoderKind(val label: String) { HARDWARE("硬件解码"), SOFTWARE("软件解码"), UNKNOWN("未知") }

internal object VideoDecoderCapabilities {
    private val kinds = ConcurrentHashMap<String, VideoDecoderKind>()
    fun record(name: String, hardware: Boolean, software: Boolean) {
        kinds[name] = when {
            software -> VideoDecoderKind.SOFTWARE
            hardware -> VideoDecoderKind.HARDWARE
            else -> VideoDecoderKind.UNKNOWN
        }
    }
    fun kind(name: String): VideoDecoderKind = kinds[name] ?: VideoDecoderKind.UNKNOWN
}

data class VideoDecoderDiagnosticState(
    val decoderName: String = "",
    val kind: VideoDecoderKind = VideoDecoderKind.UNKNOWN,
    val preference: String = "未知",
    val fallbackReason: String = "正在准备视频解码",
)

internal fun decoderSelectionReason(preferHardware: Boolean, kind: VideoDecoderKind, failures: List<String>): String {
    if (failures.isNotEmpty()) return "原解码器启动失败，已换用其他解码器。\n错误详情：${failures.joinToString("；")}"
    if (kind == VideoDecoderKind.UNKNOWN) return "暂时无法确认正在使用硬件解码还是软件解码。"
    val current = kind.label
    val preferred = if (preferHardware) "硬件解码" else "软件解码"
    return if ((preferHardware && kind == VideoDecoderKind.HARDWARE) || (!preferHardware && kind == VideoDecoderKind.SOFTWARE)) {
        "当前使用${current}，符合播放设置。"
    } else {
        "当前使用${current}；未能确认为什么没有使用${preferred}。"
    }
}

/** 按播放器保存事件快照，不轮询、不注册逐帧任务；释放播放器后弱键可回收。 */
object VideoDecoderDiagnostics {
    private class PlayerDiagnosticEntry {
        val state = MutableStateFlow(VideoDecoderDiagnosticState())
        val view: StateFlow<VideoDecoderDiagnosticState> = state.asStateFlow()
        var attached = false
    }

    private val entries = WeakHashMap<ExoPlayer, PlayerDiagnosticEntry>()

    @Synchronized
    fun observe(player: ExoPlayer): StateFlow<VideoDecoderDiagnosticState> =
        entries.getOrPut(player) { PlayerDiagnosticEntry() }.view

    @Synchronized
    fun attach(player: ExoPlayer, preferHardware: Boolean): ExoPlayer {
        val entry = entries.getOrPut(player) { PlayerDiagnosticEntry() }
        val preferenceLabel = if (preferHardware) "硬件优先" else "软件优先"
        entry.state.update { it.copy(preference = preferenceLabel) }
        if (entry.attached) return player
        entry.attached = true
        val state = entry.state
        val failures = ArrayDeque<String>()
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoCodecError(eventTime: AnalyticsListener.EventTime, videoCodecError: Exception) {
                if (videoCodecError is DecoderInitializationException) {
                    val failure = listOfNotNull(videoCodecError.codecInfo?.name, videoCodecError.diagnosticInfo,
                        videoCodecError.cause?.javaClass?.simpleName).joinToString(" · ").take(240)
                    if (failures.size == 4) failures.removeFirst()
                    failures.addLast(failure.ifBlank { "初始化失败" })
                    state.update { it.copy(fallbackReason = "解码器启动失败，正在尝试其他解码器。\n错误详情：${failures.joinToString("；")}") }
                } else {
                    state.update { it.copy(fallbackReason = "解码时发生错误，暂未确认是否已换用其他解码器。\n错误详情：${videoCodecError.javaClass.simpleName}") }
                }
            }
            override fun onVideoDecoderInitialized(eventTime: AnalyticsListener.EventTime, decoderName: String,
                initializedTimestampMs: Long, initializationDurationMs: Long) {
                val kind = VideoDecoderCapabilities.kind(decoderName)
                val reason = decoderSelectionReason(preferHardware, kind, failures.toList())
                state.update { current ->
                    VideoDecoderDiagnosticState(decoderName, kind, current.preference, reason)
                }
                failures.clear()
            }
            override fun onVideoDecoderReleased(eventTime: AnalyticsListener.EventTime, decoderName: String) {
                state.update { it.copy(decoderName = "", kind = VideoDecoderKind.UNKNOWN, fallbackReason = "当前未使用解码器，等待重新启动。") }
            }
            override fun onMediaItemTransition(eventTime: AnalyticsListener.EventTime, mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
                failures.clear()
                state.update { it.copy(fallbackReason = if (it.decoderName.isBlank()) "正在准备视频解码" else "继续使用当前解码器，未记录到本视频的解码器切换。") }
            }
        })
        return player
    }
}
