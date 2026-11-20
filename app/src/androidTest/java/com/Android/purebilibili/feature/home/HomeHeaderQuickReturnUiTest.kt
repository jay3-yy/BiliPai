package com.android.purebilibili.feature.home

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation3.runtime.NavBackStack
import com.android.purebilibili.navigation3.BiliPaiNavKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeHeaderQuickReturnUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun searchRoundTripBeforeObserverStartsStillPublishesOneLanding() {
        val stack = NavBackStack<BiliPaiNavKey>(BiliPaiNavKey.MainHost)
        val pending = mutableStateOf<HomeHeaderReturn?>(null)
        var delivered = 0
        rule.setContent {
            val event = pending.value
            LaunchedEffect(event) {
                if (event == null) return@LaunchedEffect
                event.awaitHomeAfterDeparture { stack.lastOrNull() == BiliPaiNavKey.MainHost }
                delivered++
                pending.value = null
            }
        }
        rule.runOnIdle {
            // push/pop 在同一轮主线程事件中完成，观察协程没有机会看到搜索在栈顶。
            val event = HomeHeaderReturn(HomeHeaderControl.SEARCH)
            pending.value = event
            event.markNavigationDeparted()
            stack.add(BiliPaiNavKey.Search())
            stack.removeLastOrNull()
        }
        rule.waitUntil { delivered == 1 }
        rule.runOnIdle {
            assertEquals(1, delivered)
            assertEquals(BiliPaiNavKey.MainHost, stack.last())
        }
    }
}
