// 文件路径: data/model/response/ResponseModels.kt
// 1. 强制压制 InternalSerializationApi 报错
@file:OptIn(
    kotlinx.serialization.InternalSerializationApi::class,
    kotlinx.serialization.ExperimentalSerializationApi::class
)

package com.android.purebilibili.data.model.response

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@Serializable
data class ReplyResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ReplyData? = null
)

@Serializable
data class ReplyCountResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ReplyCountData? = null
)

@Serializable
data class ReplyCountData(
    val count: Int = 0
)

@Serializable
data class ReplyData(
    //  WBI API 使用 cursor
    val cursor: ReplyCursor = ReplyCursor(),
    //  旧版 API 使用 page
    val page: ReplyPage = ReplyPage(),
    //  桌面端评论配置
    val config: ReplyConfig? = null,
    //  普通评论列表
    val replies: List<ReplyItem>? = emptyList(),
    //  [新增] 置顶评论列表 (WBI API)
    @SerialName("top_replies")
    val topReplies: List<ReplyItem>? = null,
    //  [新增] 热门评论列表
    val hots: List<ReplyItem>? = null,
    //  [新增] WBI API 置顶信息（top.upper/admin/vote）
    val top: ReplyTop? = null,
    //  [新增] UP主信息（包含 UP 置顶评论）
    val upper: ReplyUpper? = null,
    //  [新增] 评论输入控制（占位文案/图片上传开关）
    val control: ReplyPageControl? = null
) {
    //  统一获取总评论数
    fun getAllCount(): Int = when {
        cursor.allCount > 0 -> cursor.allCount
        page.acount > 0 -> page.acount
        else -> page.count
    }
    //  统一获取是否结束
    fun getIsEnd(currentPage: Int, currentSize: Int): Boolean {
        return if (cursor.allCount > 0) {
            cursor.isEnd
        } else {
            // 旧版 API 没有 isEnd，用页数判断
            currentSize >= page.count || page.count == 0
        }
    }
    //  [新增] 获取置顶评论（WBI 和旧版 API 兼容）
    fun collectTopReplies(): List<ReplyItem> {
        val result = mutableListOf<ReplyItem>()
        // WBI API: data.top.upper/admin/vote
        top?.upper?.let { result.add(it) }
        top?.admin?.let { result.add(it) }
        top?.vote?.let { result.add(it) }
        // 添加 UP 置顶
        upper?.top?.let { result.add(it) }
        // 添加其他置顶
        topReplies?.let { result.addAll(it) }
        return result.distinctBy { it.rpid }
    }
}

@Serializable
data class ReplyPageControl(
    @SerialName("input_disable")
    val inputDisable: Boolean = false,
    @SerialName("root_input_text")
    val rootInputText: String = "",
    @SerialName("child_input_text")
    val childInputText: String = "",
    @SerialName("upload_picture_icon_state")
    val uploadPictureIconState: Int = 0
) {
    val canUploadPicture: Boolean
        get() = uploadPictureIconState == 1 && !inputDisable
}

@Serializable
data class ReplyConfig(
    @Serializable(with = FlexibleBooleanSerializer::class)
    @SerialName("show_up_flag")
    val showUpFlag: Boolean = false
)

//  [新增] UP 主信息
@Serializable
data class ReplyUpper(
    val mid: Long = 0,
    // UP 主置顶评论
    val top: ReplyItem? = null
)

@Serializable
data class ReplyTop(
    val admin: ReplyItem? = null,
    val upper: ReplyItem? = null,
    val vote: ReplyItem? = null
)

//  WBI API 的游标信息
@Serializable
data class ReplyCursor(
    @SerialName("all_count") val allCount: Int = 0,
    @SerialName("is_end") val isEnd: Boolean = false,
    val next: Int = 0
)

