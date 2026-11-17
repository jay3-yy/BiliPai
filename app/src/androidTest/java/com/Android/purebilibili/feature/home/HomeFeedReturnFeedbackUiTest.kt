package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Exercises the home wrapper rather than bypassing it with a native grid state. */
class HomeFeedReturnFeedbackUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun regularGridRoutesAnimatedAndAtomicReturns() = checkReturns(regular = true)
    @Test fun waterfallRoutesAnimatedAndAtomicReturns() = checkReturns(regular = false)

    private fun checkReturns(regular: Boolean) {
        lateinit var state: HomeFeedGridState
        lateinit var scope: CoroutineScope
        rule.setContent {
            state = rememberHomeFeedGridState(regular)
            scope = rememberCoroutineScope()
            HomeFeedGrid(state, 2, Modifier.requiredSize(320.dp, 360.dp).testTag("viewport")) {
                items(60, key = { it }) { index ->
                    Box(Modifier.height(80.dp).testTag("card-$index"))
                }
            }
        }
        rule.waitForIdle()
        for (animated in listOf(true, false)) {
            rule.mainClock.autoAdvance = true
            rule.runOnIdle { scope.launch { state.scrollToItem(16) } }
            rule.waitForIdle()
            rule.runOnIdle { assertTrue(state.firstVisibleItemIndex > 0) }
            rule.mainClock.autoAdvance = false
            var completed = false
            rule.runOnIdle {
                scope.launch {
                    if (animated) state.animateScrollToTop(fast = true)
                    else {
                        state.scrollToItem(0)
                        state.notifyAppScrollToTop()
                    }
                    completed = true
                }
            }
            var frames = 0
            while (!rule.runOnIdle { completed } && frames++ < 240) {
                rule.mainClock.advanceTimeByFrame()
                rule.waitForIdle()
            }
            rule.runOnIdle {
                assertTrue(completed)
                assertEquals(0, state.firstVisibleItemIndex)
                assertEquals(0, state.firstVisibleItemScrollOffset)
            }
            rule.mainClock.advanceTimeBy(64)
            rule.waitForIdle()
            val viewportTop = top("viewport")
            assertTrue("Return feedback must reach the rendered native grid", top("card-0") > viewportTop)
            rule.mainClock.advanceTimeBy(1_000)
            rule.waitForIdle()
            assertEquals(viewportTop, top("card-0"), 0.1f)
        }
    }

    private fun top(tag: String): Float =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
}
