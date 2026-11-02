package com.android.purebilibili.feature.video.playback.loader

import com.android.purebilibili.data.model.VideoLoadError
import com.android.purebilibili.data.model.response.ViewInfo
import com.android.purebilibili.feature.video.usecase.VideoLoadResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class PlaybackLoaderTest {

    @Test
    fun `load should wrap success with request config and cached position`() = runTest {
        val request = PlaybackRequest.create(
            bvid = "BV1loader",
            aid = 7788L,
            cid = 8899L,
            audioLang = "jp"
        )
        val config = PlaybackLoadConfig(
            defaultQuality = 80,
            audioQualityPreference = 30280,
            videoCodecPreference = "hev1",
            videoSecondCodecPreference = "avc1",
            playWhenReady = true,
            isAv1Supported = true,
            isHdrSupported = true,
            isDolbyVisionSupported = false
        )
        var seenRequest: PlaybackRequest? = null
        var seenConfig: PlaybackLoadConfig? = null
        val payload = VideoLoadResult.Success(
            info = ViewInfo(bvid = "BV1loader", cid = 8899L),
            playUrl = "https://example.com/video.mpd",
            audioUrl = "https://example.com/audio.m4s",
            related = emptyList(),
            quality = 80,
            qualityIds = listOf(80, 64),
            qualityLabels = listOf("1080P", "720P"),
            cachedDashVideos = emptyList(),
            cachedDashAudios = emptyList(),
            emoteMap = emptyMap(),
            isLoggedIn = true,
            isVip = false,
            isFollowing = false,
            isFavorited = false,
            isLiked = false,
            coinCount = 0
        )
        val loader = PlaybackLoader { capturedRequest, capturedConfig ->
            seenRequest = capturedRequest
            seenConfig = capturedConfig
            payload
        }

        val result = loader.load(
            request = request,
            cachedPositionMs = 12_345L,
            config = config
        )

        val success = assertIs<PlaybackLoadResult.Success>(result)
        assertSame(request, seenRequest)
        assertSame(config, seenConfig)
        assertEquals(12_345L, success.cachedPositionMs)
        assertSame(request, success.request)
        assertSame(payload, success.payload)

        // 设备 B 的新云端记录可以比设备 A 的旧缓存更前、更后或恰好为 0。
        listOf(60_000L, 5_000L, 0L, -1L).forEach { cloudMs ->
            val cloudPayload = payload.copy(serverPositionMs = cloudMs)
            val synced = assertIs<PlaybackLoadResult.Success>(PlaybackLoadResult.from(
                request, cachedPositionMs = 12_345L, result = cloudPayload,
            ))
            assertEquals(cloudMs.coerceAtLeast(0L), synced.cachedPositionMs)
            val replay = assertIs<PlaybackLoadResult.Success>(PlaybackLoadResult.from(
                request.copy(ignoreSavedProgress = true), 12_345L, cloudPayload,
            ))
            assertEquals(0L, replay.cachedPositionMs)
        }
    }

    @Test
    fun `load should wrap error with request context`() = runTest {
        val request = PlaybackRequest.create(bvid = "BV1error")
        val config = PlaybackLoadConfig(
            defaultQuality = 64,
            audioQualityPreference = -1,
            videoCodecPreference = "hev1",
            videoSecondCodecPreference = "avc1",
            playWhenReady = false,
            isAv1Supported = true,
            isHdrSupported = false,
            isDolbyVisionSupported = false
        )
        val loader = PlaybackLoader {
                _,
                _ ->
            VideoLoadResult.Error(
                error = VideoLoadError.Timeout,
                canRetry = false
            )
        }

        val result = loader.load(
            request = request,
            cachedPositionMs = 0L,
            config = config
        )

        val error = assertIs<PlaybackLoadResult.Error>(result)
        assertSame(request, error.request)
        assertEquals(0L, error.cachedPositionMs)
        assertEquals(VideoLoadError.Timeout, error.error)
        assertEquals(false, error.canRetry)
    }
}
