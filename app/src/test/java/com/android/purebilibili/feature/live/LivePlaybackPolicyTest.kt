package com.android.purebilibili.feature.live

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.android.purebilibili.data.model.response.CodecInfo
import com.android.purebilibili.data.model.response.FormatInfo
import com.android.purebilibili.data.model.response.LivePlayUrlData
import com.android.purebilibili.data.model.response.LiveDurl
import com.android.purebilibili.data.model.response.LiveQuality
import com.android.purebilibili.data.model.response.Playurl
import com.android.purebilibili.data.model.response.PlayurlInfo
import com.android.purebilibili.data.model.response.StreamInfo
import com.android.purebilibili.data.model.response.UrlInfo
import com.android.purebilibili.feature.video.ui.components.VideoAspectRatio
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertNull

class LivePlaybackPolicyTest {
    @Test
    fun `stream preference selects protocol format codec and falls back when missing`() {
        val candidates = listOf(
            LivePlaybackCandidate("http_stream", "flv", "avc", listOf("https://one"), 80, emptyList()),
            LivePlaybackCandidate("http_hls", "ts", "avc", listOf("https://two"), 80, emptyList()),
            LivePlaybackCandidate("http_hls", "fmp4", "avc", listOf("https://three"), 80, emptyList()),
            LivePlaybackCandidate("http_hls", "fmp4", "hevc", listOf("https://four"), 80, emptyList()),
        )
        assertEquals(3, resolveLivePreferredCandidateIndex(candidates, "http_hls|fmp4|hevc"))
        assertEquals(2, resolveLivePreferredCandidateIndex(candidates, "http_hls|fmp4|av1"))
        assertEquals(1, resolveLivePreferredCandidateIndex(candidates, "http_hls|flv|avc"))
        assertEquals(0, resolveLivePreferredCandidateIndex(candidates, "rtmp|flv|avc"))
        assertEquals(0, resolveLivePreferredCandidateIndex(candidates, null))
        assertEquals(0, resolveLivePreferredCandidateIndex(candidates, "invalid"))
    }


    @Test
    fun `default video quality preference maps to a live quality tier`() {
        assertEquals(400, resolveLiveDefaultQualityQn(116))
        assertEquals(400, resolveLiveDefaultQualityQn(80))
        assertEquals(250, resolveLiveDefaultQualityQn(64))
        assertEquals(150, resolveLiveDefaultQualityQn(32))
        assertEquals(80, resolveLiveDefaultQualityQn(16))
    }

    @Test
    fun `behind live window should seek to current live edge`() {
        assertEquals(
            LivePlaybackErrorRecovery.SEEK_TO_LIVE_EDGE,
            resolveLivePlaybackErrorRecovery(
                errorCode = PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
                httpResponseCode = null
            )
        )
    }

    @Test
    fun `expired live url should try next source`() {
        assertEquals(
            LivePlaybackErrorRecovery.TRY_NEXT_SOURCE,
            resolveLivePlaybackErrorRecovery(
                errorCode = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                httpResponseCode = 403
            )
        )
    }

    @Test
    fun `audio and video decoder failures should try next source`() {
        assertEquals(
            LivePlaybackErrorRecovery.TRY_NEXT_SOURCE,
            resolveLivePlaybackErrorRecovery(
                errorCode = PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
                httpResponseCode = null
            )
        )
        assertEquals(
            LivePlaybackErrorRecovery.TRY_NEXT_SOURCE,
            resolveLivePlaybackErrorRecovery(
                errorCode = PlaybackException.ERROR_CODE_DECODING_FAILED,
                httpResponseCode = null
            )
        )
    }

