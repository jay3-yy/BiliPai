package com.android.purebilibili.feature.video.usecase

import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.Durl
import com.android.purebilibili.data.model.response.Dolby
import com.android.purebilibili.data.model.response.Flac
import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.repository.playableVideoQualityIds
import com.android.purebilibili.core.util.resolvePlaybackDefaultQualityId
import com.android.purebilibili.feature.video.playback.audio.AUDIO_QUALITY_AUTO
import com.android.purebilibili.feature.video.playback.audio.AUDIO_QUALITY_DOLBY
import com.android.purebilibili.feature.video.playback.audio.AUDIO_QUALITY_HI_RES
import com.android.purebilibili.feature.video.playback.audio.AudioFallbackReason
import com.android.purebilibili.core.player.policy.PlaybackQualityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

class VideoPlaybackUseCaseQualitySwitchTest {

    @Test
    fun `initial selection locks the available quality at or below the preference`() {
        val result = VideoPlaybackUseCase(videoTrackFilter = { it }).resolvePlaybackSelection(
            playUrlData = PlayUrlData(dash = Dash(video = listOf(
                DashVideo(id = 125, baseUrl = "https://example.com/hdr", codecs = "hev1"),
                DashVideo(id = 120, baseUrl = "https://example.com/4k", codecs = "hev1"),
                DashVideo(id = 80, baseUrl = "https://example.com/1080", codecs = "avc1"),
            ))),
            targetQuality = 120,
            isHevcSupported = true,
            isAv1Supported = false,
            isDolbyAudioSupported = false,
            isDolbyAudioSoftwareDecoded = false,
            lockSelectedQuality = true,
        )
        assertEquals(120, result?.actualQuality)
        assertEquals(PlaybackQualityMode.LOCKED(120), result?.adaptiveDashSource?.playbackQualityMode)
        assertEquals(listOf(120), result?.adaptiveDashSource?.videoTracks?.map { it.id })
    }

