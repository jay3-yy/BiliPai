package com.android.purebilibili.feature.video.ui.pager

import com.android.purebilibili.data.model.response.Dash
import com.android.purebilibili.data.model.response.DashAudio
import com.android.purebilibili.data.model.response.DashVideo
import com.android.purebilibili.data.model.response.Durl
import com.android.purebilibili.data.model.response.Flac
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.PlayUrlData
import com.android.purebilibili.data.model.response.RelatedVideo
import com.android.purebilibili.data.model.response.ViewInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PortraitVideoLoadPolicyTest {
    @Test
    fun portraitHardwarePreferenceSkipsSoftwareOnlyAv1AtTheSameQuality() {
        val av1 = DashVideo(id = 80, codecs = "av01", baseUrl = "https://example.com/av1.m4s")
        val hevc = DashVideo(id = 80, codecs = "hev1", baseUrl = "https://example.com/hevc.m4s")
        val urls = resolvePortraitPlaybackStreamUrls(
            playData = PlayUrlData(quality = 80, dash = Dash(video = listOf(av1, hevc))),
            targetQuality = 80,
            preferCodec = "av01",
            isHevcSupported = true,
            isAv1Supported = true,
            isDolbyAudioSupported = false,
            isDolbyAudioSoftwareDecoded = false,
            videoTrackFilter = { tracks ->
                com.android.purebilibili.core.util.selectPlayableVideoTracks(tracks, preferHardware = true) { track ->
                    com.android.purebilibili.core.util.VideoTrackDecoderSupport(
                        supported = true, hardware = track == hevc, software = track == av1,
                    )
                }
            },
        )

        assertEquals(hevc.baseUrl, urls?.videoUrl)
        assertEquals(80, urls?.videoQualityId)
    }

    @Test
    fun unsupportedPortraitDashCannotBypassCapabilityFilteringThroughLegacyUrl() {
        val urls = resolvePortraitPlaybackStreamUrls(
            playData = PlayUrlData(
                quality = 80,
                dash = Dash(video = listOf(DashVideo(id = 80, codecs = "hev1", baseUrl = "https://example.com/video.m4s"))),
                durl = listOf(Durl(url = "https://example.com/legacy.mp4")),
            ),
            targetQuality = 80,
            isHevcSupported = true,
            isAv1Supported = false,
            isDolbyAudioSupported = false,
            isDolbyAudioSoftwareDecoded = false,
            videoTrackFilter = { emptyList() },
        )

        assertNull(urls)
    }

    @Test
    fun portraitQualityIdentityFollowsTheActuallySupportedTrack() {
        val unsupported = DashVideo(id = 80, codecs = "hev1", baseUrl = "https://example.com/1080.m4s")
        val supported = DashVideo(id = 64, codecs = "avc1", baseUrl = "https://example.com/720.m4s")
        val urls = resolvePortraitPlaybackStreamUrls(
            playData = PlayUrlData(quality = 80, dash = Dash(video = listOf(unsupported, supported))),
            targetQuality = 80,
            isHevcSupported = true,
            isAv1Supported = false,
            isDolbyAudioSupported = false,
            isDolbyAudioSoftwareDecoded = false,
            videoTrackFilter = { tracks -> tracks.filter { it == supported } },
        )

        assertEquals(supported.baseUrl, urls?.videoUrl)
        assertEquals(64, urls?.videoQualityId)
    }

    @Test
    fun portraitSourcesApplySavedCdnPreferenceBeforePlayerPreparation() {
        val baseVideo = "https://a.mcdn.bilivideo.cn/upgcxcode/v.m4s?sign=a%2Fb"
        val backupVideo = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/v.m4s?sign=c%2Fd"
        val baseAudio = "https://a.mcdn.bilivideo.cn/upgcxcode/a.m4s?sign=e%2Ff"
        val backupAudio = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/a.m4s?sign=g%2Fh"
        val videos = listOf(DashVideo(id = 80, baseUrl = baseVideo, backupUrl = listOf(backupVideo)))
        val audios = listOf(DashAudio(id = 30280, baseUrl = baseAudio, backupUrl = listOf(backupAudio)))
        val streams = PortraitPlaybackStreamUrls(backupVideo, backupAudio)
        val defaults = resolvePortraitPlaybackCdnUrls(streams, videos, audios, 80, cdnPlugin = null)

        assertEquals(backupVideo, defaults.videoUrl)
        assertEquals(backupAudio, defaults.audioUrl)

        val manual = resolvePortraitPlaybackCdnUrls(
            streams, videos, audios, 80, cdnPlugin = null,
            preference = com.android.purebilibili.feature.plugin.PlaybackCdnPreference.HW,
        )
        assertEquals("upos-sz-mirrorhw.bilivideo.com", java.net.URI(manual.videoUrl).host)
        assertEquals("upos-sz-mirrorhw.bilivideo.com", java.net.URI(manual.audioUrl).host)
    }


    @Test
    fun enrichPortraitPageItemWithLoadedInfo_fillsOwnerFromDetail() {
        val seed = ViewInfo(bvid = "BV1", title = "seed", pic = "cover")
        val loaded = ViewInfo(
            bvid = "BV1",
            aid = 99L,
            cid = 12L,
            title = "正式标题",
            pic = "https://i0.hdslb.com/bfs/cover.jpg",
            owner = Owner(mid = 42L, name = "熊猫大G", face = "https://face")
        )

        val enriched = assertIs<ViewInfo>(
            enrichPortraitPageItemWithLoadedInfo(existing = seed, loaded = loaded)
        )
        assertEquals("熊猫大G", enriched.owner.name)
        assertEquals(42L, enriched.owner.mid)
        assertEquals("正式标题", enriched.title)
        assertEquals(99L, enriched.aid)
        assertEquals(12L, enriched.cid)
    }

    @Test
    fun resolvePortraitAuthorLabel_avoidsBareAtWhenNameMissing() {
        assertEquals("UP主", resolvePortraitAuthorLabel(""))
        assertEquals("UP主", resolvePortraitAuthorLabel("   "))
        assertEquals("@熊猫大G", resolvePortraitAuthorLabel("熊猫大G"))
    }

    @Test
    fun playbackTargetQuality_prefersUserSettingOverFallback() {
        assertEquals(64, resolvePortraitPlaybackTargetQuality())
        assertEquals(80, resolvePortraitPlaybackTargetQuality(preferredQuality = 80))
        assertEquals(125, resolvePortraitPlaybackTargetQuality(preferredQuality = 125))
        assertEquals(127, resolvePortraitPlaybackTargetQuality(preferredQuality = 127))
        assertEquals(64, resolvePortraitPlaybackTargetQuality(preferredQuality = 0))
        assertEquals(64, resolvePortraitPlaybackTargetQuality(preferredQuality = -1))
    }

    @Test
    fun qualityLabel_coversPremiumAndAutoTiers() {
        assertEquals("自动", resolvePortraitQualityLabel(127))
        assertEquals("HDR", resolvePortraitQualityLabel(125))
        assertEquals("4K", resolvePortraitQualityLabel(120))
        assertEquals("1080P", resolvePortraitQualityLabel(80))
        assertEquals("720P", resolvePortraitQualityLabel(64))
    }

    @Test
    fun availableQualityIds_unionsDashAndAcceptSortedDesc() {
        assertEquals(
            listOf(125, 120, 80, 64, 32),
            resolvePortraitAvailableQualityIds(
                acceptQualities = listOf(125, 120, 80, 64, 32),
                dashVideoIds = listOf(80, 64)
            )
        )
    }

    @Test
    fun displayedQualityId_prefersExactRequestedDashTrack() {
        assertEquals(
            80,
            resolvePortraitDisplayedQualityId(
                requestedQuality = 80,
                returnedQuality = 64,
                dashVideoIds = listOf(80, 64)
            )
        )
        assertEquals(
            64,
            resolvePortraitDisplayedQualityId(
                requestedQuality = 125,
                returnedQuality = 64,
                dashVideoIds = listOf(64, 32)
            )
        )
    }

    @Test
    fun displayedQualityId_tracksDiscoveredStreamsInsteadOfResponseMetadata() {
        assertEquals(80, resolvePortraitDisplayedQualityId(
            requestedQuality = 80,
            returnedQuality = 129,
            dashVideoIds = listOf(129, 120, 80, 64)
        ))
        assertEquals(129, resolvePortraitDisplayedQualityId(
            requestedQuality = 127,
            returnedQuality = 127,
            dashVideoIds = listOf(129, 127, 80)
        ))
        assertEquals(64, resolvePortraitDisplayedQualityId(
            requestedQuality = 80,
            returnedQuality = 129,
            dashVideoIds = listOf(129, 64, 32)
        ))
    }

    @Test
    fun parallelBootstrap_enablesWhenFeedProvidesCid() {
        assertTrue(
            shouldUsePortraitParallelPlaybackBootstrap(
                bvid = "BV1test",
                requestedCid = 12345L
            )
        )
    }

    @Test
    fun parallelBootstrap_disablesWhenCidMissing() {
        assertFalse(
            shouldUsePortraitParallelPlaybackBootstrap(
                bvid = "BV1test",
                requestedCid = 0L
            )
        )
    }

    @Test
    fun parallelBootstrap_disablesForAidFallbackIdentifier() {
        assertFalse(
            shouldUsePortraitParallelPlaybackBootstrap(
                bvid = "av12345",
                requestedCid = 67890L
            )
        )
    }

    @Test
    fun pagePlaybackIdentity_readsCidFromRelatedVideo() {
        val identity = resolvePortraitPagePlaybackIdentity(
            RelatedVideo(
                bvid = "BV1related",
                aid = 42L,
                cid = 99L
            )
        )

        assertEquals("BV1related", identity?.bvid)
        assertEquals(42L, identity?.aid)
        assertEquals(99L, identity?.cid)
    }

    @Test
    fun pagePlaybackIdentity_readsCidFromViewInfo() {
        val identity = resolvePortraitPagePlaybackIdentity(
            ViewInfo(
                bvid = "BV1view",
                aid = 7L,
                cid = 8L
            )
        )

        assertEquals("BV1view", identity?.bvid)
        assertEquals(7L, identity?.aid)
        assertEquals(8L, identity?.cid)
    }

    @Test
    fun playbackStreamUrls_prefersBestDashTrackOverFirstTrack() {
        val playData = PlayUrlData(
            dash = Dash(
                video = listOf(
                    DashVideo(
                        id = 64,
                        baseUrl = "https://cdn.example/64.m4s",
                        codecs = "avc1.64001E"
                    ),
                    DashVideo(
                        id = 80,
                        baseUrl = "https://cdn.example/80.m4s",
                        codecs = "hev1.1.6.L120.90"
                    )
                ),
                audio = listOf(
                    DashAudio(
                        id = 30232,
                        baseUrl = "https://cdn.example/audio.m4s"
                    )
                )
            )
        )

        val urls = resolvePortraitPlaybackStreamUrls(
            playData = playData,
            targetQuality = 64,
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals("https://cdn.example/64.m4s", urls?.videoUrl)
        assertEquals("https://cdn.example/audio.m4s", urls?.audioUrl)
    }

    @Test
    fun playbackStreamUrls_honorsPreferredHighQualityTrack() {
        val playData = PlayUrlData(
            dash = Dash(
                video = listOf(
                    DashVideo(
                        id = 64,
                        baseUrl = "https://cdn.example/64.m4s",
                        codecs = "avc1.64001E"
                    ),
                    DashVideo(
                        id = 80,
                        baseUrl = "https://cdn.example/80.m4s",
                        codecs = "hev1.1.6.L120.90"
                    )
                ),
                audio = listOf(
                    DashAudio(
                        id = 30232,
                        baseUrl = "https://cdn.example/audio.m4s"
                    )
                )
            )
        )

        val urls = resolvePortraitPlaybackStreamUrls(
            playData = playData,
            targetQuality = 80,
            isHevcSupported = true,
            isAv1Supported = false
        )

        assertEquals("https://cdn.example/80.m4s", urls?.videoUrl)
    }

    @Test
    fun playbackStreamUrls_selectsRequestedHiResTrack() {
        val playData = PlayUrlData(
            dash = Dash(
                video = listOf(
                    DashVideo(
                        id = 80,
                        baseUrl = "https://cdn.example/video.m4s",
                        codecs = "avc1.64001E"
                    )
                ),
                audio = listOf(
                    DashAudio(
                        id = 30232,
                        baseUrl = "https://cdn.example/standard.m4s"
                    )
                ),
                flac = Flac(
                    display = true,
                    audio = DashAudio(
                        id = 30251,
                        baseUrl = "https://cdn.example/hires.m4s"
                    )
                )
            )
        )

        val urls = resolvePortraitPlaybackStreamUrls(
            playData = playData,
            requestedAudioQuality = 30251
        )

        assertEquals("https://cdn.example/hires.m4s", urls?.audioUrl)
        assertEquals(30251, urls?.audioSelection?.selectedPreferenceId)
        assertTrue(urls?.audioSelection?.availableOptions?.any { it.isHiRes } == true)
    }

    @Test
    fun playbackStreamUrls_fallsBackToProgressiveUrl() {
        val urls = resolvePortraitPlaybackStreamUrls(
            playData = PlayUrlData(
                durl = listOf(Durl(url = "https://cdn.example/progressive.mp4"))
            )
        )

        assertEquals("https://cdn.example/progressive.mp4", urls?.videoUrl)
        assertNull(urls?.audioUrl)
    }

    @Test
    fun playUrlPreloadCount_keepsTwoPagesWarmOnWifiByDefault() {
        assertEquals(
            2,
            resolvePortraitPlayUrlPreloadCount(
                prefetchVideoEnabled = false,
                isWifi = true,
                availableTargets = 3
            )
        )
    }

    @Test
    fun playUrlPreloadCount_expandsToThreeWhenPrefetchIsEnabled() {
        assertEquals(
            3,
            resolvePortraitPlayUrlPreloadCount(
                prefetchVideoEnabled = true,
                isWifi = true,
                availableTargets = 5
            )
        )
    }

    @Test
    fun playUrlPreloadCount_warmsOneCellularPageOnlyWhenExplicitlyEnabled() {
        assertEquals(
            1,
            resolvePortraitPlayUrlPreloadCount(
                prefetchVideoEnabled = true,
                isWifi = false,
                availableTargets = 3
            )
        )
        assertEquals(
            0,
            resolvePortraitPlayUrlPreloadCount(
                prefetchVideoEnabled = false,
                isWifi = false,
                availableTargets = 3
            )
        )
        assertEquals(
            0,
            resolvePortraitPlayUrlPreloadCount(
                prefetchVideoEnabled = true,
                isWifi = true,
                availableTargets = 0
            )
        )
    }

    @Test
    fun playbackHeadPrefetch_hasEnoughOpeningMediaForFastSwipes() {
        assertEquals(1536L * 1024L, PORTRAIT_VIDEO_HEAD_PREFETCH_BYTES)
        assertEquals(256L * 1024L, PORTRAIT_AUDIO_HEAD_PREFETCH_BYTES)
    }

    @Test
    fun swipePrefetchTargetPage_triggersWhenSwipingDownPastThreshold() {
        assertEquals(
            2,
            resolvePortraitSwipePrefetchTargetPage(
                isScrollInProgress = true,
                currentPage = 1,
                currentPageOffsetFraction = -0.3f,
                lastPageIndex = 4
            )
        )
    }

    @Test
    fun swipePrefetchTargetPage_triggersWhenSwipingUpPastThreshold() {
        assertEquals(
            0,
            resolvePortraitSwipePrefetchTargetPage(
                isScrollInProgress = true,
                currentPage = 1,
                currentPageOffsetFraction = 0.3f,
                lastPageIndex = 4
            )
        )
    }

    @Test
    fun swipePrefetchTargetPage_ignoresSmallOffsetAndSettledPager() {
        assertNull(
            resolvePortraitSwipePrefetchTargetPage(
                isScrollInProgress = true,
                currentPage = 1,
                currentPageOffsetFraction = -0.1f,
                lastPageIndex = 4
            )
        )
        assertNull(
            resolvePortraitSwipePrefetchTargetPage(
                isScrollInProgress = false,
                currentPage = 1,
                currentPageOffsetFraction = -0.8f,
                lastPageIndex = 4
            )
        )
    }

    @Test
    fun earlyPlaybackPage_bindsAsSoonAsPagerCrossesToTheTargetPage() {
        assertNull(
            resolvePortraitEarlyPlaybackPage(
                isScrollInProgress = true,
                currentPage = 1,
                lastCommittedPage = 1,
                lastPageIndex = 4
            )
        )
        assertEquals(
            2,
            resolvePortraitEarlyPlaybackPage(
                isScrollInProgress = true,
                currentPage = 2,
                lastCommittedPage = 1,
                lastPageIndex = 4
            )
        )
        assertEquals(
            0,
            resolvePortraitEarlyPlaybackPage(
                isScrollInProgress = true,
                currentPage = 0,
                lastCommittedPage = 1,
                lastPageIndex = 4
            )
        )
    }

    @Test
    fun playUrlPreloadTargets_collectsUpcomingPagesWithoutDuplicates() {
        val targets = resolvePortraitPlayUrlPreloadTargets(
            committedPage = 0,
            pageItems = listOf(
                RelatedVideo(bvid = "BV1", cid = 1L),
                RelatedVideo(bvid = "BV2", cid = 2L),
                RelatedVideo(bvid = "BV2", cid = 2L),
                RelatedVideo(bvid = "BV3", cid = 3L)
            ),
            preloadCount = 2
        )

        assertEquals(2, targets.size)
        assertEquals("BV2", targets[0].bvid)
        assertEquals(2L, targets[0].cid)
        assertEquals("BV3", targets[1].bvid)
    }
}
