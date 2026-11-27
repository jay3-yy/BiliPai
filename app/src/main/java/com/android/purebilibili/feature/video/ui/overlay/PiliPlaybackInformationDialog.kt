package com.android.purebilibili.feature.video.ui.overlay

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import com.android.purebilibili.core.ui.AppAlertDialog
import com.android.purebilibili.core.ui.AppContentColors
import com.android.purebilibili.core.ui.AppDialogAction
import com.android.purebilibili.core.ui.components.AppText
import com.android.purebilibili.core.ui.resolveAppExpandedContentDialogLayoutPolicy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.android.purebilibili.core.ui.components.appVerticalScroll as verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.Tracks

/** Reads event-driven player state only; opening this dialog never probes platform decoders. */
@Composable
internal fun PiliPlaybackInformationDialog(
    player: Player?,
    debugInfo: PlaybackDebugInfo?,
    videoUrl: String,
    audioUrl: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var playerRevision by remember(player) { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                playerRevision++
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }
    val decoder = if (player is androidx.media3.exoplayer.ExoPlayer) {
        val flow = remember(player) { com.android.purebilibili.core.player.VideoDecoderDiagnostics.observe(player) }
        val snapshot by flow.collectAsStateWithLifecycle()
        snapshot
    } else null
    val effectiveDebugInfo = if (decoder != null) (debugInfo ?: PlaybackDebugInfo()).copy(
        videoDecoder = decoder.decoderName,
        videoDecoderType = decoder.kind.label,
        videoDecoderPreference = decoder.preference,
        videoDecoderFallbackReason = decoder.fallbackReason,
        videoDecoderDropWarning = decoder.dropRateWarning,
    ) else debugInfo
    val rows = remember(player, playerRevision, effectiveDebugInfo, videoUrl, audioUrl) {
        playbackInformationRows(player, effectiveDebugInfo, videoUrl, audioUrl)
    }

    AppAlertDialog(
        onDismissRequest = onDismiss,
        contentLayout = remember { resolveAppExpandedContentDialogLayoutPolicy() },
        title = { AppText("播放信息") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(row.label, row.value))
                                Toast.makeText(context, "已复制 ${row.label}", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppText(row.label, style = MaterialTheme.typography.bodyMedium)
                        AppText(
                            row.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = AppContentColors.secondaryText(),
                        )
                    }
                }
            }
        },
        confirmButton = {
            AppDialogAction(onClick = onDismiss) { AppText("确定") }
        },
    )
}

private const val UNKNOWN_PLAYBACK_VALUE = "未知"

private fun playbackInformationRows(
    player: Player?,
    debugInfo: PlaybackDebugInfo?,
    videoUrl: String,
    audioUrl: String,
): List<DebugStatRow> {
    val tracks = player?.currentTracks
    val video = tracks?.selectedPlaybackFormat(C.TRACK_TYPE_VIDEO)
    val audio = tracks?.selectedPlaybackFormat(C.TRACK_TYPE_AUDIO)
    return buildList {
        fun row(label: String, value: String?) {
            add(DebugStatRow(label, value?.takeIf { it.isNotBlank() } ?: UNKNOWN_PLAYBACK_VALUE))
        }
        row("Resolution", video?.takeIf { it.width > 0 && it.height > 0 }
            ?.let { "${it.width} × ${it.height}" } ?: debugInfo?.resolution)
        row("VideoParams", video?.playbackFormatDescription() ?: debugInfo?.videoCodec)
        row("AudioParams", audio?.playbackFormatDescription() ?: debugInfo?.audioCodec)
        row("Video bitrate", video?.playbackBitrate() ?: debugInfo?.videoBitrate)
        row("Audio bitrate", audio?.playbackBitrate() ?: debugInfo?.audioBitrate)
        row("Frame rate", video?.frameRate?.takeIf { it > 0f }?.let { "$it fps" } ?: debugInfo?.frameRate)
        row("AudioTrack", tracks?.playbackTrackDescription(C.TRACK_TYPE_AUDIO))
        row("VideoTrack", tracks?.playbackTrackDescription(C.TRACK_TYPE_VIDEO))
        row("rate", player?.playbackParameters?.speed?.let { "${it}×" })
        row("Volume", player?.volume?.let { "${it * 100f}%" })
        row("Video URL", videoUrl)
        row("Audio URL", audioUrl)
        row("Media3 state", player?.let {
            when (it.playbackState) {
                Player.STATE_IDLE -> "IDLE"
                Player.STATE_BUFFERING -> "BUFFERING"
                Player.STATE_READY -> "READY"
                Player.STATE_ENDED -> "ENDED"
                else -> UNKNOWN_PLAYBACK_VALUE
            }
        } ?: debugInfo?.playbackState)
        row("playWhenReady", player?.playWhenReady?.toString() ?: debugInfo?.playWhenReady)
        row("isPlaying", player?.isPlaying?.toString() ?: debugInfo?.isPlaying)
        debugInfo?.videoDecoder?.takeIf { it.isNotBlank() }?.let { row("Video decoder", it) }
        debugInfo?.videoDecoderType?.takeIf { it.isNotBlank() }?.let { row("实际解码类型", it) }
        debugInfo?.videoDecoderPreference?.takeIf { it.isNotBlank() }?.let { row("创建时解码偏好", it) }
        debugInfo?.videoDecoderFallbackReason?.takeIf { it.isNotBlank() }?.let { row("解码方式说明", it) }
        debugInfo?.videoDecoderDropWarning?.takeIf { it.isNotBlank() }?.let { row("掉帧诊断", it) }
        debugInfo?.audioDecoder?.takeIf { it.isNotBlank() }?.let { row("Audio decoder", it) }
        player?.playerError?.let { row("Player error", "${it.errorCodeName}: ${it.message.orEmpty()}") }
    }
}

private fun Tracks.selectedPlaybackFormat(type: Int): Format? {
    for (group in groups) {
        if (group.type != type) continue
        for (index in 0 until group.length) {
            if (group.isTrackSelected(index)) return group.getTrackFormat(index)
        }
    }
    return null
}

private fun Tracks.playbackTrackDescription(type: Int): String? {
    val descriptions = buildList {
        var trackNumber = 0
        for (group in groups) {
            if (group.type != type) continue
            for (index in 0 until group.length) {
                trackNumber++
                val format = group.getTrackFormat(index)
                add(buildString {
                    append(if (group.isTrackSelected(index)) "✓ " else "• ")
                    append(trackNumber)
                    format.id?.let { append(" · id=").append(it) }
                    format.language?.let { append(" · ").append(it) }
                    append(" · ").append(format.playbackFormatDescription())
                })
            }
        }
    }
    return descriptions.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

private fun Format.playbackBitrate(): String? = when {
    averageBitrate > 0 -> "$averageBitrate bit/s"
    peakBitrate > 0 -> "$peakBitrate bit/s (peak)"
    else -> null
}

private fun Format.playbackFormatDescription(): String = buildList {
    sampleMimeType?.let { add(it) }
    codecs?.let { add(it) }
    if (width > 0 && height > 0) add("$width × $height")
    if (sampleRate > 0) add("$sampleRate Hz")
    if (channelCount > 0) add("$channelCount channels")
    playbackBitrate()?.let { add(it) }
}.joinToString(" · ").ifBlank { UNKNOWN_PLAYBACK_VALUE }
