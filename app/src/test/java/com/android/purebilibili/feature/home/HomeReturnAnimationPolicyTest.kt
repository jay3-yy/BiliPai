package com.android.purebilibili.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeReturnAnimationPolicyTest {

    @Test
    fun quickReturn_withTransition_usesSharedElementSoftLandingSuppressionOnPhone() {
        // 360 standard + 40 buffer + 48 settle
        assertEquals(
            448L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = false,
                cardAnimationEnabled = true,
                cardTransitionEnabled = true,
            )
        )
    }

    @Test
    fun quickReturn_withTransition_usesSharedElementSoftLandingSuppressionOnTablet() {
        // 360 + 40 + 48 + 40 tablet extra
        assertEquals(
            488L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = true,
                cardAnimationEnabled = true,
                cardTransitionEnabled = true,
            )
        )
    }

    @Test
    fun normalReturn_usesUnifiedSharedTransitionDuration() {
        assertEquals(
            448L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = false,
                cardAnimationEnabled = true,
                cardTransitionEnabled = true,
            )
        )
        assertEquals(
            220L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = false,
                cardAnimationEnabled = false,
                cardTransitionEnabled = false,
            )
        )
    }

    @Test
    fun sharedReturnSuppressionUsesTheConfiguredTransitionDuration() {
        // 520 + 40 buffer + 48 settle buffer（返回已改固定时长 tween，无需 spring 过冲余量）
        assertEquals(
            608L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = false,
                cardAnimationEnabled = true,
                cardTransitionEnabled = true,
                sharedTransitionDurationMillis = 520,
            )
        )
    }

    @Test
    fun nonSharedReturn_usesShorterSuppressionDurations() {
        assertEquals(
            240L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = false,
                cardAnimationEnabled = true,
                cardTransitionEnabled = false,
            )
        )
        assertEquals(
            220L,
            resolveReturnAnimationSuppressionDurationMs(
                isTabletLayout = true,
                cardAnimationEnabled = true,
                cardTransitionEnabled = false,
            )
        )
    }

    @Test
    fun staleGate_recoversOnlyAfterTimeoutWithoutReturnGesture() {
        // 门控未挂起或手势进行中时永不强制恢复：用户按住预测性返回可以远超期限。
        assertFalse(
            shouldRecoverStaleHomeGate(active = false, elapsedMs = 9_999L, gestureInProgress = false)
        )
        assertFalse(
            shouldRecoverStaleHomeGate(active = true, elapsedMs = 9_999L, gestureInProgress = true)
        )
        // 恢复期限内保持门控：任何合法返回动画（含平板 buffer）都短于该值。
        assertFalse(
            shouldRecoverStaleHomeGate(active = true, elapsedMs = 1_999L, gestureInProgress = false)
        )
        // 期限到达即判定失联，恢复门控。
        assertTrue(
            shouldRecoverStaleHomeGate(active = true, elapsedMs = 2_000L, gestureInProgress = false)
        )
    }

    @Test
    fun staleGate_honorsExplicitTimeout() {
        assertFalse(
            shouldRecoverStaleHomeGate(
                active = true,
                elapsedMs = 499L,
                gestureInProgress = false,
                timeoutMs = 500L,
            )
        )
        assertTrue(
            shouldRecoverStaleHomeGate(
                active = true,
                elapsedMs = 500L,
                gestureInProgress = false,
                timeoutMs = 500L,
            )
        )
    }

}
