package com.android.purebilibili.core.ui.animation

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class NavigationContentReadyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contentCanAppearDuringNavigationAndDoesNotResetDuringReturn() {
        val navigationReady = mutableStateOf(false)
        val containerReady = mutableStateOf(false)
        val userId = mutableStateOf(1L)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppNavigationEntranceReady provides navigationReady) {
                val ready = rememberEntryContentReady(userId.value, containerReady.value)
                BasicText(if (ready) "空间内容 ${userId.value}" else "加载中")
            }
        }
        composeRule.onNodeWithText("加载中").assertIsDisplayed()
        // 导航尚在运行时，内容只要自身容器就绪就可以展示。
        composeRule.runOnIdle { containerReady.value = true }
        composeRule.onNodeWithText("空间内容 1").assertIsDisplayed()

        // 返回预览、取消和宿主动效信号改变，不得卸载已展示的同一份内容。
        composeRule.runOnIdle {
            navigationReady.value = false
            containerReady.value = false
        }
        composeRule.onNodeWithText("空间内容 1").assertIsDisplayed()

        // 新内容身份仍需等待自身的入场，不能借用上一个 UP 的完成状态。
        composeRule.runOnIdle { userId.value = 2L }
        composeRule.onNodeWithText("加载中").assertIsDisplayed()
        composeRule.runOnIdle { containerReady.value = true }
        composeRule.onNodeWithText("空间内容 2").assertIsDisplayed()
    }

    @Test
    fun entryWithoutRunningNavigationDisplaysContentImmediately() {
        composeRule.setContent {
            val ready = rememberEntryContentReady(1L, contentReady = true)
            BasicText(if (ready) "空间内容" else "加载中")
        }
        composeRule.onNodeWithText("空间内容").assertIsDisplayed()
    }
}
