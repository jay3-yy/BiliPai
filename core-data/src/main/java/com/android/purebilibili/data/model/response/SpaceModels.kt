@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

// =============== UP主空间 API 响应模型 ===============

// /x/space/wbi/acc/info 用户信息响应
@Serializable
data class SpaceInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceUserInfo? = null
)

@Serializable
data class SpaceTopImageItem(
    val header: String = "",
    val fullCover: String = "",
    val dy: Float = 0f,
    val title: SpaceCollectionTopTitle? = null
)

@Serializable
data class SpaceUserInfo(
    val mid: Long = 0,
    val name: String = "",
    val sex: String = "",
    val face: String = "",
    val sign: String = "",
    val level: Int = 0,
    val silence: Int = 0,
    @SerialName("fans_badge")
    val fansBadge: Boolean = false,
    val official: SpaceOfficial = SpaceOfficial(),
    val vip: SpaceVip = SpaceVip(),
    @SerialName("is_followed")
    val isFollowed: Boolean = false,
    @SerialName("relation_status")
    val relationStatus: Int = 0,
    @SerialName("top_photo")
    val topPhoto: String = "",
    @SerialName("night_top_photo")
    val nightTopPhoto: String = "",
    @SerialName("top_images")
    val topImages: List<SpaceTopImageItem> = emptyList(),
    @SerialName("followings_followed")
    val followingsFollowed: SpaceFollowingsFollowedUpper? = null,
    @SerialName("space_tags")
    val spaceTags: List<SpaceTagItem> = emptyList(),
    @SerialName("live_room")
    val liveRoom: SpaceLiveRoom? = null,
    @SerialName("live_place")
    val livePlace: String? = null,
    @SerialName("ip_location")
    val ipLocation: String? = null
)

@Serializable
data class SpaceOfficial(
    val role: Int = 0,
    val title: String = "",
    @SerialName("splice_title")
    val spliceTitle: String = "",
    val desc: String = "",
    val type: Int = -1  // -1无认证 0个人认证 1机构认证
)

@Serializable
data class SpaceVip(
    val type: Int = 0,  // 0无 1月度 2年度及以上
    val status: Int = 0,
    val label: SpaceVipLabel = SpaceVipLabel()
)

@Serializable
data class SpaceVipLabel(
    val text: String = ""
)

@Serializable
data class SpaceLiveRoom(
    val roomStatus: Int = 0,  // 0无房间 1有房间
    val liveStatus: Int = 0,  // 0未开播 1直播中
    val url: String = "",
    val title: String = "",
    val cover: String = "",
    @SerialName("roomid")
    val roomId: Long = 0
)

// /x/v2/space 聚合空间首屏响应
@Serializable
data class SpaceAggregateResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceAggregateData? = null
)

@Serializable
data class SpaceAggregateData(
    @SerialName("reservation_card_list")
    val reservations: List<SpaceReservationItem> = emptyList(),
    val medal: Int = 0,
    @SerialName("default_tab")
    val defaultTab: String = "",
    val relation: Int? = null,
    @SerialName("rel_special")
    val relSpecial: Int? = null,
    val card: SpaceAggregateCard? = null,
    val images: SpaceAggregateImages? = null,
    val live: SpaceLiveRoom? = null,
    val archive: SpaceAggregateArchive? = null,
    val article: SpaceAggregateArticleSection? = null,
    val audios: SpaceAggregateAudioSection? = null,
    @SerialName("coin_archive")
    val coinArchive: SpaceAggregateArchive? = null,
    @SerialName("like_archive")
    val likeArchive: SpaceAggregateArchive? = null,
    val season: SpaceAggregateArchive? = null,
    @SerialName("favourite2")
    val favourite2: SpaceAggregateFavoriteSection? = null,
    val comic: SpaceAggregateArchive? = null,
    @SerialName("ugc_season")
    val ugcSeason: SpaceAggregateArchive? = null,
    val tab2: List<SpaceAggregateTab> = emptyList()
)

