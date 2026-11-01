package com.android.purebilibili.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.android.purebilibili.core.store.UnreadBadgeMode
import com.android.purebilibili.feature.home.components.HOME_TOP_RIGHT_BADGE_DOT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeUnreadBadgeUpdatesTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun badgeChangesDoNotRecomposeFeedOrItsHost() {
        val unread = MutableStateFlow(7)
        val mode = MutableStateFlow(UnreadBadgeMode.NUMBER)
        var hostCommits = 0
        var feedCommits = 0
        rule.setContent {
            SideEffect { hostCommits++ }
            Column {
                HomeUnreadBadgeUpdates(unread, mode) { BasicText("badge:$it") }
                SideEffect { feedCommits++ }
                BasicText("feed")
            }
        }
        rule.onNodeWithText("badge:7").assertTextEquals("badge:7")
        val baseline = rule.runOnIdle { hostCommits to feedCommits }
        rule.runOnIdle { unread.value = 12 }
        rule.onNodeWithText("badge:12").assertExists()
        rule.runOnIdle { mode.value = UnreadBadgeMode.DOT }
        rule.onNodeWithText("badge:$HOME_TOP_RIGHT_BADGE_DOT").assertExists()
        rule.runOnIdle { unread.value = 0 }
        rule.onNodeWithText("badge:0").assertExists()
        rule.runOnIdle {
            mode.value = UnreadBadgeMode.HIDDEN
            unread.value = 99
        }
        rule.onNodeWithText("badge:0").assertExists()
        rule.runOnIdle { assertEquals(baseline, hostCommits to feedCommits) }
    }

    @Test
    fun replacingAccountSourceUsesCurrentCountAndStopsObservingOldSource() {
        val old = MutableStateFlow(9)
        val current = MutableStateFlow(0)
        val mode = MutableStateFlow(UnreadBadgeMode.NUMBER)
        var source by mutableStateOf<StateFlow<Int>>(old)
        rule.setContent { HomeUnreadBadgeUpdates(source, mode) { BasicText("badge:$it") } }
        rule.onNodeWithText("badge:9").assertExists()
        rule.runOnIdle { source = current }
        rule.onNodeWithText("badge:0").assertExists()
        rule.runOnIdle {
            assertEquals(0, old.subscriptionCount.value)
            old.value = 999
        }
        rule.onNodeWithText("badge:0").assertExists()
        rule.runOnIdle { current.value = 3 }
        rule.onNodeWithText("badge:3").assertExists()
    }
}
