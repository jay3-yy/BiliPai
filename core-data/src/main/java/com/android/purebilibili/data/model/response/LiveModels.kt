// 文件路径: data/model/response/LiveModels.kt
package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNames

/**
 * 直播相关数据模型
 * 从 ListModels.kt 拆分出来，提高代码可维护性
 */

// --- 直播列表 Response ---
@Serializable
data class LiveResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveData? = null
)

@Serializable
data class LiveData(
    val list: List<LiveRoom>? = null,
    @SerialName("list_by_area") val listByArea: List<LiveRoom>? = null,
    val count: Int = 0,
    @SerialName("has_more") val hasMore: Int? = null
) {
    fun getAllRooms(): List<LiveRoom> = list ?: listByArea ?: emptyList()
}

@Serializable
data class LiveRecommendResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveRecommendData? = null
)

@Serializable
data class LiveRecommendData(
    @SerialName("recommend_room_list") val recommendRoomList: List<LiveRecommendRoom>? = null,
    @SerialName("top_room_id") val topRoomId: Long = 0
)

@Serializable
data class LiveRecommendRoom(
    @SerialName("roomid") val roomId: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val uname: String = "",
    val face: String = "",
    val cover: String = "",
    val keyframe: String = "",
    val online: Int = 0,
    @SerialName("area_v2_name") val areaName: String = "",
    @SerialName("area_v2_parent_name") val parentName: String = "",
    @SerialName("watched_show") val watchedShow: WatchedShow? = null,
    @SerialName("is_ad") val isAd: Boolean = false,
    @SerialName("show_callback") val showCallback: String = "",
    @SerialName("click_callback") val clickCallback: String = "",
    @SerialName("session_id") val sessionId: String = ""
) {
    fun toLiveRoom(): LiveRoom {
        return LiveRoom(
            roomid = roomId,
            uid = uid,
            title = title,
            uname = uname,
            face = face,
            cover = cover,
            online = online,
            watchedShow = watchedShow,
            areaName = areaName,
            parentName = parentName,
            keyframe = keyframe
        )
    }
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class LiveRoom(
    @JsonNames("roomid", "room_id")
    val roomid: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val uname: String = "",
    val face: String = "",
    val cover: String = "",
    @SerialName("user_cover") val userCover: String = "",
    @SerialName("system_cover") val systemCover: String = "",
    @SerialName("show_cover") val showCover: String = "",
    val online: Int = 0,
    @SerialName("watched_show") val watchedShow: WatchedShow? = null,
    @JsonNames("area_name", "area_v2_name")
    @SerialName("area_name")
    val areaName: String = "",
    @JsonNames("parent_name", "area_v2_parent_name")
    @SerialName("parent_name")
    val parentName: String = "",
    val keyframe: String = "",
    val feedback: List<LiveFeedbackGroup> = emptyList()
) {
    fun displayCover(preferFirstFrame: Boolean = false): String {
        val ordered = if (preferFirstFrame) {
            listOf(systemCover, keyframe, cover, userCover, showCover, face)
        } else {
            listOf(cover, userCover, showCover, systemCover, keyframe, face)
        }
        return ordered.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    fun viewerCount(): Int {
        val watchedCount = watchedShow?.viewerCount() ?: 0
        return when {
            watchedCount > 0 -> watchedCount
            online > 0 -> online
            else -> 0
        }
    }
}

// --- 直播播放 URL Response ---
@Serializable
data class LivePlayUrlResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LivePlayUrlData? = null
)

@Serializable
data class LivePlayUrlData(
    val durl: List<LiveDurl>? = null,
    val quality_description: List<LiveQuality>? = null,
    val current_quality: Int = 0,
    val playurl_info: PlayurlInfo? = null,
    @SerialName("room_id") val roomId: Long = 0,
    val uid: Long = 0,
    @SerialName("live_status") val liveStatus: Int? = null,
    @SerialName("live_time") val liveTime: Long = 0,
    @SerialName("is_portrait") val isPortrait: Boolean? = null
)

@Serializable
data class PlayurlInfo(
    val playurl: Playurl? = null
)

@Serializable
data class Playurl(
    val stream: List<StreamInfo>? = null,
    @SerialName("g_qn_desc") val gQnDesc: List<LiveQuality>? = null
)

@Serializable
data class StreamInfo(
    @SerialName("protocol_name") val protocolName: String = "",
    val format: List<FormatInfo>? = null
)

@Serializable
data class FormatInfo(
    @SerialName("format_name") val formatName: String = "",
    val codec: List<CodecInfo>? = null
)

@Serializable
data class CodecInfo(
    @SerialName("codec_name") val codecName: String = "",
    @SerialName("current_qn") val currentQn: Int = 0,
    @SerialName("accept_qn") val acceptQn: List<Int>? = null,
    @SerialName("base_url") val baseUrl: String = "",
    val url_info: List<UrlInfo>? = null
)

@Serializable
data class UrlInfo(
    val host: String = "",
    val extra: String = ""
)

@Serializable
data class LiveDurl(
    val url: String = "",
    val order: Int = 0
)

@Serializable
data class LiveQuality(
    val qn: Int = 0,
    val desc: String = ""
)

// --- 关注的直播 Response ---
@Serializable
data class FollowedLiveResponse(
    val code: Int = 0,
    val message: String = "",
    val data: FollowedLiveData? = null
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class FollowedLiveData(
    val list: List<FollowedLiveRoom>? = null,
    @JsonNames("living_num", "live_count") val livingNum: Int? = null,
    @JsonNames("not_living_num", "count") val notLivingNum: Int = 0,
    @JsonNames("page_info") val pageinfo: PageInfo? = null,
    val pageSize: Int = 0,
    val totalPage: Int = 0,
)

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PageInfo(
    val page: Int = 0,
    @JsonNames("pageSize") val page_size: Int = 0,
    @JsonNames("totalPage") val total_page: Int = 0
)

@Serializable
data class RoomInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RoomInfoData? = null
)

@Serializable
data class RoomInfoData(
    val room_id: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val online: Int = 0,
    val attention: Int = 0,
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("area_name") val areaName: String = ""
)

@Serializable
data class WatchedShow(
    val switch: Boolean = false,
    val num: Int = 0,
    @SerialName("text_small") val textSmall: String = "",
    @SerialName("text_large") val textLarge: String = ""
) {
    fun viewerCount(): Int {
        return when {
            num > 0 -> num
            else -> parseLiveViewerCountText(textSmall)
                .takeIf { it > 0 }
                ?: parseLiveViewerCountText(textLarge)
        }
    }
}

private fun parseLiveViewerCountText(text: String): Int {
    val normalized = text.trim()
    if (normalized.isEmpty()) return 0
    val numberText = Regex("""\d+(?:\.\d+)?""").find(normalized)?.value ?: return 0
    val multiplier = when {
        normalized.contains("亿") -> 100_000_000f
        normalized.contains("万") -> 10_000f
        else -> 1f
    }
    return (numberText.toFloatOrNull() ?: 0f)
        .times(multiplier)
        .toInt()
}

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class FollowedLiveRoom(
    val roomid: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val uname: String = "",
    val face: String = "",
    val cover: String = "",
    @SerialName("room_cover") val roomCover: String = "",
    @SerialName("user_cover") val userCover: String = "",
    @SerialName("system_cover") val systemCover: String = "",
    val online: Int = 0,
    val popularity: Int = 0,
    val attention: Long = 0,
    @SerialName("text_small") val textSmall: String = "",
    @SerialName("text_large") val textLarge: String = "",
    @SerialName("watched_show") val watchedShow: WatchedShow? = null,
    @JsonNames("area_name", "area_name_v2")
    @SerialName("area_name") val areaName: String = "",
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("live_time") val liveTime: Long = 0
) {
    fun toLiveRoom(): LiveRoom {
        val validCover = listOf(cover, roomCover, userCover, systemCover, face)
            .firstOrNull { it.isNotEmpty() } ?: ""
        val watchedCount = watchedShow?.viewerCount() ?: 0
        val topLevelTextCount = parseLiveViewerCountText(textSmall)
            .takeIf { it > 0 }
            ?: parseLiveViewerCountText(textLarge)
        val validOnline = when {
            watchedCount > 0 -> watchedCount
            topLevelTextCount > 0 -> topLevelTextCount
            popularity > 0 -> popularity
            online > 0 -> online
            else -> online
        }
        return LiveRoom(
            roomid = roomid, uid = uid, title = title, uname = uname, face = face,
            cover = validCover, userCover = userCover.ifEmpty { validCover },
            online = validOnline, watchedShow = watchedShow, areaName = areaName, keyframe = validCover
        )
    }
}

// --- 直播分区列表 Response ---
@Serializable
data class LiveAreaListResponse(
    val code: Int = 0,
    val msg: String = "",
    val message: String = "",
    val data: List<LiveAreaParent>? = null
)

@Serializable
data class LiveAreaNavigationResponse(
    val code: Int,
    val message: String = "",
    val data: List<LiveAreaNavigationTab>? = null,
)

@Serializable
data class LiveAreaNavigationTab(
    @Serializable(with = FlexibleIntSerializer::class) val id: Int,
    @SerialName("parent_id") @Serializable(with = FlexibleIntSerializer::class) val parentId: Int = 0,
    @SerialName("parent_name") val parentName: String = "",
    val name: String = "",
    val pic: String = "",
)

@Serializable
data class LiveAreaParent(
    val id: Int = 0,
    val name: String = "",
    val list: List<LiveAreaChild>? = null
)

@Serializable
data class LiveAreaChild(
    val id: String = "",
    val parent_id: String = "",
    val old_area_id: String = "",
    val name: String = "",
    val act_id: String = "",
    val pk_status: String = "",
    val hot_status: Int = 0,
    val lock_status: String = "",
    val pic: String = "",
    val complex_areaid: Int = 0,
    val parent_name: String = "",
    val area_type: Int = 0
)

@Serializable
data class LiveFavoriteTagEntry(
    val parentAreaId: Int = 0,
    val areaId: Int = 0,
    val title: String = "",
    val coverUrl: String = "",
    val parentTitle: String = ""
)

@Serializable
data class LiveSecondAreaResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveSecondAreaData? = null
)

