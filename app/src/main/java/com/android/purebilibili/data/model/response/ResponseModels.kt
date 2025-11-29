package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

// 评论列表响应
@Serializable
data class ReplyResponse(
    val code: Int,
    val message: String,
    val data: ReplyData?
)

@Serializable
data class ReplyData(
    val cursor: ReplyCursor,
    val replies: List<ReplyItem>? // 可能为空
)

@Serializable
data class ReplyCursor(
    val all_count: Int, // 总评论数
    val is_end: Boolean,
    val next: Int // 下一页的游标
)

@Serializable
data class ReplyItem(
    val rpid: Long,      // 评论ID
    val oid: Long,       // 对应的稿件ID (aid)
    val mid: Long,       // 发送者ID
    val count: Int,      // 子评论数量
    val rcount: Int,     // 子评论数量 (显示用)
    val like: Int,       // 点赞数
    val ctime: Long,     // 发布时间 (秒)
    val member: ReplyMember, // 发送者信息
    val content: ReplyContent, // 评论内容
    val replies: List<ReplyItem>? = null // 子评论 (二级评论)
)

@Serializable
data class ReplyMember(
    val mid: String,
    val uname: String,
    val avatar: String,
    val level_info: LevelInfo,
    val vip: VipInfo? = null
)

@Serializable
data class ReplyContent(
    val message: String, // 评论文本
    val device: String? = "", // 设备 (如 "Android")

    // 🔥🔥 [核心补全] 新增 emote 字段
    // B站接口会把这条评论用到的特殊表情详情（包括URL）放在这里
    val emote: Map<String, ReplyEmote>? = null
)

// 🔥🔥 [新增] 表情详情类
@Serializable
data class ReplyEmote(
    val id: Long,
    val text: String, // 如 "[doge]"
    val url: String   // 图片 URL
)