//  旧版 API 的分页信息
@Serializable
data class ReplyPage(
    val num: Int = 0,      // 当前页码
    val size: Int = 0,     // 每页数量
    val count: Int = 0,    // 总评论数
    val acount: Int = 0    // 总计评论条数（包含回复）
)

@Serializable
data class ReplyItem(
    val rpid: Long = 0,
    val root: Long = 0,
    val oid: Long = 0,
    val mid: Long = 0,
    val count: Int = 0,
    val rcount: Int = 0,
    val like: Int = 0,
    val ctime: Long = 0,
    
    // [新增] 当前用户是否已点赞: 0=未点赞, 1=已点赞
    val action: Int = 0,

    //  核心修复：给对象类型加上默认值 = ReplyMember()
    // 遇到被删除用户或特殊评论时，member 字段可能缺失或为 null，不加默认值会导致整个列表解析崩溃
    @Serializable(with = ReplyMemberOrDefaultSerializer::class)
    val member: ReplyMember = ReplyMember(),
    val content: ReplyContent = ReplyContent(),

    val replies: List<ReplyItem>? = null,

    @SerialName("card_label")
    val cardLabels: List<ReplyCardLabel>? = null,
    
    //  UP主操作信息（UP觉得很赞/UP回复了）
    @SerialName("up_action")
    val upAction: ReplyUpAction? = null,
    
    // [新增] 评论控制信息（IP属地等）
    @SerialName("reply_control")
    val replyControl: ReplyControl? = null
)

//  UP主操作信息
@Serializable
data class ReplyUpAction(
    val like: Boolean = false,  // UP主觉得很赞
    val reply: Boolean = false  // UP主回复了
)

@Serializable
data class ReplyCardLabel(
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("text_content")
    val textContent: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("label_color")
    val labelColor: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("jump_url")
    val jumpUrl: String = ""
)

object ReplyMemberOrDefaultSerializer : KSerializer<ReplyMember> {
    override val descriptor: SerialDescriptor = ReplyMember.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ReplyMember) {
        encoder.encodeSerializableValue(ReplyMember.serializer(), value)
    }

    override fun deserialize(decoder: Decoder): ReplyMember {
        if (decoder !is JsonDecoder) {
            return decoder.decodeSerializableValue(ReplyMember.serializer())
        }
        val element = decoder.decodeJsonElement()
        if (element is JsonNull) {
            return ReplyMember()
        }
        return decoder.json.decodeFromJsonElement(ReplyMember.serializer(), element)
    }
}

@Serializable
data class ReplyMember(
    @Serializable(with = FlexibleStringSerializer::class)
    val mid: String = "0",
    @Serializable(with = FlexibleStringSerializer::class)
    val uname: String = "未知用户",
    @Serializable(with = FlexibleStringSerializer::class)
    val avatar: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("is_senior_member")
    val isSeniorMember: Int = 0,

    @SerialName("level_info")
    val levelInfo: ReplyLevelInfo = ReplyLevelInfo(),

    val vip: ReplyVipInfo? = null,

    @SerialName("fans_detail")
    val fansDetail: ReplyFansDetail? = null,

    val nameplate: ReplyNameplate? = null,

    @Serializable(with = FlexibleImageUrlSerializer::class)
    @SerialName("garb_card_image")
    val garbCardImage: String = "",

    @Serializable(with = FlexibleImageUrlSerializer::class)
    @SerialName("garb_card_image_with_focus")
    val garbCardImageWithFocus: String = "",

    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("garb_card_number")
    val garbCardNumber: String = "",

    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("garb_card_fan_color")
    val garbCardFanColor: String = "",

    @Serializable(with = FlexibleFlagIntSerializer::class)
    @SerialName("garb_card_is_fan")
    val garbCardIsFan: Int = 0,

    @SerialName("user_sailing")
    @Serializable(with = ReplyUserSailingOrNullSerializer::class)
    val userSailing: ReplyUserSailing? = null,

    @SerialName("user_sailing_v2")
    @Serializable(with = ReplyUserSailingOrNullSerializer::class)
    val userSailingV2: ReplyUserSailing? = null
)

