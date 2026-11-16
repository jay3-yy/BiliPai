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
    fun staleReturnGate_recoversOnlyAfterTimeoutWhileHomeVisible() {
        // 门控未挂起或首页不可见时永不强制恢复。
        assertFalse(
            shouldForceRecoverStaleReturnGate(
                homeVisible = true,
                gateActive = false,
                gateStartedAtMs = 1_000L,
                nowMs = 9_999L,
            )
        )
        assertFalse(
            shouldForceRecoverStaleReturnGate(
                homeVisible = false,
                gateActive = true,
                gateStartedAtMs = 1_000L,
                nowMs = 9_999L,
            )
        )
        // 超时前保持门控（正常返回动画时长内不受影响）。
        assertFalse(
            shouldForceRecoverStaleReturnGate(
                homeVisible = true,
                gateActive = true,
                gateStartedAtMs = 1_000L,
                nowMs = 2_999L,
            )
        )
        // 超过恢复期限即强制恢复。
        assertTrue(
            shouldForceRecoverStaleReturnGate(
                homeVisible = true,
                gateActive = true,
                gateStartedAtMs = 1_000L,
                nowMs = 3_000L,
            )
        )
    }

    @Test
    fun staleReturnGate_ignoresUnarmedGateTimestamp() {
        assertFalse(
            shouldForceRecoverStaleReturnGate(
                homeVisible = true,
                gateActive = true,
                gateStartedAtMs = 0L,
                nowMs = 60_000L,
            )
        )
    }

}
