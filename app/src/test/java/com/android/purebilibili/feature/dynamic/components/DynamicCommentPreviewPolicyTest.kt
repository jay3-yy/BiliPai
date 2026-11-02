package com.android.purebilibili.feature.dynamic.components

import com.android.purebilibili.core.util.BilibiliNavigationTarget
import com.android.purebilibili.data.model.response.DynamicBasic
import com.android.purebilibili.data.model.response.DynamicDesc
import com.android.purebilibili.data.model.response.DynamicInteractionItem
import com.android.purebilibili.data.model.response.DynamicInteractionModule
import com.android.purebilibili.data.model.response.DynamicModules
import com.android.purebilibili.data.model.response.DynamicItem
import com.android.purebilibili.data.model.response.SpaceDynamicItem
import com.android.purebilibili.data.model.response.RichTextNode
import com.android.purebilibili.feature.dynamic.resolveDynamicCommentTarget
import com.android.purebilibili.feature.space.resolveSpaceDynamicCardItem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class DynamicCommentPreviewPolicyTest {
    private val dynamic = DynamicItem(id_str = "123")
    private val comment = DynamicInteractionItem(
        type = 1, desc = DynamicDesc(text = "相关评论"), extend_click_param = mapOf("comment_root_id" to "701"),
    )
    private val like = DynamicInteractionItem(type = 2, desc = DynamicDesc(text = "UP 主觉得很赞"))

    private fun visible(items: List<DynamicInteractionItem>, commentPreviewVisible: Boolean) =
        resolveVisibleDynamicInteractionItems(
            dynamic.copy(modules = DynamicModules(module_interaction = DynamicInteractionModule(items))),
            commentPreviewVisible,
        ).map { it.item }

    @Test
    fun disabledCommentPreviewKeepsLikeHints() {
        assertEquals(
            listOf(like),
            visible(listOf(comment, like), commentPreviewVisible = false),
        )
    }

    @Test
    fun enabledCommentPreviewKeepsOriginalOrder() {
        val items = listOf(like, comment)

        assertEquals(items, visible(items, commentPreviewVisible = true))
    }

    @Test
    fun commentOnlyBarHasNoVisibleItemsWhenDisabled() {
        assertTrue(
            visible(listOf(comment), commentPreviewVisible = false).isEmpty(),
        )
    }

    @Test
    fun missingAndBlankDescriptionsDoNotLeaveEmptyBars() {
        val items = listOf(
            DynamicInteractionItem(type = 1),
            DynamicInteractionItem(type = 2, desc = DynamicDesc(text = " ")),
        )

        assertTrue(visible(items, commentPreviewVisible = true).isEmpty())
        assertTrue(visible(items, commentPreviewVisible = false).isEmpty())
    }

    @Test
    fun nodeOnlyCommentsCanBeShown() {
        val nodeOnlyComment = DynamicInteractionItem(
            type = 1,
            desc = DynamicDesc(rich_text_nodes = listOf(RichTextNode(type = "TEXT", text = "评论内容"))),
            extend_click_param = mapOf("comment_root_id" to "701"),
        )

        assertEquals(
            listOf(nodeOnlyComment),
            visible(listOf(nodeOnlyComment), commentPreviewVisible = true),
        )
    }

    @Test
    fun atRidIsAUserIdAndDoesNotBecomeACommentLocation() {
        val item = comment.copy(extend_click_param = emptyMap(), desc = DynamicDesc(rich_text_nodes = listOf(
            RichTextNode(type = "AT", text = "@用户", rid = "4422"),
        )))

        assertEquals(
            BilibiliNavigationTarget.Dynamic("123", openComments = true),
            resolveDynamicCommentPreviewTarget(dynamic, item),
        )
        assertNull(resolveDynamicCommentPreviewTarget(dynamic.copy(id_str = ""), item))
        assertNull(resolveDynamicCommentPreviewTarget(dynamic, like))
    }

    @Test
    fun bodyLinksCannotSupplyTheSummarysOwnCommentIdentity() {
        val item = comment.copy(extend_click_param = emptyMap(), desc = DynamicDesc(rich_text_nodes = listOf(
            RichTextNode(type = "AT", rid = "4422", jump_url = "https://space.bilibili.com/4422"),
            RichTextNode(type = "WEB", jump_url = "bilibili://following/detail/999?root=10&comment_id=20"),
            RichTextNode(type = "WEB", jump_url = "https://www.bilibili.com/video/BV1xx411c7mD?root=30&comment_id=40"),
            RichTextNode(type = "WEB", jump_url = "https://www.bilibili.com/opus/123?root=701#reply702"),
        )))

        assertEquals(
            BilibiliNavigationTarget.Dynamic("123", openComments = true),
            resolveDynamicCommentPreviewTarget(dynamic, item),
        )
    }

    @Test
    fun unlocatedLegacyCachePreviewsStayHiddenWithoutHidingLikeHints() {
        val legacy = comment.copy(extend_click_param = emptyMap())
        assertEquals(listOf(like), visible(listOf(legacy, like), commentPreviewVisible = true))
    }

    @Test
    fun feedAndSpacePreviewKeepDynamicIdentityAndTheServerCommentSubject() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = """{
            "id_str":"123","type":"DYNAMIC_TYPE_DRAW",
            "basic":{"comment_id_str":"888","comment_type":11,"rid_str":"777"},
            "modules":{
                "module_dynamic":{"major":{"type":"MAJOR_TYPE_OPUS","opus":{"jump_url":"https://www.bilibili.com/opus/123"}}},
                "module_interaction":{"items":[{"type":1,"desc":{"rich_text_nodes":[
                    {"type":"RICH_TEXT_NODE_TYPE_AT","text":"评论作者：","rid":4422},
                    {"type":"RICH_TEXT_NODE_TYPE_TEXT","text":"热门评论"}
                ]}}]}
            }
        }"""
        val feedItem = json.decodeFromString<DynamicItem>(payload)
        val spaceItem = resolveSpaceDynamicCardItem(json.decodeFromString<SpaceDynamicItem>(payload))

        listOf(feedItem, spaceItem).forEach { item ->
            assertEquals("888", item.basic?.comment_id_str)
            assertEquals(11, item.basic?.comment_type)
            assertEquals(888L, resolveDynamicCommentTarget(item)?.oid)
            assertEquals(11, resolveDynamicCommentTarget(item)?.type)
            assertEquals(
                BilibiliNavigationTarget.Dynamic("123", openComments = true),
                resolveDynamicCommentPreviewTarget(item, item.modules.module_interaction!!.items.single()),
            )
        }
    }

    @Test
    fun summaryClickParamsOverrideUriAndBodyLinksAfterDecodingFeedAndSpacePayloads() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = """{
            "id_str":"123","type":"DYNAMIC_TYPE_DRAW",
            "basic":{"comment_id_str":"888","comment_type":11},
            "modules":{"module_interaction":{"items":[{
                "type":1,
                "uri":"bilibili://comment3/main/888/11?rp_id=10&sub_rp_id=20",
                "extend_click_param":{"rp_id":"701","sub_rp_id":"702"},
                "desc":{"rich_text_nodes":[
                    {"type":"AT","text":"@用户","rid":"4422"},
                    {"type":"WEB","jump_url":"bilibili://following/detail/123?root=30&comment_id=40"}
                ]}
            }]}}
        }"""
        val feedItem = json.decodeFromString<DynamicItem>(payload)
        val spaceItem = resolveSpaceDynamicCardItem(json.decodeFromString<SpaceDynamicItem>(payload))
        listOf(feedItem, spaceItem).forEach { card ->
            assertEquals(
                BilibiliNavigationTarget.Dynamic("123", 701L, 702L, openComments = true),
                resolveDynamicCommentPreviewTarget(card, card.modules.module_interaction!!.items.single()),
            )
        }
    }

    @Test
    fun comment3SubjectMustMatchBothOidAndBusinessType() {
        val card = dynamic.copy(basic = DynamicBasic(comment_id_str = "888", comment_type = 11))
        assertEquals(
            BilibiliNavigationTarget.Dynamic("123", 701L, 702L, openComments = true),
            resolveDynamicCommentPreviewTarget(
                card, comment.copy(uri = "bilibili://comment3/main/888/11?rp_id=701&sub_rp_id=702"),
            ),
        )
        listOf("999/11", "888/1").forEach { subject ->
            assertEquals(
                BilibiliNavigationTarget.Dynamic("123", openComments = true),
                resolveDynamicCommentPreviewTarget(
                    card, comment.copy(uri = "bilibili://comment3/main/$subject?rp_id=701&sub_rp_id=702"),
                ),
            )
        }
    }

    @Test
    fun mapOnlyLocationUsesCurrentDynamicAndDoesNotPromoteReplyToRoot() {
        assertEquals(
            BilibiliNavigationTarget.Dynamic("123", 0L, 702L, openComments = true),
            resolveDynamicCommentPreviewTarget(
                dynamic, comment.copy(extend_click_param = mapOf("sub_rp_id" to "702")),
            ),
        )
        assertEquals(
            BilibiliNavigationTarget.Dynamic("123", openComments = true),
            resolveDynamicCommentPreviewTarget(
                dynamic, comment.copy(extend_click_param = mapOf("rp_id" to "0", "reply_id" to "-1")),
            ),
        )
    }

    @Test
    fun videoSummaryUriOnlySuppliesLocationForTheCardsActualCommentSubject() {
        val card = dynamic.copy(basic = DynamicBasic(comment_id_str = "888", comment_type = 1))
        assertEquals(
            BilibiliNavigationTarget.Dynamic("123", 701L, 702L, openComments = true),
            resolveDynamicCommentPreviewTarget(
                card, comment.copy(uri = "bilibili://video/888?rp_id=701&sub_rp_id=702"),
            ),
        )
        assertEquals(
            BilibiliNavigationTarget.Dynamic("123", openComments = true),
            resolveDynamicCommentPreviewTarget(
                card, comment.copy(uri = "bilibili://video/999?rp_id=701&sub_rp_id=702"),
            ),
        )
    }
}