@Serializable
data class ReplyLevelInfo(
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("current_level")
    val currentLevel: Int = 0
)

@Serializable
data class ReplyVipInfo(
    val vipType: Int = 0,
    val vipStatus: Int = 0
)

@Serializable
data class ReplyFansDetail(
    @Serializable(with = FlexibleLongSerializer::class)
    val uid: Long = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    @SerialName("medal_id")
    val medalId: Long = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("medal_name")
    val medalName: String = "",
    @Serializable(with = FlexibleIntSerializer::class)
    val score: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val level: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val intimacy: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("master_status")
    val masterStatus: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    @SerialName("is_receive")
    val isReceive: Int = 0
)

@Serializable
data class ReplyNameplate(
    @Serializable(with = FlexibleLongSerializer::class)
    val nid: Long = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val image: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("image_small")
    val imageSmall: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val level: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val condition: String = ""
)

@Serializable
data class ReplyUserSailing(
    val pendant: ReplySailingPendant? = null,
    @SerialName("cardbg")
    @Serializable(with = ReplySailingCardBgOrNullSerializer::class)
    val cardBg: ReplySailingCardBg? = null,
    @SerialName("cardbg_with_focus")
    @Serializable(with = ReplySailingCardBgOrNullSerializer::class)
    val cardBgWithFocus: ReplySailingCardBg? = null
)

@Serializable
data class ReplySailingPendant(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val image: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("jump_url")
    val jumpUrl: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val type: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("image_enhance")
    val imageEnhance: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("image_enhance_frame")
    val imageEnhanceFrame: String = ""
)

@Serializable
data class ReplySailingCardBg(
    @Serializable(with = FlexibleLongSerializer::class)
    val id: Long = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String = "",
    @Serializable(with = FlexibleImageUrlSerializer::class)
    val image: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("jump_url")
    val jumpUrl: String = "",
    @Serializable(with = ReplySailingFanOrNullSerializer::class)
    val fan: ReplySailingFan? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val type: String = ""
)

@Serializable
data class ReplySailingFan(
    @Serializable(with = FlexibleFlagIntSerializer::class)
    @SerialName("is_fan")
    val isFan: Int = 0,
    @Serializable(with = FlexibleLongSerializer::class)
    val number: Long = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    val color: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    val name: String = "",
    @Serializable(with = FlexibleStringSerializer::class)
    @SerialName("num_desc")
    val numDesc: String = ""
)

object ReplyUserSailingOrNullSerializer : KSerializer<ReplyUserSailing?> {
    override val descriptor: SerialDescriptor = ReplyUserSailing.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ReplyUserSailing?) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        encoder.encodeSerializableValue(ReplyUserSailing.serializer(), value)
    }

    override fun deserialize(decoder: Decoder): ReplyUserSailing? {
        if (decoder !is JsonDecoder) {
            return runCatching { decoder.decodeSerializableValue(ReplyUserSailing.serializer()) }.getOrNull()
        }
        val element = decoder.decodeJsonElement()
        if (element is JsonNull || element !is JsonObject) return null
        return runCatching { decoder.json.decodeFromJsonElement(ReplyUserSailing.serializer(), element) }.getOrNull()
    }
}

object ReplySailingCardBgOrNullSerializer : KSerializer<ReplySailingCardBg?> {
    override val descriptor: SerialDescriptor = ReplySailingCardBg.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ReplySailingCardBg?) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        encoder.encodeSerializableValue(ReplySailingCardBg.serializer(), value)
    }

    override fun deserialize(decoder: Decoder): ReplySailingCardBg? {
        if (decoder !is JsonDecoder) {
            return runCatching { decoder.decodeSerializableValue(ReplySailingCardBg.serializer()) }.getOrNull()
        }
        val element = decoder.decodeJsonElement()
        if (element is JsonNull || element !is JsonObject) return null
        return runCatching { decoder.json.decodeFromJsonElement(ReplySailingCardBg.serializer(), element) }.getOrNull()
    }
}

