// 文件路径: data/model/response/BangumiModels.kt
package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.android.purebilibili.core.util.IdUtils


// ========== 番剧/影视响应模型 ==========

/**
 * 番剧时间表响应
 */
@Serializable
data class BangumiTimelineResponse(
    val code: Int = 0,
    val message: String = "",
    val result: List<TimelineDay>? = null
)

@Serializable
data class TimelineDay(
    val date: String = "",           // 日期 "2024-12-14"
    @SerialName("date_ts")
    val dateTs: Long = 0,            // 时间戳
    @SerialName("day_of_week")
    val dayOfWeek: Int = 0,          // 星期几 (1-7)
    @SerialName("is_today")
    val isToday: Int = 0,            // 是否是今天
    val episodes: List<TimelineEpisode>? = null
)

@Serializable
data class TimelineEpisode(
    @SerialName("episode_id")
    val episodeId: Long = 0,
    @SerialName("season_id")
    val seasonId: Long = 0,
    val title: String = "",           // 番剧标题
    val cover: String = "",           // 封面
    @SerialName("square_cover")
    val squareCover: String = "",     // 方形封面
    @SerialName("pub_index")
    val pubIndex: String = "",        // 更新集数 "第12话"
    @SerialName("pub_time")
    val pubTime: String = "",         // 发布时间 "22:00"
    @SerialName("pub_ts")
    val pubTs: Long = 0,              // 发布时间戳
    val delay: Int = 0,               // 是否延迟
    @SerialName("delay_id")
    val delayId: Long = 0,
    @SerialName("delay_index")
    val delayIndex: String = "",
    @SerialName("delay_reason")
    val delayReason: String = "",     // 延迟原因
    @SerialName("ep_cover")
    val episodeCover: String = "",
    val published: Int? = null,
    val follows: String = "",
    val plays: String = "",
    val follow: Int = 0               // 是否追番
)

/**
 * 番剧索引/筛选响应
 */
@Serializable
data class BangumiIndexResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BangumiIndexData? = null
)

@Serializable
data class BangumiIndexData(
    @SerialName("has_next")
    val hasNext: Int = 0,
    val list: List<BangumiItem>? = null,
    val num: Int = 0,                  // 当前页数量
    val size: Int = 0,                 // 每页数量
    val total: Int = 0                 // 总数
)

@Serializable
data class BangumiIndexConditionResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BangumiIndexConditionData? = null,
)

@Serializable
data class BangumiIndexConditionData(
    val filter: List<BangumiIndexConditionFilter>? = null,
    val order: List<BangumiIndexConditionOrder>? = null,
)

@Serializable
data class BangumiIndexConditionFilter(
    val field: String? = null,
    val name: String? = null,
    val values: List<BangumiIndexConditionValue>? = null,
)

@Serializable
data class BangumiIndexConditionOrder(
    val field: String? = null,
    val name: String? = null,
    val sort: String? = null,
)

@Serializable
data class BangumiIndexConditionValue(
    val keyword: String? = null,
    val name: String? = null,
)

@Serializable
data class BangumiItem(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("media_id")
    val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val badge: String = "",           // 角标 "会员专享" "独家"
    @SerialName("badge_type")
    val badgeType: Int = 0,
    val score: String = "",           // 评分 "9.8"
    @SerialName("new_ep")
    val newEp: NewEpInfo? = null,
    val order: String = "",           // 播放量/追番数
    @SerialName("order_type")
    val orderType: String = "",       // "追番人数" "播放数"
    @SerialName("season_type")
    val seasonType: Int = 0,          // 1=番剧 2=电影 3=纪录片 4=国创 5=电视剧
    @SerialName("season_type_name")
    val seasonTypeName: String = "",
    @SerialName("subTitle")
    val subtitle: String = "",        // 副标题
    val styles: String = "",          // 风格标签
    @SerialName("index_show")
    val indexShow: String = "",
    @SerialName("is_finish")
    val isFinish: Int = 0,
    @SerialName("first_ep")
    val firstEp: BangumiFirstEpisode? = null,
    @SerialName("season_status")
    val seasonStatus: Int = 0
)

@Serializable
data class NewEpInfo(
    val cover: String = "",
    val id: Long = 0,
    @SerialName("index_show")
    val indexShow: String = ""        // "全13话" "更新至第12话"
)

