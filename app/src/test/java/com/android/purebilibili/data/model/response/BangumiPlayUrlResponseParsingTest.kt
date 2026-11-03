package com.android.purebilibili.data.model.response

import com.android.purebilibili.data.repository.decodeBangumiPlayUrlPayload
import com.android.purebilibili.data.repository.shouldFallbackToLegacyBangumiPlayUrl
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

class BangumiPlayUrlResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `successful envelope preserves region restriction instead of parsing empty video info`() {
        for (envelope in listOf("data", "result")) {
            val payload = decodeBangumiPlayUrlPayload(
                """{
                    "code":0,"message":"0",
                    "$envelope":{
                        "video_info":null,
                        "not_playable_reason":{
                            "code":-10403,"message":"根据版权方要求，您所在的地区无法观看本片"
                        }
                    }
                }""",
                json
            )
            assertEquals(-10403, payload.code)
            assertEquals("根据版权方要求，您所在的地区无法观看本片", payload.message)
            assertEquals(null, payload.videoInfo)
            assertFalse(shouldFallbackToLegacyBangumiPlayUrl(payload))
        }
    }

    @Test
    fun `decodeBangumiPlayUrlPayload parses v2 video_info envelope`() {
        val payload = decodeBangumiPlayUrlPayload(
            """
            {
              "code": 0,
              "message": "0",
              "result": {
                "video_info": {
                  "fnver": 0,
                  "fnval": 12240,
                  "type": "DASH",
                  "quality": 80,
                  "format": "hdflv2",
                  "timelength": 123456,
                  "accept_quality": [80, 64, 32],
                  "accept_description": ["1080P", "720P", "480P"],
                  "support_formats": [
                    {
                      "quality": 127,
                      "format": "dash",
                      "display_desc": "8K",
                      "codecs": ["av01.0.08M.08"],
                      "need_login": true,
                      "need_vip": true
                    }
                  ],
                  "record_info": {
                    "record_icon": "",
                    "record": "登记号：TEST"
                  },
                  "dash": {
                    "duration": 123,
                    "video": [
                      {
                        "id": 80,
                        "base_url": "https://video.cdn/example-80.m4s",
                        "backup_url": []
                      }
                    ],
                    "audio": []
                  }
                }
              }
            }
            """.trimIndent(),
            json
        )

        assertEquals(0, payload.code)
        assertEquals("0", payload.message)
        val videoInfo = assertNotNull(payload.videoInfo)
        assertEquals(80, videoInfo.quality)
        assertEquals(12240, videoInfo.fnval)
        assertEquals(listOf(80, 64, 32), videoInfo.acceptQuality)
        assertEquals(80, videoInfo.dash?.video?.firstOrNull()?.id)
        assertEquals(listOf("av01.0.08M.08"), videoInfo.supportFormats?.single()?.codecs)
        assertEquals(true, videoInfo.supportFormats?.single()?.needVip)
        assertEquals("登记号：TEST", videoInfo.recordInfo?.record)
    }

    @Test
    fun `decodeBangumiPlayUrlPayload accepts mixed playback permission field types`() {
        val payload = decodeBangumiPlayUrlPayload(
            """
            {
              "code": 0,
              "message": "0",
              "result": {
                "quality": 80,
                "is_drm": 0,
                "has_paid": 1,
                "is_preview": "true",
                "status": false
              }
            }
            """.trimIndent(),
            json
        )

        val videoInfo = assertNotNull(payload.videoInfo)
        assertEquals(false, videoInfo.isDrm)
        assertEquals(true, videoInfo.hasPaid)
        assertEquals(true, videoInfo.isPreview)
        assertEquals(0, videoInfo.status)
    }

    @Test
    fun `decodeBangumiPlayUrlPayload still supports legacy direct result envelope`() {
        val payload = decodeBangumiPlayUrlPayload(
            """
            {
              "code": 0,
              "message": "0",
              "result": {
                "quality": 64,
                "format": "flv720",
                "timelength": 45678,
                "durl": [
                  {
                    "order": 1,
                    "url": "https://video.cdn/example-64.mp4"
                  }
                ]
              }
            }
            """.trimIndent(),
            json
        )

        assertEquals(0, payload.code)
        val videoInfo = assertNotNull(payload.videoInfo)
        assertEquals(64, videoInfo.quality)
        assertEquals("https://video.cdn/example-64.mp4", videoInfo.durl?.firstOrNull()?.url)
    }

    @Test
    fun `decodeBangumiPlayUrlPayload keeps empty result without crashing`() {
        val payload = decodeBangumiPlayUrlPayload(
            """
            {
              "code": 0,
              "message": "0",
              "result": null
            }
            """.trimIndent(),
            json
        )

        assertEquals(0, payload.code)
        assertEquals(null, payload.videoInfo)
    }

    @Test
    fun `decodeBangumiPlayUrlPayload keeps auth error code and message`() {
        val payload = decodeBangumiPlayUrlPayload(
            """
            {
              "code": -10403,
              "message": "大会员专享",
              "result": null
            }
            """.trimIndent(),
            json
        )

        assertEquals(-10403, payload.code)
        assertEquals("大会员专享", payload.message)
        assertEquals(null, payload.videoInfo)
    }

    @Test
    fun `decodeBangumiPlayUrlPayload supports pugv data envelope`() {
        val payload = decodeBangumiPlayUrlPayload(
            """
            {
              "code": 0,
              "message": "0",
              "data": {
                "quality": 80,
                "format": "flv720",
                "timelength": 584000,
                "accept_quality": [80, 64, 32],
                "dash": {
                  "duration": 584,
                  "video": [
                    {
                      "id": 80,
                      "base_url": "https://video.cdn/pugv-80.m4s",
                      "backup_url": []
                    }
                  ],
                  "audio": [
                    {
                      "id": 30280,
                      "base_url": "https://video.cdn/pugv-audio.m4s",
                      "backup_url": []
                    }
                  ]
                }
              }
            }
            """.trimIndent(),
            json
        )

        assertEquals(0, payload.code)
        val videoInfo = assertNotNull(payload.videoInfo)
        assertEquals(80, videoInfo.quality)
        assertEquals(584000L, videoInfo.timelength)
        assertEquals("https://video.cdn/pugv-80.m4s", videoInfo.dash?.video?.firstOrNull()?.baseUrl)
        assertEquals("https://video.cdn/pugv-audio.m4s", videoInfo.dash?.audio?.firstOrNull()?.baseUrl)
    }
}

