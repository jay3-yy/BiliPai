package com.android.purebilibili.feature.video.screen

import com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot
import com.android.purebilibili.core.ui.transition.VideoCardSourceCoverPresentation
import com.android.purebilibili.core.ui.transition.VideoCardSourceLayout
import com.android.purebilibili.core.ui.transition.VideoCardTransitionBackgroundPhase
import com.android.purebilibili.core.ui.transition.VideoSharedTransitionPlaybackIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VideoDetailReturnCoverPolicyTest {
    @Test
    fun liveReturnAndCancelledReturnNeverExposeTheMediaBackdrop() {
        val depths = (0..100).map { it / 100f }
        for (committed in listOf(false, true)) {
            val frames = (depths + depths.reversed()).map { depth ->
                resolveVideoDetailReturnMediaFrame(
                    transitionProgress = depth,
                    isCommittedCardReturn = committed,
                    isReturnGestureInProgress = !committed,
                    hasResidentCover = true,
                    liveReturnMorph = true,
                )
            }
            for (frame in frames) {
                val compositedAlpha = frame.coverAlpha + frame.playerAlpha * (1f - frame.coverAlpha)
                assertEquals(1f, compositedAlpha, .0001f)
            }
            assertEquals(frames.take(depths.size), frames.takeLast(depths.size).reversed())
        }
    }

    @Test
    fun entryCoverFadeNeverExposesAnEmptyMediaSlot() {
        for (step in 0..20) {
            val frame = resolveVideoDetailReturnMediaFrame(
                transitionProgress = 1f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                entryCoverAlpha = step / 20f,
            )
            // Source-over alpha: the video stays opaque underneath throughout entry.
            val compositedAlpha = frame.coverAlpha + frame.playerAlpha * (1f - frame.coverAlpha)
            assertEquals(1f, compositedAlpha, 0.0001f)
            assertEquals(1f, frame.playerAlpha, 0.0001f)
        }
        val waiting = resolveVideoDetailReturnMediaFrame(
            transitionProgress = 1f,
            isCommittedCardReturn = false,
            hasResidentCover = true,
            showResidentCoverUntilFirstFrame = true,
            entryCoverAlpha = 0f,
        )
        assertEquals(VideoDetailReturnMediaFrame(1f, 0f), waiting)
    }

    @Test
    fun returnAndGestureCancellationKeepTheirTimelineDuringFirstFrameFade() {
        for (depth in listOf(1f, 0.6f, 0.2f, 0.05f, 0.2f, 0.6f, 1f)) {
            for (committed in listOf(false, true)) {
                fun frame(entryAlpha: Float) = resolveVideoDetailReturnMediaFrame(
                    transitionProgress = depth,
                    isCommittedCardReturn = committed,
                    isReturnGestureInProgress = !committed,
                    hasResidentCover = true,
                    liveReturnMorph = true,
                    entryCoverAlpha = entryAlpha,
                )
                assertEquals(frame(0f), frame(0.7f))
            }
        }
        assertEquals(
            VideoDetailReturnMediaFrame(0f, 1f),
            resolveVideoDetailReturnMediaFrame(1f, false, false, entryCoverAlpha = 1f),
        )
    }

    @Test
    fun nativeCardInfoFillsTheBodyYieldDuringOpeningReturnAndCancel() {
        for (layout in listOf(VideoCardSourceLayout.STACKED, VideoCardSourceLayout.SIDE_BY_SIDE)) {
            for (phase in listOf(
                VideoCardTransitionBackgroundPhase.OPENING,
                VideoCardTransitionBackgroundPhase.RETURNING,
                VideoCardTransitionBackgroundPhase.HELD,
            )) {
                var previousMedia: Float? = null
                // Reverse direction without changing phase, as a cancelled or re-grabbed
                // predictive gesture does. No frame may lose both information layers.
                for (step in (0..100).toList() + (100 downTo 0).toList()) {
                    val depth = step / 100f
                    val gesture = phase == VideoCardTransitionBackgroundPhase.HELD
                    val source = resolveVideoDetailFlyingSourceChromeAlpha(
                        morphDepthProgress = depth,
                        phase = phase,
                        isReturnGestureInProgress = gesture,
                        sourceLayout = layout,
                        preserveNativeViewport = true,
                    )
                    val body = com.android.purebilibili.core.ui.transition
                        .resolveVideoCardSecondaryContentVisualFrame(
                            morphDepthProgress = depth,
                            phase = phase,
                            isReturnGestureInProgress = gesture,
                            motionTier = com.android.purebilibili.core.ui.adaptive.MotionTier.Normal,
                            sourceLayout = layout,
                            preserveNativeViewport = true,
                        )
                    assertEquals("Missing information at $phase depth=$depth", 1f, source + body.alpha, .0001f)
                    val media = com.android.purebilibili.core.ui.transition
                        .resolveVideoDetailMediaLayoutHandoffProgress(
                            morphDepthProgress = depth,
                            phase = phase,
                            isReturnGestureInProgress = gesture,
                            sourceLayout = layout,
                            sourceChromeAlpha = source,
                            preserveNativeViewport = true,
                        )
                    assertEquals(source, media, .0001f)
                    previousMedia?.let { assertTrue(kotlin.math.abs(media - it) < .025f) }
                    previousMedia = media
                }
            }
        }
    }

    @Test
    fun nativeInfoHandoffKeepsTheLiveCoverSwapLateAndReleasesAfterCommit() {
        val depth = .5f
        assertTrue(resolveVideoDetailFlyingSourceChromeAlpha(
            depth, VideoCardTransitionBackgroundPhase.RETURNING, false,
            VideoCardSourceLayout.STACKED, preserveNativeViewport = true,
        ) > 0f)
        assertEquals(0f, com.android.purebilibili.core.ui.transition
            .resolveVideoCardLiveReturnVisualHandoffAlpha(depth), .0001f)
        assertEquals(0f, resolveVideoDetailFlyingSourceChromeAlpha(
            0f, VideoCardTransitionBackgroundPhase.IDLE, false,
            VideoCardSourceLayout.STACKED, isCommittedCardReturn = true,
            preserveNativeViewport = true,
        ), .0001f)
        // Playback bars retain their separate capsule geometry and timing.
        assertEquals(0f, resolveVideoDetailFlyingSourceChromeAlpha(
            depth, VideoCardTransitionBackgroundPhase.RETURNING, false,
            VideoCardSourceLayout.SIDE_BY_SIDE, isNowPlayingBar = true,
            preserveNativeViewport = true,
        ), .0001f)
    }

    @Test
    fun horizontalInfoIsClippedBehindTheExpandingMediaEdge() {
        // 800px viewport, 300px source cover + 500px info, compensated by 2x.
        fun clip(progress: Float, localScale: Float = 1f) =
            resolveVideoDetailHorizontalInfoClipLeftPx(
                viewportWidthPx = 800f,
                coverRightPx = 150f,
                infoLeftPx = 150f,
                inverseScaleX = 2f,
                handoffProgress = progress,
                localScaleX = localScale,
            )
        assertEquals(500f, clip(0f), 0.001f)
        assertEquals(250f, clip(0.5f), 0.001f)
        assertEquals(0f, clip(1f), 0.001f)
        // Reconstructed content is drawn in source coordinates rather than entry coordinates.
        assertEquals(125f, clip(0.5f, localScale = 2f), 0.001f)
    }


    @Test
    fun loadingDetailRevealsFrozenCardEarlyOnlyOnReturn() {
        assertEquals(
            1f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 0.7f,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
                detailContentLoading = true,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 0.7f,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
                detailContentLoading = true,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 0.7f,
                phase = VideoCardTransitionBackgroundPhase.HELD,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
                detailContentLoading = true,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 0.7f,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
            ),
            0.001f,
        )
    }

    @Test
    fun ordinaryDetailMediaRemainsVisibleThroughoutNavigation() {
        for (phase in VideoCardTransitionBackgroundPhase.entries) {
            for (sourceAlpha in listOf(0f, 0.5f, 1f)) {
                assertEquals(1f, resolveVideoDetailNowPlayingMediaAlpha(
                    ownsNowPlayingTransition = false,
                    isCommittedCardReturn = false,
                    phase = phase,
                    sourceChromeAlpha = sourceAlpha,
                ), 0.001f)
            }
        }
    }

    @Test
    fun nowPlayingOpeningHandsSnapshotOverToVisiblePlayer() {
        for ((depth, expectedMediaAlpha) in listOf(0f to 0f, 0.1f to 0.5f, 0.7f to 1f, 1f to 1f)) {
            val sourceAlpha = resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = depth,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
                isNowPlayingBar = true,
            )
            assertEquals(expectedMediaAlpha, resolveVideoDetailNowPlayingMediaAlpha(
                ownsNowPlayingTransition = true,
                isCommittedCardReturn = false,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                sourceChromeAlpha = sourceAlpha,
            ), 0.001f)
        }
    }

    @Test
    fun completedNowPlayingReturnRemovesDetailMedia() {
        assertEquals(0f, resolveVideoDetailNowPlayingMediaAlpha(
            ownsNowPlayingTransition = true,
            isCommittedCardReturn = true,
            phase = VideoCardTransitionBackgroundPhase.IDLE,
            sourceChromeAlpha = 0f,
        ), 0.001f)
    }

    @Test
    fun nowPlayingBarUsesOneSnapshotWindowForOpeningReturnAndGestureRestore() {
        for ((depth, expected) in listOf(1f to 0f, 0.7f to 0f, 0.1f to 0.5f, 0.02f to 1f, 0f to 1f)) {
            for (phase in listOf(
                VideoCardTransitionBackgroundPhase.OPENING,
                VideoCardTransitionBackgroundPhase.RETURNING,
                VideoCardTransitionBackgroundPhase.HELD,
            )) {
                assertEquals(
                    expected,
                    resolveVideoDetailFlyingSourceChromeAlpha(
                        morphDepthProgress = depth,
                        phase = phase,
                        isReturnGestureInProgress = phase == VideoCardTransitionBackgroundPhase.HELD,
                        sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
                        isNowPlayingBar = true,
                    ),
                    0.001f,
                )
            }
        }
    }

    @Test
    fun nowPlayingSnapshotDoesNotReappearAfterOwnershipReturnsToRealBar() {
        assertEquals(
            0f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 0f,
                phase = VideoCardTransitionBackgroundPhase.IDLE,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.SIDE_BY_SIDE,
                isNowPlayingBar = true,
                isCommittedCardReturn = true,
            ),
            0.001f,
        )
    }

    @Test
    fun flyingSourceChromeOwnsClickFrameAndReturnLandingFrame() {
        assertEquals(
            1f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 0f,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
            ),
            0.001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 1f,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
            ),
            0.001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 0f,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
            ),
            0.001f,
        )
        val openingMidDepth = 0.45f
        val returnMidDepth = 0.10f
        val openingMid = resolveVideoDetailFlyingSourceChromeAlpha(
            morphDepthProgress = openingMidDepth,
            phase = VideoCardTransitionBackgroundPhase.OPENING,
            isReturnGestureInProgress = false,
            sourceLayout = VideoCardSourceLayout.STACKED,
        )
        val returningMid = resolveVideoDetailFlyingSourceChromeAlpha(
            morphDepthProgress = returnMidDepth,
            phase = VideoCardTransitionBackgroundPhase.RETURNING,
            isReturnGestureInProgress = false,
            sourceLayout = VideoCardSourceLayout.STACKED,
        )
        val heldSeekMid = resolveVideoDetailFlyingSourceChromeAlpha(
            morphDepthProgress = returnMidDepth,
            phase = VideoCardTransitionBackgroundPhase.HELD,
            isReturnGestureInProgress = true,
            sourceLayout = VideoCardSourceLayout.STACKED,
        )
        assertEquals(0.5f, openingMid, 0.001f)
        assertEquals(0.5f, returningMid, 0.001f)
        assertEquals(returningMid, heldSeekMid, 0.001f)
        assertEquals(
            0f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 1f,
                phase = VideoCardTransitionBackgroundPhase.HELD,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
            ),
            0.001f,
        )
    }

    @Test
    fun flyingCoverUsesTheFrozenStationaryCardPresentation() {
        val coverPresentation = VideoCardSourceCoverPresentation(
            showGradientMask = true,
            showStatsOnCover = true,
            showSecondaryStatOnCover = true,
            showDurationOnCover = true,
            showHistoryProgressBar = true,
            historyProgressFraction = 0.42f,
        )
        val model = resolveVideoDetailReturnSourceCardChromeModel(
            info = null,
            snapshot = VideoCardSourceChromeSnapshot(
                title = "title",
                ownerName = "owner",
                viewText = "12.3万",
                danmakuText = "456",
                durationText = "05:20",
                coverPresentation = coverPresentation,
            ),
        )

        assertEquals(coverPresentation, model?.coverPresentation)
        assertEquals(2f, resolveVideoDetailReturnCoverChromeDensityScale(0.5f), 0.001f)
        assertTrue(shouldDrawNativeCoverOverlay(overlayWidthPx = 320f, overlayHeightPx = 180f))
        assertFalse(shouldDrawNativeCoverOverlay(overlayWidthPx = 0f, overlayHeightPx = 180f))
        assertFalse(shouldDrawNativeCoverOverlay(overlayWidthPx = 320f, overlayHeightPx = 1f))
    }

    @Test
    fun restoredParentSessionDoesNotRetakeVisualAssetsWhileClockIsIdle() {
        assertFalse(
            shouldConsumeMiuixTransitionVisualAssets(
                entryOwnsMiuixCardTransition = true,
                phase = VideoCardTransitionBackgroundPhase.IDLE,
                isReturnGestureInProgress = false,
            )
        )
        assertTrue(
            shouldConsumeMiuixTransitionVisualAssets(
                entryOwnsMiuixCardTransition = true,
                phase = VideoCardTransitionBackgroundPhase.IDLE,
                isReturnGestureInProgress = true,
            )
        )
        assertTrue(
            shouldConsumeMiuixTransitionVisualAssets(
                entryOwnsMiuixCardTransition = true,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                isReturnGestureInProgress = false,
            )
        )
        assertTrue(
            shouldConsumeMiuixTransitionVisualAssets(
                entryOwnsMiuixCardTransition = true,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                isReturnGestureInProgress = false,
            )
        )
        assertFalse(
            shouldConsumeMiuixTransitionVisualAssets(
                entryOwnsMiuixCardTransition = false,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                isReturnGestureInProgress = true,
            )
        )
    }

    @Test
    fun nestedDetailOnlyConsumesMiuixTransitionFromItsOwnSourceEntry() {
        assertTrue(
            isVideoDetailEntryActiveMiuixTransitionSource(
                entrySourceRoute = "video/BV_PARENT",
                activeSourceRoute = "video/BV_PARENT",
                miuixTransitionEnabled = true,
            )
        )
        assertTrue(
            isVideoDetailEntryActiveMiuixTransitionSource(
                entrySourceRoute = "video/BV_PARENT?title=parent",
                activeSourceRoute = "video/BV_PARENT?from=related",
                miuixTransitionEnabled = true,
            )
        )
        assertFalse(
            isVideoDetailEntryActiveMiuixTransitionSource(
                entrySourceRoute = "home",
                activeSourceRoute = "video/BV_PARENT",
                miuixTransitionEnabled = true,
            )
        )
        assertFalse(
            isVideoDetailEntryActiveMiuixTransitionSource(
                entrySourceRoute = null,
                activeSourceRoute = "video/BV_PARENT",
                miuixTransitionEnabled = true,
            )
        )
    }

    @Test
    fun matchingSourceDoesNotOwnDisabledCardTransition() {
        // The now-playing bar still records its source route and capsule bounds with motion off.
        assertFalse(
            isVideoDetailEntryActiveMiuixTransitionSource(
                entrySourceRoute = "home",
                activeSourceRoute = "home",
                miuixTransitionEnabled = false,
            )
        )
    }

    @Test
    fun nestedReturnSessionOnlyChangesOutgoingDetailVisualState() {
        assertTrue(
            shouldConsumeMiuixReturnSessionForVideoDetailEntry(
                entryOwnsMiuixCardTransition = true,
                isReturningFromDetail = true,
                transitionEnabled = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = false,
            )
        )
        assertFalse(
            shouldConsumeMiuixReturnSessionForVideoDetailEntry(
                entryOwnsMiuixCardTransition = false,
                isReturningFromDetail = true,
                transitionEnabled = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = false,
            )
        )
        assertFalse(
            shouldConsumeMiuixReturnSessionForVideoDetailEntry(
                entryOwnsMiuixCardTransition = true,
                isReturningFromDetail = true,
                transitionEnabled = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = true,
            )
        )
    }

    @Test
    fun retainedParentDoesNotConsumeStalePostExitFromNestedReturn() {
        assertFalse(
            shouldTreatVideoDetailCardExitAsReturning(
                isExitTransitionInProgress = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = false,
                entryOwnsCardTransition = false,
            )
        )
        assertTrue(
            shouldTreatVideoDetailCardExitAsReturning(
                isExitTransitionInProgress = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = false,
                entryOwnsCardTransition = true,
            )
        )
    }

    @Test
    fun flyingEntryOwnsInformationAndCoverChrome() {
        val holder = File(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt",
        ).takeIf { it.isFile }?.readText()
            ?: File(
                "src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt",
            ).readText()
        assertTrue(holder.contains("VideoDetailReturnSourceCardChrome("))
        assertTrue(holder.contains("VideoDetailReturnCoverChrome("))
        assertTrue(holder.contains("alpha = flyingSourceChromeAlphaProvider()"))
        val chrome = File(
            "app/src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailReturnSourceCardChrome.kt",
        ).takeIf { it.isFile }?.readText()
            ?: File(
                "src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailReturnSourceCardChrome.kt",
            ).readText()
        assertTrue(chrome.contains("nativeInfoSlot("))
        assertTrue(chrome.contains("widthPx * inverse.scaleX"))
        assertTrue(chrome.contains("heightPx * inverse.scaleY"))
        assertTrue(chrome.contains("lastClickedNativeCoverOverlayLayer"))
        assertTrue(chrome.contains("shouldDrawNativeCoverOverlay("))
        // Include the full multi-line ownership guard before each chrome call.
        val coverChromeGuard = holder
            .substringBefore("VideoDetailReturnCoverChrome(")
            .takeLast(1_000)
        // 源码里 `if (` 与条件分行，故断言条件标识符本身而非单行字面量。
        assertTrue(coverChromeGuard.contains("miuixVisualAssetsActive"))
        assertTrue(coverChromeGuard.contains("shouldDrawFlyingReconstructedSourceChrome("))
        assertFalse(coverChromeGuard.contains("lastClickedNativeCardLayer == null"))
        val sourceChromeGuard = holder
            .substringBefore("VideoDetailReturnSourceCardChrome(")
            .takeLast(1_400)
        assertTrue(sourceChromeGuard.contains("miuixVisualAssetsActive"))
        assertTrue(sourceChromeGuard.contains("shouldDrawFlyingReconstructedSourceChrome("))
    }

    @Test
    fun reconstructedFlyingChromePaintsForTheWholeCardMorph() {
        assertTrue(
            shouldDrawFlyingReconstructedSourceChrome(
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                isReturnGestureInProgress = false,
            )
        )
        assertTrue(
            shouldDrawFlyingReconstructedSourceChrome(
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                isReturnGestureInProgress = false,
            )
        )
        assertTrue(
            shouldDrawFlyingReconstructedSourceChrome(
                phase = VideoCardTransitionBackgroundPhase.HELD,
                isReturnGestureInProgress = true,
            )
        )
        assertFalse(
            shouldPunchThroughFlyingMediaToNativeListCard(
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                isReturnGestureInProgress = false,
            )
        )
        assertFalse(
            shouldPunchThroughFlyingMediaToNativeListCard(
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                isReturnGestureInProgress = false,
            )
        )
    }

    @Test
    fun expandPlayerViewportForSharedReturn_whenExitOrGesture() {
        assertTrue(
            shouldExpandPlayerViewportForSharedReturn(
                isExitTransitionInProgress = true,
                isReturnGestureInProgress = false,
            )
        )
        assertTrue(
            shouldExpandPlayerViewportForSharedReturn(
                isExitTransitionInProgress = false,
                isReturnGestureInProgress = true,
            )
        )
        assertTrue(
            shouldExpandPlayerViewportForSharedReturn(
                isExitTransitionInProgress = false,
                isReturnGestureInProgress = false,
                isGestureRestoreInProgress = true,
            )
        )
        assertFalse(
            shouldExpandPlayerViewportForSharedReturn(
                isExitTransitionInProgress = false,
                isReturnGestureInProgress = false,
            )
        )
        assertFalse(
            shouldExpandPlayerViewportForSharedReturn(
                isExitTransitionInProgress = true,
                isReturnGestureInProgress = true,
                sharedReturnLikely = false,
            )
        )
        assertEquals(
            0f,
            resolvePlayerCollapseProgressForLayout(
                manualOrCompactCollapseProgress = 0.85f,
                expandForSharedReturn = true,
            ),
            0.0001f,
        )
        assertEquals(
            0.85f,
            resolvePlayerCollapseProgressForLayout(
                manualOrCompactCollapseProgress = 0.85f,
                expandForSharedReturn = false,
            ),
            0.0001f,
        )
        assertEquals(0f, resolveVideoDetailCollapseShadowAlpha(0f), 0.0001f)
        assertEquals(0.08f, resolveVideoDetailCollapseShadowAlpha(0.5f), 0.0001f)
        assertEquals(0.16f, resolveVideoDetailCollapseShadowAlpha(1f), 0.0001f)
    }

    @Test
    fun exitTransitionInProgressFallsBackToCardClockReturning() {
        assertTrue(
            shouldTreatVideoDetailExitTransitionInProgress(
                animatedVisibilityTargetIsPostExit = true,
                videoCardBackgroundPhase = VideoCardTransitionBackgroundPhase.HELD,
            )
        )
        assertTrue(
            shouldTreatVideoDetailExitTransitionInProgress(
                animatedVisibilityTargetIsPostExit = false,
                videoCardBackgroundPhase = VideoCardTransitionBackgroundPhase.RETURNING,
            )
        )
        assertFalse(
            shouldTreatVideoDetailExitTransitionInProgress(
                animatedVisibilityTargetIsPostExit = false,
                videoCardBackgroundPhase = VideoCardTransitionBackgroundPhase.HELD,
            )
        )
        assertFalse(
            shouldTreatVideoDetailExitTransitionInProgress(
                animatedVisibilityTargetIsPostExit = false,
                videoCardBackgroundPhase = null,
            )
        )
    }

    @Test
    fun `immediate video back target keeps secondary content visible`() {
        assertFalse(
            shouldUseVideoDetailRootTransitionProgress(
                detailShellSharedBoundsEnabled = true,
                hasAnimatedVisibilityScope = true,
                keepLoadedContentForBackPreview = true,
            )
        )
    }

    @Test
    fun `related detail return suppresses enter fade flash while keeping exit fade available`() {
        assertTrue(
            shouldSuppressVideoDetailEnterFadeAfterBackPreview(
                wasKeptAsBackPreview = true,
                keepLoadedContentForBackPreview = false,
            )
        )
        assertFalse(
            shouldSuppressVideoDetailEnterFadeAfterBackPreview(
                wasKeptAsBackPreview = false,
                keepLoadedContentForBackPreview = false,
            )
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnContentAlpha(
                transitionProgress = 0.2f,
                isCommittedCardReturn = false,
                holdFullyOpaqueAfterBackPreview = true,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailReturnContentAlpha(
                transitionProgress = 0.2f,
                isCommittedCardReturn = true,
                holdFullyOpaqueAfterBackPreview = true,
            ),
            0.0001f,
        )
    }

    @Test
    fun `normal card detail transition still animates secondary content`() {
        assertTrue(
            shouldUseVideoDetailRootTransitionProgress(
                detailShellSharedBoundsEnabled = true,
                hasAnimatedVisibilityScope = true,
                keepLoadedContentForBackPreview = false,
            )
        )
    }

    @Test
    fun `shared return morph keeps playback session after stack pop`() {
        // 轻滑即松：栈已非详情但 exit morph 仍在 → 必须保活，否则前半段黑壳。
        assertTrue(
            shouldKeepPlaybackSessionActiveForSharedReturnMorph(
                isVisible = false,
                sharedBoundsActive = true,
                isExitTransitionInProgress = true,
            ),
        )
        assertTrue(
            shouldKeepPlaybackSessionActiveForSharedReturnMorph(
                isVisible = true,
                sharedBoundsActive = false,
                isExitTransitionInProgress = false,
            ),
        )
        assertFalse(
            shouldKeepPlaybackSessionActiveForSharedReturnMorph(
                isVisible = false,
                sharedBoundsActive = true,
                isExitTransitionInProgress = false,
            ),
        )
        assertFalse(
            shouldKeepPlaybackSessionActiveForSharedReturnMorph(
                isVisible = false,
                sharedBoundsActive = false,
                isExitTransitionInProgress = true,
            ),
        )
    }

    @Test
    fun `predictive cancel keeps the cover until the detail exit transition has settled`() {
        assertTrue(
            shouldTreatVideoDetailCardExitAsReturning(
                isExitTransitionInProgress = true,
                sharedBoundsActive = true,
            )
        )
        assertFalse(
            shouldTreatVideoDetailCardExitAsReturning(
                isExitTransitionInProgress = false,
                sharedBoundsActive = true,
            )
        )
    }

    @Test
    fun `detail used as immediate back target keeps its loaded player and controls`() {
        assertFalse(
            shouldTreatVideoDetailCardExitAsReturning(
                isExitTransitionInProgress = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = true,
            )
        )
    }

    @Test
    fun `video target without player ownership uses its own cover behind the live outgoing video`() {
        assertTrue(
            shouldForceBackPreviewPlayerCover(
                keepLoadedContentForBackPreview = true,
                bindLivePlayerForBackPreview = false
            )
        )
        assertFalse(
            shouldForceBackPreviewPlayerCover(
                keepLoadedContentForBackPreview = true,
                bindLivePlayerForBackPreview = true
            )
        )
    }

    @Test
    fun `immediate back target mounts the live inline player`() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt")
            .readText()
        val inlinePlayerCall = source
            .substringAfter("PortraitInlineVideoPlayerHost(", "")
            .substringBefore("allowLivePlayerSharedElement = allowLivePlayerSharedElement")

        assertTrue(inlinePlayerCall.contains("liveBackPreview = bindLivePlayerForBackPreview"))
    }

    @Test
    fun `detail route does not manually fade its background during return`() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt")
            .readText()

        assertFalse(source.contains("resolveVideoDetailShellBackgroundAlphaTarget"))
        assertFalse(source.contains("shellBackgroundAlpha"))
    }

    @Test
    fun `force cover becomes active when explicit return flag is true`() {
        assertTrue(
            resolveForceCoverOnlyForReturn(
                forceCoverOnlyOnReturn = true
            )
        )
    }

    @Test
    fun `global returning state does not force the target detail cover`() {
        assertFalse(
            resolveForceCoverOnlyForReturn(
                forceCoverOnlyOnReturn = false
            )
        )
    }

    @Test
    fun `detail shell shared bounds does not disable return cover visual`() {
        assertTrue(
            resolveForceCoverOnlyForReturn(
                forceCoverOnlyOnReturn = true
            )
        )
        val source = File("src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailTransitionPolicy.kt")
            .readText()
        val policyBlock = source
            .substringAfter("internal fun resolveForceCoverOnlyForReturn(")
            .substringBefore("internal fun shouldUseReturningVideoDetailVisualState")
        assertFalse(policyBlock.contains("detailShellSharedBoundsEnabled"))
    }

    @Test
    fun `force cover stays disabled when shared transition is disabled`() {
        assertFalse(
            resolveForceCoverOnlyForReturn(
                forceCoverOnlyOnReturn = true,
                transitionEnabled = false
            )
        )
    }

    @Test
    fun `force cover stays disabled when only exit transition is in progress`() {
        assertFalse(
            resolveForceCoverOnlyForReturn(
                forceCoverOnlyOnReturn = false
            )
        )
    }

    @Test
    fun `predictive card return keeps the live player instead of forcing cover`() {
        assertFalse(
            resolveForceCoverOnlyForReturn(
                forceCoverOnlyOnReturn = false,
                isCardReturnExitInProgress = true
            )
        )
    }

    @Test
    fun `force cover stays disabled during predictive card return exit when transition disabled`() {
        assertFalse(
            resolveForceCoverOnlyForReturn(
                forceCoverOnlyOnReturn = false,
                transitionEnabled = false,
                isCardReturnExitInProgress = true
            )
        )
    }

    @Test
    fun `force cover stays disabled when no return state is active`() {
        assertFalse(
            resolveForceCoverOnlyForReturn(
                forceCoverOnlyOnReturn = false
            )
        )
    }

    @Test
    fun `idle detail does not use returning visual without exit or force cover`() {
        assertFalse(
            shouldUseReturningVideoDetailVisualState(
                forceCoverOnlyForReturn = false,
                isCardReturnExitInProgress = false,
                isSessionReturningToCard = false,
            )
        )
    }

    @Test
    fun `committed card return exit enables cover handoff without forceCoverOnly`() {
        assertTrue(
            shouldUseReturningVideoDetailVisualState(
                forceCoverOnlyForReturn = false,
                isCardReturnExitInProgress = true,
            )
        )
        assertTrue(
            shouldUseReturningVideoDetailVisualState(
                forceCoverOnlyForReturn = false,
                isSessionReturningToCard = true,
            )
        )
        assertFalse(
            resolveForceCoverOnlyForReturn(
                forceCoverOnlyOnReturn = false,
                isCardReturnExitInProgress = true,
            )
        )
    }

    @Test
    fun `predictive seek exit is leaving but not committed until markReturning`() {
        // targetState=PostExit 的 seek：离开态 true，但尚未松手提交
        assertTrue(
            shouldUseReturningVideoDetailVisualState(
                forceCoverOnlyForReturn = false,
                isCardReturnExitInProgress = true,
                isSessionReturningToCard = false,
            )
        )
        assertFalse(
            shouldTreatVideoDetailCardReturnAsCommitted(
                isActuallyLeaving = false,
                isSessionReturningToCard = false,
            )
        )
        // 松手提交 / 按钮返回后才 committed
        assertTrue(
            shouldTreatVideoDetailCardReturnAsCommitted(
                isActuallyLeaving = false,
                isSessionReturningToCard = true,
            )
        )
        assertTrue(
            shouldTreatVideoDetailCardReturnAsCommitted(
                isActuallyLeaving = true,
                isSessionReturningToCard = false,
            )
        )
    }

    @Test
    fun `predictive seek uses the same live to cover handoff as committed return`() {
        // depth=0.7 尚未进入 82%–98% 媒体接管窗口。
        assertEquals(
            0f,
            resolveVideoDetailReturnCoverAlpha(
                transitionProgress = 0.7f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                liveReturnMorph = true,
                keepLivePlayerForPredictiveBack = true,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnPlayerAlpha(
                transitionProgress = 0.7f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                liveReturnMorph = true,
                keepLivePlayerForPredictiveBack = true,
            ),
            0.0001f,
        )
        // 已提交但未到 handoff 窗口：player 仍满不透明
        assertEquals(
            0f,
            resolveVideoDetailReturnCoverAlpha(
                transitionProgress = 0.5f,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                liveReturnMorph = true,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnPlayerAlpha(
                transitionProgress = 0.5f,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                liveReturnMorph = true,
            ),
            0.0001f,
        )
    }

    @Test
    fun `resident path forces cover during predictive return`() {
        assertTrue(
            com.android.purebilibili.core.ui.transition.shouldForceCoverOnlyForReturnOwnership(
                ownership = com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.RESIDENT_COVER,
                useReturningVisualState = true,
                forceCoverOnlyOnReturn = false,
                isCommittedCardReturn = false,
            )
        )
        assertTrue(
            com.android.purebilibili.core.ui.transition.shouldForceCoverOnlyForReturnOwnership(
                ownership = com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.RESIDENT_COVER,
                useReturningVisualState = true,
                forceCoverOnlyOnReturn = false,
                isCommittedCardReturn = true,
            )
        )
    }

    @Test
    fun `detail state holder splits committed return from predictive exit leaving`() {
        val source = File(
            "src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt"
        ).readText()
        assertTrue(source.contains("shouldTreatVideoDetailCardReturnAsCommitted("))
        assertTrue(source.contains("isCommittedCardReturn = isCommittedCardReturn"))
        assertFalse(source.contains("isCommittedCardReturn = isLeaving"))
        assertTrue(source.contains("isCommittedCardReturn = isCommittedCardReturn,"))
    }

    @Test
    fun `live return morph keeps player visible before the landing handoff`() {
        // 封面现在位于播放器上层；进入窗口前保持透明。
        assertEquals(
            0f,
            resolveVideoDetailReturnCoverAlpha(0.8f, true, true, liveReturnMorph = true),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnPlayerAlpha(0.8f, true, true, liveReturnMorph = true),
            0.0001f,
        )
        // 正文/控制器在形变窗口前仍完整可见。
        assertEquals(
            1f,
            resolveVideoDetailReturnContentAlpha(0.85f, true, liveReturnMorph = true),
            0.0001f,
        )
        // 后段详情信息开始变换为来源卡标题/统计，但飞行卡壳保持不透明。
        val lateContent = resolveVideoDetailReturnContentAlpha(0.2f, true, liveReturnMorph = true)
        assertEquals(0.6666667f, lateContent, 0.0001f)
    }

    @Test
    fun `committed live return transforms player into cover inside the flying card`() {
        // Flying media slot stays opaque so the cover clip is never an empty plate.
        assertEquals(
            0.5f,
            resolveVideoDetailReturnCoverAlpha(0.1f, true, true, liveReturnMorph = true),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnPlayerAlpha(0.1f, true, true, liveReturnMorph = true),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnCoverAlpha(0.02f, true, true, liveReturnMorph = true),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailReturnPlayerAlpha(0.02f, true, true, liveReturnMorph = true),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnCoverAlpha(0f, true, true, liveReturnMorph = true),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailReturnPlayerAlpha(0f, true, true, liveReturnMorph = true),
            0.0001f,
        )
    }

    @Test
    fun `predictive commit keeps live handoff on continuous morph clock`() {
        // 松手提交时 AVS 可能短暂投影到 PostExit=0；实时路径必须继续使用手势提交点，
        // 否则 0 会被误判为完全落位，常驻封面盖住播放器一帧。
        val progress = resolveVideoDetailReturnVisualProgress(
            animatedVisibilityProgress = 0f,
            morphDepthProgress = 0.72f,
            liveReturnMorph = true,
        )
        assertEquals(0.72f, progress, 0.0001f)
        assertEquals(
            0f,
            resolveVideoDetailReturnCoverAlpha(
                transitionProgress = progress,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                liveReturnMorph = true,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnPlayerAlpha(
                transitionProgress = progress,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                liveReturnMorph = true,
            ),
            0.0001f,
        )
    }

    @Test
    fun `resident return keeps animated visibility progress`() {
        assertEquals(
            0.24f,
            resolveVideoDetailReturnVisualProgress(
                animatedVisibilityProgress = 0.24f,
                morphDepthProgress = 0.72f,
                liveReturnMorph = false,
            ),
            0.0001f,
        )
    }

    @Test
    fun `quick committed live return still follows the flying card content timeline`() {
        assertEquals(
            1f,
            resolveVideoDetailReturnContentAlpha(
                transitionProgress = 0.9f,
                isCommittedCardReturn = true,
                liveReturnMorph = true,
                isQuickReturn = true,
            ),
            0.0001f,
        )
        assertEquals(
            0.6666667f,
            resolveVideoDetailReturnContentAlpha(
                transitionProgress = 0.2f,
                isCommittedCardReturn = true,
                liveReturnMorph = true,
                isQuickReturn = true,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnContentAlpha(
                transitionProgress = 0.9f,
                isCommittedCardReturn = false,
                liveReturnMorph = true,
                isQuickReturn = true,
                morphDepthProgress = 0.9f,
            ),
            0.0001f,
        )
    }

    @Test
    fun `live morph content follows only the shared flying card clock`() {
        val content = resolveVideoDetailReturnContentAlpha(
            transitionProgress = 0.95f,
            isCommittedCardReturn = true,
            liveReturnMorph = true,
            depthBlurProgress = 0.95f,
            morphDepthProgress = 0.2f,
        )
        assertEquals(0.6666667f, content, 0.0001f)
        assertEquals(
            resolveVideoDetailReturnContentAlpha(
                transitionProgress = 0.2f,
                isCommittedCardReturn = true,
                liveReturnMorph = true,
                morphDepthProgress = 0.2f,
            ),
            content,
            0.0001f,
        )
    }

    @Test
    fun `return session prefers LIVE upgrade and blocks LIVE demotion`() {
        val lockedResident = resolveVideoDetailReturnSessionLockedOwnership(
            lockedOwnership = null,
            isReturnSessionActive = true,
            candidateOwnership = com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.RESIDENT_COVER,
        )
        assertEquals(
            com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.RESIDENT_COVER,
            lockedResident.second,
        )
        // 首帧到达：升 LIVE，实时画面跟壳缩
        val upgraded = resolveVideoDetailReturnSessionLockedOwnership(
            lockedOwnership = lockedResident.first,
            isReturnSessionActive = true,
            candidateOwnership = com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.LIVE_SURFACE,
        )
        assertEquals(
            com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.LIVE_SURFACE,
            upgraded.second,
        )
        // 中途 candidate 变 RESIDENT：不得掐 live
        val stillLive = resolveVideoDetailReturnSessionLockedOwnership(
            lockedOwnership = upgraded.first,
            isReturnSessionActive = true,
            candidateOwnership = com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.RESIDENT_COVER,
        )
        assertEquals(
            com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.LIVE_SURFACE,
            stillLive.second,
        )
        val cleared = resolveVideoDetailReturnSessionLockedOwnership(
            lockedOwnership = stillLive.first,
            isReturnSessionActive = false,
            candidateOwnership = com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.RESIDENT_COVER,
        )
        assertEquals(null, cleared.first)
        assertEquals(
            com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.RESIDENT_COVER,
            cleared.second,
        )
    }

    @Test
    fun `cover-first committed return still hands visual ownership to resident cover`() {
        // 关实时画面：resident cover 从返回起点直接接管。
        assertEquals(
            1f,
            resolveVideoDetailReturnCoverAlpha(0.8f, true, true, liveReturnMorph = false),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailReturnPlayerAlpha(0.8f, true, true, liveReturnMorph = false),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailReturnContentAlpha(0.8f, true, liveReturnMorph = false),
            0.0001f,
        )
    }

    @Test
    fun `uncommitted predictive live morph keeps content before the late transform window`() {
        // 非返回态：上层封面透明，player 正常显示。
        assertEquals(
            0f,
            resolveVideoDetailReturnCoverAlpha(0.8f, false, true, liveReturnMorph = true),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnPlayerAlpha(0.8f, false, true, liveReturnMorph = true),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnContentAlpha(0.85f, false, liveReturnMorph = true),
            0.0001f,
        )
        // 手势进入后段时，即使尚未提交，也应在飞行卡内把 player 变成封面。
        assertEquals(
            0.5f,
            resolveVideoDetailReturnCoverAlpha(
                transitionProgress = 0.1f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                liveReturnMorph = true,
                keepLivePlayerForPredictiveBack = true,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnPlayerAlpha(
                transitionProgress = 0.1f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                liveReturnMorph = true,
                keepLivePlayerForPredictiveBack = true,
            ),
            0.0001f,
        )
    }

    @Test
    fun `entry keeps resident cover above black surface until first player frame`() {
        assertEquals(
            1f,
            resolveVideoDetailReturnCoverAlpha(
                transitionProgress = 0f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                showResidentCoverUntilFirstFrame = true,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailReturnPlayerAlpha(
                transitionProgress = 0f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                showResidentCoverUntilFirstFrame = true,
            ),
            0.0001f,
        )
    }

    @Test
    fun `predictive cover-first return immediately uses resident cover`() {
        assertEquals(
            1f,
            resolveVideoDetailReturnCoverAlpha(
                transitionProgress = 0.8f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                liveReturnMorph = false,
                keepLivePlayerForPredictiveBack = true,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailReturnPlayerAlpha(
                transitionProgress = 0.8f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                liveReturnMorph = false,
                keepLivePlayerForPredictiveBack = true,
            ),
            0.0001f,
        )
        assertEquals(
            0.8f,
            resolveVideoDetailReturnContentAlpha(0.8f, false, liveReturnMorph = false),
            0.0001f,
        )
    }

    @Test
    fun `cover-first restore keeps resident cover until the gesture session ends`() {
        assertEquals(
            1f,
            resolveVideoDetailReturnCoverAlpha(
                transitionProgress = 0.6f,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                liveReturnMorph = false,
                keepLivePlayerForPredictiveBack = true,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailReturnPlayerAlpha(
                transitionProgress = 0.6f,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                liveReturnMorph = false,
                keepLivePlayerForPredictiveBack = true,
            ),
            0.0001f,
        )
    }

    @Test
    fun `live return morph gate requires immediate playback and active shared bounds`() {
        assertTrue(
            shouldUseLiveReturnMorph(
                transitionEnabled = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = false,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
                detailContentReady = true,
                liveSurfaceCardTransitionEnabled = true,
            )
        )
        assertFalse(
            shouldUseLiveReturnMorph(
                transitionEnabled = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = false,
                playbackIntent = VideoSharedTransitionPlaybackIntent.CoverFirst,
                detailContentReady = true,
                liveSurfaceCardTransitionEnabled = true,
            )
        )
        assertFalse(
            shouldUseLiveReturnMorph(
                transitionEnabled = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = true,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
                detailContentReady = true,
                liveSurfaceCardTransitionEnabled = true,
            )
        )
        assertFalse(
            shouldUseLiveReturnMorph(
                transitionEnabled = true,
                sharedBoundsActive = false,
                keepLoadedContentForBackPreview = false,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
                detailContentReady = true,
                liveSurfaceCardTransitionEnabled = true,
            )
        )
    }

    @Test
    fun `live return morph disabled while detail content still loading to avoid skeleton in card`() {
        assertFalse(
            shouldTreatVideoDetailContentReadyForLiveReturnMorph(
                hasSuccessfulDetailContent = false,
            )
        )
        assertTrue(
            shouldTreatVideoDetailContentReadyForLiveReturnMorph(
                hasSuccessfulDetailContent = true,
            )
        )
        assertFalse(
            shouldUseLiveReturnMorph(
                transitionEnabled = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = false,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
                detailContentReady = false,
            )
        )
    }

    @Test
    fun `resident cover ownership is suppressed during live return morph`() {
        assertFalse(
            shouldHandVisualOwnershipToResidentCover(
                useReturningVisualState = true,
                hasResidentCover = true,
                liveReturnMorph = true,
            )
        )
        assertTrue(
            shouldHandVisualOwnershipToResidentCover(
                useReturningVisualState = true,
                hasResidentCover = true,
                liveReturnMorph = false,
            )
        )
        assertFalse(
            shouldHandVisualOwnershipToResidentCover(
                useReturningVisualState = true,
                hasResidentCover = false,
                liveReturnMorph = false,
            )
        )
    }

    @Test
    fun `detail return cover ownership table matches timeline contract`() {
        val live = resolveVideoDetailReturnCoverOwnership(
            transitionEnabled = true,
            sharedBoundsActive = true,
            keepLoadedContentForBackPreview = false,
            playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
            detailContentReady = true,
            hasResidentCover = true,
            hasRenderableLiveFrame = true,
            liveSurfaceCardTransitionEnabled = true,
        )
        assertTrue(isLiveReturnMorphFromOwnership(live))
        assertFalse(
            shouldHandResidentCoverFromOwnership(
                ownership = live,
                useReturningVisualState = true,
                hasResidentCover = true,
            )
        )

        val coverFirst = resolveVideoDetailReturnCoverOwnership(
            transitionEnabled = true,
            sharedBoundsActive = true,
            keepLoadedContentForBackPreview = false,
            playbackIntent = VideoSharedTransitionPlaybackIntent.CoverFirst,
            detailContentReady = true,
            hasResidentCover = true,
            liveSurfaceCardTransitionEnabled = true,
        )
        assertFalse(isLiveReturnMorphFromOwnership(coverFirst))
        assertTrue(
            shouldHandResidentCoverFromOwnership(
                ownership = coverFirst,
                useReturningVisualState = true,
                hasResidentCover = true,
            )
        )

        val noFrame = resolveVideoDetailReturnCoverOwnership(
            transitionEnabled = true,
            sharedBoundsActive = true,
            keepLoadedContentForBackPreview = false,
            playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
            detailContentReady = true,
            hasResidentCover = true,
            hasRenderableLiveFrame = false,
            liveSurfaceCardTransitionEnabled = true,
        )
        assertFalse(isLiveReturnMorphFromOwnership(noFrame))
        assertTrue(
            shouldHandResidentCoverFromOwnership(
                ownership = noFrame,
                useReturningVisualState = true,
                hasResidentCover = true,
            )
        )
    }

    @Test
    fun `cover-first entry upgrades to live surface after current video renders`() {
        val returnIntent = resolveVideoDetailReturnPlaybackIntent(
            entryPlaybackIntent = VideoSharedTransitionPlaybackIntent.CoverFirst,
            hasRenderableLiveFrame = true,
        )

        assertEquals(VideoSharedTransitionPlaybackIntent.ImmediatePlayback, returnIntent)
        val ownership = resolveVideoDetailReturnCoverOwnership(
            transitionEnabled = true,
            sharedBoundsActive = true,
            keepLoadedContentForBackPreview = false,
            playbackIntent = returnIntent,
            detailContentReady = true,
            hasResidentCover = true,
            hasRenderableLiveFrame = true,
            liveSurfaceCardTransitionEnabled = true,
        )
        assertTrue(isLiveReturnMorphFromOwnership(ownership))
        assertEquals(
            0f,
            resolveVideoDetailReturnCoverAlpha(
                transitionProgress = 0.5f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                liveReturnMorph = true,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnPlayerAlpha(
                transitionProgress = 0.5f,
                isCommittedCardReturn = false,
                hasResidentCover = true,
                liveReturnMorph = true,
            ),
            0.0001f,
        )
    }

    @Test
    fun `cover-first entry keeps resident cover before first frame`() {
        assertEquals(
            VideoSharedTransitionPlaybackIntent.CoverFirst,
            resolveVideoDetailReturnPlaybackIntent(
                entryPlaybackIntent = VideoSharedTransitionPlaybackIntent.CoverFirst,
                hasRenderableLiveFrame = false,
            ),
        )
    }

    @Test
    fun `detail state holder derives return intent from rendered live frame`() {
        val source = File(
            "src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt"
        ).readText()

        assertTrue(source.contains("val returnPlaybackIntent = resolveVideoDetailReturnPlaybackIntent("))
        assertTrue(source.contains("hasRenderableLiveFrame = hasRenderableLiveFrameForReturn"))
        assertTrue(source.contains("playbackIntent = returnPlaybackIntent"))
    }

    @Test
    fun `detail state holder always keeps live return preview without settings gate`() {
        val source = File(
            "src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt"
        ).readText()

        assertFalse(source.contains("getVideoTransitionLiveReturnPreviewEnabled"))
        assertFalse(source.contains("liveReturnPreviewEnabled"))
    }

    @Test
    fun `live return morph stays enabled when gate conditions are met`() {
        assertEquals(
            com.android.purebilibili.core.ui.transition.VideoCardReturnCoverOwnership.LIVE_SURFACE,
            resolveVideoDetailReturnCoverOwnership(
                transitionEnabled = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = false,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
                detailContentReady = true,
                hasResidentCover = true,
                hasRenderableLiveFrame = true,
                liveSurfaceCardTransitionEnabled = true,
            ),
        )
        assertTrue(
            shouldUseLiveReturnMorph(
                transitionEnabled = true,
                sharedBoundsActive = true,
                keepLoadedContentForBackPreview = false,
                playbackIntent = VideoSharedTransitionPlaybackIntent.ImmediatePlayback,
                detailContentReady = true,
                hasRenderableLiveFrame = true,
                liveSurfaceCardTransitionEnabled = true,
            ),
        )
    }

    @Test
    fun `missing return cover with live-surface off uses black shell not forced player`() {
        // 无 resident cover 时保留 player，避免黑壳。
        assertEquals(0f, resolveVideoDetailReturnCoverAlpha(0.2f, true, false), 0.0001f)
        assertEquals(1f, resolveVideoDetailReturnPlayerAlpha(0.2f, true, false), 0.0001f)
        assertEquals(0f, resolveVideoDetailReturnContentAlpha(0.2f, true), 0.0001f)
    }

    @Test
    fun `live media slot crossfades internally while fallback content retains root progress`() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt")
            .readText()

        assertTrue(source.contains("val detailTransitionProgress ="))
        assertTrue(source.contains("alpha = resolveVideoDetailReturnMediaFrame("))
        assertTrue(source.contains(").coverAlpha"))
        assertTrue(source.contains(").playerAlpha"))
        assertTrue(source.contains(".zIndex(1f)"))
        assertTrue(source.contains("alpha = resolveVideoDetailReturnContentAlpha("))
        assertFalse(source.contains("val coverCrossfadeAlpha ="))
        assertFalse(source.contains("val playerFadeAlpha ="))
    }

    @Test
    fun `shared shell keeps detail content mounted while root transition owns alpha`() {
        assertTrue(
            shouldShowVideoDetailContent(
                isTransitionFinished = true,
                isLeaving = true,
                rootTransitionOwnsContentAlpha = true,
            )
        )
        assertFalse(
            shouldShowVideoDetailContent(
                isTransitionFinished = true,
                isLeaving = true,
                rootTransitionOwnsContentAlpha = false,
            )
        )
    }

    @Test
    fun `related back preview keeps parent content mounted during return morph`() {
        assertTrue(
            shouldShowVideoDetailContent(
                isTransitionFinished = false,
                isLeaving = false,
                rootTransitionOwnsContentAlpha = true,
                keepContentVisibleAfterBackPreview = true,
            )
        )
        assertFalse(
            shouldShowVideoDetailContent(
                isTransitionFinished = false,
                isLeaving = true,
                rootTransitionOwnsContentAlpha = false,
                keepContentVisibleAfterBackPreview = true,
            )
        )
    }

    @Test
    fun `flying entry mounts detail content before morph settles`() {
        // 进场：Miuix 飞行 entry 的根过渡已接管正文 alpha（父层 graphicsLayer 按 morph 进度
        // 淡入 + 自上而下裁剪），因此正文必须随 morph 提前挂载。否则数据到达后正文被
        // AnimatedVisibility 整块隐藏，只剩近白容器背景 → 「进详情页一片白遮罩」。
        assertTrue(
            shouldShowVideoDetailContent(
                isTransitionFinished = false,
                isLeaving = false,
                rootTransitionOwnsContentAlpha = true,
            )
        )
        // 无根过渡接管（非 morph 路径）时仍等 settle，避免提前挂载重内容。
        assertFalse(
            shouldShowVideoDetailContent(
                isTransitionFinished = false,
                isLeaving = false,
                rootTransitionOwnsContentAlpha = false,
            )
        )
        // 退场语义保持不变：未 finish 不得重新放行。
        assertFalse(
            shouldShowVideoDetailContent(
                isTransitionFinished = false,
                isLeaving = true,
                rootTransitionOwnsContentAlpha = true,
            )
        )
    }

    @Test
    fun `fresh detail body waits for landing while an already loaded preview stays mounted`() {
        assertFalse(shouldShowVideoDetailContent(
            isTransitionFinished = false,
            isLeaving = false,
            rootTransitionOwnsContentAlpha = true,
            deferFreshEntryContent = true,
        ))
        assertTrue(shouldShowVideoDetailContent(
            isTransitionFinished = true,
            isLeaving = false,
            rootTransitionOwnsContentAlpha = true,
            deferFreshEntryContent = true,
        ))
        assertTrue(shouldShowVideoDetailContent(
            isTransitionFinished = false,
            isLeaving = false,
            rootTransitionOwnsContentAlpha = true,
            keepContentVisibleAfterBackPreview = true,
            deferFreshEntryContent = true,
        ))
        assertFalse(shouldShowVideoDetailContent(
            isTransitionFinished = false,
            isLeaving = true,
            rootTransitionOwnsContentAlpha = true,
            keepContentVisibleAfterBackPreview = true,
            deferFreshEntryContent = true,
        ))
    }

    @Test
    fun `explicit force cover still switches detail into returning visual state`() {
        assertTrue(
            shouldUseReturningVideoDetailVisualState(
                forceCoverOnlyForReturn = true,
                isCardReturnExitInProgress = false,
            )
        )
    }

    @Test
    fun `returning visual is wired from exit progress and session for handoff`() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt")
            .readText()
        val transitionHostSource = File(
            "src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailTransitionHost.kt"
        ).readText()
        val call = source
            .substringAfter("val useReturningVideoDetailVisualState = shouldUseReturningVideoDetailVisualState(")
            .substringBefore("val handleTopBarAction")
        assertTrue(call.contains("isCardReturnExitInProgress = isCardReturnExitInProgress"))
        // session 先算 isSessionReturningToCard（含 transition/shared 门闩），再传入
        assertTrue(source.contains("val isSessionReturningToCard = isReturningFromDetail &&"))
        assertTrue(call.contains("isSessionReturningToCard = isSessionReturningToCard"))
        assertTrue(source.contains("shouldTreatVideoDetailCardReturnAsCommitted("))
        assertTrue(transitionHostSource.contains("video-detail-shared-morph-clock"))
    }

    @Test
    fun `resident cover starts return without a pre-navigation dead frame`() {
        assertEquals(0L, resolveCoverTakeoverDelayBeforeBackNavigationMillis())
    }

    @Test
    fun `resident return cover reuses the home card image cache`() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt")
            .readText()
        val requestBlock = source
            .substringAfter("val residentCoverImageRequest =")
            .substringBefore("//  播放器容器按当前顶部避让高度计算")

        assertTrue(requestBlock.contains(".crossfade(false)"))
        assertTrue(requestBlock.contains(".placeholderMemoryCacheKey(sharedCoverCacheKey)"))
        assertTrue(requestBlock.contains(".memoryCacheKey(sharedCoverCacheKey)"))
        assertTrue(requestBlock.contains(".diskCacheKey(sharedCoverCacheKey)"))
    }

    @Test
    fun `navigation actions do not switch the loaded player to a cover`() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt")
            .readText()
        val actionBlock = source
            .substringAfter("action@{ action: VideoDetailTopBarAction ->")
            .substringBefore("val handleBack =")

        assertFalse(actionBlock.contains("forceCoverOnlyOnReturn = true"))
        assertTrue(source.contains("useTextureSurfaceForNavigation = useTextureSurfaceForNavigation"))
        assertTrue(source.contains("resolveNavigationLiveSurfaceTextureEnabled("))
        assertTrue(source.contains("getLiveSurfaceCardTransitionEnabled("))
    }

    @Test
    fun `player container shared bounds are disabled during return to avoid cover key conflict`() {
        val source = File("src/main/java/com/android/purebilibili/feature/video/screen/VideoDetailScreenStateHolder.kt")
            .readText()
        // shell 生效时不挂 cover sharedBounds；forceCoverOnly 也禁止，避免与返回封面 key 冲突。
        assertTrue(source.contains("shouldAttachVideoDetailCoverSharedBounds("))
        val attachBlock = source
            .substringAfter("val attachPlayerCoverSharedBounds =")
            .substringBefore("val playerContainerModifier =")
        assertTrue(
            "Player container must not claim the cover shared bounds during return; the forced return cover overlay owns that key.",
            attachBlock.contains("forceCoverOnlyForReturn = forceCoverOnlyForReturn")
        )
        assertTrue(attachBlock.contains("detailShellSharedBoundsEnabled = detailShellSharedBoundsEnabled"))
        assertTrue(source.contains("resolveVideoCardSharedBoundsResizeMode("))
    }

    @Test
    fun committedReturn_transformsLivePlayerIntoResidentCoverBeforeLanding() {
        assertEquals(
            0f,
            resolveVideoDetailReturnCoverAlpha(
                transitionProgress = 1f,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                liveReturnMorph = true,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoDetailReturnPlayerAlpha(
                transitionProgress = 1f,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                liveReturnMorph = true,
            ),
            0.0001f,
        )
        // 非 live：resident cover 覆盖 player。
        assertEquals(
            1f,
            resolveVideoDetailReturnCoverAlpha(
                transitionProgress = 1f,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                liveReturnMorph = false,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoDetailReturnPlayerAlpha(
                transitionProgress = 1f,
                isCommittedCardReturn = true,
                hasResidentCover = true,
                liveReturnMorph = false,
            ),
            0.0001f,
        )
        // 接近落位时 player 已完成向封面的内部形变。
        val playerNearEnd = resolveVideoDetailReturnPlayerAlpha(
            transitionProgress = 0.01f,
            isCommittedCardReturn = true,
            hasResidentCover = true,
            liveReturnMorph = true,
        )
        assertEquals(0f, playerNearEnd, 0.0001f)
    }

    @Test
    fun residentCoverPrefersStationaryListSnapshotOverRouteCover() {
        val snapshot = com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot(
            title = "t",
            ownerName = "up",
            viewText = "1",
            danmakuText = "2",
            durationText = "1:00",
            coverUrl = "https://i0.hdslb.com/bfs/cover.jpg@640w_400h.webp",
            coverCacheKey = "cover_BV1_n_640x400",
            coverDecodeWidthPx = 640,
            coverDecodeHeightPx = 400,
        )
        val source = resolveVideoDetailResidentCoverSource(
            sourceChromeSnapshot = snapshot,
            routeCoverUrl = "https://i0.hdslb.com/bfs/cover.jpg",
            bvid = "BV1",
        )
        assertEquals(snapshot.coverUrl, source!!.url)
        assertEquals(snapshot.coverCacheKey, source.cacheKey)
        assertEquals(640, source.decodeWidthPx)
        assertEquals(400, source.decodeHeightPx)
    }

    @Test
    fun residentCoverUsesClickSnapshotThenPrefetchBeforeRoute() {
        val click = com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot(
            title = "t",
            ownerName = "up",
            viewText = "1",
            danmakuText = "2",
            durationText = "1:00",
            coverUrl = "https://list/cover@480w_300h.webp",
            coverCacheKey = "cover_BV1_n_480x300",
            coverDecodeWidthPx = 480,
            coverDecodeHeightPx = 300,
        )
        val fromClick = resolveVideoDetailResidentCoverSource(
            sourceChromeSnapshot = null,
            clickChromeSnapshot = click,
            prefetchUrl = "https://prefetch",
            prefetchCacheKey = "prefetch_key",
            routeCoverUrl = "https://route",
            bvid = "BV1",
        )
        assertEquals(click.coverUrl, fromClick!!.url)
        assertEquals(click.coverCacheKey, fromClick.cacheKey)

        val fromPrefetch = resolveVideoDetailResidentCoverSource(
            sourceChromeSnapshot = null,
            clickChromeSnapshot = null,
            prefetchUrl = "https://prefetch@640w_400h.webp",
            prefetchCacheKey = "cover_BV1_n_640x400",
            prefetchDecodeWidthPx = 640,
            prefetchDecodeHeightPx = 400,
            routeCoverUrl = "https://route",
            bvid = "BV1",
        )
        assertEquals("https://prefetch@640w_400h.webp", fromPrefetch!!.url)
        assertEquals("cover_BV1_n_640x400", fromPrefetch.cacheKey)
        assertEquals(640, fromPrefetch.decodeWidthPx)
        assertEquals(400, fromPrefetch.decodeHeightPx)
    }

    @Test
    fun residentCoverKeepsItsDecodeAndCacheIdentityAfterNavigationDropsSourceMetadata() {
        val retained = VideoDetailResidentCoverSource(
            url = "https://dynamic/cover.jpg",
            cacheKey = "dynamic_cover",
            decodeWidthPx = 640,
            decodeHeightPx = 400,
        )
        val source = resolveVideoDetailResidentCoverSource(
            sourceChromeSnapshot = null,
            clickChromeSnapshot = null,
            retainedStationarySource = retained,
            prefetchUrl = "https://home/other-crop.jpg",
            prefetchCacheKey = "home_cover",
            routeCoverUrl = "https://detail/other-crop.jpg",
            bvid = "BV1",
        )
        assertEquals(retained, source)
        // A nested destination can still supply its own confirmed source.
        val next = VideoCardSourceChromeSnapshot(
            title = "child",
            ownerName = "up",
            coverUrl = "https://related/child.jpg",
            coverCacheKey = "child_cover",
        )
        assertEquals(next.coverUrl, resolveVideoDetailResidentCoverSource(
            sourceChromeSnapshot = next,
            retainedStationarySource = retained,
            routeCoverUrl = "https://detail/other-crop.jpg",
            bvid = "BV2",
        )?.url)
    }

    @Test
    fun residentCoverFallsBackToRouteWhenSnapshotHasNoCover() {
        val source = resolveVideoDetailResidentCoverSource(
            sourceChromeSnapshot = com.android.purebilibili.core.ui.transition.VideoCardSourceChromeSnapshot(
                title = "t",
                ownerName = "up",
                viewText = "1",
                danmakuText = "2",
                durationText = "1:00",
            ),
            routeCoverUrl = "http://i0.hdslb.com/bfs/cover.jpg",
            bvid = "BV1",
        )
        assertEquals("https://i0.hdslb.com/bfs/cover.jpg", source!!.url)
        assertEquals(
            com.android.purebilibili.core.ui.transition.resolveVideoSharedCoverCacheKey("BV1"),
            source.cacheKey,
        )
    }

    @Test
    fun committedIdleReturnZeroesFlyingChromeInsteadOfInvertingDetailChrome() {
        // IDLE + 返回已提交时详情 chrome 已返回 0（让位给列表卡）。重建壳若继续沿用
        // `1f − detailChromeAlpha` 的互补关系，就会得到 1f —— 一块全不透明的信息板
        // 凭空盖在卡槽上，正是「白色遮罩」。两者必须同时为 0。
        assertEquals(
            0f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 1f,
                phase = VideoCardTransitionBackgroundPhase.IDLE,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
                isCommittedCardReturn = true,
            ),
            0.001f,
        )
        // 未提交的 IDLE 保持原语义：互补路径本身就让位（detailChromeAlpha = 1）。
        assertEquals(
            0f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 0f,
                phase = VideoCardTransitionBackgroundPhase.IDLE,
                isReturnGestureInProgress = false,
                sourceLayout = VideoCardSourceLayout.STACKED,
            ),
            0.001f,
        )
        // 预测返回手势进行中不走这条守卫，仍走互补路径：IDLE 下 depth 被强制为 0，
        // 手势分支取 `1f − handoff` 得 detailChromeAlpha = 1 ⇒ 重建壳 alpha = 0。
        // 若守卫不区分手势，手势起点会把重建壳拉满 1f（详情页此刻完全可见）。
        assertEquals(
            0f,
            resolveVideoDetailFlyingSourceChromeAlpha(
                morphDepthProgress = 0f,
                phase = VideoCardTransitionBackgroundPhase.IDLE,
                isReturnGestureInProgress = true,
                sourceLayout = VideoCardSourceLayout.STACKED,
                isCommittedCardReturn = true,
            ),
            0.001f,
        )
    }

    @Test
    fun ordinaryAndPredictiveReturnsKeepTheInfoBandMountedThroughoutHandoff() {
        for (gesture in listOf(false, true)) {
            assertTrue(
                shouldDrawFlyingReconstructedSourceChrome(
                    phase = VideoCardTransitionBackgroundPhase.RETURNING,
                    isReturnGestureInProgress = gesture,
                ),
            )
            for (depth in listOf(0.18f, 0.1f, 0.02f)) {
                val detailAlpha = com.android.purebilibili.core.ui.transition
                    .resolveVideoCardDetailChromeAlpha(
                        morphDepthProgress = depth,
                        phase = VideoCardTransitionBackgroundPhase.RETURNING,
                        isReturnGestureInProgress = gesture,
                    )
                val sourceAlpha = resolveVideoDetailFlyingSourceChromeAlpha(
                    morphDepthProgress = depth,
                    phase = VideoCardTransitionBackgroundPhase.RETURNING,
                    isReturnGestureInProgress = gesture,
                    sourceLayout = VideoCardSourceLayout.STACKED,
                )
                assertEquals(1f, detailAlpha + sourceAlpha, 0.001f)
                if (depth == 0.1f) assertEquals(0.5f, sourceAlpha, 0.001f)
            }
        }
        assertFalse(
            shouldDrawFlyingReconstructedSourceChrome(
                phase = VideoCardTransitionBackgroundPhase.IDLE,
                isReturnGestureInProgress = false,
            ),
        )
        assertTrue(
            shouldDrawFlyingReconstructedSourceChrome(
                phase = VideoCardTransitionBackgroundPhase.HELD,
                isReturnGestureInProgress = false,
                isGestureRestoreInProgress = true,
            ),
        )
    }

    @Test
    fun reconstructedInfoSurfacePaintsAnOpaqueBaseUnderTheTint() {
        val source = File(
            "src/main/java/com/android/purebilibili/feature/video/screen/" +
                "VideoDetailReturnSourceCardChrome.kt"
        ).readText()
        val opaqueBase = source.indexOf(".background(baseContainer, shape)")
        val tintLayer = source.indexOf(".background(surface.containerColor, shape)")
        // tint 路径下 containerColor 只有 ~0.85 alpha。若只铺这一层，下层详情正文会在
        // 信息槽里以 ~15% 透出，且 0.85×254 + 0.15×247 ≈ 254 比页面底色 247 更亮 ——
        // 观感就是一块盖住卡片的白色遮罩。必须先铺不透明 cardContainer。
        assertTrue(opaqueBase >= 0)
        assertTrue(tintLayer >= 0)
        assertTrue(opaqueBase < tintLayer)
    }
}
