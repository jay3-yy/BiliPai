package com.android.purebilibili.core.network.grpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DynamicPersonalFeedApiTest {
    @Test
    fun laterPagesStillExplicitlySelectPreloadRatherThanProto3ReadDefault() {
        val fields = ProtoWire.parseFields(buildDynamicPersonalFeedRequest(42L, "opaque-cursor", 2))
        assertEquals(1L, fields.single { it.number == 4 }.varint)
    }

    @Test
    fun personalReplyKeepsDynamicIdentityVideoTargetAndUnspecifiedFollowingState() {
        val video = ProtoWire.message(
            ProtoWire.string(1, "Video title"), ProtoWire.int64(7, 123L),
            ProtoWire.string(21, "https://www.bilibili.com/video/BV1xx411c7mD"),
            ProtoWire.string(26, "BV1xx411c7mD"),
        )
        val item = card("18446744073709551615", 2, ProtoWire.bytes(2, video))
        val reply = ProtoWire.message(
            ProtoWire.bytes(1, item), ProtoWire.string(2, "next-page"), ProtoWire.bool(3, true),
            ProtoWire.string(4, "18446744073709551615"), ProtoWire.string(63, "future field"),
        )
        val page = parseDynamicPersonalFeedReply(reply)
        val parsed = page.items.single()
        assertEquals("18446744073709551615", parsed.id_str)
        assertEquals(42L, parsed.modules.module_author?.mid)
        assertEquals("Author", parsed.modules.module_author?.name)
        // attend=0 belongs to likes lists and must not suppress all personal-feed notifications.
        assertNull(parsed.modules.module_author?.following)
        assertEquals("DYNAMIC_TYPE_AV", parsed.type)
        assertEquals("BV1xx411c7mD", parsed.modules.module_dynamic?.major?.archive?.bvid)
        assertEquals("123", parsed.modules.module_dynamic?.major?.archive?.aid)
        assertEquals("Video title", parsed.modules.module_dynamic?.major?.archive?.title)
        assertEquals("next-page", page.offset)
        assertTrue(page.hasMore)
    }

    @Test
    fun forwardedCardsKeepTheNewPostIdSeparateFromTheOriginalVideoId() {
        val original = card("100", 2, ProtoWire.bytes(2, ProtoWire.message(
            ProtoWire.string(1, "Original video"), ProtoWire.string(26, "BV1xx411c7mD"),
        )))
        val forward = card("200", 1, ProtoWire.bytes(6, ProtoWire.message(ProtoWire.bytes(1, original))), "100")
        val parsed = parseDynamicPersonalFeedReply(ProtoWire.message(ProtoWire.bytes(1, forward))).items.single()
        assertEquals("200", parsed.id_str)
        assertEquals("DYNAMIC_TYPE_FORWARD", parsed.type)
        assertEquals("100", parsed.orig?.id_str)
        assertEquals("BV1xx411c7mD", parsed.orig?.modules?.module_dynamic?.major?.archive?.bvid)
    }

    @Test
    fun foldedSourceMetadataStillKeepsItsActualOriginalDynamicTarget() {
        val forward = card("200", 1, null, "100")
        val parsed = parseDynamicPersonalFeedReply(ProtoWire.message(ProtoWire.bytes(1, forward))).items.single()
        assertEquals("200", parsed.id_str)
        assertEquals("100", parsed.orig?.id_str)
        assertEquals("DYNAMIC_TYPE_WORD", parsed.orig?.type)
    }

    @Test
    fun pgcAndArticleUseTheirOwnProtocolTargetFields() {
        val pgc = card("200", 3, ProtoWire.bytes(3, ProtoWire.message(
            ProtoWire.string(1, "Episode"), ProtoWire.int64(8, 1000L), ProtoWire.int64(9, 2000L),
        )))
        val article = card("300", 8, ProtoWire.bytes(8, ProtoWire.message(
            ProtoWire.int64(1, 1234L), ProtoWire.string(2, "https://www.bilibili.com/read/cv1234"),
            ProtoWire.string(3, "Article"),
        )))
        val items = parseDynamicPersonalFeedReply(ProtoWire.message(
            ProtoWire.bytes(1, pgc), ProtoWire.bytes(1, article),
        )).items
        assertEquals(1000L, items[0].modules.module_dynamic?.major?.pgc?.season_id)
        assertEquals(2000L, items[0].modules.module_dynamic?.major?.pgc?.epid)
        assertEquals(1234L, items[1].modules.module_dynamic?.major?.article?.id)
        assertEquals("Article", items[1].modules.module_dynamic?.major?.article?.title)
    }

    @Test
    fun opusParagraphsKeepNotificationTitleAndBodyAcrossBothTextNodeForms() {
        val title = ProtoWire.message(ProtoWire.bytes(3, ProtoWire.message(ProtoWire.bytes(1, ProtoWire.message(
            ProtoWire.bytes(3, ProtoWire.message(ProtoWire.string(1, "Long post"))),
        )))))
        val summary = ProtoWire.message(ProtoWire.bytes(3, ProtoWire.message(ProtoWire.bytes(1,
            ProtoWire.message(ProtoWire.string(2, "Post body"))))))
        val item = card("200", 6, null) + ProtoWire.bytes(3, ProtoWire.message(ProtoWire.bytes(30,
            ProtoWire.message(ProtoWire.bytes(1, title), ProtoWire.bytes(2, summary)))))
        val parsed = parseDynamicPersonalFeedReply(ProtoWire.message(ProtoWire.bytes(1, item))).items.single()
        assertEquals("Long post", parsed.modules.module_dynamic?.major?.opus?.title)
        assertEquals("Post body", parsed.modules.module_dynamic?.desc?.text)
    }

    @Test
    fun blockedCardsCannotBecomeRenderableNotificationCandidates() {
        val item = card("200", 6, null) + ProtoWire.bytes(3, ProtoWire.message(
            ProtoWire.bytes(33, ProtoWire.message(ProtoWire.string(2, "restricted"))),
        ))
        val parsed = parseDynamicPersonalFeedReply(ProtoWire.message(ProtoWire.bytes(1, item))).items.single()
        assertFalse(parsed.visible)
    }

    @Test
    fun standalonePinnedTagSurvivesAnAuthorModuleWithDefaultIsTop() {
        val top = ProtoWire.bytes(3, ProtoWire.message(ProtoWire.bytes(25,
            ProtoWire.message(ProtoWire.string(1, "置顶")))))
        val item = top + card("100", 6, null)
        val parsed = parseDynamicPersonalFeedReply(ProtoWire.message(ProtoWire.bytes(1, item))).items.single()
        assertEquals("置顶", parsed.modules.module_tag?.text)
    }

    @Test
    fun missingPaginationCursorFailsInsteadOfCommittingAnIncompleteRange() {
        try {
            parseDynamicPersonalFeedReply(ProtoWire.message(ProtoWire.bool(3, true)))
            fail("An incomplete range must not be accepted")
        } catch (_: IllegalStateException) {
            // Expected: the poller retains the last successful baseline.
        }
    }

    private fun card(id: String, type: Int, media: ByteArray?, originalId: String = ""): ByteArray {
        val fields = mutableListOf(
            ProtoWire.int32(1, type),
            ProtoWire.bytes(4, ProtoWire.message(
                ProtoWire.string(1, id), ProtoWire.string(3, originalId),
                ProtoWire.int32(8, 6), ProtoWire.int64(14, 42L),
            )),
            ProtoWire.bytes(3, ProtoWire.message(ProtoWire.bytes(2, ProtoWire.message(
                ProtoWire.int64(1, 42L), ProtoWire.int32(9, 0),
                ProtoWire.bytes(3, ProtoWire.message(ProtoWire.int64(1, 42L), ProtoWire.string(2, "Author"))),
            )))),
        )
        if (media != null) fields += ProtoWire.bytes(3, ProtoWire.message(ProtoWire.bytes(5, media)))
        return ProtoWire.message(*fields.toTypedArray())
    }
}
