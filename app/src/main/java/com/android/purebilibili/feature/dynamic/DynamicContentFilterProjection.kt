package com.android.purebilibili.feature.dynamic

import com.android.purebilibili.core.filter.ContentListProjection
import com.android.purebilibili.core.filter.contentFiltered
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.repository.isDynamicFromBlockedUser
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal fun StateFlow<DynamicUiState>.filteredDynamicContent(scope: CoroutineScope): StateFlow<DynamicUiState> {
    val timelines = mutableMapOf<String, ContentListProjection<DynamicItem>>()
    val userItems = ContentListProjection<DynamicItem>()
    return contentFiltered(scope) { state, rules ->
        val types = state.timelinePages.keys + state.timelineRequestType
        val updated = types.fold(state) { result, type ->
            val cache = timelines.getOrPut(type) { ContentListProjection() }
            updateDynamicTimelinePage(result, type) { page ->
                page.copy(items = cache.filter(page.items, rules) {
                    !isDynamicFromBlockedUser(it, rules.blockedMids)
                }.toImmutableList())
            }
        }
        updated.copy(userItems = userItems.filter(state.userItems, rules) {
            !isDynamicFromBlockedUser(it, rules.blockedMids)
        }.toImmutableList())
    }
}
