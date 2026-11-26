package com.android.purebilibili.feature.settings

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelemetryDisclosurePolicyTest {

    @Test
    fun `global telemetry toggle reflects combined crash and analytics state`() {
        assertFalse(resolveGlobalTelemetryEnabled(crashTrackingEnabled = false, analyticsEnabled = false))
        assertTrue(resolveGlobalTelemetryEnabled(crashTrackingEnabled = true, analyticsEnabled = false))
        assertTrue(resolveGlobalTelemetryEnabled(crashTrackingEnabled = false, analyticsEnabled = true))
        assertTrue(resolveGlobalTelemetryEnabled(crashTrackingEnabled = true, analyticsEnabled = true))
    }

    @Test
    fun `global telemetry subtitle clearly communicates state and opt-out`() {
        val allOn = resolveGlobalTelemetrySubtitle(crashTrackingEnabled = true, analyticsEnabled = true)
        assertTrue(allOn.contains("关闭"))

        val crashOnly = resolveGlobalTelemetrySubtitle(crashTrackingEnabled = true, analyticsEnabled = false)
        assertTrue(crashOnly.contains("崩溃"))
        assertTrue(crashOnly.contains("关闭"))

        val analyticsOnly = resolveGlobalTelemetrySubtitle(crashTrackingEnabled = false, analyticsEnabled = true)
        assertTrue(analyticsOnly.contains("统计"))
        assertTrue(analyticsOnly.contains("关闭"))

        val allOff = resolveGlobalTelemetrySubtitle(crashTrackingEnabled = false, analyticsEnabled = false)
        assertTrue(allOff.contains("已关闭全部遥测"))
    }

    @Test
    fun `disclosure sections explicitly document collected items, exclusions, and opt-out`() {
        assertTrue(TELEMETRY_DISCLOSURE_SECTIONS.size >= 4)
        val allText = TELEMETRY_DISCLOSURE_SECTIONS.joinToString("\n") { section ->
            section.title + "\n" + section.items.joinToString("\n")
        }
        assertTrue(allText.contains("不收集"))
        assertTrue(allText.contains("Cookie"))
        assertTrue(allText.contains("随时关闭"))
    }
}
