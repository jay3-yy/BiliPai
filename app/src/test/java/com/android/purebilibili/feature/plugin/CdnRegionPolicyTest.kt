package com.android.purebilibili.feature.plugin

import com.android.purebilibili.plugin.sdk.PlaybackCdnCandidateSource

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CdnRegionPolicyTest {

    private val catalog = mapOf(
        "上海" to listOf("cn-sh-ct-01-01.bilivideo.com"),
        "北京" to listOf("cn-bj-cc-03-14.bilivideo.com"),
        "南京" to listOf("cn-jsnj-fx-02-05.bilivideo.com"),
        "哈市" to listOf("cn-hljheb-cm-01-01.bilivideo.com"),
        "广州" to listOf("cn-gdgz-cm-01-02.bilivideo.com"),
        "成都" to listOf("cn-sccd-cm-03-02.bilivideo.com"),
        "海外" to listOf("d1--ov-gotcha01.bilivideo.com"),
        "深圳" to listOf("cn-gdsz-fx-01-01.bilivideo.com"),
        "福建" to listOf("cn-fjxm-ct-01-01.bilivideo.com"),
        "西安" to listOf("cn-sxxa-ct-01-01.bilivideo.com"),
        "武汉" to listOf("cn-hbwh-ct-01-01.bilivideo.com")
    )

    @Test
    fun `ip snapshot preserves public address and isp for internal cdn decisions`() {
        val snapshot = IpLocationSnapshot(
            addr = "36.40.120.145",
            country = "中国",
            province = "陕西",
            city = "渭南",
            isp = "电信"
        )

        assertEquals("36.40.120.145", snapshot.addr)
        assertEquals("电信", snapshot.isp)
    }

    @Test
    fun `selects direct city match before province`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "中国", province = "广东", city = "深圳"),
            catalog = catalog
        )

        assertEquals("深圳", selection.region)
        assertFalse(selection.fallbackUsed)
    }

    @Test
    fun `maps province aliases to nearest catalog region`() {
        assertEquals(
            "西安",
            selectCdnRegionForLocation(
                location = IpLocationSnapshot(country = "中国", province = "陕西", city = "渭南"),
                catalog = catalog
            ).region
        )
        assertEquals(
            "武汉",
            selectCdnRegionForLocation(
                location = IpLocationSnapshot(country = "中国", province = "湖北省", city = ""),
                catalog = catalog
            ).region
        )
        assertEquals(
            "成都",
            selectCdnRegionForLocation(
                location = IpLocationSnapshot(country = "中国", province = "重庆市", city = "重庆"),
                catalog = catalog
            ).region
        )
        assertEquals(
            "南京",
            selectCdnRegionForLocation(
                location = IpLocationSnapshot(country = "中国", province = "江苏", city = "苏州"),
                catalog = catalog
            ).region
        )
        assertEquals(
            "哈市",
            selectCdnRegionForLocation(
                location = IpLocationSnapshot(country = "中国", province = "黑龙江", city = "哈尔滨"),
                catalog = catalog
            ).region
        )
    }

    @Test
    fun `maps non mainland locations to overseas when available`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "荷兰", province = "北荷兰省", city = "阿姆斯特丹"),
            catalog = catalog
        )

        assertEquals("海外", selection.region)
        assertFalse(selection.fallbackUsed)
    }

    @Test
    fun `does not choose random fallback region when location has no catalog match`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "中国", province = "青海", city = "西宁"),
            catalog = catalog
        )

        assertEquals("", selection.region)
        assertEquals(emptyList<String>(), selection.hosts)
        assertTrue(selection.fallbackUsed)
    }

    @Test
    fun `sorts selected region hosts by isp carrier before rewriting`() {
        val selection = selectCdnRegionForLocation(
            location = IpLocationSnapshot(country = "中国", province = "上海", city = "上海", isp = "中国移动"),
            catalog = mapOf(
                "上海" to listOf(
                    "cn-sh-ct-01-01.bilivideo.com",
                    "cn-sh-cu-01-01.bilivideo.com",
                    "cn-sh-cm-01-01.bilivideo.com",
                    "cn-sh-fx-01-01.bilivideo.com"
                )
            )
        )

        assertEquals("cn-sh-cm-01-01.bilivideo.com", selection.hosts.first())
    }

    @Test
    fun `rewrites bilivideo hosts while preserving path query and originals`() {
        val result = rewriteCdnUrlCandidates(
            originalUrls = listOf(
                "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/2/video.m4s?deadline=1&gen=playurl",
                "https://backup.example.com/upgcxcode/1/2/video.m4s?deadline=1"
            ),
            preferredHosts = listOf("cn-sh-ct-01-01.bilivideo.com")
        )

        assertEquals(
            "https://cn-sh-ct-01-01.bilivideo.com/upgcxcode/1/2/video.m4s?deadline=1&gen=playurl",
            result.urls.first()
        )
        assertTrue(result.urls.contains("https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/2/video.m4s?deadline=1&gen=playurl"))
        assertTrue(result.urls.contains("https://backup.example.com/upgcxcode/1/2/video.m4s?deadline=1"))
        assertEquals(3, result.urls.size)
    }

    @Test
    fun `custom rules use first valid match without chaining and preserve pairs`() {
        val candidates = buildPlaybackCdnCandidates(
            videoUrls = listOf("https://upos-sz-mirrorali.bilivideo.com/video.m4s?token=1"),
            audioUrls = listOf("https://upos-sz-mirrorali.bilivideo.com/audio.m4s?token=1")
        )

        val rewritten = rewritePlaybackCdnCandidatesForCustomRules(
            candidates = candidates,
            rules = listOf(
                CdnCustomRule(
                    pattern = "upos-sz-mirrorali\\.bilivideo\\.com",
                    replacement = "cn-hk-eq-01-03.bilivideo.com"
                ),
                CdnCustomRule(
                    pattern = "cn-hk-eq-01-03",
                    replacement = "must-not-chain"
                )
            )
        )

        assertEquals(1, rewritten.size)
        assertEquals("https://cn-hk-eq-01-03.bilivideo.com/video.m4s?token=1", rewritten.single().videoUrl)
        assertEquals("https://cn-hk-eq-01-03.bilivideo.com/audio.m4s?token=1", rewritten.single().audioUrl)
        assertEquals(PlaybackCdnCandidateSource.CUSTOM, rewritten.single().source)
        assertEquals(
            PlaybackCdnCandidateSource.CUSTOM,
            sortPlaybackCdnCandidatesByHealth(
                candidates = rewritten + candidates,
                healthByHost = mapOf(
                    "upos-sz-mirrorali.bilivideo.com" to CdnCandidateHealth(
                        host = "upos-sz-mirrorali.bilivideo.com",
                        readyCount = 10,
                        manualProbeSpeedKbps = 10_000
                    )
                )
            ).first().source
        )
    }

    @Test
    fun `custom rules reject invalid regex and untrusted targets`() {
        val candidates = buildPlaybackCdnCandidates(
            videoUrls = listOf("https://upos-sz-mirrorali.bilivideo.com/video.m4s"),
            audioUrls = emptyList()
        )

        assertTrue(validateCdnCustomRules(listOf(CdnCustomRule(pattern = "[", replacement = "x"))).single().error != null)
        assertTrue(
            rewritePlaybackCdnCandidatesForCustomRules(
                candidates,
                listOf(CdnCustomRule("https://[^/]+", "http://evil.example.com"))
            ).isEmpty()
        )
    }

    @Test
    fun `disabled custom rule does not rewrite`() {
        val candidates = buildPlaybackCdnCandidates(
            videoUrls = listOf("https://upos-sz-mirrorali.bilivideo.com/video.m4s"),
            audioUrls = emptyList()
        )

        assertTrue(
            rewritePlaybackCdnCandidatesForCustomRules(
                candidates,
                listOf(CdnCustomRule("upos-sz", "cn-hk-eq-01-03", enabled = false))
            ).isEmpty()
        )
    }

    @Test
    fun `does not refresh cached location before ttl expires`() {
        assertFalse(
            shouldRefreshCdnIpLocation(
                enabled = true,
                nowMs = 25_000L,
                lastRefreshMs = 10_000L,
                ttlMs = 60_000L,
                hasSelection = true
            )
        )
        assertTrue(
            shouldRefreshCdnIpLocation(
                enabled = true,
                nowMs = 80_001L,
                lastRefreshMs = 10_000L,
                ttlMs = 60_000L,
                hasSelection = true
            )
        )
        assertFalse(
            shouldRefreshCdnIpLocation(
                enabled = false,
                nowMs = 80_001L,
                lastRefreshMs = 10_000L,
                ttlMs = 60_000L,
                hasSelection = true
            )
        )
    }

    @Test
    fun `bundled catalog uses provided overseas upos cdn hosts`() {
        val catalogFile = listOf(
            File("src/main/res/raw/cdn_region_catalog.json"),
            File("app/src/main/res/raw/cdn_region_catalog.json")
        ).first { it.exists() }
        val catalog = Json.decodeFromString<Map<String, List<String>>>(catalogFile.readText())

        assertEquals(
            listOf(
                "upos-hz-mirrorakam.akamaized.net",
                "upos-sz-mirroraliov.bilivideo.com",
                "upos-sz-mirrorcosov.bilivideo.com"
            ),
            catalog["海外"]
        )
    }

    @Test
    fun `stale cached hosts fall back to current catalog hosts`() {
        val currentCatalogHosts = listOf(
            "upos-hz-mirrorakam.akamaized.net",
            "upos-sz-mirroraliov.bilivideo.com"
        )

        assertEquals(
            currentCatalogHosts,
            resolveCdnRegionHosts(
                region = "海外",
                cachedHosts = listOf("d1--ov-gotcha01.bilivideo.com"),
                catalog = mapOf("海外" to currentCatalogHosts)
            )
        )
        assertFalse(
            hasUsableCdnRegionSelection(
                region = "海外",
                cachedHosts = listOf("d1--ov-gotcha01.bilivideo.com"),
                catalog = mapOf("海外" to currentCatalogHosts)
            )
        )
    }

    @Test
    fun `health score prefers stable fast host over low latency unstable host`() {
        val urls = listOf(
            "https://fast.bilivideo.com/video.m4s",
            "https://laggy.bilivideo.com/video.m4s"
        )
        val sorted = sortCdnCandidatesByHealth(
            urls = urls.reversed(),
            healthByHost = mapOf(
                "fast.bilivideo.com" to CdnCandidateHealth(
                    host = "fast.bilivideo.com",
                    readyCount = 3,
                    manualProbeLatencyMs = 320,
                    manualProbeSpeedKbps = 5_120,
                    lastUpdatedAtMs = 1_000
                ),
                "laggy.bilivideo.com" to CdnCandidateHealth(
                    host = "laggy.bilivideo.com",
                    bufferingCount = 4,
                    playbackErrorCount = 1,
                    manualProbeLatencyMs = 80,
                    manualProbeSpeedKbps = 1_024,
                    lastUpdatedAtMs = 1_000
                )
            )
        )

        assertEquals("https://fast.bilivideo.com/video.m4s", sorted.first())
    }

    @Test
    fun `health sorting keeps error host as fallback`() {
        val urls = listOf(
            "https://ok.bilivideo.com/video.m4s",
            "https://bad.bilivideo.com/video.m4s"
        )
        val sorted = sortCdnCandidatesByHealth(
            urls = urls,
            healthByHost = mapOf(
                "ok.bilivideo.com" to CdnCandidateHealth(
                    host = "ok.bilivideo.com",
                    readyCount = 1,
                    lastUpdatedAtMs = 1_000
                ),
                "bad.bilivideo.com" to CdnCandidateHealth(
                    host = "bad.bilivideo.com",
                    playbackErrorCount = 3,
                    lastUpdatedAtMs = 1_000
                )
            )
        )

        assertEquals(urls.first(), sorted.first())
        assertTrue(sorted.contains(urls.last()))
    }

    @Test
    fun `health sorting preserves original order without observations`() {
        val urls = listOf(
            "https://a.bilivideo.com/video.m4s",
            "https://b.bilivideo.com/video.m4s"
        )

        assertEquals(urls, sortCdnCandidatesByHealth(urls, emptyMap()))
    }

    @Test
    fun `manual probe candidates respect count and cooldown`() {
        val urls = (1..7).map { index -> "https://h$index.bilivideo.com/video.m4s" }
        val candidates = resolveCdnProbeCandidates(
            urls = urls,
            healthByHost = mapOf(
                "h1.bilivideo.com" to CdnCandidateHealth(
                    host = "h1.bilivideo.com",
                    lastProbeAtMs = 9_500L
                )
            ),
            nowMs = 10_000L,
            limit = CdnProbeLimit(maxCandidates = 5, cooldownMs = 1_000L)
        )

        assertEquals(5, candidates.size)
        assertFalse(candidates.first().allowed)
        assertEquals(500L, candidates.first().cooldownRemainingMs)
        assertTrue(candidates.drop(1).all { it.allowed })
    }

    @Test
    fun `host diagnostics expose concrete manual probe latency`() {
        val diagnostics = buildCdnHostDiagnostics(
            hosts = listOf("cn-sccd-cm-03-02.bilivideo.com"),
            healthByHost = mapOf(
                "cn-sccd-cm-03-02.bilivideo.com" to CdnCandidateHealth(
                    host = "cn-sccd-cm-03-02.bilivideo.com",
                    manualProbeLatencyMs = 42L,
                    readyCount = 1,
                    lastProbeAtMs = 10_000L,
                    lastUpdatedAtMs = 10_000L
                )
            )
        )

        assertEquals(42L, diagnostics.single().latencyMs)
        assertEquals("延迟较低", diagnostics.single().statusLabel)
    }

    @Test
    fun `playback cdn names match PiliPlus provider labels`() {
        assertEquals(
            "ali（阿里云）",
            resolvePlaybackCdnDisplayName("upos-sz-mirrorali.bilivideo.com", index = 0)
        )
        assertEquals(
            "cosb（腾讯云，VOD加速类型）",
            resolvePlaybackCdnDisplayName("upos-sz-mirrorcosb.bilivideo.com", index = 1)
        )
        assertEquals(
            "hw（华为云，融合CDN）",
            resolvePlaybackCdnDisplayName("upos-sz-mirrorhw.bilivideo.com", index = 2)
        )
    }

    @Test
    fun `unknown playback cdn names fall back to base and backup labels`() {
        assertEquals("基础URL", resolvePlaybackCdnDisplayName("unknown.example.com", index = 0))
        assertEquals("备用URL", resolvePlaybackCdnDisplayName("unknown.example.com", index = 1))
    }

    @Test
    fun `global playback cdn preference rewrites paired tracks and preserves fallbacks`() {
        val original = buildPlaybackCdnCandidates(
            videoUrls = listOf(
                "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/2/video.m4s?deadline=1",
                "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/1/2/video.m4s?deadline=1",
            ),
            audioUrls = listOf(
                "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/2/audio.m4s?deadline=1",
                "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/1/2/audio.m4s?deadline=1",
            ),
        )

        val preferred = applyPlaybackCdnPreference(original, PlaybackCdnPreference.HW)

        assertEquals("upos-sz-mirrorhw.bilivideo.com", hostFromCdnUrl(preferred.first().videoUrl))
        assertEquals("upos-sz-mirrorhw.bilivideo.com", hostFromCdnUrl(preferred.first().audioUrl.orEmpty()))
        assertTrue(preferred.drop(1).any { it.videoUrl == original.first().videoUrl })
    }

    @Test
    fun `base preference skips an mcdn-marked primary and keeps the normal backup`() {
        val original = buildPlaybackCdnCandidates(
            videoUrls = listOf(
                "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/1/2/video.m4s?os=mcdn&sign=a",
                "https://cn-example-cm-01-01.bilivideo.com/upgcxcode/1/2/video.m4s?sign=b",
            ),
            audioUrls = emptyList(),
        )

        val preferred = applyPlaybackCdnPreference(original, PlaybackCdnPreference.BASE_URL)

        assertEquals(
            "https://cn-example-cm-01-01.bilivideo.com/upgcxcode/1/2/video.m4s?sign=b",
            preferred.first().videoUrl,
        )
    }

    @Test
    fun `fixed line uses a portable upgcxcode candidate instead of a resource path`() {
        val original = buildPlaybackCdnCandidates(
            videoUrls = listOf(
                "https://upos-sz-mirrorali.bilivideo.com/v1/resource/upgcxcode/1/2/video.m4s?sign=a",
                "https://cn-example-cm-01-01.bilivideo.com/upgcxcode/1/2/video.m4s?sign=b",
            ),
            audioUrls = emptyList(),
        )

        val preferred = applyPlaybackCdnPreference(original, PlaybackCdnPreference.HW_08C)

        assertEquals(
            "https://upos-sz-mirror08c.bilivideo.com/upgcxcode/1/2/video.m4s?sign=b",
            preferred.first().videoUrl,
        )
        assertFalse(preferred.first().videoUrl.contains("/v1/resource/"))
    }

    @Test
    fun `fixed line does not invent a host when only resource paths exist`() {
        val original = buildPlaybackCdnCandidates(
            videoUrls = listOf(
                "https://upos-sz-mirrorali.bilivideo.com/v1/resource/upgcxcode/1/2/video.m4s?sign=a",
            ),
            audioUrls = emptyList(),
        )

        val preferred = applyPlaybackCdnPreference(original, PlaybackCdnPreference.HW_08C)

        assertEquals(original, preferred)
        assertFalse(preferred.any { it.videoUrl.contains("upos-sz-mirror08c") })
    }

    @Test
    fun `default preference promotes signed regular backup without rewriting tracks`() {
        val original = buildPlaybackCdnCandidates(
            videoUrls = listOf(
                "https://a.mcdn.bilivideo.cn/v?sign=a%2Fb",
                "https://backup.bilivideo.com/v?sign=c%2Fd",
            ),
            audioUrls = listOf(
                "https://a.mcdn.bilivideo.cn/a?sign=e%2Ff",
                "https://backup.bilivideo.com/a?sign=g%2Fh",
            ),
        )

        val preferred = applyPlaybackCdnPreference(original, PlaybackCdnPreference.fromStorageValue(null))

        assertEquals(listOf(original[1], original[0]), preferred)
    }

    @Test
    fun `backup preference preserves regular primary and stable server order`() {
        val original = buildPlaybackCdnCandidates(
            videoUrls = listOf("https://z.bilivideo.com/v", "https://a.bilivideo.com/v"),
            audioUrls = emptyList(),
        )

        assertEquals(original, applyPlaybackCdnPreference(original, PlaybackCdnPreference.BACKUP_URL))
        assertEquals(original, applyPlaybackCdnPreference(original, PlaybackCdnPreference.fromStorageValue("base_url")))
    }

    @Test
    fun `backup preference keeps original candidates when only non-portable paths exist`() {
        val original = buildPlaybackCdnCandidates(
            videoUrls = listOf("https://1.2.3.4/v1/resource/a?sign=a", "https://a.mcdn.bilivideo.cn/v?sign=b"),
            audioUrls = emptyList(),
        )

        assertEquals(original, applyPlaybackCdnPreference(original, PlaybackCdnPreference.BACKUP_URL))
    }

    @Test
    fun `backup preference does not rotate plugin health ranking`() {
        val original = buildPlaybackCdnCandidates(
            videoUrls = listOf("https://a.bilivideo.com/v", "https://b.bilivideo.com/v"),
            audioUrls = emptyList(),
        )
        val ranked = sortSafeSignedPlaybackCandidates(
            original,
            mapOf("a.bilivideo.com" to CdnCandidateHealth(playbackErrorCount = 2)),
        )

        assertEquals(original[1], ranked.first())
        assertEquals(ranked, applyPlaybackCdnPreference(ranked, PlaybackCdnPreference.BACKUP_URL, pluginApplied = true))
    }
}