@Serializable
data class BangumiFirstEpisode(
    val cover: String = "",
    @SerialName("ep_id")
    val epId: Long = 0
)

/**
 * 番剧详情响应
 */
@Serializable
data class BangumiDetailResponse(
    val code: Int = 0,
    val message: String = "",
    val result: BangumiDetail? = null
)

/**
 * 通过 media_id 查询剧集基础信息。
 * 对应 /pgc/review/user，主要用于 md 链接解析和仅持有 media_id 的入口。
 */
@Serializable
data class BangumiMediaInfoResponse(
    val code: Int = 0,
    val message: String = "",
    val result: BangumiMediaInfoResult? = null
)

@Serializable
data class BangumiMediaInfoResult(
    val media: BangumiMediaInfo? = null,
    val review: BangumiMediaReviewStatus? = null
)

@Serializable
data class BangumiMediaInfo(
    val areas: List<AreaInfo>? = null,
    val cover: String = "",
    @SerialName("horizontal_picture")
    val horizontalPicture: String = "",
    @SerialName("media_id")
    val mediaId: Long = 0,
    @SerialName("new_ep")
    val newEpisode: BangumiMediaNewEpisode? = null,
    val rating: BangumiRating? = null,
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("share_url")
    val shareUrl: String = "",
    val title: String = "",
    val type: Int = 0,
    @SerialName("type_name")
    val typeName: String = ""
)

@Serializable
data class BangumiMediaNewEpisode(
    val id: Long = 0,
    val index: String = "",
    @SerialName("index_show")
    val indexShow: String = ""
)

@Serializable
data class BangumiMediaReviewStatus(
    @SerialName("is_coin")
    val isCoin: Int = 0,
    @SerialName("is_open")
    val isOpen: Int = 0
)

/** 独立分集接口响应，用于详情响应未携带完整分区时补全。 */
@Serializable
data class BangumiSectionResponse(
    val code: Int = 0,
    val message: String = "",
    val result: BangumiSectionResult? = null
)

@Serializable
data class BangumiSectionResult(
    @SerialName("main_section")
    val mainSection: BangumiSection? = null,
    val section: List<BangumiSection>? = null
)

@Serializable
data class BangumiDetail(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("media_id")
    val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("square_cover")
    val squareCover: String = "",
    val evaluate: String = "",        // 简介
    val rating: BangumiRating? = null,
    val stat: BangumiStat? = null,
    @SerialName("new_ep")
    val newEp: NewEpDetail? = null,
    val episodes: List<BangumiEpisode>? = null,
    val seasons: List<SeasonInfo>? = null,      // 关联季度
    val areas: List<AreaInfo>? = null,          // 地区
    val styles: List<String>? = null,           //  [修复] 风格是字符串数组，不是对象数组
    val actors: String = "",                     // 演员/声优
    val staff: String = "",                      // 制作人员
    @SerialName("season_type")
    val seasonType: Int = 0,
    @SerialName("season_type_name")
    val seasonTypeName: String = "",
    val total: Int = 0,                          // 总集数
    val mode: Int = 0,                           // 2=电影 3=番剧
    val rights: BangumiRights? = null,
    @SerialName("user_status")
    val userStatus: UserStatus? = null,
    val publish: BangumiPublish? = null,
    val payment: BangumiPayment? = null,
    val positive: BangumiPositive? = null,
    val section: List<BangumiSection>? = null,
    @SerialName("season_title")
    val seasonTitle: String = "",
    val subtitle: String = "",
    @SerialName("up_info")
    val upInfo: PugvUpInfo? = null,
    val briefImgs: List<PugvBriefImg>? = null,
    /** PUGV may expose several instructors/cooperators instead of one up_info. */
    val cooperators: List<PugvCooperator> = emptyList(),
    /** True when the current account has paid for this course. */
    val hasPaid: Boolean = false
)

@Serializable
data class BangumiRating(
    val score: Float = 0f,
    val count: Int = 0
)

@Serializable
data class BangumiStat(
    val views: Long = 0,              // 播放量
    val danmakus: Long = 0,           // 弹幕数
    val favorites: Long = 0,          // 追番/追剧数
    val favorite: Long? = null,       // 收藏量，与追番数分别展示
    val coins: Long = 0,
    val likes: Long = 0,
    val reply: Long = 0,              // 评论数
    val share: Long = 0
)