@Serializable
data class SpaceFollowingsFollowedUpper(
    val items: List<SpaceFollowingsFollowedItem> = emptyList(),
    @SerialName("jump_url")
    val jumpUrl: String = ""
)

@Serializable
data class SpaceFollowingsFollowedItem(
    val mid: Long = 0L,
    val name: String = "",
    val face: String = ""
)

@Serializable
data class SpaceTagItem(
    val title: String = "",
    val uri: String = "",
    val type: String = "",
    @SerialName("text_color")
    val textColor: String = "",
    @SerialName("night_text_color")
    val nightTextColor: String = "",
    @SerialName("background_color")
    val backgroundColor: String = "",
    @SerialName("night_background_color")
    val nightBackgroundColor: String = "",
    val icon: String = ""
)

@Serializable
data class SpaceAggregateCard(
    val mid: String = "",
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    val sex: String = "",
    val attention: Int = 0,
    val fans: Int = 0,
    val silence: Int = 0,
    @SerialName("official_verify")
    val officialVerify: SpaceOfficial = SpaceOfficial(),
    val vip: SpaceVip = SpaceVip(),
    @SerialName("level_info")
    val levelInfo: SpaceAggregateLevelInfo = SpaceAggregateLevelInfo(),
    val likes: SpaceAggregateLikes = SpaceAggregateLikes(),
    val relation: SpaceAggregateRelation = SpaceAggregateRelation(),
    @SerialName("followings_followed_upper")
    val followingsFollowedUpper: SpaceFollowingsFollowedUpper? = null,
    @SerialName("space_tag")
    val spaceTag: List<SpaceTagItem> = emptyList(),
    @SerialName("ip_location")
    val ipLocation: String? = null
)

@Serializable
data class SpaceAggregateLevelInfo(
    @SerialName("current_level")
    val currentLevel: Int = 0
)

@Serializable
data class SpaceAggregateLikes(
    @SerialName("like_num")
    val likeNum: Long = 0
)

@Serializable
data class SpaceAggregateRelation(
    val status: Int = 0,
    @SerialName("is_follow")
    val isFollow: Int = 0,
    @SerialName("is_followed")
    val isFollowed: Int = 0
)

@Serializable
data class SpaceAggregateImages(
    @JsonNames("imgUrl", "img_url")
    val imgUrl: String = "",
    @JsonNames("night_imgurl", "night_img_url", "nightImgurl")
    val nightImgUrl: String = "",
    @SerialName("collection_top_simple")
    val collectionTopSimple: SpaceCollectionTopSimple? = null
)

@Serializable
data class SpaceCollectionTopSimple(
    val top: SpaceCollectionTop? = null
)

@Serializable
data class SpaceCollectionTop(
    @JsonNames("result", "imgUrls", "img_urls")
    val result: List<SpaceCollectionTopItem> = emptyList()
)

@Serializable
data class SpaceCollectionTopItem(
    val item: SpaceCollectionTopItemDetail? = null,
    val cover: String = "",
    val title: SpaceCollectionTopTitle? = null
)

@Serializable
data class SpaceCollectionTopItemDetail(
    val image: SpaceCollectionTopImage? = null,
    val animation: SpaceCollectionTopImage? = null
)

@Serializable
data class SpaceCollectionTopImage(
    @SerialName("default_image")
    val defaultImage: String = "",
    val location: String = "",
    val height: Double = 0.0
)

@Serializable
data class SpaceSubTitleColorFormat(
    val colors: List<String> = emptyList()
)

@Serializable
data class SpaceCollectionTopTitle(
    val title: String = "",
    @SerialName("sub_title")
    val subTitle: String = "",
    @SerialName("sub_title_color_format")
    val subTitleColorFormat: SpaceSubTitleColorFormat? = null
)

