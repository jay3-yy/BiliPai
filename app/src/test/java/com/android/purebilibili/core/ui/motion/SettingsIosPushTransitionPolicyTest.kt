package com.android.purebilibili.core.ui.motion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsIosPushTransitionPolicyTest {

    @Test
    fun resolveSettingsIosPushTransitionMillis_respectsReduceMotion() {
        assertEquals(0, resolveSettingsIosPushTransitionMillis(animationEnabled = true, reduceMotion = true))
        assertEquals(
            SETTINGS_IOS_PUSH_DURATION_MS,
            resolveSettingsIosPushTransitionMillis(animationEnabled = true, reduceMotion = false),
        )
    }

    @Test
    fun settingsIosPushParallaxFactor_isStable() {
        assertTrue(SETTINGS_IOS_PUSH_PARALLAX_FACTOR in 0.25f..0.4f)
    }
}
