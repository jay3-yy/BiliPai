package com.android.purebilibili.data.model.response

import kotlinx.serialization.Serializable

@Serializable
data class BangumiReviewListResponse(
    val code: Int = 0,
    val message: String = "",
    val data: BangumiReviewListData? = null
)

@Serializable
data class BangumiReviewListData(
    val list: List<BangumiReviewItem> = emptyList(),
    val next: String = "",
    val count: Int? = null,
    val total: Int? = null
)

@Serializable
data class BangumiReviewItem(
    val review_id: Long = 0,
    val article_id: Long = 0,
    val title: String = "",
    val content: String = "",
    val push_time_str: String = "",
    val score: Int = 0,
    val author: BangumiReviewAuthor? = null,
    val stat: BangumiReviewStat? = null
)

@Serializable
data class BangumiReviewAuthor(
    val mid: Long = 0,
    val uname: String = "",
    val avatar: String = "",
    val level: Int = 0,
    val vip: BangumiReviewVip? = null,
)

@Serializable
data class BangumiReviewVip(
    val status: Int = 0,
    val type: Int = 0,
)

@Serializable
data class BangumiReviewStat(
    val likes: Int = 0,
    val liked: Int = 0,
    val disliked: Int = 0
)

enum class BangumiReviewType(val label: String) {
    SHORT("短评"),
    LONG("长评")
}