    @Test
    fun `unexpected ended state recovers only with foreground playback intent`() {
        assertTrue(
            shouldRecoverUnexpectedLiveEnd(
                playbackState = Player.STATE_ENDED,
                playWhenReady = true,
                isRoomLive = true,
                isMiniLiveMode = false
            )
        )
        assertFalse(
            shouldRecoverUnexpectedLiveEnd(
                playbackState = Player.STATE_ENDED,
                playWhenReady = false,
                isRoomLive = true,
                isMiniLiveMode = false
            )
        )
        assertFalse(
            shouldRecoverUnexpectedLiveEnd(
                playbackState = Player.STATE_ENDED,
                playWhenReady = true,
                isRoomLive = true,
                isMiniLiveMode = true
            )
        )
        assertFalse(
            shouldRecoverUnexpectedLiveEnd(
                playbackState = Player.STATE_ENDED,
                playWhenReady = true,
                isRoomLive = false,
                isMiniLiveMode = false
            )
        )
    }

    @Test
    fun `resolve playback should prefer hls and keep server ordered hosts`() {
        val resolved = resolveLivePlayback(
            data = playbackData(
                streams = listOf(
                    stream(
                        protocol = "http_stream",
                        format = "flv",
                        codec = codec(
                            codecName = "avc",
                            baseUrl = "/live.flv",
                            urls = listOf(
                                "https://flv-primary.example.com/live.flv?token=1",
                                "https://flv-backup.example.com/live.flv?token=2"
                            )
                        )
                    ),
                    stream(
                        protocol = "http_hls",
                        format = "ts",
                        codec = codec(
                            codecName = "avc",
                            baseUrl = "/live.m3u8",
                            urls = listOf(
                                "https://hls-primary.example.com/live.m3u8?token=1",
                                "https://hls-backup.example.com/live.m3u8?token=2"
                            )
                        )
                    )
                )
            ),
            requestedQn = 10000
        )

        assertNotNull(resolved)
        assertEquals("https://hls-primary.example.com/live.m3u8?token=1", resolved.primaryUrl)
        assertEquals(
            listOf(
                "https://hls-primary.example.com/live.m3u8?token=1",
                "https://hls-backup.example.com/live.m3u8?token=2"
            ),
            resolved.candidates.first().urls
        )
    }

    @Test
    fun `resolve playback should prefer stable hls fmp4 avc before hevc and flv`() {
        val resolved = resolveLivePlayback(
            data = playbackData(
                streams = listOf(
                    stream(
                        protocol = "http_hls",
                        format = "fmp4",
                        codec = codec(
                            codecName = "hevc",
                            baseUrl = "/hevc.m4s",
                            urls = listOf("https://hls.example.com/hevc.m4s?token=1")
                        )
                    ),
                    stream(
                        protocol = "http_hls",
                        format = "fmp4",
                        codec = codec(
                            codecName = "avc",
                            baseUrl = "/avc.m4s",
                            urls = listOf("https://hls.example.com/avc.m4s?token=1")
                        )
                    ),
                    stream(
                        protocol = "http_stream",
                        format = "flv",
                        codec = codec(
                            codecName = "avc",
                            baseUrl = "/live.flv",
                            urls = listOf("https://flv.example.com/live.flv?token=1")
                        )
                    )
                )
            ),
            requestedQn = 10000
        )

        assertNotNull(resolved)
        val first = resolved.candidates.first()
        assertEquals("http_hls", first.protocolName)
        assertEquals("fmp4", first.formatName)
        assertEquals("avc", first.codecName)
        assertEquals("https://hls.example.com/avc.m4s?token=1", resolved.primaryUrl)
    }

    @Test
    fun `resolve playback should use server current and accept qn before legacy descriptions`() {
        val resolved = resolveLivePlayback(
            data = playbackData(
                streams = listOf(
                    stream(
                        protocol = "http_hls",
                        format = "ts",
                        codec = codec(
                            codecName = "avc",
                            currentQn = 400,
                            acceptQn = listOf(10000, 400, 250),
                            baseUrl = "/live.m3u8",
                            urls = listOf("https://hls-primary.example.com/live.m3u8?token=1")
                        )
                    )
                ),
                gQnDesc = listOf(
                    LiveQuality(qn = 10000, desc = "Original"),
                    LiveQuality(qn = 400, desc = "BluRay"),
                    LiveQuality(qn = 250, desc = "Ultra HD")
                ),
                legacyQuality = listOf(
                    LiveQuality(qn = 150, desc = "HD"),
                    LiveQuality(qn = 80, desc = "Smooth")
                )
            ),
            requestedQn = 10000
        )

        assertNotNull(resolved)
        assertEquals(400, resolved.currentQuality)
        assertEquals(listOf(10000, 400, 250), resolved.qualityList.map { it.qn })
    }