    @Test
    fun `ordinary video starts at preferred actual dash quality even when response says 480p`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })
        fun select(stored: Int, highest: Boolean, available: List<Int>): PlaybackSelectionResult? {
            val data = PlayUrlData(
                quality = 32,
                acceptQuality = listOf(120, 116, 80, 64, 32),
                dash = Dash(video = available.map { quality ->
                    DashVideo(id = quality, baseUrl = "https://example.com/$quality.m4s", codecs = "avc1")
                })
            )
            val preference = resolvePlaybackDefaultQualityId(
                storedQuality = stored, autoHighestEnabled = highest,
                isLoggedIn = true, isVip = false
            )
            val target = if (preference >= 127) useCase.resolveAutoHighestTargetQuality(
                data.playableVideoQualityIds(), isHdrSupported = false, isDolbyVisionSupported = false
            ) else preference
            return useCase.resolvePlaybackSelection(
                playUrlData = data,
                targetQuality = target,
                isHevcSupported = true,
                isAv1Supported = false,
                isDolbyAudioSupported = false,
                isDolbyAudioSoftwareDecoded = false,
                allowPremiumApiOnlyQualities = false,
                lockSelectedQuality = true
            )
        }
        listOf(
            Triple(80, false, 80),
            Triple(64, false, 64),
            Triple(32, true, 116),
            Triple(120, false, 116)
        ).forEach { (stored, highest, expected) ->
            val selection = select(stored, highest, listOf(116, 80, 64, 32))
            assertEquals(expected, selection?.actualQuality)
            assertEquals("https://example.com/$expected.m4s", selection?.videoUrl)
            assertEquals(listOf(expected), selection?.adaptiveDashSource?.videoTracks?.map { it.id })
            assertEquals(PlaybackQualityMode.LOCKED(expected), selection?.adaptiveDashSource?.playbackQualityMode)
        }
        // Account restrictions are represented by actual returned streams, not advertised tiers.
        assertEquals(32, select(120, false, listOf(32))?.actualQuality)
        assertEquals(32, select(80, true, listOf(32))?.actualQuality)
    }

    private val cachedVideos = listOf(
        DashVideo(id = 80, baseUrl = "https://example.com/1080.m4s"),
        DashVideo(id = 64, baseUrl = "https://example.com/720.m4s"),
        DashVideo(id = 32, baseUrl = "https://example.com/480.m4s")
    )

    private val cachedAudios = listOf(
        DashAudio(id = 30280, baseUrl = "https://example.com/audio.m4s")
    )

    @Test
    fun `changeQualityFromCache returns null when target quality not cached`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.changeQualityFromCache(
            qualityId = 120,
            cachedVideos = cachedVideos,
            cachedAudios = cachedAudios,
            currentPos = 0L
        )

        assertNull(result)
    }

    @Test
    fun `changeQualityFromCache returns exact match when target quality exists`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.changeQualityFromCache(
            qualityId = 64,
            cachedVideos = cachedVideos,
            cachedAudios = cachedAudios,
            currentPos = 0L,
            durationMs = 120_000L,
            playbackQualityMode = PlaybackQualityMode.LOCKED(64)
        )

        assertNotNull(result)
        assertEquals(64, result?.actualQuality)
        assertNotNull(result?.adaptiveDashSource)
        assertEquals(listOf(64), result?.adaptiveDashSource?.videoTracks?.map { it.id })
    }

    @Test
    fun `changeQualityFromCache prefers supported codec when same quality has multiple tracks`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.changeQualityFromCache(
            qualityId = 80,
            cachedVideos = listOf(
                DashVideo(id = 80, baseUrl = "https://example.com/1080-hevc.m4s", codecs = "hev1"),
                DashVideo(id = 80, baseUrl = "https://example.com/1080-avc.m4s", codecs = "avc1"),
                DashVideo(id = 64, baseUrl = "https://example.com/720-avc.m4s", codecs = "avc1")
            ),
            cachedAudios = cachedAudios,
            currentPos = 0L,
            videoCodecPreference = "hev1",
            videoSecondCodecPreference = "avc1",
            isHevcSupported = false,
            isAv1Supported = false
        )

        assertNotNull(result)
        assertEquals(80, result?.actualQuality)
        assertEquals("https://example.com/1080-avc.m4s", result?.videoUrl)
    }

    @Test
    fun `mergeQualityOptions keeps api high tiers when dash list misses them`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.mergeQualityOptions(
            apiQualities = listOf(120, 116, 80, 64, 32, 16),
            dashVideoIds = listOf(80, 64, 32, 16)
        )

        assertEquals(listOf(120, 116, 80, 64, 32, 16), result.mergedQualityIds)
        assertEquals(listOf(120, 116), result.apiOnlyHighQualities)
    }

    @Test
    fun `mergeQualityOptions keeps api advertised 1080P when dash list is capped at 720P`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.mergeQualityOptions(
            apiQualities = listOf(80, 64, 32, 16),
            dashVideoIds = listOf(64, 32, 16)
        )

        assertEquals(listOf(80, 64, 32, 16), result.mergedQualityIds)
        assertEquals(listOf(80), result.apiOnlyHighQualities)
    }

    @Test
    fun `mergeQualityOptions uses dash list when api list is empty`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.mergeQualityOptions(
            apiQualities = emptyList(),
            dashVideoIds = listOf(80, 64)
        )

        assertEquals(listOf(80, 64), result.mergedQualityIds)
        assertTrue(result.apiOnlyHighQualities.isEmpty())
    }

    @Test
    fun `buildQualitySelectionState keeps api advertised 1080P label when dash is capped at 720P`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.buildQualitySelectionState(
            apiQualities = listOf(80, 64, 32, 16),
            dashVideoIds = listOf(64, 32, 16)
        )

        assertEquals(listOf(80, 64, 32, 16), result.qualityIds)
        assertEquals(listOf(64, 32, 16), result.switchableQualityIds)
        assertEquals(listOf("1080P", "720P", "480P", "360P"), result.qualityLabels)
    }

    @Test
    fun `buildQualitySelectionState keeps vip tiers visible after api re-fetch`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.buildQualitySelectionState(
            apiQualities = listOf(120, 116, 80, 64, 32),
            dashVideoIds = listOf(120, 80, 64, 32)
        )

        assertEquals(listOf(120, 116, 80, 64, 32), result.qualityIds)
        assertEquals(listOf(120, 80, 64, 32), result.switchableQualityIds)
        assertEquals(listOf("4K", "1080P60", "1080P", "720P", "480P"), result.qualityLabels)
    }

    @Test
    fun `buildQualitySelectionState keeps premium labels visible but disables missing dash tiers`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.buildQualitySelectionState(
            apiQualities = listOf(120, 116, 80, 64, 32),
            dashVideoIds = listOf(80, 64, 32)
        )

        assertEquals(listOf(120, 116, 80, 64, 32), result.qualityIds)
        assertEquals(listOf(80, 64, 32), result.switchableQualityIds)
        assertEquals(listOf("4K", "1080P60", "1080P", "720P", "480P"), result.qualityLabels)
    }

    @Test
    fun `buildQualitySelectionState hides premium api only tiers during app cooldown`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.buildQualitySelectionState(
            apiQualities = listOf(120, 116, 112, 80, 64, 32),
            dashVideoIds = listOf(80, 64, 32),
            allowPremiumApiOnlyQualities = false
        )

        assertEquals(listOf(80, 64, 32), result.qualityIds)
        assertEquals(listOf(80, 64, 32), result.switchableQualityIds)
        assertEquals(listOf("1080P", "720P", "480P"), result.qualityLabels)
    }

    @Test
    fun `buildQualitySelectionState does not invent low tiers missing from api list`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.buildQualitySelectionState(
            apiQualities = listOf(80, 64),
            dashVideoIds = listOf(80, 64)
        )

        assertEquals(listOf(80, 64), result.qualityIds)
        assertEquals(listOf(80, 64), result.switchableQualityIds)
        assertEquals(listOf("1080P", "720P"), result.qualityLabels)
    }

    @Test
    fun `mergeQualityOptions keeps dash-only lower tiers when api only reports current max quality`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.mergeQualityOptions(
            apiQualities = listOf(64),
            dashVideoIds = listOf(64, 32, 16)
        )

        assertEquals(listOf(64, 32, 16), result.mergedQualityIds)
        assertEquals(emptyList<Int>(), result.apiOnlyHighQualities)
    }

    @Test
    fun `buildQualitySelectionState keeps lower dash tiers under api capped guest payload`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.buildQualitySelectionState(
            apiQualities = listOf(64),
            dashVideoIds = listOf(64, 32, 16)
        )

        assertEquals(listOf(64, 32, 16), result.qualityIds)
        assertEquals(listOf(64, 32, 16), result.switchableQualityIds)
        assertEquals(listOf("720P", "480P", "360P"), result.qualityLabels)
    }

    @Test
    fun `buildQualitySelectionState keeps dash lower tiers alongside api-only login tier`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.buildQualitySelectionState(
            apiQualities = listOf(80, 64),
            dashVideoIds = listOf(64, 32, 16)
        )

        assertEquals(listOf(80, 64, 32, 16), result.qualityIds)
        assertEquals(listOf(64, 32, 16), result.switchableQualityIds)
        assertEquals(listOf("1080P", "720P", "480P", "360P"), result.qualityLabels)
    }

    @Test
    fun `auto highest uses actual streams instead of advertised entitlement tiers`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })
        // The advertised list may mention 4K while this account only received 1080P.
        assertEquals(80, useCase.resolveAutoHighestTargetQuality(
            playableQualities = listOf(80, 64, 32),
            isHdrSupported = true,
            isDolbyVisionSupported = true
        ))
        // An exact 1080P stream is usable for guests too (try_look).
        assertEquals(80, useCase.resolveAutoHighestTargetQuality(
            playableQualities = listOf(80, 64),
            isHdrSupported = false,
            isDolbyVisionSupported = false
        ))
    }

    @Test
    fun `auto highest chooses HDR Vivid when present and falls back for device capability`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })
        assertEquals(129, useCase.resolveAutoHighestTargetQuality(
            playableQualities = listOf(129, 127, 126, 125, 120, 80),
            isHdrSupported = true,
            isDolbyVisionSupported = true
        ))
        assertEquals(120, useCase.resolveAutoHighestTargetQuality(
            playableQualities = listOf(129, 126, 125, 120, 80),
            isHdrSupported = false,
            isDolbyVisionSupported = false
        ))
    }

    @Test
    fun `buildPlaybackSelectionSummary describes final selection context`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.buildPlaybackSelectionSummary(
            bvid = "BV1TEST12345",
            cid = 9527L,
            defaultQuality = 80,
            targetQuality = 80,
            returnedQuality = 64,
            selectedDashQuality = 80,
            mergedQualityIds = listOf(80, 64, 32, 16),
            isLoggedIn = true,
            isVip = false
        )

        assertEquals(
            "PLAY_DIAG playback_selection bvid=BV1TEST12345 cid=9527 default=80 target=80 returned=64 selectedDash=80 merged=[80, 64, 32, 16] isLoggedIn=true isVip=false",
            result
        )
    }

    @Test
    fun `buildPlaybackSelectionSummary includes selected stream bitrate when available`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.buildPlaybackSelectionSummary(
            bvid = "BV1TEST12345",
            cid = 9527L,
            defaultQuality = 80,
            targetQuality = 80,
            returnedQuality = 80,
            selectedDashQuality = 80,
            selectedDashCodec = "hev1.1.6.L120.90",
            selectedDashBandwidth = 900_000,
            selectedAudioBandwidth = 64_000,
            mergedQualityIds = listOf(80, 64, 32),
            isLoggedIn = true,
            isVip = false
        )

        assertEquals(
            "PLAY_DIAG playback_selection bvid=BV1TEST12345 cid=9527 default=80 target=80 returned=80 selectedDash=80 selectedCodec=hev1.1.6.L120.90 selectedBandwidth=900000 selectedAudioBandwidth=64000 merged=[80, 64, 32] isLoggedIn=true isVip=false",
            result
        )
    }

    @Test
    fun `resolvePlaybackSelection chooses requested dash quality and keeps merged labels`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.resolvePlaybackSelection(
            playUrlData = PlayUrlData(
                quality = 64,
                acceptQuality = listOf(80, 64, 32, 16),
                dash = Dash(
                    video = listOf(
                        DashVideo(id = 80, baseUrl = "https://example.com/1080-hevc.m4s", codecs = "hev1"),
                        DashVideo(id = 64, baseUrl = "https://example.com/720-avc.m4s", codecs = "avc1")
                    ),
                    audio = listOf(
                        DashAudio(id = 30280, baseUrl = "https://example.com/audio-192.m4s", bandwidth = 192000)
                    )
                )
            ),
            targetQuality = 80,
            audioQualityPreference = 30280,
            videoCodecPreference = "hev1",
            videoSecondCodecPreference = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertNotNull(result)
        assertEquals("https://example.com/1080-hevc.m4s", result?.videoUrl)
        assertEquals("https://example.com/audio-192.m4s", result?.audioUrl)
        assertEquals(80, result?.actualQuality)
        assertEquals(listOf(80, 64, 32, 16), result?.qualityIds)
        assertEquals(listOf(80, 64), result?.switchableQualityIds)
        assertEquals(listOf("1080P", "720P", "480P", "360P"), result?.qualityLabels)
    }

    @Test
    fun `resolvePlaybackSelection uses standard dash audio for hi res preference at high speed`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.resolvePlaybackSelection(
            playUrlData = PlayUrlData(
                quality = 80,
                acceptQuality = listOf(80, 64),
                dash = Dash(
                    video = listOf(
                        DashVideo(id = 80, baseUrl = "https://example.com/1080-hevc.m4s", codecs = "hev1")
                    ),
                    audio = listOf(
                        DashAudio(id = 30280, baseUrl = "https://example.com/audio-192.m4s", bandwidth = 192000),
                        DashAudio(id = 30216, baseUrl = "https://example.com/audio-64.m4s", bandwidth = 64000)
                    )
                )
            ),
            targetQuality = 80,
            audioQualityPreference = 30251,
            playbackSpeed = 2.0f,
            videoCodecPreference = "hev1",
            videoSecondCodecPreference = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals("https://example.com/audio-192.m4s", result?.audioUrl)
        assertEquals(listOf(30280, 30216), result?.adaptiveDashSource?.audioTracks?.map { it.id })
        assertEquals(AUDIO_QUALITY_HI_RES, result?.requestedAudioQuality)
        assertEquals(30280, result?.selectedAudioQuality)
        assertEquals(AudioFallbackReason.SPEED_INCOMPATIBLE, result?.audioFallbackReason)
    }

    @Test
    fun `resolvePlaybackSelection selects flac and exposes real audio options`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })
        val hiResAudio = DashAudio(
            id = AUDIO_QUALITY_HI_RES,
            baseUrl = "https://example.com/audio-hires.m4s",
            bandwidth = 1_800_000,
            codecs = "fLaC"
        )

        val result = useCase.resolvePlaybackSelection(
            playUrlData = PlayUrlData(
                quality = 80,
                acceptQuality = listOf(80),
                dash = Dash(
                    video = listOf(
                        DashVideo(id = 80, baseUrl = "https://example.com/1080-hevc.m4s", codecs = "hev1")
                    ),
                    audio = listOf(
                        DashAudio(id = 30280, baseUrl = "https://example.com/audio-192.m4s", bandwidth = 192_000)
                    ),
                    flac = Flac(display = true, audio = hiResAudio)
                )
            ),
            targetQuality = 80,
            audioQualityPreference = AUDIO_QUALITY_HI_RES,
            videoCodecPreference = "hev1",
            videoSecondCodecPreference = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals("https://example.com/audio-hires.m4s", result?.audioUrl)
        assertEquals(listOf(AUDIO_QUALITY_HI_RES), result?.adaptiveDashSource?.audioTracks?.map { it.id })
        assertEquals(AUDIO_QUALITY_HI_RES, result?.selectedAudioQuality)
        assertEquals(
            listOf(AUDIO_QUALITY_AUTO, AUDIO_QUALITY_HI_RES, 30280),
            result?.availableAudioQualities?.map { it.preferenceId }
        )
        assertEquals(hiResAudio, result?.cachedDash?.flac?.audio)
    }

    @Test
    fun `resolvePlaybackSelection excludes dolby when device decoder is unavailable`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })
        val standardAudio = DashAudio(
            id = 30280,
            baseUrl = "https://example.com/audio-aac.m4s",
            bandwidth = 192_000,
            codecs = "mp4a.40.2"
        )
        val dolbyAudio = DashAudio(
            id = AUDIO_QUALITY_DOLBY,
            baseUrl = "https://example.com/audio-dolby.m4s",
            bandwidth = 448_000,
            codecs = "ec-3"
        )

        val result = useCase.resolvePlaybackSelection(
            playUrlData = PlayUrlData(
                quality = 80,
                acceptQuality = listOf(80),
                dash = Dash(
                    video = listOf(
                        DashVideo(
                            id = 80,
                            baseUrl = "https://example.com/1080-hevc.m4s",
                            codecs = "hev1"
                        )
                    ),
                    audio = listOf(standardAudio),
                    dolby = Dolby(type = 1, audio = listOf(dolbyAudio))
                )
            ),
            targetQuality = 80,
            audioQualityPreference = AUDIO_QUALITY_DOLBY,
            videoCodecPreference = "hev1",
            videoSecondCodecPreference = "avc1",
            isHevcSupported = true,
            isAv1Supported = false,
            isDolbyAudioSupported = false
        )

        assertEquals(standardAudio.getValidUrl(), result?.audioUrl)
        assertEquals(listOf(30280), result?.adaptiveDashSource?.audioTracks?.map { it.id })
        assertEquals(30280, result?.selectedAudioQuality)
        assertEquals(AudioFallbackReason.REQUESTED_UNAVAILABLE, result?.audioFallbackReason)
        assertTrue(result?.availableAudioQualities?.none { it.preferenceId == AUDIO_QUALITY_DOLBY } == true)
    }

    @Test
    fun `raw audio selection controls backend source and manifest while preserving fallback preference`() {
        val standard64 = DashAudio(30216, baseUrl = "https://example.com/64.m4s", bandwidth = 64_000, codecs = "mp4a.40.2")
        val standard192 = DashAudio(30280, baseUrl = "https://example.com/192.m4s", bandwidth = 192_000, codecs = "mp4a.40.2")
        val alternateDolby = DashAudio(30255, baseUrl = "https://example.com/30255.m4s", bandwidth = 448_000, codecs = "ec-3")
        val data = PlayUrlData(
            quality = 80,
            acceptQuality = listOf(80),
            dash = Dash(
                video = listOf(DashVideo(80, baseUrl = "https://example.com/video.m4s", codecs = "avc1")),
                audio = listOf(standard64, standard192),
                dolby = Dolby(type = 1, audio = listOf(alternateDolby))
            )
        )
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })
        for ((request, speed, expectedTrack) in listOf(
            Triple(30216, 1f, standard64),
            Triple(30280, 1f, standard192),
            Triple(30232, 1f, standard192),
            Triple(30255, 1f, alternateDolby),
            Triple(30255, 2f, standard192)
        )) {
            val result = useCase.resolvePlaybackSelection(
                playUrlData = data,
                targetQuality = 80,
                audioQualityPreference = request,
                playbackSpeed = speed,
                videoCodecPreference = "avc1",
                videoSecondCodecPreference = "hev1",
                isHevcSupported = true,
                isAv1Supported = false,
                isDolbyAudioSupported = true
            )

            assertNotNull(result)
            assertEquals(request, result?.requestedAudioQuality)
            assertEquals(expectedTrack.id, result?.selectedAudioQuality)
            assertEquals(expectedTrack.getValidUrl(), result?.audioUrl)
            val manifestTracks = result?.adaptiveDashSource?.audioTracks.orEmpty()
            assertSame(expectedTrack, manifestTracks.first())
            assertEquals(
                if (request == 30255 && speed > 1.5f) listOf(30280, 30216) else listOf(expectedTrack.id),
                manifestTracks.map { it.id }
            )
            assertEquals(
                when {
                    speed > 1.5f -> AudioFallbackReason.SPEED_INCOMPATIBLE
                    request == 30232 -> AudioFallbackReason.REQUESTED_UNAVAILABLE
                    else -> null
                },
                result?.audioFallbackReason
            )
            assertEquals(
                expectedTrack.codecs,
                result?.availableAudioQualities?.single { it.preferenceId == expectedTrack.id }?.codecDescription
            )
        }
    }

    @Test
    fun `resolvePlaybackSelection falls back to durl when dash is missing`() {
        val useCase = VideoPlaybackUseCase(videoTrackFilter = { it })

        val result = useCase.resolvePlaybackSelection(
            playUrlData = PlayUrlData(
                quality = 32,
                acceptQuality = listOf(32, 16),
                durl = listOf(
                    Durl(url = "https://example.com/480.mp4")
                )
            ),
            targetQuality = 32,
            audioQualityPreference = -1,
            videoCodecPreference = "hev1",
            videoSecondCodecPreference = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertNotNull(result)
        assertEquals("https://example.com/480.mp4", result?.videoUrl)
        assertEquals(null, result?.audioUrl)
        assertEquals(32, result?.actualQuality)
        assertEquals(listOf(32, 16), result?.qualityIds)
        assertEquals(emptyList<Int>(), result?.switchableQualityIds)
        assertEquals(listOf("480P", "360P"), result?.qualityLabels)
    }
}
