package com.android.purebilibili.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import com.android.purebilibili.data.model.response.SearchType

/** 输入文字、联想和正文到达时，页面外壳只在其结构状态改变后重组。 */
@Composable
internal fun rememberSearchScreenState(source: State<SearchUiState>): State<SearchScreenState> =
    remember(source) {
        derivedStateOf(structuralEqualityPolicy()) { source.value.toSearchScreenState() }
    }

/** 每个保留的 Pager 页只读取自身结果；稳定根节点继续由 SearchResultPageHost 持有。 */
@Composable
internal fun SearchResultStateHost(
    source: State<SearchUiState>,
    searchType: SearchType,
    modifier: Modifier = Modifier,
    content: @Composable (SearchResultPageUiState) -> Unit,
) {
    val page by remember(source, searchType) {
        derivedStateOf(structuralEqualityPolicy()) { resolveSearchResultPageState(source.value, searchType) }
    }
    SearchResultPageHost(modifier = modifier) {
        content(page)
    }
}

private data class SearchSuggestionsState(
    val visible: Boolean,
    val suggestions: List<SearchSuggestionUiModel>,
)

@Composable
internal fun SearchSuggestionsHost(
    source: State<SearchUiState>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by remember(source) {
        derivedStateOf(structuralEqualityPolicy()) {
            val current = source.value
            SearchSuggestionsState(
                visible = current.query.isNotEmpty() && !current.showResults,
                suggestions = current.suggestions,
            )
        }
    }
    if (state.visible && state.suggestions.isNotEmpty()) {
        SearchSuggestionDropdown(
            suggestions = state.suggestions,
            onSuggestionClick = onSuggestionClick,
            modifier = modifier,
        )
    }
}
