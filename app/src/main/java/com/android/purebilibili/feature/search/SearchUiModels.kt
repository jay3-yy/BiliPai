package com.android.purebilibili.feature.search

import androidx.compose.runtime.Immutable
import com.android.purebilibili.data.model.response.HotItem
import com.android.purebilibili.data.model.response.SearchSuggestTag

@Immutable
data class SearchKeywordUiModel(
    val keyword: String,
    val title: String = keyword,
    val subtitle: String? = null,
    val iconUrl: String? = null,
    val showLiveBadge: Boolean = false
)

@Immutable
data class SearchSuggestionUiModel(
    val keyword: String,
    val richText: String = keyword
)

internal fun HotItem.toSearchKeywordUiModel(): SearchKeywordUiModel {
    return SearchKeywordUiModel(
        keyword = keyword.ifBlank { show_name },
        title = show_name.ifBlank { keyword },
        subtitle = recommend_reason.replaceFirst('·', ' ').ifBlank { null },
        iconUrl = icon
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { raw ->
                when {
                    raw.startsWith("//") -> "https:$raw"
                    raw.startsWith("http://") -> raw.replace("http://", "https://")
                    else -> raw
                }
            },
        showLiveBadge = show_live_icon
    )
}

internal fun SearchSuggestTag.toSearchSuggestionUiModel(): SearchSuggestionUiModel {
    val resolvedKeyword = term.ifBlank {
        value.ifBlank { name.stripSearchMarkup() }
    }
    return SearchSuggestionUiModel(
        keyword = resolvedKeyword,
        richText = name.ifBlank { resolvedKeyword }
    )
}

private val SEARCH_UI_HTML_TAG_REGEX = Regex("<.*?>")

internal fun String.stripSearchMarkup(): String {
    return replace(SEARCH_UI_HTML_TAG_REGEX, "")
        .replace("&quot;", "\"")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .trim()
}
