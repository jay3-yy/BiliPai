package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

@Serializable
data class VideoDetailResponse(
    // 👇 之前报错是因为缺了下面这行
    val code: Int = 0,
    val message: String = "",
    // 👆 补上就好了
    val data: ViewInfo? = null
)

@Serializable
data class ViewInfo(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val desc: String = "",
    val pic: String = "",
    val owner: Owner = Owner(),
    val stat: Stat = Stat(),
    val pages: List<Page> = emptyList()
)

@Serializable
data class Page(
    val cid: Long = 0,
    val page: Int = 0,
    val from: String = "",
    val part: String = ""
)