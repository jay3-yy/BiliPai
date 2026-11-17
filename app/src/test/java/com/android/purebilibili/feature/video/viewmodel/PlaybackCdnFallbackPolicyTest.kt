package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.feature.plugin.CdnCandidateHealth
import com.android.purebilibili.plugin.sdk.CdnHealthEvent
import com.android.purebilibili.plugin.sdk.PlaybackCdnCandidate
import com.android.purebilibili.plugin.sdk.PlaybackCdnCandidateSource
import com.android.purebilibili.feature.plugin.recordCdnHealthEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackCdnFallbackPolicyTest {
    @Test
    fun `candidate discovery restores signed track urls instead of a synthesized host`() {
        val base = "https://a.mcdn.bilivideo.cn/upgcxcode/v.m4s?sign=a%2Fb"
        val backup = "https://b.mcdn.bilivideo.cn/upgcxcode/v.m4s?sign=c%2Fd"
        val video = DashVideo(id = 80, baseUrl = base, backupUrl = listOf(backup), codecs = "hev1")
        val audio = DashAudio(id = 30280, baseUrl = base, backupUrl = listOf(backup))

        assertEquals(listOf(base, backup), buildPlaybackVideoUrlCandidates(video.getValidUrl(), 80, listOf(video)))
        assertEquals(listOf(base, backup), buildPlaybackAudioUrlCandidates(audio.getValidUrl(), listOf(audio)))
    }

    @Test
    fun `fallback keeps original signed urls when every returned route is risky`() {
        val first = PlaybackCdnCandidate(
            videoUrl = "https://a.mcdn.bilivideo.cn/v?sign=a%2Fb",
            audioUrl = "https://a.mcdn.bilivideo.cn/a?sign=c%2Fd",
            source = PlaybackCdnCandidateSource.ORIGINAL,
        )
        val second = first.copy(
            videoUrl = "https://b.mcdn.bilivideo.cn/v?sign=e%2Ff",
            audioUrl = "https://b.mcdn.bilivideo.cn/a?sign=g%2Fh",
        )
        val state = buildPlaybackCdnFallbackState(
            first.videoUrl, first.audioUrl, first.videoUrl, first.audioUrl,
            regionLabel = null,
            fallbackCandidates = listOf(first, second),
        )

        assertEquals(listOf(first, second), state.fallbackCandidates)
        assertEquals(second.videoUrl, state.fallbackVideoUrl)
        assertEquals(second.audioUrl, state.fallbackAudioUrl)
    }


    @Test
    fun `video candidates keep backups on selected HEVC track when AVC is listed first`() {
        val candidates = buildPlaybackVideoUrlCandidates(
            videoUrl = "https://hevc.example.com/1080-base.m4s",
            quality = 80,
            cachedDashVideos = listOf(
                DashVideo(
                    id = 80,
                    baseUrl = "https://avc.example.com/1080-base.m4s",
                    backupUrl = listOf("https://avc.example.com/1080-backup.m4s"),
                    codecs = "avc1.640028"
                ),
                DashVideo(
                    id = 80,
                    baseUrl = "https://hevc.example.com/1080-base.m4s",
                    backupUrl = listOf(
                        "https://hevc-backup-a.example.com/1080.m4s",
                        "https://hevc-backup-b.example.com/1080.m4s"
                    ),
                    codecs = "hvc1.1.6.L120.90"
                )
            )
        )

        assertEquals(
            listOf(
                "https://hevc.example.com/1080-base.m4s",
                "https://hevc-backup-a.example.com/1080.m4s",
                "https://hevc-backup-b.example.com/1080.m4s"
            ),
            candidates
        )
    }

    @Test
    fun `video candidates do not guess another codec when selected track is unknown`() {
        val candidates = buildPlaybackVideoUrlCandidates(
            videoUrl = "https://selected.example.com/video.m4s",
            quality = 80,
            cachedDashVideos = listOf(
                DashVideo(
                    id = 80,
                    baseUrl = "https://avc.example.com/1080-base.m4s",
                    backupUrl = listOf("https://avc.example.com/1080-backup.m4s"),
                    codecs = "avc1.640028"
                )
            )
        )

        assertEquals(listOf("https://selected.example.com/video.m4s"), candidates)
    }

    @Test
    fun `custom candidate falls back through each original pair once`() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://cn-hk-eq-01-03.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://cn-hk-eq-01-03.bilivideo.com/audio.m4s",
            originalVideoUrl = "https://upos-a.bilivideo.com/video.m4s",
            originalAudioUrl = "https://upos-a.bilivideo.com/audio.m4s",
            regionLabel = null,
            usesCustomRule = true,
            fallbackCandidates = listOf(
                PlaybackCdnCandidate(
                    "https://upos-a.bilivideo.com/video.m4s",
                    "https://upos-a.bilivideo.com/audio.m4s",
                    PlaybackCdnCandidateSource.ORIGINAL
                ),
                PlaybackCdnCandidate(
                    "https://upos-b.bilivideo.com/video.m4s",
                    "https://upos-b.bilivideo.com/audio.m4s",
                    PlaybackCdnCandidateSource.ORIGINAL
                )
            )
        )

        assertEquals("https://upos-a.bilivideo.com/video.m4s", state.fallbackVideoUrl)
        val afterFirstFallback = state.advanceFallback(
            currentFallbackVideoUrl = state.fallbackVideoUrl.orEmpty(),
            currentFallbackAudioUrl = state.fallbackAudioUrl
        )
        assertEquals("https://upos-b.bilivideo.com/video.m4s", afterFirstFallback.fallbackVideoUrl)
        assertFalse(afterFirstFallback.usesCustomRule)
        val exhausted = afterFirstFallback.advanceFallback(
            currentFallbackVideoUrl = afterFirstFallback.fallbackVideoUrl.orEmpty(),
            currentFallbackAudioUrl = afterFirstFallback.fallbackAudioUrl
        )
        assertTrue(exhausted.fallbackConsumed)
    }

    @Test
    fun `cdn rewrite keeps original playback pair as fallback`() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://cn-sh-ct-01-01.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://cn-sh-ct-01-01.bilivideo.com/audio.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            regionLabel = "上海"
        )

        assertTrue(state.usesCdnRewrite)
        assertEquals("https://upos-sz-mirrorali.bilivideo.com/video.m4s", state.fallbackVideoUrl)
        assertEquals("https://upos-sz-mirrorali.bilivideo.com/audio.m4s", state.fallbackAudioUrl)
        assertTrue(shouldFallbackFromCdnRewrite(state, playbackReady = false))
    }

    @Test
    fun `unchanged playback url does not arm cdn fallback`() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            regionLabel = null
        )

        assertFalse(state.usesCdnRewrite)
        assertFalse(shouldFallbackFromCdnRewrite(state, playbackReady = false))
    }

    @Test
    fun `audio candidates use backup urls from selected audio track`() {
        val candidates = buildPlaybackAudioUrlCandidates(
            audioUrl = "https://audio.example.com/30280-base.m4s",
            cachedDashAudios = listOf(
                DashAudio(
                    id = 30232,
                    baseUrl = "https://audio.example.com/30232-base.m4s",
                    backupUrl = listOf("https://audio.example.com/30232-backup.m4s")
                ),
                DashAudio(
                    id = 30280,
                    baseUrl = "https://audio.example.com/30280-base.m4s",
                    backupUrl = listOf("https://audio.example.com/30280-backup.m4s")
                )
            )
        )

        assertEquals(
            listOf(
                "https://audio.example.com/30280-base.m4s",
                "https://audio.example.com/30280-backup.m4s"
            ),
            candidates
        )
    }

    @Test
    fun `same video url can arm audio fallback when selected audio has backup`() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://audio.example.com/30280-base.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://audio.example.com/30280-base.m4s",
            regionLabel = null,
            audioFallbackUrl = "https://audio.example.com/30280-backup.m4s"
        )

        assertTrue(state.usesCdnRewrite)
        assertEquals("https://audio.example.com/30280-backup.m4s", state.fallbackAudioUrl)
        assertTrue(
            shouldFallbackFromCdnRewrite(
                state = state,
                playbackReady = true,
                expectedAudioTrack = true,
                hasSelectedAudioTrack = false,
                audioRendererError = false
            )
        )
    }

    @Test
    fun `cdn fallback can only fire once`() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://cn-sh-ct-01-01.bilivideo.com/video.m4s",
            selectedAudioUrl = null,
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = null,
            regionLabel = "上海"
        )

        val consumed = state.markFallbackConsumed()

        assertFalse(shouldFallbackFromCdnRewrite(consumed, playbackReady = false))
    }

    @Test
    fun `ready playback still falls back when rewritten audio track is missing`() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://cn-sh-ct-01-01.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://cn-sh-ct-01-01.bilivideo.com/audio.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            regionLabel = "上海"
        )

        assertTrue(
            shouldFallbackFromCdnRewrite(
                state = state,
                playbackReady = true,
                expectedAudioTrack = true,
                hasSelectedAudioTrack = false,
                audioRendererError = false
            )
        )
    }

    @Test
    fun `ready playback falls back immediately after audio renderer error`() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://cn-sh-ct-01-01.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://cn-sh-ct-01-01.bilivideo.com/audio.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            regionLabel = "上海"
        )

        assertTrue(
            shouldFallbackFromCdnRewrite(
                state = state,
                playbackReady = true,
                expectedAudioTrack = true,
                hasSelectedAudioTrack = true,
                audioRendererError = true
            )
        )
    }

    @Test
    fun `ready playback keeps rewritten source when audio track is selected`() {
        val state = buildPlaybackCdnFallbackState(
            selectedVideoUrl = "https://cn-sh-ct-01-01.bilivideo.com/video.m4s",
            selectedAudioUrl = "https://cn-sh-ct-01-01.bilivideo.com/audio.m4s",
            originalVideoUrl = "https://upos-sz-mirrorali.bilivideo.com/video.m4s",
            originalAudioUrl = "https://upos-sz-mirrorali.bilivideo.com/audio.m4s",
            regionLabel = "上海"
        )

        assertFalse(
            shouldFallbackFromCdnRewrite(
                state = state,
                playbackReady = true,
                expectedAudioTrack = true,
                hasSelectedAudioTrack = true,
                audioRendererError = false
            )
        )
    }

    @Test
    fun `cdn playback failures record negative health feedback`() {
        val initial = CdnCandidateHealth(host = "cn-sh-ct-01-01.bilivideo.com")
        val timeout = recordCdnHealthEvent(initial, CdnHealthEvent.FIRST_FRAME_TIMEOUT, nowMs = 1_000L)
        val audioMissing = recordCdnHealthEvent(timeout, CdnHealthEvent.AUDIO_TRACK_MISSING, nowMs = 2_000L)
        val playerError = recordCdnHealthEvent(audioMissing, CdnHealthEvent.PLAYER_ERROR, nowMs = 3_000L)

        assertEquals(1, playerError.firstFrameTimeoutCount)
        assertEquals(2, playerError.playbackErrorCount)
        assertEquals(3_000L, playerError.lastUpdatedAtMs)
    }

    @Test
    fun `ready playback records positive health feedback`() {
        val initial = CdnCandidateHealth(host = "cn-sh-ct-01-01.bilivideo.com")
        val ready = recordCdnHealthEvent(initial, CdnHealthEvent.PLAYBACK_READY, nowMs = 1_000L)

        assertEquals(1, ready.readyCount)
        assertEquals(0, ready.playbackErrorCount)
        assertEquals(1_000L, ready.lastUpdatedAtMs)
    }
}
