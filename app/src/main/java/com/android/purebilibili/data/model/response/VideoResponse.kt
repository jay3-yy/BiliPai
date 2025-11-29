package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

// --- 播放地址 Response (Wbi 接口) ---
// 只保留这个，因为其他类（VideoDetail, Nav, Search等）在你的其他文件中已经存在了
@Serializable
data class PlayUrlResponse(
    val code: Int = 0,
    val message: String = "",
    val data: PlayUrlData? = null
)

@Serializable
data class PlayUrlData(
    val quality: Int = 0,
    val format: String = "",
    val timelength: Long = 0,
    val accept_format: String = "",
    val accept_description: List<String> = emptyList(),
    val accept_quality: List<Int> = emptyList(),
    val video_codecid: Int = 0,
    val durl: List<Durl>? = null,
    val dash: Dash? = null
)

@Serializable
data class Durl(
    val order: Int = 0,
    val length: Long = 0,
    val size: Long = 0,
    val url: String = "",
    val backup_url: List<String>? = null
)

@Serializable
data class Dash(
    val duration: Int = 0,
    val minBufferTime: Float = 0f,
    val video: List<DashMedia> = emptyList(),
    val audio: List<DashMedia>? = emptyList()
)

@Serializable
data class DashMedia(
    val id: Int = 0,
    val baseUrl: String = "",
    val backupUrl: List<String>? = emptyList(),
    val bandwidth: Int = 0,
    val mimeType: String = "",
    val codecs: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: String = "",
    val sar: String = ""
)