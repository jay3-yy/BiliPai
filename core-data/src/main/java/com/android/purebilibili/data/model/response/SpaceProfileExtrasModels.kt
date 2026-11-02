package com.android.purebilibili.data.model.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpaceReservationItem(
    val sid: Long = 0,
    val name: String = "",
    val total: Long = 0,
    @SerialName("is_follow") val isFollow: Int = 0,
    @SerialName("desc_text_1") val description: SpaceReservationText? = null,
    @SerialName("dynamic_id") val dynamicId: String = "",
    @SerialName("lottery_prize_info") val lottery: SpaceReservationLottery? = null,
)

@Serializable
data class SpaceReservationText(val text: String = "")

@Serializable
data class SpaceReservationLottery(val text: String = "", @SerialName("jump_url") val jumpUrl: String = "")

@Serializable
data class SpaceMedalWallResponse(val code: Int = 0, val message: String = "", val data: SpaceMedalWall? = null)

@Serializable
data class SpaceMedalWall(val list: List<SpaceMedalWallItem> = emptyList(), val count: Int = 0, val name: String = "")

@Serializable
data class SpaceMedalWallItem(
    @SerialName("target_name") val targetName: String = "",
    @SerialName("target_icon") val targetIcon: String = "",
    val link: String = "",
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("medal_info") val medalInfo: SpaceMedalWearing? = null,
    @SerialName("uinfo_medal") val medal: SpaceMedalIdentity? = null,
)

@Serializable
data class SpaceMedalWearing(@SerialName("wearing_status") val wearingStatus: Int = 0)

@Serializable
data class SpaceMedalIdentity(val id: Long = 0, val ruid: Long = 0, val name: String = "", val level: Int = 0)
