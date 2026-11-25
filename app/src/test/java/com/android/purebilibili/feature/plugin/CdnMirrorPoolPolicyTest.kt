package com.android.purebilibili.feature.plugin

import com.android.purebilibili.core.player.dash.AdaptiveDashPlaybackSource
import com.android.purebilibili.core.player.dash.buildLocalDashManifest
import com.android.purebilibili.core.player.policy.PlaybackQualityMode
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.SegmentBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CdnMirrorPoolPolicyTest {
    @Test
    fun `safe signed mode keeps original urls unchanged`() {
        val originals = listOf(
            "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/1/2/v.m4s?sig=a",
            "https://cn-hk-eq-01-01.bilivideo.com/upgcxcode/1/2/v.m4s?sig=b",
        )
        assertEquals(originals, buildParallelMirrorUrls(originals, CdnParallelMirrorMode.SAFE_SIGNED))
    }

    @Test
    fun `mainland mode synthesizes all mainland upos mirrors from portable donor`() {
        val donor = "https://upos-sz-mirrorali.bilivideo.com:443/upgcxcode/1/2/v.m4s?deadline=1&sig=a%2Fb"
        val urls = buildParallelMirrorUrls(listOf(donor), CdnParallelMirrorMode.MAINLAND_UPOS)
        val hosts = urls.map(::hostFromCdnUrl)

        assertTrue(BTR_MAINLAND_UPOS_HOSTS.all { it in hosts })
        assertTrue(urls.all { it.contains("/upgcxcode/1/2/v.m4s?deadline=1&sig=a%2Fb") })
        assertTrue(urls.none { ":443/" in it })
    }

    @Test
    fun `akamai only overseas urls act as fallback donor in mainland mode`() {
        val akamai = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/1/2/v.m4s?sig=a"
        val urls = buildParallelMirrorUrls(listOf(akamai), CdnParallelMirrorMode.MAINLAND_UPOS)
        val hosts = urls.map(::hostFromCdnUrl)

        assertTrue(BTR_MAINLAND_UPOS_HOSTS.all { it in hosts })
        assertFalse(akamai in urls)
    }

    @Test
    fun `overseas mode synthesizes overseas mirrors and excludes mainland hosts`() {
        val donor = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/2/v.m4s?sig=a"
        val urls = buildParallelMirrorUrls(listOf(donor), CdnParallelMirrorMode.OVERSEAS_UPOS)
        val hosts = urls.map(::hostFromCdnUrl)

        assertTrue(BTR_OVERSEAS_UPOS_HOSTS.all { it in hosts })
        assertTrue(hosts.none { it in BTR_MAINLAND_UPOS_HOSTS })
    }

    @Test
    fun `relay and pcdn urls are never swapped to ordinary hosts and stay only as last resort`() {
        val relayOnly = listOf("https://xy123.mcdn.bilivideo.cn:8082/v1/resource/123.m4s?os=mcdn&sig=a")
        val mixed = relayOnly + "https://upos-sz-mirrorhw.bilivideo.com/upgcxcode/1/2/v.m4s?sig=b"

        assertEquals(relayOnly, buildParallelMirrorUrls(relayOnly, CdnParallelMirrorMode.MAINLAND_UPOS))
        val mixedUrls = buildParallelMirrorUrls(mixed, CdnParallelMirrorMode.MAINLAND_UPOS)
        assertFalse(relayOnly.first() in mixedUrls)
        assertTrue(mixedUrls.any { hostFromCdnUrl(it) == "upos-sz-mirrorali.bilivideo.com" })
    }

    @Test
    fun `custom host normalization rejects untrusted hosts and parses mixed separators`() {
        val (valid, invalid) = parseCustomCdnHosts(
            "https://upos-sz-mirrorali.bilivideo.com/path, upos-sz-mirrorhw.bilivideo.com; evil.example.org\n127.0.0.1"
        )
        assertEquals(
            listOf("upos-sz-mirrorali.bilivideo.com", "upos-sz-mirrorhw.bilivideo.com"),
            valid,
        )
        assertEquals(listOf("evil.example.org", "127.0.0.1"), invalid)
        assertNull(normalizeBiliCdnHost("bilivideo.com.attacker.com"))
    }

    @Test
    fun `ban list attributes non-4xx to node and 4xx using proven good sets`() {
        val bans = CdnSessionBanList(strikeLimit = 2)
        val goodHostBadAddr = "https://good.bilivideo.com/bad-addr.m4s?sig=1"
        val goodHostGoodAddr = "https://good.bilivideo.com/good-addr.m4s?sig=1"
        val badHostGoodAddr = "https://bad.bilivideo.com/good-addr.m4s?sig=1"
        val timeoutHost = "https://timeout.bilivideo.com/good-addr.m4s?sig=3"

        // Non-4xx bans the node immediately at strike limit.
        bans.recordFailure(timeoutHost, 0, null)
        assertTrue(bans.allows(timeoutHost))
        bans.recordFailure(timeoutHost, 0, 502)
        assertFalse(bans.allows(timeoutHost))
        assertTrue(bans.isNodeBanned("timeout.bilivideo.com"))

        // 412/429 pushback never permanently bans the node.
        bans.recordFailure(goodHostGoodAddr, 0, 412)
        bans.recordFailure(goodHostGoodAddr, 0, 429)
        assertTrue(bans.allows(goodHostGoodAddr))

        // 403 on badHost while goodAddress is proven good -> blames badHost node.
        bans.recordSuccess(goodHostGoodAddr)
        bans.recordFailure(badHostGoodAddr, 0, 403)
        bans.recordFailure(badHostGoodAddr, 0, 403)
        assertFalse(bans.allows(badHostGoodAddr))
        assertTrue(bans.isNodeBanned("bad.bilivideo.com"))

        // 403 on goodHost for unknown badAddr -> blames address, keeps goodHost allowed.
        bans.recordFailure(goodHostBadAddr, 0, 403)
        bans.recordFailure(goodHostBadAddr, 0, 403)
        assertFalse(bans.allows(goodHostBadAddr))
        assertTrue(bans.allows(goodHostGoodAddr))
        assertFalse(bans.isNodeBanned("good.bilivideo.com"))

        bans.reset()
        assertTrue(bans.allows(timeoutHost))
        assertTrue(bans.allows(badHostGoodAddr))
    }

    @Test
    fun `speed weighted dispatch prefers fast measured nodes with single exploration slots`() {
        val pool = listOf(
            "https://fast.bilivideo.com/upgcxcode/v.m4s",
            "https://medium.bilivideo.com/upgcxcode/v.m4s",
            "https://stale.bilivideo.com/upgcxcode/v.m4s",
            "https://untested.bilivideo.com/upgcxcode/v.m4s",
        )
        val now = 100_000L
        val speeds = mapOf(
            "fast.bilivideo.com" to 10_000_000L,
            "medium.bilivideo.com" to 2_000_000L,
            "stale.bilivideo.com" to 5_000_000L,
        )
        val measuredAt = mapOf(
            "fast.bilivideo.com" to 99_000L,
            "medium.bilivideo.com" to 99_000L,
            "stale.bilivideo.com" to 1_000L, // > 90s TTL -> treated as unmeasured/stale
        )
        val (selected, _) = selectParallelCandidates(pool, speeds, measuredAt, now, width = 3, exploreCursor = 0)
        assertEquals(3, selected.size)
        assertTrue("https://fast.bilivideo.com/upgcxcode/v.m4s" in selected)

        val assignments = assignPieceUrlsBySpeed(
            pieceCount = 8,
            candidates = listOf(
                "https://fast.bilivideo.com/upgcxcode/v.m4s",
                "https://medium.bilivideo.com/upgcxcode/v.m4s",
                "https://untested.bilivideo.com/upgcxcode/v.m4s",
            ),
            speedOf = { speeds[hostFromCdnUrl(it)] ?: 0L },
        )
        assertEquals(8, assignments.size)
        assertEquals(1, assignments.count { hostFromCdnUrl(it) == "untested.bilivideo.com" })
        assertTrue(
            assignments.count { hostFromCdnUrl(it) == "fast.bilivideo.com" } >
                assignments.count { hostFromCdnUrl(it) == "medium.bilivideo.com" }
        )
    }

    @Test
    fun `rebindAdaptiveDashSourceForCdnSelection rewrites BaseURL without dropping DASH manifest`() {
        val origVideo1080 = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/1/1080.m4s?sig=v1"
        val origVideo720 = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/1/720.m4s?sig=v2"
        val origAudio = "https://upos-hz-mirrorakam.akamaized.net/upgcxcode/1/audio.m4s?sig=a1"
        val newVideo1080 = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/1080.m4s?sig=v1"
        val newAudio = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/audio.m4s?sig=a1"

        val v1080 = DashVideo(
            id = 80,
            baseUrl = origVideo1080,
            bandwidth = 4_000_000,
            codecs = "avc1",
            segmentBase = SegmentBase("0-900", "901-1200"),
        )
        val v720 = DashVideo(
            id = 64,
            baseUrl = origVideo720,
            bandwidth = 2_000_000,
            codecs = "avc1",
            segmentBase = SegmentBase("0-800", "801-1100"),
        )
        val a = DashAudio(
            id = 30280,
            baseUrl = origAudio,
            bandwidth = 320_000,
            codecs = "mp4a.40.2",
            segmentBase = SegmentBase("0-500", "501-800"),
        )
        val manifest = buildLocalDashManifest(60_000L, 1_500L, listOf(v1080, v720), listOf(a))
        val source = AdaptiveDashPlaybackSource(manifest, listOf(v1080, v720), listOf(a), PlaybackQualityMode.AUTO)

        val rebound = rebindAdaptiveDashSourceForCdnSelection(
            source = source,
            originalVideoUrl = origVideo1080,
            originalAudioUrl = origAudio,
            selectedVideoUrl = newVideo1080,
            selectedAudioUrl = newAudio,
        )

        assertNotNull(rebound)
        assertTrue(rebound.manifest.contains("<BaseURL>$newVideo1080</BaseURL>"))
        assertTrue(rebound.manifest.contains("<BaseURL>https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/720.m4s?sig=v2</BaseURL>"))
        assertTrue(rebound.manifest.contains("<BaseURL>$newAudio</BaseURL>"))
        assertFalse(rebound.manifest.contains("akamaized.net"))
        assertEquals(newVideo1080, rebound.videoTracks.first().getValidUrl())
    }

    @Test
    fun `splitAdaptiveCdnPieces aligns startup boundary and covers range contiguously`() {
        val pieces = splitAdaptiveCdnPieces(
            start = 137L,
            length = 700_000L,
            targetConnections = 8,
            maxConnections = 16,
            startupBoundaryBytes = 524_288L,
        )
        assertEquals(137L, pieces.first().start)
        assertEquals(700_136L, pieces.last().endInclusive)
        assertEquals(700_000L, pieces.sumOf { it.length })
        assertTrue(pieces.any { it.start == 524_425L })
        pieces.zipWithNext().forEach { (a, b) -> assertEquals(a.endInclusive + 1L, b.start) }

        assertTrue(splitAdaptiveCdnPieces(Long.MAX_VALUE - 5, 10, 4).isEmpty())
    }

    @Test
    fun `shouldHedgePiece evaluates stall timeout, slow pace, and faster alternate`() {
        assertTrue(
            shouldHedgePiece(
                elapsedMs = 950L,
                sinceLastProgressMs = 900L,
                receivedBytes = 0,
                pieceLength = 131_072,
                bufferMs = 20_000L,
                sessionConnectionBps = 0L,
                alternateHostBps = 0L,
            )
        )
        assertTrue(
            shouldHedgePiece(
                elapsedMs = 1_500L,
                sinceLastProgressMs = 100L,
                receivedBytes = 15_000, // 10 KB/s < 60% of 100 KB/s
                pieceLength = 131_072,
                bufferMs = 5_000L,
                sessionConnectionBps = 100_000L,
                alternateHostBps = 0L,
            )
        )
        assertTrue(
            shouldHedgePiece(
                elapsedMs = 1_500L,
                sinceLastProgressMs = 100L,
                receivedBytes = 150_000, // 100 KB/s
                pieceLength = 524_288,
                bufferMs = 5_000L,
                sessionConnectionBps = 100_000L,
                alternateHostBps = 200_000L, // 2.0x > 1.5x
            )
        )
        assertFalse(
            shouldHedgePiece(
                elapsedMs = 1_500L,
                sinceLastProgressMs = 100L,
                receivedBytes = 15_000,
                pieceLength = 131_072,
                bufferMs = 16_000L, // comfortable buffer -> no pace hedge
                sessionConnectionBps = 100_000L,
                alternateHostBps = 500_000L,
            )
        )
    }
}
