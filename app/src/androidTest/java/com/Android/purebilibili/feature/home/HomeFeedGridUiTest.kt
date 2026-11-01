package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.theme.AppUiStyle
import com.android.purebilibili.core.theme.LocalAppUiStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import top.yukonga.miuix.kmp.theme.MiuixTheme

class HomeFeedGridUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun regularGridAnimatesDeletionWithOrdinaryPlacementDisabled() = checkDeletionReflow(true)

    @Test
    fun waterfallAnimatesDeletionWithOrdinaryPlacementDisabled() = checkDeletionReflow(false)

    private fun checkDeletionReflow(regular: Boolean) {
        rule.mainClock.autoAdvance = false
        var cards by mutableStateOf(List(8) { "video_$it" })
        var reflowActive by mutableStateOf(false)
        val identities = mutableMapOf<String, Any>()
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    val state = rememberHomeFeedGridState(regular)
                    HomeFeedGrid(state, 2, Modifier.requiredSize(360.dp, 480.dp)) {
                        items(cards.size, key = { cards[it] }) { index ->
                            val id = cards[index]
                            val identity = remember { Any() }
                            SideEffect { identities[id] = identity }
                            CardWithoutIntrinsicQueries(
                                videoListItemModifier(
                                    enabled = false,
                                    dissolveReflowEnabled = true,
                                    dissolveReflowActive = reflowActive,
                                ).testTag(id)
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        val original = rule.onNodeWithTag("video_2").getUnclippedBoundsInRoot()
        val survivor = rule.runOnIdle { identities.getValue("video_2") }
        rule.runOnIdle {
            reflowActive = true
            cards = cards.drop(1)
        }
        rule.mainClock.advanceTimeBy(64)
        val moving = rule.onNodeWithTag("video_2").getUnclippedBoundsInRoot()
        assertTrue(moving.top > 0.dp && moving.top < original.top)
        rule.mainClock.advanceTimeBy(400)
        val settled = rule.onNodeWithTag("video_2").getUnclippedBoundsInRoot()
        assertEquals(0.dp, settled.top)
        assertEquals(180.dp, settled.left)
        rule.runOnIdle { assertTrue(survivor === identities.getValue("video_2")) }

        // Once the deletion phase ends, ordinary reorders still use immediate placement.
        rule.runOnIdle { reflowActive = false }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { cards = cards.drop(1) }
        rule.mainClock.advanceTimeByFrame()
        assertEquals(0.dp, rule.onNodeWithTag("video_2").getUnclippedBoundsInRoot().left)
    }

    @Test
    fun regularGridKeepsPerVideoIdentityAcrossDeletionAndColumnChanges() {
        var cards by mutableStateOf(List(30) { "video_$it" })
        var columns by mutableStateOf(2)
        val identities = mutableMapOf<String, Any>()
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    val state = rememberHomeFeedGridState(true)
                    HomeFeedGrid(
                        state, columns,
                        modifier = Modifier.requiredSize(360.dp, 480.dp),
                        contentPadding = PaddingValues(top = 84.dp),
                        verticalItemSpacing = 6.dp,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item(key = "hero", fullLine = true) { Box(Modifier.height(60.dp)) }
                        items(cards.size, key = { cards[it] }, contentType = { "card" }) { index ->
                            val id = cards[index]
                            val identity = remember { Any() }
                            SideEffect { identities[id] = identity }
                            CardWithoutIntrinsicQueries(Modifier.testTag(id))
                        }
                    }
                }
            }
        }
        val survivor = rule.runOnIdle { identities.getValue("video_1") }
        rule.runOnIdle { cards = cards.drop(1) }
        rule.runOnIdle { assertTrue(survivor === identities.getValue("video_1")) }
        for (count in listOf(4, 7)) {
            rule.runOnIdle { columns = count }
            rule.runOnIdle { assertTrue(survivor === identities.getValue("video_1")) }
            val row = (0 until count).map { rule.onNodeWithTag(cards[it]).getUnclippedBoundsInRoot() }
            assertTrue(row.all { it.top == row.first().top })
            assertEquals(360.dp, row.last().right - row.first().left)
        }
    }

    @Test
    fun eachLayoutRestoresItsOwnViewportAfterModeSwitchAndStateRestoration() {
        val regular = mutableStateOf(true)
        lateinit var state: HomeFeedGridState
        lateinit var scope: CoroutineScope
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            state = rememberHomeFeedGridState(regular.value)
            scope = rememberCoroutineScope()
            HomeFeedGrid(state, 2, Modifier.requiredSize(360.dp, 400.dp)) {
                items(100, key = { "video_$it" }, contentType = { "card" }) {
                    CardWithoutIntrinsicQueries()
                }
            }
        }
        rule.runOnIdle { scope.launch { state.scrollToItem(20, 12) } }
        rule.waitForIdle()
        val regularAnchor = rule.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
        rule.runOnIdle { regular.value = false }
        rule.runOnIdle { scope.launch { state.scrollToItem(40, 7) } }
        rule.waitForIdle()
        val waterfallAnchor = rule.runOnIdle { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
        rule.runOnIdle { regular.value = true }
        rule.runOnIdle { assertEquals(regularAnchor, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset) }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle { assertEquals(regularAnchor, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset) }
        rule.runOnIdle { regular.value = false }
        rule.runOnIdle { assertEquals(waterfallAnchor, state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset) }
        rule.runOnIdle { scope.launch { state.scrollToItem(0) } }
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(0, state.firstVisibleItemIndex) }
    }

    @Test
    fun headersAndVideoKeysStayConsistentAcrossNativeMaterials() {
        var style by mutableStateOf(AppUiStyle.MATERIAL3)
        lateinit var state: HomeFeedGridState
        rule.setContent {
            MiuixTheme {
                MaterialTheme {
                    CompositionLocalProvider(LocalAppUiStyle provides style) {
                        state = rememberHomeFeedGridState(true)
                        HomeFeedGrid(state, 2, Modifier.requiredSize(360.dp, 480.dp)) {
                            item(key = "hero", fullLine = true) { Box(Modifier.height(60.dp)) }
                            items(4, key = { "video_$it" }, contentType = { "card" }) { CardWithoutIntrinsicQueries() }
                            item(key = "divider", fullLine = true) { Box(Modifier.height(32.dp)) }
                            items(4, key = { "old_$it" }, contentType = { "card" }) { CardWithoutIntrinsicQueries() }
                        }
                    }
                }
            }
        }
        for (material in AppUiStyle.entries) {
            rule.runOnIdle { style = material }
            rule.runOnIdle {
                assertTrue(state.visibleItemKeys().containsAll(listOf("hero", "video_0", "video_1", "divider")))
                assertEquals(10, state.totalItemsCount)
            }
        }
    }

    @Composable
    private fun CardWithoutIntrinsicQueries(modifier: Modifier = Modifier) {
        Layout(content = {}, modifier = modifier, measurePolicy = remember {
            object : MeasurePolicy {
                override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult =
                    layout(constraints.maxWidth, constraints.constrainHeight(96.dp.roundToPx())) {}

                override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
                    error("A regular feed card must be laid out without an intrinsic-height traversal")
            }
        })
    }
}