@Serializable
data class NewEpDetail(
    val id: Long = 0,
    val title: String = "",
    val desc: String = "",            // "全13话"
    @SerialName("is_new")
    val isNew: Int = 0
)

@Serializable
data class BangumiEpisode(
    val id: Long = 0,                 // ep_id
    val aid: Long = 0,                // 对应的视频 aid
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",           // 集标题 "第1话 开始"
    @SerialName("long_title")
    val longTitle: String = "",       // 长标题
    val cover: String = "",
    val duration: Long = 0,           // 时长（毫秒）
    val badge: String = "",           // "会员" "预告"
    @SerialName("badge_type")
    val badgeType: Int = 0,
    val status: Int = 0,              // 状态
    /** PUGV access flags are kept on the episode so the UI does not infer access from text. */
    val playable: Boolean = false,
    @SerialName("episode_can_view")
    val episodeCanView: Boolean = false,
    val playCount: Long = 0L,
    val from: String = "",
    @SerialName("pub_time")
    val pubTime: Long = 0,
    val skip: EpisodeSkip? = null     // 跳过片头片尾信息
)

@Serializable
data class EpisodeSkip(
    val op: SkipRange? = null,        // 片头
    val ed: SkipRange? = null         // 片尾
)

@Serializable
data class SkipRange(
    val start: Int = 0,
    val end: Int = 0
)

@Serializable
data class SeasonInfo(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("season_title")
    val seasonTitle: String = "",     // "第一季" "第二季"
    val title: String = "",
    val cover: String = "",
    val badge: String = "",
    @SerialName("is_new")
    val isNew: Int = 0
)

@Serializable
data class AreaInfo(
    val id: Int = 0,
    val name: String = ""             // "日本" "中国大陆"
)

@Serializable
data class StyleInfo(
    val id: Int = 0,
    val name: String = ""             // "热血" "恋爱"
)

@Serializable
data class BangumiRights(
    @SerialName("allow_download")
    val allowDownload: Int? = null,
    @SerialName("allow_review")
    val allowReview: Int? = null,
    @SerialName("is_preview")
    val isPreview: Int = 0,           // 是否预告/预览
    @SerialName("watch_platform")
    val watchPlatform: Int = 0,
    @SerialName("allow_demand")
    val allowDemand: Int = 0,
    @SerialName("area_limit")
    val areaLimit: Int = 0,
    @SerialName("allow_dm")
    val allowDanmaku: Int? = null
)

@Serializable
data class BangumiPublish(
    @SerialName("is_finish")
    val isFinish: Int = 0,
    @SerialName("is_started")
    val isStarted: Int = 0,
    @SerialName("pub_time")
    val pubTime: String = "",
    @SerialName("pub_time_show")
    val pubTimeShow: String = ""
)

@Serializable
data class BangumiPayment(
    val price: String = "",
    val tip: String = "",
    val promotion: String = "",
    @SerialName("vip_price")
    val vipPrice: String = "",
    @SerialName("vip_promotion")
    val vipPromotion: String = "",
    @SerialName("view_start_time")
    val viewStartTime: Long = 0
)

@Serializable
data class BangumiPositive(
    val id: Long = 0,
    val title: String = ""
)

@Serializable
data class BangumiSection(
    val id: Long = 0,
    val title: String = "",
    @SerialName("type")
    val type: Int = 0,
    val episodes: List<BangumiEpisode>? = null
)

@Serializable
data class UserStatus(
    val follow: Int = 0,              // 是否追番
    @SerialName("follow_status")
    val followStatus: Int = 0,
    val vip: Int = 0,                 // 是否大会员
    @SerialName("vip_frozen")
    val vipFrozen: Int = 0,
    val progress: WatchProgress? = null
)

@Serializable
data class WatchProgress(
    @SerialName("last_ep_id")
    val lastEpId: Long = 0,
    @SerialName("last_ep_index")
    val lastEpIndex: String = "",
    @SerialName("last_time")
    val lastTime: Long = 0            // 上次观看时间点
)

@Serializable
data class BangumiEpisodeCommunityResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BangumiEpisodeCommunity? = null,
)

