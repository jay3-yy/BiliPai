package com.android.purebilibili.feature.video.ui.section

import com.android.purebilibili.core.ui.transition.VideoSharedTransitionTargetMode
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionPlaybackIntent
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoPlayerCoverPolicyTest {

    @Test
    fun firstFrameReveal_usesShortFadeAndStartsHidden() {
        val motion = resolveVideoPlayerRevealMotionSpec()
        assertTrue(motion.surfaceRevealDurationMillis in 100..160)
        val hidden = resolveVideoPlayerSurfaceRevealSpec(
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasStartedSmoothReveal = false,
        )
        assertEquals(0f, hidden.alpha)
    }

    @Test
    fun reducedMotionShortensRevealWithoutRemovingOpaqueCoverUnderlay() {
        val normal = resolveVideoPlayerRevealMotionSpec()
        val reduced = resolveVideoPlayerRevealMotionSpec(reducedMotion = true)
        assertTrue(reduced.surfaceRevealDurationMillis < normal.surfaceRevealDurationMillis)
        assertTrue(shouldHoldEntryCoverUnderlay(
            isFirstFrameRendered = true,
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasStartedSmoothReveal = true,
            isSurfaceRevealSettling = true,
        ))
        assertFalse(shouldHoldEntryCoverUnderlay(
            isFirstFrameRendered = true,
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasStartedSmoothReveal = true,
            isSurfaceRevealSettling = false,
        ))
    }

    @Test
    fun immediatePlaybackCoverBecomesThePlayerUnderlay() {
        assertEquals(
            -1f,
            resolveVideoPlayerCoverLayerZIndex(
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
            ),
        )
        assertEquals(
            100f,
            resolveVideoPlayerCoverLayerZIndex(
                playbackIntent = VideoSharedTransitionPlaybackIntent.CoverFirst,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = true,
            ),
        )
    }

    @Test
    fun verticalVideo_fillsPlayerViewportDuringCoverPhase() {
        assertTrue(
            resolveVideoPlayerEntryPresentationSpec(
                shouldKeepCoverForManualStart = false,
                forceCoverDuringReturnAnimation = false,
                isVerticalVideo = true,
                targetMode = VideoSharedTransitionTargetMode.InlinePlayer,
            ).fillCoverViewport
        )
    }

    @Test
    fun returnCoverSharedBounds_doesNotForceViewportFill() {
        assertFalse(
            resolveVideoPlayerEntryPresentationSpec(
                shouldKeepCoverForManualStart = true,
                forceCoverDuringReturnAnimation = true,
                isVerticalVideo = true,
                targetMode = VideoSharedTransitionTargetMode.InlinePlayer,
            ).fillCoverViewport
        )
    }

    @Test
    fun horizontalManualStartCover_fillsPlayerViewport() {
        assertTrue(
            resolveVideoPlayerEntryPresentationSpec(
                shouldKeepCoverForManualStart = true,
                forceCoverDuringReturnAnimation = false,
                isVerticalVideo = false,
                targetMode = VideoSharedTransitionTargetMode.InlinePlayer,
            ).fillCoverViewport
        )
    }

    @Test
    fun verticalManualStartCover_canFillViewport() {
        assertTrue(
            resolveVideoPlayerEntryPresentationSpec(
                shouldKeepCoverForManualStart = true,
                forceCoverDuringReturnAnimation = false,
                isVerticalVideo = true,
                targetMode = VideoSharedTransitionTargetMode.InlinePlayer,
            ).fillCoverViewport
        )
    }

    @Test
    fun coverBootstrap_reusesFirstFrameButStillStartsFromCoverForReentry() {
        // 已出画后回首页再进：可跳过等首帧，但必须再走 smooth reveal，否则无封面过渡。
        val reused = resolveVideoPlayerCoverBootstrapState(
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasPersistedRenderedFirstFrame = true,
        )
        assertTrue(reused.isFirstFrameRendered)
        assertFalse(reused.hasStartedSmoothReveal)
        assertTrue(
            shouldCommitSmoothCoverReveal(
                isFirstFrameRendered = reused.isFirstFrameRendered,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
            )
        )
        assertTrue(
            shouldHoldEntryCoverUnderlay(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = false,
            )
        )

        val forcedReturn = resolveVideoPlayerCoverBootstrapState(
            forceCoverDuringReturnAnimation = true,
            shouldKeepCoverForManualStart = false,
            hasPersistedRenderedFirstFrame = true,
        )
        assertFalse(forcedReturn.isFirstFrameRendered)
        assertFalse(forcedReturn.hasStartedSmoothReveal)

        val fresh = resolveVideoPlayerCoverBootstrapState(
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasPersistedRenderedFirstFrame = false,
        )
        assertFalse(fresh.isFirstFrameRendered)
        assertFalse(fresh.hasStartedSmoothReveal)
    }

    @Test
    fun coverBootstrap_fullscreenSwitchKeepsCurrentFrameVisible() {
        val fullscreenSwitch = resolveVideoPlayerCoverBootstrapState(
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasPersistedRenderedFirstFrame = false,
            preserveCurrentFrameOnFullscreenChange = true,
            hasCurrentOutputFrame = true,
        )

        assertTrue(fullscreenSwitch.isFirstFrameRendered)
        assertTrue(fullscreenSwitch.hasStartedSmoothReveal)
        assertFalse(
            shouldHoldEntryCoverUnderlay(
                isFirstFrameRendered = fullscreenSwitch.isFirstFrameRendered,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = fullscreenSwitch.hasStartedSmoothReveal,
            )
        )
    }

    @Test
    fun coverBootstrap_fullscreenIntentCannotInventAFrameForANewOutput() {
        val newOutput = resolveVideoPlayerCoverBootstrapState(
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasPersistedRenderedFirstFrame = false,
            preserveCurrentFrameOnFullscreenChange = true,
            hasCurrentOutputFrame = false,
        )
        assertFalse(newOutput.isFirstFrameRendered)
        assertFalse(newOutput.hasStartedSmoothReveal)

        val historicalFrame = resolveVideoPlayerCoverBootstrapState(
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasPersistedRenderedFirstFrame = true,
            preserveCurrentFrameOnFullscreenChange = true,
            hasCurrentOutputFrame = false,
        )
        // 历史首帧可以作为封面揭开的种子，但不能直接跳过揭开和绘制边界。
        assertTrue(historicalFrame.isFirstFrameRendered)
        assertFalse(historicalFrame.hasStartedSmoothReveal)
    }

    @Test
    fun coverBootstrap_currentOutputDoesNotOverrideForcedOrManualCover() {
        for ((forced, manual) in listOf(true to false, false to true)) {
            val state = resolveVideoPlayerCoverBootstrapState(
                forceCoverDuringReturnAnimation = forced,
                shouldKeepCoverForManualStart = manual,
                hasPersistedRenderedFirstFrame = true,
                preserveCurrentFrameOnFullscreenChange = true,
                hasCurrentOutputFrame = true,
            )
            assertFalse(state.isFirstFrameRendered)
            assertFalse(state.hasStartedSmoothReveal)
        }
    }

    @Test
    fun smoothRevealReset_onlyWhenForcedCoverOrManualStart() {
        assertTrue(
            shouldResetSmoothCoverReveal(
                forceCoverDuringReturnAnimation = true,
                shouldKeepCoverForManualStart = false,
            )
        )
        assertTrue(
            shouldResetSmoothCoverReveal(
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = true,
            )
        )
        // 首帧尚未到 / 仅缺 isFirstFrame 时不得清掉揭开标记
        assertFalse(
            shouldResetSmoothCoverReveal(
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
            )
        )
    }

    @Test
    fun immediatePlayback_holdsOpaqueCoverUnderlayUntilFirstFrameReveal() {
        assertTrue(
            shouldHoldEntryCoverUnderlay(
                isFirstFrameRendered = false,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = false,
            )
        )
        assertTrue(
            shouldHoldEntryCoverUnderlay(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = false,
            )
        )
        assertFalse(
            shouldHoldEntryCoverUnderlay(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = true,
            )
        )
        assertFalse(
            shouldEnableCoverImageCrossfade(
                forceCoverDuringReturnAnimation = false,
                holdEntryCoverUnderlay = true,
            )
        )
        assertFalse(
            resolveVideoPlayerCoverMotionSpec(
                forceCoverDuringReturnAnimation = false,
                holdEntryCoverUnderlay = true,
            ).shouldAnimateFade
        )
    }

    @Test
    fun coverFirst_andReturn_keepUnderlayWithoutCrossfade() {
        assertTrue(
            shouldHoldEntryCoverUnderlay(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = true,
                hasStartedSmoothReveal = true,
            )
        )
        assertTrue(
            shouldHoldEntryCoverUnderlay(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = true,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = true,
            )
        )
    }

    @Test
    fun surfaceRevealSettling_keepsOpaqueCoverUnderlayUntilVideoIsOpaque() {
        // 揭开进行中（视频 surface 淡入未完成）封面必须保持不透明垫底：
        // 视频在封面之上淡入，封面同步淡出会让两层半透明叠加透出黑底（亮度凹陷）。
        assertTrue(
            shouldHoldEntryCoverUnderlay(
                isFirstFrameRendered = false,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = false,
                isSurfaceRevealSettling = false,
            )
        )
        assertTrue(
            shouldHoldEntryCoverUnderlay(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = true,
                isSurfaceRevealSettling = true,
            )
        )
        // 揭开完全落定后移除垫底（视频已完全不透明，移除不可见）。
        assertFalse(
            shouldHoldEntryCoverUnderlay(
                isFirstFrameRendered = true,
                forceCoverDuringReturnAnimation = false,
                shouldKeepCoverForManualStart = false,
                hasStartedSmoothReveal = true,
                isSurfaceRevealSettling = false,
            )
        )
    }

    @Test
    fun coverHandoff_waitsForActualOpaqueSurface() {
        for (alpha in listOf(0f, 0.5f, 0.9999f, Float.NaN)) {
            assertFalse(shouldFinishVideoPlayerSurfaceReveal(
                hasStartedSmoothReveal = true,
                isPlayerSurfaceReady = true,
                surfaceAlpha = alpha,
            ))
        }
        assertTrue(shouldFinishVideoPlayerSurfaceReveal(
            hasStartedSmoothReveal = true,
            isPlayerSurfaceReady = true,
            surfaceAlpha = 1f,
        ))
    }

    @Test
    fun coverHandoff_keepsCoverWhenRevealIsCancelledOrSurfaceUnavailable() {
        assertFalse(shouldFinishVideoPlayerSurfaceReveal(
            hasStartedSmoothReveal = false,
            isPlayerSurfaceReady = true,
            surfaceAlpha = 1f,
        ))
        assertFalse(shouldFinishVideoPlayerSurfaceReveal(
            hasStartedSmoothReveal = true,
            isPlayerSurfaceReady = false,
            surfaceAlpha = 1f,
        ))
    }

    @Test
    fun surfaceViewRemovesSettledCoverWithoutFade() {
        val holdUnderlay = shouldHoldEntryCoverUnderlay(
            isFirstFrameRendered = true,
            forceCoverDuringReturnAnimation = false,
            shouldKeepCoverForManualStart = false,
            hasStartedSmoothReveal = true,
            isSurfaceRevealSettling = false,
        )
        val motion = resolveVideoPlayerCoverMotionSpec(
            forceCoverDuringReturnAnimation = false,
            holdEntryCoverUnderlay = holdUnderlay,
            surfaceCanCrossfade = canSurfaceParticipateInAlphaCrossfade(
                usesTextureSurface = false,
            ),
        )

        assertFalse(holdUnderlay)
        assertFalse(motion.shouldAnimateFade)
    }

    @Test
    fun coverExitFadeMatchesSurfaceRevealDuration() {
        // 封面淡出必须与 surface 淡入同源同时长：否则中间的「已不透明 surface + 半透明封面」
        // 窗口就是进场时视频位置「叠一层黑」的来源。
        val reveal = resolveVideoPlayerRevealMotionSpec()
        val cover = resolveVideoPlayerCoverMotionSpec(
            forceCoverDuringReturnAnimation = false,
            holdEntryCoverUnderlay = false,
            surfaceRevealDurationMillis = reveal.surfaceRevealDurationMillis,
            surfaceCanCrossfade = canSurfaceParticipateInAlphaCrossfade(
                usesTextureSurface = true,
            ),
        )
        assertTrue(cover.shouldAnimateFade)
        assertEquals(reveal.surfaceRevealDurationMillis, cover.exitFadeDurationMillis)
        val reduced = resolveVideoPlayerRevealMotionSpec(reducedMotion = true)
        val coverReduced = resolveVideoPlayerCoverMotionSpec(
            forceCoverDuringReturnAnimation = false,
            holdEntryCoverUnderlay = false,
            surfaceRevealDurationMillis = reduced.surfaceRevealDurationMillis,
            surfaceCanCrossfade = canSurfaceParticipateInAlphaCrossfade(
                usesTextureSurface = true,
            ),
        )
        assertTrue(coverReduced.shouldAnimateFade)
        assertEquals(reduced.surfaceRevealDurationMillis, coverReduced.exitFadeDurationMillis)
    }

    @Test
    fun coverRevealPolish_desaturatesDuringBlend_withoutTouchingLuminance() {
        assertEquals(
            1.0f,
            resolveVideoPlayerCoverRevealSaturation(progress = 1f),
        )
        val start = resolveVideoPlayerCoverRevealSaturation(progress = 0f)
        assertTrue(start < 1f && start > 0.8f, "start saturation must be a gentle desaturation, got $start")
        val mid = resolveVideoPlayerCoverRevealSaturation(progress = 0.5f)
        assertTrue(mid > start && mid < 1f, "saturation must ramp monotonically, got $mid")
        // 落定后不挂 colorFilter（封面已被视频完全盖住，零开销）。
        assertNull(resolveVideoPlayerCoverRevealColorFilter(progress = 1f))
        assertNotNull(resolveVideoPlayerCoverRevealColorFilter(progress = 0.5f))
    }

    @Test
    fun autoPlayEnabled_neverUsesManualStartCoverUnderlay() {
        // 自动播放：即便尚未 playWhenReady、进度为 0，也不得进 manual-start 垫封面，
        // 否则会 INVISIBLE surface / 卡住揭开，合集换片或重进详情整页一直封面。
        assertFalse(
            shouldKeepCoverForManualStart(
                playWhenReady = false,
                currentPositionMs = 0L,
                autoPlayEnabled = true,
                hasManualStartPlaybackIntent = false
            )
        )
        assertFalse(
            shouldKeepCoverForManualStart(
                playWhenReady = true,
                currentPositionMs = 0L,
                autoPlayEnabled = true,
                hasManualStartPlaybackIntent = false
            )
        )
        assertFalse(
            shouldKeepCoverForManualStart(
                playWhenReady = false,
                currentPositionMs = 12_000L,
                autoPlayEnabled = true,
                hasManualStartPlaybackIntent = true
            )
        )
    }

    @Test
    fun manualStartCover_staysVisibleForSavedProgressBeforeUserPlay() {
        assertTrue(
            shouldKeepCoverForManualStart(
                playWhenReady = false,
                currentPositionMs = 98_000L,
                autoPlayEnabled = false,
                hasManualStartPlaybackIntent = false
            )
        )
    }

    @Test
    fun manualStartCover_staysVisibleBeforeLoadResetsFreshPlayerFlag() {
        assertTrue(
            shouldKeepCoverForManualStart(
                playWhenReady = true,
                currentPositionMs = 0L,
                autoPlayEnabled = false,
                hasManualStartPlaybackIntent = false
            )
        )
    }

    @Test
    fun manualStartCover_hidesAfterUserPlayIntentEvenWithSavedProgress() {
        assertFalse(
            shouldKeepCoverForManualStart(
                playWhenReady = false,
                currentPositionMs = 98_000L,
                autoPlayEnabled = false,
                hasManualStartPlaybackIntent = true
            )
        )
    }

    @Test
    fun manualStartCover_hidesWhenAutoPlayOverrideStartsPlayback() {
        assertFalse(
            shouldKeepCoverForManualStart(
                playWhenReady = true,
                currentPositionMs = 0L,
                autoPlayEnabled = false,
                hasManualStartPlaybackIntent = true
            )
        )
    }

    @Test
    fun horizontalManualStartCover_usesCoverSharedBoundsWithoutViewportFill() {
        val spec = resolveVideoPlayerEntryPresentationSpec(
            shouldKeepCoverForManualStart = true,
            forceCoverDuringReturnAnimation = false,
            isVerticalVideo = false,
            targetMode = VideoSharedTransitionTargetMode.InlineCover
        )

        assertTrue(spec.coverUsesSharedBounds)
        assertFalse(spec.fillCoverViewport)
        assertTrue(spec.showManualStartPlayButton)
        assertTrue(spec.enableManualStartCoverOverlay)
        assertEquals(VideoPlayerCoverContentScaleMode.Crop, spec.coverContentScaleMode)
    }

    @Test
    fun horizontalLoadingCoverFillsInlineSlotButKeepsFullscreenFit() {
        for (manualStart in listOf(false, true)) {
            for (target in listOf(VideoSharedTransitionTargetMode.InlineCover, VideoSharedTransitionTargetMode.InlinePlayer)) {
                val spec = resolveVideoPlayerEntryPresentationSpec(manualStart, false, false, target)
                assertEquals(VideoPlayerCoverContentScaleMode.Crop, spec.coverContentScaleMode)
            }
            val fullscreen = resolveVideoPlayerEntryPresentationSpec(
                manualStart, false, false, VideoSharedTransitionTargetMode.LandscapeFullscreen,
            )
            assertEquals(VideoPlayerCoverContentScaleMode.Fit, fullscreen.coverContentScaleMode)
        }
    }

    @Test
    fun verticalManualStartCover_usesViewportFillAndCropContentScale() {
        val spec = resolveVideoPlayerEntryPresentationSpec(
            shouldKeepCoverForManualStart = true,
            forceCoverDuringReturnAnimation = false,
            isVerticalVideo = true,
            targetMode = VideoSharedTransitionTargetMode.PortraitFullscreen
        )

        assertFalse(spec.coverUsesSharedBounds)
        assertTrue(spec.fillCoverViewport)
        assertTrue(spec.showManualStartPlayButton)
        assertEquals(VideoPlayerCoverContentScaleMode.Crop, spec.coverContentScaleMode)
    }

    @Test
    fun autoPlaybackCover_doesNotStealSharedBoundsFromPlayerContainer() {
        val spec = resolveVideoPlayerEntryPresentationSpec(
            shouldKeepCoverForManualStart = false,
            forceCoverDuringReturnAnimation = false,
            isVerticalVideo = false,
            targetMode = VideoSharedTransitionTargetMode.InlinePlayer
        )

        assertFalse(spec.coverUsesSharedBounds)
        assertFalse(spec.fillCoverViewport)
        assertFalse(spec.showManualStartPlayButton)
        assertFalse(spec.enableManualStartCoverOverlay)
    }

    @Test
    fun forcedReturnCoverSharedBounds_keepsHomeCoverKeyMatchedDuringReturn() {
        // 返回阶段播放器容器会让出 sharedBounds，强制封面必须承接同一个 cover key。
        assertTrue(
            shouldEnableCoverOverlaySharedBounds(
                useCoverOverlaySharedBounds = true,
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true,
                sourceRoute = com.android.purebilibili.navigation.ScreenRoutes.Home.route
            )
        )
        assertTrue(
            shouldEnableCoverOverlaySharedBounds(
                useCoverOverlaySharedBounds = true,
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true,
                sourceRoute = "${com.android.purebilibili.navigation.ScreenRoutes.Home.route}?from=tab"
            )
        )
    }

    @Test
    fun forcedReturnCoverSharedBounds_stillActiveForNonHomeCardReturnTargets() {
        listOf("dynamic", "search", "history", "favorite", "watch_later", "partition").forEach { route ->
            assertTrue(
                shouldEnableCoverOverlaySharedBounds(
                    useCoverOverlaySharedBounds = true,
                    transitionEnabled = true,
                    hasSharedTransitionScope = true,
                    hasAnimatedVisibilityScope = true,
                    sourceRoute = route
                ),
                "expected forced cover sharedBounds to remain enabled for sourceRoute=$route"
            )
        }
    }

    @Test
    fun coverFirstOverlaySharedBounds_usesSameCardRouteGuardAsReturn() {
        assertTrue(
            shouldEnableCoverOverlaySharedBounds(
                useCoverOverlaySharedBounds = true,
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true,
                sourceRoute = "partition"
            )
        )
        assertFalse(
            shouldEnableCoverOverlaySharedBounds(
                useCoverOverlaySharedBounds = true,
                transitionEnabled = true,
                hasSharedTransitionScope = true,
                hasAnimatedVisibilityScope = true,
                sourceRoute = "settings"
            )
        )
    }

    @Test
    fun forcedReturnCoverSourceRoute_keepsEveryVideoCardReturnTargetRoute() {
        listOf(
            "home",
            "dynamic",
            "search",
            "history",
            "favorite",
            "watch_later",
            "partition",
            "dynamic_detail/123",
            "category/1",
            "season_series_detail/series/1/2/title/owner",
            "space/123"
        ).forEach { route ->
            assertTrue(resolveForcedReturnCoverSharedElementSourceRoute(route) == route)
            assertTrue(resolveForcedReturnCoverSharedElementSourceRoute("$route?from=tab") == route)
        }
        assertTrue(resolveForcedReturnCoverSharedElementSourceRoute("settings") == null)
    }

    @Test
    fun detailReturnCoverCrossfade_showsCoverForCoverFirst_butDoesNotFadeAbsentPlayer() {
        assertTrue(
            com.android.purebilibili.core.ui.transition.shouldUseDetailReturnCoverCrossfade(
                isLeaving = true,
                playbackIntent = com.android.purebilibili.core.ui.transition.VideoSharedTransitionPlaybackIntent.CoverFirst
            )
        )
        assertFalse(
            com.android.purebilibili.core.ui.transition.shouldFadePlayerSurfaceOnDetailReturn(
                isLeaving = true,
                playbackIntent = com.android.purebilibili.core.ui.transition.VideoSharedTransitionPlaybackIntent.CoverFirst
            )
        )
    }

    @Test
    fun detailReturnCoverUsesSingleAlphaTimelineWithoutCoilCrossfade() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt")
            .readText()
        val residentCoverBlock = source
            .substringAfter("val residentCoverImageRequest =")
            .substringBefore("PortraitInlineVideoPlayerHost(")

        assertTrue(residentCoverBlock.contains(".crossfade(false)"))
        assertTrue(residentCoverBlock.contains("resolveVideoDetailReturnMediaFrame("))
        assertTrue(residentCoverBlock.contains(").coverAlpha"))
        assertTrue(residentCoverBlock.contains(".zIndex(1f)"))
        // 返回封面透明度由合成视觉进度驱动：animatedVisibility 进度 + morph 深度进度
        assertTrue(residentCoverBlock.contains("resolveVideoDetailReturnVisualProgress("))
        assertTrue(residentCoverBlock.contains("animatedVisibilityProgress ="))
        assertTrue(residentCoverBlock.contains("detailTransitionProgress.value"))
        assertFalse(residentCoverBlock.contains("coverCrossfadeAlpha"))
    }

    @Test
    fun manualStartPlaybackButton_usesPiliTvAssetWithThemeColor() {
        val source = listOf(
            File("app/src/main/java/com/android/purebilibili/feature/video/ui/section/VideoPlayerSection.kt"),
            File("src/main/java/com/android/purebilibili/feature/video/ui/section/VideoPlayerSection.kt"),
        ).first(File::exists).readText()
        val buttonSource = source
            .substringAfter("if (entryPresentationSpec.showManualStartPlayButton)")
            .substringBefore("// 2. DanmakuView")

        assertTrue(buttonSource.contains("AppIconButton("))
        assertFalse(buttonSource.contains("AppFilledIconButton("))
        assertTrue(buttonSource.contains("painter = manualStartPlayIcon"))
        assertTrue(source.contains("val manualStartPlayIcon = painterResource(R.drawable.ic_pili_tv_play)"))
        assertTrue(buttonSource.contains("tint = AppContentColors.accent()"))
        assertTrue(buttonSource.contains("Modifier.size(60.dp)"))
        assertFalse(buttonSource.contains("Color(0xFF4D5160)"))
        assertFalse(buttonSource.contains("Color.White.copy(alpha = 0.96f)"))
    }
}
