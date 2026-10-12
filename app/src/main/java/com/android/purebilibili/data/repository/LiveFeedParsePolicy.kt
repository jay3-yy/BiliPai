package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.LiveFeedAreaEntry
import com.android.purebilibili.data.model.response.LiveFeedHomeSnapshot
import com.android.purebilibili.data.model.response.LiveFeedIndexData
import com.android.purebilibili.data.model.response.LiveFeedRoomCard
import com.android.purebilibili.data.model.response.LiveRoom

/** Card types we intentionally ignore (ads/banners/unknown modules). */
internal val LIVE_FEED_IGNORED_CARD_TYPES = setOf(
    "banner_v2",
    "banner_v1",
    "ad_card",
    "ad_v1",
    "operation_card",
)

internal fun isUsableLiveFeedRoom(card: LiveFeedRoomCard): Boolean {
    if (card.isAd) return false
    if (card.resolvedRoomId() <= 0L) return false
    // 无标题且无封面的卡片通常是占位/广告壳
    if (card.title.isBlank() && card.cover.isBlank() && card.systemCover.isBlank()) return false
    return true
}

internal fun isUsableLiveFeedAreaEntry(card: LiveFeedRoomCard): Boolean {
    val title = card.title.ifBlank { card.areaName }
    if (title.isBlank()) return false
    // 至少要有一级或二级分区 id
    return card.areaV2ParentId > 0 || card.areaV2Id > 0
}

/**
 * Parse app live feed `card_list` into home snapshot.
 * Only keeps: small_card_v1 / my_idol_v1 / area_entrance_v3 (BiliPai same set).
 */
internal fun parseLiveFeedHomeSnapshot(data: LiveFeedIndexData): LiveFeedHomeSnapshot {
    val rooms = linkedMapOf<Long, LiveRoom>()
    val followRooms = linkedMapOf<Long, LiveRoom>()
    val areaEntries = linkedMapOf<String, LiveFeedAreaEntry>()
    var followedLiveCount = 0

    data.cardList.orEmpty().forEach { card ->
        val type = card.cardType
        if (type in LIVE_FEED_IGNORED_CARD_TYPES) return@forEach

        when (type) {
            "small_card_v1" -> {
                val roomCard = card.cardData?.smallCardV1 ?: return@forEach
                if (!isUsableLiveFeedRoom(roomCard)) return@forEach
                val room = roomCard.toLiveRoom()
                rooms.putIfAbsent(room.roomid, room)
            }
            "my_idol_v1" -> {
                followedLiveCount = card.cardData?.myIdolV1?.extraInfo?.totalCount ?: 0
                card.cardData?.myIdolV1?.list.orEmpty().forEach { roomCard ->
                    if (!isUsableLiveFeedRoom(roomCard)) return@forEach
                    val room = roomCard.toLiveRoom()
                    followRooms.putIfAbsent(room.roomid, room)
                }
            }
            "area_entrance_v3" -> {
                card.cardData?.areaEntranceV3?.list.orEmpty().forEach { item ->
                    if (!isUsableLiveFeedAreaEntry(item)) return@forEach
                    val title = item.title.ifBlank { item.areaName }
                    val key = "${item.areaV2ParentId}:${item.areaV2Id}:$title"
                    areaEntries.putIfAbsent(
                        key,
                        LiveFeedAreaEntry(
                            title = title,
                            areaId = item.areaV2Id,
                            parentAreaId = item.areaV2ParentId,
                        )
                    )
                }
            }
            // Unknown types intentionally dropped.
            else -> Unit
        }
    }

    return LiveFeedHomeSnapshot(
        rooms = rooms.values.toList(),
        followRooms = followRooms.values.toList(),
        areaEntries = areaEntries.values.toList(),
        hasMore = data.hasMore == 1,
        totalCount = followedLiveCount.coerceAtLeast(followRooms.size),
    )
}

internal fun shouldKeepLiveSecondListRoom(room: LiveRoom): Boolean {
    if (room.roomid <= 0L) return false
    if (room.title.isBlank() && room.displayCover().isBlank()) return false
    return true
}
