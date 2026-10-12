package com.android.purebilibili.feature.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeProtectionPolicyTest {

    @Test
    fun `isWithinProtectionWindow handles cross-day time ranges`() {
        assertTrue(isWithinProtectionWindow(currentHour = 23, startHour = 22, endHour = 7))
        assertTrue(isWithinProtectionWindow(currentHour = 2, startHour = 22, endHour = 7))
        assertFalse(isWithinProtectionWindow(currentHour = 14, startHour = 22, endHour = 7))
    }

    @Test
    fun `isWithinProtectionWindow handles same-day time ranges`() {
        assertTrue(isWithinProtectionWindow(currentHour = 20, startHour = 18, endHour = 23))
        assertFalse(isWithinProtectionWindow(currentHour = 8, startHour = 18, endHour = 23))
    }

    @Test
    fun `shouldTriggerCareReminder respects interval and snooze`() {
        assertFalse(
            shouldTriggerCareReminder(
                usageMinutes = 29,
                intervalMinutes = 30,
                snoozeUntilMinute = null,
                lastReminderMinute = null
            )
        )

        assertTrue(
            shouldTriggerCareReminder(
                usageMinutes = 30,
                intervalMinutes = 30,
                snoozeUntilMinute = null,
                lastReminderMinute = null
            )
        )

        assertFalse(
            shouldTriggerCareReminder(
                usageMinutes = 60,
                intervalMinutes = 30,
                snoozeUntilMinute = 65,
                lastReminderMinute = 30
            )
        )
    }

    @Test
    fun `tuningForPreset returns expected defaults`() {
        val gentle = tuningForPreset(EyeCarePreset.GENTLE)
        val focus = tuningForPreset(EyeCarePreset.FOCUS)

        assertEquals(0.88f, gentle.brightnessLevel)
        assertEquals(45, gentle.reminderIntervalMinutes)
        assertEquals(0.65f, focus.brightnessLevel)
        assertEquals(25, focus.reminderIntervalMinutes)
    }

    @Test
    fun `isVisualEffectActive respects force and schedule`() {
        assertTrue(
            isVisualEffectActive(
                forceEnabled = true,
                nightModeEnabled = false,
                currentHour = 14,
                startHour = 22,
                endHour = 7
            )
        )

        assertTrue(
            isVisualEffectActive(
                forceEnabled = false,
                nightModeEnabled = true,
                currentHour = 23,
                startHour = 22,
                endHour = 7
            )
        )

        assertFalse(
            isVisualEffectActive(
                forceEnabled = false,
                nightModeEnabled = false,
                currentHour = 23,
                startHour = 22,
                endHour = 7
            )
        )
    }

    @Test
    fun `preview mode should always activate visual state with clamped values`() {
        val visualState = resolveEyeVisualState(
            settingsPreviewEnabled = true,
            forceEnabled = false,
            nightModeEnabled = false,
            currentHour = 14,
            startHour = 22,
            endHour = 7,
            brightnessLevel = 0.1f,
            warmFilterStrength = 0.8f
        )

        assertTrue(visualState.isActive)
        assertEquals(0.3f, visualState.brightnessLevel)
        assertEquals(0.5f, visualState.warmFilterStrength)
    }

    @Test
    fun `non-preview mode should reset to defaults when inactive`() {
        val visualState = resolveEyeVisualState(
            settingsPreviewEnabled = false,
            forceEnabled = false,
            nightModeEnabled = false,
            currentHour = 14,
            startHour = 22,
            endHour = 7,
            brightnessLevel = 0.75f,
            warmFilterStrength = 0.2f
        )

        assertFalse(visualState.isActive)
        assertEquals(1.0f, visualState.brightnessLevel)
        assertEquals(0f, visualState.warmFilterStrength)
    }

    @Test
    fun `reminder dialog uses compact actions on short screens`() {
        val policy = resolveEyeReminderDialogLayoutPolicy(screenHeightDp = 640)

        assertTrue(policy.useCompactSecondaryActions)
        assertEquals(0.86f, policy.maxHeightFraction)
    }

    @Test
    fun `reminder dialog keeps regular actions on taller screens`() {
        val policy = resolveEyeReminderDialogLayoutPolicy(screenHeightDp = 820)

        assertFalse(policy.useCompactSecondaryActions)
        assertEquals(0.92f, policy.maxHeightFraction)
    }

    @Test
    fun `schedule progress ramps in after window start`() {
        val startProgress = resolveScheduleProgress(
            currentMinuteOfDay = minuteOfDay(22),
            startHour = 22,
            endHour = 7,
            rampMinutes = 20
        )
        val midRamp = resolveScheduleProgress(
            currentMinuteOfDay = minuteOfDay(22, 10),
            startHour = 22,
            endHour = 7,
            rampMinutes = 20
        )
        val full = resolveScheduleProgress(
            currentMinuteOfDay = minuteOfDay(23),
            startHour = 22,
            endHour = 7,
            rampMinutes = 20
        )
        val fadeOut = resolveScheduleProgress(
            currentMinuteOfDay = minuteOfDay(6, 50),
            startHour = 22,
            endHour = 7,
            rampMinutes = 20
        )
        val outside = resolveScheduleProgress(
            currentMinuteOfDay = minuteOfDay(14),
            startHour = 22,
            endHour = 7,
            rampMinutes = 20
        )

        assertEquals(0.08f, startProgress, 0.001f)
        assertEquals(0.5f, midRamp, 0.001f)
        assertEquals(1f, full, 0.001f)
        assertEquals(0.5f, fadeOut, 0.001f)
        assertEquals(0f, outside, 0.001f)
    }

    @Test
    fun `visual state follows schedule ramp instead of snapping`() {
        val ramping = resolveEyeVisualState(
            settingsPreviewEnabled = false,
            forceEnabled = false,
            nightModeEnabled = true,
            currentMinuteOfDay = minuteOfDay(22, 10),
            startHour = 22,
            endHour = 7,
            brightnessLevel = 0.6f,
            warmFilterStrength = 0.4f,
            rampMinutes = 20
        )

        assertTrue(ramping.isActive)
        assertEquals(0.5f, ramping.scheduleProgress, 0.001f)
        assertEquals(0.8f, ramping.brightnessLevel, 0.001f)
        assertEquals(0.2f, ramping.warmFilterStrength, 0.001f)
    }

    @Test
    fun `overlay paint weakens during playback`() {
        val full = resolveEyeOverlayPaint(
            brightnessLevel = 0.7f,
            warmFilterStrength = 0.3f,
            playbackWeaken = false
        )
        val weakened = resolveEyeOverlayPaint(
            brightnessLevel = 0.7f,
            warmFilterStrength = 0.3f,
            playbackWeaken = true
        )

        assertTrue(weakened.dimAlpha < full.dimAlpha)
        assertTrue(weakened.warmAlpha < full.warmAlpha)
        assertEquals(EYE_WARM_FILTER_COLOR, full.warmColor)
    }

    @Test
    fun `status copy reports active schedule and standby`() {
        val active = resolveEyeProtectionStatusCopy(
            pluginEnabled = true,
            isActive = true,
            forceEnabled = false,
            nightModeEnabled = true,
            startHour = 22,
            endHour = 7,
            brightnessPercent = 78,
            warmPercent = 22,
            usageMinutes = 12,
            reminderEnabled = true,
            nextReminderInMinutes = 18
        )
        assertEquals("护眼已开启", active.title)
        assertTrue(active.subtitle.contains("07:00"))
        assertTrue(active.subtitle.contains("12 分钟"))

        val standby = resolveEyeProtectionStatusCopy(
            pluginEnabled = true,
            isActive = false,
            forceEnabled = false,
            nightModeEnabled = true,
            startHour = 22,
            endHour = 7,
            brightnessPercent = 78,
            warmPercent = 22,
            usageMinutes = 0,
            reminderEnabled = true,
            nextReminderInMinutes = 30
        )
        assertEquals("定时护眼待机", standby.title)
        assertTrue(standby.subtitle.contains("22:00"))
    }

    @Test
    fun `minutes until reminder respects snooze`() {
        assertEquals(
            30,
            resolveMinutesUntilReminder(
                usageMinutes = 0,
                intervalMinutes = 30,
                snoozeUntilMinute = null,
                reminderEnabled = true
            )
        )
        assertEquals(
            8,
            resolveMinutesUntilReminder(
                usageMinutes = 20,
                intervalMinutes = 30,
                snoozeUntilMinute = 28,
                reminderEnabled = true
            )
        )
        assertNull(
            resolveMinutesUntilReminder(
                usageMinutes = 12,
                intervalMinutes = 30,
                snoozeUntilMinute = null,
                reminderEnabled = false
            )
        )
    }
}