    @Test
    fun `advance playback should move to next host before next candidate`() {
        val resolved = resolveLivePlayback(
            data = playbackData(
                streams = listOf(
                    stream(
                        protocol = "http_hls",
                        format = "ts",
                        codec = codec(
                            codecName = "avc",
                            baseUrl = "/hls.m3u8",
                            urls = listOf(
                                "https://hls-primary.example.com/hls.m3u8?token=1",
                                "https://hls-backup.example.com/hls.m3u8?token=2"
                            )
                        )
                    ),
                    stream(
                        protocol = "http_stream",
                        format = "flv",
                        codec = codec(
                            codecName = "avc",
                            baseUrl = "/stream.flv",
                            urls = listOf("https://flv-primary.example.com/stream.flv?token=1")
                        )
                    )
                )
            ),
            requestedQn = 10000
        )
        assertNotNull(resolved)

        val next = advanceLivePlayback(
            resolved = resolved,
            candidateIndex = 0,
            urlIndex = 0
        )

        assertIs<LiveAdvanceResult.NextSource>(next)
        assertEquals(0, next.candidateIndex)
        assertEquals(1, next.urlIndex)
        assertEquals("https://hls-backup.example.com/hls.m3u8?token=2", next.playUrl)
    }

    @Test
    fun `advance playback should request reload after all candidates are exhausted`() {
        val resolved = resolveLivePlayback(
            data = playbackData(
                streams = listOf(
                    stream(
                        protocol = "http_hls",
                        format = "ts",
                        codec = codec(
                            codecName = "avc",
                            baseUrl = "/hls.m3u8",
                            urls = listOf("https://hls-primary.example.com/hls.m3u8?token=1")
                        )
                    )
                )
            ),
            requestedQn = 10000
        )
        assertNotNull(resolved)

        val action = advanceLivePlayback(
            resolved = resolved,
            candidateIndex = 0,
            urlIndex = 0
        )

        assertIs<LiveAdvanceResult.ReloadCurrentQuality>(action)
        assertEquals(10000, action.qualityQn)
    }

    @Test
    fun `quality follows the playable selected codec rather than response order`() {
        val resolved = resolveLivePlayback(
            playbackData(
                streams = listOf(
                    stream(
                        "http_stream", "flv",
                        codec("avc", "/live.flv", listOf("https://flv.example.com/live.flv"), 10000, listOf(10000, 400))
                    ),
                    stream(
                        "http_hls", "ts",
                        codec("avc", "/live.m3u8", listOf("https://hls.example.com/live.m3u8"), 400, listOf(400, 250))
                    )
                ),
                gQnDesc = listOf(
                    LiveQuality(10000, "原画"),
                    LiveQuality(400, "蓝光"),
                    LiveQuality(250, "超清")
                )
            ),
            requestedQn = 10000
        )
        assertNotNull(resolved)
        assertEquals("https://hls.example.com/live.m3u8", resolved.primaryUrl)
        assertEquals(400, resolved.currentQuality)
        assertEquals(listOf(LiveQuality(400, "蓝光"), LiveQuality(250, "超清")), resolved.qualityList)

        val next = advanceLivePlayback(resolved, 0, 0)
        assertIs<LiveAdvanceResult.NextSource>(next)
        val nextCandidate = resolved.candidates[next.candidateIndex]
        assertEquals("https://flv.example.com/live.flv", next.playUrl)
        assertEquals(10000, nextCandidate.currentQuality)
        assertEquals(listOf(10000, 400), nextCandidate.qualityList.map { it.qn })
    }

