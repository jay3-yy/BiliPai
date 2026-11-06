package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

// --- 0. 基础响应 (用于只返回 code 的 API) ---
@Serializable
data class BaseResponse(
    val code: Int = 0,
    val message: String = ""
)

// --- 1. 导航信息 (基本信息、等级、钱包、VIP、Wbi签名Key) ---
@Serializable
data class NavResponse(
    val code: Int = 0,
    val data: NavData? = null,
    val message: String = ""
)

@Serializable
data class NavData(
    val isLogin: Boolean = false,
    val uname: String = "",
    val face: String = "",
    val mid: Long = 0,
    val level_info: LevelInfo = LevelInfo(),
    val money: Double = 0.0, // 硬币
    val wallet: Wallet = Wallet(),
    val vip: VipInfo = VipInfo(),
    //  核心修复：补回 wbi_img 字段
    val wbi_img: WbiImg? = null,
    //  [New] 顶部背景图 (API 返回)
    val top_photo: String = "",
    // 认证信息：-1 无认证，0 个人认证（黄V），1 机构认证（蓝V）
    val officialVerify: NavOfficialVerify = NavOfficialVerify()
)

@Serializable
data class NavOfficialVerify(
    val type: Int = -1
)

@Serializable
data class WbiImg(
    val img_url: String = "",
    val sub_url: String = ""
)

@Serializable
data class LevelInfo(
    @Serializable(with = FlexibleIntSerializer::class)
    val current_level: Int = 0,
    @Serializable(with = FlexibleIntSerializer::class)
    val current_min: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val current_exp: Int? = null,
    @Serializable(with = FlexibleIntSerializer::class)
    val next_exp: Int? = null
)

@Serializable
data class Wallet(
    val bcoin_balance: Double = 0.0 // B币
)

@Serializable
data class VipInfo(
    val status: Int = 0, // 1: 有效
    val type: Int = 0,   // 1: 月度大会员, 2: 年度大会员
    val label: VipLabel = VipLabel()
)

@Serializable
data class VipLabel(
    val text: String = "" // "大会员", "年度大会员"
)

// --- 2. 统计信息 (关注、粉丝、动态) ---
@Serializable
data class NavStatResponse(
    val code: Int = 0,
    val data: NavStatData? = null
)

@Serializable
data class NavStatData(
    val following: Int = 0,      // 关注
    val follower: Int = 0,       // 粉丝
    val dynamic_count: Int = 0   // 动态
)

// 签名 key 刷新响应
@Serializable
data class WebTicketResponse(
    val code: Int = 0,
    val message: String = "",
    val data: WebTicketData? = null
)

@Serializable
data class WebTicketData(
    val ticket: String = "",
    val ttl: Long = 0,
    @kotlinx.serialization.SerialName("created_at")
    val createdAt: Long = 0,
    val nav: WebTicketNav? = null
)

@Serializable
data class WebTicketNav(
    val img: String = "",
    val sub: String = ""
)
