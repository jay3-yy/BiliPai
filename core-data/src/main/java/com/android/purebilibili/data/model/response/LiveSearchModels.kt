package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LiveAppSearchResponse(
    val code: Int,
    val message: String = "",
    val data: LiveAppSearchData? = null,
)

@Serializable
data class LiveAppSearchData(
    val room: LiveAppSearchRooms? = null,
    val user: LiveAppSearchUsers? = null,
)

@Serializable
data class LiveAppSearchRooms(
    @SerialName("total_room") val total: Int? = null,
    val list: List<LiveAppSearchRoom>? = null,
)

@Serializable
data class LiveAppSearchUsers(
    @SerialName("total_user") val total: Int? = null,
    val list: List<LiveAppSearchUser>? = null,
)

@Serializable
data class LiveAppSearchRoom(
    val roomid: Long,
    val title: String = "",
    val name: String = "",
    val cover: String = "",
    val face: String = "",
    @SerialName("watched_show") val watchedShow: WatchedShow? = null,
)

@Serializable
data class LiveAppSearchUser(
    val roomid: Long,
    val name: String = "",
    val face: String = "",
    @SerialName("live_status") val liveStatus: Int = 0,
    val areaName: String = "",
    val fansNum: Int? = null,
)
