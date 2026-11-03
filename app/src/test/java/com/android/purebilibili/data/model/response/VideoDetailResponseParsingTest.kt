package com.android.purebilibili.data.model.response

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VideoDetailResponseParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `decode video detail preserves bangumi redirect without a playable cid`() {
        val response = json.decodeFromString<VideoDetailResponse>(
            """{"code":0,"data":{"redirect_url":"https://www.bilibili.com/bangumi/play/ep123"}}"""
        )
        val info = assertNotNull(response.data)
        assertEquals("https://www.bilibili.com/bangumi/play/ep123", info.redirectUrl)
        assertEquals(0L, info.cid)
    }

    @Test
    fun `decode video detail exposes staff cooperation and upower exclusive fields`() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "bvid": "BV1staff",
                "aid": 123,
                "cid": 456,
                "title": "联合投稿示例",
                "is_upower_exclusive": true,
                "is_upower_play": false,
                "is_upower_preview": true,
                "is_upower_exclusive_with_qa": false,
                "rights": {
                  "elec": 1,
                  "is_cooperation": 1
                },
                "staff": [
                  {
                    "mid": 101,
                    "title": "UP主",
                    "name": "Lucky-101",
                    "face": "https://example.com/up.jpg",
                    "follower": 6011,
                    "label_style": 1,
                    "vip": {
                      "type": 2,
                      "status": 1,
                      "due_date": 1893456000000,
                      "vip_pay_type": 1
                    },
                    "official": {
                      "role": 1,
                      "title": "认证",
                      "desc": "认证信息",
                      "type": 0
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<VideoDetailResponse>(payload)
        val info = response.data
        assertNotNull(info)

        assertTrue(info.isUpowerExclusive)
        assertTrue(info.isUpowerPreview)
        assertEquals(1, info.rights.isCooperation)
        assertEquals(1, info.rights.elec)
        assertTrue(info.isCooperation)
        assertEquals(1, info.staff.size)
        assertEquals(101L, info.staff.first().mid)
        assertEquals("Lucky-101", info.staff.first().name)
        assertEquals("UP主", info.staff.first().title)
        assertEquals(6011, info.staff.first().follower)
        assertEquals(2, info.staff.first().vip.type)
        assertEquals("认证", info.staff.first().official.title)
    }

    @Test
    fun `decode ugc season episode arc keeps publish timestamps`() {
        val payload = """
            {
              "code": 0,
              "message": "0",
              "data": {
                "bvid": "BV1collection",
                "ugc_season": {
                  "id": 725909,
                  "title": "洞穴探险",
                  "sections": [
                    {
                      "season_id": 725909,
                      "episodes": [
                        {
                          "id": 1,
                          "aid": 1001,
                          "bvid": "BV1ep",
                          "cid": 2001,
                          "title": "潜水抓鱼",
                          "arc": {
                            "aid": 1001,
                            "pic": "https://example.com/cover.jpg",
                            "title": "潜水抓鱼",
                            "duration": 755,
                            "pubdate": 1715427472,
                            "ctime": 1715427000
                          }
                        }
                      ]
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<VideoDetailResponse>(payload)
        val arc = response.data?.ugc_season?.sections
            ?.firstOrNull()
            ?.episodes
            ?.firstOrNull()
            ?.arc

        assertNotNull(arc)
        assertEquals(1715427472L, arc.pubdate)
        assertEquals(1715427000L, arc.ctime)
    }
}
