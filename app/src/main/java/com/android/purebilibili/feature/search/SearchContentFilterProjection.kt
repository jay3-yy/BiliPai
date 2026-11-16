package com.android.purebilibili.feature.search

import com.android.purebilibili.core.filter.ContentListProjection
import com.android.purebilibili.core.filter.contentFiltered
import com.android.purebilibili.core.filter.keepsVideo
import com.android.purebilibili.data.model.response.SearchType
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.SearchUpItem
import com.android.purebilibili.data.model.response.LiveRoomSearchItem
import com.android.purebilibili.data.model.response.SearchLiveUserItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

internal fun StateFlow<SearchUiState>.filteredSearchContent(scope: CoroutineScope): StateFlow<SearchUiState> {
    val videos = mutableMapOf<SearchType, ContentListProjection<VideoItem>>()
    val usersCache = mutableMapOf<SearchType, ContentListProjection<SearchUpItem>>()
    val roomsCache = mutableMapOf<SearchType, ContentListProjection<LiveRoomSearchItem>>()
    val liveUsersCache = mutableMapOf<SearchType, ContentListProjection<SearchLiveUserItem>>()
    return contentFiltered(scope) { state, rules ->
        fun filter(type: SearchType, page: SearchResultPageUiState): SearchResultPageUiState {
            val filtered = videos.getOrPut(type) { ContentListProjection() }.filter(page.searchResults, rules) {
                rules.keepsVideo(it, com.android.purebilibili.plugin.sdk.FeedKind.SEARCH)
            }
            val users = usersCache.getOrPut(type) { ContentListProjection() }.project(page.upResults, rules) { up ->
                if (rules.blocksUser(up.mid)) null else {
                val nested = up.res?.filterNot {
                    rules.blocksVideo(it.title, it.mid, surface = com.android.purebilibili.core.store.ContentVideoSurface.SEARCH)
                }
                if (nested == up.res) up else up.copy(res = nested)
                }
            }
            val rooms = roomsCache.getOrPut(type) { ContentListProjection() }.filter(page.liveResults, rules) { !rules.blocksUser(it.uid) }
            val liveUsers = liveUsersCache.getOrPut(type) { ContentListProjection() }.filter(page.liveUserResults, rules) { !rules.blocksUser(it.uid) }
            val visible = filtered.size + users.size + page.bangumiResults.size + page.matchResults.size +
                rooms.size + liveUsers.size +
                page.articleResults.size + page.topicResults.size + page.photoResults.size
            val raw = page.searchResults.size + page.upResults.size + page.bangumiResults.size + page.matchResults.size +
                page.liveResults.size + page.liveUserResults.size + page.articleResults.size + page.topicResults.size + page.photoResults.size
            return page.copy(
                searchResults = filtered, upResults = users,
                liveResults = rooms, liveUserResults = liveUsers,
                emptyStateReason = if (raw > 0) resolveSearchEmptyStateReason(raw, visible) else page.emptyStateReason,
            )
        }
        val pages = state.resultPages.mapValues { (type, page) -> filter(type, page) }
        val current = filter(state.searchType, state.toCurrentSearchResultPage())
        state.copy(resultPages = pages).withSearchResultPageMirrored(state.searchType, current)
    }
}
