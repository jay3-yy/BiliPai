package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.core.filter.ContentListProjection
import com.android.purebilibili.core.filter.contentFiltered
import com.android.purebilibili.core.store.ContentFilterReason
import com.android.purebilibili.core.store.ContentFilterScope
import com.android.purebilibili.core.store.ContentFilterSnapshot
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.repository.isDynamicFromBlockedUser
import com.android.purebilibili.data.repository.recordFilteredDynamic
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

private fun shouldKeepDynamicBySharedRules(
    item: DynamicItem,
    rules: ContentFilterSnapshot,
): Boolean {
    if (isDynamicFromBlockedUser(item, rules.blockedMids)) {
        recordFilteredDynamic(item, ContentFilterReason.BLOCKED_USER)
        return false
    }
    if (rules.keywordFilteringEnabled && ContentFilterScope.DYNAMIC in rules.scopes && rules.keywords.isNotEmpty()) {
        val text = extractDynamicFilterText(item)
        if (text.isNotEmpty() && rules.blocksText(text, ContentFilterScope.DYNAMIC)) {
            recordFilteredDynamic(
                item = item,
                reason = ContentFilterReason.KEYWORD,
                matchedRule = rules.firstMatchingSharedKeyword(text).orEmpty(),
            )
            return false
        }
    }
    return true
}

internal fun StateFlow<DynamicUiState>.filteredDynamicContent(scope: CoroutineScope): StateFlow<DynamicUiState> {
    val timelines = mutableMapOf<String, ContentListProjection<DynamicItem>>()
    val userItems = ContentListProjection<DynamicItem>()
    return contentFiltered(scope) { state, rules ->
        val types = state.timelinePages.keys + state.timelineRequestType
        val updated = types.fold(state) { result, type ->
            val cache = timelines.getOrPut(type) { ContentListProjection() }
            updateDynamicTimelinePage(result, type) { page ->
                page.copy(items = cache.filter(page.items, rules) {
                    shouldKeepDynamicBySharedRules(it, rules)
                }.toImmutableList())
            }
        }
        updated.copy(userItems = userItems.filter(state.userItems, rules) {
            shouldKeepDynamicBySharedRules(it, rules)
        }.toImmutableList())
    }
}