@Serializable
data class SpaceAggregateArchive(
    @SerialName("episodic_button")
    val episodicButton: SpaceAggregateEpisodicButton? = null,
    val count: Int = 0,
    val item: List<SpaceAggregateArchiveItem> = emptyList()
)

@Serializable
data class SpaceArchiveSectionResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceAggregateArchive? = null,
)

@Serializable
data class SpaceAggregateEpisodicButton(
    val text: String = "",
    val uri: String = ""
)

@Serializable
data class SpaceAggregateArchiveItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val author: String = "",
    val subtitle: String = "",
    @SerialName("cover_icon")
    val coverIcon: String = "",
    val uri: String = "",
    val param: String = "",
    val goto: String = "",
    val length: String = "",
    val duration: Int = 0,
    val play: Int = 0,
    val danmaku: Int = 0,
    val reply: Int = 0,
    val ctime: Long = 0,
    val tname: String = "",
    @SerialName("first_cid")
    val firstCid: Long = 0,
    @SerialName("is_pgc")
    val isPgc: Boolean = false,
    @SerialName("publish_time_text")
    val publishTimeText: String = "",
    val styles: String = "",
    val label: String = "",
    val badges: List<SpaceAggregateBadge> = emptyList(),
    @SerialName("is_cooperation")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isCooperation: Boolean = false,
    @SerialName("is_steins")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isSteins: Boolean = false,
    @SerialName("is_lesson_video")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isLessonVideo: Boolean = false
) {
    val statusLabels: List<String>
        get() = resolveVideoStatusLabels(
            cooperation = isCooperation,
            interactive = isSteins,
            course = isLessonVideo,
            serverLabels = listOf(label) + badges.map { it.text },
        )
}

@Serializable
data class SpaceAggregateArticleSection(
    val count: Int = 0,
    val item: List<SpaceArticleItem> = emptyList()
)

@Serializable
data class SpaceAggregateAudioSection(
    val count: Int = 0,
    val item: List<SpaceAudioItem> = emptyList()
)

@Serializable
data class SpaceAggregateFavoriteSection(
    val count: Int = 0,
    val item: List<SpaceAggregateFavoriteItem> = emptyList()
)

@Serializable
data class SpaceAggregateFavoriteItem(
    val id: Long = 0,
    val fid: Long = 0,
    val mid: Long = 0,
    val title: String = "",
    @JsonNames("pic", "cover_url", "coverUrl")
    val cover: String = "",
    val count: Int = 0,
    @SerialName("media_id")
    val mediaId: Long = 0,
    @SerialName("media_count")
    val media_count: Int = 0,
    @SerialName("is_public")
    val isPublic: Int = 0
)

@Serializable
data class SpaceAggregateBadge(
    val text: String = ""
)

@Serializable
data class SpaceAggregateTab(
    val title: String = "",
    val param: String = "",
    val items: List<SpaceAggregateTabItem> = emptyList()
)

@Serializable
data class SpaceAggregateTabItem(
    val filter: List<SpaceOpusFilter> = emptyList(),
    val title: String = "",
    val param: String = "",
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("series_id")
    val seriesId: Long = 0
)

@Serializable
data class SpaceOpusFilter(val text: String = "", val meta: String = "all", @SerialName("tab_name") val tabName: String = "")

// /x/space/wbi/arc/search UP主投稿视频列表
@Serializable
data class SpaceVideoResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceVideoData? = null
)

@Serializable
data class SpaceVideoData(
    val list: SpaceVideoList = SpaceVideoList(),
    val page: SpacePage = SpacePage()
)

@Serializable
data class SpaceVideoList(
    val vlist: List<SpaceVideoItem> = emptyList(),
    val tags: List<SpaceVideoTag> = emptyList()
)

@Serializable
data class SpaceVideoTag(
    val tid: Int = 0,
    val name: String = "",
    val count: Int? = null,
    @SerialName("special_type") val specialType: String? = null,
)

