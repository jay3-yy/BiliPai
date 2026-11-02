package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.SpaceAggregateData
import com.android.purebilibili.data.model.response.SpaceMedalWallResponse
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class SpaceProfileExtrasMappingTest {
    @Test
    fun webUploadTagsKeepPartitionChargingAndUnknownCountsDistinct() {
        val data = Json.decodeFromString<com.android.purebilibili.data.model.response.SpaceVideoData>("""{
          "list":{"vlist":[],"tags":[
            {"tid":17,"name":"单机游戏","count":0},
            {"tid":0,"name":"充电专属","count":31,"special_type":"charging"},
            {"tid":3,"name":"音乐"}
          ]},"page":{"pn":2,"ps":30,"count":31}
        }""")
        assertEquals(0, data.list.tags[0].count)
        assertEquals(17, data.list.tags[0].tid)
        assertEquals("charging", data.list.tags[1].specialType)
        assertEquals(null, data.list.tags[2].count)
        assertEquals(31, data.page.count)
        assertEquals(2, data.page.pn)
    }

    @Test
    fun reservationsKeepTheirOwnIdentityStateDynamicAndLotteryTarget() {
        val data = Json.decodeFromString<SpaceAggregateData>("""{
          "medal":1,"reservation_card_list":[{"sid":123,"name":"直播预约","total":42,"is_follow":1,
          "desc_text_1":{"text":"明晚八点"},"dynamic_id":"987654321012345678",
          "lottery_prize_info":{"text":"预约抽奖","jump_url":"https://t.bilibili.com/987654321012345678"}}]
        }""")
        val reservation = data.reservations.single()
        assertEquals(123L, reservation.sid)
        assertEquals(1, reservation.isFollow)
        assertEquals(42L, reservation.total)
        assertEquals("明晚八点", reservation.description?.text)
        assertEquals("987654321012345678", reservation.dynamicId)
        assertEquals("https://t.bilibili.com/987654321012345678", reservation.lottery?.jumpUrl)
        assertEquals(1, data.medal)
    }

    @Test
    fun medalWallRetainsWornMedalOwnerAndLiveLink() {
        val response = Json.decodeFromString<SpaceMedalWallResponse>("""{"data":{"count":1,"name":"UP",
          "list":[{"target_name":"主播","link":"https://live.bilibili.com/88","live_status":1,
          "medal_info":{"wearing_status":1},"uinfo_medal":{"id":5,"ruid":42,"name":"粉丝","level":20}}]}}""")
        val medal = response.data!!.list.single()
        assertEquals(42L, medal.medal?.ruid)
        assertEquals(20, medal.medal?.level)
        assertEquals(1, medal.medalInfo?.wearingStatus)
        assertEquals("https://live.bilibili.com/88", medal.link)
    }
}
