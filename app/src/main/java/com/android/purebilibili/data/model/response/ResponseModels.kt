// 文件路径: data/model/response/ResponseModels.kt
// 1. 强制压制 InternalSerializationApi 报错
@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReplyResponse(
    val code: Int = 0,
    val message: String = "",
    val data: ReplyData? = null
)

@Serializable
data class ReplyData(
    val cursor: ReplyCursor = ReplyCursor(),
    val replies: List<ReplyItem>? = emptyList()
)

@Serializable
data class ReplyCursor(
    @SerialName("all_count") val allCount: Int = 0,
    @SerialName("is_end") val isEnd: Boolean = false,
    val next: Int = 0
)

@Serializable
data class ReplyItem(
    val rpid: Long = 0,
    val oid: Long = 0,
    val mid: Long = 0,
    val count: Int = 0,
    val rcount: Int = 0,
    val like: Int = 0,
    val ctime: Long = 0,

    // 🔥🔥 核心修复：给对象类型加上默认值 = ReplyMember()
    // 遇到被删除用户或特殊评论时，member 字段可能缺失或为 null，不加默认值会导致整个列表解析崩溃
    val member: ReplyMember = ReplyMember(),
    val content: ReplyContent = ReplyContent(),

    val replies: List<ReplyItem>? = null
)

@Serializable
data class ReplyMember(
    val mid: String = "0",
    val uname: String = "未知用户",
    val avatar: String = "",

    @SerialName("level_info")
    val levelInfo: ReplyLevelInfo = ReplyLevelInfo(),

    val vip: ReplyVipInfo? = null
)

@Serializable
data class ReplyLevelInfo(
    @SerialName("current_level")
    val currentLevel: Int = 0
)

@Serializable
data class ReplyVipInfo(
    val vipType: Int = 0,
    val vipStatus: Int = 0
)

@Serializable
data class ReplyContent(
    val message: String = "",
    val device: String? = "",
    val emote: Map<String, ReplyEmote>? = null
)

@Serializable
data class ReplyEmote(
    val id: Long = 0,
    val text: String = "",
    val url: String = ""
)