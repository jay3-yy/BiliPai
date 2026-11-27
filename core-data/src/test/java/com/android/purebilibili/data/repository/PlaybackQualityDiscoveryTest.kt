package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.FormatItem
import com.android.purebilibili.data.model.response.PlayUrlData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PlaybackQualityDiscoveryTest {
    private fun track(id: Int, codec: String = "avc1") = DashVideo(
        id = id, codecs = codec, baseUrl = "https://example.com/$id/$codec.m4s"
    )

    @Test
    fun `VIP badge follows the server flag even when the quality name suggests otherwise`() {
        val data = Json.decodeFromString<PlayUrlData>(
            """{
                "support_formats": [
                    {"quality": 120, "description": "4K", "need_vip": false},
                    {"quality": 125, "description": "HDR"},
                    {"quality": 112, "description": "1080P+", "need_vip": true}
                ]
            }""".trimIndent(),
        )

        assertEquals(setOf(112), data.vipVideoQualityIds())
    }

    @Test
    fun `supplement requests highest missing lower tier once and keeps first response metadata`() = runTest {
        val audio = DashAudio(id = 30280, baseUrl = "https://example.com/audio.m4s")
        val initial = PlayUrlData(
            quality = 129,
            timelength = 90_000,
            lastPlayTime = 5_000,
            curLanguage = "zh",
            acceptQuality = listOf(129, 120, 80, 64),
            supportFormats = listOf(129, 120, 80, 64).map { FormatItem(quality = it) },
            dash = Dash(video = listOf(track(129, "hev1")), audio = listOf(audio))
        )
        val requests = mutableListOf<Int>()
        val result = supplementPlaybackQualities(initial) { quality ->
            requests += quality
            PlayUrlData(
                quality = 120,
                curLanguage = "en",
                dash = Dash(video = listOf(track(120), track(80), track(80, "hev1"), track(80)))
            )
        }
        assertEquals(listOf(120), requests)
        assertEquals(listOf(129, 120, 80), result.playableVideoQualityIds())
        assertEquals(2, result.dash!!.video.count { it.id == 80 })
        assertEquals(initial, result.copy(dash = initial.dash))
        assertEquals(listOf(audio), result.dash.audio)
    }

    @Test
    fun `advertised premium tiers above the account maximum never trigger supplement`() = runTest {
        val data = PlayUrlData(
            acceptQuality = listOf(120, 116, 80, 64),
            supportFormats = listOf(120, 116, 80, 64).map { FormatItem(quality = it) },
            dash = Dash(video = listOf(track(80), track(64)))
        )
        val result = supplementPlaybackQualities(data) { error("must not request locked tiers") }
        assertSame(data, result)
        assertEquals(listOf(80, 64), result.playableVideoQualityIds())
    }

    @Test
    fun `failed or empty supplement preserves initial playback and cancellation propagates`() = runTest {
        val data = PlayUrlData(
            supportFormats = listOf(FormatItem(quality = 80)),
            dash = Dash(video = listOf(track(120)))
        )
        assertSame(data, supplementPlaybackQualities(data) { throw IllegalStateException("offline") })
        assertSame(data, supplementPlaybackQualities(data) { null })
        assertEquals(data, supplementPlaybackQualities(data) { PlayUrlData() })
        assertFailsWith<CancellationException> {
            supplementPlaybackQualities(data) { throw CancellationException("account changed") }
        }
    }

    @Test
    fun `hung supplement returns the first playable response within its deadline`() = runTest {
        val initial = PlayUrlData(
            supportFormats = listOf(FormatItem(quality = 80)),
            dash = Dash(video = listOf(track(120))),
        )
        var fetchCancelled = false
        val result = supplementPlaybackQualities(initial, timeoutMs = 100L) {
            try { awaitCancellation() } finally { fetchCancelled = true }
        }
        assertSame(initial, result)
        assertTrue(fetchCancelled)
    }

    @Test
    fun `parent timeout cancels discovery rather than returning stale playback`() = runTest {
        val initial = PlayUrlData(
            supportFormats = listOf(FormatItem(quality = 80)),
            dash = Dash(video = listOf(track(120))),
        )
        assertFailsWith<CancellationException> {
            withTimeout(50L) {
                supplementPlaybackQualities(initial, timeoutMs = 100L) { awaitCancellation() }
            }
        }
    }

    @Test
    fun `empty URLs are unavailable while backup URLs remain playable`() {
        val data = PlayUrlData(
            acceptQuality = listOf(80, 64),
            supportFormats = listOf(FormatItem(quality = 120)),
            dash = Dash(video = listOf(
                DashVideo(id = 120),
                DashVideo(id = 80, backupUrl = listOf("https://example.com/backup.m4s")),
                track(64)
            ))
        )
        assertEquals(listOf(80, 64), data.playableVideoQualityIds())
        assertEquals(listOf(120, 80, 64), data.advertisedVideoQualityIds())
    }

    @Test
    fun `discovery caches a legitimate lower tier but manual premium requests require exact track`() {
        assertTrue(shouldCachePlayUrlResult(
            source = PlayUrlSource.DASH,
            audioLang = null,
            requestedQuality = 129,
            returnedQuality = 80,
            dashVideoIds = listOf(80, 64),
            requestKind = PlayUrlRequestKind.INITIAL
        ))
        assertEquals(false, shouldCachePlayUrlResult(
            source = PlayUrlSource.DASH,
            audioLang = null,
            requestedQuality = 129,
            returnedQuality = 80,
            dashVideoIds = listOf(80, 64)
        ))
    }
}
