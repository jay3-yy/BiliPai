package com.android.purebilibili.navigation3

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SystemWindowControlMotionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tractionWaitsForSystemEndAndCancellationRestoresTheControl() {
        val control = SystemWindowControlNeighbor(
            rule.activity.window.decorView,
            "home",
            rule.activity.resources.displayMetrics.density,
        ).also { it.inViewport = true }
        rule.setContent {
            CompositionLocalProvider(
                LocalComponentMotionEnabled provides true,
                LocalSystemReduceMotion provides false,
            ) {
                ObserveSystemWindowControlNeighbor(control, pageActive = true, returnHandledByOwner = false)
                Box(Modifier.size(200.dp, 40.dp).then(control.modifier).testTag("search"))
            }
        }
        rule.waitUntil { control.eligible }
        fun width() = rule.onNodeWithTag("search").fetchSemanticsNode().boundsInRoot.width
        val restingWidth = width()
        rule.mainClock.autoAdvance = false
        fun dispatch(phase: SystemWindowMotionPhase) {
            rule.runOnIdle { control.motion = SystemWindowControlNeighbor.Motion(phase, Offset(-1f, 0f)) }
            rule.mainClock.advanceTimeBy(2_000)
            rule.waitForIdle()
        }
        dispatch(SystemWindowMotionPhase.RETURNING)
        val compressedWidth = width()
        assertTrue(compressedWidth < restingWidth)
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals(compressedWidth, width(), 0.5f)
        dispatch(SystemWindowMotionPhase.RETURNED)
        assertEquals(restingWidth, width(), 0.5f)
        dispatch(SystemWindowMotionPhase.RETURNING)
        assertTrue(width() < restingWidth)
        dispatch(SystemWindowMotionPhase.RETURN_CANCELLED)
        assertEquals(restingWidth, width(), 0.5f)
    }
}
