package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

// --- 1. 热搜模型 ---
@Serializable
data class HotSearchResponse(
    val data: HotSearchData? = null
)

@Serializable
data class HotSearchData(
    val trending: TrendingData? = null
)

@Serializable
data class TrendingData(
    val list: List<HotItem>? = null
)

@Serializable
data class HotItem(
    val keyword: String = "",
    val show_name: String = "",
    val icon: String = ""
)

// --- 2. 搜索结果模型 (已切回适配 search/all/v2) ---
@Serializable
data class SearchResponse(
    val data: SearchData? = null
)

@Serializable
data class SearchData(
    // 综合搜索返回的是一个分类列表 (包含 video, user, bangumi 等)
    val result: List<SearchResultCategory>? = null
)

@Serializable
data class SearchResultCategory(
    val result_type: String = "", // 例如 "video"
    val data: List<VideoItem>? = null
)