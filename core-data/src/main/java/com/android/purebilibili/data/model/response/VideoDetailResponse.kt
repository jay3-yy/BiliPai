package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class VideoDetailResponse(
    //  之前报错是因为缺了下面这行
    val code: Int = 0,
    val message: String = "",
    //  补上就好了
    val data: ViewInfo? = null
)

/**
 *  视频尺寸信息
 * 用于判断横竖屏
 */
@Serializable
data class Dimension(
    val width: Int = 0,
    val height: Int = 0,
    val rotate: Int = 0
) {
    /** 是否为竖屏视频（考虑 rotate=90/270 的场景） */
    val isVertical: Boolean
        get() {
            if (width <= 0 || height <= 0) return false
            val normalizedRotate = ((rotate % 360) + 360) % 360
            val shouldSwap = normalizedRotate == 90 || normalizedRotate == 270
            val effectiveWidth = if (shouldSwap) height else width
            val effectiveHeight = if (shouldSwap) width else height
            return effectiveHeight > effectiveWidth
        }
}

@Serializable
data class ViewInfo(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val desc: String = "",
    val pic: String = "",
    val pubdate: Long = 0,  //  发布时间戳 (秒)
    val tname: String = "", //  分区名称
    val owner: Owner = Owner(),
    val stat: Stat = Stat(),
    val pages: List<Page> = emptyList(),
    @SerialName("is_stein_gate")
    val isSteinGate: Int = 0,
    val dimension: Dimension? = null,  //  视频尺寸信息
    val ugc_season: UgcSeason? = null,  //  [新增] 视频合集信息
    val copyright: Int = 1,  //  1=原创,2=转载,0=历史脏数据(按原创处理)
    val staff: List<VideoStaff> = emptyList(),
    val rights: VideoDetailRights = VideoDetailRights(),
    @SerialName("is_upower_exclusive")
    val isUpowerExclusive: Boolean = false,
    @SerialName("is_upower_play")
    val isUpowerPlay: Boolean = false,
    @SerialName("is_upower_preview")
    val isUpowerPreview: Boolean = false,
    @SerialName("is_upower_exclusive_with_qa")
    val isUpowerExclusiveWithQa: Boolean = false,
    @SerialName("argue_info")
    val argueInfo: VideoArgueInfo? = null,
    @SerialName("honor_reply")
    val honorReply: VideoHonorReply? = null,
    /** 新版简介分段:type=2 为 @提及,biz_id 是被@用户 mid,可跳空间。 */
    @SerialName("desc_v2")
    val descV2: List<VideoDescSegment> = emptyList(),
    @SerialName("redirect_url")
    val redirectUrl: String? = null
) {
    val isCooperation: Boolean
        get() = rights.isCooperation == 1 || staff.isNotEmpty()

    /** 转载稿件服务端强制 1 币上限；与 PiliPlus `copyright != 2` 判定一致。 */
    val isRepost: Boolean
        get() = copyright == 2
}

@Serializable
data class VideoDescSegment(
    @SerialName("raw_text")
    val rawText: String = "",
    val type: Int = 1,
    @SerialName("biz_id")
    val bizId: Long = 0
)

/**
 * 视频荣誉(入站必刷/每周必看/全站排行榜/热门)。
 * desc 线上可能是数字或字符串,用 JsonPrimitive 容忍两种形态。
 */
@Serializable
data class VideoHonorReply(
    val honor: List<VideoHonor> = emptyList()
)

@Serializable
data class VideoHonor(
    val aid: Long = 0,
    val type: Int = 0,
    val desc: kotlinx.serialization.json.JsonPrimitive? = null,
    @SerialName("weekly_recommend_num")
    val weeklyRecommendNum: Int = 0,
    @SerialName("honor_name")
    val honorName: String = "",
    @SerialName("honor_url")
    val honorUrl: String = "",
    @SerialName("honor_icon_url")
    val honorIconUrl: String = ""
)

/**
 * UP 主设置的视频声明（如"虚构演绎,请勿过度解读"）。
 * 对齐 PiliPlus ArgueInfo:仅 argue_msg 一个有效字段。
 */
@Serializable
data class VideoArgueInfo(
    @SerialName("argue_msg")
    val argueMsg: String = ""
)

@Serializable
data class VideoDetailRights(
    val elec: Int = 0,
    @SerialName("is_cooperation")
    val isCooperation: Int = 0,
    @SerialName("no_reprint")
    val noReprint: Int = 0
)

@Serializable
data class VideoStaff(
    val mid: Long = 0,
    val title: String = "",
    val name: String = "",
    val face: String = "",
    val vip: VideoStaffVip = VideoStaffVip(),
    val official: VideoStaffOfficial = VideoStaffOfficial(),
    val follower: Int = 0,
    @SerialName("label_style")
    val labelStyle: Int = 0
)

