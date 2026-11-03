package com.android.purebilibili.data.model.response

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BangumiReviewResponseParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `long reviews preserve author identity rating and complete preview`() {
        val response = json.decodeFromString<BangumiReviewListResponse>(
            """{
              "code": 0,
              "data": {
                "total": 1689, "next": "next-page",
                "list": [{
                  "article_id": 1234, "review_id": 5678,
                  "title": "长评标题", "content": "完整长评摘要", "score": 10,
                  "push_time_str": "9月12日",
                  "author": {"mid": 99, "uname": "作者", "avatar": "https://example.com/avatar.jpg",
                    "level": 5, "vip": {"status": 1, "type": 2}},
                  "stat": {"likes": 16, "liked": 0, "disliked": 0}
                }]
              }
            }""",
        )
        val data = requireNotNull(response.data)
        val item = data.list.single()
        assertNull(data.count)
        assertEquals(1689, data.total)
        assertEquals(1234L, item.article_id)
        assertEquals("完整长评摘要", item.content)
        assertEquals(10, item.score)
        assertEquals(5, item.author?.level)
        assertEquals(BangumiReviewVip(status = 1, type = 2), item.author?.vip)
        assertEquals(16, item.stat?.likes)
    }

    @Test
    fun `missing and null review counts remain unknown while actual zero is preserved`() {
        for (fields in listOf("", "\"count\":null,\"total\":null,")) {
            val data = json.decodeFromString<BangumiReviewListData>("{$fields\"list\":[]}")
            assertNull(data.count)
            assertNull(data.total)
        }
        val empty = json.decodeFromString<BangumiReviewListData>("""{"count":0,"total":0,"list":[]}""")
        assertEquals(0, empty.count)
        assertEquals(0, empty.total)
    }
}