@Serializable
data class BangumiEpisodeCommunity(
    val like: Int? = null,
    val favorite: Int? = null,
    @SerialName("coin_number") val coinNumber: Double? = null,
)

@Serializable
data class BangumiTripleResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BangumiTripleResult? = null,
)

@Serializable
data class BangumiTripleResult(
    val like: Int? = null,
    val coin: Int? = null,
    val favorite: Int? = null,
)

/**
 * 番剧播放地址响应
 * 标准 API /pgc/player/web/playurl 直接返回播放信息在 result 中
 */
@Serializable
data class BangumiPlayUrlResponse(
    val code: Int = 0,
    val message: String = "",
    val result: BangumiVideoInfo? = null
)

/**
 * 番剧播放视频信息（包含 DASH 等）。
 * 部分权限字段会在 Boolean、0/1 和字符串之间切换，统一使用宽容序列化器解析。
 */
@Serializable
data class BangumiVideoInfo(
    val fnver: Int = 0,
    val fnval: Int = 0,
    val type: String = "",
    val bp: Int = 0,
    @SerialName("vip_type")
    val vipType: Int = 0,
    @SerialName("vip_status")
    val vipStatus: Int = 0,
    @Serializable(with = FlexibleBooleanSerializer::class)
    @SerialName("is_drm")
    val isDrm: Boolean = false,
    @Serializable(with = FlexibleBooleanSerializer::class)
    @SerialName("has_paid")
    val hasPaid: Boolean = false,
    @Serializable(with = FlexibleBooleanSerializer::class)
    @SerialName("is_preview")
    val isPreview: Boolean = false,
    @Serializable(with = FlexibleFlagIntSerializer::class)
    val status: Int = 0,
    @SerialName("no_rexcode")
    val noRexcode: Int = 0,
    val quality: Int = 0,
    val format: String = "",
    val timelength: Long = 0,
    @SerialName("time_length")
    val timeLengthAlt: Long = 0L,
    @SerialName("accept_format")
    val acceptFormat: String = "",
    @SerialName("accept_quality")
    val acceptQuality: List<Int>? = null,
    @SerialName("accept_description")
    val acceptDescription: List<String>? = null,
    @SerialName("video_codecid")
    val videoCodecid: Int = 0,
    @SerialName("seek_param")
    val seekParam: String = "",
    @SerialName("seek_type")
    val seekType: String = "",
    //  关键：durl 和 dash 字段
    val durl: List<Durl>? = null,
    val durls: List<Durl>? = null,  // 某些情况下叫 durls
    val dash: Dash? = null,
    @SerialName("support_formats")
    val supportFormats: List<FormatItem>? = null,
    @SerialName("record_info")
    val recordInfo: BangumiRecordInfo? = null
)

@Serializable
data class BangumiRecordInfo(
    @SerialName("record_icon")
    val recordIcon: String = "",
    val record: String = ""
)

/**
 * 番剧类型枚举
 */
enum class BangumiType(val value: Int, val label: String) {
    ANIME(1, "番剧"),
    MOVIE(2, "电影"),
    DOCUMENTARY(3, "纪录片"),
    GUOCHUANG(4, "国创"),
    TV_SHOW(5, "电视剧"),
    VARIETY(7, "综艺")
}

// ========== 番剧搜索响应 ==========

/**
 * 番剧搜索响应
 */
@Serializable
data class BangumiSearchResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BangumiSearchData? = null
)

@Serializable
data class BangumiSearchData(
    val numPages: Int = 0,
    val numResults: Int = 0,
    val page: Int = 1,
    @SerialName("pagesize")
    val pageSize: Int = 20,
    val result: List<BangumiSearchItem>? = null
)

