package com.android.purebilibili.feature.plugin

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CdnOptimizationPolicyTest {
    @Test
    fun `paired ranking defers risky audio and mcdn query even on regular video hosts`() {
        val candidates = buildPlaybackCdnCandidates(
            videoUrls = listOf(
                "https://a.bilivideo.com/v?sign=a",
                "https://b.bilivideo.com/v?os=mcdn&sign=b",
                "https://c.bilivideo.com/v?sign=c%2Fd",
            ),
            audioUrls = listOf(
                "https://audio.mcdn.bilivideo.cn/a?sign=d",
                "https://b.bilivideo.com/a?sign=e",
                "https://c.bilivideo.com/a?sign=f%2Fg",
            ),
        )

        assertEquals(
            listOf(candidates[2], candidates[0], candidates[1]),
            sortSafeSignedPlaybackCandidates(candidates, emptyMap()),
        )
    }


    @Test
    fun `safe candidates preserve signed urls and defer risky hosts`() {
        val official = "https://upos-hz-mirrorakam.akamaized.net/video.m4s?deadline=1&sig=a"
        val risky = "https://d1--ov-gotcha01.bilivideo.com/video.m4s?deadline=1&sig=b"

        val candidates = buildAuthorizedCdnCandidates(listOf(risky, official, official))
        val sorted = sortAuthorizedCdnCandidates(candidates, emptyMap())

        assertEquals(listOf(official, risky), sorted.map { it.url })
        assertTrue(sorted.all { it.url.contains("deadline=") && it.url.contains("sig=") })
    }

    @Test
    fun `manual pin wins while it remains an authorized candidate`() {
        val first = "https://a.bilivideo.com/video.m4s?sig=a"
        val second = "https://b.bilivideo.com/video.m4s?sig=b"

        val sorted = sortAuthorizedCdnCandidates(
            candidates = buildAuthorizedCdnCandidates(listOf(first, second)),
            healthByHost = emptyMap(),
            pinnedHost = "b.bilivideo.com"
        )

        assertEquals(second, sorted.first().url)
    }

    @Test
    fun `nonstandard cdn port or mcdn query is risky even on a regular mirror host`() {
        assertTrue(
            isRiskyCdnUrl(
                "https://upos-sz-mirrorali.bilivideo.com:8082/v1/resource/upgcxcode/a.m4s?os=bcache&sign=a"
            )
        )
        assertTrue(
            isRiskyCdnUrl(
                "https://upos-sz-mirrorali.bilivideo.com:4483/upgcxcode/a.m4s?os=mcdn&sign=b"
            )
        )
        assertFalse(
            isRiskyCdnUrl(
                "https://cn-example-cm-01-01.bilivideo.com/upgcxcode/a.m4s?os=bcache&sign=c"
            )
        )
    }

    @Test
    fun `prefetch window requires safe buffer and grows in bounded steps`() {
        assertEquals(0, resolveCdnPrefetchSegmentCount(14_999L))
        assertEquals(3, resolveCdnPrefetchSegmentCount(15_000L))
        assertEquals(5, resolveCdnPrefetchSegmentCount(30_000L))
        assertEquals(8, resolveCdnPrefetchSegmentCount(60_000L))
    }

    @Test
    fun `cache key is shared by mirrors but isolates audio`() {
        val firstMirror = "https://a.bilivideo.com/upgcxcode/1/2/video.m4s?sig=a"
        val secondMirror = "https://b.bilivideo.com/upgcxcode/1/2/video.m4s?sig=b"

        val first = buildCdnTrackCacheKey("video", firstMirror)
        val second = buildCdnTrackCacheKey("video", secondMirror)

        assertEquals(first, second)
        assertNotEquals(first, buildCdnTrackCacheKey("audio", firstMirror))
    }

    @Test
    fun `parses finite media segments from sidx`() {
        val bytes = ByteBuffer.allocate(44).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(44)
            put("sidx".encodeToByteArray())
            putInt(0) // version and flags
            putInt(1) // reference id
            putInt(1_000) // timescale
            putInt(0) // earliest presentation time
            putInt(0) // first offset
            putShort(0)
            putShort(1)
            putInt(10) // media reference, 10 bytes
            putInt(2_000) // duration
            putInt(0x9000_0000.toInt())
        }.array()

        val index = parseCdnSidx(bytes, indexRangeStart = 100L)

        requireNotNull(index)
        assertEquals(1, index.segments.size)
        assertEquals(CdnByteRange(144L, 153L), index.segments.single().range)
        assertEquals(2_000_000L, index.segments.single().durationUs)
        assertFalse(index.segments.single().range.length <= 0L)
    }
}
