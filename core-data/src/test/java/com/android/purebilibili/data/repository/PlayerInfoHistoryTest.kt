package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.PlayerInfoData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlayerInfoHistoryTest {
    @Test
    fun omittedOnlineCountStaysUnknownWhileServerZeroIsRetained() {
        assertNull(Json.decodeFromString<PlayerInfoData>("""{"cid":101}""").onlineCount)
        assertEquals(0, Json.decodeFromString<PlayerInfoData>("""{"cid":101,"online_count":0}""").onlineCount)
        assertEquals(123, Json.decodeFromString<PlayerInfoData>("""{"cid":101,"online_count":123}""").onlineCount)
    }

    @Test
    fun playerInfoRetainsTheServerHistoryPart() {
        val data = Json.decodeFromString<PlayerInfoData>("""{"cid":101,"last_play_cid":202,"last_play_time":125000}""")
        assertEquals(202L, data.lastPlayCid)
        assertEquals(125_000L, data.lastPlayTime)
    }

    @Test
    fun omittedHistoryIsDistinctFromNoPreviousPart() {
        assertNull(Json.decodeFromString<PlayerInfoData>("""{"cid":101}""").lastPlayCid)
        assertNull(Json.decodeFromString<PlayerInfoData>("""{"cid":101}""").lastPlayTime)
        assertEquals(0L, Json.decodeFromString<PlayerInfoData>("""{"cid":101,"last_play_time":0}""").lastPlayTime)
        assertEquals(0L, Json.decodeFromString<PlayerInfoData>("""{"cid":101,"last_play_cid":0}""").lastPlayCid)
    }

    @Test
    fun resumeProgressIsBoundToTheWatchedPartAndKeepsZeroDistinctFromUnknown() {
        val history = PlayerInfoData(lastPlayCid = 202L, lastPlayTime = 125_000L)
        assertEquals(125_000L, history.resumePositionMs(202L))
        assertNull(history.resumePositionMs(101L))
        assertNull(history.copy(lastPlayTime = null).resumePositionMs(202L))
        assertEquals(0L, history.copy(lastPlayTime = 0L).resumePositionMs(202L))
        assertEquals(0L, history.copy(lastPlayTime = -1L).resumePositionMs(202L))
    }
}
