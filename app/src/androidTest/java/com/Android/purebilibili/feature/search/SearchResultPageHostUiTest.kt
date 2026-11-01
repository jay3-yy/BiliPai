package com.android.purebilibili.feature.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchResultPageHostUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun loadingAndResultsKeepThePageRootUntilThePagerLeavesComposition() {
        val loading = mutableStateOf(true)
        val showResults = mutableStateOf(true)
        var currentRoot: LayoutCoordinates? = null
        rule.setContent {
            val pager = rememberPagerState { 1 }
            if (showResults.value) {
                HorizontalPager(state = pager, modifier = Modifier.size(300.dp)) {
                    SearchResultPageHost(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { currentRoot = it },
                    ) {
                        if (loading.value) {
                            Box { BasicText("加载中") }
                        } else {
                            Column { BasicText("搜索结果") }
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("加载中").assertIsDisplayed()
        lateinit var originalRoot: LayoutCoordinates
        rule.runOnIdle {
            originalRoot = checkNotNull(currentRoot)
            loading.value = false
        }
        rule.onNodeWithText("搜索结果").assertIsDisplayed()
        rule.runOnIdle {
            assertSame(originalRoot, currentRoot)
            assertTrue(originalRoot.isAttached)
            showResults.value = false
        }
        rule.waitForIdle()
        rule.runOnIdle {
            assertTrue(!originalRoot.isAttached)
            showResults.value = true
        }
        rule.onNodeWithText("搜索结果").assertIsDisplayed()
        rule.runOnIdle { assertTrue(checkNotNull(currentRoot).isAttached) }
    }
}
