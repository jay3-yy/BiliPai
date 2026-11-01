package com.android.purebilibili.core.ui.transition

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.geometry.Rect
import com.android.purebilibili.core.ui.motion.AppMotionEasing
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VideoSharedTransitionPolicyTest {
    @Test
    fun cardCurveReducesFrontRushAndNearStationaryTailWithoutOvershoot() {
        val curve = resolveVideoCardSharedTransitionSpatialEasing()
        assertTrue(curve.transform(0.1f) < 0.3f)
        assertTrue(curve.transform(0.1f) < AppMotionEasing.Continuity.transform(0.1f))
        assertTrue(curve.transform(0.6f) < 0.95f)
        assertTrue(curve.transform(0.8f) > 0.98f)
        var previous = 0f
        for (step in 0..100) {
            val progress = curve.transform(step / 100f)
            assertTrue(progress in 0f..1f)
            assertTrue(progress >= previous)
            previous = progress
        }
        assertEquals(0f, curve.transform(0f))
        assertEquals(1f, curve.transform(1f))
    }

    @Test
    fun entryAndCancelKeepTheirCurveWhileAutomaticReturnHasItsOwnPacing() {
        val curve = resolveVideoCardSharedTransitionSpatialEasing()
        val hero = resolveVideoHeroMotionSpec(360)
        val content = resolveVideoCardSharedTransitionMotionSpec("home", true)
        assertSame(curve, hero.enterSpatialSpec)
        assertSame(curve, hero.cancelSpatialSpec)
        assertTrue(hero.returnSpatialSpec.transform(.5f) < curve.transform(.5f))
        assertSame(curve, content.enterAlphaEasing)
        assertSame(curve, content.returnAlphaEasing)
        assertSame(LinearEasing, hero.predictiveSeekSpec)
        assertSame(LinearEasing, hero.effectsEasing)
        val reduced = resolveVideoHeroMotionSpec(360, reducedMotion = true)
        assertSame(curve, reduced.returnSpatialSpec)
        assertSame(curve, reduced.cancelSpatialSpec)
    }

    @Test
    fun automaticReturnKeepsAHandoffWindowAndReducesTheNearStationaryTail() {
        val hero = resolveVideoHeroMotionSpec(360)
        fun timeAtProgress(curve: Easing, progress: Float): Float {
            var low = 0f
            var high = 1f
            repeat(24) {
                val time = (low + high) / 2f
                if (curve.transform(time) < progress) low = time else high = time
            }
            return (low + high) / 2f
        }
        val start = timeAtProgress(hero.returnSpatialSpec, VideoCardTransitionVisualTimeline.MEDIA_RETURN_START)
        val end = timeAtProgress(hero.returnSpatialSpec, VideoCardTransitionVisualTimeline.MEDIA_RETURN_END)
        // 接管从时间后半段开始，并保留至少四分之一总时长，不能靠骤然换图压缩尾段。
        assertTrue(start in 0.50f..0.58f)
        assertTrue(end - start >= .25f)
        assertTrue(1f - end in 0.10f..0.20f)
        assertTrue(end > timeAtProgress(hero.enterSpatialSpec, VideoCardTransitionVisualTimeline.MEDIA_RETURN_END))
        var previous = 0f
        for (step in 0..1000) {
            val progress = hero.returnSpatialSpec.transform(step / 1000f)
            assertTrue(progress in previous..1f)
            previous = progress
            val depth = 1f - progress
            val media = resolveVideoCardLiveReturnVisualHandoffAlpha(depth)
            assertEquals(media, resolveVideoCardSourceChromeReturnAlpha(depth), .0001f)
            assertTrue(resolveVideoCardWholeCardReturnHandoffAlpha(depth) + .0001f >= media)
        }
        assertEquals(0f, hero.returnSpatialSpec.transform(0f))
        assertEquals(1f, hero.returnSpatialSpec.transform(1f))
    }
    @Test
    fun heroGeometryUsesDpNotPixelsAndKeepsMissingBoundsFallback() {
        val card = Rect(0f, 0f, 100f, 60f)
        val target = Rect(0f, 0f, 200f, 220f)
        val small = resolveVideoHeroMotionSpec(360, card, card)
        val large = resolveVideoHeroMotionSpec(360, card, target)
        val dense = resolveVideoHeroMotionSpec(360, Rect(0f, 0f, 300f, 180f),
            Rect(0f, 0f, 600f, 660f), density = 3f)
        assertTrue(large.enterDurationMillis > small.enterDurationMillis)
        assertEquals(large, dense)
        assertEquals(360, resolveVideoHeroMotionSpec(360, null, target).enterDurationMillis)
        assertEquals(360, resolveVideoHeroMotionSpec(360, Rect.Zero, target).enterDurationMillis)
        assertEquals(360, resolveVideoHeroMotionSpec(360, card, target, Float.NaN).enterDurationMillis)
    }

    @Test
    fun heroKeepsSpeedPreferencesAndNoAnimationZeroDuration() {
        for (base in listOf(280, 360, 480, 240, 900)) {
            val spec = resolveVideoHeroMotionSpec(base)
            assertEquals(base, spec.enterDurationMillis)
            assertTrue(spec.returnDurationMillis >= 220)
            assertTrue(spec.returnDurationMillis <= spec.enterDurationMillis)
            assertEquals(spec.returnDurationMillis / 2f,
                spec.remainingDuration(.5f, 0f).toFloat(), .5f)
        }
        assertEquals(0, resolveVideoHeroMotionSpec(0).returnDurationMillis)
        assertEquals(299, resolveVideoHeroMotionSpec(360).returnDurationMillis)
        assertEquals(140, resolveVideoHeroMotionSpec(900, reducedMotion = true).enterDurationMillis)
    }

    @Test
    fun heroEffectsAndSeekAreMonotonicAndSpatialPulseIsBounded() {
        val spec = resolveVideoHeroMotionSpec(360)
        for (i in 0..1000) {
            val p = i / 1000f
            assertEquals(p, spec.predictiveSeekSpec.transform(p))
            assertTrue(spec.effectsEasing.transform(p) in 0f..1f)
            assertEquals(1f, resolveVideoHeroLandingScale(p, true))
            assertEquals(1f, resolveVideoHeroLandingScale(p, false))
        }
        assertEquals(1f, resolveVideoHeroLandingScale(0f, true))
        assertEquals(1f, resolveVideoHeroLandingScale(1f, true))
        assertEquals(0f, VideoHeroMotionTokens.LANDING_COMPRESSION)
        assertEquals(1f, resolveVideoHeroLandingScale(-0.01f, true), 0.0001f)
        assertEquals(1f, resolveVideoHeroLandingScale(-0.2f, true), 0.0001f)
        assertEquals(1f, resolveVideoHeroLandingScale(-0.01f, false))
    }

    @Test
    fun legacySharedBoundsSeekAndProgrammaticSpecsAreExplicit() {
        val motion = resolveVideoCardSharedTransitionMotionSpec("home", true)
        val detail = Rect(0f, 0f, 360f, 800f)
        val card = Rect(0f, 0f, 120f, 80f)
        val seek = videoSharedElementBoundsTransformSpec(motion, detail, card) as TweenSpec<*>
        val auto = videoSharedElementBoundsTransformSpec(motion, detail, card,
            interactive = false) as TweenSpec<*>
        assertSame(LinearEasing, seek.easing)
        assertEquals(resolveVideoHeroMotionSpec(motion.durationMillis).returnDurationMillis, auto.durationMillis)
        assertSame(resolveVideoHeroMotionSpec(motion.durationMillis).returnSpatialSpec, auto.easing)
    }

    @Test
    fun videoSharedTransitionAlphaSharesCardSpatialCurve() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true
        )
        val enter = motion.enterAlphaEasing
        val returning = motion.returnAlphaEasing

        assertSame(enter, returning)
        assertEquals(
            resolveVideoCardSharedTransitionSpatialEasing().transform(0.5f),
            enter.transform(0.5f),
            0.001f,
        )
        assertTrue(enter.transform(0.5f) > 0.7f)
    }

    @Test
    fun videoSharedTransitionSpatialEasing_hasBoundedEaseOut() {
        val easing = resolveVideoCardSharedTransitionSpatialEasing()
        // Already responsive at halfway, but leaves meaningful travel for the second half.
        assertTrue(easing.transform(0.5f) in 0.85f..0.90f)
    }

    @Test
    fun videoSharedBoundsUseCardEnterAndLinearSeekableReturn() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true
        )
        val cardBounds = Rect(0f, 0f, 160f, 100f)
        val detailBounds = Rect(0f, 0f, 360f, 800f)

        val enter = videoSharedElementBoundsTransformSpec(motion, cardBounds, detailBounds)
        val returning = videoSharedElementBoundsTransformSpec(motion, detailBounds, cardBounds)

        assertTrue(enter is TweenSpec<*>)
        assertTrue(returning is TweenSpec<*>)
        assertEquals(motion.durationMillis, (enter as TweenSpec<*>).durationMillis)
        assertEquals(motion.durationMillis, (returning as TweenSpec<*>).durationMillis)
        // 进场：卡片专用曲线，先快后慢、无过冲
        assertEquals(
            resolveVideoCardSharedTransitionSpatialEasing().transform(0.4f),
            enter.easing.transform(0.4f),
            0.001f,
        )
        // 返回：Linear 固定时长，预测返回 seek/松手 remainingDuration 可算，避免一闪落位
        assertSame(LinearEasing, returning.easing)
        assertEquals(
            LinearEasing,
            resolveVideoSharedElementSpatialEasing(detailBounds, cardBounds),
        )
        assertEquals(
            resolveVideoCardSharedTransitionSpatialEasing(),
            resolveVideoSharedElementSpatialEasing(cardBounds, detailBounds),
        )
        // settle buffer 仅覆盖主时长后的短收尾，不再为 spring 过冲预留长窗口
        assertEquals(48L, resolveVideoCardReturnSpringSettleBufferMs())
    }

    @Test
    fun videoSharedCoverCacheKeyMatchesTheHomeCardIdentity() {
        assertEquals("cover_BV1ab411_n", resolveVideoSharedCoverCacheKey(" BV1ab411 "))
        assertEquals("cover_BV1ab411_s", resolveVideoSharedCoverCacheKey("BV1ab411", true))
    }

    @Test
    fun videoSharedTransitionDirection_followsBoundsArea() {
        val cardBounds = Rect(0f, 0f, 160f, 100f)
        val detailBounds = Rect(0f, 0f, 360f, 800f)

        assertEquals(
            VideoSharedTransitionDirection.ENTER,
            resolveVideoSharedTransitionDirection(cardBounds, detailBounds)
        )
        assertEquals(
            VideoSharedTransitionDirection.RETURN,
            resolveVideoSharedTransitionDirection(detailBounds, cardBounds)
        )
    }

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
    fun homeVideoTransition_usesCoverAndCardShellWithoutMetadataBounds() {
        val policy = resolveVideoSharedTransitionOwnership(
            sourceRoute = "home",
            coverSharedEnabled = true,
            isQuickReturnLimited = false
        )

        assertTrue(policy.useCoverSharedBounds)
        assertTrue(policy.useCardContainerSharedBounds)
        assertFalse(policy.useMetadataSharedBounds)
    }

    @Test
    fun transparentShellAnchor_ownsHorizontalRelatedAndPartitionCards() {
        val related = resolveVideoSharedTransitionOwnership(
            sourceRoute = "video/BV_A",
            coverSharedEnabled = true,
            isQuickReturnLimited = false
        )
        assertTrue(related.useCoverSharedBounds)
        assertTrue(related.useCardContainerSharedBounds)
        assertFalse(related.useMetadataSharedBounds)

        val partition = resolveVideoSharedTransitionOwnership(
            sourceRoute = "partition",
            coverSharedEnabled = true,
            isQuickReturnLimited = false
        )
        assertTrue(partition.useCoverSharedBounds)
        assertTrue(partition.useCardContainerSharedBounds)
        assertFalse(partition.useMetadataSharedBounds)
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
    fun videoCardShellKey_stripsSeasonSeriesQueryForStableMatch() {
        val shellKey = videoCardShellSharedElementKey(
            bvid = "BV1",
            sourceRoute = "season_series_detail/favorite_season/123?mid=1&title=%E6%B5%8B%E8%AF%95"
        )
        assertEquals("season_series_detail/favorite_season/123", shellKey.sourceRoute)
    }

    @Test
    fun homeCategoryVideoCardSourceKeyKeepsCategoryRoute() {
        val homeCardSource = File(
            "src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt"
        ).readText()
        val sharedKeySource = File(
            "src/main/java/com/android/purebilibili/core/ui/transition/BiliPaiSharedElementKey.kt"
        ).readText()

        assertTrue(homeCardSource.contains("isVideoCardSharedReturnTarget("))
        assertTrue(sharedKeySource.contains("home?category="))
        assertTrue(homeCardSource.contains("videoCardShellReturnChromeAlpha("))

        val shellKey = videoCardShellSharedElementKey(
            bvid = "BV1",
            sourceRoute = "home?category=动画"
        )
        assertEquals("home?category=动画", shellKey.sourceRoute)
    }

    @Test
    fun nonHomeVideoTransition_usesWholeCardShellWithoutMetadataBounds() {
        val policy = resolveVideoSharedTransitionOwnership(
            sourceRoute = "search",
            coverSharedEnabled = true,
            isQuickReturnLimited = false
        )

        assertTrue(policy.useCoverSharedBounds)
        assertTrue(policy.useCardContainerSharedBounds)
        assertFalse(policy.useMetadataSharedBounds)
    }

    @Test
    fun videoCoverRelay_isDisabledToPreserveRealPreviousScreen() {
        assertFalse(shouldUseVideoCoverRelayTransition("partition"))
        assertFalse(shouldUseVideoCoverRelayTransition("partition?tab=1"))
        assertFalse(shouldUseVideoCoverRelayTransition("video/BV_A"))
        assertFalse(shouldUseVideoCoverRelayTransition("home"))
        assertFalse(shouldUseVideoCoverRelayTransition("video"))
        assertFalse(shouldUseVideoCoverRelayTransition(null))
    }

    @Test
    fun videoCardShellSharedBounds_includesHorizontalAnchorSources() {
        assertTrue(shouldUseVideoCardShellSharedBounds("home", transitionEnabled = true))
        assertTrue(shouldUseVideoCardShellSharedBounds("dynamic", transitionEnabled = true))
        assertTrue(shouldUseVideoCardShellSharedBounds("watch_later", transitionEnabled = true))
        assertTrue(shouldUseVideoCardShellSharedBounds("space", transitionEnabled = true))
        assertTrue(shouldUseVideoCardShellSharedBounds("partition", transitionEnabled = true))
        assertTrue(shouldUseVideoCardShellSharedBounds("video/BV_A", transitionEnabled = true))
        assertTrue(shouldUseVideoCardShellSharedBounds("video", transitionEnabled = true))
        assertFalse(shouldUseVideoCardShellSharedBounds("home", transitionEnabled = false))
        assertFalse(shouldUseVideoCardShellSharedBounds(null, transitionEnabled = true))
        assertFalse(shouldSkipVideoCardSharedBoundsMorph("video/BV_A"))
        assertFalse(shouldSkipVideoCardSharedBoundsMorph("partition"))
        assertFalse(shouldSkipVideoCardSharedBoundsMorph("home"))
    }

    @Test
    fun videoCardShellContainerTransform_includesCardSources() {
        assertTrue(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "home",
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertTrue(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "video/BV_A",
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertTrue(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "partition",
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertTrue(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "home?tab=recommend",
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertTrue(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "search",
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertTrue(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "dynamic_detail/123",
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertTrue(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "space/42",
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertFalse(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "settings",
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertFalse(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "video",
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertFalse(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "home",
                transitionEnabled = false,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true
            )
        )
        assertFalse(
            shouldUseVideoCardShellContainerTransform(
                sourceRoute = "home",
                transitionEnabled = true,
                hasSharedTransitionScope = false,
                hasAnimatedVisibilityScope = true
            )
        )
    }

    @Test
    fun cardShellSharedBoundsHelperUsesCardShellKeyNotCoverKey() {
        val helperSource = File(
            "src/main/java/com/android/purebilibili/core/ui/transition/VideoCardShellSharedBounds.kt"
        ).readText()

        assertTrue(helperSource.contains("videoCardShellSharedElementKey("))
        assertFalse(helperSource.contains("videoCoverSharedElementKey("))
    }

    @Test
    fun cardShellSharedBoundsScalesTowardTopNotCenter() {
        val helperSource = File(
            "src/main/java/com/android/purebilibili/core/ui/transition/VideoCardShellSharedBounds.kt"
        ).readText()

        // 统一走 resolveVideoCardSharedBoundsResizeMode，避免各 call-site 默认 Center
        assertTrue(helperSource.contains("fun resolveVideoCardSharedBoundsResizeMode("))
        // 默认详情壳：FillWidth + TopCenter，对齐顶部播放器落点
        assertTrue(helperSource.contains("scaleToBounds(ContentScale.FillWidth, Alignment.TopCenter)"))
        // 竖屏全屏壳：FillBounds/Crop + Center（整卡展开）
        assertTrue(helperSource.contains("scaleToBounds(ContentScale.Crop, Alignment.Center)"))
        // shell 生效时详情侧不得再挂 cover sharedBounds（默认 Center 会往中间飞）
        assertTrue(helperSource.contains("fun shouldAttachVideoDetailCoverSharedBounds("))
        assertFalse(
            shouldAttachVideoDetailCoverSharedBounds(
                coverSharedBoundsEnabled = true,
                detailShellSharedBoundsEnabled = true,
                immediatePlayback = true,
                forceCoverOnlyForReturn = false,
            )
        )
        assertTrue(
            shouldAttachVideoDetailCoverSharedBounds(
                coverSharedBoundsEnabled = true,
                detailShellSharedBoundsEnabled = false,
                immediatePlayback = true,
                forceCoverOnlyForReturn = false,
            )
        )
    }

    @Test
    fun cardShellSharedBoundsKeepsLiveReturnVisibleBeforeSourceCoverFadesIn() {
        assertEquals(
            EnterTransition.None,
            resolveVideoCardShellSharedBoundsEnter(
                role = VideoCardShellSharedBoundsRole.DetailShell,
                transitionDurationMillis = 360,
            )
        )
        // 整壳 Enter 永不延后：封面列表位待命，文字只靠 chrome alpha，消除卸层闪烁。
        assertFalse(shouldDelaySourceCardEnterForLiveReturnMorph("home"))
        assertFalse(shouldDelaySourceCardEnterForLiveReturnMorph("dynamic"))
        assertFalse(shouldDelaySourceCardEnterForLiveReturnMorph("partition"))
        assertFalse(shouldDelaySourceCardEnterForLiveReturnMorph("video/BV_A"))
        assertFalse(
            shouldDelaySourceCardEnterForLiveReturnMorph(
                sourceRoute = "home",
                isQuickReturnFromDetail = true,
            )
        )
        assertEquals(
            EnterTransition.None,
            resolveVideoCardShellSharedBoundsEnter(
                role = VideoCardShellSharedBoundsRole.SourceCard,
                transitionDurationMillis = 360,
                delaySourceCardEnterForLiveReturn = false,
            ),
        )
        // delay 标志为 true 时仍可算出 fadeIn（API 保留）；默认接线 delay=false → None。
        assertTrue(
            resolveVideoCardShellSharedBoundsEnter(
                role = VideoCardShellSharedBoundsRole.SourceCard,
                transitionDurationMillis = 360,
                delaySourceCardEnterForLiveReturn = true,
            ) != EnterTransition.None
        )
        // 旧的飞行 facade 模式仍可请求末段交叉淡入。
        assertEquals(260, resolveVideoCardShellCrossfadeSourceEnterDelayMillis(360))
        assertTrue(
            resolveVideoCardShellSharedBoundsEnter(
                role = VideoCardShellSharedBoundsRole.SourceCard,
                transitionDurationMillis = 360,
                crossfadeSourceContent = true,
            ) != EnterTransition.None
        )
        // 全局整卡反向还原时，真实来源卡片从返回第一帧保持可见。
        assertFalse(
            shouldCrossfadeVideoCardSourceContentOnReturn(
                requested = true,
                isQuickReturnFromDetail = false,
                preferWholeCardReturn = true,
            )
        )
        // ratio 已为 0 → delay ms 为 0
        assertEquals(0, resolveVideoCardShellSourceEnterFadeDelayMillis(360))
        assertTrue(canCoexistLiveSurfaceStableCoverAndChromeOnReturn())
        assertEquals(
            ExitTransition.None,
            resolveVideoCardShellSharedBoundsExit(
                role = VideoCardShellSharedBoundsRole.DetailShell,
            )
        )
        // shell 竖卡进场：源卡不淡出。
        assertEquals(
            ExitTransition.None,
            resolveVideoCardShellSharedBoundsExit(
                role = VideoCardShellSharedBoundsRole.SourceCard,
                fadeOutSourceCardOnOpen = false,
            )
        )
        assertFalse(shouldFadeOutShellSourceCardOnOpen("partition"))
        assertFalse(shouldFadeOutShellSourceCardOnOpen("video/BV_A"))
        assertFalse(shouldFadeOutShellSourceCardOnOpen("home"))
        assertTrue(
            resolveVideoCardShellSharedBoundsExit(
                role = VideoCardShellSharedBoundsRole.SourceCard,
                fadeOutSourceCardOnOpen = true,
                transitionDurationMillis = 360,
            ) != ExitTransition.None
        )
        assertEquals(100, resolveVideoCardShellSourceExitFadeDurationMillis(360))
        val detailSource = File(
            "src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt"
        ).readText()
        assertTrue(detailSource.contains("VideoCardShellSharedBoundsRole.DetailShell"))
        val homeCardSource = File(
            "src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt"
        ).readText()
        // 整卡 alpha=0 会落位黑闪；只对 info 区接 chrome alpha。
        assertTrue(homeCardSource.contains("infoContainerModifier.videoCardShellReturnChromeAlpha"))
        assertTrue(homeCardSource.contains("recordNativeVideoCardLayer("))
        assertTrue(homeCardSource.contains("bvid = video.bvid"))
        assertTrue(homeCardSource.contains("captureNativeVideoCardImage(nativeCardLayer)"))
        assertTrue(homeCardSource.contains("captureNativeCoverOverlayLayer(nativeCoverOverlayLayer)"))
        assertTrue(homeCardSource.contains("nativeCoverOverlayLayer"))
        assertFalse(
            Regex(
                """videoCardShellSharedBoundsOrEmpty\([\s\S]{0,400}?\)\s*\.graphicsLayer"""
            ).containsMatchIn(homeCardSource)
        )
        val chromeHelper = File(
            "src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCardShellReturnChrome.kt"
        ).readText()
        assertTrue(chromeHelper.contains("resolveHomeCardChromeAlphaDuringShellReturnMorph("))
        assertTrue(chromeHelper.contains("resolveSourceOwnershipAtDraw: Boolean = false"))
        val shellHelper = File(
            "src/main/java/com/android/purebilibili/core/ui/transition/VideoCardShellSharedBounds.kt"
        ).readText()
        assertTrue(shellHelper.contains("shouldDelaySourceCardEnterForLiveReturnMorph("))
        assertTrue(shellHelper.contains("isQuickReturnFromDetail"))
    }

    @Test
    fun nonHomeVideoCardsFreezeNativePixelsForReturnMorph() {
        val cardSources = listOf(
            "src/main/java/com/android/purebilibili/feature/space/SpaceScreen.kt",
            "src/main/java/com/android/purebilibili/feature/video/ui/components/RelatedVideoItem.kt",
            "src/main/java/com/android/purebilibili/feature/home/components/cards/HomeStyleSingleColumnVideoCard.kt",
            "src/main/java/com/android/purebilibili/feature/personal/PersonalCardVideoTransition.kt",
            "src/main/java/com/android/purebilibili/feature/watchlater/WatchLaterScreen.kt",
            "src/main/java/com/android/purebilibili/feature/dynamic/components/VideoCards.kt",
        )
        cardSources.forEach { path ->
            val source = File(path).readText()
            assertTrue(
                source.contains("rememberNativeVideoCardSnapshotController("),
                "$path is missing native snapshot capture",
            )
            assertTrue(
                source.contains("nativeCardSnapshot.capture()"),
                "$path does not freeze native pixels after recording position",
            )
        }
    }

    @Test
    fun videoDetailRootProvidesGlobalCardShellSharedBoundsTarget() {
        val detailSource = listOf(
            "VideoDetailTransitionHost.kt",
            "VideoDetailScreenStateHolder.kt"
        ).joinToString("\n") { name ->
            File("src/main/java/com/android/purebilibili/feature/video/screen/$name").readText()
        }

        assertTrue(detailSource.contains("shouldUseVideoCardShellContainerTransform("))
        assertTrue(detailSource.contains("detailShellSharedBoundsEnabled"))
        assertTrue(detailSource.contains("videoCardShellSharedBoundsOrEmpty("))
    }

    @Test
    fun videoCardSharedTransitionMotion_usesStandardCoverPrimaryTimelineByDefault() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true
        )

        assertTrue(motion.enabled)
        assertEquals(360, motion.durationMillis)
        assertEquals(360, motion.fullscreenDurationMillis)
        assertEquals(86, motion.contentDelayMillis)
        assertEquals(209, motion.contentDurationMillis)
        assertEquals(8, motion.contentSlideOffsetDp)
        assertEquals(1f, motion.contentInitialScale, 0.0001f)
        assertSame(motion.enterAlphaEasing, motion.returnAlphaEasing)
        assertEquals(
            resolveVideoCardSharedTransitionSpatialEasing().transform(0.35f),
            motion.enterAlphaEasing.transform(0.35f),
            0.001f,
        )
        assertEquals(
            resolveVideoCardSharedTransitionSpatialEasing().transform(0.5f),
            resolveVideoCardSharedTransitionEnterEasing().transform(0.5f),
            0.001f,
        )
        assertEquals(
            resolveVideoCardSharedTransitionSpatialEasing().transform(0.5f),
            resolveVideoCardSharedTransitionReturnEasing().transform(0.5f),
            0.001f,
        )
        // 景深返回清晰与 morph 同用 Linear，中段 fraction 与时间线性对齐。
        assertEquals(
            0.5f,
            resolveVideoCardTransitionBackgroundReturnClearEasing().transform(0.5f),
            0.001f,
        )
    }

    @Test
    fun videoCardSharedTransitionMotion_preservesFastTimelineOption() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true,
            speedSettings = VideoSharedTransitionSpeedSettings(VideoSharedTransitionSpeed.FAST)
        )

        assertEquals(280, motion.durationMillis)
        assertEquals(280, motion.fullscreenDurationMillis)
        assertEquals(162, motion.contentDurationMillis)
    }

    @Test
    fun videoCardSharedTransitionMotion_keepsMasterTimelineForQuickReturn() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true,
            isQuickReturn = true,
        )

        assertEquals(360, motion.durationMillis)
        assertEquals(0, motion.contentDelayMillis)
        assertEquals(209, motion.contentDurationMillis)
        assertEquals(360, motion.fullscreenDurationMillis)
    }

    @Test
    fun videoCardSharedTransitionMotion_supportsSlowTimelineOption() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "home",
            transitionEnabled = true,
            speedSettings = VideoSharedTransitionSpeedSettings(VideoSharedTransitionSpeed.SLOW)
        )

        assertEquals(480, motion.durationMillis)
        assertEquals(480, motion.fullscreenDurationMillis)
        assertEquals(278, motion.contentDurationMillis)
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

        assertEquals(240, low.durationMillis)
        assertEquals(620, custom.durationMillis)
        assertEquals(620, custom.fullscreenDurationMillis)
        assertEquals(360, custom.contentDurationMillis)
        assertEquals(900, high.durationMillis)
    }

    @Test
    fun videoMetadataSharedTransitionMotion_matchesCoverTimeline() {
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
        assertSame(coverMotion.enterAlphaEasing, metadataMotion.enterAlphaEasing)
        assertSame(coverMotion.returnAlphaEasing, metadataMotion.returnAlphaEasing)
        assertEquals(
            coverMotion.durationMillis,
            resolveVideoMetadataSharedBoundsDurationMillis(metadataMotion),
        )
    }

    @Test
    fun videoCardSources_useShellSharedBoundsWithoutMetadataKeys() {
        val homeCardSource = File(
            "src/main/java/com/android/purebilibili/feature/home/components/cards/VideoCard.kt"
        ).readText()
        val detailInfoSource = File(
            "src/main/java/com/android/purebilibili/feature/video/ui/section/VideoInfoSection.kt"
        ).readText()
        val partitionSource = File(
            "src/main/java/com/android/purebilibili/feature/partition/PartitionScreen.kt"
        ).readText()
        val singleColumnCardSource = File(
            "src/main/java/com/android/purebilibili/feature/home/components/cards/HomeStyleSingleColumnVideoCard.kt"
        ).readText()
        val relatedCardSource = File(
            "src/main/java/com/android/purebilibili/feature/video/ui/components/RelatedVideoItem.kt"
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
        val navHostSource = File(
            "src/main/java/com/android/purebilibili/navigation3/BiliPaiNavDisplayHost.kt"
        ).readText()

        assertTrue(homeCardSource.contains("videoCardShellSharedBoundsOrEmpty("))
        assertFalse(homeCardSource.contains("videoTitleSharedElementKey("))
        assertTrue(detailInfoSource.contains("useCardContainerSharedBounds = useCardContainerSharedBounds"))
        // 分区横卡与相关推荐都由整卡承载 shared bounds，标题/UP/数据与封面一起移动。
        assertTrue(partitionSource.contains("HomeStyleSingleColumnVideoCard("))
        assertTrue(partitionSource.contains("items = state.videos"))
        assertTrue(partitionSource.contains("coverAspectRatio = cardLayout.coverAspectRatio"))
        assertFalse(partitionSource.contains("state.videos.chunked(2)"))
        assertTrue(singleColumnCardSource.contains("videoCardShellSharedBoundsOrEmpty("))
        assertFalse(singleColumnCardSource.contains("videoCoverSharedBoundsOrEmpty("))
        assertFalse(singleColumnCardSource.contains("videoCardShellReturnChromeAlpha("))
        assertTrue(
            singleColumnCardSource.indexOf(".onGloballyPositioned") <
                singleColumnCardSource.indexOf(".videoCardShellSharedBoundsOrEmpty(")
        )
        assertTrue(
            singleColumnCardSource.indexOf(".videoCardShellSharedBoundsOrEmpty(") <
                singleColumnCardSource.indexOf(".padding(AppSpacingTokens.Small)")
        )
        assertTrue(singleColumnCardSource.contains("clipShape = cardShape"))
        assertTrue(singleColumnCardSource.contains("crossfadeSourceContent = true"))
        assertFalse(singleColumnCardSource.contains("followShellMotion = true"))
        assertTrue(singleColumnCardSource.contains("HorizontalVideoCardFrame("))
        assertFalse(singleColumnCardSource.contains("coverAspectRatio = coverAspectRatio"))
        assertFalse(singleColumnCardSource.contains(".height(coverHeight)"))
        assertTrue(relatedCardSource.contains("videoCardShellSharedBoundsOrEmpty("))
        assertTrue(relatedCardSource.contains("crossfadeSourceContent = true"))
        assertTrue(relatedCardSource.contains("sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE"))
        assertTrue(relatedCardSource.contains("sourceChromeSnapshot = VideoCardSourceChromeSnapshot("))
        assertFalse(relatedCardSource.contains("videoCardShellReturnChromeAlpha("))
        assertFalse(relatedCardSource.contains("videoCardShellReturnCoverAlpha("))
        assertFalse(relatedCardSource.contains("followShellMotion = true"))
        assertFalse(partitionSource.contains("videoTitleSharedElementKey("))
        assertTrue(dynamicCardSource.contains("videoCardShellSharedBoundsOrEmpty("))
        assertFalse(dynamicCardSource.contains("videoTitleSharedElementKey("))
        assertTrue(watchLaterSource.contains("videoCardShellSharedBoundsOrEmpty("))
        assertFalse(watchLaterSource.contains("videoTitleSharedElementKey("))
        assertTrue(spaceSource.contains("videoCardShellSharedBoundsOrEmpty("))
        assertFalse(spaceSource.contains("videoTitleSharedElementKey("))
        assertFalse(navHostSource.contains("VideoSharedTransitionBackdropHost("))
    }

    @Test
    fun videoCardSharedTransitionMotion_keepsTimelineForNonHomeSources() {
        val motion = resolveVideoCardSharedTransitionMotionSpec(
            sourceRoute = "search",
            transitionEnabled = true
        )

        assertTrue(motion.enabled)
        assertEquals(360, motion.durationMillis)
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
        assertEquals(16f / 10f, VIDEO_SHARED_COVER_ASPECT_RATIO, 0.0001f)
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
    fun sharedTransitionVisualSpec_returnKeepsPlayerCornerNotListCardCorner() {
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
        // 返回期共享层保持来源卡 8dp，避免落位时再从直角切回圆角。
        assertEquals(12, spec.targetCornerDp)
        assertFalse(spec.fillTargetViewport)
        assertTrue(spec.suppressCoverFade)
        assertEquals(
            8,
            resolveVideoDetailShellOverlayCornerDp(
                visualSpec = spec,
                liveReturnMorph = true,
                isReturningVisualState = true,
            ),
        )
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
    fun detailReturnFade_immediateKeepsLiveSurface_coverFirstStillUsesCover() {
        assertFalse(
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
        // ImmediatePlayback 一镜到底不叠封面；CoverFirst 仍叠封面
        assertFalse(
            shouldUseDetailReturnCoverCrossfade(
                isLeaving = true,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback
            )
        )
        assertTrue(
            shouldUseDetailReturnCoverCrossfade(
                isLeaving = true,
                playbackIntent = VideoSharedTransitionPlaybackIntent.CoverFirst
            )
        )
        assertFalse(
            shouldUseDetailReturnCoverCrossfade(
                isLeaving = false,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback
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

    @Test
    fun livePlayerAndExpandedLayoutSharedBoundsDoNotUseUnsynchronizedSpatialSpring() {
        val playerSource = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/video/ui/section/VideoPlayerSection.kt"),
            File("src/main/java/com/android/purebilibili/feature/video/ui/section/VideoPlayerSection.kt")
        ).first { it.exists() }.readText()
        val tabletSource = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/video/screen/TabletVideoLayout.kt"),
            File("src/main/java/com/android/purebilibili/feature/video/screen/TabletVideoLayout.kt")
        ).first { it.exists() }.readText()
        val largeScreenSource = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/video/screen/LargeScreenVideoLayout.kt"),
            File("src/main/java/com/android/purebilibili/feature/video/screen/LargeScreenVideoLayout.kt")
        ).first { it.exists() }.readText()

        assertTrue(playerSource.contains("livePlayerSharedTransitionMotionSpec"))
        assertTrue(playerSource.contains("videoSharedElementBoundsTransformSpec("))
        assertFalse(
            playerSource.contains("com.android.purebilibili.core.ui.motion.AppMotionTokens.spatialSpec()"),
            "VideoPlayerSection should not use spatialSpec() for shared bounds"
        )

        assertTrue(tabletSource.contains("videoSharedElementBoundsTransformSpec("))
        assertTrue(largeScreenSource.contains("videoSharedElementBoundsTransformSpec("))
    }
}
