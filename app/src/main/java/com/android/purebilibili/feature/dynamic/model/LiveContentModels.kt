// 文件路径: feature/dynamic/model/LiveContentModels.kt
package com.android.purebilibili.feature.dynamic.model

import kotlinx.serialization.Serializable

/**
 *  直播内容信息（用于解析 JSON）
 * 注意：B站动态API的live_rcmd.content是嵌套的JSON字符串
 */
@Serializable
data class LiveContentInfo(
    val live_play_info: LivePlayInfo? = null,
    val type: Int = 0  // 直播类型
)

@Serializable
data class LivePlayInfo(
    val title: String = "",
    val cover: String = "",
    val online: Int? = null,
    val room_id: Long = 0,
    //  添加更多可选字段提高兼容性
    val area_name: String = "",  // 分区名称
    val parent_area_name: String = "",  // 父分区名称
    val uid: Long = 0,  // UP主ID
    val link: String = "",  // 直播间链接
    val watched_show: WatchedShow? = null  // 观看人数展示信息
)

@Serializable
data class WatchedShow(
    val num: Int = 0,
    val text_small: String = "",
    val text_large: String = ""
)
