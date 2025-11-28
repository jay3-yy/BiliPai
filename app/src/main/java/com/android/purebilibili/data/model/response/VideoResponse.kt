package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

// ❌ 已删除 NavResponse, NavData, WbiImg (移至 NavModels.kt)

// --- 1. 首页推荐流 ---
@Serializable
data class RecommendResponse(
    val data: RecommendData? = null
)

@Serializable
data class RecommendData(
    val item: List<VideoItem>? = null
)

@Serializable
data class VideoItem(
    val id: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val uri: String = "",
    val owner: Owner = Owner(),
    val stat: Stat = Stat()
)

@Serializable
data class Owner(
    val name: String = "",
    val face: String = "",
    val mid: Long = 0
)

@Serializable
data class Stat(
    val view: Long = 0,
    val danmaku: Long = 0,
    val like: Long = 0
)

// --- 2. 视频流地址 (升级版) ---
@Serializable
data class PlayUrlResponse(
    val data: PlayUrlData? = null
)

@Serializable
data class PlayUrlData(
    val dash: DashData? = null,
    val durl: List<Durl>? = null,
    val quality: Int = 0,      // 当前清晰度 ID (如 80, 64)
    val format: String = "",
    // 可选清晰度列表
    val accept_description: List<String> = emptyList(),
    val accept_quality: List<Int> = emptyList()
)

@Serializable
data class DashData(
    val video: List<DashItem> = emptyList(),
    val audio: List<DashItem> = emptyList()
)

@Serializable
data class DashItem(
    val id: Int = 0,
    val baseUrl: String = "",
    val bandwidth: Int = 0,
    val codecid: Int = 0
)

@Serializable
data class Durl(
    val url: String = ""
)