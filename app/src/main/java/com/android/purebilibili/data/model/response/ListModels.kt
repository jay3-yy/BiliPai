package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 核心列表模型 - 视频、推荐、热门、分区相关
 * 
 * 注意：直播、收藏夹、历史记录相关模型已拆分到独立文件：
 * - LiveModels.kt: 直播相关
 * - FavoriteModels.kt: 收藏夹和稍后再看相关
 * - HistoryModels.kt: 历史记录相关
 */

// --- 0. 通用简单响应（用于操作类接口如关注/收藏）---
@Serializable
data class SimpleApiResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 0
)

// --- 0.1 关注关系响应 ---
@Serializable
data class RelationResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RelationData? = null
)

@Serializable
data class RelationData(
    val mid: Long = 0,
    val attribute: Int = 0,
    val mtime: Long = 0,
    val tag: List<Int>? = null,
    val special: Int = 0
) {
    val isFollowing: Boolean get() = attribute == 2 || attribute == 6
}

// --- 0.2 收藏状态响应 ---
@Serializable
data class FavouredResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FavouredData? = null
)

@Serializable
data class FavouredData(
    val count: Int = 0,
    val favoured: Boolean = false
)

// --- 0.3 点赞状态响应 ---
@Serializable
data class HasLikedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: Int = 0
)

// --- 0.4 投币状态响应 ---
@Serializable
data class HasCoinedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: CoinedData? = null
)

@Serializable
data class CoinedData(
    val multiply: Int = 0
)

// --- 0.5 关注列表响应 ---
@Serializable
data class FollowingsResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FollowingsData? = null
)

@Serializable
data class FollowingsData(
    val list: List<FollowingUser>? = null,
    val total: Int = 0
)

@Serializable
data class FollowingUser(
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val sign: String = ""
)

// --- 1. 核心通用视频模型 (UI层使用) ---
@Serializable
data class VideoItem(
    val id: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,  //  [新增] 明确的 CID 字段，用于播放
    val title: String = "",
    val pic: String = "",
    val owner: Owner = Owner(),
    val stat: Stat = Stat(),
    val duration: Int = 0,
    val progress: Int = -1,
    val view_at: Long = 0,
    val pubdate: Long = 0,
    val isVertical: Boolean = false  //  [新增] 是否为竖屏视频
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
    val like: Int = 0,
    val coin: Int = 0,
    val favorite: Int = 0,
    val share: Int = 0
)

// --- 2. 通用列表响应包装类 ---
@Serializable
data class ListResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: ListData<T>? = null
)

@Serializable
data class ListData<T>(
    val list: List<T>? = null,
    val medias: List<T>? = null
)

// --- 3. 推荐视频 Response ---
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
    val pic: String? = null,
    val title: String? = null,
    val duration: Int? = null,
    val pubdate: Long? = null,
    val owner: RecommendOwner? = null,
    val stat: RecommendStat? = null,
    //  [新增] 视频尺寸信息 (用于判断竖屏视频)
    val dimension: Dimension? = null
) {
    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = id,
            bvid = bvid ?: "",
            cid = cid ?: 0L,  //  [新增] 填充 CID
            title = title ?: "",
            pic = pic ?: "",
            owner = Owner(mid = owner?.mid ?: 0, name = owner?.name ?: "", face = owner?.face ?: ""),
            stat = Stat(view = requestStatConvert(stat?.view), like = requestStatConvert(stat?.like), danmaku = requestStatConvert(stat?.danmaku)),
            duration = duration ?: 0,
            isVertical = dimension?.isVertical == true  //  判断竖屏
        )
    }
    private fun requestStatConvert(num: Long?): Int = num?.toInt() ?: 0
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

// --- 4. 热门视频 Response ---
@Serializable
data class PopularResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PopularData? = null
)

@Serializable
data class PopularData(
    val list: List<PopularItem>? = null,
    val no_more: Boolean = false
)

@Serializable
data class PopularItem(
    val bvid: String = "",
    val cid: Long = 0,
    val pic: String = "",
    val title: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: Owner = Owner(),
    val stat: PopularStat = PopularStat()
) {
    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = cid,
            bvid = bvid,
            cid = cid,  //  [新增] 填充 CID
            title = title,
            pic = pic,
            owner = owner,
            stat = Stat(view = stat.view, like = stat.like, danmaku = stat.danmaku),
            duration = duration
        )
    }
}

@Serializable
data class PopularStat(
    val view: Int = 0,
    val like: Int = 0,
    val danmaku: Int = 0,
    val reply: Int = 0,
    val coin: Int = 0,
    val favorite: Int = 0,
    val share: Int = 0
)

// --- 5. 分区视频 Response ---
@Serializable
data class DynamicRegionResponse(
    val code: Int = 0,
    val message: String = "",
    val data: DynamicRegionData? = null
)

@Serializable
data class DynamicRegionData(
    val archives: List<DynamicRegionItem>? = null
)

@Serializable
data class DynamicRegionItem(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val pic: String = "",
    val title: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: Owner = Owner(),
    val stat: DynamicRegionStat = DynamicRegionStat()
) {
     fun toVideoItem(): VideoItem {
        return VideoItem(
            id = cid,
            bvid = bvid,
            cid = cid,  //  [新增] 填充 CID
            title = title,
            pic = pic,
            owner = owner,
            stat = Stat(
                view = stat.view,
                like = stat.like,
                danmaku = stat.danmaku,
                reply = stat.reply,
                coin = stat.coin,
                favorite = stat.favorite,
                share = stat.share
            ),
            duration = duration,
            pubdate = pubdate
        )
    }
}

@Serializable
data class DynamicRegionStat(
    val view: Int = 0,
    val like: Int = 0,
    val danmaku: Int = 0,
    val reply: Int = 0,
    val coin: Int = 0,
    val favorite: Int = 0,
    val share: Int = 0
)

// --- 6. 旧版分区 Response (兼容) ---
@Serializable
data class RegionVideosResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RegionVideosData? = null
)

@Serializable
data class RegionVideosData(
    val archives: List<PopularItem>? = null,
    val page: RegionPage? = null
)

@Serializable
data class RegionPage(
    val count: Int = 0,
    val num: Int = 1,
    val size: Int = 30
)