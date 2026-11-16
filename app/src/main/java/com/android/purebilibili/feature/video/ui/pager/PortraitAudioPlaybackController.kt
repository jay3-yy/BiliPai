package com.android.purebilibili.feature.video.ui.pager

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.core.util.MediaUtils
import com.android.purebilibili.plugin.sdk.PlaybackCdnPlugin
import com.android.purebilibili.feature.plugin.PlaybackCdnPreference
import com.android.purebilibili.feature.video.playback.audio.AudioSelectionDecision
import com.android.purebilibili.feature.video.playback.audio.collectAudioStreamCandidates
import com.android.purebilibili.feature.video.playback.audio.resolveAudioStreamSelection

internal data class PortraitAudioSourceSwitchResult(
    val videoUrl: String,
    val audioUrl: String,
    val selection: AudioSelectionDecision
)

/**
 * 复用当前视频轨，仅替换竖屏播放器的音频轨，并保留进度和播放状态。
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun switchPortraitPlaybackAudioSource(
    player: ExoPlayer,
    mediaSourceFactory: DefaultMediaSourceFactory,
    dash: Dash,
    currentVideoUrl: String,
    requestedAudioQuality: Int,
    targetVideoQuality: Int,
    mediaId: String,
    cdnPlugin: PlaybackCdnPlugin?,
    preference: PlaybackCdnPreference = PlaybackCdnPreference.DEFAULT,
    isDolbyAudioSupported: Boolean = MediaUtils.isDolbyAtmosAudioSupported(),
    isDolbyAudioSoftwareDecoded: Boolean = MediaUtils.isDolbySoftwareAudioDecoderRequired()
): PortraitAudioSourceSwitchResult? {
    if (currentVideoUrl.isBlank() || mediaId.isBlank()) return null
    val selection = resolveAudioStreamSelection(
        dash = dash,
        requestedAudioQuality = requestedAudioQuality,
        playbackSpeed = player.playbackParameters.speed,
        isDolbyAudioSupported = isDolbyAudioSupported,
        isDolbyAudioSoftwareDecoded = isDolbyAudioSoftwareDecoded
    )
    val selectedAudioUrl = selection.selected?.track?.getValidUrl()
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val resolvedUrls = resolvePortraitPlaybackCdnUrls(
        streamUrls = PortraitPlaybackStreamUrls(
            videoUrl = currentVideoUrl,
            audioUrl = selectedAudioUrl,
            audioSelection = selection
        ),
        cachedDashVideos = dash.video,
        cachedDashAudios = collectAudioStreamCandidates(
            dash = dash,
            isDolbyAudioSupported = isDolbyAudioSupported
        ).map { it.track },
        targetQuality = targetVideoQuality,
        cdnPlugin = cdnPlugin,
        preference = preference,
    )
    val resolvedAudioUrl = resolvedUrls.audioUrl?.takeIf { it.isNotBlank() } ?: return null
    val videoSource = mediaSourceFactory.createMediaSource(
        MediaItem.Builder()
            .setUri(resolvedUrls.videoUrl)
            .setMediaId(mediaId)
            .build()
    )
    val audioSource = mediaSourceFactory.createMediaSource(
        MediaItem.Builder()
            .setUri(resolvedAudioUrl)
            .setMediaId("audio_$mediaId")
            .build()
    )
    val currentPosition = player.currentPosition.coerceAtLeast(0L)
    val playWhenReady = player.playWhenReady

    player.setMediaSource(MergingMediaSource(videoSource, audioSource), false)
    player.prepare()
    player.seekTo(currentPosition)
    player.playWhenReady = playWhenReady
    return PortraitAudioSourceSwitchResult(
        videoUrl = resolvedUrls.videoUrl,
        audioUrl = resolvedAudioUrl,
        selection = selection
    )
}
