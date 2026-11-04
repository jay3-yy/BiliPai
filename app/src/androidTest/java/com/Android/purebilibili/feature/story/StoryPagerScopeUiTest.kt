package com.android.purebilibili.feature.story

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StoryPagerScopeUiTest {
    @get:Rule val rule = createComposeRule()

    class ChildModel : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test
    fun reentryStartsAtUnreadHeadAndClearsThePreviousChildModels() {
        val entered = mutableStateOf(true)
        lateinit var pager: PagerState
        lateinit var scope: CoroutineScope
        lateinit var child: ChildModel
        rule.setContent {
            val holder = rememberSaveableStateHolder()
            if (entered.value) {
                holder.SaveableStateProvider("story") {
                    StoryPagerScope {
                        val currentPager = rememberPagerState { 5 }
                        val currentScope = rememberCoroutineScope()
                        val currentChild = viewModel<ChildModel>()
                        SideEffect {
                            pager = currentPager
                            scope = currentScope
                            child = currentChild
                        }
                        Column {
                            Text("current:${currentPager.currentPage}")
                            HorizontalPager(currentPager) { Text("clip:$it") }
                        }
                    }
                }
            }
        }
        rule.runOnIdle { scope.launch { pager.scrollToPage(2) } }
        rule.onNodeWithText("current:2").assertIsDisplayed()
        lateinit var previous: ChildModel
        rule.runOnIdle { previous = child; entered.value = false }
        rule.runOnIdle { assertTrue(previous.cleared); entered.value = true }
        rule.onNodeWithText("current:0").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(0, pager.currentPage)
            assertNotSame(previous, child)
        }
    }
}
