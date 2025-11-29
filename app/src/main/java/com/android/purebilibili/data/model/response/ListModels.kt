package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- 1. 核心通用视频模型 (UI层使用) ---
@Serializable
data class VideoItem(
    val id: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "", // 封面图 URL
    val owner: Owner = Owner(),
    val stat: Stat = Stat(),
    // 🔥 关键修复：补全时长字段，解决 HomeScreen 报错
    val duration: Int = 0
)

@Serializable
data class Owner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)

@Serializable
data class Stat(
    val view: Int = 0,
    val danmaku: Int = 0,
    val reply: Int = 0,
    val like: Int = 0
)

// --- 2. 历史记录相关模型 ---
@Serializable
data class HistoryData(
    val title: String = "",
    val pic: String = "", // 历史记录接口返回的封面字段是 pic
    val author_name: String = "",
    val author_face: String = "",
    val duration: Int = 0,
    // 历史记录的 BVID 藏在 history 对象里
    val history: HistoryPage? = null,
    val stat: Stat = Stat() // 历史接口有时包含 stat
) {
    // 转换函数：转为通用 VideoItem
    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = history?.oid ?: 0,
            bvid = history?.bvid ?: "",
            title = title,
            pic = pic,
            owner = Owner(name = author_name, face = author_face),
            stat = stat,
            duration = duration
        )
    }
}

@Serializable
data class HistoryPage(
    val oid: Long = 0,
    val bvid: String = ""
)

// --- 3. 收藏夹相关模型 ---
// 收藏夹列表响应
@Serializable
data class FavFolderResponse(
    val code: Int = 0,
    val data: FavFolderList? = null
)

@Serializable
data class FavFolderList(
    val list: List<FavFolder>? = null
)

@Serializable
data class FavFolder(
    val id: Long = 0,
    val fid: Long = 0,
    val mid: Long = 0,
    val title: String = "",
    val media_count: Int = 0
)

// 收藏夹内容单项
@Serializable
data class FavoriteData(
    val id: Long = 0,
    val title: String = "",
    val cover: String = "", // 收藏夹接口返回的封面字段是 cover
    val bvid: String = "",
    val duration: Int = 0,
    val upper: Upper? = null,
    val cnt_info: CntInfo? = null
) {
    // 转换函数：转为通用 VideoItem
    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = id,
            bvid = bvid,
            title = title,
            pic = cover, // 注意这里映射 cover -> pic
            owner = Owner(mid = upper?.mid ?: 0, name = upper?.name ?: "", face = upper?.face ?: ""),
            stat = Stat(view = cnt_info?.play ?: 0, danmaku = cnt_info?.danmaku ?: 0),
            duration = duration
        )
    }
}

@Serializable
data class Upper(
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)

@Serializable
data class CntInfo(
    val play: Int = 0,
    val danmaku: Int = 0,
    val collect: Int = 0
)

// --- 4. 通用列表响应包装类 ---
@Serializable
data class ListResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: ListData<T>? = null
)

@Serializable
data class ListData<T>(
    // 历史记录接口用 "list"，收藏夹接口用 "medias"
    // 我们在这里定义两个字段，Json 解析时只会填充其中一个
    val list: List<T>? = null,
    val medias: List<T>? = null
)
// --- 5. 推荐视频 Response (追加内容) ---
@Serializable
data class RecommendResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 0,
    val data: RecommendData? = null
)

@Serializable
data class RecommendData(
    val item: List<RecommendItem>? = null
)

@Serializable
data class RecommendItem(
    val id: Long = 0,
    val bvid: String? = null,
    val cid: Long? = null,
    val goto: String? = null,
    val uri: String? = null,
    val pic: String? = null, // 推荐接口的封面通常是 pic
    val title: String? = null,
    val duration: Int? = null,
    val pubdate: Long? = null,
    val owner: RecommendOwner? = null,
    val stat: RecommendStat? = null
) {
    // 转换函数：转为通用 VideoItem，方便 UI 显示
    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = id,
            bvid = bvid ?: "",
            title = title ?: "",
            pic = pic ?: "",
            owner = Owner(mid = owner?.mid ?: 0, name = owner?.name ?: "", face = owner?.face ?: ""),
            stat = Stat(view = requestStatConvert(stat?.view), like = requestStatConvert(stat?.like), danmaku = requestStatConvert(stat?.danmaku)),
            duration = duration ?: 0
        )
    }

    // 辅助函数：处理可能为 Long 也可能为 Int 的数据
    private fun requestStatConvert(num: Long?): Int {
        return num?.toInt() ?: 0
    }
}

@Serializable
data class RecommendOwner(
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)

@Serializable
data class RecommendStat(
    val view: Long = 0,
    val like: Long = 0,
    val danmaku: Long = 0
)