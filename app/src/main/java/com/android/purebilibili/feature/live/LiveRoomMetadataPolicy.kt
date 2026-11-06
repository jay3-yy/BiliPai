package com.android.purebilibili.feature.live

/** 详情请求发出之后到达的实时标题、观看数与高能榜不能被旧快照覆盖。 */
internal fun mergeLiveRoomMetadata(current: RoomInfo, published: RoomInfo, loaded: RoomInfo): RoomInfo =
    loaded.copy(
        title = if (current.title != published.title) current.title else loaded.title,
        watchedText = if (current.watchedText != published.watchedText) current.watchedText else loaded.watchedText,
        onlineRankText = current.onlineRankText,
    )
