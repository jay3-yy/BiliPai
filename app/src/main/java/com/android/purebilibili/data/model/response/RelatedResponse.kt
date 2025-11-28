package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

@Serializable
data class RelatedResponse(
    val data: List<RelatedVideo>? = null
)

@Serializable
data class RelatedVideo(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val owner: Owner = Owner(),
    val stat: Stat = Stat(),
    val duration: Int = 0 // 视频时长(秒)
)