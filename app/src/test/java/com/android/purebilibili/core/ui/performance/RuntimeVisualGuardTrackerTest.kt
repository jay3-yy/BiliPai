package com.android.purebilibili.core.ui.performance

import androidx.metrics.performance.FrameData
import androidx.metrics.performance.StateInfo
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.core.ui.adaptive.RUNTIME_VISUAL_GUARD_DOWNGRADE_COOLDOWN_MS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeVisualGuardTrackerTest {

    @Test
    fun sampleBelowMinimumFrameCount_isIgnored() {
        val tracker = RuntimeVisualGuardTracker()

        tracker.finishWindow(totalFrames = 11, jankyFrames = 11, nowMs = 100L)

        assertFalse(tracker.decision.value.downgraded)
    }

    @Test
    fun framesWithoutVideoTransitionState_areIgnored() {
        val tracker = RuntimeVisualGuardTracker()
        repeat(24) { index ->
            tracker.onFrame(
                frameData = FrameData(
                    frameStartNanos = index.toLong(),
                    frameDurationUiNanos = 20_000_000L,
                    isJank = true,
                    states = listOf(StateInfo("OtherTransition", "Running")),
                ),
                nowMs = index.toLong(),
            )
        }

        assertFalse(tracker.decision.value.downgraded)
    }

    @Test
    fun firstHighJankWindow_doesNotDowngrade() {
        val tracker = RuntimeVisualGuardTracker()

        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 100L)

        assertEquals(MotionTier.Normal, tracker.decision.value.effectiveMotionTier)
    }

    @Test
    fun twoConsecutiveHighJankWindows_downgradeToReduced() {
        val tracker = RuntimeVisualGuardTracker()

        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 100L)
        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 200L)

        assertTrue(tracker.decision.value.downgraded)
        assertEquals(MotionTier.Reduced, tracker.decision.value.effectiveMotionTier)
    }

    @Test
    fun lowJankWindowDuringCooldown_keepsReducedTier() {
        val tracker = downgradedTracker(downgradedAtMs = 200L)

        tracker.finishWindow(
            totalFrames = 20,
            jankyFrames = 0,
            nowMs = 200L + RUNTIME_VISUAL_GUARD_DOWNGRADE_COOLDOWN_MS - 1L,
        )

        assertTrue(tracker.decision.value.downgraded)
    }

    @Test
    fun onlyLowJankWindowAfterCooldown_recoversNormalTier() {
        val tracker = downgradedTracker(downgradedAtMs = 200L)

        tracker.finishWindow(
            totalFrames = 20,
            jankyFrames = 1,
            nowMs = 200L + RUNTIME_VISUAL_GUARD_DOWNGRADE_COOLDOWN_MS,
        )
        assertTrue(tracker.decision.value.downgraded)

        tracker.finishWindow(
            totalFrames = 20,
            jankyFrames = 0,
            nowMs = 201L + RUNTIME_VISUAL_GUARD_DOWNGRADE_COOLDOWN_MS,
        )

        assertFalse(tracker.decision.value.downgraded)
        assertEquals(MotionTier.Normal, tracker.decision.value.effectiveMotionTier)
    }

    @Test
    fun discardedWindow_doesNotTriggerOrBreakConsecutiveHighCount() {
        val tracker = RuntimeVisualGuardTracker()
        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 100L)
        repeat(20) {
            tracker.onFrame(tracked = true, isJank = true, nowMs = 150L)
        }

        tracker.discardActiveWindow()
        assertFalse(tracker.decision.value.downgraded)

        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 200L)
        assertTrue(tracker.decision.value.downgraded)
    }

    @Test
    fun staleActivityStop_cannotDiscardNewActivityWindow() {
        val tracker = RuntimeVisualGuardTracker()
        val oldActivity = Any()
        val newActivity = Any()
        tracker.activateSession(oldActivity)
        tracker.activateSession(newActivity)

        repeat(12) {
            tracker.onFrame(
                session = newActivity,
                frameData = FrameData(
                    frameStartNanos = it.toLong(),
                    frameDurationUiNanos = 20_000_000L,
                    isJank = true,
                    states = listOf(StateInfo(VIDEO_CARD_TRANSITION_JANK_STATE, "Opening")),
                ),
                nowMs = 100L,
            )
        }
        tracker.discardActiveWindow(oldActivity)
        tracker.onFrame(
            session = newActivity,
            frameData = FrameData(12L, 8_000_000L, false, emptyList()),
            nowMs = 100L,
        )
        repeat(12) { index ->
            tracker.onFrame(
                session = newActivity,
                frameData = FrameData(
                    frameStartNanos = (20 + index).toLong(),
                    frameDurationUiNanos = 20_000_000L,
                    isJank = true,
                    states = listOf(StateInfo(VIDEO_CARD_TRANSITION_JANK_STATE, "Returning")),
                ),
                nowMs = 200L,
            )
        }
        tracker.onFrame(
            session = newActivity,
            frameData = FrameData(32L, 8_000_000L, false, emptyList()),
            nowMs = 200L,
        )

        assertTrue(tracker.decision.value.downgraded)
    }

    @Test
    fun stateValueChange_closesPreviousWindowWithoutInactiveFrame() {
        val tracker = RuntimeVisualGuardTracker()

        repeat(12) { tracker.onFrame(stateValue = "Opening", isJank = true, nowMs = 100L) }
        repeat(12) { tracker.onFrame(stateValue = "Returning", isJank = true, nowMs = 200L) }
        tracker.onFrame(stateValue = null, isJank = false, nowMs = 300L)

        assertTrue(tracker.decision.value.downgraded)
    }

    @Test
    fun feedScrollSignal_alsoDrivesDowngrade() {
        // 回归：此前 Tracker 只匹配 VideoCardTransition，首页滚动打点无人消费。
        val tracker = RuntimeVisualGuardTracker()

        repeat(2) {
            tracker.finishFrameWindow(
                stateKey = "home:feed:recommend",
                totalFrames = 12,
                jankyFrames = 1,
                nowMs = 100L * (it + 1),
            )
        }

        assertTrue(tracker.decision.value.downgraded)
        assertTrue(tracker.decision.value.forceLowBlurBudget)
    }

    @Test
    fun separateSignals_doNotShareWindows() {
        // 竖滑与横滑同帧共存；若共用一个窗口，两边各 6 帧会被误判成一个满窗口。
        val tracker = RuntimeVisualGuardTracker()

        repeat(2) { round ->
            tracker.finishFrameWindow("home:feed:recommend", 6, 6, 100L * (round + 1))
            tracker.finishFrameWindow("home:pager_swipe", 6, 6, 100L * (round + 1))
        }

        assertFalse(tracker.decision.value.downgraded)
    }

    @Test
    fun lowJankWindowOnAnotherSignal_recoversExpiredGlobalDowngrade() {
        val tracker = RuntimeVisualGuardTracker()
        tracker.finishFrameWindow("home:feed:recommend", 12, 1, 100L)
        tracker.finishFrameWindow("home:feed:recommend", 12, 1, 200L)
        assertTrue(tracker.decision.value.downgraded)

        tracker.finishFrameWindow(
            stateKey = "home:pager_swipe",
            totalFrames = 20,
            jankyFrames = 0,
            nowMs = 200L + RUNTIME_VISUAL_GUARD_DOWNGRADE_COOLDOWN_MS,
        )

        assertFalse(tracker.decision.value.downgraded)
        assertEquals(MotionTier.Normal, tracker.decision.value.effectiveMotionTier)
    }

    @Test
    fun untrackedStateKey_isIgnored() {
        val tracker = RuntimeVisualGuardTracker()

        repeat(4) { round ->
            tracker.finishFrameWindow("home:current_category", 24, 24, 100L * (round + 1))
        }

        assertFalse(tracker.decision.value.downgraded)
    }

    @Test
    fun tabletBaseTier_recoversToEnhancedAfterCooldown() {
        // 回归：baseTier 曾硬编码为 Normal，平板降级一次后永久丢失 Enhanced 档。
        val tracker = RuntimeVisualGuardTracker()
        tracker.setBaseTier(MotionTier.Enhanced)
        assertEquals(MotionTier.Enhanced, tracker.decision.value.effectiveMotionTier)

        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 100L)
        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 200L)
        assertEquals(MotionTier.Reduced, tracker.decision.value.effectiveMotionTier)

        tracker.finishWindow(
            totalFrames = 20,
            jankyFrames = 0,
            nowMs = 200L + RUNTIME_VISUAL_GUARD_DOWNGRADE_COOLDOWN_MS,
        )

        assertFalse(tracker.decision.value.downgraded)
        assertEquals(MotionTier.Enhanced, tracker.decision.value.effectiveMotionTier)
    }

    @Test
    fun disablingGuard_resetsDecisionImmediately() {
        val tracker = downgradedTracker(downgradedAtMs = 200L)
        assertTrue(tracker.decision.value.downgraded)

        tracker.setEnabled(false)

        assertFalse(tracker.decision.value.downgraded)
        assertFalse(tracker.decision.value.forceLowBlurBudget)
        assertEquals(MotionTier.Normal, tracker.decision.value.effectiveMotionTier)
    }

    @Test
    fun disabledGuard_ignoresIncomingFrames() {
        val tracker = RuntimeVisualGuardTracker(enabled = false)

        tracker.finishWindow(totalFrames = 12, jankyFrames = 12, nowMs = 100L)
        tracker.finishWindow(totalFrames = 12, jankyFrames = 12, nowMs = 200L)

        assertFalse(tracker.decision.value.downgraded)
    }

    private fun RuntimeVisualGuardTracker.finishFrameWindow(
        stateKey: String,
        totalFrames: Int,
        jankyFrames: Int,
        nowMs: Long,
    ) {
        repeat(totalFrames) { index ->
            onFrame(
                frameData = FrameData(
                    frameStartNanos = index.toLong(),
                    frameDurationUiNanos = 20_000_000L,
                    isJank = index < jankyFrames,
                    states = listOf(StateInfo(stateKey, "Active")),
                ),
                nowMs = nowMs,
            )
        }
        onFrame(
            frameData = FrameData(
                frameStartNanos = totalFrames.toLong(),
                frameDurationUiNanos = 8_000_000L,
                isJank = false,
                states = emptyList(),
            ),
            nowMs = nowMs,
        )
    }

    private fun downgradedTracker(downgradedAtMs: Long): RuntimeVisualGuardTracker {
        return RuntimeVisualGuardTracker().also { tracker ->
            tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 100L)
            tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = downgradedAtMs)
        }
    }

    @Test
    fun beginSession_clearsCrossSessionDowngradeHistory() {
        val tracker = RuntimeVisualGuardTracker()

        // 连续两个高掉帧窗口 → 降级。
        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 100L)
        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 200L)
        assertTrue(tracker.decision.value.downgraded)
        assertEquals(MotionTier.Reduced, tracker.decision.value.effectiveMotionTier)

        // 进入新会话（如返回列表后再次进入详情）→ 惩罚历史清零，不再「越进越卡」。
        tracker.beginSession(Any())
        assertFalse(tracker.decision.value.downgraded)
        assertFalse(tracker.decision.value.forceLowBlurBudget)
        assertEquals(MotionTier.Normal, tracker.decision.value.effectiveMotionTier)
    }

    @Test
    fun beginSession_isIdempotentForTheSameSession() {
        val tracker = RuntimeVisualGuardTracker(minimumFrameCount = 12)
        val session = Any()

        tracker.beginSession(session)
        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 100L)

        // 同会话重入须同时保留已结算的降级历史和当前未结算的采样窗口。
        repeat(6) { index ->
            tracker.onFrame(tracked = true, isJank = index == 0, nowMs = 200L)
        }
        tracker.beginSession(session)
        repeat(6) {
            tracker.onFrame(tracked = true, isJank = false, nowMs = 200L)
        }
        tracker.onFrame(tracked = false, isJank = false, nowMs = 200L)

        assertTrue(tracker.decision.value.downgraded)
    }

    @Test
    fun resetDowngradeHistory_keepsBaseTier() {
        val tracker = RuntimeVisualGuardTracker(baseTier = MotionTier.Reduced)

        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 100L)
        tracker.finishWindow(totalFrames = 12, jankyFrames = 1, nowMs = 200L)
        tracker.beginSession(Any())

        // 只清惩罚历史，不覆盖组合根注入的设备基线档位。
        assertEquals(MotionTier.Reduced, tracker.decision.value.effectiveMotionTier)
        assertFalse(tracker.decision.value.downgraded)
    }

    private fun RuntimeVisualGuardTracker.finishWindow(
        totalFrames: Int,
        jankyFrames: Int,
        nowMs: Long,
    ) {
        repeat(totalFrames) { index ->
            onFrame(
                tracked = true,
                isJank = index < jankyFrames,
                nowMs = nowMs,
            )
        }
        onFrame(tracked = false, isJank = false, nowMs = nowMs)
    }
}