    @Test
    fun `unplayable codec does not leak its quality into a usable source`() {
        val resolved = resolveLivePlayback(
            playbackData(
                streams = listOf(
                    stream("http_hls", "fmp4", codec("avc", "", emptyList(), 10000, listOf(10000))),
                    stream(
                        "http_hls", "ts",
                        codec("avc", "/live.m3u8", listOf("https://hls.example.com/live.m3u8"), 250, listOf(250, 150))
                    )
                )
            ),
            requestedQn = 10000
        )
        assertNotNull(resolved)
        assertEquals(250, resolved.currentQuality)
        assertEquals(listOf(250, 150), resolved.qualityList.map { it.qn })
    }

    @Test
    fun `legacy addresses skip blank entries and use the same failover path`() {
        val resolved = resolveLivePlayback(
            LivePlayUrlData(
                durl = listOf(LiveDurl(""), LiveDurl("https://a.example.com/live.flv"), LiveDurl("https://b.example.com/live.flv")),
                current_quality = 80,
                quality_description = listOf(LiveQuality(80, "流畅"))
            ),
            requestedQn = 400
        )
        assertNotNull(resolved)
        assertEquals("https://a.example.com/live.flv", resolved.primaryUrl)
        assertEquals(80, resolved.currentQuality)
        val next = advanceLivePlayback(resolved, 0, 0)
        assertIs<LiveAdvanceResult.NextSource>(next)
        assertEquals("https://b.example.com/live.flv", next.playUrl)
        assertIs<LiveAdvanceResult.ReloadCurrentQuality>(advanceLivePlayback(resolved, 0, 1))
        assertNull(resolveLivePlayback(LivePlayUrlData(durl = listOf(LiveDurl(" "))), 400))
    }

    private fun playbackData(
        streams: List<StreamInfo>,
        gQnDesc: List<LiveQuality> = emptyList(),
        legacyQuality: List<LiveQuality> = emptyList()
    ): LivePlayUrlData {
        return LivePlayUrlData(
            quality_description = legacyQuality,
            current_quality = 0,
            playurl_info = PlayurlInfo(
                playurl = Playurl(
                    stream = streams,
                    gQnDesc = gQnDesc
                )
            )
        )
    }

    private fun stream(
        protocol: String,
        format: String,
        codec: CodecInfo
    ): StreamInfo {
        return StreamInfo(
            protocolName = protocol,
            format = listOf(
                FormatInfo(
                    formatName = format,
                    codec = listOf(codec)
                )
            )
        )
    }

    private fun codec(
        codecName: String,
        baseUrl: String,
        urls: List<String>,
        currentQn: Int = 0,
        acceptQn: List<Int> = emptyList()
    ): CodecInfo {
        return CodecInfo(
            codecName = codecName,
            currentQn = currentQn,
            acceptQn = acceptQn,
            baseUrl = baseUrl,
            url_info = urls.map { raw ->
                val uri = URI(raw)
                UrlInfo(
                    host = "${uri.scheme}://${uri.authority}",
                    extra = uri.rawQuery?.let { "?$it" }.orEmpty()
                )
            }
        )
    }

    @Test
    fun `auto mini retains ready and buffering live playback but respects exit preferences`() {
        for (state in listOf(Player.STATE_READY, Player.STATE_BUFFERING)) {
            assertTrue(shouldAutoEnterLiveMiniPlayer(true, true, true, true, state, false))
            assertFalse(shouldAutoEnterLiveMiniPlayer(false, true, true, true, state, false))
            assertFalse(shouldAutoEnterLiveMiniPlayer(true, true, true, true, state, true))
            assertFalse(shouldAutoEnterLiveMiniPlayer(true, true, false, true, state, false))
            assertFalse(shouldAutoEnterLiveMiniPlayer(true, false, true, true, state, false))
            assertFalse(shouldAutoEnterLiveMiniPlayer(true, true, true, false, state, false))
        }
        assertFalse(shouldAutoEnterLiveMiniPlayer(true, true, true, true, Player.STATE_IDLE, false))
        assertFalse(shouldAutoEnterLiveMiniPlayer(true, true, true, true, Player.STATE_ENDED, false))
    }

}
