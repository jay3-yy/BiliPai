package com.android.purebilibili.core.ui.transition

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalComponentMotionEnabled
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VideoCardReturnLandingUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun returnedCardContractsItsPixelsAndRestoresWithoutMovingItsAnchor() {
        val landing = NavigationReturnLanding()
        var anchor = Rect.Zero
        rule.setContent {
            CompositionLocalProvider(
                LocalComponentMotionEnabled provides true,
                LocalSystemReduceMotion provides false,
            ) {
                Box(Modifier.size(200.dp, 120.dp).background(Color.White).testTag("slot")) {
                    Box(Modifier.size(200.dp, 120.dp).onGloballyPositioned { anchor = it.boundsInRoot() }
                        .navigationReturnLanding(
                            landing,
                            style = NavigationReturnLandingStyle.VIDEO_CARD,
                            isSourceVisible = { true },
                        ).background(Color.Red))
                }
            }
        }
        rule.waitForIdle()
        val restingAnchor = anchor
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { landing.request() }
        rule.mainClock.advanceTimeBy(96)
        rule.waitForIdle()
        val compressed = rule.onNodeWithTag("slot").captureToImage().toPixelMap()
        assertEquals(restingAnchor, anchor)
        assertTrue(compressed[0, compressed.height / 2].green > 0.8f)
        assertTrue(compressed[compressed.width / 2, compressed.height / 2].green < 0.2f)
        rule.mainClock.advanceTimeBy(400)
        rule.waitForIdle()
        val restored = rule.onNodeWithTag("slot").captureToImage().toPixelMap()
        assertTrue(restored[0, restored.height / 2].green < 0.2f)
        assertEquals(restingAnchor, anchor)
    }
}
