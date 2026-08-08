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

@Serializable
data class RelationTagItem(
    val tagid: Long = 0,
    val name: String = "",
    val count: Int = 0,
    val tip: String = ""
)

@Serializable
data class RelationTagsResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 0,
    val data: List<RelationTagItem> = emptyList()
)

@Serializable
data class RelationTagUserResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 0,
    val data: Map<String, String> = emptyMap()
)

@Serializable
data class RelationTagMembersResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 0,
    val data: List<RelationTagMemberItem> = emptyList()
)

@Serializable
data class RelationTagMemberItem(
    val mid: Long = 0
)

@Serializable
data class RelationBlacksResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 0,
    val data: RelationBlacksData = RelationBlacksData()
)

@Serializable
data class RelationBlacksData(
    val list: List<FollowingUser> = emptyList(),
    val total: Int = 0,
    val re_version: Int = 0
)

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

// --- 0.3.1 视频交互关系响应 ---
@Serializable
data class VideoRelationResponse(
    val code: Int = 0,
    val message: String = "",
    val data: VideoRelationData? = null
)

@Serializable
data class VideoRelationData(
    val attention: Boolean = false,
    val favorite: Boolean = false,
    @SerialName("season_fav")
    val seasonFav: Boolean = false,
    val like: Boolean = false,
    val dislike: Boolean = false,
    val coin: Int = 0
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
data class RelationTagFollowingsResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 0,
    val data: List<FollowingUser> = emptyList()
)

@Serializable
data class FollowingUser(
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    val sign: String = "",
    val mtime: Long = 0,
    @SerialName("official_verify")
    val officialVerify: OfficialVerify = OfficialVerify()
)

// --- 1. 核心通用视频模型 (UI层使用) ---
@Serializable
data class VideoItem(
    val id: Long = 0,
    val bvid: String = "",
    val dynamicId: String = "", // Home Follow 动态卡片跳转详情用
    val aid: Long = 0,   // [修复] 新增 aid 字段，移动端推荐流可能只返回 aid
    val cid: Long = 0,   //  明确的 CID 字段，用于播放
    val title: String = "",
    val searchHighlightedTitle: String = "",
    val pic: String = "",
    val owner: Owner = Owner(),
    val stat: Stat = Stat(),
    val tid: Int = 0,
    val tname: String = "",
    val duration: Int = 0,
    val progress: Int = -1,
    val view_at: Long = 0,
    val pubdate: Long = 0,
    val isVertical: Boolean = false,  //  是否为竖屏视频
    val isFollowed: Boolean = false,  //  是否已关注该 UP 主(web 推荐接口的 is_followed)
    val isCollectionResource: Boolean = false,
    val collectionId: Long = 0,
    val collectionMid: Long = 0,
    val collectionMediaCount: Int = 0,
    val collectionSubtitle: String = "",
    val rights: VideoRights? = null,
    val recommendationFeedback: RecommendationFeedbackMetadata? = null,
    val contentType: String = "",
    val navigationUrl: String = ""
)

@Serializable
data class RecommendationFeedbackMetadata(
    val goto: String = "",
    val param: String = "",
    val reasons: List<RecommendationFeedbackReason> = emptyList(),
    val supportsServerSync: Boolean = false
)

@Serializable
data class RecommendationFeedbackReason(
    val id: Long? = null,
    val name: String = "",
    val toast: String = "",
    val type: RecommendationFeedbackType = RecommendationFeedbackType.DISLIKE,
    val localAction: RecommendationFeedbackLocalAction = RecommendationFeedbackLocalAction.VIDEO_ONLY
)

@Serializable
enum class RecommendationFeedbackType {
    DISLIKE,
    FEEDBACK
}

@Serializable
enum class RecommendationFeedbackLocalAction {
    VIDEO_ONLY,
    CREATOR,
    CATEGORY,
    SIMILAR_CONTENT
}