@Serializable
data class SpacePage(
    val pn: Int = 1,  // 当前页
    val ps: Int = 30, // 每页数量
    val count: Int = 0 // 总视频数
)

@Serializable
data class SpaceVideoItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val description: String = "",
    val play: Int = 0,
    val comment: Int = 0,
    val length: String = "",  // "10:24" 格式
    val created: Long = 0,    // 发布时间戳
    val author: String = "",
    val typeid: Int = 0,      //  分区 ID
    val typename: String = "", //  分区名称
    @SerialName("is_charging_arc")
    val isChargingArc: Boolean = false,
    @SerialName("elec_arc_type")
    val elecArcType: Int = 0,
    @SerialName("is_ugcpay")
    val isUgcpay: Boolean = false,
    @SerialName("ugc_pay")
    val ugcPay: Int = 0,
    @SerialName("ugc_pay_preview")
    val ugcPayPreview: Int = 0,
    @SerialName("is_union_video")
    @Serializable(with = FlexibleIntSerializer::class)
    val isUnionVideo: Int = 0,
    @SerialName("is_cooperation")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isCooperation: Boolean = false,
    @SerialName("is_steins")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isSteins: Boolean = false,
    @SerialName("is_lesson_video")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isLessonVideo: Boolean = false,
    val badges: List<SpaceAggregateBadge> = emptyList()
) {
    val statusLabels: List<String>
        get() = resolveVideoStatusLabels(
            cooperation = isCooperation || isUnionVideo == 1,
            interactive = isSteins,
            course = isLessonVideo,
            charging = isChargingArc || elecArcType == 1 || isUgcpay || ugcPay > 0 || ugcPayPreview > 0,
            serverLabels = badges.map { it.text },
        )
}

// /x/relation/stat 粉丝关注数
@Serializable
data class RelationStatResponse(
    val code: Int = 0,
    val message: String = "",
    val data: RelationStatData? = null
)

@Serializable
data class RelationStatData(
    val mid: Long = 0,
    val following: Int = 0,
    val follower: Int = 0
)

// /x/space/upstat UP主播放量获赞数
@Serializable
data class UpStatResponse(
    val code: Int = 0,
    val message: String = "",
    val data: UpStatData? = null
)

@Serializable
data class UpStatData(
    val archive: ArchiveStatInfo = ArchiveStatInfo(),
    val likes: Long = 0
)

@Serializable
data class ArchiveStatInfo(
    val view: Long = 0  // 总播放量
)

//  视频分类
data class SpaceVideoCategory(
    val tid: Int,       // 分类 ID
    val name: String,   // 分类名称
    val count: Int      // 该分类下的视频数量
)

//  视频排序方式
enum class VideoSortOrder(val apiValue: String, val displayName: String) {
    PUBDATE("pubdate", "最新发布"),
    OLDEST_PUBDATE("pubdate", "最早发布"),
    CLICK("click", "最多播放"),
    STOW("stow", "最多收藏")
}

// ==========  合集和系列 Models ==========

@kotlinx.serialization.Serializable
data class SeasonsSeriesListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SeasonsSeriesData? = null
)

@kotlinx.serialization.Serializable
data class SeasonsSeriesData(
    val items_lists: SeasonsSeriesItems? = null
)

@kotlinx.serialization.Serializable
data class SeasonsSeriesItems(
    val seasons_list: List<SeasonItem> = emptyList(),  // 合集列表
    val series_list: List<SeriesItem> = emptyList()    // 系列列表
)

@kotlinx.serialization.Serializable
data class SeasonItem(
    val meta: SeasonMeta = SeasonMeta(),
    val archives: List<SeasonArchiveItem> = emptyList(),
    val recent_aids: List<Long> = emptyList()
)

