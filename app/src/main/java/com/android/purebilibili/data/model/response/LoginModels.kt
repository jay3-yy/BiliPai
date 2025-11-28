package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- 1. 二维码申请响应 ---
@Serializable
data class QrCodeResponse(
    val code: Int = 0,
    val message: String = "",
    val ttl: Int = 1,
    val data: QrData? = null
)

@Serializable
data class QrData(
    val url: String? = null,
    val qrcode_key: String? = null
)

// --- 2. 轮询状态响应 ---
@Serializable
data class PollResponse(
    val code: Int = 0,    // 接口请求状态 (0为成功)
    val message: String = "",
    val ttl: Int = 1,
    val data: PollData? = null
)

@Serializable
data class PollData(
    val url: String? = null,

    @SerialName("refresh_token")
    val refreshToken: String? = null,

    val timestamp: Long = 0,

    // 🔥 核心字段：
    // 0: 成功 (此时才有 refresh_token 和 cookie)
    // 86101: 未扫码
    // 86090: 已扫码未确认
    // 86038: 二维码过期
    val code: Int = 0,

    val message: String = ""
)