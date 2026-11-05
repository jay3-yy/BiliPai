package com.android.purebilibili.feature.search

import com.android.purebilibili.data.model.response.SearchType
import com.android.purebilibili.data.repository.SearchOrder
import com.android.purebilibili.data.repository.SearchDuration
import com.android.purebilibili.data.repository.SearchLiveOrder
import com.android.purebilibili.data.repository.SearchArticleCategory
import com.android.purebilibili.data.repository.SearchPhotoCategory
import com.android.purebilibili.data.repository.SearchOrderSort
import com.android.purebilibili.data.repository.SearchUpOrder
import com.android.purebilibili.data.repository.SearchUserType
import com.android.purebilibili.core.database.entity.SearchHistory

/** 页面壳只观察导航、筛选和输入是否为空；正文和联想数据由各自宿主读取。 */
internal data class SearchScreenState(
    val hasQuery: Boolean,
    val articleCategory: SearchArticleCategory,
    val articleOrder: SearchOrder,
    val defaultSearchHint: String,
    val defaultSearchKeyword: String,
    val easterEggMessage: String?,
    val isSearching: Boolean,
    val liveOrder: SearchLiveOrder,
    val photoCategory: SearchPhotoCategory,
    val photoOrder: SearchOrder,
    val pubBegin: Long?,
    val pubEnd: Long?,
    val pubTimeType: SearchVideoPubTimeType,
    val searchDurations: Set<SearchDuration>,
    val searchOrder: SearchOrder,
    val searchSessionId: Long,
    val searchType: SearchType,
    val searchTypeCounts: Map<SearchType, Int>,
    val showResults: Boolean,
    val upOrder: SearchUpOrder,
    val upOrderSort: SearchOrderSort,
    val upUserType: SearchUserType,
    val videoTid: Int,
)

internal fun SearchUiState.toSearchScreenState() = SearchScreenState(
    hasQuery = query.isNotBlank(),
    articleCategory = articleCategory,
    articleOrder = articleOrder,
    defaultSearchHint = defaultSearchHint,
    defaultSearchKeyword = defaultSearchKeyword,
    easterEggMessage = easterEggMessage,
    isSearching = isSearching,
    liveOrder = liveOrder,
    photoCategory = photoCategory,
    photoOrder = photoOrder,
    pubBegin = pubBegin,
    pubEnd = pubEnd,
    pubTimeType = pubTimeType,
    searchDurations = searchDurations,
    searchOrder = searchOrder,
    searchSessionId = searchSessionId,
    searchType = searchType,
    searchTypeCounts = searchTypeCounts,
    showResults = showResults,
    upOrder = upOrder,
    upOrderSort = upOrderSort,
    upUserType = upUserType,
    videoTid = videoTid,
)

internal data class SearchLandingState(
    val hotList: List<SearchKeywordUiModel>,
    val hotListError: String?,
    val isRefreshingHotList: Boolean,
    val discoverTitle: String,
    val discoverList: List<SearchKeywordUiModel>,
    val discoverListError: String?,
    val isRefreshingDiscoverList: Boolean,
    val historyList: List<SearchHistory>,
    val recordSearchHistory: Boolean,
    val globalPrivacyMode: Boolean,
)

internal fun SearchUiState.toSearchLandingState() = SearchLandingState(
    hotList = hotList,
    hotListError = hotListError,
    isRefreshingHotList = isRefreshingHotList,
    discoverTitle = discoverTitle,
    discoverList = discoverList,
    discoverListError = discoverListError,
    isRefreshingDiscoverList = isRefreshingDiscoverList,
    historyList = historyList,
    recordSearchHistory = recordSearchHistory,
    globalPrivacyMode = globalPrivacyMode,
)
