package com.android.purebilibili.feature.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.android.purebilibili.core.store.AccountSessionIdentity
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LiveFollowRowStateUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun remountAndRestoreKeepPositionWhileNewRefreshOrAccountResetsIt() {
        val show = mutableStateOf(true)
        val identity = mutableStateOf(AccountSessionIdentity(mid = 1L, generation = 1L))
        val completed = mutableLongStateOf(1L)
        lateinit var row: LazyListState
        lateinit var scrollTo: (Int, Int) -> Unit
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            val holder = rememberSaveableStateHolder()
            if (show.value) {
                holder.SaveableStateProvider("live") {
                    val state = rememberLiveFollowRowState(identity.value, completed.longValue)
                    val scope = rememberCoroutineScope()
                    SideEffect {
                        row = state
                        scrollTo = { index, offset -> scope.launch { state.scrollToItem(index, offset) } }
                    }
                    LazyRow(Modifier.width(240.dp).height(64.dp), state = state) {
                        items((0..29).toList(), key = { it }) { index ->
                            Box(Modifier.width(60.dp)) { BasicText("主播 $index") }
                        }
                    }
                }
            }
        }
        rule.runOnIdle { scrollTo(8, 11) }
        rule.waitUntil { row.firstVisibleItemIndex == 8 && row.firstVisibleItemScrollOffset == 11 }
        rule.runOnIdle { show.value = false }
        rule.runOnIdle { show.value = true }
        rule.runOnIdle {
            assertEquals(8, row.firstVisibleItemIndex)
            assertEquals(11, row.firstVisibleItemScrollOffset)
        }
        restoration.emulateSavedInstanceStateRestore()
        rule.runOnIdle {
            assertEquals(8, row.firstVisibleItemIndex)
            assertEquals(11, row.firstVisibleItemScrollOffset)
        }
        rule.runOnIdle { completed.longValue = 2L }
        rule.waitUntil { row.firstVisibleItemIndex == 0 && row.firstVisibleItemScrollOffset == 0 }
        rule.runOnIdle { scrollTo(6, 7) }
        rule.waitUntil { row.firstVisibleItemIndex == 6 }
        rule.runOnIdle { show.value = false }
        rule.runOnIdle { completed.longValue = 3L }
        rule.runOnIdle { show.value = true }
        rule.waitUntil { row.firstVisibleItemIndex == 0 && row.firstVisibleItemScrollOffset == 0 }
        rule.runOnIdle { scrollTo(5, 0) }
        rule.waitUntil { row.firstVisibleItemIndex == 5 }
        rule.runOnIdle { identity.value = AccountSessionIdentity(mid = 2L, generation = 2L) }
        rule.runOnIdle { assertEquals(0, row.firstVisibleItemIndex) }
    }
}
