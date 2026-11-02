package com.android.purebilibili.feature.video.playback.policy

import com.android.purebilibili.core.player.policy.PlaybackQualityMode

import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.Dolby
import com.android.purebilibili.data.model.response.Flac
import com.android.purebilibili.feature.video.playback.audio.AUDIO_QUALITY_DOLBY
import com.android.purebilibili.feature.video.playback.audio.AUDIO_QUALITY_HI_RES
import com.android.purebilibili.feature.video.playback.audio.AudioStreamKind
import kotlin.test.Test
import kotlin.test.assertEquals

class AdaptiveDashTrackPolicyTest {

    private val dash = Dash(
        video = listOf(
            DashVideo(id = 120, baseUrl = "https://example.com/4k-av1.m4s", codecs = "av01", bandwidth = 20_000_000),
            DashVideo(id = 80, baseUrl = "https://example.com/1080-hevc.m4s", codecs = "hev1", bandwidth = 8_000_000),
            DashVideo(id = 64, baseUrl = "https://example.com/720-avc.m4s", codecs = "avc1", bandwidth = 4_000_000),
            DashVideo(id = 32, baseUrl = "https://example.com/480-avc.m4s", codecs = "avc1", bandwidth = 1_500_000)
        ),
        audio = listOf(
            DashAudio(id = 30280, baseUrl = "https://example.com/audio-192k.m4s", codecs = "mp4a.40.2", bandwidth = 192_000),
            DashAudio(id = 30216, baseUrl = "https://example.com/audio-64k.m4s", codecs = "mp4a.40.2", bandwidth = 64_000)
        )
    )

    @Test
    fun `auto mode keeps all playable tracks up to quality cap`() {
        val result = buildAdaptiveDashTrackSet(
            dash = dash,
            mode = PlaybackQualityMode.AUTO,
            autoQualityCap = 80,
            preferredAudioQuality = -1,
            preferredVideoCodec = "hev1",
            secondaryVideoCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals(listOf(80, 64, 32), result.videoTracks.map { it.id })
        assertEquals(listOf(30280, 30216), result.audioTracks.map { it.id })
    }

    @Test
    fun `locked mode keeps only the requested quality`() {
        val result = buildAdaptiveDashTrackSet(
            dash = dash,
            mode = PlaybackQualityMode.LOCKED(64),
            autoQualityCap = 80,
            preferredAudioQuality = -1,
            preferredVideoCodec = "hev1",
            secondaryVideoCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals(listOf(64), result.videoTracks.map { it.id })
    }

    @Test
    fun `session codec fallback removes blocked av1 tracks from auto mode`() {
        val result = buildAdaptiveDashTrackSet(
            dash = dash,
            mode = PlaybackQualityMode.AUTO,
            autoQualityCap = 120,
            preferredAudioQuality = -1,
            preferredVideoCodec = "av01",
            secondaryVideoCodec = "hev1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals(listOf(80, 64, 32), result.videoTracks.map { it.id })
    }

    @Test
    fun `high speed resolves hi res preference to standard dash audio`() {
        val effective = resolveSpeedCompatibleAudioQualityPreference(
            requestedAudioQuality = 30251,
            playbackSpeed = 2.0f
        )

        assertEquals(-1, effective)
    }

    @Test
    fun `normal speed keeps hi res preference`() {
        val effective = resolveSpeedCompatibleAudioQualityPreference(
            requestedAudioQuality = 30251,
            playbackSpeed = 1.25f
        )

        assertEquals(30251, effective)
    }

    @Test
    fun `high speed keeps standard audio preference`() {
        val effective = resolveSpeedCompatibleAudioQualityPreference(
            requestedAudioQuality = 30280,
            playbackSpeed = 3.0f
        )

        assertEquals(30280, effective)
    }

    @Test
    fun `explicit hi res keeps only flac representation`() {
        val hiRes = DashAudio(
            id = AUDIO_QUALITY_HI_RES,
            baseUrl = "https://example.com/audio-hires.m4s",
            codecs = "fLaC",
            bandwidth = 1_800_000
        )
        val result = buildAdaptiveDashTrackSet(
            dash = dash.copy(flac = Flac(display = true, audio = hiRes)),
            mode = PlaybackQualityMode.AUTO,
            autoQualityCap = 80,
            preferredAudioQuality = AUDIO_QUALITY_HI_RES,
            preferredVideoCodec = "hev1",
            secondaryVideoCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals(listOf("https://example.com/audio-hires.m4s"), result.audioTracks.map { it.getValidUrl() })
    }

    @Test
    fun `explicit dolby keeps only dolby representation`() {
        val dolby = DashAudio(
            id = AUDIO_QUALITY_DOLBY,
            baseUrl = "https://example.com/audio-dolby.m4s",
            bandwidth = 448_000,
            codecs = "ec-3"
        )
        val result = buildAdaptiveDashTrackSet(
            dash = dash.copy(dolby = Dolby(type = 1, audio = listOf(dolby))),
            mode = PlaybackQualityMode.AUTO,
            autoQualityCap = 80,
            preferredAudioQuality = AUDIO_QUALITY_DOLBY,
            preferredVideoCodec = "hev1",
            secondaryVideoCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals(listOf("https://example.com/audio-dolby.m4s"), result.audioTracks.map { it.getValidUrl() })
    }

    @Test
    fun `unsupported dolby decoder falls back to standard representation`() {
        val dolby = DashAudio(
            id = AUDIO_QUALITY_DOLBY,
            baseUrl = "https://example.com/audio-dolby.m4s",
            bandwidth = 448_000,
            codecs = "ec-3"
        )
        val result = buildAdaptiveDashTrackSet(
            dash = dash.copy(dolby = Dolby(type = 1, audio = listOf(dolby))),
            mode = PlaybackQualityMode.AUTO,
            autoQualityCap = 80,
            preferredAudioQuality = AUDIO_QUALITY_DOLBY,
            preferredVideoCodec = "hev1",
            secondaryVideoCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = false,
            isDolbyAudioSupported = false
        )

        assertEquals(listOf(30280), result.audioTracks.map { it.id })
    }

    @Test
    fun `explicit unavailable premium audio falls back to one best standard representation`() {
        val result = buildAdaptiveDashTrackSet(
            dash = dash,
            mode = PlaybackQualityMode.AUTO,
            autoQualityCap = 80,
            preferredAudioQuality = AUDIO_QUALITY_HI_RES,
            preferredVideoCodec = "hev1",
            secondaryVideoCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals(listOf(30280), result.audioTracks.map { it.id })
    }
    @Test
    fun `speed transitions preserve raw special preferences using actual returned kind`() {
        for (kind in listOf(AudioStreamKind.DOLBY, AudioStreamKind.HI_RES)) {
            assertEquals(100010, resolveSpeedCompatibleAudioQualityPreference(100010, 1.5f, requestedAudioKind = kind))
            assertEquals(-1, resolveSpeedCompatibleAudioQualityPreference(100010, 1.51f, requestedAudioKind = kind))
        }
        assertEquals(100010, resolveSpeedCompatibleAudioQualityPreference(100010, 2f, requestedAudioKind = AudioStreamKind.STANDARD))
        assertEquals(-1, resolveSpeedCompatibleAudioQualityPreference(30255, 2f))
    }
}
