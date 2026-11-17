package com.android.purebilibili.core.ui.transition

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.android.purebilibili.core.ui.adaptive.MotionTier
import com.android.purebilibili.navigation3.BiliPaiNavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

class PredictiveBackBackgroundStateUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun retainedBackgroundUsesLatestTargetProgressAndAppearanceWithoutReplacingProviders() {
        val target = mutableStateOf<BiliPaiNavKey?>(BiliPaiNavKey.MainHost)
        val progress = mutableFloatStateOf(0.2f)
        val tier = mutableStateOf(MotionTier.Normal)
        val light = mutableStateOf(false)
        lateinit var current: PredictiveBackBackgroundState
        rule.setContent {
            val capturedProgress = progress.floatValue
            val state = rememberPredictiveBackBackgroundState(
                progressProvider = { capturedProgress },
                targetKey = target.value,
                motionTier = tier.value,
                isLightBackground = light.value,
            )
            SideEffect { current = state }
        }
        val retained = rule.runOnIdle { current }
        rule.runOnIdle {
            target.value = BiliPaiNavKey.Settings
            progress.floatValue = 0.75f
            tier.value = MotionTier.Reduced
            light.value = true
        }
        rule.runOnIdle {
            assertSame(retained, current)
            assertSame(retained.progressProvider, current.progressProvider)
            assertSame(retained.targetKeyProvider, current.targetKeyProvider)
            assertEquals(BiliPaiNavKey.Settings, retained.targetKeyProvider())
            assertEquals(0.75f, retained.progressProvider(), 0f)
            assertEquals(MotionTier.Reduced, retained.motionTierProvider())
            assertEquals(true, retained.isLightBackgroundProvider())
            target.value = null
            progress.floatValue = 0f
        }
        rule.runOnIdle {
            assertSame(retained, current)
            assertEquals(null, retained.targetKeyProvider())
            assertEquals(0f, retained.progressProvider(), 0f)
        }
    }
}