@Serializable
data class BangumiSearchItem(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("pgc_season_id")
    val pgcSeasonId: Long = 0,
    @SerialName("media_id")
    val mediaId: Long = 0,
    val title: String = "",              // 标题 (可能包含高亮标签)
    @SerialName("org_title")
    val orgTitle: String = "",           // 原标题
    val cover: String = "",
    val areas: String = "",              // 地区
    val styles: String = "",             // 风格
    @SerialName("cv")
    val cv: String = "",                 // 声优
    val staff: String = "",              // 制作人员
    @SerialName("season_type_name")
    val seasonTypeName: String = "",     // "番剧" "电影" 等
    @SerialName("season_type")
    val seasonType: Int = 0,
    @SerialName("media_type")
    val mediaType: Int = 0,
    val desc: String = "",               // 简介
    @SerialName("pubtime")
    val pubTime: Long = 0,
    @SerialName("media_score")
    val mediaScore: MediaScore? = null,
    @SerialName("ep_size")
    val epSize: Int = 0,                 // 集数
    @SerialName("is_avid")
    val isAvid: Boolean = false,
    val badges: List<BangumiSearchBadge>? = null,
    @SerialName("goto_url")
    val gotoUrl: String = "",
    @SerialName("index_show")
    val indexShow: String = "",          // "全12话" "更新至第5话"
    @SerialName("button_text")
    val buttonText: String = "",
    @SerialName("is_follow")
    val isFollow: Int = 0,
    @SerialName("eps")
    val episodes: List<BangumiSearchEpisode>? = null,
    @SerialName("hit_epids")
    val hitEpids: String = ""
)

@Serializable
data class MediaScore(
    val score: Float = 0f,
    @SerialName("user_count")
    val userCount: Int = 0
)

@Serializable
data class BangumiSearchBadge(
    val text: String = "",
    @SerialName("text_color")
    val textColor: String = "",
    @SerialName("text_color_night")
    val textColorNight: String = "",
    @SerialName("bg_color")
    val bgColor: String = "",
    @SerialName("bg_color_night")
    val bgColorNight: String = "",
    @SerialName("border_color")
    val borderColor: String = "",
    @SerialName("border_color_night")
    val borderColorNight: String = "",
    @SerialName("bg_style")
    val bgStyle: Int = 0
)

@Serializable
data class BangumiSearchEpisode(
    val id: Long = 0,
    val cover: String = "",
    val title: String = "",
    val url: String = "",
    @SerialName("index_title")
    val indexTitle: String = "",
    @SerialName("long_title")
    val longTitle: String = "",
    val badges: List<BangumiSearchBadge>? = null
)

// ========== 我的追番列表响应 ==========

/**
 * 我的追番列表响应
 */
@Serializable
data class MyFollowBangumiResponse(
    val code: Int = 0,
    val message: String = "",
    val data: MyFollowBangumiData? = null
)

@Serializable
data class MyFollowBangumiData(
    val total: Int = 0,
    val pn: Int = 1,
    val ps: Int = 30,
    val list: List<FollowBangumiItem>? = null
)

@Serializable
data class FollowBangumiItem(
    @SerialName("season_id")
    val seasonId: Long = 0,
    @SerialName("media_id")
    val mediaId: Long = 0,
    val title: String = "",
    val cover: String = "",
    @SerialName("square_cover")
    val squareCover: String = "",
    val evaluate: String = "",           // 简介
    val areas: List<AreaInfo>? = null,
    @SerialName("season_type_name")
    val seasonTypeName: String = "",
    @SerialName("season_type")
    val seasonType: Int = 0,
    val badge: String = "",              // "会员" "独家"
    @SerialName("badge_type")
    val badgeType: Int = 0,
    @SerialName("new_ep")
    val newEp: NewEpInfo? = null,
    val progress: String = "",           // 观看进度文案 "看到第5话"
    @SerialName("is_finish")
    val isFinish: Int = 0,               // 是否完结
    @SerialName("follow_status")
    val followStatus: Int = 0,           // 追番状态
    val total: Int = 0,                  // 总集数
    @SerialName("first_ep")
    val firstEp: Long = 0,               // 第一集 epId
    val url: String = ""
)

// ========== 筛选条件 ==========

/**
 * 番剧筛选条件
 */
