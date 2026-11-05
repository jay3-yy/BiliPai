package com.android.purebilibili.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.purebilibili.data.model.response.SearchType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchStateIsolationUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun editingAndReceivingSuggestionsUpdatesTheDropdownWithoutRecomposingTheShell() {
        val source = mutableStateOf(SearchUiState(query = "动"))
        var shellCompositions = 0
        var submitted = ""
        rule.setContent {
            MaterialTheme {
                SearchShellProbe(source, { shellCompositions++ }) {
                    SearchSuggestionsHost(source, onSuggestionClick = { submitted = it })
                }
            }
        }
        var initialCompositions = 0
        rule.runOnIdle {
            initialCompositions = shellCompositions
            source.value = source.value.copy(query = "动画")
        }
        rule.runOnIdle {
            source.value = source.value.copy(
                suggestions = listOf(SearchSuggestionUiModel("动画教程")),
            )
        }
        rule.onNodeWithText("动画教程").assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertEquals("动画教程", submitted)
            assertEquals(initialCompositions, shellCompositions)
            source.value = source.value.copy(showResults = true)
        }
        rule.onNodeWithText("动画教程").assertDoesNotExist()
        rule.onNodeWithText("搜索结果页").assertIsDisplayed()
        rule.runOnIdle { assertTrue(shellCompositions > initialCompositions) }
    }

    @Test
    fun resultErrorsAndRetriesUpdateOnlyTheOwningPageAndKeepCachedPages() {
        val source = mutableStateOf(
            SearchUiState(
                query = "动画",
                submittedQuery = "动画",
                showResults = true,
                searchType = SearchType.VIDEO,
                resultPages = mapOf(
                    SearchType.UP to SearchResultPageUiState(query = "动画", currentPage = 1),
                ),
            ),
        )
        var shellCompositions = 0
        var cachedPageCompositions = 0
        rule.setContent {
            SearchShellProbe(source, { shellCompositions++ }) {
                SearchResultStateHost(source, SearchType.VIDEO) { page ->
                    BasicText(page.error ?: if (page.isSearching) "正在重试" else "当前视频结果")
                }
                SearchResultStateHost(source, SearchType.UP) { page ->
                    SideEffect { cachedPageCompositions++ }
                    BasicText("缓存UP结果：${page.query}")
                }
            }
        }
        var initialShellCompositions = 0
        var initialCachedCompositions = 0
        rule.runOnIdle {
            initialShellCompositions = shellCompositions
            initialCachedCompositions = cachedPageCompositions
            source.value = source.value.copy(error = "搜索暂时失败")
        }
        rule.onNodeWithText("搜索暂时失败").assertIsDisplayed()
        rule.onNodeWithText("缓存UP结果：动画").assertIsDisplayed()
        rule.runOnIdle {
            assertEquals(initialShellCompositions, shellCompositions)
            assertEquals(initialCachedCompositions, cachedPageCompositions)
            source.value = source.value.copy(error = null, isSearching = true)
        }
        rule.onNodeWithText("正在重试").assertIsDisplayed()
        rule.runOnIdle { source.value = source.value.copy(isSearching = false, currentPage = 1) }
        rule.onNodeWithText("当前视频结果").assertIsDisplayed()
        rule.onNodeWithText("缓存UP结果：动画").assertIsDisplayed()
        rule.runOnIdle { assertEquals(initialCachedCompositions, cachedPageCompositions) }
    }
}

@Composable
private fun SearchShellProbe(
    source: State<SearchUiState>,
    onComposed: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSearchScreenState(source).value
    SideEffect(onComposed)
    Column {
        BasicText(if (state.showResults) "搜索结果页" else "搜索落地页")
        // The shell observes query emptiness for landing bootstrap and motion budgets.
        BasicText(if (state.hasQuery) "有搜索词" else "无搜索词")
        content()
    }
}
