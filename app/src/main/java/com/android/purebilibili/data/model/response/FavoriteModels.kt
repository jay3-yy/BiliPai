// 文件路径: data/model/response/FavoriteModels.kt
package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

/**
 * 收藏夹和稍后再看相关数据模型
 * 从 ListModels.kt 拆分出来，提高代码可维护性
 */

@Serializable
enum class FavFolderSource {
    OWNED,
    SUBSCRIBED
}

// --- 收藏夹列表响应 ---
@Serializable
data class FavFolderResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FavFolderList? = null
)

// --- 收藏夹内容响应 (支持分页) ---
@Serializable
data class FavoriteResourceResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FavoriteResourceData? = null
)

@Serializable
data class FavoriteResourceData(
    val info: FavoriteInfo? = null,
    val medias: List<FavoriteData>? = null,
    val has_more: Boolean = false,
    val ttl: Int = 0
)

@Serializable
data class FavoriteInfo(
    val id: Long = 0,
    val fid: Long = 0,
    val mid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val media_count: Int = 0
)

@Serializable
data class FavFolderList(
    val count: Int = 0,
    val list: List<FavFolder>? = null
)

@Serializable
data class FavFolder(
    val id: Long = 0,
    val fid: Long = 0,
    val mid: Long = 0,
    val title: String = "",
    val media_count: Int = 0,
    val fav_state: Int = 0,
    val type: Int = 0,
    val source: FavFolderSource = FavFolderSource.OWNED
)

// --- 收藏夹内容单项 ---
@Serializable
data class FavoriteData(
    val id: Long = 0,
    val type: Int = 0,
    val attr: Int = 0,
    val title: String = "",
    val cover: String = "",
    val intro: String = "",
    val media_count: Int = 0,
    val season_id: Long = 0,
    val bv_id: String = "",
    val bvid: String = "",
    val duration: Int = 0,
    val progress: Int = 0,
    val view_at: Long = 0,
    val upper: Upper? = null,
    val cnt_info: CntInfo? = null,
    val ugc: FavoriteUgc? = null
) {
    fun toVideoItem(): VideoItem {
        if (type == 21) {
            val resolvedCollectionId = season_id.takeIf { it > 0L } ?: id
            return VideoItem(
                id = id,
                title = title,
                pic = cover,
                owner = Owner(mid = upper?.mid ?: 0, name = upper?.name ?: "", face = upper?.face ?: ""),
                stat = Stat(favorite = cnt_info?.collect ?: 0),
                isCollectionResource = true,
                collectionId = resolvedCollectionId,
                collectionMid = upper?.mid ?: 0,
                collectionMediaCount = media_count,
                collectionSubtitle = intro
            )
        }
        val resolvedBvid = bvid.ifBlank { bv_id }
        return VideoItem(
            id = id,
            aid = id, // [Fix] FavoriteData.id is equivalent to aid, required for removing favorite
            bvid = resolvedBvid,
            cid = ugc?.first_cid ?: 0L,
            title = title,
            pic = cover,
            owner = Owner(mid = upper?.mid ?: 0, name = upper?.name ?: "", face = upper?.face ?: ""),
            stat = Stat(view = cnt_info?.play ?: 0, danmaku = cnt_info?.danmaku ?: 0),
            duration = duration,
            progress = progress,
            view_at = view_at
        )
    }
}

@Serializable
data class FavoriteUgc(
    val first_cid: Long = 0
)

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

// --- 稍后再看 Response ---
@Serializable
data class WatchLaterResponse(
    val code: Int = 0,
    val message: String = "",
    val data: WatchLaterData? = null
)

@Serializable
data class WatchLaterData(
    val count: Int = 0,
    val list: List<WatchLaterItem>? = null
)

@Serializable
data class WatchLaterItem(
    val aid: Long = 0,
    val bvid: String? = null,
    val cid: Long? = null,
    val title: String? = null,
    val pic: String? = null,
    val duration: Int? = null,
    val progress: Int? = null,
    val view_at: Long? = null,
    val pubdate: Long? = null,
    val owner: WatchLaterOwner? = null,
    val stat: WatchLaterStat? = null
)

@Serializable
data class WatchLaterOwner(
    val mid: Long? = null,
    val name: String? = null,
    val face: String? = null
)

@Serializable
data class WatchLaterStat(
    val view: Int? = null,
    val danmaku: Int? = null,
    val reply: Int? = null,
    val like: Int? = null,
    val coin: Int? = null,
    val favorite: Int? = null,
    val share: Int? = null
)
