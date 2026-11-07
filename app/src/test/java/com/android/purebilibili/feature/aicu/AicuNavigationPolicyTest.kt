package com.android.purebilibili.feature.aicu

import com.android.purebilibili.data.model.response.AicuCategory
import com.android.purebilibili.data.model.response.AicuRecord
import com.android.purebilibili.navigation3.*
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*

class AicuNavigationPolicyTest {
    @Test fun `every query category round trips including standalone and restored navigation`() {
        val json = Json { ignoreUnknownKeys = true }
        for (category in AicuCategory.entries) {
            val key = BiliPaiNavKey.AicuQuery(2L, category.name)
            assertEquals(key, legacyRouteToBiliPaiNavKey(key.toLegacyRoute()))
            assertEquals(BiliPaiNavEntryContentRole.AICU_QUERY, resolveBiliPaiNavEntryContentRole(key))
            assertEquals(key, json.decodeFromString(BiliPaiNavKey.serializer(), json.encodeToString(BiliPaiNavKey.serializer(), key)))
        }
        assertEquals(BiliPaiNavKey.AicuQuery(), legacyRouteToBiliPaiNavKey("aicu"))
        assertEquals(BiliPaiNavKey.AicuQuery(), legacyRouteToBiliPaiNavKey("aicu?uid=-1&category=unknown"))
    }

    @Test fun `video and dynamic comments carry both root and target IDs`() {
        val comment = AicuRecord("310971854512", "text", 1L, "116968233375300", 1, "306765041841", 2)
        assertEquals(BiliPaiNavKey.VideoDetail("av116968233375300", commentRootRpid = 306765041841, commentTargetRpid = 310971854512),
            aicuNativeTarget(AicuCategory.COMMENT, comment))
        val dynamic = comment.copy(objectId = "1226750626638069766", objectType = 17)
        assertEquals(BiliPaiNavKey.DynamicDetail("1226750626638069766", 306765041841, 310971854512),
            aicuNativeTarget(AicuCategory.COMMENT, dynamic))
        val root = aicuNativeTarget(AicuCategory.COMMENT, comment.copy(rank = 1)) as BiliPaiNavKey.VideoDetail
        assertEquals(310971854512, root.commentRootRpid)
    }

    @Test fun `video danmaku IDs must not become comment anchors`() {
        val target = aicuNativeTarget(AicuCategory.VIDEO_DANMAKU, AicuRecord("123", "dm", 1, "456", 1, progressMs = 1000)) as BiliPaiNavKey.VideoDetail
        assertEquals(0L, target.commentTargetRpid)
        assertEquals("av456", target.bvid)
    }

    @Test fun `child comment without root preserves its target for server resolution`() {
        val comment = AicuRecord("123", "reply", 1L, "456", 1, rank = 2)
        assertEquals(
            BiliPaiNavKey.VideoDetail("av456", commentTargetRpid = 123L),
            aicuNativeTarget(AicuCategory.COMMENT, comment),
        )
        assertEquals(
            BiliPaiNavKey.DynamicDetail("456", commentTargetRpid = 123L),
            aicuNativeTarget(AicuCategory.COMMENT, comment.copy(objectType = 17)),
        )
        assertEquals(
            BiliPaiNavKey.ArticleDetail(456L, commentTargetRpid = 123L),
            aicuNativeTarget(AicuCategory.COMMENT, comment.copy(objectType = 12)),
        )
    }

    @Test fun `live and articles use native destinations while unsupported records never open a web page`() {
        assertEquals(BiliPaiNavKey.Live(roomId = "42", title = "room", uname = "up"),
            aicuNativeTarget(AicuCategory.LIVE_DANMAKU, AicuRecord("", "dm", 1, roomId = "42", roomName = "room", upName = "up")))
        assertEquals(BiliPaiNavKey.ArticleDetail(10, commentTargetRpid = 1L), aicuNativeTarget(AicuCategory.COMMENT, AicuRecord("1", "text", 1, "10", 12)))
        assertNull(aicuNativeTarget(AicuCategory.COMMENT, AicuRecord("1", "text", 1, "10", 11)))
        assertNull(aicuNativeTarget(AicuCategory.COMMENT, AicuRecord("1", "text", 1, "https://example.com", 1)))
        assertNull(aicuNativeTarget(AicuCategory.LIVE_DANMAKU, AicuRecord("1", "text", 1, roomId = "0")))
    }
}
