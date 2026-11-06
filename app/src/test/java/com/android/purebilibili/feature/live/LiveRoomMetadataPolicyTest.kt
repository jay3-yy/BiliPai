package com.android.purebilibili.feature.live

import kotlin.test.Test
import kotlin.test.assertEquals

class LiveRoomMetadataPolicyTest {
    @Test
    fun lateDetailsPreserveRealtimeChangesAndStillFillAnchorRoomContent() {
        val published = RoomInfo(roomId = 7, title = "进入时标题", watchedText = "10人看过")
        val current = published.copy(title = "主播刚改的新标题", watchedText = "20人看过", onlineRankText = "高能观众(3)")
        val loaded = published.copy(title = "详情中的旧标题", watchedText = "12人看过", cover = "cover", description = "简介")
        val merged = mergeLiveRoomMetadata(current, published, loaded)
        assertEquals(current.title, merged.title)
        assertEquals(current.watchedText, merged.watchedText)
        assertEquals(current.onlineRankText, merged.onlineRankText)
        assertEquals(loaded.cover, merged.cover)
        assertEquals(loaded.description, merged.description)
    }

    @Test
    fun unchangedRoomReceivesFreshDetailsOnReload() {
        val published = RoomInfo(roomId = 7, title = "之前标题", watchedText = "10人看过")
        val loaded = published.copy(title = "新的详情标题", watchedText = "30人看过")
        assertEquals(loaded, mergeLiveRoomMetadata(published, published, loaded))
    }
}
