package com.android.purebilibili.core.ui.transition

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VideoSharedTransitionPolicyTest {

    @Test
    fun coverSharedTransition_enabled_whenTransitionAndScopesAreReady() {
        assertTrue(
            shouldEnableVideoCoverSharedTransition(
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertFalse(
            shouldEnableVideoCoverSharedTransition(
                transitionEnabled = true,
                hasSharedTransitionScope = false,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun metadataSharedTransition_keepsDefaultForNonHomeCallers() {
        assertEquals(VideoSharedTransitionProfile.COVER_AND_METADATA, resolveVideoSharedTransitionProfile())
        assertTrue(
            shouldEnableVideoMetadataSharedTransition(
                coverSharedEnabled = true,
                isQuickReturnLimited = false
            )
        )
    }

    @Test
    fun metadataSharedTransition_staysEnabledWhenQuickReturnLimitedForNonHomeCallers() {
        assertTrue(
            shouldEnableVideoMetadataSharedTransition(
                coverSharedEnabled = true,
                isQuickReturnLimited = true
            )
        )
    }

    @Test
    fun metadataSharedTransition_disabledWhenCardContainerOwnsSharedBounds() {
        assertFalse(
            shouldEnableVideoMetadataSharedTransition(
                coverSharedEnabled = true,
                isQuickReturnLimited = false,
                useCardContainerSharedBounds = true
            )
        )
    }

    @Test
    fun homeVideoTransition_usesCoverOnlyToAvoidTextBoundsScaling() {
        val policy = resolveVideoSharedTransitionOwnership(
            sourceRoute = "home",
            coverSharedEnabled = true,
            isQuickReturnLimited = false
        )

        assertTrue(policy.useCoverSharedBounds)
        // Home 卡片标题和 UP 名排版与详情页不同，参与 sharedBounds 会在落位时缩放文字。
        assertFalse(policy.useMetadataSharedBounds)
    }

    @Test
    fun videoCardShellKey_keepsSourceRouteDistinctFromCoverKey() {
        val shellKey = videoCardShellSharedElementKey(
            bvid = "BV1",
            sourceRoute = "history"
        )
        val coverKey = videoCoverSharedElementKey(
            bvid = "BV1",
            sourceRoute = "history"
        )

        assertEquals(VideoSharedElement.CARD_SHELL, shellKey.element)
        assertEquals("history", shellKey.sourceRoute)
        assertFalse(shellKey == coverKey)
    }

    @Test
    fun nonHomeVideoTransition_keepsMetadataSharedBoundsWhenAvailable() {
        val policy = resolveVideoSharedTransitionOwnership(
            sourceRoute = "search",
            coverSharedEnabled = true,
            isQuickReturnLimited = false
        )

        assertTrue(policy.useCoverSharedBounds)
        assertTrue(policy.useMetadataSharedBounds)
    }

    @Test
    fun videoCardSharedTransitionMotion_usesStandardCoverPrimaryTimelineByDefault() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true
        )

        assertTrue(motion.enabled)
        assertEquals(460, motion.durationMillis)
        assertEquals(540, motion.fullscreenDurationMillis)
        assertEquals(40, motion.contentDelayMillis)
        assertEquals(276, motion.contentDurationMillis)
        assertEquals(14, motion.contentSlideOffsetDp)
        assertEquals(0.985f, motion.contentInitialScale, 0.0001f)
        assertTrue(motion.easing.transform(0.35f) > 0.7f)
        assertTrue(motion.easing.transform(0.35f) < 0.9f)
        assertTrue(motion.easing.transform(0.75f) > 0.96f)
    }

    @Test
    fun videoCardSharedTransitionMotion_preservesFastTimelineOption() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true,
            speedSettings = VideoSharedTransitionSpeedSettings(VideoSharedTransitionSpeed.FAST)
        )

        assertEquals(360, motion.durationMillis)
        assertEquals(440, motion.fullscreenDurationMillis)
        assertEquals(220, motion.contentDurationMillis)
    }

    @Test
    fun videoCardSharedTransitionMotion_supportsSlowTimelineOption() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true,
            speedSettings = VideoSharedTransitionSpeedSettings(VideoSharedTransitionSpeed.SLOW)
        )

        assertEquals(560, motion.durationMillis)
        assertEquals(640, motion.fullscreenDurationMillis)
        assertEquals(336, motion.contentDurationMillis)
    }

    @Test
    fun videoCardSharedTransitionMotion_supportsClampedCustomTimeline() {
        val low = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true,
            speedSettings = VideoSharedTransitionSpeedSettings(
                speed = VideoSharedTransitionSpeed.CUSTOM,
                customDurationMillis = 120
            )
        )
        val custom = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true,
            speedSettings = VideoSharedTransitionSpeedSettings(
                speed = VideoSharedTransitionSpeed.CUSTOM,
                customDurationMillis = 620
            )
        )
        val high = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true,
            speedSettings = VideoSharedTransitionSpeedSettings(
                speed = VideoSharedTransitionSpeed.CUSTOM,
                customDurationMillis = 1200
            )
        )

        assertEquals(280, low.durationMillis)
        assertEquals(620, custom.durationMillis)
        assertEquals(700, custom.fullscreenDurationMillis)
        assertEquals(360, custom.contentDurationMillis)
        assertEquals(900, high.durationMillis)
    }

    @Test
    fun videoMetadataSharedTransitionMotion_usesCoverTimingButBoundsFinishEarlier() {
        val coverMotion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true
        )
        val metadataMotion = resolveVideoMetadataSharedTransitionMotionSpec(
            transitionEnabled = true
        )

        assertTrue(metadataMotion.enabled)
        assertEquals(coverMotion.durationMillis, metadataMotion.durationMillis)
        assertEquals(coverMotion.fullscreenDurationMillis, metadataMotion.fullscreenDurationMillis)
        assertEquals(0, metadataMotion.contentDelayMillis)
        assertSame(coverMotion.easing, metadataMotion.easing)
        assertEquals(331, resolveVideoMetadataSharedBoundsDurationMillis(metadataMotion))
        assertTrue(resolveVideoMetadataSharedBoundsDurationMillis(metadataMotion) < metadataMotion.durationMillis)
    }

    @Test
    fun videoMetadataSharedBounds_avoidGenericSpatialOrSpringSpecs() {
        val homeCardSource = File(
            "src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt"
        ).readText()
        val detailInfoSource = File(
            "src/main/java/com/android/purebilibili/feature/video/ui/section/VideoInfoSection.kt"
        ).readText()
        val partitionSource = File(
            "src/main/java/com/android/purebilibili/feature/partition/PartitionScreen.kt"
        ).readText()
        val cinematicCardSource = File(
            "src/main/java/com/android/purebilibili/feature/home/components/cards/CinematicVideoCard.kt"
        ).readText()
        val glassCardSource = File(
            "src/main/java/com/android/purebilibili/feature/home/components/cards/GlassVideoCard.kt"
        ).readText()
        val dynamicCardSource = File(
            "src/main/java/com/android/purebilibili/feature/dynamic/components/VideoCards.kt"
        ).readText()
        val watchLaterSource = File(
            "src/main/java/com/android/purebilibili/feature/watchlater/WatchLaterScreen.kt"
        ).readText()
        val spaceSource = File(
            "src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt"
        ).readText()

        assertTrue(homeCardSource.contains("videoMetadataSharedElementBoundsTransformSpec(homeSharedTransitionMotionSpec)"))
        assertFalse(homeCardSource.contains("AppMotionTokens.spatialSpec()"))
        assertTrue(detailInfoSource.contains("resolveVideoMetadataSharedTransitionMotionSpec("))
        assertTrue(detailInfoSource.contains("videoMetadataSharedElementBoundsTransformSpec(metadataSharedTransitionMotionSpec)"))
        assertFalse(detailInfoSource.contains("spring(dampingRatio = 0.8f, stiffness = 200f)"))
        assertTrue(partitionSource.contains("videoMetadataSharedElementBoundsTransformSpec(sharedTransitionMotionSpec)"))
        assertFalse(partitionSource.contains("spring(dampingRatio = 0.8f, stiffness = 200f)"))
        assertTrue(cinematicCardSource.contains("videoMetadataSharedElementBoundsTransformSpec(cardSharedTransitionMotionSpec)"))
        assertFalse(cinematicCardSource.contains("spring(dampingRatio = 0.8f, stiffness = 200f)"))
        assertTrue(glassCardSource.contains("videoTitleSharedElementKey("))
        assertTrue(glassCardSource.contains("sourceRoute = effectiveSharedElementSourceRoute"))
        assertTrue(glassCardSource.contains("videoMetadataSharedElementBoundsTransformSpec(cardSharedTransitionMotionSpec)"))
        assertTrue(dynamicCardSource.contains("videoMetadataSharedElementBoundsTransformSpec(sharedTransitionMotionSpec)"))
        assertTrue(watchLaterSource.contains("videoMetadataSharedElementBoundsTransformSpec(sharedTransitionMotionSpec)"))
        assertTrue(spaceSource.contains("videoMetadataSharedElementBoundsTransformSpec(cardSharedTransitionMotionSpec)"))
    }

    @Test
    fun videoCardSharedTransitionMotion_keepsTimelineForNonHomeSources() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "search",
            transitionEnabled = true
        )

        assertTrue(motion.enabled)
        assertEquals(460, motion.durationMillis)
    }

    @Test
    fun homeSharedTransitionCornerSpec_softlyConvergesFromCardToPlayer() {
        val corner = resolveHomeVideoSharedTransitionCornerSpec(
            sourceRoute = "home",
            transitionEnabled = true
        )

        assertTrue(corner.enabled)
        assertEquals(16, corner.startCornerDp)
        assertEquals(12, corner.endCornerDp)
    }

    @Test
    fun sharedCoverAspectRatio_defaultsToHomeCardSixteenByTen() {
        assertEquals(1.6f, VIDEO_SHARED_COVER_ASPECT_RATIO, 0.0001f)
    }

    @Test
    fun sharedTransitionVisualSpec_coverFirst_anchorsToInlineCover() {
        val spec = resolveVideoSharedTransitionVisualSpec(
            sourceRoute = "home",
            sourceCornerDp = 12,
            playbackIntent = VideoSharedTransitionPlaybackIntent.CoverFirst,
            fullscreen = false,
            autoPortrait = false,
            initialVertical = false,
            isVerticalVideo = false,
            isReturning = false
        )

        assertEquals(VideoSharedTransitionTargetMode.InlineCover, spec.targetMode)
        assertEquals(12, spec.sourceCornerDp)
        assertEquals(12, spec.targetCornerDp)
        assertFalse(spec.fillTargetViewport)
        assertTrue(spec.useCoverSharedBounds)
        assertFalse(spec.suppressCoverFade)
    }

    @Test
    fun sharedTransitionVisualSpec_coverFirstVertical_usesPortraitViewport() {
        val spec = resolveVideoSharedTransitionVisualSpec(
            sourceRoute = "home",
            sourceCornerDp = 12,
            playbackIntent = VideoSharedTransitionPlaybackIntent.CoverFirst,
            fullscreen = false,
            autoPortrait = true,
            initialVertical = true,
            isVerticalVideo = true,
            isReturning = false
        )

        assertEquals(VideoSharedTransitionTargetMode.PortraitFullscreen, spec.targetMode)
        assertEquals(0, spec.targetCornerDp)
        assertTrue(spec.fillTargetViewport)
        assertTrue(spec.useCoverSharedBounds)
    }

    @Test
    fun sharedTransitionVisualSpec_immediateLandscapeFullscreen_usesSquareViewport() {
        val spec = resolveVideoSharedTransitionVisualSpec(
            sourceRoute = "partition",
            sourceCornerDp = 10,
            playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
            fullscreen = true,
            autoPortrait = false,
            initialVertical = false,
            isVerticalVideo = false,
            isReturning = false
        )

        assertEquals(VideoSharedTransitionTargetMode.LandscapeFullscreen, spec.targetMode)
        assertEquals(10, spec.sourceCornerDp)
        assertEquals(0, spec.targetCornerDp)
        assertTrue(spec.fillTargetViewport)
    }

    @Test
    fun sharedTransitionVisualSpec_portraitRoute_usesPortraitViewport() {
        val spec = resolveVideoSharedTransitionVisualSpec(
            sourceRoute = "home",
            sourceCornerDp = 12,
            playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
            fullscreen = false,
            autoPortrait = true,
            initialVertical = true,
            isVerticalVideo = true,
            isReturning = false
        )

        assertEquals(VideoSharedTransitionTargetMode.PortraitFullscreen, spec.targetMode)
        assertEquals(0, spec.targetCornerDp)
        assertTrue(spec.fillTargetViewport)
    }

    @Test
    fun sharedTransitionVisualSpec_returnConvergesToRecordedCardCorner() {
        val spec = resolveVideoSharedTransitionVisualSpec(
            sourceRoute = "watch_later",
            sourceCornerDp = 8,
            playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
            fullscreen = true,
            autoPortrait = true,
            initialVertical = true,
            isVerticalVideo = true,
            isReturning = true
        )

        assertEquals(VideoSharedTransitionTargetMode.InlineCover, spec.targetMode)
        assertEquals(8, spec.targetCornerDp)
        assertFalse(spec.fillTargetViewport)
        assertTrue(spec.suppressCoverFade)
    }

    @Test
    fun sharedTransitionPlaybackIntent_mapsClickToPlaySetting() {
        assertEquals(
            VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
            resolveVideoSharedTransitionPlaybackIntent(clickToPlayEnabled = true)
        )
        assertEquals(
            VideoSharedTransitionPlaybackIntent.CoverFirst,
            resolveVideoSharedTransitionPlaybackIntent(clickToPlayEnabled = false)
        )
        assertEquals(
            VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
            resolveVideoSharedTransitionPlaybackIntent(
                clickToPlayEnabled = false,
                forceImmediatePlayback = true
            )
        )
    }

    @Test
    fun detailReturnFade_onlyAppliesToImmediatePlaybackProfile() {
        assertTrue(
            shouldFadePlayerSurfaceOnDetailReturn(
                isLeaving = true,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback
            )
        )
        assertFalse(
            shouldFadePlayerSurfaceOnDetailReturn(
                isLeaving = true,
                playbackIntent = VideoSharedTransitionPlaybackIntent.CoverFirst
            )
        )
        assertFalse(
            shouldFadePlayerSurfaceOnDetailReturn(
                isLeaving = false,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback
            )
        )
        assertTrue(
            shouldUseDetailReturnCoverCrossfade(
                isLeaving = true,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback
            )
        )
        assertFalse(
            shouldUseDetailReturnCoverCrossfade(
                isLeaving = true,
                playbackIntent = VideoSharedTransitionPlaybackIntent.CoverFirst
            )
        )
    }

    @Test
    fun homeVideoCardPropagatesClickToPlayPlaybackIntent() {
        val cardSource = File(
            "src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt"
        ).readText()

        assertTrue(cardSource.contains("resolveVideoSharedTransitionPlaybackIntent("))
        assertTrue(cardSource.contains("SettingsManager.getClickToPlaySync(context)"))
        assertTrue(cardSource.contains("playbackIntent = videoSharedPlaybackIntent"))
    }

    @Test
    fun sharedTransitionSourceCorner_mapsKnownNonHomeSources() {
        assertEquals(10, resolveVideoSharedTransitionSourceCornerDp("dynamic", fallbackCornerDp = 12))
        assertEquals(8, resolveVideoSharedTransitionSourceCornerDp("watch_later", fallbackCornerDp = 12))
        assertEquals(12, resolveVideoSharedTransitionSourceCornerDp("history", fallbackCornerDp = 12))
        assertEquals(12, resolveVideoSharedTransitionSourceCornerDp("partition?from=tab", fallbackCornerDp = 12))
    }
}
