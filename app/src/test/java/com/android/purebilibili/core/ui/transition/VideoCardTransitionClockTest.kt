package com.android.purebilibili.core.ui.transition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoCardTransitionClockTest {
    @Test
    fun navigationOwnerCannotBePreemptedBySharedOrFallbackCompletion() {
        val clock = VideoCardTransitionClock()
        clock.beginOpening("home")
        var depth = .6f
        clock.bindNavigationDriver(provider = { depth })
        clock.followNavigationDriver(VideoCardTransitionSettleState.AutoEnter)
        clock.reportSharedMorphProgress(1f, false)
        clock.markHeld()
        assertEquals(VideoCardTransitionBackgroundPhase.OPENING, clock.phase)
        assertEquals(.6f, clock.depthProgress())
        clock.beginReturning("home", clock.depthProgress())
        clock.followNavigationDriver(VideoCardTransitionSettleState.AutoReturn, -2f)
        assertEquals(.6f, clock.depthProgress())
        assertEquals(-2f, clock.initialVelocity)
        clock.markIdle()
        assertEquals(VideoCardTransitionBackgroundPhase.RETURNING, clock.phase)
        depth = 0f
        clock.followNavigationDriver(VideoCardTransitionSettleState.Idle)
        assertEquals(VideoCardTransitionBackgroundPhase.IDLE, clock.phase)
    }

    @Test
    fun predictiveCancelRestoresFromCurrentPositionWithoutOpeningReset() {
        val clock = VideoCardTransitionClock()
        clock.bindNavigationDriver(provider = { .4f })
        clock.followNavigationDriver(VideoCardTransitionSettleState.InteractiveSeek)
        clock.followNavigationDriver(VideoCardTransitionSettleState.CancelRestore, 1f)
        assertEquals(.4f, clock.depthProgress())
        assertTrue(clock.gestureRestoreInProgress)
        assertEquals(VideoCardTransitionSettleState.CancelRestore, clock.settleState)
        clock.followNavigationDriver(VideoCardTransitionSettleState.Held)
        assertFalse(clock.gestureRestoreInProgress)
    }

    @Test
    fun depthPriority_gestureBeatsSharedBeatsFallback() {
        assertEquals(
            0.4f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = 0.6f,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.HELD,
                sharedMorphActive = true,
                sharedMorphFraction = 0.9f,
                fallbackProgress = 0.2f,
            ),
            0.001f,
        )
        // RETURNING：shared 与 fallback 取较大值（shared 领先时跟 shared）
        assertEquals(
            0.75f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = null,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                sharedMorphActive = true,
                sharedMorphFraction = 0.75f,
                fallbackProgress = 0.1f,
            ),
            0.001f,
        )
        // RETURNING：shared 因 Exit.None 瞬间掉到 0 时，保留 fallback 消糊曲线
        assertEquals(
            0.8f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = null,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                sharedMorphActive = true,
                sharedMorphFraction = 0f,
                fallbackProgress = 0.8f,
            ),
            0.001f,
        )
        assertEquals(
            0.33f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = null,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                sharedMorphActive = false,
                sharedMorphFraction = 0.9f,
                fallbackProgress = 0.33f,
            ),
            0.001f,
        )
    }

    @Test
    fun predictiveGestureDepth_mapsBackProgressToBlurClearCurve() {
        assertEquals(
            1f,
            resolveVideoCardPredictiveGestureDepthProgress(
                phase = VideoCardTransitionBackgroundPhase.HELD,
                backProgress = 0f,
                gestureStartDepth = 1f,
            ),
            0.0001f,
        )
        assertEquals(
            0.5f,
            resolveVideoCardPredictiveGestureDepthProgress(
                phase = VideoCardTransitionBackgroundPhase.HELD,
                backProgress = 0.5f,
                gestureStartDepth = 1f,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoCardPredictiveGestureDepthProgress(
                phase = VideoCardTransitionBackgroundPhase.HELD,
                backProgress = 1f,
                gestureStartDepth = 1f,
            ),
            0.0001f,
        )
        // 开场中途手势：从当前开场 depth 线性消到 0
        assertEquals(
            0.3f,
            resolveVideoCardPredictiveGestureDepthProgress(
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                backProgress = 0.5f,
                gestureStartDepth = 0.6f,
            ),
            0.0001f,
        )
    }

    @Test
    fun heldRestoreUsesFallbackForClearToBlur() {
        assertEquals(
            0.35f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = null,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.HELD,
                sharedMorphActive = false,
                sharedMorphFraction = null,
                fallbackProgress = 0.35f,
                gestureRestoreInProgress = true,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = null,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.HELD,
                sharedMorphActive = false,
                sharedMorphFraction = null,
                fallbackProgress = 0.35f,
                gestureRestoreInProgress = false,
            ),
            0.0001f,
        )
    }

    @Test
    fun preferSharedOnlyWhenActiveAndHasFraction() {
        assertTrue(
            shouldPreferSharedMorphProgress(
                sharedMorphActive = true,
                hasSharedFraction = true,
                gestureActive = false,
            ),
        )
        assertFalse(
            shouldPreferSharedMorphProgress(
                sharedMorphActive = true,
                hasSharedFraction = true,
                gestureActive = true,
            ),
        )
        assertFalse(
            shouldPreferSharedMorphProgress(
                sharedMorphActive = false,
                hasSharedFraction = true,
                gestureActive = false,
            ),
        )
    }

    @Test
    fun morphFractionToSettle_isOneMinusMorph() {
        assertEquals(0f, morphFractionToReturnSettle(1f), 0.0001f)
        assertEquals(1f, morphFractionToReturnSettle(0f), 0.0001f)
        assertEquals(0.3f, morphFractionToReturnSettle(0.7f), 0.0001f)
    }

    @Test
    fun fallbackDuration_scalesWithDepthSpan() {
        assertEquals(
            360,
            resolveMorphAlignedFallbackDurationMs(
                timelineDurationMs = 360,
                startDepth = 1f,
                targetDepth = 0f,
            ),
        )
        assertEquals(
            180,
            resolveMorphAlignedFallbackDurationMs(
                timelineDurationMs = 360,
                startDepth = 0.5f,
                targetDepth = 0f,
            ),
        )
        assertEquals(
            0,
            resolveMorphAlignedFallbackDurationMs(
                timelineDurationMs = 360,
                startDepth = 0f,
                targetDepth = 0f,
            ),
        )
    }

    @Test
    fun clock_reportSharedMorph_drivesDepthWhileActive() {
        val clock = VideoCardTransitionClock()
        clock.beginOpening("home")
        clock.reportSharedMorphProgress(morphFraction = 0.4f, active = true)
        assertEquals(0.4f, clock.depthProgress(), 0.0001f)
        clock.reportSharedMorphProgress(morphFraction = 1f, active = false)
        assertEquals(VideoCardTransitionBackgroundPhase.HELD, clock.phase)
        // shared-only 进场结束后 fallback 可能仍为 0；HELD 合同仍是满糊。
        assertEquals(1f, clock.depthProgress(), 0.0001f)
    }

    @Test
    fun prearmedOpeningIsNotRestartedByTheNavHostObserver() {
        val clock = VideoCardTransitionClock()
        clock.beginOpeningIfNeeded("history")
        clock.reportSharedMorphProgress(morphFraction = 0.35f, active = true)

        clock.beginOpeningIfNeeded("history")

        assertEquals(VideoCardTransitionBackgroundPhase.OPENING, clock.phase)
        assertEquals("history", clock.sourceRoute)
        assertEquals(0.35f, clock.depthProgress(), 0.0001f)
    }

    @Test
    fun heldPhaseKeepsFullDepthWhenFallbackNeverSeeded() {
        assertEquals(
            1f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = null,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.HELD,
                sharedMorphActive = false,
                sharedMorphFraction = null,
                fallbackProgress = 0f,
            ),
            0.0001f,
        )
    }

    @Test
    fun returnSharedMorphEndDoesNotForceIdle() {
        val clock = VideoCardTransitionClock()
        clock.beginOpening("home")
        clock.reportSharedMorphProgress(morphFraction = 1f, active = false)
        assertEquals(VideoCardTransitionBackgroundPhase.HELD, clock.phase)
        clock.beginReturning("home", startDepth = 1f)
        // 详情 dispose / Exit.None：shared 结束且 fraction≈0 时仍保持 RETURNING，
        // 留给 Host fallback 跑完模糊→清晰后再 markIdle。
        clock.reportSharedMorphProgress(morphFraction = 0f, active = false)
        assertEquals(VideoCardTransitionBackgroundPhase.RETURNING, clock.phase)
    }

    @Test
    fun returnAfterHeldKeepsFullBlurUntilFallbackSnaps() {
        // shared-only 进场：fallback 仍为 0，HELD 合同 depth=1。
        assertEquals(
            1f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = null,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.HELD,
                sharedMorphActive = false,
                sharedMorphFraction = null,
                fallbackProgress = 0f,
            ),
            0.0001f,
        )
        // 刚 beginReturning：fallback 仍 0，floor 顶住满糊（否则首帧无模糊过程）
        assertEquals(
            1f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = null,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                sharedMorphActive = false,
                sharedMorphFraction = null,
                fallbackProgress = 0f,
                returnDepthFloor = 1f,
            ),
            0.0001f,
        )
        assertEquals(
            1f,
            resolveReturningDepthWithFloor(
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                fallbackProgress = 0f,
                returnDepthFloor = 1f,
            ),
            0.0001f,
        )
        // snap 后 floor 清空，fallback 动画 0.4→0 可见消糊
        assertEquals(
            0.4f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = null,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                sharedMorphActive = false,
                sharedMorphFraction = null,
                fallbackProgress = 0.4f,
                returnDepthFloor = null,
            ),
            0.0001f,
        )
        // shared 瞬间 0 时仍被 floor 顶住
        assertEquals(
            1f,
            resolveVideoCardClockDepthProgress(
                gestureBackProgress = null,
                gestureStartDepth = 1f,
                phase = VideoCardTransitionBackgroundPhase.RETURNING,
                sharedMorphActive = true,
                sharedMorphFraction = 0f,
                fallbackProgress = 0f,
                returnDepthFloor = 1f,
            ),
            0.0001f,
        )
    }

    @Test
    fun clock_beginReturning_setsDepthFloorSynchronously() {
        val clock = VideoCardTransitionClock()
        clock.beginOpening("home")
        clock.reportSharedMorphProgress(morphFraction = 1f, active = false)
        assertEquals(1f, clock.depthProgress(), 0.0001f)
        // fallback 未写入仍为 0
        clock.beginReturning("home", startDepth = 1f)
        assertEquals(VideoCardTransitionBackgroundPhase.RETURNING, clock.phase)
        assertEquals(1f, clock.returnDepthFloor)
        // 关键：snapFallback 之前 depth 已是满糊
        assertEquals(1f, clock.depthProgress(), 0.0001f)
    }

    @Test
    fun returnClearStartDepth_recoversFullBlurWhenHeldReadsZero() {
        assertEquals(
            1f,
            resolveVideoCardReturnClearStartDepth(
                phase = VideoCardTransitionBackgroundPhase.HELD,
                currentDepth = 0f,
            ),
            0.0001f,
        )
        assertEquals(
            0.6f,
            resolveVideoCardReturnClearStartDepth(
                phase = VideoCardTransitionBackgroundPhase.HELD,
                currentDepth = 0.6f,
            ),
            0.0001f,
        )
        assertEquals(
            0f,
            resolveVideoCardReturnClearStartDepth(
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                currentDepth = 0f,
            ),
            0.0001f,
        )
    }

    @Test
    fun timelineSpec_returnIsLinearEnterIsContinuity() {
        val spec = resolveVideoCardTimelineSpec(360)
        assertEquals(360, spec.durationMillis)
        assertEquals(0.5f, spec.returnEasing.transform(0.5f), 0.001f)
        // Continuity at 0.5 is not 0.5
        assertTrue(spec.enterEasing.transform(0.5f) > 0.5f)
    }

    @Test
    fun gestureStartDepth_heldAlwaysFullBlur() {
        assertEquals(
            1f,
            resolveVideoCardGestureStartDepth(
                phase = VideoCardTransitionBackgroundPhase.HELD,
                currentDepth = 0f,
            ),
        )
        assertEquals(
            1f,
            resolveVideoCardGestureStartDepth(
                phase = VideoCardTransitionBackgroundPhase.HELD,
                currentDepth = 0.4f,
            ),
        )
        assertEquals(
            0.55f,
            resolveVideoCardGestureStartDepth(
                phase = VideoCardTransitionBackgroundPhase.OPENING,
                currentDepth = 0.55f,
            ),
            0.0001f,
        )
    }
}