@kotlinx.serialization.Serializable
data class SeasonMeta(
    val season_id: Long = 0,
    val name: String = "",
    val cover: String = "",
    val total: Int = 0,
    val description: String = "",
    val mid: Long = 0
)

@kotlinx.serialization.Serializable
data class SeriesItem(
    val meta: SeriesMeta = SeriesMeta(),
    val archives: List<SeriesArchiveItem> = emptyList(),
    val recent_aids: List<Long> = emptyList()
)

@kotlinx.serialization.Serializable
data class SeriesMeta(
    val series_id: Long = 0,
    val name: String = "",
    val cover: String = "",
    val total: Int = 0,
    val description: String = "",
    val creator: String = "",
    val ctime: Long = 0,
    val mtime: Long = 0,
    val mid: Long = 0
)

// 合集/系列内视频列表响应
@kotlinx.serialization.Serializable
data class SeasonArchivesResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SeasonArchivesData? = null
)

@kotlinx.serialization.Serializable
data class SeasonArchivesData(
    val aids: List<Long> = emptyList(),
    val archives: List<SeasonArchiveItem> = emptyList(),
    val meta: SeasonMeta = SeasonMeta(),
    val page: SeasonPage = SeasonPage()
)

@kotlinx.serialization.Serializable
data class SeasonArchiveItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val author: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val stat: SeasonArchiveStat = SeasonArchiveStat()
)

@kotlinx.serialization.Serializable
data class SeasonArchiveStat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0
)

@kotlinx.serialization.Serializable
data class SeasonPage(
    val page_num: Int = 1,
    val page_size: Int = 30,
    val total: Int = 0
)

//  系列视频列表响应
@kotlinx.serialization.Serializable
data class SeriesArchivesResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SeriesArchivesData? = null
)

@kotlinx.serialization.Serializable
data class SeriesArchivesData(
    val aids: List<Long> = emptyList(),
    val archives: List<SeriesArchiveItem> = emptyList(),
    val page: SeriesPage = SeriesPage()
)

@kotlinx.serialization.Serializable
data class SeriesArchiveItem(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val author: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val stat: SeriesArchiveStat = SeriesArchiveStat()
)

@kotlinx.serialization.Serializable
data class SeriesArchiveStat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0
)

@kotlinx.serialization.Serializable
data class SeriesPage(
    val num: Int = 1,
    val size: Int = 30,
    val total: Int = 0
)

// ==========  主页 Tab Models ==========

// 置顶视频响应 /x/space/top/arc
@kotlinx.serialization.Serializable
data class SpaceTopArcResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceTopArcData? = null
)

@kotlinx.serialization.Serializable
data class SpaceTopArcData(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val stat: SpaceTopArcStat = SpaceTopArcStat(),
    val reason: String = ""  // 置顶理由
)

@kotlinx.serialization.Serializable
data class SpaceTopArcStat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
    val favorite: Long = 0,
    val coin: Long = 0,
    val share: Long = 0,
    val like: Long = 0
)

// 个人公告响应 /x/space/notice
@kotlinx.serialization.Serializable
data class SpaceNoticeResponse(
    val code: Int = 0,
    val message: String = "",
    val data: String = ""  // 公告内容（纯文本或 HTML）
)

// ==========  动态 Tab Models ==========

// 用户动态响应 /x/polymer/web-dynamic/v1/feed/space
@kotlinx.serialization.Serializable
data class SpaceDynamicResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceDynamicData? = null
)

