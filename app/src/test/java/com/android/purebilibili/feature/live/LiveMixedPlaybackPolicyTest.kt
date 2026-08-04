package com.android.purebilibili.feature.live

import com.android.purebilibili.data.model.response.CodecInfo
import com.android.purebilibili.data.model.response.FormatInfo
import com.android.purebilibili.data.model.response.LiveDurl
import com.android.purebilibili.data.model.response.LivePlayUrlData
import com.android.purebilibili.data.model.response.LiveQuality
import com.android.purebilibili.data.model.response.Playurl
import com.android.purebilibili.data.model.response.PlayurlInfo
import com.android.purebilibili.data.model.response.StreamInfo
import com.android.purebilibili.data.model.response.UrlInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveMixedPlaybackPolicyTest {

    private fun codec(
        name: String = "avc",
        baseUrl: String = "/live/avc.m3u8",
        qn: Int = 150,
        hosts: List<String> = listOf("https://cdn.example.com")
    ): CodecInfo = CodecInfo(
        codecName = name,
        currentQn = qn,
        acceptQn = listOf(10000, 150, 80),
        baseUrl = baseUrl,
        url_info = hosts.map { UrlInfo(host = it, extra = "?extra=1") }
    )

    private fun stream(
        protocol: String = "http_hls",
        format: String = "fmp4",
        codecs: List<CodecInfo>
    ): StreamInfo = StreamInfo(
        protocolName = protocol,
        format = listOf(FormatInfo(formatName = format, codec = codecs))
    )

    private fun data(
        streams: List<StreamInfo>,
        gQnDesc: List<LiveQuality>? = null,
        qualityDescription: List<LiveQuality>? = null,
        currentQuality: Int = 150,
        durls: List<LiveDurl>? = null
    ): LivePlayUrlData = LivePlayUrlData(
        durl = durls,
        quality_description = qualityDescription,
        current_quality = currentQuality,
        playurl_info = PlayurlInfo(
            playurl = Playurl(stream = streams, gQnDesc = gQnDesc)
        )
    )

    @Test
    fun `null web returns null`() {
        assertNull(LiveMixedPlaybackPolicy.merge(web = null, app = data(emptyList())))
    }

    @Test
    fun `null app returns web unchanged`() {
        val web = data(listOf(stream(codecs = listOf(codec()))))
        assertEquals(web, LiveMixedPlaybackPolicy.merge(web = web, app = null))
    }

    @Test
    fun `app only streams are appended when web has none`() {
        val app = data(listOf(stream(codecs = listOf(codec()))))
        val merged = LiveMixedPlaybackPolicy.merge(web = data(emptyList()), app = app)
        assertEquals(1, merged?.playurl_info?.playurl?.stream?.size)
    }

    @Test
    fun `duplicate codec from app is not added twice`() {
        val web = data(listOf(stream(codecs = listOf(codec(baseUrl = "/live/avc.m3u8")))))
        val app = data(listOf(stream(codecs = listOf(codec(baseUrl = "/live/avc.m3u8")))))
        val merged = LiveMixedPlaybackPolicy.merge(web = web, app = app)
        assertEquals(1, merged?.playurl_info?.playurl?.stream?.size)
    }

    @Test
    fun `distinct app codec is merged into candidates`() {
        val web = data(listOf(stream(codecs = listOf(codec(baseUrl = "/live/avc.m3u8")))))
        val app = data(listOf(stream(codecs = listOf(codec(baseUrl = "/live/h265.m3u8")))))
        val merged = LiveMixedPlaybackPolicy.merge(web = web, app = app)
        val streams = merged?.playurl_info?.playurl?.stream.orEmpty()
        assertEquals(2, streams.size)
        val baseUrls = streams.flatMap { it.format.orEmpty() }.flatMap { it.codec.orEmpty() }.map { it.baseUrl }
        assertTrue(baseUrls.contains("/live/avc.m3u8"))
        assertTrue(baseUrls.contains("/live/h265.m3u8"))
    }

    @Test
    fun `quality lists are merged and deduplicated by qn`() {
        val web = data(
            streams = emptyList(),
            gQnDesc = listOf(LiveQuality(qn = 10000, desc = "原画"), LiveQuality(qn = 150, desc = "高清"))
        )
        val app = data(
            streams = emptyList(),
            gQnDesc = listOf(LiveQuality(qn = 150, desc = "高清"), LiveQuality(qn = 80, desc = "流畅"))
        )
        val merged = LiveMixedPlaybackPolicy.merge(web = web, app = app)
        val qns = merged?.playurl_info?.playurl?.gQnDesc.orEmpty().map { it.qn }
        assertEquals(listOf(10000, 150, 80), qns)
    }

    @Test
    fun `top level quality description is merged and deduplicated`() {
        val web = data(
            streams = emptyList(),
            qualityDescription = listOf(LiveQuality(qn = 10000, desc = "原画"))
        )
        val app = data(
            streams = emptyList(),
            qualityDescription = listOf(LiveQuality(qn = 10000, desc = "原画"), LiveQuality(qn = 80, desc = "流畅"))
        )
        val merged = LiveMixedPlaybackPolicy.merge(web = web, app = app)
        val qns = merged?.quality_description.orEmpty().map { it.qn }
        assertEquals(listOf(10000, 80), qns)
    }

    @Test
    fun `current quality takes the higher of the two platforms`() {
        val web = data(streams = emptyList(), currentQuality = 150)
        val app = data(streams = emptyList(), currentQuality = 10000)
        assertEquals(10000, LiveMixedPlaybackPolicy.merge(web = web, app = app)?.current_quality)
    }

    @Test
    fun `legacy durls are merged and deduplicated`() {
        val web = data(streams = emptyList(), durls = listOf(LiveDurl(url = "https://a/flv", order = 0)))
        val app = data(
            streams = emptyList(),
            durls = listOf(
                LiveDurl(url = "https://a/flv", order = 0),
                LiveDurl(url = "https://b/flv", order = 1)
            )
        )
        val merged = LiveMixedPlaybackPolicy.merge(web = web, app = app)
        assertEquals(listOf("https://a/flv", "https://b/flv"), merged?.durl?.map { it.url })
    }
}