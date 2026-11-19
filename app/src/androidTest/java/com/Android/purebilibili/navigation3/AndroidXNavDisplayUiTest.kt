package com.android.purebilibili.navigation3

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.purebilibili.navigation3.predictiveback.BiliPaiPredictiveBackAnimationStyle
import com.android.purebilibili.navigation3.predictiveback.InstantPageNavTransition
import com.android.purebilibili.navigation3.predictiveback.VideoCardBackCompletionPolicy
import com.android.purebilibili.navigation3.predictiveback.pagePresetNavTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import top.yukonga.miuix.kmp.nav.core.LocalNavTransitionScope
import top.yukonga.miuix.kmp.nav.core.NavBackCompletionPolicy
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.transition.NavTransition

@RunWith(AndroidJUnit4::class)
class AndroidXNavDisplayUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun cancellationBeyondFloatingCardThresholdRestoresDetailAndNextGestureCommitsOnce() {
        val detail = BiliPaiNavKey.VideoDetail(bvid = "BV1", openId = 1)
        val callbacks = mutableListOf<String>()
        lateinit var dispatcher: NavigationEventDispatcher
        lateinit var detailDepth: () -> Float
        rule.setContent {
            val backStack = rememberBiliPaiNavBackStack(listOf(BiliPaiNavKey.MainHost, detail))
            val owner = checkNotNull(LocalNavigationEventDispatcherOwner.current)
            SideEffect { dispatcher = owner.navigationEventDispatcher }
            TestDisplay(
                backStack = backStack,
                backCompletionPolicy = VideoCardBackCompletionPolicy,
                onBack = {
                    callbacks.add("committed")
                    backStack.removeAt(backStack.lastIndex)
                },
                onBackStarted = { callbacks.add("started") },
                onBackCancelled = { callbacks.add("cancelled") },
            ) { route ->
                if (route == detail) {
                    val transition = LocalNavTransitionScope.current
                    SideEffect { detailDepth = { transition.relativeDepth } }
                }
                Text(if (route == detail) "Video detail" else "Feed")
            }
        }