data class BangumiFilter(
    val year: String = "-1",             // 年份，-1=全部
    val area: Int = -1,                  // 地区，-1=全部
    val styleId: Int = -1,               // 风格，-1=全部
    val isFinish: Int = -1,              // 状态，-1=全部, 0=连载, 1=完结
    val seasonStatus: String = "-1",     // 付费类型，-1=全部, 1=免费, 4,6=大会员
    val producerId: Int = -1,             // 出品方，-1=全部
    val order: Int = 3,                  // 排序指标，3=综合/追剧人数, 2=播放量
    val sortDirection: Int = 0,          // 排序方向，0=降序, 1=升序
    val seasonVersion: Int = -1,         // 类型：-1=全部，1=正片，2=电影，3=其他
    val spokenLanguageType: Int = -1,    // 配音：-1=全部，1=原声，2=中文配音
    val copyright: String = "-1",        // 版权：-1=全部，3=独家，1,2,4=其他
    val seasonMonth: Int = -1            // 季度：-1=全部，1/4/7/10
) {
    fun toApiYear(seasonType: Int): String {
        return if (seasonType == BangumiType.ANIME.value || seasonType == BangumiType.GUOCHUANG.value) {
            year
        } else {
            "-1"
        }
    }

    fun toApiReleaseDate(seasonType: Int): String {
        val shouldUseReleaseDate = seasonType == BangumiType.MOVIE.value ||
            seasonType == BangumiType.DOCUMENTARY.value ||
            seasonType == BangumiType.TV_SHOW.value ||
            seasonType == BangumiType.VARIETY.value
        if (!shouldUseReleaseDate || year == "-1") return "-1"

        val range = Regex("""^\[(.*?),(.*?)\)$""").matchEntire(year) ?: return "-1"
        val rawStart = range.groupValues[1].trim()
        val rawEnd = range.groupValues[2].trim()
        val start = rawStart.takeIf { it.isNotEmpty() }?.let { "$it-01-01 00:00:00" } ?: ""
        val end = rawEnd.takeIf { it.isNotEmpty() }?.let { "$it-01-01 00:00:00" } ?: ""
        return "[$start,$end)"
    }

    companion object {
        val ORDER_OPTIONS = listOf(
            3 to "综合排序",
            2 to "最多播放",
            0 to "最近更新",
            4 to "最高评分"
        )
        
        val AREA_OPTIONS = listOf(
            -1 to "全部地区",
            1 to "中国大陆",
            2 to "日本",
            3 to "美国",
            4 to "英国",
            5 to "加拿大",
            16 to "其他"
        )
        
        val STATUS_OPTIONS = listOf(
            -1 to "全部状态",
            0 to "连载中",
            1 to "已完结"
        )
        
        val YEAR_OPTIONS = listOf(
            "-1" to "全部年份",
            "[2025,2026)" to "2025",
            "[2024,2025)" to "2024",
            "[2023,2024)" to "2023",
            "[2022,2023)" to "2022",
            "[2021,2022)" to "2021",
            "[2020,2021)" to "2020",
            "[2019,2020)" to "2019",
            "[2018,2019)" to "2018",
            "[2017,2018)" to "2017",
            "[2016,2017)" to "2016",
            "[2015,2016)" to "2015",
            "[2010,2015)" to "2010-2014",
            "[2005,2010)" to "2005-2009",
            "[2000,2005)" to "2000-2004",
            "[1990,2000)" to "90年代",
            "[1980,1990)" to "80年代",
            "[,1980)" to "更早"
        )
    }
}

// ========== 课堂 (PUGV) 响应与映射模型 ==========

@Serializable
data class PugvSeasonResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PugvSeasonData? = null
)

@Serializable
data class PugvSeasonData(
    @SerialName("season_id")
    val seasonId: Long = 0L,
    val title: String = "",
    val subtitle: String? = null,
    val cover: String = "",
    val evaluate: String? = null,
    val brief: PugvBrief? = null,
    @SerialName("ep_count")
    val epCount: Int = 0,
    val stat: PugvStat? = null,
    val episodes: List<PugvEpisode>? = null,
    @SerialName("up_info")
    val upInfo: PugvUpInfo? = null,
    @SerialName("user_status")
    val userStatus: PugvUserStatus? = null,
    val cooperators: List<PugvCooperator>? = null
)

@Serializable
data class PugvBrief(
    val title: String? = null,
    val content: String? = null,
    val img: List<PugvBriefImg>? = null,
    val type: Int = 0
)

@Serializable
data class PugvBriefImg(
    val url: String = "",
    @SerialName("aspect_ratio")
    val aspectRatio: Float = 1.0f
)

