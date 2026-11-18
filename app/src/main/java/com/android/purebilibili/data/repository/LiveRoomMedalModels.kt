package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.LiveMessageMedal

internal data class LiveRoomMedalStatus(
    val hasRoomMedal: Boolean,
    val displayed: LiveMessageMedal?,
    val roomMedalId: Long?,
    val wearingMedalId: Long?,
)

internal data class LiveRoomMedalEntry(
    val id: Long,
    val anchorId: Long,
    val anchorName: String,
    val medal: LiveMessageMedal,
    val wearing: Boolean,
    val intimacy: Int?,
    val nextIntimacy: Int?,
    val todayFeed: Int?,
    val dailyLimit: Int?,
)

internal data class LiveRoomMedalPage(
    val items: List<LiveRoomMedalEntry>,
    val hasMore: Boolean,
    val total: Int?,
)

internal enum class LiveMedalVisibilityOption(val apiKey: String) {
    HIDE_SPACE_MEDAL("close_space_medal"),
    SHOW_WEARING_ONLY("only_show_wearing"),
}

internal data class LiveMedalVisibility(
    val hideSpaceMedal: Boolean,
    val showWearingOnly: Boolean,
)