        val input = DirectNavigationEventInput()
        rule.runOnIdle { dispatcher.addInput(input) }
        try {
            rule.runOnIdle {
                input.backStarted(backEvent(0f))
                input.backProgressed(backEvent(0.8f))
            }
            rule.waitUntil { detailDepth() < -0.7f }
            rule.runOnIdle { input.backCancelled() }
            rule.waitUntil { "cancelled" in callbacks && detailDepth() >= -0.001f }
            rule.onNodeWithText("Video detail").assertIsDisplayed()
            rule.runOnIdle {
                assertEquals(0f, detailDepth(), 0.001f)
                input.backStarted(backEvent(0f))
                input.backProgressed(backEvent(0.8f))
                input.backCompleted()
            }
            rule.waitUntil { "committed" in callbacks }
            rule.onNodeWithText("Feed").assertIsDisplayed()
            rule.onNodeWithText("Video detail").assertDoesNotExist()
            rule.runOnIdle {
                assertEquals(listOf("started", "cancelled", "started", "committed"), callbacks)
            }
        } finally {
            rule.runOnIdle { dispatcher.removeInput(input) }
        }
    }

    @Test
    fun finishingAnOldBackGestureKeepsTheReplacementPage() {
        val detail = BiliPaiNavKey.VideoDetail(bvid = "BV1", openId = 1)
        val replacement = BiliPaiNavKey.VideoDetail(bvid = "BV2", openId = 2)
        var started = false
        var backCount = 0
        lateinit var stack: NavBackStack<BiliPaiNavKey>
        lateinit var dispatcher: NavigationEventDispatcher
        lateinit var depth: () -> Float
        rule.setContent {
            val backStack = rememberBiliPaiNavBackStack(listOf(BiliPaiNavKey.MainHost, detail))
            val owner = checkNotNull(LocalNavigationEventDispatcherOwner.current)
            SideEffect {
                stack = backStack
                dispatcher = owner.navigationEventDispatcher
            }
            TestDisplay(
                backStack = backStack,
                onBack = {
                    backCount++
                    backStack.removeAt(backStack.lastIndex)
                },
                onBackStarted = { started = true },
            ) { route ->
                if (route is BiliPaiNavKey.VideoDetail) {
                    val transition = LocalNavTransitionScope.current
                    SideEffect { depth = { transition.relativeDepth } }
                    Text(route.bvid)
                }
            }
        }
        val input = DirectNavigationEventInput()
        rule.runOnIdle { dispatcher.addInput(input) }
        try {
            rule.runOnIdle {
                input.backStarted(backEvent(0f))
                input.backProgressed(backEvent(0.5f))
            }
            rule.waitUntil { started && depth() < -0.35f }
            rule.runOnIdle {
                stack[stack.lastIndex] = replacement
            }
            rule.onNodeWithText("BV2").assertIsDisplayed()
            rule.runOnIdle {
                // The replacement must reach rest even while the old finger is still down.
                assertEquals(0f, depth(), 0.001f)
                input.backCompleted()
            }
            rule.onNodeWithText("BV2").assertIsDisplayed()
            rule.runOnIdle {
                assertEquals(0, backCount)
                assertEquals(replacement, stack.last())
            }
        } finally {
            rule.runOnIdle { dispatcher.removeInput(input) }
        }
    }

    @Test
    fun poppedPageRemainsComposedUntilItsExitFinishes() {
        val detail = BiliPaiNavKey.VideoDetail(bvid = "BV1", openId = 1)
        val stack = NavBackStack<BiliPaiNavKey>(BiliPaiNavKey.MainHost, detail)
        var detailDisposed = false
        rule.setContent {
            TestDisplay(backStack = stack, onBack = { stack.removeLastOrNull() }) { route ->
                if (route == detail) {
                    DisposableEffect(Unit) { onDispose { detailDisposed = true } }
                    Text("Video detail")
                } else {
                    Text("Feed")
                }
            }
        }
        rule.onNodeWithText("Video detail").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        try {
            rule.runOnIdle { stack.removeLastOrNull() }
            rule.mainClock.advanceTimeBy(96)
            rule.runOnIdle { assertFalse(detailDisposed) }

            rule.mainClock.advanceTimeBy(400)
            rule.runOnIdle { assertTrue(detailDisposed) }
            rule.onNodeWithText("Feed").assertIsDisplayed()
        } finally {
            rule.mainClock.autoAdvance = true
        }
    }

    @Test
    fun changingMotionWhileCoveredRetainsTheEntryAndItsSelection() {
        var rootDisposals = 0
        var backCount = 0
        rule.setContent {
            val backStack = rememberBiliPaiNavBackStack(listOf(BiliPaiNavKey.MainHost))
            var transition by rememberSaveable { mutableIntStateOf(0) }
            val pageTransition = if (transition == 0) InstantPageNavTransition else testPageTransition
            TestDisplay(
                backStack = backStack,
                transition = pageTransition,
                onBack = {
                    backCount++
                    backStack.removeAt(backStack.lastIndex)
                },
            ) { route ->
                when (route) {
                    BiliPaiNavKey.MainHost -> {
                        DisposableEffect(Unit) { onDispose { rootDisposals++ } }
                        var selectedItem by rememberSaveable { mutableIntStateOf(0) }
                        Column {
                            Button(onClick = { selectedItem++ }) { Text("Selected: $selectedItem") }
                            Button(onClick = { backStack.add(BiliPaiNavKey.History) }) { Text("Open history") }
                        }
                    }
                    BiliPaiNavKey.History -> Button(onClick = { transition++ }) { Text("Change animation") }
                    else -> error("Unexpected route: $route")
                }
            }
        }

        rule.onNodeWithText("Selected: 0").performClick()
        rule.onNodeWithText("Open history").performClick()
        rule.onNodeWithText("Change animation").performClick()
        rule.runOnIdle { assertEquals(0, rootDisposals) }

        pressBack()

        rule.onNodeWithText("Change animation").assertDoesNotExist()
        rule.onNodeWithText("Selected: 1").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(1, backCount)
            assertEquals(0, rootDisposals)
        }
    }
}

private fun backEvent(progress: Float) =
    NavigationEvent(progress = progress, swipeEdge = NavigationEvent.EDGE_LEFT)

private val testPageTransition =
    pagePresetNavTransition(BiliPaiPredictiveBackAnimationStyle.RIGHT_TO_LEFT)

@Composable
private fun TestDisplay(
    backStack: NavBackStack<BiliPaiNavKey>,
    onBack: () -> Unit,
    transition: NavTransition = testPageTransition,
    backCompletionPolicy: NavBackCompletionPolicy? = null,
    onBackStarted: () -> Unit = {},
    onBackCancelled: () -> Unit = {},
    content: @Composable (BiliPaiNavKey) -> Unit,
) {
    BiliPaiAndroidXNavDisplay(
        backStack = backStack,
        onBack = onBack,
        transition = transition,
        effects = NavDisplayEffects.None,
        backCompletionPolicy = backCompletionPolicy,
        onBackStarted = onBackStarted,
        onBackCancelled = onBackCancelled,
        motion = {
            entry<BiliPaiNavKey.MainHost> {}
            entry<BiliPaiNavKey.History> {}
            entry<BiliPaiNavKey.VideoDetail> {}
        },
        content = content,
    )
}
