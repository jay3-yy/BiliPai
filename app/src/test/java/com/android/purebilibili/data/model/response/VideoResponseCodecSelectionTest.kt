package com.android.purebilibili.data.model.response

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VideoResponseCodecSelectionTest {

    @Test
    fun `getBestVideo falls back to second preferred codec when primary unavailable`() {
        val dash = Dash(
            video = listOf(
                DashVideo(id = 80, baseUrl = "https://example.com/avc.m4s", codecs = "avc1"),
                DashVideo(id = 80, baseUrl = "https://example.com/av1.m4s", codecs = "av01")
            )
        )

        val selected = dash.getBestVideo(
            targetQn = 80,
            preferCodec = "hev1",
            secondPreferCodec = "av01",
            isHevcSupported = true,
            isAv1Supported = true
        )

        assertNotNull(selected)
        assertEquals("av01", selected.codecs)
    }

    @Test
    fun `AV1 decoder fallback does not select failed HEVC again when AV1 is unavailable`() {
        val dash = Dash(
            video = listOf(
                DashVideo(id = 80, baseUrl = "https://example.com/hevc.m4s", codecs = "hev1"),
                DashVideo(id = 80, baseUrl = "https://example.com/avc.m4s", codecs = "avc1")
            )
        )

        val selected = dash.getBestVideo(
            targetQn = 80,
            preferCodec = "av01",
            secondPreferCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = true
        )

        assertNotNull(selected)
        assertEquals("avc1", selected.codecs)
    }

    @Test
    fun `getBestVideo prefers lower bitrate when quality and codec are equivalent`() {
        val dash = Dash(
            video = listOf(
                DashVideo(
                    id = 80,
                    baseUrl = "https://example.com/1080-hevc-high.m4s",
                    codecs = "hev1",
                    bandwidth = 7_000_000
                ),
                DashVideo(
                    id = 80,
                    baseUrl = "https://example.com/1080-hevc-efficient.m4s",
                    codecs = "hev1",
                    bandwidth = 900_000
                )
            )
        )

        val selected = dash.getBestVideo(
            targetQn = 80,
            preferCodec = "hev1",
            secondPreferCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertNotNull(selected)
        assertEquals("https://example.com/1080-hevc-efficient.m4s", selected.baseUrl)
        assertEquals(900_000, selected.bandwidth)
    }

    @Test
    fun `getValidUrl skips a bcache url on a nonstandard port when a regular backup exists`() {
        val video = DashVideo(
            id = 80,
            baseUrl = "https://upos-sz-mirrorali.bilivideo.com:8082/v1/resource/abc?os=bcache&sign=a",
            backupUrl = listOf(
                "https://cn-example-cm-01-01.bilivideo.com/upgcxcode/abc/video.m4s?os=bcache&sign=b"
            ),
            codecs = "avc1"
        )

        assertEquals(
            "https://cn-example-cm-01-01.bilivideo.com/upgcxcode/abc/video.m4s?os=bcache&sign=b",
            video.getValidUrl()
        )
    }

    @Test
    fun `getBestVideo keeps codec preference ahead of bitrate when codecs differ`() {
        val dash = Dash(
            video = listOf(
                DashVideo(
                    id = 80,
                    baseUrl = "https://example.com/1080-avc-low.m4s",
                    codecs = "avc1",
                    bandwidth = 800_000
                ),
                DashVideo(
                    id = 80,
                    baseUrl = "https://example.com/1080-hevc.m4s",
                    codecs = "hev1",
                    bandwidth = 1_200_000
                )
            )
        )

        val selected = dash.getBestVideo(
            targetQn = 80,
            preferCodec = "hev1",
            secondPreferCodec = "avc1",
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertNotNull(selected)
        assertEquals("hev1", selected.codecs)
    }
}
