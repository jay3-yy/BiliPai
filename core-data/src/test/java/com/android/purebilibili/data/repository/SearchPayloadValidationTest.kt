package com.android.purebilibili.data.repository

import com.android.purebilibili.data.model.response.SearchAllResponse
import com.android.purebilibili.data.model.response.SearchUpResponse
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SearchPayloadValidationTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `successful code carrying a risk voucher is not an empty successful search`() {
        val payload = """{"code":0,"data":{"v_voucher":"voucher_search_challenge"}}"""
        val all = json.decodeFromString<SearchAllResponse>(payload)
        val users = json.decodeFromString<SearchUpResponse>(payload)
        assertFailsWith<IllegalStateException> { requireSearchPayload(all.data != null, all.data?.v_voucher) }
        assertFailsWith<IllegalStateException> { requireSearchPayload(users.data != null, users.data?.v_voucher) }
        assertFailsWith<IllegalStateException> { requireSearchPayload(false, null) }
        requireSearchPayload(true, null)
        requireSearchPayload(true, "")
    }
}
