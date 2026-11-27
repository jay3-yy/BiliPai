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
}