@Serializable
data class VideoRights(
    val pay: Int = 0,
    @SerialName("arc_pay")
    val arcPay: Int = 0,
    @SerialName("ugc_pay")
    val ugcPay: Int = 0,
    @SerialName("ugc_pay_preview")
    val ugcPayPreview: Int = 0,
    @SerialName("pay_free_watch")
    val payFreeWatch: Int = 0
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
    val tid: Int = 0,
    val tname: String = "",
    //  [新增] 视频尺寸信息 (用于判断竖屏视频)
    val dimension: Dimension? = null,
    val rights: VideoRights? = null,
    //  [新增] 是否已关注该 UP 主(推荐流过滤已关注豁免用; 接口返回 0/1 整数)
    @SerialName("is_followed")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isFollowed: Boolean = false
) {
    fun toVideoItem(): VideoItem {
        return VideoItem(
            id = id,
            bvid = bvid ?: "",
            aid = id,  // [修复] 填充 aid (id 通常就是 aid)
            cid = cid ?: 0L,
            title = title ?: "",
            pic = pic ?: "",
            owner = Owner(mid = owner?.mid ?: 0, name = owner?.name ?: "", face = owner?.face ?: ""),
            stat = Stat(
                view = requestStatConvert(stat?.view),
                like = requestStatConvert(stat?.like),
                danmaku = requestStatConvert(stat?.danmaku),
                reply = requestStatConvert(stat?.reply)
            ),
            tid = tid,
            tname = tname,
            duration = duration ?: 0,
            pubdate = pubdate ?: 0L,
            isVertical = dimension?.isVertical == true,
            isFollowed = isFollowed,
            rights = rights
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
    val danmaku: Long = 0,
    val reply: Long = 0
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
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val tid: Int = 0,
    val tname: String = "",
    val pic: String = "",
    val title: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: Owner = Owner(),
    val stat: PopularStat = PopularStat(),
    val rights: VideoRights? = null
) {
    fun toVideoItem(): VideoItem {
        val resolvedAid = if (aid > 0) aid else cid
        return VideoItem(
            id = resolvedAid,
            bvid = bvid,
            aid = resolvedAid,
            cid = cid,
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
            tid = tid,
            tname = tname,
            duration = duration,
            pubdate = pubdate,
            rights = rights
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

@Serializable
data class RankingResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RankingData? = null
)

@Serializable
data class RankingData(
    val note: String = "",
    val list: List<PopularItem>? = null
)

@Serializable
data class PopularPreciousResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PopularPreciousData? = null
)

@Serializable
data class PopularPreciousData(
    val title: String = "",
    val media_id: Long = 0,
    val explain: String = "",
    val list: List<PopularItem>? = null
)

@Serializable
data class PopularSeriesListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PopularSeriesListData? = null
)

@Serializable
data class PopularSeriesListData(
    val list: List<PopularSeriesPeriod>? = null
)

@Serializable
data class PopularSeriesPeriod(
    val number: Int = 0,
    val subject: String = "",
    val status: Int = 0,
    val name: String = ""
)

@Serializable
data class PopularSeriesOneResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PopularSeriesOneData? = null
)

@Serializable
data class PopularSeriesOneData(
    val config: PopularSeriesConfig? = null,
    val reminder: String = "",
    val list: List<PopularItem>? = null
)

@Serializable
data class PopularSeriesConfig(
    val id: Long = 0,
    val type: String = "",
    val number: Int = 0,
    val subject: String = "",
    val stime: Long = 0,
    val etime: Long = 0,
    val status: Int = 0,
    val name: String = "",
    val label: String = "",
    val hint: String = "",
    val color: Int = 0,
    val cover: String = "",
    val share_title: String = "",
    val share_subtitle: String = "",
    val media_id: Long = 0
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
    val tid: Int = 0,
    val tname: String = "",
    val pic: String = "",
    val title: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: Owner = Owner(),
    val stat: DynamicRegionStat = DynamicRegionStat(),
    val rights: VideoRights? = null
) {
     fun toVideoItem(): VideoItem {
        return VideoItem(
            id = cid,
            bvid = bvid,
            aid = aid,  // [修复] 使用实际的 aid 字段
            cid = cid,
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
            tid = tid,
            tname = tname,
            duration = duration,
            pubdate = pubdate,
            rights = rights
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

// --- 7. 移动端推荐流 Response ---
@Serializable
data class MobileFeedResponse(
    val code: Int = 0,
    val message: String = "",
    val data: MobileFeedData? = null
)

@Serializable
data class MobileFeedData(
    val items: List<MobileFeedItem>? = null
)

@Serializable
data class MobileFeedItem(
    val idx: Long = 0,
    @SerialName("param") val param: String = "",  // 这是 bvid 或 aid
    val goto: String = "",  // "av" 表示视频
    val uri: String = "",
    val cover: String = "",
    val title: String = "",
    val duration: Long = 0,
    val args: MobileFeedArgs? = null,
    @SerialName("player_args") val playerArgs: MobileFeedPlayerArgs? = null,
    @SerialName("three_point_v2") val threePointV2: List<MobileFeedThreePoint> = emptyList(),
    @SerialName("cover_left_text_1") val coverLeftText1: String = "",  // 播放量
    @SerialName("cover_left_text_2") val coverLeftText2: String = ""   // 弹幕数
) {
    fun toVideoItem(): VideoItem {
        // [修复] 改进 bvid 提取逻辑
        // 1. 优先从 uri 提取 (格式: bilibili://video/BV1xxx)
        // 2. 如果 param 以 "BV" 开头，使用 param
        // 3. 否则 bvid 置空，依赖 aid 回退
        val extractedBvid = uri.substringAfterLast("/").takeIf { it.startsWith("BV") }
            ?: param.takeIf { it.startsWith("BV") }
            ?: ""
        
        // [修复] 获取 aid：优先使用 playerArgs.aid，其次 args.aid，最后尝试解析 param
        val extractedAid = playerArgs?.aid 
            ?: args?.aid 
            ?: param.toLongOrNull() 
            ?: 0L
        
        return VideoItem(
            id = idx,
            bvid = extractedBvid,
            aid = extractedAid,  // [修复] 填充 aid，用于 bvid 为空时的回退
            cid = playerArgs?.cid ?: 0L,
            title = title,
            pic = cover,
            owner = Owner(
                mid = args?.upId ?: 0,
                name = args?.upName ?: "",
                face = args?.upFace ?: ""
            ),
            stat = Stat(
                view = parseStatText(coverLeftText1),
                danmaku = parseStatText(coverLeftText2)
            ),
            tid = args?.tid?.takeIf { it > 0 } ?: args?.rid ?: 0,
            tname = args?.let { it.tname.ifBlank { it.rname } }.orEmpty(),
            duration = duration.toInt(),
            recommendationFeedback = RecommendationFeedbackMetadata(
                goto = goto,
                param = param,
                reasons = threePointV2.flatMap { group ->
                    val type = when (group.type) {
                        "feedback" -> RecommendationFeedbackType.FEEDBACK
                        "dislike" -> RecommendationFeedbackType.DISLIKE
                        else -> null
                    } ?: return@flatMap emptyList()
                    group.reasons.mapNotNull { reason ->
                        reason.name.takeIf { it.isNotBlank() }?.let {
                            RecommendationFeedbackReason(
                                id = reason.id,
                                name = it,
                                toast = reason.toast,
                                type = type,
                                localAction = resolveRecommendationFeedbackLocalAction(
                                    type = type,
                                    reasonId = reason.id,
                                    reasonName = it
                                )
                            )
                        }
                    }
                },
                supportsServerSync = goto.isNotBlank() && param.isNotBlank()
            )
        )
    }
    
    // 解析 "123.4万" -> 1234000
    private fun parseStatText(text: String): Int {
        return try {
            when {
                text.contains("万") -> (text.replace("万", "").replace(",", "").toFloat() * 10000).toInt()
                text.contains("亿") -> (text.replace("亿", "").replace(",", "").toFloat() * 100000000).toInt()
                else -> text.replace(",", "").toIntOrNull() ?: 0
            }
        } catch (e: Exception) { 0 }
    }
}

@Serializable
data class MobileFeedArgs(
    @SerialName("up_id") val upId: Long = 0,
    @SerialName("up_name") val upName: String = "",
    @SerialName("up_face") val upFace: String = "",
    val aid: Long = 0,
    val rid: Int = 0,
    val rname: String = "",
    val tid: Int = 0,
    val tname: String = ""
)

@Serializable
data class MobileFeedThreePoint(
    val title: String = "",
    val type: String = "",
    val reasons: List<MobileFeedFeedbackReason> = emptyList()
)

@Serializable
data class MobileFeedFeedbackReason(
    val id: Long = 0,
    val name: String = "",
    val toast: String = ""
)

internal fun resolveRecommendationFeedbackLocalAction(
    type: RecommendationFeedbackType,
    reasonId: Long,
    reasonName: String = ""
): RecommendationFeedbackLocalAction {
    if (type == RecommendationFeedbackType.FEEDBACK) {
        return RecommendationFeedbackLocalAction.VIDEO_ONLY
    }
    return when (reasonId) {
        4L -> RecommendationFeedbackLocalAction.CREATOR
        2L, 3L -> RecommendationFeedbackLocalAction.CATEGORY
        12L -> RecommendationFeedbackLocalAction.SIMILAR_CONTENT
        // 服务端 reason id 可能变更；用文案再推断一次，避免全部退化为“仅当前视频”。
        else -> inferRecommendationFeedbackLocalActionFromName(reasonName)
    }
}

internal fun inferRecommendationFeedbackLocalActionFromName(
    reasonName: String
): RecommendationFeedbackLocalAction {
    val normalized = reasonName.trim().lowercase()
    if (normalized.isBlank()) return RecommendationFeedbackLocalAction.VIDEO_ONLY
    return when {
        normalized.contains("up主") ||
            normalized.startsWith("up:") ||
            normalized.startsWith("up：") ||
            normalized.contains("up主:") ||
            normalized.contains("up主：") -> RecommendationFeedbackLocalAction.CREATOR
        normalized.contains("分区") -> RecommendationFeedbackLocalAction.CATEGORY
        normalized.contains("此类") ||
            normalized.contains("相似") ||
            normalized.contains("过多") ||
            normalized.contains("同类") -> RecommendationFeedbackLocalAction.SIMILAR_CONTENT
        else -> RecommendationFeedbackLocalAction.VIDEO_ONLY
    }
}

@Serializable
data class MobileFeedPlayerArgs(
    val aid: Long = 0,
    val cid: Long = 0,
    val duration: Long = 0
)