object ReplySailingFanOrNullSerializer : KSerializer<ReplySailingFan?> {
    override val descriptor: SerialDescriptor = ReplySailingFan.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ReplySailingFan?) {
        if (value == null) {
            encoder.encodeNull()
            return
        }
        encoder.encodeSerializableValue(ReplySailingFan.serializer(), value)
    }

    override fun deserialize(decoder: Decoder): ReplySailingFan? {
        if (decoder !is JsonDecoder) {
            return runCatching { decoder.decodeSerializableValue(ReplySailingFan.serializer()) }.getOrNull()
        }
        val element = decoder.decodeJsonElement()
        if (element is JsonNull || element !is JsonObject) return null
        return runCatching { decoder.json.decodeFromJsonElement(ReplySailingFan.serializer(), element) }.getOrNull()
    }
}

object FlexibleFlagIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = FlexibleIntSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Int) {
        encoder.encodeInt(value)
    }

    override fun deserialize(decoder: Decoder): Int {
        if (decoder !is JsonDecoder) return decoder.decodeInt()
        val element = decoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return 0
        primitive.booleanOrNull?.let { return if (it) 1 else 0 }
        val content = runCatching { primitive.content }.getOrNull() ?: return 0
        return when (content.lowercase()) {
            "true" -> 1
            "false" -> 0
            else -> content.toIntOrNull() ?: content.toDoubleOrNull()?.toInt() ?: 0
        }
    }
}

object FlexibleBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = FlexibleIntSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Boolean) {
        encoder.encodeBoolean(value)
    }

    override fun deserialize(decoder: Decoder): Boolean {
        if (decoder !is JsonDecoder) return decoder.decodeBoolean()
        val element = decoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return false
        primitive.booleanOrNull?.let { return it }
        val content = runCatching { primitive.content }.getOrNull() ?: return false
        return when (content.lowercase()) {
            "true", "1" -> true
            "false", "0" -> false
            else -> content.toIntOrNull()?.let { it != 0 } ?: false
        }
    }
}

@Serializable
data class ReplyContent(
    val message: String = "",
    val device: String? = "",
    val emote: Map<String, ReplyEmote>? = null,
    //  评论图片
    val pictures: List<ReplyPicture>? = null
)

//  评论图片
@Serializable
data class ReplyPicture(
    @SerialName("img_src") val imgSrc: String = "",
    @SerialName("img_width") val imgWidth: Int = 0,
    @SerialName("img_height") val imgHeight: Int = 0,
    @SerialName("img_size") val imgSize: Float = 0f
)

@Serializable
data class ReplyEmote(
    val id: Long = 0,
    val text: String = "",
    val url: String = ""
)

// [新增] 发送评论响应
@Serializable
data class AddReplyResponse(
    val code: Int = 0,
    val message: String = "",
    val data: AddReplyData? = null
)

@Serializable
data class AddReplyData(
    val rpid: Long = 0,
    @SerialName("rpid_str") val rpidStr: String = "",
    val dialog: Long = 0,
    val root: Long = 0,
    val parent: Long = 0,
    val reply: ReplyItem? = null
)

@Serializable
data class UploadCommentImageResponse(
    val code: Int = 0,
    val message: String = "",
    val data: UploadCommentImageData? = null
)

@Serializable
data class UploadCommentImageData(
    @SerialName("image_url") val imageUrl: String = "",
    @SerialName("image_width") val imageWidth: Int = 0,
    @SerialName("image_height") val imageHeight: Int = 0,
    @SerialName("img_size") val imgSize: Float = 0f
)

// [新增] 评论控制信息（IP属地等）
@Serializable
data class ReplyControl(
    val location: String = ""  // IP 属地，如 "IP属地：北京"
)