@Serializable
data class LiveSecondAreaData(
    val list: List<LiveRoom>? = null,
    @SerialName("has_more") val hasMore: Int = 0,
    val count: Int = 0,
    @SerialName("new_tags") val newTags: List<LiveSecondSortTag>? = null,
)

@Serializable
data class LiveContributionRankResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveContributionRankData? = null
)

@Serializable
data class LiveContributionRankData(
    val item: List<LiveContributionRankItem>? = null
)

@Serializable
data class LiveContributionRankItem(
    val uid: Long = 0,
    val name: String = "",
    val face: String = "",
    val rank: Int = 0,
    val score: Int = 0,
    @SerialName("medal_info") val medalInfo: LiveContributionMedalInfo? = null
)

@Serializable
data class LiveContributionMedalInfo(
    @SerialName("medal_name") val medalName: String = "",
    val level: Int = 0
)

// --- 直播间初始化/详情 Response ---
@Serializable
data class LiveRoomInitResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveRoomInitData? = null
)

@Serializable
data class LiveRoomInitData(
    @SerialName("room_id") val roomId: Long = 0,
    @SerialName("short_id") val shortId: Int = 0,
    val uid: Long = 0,
    @SerialName("need_p2p") val needP2p: Int = 0,
    @SerialName("is_hidden") val isHidden: Boolean = false,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("is_portrait") val isPortrait: Boolean = false,
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("hidden_till") val hiddenTill: Long = 0,
    @SerialName("lock_till") val lockTill: Long = 0,
    val encrypted: Boolean = false,
    @SerialName("pwd_verified") val pwdVerified: Boolean = false,
    @SerialName("live_time") val liveTime: Long = 0,
    @SerialName("is_sp") val isSp: Int = 0,
    @SerialName("special_type") val specialType: Int = 0
)