@Serializable
data class VideoStaffVip(
    val type: Int = 0,
    val status: Int = 0,
    @SerialName("due_date")
    val dueDate: Long = 0,
    @SerialName("vip_pay_type")
    val vipPayType: Int = 0,
    @SerialName("theme_type")
    val themeType: Int = 0
)

@Serializable
data class VideoStaffOfficial(
    val role: Int = 0,
    val title: String = "",
    val desc: String = "",
    val type: Int = -1
)

@Serializable
data class Page(
    val cid: Long = 0,
    val page: Int = 0,
    val from: String = "",
    val part: String = "",
    val duration: Long = 0,
    @SerialName("first_frame") val firstFrame: String = "",
    /** 分P尺寸；/x/player/pagelist 会返回，用于在播放信息加载前判断横竖屏。 */
    val dimension: Dimension? = null,
)

/** /x/player/pagelist 的响应：只含分P列表，比 x/web-interface/view 轻量得多。 */
@Serializable
data class PageListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<Page>? = null
)

//  视频标签响应
@Serializable
data class VideoTagResponse(
    val code: Int = 0,
    val message: String = "",
    val data: List<VideoTag>? = null
)

@Serializable
data class VideoTag(
    val tag_id: Long = 0,
    val tag_name: String = "",
    val music_id: String = "",
    val tag_type: String = "",
    val jump_url: String = "",
    val cover: String = "",
    val content: String = "",
    val short_content: String = "",
    val type: Int = 0,
    val state: Int = 0,
    val count: VideoTagCount? = null
)

@Serializable
data class VideoTagCount(
    val view: Int = 0,
    val use: Int = 0,
    val atten: Int = 0
)

//  [新增] 视频合集 (UGC Season) 数据结构
@Serializable
data class UgcSeason(
    val id: Long = 0,
    val title: String = "",
    val cover: String = "",
    val mid: Long = 0,
    val ep_count: Int = 0,  // 总集数
    val intro: String = "", // 合集简介（视图接口返回，PiliPlus 未利用）
    val desc: String = "",
    val sections: List<UgcSection> = emptyList()
) {
    /** 展示用简介：intro 优先，desc 兜底 */
    val displayIntro: String get() = intro.ifBlank { desc }
}

@Serializable
data class UgcSection(
    val season_id: Long = 0,
    val id: Long = 0,
    val title: String = "",
    val episodes: List<UgcEpisode> = emptyList()
)

@Serializable
data class UgcEpisode(
    val id: Long = 0,
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val arc: UgcEpisodeArc? = null,
    // 分 P 列表：合集接口按集返回，弹窗内的分 P chips 依赖此字段
    val pages: List<Page> = emptyList(),
    @SerialName("is_upower_exclusive")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isUpowerExclusive: Boolean = false,
    @SerialName("is_upower_exclusive_with_qa")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isUpowerExclusiveWithQa: Boolean = false,
    @SerialName("is_charging_arc")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isChargingArc: Boolean = false,
    @SerialName("elec_arc_type")
    @Serializable(with = FlexibleIntSerializer::class)
    val elecArcType: Int = 0,
    val rights: VideoRights? = null,
    // 合集按集返回的封面角标独立于详情页权限字段，不能在解码时丢弃。
    val badge: String? = null,
) {
    /** 只使用当前集的权限元数据，不从合集名称、播放权限或封面推断。 */
    val accessLabel: String?
        get() = resolveVideoStatusLabels(
            charging = isUpowerExclusive || isUpowerExclusiveWithQa || isChargingArc || elecArcType == 1 ||
                (rights?.ugcPay ?: 0) > 0 || (rights?.ugcPayPreview ?: 0) > 0 || arc?.isChargingExclusive == true,
            paid = (rights?.pay ?: 0) > 0 || (rights?.arcPay ?: 0) > 0 || arc?.isPaid == true,
            serverLabels = listOfNotNull(badge),
        ).firstOrNull()
}

@Serializable
data class UgcEpisodeArc(
    val aid: Long = 0,
    val pic: String = "",
    val title: String = "",
    val pubdate: Long = 0,
    val ctime: Long = 0,
    val duration: Int = 0,
    val stat: Stat? = null,
    @SerialName("is_upower_exclusive")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isUpowerExclusive: Boolean = false,
    @SerialName("is_upower_exclusive_with_qa")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isUpowerExclusiveWithQa: Boolean = false,
    @SerialName("is_charging_arc")
    @Serializable(with = FlexibleBooleanSerializer::class)
    val isChargingArc: Boolean = false,
    @SerialName("elec_arc_type")
    @Serializable(with = FlexibleIntSerializer::class)
    val elecArcType: Int = 0,
    val rights: VideoRights? = null,
) {
    val isChargingExclusive: Boolean
        get() = isUpowerExclusive || isUpowerExclusiveWithQa || isChargingArc || elecArcType == 1 ||
            (rights?.ugcPay ?: 0) > 0 || (rights?.ugcPayPreview ?: 0) > 0

    val isPaid: Boolean
        get() = (rights?.pay ?: 0) > 0 || (rights?.arcPay ?: 0) > 0
}
