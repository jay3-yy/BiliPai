package com.android.purebilibili.feature.video.usecase

import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.Durl
import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.feature.video.playback.policy.PlaybackQualityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlaybackUseCaseQualitySwitchTest {

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
        val useCase = VideoPlaybackUseCase()

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
        val useCase = VideoPlaybackUseCase()

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
        val useCase = VideoPlaybackUseCase()

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
        val useCase = VideoPlaybackUseCase()

        val result = useCase.mergeQualityOptions(
            apiQualities = listOf(120, 116, 80, 64, 32, 16),
            dashVideoIds = listOf(80, 64, 32, 16)
        )

        assertEquals(listOf(120, 116, 80, 64, 32, 16), result.mergedQualityIds)
        assertEquals(listOf(120, 116), result.apiOnlyHighQualities)
    }

    @Test
    fun `mergeQualityOptions keeps api advertised 1080P when dash list is capped at 720P`() {
        val useCase = VideoPlaybackUseCase()

        val result = useCase.mergeQualityOptions(
            apiQualities = listOf(80, 64, 32, 16),
            dashVideoIds = listOf(64, 32, 16)
        )

        assertEquals(listOf(80, 64, 32, 16), result.mergedQualityIds)
        assertEquals(listOf(80), result.apiOnlyHighQualities)
    }

    @Test
    fun `mergeQualityOptions uses dash list when api list is empty`() {
        val useCase = VideoPlaybackUseCase()

        val result = useCase.mergeQualityOptions(
            apiQualities = emptyList(),
            dashVideoIds = listOf(80, 64)
        )

        assertEquals(listOf(80, 64, 32, 16), result.mergedQualityIds)
        assertTrue(result.apiOnlyHighQualities.isEmpty())
    }

    @Test
    fun `buildQualitySelectionState keeps api advertised 1080P label when dash is capped at 720P`() {
        val useCase = VideoPlaybackUseCase()

        val result = useCase.buildQualitySelectionState(
            apiQualities = listOf(80, 64, 32, 16),
            dashVideoIds = listOf(64, 32, 16)
        )

        assertEquals(listOf(80, 64, 32, 16), result.qualityIds)
        assertEquals(listOf("1080P", "720P", "480P", "360P"), result.qualityLabels)
    }

    @Test
    fun `buildQualitySelectionState keeps vip tiers visible after api re-fetch`() {
        val useCase = VideoPlaybackUseCase()

        val result = useCase.buildQualitySelectionState(
            apiQualities = listOf(120, 116, 80, 64, 32),
            dashVideoIds = listOf(120, 80, 64, 32)
        )

        assertEquals(listOf(120, 116, 80, 64, 32, 16), result.qualityIds)
        assertEquals(listOf("4K", "1080P60", "1080P", "720P", "480P", "360P"), result.qualityLabels)
    }

    @Test
    fun `buildQualitySelectionState hides premium api only tiers during app cooldown`() {
        val useCase = VideoPlaybackUseCase()

        val result = useCase.buildQualitySelectionState(
            apiQualities = listOf(120, 116, 112, 80, 64, 32),
            dashVideoIds = listOf(80, 64, 32),
            allowPremiumApiOnlyQualities = false
        )

        assertEquals(listOf(80, 64, 32, 16), result.qualityIds)
        assertEquals(listOf("1080P", "720P", "480P", "360P"), result.qualityLabels)
    }

    @Test
    fun `resolveAutoHighestTargetQuality caps non vip users at 1080p`() {
        val useCase = VideoPlaybackUseCase()

        val result = useCase.resolveAutoHighestTargetQuality(
            acceptQualities = listOf(120, 116, 112, 80, 64, 32),
            isLoggedIn = true,
            isVip = false,
            isHdrSupported = true,
            isDolbyVisionSupported = true
        )

        assertEquals(80, result)
    }

    @Test
    fun `resolveAutoHighestTargetQuality caps guests at 720p`() {
        val useCase = VideoPlaybackUseCase()

        val result = useCase.resolveAutoHighestTargetQuality(
            acceptQualities = listOf(116, 80, 64, 32),
            isLoggedIn = false,
            isVip = false,
            isHdrSupported = true,
            isDolbyVisionSupported = true
        )

        assertEquals(64, result)
    }

    @Test
    fun `resolveAutoHighestTargetQuality keeps vip highest playable tier`() {
        val useCase = VideoPlaybackUseCase()

        val result = useCase.resolveAutoHighestTargetQuality(
            acceptQualities = listOf(120, 116, 112, 80, 64),
            isLoggedIn = true,
            isVip = true,
            isHdrSupported = true,
            isDolbyVisionSupported = true
        )

        assertEquals(120, result)
    }

    @Test
    fun `buildPlaybackSelectionSummary describes final selection context`() {
        val useCase = VideoPlaybackUseCase()

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
    fun `resolvePlaybackSelection chooses requested dash quality and keeps merged labels`() {
        val useCase = VideoPlaybackUseCase()

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
        assertEquals(listOf("1080P", "720P", "480P", "360P"), result?.qualityLabels)
    }

    @Test
    fun `resolvePlaybackSelection falls back to durl when dash is missing`() {
        val useCase = VideoPlaybackUseCase()

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
        assertEquals(listOf("480P", "360P"), result?.qualityLabels)
    }
}
