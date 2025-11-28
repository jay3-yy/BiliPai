// 文件路径: data/model/response/ListModels.kt
package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

// 通用的列表响应包装
@Serializable
data class ListResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
)

// --- 历史记录相关 ---
@Serializable
data class HistoryData(
    val list: List<HistoryItem> = emptyList()
)

@Serializable
data class HistoryItem(
    val title: String = "",
    val cover: String = "",
    val history: HistoryDetails = HistoryDetails(),
    val author_name: String = "",
    val author_mid: Long = 0,
    val view_at: Long = 0 // 观看时间戳
) {
    // 转换为通用的 VideoItem 以便 UI 复用
    fun toVideoItem(): VideoItem {
        return VideoItem(
            bvid = history.bvid,
            title = title,
            pic = cover,
            owner = Owner(name = author_name, mid = author_mid)
        )
    }
}

@Serializable
data class HistoryDetails(
    val bvid: String = "",
    val cid: Long = 0
)

// --- 收藏夹相关 ---
// 1. 收藏夹内容
@Serializable
data class FavoriteData(
    val medias: List<FavoriteItem>? = emptyList()
)

@Serializable
data class FavoriteItem(
    val id: Long = 0,
    val title: String = "",
    val cover: String = "",
    val intro: String = "",
    val upper: Upper = Upper(),
    val ctime: Long = 0, // 收藏时间
    val bvid: String = ""
) {
    fun toVideoItem(): VideoItem {
        return VideoItem(
            bvid = bvid,
            title = title,
            pic = cover,
            owner = Owner(name = upper.name, mid = upper.mid)
        )
    }
}

@Serializable
data class Upper(
    val mid: Long = 0,
    val name: String = ""
)

// 2. 🔥 新增：收藏夹文件夹列表响应
@Serializable
data class FavFolderResponse(
    val code: Int = 0,
    val data: FavFolderData? = null
)

@Serializable
data class FavFolderData(
    val list: List<FavFolderItem>? = emptyList()
)

@Serializable
data class FavFolderItem(
    val id: Long = 0, // 这个 id 就是 media_id
    val fid: Long = 0,
    val title: String = "",
    val media_count: Int = 0
)