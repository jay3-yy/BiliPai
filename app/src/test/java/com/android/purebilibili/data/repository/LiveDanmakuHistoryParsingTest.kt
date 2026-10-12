package com.android.purebilibili.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiveDanmakuHistoryParsingTest {

    @Test
    fun `history parser ignores blank emoticon url so normal text stays visible`() {
        val result = parseLiveDanmakuHistoryItems(
            """
            {
              "code": 0,
              "data": {
                "room": [
                  {
                    "text": "赛事",
                    "emoticon": {
                      "url": ""
                    },
                    "user": {
                      "uid": 42,
                      "base": {
                        "name": "呆头鹅皮"
                      }
                    },
                    "check_info": {
                      "ts": 10,
                      "ct": "report-sign"
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val item = result.getOrThrow().single()
        assertEquals("呆头鹅皮", item.uname)
        assertEquals("赛事", item.text)
        assertNull(item.emoticonUrl)
    }
    @Test
    fun `history preserves reply identity for profile navigation`() {
        val result = parseLiveDanmakuHistoryItems(
            """{"code":0,"data":{"room":[
                {"text":"回复","reply":{"reply_uname":"Carol","reply_mid":"123"}},
                {"text":"普通消息"}
            ]}}"""
        ).getOrThrow()
        assertEquals("Carol", result[0].replyToName)
        assertEquals(123L, result[0].replyToUid)
        assertEquals(0L, result[1].replyToUid)
    }

}
