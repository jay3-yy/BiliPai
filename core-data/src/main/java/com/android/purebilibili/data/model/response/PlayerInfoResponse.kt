package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 播放器信息 API 响应
 * 
 * API: GET https://api.bilibili.com/x/player/v2
 * 参数: bvid, cid (或 aid, cid)
 * 
 * 主要用途:
 * - 获取视频章节信息 (view_points)
 * - 获取字幕信息 (subtitle)
 * - 获取在线人数 (online_count)
 */
@Serializable
data class PlayerInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlayerInfoData? = null
)

@Serializable
data class PlayerInfoData(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    @SerialName("last_play_cid")
    val lastPlayCid: Long? = null,
    @SerialName("last_play_time")
    val lastPlayTime: Long? = null,
    @SerialName("view_points")
    val viewPoints: List<ViewPoint> = emptyList(),
    @SerialName("online_count")
    val onlineCount: Int = 0,
    val subtitle: SubtitleInfo? = null,
    @SerialName("dm_mask")
    val dmMask: DanmakuMaskInfo? = null,
    val interaction: PlayerInteractionInfo? = null,
    @SerialName("bgm_info")
    val bgmInfo: BgmInfo? = null
) {
    /** 上次观看属于其他分 P 或字段缺失时，当前分 P 的进度仍未知。 */
    fun resumePositionMs(cid: Long): Long? =
        lastPlayTime?.takeIf { lastPlayCid == cid }?.coerceAtLeast(0L)
}

@Serializable
data class DanmakuMaskInfo(
    val cid: Long = 0L,
    val plat: Int = 0,
    val fps: Int = 0,
    val time: Long = 0L,
    @SerialName("mask_url")
    val maskUrl: String = ""
)

@Serializable
data class PlayerInteractionInfo(
    @SerialName("graph_version")
    val graphVersion: Long = 0,
    val msg: String = "",
    @SerialName("error_toast")
    val errorToast: String = "",
    val mark: Int = 0,
    @SerialName("need_reload")
    val needReload: Int = 0
)

@Serializable
data class BgmInfo(
    @SerialName("music_id")
    val musicId: String = "",
    @SerialName("music_title")
    val musicTitle: String = "",
    @SerialName("jump_url")
    val jumpUrl: String = "",
    val actor: String = "",
    @SerialName("cover_url")
    val coverUrl: String = ""
)

@Serializable
data class BgmMultipleMusicResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BgmMultipleMusicData? = null
)

@Serializable
data class BgmMultipleMusicData(
    val list: List<BgmInfo> = emptyList()
)

@Serializable
data class BgmDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BgmDetailData? = null
)

@Serializable
data class BgmDetailData(
    @SerialName("origin_artist_list") val originArtistList: String = "",
    val album: String = "",
    @SerialName("music_source") val musicSource: String = "",
    @SerialName("music_publish") val musicPublish: String = "",
    @SerialName("mv_aid") val mvAid: Long = 0,
    @SerialName("mv_bvid") val mvBvid: String = "",
    @SerialName("mv_cid") val mvCid: Long = 0,
    @SerialName("wish_listen") val wishListen: Boolean = false,
    @SerialName("artists_list") val artistsList: List<BgmArtist> = emptyList(),
    val achievement: List<String> = emptyList(),
    @SerialName("music_rank") val musicRank: String = "",
    @SerialName("recreation_rank") val recreationRank: String = "",
    @SerialName("hot_song_heat") val hotSongHeat: BgmHotSongHeat? = null,
    @SerialName("music_title")
    val musicTitle: String = "",
    @SerialName("origin_artist")
    val originArtist: String = "",
    @SerialName("mv_cover")
    val mvCover: String = "",
    @SerialName("wish_count")
    val wishCount: Int = 0,
    @SerialName("music_shares")
    val musicShares: Int = 0,
    @SerialName("listen_pv")
    val listenPv: Long = 0,
    @SerialName("music_hot")
    val musicHot: Long = 0,
    @SerialName("music_relation")
    val musicRelation: Int = 0,
    @SerialName("music_comment")
    val musicComment: BgmCommentInfo? = null,
    @SerialName("flow_attr")
    val flowAttr: BgmFlowAttr? = null
)

@Serializable
data class BgmArtist(val mid: Long = 0, val name: String = "", val face: String = "", val identity: String = "演唱者")

@Serializable
data class BgmHotSongHeat(
    @SerialName("last_heat") val lastHeat: Long = 0,
    @SerialName("song_heat") val songHeat: List<BgmSongHeat> = emptyList()
)

@Serializable
data class BgmSongHeat(val date: Long = 0, val heat: Long = 0)

@Serializable
data class BgmVideoLabel(val name: String = "")

@Serializable
data class BgmCommentInfo(
    val state: Int = 0,
    val nums: Int = 0,
    val oid: Long = 0,
    @SerialName("page_type")
    val pageType: Int = 0
)

@Serializable
data class BgmFlowAttr(
    @SerialName("no_share")
    val noShare: Boolean = false,
    @SerialName("no_comment")
    val noComment: Boolean = false
)

@Serializable
data class BgmRecommendListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BgmRecommendListData? = null
)

@Serializable
data class BgmRecommendListData(
    val list: List<BgmRecommendVideo> = emptyList()
)

@Serializable
data class BgmRecommendVideo(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val cover: String = "",
    val title: String = "",
    val mid: Long = 0,
    @SerialName("up_nick_name")
    val upNickName: String = "",
    val play: Int = 0,
    val danmu: Int = 0,
    val duration: Int = 0,
    val label: String = "",
    @SerialName("label_list") val labelList: List<BgmVideoLabel> = emptyList()
)

/**
 * 视频章节/看点信息
 *
 * 用于在进度条上显示章节标记
 */
@Serializable
data class ViewPoint(
    val content: String = "",      // 章节名称
    val from: Int = 0,             // 开始秒数
    val to: Int = 0,               // 结束秒数
    val imgUrl: String = "",       // 章节缩略图 URL
    val logoUrl: String = "",
    val type: Int = 0
) {
    /** 章节开始时间（毫秒） */
    val fromMs: Long get() = from * 1000L
    
    /** 章节结束时间（毫秒） */
    val toMs: Long get() = to * 1000L
    
    /** 章节时长（秒） */
    val duration: Int get() = to - from
}

/**
 * 字幕信息
 */
@Serializable
data class SubtitleInfo(
    @SerialName("allow_submit")
    val allowSubmit: Boolean = false,
    val lan: String = "",
    @SerialName("lan_doc")
    val lanDoc: String = "",
    val subtitles: List<SubtitleItem> = emptyList()
)

@Serializable
data class SubtitleItem(
    val id: Long = 0,
    @SerialName("id_str")
    val idStr: String = "",
    val lan: String = "",
    @SerialName("lan_doc")
    val lanDoc: String = "",
    @SerialName("subtitle_url")
    val subtitleUrl: String = "",
    @SerialName("ai_status")
    val aiStatus: Int = 0,
    @SerialName("ai_type")
    val aiType: Int = 0,
    @SerialName("is_lock")
    val isLock: Boolean = false,
    val type: Int = 0
)
