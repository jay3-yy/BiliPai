package com.android.purebilibili.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.ui.LocalSystemReduceMotion
import com.android.purebilibili.core.ui.LocalVisualWorkAllowed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScrollToTopFeedbackUiTest {
    @get:Rule val rule = createComposeRule()
    private val active = mutableStateOf(true)
    private val reduced = mutableStateOf(false)
    private val parentOwnsDisplacement = mutableStateOf(false)
    private val state = LazyListState(firstVisibleItemIndex = 8)
    private lateinit var scope: CoroutineScope

    private fun showList() {
        rule.setContent {
            scope = rememberCoroutineScope()
            CompositionLocalProvider(
                LocalVisualWorkAllowed provides active.value,
                LocalSystemReduceMotion provides reduced.value,
                LocalAppScrollToTopFeedbackAllowed provides !parentOwnsDisplacement.value,
            ) {
                Column {
                    Box(Modifier.width(200.dp).height(40.dp).testTag("fixed-header"))
                    AppLazyColumn(
                        state = state,
                        modifier = Modifier.width(200.dp).height(240.dp).testTag("viewport"),
                        overscrollEffect = null,
                    ) {
                        items(30) { index ->
                            Box(Modifier.width(200.dp).height(60.dp).testTag("item-$index"))
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        advance(80)
    }

    // Same atomic return used by the home feed; the effect moves rendered content only.
    private fun returnToTop() {
        rule.runOnIdle {
            scope.launch {
                state.scrollToItem(0)
                state.notifyAppScrollToTop()
            }
        }
    }

    @Test
    fun returnMovesContentAndSettlesWithoutMovingFixedHeader() {
        showList()
        val headerTop = top("fixed-header")
        returnToTop()
        advance(80)
        assertTrue(top("item-0") > top("viewport"))
        assertEquals(headerTop, top("fixed-header"), 0.1f)
        advance(1_200)
        assertEquals(top("viewport"), top("item-0"), 0.1f)
        rule.runOnIdle { assertEquals(0, state.firstVisibleItemIndex) }
    }

    @Test
    fun duplicateReturnNotificationDoesNotRestartTheSettlingContent() {
        showList()
        returnToTop()
        advance(128)
        val before = top("item-0") - top("viewport")
        assertTrue(before > 0f)
        rule.runOnIdle { state.notifyAppScrollToTop() }
        advance(32)
        val after = top("item-0") - top("viewport")
        assertTrue("Settling content must not receive another outward impulse", after in 0f..before)
        advance(1_000)
        assertEquals(top("viewport"), top("item-0"), 0.1f)
    }

    @Test
    fun parentRefreshDiscardsReturnPulseWithoutReplayingItAfterRelease() {
        showList()
        rule.runOnIdle { parentOwnsDisplacement.value = true }
        advance(32)
        returnToTop()
        advance(80)
        assertEquals(top("viewport"), top("item-0"), 0.1f)
        rule.runOnIdle { parentOwnsDisplacement.value = false }
        advance(80)
        assertEquals(top("viewport"), top("item-0"), 0.1f)
        // A subsequent explicit return still receives feedback after native motion has ended.
        rule.runOnIdle { state.notifyAppScrollToTop() }
        advance(64)
        assertTrue(top("item-0") > top("viewport"))
    }

    @Test
    fun hidingCancelsPulseAndRestoringDoesNotReplayIt() {
        showList()
        returnToTop()
        advance(80)
        assertTrue(top("item-0") > top("viewport"))
        rule.runOnIdle { active.value = false }
        advance(32)
        assertEquals(top("viewport"), top("item-0"), 0.1f)
        rule.runOnIdle { active.value = true }
        advance(80)
        assertEquals(top("viewport"), top("item-0"), 0.1f)
        // The restored container can respond to a new explicit return, unless reduced motion is set.
        rule.runOnIdle { reduced.value = true }
        advance(32)
        rule.runOnIdle { state.notifyAppScrollToTop() }
        advance(80)
        assertEquals(top("viewport"), top("item-0"), 0.1f)
    }

    private fun advance(ms: Long) {
        rule.mainClock.advanceTimeBy(ms)
        rule.waitForIdle()
    }

    private fun top(tag: String): Float =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
}