@kotlinx.serialization.Serializable
data class SpaceDynamicData(
    val has_more: Boolean = false,
    val offset: String = "",
    val items: List<SpaceDynamicItem> = emptyList()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicItem(
    val basic: DynamicBasic? = null,
    val id_str: String = "",
    val modules: SpaceDynamicModules = SpaceDynamicModules(),
    val orig: SpaceDynamicItem? = null,
    val type: String = "",  // DYNAMIC_TYPE_AV, DYNAMIC_TYPE_DRAW, DYNAMIC_TYPE_WORD 等
    val visible: Boolean = true
)

@kotlinx.serialization.Serializable
data class SpaceDynamicModules(
    val module_author: SpaceDynamicAuthor? = null,
    val module_dynamic: SpaceDynamicContent? = null,
    val module_more: DynamicMoreModule? = null,
    val module_stat: SpaceDynamicStat? = null,
    val module_dispute: DynamicDisputeModule? = null,
    val module_interaction: DynamicInteractionModule? = null,
)

@kotlinx.serialization.Serializable
data class SpaceDynamicAuthor(
    @Serializable(with = FlexibleLongSerializer::class)
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
    val pub_time: String = "",
    val pub_ts: Long = 0,
    val pub_location_text: String = "",
    val official_verify: DynamicOfficialVerify? = null,
    val vip: DynamicVipInfo? = null
)

@kotlinx.serialization.Serializable
data class SpaceDynamicContent(
    val desc: SpaceDynamicDesc? = null,
    val major: SpaceDynamicMajor? = null,
    val topic: DynamicTopic? = null,
    val additional: DynamicAdditional? = null,
)

@kotlinx.serialization.Serializable
data class SpaceDynamicDesc(
    val text: String = "",
    val rich_text_nodes: List<SpaceDynamicRichText> = emptyList()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicRichText(
    val type: String = "",  // RICH_TEXT_NODE_TYPE_TEXT, RICH_TEXT_NODE_TYPE_EMOJI 等
    val text: String = "",
    val orig_text: String = "",
    val emoji: SpaceDynamicEmoji? = null,
    val jump_url: String? = null,
    /** AT 节点对应用户 mid；API 偶发 number，用 flexible string 避免整段 desc 解析失败 */
    @Serializable(with = FlexibleStringSerializer::class)
    val rid: String? = null
)

@kotlinx.serialization.Serializable
data class SpaceDynamicEmoji(
    @JsonNames("url")
    val icon_url: String = "",
    val webp_url: String = "",
    val gif_url: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val size: Int = 1,
    val text: String = ""
)

@kotlinx.serialization.Serializable
data class SpaceDynamicMajor(
    val type: String = "",  // MAJOR_TYPE_ARCHIVE, MAJOR_TYPE_DRAW, MAJOR_TYPE_OPUS 等
    val archive: SpaceDynamicArchive? = null,
    val draw: SpaceDynamicDraw? = null,
    val opus: SpaceDynamicOpus? = null,
    val article: SpaceDynamicArticle? = null
)

@kotlinx.serialization.Serializable
data class SpaceDynamicArchive(
    @Serializable(with = FlexibleStringSerializer::class)
    val aid: String = "",
    val bvid: String = "",
    val title: String = "",
    val cover: String = "",
    val desc: String = "",
    val duration_text: String = "",
    val stat: SpaceDynamicArchiveStat = SpaceDynamicArchiveStat(),
    val badge: DynamicMajorBadge? = null,
    @SerialName("is_charging_arc")
    val isChargingArc: Boolean = false,
    @SerialName("elec_arc_type")
    val elecArcType: Int = 0,
    @SerialName("is_ugcpay")
    val isUgcpay: Boolean = false,
    @SerialName("ugc_pay")
    val ugcPay: Int = 0,
    @SerialName("ugc_pay_preview")
    val ugcPayPreview: Int = 0
)

@kotlinx.serialization.Serializable
data class SpaceDynamicArchiveStat(
    val play: String = "",
    val danmaku: String = ""
)

@kotlinx.serialization.Serializable
data class SpaceDynamicDraw(
    val id: Long = 0,
    val items: List<SpaceDynamicDrawItem> = emptyList()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicDrawItem(
    @JsonNames("src", "url")
    val src: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @JsonNames("live_url", "liveUrl")
    val live_url: String? = null
)

@kotlinx.serialization.Serializable
data class SpaceDynamicOpus(
    val summary: SpaceDynamicOpusSummary? = null,
    val pics: List<SpaceDynamicDrawItem> = emptyList(),
    val title: String = ""
)

@kotlinx.serialization.Serializable
data class SpaceDynamicOpusSummary(
    val text: String = "",
    val rich_text_nodes: List<SpaceDynamicRichText> = emptyList()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicArticle(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val title: String = "",
    val desc: String = "",
    val covers: List<String> = emptyList(),
    val jump_url: String = "",
    val label: String = ""
)

@kotlinx.serialization.Serializable
data class SpaceDynamicStat(
    val comment: SpaceDynamicCount = SpaceDynamicCount(),
    val forward: SpaceDynamicCount = SpaceDynamicCount(),
    val like: SpaceDynamicCount = SpaceDynamicCount()
)

@kotlinx.serialization.Serializable
data class SpaceDynamicCount(
    @Serializable(with = FlexibleIntSerializer::class)
    val count: Int = 0,
    val forbidden: Boolean = false,
    val hidden: Boolean = false,
    val status: Boolean = false
)

// ==========  Space Audio Models ==========

@Serializable
data class SpaceAudioResponse(
    val code: Int = 0,
    val msg: String = "",
    val data: SpaceAudioData? = null
)

@Serializable
data class SpaceAudioData(
    val curPage: Int = 1,
    val pageCount: Int = 0,
    val totalSize: Int = 0,
    val pageSize: Int = 30,
    val data: List<SpaceAudioItem>? = null
)

@Serializable
data class SpaceAudioItem(
    val id: Long = 0,
    val uid: Long = 0,
    val uname: String = "",
    val author: String = "",
    val title: String = "",
    val cover: String = "",
    val intro: String = "",
    val lyric: String = "",
    val crtype: Int = 0,
    val duration: Int = 0,
    val passtime: Long = 0,
    val curtime: Long = 0,
    val aid: Long = 0,
    val bvid: String = "",
    val ctime: Long = 0,
    val coin_num: Int = 0,
    val play_count: Int = 0,
    val reply_count: Int = 0,
    val share_count: Int = 0,
    val collect_count: Int = 0,
    // 真实播放/收藏/评论数在嵌套 statistic 里（顶层 play_count 恒为 0）
    val statistic: SpaceAudioStatistic? = null
)

@Serializable
data class SpaceAudioStatistic(
    val sid: Long = 0,
    val play: Long = 0,
    val collect: Long = 0,
    val comment: Long = 0,
    val share: Long = 0
)

// ==========  Space Article Models ==========

@Serializable
data class SpaceArticleResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceArticleData? = null
)

@Serializable
data class SpaceArticleData(
    @JsonNames("articles", "items")
    val lists: List<SpaceArticleItem> = emptyList(),
    val pn: Int = 1,
    val ps: Int = 30,
    @JsonNames("count")
    val total: Int = 0,
    val has_more: Boolean = false,
    val offset: String = ""
)

@Serializable
data class SpaceArticleItem(
    @JsonNames("opus_id")
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    val category: SpaceArticleCategory? = null,
    @JsonNames("content")
    val title: String = "",
    val summary: String = "",
    val banner_url: String = "",
    val cover: SpaceArticleCover? = null,
    val jump_url: String = "",
    val template_id: Int = 0,
    val state: Int = 0,
    val author: SpaceArticleAuthor? = null,
    @JsonNames("stat")
    val stats: SpaceArticleStats? = null,
    val publish_time: Long = 0,
    val ctime: Long = 0,
    val mtime: Long = 0,
    val is_like: Boolean = false,
    val image_urls: List<String> = emptyList(),
    val darkCover: SpaceArticleCover? = null,
    val displayStats: List<String>? = null,
)

fun SpaceArticleItem.displayImageUrls(darkTheme: Boolean = false): List<String> {
    if (darkTheme && darkCover != null) return listOf(darkCover.url)
    return image_urls.ifEmpty {
        listOfNotNull(
            banner_url.takeIf { it.isNotBlank() },
            cover?.url?.takeIf { it.isNotBlank() }
        )
    }
}

@Serializable
data class SpaceArticleCover(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
data class SpaceArticleCategory(
    val id: Int = 0,
    val parent_id: Int = 0,
    val name: String = ""
)

@Serializable
data class SpaceArticleAuthor(
    val mid: Long = 0,
    val name: String = "",
    val face: String = ""
)

@Serializable
data class SpaceArticleStats(
    @Serializable(with = FlexibleIntSerializer::class)
    val view: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val favorite: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val like: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val dislike: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val reply: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val share: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val coin: Int = 0,
    val dynamic: Int = 0
)

// ==================== UP主空间课堂 (Cheese / PUGV) ====================

@Serializable
data class SpaceCheeseResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceCheeseData? = null
)

@Serializable
data class SpaceCheeseData(
    val items: List<SpaceCheeseItem> = emptyList(),
    val page: SpaceCheesePage? = null
)

@Serializable
data class SpaceCheesePage(
    val next: Boolean = false
)

@Serializable
data class SpaceCheeseItem(
    val cover: String = "",
    val marks: List<String> = emptyList(),
    @SerialName("season_id")
    val seasonId: Long = 0L,
    val status: String = "",
    val title: String = "",
    val ctime: String = ""
)

// App 端 /x/v2/space 的充电（elec）与大航海（guard）摘要，仅解析头部展示所需字段
@Serializable
data class SpaceSupportersResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceSupportersData? = null
)

@Serializable
data class SpaceSupportersData(
    val elec: SpaceElecBlock? = null,
    val guard: SpaceGuardBlock? = null
)

@Serializable
data class SpaceElecBlock(
    val total: Long = 0L,
    val list: List<SpaceSupporterUser> = emptyList()
)

@Serializable
data class SpaceGuardBlock(
    val uri: String = "",
    val desc: String = "",
    val item: List<SpaceSupporterUser> = emptyList()
)

@Serializable
data class SpaceSupporterUser(
    val mid: Long = 0L,
    val uname: String = "",
    val avatar: String = "",
    val face: String = ""
)

// 充电排行 /x/upower/up/member/rank/v2
@Serializable
data class SpaceUpowerRankResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceUpowerRankData? = null
)

@Serializable
data class SpaceUpowerRankData(
    @SerialName("rank_info") val rankInfo: List<SpaceUpowerRankItem> = emptyList(),
    @SerialName("privilege_type") val privilegeType: Int = 0,
    val tabs: List<Int> = emptyList(),
    @SerialName("level_info") val levelInfo: List<SpaceUpowerLevelInfo> = emptyList()
)

@Serializable
data class SpaceUpowerRankItem(
    val mid: Long = 0L,
    val nickname: String = "",
    val avatar: String = "",
    val day: Int = 0
)

@Serializable
data class SpaceUpowerLevelInfo(
    @SerialName("privilege_type") val privilegeType: Int = 0,
    val name: String = "",
    @SerialName("member_total") val memberTotal: Int = 0
)

// 大航海 /xlive/app-ucenter/v1/guard/MainGuardCardAll
@Serializable
data class SpaceMemberGuardResponse(
    val code: Int = 0,
    val message: String = "",
    val data: SpaceMemberGuardData? = null
)

@Serializable
data class SpaceMemberGuardData(
    @SerialName("guard_top_list") val guardTopList: List<SpaceGuardMemberItem> = emptyList(),
    @SerialName("has_more") val hasMore: Int = 0
)

@Serializable
data class SpaceGuardMemberItem(
    val uid: Long = 0L,
    val username: String = "",
    val face: String = "",
    @SerialName("guard_level") val guardLevel: Int = 0
)