@Serializable
data class PugvStat(
    val play: Long = 0L,
    @SerialName("play_desc")
    val playDesc: String = "",
    val views: Long = 0L,
    val reply: Long = 0L,
    @SerialName("favored_count")
    val favoredCount: Long = 0L,
    val share: Long = 0L
)

@Serializable
data class PugvUpInfo(
    val mid: Long = 0L,
    val uname: String = "",
    val avatar: String = "",
    val brief: String? = null,
    val follower: Long = 0L,
    @SerialName("is_follow")
    val isFollow: Int = 0
)

@Serializable
data class PugvCooperator(
    val mid: Long = 0L,
    val uname: String = "",
    val avatar: String = "",
    val role: String? = null
)

@Serializable
data class PugvUserStatus(
    val payed: Int = 0,
    val favored: Int = 0,
    @SerialName("favored_count")
    val favoredCount: Long = 0L,
    val progress: PugvProgress? = null
)

@Serializable
data class PugvProgress(
    @SerialName("last_ep_id")
    val lastEpId: Long = 0L
)

@Serializable
data class PugvEpisode(
    val id: Long = 0L,
    @SerialName("ep_id")
    val epId: Long = 0L,
    val aid: Long = 0L,
    val cid: Long = 0L,
    val title: String = "",
    val subtitle: String? = null,
    val cover: String = "",
    val duration: Long = 0L,
    val from: String = "pugv",
    val playable: Boolean = false,
    val status: Int = 0,
    val label: String? = null,
    @SerialName("episode_can_view")
    val episodeCanView: Boolean = false,
    val play: Long = 0L
)

fun PugvSeasonData.toBangumiDetail(): BangumiDetail {
    val episodesList = episodes.orEmpty().mapIndexed { index, ep ->
        val resolvedTitle = ep.title.ifBlank { "第${index + 1}讲" }
        val resolvedBadge = ep.label?.takeIf { it.isNotBlank() } ?: when {
            ep.playable || ep.episodeCanView -> "试看"
            else -> "付费"
        }
        // PUGV episode duration is expressed in seconds (matching PiliPlus' EpisodeItem).
        // Keep the conversion explicit so a short episode is not accidentally treated as ms.
        val durationMs = ep.duration.coerceAtLeast(0L).coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L
        val effectiveEpId = if (ep.id > 0L) ep.id else ep.epId
        BangumiEpisode(
            id = effectiveEpId,
            aid = ep.aid,
            bvid = if (ep.aid > 0L) IdUtils.av2bv(ep.aid) else "",
            cid = ep.cid,
            title = resolvedTitle,
            longTitle = ep.subtitle.orEmpty(),
            cover = ep.cover.ifBlank { cover },
            duration = durationMs,
            badge = resolvedBadge,
            status = ep.status,
            playable = ep.playable,
            episodeCanView = ep.episodeCanView,
            playCount = ep.play,
            from = ep.from
        )
    }
    val desc = evaluate?.takeIf { it.isNotBlank() }
        ?: brief?.content?.takeIf { it.isNotBlank() }
        ?: subtitle.orEmpty()
        
    val playCount = stat?.play?.takeIf { it > 0L } ?: stat?.views ?: 0L
    val favCount = userStatus?.favoredCount?.takeIf { it > 0L } ?: stat?.favoredCount ?: 0L

    return BangumiDetail(
        seasonId = seasonId,
        mediaId = 0L,
        title = title,
        cover = cover,
        squareCover = cover,
        evaluate = desc,
        stat = BangumiStat(
            views = playCount,
            favorites = favCount,
            reply = stat?.reply ?: 0L,
            share = stat?.share ?: 0L
        ),
        episodes = episodesList,
        seasonType = 10,
        seasonTypeName = "课堂",
        total = epCount.takeIf { it > 0 } ?: episodesList.size,
        userStatus = UserStatus(
            follow = if (userStatus?.favored == 1) 1 else 0,
            progress = userStatus?.progress?.lastEpId?.takeIf { epId -> epId > 0L }?.let { lastEpId ->
                WatchProgress(lastEpId = lastEpId)
            }
        ),
        seasonTitle = title,
        subtitle = subtitle.orEmpty(),
        upInfo = upInfo,
        briefImgs = brief?.img,
        cooperators = cooperators.orEmpty(),
        hasPaid = userStatus?.payed == 1
    )
}
