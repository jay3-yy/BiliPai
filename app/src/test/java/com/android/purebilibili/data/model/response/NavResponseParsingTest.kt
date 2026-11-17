package com.android.purebilibili.data.model.response

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NavResponseParsingTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `level info parses max level next_exp placeholder`() {
        // 满级用户 B 站 API 返回 "next_exp": "--"，此前会导致整个 NavResponse 解析失败
        val response = json.decodeFromString(
            NavResponse.serializer(),
            """
            {
              "code": 0,
              "message": "0",
              "data": {
                "isLogin": true,
                "uname": "满级大佬",
                "is_senior_member": 1,
                "mid": 74823629,
                "level_info": {
                  "current_level": 6,
                  "current_min": 28800,
                  "current_exp": 57063,
                  "next_exp": "--"
                }
              }
            }
            """.trimIndent()
        )

        val data = assertNotNull(response.data)
        assertEquals(6, data.level_info.current_level)
        assertEquals(1, data.is_senior_member)
        assertEquals(28800, data.level_info.current_min)
        assertEquals(57063, data.level_info.current_exp)
        assertEquals(0, data.level_info.next_exp)
    }

    @Test
    fun `level info parses numeric exp`() {
        val response = json.decodeFromString(
            NavResponse.serializer(),
            """
            {
              "code": 0,
              "data": {
                "isLogin": true,
                "level_info": {
                  "current_level": 4,
                  "current_min": 4500,
                  "current_exp": 7200,
                  "next_exp": 10800
                }
              }
            }
            """.trimIndent()
        )

        val data = assertNotNull(response.data)
        assertEquals(4, data.level_info.current_level)
        assertEquals(0, data.is_senior_member)
        assertEquals(7200, data.level_info.current_exp)
        assertEquals(10800, data.level_info.next_exp)
    }

    @Test
    fun `level info parses missing exp fields`() {
        val response = json.decodeFromString(
            NavResponse.serializer(),
            """
            {
              "code": 0,
              "data": {
                "isLogin": false,
                "level_info": {
                  "current_level": 0
                }
              }
            }
            """.trimIndent()
        )

        val data = assertNotNull(response.data)
        assertEquals(0, data.level_info.current_min)
        assertEquals(0, data.level_info.current_exp)
        assertEquals(0, data.level_info.next_exp)
    }
}
