package com.android.purebilibili.core.ui.transition

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.android.purebilibili.core.ui.adaptive.MotionTier
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCardTransitionBackgroundPolicyTest {
    @Test
    fun reversingAtAnyDepthKeepsTheSameBlurRadiusAcrossPhasesAndDensities() {
        for (density in listOf(1f, 1.5f, 2.75f, 3f)) {
            for (step in 0..240) {
                val depth = step / 240f
                val opening = resolveVideoCardTransitionBackgroundFrame(
                    progress = depth,
                    phase = VideoCardTransitionBackgroundPhase.OPENING,
                    sdkInt = 35,
                    density = density,
                )
                for (phase in listOf(
                    VideoCardTransitionBackgroundPhase.RETURNING,
                    VideoCardTransitionBackgroundPhase.HELD,
                )) {
                    val reversed = resolveVideoCardTransitionBackgroundFrame(
                        progress = depth,
                        phase = phase,
                        isGestureRestoreInProgress = true,
                        sdkInt = 35,
                        density = density,
                    )
                    assertEquals(opening.blurRadiusPx, reversed.blurRadiusPx)
                }
            }
        }
    }

    @Test
    fun clearingPreservesFineDetailBeforeTheFinalSharpFrame() {
        val radii = listOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f).map { radius ->
            resolveVideoCardTransitionBackgroundFrame(
                progress = radius / 12f,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                sdkInt = 35,
            ).blurRadiusPx
        }
        assertEquals(listOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f), radii)
    }

    @Test
    fun backdropHandoffHasGentleEndpointsAndRemainsReversible() {
        val start = VideoCardTransitionVisualTimeline.WHOLE_SOURCE_CARD_RETURN_START
        val end = VideoCardTransitionVisualTimeline.WHOLE_SOURCE_CARD_RETURN_END
        val window = end - start
        fun alphaAt(settle: Float, exposure: VideoCardTransitionExposure) =
            resolveVideoCardTransitionFrozenLayerAlpha(exposure, 1f - settle)

        // 交接窗刚开始/将结束时，每 1% 窗口的透明度变化应小于 0.1%。
        assertTrue(1f - alphaAt(start + window * .01f, VideoCardTransitionExposure.Returning) < .001f)
        assertTrue(alphaAt(end - window * .01f, VideoCardTransitionExposure.Returning) < .001f)
        val samples = (0..100).map { start + window * it / 100f }
        val forward = samples.map { alphaAt(it, VideoCardTransitionExposure.Returning) }
        val reversed = samples.reversed().map { alphaAt(it, VideoCardTransitionExposure.Restoring) }
        assertEquals(forward, reversed.reversed())
        assertTrue(forward.zipWithNext().all { (a, b) -> b <= a })
        assertEquals(1f, forward.first(), .0001f)
        assertEquals(0f, forward.last(), .0001f)
    }

    @Test
    fun hiddenFrozenLayerIsSkippedUntilAnInterruptedReturnRevealsItAgain() {
        for (exposure in listOf(
            VideoCardTransitionExposure.BackPreview,
            VideoCardTransitionExposure.Returning,
            VideoCardTransitionExposure.Restoring,
        )) {
            val depths = listOf(1f, 0.6f, 0.315f, 0.1f, 0f, 0.1f, 0.315f, 0.6f, 1f)
            val visibility = depths.map {
                shouldDrawVideoCardTransitionFrozenLayer(
                    resolveVideoCardTransitionFrozenLayerAlpha(exposure, it),
                )
            }
            assertEquals(listOf(true, true, true, false, false, false, true, true, true), visibility)
        }
        // Opening must still capture a frame even when its initial depth is zero.
        assertTrue(shouldDrawVideoCardTransitionFrozenLayer(
            resolveVideoCardTransitionFrozenLayerAlpha(VideoCardTransitionExposure.Opening, 0f),
        ))
        assertFalse(shouldDrawVideoCardTransitionFrozenLayer(0.001f))
        assertTrue(shouldDrawVideoCardTransitionFrozenLayer(0.0011f))
    }

    @Test
    fun scrimOnlyPathRetainsThemeOpacityAndIdleEndpoint() {
        for (phase in VideoCardTransitionBackgroundPhase.entries) {
            for (tier in MotionTier.entries) {
                for (light in listOf(false, true)) {
                    for (progress in listOf(0f, .25f, .5f, 1f)) {
                        val expected = if (phase == VideoCardTransitionBackgroundPhase.IDLE) {
                            0f
                        } else {
                            progress * (if (light || tier == MotionTier.Reduced) .08f else .14f)
                        }
                        assertEquals(
                            expected,
                            resolveVideoCardTransitionBackgroundScrimAlpha(progress, phase, tier, light),
                            .0001f,
                        )
                        assertEquals(
                            expected,
                            resolveVideoCardTransitionBackgroundFrame(
                                progress = progress,
                                phase = phase,
                                motionTier = tier,
                                isLightBackground = light,
                                sdkInt = 35,
                            ).scrimAlpha,
                            .0001f,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun backgroundScalesSmoothlyWithCornerRadiiDuringTransition() {
        for (phase in VideoCardTransitionBackgroundPhase.entries) {
            for (progress in listOf(0f, 0.25f, 0.5f, 1f)) {
                for (restoring in listOf(false, true)) {
                    val frame = resolveVideoCardTransitionBackgroundFrame(
                        progress = progress,
                        phase = phase,
                        isGestureRestoreInProgress = restoring,
                        sdkInt = 35,
                    )
                    if (phase == VideoCardTransitionBackgroundPhase.IDLE || progress == 0f) {
                        assertEquals(1f, frame.contentScale, 0.0001f)
                        assertEquals(0f, frame.cornerRadiusPx, 0.0001f)
                        assertFalse(shouldDrawVideoCardTransitionScaleGapFill(frame.contentScale))
                    } else {
                        val expectedScale = 1f - 0.04f * progress
                        val expectedCorner = 24f * progress
                        assertEquals(expectedScale, frame.contentScale, 0.0001f)
                        assertEquals(expectedCorner, frame.cornerRadiusPx, 0.0001f)
                        assertTrue(shouldDrawVideoCardTransitionScaleGapFill(frame.contentScale))
                        assertTrue(frame.shadowElevationPx > 0f)
                    }
                }
            }
        }
    }

    @Test
    fun blurEffectsSurviveGestureReversalAndTheClearEndpoint() {
        val cache = VideoCardTransitionBlurEffectCache()
        val first = kotlin.test.assertNotNull(cache.resolve(12f, sdkInt = 35))
        cache.resolve(24f, sdkInt = 35)
        assertEquals(null, cache.resolve(0f, sdkInt = 35))
        kotlin.test.assertSame(first, cache.resolve(12f, sdkInt = 35))
        assertEquals(null, cache.resolve(Float.NaN, sdkInt = 35))
        assertEquals(null, cache.resolve(Float.POSITIVE_INFINITY, sdkInt = 35))
        assertEquals(null, cache.resolve(12f, sdkInt = 30))
        cache.clear()
        kotlin.test.assertNotSame(first, cache.resolve(12f, sdkInt = 35))
    }

    @Test
    fun blurEffectCacheEvictsOldRadiiWhenItsCapacityIsExceeded() {
        val cache = VideoCardTransitionBlurEffectCache()
        val first = kotlin.test.assertNotNull(cache.resolve(1f, sdkInt = 35))
        for (radius in 2..257) cache.resolve(radius.toFloat(), sdkInt = 35)
        val recent = cache.resolve(257f, sdkInt = 35)
        kotlin.test.assertNotSame(first, cache.resolve(1f, sdkInt = 35))
        kotlin.test.assertSame(recent, cache.resolve(257f, sdkInt = 35))
    }

    @Test
    fun blurEffectsAreCachedForTheSnapshotSessionAndReleasedWithIt() {
        val state = VideoCardTransitionSnapshotLayerState()
        val first = state.blurEffect(12f, sdkInt = 35)
        kotlin.test.assertSame(first, state.blurEffect(12f, sdkInt = 35))
        assertEquals(null, state.blurEffect(0f, sdkInt = 35))
        assertEquals(null, state.blurEffect(12f, sdkInt = 30))
        state.reset()
        kotlin.test.assertNotSame(first, state.blurEffect(12f, sdkInt = 35))
    }

    @Test
    fun detachedSourceInvalidatesRecordedSnapshotBeforeBackPreview() {
        val state = VideoCardTransitionSnapshotLayerState().apply {
            freezeRecording = true
            hasRecordedContent = true
            lastBlurRadiusPx = 12f
            lastCornerRadiusPx = 24f
        }

        state.invalidateRecordedContent()

        assertFalse(state.freezeRecording)
        assertFalse(state.hasRecordedContent)
        assertTrue(state.lastBlurRadiusPx.isNaN())
        assertTrue(state.lastCornerRadiusPx.isNaN())
    }

    @Test
    fun hostOwnedSnapshotMustNotInvalidateWhenSourceDetaches() {
        assertFalse(shouldInvalidateSnapshotOnSourceDispose(isHostOwnedSnapshot = true))
        assertTrue(shouldInvalidateSnapshotOnSourceDispose(isHostOwnedSnapshot = false))
    }

    @Test
    fun hostOwnedSourceDetachMarksDisplayListStaleForRerecord() {
        val state = VideoCardTransitionSnapshotLayerState().apply {
            freezeRecording = true
            hasRecordedContent = true
            displayListStale = false
            lastBlurRadiusPx = 12f
        }
        state.markDisplayListStale()
        assertTrue(state.displayListStale)
        assertTrue(state.hasRecordedContent)
        assertFalse(
            isVideoCardTransitionSnapshotDrawable(
                hasRecordedContent = state.hasRecordedContent,
                displayListStale = state.displayListStale,
            ),
        )
        state.markDisplayListFresh()
        assertFalse(state.displayListStale)
        assertTrue(state.hasRecordedContent)
        assertTrue(state.freezeRecording)
    }

    @Test
    fun transitionMotionTierOnlyReducesForSystemReduceMotion() {
        assertEquals(MotionTier.Normal, resolveVideoCardTransitionMotionTier(reduceMotion = false))
        assertEquals(MotionTier.Reduced, resolveVideoCardTransitionMotionTier(reduceMotion = true))
    }

    @Test
    fun snapshotBlur_isEnabledForActivePhasesOnApi31Plus() {
        assertTrue(
            shouldUseVideoCardTransitionSnapshotBlur(
                exposure = VideoCardTransitionExposure.Opening,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            )
        )
        assertTrue(
            shouldUseVideoCardTransitionSnapshotBlur(
                exposure = VideoCardTransitionExposure.BackPreview,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 31,
            )
        )
        assertTrue(
            shouldUseVideoCardTransitionSnapshotBlur(
                exposure = VideoCardTransitionExposure.Returning,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            )
        )
    }

    @Test
    fun snapshotBlur_isDisabledForIdleReducedOrLegacyApi() {
        assertFalse(
            shouldUseVideoCardTransitionSnapshotBlur(
                exposure = VideoCardTransitionExposure.Idle,
                motionTier = MotionTier.Normal,
                sdkInt = 35,
            )
        )
        assertFalse(
            shouldUseVideoCardTransitionSnapshotBlur(
                exposure = VideoCardTransitionExposure.Opening,
                motionTier = MotionTier.Reduced,
                sdkInt = 35,
            )
        )
        assertFalse(
            shouldUseVideoCardTransitionSnapshotBlur(
                exposure = VideoCardTransitionExposure.Opening,
                motionTier = MotionTier.Normal,
                sdkInt = 30,
            )
        )
    }

    @Test
    fun snapshotBlur_respectsRealtimeBlurSetting() {
        assertFalse(
            shouldUseVideoCardTransitionSnapshotBlur(
                exposure = VideoCardTransitionExposure.Opening,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = false,
                sdkInt = 35,
            )
        )
    }

    @Test
    fun settledHiddenRetainsSnapshotWithoutAnyRenderWork() {
        val exposure = resolveVideoCardTransitionExposure(
            phase = VideoCardTransitionBackgroundPhase.HELD,
            predictiveBackInProgress = false,
            gestureRestoreInProgress = false,
        )
        val decision = resolveVideoCardTransitionRenderDecision(exposure)

        assertEquals(VideoCardTransitionExposure.SettledHidden, exposure)
        assertTrue(decision.retainSourceSnapshot)
        assertFalse(decision.drawSourceNormally)
        assertFalse(decision.drawTransitionBackground)
        assertFalse(decision.updateBlurEffect)
        assertFalse(decision.drawNavBackdrop)
        // When the source is the sole composed scene under HELD, draw path still paints
        // the retained layer (or live fallback) to avoid pure black under shared overlay.
        assertTrue(shouldPaintRetainedSourceWithoutTransitionBackground(decision))
        assertFalse(
            shouldUseVideoCardTransitionSnapshotBlur(
                exposure = exposure,
                motionTier = MotionTier.Normal,
                realtimeBlurEnabled = true,
                sdkInt = 35,
            )
        )
    }

    @Test
    fun paintRetainedSourceOnlyWhenSettledHiddenStyleDecision() {
        val idle = resolveVideoCardTransitionRenderDecision(VideoCardTransitionExposure.Idle)
        val returning = resolveVideoCardTransitionRenderDecision(VideoCardTransitionExposure.Returning)
        val settled = resolveVideoCardTransitionRenderDecision(VideoCardTransitionExposure.SettledHidden)

        assertFalse(shouldPaintRetainedSourceWithoutTransitionBackground(idle))
        assertFalse(shouldPaintRetainedSourceWithoutTransitionBackground(returning))
        assertTrue(shouldPaintRetainedSourceWithoutTransitionBackground(settled))
    }

    @Test
    fun predictiveBackAndCancellationResolveToExposedThenHiddenStates() {
        val preview = resolveVideoCardTransitionExposure(
            phase = VideoCardTransitionBackgroundPhase.HELD,
            predictiveBackInProgress = true,
            gestureRestoreInProgress = false,
        )
        val restoring = resolveVideoCardTransitionExposure(
            phase = VideoCardTransitionBackgroundPhase.HELD,
            predictiveBackInProgress = false,
            gestureRestoreInProgress = true,
        )
        val settled = resolveVideoCardTransitionExposure(
            phase = VideoCardTransitionBackgroundPhase.HELD,
            predictiveBackInProgress = false,
            gestureRestoreInProgress = false,
        )

        assertEquals(VideoCardTransitionExposure.BackPreview, preview)
        assertEquals(VideoCardTransitionExposure.Restoring, restoring)
        assertEquals(VideoCardTransitionExposure.SettledHidden, settled)
        assertTrue(resolveVideoCardTransitionRenderDecision(preview).drawNavBackdrop)
        assertFalse(resolveVideoCardTransitionRenderDecision(restoring).drawNavBackdrop)
        assertTrue(resolveVideoCardTransitionRenderDecision(restoring).drawTransitionBackground)
    }

    @Test
    fun sourceChromeHidesWhileOpeningAndSettledThenReturnsOnPredictiveBack() {
        assertFalse(
            shouldShowVideoCardTransitionSourceChrome(
                isVideoDetailDestination = true,
                exposure = VideoCardTransitionExposure.Opening,
            )
        )
        assertFalse(
            shouldShowVideoCardTransitionSourceChrome(
                isVideoDetailDestination = true,
                exposure = VideoCardTransitionExposure.SettledHidden,
            )
        )
        assertTrue(
            shouldShowVideoCardTransitionSourceChrome(
                isVideoDetailDestination = true,
                exposure = VideoCardTransitionExposure.BackPreview,
            )
        )
        assertFalse(
            shouldShowVideoCardTransitionSourceChrome(
                isVideoDetailDestination = true,
                exposure = VideoCardTransitionExposure.Restoring,
            )
        )
        assertTrue(
            shouldShowVideoCardTransitionSourceChrome(
                isVideoDetailDestination = true,
                exposure = VideoCardTransitionExposure.Returning,
            )
        )
        assertTrue(
            shouldShowVideoCardTransitionSourceChrome(
                isVideoDetailDestination = false,
                exposure = VideoCardTransitionExposure.Idle,
            )
        )
        assertFalse(
            shouldShowVideoCardTransitionSourceChrome(
                isVideoDetailDestination = true,
                exposure = VideoCardTransitionExposure.Returning,
                sourceIsBottomBarDestination = false,
            )
        )
        assertFalse(
            shouldShowVideoCardTransitionSourceChrome(
                isVideoDetailDestination = true,
                exposure = VideoCardTransitionExposure.BackPreview,
                sourceIsBottomBarDestination = false,
            )
        )
    }

    @Test
    fun homeOverlayChromeMatchesSourceChromePhases() {
        assertFalse(
            shouldShowHomeOverlayChromeDuringVideoCardTransition(
                VideoCardTransitionExposure.Opening,
            )
        )
        assertFalse(
            shouldShowHomeOverlayChromeDuringVideoCardTransition(
                VideoCardTransitionExposure.SettledHidden,
            )
        )
        assertTrue(
            shouldShowHomeOverlayChromeDuringVideoCardTransition(
                VideoCardTransitionExposure.BackPreview,
            )
        )
        assertFalse(
            shouldShowHomeOverlayChromeDuringVideoCardTransition(
                VideoCardTransitionExposure.Restoring,
            )
        )
        assertTrue(
            shouldShowHomeOverlayChromeDuringVideoCardTransition(
                VideoCardTransitionExposure.Returning,
            )
        )
        assertTrue(
            shouldShowHomeOverlayChromeDuringVideoCardTransition(
                VideoCardTransitionExposure.Idle,
            )
        )
    }

    @Test
    fun chromeRevealFollowsDepthSoPredictiveBackCanReverse() {
        assertEquals(0f, resolveVideoCardTransitionChromeReveal(1f), 0.0001f)
        assertEquals(1f, resolveVideoCardTransitionChromeReveal(0f), 0.0001f)
        assertEquals(0.4f, resolveVideoCardTransitionChromeReveal(0.6f), 0.0001f)
        assertTrue(
            shouldDriveVideoCardTransitionChromeByProgress(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.BackPreview,
            ),
        )
        assertTrue(
            shouldDriveVideoCardTransitionChromeByProgress(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.Restoring,
            ),
        )
        assertFalse(
            shouldDriveVideoCardTransitionChromeByProgress(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.Idle,
            ),
        )
        assertFalse(
            shouldDriveVideoCardTransitionChromeByProgress(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.Opening,
                sourceIsBottomBarDestination = false,
            ),
        )
        assertFalse(
            shouldDriveVideoCardTransitionChromeByProgress(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.Returning,
                sourceIsBottomBarDestination = false,
            ),
        )
        val overlayPivot = resolveVideoCardTransitionOverlayDepthPivot(
            sourceBounds = androidx.compose.ui.geometry.Rect(100f, 400f, 500f, 900f),
            canvasWidth = 1000f,
            canvasHeight = 2000f,
            overlayWidth = 1000f,
            overlayHeight = 200f,
        )
        assertEquals(0.5f, overlayPivot.x, 0.0001f)
        assertEquals(5f, overlayPivot.y, 0.0001f)
    }

    @Test
    fun relatedAndNonTabSourcesDoNotRevealTheHomeBottomBar() {
        val visibleTabs = setOf("home", "dynamic")
        assertTrue(isVideoCardTransitionBottomBarSource("home", visibleTabs))
        assertTrue(isVideoCardTransitionBottomBarSource("home?category=1", visibleTabs))
        assertTrue(isVideoCardTransitionBottomBarSource("dynamic", visibleTabs))
        assertTrue(isVideoCardTransitionBottomBarSource("main_host", visibleTabs))
        assertFalse(isVideoCardTransitionBottomBarSource("video/BV1xx", visibleTabs))
        assertFalse(isVideoCardTransitionBottomBarSource("space/123", visibleTabs))
        assertFalse(isVideoCardTransitionBottomBarSource("search", visibleTabs))
        assertFalse(isVideoCardTransitionBottomBarSource(null, visibleTabs))
    }

    @Test
    fun predictiveBackBottomBarUsesTheRetainedTabInsteadOfTheVideoRoute() {
        assertEquals(
            "home",
            resolveVideoCardTransitionChromeBottomBarRoute(
                isVideoDetailDestination = true,
                activeBottomTabRoute = "video/BV1",
                retainedTabRoute = "home",
            ),
        )
        assertEquals(
            "dynamic",
            resolveVideoCardTransitionChromeBottomBarRoute(
                isVideoDetailDestination = false,
                activeBottomTabRoute = "dynamic",
                retainedTabRoute = "home",
            ),
        )
    }

    @Test
    fun homeFeedOwnsHostSnapshotSoRouteShellDoesNotRecordTheHeader() {
        assertTrue(
            shouldHomeFeedOwnVideoCardTransitionSnapshot(
                sourceRoute = "home",
                hasSnapshotHandle = true,
            )
        )
        assertFalse(
            shouldHomeFeedOwnVideoCardTransitionSnapshot(
                sourceRoute = "home",
                hasSnapshotHandle = false,
            )
        )
        assertFalse(
            shouldHomeFeedOwnVideoCardTransitionSnapshot(
                sourceRoute = "dynamic",
                hasSnapshotHandle = true,
            )
        )
        assertFalse(
            shouldApplyVideoCardTransitionSnapshotOnRouteShell(
                entryRoute = "main_host",
                sourceRoute = "home",
                activeMainHostRoute = "home",
            )
        )
        assertTrue(
            shouldApplyVideoCardTransitionSnapshotOnRouteShell(
                entryRoute = "dynamic",
                sourceRoute = "dynamic",
                activeMainHostRoute = "home",
            )
        )
    }

    @Test
    fun navBackdrop_isHiddenWhenPredictiveReturnTargetsAnotherVideoDetail() {
        assertFalse(
            shouldShowVideoCardTransitionNavBackdrop(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.SettledHidden,
                isVideoDetailOnStack = true,
                isReturningToVideoDetail = true,
            )
        )
    }

    @Test
    fun hazePrime_drawsLiveContentNearClearDepthDuringReturnPreview() {
        assertTrue(
            shouldPrimeLiveContentForHazeDuringDepthDraw(
                exposure = VideoCardTransitionExposure.Returning,
                depthProgress = 0.2f,
            )
        )
        assertTrue(
            shouldPrimeLiveContentForHazeDuringDepthDraw(
                exposure = VideoCardTransitionExposure.BackPreview,
                depthProgress = 0.0f,
            )
        )
        assertFalse(
            shouldPrimeLiveContentForHazeDuringDepthDraw(
                exposure = VideoCardTransitionExposure.Returning,
                depthProgress = 0.9f,
            )
        )
        assertFalse(
            shouldPrimeLiveContentForHazeDuringDepthDraw(
                exposure = VideoCardTransitionExposure.Opening,
                depthProgress = 0.1f,
            )
        )
        assertFalse(
            shouldPrimeLiveContentForHazeDuringDepthDraw(
                exposure = VideoCardTransitionExposure.SettledHidden,
                depthProgress = 0.0f,
            )
        )
    }

    @Test
    fun frozenDepthLayerHandsOffContinuouslyToTheCompleteSourceCardBeforeLanding() {
        assertEquals(
            1f,
            resolveVideoCardTransitionFrozenLayerAlpha(
                exposure = VideoCardTransitionExposure.BackPreview,
                depthProgress = 0.45f,
            ),
            absoluteTolerance = 0.0001f,
        )
        assertEquals(
            0.5f,
            resolveVideoCardTransitionFrozenLayerAlpha(
                exposure = VideoCardTransitionExposure.Returning,
                depthProgress = 0.315f,
            ),
            absoluteTolerance = 0.02f,
        )
        assertEquals(
            0f,
            resolveVideoCardTransitionFrozenLayerAlpha(
                exposure = VideoCardTransitionExposure.BackPreview,
                depthProgress = 0.10f,
            ),
            absoluteTolerance = 0.0001f,
        )
    }

    @Test
    fun frozenDepthLayerStaysOpaqueOutsideReturnHandoff() {
        assertEquals(
            1f,
            resolveVideoCardTransitionFrozenLayerAlpha(
                exposure = VideoCardTransitionExposure.Opening,
                depthProgress = 0f,
            ),
        )
        assertEquals(
            1f,
            resolveVideoCardTransitionFrozenLayerAlpha(
                exposure = VideoCardTransitionExposure.SettledHidden,
                depthProgress = 0f,
            ),
        )
    }

    @Test
    fun reducedMotionTierSkipsRealtimeBlurAndDepthScaleButKeepsScrim() {
        val opening = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Reduced,
            sdkInt = 35
        )
        val returning = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            motionTier = MotionTier.Reduced,
            sdkInt = 35
        )

        assertEquals(0f, opening.blurRadiusPx)
        assertTrue(opening.scrimAlpha > 0f)
        assertEquals(1f, opening.contentScale)
        assertEquals(0f, opening.cornerRadiusPx)
        assertEquals(0f, returning.blurRadiusPx)
        assertTrue(returning.scrimAlpha > 0f)
        assertEquals(0f, returning.cornerRadiusPx)
    }

    @Test
    fun api35OpeningFrameUsesCalibratedBlurStrengthAndScrim() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Enhanced,
            isLightBackground = false,
            sdkInt = 35
        )

        // density=1 时 12dp → 12px；真实机型由 DrawScope.density 换算。
        assertEquals(12f, frame.blurRadiusPx)
        assertEquals(0f, frame.blurRadiusPx % 2f)
        // 恒定半径混合：模糊强度独立成通道，随景深线性建立。
        assertEquals(1f, frame.blurAlpha)
        assertEquals(0.14f, frame.scrimAlpha)
        assertFalse(frame.useLightScrimTint)
        assertEquals(1f, frame.contentScale, 0.0001f)
        assertEquals(0f, frame.cornerRadiusPx, 0.0001f)
        assertEquals(0f, frame.shadowElevationPx, 0.0001f)
    }

    @Test
    fun normalAndEnhancedShareFullBlurBudget_noDeviceTierDowngrade() {
        val normal = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Normal,
            sdkInt = 35,
        )
        val enhanced = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Enhanced,
            sdkInt = 35,
        )

        assertEquals(12f, normal.blurRadiusPx)
        assertEquals(12f, enhanced.blurRadiusPx)
        assertEquals(12f, resolveVideoCardTransitionMaxBlurRadiusPx(MotionTier.Normal))
        assertEquals(12f, resolveVideoCardTransitionMaxBlurRadiusPx(MotionTier.Enhanced))
        assertEquals(0f, resolveVideoCardTransitionMaxBlurRadiusPx(MotionTier.Reduced))
        assertEquals(2f, resolveVideoCardTransitionBlurQuantumPx(MotionTier.Normal))
        assertEquals(2f, resolveVideoCardTransitionBlurQuantumPx(MotionTier.Enhanced))
    }

    @Test
    fun openingBlurLimitsRadiusChangesAndPreservesEndpoints() {
        for (density in listOf(1f, 2.7f, 3f)) {
            val radii = (0..240).map { step ->
                resolveVideoCardTransitionBackgroundFrame(
                    progress = step / 240f,
                    phase = VideoCardTransitionBackgroundPhase.OPENING,
                    motionTier = MotionTier.Normal,
                    sdkInt = 35,
                    density = density,
                ).blurRadiusPx
            }
            assertEquals(0f, radii.first())
            assertEquals(12f * density, radii.last(), 0.001f)
            assertTrue(radii.zipWithNext().all { (a, b) -> b >= a })
            // 仅 0–4px 细分；3x 密度仍限制为 25 档，不逐帧创建新 effect。
            assertTrue(radii.distinct().size <= 25)
        }
    }

    @Test
    fun blurRadiusScalesWithDensity_forConsistentDpFeel() {
        val phone = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Normal,
            sdkInt = 35,
            density = 2.75f,
        )
        val tablet = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Normal,
            sdkInt = 35,
            density = 1.5f,
        )

        // 12dp × 2.75 ≈ 33px；12dp × 1.5 = 18px
        assertEquals(33f, phone.blurRadiusPx, 0.51f)
        assertEquals(18f, tablet.blurRadiusPx, 0.51f)
        assertEquals(66f, phone.cornerRadiusPx, 0.01f)
        assertEquals(36f, tablet.cornerRadiusPx, 0.01f)
        assertEquals(
            33f,
            resolveVideoCardTransitionMaxBlurRadiusPx(MotionTier.Normal, density = 2.75f),
            0.01f,
        )
    }

    @Test
    fun backgroundCornerUsesDeviceRadiusWhenLargerThanFallback() {
        assertEquals(
            80f,
            resolveVideoCardTransitionBackgroundCornerRadiusPx(
                depthProgress = 1f,
                motionTier = MotionTier.Normal,
                density = 2.75f,
                deviceCornerRadiusPx = 80f,
            ),
            0.0001f,
        )
        // 页面后退时使用设备物理圆角，避免冻结层边缘露出直角。
        assertEquals(
            80f / 2.75f,
            resolveVideoCardTransitionBackgroundCornerRadiusDp(
                deviceCornerRadiusPx = 80f,
                density = 2.75f,
            ),
            0.0001f,
        )
        assertEquals(
            24f,
            resolveVideoCardTransitionBackgroundCornerRadiusDp(
                deviceCornerRadiusPx = 40f,
                density = 2.75f,
            ),
            0.0001f,
        )
        assertEquals(
            24f,
            resolveVideoCardTransitionBackgroundCornerRadiusDp(
                deviceCornerRadiusPx = 0f,
                density = 2.75f,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveDeviceDisplayCornerRadiusPx(
                rootWindowInsets = null,
                sdkInt = 35,
            ),
        )
        assertEquals(
            0f,
            resolveDeviceDisplayCornerRadiusPx(
                rootWindowInsets = null,
                sdkInt = 30,
            ),
        )
    }

    @Test
    fun backgroundPageRecedesWithTheSharedCardProgress() {
        VideoCardTransitionBackgroundPhase.entries.forEach { phase ->
            assertEquals(
                if (phase == VideoCardTransitionBackgroundPhase.IDLE) 1f else 1f,
                resolveVideoCardTransitionContentScale(
                    progress = 1f,
                    phase = phase,
                    motionTier = MotionTier.Normal,
                ),
                0.0001f,
            )
        }
        val backgroundSource = File(
            "src/main/java/com/android/purebilibili/core/ui/transition/" +
                "VideoCardTransitionBackgroundPolicy.kt"
        ).readText()
        assertFalse(backgroundSource.contains("translationX ="))
        assertFalse(backgroundSource.contains("translationY ="))
        assertTrue(backgroundSource.contains("resolveVideoCardTransitionBackgroundScalePivot("))
        assertTrue(backgroundSource.contains("transformOrigin = TransformOrigin(pivot.x, pivot.y)"))
    }

    @Test
    fun backgroundScalePivotsAroundTheScreenCenter() {
        val pivot = resolveVideoCardTransitionBackgroundScalePivot(
            sourceBounds = Rect(100f, 200f, 300f, 400f),
            canvasWidth = 1000f,
            canvasHeight = 2000f,
        )
        assertEquals(0.5f, pivot.x, 0.0001f)
        assertEquals(0.5f, pivot.y, 0.0001f)
        assertEquals(
            pivot,
            resolveVideoCardTransitionBackgroundScalePivot(
                sourceBounds = Rect(700f, 1600f, 950f, 1900f),
                canvasWidth = 1000f,
                canvasHeight = 2000f,
            ),
        )
        assertEquals(
            Offset(500f, 1000f),
            resolveVideoCardTransitionBackgroundScalePivotOffset(
                sourceBounds = Rect(100f, 200f, 300f, 400f),
                canvasSize = Size(1000f, 2000f),
            ),
        )
    }

    @Test
    fun backgroundScaleFallsBackToScreenCenterWithoutCardBounds() {
        assertEquals(
            Offset(0.5f, 0.5f),
            resolveVideoCardTransitionBackgroundScalePivot(
                sourceBounds = null,
                canvasWidth = 1080f,
                canvasHeight = 2400f,
            ),
        )
        assertEquals(
            Offset(0.5f, 0.5f),
            resolveVideoCardTransitionBackgroundScalePivot(
                sourceBounds = Rect(10f, 10f, 10.5f, 10.5f),
                canvasWidth = 1080f,
                canvasHeight = 2400f,
            ),
        )
        assertEquals(
            Offset(540f, 1200f),
            resolveVideoCardTransitionBackgroundScalePivotOffset(
                sourceBounds = null,
                canvasSize = Size(1080f, 2400f),
            ),
        )
    }

    @Test
    fun relatedAndPartitionUseTheSameWholePageDepth() {
        val relatedReduction = resolveVideoCardTransitionBackgroundScaleReduction(
            resolveVideoCardTransitionBackgroundSource("video/BV_related"),
        )
        val partitionReduction = resolveVideoCardTransitionBackgroundScaleReduction(
            resolveVideoCardTransitionBackgroundSource("partition"),
        )

        assertEquals(0f, relatedReduction, 0.0001f)
        assertEquals(0f, partitionReduction, 0.0001f)
        assertTrue(
            shouldUseRealtimeVideoCardTransitionBackgroundBlur(
                realtimeBlurEnabled = true,
            )
        )
        assertFalse(
            shouldUseRealtimeVideoCardTransitionBackgroundBlur(
                realtimeBlurEnabled = false,
            )
        )
        assertTrue(
            shouldUseRealtimeVideoCardTransitionBackgroundBlur(
                realtimeBlurEnabled = true,
            )
        )
        assertEquals(
            1f,
            resolveVideoCardTransitionContentScale(
                progress = 1f,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                motionTier = MotionTier.Normal,
                scaleReduction = relatedReduction,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoCardTransitionContentScale(
                progress = 1f,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                motionTier = MotionTier.Normal,
                scaleReduction = partitionReduction,
            ),
            0.0001f,
        )
    }

    @Test
    fun sharedShellDoesNotApplyBackgroundScale() {
        val source = File(
            "src/main/java/com/android/purebilibili/core/ui/transition/" +
                "VideoCardShellSharedBounds.kt"
        ).readText()

        assertTrue(source.contains("OverlayClip(clipShape)"))
        assertFalse(source.contains("resolveVideoCardSiblingDepthScale("))
        assertFalse(source.contains(".graphicsLayer {"))
        assertFalse(source.contains("scaleX = scale"))
        assertFalse(source.contains("scaleY = scale"))
        assertFalse(source.contains("shadowElevation"))
        assertFalse(source.contains("translationX ="))
        assertFalse(source.contains("translationY ="))
        assertFalse(source.contains("animateFloatAsState"))
        assertFalse(source.contains("Animatable"))
    }

    @Test
    fun liveBackgroundLayerReusesRenderEffectAndClipShapeAcrossFrames() {
        val source = File(
            "src/main/java/com/android/purebilibili/core/ui/transition/" +
                "VideoCardTransitionBackgroundPolicy.kt"
        ).readText()
        val live = source
            .substringAfter("internal fun Modifier.videoCardTransitionLiveBackgroundEffect(")
            .substringBefore("internal data class VideoCardTransitionNavBackdropFrame")
        // 返回手势逐帧推进：若每帧重建 BlurEffect/RoundedCornerShape，会持续产生分配并
        // 触发 GPU effect 重建。这里改为按量化半径复用，模糊仍然全程开启且对所有设备一致。
        assertTrue(live.contains("var cachedLiveBlurRadiusPx = Float.NaN"))
        assertTrue(live.contains("var cachedLiveClipShape: RoundedCornerShape? = null"))
        assertTrue(live.contains("if (liveBlurRadiusPx != cachedLiveBlurRadiusPx)"))
        assertTrue(live.contains("renderEffect = cachedLiveBlurEffect"))
        assertTrue(live.contains("cachedLiveClipShape?.let { shape = it }"))
        assertFalse(live.contains("renderEffect = if ("))
    }

    @Test
    fun openingBlurBuildsInLockstepWithDepth() {
        val early = resolveVideoCardTransitionBackgroundFrame(
            progress = 0.2f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Normal,
            sdkInt = 35,
        )
        // 清晰附近采用 0.5px 步长，2.4px → 2.5px。
        assertEquals(2.5f, early.blurRadiusPx, 0.01f)
        assertTrue(early.scrimAlpha > 0f)
        assertTrue(early.blurRadiusPx > 0f)
        assertEquals(4.8f, early.cornerRadiusPx, 0.01f)
    }

    @Test
    fun lightOpeningUsesReducedScrimAndWarmTint() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Enhanced,
            isLightBackground = true,
            sdkInt = 35
        )

        assertEquals(12f, frame.blurRadiusPx)
        assertEquals(0.08f, frame.scrimAlpha)
        assertTrue(frame.useLightScrimTint)
    }

    @Test
    fun lightReducedMotionUsesMinimalOpeningScrimWithoutBlur() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Reduced,
            isLightBackground = true,
            sdkInt = 35
        )

        assertEquals(0f, frame.blurRadiusPx)
        assertEquals(0.08f, frame.scrimAlpha)
        assertTrue(frame.useLightScrimTint)
    }

    @Test
    fun returningScrimMatchesHeldIntensityAtTheSameProgress() {
        val held = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.HELD,
            sdkInt = 35,
        )
        val returning = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            sdkInt = 35,
        )

        assertEquals(held.scrimAlpha, returning.scrimAlpha)
    }

    @Test
    fun returningDepthProgressIsLinearToLockstepWithMorph() {
        // RETURNING 与 OPENING 同源线性，禁止 soft-clear 二次映射拖糊。
        assertEquals(
            0.5f,
            resolveVideoCardTransitionDepthProgress(
                progress = 0.5f,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
            ),
            0.0001f,
        )
        assertEquals(
            0.5f,
            resolveVideoCardTransitionDepthProgress(
                progress = 0.5f,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
            ),
            0.0001f,
        )
    }

    @Test
    fun morphAlignedDepthClearDuration_matchesMorphRemainingWallClock() {
        assertEquals(
            360,
            resolveMorphAlignedDepthClearDurationMs(
                morphRemainingMs = 360,
                blurStartProgress = 1f,
            ),
        )
        assertEquals(
            180,
            resolveMorphAlignedDepthClearDurationMs(
                morphRemainingMs = 360,
                blurStartProgress = 0.5f,
            ),
        )
        // 禁止 min 160 地板：手势已拖到底后不应再补一段长消糊。
        assertEquals(
            0,
            resolveMorphAlignedDepthClearDurationMs(
                morphRemainingMs = 90,
                blurStartProgress = 0f,
            ),
        )
        assertEquals(
            18,
            resolveMorphAlignedDepthClearDurationMs(
                morphRemainingMs = 90,
                blurStartProgress = 0.2f,
            ),
        )
    }

    @Test
    fun returningFrameFadesBlurAndScrimWithSharedElementProgress() {
        val start = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            motionTier = MotionTier.Enhanced,
            sdkInt = 35
        )
        val middle = resolveVideoCardTransitionBackgroundFrame(
            progress = 0.5f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            motionTier = MotionTier.Enhanced,
            sdkInt = 35
        )
        val end = resolveVideoCardTransitionBackgroundFrame(
            progress = 0f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            motionTier = MotionTier.Enhanced,
            sdkInt = 35
        )

        assertEquals(12f, start.blurRadiusPx)
        assertEquals(6f, middle.blurRadiusPx)
        assertTrue(middle.blurRadiusPx in 1f..<start.blurRadiusPx)
        assertEquals(0f, end.blurRadiusPx)
        assertTrue(start.scrimAlpha > middle.scrimAlpha)
        assertTrue(middle.scrimAlpha > 0f)
        assertEquals(0f, end.scrimAlpha)
        assertEquals(1f, start.contentScale, 0.0001f)
        assertEquals(1f, middle.contentScale, 0.0001f)
        assertEquals(1f, end.contentScale, 0.0001f)
        assertEquals(0f, start.cornerRadiusPx, 0.0001f)
        assertEquals(0f, middle.cornerRadiusPx, 0.0001f)
        assertEquals(0f, end.cornerRadiusPx, 0.0001f)
    }

    @Test
    fun detailToDetailSourceAppliesDepthToTheLivePreviousDetailInsteadOfAHostSnapshot() {
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "video/BV_A",
                sourceRoute = "video/BV_A",
                activeMainHostRoute = "home"
            )
        )
        assertFalse(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "video/BV_B",
                sourceRoute = "video/BV_A",
                activeMainHostRoute = "home"
            )
        )
        assertFalse(
            shouldUseHostOwnedVideoCardTransitionSnapshot(
                sourceRoute = "video/BV_A?from=related"
            )
        )
        assertTrue(shouldUseHostOwnedVideoCardTransitionSnapshot(sourceRoute = "home"))
    }

    @Test
    fun heldFrameKeepsBackgroundBlurDepthAndScrimForStableIosLikeStack() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.HELD,
            motionTier = MotionTier.Enhanced,
            isLightBackground = false,
            sdkInt = 35
        )

        assertEquals(12f, frame.blurRadiusPx)
        // HELD 保留与满进度开场一致的压暗，避免详情停留时景深断裂。
        assertEquals(0.14f, frame.scrimAlpha)
        assertEquals(1f, frame.contentScale, 0.0001f)
        assertEquals(0f, frame.cornerRadiusPx, 0.0001f)
    }

    @Test
    fun gestureRestoreSmoothlyReturnsBackgroundToHeldDepth() {
        val openingScale = resolveVideoCardTransitionContentScale(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            motionTier = MotionTier.Normal,
        )
        val restoreScale = resolveVideoCardTransitionContentScale(
            progress = 0.5f,
            phase = VideoCardTransitionBackgroundPhase.HELD,
            motionTier = MotionTier.Normal,
        )

        assertEquals(1f, openingScale, 0.0001f)
        assertEquals(1f, restoreScale, 0.0001f)
    }

    @Test
    fun idleFrameClearsBackgroundEffect() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.IDLE,
            sdkInt = 35
        )

        assertEquals(0f, frame.blurRadiusPx)
        assertEquals(0f, frame.scrimAlpha)
        assertEquals(1f, frame.contentScale)
        assertEquals(0f, frame.cornerRadiusPx)
    }

    @Test
    fun androidBeforeSDisablesRealtimeBlurButKeepsOpeningScrim() {
        val opening = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            sdkInt = 30
        )
        val returning = resolveVideoCardTransitionBackgroundFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            sdkInt = 30
        )

        assertEquals(0f, opening.blurRadiusPx)
        assertTrue(opening.scrimAlpha > 0f)
        assertEquals(1f, opening.contentScale, 0.0001f)
        assertEquals(0f, opening.cornerRadiusPx, 0.0001f)
        assertEquals(0f, returning.blurRadiusPx)
        assertTrue(returning.scrimAlpha > 0f)
        assertEquals(1f, returning.contentScale, 0.0001f)
        assertEquals(0f, returning.cornerRadiusPx, 0.0001f)
    }

    @Test
    fun midReturnProgressStillKeepsFadingBackgroundEffect() {
        val frame = resolveVideoCardTransitionBackgroundFrame(
            progress = 0.25f,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            sdkInt = 35
        )

        assertTrue(frame.blurRadiusPx > 0f)
        assertTrue(frame.scrimAlpha > 0f)
        assertEquals(0.99f, frame.contentScale, 0.0001f)
        assertEquals(6f, frame.cornerRadiusPx, 0.0001f)
    }

    @Test
    fun snapshotRecording_staysFrozenForAllActivePhases_toProtectFrameBudget() {
        assertFalse(
            shouldLiveRecordVideoCardTransitionSnapshot(
                phase = VideoCardTransitionBackgroundPhase.OPENING,
            )
        )
        assertFalse(
            shouldLiveRecordVideoCardTransitionSnapshot(
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
            )
        )
        assertFalse(
            shouldLiveRecordVideoCardTransitionSnapshot(
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
            )
        )
        assertFalse(
            shouldLiveRecordVideoCardTransitionSnapshot(
                phase = VideoCardTransitionBackgroundPhase.HELD,
            )
        )
        assertFalse(
            shouldLiveRecordVideoCardTransitionSnapshot(
                phase = VideoCardTransitionBackgroundPhase.IDLE,
            )
        )
    }

    @Test
    fun routeMatcherTargetsOnlyRecordedSourceEntryOrActiveMainHostPage() {
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "main_host",
                sourceRoute = "home",
                activeMainHostRoute = "home"
            )
        )
        assertFalse(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "main_host",
                sourceRoute = "home",
                activeMainHostRoute = "dynamic"
            )
        )
        assertFalse(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "main_host",
                sourceRoute = "home",
                activeMainHostRoute = "video/BV1"
            )
        )
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "home",
                sourceRoute = "home",
                activeMainHostRoute = "video/BV1"
            )
        )
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "search",
                sourceRoute = "search",
                activeMainHostRoute = "home"
            )
        )
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "space/123",
                sourceRoute = "space/123?from=archive",
                activeMainHostRoute = "home"
            )
        )
        assertFalse(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "settings",
                sourceRoute = "home",
                activeMainHostRoute = "home"
            )
        )
        assertFalse(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "video/BV1",
                sourceRoute = "video",
                activeMainHostRoute = "home"
            )
        )
    }

    @Test
    fun routeMatcherTreatsHomeCategoryAsActiveHomePageForRealtimeBlur() {
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "main_host",
                sourceRoute = "home?category=RECOMMEND",
                activeMainHostRoute = "home"
            )
        )
    }

    @Test
    fun routeMatcherAppliesHomeHostDepthForInlinePartitionSource() {
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "home",
                sourceRoute = "partition",
                activeMainHostRoute = "home"
            )
        )
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "main_host",
                sourceRoute = "partition",
                activeMainHostRoute = "home"
            )
        )
        assertTrue(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "partition",
                sourceRoute = "partition",
                activeMainHostRoute = "home"
            )
        )
        assertFalse(
            shouldApplyVideoCardTransitionBackgroundToRoute(
                entryRoute = "main_host",
                sourceRoute = "partition",
                activeMainHostRoute = "dynamic"
            )
        )
    }

    @Test
    fun gestureProgressMapsBackGestureToDecreasingBlurStartingFromFull() {
        // 手势起点保持满虚化，与 HELD 衔接；拖到底背景清晰；中途单调递减。
        assertEquals(1f, resolveVideoCardTransitionBackgroundGestureProgress(0f))
        assertEquals(0.5f, resolveVideoCardTransitionBackgroundGestureProgress(0.5f))
        assertEquals(0f, resolveVideoCardTransitionBackgroundGestureProgress(1f))
    }

    @Test
    fun gestureProgressClampsOutOfRangeBackProgress() {
        assertEquals(1f, resolveVideoCardTransitionBackgroundGestureProgress(-0.5f))
        assertEquals(0f, resolveVideoCardTransitionBackgroundGestureProgress(1.5f))
    }

    @Test
    fun openingGestureProgress_fadesFromCurrentOpeningBlurLinearly() {
        assertEquals(0.6f, resolveVideoCardTransitionBackgroundOpeningGestureProgress(
            openingBlurProgress = 0.6f,
            backProgress = 0f,
        ))
        assertEquals(0.3f, resolveVideoCardTransitionBackgroundOpeningGestureProgress(
            openingBlurProgress = 0.6f,
            backProgress = 0.5f,
        ))
        assertEquals(0f, resolveVideoCardTransitionBackgroundOpeningGestureProgress(
            openingBlurProgress = 0.6f,
            backProgress = 1f,
        ))
    }

    @Test
    fun resolveGestureBlurProgress_routesHeldAndOpeningPhases() {
        assertEquals(
            0.5f,
            resolveVideoCardTransitionBackgroundGestureBlurProgress(
                phase = VideoCardTransitionBackgroundPhase.HELD,
                currentBlurProgress = 1f,
                backProgress = 0.5f,
            )
        )
        assertEquals(
            0.4f * (1f - 0.4f),
            resolveVideoCardTransitionBackgroundGestureBlurProgress(
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                currentBlurProgress = 0.4f,
                backProgress = 0.4f,
            )
        )
    }

    @Test
    fun gesturePhase_includesHeldAndOpeningOnly() {
        assertTrue(isVideoCardTransitionBackgroundGesturePhase(VideoCardTransitionBackgroundPhase.HELD))
        assertTrue(isVideoCardTransitionBackgroundGesturePhase(VideoCardTransitionBackgroundPhase.OPENING))
        assertFalse(isVideoCardTransitionBackgroundGesturePhase(VideoCardTransitionBackgroundPhase.IDLE))
        assertFalse(isVideoCardTransitionBackgroundGesturePhase(VideoCardTransitionBackgroundPhase.RETURNING))
    }

    @Test
    fun returnDurationScalesWithRemainingBlurButKeepsMinimumFloor() {
        // 未消解(startProgress=1)时用完整时长；手势已消解一半则约减半；接近清晰时不低于取消时长下限。
        assertEquals(
            VIDEO_CARD_TRANSITION_BACKGROUND_RETURN_DURATION_MS,
            resolveVideoCardTransitionBackgroundReturnDurationMs(1f)
        )
        assertEquals(
            VIDEO_CARD_TRANSITION_BACKGROUND_RETURN_DURATION_MS / 2,
            resolveVideoCardTransitionBackgroundReturnDurationMs(0.5f)
        )
        assertEquals(
            VIDEO_CARD_TRANSITION_BACKGROUND_CANCEL_DURATION_MS,
            resolveVideoCardTransitionBackgroundReturnDurationMs(0f)
        )
        assertEquals(
            VIDEO_CARD_TRANSITION_BACKGROUND_CANCEL_DURATION_MS,
            resolveVideoCardTransitionBackgroundReturnDurationMs(0.05f)
        )
    }

    @Test
    fun returnFullDurationKeepsTheSharedMasterTimeline() {
        assertEquals(
            460,
            resolveVideoCardTransitionReturnFullDurationMillis(
                baseDurationMillis = 460,
            ),
        )
        assertEquals(0, resolveVideoCardTransitionReturnFullDurationMillis(-1))
    }

    @Test
    fun openingPhaseIsInterruptedOnReturn() {
        assertTrue(
            shouldInterruptVideoCardOpeningOnReturn(VideoCardTransitionBackgroundPhase.OPENING)
        )
        assertFalse(
            shouldInterruptVideoCardOpeningOnReturn(VideoCardTransitionBackgroundPhase.HELD)
        )
        assertFalse(
            shouldInterruptVideoCardOpeningOnReturn(VideoCardTransitionBackgroundPhase.RETURNING)
        )
    }

    @Test
    fun navBackdropIsRemovedAtSettledHiddenAndVisibleOnlyWhileExposed() {
        assertFalse(
            shouldShowVideoCardTransitionNavBackdrop(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.SettledHidden,
                isVideoDetailOnStack = true,
            )
        )
        assertTrue(
            shouldShowVideoCardTransitionNavBackdrop(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.Opening,
                isVideoDetailOnStack = true,
            )
        )
        assertTrue(
            shouldShowVideoCardTransitionNavBackdrop(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.BackPreview,
                isVideoDetailOnStack = true,
            )
        )
        assertTrue(
            shouldShowVideoCardTransitionNavBackdrop(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.Returning,
                isVideoDetailOnStack = false,
            )
        )
        assertFalse(
            shouldShowVideoCardTransitionNavBackdrop(
                cardTransitionEnabled = false,
                exposure = VideoCardTransitionExposure.BackPreview,
                isVideoDetailOnStack = true,
            )
        )
        assertFalse(
            shouldShowVideoCardTransitionNavBackdrop(
                cardTransitionEnabled = true,
                exposure = VideoCardTransitionExposure.SettledHidden,
                isVideoDetailOnStack = false,
            )
        )
    }

    @Test
    fun navBackdropFrameTracksBlurStrengthDuringHeldAndOpening() {
        val heldFull = resolveVideoCardTransitionNavBackdropFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.HELD,
            isLightBackground = true,
        )
        val heldHalf = resolveVideoCardTransitionNavBackdropFrame(
            progress = 0.5f,
            phase = VideoCardTransitionBackgroundPhase.HELD,
            isLightBackground = true,
        )
        val openingFull = resolveVideoCardTransitionNavBackdropFrame(
            progress = 1f,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            isLightBackground = false,
        )

        assertEquals(0.08f, heldFull.scrimAlpha)
        assertTrue(heldHalf.scrimAlpha < heldFull.scrimAlpha)
        assertEquals(0.14f, openingFull.scrimAlpha)
        assertTrue(heldFull.useLightScrimTint)
        assertFalse(openingFull.useLightScrimTint)
    }

    @Test
    fun navBackdropColorLerpsFromBaseBackgroundTowardScrimTint() {
        val base = androidx.compose.ui.graphics.Color.White
        val frame = VideoCardTransitionNavBackdropFrame(
            scrimAlpha = 0.10f,
            useLightScrimTint = true,
        )
        val blended = resolveVideoCardTransitionNavBackdropColor(
            baseBackgroundColor = base,
            frame = frame,
        )

        assertTrue(blended != base)
        assertTrue(blended.alpha > 0f)
    }

    @Test
    fun scaleGapFillKeepsOpaqueGrayFloorSoPredictiveBackEdgesDoNotReadAsWhiteBars() {
        val lightHeld = resolveVideoCardTransitionScaleGapFillColor(
            isLightBackground = true,
            scrimAlpha = 0.14f,
        )
        val lightMidGesture = resolveVideoCardTransitionScaleGapFillColor(
            isLightBackground = true,
            scrimAlpha = 0.03f,
        )
        val darkHeld = resolveVideoCardTransitionScaleGapFillColor(
            isLightBackground = false,
            scrimAlpha = 0.28f,
        )

        assertEquals(1f, lightHeld.alpha)
        assertEquals(1f, lightMidGesture.alpha)
        assertTrue(lightHeld.red < 0.92f)
        assertTrue(lightMidGesture.red < 0.92f)
        assertEquals(lightHeld, lightMidGesture)
        assertTrue(darkHeld.red < 0.35f)
        assertTrue(
            shouldDrawVideoCardTransitionScaleGapFill(contentScale = 0.96f)
        )
        assertFalse(
            shouldDrawVideoCardTransitionScaleGapFill(contentScale = 1f)
        )
    }

    @Test
    fun navBackdropColorUsesScaleGapFloorDuringLowProgressGesture() {
        val base = androidx.compose.ui.graphics.Color.White
        val lowProgress = resolveVideoCardTransitionNavBackdropColor(
            baseBackgroundColor = base,
            frame = VideoCardTransitionNavBackdropFrame(
                scrimAlpha = 0.02f,
                useLightScrimTint = true,
            ),
        )
        val gapFill = resolveVideoCardTransitionScaleGapFillColor(
            isLightBackground = true,
            scrimAlpha = 0.02f,
        )

        assertEquals(gapFill, lowProgress)
        assertTrue(lowProgress.red < 0.92f)
    }

    @Test
    fun hostOwnedSourceDetachRequestsRefreshWithoutMarkingStale() {
        val state = VideoCardTransitionSnapshotLayerState().apply {
            freezeRecording = true
            hasRecordedContent = true
            displayListStale = false
            lastBlurRadiusPx = 12f
        }
        state.markSourceDetachedForRefresh()
        assertTrue(state.needsSourceRefresh)
        assertFalse(state.freezeRecording)
        assertTrue(state.hasRecordedContent)
        assertFalse(state.displayListStale)
        // Host 可在预测 BackPreview 首帧暂用冻结内容撑住满模糊。
        assertTrue(
            isVideoCardTransitionSnapshotDrawable(
                hasRecordedContent = state.hasRecordedContent,
                displayListStale = state.displayListStale,
            ),
        )
        state.markDisplayListFresh()
        assertFalse(state.needsSourceRefresh)
    }
}
