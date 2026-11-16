package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.data.model.response.UgcEpisode
import com.android.purebilibili.data.model.response.UgcSeason
import com.android.purebilibili.data.model.response.UgcSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoDetailScreenPolicyTest {


    @Test
    fun localBack_prefersPortraitFullscreenOverLandscapeFullscreen() {
        assertEquals(
            VideoDetailLocalBackTarget.EXIT_PORTRAIT_FULLSCREEN,
            resolveVideoDetailLocalBackTarget(
                isLandscapeFullscreen = true,
                isPortraitFullscreen = true,
            )
        )
        assertEquals(
            VideoDetailLocalBackTarget.EXIT_LANDSCAPE_FULLSCREEN,
            resolveVideoDetailLocalBackTarget(
                isLandscapeFullscreen = true,
                isPortraitFullscreen = false,
            )
        )
        assertEquals(
            VideoDetailLocalBackTarget.NAVIGATE_BACK,
            resolveVideoDetailLocalBackTarget(
                isLandscapeFullscreen = false,
                isPortraitFullscreen = false,
            )
        )
        assertEquals(
            VideoDetailLocalBackTarget.NAVIGATE_BACK,
            resolveVideoDetailLocalBackTarget(
                isLandscapeFullscreen = false,
                isPortraitFullscreen = true,
                directPortraitEntry = true,
            )
        )
    }

    @Test
    fun localBack_consumesPendingEnterExitAndCollapseBeforeRouteNavigation() {
        for (phase in ContinuousPlayerTransitionPhase.entries) {
            assertEquals(
                if (phase == ContinuousPlayerTransitionPhase.Inline) {
                    VideoDetailLocalBackTarget.NAVIGATE_BACK
                } else {
                    VideoDetailLocalBackTarget.EXIT_LANDSCAPE_FULLSCREEN
                },
                resolveVideoDetailLocalBackTarget(
                    isLandscapeFullscreen = false,
                    isPortraitFullscreen = false,
                    continuousPlayerPhase = phase,
                ),
            )
        }
    }

    @Test
    fun localBack_keepsPortraitPriorityButCancelsPendingLandscapeForDirectPortraitEntry() {
        for (phase in ContinuousPlayerTransitionPhase.entries) {
            assertEquals(
                VideoDetailLocalBackTarget.EXIT_PORTRAIT_FULLSCREEN,
                resolveVideoDetailLocalBackTarget(
                    isLandscapeFullscreen = false,
                    isPortraitFullscreen = true,
                    continuousPlayerPhase = phase,
                ),
            )
            assertEquals(
                if (phase == ContinuousPlayerTransitionPhase.Inline) {
                    VideoDetailLocalBackTarget.NAVIGATE_BACK
                } else {
                    VideoDetailLocalBackTarget.EXIT_LANDSCAPE_FULLSCREEN
                },
                resolveVideoDetailLocalBackTarget(
                    isLandscapeFullscreen = false,
                    isPortraitFullscreen = true,
                    directPortraitEntry = true,
                    continuousPlayerPhase = phase,
                ),
            )
        }
    }

    @Test
    fun landscapeNaturalCover_backOnlyConsumesUserRequestedFullscreen() {
        val orientationDrivenFullscreen = false
        val initialFullscreen = resolveVideoDetailFullscreenMode(
            isOrientationDrivenFullscreen = orientationDrivenFullscreen,
            isLandscape = true,
            userRequestedFullscreen = false,
            isInMultiWindowMode = false,
        )
        val requestedFullscreen = resolveVideoDetailFullscreenMode(
            isOrientationDrivenFullscreen = orientationDrivenFullscreen,
            isLandscape = true,
            userRequestedFullscreen = true,
            isInMultiWindowMode = false,
        )

        assertEquals(
            VideoDetailLocalBackTarget.NAVIGATE_BACK,
            resolveVideoDetailLocalBackTarget(
                isLandscapeFullscreen = initialFullscreen,
                isPortraitFullscreen = false,
            )
        )
        assertEquals(
            VideoDetailLocalBackTarget.EXIT_LANDSCAPE_FULLSCREEN,
            resolveVideoDetailLocalBackTarget(
                isLandscapeFullscreen = requestedFullscreen,
                isPortraitFullscreen = false,
            )
        )
    }

    @Test
    fun portraitExitPlayerTarget_prefersCurrentInternalBvidOverRouteBvid() {
        val resolved = resolveVideoPlayerSectionTarget(
            routeBvid = "BV_ROUTE",
            routeCoverUrl = "https://img/route.jpg",
            currentBvid = "BV_PORTRAIT_NEXT"
        )

        assertEquals("BV_PORTRAIT_NEXT", resolved.bvid)
        assertEquals("", resolved.entryCoverUrl)
        // Shared-element key stays on route entry so in-page switch does not rekey the surface.
        assertEquals("BV_ROUTE", resolved.sharedElementBvid)
    }

    @Test
    fun portraitExitPlayerTarget_usesSwitchedCoverWhenInternalBvidDiffers() {
        val resolved = resolveVideoPlayerSectionTarget(
            routeBvid = "BV_ROUTE",
            routeCoverUrl = "https://img/route.jpg",
            currentBvid = "BV_COLLECTION_NEXT",
            switchedCoverUrl = "https://img/episode.jpg"
        )

        assertEquals("BV_COLLECTION_NEXT", resolved.bvid)
        assertEquals("https://img/episode.jpg", resolved.entryCoverUrl)
        assertEquals("BV_ROUTE", resolved.sharedElementBvid)
    }

    @Test
    fun portraitExitPlayerTarget_doesNotFallBackToRouteCoverForPortraitNextWithoutSwitchedCover() {
        // 竖屏已切到下一片但尚未写入 switchedCover 时，entry 应为空，
        // 避免 resolvePreferredVideoCoverUrl 用路由首个视频封面冒充当前片。
        val resolved = resolveVideoPlayerSectionTarget(
            routeBvid = "BV_ROUTE",
            routeCoverUrl = "https://img/first.jpg",
            currentBvid = "BV_PORTRAIT_NEXT",
            switchedCoverUrl = "",
        )
        assertEquals("BV_PORTRAIT_NEXT", resolved.bvid)
        assertEquals("", resolved.entryCoverUrl)
    }

    @Test
    fun portraitExitPlayerTarget_keepsRouteCoverWhenStillShowingRouteVideo() {
        val resolved = resolveVideoPlayerSectionTarget(
            routeBvid = "BV_ROUTE",
            routeCoverUrl = "https://img/route.jpg",
            currentBvid = "BV_ROUTE"
        )

        assertEquals("BV_ROUTE", resolved.bvid)
        assertEquals("https://img/route.jpg", resolved.entryCoverUrl)
        assertEquals("BV_ROUTE", resolved.sharedElementBvid)
    }

    @Test
    fun portraitExitPlayerTarget_fallsBackToRouteWhenInternalTargetMissing() {
        val resolved = resolveVideoPlayerSectionTarget(
            routeBvid = "BV_ROUTE",
            routeCoverUrl = "https://img/route.jpg",
            currentBvid = ""
        )

        assertEquals("BV_ROUTE", resolved.bvid)
        assertEquals("https://img/route.jpg", resolved.entryCoverUrl)
        assertEquals("BV_ROUTE", resolved.sharedElementBvid)
    }

    @Test
    fun ugcSeasonEpisodeCover_prefersExactCidMatch() {
        val season = UgcSeason(
            sections = listOf(
                UgcSection(
                    episodes = listOf(
                        UgcEpisode(
                            bvid = "BV1",
                            cid = 11L,
                            arc = com.android.purebilibili.data.model.response.UgcEpisodeArc(
                                pic = "https://img/ep1.jpg"
                            )
                        ),
                        UgcEpisode(
                            bvid = "BV1",
                            cid = 22L,
                            arc = com.android.purebilibili.data.model.response.UgcEpisodeArc(
                                pic = "https://img/ep2.jpg"
                            )
                        )
                    )
                )
            )
        )

        assertEquals(
            "https://img/ep2.jpg",
            resolveUgcSeasonEpisodeCoverUrl(
                ugcSeason = season,
                targetBvid = "BV1",
                targetCid = 22L
            )
        )
        assertEquals(
            "https://img/ep1.jpg",
            resolveUgcSeasonEpisodeCoverUrl(
                ugcSeason = season,
                targetBvid = "BV1",
                targetCid = 0L
            )
        )
    }

    @Test
    fun initialVerticalRouteHint_doesNotBypassInlinePresentationPolicy() {
        assertFalse(
            shouldStartInPortraitFullscreenFromRouteHint(
                autoEnterPortraitFromRoute = true,
                startAudioFromRoute = false,
                initialVerticalFromRoute = true
            )
        )
    }


    @Test
    fun collectionNavigationKeepsCurrentVideoPartsAndSameCollectionInsideDetail() {
        val season = UgcSeason(
            sections = listOf(
                UgcSection(
                    episodes = listOf(
                        UgcEpisode(bvid = "BV1A", cid = 1001L),
                        UgcEpisode(bvid = "BV2B", cid = 2002L)
                    )
                )
            )
        )

        assertTrue(
            shouldSwitchCollectionVideoInsideCurrentDetailPage(
                targetBvid = "BV2B",
                currentBvid = "BV1A",
                ugcSeason = season
            )
        )
        assertFalse(
            shouldSwitchCollectionVideoInsideCurrentDetailPage(
                targetBvid = "BV3C",
                currentBvid = "BV1A",
                ugcSeason = season
            )
        )
        assertTrue(
            shouldSwitchCollectionVideoInsideCurrentDetailPage(
                targetBvid = "BV1A",
                currentBvid = "BV1A",
                ugcSeason = season
            )
        )
    }

}
