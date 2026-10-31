package com.android.purebilibili.feature.dynamic.visibility

import com.android.purebilibili.data.model.response.DynamicCreateFeedResponse
import com.android.purebilibili.data.model.response.resolveCreatedDynamicId
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicVisibilityCheckPolicyTest {
    @Test
    fun `only a successful matching detail confirms anonymous visibility`() {
        assertEquals(DynamicVisibilityStatus.VISIBLE, resolveDynamicVisibilityStatus(0, true))
        assertEquals(DynamicVisibilityStatus.UNKNOWN, resolveDynamicVisibilityStatus(0, false))
        assertEquals(DynamicVisibilityStatus.UNAVAILABLE, resolveDynamicVisibilityStatus(-404, false))
        listOf(-352, -412, -403, 500).forEach { code ->
            assertEquals(DynamicVisibilityStatus.UNKNOWN, resolveDynamicVisibilityStatus(code, false))
        }
    }

    @Test
    fun `private scheduled and unidentified publications are not checked immediately`() {
        assertTrue(shouldCheckPublishedDynamic("123", false, false))
        assertFalse(shouldCheckPublishedDynamic("123", true, false))
        assertFalse(shouldCheckPublishedDynamic("123", false, true))
        assertFalse(shouldCheckPublishedDynamic("", false, false))
    }

    @Test
    fun `repost response preserves newly created id across server response variants`() {
        val json = Json { ignoreUnknownKeys = true }
        listOf(
            """{"code":0,"data":{"dyn_id_str":"123456"}}""",
            """{"code":0,"data":{"dynamic_id_str":"123456"}}""",
            """{"code":0,"data":{"dyn_id":123456}}""",
        ).forEach { payload ->
            val response = json.decodeFromString<DynamicCreateFeedResponse>(payload)
            assertEquals("123456", resolveCreatedDynamicId(response.data))
        }
        assertEquals("", resolveCreatedDynamicId(json.decodeFromString<DynamicCreateFeedResponse>("""{"code":0}""").data))
    }
}
