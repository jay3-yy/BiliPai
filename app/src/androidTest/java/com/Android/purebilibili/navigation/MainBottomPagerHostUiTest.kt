package com.android.purebilibili.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.purebilibili.feature.home.components.BottomNavItem
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainBottomPagerHostUiTest {
    @get:Rule val rule = createComposeRule()
    private val tabs = listOf(BottomNavItem.HOME, BottomNavItem.DYNAMIC, BottomNavItem.PROFILE)

    @Test
    fun hiddenPageStopsCollectingAndResumesWithLatestData() {
        val data = List(tabs.size) { MutableStateFlow(0) }
        val displayed = IntArray(tabs.size) { -1 }
        lateinit var navigation: MainBottomPagerState
        rule.setContent {
            navigation = rememberMainBottomPagerState(pageCount = tabs.size, reduceMotion = true)
            MainBottomPagerHost(navigation, tabs, modifier = Modifier.fillMaxSize()) { page, _ ->
                val value by data[page].collectAsStateWithLifecycle()
                SideEffect { displayed[page] = value }
                BasicText("$page:$value")
            }
        }
        rule.waitUntil { data[0].subscriptionCount.value == 1 }
        rule.runOnIdle {
            assertEquals(0, data[1].subscriptionCount.value)
            assertEquals(0, data[2].subscriptionCount.value)
            navigation.switchToPage(1)
        }
        rule.waitUntil { !navigation.isNavigating && data[1].subscriptionCount.value == 1 && data[0].subscriptionCount.value == 0 }
        rule.runOnIdle { data[0].value = 7 }
        rule.runOnIdle { assertEquals(0, displayed[0]) }
        rule.runOnIdle { navigation.switchToPage(0) }
        rule.waitUntil { !navigation.isNavigating && displayed[0] == 7 && data[1].subscriptionCount.value == 0 }
    }

    @Test
    fun remountRestoresSelectedPageStateWithoutMountingPreviouslyVisitedTabs() {
        var showHost by mutableStateOf(true)
        val mounted = mutableSetOf<BottomNavItem>()
        val drafts = mutableMapOf<BottomNavItem, Int>()
        lateinit var navigation: MainBottomPagerState
        lateinit var editDraft: () -> Unit
        rule.setContent {
            val holder = rememberSaveableStateHolder()
            navigation = rememberMainBottomPagerState(pageCount = tabs.size, reduceMotion = true)
            if (showHost) {
                MainBottomPagerHost(navigation, tabs, modifier = Modifier.fillMaxSize()) { _, item ->
                    holder.SaveableStateProvider(item.name) {
                        var draft by rememberSaveable { mutableIntStateOf(0) }
                        DisposableEffect(item) {
                            mounted.add(item)
                            onDispose { mounted.remove(item) }
                        }
                        SideEffect {
                            drafts[item] = draft
                            if (item == tabs[navigation.selectedPage]) editDraft = { draft++ }
                        }
                        BasicText("${item.name}:$draft")
                    }
                }
            }
        }
        rule.runOnIdle {
            assertEquals(setOf(BottomNavItem.HOME), mounted)
            navigation.switchToPage(1)
        }
        rule.waitUntil { !navigation.isNavigating && mounted == setOf(BottomNavItem.DYNAMIC) }
        rule.runOnIdle { editDraft() }
        rule.runOnIdle {
            assertEquals(1, drafts[BottomNavItem.DYNAMIC])
            navigation.switchToPage(0)
        }
        rule.waitUntil { !navigation.isNavigating && mounted == setOf(BottomNavItem.HOME) }
        rule.runOnIdle { navigation.switchToPage(1) }
        rule.waitUntil { !navigation.isNavigating && mounted == setOf(BottomNavItem.DYNAMIC) }
        rule.runOnIdle {
            assertEquals(1, drafts[BottomNavItem.DYNAMIC])
            showHost = false
        }
        rule.runOnIdle { assertTrue(mounted.isEmpty()) }
        rule.runOnIdle { showHost = true }
        rule.waitUntil { BottomNavItem.DYNAMIC in mounted }
        rule.runOnIdle {
            assertEquals(setOf(BottomNavItem.DYNAMIC), mounted)
            assertEquals(1, drafts[BottomNavItem.DYNAMIC])
        }
    }

    @Test
    fun parentLifecycleCapsEveryPageAndRapidSelectionsFinishAtLatestTarget() {
        val subscriptions = List(tabs.size) { MutableStateFlow(0) }
        lateinit var navigation: MainBottomPagerState
        lateinit var owner: LifecycleOwner
        rule.setContent {
            val parent = remember {
                object : LifecycleOwner {
                    val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
                    override val lifecycle: Lifecycle get() = registry
                }
            }
            owner = parent
            CompositionLocalProvider(LocalLifecycleOwner provides parent) {
                navigation = rememberMainBottomPagerState(pageCount = tabs.size)
                MainBottomPagerHost(navigation, tabs, modifier = Modifier.fillMaxSize()) { page, _ ->
                    val value by subscriptions[page].collectAsStateWithLifecycle()
                    BasicText("$page:$value")
                }
            }
        }
        rule.waitUntil { subscriptions[0].subscriptionCount.value == 1 }
        rule.runOnIdle {
            navigation.switchToPage(1)
            navigation.switchToPage(2)
            navigation.switchToPage(0)
        }
        rule.waitUntil { !navigation.isNavigating }
        rule.runOnIdle {
            assertEquals(0, navigation.selectedPage)
            assertEquals(0, navigation.settledPage)
            (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.CREATED
        }
        rule.waitUntil { subscriptions.all { it.subscriptionCount.value == 0 } }
        rule.runOnIdle { (owner.lifecycle as LifecycleRegistry).currentState = Lifecycle.State.RESUMED }
        rule.waitUntil { subscriptions[0].subscriptionCount.value == 1 }
        rule.runOnIdle {
            assertFalse(navigation.isNavigating)
            assertEquals(0, subscriptions[1].subscriptionCount.value)
            assertEquals(0, subscriptions[2].subscriptionCount.value)
        }
    }

    @Test
    fun firstAndLastTabsSlideDirectlyAndReverseWithoutMountingIntermediateTabs() {
        val fiveTabs = listOf(
            BottomNavItem.HOME, BottomNavItem.DYNAMIC, BottomNavItem.HISTORY,
            BottomNavItem.PROFILE, BottomNavItem.SETTINGS,
        )
        val mounted = mutableSetOf<Int>()
        val everMounted = mutableSetOf<Int>()
        val positions = mutableMapOf<Int, Float>()
        var pageWidth = 0
        lateinit var navigation: MainBottomPagerState
        rule.setContent {
            navigation = rememberMainBottomPagerState(pageCount = fiveTabs.size)
            MainBottomPagerHost(navigation, fiveTabs, modifier = Modifier.fillMaxSize()) { page, _ ->
                DisposableEffect(page) {
                    mounted += page
                    everMounted += page
                    onDispose { mounted -= page }
                }
                Box(Modifier.fillMaxSize().onGloballyPositioned {
                    positions[page] = it.positionInRoot().x
                    pageWidth = it.size.width
                }) { BasicText("page:$page") }
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { navigation.switchToPage(4) }
        rule.mainClock.advanceTimeBy(100)
        rule.runOnIdle {
            assertEquals(setOf(0, 4), mounted)
            assertEquals(0, navigation.settledPage)
            assertTrue(positions.getValue(0) < 0f)
            assertTrue(positions.getValue(4) > 0f)
        }
        rule.mainClock.advanceTimeBy(500)
        rule.runOnIdle {
            assertEquals(setOf(4), mounted)
            assertEquals(4, navigation.settledPage)
            navigation.switchToPage(0)
        }
        rule.mainClock.advanceTimeBy(100)
        var positionBeforeReversal = 0f
        rule.runOnIdle {
            assertEquals(setOf(0, 4), mounted)
            assertEquals(4, navigation.settledPage)
            assertTrue(positions.getValue(0) < 0f)
            assertTrue(positions.getValue(4) > 0f)
            positionBeforeReversal = positions.getValue(4)
            navigation.switchToPage(4)
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle {
            assertTrue(kotlin.math.abs(positions.getValue(4) - positionBeforeReversal) < pageWidth / 2f)
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.runOnIdle {
            assertFalse(navigation.isNavigating)
            assertEquals(4, navigation.selectedPage)
            assertEquals(4, navigation.settledPage)
            assertEquals(setOf(4), mounted)
            assertEquals(setOf(0, 4), everMounted)
        }
    }

    @Test
    fun savedInstanceStateRestoresSelectedTabWithoutReplayingTransition() {
        val restoration = StateRestorationTester(rule)
        lateinit var navigation: MainBottomPagerState
        restoration.setContent {
            navigation = rememberMainBottomPagerState(pageCount = tabs.size, reduceMotion = true)
            MainBottomPagerHost(navigation, tabs, modifier = Modifier.fillMaxSize()) { page, _ ->
                BasicText("page:$page")
            }
        }
        rule.runOnIdle { navigation.switchToPage(2) }
        rule.waitUntil { !navigation.isNavigating && navigation.settledPage == 2 }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(2, navigation.selectedPage)
            assertEquals(2, navigation.settledPage)
            assertFalse(navigation.isNavigating)
        }
    }

}
