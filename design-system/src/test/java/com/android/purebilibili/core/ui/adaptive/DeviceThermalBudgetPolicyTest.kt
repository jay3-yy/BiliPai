package com.android.purebilibili.core.ui.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceThermalBudgetPolicyTest {

    @Test
    fun resolvesNormalElevatedAndCriticalTiersFromThermalHeadroomAndPowerSave() {
        assertEquals(
            DeviceThermalTier.NORMAL,
            resolveDeviceThermalTier(thermalStatus = 1, thermalHeadroom = 0.72f, powerSaveMode = false),
        )
        assertEquals(
            DeviceThermalTier.NORMAL,
            resolveDeviceThermalTier(thermalStatus = 0, thermalHeadroom = Float.NaN, powerSaveMode = false),
        )
        assertEquals(
            DeviceThermalTier.ELEVATED,
            resolveDeviceThermalTier(thermalStatus = 0, thermalHeadroom = Float.NaN, powerSaveMode = true),
        )
        assertEquals(
            DeviceThermalTier.ELEVATED,
            resolveDeviceThermalTier(thermalStatus = 2, thermalHeadroom = 0.6f, powerSaveMode = false),
        )
        assertEquals(
            DeviceThermalTier.ELEVATED,
            resolveDeviceThermalTier(thermalStatus = 1, thermalHeadroom = 0.9f, powerSaveMode = false),
        )
        assertEquals(
            DeviceThermalTier.CRITICAL,
            resolveDeviceThermalTier(thermalStatus = 3, thermalHeadroom = 0.5f, powerSaveMode = false),
        )
        assertEquals(
            DeviceThermalTier.CRITICAL,
            resolveDeviceThermalTier(thermalStatus = 1, thermalHeadroom = 1.02f, powerSaveMode = false),
        )
    }

    @Test
    fun appliesThermalTierToVisualGuardWithoutOverridingDisabledGuard() {
        val base = RuntimeVisualGuardDecision(
            effectiveMotionTier = MotionTier.Enhanced,
            forceLowBlurBudget = false,
            downgraded = false,
            nextLastDowngradeAtMs = null,
        )

        val elevated = applyThermalTierToVisualGuardDecision(base, DeviceThermalTier.ELEVATED, enabled = true)
        assertTrue(elevated.forceLowBlurBudget)
        assertFalse(elevated.downgraded)
        assertEquals(MotionTier.Enhanced, elevated.effectiveMotionTier)

        val critical = applyThermalTierToVisualGuardDecision(base, DeviceThermalTier.CRITICAL, enabled = true)
        assertTrue(critical.forceLowBlurBudget)
        assertTrue(critical.downgraded)
        assertEquals(MotionTier.Reduced, critical.effectiveMotionTier)

        val disabled = applyThermalTierToVisualGuardDecision(base, DeviceThermalTier.CRITICAL, enabled = false)
        assertEquals(base, disabled)
    }

    @Test
    fun schedulesThermalHeadroomFollowUpWhenThrottledOrForecastElevated() {
        assertEquals(
            4_200L,
            resolveThermalHeadroomFollowUpDelayMs(
                thermalStatus = 0,
                thermalHeadroom = 0.95f,
                powerSaveMode = false,
                throttledRemainingMs = 4_200L,
                forecastWindowMs = 10_000L,
            ),
        )
        assertEquals(
            10_000L,
            resolveThermalHeadroomFollowUpDelayMs(
                thermalStatus = 0,
                thermalHeadroom = 0.92f,
                powerSaveMode = false,
                throttledRemainingMs = 0L,
                forecastWindowMs = 10_000L,
            ),
        )
        assertEquals(
            null,
            resolveThermalHeadroomFollowUpDelayMs(
                thermalStatus = 0,
                thermalHeadroom = 0.65f,
                powerSaveMode = false,
                throttledRemainingMs = 0L,
                forecastWindowMs = 10_000L,
            ),
        )
        assertEquals(
            null,
            resolveThermalHeadroomFollowUpDelayMs(
                thermalStatus = 2,
                thermalHeadroom = 0.95f,
                powerSaveMode = false,
                throttledRemainingMs = 0L,
                forecastWindowMs = 10_000L,
            ),
        )
        // 中度发热或省电模式下，若预测值将档位进一步推到 CRITICAL，也必须安排补采以支持降温恢复。
        assertEquals(
            10_000L,
            resolveThermalHeadroomFollowUpDelayMs(
                thermalStatus = 2,
                thermalHeadroom = 1.02f,
                powerSaveMode = false,
                throttledRemainingMs = 0L,
                forecastWindowMs = 10_000L,
            ),
        )
        assertEquals(
            10_000L,
            resolveThermalHeadroomFollowUpDelayMs(
                thermalStatus = 0,
                thermalHeadroom = 1.02f,
                powerSaveMode = true,
                throttledRemainingMs = 0L,
                forecastWindowMs = 10_000L,
            ),
        )
    }

    @Test
    fun calibratesHeadroomThresholdsFromDeviceMapAndAppliesToTierResolution() {
        val calibrated = resolveThermalHeadroomThresholds(
            mapOf(
                2 to 0.76f,
                3 to 0.96f,
            ),
        )
        assertEquals(ThermalHeadroomThresholds(elevated = 0.76f, critical = 0.96f), calibrated)
        assertEquals(
            DeviceThermalTier.ELEVATED,
            resolveDeviceThermalTier(
                thermalStatus = 0,
                thermalHeadroom = 0.78f,
                powerSaveMode = false,
                headroomThresholds = calibrated,
            ),
        )
        assertEquals(
            DeviceThermalTier.CRITICAL,
            resolveDeviceThermalTier(
                thermalStatus = 0,
                thermalHeadroom = 0.97f,
                powerSaveMode = false,
                headroomThresholds = calibrated,
            ),
        )

        val fallback = resolveThermalHeadroomThresholds(
            mapOf(
                2 to Float.NaN,
                3 to -1f,
            ),
        )
        assertEquals(ThermalHeadroomThresholds(), fallback)

        val clamped = resolveThermalHeadroomThresholds(
            mapOf(
                2 to 1.05f,
                3 to 0.92f,
            ),
        )
        assertEquals(ThermalHeadroomThresholds(elevated = 0.92f, critical = 0.92f), clamped)
    }
}