@Serializable
data class LiveRoomDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val data: LiveRoomDetailData? = null
)

@Serializable
data class LiveRoomDetailData(
    @SerialName("room_info") val roomInfo: LiveRoomInfo? = null,
    @SerialName("anchor_info") val anchorInfo: LiveAnchorInfo? = null,
    @SerialName("watched_show") val watchedShow: WatchedShow? = null
)

@Serializable
data class LiveRoomInfo(
    @SerialName("room_id") val roomId: Long = 0,
    @SerialName("short_id") val shortId: Int = 0,
    val uid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val tags: String = "",
    val background: String = "",
    val description: String = "",
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("live_start_time") val liveStartTime: Long = 0,
    @SerialName("live_screen_type") val liveScreenType: Int = 0,
    @SerialName("lock_status") val lockStatus: Int = 0,
    @SerialName("lock_time") val lockTime: Long = 0,
    @SerialName("hidden_status") val hiddenStatus: Int = 0,
    @SerialName("hidden_time") val hiddenTime: Long = 0,
    @SerialName("area_id") val areaId: Int = 0,
    @SerialName("area_name") val areaName: String = "",
    @SerialName("parent_area_id") val parentAreaId: Int = 0,
    @SerialName("parent_area_name") val parentAreaName: String = "",
    val keyframe: String = "",
    @SerialName("special_type") val specialType: Int = 0,
    @SerialName("up_session") val upSession: String = "",
    @SerialName("pk_status") val pkStatus: Int = 0,
    val online: Int = 0,
    @SerialName("live_id") val liveId: Long = 0
)

@Serializable
data class LiveAnchorInfo(
    @SerialName("base_info") val baseInfo: LiveAnchorBaseInfo? = null,
    @SerialName("relation_info") val relationInfo: LiveRelationInfo? = null,
    @SerialName("medal_info") val medalInfo: LiveMedalInfo? = null
)

@Serializable
data class LiveAnchorBaseInfo(
    val uname: String = "",
    val face: String = "",
    val gender: String = "",
    @SerialName("official_info") val officialInfo: LiveOfficialInfo? = null
)

@Serializable
data class LiveOfficialInfo(
    val role: Int = 0,
    val title: String = "",
    val desc: String = "",
    @SerialName("is_nft") val isNft: Int = 0,
    @SerialName("nft_dmark") val nftDmark: String = ""
)

@Serializable
data class LiveRelationInfo(
    val attention: Long = 0
)

@Serializable
data class LiveMedalInfo(
    @SerialName("medal_name") val medalName: String = "",
    @SerialName("medal_id") val medalId: Long = 0,
    @SerialName("fansclub") val fansclub: Long = 0
)
