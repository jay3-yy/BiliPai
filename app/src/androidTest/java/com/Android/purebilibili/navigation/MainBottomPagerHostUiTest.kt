package com.android.purebilibili.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
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
            val pager = rememberPagerState { tabs.size }
            navigation = rememberMainBottomPagerState(pager, reduceMotion = true)
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
            val pager = rememberPagerState { tabs.size }
            navigation = rememberMainBottomPagerState(pager, reduceMotion = true)
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
                val pager = rememberPagerState { tabs.size }
                navigation = rememberMainBottomPagerState(pager)
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
            assertEquals(0, navigation.pagerState.settledPage)
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
}
