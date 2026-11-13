package com.android.purebilibili.data.model.response

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DynamicDecorationFanTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decorationKeepsNumberLeadingZerosAndColor() {
        val decorate = json.decodeFromString<DecorateInfo>(
            """{"card_url":"https://example.com/card.png","fan":{"num_str":"000123","color":"#FA7298"}}""",
        )
        assertEquals("000123", decorate.fan?.displayNumber)
        assertEquals("#FA7298", decorate.fan?.color)
    }

    @Test
    fun descriptionIsUsedWhenNumberIsAbsentOrNull() {
        for (payload in listOf(
            """{"fan":{"num_desc":"001234"}}""",
            """{"fan":{"num_str":null,"num_desc":"001234"}}""",
            """{"fan":{"num_str":"","num_desc":"001234"}}""",
        )) {
            assertEquals("001234", json.decodeFromString<DecorateInfo>(payload).fan?.displayNumber)
        }
    }

    @Test
    fun ordinaryDecorationDoesNotInventAFanNumber() {
        assertNull(json.decodeFromString<DecorateInfo>("""{"card_url":"card.png"}""").fan)
        assertEquals("", json.decodeFromString<DecorateInfo>("""{"fan":{}}""").fan?.displayNumber)
    }
}
