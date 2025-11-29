package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

// --- 1. 热搜模型 (保持不变) ---
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

// --- 2. 搜索结果模型 ---
@Serializable
data class SearchResponse(
    val data: SearchData? = null
)

@Serializable
data class SearchData(
    val result: List<SearchResultCategory>? = null
)

@Serializable
data class SearchResultCategory(
    val result_type: String = "",
    val data: List<SearchVideoItem>? = null
)

@Serializable
data class SearchVideoItem(
    val id: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val author: String = "",
    val play: Int = 0,
    val video_review: Int = 0,
    val duration: String = ""
) {
    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = id,
            bvid = bvid,
            // 🔥🔥🔥 核心修复：使用正则表达式清洗 HTML 标签和转义字符 🔥🔥🔥
            title = title.replace(Regex("<.*?>"), "") // 去除 <em class="..."> 和 </em>
                .replace("&quot;", "\"")      // 修复双引号转义
                .replace("&amp;", "&")        // 修复 & 符号转义
                .replace("&lt;", "<")         // 修复 < 符号
                .replace("&gt;", ">"),        // 修复 > 符号

            pic = if (pic.startsWith("//")) "https:$pic" else pic,
            owner = Owner(name = author),
            stat = Stat(view = play, danmaku = video_review),
            duration = parseDuration(duration)
        )
    }

    private fun parseDuration(raw: String): Int {
        if (raw.isBlank()) return 0
        if (raw.all { it.isDigit() }) return raw.toIntOrNull() ?: 0
        val parts = raw.split(":")
        return when (parts.size) {
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 + (parts[1].toIntOrNull() ?: 0) * 60 + (parts[2].toIntOrNull() ?: 0)
            else -> 0
        }
    }
}