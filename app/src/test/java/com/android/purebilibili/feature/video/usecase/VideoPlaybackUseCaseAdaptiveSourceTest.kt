package com.android.purebilibili.feature.video.usecase

import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.Durl
import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.model.response.SegmentBase
import com.android.purebilibili.core.player.dash.AdaptiveDashPlaybackSource
import com.android.purebilibili.core.player.policy.PlaybackQualityMode
import com.android.purebilibili.core.util.VideoTrackDecoderSupport
import com.android.purebilibili.core.util.selectPlayableVideoTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoPlaybackUseCaseAdaptiveSourceTest {

    @Test
    fun `unsupported dash does not bypass capability checks while empty dash keeps legacy url playback`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { emptyList() })
        val legacy = Durl(url = "https://example.com/legacy.mp4")
        fun select(videos: List<DashVideo>) = useCase.resolvePlaybackSelection(
            playUrlData = PlayUrlData(quality = 80, dash = Dash(video = videos), durl = listOf(legacy)),
            targetQuality = 80,
            isHevcSupported = true,
            isAv1Supported = false,
            isDolbyAudioSupported = false,
            isDolbyAudioSoftwareDecoded = false,
            allowPremiumApiOnlyQualities = false,
        )
        assertEquals(legacy.url, select(emptyList())?.videoUrl)
        assertNull(select(listOf(DashVideo(id = 80, codecs = "hev1", baseUrl = "https://example.com/unsupported"))))
    }

    @Test
    fun `first play and cached quality switch keep software only av1 out of hardware preferred manifest`() {
        val av1 = DashVideo(id = 80, codecs = "av01", baseUrl = "https://example.com/av1.m4s")
        val hevc = DashVideo(id = 80, codecs = "hev1", baseUrl = "https://example.com/hevc.m4s")
        val dash = Dash(video = listOf(av1, hevc))
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { tracks ->
            selectPlayableVideoTracks(tracks, preferHardware = true) { video ->
                VideoTrackDecoderSupport(true, hardware = video == hevc, software = video == av1)
            }
        })
        val first = useCase.resolvePlaybackSelection(
            playUrlData = PlayUrlData(quality = 80, dash = dash),
            targetQuality = 80,
            videoCodecPreference = "av01",
            isHevcSupported = true,
            isAv1Supported = true,
            isDolbyAudioSupported = false,
            isDolbyAudioSoftwareDecoded = false,
            allowPremiumApiOnlyQualities = false,
        )
        assertEquals(hevc.baseUrl, first?.videoUrl)
        assertEquals(listOf(hevc), first?.adaptiveDashSource?.videoTracks)

        val cached = useCase.changeQualityFromCache(
            qualityId = 80,
            cachedVideos = dash.video,
            cachedAudios = emptyList(),
            cachedDash = dash,
            currentPos = 20_000L,
            videoCodecPreference = "av01",
            isHevcSupported = true,
            isAv1Supported = true,
            isDolbyAudioSupported = false,
            isDolbyAudioSoftwareDecoded = false,
            applyToPlayer = false,
        )
        assertEquals(hevc.baseUrl, cached?.videoUrl)
        assertEquals(listOf(hevc), cached?.adaptiveDashSource?.videoTracks)
    }

    @Test
    fun `resolve playback selection exposes adaptive dash source in auto mode`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })
        val playUrlData = PlayUrlData(
            quality = 80,
            acceptQuality = listOf(80, 64, 32),
            dash = Dash(
                duration = 123,
                minBufferTime = 1.5f,
                video = listOf(
                    DashVideo(
                        id = 80,
                        baseUrl = "https://example.com/video-1080.m4s",
                        bandwidth = 8_000_000,
                        mimeType = "video/mp4",
                        codecs = "hev1",
                        width = 1920,
                        height = 1080,
                        frameRate = "60",
                        segmentBase = SegmentBase("0-999", "1000-1999")
                    ),
                    DashVideo(
                        id = 64,
                        baseUrl = "https://example.com/video-720.m4s",
                        bandwidth = 4_000_000,
                        mimeType = "video/mp4",
                        codecs = "avc1",
                        width = 1280,
                        height = 720,
                        frameRate = "30",
                        segmentBase = SegmentBase("0-888", "889-1777")
                    )
                ),
                audio = listOf(
                    DashAudio(
                        id = 30280,
                        baseUrl = "https://example.com/audio.m4s",
                        bandwidth = 192_000,
                        mimeType = "audio/mp4",
                        codecs = "mp4a.40.2",
                        segmentBase = SegmentBase("0-555", "556-999")
                    )
                )
            )
        )

        val selection = useCase.resolvePlaybackSelection(
            playUrlData = playUrlData,
            targetQuality = 80,
            audioQualityPreference = -1,
            videoCodecPreference = "hev1",
            videoSecondCodecPreference = "avc1",
            playbackQualityMode = PlaybackQualityMode.AUTO,
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertNotNull(selection)
        assertNotNull(selection.adaptiveDashSource)
        assertEquals(PlaybackQualityMode.AUTO, selection.adaptiveDashSource.playbackQualityMode)
        assertEquals(listOf(80, 64), selection.adaptiveDashSource.videoTracks.map { it.id })
    }

    @Test
    fun `adaptive dash playback uses local manifest when segment base is complete`() {
        val source = AdaptiveDashPlaybackSource(
            manifest = "<MPD />",
            videoTracks = listOf(
                DashVideo(
                    id = 80,
                    baseUrl = "https://example.com/video.m4s",
                    segmentBase = SegmentBase("0-999", "1000-1999")
                )
            ),
            audioTracks = listOf(
                DashAudio(
                    id = 30280,
                    baseUrl = "https://example.com/audio.m4s",
                    segmentBase = SegmentBase("0-555", "556-999")
                )
            ),
            playbackQualityMode = PlaybackQualityMode.AUTO
        )

        assertTrue(
            shouldUseAdaptiveDashPlayback(
                adaptiveDashSource = source,
                audioUrl = "https://example.com/audio.m4s"
            )
        )
    }

    @Test
    fun `adaptive dash playback falls back when segment base is incomplete`() {
        val source = AdaptiveDashPlaybackSource(
            manifest = "<MPD />",
            videoTracks = listOf(
                DashVideo(
                    id = 80,
                    baseUrl = "https://example.com/video.m4s",
                    segmentBase = SegmentBase(initialization = "0-999", indexRange = null)
                )
            ),
            audioTracks = emptyList(),
            playbackQualityMode = PlaybackQualityMode.AUTO
        )

        assertFalse(
            shouldUseAdaptiveDashPlayback(
                adaptiveDashSource = source,
                audioUrl = null
            )
        )
    }

    @Test
    fun `adaptive dash playback falls back when dash segment requests are disabled`() {
        val source = AdaptiveDashPlaybackSource(
            manifest = "<MPD />",
            videoTracks = listOf(
                DashVideo(
                    id = 80,
                    baseUrl = "https://example.com/video.m4s",
                    segmentBase = SegmentBase("0-999", "1000-1999")
                )
            ),
            audioTracks = emptyList(),
            playbackQualityMode = PlaybackQualityMode.AUTO
        )

        assertFalse(
            shouldUseAdaptiveDashPlayback(
                adaptiveDashSource = source,
                audioUrl = null,
                dashSegmentRequestsEnabled = false
            )
        )
    }

    @Test
    fun `adaptive dash playback falls back when selected audio is missing from manifest`() {
        val source = AdaptiveDashPlaybackSource(
            manifest = "<MPD />",
            videoTracks = listOf(
                DashVideo(
                    id = 80,
                    baseUrl = "https://example.com/video.m4s",
                    segmentBase = SegmentBase("0-999", "1000-1999")
                )
            ),
            audioTracks = emptyList(),
            playbackQualityMode = PlaybackQualityMode.AUTO
        )

        assertFalse(
            shouldUseAdaptiveDashPlayback(
                adaptiveDashSource = source,
                audioUrl = "https://example.com/audio.m4s"
            )
        )
    }

    @Test
    fun `local dash manifest file name is stable and uses mpd suffix`() {
        val fileName = resolveLocalDashManifestFileName("<MPD>same</MPD>")

        assertEquals(fileName, resolveLocalDashManifestFileName("<MPD>same</MPD>"))
        assertTrue(fileName.endsWith(".mpd"))
    }
}
