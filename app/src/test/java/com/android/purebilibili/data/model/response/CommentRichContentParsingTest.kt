package com.android.purebilibili.data.model.response

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommentRichContentParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun decodeReplyResponse_exposesRichContentMetadata() {
        val payload = """
            {
              "code": 0,
              "data": {
                "replies": [
                  {
                    "rpid": 1,
                    "mid": 2,
                    "ctime": 1700000000,
                    "member": {
                      "mid": "2",
                      "uname": "测试用户"
                    },
                    "reply_control": {
                      "location": "IP属地：上海",
                      "is_up_top": true,
                      "up_reply": 1
                    },
                    "note_cvid_str": "123456",
                    "content": {
                      "message": "你好 @路人 看 #动画# https://b23.tv/demo",
                      "vote": {
                        "id": "987",
                        "title": "投票标题",
                        "count": 12
                      },
                      "rich_text": {
                        "note": {
                          "summary": "笔记摘要",
                          "click_url": "https://www.bilibili.com/h5/note-app/view?cvid=123456",
                          "last_mtime_text": "刚刚"
                        },
                        "opus": {
                          "opus_id": "112233"
                        }
                      },
                      "at_name_to_mid": {
                        "路人": "12345"
                      },
                      "topics": {
                        "动画": {
                          "id": 1
                        }
                      },
                      "jump_url": {
                        "https://b23.tv/demo": {
                          "title": "UP主空间",
                          "appUrlSchema": "bilibili://space/495695169",
                          "prefixIcon": "https://example.com/icon.png",
                          "pc_url": "//space.bilibili.com/495695169"
                        }
                      }
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val reply = json.decodeFromString<ReplyResponse>(payload)
            .data
            ?.replies
            ?.firstOrNull()
        assertNotNull(reply)

        assertEquals("IP属地：上海", reply.replyControl?.location)
        assertEquals(true, reply.replyControl?.isUpTop)
        assertEquals(true, reply.replyControl?.upReply)
        assertEquals("123456", reply.noteCvidStr)

        val content = reply.content
        assertEquals(987L, content.vote?.id)
        assertEquals("投票标题", content.vote?.title)
        assertEquals(12L, content.vote?.count)
        assertEquals("笔记摘要", content.richText.note?.summary)
        assertEquals(
            "https://www.bilibili.com/h5/note-app/view?cvid=123456",
            content.richText.note?.clickUrl
        )
        assertEquals(112233L, content.richText.opus?.opusId)
        assertEquals(12345L, content.atNameToMid["路人"])
        assertTrue(content.topics.containsKey("动画"))

        val url = content.urls["https://b23.tv/demo"]
        assertNotNull(url)
        assertEquals("UP主空间", url.title)
        assertEquals("bilibili://space/495695169", url.appUrlSchema)
        assertEquals("https://example.com/icon.png", url.prefixIcon)
        assertEquals("//space.bilibili.com/495695169", url.url)
    }
}
