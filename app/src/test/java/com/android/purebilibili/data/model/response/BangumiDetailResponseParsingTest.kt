package com.android.purebilibili.data.model.response

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BangumiDetailResponseParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `decode detail keeps publish payment rights and section fields`() {
        val response = json.decodeFromString<BangumiDetailResponse>(
            """
            {
              "code": 0,
              "message": "success",
              "result": {
                "season_id": 123,
                "media_id": 456,
                "title": "测试番剧",
                "cover": "https://example.com/cover.jpg",
                "season_type": 1,
                "season_type_name": "番剧",
                "styles": ["原创", "热血"],
                "areas": [{"id": 2, "name": "日本"}],
                "publish": {
                  "is_finish": 1,
                  "is_started": 1,
                  "pub_time": "2026-01-01 00:00:00",
                  "pub_time_show": "2026年1月开播"
                },
                "payment": {
                  "price": "6",
                  "tip": "付费观看",
                  "vip_price": "3"
                },
                "rights": {
                  "allow_download": 1,
                  "is_preview": 1,
                  "area_limit": 0
                },
                "positive": {
                  "id": 1,
                  "title": "正片"
                },
                "section": [
                  {
                    "id": 9,
                    "title": "PV",
                    "episodes": [
                      {
                        "id": 1001,
                        "aid": 2001,
                        "cid": 3001,
                        "title": "PV1",
                        "long_title": "正式预告"
                      }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val detail = requireNotNull(response.result)
        assertEquals("2026年1月开播", detail.publish?.pubTimeShow)
        assertEquals("付费观看", detail.payment?.tip)
        assertEquals(1, detail.rights?.isPreview)
        assertEquals("正片", detail.positive?.title)
        assertEquals("PV", detail.section?.single()?.title)
        assertEquals(1001L, detail.section?.single()?.episodes?.single()?.id)
    }

    @Test
    fun `decode detail tolerates missing optional extension fields`() {
        val response = json.decodeFromString<BangumiDetailResponse>(
            """
            {
              "code": 0,
              "result": {
                "season_id": 123,
                "title": "无扩展字段"
              }
            }
            """.trimIndent()
        )

        val detail = requireNotNull(response.result)
        assertEquals(123L, detail.seasonId)
        assertEquals(null, detail.publish)
        assertEquals(null, detail.section)
    }
    @Test
    fun `collection count is independent from follow count and preserves unknown`() {
        val stat = json.decodeFromString<BangumiStat>("""{"favorites":120,"favorite":34}""")
        assertEquals(120L, stat.favorites)
        assertEquals(34L, requireNotNull(stat.favorite))
        assertEquals(0L, json.decodeFromString<BangumiStat>("""{"favorite":0}""").favorite)
        assertEquals(null, json.decodeFromString<BangumiStat>("""{"favorites":120}""").favorite)
    }

    @Test
    fun `episode community decodes all three interaction states`() {
        val response = json.decodeFromString<BangumiEpisodeCommunityResponse>(
            """{"code":0,"data":{"like":1,"favorite":0,"coin_number":2}}"""
        )
        assertEquals(1, response.data?.like)
        assertEquals(0, response.data?.favorite)
        assertEquals(2.0, requireNotNull(response.data?.coinNumber), 0.0)
    }

}
