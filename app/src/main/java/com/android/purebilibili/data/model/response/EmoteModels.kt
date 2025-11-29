package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

@Serializable
data class EmoteResponse(
    val code: Int,
    val data: EmoteData?
)

@Serializable
data class EmoteData(
    val packages: List<EmotePackage>?
)

@Serializable
data class EmotePackage(
    val id: Long,
    val text: String, // 包名，如 "小黄脸"
    val emote: List<EmoteItem>?
)

@Serializable
data class EmoteItem(
    val id: Long,
    val text: String, // 表情代码，如 "[doge]"
    val url: String   // 图片链接
)